<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%!
    /** Bloque 4 (E8, corrección de Joel): resalta la opción del menú donde está el usuario --
     * antes ninguna quedaba marcada como activa y "Panel" parecía resaltado por ser la primera
     * (nav-pills de Bootstrap). Coincide por ruta exacta o por el primer segmento (así
     * "/mora/13/gestion" también resalta "Seguimiento de mora"). */
    private boolean coincideRuta(String rutaActual, String ruta) {
        return rutaActual.equals(ruta) || rutaActual.startsWith(ruta + "/");
    }

    private String claseActiva(String rutaActual, String ruta) {
        return coincideRuta(rutaActual, ruta) ? " active" : "";
    }

    /**
     * V3c (pedido de Joel) + RF38/D1 (13/10/2026, pedido de Joel): una opción del menú -- el
     * bloqueo real lo sigue haciendo FiltroAutorizacion en el servidor; esto solo arma el HTML.
     * RF38 dice "mostrar SOLO las opciones que corresponden a su perfil": sin permiso, no se
     * dibuja nada (antes: un <span> con candado) -- mismas condiciones de siempre, ninguna
     * lógica de permisos nueva.
     */
    private String opcionMenu(String contextPath, String rutaActual, String ruta, String icono, String texto,
            boolean permitido) {
        if (!permitido) {
            return "";
        }
        return "<a href=\"" + contextPath + ruta + "\" class=\"nav-link" + claseActiva(rutaActual, ruta) + "\">"
                + "<i class=\"nav-icon " + icono + "\"></i><p>" + texto + "</p></a>";
    }

    /** Igual que opcionMenu, sin claseActiva -- para los dos accesos directos de "Cancelación"
     * (pedido de Joel: que no se marquen en verde junto con Cobranza/Formalización, que ya
     * tienen su propio resaltado en sus propios módulos). */
    private String opcionMenuSinActiva(String contextPath, String ruta, String icono, String texto,
            boolean permitido) {
        if (!permitido) {
            return "";
        }
        return "<a href=\"" + contextPath + ruta + "\" class=\"nav-link\">"
                + "<i class=\"nav-icon " + icono + "\"></i><p>" + texto + "</p></a>";
    }

    /**
     * V3b/V3c (pedido de Joel) + RF38/D1 (13/10/2026, pedido de Joel): arma el <li> completo de
     * un módulo -- encabezado + sus opciones (ya armadas con opcionMenu/opcionMenuSinActiva). Un
     * solo método para los 8 módulos, en vez de repetir la misma estructura <li>/<a>/<ul> ocho
     * veces con scriptlets intercalados (D-150: la versión con todo HTML directo en la plantilla
     * hacía que _jspService (el método que arma toda la página, compartido con cabecera.jsp vía
     * el include de JSP) creciera tanto que Jasper fallaba al generar el código -- "cannot be
     * resolved to a variable" primero, y errores de sintaxis al achicar el problema a medias
     * después. Juntando todo el HTML de cada módulo en un solo String construido en Java, en vez
     * de muchos fragmentos de plantilla con scriptlets cortos intercalados, el método generado
     * queda chico otra vez.
     * RF38 dice "mostrar SOLO las opciones que corresponden a su perfil": sin ninguna opción
     * habilitada, el módulo entero no se dibuja (antes: encabezado con candado atenuado, igual
     * desplegable) -- verModulo ya es el OR de las mismas condiciones que sus propias opciones,
     * así que alcanza con no dibujar nada acá, sin ninguna lógica de permisos nueva; y una
     * opción vacía (opcionMenu con permitido=false) tampoco deja un <li> suelto. */
    private String moduloMenu(boolean abrir, String icono, String titulo, boolean verModulo, String... opciones) {
        if (!verModulo) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<li class=\"nav-item").append(abrir ? " menu-open" : "").append("\">");
        html.append("<a href=\"#\" class=\"nav-link nav-link-modulo\"><i class=\"nav-icon ").append(icono)
                .append("\"></i><p>").append(titulo).append(' ');
        html.append("<i class=\"right fas fa-angle-left\"></i></p></a><ul class=\"nav nav-treeview\">");
        for (String opcion : opciones) {
            if (!opcion.isEmpty()) {
                html.append("<li class=\"nav-item\">").append(opcion).append("</li>");
            }
        }
        html.append("</ul></li>");
        return html.toString();
    }
%>
<%
    Usuario usuarioMenu = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    boolean esGerente = usuarioMenu != null && usuarioMenu.getPerfilUsuario() == PerfilUsuario.GERENTE_ADMINISTRADOR;
    boolean esOficial = usuarioMenu != null && usuarioMenu.getPerfilUsuario() == PerfilUsuario.OFICIAL_CREDITO;
    boolean esCajero = usuarioMenu != null && usuarioMenu.getPerfilUsuario() == PerfilUsuario.CAJERO;
    // D7 (pedido de Joel): el administrador ve TODAS las opciones del menú, incluidas las de la
    // cajera y el oficial -- se agrega a cada condición existente, GERENTE_ADMINISTRADOR no se toca.
    boolean esAdministrador = usuarioMenu != null && usuarioMenu.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR;
    // Encontrado en la prueba de E3: FiltroAutenticacion ya redirige de vuelta a
    // /cambiar-contrasena ante cualquier otra ruta mientras debe_cambiar_contrasena=true (el
    // filtro sí bloquea la navegación), pero mostrar el menú completo igual confunde -- parece
    // que se puede navegar y después rebota. No se muestra ningún enlace hasta que cambie.
    boolean debeCambiarContrasena = usuarioMenu != null && usuarioMenu.isDebeCambiarContrasena();
    // V3b (causa encontrada por Joel): menu.jsp se ejecuta DESPUÉS del forward del servlet a la
    // vista, y en un forward request.getRequestURI() devuelve la ruta del JSP
    // (/WEB-INF/vistas/panel.jsp), no la ruta original (/panel) -- por eso claseActiva(...)
    // nunca coincidía. "javax.servlet.forward.request_uri" es el atributo estándar que guarda la
    // URI original antes del forward; si es null (acceso directo, sin forward), se usa
    // getRequestURI() como hasta ahora.
    String uriOriginalMenu = (String) request.getAttribute("javax.servlet.forward.request_uri");
    if (uriOriginalMenu == null) {
        uriOriginalMenu = request.getRequestURI();
    }
    String rutaActualMenu = uriOriginalMenu.substring(request.getContextPath().length());
    String cp = request.getContextPath();

    // V3b (pedido de Joel): agrupa las opciones existentes en los 8 módulos del anteproyecto,
    // como menú desplegable (treeview de AdminLTE) -- cada condición de perfil de abajo es
    // EXACTAMENTE la misma que ya tenía cada opción.
    //
    // V3c (pedido de Joel): el candado es SOLO visual -- el bloqueo real lo sigue haciendo
    // FiltroAutorizacion en el servidor, que no se toca. Ahora TODOS los módulos y TODAS las
    // opciones se muestran siempre (moduloMenu/opcionMenu deciden, por opción, si es <a> o
    // <span> con candado); estas variables se usan para: 1) si el módulo no tiene NINGUNA opción
    // habilitada, su encabezado se ve atenuado con un candado (igual se puede desplegar), y 2) si
    // arranca abierto. El administrador no ve ningún candado porque esAdministrador ya está en
    // el OR de todas las condiciones (de cada opción Y de cada módulo).
    boolean verAprobacion = esGerente || esAdministrador;
    boolean verFormalizacion = esOficial || esGerente || esAdministrador;
    boolean verDesembolsoCaja = esCajero || esGerente || esAdministrador;
    boolean verCobranzaMora = esCajero || esOficial || esGerente || esAdministrador;
    // "Cancelación" (pedido de Joel): dos accesos directos nuevos a pantallas ya existentes
    // (Cobranza y Formalización), con la MISMA condición que esas pantallas -- no agregan ningún
    // permiso nuevo, solo otra puerta de entrada a los mismos lugares.
    boolean verCobranzaAnticipada = esCajero || esAdministrador;
    boolean verDevolucionPagare = esOficial || esGerente || esAdministrador;
    boolean verInformes = esOficial || esGerente || esAdministrador;
    boolean verSeguridad = esGerente || esAdministrador;

    boolean abrirClientesSolicitudes = coincideRuta(rutaActualMenu, "/cotizador")
            || coincideRuta(rutaActualMenu, "/clientes") || coincideRuta(rutaActualMenu, "/solicitudes");
    boolean abrirAprobacion = coincideRuta(rutaActualMenu, "/aprobaciones");
    boolean abrirFormalizacion = coincideRuta(rutaActualMenu, "/formalizacion");
    boolean abrirDesembolsoCaja = coincideRuta(rutaActualMenu, "/caja") || coincideRuta(rutaActualMenu, "/desembolso");
    boolean abrirCobranzaMora = coincideRuta(rutaActualMenu, "/cobranza") || coincideRuta(rutaActualMenu, "/mora")
            || coincideRuta(rutaActualMenu, "/reversiones");
    boolean abrirInformes = coincideRuta(rutaActualMenu, "/prestamos") || coincideRuta(rutaActualMenu, "/reportes");
    boolean abrirSeguridad = coincideRuta(rutaActualMenu, "/usuarios") || coincideRuta(rutaActualMenu, "/parametros");
%>
<nav class="mt-2">
    <ul class="nav nav-pills nav-sidebar flex-column" data-widget="treeview" role="menu">
        <% if (!debeCambiarContrasena) { %>
        <%-- Panel: siempre habilitado, para cualquier perfil -- nunca lleva candado. --%>
        <li class="nav-item">
            <a href="${pageContext.request.contextPath}/panel" class="nav-link<%= claseActiva(rutaActualMenu, "/panel") %>">
                <i class="nav-icon fas fa-home"></i>
                <p>Panel</p>
            </a>
        </li>

        <%-- 1. Clientes y solicitudes: Cotizador y Clientes no tienen condición propia (siempre
             habilitados), así que el módulo nunca queda sin ninguna opción habilitada. --%>
        <%= moduloMenu(abrirClientesSolicitudes, "fas fa-users", "Clientes y solicitudes", true,
                opcionMenu(cp, rutaActualMenu, "/cotizador", "fas fa-calculator", "Cotizador", true),
                opcionMenu(cp, rutaActualMenu, "/clientes", "fas fa-address-card", "Clientes", true),
                opcionMenu(cp, rutaActualMenu, "/solicitudes", "fas fa-file-signature", "Solicitudes", esOficial || esAdministrador)) %>

        <%-- 2. Aprobación. --%>
        <%= moduloMenu(abrirAprobacion, "fas fa-stamp", "Aprobación", verAprobacion,
                opcionMenu(cp, rutaActualMenu, "/aprobaciones", "fas fa-stamp", "Aprobaciones", esGerente || esAdministrador)) %>

        <%-- 3. Formalización. E5 (D-68): oficial y gerente operan esta pantalla, a diferencia de
             Solicitudes/Aprobaciones. --%>
        <%= moduloMenu(abrirFormalizacion, "fas fa-file-signature", "Formalización", verFormalizacion,
                opcionMenu(cp, rutaActualMenu, "/formalizacion", "fas fa-file-contract", "Formalización", esOficial || esGerente || esAdministrador)) %>

        <%-- 4. Desembolso y caja. E6 (P07/P08): ABRIR_CERRAR_CAJA es TOTAL para la cajera y
             CONSULTA para el gerente. --%>
        <%= moduloMenu(abrirDesembolsoCaja, "fas fa-cash-register", "Desembolso y caja", verDesembolsoCaja,
                opcionMenu(cp, rutaActualMenu, "/caja", "fas fa-cash-register", "Caja", esCajero || esGerente || esAdministrador),
                opcionMenu(cp, rutaActualMenu, "/desembolso", "fas fa-hand-holding-usd", "Desembolsos", esCajero || esAdministrador)) %>

        <%-- 5. Cobranza y mora. E7 (D-77 punto 1): COBRAR es TOTAL solo para la cajera. Bloque 4
             (E8, decisión 4): /mora es de oficial y gerente -- la cajera no la tiene. E9/Bloque 5
             (PO-13): AUTORIZAR_REVERSION_O_DIFERENCIA es TOTAL solo del gerente -- la cajera
             solicita una reversión desde el botón en la propia ficha de cobranza, no tiene esta
             pantalla en el menú. --%>
        <%= moduloMenu(abrirCobranzaMora, "fas fa-hand-holding-usd", "Cobranza y mora", verCobranzaMora,
                opcionMenu(cp, rutaActualMenu, "/cobranza", "fas fa-coins", "Cobranza", esCajero || esAdministrador),
                opcionMenu(cp, rutaActualMenu, "/mora", "fas fa-user-clock", "Seguimiento de mora", esOficial || esGerente || esAdministrador),
                opcionMenu(cp, rutaActualMenu, "/reversiones", "fas fa-undo", "Reversiones", esGerente || esAdministrador)) %>

        <%-- 6. Cancelación (pedido de Joel): dos accesos directos nuevos, sin claseActiva a
             propósito (para que no se marquen en verde junto con Cobranza o Formalización --
             esas dos ya tienen su propio resaltado en sus módulos, manejado por el script de
             V3h en pie.jsp). "Devolución del pagaré" apunta a /formalizacion#cierre-documental
             -- id agregado en formalizacion/lista.jsp sobre el encabezado "Préstamos finalizados
             — cierre documental pendiente". RF38/D3 (13/10/2026, pedido de Joel): "Cancelación
             anticipada" apunta a /cobranza?origen=cancelacion-anticipada -- mismo patrón que
             "Devolución del pagaré" (una pantalla compartida, resaltado corregido por el mismo
             script de pie.jsp), para que busqueda.jsp (/cobranza) muestre un aviso propio en vez
             de abrir como si fuera "Cobranza" sin distinción. --%>
        <%= moduloMenu(false, "fas fa-check-circle", "Cancelación", verCobranzaAnticipada || verDevolucionPagare,
                opcionMenuSinActiva(cp, "/cobranza?origen=cancelacion-anticipada", "fas fa-coins", "Cancelación anticipada", verCobranzaAnticipada),
                opcionMenuSinActiva(cp, "/formalizacion#cierre-documental", "fas fa-file-contract", "Devolución del pagaré", verDevolucionPagare)) %>

        <%-- 7. Informes. B1 (Bloque 7/E10, pedido de Joel): mismo permiso que Reportes
             (VER_REPORTES) -- gerente y oficial, la cajera no la tiene. Bloque 4 (E8, decisión
             2): /reportes es TOTAL para gerente, CONSULTA para oficial. B208 (15/11/2026): este
             comentario decía "4 de los 19 sub-reportes" y había quedado viejo DOS veces -- son
             21 reportes y el oficial ve 5 (Vencimientos, Estado de cuenta, Consultas al buró,
             Morosidad y Gestiones), contados sobre las tarjetas de reportes/indice.jsp, que es
             donde se decide. El resto lo acota la propia pantalla. --%>
        <%= moduloMenu(abrirInformes, "fas fa-chart-bar", "Informes", verInformes,
                opcionMenu(cp, rutaActualMenu, "/prestamos", "fas fa-money-check-alt", "Préstamos", esOficial || esGerente || esAdministrador),
                opcionMenu(cp, rutaActualMenu, "/reportes", "fas fa-chart-bar", "Reportes", esOficial || esGerente || esAdministrador)) %>

        <%-- 8. Seguridad. D-151 (pedido de Joel): "Usuarios" queda exclusivo del administrador
             (el gerente ya no tiene acceso); "Parámetros" sigue para el gerente, que ahora solo
             consulta (sin los botones de modificar, ocultos en la propia pantalla). --%>
        <%= moduloMenu(abrirSeguridad, "fas fa-shield-alt", "Seguridad", verSeguridad,
                opcionMenu(cp, rutaActualMenu, "/usuarios", "fas fa-users", "Usuarios", esAdministrador),
                opcionMenu(cp, rutaActualMenu, "/parametros", "fas fa-sliders-h", "Parámetros", esGerente || esAdministrador)) %>
        <% } %>
    </ul>
</nav>
