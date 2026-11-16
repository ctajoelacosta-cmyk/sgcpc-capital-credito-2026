package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de archivos_binarios.origen_archivo (bd/01_esquema.sql). */
public enum OrigenArchivo {
    CARGADO("Cargado"),
    JASPER("Generado por el sistema");

    private final String etiqueta;

    OrigenArchivo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
