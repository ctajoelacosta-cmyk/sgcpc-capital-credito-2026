package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
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
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.InfoCobroPrestamo;
import py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza;
import py.com.capitalcredito.sgcpc.dto.ListaTrabajoCobranza;
import py.com.capitalcredito.sgcpc.dto.ResultadoBusquedaCobranza;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.excepciones.CajaDeOtroDiaException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioCaja;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioCobranza;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioMora;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;
import py.com.capitalcredito.sgcpc.servicios.ServicioReportes;
import py.com.capitalcredito.sgcpc.servicios.ServicioReversiones;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * P09, cobranza (Doc. 10 §12: ControladorCobranzaServlet / ServicioCobranza). Protegido por
 * FiltroAutorizacion (Permiso.COBRAR, TOTAL solo para la cajera).
 */
public class ControladorCobranzaServlet extends HttpServlet {

    private static final String VISTA_BUSQUEDA = "/WEB-INF/vistas/cobranza/busqueda.jsp";
    // D-146 (lista de trabajo + búsqueda en vivo, pedido de Joel): fragmento de resultados --
    // mismo patrón "listado vivo" (D-136, ControladorClientesServlet) ya usado en /clientes:
    // incluido (<%@ include %>) dentro de busqueda.jsp en la carga normal, y reenviado tal cual
    // cuando PeticionAjax.esAjax(request).
    private static final String VISTA_RESULTADOS_BUSQUEDA = "/WEB-INF/vistas/cobranza/_resultados_busqueda.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/cobranza/ficha.jsp";
    private static final String VISTA_FRAGMENTO_ANTICIPO = "/WEB-INF/vistas/cobranza/fragmento_vista_previa_anticipo.jsp";
    /**
     * Decisión de Joel (27/10/2026): valor de {@code modoAnticipo} que identifica la opción "Por
     * cantidad de cuotas" (la otra, "Por monto", manda {@code "monto"}). Ver
     * {@link #recibidoNoAlcanzaParaLasCuotas}.
     */
    private static final String MODO_ANTICIPO_CUOTAS = "cuotas";

    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    // D-146: ServicioMora.listarPrestamosEnMora también se usa acá en construirInfoCobroPorPrestamo
    // (resultado de la búsqueda) y para la tarjeta "En mora" de la lista de trabajo.
    // ServicioReportes.listarVencimientos, para "Vencen hoy"/"Próximos 7 días" (RF32, también
    // usado por el informe de Vencimientos). La regla de "un préstamo EN_MORA aparece solo en
    // 'en mora'" (RF20, 13/10/2026) vive en ServicioCobranza.armarListaTrabajo -- acá solo se
    // trae cada lista y se la pasa (REGLAS_DESARROLLO.md §13 punto 4). ServicioCobranza no puede
    // tener ServicioMora/ServicioReportes como campo: los dos ya dependen de ServicioCobranza
    // (ciclo de construcción) -- por eso armarListaTrabajo es pura y recibe las 3 listas ya
    // traídas, en vez de buscarlas ella misma.
    private final ServicioMora servicioMora = new ServicioMora();
    private final ServicioReportes servicioReportes = new ServicioReportes();
    // C30 (03/11/2026): solo para preguntarle el aviso de la reversión pendiente (solo lectura).
    private final ServicioReversiones servicioReversiones = new ServicioReversiones();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarBusqueda(request, response);
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
        // D-145 (P-5, pedido de Joel): vista previa en vivo del adelanto, por AJAX (GET, sin
        // grabar nada) -- /cobranza/{idPrestamo}/vista-previa-anticipo?monto=X.
        if (segmentos.length == 2 && "vista-previa-anticipo".equals(segmentos[1])) {
            Long idPrestamoPreview = idDeTexto(segmentos[0]);
            if (idPrestamoPreview == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarVistaPreviaAnticipo(request, response, idPrestamoPreview);
            return;
        }
        // E8 (Bloque 2, D-94): reimpresión -- /cobranza/{idPrestamo}/recibo|factura|constancia/{id}.
        // idPrestamo solo ordena la URL (se llega desde la ficha de ESE préstamo); el id real del
        // documento es el tercer segmento, igual que en los otros servicios de E8.
        if (segmentos.length == 3 && idDeTexto(segmentos[0]) != null && idDeTexto(segmentos[2]) != null) {
            long idPrestamo = idDeTexto(segmentos[0]);
            long idDocumento = idDeTexto(segmentos[2]);
            Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
            if ("recibo".equals(segmentos[1])) {
                descargarPdf(request, response, idPrestamo, "recibo_" + idDocumento + ".pdf",
                        () -> servicioCobranza.obtenerPdfRecibo(idDocumento, solicitante.getIdUsuario()));
                return;
            }
            if ("factura".equals(segmentos[1])) {
                descargarPdf(request, response, idPrestamo, "factura_" + idDocumento + ".pdf",
                        () -> servicioCobranza.obtenerPdfFactura(idDocumento, solicitante.getIdUsuario()));
                return;
            }
            if ("constancia".equals(segmentos[1])) {
                descargarPdf(request, response, idPrestamo, "constancia_" + idDocumento + ".pdf",
                        () -> servicioCobranza.obtenerPdfConstancia(idDocumento, solicitante.getIdUsuario()));
                return;
            }
            // E9/Bloque 5: nota de anulación de una factura revertida -- idDocumento acá es
            // id_factura, mismo patrón que "factura" arriba.
            if ("nota-anulacion".equals(segmentos[1])) {
                descargarPdf(request, response, idPrestamo, "nota_anulacion_" + idDocumento + ".pdf",
                        () -> servicioCobranza.obtenerPdfNotaAnulacion(idDocumento, solicitante.getIdUsuario()));
                return;
            }
            // Punto 2 (RF25, pedido de Joel): constancia de una reversión autorizada -- idDocumento
            // acá es id_reversion_pago, mismo patrón que "nota-anulacion" arriba.
            if ("reversion".equals(segmentos[1])) {
                descargarPdf(request, response, idPrestamo, "constancia_reversion_" + idDocumento + ".pdf",
                        () -> servicioCobranza.obtenerPdfConstanciaReversion(idDocumento, solicitante.getIdUsuario()));
                return;
            }
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @FunctionalInterface
    private interface GeneradorPdfDescarga {
        byte[] generar() throws SQLException;
    }

    /**
     * E8 (Bloque 2): mismo Content-Disposition que ControladorDocumentosServlet -- el PDF se
     * descarga, nunca se sirve una ruta física del servidor. Si el servicio rechaza con un mensaje
     * claro (ej. "Este préstamo no tiene comprobante..."), vuelve a la ficha con ese mensaje en vez
     * de romper la descarga con un error genérico.
     */
    private void descargarPdf(HttpServletRequest request, HttpServletResponse response, long idPrestamo,
            String nombreArchivo, GeneradorPdfDescarga generador) throws IOException {
        HttpSession sesion = request.getSession();
        try {
            byte[] pdf = generador.generar();
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");
            try (OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
            response.sendRedirect(request.getContextPath() + "/cobranza/" + idPrestamo);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar un comprobante del préstamo "
                    + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo generar el comprobante. Intente nuevamente.");
            response.sendRedirect(request.getContextPath() + "/cobranza/" + idPrestamo);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String[] segmentos = pathInfo == null ? new String[0] : pathInfo.substring(1).split("/");

        if (segmentos.length == 2 && "cobrar".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            procesarCobrar(request, response, sesion, solicitante, idPrestamo);
            return;
        }
        if (segmentos.length == 2 && "cancelar".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            procesarCancelar(request, response, sesion, solicitante, idPrestamo);
            return;
        }
        if (segmentos.length == 2 && "anticipar".equals(segmentos[1])) {
            Long idPrestamo = idDeTexto(segmentos[0]);
            if (idPrestamo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            procesarAnticipar(request, response, sesion, solicitante, idPrestamo);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarCobrar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, long idPrestamo) throws IOException {
        String montoTexto = request.getParameter("montoRecibido");
        String fechaPromesaTexto = request.getParameter("fechaPromesa");
        EstadoPrestamo estadoResultante = null;
        try {
            String identificadorConfirmacion = request.getParameter("identificadorConfirmacion");
            BigDecimal montoRecibido = parsearMonto(montoTexto);
            LocalDate fechaPromesa = (fechaPromesaTexto == null || fechaPromesaTexto.trim().isEmpty()) ? null
                    : LocalDate.parse(fechaPromesaTexto);
            // D-145 (ajuste de Joel: se saca "dejar el vuelto para la próxima cuota" y la llamada
            // en dos pasos -- el vuelto de "Cobrar" siempre se devuelve). Una sola llamada a
            // confirmarCobro, exactamente como antes de ese intento.
            Pago pago = servicioCobranza.confirmarCobro(idPrestamo, identificadorConfirmacion, montoRecibido,
                    fechaPromesa, Reloj.hoy(), solicitante.getIdUsuario());
            estadoResultante = pago.getEstadoPrestamoResultante();
            String mensaje = "Cobro de " + FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()) + " aplicado.";
            if (pago.getMontoVuelto().compareTo(BigDecimal.ZERO) > 0) {
                // D-86: comoGuaranies ya termina en "Gs." -- agregar otro "." duplica el punto.
                mensaje += " Vuelto: " + FormateadorNumeros.comoGuaranies(pago.getMontoVuelto());
            }
            mensaje += mensajeRedondeo(pago);
            // D-144 (pedido de Joel): si el vuelto es más que lo que el redondeo solo ya explica,
            // el cliente entregó de más a propósito -- se le sugiere "Adelantar a capital" para
            // ese excedente, como una operación aparte (D-145: ya no hay combinación automática).
            if (pago.getMontoVuelto().compareTo(pago.getMontoRedondeo()) > 0) {
                mensaje += " Si el cliente quiere adelantar ese excedente, use \"Adelantar a capital\".";
            }
            mensaje += " Recibo " + pago.getNumeroRecibo() + ". " + mensajeFactura(pago);
            MensajesFlash.guardarExito(sesion, mensaje, imprimiblesDePago(idPrestamo, pago));
        } catch (CajaDeOtroDiaException error) {
            // A.8 (consolidación, pedido de Joel): bloqueo de tiempo -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
            guardarValoresCobro(sesion, montoTexto, fechaPromesaTexto);
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
            guardarValoresCobro(sesion, montoTexto, fechaPromesaTexto);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cobrar del préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
            guardarValoresCobro(sesion, montoTexto, fechaPromesaTexto);
        }
        redirigirTrasOperacion(request, response, idPrestamo, estadoResultante);
    }

    private void procesarCancelar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, long idPrestamo) throws IOException {
        EstadoPrestamo estadoResultante = null;
        try {
            String identificadorConfirmacion = request.getParameter("identificadorConfirmacion");
            BigDecimal montoRecibido = parsearMonto(request.getParameter("montoRecibido"));
            Pago pago = servicioCobranza.confirmarCancelacionTotal(idPrestamo, identificadorConfirmacion, montoRecibido,
                    Reloj.hoy(), solicitante.getIdUsuario());
            estadoResultante = pago.getEstadoPrestamoResultante();
            String mensaje = "Cancelación total aplicada. Total aplicado "
                    + FormateadorNumeros.comoGuaranies(pago.getMontoAplicado());
            if (pago.getMontoVuelto().compareTo(BigDecimal.ZERO) > 0) {
                // D-86: comoGuaranies ya termina en "Gs." -- agregar otro "." duplica el punto.
                mensaje += " Vuelto: " + FormateadorNumeros.comoGuaranies(pago.getMontoVuelto());
            }
            mensaje += mensajeRedondeo(pago);
            mensaje += " Recibo " + pago.getNumeroRecibo() + ". " + mensajeFactura(pago);
            MensajesFlash.guardarExito(sesion, mensaje, imprimiblesDePago(idPrestamo, pago));
        } catch (CajaDeOtroDiaException error) {
            // A.8 (consolidación, pedido de Joel): bloqueo de tiempo -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cancelar el préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        redirigirTrasOperacion(request, response, idPrestamo, estadoResultante);
    }

    private void procesarAnticipar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante, long idPrestamo) throws IOException {
        EstadoPrestamo estadoResultante = null;
        try {
            String identificadorConfirmacion = request.getParameter("identificadorConfirmacion");
            BigDecimal montoRecibido = parsearMonto(request.getParameter("montoRecibido"));
            // D-145 (P-5, pedido de Joel): el cliente puede adelantar menos que la cuota sugerida
            // -- lo que de verdad se aplica es el menor entre lo recibido y la cuota sugerida
            // (nunca más que esa cuota; adelantar varias cuotas sigue siendo otra operación). Esto
            // se valida acá, no solo en cobranza-ficha.js, para no depender de que nadie manipule
            // el formulario; confirmarAnticipoCapital (sin cambios) sigue validando por su cuenta
            // el mínimo de interés + IVA del período.
            String montoSugeridoTexto = request.getParameter("montoSugerido");
            BigDecimal montoAplicado = montoRecibido;
            if (montoSugeridoTexto != null && !montoSugeridoTexto.trim().isEmpty()) {
                BigDecimal montoSugerido = parsearMonto(montoSugeridoTexto);
                // Decisión de Joel (27/10/2026, tras la tercera prueba en pantalla): en "Por
                // cantidad de cuotas" el monto de la tarjeta ES lo que se cobra -- si lo recibido
                // no alcanza, se rechaza la operación completa en vez de aplicar menos en
                // silencio (la tarjeta diría "Paga 3 cuotas" y el cobro sería de otra cantidad).
                // El min() de D-145 (P-5) sigue intacto para "Por monto", que es donde la cajera
                // aplica un monto libre. La pantalla ya lo bloquea antes; esto es el control del
                // servidor, para no depender de que nadie manipule el formulario.
                if (recibidoNoAlcanzaParaLasCuotas(request.getParameter("modoAnticipo"), montoRecibido,
                        montoSugerido)) {
                    throw new NegocioException(mensajeNoAlcanzaParaLasCuotas(montoRecibido, montoSugerido));
                }
                montoAplicado = montoRecibido.min(montoSugerido);
            }
            // D-146 (pedido de Joel, autorizado a tocar el servicio): confirmarAnticipoCapital
            // ahora recibe lo recibido y lo aplicado por separado -- guarda y registra en caja el
            // vuelto real, igual que confirmarCobro.
            Pago pago = servicioCobranza.confirmarAnticipoCapital(idPrestamo, identificadorConfirmacion, montoRecibido,
                    montoAplicado, Reloj.hoy(), solicitante.getIdUsuario());
            estadoResultante = pago.getEstadoPrestamoResultante();
            String mensaje = "Anticipo de capital aplicado (" + FormateadorNumeros.comoGuaranies(pago.getMontoAplicado())
                    + ").";
            if (pago.getMontoVuelto().compareTo(BigDecimal.ZERO) > 0) {
                mensaje += " Vuelto: " + FormateadorNumeros.comoGuaranies(pago.getMontoVuelto());
            }
            mensaje += " Recibo " + pago.getNumeroRecibo() + ". " + mensajeFactura(pago);
            MensajesFlash.guardarExito(sesion, mensaje, imprimiblesDePago(idPrestamo, pago));
        } catch (CajaDeOtroDiaException error) {
            // A.8 (consolidación, pedido de Joel): bloqueo de tiempo -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al adelantar capital del préstamo " + idPrestamo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        redirigirTrasOperacion(request, response, idPrestamo, estadoResultante);
    }

    /**
     * Bloque 7/E10 (D-131, pedido de Joel): botones del modal de éxito -- recibo siempre, factura
     * si se emitió. D-139 (pedido de Joel): también la constancia de cancelación (CAN-...) cuando
     * el pago es una cancelación total -- antes el modal solo ofrecía el recibo, y había que ir a
     * buscar la constancia aparte en el historial.
     */
    private Map<String, String> imprimiblesDePago(long idPrestamo, Pago pago) {
        Map<String, String> imprimibles = new java.util.LinkedHashMap<>();
        imprimibles.put("Imprimir recibo", "/cobranza/" + idPrestamo + "/recibo/" + pago.getIdPago());
        if (pago.getFacturaEmitida() != null) {
            imprimibles.put("Imprimir factura", "/cobranza/" + idPrestamo + "/factura/" + pago.getFacturaEmitida().getIdFactura());
        }
        if (pago.getTipo() == py.com.capitalcredito.sgcpc.enumeraciones.TipoPago.CANCELACION
                && pago.getIdLiquidacionAnticipada() != null) {
            imprimibles.put("Imprimir constancia de cancelación",
                    "/cobranza/" + idPrestamo + "/constancia/" + pago.getIdLiquidacionAnticipada());
        }
        return imprimibles;
    }

    /** PA-12: número de factura si se emitió una, o el motivo claro de por qué no. */
    private String mensajeFactura(Pago pago) {
        return pago.getFacturaEmitida() != null
                ? "Factura " + pago.getFacturaEmitida().getNumeroCompleto() + "."
                : "Sin factura (no hay conceptos gravados).";
    }

    /** D-142 (corrección de Joel sobre D-141): el efectivo realmente cobrado, cuando hubo redondeo. */
    private String mensajeRedondeo(Pago pago) {
        if (pago.getMontoRedondeo().compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        BigDecimal cobradoEnEfectivo = pago.getMontoRecibido().subtract(pago.getMontoVuelto());
        return " Cobrado en efectivo: " + FormateadorNumeros.comoGuaranies(cobradoEnEfectivo)
                + " (redondeo a favor del cliente: " + FormateadorNumeros.comoGuaranies(pago.getMontoRedondeo()) + ")";
    }

    /**
     * D-89 (corrección Bloque 7/E10, D-130, pedido de Joel): antes, tras una cancelación total (o
     * un cobro que salda la última cuota), el préstamo queda FINALIZADO y esto volvía a la
     * búsqueda en vez de la ficha -- una prevención de D-89 que quedó vencida: desde el historial
     * de cobros con reimpresión de E8 (Bloque 2), /cobranza/{id} sí sigue existiendo para un
     * préstamo FINALIZADO (mostrarFicha ya lo permite explícitamente). Volver siempre a la misma
     * ficha deja el recibo y la factura del cobro recién hecho -- también el que finaliza el
     * préstamo -- para imprimir en la misma pantalla, en el momento (mismo criterio que el
     * comprobante de desembolso).
     */
    private void redirigirTrasOperacion(HttpServletRequest request, HttpServletResponse response, long idPrestamo,
            EstadoPrestamo estadoResultante) throws IOException {
        response.sendRedirect(request.getContextPath() + "/cobranza/" + idPrestamo);
    }

    /** D-89: para que el formulario de cobro no se vacíe si el servicio lo rechaza (PRG). */
    private void guardarValoresCobro(HttpSession sesion, String montoTexto, String fechaPromesaTexto) {
        Map<String, String> valores = new HashMap<>();
        valores.put("montoRecibido", montoTexto == null ? "" : montoTexto);
        valores.put("fechaPromesa", fechaPromesaTexto == null ? "" : fechaPromesaTexto);
        MensajesFlash.guardarValores(sesion, valores);
    }

    private void mostrarBusqueda(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        String textoBusqueda = request.getParameter("q");
        request.setAttribute("textoBusqueda", textoBusqueda);
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // B193 (15/11/2026): el estado de la caja también en la LISTA DE TRABAJO, no solo en la
            // ficha. Sin esto la pantalla ofrece un botón "Cobrar" por fila como si se pudiera, y el
            // bloqueo aparece un clic después (ficha.jsp:265-270) -- el criterio de B88/B89/B100, y
            // exactamente lo que B186 resolvió en la bandeja de desembolso. Es lo mismo que ya hace
            // mostrarFicha, con el MISMO dueño del texto (ServicioCaja.mensajeCajaDeOtroDia):
            // ningún mensaje nuevo.
            SesionCaja sesionAbiertaBusqueda = servicioCaja.obtenerSesionAbierta(conexion);
            request.setAttribute("sesionAbierta", sesionAbiertaBusqueda);
            if (sesionAbiertaBusqueda != null && !servicioCaja.esDeHoy(sesionAbiertaBusqueda)) {
                request.setAttribute("mensajeCajaOtroDia", servicioCaja.mensajeCajaDeOtroDia(sesionAbiertaBusqueda));
            }
            if (textoBusqueda != null && !textoBusqueda.trim().isEmpty()) {
                // D-146 (búsqueda en vivo, pedido de Joel): un solo campo, por cédula (con o sin
                // puntos), nombre/apellido o número de operación -- ServicioCobranza.buscarParaCobranza
                // ya resuelve también el mensaje claro cuando no hay nada cobrable. RF20 (13/10/2026):
                // el perfil decide si corresponde el enlace a la ficha de Formalización.
                ResultadoBusquedaCobranza resultadoBusqueda = servicioCobranza.buscarParaCobranza(conexion,
                        textoBusqueda, solicitante.getPerfilUsuario());
                request.setAttribute("resultadoBusqueda", resultadoBusqueda);
                request.setAttribute("infoCobroPorPrestamo",
                        construirInfoCobroPorPrestamo(conexion, resultadoBusqueda.getPrestamos()));
            } else {
                // D-146 (lista de trabajo al entrar, pedido de Joel): Vencen hoy / En mora /
                // Próximos 7 días -- mismas consultas de siempre (ServicioMora/ServicioReportes).
                // RF20 (13/10/2026): ServicioCobranza.armarListaTrabajo aplica la Decisión 2 del
                // análisis (un préstamo EN_MORA no se repite en las otras dos tarjetas) -- la
                // regla vive ahí, el controlador solo trae las 3 listas y se las pasa.
                LocalDate hoy = Reloj.hoy();
                List<FilaMora> enMora = servicioMora.listarPrestamosEnMora(conexion);
                List<FilaVencimiento> vencenHoyCrudo = servicioReportes.listarVencimientos(conexion, hoy, hoy);
                List<FilaVencimiento> proximos7DiasCrudo = servicioReportes.listarVencimientos(conexion,
                        hoy.plusDays(1), hoy.plusDays(7));
                ListaTrabajoCobranza listaTrabajo = servicioCobranza.armarListaTrabajo(enMora, vencenHoyCrudo,
                        proximos7DiasCrudo);
                // B197 (15/11/2026): el bloque "En mora" es el unico grande de los tres (hoy 69
                // filas contra 1 y 0), asi que es el unico que pagina. Esta pantalla tiene UNA
                // sola paginacion, asi que usa el parametro "pagina" de siempre y pasa por
                // listado-vivo.js, igual que Clientes/Solicitudes/Aprobaciones.
                List<py.com.capitalcredito.sgcpc.dto.FilaMora> enMoraCompleto = listaTrabajo.getEnMora();
                int totalEnMora = enMoraCompleto == null ? 0 : enMoraCompleto.size();
                int totalPaginasEnMora = Math.max(1, (int) Math.ceil(totalEnMora / (double) FILAS_POR_PAGINA));
                int paginaEnMora = paginaPedida(request.getParameter("pagina"), totalPaginasEnMora);
                int desdeEnMora = (paginaEnMora - 1) * FILAS_POR_PAGINA;
                int hastaEnMora = Math.min(totalEnMora, desdeEnMora + FILAS_POR_PAGINA);
                request.setAttribute("trabajoEnMora",
                        enMoraCompleto == null ? null : enMoraCompleto.subList(desdeEnMora, hastaEnMora));
                // El contador de la pildora cuenta el TOTAL, no las filas de la pagina.
                request.setAttribute("totalEnMora", Integer.valueOf(totalEnMora));
                request.setAttribute("pagina", Integer.valueOf(paginaEnMora));
                request.setAttribute("totalPaginas", Integer.valueOf(totalPaginasEnMora));
                request.setAttribute("trabajoVencenHoy", listaTrabajo.getVencenHoy());
                request.setAttribute("trabajoProximos7Dias", listaTrabajo.getProximos7Dias());
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al buscar para cobranza.", error);
            request.setAttribute("mensajeError", "No se pudo completar la búsqueda.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_BUSQUEDA : VISTA_BUSQUEDA);
        dispatcher.forward(request, response);
    }

    /**
     * D-146 (A1b, resultado de la búsqueda de Cobranza, pedido de Joel): próxima cuota (N/M) +
     * vencimiento, monto a cobrar hoy (lo exigible si tiene atraso, o la próxima cuota si está
     * al día) y días de atraso -- sin cálculo nuevo: para los EN_MORA reusa tal cual
     * ServicioMora.listarPrestamosEnMora (lista de trabajo, ya con la cuota más antigua y el
     * exigible); para los ACTIVO (al día), ServicioCobranza.obtenerProximaCuotaPendiente (lo
     * mismo que calcula la ficha para "Adelantar a capital").
     */
    private Map<Long, InfoCobroPrestamo> construirInfoCobroPorPrestamo(Connection conexion,
            List<PrestamoCredito> prestamos) throws SQLException {
        Map<Long, InfoCobroPrestamo> infoPorPrestamo = new HashMap<>();
        boolean hayEnMora = false;
        for (PrestamoCredito prestamo : prestamos) {
            if (prestamo.getEstadoPrestamo() == EstadoPrestamo.EN_MORA) {
                hayEnMora = true;
                break;
            }
        }
        Map<Long, FilaMora> filaMoraPorPrestamo = new HashMap<>();
        if (hayEnMora) {
            for (FilaMora fila : servicioMora.listarPrestamosEnMora(conexion)) {
                filaMoraPorPrestamo.put(fila.getIdPrestamo(), fila);
            }
        }
        for (PrestamoCredito prestamo : prestamos) {
            FilaMora filaMora = filaMoraPorPrestamo.get(prestamo.getIdPrestamo());
            if (filaMora != null) {
                infoPorPrestamo.put(prestamo.getIdPrestamo(),
                        new InfoCobroPrestamo(filaMora.getNumeroCuotaMasAntigua() + "/" + filaMora.getTotalCuotas(),
                                filaMora.getFechaVencimientoMasAntigua(), filaMora.getMontoExigible(), false,
                                filaMora.getDiasAtraso()));
                continue;
            }
            Cuota proxima = servicioCobranza.obtenerProximaCuotaPendiente(conexion, prestamo.getIdPrestamo());
            int totalCuotas = servicioCobranza.cargarPrestamoParaMotor(conexion, prestamo.getIdPrestamo())
                    .getCuotas().size();
            String proximaCuotaTexto = proxima == null ? "-" : proxima.getNumeroCuota() + "/" + totalCuotas;
            String proximoVencimiento = proxima == null ? "-" : FormateadorFechas.comoDdMmAaaa(proxima.getFechaVencimiento());
            String aCobrarHoy = proxima == null ? FormateadorNumeros.comoGuaranies(BigDecimal.ZERO)
                    : FormateadorNumeros.comoGuaranies(proxima.getTotalProgramado());
            infoPorPrestamo.put(prestamo.getIdPrestamo(),
                    new InfoCobroPrestamo(proximaCuotaTexto, proximoVencimiento, aCobrarHoy, true, 0));
        }
        return infoPorPrestamo;
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, long idPrestamo)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            LocalDate hoy = Reloj.hoy();
            servicioCobranza.recalcularEstadoPrestamo(conexion, idPrestamo, hoy);

            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamo);
            if (prestamo == null || (prestamo.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                    && prestamo.getEstadoPrestamo() != EstadoPrestamo.EN_MORA
                    && prestamo.getEstadoPrestamo() != EstadoPrestamo.FINALIZADO)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            request.setAttribute("prestamo", prestamo);
            request.setAttribute("cliente", cliente);
            // E8 (Bloque 2): historial de cobros y reimpresión -- se muestra siempre, incluso con
            // el préstamo ya FINALIZADO (ej. reimprimir la constancia de una cancelación de hace
            // semanas). Solo los formularios de cobro/cancelación/anticipo de abajo son exclusivos
            // de ACTIVO/EN_MORA.
            // B197 (15/11/2026): el historial tambien pagina. Es la tabla con mas acciones por
            // fila del sistema y crece con cada cobro y cada reversion. La pagina 1 sigue siendo
            // la mas nueva, que es donde vive el boton de reversion de B192 (solo la ultima
            // operacion lo ofrece): paginar no puede esconderlo.
            List<FilaHistorialCobranza> historialCompleto =
                    servicioCobranza.listarHistorialCobros(conexion, idPrestamo);
            int totalHistorial = historialCompleto.size();
            int totalPaginasHistorial = Math.max(1, (int) Math.ceil(totalHistorial / (double) FILAS_POR_PAGINA));
            int paginaHistorial = paginaPedida(request.getParameter("paginaHistorial"), totalPaginasHistorial);
            int desdeHistorial = (paginaHistorial - 1) * FILAS_POR_PAGINA;
            int hastaHistorial = Math.min(totalHistorial, desdeHistorial + FILAS_POR_PAGINA);
            request.setAttribute("historialCobros", historialCompleto.subList(desdeHistorial, hastaHistorial));
            request.setAttribute("totalHistorial", Integer.valueOf(totalHistorial));
            request.setAttribute("paginaHistorial", Integer.valueOf(paginaHistorial));
            request.setAttribute("totalPaginasHistorial", Integer.valueOf(totalPaginasHistorial));
            boolean prestamoFinalizado = prestamo.getEstadoPrestamo() == EstadoPrestamo.FINALIZADO;
            request.setAttribute("prestamoFinalizado", prestamoFinalizado);
            // E9/Bloque 5: el botón "Solicitar reversión" vive en el historial de arriba, visible
            // incluso con el préstamo FINALIZADO (revertir una cancelación total es justamente lo
            // que lo saca de FINALIZADO) -- por eso se genera ANTES del corte de más abajo.
            request.setAttribute("identificadorConfirmacionReversion", UUID.randomUUID().toString());
            // C30 (decisión de Joel, 03/11/2026): avisar, no bloquear. Con una reversión esperando
            // autorización, confirmar cualquiera de los tres cobros la deja imposible de autorizar
            // (autorizarReversion revalida "es la última operación"). El texto lo decide
            // ServicioReversiones, dueño del concepto; la ficha solo lo lleva al cartel.
            request.setAttribute("avisoReversionPendiente",
                    servicioReversiones.avisoDeReversionPendiente(conexion, idPrestamo));
            if (prestamoFinalizado) {
                RequestDispatcher dispatcherFinalizado = request.getRequestDispatcher(VISTA_FICHA);
                dispatcherFinalizado.forward(request, response);
                return;
            }

            // Bloque 6 (PA-13): aviso previo (números de factura y/o vigencia del timbrado por
            // agotarse), visible justo antes del formulario de cobro -- nunca bloquea nada.
            request.setAttribute("avisosTimbrado", servicioCobranza.obtenerAvisosTimbrado(conexion, hoy));

            ResultadoDeudaExigible exigible = servicioCobranza.calcularDeudaExigible(conexion, idPrestamo, hoy);
            request.setAttribute("exigible", exigible);
            BigDecimal montoCobrableNormal = servicioCobranza.calcularMontoCobrableNormal(conexion, idPrestamo, hoy);
            request.setAttribute("montoCobrableNormal", montoCobrableNormal);
            // D-144 (corrección de Joel sobre D-140): "Cobrar" nunca toca una cuota futura (ver
            // ServicioMotorFinanciero.distribuirPago) -- solo tiene algo para cobrar si lo vencido
            // y/o la cuota que vence justo hoy suman más de cero.
            boolean hayAlgoParaCobrar = montoCobrableNormal.compareTo(BigDecimal.ZERO) > 0;
            request.setAttribute("hayAlgoParaCobrar", hayAlgoParaCobrar);

            ResultadoLiquidacion previewCancelacion = servicioCobranza.calcularPreviewCancelacionTotal(conexion,
                    idPrestamo, hoy);
            request.setAttribute("previewCancelacion", previewCancelacion);

            int redondeoEfectivo = servicioParametros.obtenerValorEntero(conexion, "credito.redondeo_efectivo");
            request.setAttribute("redondeoEfectivo", redondeoEfectivo);
            // D-145 (pedido de Joel, P-2): cada botón muestra el monto exacto y, si el redondeo lo
            // cambia, el monto "en efectivo" (piso al múltiplo del parámetro, D-141) debajo, más
            // chico -- las dos son solo para mostrar; lo que de verdad se cobra lo decide cada
            // servicio como siempre (confirmarCobro/confirmarCancelacionTotal, sin cambios).
            request.setAttribute("montoCobrableRedondeado", redondearHaciaAbajo(montoCobrableNormal, redondeoEfectivo));
            request.setAttribute("montoCancelarRedondeado",
                    redondearHaciaAbajo(previewCancelacion.getMontoTotalLiquidacion(), redondeoEfectivo));

            boolean sinVencidas = exigible.getTotalGeneral().compareTo(BigDecimal.ZERO) == 0;
            request.setAttribute("sinVencidas", sinVencidas);
            // D-145 (pantalla única, pedido de Joel): cuando no hay nada vencido, la primera
            // opción va por "Adelantar capital", nunca con lo que la cajera escriba en "Recibido"
            // (eso sirve solo para el vuelto en pantalla).
            //
            // Corrección de diseño del 27/10/2026 (prueba en pantalla de Joel, opción B): la
            // cantidad de cuotas la elige la cajera entre 1 y K (las que le quedan), y el monto
            // de N cuotas YA NO es "N × la cuota" -- es el monto MÍNIMO que deja exactamente
            // K − N cuotas en el plan nuevo (ServicioCobranza.calcularMontoMinimoParaDejarCuotas,
            // que no agrega ninguna fórmula: busca con el cálculo del motor que ya existe). El
            // "N × cuota" de D-145 (P-7) confundía a la cajera y al cliente, porque el adelanto
            // no cobra intereses futuros (RN-11) y rinde más que la suma de las cuotas: pagar 4
            // de 12 dejaba 7, no 8. Acá se arranca en N = 1.
            if (sinVencidas) {
                Cuota proximaCuota = servicioCobranza.obtenerProximaCuotaPendiente(conexion, idPrestamo);
                if (proximaCuota != null) {
                    int cuotasQueQuedan = contarCuotasPendientes(
                            servicioCobranza.cargarPrestamoParaMotor(conexion, idPrestamo));
                    request.setAttribute("cuotasPendientes", cuotasQueQuedan);
                    try {
                        // N = 1: el monto mínimo que deja K − 1 cuotas. null = no existe un monto
                        // que deje esa cantidad exacta (ej. a un préstamo le queda una sola cuota
                        // y lo único posible es "Todo el préstamo").
                        BigDecimal montoUnaCuota = servicioCobranza.calcularMontoMinimoParaDejarCuotas(conexion,
                                idPrestamo, hoy, cuotasQueQuedan - 1, redondeoEfectivo);
                        if (montoUnaCuota == null) {
                            request.setAttribute("anticipoBloqueadoMensaje",
                                    "con lo que queda del préstamo, la única opción es pagarlo todo.");
                        } else {
                            ResultadoLiquidacion previewAnticipo = servicioCobranza.calcularPreviewAnticipoParcial(
                                    conexion, idPrestamo, hoy, montoUnaCuota);
                            // Un solo monto, ya múltiplo de credito.redondeo_efectivo (aprobado
                            // por Joel el 27/10/2026): acá el monto no es un precio con descuento
                            // sino el mínimo que logra sacar esas cuotas del plan, así que no va
                            // el par "exacto / en efectivo" de D-145 (P-2) -- ese par sigue tal
                            // cual en "Pagar lo vencido" y en "Todo el préstamo".
                            request.setAttribute("montoAnticipoCuotas", montoUnaCuota);
                            request.setAttribute("ahorroAnticipoCuotas",
                                    ahorroDeInteresesPorCuotas(proximaCuota.getTotalProgramado(), 1, montoUnaCuota));
                            request.setAttribute("previewAnticipo", previewAnticipo);
                            // D-145 (P-5, pedido de Joel): el mínimo que ya exige
                            // confirmarAnticipoCapital (interés devengado + IVA del período, sin
                            // tocar el servicio) -- no depende del monto, solo de la fecha, así
                            // que es el mismo mientras la cajera escribe distintos montos; se usa
                            // para el aviso en rojo en cobranza-ficha.js sin ir y volver al
                            // servidor por cada tecla.
                            request.setAttribute("montoAnticipoMinimo", previewAnticipo.getInteresDevengado()
                                    .add(previewAnticipo.getIvaInteresDevengado()));
                        }
                    } catch (NegocioException error) {
                        // D-88 (hay una cuota con fecha teórica ya pasada) o D-90: no se ofrece
                        // adelantar, con el motivo exacto del servicio -- con los nombres de los
                        // botones de ESTA pantalla (corrección de Joel, 27/10/2026).
                        request.setAttribute("anticipoBloqueadoMensaje",
                                conNombresDeEstaPantalla(error.getMessage()));
                    }
                }
            }

            // B98 (30/10/2026, hallazgo y decision de Joel): la pantalla necesita distinguir
            // "hay algo vencido" de "hay algo cobrable hoy" -- con una cuota que vence HOY no hay
            // nada vencido (isBefore) pero si hay algo que cobrar (D-144). La decision la toma el
            // dueno; aca solo se le pasan los dos datos que ya estaban calculados, y la etiqueta
            // "N/M" de la cuota del dia para los textos que la nombran.
            ServicioCobranza.SituacionCobroHoy situacionCobroHoy =
                    ServicioCobranza.situacionCobroHoy(hayAlgoParaCobrar, !sinVencidas);
            request.setAttribute("situacionCobroHoy", situacionCobroHoy);
            if (situacionCobroHoy == ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY) {
                Cuota cuotaDeHoy = servicioCobranza.obtenerProximaCuotaPendiente(conexion, idPrestamo);
                if (cuotaDeHoy != null) {
                    request.setAttribute("etiquetaCuotaDeHoy", ServicioCobranza.etiquetaNumeroCuota(
                            cuotaDeHoy.getNumeroCuota(),
                            servicioCobranza.cargarPrestamoParaMotor(conexion, idPrestamo).getCuotas().size()));
                }
            }

            SesionCaja sesionAbierta = servicioCaja.obtenerSesionAbierta(conexion);
            request.setAttribute("sesionAbierta", sesionAbierta);
            boolean cajaLista = sesionAbierta != null && servicioCaja.esDeHoy(sesionAbierta);
            request.setAttribute("cajaLista", cajaLista);
            // Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto que Caja/Desembolso.
            if (sesionAbierta != null && !cajaLista) {
                request.setAttribute("mensajeCajaOtroDia", servicioCaja.mensajeCajaDeOtroDia(sesionAbierta));
            }

            // Un identificador distinto por formulario -- si compartieran uno solo, la idempotencia
            // podría emparejar por error la confirmación de una acción con otra (ej. reenviar el
            // formulario de cobro con la clave que en realidad correspondía a una cancelación).
            request.setAttribute("identificadorConfirmacionCobro", UUID.randomUUID().toString());
            request.setAttribute("identificadorConfirmacionCancelacion", UUID.randomUUID().toString());
            request.setAttribute("identificadorConfirmacionAnticipo", UUID.randomUUID().toString());
        } catch (NegocioException error) {
            request.removeAttribute("prestamo");
            request.setAttribute("mensajeError", error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la cobranza del préstamo " + idPrestamo + ".",
                    error);
            request.removeAttribute("prestamo");
            request.setAttribute("mensajeError", "No se pudo leer el préstamo.");
        } catch (RuntimeException error) {
            // D-88: el motor financiero puede lanzar un error de programación (ej. NPE, D-87) que
            // ningún NegocioException/SQLException atrapa -- sin este catch, sube crudo hasta el
            // error-500.jsp genérico ("Ocurrió un error inesperado"), sin loguear nada útil ni dar
            // una pista a la cajera. La ficha nunca debe terminar en un 500: se loguea el detalle
            // real para el administrador y se muestra un mensaje claro en pantalla.
            getServletContext().log("No se pudo calcular la deuda de la operación " + idPrestamo + ".", error);
            request.removeAttribute("prestamo");
            request.setAttribute("mensajeError",
                    "No se pudo calcular la deuda de esta operación; avise al administrador.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }

    /**
     * D-145 (P-5, pedido de Joel): vista previa en vivo del adelanto, por AJAX (GET, sin grabar
     * nada) -- reusa calcularPreviewAnticipoParcial tal cual (sin tocar el servicio).
     * <p>
     * Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel): admite las dos formas de
     * la pregunta "¿Cuánto quiere pagar el cliente?" --
     * <ul>
     * <li><b>{@code ?cuotas=N}</b> (por cantidad de cuotas): el MONTO lo arma el servidor, nunca
     * el navegador (REGLAS_DESARROLLO.md §5). Corrección de diseño del 27/10/2026 (opción B,
     * tras la prueba en pantalla de Joel): es el monto mínimo que deja exactamente K − N cuotas
     * ({@link ServicioCobranza#calcularMontoMinimoParaDejarCuotas}), ya múltiplo de
     * {@code credito.redondeo_efectivo} -- no "N × la cuota", que dejaba menos cuotas de las que
     * la cajera decía y no se podía explicar al cliente.</li>
     * <li><b>{@code ?monto=X}</b> (por monto): el monto que la cajera escribió, como antes.</li>
     * </ul>
     * El servidor VALIDA el parámetro antes de calcular nada (condición de Joel, 27/10/2026): si
     * {@code cuotas} no es un número, es menor que 1 o pasa las cuotas que le quedan, o si
     * {@code monto} no es un número positivo, responde el mensaje de bloqueo y NO llama al motor
     * con un valor inválido. Lo mismo con los rechazos que ya produce el servicio (mínimo de
     * interés del período, D-88, D-90): su texto exacto viaja al navegador en vez de perderse en
     * una respuesta vacía, que dejaba a la cajera sin saber por qué no podía cobrar.
     */
    private void mostrarVistaPreviaAnticipo(HttpServletRequest request, HttpServletResponse response, long idPrestamo)
            throws IOException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            LocalDate hoy = Reloj.hoy();
            int redondeoEfectivo = servicioParametros.obtenerValorEntero(conexion, "credito.redondeo_efectivo");
            BigDecimal monto;
            BigDecimal ahorro = null;
            String cuotasTexto = request.getParameter("cuotas");
            if (cuotasTexto != null && !cuotasTexto.trim().isEmpty()) {
                Cuota proximaCuota = servicioCobranza.obtenerProximaCuotaPendiente(conexion, idPrestamo);
                if (proximaCuota == null) {
                    responderBloqueoAnticipo(request, response, idPrestamo,
                            "Este préstamo no tiene cuotas pendientes.");
                    return;
                }
                int cuotasQueQuedan = contarCuotasPendientes(
                        servicioCobranza.cargarPrestamoParaMotor(conexion, idPrestamo));
                Integer cuotas = cuotasAnticipoValidas(cuotasTexto, cuotasQueQuedan);
                if (cuotas == null) {
                    responderBloqueoAnticipo(request, response, idPrestamo,
                            "Elija entre 1 y " + cuotasQueQuedan
                                    + (cuotasQueQuedan == 1 ? " cuota." : " cuotas."));
                    return;
                }
                if (cuotas.intValue() >= cuotasQueQuedan) {
                    // N = K: pagar todas las cuotas que quedan no es un adelanto, es la
                    // cancelación total (RF26/RF28). cobranza-ficha.js ya cambia solo a ese flujo
                    // sin pedir esta vista previa; este mensaje es para el caso de que alguien
                    // llame igual a la ruta.
                    // B106 (05/11/2026): el nombre de la opción sale de su único dueño
                    // (ServicioCobranza.OPCION_TODO_EL_PRESTAMO), no de una copia escrita acá.
                    responderBloqueoAnticipo(request, response, idPrestamo,
                            "Con " + cuotasQueQuedan + " cuotas se paga todo el préstamo."
                                    + " Use «" + ServicioCobranza.OPCION_TODO_EL_PRESTAMO + "».");
                    return;
                }
                // Corrección de diseño del 27/10/2026 (opción B): el monto mínimo que deja
                // exactamente K − N cuotas, ya múltiplo de credito.redondeo_efectivo.
                monto = servicioCobranza.calcularMontoMinimoParaDejarCuotas(conexion, idPrestamo, hoy,
                        cuotasQueQuedan - cuotas, redondeoEfectivo);
                if (monto == null) {
                    responderBloqueoAnticipo(request, response, idPrestamo,
                            "No hay un monto que deje exactamente " + (cuotasQueQuedan - cuotas)
                                    + " cuotas. Pruebe con otra cantidad.");
                    return;
                }
                ahorro = ahorroDeInteresesPorCuotas(proximaCuota.getTotalProgramado(), cuotas, monto);
            } else {
                monto = montoAnticipoLibreValido(request.getParameter("monto"));
                if (monto == null) {
                    responderBloqueoAnticipo(request, response, idPrestamo, "Escriba el monto que trae el cliente.");
                    return;
                }
            }
            ResultadoLiquidacion previewAnticipo = servicioCobranza.calcularPreviewAnticipoParcial(conexion,
                    idPrestamo, hoy, monto);
            request.setAttribute("previewAnticipo", previewAnticipo);
            request.setAttribute("montoAnticipoPrevia", monto);
            request.setAttribute("ahorroAnticipoPrevia", ahorro);
            request.getRequestDispatcher(VISTA_FRAGMENTO_ANTICIPO).forward(request, response);
        } catch (NegocioException error) {
            // D-88 ("Primero hay que cobrar la cuota del ..."), D-90 ("El monto cubre todo el
            // saldo... Use Cancelación total.") y el mínimo de interés del período: el servicio ya
            // trae el texto exacto, se muestra tal cual.
            responderBloqueoAnticipo(request, response, idPrestamo, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log(
                    "Error de base de datos al calcular la vista previa del adelanto del préstamo " + idPrestamo
                            + ".", error);
            responderBloqueoAnticipo(request, response, idPrestamo,
                    "No se pudo calcular el adelanto; intente nuevamente.");
        } catch (ServletException error) {
            // El reenvío al fragmento falló (nunca por un dato del negocio) -- se loguea y se
            // responde vacío, igual que antes de la ampliación: un AJAX de la ficha de cobro no
            // puede terminar en un 500 crudo.
            getServletContext().log(
                    "Error al mostrar el fragmento de vista previa del adelanto del préstamo " + idPrestamo + ".",
                    error);
            if (!response.isCommitted()) {
                response.setContentType("text/plain; charset=UTF-8");
                response.getWriter().write("");
            }
        }
    }

    /**
     * Responde el fragmento de vista previa con solo el mensaje de bloqueo (sin vista previa) --
     * cobranza-ficha.js lo muestra debajo de la pregunta y deja "Cobrar" en gris. Si el propio
     * reenvío falla, recién ahí se responde vacío (el comportamiento anterior a la ampliación del
     * 27/10/2026): nunca un 500 crudo en medio de un AJAX de la ficha de cobro.
     */
    private void responderBloqueoAnticipo(HttpServletRequest request, HttpServletResponse response, long idPrestamo,
            String mensaje) throws IOException {
        request.removeAttribute("previewAnticipo");
        request.setAttribute("mensajeBloqueoAnticipo", conNombresDeEstaPantalla(mensaje));
        try {
            request.getRequestDispatcher(VISTA_FRAGMENTO_ANTICIPO).forward(request, response);
        } catch (ServletException | IOException error) {
            getServletContext().log(
                    "Error al mostrar el fragmento de vista previa del adelanto del préstamo " + idPrestamo + ".",
                    error);
            if (!response.isCommitted()) {
                response.setContentType("text/plain; charset=UTF-8");
                response.getWriter().write("");
            }
        }
    }

    private BigDecimal parsearMonto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese un monto.");
        }
        return ConversorNumeros.aBigDecimalGuaranies(texto);
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

    /**
     * D-145: misma aritmética ya aprobada en D-141 (piso al múltiplo del parámetro
     * credito.redondeo_efectivo) -- acá vive en el controlador, no en un servicio, porque se usa
     * para decidir QUÉ MONTO ENVIAR a un servicio que no redondea nada por su cuenta
     * (confirmarAnticipoCapital, pedido explícito de Joel de no tocarlo). No es una regla nueva.
     */
    private BigDecimal redondearHaciaAbajo(BigDecimal monto, int paso) {
        if (paso <= 0) {
            return monto;
        }
        BigDecimal pasoDecimal = BigDecimal.valueOf(paso);
        return monto.subtract(monto.remainder(pasoDecimal));
    }

    // ----------------------------------------------------------------
    // Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel), con la corrección de
    // diseño de esa misma noche (opción B, tras la prueba en pantalla). Funciones PURAS y
    // package-private para poder probarlas con JUnit sin base de datos ni servlet container
    // (REGLAS_DESARROLLO.md §7 punto 9, mismo criterio que ServicioCobranza.calcularRedondeoEfectivo).
    //
    // El monto de "N cuotas" NO se calcula acá: lo busca
    // ServicioCobranza.calcularMontoMinimoParaDejarCuotas con el cálculo del motor que ya existe.
    // Acá solo queda la validación de los parámetros y el ahorro, que es informativo.
    // ----------------------------------------------------------------

    /**
     * Decisión de Joel (27/10/2026, tras la tercera prueba en pantalla): ¿hay que rechazar este
     * adelanto porque lo recibido no alcanza para la cantidad de cuotas elegida?
     * <p>
     * Solo cuando la cajera usó <b>"Por cantidad de cuotas"</b> ({@code modoAnticipo = "cuotas"},
     * lo que manda {@code cobranza-ficha.js} al confirmar). En <b>"Por monto"</b> no aplica: ahí
     * el monto libre es justamente la respuesta de la cajera, y sigue valiendo el
     * {@code min(recibido, sugerido)} de D-145 (P-5) sin cambios. Cualquier otro valor de
     * {@code modoAnticipo} (ausente, vacío, manipulado) se trata como "no es la opción por
     * cantidad" y por lo tanto no rechaza nada: el control nunca endurece de más un camino que ya
     * funcionaba.
     */
    static boolean recibidoNoAlcanzaParaLasCuotas(String modoAnticipo, BigDecimal montoRecibido,
            BigDecimal montoSugerido) {
        return MODO_ANTICIPO_CUOTAS.equals(modoAnticipo) && montoRecibido != null && montoSugerido != null
                && montoRecibido.compareTo(montoSugerido) < 0;
    }

    /**
     * El mismo aviso que ya muestra la pantalla, para cuando el rechazo lo tiene que dar el
     * servidor. No nombra la cantidad de cuotas a propósito: esa cantidad solo existe en el
     * navegador, y traerla en el formulario sería meter un dato que el servidor no puede cruzar
     * contra el monto dentro de un mensaje que se muestra en pantalla. Los dos montos sí vienen del
     * formulario, pero pasan por {@code parsearMonto} antes, así que se vuelven a formatear desde
     * un número.
     * <p>
     * B115 (05/11/2026): la frase ya no se escribe acá. Tiene un solo dueño,
     * {@link ServicioCobranza#textoNoAlcanzaParaLasCuotas(String, String)}, que es el mismo que
     * arma la de la pantalla — la única diferencia permitida entre las dos es justo el fragmento
     * de la cantidad, y un test lo fija.
     */
    static String mensajeNoAlcanzaParaLasCuotas(BigDecimal montoRecibido, BigDecimal montoSugerido) {
        return ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                FormateadorNumeros.comoGuaranies(montoRecibido),
                FormateadorNumeros.comoGuaranies(montoSugerido));
    }

    /**
     * Corrección de Joel tras la prueba en pantalla del 27/10/2026: el motor rechaza un adelanto
     * que cubre todo el saldo con *"...Use Cancelación total."*, pero en esta pantalla esa opción
     * se llama **«Todo el préstamo»** -- la cajera leía el nombre de un botón que no existe.
     * <p>
     * La adaptación va acá, en el controlador, y <b>no</b> en el texto de la excepción: ese mismo
     * mensaje de {@code ServicioMotorFinanciero.calcularAnticipoParcial} lo pueden estar leyendo
     * otros llamadores (D-90 protege a cualquiera que llame al motor), así que se traduce solo
     * para esta vista. Pura presentación: no cambia ninguna validación ni ningún monto.
     * <p>
     * B106 (05/11/2026): el nombre nuevo sale de {@link ServicioCobranza#OPCION_TODO_EL_PRESTAMO},
     * su único dueño — si alguna vez se cambia el rótulo del botón, este mensaje lo acompaña solo.
     */
    static String conNombresDeEstaPantalla(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        return mensaje.replace("Use Cancelación total.",
                "Elija «" + ServicioCobranza.OPCION_TODO_EL_PRESTAMO + "».");
    }

    /**
     * Lo que el cliente se ahorra de intereses al adelantar: lo que habría pagado por esas N
     * cuotas según el plan ({@code N × la cuota programada}) menos lo que de verdad paga ahora.
     * Es positivo porque el adelanto no cobra los intereses futuros (RN-11, Ley 6624/2020) -- esa
     * diferencia es justamente el interés que el cliente deja de pagar. Solo informativo: no
     * interviene en ningún cobro. Cero si el dato no permite calcularlo.
     */
    static BigDecimal ahorroDeInteresesPorCuotas(BigDecimal totalProgramadoProximaCuota, int cuotas,
            BigDecimal montoAPagar) {
        if (totalProgramadoProximaCuota == null || montoAPagar == null || cuotas < 1) {
            return BigDecimal.ZERO;
        }
        BigDecimal segunElPlan = totalProgramadoProximaCuota.multiply(BigDecimal.valueOf(cuotas));
        BigDecimal ahorro = segunElPlan.subtract(montoAPagar);
        return ahorro.compareTo(BigDecimal.ZERO) > 0 ? ahorro : BigDecimal.ZERO;
    }

    /**
     * Valida el {@code ?cuotas=N} de la vista previa (condición de Joel, 27/10/2026: el servidor
     * nunca calcula con un N inválido). Devuelve null -- y el llamador responde el mensaje de
     * bloqueo -- si no es un entero, si es menor que 1 o si pasa {@code maximo} (las cuotas que
     * le quedan al cliente; con N igual a esas cuotas ya no es un adelanto sino la cancelación
     * total, caso que decide el llamador).
     */
    static Integer cuotasAnticipoValidas(String texto, int maximo) {
        if (texto == null || maximo < 1) {
            return null;
        }
        int cuotas;
        try {
            cuotas = Integer.parseInt(texto.trim());
        } catch (NumberFormatException error) {
            return null;
        }
        return cuotas >= 1 && cuotas <= maximo ? Integer.valueOf(cuotas) : null;
    }

    /**
     * Valida el {@code ?monto=X} de la vista previa por monto libre (misma condición de Joel):
     * null si no es un monto en guaraníes válido o si no es positivo. El mínimo real (interés
     * devengado + IVA del período) y el tope de D-90 los sigue decidiendo el servicio, no esto.
     */
    static BigDecimal montoAnticipoLibreValido(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        BigDecimal monto;
        try {
            monto = ConversorNumeros.aBigDecimalGuaranies(texto);
        } catch (NegocioException error) {
            return null;
        }
        return monto.compareTo(BigDecimal.ZERO) > 0 ? monto : null;
    }

    /**
     * Cuántas cuotas del plan vigente le quedan al cliente por pagar -- solo para el texto "de N
     * cuotas que le quedan" del control de cantidad. No interviene en ningún monto.
     */
    static int contarCuotasPendientes(Prestamo prestamoMotor) {
        if (prestamoMotor == null || prestamoMotor.getCuotas() == null) {
            return 0;
        }
        int pendientes = 0;
        for (Cuota cuota : prestamoMotor.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                pendientes++;
            }
        }
        return pendientes;
    }
}
