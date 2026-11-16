package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET /sesion/renovar: endpoint liviano para el aviso de sesión por vencer
 * (recursos/js/aviso-sesion.js, "Seguir conectado"). No hace nada por sí
 * mismo — FiltroAutenticacion ya toca la sesión (request.getSession()) en
 * cualquier petición autenticada, lo que ya reinicia el conteo de
 * inactividad del contenedor; este servlet solo le da al JS algo liviano a
 * lo que llamar, sin bajar ningún dato. No audita: renovar la sesión no es
 * una acción de negocio.
 */
public class ControladorSesionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
