package py.com.capitalcredito.sgcpc.dto;

/**
 * D-146 (pantalla de Caja, pedido de Joel): una fila de la tabla "Movimientos" de la sesión
 * abierta, con su comprobante (recibo de un cobro, comprobante de un desembolso) y la URL para
 * reimprimirlo -- el mismo PDF ya guardado (ServicioCobranza.obtenerPdfRecibo /
 * ServicioDesembolso.obtenerPdfComprobante, ambos con el mismo candado de "no regenerar" que ya
 * usa el arqueo). Todo ya viene resuelto desde el servicio -- la JSP no decide nada (REGLAS_DESARROLLO.md §5).
 */
public class FilaMovimientoCaja {

    private final String fechaHora;
    private final String tipoEtiqueta;
    private final String montoConSigno;
    private final String observacion;
    private final String numeroComprobante;
    private final String urlReimprimir;

    public FilaMovimientoCaja(String fechaHora, String tipoEtiqueta, String montoConSigno, String observacion,
            String numeroComprobante, String urlReimprimir) {
        this.fechaHora = fechaHora;
        this.tipoEtiqueta = tipoEtiqueta;
        this.montoConSigno = montoConSigno;
        this.observacion = observacion;
        this.numeroComprobante = numeroComprobante;
        this.urlReimprimir = urlReimprimir;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public String getTipoEtiqueta() {
        return tipoEtiqueta;
    }

    public String getMontoConSigno() {
        return montoConSigno;
    }

    public String getObservacion() {
        return observacion;
    }

    /** Null (la JSP muestra "-"): entregas/rendiciones de gerente y compensaciones no tienen
     * comprobante -- ninguna cuota ni desembolso detrás, mismo criterio que el arqueo. */
    public String getNumeroComprobante() {
        return numeroComprobante;
    }

    public String getUrlReimprimir() {
        return urlReimprimir;
    }
}
