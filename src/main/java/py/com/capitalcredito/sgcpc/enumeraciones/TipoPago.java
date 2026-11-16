package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de pagos.tipo (bd/01_esquema.sql, Doc. 00 §10.1/§10.2, P09, E7). */
public enum TipoPago {
    NORMAL("Cobro normal"),
    PARCIAL("Pago parcial con promesa"),
    CANCELACION("Cancelación total"),
    ANTICIPO("Anticipo de capital");

    private final String etiqueta;

    TipoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
