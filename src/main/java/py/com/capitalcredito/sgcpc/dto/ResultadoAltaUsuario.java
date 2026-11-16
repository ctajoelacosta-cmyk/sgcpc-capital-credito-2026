package py.com.capitalcredito.sgcpc.dto;

/**
 * RF37 (hallazgo de Joel, probando en pantalla): el alta ya no deja la cuenta con la contraseña
 * inicial fija ("Cambiar@2026", conocida por cualquiera que haya leído el código o la
 * documentación) -- genera una temporal aleatoria, igual que restablecerContrasena, que el
 * controlador necesita para mostrarla una sola vez al administrador.
 */
public class ResultadoAltaUsuario {

    private final long idUsuario;
    private final String contrasenaTemporal;

    public ResultadoAltaUsuario(long idUsuario, String contrasenaTemporal) {
        this.idUsuario = idUsuario;
        this.contrasenaTemporal = contrasenaTemporal;
    }

    public long getIdUsuario() {
        return idUsuario;
    }

    public String getContrasenaTemporal() {
        return contrasenaTemporal;
    }
}
