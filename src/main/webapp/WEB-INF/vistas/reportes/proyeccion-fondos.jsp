<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaProyeccionFondos> filasProyeccion = (List<FilaProyeccionFondos>) request.getAttribute("filas");
%>
<h1>Proyección de fondos</h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p class="text-muted">Foto de hoy: no depende de un rango de fechas elegido.</p>
<p>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
    <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasProyeccion != null && !filasProyeccion.isEmpty())); %>
    <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasProyeccion != null && !filasProyeccion.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasProyeccion != null && !filasProyeccion.isEmpty()) %> target="_blank"
       href="${pageContext.request.contextPath}/reportes/proyeccion-fondos/informe"><i class="fas fa-print"></i> Informe PDF</a>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasProyeccion != null && !filasProyeccion.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasProyeccion != null && !filasProyeccion.isEmpty()) %>
       href="${pageContext.request.contextPath}/reportes/proyeccion-fondos/exportar"><i class="fas fa-file-excel"></i> Excel/CSV</a>
</p>

<% if (filasProyeccion != null && !filasProyeccion.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Horizonte</th><th>Ingresos esperados (cobros)</th><th>Egresos comprometidos (a desembolsar)</th>
            <th>Posibles egresos (solicitudes en trámite)</th><th>Flujo neto proyectado</th></tr></thead>
        <tbody>
        <% for (FilaProyeccionFondos fila : filasProyeccion) { %>
        <tr>
            <td>A <%= fila.getHorizonteDias() %> días</td>
            <td><%= fila.getIngresosEsperados() %></td>
            <td><%= fila.getEgresosComprometidos() %></td>
            <td class="text-muted"><%= fila.getPosiblesEgresosEnTramite() %></td>
            <% if (fila.isFlujoNetoNegativo()) { %>
            <td class="text-danger font-weight-bold">
                <%= fila.getFlujoNeto() %>
                <br><small>Necesidad de fondos: <%= fila.getFlujoNeto().replace("-", "") %></small>
            </td>
            <% } else { %>
            <td><%= fila.getFlujoNeto() %></td>
            <% } %>
        </tr>
        <% } %>
        </tbody>
    </table>
</div>
<p class="text-muted mt-2"><small>Ingresos esperados: cuotas de préstamos activos/en mora que vencen dentro de cada horizonte
    (acumulado), con el importe ya programado (sin recalcular nada) -- <strong>incluye las cuotas de préstamos en
    mora</strong> (ya vencidas, pero que se siguen esperando cobrar). Egresos comprometidos: monto ya aprobado y
    todavía sin desembolsar (solicitudes aprobadas sin formalizar + préstamos pendientes de desembolso) -- no tiene
    una fecha programada propia, por eso se muestra igual en los tres horizontes. Posibles egresos (solicitudes en
    trámite): solicitudes todavía sin decidir (ni aprobadas ni rechazadas) -- solo informativo, <strong>no se suma
    al flujo neto</strong> porque todavía no es un compromiso real.</small></p>
<% } else { %>
<div class="alert alert-warning mt-3">No se pudo calcular la proyección.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
