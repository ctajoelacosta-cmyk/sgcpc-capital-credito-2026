<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    Usuario usuarioCambio = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
%>
<h1>Cambiar contraseña</h1>

<% if (usuarioCambio != null && usuarioCambio.isDebeCambiarContrasena()) { %>
<div class="alert alert-warning">Por seguridad, debe cambiar su contraseña antes de continuar.</div>
<% } %>

<div class="card card-primary" style="max-width: 420px;">
    <div class="card-body">
        <form method="post" action="${pageContext.request.contextPath}/cambiar-contrasena">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-group">
                <label>Contraseña actual</label>
                <input type="password" name="contrasenaActual" class="form-control" autocomplete="current-password" required>
            </div>
            <div class="form-group">
                <label for="campoContrasenaNueva">Contraseña nueva</label>
                <input type="password" id="campoContrasenaNueva" name="contrasenaNueva" class="form-control"
                       autocomplete="new-password" required>
                <small class="form-text text-muted">Mínimo 8 caracteres, con al menos una letra y un número.</small>
            </div>
            <div class="form-group">
                <label for="campoContrasenaConfirmacion">Confirmar contraseña nueva</label>
                <%-- B216 (pedido de Joel, 15/11/2026): data-confirma-a avisa EN VIVO si las dos no
                     coinciden y no deja enviar hasta que coincidan (recursos/js/campo-contrasena.js,
                     el mismo script que ya le pone a estos campos el ojo de mostrar/ocultar y el
                     aviso de Bloq Mayús). Antes el error llegaba del servidor y los tres campos
                     volvían vacíos; ahora el viaje no ocurre. La validación del servidor
                     (ServicioAutenticacion.validarConfirmacionCoincide) no se toca: sigue siendo
                     la que decide. --%>
                <input type="password" id="campoContrasenaConfirmacion" name="contrasenaConfirmacion"
                       class="form-control" autocomplete="new-password"
                       data-confirma-a="campoContrasenaNueva" required>
            </div>
            <button type="submit" class="btn btn-primary">Cambiar contraseña</button>
        </form>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
