package py.com.capitalcredito.sgcpc.infraestructura;

import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Token CSRF por sesion, reutilizable desde cualquier formulario POST
 * (E2 en adelante). Un token por HttpSession: se genera la primera vez
 * que se pide (incluida la propia pantalla de acceso, antes de
 * autenticar) y se valida en FiltroCsrf comparando contra el parametro
 * de formulario "tokenCsrf".
 */
public final class Csrf {

    private static final String ATRIBUTO_SESION = "tokenCsrf";
    private static final String PARAMETRO_FORMULARIO = "tokenCsrf";
    private static final SecureRandom GENERADOR = new SecureRandom();

    private Csrf() {
    }

    public static String obtenerOCrearToken(HttpSession sesion) {
        String token = (String) sesion.getAttribute(ATRIBUTO_SESION);
        if (token == null) {
            byte[] bytesAleatorios = new byte[32];
            GENERADOR.nextBytes(bytesAleatorios);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);
            sesion.setAttribute(ATRIBUTO_SESION, token);
        }
        return token;
    }

    public static boolean esValido(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return false;
        }
        String tokenSesion = (String) sesion.getAttribute(ATRIBUTO_SESION);
        String tokenFormulario = request.getParameter(PARAMETRO_FORMULARIO);
        return tokenSesion != null && tokenSesion.equals(tokenFormulario);
    }
}
