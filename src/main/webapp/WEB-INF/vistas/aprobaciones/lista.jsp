<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    boolean vistaHistorial = Boolean.TRUE.equals(request.getAttribute("vistaHistorial"));
    String estadoFiltroActual = (String) request.getAttribute("estadoFiltro");
    if (estadoFiltroActual == null) { estadoFiltroActual = ""; }
    LocalDate desdeFiltroActual = (LocalDate) request.getAttribute("desdeFiltro");
    LocalDate hastaFiltroActual = (LocalDate) request.getAttribute("hastaFiltro");
    String textoBusquedaAprobaciones = (String) request.getAttribute("textoBusqueda");
    // B172 (14/11/2026): cuántas solicitudes esperan decisión, para que la pestaña lo diga. Lo
    // cuenta el controlador (sin el filtro de texto); si no llega, la pestaña queda como antes.
    Integer cantidadPendientesAprobaciones = (Integer) request.getAttribute("cantidadPendientes");
    // B175 (14/11/2026): el orden vigente, para que viaje DENTRO del formulario (si no, la
    // búsqueda en vivo y la paginación por AJAX volverían al orden por defecto). El dueño del
    // valor es ServicioAprobacionCredito.ordenPorDefecto; acá va el literal como respaldo para
    // una sola situación: esta JSP contra un .class viejo, antes del Clean and Build (punto 11b).
    String ordenActualLista = (String) request.getAttribute("ordenActual");
    String sentidoActualLista = (String) request.getAttribute("sentidoActual");
    if (ordenActualLista == null) { ordenActualLista = vistaHistorial ? "fechadecision" : "enviada"; }
    if (sentidoActualLista == null) { sentidoActualLista = vistaHistorial ? "desc" : "asc"; }
    String ordenEnUrlLista = "orden=" + ordenActualLista + "&sentido=" + sentidoActualLista;
%>
<h1>Aprobaciones</h1>
<p class="text-muted"><%= vistaHistorial ? "Solicitudes ya decididas (aprobadas, rechazadas o canceladas)."
        : "Solicitudes pendientes de aprobación, en orden de llegada." %></p>

<%-- Corrección 4 (Joel): pestaña Historial para ver lo ya decidido sin escribir la URL a mano. --%>
<ul class="nav nav-tabs mb-3">
    <%-- B172 (14/11/2026): la pestaña dice cuántas esperan. Antes había que entrar para saberlo,
         aunque el dato ya lo mostraba la tarjeta del Panel. El número va SOLO en "Pendientes":
         estando en Historial es lo que el gerente necesita ver de un vistazo, y un contador en
         "Historial" (409 y creciendo) no le serviría para decidir nada -- además el contador de
         resultados de abajo ya dice cuántas trae la pestaña abierta. --%>
    <li class="nav-item"><a class="nav-link <%= vistaHistorial ? "" : "active" %>"
                             href="${pageContext.request.contextPath}/aprobaciones">Pendientes<%=
                             cantidadPendientesAprobaciones == null ? "" : " (" + cantidadPendientesAprobaciones + ")" %></a></li>
    <li class="nav-item"><a class="nav-link <%= vistaHistorial ? "active" : "" %>"
                             href="${pageContext.request.contextPath}/aprobaciones?vista=historial">Historial</a></li>
</ul>

<%-- Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): mismo
     mecanismo de "listado vivo" que Clientes/Mora. listado-vivo.js ya ataba 'change' a los
     <select>/<input type="date"> del mismo <form> (lo usa Clientes con su <select> de Estado) --
     por eso el texto nuevo se combina con estado/desde/hasta de Historial sin tocar ese script. --%>
<% if (vistaHistorial) { %>
<%-- RF07 (pedido de Joel, 10/10/2026): filtro por estado y por rango de fechas (fecha de la
     decisión), y un enlace directo al informe de /reportes/aprobaciones (filtros/export en
     fecha completos viven ahí, no en esta bandeja). --%>
<div class="card mb-3">
    <div class="card-body">
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo layout que Clientes (la
             referencia) en las 6 pantallas con buscador -- los campos en su propia fila, los
             botones (Limpiar antes de Filtrar) alineados a la derecha en la fila de abajo. --%>
        <form method="get" action="${pageContext.request.contextPath}/aprobaciones" id="formularioFiltrosAprobaciones"
              data-listado-vivo="resultadosAprobaciones">
            <input type="hidden" name="vista" value="historial">
            <%-- B175 (14/11/2026): el orden viaja dentro del formulario, igual que en Clientes
                 (B157) y Solicitudes (B167), así la búsqueda en vivo y la paginación por AJAX lo
                 conservan (listado-vivo.js manda el FormData). "Limpiar" no los toca
                 (limpiarFormulario solo vacía text/search/date/select), así que limpiar los
                 filtros deja el orden puesto. --%>
            <input type="hidden" name="orden" value="<%= ordenActualLista %>">
            <input type="hidden" name="sentido" value="<%= sentidoActualLista %>">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">N.º de solicitud, cédula o nombre</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaAprobaciones != null ? textoBusquedaAprobaciones : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Estado</label>
                    <select name="estado" class="form-control">
                        <option value="" <%= estadoFiltroActual.isEmpty() ? "selected" : "" %>>Todas</option>
                        <option value="APROBADA" <%= "APROBADA".equals(estadoFiltroActual) ? "selected" : "" %>>Aprobada</option>
                        <option value="RECHAZADA" <%= "RECHAZADA".equals(estadoFiltroActual) ? "selected" : "" %>>Rechazada</option>
                        <option value="CANCELADA" <%= "CANCELADA".equals(estadoFiltroActual) ? "selected" : "" %>>Cancelada</option>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="<%= desdeFiltroActual == null ? "" : desdeFiltroActual %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="<%= hastaFiltroActual == null ? "" : hastaFiltroActual %>">
                </div>
            </div>
            <div class="form-row justify-content-between align-items-center">
                <%-- B176 (14/11/2026): el enlace lleva los filtros que el informe sabe recibir --
                     las fechas y el texto del buscador (allá se llama clienteUOperacion). Antes
                     llevaba solo las fechas, así que el gerente que había buscado un cliente
                     aterrizaba en el informe sin su búsqueda. El filtro de ESTADO no viaja: el
                     informe no tiene dónde recibirlo (ver el javadoc del armador en el
                     controlador). La consulta la arma el servidor; acá solo se dibuja.
                     El respaldo (cadena vacía) es para esta JSP contra un .class viejo, antes del
                     Clean and Build: el enlace queda como antes de B176. --%>
                <div class="col-auto">
                    <%
                        String consultaInformeAprobaciones = (String) request.getAttribute("consultaInformeAprobaciones");
                        if (consultaInformeAprobaciones == null) { consultaInformeAprobaciones = ""; }
                    %>
                    <a href="${pageContext.request.contextPath}/reportes/aprobaciones<%= consultaInformeAprobaciones %>">Exportar en Informes <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
                </div>
                <div class="col-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <%-- B175: el href de respaldo (sin JavaScript) también conserva el orden,
                         para que los dos caminos hagan lo mismo. --%>
                    <a href="${pageContext.request.contextPath}/aprobaciones?vista=historial&<%= ordenEnUrlLista %>" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosAprobaciones">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-filter"></i> Filtrar</button>
                </div>
            </div>
        </form>
    </div>
</div>
<% } else { %>
<div class="card mb-3">
    <div class="card-body">
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo layout que Clientes. --%>
        <form method="get" action="${pageContext.request.contextPath}/aprobaciones" id="formularioFiltrosAprobaciones"
              data-listado-vivo="resultadosAprobaciones">
            <%-- B173 (14/11/2026): "Limpiar" y "Buscar" estaban en una fila propia y, con un
                 solo campo arriba, quedaban solos con mucho espacio vacío -- el mismo detalle que
                 Joel marcó en Clientes (B156) y Solicitudes (B166). Van en ESTA fila, a la derecha
                 (ml-auto) y alineados con el borde de abajo del campo (align-items-end). Las clases
                 de listado-vivo.js no cambian ni salen del <form>.
                 La fila de botones del HISTORIAL no se toca: ahí comparte el renglón con el enlace
                 "Exportar en Informes", así que no queda vacía. --%>
            <%-- B175: el orden viaja dentro del formulario, igual que en el Historial. --%>
            <input type="hidden" name="orden" value="<%= ordenActualLista %>">
            <input type="hidden" name="sentido" value="<%= sentidoActualLista %>">
            <div class="form-row align-items-end">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">N.º de solicitud, cédula o nombre del cliente</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaAprobaciones != null ? textoBusquedaAprobaciones : "" %>">
                </div>
                <div class="form-group col-auto ml-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/aprobaciones?<%= ordenEnUrlLista %>" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosAprobaciones">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>
    </div>
</div>
<% } %>

<div id="resultadosAprobaciones">
    <%@ include file="/WEB-INF/vistas/aprobaciones/_resultados.jsp" %>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
