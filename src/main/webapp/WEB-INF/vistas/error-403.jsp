<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String destino = session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO) != null ? "/panel" : "/acceso";
%>
<div class="text-center mt-5">
    <h1 class="display-4"><i class="fas fa-ban text-warning"></i> Acceso restringido</h1>
    <p class="lead">No tiene permiso para acceder a esta sección.</p>
    <a href="${pageContext.request.contextPath}<%= destino %>" class="btn btn-primary">
        <i class="fas fa-home"></i> Volver al panel
    </a>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
