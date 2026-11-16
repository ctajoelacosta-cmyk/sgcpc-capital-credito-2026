<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%--
    Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 5): fragmento de
    resultados de /desembolso -- mismo patrón que solicitudes/_resultados.jsp: incluido
    (<%@ include %>) dentro de lista.jsp en la carga normal, y como respuesta completa de un
    pedido AJAX (ControladorDesembolsoServlet.mostrarLista, PeticionAjax.esAjax). Sin modales ni
    formularios adentro (solo el enlace "Desembolsar" a la ficha), no hace falta ningún evento
    para re-atar nada después de un filtrado.
--%>
<%
    @SuppressWarnings("unchecked")
    List<PrestamoCredito> listosResultado = (List<PrestamoCredito>) request.getAttribute("listos");
    @SuppressWarnings("unchecked")
    Map<Long, Persona> clientesPorPrestamoResultado = (Map<Long, Persona>) request.getAttribute("clientesPorPrestamo");
    String textoBusquedaResultado = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultado = textoBusquedaResultado != null && !textoBusquedaResultado.trim().isEmpty();
    // Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7 paso E): si hay préstamos
    // PENDIENTE_DESEMBOLSO con checklist incompleto, avisar -- nunca con una búsqueda de texto
    // activa (ahí solo importa el resultado de esa búsqueda, el controlador ni lo calcula).
    // Los 2 textos completos se arman acá, en una sola variable cada uno, para que la expresión
    // de salida de abajo quede en una sola línea de fuente (el humo los busca con grep -F, que
    // no cruza líneas).
    // B191 (15/11/2026): contador, orden y paginacion. Los valores los deja
    // ControladorDesembolsoServlet.mostrarLista; aca solo se dibujan. Los respaldos (?:) son para
    // esta JSP contra un .class viejo, antes del Clean and Build (punto 11b).
    Integer totalListosResultado = (Integer) request.getAttribute("totalResultados");
    if (totalListosResultado == null) {
        totalListosResultado = listosResultado == null ? 0 : listosResultado.size();
    }
    String ordenActualResultado = (String) request.getAttribute("ordenActual");
    String sentidoActualResultado = (String) request.getAttribute("sentidoActual");
    // El dueno de estos valores por defecto es ServicioFormalizacion.ordenPorDefecto; aca va el
    // literal para no referenciar desde la JSP una constante nueva (punto 11b).
    if (ordenActualResultado == null) { ordenActualResultado = "formalizado"; }
    if (sentidoActualResultado == null) { sentidoActualResultado = "asc"; }
    String prefijoFiltrosResultado = hayBusquedaResultado
            ? "texto=" + java.net.URLEncoder.encode(textoBusquedaResultado, "UTF-8") + "&" : "";
    // {rotulo, clave de orden (null = no ordena), clase de la celda}. Mismas claves y mismas clases
    // que la seccion "pendientes de desembolso" de Formalizacion (B183): es la misma entidad.
    String[][] columnasListosResultado = {
            {"Operación", "operacion", "cc-sin-partir"},
            {"Cliente", "cliente", "cc-celda-nombre"},
            {"Monto aprobado", "monto", "cc-sin-partir cc-monto"},
            {"Formalizado", "formalizado", "cc-sin-partir"},
            {"", null, ""}};
    Integer pendientesIncompletosResultado = (Integer) request.getAttribute("pendientesIncompletos");
    String mensajeSinListosResultado = null;
    String notaMezclaResultado = null;
    if (pendientesIncompletosResultado != null && pendientesIncompletosResultado > 0) {
        String textoPendientesResultado = pendientesIncompletosResultado == 1
                ? "1 préstamo pendiente" : pendientesIncompletosResultado + " préstamos pendientes";
        mensajeSinListosResultado = "No hay préstamos listos para desembolsar. Hay " + textoPendientesResultado
                + " esperando los documentos firmados, que se cargan en Formalización.";
        notaMezclaResultado = "Además, hay " + textoPendientesResultado
                + " esperando los documentos firmados, que se cargan en Formalización.";
    }
%>
<%-- B191: el contador cuenta el TOTAL del filtro, no las filas de la pagina. --%>
<p class="text-muted"><%= FormateadorNumeros.comoCantidad(totalListosResultado) %>
    préstamo<%= totalListosResultado == 1 ? "" : "s" %>
    encontrado<%= totalListosResultado == 1 ? "" : "s" %>.</p>
<div class="table-responsive">
    <table class="table table-striped table-hover">
        <%-- B191: encabezados que ordenan, con el mismo marcado y las mismas clases que
             Aprobaciones (B175) y Formalizacion (B183). El enlace es un href real con el filtro
             actual; ordenar pasa por el servidor, que ordena la lista COMPLETA antes de cortar la
             pagina, y vuelve a la pagina 1 (no lleva "pagina"). --%>
        <thead>
        <tr>
        <% for (String[] columnaListos : columnasListosResultado) {
               if (columnaListos[1] == null) { %>
            <th class="<%= columnaListos[2] %>"><%= columnaListos[0] %></th>
        <%     } else {
                   boolean esOrdenadaListos = columnaListos[1].equals(ordenActualResultado);
                   String sentidoEnlaceListos = esOrdenadaListos && "asc".equals(sentidoActualResultado) ? "desc" : "asc";
                   String iconoListos = !esOrdenadaListos ? "fa-sort"
                           : ("asc".equals(sentidoActualResultado) ? "fa-sort-up" : "fa-sort-down"); %>
            <th class="<%= columnaListos[2] %>"><a class="cc-orden-columna"
                   title="Ordenar por <%= columnaListos[0] %>"
                   href="${pageContext.request.contextPath}/desembolso?<%= prefijoFiltrosResultado %>orden=<%= columnaListos[1] %>&sentido=<%= sentidoEnlaceListos %>"><%=
                   columnaListos[0] %> <i class="fas <%= iconoListos %>"></i></a></th>
        <%     } %>
        <% } %>
        </tr>
        </thead>
        <tbody>
        <% if (listosResultado != null) { for (PrestamoCredito prestamoResultado : listosResultado) {
            Persona clienteResultado = clientesPorPrestamoResultado == null ? null
                    : clientesPorPrestamoResultado.get(prestamoResultado.getIdPrestamo());
        %>
        <tr>
            <td class="celda-codigo"><%= prestamoResultado.getIdentificadorOperacion() %></td>
            <td><% if (clienteResultado != null) { %><%= clienteResultado.getNombres() %> <%= clienteResultado.getApellidos() %>
                — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteResultado.getCedulaIdentidad()) %><% } %></td>
            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(prestamoResultado.getMontoAprobado()) %></td>
            <td><%= FormateadorFechas.comoDdMmAaaaHhMm(prestamoResultado.getFechaFormalizacion()) %></td>
            <td><a href="${pageContext.request.contextPath}/desembolso/<%= prestamoResultado.getIdPrestamo() %>" class="btn btn-sm btn-primary">Desembolsar</a></td>
        </tr>
        <% } } %>
        <% if (listosResultado == null || listosResultado.isEmpty()) { %>
        <tr><td colspan="<%= columnasListosResultado.length %>" class="text-muted">
            <% if (hayBusquedaResultado) { %>
            No se encontraron préstamos para esa búsqueda.
            <% } else if (mensajeSinListosResultado != null) { %>
            <%= mensajeSinListosResultado %>
            <% } else { %>
            No hay préstamos listos para desembolsar.
            <% } %>
        </td></tr>
        <% } %>
        </tbody>
    </table>
</div>
<% if (!hayBusquedaResultado && notaMezclaResultado != null && listosResultado != null && !listosResultado.isEmpty()) { %>
<p class="text-muted"><%= notaMezclaResultado %></p>
<% } %>
<%-- B191: la paginacion va DENTRO de este fragmento (es el que reemplaza el pedido AJAX del
     "listado vivo"), y esta pantalla tiene UN solo listado, asi que usa el parametro "pagina" de
     siempre y SI pasa por listado-vivo.js -- igual que Clientes, Solicitudes y Aprobaciones. Por
     eso no hace falta ninguno de los dos atributos que agrego B183. --%>
<% request.setAttribute("paginacionUrlBase",
           request.getContextPath() + "/desembolso?" + prefijoFiltrosResultado
                   + "orden=" + ordenActualResultado + "&sentido=" + sentidoActualResultado + "&"); %>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
