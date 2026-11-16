package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioPanel;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P02 (Doc. 00 §12 / RF-43 / PO-15, Bloque 4 de E8): panel con los indicadores reales por
 * perfil (ServicioPanel). Sin permiso propio en FiltroAutorizacion -- todos los perfiles lo ven,
 * cada uno con su propio contenido (Doc. 00 §3.1: "Ver panel y reportes ... Parcial").
 */
public class ControladorPanelServlet extends HttpServlet {

    private static final String VISTA = "/WEB-INF/vistas/panel.jsp";

    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioPanel servicioPanel = new ServicioPanel();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("nombreComercial", servicioParametros.obtenerNombreComercial());
            request.setAttribute("indicadores",
                    servicioPanel.calcularIndicadores(conexion, usuario.getIdUsuario(), usuario.getPerfilUsuario()));
            // Continuidad del administrador (hallazgo de Joel): solo el administrador ve este
            // aviso -- a los demás perfiles no les sirve de nada (no pueden hacer nada al
            // respecto, ABM Usuarios es exclusivo del administrador desde D-151).
            if (usuario.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR
                    && servicioUsuarios.contarAdministradoresActivos(conexion) < 2) {
                request.setAttribute("avisoContinuidadAdministradores",
                        "Recomendado: tener al menos 2 administradores activos para no quedar sin acceso.");
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al calcular los indicadores del panel.", error);
            request.setAttribute("mensajeError", "No se pudieron calcular los indicadores del panel.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA);
        dispatcher.forward(request, response);
    }
}
