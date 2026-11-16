package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de documentos_expediente.estado_documento (bd/01_esquema.sql). */
public enum EstadoDocumento {
    VIGENTE("Vigente"),
    REEMPLAZADO("Reemplazado"),
    CERRADO("Cerrado"),
    ANULADO("Anulado");

    private final String etiqueta;

    EstadoDocumento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
