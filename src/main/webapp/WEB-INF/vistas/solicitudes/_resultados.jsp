<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%--
    Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 3): fragmento de
    resultados de /solicitudes -- mismo patrón que aprobaciones/_resultados.jsp: incluido
    (<%@ include %>) dentro de lista.jsp en la carga normal, y como respuesta completa de un
    pedido AJAX (ControladorSolicitudesServlet.mostrarLista, PeticionAjax.esAjax). Sin modales ni
    formularios adentro (solo el enlace "Ver" a la ficha), así que no hace falta ningún evento
    para re-atar nada después de un filtrado.
--%>
<%
    @SuppressWarnings("unchecked")
    List<SolicitudCredito> listaSolicitudesResultado = (List<SolicitudCredito>) request.getAttribute("solicitudes");
    @SuppressWarnings("unchecked")
    Map<Long, Persona> clientesPorSolicitudResultado = (Map<Long, Persona>) request.getAttribute("clientesPorSolicitud");
    String textoBusquedaResultado = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultado = textoBusquedaResultado != null && !textoBusquedaResultado.trim().isEmpty();
    // B165 (14/11/2026): el filtro de estado, para que el vacío diga cuál es. El atributo lo pone
    // ControladorSolicitudesServlet.mostrarLista en los dos casos (página completa y fragmento
    // AJAX). La etiqueta se le pide al enum, que es su dueño -- acá no se escribe ningún nombre
    // de estado a mano.
    String estadoFiltroResultado = (String) request.getAttribute("estadoFiltro");
    String etiquetaEstadoFiltroResultado = "";
    if (estadoFiltroResultado != null && !estadoFiltroResultado.isEmpty()) {
        try {
            etiquetaEstadoFiltroResultado = EstadoSolicitud.valueOf(estadoFiltroResultado).getEtiqueta();
        } catch (IllegalArgumentException estadoDesconocido) {
            estadoFiltroResultado = "";
        }
    }
%>
<%-- B132 (11/11/2026): cuántos resultados hay, como ya lo decían Clientes, Préstamos y los
     informes. Mismo formato y mismo plural resuelto que prestamos/lista.jsp. --%>
<%-- B167 (14/11/2026): el contador cuenta el TOTAL, no las filas de la página. Desde que el
     listado pagina, `listaSolicitudesResultado.size()` son 20 como máximo: dejarlo así habría
     hecho que la pantalla dijera "20 solicitudes encontradas" sobre 553. El total lo pone
     ControladorSolicitudesServlet.mostrarLista, que es el que corta la página. --%>
<p class="text-muted mb-2"><%
    Integer totalResultadosSolicitudes = (Integer) request.getAttribute("totalResultados");
    int totalSolicitudesResultado = totalResultadosSolicitudes != null ? totalResultadosSolicitudes
            : (listaSolicitudesResultado == null ? 0 : listaSolicitudesResultado.size());
%><%= FormateadorNumeros.comoCantidad(totalSolicitudesResultado) %>
    solicitud<%= totalSolicitudesResultado == 1 ? "" : "es" %>
    encontrada<%= totalSolicitudesResultado == 1 ? "" : "s" %>.</p>
<div class="table-responsive">
    <table class="table table-striped table-hover">
        <%-- B167 (14/11/2026): encabezados que ordenan, las SEIS columnas. Pueden ser las seis
             porque acá el orden es en memoria (ServicioSolicitudes.ordenarParaListado): el nombre
             del cliente ya viene en el mapa que esta tabla usa, así que no falta ningún JOIN -- en
             Clientes, con el orden en SQL, solo podían ser cuatro (B157).
             El enlace es un href real con los filtros actuales; ordenar pasa por el servidor, que
             ordena la lista COMPLETA antes de cortar la página. --%>
        <%
            String ordenActualTabla = (String) request.getAttribute("ordenActual");
            String sentidoActualTabla = (String) request.getAttribute("sentidoActual");
            // Respaldo para el único caso en que no llegan: esta JSP contra un .class viejo, antes
            // del Clean and Build. El dueño del defecto es ServicioSolicitudes.ORDEN_POR_DEFECTO;
            // acá va el literal para no referenciar una constante nueva desde la JSP (punto 11b).
            if (ordenActualTabla == null) { ordenActualTabla = "fecha"; }
            if (sentidoActualTabla == null) { sentidoActualTabla = "desc"; }
            String queryFiltrosTabla = (String) request.getAttribute("queryStringFiltros");
            if (queryFiltrosTabla == null) { queryFiltrosTabla = ""; }
            String prefijoFiltrosTabla = queryFiltrosTabla.isEmpty() ? "" : queryFiltrosTabla + "&";
            // {rotulo, clave de orden (null = no ordena), clase de la celda}. La tercera
            // posicion es la misma convencion que Aprobaciones, Desembolso y Mora: la columna de
            // cifras se declara aca y el <th> solo la dibuja (B160).
            String[][] columnasTabla = {
                {"Código", "codigo", ""}, {"Fecha", "fecha", ""}, {"Cliente", "cliente", ""},
                {"Monto solicitado", "monto", "cc-monto"}, {"Plazo", "plazo", ""},
                {"Estado", "estado", ""},
                {"", null, ""}
            };
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
            <th class="<%= columnaTabla[2] %>"><a class="cc-orden-columna" title="Ordenar por <%= columnaTabla[0] %>"
                   href="${pageContext.request.contextPath}/solicitudes?<%= prefijoFiltrosTabla %>orden=<%= columnaTabla[1] %>&sentido=<%= sentidoDelEnlace %>"><%=
                   columnaTabla[0] %> <i class="fas <%= iconoOrden %>"></i></a></th>
        <%     } %>
        <% } %>
        </tr>
        </thead>
        <tbody>
        <% if (listaSolicitudesResultado == null || listaSolicitudesResultado.isEmpty()) { %>
        <%-- B165 (14/11/2026): el vacío decía "No hay solicitudes que coincidan" incluso sin
             ningún filtro puesto -- no había con qué coincidir, así que el mensaje no se entendía
             y no ofrecía salida. Ahora son TRES casos, porque son tres situaciones distintas: con
             búsqueda escrita, con solo el filtro de estado, y sin ningún filtro (ahí dice lo que
             pasa de verdad y ofrece crear la primera).
             El texto de la búsqueda NO se toca: `pruebas/humo_http.sh` lo verifica en 5 lugares
             (una comprobación de presencia se rompe ruidosamente, y esta tiene que seguir en
             verde). --%>
        <tr><td colspan="7" class="text-muted">
            <% if (hayBusquedaResultado) { %>
            No se encontraron solicitudes para esa búsqueda.
            <% } else if (estadoFiltroResultado != null && !estadoFiltroResultado.isEmpty()) { %>
            Ninguna solicitud está en estado «<%= etiquetaEstadoFiltroResultado %>».
            <% } else { %>
            Todavía no hay solicitudes cargadas.
            <a href="${pageContext.request.contextPath}/solicitudes/nuevo">Crear la primera</a>.
            <% } %>
        </td></tr>
        <% } %>
        <% if (listaSolicitudesResultado != null) { for (SolicitudCredito filaResultado : listaSolicitudesResultado) {
            Persona clienteFilaResultado = clientesPorSolicitudResultado == null ? null
                    : clientesPorSolicitudResultado.get(filaResultado.getIdCliente()); %>
        <tr>
            <td class="celda-codigo"><%= filaResultado.getCodigoSolicitud() %></td>
            <td><%= FormateadorFechas.comoDdMmAaaa(filaResultado.getFechaSolicitud().toLocalDate()) %></td>
            <td><% if (clienteFilaResultado != null) { %>
                <%= clienteFilaResultado.getNombres() %> <%= clienteFilaResultado.getApellidos() %>
                — <%= FormateadorTexto.comoCedulaConPuntos(clienteFilaResultado.getCedulaIdentidad()) %>
                <% } else { %>-<% } %></td>
            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(filaResultado.getMontoSolicitado()) %></td>
            <td><%= filaResultado.getPlazoSolicitadoMeses() %> meses</td>
            <td><%= filaResultado.getEstadoSolicitud().getEtiqueta() %></td>
            <td>
                <a href="${pageContext.request.contextPath}/solicitudes/<%= filaResultado.getIdSolicitud() %>" class="btn btn-sm btn-info">Ver</a>
            </td>
        </tr>
        <% } } %>
        </tbody>
    </table>
</div>
<%-- B167 (14/11/2026): la paginación es el fragmento compartido con Clientes
     (plantilla/paginacion.jsp, decisión de Joel: un solo dueño del marcado). Esta pantalla solo
     arma la URL base, con los filtros y el orden vigentes y terminada en "&", porque el fragmento
     le pega "pagina=N" y nada más. --%>
<%
    String prefijoPaginaSolicitudes = (queryFiltrosTabla.isEmpty() ? "?" : "?" + queryFiltrosTabla + "&")
            + "orden=" + ordenActualTabla + "&sentido=" + sentidoActualTabla + "&";
    request.setAttribute("paginacionUrlBase",
            request.getContextPath() + "/solicitudes" + prefijoPaginaSolicitudes);
%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
