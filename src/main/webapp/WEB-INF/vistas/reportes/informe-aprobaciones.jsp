<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    FiltroAprobacionesReporte filtroAprobacionesReporte = (FiltroAprobacionesReporte) request.getAttribute("filtro");
    @SuppressWarnings("unchecked")
    List<FilaInformeAprobacion> filasAprobacionesReporte = (List<FilaInformeAprobacion>) request.getAttribute("filas");
    TotalesAprobaciones totalesAprobacionesReporte = (TotalesAprobaciones) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Aprobaciones y rechazos", (Integer) request.getAttribute("totalFilas"), "decisión", "decisiones") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 2: mismo patrón que Préstamos/Caja -- columnas iguales,
     Limpiar+Buscar a la derecha (no tenía Limpiar, se agrega como enlace simple sin
     parámetros), PDF/Excel a la barra de resultados (form="formularioFiltrosAprobacionesReporte",
     mismo formaction/formtarget de siempre). Sin contador: no hay un solo campo de "cantidad
     total" en TotalesAprobaciones (solo aprobadas/rechazadas por separado) -- no se inventa
     ninguno nuevo. Sin buscador de texto en el servidor: se agrega "Buscar en la tabla..."
     (recursos/js/buscar-en-tabla.js, compartido). --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/reportes/aprobaciones" id="formularioFiltrosAprobacionesReporte">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde (fecha de decisión)</label>
                    <input type="date" name="desde" class="form-control" value="<%= filtroAprobacionesReporte.getDesde() %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta (fecha de decisión)</label>
                    <input type="date" name="hasta" class="form-control" value="<%= filtroAprobacionesReporte.getHasta() %>">
                </div>
                <%-- 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o código
                     de la solicitud. --%>
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">Cliente u operación</label>
                    <input type="text" name="clienteUOperacion" class="form-control"
                           placeholder="Nombre, C.I. o código de solicitud" value="<%= filtroAprobacionesReporte.getClienteUOperacion() == null ? "" : filtroAprobacionesReporte.getClienteUOperacion() %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/aprobaciones" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
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

<div class="d-flex justify-content-end align-items-center flex-wrap listado-vivo-barra-resultados mt-3">
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaAprobacionesReporte">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaAprobacionesReporte"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <button type="submit" form="formularioFiltrosAprobacionesReporte" formaction="${pageContext.request.contextPath}/reportes/aprobaciones/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosAprobacionesReporte" formaction="${pageContext.request.contextPath}/reportes/aprobaciones/exportar"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaAprobacionesReporte">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<div class="card">
    <div class="card-body">
        <% if (filasAprobacionesReporte != null && !filasAprobacionesReporte.isEmpty()) { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped" id="tablaAprobacionesReporte">
                <thead>
                <tr><th>Número</th><th>Cliente</th><th class="cc-monto">Monto solicitado</th><th class="cc-monto">Monto aprobado</th>
                    <th>Decisión</th><th>Decidido por</th><th>Fecha de envío</th><th>Fecha de decisión</th>
                    <th>Tiempo de respuesta</th><th>Motivo</th></tr>
                </thead>
                <tbody>
                <% for (FilaInformeAprobacion filaAprobacionesReporte : filasAprobacionesReporte) { %>
                <tr>
                    <td><%= filaAprobacionesReporte.getNumero() %></td>
                    <td><%= filaAprobacionesReporte.getClienteNombre() %></td>
                    <td class="cc-monto"><%= filaAprobacionesReporte.getMontoSolicitadoFormateado() %></td>
                    <td class="cc-monto"><%= filaAprobacionesReporte.getMontoAprobadoFormateado() %></td>
                    <td><%= filaAprobacionesReporte.getDecision() %></td>
                    <td><%= filaAprobacionesReporte.getDecididoPorNombre() %></td>
                    <td><%= filaAprobacionesReporte.getFechaEnvio() %></td>
                    <td><%= filaAprobacionesReporte.getFechaDecision() %></td>
                    <td><%= filaAprobacionesReporte.getTiempoRespuestaFormateado() %></td>
                    <td><%= filaAprobacionesReporte.getMotivo() %></td>
                </tr>
                <% } %>
                <tr class="buscar-en-tabla-sin-coincidencias d-none">
                    <td colspan="10" class="text-center text-muted">Sin coincidencias.</td>
                </tr>
                </tbody>
                <% if (totalesAprobacionesReporte != null) { %>
                <tfoot>
                <tr class="font-weight-bold">
                    <%-- RF07 (pedido de Joel, 10/10/2026): tercera categoría (Canceladas) -- se
                         combina cantidad+monto en una sola celda por categoría para no pasar las
                         10 columnas del encabezado (3+3+2+2 = 10). --%>
                    <td colspan="3">Aprobadas en el período: <%= totalesAprobacionesReporte.getCantidadAprobadas() %>
                        (<%= totalesAprobacionesReporte.getMontoAprobadas() %>)</td>
                    <td colspan="3">Rechazadas en el período: <%= totalesAprobacionesReporte.getCantidadRechazadas() %>
                        (<%= totalesAprobacionesReporte.getMontoRechazadas() %>)</td>
                    <td colspan="2">Canceladas en el período: <%= totalesAprobacionesReporte.getCantidadCanceladas() %>
                        (<%= totalesAprobacionesReporte.getMontoCanceladas() %>)</td>
                    <td colspan="2">Tiempo promedio: <%= totalesAprobacionesReporte.getTiempoPromedioRespuesta() %></td>
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
        <div class="alert alert-info mt-3">No hay solicitudes decididas para el período elegido.</div>
        <% } %>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
