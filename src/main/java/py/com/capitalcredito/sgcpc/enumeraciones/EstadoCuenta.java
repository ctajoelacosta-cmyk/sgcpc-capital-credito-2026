package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de usuarios.estado_cuenta (bd/01_esquema.sql). */
public enum EstadoCuenta {
    ACTIVA("Activa"),
    BLOQUEADA("Bloqueada"),
    INACTIVA("Inactiva");

    private final String etiqueta;

    EstadoCuenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
