<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%--
    Bloque 7/E10 (D-136, pedido de Joel): fragmento de resultados de /clientes -- contador +
    tabla + paginación, extraído de lista.jsp para poder reusarlo de dos formas: (a) incluido
    (<%@ include %>) dentro de lista.jsp en la carga normal de la página, y (b) como respuesta
    completa de un pedido AJAX ("listado vivo", recursos/js/listado-vivo.js), reenviado
    directamente por ControladorClientesServlet cuando PeticionAjax.esAjax(request) -- MISMA
    consulta y MISMOS filtros que arma mostrarLista en los dos casos, esto solo decide qué HTML
    se manda de vuelta. Mecanismo pensado para reusarse en los próximos listados del sistema:
    cada uno con su propio fragmento de resultados y el mismo recursos/js/listado-vivo.js.

    Nombres de variable (puedeEditarListaFila, etc.) sin choques con lista.jsp a propósito: este
    archivo se incluye (<%@ include %>, traducción estática) DENTRO del mismo _jspService que
    lista.jsp, así que una variable local con el mismo nombre en los dos archivos no compila
    (Java no permite "redeclarar"/sombrear una variable local ya declarada en el mismo método,
    ni siquiera dentro de un bloque { } propio -- se probó y falla igual).
--%>
<%
    @SuppressWarnings("unchecked")
    List<Persona> listaResultados = (List<Persona>) request.getAttribute("resultados");
    Boolean puedeEditarListaFila = (Boolean) request.getAttribute("puedeEditar");
    Boolean puedeDarDeBaja = (Boolean) request.getAttribute("puedeDarDeBaja");
    Integer paginaActual = (Integer) request.getAttribute("pagina");
    Integer totalPaginas = (Integer) request.getAttribute("totalPaginas");
    Integer totalResultados = (Integer) request.getAttribute("totalResultados");
    String queryStringFiltros = (String) request.getAttribute("queryStringFiltros");
    if (queryStringFiltros == null) { queryStringFiltros = ""; }
    @SuppressWarnings("unchecked")
    Map<Long, String> ciudadPorId = (Map<Long, String>) request.getAttribute("ciudadPorId");
    @SuppressWarnings("unchecked")
    Map<Long, Integer> prestamosActivosPorId = (Map<Long, Integer>) request.getAttribute("prestamosActivosPorId");
    // Normalización de personas, grupo Domicilio (16/10/2026): la ciudad de cada persona ya no
    // está en Persona -- este mapa (ControladorClientesServlet.mostrarLista) la resuelve de una
    // sola consulta a personas_domicilio.
    @SuppressWarnings("unchecked")
    Map<Long, Long> idCiudadPorPersona = (Map<Long, Long>) request.getAttribute("idCiudadPorPersona");
    // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que
    // idCiudadPorPersona -- la situación laboral ya no está en Persona.
    @SuppressWarnings("unchecked")
    Map<Long, String> situacionLaboralPorPersona = (Map<Long, String>) request.getAttribute("situacionLaboralPorPersona");
    // V11 (pedido de Joel): "Informe PDF"/"Excel/CSV" se movieron acá (barra de resultados,
    // justo arriba de la tabla) -- misma clave de request.setAttribute de siempre
    // (ControladorClientesServlet.mostrarLista la pone en los dos casos, página completa y
    // fragmento AJAX), con nombre de variable distinto al de lista.jsp ("Fila" al final, mismo
    // criterio que puedeEditarListaFila/puedeDarDeBaja de abajo) porque este archivo se incluye
    // DENTRO del mismo _jspService que lista.jsp (variable duplicada = error de compilación).
    Boolean puedeEmitirInformesFila = (Boolean) request.getAttribute("puedeEmitirInformes");
%>
<% if (listaResultados != null) { %>
<%-- V11: barra de resultados -- conteo a la izquierda (se sigue actualizando solo, es parte de
     este mismo fragmento que recursos/js/listado-vivo.js reemplaza en cada búsqueda) y, a la
     derecha, los mismos botones de informe de siempre: type="submit" con el mismo
     formaction/formtarget de antes, ahora asociados al <form> de lista.jsp (que quedó fuera de
     este fragmento) con form="formularioFiltrosClientes" -- toman los filtros actuales
     igual que si estuvieran adentro del <form>, es el mismo mecanismo nativo de HTML. --%>
<div class="d-flex justify-content-between align-items-center flex-wrap listado-vivo-barra-resultados">
    <%-- B132 (11/11/2026): el plural se resuelve, como en prestamos/lista.jsp y en los informes.
         Era el único "(s)" del sistema. --%>
    <p class="text-muted mb-2"><%
        int totalClientesResultado = totalResultados == null ? listaResultados.size() : totalResultados;
    %><%= FormateadorNumeros.comoCantidad(totalClientesResultado) %> cliente<%= totalClientesResultado == 1 ? "" : "s" %>
        encontrado<%= totalClientesResultado == 1 ? "" : "s" %>.</p>
    <% if (Boolean.TRUE.equals(puedeEmitirInformesFila)) { %>
    <div class="mb-2">
        <button type="submit" form="formularioFiltrosClientes"
                formaction="${pageContext.request.contextPath}/clientes/informe" formtarget="_blank"
                class="btn btn-outline-secondary btn-sm"><i class="fas fa-file-pdf"></i> PDF</button>
        <button type="submit" form="formularioFiltrosClientes"
                formaction="${pageContext.request.contextPath}/clientes/exportar"
                class="btn btn-outline-secondary btn-sm"><i class="fas fa-file-excel"></i> Excel/CSV</button>
    </div>
    <% } %>
</div>
<div class="table-responsive">
    <table class="table table-striped table-hover">
        <%-- B157 (14/11/2026): encabezados que ordenan. Solo las CUATRO columnas que viven en
             "personas" (la segunda posición de cada par es la clave de la lista blanca de
             PersonaDAO; null = esta columna no ordena). Ciudad, Situación laboral y Préstamos
             activos no salen de esa consulta -- los resuelve el controlador con tres mapas
             aparte-- así que no ofrecen el control: un encabezado que parece clickeable y no
             ordena es peor que uno que no lo parece (decisión de Joel, 14/11/2026).
             El enlace es un href real con los filtros actuales: ordenar tiene que pasar por el
             SERVIDOR, porque la paginación es LIMIT/OFFSET -- ordenar en el navegador ordenaría
             solo las 20 filas de esta página y mostraría un orden falso. --%>
        <%
            // El dueño del orden por defecto es PersonaDAO.ORDEN_POR_DEFECTO, y el controlador
            // SIEMPRE deja estos dos atributos puestos. El "nombre"/"asc" de abajo no es un
            // segundo dueño: es el respaldo para el único caso en que no llegan -- la copia
            // desplegada de esta JSP corriendo contra un .class viejo, antes del Clean and Build.
            // Por eso acá va el literal y NO PersonaDAO.ORDEN_POR_DEFECTO: una constante nueva
            // referenciada desde la JSP dejaría la pantalla en 500 hasta ese Clean and Build
            // (sgcpc-cambio-seguro, punto 11b).
            String ordenActualTabla = (String) request.getAttribute("ordenActual");
            String sentidoActualTabla = (String) request.getAttribute("sentidoActual");
            if (ordenActualTabla == null) { ordenActualTabla = "nombre"; }
            if (sentidoActualTabla == null) { sentidoActualTabla = "asc"; }
            String prefijoFiltrosTabla = queryStringFiltros.isEmpty() ? "" : queryStringFiltros + "&";
            String[][] columnasTabla = {
                {"C.I.", "ci"}, {"Apellidos y nombres", "nombre"}, {"Teléfono", "telefono"},
                {"Ciudad", null}, {"Situación laboral", null}, {"Préstamos activos", null},
                {"Estado", "estado"}, {"", null}
            };
        %>
        <thead>
        <tr>
        <% for (String[] columnaTabla : columnasTabla) {
               if (columnaTabla[1] == null) { %>
            <th><%= columnaTabla[0] %></th>
        <%     } else {
                   boolean esColumnaOrdenada = columnaTabla[1].equals(ordenActualTabla);
                   String sentidoDelEnlace = esColumnaOrdenada && "asc".equals(sentidoActualTabla) ? "desc" : "asc";
                   String iconoOrden = !esColumnaOrdenada ? "fa-sort"
                           : ("asc".equals(sentidoActualTabla) ? "fa-sort-up" : "fa-sort-down"); %>
            <th><a class="cc-orden-columna" title="Ordenar por <%= columnaTabla[0] %>"
                   href="${pageContext.request.contextPath}/clientes?<%= prefijoFiltrosTabla %>orden=<%= columnaTabla[1] %>&sentido=<%= sentidoDelEnlace %>"><%=
                   columnaTabla[0] %> <i class="fas <%= iconoOrden %>"></i></a></th>
        <%     } %>
        <% } %>
        </tr>
        </thead>
        <tbody>
        <% if (listaResultados.isEmpty()) { %>
        <tr><td colspan="8" class="text-muted">Sin clientes para los filtros elegidos.</td></tr>
        <% } %>
        <% for (Persona fila : listaResultados) {
               Long idCiudadFila = idCiudadPorPersona == null ? null : idCiudadPorPersona.get(fila.getIdPersona());
               String ciudadNombre = ciudadPorId == null || idCiudadFila == null ? "-" : ciudadPorId.get(idCiudadFila);
               Integer prestamosActivos = prestamosActivosPorId == null ? null : prestamosActivosPorId.get(fila.getIdPersona());
               boolean inactivo = fila.getEstadoPersona().name().equals("INACTIVA"); %>
        <tr>
            <td><%= FormateadorTexto.comoCedulaConPuntos(fila.getCedulaIdentidad()) %></td>
            <td><%= fila.getApellidos() %> <%= fila.getNombres() %></td>
            <td><%= fila.getTelefonoWhatsapp() %></td>
            <td><%= ciudadNombre == null ? "-" : ciudadNombre %></td>
            <td><%= py.com.capitalcredito.sgcpc.servicios.ServicioClientes.etiquetaSituacionLaboral(
                    situacionLaboralPorPersona == null ? null : situacionLaboralPorPersona.get(fila.getIdPersona())) %></td>
            <td><%= prestamosActivos == null ? "-" : prestamosActivos %></td>
            <td><span class="badge <%= inactivo ? "badge-secondary" : "badge-success" %>"><%= fila.getEstadoPersona().getEtiqueta() %></span></td>
            <%-- B157, ajuste del 14/11/2026 (Joel, probándolo en pantalla): las flechitas de los
                 encabezados ensancharon el <thead> y la tabla quedó apretada, así que "Ver /
                 editar" se partía en dos renglones. La clase le pone nowrap a TODA la celda, así
                 que tampoco se parten "Dar de baja" ni "Reactivar" (sgcpc-tema.css §12). --%>
            <td class="cc-celda-acciones">
                <a href="${pageContext.request.contextPath}/clientes/<%= fila.getIdPersona() %>" class="btn btn-sm btn-info">
                    <%= Boolean.TRUE.equals(puedeEditarListaFila) ? "Ver / editar" : "Ver" %>
                </a>
                <% if (Boolean.TRUE.equals(puedeDarDeBaja)) { %>
                    <% if (inactivo) { %>
                    <form method="post" action="${pageContext.request.contextPath}/clientes/<%= fila.getIdPersona() %>/reactivar"
                          class="d-inline" data-confirmar-titulo="¿Reactivar a <%= fila.getNombres() %> <%= fila.getApellidos() %>?" data-confirmar=""
                          data-confirmar-boton="Sí, reactivar">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <button type="submit" class="btn btn-sm btn-outline-success">Reactivar</button>
                    </form>
                    <% } else { %>
                    <%-- B155 (14/11/2026): abre el modal de más abajo. Antes era un
                         data-toggle="collapse" que desplegaba una fila dentro de la tabla: el
                         botón quedaba en una fila y el motivo aparecía en otra, así que en una
                         tabla rayada se perdía de quién era la baja que se estaba confirmando. --%>
                    <button type="button" class="btn btn-sm btn-outline-danger" data-toggle="modal"
                            data-target="#modalBaja<%= fila.getIdPersona() %>">Dar de baja</button>
                    <% } %>
                <% } %>
            </td>
        </tr>
        <% } %>
        </tbody>
    </table>
</div>
<%-- B155 (14/11/2026): un modal por fila, dibujado DESPUÉS de la tabla y dentro de este mismo
     fragmento -- exactamente el patrón de mora/_resultados.jsp, que es el único otro modal del
     sistema y vive igual que éste dentro de un listado vivo. Es Bootstrap puro
     (data-toggle/data-target, delegado en document), así que sobrevive solo al reemplazo del
     fragmento por AJAX: ni hay que re-atar nada ni listado-vivo.js tiene que saber de esto.
     El formulario es el MISMO de antes -- misma ruta, mismos motivoBaja/detalleBaja, mismo CSRF --
     y NO lleva data-confirmar: el modal ES la confirmación (nombra al cliente en el título y el
     botón dice la operación), y dejarlo apilaría dos diálogos. Mismo criterio que el modal de
     mora, que tampoco lo lleva. La baja es reversible ("Reactivar"), no mueve dinero y no emite
     ningún documento, así que no entra en el foco-en-cancelar de B105. --%>
<% if (Boolean.TRUE.equals(puedeDarDeBaja)) { %>
<% for (Persona filaModalBaja : listaResultados) {
       if (filaModalBaja.getEstadoPersona().name().equals("INACTIVA")) { continue; } %>
<div class="modal fade" id="modalBaja<%= filaModalBaja.getIdPersona() %>" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post" action="${pageContext.request.contextPath}/clientes/<%= filaModalBaja.getIdPersona() %>/baja">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <div class="modal-header">
                    <h5 class="modal-title">¿Dar de baja a <%= filaModalBaja.getNombres() %> <%= filaModalBaja.getApellidos() %>?</h5>
                </div>
                <div class="modal-body">
                    <p class="text-muted mb-3"><small>Queda inactivo y no puede iniciar solicitudes nuevas.
                        Se lo puede reactivar después, desde esta misma lista.</small></p>
                    <div class="form-group">
                        <label>Motivo *</label>
                        <select name="motivoBaja" class="form-control" required>
                            <option value="">Motivo...</option>
                            <option value="FALLECIMIENTO">Fallecimiento</option>
                            <option value="PEDIDO_CLIENTE">Pedido del cliente</option>
                            <option value="DATOS_DUPLICADOS">Datos duplicados</option>
                            <option value="OTRO">Otro</option>
                        </select>
                    </div>
                    <div class="form-group mb-0">
                        <label>Detalle (opcional)</label>
                        <%-- El 300 es el largo REAL de personas_baja.detalle_baja (varchar(300),
                             verificado en la base), no un número elegido a ojo: así el navegador
                             frena antes de que el servidor tenga que rechazar o recortar. --%>
                        <input type="text" name="detalleBaja" class="form-control" maxlength="300">
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal">No, volver</button>
                    <button type="submit" class="btn btn-danger">Sí, dar de baja</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>
<% } %>
<%-- B167 (14/11/2026, decisión de Joel): la paginación salió de acá a
     plantilla/paginacion.jsp, que ahora usan Clientes y Solicitudes. El marcado es el MISMO de
     B148 -- lo único que esta pantalla hace es armar la URL base, con los filtros y el orden
     vigentes, terminada en "&" (o en "?" si no hay filtros), porque el fragmento le pega
     "pagina=N" y nada más. Así la paginación del sistema vuelve a tener un solo dueño. --%>
<% if (totalPaginas != null && totalPaginas > 1) {
       String prefijoPaginaUrl = (queryStringFiltros.isEmpty() ? "?" : "?" + queryStringFiltros + "&")
               + "orden=" + ordenActualTabla + "&sentido=" + sentidoActualTabla + "&";
       request.setAttribute("paginacionUrlBase", request.getContextPath() + "/clientes" + prefijoPaginaUrl);
%>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% } %>
<% } %>
