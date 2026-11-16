package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de solicitudes_credito.estado_solicitud (bd/01_esquema.sql, Doc. 00 §7). */
public enum EstadoSolicitud {
    BORRADOR("Borrador"),
    PENDIENTE_APROBACION("Pendiente de aprobación"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada"),
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoSolicitud(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
