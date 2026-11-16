package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF24 §10 (Tanda 2, 14/10/2026, pedido de Joel): subtipo de NegocioException para cuando se
 * registra una gestión de mora sin "Resultado" -- no es un error del servidor, así que
 * `ControladorMoraServlet` lo distingue de una `NegocioException` cualquiera para mostrarlo como
 * advertencia (`MensajesFlash.guardarAdvertencia`), no como error. Mismo patrón que
 * `MontoPrometidoFaltanteException` (también RF24) y `MotivoDiferenciaInsuficienteException`
 * (RF19).
 */
public class ResultadoGestionFaltanteException extends NegocioException {

    public ResultadoGestionFaltanteException(String mensaje) {
        super(mensaje);
    }
}
