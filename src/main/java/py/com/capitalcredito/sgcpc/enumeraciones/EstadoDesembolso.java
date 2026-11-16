package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de desembolsos.estado_desembolso (bd/01_esquema.sql, Doc. 00 §7, E6). */
public enum EstadoDesembolso {
    CONFIRMADO("Confirmado"),
    REVERTIDO("Revertido");

    private final String etiqueta;

    EstadoDesembolso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
