package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** Una fila del reporte "Facturas anuladas del mes" (E9/Bloque 5, gerente, para la baja en
 * Marangatu) -- lee directo de facturas, nunca recalcula nada (REGLAS_DESARROLLO.md §5). Bloque C, tanda 2
 * (pedido de Joel): suma "quién anuló" (el gerente que autorizó la reversión que anuló la
 * factura) y totalCrudo (sin formatear, para el total de montos anulados). */
public class FilaFacturaAnulada {

    private final long idFactura;
    private final String numeroFactura;
    private final String fecha;
    private final String clienteNombre;
    private final String clienteDocumento;
    private final String total;
    private final BigDecimal totalCrudo;
    private final String numeroNotaAnulacion;
    private final String timbrado;
    private final String fechaAnulacion;
    private final String motivo;
    private final String anuladoPorNombre;

    public FilaFacturaAnulada(long idFactura, String numeroFactura, String fecha, String clienteNombre,
            String clienteDocumento, String total, BigDecimal totalCrudo, String numeroNotaAnulacion,
            String timbrado, String fechaAnulacion, String motivo, String anuladoPorNombre) {
        this.idFactura = idFactura;
        this.numeroFactura = numeroFactura;
        this.fecha = fecha;
        this.clienteNombre = clienteNombre;
        this.clienteDocumento = clienteDocumento;
        this.total = total;
        this.totalCrudo = totalCrudo;
        this.numeroNotaAnulacion = numeroNotaAnulacion;
        this.timbrado = timbrado;
        this.fechaAnulacion = fechaAnulacion;
        this.motivo = motivo;
        this.anuladoPorNombre = anuladoPorNombre;
    }

    public long getIdFactura() {
        return idFactura;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public String getFecha() {
        return fecha;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteDocumento() {
        return clienteDocumento;
    }

    public String getTotal() {
        return total;
    }

    public BigDecimal getTotalCrudo() {
        return totalCrudo;
    }

    public String getNumeroNotaAnulacion() {
        return numeroNotaAnulacion;
    }

    public String getTimbrado() {
        return timbrado;
    }

    public String getFechaAnulacion() {
        return fechaAnulacion;
    }

    public String getMotivo() {
        return motivo;
    }

    /** Bloque C, tanda 2 (pedido de Joel): el gerente que autorizó la reversión que anuló esta
     * factura ("-" si la reversión no tiene autorizador registrado). */
    public String getAnuladoPorNombre() {
        return anuladoPorNombre;
    }
}
