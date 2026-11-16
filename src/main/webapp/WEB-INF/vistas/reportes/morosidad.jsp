<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaMora" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaMora> filasMorosidad = (List<FilaMora>) request.getAttribute("filas");
    @SuppressWarnings("unchecked")
    List<SubtotalTramoMorosidad> subtotalesPorTramoMorosidad = (List<SubtotalTramoMorosidad>) request.getAttribute("subtotalesPorTramo");
    SubtotalTramoMorosidad totalGeneralMorosidad = (SubtotalTramoMorosidad) request.getAttribute("totalGeneral");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Morosidad", (Integer) request.getAttribute("totalFilas"), "préstamo en mora", "préstamos en mora") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a> —
    foto del momento, a <%= FormateadorFechas.comoDdMmAaaa(java.time.LocalDate.now()) %> (sin filtro de fecha).</p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>
<p>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
    <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasMorosidad != null && !filasMorosidad.isEmpty())); %>
    <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasMorosidad != null && !filasMorosidad.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasMorosidad != null && !filasMorosidad.isEmpty()) %> target="_blank"
       href="${pageContext.request.contextPath}/reportes/morosidad/informe"><i class="fas fa-print"></i> Informe PDF</a>
    <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasMorosidad != null && !filasMorosidad.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasMorosidad != null && !filasMorosidad.isEmpty()) %>
       href="${pageContext.request.contextPath}/reportes/morosidad/exportar"><i class="fas fa-file-excel"></i> Excel/CSV</a>
</p>

<% if (filasMorosidad != null && !filasMorosidad.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead><tr><th>Operación</th><th>Cliente</th><th>Teléfono</th><th>Días atraso</th>
            <th>Tramo</th><th>Cuotas venc.</th><th class="cc-monto">Monto exigible</th></tr></thead>
        <tbody>
        <% for (FilaMora filaMorosidad : filasMorosidad) { %>
        <tr>
            <td><a href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaMorosidad.getIdPrestamo() %>"><%= filaMorosidad.getIdentificadorOperacion() %></a></td>
            <td><%= filaMorosidad.getClienteNombre() %></td>
            <td><%= filaMorosidad.getClienteTelefono() %></td>
            <td><%= filaMorosidad.getDiasAtraso() %></td>
            <td><%= filaMorosidad.getTramo() %></td>
            <td><%= filaMorosidad.getCuotasVencidas() %></td>
            <td class="cc-monto"><%= filaMorosidad.getMontoExigible() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (subtotalesPorTramoMorosidad != null) { %>
        <%-- B212 (15/11/2026, ítem 8 del informe de UX de Informes): el capital vencido y el saldo
             estaban apilados con <br> en una sola celda, debajo de la columna "Monto exigible".
             Acá hay algo peor que en Vencimientos y vale decirlo: **ninguno de los dos es el total
             de esa columna** -- son otras dos medidas del tramo (lo vencido y lo que queda del
             préstamo), y el DTO no trae un total de lo exigible. Así que no se inventó uno: cada
             número va en su propio renglón, con su rótulo, un número por celda (el criterio del
             tfoot de B195). --%>
        <tfoot>
        <% for (SubtotalTramoMorosidad subtotal : subtotalesPorTramoMorosidad) { %>
        <%-- B212, segunda vuelta (15/11/2026, detalle que Joel encontró al probar): la
             cantidad caía bajo una columna de NUMEROS DE OTRA COSA y se leía como uno de
             ellos (acá la cantidad es de PRÉSTAMOS del tramo y
             caía bajo "Cuotas venc."). Una cantidad no totaliza ninguna
             columna: va en el rótulo, con su unidad, y el formato lo decide
             ServicioInformes.tituloConCantidad (el mismo de los títulos de B210). --%>
        <tr>
            <td colspan="6" class="text-right"><%= ServicioInformes.tituloConCantidad(
                    "Subtotal tramo " + subtotal.getTramo(), Integer.valueOf(subtotal.getCantidad()),
                    "préstamo", "préstamos") %> · capital vencido</td>
            <%-- B210 puso acá el "text-nowrap" de Bootstrap; B160 (16/11/2026) lo reemplaza por
                 cc-monto + cc-sin-partir, que es la forma única del proyecto. --%>
            <td class="cc-monto cc-sin-partir"><%= subtotal.getCapitalVencido() %></td>
        </tr>
        <tr class="small text-muted">
            <td colspan="6" class="text-right">Subtotal tramo <%= subtotal.getTramo() %> · saldo</td>
            <td class="cc-monto cc-sin-partir"><%= subtotal.getSaldo() %></td>
        </tr>
        <% } %>
        <tr class="font-weight-bold">
            <td colspan="6" class="text-right"><%= ServicioInformes.tituloConCantidad("Total general",
                    Integer.valueOf(totalGeneralMorosidad.getCantidad()), "préstamo", "préstamos") %> · capital vencido</td>
            <td class="cc-monto cc-sin-partir"><%= totalGeneralMorosidad.getCapitalVencido() %></td>
        </tr>
        <tr class="font-weight-bold small">
            <td colspan="6" class="text-right">Total general · saldo</td>
            <td class="cc-monto cc-sin-partir"><%= totalGeneralMorosidad.getSaldo() %></td>
        </tr>
        </tfoot>
        <% } %>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } else { %>
<div class="alert alert-success">No hay préstamos en mora en este momento.</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
