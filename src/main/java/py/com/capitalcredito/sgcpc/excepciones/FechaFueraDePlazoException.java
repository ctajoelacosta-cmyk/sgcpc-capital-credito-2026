package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para RF11 -- la fecha de
 * primer vencimiento elegida, al corregirse al siguiente día hábil, queda fuera del plazo
 * permitido. No es un error del servidor ni un dato mal escrito: es que el tiempo avanzó (o se
 * eligió una fecha al límite) y el plazo ya no da -- mismo criterio que
 * {@code SolicitudYaDecididaException} (RF09/RF10) y {@code CajaYaCerradaException} (RF19): se
 * muestra como advertencia (ícono y botón "Entendido"), no como error.
 */
public class FechaFueraDePlazoException extends NegocioException {

    public FechaFueraDePlazoException(String mensaje) {
        super(mensaje);
    }
}
