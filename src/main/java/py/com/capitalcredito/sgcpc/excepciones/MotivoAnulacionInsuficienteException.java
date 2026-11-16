package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF14 (pedido de Joel, 11/10/2026): subtipo de NegocioException para cuando el motivo de
 * anulación de un préstamo no llega al mínimo de caracteres reales (mismo criterio que
 * {@code MotivoDiferenciaInsuficienteException}, RF19) -- no es que algo salió mal, es que
 * falta completar el motivo con contenido real. Se muestra como advertencia ("Entendido"), no
 * error.
 */
public class MotivoAnulacionInsuficienteException extends NegocioException {

    public MotivoAnulacionInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
