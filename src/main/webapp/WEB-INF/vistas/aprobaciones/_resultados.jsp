<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaBandejaAprobacion" %>
<%--
    Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): fragmento de
    resultados de /aprobaciones -- mismo patrón que mora/_resultados.jsp: incluido (<%@ include %>)
    dentro de lista.jsp en la carga normal, y como respuesta completa de un pedido AJAX
    (ControladorAprobacionesServlet.mostrarLista, PeticionAjax.esAjax). A diferencia de Mora, esta
    lista no tiene modales ni formularios adentro (solo enlaces "Revisar"/"Ver" a la ficha), así
    que no hace falta ningún evento para re-atar nada después de un filtrado.
--%>
<%
    @SuppressWarnings("unchecked")
    List<FilaBandejaAprobacion> listaFilasResultado = (List<FilaBandejaAprobacion>) request.getAttribute("filas");
    boolean vistaHistorialResultado = Boolean.TRUE.equals(request.getAttribute("vistaHistorial"));
    String textoBusquedaResultado = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultado = textoBusquedaResultado != null && !textoBusquedaResultado.trim().isEmpty();
%>
<%-- B132 (11/11/2026): cuántos resultados hay, igual que en Solicitudes y Préstamos. --%>
<%-- B175 (14/11/2026): el contador cuenta el TOTAL, no las filas de la página. Desde que la
     bandeja pagina, listaFilasResultado.size() son 20 como máximo: dejarlo así habría hecho que el
     Historial dijera "20 solicitudes encontradas" sobre 409. El total lo pone el controlador, que
     es el que corta la página. El respaldo (size()) es para una sola situación: esta JSP contra un
     .class viejo, antes del Clean and Build. --%>
<p class="text-muted mb-2"><%
    Integer totalResultadosAprobaciones = (Integer) request.getAttribute("totalResultados");
    int totalAprobacionesResultado = totalResultadosAprobaciones != null ? totalResultadosAprobaciones
            : (listaFilasResultado == null ? 0 : listaFilasResultado.size());
%><%= FormateadorNumeros.comoCantidad(totalAprobacionesResultado) %>
    solicitud<%= totalAprobacionesResultado == 1 ? "" : "es" %>
    encontrada<%= totalAprobacionesResultado == 1 ? "" : "s" %>.</p>
<div class="card">
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <%-- B175 (14/11/2026): encabezados que ordenan, en las dos pestañas. Cada una
                     tiene sus columnas, así que cada una tiene su tabla de encabezados y su lista
                     blanca en el servicio: en Pendientes no existe "Fecha de decisión" y en el
                     Historial no existe "Enviada por".
                     El enlace es un href real con los filtros actuales (incluido vista=historial);
                     ordenar pasa por el servidor, que ordena la lista COMPLETA antes de cortar la
                     página. Mismo mecanismo y mismas clases que Solicitudes (B167) y Clientes (B157).
                     Las clases de ancho salieron de las dos pruebas de Joel en pantalla:
                     cc-sin-partir (§13 del tema, probando B174) la llevan TODAS las columnas menos
                     "Cliente" -- su contenido es una sola unidad (un código, una fecha, un monto
                     con su "Gs.", un plazo, un porcentaje, un nombre de usuario, una etiqueta) y
                     además así la flecha del encabezado no baja de renglón, que es lo que Joel
                     marcó en Clientes (B157). "Cliente" lleva cc-celda-nombre (§14, probando B175):
                     con 9 columnas que no se parten, al nombre le sobraba tan poco que se partía en
                     4-5 renglones, así que tiene un ancho MÍNIMO y sigue pudiendo bajar de
                     renglón -- lo que esa columna no puede es quedar en una sola línea.
                     "Esperando" NO ordena a propósito: es el mismo dato que "Enviada" contado de
                     otra manera, y dos controles de orden sobre el mismo dato se contradirían
                     entre sí (B123). --%>
                <%
                    String ordenActualTabla = (String) request.getAttribute("ordenActual");
                    String sentidoActualTabla = (String) request.getAttribute("sentidoActual");
                    // Respaldo para la única situación en que no llegan: esta JSP contra un .class
                    // viejo, antes del Clean and Build. El dueño del valor es
                    // ServicioAprobacionCredito.ordenPorDefecto; acá va el literal para no
                    // referenciar desde la JSP una constante nueva (punto 11b).
                    if (ordenActualTabla == null) {
                        ordenActualTabla = vistaHistorialResultado ? "fechadecision" : "enviada";
                    }
                    if (sentidoActualTabla == null) {
                        sentidoActualTabla = vistaHistorialResultado ? "desc" : "asc";
                    }
                    String queryFiltrosTabla = (String) request.getAttribute("queryStringFiltros");
                    if (queryFiltrosTabla == null) { queryFiltrosTabla = ""; }
                    String prefijoFiltrosTabla = queryFiltrosTabla.isEmpty() ? "" : queryFiltrosTabla + "&";
                    // {rótulo, clave de orden (null = no ordena), clase de la celda}
                    String[][] columnasTabla = vistaHistorialResultado
                            ? new String[][] {
                                {"Código", "codigo", "cc-sin-partir"},
                                {"Enviada", "enviada", "cc-sin-partir"},
                                {"Cliente", "cliente", "cc-celda-nombre"},
                                {"Monto solicitado", "monto", "cc-sin-partir cc-monto"},
                                {"Plazo", "plazo", "cc-sin-partir"},
                                {"Afectación", "afectacion", "cc-sin-partir"},
                                {"Decisión", "decision", "cc-sin-partir"},
                                {"Decidida por", "decididapor", "cc-sin-partir"},
                                {"Fecha de decisión", "fechadecision", "cc-sin-partir"},
                                {"", null, ""}}
                            : new String[][] {
                                {"Código", "codigo", "cc-sin-partir"},
                                {"Enviada", "enviada", "cc-sin-partir"},
                                {"Esperando", null, "cc-sin-partir"},
                                {"Cliente", "cliente", "cc-celda-nombre"},
                                {"Monto solicitado", "monto", "cc-sin-partir cc-monto"},
                                {"Plazo", "plazo", "cc-sin-partir"},
                                {"Afectación", "afectacion", "cc-sin-partir"},
                                {"Enviada por", "enviadapor", "cc-sin-partir"},
                                {"", null, ""}};
                %>
                <thead>
                <tr>
                <% for (String[] columnaTabla : columnasTabla) {
                       if (columnaTabla[1] == null) { %>
                    <th class="<%= columnaTabla[2] %>"><%= columnaTabla[0] %></th>
                <%     } else {
                           boolean esColumnaOrdenada = columnaTabla[1].equals(ordenActualTabla);
                           String sentidoDelEnlace = esColumnaOrdenada && "asc".equals(sentidoActualTabla) ? "desc" : "asc";
                           String iconoOrden = !esColumnaOrdenada ? "fa-sort"
                                   : ("asc".equals(sentidoActualTabla) ? "fa-sort-up" : "fa-sort-down"); %>
                    <th class="<%= columnaTabla[2] %>"><a class="cc-orden-columna"
                           title="Ordenar por <%= columnaTabla[0] %>"
                           href="${pageContext.request.contextPath}/aprobaciones?<%= prefijoFiltrosTabla %>orden=<%= columnaTabla[1] %>&sentido=<%= sentidoDelEnlace %>"><%=
                           columnaTabla[0] %> <i class="fas <%= iconoOrden %>"></i></a></th>
                <%     } %>
                <% } %>
                </tr>
                </thead>
                <tbody>
                <% if (listaFilasResultado == null || listaFilasResultado.isEmpty()) { %>
                <%-- B175: el colspan sale de la tabla de encabezados, no de un número escrito a
                     mano -- justo lo que se olvidó al agregar la columna de B174 (quedó en 8 con 9
                     columnas) y hay que acordarse cada vez que una pestaña gana o pierde una. --%>
                <tr><td colspan="<%= columnasTabla.length %>" class="text-muted">
                    <% if (hayBusquedaResultado) { %>
                    No se encontraron solicitudes para esa búsqueda.
                    <% } else if (vistaHistorialResultado) { %>
                    Todavía no hay solicitudes decididas.
                    <% } else { %>
                    No hay solicitudes pendientes de aprobación.
                    <br><small>Cuando un oficial envíe una solicitud, aparecerá aquí en orden de llegada.</small>
                    <% } %>
                </td></tr>
                <% } %>
                <% if (listaFilasResultado != null) { for (FilaBandejaAprobacion filaResultado : listaFilasResultado) { %>
                <tr>
                    <td class="celda-codigo cc-sin-partir"><%= filaResultado.getCodigo() %></td>
                    <td class="cc-sin-partir"><%= filaResultado.getFechaEnvioFormateada() %></td>
                    <% if (!vistaHistorialResultado) { %>
                    <td class="cc-sin-partir"><%= filaResultado.getEsperando() %></td>
                    <% } %>
                    <td class="cc-celda-nombre"><%= filaResultado.getClienteTexto() %></td>
                    <td class="cc-sin-partir cc-monto"><%= filaResultado.getMontoSolicitadoFormateado() %></td>
                    <td class="cc-sin-partir"><%= filaResultado.getPlazoTexto() %></td>
                    <td class="cc-sin-partir"><%= filaResultado.getAfectacionFormateada() %></td>
                    <% if (vistaHistorialResultado) { %>
                    <td class="cc-sin-partir"><span class="badge badge-secondary"><%= filaResultado.getDecisionEtiqueta() %></span></td>
                    <td class="cc-sin-partir"><%= filaResultado.getDecididaPorNombre() %></td>
                    <td class="cc-sin-partir"><%= filaResultado.getFechaDecisionFormateada() %></td>
                    <% } else { %>
                    <td class="cc-sin-partir"><%= filaResultado.getEnviadaPorNombre() %></td>
                    <% } %>
                    <td>
                        <a href="${pageContext.request.contextPath}/aprobaciones/<%= filaResultado.getIdSolicitud() %>" class="btn btn-sm btn-info"><%= vistaHistorialResultado ? "Ver" : "Revisar" %></a>
                    </td>
                </tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%-- B175 (14/11/2026): la paginación es el fragmento compartido con Clientes y Solicitudes
     (plantilla/paginacion.jsp, decisión de Joel en B167: un solo dueño del marcado). Esta pantalla
     solo arma la URL base, con los filtros y el orden vigentes y terminada en "&", porque el
     fragmento le pega "pagina=N" y nada más. --%>
<%
    String prefijoPaginaAprobaciones = (queryFiltrosTabla.isEmpty() ? "?" : "?" + queryFiltrosTabla + "&")
            + "orden=" + ordenActualTabla + "&sentido=" + sentidoActualTabla + "&";
    request.setAttribute("paginacionUrlBase",
            request.getContextPath() + "/aprobaciones" + prefijoPaginaAprobaciones);
%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
