package py.com.capitalcredito.sgcpc.enumeraciones;

/** Espejo del CHECK de usuarios.perfil_usuario (bd/01_esquema.sql). */
public enum PerfilUsuario {
    OFICIAL_CREDITO("Oficial de crédito"),
    // D7b (pedido de Joel): solo la etiqueta visible cambia a "Gerente" (para no confundirla con
    // el perfil nuevo ADMINISTRADOR) -- el nombre interno GERENTE_ADMINISTRADOR, la base, los
    // permisos y los usuarios no se tocan.
    GERENTE_ADMINISTRADOR("Gerente"),
    // Hallazgo de Joel (RF37, 10/10/2026): el anteproyecto firmado dice "cajera" -- solo cambia
    // la etiqueta visible; el valor interno CAJERO (nombre, base, permisos) no se toca.
    CAJERO("Cajera"),
    // D7 (pedido de Joel, anteproyecto entregado): acceso a TODO el sistema (TOTAL en todos los
    // permisos, ver ServicioAutorizacion) -- GERENTE_ADMINISTRADOR no se toca, se agrega aparte.
    ADMINISTRADOR("Administrador");

    private final String etiqueta;

    PerfilUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre legible para pantalla; name()/valueOf() siguen usándose para la base. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
