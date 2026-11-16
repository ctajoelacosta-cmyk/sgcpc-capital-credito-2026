<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    FiltroSolicitudesReporte filtroSolicitudesReporte = (FiltroSolicitudesReporte) request.getAttribute("filtro");
    @SuppressWarnings("unchecked")
    List<FilaInformeSolicitud> filasSolicitudesReporte = (List<FilaInformeSolicitud>) request.getAttribute("filas");
    TotalesSolicitudesReporte totalesSolicitudesReporte = (TotalesSolicitudesReporte) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<ResumenEstadoSolicitud> resumenPorEstadoSolicitudesReporte =
            (List<ResumenEstadoSolicitud>) request.getAttribute("resumenPorEstado");
    @SuppressWarnings("unchecked")
    List<Usuario> oficialesSolicitudesReporte = (List<Usuario>) request.getAttribute("oficiales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Solicitudes", (Integer) request.getAttribute("totalFilas"), "solicitud", "solicitudes") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 2: mismo patrón que Préstamos/Caja. "Resumen por estado" queda
     igual que en Préstamos -- mismo lugar, solo con el estilo general. --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/reportes/solicitudes" id="formularioFiltrosSolicitudesReporte">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="<%= filtroSolicitudesReporte.getDesde() %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="<%= filtroSolicitudesReporte.getHasta() %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Estado</label>
                    <select name="estado" class="form-control">
                        <option value="">Todos</option>
                        <% for (EstadoSolicitud estadoSolicitudesReporte : EstadoSolicitud.values()) { %>
                        <option value="<%= estadoSolicitudesReporte.name() %>"
                                <%= estadoSolicitudesReporte.name().equals(filtroSolicitudesReporte.getEstado()) ? "selected" : "" %>>
                            <%= estadoSolicitudesReporte.getEtiqueta() %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Oficial</label>
                    <select name="idOficial" class="form-control">
                        <option value="">Todos</option>
                        <% if (oficialesSolicitudesReporte != null) {
                            for (Usuario oficialSolicitudesReporte : oficialesSolicitudesReporte) { %>
                        <option value="<%= oficialSolicitudesReporte.getIdUsuario() %>"
                                <%= oficialSolicitudesReporte.getIdUsuario().equals(filtroSolicitudesReporte.getIdOficial()) ? "selected" : "" %>>
                            <%= oficialSolicitudesReporte.getApellidos() %> <%= oficialSolicitudesReporte.getNombres() %></option>
                        <% } } %>
                    </select>
                </div>
            </div>
            <%-- 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o código de
                 la solicitud. --%>
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">Cliente u operación</label>
                    <input type="text" name="clienteUOperacion" class="form-control"
                           placeholder="Nombre, C.I. o código de solicitud" value="<%= filtroSolicitudesReporte.getClienteUOperacion() == null ? "" : filtroSolicitudesReporte.getClienteUOperacion() %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/solicitudes" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
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
        <% if (totalesSolicitudesReporte != null) { %>
        <%= totalesSolicitudesReporte.getCantidad() %> solicitud<%= totalesSolicitudesReporte.getCantidad() == 1 ? "" : "es" %>
        encontrada<%= totalesSolicitudesReporte.getCantidad() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaSolicitudesReporte">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaSolicitudesReporte"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <button type="submit" form="formularioFiltrosSolicitudesReporte" formaction="${pageContext.request.contextPath}/reportes/solicitudes/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosSolicitudesReporte" formaction="${pageContext.request.contextPath}/reportes/solicitudes/exportar"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaSolicitudesReporte">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<div class="card">
    <div class="card-body">
        <% if (filasSolicitudesReporte != null && !filasSolicitudesReporte.isEmpty()) { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped" id="tablaSolicitudesReporte">
                <thead>
                <tr><th>Número</th><th>Fecha</th><th>Cliente</th><th>C.I.</th><th class="cc-monto">Monto solicitado</th>
                    <th>Plazo</th><th>Estado</th><th>Oficial</th></tr>
                </thead>
                <tbody>
                <% for (FilaInformeSolicitud filaSolicitudesReporte : filasSolicitudesReporte) { %>
                <tr style="cursor: pointer;"
                    onclick="location.href='${pageContext.request.contextPath}/aprobaciones/<%= filaSolicitudesReporte.getIdSolicitud() %>'">
                    <td><a href="${pageContext.request.contextPath}/aprobaciones/<%= filaSolicitudesReporte.getIdSolicitud() %>">
                        <%= filaSolicitudesReporte.getNumero() %></a></td>
                    <td><%= filaSolicitudesReporte.getFecha() %></td>
                    <td><%= filaSolicitudesReporte.getClienteNombre() %></td>
                    <td><%= filaSolicitudesReporte.getClienteCedula() %></td>
                    <td class="cc-monto"><%= filaSolicitudesReporte.getMontoSolicitadoFormateado() %></td>
                    <td><%= filaSolicitudesReporte.getPlazoMeses() %> meses</td>
                    <td><%= filaSolicitudesReporte.getEstado() %></td>
                    <td><%= filaSolicitudesReporte.getOficialNombre() %></td>
                </tr>
                <% } %>
                <tr class="buscar-en-tabla-sin-coincidencias d-none">
                    <td colspan="8" class="text-center text-muted">Sin coincidencias.</td>
                </tr>
                </tbody>
                <% if (totalesSolicitudesReporte != null) { %>
                <tfoot>
                <tr class="font-weight-bold">
                    <td colspan="4">Total del período (<%= totalesSolicitudesReporte.getCantidad() %> solicitud<%= totalesSolicitudesReporte.getCantidad() == 1 ? "" : "es" %>)</td>
                    <td class="cc-monto cc-sin-partir"><%= totalesSolicitudesReporte.getMontoTotal() %></td>
                    <td colspan="3"></td>
                </tr>
                </tfoot>
                <% } %>
            </table>
        </div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>

        <% if (resumenPorEstadoSolicitudesReporte != null && !resumenPorEstadoSolicitudesReporte.isEmpty()) { %>
        <div class="table-responsive mt-2">
            <table class="table table-sm">
                <caption class="mt-0">Resumen por estado</caption>
                <thead><tr><th>Estado</th><th>Cantidad</th><th class="cc-monto">Monto</th></tr></thead>
                <tbody>
                <% for (ResumenEstadoSolicitud filaResumenSolicitudesReporte : resumenPorEstadoSolicitudesReporte) { %>
                <tr>
                    <td><%= filaResumenSolicitudesReporte.getEstado() %></td>
                    <td><%= filaResumenSolicitudesReporte.getCantidad() %></td>
                    <td class="cc-monto"><%= filaResumenSolicitudesReporte.getMonto() %></td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% } %>
        <% } else { %>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
        <% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
        <div class="alert alert-info mt-3">No hay solicitudes para los filtros elegidos.</div>
        <% } %>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
