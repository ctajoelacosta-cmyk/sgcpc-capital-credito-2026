package py.com.capitalcredito.sgcpc.enumeraciones;

/**
 * Espejo del CHECK de movimientos_caja.tipo (bd/01_esquema.sql, Doc. 00 §10.1, E6). En E6 solo
 * se generan ENTREGA_GERENTE, RENDICION_GERENTE y DESEMBOLSO; COBRO/VUELTO quedan para E7 y
 * COMPENSACION para E9 (reversiones) -- se declaran los 6 ahora porque son el mismo CHECK.
 */
public enum TipoMovimientoCaja {
    ENTREGA_GERENTE("Entrega del gerente"),
    RENDICION_GERENTE("Rendición al gerente"),
    DESEMBOLSO("Desembolso"),
    COBRO("Cobro"),
    VUELTO("Vuelto"),
    COMPENSACION("Compensación");

    private final String etiqueta;

    TipoMovimientoCaja(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
