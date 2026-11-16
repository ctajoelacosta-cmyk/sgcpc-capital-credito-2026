package py.com.capitalcredito.sgcpc.filtros;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAuditoria;

/**
 * Bloque de auditoría (hallazgo de Joel, punto 9): audita SESION_EXPIRADA cuando una sesión con
 * un usuario autenticado se destruye SIN pasar por un cierre voluntario (ControladorAutenticacion
 * Servlet.cerrarSesion / ControladorCambioContrasenaServlet.cerrarSesionPorIntentosFallidos, que
 * ya auditan CERRAR_SESION / CERRAR_SESION_POR_INTENTOS_FALLIDOS y marcan
 * FiltroAutenticacion.ATRIBUTO_CIERRE_VOLUNTARIO antes de invalidate() -- sessionDestroyed se
 * dispara igual en los dos casos, voluntario o por vencimiento, así que esa marca es la única
 * forma de distinguirlos). Sin usuario autenticado (ej. la sesión anónima que solo tenía el
 * token CSRF de /acceso, nunca llegó a loguearse) no se audita nada.
 *
 * Nota: un Undeploy/redeploy del servidor (NetBeans, o Tomcat al apagarse) también invalida
 * todas las sesiones activas y dispara esto -- una sesión realmente en uso en ese momento
 * quedaría auditada como "expiró por inactividad" aunque no sea así. Es una limitación conocida
 * del mecanismo (HttpSessionListener no distingue el motivo de la invalidación), no algo que se
 * pueda evitar sin guardar más estado del que vale la pena para este sistema.
 */
public class ListenerSesion implements HttpSessionListener {

    private static final Logger LOGGER = Logger.getLogger(ListenerSesion.class.getName());

    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();

    @Override
    public void sessionDestroyed(HttpSessionEvent evento) {
        HttpSession sesion = evento.getSession();
        if (Boolean.TRUE.equals(sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_CIERRE_VOLUNTARIO))) {
            return;
        }
        Usuario usuario = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        if (usuario == null) {
            return;
        }
        String ip = (String) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_IP_LOGIN);
        String navegador = (String) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_NAVEGADOR_LOGIN);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioAuditoria.registrar(conexion, usuario.getIdUsuario(), "SESION_EXPIRADA", "USUARIO",
                    usuario.getIdUsuario(), ip, navegador, "La sesión venció por inactividad.");
        } catch (SQLException | RuntimeException error) {
            // No hay HttpServletResponse en este punto para mostrar nada -- queda en el log del
            // servidor, nunca se traga en silencio.
            LOGGER.log(Level.WARNING, "No se pudo auditar SESION_EXPIRADA para el usuario id "
                    + usuario.getIdUsuario() + ".", error);
        }
    }
}
