package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlosarioFinancieroTest {

    @Test
    public void teaInterpolaElTopeVigente() {
        String completo = GlosarioFinanciero.TEA.completo("27,39");
        assertTrue(completo.contains("27,39"));
    }

    @Test
    public void tnaInterpolaTasaMensualYTna() {
        String completo = GlosarioFinanciero.TNA.completo("2,0118", "24,1413");
        assertTrue(completo.contains("2,0118"));
        assertTrue(completo.contains("24,1413"));
    }

    @Test
    public void terminosSinValorDinamicoNoLanzan() {
        assertFalse(GlosarioFinanciero.CFT.completo().isEmpty());
        assertFalse(GlosarioFinanciero.IVA.completo().isEmpty());
        assertFalse(GlosarioFinanciero.CAPITAL.completo().isEmpty());
        assertFalse(GlosarioFinanciero.INTERES.completo().isEmpty());
        assertFalse(GlosarioFinanciero.CUOTA_MAXIMA.completo().isEmpty());
        assertFalse(GlosarioFinanciero.TOTAL_A_PAGAR.completo().isEmpty());
    }

    @Test
    public void textosCortosNoEstanVacios() {
        assertFalse(GlosarioFinanciero.TEA.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.TNA.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.CFT.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.IVA.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.CAPITAL.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.INTERES.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.CUOTA_MAXIMA.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.TOTAL_A_PAGAR.getCorto().isEmpty());
        assertFalse(GlosarioFinanciero.AFECTACION.getCorto().isEmpty());
    }

    @Test
    public void tituloCombinaSiglaYNombre() {
        assertEquals("TEA – Tasa Efectiva Anual", GlosarioFinanciero.TEA.getTitulo());
        assertEquals("TNA – Tasa Nominal Anual", GlosarioFinanciero.TNA.getTitulo());
        assertEquals("CFT – Costo Financiero Total", GlosarioFinanciero.CFT.getTitulo());
        assertEquals("IVA – Impuesto al Valor Agregado", GlosarioFinanciero.IVA.getTitulo());
    }

    @Test
    public void tituloEsSoloLaSiglaSinNombreExpandido() {
        assertEquals("Capital", GlosarioFinanciero.CAPITAL.getTitulo());
        assertEquals("Interés", GlosarioFinanciero.INTERES.getTitulo());
        assertEquals("Cuota más alta", GlosarioFinanciero.CUOTA_MAXIMA.getTitulo());
        assertEquals("Total a pagar", GlosarioFinanciero.TOTAL_A_PAGAR.getTitulo());
        assertEquals("Afectación al ingreso", GlosarioFinanciero.AFECTACION.getTitulo());
    }

    // --- RF06 (pedido de Joel, 10/10/2026): términos nuevos para Solicitud y Aprobación ---

    @Test
    public void afectacionInterpolaElLimiteVigente() {
        String completo = GlosarioFinanciero.AFECTACION.completo("40 %");
        assertTrue(completo.contains("40 %"));
    }
}
