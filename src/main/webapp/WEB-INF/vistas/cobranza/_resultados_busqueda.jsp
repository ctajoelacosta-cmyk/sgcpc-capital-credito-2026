<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.io.UnsupportedEncodingException" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.CoincidenciaBusquedaCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaMora" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaVencimiento" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.InfoCobroPrestamo" %>
<%-- B201 (15/11/2026): import propio -- este fragmento tambien se sirve SOLO como respuesta
     del pedido AJAX, asi que compila por su cuenta (la leccion de B169). --%>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoBusquedaCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%--
    D-146 (lista de trabajo + búsqueda en vivo de /cobranza, pedido de Joel): fragmento de
    resultados, MISMO mecanismo de "listado vivo" (D-136, recursos/js/listado-vivo.js) que ya usa
    /clientes -- incluido (<%@ include %>) dentro de busqueda.jsp en la carga normal, y reenviado
    tal cual por ControladorCobranzaServlet cuando PeticionAjax.esAjax(request). No decide nada
    por su cuenta: todo (lista de trabajo, mensajes, enlaces) ya viene armado desde el servicio.
--%>
<%
    String textoBusquedaResultados = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultados = textoBusquedaResultados != null && !textoBusquedaResultados.trim().isEmpty();
    ResultadoBusquedaCobranza resultadoBusquedaResultados = (ResultadoBusquedaCobranza) request.getAttribute("resultadoBusqueda");
    @SuppressWarnings("unchecked")
    Map<Long, InfoCobroPrestamo> infoCobroResultados = (Map<Long, InfoCobroPrestamo>) request.getAttribute("infoCobroPorPrestamo");
    // A3a (#11, pedido de Joel): el link "Cobrar" de un resultado de búsqueda lleva el mismo q
    // para que, en la ficha, "Volver a la búsqueda" pueda volver acá en vez de a la lista de
    // trabajo -- q va escapado en la URL.
    // B201 (15/11/2026): y si se entró por el menú "Cancelación anticipada", el link también lleva
    // ESE dato, para que la ficha pueda decirlo y su "Volver" pueda traer de vuelta acá con el
    // título y el aviso propios. Sin esto la ficha nunca se enteraba: el origen vive en el query
    // string y se perdía en el primer clic.
    boolean origenCancelacionResultados =
            ServicioCobranza.esOrigenCancelacionAnticipada(request.getParameter("origen"));
    String queryOrigenResultados = origenCancelacionResultados
            ? "origen=" + ServicioCobranza.ORIGEN_CANCELACION_ANTICIPADA : "";
    String queryQEscapadaResultados = "";
    if (hayBusquedaResultados) {
        try {
            queryQEscapadaResultados = "?q=" + URLEncoder.encode(textoBusquedaResultados, "UTF-8");
        } catch (UnsupportedEncodingException errorCodificacionResultados) {
            // No pasa nunca: UTF-8 siempre está disponible.
        }
    }
    // B201: el sufijo que llevan TODOS los enlaces a una ficha de esta pantalla -- la busqueda (q)
    // y el origen, cada uno si corresponde. Un solo lugar que lo arma, en vez de cuatro enlaces
    // decidiendo por su cuenta (un dato, un dueno).
    String sufijoEnlaceFichaResultados = queryQEscapadaResultados;
    if (!queryOrigenResultados.isEmpty()) {
        sufijoEnlaceFichaResultados += (sufijoEnlaceFichaResultados.isEmpty() ? "?" : "&") + queryOrigenResultados;
    }
%>
<% if (hayBusquedaResultados && resultadoBusquedaResultados != null) { %>
    <% if (!resultadoBusquedaResultados.getPrestamos().isEmpty()) { %>
    <div class="card">
        <div class="card-body">
            <div class="table-responsive">
                <table class="table table-striped table-hover">
                    <thead>
                    <tr><th>Operación</th><th>Cliente</th><th>C.I.</th><th>Próxima cuota</th><th class="cc-monto">Monto a cobrar</th><th>Estado</th><th></th></tr>
                    </thead>
                    <tbody>
                    <% for (PrestamoCredito prestamoBusqueda : resultadoBusquedaResultados.getPrestamos()) {
                        Persona clienteBusqueda = resultadoBusquedaResultados.getClientesPorPrestamo().get(prestamoBusqueda.getIdPrestamo());
                        InfoCobroPrestamo infoCobroBusqueda = infoCobroResultados == null ? null
                                : infoCobroResultados.get(prestamoBusqueda.getIdPrestamo());
                    %>
                    <tr>
                        <td class="celda-codigo"><%= prestamoBusqueda.getIdentificadorOperacion() %></td>
                        <td><% if (clienteBusqueda != null) { %><%= clienteBusqueda.getNombres() %> <%= clienteBusqueda.getApellidos() %><% } %></td>
                        <td><%= clienteBusqueda == null ? "-" : FormateadorTexto.comoCedulaConPuntos(clienteBusqueda.getCedulaIdentidad()) %></td>
                        <% if (infoCobroBusqueda != null) { %>
                        <td><%= infoCobroBusqueda.getProximaCuota() %> — vence <%= infoCobroBusqueda.getProximoVencimiento() %></td>
                        <td class="cc-monto"><%= infoCobroBusqueda.getACobrarHoy() %></td>
                        <td><% if (infoCobroBusqueda.isAlDia()) { %>
                            <span class="text-success">Al día</span>
                            <% } else { %>
                            <span class="text-danger"><%= infoCobroBusqueda.getDiasAtraso() %> día(s) de atraso</span>
                            <% } %></td>
                        <% } else { %>
                        <td>-</td><td>-</td><td>-</td>
                        <% } %>
                        <td><a href="${pageContext.request.contextPath}/cobranza/<%= prestamoBusqueda.getIdPrestamo() %><%= sufijoEnlaceFichaResultados %>"
                               class="btn btn-sm btn-primary">Cobrar</a></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <% } else if (!resultadoBusquedaResultados.getCoincidencias().isEmpty()) { %>
    <%-- A3b (#6, pedido de Joel): texto ambiguo, 2+ clientes, ninguno con préstamo cobrable --
         lista de coincidencias (máximo 10, ya recortada por el servicio) en vez del mensaje
         genérico, con la misma situación por cliente que el caso de un solo cliente de abajo. --%>
    <div class="alert alert-warning mb-2"><%= resultadoBusquedaResultados.getMensajeSinResultados() %></div>
    <div class="card">
        <div class="card-body">
            <div class="table-responsive">
                <table class="table table-sm table-striped mb-0">
                    <thead><tr><th>Cliente</th><th>C.I.</th><th>Situación</th><th></th></tr></thead>
                    <tbody>
                    <% for (CoincidenciaBusquedaCobranza coincidenciaBusqueda : resultadoBusquedaResultados.getCoincidencias()) { %>
                    <tr>
                        <td><%= coincidenciaBusqueda.getNombreCompleto() %></td>
                        <td><%= coincidenciaBusqueda.getCedula() %></td>
                        <td><%= coincidenciaBusqueda.getSituacion() == null ? "-" : coincidenciaBusqueda.getSituacion() %></td>
                        <td>
                            <% if (coincidenciaBusqueda.getIdPrestamoEnlace() != null) {
                                String enlaceCoincidencia;
                                String etiquetaEnlaceCoincidencia;
                                if (coincidenciaBusqueda.isEnlaceADesembolso()) {
                                    enlaceCoincidencia = "/desembolso/" + coincidenciaBusqueda.getIdPrestamoEnlace();
                                    etiquetaEnlaceCoincidencia = "Ir a Desembolsos";
                                } else if (coincidenciaBusqueda.isEnlaceAFormalizacion()) {
                                    // RF20 (pedido de Joel, 13/10/2026): préstamo anulado antes
                                    // del desembolso -- solo se ofrece si el perfil puede ver
                                    // Formalización (ya resuelto en el servicio).
                                    enlaceCoincidencia = "/formalizacion/" + coincidenciaBusqueda.getIdPrestamoEnlace();
                                    etiquetaEnlaceCoincidencia = "Ver ficha de formalización";
                                } else {
                                    enlaceCoincidencia = "/cobranza/" + coincidenciaBusqueda.getIdPrestamoEnlace();
                                    etiquetaEnlaceCoincidencia = "Ver historial";
                                }
                            %>
                            <a href="${pageContext.request.contextPath}<%= enlaceCoincidencia %>" class="btn btn-sm btn-outline-primary"><%= etiquetaEnlaceCoincidencia %></a>
                            <% } %>
                        </td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
            <% if (resultadoBusquedaResultados.isHayMasCoincidencias()) { %>
            <p class="text-muted small mb-0 mt-2">Hay más coincidencias que no se muestran. Refiná la búsqueda.</p>
            <% } %>
        </div>
    </div>
    <% } else { %>
    <div class="alert alert-warning">
        <%= resultadoBusquedaResultados.getMensajeSinResultados() %>
        <% if (resultadoBusquedaResultados.getIdPrestamoEnlace() != null) {
            String enlaceBusqueda;
            String etiquetaEnlaceBusqueda;
            if (resultadoBusquedaResultados.isEnlaceADesembolso()) {
                enlaceBusqueda = "/desembolso/" + resultadoBusquedaResultados.getIdPrestamoEnlace();
                etiquetaEnlaceBusqueda = "Ir a Desembolsos";
            } else if (resultadoBusquedaResultados.isEnlaceAFormalizacion()) {
                // RF20 (pedido de Joel, 13/10/2026): préstamo anulado antes del desembolso --
                // solo se ofrece si el perfil puede ver Formalización (ya resuelto en el servicio).
                enlaceBusqueda = "/formalizacion/" + resultadoBusquedaResultados.getIdPrestamoEnlace();
                etiquetaEnlaceBusqueda = "Ver ficha de formalización";
            } else {
                enlaceBusqueda = "/cobranza/" + resultadoBusquedaResultados.getIdPrestamoEnlace();
                etiquetaEnlaceBusqueda = "Ver historial";
            }
        %>
        <a href="${pageContext.request.contextPath}<%= enlaceBusqueda %>"><%= etiquetaEnlaceBusqueda %></a>
        <% } %>
    </div>
    <% } %>
<% } else { %>
    <%
        @SuppressWarnings("unchecked")
        List<FilaMora> trabajoEnMoraResultados = (List<FilaMora>) request.getAttribute("trabajoEnMora");
        // B197: el total del bloque "En mora" (el de la pildora). Respaldo para esta JSP contra un
        // .class viejo, antes del Clean and Build (punto 11b).
        Integer totalEnMoraResultados = (Integer) request.getAttribute("totalEnMora");
        if (totalEnMoraResultados == null) {
            totalEnMoraResultados = trabajoEnMoraResultados == null ? 0 : trabajoEnMoraResultados.size();
        }
        @SuppressWarnings("unchecked")
        List<FilaVencimiento> trabajoVencenHoyResultados = (List<FilaVencimiento>) request.getAttribute("trabajoVencenHoy");
        @SuppressWarnings("unchecked")
        List<FilaVencimiento> trabajoProximos7DiasResultados = (List<FilaVencimiento>) request.getAttribute("trabajoProximos7Dias");
    %>
    <%-- A3a (#3, pedido de Joel): "En mora" primero (lo más urgente), después "Vencen hoy",
         después "Próximos 7 días" -- solo se reordenaron los bloques, ninguna consulta ni dato
         cambió. --%>
    <%-- B196 (15/11/2026): un bloque vacío ya no dibuja su tarjeta. Antes alcanzaba con que la
         lista no fuera null, así que con 0 filas quedaba el encabezado de color, los títulos de
         columna y nada debajo -- hoy pasaba con "Próximos 7 días 0". Es el mismo ruido que el
         ítem 5 del informe de Formalización: un vacío con marco se lee como "algo falló". Cuando
         los TRES están vacíos, el aviso de abajo lo dice en una sola línea. --%>
    <% if (trabajoEnMoraResultados != null && !trabajoEnMoraResultados.isEmpty()) { %>
    <div class="card card-danger card-outline mb-3">
        <div class="card-header bg-danger">
            <%-- B197 (15/11/2026): la pildora cuenta el TOTAL, no las filas de la pagina -- con 69
                 en mora y 20 por pagina, contar lo dibujado diria "20". --%>
            <h5 class="m-0 text-white">En mora <span class="badge badge-light"><%= totalEnMoraResultados %></span></h5>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-sm table-striped mb-0">
                    <thead><tr><th>Cliente</th><th>C.I.</th><th>Operación</th><th>Cuota</th><th class="cc-monto">Monto exigible</th><th>Vencimiento</th><th>Atraso</th><th></th></tr></thead>
                    <tbody>
                    <% if (trabajoEnMoraResultados.isEmpty()) { %>
                    <tr><td colspan="8" class="text-muted">Ningún préstamo en mora.</td></tr>
                    <% } %>
                    <% for (FilaMora filaMoraTrabajo : trabajoEnMoraResultados) { %>
                    <tr>
                        <td><%= filaMoraTrabajo.getClienteNombre() %>
                            <% if (filaMoraTrabajo.isPromesaVencida()) { %><span class="badge badge-danger ml-1">Promesa vencida</span><% } %></td>
                        <td><%= filaMoraTrabajo.getClienteCedula() %></td>
                        <td class="celda-codigo"><%= filaMoraTrabajo.getIdentificadorOperacion() %></td>
                        <td><%= filaMoraTrabajo.getNumeroCuotaMasAntigua() %>/<%= filaMoraTrabajo.getTotalCuotas() %>
                            <% if (filaMoraTrabajo.getCuotasVencidas() > 1) { %><small class="text-muted">(+<%= filaMoraTrabajo.getCuotasVencidas() - 1 %> más)</small><% } %></td>
                        <td class="cc-monto"><%= filaMoraTrabajo.getMontoExigible() %></td>
                        <td><%= filaMoraTrabajo.getFechaVencimientoMasAntigua() %></td>
                        <td><%= filaMoraTrabajo.getDiasAtraso() %> día(s)</td>
                        <td><a href="${pageContext.request.contextPath}/cobranza/<%= filaMoraTrabajo.getIdPrestamo() %><%= sufijoEnlaceFichaResultados %>" class="btn btn-sm btn-primary">Cobrar</a></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <%-- B197: la paginacion del bloque "En mora". Esta pantalla tiene UNA sola, asi que usa el
         parametro "pagina" de siempre y pasa por listado-vivo.js, como Clientes o Solicitudes.
         Va DENTRO del fragmento de resultados, que es lo que reemplaza el pedido AJAX. --%>
    <% request.setAttribute("paginacionUrlBase", request.getContextPath() + "/cobranza?"); %>
    <%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
    <% } %>

    <% if (trabajoVencenHoyResultados != null && !trabajoVencenHoyResultados.isEmpty()) { %>
    <div class="card card-warning card-outline mb-3">
        <div class="card-header bg-warning">
            <h5 class="m-0">Vencen hoy <span class="badge badge-dark"><%= trabajoVencenHoyResultados.size() %></span></h5>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-sm table-striped mb-0">
                    <thead><tr><th>Cliente</th><th>C.I.</th><th>Operación</th><th>Cuota</th><th class="cc-monto">Monto</th><th>Vencimiento</th><th></th></tr></thead>
                    <tbody>
                    <% if (trabajoVencenHoyResultados.isEmpty()) { %>
                    <tr><td colspan="7" class="text-muted">Nada vence hoy.</td></tr>
                    <% } %>
                    <% for (FilaVencimiento filaHoy : trabajoVencenHoyResultados) { %>
                    <tr>
                        <td><%= filaHoy.getClienteNombre() %></td>
                        <td><%= filaHoy.getClienteCedula() %></td>
                        <td class="celda-codigo"><%= filaHoy.getIdentificadorOperacion() %></td>
                        <td><%= filaHoy.getNumeroCuota() %>/<%= filaHoy.getTotalCuotas() %></td>
                        <td class="cc-monto"><%= filaHoy.getMonto() %></td>
                        <td><%= filaHoy.getFechaVencimiento() %></td>
                        <td><a href="${pageContext.request.contextPath}/cobranza/<%= filaHoy.getIdPrestamo() %><%= sufijoEnlaceFichaResultados %>" class="btn btn-sm btn-primary">Cobrar</a></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <% } %>

    <% if (trabajoProximos7DiasResultados != null && !trabajoProximos7DiasResultados.isEmpty()) { %>
    <div class="card card-secondary card-outline mb-3">
        <div class="card-header bg-secondary">
            <h5 class="m-0 text-white">Próximos 7 días <span class="badge badge-light"><%= trabajoProximos7DiasResultados.size() %></span></h5>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-sm table-striped mb-0">
                    <thead><tr><th>Cliente</th><th>C.I.</th><th>Operación</th><th>Cuota</th><th class="cc-monto">Monto</th><th>Vencimiento</th><th></th></tr></thead>
                    <tbody>
                    <% if (trabajoProximos7DiasResultados.isEmpty()) { %>
                    <tr><td colspan="7" class="text-muted">Nada vence en los próximos 7 días.</td></tr>
                    <% } %>
                    <% for (FilaVencimiento filaProxima : trabajoProximos7DiasResultados) { %>
                    <tr>
                        <td><%= filaProxima.getClienteNombre() %></td>
                        <td><%= filaProxima.getClienteCedula() %></td>
                        <td class="celda-codigo"><%= filaProxima.getIdentificadorOperacion() %></td>
                        <td><%= filaProxima.getNumeroCuota() %>/<%= filaProxima.getTotalCuotas() %></td>
                        <td class="cc-monto"><%= filaProxima.getMonto() %></td>
                        <td><%= filaProxima.getFechaVencimiento() %></td>
                        <td><a href="${pageContext.request.contextPath}/cobranza/<%= filaProxima.getIdPrestamo() %><%= sufijoEnlaceFichaResultados %>" class="btn btn-sm btn-primary">Cobrar</a></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <% } %>

    <%-- B196: y si no hay NADA en los tres bloques, una sola línea que lo diga -- antes se veían
         tres tarjetas vacías, que es la forma más confusa de decir "no hay trabajo". En verde,
         porque no tener cobranza pendiente no es un problema (mismo criterio que el
         "No hay préstamos en mora en este momento." de /mora). --%>
    <% if ((trabajoEnMoraResultados == null || trabajoEnMoraResultados.isEmpty())
            && (trabajoVencenHoyResultados == null || trabajoVencenHoyResultados.isEmpty())
            && (trabajoProximos7DiasResultados == null || trabajoProximos7DiasResultados.isEmpty())) { %>
    <div class="alert alert-success mb-0">No hay cobranza pendiente: nada en mora, nada que venza hoy
        y nada en los próximos 7 días. Para cobrar una cuota que vence más adelante, busque el
        préstamo por cédula, nombre o número de operación.</div>
    <% } %>
<% } %>
