package py.com.capitalcredito.sgcpc.dto;

/**
 * Una cláusula del contrato de préstamo (D-70): encabezado + texto, ya armados en Java -- el
 * reporte solo itera la lista, una fila por cláusula, para que JasperReports pagine
 * correctamente un documento largo (REGLAS_DESARROLLO.md §6: el reporte no calcula ni decide
 * nada, solo muestra).
 */
public class ClausulaReporte {

    private final String encabezado;
    private final String texto;

    public ClausulaReporte(String encabezado, String texto) {
        this.encabezado = encabezado;
        this.texto = texto;
    }

    public String getEncabezado() {
        return encabezado;
    }

    public String getTexto() {
        return texto;
    }
}
