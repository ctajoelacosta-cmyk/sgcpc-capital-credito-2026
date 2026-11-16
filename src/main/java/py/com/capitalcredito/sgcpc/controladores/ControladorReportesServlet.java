package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.ExportadorCsv;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioMora;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;
import py.com.capitalcredito.sgcpc.servicios.ServicioReportes;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * P11, reportes (Doc. 00 §13, Bloque 4 de E8). Protegido en FiltroAutorizacion por
 * Permiso.VER_REPORTES (gerente TOTAL, oficial CONSULTA, cajera NINGUNO -- decisión 2). Ese
 * chequeo grueso solo distingue "algo vs. nada"; la restricción fina de que el oficial solo ve
 * 4 de los 10 sub-reportes (Vencimientos, Morosidad, Gestiones de cobranza, Estado de cuenta) se
 * hace acá, con 403 para el resto -- mismo patrón que ServicioCaja.registrarEntregaGerente.
 */
public class ControladorReportesServlet extends HttpServlet {

    private static final String VISTA_INDICE = "/WEB-INF/vistas/reportes/indice.jsp";
    private static final Set<String> SUBREPORTES_OFICIAL = new HashSet<>(
            // C-4.4 (pedido de Joel): "consultas-buro" entra acá -- mismos permisos que la
            // pestaña de buró en la ficha del cliente (Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO:
            // gerente y oficial TOTAL, cajera NINGUNO), no solo gerente.
            Arrays.asList("vencimientos", "morosidad", "gestiones", "estado-cuenta", "consultas-buro"));
    /** C-4.4: lo que manda el <select> de "resultado" cuando el usuario elige "(todos)" -- para no
     * confundirlo con "" (una clasificación real y filtrable: hay consultas sin clasificación
     * cargada, que "" sí representa). */
    private static final String SENTINEL_TODOS_LOS_RESULTADOS = "(todos)";

    private final ServicioReportes servicioReportes = new ServicioReportes();
    private final ServicioMora servicioMora = new ServicioMora();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioInformes servicioInformes = new ServicioInformes();
    // RF40/F1 (pedido de Joel, 13/10/2026): lista de usuarios para el <select> del filtro de
    // Auditoría.
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_INDICE);
            dispatcher.forward(request, response);
            return;
        }
        String[] segmentos = pathInfo.substring(1).split("/");
        String subreporte = segmentos[0];

        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        if (usuario.getPerfilUsuario() == PerfilUsuario.OFICIAL_CREDITO && !SUBREPORTES_OFICIAL.contains(subreporte)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para este reporte.");
            return;
        }

        try {
            switch (subreporte) {
                case "vencimientos":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeVencimientosPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarVencimientosCsv(request, response);
                    } else {
                        mostrarVencimientos(request, response);
                    }
                    return;
                case "morosidad":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeMorosidadPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarMorosidadCsv(request, response);
                    } else {
                        mostrarMorosidad(request, response);
                    }
                    return;
                case "gestiones":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeGestionesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarGestionesCsv(request, response);
                    } else {
                        mostrarGestiones(request, response);
                    }
                    return;
                case "cartera":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeCarteraPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarCarteraCsv(request, response);
                    } else {
                        mostrarCartera(request, response);
                    }
                    return;
                case "resumen-mensual":
                    if (segmentos.length == 2 && "csv".equals(segmentos[1])) {
                        descargarResumenMensualCsv(request, response);
                    } else if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeResumenMensualPdf(request, response);
                    } else {
                        mostrarResumenMensual(request, response);
                    }
                    return;
                case "caja":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeCajaPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarCajaCsv(request, response);
                    } else {
                        mostrarCaja(request, response);
                    }
                    return;
                case "iva":
                    if (segmentos.length == 2 && "csv".equals(segmentos[1])) {
                        descargarIvaCsv(request, response);
                    } else if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeIvaPdf(request, response);
                    } else {
                        mostrarIva(request, response);
                    }
                    return;
                case "incobrables":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeIncobrablesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarIncobrablesCsv(request, response);
                    } else {
                        mostrarIncobrables(request, response);
                    }
                    return;
                case "facturas-anuladas":
                    if (segmentos.length == 2 && "csv".equals(segmentos[1])) {
                        descargarFacturasAnuladasCsv(request, response);
                    } else if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeFacturasAnuladasPdf(request, response);
                    } else {
                        mostrarFacturasAnuladas(request, response);
                    }
                    return;
                case "auditoria":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeAuditoriaPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarAuditoriaCsv(request, response);
                    } else {
                        mostrarAuditoria(request, response);
                    }
                    return;
                case "resumen-financiero":
                    if (segmentos.length == 2 && "csv".equals(segmentos[1])) {
                        descargarResumenFinancieroCsv(request, response);
                    } else if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeResumenFinancieroPdf(request, response);
                    } else {
                        mostrarResumenFinanciero(request, response);
                    }
                    return;
                case "solicitudes":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeSolicitudesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarSolicitudesCsv(request, response);
                    } else {
                        mostrarInformeSolicitudes(request, response);
                    }
                    return;
                case "aprobaciones":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeAprobacionesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarAprobacionesCsv(request, response);
                    } else {
                        mostrarInformeAprobaciones(request, response);
                    }
                    return;
                case "desembolsos":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeDesembolsosPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarDesembolsosCsv(request, response);
                    } else {
                        mostrarInformeDesembolsos(request, response);
                    }
                    return;
                case "cobros":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeCobrosPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarCobrosCsv(request, response);
                    } else {
                        mostrarInformeCobros(request, response);
                    }
                    return;
                case "reversiones":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeReversionesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarReversionesCsv(request, response);
                    } else {
                        mostrarInformeReversiones(request, response);
                    }
                    return;
                case "clientes-nuevos":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeClientesNuevosPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarClientesNuevosCsv(request, response);
                    } else {
                        mostrarInformeClientesNuevos(request, response);
                    }
                    return;
                case "cierres-documentales":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeCierresDocumentalesPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarCierresDocumentalesCsv(request, response);
                    } else {
                        mostrarInformeCierresDocumentales(request, response);
                    }
                    return;
                case "proyeccion-fondos":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeProyeccionFondosPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarProyeccionFondosCsv(request, response);
                    } else {
                        mostrarProyeccionFondos(request, response);
                    }
                    return;
                case "consultas-buro":
                    if (segmentos.length == 2 && "informe".equals(segmentos[1])) {
                        generarInformeConsultasBuroPdf(request, response);
                    } else if (segmentos.length == 2 && "exportar".equals(segmentos[1])) {
                        exportarConsultasBuroCsv(request, response);
                    } else {
                        mostrarConsultasBuro(request, response);
                    }
                    return;
                case "estado-cuenta":
                    if (segmentos.length == 4 && idDeTexto(segmentos[1]) != null && idDeTexto(segmentos[3]) != null) {
                        descargarComprobanteEstadoCuenta(request, response, segmentos);
                    } else if (segmentos.length == 3 && idDeTexto(segmentos[1]) != null && "informe".equals(segmentos[2])) {
                        generarInformeEstadoCuentaPdf(request, response, segmentos[1]);
                    } else if (segmentos.length == 3 && idDeTexto(segmentos[1]) != null && "csv".equals(segmentos[2])) {
                        exportarEstadoCuentaCsv(request, response, segmentos[1]);
                    } else if (segmentos.length == 2) {
                        mostrarEstadoCuentaFicha(request, response, segmentos[1]);
                    } else {
                        mostrarEstadoCuentaBusqueda(request, response);
                    }
                    return;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el reporte " + subreporte + ".", error);
            request.setAttribute("mensajeError", "No se pudo generar el reporte.");
            RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_INDICE);
            dispatcher.forward(request, response);
        }
    }

    /**
     * Estado de cuenta (§13): la reimpresión de recibo/factura/constancia vive acá, con ruta
     * propia bajo /reportes -- corrección de Joel ("no le da al oficial acceso a /cobranza, que
     * sigue con 403"). Mismo PDF que ServicioCobranza ya genera; solo cambia quién puede pedirlo.
     */
    private void descargarComprobanteEstadoCuenta(HttpServletRequest request, HttpServletResponse response,
            String[] segmentos) throws IOException {
        long idPrestamo = idDeTexto(segmentos[1]);
        String tipoDocumento = segmentos[2];
        long idDocumento = idDeTexto(segmentos[3]);
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String destino = request.getContextPath() + "/reportes/estado-cuenta/" + idPrestamo;
        try {
            byte[] pdf;
            String nombreArchivo;
            py.com.capitalcredito.sgcpc.servicios.ServicioCobranza servicioCobranza =
                    new py.com.capitalcredito.sgcpc.servicios.ServicioCobranza();
            if ("recibo".equals(tipoDocumento)) {
                pdf = servicioCobranza.obtenerPdfRecibo(idDocumento, solicitante.getIdUsuario());
                nombreArchivo = "recibo_" + idDocumento + ".pdf";
            } else if ("factura".equals(tipoDocumento)) {
                pdf = servicioCobranza.obtenerPdfFactura(idDocumento, solicitante.getIdUsuario());
                nombreArchivo = "factura_" + idDocumento + ".pdf";
            } else if ("constancia".equals(tipoDocumento)) {
                pdf = servicioCobranza.obtenerPdfConstancia(idDocumento, solicitante.getIdUsuario());
                nombreArchivo = "constancia_" + idDocumento + ".pdf";
            } else if ("nota-anulacion".equals(tipoDocumento)) {
                pdf = servicioCobranza.obtenerPdfNotaAnulacion(idDocumento, solicitante.getIdUsuario());
                nombreArchivo = "nota_anulacion_" + idDocumento + ".pdf";
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (NegocioException error) {
            py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash.guardarError(request.getSession(),
                    error.getMessage());
            response.sendRedirect(destino);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar un comprobante del préstamo " + idPrestamo + ".",
                    error);
            py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash.guardarError(request.getSession(),
                    "No se pudo generar el comprobante. Intente nuevamente.");
            response.sendRedirect(destino);
        }
    }

    private void mostrarVencimientos(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate desde = parsearFecha(request.getParameter("desde"), Reloj.hoy());
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), desde.plusDays(7));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("filas",
                    paginarReporte(request, servicioReportes.listarVencimientos(conexion, desde, hasta)));
            request.setAttribute("totales", servicioReportes.calcularTotalesVencimientos(conexion, desde, hasta));
        }
        reenviar(request, response, "vencimientos");
    }

    /** Bloque C, tanda 1 (pedido de Joel): "Informe PDF" de Vencimientos -- mismo mecanismo que
     * Morosidad/Cartera/Incobrables, con el filtro desde/hasta de la pantalla. */
    private void generarInformeVencimientosPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate desde = parsearFecha(request.getParameter("desde"), Reloj.hoy());
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), desde.plusDays(7));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<FilaVencimiento> filas = servicioReportes.listarVencimientos(conexion, desde, hasta);
            py.com.capitalcredito.sgcpc.dto.TotalesVencimientos totales =
                    servicioReportes.calcularTotalesVencimientos(conexion, desde, hasta);
            String textoFiltros = servicioInformes.construirTextoFiltrosVencimientos(desde, hasta);
            byte[] pdf = servicioInformes.generarInformeVencimientosPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_vencimientos.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de vencimientos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 1 (pedido de Joel): CSV de Vencimientos -- C.I./teléfono con
     * ExportadorCsv.comoTexto (E-7); montos quedan numéricos (ya crudos en el motor, D-146). */
    private void exportarVencimientosCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate desde = parsearFecha(request.getParameter("desde"), Reloj.hoy());
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), desde.plusDays(7));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<FilaVencimiento> filas = servicioReportes.listarVencimientos(conexion, desde, hasta);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "vencimientos.csv")) {
                escritor.println("Operación;Cliente;Teléfono;C.I.;Cuota;Vencimiento;Monto;Estado");
                for (FilaVencimiento fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.comoTexto(fila.getClienteTelefono()),
                            ExportadorCsv.comoTexto(fila.getClienteCedula()),
                            ExportadorCsv.comoTexto(fila.getNumeroCuota() + "/" + fila.getTotalCuotas()),
                            fila.getFechaVencimiento(), ExportadorCsv.limpiar(fila.getMonto()),
                            ExportadorCsv.comilla(fila.getEstado())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosVencimientos(desde, hasta);
            servicioInformes.auditarExportacionCsvVencimientos(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar vencimientos a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    /** Morosidad (§13): sin filtro de fecha, es una foto del momento (corrección de Joel). */
    private void mostrarMorosidad(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, usuario.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarPrestamosEnMora(conexion);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("subtotalesPorTramo", servicioReportes.calcularSubtotalesPorTramo(filas));
            request.setAttribute("totalGeneral", servicioReportes.calcularTotalGeneralMorosidad(filas));
        }
        reenviar(request, response, "morosidad");
    }

    /** Bloque C, tanda 1 (pedido de Joel): "Informe PDF" de Morosidad -- mismo mecanismo que B1-B5,
     * hoja horizontal. Recalcula la morosidad diaria primero (mismo criterio que mostrarMorosidad),
     * para que el PDF nunca quede desactualizado frente a la pantalla. */
    private void generarInformeMorosidadPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarPrestamosEnMora(conexion);
            List<py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad> subtotalesPorTramo =
                    servicioReportes.calcularSubtotalesPorTramo(filas);
            py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad totalGeneral =
                    servicioReportes.calcularTotalGeneralMorosidad(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosMorosidad(Reloj.hoy());
            byte[] pdf = servicioInformes.generarInformeMorosidadPdf(conexion, filas, subtotalesPorTramo, totalGeneral,
                    textoFiltros, solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_morosidad.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de morosidad.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 1 (pedido de Joel): CSV de Morosidad -- C.I./teléfono del cliente con
     * ExportadorCsv.comoTexto (E-7: Excel les saca el 0 inicial); montos y cantidades quedan
     * numéricos para poder sumarlos en Excel. */
    private void exportarMorosidadCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarPrestamosEnMora(conexion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "morosidad.csv")) {
                escritor.println("Operación;Cliente;Teléfono;C.I.;Días atraso;Tramo;"
                        + "Cuotas vencidas;Monto exigible;Capital vencido;Saldo");
                for (FilaMora fila : filas) {
                    List<String> columnas = new java.util.ArrayList<>(java.util.Arrays.asList(
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.comoTexto(fila.getClienteTelefono()),
                            ExportadorCsv.comoTexto(fila.getClienteCedula())));
                    columnas.add(String.valueOf(fila.getDiasAtraso()));
                    columnas.add(ExportadorCsv.comilla(fila.getTramo()));
                    columnas.add(String.valueOf(fila.getCuotasVencidas()));
                    columnas.add(fila.getMontoExigibleCrudo().toPlainString());
                    columnas.add(fila.getCapitalVencidoCrudo().toPlainString());
                    columnas.add(fila.getSaldoCrudo().toPlainString());
                    escritor.println(String.join(";", columnas));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosMorosidad(Reloj.hoy());
            servicioInformes.auditarExportacionCsvMorosidad(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar morosidad a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private void mostrarGestiones(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String tipo = vacioComoNulo(request.getParameter("tipo"));
        boolean soloIncumplidas = "true".equals(request.getParameter("soloIncumplidas"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        request.setAttribute("tipo", tipo);
        request.setAttribute("soloIncumplidas", soloIncumplidas);
        request.setAttribute("clienteUOperacion", clienteUOperacion);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // Bloque C, tanda 3 (pedido de Joel): listarGestiones ahora devuelve filas ya
            // formateadas (operación, contactado y quién registró resueltos adentro) -- no hace
            // falta armar los mapas de enriquecimiento acá, mismo molde que el resto de los
            // informes de este bloque.
            List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas =
                    servicioReportes.listarGestiones(conexion, desde, hasta, tipo, soloIncumplidas, clienteUOperacion);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesGestiones(filas));
        }
        reenviar(request, response, "gestiones");
    }

    /** Bloque C, tanda 3 (pedido de Joel): "Informe PDF" de Gestiones de cobranza -- mismo
     * mecanismo que los informes anteriores. */
    private void generarInformeGestionesPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String tipo = vacioComoNulo(request.getParameter("tipo"));
        boolean soloIncumplidas = "true".equals(request.getParameter("soloIncumplidas"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas =
                    servicioReportes.listarGestiones(conexion, desde, hasta, tipo, soloIncumplidas, clienteUOperacion);
            py.com.capitalcredito.sgcpc.dto.TotalesGestiones totales = servicioReportes.calcularTotalesGestiones(filas);
            String tipoEtiqueta = tipo == null ? null
                    : py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza.valueOf(tipo).getEtiqueta();
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosGestiones(desde, hasta, tipoEtiqueta, soloIncumplidas), clienteUOperacion);
            byte[] pdf = servicioInformes.generarInformeGestionesPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_gestiones.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de gestiones.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarGestionesCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String tipo = vacioComoNulo(request.getParameter("tipo"));
        boolean soloIncumplidas = "true".equals(request.getParameter("soloIncumplidas"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas =
                    servicioReportes.listarGestiones(conexion, desde, hasta, tipo, soloIncumplidas, clienteUOperacion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "gestiones_" + desde + "_" + hasta + ".csv")) {
                escritor.println("Fecha;Operación;Contactado;Tipo;Resultado;Promesa;Cumplida;Quién registró");
                for (py.com.capitalcredito.sgcpc.dto.FilaGestionReporte fila : filas) {
                    escritor.println(String.join(";", fila.getFechaHora(),
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getContactadoNombre()), ExportadorCsv.comilla(fila.getTipoEtiqueta()),
                            ExportadorCsv.comilla(fila.getResultado()), ExportadorCsv.comilla(fila.getPromesaTexto()),
                            fila.getCumplidaTexto(), ExportadorCsv.comilla(fila.getRegistradoPorNombre())));
                }
            }
            String tipoEtiqueta = tipo == null ? null
                    : py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza.valueOf(tipo).getEtiqueta();
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosGestiones(desde, hasta, tipoEtiqueta, soloIncumplidas), clienteUOperacion);
            servicioInformes.auditarExportacionCsvGestiones(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private void mostrarCartera(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, usuario.getIdUsuario());
            List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas = servicioReportes.listarCartera(conexion);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesCartera(filas));
            request.setAttribute("resumenPorEstado", servicioReportes.calcularResumenPorEstadoCartera(filas));
        }
        reenviar(request, response, "cartera");
    }

    /** Bloque C, tanda 1 (pedido de Joel): "Informe PDF" de Cartera -- mismo mecanismo que B1-B5,
     * hoja horizontal. */
    private void generarInformeCarteraPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas = servicioReportes.listarCartera(conexion);
            py.com.capitalcredito.sgcpc.dto.TotalesCartera totales = servicioReportes.calcularTotalesCartera(filas);
            List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera> resumenPorEstado =
                    servicioReportes.calcularResumenPorEstadoCartera(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosCartera(Reloj.hoy());
            byte[] pdf = servicioInformes.generarInformeCarteraPdf(conexion, filas, totales, resumenPorEstado,
                    textoFiltros, solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_cartera.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de cartera.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 1 (pedido de Joel): CSV de Cartera -- saldo e interés futuro quedan
     * numéricos (ya crudos en FilaCartera) para poder sumarlos en Excel; monto aprobado sale de
     * ExportadorCsv.limpiar sobre el valor ya formateado (FilaCartera no guarda su crudo, no hace
     * falta: no es columna de total en este informe). */
    private void exportarCarteraCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas = servicioReportes.listarCartera(conexion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "cartera.csv")) {
                escritor.println("Operación;Cliente;Monto aprobado;Saldo;Interés futuro programado;Estado");
                for (py.com.capitalcredito.sgcpc.dto.FilaCartera fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.limpiar(fila.getMontoAprobado()),
                            fila.getCapitalPendienteCrudo().toPlainString(),
                            fila.getInteresFuturoCrudo().toPlainString(),
                            ExportadorCsv.comilla(fila.getEstado())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosCartera(Reloj.hoy());
            servicioInformes.auditarExportacionCsvCartera(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar cartera a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private void mostrarResumenMensual(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        YearMonth mes = parsearMes(request.getParameter("mes"));
        request.setAttribute("mes", mes);
        // B211: estos tres reportes no tienen rango sino un mes -- mismo renglón, otro texto.
        request.setAttribute("rangoAplicado", ServicioReportes.textoMesAplicado(mes));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("resumen", servicioReportes.calcularResumenMensual(conexion, mes.atDay(1)));
        }
        reenviar(request, response, "resumen-mensual");
    }

    /** Bloque C, tanda 2 (pedido de Joel): "el PDF con el mismo contenido que la pantalla" --
     * mismo mecanismo que los demás informes, sin lista de filas (un solo resumen). */
    private void generarInformeResumenMensualPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            py.com.capitalcredito.sgcpc.dto.ResumenMensual resumen = servicioReportes.calcularResumenMensual(conexion, mes.atDay(1));
            String textoFiltros = servicioInformes.construirTextoFiltrosResumenMensual(mes);
            byte[] pdf = servicioInformes.generarInformeResumenMensualPdf(conexion, resumen, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_resumen_mensual.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de resumen mensual.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void descargarResumenMensualCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            py.com.capitalcredito.sgcpc.dto.ResumenMensual resumen =
                    servicioReportes.calcularResumenMensual(conexion, mes.atDay(1));
            // 13/10/2026 (hallazgo de Joel): faltaba, a diferencia de los demás informes.
            servicioInformes.auditarExportacionCsvResumenMensual(conexion, solicitante.getIdUsuario(),
                    servicioInformes.construirTextoFiltrosResumenMensual(mes));
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "resumen_mensual_" + mes + ".csv")) {
                escritor.println("Concepto;Valor");
                escritor.println("Préstamos otorgados (cantidad);" + resumen.getPrestamosOtorgadosCantidad());
                escritor.println("Préstamos otorgados (monto);" + ExportadorCsv.limpiar(resumen.getPrestamosOtorgadosMonto()));
                escritor.println("Cobrado capital;" + ExportadorCsv.limpiar(resumen.getCobradoCapital()));
                escritor.println("Cobrado interés;" + ExportadorCsv.limpiar(resumen.getCobradoInteres()));
                escritor.println("Cobrado moratorio;" + ExportadorCsv.limpiar(resumen.getCobradoMoratorio()));
                escritor.println("Cobrado punitorio;" + ExportadorCsv.limpiar(resumen.getCobradoPunitorio()));
                escritor.println("Cobrado IVA;" + ExportadorCsv.limpiar(resumen.getCobradoIva()));
                escritor.println("Cobrado total;" + ExportadorCsv.limpiar(resumen.getCobradoTotal()));
                escritor.println("Ganancia del mes;" + ExportadorCsv.limpiar(resumen.getGananciaDelMes()));
                escritor.println("Morosidad (cantidad);" + resumen.getMorosidadPorcentajeCantidad());
                // 13/10/2026 (hallazgo de Joel): la etiqueta decía "Morosidad (monto)", pero el
                // número es capital VENCIDO sobre la cartera -- no el saldo total en mora.
                escritor.println("Morosidad (capital vencido sobre la cartera);" + resumen.getMorosidadPorcentajeMonto());
                escritor.println("Saldo de préstamos en mora (% de la cartera);" + resumen.getSaldoPrestamosEnMoraPorcentaje());
            }
        }
    }

    // ==================================================================
    // Bloque 7/E10, Tanda D (D-128): resumen financiero, informes desde-
    // hasta y proyección de fondos.
    // ==================================================================

    private void mostrarResumenFinanciero(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.withDayOfMonth(1));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("resumen", servicioReportes.calcularResumenFinanciero(conexion, desde, hasta));
        }
        reenviar(request, response, "resumen-financiero");
    }

    /** Bloque C, tanda 2 (pedido de Joel): "el PDF con el mismo contenido que la pantalla" --
     * mismo mecanismo que los demás informes, sin lista de filas (un solo resumen). */
    private void generarInformeResumenFinancieroPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.withDayOfMonth(1));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            py.com.capitalcredito.sgcpc.dto.ResumenFinanciero resumen = servicioReportes.calcularResumenFinanciero(conexion, desde, hasta);
            String textoFiltros = servicioInformes.construirTextoFiltrosResumenFinanciero(desde, hasta);
            byte[] pdf = servicioInformes.generarInformeResumenFinancieroPdf(conexion, resumen, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_resumen_financiero.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de resumen financiero.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void descargarResumenFinancieroCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.withDayOfMonth(1));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            py.com.capitalcredito.sgcpc.dto.ResumenFinanciero resumen =
                    servicioReportes.calcularResumenFinanciero(conexion, desde, hasta);
            // 13/10/2026 (hallazgo de Joel): faltaba, a diferencia de los demás informes.
            servicioInformes.auditarExportacionCsvResumenFinanciero(conexion, solicitante.getIdUsuario(),
                    servicioInformes.construirTextoFiltrosResumenFinanciero(desde, hasta));
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "resumen_financiero_" + desde + "_" + hasta + ".csv")) {
                escritor.println("Concepto;Valor");
                escritor.println("Otorgados (cantidad);" + resumen.getOtorgadosCantidad());
                escritor.println("Otorgados (monto);" + ExportadorCsv.limpiar(resumen.getOtorgadosMonto()));
                escritor.println("En trámite (cantidad);" + resumen.getEnTramiteCantidad());
                escritor.println("En trámite (monto);" + ExportadorCsv.limpiar(resumen.getEnTramiteMonto()));
                escritor.println("Aprobados sin desembolsar (cantidad);" + resumen.getAprobadosSinDesembolsarCantidad());
                escritor.println("Aprobados sin desembolsar (monto);" + ExportadorCsv.limpiar(resumen.getAprobadosSinDesembolsarMonto()));
                escritor.println("Cobrado capital;" + ExportadorCsv.limpiar(resumen.getCobradoCapital()));
                escritor.println("Cobrado interés;" + ExportadorCsv.limpiar(resumen.getCobradoInteres()));
                escritor.println("Cobrado moratorio;" + ExportadorCsv.limpiar(resumen.getCobradoMoratorio()));
                escritor.println("Cobrado punitorio;" + ExportadorCsv.limpiar(resumen.getCobradoPunitorio()));
                escritor.println("Cobrado IVA;" + ExportadorCsv.limpiar(resumen.getCobradoIva()));
                escritor.println("Cobrado total;" + ExportadorCsv.limpiar(resumen.getCobradoTotal()));
                escritor.println("Ganancia del período;" + ExportadorCsv.limpiar(resumen.getGananciaDelPeriodo()));
                escritor.println("Cartera -- capital pendiente;" + ExportadorCsv.limpiar(resumen.getCarteraCapitalPendiente()));
                escritor.println("Cartera -- interés futuro;" + ExportadorCsv.limpiar(resumen.getCarteraInteresFuturo()));
                escritor.println("Morosidad (cantidad);" + resumen.getMorosidadPorcentajeCantidad());
                // 13/10/2026 (hallazgo de Joel): la etiqueta decía "Morosidad (monto)", pero el
                // número es capital VENCIDO sobre la cartera -- no el saldo total en mora.
                escritor.println("Morosidad (capital vencido sobre la cartera);" + resumen.getMorosidadPorcentajeMonto());
                escritor.println("Saldo de préstamos en mora (% de la cartera);" + resumen.getSaldoPrestamosEnMoraPorcentaje());
                for (py.com.capitalcredito.sgcpc.dto.SubtotalTramo tramo : resumen.getDemandaPorMonto()) {
                    escritor.println("Demanda por monto " + tramo.getTramo() + " (cantidad);" + tramo.getCantidad());
                    escritor.println("Demanda por monto " + tramo.getTramo() + " (monto);" + ExportadorCsv.limpiar(tramo.getMonto()));
                }
                for (py.com.capitalcredito.sgcpc.dto.SubtotalTramo tramo : resumen.getDemandaPorPlazo()) {
                    escritor.println("Demanda por plazo " + tramo.getTramo() + " (cantidad);" + tramo.getCantidad());
                    escritor.println("Demanda por plazo " + tramo.getTramo() + " (monto);" + ExportadorCsv.limpiar(tramo.getMonto()));
                }
            }
        }
    }

    private void mostrarInformeSolicitudes(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro = construirFiltroSolicitudesDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        // B211: igual que en los otros ocho reportes, pero el rango vive adentro del filtro.
        request.setAttribute("rangoAplicado",
                ServicioReportes.textoRangoAplicado(filtro.getDesde(), filtro.getHasta()));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas =
                    servicioReportes.listarInformeSolicitudes(conexion, filtro);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesSolicitudes(filas));
            request.setAttribute("resumenPorEstado", servicioReportes.calcularResumenPorEstadoSolicitudes(filas));
            request.setAttribute("oficiales", servicioReportes.listarOficiales(conexion));
        }
        reenviar(request, response, "informe-solicitudes");
    }

    /** B2 (Bloque 7/E10, pedido de Joel): "Informe PDF" -- mismo mecanismo que B1 (Préstamos),
     * hoja horizontal, con GeneradorInformesPdf (D-135). Se abre en pestaña nueva ("inline"). */
    private void generarInformeSolicitudesPdf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro = construirFiltroSolicitudesDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas =
                    servicioReportes.listarInformeSolicitudes(conexion, filtro);
            py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte totales = servicioReportes.calcularTotalesSolicitudes(filas);
            List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud> resumenPorEstado =
                    servicioReportes.calcularResumenPorEstadoSolicitudes(filas);
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosSolicitudes(filtro,
                            etiquetaFiltroEstadoSolicitud(filtro.getEstado()), etiquetaFiltroOficial(conexion, filtro.getIdOficial())),
                    filtro.getClienteUOperacion());
            byte[] pdf = servicioInformes.generarInformeSolicitudesPdf(conexion, filas, totales, resumenPorEstado,
                    textoFiltros, solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_solicitudes.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de solicitudes.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** B2 (Bloque 7/E10, pedido de Joel): CSV con las mismas columnas/filtros que el informe PDF
     * -- número y C.I. van marcadas como texto (ExportadorCsv.comoTexto, E-7); el monto sigue
     * como número plano, para que se pueda sumar en Excel. */
    private void exportarSolicitudesCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro = construirFiltroSolicitudesDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas =
                    servicioReportes.listarInformeSolicitudes(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "solicitudes.csv")) {
                escritor.println("Número;Fecha;Cliente;C.I.;Monto solicitado;Plazo (meses);Estado;Oficial");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comoTexto(fila.getNumero()), fila.getFecha(),
                            ExportadorCsv.comilla(fila.getClienteNombre()), ExportadorCsv.comoTexto(fila.getClienteCedula()),
                            fila.getMontoSolicitado().toPlainString(), String.valueOf(fila.getPlazoMeses()),
                            fila.getEstado(), ExportadorCsv.comilla(fila.getOficialNombre())));
                }
            }
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosSolicitudes(filtro,
                            etiquetaFiltroEstadoSolicitud(filtro.getEstado()), etiquetaFiltroOficial(conexion, filtro.getIdOficial())),
                    filtro.getClienteUOperacion());
            servicioInformes.auditarExportacionCsvSolicitudes(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar solicitudes a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte construirFiltroSolicitudesDesdeRequest(
            HttpServletRequest request) {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro = new py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte();
        filtro.setDesde(desde);
        filtro.setHasta(hasta);
        filtro.setEstado(vacioComoNulo(request.getParameter("estado")));
        filtro.setIdOficial(idDeTexto(request.getParameter("idOficial")));
        filtro.setClienteUOperacion(vacioComoNulo(request.getParameter("clienteUOperacion")));
        return filtro;
    }

    private String etiquetaFiltroEstadoSolicitud(String estado) {
        if (estado == null) {
            return "Todos los estados";
        }
        try {
            return py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.valueOf(estado).getEtiqueta();
        } catch (IllegalArgumentException error) {
            return "Todos los estados";
        }
    }

    private String etiquetaFiltroOficial(Connection conexion, Long idOficial) throws SQLException {
        if (idOficial == null) {
            return "Todos los oficiales";
        }
        return servicioReportes.obtenerNombreCajero(conexion, idOficial);
    }

    // ======================================================================
    // B3 (Bloque 7/E10, pedido de Joel): /reportes/aprobaciones -- pantalla nueva, mismo
    // mecanismo que B2 (Solicitudes).
    // ======================================================================

    private void mostrarInformeAprobaciones(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte filtro = construirFiltroAprobacionesDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        // B211: igual que en los otros ocho reportes, pero el rango vive adentro del filtro.
        request.setAttribute("rangoAplicado",
                ServicioReportes.textoRangoAplicado(filtro.getDesde(), filtro.getHasta()));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas =
                    servicioReportes.listarInformeAprobaciones(conexion, filtro);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesAprobaciones(filas));
        }
        reenviar(request, response, "informe-aprobaciones");
    }

    /** B3 (Bloque 7/E10, pedido de Joel): "Informe PDF" -- mismo mecanismo que B2 (Solicitudes),
     * hoja horizontal, con GeneradorInformesPdf (D-135). Se abre en pestaña nueva ("inline"). */
    private void generarInformeAprobacionesPdf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte filtro = construirFiltroAprobacionesDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas =
                    servicioReportes.listarInformeAprobaciones(conexion, filtro);
            py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones totales = servicioReportes.calcularTotalesAprobaciones(filas);
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosAprobaciones(filtro), filtro.getClienteUOperacion());
            byte[] pdf = servicioInformes.generarInformeAprobacionesPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_aprobaciones.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de aprobaciones.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** B3 (Bloque 7/E10, pedido de Joel): CSV con las mismas columnas/filtros que el informe PDF
     * -- número y C.I. (ninguna acá, pero mismo criterio que B1/B2) van como texto; acá el único
     * dato que podría leerse mal en Excel es el número de solicitud, marcado con comoTexto (E-7). */
    private void exportarAprobacionesCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte filtro = construirFiltroAprobacionesDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas =
                    servicioReportes.listarInformeAprobaciones(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "aprobaciones.csv")) {
                escritor.println("Número;Cliente;Monto solicitado;Monto aprobado;Decisión;Decidido por;"
                        + "Fecha de envío;Fecha de decisión;Tiempo de respuesta;Motivo");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comoTexto(fila.getNumero()), ExportadorCsv.comilla(fila.getClienteNombre()),
                            fila.getMontoSolicitado().toPlainString(),
                            fila.getMontoAprobado() == null ? "" : fila.getMontoAprobado().toPlainString(),
                            fila.getDecision(), ExportadorCsv.comilla(fila.getDecididoPorNombre()),
                            fila.getFechaEnvio(), fila.getFechaDecision(),
                            ExportadorCsv.comilla(fila.getTiempoRespuestaFormateado()),
                            ExportadorCsv.comilla(fila.getMotivo())));
                }
            }
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosAprobaciones(filtro), filtro.getClienteUOperacion());
            servicioInformes.auditarExportacionCsvAprobaciones(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar aprobaciones a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte construirFiltroAprobacionesDesdeRequest(
            HttpServletRequest request) {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte filtro = new py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte();
        filtro.setDesde(desde);
        filtro.setHasta(hasta);
        filtro.setClienteUOperacion(vacioComoNulo(request.getParameter("clienteUOperacion")));
        return filtro;
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): /reportes/desembolsos -- mismo mecanismo que B2/B3.
    // ======================================================================

    private void mostrarInformeDesembolsos(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte filtro = construirFiltroDesembolsosDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        // B211: igual que en los otros ocho reportes, pero el rango vive adentro del filtro.
        request.setAttribute("rangoAplicado",
                ServicioReportes.textoRangoAplicado(filtro.getDesde(), filtro.getHasta()));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas =
                    servicioReportes.listarInformeDesembolsos(conexion, filtro);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesDesembolsos(filas));
        }
        reenviar(request, response, "informe-desembolsos");
    }

    private void generarInformeDesembolsosPdf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte filtro = construirFiltroDesembolsosDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas =
                    servicioReportes.listarInformeDesembolsos(conexion, filtro);
            py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos totales = servicioReportes.calcularTotalesDesembolsos(filas);
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosDesembolsos(filtro), filtro.getClienteUOperacion());
            byte[] pdf = servicioInformes.generarInformeDesembolsosPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_desembolsos.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de desembolsos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** B4 (Bloque 7/E10, pedido de Joel): CSV -- comprobante y C.I. como texto (E-7), monto numérico. */
    private void exportarDesembolsosCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte filtro = construirFiltroDesembolsosDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas =
                    servicioReportes.listarInformeDesembolsos(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "desembolsos.csv")) {
                escritor.println("Fecha;Comprobante;Cliente;C.I.;Operación;Monto;Desembolsado por");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso fila : filas) {
                    escritor.println(String.join(";",
                            fila.getFecha(), ExportadorCsv.comoTexto(fila.getComprobante()),
                            ExportadorCsv.comilla(fila.getClienteNombre()), ExportadorCsv.comoTexto(fila.getClienteCedula()),
                            ExportadorCsv.comoTexto(fila.getOperacion()), fila.getMonto().toPlainString(),
                            ExportadorCsv.comilla(fila.getDesembolsadoPorNombre())));
                }
            }
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosDesembolsos(filtro), filtro.getClienteUOperacion());
            servicioInformes.auditarExportacionCsvDesembolsos(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar desembolsos a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte construirFiltroDesembolsosDesdeRequest(
            HttpServletRequest request) {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte filtro = new py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte();
        filtro.setDesde(desde);
        filtro.setHasta(hasta);
        filtro.setClienteUOperacion(vacioComoNulo(request.getParameter("clienteUOperacion")));
        return filtro;
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): /reportes/cobros -- mismo mecanismo que B2/B3.
    // ======================================================================

    private void mostrarInformeCobros(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte filtro = construirFiltroCobrosDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        // B211: igual que en los otros ocho reportes, pero el rango vive adentro del filtro.
        request.setAttribute("rangoAplicado",
                ServicioReportes.textoRangoAplicado(filtro.getDesde(), filtro.getHasta()));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas = servicioReportes.listarInformeCobros(conexion, filtro);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesCobros(filas));
        }
        reenviar(request, response, "informe-cobros");
    }

    private void generarInformeCobrosPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte filtro = construirFiltroCobrosDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas = servicioReportes.listarInformeCobros(conexion, filtro);
            py.com.capitalcredito.sgcpc.dto.TotalesCobros totales = servicioReportes.calcularTotalesCobros(filas);
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosCobros(filtro), filtro.getClienteUOperacion());
            byte[] pdf = servicioInformes.generarInformeCobrosPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_cobros.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de cobros.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** B4 (Bloque 7/E10, pedido de Joel): CSV -- recibo, operación y C.I. como texto (E-7); los
     * montos quedan numéricos (para poder sumarlos en Excel), incluidos los de un REVERTIDO (se ve
     * el importe real que tuvo ese cobro, la columna Tipo ya dice "— Revertido"). */
    private void exportarCobrosCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte filtro = construirFiltroCobrosDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas = servicioReportes.listarInformeCobros(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "cobros.csv")) {
                escritor.println("Fecha;Recibo;Cliente;C.I.;Operación;Tipo;Capital;Interés;Moratorio;Punitorio;IVA;Total;Cajera");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeCobro fila : filas) {
                    escritor.println(String.join(";",
                            fila.getFecha(), ExportadorCsv.comoTexto(fila.getRecibo()),
                            ExportadorCsv.comilla(fila.getClienteNombre()), ExportadorCsv.comoTexto(fila.getClienteCedula()),
                            ExportadorCsv.comoTexto(fila.getOperacion()), ExportadorCsv.comilla(fila.getTipo()),
                            fila.getCapital().toPlainString(), fila.getInteres().toPlainString(),
                            fila.getMoratorio().toPlainString(), fila.getPunitorio().toPlainString(),
                            fila.getIva().toPlainString(), fila.getTotal().toPlainString(),
                            ExportadorCsv.comilla(fila.getCajeraNombre())));
                }
            }
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosCobros(filtro), filtro.getClienteUOperacion());
            servicioInformes.auditarExportacionCsvCobros(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar cobros a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte construirFiltroCobrosDesdeRequest(HttpServletRequest request) {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte filtro = new py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte();
        filtro.setDesde(desde);
        filtro.setHasta(hasta);
        filtro.setClienteUOperacion(vacioComoNulo(request.getParameter("clienteUOperacion")));
        return filtro;
    }

    private void mostrarInformeReversiones(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas =
                    servicioReportes.listarReversionesPorRango(conexion, desde, hasta);
            request.setAttribute("filas", paginarReporte(request, filas));
            py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales = servicioReportes.calcularTotalesReversiones(filas);
            request.setAttribute("totales", totales);
            // C-3b (pedido de Joel): el mismo texto desglosado en pantalla, PDF y CSV.
            request.setAttribute("resumen", servicioInformes.construirResumenReversiones(totales));
        }
        reenviar(request, response, "informe-reversiones");
    }

    /** Bloque C, tanda 3 (pedido de Joel): "Informe PDF" de Reversiones -- mismo mecanismo que los
     * informes anteriores. */
    private void generarInformeReversionesPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas =
                    servicioReportes.listarReversionesPorRango(conexion, desde, hasta);
            py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales = servicioReportes.calcularTotalesReversiones(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosReversiones(desde, hasta);
            byte[] pdf = servicioInformes.generarInformeReversionesPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_reversiones.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de reversiones.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarReversionesCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas =
                    servicioReportes.listarReversionesPorRango(conexion, desde, hasta);
            py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales = servicioReportes.calcularTotalesReversiones(filas);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "reversiones_" + desde + "_" + hasta + ".csv")) {
                escritor.println("Fecha;Recibo revertido;Operación;Cliente;Monto;Motivo;Estado;Quién solicitó;"
                        + "Quién autorizó;Nota de anulación");
                for (py.com.capitalcredito.sgcpc.dto.FilaReversionReporte fila : filas) {
                    escritor.println(String.join(";", fila.getFecha(), ExportadorCsv.comilla(fila.getNumeroRecibo()),
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()), ExportadorCsv.limpiar(fila.getMontoFormateado()),
                            ExportadorCsv.comilla(fila.getMotivo()), ExportadorCsv.comilla(fila.getEstado()),
                            ExportadorCsv.comilla(fila.getSolicitadoPorNombre()),
                            ExportadorCsv.comilla(fila.getAutorizadoPorNombre()),
                            ExportadorCsv.comilla(fila.getNotaAnulacion())));
                }
                // C-3b (pedido de Joel): el mismo resumen desglosado que pantalla y PDF, como
                // última línea (una sola celda de texto, el resto de columnas vacías).
                escritor.println();
                escritor.println(ExportadorCsv.comilla(servicioInformes.construirResumenReversiones(totales)));
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosReversiones(desde, hasta);
            servicioInformes.auditarExportacionCsvReversiones(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private void mostrarInformeClientesNuevos(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas =
                    servicioReportes.listarClientesNuevosPorRango(conexion, desde, hasta);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesClientesNuevos(filas));
        }
        reenviar(request, response, "informe-clientes-nuevos");
    }

    /** Bloque C, tanda 3 (pedido de Joel): "Informe PDF" de Clientes nuevos -- mismo mecanismo que
     * los informes anteriores. */
    private void generarInformeClientesNuevosPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas =
                    servicioReportes.listarClientesNuevosPorRango(conexion, desde, hasta);
            py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos totales = servicioReportes.calcularTotalesClientesNuevos(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosClientesNuevos(desde, hasta);
            byte[] pdf = servicioInformes.generarInformeClientesNuevosPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_clientes_nuevos.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de clientes nuevos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarClientesNuevosCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas =
                    servicioReportes.listarClientesNuevosPorRango(conexion, desde, hasta);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "clientes_nuevos_" + desde + "_" + hasta + ".csv")) {
                escritor.println("C.I.;Nombre;Fecha de alta;Teléfono;Ciudad;Situación laboral;Préstamo");
                for (py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo fila : filas) {
                    // E-7 (pedido de Joel): C.I. y Teléfono marcados como texto -- mismo riesgo de
                    // cero inicial que el resto de los CSV de este sistema.
                    escritor.println(String.join(";", ExportadorCsv.comoTexto(fila.getCedula()),
                            ExportadorCsv.comilla(fila.getNombre()), fila.getFechaRegistro(),
                            ExportadorCsv.comoTexto(fila.getTelefono()), ExportadorCsv.comilla(fila.getCiudad()),
                            ExportadorCsv.comilla(fila.getSituacionLaboral()), fila.getEstadoUltimoPrestamo()));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosClientesNuevos(desde, hasta);
            servicioInformes.auditarExportacionCsvClientesNuevos(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private void mostrarInformeCierresDocumentales(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare filtroPagare =
                parsearFiltroEstadoPagare(request.getParameter("pagare"));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        request.setAttribute("filtroPagare", filtroPagare);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas =
                    servicioReportes.listarCierresDocumentalesPorRango(conexion, desde, hasta, filtroPagare);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesCierresDocumentales(filas));
        }
        reenviar(request, response, "informe-cierres-documentales");
    }

    /** E-12 (pedido de Joel): "Todos/Devueltos/Pendientes de devolución" -- mismo criterio que
     * parsearFecha/parsearMes (valor no reconocido u omitido = TODOS, nunca un error). */
    private py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare parsearFiltroEstadoPagare(String texto) {
        if ("DEVUELTOS".equals(texto)) {
            return py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare.DEVUELTOS;
        }
        if ("PENDIENTES".equals(texto)) {
            return py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare.PENDIENTES;
        }
        return py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare.TODOS;
    }

    /** Bloque C, tanda 2 (pedido de Joel): "Informe PDF" de Cierres documentales -- mismo
     * mecanismo que los demás informes. */
    private void generarInformeCierresDocumentalesPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare filtroPagare =
                parsearFiltroEstadoPagare(request.getParameter("pagare"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas =
                    servicioReportes.listarCierresDocumentalesPorRango(conexion, desde, hasta, filtroPagare);
            py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales totales = servicioReportes.calcularTotalesCierresDocumentales(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosCierresDocumentales(desde, hasta, filtroPagare);
            byte[] pdf = servicioInformes.generarInformeCierresDocumentalesPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_cierres_documentales.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de cierres documentales.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 2 (pedido de Joel), ampliado E-12: CSV de Cierres documentales, mismas
     * columnas que la pantalla y el PDF. */
    private void exportarCierresDocumentalesCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(90));
        py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare filtroPagare =
                parsearFiltroEstadoPagare(request.getParameter("pagare"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas =
                    servicioReportes.listarCierresDocumentalesPorRango(conexion, desde, hasta, filtroPagare);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "cierres_documentales.csv")) {
                escritor.println("Operación;Cliente;Fecha de cancelación;Constancia de cancelación;Estado del pagaré;"
                        + "Constancia de devolución;Fecha de devolución;Quién entregó");
                for (py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()), fila.getFechaCancelacion(),
                            ExportadorCsv.comilla(fila.getNumeroConstanciaCancelacion()),
                            ExportadorCsv.comilla(fila.getEstadoPagare()),
                            ExportadorCsv.comilla(fila.getNumeroConstanciaDevolucion()), fila.getFechaDevolucionPagare(),
                            ExportadorCsv.comilla(fila.getEntregadoPorNombre())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosCierresDocumentales(desde, hasta, filtroPagare);
            servicioInformes.auditarExportacionCsvCierresDocumentales(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar cierres documentales a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private void mostrarProyeccionFondos(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("filas", servicioReportes.calcularProyeccionFondos(conexion));
        }
        reenviar(request, response, "proyeccion-fondos");
    }

    /** Bloque C, tanda 1 (pedido de Joel): "Informe PDF" de Proyección de fondos -- mismo
     * mecanismo que Morosidad/Cartera/Incobrables/Vencimientos. */
    private void generarInformeProyeccionFondosPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos> filas = servicioReportes.calcularProyeccionFondos(conexion);
            String textoFiltros = servicioInformes.construirTextoFiltrosProyeccionFondos(Reloj.hoy());
            byte[] pdf = servicioInformes.generarInformeProyeccionFondosPdf(conexion, filas, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_proyeccion_fondos.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de proyección de fondos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 1 (pedido de Joel): CSV de Proyección de fondos -- sin C.I./teléfono/
     * fracciones "N/M", ningún riesgo E-7/E-8; montos quedan numéricos con ExportadorCsv.limpiar. */
    private void exportarProyeccionFondosCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos> filas = servicioReportes.calcularProyeccionFondos(conexion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "proyeccion_fondos.csv")) {
                escritor.println("Horizonte (días);Ingresos esperados;Egresos comprometidos;Posibles egresos en trámite;Flujo neto");
                for (py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos fila : filas) {
                    escritor.println(String.join(";",
                            String.valueOf(fila.getHorizonteDias()), ExportadorCsv.limpiar(fila.getIngresosEsperados()),
                            ExportadorCsv.limpiar(fila.getEgresosComprometidos()),
                            ExportadorCsv.limpiar(fila.getPosiblesEgresosEnTramite()),
                            ExportadorCsv.limpiar(fila.getFlujoNeto())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosProyeccionFondos(Reloj.hoy());
            servicioInformes.auditarExportacionCsvProyeccionFondos(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar proyección de fondos a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    // ======================================================================
    // C-4.4 (pedido de Joel): Consultas al buró -- último informe del bloque C. Mismos permisos
    // que la pestaña de buró en la ficha del cliente (gerente y oficial, ver SUBREPORTES_OFICIAL).
    // Solo lectura: nunca cambia ninguna consulta ni consentimiento.
    // ======================================================================

    private void mostrarConsultasBuro(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String clasificacion = clasificacionDeTexto(request.getParameter("resultado"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        request.setAttribute("resultado", clasificacion);
        request.setAttribute("clienteUOperacion", clienteUOperacion);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("clasificacionesDisponibles", servicioReportes.listarClasificacionesBuroDistintas(conexion));
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas =
                    servicioReportes.listarConsultasBuro(conexion, desde, hasta, clasificacion, clienteUOperacion);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesConsultasBuro(filas));
        }
        reenviar(request, response, "consultas-buro");
    }

    private void generarInformeConsultasBuroPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String clasificacion = clasificacionDeTexto(request.getParameter("resultado"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas =
                    servicioReportes.listarConsultasBuro(conexion, desde, hasta, clasificacion, clienteUOperacion);
            py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro totales = servicioReportes.calcularTotalesConsultasBuro(filas);
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosConsultasBuro(desde, hasta, etiquetaResultado(clasificacion)),
                    clienteUOperacion);
            byte[] pdf = servicioInformes.generarInformeConsultasBuroPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_consultas_buro.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de consultas al buró.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarConsultasBuroCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.minusDays(30));
        String clasificacion = clasificacionDeTexto(request.getParameter("resultado"));
        String clienteUOperacion = vacioComoNulo(request.getParameter("clienteUOperacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas =
                    servicioReportes.listarConsultasBuro(conexion, desde, hasta, clasificacion, clienteUOperacion);
            // C.I. como texto (Joel): mismo riesgo de cero inicial que el resto de los CSV de este sistema.
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "consultas_buro_" + desde + "_" + hasta + ".csv")) {
                escritor.println("Fecha;Cliente;C.I.;Solicitud asociada;Resultado/Calificación;Quién consultó;"
                        + "Autorización vigente en ese momento");
                for (py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro fila : filas) {
                    escritor.println(String.join(";", fila.getFecha(), ExportadorCsv.comilla(fila.getCliente()),
                            ExportadorCsv.comoTexto(fila.getCedula()), ExportadorCsv.comilla(fila.getSolicitud()),
                            ExportadorCsv.comilla(fila.getResultado()), ExportadorCsv.comilla(fila.getQuienConsulto()),
                            fila.getAutorizacionVigente()));
                }
            }
            String textoFiltros = servicioInformes.agregarClausulaClienteUOperacion(
                    servicioInformes.construirTextoFiltrosConsultasBuro(desde, hasta, etiquetaResultado(clasificacion)),
                    clienteUOperacion);
            servicioInformes.auditarExportacionCsvConsultasBuro(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private String clasificacionDeTexto(String parametro) {
        return (parametro == null || SENTINEL_TODOS_LOS_RESULTADOS.equals(parametro)) ? null : parametro;
    }

    private String etiquetaResultado(String clasificacion) {
        if (clasificacion == null) {
            return null;
        }
        return clasificacion.trim().isEmpty() ? "Sin clasificación" : clasificacion;
    }

    private void mostrarCaja(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro = construirFiltroCajaDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        // B211: igual que en los otros ocho reportes, pero el rango vive adentro del filtro.
        request.setAttribute("rangoAplicado",
                ServicioReportes.textoRangoAplicado(filtro.getDesde(), filtro.getHasta()));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas = servicioReportes.listarInformeCaja(conexion, filtro);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesCaja(filas));
        }
        reenviar(request, response, "caja");
    }

    /** B5 (Bloque 7/E10, pedido de Joel): "Informe PDF" -- mismo mecanismo que B1-B4, hoja
     * horizontal, con GeneradorInformesPdf (D-135). Se abre en pestaña nueva ("inline"). */
    private void generarInformeCajaPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro = construirFiltroCajaDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas = servicioReportes.listarInformeCaja(conexion, filtro);
            py.com.capitalcredito.sgcpc.dto.TotalesCaja totales = servicioReportes.calcularTotalesCaja(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosCaja(filtro);
            byte[] pdf = servicioInformes.generarInformeCajaPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_caja.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de caja.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** B5 (Bloque 7/E10, pedido de Joel): CSV -- fondo inicial/saldo teórico/efectivo contado/
     * diferencia quedan numéricos (con signo: positivo sobra, negativo falta), para poder
     * sumarlos en Excel; ninguna columna de Caja tiene el riesgo de E-7/E-8 (ni ceros iniciales
     * ni fracciones "N/M"), así que no hace falta ExportadorCsv.comoTexto acá. */
    private void exportarCajaCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro = construirFiltroCajaDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas = servicioReportes.listarInformeCaja(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "caja.csv")) {
                escritor.println("Cajera;Apertura;Cierre;Fondo inicial;Saldo teórico;Efectivo contado;Diferencia;Revisada");
                for (py.com.capitalcredito.sgcpc.dto.FilaInformeCaja fila : filas) {
                    escritor.println(String.join(";",
                            ExportadorCsv.comilla(fila.getCajeraNombre()), fila.getApertura(), fila.getCierre(),
                            fila.getFondoInicial().toPlainString(),
                            fila.getSaldoTeorico() == null ? "" : fila.getSaldoTeorico().toPlainString(),
                            fila.getEfectivoContado() == null ? "" : fila.getEfectivoContado().toPlainString(),
                            fila.getDiferencia() == null ? "" : fila.getDiferencia().toPlainString(),
                            fila.isRevisada() ? "Sí" : "No"));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosCaja(filtro);
            servicioInformes.auditarExportacionCsvCaja(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar caja a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte construirFiltroCajaDesdeRequest(HttpServletRequest request) {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta.withDayOfMonth(1));
        py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro = new py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte();
        filtro.setDesde(desde);
        filtro.setHasta(hasta);
        return filtro;
    }

    private void mostrarIva(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        YearMonth mes = parsearMes(request.getParameter("mes"));
        request.setAttribute("mes", mes);
        // B211: estos tres reportes no tienen rango sino un mes -- mismo renglón, otro texto.
        request.setAttribute("rangoAplicado", ServicioReportes.textoMesAplicado(mes));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaIva> filas = servicioReportes.listarIva(conexion, mes.atDay(1));
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesIva(filas));
        }
        reenviar(request, response, "iva");
    }

    private void descargarIvaCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaIva> filas = servicioReportes.listarIva(conexion, mes.atDay(1));
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "iva_" + mes + ".csv")) {
                escritor.println("Factura;Fecha;Cliente;Documento;Exenta;Gravada 5%;IVA 5%;Gravada 10%;IVA 10%;Total;Anulada");
                for (py.com.capitalcredito.sgcpc.dto.FilaIva fila : filas) {
                    // E-7 (pedido de Joel): Factura y Documento (C.I.) marcados como texto -- Excel
                    // en español convertía un número con cero inicial y lo perdía.
                    escritor.println(String.join(";", ExportadorCsv.comoTexto(fila.getNumeroFactura()), fila.getFecha(),
                            ExportadorCsv.comilla(fila.getClienteNombre()), ExportadorCsv.comoTexto(fila.getClienteDocumento()),
                            ExportadorCsv.limpiar(fila.getTotalExenta()), "0", "0",
                            ExportadorCsv.limpiar(fila.getTotalGravada10()), ExportadorCsv.limpiar(fila.getIva10()),
                            ExportadorCsv.limpiar(fila.getTotal()), fila.isAnulada() ? "Sí" : "No"));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosIva(mes);
            servicioInformes.auditarExportacionCsvIva(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    /** Bloque C, tanda 2 (pedido de Joel): "Informe PDF" de IVA del mes -- mismo mecanismo que
     * los informes anteriores. */
    private void generarInformeIvaPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaIva> filas = servicioReportes.listarIva(conexion, mes.atDay(1));
            py.com.capitalcredito.sgcpc.dto.TotalesIva totales = servicioReportes.calcularTotalesIva(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosIva(mes);
            byte[] pdf = servicioInformes.generarInformeIvaPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_iva.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de IVA.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void mostrarFacturasAnuladas(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        YearMonth mes = parsearMes(request.getParameter("mes"));
        request.setAttribute("mes", mes);
        // B211: estos tres reportes no tienen rango sino un mes -- mismo renglón, otro texto.
        request.setAttribute("rangoAplicado", ServicioReportes.textoMesAplicado(mes));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas =
                    servicioReportes.listarFacturasAnuladas(conexion, mes.atDay(1));
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesFacturasAnuladas(filas));
        }
        reenviar(request, response, "facturas-anuladas");
    }

    /** Bloque C, tanda 2 (pedido de Joel): "Informe PDF" de Facturas anuladas -- mismo mecanismo
     * que los informes anteriores. */
    private void generarInformeFacturasAnuladasPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas =
                    servicioReportes.listarFacturasAnuladas(conexion, mes.atDay(1));
            py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas totales =
                    servicioReportes.calcularTotalesFacturasAnuladas(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosFacturasAnuladas(mes);
            byte[] pdf = servicioInformes.generarInformeFacturasAnuladasPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_facturas_anuladas.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de facturas anuladas.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void descargarFacturasAnuladasCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        YearMonth mes = parsearMes(request.getParameter("mes"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas =
                    servicioReportes.listarFacturasAnuladas(conexion, mes.atDay(1));
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "facturas_anuladas_" + mes + ".csv")) {
                escritor.println("Factura;Timbrado;Fecha;Cliente;Documento;Total;Nota de anulación;"
                        + "Fecha de anulación;Motivo;Quién anuló");
                for (py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada fila : filas) {
                    // E-7/E-8 (pedido de Joel): Factura, Timbrado y Documento (C.I.) marcados
                    // como texto -- Timbrado es un identificador de 8 dígitos, el mismo riesgo de
                    // cero inicial que C.I., encontrado al revisar el resto de los CSV para E-8.
                    escritor.println(String.join(";", ExportadorCsv.comoTexto(fila.getNumeroFactura()),
                            ExportadorCsv.comoTexto(fila.getTimbrado()),
                            fila.getFecha(), ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.comoTexto(fila.getClienteDocumento()), ExportadorCsv.limpiar(fila.getTotal()),
                            fila.getNumeroNotaAnulacion() == null ? "" : fila.getNumeroNotaAnulacion(),
                            fila.getFechaAnulacion(), ExportadorCsv.comilla(fila.getMotivo()),
                            ExportadorCsv.comilla(fila.getAnuladoPorNombre())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosFacturasAnuladas(mes);
            servicioInformes.auditarExportacionCsvFacturasAnuladas(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private void mostrarIncobrables(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, usuario.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarIncobrables(conexion);
            request.setAttribute("filas", paginarReporte(request, filas));
            request.setAttribute("totales", servicioReportes.calcularTotalesIncobrables(filas));
        }
        reenviar(request, response, "incobrables");
    }

    /** Bloque C, tanda 1 (pedido de Joel): "Informe PDF" de Incobrables -- mismo mecanismo que
     * Morosidad/Cartera. */
    private void generarInformeIncobrablesPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarIncobrables(conexion);
            py.com.capitalcredito.sgcpc.dto.TotalesIncobrables totales = servicioReportes.calcularTotalesIncobrables(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosIncobrables(Reloj.hoy());
            byte[] pdf = servicioInformes.generarInformeIncobrablesPdf(conexion, filas, totales, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_incobrables.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de incobrables.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque C, tanda 1 (pedido de Joel): CSV de Incobrables -- mismo criterio E-7 que Morosidad. */
    private void exportarIncobrablesCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<FilaMora> filas = servicioMora.listarIncobrables(conexion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "incobrables.csv")) {
                escritor.println("Operación;Cliente;Teléfono;C.I.;Días atraso;"
                        + "Monto exigible;Saldo");
                for (FilaMora fila : filas) {
                    List<String> columnas = new java.util.ArrayList<>(java.util.Arrays.asList(
                            ExportadorCsv.comilla(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.comoTexto(fila.getClienteTelefono()),
                            ExportadorCsv.comoTexto(fila.getClienteCedula())));
                    columnas.add(String.valueOf(fila.getDiasAtraso()));
                    columnas.add(fila.getMontoExigibleCrudo().toPlainString());
                    columnas.add(fila.getSaldoCrudo().toPlainString());
                    escritor.println(String.join(";", columnas));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosIncobrables(Reloj.hoy());
            servicioInformes.auditarExportacionCsvIncobrables(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar incobrables a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private void mostrarAuditoria(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta);
        Long idUsuario = idDeTexto(request.getParameter("idUsuario"));
        String tipoEntidad = vacioComoNulo(request.getParameter("tipoEntidad"));
        String operacion = vacioComoNulo(request.getParameter("operacion"));
        request.setAttribute("desde", desde);
        request.setAttribute("hasta", hasta);
        // B211: el rango EFECTIVO en texto, para el renglón de abajo del título
        // (plantilla/rango_aplicado_reporte.jsp). Va acá y no en la vista porque en la
        // primera carga no hay ningún parámetro: el valor por defecto lo resolvió este método.
        request.setAttribute("rangoAplicado", ServicioReportes.textoRangoAplicado(desde, hasta));
        request.setAttribute("idUsuario", idUsuario);
        request.setAttribute("tipoEntidad", tipoEntidad);
        request.setAttribute("operacion", operacion);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // RF40/F1-F2 (pedido de Joel, 13/10/2026): usuarios y entidades reales para los
            // <select> del formulario -- antes "idUsuario" se leía pero no había ningún campo en
            // la pantalla para escribirlo, y "Entidad" era texto libre.
            request.setAttribute("usuarios", servicioUsuarios.listar(conexion));
            request.setAttribute("entidades", servicioReportes.listarEntidadesAuditoria(conexion));
            // B205 (15/11/2026, ítem 1 del informe de UX de Informes): la pantalla PAGINA, con el
            // total real del rango -- antes se quedaba con las primeras 500 filas y, como el rango
            // por defecto es un solo día y ese día tenía 2.226 registros, abría truncada avisando
            // "acote el filtro" con el filtro ya en un día: de la fila 501 en adelante no se
            // llegaba. Acá la paginación va en SQL (LIMIT/OFFSET) y no con el recortarPagina en
            // Java de las otras cinco pantallas, porque cortar en Java obliga a traer las 18.381
            // filas a memoria -- justo lo que el tope evitaba.
            int cantidadTotal = servicioReportes.contarAuditoria(conexion, desde, hasta, idUsuario, tipoEntidad, operacion);
            int totalPaginas = py.com.capitalcredito.sgcpc.servicios.ServicioReportes
                    .totalPaginasAuditoria(cantidadTotal);
            int paginaAuditoria = paginaPedida(request.getParameter("pagina"), totalPaginas);
            request.setAttribute("filas", servicioReportes.listarAuditoriaPagina(conexion, desde, hasta,
                    idUsuario, tipoEntidad, operacion, paginaAuditoria));
            request.setAttribute("pagina", Integer.valueOf(paginaAuditoria));
            request.setAttribute("totalPaginas", Integer.valueOf(totalPaginas));
            request.setAttribute("totalFilas", Integer.valueOf(cantidadTotal));
            // La URL base de plantilla/paginacion.jsp lleva TODOS los filtros, así que cambiar de
            // página no pierde ninguno (la lección de B147/B163/B170/B175). Se rearma desde los
            // parámetros del pedido, igual que B191 en Caja: lo que llegó es lo que viaja.
            request.setAttribute("paginacionUrlBase", urlPaginacionAuditoria(request));
            // E-15 (pedido de Joel, C-3b): los totales por operación van sobre TODO el período
            // filtrado (GROUP BY aparte), no sobre las filas del detalle (que el límite puede
            // haber cortado).
            request.setAttribute("tituloTotales", servicioInformes.construirTituloTotalesAuditoria(cantidadTotal));
            request.setAttribute("totalesPorOperacion",
                    servicioReportes.contarTotalesPorOperacion(conexion, desde, hasta, idUsuario, tipoEntidad, operacion));
        }
        reenviar(request, response, "auditoria");
    }

    /** B210: las filas por página de los reportes -- las 20 de todo el sistema. */
    private static final int FILAS_POR_PAGINA_REPORTE = 20;

    /**
     * B210 (15/11/2026, ítems 2+3 del informe de UX de Informes): pagina un listado de reporte y
     * deja en el request todo lo que {@code plantilla/paginacion.jsp} necesita, más el
     * {@code totalFilas} para el contador del título.
     *
     * <p><b>Es uno para los dieciséis</b>, y por eso no recibe ni la ruta ni los filtros: la URL
     * base sale de {@code getRequestURI()} y de los parámetros del propio pedido. Así un filtro
     * nuevo en cualquier reporte viaja solo al cambiar de página, sin que nadie se acuerde de
     * agregarlo acá — que es justo el olvido que B147/B163/B170/B175 fueron corrigiendo pantalla
     * por pantalla.</p>
     *
     * <p>El recorte va <b>en Java</b>, como en las otras cinco pantallas (B175/B183/B191/B197):
     * acá los listados son de 190-292 filas (medido el 15/11/2026) y traerlas para cortar no
     * cuesta nada. Auditoría es la excepción y pagina en SQL, porque son 18.381 (ver B205).</p>
     *
     * <p>{@code totalFilas} es el total del listado COMPLETO, no el de la página: contar las
     * dibujadas diría "20" en cada página, la trampa que B191 ya había encontrado en Caja.</p>
     */
    private <T> java.util.List<T> paginarReporte(HttpServletRequest request, java.util.List<T> filas) {
        int total = filas.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) FILAS_POR_PAGINA_REPORTE));
        int pagina = paginaPedida(request.getParameter("pagina"), totalPaginas);
        request.setAttribute("pagina", Integer.valueOf(pagina));
        request.setAttribute("totalPaginas", Integer.valueOf(totalPaginas));
        request.setAttribute("totalFilas", Integer.valueOf(total));
        request.setAttribute("paginacionUrlBase", urlPaginacionReporte(request));
        // Los enlaces navegan de verdad: ningún reporte tiene búsqueda en vivo, y listado-vivo.js
        // le pega "pagina=N" fijo al pedido que arma desde el formulario (misma razón que B183).
        request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
        int desde = (pagina - 1) * FILAS_POR_PAGINA_REPORTE;
        return filas.subList(desde, Math.min(total, desde + FILAS_POR_PAGINA_REPORTE));
    }

    /**
     * B210: la URL base de la paginación de cualquier reporte -- la ruta del pedido con todos sus
     * parámetros menos "pagina", y terminada en "?" o "&" (el contrato de
     * plantilla/paginacion.jsp).
     */
    private String urlPaginacionReporte(HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        java.util.Enumeration<String> nombres = request.getParameterNames();
        while (nombres.hasMoreElements()) {
            String nombre = nombres.nextElement();
            String valor = request.getParameter(nombre);
            if ("pagina".equals(nombre) || valor == null || valor.trim().isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            try {
                query.append(java.net.URLEncoder.encode(nombre, "UTF-8")).append('=')
                        .append(java.net.URLEncoder.encode(valor, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                // UTF-8 siempre esta en la JVM; nunca deberia pasar.
            }
        }
        return request.getRequestURI() + "?" + (query.length() == 0 ? "" : query + "&");
    }

    /**
     * B205: la página pedida, acotada a lo que existe -- misma forma que en B175/B183/B191/B197.
     * Fuera de rango, o basura, cae en 1 o en la última.
     */
    private int paginaPedida(String texto, int totalPaginas) {
        int pagina = 1;
        if (texto != null && !texto.trim().isEmpty()) {
            try {
                pagina = Integer.parseInt(texto.trim());
            } catch (NumberFormatException error) {
                pagina = 1;
            }
        }
        if (pagina < 1) {
            pagina = 1;
        }
        return Math.min(pagina, Math.max(1, totalPaginas));
    }

    /**
     * B205: la URL base de la paginación de Auditoría -- conserva los cinco filtros de la pantalla
     * y termina en "?" o "&" (el contrato de plantilla/paginacion.jsp). Misma forma que
     * urlPaginacionCaja de B191: se rearma desde los parámetros del pedido, así un filtro nuevo
     * no se olvida acá.
     */
    private String urlPaginacionAuditoria(HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        for (String nombre : new String[] {"desde", "hasta", "idUsuario", "tipoEntidad", "operacion"}) {
            String valor = request.getParameter(nombre);
            if (valor == null || valor.trim().isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            try {
                query.append(nombre).append('=').append(java.net.URLEncoder.encode(valor, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                // UTF-8 siempre esta en la JVM; nunca deberia pasar.
            }
        }
        return request.getContextPath() + "/reportes/auditoria?" + (query.length() == 0 ? "" : query + "&");
    }

    /** RF40/F1 (pedido de Joel, 13/10/2026): nombre completo del usuario filtrado, para que el
     * PDF/CSV lo mencionen en "Filtros aplicados" -- null si no se filtró por usuario. */
    private String resolverNombreUsuarioAuditoria(Connection conexion, Long idUsuario) throws SQLException {
        if (idUsuario == null) {
            return null;
        }
        Usuario usuario = servicioUsuarios.buscarPorId(conexion, idUsuario);
        return usuario == null ? null : usuario.getNombres() + " " + usuario.getApellidos();
    }

    /** Bloque C, tanda 3 (pedido de Joel): "Informe PDF" de Auditoría -- mismo mecanismo que los
     * informes anteriores, con el límite de filas aplicado (mismo que la pantalla). */
    private void generarInformeAuditoriaPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta);
        Long idUsuario = idDeTexto(request.getParameter("idUsuario"));
        String tipoEntidad = vacioComoNulo(request.getParameter("tipoEntidad"));
        String operacion = vacioComoNulo(request.getParameter("operacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // B205: el tope queda SOLO acá. Un informe de 18.381 filas no lo quiere nadie y es el
            // que puede tumbar la memoria de Jasper; el aviso del PDF ahora además dice de dónde
            // sacar el resto (el CSV, que ya no tiene tope).
            List<py.com.capitalcredito.sgcpc.dto.FilaAuditoria> filas =
                    servicioReportes.listarAuditoria(conexion, desde, hasta, idUsuario, tipoEntidad, operacion,
                            py.com.capitalcredito.sgcpc.servicios.ServicioReportes.LIMITE_AUDITORIA_PDF, 0);
            int cantidadTotal = servicioReportes.contarAuditoria(conexion, desde, hasta, idUsuario, tipoEntidad, operacion);
            String avisoLimite = servicioInformes.construirAvisoLimiteAuditoria(filas.size(), cantidadTotal,
                    py.com.capitalcredito.sgcpc.servicios.ServicioReportes.LIMITE_AUDITORIA_PDF);
            java.util.Map<String, Integer> totalesPorOperacion =
                    servicioReportes.contarTotalesPorOperacion(conexion, desde, hasta, idUsuario, tipoEntidad, operacion);
            String nombreUsuarioFiltro = resolverNombreUsuarioAuditoria(conexion, idUsuario);
            String textoFiltros = servicioInformes.construirTextoFiltrosAuditoria(desde, hasta, nombreUsuarioFiltro,
                    tipoEntidad, operacion);
            byte[] pdf = servicioInformes.generarInformeAuditoriaPdf(conexion, filas, totalesPorOperacion, cantidadTotal,
                    textoFiltros, avisoLimite, solicitante.getIdUsuario(),
                    solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_auditoria.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de auditoría.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    private void exportarAuditoriaCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        LocalDate hasta = parsearFecha(request.getParameter("hasta"), Reloj.hoy());
        LocalDate desde = parsearFecha(request.getParameter("desde"), hasta);
        Long idUsuario = idDeTexto(request.getParameter("idUsuario"));
        String tipoEntidad = vacioComoNulo(request.getParameter("tipoEntidad"));
        String operacion = vacioComoNulo(request.getParameter("operacion"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<py.com.capitalcredito.sgcpc.dto.FilaAuditoria> filas =
                    // B205: el CSV es el camino para llevarse TODO, así que acá no hay tope.
                    servicioReportes.listarAuditoriaCompleta(conexion, desde, hasta, idUsuario, tipoEntidad, operacion);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "auditoria_" + desde + "_" + hasta + ".csv")) {
                // RF40/F3 (pedido de Joel, 13/10/2026): 3 columnas nuevas (Usuario escrito, IP,
                // Navegador) -- mismo texto legible que ya muestra la pantalla (FilaAuditoria.
                // getDireccionIpLegible/getNavegadorResumido).
                escritor.println("Fecha/hora;Usuario;Usuario escrito;Operación;Entidad;Id;IP;Navegador;Detalle");
                for (py.com.capitalcredito.sgcpc.dto.FilaAuditoria fila : filas) {
                    escritor.println(String.join(";", fila.getFechaHora(), ExportadorCsv.comilla(fila.getUsuario()),
                            ExportadorCsv.comilla(fila.getUsuarioEscrito() == null ? "" : fila.getUsuarioEscrito()),
                            ExportadorCsv.comilla(fila.getOperacion()), ExportadorCsv.comilla(fila.getTipoEntidad()),
                            ExportadorCsv.comoTexto(String.valueOf(fila.getIdEntidad())),
                            ExportadorCsv.comilla(fila.getDireccionIpLegible()),
                            ExportadorCsv.comilla(fila.getNavegadorResumido()),
                            ExportadorCsv.comilla(fila.getDetalle() == null ? "" : fila.getDetalle())));
                }
            }
            String nombreUsuarioFiltro = resolverNombreUsuarioAuditoria(conexion, idUsuario);
            String textoFiltros = servicioInformes.construirTextoFiltrosAuditoria(desde, hasta, nombreUsuarioFiltro,
                    tipoEntidad, operacion);
            servicioInformes.auditarExportacionCsvAuditoria(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        }
    }

    private void mostrarEstadoCuentaBusqueda(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String textoBusqueda = request.getParameter("q");
        request.setAttribute("textoBusqueda", textoBusqueda);
        if (textoBusqueda != null && !textoBusqueda.trim().isEmpty()) {
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                PrestamoCredito prestamo = servicioReportes.buscarPrestamoParaEstadoCuenta(conexion, textoBusqueda);
                response.sendRedirect(request.getContextPath() + "/reportes/estado-cuenta/" + prestamo.getIdPrestamo());
                return;
            } catch (NegocioException error) {
                request.setAttribute("mensajeError", error.getMessage());
            } catch (SQLException error) {
                getServletContext().log("Error de base de datos al buscar el estado de cuenta.", error);
                request.setAttribute("mensajeError", "No se pudo completar la búsqueda.");
            }
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/vistas/reportes/estado_cuenta_busqueda.jsp");
        dispatcher.forward(request, response);
    }

    private void mostrarEstadoCuentaFicha(HttpServletRequest request, HttpServletResponse response, String idTexto)
            throws ServletException, IOException {
        Long idPrestamo = idDeTexto(idTexto);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            py.com.capitalcredito.sgcpc.servicios.ServicioClientes servicioClientes =
                    new py.com.capitalcredito.sgcpc.servicios.ServicioClientes();
            request.setAttribute("prestamo", prestamo);
            request.setAttribute("cliente", servicioClientes.buscarPorId(conexion, solicitud.getIdCliente()));
            request.setAttribute("historialCobros", servicioReportes.obtenerHistorialCobros(conexion, idPrestamo));
            request.setAttribute("cuotasPendientes", servicioReportes.listarPlanDePagos(conexion, idPrestamo));
            request.setAttribute("totales", servicioReportes.calcularTotalesEstadoCuenta(conexion, prestamo));

            List<py.com.capitalcredito.sgcpc.modelos.GestionCobranza> gestiones =
                    servicioReportes.obtenerGestionesDelPrestamo(conexion, idPrestamo);
            request.setAttribute("gestiones", gestiones);
            // Enriquecimiento (Contactado, Registrado por): mismo patrón que /reportes/gestiones --
            // la fila cruda solo trae los ids, la ficha necesita nombres legibles.
            java.util.Map<Long, String> nombrePorPersona = new java.util.HashMap<>();
            java.util.Map<Long, String> nombrePorUsuario = new java.util.HashMap<>();
            py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios servicioUsuarios =
                    new py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios();
            for (py.com.capitalcredito.sgcpc.modelos.GestionCobranza gestion : gestiones) {
                if (!nombrePorPersona.containsKey(gestion.getIdPersonaContactada())) {
                    py.com.capitalcredito.sgcpc.modelos.Persona persona =
                            servicioClientes.buscarPorId(conexion, gestion.getIdPersonaContactada());
                    nombrePorPersona.put(gestion.getIdPersonaContactada(),
                            persona == null ? "?" : persona.getNombres() + " " + persona.getApellidos());
                }
                if (!nombrePorUsuario.containsKey(gestion.getRegistradoPor())) {
                    py.com.capitalcredito.sgcpc.modelos.Usuario registrador =
                            servicioUsuarios.buscarPorId(conexion, gestion.getRegistradoPor());
                    nombrePorUsuario.put(gestion.getRegistradoPor(),
                            registrador == null ? "?" : registrador.getNombres() + " " + registrador.getApellidos());
                }
            }
            request.setAttribute("nombrePorPersonaGestion", nombrePorPersona);
            request.setAttribute("nombrePorUsuarioGestion", nombrePorUsuario);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el estado de cuenta del préstamo " + idPrestamo + ".",
                    error);
            request.setAttribute("mensajeError", "No se pudo leer el estado de cuenta.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/vistas/reportes/estado_cuenta_ficha.jsp");
        dispatcher.forward(request, response);
    }

    /** RF34 (auditoría de impresión, pedido de Joel): el estado de cuenta ya no se imprime con
     * window.print() -- mismo PDF que la ficha, con el encabezado común (logo/empresa). */
    private void generarInformeEstadoCuentaPdf(HttpServletRequest request, HttpServletResponse response, String idTexto)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        Long idPrestamo = idDeTexto(idTexto);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            py.com.capitalcredito.sgcpc.servicios.ServicioClientes servicioClientes =
                    new py.com.capitalcredito.sgcpc.servicios.ServicioClientes();
            py.com.capitalcredito.sgcpc.modelos.Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            py.com.capitalcredito.sgcpc.dto.TotalesEstadoCuenta totales =
                    servicioReportes.calcularTotalesEstadoCuenta(conexion, prestamo);
            String avisoPlan = servicioReportes.construirAvisoPlanEstadoCuenta(prestamo.getEstadoPrestamo());
            byte[] pdf = servicioInformes.generarEstadoCuentaPdf(conexion, prestamo, cliente, totales, avisoPlan,
                    servicioReportes.listarPlanDePagos(conexion, idPrestamo),
                    servicioReportes.obtenerHistorialCobros(conexion, idPrestamo),
                    servicioReportes.listarGestionesEstadoCuenta(conexion, idPrestamo), solicitante.getIdUsuario(),
                    solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition",
                    "inline; filename=\"estado_cuenta_" + prestamo.getIdentificadorOperacion() + ".pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe del estado de cuenta " + idPrestamo + ".",
                    error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** RF34 (auditoría de impresión, pedido de Joel): CSV del estado de cuenta, mismas 3
     * secciones que el PDF (datos del préstamo, plan de pagos, historial de cobros, gestiones). */
    private void exportarEstadoCuentaCsv(HttpServletRequest request, HttpServletResponse response, String idTexto)
            throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        Long idPrestamo = idDeTexto(idTexto);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            py.com.capitalcredito.sgcpc.servicios.ServicioClientes servicioClientes =
                    new py.com.capitalcredito.sgcpc.servicios.ServicioClientes();
            py.com.capitalcredito.sgcpc.modelos.Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            py.com.capitalcredito.sgcpc.dto.TotalesEstadoCuenta totales =
                    servicioReportes.calcularTotalesEstadoCuenta(conexion, prestamo);
            List<py.com.capitalcredito.sgcpc.dto.FilaCuotaPendiente> cuotas =
                    servicioReportes.listarPlanDePagos(conexion, idPrestamo);
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza> historial =
                    servicioReportes.obtenerHistorialCobros(conexion, idPrestamo);
            List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> gestiones =
                    servicioReportes.listarGestionesEstadoCuenta(conexion, idPrestamo);
            try (PrintWriter escritor = ExportadorCsv.abrir(response,
                    "estado_cuenta_" + prestamo.getIdentificadorOperacion() + ".csv")) {
                escritor.println("Operación;" + ExportadorCsv.comilla(prestamo.getIdentificadorOperacion()));
                escritor.println("Estado;" + ExportadorCsv.comilla(prestamo.getEstadoPrestamo().getEtiqueta()));
                escritor.println("Cliente;" + ExportadorCsv.comilla(cliente.getNombres() + " " + cliente.getApellidos()));
                escritor.println("C.I.;" + ExportadorCsv.comoTexto(cliente.getCedulaIdentidad()));
                escritor.println("Saldo de capital;" + ExportadorCsv.limpiar(totales.getSaldoCapital()));
                escritor.println("Total pagado;" + ExportadorCsv.limpiar(totales.getTotalPagado()));
                escritor.println("Monto exigible hoy;"
                        + ExportadorCsv.limpiar(totales.getMontoExigibleHoy() == null ? "—" : totales.getMontoExigibleHoy()));
                escritor.println();
                escritor.println("Plan de pagos");
                escritor.println("Cuota;Vencimiento;Capital;Interés;IVA;Total;Estado;Pagada el;Recibo");
                for (py.com.capitalcredito.sgcpc.dto.FilaCuotaPendiente cuota : cuotas) {
                    escritor.println(String.join(";", String.valueOf(cuota.getNumeroCuota()), cuota.getFechaVencimiento(),
                            ExportadorCsv.limpiar(cuota.getCapital()), ExportadorCsv.limpiar(cuota.getInteres()),
                            ExportadorCsv.limpiar(cuota.getIva()), ExportadorCsv.limpiar(cuota.getTotal()),
                            ExportadorCsv.comilla(cuota.getEstado()),
                            cuota.getFechaPago() == null ? "" : cuota.getFechaPago(),
                            ExportadorCsv.comoTexto(cuota.getNumeroRecibo())));
                }
                escritor.println();
                escritor.println("Historial de cobros");
                escritor.println("Fecha/hora;Tipo;Recibo;Monto;Revertido");
                for (py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza fila : historial) {
                    escritor.println(String.join(";", fila.getFechaHora(), ExportadorCsv.comilla(fila.getEtiquetaTipo()),
                            ExportadorCsv.comoTexto(fila.getNumeroRecibo()), ExportadorCsv.limpiar(fila.getMontoAplicado()),
                            fila.isRevertido() ? "Sí" : ""));
                }
                escritor.println();
                escritor.println("Gestiones de cobranza");
                escritor.println("Fecha/hora;Contactado;Tipo;Resultado;Promesa;Cumplida;Registrado por");
                for (py.com.capitalcredito.sgcpc.dto.FilaGestionReporte gestion : gestiones) {
                    escritor.println(String.join(";", gestion.getFechaHora(),
                            ExportadorCsv.comilla(gestion.getContactadoNombre()), ExportadorCsv.comilla(gestion.getTipoEtiqueta()),
                            ExportadorCsv.comilla(gestion.getResultado()), ExportadorCsv.comilla(gestion.getPromesaTexto()),
                            gestion.getCumplidaTexto(), ExportadorCsv.comilla(gestion.getRegistradoPorNombre())));
                }
            }
            servicioInformes.auditarExportacionCsvEstadoCuenta(conexion, solicitante.getIdUsuario(), prestamo, cuotas.size(),
                    historial.size(), gestiones.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar el estado de cuenta " + idPrestamo + " a CSV.",
                    error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el estado de cuenta.");
        }
    }

    private void reenviar(HttpServletRequest request, HttpServletResponse response, String nombre)
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/vistas/reportes/" + nombre + ".jsp");
        dispatcher.forward(request, response);
    }

    private LocalDate parsearFecha(String texto, LocalDate porDefecto) {
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException error) {
            return porDefecto;
        }
    }

    private YearMonth parsearMes(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(texto);
        } catch (DateTimeParseException error) {
            return YearMonth.now();
        }
    }

    private String vacioComoNulo(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? null : texto;
    }

    private Long idDeTexto(String texto) {
        try {
            return (texto == null || texto.trim().isEmpty()) ? null : Long.parseLong(texto);
        } catch (NumberFormatException error) {
            return null;
        }
    }

}
