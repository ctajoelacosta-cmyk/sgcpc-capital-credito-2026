package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para la carrera de dos
 * pestañas al abrir caja -- otra pestaña/sesión ya la abrió en la angosta ventana antes de este
 * intento. Mismo criterio que {@code CajaYaCerradaException} (RF19): advertencia, no error.
 */
public class CajaYaAbiertaException extends NegocioException {

    public CajaYaAbiertaException(String mensaje) {
        super(mensaje);
    }
}
