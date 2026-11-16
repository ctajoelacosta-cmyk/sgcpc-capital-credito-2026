package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
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
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;
import py.com.capitalcredito.sgcpc.excepciones.MontoPrometidoFaltanteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.ResultadoGestionFaltanteException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.infraestructura.TokenUnaVez;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioMora;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * P14, seguimiento de mora (Doc. 00 §12: ControladorMoraServlet / ServicioMora). Protegido por
 * FiltroAutorizacion (REGISTRAR_GESTION_COBRANZA, TOTAL para oficial y gerente, NINGUNO para la
 * cajera -- decisión 4 del Bloque 4).
 */
public class ControladorMoraServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/mora/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/mora/_resultados.jsp";

    private final ServicioMora servicioMora = new ServicioMora();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String[] segmentos = pathInfo == null ? new String[0] : pathInfo.substring(1).split("/");

        if (segmentos.length == 2 && "gestion".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            procesarGestion(request, response, sesion, solicitante, idPrestamo);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarGestion(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, long idPrestamo) throws IOException {
        // Tanda 2 (14/10/2026, pedido de Joel, causa real del doble clic en pantalla): protección
        // EN EL SERVIDOR, sin tocar la base -- gestiones_cobranza no tiene (ni se le agrega acá)
        // identificador_confirmacion como pagos/desembolsos; un token de un solo uso por sesión
        // (TokenUnaVez), generado por fila al mostrar /mora.
        //
        // Tanda de usabilidad (19/10/2026, hallazgo de Joel, RF24 §10): si el token ya fue
        // consumido (doble clic real) o es inválido/no existe (dos pestañas, sesión vencida,
        // token manipulado), TokenUnaVez.consumir devuelve false en los 3 casos por igual --
        // indistinguibles desde acá sin agregar un segundo registro (cambio mínimo, REGLAS §13:
        // se prefiere una advertencia honesta que sirva para los 3, no un mecanismo nuevo).
        if (!TokenUnaVez.consumir(sesion, request.getParameter("tokenGestion"))) {
            MensajesFlash.guardarAdvertencia(sesion, "No se registró una gestión nueva. Si ya la había "
                    + "enviado, aparece en la columna Última gestión; si no, actualice la página e intente "
                    + "de nuevo.");
            response.sendRedirect(request.getContextPath() + "/mora");
            return;
        }
        try {
            long idPersonaContactada = Long.parseLong(request.getParameter("idPersonaContactada"));
            TipoGestionCobranza tipo = TipoGestionCobranza.valueOf(request.getParameter("tipo"));
            String resultado = request.getParameter("resultado");
            String excusa = request.getParameter("excusa");
            String fechaPromesaTexto = request.getParameter("fechaPromesa");
            LocalDate fechaPromesa = (fechaPromesaTexto == null || fechaPromesaTexto.trim().isEmpty()) ? null
                    : LocalDate.parse(fechaPromesaTexto);
            String montoTexto = request.getParameter("montoPrometido");
            BigDecimal montoPrometido = (montoTexto == null || montoTexto.trim().isEmpty()) ? null
                    : ConversorNumeros.aBigDecimalGuaranies(montoTexto);
            servicioMora.registrarGestion(idPrestamo, idPersonaContactada, tipo, resultado, excusa, fechaPromesa,
                    montoPrometido, solicitante.getIdUsuario());
            MensajesFlash.guardarExito(sesion, "Gestión registrada.");
        } catch (MontoPrometidoFaltanteException | ResultadoGestionFaltanteException error) {
            // RF24 (pedido de Joel, 12/10/2026): falta un dato, no un error -- advertencia (ícono
            // "!" y botón "Entendido"), mismo patrón que MotivoDiferenciaInsuficienteException (RF19).
            // Tanda 2 (14/10/2026): agregado ResultadoGestionFaltanteException ("Resultado"
            // obligatorio), mismo criterio.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException error) {
            MensajesFlash.guardarError(sesion, "Datos inválidos en el formulario de gestión.");
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al registrar una gestión del préstamo " + idPrestamo + ".",
                    error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/mora");
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): buscador
        // con el mismo mecanismo que Clientes/Cobranza (listado-vivo.js + PeticionAjax.esAjax).
        String textoBusqueda = request.getParameter("texto");
        request.setAttribute("textoBusqueda", textoBusqueda);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioMora.recalcularMorosidadDiaria(conexion, solicitante.getIdUsuario());
            List<py.com.capitalcredito.sgcpc.dto.FilaMora> filasFiltradas =
                    servicioMora.listarPrestamosEnMora(conexion, textoBusqueda);
            // B197 (15/11/2026): ordenar la lista COMPLETA y recien entonces cortar la pagina --
            // ordenar despues del corte daria un orden falso (B157). Las reglas del orden viven en
            // ServicioMora (puras, con lista blanca); el controlador pasa los parametros crudos,
            // que es la leccion de B167.
            List<py.com.capitalcredito.sgcpc.dto.FilaMora> filasOrdenadas = servicioMora.ordenarParaBandeja(
                    filasFiltradas, request.getParameter("orden"), request.getParameter("sentido"));
            int totalFilas = filasOrdenadas.size();
            int totalPaginas = Math.max(1, (int) Math.ceil(totalFilas / (double) FILAS_POR_PAGINA));
            int pagina = paginaPedida(request.getParameter("pagina"), totalPaginas);
            int desde = (pagina - 1) * FILAS_POR_PAGINA;
            int hasta = Math.min(totalFilas, desde + FILAS_POR_PAGINA);
            List<py.com.capitalcredito.sgcpc.dto.FilaMora> filas = filasOrdenadas.subList(desde, hasta);
            request.setAttribute("filas", filas);
            request.setAttribute("pagina", Integer.valueOf(pagina));
            request.setAttribute("totalPaginas", Integer.valueOf(totalPaginas));
            // El contador cuenta el TOTAL del filtro, no las filas de la pagina (B167/B175/B183).
            request.setAttribute("totalResultados", Integer.valueOf(totalFilas));
            request.setAttribute("ordenActual", ServicioMora.ordenAplicado(request.getParameter("orden")));
            request.setAttribute("sentidoActual",
                    ServicioMora.sentidoAplicado(request.getParameter("orden"), request.getParameter("sentido")));

            // Para el formulario de gestión de cada fila: id de la persona cliente (siempre
            // disponible para contactar) por préstamo.
            java.util.Map<Long, Long> idClientePorPrestamo = new java.util.HashMap<>();
            // Tanda 2 (14/10/2026, pedido de Joel): un token de un solo uso por fila (no uno
            // compartido para toda la página) -- así un doble clic en el formulario de UN
            // préstamo no bloquea el de otro en la misma carga de /mora.
            // B197: el bucle recorre SOLO las filas de la pagina, no las 69 del listado. Son dos
            // consultas por fila (el prestamo y su solicitud), asi que antes crecian con el total.
            java.util.Map<Long, String> tokensGestionPorPrestamo = new java.util.HashMap<>();
            for (py.com.capitalcredito.sgcpc.dto.FilaMora fila : filas) {
                PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, fila.getIdPrestamo());
                SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
                idClientePorPrestamo.put(fila.getIdPrestamo(), solicitud.getIdCliente());
                tokensGestionPorPrestamo.put(fila.getIdPrestamo(), TokenUnaVez.generar(sesion));
            }
            request.setAttribute("idClientePorPrestamo", idClientePorPrestamo);
            request.setAttribute("tokensGestionPorPrestamo", tokensGestionPorPrestamo);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el seguimiento de mora.", error);
            request.setAttribute("mensajeError", "No se pudo leer el seguimiento de mora.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /** B197 (15/11/2026): cuantas filas por pagina. El mismo 20 que el resto del sistema. */
    private static final int FILAS_POR_PAGINA = 20;

    /** B197: la pagina pedida, acotada a lo que existe -- misma forma que en B175/B183/B191. */
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

    private Long idDeTexto(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
