package py.com.capitalcredito.sgcpc.enumeraciones;

/**
 * Espejo del CHECK de gestiones_cobranza.tipo (bd/01_esquema.sql, P09/P11, E7). En E7 solo se
 * genera PAGO_PARCIAL (RN-20); LLAMADA/MENSAJE/VISITA_LOCAL quedan para el seguimiento de mora de
 * E8 -- se declaran los 4 ahora porque son el mismo CHECK.
 */
public enum TipoGestionCobranza {
    LLAMADA("Llamada"),
    MENSAJE("Mensaje"),
    VISITA_LOCAL("Visita al local"),
    PAGO_PARCIAL("Pago parcial con promesa");

    private final String etiqueta;

    TipoGestionCobranza(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
