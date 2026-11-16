package py.com.capitalcredito.sgcpc.servicios;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import py.com.capitalcredito.sgcpc.dao.DesembolsoDAO;
import py.com.capitalcredito.sgcpc.dao.DocumentoExpedienteDAO;
import py.com.capitalcredito.sgcpc.dao.MovimientoCajaDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SesionCajaDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P08, desembolso (Doc. 10 §10.11: ControladorDesembolsoServlet / ServicioDesembolso /
 * ServicioCaja). RN-08: desembolso único, en efectivo, con caja ABIERTA y efectivo suficiente,
 * sin descuentos (decisión 7 del plan de E6: cargos_operacion no se usa en V1, RN-17).
 */
public class ServicioDesembolso {

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final DesembolsoDAO desembolsoDAO = new DesembolsoDAO();
    private final SesionCajaDAO sesionCajaDAO = new SesionCajaDAO();
    private final MovimientoCajaDAO movimientoCajaDAO = new MovimientoCajaDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    private final DocumentoExpedienteDAO documentoExpedienteDAO = new DocumentoExpedienteDAO();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();

    /** Última versión VIGENTE del contrato generado, para mostrar/validar su vigencia. Pública para la ficha. */
    public DocumentoExpediente obtenerContratoGeneradoVigente(Connection conexion, long idPrestamo) throws SQLException {
        TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, "CONTRATO_GENERADO");
        return documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo, tipo.getIdTipoDocumento());
    }

    /**
     * Decisión 6 del plan de E6: bloqueo duro si pasaron más de "dias_validez_documentos" días
     * desde que se emitió la última versión vigente del contrato generado (se refresca solo al
     * regenerar). Pura.
     */
    public boolean validarDocumentosVigentes(LocalDate fechaDocumento, int diasValidez) {
        return fechaDocumento != null && !Reloj.hoy().isAfter(fechaDocumento.plusDays(diasValidez));
    }

    /**
     * Ajuste de Joel (prueba manual de E6): mensaje de rechazo por saldo insuficiente con los
     * tres montos, mismo formato que el resto del sistema ("3.000.000 Gs."). Pura.
     */
    public String construirMensajeSaldoInsuficiente(BigDecimal saldoTeorico, BigDecimal montoNeto) {
        BigDecimal faltante = montoNeto.subtract(saldoTeorico);
        // FormateadorNumeros.comoGuaranies ya termina en "." (p. ej. "3.000.000 Gs.") -- sin
        // punto propio antes de la siguiente oración, para no duplicarlo ("Gs.. Faltan").
        return "Hay " + FormateadorNumeros.comoGuaranies(saldoTeorico)
                + " en caja y el desembolso es de " + FormateadorNumeros.comoGuaranies(montoNeto)
                + " Faltan " + FormateadorNumeros.comoGuaranies(faltante)
                + " Pedí al gerente que entregue efectivo.";
    }

    /**
     * RF14 (pedido de Joel, 11/10/2026): cuando `marcarDesembolsado` devuelve `false` (alguien
     * anuló el préstamo primero, carrera de dos pestañas) -- relee el préstamo ya actualizado
     * (la misma transacción, bajo READ COMMITTED, ya ve el valor comiteado por la otra, mismo
     * criterio que `ServicioAprobacionCredito.construirErrorYaDecidida`/
     * `ServicioCaja.construirErrorCajaYaCerrada`) para armar el mensaje con quién y cuándo.
     */
    private py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException
            construirErrorPrestamoYaAnulado(Connection conexion, long idPrestamo) throws SQLException {
        PrestamoCredito actual = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        Usuario quienAnulo = actual.getAnuladoPor() == null ? null
                : servicioUsuarios.buscarPorId(conexion, actual.getAnuladoPor());
        return new py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException(
                construirMensajePrestamoYaAnulado(actual, quienAnulo));
    }

    /** Pura: "Este préstamo ya fue anulado por NOMBRE el FECHA HORA. Actualice la página." */
    String construirMensajePrestamoYaAnulado(PrestamoCredito actual, Usuario quienAnulo) {
        String nombre = quienAnulo == null ? "otro usuario" : quienAnulo.getNombres() + " " + quienAnulo.getApellidos();
        return "Este préstamo ya fue anulado por " + nombre + " el "
                + FormateadorFechas.comoDdMmAaaaHhMm(actual.getFechaAnulacionSinDesembolso())
                + ". Actualice la página.";
    }

    /**
     * PO-07: confirma el desembolso en una sola transacción. Idempotente por
     * identificadorConfirmacion (§14.3): si ya existe, no repite nada (decisión 10 del plan de
     * E6); la restricción UNIQUE de la columna es la red de seguridad final ante una carrera real
     * (dos clics simultáneos), tratada también como éxito, no como error. Devuelve el préstamo
     * (identificador de operación y monto) para que el controlador arme el mensaje de éxito -- el
     * servicio no redacta texto de pantalla (mismo criterio que `ServicioCaja.cerrarCaja`, ajuste
     * de Joel tras E6).
     */
    public PrestamoCredito confirmarDesembolso(long idPrestamo, String identificadorConfirmacion,
            boolean confirmoEntregaEfectivo, long idUsuarioCajero) throws SQLException {
        if (identificadorConfirmacion == null || identificadorConfirmacion.trim().isEmpty()) {
            throw new NegocioException("Falta la clave de confirmación del desembolso.");
        }
        // Decisión 8 del plan de E6: la casilla es obligatoria y se valida también en el servidor.
        if (!confirmoEntregaEfectivo) {
            throw new NegocioException("Debe verificar la cédula del cliente y confirmar que le entregó el efectivo.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            PrestamoCredito prestamo = null;
            try {
                prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                if (desembolsoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion) != null) {
                    // Ya se registró con esta misma clave (doble clic, F5, reintento): no se repite.
                    conexion.commit();
                    return prestamo;
                }

                if (prestamo.getEstadoPrestamo() != EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
                    // A.8 (consolidación, pedido de Joel): carrera de dos pestañas -- advertencia,
                    // no error, mismo criterio que SolicitudYaDecididaException (RF09/RF10).
                    throw new PrestamoNoPendienteDesembolsoException("Este préstamo ya no está pendiente de desembolso.");
                }
                BigDecimal montoNeto = prestamo.getMontoAprobado();
                SesionCaja sesion = asegurarCondicionesDesembolso(conexion, idPrestamo, montoNeto);

                // Bloque 7/E10 (D-130, pedido de Joel): el número de comprobante YA se asignó al
                // imprimir "para firma" (generarComprobantePreDesembolso), antes de confirmar --
                // acá solo se reutiliza, nunca se genera uno nuevo (D-93/D-94, E8, quedó
                // reemplazado: antes se asignaba recién acá).
                if (prestamo.getNumeroComprobanteDesembolso() == null) {
                    throw new NegocioException("Imprima el comprobante de desembolso (para firma) antes de confirmar.");
                }
                String numeroComprobante = prestamo.getNumeroComprobanteDesembolso();

                Desembolso desembolso = new Desembolso();
                desembolso.setIdentificadorConfirmacion(identificadorConfirmacion);
                desembolso.setIdPrestamo(idPrestamo);
                desembolso.setIdSesionCaja(sesion.getIdSesionCaja());
                desembolso.setMontoAprobadoSnapshot(prestamo.getMontoAprobado());
                desembolso.setMontoNetoEntregado(montoNeto);
                desembolso.setDesembolsadoPor(idUsuarioCajero);
                desembolso.setNumeroComprobante(numeroComprobante);
                long idDesembolso = desembolsoDAO.insertar(conexion, desembolso);

                MovimientoCaja movimiento = new MovimientoCaja();
                movimiento.setIdSesionCaja(sesion.getIdSesionCaja());
                movimiento.setTipo(TipoMovimientoCaja.DESEMBOLSO);
                movimiento.setMonto(montoNeto);
                movimiento.setSigno(-1);
                movimiento.setReferenciaTipo("DESEMBOLSO");
                movimiento.setIdReferencia(idDesembolso);
                movimiento.setRegistradoPor(idUsuarioCajero);
                movimiento.setObservacion("Desembolso de " + prestamo.getIdentificadorOperacion() + ".");
                movimientoCajaDAO.insertar(conexion, movimiento);

                // RF14 (pedido de Joel, 11/10/2026): carrera de dos pestañas -- si alguien anuló
                // el préstamo mientras se armaba este desembolso (desembolsoDAO.insertar y el
                // movimiento de arriba ya están en esta misma transacción, todavía sin comitear),
                // el UPDATE condicionado no actualiza nada; se arma el mensaje con quién y cuándo
                // y se corta acá -- el catch de abajo hace rollback de TODO (desembolso,
                // movimiento de caja, nada queda), ningún comprobante se emite.
                if (!prestamoDAO.marcarDesembolsado(conexion, idPrestamo, Reloj.hoy())) {
                    throw construirErrorPrestamoYaAnulado(conexion, idPrestamo);
                }

                servicioAuditoria.registrar(conexion, idUsuarioCajero, "DESEMBOLSAR", "PRESTAMO", idPrestamo,
                        "Desembolsó " + FormateadorNumeros.comoGuaranies(montoNeto) + " de "
                                + prestamo.getIdentificadorOperacion() + ".");
                conexion.commit();
                return prestamo;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                // Carrera real (dos clics simultáneos con la misma clave, ambos pasaron el chequeo
                // de arriba antes de que el primero confirmara): la restricción UNIQUE de
                // identificador_confirmacion la frena acá. Se trata como éxito idempotente, no
                // como error (decisión 10 del plan de E6, §14.3).
                if ("23505".equals(error.getSQLState())) {
                    return prestamo;
                }
                throw error;
            }
        }
    }

    /**
     * Consolidación RF16/RF17 (pedido de Joel): las 4 condiciones de RF16 (checklist completo,
     * documentos vigentes, caja abierta y de hoy, saldo suficiente) en un solo lugar -- antes
     * solo las revalidaba {@code confirmarDesembolso}; "Imprimir comprobante para firma" las
     * ignoraba y generaba el PDF (y el N.º de comprobante) igual, aunque faltara alguna.
     * Devuelve la sesión de caja abierta, que {@code confirmarDesembolso} necesita después.
     */
    private SesionCaja asegurarCondicionesDesembolso(Connection conexion, long idPrestamo, BigDecimal montoAprobado)
            throws SQLException {
        if (!servicioFormalizacion
                .esChecklistCompleto(servicioFormalizacion.obtenerChecklist(conexion, idPrestamo))) {
            throw new NegocioException("Faltan documentos firmados; complete el checklist antes de desembolsar.");
        }
        DocumentoExpediente contratoGenerado = obtenerContratoGeneradoVigente(conexion, idPrestamo);
        int diasValidez = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_validez_documentos");
        if (!validarDocumentosVigentes(contratoGenerado == null ? null : contratoGenerado.getFechaDocumento(),
                diasValidez)) {
            throw new NegocioException("Los documentos generados vencieron (más de " + diasValidez
                    + " días sin desembolsar); regenérelos antes de desembolsar.");
        }
        SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
        if (sesion == null) {
            throw new NegocioException("No hay una caja abierta.");
        }
        // Duplicaciones y parches (Commit 1): el mensaje y la excepción salen de ServicioCaja,
        // único lugar (antes se reconstruían a mano acá).
        servicioCaja.validarSesionDeHoy(sesion);
        BigDecimal saldoTeorico = sesion.getSaldoInicial()
                .add(movimientoCajaDAO.sumarMovimientos(conexion, sesion.getIdSesionCaja()));
        if (saldoTeorico.compareTo(montoAprobado) < 0) {
            throw new NegocioException(construirMensajeSaldoInsuficiente(saldoTeorico, montoAprobado));
        }
        return sesion;
    }

    // =====================================================================
    // Bloque 7/E10 (D-130, pedido de Joel, prioridad máxima): comprobante de desembolso impreso
    // y firmado ANTES de entregar el efectivo. El N° se asigna la primera vez que se pide
    // "Imprimir comprobante para firma" -- con el préstamo todavía PENDIENTE_DESEMBOLSO, antes
    // de que exista ninguna fila en desembolsos (confirmarDesembolso lo exige y lo reutiliza,
    // nunca genera uno nuevo). Reemplaza el diseño anterior (D-93/D-94, E8), que asignaba el
    // número recién al confirmar. Cualquier impresión después de la primera (antes o después de
    // confirmar) se regenera al vuelo (nunca se cachea un PDF viejo) y queda marcada
    // "REIMPRESIÓN" con fecha/hora/usuario, registrada en Auditoría -- por eso ya no usa el
    // patrón "generar al primer pedido y guardar" de ServicioCobranza.
    // =====================================================================

    /** E8 (Bloque 2): para que la ficha decida si muestra el botón "Reimprimir comprobante". */
    public boolean tieneComprobanteConfirmado(Connection conexion, long idPrestamo) throws SQLException {
        return desembolsoDAO.obtenerConfirmadoPorPrestamo(conexion, idPrestamo) != null;
    }

    /** E8 (Bloque 2, corrección de Joel): el desembolso ya confirmado de este préstamo, para
     * mostrar "Desembolsado el ..." en vez de repetir las condiciones de un desembolso pendiente. */
    public Desembolso obtenerConfirmadoPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        return desembolsoDAO.obtenerConfirmadoPorPrestamo(conexion, idPrestamo);
    }

    /**
     * D-130: se llama con el préstamo PENDIENTE_DESEMBOLSO, al apretar "Imprimir comprobante
     * para firma". Asigna el N° correlativo si todavía no tiene (primera vez); si ya lo tiene,
     * es una reimpresión (ej. se atascó la impresora antes de la firma) y el PDF sale marcado.
     */
    public byte[] generarComprobantePreDesembolso(long idPrestamo, long idUsuarioCajero) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                if (prestamo.getEstadoPrestamo() != EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
                    // A.8 (consolidación, pedido de Joel): carrera de dos pestañas -- advertencia,
                    // no error, mismo criterio que SolicitudYaDecididaException (RF09/RF10).
                    throw new PrestamoNoPendienteDesembolsoException("Este préstamo ya no está pendiente de desembolso.");
                }
                // A.5 (consolidación, pedido de Joel): las 4 condiciones también acá -- antes solo
                // se exigían al confirmar, y se podía imprimir "para firma" (por URL directa
                // incluida) sin cumplirlas.
                asegurarCondicionesDesembolso(conexion, idPrestamo, prestamo.getMontoAprobado());
                boolean esReimpresion = prestamo.getNumeroComprobanteDesembolso() != null;
                if (!esReimpresion) {
                    int anio = Reloj.hoy().getYear();
                    long secuencia = numeradorDAO.siguienteValor(conexion, "DESEMBOLSO", anio);
                    String numero = String.format("DES-%d-%06d", anio, secuencia);
                    prestamoDAO.actualizarNumeroComprobanteDesembolso(conexion, idPrestamo, numero);
                    prestamo.setNumeroComprobanteDesembolso(numero);
                }
                SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
                Usuario cajero = servicioUsuarios.buscarPorId(conexion, idUsuarioCajero);
                Usuario solicitante = cajero;
                String marcaReimpresion = esReimpresion ? construirMarcaReimpresion(solicitante) : null;
                byte[] pdf = generarPdfComprobante(conexion, prestamo, sesion, cajero, Reloj.ahora(), marcaReimpresion);
                registrarImpresionComprobante(conexion, idPrestamo, idUsuarioCajero, esReimpresion);
                conexion.commit();
                return pdf;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * D-130: reimpresión desde la ficha del desembolso ya confirmado, desde el arqueo de caja o
     * desde la ficha del préstamo -- siempre después de la impresión original "para firma", así
     * que siempre queda marcada. Caja/sesión y cajera son los de la operación REAL (del
     * desembolso confirmado), no los de quien reimprime -- eso solo identifica la marca.
     */
    public byte[] obtenerPdfComprobanteDesembolso(long idPrestamo, long idUsuarioSolicitante) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Desembolso desembolso = desembolsoDAO.obtenerConfirmadoPorPrestamo(conexion, idPrestamo);
                if (desembolso == null) {
                    throw new NegocioException("Este préstamo no tiene comprobante de desembolso registrado.");
                }
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                SesionCaja sesion = sesionCajaDAO.obtenerPorId(conexion, desembolso.getIdSesionCaja());
                Usuario cajero = servicioUsuarios.buscarPorId(conexion, desembolso.getDesembolsadoPor());
                Usuario solicitante = servicioUsuarios.buscarPorId(conexion, idUsuarioSolicitante);
                String marcaReimpresion = construirMarcaReimpresion(solicitante);
                byte[] pdf = generarPdfComprobante(conexion, prestamo, sesion, cajero,
                        desembolso.getFechaHoraDesembolso(), marcaReimpresion);
                registrarImpresionComprobante(conexion, idPrestamo, idUsuarioSolicitante, true);
                conexion.commit();
                return pdf;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private String construirMarcaReimpresion(Usuario usuario) {
        return "Reimpreso por " + (usuario == null ? "-" : usuario.getNombres() + " " + usuario.getApellidos())
                + " el " + FormateadorFechas.comoDdMmAaaaHhMm(Reloj.ahora()) + ".";
    }

    private void registrarImpresionComprobante(Connection conexion, long idPrestamo, long idUsuario,
            boolean esReimpresion) throws SQLException {
        String operacion = esReimpresion ? "REIMPRIMIR_COMPROBANTE_DESEMBOLSO" : "IMPRIMIR_COMPROBANTE_DESEMBOLSO";
        servicioAuditoria.registrar(conexion, idUsuario, operacion, "PRESTAMO", idPrestamo,
                (esReimpresion ? "Reimprimió" : "Imprimió") + " el comprobante de desembolso.");
    }

    /** D-130: tipo de documento para el escaneo firmado (opcional, se sube después de confirmar). */
    public long obtenerIdTipoComprobanteFirmado(Connection conexion) throws SQLException {
        return tipoDocumentoDAO.obtenerPorCodigo(conexion, "COMPROBANTE_DESEMBOLSO_FIRMADO").getIdTipoDocumento();
    }

    /** D-130: el escaneo firmado vigente, o null si todavía no se cargó. */
    public DocumentoExpediente obtenerComprobanteDesembolsoFirmado(Connection conexion, long idPrestamo)
            throws SQLException {
        TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, "COMPROBANTE_DESEMBOLSO_FIRMADO");
        return documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo, tipo.getIdTipoDocumento());
    }

    /**
     * D-130: contenido del comprobante según lo pedido por Joel -- cabecera con datos de la
     * empresa, N° correlativo, caja/sesión y cajera, datos del cliente, detalle de la operación
     * (préstamo, referencia a contrato y pagaré, monto, deducciones "sin comisiones ni gastos",
     * forma de entrega, plazo/cuota/TEA/CFT, primer vencimiento) y pie con la leyenda de recibido
     * conforme, firma/aclaración/C.I. del cliente, firma de la cajera, "documento interno -- no
     * válido como comprobante fiscal" y dos ejemplares (ORIGINAL -- ver <title>, COPIA -- ver
     * <summary> del .jrxml, en dos páginas). Todo ya formateado en Java (REGLAS_DESARROLLO.md §6): no lleva
     * timbrado ni IVA -- no es un comprobante de venta (el capital no es una venta; el IVA sobre
     * intereses ya se factura en cada cuota, aparte).
     */
    private byte[] generarPdfComprobante(Connection conexion, PrestamoCredito prestamo, SesionCaja sesion,
            Usuario cajero, java.time.LocalDateTime fechaHoraOperacion, String marcaReimpresion) throws SQLException {
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        LocalDate fechaOperacion = fechaHoraOperacion.toLocalDate();
        BigDecimal montoNeto = prestamo.getMontoAprobado();

        Map<String, Object> parametros = new HashMap<>();
        // Punto 16b (pedido de Joel): si no hay versión vigente en la fecha de la operación, usa
        // la más antigua disponible en vez de rechazar la impresión/reimpresión.
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fechaOperacion));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fechaOperacion));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fechaOperacion));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fechaOperacion));
        // Lote 2, L2-A Parte 2 (14/10/2026, pedido de Joel, autorizado): mismo patrón D-94 que
        // RAZON_SOCIAL/RUC/DOMICILIO/TELEFONO de arriba.
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fechaOperacion));
        parametros.put("NUMERO_COMPROBANTE", prestamo.getNumeroComprobanteDesembolso());
        parametros.put("FECHA_HORA", FormateadorFechas.comoDdMmAaaaHhMm(fechaHoraOperacion));
        parametros.put("CAJA_SESION", sesion == null ? "-" : "Sesión N.º " + sesion.getIdSesionCaja()
                + " (abierta " + FormateadorFechas.comoDdMmAaaa(sesion.getFechaHoraApertura().toLocalDate()) + ")");
        parametros.put("CAJERA_NOMBRE", cajero == null ? "-" : cajero.getNombres() + " " + cajero.getApellidos());
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("NUMERO_CONTRATO", prestamo.getNumeroContrato() == null ? "-" : prestamo.getNumeroContrato());
        parametros.put("NUMERO_PAGARE", prestamo.getNumeroPagare() == null ? "-" : prestamo.getNumeroPagare());
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        // B.15 (consolidación, pedido de Joel): sin la referencia coloquial en el documento legal.
        // Normalización de personas, grupo Domicilio (16/10/2026): texto armado al vuelo (D-94:
        // solo corre al generar el comprobante, nunca sobre uno ya emitido).
        String textoDomicilioCliente = servicioClientes.obtenerTextoDomicilio(conexion, cliente.getIdPersona());
        parametros.put("CLIENTE_DOMICILIO", textoDomicilioCliente == null ? "-"
                : ServicioClientes.domicilioSinReferencia(textoDomicilioCliente));
        parametros.put("MONTO_APROBADO", FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()));
        parametros.put("MONTO_DEDUCCIONES", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO) + " (sin comisiones ni gastos)");
        parametros.put("MONTO_NETO", FormateadorNumeros.comoGuaranies(montoNeto));
        parametros.put("MONTO_NETO_LETRAS", ConversorNumerosALetras.comoLetrasConGuaranies(montoNeto).toUpperCase(new Locale("es")));
        parametros.put("FORMA_ENTREGA", "Efectivo");
        parametros.put("PLAZO_MESES", prestamo.getPlazoMeses());
        // Corrección de Joel (D-138): la cuota 1.ª CON IVA (no cuotaContractual, que es sin IVA)
        // -- mismo criterio ya aplicado en el contrato (ServicioFormalizacion.CUOTA_1). El cliente
        // firma acá el monto que realmente va a pagar, no uno distinto que nunca ve después.
        java.util.List<py.com.capitalcredito.sgcpc.modelos.Cuota> cuotas =
                servicioFormalizacion.listarCuotasVigentes(conexion, prestamo.getIdPrestamo());
        BigDecimal cuota1ConIva = cuotas.isEmpty() ? prestamo.getCuotaContractual() : cuotas.get(0).getTotalProgramado();
        BigDecimal cuotaUltimaConIva = cuotas.isEmpty() ? prestamo.getCuotaContractual()
                : cuotas.get(cuotas.size() - 1).getTotalProgramado();
        parametros.put("CUOTA_1", FormateadorNumeros.comoGuaranies(cuota1ConIva));
        parametros.put("CUOTA_ULTIMA", FormateadorNumeros.comoGuaranies(cuotaUltimaConIva));
        parametros.put("CUOTA_ES_DECRECIENTE", cuota1ConIva.compareTo(cuotaUltimaConIva) != 0);
        parametros.put("TEA", FormateadorNumeros.comoPorcentaje(prestamo.getTasaTea()));
        parametros.put("CFT", prestamo.getCft() == null ? "-" : FormateadorNumeros.comoPorcentaje(prestamo.getCft()));
        // C12 (hallazgo del recorrido, decisión de Joel del 29/10/2026): la fecha EFECTIVA de la
        // cuota 1, la misma que ya muestran el contrato y el plan que el cliente firmó -- antes
        // salía prestamo.getPrimeraFechaVencimiento(), que es la ELEGIDA al formalizar, y con una
        // elegida en día no hábil este comprobante contradecía a los otros dos documentos. Mismo
        // criterio que D-138 (la cuota 1 con IVA, diez líneas más arriba) y un solo dueño del
        // cálculo: ServicioFormalizacion.primeraFechaVencimientoEfectiva, sobre las cuotas que
        // esta misma clase ya tenía cargadas.
        parametros.put("PRIMER_VENCIMIENTO", FormateadorFechas.comoDdMmAaaa(
                servicioFormalizacion.primeraFechaVencimientoEfectiva(cuotas, prestamo)));
        parametros.put("USUARIO_EMISOR", cajero == null ? "-" : cajero.getNombres() + " " + cajero.getApellidos());
        // Consolidación RF17/RF18 (pedido de Joel): FECHA_EMISION es la fecha/hora de la operación
        // real (original), nunca la del momento de la reimpresión -- eso va aparte, en
        // MARCA_REIMPRESION ("Reimpreso por ... el ...", construirMarcaReimpresion).
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(fechaHoraOperacion));
        parametros.put("MARCA_REIMPRESION", marcaReimpresion == null ? "" : marcaReimpresion);

        return llenarYExportar("comprobante_desembolso.jasper", parametros, new net.sf.jasperreports.engine.JREmptyDataSource(1));
    }

    private byte[] llenarYExportar(String nombreArchivoJasper, Map<String, Object> parametros,
            net.sf.jasperreports.engine.JRDataSource datasource) {
        try (InputStream entrada = getClass().getResourceAsStream("/reportes/" + nombreArchivoJasper)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró \"" + nombreArchivoJasper + "\" en el classpath.");
            }
            parametros.put("LOGO", GeneradorInformesPdf.logoComoBytes());
            JasperPrint impreso = JasperFillManager.fillReport(entrada, parametros, datasource);
            return JasperExportManager.exportReportToPdf(impreso);
        } catch (JRException | IOException error) {
            throw new IllegalStateException("No se pudo generar el PDF (" + nombreArchivoJasper + ").", error);
        }
    }
}
