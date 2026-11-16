package py.com.capitalcredito.sgcpc.dto;

/**
 * RF37 (hallazgo de Joel): ServicioUsuarios.restablecerContrasena devuelve además si la cuenta
 * estaba BLOQUEADA y se desbloqueó en la misma operación -- el controlador lo necesita para que
 * el mensaje al administrador lo diga explícitamente ("además se desbloqueó la cuenta").
 */
public class ResultadoRestablecerContrasena {

    private final String contrasenaTemporal;
    private final boolean seDesbloqueoLaCuenta;

    public ResultadoRestablecerContrasena(String contrasenaTemporal, boolean seDesbloqueoLaCuenta) {
        this.contrasenaTemporal = contrasenaTemporal;
        this.seDesbloqueoLaCuenta = seDesbloqueoLaCuenta;
    }

    public String getContrasenaTemporal() {
        return contrasenaTemporal;
    }

    public boolean isSeDesbloqueoLaCuenta() {
        return seDesbloqueoLaCuenta;
    }
}
