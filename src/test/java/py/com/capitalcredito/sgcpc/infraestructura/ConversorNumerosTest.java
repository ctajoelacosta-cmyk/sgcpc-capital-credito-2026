package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;

public class ConversorNumerosTest {

    @Test
    public void testAceptaComaComoSeparadorDecimal() {
        assertEquals(0, new BigDecimal("1.5").compareTo(ConversorNumeros.aBigDecimal("1,5")));
    }

    @Test
    public void testAceptaPuntoComoSeparadorDecimal() {
        assertEquals(0, new BigDecimal("1.5").compareTo(ConversorNumeros.aBigDecimal("1.5")));
    }

    @Test
    public void testAceptaEspaciosAlrededor() {
        assertEquals(0, new BigDecimal("27.39").compareTo(ConversorNumeros.aBigDecimal("  27,39  ")));
    }

    @Test
    public void testValorNoNumericoLanzaNegocioException() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aBigDecimal("abc"));
    }

    @Test
    public void testValorVacioLanzaNegocioException() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aBigDecimal(""));
        assertThrows(NegocioException.class, () -> ConversorNumeros.aBigDecimal(null));
    }

    @Test
    public void testEnteroValido() {
        assertEquals(36, ConversorNumeros.aEntero("36"));
    }

    @Test
    public void testEnteroConDecimalLanzaNegocioException() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero("1,5"));
    }

    @Test
    public void testEnteroNoNumericoLanzaNegocioException() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero("xyz"));
    }

    @Test
    public void testEnteroVacioLanzaNegocioException() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero(""));
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero(null));
    }

    // --- aBigDecimalGuaranies: el punto es separador de miles, nunca decimal
    // (los guaraníes no llevan decimales) — distinto de aBigDecimal, que sigue
    // usando la coma como decimal en los campos de porcentaje. ---

    @Test
    public void testGuaraniesConPuntosDeMiles() {
        assertEquals(0, new BigDecimal("3000000").compareTo(ConversorNumeros.aBigDecimalGuaranies("3.000.000")));
    }

    @Test
    public void testGuaraniesSinPuntos() {
        assertEquals(0, new BigDecimal("3000000").compareTo(ConversorNumeros.aBigDecimalGuaranies("3000000")));
    }

    @Test
    public void testGuaraniesConComaDecimalSeRechaza() {
        // Los guaraníes no llevan decimales: una coma acá siempre es un error, nunca un decimal.
        assertThrows(NegocioException.class, () -> ConversorNumeros.aBigDecimalGuaranies("3.000.000,50"));
    }

    @Test
    public void testPorcentajeSigueUsandoComaComoDecimal() {
        // "1,5" en un campo de porcentaje (no de guaraníes) sigue siendo 1,5 -- coma decimal.
        assertEquals(0, new BigDecimal("1.5").compareTo(ConversorNumeros.aBigDecimal("1,5")));
    }
}
