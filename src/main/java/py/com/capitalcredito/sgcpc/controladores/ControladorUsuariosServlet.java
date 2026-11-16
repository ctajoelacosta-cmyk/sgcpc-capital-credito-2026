package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.ExportadorCsv;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioReportes;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P12, parte usuarios (Doc. 10 §10.19: ControladorUsuariosServlet /
 * ServicioUsuarios). Protegido por FiltroAutorizacion (solo
 * GERENTE_ADMINISTRADOR, RNF11). C-4.2 (pedido de Joel): /usuarios/informe y
 * /usuarios/exportar -- mismo molde que /reportes/*, reusando el web.xml que
 * ya tenía /usuarios/* mapeado (no hace falta tocarlo, a diferencia de E-16).
 */
public class ControladorUsuariosServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/usuarios/lista.jsp";
    private static final String VISTA_FORMULARIO = "/WEB-INF/vistas/usuarios/formulario.jsp";

    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final ServicioReportes servicioReportes = new ServicioReportes();
    private final ServicioInformes servicioInformes = new ServicioInformes();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response, null, null);
        } else if ("/nuevo".equals(pathInfo)) {
            mostrarFormulario(request, response);
        } else if ("/editar".equals(pathInfo)) {
            mostrarFormularioEdicion(request, response);
        } else if ("/informe".equals(pathInfo)) {
            generarInformePdf(request, response);
        } else if ("/exportar".equals(pathInfo)) {
            exportarCsv(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /** C-4.2 (pedido de Joel): "Informe PDF" de Usuarios y accesos -- mismo mecanismo que los
     * informes de /reportes. Nunca pasa el hash de la contraseña ni ningún otro dato de
     * seguridad: ServicioReportes.construirFilasInformeUsuarios ya arma la fila sin ese campo. */
    private void generarInformePdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas =
                    servicioReportes.construirFilasInformeUsuarios(servicioUsuarios.listar(conexion));
            py.com.capitalcredito.sgcpc.dto.TotalesUsuarios totales = servicioReportes.calcularTotalesUsuarios(filas);
            byte[] pdf = servicioInformes.generarInformeUsuariosPdf(conexion, filas, totales,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_usuarios.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de usuarios.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas =
                    servicioReportes.construirFilasInformeUsuarios(servicioUsuarios.listar(conexion));
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "usuarios.csv")) {
                escritor.println("Usuario;Nombre;C.I.;Perfil;Estado;Debe cambiar contraseña;Último acceso");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario fila : filas) {
                    // E-7 (pedido de Joel): C.I. marcada como texto -- mismo riesgo de cero
                    // inicial que el resto de los CSV de este sistema.
                    escritor.println(String.join(";", ExportadorCsv.comilla(fila.getNombreUsuario()),
                            ExportadorCsv.comilla(fila.getNombreCompleto()), ExportadorCsv.comoTexto(fila.getCedula()),
                            ExportadorCsv.comilla(fila.getPerfil()), ExportadorCsv.comilla(fila.getEstado()),
                            fila.getDebeCambiarContrasena(), ExportadorCsv.comilla(fila.getUltimoAcceso())));
                }
            }
            servicioInformes.auditarExportacionCsvUsuarios(conexion, solicitante.getIdUsuario(), filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar usuarios a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);

        if ("/nuevo".equals(pathInfo)) {
            procesarAltaConFormulario(request, response, solicitante);
            return;
        }
        if ("/editar".equals(pathInfo)) {
            procesarEdicionConFormulario(request, response, solicitante);
            return;
        }

        // Post/Redirect/Get (D-57, AVANCE.md): antes estas acciones terminaban con un forward a
        // la lista, dejando la URL del POST (/usuarios/bloquear, etc.) en la barra -- un F5
        // reenviaba el mismo POST. Ahora siempre redirige a /usuarios (exito o error), con el
        // mensaje como flash (MensajesFlash), igual que ya hacia ControladorDocumentosServlet.
        try {
            String mensajeExito;
            String contrasenaTemporalParaCopiar = null;
            if ("/bloquear".equals(pathInfo)) {
                servicioUsuarios.bloquear(idParametro(request), solicitante.getIdUsuario());
                mensajeExito = "Usuario bloqueado.";
            } else if ("/reactivar".equals(pathInfo)) {
                servicioUsuarios.reactivar(idParametro(request), solicitante.getIdUsuario());
                mensajeExito = "Usuario reactivado.";
            } else if ("/inactivar".equals(pathInfo)) {
                servicioUsuarios.inactivar(idParametro(request), solicitante.getIdUsuario());
                mensajeExito = "Usuario inactivado.";
            } else if ("/restablecer".equals(pathInfo)) {
                py.com.capitalcredito.sgcpc.dto.ResultadoRestablecerContrasena resultado =
                        servicioUsuarios.restablecerContrasena(idParametro(request), solicitante.getIdUsuario());
                contrasenaTemporalParaCopiar = resultado.getContrasenaTemporal();
                mensajeExito = "Contraseña restablecida. Nueva contraseña temporal: \"" + resultado.getContrasenaTemporal()
                        + "\" (comuníquesela al usuario; deberá cambiarla al entrar)."
                        + (resultado.isSeDesbloqueoLaCuenta() ? " La cuenta estaba bloqueada: también se desbloqueó." : "");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            // B213 (pedido de Joel, 15/11/2026): si el mensaje trae la contrasena temporal, se
            // guarda como sensible -- el modal no se cierra con un clic afuera ni con Esc y
            // ofrece "Copiar" (ver MensajesFlash.guardarExitoSensible). Es el unico momento en
            // que esa contrasena se puede leer: no se persiste en ninguna parte (RF37 Sec. 3).
            if (contrasenaTemporalParaCopiar == null) {
                MensajesFlash.guardarExito(request.getSession(), mensajeExito);
            } else {
                MensajesFlash.guardarExitoSensible(request.getSession(), mensajeExito,
                        contrasenaTemporalParaCopiar);
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(request.getSession(), error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/usuarios");
    }

    /**
     * Encontrado en la prueba de E3 (mismo problema que la ficha del cliente): ante un rechazo
     * (ej. cédula duplicada), el alta de usuario mostraba la LISTA con el error, perdiendo todo
     * lo que el gerente ya había escrito. Ahora arma el Usuario con lo tipeado antes de llamar
     * al servicio, y si falla, vuelve al formulario (no a la lista) con esos mismos valores.
     */
    private void procesarAltaConFormulario(HttpServletRequest request, HttpServletResponse response,
            Usuario solicitante) throws ServletException, IOException {
        Usuario intentado = armarUsuarioDesdeRequest(request);
        try {
            py.com.capitalcredito.sgcpc.dto.ResultadoAltaUsuario resultado =
                    servicioUsuarios.alta(intentado, solicitante.getIdUsuario());
            // PRG (D-57): redirige a /usuarios en vez de forward -- un F5 tras un alta exitosa ya
            // no reenvía el POST. El error sigue con forward al formulario (preserva lo tipeado,
            // D-53): un alta rechazada no escribió nada, no hay riesgo de duplicar con un F5 ahí.
            // Hallazgo de Joel probando RF37 en pantalla: ya no hay una contraseña inicial fija
            // -- se muestra la temporal generada, una sola vez, mismo criterio que /restablecer.
            // B213 (pedido de Joel, 15/11/2026): mismo criterio que /restablecer -- la temporal se
            // muestra una sola vez, asi que el modal no se cierra solo y trae "Copiar".
            MensajesFlash.guardarExitoSensible(request.getSession(), "Usuario creado. Contraseña temporal: \""
                    + resultado.getContrasenaTemporal() + "\" (comuníquesela al usuario; deberá cambiarla al entrar).",
                    resultado.getContrasenaTemporal());
            response.sendRedirect(request.getContextPath() + "/usuarios");
        } catch (NegocioException error) {
            // Tanda 2 (14/10/2026, pedido de Joel): dato mal cargado (cédula, nombre, perfil) es
            // una advertencia ("!"), no un error del sistema (X roja) -- mismo mecanismo que
            // RF39 2a. El mensaje es una sola oración, así que se le agrega un título corto para
            // que dividirEnTituloYDetalle (pie.jsp) separe título/detalle.
            request.setAttribute("mensajeAdvertencia", "Dato incorrecto. " + error.getMessage());
            mostrarFormulario(request, response, intentado, null);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en /usuarios/nuevo.", error);
            mostrarFormulario(request, response, intentado, "No se pudo completar la operación. Intente nuevamente.");
        }
    }

    /**
     * Edición (plan "Editar usuario", aprobado por Joel): mismo patrón que el alta -- si el
     * servicio rechaza el cambio, se vuelve al formulario (no a la lista) con lo que el gerente
     * ya había escrito, no a una lista en blanco.
     */
    private void procesarEdicionConFormulario(HttpServletRequest request, HttpServletResponse response,
            Usuario solicitante) throws ServletException, IOException {
        Usuario intentado = armarUsuarioDesdeRequest(request);
        try {
            servicioUsuarios.actualizar(intentado, solicitante.getIdUsuario());
            MensajesFlash.guardarExito(request.getSession(), "Usuario actualizado.");
            response.sendRedirect(request.getContextPath() + "/usuarios");
        } catch (NegocioException error) {
            // Tanda 2 (14/10/2026, pedido de Joel): mismo criterio que el alta, arriba.
            Usuario guardado = completarDatosNoEditablesParaRedisplay(intentado);
            request.setAttribute("mensajeAdvertencia", "Dato incorrecto. " + error.getMessage());
            mostrarFormularioEdicion(request, response, intentado, solicitante, null, guardado);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en /usuarios/editar.", error);
            Usuario guardado = completarDatosNoEditablesParaRedisplay(intentado);
            mostrarFormularioEdicion(request, response, intentado, solicitante,
                    "No se pudo completar la operación. Intente nuevamente.", guardado);
        }
    }

    /**
     * Solo para volver a mostrar el formulario tras un error -- nunca cambia lo que
     * ServicioUsuarios.actualizar() ya validó/rechazó. E1/V4 (encontrado probando D-151):
     * "nombreUsuario" nunca llega en el POST (el campo está disabled en la edición), así que acá
     * SIEMPRE se repone con el valor real de la base -- antes quedaba null y se veía "null" en
     * pantalla. Si el perfil también quedó null (edición de uno mismo, con el <select>
     * deshabilitado, o un valor inválido) se completa con el perfil ACTUAL. Devuelve el usuario
     * tal como está guardado en la base (o null si no se pudo leer), para que el título del
     * formulario use el nombre GUARDADO y no lo tipeado (que puede venir vacío si el error fue
     * justamente por eso).
     */
    private Usuario completarDatosNoEditablesParaRedisplay(Usuario intentado) {
        if (intentado.getIdUsuario() == null) {
            return null;
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            Usuario actual = servicioUsuarios.buscarPorId(conexion, intentado.getIdUsuario());
            if (actual != null) {
                intentado.setNombreUsuario(actual.getNombreUsuario());
                if (intentado.getPerfilUsuario() == null) {
                    intentado.setPerfilUsuario(actual.getPerfilUsuario());
                }
            }
            return actual;
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en /usuarios/editar.", error);
            return null;
        }
    }

    /**
     * Sirve para alta (sin idUsuario en el request) y edición (con idUsuario). Plan "Editar
     * usuario" (pedido de Joel), punto 6: un perfil que no existe (o que no llegó porque el
     * <select> estaba deshabilitado por ser la edición de uno mismo) queda null acá, nunca
     * revienta con IllegalArgumentException -- ServicioUsuarios.validarDatosBasicos lo rechaza
     * con un mensaje claro, y ServicioUsuarios.actualizar ya ignora este campo por completo
     * cuando quien edita es el mismo usuario.
     */
    private Usuario armarUsuarioDesdeRequest(HttpServletRequest request) {
        Usuario usuario = new Usuario();
        String idUsuarioParametro = request.getParameter("idUsuario");
        if (idUsuarioParametro != null && !idUsuarioParametro.isEmpty()) {
            usuario.setIdUsuario(Long.parseLong(idUsuarioParametro));
        }
        usuario.setNombreUsuario(request.getParameter("nombreUsuario"));
        usuario.setNombres(request.getParameter("nombres"));
        usuario.setApellidos(request.getParameter("apellidos"));
        usuario.setCedulaIdentidad(request.getParameter("cedulaIdentidad"));
        String perfil = request.getParameter("perfilUsuario");
        if (perfil != null && !perfil.isEmpty()) {
            try {
                usuario.setPerfilUsuario(PerfilUsuario.valueOf(perfil));
            } catch (IllegalArgumentException perfilInvalido) {
                // Queda null a propósito -- ver el comentario del método.
            }
        }
        return usuario;
    }

    private long idParametro(HttpServletRequest request) {
        return Long.parseLong(request.getParameter("idUsuario"));
    }

    private void mostrarFormulario(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        mostrarFormulario(request, response, null, null);
    }

    private void mostrarFormulario(HttpServletRequest request, HttpServletResponse response, Usuario usuarioIntentado,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        if (usuarioIntentado != null) {
            request.setAttribute("usuario", usuarioIntentado);
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FORMULARIO);
        dispatcher.forward(request, response);
    }

    /** GET /usuarios/editar?idUsuario=X -- carga el usuario real de la base para precargar el
     * formulario (a diferencia del alta, que siempre arranca en blanco). */
    private void mostrarFormularioEdicion(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        long idUsuario;
        try {
            idUsuario = idParametro(request);
        } catch (NumberFormatException | NullPointerException error) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            Usuario usuarioAEditar = servicioUsuarios.buscarPorId(conexion, idUsuario);
            if (usuarioAEditar == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarFormularioEdicion(request, response, usuarioAEditar, solicitante, null);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en /usuarios/editar.", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo leer el usuario.");
            response.sendRedirect(request.getContextPath() + "/usuarios");
        }
    }

    /** Carga inicial (GET /usuarios/editar, sin error): "usuario" YA es el registro real de la
     * base, así que también sirve como "usuarioGuardado" para el título (V4). */
    private void mostrarFormularioEdicion(HttpServletRequest request, HttpServletResponse response, Usuario usuario,
            Usuario solicitante, String mensajeError) throws ServletException, IOException {
        mostrarFormularioEdicion(request, response, usuario, solicitante, mensajeError, usuario);
    }

    /** Punto 2 (pedido de Joel): "editaAsiMismo" le dice al formulario.jsp si debe deshabilitar
     * el <select> de perfil -- el servidor no confía en eso (ver ServicioUsuarios.actualizar),
     * es solo para que el gerente entienda por qué no puede tocarlo ahí mismo. V4 (encontrado
     * probando D-151): "usuarioGuardado" es el registro real de la base (puede diferir de
     * "usuario" tras un error, que conserva lo tipeado) -- formulario.jsp lo usa para el título
     * "Editar usuario: Nombre Apellido", nunca el valor tipeado que pudo rechazarse. */
    private void mostrarFormularioEdicion(HttpServletRequest request, HttpServletResponse response, Usuario usuario,
            Usuario solicitante, String mensajeError, Usuario usuarioGuardado) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        request.setAttribute("usuario", usuario);
        request.setAttribute("usuarioGuardado", usuarioGuardado);
        request.setAttribute("modoEdicion", Boolean.TRUE);
        request.setAttribute("editaAsiMismo", solicitante.getIdUsuario().equals(usuario.getIdUsuario()));
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FORMULARIO);
        dispatcher.forward(request, response);
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response, String mensajeExito,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<Usuario> usuarios = servicioUsuarios.listar(conexion);
            request.setAttribute("usuarios", usuarios);
            // C-4.2 (pedido de Joel): totales por perfil y por estado, igual en pantalla, PDF y CSV.
            request.setAttribute("totalesUsuarios",
                    servicioReportes.calcularTotalesUsuarios(servicioReportes.construirFilasInformeUsuarios(usuarios)));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la lista de usuarios.", error);
            request.setAttribute("mensajeError", "No se pudo leer la lista de usuarios.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_LISTA);
        dispatcher.forward(request, response);
    }
}
