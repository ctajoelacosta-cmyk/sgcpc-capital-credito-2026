package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
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
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.excepciones.CajaDeOtroDiaException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioCaja;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioDesembolso;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * P08, desembolso (Doc. 10 §10.11: ControladorDesembolsoServlet / ServicioDesembolso /
 * ServicioCaja). Protegido por FiltroAutorizacion (DESEMBOLSAR, TOTAL solo para la cajera).
 */
public class ControladorDesembolsoServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/desembolso/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/desembolso/_resultados.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/desembolso/ficha.jsp";

    /**
     * B189 (15/11/2026): el punto de retorno de la bandeja, guardado en la sesión para que el
     * "Volver a la bandeja" de la ficha devuelva la búsqueda que la cajera tenía, en vez de la
     * bandeja sin filtrar. Mismo criterio y misma forma que B147 (Clientes), B163 (Solicitudes),
     * B170 (Aprobaciones) y B180 (Formalización), con su propia clave: cada listado del sistema
     * recuerda el suyo por separado.
     */
    private static final String RETORNO_BANDEJA_EN_SESION = "retornoBandejaDesembolso";

    private final ServicioDesembolso servicioDesembolso = new ServicioDesembolso();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response);
            return;
        }
        String[] segmentos = pathInfo.substring(1).split("/");
        if (segmentos.length == 1) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarFicha(request, response, idPrestamo);
            return;
        }
        // D-130 (antes D-94, E8): impresión "para firma" (préstamo todavía PENDIENTE_DESEMBOLSO)
        // y reimpresión del comprobante de desembolso (ya confirmado) -- misma ruta para las dos,
        // ServicioDesembolso decide según el estado del préstamo. "inline" (no "attachment"): se
        // abre listo para imprimir en una pestaña nueva, pedido de Joel.
        if (segmentos.length == 2 && "comprobante".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
            HttpSession sesion = request.getSession();
            try {
                boolean pendienteDesembolso;
                try (Connection conexionEstado = FabricaConexionBD.obtenerConexion()) {
                    PrestamoCredito prestamoComprobante = servicioFormalizacion.obtenerPorId(conexionEstado, idPrestamo);
                    pendienteDesembolso = prestamoComprobante != null
                            && prestamoComprobante.getEstadoPrestamo() == EstadoPrestamo.PENDIENTE_DESEMBOLSO;
                }
                byte[] pdf = pendienteDesembolso
                        ? servicioDesembolso.generarComprobantePreDesembolso(idPrestamo, solicitante.getIdUsuario())
                        : servicioDesembolso.obtenerPdfComprobanteDesembolso(idPrestamo, solicitante.getIdUsuario());
                response.setContentType("application/pdf");
                response.setContentLength(pdf.length);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Content-Disposition", "inline; filename=\"comprobante_desembolso_" + idPrestamo + ".pdf\"");
                try (java.io.OutputStream salida = response.getOutputStream()) {
                    salida.write(pdf);
                }
            } catch (PrestamoNoPendienteDesembolsoException | CajaDeOtroDiaException error) {
                // A.8 (consolidación, pedido de Joel): bloqueos de negocio por estado/tiempo --
                // advertencia, no error.
                MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
                response.sendRedirect(request.getContextPath() + "/desembolso/" + idPrestamo);
            } catch (NegocioException error) {
                MensajesFlash.guardarError(sesion, error.getMessage());
                response.sendRedirect(request.getContextPath() + "/desembolso/" + idPrestamo);
            } catch (SQLException error) {
                getServletContext().log("Error de base de datos al generar el comprobante del préstamo "
                        + idPrestamo + ".", error);
                MensajesFlash.guardarError(sesion, "No se pudo generar el comprobante. Intente nuevamente.");
                response.sendRedirect(request.getContextPath() + "/desembolso/" + idPrestamo);
            }
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

        if (segmentos.length == 2 && "confirmar".equals(segmentos[1])) {
            procesarConfirmar(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarConfirmar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            String identificadorConfirmacion = request.getParameter("identificadorConfirmacion");
            boolean confirmoEntregaEfectivo = "true".equals(request.getParameter("confirmoEntregaEfectivo"));
            PrestamoCredito prestamoDesembolsado = servicioDesembolso.confirmarDesembolso(idPrestamo,
                    identificadorConfirmacion, confirmoEntregaEfectivo, solicitante.getIdUsuario());
            // Bug encontrado por Joel: redirigía a /formalizacion/{id}, ruta a la que la cajera
            // no tiene acceso (GENERAR_DOCUMENTOS_FORMALIZACION es NINGUNO para su perfil) -- 403
            // apenas después del mensaje de éxito. Ahora vuelve a su propia bandeja.
            // Bloque 7/E10 (D-131, pedido de Joel): el modal de éxito trae el botón para imprimir
            // el comprobante (misma ruta que "reimprimir" -- ya está marcado y auditado igual).
            java.util.Map<String, String> imprimiblesDesembolso = new java.util.LinkedHashMap<>();
            imprimiblesDesembolso.put("Imprimir comprobante", "/desembolso/" + idPrestamo + "/comprobante");
            MensajesFlash.guardarExito(sesion, "Desembolso de " + prestamoDesembolsado.getIdentificadorOperacion()
                    + " confirmado (" + FormateadorNumeros.comoGuaranies(prestamoDesembolsado.getMontoAprobado()) + ").",
                    imprimiblesDesembolso);
            response.sendRedirect(request.getContextPath() + "/desembolso");
            return;
        } catch (PrestamoNoPendienteDesembolsoException | CajaDeOtroDiaException error) {
            // A.8 (consolidación, pedido de Joel): bloqueos de negocio por estado/tiempo --
            // advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al confirmar el desembolso del préstamo " + idPrestamo + ".",
                    error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/desembolso/" + idPrestamo);
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 5): buscador
        // con el mismo mecanismo que Clientes/Mora/Aprobaciones/Solicitudes/Formalización
        // (listado-vivo.js + PeticionAjax.esAjax).
        String textoBusqueda = request.getParameter("texto");
        request.setAttribute("textoBusqueda", textoBusqueda);
        // B189 (15/11/2026): punto de retorno de la bandeja, en la sesión. Lo escribe SOLO este
        // método y lo sirve SOLO mostrarFicha (un dato, un dueño): la JSP no lo arma. En la sesión
        // por el mismo motivo que B163/B170/B180 -- así sobrevive al redirect de después de
        // confirmar el desembolso, que vuelve a /desembolso sin parámetros. El pedido AJAX del
        // "listado vivo" pasa por acá igual, así que buscar en vivo lo actualiza.
        request.getSession().setAttribute(RETORNO_BANDEJA_EN_SESION, construirRetornoBandeja(request));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<PrestamoCredito> listos = servicioFormalizacion.listarListosParaDesembolsar(conexion);
            Map<Long, Persona> clientesPorPrestamo = new HashMap<>();
            for (PrestamoCredito prestamo : listos) {
                SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
                clientesPorPrestamo.put(prestamo.getIdPrestamo(),
                        servicioClientes.buscarPorId(conexion, solicitud.getIdCliente()));
            }
            // B191 (15/11/2026): filtrar, ordenar y recien entonces cortar la pagina -- ordenar
            // despues del corte daria un orden falso (B157). El ordenador es el MISMO que usa la
            // seccion "pendientes de desembolso" de Formalizacion (B183): misma entidad y las
            // mismas cuatro columnas (operacion, cliente, monto, formalizado), asi que las dos
            // pantallas ordenan igual sin una segunda copia de la regla.
            List<PrestamoCredito> listosFiltrados =
                    servicioFormalizacion.filtrarPrestamosPorTexto(listos, clientesPorPrestamo, textoBusqueda);
            List<PrestamoCredito> listosOrdenados = servicioFormalizacion.ordenarPrestamosDeBandeja(
                    listosFiltrados, clientesPorPrestamo, request.getParameter("orden"),
                    request.getParameter("sentido"), ServicioFormalizacion.SeccionBandeja.DESEMBOLSO);
            int totalListos = listosOrdenados.size();
            int totalPaginasListos = Math.max(1, (int) Math.ceil(totalListos / (double) FILAS_POR_PAGINA));
            int paginaListos = paginaPedida(request.getParameter("pagina"), totalPaginasListos);
            int desdeListos = (paginaListos - 1) * FILAS_POR_PAGINA;
            int hastaListos = Math.min(totalListos, desdeListos + FILAS_POR_PAGINA);
            request.setAttribute("listos", listosOrdenados.subList(desdeListos, hastaListos));
            request.setAttribute("pagina", Integer.valueOf(paginaListos));
            request.setAttribute("totalPaginas", Integer.valueOf(totalPaginasListos));
            // El contador cuenta el TOTAL del filtro, no las filas de la pagina (B167/B175/B183).
            request.setAttribute("totalResultados", Integer.valueOf(totalListos));
            request.setAttribute("ordenActual", ServicioFormalizacion.ordenAplicado(
                    request.getParameter("orden"), ServicioFormalizacion.SeccionBandeja.DESEMBOLSO));
            request.setAttribute("sentidoActual",
                    ServicioFormalizacion.sentidoAplicado(request.getParameter("sentido")));
            request.setAttribute("clientesPorPrestamo", clientesPorPrestamo);
            // Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7 paso E): cuántos préstamos
            // PENDIENTE_DESEMBOLSO hay con el checklist todavía incompleto -- para que el título
            // "Préstamos listos para desembolsar" no deje a la cajera pensando que no hay nada
            // pendiente en absoluto. Solo se calcula sin búsqueda activa (con texto escrito, la
            // pantalla muestra solo el resultado de esa búsqueda, sin este aviso general).
            if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
                request.setAttribute("pendientesIncompletos",
                        servicioFormalizacion.contarPendientesConChecklistIncompleto(conexion));
            }
            // B186 (15/11/2026): el estado de la caja también en la BANDEJA, no solo en la ficha.
            // Sin esto, la lista ofrece "Desembolsar" como si se pudiera y el bloqueo recién
            // aparece un clic después -- el criterio de B88/B89/B100, que el aviso esté donde se
            // decide. Es exactamente lo que ya hace mostrarFicha unas líneas más abajo, con el
            // MISMO dueño del texto (ServicioCaja.mensajeCajaDeOtroDia): ningún mensaje nuevo.
            SesionCaja sesionAbiertaLista = servicioCaja.obtenerSesionAbierta(conexion);
            request.setAttribute("sesionAbierta", sesionAbiertaLista);
            if (sesionAbiertaLista != null && !servicioCaja.esDeHoy(sesionAbiertaLista)) {
                request.setAttribute("mensajeCajaOtroDia", servicioCaja.mensajeCajaDeOtroDia(sesionAbiertaLista));
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la bandeja de desembolsos.", error);
            request.setAttribute("mensajeError", "No se pudo leer la bandeja de desembolsos.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /**
     * B191 (15/11/2026): cuantas filas por pagina. El mismo 20 que el resto del sistema.
     */
    private static final int FILAS_POR_PAGINA = 20;

    /** B191: la pagina pedida, acotada a lo que existe -- misma forma que en B175/B183. */
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
     * B189: el único filtro de esta bandeja (texto) como query string, para el "Volver a la
     * bandeja" de la ficha. Misma forma que construirRetornoBandeja de B180 en Formalización. Si
     * está vacío devuelve cadena vacía y el enlace queda igual que siempre.
     */
    private String construirRetornoBandeja(HttpServletRequest request) {
        String valor = request.getParameter("texto");
        if (valor == null || valor.trim().isEmpty()) {
            return "";
        }
        try {
            return "texto=" + java.net.URLEncoder.encode(valor, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            // UTF-8 siempre está en la JVM; nunca debería pasar.
            return "";
        }
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, long idPrestamo)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // B189: el "Volver a la bandeja" devuelve la búsqueda que la cajera tenía. Vacío si
        // todavía no pasó por la bandeja en esta sesión: entonces va a /desembolso, como siempre.
        Object retornoBandeja = request.getSession().getAttribute(RETORNO_BANDEJA_EN_SESION);
        request.setAttribute("urlVolverBandeja", retornoBandeja == null ? "" : retornoBandeja.toString());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            request.setAttribute("prestamo", prestamo);
            request.setAttribute("cliente", cliente);

            // Corrección de Joel (Bloque 2, revisión): un préstamo que YA se desembolsó no tiene
            // por qué seguir mostrando "Condiciones para desembolsar" (checklist/vigencia/caja/
            // saldo) -- eso solo tiene sentido mientras sigue PENDIENTE_DESEMBOLSO. Acá se corta
            // temprano con el dato real del desembolso (fecha y N° de comprobante) + el botón de
            // reimprimir; D-89: CC-2026-90001 quedó ACTIVO sin fila en desembolsos (se cargó por
            // SQL directo), así que este caso puede no tener desembolsoConfirmado igual.
            if (prestamo.getEstadoPrestamo() != EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
                request.setAttribute("yaDesembolsado", true);
                Desembolso desembolsoConfirmado = servicioDesembolso.obtenerConfirmadoPorPrestamo(conexion, idPrestamo);
                request.setAttribute("desembolsoConfirmado", desembolsoConfirmado);
                request.setAttribute("tieneComprobante", desembolsoConfirmado != null);
                // D-130 (pedido de Joel): cargar el escaneo firmado (opcional) + reimprimir.
                request.setAttribute("idTipoComprobanteFirmado", servicioDesembolso.obtenerIdTipoComprobanteFirmado(conexion));
                request.setAttribute("comprobanteFirmado",
                        servicioDesembolso.obtenerComprobanteDesembolsoFirmado(conexion, idPrestamo));
                RequestDispatcher dispatcherDesembolsado = request.getRequestDispatcher(VISTA_FICHA);
                dispatcherDesembolsado.forward(request, response);
                return;
            }
            request.setAttribute("tieneComprobante", false);
            // D-130 (pedido de Joel): "Confirmar desembolso" se habilita solo después de haber
            // impreso el comprobante para firma al menos una vez.
            request.setAttribute("comprobanteYaImpreso", prestamo.getNumeroComprobanteDesembolso() != null);

            boolean checklistCompleto = servicioFormalizacion
                    .esChecklistCompleto(servicioFormalizacion.obtenerChecklist(conexion, idPrestamo));
            request.setAttribute("checklistCompleto", checklistCompleto);

            int diasValidez = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_validez_documentos");
            DocumentoExpediente contratoGenerado = servicioDesembolso.obtenerContratoGeneradoVigente(conexion, idPrestamo);
            boolean documentosVigentes = servicioDesembolso.validarDocumentosVigentes(
                    contratoGenerado == null ? null : contratoGenerado.getFechaDocumento(), diasValidez);
            request.setAttribute("documentosVigentes", documentosVigentes);
            request.setAttribute("diasValidezDocumentos", diasValidez);

            SesionCaja sesionAbierta = servicioCaja.obtenerSesionAbierta(conexion);
            request.setAttribute("sesionAbierta", sesionAbierta);
            boolean cajaLista = sesionAbierta != null && servicioCaja.esDeHoy(sesionAbierta);
            request.setAttribute("cajaLista", cajaLista);
            // Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto que Caja/Cobranza.
            if (sesionAbierta != null && !cajaLista) {
                request.setAttribute("mensajeCajaOtroDia", servicioCaja.mensajeCajaDeOtroDia(sesionAbierta));
            }
            // Mejora de Joel: 4.ª condición explícita de saldo suficiente (antes solo la validaba
            // el servidor al confirmar; la ficha no avisaba de antemano si faltaba efectivo).
            boolean saldoSuficiente = false;
            if (cajaLista) {
                BigDecimal saldoTeoricoFicha = servicioCaja.calcularSaldoTeorico(conexion, sesionAbierta.getIdSesionCaja());
                request.setAttribute("saldoTeorico", saldoTeoricoFicha);
                saldoSuficiente = saldoTeoricoFicha.compareTo(prestamo.getMontoAprobado()) >= 0;
            }
            request.setAttribute("saldoSuficiente", saldoSuficiente);

            request.setAttribute("puedeDesembolsar",
                    checklistCompleto && documentosVigentes && cajaLista && saldoSuficiente);
            request.setAttribute("identificadorConfirmacion", UUID.randomUUID().toString());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el desembolso del préstamo " + idPrestamo + ".", error);
            request.setAttribute("mensajeError", "No se pudo leer el préstamo.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }

    private Long idDeTexto(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
