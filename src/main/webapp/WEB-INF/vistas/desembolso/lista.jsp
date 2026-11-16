<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SesionCaja" %>
<%
    String textoBusquedaDesembolso = (String) request.getAttribute("textoBusqueda");
    // B186 (15/11/2026): el estado de la caja, arriba de la lista. Los dos valores los deja
    // ControladorDesembolsoServlet.mostrarLista y el texto es el de ServicioCaja (un dato, un
    // dueño): acá solo se dibuja.
    SesionCaja sesionAbiertaListaDesembolso = (SesionCaja) request.getAttribute("sesionAbierta");
    String mensajeCajaOtroDiaListaDesembolso = (String) request.getAttribute("mensajeCajaOtroDia");
%>
<h1>Préstamos listos para desembolsar</h1>

<%-- B186 (15/11/2026): el aviso va ACÁ, fuera del fragmento de resultados, por dos razones: no
     depende de la búsqueda (es el estado de la caja, no del filtro) y así el pedido AJAX del
     "listado vivo" no lo borra ni lo repite. Mismo texto, mismo color (alert-warning: es un
     bloqueo de tiempo, no un error -- A.8) y mismo enlace que la ficha. --%>
<% if (sesionAbiertaListaDesembolso == null) { %>
<div class="alert alert-warning">No hay caja abierta. Sin caja no se puede desembolsar.
    <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<% } else if (mensajeCajaOtroDiaListaDesembolso != null) { %>
<div class="alert alert-warning"><%= mensajeCajaOtroDiaListaDesembolso %>
    <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
<% } %>

<%-- Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 5): mismo
     mecanismo de "listado vivo" que Clientes/Mora/Aprobaciones/Solicitudes/Formalización. --%>
<div class="card mb-3">
    <div class="card-body">
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo layout que Clientes (la
             referencia) en las 6 pantallas con buscador. --%>
        <form method="get" action="${pageContext.request.contextPath}/desembolso" id="formularioFiltrosDesembolso"
              data-listado-vivo="resultadosDesembolso">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">N.º de operación, cédula o nombre del cliente</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaDesembolso != null ? textoBusquedaDesembolso : "" %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/desembolso" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosDesembolso">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>
    </div>
</div>

<div class="card">
    <div class="card-body">
        <div id="resultadosDesembolso">
            <%@ include file="/WEB-INF/vistas/desembolso/_resultados.jsp" %>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
