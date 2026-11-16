<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaConsultaBuro> filasConsultasBuro = (List<FilaConsultaBuro>) request.getAttribute("filas");
    TotalesConsultasBuro totalesConsultasBuro = (TotalesConsultasBuro) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<String> clasificacionesDisponiblesBuro = (List<String>) request.getAttribute("clasificacionesDisponibles");
    String resultadoSeleccionadoBuro = (String) request.getAttribute("resultado");
    // "(todos)" es el valor que manda el <select> para "sin filtro" -- si no se usa este mismo
    // centinela acá, un resultado=null en la URL de Informe/CSV se interpretaría como "" (sin
    // clasificación cargada), filtrando de más sin que el usuario lo haya pedido.
    String resultadoParametroUrlBuro = resultadoSeleccionadoBuro == null ? "(todos)" : resultadoSeleccionadoBuro;
    // 13/10/2026 (pedido de Joel): "Cliente u operación", mismo criterio de encoding que "resultado".
    String clienteUOperacionBuro = (String) request.getAttribute("clienteUOperacion");
    String clienteUOperacionParametroUrlBuro = clienteUOperacionBuro == null ? "" : clienteUOperacionBuro;
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Consultas al buró", (Integer) request.getAttribute("totalFilas"), "consulta", "consultas") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a> ·
    Solo lectura: esta pantalla no cambia ninguna consulta.</p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 3: de form-inline a grilla. PDF/Excel eran (y siguen siendo)
     <a href="..."> con la URL armada con EL + el mismo java.net.URLEncoder.encode(...) de
     siempre para "resultado" -- no son botones de formulario, no necesitan form="id"; se movió
     el <a> de lugar sin tocar ni un carácter de cómo arma la URL. --%>
<div class="card">
    <div class="card-body">
        <form method="get">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <input type="date" name="desde" class="form-control" value="${desde}">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="${hasta}">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Resultado</label>
                    <select name="resultado" class="form-control">
                        <option value="(todos)" <%= resultadoSeleccionadoBuro == null ? "selected" : "" %>>(todos)</option>
                        <% if (clasificacionesDisponiblesBuro != null) {
                            for (String clasificacion : clasificacionesDisponiblesBuro) {
                                String etiqueta = clasificacion == null || clasificacion.trim().isEmpty() ? "Sin clasificación" : clasificacion; %>
                        <option value="<%= clasificacion %>" <%= clasificacion.equals(resultadoSeleccionadoBuro) ? "selected" : "" %>>
                            <%= etiqueta %></option>
                        <% } } %>
                    </select>
                </div>
            </div>
            <%-- 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o el código de
                 la solicitud (todavía no hay préstamo al consultar el buró). --%>
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">Cliente u operación</label>
                    <input type="text" name="clienteUOperacion" class="form-control"
                           placeholder="Nombre, C.I. o código de solicitud" value="${clienteUOperacion}">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/consultas-buro" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
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
        <% if (totalesConsultasBuro != null) { %>
        <%= totalesConsultasBuro.getCantidadTotal() %> consulta<%= totalesConsultasBuro.getCantidadTotal() == 1 ? "" : "s" %> encontrada<%= totalesConsultasBuro.getCantidadTotal() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaConsultasBuro">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaConsultasBuro"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasConsultasBuro != null && !filasConsultasBuro.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasConsultasBuro != null && !filasConsultasBuro.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasConsultasBuro != null && !filasConsultasBuro.isEmpty()) %> target="_blank"
           title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."
           href="${pageContext.request.contextPath}/reportes/consultas-buro/informe?desde=${desde}&hasta=${hasta}&resultado=<%= java.net.URLEncoder.encode(resultadoParametroUrlBuro, "UTF-8") %>&clienteUOperacion=<%= java.net.URLEncoder.encode(clienteUOperacionParametroUrlBuro, "UTF-8") %>"><i class="fas fa-file-pdf"></i> PDF</a>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasConsultasBuro != null && !filasConsultasBuro.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasConsultasBuro != null && !filasConsultasBuro.isEmpty()) %>
           title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."
           href="${pageContext.request.contextPath}/reportes/consultas-buro/exportar?desde=${desde}&hasta=${hasta}&resultado=<%= java.net.URLEncoder.encode(resultadoParametroUrlBuro, "UTF-8") %>&clienteUOperacion=<%= java.net.URLEncoder.encode(clienteUOperacionParametroUrlBuro, "UTF-8") %>">
            <i class="fas fa-file-excel"></i> Excel/CSV</a>
    </div>
</div>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaConsultasBuro">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<% if (filasConsultasBuro != null && !filasConsultasBuro.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped" id="tablaConsultasBuro">
        <thead><tr><th>Fecha</th><th>Cliente</th><th>C.I.</th><th>Solicitud asociada</th>
            <th>Resultado / Calificación</th><th>Quién consultó</th><th>Autorización vigente<br>en ese momento</th></tr></thead>
        <tbody>
        <% for (FilaConsultaBuro fila : filasConsultasBuro) { %>
        <tr style="cursor:pointer;" onclick="window.location='${pageContext.request.contextPath}/clientes/<%= fila.getIdPersona() %>'">
            <td><%= fila.getFecha() %></td>
            <td><%= fila.getCliente() %></td>
            <td><%= fila.getCedula() %></td>
            <td><%= fila.getSolicitud() %></td>
            <td><%= fila.getResultado() %></td>
            <td><%= fila.getQuienConsulto() %></td>
            <td><%= fila.getAutorizacionVigente() %></td>
        </tr>
        <% } %>
        <tr class="buscar-en-tabla-sin-coincidencias d-none">
            <td colspan="7" class="text-center text-muted">Sin coincidencias.</td>
        </tr>
        </tbody>
        <% if (totalesConsultasBuro != null) { %>
        <tfoot>
        <tr class="font-weight-bold">
            <td colspan="7">Total del período: <%= totalesConsultasBuro.getCantidadTotal() %> consulta<%= totalesConsultasBuro.getCantidadTotal() == 1 ? "" : "s" %>
                · Clientes distintos: <%= totalesConsultasBuro.getCantidadClientesDistintos() %> —
                <% for (Map.Entry<String, Integer> entrada : totalesConsultasBuro.getCantidadPorResultado().entrySet()) { %>
                <%= entrada.getKey() %>: <%= entrada.getValue() %> &nbsp;
                <% } %>
            </td>
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
     (alert-success) y acá vacío no es buena noticia: ninguna consulta al buró no es un logro, es que no se evaluó a nadie. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay consultas al buró en ese rango.</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
