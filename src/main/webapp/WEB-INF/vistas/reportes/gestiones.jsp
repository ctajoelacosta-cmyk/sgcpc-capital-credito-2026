<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaGestionReporte" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesGestiones" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaGestionReporte> filasGestiones = (List<FilaGestionReporte>) request.getAttribute("filas");
    TotalesGestiones totalesGestiones = (TotalesGestiones) request.getAttribute("totales");
    // V13-ter (pedido de Joel): mismo atributo que ya lee el checkbox por EL (${soloIncumplidas}),
    // leído acá también para poder usarlo en un if de Java y elegir el mensaje de "sin resultados".
    Boolean soloIncumplidasGestiones = (Boolean) request.getAttribute("soloIncumplidas");
%>
<%-- B210 (15/11/2026, ítems 2+3): el contador va en el TÍTULO, como en las otras siete
     pantallas, y con el total del listado COMPLETO — no el de la página (la trampa de
     B191). El formato lo decide ServicioInformes.tituloConCantidad. --%>
<h1><%= ServicioInformes.tituloConCantidad("Gestiones de cobranza", (Integer) request.getAttribute("totalFilas"), "gestión", "gestiones") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 3: de form-inline a grilla. PDF/Excel eran (y siguen siendo)
     <a href="..."> con la URL armada con EL -- no son botones de formulario, no necesitan
     form="id". Checkbox "Solo incumplidas" alineado abajo en su propia columna
     (d-flex align-items-end empuja el .form-check al piso de la fila, a la misma altura que
     el borde inferior de los demás campos). --%>
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
                    <label class="mb-0">Tipo</label>
                    <select name="tipo" class="form-control">
                        <option value="">(todos)</option>
                        <option value="LLAMADA" ${tipo == 'LLAMADA' ? 'selected' : ''}>Llamada</option>
                        <option value="MENSAJE" ${tipo == 'MENSAJE' ? 'selected' : ''}>Mensaje</option>
                        <option value="VISITA_LOCAL" ${tipo == 'VISITA_LOCAL' ? 'selected' : ''}>Visita al local</option>
                        <option value="PAGO_PARCIAL" ${tipo == 'PAGO_PARCIAL' ? 'selected' : ''}>Pago parcial</option>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3 d-flex align-items-end">
                    <div class="form-check mb-0">
                        <input type="checkbox" class="form-check-input" name="soloIncumplidas" value="true" id="soloIncumplidas" ${soloIncumplidas ? 'checked' : ''}>
                        <label class="form-check-label" for="soloIncumplidas">Solo promesas incumplidas</label>
                    </div>
                </div>
            </div>
            <%-- 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o CC-aaaa-nnnnn,
                 el mismo filtro en pantalla, PDF y CSV (ServicioReportes.coincideClienteUOperacion). --%>
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">Cliente u operación</label>
                    <input type="text" name="clienteUOperacion" class="form-control"
                           placeholder="Nombre, C.I. o CC-aaaa-nnnnn" value="${clienteUOperacion}">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/gestiones" class="btn btn-outline-secondary" data-buscar-en-tabla-limpiar>
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
        <% if (totalesGestiones != null) { %>
        <%= totalesGestiones.getCantidadTotal() %> gestion<%= totalesGestiones.getCantidadTotal() == 1 ? "" : "es" %> encontrada<%= totalesGestiones.getCantidadTotal() == 1 ? "" : "s" %>.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <div class="input-group input-group-sm barra-resultados-buscar-tabla">
            <div class="input-group-prepend">
                <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
            </div>
            <input type="search" class="form-control" placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaGestionesReporte">
        </div>
        <span class="barra-resultados-conteo-visible d-none" data-buscar-en-tabla-conteo="tablaGestionesReporte"></span>
<%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
     gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
     la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasGestiones != null && !filasGestiones.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasGestiones != null && !filasGestiones.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasGestiones != null && !filasGestiones.isEmpty()) %> target="_blank"
           title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."
           href="${pageContext.request.contextPath}/reportes/gestiones/informe?desde=${desde}&hasta=${hasta}&tipo=${tipo}&soloIncumplidas=${soloIncumplidas}&clienteUOperacion=${clienteUOperacion}"><i class="fas fa-file-pdf"></i> PDF</a>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasGestiones != null && !filasGestiones.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasGestiones != null && !filasGestiones.isEmpty()) %>
           title="Exporta según los filtros aplicados (fechas, tipo, etc.); no incluye la búsqueda en la tabla."
           href="${pageContext.request.contextPath}/reportes/gestiones/exportar?desde=${desde}&hasta=${hasta}&tipo=${tipo}&soloIncumplidas=${soloIncumplidas}&clienteUOperacion=${clienteUOperacion}">
            <i class="fas fa-file-excel"></i> Excel/CSV</a>
    </div>
</div>
<div class="barra-resultados-aviso-exportar-wrap">
    <p class="barra-resultados-aviso-exportar d-none" data-buscar-en-tabla-aviso-exportar="tablaGestionesReporte">
        <i class="fas fa-info-circle" aria-hidden="true"></i>
        <span data-buscar-en-tabla-aviso-exportar-texto></span>
    </p>
</div>

<% if (filasGestiones != null && !filasGestiones.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped" id="tablaGestionesReporte">
        <thead><tr><th>Fecha/hora</th><th>Operación</th><th>Contactado</th><th>Tipo</th><th>Resultado</th>
            <th>Promesa</th><th>Cumplida</th><th>Quién registró</th></tr></thead>
        <tbody>
        <% for (FilaGestionReporte filaGestion : filasGestiones) { %>
        <tr style="cursor:pointer;" onclick="window.location='${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaGestion.getIdPrestamo() %>'">
            <td><%= filaGestion.getFechaHora() %></td>
            <td><%= filaGestion.getIdentificadorOperacion() %></td>
            <td><%= filaGestion.getContactadoNombre() %></td>
            <td><%= filaGestion.getTipoEtiqueta() %></td>
            <td><%= filaGestion.getResultado() %></td>
            <td><%= filaGestion.getPromesaTexto() %></td>
            <%-- V13-ter (pedido de Joel): antes mostraba "No" también para promesas que todavía
                 no vencieron (parecía un incumplimiento que no era tal). isVencidaSinCumplir()
                 ya viene calculado desde el servidor contra Reloj.hoy() (ServicioReportes --
                 "tienePromesa && !cumplida && fechaPromesa.isBefore(hoy)"), el MISMO booleano
                 que suma calcularTotalesGestiones() para "Pendientes (aún no vencen)" --
                 reusarlo acá, fila por fila, garantiza que la tabla coincida con esos totales,
                 sin repetir ninguna comparación de fechas en este JSP. --%>
            <td><%
                if (!filaGestion.isTienePromesa()) { %>—<%
                } else if (filaGestion.isCumplida()) { %>
                <span class="badge badge-success">Sí</span><%
                } else if (filaGestion.isVencidaSinCumplir()) { %>
                <span class="badge badge-danger">No</span><%
                } else { %>
                <span class="badge badge-secondary">Pendiente</span><%
                } %></td>
            <td><%= filaGestion.getRegistradoPorNombre() %></td>
        </tr>
        <% } %>
        <tr class="buscar-en-tabla-sin-coincidencias d-none">
            <td colspan="8" class="text-center text-muted">Sin coincidencias.</td>
        </tr>
        </tbody>
    </table>
</div>
<%-- B210: LA paginación del sistema (B167). La URL base y "sin listado vivo" los dejó
     paginarReporte en el servlet, igual para los dieciséis reportes. --%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% if (totalesGestiones != null) { %>
<div class="card mt-3">
    <div class="card-body">
        <%-- V11, punto 5 (pedido de Joel): "del período" -- esta tabla tiene buscador en el
             navegador (arriba), así que el total de abajo puede no coincidir con lo que se ve
             filtrado en ese momento; esta aclaración evita la confusión, sin tocar el cálculo. --%>
        <strong>Total del período: <%= totalesGestiones.getCantidadTotal() %> gestiones</strong> —
        <% for (Map.Entry<String, Integer> entrada : totalesGestiones.getCantidadPorTipo().entrySet()) { %>
        <%= entrada.getKey() %>: <%= entrada.getValue() %> &nbsp;
        <% } %>
        <br>
        Promesas hechas: <strong><%= totalesGestiones.getCantidadPromesasHechas() %></strong> —
        Cumplidas: <strong><%= totalesGestiones.getCantidadCumplidas() %></strong> —
        Vencidas sin cumplir: <strong><%= totalesGestiones.getCantidadVencidasSinCumplir() %></strong> —
        Pendientes (aún no vencen): <strong><%= totalesGestiones.getCantidadPendientes() %></strong>
    </div>
</div>
<% } %>
<% } else if (Boolean.TRUE.equals(soloIncumplidasGestiones)) { %>
<%-- V13-ter (pedido de Joel): con el filtro "Solo promesas incumplidas" marcado, cero filas es
     una buena noticia, no una ausencia de datos -- mismo ícono de check verde que ya usan los
     mensajes de éxito del sistema. --%>
<div class="alert alert-success mt-3"><i class="fas fa-check-circle mr-1" aria-hidden="true"></i>No hay promesas incumplidas en ese período.</div>
<% } else { %>
<%-- B206 (15/11/2026, ítem 4 del informe de UX de Informes): el estado vacío iba en VERDE
     (alert-success) y acá vacío no es buena noticia: cero llamadas, mensajes y visitas es cobranza sin trabajar. El verde dice "todo bien" y se
     lee al revés de lo que pasó. Queda neutro (alert-secondary); el verde se conserva solo
     donde cero SÍ es buena noticia: Morosidad, Incobrables y el filtro de promesas
     incumplidas de Gestiones (V13-ter, decisión de Joel). --%>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay gestiones registradas en ese rango.</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
