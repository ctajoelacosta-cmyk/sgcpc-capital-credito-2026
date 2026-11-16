<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%--
    Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): fragmento de
    resultados de /formalizacion -- a diferencia de Mora/Aprobaciones/Solicitudes, acá UN solo
    campo de texto filtra las 3 secciones a la vez, así que las 3 van juntas en este mismo
    fragmento (se reemplazan todas en el mismo pedido AJAX). Sin modales ni formularios en
    ninguna de las 3 (solo enlaces "Formalizar"/"Ver"/"Devolución del pagaré"), no hace falta
    ningún evento para re-atar nada después de un filtrado.

    B183 (15/11/2026): las 3 secciones tienen título, contador, encabezados que ordenan y su
    propia paginación. Cuatro cosas que conviene tener a mano al leer esto:

    - Cada sección tiene SUS parámetros (ordenFormalizar/sentidoFormalizar/paginaFormalizar, y
      los equivalentes de Desembolso y Pagare), porque son tres listados en una sola pantalla y
      un solo "pagina" los movería a los tres.
    - Las URL las arma el controlador (urlOrdenX / urlPaginacionX): cada enlace lleva puesto el
      estado de las OTRAS dos secciones, así que tocar una no mueve a las demás. Un encabezado
      además vuelve a la página 1 de su sección, porque reordenar deja la página 4 sin sentido.
    - La paginación se incluye con <jsp:include> (dinámico) y no con <%@ include %>: tres copias
      estáticas del fragmento redeclararían sus variables y no compilaría.
    - Los enlaces de página NO pasan por listado-vivo.js (paginacionConListadoVivo=false): ese
      script arma el pedido AJAX desde el formulario y le pega "pagina=N" fijo, así que con tres
      secciones movería la que no se tocó. Acá navegan de verdad, igual que los encabezados que
      ordenan en todas las pantallas del sistema.
--%>
<%
    @SuppressWarnings("unchecked")
    List<SolicitudCredito> listaParaFormalizarResultado = (List<SolicitudCredito>) request.getAttribute("paraFormalizar");
    @SuppressWarnings("unchecked")
    Map<Long, Persona> clientesPorSolicitudResultado = (Map<Long, Persona>) request.getAttribute("clientesPorSolicitud");
    @SuppressWarnings("unchecked")
    List<PrestamoCredito> listaPendientesDesembolsoResultado = (List<PrestamoCredito>) request.getAttribute("pendientesDesembolso");
    @SuppressWarnings("unchecked")
    Map<Long, Persona> clientesPorPrestamoResultado = (Map<Long, Persona>) request.getAttribute("clientesPorPrestamo");
    @SuppressWarnings("unchecked")
    Map<Long, Boolean> checklistCompletoPorPrestamoResultado = (Map<Long, Boolean>) request.getAttribute("checklistCompletoPorPrestamo");
    // B181 (14/11/2026): qué falta en el checklist, por préstamo. Lo arma
    // ControladorFormalizacionServlet.mostrarLista con ServicioFormalizacion.nombrarLoQueFalta;
    // acá solo se dibuja. Null = esta JSP corre contra un .class viejo: la columna queda como
    // antes, solo con la etiqueta.
    @SuppressWarnings("unchecked")
    Map<Long, String> faltantesChecklistPorPrestamoResultado = (Map<Long, String>) request.getAttribute("faltantesChecklistPorPrestamo");
    @SuppressWarnings("unchecked")
    List<PrestamoCredito> listaCierreDocumentalPendienteResultado = (List<PrestamoCredito>) request.getAttribute("cierreDocumentalPendiente");
    @SuppressWarnings("unchecked")
    Map<Long, Persona> clientesPorPrestamoCierreResultado = (Map<Long, Persona>) request.getAttribute("clientesPorPrestamoCierre");
    String textoBusquedaResultado = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultado = textoBusquedaResultado != null && !textoBusquedaResultado.trim().isEmpty();

    // B183: totales, orden y URL de cada sección. Los respaldos (?:) son para la única situación
    // en que no llegan: esta JSP contra un .class viejo, antes del Clean and Build -- ahí la
    // pantalla sale como salía antes, sin paginación y sin flechas, en vez de romperse (punto 11b).
    Integer totalFormalizarResultado = (Integer) request.getAttribute("totalParaFormalizar");
    Integer totalDesembolsoResultado = (Integer) request.getAttribute("totalPendientesDesembolso");
    Integer totalPagareResultado = (Integer) request.getAttribute("totalCierrePendiente");
    if (totalFormalizarResultado == null) {
        totalFormalizarResultado = listaParaFormalizarResultado == null ? 0 : listaParaFormalizarResultado.size();
    }
    if (totalDesembolsoResultado == null) {
        totalDesembolsoResultado = listaPendientesDesembolsoResultado == null ? 0 : listaPendientesDesembolsoResultado.size();
    }
    if (totalPagareResultado == null) {
        totalPagareResultado = listaCierreDocumentalPendienteResultado == null ? 0 : listaCierreDocumentalPendienteResultado.size();
    }
    // El dueño de estos valores por defecto es ServicioFormalizacion.ordenPorDefecto; acá va el
    // literal para no referenciar desde la JSP una constante nueva (punto 11b).
    String ordenFormalizarResultado = (String) request.getAttribute("ordenActualFormalizar");
    String sentidoFormalizarResultado = (String) request.getAttribute("sentidoActualFormalizar");
    String ordenDesembolsoResultado = (String) request.getAttribute("ordenActualDesembolso");
    String sentidoDesembolsoResultado = (String) request.getAttribute("sentidoActualDesembolso");
    String ordenPagareResultado = (String) request.getAttribute("ordenActualPagare");
    String sentidoPagareResultado = (String) request.getAttribute("sentidoActualPagare");
    if (ordenFormalizarResultado == null) { ordenFormalizarResultado = "aprobada"; }
    if (ordenDesembolsoResultado == null) { ordenDesembolsoResultado = "formalizado"; }
    if (ordenPagareResultado == null) { ordenPagareResultado = "cancelada"; }
    if (sentidoFormalizarResultado == null) { sentidoFormalizarResultado = "asc"; }
    if (sentidoDesembolsoResultado == null) { sentidoDesembolsoResultado = "asc"; }
    if (sentidoPagareResultado == null) { sentidoPagareResultado = "asc"; }
    String urlOrdenFormalizarResultado = (String) request.getAttribute("urlOrdenFormalizar");
    String urlOrdenDesembolsoResultado = (String) request.getAttribute("urlOrdenDesembolso");
    String urlOrdenPagareResultado = (String) request.getAttribute("urlOrdenPagare");
    if (urlOrdenFormalizarResultado == null) { urlOrdenFormalizarResultado = request.getContextPath() + "/formalizacion?"; }
    if (urlOrdenDesembolsoResultado == null) { urlOrdenDesembolsoResultado = request.getContextPath() + "/formalizacion?"; }
    if (urlOrdenPagareResultado == null) { urlOrdenPagareResultado = request.getContextPath() + "/formalizacion?"; }

    // {rótulo, clave de orden (null = no ordena), clase de la celda}. Mismas clases que
    // Aprobaciones (B175): cc-sin-partir en todo lo que es una sola unidad (un código, una fecha,
    // un monto con su "Gs."), cc-celda-nombre en "Cliente", que necesita ancho mínimo y sí puede
    // bajar de renglón.
    String[][] columnasFormalizarResultado = {
            {"Código", "codigo", "cc-sin-partir"},
            {"Aprobada", "aprobada", "cc-sin-partir"},
            {"Cliente", "cliente", "cc-celda-nombre"},
            {"Monto aprobado", "monto", "cc-sin-partir cc-monto"},
            {"Plazo", "plazo", "cc-sin-partir"},
            {"", null, ""}};
    // "Checklist" NO ordena a propósito: no sale de la consulta -- lo arma el controlador leyendo
    // el checklist de cada préstamo de la página -- y un encabezado que parece clickeable y no
    // ordena es peor que uno que no lo parece (B157).
    String[][] columnasDesembolsoResultado = {
            {"N.º de operación", "operacion", "cc-sin-partir"},
            {"Cliente", "cliente", "cc-celda-nombre"},
            {"Monto", "monto", "cc-sin-partir cc-monto"},
            {"Formalizado", "formalizado", "cc-sin-partir"},
            {"Checklist", null, "cc-sin-partir"},
            {"", null, ""}};
    String[][] columnasPagareResultado = {
            {"N.º de operación", "operacion", "cc-sin-partir"},
            {"Cliente", "cliente", "cc-celda-nombre"},
            {"Monto", "monto", "cc-sin-partir cc-monto"},
            {"", null, ""}};
%>
<%-- B183: la sección 1 no tenía título propio ni contador, y las otras dos sí tenían título --
     con una búsqueda que coincidía en una sola sección, las tres se leían como "no hay nada".
     Ahora las tres dicen qué son y cuántas hay. --%>
<h2>Solicitudes aprobadas listas para formalizar</h2>
<p class="text-muted"><%= FormateadorNumeros.comoCantidad(totalFormalizarResultado) %>
    solicitud<%= totalFormalizarResultado == 1 ? "" : "es" %>
    encontrada<%= totalFormalizarResultado == 1 ? "" : "s" %>.</p>
<div class="card">
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead>
                <tr>
                <% for (String[] columnaFormalizar : columnasFormalizarResultado) {
                       if (columnaFormalizar[1] == null) { %>
                    <th class="<%= columnaFormalizar[2] %>"><%= columnaFormalizar[0] %></th>
                <%     } else {
                           boolean esOrdenadaFormalizar = columnaFormalizar[1].equals(ordenFormalizarResultado);
                           String sentidoEnlaceFormalizar = esOrdenadaFormalizar && "asc".equals(sentidoFormalizarResultado) ? "desc" : "asc";
                           String iconoFormalizar = !esOrdenadaFormalizar ? "fa-sort"
                                   : ("asc".equals(sentidoFormalizarResultado) ? "fa-sort-up" : "fa-sort-down"); %>
                    <th class="<%= columnaFormalizar[2] %>"><a class="cc-orden-columna"
                           title="Ordenar por <%= columnaFormalizar[0] %>"
                           href="<%= urlOrdenFormalizarResultado %>ordenFormalizar=<%= columnaFormalizar[1] %>&sentidoFormalizar=<%= sentidoEnlaceFormalizar %>"><%=
                           columnaFormalizar[0] %> <i class="fas <%= iconoFormalizar %>"></i></a></th>
                <%     } %>
                <% } %>
                </tr>
                </thead>
                <tbody>
                <% if (listaParaFormalizarResultado == null || listaParaFormalizarResultado.isEmpty()) { %>
                <tr><td colspan="<%= columnasFormalizarResultado.length %>" class="text-muted">
                    <% if (hayBusquedaResultado) { %>
                    No se encontraron solicitudes para esa búsqueda.
                    <% } else { %>
                    No hay solicitudes aprobadas pendientes de formalizar.
                    <% } %>
                </td></tr>
                <% } %>
                <% if (listaParaFormalizarResultado != null) { for (SolicitudCredito filaResultado : listaParaFormalizarResultado) {
                    Persona clienteFilaFormalizarResultado = clientesPorSolicitudResultado == null ? null
                            : clientesPorSolicitudResultado.get(filaResultado.getIdCliente()); %>
                <tr>
                    <td class="celda-codigo"><%= filaResultado.getCodigoSolicitud() %></td>
                    <td><%= filaResultado.getFechaDecision() == null ? "-" : FormateadorFechas.comoDdMmAaaa(filaResultado.getFechaDecision().toLocalDate()) %></td>
                    <td><% if (clienteFilaFormalizarResultado != null) { %>
                        <%= clienteFilaFormalizarResultado.getNombres() %> <%= clienteFilaFormalizarResultado.getApellidos() %>
                        — <%= FormateadorTexto.comoCedulaConPuntos(clienteFilaFormalizarResultado.getCedulaIdentidad()) %>
                        <% } else { %>-<% } %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(filaResultado.getMontoAprobado()) %></td>
                    <td><%= filaResultado.getPlazoAprobadoMeses() %> meses</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/formalizacion/nueva/<%= filaResultado.getIdSolicitud() %>" class="btn btn-sm btn-info">Formalizar</a>
                    </td>
                </tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%
    request.setAttribute("pagina", request.getAttribute("paginaFormalizar"));
    request.setAttribute("totalPaginas", request.getAttribute("totalPaginasFormalizar"));
    request.setAttribute("paginacionUrlBase", request.getAttribute("urlPaginacionFormalizar"));
    request.setAttribute("paginacionParamPagina", "paginaFormalizar");
    request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
%>
<jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />

<%-- Corrección 1 (Joel): antes, una vez formalizada, la operación solo se veía escribiendo la URL.
     OJO: el texto del <h2> lo verifica pruebas/humo_http.sh:2271 -- el contador va en su propio
     renglón, debajo, así que ese literal sigue entero. --%>
<h2 class="mt-4">Préstamos pendientes de desembolso</h2>
<p class="text-muted"><%= FormateadorNumeros.comoCantidad(totalDesembolsoResultado) %>
    préstamo<%= totalDesembolsoResultado == 1 ? "" : "s" %>
    encontrado<%= totalDesembolsoResultado == 1 ? "" : "s" %>.
    Ya formalizados; el checklist de firmados debe estar completo antes de poder desembolsar.</p>
<div class="card">
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead>
                <tr>
                <% for (String[] columnaDesembolso : columnasDesembolsoResultado) {
                       if (columnaDesembolso[1] == null) { %>
                    <th class="<%= columnaDesembolso[2] %>"><%= columnaDesembolso[0] %></th>
                <%     } else {
                           boolean esOrdenadaDesembolso = columnaDesembolso[1].equals(ordenDesembolsoResultado);
                           String sentidoEnlaceDesembolso = esOrdenadaDesembolso && "asc".equals(sentidoDesembolsoResultado) ? "desc" : "asc";
                           String iconoDesembolso = !esOrdenadaDesembolso ? "fa-sort"
                                   : ("asc".equals(sentidoDesembolsoResultado) ? "fa-sort-up" : "fa-sort-down"); %>
                    <th class="<%= columnaDesembolso[2] %>"><a class="cc-orden-columna"
                           title="Ordenar por <%= columnaDesembolso[0] %>"
                           href="<%= urlOrdenDesembolsoResultado %>ordenDesembolso=<%= columnaDesembolso[1] %>&sentidoDesembolso=<%= sentidoEnlaceDesembolso %>"><%=
                           columnaDesembolso[0] %> <i class="fas <%= iconoDesembolso %>"></i></a></th>
                <%     } %>
                <% } %>
                </tr>
                </thead>
                <tbody>
                <% if (listaPendientesDesembolsoResultado == null || listaPendientesDesembolsoResultado.isEmpty()) { %>
                <tr><td colspan="<%= columnasDesembolsoResultado.length %>" class="text-muted">
                    <% if (hayBusquedaResultado) { %>
                    No se encontraron préstamos para esa búsqueda.
                    <% } else { %>
                    No hay préstamos pendientes de desembolso.
                    <% } %>
                </td></tr>
                <% } %>
                <% if (listaPendientesDesembolsoResultado != null) { for (PrestamoCredito filaPrestamoResultado : listaPendientesDesembolsoResultado) {
                    Persona clienteFilaPrestamoResultado = clientesPorPrestamoResultado == null ? null
                            : clientesPorPrestamoResultado.get(filaPrestamoResultado.getIdPrestamo());
                    boolean completoFilaPrestamoResultado = checklistCompletoPorPrestamoResultado != null
                            && Boolean.TRUE.equals(checklistCompletoPorPrestamoResultado.get(filaPrestamoResultado.getIdPrestamo()));
                    String faltanteFilaPrestamoResultado = faltantesChecklistPorPrestamoResultado == null ? null
                            : faltantesChecklistPorPrestamoResultado.get(filaPrestamoResultado.getIdPrestamo()); %>
                <tr>
                    <td class="celda-codigo"><%= filaPrestamoResultado.getIdentificadorOperacion() %></td>
                    <td><% if (clienteFilaPrestamoResultado != null) { %>
                        <%= clienteFilaPrestamoResultado.getNombres() %> <%= clienteFilaPrestamoResultado.getApellidos() %>
                        — <%= FormateadorTexto.comoCedulaConPuntos(clienteFilaPrestamoResultado.getCedulaIdentidad()) %>
                        <% } else { %>-<% } %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(filaPrestamoResultado.getMontoAprobado()) %></td>
                    <td><%= FormateadorFechas.comoDdMmAaaa(filaPrestamoResultado.getFechaFormalizacion().toLocalDate()) %></td>
                    <%-- B181 (14/11/2026): la etiqueta "Incompleto" se queda (es la que mira el humo,
                         línea 2273, y la que da el estado de un vistazo) y debajo va QUÉ falta, con
                         los nombres de los tipos de documento que nombra
                         ServicioFormalizacion.nombrarLoQueFalta -- mismo criterio que B88/B89: un
                         aviso nombra lo que falta en vez de mandar a buscarlo. --%>
                    <td><% if (completoFilaPrestamoResultado) { %><span class="badge badge-success">Completo</span>
                        <% } else { %><span class="badge badge-secondary">Incompleto</span>
                        <% if (faltanteFilaPrestamoResultado != null && !faltanteFilaPrestamoResultado.isEmpty()) { %>
                        <div class="small text-muted mt-1">Falta: <%= faltanteFilaPrestamoResultado %></div>
                        <% } %>
                        <% } %></td>
                    <td>
                        <a href="${pageContext.request.contextPath}/formalizacion/<%= filaPrestamoResultado.getIdPrestamo() %>" class="btn btn-sm btn-info">Ver</a>
                    </td>
                </tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%
    request.setAttribute("pagina", request.getAttribute("paginaDesembolso"));
    request.setAttribute("totalPaginas", request.getAttribute("totalPaginasDesembolso"));
    request.setAttribute("paginacionUrlBase", request.getAttribute("urlPaginacionDesembolso"));
    request.setAttribute("paginacionParamPagina", "paginaDesembolso");
    request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
%>
<jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />

<%-- E9/Bloque 5 (P10, corrección de Joel): antes el cierre documental no tenía ningún acceso
     visible desde acá -- préstamos ya FINALIZADO esperando la devolución del pagaré.
     B85 (28/10/2026, aprobado por Joel): el título dice el trámite en vez del nombre interno del
     estado ("cierre documental" no se entendía). OJO: el id="cierre-documental" NO se toca --
     de él dependen el enlace del menú ("Devolución del pagaré"), el de la tarjeta del panel
     (B76), el script V3h de pie.jsp (resaltado del módulo + scroll) y el scroll-margin-top de
     cabecera.jsp. --%>
<h2 class="mt-4" id="cierre-documental">Préstamos finalizados — pagaré pendiente de devolución</h2>
<p class="text-muted"><%= FormateadorNumeros.comoCantidad(totalPagareResultado) %>
    préstamo<%= totalPagareResultado == 1 ? "" : "s" %>
    encontrado<%= totalPagareResultado == 1 ? "" : "s" %>.</p>
<div class="card">
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead>
                <tr>
                <% for (String[] columnaPagare : columnasPagareResultado) {
                       if (columnaPagare[1] == null) { %>
                    <th class="<%= columnaPagare[2] %>"><%= columnaPagare[0] %></th>
                <%     } else {
                           boolean esOrdenadaPagare = columnaPagare[1].equals(ordenPagareResultado);
                           String sentidoEnlacePagare = esOrdenadaPagare && "asc".equals(sentidoPagareResultado) ? "desc" : "asc";
                           String iconoPagare = !esOrdenadaPagare ? "fa-sort"
                                   : ("asc".equals(sentidoPagareResultado) ? "fa-sort-up" : "fa-sort-down"); %>
                    <th class="<%= columnaPagare[2] %>"><a class="cc-orden-columna"
                           title="Ordenar por <%= columnaPagare[0] %>"
                           href="<%= urlOrdenPagareResultado %>ordenPagare=<%= columnaPagare[1] %>&sentidoPagare=<%= sentidoEnlacePagare %>"><%=
                           columnaPagare[0] %> <i class="fas <%= iconoPagare %>"></i></a></th>
                <%     } %>
                <% } %>
                </tr>
                </thead>
                <tbody>
                <% if (listaCierreDocumentalPendienteResultado == null || listaCierreDocumentalPendienteResultado.isEmpty()) { %>
                <tr><td colspan="<%= columnasPagareResultado.length %>" class="text-muted">
                    <% if (hayBusquedaResultado) { %>
                    No se encontraron préstamos para esa búsqueda.
                    <% } else { %>
                    No hay préstamos finalizados con el pagaré pendiente de devolución.
                    <% } %>
                </td></tr>
                <% } %>
                <% if (listaCierreDocumentalPendienteResultado != null) { for (PrestamoCredito filaCierreResultado : listaCierreDocumentalPendienteResultado) {
                    Persona clienteFilaCierreResultado = clientesPorPrestamoCierreResultado == null ? null
                            : clientesPorPrestamoCierreResultado.get(filaCierreResultado.getIdPrestamo()); %>
                <tr>
                    <td class="celda-codigo"><%= filaCierreResultado.getIdentificadorOperacion() %></td>
                    <td><% if (clienteFilaCierreResultado != null) { %>
                        <%= clienteFilaCierreResultado.getNombres() %> <%= clienteFilaCierreResultado.getApellidos() %>
                        — <%= FormateadorTexto.comoCedulaConPuntos(clienteFilaCierreResultado.getCedulaIdentidad()) %>
                        <% } else { %>-<% } %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(filaCierreResultado.getMontoAprobado()) %></td>
                    <td>
                        <a href="${pageContext.request.contextPath}/formalizacion/<%= filaCierreResultado.getIdPrestamo() %>/cierre-documental" class="btn btn-sm btn-info">Devolución del pagaré</a>
                    </td>
                </tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%
    request.setAttribute("pagina", request.getAttribute("paginaPagare"));
    request.setAttribute("totalPaginas", request.getAttribute("totalPaginasPagare"));
    request.setAttribute("paginacionUrlBase", request.getAttribute("urlPaginacionPagare"));
    request.setAttribute("paginacionParamPagina", "paginaPagare");
    request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
%>
<jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />
