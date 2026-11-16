<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    // El error real (con stacktrace) ya quedo en el log del servidor (Tomcat lo
    // escribe solo); aca nunca se muestra SQL, nombres de clase ni la version
    // de Tomcat (Doc. 10 §10.18).
    String destino = session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO) != null ? "/panel" : "/acceso";
%>
<div class="text-center mt-5">
    <h1 class="display-4"><i class="fas fa-exclamation-triangle text-danger"></i></h1>
    <p class="lead">Ocurrió un error inesperado. Ya quedó registrado para revisarlo.</p>
    <a href="${pageContext.request.contextPath}<%= destino %>" class="btn btn-primary">
        <i class="fas fa-home"></i> Volver al panel
    </a>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
