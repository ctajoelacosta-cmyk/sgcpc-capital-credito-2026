package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** Una fila del reporte "IVA del mes" (§13, Bloque 4, E8) -- lee directo de facturas, nunca
 * recalcula el IVA (REGLAS_DESARROLLO.md §5). Bloque C, tanda 2 (pedido de Joel): ahora incluye también las
 * facturas ANULADAS del mes (antes quedaban completamente afuera) -- marcadas con `anulada`, para
 * que la numeración completa del mes quede visible (una factura anulada sigue ocupando un número,
 * hay que poder justificarlo ante la DNIT); los *Crudo (sin formatear) se usan para sumar los
 * totales SOLO de las no anuladas. */
public class FilaIva {

    private final String numeroFactura;
    private final String fecha;
    private final String clienteNombre;
    private final String clienteDocumento;
    private final String totalExenta;
    private final BigDecimal totalExentaCrudo;
    private final String totalGravada10;
    private final BigDecimal totalGravada10Crudo;
    private final String iva10;
    private final BigDecimal iva10Crudo;
    private final String total;
    private final BigDecimal totalCrudo;
    private final boolean anulada;

    public FilaIva(String numeroFactura, String fecha, String clienteNombre, String clienteDocumento,
            String totalExenta, BigDecimal totalExentaCrudo, String totalGravada10, BigDecimal totalGravada10Crudo,
            String iva10, BigDecimal iva10Crudo, String total, BigDecimal totalCrudo, boolean anulada) {
        this.numeroFactura = numeroFactura;
        this.fecha = fecha;
        this.clienteNombre = clienteNombre;
        this.clienteDocumento = clienteDocumento;
        this.totalExenta = totalExenta;
        this.totalExentaCrudo = totalExentaCrudo;
        this.totalGravada10 = totalGravada10;
        this.totalGravada10Crudo = totalGravada10Crudo;
        this.iva10 = iva10;
        this.iva10Crudo = iva10Crudo;
        this.total = total;
        this.totalCrudo = totalCrudo;
        this.anulada = anulada;
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

    public String getTotalExenta() {
        return totalExenta;
    }

    public BigDecimal getTotalExentaCrudo() {
        return totalExentaCrudo;
    }

    public String getTotalGravada10() {
        return totalGravada10;
    }

    public BigDecimal getTotalGravada10Crudo() {
        return totalGravada10Crudo;
    }

    public String getIva10() {
        return iva10;
    }

    public BigDecimal getIva10Crudo() {
        return iva10Crudo;
    }

    public String getTotal() {
        return total;
    }

    public BigDecimal getTotalCrudo() {
        return totalCrudo;
    }

    /** Bloque C, tanda 2 (pedido de Joel): "las facturas anuladas se muestran marcadas y no
     * suman" -- esta fila sigue viniendo en la lista (para que la numeración del mes quede
     * completa), pero calcularTotalesIva la salta. */
    public boolean isAnulada() {
        return anulada;
    }
}
