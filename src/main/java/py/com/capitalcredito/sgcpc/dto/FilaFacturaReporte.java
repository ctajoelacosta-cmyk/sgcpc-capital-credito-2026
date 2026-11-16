package py.com.capitalcredito.sgcpc.dto;

/**
 * Una línea de la factura (docs/diseno_comprobantes_E8.md sección 1), ya formateada para el
 * reporte -- envoltorio de LineaFactura (importes/IVA en BigDecimal) con los mismos valores como
 * texto (FormateadorNumeros), porque el .jrxml no formatea ni calcula nada (REGLAS_DESARROLLO.md §6).
 */
public class FilaFacturaReporte {

    private final String descripcion;
    private final String importeNeto;
    private final String iva;
    private final String total;

    public FilaFacturaReporte(String descripcion, String importeNeto, String iva, String total) {
        this.descripcion = descripcion;
        this.importeNeto = importeNeto;
        this.iva = iva;
        this.total = total;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getImporteNeto() {
        return importeNeto;
    }

    public String getIva() {
        return iva;
    }

    public String getTotal() {
        return total;
    }
}
