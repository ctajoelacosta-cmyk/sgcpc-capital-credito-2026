package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;

public class ServicioCotizacionTest {

    private final ServicioCotizacion servicio = new ServicioCotizacion();

    private static final BigDecimal MONTO_MINIMO = new BigDecimal("500000");
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("15000000");

    @Test
    public void testMontoBajoElMinimoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarMontoDentroDeLimites(new BigDecimal("100000"), MONTO_MINIMO, MONTO_MAXIMO));
    }

    @Test
    public void testMontoSobreElMaximoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarMontoDentroDeLimites(new BigDecimal("20000000"), MONTO_MINIMO, MONTO_MAXIMO));
    }

    @Test
    public void testMontoDentroDeLimitesNoLanza() {
        servicio.validarMontoDentroDeLimites(new BigDecimal("2000000"), MONTO_MINIMO, MONTO_MAXIMO);
    }

    @Test
    public void testPlazoBajoElMinimoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarPlazoDentroDeLimites(1, 3, 24));
    }

    @Test
    public void testPlazoSobreElMaximoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarPlazoDentroDeLimites(48, 3, 24));
    }

    @Test
    public void testPlazoDentroDeLimitesNoLanza() {
        servicio.validarPlazoDentroDeLimites(6, 3, 24);
    }

    // RF01-O: validarRangoFechaPrimerVencimiento reutiliza ServicioFormalizacion.
    // validarPrimerVencimientoFormalizacion -- estos casos espejan sus límites (debajo del
    // mínimo, encima del máximo, en los dos bordes), usando "hoy" como referencia.

    @Test
    public void testRangoFechaAntesDelMinimoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarRangoFechaPrimerVencimiento(
                LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-02"), 10, 45));
    }

    @Test
    public void testRangoFechaDespuesDelMaximoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarRangoFechaPrimerVencimiento(
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-01-02"), 10, 45));
    }

    @Test
    public void testRangoFechaEnLosBordesNoLanza() {
        LocalDate hoy = LocalDate.parse("2026-01-02");
        servicio.validarRangoFechaPrimerVencimiento(hoy.plusDays(10), hoy, 10, 45);
        servicio.validarRangoFechaPrimerVencimiento(hoy.plusDays(45), hoy, 10, 45);
    }

    @Test
    public void testRangoFechaMensajeConFechasConcretas() {
        LocalDate hoy = LocalDate.parse("2026-01-02");
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarRangoFechaPrimerVencimiento(hoy, hoy, 10, 45));
        assertEquals("La fecha del primer vencimiento debe estar entre 12/01/2026 y 16/02/2026. "
                + "Elija una fecha entre 10 y 45 días desde hoy.", error.getMessage());
    }

    // "Comparar plazos" (pedido de Joel): construirPlazosComparacion arma la lista de plazos a
    // cotizar para la tabla -- 6/12/18/24 recortado al rango vigente, más el plazo actual
    // insertado en su lugar si no es ninguno de esos cuatro.

    @Test
    public void testPlazosComparacionConRangoAmplioDaLosCuatroEstandar() {
        assertEquals(Arrays.asList(6, 12, 18, 24), servicio.construirPlazosComparacion(3, 36, 12));
    }

    @Test
    public void testPlazosComparacionConPlazoActualYaEnLaListaNoDuplica() {
        assertEquals(Arrays.asList(6, 12, 18, 24), servicio.construirPlazosComparacion(3, 36, 12));
    }

    @Test
    public void testPlazosComparacionInsertaPlazoActualEnElMedio() {
        assertEquals(Arrays.asList(6, 9, 12, 18, 24), servicio.construirPlazosComparacion(3, 36, 9));
    }

    @Test
    public void testPlazosComparacionInsertaPlazoActualAntesDeTodos() {
        assertEquals(Arrays.asList(3, 6, 12, 18, 24), servicio.construirPlazosComparacion(3, 36, 3));
    }

    @Test
    public void testPlazosComparacionInsertaPlazoActualDespuesDeTodos() {
        assertEquals(Arrays.asList(6, 12, 18, 24, 30), servicio.construirPlazosComparacion(3, 36, 30));
    }

    @Test
    public void testPlazosComparacionRecortaAlRangoVigente() {
        assertEquals(Arrays.asList(6), servicio.construirPlazosComparacion(3, 10, 6));
    }

    @Test
    public void testPlazosComparacionConRangoQueNoIncluyeNingunEstandar() {
        assertEquals(Arrays.asList(9), servicio.construirPlazosComparacion(7, 11, 9));
    }
}
