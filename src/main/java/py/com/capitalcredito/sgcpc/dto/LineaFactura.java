package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * D-85: una línea de la factura tal como la vería el `.jrxml` (RN-25/D-78 -- solo conceptos
 * gravados, sin capital, con la descripción identificando la operación y la cuota). No hay
 * ningún reporte todavía: esta clase solo junta el importe neto y el IVA de cada concepto para
 * poder validar, antes de guardar la factura, que la suma de las líneas cuadra exacto con los
 * totales guardados (`facturas.total_gravada_10`/`iva_10`).
 */
public class LineaFactura {

    private final String descripcion;
    private final BigDecimal importeNeto;
    private final BigDecimal iva;

    public LineaFactura(String descripcion, BigDecimal importeNeto, BigDecimal iva) {
        this.descripcion = descripcion;
        this.importeNeto = importeNeto;
        this.iva = iva;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getImporteNeto() {
        return importeNeto;
    }

    public BigDecimal getIva() {
        return iva;
    }

    /** Importe + IVA de esta línea (lo que suma a total_gravada_10). */
    public BigDecimal getTotal() {
        return importeNeto.add(iva);
    }
}
