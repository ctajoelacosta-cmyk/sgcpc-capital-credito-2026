<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaVencimiento" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesVencimientos" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaVencimiento> filasVencimientos = (List<FilaVencimiento>) request.getAttribute("filas");
    TotalesVencimientos totalesVencimientos = (TotalesVencimientos) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Vencimientos", (Integer) request.getAttribute("totalFilas"), "cuota", "cuotas") %></h1>
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
            <button type="submit" class="btn btn-primary">Filtrar</button>
        </form>
        <p class="mt-3 mb-0">
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
            <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasVencimientos != null && !filasVencimientos.isEmpty())); %>
            <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasVencimientos != null && !filasVencimientos.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasVencimientos != null && !filasVencimientos.isEmpty()) %> target="_blank"
               href="${pageContext.request.contextPath}/reportes/vencimientos/informe?desde=${desde}&hasta=${hasta}"><i class="fas fa-print"></i> Informe PDF</a>
            <a class="btn btn-outline-secondary<%= ServicioInformes.claseBotonExportar(filasVencimientos != null && !filasVencimientos.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasVencimientos != null && !filasVencimientos.isEmpty()) %>
               href="${pageContext.request.contextPath}/reportes/vencimientos/exportar?desde=${desde}&hasta=${hasta}"><i class="fas fa-file-excel"></i> Excel/CSV</a>
        </p>
    </div>
</div>

<%-- B207 (15/11/2026, ítem 5): el aviso de rango invertido, en los trece reportes con
     fechas. No recibe nada: el fragmento lee los parámetros del pedido y el texto lo decide
     ServicioReportes.avisoRangoInvertido (un dato, un dueño). --%>
<%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

<% if (filasVencimientos != null && !filasVencimientos.isEmpty()) { %>
<div class="table-responsive mt-3">
    <table class="table table-sm table-striped">
        <thead><tr><th>Operación</th><th>Cliente</th><th>Teléfono</th><th>Cuota</th><th>Vencimiento</th><th class="cc-monto">Monto</th><th>Estado</th></tr></thead>
        <tbody>
        <% for (FilaVencimiento filaVencimiento : filasVencimientos) { %>
        <tr>
            <td><%= filaVencimiento.getIdentificadorOperacion() %></td>
            <td><%= filaVencimiento.getClienteNombre() %></td>
            <td><%= filaVencimiento.getClienteTelefono() %></td>
            <td><%= filaVencimiento.getNumeroCuota() %></td>
            <td><%= filaVencimiento.getFechaVencimiento() %></td>
            <td class="cc-monto"><%= filaVencimiento.getMonto() %></td>
            <td><%= filaVencimiento.getEstado() %></td>
        </tr>
        <% } %>
        </tbody>
        <% if (totalesVencimientos != null) { %>
        <%-- B212 (15/11/2026, ítem 8 del informe de UX de Informes): los cuatro importes estaban
             apilados con <br> en UNA celda, debajo de una columna que en el cuerpo muestra un
             único número -- así que el total no quedaba debajo de lo que totaliza. Ahora, como en
             el tfoot de B195: la celda de "Monto" lleva el total DE ESA COLUMNA, y el desglose
             (capital, interés, IVA) va en su propio renglón, con un número por celda. --%>
        <tfoot>
        <%-- B212, segunda vuelta (15/11/2026, detalle que Joel encontró al probar): la
             cantidad caía bajo una columna de NUMEROS DE OTRA COSA y se leía como uno de
             ellos (el "205" parecía un número de cuota). Una cantidad no totaliza ninguna
             columna: va en el rótulo, con su unidad, y el formato lo decide
             ServicioInformes.tituloConCantidad (el mismo de los títulos de B210). --%>
        <tr class="font-weight-bold">
            <td colspan="5"><%= ServicioInformes.tituloConCantidad("Total",
                    Integer.valueOf(totalesVencimientos.getCantidadCuotas()), "cuota", "cuotas") %></td>
            <%-- B210 puso acá el "text-nowrap" de Bootstrap; B160 (16/11/2026) lo reemplaza por
                 cc-monto + cc-sin-partir, que es la forma única del proyecto. --%>
            <td class="cc-monto cc-sin-partir"><%= totalesVencimientos.getMontoTotal() %></td>
            <td></td>
        </tr>
        <tr class="small text-muted">
            <td colspan="5">Del total: capital</td>
            <td class="cc-monto cc-sin-partir"><%= totalesVencimientos.getCapitalTotal() %></td>
            <td></td>
        </tr>
        <tr class="small text-muted">
            <td colspan="5">Interés</td>
            <td class="cc-monto cc-sin-partir"><%= totalesVencimientos.getInteresTotal() %></td>
            <td></td>
        </tr>
        <tr class="small text-muted">
            <td colspan="5">IVA</td>
            <td class="cc-monto cc-sin-partir"><%= totalesVencimientos.getIvaTotal() %></td>
            <td></td>
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
     (alert-success) y acá vacío no es buena noticia: ninguna cuota por vencer significa que no entra plata. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay cuotas que venzan en ese rango.</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
