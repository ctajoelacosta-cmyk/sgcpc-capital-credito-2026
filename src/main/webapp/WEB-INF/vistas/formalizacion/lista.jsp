<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String textoBusquedaFormalizacion = (String) request.getAttribute("textoBusqueda");
%>
<h1>Formalización</h1>
<p class="text-muted">Solicitudes aprobadas, listas para formalizar (generar contrato, pagaré y plan de pagos).</p>

<%-- Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): UN solo
     campo filtra las 3 secciones de abajo a la vez (para formalizar, pendientes de desembolso,
     cierre documental pendiente) -- mismo mecanismo de "listado vivo" que las otras pantallas. --%>
<div class="card mb-3">
    <div class="card-body">
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo layout que Clientes (la
             referencia) en las 6 pantallas con buscador. --%>
        <form method="get" action="${pageContext.request.contextPath}/formalizacion" id="formularioFiltrosFormalizacion"
              data-listado-vivo="resultadosFormalizacion">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-4">
                    <label class="mb-0">N.º de solicitud/operación, cédula o nombre del cliente</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaFormalizacion != null ? textoBusquedaFormalizacion : "" %>">
                </div>
            </div>
            <div class="form-row justify-content-end align-items-center">
                <div class="col-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/formalizacion" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosFormalizacion">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultadosFormalizacion">
    <%@ include file="/WEB-INF/vistas/formalizacion/_resultados.jsp" %>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
