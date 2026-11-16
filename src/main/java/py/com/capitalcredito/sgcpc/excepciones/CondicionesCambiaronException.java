package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para RF08 -- al aprobar, el
 * tope de tasa vigente cambió o la capacidad de pago (revalidada con los datos actuales) ya no
 * da, aunque la solicitud era correcta cuando se simuló/envió. No es un error del servidor: es
 * que las condiciones cambiaron entre que se armó la solicitud y que se decidió -- mismo
 * criterio que {@code FechaFueraDePlazoException} (RF11): advertencia, no error.
 */
public class CondicionesCambiaronException extends NegocioException {

    public CondicionesCambiaronException(String mensaje) {
        super(mensaje);
    }
}
