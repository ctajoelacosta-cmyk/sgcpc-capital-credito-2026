<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaFacturaAnulada> filasFacturasAnuladas = (List<FilaFacturaAnulada>) request.getAttribute("filas");
    java.time.YearMonth mesFacturasAnuladas = (java.time.YearMonth) request.getAttribute("mes");
    TotalesFacturasAnuladas totalesFacturasAnuladas = (TotalesFacturasAnuladas) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Facturas anuladas del mes", (Integer) request.getAttribute("totalFilas"), "factura", "facturas") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p class="text-muted">Para dar de baja en Marangatu. Cada anulación viene de una reversión
    <%-- B210, tercera vuelta (15/11/2026, detalle que Joel encontró al probar): decía
         "autorizada -- ver la Nota" con dos guiones. En texto de pantalla va la raya (—); los
         "--" de este archivo son todos de comentarios JSP, y es el único que estaba en el
         cuerpo (barrido de los 21 reportes y de los literales de src/main). --%>
    de cobro autorizada — ver la Nota de anulación de cada una en la ficha del préstamo.</p>

<div class="card">
    <div class="card-body">
        <form method="get" class="form-inline">
            <label class="mr-2">Mes</label>
            <input type="month" name="mes" class="form-control mr-3" value="<%= mesFacturasAnuladas %>">
            <button type="submit" class="btn btn-primary mr-3">Ver</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty())); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty()) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/facturas-anuladas/informe?mes=<%= mesFacturasAnuladas %>"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty()) %> href="${pageContext.request.contextPath}/reportes/facturas-anuladas/csv?mes=<%= mesFacturasAnuladas %>">
                <i class="fas fa-file-csv mr-1"></i>Descargar CSV</a>
        </form>
    </div>
</div>

<% if (filasFacturasAnuladas != null && !filasFacturasAnuladas.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Factura</th><th>Timbrado</th><th>Fecha</th><th>Cliente</th><th>Documento</th><th class="cc-monto">Total</th>
            <th>Nota de anulación</th><th>Fecha de anulación</th><th>Motivo</th><th>Quién anuló</th></tr></thead>
        <tbody>
        <% for (FilaFacturaAnulada filaFacturaAnulada : filasFacturasAnuladas) { %>
        <tr>
            <td><%= filaFacturaAnulada.getNumeroFactura() %> <span class="badge badge-secondary">ANULADA</span></td>
            <td><%= filaFacturaAnulada.getTimbrado() %></td>
            <td><%= filaFacturaAnulada.getFecha() %></td>
            <td><%= filaFacturaAnulada.getClienteNombre() %></td>
            <td><%= filaFacturaAnulada.getClienteDocumento() %></td>
            <td class="cc-monto"><%= filaFacturaAnulada.getTotal() %></td>
            <td><%= filaFacturaAnulada.getNumeroNotaAnulacion() == null ? "-" : filaFacturaAnulada.getNumeroNotaAnulacion() %></td>
            <td><%= filaFacturaAnulada.getFechaAnulacion() %></td>
            <td><%= filaFacturaAnulada.getMotivo() %></td>
            <td><%= filaFacturaAnulada.getAnuladoPorNombre() %></td>
        </tr>
        <% } %>
        </tbody>
        <tfoot>
        <tr class="font-weight-bold">
            <%-- B210, tercera vuelta: el importe se partía en dos renglones. Son diez columnas
                 en una table-sm, así que la celda queda angosta: "text-nowrap" la mantiene en una
                 línea (la misma clase que ya usan parametros/administracion.jsp e historial.jsp
                 para lo mismo). El rótulo sí puede partirse, no molesta. --%>
            <td colspan="5">Total: <%= totalesFacturasAnuladas.getCantidad() %> facturas anuladas</td>
            <td class="cc-monto cc-sin-partir"><%= totalesFacturasAnuladas.getMontoTotal() %></td>
            <td colspan="4"></td>
        </tr>
        </tfoot>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<div class="alert alert-secondary mt-3">No hay facturas anuladas en ese mes.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
