package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF19 (pedido de Joel, 11/10/2026): subtipo de NegocioException para cuando el motivo de una
 * diferencia de arqueo falta o es demasiado corto (menos de 10 caracteres reales, sin contar
 * espacios al inicio y al final) -- no es un error del servidor, así que
 * `ControladorCajaServlet` lo distingue de una `NegocioException` cualquiera para mostrarlo
 * como advertencia (`MensajesFlash.guardarAdvertencia`), no como error.
 */
public class MotivoDiferenciaInsuficienteException extends NegocioException {

    public MotivoDiferenciaInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
