package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Usuarios y accesos" (C-4.2, pedido de Joel). Todo ya formateado -- mismo
 * molde que el resto de los informes de este bloque. Nunca lleva el hash de la contraseña ni
 * ningún otro dato de seguridad (pedido explícito de Joel): solo los mismos campos que ya se ven
 * en /usuarios. */
public class FilaInformeUsuario {

    private final String nombreUsuario;
    private final String nombreCompleto;
    private final String cedula;
    private final String perfil;
    private final String estado;
    private final String debeCambiarContrasena;
    private final String ultimoAcceso;

    public FilaInformeUsuario(String nombreUsuario, String nombreCompleto, String cedula, String perfil,
            String estado, String debeCambiarContrasena, String ultimoAcceso) {
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.cedula = cedula;
        this.perfil = perfil;
        this.estado = estado;
        this.debeCambiarContrasena = debeCambiarContrasena;
        this.ultimoAcceso = ultimoAcceso;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCedula() {
        return cedula;
    }

    public String getPerfil() {
        return perfil;
    }

    public String getEstado() {
        return estado;
    }

    /** "Sí" / "No". */
    public String getDebeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    /** Fecha y hora, o "Nunca" si el usuario todavía no ingresó. */
    public String getUltimoAcceso() {
        return ultimoAcceso;
    }
}
