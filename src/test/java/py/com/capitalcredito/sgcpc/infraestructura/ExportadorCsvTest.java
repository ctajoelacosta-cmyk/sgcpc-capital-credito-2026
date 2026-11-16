package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * B1 (Bloque 7/E10, pedido de Joel): formato de ExportadorCsv -- ninguno toca la base. E-7:
 * comoTexto() es la corrección al bug real que encontró Joel probando el CSV de Clientes (el
 * teléfono "0985678901" salía "985678901" en Excel, sin el cero inicial).
 */
public class ExportadorCsvTest {

    @Test
    public void testComoTextoConCeroInicial() {
        // Caso exacto de E-7: el campo CSV completo tiene que ser ="0985678901" entre comillas
        // CSV (las comillas de la fórmula se duplican como pide RFC 4180).
        assertEquals("\"=\"\"0985678901\"\"\"", ExportadorCsv.comoTexto("0985678901"));
    }

    /** Deshace las dos capas de comoTexto() (CSV, después fórmula de Excel) -- así las pruebas
     * de abajo verifican el valor que Excel terminaría mostrando, no una cadena pegada a mano. */
    private static String decodificarComoTexto(String campoCsv) {
        assertTrue("tiene que venir entre comillas CSV", campoCsv.startsWith("\"") && campoCsv.endsWith("\""));
        String sinComillasCsv = campoCsv.substring(1, campoCsv.length() - 1).replace("\"\"", "\"");
        assertTrue("tiene que ser una fórmula =\"...\"", sinComillasCsv.startsWith("=\"") && sinComillasCsv.endsWith("\""));
        String formula = sinComillasCsv.substring(2, sinComillasCsv.length() - 1);
        return formula.replace("\"\"", "\"");
    }

    @Test
    public void testComoTextoEsReversibleConCeroInicial() {
        assertEquals("0985678901", decodificarComoTexto(ExportadorCsv.comoTexto("0985678901")));
    }

    @Test
    public void testComoTextoEsReversibleConCedulaConPuntos() {
        assertEquals("1.234.567", decodificarComoTexto(ExportadorCsv.comoTexto("1.234.567")));
    }

    @Test
    public void testComoTextoEsReversibleConOperacionAlfanumerica() {
        assertEquals("CC-2026-00008", decodificarComoTexto(ExportadorCsv.comoTexto("CC-2026-00008")));
    }

    /** E-8 (pedido de Joel, encontrado probando el CSV de Préstamos en el navegador): "3/3" se
     * veía como 03-mar y "2/12" como 02-dic -- Excel interpreta una celda "N/M" como fecha
     * día/mes. Mismo arreglo que E-7 (comoTexto), aplicado ahora a "Cuotas pagadas/total". */
    @Test
    public void testComoTextoEsReversibleConCuotasPagadasTotal() {
        assertEquals("3/3", decodificarComoTexto(ExportadorCsv.comoTexto("3/3")));
        assertEquals("2/12", decodificarComoTexto(ExportadorCsv.comoTexto("2/12")));
    }

    @Test
    public void testComoTextoEsReversibleConComillaInterna() {
        assertEquals("a\"b", decodificarComoTexto(ExportadorCsv.comoTexto("a\"b")));
    }

    @Test
    public void testComoTextoNulo() {
        assertEquals("", decodificarComoTexto(ExportadorCsv.comoTexto(null)));
    }

    @Test
    public void testComilla() {
        assertEquals("\"Andrea Benegas\"", ExportadorCsv.comilla("Andrea Benegas"));
    }

    @Test
    public void testComillaConComillaInterna() {
        assertEquals("\"dijo \"\"hola\"\"\"", ExportadorCsv.comilla("dijo \"hola\""));
    }

    @Test
    public void testComillaNulo() {
        assertEquals("\"\"", ExportadorCsv.comilla(null));
    }

    @Test
    public void testLimpiar() {
        assertEquals("1234567", ExportadorCsv.limpiar("1.234.567 Gs."));
    }

    @Test
    public void testLimpiarSinPuntosNiSufijo() {
        assertEquals("0", ExportadorCsv.limpiar("0 Gs."));
    }

    @Test
    public void testLimpiarNulo() {
        assertEquals("", ExportadorCsv.limpiar(null));
    }
}
