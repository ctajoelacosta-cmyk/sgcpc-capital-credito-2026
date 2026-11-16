<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaCierreDocumentalReporte> filasCierres = (List<FilaCierreDocumentalReporte>) request.getAttribute("filas");
    TotalesCierresDocumentales totalesCierres = (TotalesCierresDocumentales) request.getAttribute("totales");
    FiltroEstadoPagare filtroPagareCierres = (FiltroEstadoPagare) request.getAttribute("filtroPagare");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Cierres documentales", (Integer) request.getAttribute("totalFilas"), "cierre", "cierres") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p class="text-muted">Todos los préstamos cancelados en el período, con o sin el pagaré ya devuelto.</p>

<div class="card">
    <div class="card-body">
        <form method="get" class="form-inline">
            <label class="mr-2">Desde</label>
            <input type="date" name="desde" class="form-control mr-3" value="${desde}">
            <label class="mr-2">Hasta</label>
            <input type="date" name="hasta" class="form-control mr-3" value="${hasta}">
            <label class="mr-2">Pagaré</label>
            <select name="pagare" class="form-control mr-3">
                <option value="TODOS" <%= filtroPagareCierres == FiltroEstadoPagare.TODOS ? "selected" : "" %>>Todos</option>
                <option value="DEVUELTOS" <%= filtroPagareCierres == FiltroEstadoPagare.DEVUELTOS ? "selected" : "" %>>Devueltos</option>
                <option value="PENDIENTES" <%= filtroPagareCierres == FiltroEstadoPagare.PENDIENTES ? "selected" : "" %>>Pendientes de devolución</option>
            </select>
            <button type="submit" class="btn btn-primary mr-3">Filtrar</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasCierres != null && !filasCierres.isEmpty())); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(filasCierres != null && !filasCierres.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCierres != null && !filasCierres.isEmpty()) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/cierres-documentales/informe?desde=${desde}&hasta=${hasta}&pagare=${filtroPagare}"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasCierres != null && !filasCierres.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCierres != null && !filasCierres.isEmpty()) %> href="${pageContext.request.contextPath}/reportes/cierres-documentales/exportar?desde=${desde}&hasta=${hasta}&pagare=${filtroPagare}">
                <i class="fas fa-file-excel mr-1"></i>Excel/CSV</a>
        </form>
    </div>
</div>

<%-- B207 (15/11/2026, ítem 5): el aviso de rango invertido, en los trece reportes con
     fechas. No recibe nada: el fragmento lee los parámetros del pedido y el texto lo decide
     ServicioReportes.avisoRangoInvertido (un dato, un dueño). --%>
<%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

<% if (filasCierres != null && !filasCierres.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Operación</th><th>Cliente</th><th>Fecha de cancelación</th><th>Constancia de cancelación</th>
            <th>Estado del pagaré</th><th>Constancia de devolución</th><th>Fecha de devolución</th><th>Quién entregó</th></tr></thead>
        <tbody>
        <% for (FilaCierreDocumentalReporte fila : filasCierres) { %>
        <tr style="cursor:pointer;" onclick="window.location='${pageContext.request.contextPath}/reportes/estado-cuenta/<%= fila.getIdPrestamo() %>'">
            <td><%= fila.getIdentificadorOperacion() %></td>
            <td><%= fila.getClienteNombre() %></td>
            <td><%= fila.getFechaCancelacion() %></td>
            <td><%= fila.getNumeroConstanciaCancelacion() %></td>
            <td><% if (fila.isPagareDevuelto()) { %>
                <span class="badge badge-success"><%= fila.getEstadoPagare() %></span>
                <% } else { %>
                <span class="badge badge-warning" style="color:#a15c00;background-color:#fff3cd;"><%= fila.getEstadoPagare() %></span>
                <% } %></td>
            <td><%= fila.getNumeroConstanciaDevolucion() %></td>
            <td><%= fila.getFechaDevolucionPagare() %></td>
            <td><%= fila.getEntregadoPorNombre() %></td>
        </tr>
        <% } %>
        </tbody>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="8">Total: <%= totalesCierres.getCantidadCancelados() %> cancelado<%= totalesCierres.getCantidadCancelados() == 1 ? "" : "s" %>
                — <%= totalesCierres.getCantidadDevueltos() %> devuelto<%= totalesCierres.getCantidadDevueltos() == 1 ? "" : "s" %>
                — <%= totalesCierres.getCantidadPendientes() %> pendiente<%= totalesCierres.getCantidadPendientes() == 1 ? "" : "s" %> de devolución.</td>
        </tr>
        </tfoot>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<%-- B206 (15/11/2026, ítem 4 del informe de UX de Informes): el estado vacío iba en VERDE
     (alert-success) y acá vacío no es buena noticia: ningún préstamo cancelado no es bueno ni malo, es simplemente que no hubo. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay préstamos cancelados en ese rango (con ese filtro).</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
