package py.com.capitalcredito.sgcpc.infraestructura;

import javax.servlet.http.HttpServletRequest;

/**
 * Bloque 7/E10 (D-136, pedido de Joel): mismo criterio que ya usaba FiltroAutenticacion
 * (encabezado "X-Requested-With: XMLHttpRequest", o "Accept: application/json") para reconocer
 * un pedido AJAX -- ahora compartido, para que un controlador pueda usarlo también y decidir si
 * responde un fragmento HTML/JSON en vez de la página completa (ver ControladorClientesServlet,
 * "listado vivo"). Una sola definición, no dos que puedan desalinearse con el tiempo.
 */
public final class PeticionAjax {

    private PeticionAjax() {
    }

    public static boolean esAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equals(requestedWith) || (accept != null && accept.contains("application/json"));
    }
}
