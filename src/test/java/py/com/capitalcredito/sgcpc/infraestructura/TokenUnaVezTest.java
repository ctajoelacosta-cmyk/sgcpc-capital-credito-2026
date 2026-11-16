package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Tanda 2 (14/10/2026, pedido de Joel): protección de doble clic en la sesión, sin tocar la
 * base -- causa real del hallazgo de RF24 (el botón deshabilitado por JS no alcanzó, ver
 * RF24_seguimiento_mora.md §10). Prueba solo la parte pura (consumir sobre un Set), sin
 * HttpSession. */
public class TokenUnaVezTest {

    @Test
    public void testPrimerConsumoDeUnTokenPendienteDevuelveTrue() {
        Set<String> pendientes = new HashSet<>();
        pendientes.add("abc123");
        assertTrue(TokenUnaVez.consumir(pendientes, "abc123"));
    }

    /** El caso del hallazgo: doble clic -- el mismo token llega dos veces. */
    @Test
    public void testSegundoConsumoDelMismoTokenDevuelveFalse() {
        Set<String> pendientes = new HashSet<>();
        pendientes.add("abc123");
        assertTrue(TokenUnaVez.consumir(pendientes, "abc123"));
        assertFalse(TokenUnaVez.consumir(pendientes, "abc123"));
    }

    @Test
    public void testTokenNuloDevuelveFalse() {
        assertFalse(TokenUnaVez.consumir(new HashSet<>(), null));
    }

    @Test
    public void testTokenDesconocidoDevuelveFalse() {
        assertFalse(TokenUnaVez.consumir(new HashSet<>(), "nunca-existio"));
    }

    @Test
    public void testConsumirUnTokenNoAfectaOtro() {
        Set<String> pendientes = new HashSet<>();
        pendientes.add("tokenFila27");
        pendientes.add("tokenFila29");
        assertTrue(TokenUnaVez.consumir(pendientes, "tokenFila27"));
        // el de la otra fila sigue pendiente -- un doble clic en un préstamo no bloquea otro.
        assertTrue(TokenUnaVez.consumir(pendientes, "tokenFila29"));
    }
}
