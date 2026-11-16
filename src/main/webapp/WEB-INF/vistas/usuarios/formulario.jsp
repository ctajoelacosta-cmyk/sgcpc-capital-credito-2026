<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    // Encontrado en la prueba de E3: ante un rechazo (ej. cédula duplicada) el formulario volvía
    // en blanco. Si el controlador reenvía acá tras un error, "usuario" trae lo que el gerente
    // ya había escrito (alta) o lo último que se intentó guardar (edición).
    Usuario usuarioIntentado = (Usuario) request.getAttribute("usuario");
    // Plan "Editar usuario" (pedido de Joel): mismo formulario para alta y edición -- menos
    // duplicar todo el bloque de campos en dos archivos.
    boolean modoEdicion = Boolean.TRUE.equals(request.getAttribute("modoEdicion"));
    // Punto 2: el <select> de perfil se deshabilita cuando alguien edita su propio usuario (el
    // servidor no confía en esto -- ver ServicioUsuarios.actualizar -- es solo para que se
    // entienda por qué no se puede tocar desde acá).
    boolean editaAsiMismo = modoEdicion && Boolean.TRUE.equals(request.getAttribute("editaAsiMismo"));
    // V4 (encontrado probando D-151): el título usa el nombre GUARDADO en la base, nunca lo
    // tipeado -- si el error fue justamente por nombres vacíos, el título no puede quedar en
    // blanco. "usuarioGuardado" es el registro real (ver ControladorUsuariosServlet); si por
    // algún motivo no se pudo leer, se cae al nombre tipeado como último recurso.
    Usuario usuarioGuardado = (Usuario) request.getAttribute("usuarioGuardado");
    Usuario usuarioParaTitulo = usuarioGuardado != null ? usuarioGuardado : usuarioIntentado;
    String tituloEdicion = usuarioParaTitulo == null ? "Editar usuario"
            : "Editar usuario: " + usuarioParaTitulo.getNombres() + " " + usuarioParaTitulo.getApellidos();
%>
<h1><%= modoEdicion ? tituloEdicion : "Nuevo usuario" %></h1>

<div class="card card-primary" style="max-width: 480px;">
    <div class="card-body">
        <form method="post" action="${pageContext.request.contextPath}/usuarios/<%= modoEdicion ? "editar" : "nuevo" %>">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <% if (modoEdicion) { %>
            <input type="hidden" name="idUsuario" value="<%= usuarioIntentado.getIdUsuario() %>">
            <% } %>
            <div class="form-group">
                <label>Nombre de usuario</label>
                <% if (modoEdicion) { %>
                <%-- El nombre de usuario (login) no se edita -- ver el plan aprobado. Ni
                     siquiera se manda en el POST (no hay name acá a propósito), así no hace
                     falta confiar en que el servidor lo ignore. --%>
                <input type="text" class="form-control"
                       value="<%= usuarioIntentado == null || usuarioIntentado.getNombreUsuario() == null ? "" : usuarioIntentado.getNombreUsuario() %>" disabled>
                <% } else { %>
                <input type="text" name="nombreUsuario" class="form-control"
                       value="<%= usuarioIntentado == null || usuarioIntentado.getNombreUsuario() == null ? "" : usuarioIntentado.getNombreUsuario() %>" required>
                <% } %>
            </div>
            <div class="form-group">
                <label>Nombres</label>
                <input type="text" name="nombres" class="form-control"
                       value="<%= usuarioIntentado == null || usuarioIntentado.getNombres() == null ? "" : usuarioIntentado.getNombres() %>" required>
            </div>
            <div class="form-group">
                <label>Apellidos</label>
                <input type="text" name="apellidos" class="form-control"
                       value="<%= usuarioIntentado == null || usuarioIntentado.getApellidos() == null ? "" : usuarioIntentado.getApellidos() %>" required>
            </div>
            <div class="form-group">
                <label>Cédula de identidad</label>
                <input type="text" name="cedulaIdentidad" class="form-control"
                       value="<%= usuarioIntentado == null || usuarioIntentado.getCedulaIdentidad() == null ? "" : usuarioIntentado.getCedulaIdentidad() %>" required>
            </div>
            <div class="form-group">
                <label>Perfil</label>
                <%
                    PerfilUsuario perfilActual = usuarioIntentado == null ? null : usuarioIntentado.getPerfilUsuario();
                %>
                <select name="perfilUsuario" class="form-control" <%= editaAsiMismo ? "disabled" : "" %> required>
                    <option value="OFICIAL_CREDITO" <%= perfilActual == PerfilUsuario.OFICIAL_CREDITO ? "selected" : "" %>>Oficial de crédito</option>
                    <option value="GERENTE_ADMINISTRADOR" <%= perfilActual == PerfilUsuario.GERENTE_ADMINISTRADOR ? "selected" : "" %>>Gerente</option>
                    <option value="CAJERO" <%= perfilActual == PerfilUsuario.CAJERO ? "selected" : "" %>>Cajera</option>
                    <%-- D7 (pedido de Joel): perfil nuevo, acceso a todo el sistema. --%>
                    <option value="ADMINISTRADOR" <%= perfilActual == PerfilUsuario.ADMINISTRADOR ? "selected" : "" %>>Administrador</option>
                </select>
                <% if (editaAsiMismo) { %>
                <small class="text-muted">No puede cambiar su propio perfil.</small>
                <% } else if (modoEdicion) { %>
                <small class="text-muted">El cambio de perfil rige desde el próximo inicio de sesión del usuario.</small>
                <% } %>
            </div>
            <% if (!modoEdicion) { %>
            <p class="text-muted"><small>Al guardar, el sistema genera una contraseña temporal que se muestra una sola vez. La persona deberá cambiarla al entrar.</small></p>
            <% } %>
            <button type="submit" class="btn btn-primary"><%= modoEdicion ? "Guardar cambios" : "Crear usuario" %></button>
            <a href="${pageContext.request.contextPath}/usuarios" class="btn btn-default">Cancelar</a>
        </form>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
