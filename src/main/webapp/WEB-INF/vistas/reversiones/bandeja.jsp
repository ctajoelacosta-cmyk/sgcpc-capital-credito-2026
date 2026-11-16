<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.ReversionPago" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Pago" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Factura" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<ReversionPago> solicitadasBandeja = (List<ReversionPago>) request.getAttribute("solicitadas");
    @SuppressWarnings("unchecked")
    Map<Long, Pago> pagoPorReversionBandeja = (Map<Long, Pago>) request.getAttribute("pagoPorReversion");
    @SuppressWarnings("unchecked")
    Map<Long, PrestamoCredito> prestamoPorReversionBandeja = (Map<Long, PrestamoCredito>) request.getAttribute("prestamoPorReversion");
    @SuppressWarnings("unchecked")
    Map<Long, Factura> facturaPorReversionBandeja = (Map<Long, Factura>) request.getAttribute("facturaPorReversion");
    @SuppressWarnings("unchecked")
    Map<Long, String> solicitantePorReversionBandeja = (Map<Long, String>) request.getAttribute("solicitantePorReversion");
    @SuppressWarnings("unchecked")
    Map<Long, String> decisorPorReversionBandeja = (Map<Long, String>) request.getAttribute("decisorPorReversion");
    @SuppressWarnings("unchecked")
    Map<Long, String> clientePorReversionBandeja = (Map<Long, String>) request.getAttribute("clientePorReversion");
    boolean vistaHistorialBandeja = Boolean.TRUE.equals(request.getAttribute("vistaHistorial"));
    // C30: por reversión pendiente, el aviso de que ya hay una operación posterior y por lo tanto
    // autorizar va a fallar. Lo decide ServicioReversiones (mismo criterio que la autorización).
    @SuppressWarnings("unchecked")
    Map<Long, String> avisoOperacionPosteriorBandeja =
            (Map<Long, String>) request.getAttribute("avisoOperacionPosteriorPorReversion");
%>
<h1>Reversiones</h1>
<p class="text-muted"><%= vistaHistorialBandeja
        ? "Reversiones ya decididas (autorizadas o rechazadas)."
        : "Solicitudes de reversión de cobro pendientes de autorizar. Solo se puede revertir la operación más "
            + "reciente de cada préstamo, y hace falta una caja abierta hoy con efectivo suficiente." %></p>

<% if (request.getAttribute("mensajeError") != null) { %>
<div class="alert alert-danger"><%= request.getAttribute("mensajeError") %></div>
<% } %>

<%-- Punto 12 (pedido de Joel): pestaña Historial, mismo patrón que /aprobaciones. --%>
<ul class="nav nav-tabs mb-3">
    <li class="nav-item"><a class="nav-link <%= vistaHistorialBandeja ? "" : "active" %>"
                             href="${pageContext.request.contextPath}/reversiones">Pendientes</a></li>
    <li class="nav-item"><a class="nav-link <%= vistaHistorialBandeja ? "active" : "" %>"
                             href="${pageContext.request.contextPath}/reversiones?vista=historial">Historial</a></li>
</ul>

<div class="card">
    <div class="card-header"><h5 class="m-0"><%= vistaHistorialBandeja ? "Autorizadas o rechazadas" : "Pendientes de autorizar" %></h5></div>
    <div class="card-body">
        <% if (solicitadasBandeja == null || solicitadasBandeja.isEmpty()) { %>
        <p class="text-muted mb-0"><%= vistaHistorialBandeja ? "Sin reversiones decididas todavía."
                : "No hay solicitudes pendientes." %></p>
        <% } else { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped align-middle">
                <thead>
                <% if (vistaHistorialBandeja) { %>
                <%-- Punto 4 (RF25, pedido de Joel): fecha y motivo de la solicitud, separados de
                     la fecha y el motivo de la decisión. --%>
                <tr>
                    <th>Fecha de la solicitud</th><th>Operación</th><th>Cliente</th><th class="cc-monto">Monto</th>
                    <th>Motivo de la solicitud</th><th>Solicitó</th><th>Estado</th>
                    <th>Fecha de la decisión</th><th>Motivo de la decisión</th><th>Autorizó/Rechazó</th>
                    <th>Comprobante</th>
                </tr>
                <% } else { %>
                <tr>
                    <th>Solicitada</th>
                    <th>Cajera</th>
                    <th>Operación</th>
                    <th>Recibo</th>
                    <th class="cc-monto">Monto</th>
                    <th>Motivo</th>
                    <th style="min-width: 320px">Acción</th>
                </tr>
                <% } %>
                </thead>
                <tbody>
                <% for (ReversionPago reversion : solicitadasBandeja) {
                    Pago pagoFila = pagoPorReversionBandeja.get(reversion.getIdReversionPago());
                    PrestamoCredito prestamoFila = prestamoPorReversionBandeja.get(reversion.getIdReversionPago());
                    Factura facturaFila = facturaPorReversionBandeja.get(reversion.getIdReversionPago());
                %>
                <% if (vistaHistorialBandeja) { %>
                <tr>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(reversion.getFechaHoraSolicitud()) %></td>
                    <td class="celda-codigo"><%= prestamoFila == null ? "-" : prestamoFila.getIdentificadorOperacion() %></td>
                    <td><%= clientePorReversionBandeja.get(reversion.getIdReversionPago()) %></td>
                    <td class="cc-monto"><%= pagoFila == null ? "-" : FormateadorNumeros.comoGuaranies(pagoFila.getMontoAplicado()) %></td>
                    <td><%= reversion.getMotivoReversion() %></td>
                    <td><%= solicitantePorReversionBandeja.get(reversion.getIdReversionPago()) %></td>
                    <td><span class="badge <%= reversion.getEstado().name().equals("RECHAZADA") ? "badge-danger" : "badge-success" %>">
                        <%= reversion.getEstado().name().equals("RECHAZADA") ? "Rechazada" : "Autorizada" %></span></td>
                    <td><%= reversion.getFechaHoraReversion() != null
                            ? FormateadorFechas.comoDdMmAaaaHhMm(reversion.getFechaHoraReversion()) : "-" %></td>
                    <td><%= reversion.getEstado().name().equals("RECHAZADA") ? reversion.getMotivoRechazo() : "-" %></td>
                    <td><%= decisorPorReversionBandeja.get(reversion.getIdReversionPago()) %></td>
                    <td>
                        <%-- Punto 2 (RF25, pedido de Joel): reimprimir la constancia desde el
                             historial de reversiones -- solo existe para una AUTORIZADA. --%>
                        <% if (!reversion.getEstado().name().equals("RECHAZADA") && prestamoFila != null) { %>
                        <a class="btn btn-sm btn-outline-secondary"
                           href="${pageContext.request.contextPath}/cobranza/<%= prestamoFila.getIdPrestamo() %>/reversion/<%= reversion.getIdReversionPago() %>">Constancia</a>
                        <% } %>
                    </td>
                </tr>
                <% } else { %>
                <tr>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(reversion.getFechaHoraSolicitud()) %></td>
                    <td><%= solicitantePorReversionBandeja.get(reversion.getIdReversionPago()) %></td>
                    <td class="celda-codigo"><%= prestamoFila == null ? "-" : prestamoFila.getIdentificadorOperacion() %></td>
                    <td class="celda-codigo"><%= pagoFila == null ? "-" : pagoFila.getNumeroRecibo() %></td>
                    <td class="cc-monto"><%= pagoFila == null ? "-" : FormateadorNumeros.comoGuaranies(pagoFila.getMontoAplicado()) %></td>
                    <td><%= reversion.getMotivoReversion() %></td>
                    <td>
                        <%-- C30 (decisión de Joel, 03/11/2026): si ya hay una operación posterior
                             sobre ese préstamo, autorizar va a fallar -- se dice acá, antes de que
                             el gerente lo intente, y no se oculta el botón: la decisión sigue
                             siendo suya (rechazar es lo único que va a poder hacer). El texto sale
                             de ServicioReversiones. --%>
                        <% String avisoPosteriorFila = avisoOperacionPosteriorBandeja == null ? null
                                : avisoOperacionPosteriorBandeja.get(reversion.getIdReversionPago());
                           if (avisoPosteriorFila != null) { %>
                        <div class="alert alert-warning py-1 px-2 mb-1 small"><%= avisoPosteriorFila %></div>
                        <% } %>
                        <%-- A3c (pedido de Joel): confirmación con foco en "Cancelar" -- autorizar
                             una reversión es irreversible. Punto 3 (RF25, pedido de Joel): el
                             texto incluye el recibo y el monto reales. --%>
                        <form method="post" action="${pageContext.request.contextPath}/reversiones/autorizar/<%= reversion.getIdReversionPago() %>" class="form-inline mb-1"
                              data-confirmar-titulo="¿Autorizar la reversión del cobro <%= pagoFila == null ? "-" : pagoFila.getNumeroRecibo() %> por <%= pagoFila == null ? "-" : FormateadorNumeros.comoGuaranies(pagoFila.getMontoAplicado()) %>?"
                              data-confirmar="No se puede deshacer."
                              data-confirmar-foco="cancelar"
                              data-confirmar-boton="Sí, autorizar">
                            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                            <%-- 13/10/2026 (pedido de Joel): la constancia de devolución ya se generó
                                 (DEV-...) pero el cierre documental todavía no está COMPLETO -- eso
                                 significa que el pagaré podría estar ya en trámite de devolución;
                                 esta reversión todavía se permite (el bloqueo real es con el cierre
                                 COMPLETO, ver ServicioReversiones), pero el gerente tiene que verificar
                                 a mano antes de autorizar. --%>
                            <% if (prestamoFila != null && prestamoFila.getNumeroConstanciaDevolucion() != null
                                    && !"COMPLETO".equals(prestamoFila.getEstadoCierreDocumental())) { %>
                            <div class="alert alert-warning small py-1 px-2 mb-1" style="width: 100%">
                                Ya se generó la constancia de devolución del pagaré. Verifique que el pagaré
                                original siga en poder de la empresa antes de autorizar.
                            </div>
                            <% } %>
                            <% if (facturaFila != null) { %>
                            <%-- Punto 3 (RF25, factura, pedido de Joel): la cajera ya tuvo que
                                 declarar esto para que esta solicitud pudiera existir
                                 (ServicioReversiones.validarDeclaracionFacturaCajera la exige al
                                 solicitar) -- doble control, no sustituye la casilla del gerente. --%>
                            <div class="alert alert-light border small py-1 px-2 mb-1" style="width: 100%">
                                La cajera declaró tener en su poder el original y las copias de la factura
                                <%= facturaFila.getNumeroCompleto() %>.
                            </div>
                            <div class="form-check mr-2">
                                <input type="checkbox" class="form-check-input" name="facturaEnPoderConfirmado" value="true" required id="factura<%= reversion.getIdReversionPago() %>">
                                <label class="form-check-label small" for="factura<%= reversion.getIdReversionPago() %>">
                                    Tengo el original y las copias de la factura <%= facturaFila.getNumeroCompleto() %> en mi poder
                                </label>
                                <div class="text-muted small">Si el cliente ya se llevó la factura, corresponde Nota de Crédito
                                    según la normativa de la DNIT; no autorice.</div>
                            </div>
                            <% } %>
                            <button type="submit" class="btn btn-sm btn-success">Autorizar</button>
                        </form>
                        <form method="post" action="${pageContext.request.contextPath}/reversiones/rechazar/<%= reversion.getIdReversionPago() %>" class="form-inline"
                              data-confirmar-titulo="¿Rechazar la reversión del cobro <%= pagoFila == null ? "-" : pagoFila.getNumeroRecibo() %> por <%= pagoFila == null ? "-" : FormateadorNumeros.comoGuaranies(pagoFila.getMontoAplicado()) %>?"
                              data-confirmar=""
                              data-confirmar-foco="cancelar"
                              data-confirmar-boton="Sí, rechazar">
                            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                            <input type="text" name="motivoRechazo" class="form-control form-control-sm mr-2" placeholder="Motivo del rechazo" required>
                            <button type="submit" class="btn btn-sm btn-outline-danger">Rechazar</button>
                        </form>
                    </td>
                </tr>
                <% } %>
                <% } %>
                </tbody>
            </table>
        </div>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
