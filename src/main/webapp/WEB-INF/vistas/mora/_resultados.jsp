<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaMora" %>
<%--
    Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): fragmento de
    resultados de /mora -- tabla + modales de gestión, extraído de lista.jsp para el mecanismo de
    "listado vivo" (recursos/js/listado-vivo.js), mismo patrón que clientes/_resultados.jsp:
    incluido (<%@ include %>) dentro de lista.jsp en la carga normal, y como respuesta completa
    de un pedido AJAX (ControladorMoraServlet.mostrarLista, PeticionAjax.esAjax).

    Hallazgo de Joel al pedir este paso: con los modales de gestión como hermanos de <tr> dentro
    de <tbody> (como estaban en lista.jsp), el HTML es inválido -- un <div> ahí nunca es hijo real
    de <tbody> (ya documentado en RF24_seguimiento_mora.md §10 como la causa de que el deshabilitado
    del botón, antes de tokenGestion, no fuera confiable). Acá los modales van DESPUÉS de </table>,
    en su propio bucle -- HTML válido, nada que el navegador tenga que "corregir" en cada
    reemplazo de innerHTML.
--%>
<%
    @SuppressWarnings("unchecked")
    List<FilaMora> filasMoraResultado = (List<FilaMora>) request.getAttribute("filas");
    @SuppressWarnings("unchecked")
    Map<Long, Long> idClientePorPrestamoResultado = (Map<Long, Long>) request.getAttribute("idClientePorPrestamo");
    @SuppressWarnings("unchecked")
    Map<Long, String> tokensGestionPorPrestamoResultado = (Map<Long, String>) request.getAttribute("tokensGestionPorPrestamo");
    String textoBusquedaResultado = (String) request.getAttribute("textoBusqueda");
    boolean hayBusquedaResultado = textoBusquedaResultado != null && !textoBusquedaResultado.trim().isEmpty();
%>
<%
    // B197 (15/11/2026): contador, orden y paginacion. Los valores los deja
    // ControladorMoraServlet.mostrarLista; aca solo se dibujan. Los respaldos (?:) son para esta
    // JSP contra un .class viejo, antes del Clean and Build (punto 11b).
    Integer totalMoraResultado = (Integer) request.getAttribute("totalResultados");
    if (totalMoraResultado == null) {
        totalMoraResultado = filasMoraResultado == null ? 0 : filasMoraResultado.size();
    }
    String ordenActualMora = (String) request.getAttribute("ordenActual");
    String sentidoActualMora = (String) request.getAttribute("sentidoActual");
    // El dueno de estos valores es ServicioMora.ordenAplicado/sentidoAplicado; aca va el literal
    // para no referenciar desde la JSP una constante nueva (punto 11b).
    if (ordenActualMora == null) { ordenActualMora = "prioridad"; }
    if (sentidoActualMora == null) { sentidoActualMora = "asc"; }
    String prefijoFiltroMora = hayBusquedaResultado
            ? "texto=" + java.net.URLEncoder.encode(textoBusquedaResultado, "UTF-8") + "&" : "";
    // {rotulo, clave de orden (null = no ordena), clase de la celda}. "Telefono", "Promesa" y
    // "Ultima gestion" NO ordenan a proposito: llegan como texto ya formateado y una fecha
    // "dd/mm/aaaa" ordena mal como cadena -- la regla de B157.
    String[][] columnasMora = {
            {"Operación", "operacion", "cc-sin-partir"},
            {"Cliente", "cliente", "cc-celda-nombre"},
            {"Teléfono", null, "cc-sin-partir"},
            {"Días atraso", "dias", "cc-sin-partir"},
            {"Cuotas venc.", "cuotas", "cc-sin-partir"},
            {"Monto exigible", "exigible", "cc-sin-partir cc-monto"},
            {"Promesa", null, "cc-sin-partir"},
            {"Última gestión", null, ""},
            {"Registrar gestión", null, ""}};
%>
<% if (filasMoraResultado != null && !filasMoraResultado.isEmpty()) { %>
<%-- B197: el contador cuenta el TOTAL del filtro, no las filas de la pagina. --%>
<p class="text-muted"><%= FormateadorNumeros.comoCantidad(totalMoraResultado) %>
    préstamo<%= totalMoraResultado == 1 ? "" : "s" %>
    en mora<%= hayBusquedaResultado ? " para esa búsqueda" : "" %>.</p>
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <%-- B197: encabezados que ordenan, con el mismo marcado y las mismas clases que
             Aprobaciones (B175), Formalizacion (B183) y Desembolso (B191). El enlace es un href
             real con el filtro actual; ordenar pasa por el servidor, que ordena la lista COMPLETA
             antes de cortar la pagina, y vuelve a la pagina 1 (no lleva "pagina"). --%>
        <thead>
        <tr>
        <% for (String[] columnaMora : columnasMora) {
               if (columnaMora[1] == null) { %>
            <th class="<%= columnaMora[2] %>"><%= columnaMora[0] %></th>
        <%     } else {
                   boolean esOrdenadaMora = columnaMora[1].equals(ordenActualMora);
                   String sentidoEnlaceMora = esOrdenadaMora && "asc".equals(sentidoActualMora) ? "desc" : "asc";
                   String iconoMora = !esOrdenadaMora ? "fa-sort"
                           : ("asc".equals(sentidoActualMora) ? "fa-sort-up" : "fa-sort-down"); %>
            <th class="<%= columnaMora[2] %>"><a class="cc-orden-columna"
                   title="Ordenar por <%= columnaMora[0] %>"
                   href="${pageContext.request.contextPath}/mora?<%= prefijoFiltroMora %>orden=<%= columnaMora[1] %>&sentido=<%= sentidoEnlaceMora %>"><%=
                   columnaMora[0] %> <i class="fas <%= iconoMora %>"></i></a></th>
        <%     } %>
        <% } %>
        </tr>
        </thead>
        <tbody>
        <% for (FilaMora filaMoraResultado : filasMoraResultado) { %>
        <tr class="<%= filaMoraResultado.isPromesaVencida() ? "table-danger" : "" %>">
            <%-- Tanda 2 (14/10/2026, hallazgo de Joel): antes iba a /cobranza/{id}, donde el
                 oficial no tiene permiso ("Acceso restringido", REGISTRAR_COBRO es solo de la
                 cajera) -- /reportes/estado-cuenta/{id} lo pueden ver oficial y gerente
                 (VER_REPORTES + SUBREPORTES_OFICIAL, ya incluye "estado-cuenta", RF34). --%>
            <td class="celda-codigo"><a href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= filaMoraResultado.getIdPrestamo() %>"><%= filaMoraResultado.getIdentificadorOperacion() %></a></td>
            <td><%= filaMoraResultado.getClienteNombre() %></td>
            <td><%= filaMoraResultado.getClienteTelefono() %></td>
            <td><%= filaMoraResultado.getDiasAtraso() %>
                <% if (filaMoraResultado.isIncobrable()) { %><br><span class="badge badge-dark">Incobrable</span><% } %>
            </td>
            <td><%= filaMoraResultado.getCuotasVencidas() %></td>
            <td class="cc-monto"><%= filaMoraResultado.getMontoExigible() %></td>
            <td>
                <% if (filaMoraResultado.isTienePromesaAbierta()) { %>
                <span class="<%= filaMoraResultado.isPromesaVencida() ? "text-danger font-weight-bold" : "" %>">
                    <%= filaMoraResultado.getPromesaFecha() %> — <%= filaMoraResultado.getPromesaMonto() %>
                    <%= filaMoraResultado.isPromesaVencida() ? " (vencida)" : "" %>
                </span>
                <% } %>
            </td>
            <td><% if (filaMoraResultado.getUltimaGestionTipo() != null) { %><%= filaMoraResultado.getUltimaGestionTipo() %> — <%= filaMoraResultado.getUltimaGestionFecha() %>
                <%-- Punto 1 de la mejora chica de RF24 (pedido de Joel): el resultado, para no
                     obligar a abrir el estado de cuenta solo para verlo. --%>
                <% if (filaMoraResultado.getUltimaGestionResultado() != null && !filaMoraResultado.getUltimaGestionResultado().isEmpty()) { %>
                <br><small class="text-muted"><%= filaMoraResultado.getUltimaGestionResultado() %></small>
                <% } %>
            <% } %></td>
            <td>
                <button type="button" class="btn btn-sm btn-outline-primary" data-toggle="modal"
                        data-target="#modalGestion<%= filaMoraResultado.getIdPrestamo() %>">Registrar</button>
            </td>
        </tr>
        <% } %>
        </tbody>
    </table>
</div>

<%-- B197 (15/11/2026): /mora tiene UN solo listado, asi que usa el parametro "pagina" de siempre y
     SI pasa por listado-vivo.js, igual que Clientes, Solicitudes, Aprobaciones y Desembolso -- no
     hace falta ninguno de los dos atributos que agrego B183 para las pantallas con varios. La URL
     base lleva el filtro y el orden vigentes. --%>
<% request.setAttribute("paginacionUrlBase",
           request.getContextPath() + "/mora?" + prefijoFiltroMora
                   + "orden=" + ordenActualMora + "&sentido=" + sentidoActualMora + "&"); %>
<%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>
<% for (FilaMora filaMoraModal : filasMoraResultado) {
    Long idClienteModal = idClientePorPrestamoResultado == null ? null : idClientePorPrestamoResultado.get(filaMoraModal.getIdPrestamo());
%>
<div class="modal fade" id="modalGestion<%= filaMoraModal.getIdPrestamo() %>" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <%-- Tanda 2 (14/10/2026, pedido de Joel): el deshabilitado del botón de abajo es solo
                 una ayuda visual del navegador -- NO es la protección real contra el doble clic.
                 La protección real es tokenGestion, de un solo uso por sesión (TokenUnaVez), que
                 ControladorMoraServlet.procesarGestion consume del lado del servidor. --%>
            <form method="post" action="${pageContext.request.contextPath}/mora/<%= filaMoraModal.getIdPrestamo() %>/gestion"
                  data-validar-promesa
                  onsubmit="this.querySelector('button[type=submit]').disabled = true; this.querySelector('button[type=submit]').textContent = 'Guardando...';">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <input type="hidden" name="tokenGestion" value="<%= tokensGestionPorPrestamoResultado == null ? ""
                        : tokensGestionPorPrestamoResultado.get(filaMoraModal.getIdPrestamo()) %>">
                <div class="modal-header">
                    <h5 class="modal-title">Registrar gestión — <%= filaMoraModal.getIdentificadorOperacion() %></h5>
                </div>
                <div class="modal-body">
                    <div class="form-group">
                        <label>Contactado</label>
                        <select name="idPersonaContactada" class="form-control" required>
                            <% if (idClienteModal != null) { %>
                            <option value="<%= idClienteModal %>">Cliente (<%= filaMoraModal.getClienteNombre() %>)</option>
                            <% } %>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Tipo</label>
                        <select name="tipo" class="form-control" required>
                            <option value="LLAMADA">Llamada</option>
                            <option value="MENSAJE">Mensaje</option>
                            <option value="VISITA_LOCAL">Visita al local</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Resultado</label>
                        <input type="text" name="resultado" class="form-control" maxlength="200" required>
                    </div>
                    <div class="form-group">
                        <label>Excusa (si no pagó)</label>
                        <input type="text" name="excusa" class="form-control" maxlength="300">
                    </div>
                    <div class="form-group">
                        <label>Fecha de promesa (si prometió pagar)</label>
                        <input type="date" name="fechaPromesa" class="form-control">
                    </div>
                    <div class="form-group">
                        <label>Monto prometido</label>
                        <input type="text" name="montoPrometido" class="form-control campo-guaranies">
                    </div>
                </div>
                <%-- B194 (15/11/2026): el botón de descartar decía *"Cancelar"*, y era la única
                     ventana del sistema que había quedado con ese verbo -- las de B155, B168, B171,
                     B178, B188 y B192 dicen *"No, volver"*. Es el criterio de B116 (un botón de
                     descartar no se llama igual que la acción de cancelar algo del negocio, que acá
                     además existe: se cancelan solicitudes y préstamos), aplicado a los modales:
                     B116 solo había unificado los carteles de SweetAlert.
                     **Sin autofocus a propósito**: registrar una gestión no es irreversible ni
                     mueve dinero (B105 pide el foco en descartar solo para lo que no se deshace), y
                     el foco útil acá es el primer campo del formulario. --%>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal">No, volver</button>
                    <button type="submit" class="btn btn-primary">Guardar</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>
<%-- Corrección de Joel (20/10/2026, prueba en pantalla): "sin mora" es una buena noticia
     (alert-success), pero "sin resultados para esa búsqueda" es neutro. Probado en pantalla que
     alert-info NO sirve para eso acá: recursos/css/sgcpc-tema.css le pone a propósito el mismo
     verde que a alert-success (comentario de ese archivo: "nada de celeste eléctrico, que no es
     un color del logo"), así que las dos clases se ven iguales. Mismo criterio que ya usa el
     "Sin clientes para los filtros elegidos" de clientes/_resultados.jsp: texto simple
     (text-muted), sin caja de aviso, para lo que no es ni un éxito ni un error. Cada texto en una
     sola línea de fuente (pruebas/humo_http.sh los busca con grep -F, que no cruza líneas). --%>
<% } else if (hayBusquedaResultado) { %>
<p class="text-muted">No se encontraron préstamos en mora para esa búsqueda.</p>
<% } else { %>
<div class="alert alert-success">No hay préstamos en mora en este momento.</div>
<% } %>
