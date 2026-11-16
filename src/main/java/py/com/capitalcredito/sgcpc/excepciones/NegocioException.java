package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Error de negocio con mensaje listo para mostrar al usuario (Doc. 10
 * §10.18: nunca SQL, stacktrace ni ruta de servidor en pantalla). La
 * lanzan los Servicios cuando una regla no se cumple o una accion no esta
 * permitida; los Servlet la atrapan y muestran getMessage() tal cual.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
