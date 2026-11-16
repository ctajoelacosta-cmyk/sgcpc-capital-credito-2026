package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.FacturaDAO;
import py.com.capitalcredito.sgcpc.dao.GestionCobranzaDAO;
import py.com.capitalcredito.sgcpc.dao.LiquidacionAnticipadaDAO;
import py.com.capitalcredito.sgcpc.dao.MovimientoCajaDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.PlanPagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.ReversionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.SesionCajaDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.excepciones.CierreDocumentalCompletoException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.PlanPago;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;

/**
 * PO-13 (E9/Bloque 5): reversión de un cobro -- cajera solicita, gerente autoriza o rechaza. RN-13
 * ("nada se borra, se exige caja ABIERTA para ejecutar") y la decisión de Joel de limitarla a la
 * ÚLTIMA operación del préstamo (sin importar la sesión de caja). "Nunca se borra": el pago pasa a
 * REVERTIDO, la liquidación (si había) a REVERTIDA, la factura (si había) a ANULADA -- todo sigue
 * siendo una fila reimprimible, nunca desaparece.
 */
public class ServicioReversiones {

    private final PagoDAO pagoDAO = new PagoDAO();
    private final ReversionPagoDAO reversionPagoDAO = new ReversionPagoDAO();
    private final AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();
    private final CuotaDAO cuotaDAO = new CuotaDAO();
    private final PlanPagoDAO planPagoDAO = new PlanPagoDAO();
    private final LiquidacionAnticipadaDAO liquidacionAnticipadaDAO = new LiquidacionAnticipadaDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final FacturaDAO facturaDAO = new FacturaDAO();
    private final GestionCobranzaDAO gestionCobranzaDAO = new GestionCobranzaDAO();
    private final MovimientoCajaDAO movimientoCajaDAO = new MovimientoCajaDAO();
    private final SesionCajaDAO sesionCajaDAO = new SesionCajaDAO();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioCobranza servicioCobranza = new ServicioCobranza();

    /**
     * Decisión 2 (Joel): "solo la operación más reciente" -- sin importar la sesión de caja en la
     * que se hizo. Se revalida de nuevo (con lock) dentro de autorizarReversion, por si se cobró
     * algo nuevo entre la solicitud y la autorización.
     */
    private boolean esLaUltimaOperacion(Connection conexion, Pago pago) throws SQLException {
        List<Pago> pagosPrestamo = pagoDAO.listarPorPrestamo(conexion, pago.getIdPrestamo());
        return !pagosPrestamo.isEmpty() && pagosPrestamo.get(0).getIdPago().equals(pago.getIdPago());
    }

    /**
     * Punto 1 (RF25, Opción A, pedido de Joel): reglas puras (sin Connection) que decide si se
     * puede solicitar -- o volver a solicitar tras un rechazo -- la reversión de un pago. Separado
     * de solicitarReversion para poder probarlo sin base de datos (REGLAS_DESARROLLO.md §7 punto
     * 9), mismo criterio que ServicioCaja.validarMotivoDiferencia/esDeHoy. Una RECHAZADA no
     * bloquea (se puede reintentar); una SOLICITADA o AUTORIZADA sí. Independientemente de eso,
     * si ya no es la última operación del préstamo (regla de RF25_reversion_cobro.md §6c/§7),
     * nunca se puede solicitar.
     * <p>
     * 13/10/2026 (hallazgo de Joel): tampoco se puede solicitar si el cierre documental de la
     * operación ya está COMPLETO (el pagaré original ya se devolvió al cliente) -- autorizar esa
     * reversión dejaría al préstamo con deuda otra vez sin que la empresa tenga el pagaré en su
     * poder. Ver RF25 §6g/RF29 §6.
     */
    public void validarPuedeSolicitarReversion(ReversionPago reversionPrevia, boolean esLaUltimaOperacion,
            boolean cierreDocumentalCompleto) {
        if (reversionPrevia != null) {
            if (reversionPrevia.getEstado() == EstadoReversion.SOLICITADA) {
                throw new NegocioException("Ya hay una solicitud de reversión pendiente para este pago.");
            }
            if (reversionPrevia.getEstado() == EstadoReversion.AUTORIZADA) {
                throw new NegocioException("Este pago ya fue revertido.");
            }
        }
        if (!esLaUltimaOperacion) {
            throw new NegocioException("Solo se puede revertir la operación más reciente de este préstamo.");
        }
        if (cierreDocumentalCompleto) {
            // 13/10/2026 (pedido de Joel, reprueba en pantalla): no es un error del servidor --
            // advertencia ("!"/"Entendido"), mismo patrón que MotivoDiferenciaInsuficienteException
            // (RF19)/MontoPrometidoFaltanteException (RF24).
            throw new CierreDocumentalCompletoException("No se puede solicitar la reversión: el cierre "
                    + "documental de esta operación ya está completo (se devolvió el pagaré original al "
                    + "cliente).");
        }
    }

    /**
     * Pura (REGLAS_DESARROLLO.md §7 punto 9): si el pago tenía factura, la cajera tiene que
     * declarar que tiene en su poder el original y todas las copias devueltas por el cliente --
     * mismo control que después hace el gerente al autorizar (doble control, pedido de Joel,
     * 12/10/2026), pero del lado de la cajera y antes de que la solicitud llegue a la bandeja.
     */
    public void validarDeclaracionFacturaCajera(boolean tieneFactura, boolean facturaEnPoderDeclaradoCajera) {
        if (tieneFactura && !facturaEnPoderDeclaradoCajera) {
            throw new NegocioException("Para solicitar la reversión de un cobro con factura, confirme que "
                    + "tiene en su poder el original y todas las copias de la factura devueltas por el cliente.");
        }
    }

    // ======================================================================
    // C30 (01/11/2026 → decisión de Joel del 03/11/2026: avisar, NO bloquear).
    //
    // Con una reversión SOLICITADA, los tres caminos de cobro (cobrar, adelantar a capital y
    // cancelar todo) siguen confirmando sin mirar `reversiones_pago` -- y está bien que no
    // bloqueen: el cliente que vino a pagar no puede quedar esperando la decisión del gerente.
    // Lo que faltaba es que nadie avisara de la consecuencia: `autorizarReversion` revalida
    // `esLaUltimaOperacion` (abajo), así que CUALQUIER pago CONFIRMADO posterior --un cobro de
    // cuota, un adelanto o una cancelación-- deja esa reversión imposible de autorizar: al
    // gerente solo le queda rechazarla.
    //
    // Los dos textos son de acá (un dato, un dueño; B92/B96: redactados en "usted" y las
    // pantallas los muestran tal cual, nunca los reescriben), y los dos métodos de consulta son
    // de solo lectura.
    // ======================================================================

    /**
     * El aviso para la cajera, en el cartel de confirmación de los tres caminos de cobro. Puro.
     *
     * <p>Va en DOS oraciones porque el cartel parte el mensaje en título y detalle por el primer
     * ". " seguido de mayúscula ({@code pie.jsp}, {@code dividirEnTituloYDetalle}) -- un mensaje
     * de una sola oración se muestra entero como título y pierde su punto final (B42).</p>
     */
    public static String textoAvisoDeReversionPendiente(String numeroRecibo) {
        return "Hay una reversión pendiente de autorizar sobre el recibo " + numeroRecibo
                + ". Si confirma esta operación, esa reversión ya no podrá autorizarse: el gerente"
                + " solo podrá rechazarla.";
    }

    /** La marca de la bandeja del gerente, sobre una solicitud que ya no se puede autorizar. Pura. */
    public static String textoAvisoOperacionPosterior() {
        return "Ya hay una operación posterior: solo se puede rechazar.";
    }

    /**
     * C30: el aviso de este préstamo, o null si no hay ninguna reversión esperando autorización.
     *
     * <p>Sale de la misma lista que ve el gerente en su bandeja ({@code listarSolicitadas}, que
     * hoy trae un puñado de filas), así que el aviso de la cajera y la bandeja no pueden decir
     * cosas distintas. Solo lectura.</p>
     */
    public String avisoDeReversionPendiente(Connection conexion, long idPrestamo) throws SQLException {
        for (ReversionPago reversion : reversionPagoDAO.listarSolicitadas(conexion)) {
            Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
            if (pago != null && pago.getIdPrestamo() == idPrestamo) {
                return textoAvisoDeReversionPendiente(pago.getNumeroRecibo());
            }
        }
        return null;
    }

    /**
     * C30: ¿ya existe un pago CONFIRMADO posterior al que se quiere revertir? Si sí,
     * {@link #autorizarReversion} va a rechazar la autorización, y la bandeja lo dice **antes**
     * de que el gerente lo intente. Mismo criterio que usa la autorización
     * ({@code esLaUltimaOperacion}), no una consulta paralela. Solo lectura.
     */
    public boolean hayOperacionPosterior(Connection conexion, Pago pagoARevertir) throws SQLException {
        return pagoARevertir != null && !esLaUltimaOperacion(conexion, pagoARevertir);
    }

    /** Cajera (Permiso.SOLICITAR_REVERSION): deja la solicitud en SOLICITADA, sin tocar dinero ni cuotas. */
    public ReversionPago solicitarReversion(long idPago, String motivo, long idUsuarioCajero,
            String identificadorConfirmacion, boolean facturaEnPoderDeclaradoCajera) throws SQLException {
        if (identificadorConfirmacion == null || identificadorConfirmacion.trim().isEmpty()) {
            throw new NegocioException("Falta la clave de confirmación de la solicitud.");
        }
        if (motivo == null || motivo.trim().length() < 10) {
            throw new NegocioException("Ingrese el motivo de la reversión (mínimo 10 caracteres).");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                ReversionPago existente = reversionPagoDAO.obtenerPorIdentificadorConfirmacion(conexion,
                        identificadorConfirmacion);
                if (existente != null) {
                    conexion.commit();
                    return existente;
                }

                Pago pago = pagoDAO.obtenerPorId(conexion, idPago);
                if (pago == null) {
                    throw new NegocioException("El pago no existe.");
                }
                if (pago.getEstadoPago() != EstadoConfirmacionPago.CONFIRMADO) {
                    throw new NegocioException("Este pago ya fue revertido.");
                }
                Factura factura = facturaDAO.obtenerPorPago(conexion, idPago);
                validarDeclaracionFacturaCajera(factura != null, facturaEnPoderDeclaradoCajera);
                ReversionPago reversionPrevia = reversionPagoDAO.obtenerPorIdPago(conexion, idPago);
                // 13/10/2026 (hallazgo de Joel): cierre documental ya completo -- el pagaré
                // original ya se devolvió, no se puede solicitar la reversión.
                PrestamoCredito prestamoDeLaSolicitud = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
                boolean cierreDocumentalCompleto = prestamoDeLaSolicitud != null
                        && "COMPLETO".equals(prestamoDeLaSolicitud.getEstadoCierreDocumental());
                validarPuedeSolicitarReversion(reversionPrevia, esLaUltimaOperacion(conexion, pago),
                        cierreDocumentalCompleto);

                // Punto 5 (RF25, factura, pedido de Joel): la declaración de la cajera queda en la
                // auditoría -- no hay una columna propia en reversiones_pago para esto (sería la
                // segunda, junto a factura_en_poder_confirmado del gerente, solo para mostrarla en
                // la bandeja mientras está pendiente; se evita el cambio de esquema porque, al
                // estar validada acá como obligatoria, cualquier solicitud de un pago con factura
                // que llega a existir YA implica que la cajera la declaró -- ver RF25 §7).
                String detalleFactura = factura == null ? ""
                        : " Declaró tener en su poder la factura " + factura.getNumeroCompleto() + ".";

                ReversionPago reversion;
                if (reversionPrevia != null) {
                    // Punto 1 (RF25, Opción A, pedido de Joel): tras un rechazo, se puede volver a
                    // solicitar con un motivo nuevo -- se reutiliza la misma fila (id_pago es
                    // UNIQUE), nunca se inserta una segunda. El motivo del rechazo anterior se
                    // guarda ACÁ (antes de limpiarlo) porque reversion == reversionPrevia -- es el
                    // mismo objeto; si se lee después de setMotivoRechazo(null), ya está vacío.
                    String motivoRechazoAnterior = reversionPrevia.getMotivoRechazo();
                    reversionPagoDAO.marcarResolicitada(conexion, reversionPrevia.getIdReversionPago(),
                            identificadorConfirmacion, motivo.trim(), idUsuarioCajero);
                    reversion = reversionPrevia;
                    reversion.setIdentificadorConfirmacion(identificadorConfirmacion);
                    reversion.setMotivoReversion(motivo.trim());
                    reversion.setSolicitadoPor(idUsuarioCajero);
                    reversion.setMotivoRechazo(null);
                    servicioAuditoria.registrar(conexion, idUsuarioCajero, "SOLICITAR_REVERSION", "PAGO", idPago,
                            "Volvió a solicitar revertir " + pago.getNumeroRecibo() + " (rechazo anterior: "
                                    + motivoRechazoAnterior + "): " + motivo.trim() + detalleFactura);
                } else {
                    reversion = new ReversionPago();
                    reversion.setIdentificadorConfirmacion(identificadorConfirmacion);
                    reversion.setIdPago(idPago);
                    reversion.setMotivoReversion(motivo.trim());
                    reversion.setSolicitadoPor(idUsuarioCajero);
                    long idReversion = reversionPagoDAO.insertarSolicitud(conexion, reversion);
                    reversion.setIdReversionPago(idReversion);
                    servicioAuditoria.registrar(conexion, idUsuarioCajero, "SOLICITAR_REVERSION", "PAGO", idPago,
                            "Solicitó revertir " + pago.getNumeroRecibo() + ": " + motivo.trim() + detalleFactura);
                }
                reversion.setEstado(EstadoReversion.SOLICITADA);
                conexion.commit();
                return reversion;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                if ("23505".equals(error.getSQLState())) {
                    ReversionPago reversionExistente = reversionPagoDAO
                            .obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                    if (reversionExistente != null) {
                        return reversionExistente;
                    }
                }
                throw error;
            }
        }
    }

    /** Bandeja del gerente (/reversiones). */
    public List<ReversionPago> listarSolicitadas(Connection conexion) throws SQLException {
        return reversionPagoDAO.listarSolicitadas(conexion);
    }

    /** Punto 12: pestaña "Historial" de /reversiones. */
    public List<ReversionPago> listarDecididas(Connection conexion) throws SQLException {
        return reversionPagoDAO.listarDecididas(conexion);
    }

    public ReversionPago obtenerPorId(Connection conexion, long idReversionPago) throws SQLException {
        return reversionPagoDAO.obtenerPorId(conexion, idReversionPago);
    }

    /** Gerente (Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA): rechaza sin tocar dinero ni cuotas. */
    public ReversionPago rechazarReversion(long idReversionPago, long idUsuarioGerente, String motivoRechazo)
            throws SQLException {
        if (motivoRechazo == null || motivoRechazo.trim().isEmpty()) {
            throw new NegocioException("Ingrese el motivo del rechazo.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                ReversionPago reversion = reversionPagoDAO.obtenerPorIdConLock(conexion, idReversionPago);
                if (reversion == null) {
                    throw new NegocioException("La solicitud de reversión no existe.");
                }
                if (reversion.getEstado() == EstadoReversion.RECHAZADA) {
                    conexion.commit();
                    return reversion;
                }
                if (reversion.getEstado() != EstadoReversion.SOLICITADA) {
                    throw new NegocioException("Esta solicitud ya fue autorizada; no se puede rechazar.");
                }
                reversionPagoDAO.marcarRechazada(conexion, idReversionPago, motivoRechazo.trim());
                servicioAuditoria.registrar(conexion, idUsuarioGerente, "RECHAZAR_REVERSION", "REVERSION_PAGO",
                        idReversionPago, "Rechazó la reversión: " + motivoRechazo.trim());
                conexion.commit();
                reversion.setEstado(EstadoReversion.RECHAZADA);
                reversion.setMotivoRechazo(motivoRechazo.trim());
                return reversion;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Gerente (Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA): el corazón de PO-13. Orden de la
     * transacción (ajustes A-I aprobados por Joel): 1) revalida SOLICITADA + última operación;
     * 2) DNIT -- si hay factura, exige la casilla de "factura en poder del cliente" marcada;
     * 3) caja ABIERTA, de hoy, con efectivo suficiente para devolver; 4) restaura cada cuota
     * tocada (inversa exacta del motor); 5) deshace el anticipo (plan anterior vuelve a vigente) o
     * la cancelación total (el préstamo deja de estar FINALIZADO); 6) recalcula estado_prestamo
     * con calcularEstadoSegunCuotas (nunca recalcularEstadoPrestamo, que no revierte un
     * FINALIZADO); 7) anula la factura (Nota de anulación -- el PDF se genera después, al primer
     * pedido, D-94); 8) la gestión que este pago haya cerrado vuelve a abierta; 9) COMPENSACION en
     * caja; 10) pago -> REVERTIDO, reversión -> AUTORIZADA; 11) auditoría.
     */
    public ReversionPago autorizarReversion(long idReversionPago, long idUsuarioGerente,
            Boolean facturaEnPoderConfirmado, LocalDate fecha) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                ReversionPago reversion = reversionPagoDAO.obtenerPorIdConLock(conexion, idReversionPago);
                if (reversion == null) {
                    throw new NegocioException("La solicitud de reversión no existe.");
                }
                if (reversion.getEstado() == EstadoReversion.AUTORIZADA) {
                    conexion.commit();
                    return reversion;
                }
                if (reversion.getEstado() != EstadoReversion.SOLICITADA) {
                    throw new NegocioException("Esta solicitud ya fue rechazada; no se puede autorizar.");
                }

                Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
                if (pago == null) {
                    throw new NegocioException("El pago no existe.");
                }
                if (pago.getEstadoPago() != EstadoConfirmacionPago.CONFIRMADO) {
                    throw new NegocioException("Este pago ya no está confirmado.");
                }
                if (!esLaUltimaOperacion(conexion, pago)) {
                    throw new NegocioException("Ya se registró una operación posterior en este préstamo; "
                            + "no se puede autorizar esta reversión.");
                }

                PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
                if (prestamoCredito == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                // 13/10/2026 (hallazgo de Joel): revalida acá también -- el cierre documental
                // pudo completarse entre la solicitud y esta autorización (mismo criterio que
                // esLaUltimaOperacion, revalidado en ambos lugares).
                if ("COMPLETO".equals(prestamoCredito.getEstadoCierreDocumental())) {
                    // 13/10/2026 (pedido de Joel): advertencia, no error -- mismo criterio que
                    // al solicitar (ver validarPuedeSolicitarReversion).
                    throw new CierreDocumentalCompletoException("No se puede autorizar la reversión: el "
                            + "cierre documental de esta operación ya está completo (se devolvió el pagaré "
                            + "original al cliente).");
                }

                // Ajuste I (DNIT, PA-11): sin la factura física de vuelta, no se anula. Dos citas
                // distintas encontradas para esta misma regla (Decreto 6539/05 y RG 118/2017) --
                // a confirmar con el contador cuál es la vigente (12/10/2026, ver RF25 §10); en
                // pantalla (reversiones/bandeja.jsp) no se cita ningún número, solo "según la
                // normativa de la DNIT".
                Factura factura = facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
                if (factura != null && (facturaEnPoderConfirmado == null || !facturaEnPoderConfirmado)) {
                    throw new NegocioException("La factura ya fue entregada al cliente. Corresponde emitir una "
                            + "Nota de Crédito, que no está disponible en esta versión.");
                }

                // Ajuste C: caja ABIERTA, de hoy, con efectivo suficiente para devolver.
                SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
                if (sesion == null) {
                    throw new NegocioException("No hay una caja abierta.");
                }
                if (!servicioCaja.esDeHoy(sesion)) {
                    throw new NegocioException("La caja del "
                            + FormateadorFechas.comoDdMmAaaa(sesion.getFechaHoraApertura().toLocalDate())
                            + " sigue abierta: ciérrela y rinda antes de operar hoy.");
                }
                BigDecimal saldoTeorico = servicioCaja.calcularSaldoTeorico(conexion, sesion.getIdSesionCaja());
                if (saldoTeorico.compareTo(pago.getMontoAplicado()) < 0) {
                    throw new NegocioException("La caja no tiene efectivo suficiente para devolver "
                            + FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()) + " (saldo actual "
                            + FormateadorNumeros.comoGuaranies(saldoTeorico) + ").");
                }

                EstadoPrestamo estadoAnterior = prestamoCredito.getEstadoPrestamo();
                int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");

                // Restaura cada cuota que este pago haya tocado -- inversa exacta del motor (NORMAL,
                // PARCIAL y CANCELACION lo hacen vía filas CUOTA; un ANTICIPO no toca ninguna cuota,
                // solo genera un plan nuevo, ver más abajo).
                Prestamo prestamoMotor = servicioCobranza.cargarPrestamoParaMotor(conexion, pago.getIdPrestamo());
                List<AplicacionPago> aplicaciones = aplicacionPagoDAO.listarPorPago(conexion, pago.getIdPago());
                for (AplicacionPago aplicacionRow : aplicaciones) {
                    if (aplicacionRow.getTipoAplicacion() != TipoAplicacion.CUOTA
                            || aplicacionRow.getIdCuota() == null) {
                        continue;
                    }
                    Cuota cuotaActual = buscarCuotaPorId(prestamoMotor, aplicacionRow.getIdCuota());
                    if (cuotaActual == null) {
                        continue;
                    }
                    LocalDate fechaCorteMoraAnterior = aplicacionPagoDAO.obtenerUltimaFechaTocada(conexion,
                            aplicacionRow.getIdCuota(), pago.getIdPago());
                    Cuota restaurada = servicioMotorFinanciero.revertirAplicacionEnCuota(cuotaActual,
                            aplicacionRow.getMontoCapital(), aplicacionRow.getMontoInteres(),
                            aplicacionRow.getMontoMoratorio(), aplicacionRow.getMontoPunitorio(),
                            fechaCorteMoraAnterior, prestamoCredito.getTasaMoratoria(),
                            prestamoCredito.getTasaPunitoria(), diasGracia,
                            pago.getFechaHoraPago().toLocalDate());
                    cuotaDAO.actualizarTrasPago(conexion, restaurada);
                    reemplazarCuota(prestamoMotor, restaurada);
                }

                if (pago.getTipo() == TipoPago.ANTICIPO) {
                    // Ajuste D/RN-13: el plan que generó el anticipo queda como registro histórico
                    // (nunca se borra); vuelve a vigente el que estaba antes.
                    PlanPago planNuevo = planPagoDAO.obtenerPorLiquidacionOrigen(conexion,
                            pago.getIdLiquidacionAnticipada());
                    if (planNuevo != null) {
                        PlanPago planAnterior = planPagoDAO.obtenerPorPrestamoYVersion(conexion, pago.getIdPrestamo(),
                                planNuevo.getVersionPlan() - 1);
                        if (planAnterior == null) {
                            throw new IllegalStateException("No se encontró la versión de plan anterior al "
                                    + "anticipo (id_plan_pago " + planNuevo.getIdPlanPago()
                                    + "); no se puede revertir.");
                        }
                        planPagoDAO.marcarNoVigente(conexion, planNuevo.getIdPlanPago());
                        planPagoDAO.marcarVigente(conexion, planAnterior.getIdPlanPago());
                    }
                    liquidacionAnticipadaDAO.marcarRevertida(conexion, pago.getIdLiquidacionAnticipada());
                    // El plan vigente cambió: se recarga desde la base (ya ve el swap de arriba,
                    // misma transacción/conexión) para calcular el estado con las cuotas correctas.
                    prestamoMotor = servicioCobranza.cargarPrestamoParaMotor(conexion, pago.getIdPrestamo());
                } else if (pago.getTipo() == TipoPago.CANCELACION) {
                    liquidacionAnticipadaDAO.marcarRevertida(conexion, pago.getIdLiquidacionAnticipada());
                }

                // Ajuste: nunca recalcularEstadoPrestamo (no revierte un FINALIZADO) -- siempre
                // calcularEstadoSegunCuotas + el UPDATE que corresponda.
                EstadoPrestamo nuevoEstado = servicioCobranza.calcularEstadoSegunCuotas(prestamoMotor, fecha);
                // E-13 (pedido de Joel): revertirFinalizacion avisa si el cierre documental ya
                // estaba COMPLETO (pagaré ya devuelto) -- caso que no debería poder darse; se dibuja
                // en el detalle de auditoría en vez de quedar en silencio.
                boolean cierreDocumentalYaCompleto = false;
                if (estadoAnterior == EstadoPrestamo.FINALIZADO) {
                    cierreDocumentalYaCompleto = prestamoDAO.revertirFinalizacion(conexion, pago.getIdPrestamo(), nuevoEstado);
                } else if (nuevoEstado != estadoAnterior) {
                    prestamoDAO.actualizarEstadoMora(conexion, pago.getIdPrestamo(), nuevoEstado);
                }

                // Ajuste B/F (RF-41, DNIT RG 118/2017): anula la factura -- reserva el número de la
                // Nota de anulación acá mismo, dentro de la transacción de dinero (como el número de
                // recibo/constancia); el PDF se genera después, al primer pedido (D-94).
                String numeroNotaAnulacion = null;
                if (factura != null) {
                    int anioNotaAnulacion = fecha.getYear();
                    long secuencia = numeradorDAO.siguienteValor(conexion, "ANULACION", anioNotaAnulacion);
                    numeroNotaAnulacion = String.format("NAN-%d-%06d", anioNotaAnulacion, secuencia);
                    facturaDAO.marcarAnulada(conexion, factura.getIdFactura(), idReversionPago, numeroNotaAnulacion);
                }

                // Ajuste A: "como si el cobro nunca hubiera existido" -- la gestión que ESTE pago
                // haya cerrado vuelve a abierta (limitación conocida: solo la vinculada directo por
                // id_pago, ver GestionCobranzaDAO.marcarNoCumplidaPorPago).
                gestionCobranzaDAO.marcarNoCumplidaPorPago(conexion, pago.getIdPago());

                MovimientoCaja compensacion = new MovimientoCaja();
                compensacion.setIdSesionCaja(sesion.getIdSesionCaja());
                compensacion.setTipo(TipoMovimientoCaja.COMPENSACION);
                compensacion.setMonto(pago.getMontoAplicado());
                compensacion.setSigno(-1);
                compensacion.setReferenciaTipo("REVERSION_PAGO");
                compensacion.setIdReferencia(idReversionPago);
                compensacion.setRegistradoPor(idUsuarioGerente);
                compensacion.setObservacion("Reversión de " + pago.getNumeroRecibo() + " de "
                        + prestamoCredito.getIdentificadorOperacion() + ".");
                movimientoCajaDAO.insertar(conexion, compensacion);

                pagoDAO.marcarRevertido(conexion, pago.getIdPago());
                reversionPagoDAO.marcarAutorizada(conexion, idReversionPago, idUsuarioGerente, idUsuarioGerente,
                        sesion.getIdSesionCaja(), pago.getMontoAplicado(), factura != null);

                servicioAuditoria.registrar(conexion, idUsuarioGerente, "AUTORIZAR_REVERSION", "PAGO",
                        pago.getIdPago(), "Autorizó revertir " + pago.getNumeroRecibo() + " ("
                                + FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()) + ") de "
                                + prestamoCredito.getIdentificadorOperacion()
                                // Punto 5 (RF25, factura, pedido de Joel): la declaración del
                                // gerente (doble control, junto a la de la cajera en
                                // SOLICITAR_REVERSION) -- llegar acá con factura != null ya
                                // implica que la casilla estaba tildada (verificado más arriba).
                                + (numeroNotaAnulacion == null ? "" : "; declaró tener en su poder la factura "
                                        + factura.getNumeroCompleto() + "; anuló factura, " + numeroNotaAnulacion)
                                + (cierreDocumentalYaCompleto ? "; AVISO: el cierre documental de este préstamo ya "
                                        + "estaba COMPLETO (pagaré devuelto) al revertir la finalización -- caso "
                                        + "que no debería poder darse, no se tocó el estado del cierre documental"
                                        : "")
                                + ".");
                conexion.commit();

                reversion.setEstado(EstadoReversion.AUTORIZADA);
                return reversion;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private Cuota buscarCuotaPorId(Prestamo prestamoMotor, long idCuota) {
        for (Cuota cuota : prestamoMotor.getCuotas()) {
            if (cuota.getIdCuota() != null && cuota.getIdCuota() == idCuota) {
                return cuota;
            }
        }
        return null;
    }

    private void reemplazarCuota(Prestamo prestamoMotor, Cuota restaurada) {
        List<Cuota> cuotas = prestamoMotor.getCuotas();
        for (int i = 0; i < cuotas.size(); i++) {
            if (cuotas.get(i).getIdCuota().equals(restaurada.getIdCuota())) {
                cuotas.set(i, restaurada);
                return;
            }
        }
    }
}
