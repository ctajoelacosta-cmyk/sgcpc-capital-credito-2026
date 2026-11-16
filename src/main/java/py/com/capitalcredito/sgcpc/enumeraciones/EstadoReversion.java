package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de reversiones_pago.estado (bd/01_esquema.sql, Doc. 00 §7, E9/Bloque 5). */
public enum EstadoReversion {
    SOLICITADA("Solicitada"),
    AUTORIZADA("Autorizada"),
    RECHAZADA("Rechazada");

    private final String etiqueta;

    EstadoReversion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
