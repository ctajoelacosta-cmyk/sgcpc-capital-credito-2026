package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF24 (pedido de Joel, 12/10/2026): subtipo de NegocioException para cuando se registra una
 * gestión de mora con fecha de promesa pero sin monto prometido -- no es un error del servidor,
 * así que `ControladorMoraServlet` lo distingue de una `NegocioException` cualquiera para
 * mostrarlo como advertencia (`MensajesFlash.guardarAdvertencia`), no como error. Mismo patrón
 * que `MotivoDiferenciaInsuficienteException` (RF19).
 */
public class MontoPrometidoFaltanteException extends NegocioException {

    public MontoPrometidoFaltanteException(String mensaje) {
        super(mensaje);
    }
}
