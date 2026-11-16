<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String destino = session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO) != null ? "/panel" : "/acceso";
%>
<div class="text-center mt-5">
    <%-- B135 (11/11/2026, revisión final): el título decía "404". Era el único lugar donde el
         sistema le habla al usuario con el código del protocolo (B85: lo que se lee se llama como
         el trámite, no como el sistema); el 403 de al lado ya decía "Acceso restringido". El
         número se conserva en el renglón de abajo, que es donde sirve: para nombrarlo si hay que
         reportar el problema. --%>
    <h1 class="display-4"><i class="fas fa-question-circle text-secondary"></i> Página no encontrada</h1>
    <p class="lead">La página que buscaba no existe (error 404).</p>
    <a href="${pageContext.request.contextPath}<%= destino %>" class="btn btn-primary">
        <i class="fas fa-home"></i> Volver al panel
    </a>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
