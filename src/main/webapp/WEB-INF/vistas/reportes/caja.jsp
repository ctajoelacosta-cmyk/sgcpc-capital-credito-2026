<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaInformeCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    FiltroCajaReporte filtroCajaReporte = (FiltroCajaReporte) request.getAttribute("filtro");
    @SuppressWarnings("unchecked")
    List<FilaInformeCaja> filasCajaReporte = (List<FilaInformeCaja>) request.getAttribute("filas");
    TotalesCaja totalesCajaReporte = (TotalesCaja) request.getAttribute("totales");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Caja", (Integer) request.getAttribute("totalFilas"), "sesión", "sesiones") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 1: mismo patrón que Clientes/Préstamos -- columnas iguales,
     Limpiar+Buscar a la derecha, PDF/Excel movidos a la barra de resultados (fuera del <form>,
     con form="formularioFiltrosCajaReporte" -- mismo formaction/formtarget de siempre). Esta
     pantalla no tenía Limpiar: se agrega como enlace simple a /reportes/caja sin parámetros (el
     servlet ya resuelve el rango por defecto solo, no se tocó nada de eso). Punto 4: como esta
     pantalla no tiene buscador de texto propio, se agrega un campo que filtra las filas YA
     cargadas en el navegador (puro JS, ver el <script> al final) -- no es un filtro nuevo del
     servidor, no cambia la consulta ni los totales -- recursos/js/buscar-en-tabla.js (compartido,
     registrado en pie.jsp), convención data-buscar-en-tabla="idDeLaTabla" en el campo. --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/reportes/caja" id="formularioFiltrosCajaReporte">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="<%= filtroCajaReporte.getDesde() %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="<%= filtroCajaReporte.getHasta() %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/caja" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
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
        <% if (totalesCajaReporte != null) { %>
        <%= totalesCajaReporte.getCantidadSesiones() %> sesion<%= totalesCajaReporte.getCantidadSesiones() == 1 ? "" : "es" %>
        encontrada<%= totalesCajaReporte.getCantidadSesiones() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaCajaReporte">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaCajaReporte"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasCajaReporte != null && !filasCajaReporte.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <button type="submit" form="formularioFiltrosCajaReporte" formaction="${pageContext.request.contextPath}/reportes/caja/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasCajaReporte != null && !filasCajaReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCajaReporte != null && !filasCajaReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosCajaReporte" formaction="${pageContext.request.contextPath}/reportes/caja/exportar"
                class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasCajaReporte != null && !filasCajaReporte.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasCajaReporte != null && !filasCajaReporte.isEmpty()) %>
                title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
</div>
<%-- V15 (pedido de Joel): mientras hay texto en "Buscar en la tabla...", aviso de que PDF/Excel
     exportan según los filtros del formulario, no según lo que queda visible acá -- lo
     muestra/completa recursos/js/buscar-en-tabla.js (mismos X/N que "Mostrando X de N"). --%>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaCajaReporte">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<div class="card">
    <div class="card-body">
        <% if (filasCajaReporte != null && !filasCajaReporte.isEmpty()) { %>
        <div class="table-responsive">
            <table class="table table-sm table-striped" id="tablaCajaReporte">
                <thead>
                <tr><th>Cajera</th><th>Apertura</th><th>Cierre</th><th class="cc-monto">Fondo inicial</th>
                    <th class="cc-monto">Saldo teórico</th><th class="cc-monto">Efectivo contado</th><th class="cc-monto">Diferencia</th><th>Revisada</th><th></th></tr>
                </thead>
                <tbody>
                <% for (FilaInformeCaja filaCajaReporte : filasCajaReporte) {
                    // Mismo criterio de color que A3d: sobrante en ámbar oscuro (nunca verde --
                    // acá "sobra" no es "todo bien"), faltante en rojo, 0 sin resaltar.
                    String estiloDiferenciaCaja = filaCajaReporte.getDiferencia() == null ? ""
                            : filaCajaReporte.getDiferencia().signum() > 0 ? "color:#a15c00;"
                            : filaCajaReporte.getDiferencia().signum() < 0 ? "color:#dc3545;" : "";
                %>
                <tr>
                    <td><%= filaCajaReporte.getCajeraNombre() %></td>
                    <td><%= filaCajaReporte.getApertura() %></td>
                    <td><%= filaCajaReporte.getCierre() %></td>
                    <td class="cc-monto"><%= filaCajaReporte.getFondoInicialFormateado() %></td>
                    <td class="cc-monto"><%= filaCajaReporte.getSaldoTeoricoFormateado() %></td>
                    <td class="cc-monto"><%= filaCajaReporte.getEfectivoContadoFormateado() %></td>
                    <td class="cc-monto" style="<%= estiloDiferenciaCaja %>"><%= filaCajaReporte.getDiferenciaFormateada() %></td>
                    <td><%= filaCajaReporte.isRevisada() ? "Sí" : "No" %></td>
                    <td><a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/caja/<%= filaCajaReporte.getIdSesionCaja() %>/arqueo">Ver arqueo</a></td>
                </tr>
                <% } %>
                <%-- V11, punto b (corrección de Joel): la fila de "Sin coincidencias." vive
                     ADENTRO de la tabla, como última fila del tbody -- justo arriba del
                     <tfoot>, no suelta debajo de la tabla. recursos/js/buscar-en-tabla.js la
                     muestra/oculta, nunca la crea. --%>
                <tr class="buscar-en-tabla-sin-coincidencias d-none">
                    <td colspan="9" class="text-center text-muted">Sin coincidencias.</td>
                </tr>
                </tbody>
                <% if (totalesCajaReporte != null) { %>
                <%-- B212 (15/11/2026, ítem 8): los sobrantes y los faltantes estaban apilados con
                     <br> en una sola celda. Son los dos lados de la columna "Diferencia", así que
                     cada uno va en su propio renglón, en esa columna y con su rótulo: un número por
                     celda (el criterio del tfoot de B195). No se suman en un solo total a
                     propósito -- un sobrante y un faltante no se cancelan entre sí, cada uno es un
                     problema de caja distinto (RF18/B52). --%>
                <tfoot>
                <tr class="font-weight-bold">
                    <td colspan="9">Total del período (<%= totalesCajaReporte.getCantidadSesiones() %> sesion<%= totalesCajaReporte.getCantidadSesiones() == 1 ? "" : "es" %>)</td>
                </tr>
                <tr>
                    <td colspan="6">Suma de sobrantes</td>
            <%-- B210 puso acá el "text-nowrap" de Bootstrap para que el importe no se partiera;
                 B160 (16/11/2026) lo reemplaza por cc-sin-partir, que es la clase del proyecto
                 para lo mismo (§13), así que ahora hay UNA sola forma de pedirlo. --%>
                    <td class="cc-monto cc-sin-partir"><%= totalesCajaReporte.getSumaSobrantes() %></td>
                    <td colspan="2"></td>
                </tr>
                <tr>
                    <td colspan="6">Suma de faltantes</td>
                    <td class="cc-monto cc-sin-partir"><%= totalesCajaReporte.getSumaFaltantes() %></td>
                    <td colspan="2"></td>
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
        <div class="alert alert-secondary mt-3">No hay sesiones de caja cerradas en ese rango.</div>
        <% } %>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
