package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para RF16/RF17 -- el
 * préstamo ya no está PENDIENTE_DESEMBOLSO (se desembolsó, se anuló, etc.) en el momento de
 * imprimir el comprobante o confirmar el desembolso, típicamente por una carrera de dos
 * pestañas. Mismo criterio que {@code SolicitudYaDecididaException} (RF09/RF10): advertencia,
 * no error.
 */
public class PrestamoNoPendienteDesembolsoException extends NegocioException {

    public PrestamoNoPendienteDesembolsoException(String mensaje) {
        super(mensaje);
    }
}
