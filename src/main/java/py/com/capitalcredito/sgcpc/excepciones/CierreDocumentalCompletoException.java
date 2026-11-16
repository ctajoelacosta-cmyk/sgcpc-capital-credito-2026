package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF25 (pedido de Joel, 13/10/2026): subtipo de NegocioException para cuando se solicita o se
 * autoriza la reversión de un pago cuyo préstamo ya tiene el cierre documental COMPLETO (el
 * pagaré original ya se devolvió al cliente) -- no es un error del servidor, así que
 * `ControladorReversionesServlet` lo distingue de una `NegocioException` cualquiera para
 * mostrarlo como advertencia (`MensajesFlash.guardarAdvertencia`), no como error. Mismo patrón
 * que `MotivoDiferenciaInsuficienteException` (RF19) y `MontoPrometidoFaltanteException` (RF24).
 */
public class CierreDocumentalCompletoException extends NegocioException {

    public CierreDocumentalCompletoException(String mensaje) {
        super(mensaje);
    }
}
