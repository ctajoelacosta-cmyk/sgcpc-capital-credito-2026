package py.com.capitalcredito.sgcpc.filtros;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.infraestructura.ContextoPeticion;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P01: exige sesion con usuario autenticado para cualquier ruta que
 * no sea publica (RF36, con el permiso de cada ruta validado en RNF11). Si la sesion vencio
 * (session-timeout, punto 2) el atributo ya no esta y esto se comporta igual que "nunca entro".
 */
public class FiltroAutenticacion implements Filter {

    public static final String ATRIBUTO_USUARIO = "usuarioAutenticado";
    /** Minutos de inactividad de esta sesión (copia de seguridad.minutos_inactividad al momento del login), para el aviso de sesión por vencer. */
    public static final String ATRIBUTO_MINUTOS_INACTIVIDAD = "minutosInactividad";
    /** Bloque de seguridad (hallazgo de Joel, punto 9): IP/navegador del login, para que
     * ListenerSesion (sin HttpServletRequest disponible en sessionDestroyed) pueda auditar
     * SESION_EXPIRADA con los mismos datos. */
    public static final String ATRIBUTO_IP_LOGIN = "ipLogin";
    public static final String ATRIBUTO_NAVEGADOR_LOGIN = "navegadorLogin";
    /** Marca que ControladorAutenticacionServlet.cerrarSesion pone antes de invalidate(), para
     * que ListenerSesion no audite SESION_EXPIRADA encima de un cierre voluntario (CERRAR_SESION) --
     * invalidate() dispara sessionDestroyed igual que un vencimiento por inactividad. */
    public static final String ATRIBUTO_CIERRE_VOLUNTARIO = "cierreVoluntario";

    private static final String[] RUTAS_PUBLICAS = {"/acceso", "/index.jsp", "/"};

    @Override
    public void init(FilterConfig filterConfig) {
        // Sin inicializacion adicional.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 2, PA-28): mismo IP/
        // navegador que ya lee el login (request.getRemoteAddr()/getHeader("User-Agent"), sin
        // helper propio) para que ServicioAuditoria.registrar/registrarCambioDeValor los tengan
        // disponibles sin que cada Servicio necesite el HttpServletRequest. El try/finally
        // envuelve el método ENTERO -- limpiar() corre para cualquier return de abajo (ruta
        // pública, sesión inválida, redirect) o el camino normal, nunca queda pegado al hilo.
        try {
            ContextoPeticion.establecer(httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
            String ruta = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

            if (esRutaPublica(ruta)) {
                chain.doFilter(request, response);
                return;
            }

            HttpSession sesion = httpRequest.getSession(false);
            Usuario usuario = sesion == null ? null : (Usuario) sesion.getAttribute(ATRIBUTO_USUARIO);
            if (usuario == null) {
                if (PeticionAjax.esAjax(httpRequest)) {
                    // Encontrado en la prueba de E3: un fetch() (ej. GET /utilidades/letras) que
                    // pisaba una sesion vencida recibia el sendRedirect de mas abajo y mostraba el
                    // HTML entero de /acceso como si fuera la respuesta esperada. Una peticion AJAX
                    // nunca sigue el redirect como si fuera navegacion: se le devuelve 401 con
                    // cuerpo vacio, para que el JS decida que mostrar (SweetAlert, nada, etc).
                    httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{}");
                    return;
                }
                httpResponse.sendRedirect(construirRedirectAAcceso(httpRequest));
                return;
            }

            // RF36: con cambio de contraseña pendiente, no se deja avanzar a ninguna otra pantalla.
            if (usuario.isDebeCambiarContrasena() && !esRutaDeCambioDeContrasenaOSalida(ruta)) {
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/cambiar-contrasena");
                return;
            }

            // Bloque de seguridad (hallazgo de Joel): sin esto, el botón "atrás" del navegador después de salir
            // puede mostrar una página interna ya cacheada sin volver a pedirla al servidor. Solo acá
            // (rutas con sesión) -- las públicas y /recursos/ (CSS/JS/imágenes) ya se filtraron arriba
            // y deben seguir cacheando normal.
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Expires", "0");

            chain.doFilter(request, response);
        } finally {
            ContextoPeticion.limpiar();
        }
    }

    /**
     * Bloque de seguridad (hallazgo de Joel): si el pedido original era un GET (nunca un POST --
     * no tiene sentido "reintentar" un formulario enviado), arma /acceso?destino=... con la
     * URL completa (con query string, dentro de /sgcpc) para que, tras loguearse,
     * ControladorAutenticacionServlet pueda volver ahí en vez de siempre a /panel. La validación
     * real (que sea una ruta interna) la hace ese controlador antes de redirigir -- acá solo se
     * arma el valor, nunca se confía en él todavía.
     */
    private String construirRedirectAAcceso(HttpServletRequest httpRequest) {
        String contextPath = httpRequest.getContextPath();
        if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
            return contextPath + "/acceso";
        }
        String query = httpRequest.getQueryString();
        String destino = httpRequest.getRequestURI() + (query == null ? "" : "?" + query);
        try {
            return contextPath + "/acceso?destino=" + java.net.URLEncoder.encode(destino, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return contextPath + "/acceso"; // UTF-8 siempre esta disponible en la JVM -- no deberia pasar nunca.
        }
    }

    private boolean esRutaPublica(String ruta) {
        if (ruta.startsWith("/recursos/")) {
            return true;
        }
        for (String rutaPublica : RUTAS_PUBLICAS) {
            if (ruta.equals(rutaPublica)) {
                return true;
            }
        }
        return false;
    }

    private boolean esRutaDeCambioDeContrasenaOSalida(String ruta) {
        return ruta.equals("/cambiar-contrasena") || ruta.equals("/salir");
    }

    @Override
    public void destroy() {
        // Sin recursos que liberar.
    }
}
