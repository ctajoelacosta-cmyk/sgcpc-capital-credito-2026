<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaInformePrestamo" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroPrestamos" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesPrestamos" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    FiltroPrestamos filtroPrestamos = (FiltroPrestamos) request.getAttribute("filtro");
    @SuppressWarnings("unchecked")
    List<FilaInformePrestamo> filasPrestamos = (List<FilaInformePrestamo>) request.getAttribute("filas");
    TotalesPrestamos totalesPrestamos = (TotalesPrestamos) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<ResumenEstadoPrestamo> resumenPorEstadoPrestamos =
            (List<ResumenEstadoPrestamo>) request.getAttribute("resumenPorEstado");
%>
<h1>Préstamos</h1>

<%-- V11 (pedido de Joel), lote 1: mismo patrón que el piloto de Clientes -- 4 columnas iguales,
     Limpiar+Buscar a la derecha en una línea, y PDF/Excel movidos a la barra de resultados
     (fuera del <form>, asociados con form="formularioFiltrosPrestamos" -- mismo
     formaction/formtarget de siempre, solo cambia de dónde cuelgan en el HTML). Esta pantalla no
     usa listado-vivo.js (siempre fue de página completa, sin AJAX) -- "Limpiar" sigue siendo un
     enlace simple a /prestamos sin parámetros, ya lo era antes. --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/prestamos" id="formularioFiltrosPrestamos">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Cliente, C.I. u operación</label>
                    <input type="text" name="texto" class="form-control" placeholder="Cliente, C.I. u operación"
                           value="<%= filtroPrestamos != null && filtroPrestamos.getTexto() != null ? filtroPrestamos.getTexto() : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Estado</label>
                    <select name="estado" class="form-control">
                        <option value="">Todos</option>
                        <% String estadoActualPrestamos = filtroPrestamos == null ? null : filtroPrestamos.getEstado();
                           for (EstadoPrestamo estadoPrestamos : EstadoPrestamo.values()) { %>
                        <option value="<%= estadoPrestamos.name() %>"
                                <%= estadoPrestamos.name().equals(estadoActualPrestamos) ? "selected" : "" %>>
                            <%= estadoPrestamos.getEtiqueta() %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desembolso desde</label>
                    <input type="date" name="desembolsoDesde" class="form-control"
                           value="<%= filtroPrestamos != null && filtroPrestamos.getDesembolsoDesde() != null ? filtroPrestamos.getDesembolsoDesde() : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desembolso hasta</label>
                    <input type="date" name="desembolsoHasta" class="form-control"
                           value="<%= filtroPrestamos != null && filtroPrestamos.getDesembolsoHasta() != null ? filtroPrestamos.getDesembolsoHasta() : "" %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/prestamos" class="btn btn-outline-secondary">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>
    </div>
</div>

<%-- Barra de resultados: PDF/Excel quedan siempre visibles (igual que antes, nunca dependieron
     de si hay resultados); el contador reusa totalesPrestamos.getCantidad(), que ya existe (se
     mostraba en el pie de la tabla, "Total (N préstamos)") -- no se agregó ningún cálculo nuevo
     del lado del servidor. --%>
<div class="d-flex justify-content-between align-items-center flex-wrap listado-vivo-barra-resultados mt-3">
    <p class="text-muted mb-2">
        <% if (totalesPrestamos != null) { %>
        <%= totalesPrestamos.getCantidad() %> préstamo<%= totalesPrestamos.getCantidad() == 1 ? "" : "s" %>
        encontrado<%= totalesPrestamos.getCantidad() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="mb-2">
        <button type="submit" form="formularioFiltrosPrestamos" formaction="${pageContext.request.contextPath}/prestamos/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm"><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosPrestamos" formaction="${pageContext.request.contextPath}/prestamos/exportar"
                class="btn btn-outline-secondary btn-sm"><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>

<div class="card">
    <div class="card-body">
        <%-- Los préstamos sin desembolsar (PENDIENTE_DESEMBOLSO) no tienen fecha_desembolso; el
             DAO (PrestamoDAO.listarConFiltros) ya decide cuándo entran igual (sin filtro de
             fechas, o con el estado PENDIENTE_DESEMBOLSO elegido) -- acá solo se muestran. --%>
        <% if (filasPrestamos != null && !filasPrestamos.isEmpty()) { %>
        <%-- B241 (16/11/2026, pedido de Joel): el resumen por estado va ARRIBA de la tabla. Con la
             lista paginada, abajo quedaba despues de la pagina 1 de 18 y habia que buscarlo; y es
             lo primero que se mira para saber como esta la cartera, antes de recorrer prestamo por
             prestamo. Sus numeros son de TODO lo filtrado, no de la pagina: el controlador los
             calcula sobre la lista completa antes de cortar. --%>
        <% if (resumenPorEstadoPrestamos != null && !resumenPorEstadoPrestamos.isEmpty()) { %>
        <div class="table-responsive mb-3">
            <table class="table table-sm">
                <caption class="mt-0">Resumen por estado (de todo lo filtrado)</caption>
                <thead><tr><th>Estado</th><th>Cantidad</th><th class="cc-monto">Monto</th><th class="cc-monto">Saldo</th></tr></thead>
                <tbody>
                <% for (ResumenEstadoPrestamo filaResumenPrestamos : resumenPorEstadoPrestamos) { %>
                <tr>
                    <td><%= filaResumenPrestamos.getEstado() %></td>
                    <td><%= filaResumenPrestamos.getCantidad() %></td>
                    <td class="cc-monto"><%= filaResumenPrestamos.getMonto() %></td>
                    <td class="cc-monto"><%= filaResumenPrestamos.getSaldo() %></td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% } %>
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead>
                <tr>
                    <th>Operación</th><th>Cliente</th><th>C.I.</th><th class="cc-monto">Monto</th><th class="cc-sin-partir">Plazo</th>
                    <th>Fecha de desembolso</th><th class="cc-celda-estado">Estado</th><th class="cc-monto">Saldo</th><th>Cuotas pagadas/total</th>
                </tr>
                </thead>
                <tbody>
                <% for (FilaInformePrestamo filaPrestamos : filasPrestamos) { %>
                <%-- Clic en la fila abre el estado de cuenta (ya existe, de solo lectura) -- no
                     hace falta una ficha propia de /prestamos. --%>
                <tr style="cursor: pointer;"
                    onclick="location.href='${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaPrestamos.getIdPrestamo() %>'">
                    <td class="celda-codigo"><a href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaPrestamos.getIdPrestamo() %>">
                        <%= filaPrestamos.getIdentificadorOperacion() %></a></td>
                    <td><%= filaPrestamos.getClienteNombre() %></td>
                    <td><%= filaPrestamos.getClienteCedula() %></td>
                    <td class="cc-monto"><%= filaPrestamos.getMontoAprobadoFormateado() %></td>
                    <td class="cc-sin-partir"><%= filaPrestamos.getPlazoMeses() %> meses</td>
                    <td><%= filaPrestamos.getFechaDesembolso() %></td>
                    <td class="cc-celda-estado"><%= filaPrestamos.getEstado() %></td>
                    <td class="cc-monto"><%= filaPrestamos.getSaldoFormateado() %></td>
                    <td><%= filaPrestamos.getCuotasTexto() %></td>
                </tr>
                <% } %>
                </tbody>
                <% if (totalesPrestamos != null) { %>
                <tfoot>
                <tr class="font-weight-bold">
                    <td colspan="3">Total del período (<%= totalesPrestamos.getCantidad() %> préstamo<%= totalesPrestamos.getCantidad() == 1 ? "" : "s" %>)</td>
                    <%-- B241 (hallazgo de Joel): el "Gs." se bajaba solo a un segundo renglón
                         ("818.100.000" arriba y "Gs." abajo). cc-sin-partir es la clase que ya usan
                         las otras pantallas para esto (§13 de sgcpc-tema.css); el importe es una
                         sola unidad y cortado se lee como si fuera otro número. --%>
                    <td class="cc-monto cc-sin-partir"><%= totalesPrestamos.getMontoTotal() %></td>
                    <td></td><td></td><td></td>
                    <td class="cc-monto cc-sin-partir"><%= totalesPrestamos.getSaldoTotal() %></td>
                    <td></td>
                </tr>
                </tfoot>
                <% } %>
            </table>
        </div>
        <%-- B241: LA paginación del sistema, la misma pieza de B167 que ya usan Clientes,
             Solicitudes, Aprobaciones, Mora, Formalización y los reportes. El controlador deja
             "pagina", "totalPaginas" y "paginacionUrlBase" --esa URL lleva los filtros y el orden
             actuales, así que cambiar de página no pierde nada-- y este fragmento solo dibuja.
             Los enlaces navegan de verdad (paginacionConListadoVivo en FALSE, que es lo que deja
             PaginacionPantalla.paginar): esta pantalla no tiene búsqueda en vivo, así que
             listado-vivo.js no la intercepta. --%>
        <%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>

        <% } else { %>
        <div class="alert alert-info mt-3">No hay préstamos para los filtros elegidos.</div>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
