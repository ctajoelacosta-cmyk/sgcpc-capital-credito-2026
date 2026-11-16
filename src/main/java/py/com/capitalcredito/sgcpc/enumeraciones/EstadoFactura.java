package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de facturas.estado (bd/01_esquema.sql, RN-25, P09, E7). */
public enum EstadoFactura {
    EMITIDA("Emitida"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoFactura(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
