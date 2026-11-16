package py.com.capitalcredito.sgcpc.infraestructura;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpSession;

/**
 * Tanda 2 (14/10/2026, pedido de Joel): protección de doble clic para operaciones que NO tienen
 * (ni se les agrega) una columna {@code identificador_confirmacion} como pagos/desembolsos/
 * reversiones (RF24: {@code gestiones_cobranza} no la tiene, y agregarla pedía una migración).
 * Token de un solo uso, por sesión HTTP: se genera al mostrar el formulario
 * ({@link #generar}) y se consume al procesarlo ({@link #consumir}) -- un segundo POST con el
 * mismo token ya no lo encuentra pendiente, así que no vuelve a ejecutar la acción. Vive solo en
 * la sesión, nunca en la base (a diferencia de {@code identificador_confirmacion}, que sí
 * persiste para sobrevivir a un F5/recarga -- este mecanismo es más liviano, pensado solo para
 * el caso de doble clic dentro de la misma carga de página).
 */
public final class TokenUnaVez {

    private static final String ATRIBUTO_SESION = "tokensUnaVezPendientes";
    private static final SecureRandom GENERADOR = new SecureRandom();

    private TokenUnaVez() {
    }

    /** Genera un token nuevo y lo deja pendiente en la sesión. */
    public static String generar(HttpSession sesion) {
        byte[] bytesAleatorios = new byte[24];
        GENERADOR.nextBytes(bytesAleatorios);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);
        obtenerPendientes(sesion).add(token);
        return token;
    }

    /** true la primera vez que se consulta este token (y lo quita de pendientes); false
     * cualquier otra vez (ya consumido, nulo, o nunca existió). */
    public static boolean consumir(HttpSession sesion, String token) {
        return consumir(obtenerPendientes(sesion), token);
    }

    /** Pura, separada de HttpSession para poder probarla con JUnit sin un servlet container. */
    static boolean consumir(Set<String> pendientes, String token) {
        if (token == null) {
            return false;
        }
        return pendientes.remove(token);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> obtenerPendientes(HttpSession sesion) {
        Set<String> pendientes = (Set<String>) sesion.getAttribute(ATRIBUTO_SESION);
        if (pendientes == null) {
            pendientes = new HashSet<>();
            sesion.setAttribute(ATRIBUTO_SESION, pendientes);
        }
        return pendientes;
    }
}
