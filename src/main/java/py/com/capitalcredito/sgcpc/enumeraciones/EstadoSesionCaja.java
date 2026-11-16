package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de sesiones_caja.estado_sesion (bd/01_esquema.sql, Doc. 00 §7, E6). */
public enum EstadoSesionCaja {
    ABIERTA("Abierta"),
    CERRADA("Cerrada");

    private final String etiqueta;

    EstadoSesionCaja(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
