<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesUsuarios" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    @SuppressWarnings("unchecked")
    List<Usuario> listaUsuarios = (List<Usuario>) request.getAttribute("usuarios");
    Usuario usuarioSesionLista = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    TotalesUsuarios totalesUsuariosLista = (TotalesUsuarios) request.getAttribute("totalesUsuarios");
%>
<h1>Usuarios</h1>

<%-- B214 (pedido de Joel, 15/11/2026): la pantalla ofrecia «Bloquear» e «Inactivar» una al lado
     de la otra, las dos en ambar (B133) y sin decir en que se diferencian. Las tres diferencias
     que se nombran aca estan verificadas en el codigo, no son una interpretacion:
     (a) inactivar sella usuarios.fecha_inactivacion (UsuarioDAO.actualizarEstadoCuenta) y
         bloquear no;
     (b) «Restablecer contraseña» desbloquea una cuenta BLOQUEADA en el mismo paso, y a una
         INACTIVA solo le cambia la contrasena, sin reactivarla
         (ServicioUsuarios.restablecerContrasena);
     (c) BLOQUEADA es ademas el estado en que el sistema deja una cuenta tras
         seguridad.intentos_login fallidos; ese bloqueo automatico se libera solo despues de
         seguridad.minutos_bloqueo, pero el MANUAL no se libera nunca solo
         (ServicioAutenticacion.debeDesbloquearPorTiempo). --%>
<p class="text-muted">
    <i class="fas fa-info-circle" aria-hidden="true"></i>
    <strong>«Bloquear»</strong> suspende el acceso de forma temporal: es el mismo estado en que
    queda una cuenta tras varios intentos fallidos.
    <strong>«Inactivar»</strong> es la baja definitiva: queda registrada la fecha de baja y
    restablecerle la contraseña no la reactiva.
    Las dos se deshacen con <strong>«Reactivar»</strong>.
</p>

<a href="${pageContext.request.contextPath}/usuarios/nuevo" class="btn btn-primary mb-3">
    <i class="fas fa-plus"></i> Nuevo usuario
</a>
<%-- B222 (pedido de Joel, 15/11/2026): Usuarios era el último listado con los totales en una
     tarjeta DEBAJO de la tabla (con los conteos pegados con &nbsp;) y con los exportes arriba, al
     lado de la acción primaria. Ahora sigue el molde de los listados ya cerrados: el conteo a la
     izquierda de una barra de resultados, los exportes a la derecha, y arriba queda sola la acción
     primaria. Mismo marcado que parametros/historial.jsp y los reportes. --%>
<div class="d-flex justify-content-between align-items-center flex-wrap listado-vivo-barra-resultados">
    <%-- B222, segunda vuelta (prueba de Joel, 15/11/2026): el resumen salia con un espacio ANTES
         de cada coma ("Administrador: 1 , Cajera: 1 ,"). No era el texto: el separador se imprimia
         con una expresion por vuelta del bucle, y el salto de linea y la sangria que quedan ENTRE
         las expresiones son texto de plantilla, que el navegador colapsa en un espacio. Ahora la
         lista se arma entera en el scriptlet y la pantalla imprime un solo valor: no hay plantilla
         en el medio donde se pueda meter un espacio. --%>
    <%
        String conteosPorPerfil = "";
        String conteosPorEstado = "";
        if (totalesUsuariosLista != null) {
            StringBuilder armadoPerfil = new StringBuilder();
            for (Map.Entry<String, Integer> entrada : totalesUsuariosLista.getCantidadPorPerfil().entrySet()) {
                if (armadoPerfil.length() > 0) {
                    armadoPerfil.append(", ");
                }
                armadoPerfil.append(entrada.getKey()).append(": ").append(entrada.getValue());
            }
            conteosPorPerfil = armadoPerfil.toString();
            StringBuilder armadoEstado = new StringBuilder();
            for (Map.Entry<String, Integer> entrada : totalesUsuariosLista.getCantidadPorEstado().entrySet()) {
                if (armadoEstado.length() > 0) {
                    armadoEstado.append(", ");
                }
                armadoEstado.append(entrada.getKey()).append(": ").append(entrada.getValue());
            }
            conteosPorEstado = armadoEstado.toString();
        }
    %>
    <p class="text-muted mb-2">
        <% if (totalesUsuariosLista != null) { %>
        <%= FormateadorNumeros.comoCantidad(totalesUsuariosLista.getCantidadTotal()) %>
        usuario<%= totalesUsuariosLista.getCantidadTotal() == 1 ? "" : "s" %><%
            %><% if (!conteosPorPerfil.isEmpty()) { %> &middot; <strong>Perfil:</strong> <%= conteosPorPerfil %><% } %><%
            %><% if (!conteosPorEstado.isEmpty()) { %> &middot; <strong>Estado:</strong> <%= conteosPorEstado %><% } %>
        <% } %>
    </p>
    <div class="barra-resultados-acciones mb-2">
        <a class="btn btn-outline-secondary btn-sm" target="_blank" href="${pageContext.request.contextPath}/usuarios/informe">
            <i class="fas fa-file-pdf"></i> PDF</a>
        <a class="btn btn-outline-secondary btn-sm" href="${pageContext.request.contextPath}/usuarios/exportar">
            <i class="fas fa-file-excel"></i> Excel/CSV</a>
    </div>
</div>

<div class="card">
    <div class="card-body table-responsive p-0">
        <table class="table table-striped table-hover">
            <thead>
            <tr>
                <th>Usuario</th>
                <th>Nombre</th>
                <th>C.I.</th>
                <th>Perfil</th>
                <th>Estado</th>
                <th>Debe cambiar contraseña</th>
                <th>Último acceso</th>
                <th>Acciones</th>
            </tr>
            </thead>
            <tbody>
            <% if (listaUsuarios != null) {
                for (Usuario fila : listaUsuarios) { %>
            <tr>
                <td><%= fila.getNombreUsuario() %></td>
                <td><%= fila.getNombres() %> <%= fila.getApellidos() %></td>
                <%-- B133 (11/11/2026): la cédula con puntos, como en Clientes y en la
                     auditoría. Era el único listado que la imprimía cruda. --%>
                <td><%= FormateadorTexto.comoCedulaConPuntos(fila.getCedulaIdentidad()) %></td>
                <td><%= fila.getPerfilUsuario().getEtiqueta() %></td>
                <td><%= fila.getEstadoCuenta().getEtiqueta() %></td>
                <td><%= fila.isDebeCambiarContrasena() ? "Sí" : "No" %></td>
                <td><%= fila.getFechaUltimoAcceso() == null ? "Nunca" : FormateadorFechas.comoDdMmAaaaHhMm(fila.getFechaUltimoAcceso()) %></td>
                <td>
                    <%-- V3 (encontrado probando D-151): los botones se agrupan en un contenedor
                         flex-wrap (sgcpc-tema.css) para que fluyan en una línea compacta en vez
                         de apilarse uno por renglón -- ninguna acción, ruta ni formulario POST
                         cambia. --%>
                    <div class="acciones-usuario">
                    <%-- Plan "Editar usuario" (aprobado por Joel), punto 7: visible en TODAS las
                         filas, incluida la propia (a diferencia de Bloquear/Inactivar/
                         Restablecer, que siguen ocultos ahí) -- mismo estilo que "Ver/editar"
                         en Clientes (docs/GUIA_VISUAL.md). --%>
                    <a href="${pageContext.request.contextPath}/usuarios/editar?idUsuario=<%= fila.getIdUsuario() %>"
                       class="btn btn-sm btn-info">Editar</a>
                    <% boolean esUnoMismo = usuarioSesionLista != null && usuarioSesionLista.getIdUsuario().equals(fila.getIdUsuario());
                       if (!esUnoMismo) { %>
                    <% if (fila.getEstadoCuenta() == EstadoCuenta.BLOQUEADA || fila.getEstadoCuenta() == EstadoCuenta.INACTIVA) { %>
                    <form method="post" action="${pageContext.request.contextPath}/usuarios/reactivar" class="d-inline"
                          data-confirmar-titulo="¿Reactivar a &quot;<%= fila.getNombreUsuario() %>&quot;?" data-confirmar="" data-confirmar-boton="Sí, reactivar">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <input type="hidden" name="idUsuario" value="<%= fila.getIdUsuario() %>">
                        <button type="submit" class="btn btn-sm btn-success">Reactivar</button>
                    </form>
                    <% } else { %>
                    <form method="post" action="${pageContext.request.contextPath}/usuarios/bloquear" class="d-inline"
                          data-confirmar-titulo="¿Bloquear a &quot;<%= fila.getNombreUsuario() %>&quot;?" data-confirmar="Suspensión temporal del acceso: no va a poder ingresar hasta que lo reactiven. Es el mismo estado en que queda una cuenta tras varios intentos fallidos."
                          data-confirmar-boton="Sí, bloquear">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <input type="hidden" name="idUsuario" value="<%= fila.getIdUsuario() %>">
                        <%-- B133: "Bloquear" e "Inactivar" son lo mismo para el color -- las dos restringen el
                             acceso y las dos se deshacen con "Reactivar" --, así que las dos van en el
                             ámbar de advertencia de GUIA_VISUAL.md. Antes "Inactivar" usaba el gris oscuro
                             neutro y parecía la más inofensiva de las dos, que es justo al revés.
                             El contorno (`btn-outline-warning`) se probó y se descartó MIDIENDO: deja el
                             rótulo ámbar sobre blanco en 1,46:1, el peor contraste de la pantalla. El
                             ámbar sólido lleva texto oscuro y se lee. --%>
                        <button type="submit" class="btn btn-sm btn-warning">Bloquear</button>
                    </form>
                    <form method="post" action="${pageContext.request.contextPath}/usuarios/inactivar" class="d-inline"
                          data-confirmar-titulo="¿Inactivar a &quot;<%= fila.getNombreUsuario() %>&quot;?" data-confirmar="Baja definitiva: queda registrada la fecha de baja, y restablecerle la contraseña no la reactiva. Se deshace con «Reactivar»."
                          data-confirmar-boton="Sí, inactivar">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <input type="hidden" name="idUsuario" value="<%= fila.getIdUsuario() %>">
                        <button type="submit" class="btn btn-sm btn-warning">Inactivar</button>
                    </form>
                    <% } %>
                    <form method="post" action="${pageContext.request.contextPath}/usuarios/restablecer" class="d-inline"
                          data-confirmar-titulo="¿Restablecer la contraseña de &quot;<%= fila.getNombreUsuario() %>&quot;?" data-confirmar-foco="cancelar" data-confirmar="Se genera una contraseña temporal."
                          data-confirmar-boton="Sí, restablecer">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <input type="hidden" name="idUsuario" value="<%= fila.getIdUsuario() %>">
                        <%-- B133: el botón dice la operación real, no un verbo suelto (B96). El title decía
                             "Restablecer contraseña" desde siempre: ahora lo dice el rótulo. --%>
                        <button type="submit" class="btn btn-sm btn-info">Restablecer contraseña</button>
                    </form>
                    <% } else { %>
                    <span class="text-muted">(su propio usuario)</span>
                    <% } %>
                    </div>
                </td>
            </tr>
            <% } } %>
            </tbody>
        </table>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
