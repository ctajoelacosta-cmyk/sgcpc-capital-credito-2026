package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de liquidaciones_anticipadas.estado_liquidacion (bd/01_esquema.sql, P09, E7). */
public enum EstadoLiquidacionAnticipada {
    GENERADA("Generada"),
    APLICADA("Aplicada"),
    CANCELADA("Cancelada"),
    // E9/Bloque 5: un anticipo o cancelación revertido no se borra -- queda REVERTIDA, con la
    // constancia (si tenía) intacta para reimprimir, y el plan anterior vuelve a ser el vigente.
    REVERTIDA("Revertida");

    private final String etiqueta;

    EstadoLiquidacionAnticipada(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
