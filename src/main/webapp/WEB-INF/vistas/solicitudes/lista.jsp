<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String estadoFiltroActual = (String) request.getAttribute("estadoFiltro");
    if (estadoFiltroActual == null) {
        estadoFiltroActual = "";
    }
    String textoBusquedaSolicitudes = (String) request.getAttribute("textoBusqueda");
    // B167 (14/11/2026): el orden vigente, que ya viene validado contra la lista blanca del
    // servicio. El respaldo es el orden de siempre (fecha, descendente).
    String ordenActualLista = (String) request.getAttribute("ordenActual");
    String sentidoActualLista = (String) request.getAttribute("sentidoActual");
    if (ordenActualLista == null) { ordenActualLista = "fecha"; }
    if (sentidoActualLista == null) { sentidoActualLista = "desc"; }
%>
<h1>Solicitudes</h1>

<a href="${pageContext.request.contextPath}/solicitudes/nuevo" class="btn btn-primary mb-3">
    <i class="fas fa-plus"></i> Nueva solicitud
</a>

<div class="card">
    <div class="card-body">
        <%-- Ajuste 3 (Joel): filtro por estado. Tanda de usabilidad (20/10/2026, pedido de Joel,
             RNF_revision.md §10c, paso 3): buscador de texto agregado DENTRO de este mismo
             <form> (no uno aparte), mismo criterio que Historial de Aprobaciones -- así
             listado-vivo.js manda estado + texto juntos en el mismo pedido AJAX. Se sacó el
             onchange="this.form.submit()" del <select> (ya lo intercepta listado-vivo.js con
             'change'; dejarlo hubiera disparado el pedido dos veces). Botones Limpiar/Buscar
             agregados (20/10/2026, mismo orden/clase/ícono/layout que Clientes, la referencia en
             las 6 pantallas con buscador) -- "Buscar", visible y sin JS, ya es el respaldo nativo
             (submit normal del <form>), así que se sacó el botón duplicado del <noscript>. --%>
        <form method="get" action="${pageContext.request.contextPath}/solicitudes" id="formularioFiltrosSolicitudes"
              class="mb-3" data-listado-vivo="resultadosSolicitudes">
            <%-- B167 (14/11/2026): el orden viaja DENTRO del formulario, no solo en los enlaces de
                 los encabezados, así la búsqueda en vivo lo conserva (listado-vivo.js manda el
                 FormData). "Limpiar" no los toca -- limpiarFormulario solo vacía
                 text/search/date/select --, así que limpiar los filtros deja el orden. --%>
            <input type="hidden" name="orden" value="<%= ordenActualLista %>">
            <input type="hidden" name="sentido" value="<%= sentidoActualLista %>">
            <%-- B166 (14/11/2026): "Limpiar" y "Buscar" estaban en una fila propia y quedaban
                 solos, con mucho espacio vacío arriba -- el mismo detalle que Joel marcó en
                 Clientes y que se corrigió en B156. Van en ESTA fila, a la derecha (ml-auto) y
                 alineados con el borde de abajo de los campos (align-items-end), que son más
                 altos porque llevan etiqueta. Las clases de listado-vivo.js no cambian ni salen
                 del <form>. --%>
            <div class="form-row align-items-end">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">N.º de solicitud, cédula o nombre del cliente</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaSolicitudes != null ? textoBusquedaSolicitudes : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Estado</label>
                    <select name="estado" class="form-control">
                        <option value="" <%= estadoFiltroActual.isEmpty() ? "selected" : "" %>>Todas</option>
                        <% for (EstadoSolicitud estado : EstadoSolicitud.values()) { %>
                        <option value="<%= estado.name() %>" <%= estado.name().equals(estadoFiltroActual) ? "selected" : "" %>><%= estado.getEtiqueta() %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group col-auto ml-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/solicitudes" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosSolicitudes">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>

        <div id="resultadosSolicitudes">
            <%@ include file="/WEB-INF/vistas/solicitudes/_resultados.jsp" %>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
