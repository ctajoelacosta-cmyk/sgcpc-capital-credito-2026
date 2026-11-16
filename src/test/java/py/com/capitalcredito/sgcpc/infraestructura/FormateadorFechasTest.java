package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;

public class FormateadorFechasTest {

    @Test
    public void testComoDdMmAaaa() {
        assertEquals("01/01/2026", FormateadorFechas.comoDdMmAaaa(LocalDate.of(2026, 1, 1)));
        assertEquals("24/09/2026", FormateadorFechas.comoDdMmAaaa(LocalDate.of(2026, 9, 24)));
    }

    @Test
    public void testComoDdMmAaaaNulo() {
        assertEquals("", FormateadorFechas.comoDdMmAaaa(null));
    }

    @Test
    public void testComoDuracionMenosDeUnaHora() {
        assertEquals("45 min", FormateadorFechas.comoDuracion(45));
    }

    @Test
    public void testComoDuracionHorasExactas() {
        assertEquals("3 h", FormateadorFechas.comoDuracion(180));
    }

    @Test
    public void testComoDuracionHorasConMinutos() {
        assertEquals("2 h 45 min", FormateadorFechas.comoDuracion(165));
    }

    @Test
    public void testComoDuracionUnDia() {
        assertEquals("1 día", FormateadorFechas.comoDuracion(1440));
    }

    @Test
    public void testComoDuracionVariosDiasConHoras() {
        assertEquals("2 días 4 h", FormateadorFechas.comoDuracion(2 * 1440 + 4 * 60));
    }
}
