package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de prestamos.estado_prestamo (bd/01_esquema.sql, Doc. 00 §7). */
public enum EstadoPrestamo {
    PENDIENTE_DESEMBOLSO("Pendiente de desembolso"),
    ACTIVO("Activo"),
    EN_MORA("En mora"),
    FINALIZADO("Finalizado"),
    ANULADO_ANTES_DESEMBOLSO("Anulado antes del desembolso");

    private final String etiqueta;

    EstadoPrestamo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
