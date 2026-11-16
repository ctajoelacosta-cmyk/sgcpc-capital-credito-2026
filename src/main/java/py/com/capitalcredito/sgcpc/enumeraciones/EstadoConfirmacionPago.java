package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de pagos.estado_pago (bd/01_esquema.sql, P09, E7). Mismo patrón que EstadoDesembolso. */
public enum EstadoConfirmacionPago {
    CONFIRMADO("Confirmado"),
    REVERTIDO("Revertido");

    private final String etiqueta;

    EstadoConfirmacionPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
