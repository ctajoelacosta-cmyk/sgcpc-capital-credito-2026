package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dto.FilaComparacionPlazo;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioCotizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;

/**
 * P13, cotizador (RF-33/PO-01 — Doc. 10 no tiene sección para P13, la
 * pantalla se agregó solo en Doc. 00 §12). GET muestra el formulario, POST
 * calcula y muestra el resultado en la misma pantalla; nunca persiste nada
 * (RF-33: "no guarda registros"). C-4.1 (pedido de Joel): POST /cotizador/informe
 * recalcula con los mismos datos (mismo ServicioCotizacion.cotizar) y devuelve el
 * PDF -- tampoco persiste la cotización en sí, solo audita la emisión del PDF.
 */
public class ControladorCotizadorServlet extends HttpServlet {

    private static final String VISTA = "/WEB-INF/vistas/cotizador/formulario.jsp";

    private final ServicioCotizacion servicioCotizacion = new ServicioCotizacion();
    private final ServicioInformes servicioInformes = new ServicioInformes();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        mostrarFormulario(request, response, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/informe".equals(request.getPathInfo())) {
            generarInformePdf(request, response);
            return;
        }
        try {
            BigDecimal monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
            int plazoMeses = ConversorNumeros.aEntero(request.getParameter("plazoMeses"));
            LocalDate fechaPrimerVencimiento = LocalDate.parse(request.getParameter("fechaPrimerVencimiento"));

            request.setAttribute("montoIngresado", request.getParameter("monto"));
            request.setAttribute("plazoIngresado", request.getParameter("plazoMeses"));
            request.setAttribute("fechaIngresada", request.getParameter("fechaPrimerVencimiento"));
            request.setAttribute("nombreIngresado", request.getParameter("nombreInteresado"));
            request.setAttribute("cedulaIngresada", request.getParameter("cedulaInteresado"));

            request.setAttribute("resultado", servicioCotizacion.cotizar(monto, plazoMeses, fechaPrimerVencimiento));
            mostrarFormulario(request, response, null, null);
        } catch (NegocioException error) {
            // Tanda 2 (14/10/2026, pedido de Joel): un monto/plazo fuera de rango es una
            // validación del usuario, no un error del sistema -- advertencia ("!"), no X roja.
            // El mensaje ya viene en 2 oraciones ("...Gs. Ajuste..."), así que
            // dividirEnTituloYDetalle (pie.jsp) ya separa título/detalle solo, sin prefijo.
            request.setAttribute("mensajeAdvertencia", error.getMessage());
            mostrarFormulario(request, response, null, null);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cotizar.", error);
            mostrarFormulario(request, response, null, "No se pudo calcular la cotización. Intente nuevamente.");
        }
    }

    /** C-4.1 (pedido de Joel): recalcula en el servidor con los mismos datos del formulario (nunca
     * confía en números ya mostrados en el navegador) y arma el PDF. Si algo falla (datos fuera de
     * límite, error de base), vuelve al formulario con el error -- mismo patrón que el POST normal. */
    private void generarInformePdf(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String nombreInteresado = request.getParameter("nombreInteresado");
        String cedulaInteresado = request.getParameter("cedulaInteresado");
        try {
            BigDecimal monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
            int plazoMeses = ConversorNumeros.aEntero(request.getParameter("plazoMeses"));
            LocalDate fechaPrimerVencimiento = LocalDate.parse(request.getParameter("fechaPrimerVencimiento"));
            ResultadoCotizacion resultado = servicioCotizacion.cotizar(monto, plazoMeses, fechaPrimerVencimiento);
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                byte[] pdf = servicioInformes.generarInformeCotizacionPdf(conexion, monto, plazoMeses,
                        nombreInteresado, cedulaInteresado, resultado, solicitante.getIdUsuario(),
                        solicitante.getNombres() + " " + solicitante.getApellidos());
                response.setContentType("application/pdf");
                response.setContentLength(pdf.length);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Content-Disposition", "inline; filename=\"cotizacion.pdf\"");
                try (java.io.OutputStream salida = response.getOutputStream()) {
                    salida.write(pdf);
                }
            }
        } catch (NegocioException error) {
            request.setAttribute("montoIngresado", request.getParameter("monto"));
            request.setAttribute("plazoIngresado", request.getParameter("plazoMeses"));
            request.setAttribute("fechaIngresada", request.getParameter("fechaPrimerVencimiento"));
            request.setAttribute("nombreIngresado", nombreInteresado);
            request.setAttribute("cedulaIngresada", cedulaInteresado);
            // Tanda 2 (14/10/2026, pedido de Joel): mismo criterio que el POST normal de arriba.
            request.setAttribute("mensajeAdvertencia", error.getMessage());
            mostrarFormulario(request, response, null, null);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el PDF de la cotización.", error);
            request.setAttribute("montoIngresado", request.getParameter("monto"));
            request.setAttribute("plazoIngresado", request.getParameter("plazoMeses"));
            request.setAttribute("fechaIngresada", request.getParameter("fechaPrimerVencimiento"));
            request.setAttribute("nombreIngresado", nombreInteresado);
            request.setAttribute("cedulaIngresada", cedulaInteresado);
            mostrarFormulario(request, response, null, "No se pudo generar el PDF. Intente nuevamente.");
        }
    }

    private void mostrarFormulario(HttpServletRequest request, HttpServletResponse response, String mensajeExito,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        request.setAttribute("fechaSugerida", Reloj.hoy().plusMonths(1).toString());
        // RF01-C (pedido de Joel): muestra los límites vigentes de monto/plazo debajo de cada
        // campo (parámetros, nunca hardcodeados). Es solo informativo -- si falla la lectura, el
        // formulario se muestra igual sin el dato, sin tapar un mensajeError ya existente.
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("montoMinimo", servicioParametros.obtenerValorDecimal(conexion, "credito.monto_minimo"));
            request.setAttribute("montoMaximo", servicioParametros.obtenerValorDecimal(conexion, "credito.monto_maximo"));
            int plazoMinimoValor = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_minimo");
            int plazoMaximoValor = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_maximo");
            request.setAttribute("plazoMinimo", plazoMinimoValor);
            request.setAttribute("plazoMaximo", plazoMaximoValor);

            // RF01-O (pedido de Joel): mismo rango que ServicioFormalizacion, contado desde hoy.
            // La fecha sugerida por defecto se reemplaza por el mínimo del rango, para que caiga
            // siempre dentro de lo que el servidor va a aceptar.
            ServicioFormalizacion.RangoFechas rango = servicioFormalizacion.calcularRangoPrimerVencimiento(conexion,
                    Reloj.hoy());
            request.setAttribute("rangoFechas", rango);
            request.setAttribute("fechaSugerida", rango.getMinima().toString());

            // RF01-E (pedido de Joel): el texto completo de TEA en el glosario menciona el tope
            // legal vigente -- se resuelve aquí (el controlador), no dentro de GlosarioFinanciero.
            LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
            if (limiteVigente != null) {
                request.setAttribute("topeTeaVigente", limiteVigente.getTasaMaximaTea());
            }

            // "Comparar plazos" (pedido de Joel): solo si ya hay un resultado (cotización
            // exitosa) -- mismo monto y fecha, un ServicioCotizacion.cotizar() por plazo
            // candidato (6/12/18/24 recortados al rango vigente + el plazo actual si no está
            // entre esos cuatro). Ningún cálculo nuevo: es el mismo método que ya usa el
            // cotizador, llamado varias veces. Si falla, la página se muestra igual sin la tabla
            // (no tapa el resultado principal, que ya se calculó bien).
            ResultadoCotizacion resultadoActual = (ResultadoCotizacion) request.getAttribute("resultado");
            if (resultadoActual != null) {
                try {
                    BigDecimal montoComparacion =
                            ConversorNumeros.aBigDecimalGuaranies((String) request.getAttribute("montoIngresado"));
                    LocalDate fechaComparacion = LocalDate.parse((String) request.getAttribute("fechaIngresada"));
                    int plazoActual = resultadoActual.getPlan().size();
                    List<Integer> plazosComparacion = servicioCotizacion.construirPlazosComparacion(plazoMinimoValor,
                            plazoMaximoValor, plazoActual);
                    List<FilaComparacionPlazo> filasComparacion = new ArrayList<>();
                    for (int plazoCandidato : plazosComparacion) {
                        ResultadoCotizacion resultadoCandidato =
                                servicioCotizacion.cotizar(montoComparacion, plazoCandidato, fechaComparacion);
                        filasComparacion.add(new FilaComparacionPlazo(plazoCandidato,
                                resultadoCandidato.getPlan().get(0).getTotalProgramado(),
                                resultadoCandidato.getMontoTotalConIva(), plazoCandidato == plazoActual));
                    }
                    request.setAttribute("comparacionPlazos", filasComparacion);
                } catch (NegocioException | SQLException error) {
                    getServletContext().log("No se pudo armar la comparación de plazos.", error);
                }
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer los límites de monto/plazo/fecha.", error);
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA);
        dispatcher.forward(request, response);
    }
}
