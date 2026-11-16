package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.excepciones.ContrasenaActualIncorrectaException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutenticacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;

/**
 * Cambio de contraseña propia (P01): tanto el voluntario como el
 * obligatorio del primer ingreso / tras un restablecimiento del gerente
 * (Doc. 10 §10.5).
 */
public class ControladorCambioContrasenaServlet extends HttpServlet {

    private static final String VISTA = "/WEB-INF/vistas/cambiar-contrasena.jsp";

    /** Bloque de seguridad (hallazgo de Joel): cuenta intentos seguidos de la contraseña ACTUAL
     * incorrecta, solo en esta sesión -- reusa seguridad.intentos_login (mismo criterio que el
     * login) en vez de un parámetro nuevo solo para este caso puntual. */
    private static final String ATRIBUTO_INTENTOS_FALLIDOS = "intentosFallidosCambioContrasena";

    private final ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        mostrarFormulario(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String contrasenaActual = request.getParameter("contrasenaActual");
        String contrasenaNueva = request.getParameter("contrasenaNueva");
        String contrasenaConfirmacion = request.getParameter("contrasenaConfirmacion");
        HttpSession sesion = request.getSession();
        Usuario usuario = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);

        try {
            // La contraseña actual/nueva/confirmación se validan dentro del servicio
            // (validarContrasenaActual/validarConfirmacionCoincide/validarContrasenaNueva): un
            // rechazo ahí nunca toca intentos_fallidos ni bloquea la cuenta (eso es solo del login).
            servicioAutenticacion.cambiarContrasenaPropia(usuario.getIdUsuario(), contrasenaActual, contrasenaNueva,
                    contrasenaConfirmacion);
            usuario.setDebeCambiarContrasena(false);
            sesion.removeAttribute(ATRIBUTO_INTENTOS_FALLIDOS);
            MensajesFlash.guardarExito(sesion, "Contraseña actualizada correctamente.");
            response.sendRedirect(request.getContextPath() + "/panel");
        } catch (ContrasenaActualIncorrectaException error) {
            // Bloque de seguridad (hallazgo de Joel): a los N fallos seguidos (N =
            // seguridad.intentos_login) de la contraseña actual, se cierra la sesión -- puede ser
            // alguien distinto del dueño probando contraseñas desde una sesión ya abierta.
            int intentos = incrementarIntentosFallidos(sesion);
            try {
                if (intentos >= obtenerLimiteIntentos()) {
                    cerrarSesionPorIntentosFallidos(request, usuario);
                    response.sendRedirect(request.getContextPath() + "/acceso?motivo=intentosFallidosCambioContrasena");
                    return;
                }
            } catch (SQLException errorLimite) {
                getServletContext().log("Error de base de datos al verificar el límite de intentos.", errorLimite);
                // No bloquea el aviso del error de contraseña de este intento puntual.
            }
            mostrarFormulario(request, response, error.getMessage());
        } catch (NegocioException error) {
            mostrarFormulario(request, response, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cambiar la contraseña de \"" + usuario.getNombreUsuario() + "\".", error);
            mostrarFormulario(request, response, "No se pudo cambiar la contraseña. Intente nuevamente.");
        }
    }

    private int incrementarIntentosFallidos(HttpSession sesion) {
        Integer previos = (Integer) sesion.getAttribute(ATRIBUTO_INTENTOS_FALLIDOS);
        int nuevos = (previos == null ? 0 : previos) + 1;
        sesion.setAttribute(ATRIBUTO_INTENTOS_FALLIDOS, nuevos);
        return nuevos;
    }

    private int obtenerLimiteIntentos() throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return servicioParametros.obtenerValorEntero(conexion, "seguridad.intentos_login");
        }
    }

    private void cerrarSesionPorIntentosFallidos(HttpServletRequest request, Usuario usuario) throws IOException {
        // Bloque de seguridad (hallazgo de Joel, punto 9): marca el cierre como "ya auditado" --
        // ListenerSesion no debe auditar SESION_EXPIRADA encima de este CERRAR_SESION_POR_
        // INTENTOS_FALLIDOS (invalidate() dispara sessionDestroyed igual que un vencimiento).
        request.getSession().setAttribute(FiltroAutenticacion.ATRIBUTO_CIERRE_VOLUNTARIO, Boolean.TRUE);
        try {
            servicioAutenticacion.registrarCierreSesionPorIntentosFallidos(usuario.getIdUsuario(),
                    request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (SQLException error) {
            getServletContext().log("No se pudo auditar el cierre de sesión por intentos fallidos de \""
                    + usuario.getNombreUsuario() + "\".", error);
        }
        request.getSession().invalidate();
    }

    private void mostrarFormulario(HttpServletRequest request, HttpServletResponse response, String mensajeError)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA);
        dispatcher.forward(request, response);
    }
}
