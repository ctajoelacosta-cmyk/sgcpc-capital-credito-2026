package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.dao.FacturaDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.excepciones.CierreDocumentalCompletoException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioReversiones;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * PO-13 (E9/Bloque 5): bandeja de reversiones (gerente) y acciones de solicitar/autorizar/rechazar.
 * Protegido por FiltroAutorizacion: SOLICITAR_REVERSION (cajera) para /reversiones/solicitar,
 * AUTORIZAR_REVERSION_O_DIFERENCIA (gerente) para /reversiones/autorizar, /reversiones/rechazar y
 * el GET de la bandeja (/reversiones).
 */
public class ControladorReversionesServlet extends HttpServlet {

    private static final String VISTA_BANDEJA = "/WEB-INF/vistas/reversiones/bandeja.jsp";

    private final ServicioReversiones servicioReversiones = new ServicioReversiones();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final FacturaDAO facturaDAO = new FacturaDAO();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final py.com.capitalcredito.sgcpc.dao.AuditoriaDAO auditoriaDAO = new py.com.capitalcredito.sgcpc.dao.AuditoriaDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final ServicioClientes servicioClientes = new ServicioClientes();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // Punto 12 (pedido de Joel): pestaña "Historial" (?vista=historial), mismo patrón que
            // ControladorAprobacionesServlet.mostrarLista.
            boolean vistaHistorial = "historial".equals(request.getParameter("vista"));
            request.setAttribute("vistaHistorial", vistaHistorial);
            List<ReversionPago> filas = vistaHistorial
                    ? servicioReversiones.listarDecididas(conexion)
                    : servicioReversiones.listarSolicitadas(conexion);
            request.setAttribute("solicitadas", filas);
            // Datos del pago/préstamo/factura/usuarios de cada fila, para que la pantalla los
            // muestre sin que el gerente tenga que abrir la ficha de cobranza aparte.
            Map<Long, Pago> pagoPorReversion = new HashMap<>();
            Map<Long, PrestamoCredito> prestamoPorReversion = new HashMap<>();
            Map<Long, Factura> facturaPorReversion = new HashMap<>();
            Map<Long, String> solicitantePorReversion = new HashMap<>();
            Map<Long, String> decisorPorReversion = new HashMap<>();
            Map<Long, String> clientePorReversion = new HashMap<>();
            // C30 (decisión de Joel, 03/11/2026): la marca de la solicitud que ya no se puede
            // autorizar, porque se confirmó una operación posterior sobre ese préstamo. Antes el
            // gerente se enteraba recién al intentar autorizar, con el rechazo como único
            // resultado posible. El criterio y el texto son de ServicioReversiones, los mismos que
            // usa autorizarReversion -- la pantalla no decide nada.
            Map<Long, String> avisoOperacionPosteriorPorReversion = new HashMap<>();
            for (ReversionPago reversion : filas) {
                Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
                pagoPorReversion.put(reversion.getIdReversionPago(), pago);
                if (!vistaHistorial && servicioReversiones.hayOperacionPosterior(conexion, pago)) {
                    avisoOperacionPosteriorPorReversion.put(reversion.getIdReversionPago(),
                            ServicioReversiones.textoAvisoOperacionPosterior());
                }
                if (pago != null) {
                    PrestamoCredito prestamoFila = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
                    prestamoPorReversion.put(reversion.getIdReversionPago(), prestamoFila);
                    facturaPorReversion.put(reversion.getIdReversionPago(),
                            facturaDAO.obtenerPorPago(conexion, pago.getIdPago()));
                    if (prestamoFila != null) {
                        SolicitudCredito solicitudFila = solicitudDAO.obtenerPorId(conexion, prestamoFila.getIdSolicitud());
                        if (solicitudFila != null) {
                            py.com.capitalcredito.sgcpc.modelos.Persona clienteFila = servicioClientes.buscarPorId(conexion,
                                    solicitudFila.getIdCliente());
                            clientePorReversion.put(reversion.getIdReversionPago(),
                                    clienteFila == null ? "" : clienteFila.getNombres() + " " + clienteFila.getApellidos());
                        }
                    }
                }
                Usuario solicitanteFila = servicioUsuarios.buscarPorId(conexion, reversion.getSolicitadoPor());
                solicitantePorReversion.put(reversion.getIdReversionPago(),
                        solicitanteFila == null ? "" : solicitanteFila.getNombres() + " " + solicitanteFila.getApellidos());
                if (vistaHistorial) {
                    String decisor;
                    if (reversion.getAutorizadoPor() != null) {
                        Usuario autorizador = servicioUsuarios.buscarPorId(conexion, reversion.getAutorizadoPor());
                        decisor = autorizador == null ? "" : autorizador.getNombres() + " " + autorizador.getApellidos();
                    } else {
                        // RECHAZADA no guarda un usuario propio en reversiones_pago -- se recupera
                        // de la auditoría de RECHAZAR_REVERSION (ver AuditoriaDAO).
                        String nombreRechazo = auditoriaDAO.obtenerNombreUsuarioDeOperacion(conexion,
                                "RECHAZAR_REVERSION", "REVERSION_PAGO", reversion.getIdReversionPago());
                        decisor = nombreRechazo == null ? "" : nombreRechazo;
                    }
                    decisorPorReversion.put(reversion.getIdReversionPago(), decisor);
                }
            }
            request.setAttribute("pagoPorReversion", pagoPorReversion);
            request.setAttribute("prestamoPorReversion", prestamoPorReversion);
            request.setAttribute("facturaPorReversion", facturaPorReversion);
            request.setAttribute("solicitantePorReversion", solicitantePorReversion);
            request.setAttribute("decisorPorReversion", decisorPorReversion);
            request.setAttribute("clientePorReversion", clientePorReversion);
            request.setAttribute("avisoOperacionPosteriorPorReversion", avisoOperacionPosteriorPorReversion);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer las reversiones.", error);
            request.setAttribute("mensajeError", "No se pudo leer la bandeja de reversiones.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_BANDEJA);
        dispatcher.forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String[] segmentos = pathInfo == null ? new String[0] : pathInfo.substring(1).split("/");

        if (segmentos.length == 1 && "solicitar".equals(segmentos[0])) {
            procesarSolicitar(request, response, sesion, solicitante);
            return;
        }
        if (segmentos.length == 2 && "autorizar".equals(segmentos[0])) {
            procesarAutorizar(request, response, sesion, solicitante, segmentos[1]);
            return;
        }
        if (segmentos.length == 2 && "rechazar".equals(segmentos[0])) {
            procesarRechazar(request, response, sesion, solicitante, segmentos[1]);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarSolicitar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante) throws IOException {
        Long idPago = idDeTexto(request.getParameter("idPago"));
        String redirigirA = request.getParameter("volverA");
        String destino = redirigirA == null || redirigirA.trim().isEmpty()
                ? request.getContextPath() + "/cobranza"
                : request.getContextPath() + redirigirA;
        if (idPago == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            // Idempotencia (§14.3): una clave nueva por cada GET de la ficha que muestra el
            // formulario -- acá se genera la primera vez que este POST llega sin una ya guardada
            // en el propio formulario (el input oculto la trae del lado del JSP).
            String claveIdempotencia = request.getParameter("identificadorConfirmacion");
            if (claveIdempotencia == null || claveIdempotencia.trim().isEmpty()) {
                claveIdempotencia = UUID.randomUUID().toString();
            }
            // Punto 1 (RF25, factura, pedido de Joel): si el cobro tiene factura, la cajera tiene
            // que declarar que la tiene en su poder (checkbox "required" en pantalla, mismo
            // mecanismo "Falta un dato" de pie.jsp; servicioReversiones revalida en el servidor).
            boolean facturaEnPoderDeclaradoCajera = "true".equals(request.getParameter("facturaEnPoderDeclaradoCajera"));
            servicioReversiones.solicitarReversion(idPago, request.getParameter("motivo"),
                    solicitante.getIdUsuario(), claveIdempotencia, facturaEnPoderDeclaradoCajera);
            MensajesFlash.guardarExito(sesion, "Solicitud de reversión enviada. Queda pendiente de autorización del gerente.");
        } catch (CierreDocumentalCompletoException error) {
            // 13/10/2026 (pedido de Joel): advertencia ("!"/"Entendido"), no error (X roja) --
            // mismo patrón que el resto de los bloqueos de negocio con excepción propia.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al solicitar la reversión del pago " + idPago + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(destino);
    }

    private void procesarAutorizar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoId) throws IOException {
        Long idReversionPago = idDeTexto(textoId);
        if (idReversionPago == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Boolean facturaEnPoderConfirmado = "true".equals(request.getParameter("facturaEnPoderConfirmado"));
            ReversionPago reversion = servicioReversiones.autorizarReversion(idReversionPago, solicitante.getIdUsuario(),
                    facturaEnPoderConfirmado, Reloj.hoy());
            MensajesFlash.guardarExito(sesion, construirMensajeAutorizacion(reversion), imprimiblesDeReversion(reversion));
        } catch (CierreDocumentalCompletoException error) {
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al autorizar la reversión " + idReversionPago + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/reversiones");
    }

    /** Corrección de Joel (tras la prueba de la parte A): "Reversión autorizada." solo, sin
     * números, no alcanza -- el mensaje de éxito tiene que decir cuánto volvió a la caja y, si
     * había factura, con qué Nota de anulación quedó. */
    private String construirMensajeAutorizacion(ReversionPago reversion) {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
            StringBuilder mensaje = new StringBuilder("Reversión autorizada. Se devolvieron ")
                    .append(py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()))
                    .append(" de la caja.");
            Factura factura = facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
            if (factura != null) {
                mensaje.append(" Factura ").append(factura.getNumeroCompleto()).append(" anulada con la Nota ")
                        .append(factura.getNumeroNotaAnulacion()).append(".");
            }
            return mensaje.toString();
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al armar el mensaje de autorización.", error);
            return "Reversión autorizada.";
        }
    }

    /** Punto 2 (RF25, pedido de Joel): botón "Imprimir constancia de reversión" en el cartel de
     * éxito de la autorización -- mismo mecanismo que imprimiblesDePago en ControladorCobranzaServlet. */
    private Map<String, String> imprimiblesDeReversion(ReversionPago reversion) {
        Map<String, String> imprimibles = new HashMap<>();
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
            if (pago != null) {
                imprimibles.put("Imprimir constancia de reversión",
                        "/cobranza/" + pago.getIdPrestamo() + "/reversion/" + reversion.getIdReversionPago());
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al armar el botón de imprimir la constancia "
                    + "de la reversión " + reversion.getIdReversionPago() + ".", error);
        }
        return imprimibles;
    }

    private void procesarRechazar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoId) throws IOException {
        Long idReversionPago = idDeTexto(textoId);
        if (idReversionPago == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioReversiones.rechazarReversion(idReversionPago, solicitante.getIdUsuario(),
                    request.getParameter("motivoRechazo"));
            MensajesFlash.guardarExito(sesion, "Solicitud de reversión rechazada.");
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al rechazar la reversión " + idReversionPago + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/reversiones");
    }

    private Long idDeTexto(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException | NullPointerException error) {
            return null;
        }
    }
}
