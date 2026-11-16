<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%
    Object usuarioSesion = session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    if (usuarioSesion != null) {
        response.sendRedirect(request.getContextPath() + "/panel");
    } else {
        response.sendRedirect(request.getContextPath() + "/acceso");
    }
%>
