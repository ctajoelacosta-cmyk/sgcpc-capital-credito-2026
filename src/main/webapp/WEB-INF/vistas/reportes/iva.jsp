<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaIva" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesIva" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaIva> filasIva = (List<FilaIva>) request.getAttribute("filas");
    java.time.YearMonth mesIva = (java.time.YearMonth) request.getAttribute("mes");
    TotalesIva totalesIva = (TotalesIva) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("IVA del mes", (Integer) request.getAttribute("totalFilas"), "factura", "facturas") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p class="text-muted">Incluye las facturas anuladas del mes (marcadas, numeración completa para la DNIT) -- no suman en los totales.</p>

<div class="card">
    <div class="card-body">
        <form method="get" class="form-inline">
            <label class="mr-2">Mes</label>
            <input type="month" name="mes" class="form-control mr-3" value="<%= mesIva %>">
            <button type="submit" class="btn btn-primary mr-3">Ver</button>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasIva != null && !filasIva.isEmpty())); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary mr-2<%= ServicioInformes.claseBotonExportar(filasIva != null && !filasIva.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasIva != null && !filasIva.isEmpty()) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/iva/informe?mes=<%= mesIva %>"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasIva != null && !filasIva.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasIva != null && !filasIva.isEmpty()) %> href="${pageContext.request.contextPath}/reportes/iva/csv?mes=<%= mesIva %>">
                <i class="fas fa-file-csv mr-1"></i>Descargar CSV</a>
        </form>
    </div>
</div>

<% if (filasIva != null && !filasIva.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Factura</th><th>Fecha</th><th>Cliente</th><th>Documento</th>
            <th class="cc-monto">Exenta</th><th class="cc-monto">Gravada 5%</th><th class="cc-monto">IVA 5%</th><th class="cc-monto">Gravada 10%</th><th class="cc-monto">IVA 10%</th><th class="cc-monto">Total</th></tr></thead>
        <tbody>
        <% for (FilaIva filaIva : filasIva) {
            String estiloFilaIva = filaIva.isAnulada() ? "text-muted" : "";
        %>
        <tr class="<%= estiloFilaIva %>">
            <td><%= filaIva.getNumeroFactura() %>
                <% if (filaIva.isAnulada()) { %><span class="badge badge-secondary">ANULADA</span><% } %></td>
            <td><%= filaIva.getFecha() %></td>
            <td><%= filaIva.getClienteNombre() %></td>
            <td><%= filaIva.getClienteDocumento() %></td>
            <td class="cc-monto"><%= filaIva.getTotalExenta() %></td>
            <td class="cc-monto cc-sin-partir">0 Gs.</td>
            <td class="cc-monto cc-sin-partir">0 Gs.</td>
            <td class="cc-monto"><%= filaIva.getTotalGravada10() %></td>
            <td class="cc-monto"><%= filaIva.getIva10() %></td>
            <td class="cc-monto"><%= filaIva.getTotal() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (totalesIva != null) { %>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="3">Total (<%= totalesIva.getCantidadFacturas() %> facturas, sin contar anuladas)</td>
            <td></td>
            <td class="cc-monto cc-sin-partir"><%= totalesIva.getExenta() %></td>
            <td class="cc-monto cc-sin-partir">0 Gs.</td>
            <td class="cc-monto cc-sin-partir">0 Gs.</td>
            <td class="cc-monto cc-sin-partir"><%= totalesIva.getGravada10() %></td>
            <td class="cc-monto cc-sin-partir"><%= totalesIva.getIva10() %></td>
            <td class="cc-monto cc-sin-partir"><%= totalesIva.getTotal() %></td>
        </tr>
        </tfoot>
        <% } %>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<div class="alert alert-secondary mt-3">No hay facturas en ese mes.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
