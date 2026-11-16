package py.com.capitalcredito.sgcpc.servicios;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.ArchivoBinarioDAO;
import py.com.capitalcredito.sgcpc.dao.ArchivoComprobanteDAO;
import py.com.capitalcredito.sgcpc.dao.DesembolsoDAO;
import py.com.capitalcredito.sgcpc.dao.FacturaDAO;
import py.com.capitalcredito.sgcpc.dao.LiquidacionAnticipadaDAO;
import py.com.capitalcredito.sgcpc.dao.MovimientoCajaDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.ReversionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.SesionCajaDAO;
import py.com.capitalcredito.sgcpc.dto.FilaMovimientoArqueo;
import py.com.capitalcredito.sgcpc.dto.FilaMovimientoCaja;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoFactura;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSesionCaja;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.excepciones.CajaDeOtroDiaException;
import py.com.capitalcredito.sgcpc.excepciones.CajaYaCerradaException;
import py.com.capitalcredito.sgcpc.excepciones.MotivoDiferenciaInsuficienteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.LiquidacionAnticipada;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P07, caja (Doc. 10 §10.10: ControladorCajaServlet / ServicioCaja). Una sola caja ABIERTA en
 * todo el sistema (índice único parcial ux_sesiones_caja_una_abierta, bd/02_indices.sql); una
 * caja CERRADA es histórica e inmutable (RN-14) -- no hay ningún método que la modifique salvo
 * marcarRevisadaPorGerente, que solo anota id_gerente_revisor (decisión 4 del plan de E6).
 */
public class ServicioCaja {

    private final SesionCajaDAO sesionCajaDAO = new SesionCajaDAO();
    private final MovimientoCajaDAO movimientoCajaDAO = new MovimientoCajaDAO();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final FacturaDAO facturaDAO = new FacturaDAO();
    private final LiquidacionAnticipadaDAO liquidacionAnticipadaDAO = new LiquidacionAnticipadaDAO();
    private final DesembolsoDAO desembolsoDAO = new DesembolsoDAO();
    private final AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();
    private final ArchivoComprobanteDAO archivoComprobanteDAO = new ArchivoComprobanteDAO();
    private final ArchivoBinarioDAO archivoBinarioDAO = new ArchivoBinarioDAO();
    private final ReversionPagoDAO reversionPagoDAO = new ReversionPagoDAO();

    public SesionCaja obtenerSesionAbierta(Connection conexion) throws SQLException {
        return sesionCajaDAO.obtenerAbierta(conexion);
    }

    /**
     * PO-06 paso 1: "el sistema propone el fondo según el total de desembolsos pendientes del
     * día". Bug de la prueba manual de E6: antes sumaba TODOS los PENDIENTE_DESEMBOLSO sin
     * exigir checklist completo, mientras que /desembolso solo lista los que sí lo tienen --
     * ahora comparte el mismo criterio con `ServicioFormalizacion.listarListosParaDesembolsar`.
     */
    public BigDecimal calcularFondoSugerido(Connection conexion) throws SQLException {
        return servicioFormalizacion.calcularFondoSugeridoDesembolso(conexion);
    }

    public BigDecimal calcularSaldoTeorico(Connection conexion, long idSesionCaja) throws SQLException {
        SesionCaja sesion = sesionCajaDAO.obtenerPorId(conexion, idSesionCaja);
        if (sesion == null) {
            throw new NegocioException("La sesión de caja no existe.");
        }
        return sesion.getSaldoInicial().add(movimientoCajaDAO.sumarMovimientos(conexion, idSesionCaja));
    }

    /** Decisión 5 del plan de E6: una caja abierta de un día anterior solo se puede cerrar. */
    public boolean esDeHoy(SesionCaja sesion) {
        return sesion.getFechaHoraApertura().toLocalDate().equals(Reloj.hoy());
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel): público para que los 3 controladores
     * (Caja/Cobranza/Desembolso) lo usen también en el aviso fijo de la ficha, no solo al
     * lanzar CajaDeOtroDiaException -- un solo texto, con la fecha, en los 3 lugares. */
    public String mensajeCajaDeOtroDia(SesionCaja sesion) {
        return "La caja del " + FormateadorFechas.comoDdMmAaaa(sesion.getFechaHoraApertura().toLocalDate()) + " sigue abierta: ciérrela y rinda antes de operar hoy.";
    }

    /**
     * Duplicaciones y parches (Commit 1): único lugar que decide si una sesión de caja NO es de
     * hoy y arma la excepción -- antes cada servicio (ServicioCaja, ServicioCobranza,
     * ServicioDesembolso) reconstruía el mismo mensaje a mano, y en ServicioCobranza llegó a
     * lanzar NegocioException genérica en vez de CajaDeOtroDiaException (el mismo caso de
     * negocio se mostraba como error en Cobranza y como advertencia en Caja/Desembolso). Pura,
     * visibilidad de paquete (no private) para que los otros servicios y el JUnit la usen sin
     * necesitar Connection.
     */
    void validarSesionDeHoy(SesionCaja sesion) {
        if (!esDeHoy(sesion)) {
            // A.8 (consolidación, pedido de Joel): bloqueo de tiempo, no error -- mismo criterio
            // que FechaFueraDePlazoException (RF11).
            throw new CajaDeOtroDiaException(mensajeCajaDeOtroDia(sesion));
        }
    }

    /** PO-06: la cajera abre con el fondo que el gerente ya le entregó en mano. */
    public long abrirCaja(long idUsuarioCajero, BigDecimal saldoInicial) throws SQLException {
        if (saldoInicial == null || saldoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("Ingrese el saldo inicial de la caja.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SesionCaja sesionExistente = sesionCajaDAO.obtenerAbierta(conexion);
                if (sesionExistente != null) {
                    // UX (Joel, incidente de la sesión 5 en la prueba manual de E6): el rechazo
                    // no puede ser silencioso -- decir desde cuándo está abierta y quién la abrió.
                    Usuario cajeroActual = servicioUsuarios.buscarPorId(conexion, sesionExistente.getIdCajero());
                    // A.8 (consolidación, pedido de Joel): carrera de dos pestañas al abrir --
                    // advertencia, no error, mismo criterio que CajaYaCerradaException (RF19).
                    throw new py.com.capitalcredito.sgcpc.excepciones.CajaYaAbiertaException(
                            "Ya hay una caja abierta desde el "
                            + FormateadorFechas.comoDdMmAaaaHhMm(sesionExistente.getFechaHoraApertura())
                            + " (" + cajeroActual.getNombreUsuario() + "). No se abrió una nueva.");
                }
                SesionCaja sesion = new SesionCaja();
                sesion.setIdCajero(idUsuarioCajero);
                sesion.setSaldoInicial(saldoInicial);
                long idSesion = sesionCajaDAO.insertar(conexion, sesion);
                servicioAuditoria.registrar(conexion, idUsuarioCajero, "ABRIR_CAJA", "SESION_CAJA", idSesion,
                        // D-86: comoGuaranies ya termina en "Gs." -- agregar otro "." duplica el punto.
                        "Abrió la caja con saldo inicial " + FormateadorNumeros.comoGuaranies(saldoInicial));
                conexion.commit();
                return idSesion;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Decisión 2 del plan de E6 (separación de funciones): aunque ENTREGAR_RECIBIR_EFECTIVO_CAJA
     * da TOTAL a gerente y cajera por igual, la entrega de efectivo la registra solo quien
     * efectivamente la entrega -- el gerente. La cajera nunca registra dinero entrando a su
     * propia caja; solo lo ve en la lista de movimientos.
     */
    public void registrarEntregaGerente(long idUsuarioGerente, PerfilUsuario perfilUsuario, BigDecimal monto,
            String observacion) throws SQLException {
        // D7 (pedido de Joel): el administrador también puede registrar una entrega de efectivo.
        if (perfilUsuario != PerfilUsuario.GERENTE_ADMINISTRADOR && perfilUsuario != PerfilUsuario.ADMINISTRADOR) {
            throw new NegocioException("Solo el gerente puede registrar una entrega de efectivo.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingrese un monto de entrega mayor que cero.");
        }
        observacion = NormalizadorTexto.normalizarTextoLibre(observacion);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
                if (sesion == null) {
                    throw new NegocioException("No hay una caja abierta.");
                }
                validarSesionDeHoy(sesion);
                MovimientoCaja movimiento = new MovimientoCaja();
                movimiento.setIdSesionCaja(sesion.getIdSesionCaja());
                movimiento.setTipo(TipoMovimientoCaja.ENTREGA_GERENTE);
                movimiento.setMonto(monto);
                movimiento.setSigno(1);
                movimiento.setReferenciaTipo("SESION_CAJA");
                movimiento.setIdReferencia(sesion.getIdSesionCaja());
                movimiento.setRegistradoPor(idUsuarioGerente);
                movimiento.setObservacion(observacion);
                long idMovimiento = movimientoCajaDAO.insertar(conexion, movimiento);
                servicioAuditoria.registrar(conexion, idUsuarioGerente, "ENTREGAR_EFECTIVO_CAJA", "MOVIMIENTO_CAJA",
                        idMovimiento, "Entregó " + FormateadorNumeros.comoGuaranies(monto) + " a la caja.");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** RF19 (pedido de Joel, 11/10/2026): mínimo de caracteres reales (ya sin espacios al
     * inicio/final -- el llamador normaliza antes de validar) para que el motivo de una
     * diferencia no sea ".", "..." o cualquier relleno sin contenido. */
    static final int MOTIVO_DIFERENCIA_LARGO_MINIMO = 10;

    /** RN-23: "toda diferencia exige motivo" -- acá con tolerancia (caja.diferencia_tolerada,
     * hoy 0) y, desde el 11/10/2026, un mínimo de caracteres reales (no alcanza con que el
     * motivo exista, tiene que tener contenido). Pura. */
    public void validarMotivoDiferencia(BigDecimal diferencia, BigDecimal tolerancia, String motivo) {
        if (diferencia.abs().compareTo(tolerancia) > 0
                && (motivo == null || motivo.length() < MOTIVO_DIFERENCIA_LARGO_MINIMO)) {
            throw new MotivoDiferenciaInsuficienteException(construirMensajeMotivoInsuficiente(diferencia));
        }
    }

    /** RF19 (pedido de Joel, 11/10/2026): mensaje puro, separado de la validación para poder
     * probarlo con JUnit. "Sobra"/"Falta" según el signo, mismo criterio que
     * formatearDiferenciaArqueo (más abajo), sin duplicar el texto completo porque este mensaje
     * no lleva el monto con signo, solo la etiqueta. */
    String construirMensajeMotivoInsuficiente(BigDecimal diferencia) {
        String etiqueta = diferencia.signum() > 0 ? "Sobra" : "Falta";
        return "Hay una diferencia de " + FormateadorNumeros.comoGuaranies(diferencia.abs()) + " (" + etiqueta
                + "). Explique el motivo con al menos " + MOTIVO_DIFERENCIA_LARGO_MINIMO + " caracteres.";
    }

    /**
     * Datos del cierre (Doc. 10 §10.10) para que el controlador arme el mensaje de pantalla --
     * el servicio no redacta texto de pantalla, ajuste de Joel tras la prueba manual de E6.
     */
    public static final class ResultadoCierreCaja {
        private final BigDecimal saldoTeorico;
        private final BigDecimal saldoFisico;
        private final BigDecimal diferencia;

        public ResultadoCierreCaja(BigDecimal saldoTeorico, BigDecimal saldoFisico, BigDecimal diferencia) {
            this.saldoTeorico = saldoTeorico;
            this.saldoFisico = saldoFisico;
            this.diferencia = diferencia;
        }

        public BigDecimal getSaldoTeorico() {
            return saldoTeorico;
        }

        public BigDecimal getSaldoFisico() {
            return saldoFisico;
        }

        public BigDecimal getDiferencia() {
            return diferencia;
        }
    }

    /** PO-12: arqueo y rendición en un solo paso (decisión 3 del plan de E6). */
    public ResultadoCierreCaja cerrarCaja(long idUsuarioCajero, BigDecimal saldoFisico, String motivoDiferencia)
            throws SQLException {
        if (saldoFisico == null || saldoFisico.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("Ingrese el saldo físico contado.");
        }
        motivoDiferencia = NormalizadorTexto.normalizarTextoLibre(motivoDiferencia);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // RF19 (pedido de Joel, 11/10/2026): tope razonable contra errores de tipeo
                // (ceros de más) -- parámetro, nunca fijo en el código (REGLAS_DESARROLLO.md §5).
                int montoMaximoArqueo = servicioParametros.obtenerValorEntero(conexion, "caja.monto_maximo_arqueo");
                if (saldoFisico.compareTo(BigDecimal.valueOf(montoMaximoArqueo)) > 0) {
                    throw new NegocioException("El efectivo contado (" + FormateadorNumeros.comoGuaranies(saldoFisico)
                            + ") supera el máximo razonable (" + FormateadorNumeros.comoGuaranies(
                                    BigDecimal.valueOf(montoMaximoArqueo)) + "). Revise si tipeó ceros de más.");
                }
                SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
                if (sesion == null) {
                    throw new NegocioException("No hay una caja abierta.");
                }
                BigDecimal saldoTeorico = sesion.getSaldoInicial()
                        .add(movimientoCajaDAO.sumarMovimientos(conexion, sesion.getIdSesionCaja()));
                BigDecimal diferencia = saldoFisico.subtract(saldoTeorico);
                BigDecimal tolerancia = servicioParametros.obtenerValorDecimal(conexion, "caja.diferencia_tolerada");
                validarMotivoDiferencia(diferencia, tolerancia, motivoDiferencia);

                // RF19 (pedido de Joel, 11/10/2026): UPDATE condicionado a ABIERTA -- si otra
                // transacción ya cerró esta misma sesión en la angosta ventana entre el
                // obtenerAbierta de arriba y este UPDATE (carrera de dos pestañas), no se inserta
                // ni la rendición ni la auditoría: se tira el error acá mismo y el catch de abajo
                // hace rollback de todo (mismo patrón que marcarDecidida, RF09).
                if (!sesionCajaDAO.marcarCerrada(conexion, sesion.getIdSesionCaja(), saldoTeorico, saldoFisico,
                        diferencia, motivoDiferencia)) {
                    throw construirErrorCajaYaCerrada(conexion, sesion.getIdSesionCaja());
                }

                MovimientoCaja rendicion = new MovimientoCaja();
                rendicion.setIdSesionCaja(sesion.getIdSesionCaja());
                rendicion.setTipo(TipoMovimientoCaja.RENDICION_GERENTE);
                rendicion.setMonto(saldoFisico);
                rendicion.setSigno(-1);
                rendicion.setReferenciaTipo("SESION_CAJA");
                rendicion.setIdReferencia(sesion.getIdSesionCaja());
                rendicion.setRegistradoPor(idUsuarioCajero);
                rendicion.setObservacion("Rendición de cierre.");
                movimientoCajaDAO.insertar(conexion, rendicion);

                servicioAuditoria.registrar(conexion, idUsuarioCajero, "CERRAR_CAJA", "SESION_CAJA",
                        sesion.getIdSesionCaja(),
                        "Cerró la caja. Teórico " + FormateadorNumeros.comoGuaranies(saldoTeorico) + ", físico "
                                + FormateadorNumeros.comoGuaranies(saldoFisico) + ", diferencia "
                                + FormateadorNumeros.comoGuaranies(diferencia)
                                + (motivoDiferencia == null || motivoDiferencia.isEmpty() ? ""
                                        : ". Motivo: " + motivoDiferencia));
                conexion.commit();
                return new ResultadoCierreCaja(saldoTeorico, saldoFisico, diferencia);
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * RF19 (pedido de Joel, 11/10/2026): relee la sesión ya cerrada por la otra transacción y
     * busca el movimiento RENDICION_GERENTE de esa sesión (su `registrado_por` es quién cerró --
     * `sesiones_caja` no tiene una columna propia de "cerrado por", solo `id_cajero` de la
     * apertura) para armar el mensaje con quién y cuándo.
     */
    private CajaYaCerradaException construirErrorCajaYaCerrada(Connection conexion, long idSesionCaja)
            throws SQLException {
        SesionCaja actual = sesionCajaDAO.obtenerPorId(conexion, idSesionCaja);
        MovimientoCaja rendicion = buscarRendicion(conexion, idSesionCaja);
        Usuario quienCerro = rendicion == null ? null : servicioUsuarios.buscarPorId(conexion, rendicion.getRegistradoPor());
        return new CajaYaCerradaException(construirMensajeCajaYaCerrada(actual, quienCerro));
    }

    private MovimientoCaja buscarRendicion(Connection conexion, long idSesionCaja) throws SQLException {
        for (MovimientoCaja movimiento : movimientoCajaDAO.listarPorSesion(conexion, idSesionCaja)) {
            if (movimiento.getTipo() == TipoMovimientoCaja.RENDICION_GERENTE) {
                return movimiento;
            }
        }
        return null;
    }

    /** Pura: "Esta caja ya fue cerrada por NOMBRE el FECHA HORA. Actualice la página." (texto
     * exacto pedido por Joel, 11/10/2026). Separada de construirErrorCajaYaCerrada (que necesita
     * Connection) para poder probarla con JUnit sin base de datos. */
    String construirMensajeCajaYaCerrada(SesionCaja actual, Usuario quienCerro) {
        String nombre = quienCerro == null ? "otro usuario" : quienCerro.getNombres() + " " + quienCerro.getApellidos();
        return "Esta caja ya fue cerrada por " + nombre + " el "
                + FormateadorFechas.comoDdMmAaaaHhMm(actual.getFechaHoraCierre()) + ". Actualice la página.";
    }

    /**
     * Decisión 4 del plan de E6: el gerente marca revisada una diferencia con una observación
     * obligatoria (qué se hizo con la diferencia). No hay columna para ese texto en
     * sesiones_caja y no se toca el esquema: la observación queda en el detalle de la auditoría;
     * solo id_gerente_revisor se graba en la tabla, una sola vez.
     */
    public void marcarDiferenciaRevisada(long idSesionCaja, long idUsuarioGerente, String observacion)
            throws SQLException {
        observacion = NormalizadorTexto.normalizarTextoLibre(observacion);
        if (observacion == null || observacion.isEmpty()) {
            throw new NegocioException("Ingrese qué se hizo con la diferencia.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SesionCaja sesion = sesionCajaDAO.obtenerPorId(conexion, idSesionCaja);
                if (sesion == null) {
                    throw new NegocioException("La sesión de caja no existe.");
                }
                if (sesion.getEstadoSesion() != EstadoSesionCaja.CERRADA) {
                    throw new NegocioException("Solo se revisa una caja ya cerrada.");
                }
                if (sesion.getDiferenciaArqueo() == null || sesion.getDiferenciaArqueo().compareTo(BigDecimal.ZERO) == 0) {
                    throw new NegocioException("Esta caja no tiene diferencia para revisar.");
                }
                if (sesion.getIdGerenteRevisor() != null) {
                    // A.8 (consolidación, pedido de Joel): carrera de dos pestañas -- advertencia,
                    // no error, mismo criterio que CajaYaCerradaException (RF19).
                    throw new py.com.capitalcredito.sgcpc.excepciones.DiferenciaYaRevisadaException(
                            "Esta diferencia ya fue revisada.");
                }
                sesionCajaDAO.marcarRevisadaPorGerente(conexion, idSesionCaja, idUsuarioGerente);
                servicioAuditoria.registrar(conexion, idUsuarioGerente, "REVISAR_DIFERENCIA_CAJA", "SESION_CAJA",
                        idSesionCaja, "Revisó la diferencia de "
                                + FormateadorNumeros.comoGuaranies(sesion.getDiferenciaArqueo()) + ". " + observacion);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    public List<MovimientoCaja> listarMovimientos(Connection conexion, long idSesionCaja) throws SQLException {
        return movimientoCajaDAO.listarPorSesion(conexion, idSesionCaja);
    }

    /** D-146 (pantalla de Caja, pedido de Joel): MISMA resolución de comprobante que ya usa
     * generarPdfArqueo para el PDF del arqueo (PAGO -&gt; recibo del cobro, DESEMBOLSO -&gt;
     * comprobante de desembolso) -- acá para la columna "Comprobante" + "Reimprimir" de la
     * sesión abierta, con los mismos PDF ya guardados (nunca se regenera nada). Entregas y
     * rendiciones de gerente no tienen comprobante (null, igual que el arqueo). Punto 8 (RF25,
     * pedido de Joel, 12/10/2026): una COMPENSACION (reversión autorizada) SÍ tiene -- la
     * constancia de reversión. */
    public List<FilaMovimientoCaja> listarMovimientosConComprobante(Connection conexion, long idSesionCaja)
            throws SQLException {
        List<FilaMovimientoCaja> filas = new ArrayList<>();
        for (MovimientoCaja movimiento : listarMovimientos(conexion, idSesionCaja)) {
            String numeroComprobante = null;
            String urlReimprimir = null;
            String observacion = movimiento.getObservacion();
            if ("PAGO".equals(movimiento.getReferenciaTipo())) {
                Pago pago = pagoDAO.obtenerPorId(conexion, movimiento.getIdReferencia());
                if (pago != null) {
                    numeroComprobante = pago.getNumeroRecibo();
                    urlReimprimir = "/cobranza/" + pago.getIdPrestamo() + "/recibo/" + pago.getIdPago();
                    // Punto 9 (RF25, hallazgo de Joel, 12/10/2026): mismo aviso que el historial
                    // de cobros de la ficha del cliente (badge "REVERTIDO") -- acá, en texto,
                    // junto a la observación.
                    if (pago.getEstadoPago() == EstadoConfirmacionPago.REVERTIDO) {
                        observacion = (observacion == null || observacion.isEmpty() ? "" : observacion + " ")
                                + "(revertido)";
                    }
                }
            } else if ("DESEMBOLSO".equals(movimiento.getReferenciaTipo())) {
                Desembolso desembolso = desembolsoDAO.obtenerPorId(conexion, movimiento.getIdReferencia());
                if (desembolso != null) {
                    numeroComprobante = desembolso.getNumeroComprobante();
                    urlReimprimir = "/desembolso/" + desembolso.getIdPrestamo() + "/comprobante";
                }
            } else if ("REVERSION_PAGO".equals(movimiento.getReferenciaTipo())) {
                // Punto 8 (RF25, pedido de Joel): la fila "Compensación" también tiene
                // comprobante -- la constancia de reversión (ServicioCobranza.
                // construirNumeroConstanciaReversion, mismo número que ve la cajera/el gerente).
                ReversionPago reversion = reversionPagoDAO.obtenerPorId(conexion, movimiento.getIdReferencia());
                if (reversion != null && reversion.getFechaHoraReversion() != null) {
                    numeroComprobante = ServicioCobranza.construirNumeroConstanciaReversion(
                            reversion.getFechaHoraReversion().getYear(), reversion.getIdReversionPago());
                    Pago pagoRevertido = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
                    if (pagoRevertido != null) {
                        urlReimprimir = "/cobranza/" + pagoRevertido.getIdPrestamo() + "/reversion/"
                                + reversion.getIdReversionPago();
                    }
                }
            }
            String montoConSigno = montoConSigno(movimiento.getSigno(), movimiento.getMonto());
            filas.add(new FilaMovimientoCaja(FormateadorFechas.comoDdMmAaaaHhMm(movimiento.getFechaHora()),
                    movimiento.getTipo().getEtiqueta(), montoConSigno, observacion, numeroComprobante,
                    urlReimprimir));
        }
        return filas;
    }

    /** E8 (Bloque 2, reimpresión del arqueo): últimas sesiones CERRADAS, para ofrecer el PDF de cada una. */
    public List<SesionCaja> listarCerradasRecientes(Connection conexion, int limite) throws SQLException {
        return sesionCajaDAO.listarCerradas(conexion, limite);
    }

    /** B191 (15/11/2026): todas las cerradas, de la más nueva a la más vieja -- la pantalla pagina
     * sobre el total en vez de mostrar solo las últimas 10. */
    public List<SesionCaja> listarCerradasTodas(Connection conexion) throws SQLException {
        return sesionCajaDAO.listarCerradas(conexion);
    }

    /** E8 (Bloque 2, corrección de Joel): filtro por fecha de cierre, para llegar a una sesión que
     * las "últimas 10" no alcanzan a mostrar. */
    public List<SesionCaja> listarCerradasEntreFechas(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        return sesionCajaDAO.listarCerradasEntreFechas(conexion, desde, hasta);
    }

    public List<SesionCaja> listarConDiferenciaSinRevisar(Connection conexion) throws SQLException {
        return sesionCajaDAO.listarConDiferenciaSinRevisar(conexion);
    }

    // =====================================================================
    // D-94 (E8): arqueo y cierre de caja -- "generar al primer pedido y guardar".
    // =====================================================================

    @FunctionalInterface
    private interface GeneradorPdf {
        byte[] generar(Connection conexion) throws SQLException;
    }

    /** D-94: PDF del arqueo de una sesión ya CERRADA -- lo genera la primera vez que se pide. */
    public byte[] obtenerPdfArqueo(long idSesionCaja, long idUsuario) throws SQLException {
        return obtenerOGenerarPdf("sesiones_caja", "id_sesion_caja", "id_archivo_cierre", idSesionCaja, idUsuario,
                "arqueo_" + idSesionCaja + ".pdf", conexion -> generarPdfArqueo(conexion, idSesionCaja));
    }

    /**
     * B52 (B86, tramo 3): el monto de un movimiento de caja con su signo. <b>Un cero no lleva
     * signo</b>: antes la expresión era {@code (signo < 0 ? "-" : "") + monto} sin mirar el monto,
     * así que una rendición al gerente de cero salía <i>"−0 Gs."</i> y se leía como un error de
     * cuentas. En la base hay <b>11</b> movimientos con monto cero y signo negativo
     * ({@code RENDICION_GERENTE}), así que no era un caso teórico.
     *
     * <p>La misma expresión estaba escrita <b>dos veces</b> —la tabla de movimientos de la pantalla
     * y el detalle del arqueo en PDF—, así que las dos mostraban el defecto y arreglar solo una lo
     * habría dejado a medias. Ahora las dos llaman acá (un dato, un dueño). Pura, y de visibilidad
     * de paquete para poder probarla sin base.</p>
     *
     * @param signo el {@code signo} del movimiento (+1 entra, −1 sale)
     * @param monto el monto, nunca negativo en la base
     */
    static String montoConSigno(int signo, BigDecimal monto) {
        boolean restaDeVerdad = signo < 0 && monto != null && monto.compareTo(BigDecimal.ZERO) != 0;
        return (restaDeVerdad ? "-" : "") + FormateadorNumeros.comoGuaranies(monto);
    }

    /** Mismo patrón que ServicioCobranza/ServicioDesembolso -- ver su comentario para el detalle del lock. */
    private byte[] obtenerOGenerarPdf(String tabla, String columnaId, String columnaArchivo, long id,
            long idUsuario, String nombreArchivo, GeneradorPdf generador) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Long idArchivo = archivoComprobanteDAO.obtenerIdArchivoConLock(conexion, tabla, columnaId,
                        columnaArchivo, id);
                byte[] pdf;
                if (idArchivo != null) {
                    pdf = archivoBinarioDAO.obtenerContenido(conexion, idArchivo);
                } else {
                    pdf = generador.generar(conexion);
                    ArchivoBinario archivo = new ArchivoBinario();
                    archivo.setNombreArchivo(nombreArchivo);
                    archivo.setMimeType("application/pdf");
                    archivo.setExtension("pdf");
                    archivo.setTamanoBytes(pdf.length);
                    archivo.setHashSha256(ServicioDocumentos.calcularHashSha256(pdf));
                    archivo.setOrigenArchivo(OrigenArchivo.JASPER);
                    archivo.setCargadoPor(idUsuario);
                    long nuevoIdArchivo = archivoBinarioDAO.insertar(conexion, archivo, pdf);
                    archivoComprobanteDAO.vincular(conexion, tabla, columnaId, columnaArchivo, id, nuevoIdArchivo);
                }
                conexion.commit();
                return pdf;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** A3d (pedido de Joel): "Sobra X Gs." / "Falta X Gs." / "0 Gs." -- mismo criterio que ya usa
     * la columna "Diferencia" de /caja ("Sesiones cerradas", caja/ficha.jsp -- misma lógica, una
     * copia chica propia de cada lado, mismo criterio que el resto del proyecto con
     * formatearGs/formatearGsCierre) y que ahora también usa el PDF del arqueo, que mostraba el
     * monto sin decir si sobró o faltó. Pura -- no toca ningún cálculo, solo el texto de un
     * monto que ya está calculado y guardado. */
    /**
     * C22 (decisión de Joel, 03/11/2026): la línea de la firma del gerente en el acta de arqueo.
     *
     * <p>Con un gerente **registrado en esa sesión** (`id_gerente_revisor`, que hoy solo escribe
     * `marcarDiferenciaRevisada`) el acta lo nombra; sin ninguno, deja la línea **en blanco para
     * firmar a mano** en vez de afirmar que alguien recibió el efectivo. El sistema no tiene
     * ningún paso en el que un gerente confirme la recepción de un cierre sin diferencia, así que
     * el acta no puede decir que ocurrió (RF19 tampoco lo pide).</p>
     *
     * <p>Pura, y dueña única del texto: el `.jrxml` imprime lo que devuelve, sin prefijos
     * propios.</p>
     *
     * <p><b>Sin guiones bajos</b> (decisión de Joel, 03/11/2026, mirando la muestra): el pie ya
     * tiene su raya dibujada arriba, con "Gerente" debajo — una línea de guiones agregaba un
     * segundo espacio en blanco para una sola firma.</p>
     */
    static String lineaRecibiDelArqueo(String nombreGerenteRegistrado) {
        if (nombreGerenteRegistrado == null || nombreGerenteRegistrado.trim().isEmpty()) {
            return "Recibí (firma):";
        }
        return "Recibí: " + nombreGerenteRegistrado.trim();
    }

    /** B5 (Bloque 7/E10, pedido de Joel): paquete-visible (antes privado) para que
     * ServicioReportes.listarInformeCaja use EXACTAMENTE el mismo "Sobra/Falta" que ya muestra el
     * PDF de arqueo y caja/ficha.jsp (A3d) -- nunca una copia. */
    static String formatearDiferenciaArqueo(BigDecimal diferencia) {
        int signo = diferencia.signum();
        if (signo == 0) {
            return FormateadorNumeros.comoGuaranies(diferencia);
        }
        return (signo > 0 ? "Sobra " : "Falta ") + FormateadorNumeros.comoGuaranies(diferencia.abs());
    }

    private byte[] generarPdfArqueo(Connection conexion, long idSesionCaja) throws SQLException {
        SesionCaja sesion = sesionCajaDAO.obtenerPorId(conexion, idSesionCaja);
        Usuario cajera = servicioUsuarios.buscarPorId(conexion, sesion.getIdCajero());
        LocalDate fechaCierre = sesion.getFechaHoraCierre().toLocalDate();
        // C22 (decisión de Joel, 03/11/2026): el acta no afirma una recepción que nadie hizo.
        // Antes imprimía "Recibí: [caja.gerente_predeterminado]" siempre que no hubiera un
        // gerente registrado contra ESTA sesión -- que es el caso de toda sesión sin diferencia
        // (la gran mayoría) y también de una diferencia todavía sin revisar: el papel firmaba, en
        // nombre de un gerente concreto, la recepción de un efectivo que ese gerente podía no
        // haber contado, y con un faltante pendiente sugería además que ya lo había aceptado.
        // Ahora el nombre sale SOLO del único hecho que el sistema registra de verdad
        // (id_gerente_revisor, puesto por marcarDiferenciaRevisada); si no hay ninguno, va la
        // línea en blanco para firmar a mano. Sin paso nuevo ni permiso nuevo.
        String nombreGerenteRegistrado = null;
        if (sesion.getIdGerenteRevisor() != null) {
            Usuario gerenteRevisor = servicioUsuarios.buscarPorId(conexion, sesion.getIdGerenteRevisor());
            nombreGerenteRegistrado = gerenteRevisor == null ? null
                    : gerenteRevisor.getNombres() + " " + gerenteRevisor.getApellidos();
        }
        String recibiLinea = lineaRecibiDelArqueo(nombreGerenteRegistrado);

        List<MovimientoCaja> movimientos = movimientoCajaDAO.listarPorSesion(conexion, idSesionCaja);
        List<Pago> pagos = pagoDAO.listarPorSesion(conexion, idSesionCaja);
        Map<Long, Pago> pagoPorId = new HashMap<>();
        // D-141 (SEDECO, pedido de Joel): total de lo redondeado a favor del cliente en la
        // sesión, para que el arqueo explique por qué el efectivo contado es un poco menos que
        // la deuda realmente saldada -- la caja cuadra igual, el redondeo nunca fue efectivo.
        BigDecimal totalRedondeos = BigDecimal.ZERO;
        for (Pago pago : pagos) {
            pagoPorId.put(pago.getIdPago(), pago);
            totalRedondeos = totalRedondeos.add(pago.getMontoRedondeo());
        }
        List<Desembolso> desembolsos = desembolsoDAO.listarConfirmadosPorSesion(conexion, idSesionCaja);
        Map<Long, Desembolso> desembolsoPorId = new HashMap<>();
        for (Desembolso desembolso : desembolsos) {
            desembolsoPorId.put(desembolso.getIdDesembolso(), desembolso);
        }
        Map<Long, PrestamoCredito> prestamoPorId = new HashMap<>();

        int cantCobros = 0;
        int cantAnticipos = 0;
        int cantCancelaciones = 0;
        int cantVueltos = 0;
        int cantDesembolsos = 0;
        // E-6 (pedido de Joel): ENTREGA_GERENTE y COMPENSACION afectan el saldo teórico (signo
        // +1/-1, ver movimientos_caja.signo) igual que los demás tipos, pero no tenían su línea
        // en el RESUMEN -- por eso el resumen no cuadraba contra SALDO TEÓRICO cuando había una
        // entrega (el caso reportado), y tampoco cuadraría con una compensación de reversión.
        int cantEntregas = 0;
        int cantCompensaciones = 0;
        BigDecimal montoCobros = BigDecimal.ZERO;
        BigDecimal montoAnticipos = BigDecimal.ZERO;
        BigDecimal montoCancelaciones = BigDecimal.ZERO;
        BigDecimal montoVueltos = BigDecimal.ZERO;
        BigDecimal montoDesembolsos = BigDecimal.ZERO;
        BigDecimal montoEntregas = BigDecimal.ZERO;
        BigDecimal montoCompensaciones = BigDecimal.ZERO;

        List<FilaMovimientoArqueo> filas = new ArrayList<>();
        for (MovimientoCaja movimiento : movimientos) {
            String tipoMostrado;
            String operacion = "-";
            String comprobante = "-";
            if ("PAGO".equals(movimiento.getReferenciaTipo())) {
                Pago pago = pagoPorId.get(movimiento.getIdReferencia());
                if (pago != null) {
                    PrestamoCredito prestamo = prestamoPorId.computeIfAbsent(pago.getIdPrestamo(), id -> {
                        try {
                            return prestamoDAO.obtenerPorId(conexion, id);
                        } catch (SQLException error) {
                            throw new IllegalStateException(error);
                        }
                    });
                    operacion = prestamo == null ? "-" : prestamo.getIdentificadorOperacion();
                    comprobante = pago.getNumeroRecibo();
                }
                if (movimiento.getTipo() == TipoMovimientoCaja.VUELTO) {
                    tipoMostrado = "Vuelto";
                    cantVueltos++;
                    montoVueltos = montoVueltos.add(movimiento.getMonto());
                } else if (pago != null && pago.getTipo() == TipoPago.ANTICIPO) {
                    tipoMostrado = "Anticipo cap.";
                    cantAnticipos++;
                    montoAnticipos = montoAnticipos.add(movimiento.getMonto());
                } else if (pago != null && pago.getTipo() == TipoPago.CANCELACION) {
                    tipoMostrado = "Cancelación";
                    cantCancelaciones++;
                    montoCancelaciones = montoCancelaciones.add(movimiento.getMonto());
                } else {
                    tipoMostrado = "Cobro";
                    cantCobros++;
                    montoCobros = montoCobros.add(movimiento.getMonto());
                }
            } else if ("DESEMBOLSO".equals(movimiento.getReferenciaTipo())) {
                tipoMostrado = "Desembolso";
                Desembolso desembolso = desembolsoPorId.get(movimiento.getIdReferencia());
                if (desembolso != null) {
                    PrestamoCredito prestamo = prestamoPorId.computeIfAbsent(desembolso.getIdPrestamo(), id -> {
                        try {
                            return prestamoDAO.obtenerPorId(conexion, id);
                        } catch (SQLException error) {
                            throw new IllegalStateException(error);
                        }
                    });
                    operacion = prestamo == null ? "-" : prestamo.getIdentificadorOperacion();
                    comprobante = desembolso.getNumeroComprobante();
                }
                cantDesembolsos++;
                montoDesembolsos = montoDesembolsos.add(movimiento.getMonto());
            } else if (movimiento.getTipo() == TipoMovimientoCaja.ENTREGA_GERENTE) {
                tipoMostrado = "Entrega gerente";
                cantEntregas++;
                montoEntregas = montoEntregas.add(movimiento.getMonto());
            } else if (movimiento.getTipo() == TipoMovimientoCaja.RENDICION_GERENTE) {
                tipoMostrado = "Rendición gerente";
                // E-6 (pedido de Joel, confirmado leyendo cerrarCaja): la rendición es la salida
                // del cierre, no un movimiento del día -- cerrarCaja calcula y guarda
                // saldo_teorico_cierre ANTES de insertar este movimiento (líneas 237-255 de este
                // mismo archivo), así que nunca lo restó. Contarla acá en el resumen restaría
                // dos veces lo mismo contra SALDO TEÓRICO -- a propósito no se acumula.
            } else {
                tipoMostrado = "Compensación";
                cantCompensaciones++;
                montoCompensaciones = montoCompensaciones.add(movimiento.getMonto());
            }
            String montoConSigno = montoConSigno(movimiento.getSigno(), movimiento.getMonto());
            // B53 (B86, tramo 3): antes acá iba ".substring(11)", que recortaba el "dd/mm/aaaa " y
            // dejaba solo "hh:mm". Un arqueo puede abarcar movimientos de más de un día (una caja
            // que se cierra pasada la medianoche, o una sesión de otro día que se cierra hoy -- el
            // propio ficha.jsp avisa de ese caso), y con la hora sola no se sabe de cuál es cada
            // movimiento. La columna del .jrxml se ensanchó de 50 a 80 px para que la fecha entre:
            // JasperReports no recorta, corta en seco (B83/B108).
            filas.add(new FilaMovimientoArqueo(FormateadorFechas.comoDdMmAaaaHhMm(movimiento.getFechaHora()),
                    tipoMostrado, operacion, comprobante, montoConSigno));
        }

        BigDecimal[] composicion = aplicacionPagoDAO.sumarPorSesion(conexion, idSesionCaja);
        // E9/Bloque 5 (ajuste de Joel, "arqueo sin el cobro revertido"): una factura ANULADA por
        // una reversión no suma al total ni entra en la lista de números -- mismo criterio que IVA
        // del mes (FacturaDAO.listarPorRango ya filtra solo EMITIDA). Se sigue viendo igual en el
        // historial de cobranza (con su badge ANULADA), esto solo acota el total de ESTE arqueo.
        List<Factura> facturasEmitidas = new ArrayList<>();
        for (Factura factura : facturaDAO.listarPorSesion(conexion, idSesionCaja)) {
            if (factura.getEstado() == EstadoFactura.EMITIDA) {
                facturasEmitidas.add(factura);
            }
        }
        BigDecimal totalFacturas = BigDecimal.ZERO;
        StringBuilder numerosFacturas = new StringBuilder();
        for (Factura factura : facturasEmitidas) {
            if (numerosFacturas.length() > 0) {
                numerosFacturas.append(", ");
            }
            numerosFacturas.append(factura.getNumeroCompleto());
            totalFacturas = totalFacturas.add(factura.getTotal());
        }
        List<LiquidacionAnticipada> constancias = liquidacionAnticipadaDAO.listarConstanciasPorSesion(conexion, idSesionCaja);
        StringBuilder numerosConstancias = new StringBuilder();
        for (LiquidacionAnticipada constancia : constancias) {
            if (numerosConstancias.length() > 0) {
                numerosConstancias.append(", ");
            }
            numerosConstancias.append(constancia.getNumeroConstancia());
        }
        String rangoRecibos = pagos.isEmpty() ? "-"
                : pagos.size() == 1 ? pagos.get(0).getNumeroRecibo()
                : pagos.get(0).getNumeroRecibo() + " a " + pagos.get(pagos.size() - 1).getNumeroRecibo();
        StringBuilder numerosDesembolsos = new StringBuilder();
        for (Desembolso desembolso : desembolsos) {
            if (numerosDesembolsos.length() > 0) {
                numerosDesembolsos.append(", ");
            }
            numerosDesembolsos.append(desembolso.getNumeroComprobante());
        }

        Map<String, Object> parametros = new HashMap<>();
        // Punto 16b (pedido de Joel): igual que ServicioCobranza.parametrosEmpresaAFecha -- si no
        // hay versión vigente en la fecha del cierre, usa la más antigua disponible en vez de
        // rechazar la reimpresión de un arqueo viejo.
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fechaCierre));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fechaCierre));
        // Lote 2, L2-A Parte 2 (14/10/2026, pedido de Joel, autorizado): mismo patrón D-94 que
        // RAZON_SOCIAL/RUC de arriba, para que el encabezado muestre lo mismo que el comprobante.
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fechaCierre));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fechaCierre));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fechaCierre));
        parametros.put("ID_SESION", sesion.getIdSesionCaja());
        parametros.put("CAJERA_NOMBRE", cajera == null ? "-" : cajera.getNombres() + " " + cajera.getApellidos());
        // C22: la línea entera la decide Java (un solo dueño del texto) y el .jrxml la imprime tal
        // cual -- antes el "Recibí: " era un prefijo fijo de la plantilla, que no podía saber si
        // había alguien que hubiera recibido algo (misma trampa que B103).
        parametros.put("RECIBI_LINEA", recibiLinea);
        parametros.put("APERTURA", FormateadorFechas.comoDdMmAaaaHhMm(sesion.getFechaHoraApertura()));
        parametros.put("CIERRE", FormateadorFechas.comoDdMmAaaaHhMm(sesion.getFechaHoraCierre()));
        parametros.put("FONDO_INICIAL", FormateadorNumeros.comoGuaranies(sesion.getSaldoInicial()));
        // E-6 (pedido de Joel): justo después de "Fondo inicial".
        parametros.put("CANT_ENTREGAS", cantEntregas);
        parametros.put("MONTO_ENTREGAS", FormateadorNumeros.comoGuaranies(montoEntregas));
        parametros.put("CANT_COBROS", cantCobros);
        parametros.put("MONTO_COBROS", FormateadorNumeros.comoGuaranies(montoCobros));
        parametros.put("TOTAL_REDONDEOS", FormateadorNumeros.comoGuaranies(totalRedondeos));
        parametros.put("TIENE_REDONDEOS", totalRedondeos.compareTo(BigDecimal.ZERO) > 0);
        parametros.put("CANT_ANTICIPOS", cantAnticipos);
        parametros.put("MONTO_ANTICIPOS", FormateadorNumeros.comoGuaranies(montoAnticipos));
        parametros.put("CANT_CANCELACIONES", cantCancelaciones);
        parametros.put("MONTO_CANCELACIONES", FormateadorNumeros.comoGuaranies(montoCancelaciones));
        parametros.put("CANT_VUELTOS", cantVueltos);
        parametros.put("MONTO_VUELTOS", FormateadorNumeros.comoGuaranies(montoVueltos));
        parametros.put("CANT_DESEMBOLSOS", cantDesembolsos);
        parametros.put("MONTO_DESEMBOLSOS", FormateadorNumeros.comoGuaranies(montoDesembolsos));
        // E-6 (pedido de Joel): última línea "(−)" antes de SALDO TEÓRICO.
        parametros.put("CANT_COMPENSACIONES", cantCompensaciones);
        parametros.put("MONTO_COMPENSACIONES", FormateadorNumeros.comoGuaranies(montoCompensaciones));
        parametros.put("SALDO_TEORICO", FormateadorNumeros.comoGuaranies(sesion.getSaldoTeoricoCierre()));
        parametros.put("EFECTIVO_CONTADO", FormateadorNumeros.comoGuaranies(sesion.getSaldoFisico()));
        // A3d (pedido de Joel): antes mostraba el monto sin decir si sobró o faltó -- mismo
        // criterio que ya se usa en pantalla (/caja, "Sesiones cerradas").
        parametros.put("DIFERENCIA", formatearDiferenciaArqueo(sesion.getDiferenciaArqueo()));
        parametros.put("MOTIVO_DIFERENCIA", sesion.getMotivoDiferencia() == null ? "-" : sesion.getMotivoDiferencia());
        // A4 (pedido de Joel): con diferencia 0 no hay nada que explicar -- oculta la línea.
        parametros.put("TIENE_DIFERENCIA", sesion.getDiferenciaArqueo().compareTo(BigDecimal.ZERO) != 0);
        BigDecimal totalAplicado = composicion[0].add(composicion[1]).add(composicion[2]).add(composicion[3]).add(composicion[4]);
        parametros.put("TOTAL_APLICADO", FormateadorNumeros.comoGuaranies(totalAplicado));
        // D-142 (corrección de Joel sobre D-141): conciliación informativa, no un movimiento de
        // efectivo -- el redondeo nunca entró ni salió de la caja, así que no se resta del
        // RESUMEN (que ya cuadra exacto sin tocarlo, D-142 punto 1).
        parametros.put("EFECTIVO_NETO", FormateadorNumeros.comoGuaranies(totalAplicado.subtract(totalRedondeos)));
        parametros.put("COMPOSICION_CAPITAL", FormateadorNumeros.comoGuaranies(composicion[0]));
        parametros.put("COMPOSICION_INTERES", FormateadorNumeros.comoGuaranies(composicion[1]));
        parametros.put("COMPOSICION_MORATORIO", FormateadorNumeros.comoGuaranies(composicion[2]));
        parametros.put("COMPOSICION_PUNITORIO", FormateadorNumeros.comoGuaranies(composicion[3]));
        parametros.put("COMPOSICION_IVA", FormateadorNumeros.comoGuaranies(composicion[4]));
        parametros.put("RANGO_RECIBOS", rangoRecibos);
        parametros.put("CANT_RECIBOS", pagos.size());
        // A4 (pedido de Joel): "Recibos: —" cuando no hubo ninguno, mismo criterio que
        // Facturas/Constancias/Desembolsos (antes mostraba "Recibos: - (0)").
        parametros.put("TIENE_RECIBOS", !pagos.isEmpty());
        parametros.put("TIENE_FACTURAS", !facturasEmitidas.isEmpty());
        parametros.put("NUMEROS_FACTURAS", facturasEmitidas.isEmpty() ? "-" : numerosFacturas.toString());
        parametros.put("CANT_FACTURAS", facturasEmitidas.size());
        parametros.put("TOTAL_FACTURAS", FormateadorNumeros.comoGuaranies(totalFacturas));
        parametros.put("TIENE_CONSTANCIAS", !constancias.isEmpty());
        parametros.put("NUMEROS_CONSTANCIAS", constancias.isEmpty() ? "-" : numerosConstancias.toString());
        parametros.put("CANT_CONSTANCIAS", constancias.size());
        parametros.put("TIENE_DESEMBOLSOS", !desembolsos.isEmpty());
        parametros.put("NUMEROS_DESEMBOLSOS", desembolsos.isEmpty() ? "-" : numerosDesembolsos.toString());
        parametros.put("CANT_DESEMBOLSOS_COMPROBANTE", desembolsos.size());
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(sesion.getFechaHoraCierre()));

        return llenarYExportar("arqueo_caja.jasper", parametros, new JRBeanCollectionDataSource(filas));
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
