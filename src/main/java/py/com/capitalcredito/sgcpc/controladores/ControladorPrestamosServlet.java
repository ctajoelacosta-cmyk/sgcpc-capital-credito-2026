package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.dto.FilaInformePrestamo;
import py.com.capitalcredito.sgcpc.dto.FiltroPrestamos;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo;
import py.com.capitalcredito.sgcpc.dto.TotalesPrestamos;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.ExportadorCsv;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.PaginacionPantalla;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioReportes;

/**
 * B1 (Bloque 7/E10, pedido de Joel): listado de Préstamos, con filtros, PDF y CSV -- mismo
 * esqueleto que ControladorClientesServlet (D-135). Protegido en FiltroAutorizacion por
 * Permiso.VER_REPORTES (gerente TOTAL, oficial CONSULTA, cajera NINGUNO): a diferencia de
 * Clientes, acá no hace falta una restricción fina aparte para el PDF/CSV -- Joel pidió el
 * mismo acceso (gerente y oficial) para toda la pantalla, y las 3 rutas de este controlador son
 * GET, para las que CONSULTA ya alcanza.
 */
public class ControladorPrestamosServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/prestamos/lista.jsp";

    private final ServicioReportes servicioReportes = new ServicioReportes();
    private final ServicioInformes servicioInformes = new ServicioInformes();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response);
        } else if ("/informe".equals(pathInfo)) {
            generarInformePdf(request, response);
        } else if ("/exportar".equals(pathInfo)) {
            exportarCsv(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        FiltroPrestamos filtro = construirFiltroDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<FilaInformePrestamo> filas = servicioReportes.listarInformePrestamos(conexion, filtro);
            // B241 (16/11/2026, hallazgo de Joel): la pantalla traia los 347 prestamos de una sola
            // vez. El orden de estas tres lineas NO es casual y es lo unico delicado del cambio:
            // los totales y el resumen se calculan sobre la lista COMPLETA y la pagina se corta
            // DESPUES. Al revés, cada pagina diria "20 prestamos" y sus totales serian los de la
            // pagina -- la trampa que B191 ya encontro en Caja y que B210 arreglo en los reportes.
            // El PDF y el CSV no se tocan: cada uno vuelve a consultar con el mismo filtro, asi
            // que siguen exportando todo lo filtrado y no la pagina.
            request.setAttribute("totales", servicioReportes.calcularTotalesPrestamos(filas));
            request.setAttribute("resumenPorEstado", servicioReportes.calcularResumenPorEstadoPrestamos(filas));
            request.setAttribute("filas", PaginacionPantalla.paginar(request, filas));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al listar préstamos.", error);
            request.setAttribute("mensajeError", "No se pudo generar el listado.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /** Bloque 7/E10 (B1, pedido de Joel): "Informe PDF" -- mismos filtros del listado, hoja
     * horizontal, con GeneradorInformesPdf (D-135). Se abre en pestaña nueva ("inline"). */
    private void generarInformePdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        FiltroPrestamos filtro = construirFiltroDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<FilaInformePrestamo> filas = servicioReportes.listarInformePrestamos(conexion, filtro);
            TotalesPrestamos totales = servicioReportes.calcularTotalesPrestamos(filas);
            List<ResumenEstadoPrestamo> resumenPorEstado = servicioReportes.calcularResumenPorEstadoPrestamos(filas);
            String textoFiltros = servicioInformes.construirTextoFiltrosPrestamos(filtro,
                    etiquetaFiltroEstado(filtro.getEstado()));
            byte[] pdf = servicioInformes.generarInformePrestamosPdf(conexion, filas, totales, resumenPorEstado,
                    textoFiltros, solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_prestamos.pdf\"");
            try (OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de préstamos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque 7/E10 (B1, pedido de Joel): CSV con las mismas columnas/filtros que el informe PDF
     * -- operación y C.I. van marcadas como texto (ExportadorCsv.comoTexto, E-7), monto y saldo
     * siguen como número plano, para que se puedan sumar en Excel. */
    private void exportarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        FiltroPrestamos filtro = construirFiltroDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<FilaInformePrestamo> filas = servicioReportes.listarInformePrestamos(conexion, filtro);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "prestamos.csv")) {
                escritor.println("Operación;Cliente;C.I.;Monto;Plazo (meses);Fecha de desembolso;Estado;Saldo;"
                        + "Cuotas pagadas/total");
                for (FilaInformePrestamo fila : filas) {
                    // E-9 (pedido de Joel): sin desembolso no hay deuda -- Saldo y Cuotas pagadas/
                    // total van vacíos (nunca "0" ni "—": una celda vacía no se confunde con un
                    // importe real en una planilla que alguien puede sumar). E-8: Cuotas pagadas/
                    // total marcada como texto -- Excel leía "3/3" como fecha (03-mar).
                    escritor.println(String.join(";",
                            ExportadorCsv.comoTexto(fila.getIdentificadorOperacion()),
                            ExportadorCsv.comilla(fila.getClienteNombre()),
                            ExportadorCsv.comoTexto(fila.getClienteCedula()),
                            fila.getMontoAprobado().toPlainString(),
                            String.valueOf(fila.getPlazoMeses()),
                            fila.getFechaDesembolso(),
                            fila.getEstado(),
                            fila.getSaldo() == null ? "" : fila.getSaldo().toPlainString(),
                            fila.getSaldo() == null ? "" : ExportadorCsv.comoTexto(fila.getCuotasTexto())));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosPrestamos(filtro,
                    etiquetaFiltroEstado(filtro.getEstado()));
            servicioInformes.auditarExportacionCsvPrestamos(conexion, solicitante.getIdUsuario(), textoFiltros, filas.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar préstamos a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    /** Mismos parámetros del formulario de filtros de /prestamos -- reusado por la lista, el
     * informe PDF y el CSV (mismo criterio que ControladorClientesServlet.construirFiltroDesdeRequest). */
    private FiltroPrestamos construirFiltroDesdeRequest(HttpServletRequest request) {
        FiltroPrestamos filtro = new FiltroPrestamos();
        filtro.setTexto(vacioComoNulo(request.getParameter("texto")));
        filtro.setEstado(vacioComoNulo(request.getParameter("estado")));
        filtro.setDesembolsoDesde(parsearFecha(request.getParameter("desembolsoDesde")));
        filtro.setDesembolsoHasta(parsearFecha(request.getParameter("desembolsoHasta")));
        return filtro;
    }

    private String etiquetaFiltroEstado(String estado) {
        if (estado == null) {
            return "Todos los estados";
        }
        try {
            return EstadoPrestamo.valueOf(estado).getEtiqueta();
        } catch (IllegalArgumentException error) {
            return "Todos los estados";
        }
    }

    private String vacioComoNulo(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? null : texto;
    }

    private LocalDate parsearFecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException error) {
            return null;
        }
    }
}
