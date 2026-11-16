package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.ResultadoGestionFaltanteException;

/**
 * RF24 §10 (Tanda 2, 14/10/2026, pedido de Joel): "Resultado" obligatorio al registrar una
 * gestión. Solo se prueban las validaciones puras de registrarGestion (lanzan ANTES de abrir
 * Connection, mismo criterio que el resto del proyecto) -- nunca el camino que sí persiste, que
 * necesita base.
 */
public class ServicioMoraTest {

    private final ServicioMora servicio = new ServicioMora();

    @Test
    public void testRegistrarGestionSinResultadoLanzaAdvertencia() {
        assertThrows(ResultadoGestionFaltanteException.class, () -> servicio.registrarGestion(
                1L, 1L, TipoGestionCobranza.LLAMADA, null, null, null, null, 1L));
    }

    @Test
    public void testRegistrarGestionConResultadoVacioLanzaAdvertencia() {
        assertThrows(ResultadoGestionFaltanteException.class, () -> servicio.registrarGestion(
                1L, 1L, TipoGestionCobranza.LLAMADA, "   ", null, null, null, 1L));
    }

    /** Confirma que el chequeo de "Resultado" corre ANTES del de "tipo PAGO_PARCIAL" -- no debe
     * importar el orden de los parámetros para este caso, PAGO_PARCIAL ya se rechaza antes. */
    @Test
    public void testRegistrarGestionPagoParcialSigueRechazado() {
        assertThrows(NegocioException.class, () -> servicio.registrarGestion(
                1L, 1L, TipoGestionCobranza.PAGO_PARCIAL, "algo", null, null, null, 1L));
    }

    @Test
    public void testRegistrarGestionSinMontoPrometidoConFechaPromesaLanzaAdvertencia() {
        assertThrows(NegocioException.class, () -> servicio.registrarGestion(
                1L, 1L, TipoGestionCobranza.LLAMADA, "No contesta", null, LocalDate.now().plusDays(3), null, 1L));
    }

    /**
     * B121 (11/11/2026): el detalle de auditoría del cambio automático de estado por mora. Lo lee
     * alguien que no estuvo ahí, así que nombra el préstamo por su N.º de operación; y sigue la
     * convención del resto del sistema (flecha tipográfica y punto final), de la que esta línea
     * era la única excepción.
     */
    @Test
    public void testDetalleDelCambioDeEstadoNombraElPrestamo() {
        assertEquals("Cambió el estado del préstamo CC-2026-00116: Activo → En mora.",
                ServicioMora.detalleCambioDeEstado("CC-2026-00116", EstadoPrestamo.ACTIVO, EstadoPrestamo.EN_MORA));
        assertEquals("Cambió el estado del préstamo CC-2026-00007: En mora → Activo.",
                ServicioMora.detalleCambioDeEstado("CC-2026-00007", EstadoPrestamo.EN_MORA, EstadoPrestamo.ACTIVO));
    }

    /** B121: el resumen del recálculo diario, sin el "(s)" que tenía (misma familia que B132). */
    @Test
    public void testDetalleDelRecalculoDiarioResuelveElPlural() {
        assertEquals("1 préstamo cambió de estado.", ServicioMora.detalleRecalculoDiario(1));
        assertEquals("0 préstamos cambiaron de estado.", ServicioMora.detalleRecalculoDiario(0));
        assertEquals("12 préstamos cambiaron de estado.", ServicioMora.detalleRecalculoDiario(12));
    }
}
