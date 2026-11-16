package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para la carrera de dos
 * pestañas al revisar la diferencia de un arqueo -- otro gerente (u otra pestaña del mismo
 * gerente) ya la marcó revisada. Mismo criterio que {@code CajaYaCerradaException} (RF19):
 * advertencia, no error.
 */
public class DiferenciaYaRevisadaException extends NegocioException {

    public DiferenciaYaRevisadaException(String mensaje) {
        super(mensaje);
    }
}
