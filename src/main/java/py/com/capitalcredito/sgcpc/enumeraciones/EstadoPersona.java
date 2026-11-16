package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de personas.estado_persona (bd/01_esquema.sql). Sin pantalla de cambio en E3 (D-37). */
public enum EstadoPersona {
    ACTIVA("Activa"),
    INACTIVA("Inactiva");

    private final String etiqueta;

    EstadoPersona(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
