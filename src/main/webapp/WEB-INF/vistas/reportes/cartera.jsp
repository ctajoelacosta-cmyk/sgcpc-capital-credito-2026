<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaCartera" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesCartera" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaCartera> filasCartera = (List<FilaCartera>) request.getAttribute("filas");
    TotalesCartera totalesCartera = (TotalesCartera) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<ResumenEstadoCartera> resumenPorEstadoCartera = (List<ResumenEstadoCartera>) request.getAttribute("resumenPorEstado");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Cartera", (Integer) request.getAttribute("totalFilas"), "préstamo", "préstamos") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a> —
    préstamos activos y en mora, a <%= FormateadorFechas.comoDdMmAaaa(java.time.LocalDate.now()) %>.</p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
    <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasCartera != null && !filasCartera.isEmpty())); %>
    <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasCartera != null && !filasCartera.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCartera != null && !filasCartera.isEmpty()) %> target="_blank"
       href="${pageContext.request.contextPath}/reportes/cartera/informe"><i class="fas fa-print"></i> Informe PDF</a>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasCartera != null && !filasCartera.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCartera != null && !filasCartera.isEmpty()) %>
       href="${pageContext.request.contextPath}/reportes/cartera/exportar"><i class="fas fa-file-excel"></i> Excel/CSV</a>
</p>

<% if (filasCartera != null && !filasCartera.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead><tr><th>Operación</th><th>Cliente</th><th class="cc-monto">Monto aprobado</th><th class="cc-monto">Saldo</th>
            <th class="cc-monto">Interés futuro programado (no exigible, Ley 6624)</th><th>Estado</th></tr></thead>
        <tbody>
        <% for (FilaCartera filaCartera : filasCartera) { %>
        <tr>
            <td><%= filaCartera.getIdentificadorOperacion() %></td>
            <td><%= filaCartera.getClienteNombre() %></td>
            <td class="cc-monto"><%= filaCartera.getMontoAprobado() %></td>
            <td class="cc-monto"><%= filaCartera.getCapitalPendiente() %></td>
            <td class="cc-monto"><%= filaCartera.getInteresFuturoProgramado() %></td>
            <td><%= filaCartera.getEstado() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (totalesCartera != null) { %>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="2">Total (<%= totalesCartera.getCantidadPrestamos() %> préstamo<%= totalesCartera.getCantidadPrestamos() == 1 ? "" : "s" %>)</td>
            <td></td>
            <td class="cc-monto cc-sin-partir"><%= totalesCartera.getCapitalPendiente() %></td>
            <td class="cc-monto cc-sin-partir"><%= totalesCartera.getInteresFuturo() %></td>
            <td></td>
        </tr>
        <% if (resumenPorEstadoCartera != null) { %>
        <% for (ResumenEstadoCartera resumen : resumenPorEstadoCartera) { %>
        <tr>
            <td colspan="2" class="text-right">Resumen <%= resumen.getEstado() %></td>
            <td></td>
            <td><%= resumen.getCantidad() %> préstamo<%= resumen.getCantidad() == 1 ? "" : "s" %> — saldo <%= resumen.getSaldo() %></td>
            <td></td>
            <td></td>
        </tr>
        <% } %>
        <% } %>
        </tfoot>
        <% } %>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<%-- B206 (15/11/2026, ítem 4 del informe de UX de Informes): el estado vacío iba en VERDE
     (alert-success) y acá vacío no es buena noticia: un crédito sin ningún préstamo activo es el negocio parado. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<div class="alert alert-secondary">No hay préstamos activos en este momento.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
