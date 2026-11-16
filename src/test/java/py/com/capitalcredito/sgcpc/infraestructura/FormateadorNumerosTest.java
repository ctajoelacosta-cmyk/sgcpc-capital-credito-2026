package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.math.BigDecimal;
import org.junit.Test;

public class FormateadorNumerosTest {

    @Test
    public void testComoPorcentajeConDecimales() {
        assertEquals("27,39 %", FormateadorNumeros.comoPorcentaje(new BigDecimal("0.273900")));
    }

    @Test
    public void testComoPorcentajeSinDecimalesNoMuestraCerosDeMas() {
        assertEquals("100 %", FormateadorNumeros.comoPorcentaje(new BigDecimal("1.000000")));
        assertEquals("30 %", FormateadorNumeros.comoPorcentaje(new BigDecimal("0.300000")));
        assertEquals("40 %", FormateadorNumeros.comoPorcentaje(new BigDecimal("0.400000")));
    }

    @Test
    public void testComoPorcentajeNulo() {
        assertEquals("", FormateadorNumeros.comoPorcentaje(null));
    }

    @Test
    public void testComoPorcentajeUnDecimalRedondea() {
        assertEquals("66,9 %", FormateadorNumeros.comoPorcentajeUnDecimal(new BigDecimal("0.6686953333")));
    }

    @Test
    public void testComoPorcentajeUnDecimalMuestraElDecimalAunqueSeaCero() {
        assertEquals("40,0 %", FormateadorNumeros.comoPorcentajeUnDecimal(new BigDecimal("0.400000")));
    }

    @Test
    public void testComoPorcentajeUnDecimalNulo() {
        assertEquals("", FormateadorNumeros.comoPorcentajeUnDecimal(null));
    }

    @Test
    public void testFraccionDesdePorcentaje() {
        BigDecimal fraccion = FormateadorNumeros.fraccionDesdePorcentaje(new BigDecimal("27.39"), 6);
        assertEquals(0, new BigDecimal("0.273900").compareTo(fraccion));
    }

    @Test
    public void testFraccionDesdePorcentajeIdaYVuelta() {
        BigDecimal original = new BigDecimal("0.300000");
        String porcentaje = FormateadorNumeros.comoPorcentaje(original).replace(" %", "").replace(",", ".");
        BigDecimal fraccion = FormateadorNumeros.fraccionDesdePorcentaje(new BigDecimal(porcentaje), 6);
        assertEquals(0, original.compareTo(fraccion));
    }

    // ======================================================================
    // B205, segunda vuelta (15/11/2026, hallazgo de Joel al probar): el contador del titulo salia
    // "2230" pegado. comoCantidad es el dueño del separador de miles de las CANTIDADES.
    // ======================================================================

    @Test
    public void testComoCantidadPoneElSeparadorDeMiles() {
        assertEquals("2.230", FormateadorNumeros.comoCantidad(2230));
        assertEquals("18.381", FormateadorNumeros.comoCantidad(18381));
        assertEquals("1.234.567", FormateadorNumeros.comoCantidad(1234567));
    }

    /** Por debajo de mil no cambia nada: es por eso que el defecto no se habia notado en ninguno de
     * los otros contadores de la fase 2 (ninguno habia pasado de 999 todavia). */
    @Test
    public void testComoCantidadNoTocaLosNumerosChicos() {
        assertEquals("0", FormateadorNumeros.comoCantidad(0));
        assertEquals("1", FormateadorNumeros.comoCantidad(1));
        assertEquals("141", FormateadorNumeros.comoCantidad(141));
        assertEquals("999", FormateadorNumeros.comoCantidad(999));
        assertEquals("1.000", FormateadorNumeros.comoCantidad(1000));
    }

    /** Una cantidad NO lleva "Gs.": no es plata, y de paso eso evita la trampa del punto duplicado
     * que persiguieron B178 y B188. */
    @Test
    public void testComoCantidadNoLlevaLaUnidadMonetaria() {
        assertFalse(FormateadorNumeros.comoCantidad(2230).contains("Gs"));
        assertFalse(FormateadorNumeros.comoCantidad(2230).endsWith("."));
    }

    /** Null no inventa un cero, y el negativo conserva su signo. */
    @Test
    public void testComoCantidadConNullYConNegativo() {
        assertEquals("", FormateadorNumeros.comoCantidad((Long) null));
        assertEquals("-1.500", FormateadorNumeros.comoCantidad(-1500));
    }
}
