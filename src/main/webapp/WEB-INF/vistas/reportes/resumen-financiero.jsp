<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResumenFinanciero" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.SubtotalTramo" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    ResumenFinanciero resumenFinanciero = (ResumenFinanciero) request.getAttribute("resumen");
%>
<h1>Resumen financiero</h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<div class="card">
    <div class="card-body">
        <form method="get" class="form-inline">
            <label class="mr-2">Desde</label>
            <input type="date" name="desde" class="form-control mr-3" value="${desde}">
            <label class="mr-2">Hasta</label>
            <input type="date" name="hasta" class="form-control mr-3" value="${hasta}">
            <button type="submit" class="btn btn-primary mr-3">Ver</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(resumenFinanciero != null)); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(resumenFinanciero != null) %>"<%= ServicioInformes.atributosBotonExportar(resumenFinanciero != null) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/resumen-financiero/informe?desde=${desde}&hasta=${hasta}"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(resumenFinanciero != null) %>"<%= ServicioInformes.atributosBotonExportar(resumenFinanciero != null) %> href="${pageContext.request.contextPath}/reportes/resumen-financiero/csv?desde=${desde}&hasta=${hasta}">
                <i class="fas fa-file-csv mr-1"></i>Descargar CSV</a>
        </form>
    </div>
</div>

<%-- B207 (15/11/2026, ítem 5): el aviso de rango invertido, en los trece reportes con
     fechas. No recibe nada: el fragmento lee los parámetros del pedido y el texto lo decide
     ServicioReportes.avisoRangoInvertido (un dato, un dueño). --%>
<%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

<% if (resumenFinanciero != null) { %>
<div class="row mt-3">
    <div class="col-md-3">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Créditos otorgados</h5></div>
            <div class="card-body">
                <p>Cantidad: <strong><%= resumenFinanciero.getOtorgadosCantidad() %></strong></p>
                <p>Monto: <strong><%= resumenFinanciero.getOtorgadosMonto() %></strong></p>
            </div>
        </div>
    </div>
    <div class="col-md-3">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Créditos en trámite</h5></div>
            <div class="card-body">
                <p>Cantidad: <strong><%= resumenFinanciero.getEnTramiteCantidad() %></strong></p>
                <p>Monto: <strong><%= resumenFinanciero.getEnTramiteMonto() %></strong></p>
            </div>
        </div>
    </div>
    <%-- Bloque 7/E10 (D-129, Obs. 28): aprobado y todavía sin desembolsar -- foto de hoy. --%>
    <div class="col-md-3">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Aprobados sin desembolsar</h5></div>
            <div class="card-body">
                <p>Cantidad: <strong><%= resumenFinanciero.getAprobadosSinDesembolsarCantidad() %></strong></p>
                <p>Monto: <strong><%= resumenFinanciero.getAprobadosSinDesembolsarMonto() %></strong></p>
                <p class="text-muted mb-0"><small>Foto de hoy, no depende del rango elegido.</small></p>
            </div>
        </div>
    </div>
    <div class="col-md-3">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Morosidad actual (a hoy)</h5></div>
            <div class="card-body">
                <p>Por cantidad de préstamos: <strong><%= resumenFinanciero.getMorosidadPorcentajeCantidad() %></strong></p>
                <%-- 13/10/2026 (hallazgo de Joel): la etiqueta decía "Por monto de cartera", pero
                     el número es capital VENCIDO sobre la cartera (ServicioReportes.
                     calcularResumenFinanciero); no el saldo total de los préstamos en mora. Se
                     corrige la etiqueta y se agrega abajo la otra mirada. --%>
                <p>Por capital vencido sobre la cartera: <strong><%= resumenFinanciero.getMorosidadPorcentajeMonto() %></strong></p>
                <p>Saldo de préstamos en mora: <strong><%= resumenFinanciero.getSaldoPrestamosEnMoraPorcentaje() %></strong></p>
            </div>
        </div>
    </div>
</div>

<div class="row mt-3">
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Composición de lo cobrado en el período</h5></div>
            <div class="card-body">
                <table class="table table-sm">
                    <tr><td>Capital</td><td class="cc-monto"><%= resumenFinanciero.getCobradoCapital() %></td></tr>
                    <tr><td>Interés (compensatorio)</td><td class="cc-monto"><%= resumenFinanciero.getCobradoInteres() %></td></tr>
                    <tr><td>Moratorio</td><td class="cc-monto"><%= resumenFinanciero.getCobradoMoratorio() %></td></tr>
                    <tr><td>Punitorio</td><td class="cc-monto"><%= resumenFinanciero.getCobradoPunitorio() %></td></tr>
                    <tr><td>IVA (para el Estado)</td><td class="cc-monto"><%= resumenFinanciero.getCobradoIva() %></td></tr>
                    <tr class="font-weight-bold"><td style="white-space: nowrap;">Total cobrado</td><td class="cc-monto cc-sin-partir"><%= resumenFinanciero.getCobradoTotal() %></td></tr>
                    <tr class="font-weight-bold text-success"><td>Ganancia bruta del período (interés + moratorio + punitorio, sin IVA)</td>
                        <td class="cc-monto cc-sin-partir"><%= resumenFinanciero.getGananciaDelPeriodo() %></td></tr>
                </table>
            </div>
        </div>
    </div>
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Cartera (a hoy)</h5></div>
            <div class="card-body">
                <table class="table table-sm">
                    <tr><td>Capital pendiente</td><td class="cc-monto"><%= resumenFinanciero.getCarteraCapitalPendiente() %></td></tr>
                    <tr><td>Interés futuro programado</td><td class="cc-monto"><%= resumenFinanciero.getCarteraInteresFuturo() %></td></tr>
                </table>
                <p class="text-muted mb-0"><small>La cartera es una foto de hoy, no depende del rango de fechas elegido.</small></p>
            </div>
        </div>
    </div>
</div>

<div class="row mt-3">
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Demanda por tramo de monto (solicitudes del período)</h5></div>
            <div class="card-body">
                <table class="table table-sm table-striped">
                    <thead><tr><th>Tramo</th><th>Cantidad</th><th class="cc-monto">Monto</th></tr></thead>
                    <tbody>
                    <% if (resumenFinanciero.getDemandaPorMonto() != null) { for (SubtotalTramo tramo : resumenFinanciero.getDemandaPorMonto()) { %>
                    <tr><td><%= tramo.getTramo() %></td><td><%= tramo.getCantidad() %></td><td class="cc-monto"><%= tramo.getMonto() %></td></tr>
                    <% } }
                    if (resumenFinanciero.getDemandaPorMonto() == null || resumenFinanciero.getDemandaPorMonto().isEmpty()) { %>
                    <tr><td colspan="3" class="text-muted">Sin solicitudes en el período.</td></tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    <div class="col-md-6">
        <div class="card">
            <div class="card-header"><h5 class="m-0">Demanda por tramo de plazo (solicitudes del período)</h5></div>
            <div class="card-body">
                <table class="table table-sm table-striped">
                    <thead><tr><th>Tramo</th><th>Cantidad</th><th class="cc-monto">Monto</th></tr></thead>
                    <tbody>
                    <% if (resumenFinanciero.getDemandaPorPlazo() != null) { for (SubtotalTramo tramo : resumenFinanciero.getDemandaPorPlazo()) { %>
                    <tr><td><%= tramo.getTramo() %></td><td><%= tramo.getCantidad() %></td><td class="cc-monto"><%= tramo.getMonto() %></td></tr>
                    <% } }
                    if (resumenFinanciero.getDemandaPorPlazo() == null || resumenFinanciero.getDemandaPorPlazo().isEmpty()) { %>
                    <tr><td colspan="3" class="text-muted">Sin solicitudes en el período.</td></tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
