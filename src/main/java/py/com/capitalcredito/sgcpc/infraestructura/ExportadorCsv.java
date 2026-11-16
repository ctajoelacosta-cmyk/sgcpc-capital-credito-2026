package py.com.capitalcredito.sgcpc.infraestructura;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;

/**
 * B1 (Bloque 7/E10, pedido de Joel): mecanismo COMÚN para los CSV del sistema -- antes
 * ControladorReportesServlet y ControladorClientesServlet tenían cada uno su propia copia de
 * abrirCsv()/comilla()/limpiar(). Mismo criterio que GeneradorInformesPdf para el PDF: un solo
 * lugar, el próximo módulo que exporte a CSV no repite nada de esto.
 */
public final class ExportadorCsv {

    private ExportadorCsv() {
    }

    /** BOM UTF-8 + separador ";" (Bloque 4, E8, corrección de Joel): para que el CSV abra bien
     * en Excel en español -- la coma decimal de es-PY choca con "," como separador de columna, y
     * sin el BOM Excel adivina la codificación y los acentos salen mal. */
    public static PrintWriter abrir(HttpServletResponse response, String nombreArchivo) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");
        response.getOutputStream().write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        return new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * E-7 (bug encontrado por Joel al probar el CSV de Clientes): Excel, en español, convierte
     * una celda que parece un número en un número real -- un teléfono o una cédula que empiezan
     * con "0" pierden ese cero ("0985678901" sale "985678901"). La fórmula ="texto" fuerza texto
     * literal en Excel/LibreOffice sin tocar el separador de columnas: sigue siendo un campo CSV
     * normal, entre los mismos ";". Columnas que SIEMPRE tienen que pasar por acá: teléfono,
     * C.I., número de operación, recibo, factura y comprobante -- cualquier identificador que
     * pueda tener cero inicial o que Excel pueda confundir con un número, nunca algo que de
     * verdad se vaya a sumar (eso sigue viajando como número plano, sin esta función).
     * <p>
     * Dos capas de escapado, de adentro hacia afuera: (1) el valor va dentro de una fórmula
     * Excel ="..." -- una comilla propia del valor se escapa duplicándola, igual que un literal
     * de texto de cualquier fórmula; (2) el CAMPO CSV entero (la fórmula completa, que ya tiene
     * comillas) va entre comillas (RFC 4180), duplicando también las comillas internas. Para
     * "0985678901" el campo CSV final es {@code "=""0985678901"""}.
     */
    public static String comoTexto(String valor) {
        String formula = "=\"" + (valor == null ? "" : valor).replace("\"", "\"\"") + "\"";
        return "\"" + formula.replace("\"", "\"\"") + "\"";
    }

    /** CSV (RFC 4180 simplificado): comillas dobles alrededor, comillas internas escapadas
     * duplicándolas -- para columnas de texto libre que nunca llevan la marca de comoTexto(). */
    public static String comilla(String texto) {
        return "\"" + (texto == null ? "" : texto.replace("\"", "\"\"")) + "\"";
    }

    /** Los importes ya vienen formateados como "1.234.567 Gs." -- se quita el sufijo y los
     * puntos de miles para que la celda sea numérica en la planilla (sumable en Excel). */
    public static String limpiar(String montoFormateado) {
        if (montoFormateado == null) {
            return "";
        }
        return montoFormateado.replace(" Gs.", "").replace(".", "").trim();
    }
}
