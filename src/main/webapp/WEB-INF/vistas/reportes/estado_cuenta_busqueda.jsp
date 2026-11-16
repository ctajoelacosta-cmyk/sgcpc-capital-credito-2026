<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String textoBusquedaEstadoCuenta = (String) request.getAttribute("textoBusqueda");
%>
<h1>Estado de cuenta del cliente</h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>

<div class="card">
    <div class="card-header"><h5 class="m-0">Buscar préstamo</h5></div>
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/reportes/estado-cuenta" class="form-inline">
            <input type="text" name="q" class="form-control mr-2" style="width: 320px;"
                   placeholder="Cédula o número de operación (ej. CC-2026-00029)"
                   value="<%= textoBusquedaEstadoCuenta == null ? "" : textoBusquedaEstadoCuenta %>">
            <button type="submit" class="btn btn-primary">Buscar</button>
        </form>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
