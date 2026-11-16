<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.ParametroSistema" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaHistorialParametro> filasHistorial = (List<FilaHistorialParametro>) request.getAttribute("filas");
    TotalesHistorialParametros totalesHistorial = (TotalesHistorialParametros) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<ParametroSistema> parametrosDisponiblesHistorial =
            (List<ParametroSistema>) request.getAttribute("parametrosDisponibles");
    String parametroSeleccionadoHistorial = (String) request.getAttribute("parametro");
%>
<h1>Historial de cambios de parámetros</h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/parametros">&laquo; Parámetros</a> ·
    Solo lectura: esta pantalla no cambia ningún parámetro.</p>

<%-- RF39 (pantalla rediseñada, pedido de Joel): mismo patrón de filtros que el resto de los
     reportes (reportes/informe-desembolsos.jsp) -- grilla de 4 columnas, Limpiar + Aplicar
     filtros, PDF/Excel con formaction sobre este mismo <form>. Checkbox "Incluir valores
     iniciales" con el mismo molde que "Solo promesas incumplidas" (reportes/gestiones.jsp). --%>
<div class="card">
    <div class="card-body">
        <form method="get" id="formularioFiltrosHistorialParametros">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="${desde}">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="${hasta}">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Parámetro</label>
                    <select name="parametro" class="form-control">
                        <option value="">(todos)</option>
                        <% if (parametrosDisponiblesHistorial != null) {
                            for (ParametroSistema p : parametrosDisponiblesHistorial) { %>
                        <option value="<%= p.getClaveParametro() %>"
                            <%= p.getClaveParametro().equals(parametroSeleccionadoHistorial) ? "selected" : "" %>>
                            <%= p.getNombreParametro() %></option>
                        <% } } %>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3 d-flex align-items-end">
                    <div class="form-check mb-0">
                        <input type="checkbox" class="form-check-input" name="incluirIniciales" value="true"
                               id="incluirIniciales" ${incluirIniciales ? 'checked' : ''}>
                        <label class="form-check-label" for="incluirIniciales">Incluir valores iniciales</label>
                    </div>
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/parametros/historial" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-filter"></i> Aplicar filtros</button>
                </div>
            </div>
        </form>
    </div>
</div>

<div class="d-flex justify-content-between align-items-center flex-wrap listado-vivo-barra-resultados mt-3">
    <p class="text-muted mb-2">
        <% if (totalesHistorial != null) { %>
        <%= totalesHistorial.getCantidadTotal() %> cambio<%= totalesHistorial.getCantidadTotal() == 1 ? "" : "s" %> en el período
        · <%= totalesHistorial.getCantidadParametrosDistintos() %> parámetro<%= totalesHistorial.getCantidadParametrosDistintos() == 1 ? "" : "s" %> modificado<%= totalesHistorial.getCantidadParametrosDistintos() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaHistorialParametros">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaHistorialParametros"></span>
        <button type="submit" form="formularioFiltrosHistorialParametros" formaction="${pageContext.request.contextPath}/parametros/historial/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm"
                title="Exporta según los filtros aplicados (fechas, parámetro, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosHistorialParametros" formaction="${pageContext.request.contextPath}/parametros/historial/exportar"
                class="btn btn-outline-secondary btn-sm"
                title="Exporta según los filtros aplicados (fechas, parámetro, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>

<div class="card">
    <div class="card-body">
        <% if (filasHistorial != null && !filasHistorial.isEmpty()) { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped" id="tablaHistorialParametros">
                <thead>
                <tr><th class="text-nowrap">Fecha y hora</th><th>Parámetro</th><th>Cambio</th><th>Usuario</th></tr>
                </thead>
                <tbody>
                <% for (FilaHistorialParametro fila : filasHistorial) { %>
                <tr>
                    <td class="text-nowrap"><%= fila.getFechaModificacion() %></td>
                    <td><%= fila.getParametro() %><br><small class="text-muted"><%= fila.getClaveParametro() %></small></td>
                    <td><%= fila.getValorAnteriorMostrado() %> &rarr; <strong><%= fila.getValorNuevoMostrado() %></strong>
                        <% if (!"—".equals(fila.getObservacion())) { %>
                        <br><small class="text-muted"><%= fila.getObservacion() %></small>
                        <% } %>
                    </td>
                    <td><%= fila.getModificadoPor() %></td>
                </tr>
                <% } %>
                <tr class="buscar-en-tabla-sin-coincidencias d-none">
                    <td colspan="4" class="text-center text-muted">Sin coincidencias.</td>
                </tr>
                </tbody>
            </table>
        </div>
        <%-- B219 (15/11/2026): LA paginación del sistema (B167). La URL base y el "sin listado
             vivo" los dejó PaginacionPantalla.paginar en el servlet, igual que en los dieciséis
             reportes de B210. El contador del total ya está arriba, en la barra de resultados
             (es el del listado completo, no el de la página). --%>
        <%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
        <% } else { %>
        <div class="alert alert-success mt-3">No hay cambios de parámetros en ese rango.</div>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
