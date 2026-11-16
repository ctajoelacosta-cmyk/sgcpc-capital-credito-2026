package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.excepciones.FechaFueraDePlazoException;
import py.com.capitalcredito.sgcpc.excepciones.MotivoAnulacionInsuficienteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P06, formalización (Doc. 10 §10.9: ControladorFormalizacionServlet / ServicioFormalizacion).
 * Protegido por FiltroAutorizacion (GENERAR_DOCUMENTOS_FORMALIZACION, TOTAL para oficial y
 * gerente -- a diferencia de /solicitudes vs /aprobaciones, acá los dos perfiles operan la
 * misma pantalla, D-68). Anular (ANULAR_PRESTAMO_PRE_DESEMBOLSO) es solo del gerente: como
 * FiltroAutorizacion mapea por prefijo a un único permiso, esa sub-acción se valida acá con un
 * chequeo de perfil explícito.
 */
public class ControladorFormalizacionServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/formalizacion/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/formalizacion/_resultados.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/formalizacion/ficha.jsp";

    /**
     * B180 (14/11/2026): el punto de retorno de la bandeja, guardado en la sesión para que el
     * "Volver a la bandeja" de "Formalizar" y de la ficha devuelva la búsqueda que el oficial
     * tenía, en vez de la bandeja sin filtrar. Mismo nombre y mismo criterio que
     * RETORNO_LISTA_EN_SESION de ControladorClientesServlet (B147), ControladorSolicitudesServlet
     * (B163) y ControladorAprobacionesServlet (B170), pero con su propia clave: cada listado del
     * sistema recuerda el suyo por separado.
     */
    private static final String RETORNO_BANDEJA_EN_SESION = "retornoBandejaFormalizacion";

    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response);
            return;
        }
        String[] segmentos = pathInfo.substring(1).split("/");
        if (segmentos.length == 2 && "nueva".equals(segmentos[0])) {
            Long idSolicitud = idDeTexto(segmentos[1]);
            if (idSolicitud == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarNueva(request, response, idSolicitud);
            return;
        }
        // E9/Bloque 5 (P10): /formalizacion/{id}/cierre-documental -- antes del chequeo de
        // segmentos.length == 1, mismo criterio de "ruta específica antes que la general".
        if (segmentos.length == 2 && "cierre-documental".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarCierreDocumental(request, response, idPrestamo);
            return;
        }
        if (segmentos.length == 1) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarFicha(request, response, idPrestamo);
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

        if (segmentos.length == 3 && "nueva".equals(segmentos[0]) && "generar".equals(segmentos[2])) {
            procesarGenerar(request, response, sesion, solicitante, segmentos[1]);
            return;
        }
        if (segmentos.length == 2 && "regenerar".equals(segmentos[1])) {
            procesarRegenerar(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        if (segmentos.length == 2 && "anular".equals(segmentos[1])) {
            procesarAnular(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        if (segmentos.length == 2 && "generar-pdf".equals(segmentos[1])) {
            procesarGenerarPdf(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        if (segmentos.length == 2 && "regenerar-pdf".equals(segmentos[1])) {
            procesarRegenerarSoloPdfs(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        if (segmentos.length == 2 && "generar-constancia-devolucion".equals(segmentos[1])) {
            procesarGenerarConstanciaDevolucion(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        if (segmentos.length == 2 && "completar-cierre-documental".equals(segmentos[1])) {
            procesarCompletarCierreDocumental(request, response, sesion, solicitante, segmentos[0]);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarGenerar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoIdSolicitud) throws IOException {
        Long idSolicitud = idDeTexto(textoIdSolicitud);
        if (idSolicitud == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            LocalDate primerVencimiento = parsearFecha(request.getParameter("primerVencimiento"));
            // F2 (21/10/2026, pedido de Joel, punto 7): APROBAR_RECHAZAR_SOLICITUD -- cancelar la
            // solicitud (si llega a bloquearse) vive en Aprobación.
            boolean puedeResolver = nivelAccesoSesion(request, Permiso.APROBAR_RECHAZAR_SOLICITUD) != NivelAcceso.NINGUNO;
            long idPrestamo = servicioFormalizacion.generarDocumentos(idSolicitud, primerVencimiento,
                    solicitante.getIdUsuario(), puedeResolver);
            // Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7): no decía qué sigue,
            // mismo criterio que el aviso verde de "Documentación completa" de la ficha.
            MensajesFlash.guardarExito(sesion, "Documentos generados. Ahora cargue los firmados en el checklist de esta ficha.");
            response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
            return;
        } catch (FechaFueraDePlazoException error) {
            // A.8 (consolidación, pedido de Joel): no es un error del servidor, es un bloqueo de
            // plazo -- advertencia (ícono y botón "Entendido"), mismo criterio que
            // SolicitudYaDecididaException (RF09/RF10).
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
            // D.3 (reprueba Lote 1, pedido de Joel): conservar la fecha elegida -- antes volvía a
            // mostrar la propuesta por defecto, como si el usuario no hubiera escrito nada.
            guardarValorPrimerVencimiento(sesion, request);
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al formalizar la solicitud " + idSolicitud + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/nueva/" + idSolicitud);
    }

    /** D.3 (reprueba Lote 1, pedido de Joel): mismo patrón que
     * ControladorCajaServlet.guardarValoresCierre -- conservar lo que se tecleó cuando el
     * rechazo es por fecha fuera de plazo (generar o regenerar). */
    private void guardarValorPrimerVencimiento(HttpSession sesion, HttpServletRequest request) {
        Map<String, String> valores = new HashMap<>();
        valores.put("primerVencimiento", request.getParameter("primerVencimiento"));
        MensajesFlash.guardarValores(sesion, valores);
    }

    private void procesarRegenerar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            LocalDate primerVencimiento = parsearFecha(request.getParameter("primerVencimiento"));
            // F2 (21/10/2026, pedido de Joel, punto 7): mismo permiso que procesarGenerar.
            boolean puedeResolver = nivelAccesoSesion(request, Permiso.APROBAR_RECHAZAR_SOLICITUD) != NivelAcceso.NINGUNO;
            // B125 (11/11/2026): con la MISMA fecha el servicio no reemplaza nada, así que el
            // mensaje lo dice en vez de festejar una regeneración que no ocurrió.
            boolean seRegeneraron = servicioFormalizacion.regenerarDocumentos(idPrestamo, primerVencimiento,
                    solicitante.getIdUsuario(), puedeResolver);
            if (seRegeneraron) {
                MensajesFlash.guardarExito(sesion, "Documentos regenerados.");
            } else {
                MensajesFlash.guardarAdvertencia(sesion, "La fecha no cambió: no se regeneró ningún documento.");
            }
        } catch (FechaFueraDePlazoException error) {
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
            // D.3 (reprueba Lote 1, pedido de Joel): conservar la fecha elegida también al regenerar.
            guardarValorPrimerVencimiento(sesion, request);
        } catch (PrestamoNoPendienteDesembolsoException error) {
            // PA-31 (14/10/2026, pedido de Joel, autorizado): advertencia, no error, mismo criterio
            // que procesarAnular.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al regenerar el préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
    }

    private void procesarAnular(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // ANULAR_PRESTAMO_PRE_DESEMBOLSO es NINGUNO para el oficial (ver ServicioAutorizacion) --
        // FiltroAutorizacion ya solo exige GENERAR_DOCUMENTOS_FORMALIZACION (TOTAL para los dos
        // perfiles) para esta ruta, así que el permiso más restrictivo de esta sub-acción se
        // valida acá. D7 (pedido de Joel): el administrador también puede anular.
        if (solicitante.getPerfilUsuario() != PerfilUsuario.GERENTE_ADMINISTRADOR
                && solicitante.getPerfilUsuario() != PerfilUsuario.ADMINISTRADOR) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para esta acción.");
            return;
        }
        try {
            servicioFormalizacion.anularAntesDeDesembolso(idPrestamo, request.getParameter("motivo"),
                    solicitante.getIdUsuario());
            MensajesFlash.guardarExito(sesion, "Préstamo anulado.");
        } catch (MotivoAnulacionInsuficienteException | PrestamoNoPendienteDesembolsoException error) {
            // RF14 (pedido de Joel, 11/10/2026): ni "falta completar el motivo" ni "ya fue
            // desembolsado por otro" son errores del servidor -- advertencia, no error, mismo
            // criterio que el resto de A.8 (consolidación RF08-RF19).
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al anular el préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
    }

    /**
     * D-70: para un préstamo ya formalizado antes de que existiera el diseño de los reportes
     * (por ejemplo CC-2026-00002) -- genera los PDF que todavía falten, sin tocar el plan ni el
     * número de operación (no es un "regenerar"). Idempotente: si ya existen, no hace nada.
     */
    private void procesarGenerarPdf(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioFormalizacion.generarPdfsFaltantes(idPrestamo, solicitante.getIdUsuario());
            MensajesFlash.guardarExito(sesion, "Documentos PDF generados.");
        } catch (PrestamoNoPendienteDesembolsoException error) {
            // PA-31 (14/10/2026, pedido de Joel, autorizado): advertencia, no error, mismo criterio
            // que procesarAnular.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar los PDF del préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
    }

    /**
     * Ajuste de Joel (revisión de reportes E5): re-emite los 3 PDF con la plantilla/datos
     * actuales, sin tocar el plan ni el número de operación (a diferencia de "regenerar", que
     * solo re-emite si cambió la fecha de primer vencimiento).
     */
    private void procesarRegenerarSoloPdfs(HttpServletRequest request, HttpServletResponse response,
            HttpSession sesion, Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioFormalizacion.regenerarSoloPdfs(idPrestamo, solicitante.getIdUsuario());
            MensajesFlash.guardarExito(sesion, "PDF re-emitidos.");
        } catch (PrestamoNoPendienteDesembolsoException error) {
            // PA-31 (14/10/2026, pedido de Joel, autorizado): advertencia, no error, mismo criterio
            // que procesarAnular.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al re-emitir los PDF del préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
    }

    /** E9/Bloque 5 (P10): genera (o reintenta, es idempotente) la constancia de devolución de pagaré. */
    private void procesarGenerarConstanciaDevolucion(HttpServletRequest request, HttpServletResponse response,
            HttpSession sesion, Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioFormalizacion.generarConstanciaDevolucion(idPrestamo, solicitante.getIdUsuario());
            // Bloque 7/E10 (D-131, pedido de Joel): el modal de éxito trae el botón para verla.
            java.util.Map<String, String> imprimiblesConstancia = new java.util.LinkedHashMap<>();
            try (Connection conexionConstancia = FabricaConexionBD.obtenerConexion()) {
                DocumentoExpediente constanciaGenerada = servicioFormalizacion
                        .obtenerConstanciaDevolucionGenerada(conexionConstancia, idPrestamo);
                if (constanciaGenerada != null) {
                    imprimiblesConstancia.put("Ver constancia", "/documentos/" + constanciaGenerada.getIdDocumento());
                }
            }
            MensajesFlash.guardarExito(sesion, "Constancia de devolución generada.", imprimiblesConstancia);
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar la constancia de devolución del préstamo "
                    + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo + "/cierre-documental");
    }

    /** E9/Bloque 5 (P10): exige el escaneo firmado ya cargado. */
    private void procesarCompletarCierreDocumental(HttpServletRequest request, HttpServletResponse response,
            HttpSession sesion, Usuario solicitante, String textoIdPrestamo) throws IOException {
        Long idPrestamo = idDeTexto(textoIdPrestamo);
        if (idPrestamo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioFormalizacion.completarCierreDocumental(idPrestamo, solicitante.getIdUsuario());
            // B85 (28/10/2026, hallazgo de Joel en la prueba en pantalla): el aviso emergente
            // todavía decía "Cierre documental completo", el nombre interno del estado, cuando el
            // resto de la pantalla ya habla de la devolución del pagaré.
            MensajesFlash.guardarExito(sesion, "Préstamo cerrado.");
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al completar el cierre documental del préstamo "
                    + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo + "/cierre-documental");
    }

    private void mostrarCierreDocumental(HttpServletRequest request, HttpServletResponse response, long idPrestamo)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
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
            // Corrección de Joel: la pantalla de "completo" tiene que decir quién lo completó, no
            // solo la fecha.
            if (prestamo.getCierreDocumentalPor() != null) {
                Usuario completoPor = servicioUsuarios.buscarPorId(conexion, prestamo.getCierreDocumentalPor());
                request.setAttribute("nombreCierreDocumentalPor",
                        completoPor == null ? "-" : completoPor.getNombres() + " " + completoPor.getApellidos());
            }
            request.setAttribute("docGenerado", servicioFormalizacion.obtenerConstanciaDevolucionGenerada(conexion, idPrestamo));
            request.setAttribute("docFirmado", servicioFormalizacion.obtenerConstanciaDevolucionFirmada(conexion, idPrestamo));
            request.setAttribute("idTipoDevolucionFirmado", servicioFormalizacion.obtenerIdTipoDevolucionFirmado(conexion));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el cierre documental del préstamo " + idPrestamo + ".",
                    error);
            // B85: mismo vocabulario que el resto de la pantalla.
            request.setAttribute("mensajeError", "No se pudo leer la devolución del pagaré.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/vistas/formalizacion/cierre-documental.jsp");
        dispatcher.forward(request, response);
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): UN solo
        // campo filtra las 3 secciones a la vez, mismo mecanismo que Clientes/Mora/Aprobaciones/
        // Solicitudes (listado-vivo.js + PeticionAjax.esAjax).
        String textoBusqueda = request.getParameter("texto");
        request.setAttribute("textoBusqueda", textoBusqueda);
        // B180 (14/11/2026): punto de retorno de la bandeja, en la sesión. Lo escribe SOLO este
        // método y lo sirven SOLO mostrarNueva y mostrarFicha (un dato, un dueño): las JSP no lo
        // arman ni lo recalculan. En la sesión por el mismo motivo que B163/B170 -- así sobrevive
        // a los redirect que vuelven a /formalizacion/{id} sin parámetros. El pedido AJAX del
        // "listado vivo" pasa por acá igual, así que buscar en vivo lo actualiza.
        request.getSession().setAttribute(RETORNO_BANDEJA_EN_SESION, construirRetornoBandeja(request));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<SolicitudCredito> paraFormalizar = servicioFormalizacion.listarParaFormalizar(conexion);
            Map<Long, Persona> clientes = new HashMap<>();
            for (SolicitudCredito fila : paraFormalizar) {
                if (!clientes.containsKey(fila.getIdCliente())) {
                    clientes.put(fila.getIdCliente(), servicioClientes.buscarPorId(conexion, fila.getIdCliente()));
                }
            }
            // B183 (15/11/2026): filtrar, ordenar y recién entonces cortar la página. Ordenar
            // después del corte daría un orden falso --"la primera por monto" sería la primera de
            // esa página-- que es la lección que B157 dejó escrita.
            List<SolicitudCredito> formalizarFiltradas =
                    servicioSolicitudes.filtrarPorTexto(paraFormalizar, clientes, textoBusqueda);
            List<SolicitudCredito> formalizarOrdenadas = servicioFormalizacion.ordenarParaFormalizar(
                    formalizarFiltradas, clientes,
                    request.getParameter("ordenFormalizar"), request.getParameter("sentidoFormalizar"));
            request.setAttribute("paraFormalizar", recortarPagina(request, formalizarOrdenadas,
                    "paginaFormalizar", "totalParaFormalizar", "totalPaginasFormalizar"));
            request.setAttribute("ordenActualFormalizar", ServicioFormalizacion.ordenAplicado(
                    request.getParameter("ordenFormalizar"), ServicioFormalizacion.SeccionBandeja.FORMALIZAR));
            request.setAttribute("sentidoActualFormalizar",
                    ServicioFormalizacion.sentidoAplicado(request.getParameter("sentidoFormalizar")));
            request.setAttribute("clientesPorSolicitud", clientes);

            // Corrección 1 (Joel): segunda sección con los préstamos ya formalizados, esperando
            // el checklist para poder desembolsar -- antes solo se llegaba escribiendo la URL.
            List<PrestamoCredito> pendientesDesembolso = servicioFormalizacion.listarPendientesDesembolso(conexion);
            Map<Long, Persona> clientesPorPrestamo = new HashMap<>();
            for (PrestamoCredito prestamo : pendientesDesembolso) {
                SolicitudCredito solicitudDelPrestamo = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
                clientesPorPrestamo.put(prestamo.getIdPrestamo(),
                        servicioClientes.buscarPorId(conexion, solicitudDelPrestamo.getIdCliente()));
            }
            List<PrestamoCredito> desembolsoFiltrados = servicioFormalizacion.filtrarPrestamosPorTexto(
                    pendientesDesembolso, clientesPorPrestamo, textoBusqueda);
            List<PrestamoCredito> desembolsoOrdenados = servicioFormalizacion.ordenarPrestamosDeBandeja(
                    desembolsoFiltrados, clientesPorPrestamo,
                    request.getParameter("ordenDesembolso"), request.getParameter("sentidoDesembolso"),
                    ServicioFormalizacion.SeccionBandeja.DESEMBOLSO);
            List<PrestamoCredito> desembolsoDeLaPagina = recortarPagina(request, desembolsoOrdenados,
                    "paginaDesembolso", "totalPendientesDesembolso", "totalPaginasDesembolso");
            // B181 (14/11/2026): además de "Incompleto", QUÉ falta -- antes había que abrir cada
            // préstamo para saberlo. El dato no es nuevo: el checklist que ya se lee para decir
            // "Completo"/"Incompleto" lo tiene, y quien sabe nombrarlo es
            // ServicioFormalizacion.nombrarLoQueFalta, el mismo método que usa el aviso de la
            // tarjeta del panel (B100) -- un dato, un dueño.
            // B183 (15/11/2026): y el checklist se lee SOLO para las filas de la página, no para
            // las de todo el listado. Son dos consultas por préstamo, así que antes crecían con el
            // total (hoy 18, pero esta sección no tiene techo) y ahora están acotadas a 20.
            Map<Long, Boolean> checklistCompletoPorPrestamo = new HashMap<>();
            Map<Long, String> faltantesChecklistPorPrestamo = new HashMap<>();
            for (PrestamoCredito prestamo : desembolsoDeLaPagina) {
                List<ServicioFormalizacion.ItemChecklist> checklistDelPrestamo =
                        servicioFormalizacion.obtenerChecklist(conexion, prestamo.getIdPrestamo());
                boolean completo = servicioFormalizacion.esChecklistCompleto(checklistDelPrestamo);
                checklistCompletoPorPrestamo.put(prestamo.getIdPrestamo(), completo);
                if (!completo) {
                    faltantesChecklistPorPrestamo.put(prestamo.getIdPrestamo(),
                            ServicioFormalizacion.nombrarLoQueFalta(checklistDelPrestamo));
                }
            }
            request.setAttribute("pendientesDesembolso", desembolsoDeLaPagina);
            request.setAttribute("ordenActualDesembolso", ServicioFormalizacion.ordenAplicado(
                    request.getParameter("ordenDesembolso"), ServicioFormalizacion.SeccionBandeja.DESEMBOLSO));
            request.setAttribute("sentidoActualDesembolso",
                    ServicioFormalizacion.sentidoAplicado(request.getParameter("sentidoDesembolso")));
            request.setAttribute("clientesPorPrestamo", clientesPorPrestamo);
            request.setAttribute("checklistCompletoPorPrestamo", checklistCompletoPorPrestamo);
            request.setAttribute("faltantesChecklistPorPrestamo", faltantesChecklistPorPrestamo);

            // E9/Bloque 5 (P10, corrección de Joel): tercera sección -- préstamos ya finalizados
            // esperando el cierre documental (devolución del pagaré), que antes no tenía ningún
            // acceso visible.
            List<PrestamoCredito> cierreDocumentalPendiente = servicioFormalizacion.listarFinalizadosConCierrePendiente(conexion);
            Map<Long, Persona> clientesPorPrestamoCierre = new HashMap<>();
            for (PrestamoCredito prestamo : cierreDocumentalPendiente) {
                SolicitudCredito solicitudDelPrestamo = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
                clientesPorPrestamoCierre.put(prestamo.getIdPrestamo(),
                        servicioClientes.buscarPorId(conexion, solicitudDelPrestamo.getIdCliente()));
            }
            List<PrestamoCredito> pagareFiltrados = servicioFormalizacion.filtrarPrestamosPorTexto(
                    cierreDocumentalPendiente, clientesPorPrestamoCierre, textoBusqueda);
            List<PrestamoCredito> pagareOrdenados = servicioFormalizacion.ordenarPrestamosDeBandeja(
                    pagareFiltrados, clientesPorPrestamoCierre,
                    request.getParameter("ordenPagare"), request.getParameter("sentidoPagare"),
                    ServicioFormalizacion.SeccionBandeja.PAGARE);
            request.setAttribute("cierreDocumentalPendiente", recortarPagina(request, pagareOrdenados,
                    "paginaPagare", "totalCierrePendiente", "totalPaginasPagare"));
            request.setAttribute("ordenActualPagare", ServicioFormalizacion.ordenAplicado(
                    request.getParameter("ordenPagare"), ServicioFormalizacion.SeccionBandeja.PAGARE));
            request.setAttribute("sentidoActualPagare",
                    ServicioFormalizacion.sentidoAplicado(request.getParameter("sentidoPagare")));
            request.setAttribute("clientesPorPrestamoCierre", clientesPorPrestamoCierre);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la bandeja de formalización.", error);
            request.setAttribute("mensajeError", "No se pudo leer la bandeja de formalización.");
        }
        // B183: las URL de los encabezados y de las 3 paginaciones las arma el controlador, no la
        // JSP: ahí viven los parámetros y la regla de qué conserva cada enlace.
        servirUrlsDeSeccion(request, "Formalizar");
        servirUrlsDeSeccion(request, "Desembolso");
        servirUrlsDeSeccion(request, "Pagare");
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /**
     * B183: todos los parámetros que esta pantalla entiende -- el filtro común y el orden y la
     * página de cada una de las 3 secciones. Un solo lugar: lo usan el "Volver a la bandeja"
     * (B180) y las URL de los encabezados y de las paginaciones.
     */
    private static final String[] PARAMETROS_BANDEJA = {"texto",
            "ordenFormalizar", "sentidoFormalizar", "paginaFormalizar",
            "ordenDesembolso", "sentidoDesembolso", "paginaDesembolso",
            "ordenPagare", "sentidoPagare", "paginaPagare"};

    /**
     * B183: la query string vigente <b>menos</b> los parámetros que el enlace va a reemplazar.
     *
     * <p>Es lo que hace que tocar una sección no mueva a las otras dos: el enlace de una página o
     * de un encabezado de la sección 2 lleva puesto el estado de la 1 y de la 3. Un encabezado
     * además excluye la página de su propia sección, para volver a la 1 al cambiar el orden (si no,
     * reordenar dejaría al usuario en una página 4 que ya no significa lo mismo).</p>
     */
    private String queryExcepto(HttpServletRequest request, String... excluidos) {
        java.util.List<String> fuera = java.util.Arrays.asList(excluidos);
        StringBuilder query = new StringBuilder();
        for (String nombre : PARAMETROS_BANDEJA) {
            if (fuera.contains(nombre)) {
                continue;
            }
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
                // UTF-8 siempre está en la JVM; nunca debería pasar.
            }
        }
        return query.toString();
    }

    /** B183: una URL base de esta pantalla, lista para que le peguen un parámetro más -- siempre
     * termina en "?" o en "&", que es el contrato de plantilla/paginacion.jsp. */
    private String urlBandejaCon(HttpServletRequest request, String... excluidos) {
        String query = queryExcepto(request, excluidos);
        return request.getContextPath() + "/formalizacion?" + (query.isEmpty() ? "" : query + "&");
    }

    /** B183: deja en el request las dos URL de una sección -- la de su paginación (conserva su
     * orden) y la de sus encabezados (reemplazan orden y sentido, y vuelven a la página 1). */
    private void servirUrlsDeSeccion(HttpServletRequest request, String sufijo) {
        request.setAttribute("urlPaginacion" + sufijo, urlBandejaCon(request, "pagina" + sufijo));
        request.setAttribute("urlOrden" + sufijo,
                urlBandejaCon(request, "orden" + sufijo, "sentido" + sufijo, "pagina" + sufijo));
    }

    /**
     * B183: cuántas filas por página. El mismo 20 que Clientes (B148), Solicitudes (B167) y
     * Aprobaciones (B175): si una pantalla paginara distinto, la primera pregunta de Joel sería
     * por qué. Vale para las 3 secciones, por lo mismo.
     */
    private static final int FILAS_POR_PAGINA = 20;

    /**
     * B183: corta la página de una de las 3 secciones y deja en el request su número de página, su
     * total de páginas y su <b>total de filas</b>.
     *
     * <p>El total es el del listado completo y no el de la página: con 96 solicitudes, un contador
     * que contara las filas dibujadas diría "20 encontradas" (el mismo cuidado que hubo que tener
     * en B167 y B175).</p>
     *
     * @param nombreParametroPagina el parámetro propio de esa sección ({@code paginaFormalizar},
     *     {@code paginaDesembolso}, {@code paginaPagare}): son tres listados en una sola pantalla,
     *     así que cada uno pagina por su cuenta y un solo {@code pagina} los movería a los tres
     */
    private <T> List<T> recortarPagina(HttpServletRequest request, List<T> ordenadas,
            String nombreParametroPagina, String atributoTotal, String atributoTotalPaginas) {
        int total = ordenadas.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) FILAS_POR_PAGINA));
        int pagina = paginaPedida(request.getParameter(nombreParametroPagina), totalPaginas);
        int desde = (pagina - 1) * FILAS_POR_PAGINA;
        int hasta = Math.min(total, desde + FILAS_POR_PAGINA);
        request.setAttribute(nombreParametroPagina, Integer.valueOf(pagina));
        request.setAttribute(atributoTotal, Integer.valueOf(total));
        request.setAttribute(atributoTotalPaginas, Integer.valueOf(totalPaginas));
        return ordenadas.subList(desde, hasta);
    }

    /**
     * B183: la página pedida, acotada a lo que existe. Fuera de rango (o basura) cae en 1 o en la
     * última: así un enlace viejo o un número tecleado a mano nunca deja la sección vacía sin
     * explicación. Misma forma que en B175.
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
     * B180: los filtros actuales de la bandeja como query string, para el "Volver a la bandeja"
     * de "Formalizar" y de la ficha. Esta pantalla tiene UN solo filtro (texto), que filtra las 3
     * secciones a la vez. Misma forma que construirRetornoLista de B163.
     *
     * <p><b>B183</b> le agrega el orden y la página <b>de las 3 secciones</b>: con la bandeja
     * paginada, volver de una ficha a la página 1 sería una forma nueva de perder el lugar, que es
     * justo lo que B180 vino a resolver (la misma ampliación que B175 le hizo a B170). Si no hay
     * ningún parámetro, devuelve cadena vacía y el enlace queda igual que siempre.</p>
     */
    private String construirRetornoBandeja(HttpServletRequest request) {
        StringBuilder retorno = new StringBuilder();
        retorno.append(queryExcepto(request));
        return retorno.toString();
    }

    /** B180: el "Volver a la bandeja" devuelve la búsqueda que el usuario tenía. Vacío si todavía
     * no pasó por la bandeja en esta sesión: entonces va a /formalizacion, como siempre. */
    private void servirRetornoBandeja(HttpServletRequest request) {
        Object retornoBandeja = request.getSession().getAttribute(RETORNO_BANDEJA_EN_SESION);
        request.setAttribute("urlVolverBandeja", retornoBandeja == null ? "" : retornoBandeja.toString());
    }

    private void mostrarNueva(HttpServletRequest request, HttpServletResponse response, long idSolicitud)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        servirRetornoBandeja(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, idSolicitud);
            boolean yaFormalizada = solicitud != null
                    && servicioFormalizacion.obtenerPorSolicitud(conexion, idSolicitud) != null;
            if (solicitud == null || solicitud.getEstadoSolicitud() != EstadoSolicitud.APROBADA || yaFormalizada) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            LocalDate propuesta = servicioFormalizacion.proponerPrimerVencimiento(Reloj.hoy());
            request.setAttribute("solicitud", solicitud);
            request.setAttribute("cliente", cliente);
            request.setAttribute("primerVencimientoPropuesto", propuesta);
            // Corrección 2 (Joel): rango concreto debajo del campo, y feriados para avisar en el
            // momento si la fecha elegida cae en día inhábil (RN-19).
            request.setAttribute("rangoFechas", servicioFormalizacion.calcularRangoPrimerVencimiento(conexion, Reloj.hoy()));
            request.setAttribute("feriados", servicioFormalizacion.listarFeriadosActivos(conexion));
            // RF11 (pedido de Joel, 11/10/2026): cuota más alta, total, CFT y afectación (con los
            // datos actuales del cliente) antes de confirmar.
            // F2 (21/10/2026, pedido de Joel, punto 7): mismo permiso que procesarGenerar/
            // procesarRegenerar -- el aviso "Comprobante de ingreso" de esta pantalla tiene que
            // decir lo mismo que el diálogo de error si se llega a confirmar el envío.
            boolean puedeResolverNueva = nivelAccesoSesion(request, Permiso.APROBAR_RECHAZAR_SOLICITUD) != NivelAcceso.NINGUNO;
            request.setAttribute("resumenFormalizacion", servicioFormalizacion.calcularResumenParaMostrar(conexion,
                    solicitud.getMontoAprobado(), solicitud.getPlazoAprobadoMeses(), solicitud.getTasaTeaAprobada(),
                    solicitud.getIdCliente(), puedeResolverNueva));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la solicitud.", error);
            request.setAttribute("mensajeError", "No se pudo leer la solicitud.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/vistas/formalizacion/nueva.jsp");
        dispatcher.forward(request, response);
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, long idPrestamo)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        servirRetornoBandeja(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            request.setAttribute("prestamo", prestamo);
            request.setAttribute("solicitud", solicitud);
            request.setAttribute("cliente", cliente);
            // RF14 (pedido de Joel, 12/10/2026): la franja de anulado tiene que decir quién y
            // cuándo, no solo el motivo -- mismo criterio que "nombreCierreDocumentalPor" arriba.
            if (prestamo.getAnuladoPor() != null) {
                Usuario anuladoPor = servicioUsuarios.buscarPorId(conexion, prestamo.getAnuladoPor());
                request.setAttribute("nombreAnuladoPor",
                        anuladoPor == null ? "-" : anuladoPor.getNombres() + " " + anuladoPor.getApellidos());
            }
            request.setAttribute("checklist", servicioFormalizacion.obtenerChecklist(conexion, idPrestamo));
            request.setAttribute("cuotas", servicioFormalizacion.listarCuotasVigentes(conexion, idPrestamo));
            // Tanda 2 (14/10/2026, pedido de Joel): para avisar cuando el plan vigente ya no es
            // el ORIGINAL (RF27 lo regeneró con un adelanto de capital).
            request.setAttribute("planVigente", servicioFormalizacion.obtenerPlanVigente(conexion, idPrestamo));
            // D-70: enlaces de descarga reales para los PDF ya generados (null = todavía no existe).
            request.setAttribute("documentosGenerados", servicioFormalizacion.obtenerDocumentosGeneradosVigentes(conexion, idPrestamo));
            // Corrección 2 (Joel): mismo rango/feriados que en "nueva", para el campo de regenerar.
            request.setAttribute("rangoFechas", servicioFormalizacion.calcularRangoPrimerVencimiento(conexion, Reloj.hoy()));
            request.setAttribute("feriados", servicioFormalizacion.listarFeriadosActivos(conexion));
            // RF11 (pedido de Joel, 11/10/2026): mismo resumen que en "nueva", para el campo de regenerar.
            // F2 (21/10/2026, pedido de Joel, punto 7): mismo permiso que en "nueva".
            boolean puedeResolverFicha = nivelAccesoSesion(request, Permiso.APROBAR_RECHAZAR_SOLICITUD) != NivelAcceso.NINGUNO;
            request.setAttribute("resumenFormalizacion", servicioFormalizacion.calcularResumenParaMostrar(conexion,
                    prestamo.getMontoAprobado(), prestamo.getPlazoMeses(), prestamo.getTasaTea(),
                    solicitud.getIdCliente(), puedeResolverFicha));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer el préstamo " + idPrestamo + ".", error);
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

    private LocalDate parsearFecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese la fecha del primer vencimiento.");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (RuntimeException error) {
            throw new NegocioException("\"" + texto + "\" no es una fecha válida.");
        }
    }

    /** F2 (21/10/2026, pedido de Joel, punto 7): mismo patrón que
     * ControladorClientesServlet.nivelAccesoSesion -- el permiso real en sesión, nunca el nombre
     * del rol, decide la versión (a)/(b) de los mensajes de bloqueo de generarDocumentos/
     * regenerarDocumentos/calcularResumenParaMostrar. */
    private NivelAcceso nivelAccesoSesion(HttpServletRequest request, Permiso permiso) {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        return usuario == null ? NivelAcceso.NINGUNO
                : servicioAutorizacion.nivelAcceso(usuario.getPerfilUsuario(), permiso);
    }
}
