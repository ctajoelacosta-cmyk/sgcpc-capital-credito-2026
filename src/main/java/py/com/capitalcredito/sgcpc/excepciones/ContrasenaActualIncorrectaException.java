package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Bloque de seguridad (hallazgo de Joel): subtipo de NegocioException, solo para que
 * ControladorCambioContrasenaServlet pueda distinguir este caso puntual de las otras
 * validaciones del cambio de contraseña (confirmación no coincide, política de la nueva) y
 * contar los intentos seguidos -- a los 3 fallos cierra la sesión. Para cualquier otro código
 * que ya atrapaba NegocioException (ServicioAutenticacion.validarContrasenaActual la lanzaba
 * así antes), el comportamiento no cambia: sigue siendo una NegocioException con el mismo
 * mensaje.
 */
public class ContrasenaActualIncorrectaException extends NegocioException {

    public ContrasenaActualIncorrectaException() {
        super("La contraseña actual no es correcta.");
    }
}
