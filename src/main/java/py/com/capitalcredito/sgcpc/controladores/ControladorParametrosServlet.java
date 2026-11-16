package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.ExportadorCsv;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PaginacionPantalla;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Feriado;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;
import py.com.capitalcredito.sgcpc.servicios.ServicioReportes;

/**
 * P12, parte parametros/feriados/limites de tasa (Doc. 10 §10.19:
 * ControladorParametrosServlet / ServicioParametros). Un solo codigo de
 * pantalla (P12) con pestañas; protegido por FiltroAutorizacion (solo
 * GERENTE_ADMINISTRADOR, RNF11). C-4.3 (pedido de Joel): /parametros/historial
 * -- pantalla de solo lectura, nunca cambia ningún parámetro; reusa el
 * web.xml que ya tenía /parametros/* mapeado (no hace falta tocarlo, a
 * diferencia de E-16).
 */
public class ControladorParametrosServlet extends HttpServlet {

    private static final String VISTA = "/WEB-INF/vistas/parametros/administracion.jsp";
    private static final String VISTA_HISTORIAL = "/WEB-INF/vistas/parametros/historial.jsp";

    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioReportes servicioReportes = new ServicioReportes();
    private final ServicioInformes servicioInformes = new ServicioInformes();
    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if ("/historial".equals(pathInfo)) {
            mostrarHistorial(request, response);
        } else if ("/historial/informe".equals(pathInfo)) {
            generarInformeHistorialPdf(request, response);
        } else if ("/historial/exportar".equals(pathInfo)) {
            exportarHistorialCsv(request, response);
        } else {
            mostrarAdministracion(request, response, null, null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);

        // D-151 (pedido de Joel): el gerente ve Parámetros en CONSULTA -- FiltroAutorizacion ya
        // deja pasar su GET, pero un POST armado a mano (sin usar el formulario, que ya está
        // oculto para el gerente en la JSP) no debe ejecutar ningún cambio.
        if (!puedeModificar(request)) {
            MensajesFlash.guardarError(request.getSession(), "Solo el administrador puede modificar los parámetros.");
            response.sendRedirect(request.getContextPath() + "/parametros");
            return;
        }

        // PRG (D-57): antes cada accion terminaba con un forward a la propia pantalla, dejando la
        // URL del POST (ej. /parametros/feriados/nuevo) en la barra -- un F5 reenviaba el mismo
        // POST y duplicaba el feriado o el limite de tasa. Ahora siempre redirige a /parametros,
        // con el mensaje como flash.
        try {
            String mensajeExito;
            if ("/actualizar-valor".equals(pathInfo)) {
                servicioParametros.actualizarValor(request.getParameter("clave"), request.getParameter("valorNuevo"),
                        request.getParameter("motivo"), solicitante.getIdUsuario());
                mensajeExito = "Parámetro actualizado.";
            } else if ("/feriados/nuevo".equals(pathInfo)) {
                Feriado feriado = new Feriado();
                feriado.setFecha(java.time.LocalDate.parse(request.getParameter("fecha")));
                feriado.setDescripcion(request.getParameter("descripcion"));
                feriado.setActivo(true);
                servicioParametros.altaFeriado(feriado, solicitante.getIdUsuario());
                mensajeExito = "Feriado agregado.";
            } else if ("/feriados/activar".equals(pathInfo) || "/feriados/desactivar".equals(pathInfo)) {
                boolean activo = "/feriados/activar".equals(pathInfo);
                long idFeriado = Long.parseLong(request.getParameter("idFeriado"));
                servicioParametros.cambiarActivoFeriado(idFeriado, activo, solicitante.getIdUsuario());
                mensajeExito = activo ? "Feriado reactivado." : "Feriado desactivado.";
            } else if ("/limites-tasa/nuevo".equals(pathInfo)) {
                LimiteTasaUsura limite = new LimiteTasaUsura();
                limite.setVigenteDesde(java.time.LocalDate.parse(request.getParameter("vigenteDesde")));
                // Punto 2: se escribe como porcentaje (27,39) y se guarda como fraccion (0.273900).
                BigDecimal porcentaje = ConversorNumeros.aBigDecimal(request.getParameter("tasaMaximaTea"));
                limite.setTasaMaximaTea(FormateadorNumeros.fraccionDesdePorcentaje(porcentaje, 6));
                limite.setFuenteReferencia(request.getParameter("fuenteReferencia"));
                servicioParametros.altaLimiteTasa(limite, solicitante.getIdUsuario());
                mensajeExito = "Límite de tasa agregado.";
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            MensajesFlash.guardarExito(request.getSession(), mensajeExito);
        } catch (NegocioException error) {
            // RF39 (corrección 2a, pedido de Joel, 13/10/2026): un bloqueo de negocio (TEA 0, monto
            // negativo, etc. -- D2) no es un error del servidor. Mismo mecanismo que
            // ControladorClientesServlet ya usa para "No se pudo guardar." (prefijo + advertencia:
            // icono "!", título corto, el motivo como detalle) -- solo para /actualizar-valor, que
            // es lo que Joel probó; las otras 4 acciones de esta pantalla (feriados, límites de
            // tasa) siguen igual.
            if ("/actualizar-valor".equals(pathInfo)) {
                MensajesFlash.guardarAdvertencia(request.getSession(), "No se puede guardar. " + error.getMessage());
            } else {
                MensajesFlash.guardarError(request.getSession(), error.getMessage());
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo completar la operación. Intente nuevamente.");
        }
        // B.13 (consolidación, pedido de Joel): las 3 acciones de feriados vuelven a la pestaña
        // "Feriados" (antes volvían siempre a "Parámetros" general, perdiendo el lugar donde
        // estaba el usuario) -- administracion.jsp activa la pestaña según el hash de la URL.
        if (pathInfo.startsWith("/feriados/")) {
            response.sendRedirect(request.getContextPath() + "/parametros#tabFeriados");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/parametros");
    }

    private void mostrarAdministracion(HttpServletRequest request, HttpServletResponse response, String mensajeExito,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        request.setAttribute("puedeModificar", puedeModificar(request));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("parametros", servicioParametros.listarVigentes(conexion));
            request.setAttribute("feriados", servicioParametros.listarFeriados(conexion));
            request.setAttribute("limitesTasa", servicioParametros.listarLimitesTasa(conexion));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer parámetros/feriados/límites de tasa.", error);
            request.setAttribute("mensajeError", "No se pudieron leer los datos.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA);
        dispatcher.forward(request, response);
    }

    /** C-4.3 (pedido de Joel): pantalla de solo lectura -- nunca cambia ningún parámetro.
     * "incluirIniciales" (RF39, pantalla rediseñada): oculta por defecto las cargas iniciales del
     * script de siembra -- mismo patrón que "soloIncumplidas" en ControladorReportesServlet. */
    private void mostrarHistorial(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        String claveParametro = vacioComoNulo(request.getParameter("parametro"));
        boolean incluirIniciales = "true".equals(request.getParameter("incluirIniciales"));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        request.setAttribute("parametro", claveParametro);
        request.setAttribute("incluirIniciales", incluirIniciales);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("parametrosDisponibles", servicioParametros.listarVigentes(conexion));
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas =
                    servicioReportes.listarHistorialParametros(conexion, desde, hasta, claveParametro, incluirIniciales);
            // B219 (15/11/2026, item 1 de la tanda 2 de Seguridad): los totales salen del listado
            // COMPLETO y se calculan ANTES de recortar -- contar lo dibujado diria "20 cambios" en
            // cada pagina, la trampa que B191 encontro en Caja. El PDF y el CSV no pasan por aca:
            // vuelven a leer con los mismos filtros, asi que siguen exportando todo.
            request.setAttribute("totales", servicioReportes.calcularTotalesHistorialParametros(filas));
            request.setAttribute("filas", PaginacionPantalla.paginar(request, filas));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el historial de parámetros.", error);
            request.setAttribute("mensajeError", "No se pudo leer el historial.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_HISTORIAL);
        dispatcher.forward(request, response);
    }

    /** C-4.3 (pedido de Joel): "Informe PDF" del historial de parámetros -- mismo mecanismo que
     * los informes de /reportes y /usuarios. Solo lectura. */
    private void generarInformeHistorialPdf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        String claveParametro = vacioComoNulo(request.getParameter("parametro"));
        boolean incluirIniciales = "true".equals(request.getParameter("incluirIniciales"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas =
                    servicioReportes.listarHistorialParametros(conexion, desde, hasta, claveParametro, incluirIniciales);
            py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros totales =
                    servicioReportes.calcularTotalesHistorialParametros(filas);
            String etiquetaParametro = claveParametro == null ? null
                    : resolverNombreParametro(servicioParametros.listarVigentes(conexion), claveParametro);
            String textoFiltros = servicioInformes.construirTextoFiltrosHistorialParametros(desde, hasta,
                    etiquetaParametro, incluirIniciales);
            byte[] pdf = servicioInformes.generarInformeHistorialParametrosPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_historial_parametros.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de historial de parámetros.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** CSV (punto 5, pedido de Joel): mismo orden/filtros que la pantalla, pero "Valor anterior"/
     * "Valor nuevo" quedan en columnas separadas (más útil para filtrar/ordenar en Excel que la
     * celda fusionada de la pantalla), y se agregan "Fecha y hora", "Clave" y "Usuario". */
    private void exportarHistorialCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        String claveParametro = vacioComoNulo(request.getParameter("parametro"));
        boolean incluirIniciales = "true".equals(request.getParameter("incluirIniciales"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas =
                    servicioReportes.listarHistorialParametros(conexion, desde, hasta, claveParametro, incluirIniciales);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "historial_parametros_" + desde + "_" + hasta + ".csv")) {
                escritor.println("Fecha y hora;Clave;Parámetro;Valor anterior;Valor nuevo;Usuario;Observación");
                for (py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila : filas) {
                    escritor.println(String.join(";", fila.getFechaModificacion(),
                            ExportadorCsv.comilla(fila.getClaveParametro()), ExportadorCsv.comilla(fila.getParametro()),
                            ExportadorCsv.comilla(fila.getValorAnterior()), ExportadorCsv.comilla(fila.getValorNuevo()),
                            ExportadorCsv.comilla(fila.getModificadoPor()), ExportadorCsv.comilla(fila.getObservacion())));
                }
            }
            String etiquetaParametro = claveParametro == null ? null
                    : resolverNombreParametro(servicioParametros.listarVigentes(conexion), claveParametro);
            String textoFiltros = servicioInformes.construirTextoFiltrosHistorialParametros(desde, hasta,
                    etiquetaParametro, incluirIniciales);
            servicioInformes.auditarExportacionCsvHistorialParametros(conexion, solicitante.getIdUsuario(), textoFiltros,
                    filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar el historial de parámetros a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private String resolverNombreParametro(List<py.com.capitalcredito.sgcpc.modelos.ParametroSistema> vigentes,
            String claveParametro) {
        for (py.com.capitalcredito.sgcpc.modelos.ParametroSistema parametro : vigentes) {
            if (parametro.getClaveParametro().equals(claveParametro)) {
                return parametro.getNombreParametro();
            }
        }
        return claveParametro;
    }

    private LocalDate parsearFecha(String texto, LocalDate porDefecto) {
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return LocalDate.parse(texto);
        } catch (java.time.format.DateTimeParseException error) {
            return porDefecto;
        }
    }

    private String vacioComoNulo(String texto) {
        return texto == null || texto.trim().isEmpty() ? null : texto;
    }

    /** D-151 (pedido de Joel): el gerente consulta Parámetros (CONSULTA), solo el administrador
     * modifica (TOTAL) -- mismo patrón que ControladorClientesServlet.puedeEditar. */
    private boolean puedeModificar(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        return usuario != null
                && servicioAutorizacion.nivelAcceso(usuario.getPerfilUsuario(), Permiso.ADMINISTRAR_PARAMETROS) == NivelAcceso.TOTAL;
    }
}
