package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.Test;

/**
 * D-115: sin JAMÁS llamar a fijarParaCargador, hoy()/ahora() tienen que seguir dando el reloj
 * real -- esta es la garantía de que el instante simulado del cargador de 7.4 nunca se filtra a
 * ningún hilo que no lo haya pedido explícitamente (en la práctica, solo el cargador lo pide:
 * ningún Servlet/Filtro lo llama nunca).
 */
public class RelojTest {

    @Test
    public void sinFijarNuncaElCargadorDevuelveElRelojReal() {
        LocalDateTime antes = LocalDateTime.now(Reloj.ZONA);
        LocalDateTime ahora = Reloj.ahora();
        LocalDateTime despues = LocalDateTime.now(Reloj.ZONA);

        assertFalse("ahora() no debería ser anterior al reloj real", ahora.isBefore(antes));
        assertFalse("ahora() no debería ser posterior al reloj real", ahora.isAfter(despues));
        assertEquals(LocalDate.now(Reloj.ZONA), Reloj.hoy());
    }

    @Test
    public void fijarYLiberarNuncaDejaElInstanteSimuladoPegado() {
        LocalDateTime instanteSimulado = LocalDateTime.of(2020, 1, 15, 10, 0);
        Reloj.fijarParaCargador(instanteSimulado);
        try {
            assertEquals(instanteSimulado, Reloj.ahora());
            assertEquals(LocalDate.of(2020, 1, 15), Reloj.hoy());
        } finally {
            Reloj.liberarCargador();
        }

        assertEquals(LocalDate.now(Reloj.ZONA), Reloj.hoy());
    }
}
