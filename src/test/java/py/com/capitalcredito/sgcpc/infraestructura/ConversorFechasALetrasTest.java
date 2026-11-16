package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;

/** D-70 (contrato de préstamo): fecha completa en letras. */
public class ConversorFechasALetrasTest {

    @Test
    public void testFechaCompletaEnLetras() {
        assertEquals("veinticinco días del mes de septiembre de dos mil veintiséis",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2026, 9, 25)));
    }

    @Test
    public void testPrimerDiaDelMesUsaPrimerNoUno() {
        assertEquals("primer día del mes de enero de dos mil veintisiete",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2027, 1, 1)));
    }

    @Test
    public void testFechaNulaDevuelveVacio() {
        assertEquals("", ConversorFechasALetras.comoDiaMesAnioEnLetras(null));
    }

    // ======================================================================
    // B11 (decision de Joel, 01/11/2026): el dia va APOCOPADO delante de "dias".
    // ======================================================================

    @Test
    public void testElDiaVeintiunoVaApocopado() {
        // Decia "a los veintiuno dias del mes de..." para cualquier fecha terminada en 21.
        assertEquals("veintiún días del mes de octubre de dos mil veintiséis",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2026, 10, 21)));
    }

    @Test
    public void testElDiaTreintaYUnoTambienVaApocopado() {
        assertEquals("treinta y un días del mes de octubre de dos mil veintiséis",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2026, 10, 31)));
    }

    @Test
    public void testElDiaUnoSigueSiendoPrimerDia() {
        // Convencion de los imprimibles legales, anterior a B11: no cambia.
        assertEquals("primer día del mes de noviembre de dos mil veintiséis",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2026, 11, 1)));
    }

    @Test
    public void testLosDiasQueNoTerminanEnUnoNoCambian() {
        assertEquals("treinta días del mes de octubre de dos mil veintiséis",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2026, 10, 30)));
        assertEquals("dos días del mes de enero de dos mil veintisiete",
                ConversorFechasALetras.comoDiaMesAnioEnLetras(LocalDate.of(2027, 1, 2)));
    }

    // ======================================================================
    // B103 (decision de Joel, 01/11/2026): el articulo sale del MISMO dueno que el dia.
    // Se prueba la FRASE COMPLETA ARMADA -- la clausula de apertura del contrato, con el
    // parametro puesto por el mismo conversor que usa el sistema -- porque el defecto no estaba
    // en ninguna de las dos mitades por separado: estaba en la union ("a los " + "primer dia").
    // ======================================================================

    @Test
    public void testLaFechaTraeSuArticulo() {
        assertEquals("al primer día del mes de noviembre de dos mil veintiséis",
                ConversorFechasALetras.comoFechaConArticulo(LocalDate.of(2026, 11, 1)));
        assertEquals("a los veintiún días del mes de octubre de dos mil veintiséis",
                ConversorFechasALetras.comoFechaConArticulo(LocalDate.of(2026, 10, 21)));
        assertEquals("a los treinta y un días del mes de octubre de dos mil veintiséis",
                ConversorFechasALetras.comoFechaConArticulo(LocalDate.of(2026, 10, 31)));
        assertEquals("", ConversorFechasALetras.comoFechaConArticulo(null));
    }

    // El cruce con la frase completa armada (la cláusula de apertura del contrato) vive en
    // ServicioFormalizacionTest: construirClausulasContrato es de visibilidad de paquete.
}
