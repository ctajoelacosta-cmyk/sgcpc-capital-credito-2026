<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%-- B193 (15/11/2026): import propio para el aviso de la caja (la lección de B169: cabecera.jsp no
     trae los modelos). --%>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SesionCaja" %>
<%-- B198 (15/11/2026): import propio para el rótulo de la opción (la lección de B169). --%>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioCobranza" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String textoBusquedaCobranzaPagina = (String) request.getAttribute("textoBusqueda");
    // RF38/D3 (13/10/2026, pedido de Joel): "Cancelación anticipada" del menú (cajera/
    // administrador) entra acá con ?origen=cancelacion-anticipada -- mismo patrón que V3h para
    // "Devolución del pagaré" (una pantalla compartida con otro punto de entrada del menú). Sin
    // el parámetro (entrada por "Cobranza"), esta pantalla queda exactamente igual que siempre;
    // nunca cambia la lógica de búsqueda/cobro/cancelación (RF22/RF26, ya consolidados).
    // B201 (15/11/2026): el valor y la comparación tienen un solo dueño (antes el literal estaba
    // en cuatro vistas).
    boolean origenCancelacionAnticipada =
            ServicioCobranza.esOrigenCancelacionAnticipada(request.getParameter("origen"));
%>
<h1><%= origenCancelacionAnticipada ? "Cancelación anticipada" : "Cobranza" %></h1>

<% if (origenCancelacionAnticipada) { %>
<%-- B198 (15/11/2026): el aviso decía «Cancelar todo», y ese botón se llama
     «Todo el préstamo» desde B106 (05/11/2026) -- mandaba a tocar algo que ya no existía con ese
     nombre, y es lo primero que lee quien entra por el menú "Cancelación anticipada".
     Lo importante no es el texto nuevo sino de DÓNDE sale: del mismo dueño que el botón
     (ServicioCobranza.OPCION_TODO_EL_PRESTAMO), que es justo lo que B106 centralizó para que un
     renombre no vuelva a dejar copias atrás. Si mañana cambia el rótulo, este aviso cambia solo. --%>
<div class="alert alert-info">Busque el préstamo y, en su ficha, toque
    «<%= ServicioCobranza.OPCION_TODO_EL_PRESTAMO %>».</div>
<% } %>

<%-- B193 (15/11/2026): el estado de la caja, arriba de la lista de trabajo. Va ACÁ, fuera del
     fragmento de resultados, por lo mismo que en B186: no depende de la búsqueda (es el estado de
     la caja, no del filtro) y así el pedido AJAX del "listado vivo" no lo borra ni lo repite.
     Mismo texto, mismo color (alert-warning: bloqueo de tiempo, no error -- A.8) y mismo enlace que
     la ficha. Los dos valores los deja ControladorCobranzaServlet.mostrarBusqueda. --%>
<%
    SesionCaja sesionAbiertaBusquedaPagina = (SesionCaja) request.getAttribute("sesionAbierta");
    String mensajeCajaOtroDiaBusquedaPagina = (String) request.getAttribute("mensajeCajaOtroDia");
%>
<% if (sesionAbiertaBusquedaPagina == null) { %>
<div class="alert alert-warning">No hay caja abierta. Sin caja no se puede cobrar.
    <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<% } else if (mensajeCajaOtroDiaBusquedaPagina != null) { %>
<div class="alert alert-warning"><%= mensajeCajaOtroDiaBusquedaPagina %>
    <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<% } %>

<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/cobranza" id="formBusquedaCobranza"
              data-listado-vivo="resultadosCobranza" class="form-inline">
            <%-- B201, segunda vuelta (15/11/2026, defecto que Joel encontró al probar la tanda 2):
                 este formulario es un GET a /cobranza y llevaba SOLO el campo "q", así que al
                 buscar la URL perdía el "origen" -- y con él el título, el aviso de entrada y el
                 sufijo de los enlaces "Cobrar" de los resultados (la ficha abría como
                 /cobranza/8?q=... y el menú volvía a marcar "Cobranza"). Desde la lista sin buscar
                 sí funcionaba, porque ahí el origen seguía en el query string de la página.
                 El campo oculto lo arregla por los DOS caminos de una vez: el submit normal lo
                 pone en la URL, y la búsqueda en vivo lo manda igual porque listado-vivo.js arma
                 el pedido con new FormData(form) (un hidden entra; y no se le atan listeners,
                 que solo miran text/search/select/date, ni lo vacía "Limpiar"). --%>
            <% if (origenCancelacionAnticipada) { %>
            <input type="hidden" name="origen" value="<%= ServicioCobranza.ORIGEN_CANCELACION_ANTICIPADA %>">
            <% } %>
            <label class="mr-2 mb-0" for="campoBusquedaCobranza">Buscar</label>
            <%-- A3a (#5, pedido de Joel): foco automático -- cada entrada a /cobranza, se podía
                 escribir sin un clic extra. --%>
            <input type="text" name="q" id="campoBusquedaCobranza" class="form-control mr-2" style="width: 320px;"
                   placeholder="Cédula, nombre o apellido, o número de operación" autofocus
                   value="<%= textoBusquedaCobranzaPagina == null ? "" : textoBusquedaCobranzaPagina %>">
            <button type="submit" class="btn btn-primary mr-2"><i class="fas fa-search"></i> Buscar</button>
            <%-- Y el respaldo sin JavaScript de "Limpiar" también: con JS el click lo intercepta
                 listado-vivo.js (vacía los campos de texto y deja el hidden), pero el href es el
                 que vale si el script no corre, y pelado sacaba al usuario de la pantalla de
                 Cancelación anticipada igual que la búsqueda. --%>
            <a href="${pageContext.request.contextPath}/cobranza<%= origenCancelacionAnticipada
                       ? "?origen=" + ServicioCobranza.ORIGEN_CANCELACION_ANTICIPADA : "" %>"
               class="btn btn-outline-secondary listado-vivo-limpiar">
                <i class="fas fa-eraser"></i> Limpiar</a>
            <small class="text-muted listado-vivo-indicador d-none ml-2">Buscando…</small>
        </form>
    </div>
</div>

<div id="resultadosCobranza" class="mt-3">
    <%@ include file="/WEB-INF/vistas/cobranza/_resultados_busqueda.jsp" %>
</div>

<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
