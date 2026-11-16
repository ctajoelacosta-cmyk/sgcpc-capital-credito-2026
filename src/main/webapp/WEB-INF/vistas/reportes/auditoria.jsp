<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaAuditoria" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<FilaAuditoria> filasAuditoria = (List<FilaAuditoria>) request.getAttribute("filas");
    @SuppressWarnings("unchecked")
    Map<String, Integer> totalesPorOperacion = (Map<String, Integer>) request.getAttribute("totalesPorOperacion");
    String tituloTotales = (String) request.getAttribute("tituloTotales");
    // B205 (15/11/2026): el total REAL del rango (no el de la página) y la paginación. El
    // "avisoLimite" ya no llega acá: la pantalla dejó de tener tope, así que no hay nada que
    // avisar -- ese aviso quedó solo en el PDF, que sí sigue acotado.
    Integer totalFilasAuditoria = (Integer) request.getAttribute("totalFilas");
    // RF40/F1-F2 (pedido de Joel, 13/10/2026): usuarios y entidades reales para los <select>.
    @SuppressWarnings("unchecked")
    List<Usuario> usuariosAuditoria = (List<Usuario>) request.getAttribute("usuarios");
    @SuppressWarnings("unchecked")
    List<String> entidadesAuditoria = (List<String>) request.getAttribute("entidades");
    Long idUsuarioAuditoria = (Long) request.getAttribute("idUsuario");
    String tipoEntidadAuditoria = (String) request.getAttribute("tipoEntidad");
%>
<%-- B205: el contador va en el TÍTULO, como en todas las demás pantallas (B157/B175/B183/B191/
     B197) y con el total del rango completo, no el de la página. --%>
<%-- B205, segunda vuelta: el contador va con separador de miles (el mismo punto que el resto del
     sistema), porque con 2.230 registros salía "2230" pegado. El dueño del formato es
     FormateadorNumeros.comoCantidad, no esta línea. --%>
<h1>Auditoría<%= totalFilasAuditoria == null ? "" : " ("
        + FormateadorNumeros.comoCantidad(totalFilasAuditoria.intValue())
        + (totalFilasAuditoria.intValue() == 1 ? " registro)" : " registros)") %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/reportes">&laquo; Reportes</a></p>
<%-- B211 (15/11/2026, ítem 6): qué período está mostrando esta pantalla. El texto lo deja el
     controlador con el rango EFECTIVO (ServicioReportes.textoRangoAplicado/textoMesAplicado):
     en la primera carga no hay ningún parámetro y el reporte igual está usando su valor por
     defecto. Vacío en los reportes que son foto del momento (Cartera, Morosidad). --%>
<%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

<%-- V11 (pedido de Joel), lote 3: mismo patrón que los lotes anteriores -- de form-inline a
     grilla de columnas iguales. PDF/Excel eran (y siguen siendo) <a href="..."> con la URL
     armada con EL (${desde}/${hasta}/${tipoEntidad}/${operacion}, que resuelven contra los
     atributos que ya pone el servlet) -- NO son botones de formulario, así que no necesitan
     form="id": alcanza con moverlos de lugar, sin tocar ni el href ni cómo se arma. El <form>
     no tenía action (se autoenvía a la URL actual, que ya es /reportes/auditoria) -- se deja
     exactamente así; "Limpiar" apunta a esa misma ruta sin parámetros. Sin buscador de tabla:
     esta pantalla ya busca por texto en el servidor (Entidad/Operación contiene). --%>
<div class="card">
    <div class="card-body">
        <form method="get">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Desde</label>
                    <%-- Bloque de eficiencia (hallazgo de Joel): el servidor ya nunca deja esto sin
                         acotar (si se manda vacío, ControladorReportesServlet.mostrarAuditoria
                         defaultea a "hoy") -- el required es para que también quede explícito en
                         la pantalla, no silencioso. --%>
                    <input type="date" name="desde" class="form-control" value="${desde}" required>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Hasta</label>
                    <input type="date" name="hasta" class="form-control" value="${hasta}" required>
                </div>
                <%-- RF40/F1 (pedido de Joel, 13/10/2026): antes el backend ya leía "idUsuario"
                     pero la pantalla no tenía ningún campo para escribirlo. --%>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Usuario</label>
                    <select name="idUsuario" class="form-control">
                        <option value="">(todos)</option>
                        <% if (usuariosAuditoria != null) {
                            for (Usuario usuarioAuditoria : usuariosAuditoria) { %>
                        <option value="<%= usuarioAuditoria.getIdUsuario() %>"
                                <%= usuarioAuditoria.getIdUsuario().equals(idUsuarioAuditoria) ? "selected" : "" %>>
                            <%= usuarioAuditoria.getApellidos() %> <%= usuarioAuditoria.getNombres() %></option>
                        <% } } %>
                    </select>
                </div>
                <%-- RF40/F2 (pedido de Joel, 13/10/2026): antes era texto libre -- el usuario
                     tenía que saber y escribir el nombre interno exacto. --%>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Entidad</label>
                    <select name="tipoEntidad" class="form-control">
                        <option value="">(todas)</option>
                        <% if (entidadesAuditoria != null) {
                            for (String entidadAuditoria : entidadesAuditoria) { %>
                        <option value="<%= entidadAuditoria %>" <%= entidadAuditoria.equals(tipoEntidadAuditoria) ? "selected" : "" %>>
                            <%= entidadAuditoria %></option>
                        <% } } %>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Operación contiene</label>
                    <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 3): busca por
                         palabra en el nombre legible Y en el código interno, sin mayúsculas ni
                         acentos (AuditoriaDAO/EtiquetasAuditoria) -- el datalist solo sugiere
                         nombres legibles (pedido de Joel), sin obligar a elegir uno; el código
                         crudo sigue encontrándose igual si se escribe directo. --%>
                    <input type="text" name="operacion" class="form-control" value="${operacion}"
                           list="sugerenciasOperacionAuditoria">
                    <datalist id="sugerenciasOperacionAuditoria">
                        <% for (String etiquetaOperacionAuditoria : EtiquetasAuditoria.listarEtiquetasOrdenadas()) { %>
                        <option value="<%= etiquetaOperacionAuditoria %>">
                        <% } %>
                    </datalist>
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <a href="${pageContext.request.contextPath}/reportes/auditoria" class="btn btn-outline-secondary">
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
    <%-- B205: contar las filas DIBUJADAS acá diría "20 registros mostrados" en cada página, que
         es la trampa que B191 ya había encontrado en Caja ("20 encontradas" con 136 sesiones). El
         total real está en el título; acá queda en qué página está. --%>
    <p class="text-muted mb-2">
        <% if (filasAuditoria != null && !filasAuditoria.isEmpty()) { %>
        Página ${pagina} de ${totalPaginas}.
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <%-- RF40/F1 (pedido de Joel, 13/10/2026): "idUsuario" agregado a los 2 enlaces -- el
             servidor ya lo aplicaba a los datos, pero estos <a> no lo venían pasando. --%>
        <%-- B209 (15/11/2026, ítem 7): el ⓘ que dice qué hacen los dos botones, y los botones en
        gris cuando no hay nada que exportar (antes se podía sacar un PDF en blanco). El texto y
        la clase los decide ServicioInformes; acá solo se dice si hay filas. --%>
        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasAuditoria != null && !filasAuditoria.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasAuditoria != null && !filasAuditoria.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasAuditoria != null && !filasAuditoria.isEmpty()) %> target="_blank"
           href="${pageContext.request.contextPath}/reportes/auditoria/informe?desde=${desde}&hasta=${hasta}&idUsuario=${idUsuario}&tipoEntidad=${tipoEntidad}&operacion=${operacion}"><i class="fas fa-file-pdf"></i> PDF</a>
        <a class="btn btn-outline-secondary btn-sm<%= ServicioInformes.claseBotonExportar(filasAuditoria != null && !filasAuditoria.isEmpty()) %>"<%= ServicioInformes.atributosBotonExportar(filasAuditoria != null && !filasAuditoria.isEmpty()) %> href="${pageContext.request.contextPath}/reportes/auditoria/exportar?desde=${desde}&hasta=${hasta}&idUsuario=${idUsuario}&tipoEntidad=${tipoEntidad}&operacion=${operacion}">
            <i class="fas fa-file-excel"></i> Excel/CSV</a>
    </div>
</div>

<% if (filasAuditoria != null && !filasAuditoria.isEmpty()) { %>
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <%-- Bloque de seguridad (hallazgo de Joel): 3 columnas nuevas -- IP y navegador ya se
             guardaban desde antes, "Usuario escrito" es nueva (solo tiene valor en
             ACCESO_USUARIO_INEXISTENTE, cuando no hay ninguna cuenta real detrás del intento). --%>
        <thead><tr><th>Fecha/hora</th><th>Usuario</th><th>Usuario escrito</th><th>Operación</th><th>Entidad</th><th>Id</th><th>IP</th><th>Navegador</th><th>Detalle</th></tr></thead>
        <tbody>
        <% for (FilaAuditoria filaAuditoria : filasAuditoria) { %>
        <tr>
            <td><%= filaAuditoria.getFechaHora() %></td>
            <td><%= filaAuditoria.getUsuario() %></td>
            <td><%= filaAuditoria.getUsuarioEscrito() == null ? "" : filaAuditoria.getUsuarioEscrito() %></td>
            <td><%= EtiquetasAuditoria.comoEtiquetaLegible(filaAuditoria.getOperacion()) %></td>
            <td><%= filaAuditoria.getTipoEntidad() %></td>
            <%-- Bloque de seguridad (hallazgo de Joel): id_entidad=0 es la marca de "sin entidad
                 real" (ACCESO_USUARIO_INEXISTENTE, RECALCULO_MORA_DIARIO) -- nunca un id válido
                 (los seriales de Postgres arrancan en 1). --%>
            <td><%= filaAuditoria.getIdEntidad() == 0 ? "—" : String.valueOf(filaAuditoria.getIdEntidad()) %></td>
            <td><%= FormateadorTexto.comoDireccionIpLegible(filaAuditoria.getDireccionIp()) %></td>
            <td class="text-truncate" style="max-width: 220px;" title="<%= filaAuditoria.getNavegador() == null ? "" : filaAuditoria.getNavegador().replace("\"", "&quot;") %>">
                <%= FormateadorTexto.comoNavegadorResumido(filaAuditoria.getNavegador()) %></td>
            <%-- RF40/F4 (pedido de Joel, 13/10/2026): antes el texto largo quedaba cortado
                 (había que hacer scroll horizontal) -- "max-width" + "word-break" fuerza el
                 salto de línea dentro de la celda, sin agrandar la tabla. --%>
            <td style="max-width: 420px; white-space: normal; word-break: break-word;">
                <%= filaAuditoria.getDetalle() == null ? "" : filaAuditoria.getDetalle() %></td>
        </tr>
        <% } %>
        </tbody>
    </table>
</div>
<%-- B205: LA paginación del sistema (B167), con la URL base que armó el servlet con los cinco
     filtros. "paginacionConListadoVivo" en FALSE porque esta pantalla no tiene búsqueda en vivo:
     los enlaces navegan de verdad, igual que en Formalización (B183). --%>
<% request.setAttribute("paginacionConListadoVivo", Boolean.FALSE); %>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% if (totalesPorOperacion != null && !totalesPorOperacion.isEmpty()) { %>
<div class="card mt-3">
    <div class="card-body">
        <strong><%= tituloTotales %>:</strong>
        <% for (Map.Entry<String, Integer> entrada : totalesPorOperacion.entrySet()) { %>
        &nbsp; <%= EtiquetasAuditoria.comoEtiquetaLegible(entrada.getKey()) %>: <%= entrada.getValue() %>
        <% } %>
    </div>
</div>
<% } %>
<% } else { %>
<%-- B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): con el rango invertido
     este cartel salía ADEMÁS del aviso ámbar, diciendo lo mismo dos veces y peor — el aviso ya
     explica que el listado sale vacío y por qué. La marca la deja
     plantilla/aviso_rango_reporte.jsp, que se dibuja más arriba en la misma página. --%>
<% if (!Boolean.TRUE.equals(request.getAttribute("rangoReporteInvertido"))) { %>
<div class="alert alert-secondary mt-3">No hay operaciones registradas con esos filtros.</div>
<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
