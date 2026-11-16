<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResumenMensual" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    ResumenMensual resumenMensual = (ResumenMensual) request.getAttribute("resumen");
    java.time.YearMonth mesResumen = (java.time.YearMonth) request.getAttribute("mes");
%>
<h1>Resumen mensual</h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<div class="card">
    <div class="card-body">
        <form method="get" class="form-inline">
            <label class="mr-2">Mes</label>
            <input type="month" name="mes" class="form-control mr-3" value="<%= mesResumen %>">
            <button type="submit" class="btn btn-primary mr-3">Ver</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(resumenMensual != null)); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(resumenMensual != null) %>"<%= ServicioInformes.atributosBotonExportar(resumenMensual != null) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/resumen-mensual/informe?mes=<%= mesResumen %>"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(resumenMensual != null) %>"<%= ServicioInformes.atributosBotonExportar(resumenMensual != null) %> href="${pageContext.request.contextPath}/reportes/resumen-mensual/csv?mes=<%= mesResumen %>">
                <i class="fas fa-file-csv mr-1"></i>Descargar CSV</a>
        </form>
    </div>
</div>

<% if (resumenMensual != null) { %>
<div class="row mt-3">
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Préstamos otorgados</h5></div>
            <div class="card-body">
                <p>Cantidad: <strong><%= resumenMensual.getPrestamosOtorgadosCantidad() %></strong></p>
                <p>Monto: <strong><%= resumenMensual.getPrestamosOtorgadosMonto() %></strong></p>
            </div>
        </div>
    </div>
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Morosidad actual (a hoy)</h5></div>
            <div class="card-body">
                <p>Por cantidad de préstamos: <strong><%= resumenMensual.getMorosidadPorcentajeCantidad() %></strong></p>
                <%-- 13/10/2026 (hallazgo de Joel): la etiqueta decía "Por monto de cartera", pero
                     el número es capital VENCIDO sobre la cartera (ServicioReportes.
                     calcularResumenMensual); no el saldo total de los préstamos en mora. Se
                     corrige la etiqueta y se agrega abajo la otra mirada. --%>
                <p>Por capital vencido sobre la cartera: <strong><%= resumenMensual.getMorosidadPorcentajeMonto() %></strong></p>
                <p>Saldo de préstamos en mora: <strong><%= resumenMensual.getSaldoPrestamosEnMoraPorcentaje() %></strong></p>
            </div>
        </div>
    </div>
</div>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Composición de lo cobrado</h5></div>
    <div class="card-body">
        <table class="table table-sm">
            <tr><td>Capital</td><td class="cc-monto"><%= resumenMensual.getCobradoCapital() %></td></tr>
            <tr><td>Interés (compensatorio)</td><td class="cc-monto"><%= resumenMensual.getCobradoInteres() %></td></tr>
            <tr><td>Moratorio</td><td class="cc-monto"><%= resumenMensual.getCobradoMoratorio() %></td></tr>
            <tr><td>Punitorio</td><td class="cc-monto"><%= resumenMensual.getCobradoPunitorio() %></td></tr>
            <tr><td>IVA</td><td class="cc-monto"><%= resumenMensual.getCobradoIva() %></td></tr>
            <tr class="font-weight-bold"><td>Total cobrado</td><td class="cc-monto cc-sin-partir"><%= resumenMensual.getCobradoTotal() %></td></tr>
            <tr class="font-weight-bold text-success"><td>Ganancia del mes (interés + moratorio + punitorio, sin IVA)</td>
                <td class="cc-monto cc-sin-partir"><%= resumenMensual.getGananciaDelMes() %></td></tr>
        </table>
    </div>
</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
