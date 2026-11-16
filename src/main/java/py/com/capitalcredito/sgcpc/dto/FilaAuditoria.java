package py.com.capitalcredito.sgcpc.dto;

/** Una fila del reporte Auditoría (§13, Bloque 4, E8). Todo ya formateado desde el servicio.
 * Bloque de seguridad (hallazgo de Joel): direccionIp/navegador/usuarioEscrito sumados -- los
 * dos primeros ya se guardaban desde antes, usuarioEscrito es nuevo (solo tiene valor en
 * ACCESO_USUARIO_INEXISTENTE, que además no tiene un usuario real -- ver AuditoriaDAO). */
public class FilaAuditoria {

    private final String fechaHora;
    private final String usuario;
    private final String operacion;
    private final String tipoEntidad;
    private final long idEntidad;
    private final String detalle;
    private final String direccionIp;
    private final String navegador;
    private final String usuarioEscrito;

    public FilaAuditoria(String fechaHora, String usuario, String operacion, String tipoEntidad, long idEntidad,
            String detalle, String direccionIp, String navegador, String usuarioEscrito) {
        this.fechaHora = fechaHora;
        this.usuario = usuario;
        this.operacion = operacion;
        this.tipoEntidad = tipoEntidad;
        this.idEntidad = idEntidad;
        this.detalle = detalle;
        this.direccionIp = direccionIp;
        this.navegador = navegador;
        this.usuarioEscrito = usuarioEscrito;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public long getIdEntidad() {
        return idEntidad;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getDireccionIp() {
        return direccionIp;
    }

    public String getNavegador() {
        return navegador;
    }

    /** Solo tiene valor para ACCESO_USUARIO_INEXISTENTE (el nombre de usuario tal cual se
     * escribió, cuando no hay ninguna cuenta real) -- null en cualquier otra fila. */
    public String getUsuarioEscrito() {
        return usuarioEscrito;
    }

    /** RF40/F3 (pedido de Joel, 13/10/2026): mismo texto legible que ya muestra la pantalla
     * ("Este equipo (servidor)", "Chrome · Windows") -- para el PDF/CSV, que hoy solo traían el
     * valor crudo. Getters derivados, NO se tocan los campos/getters crudos de arriba: la
     * pantalla sigue llamando FormateadorTexto directamente sobre getDireccionIp()/getNavegador()
     * (esos dos métodos no son idempotentes -- aplicarlos dos veces podría dar un resultado
     * distinto, ej. "Firefox · Mac" -> "Firefox" -- así que no conviene reemplazar los campos
     * crudos por el valor ya formateado). */
    public String getDireccionIpLegible() {
        return py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoDireccionIpLegible(direccionIp);
    }

    public String getNavegadorResumido() {
        return py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoNavegadorResumido(navegador);
    }
}
