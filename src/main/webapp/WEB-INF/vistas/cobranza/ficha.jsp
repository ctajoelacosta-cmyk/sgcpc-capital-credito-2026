<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SesionCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Cuota" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    // Si hubo una excepcion, ControladorCobranzaServlet.mostrarFicha pone "mensajeError" (ya lo
    // muestra cabecera.jsp) pero NO llega a poner el resto de los atributos -- por eso todo el
    // cuerpo de la ficha va adentro de este chequeo, en vez de cortar con un "return" (D-49,
    // REGLAS_DESARROLLO.md: nunca escribir los delimitadores de scriptlet dentro de un comentario).
    PrestamoCredito prestamoFicha = (PrestamoCredito) request.getAttribute("prestamo");
    if (prestamoFicha != null) {
%>
<%
    Persona clienteFicha = (Persona) request.getAttribute("cliente");
    ResultadoDeudaExigible exigibleFicha = (ResultadoDeudaExigible) request.getAttribute("exigible");
    BigDecimal montoCobrableNormalFicha = (BigDecimal) request.getAttribute("montoCobrableNormal");
    boolean hayAlgoParaCobrarFicha = Boolean.TRUE.equals(request.getAttribute("hayAlgoParaCobrar"));
    ResultadoLiquidacion previewCancelacionFicha = (ResultadoLiquidacion) request.getAttribute("previewCancelacion");
    Integer redondeoEfectivoFicha = (Integer) request.getAttribute("redondeoEfectivo");
    boolean sinVencidasFicha = Boolean.TRUE.equals(request.getAttribute("sinVencidas"));
    BigDecimal montoAnticipoMinimoFicha = (BigDecimal) request.getAttribute("montoAnticipoMinimo");
    // Corrección de diseño del 27/10/2026 (opción B, tras la prueba en pantalla de Joel): la
    // cantidad de cuotas es libre, de 1 hasta las que le quedan (K), y el monto de N cuotas es el
    // MÍNIMO que deja exactamente K − N cuotas en el plan nuevo -- no "N × la cuota", que dejaba
    // menos cuotas de las que la cajera decía. Acá viaja K y el monto de N = 1 (el que el
    // servidor ya usó para la vista previa de esta página); el de cualquier otra cantidad llega
    // por AJAX (fragmento_vista_previa_anticipo.jsp). La JSP y el JS nunca arman un importe
    // (REGLAS_DESARROLLO.md §5).
    BigDecimal montoAnticipoCuotasFicha = (BigDecimal) request.getAttribute("montoAnticipoCuotas");
    BigDecimal ahorroAnticipoCuotasFicha = (BigDecimal) request.getAttribute("ahorroAnticipoCuotas");
    Integer cuotasPendientesFicha = (Integer) request.getAttribute("cuotasPendientes");
    int cuotasQueQuedanFicha = cuotasPendientesFicha == null ? 0 : cuotasPendientesFicha;
    BigDecimal montoCobrableRedondeadoFicha = (BigDecimal) request.getAttribute("montoCobrableRedondeado");
    BigDecimal montoCancelarRedondeadoFicha = (BigDecimal) request.getAttribute("montoCancelarRedondeado");
    ResultadoLiquidacion previewAnticipoFicha = (ResultadoLiquidacion) request.getAttribute("previewAnticipo");
    String anticipoBloqueadoMensajeFicha = (String) request.getAttribute("anticipoBloqueadoMensaje");
    // B201 (15/11/2026): si se llegó por el menú "Cancelación anticipada". Se lee del query string
    // igual que en cobranza/busqueda.jsp (D3/RF38): es una lectura directa en la vista, no se toca
    // ni el controlador ni el servicio. Lo usan el aviso de abajo y el "Volver" del pie.
    boolean origenCancelacionAnticipadaFicha =
            ServicioCobranza.esOrigenCancelacionAnticipada(request.getParameter("origen"));
    SesionCaja sesionAbiertaFicha = (SesionCaja) request.getAttribute("sesionAbierta");
    boolean cajaListaFicha = Boolean.TRUE.equals(request.getAttribute("cajaLista"));
    String mensajeCajaOtroDiaFicha = (String) request.getAttribute("mensajeCajaOtroDia");
    String identificadorCobroFicha = (String) request.getAttribute("identificadorConfirmacionCobro");
    String identificadorCancelacionFicha = (String) request.getAttribute("identificadorConfirmacionCancelacion");
    String identificadorAnticipoFicha = (String) request.getAttribute("identificadorConfirmacionAnticipo");
    boolean prestamoFinalizadoFicha = Boolean.TRUE.equals(request.getAttribute("prestamoFinalizado"));
    // B98 (30/10/2026): la situacion del cobro de hoy la decide ServicioCobranza; la JSP solo
    // pregunta. "SOLO_CUOTA_DE_HOY" = nada vencido, pero hay una cuota que vence HOY.
    ServicioCobranza.SituacionCobroHoy situacionCobroFicha =
            (ServicioCobranza.SituacionCobroHoy) request.getAttribute("situacionCobroHoy");
    if (situacionCobroFicha == null) {
        situacionCobroFicha = ServicioCobranza.SituacionCobroHoy.NADA_PARA_COBRAR;
    }
    String etiquetaCuotaDeHoyFicha = (String) request.getAttribute("etiquetaCuotaDeHoy");
    boolean soloCuotaDeHoyFicha =
            situacionCobroFicha == ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY;
    boolean hayVencidasFicha = !prestamoFinalizadoFicha
            && exigibleFicha.getTotalGeneral().compareTo(BigDecimal.ZERO) > 0;
    // D-145: la primera opción -- si hay algo vencido/de hoy, va por Cobrar con el monto exacto
    // (el propio servicio redondea el vuelto, D-141); si no, por Adelantar capital con el monto
    // que encontró el servidor (nunca lo que la cajera escriba en "Recibido").
    boolean puedePagarCuotaFicha = hayAlgoParaCobrarFicha
            || (sinVencidasFicha && montoAnticipoCuotasFicha != null && previewAnticipoFicha != null);
    BigDecimal montoPagarCuotaFicha = hayAlgoParaCobrarFicha ? montoCobrableNormalFicha : montoAnticipoCuotasFicha;
    String viaPagarCuotaFicha = hayAlgoParaCobrarFicha ? "cobrar" : "anticipar";
    // D-145 (P-2, pedido de Joel): "Pagar lo vencido" muestra el monto exacto y, si el redondeo
    // de D-141 lo cambia, el monto "en efectivo" debajo, más chico. Corrección del 27/10/2026
    // (aprobada por Joel): en "Por cantidad de cuotas" ese par NO va -- hay un solo monto, ya
    // múltiplo de 50, porque no es un precio con descuento sino el mínimo que logra sacar esas
    // cuotas del plan; los dos valores quedan iguales y la línea "en efectivo" no se muestra.
    BigDecimal montoPagarCuotaExactoFicha = hayAlgoParaCobrarFicha ? montoCobrableNormalFicha
            : montoAnticipoCuotasFicha;
    BigDecimal montoPagarCuotaRedondeadoFicha = hayAlgoParaCobrarFicha ? montoCobrableRedondeadoFicha
            : montoAnticipoCuotasFicha;
    // Mejora de pantalla del 27/10/2026 (diseño aprobado por Joel): estas dos banderas estaban
    // declaradas adentro del bloque de la primera opción. El acomodo nuevo (selector arriba,
    // "Elegir" y "Resumen del cobro" debajo) las necesita también fuera de ese bloque, así que
    // se declaran acá, con el resto. Mismo valor de siempre; "pagarCuotaConRedondeoFicha" suma
    // el chequeo de "puedePagarCuotaFicha" porque montoPagarCuotaExactoFicha es null cuando no
    // hay ninguna cuota que pagar (y antes nunca se evaluaba en ese caso).
    boolean anticiparFicha = "anticipar".equals(viaPagarCuotaFicha);
    boolean pagarCuotaConRedondeoFicha = puedePagarCuotaFicha && montoPagarCuotaRedondeadoFicha != null
            && montoPagarCuotaRedondeadoFicha.compareTo(montoPagarCuotaExactoFicha) != 0;
    // D-146 (E-4, corrección de Joel): previewCancelacionFicha es null cuando el préstamo ya
    // está FINALIZADO (ControladorCobranzaServlet.mostrarFicha corta antes de calcularla) -- sin
    // este chequeo, esta línea tira NullPointerException DESPUÉS del forward a esta JSP, fuera
    // del try/catch del servlet, y termina en error-500.jsp ("Ocurrió un error inesperado") en
    // vez de en la ficha de solo lectura. montoCancelarExactoFicha no se usa en ningún lado
    // cuando el préstamo está finalizado (ver más abajo, todo adentro de "!prestamoFinalizadoFicha"
    // o detrás de sesionAbiertaFicha/cajaListaFicha, que también son null/false en ese caso).
    BigDecimal montoCancelarExactoFicha = previewCancelacionFicha == null ? null
            : previewCancelacionFicha.getMontoTotalLiquidacion();
    @SuppressWarnings("unchecked")
    List<FilaHistorialCobranza> historialFicha = (List<FilaHistorialCobranza>) request.getAttribute("historialCobros");
    // B197 (15/11/2026): el total del historial (el del contador del titulo). Respaldo para
    // esta JSP contra un .class viejo, antes del Clean and Build (punto 11b).
    Integer totalHistorialFicha = (Integer) request.getAttribute("totalHistorial");
    if (totalHistorialFicha == null) {
        totalHistorialFicha = historialFicha == null ? 0 : historialFicha.size();
    }
    @SuppressWarnings("unchecked")
    Map<String, String> valoresFormularioFicha = (Map<String, String>) request.getAttribute("valoresFormulario");
    String montoRecibidoPrevioFicha = valoresFormularioFicha != null ? valoresFormularioFicha.get("montoRecibido") : null;
    // D-145 (P-6, pedido de Joel): si un cobro parcial se rechazó por otro motivo (ej. error de
    // base), guardarValoresCobro (sin cambios desde D-144) ya guarda la fecha de promesa tecleada
    // -- se la devuelve a la cajera para que no tenga que volver a elegirla.
    String fechaPromesaPreviaFicha = valoresFormularioFicha != null ? valoresFormularioFicha.get("fechaPromesa") : null;
    String identificadorReversionFicha = (String) request.getAttribute("identificadorConfirmacionReversion");
    // C30: el aviso de la reversión pendiente de autorizar, ya redactado por ServicioReversiones.
    String avisoReversionPendienteFicha = (String) request.getAttribute("avisoReversionPendiente");
    @SuppressWarnings("unchecked")
    List<String> avisosTimbradoFicha = (List<String>) request.getAttribute("avisosTimbrado");
%>
<h1><%= prestamoFicha.getIdentificadorOperacion() %>
    <small class="badge <%= prestamoFinalizadoFicha ? "badge-secondary"
            : (prestamoFicha.getEstadoPrestamo().name().equals("EN_MORA") ? "badge-danger" : "badge-success") %>">
        <%= prestamoFicha.getEstadoPrestamo().getEtiqueta() %></small></h1>
<p class="text-muted"><%= clienteFicha.getNombres() %> <%= clienteFicha.getApellidos() %>
    — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFicha.getCedulaIdentidad()) %></p>

<%-- B201 (15/11/2026): la ficha dice que se entró por "Cancelación anticipada". Antes el menú
     resaltaba esa opción y la pantalla era idéntica a la de Cobranza, con las tres opciones y
     ninguna marca: quien entró a cancelar tenía que acordarse solo de cuál era. El rótulo de la
     opción sale del MISMO dueño que el botón (ServicioCobranza.OPCION_TODO_EL_PRESTAMO, B106), así
     que nombra lo que de verdad va a ver abajo -- que es justo lo que B198 vino a arreglar en el
     aviso de la pantalla anterior. En alert-info: es una ayuda de contexto, no un bloqueo. --%>
<% if (origenCancelacionAnticipadaFicha) { %>
<div class="alert alert-info">Entró por <strong>Cancelación anticipada</strong>: la opción es
    «<%= ServicioCobranza.OPCION_TODO_EL_PRESTAMO %>», en la tarjeta de cobranza.</div>
<% } %>

<%-- Bloque 6 (PA-13): aviso previo, nunca bloquea nada. --%>
<% if (avisosTimbradoFicha != null && !avisosTimbradoFicha.isEmpty()) { %>
<div class="alert alert-warning">
    <% for (String avisoTimbradoFicha : avisosTimbradoFicha) { %>
    <div><i class="fas fa-exclamation-triangle mr-1"></i><%= avisoTimbradoFicha %></div>
    <% } %>
</div>
<% } %>

<%-- E8 (Bloque 2, D-94): historial de cobros y reimpresión -- siempre visible, incluso finalizado. --%>
<div class="card mt-3">
    <%-- B197 (15/11/2026): contador y paginacion. El contador cuenta el TOTAL de cobros del
         prestamo, no las filas de la pagina. --%>
    <div class="card-header"><h5 class="m-0">Historial de cobros (<%= FormateadorNumeros.comoCantidad(totalHistorialFicha) %>)</h5></div>
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead><tr><th>Fecha/hora</th><th>Tipo</th><th>Recibo</th><th class="cc-monto">Monto</th><th>Comprobantes</th><th>Reversión</th></tr></thead>
                <tbody>
                <% if (historialFicha != null) { for (FilaHistorialCobranza filaHistorial : historialFicha) { %>
                <tr>
                    <td><%= filaHistorial.getFechaHora() %></td>
                    <td><%= filaHistorial.getEtiquetaTipo() %>
                        <% if (filaHistorial.isRevertido()) { %> <span class="badge badge-secondary">REVERTIDO</span><% } %></td>
                    <td><%= filaHistorial.getNumeroRecibo() %></td>
                    <td class="cc-monto"><%= filaHistorial.getMontoAplicado() %></td>
                    <td>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/recibo/<%= filaHistorial.getIdPago() %>">Recibo</a>
                        <% if (filaHistorial.getIdFactura() != null) { %>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/factura/<%= filaHistorial.getIdFactura() %>">Factura <%= filaHistorial.getNumeroFactura() %></a>
                        <% if (filaHistorial.isFacturaAnulada()) { %>
                        <span class="badge badge-secondary">ANULADA</span>
                        <a class="btn btn-sm btn-outline-secondary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/nota-anulacion/<%= filaHistorial.getIdFactura() %>">Nota de anulación</a>
                        <% } %>
                        <% } %>
                        <% if (filaHistorial.getIdLiquidacion() != null) { %>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/constancia/<%= filaHistorial.getIdLiquidacion() %>">Constancia <%= filaHistorial.getNumeroConstancia() %></a>
                        <% } %>
                        <%-- Punto 2 (RF25, pedido de Joel): reimprimir la constancia de la
                             reversión desde el historial de cobros. --%>
                        <% if (filaHistorial.getIdReversionPago() != null) { %>
                        <a class="btn btn-sm btn-outline-secondary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/reversion/<%= filaHistorial.getIdReversionPago() %>">Constancia de reversión</a>
                        <% } %>
                    </td>
                    <td>
                        <%-- E9/Bloque 5: solo la última operación vigente ofrece el botón -- lo
                             decide ServicioCobranza.listarHistorialCobros, nunca la JSP a ojo. --%>
                        <% boolean esRechazada = "RECHAZADA".equals(filaHistorial.getEstadoReversionSolicitud());
                           if (esRechazada) { %>
                        <div class="text-danger mb-1">Reversión rechazada: <%= filaHistorial.getMotivoRechazoReversion() %></div>
                        <% }
                           if (filaHistorial.isPuedeSolicitarReversion()) { %>
                        <%-- B192 (15/11/2026): abre la ventana del final de la tarjeta. Antes el
                             formulario entero vivía ACÁ, dentro de esta celda: el motivo en un campo
                             de 180 px y, con factura, la casilla en la que la cajera declara tener el
                             original y las copias. Es una solicitud que no se deshace y que dispara
                             una autorización del gerente, así que le corresponde ventana, el patrón
                             de B155/B168/B171/B178/B188. --%>
                        <button type="button" class="btn btn-sm btn-outline-danger" data-toggle="modal"
                                data-target="#modalReversion<%= filaHistorial.getIdPago() %>"><%= esRechazada ? "Solicitar de nuevo" : "Solicitar reversión" %></button>
                        <% } else if ("SOLICITADA".equals(filaHistorial.getEstadoReversionSolicitud())) { %>
                        <span class="text-warning">Reversión solicitada — pendiente de autorización</span>
                        <% } else if (filaHistorial.isNoAdmiteReversionPorCierreDocumental()) { %>
                        <%-- 13/10/2026 (pedido de Joel): con el cierre documental ya COMPLETO
                             (el pagaré ya se devolvió), ni casilla ni motivo ni botón -- mismo
                             texto que el mensaje del servidor, en el mismo lugar donde iría el
                             formulario. --%>
                        <span class="text-muted">No admite reversión: pagaré devuelto al cliente</span>
                        <%-- B130 (11/11/2026, revisión final de pantallas): el caso que faltaba, y
                             es el MÁS COMÚN -- todo cobro que no sea el último confirmado. La regla
                             es de ServicioCobranza (yaEncontroLaUltimaConfirmada) y hasta ahora la
                             columna se quedaba VACÍA, sin decir por qué: la cajera veía una fila
                             con "Solicitar reversión" y la de abajo sin nada. Está escrita arriba
                             de la bandeja del gerente (reversiones/bandeja.jsp), que es justo quien
                             no la necesita. Mismo criterio que B87: si algo no se puede, la
                             pantalla dice por qué. El ya REVERTIDO queda afuera a propósito: su
                             fila ya lo dice con el badge REVERTIDO de la columna "Tipo". --%>
                        <% } else if (!filaHistorial.isRevertido()) { %>
                        <span class="text-muted">Solo se puede revertir el último cobro.</span>
                        <% } %>
                    </td>
                </tr>
                <% } } %>
                <% if (historialFicha == null || historialFicha.isEmpty()) { %>
                <tr><td colspan="6" class="text-muted">Todavía no hay cobros registrados.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>

        <%-- B197: la paginacion del historial. La ficha no tiene "listado vivo", asi que los
             enlaces navegan de verdad; y lleva su propio parametro (paginaHistorial) para no
             chocar con el "pagina" del bloque "En mora" de la lista de trabajo. La pagina 1 sigue
             siendo la mas nueva, que es donde vive el boton de reversion. --%>
        <%
            request.setAttribute("pagina", request.getAttribute("paginaHistorial"));
            request.setAttribute("totalPaginas", request.getAttribute("totalPaginasHistorial"));
            request.setAttribute("paginacionUrlBase",
                    request.getContextPath() + "/cobranza/" + prestamoFicha.getIdPrestamo() + "?");
            request.setAttribute("paginacionParamPagina", "paginaHistorial");
            request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
        %>
        <jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />

        <%-- B192 (15/11/2026): las ventanas de la solicitud de reversión, después de la tabla y una
             por cobro que la admita -- el patrón de B155/B188. En la práctica hay una sola: el botón
             lo ofrece únicamente la última operación vigente, y eso lo decide
             ServicioCobranza.listarHistorialCobros, nunca esta JSP.
             El formulario es el MISMO: misma ruta POST, los mismos tres campos ocultos (idPago,
             identificadorConfirmacion, volverA), el mismo CSRF, el mismo campo "motivo" con su
             minlength="10" y la MISMA casilla de la factura con su id por pago, así que ni el
             servidor ni el cartel de "invalid" de pie.jsp cambian en nada. El humo tampoco:
             inventa su propio identificadorConfirmacion y postea directo, nunca lee este marcado
             (verificado en humo_http.sh:4829).
             NO lleva data-confirmar: la ventana ES la confirmación, y lo que antes decía el cartel
             de A3c (el recibo y el monto) ahora lo dice el título y el cuerpo. Abre con el foco en
             descartar (autofocus en "No, volver"), porque la solicitud no se deshace y dispara una
             autorización del gerente. --%>
        <% if (historialFicha != null) { for (FilaHistorialCobranza filaReversionModal : historialFicha) {
               if (!filaReversionModal.isPuedeSolicitarReversion()) {
                   continue;
               }
               boolean esRechazadaModal = "RECHAZADA".equals(filaReversionModal.getEstadoReversionSolicitud()); %>
        <div class="modal fade" id="modalReversion<%= filaReversionModal.getIdPago() %>" tabindex="-1">
            <div class="modal-dialog">
                <div class="modal-content">
                    <form method="post" action="${pageContext.request.contextPath}/reversiones/solicitar">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <input type="hidden" name="idPago" value="<%= filaReversionModal.getIdPago() %>">
                        <input type="hidden" name="identificadorConfirmacion" value="<%= identificadorReversionFicha %>">
                        <input type="hidden" name="volverA" value="/cobranza/<%= prestamoFicha.getIdPrestamo() %>">
                        <div class="modal-header">
                            <h5 class="modal-title">¿Solicitar la reversión del cobro <%= filaReversionModal.getNumeroRecibo() %>?</h5>
                        </div>
                        <div class="modal-body">
                            <%-- B4/B43: el cuerpo repite lo que la fila ya muestra (el recibo, el
                                 monto y el cliente), con las MISMAS expresiones, y agrega lo único
                                 que la fila no dice: que esto no lo decide la cajera. --%>
                            <p class="text-muted mb-3"><small>Cliente: <%= clienteFicha.getNombres() %> <%= clienteFicha.getApellidos() %>
                                — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFicha.getCedulaIdentidad()) %>.
                                <%-- B192 (15/11/2026, segundo defecto, visto en pantalla por Joel):
                                     acá iba un "." pegado al monto y salía "50.000 Gs..". getMontoAplicado()
                                     devuelve el monto YA formateado --lo arma ServicioCobranza:2478 con
                                     FormateadorNumeros.comoGuaranies--, así que ese "Gs." ya cierra la
                                     oración. Es la cuarta forma del mismo defecto y la que se le escapaba
                                     a las tres redes: el nombre del formateador no aparece en esta JSP.
                                     Ahora la cubre TextosDePantallaTest.ningunaJspLePegaUnPuntoAUnGetterDeDinero. --%>
                                Monto aplicado: <%= filaReversionModal.getMontoAplicado() %>
                                La reversión no se aplica ahora: queda pendiente de autorización del gerente, y
                                mientras espera no se puede cobrar otra cosa de este préstamo.</small></p>
                            <%-- Punto 1 (RF25, factura, pedido de Joel): con factura, el control
                                 empieza en la cajera -- sin esta casilla, el servidor rechaza
                                 (ServicioReversiones.validarDeclaracionFacturaCajera). B192: en la
                                 ventana entra entera, con su letra chica, en vez de apretada en una
                                 celda de la tabla. --%>
                            <% if (filaReversionModal.getIdFactura() != null) { %>
                            <div class="form-check mb-3">
                                <input type="checkbox" class="form-check-input" name="facturaEnPoderDeclaradoCajera"
                                       value="true" required id="facturaCajera<%= filaReversionModal.getIdPago() %>">
                                <label class="form-check-label" for="facturaCajera<%= filaReversionModal.getIdPago() %>">
                                    Tengo en mi poder el original y todas las copias de la factura
                                    <%= filaReversionModal.getNumeroFactura() %>, devueltas por el cliente.
                                </label>
                                <div class="text-muted small">Sin la factura original y sus copias no se puede
                                    anular. Corresponde una nota de crédito; consulte con el contador.</div>
                            </div>
                            <% } %>
                            <div class="form-group mb-0">
                                <label for="motivoReversion<%= filaReversionModal.getIdPago() %>">Motivo de la reversión *</label>
                                <input type="text" id="motivoReversion<%= filaReversionModal.getIdPago() %>" name="motivo"
                                       class="form-control" placeholder="Motivo (mínimo 10 caracteres)"
                                       minlength="10" required>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                            <button type="submit" class="btn btn-outline-danger"><%= esRechazadaModal ? "Solicitar de nuevo" : "Solicitar reversión" %></button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        <% } } %>
    </div>
</div>

<% if (prestamoFinalizadoFicha) {
    // D-146 (E-4, pedido de Joel): fecha del cierre para el aviso -- historialFicha ya viene del
    // pago más reciente al más viejo (ServicioCobranza.listarHistorialCobros), y si el préstamo
    // está FINALIZADO esa primera fila es, por construcción, la que lo dejó así (una cancelación
    // total o el último cobro normal que agotó el saldo) -- nunca una revertida, porque revertir
    // esa operación es justamente lo que sacaría al préstamo de FINALIZADO. fechaHora ya viene
    // formateada "dd/mm/aaaa HH:mm" (FormateadorFechas.comoDdMmAaaaHhMm); se usan los primeros 10
    // caracteres para mostrar solo la fecha, sin tocar ningún cálculo ni servicio.
    String fechaFinalizacionFicha = (historialFicha != null && !historialFicha.isEmpty())
            ? historialFicha.get(0).getFechaHora().substring(0, 10) : null;
%>
<div class="alert alert-secondary mt-3">
    <% if (fechaFinalizacionFicha != null) { %>
    Préstamo cancelado el <%= fechaFinalizacionFicha %> — sin deuda pendiente.
    <% } else { %>
    Préstamo finalizado — sin deuda pendiente.
    <% } %>
</div>
<% } %>

<% if (!prestamoFinalizadoFicha) { %>
<% if (sesionAbiertaFicha == null) { %>
<div class="alert alert-warning">No hay caja abierta. <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto y color (alert-warning, no
     alert-danger -- A.8, bloqueo de tiempo, no error) que Caja/Desembolso. --%>
<% } else if (!cajaListaFicha) { %>
<div class="alert alert-warning"><%= mensajeCajaOtroDiaFicha %> <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<% } %>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Deuda exigible hoy</h5></div>
    <div class="card-body">
        <% if (hayVencidasFicha) { %>
        <div class="table-responsive">
            <table class="table table-sm">
                <thead><tr><th>Cuota</th><th class="cc-monto">Capital</th><th class="cc-monto">Interés</th><th class="cc-monto">Moratorio</th><th class="cc-monto">Punitorio</th><th class="cc-monto">IVA 10 %</th><th class="cc-monto">Total</th></tr></thead>
                <tbody>
                <%-- B195 (15/11/2026): sumas por columna para la fila de totales de abajo, con el
                     mismo patrón que la tabla del plan de pagos de Formalización (que las acumula
                     mientras dibuja las filas). El total de la ÚLTIMA columna no se suma acá: se
                     toma de exigibleFicha.getTotalGeneral(), que es el número del servicio -- el
                     mismo que la tarjeta de cobranza usa en data-monto-exigible y contra el que
                     confirmarCobro compara. Si alguna vez difiere de la suma de las filas, eso es un
                     defecto del motor y hay que mirarlo, no taparlo con una suma de pantalla. --%>
                <%
                    BigDecimal totalCapitalExigibleFicha = BigDecimal.ZERO;
                    BigDecimal totalInteresExigibleFicha = BigDecimal.ZERO;
                    BigDecimal totalMoratorioExigibleFicha = BigDecimal.ZERO;
                    BigDecimal totalPunitorioExigibleFicha = BigDecimal.ZERO;
                    BigDecimal totalIvaExigibleFicha = BigDecimal.ZERO;
                %>
                <% for (DetalleCuotaExigible detalleFicha : exigibleFicha.getDetalles()) {
                    BigDecimal ivaTotalFicha = detalleFicha.getIvaInteresPendiente()
                            .add(detalleFicha.getIvaMoratorioTotal()).add(detalleFicha.getIvaPunitorioTotal());
                    totalCapitalExigibleFicha = totalCapitalExigibleFicha.add(detalleFicha.getCapitalPendiente());
                    totalInteresExigibleFicha = totalInteresExigibleFicha.add(detalleFicha.getInteresPendiente());
                    totalMoratorioExigibleFicha = totalMoratorioExigibleFicha.add(detalleFicha.getMoratorioTotal());
                    totalPunitorioExigibleFicha = totalPunitorioExigibleFicha.add(detalleFicha.getPunitorioTotal());
                    totalIvaExigibleFicha = totalIvaExigibleFicha.add(ivaTotalFicha);
                %>
                <tr>
                    <td><%= detalleFicha.getNumeroCuota() %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(detalleFicha.getCapitalPendiente()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(detalleFicha.getInteresPendiente()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(detalleFicha.getMoratorioTotal()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(detalleFicha.getPunitorioTotal()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(ivaTotalFicha) %></td>
                    <td class="cc-monto"><strong><%= FormateadorNumeros.comoGuaranies(detalleFicha.getTotalExigible()) %></strong></td>
                </tr>
                <% } %>
                </tbody>
                <%-- B195 (15/11/2026): la fila de totales que faltaba. Son 7 columnas de dinero y
                     la cajera tenía que sumarlas de cabeza para saber cuánto pedir; el número
                     final ya estaba calculado y la pantalla no lo mostraba. Mismo marcado que la
                     fila de totales del plan de pagos de Formalización (tfoot + font-weight-bold). --%>
                <tfoot>
                <tr class="font-weight-bold">
                    <td>Total</td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalCapitalExigibleFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalInteresExigibleFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalMoratorioExigibleFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalPunitorioExigibleFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalIvaExigibleFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(exigibleFicha.getTotalGeneral()) %></td>
                </tr>
                </tfoot>
            </table>
        </div>
        <% } else { %>
        <p class="text-success mb-0">Sin cuotas vencidas.</p>
        <% } %>
    </div>
</div>

<% if (sesionAbiertaFicha != null && cajaListaFicha) { %>
<%-- D-145 (pantalla única, pedido de Joel): un solo lugar para cobrar/cancelar/adelantar -- la
     lógica de negocio sigue 100 % en confirmarCobro/confirmarCancelacionTotal/confirmarAnticipoCapital
     (sin cambios); acá solo se decide cuál de los tres formularios ocultos de abajo se envía y
     con qué monto. Todo el cálculo en vivo (vuelto, mínimo) está en
     recursos/js/cobranza-ficha.js. --%>
<div class="card mt-3" id="tarjetaCobranzaUnificada"
     data-redondeo="<%= redondeoEfectivoFicha == null ? 0 : redondeoEfectivoFicha %>"
     data-pagar-cuota-via="<%= viaPagarCuotaFicha %>"
     data-monto-pagar-cuota="<%= puedePagarCuotaFicha ? montoPagarCuotaFicha.toBigInteger() : "0" %>"
     data-monto-pagar-cuota-redondeado="<%= puedePagarCuotaFicha && montoPagarCuotaRedondeadoFicha != null
             ? montoPagarCuotaRedondeadoFicha.toBigInteger() : "0" %>"
     data-monto-cancelar-todo="<%= previewCancelacionFicha.getMontoTotalLiquidacion().toBigInteger() %>"
     data-monto-cancelar-todo-redondeado="<%= montoCancelarRedondeadoFicha == null
             ? previewCancelacionFicha.getMontoTotalLiquidacion().toBigInteger() : montoCancelarRedondeadoFicha.toBigInteger() %>"
     data-monto-anticipo-minimo="<%= montoAnticipoMinimoFicha == null ? "0" : montoAnticipoMinimoFicha.toBigInteger() %>"
     data-monto-exigible="<%= exigibleFicha.getTotalGeneral().toBigInteger() %>"
     data-cuotas-que-quedan="<%= cuotasQueQuedanFicha %>"
     data-monto-cuotas-inicial="<%= montoAnticipoCuotasFicha == null ? "0" : montoAnticipoCuotasFicha.toBigInteger() %>"
     <%-- C30 (decisión de Joel, 03/11/2026): el aviso de la reversión pendiente, para el cartel de
          confirmación de los TRES caminos de cobro. El texto lo decide
          ServicioReversiones.textoAvisoDeReversionPendiente (dueño del concepto, B92/B96): acá
          viaja tal cual y cobranza-ficha.js solo lo muestra. Vacío = no hay nada que avisar. --%>
     data-aviso-reversion-pendiente="<%= avisoReversionPendienteFicha == null ? "" : avisoReversionPendienteFicha %>">
    <div class="card-header"><h5 class="m-0">Cobranza</h5></div>
    <div class="card-body cc-cobranza">
        <%-- Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel): una sola pregunta
             con las formas de pagar como opciones excluyentes, en vez de los botones fijos
             "1/2/3 cuotas" + un "Cancelar todo" suelto al costado. Al ser excluyentes, el
             control de cantidad y el "Si adelanta..." desaparecen cuando se elige "Todo el
             préstamo" -- eso cierra el hallazgo B74. La lógica de negocio sigue 100 % en
             confirmarCobro/confirmarCancelacionTotal/confirmarAnticipoCapital (sin cambios).

             Mejora de pantalla del 27/10/2026 (diseño aprobado por Joel, SOLO presentación): las
             tres opciones pasan de tres tarjetas gigantes apiladas a un selector compacto de una
             fila, y debajo quedan dos columnas -- "Elegir" (qué pide la cajera) y "Resumen del
             cobro" (qué va a pasar, en forma de ticket, con el botón al pie). Mismos textos,
             mismos montos, mismos id: cobranza-ficha.js sigue manejando las mismas piezas, solo
             que ahora están en otro lugar de la tarjeta. --%>
        <p class="font-weight-bold mb-2">¿Cuánto quiere pagar el cliente?</p>
        <div class="cc-opciones mb-3" role="group" aria-label="Formas de pagar">
            <% if (puedePagarCuotaFicha) { %>
            <%-- Opción 1. Con cuotas vencidas: "Pagar lo vencido" (rótulo aprobado por Joel el
                 27/10/2026, reemplaza "Pagar cuota" -- puede ser más de una cuota, D-144). Sin
                 nada vencido: "Por cantidad de cuotas". Es el mismo botón único de D-145, con el
                 mismo id y la misma vía (data-pagar-cuota-via); el monto que antes iba adentro
                 del botón ahora se lee en "Resumen del cobro". --%>
            <button type="button" class="btn btn-outline-primary" id="botonPagarCuota">
                <%-- B98: el rotulo lo decide ServicioCobranza.etiquetaOpcionCobro -- antes era un
                     ternario que solo conocia dos casos y con la cuota de hoy decia "Pagar lo
                     vencido" sin que hubiera nada vencido. --%>
                <span id="textoEtiquetaPagarCuota"><%= ServicioCobranza.etiquetaOpcionCobro(situacionCobroFicha) %></span>
            </button>
            <%-- Opción 2. Monto libre: la cajera escribe lo que trae el cliente en "Recibido" y
                 el servidor muestra qué cubre y cómo queda el préstamo. Solo cuando no hay nada
                 vencido -- con cuotas vencidas, un monto menor a lo exigible ya es un pago
                 parcial con promesa (RN-20), que vive dentro de la opción 1 y no cambió. --%>
            <% if (anticiparFicha) { %>
            <%-- B106 (05/11/2026): el rótulo sale de ServicioCobranza.OPCION_POR_MONTO, el mismo
                 dueño que usa el mensaje del servidor que manda a esta opción. --%>
            <button type="button" class="btn btn-outline-primary" id="botonOpcionMonto"><%= ServicioCobranza.OPCION_POR_MONTO %></button>
            <% } %>
            <% } %>
            <%-- Opción 3. Todo el préstamo: EXACTAMENTE el flujo de cancelación total de siempre
                 (RF26, sin intereses futuros; constancia RF28) -- mismo formulario, mismo monto y
                 mismo identificador de confirmación que antes; solo cambió el rótulo del botón
                 ("Cancelar todo" se confundía con "cancelar la operación", familia B3). El
                 "en efectivo" (D-141), cuando corresponde, lo muestra el resumen al elegirla.
                 B106 (05/11/2026): el rótulo lo decide ServicioCobranza.OPCION_TODO_EL_PRESTAMO --
                 el mismo texto que antes, pero con un solo dueño para los seis lugares que lo
                 muestran (este botón, la nota de abajo, la línea grande, el título del cartel y los
                 dos mensajes del servidor que mandan a esta opción). --%>
            <button type="button" class="btn btn-outline-primary" id="botonCancelarTodo"><%= ServicioCobranza.OPCION_TODO_EL_PRESTAMO %>
                <span class="cc-opcion-monto">· <%= FormateadorNumeros.comoGuaranies(montoCancelarExactoFicha) %></span></button>
        </div>
        <% if (!puedePagarCuotaFicha) { %>
        <p class="text-muted small">
            <% if (anticipoBloqueadoMensajeFicha != null) { %>
            Este préstamo no tiene cuotas vencidas, y tampoco se puede adelantar: <%= anticipoBloqueadoMensajeFicha %>
            Por eso la única opción disponible es «<%= ServicioCobranza.OPCION_TODO_EL_PRESTAMO %>».
            <% } else { %>
            Este préstamo no tiene cuotas vencidas por cobrar hoy.
            <% } %>
        </p>
        <% } else if (hayAlgoParaCobrarFicha) { %>
        <%-- Con cuotas vencidas la lógica no cambia en nada (RN-10): primero se cobra todo lo
             vencido. El adelanto por cuotas o por monto recién aparece cuando no queda nada
             vencido, igual que antes de esta ampliación.
             B98 (30/10/2026): el texto lo arma ServicioCobranza.notaAntesDeAdelantar, porque con
             una cuota que vence HOY este mismo bloque hablaba de "lo vencido" sin que hubiera nada
             vencido. Que el adelanto no se ofrezca es CORRECTO (D-144: la cuota del dia es el cobro
             contractual de hoy; RN-11/RF27 son para capital que todavia no es exigible). --%>
        <p class="text-muted small"><%= ServicioCobranza.notaAntesDeAdelantar(situacionCobroFicha,
                etiquetaCuotaDeHoyFicha) %></p>
        <% } %>

        <div class="row">
            <%-- Columna izquierda: lo que la cajera elige/escribe. --%>
            <div class="col-lg-6">
                <%-- Corrección de Joel (27/10/2026): sin rótulo "Elegir" -- sobra, los campos de
                     esta columna ya dicen qué son. --%>
                <%-- Cantidad libre de cuotas, de 1 hasta las que le quedan (K). La caja numérica
                     existe además de los botones − / + porque con 24 cuotas llegar a 17 a fuerza
                     de clics es inviable (pedido del tutor: "si quiere pagar 8, 17 o 20"). Al
                     cambiar, el monto, las cuotas que quedan y el ahorro los recalcula el
                     SERVIDOR. Con N = K no se adelanta: es la cancelación total, y
                     cobranza-ficha.js pasa a ese flujo. --%>
                <% if (anticiparFicha && cuotasQueQuedanFicha >= 1) { %>
                <div class="form-group" id="grupoCuotasAnticipo">
                    <label class="d-block mb-1" for="campoCantidadCuotas">Cantidad de cuotas</label>
                    <div class="cc-contador">
                        <button type="button" class="btn btn-outline-secondary" id="botonMenosCuotas"
                                title="Una cuota menos">&minus;</button>
                        <input type="number" class="form-control text-center" id="campoCantidadCuotas"
                               value="1" min="1" max="<%= cuotasQueQuedanFicha %>" step="1">
                        <button type="button" class="btn btn-outline-secondary" id="botonMasCuotas"
                                title="Una cuota más">+</button>
                    </div>
                    <small class="text-muted">de <%= cuotasQueQuedanFicha %>
                        <%= cuotasQueQuedanFicha == 1 ? "cuota" : "cuotas" %> que le quedan
                        (<%= cuotasQueQuedanFicha %> = todo el préstamo)</small>
                </div>
                <% } %>
                <div class="form-group">
                    <label for="campoRecibidoUnificado">Recibido</label>
                    <%-- campo-guaranies.js inserta el monto en letras justo después de este
                         <input> (campo.nextSibling), así que el campo no puede ir adentro de un
                         input-group ni perder a su hermano de al lado. --%>
                    <input type="text" id="campoRecibidoUnificado" class="form-control campo-guaranies"
                           value="<%= montoRecibidoPrevioFicha != null ? montoRecibidoPrevioFicha : "" %>">
                    <%-- D-146 (P-15, pedido de Joel): el efectivo físico solo viene en múltiplos
                         del parámetro credito.redondeo_efectivo (hoy 50 Gs.) -- aviso en rojo,
                         igual que el de la fecha de promesa, mientras lo tecleado no sea un
                         múltiplo exacto. --%>
                    <%-- Sin "d-block": en Bootstrap 4 esa utilidad lleva !important y le gana a
                         [hidden], así que el aviso quedaría visible siempre. --%>
                    <small class="text-danger" id="avisoRecibidoNoMultiplo" hidden>El efectivo va en múltiplos de
                        <%= redondeoEfectivoFicha == null ? 0 : redondeoEfectivoFicha %> Gs.</small>
                </div>
                <%-- D-145 (P-6, pedido de Joel): "Pagar cuota" vuelve a admitir el pago parcial de
                     lo exigible, con fecha de promesa -- mismo dato que pedía la pantalla
                     anterior, confirmarCobro (sin cambios) lo sigue exigiendo cuando el monto no
                     cubre lo exigible. Oculto salvo que cobranza-ficha.js detecte ese caso. --%>
                <%-- B95 (30/10/2026, decisión de Joel): mientras el campo está vacío no va nada en
                     rojo -- el pedido sale en gris en el renglón de motivos de arriba de "Cobrar", y
                     la frase la arma ServicioCobranza (el JS no redacta, regla de B92). --%>
                <div class="form-group" id="grupoFechaPromesaUnificado" hidden
                     data-motivo-falta="<%= ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA %>">
                    <label for="campoFechaPromesaUnificado">Fecha de promesa para el resto</label>
                    <input type="date" id="campoFechaPromesaUnificado" class="form-control"
                           value="<%= fechaPromesaPreviaFicha != null ? fechaPromesaPreviaFicha : "" %>">
                    <%-- D-145 (P-8, pedido de Joel): aviso en rojo debajo del campo, además del
                         botón "Cobrar" en gris. B95 (30/10/2026): solo cuando hay una fecha que NO
                         sirve (hoy o anterior) -- con el campo vacío queda oculto, porque ahí la
                         cajera no se equivocó en nada todavía. --%>
                    <small class="text-danger" id="avisoFechaPromesaInvalida" hidden>La fecha de promesa debe ser posterior a hoy</small>
                </div>
                <%-- D-145 (pedido de Joel, punto 2): nada de "interés mínimo"/"monto máximo"/
                     "Calcular vista previa" a la vista -- el resto del detalle del adelanto va
                     detrás de un enlace. Ya se calculó una sola vez arriba, con el monto fijo
                     (ya redondeado). --%>
                <% if (anticiparFicha && previewAnticipoFicha != null) { %>
                <details id="detalleAnticipo" hidden>
                    <summary class="text-muted cc-resumen-detalle">Ver detalle del adelanto</summary>
                    <div class="small text-muted mt-1" id="contenidoDetalleAnticipo">
                        <%-- D-145 (P-11, pedido de Joel): comoGuaranies ya termina en "Gs." --
                             agregar otro "." duplicaba el punto. --%>
                        Interés devengado hoy: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFicha.getInteresDevengado()) %>
                        + IVA <%= FormateadorNumeros.comoGuaranies(previewAnticipoFicha.getIvaInteresDevengado()) %>
                        Capital aplicado: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFicha.getMontoAnticipoCapital()) %>
                        Saldo que queda: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFicha.getNuevoSaldoCapital()) %>
                    </div>
                </details>
                <% } %>
                <%-- B203 (15/11/2026, ítem 6 del informe de UX de Cancelación): el desglose que el
                     adelanto tiene desde RF27 §2a y a la cancelación le faltaba -- mostraba el
                     total y nada más, cuando es justo acá donde el cliente pregunta qué le están
                     cobrando (y lo que la Ley 6624/2020 obliga a NO cobrar hacia adelante).
                     Mismo marcado que el <details> del adelanto, unas líneas más arriba.
                     Los renglones, su orden y sus rótulos los decide
                     ServicioCobranza.desgloseCancelacionTotal (un dato, un dueño): la JSP no suma
                     ni arma ningún importe (REGLAS_DESARROLLO.md §5), solo formatea. Le pasa
                     TAMBIÉN el exigible porque el interés de las cuotas vencidas viaja dentro de
                     su total y no en getInteresDevengado() -- sin eso el desglose no daría el
                     total (ver el javadoc del método y su JUnit). --%>
                <% if (previewCancelacionFicha != null) {
                       List<ServicioCobranza.RenglonDesgloseCancelacion> desgloseCancelacionFicha =
                               ServicioCobranza.desgloseCancelacionTotal(previewCancelacionFicha, exigibleFicha); %>
                <details id="detalleCancelacion" hidden>
                    <summary class="text-muted cc-resumen-detalle">Ver detalle de la cancelación</summary>
                    <div class="small text-muted mt-1" id="contenidoDetalleCancelacion">
                        <%-- comoGuaranies ya termina en "Gs." -- agregar otro "." duplicaría el
                             punto (la lección de D-145 P-11, y la red de TextosDePantallaTest). --%>
                        <% for (ServicioCobranza.RenglonDesgloseCancelacion renglonDesgloseFicha : desgloseCancelacionFicha) { %>
                        <% if (ServicioCobranza.DESGLOSE_TOTAL_CANCELACION.equals(renglonDesgloseFicha.getEtiqueta())) { %>
                        <div class="mt-1"><strong><%= renglonDesgloseFicha.getEtiqueta() %>: <%= FormateadorNumeros.comoGuaranies(renglonDesgloseFicha.getImporte()) %></strong></div>
                        <% } else { %>
                        <div><%= renglonDesgloseFicha.getEtiqueta() %>: <%= FormateadorNumeros.comoGuaranies(renglonDesgloseFicha.getImporte()) %></div>
                        <% } %>
                        <% } %>
                        <div class="mt-1"><%= ServicioCobranza.DESGLOSE_SIN_INTERES_FUTURO %></div>
                        <%-- B204 (15/11/2026, ítem 5): y cuánto es ese interés que no se cobra, que
                             es lo que el cliente pregunta. Mismo lugar y misma redacción que el
                             ahorro del adelanto (RF27), en verde y como último renglón. El número
                             lo calcula el motor dentro de calcularCancelacionTotal y viene en el
                             ResultadoLiquidacion; la frase la arma ServicioCobranza. Si no hay
                             ahorro la frase es vacía y el renglón no se dibuja (pasa cuando todas
                             las cuotas que quedan ya están vencidas: ahí no hay interés futuro). --%>
                        <% String fraseAhorroCancelacionFicha = ServicioCobranza.fraseAhorroCancelacionTotal(
                                   previewCancelacionFicha.getInteresFuturoNoCobrado());
                           if (!fraseAhorroCancelacionFicha.isEmpty()) { %>
                        <div class="text-success mt-1"><%= fraseAhorroCancelacionFicha %></div>
                        <% } %>
                    </div>
                </details>
                <% } %>
            </div>

            <%-- Columna derecha: el ticket. Lo que paga, arriba y en grande; el resto del detalle
                 debajo, más chico; los avisos y el botón al pie, para que la cajera no tenga que
                 bajar la vista para saber qué va a cobrar. --%>
            <div class="col-lg-6">
                <div class="cc-ticket">
                    <%-- Corrección de Joel (27/10/2026): el mismo subtítulo de sección que el
                         resto del sistema ("Datos personales", "Historial de cobros") -- h5, que
                         sgcpc-tema.css ya pinta en Montserrat 600, azul, 18px, con su línea
                         debajo. "mt-0" saca el margen superior de esa regla, que acá dejaría el
                         título despegado del borde del ticket. --%>
                    <h5 class="mt-0">Resumen del cobro</h5>
                    <%-- Lo que se va a cobrar. Con cuotas vencidas: el monto exacto (y el "en
                         efectivo" debajo si D-141 lo cambia). En "Por cantidad de cuotas": un solo
                         monto con la cantidad adelante ("Paga 4 cuotas: X Gs."), que
                         cobranza-ficha.js reescribe con lo que responde el servidor al cambiar la
                         cantidad; en "Por monto" y "Todo el préstamo" lo escribe también el
                         script, con el mismo monto que ya tenía (mejora de pantalla del
                         27/10/2026: antes cada opción mostraba su monto adentro de su botón). --%>
                    <%-- B92 (29/10/2026, decisión de Joel): el texto de "Por cantidad de cuotas" tiene
                         UN solo dueño, ServicioCobranza.etiquetaAdelantoCuotas. Acá se usa para el
                         primer render, y las dos plantillas de los data- son las que usa
                         cobranza-ficha.js (que no puede llamar al servicio) para la tarjeta en vivo
                         y para el cartel de confirmación: el JS solo reemplaza {n}, nunca redacta. --%>
                    <%-- B79/B81 (30/10/2026, decisión de Joel): la línea grande dice el monto que se
                         va a cobrar. Si no se va a cobrar nada (el servidor rechazó el monto, lo
                         recibido no alcanza, el efectivo no es múltiplo) muestra data-sin-monto y el
                         motivo queda en el renglón rojo de abajo; si el cobro es parcial, muestra
                         data-parcial con lo que se cobra y el detalle de abajo explica lo exigible y
                         lo pendiente. Las tres plantillas las arma ServicioCobranza (mismo patrón que
                         B92): cobranza-ficha.js solo reemplaza los números. --%>
                    <p class="cc-ticket-principal mb-1">
                        <strong id="textoMontoPagarCuotaExacto"
                                data-adelanto-singular="<%= ServicioCobranza.etiquetaAdelantoCuotas("{n}", false) %>"
                                data-adelanto-plural="<%= ServicioCobranza.etiquetaAdelantoCuotas("{n}", true) %>"
                                data-sin-monto="<%= ServicioCobranza.LINEA_RESUMEN_SIN_MONTO %>"
                                <%-- B98: la cuota que vence hoy. "data-solo-cuota-de-hoy" le dice al
                                     JS en que situacion esta; las tres plantillas son las frases del
                                     dueno, con {monto}/{pendiente}/{cuota} para que solo reemplace
                                     numeros (regla B92). --%>
                                data-solo-cuota-de-hoy="<%= soloCuotaDeHoyFicha %>"
                                data-accion-etiqueta="<%= ServicioCobranza.etiquetaOpcionCobro(situacionCobroFicha) %>"
                                <%-- B106 (05/11/2026): los rótulos de las otras dos opciones. Los
                                     escribía a mano cobranza-ficha.js (la línea grande al elegir la
                                     opción, y el título del cartel de confirmación), así que una
                                     corrección de redacción en el servidor no los alcanzaba. Son
                                     texto fijo -- no llevan {n} ni {monto} porque no dependen del
                                     estado--, pero igual viajan en un data- para que el JS NO
                                     redacte (misma regla de B92/B98 que el data-accion-etiqueta de
                                     arriba). --%>
                                data-opcion-todo="<%= ServicioCobranza.OPCION_TODO_EL_PRESTAMO %>"
                                data-opcion-anticipo="<%= ServicioCobranza.ACCION_ADELANTAR_A_CAPITAL %>"
                                <%-- B199 + B200 (15/11/2026): las dos líneas que le faltaban al
                                     cartel de la cancelación total -- de QUIÉN es (operación,
                                     cliente y C.I.) y que al confirmar se emite una constancia
                                     NUMERADA. Las dos frases las arma ServicioCobranza y viajan en
                                     un data- para que el JS solo las muestre (regla de B92), igual
                                     que los rótulos de B106 de arriba. Viajan siempre, aunque la
                                     cajera elija otra opción: el JS las usa solo en la rama
                                     'cancelar', que es la única donde son verdad. --%>
                                data-cancelar-quien="<%= ServicioCobranza.detalleQuienCancelacionTotal(
                                        prestamoFicha.getIdentificadorOperacion(),
                                        clienteFicha.getNombres() + " " + clienteFicha.getApellidos(),
                                        FormateadorTexto.comoCedulaConPuntos(clienteFicha.getCedulaIdentidad())) %>"
                                data-cancelar-constancia="<%= ServicioCobranza.AVISO_CONSTANCIA_CANCELACION %>"
                                <%-- B106 (ampliación aprobada por Joel el 05/11/2026): los tres
                                     botones del cartel de confirmación, uno por acción. Los otros
                                     dos botones del mismo cartel ya viajaban acá desde B96/B98
                                     (data-parcial-boton y data-boton-cuota-de-hoy), que son los que
                                     pisan a estos tres en el cobro parcial y en la cuota de hoy. --%>
                                data-boton-vencido="<%= ServicioCobranza.BOTON_COBRAR_LO_VENCIDO %>"
                                data-boton-todo="<%= ServicioCobranza.BOTON_COBRAR_TODO_EL_PRESTAMO %>"
                                data-boton-anticipo="<%= ServicioCobranza.BOTON_ADELANTAR_A_CAPITAL %>"
                                <%-- B115 (05/11/2026, redacción elegida por Joel): el aviso de que
                                     lo recibido no alcanza para la cantidad de cuotas elegida.
                                     Estaba escrito en el JS Y en el servidor, y las dos
                                     redacciones ya habían quedado distintas. Ahora la frase tiene
                                     un solo dueño (ServicioCobranza) y acá viajan sus dos formas
                                     --singular y plural, como el data-adelanto-* de B92-- con
                                     {recibido}/{n}/{monto}: el JS solo reemplaza números. --%>
                                data-falta-cuotas-singular="<%= ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                                        "{recibido}", "{n}", false, "{monto}") %>"
                                data-falta-cuotas-plural="<%= ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                                        "{recibido}", "{n}", true, "{monto}") %>"
                                data-a-cuenta="<%= ServicioCobranza.lineaCobroACuenta("{monto}") %>"
                                data-a-cuenta-detalle="<%= ServicioCobranza.detalleCobroACuenta("{total}", "{pendiente}") %>"
                                data-cartel-cuota-de-hoy="<%= ServicioCobranza.lineaCartelCuotaDeHoy(
                                        etiquetaCuotaDeHoyFicha == null ? "" : etiquetaCuotaDeHoyFicha, "{monto}") %>"
                                data-boton-cuota-de-hoy="<%= ServicioCobranza.BOTON_COBRAR_CUOTA_DE_HOY %>"
                                data-solo-vencido-con-excedente="<%= ServicioCobranza.textoSoloLoVencidoConExcedente(
                                        "{excedente}", "{pendiente}") %>"
                                data-solo-vencido-sin-excedente="<%= ServicioCobranza.textoSoloLoVencidoSinExcedente(
                                        "{pendiente}") %>"
                                data-parcial="<%= ServicioCobranza.lineaCobroParcial("{monto}") %>"
                                data-parcial-boton="<%= ServicioCobranza.botonCobroParcial("{monto}") %>"><%
                            if (!puedePagarCuotaFicha) { %>&mdash;<%
                            } else if (hayAlgoParaCobrarFicha) { %><%=
                            ServicioCobranza.etiquetaOpcionCobro(situacionCobroFicha) %>: <%=
                            FormateadorNumeros.comoGuaranies(montoPagarCuotaExactoFicha) %><%
                            } else { %><%= ServicioCobranza.etiquetaAdelantoCuotas(1) %>: <%=
                            FormateadorNumeros.comoGuaranies(montoPagarCuotaExactoFicha) %><% } %></strong>
                        <span id="contenedorMontoPagarCuotaRedondeado" <%= pagarCuotaConRedondeoFicha ? "" : "hidden" %>>
                        <br><small id="textoMontoPagarCuotaRedondeado">en efectivo: <%= FormateadorNumeros.comoGuaranies(montoPagarCuotaRedondeadoFicha == null ? BigDecimal.ZERO : montoPagarCuotaRedondeadoFicha) %></small>
                        </span>
                        <%-- B81: solo en un pago parcial. Nunca se ve en el primer render (la ficha
                             abre con "Recibido" vacío), así que lo llena cobranza-ficha.js con esta
                             plantilla. No se reusa el "en efectivo" de arriba (D-141): ese dice otra
                             cosa y los dos juntos nunca corresponden. --%>
                        <span id="contenedorDetalleCobroParcial" hidden>
                        <br><small id="textoDetalleCobroParcial"
                                   data-plantilla="<%= ServicioCobranza.detalleCobroParcial("{exigible}", "{pendiente}") %>"></small>
                        </span>
                    </p>
                    <%-- D-145 (ajuste de Joel): esta línea va SIEMPRE visible (no detrás de "Ver
                         detalle"), antes de que la cajera confirme nada -- usa la misma vista
                         previa ya calculada arriba (calcularPreviewAnticipoParcial, sin tocar
                         ningún servicio).
                         B74 (resuelto el 27/10/2026): este bloque es información del ADELANTO, no
                         de la cancelación total. Con las opciones excluyentes, cobranza-ficha.js
                         lo oculta al elegir "Todo el préstamo", que es justamente lo que el
                         hallazgo B74 reportaba como dato sin sentido ahí. --%>
                    <% if (anticiparFicha && previewAnticipoFicha != null) {
                        Cuota primeraCuotaNuevoPlanFicha = previewAnticipoFicha.getNuevoPlan().get(0);
                        int cuotasRestantesFicha = previewAnticipoFicha.getNuevoPlan().size();
                    %>
                    <div id="bloqueVistaPreviaAnticipo">
                        <p class="small mb-1" id="textoProximaCuotaAnticipo">Su próxima cuota será el
                            <%-- C39 (29/10/2026): la fecha de COBRO (ya corrida a día hábil), no la
                                 teórica -- la arma el único dueño, ServicioCobranza.fechaProximaCuotaNuevoPlan. --%>
                            <strong><%= py.com.capitalcredito.sgcpc.servicios.ServicioCobranza
                                    .fechaProximaCuotaNuevoPlan(primeraCuotaNuevoPlanFicha) %></strong>
                            por <strong><%= FormateadorNumeros.comoGuaranies(primeraCuotaNuevoPlanFicha.getTotalProgramado()) %></strong>;
                            <%-- D-145 (P-14, pedido de Joel): "queda 1 cuota" en singular,
                                 "quedan N cuotas" en plural. --%>
                            le <%= cuotasRestantesFicha == 1 ? "queda" : "quedan" %>
                            <strong><%= cuotasRestantesFicha %> <%= cuotasRestantesFicha == 1 ? "cuota" : "cuotas" %></strong>.</p>
                        <%-- Corrección de diseño del 27/10/2026: el interés que el cliente deja de
                             pagar (RN-11, Ley 6624/2020). Lo calcula el servidor; es informativo,
                             no entra en ningún cobro. --%>
                        <p class="small text-success mb-1" id="textoAhorroAnticipo"
                           <%= ahorroAnticipoCuotasFicha == null || ahorroAnticipoCuotasFicha.compareTo(BigDecimal.ZERO) <= 0
                                   ? "hidden" : "" %>>El cliente se ahorra
                            <%= FormateadorNumeros.comoGuaranies(ahorroAnticipoCuotasFicha == null ? BigDecimal.ZERO
                                    : ahorroAnticipoCuotasFicha) %> de intereses por pagar por adelantado.</p>
                    </div>
                    <% } %>
                    <div class="cc-ticket-vuelto">
                        <span class="cc-ticket-rotulo">Vuelto</span>
                        <span class="font-weight-bold text-right" id="textoVueltoUnificado">&mdash;</span>
                    </div>
                    <%-- Tres usos, los tres los llena cobranza-ficha.js (el color lo pone el
                         script): en rojo, el motivo exacto que da el servidor por el que esa
                         cantidad o ese monto no se pueden adelantar (mínimo de interés del
                         período, D-88, D-90), y el "lo recibido no alcanza" de "Por cantidad de
                         cuotas" y de "Todo el préstamo" (corrección de Joel, 27/10/2026); en
                         gris, el aviso de que falta escribir el monto en "Por monto". Va acá,
                         justo encima del botón, para que la cajera lea el motivo en el mismo
                         lugar donde busca el botón (diseño aprobado por Joel, 27/10/2026). --%>
                    <p class="small mb-2" id="avisoAnticipoBloqueado" hidden></p>
                    <%-- A3a (#2, pedido de Joel): el botón que se toca en el 100 % de los cobros.
                         Mejora de pantalla del 27/10/2026: deja de ser una barra de lado a lado y
                         pasa a tamaño normal al pie del resumen, diciendo exactamente cuánto se va
                         a cobrar ("Cobrar 232.600 Gs.") -- el texto lo arma cobranza-ficha.js con
                         el mismo monto que va a mostrar el cartel de confirmación. --%>
                    <button type="button" class="btn btn-primary cc-boton-cobrar" id="botonCobrarUnificado" disabled>Cobrar</button>
                </div>
            </div>
        </div>
        <%-- Corrección de Joel (380b400, punto A4): "el monto de cada cuota no cambia" era falso
             -- al regenerar el plan con menos capital, el interés (y por lo tanto el total) de la
             nueva cuota 1 varía un poco. "Queda similar" sí es correcto (RN-11: el adelanto
             reduce la CANTIDAD de cuotas, no el monto de cada una). Mejora de pantalla del
             27/10/2026: esta nota pasó al pie de la tarjeta, chica -- es la letra chica del
             adelanto, no un dato del cobro. cobranza-ficha.js la oculta cuando la opción elegida
             no es un adelanto (misma razón que B74). --%>
        <% if (anticiparFicha && previewAnticipoFicha != null) { %>
        <p class="small text-muted mb-0 mt-3" id="notaAnticipoPie">El monto va en múltiplos de 50 Gs.;
            todo lo que pasa del interés del período va a capital. Adelantar reduce la cantidad de
            cuotas; el monto de cada cuota queda similar.</p>
        <% } %>
    </div>
</div>

<form method="post" action="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/cobrar"
      id="formCobrarUnificado" hidden>
    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
    <input type="hidden" name="identificadorConfirmacion" value="<%= identificadorCobroFicha %>">
    <input type="hidden" name="montoRecibido" id="campoMontoFormCobrar">
    <input type="hidden" name="fechaPromesa" id="campoFechaPromesaFormCobrar">
</form>
<form method="post" action="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/cancelar"
      id="formCancelarUnificado" hidden>
    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
    <input type="hidden" name="identificadorConfirmacion" value="<%= identificadorCancelacionFicha %>">
    <input type="hidden" name="montoRecibido" id="campoMontoFormCancelar">
</form>
<form method="post" action="${pageContext.request.contextPath}/cobranza/<%= prestamoFicha.getIdPrestamo() %>/anticipar"
      id="formAnticiparUnificado" hidden>
    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
    <input type="hidden" name="identificadorConfirmacion" value="<%= identificadorAnticipoFicha %>">
    <input type="hidden" name="montoRecibido" id="campoMontoFormAnticipar">
    <%-- D-145 (P-5/P-7, pedido de Joel): el servlet aplica el menor entre esto y "montoRecibido"
         -- así el cliente puede adelantar menos que lo elegido sin tocar el servicio.
         cobranza-ficha.js lo actualiza con el monto que devuelve el servidor para la cantidad de
         cuotas elegida antes de enviar; este valor inicial es el de 1 cuota (por defecto). --%>
    <input type="hidden" name="montoSugerido" id="campoMontoSugeridoFormAnticipar"
           value="<%= montoAnticipoCuotasFicha == null ? "" : montoAnticipoCuotasFicha.toBigInteger() %>">
    <%-- Decisión de Joel (27/10/2026, tras la tercera prueba en pantalla): cuál de las dos formas
         del adelanto se usó. El servidor lo necesita porque el mismo POST sirve a las dos y el
         min(recibido, sugerido) de D-145 (P-5) tiene sentidos opuestos en cada una: en "Por monto"
         es lo que la hace funcionar; en "Por cantidad de cuotas" dejaría cobrar menos que la
         cantidad que dice la tarjeta. cobranza-ficha.js lo escribe al confirmar. --%>
    <input type="hidden" name="modoAnticipo" id="campoModoFormAnticipar" value="cuotas">
</form>
<% } %>
<% } %>
<% } %>

<%-- A3a (#11, pedido de Joel): si se llegó desde un resultado de búsqueda (q en la URL, ver
     _resultados_busqueda.jsp), "Volver a la búsqueda" vuelve a esa misma búsqueda en vez de
     caer siempre en la lista de trabajo -- q va escapado (UTF-8 siempre soportado, el catch es
     solo porque el compilador lo exige). Si se llegó desde la lista de trabajo (sin q), sigue
     igual que siempre: /cobranza, sin query. --%>
<%-- B201 (15/11/2026): y si se llegó por el menú "Cancelación anticipada"
     (origen=cancelacion-anticipada), el "Volver" también conserva ESE dato -- antes volvía a
     "Cobranza" con su lista de trabajo y el usuario perdía el para qué había entrado, no un
     filtro. Es la familia B147/B163/B170/B180/B189 con una vuelta de tuerca: lo que se conserva
     es la intención, no la búsqueda. --%>
<%
    String qVolverFicha = request.getParameter("q");
    String enlaceVolverBusquedaFicha = "/cobranza";
    StringBuilder queryVolverFicha = new StringBuilder();
    if (qVolverFicha != null && !qVolverFicha.trim().isEmpty()) {
        try {
            queryVolverFicha.append("q=").append(java.net.URLEncoder.encode(qVolverFicha, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException errorCodificacionFicha) {
            // No pasa nunca: UTF-8 siempre está disponible.
        }
    }
    // El parámetro se vuelve a leer acá a propósito: TODO el cuerpo de la ficha vive dentro del
    // "if (prestamoFicha != null)" de arriba (línea 24, D-49), que ya cerró, así que la variable de
    // ahí no llega hasta este scriptlet -- es el mismo motivo por el que la línea de arriba vuelve a
    // leer "q". Lo que NO se repite es la decisión: el valor y la comparación tienen un solo dueño
    // (ServicioCobranza.esOrigenCancelacionAnticipada / ORIGEN_CANCELACION_ANTICIPADA).
    if (ServicioCobranza.esOrigenCancelacionAnticipada(request.getParameter("origen"))) {
        if (queryVolverFicha.length() > 0) {
            queryVolverFicha.append('&');
        }
        queryVolverFicha.append("origen=").append(ServicioCobranza.ORIGEN_CANCELACION_ANTICIPADA);
    }
    if (queryVolverFicha.length() > 0) {
        enlaceVolverBusquedaFicha += "?" + queryVolverFicha;
    }
%>
<a href="${pageContext.request.contextPath}<%= enlaceVolverBusquedaFicha %>" class="btn btn-default mt-3">Volver a la búsqueda</a>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
