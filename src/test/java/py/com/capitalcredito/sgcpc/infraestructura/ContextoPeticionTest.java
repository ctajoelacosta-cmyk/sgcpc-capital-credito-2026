package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;

/**
 * Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 2, PA-28): valor por defecto
 * null (ningún establecer() previo en este hilo), y que limpiar() lo vuelve a null después de
 * haberlo llenado -- las dos garantías que importan para que FiltroAutenticacion no deje nada
 * pegado al hilo entre peticiones. @After limpia siempre, para que un assert fallido en un
 * método no deje un valor pegado que arruine el método siguiente.
 */
public class ContextoPeticionTest {

    @After
    public void limpiarDespuesDeCadaPrueba() {
        ContextoPeticion.limpiar();
    }

    @Test
    public void valorPorDefectoEsNull() {
        assertNull(ContextoPeticion.obtenerIp());
        assertNull(ContextoPeticion.obtenerNavegador());
    }

    @Test
    public void establecerYLuegoLimpiarVuelveANull() {
        ContextoPeticion.establecer("192.168.1.10", "Mozilla/5.0 Chrome/120");

        assertEquals("192.168.1.10", ContextoPeticion.obtenerIp());
        assertEquals("Mozilla/5.0 Chrome/120", ContextoPeticion.obtenerNavegador());

        ContextoPeticion.limpiar();

        assertNull(ContextoPeticion.obtenerIp());
        assertNull(ContextoPeticion.obtenerNavegador());
    }
}
