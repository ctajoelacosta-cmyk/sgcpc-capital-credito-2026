<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    FiltroDesembolsosReporte filtroDesembolsosReporte = (FiltroDesembolsosReporte) request.getAttribute("filtro");
    @SuppressWarnings("unchecked")
    List<FilaInformeDesembolso> filasDesembolsosReporte = (List<FilaInformeDesembolso>) request.getAttribute("filas");
    TotalesDesembolsos totalesDesembolsosReporte = (TotalesDesembolsos) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Desembolsos", (Integer) request.getAttribute("totalFilas"), "desembolso", "desembolsos") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 2: mismo patrón que Préstamos/Caja. --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/reportes/desembolsos" id="formularioFiltrosDesembolsosReporte">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="<%= filtroDesembolsosReporte.getDesde() %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="<%= filtroDesembolsosReporte.getHasta() %>">
                </div>
                <%-- 13/10/2026 (pedido de Joel): "Cliente u operación". --%>
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">Cliente u operación</label>
                    <input type="text" name="clienteUOperacion" class="form-control"
                           placeholder="Nombre, C.I. o CC-aaaa-nnnnn" value="<%= filtroDesembolsosReporte.getClienteUOperacion() == null ? "" : filtroDesembolsosReporte.getClienteUOperacion() %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/desembolsos" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-filter"></i> Aplicar filtros</button>
                </div>
            </div>
        </form>
    </div>
</div>

<%-- B207 (15/11/2026, ítem 5): el aviso de rango invertido, en los trece reportes con
     fechas. No recibe nada: el fragmento lee los parámetros del pedido y el texto lo decide
     ServicioReportes.avisoRangoInvertido (un dato, un dueño). --%>
<%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

<div class="d-flex justify-content-between align-items-center flex-wrap listado-vivo-barra-resultados mt-3">
    <p class="text-muted mb-2">
        <% if (totalesDesembolsosReporte != null) { %>
        <%= totalesDesembolsosReporte.getCantidad() %> desembolso<%= totalesDesembolsosReporte.getCantidad() == 1 ? "" : "s" %>
        encontrado<%= totalesDesembolsosReporte.getCantidad() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaDesembolsosReporte">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaDesembolsosReporte"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <button type="submit" form="formularioFiltrosDesembolsosReporte" formaction="${pageContext.request.contextPath}/reportes/desembolsos/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosDesembolsosReporte" formaction="${pageContext.request.contextPath}/reportes/desembolsos/exportar"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaDesembolsosReporte">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<div class="card">
    <div class="card-body">
        <% if (filasDesembolsosReporte != null && !filasDesembolsosReporte.isEmpty()) { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped" id="tablaDesembolsosReporte">
                <thead>
                <tr><th>Fecha</th><th>Comprobante</th><th>Cliente</th><th>C.I.</th><th>Operación</th>
                    <th class="cc-monto">Monto</th><th>Desembolsado por</th></tr>
                </thead>
                <tbody>
                <% for (FilaInformeDesembolso filaDesembolsosReporte : filasDesembolsosReporte) { %>
                <tr style="cursor: pointer;"
                    onclick="location.href='${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaDesembolsosReporte.getIdPrestamo() %>'">
                    <td><%= filaDesembolsosReporte.getFecha() %></td>
                    <td><%= filaDesembolsosReporte.getComprobante() %></td>
                    <td><%= filaDesembolsosReporte.getClienteNombre() %></td>
                    <td><%= filaDesembolsosReporte.getClienteCedula() %></td>
                    <td><%= filaDesembolsosReporte.getOperacion() %></td>
                    <td class="cc-monto"><%= filaDesembolsosReporte.getMontoFormateado() %></td>
                    <td><%= filaDesembolsosReporte.getDesembolsadoPorNombre() %></td>
                </tr>
                <% } %>
                <tr class="buscar-en-tabla-sin-coincidencias d-none">
                    <td colspan="7" class="text-center text-muted">Sin coincidencias.</td>
                </tr>
                </tbody>
                <% if (totalesDesembolsosReporte != null) { %>
                <tfoot>
                <tr class="font-weight-bold">
                    <td colspan="5">Total del período (<%= totalesDesembolsosReporte.getCantidad() %> desembolso<%= totalesDesembolsosReporte.getCantidad() == 1 ? "" : "s" %>)</td>
                    <td class="cc-monto cc-sin-partir"><%= totalesDesembolsosReporte.getMontoTotal() %></td>
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
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
        <% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
        <div class="alert alert-info mt-3">No hay desembolsos en ese rango.</div>
        <% } %>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
