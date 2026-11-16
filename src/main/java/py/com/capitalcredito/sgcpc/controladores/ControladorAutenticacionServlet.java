package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso.Tipo;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutenticacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;

/**
 * P01 (Doc. 00 §12 / Doc. 10 §10.5 / Doc. 10 §10.19: ControladorAutenticacionServlet).
 * Mapeado a /acceso (GET muestra el formulario o redirige a /panel si ya
 * hay sesion; POST procesa el login) y /salir (cierra sesion).
 */
public class ControladorAutenticacionServlet extends HttpServlet {

    private static final String VISTA_ACCESO = "/WEB-INF/vistas/acceso.jsp";

    private final ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/salir".equals(request.getServletPath())) {
            cerrarSesion(request, response);
            return;
        }
        HttpSession sesion = request.getSession(false);
        if (sesion != null && sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO) != null) {
            response.sendRedirect(request.getContextPath() + "/panel");
            return;
        }
        // Bloque de seguridad (hallazgo de Joel): mismo mensaje único (RNF09) para no
        // revelar nada -- esto solo avisa POR QUÉ se cerró la sesión, no dice si la cuenta existe
        // ni nada sobre su estado.
        String mensajeMotivo = "intentosFallidosCambioContrasena".equals(request.getParameter("motivo"))
                ? "Se cerró la sesión por varios intentos fallidos al cambiar la contraseña. Vuelva a ingresar."
                : null;
        mostrarFormulario(request, response, mensajeMotivo);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String nombreUsuario = request.getParameter("nombreUsuario");
        String contrasena = request.getParameter("contrasena");

        try {
            ResultadoAcceso resultado = servicioAutenticacion.autenticar(nombreUsuario, contrasena,
                    request.getRemoteAddr(), request.getHeader("User-Agent"));

            if (resultado.getTipo() == Tipo.ACCESO_CONCEDIDO || resultado.getTipo() == Tipo.DEBE_CAMBIAR_CONTRASENA) {
                abrirSesion(request, nombreUsuario);
                // Bloque de seguridad (hallazgo de Joel): vuelve a la página interna que se había
                // pedido (si la había y es válida) -- pero el cambio de contraseña obligatorio
                // siempre tiene prioridad, nunca se saltea por un destino pedido.
                String destinoSolicitado = request.getParameter("destino");
                String destino;
                if (resultado.getTipo() == Tipo.DEBE_CAMBIAR_CONTRASENA) {
                    destino = request.getContextPath() + "/cambiar-contrasena";
                } else if (esDestinoInternoValido(destinoSolicitado, request.getContextPath())) {
                    destino = destinoSolicitado;
                } else {
                    destino = request.getContextPath() + "/panel";
                }
                response.sendRedirect(destino);
                return;
            }

            mostrarFormulario(request, response, resultado.getMensaje());
        } catch (SQLException error) {
            // Nunca se muestra el detalle tecnico en pantalla (Doc. 10 §10.18); si al log (REGLAS_DESARROLLO.md §15).
            getServletContext().log("Error de base de datos al procesar el login de \"" + nombreUsuario + "\".", error);
            mostrarFormulario(request, response, "No se pudo conectar con el sistema. Intente nuevamente.");
        }
    }

    private void abrirSesion(HttpServletRequest request, String nombreUsuario) throws ServletException {
        try {
            Usuario usuario = servicioAutenticacion.buscarUsuarioParaSesion(nombreUsuario);
            request.changeSessionId();
            HttpSession sesion = request.getSession();
            sesion.setAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO, usuario);
            int minutosInactividad = servicioParametros.obtenerMinutosInactividad();
            sesion.setMaxInactiveInterval(minutosInactividad * 60);
            // Para el aviso de sesión por vencer (recursos/js/aviso-sesion.js, plantilla/pie.jsp):
            // se guarda el valor ya leído en la sesión en vez de volver a consultar el parámetro
            // en cada página. Si el gerente cambia el parámetro después, solo afecta a las
            // sesiones que se abran de ahí en más (igual que el timeout mismo, ver D-2 del plan E2).
            sesion.setAttribute(FiltroAutenticacion.ATRIBUTO_MINUTOS_INACTIVIDAD, minutosInactividad);
            // Bloque de seguridad (hallazgo de Joel): se guardan acá para que el
            // HttpSessionListener (sin HttpServletRequest disponible) pueda auditar
            // SESION_EXPIRADA con la misma IP/navegador del login, cuando la sesión vence sola.
            sesion.setAttribute(FiltroAutenticacion.ATRIBUTO_IP_LOGIN, request.getRemoteAddr());
            sesion.setAttribute(FiltroAutenticacion.ATRIBUTO_NAVEGADOR_LOGIN, request.getHeader("User-Agent"));
        } catch (SQLException error) {
            throw new ServletException("No se pudo abrir la sesión.", error);
        }
    }

    private void cerrarSesion(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            Usuario usuario = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
            if (usuario != null) {
                // Bloque de seguridad (hallazgo de Joel, punto 9): marca que este cierre fue
                // voluntario -- el HttpSessionListener lo revisa para no auditar SESION_EXPIRADA
                // encima de este CERRAR_SESION (invalidate() dispara sessionDestroyed también acá).
                sesion.setAttribute(FiltroAutenticacion.ATRIBUTO_CIERRE_VOLUNTARIO, Boolean.TRUE);
                try {
                    servicioAutenticacion.registrarCierreSesion(usuario.getIdUsuario(), request.getRemoteAddr(),
                            request.getHeader("User-Agent"));
                } catch (SQLException error) {
                    // No impide cerrar la sesion si falla el registro de auditoria, pero se deja rastro.
                    getServletContext().log("No se pudo auditar el cierre de sesión de \"" + usuario.getNombreUsuario() + "\".", error);
                }
            }
            sesion.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/acceso");
    }

    /**
     * Bloque de seguridad (hallazgo de Joel): solo direcciones internas de /sgcpc -- nunca una
     * URL absoluta ni "//otro-host" (así se evita un open redirect si alguien manda un
     * ?destino= armado a mano). "/acceso" y "/salir" quedan afuera para no armar un loop.
     */
    private boolean esDestinoInternoValido(String destino, String contextPath) {
        if (destino == null || destino.isEmpty() || destino.contains("://") || destino.startsWith("//")) {
            return false;
        }
        if (!destino.startsWith(contextPath + "/")) {
            return false;
        }
        String ruta = destino.substring(contextPath.length());
        return !ruta.equals("/acceso") && !ruta.startsWith("/acceso?") && !ruta.equals("/salir");
    }

    private void mostrarFormulario(HttpServletRequest request, HttpServletResponse response, String mensajeError)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        String destino = request.getParameter("destino");
        if (esDestinoInternoValido(destino, request.getContextPath())) {
            request.setAttribute("destino", destino);
        }
        try {
            request.setAttribute("nombreComercial", servicioParametros.obtenerNombreComercial());
        } catch (SQLException error) {
            getServletContext().log("No se pudo leer empresa.nombre_comercial.", error);
            request.setAttribute("nombreComercial", "Capital Crédito");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_ACCESO);
        dispatcher.forward(request, response);
    }
}
