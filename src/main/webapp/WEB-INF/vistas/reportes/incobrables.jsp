<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaMora" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesIncobrables" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaMora> filasIncobrables = (List<FilaMora>) request.getAttribute("filas");
    TotalesIncobrables totalesIncobrables = (TotalesIncobrables) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Incobrables", (Integer) request.getAttribute("totalFilas"), "préstamo", "préstamos") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a> —
    préstamos en mora con más días de atraso que el límite configurado.</p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
    <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasIncobrables != null && !filasIncobrables.isEmpty())); %>
    <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasIncobrables != null && !filasIncobrables.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasIncobrables != null && !filasIncobrables.isEmpty()) %> target="_blank"
       href="${pageContext.request.contextPath}/reportes/incobrables/informe"><i class="fas fa-print"></i> Informe PDF</a>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasIncobrables != null && !filasIncobrables.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasIncobrables != null && !filasIncobrables.isEmpty()) %>
       href="${pageContext.request.contextPath}/reportes/incobrables/exportar"><i class="fas fa-file-excel"></i> Excel/CSV</a>
</p>

<% if (filasIncobrables != null && !filasIncobrables.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead><tr><th>Operación</th><th>Cliente</th><th>Teléfono</th><th>Días atraso</th><th class="cc-monto">Monto exigible</th></tr></thead>
        <tbody>
        <% for (FilaMora filaIncobrable : filasIncobrables) { %>
        <tr>
            <td><a href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaIncobrable.getIdPrestamo() %>"><%= filaIncobrable.getIdentificadorOperacion() %></a></td>
            <td><%= filaIncobrable.getClienteNombre() %></td>
            <td><%= filaIncobrable.getClienteTelefono() %></td>
            <td><%= filaIncobrable.getDiasAtraso() %></td>
            <td class="cc-monto"><%= filaIncobrable.getMontoExigible() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (totalesIncobrables != null) { %>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="3">Total</td>
            <td><%= totalesIncobrables.getCantidad() %></td>
            <td>Saldo <%= totalesIncobrables.getSaldo() %></td>
        </tr>
        </tfoot>
        <% } %>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<div class="alert alert-success">No hay préstamos incobrables en este momento.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
