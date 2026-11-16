package py.com.capitalcredito.sgcpc.dto;

/**
 * Una fila del historial de cobros de un préstamo, para la reimpresión (E8, D-94, Bloque 2):
 * el recibo siempre existe; la factura y la constancia son opcionales según el pago.
 */
public class FilaHistorialCobranza {

    private final long idPago;
    private final String numeroRecibo;
    private final String fechaHora;
    private final String etiquetaTipo;
    private final String montoAplicado;
    private final Long idFactura;
    private final String numeroFactura;
    private final Long idLiquidacion;
    private final String numeroConstancia;
    private final boolean revertido;
    private final boolean facturaAnulada;
    private final boolean puedeSolicitarReversion;
    private final String estadoReversionSolicitud;
    private final String motivoRechazoReversion;
    private final Long idReversionPago;
    private final boolean noAdmiteReversionPorCierreDocumental;

    public FilaHistorialCobranza(long idPago, String numeroRecibo, String fechaHora, String etiquetaTipo,
            String montoAplicado, Long idFactura, String numeroFactura, Long idLiquidacion, String numeroConstancia,
            boolean revertido, boolean facturaAnulada, boolean puedeSolicitarReversion, String estadoReversionSolicitud,
            String motivoRechazoReversion, Long idReversionPago, boolean noAdmiteReversionPorCierreDocumental) {
        this.idPago = idPago;
        this.numeroRecibo = numeroRecibo;
        this.fechaHora = fechaHora;
        this.etiquetaTipo = etiquetaTipo;
        this.montoAplicado = montoAplicado;
        this.idFactura = idFactura;
        this.numeroFactura = numeroFactura;
        this.idLiquidacion = idLiquidacion;
        this.numeroConstancia = numeroConstancia;
        this.revertido = revertido;
        this.facturaAnulada = facturaAnulada;
        this.puedeSolicitarReversion = puedeSolicitarReversion;
        this.estadoReversionSolicitud = estadoReversionSolicitud;
        this.motivoRechazoReversion = motivoRechazoReversion;
        this.idReversionPago = idReversionPago;
        this.noAdmiteReversionPorCierreDocumental = noAdmiteReversionPorCierreDocumental;
    }

    public long getIdPago() {
        return idPago;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public String getEtiquetaTipo() {
        return etiquetaTipo;
    }

    public String getMontoAplicado() {
        return montoAplicado;
    }

    public Long getIdFactura() {
        return idFactura;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public Long getIdLiquidacion() {
        return idLiquidacion;
    }

    public String getNumeroConstancia() {
        return numeroConstancia;
    }

    public boolean isRevertido() {
        return revertido;
    }

    public boolean isFacturaAnulada() {
        return facturaAnulada;
    }

    public boolean isPuedeSolicitarReversion() {
        return puedeSolicitarReversion;
    }

    /** "SOLICITADA"/"RECHAZADA" (nunca "AUTORIZADA": ahí ya se usa isRevertido()), o null si nunca
     * se pidió una reversión para este pago. */
    public String getEstadoReversionSolicitud() {
        return estadoReversionSolicitud;
    }

    public String getMotivoRechazoReversion() {
        return motivoRechazoReversion;
    }

    /** Punto 2 (RF25, pedido de Joel): null salvo que isRevertido() -- la constancia de
     * reversión solo existe para una reversión AUTORIZADA. */
    public Long getIdReversionPago() {
        return idReversionPago;
    }

    /** 13/10/2026 (pedido de Joel): true solo en la fila que sería "la última operación vigente"
     * (la que ofrecería el botón, isPuedeSolicitarReversion()), cuando el cierre documental del
     * préstamo ya está COMPLETO -- la columna "Reversión" muestra un texto fijo en vez de
     * casilla/motivo/botón. */
    public boolean isNoAdmiteReversionPorCierreDocumental() {
        return noAdmiteReversionPorCierreDocumental;
    }
}
