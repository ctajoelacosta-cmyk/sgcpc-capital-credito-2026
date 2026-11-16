<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaReversionReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaReversionReporte> filasReversiones = (List<FilaReversionReporte>) request.getAttribute("filas");
    String resumenReversiones = (String) request.getAttribute("resumen");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Reversiones", (Integer) request.getAttribute("totalFilas"), "reversión", "reversiones") %></h1>
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
            <button type="submit" class="btn btn-primary mr-3">Filtrar</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasReversiones != null && !filasReversiones.isEmpty())); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(filasReversiones != null && !filasReversiones.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasReversiones != null && !filasReversiones.isEmpty()) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/reversiones/informe?desde=${desde}&hasta=${hasta}"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasReversiones != null && !filasReversiones.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasReversiones != null && !filasReversiones.isEmpty()) %> href="${pageContext.request.contextPath}/reportes/reversiones/exportar?desde=${desde}&hasta=${hasta}">
                <i class="fas fa-file-excel mr-1"></i>Excel/CSV</a>
        </form>
    </div>
</div>

<%-- B207 (15/11/2026, ítem 5): el aviso de rango invertido, en los trece reportes con
     fechas. No recibe nada: el fragmento lee los parámetros del pedido y el texto lo decide
     ServicioReportes.avisoRangoInvertido (un dato, un dueño). --%>
<%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

<% if (filasReversiones != null && !filasReversiones.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Fecha</th><th>Recibo revertido</th><th>Operación</th><th>Cliente</th><th class="cc-monto">Monto</th>
            <th>Motivo</th><th>Estado</th><th>Quién solicitó</th><th>Quién autorizó</th><th>Nota de anulación</th></tr></thead>
        <tbody>
        <% for (FilaReversionReporte fila : filasReversiones) { %>
        <tr style="cursor:pointer;" onclick="window.location='${pageContext.request.contextPath}/reportes/estado-cuenta/<%= fila.getIdPrestamo() %>'">
            <td><%= fila.getFecha() %></td>
            <td><%= fila.getNumeroRecibo() %></td>
            <td><%= fila.getIdentificadorOperacion() %></td>
            <td><%= fila.getClienteNombre() %></td>
            <td class="cc-monto"><%= fila.getMontoFormateado() %></td>
            <td><%= fila.getMotivo() %></td>
            <td><%= fila.getEstado() %></td>
            <td><%= fila.getSolicitadoPorNombre() %></td>
            <td><%= fila.getAutorizadoPorNombre() %></td>
            <td><%= fila.getNotaAnulacion() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (resumenReversiones != null) { %>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="10"><%= resumenReversiones %></td>
        </tr>
        </tfoot>
        <% } %>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<%-- B206 (15/11/2026, ítem 4 del informe de UX de Informes): el estado vacío iba en VERDE
     (alert-success) y acá vacío no es buena noticia: es un reporte de CONTROL: informa qué se corrigió, no que no haya nada que corregir. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay reversiones solicitadas en ese rango.</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
