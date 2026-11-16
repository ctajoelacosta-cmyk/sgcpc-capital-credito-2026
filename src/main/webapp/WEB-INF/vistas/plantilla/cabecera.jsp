<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.Reloj" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.TextStyle" %>
<%@ page import="java.util.Locale" %>
<%
    Usuario usuarioSesion = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    String nombreComercial = (String) request.getAttribute("nombreComercial");
    if (nombreComercial == null || nombreComercial.isEmpty()) {
        nombreComercial = "Capital Crédito";
    }
    request.setAttribute("nombreComercialResuelto", nombreComercial);
    // Trae a request el mensaje flash de sesion (si lo hay, de un sendRedirect
    // anterior) con los mismos nombres "mensajeExito"/"mensajeError" que ya
    // usan las pantallas con forward directo; se muestra una sola vez mas abajo.
    MensajesFlash.trasladarARequest(request);

    // V4 (pedido de Joel), punto 4: fecha del SERVIDOR (Reloj.hoy(), no la de la PC del
    // usuario), en español -- "Martes, 6 de octubre de 2026". FormateadorFechas.comoFechaLarga
    // ya da "6 de octubre de 2026"; el nombre del día no existe ahí (esa clase es de uso general
    // y no lo necesitaba hasta ahora), así que se arma acá mismo con
    // DayOfWeek.getDisplayName(FULL, Locale("es")) -- que en español ya da el nombre en
    // minúscula ("martes"), por eso se capitaliza solo la primera letra.
    LocalDate fechaHoyServidor = Reloj.hoy();
    String nombreDiaSemana = fechaHoyServidor.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es"));
    String fechaHoyLarga = Character.toUpperCase(nombreDiaSemana.charAt(0)) + nombreDiaSemana.substring(1)
            + ", " + FormateadorFechas.comoFechaLarga(fechaHoyServidor);

    // V4, punto 5: iniciales para el círculo del menú de usuario (ej. "RB" para "Ramón
    // Benítez") -- primera letra de nombres + primera letra de apellidos, tal como están
    // guardados (si alguno viene vacío, usa lo que haya).
    String inicialesUsuario = "";
    if (usuarioSesion != null) {
        String nombresUsuario = usuarioSesion.getNombres() == null ? "" : usuarioSesion.getNombres().trim();
        String apellidosUsuario = usuarioSesion.getApellidos() == null ? "" : usuarioSesion.getApellidos().trim();
        if (!nombresUsuario.isEmpty()) {
            inicialesUsuario += Character.toUpperCase(nombresUsuario.charAt(0));
        }
        if (!apellidosUsuario.isEmpty()) {
            inicialesUsuario += Character.toUpperCase(apellidosUsuario.charAt(0));
        }
    }
%>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= nombreComercial %></title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/recursos/imagenes/favicon_64.png">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/fuentes/fuente-source-sans-pro.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/fontawesome/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/adminlte/dist/css/adminlte.min.css">
    <%-- V3a (pedido de Joel): estilo propio de la barra lateral -- SOLO visual, no toca Java,
         rutas ni las condiciones de perfil de menu.jsp (esOficial/esGerente/etc.), ni el orden o
         la agrupación de las opciones (eso es el paso siguiente). Colores tomados del logo. --%>
    <style>
        /* Montserrat local (mismos archivos .woff2 que ya usa acceso.jsp desde V1f, licencia
           OFL) -- declarado de nuevo acá porque cada página compila su propio <style>, no hay
           herencia entre JSP. 600 para los ítems normales, 700 para los encabezados de módulo
           (V3b). */
        @font-face {
            font-family: 'Montserrat';
            font-style: normal;
            font-weight: 600;
            font-display: swap;
            src: url('${pageContext.request.contextPath}/recursos/fuentes/montserrat/montserrat-600.woff2') format('woff2');
        }

        @font-face {
            font-family: 'Montserrat';
            font-style: normal;
            font-weight: 700;
            font-display: swap;
            src: url('${pageContext.request.contextPath}/recursos/fuentes/montserrat/montserrat-700.woff2') format('woff2');
        }

        /* V3i (pedido de Joel), punto 1: ancho de la barra de 250px (el de AdminLTE) a 280px en
           escritorio -- se pisa cada selector de adminlte.min.css que depende de ese ancho, con
           los mismos :not()/combinaciones de clases que usa el original (confirmado leyendo esa
           hoja de estilos), para no ensanchar configuraciones que este sistema no usa
           (sidebar-mini-md/-xs, layout-top-nav, nav-legacy, etc. -- nuestro <body> es siempre
           "hold-transition sidebar-mini layout-fixed", ver más abajo). Las transiciones
           (margin-left/width) ya las define AdminLTE en esas mismas reglas -- no hace falta
           repetirlas acá, solo cambia el valor final al que animan.

           V3j (arreglo de Joel, bug de V3i): TODAS estas reglas se agregan con body:not(.sidebar-collapse)
           -- antes no tenían esa condición y, al apretar ☰ (que agrega sidebar-collapse al body),
           el !important de acá le ganaba a las reglas propias de AdminLTE para el modo "solo
           íconos" (width:4.6rem en .main-sidebar/.brand-link, margin-left:4.6rem en el contenido)
           aunque esas reglas fueran más específicas -- !important siempre gana sobre
           especificidad, sin importar cuál venga primero en el archivo. Record también había una
           regla ".sidebar-collapse .main-sidebar{margin-left:-280px!important}" (pensada para
           "ocultar del todo", copiando el patrón mobile) que de hecho nunca hace falta: con
           sidebar-mini siempre presente en el body, AdminLTE ya resuelve el modo colapsado como
           una columna angosta (nunca oculta del todo en escritorio) con su propia regla
           ".sidebar-mini.sidebar-collapse .main-sidebar{margin-left:0;width:4.6rem}", más
           específica que la genérica de "ocultar" -- esa regla de acá de hecho era la causa del
           hueco blanco que reportó Joel (ganaba por !important, dejaba la barra en margin-left:
           -280px pero el contenido seguía con margin-left:280px, que tampoco bajaba a 4.6rem por
           el mismo motivo) y se sacó directamente. El ancho mini en sí (barra colapsada, sin
           pasar el mouse) sigue siendo el de siempre, AdminLTE decide ese valor sin que esto
           intervenga; el hover que la expande sí pasa a 280px, como pidió Joel (reglas de abajo,
           sin cambios). Pantallas < 992px: ese comportamiento lo decide AdminLTE con su propio
           JS/breakpoints -- esta regla tampoco lo toca, porque .sidebar-collapse ahí ya no
           importa (AdminLTE oculta la barra con display:none!important en ese ancho). */
        body:not(.sidebar-collapse) .main-sidebar {
            width: 280px !important;
        }

        body:not(.sidebar-collapse).layout-fixed .brand-link {
            width: 280px !important;
        }

        body:not(.sidebar-collapse):not(.sidebar-mini-md):not(.sidebar-mini-xs):not(.layout-top-nav) .content-wrapper,
        body:not(.sidebar-collapse):not(.sidebar-mini-md):not(.sidebar-mini-xs):not(.layout-top-nav) .main-footer,
        body:not(.sidebar-collapse):not(.sidebar-mini-md):not(.sidebar-mini-xs):not(.layout-top-nav) .main-header {
            margin-left: 280px !important;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand).sidebar-focused,
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):hover {
            width: 280px !important;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand).sidebar-focused .brand-link,
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):hover .brand-link,
        .sidebar-mini.sidebar-collapse.layout-fixed .main-sidebar:hover .brand-link {
            width: 280px !important;
        }

        /* V3j (pedido de Joel), punto 2: en modo "solo íconos" (colapsada, sin pasar el mouse ni
           foco) -- logo centrado, candado de módulo y flechita ocultos (viven dentro del mismo
           <p> que AdminLTE ya esconde en este modo, pero se repite acá explícito por las dudas,
           ya que son position:absolute) y el submenú de un módulo que haya quedado abierto antes
           de colapsar la barra se oculta (por defecto AdminLTE NO lo hace -- su propia regla
           ".sidebar-mini.sidebar-collapse .menu-open>.nav-treeview" lo deja visible aunque esté
           colapsada, confirmado leyendo adminlte.min.css). Todo esto vuelve a mostrarse en cuanto
           :hover/.sidebar-focused expande la barra a 280px (reglas de arriba), porque esta
           regla pisa solo el estado SIN expandir. */
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .brand-link {
            display: flex;
            align-items: center;
            justify-content: center;
        }

        /* V13 (corrección de Joel): display:none dejaba ver una "C" suelta -- AdminLTE tiene su
           PROPIA animación (fadeIn/fadeOut con animation-fill-mode:both) sobre este mismo
           elemento para mostrarlo/ocultarlo en este modo, y competía con nuestra regla,
           dejándolo a medio camino en algún punto de la transición. Se anula esa animación
           (animation:none, con !important porque la de AdminLTE también lo usa) y se controla
           todo con opacity/width propios: oculto de una al colapsar (sin transición, no hace
           falta demora para desaparecer) y, al expandir con :hover/.sidebar-focused, recién
           visible .2s después (transition-delay, ver la regla de abajo) -- para cuando se
           empieza a ver, el ancho de la barra (que tarda .3s en llegar a 280px) ya casi terminó,
           así nunca se ve el texto apretado ni cortado a la mitad. */
        .main-sidebar.sidebar-dark-primary .brand-text {
            opacity: 1;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .brand-text {
            opacity: 0 !important;
            width: 0 !important;
            margin-left: 0 !important;
            animation: none !important;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand).sidebar-focused .brand-text,
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):hover .brand-text {
            opacity: 1 !important;
            width: auto !important;
            animation: none !important;
            transition: opacity .2s ease .2s;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .brand-image {
            float: none;
            margin: 0;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .candado-modulo,
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .nav-link-modulo .right {
            display: none;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .menu-open > .nav-treeview {
            display: none !important;
        }

        /* 1. Fondo de la barra: azul del logo, con un degradé muy suave hacia abajo. La zona de
           la marca, un tono más oscuro, con una línea inferior fina. */
        .main-sidebar.sidebar-dark-primary {
            background: linear-gradient(180deg, #0C325C 0%, #0A2A4E 100%) !important;
        }

        .main-sidebar.sidebar-dark-primary .brand-link {
            background-color: #0A2A4E;
            border-bottom: 1px solid rgba(255, 255, 255, .08);
        }

        /* V3g (pedido de Joel): cada módulo en su propio recuadro -- se usa :has() para
           distinguir el <li> de un módulo (tiene un .nav-treeview adentro) del de Panel (que no
           lo tiene y no debe llevar recuadro), sin tocar menu.jsp para agregar una clase aparte.
           El recuadro envuelve el encabezado Y sus opciones solo: como el <li> se achica/agranda
           solo según su contenido real (el .nav-treeview colapsado mide 0 cuando el módulo está
           cerrado), el fondo/borde siempre terminan justo donde termina lo visible, sin nada
           extra. El margen es solo vertical (separación entre módulos, bajado a 5px en V3i): el
           horizontal ya lo da el margin:8px del encabezado y las opciones de las reglas de abajo,
           así el contenido queda con su propio respiro adentro del recuadro. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item:has(> .nav-treeview) {
            background: rgba(255, 255, 255, .03);
            border: 1px solid rgba(255, 255, 255, .08);
            border-radius: 10px;
            margin: 0 0 5px;
            transition: border-color .2s ease, box-shadow .2s ease;
        }

        /* "Dónde estoy" (pedido de Joel): el módulo que contiene la pantalla actual (.menu-open
           por tener algo activo adentro -- lo decide menu.jsp igual que antes, o el script de
           V3h en pie.jsp para el caso especial de #cierre-documental) lleva su recuadro con
           borde sólido y una sombra verde tenue PERMANENTE (no solo al pasar el mouse). V3i: el
           padding-bottom (para que la última opción no quede pegada al borde) se movió acá, solo
           para el estado ABIERTO -- un módulo CERRADO (el caso normal, los 8 a la vez) ya no
           suma esos 4px de más, para ayudar a que todo entre sin scroll en 1366×768 (punto 3,
           pedido de Joel). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item.menu-open:has(> .nav-treeview) {
            border-color: #1B9B6A;
            box-shadow: 0 0 10px rgba(27, 155, 106, .18);
            padding-bottom: 4px;
        }

        /* 2. Ítems normales: texto e ícono, Montserrat 600. 5. Margen lateral de ~8px para que
           el resaltado (hover/activo) no toque los bordes de la barra. V3b (pedido de Joel):
           color de los ítems INACTIVOS -- #E2E8F0 en vez de blanco al 80% (el ítem activo, más
           abajo, tiene su propio color fijo en #fff que gana por especificidad, así que este
           cambio solo se nota en los demás). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link {
            box-sizing: border-box;
            margin: 2px 8px;
            border: 1px solid transparent;
            border-radius: 8px;
            color: #E2E8F0;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 14px;
            font-weight: 600;
            outline: none;
            transition: background-color .2s ease, border-color .2s ease, box-shadow .2s ease, color .2s ease;
        }

        /* V3d (pedido de Joel): sin anillo ni sombra de foco, ni con mouse ni con teclado --
           para teclado alcanza con el mismo fondo de :hover/:focus de la regla de arriba. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link:focus,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link:focus-visible {
            outline: none;
            box-shadow: none;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link .nav-icon {
            color: rgba(226, 232, 240, .85);
            transition: color .2s ease, transform .2s ease;
        }

        /* 3./V3g: hover -- fondo y borde verdes suaves, sombra tenue, texto blanco; el ícono se
           corre 2px a la derecha (regla de abajo). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item:hover > .nav-link,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link:focus {
            background-color: rgba(27, 155, 106, .12) !important;
            border-color: rgba(27, 155, 106, .45) !important;
            box-shadow: 0 0 12px rgba(27, 155, 106, .25) !important;
            color: #fff !important;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item:hover > .nav-link .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link:focus .nav-icon {
            color: rgba(255, 255, 255, .9);
            transform: translateX(2px);
        }

        /* 4./V3g "dónde estoy": ítem activo (incluido Panel) -- fondo verde más marcado (.30,
           antes .20), texto blanco EN NEGRITA, ícono verde claro, barrita verde de 3px a la
           izquierda (border-left, no un elemento aparte: así no se desalinea con el redondeado)
           y bordes redondeados -- se quita el azul/sombra que pone AdminLTE por defecto. Va
           DESPUÉS del hover a propósito, para ganarle si el mouse pasa por encima del ítem que
           ya está activo (se mantiene verde, no parpadea). Esta clase "active" la pone
           claseActiva(...) en menu.jsp (ruta real) o el script de V3h en pie.jsp (el caso de
           #cierre-documental) -- a esta regla le da igual quién la puso. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active:hover,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active:focus {
            background-color: rgba(27, 155, 106, .30) !important;
            color: #fff !important;
            font-weight: 700 !important;
            border-left: 3px solid #1B9B6A !important;
            border-radius: 0 8px 8px 0 !important;
            box-shadow: none !important;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active:hover .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.active:focus .nav-icon {
            color: #34D399 !important;
        }

        /* V3b (pedido de Joel): encabezado de módulo -- Montserrat 700, 13px (el resto de las
           propiedades, como el color base #E2E8F0 y el margen de 8px, ya las pone la regla
           general de arriba, que también matchea esta <a> -- acá solo se pisa lo distinto). La
           flechita (i.right) es la que ya trae AdminLTE: gira sola con .menu-open/.menu-is-opening,
           no hace falta nada más para eso. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.nav-link-modulo {
            font-weight: 700;
            font-size: 13px;
            outline: none;
        }

        /* V3c/V3d (pedido de Joel): el encabezado de un módulo mostraba el recuadro de foco del
           navegador al hacer clic. Se probaron :focus-visible solo y después
           :focus:not(:focus-visible) -- ninguno alcanzó porque en un <a href="#"> (no un
           <button>) varios navegadores SÍ consideran "focus-visible" un clic de mouse, y
           :focus:not(:focus-visible) nunca se aplica justo cuando :focus-visible ya matcheó (son
           excluyentes entre sí por definición, no se complementan). Pedido de Joel: sin anillo
           ni con mouse ni con teclado -- directo :focus (sin condicionar a :focus-visible), y
           para teclado alcanza con el mismo fondo de :hover que ya pone la regla general de
           arriba (también matchea :focus, más abajo en el archivo). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.nav-link-modulo:focus {
            outline: none;
            box-shadow: none;
        }

        /* Encabezado del módulo ABIERTO: texto blanco, ícono verde claro -- el módulo en sí no
           se pinta de verde (ninguna "active"), solo su encabezado cambia de color. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item.menu-open > .nav-link.nav-link-modulo {
            color: #fff;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item.menu-open > .nav-link.nav-link-modulo .nav-icon {
            color: #34D399;
        }

        /* V3b/V3d/V3e: opciones DENTRO de un módulo -- mismo color base, hover y activo verde que
           las de arriba, un poco más chicas (13px) y un poco más indentadas para que se note que
           son de un módulo (6px extra a la izquierda -- bajado de 12px en V3e: con 12px,
           "Cancelación anticipada" -- el texto más largo del menú -- no entraba y el candado
           quedaba empujado contra el borde). Selectores aparte porque las de arriba usan ">"
           (hijo directo de .nav-sidebar) y estas viven un nivel más adentro, dentro de
           .nav-treeview -- si no, ninguna de las reglas de arriba las alcanzaría.
           box-sizing:border-box + width:auto (el valor por default, que en un bloque con margen
           ya rellena "100% menos los márgenes" solo, sin pasarse) + padding-right de 10px: el
           candado queda siempre completo, con respiro antes del borde del propio recuadro.
           gap:8px asegura el espacio mínimo entre ícono y texto. outline:none + :focus más
           abajo: sin anillo ni con mouse ni con teclado (alcanza con el fondo de :hover/:focus).
           V3g: se agrega border:1px solid transparent (2px en total entre los dos lados) para
           que el hover/activo, que sí llevan borde, no corran el contenido 2px al aparecer --
           por eso padding-right bajó de 12px a 10px (2px menos de padding + 2px de borde = el
           mismo inset de 12px que antes, el candado no se mueve ni un pixel). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link {
            display: flex;
            align-items: center;
            gap: 8px;
            box-sizing: border-box;
            width: auto;
            margin: 1px 8px;
            border: 1px solid transparent;
            padding-left: calc(.75rem + 6px);
            padding-right: 10px;
            border-radius: 8px;
            color: #E2E8F0;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 13px;
            font-weight: 600;
            outline: none;
            transition: background-color .2s ease, border-color .2s ease, box-shadow .2s ease, color .2s ease;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link:focus,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link:focus-visible {
            outline: none;
            box-shadow: none;
        }

        /* V3d/V3e: una sola línea siempre -- min-width:0 es necesario para que un hijo flex pueda
           angostarse por debajo del ancho de su propio texto (si no, nowrap+ellipsis no hace
           nada, el flex item nunca se achica). V3e (pedido de Joel): flex:1 1 auto -- el texto
           ahora ocupa TODO el espacio sobrante entre el ícono y el candado (que no crece ni se
           achica, flex-shrink:0 más abajo), en vez de quedar angosto a su propio contenido con
           el candado empujado por un margin-left:auto sin nada que lo contenga. Así el candado
           tiene su lugar siempre reservado, pegado al final, y el texto usa lo que quede -- con
           la sangría más chica (6px) y el texto más largo del menú ("Cancelación anticipada")
           entra completo, sin puntos suspensivos. Sin title con el texto completo para el caso
           de recorte porque esta regla es solo CSS (cabecera.jsp) y no toca menu.jsp -- si algún
           perfil nuevo agrega una opción más larga y se recorta, hace falta agregar el title
           en el <p> de menu.jsp (ahí sí, un cambio chico aparte). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link p {
            flex: 1 1 auto;
            min-width: 0;
            margin: 0;
            overflow: hidden;
            white-space: nowrap;
            text-overflow: ellipsis;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link .nav-icon {
            flex: 0 0 auto;
            color: rgba(226, 232, 240, .85);
            transition: color .2s ease, transform .2s ease;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item:hover > .nav-link,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link:focus {
            background-color: rgba(27, 155, 106, .12) !important;
            border-color: rgba(27, 155, 106, .45) !important;
            box-shadow: 0 0 12px rgba(27, 155, 106, .25) !important;
            color: #fff !important;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item:hover > .nav-link .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link:focus .nav-icon {
            color: rgba(255, 255, 255, .9);
            transform: translateX(2px);
        }

        /* "Dónde estoy": igual que arriba, .30 en vez de .20 y negrita -- esta clase "active" la
           pone claseActiva(...) en menu.jsp (ruta real) o el script de V3h en pie.jsp (el caso
           de #cierre-documental). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active:hover,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active:focus {
            background-color: rgba(27, 155, 106, .30) !important;
            color: #fff !important;
            font-weight: 700 !important;
            border-left: 3px solid #1B9B6A !important;
            border-radius: 0 8px 8px 0 !important;
            box-shadow: none !important;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active:hover .nav-icon,
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.active:focus .nav-icon {
            color: #34D399 !important;
        }

        /* V3c (pedido de Joel): opción SIN permiso -- <span> (no <a>: no se puede hacer clic ni
           llegar con teclado), atenuada, con un candado chico a la derecha. El bloqueo real lo
           sigue haciendo FiltroAutorizacion en el servidor -- esto es solo para que se vea.
           display:flex/align-items ya los pone la regla general de arriba (.opcion-bloqueada
           también tiene la clase nav-link), acá solo lo distinto. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item > .nav-link.opcion-bloqueada {
            opacity: .45;
            cursor: not-allowed;
        }

        /* V3c/V3g (pedido de Joel): las opciones con candado NO se iluminan -- anula el hover
           verde (fondo, borde y sombra) que heredaría de la regla general de arriba (misma
           especificidad + la clase .opcion-bloqueada de más, para ganarle de forma segura).
           Siguen atenuadas, con cursor not-allowed (ya lo pone la regla de arriba). */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .nav-treeview > .nav-item:hover > .nav-link.opcion-bloqueada {
            background-color: transparent !important;
            border-color: transparent !important;
            box-shadow: none !important;
            color: #E2E8F0 !important;
        }

        /* V3d/V3e (pedido de Joel): el candado nunca crece ni se achica (flex: 0 0 auto incluye
           flex-shrink:0) -- su lugar queda siempre reservado porque ahora es el texto (<p>,
           flex:1 1 auto en la regla de arriba) el que ocupa el espacio sobrante y se achica
           primero. margin-left:auto + el gap:8px de la regla general dejan al menos ese espacio
           respecto del texto; el padding-right:12px del recuadro completo deja el aire respecto
           del borde de la barra. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar .opcion-bloqueada .candado-opcion {
            flex: 0 0 auto;
            margin-left: auto;
            font-size: 11px;
        }

        /* V3c: módulo sin NINGUNA opción habilitada para este usuario -- encabezado atenuado con
           un candado, pero se sigue pudiendo desplegar (el <a href="#"> sigue igual, solo cambia
           el estilo). El candado va al lugar que AdminLTE reserva para un segundo ícono ".right"
           (right:2.2rem, a la izquierda de la flechita) -- a propósito NO usa esa clase, para que
           no gire junto con la flechita al abrir/cerrar el módulo. El administrador nunca ve
           esta clase: esAdministrador ya está en el OR de todas las condiciones. */
        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.nav-link-modulo.modulo-bloqueado {
            opacity: .45;
        }

        .main-sidebar.sidebar-dark-primary .nav-sidebar > .nav-item > .nav-link.nav-link-modulo .candado-modulo {
            position: absolute;
            right: 2.2rem;
            top: .7rem;
            font-size: 11px;
        }

        /* 6. Scrollbar de la barra lateral, fina y discreta. scrollbar-width/color para Firefox;
           ::-webkit-scrollbar para Chrome/Edge. V3i, punto 2 (pedido de Joel): ~10px de padding
           horizontal para que los recuadros de los módulos no queden pegados al borde derecho ni
           a la barra de desplazamiento -- ya es fina (6px, ver ::-webkit-scrollbar más abajo),
           pero "sin comerse espacio" de forma 100% confiable en todos los navegadores (ej. vía
           overflow:overlay) no es estándar/confiable hoy -- este padding es la forma segura de
           lograr el mismo resultado visual. */
        .main-sidebar .sidebar {
            padding: 0 10px;
            scrollbar-width: thin;
            scrollbar-color: rgba(255, 255, 255, .2) transparent;
        }

        .main-sidebar .sidebar::-webkit-scrollbar {
            width: 6px;
        }

        .main-sidebar .sidebar::-webkit-scrollbar-track {
            background: transparent;
        }

        .main-sidebar .sidebar::-webkit-scrollbar-thumb {
            background-color: rgba(255, 255, 255, .2);
            border-radius: 3px;
        }

        /* V3b/V3d (pedido de Joel): el botón ☰ mostraba el recuadro de foco del navegador al
           hacer clic -- mismo caso que el encabezado de módulo (ver el comentario más arriba):
           :focus-visible solo, y después :focus:not(:focus-visible), no alcanzaban porque en un
           <a href="#"> el navegador puede considerar "focus-visible" un clic de mouse. Directo a
           :focus, sin anillo ni con mouse ni con teclado. */
        .main-header .nav-link[data-widget="pushmenu"] {
            outline: none;
        }

        .main-header .nav-link[data-widget="pushmenu"]:focus,
        .main-header .nav-link[data-widget="pushmenu"]:focus-visible {
            outline: none;
            box-shadow: none;
        }

        /* V3h (pedido de Joel): #cierre-documental es el id de la sección "Préstamos
           finalizados — cierre documental pendiente" en formalizacion/lista.jsp -- el margen
           evita que, al bajar hasta ahí (scrollIntoView, ver el script en pie.jsp), la barra
           superior fija la tape.
           B63 (28/10/2026, autorizado por Joel): #diferencias-arqueo es el id de la sección
           "Diferencias de arqueo sin revisar" de caja/ficha.jsp, destino del enlace de la
           tarjeta nueva "Diferencias de caja por revisar (N)" del panel del gerente
           (/caja#diferencias-arqueo) -- mismo problema y misma solución. */
        #cierre-documental,
        #diferencias-arqueo {
            scroll-margin-top: 80px;
        }

        /* V4 (pedido de Joel), punto 2: barra superior -- fondo blanco, sombra suave en vez de
           la línea gris de AdminLTE, altura cómoda. min-height (no height fijo) para que nunca
           recorte contenido si algún navegador mide distinto el texto/los íconos. */
        .main-header.navbar {
            background-color: #fff !important;
            border-bottom: none !important;
            box-shadow: 0 2px 8px rgba(12, 50, 92, .08);
            min-height: 56px;
        }

        /* V4, punto 3: botón ☰ -- ícono azul del logo, fondo circular suave al pasar el mouse
           (el quitar el anillo de foco ya lo hacían las reglas de más arriba, V3b/V3d, sin
           cambios). */
        .main-header .nav-link[data-widget="pushmenu"] {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 40px;
            height: 40px;
            color: #0C325C;
            border-radius: 50%;
            transition: background-color .15s ease;
        }

        .main-header .nav-link[data-widget="pushmenu"]:hover {
            background-color: rgba(12, 50, 92, .06);
            color: #0C325C;
        }

        /* V4, punto 4: fecha del día (servidor, en español -- ver el scriptlet de arriba),
           discreta, a la izquierda, al lado del ☰. Se oculta en pantallas muy chicas
           (d-none d-sm-flex en el <li>, en el HTML) para no amontonar la barra en un celular. */
        .main-header .navbar-fecha-hoy {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            color: #475569;
            white-space: nowrap;
        }

        /* V4, punto 1: píldora de "base de pruebas" -- reemplaza el banner naranja grande de
           antes (misma condición del lado del servidor, FabricaConexionBD.esBaseDePruebas(),
           sin ningún cambio de lógica). Punto parpadeando suave para llamar la atención sin ser
           invasivo; prefers-reduced-motion lo desactiva (punto 6 de V4). */
        .pildora-base-pruebas {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-weight: 700;
            font-size: 11px;
            text-transform: uppercase;
            color: #8a5a00;
            background-color: #FFF4D6;
            border: 1px solid #F5D27A;
            border-radius: 999px;
            padding: 3px 10px;
            white-space: nowrap;
        }

        .pildora-base-pruebas-punto {
            width: 6px;
            height: 6px;
            flex: 0 0 auto;
            border-radius: 50%;
            background-color: #d99a00;
            animation: v4ParpadeoPildora 1.6s ease-in-out infinite;
        }

        @keyframes v4ParpadeoPildora {
            0%, 100% { opacity: 1; }
            50% { opacity: .35; }
        }

        @media (prefers-reduced-motion: reduce) {
            .pildora-base-pruebas-punto {
                animation: none;
            }
        }

        /* B111 (pedido de Joel, 05/11/2026, maqueta aprobada): tarjeta del usuario al PIE de la
           barra lateral, con "Contraseña" y "Salir" a la vista en vez de escondidos detrás de un
           clic. Reemplaza las DOS piezas que se eliminaron en este mismo cambio: la tarjeta de
           arriba del menú (V3i punto 4, que repetía el nombre) y el menú desplegable de la barra
           superior (V4 punto 5). Con el desplegable se fueron también sus reglas de
           .main-header .dropdown-menu: la barra superior ya no tiene ningún desplegable, así que
           no quedaba marcado al que aplicarse.

           "Fijo abajo aunque el menú sea corto": el <aside> pasa a ser una columna flex, el
           .sidebar (que es el que scrollea) se queda con todo el alto sobrante y el pie no se
           encoge. Con el menú corto el pie queda pegado al fondo; con el menú largo baja debajo
           de la última opción sin taparla, y el que scrollea sigue siendo el menú. */
        .main-sidebar.sidebar-dark-primary {
            display: flex;
            flex-direction: column;
        }

        /* El logo no se encoge; el menú se queda con el espacio que sobra. */
        .main-sidebar.sidebar-dark-primary > .brand-link {
            flex: 0 0 auto;
        }

        .main-sidebar.sidebar-dark-primary > .sidebar {
            flex: 1 1 auto;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario {
            flex: 0 0 auto;
            margin: 10px 12px 14px;
            padding: 14px;
            border-radius: 12px;
            background-color: rgba(255, 255, 255, .06);
            border: 1px solid rgba(255, 255, 255, .12);
            /* Al colapsar y expandir, nada se sale de los 4.6rem de la barra angosta. */
            overflow: hidden;
        }

        /* B240: el <aside> tampoco puede recortar mientras el menú está abierto. Medido en
           AdminLTE: le pone `overflow-y: hidden` siempre y, con la barra colapsada, además
           `overflow-x: hidden` -- o sea que sin esto el menú que se abre al lado del círculo
           quedaría cortado. Cede solo con el menú abierto, igual que la tarjeta. */
        .main-sidebar.sidebar-dark-primary:has(.pie-usuario.show) {
            overflow: visible;
        }

        /* B240: el recorte de la tarjeta cede SOLO mientras el menú está abierto -- Bootstrap le pone
           .show al contenedor del dropup, así que en todos los demás estados sigue protegiendo
           los 4,6 rem igual que antes. Y no deja ningún control invisible pero clickeable: con la
           barra angosta lo único que se muestra es el círculo, y el texto se sigue ocultando con
           display:none (nunca con opacidad, que es lo que V3i hacía mal). */
        .main-sidebar.sidebar-dark-primary .pie-usuario.show {
            overflow: visible;
        }

        /* B240: la fila pasó de <div> a <button>, así que se apaga lo que el navegador le pone
           a un botón y queda visualmente igual que antes. El margen de abajo era la separación
           con los dos botones que ya no están. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-fila {
            display: flex;
            align-items: center;
            gap: 10px;
            width: 100%;
            margin: 0;
            padding: 0;
            background: none;
            border: 0;
            border-radius: 8px;
            text-align: left;
            color: inherit;
            cursor: pointer;
        }

        /* La flecha dice que la tarjeta abre algo: sin ella el clic no se adivina. Gira cuando el
           menú está abierto, con la misma transición corta del resto del sistema. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-flecha {
            flex: 0 0 auto;
            margin-left: auto;
            font-size: 11px;
            color: #D7E2EE;
            transition: transform .15s ease;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario.show .pie-usuario-flecha {
            transform: rotate(180deg);
        }

        /* Mismo verde que la tarjeta "Cobrado este mes" del Panel (#167F57, B110), NO el verde de
           marca #1B9B6A: con el de marca las iniciales blancas quedan en 3,53:1 y no llegan al
           4,5:1 de AA; con este, 4,99:1. Es el mismo defecto preexistente que se anotó al cerrar
           B110 para el círculo de la barra superior -- acá, al rehacer la pieza, se corrige. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-avatar {
            flex: 0 0 auto;
            display: flex;
            align-items: center;
            justify-content: center;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background-color: #167F57;
            color: #fff;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-weight: 700;
            font-size: 14px;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario-texto {
            display: flex;
            flex-direction: column;
            min-width: 0;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario-nombre {
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-weight: 700;
            font-size: 14px;
            line-height: 1.2;
            color: #fff;
            /* Un nombre largo baja de renglón en vez de desbordar la barra. */
            overflow-wrap: break-word;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario-perfil {
            font-size: 12.5px;
            font-weight: 600;
            color: #34D399;
        }

        /* B240: el menú. Ancho mínimo el de la tarjeta, para que no quede más angosto que lo
           que lo abre, y separado unos píxeles del borde de arriba de la tarjeta. Los colores son
           los del dropdown de Bootstrap, sin agregar ninguno: la decisión de Joel del 16/11/2026
           (B238) es que no se toca ningún color del sistema. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-menu {
            min-width: 100%;
            margin-bottom: 8px;
        }

        /* "Cerrar sesión" en rojo y al final, que es lo que pidió Joel. Es el MISMO #B91C1C que
           usaba el botón rojo que esta pieza reemplaza; sobre el blanco del menú da 6,47:1, así
           que pasa AA para texto normal (4,5:1). Al pasar el mouse se invierte, con el rojo de
           fondo y el blanco encima, igual que hacía el botón viejo. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-salir {
            color: #B91C1C;
            font-weight: 600;
        }

        .main-sidebar.sidebar-dark-primary .pie-usuario-salir:hover,
        .main-sidebar.sidebar-dark-primary .pie-usuario-salir:focus {
            color: #fff;
            background-color: #B91C1C;
        }

        /* Anillo de foco verde al llegar con el teclado (GUIA_VISUAL.md §4). Ahora va en el
           botón que abre el menú, que es el único control de la tarjeta; los dos renglones de
           adentro los maneja el dropdown de Bootstrap con su propio resaltado. */
        .main-sidebar.sidebar-dark-primary .pie-usuario-fila:focus-visible {
            outline: none;
            box-shadow: 0 0 0 3px rgba(52, 211, 153, .55);
        }

        /* Barra COLAPSADA y sin el mouse encima: queda solo el círculo con las iniciales,
           centrado. Al pasar el mouse (o al expandirla con el ☰) vuelve la tarjeta entera.
           Mismos selectores que usaba la tarjeta de V3i y que AdminLTE usa para .brand-text, así
           el pie se comporta igual que el resto de la barra.

           El texto se oculta con display:none y no con la opacidad que usaba V3i: un control
           invisible por opacidad se sigue pudiendo clickear. B240: ya no hay dos botones que
           esconder -- queda el círculo, que ES el que abre el menú. */
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .pie-usuario {
            margin: 10px 8px 14px;
            padding: 8px 4px;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .pie-usuario-fila {
            justify-content: center;
            gap: 0;
            margin-bottom: 0;
        }

        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .pie-usuario-texto,
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .pie-usuario-flecha {
            display: none;
        }

        /* B240: con la barra angosta el menú se abre AL LADO del círculo y no encima, porque
           arriba no hay 4,6 rem de ancho para mostrarlo. Esto funciona porque el botón lleva
           data-display="static": sin eso, Popper le escribiría un transform en línea y le ganaría
           a estas reglas.

           Este estado es el de la barra angosta SIN el mouse encima, o sea el del teclado: con el
           mouse, AdminLTE expande la barra al pasar por arriba, y ahí el menú vuelve a abrirse
           hacia arriba sobre la tarjeta entera. Son los dos caminos y los dos quedan usables.

           Y acá el que no puede recortar es el <aside>: la tarjeta ya cede su overflow con .show,
           pero el menú se sale para la derecha de la barra. */
        .sidebar-mini.sidebar-collapse .main-sidebar:not(.sidebar-no-expand):not(.sidebar-focused):not(:hover) .pie-usuario-menu {
            top: auto;
            bottom: 0;
            left: calc(100% + 10px);
            min-width: 13rem;
            margin-bottom: 0;
        }

    </style>
    <%-- V6 (pedido de Joel): estilo general del sistema -- archivo propio, cargado DESPUÉS de
         Bootstrap/AdminLTE y de este <style> (barra lateral/superior, V3/V4), para que gane por
         orden sin pisar nada de lo que ya está cerrado. RF08 (11/10/2026): "?v=" de
         VersionDespliegue -- antes este archivo quedaba sin cache-busting (hallazgo de Joel,
         la clase .alert-riesgo-alto nueva no se veía sin Ctrl+Shift+R después de un redeploy). --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/css/sgcpc-tema.css?v=<%= VersionDespliegue.VALOR %>">
</head>
<body class="hold-transition sidebar-mini layout-fixed">
<div class="wrapper">

    <%-- V4 (pedido de Joel): el banner naranja grande de "BASE DE PRUEBAS" (que ocupaba todo el
         ancho, arriba de todo) se reemplaza por la píldora discreta de más abajo, a la izquierda
         del usuario -- misma condición (FabricaConexionBD.esBaseDePruebas()), ningún cambio de
         lógica. --%>
    <nav class="main-header navbar navbar-expand navbar-white navbar-light">
        <ul class="navbar-nav">
            <li class="nav-item">
                <a class="nav-link" data-widget="pushmenu" href="#" role="button"><i class="fas fa-bars"></i></a>
            </li>
            <li class="nav-item d-none d-sm-flex align-items-center">
                <span class="navbar-fecha-hoy">
                    <i class="far fa-calendar-alt" aria-hidden="true"></i> <%= fechaHoyLarga %>
                </span>
            </li>
        </ul>
        <ul class="navbar-nav ml-auto align-items-center">
            <% if (FabricaConexionBD.esBaseDePruebas()) { %>
            <li class="nav-item d-flex align-items-center mr-2">
                <span class="pildora-base-pruebas" title="Está usando la base de pruebas, no la oficial">
                    <span class="pildora-base-pruebas-punto" aria-hidden="true"></span>BASE DE PRUEBAS
                </span>
            </li>
            <% } %>
        </ul>
    </nav>

    <aside class="main-sidebar sidebar-dark-primary elevation-4">
        <a href="${pageContext.request.contextPath}/panel" class="brand-link">
            <%-- L1 Paso 2 (pedido de Joel): solo el escudo, sin img-circle/elevation (no es
                 redondo y la sombra lo ensucia) y con opacidad completa (AdminLTE deja el
                 .brand-image de ejemplo en opacity .8, acá se fuerza a 1). Sin eslogan: no entra
                 bien en la barra lateral angosta. Ajuste: logo_icono_claro.png (contorno blanco),
                 no el de contorno azul marino -- no se distinguía sobre el gris oscuro de la
                 barra lateral. --%>
            <img src="${pageContext.request.contextPath}/recursos/imagenes/logo_icono_claro.png"
                 alt="Capital Crédito" class="brand-image" style="opacity:1">
            <span class="brand-text font-weight-light"><%= nombreComercial %></span>
        </a>
        <div class="sidebar">
            <%@ include file="menu.jsp" %>
        </div>
        <%-- B111 (pedido de Joel, 05/11/2026, maqueta aprobada): el usuario conectado y sus dos
             acciones viven acá, al PIE de la barra lateral, y ya no en la barra superior. Antes
             estaban repartidos en dos lugares -- la tarjeta de arriba del menú (V3i punto 4,
             solo nombre y perfil) y el desplegable de la barra superior (V4 punto 5, con
             "Cambiar mi contraseña" y "Salir") --, así que el nombre aparecía dos veces y las
             dos acciones estaban escondidas detrás de un clic. Las dos piezas viejas se
             eliminaron en este mismo cambio.

             Nada de lógica nueva: usuarioSesion e inicialesUsuario ya estaban declarados arriba
             (el mismo objeto que usaba el saludo del navbar, sin ninguna consulta nueva a la
             base), y getPerfilUsuario().getEtiqueta() es la etiqueta legible de siempre.

             Las URL /cambiar-contrasena y /salir son EXACTAMENTE las mismas que tenía el
             desplegable: lo que cambia es dónde se las ve, no a dónde llevan. --%>
        <% if (usuarioSesion != null) { %>
        <%-- B240 (16/11/2026, decisión de Joel): los dos botones salen de la tarjeta y pasan a un
             menú que se abre al hacerle clic. Antes "Contraseña" y "Salir" estaban SIEMPRE a la
             vista y, como /salir es un GET, un clic accidental cerraba la sesión sin preguntar
             nada. Ahora hacen falta dos clics, y ESA es la confirmación: Joel decidió que no
             haya un cartel extra encima.

             Es el dropup de Bootstrap 4.6.1, que ya se carga en pie.jsp: el Esc, el clic afuera,
             la navegación con teclado y el aria-expanded los maneja él, así que NO hay JavaScript
             nuevo. data-display="static" apaga el posicionamiento de Popper --que escribe un
             transform en línea-- para que la posición la decida el CSS; es lo que permite abrir
             el menú AL LADO cuando la barra está angosta, sin pelearle a un estilo en línea.

             El <span class="sr-only"> existe por la barra colapsada: ahí .pie-usuario-texto se
             oculta con display:none, así que sin esto el botón se quedaría sin nombre accesible
             justo para quien llega con el teclado. Con la barra expandida el nombre accesible es
             "Mi cuenta" + el nombre y el perfil visibles, así que el rótulo visible sigue estando
             contenido en el nombre accesible (WCAG 2.5.3).

             Lo que NO se toca, y es a propósito:
             - La clase pie-usuario-nombre con el nombre adentro: pruebas/humo_http.sh (línea
               4251) lo lee de ahí con un grep, para su diagnóstico del arqueo del Bloque 2.
             - /cambiar-contrasena y /salir: mismas URL, y /salir sigue siendo un enlace GET --el
               humo lo llama así, esperando 302, en una veintena de lugares. --%>
        <div class="pie-usuario dropup">
            <button type="button" class="pie-usuario-fila" data-toggle="dropdown"
                    data-display="static" aria-haspopup="true" aria-expanded="false">
                <span class="sr-only">Mi cuenta</span>
                <span class="pie-usuario-avatar" aria-hidden="true"><%= inicialesUsuario %></span>
                <span class="pie-usuario-texto">
                    <span class="pie-usuario-nombre"><%= usuarioSesion.getNombres() %> <%= usuarioSesion.getApellidos() %></span>
                    <span class="pie-usuario-perfil"><%= usuarioSesion.getPerfilUsuario().getEtiqueta() %></span>
                </span>
                <i class="fas fa-chevron-up pie-usuario-flecha" aria-hidden="true"></i>
            </button>
            <div class="dropdown-menu pie-usuario-menu">
                <a class="dropdown-item" href="${pageContext.request.contextPath}/cambiar-contrasena">
                    <i class="fas fa-key mr-2" aria-hidden="true"></i>Cambiar contraseña
                </a>
                <div class="dropdown-divider"></div>
                <a class="dropdown-item pie-usuario-salir" href="${pageContext.request.contextPath}/salir">
                    <i class="fas fa-sign-out-alt mr-2" aria-hidden="true"></i>Cerrar sesión
                </a>
            </div>
        </div>
        <% } %>
    </aside>

    <div class="content-wrapper">
        <div class="content">
            <div class="container-fluid">
                <%-- Bloque 7/E10 (D-131, pedido de Joel): el resultado de una acción ya no se
                     muestra como un cartel fijo arriba (pasaba desapercibido, o directamente no
                     avisaba nada) -- pie.jsp lo abre como un modal (mismo estilo que los modales
                     de confirmación, SweetAlert2) apenas carga la página. Estos <div> quedan
                     ocultos, solo para que ese script lea el texto tal cual (mismo criterio de
                     siempre: el mensaje nunca lleva HTML). Un solo mecanismo para todo el
                     sistema: cualquier pantalla que ya usaba mensajeExito/mensajeError (directo
                     por forward, o vía MensajesFlash tras un redirect) sale con su modal, sin
                     tocar cada JSP una por una. --%>
                <% if (request.getAttribute("mensajeExito") != null) { %>
                <div id="datosMensajeExito" style="display:none;">${mensajeExito}</div>
                <% } %>
                <% if (request.getAttribute("mensajeError") != null) { %>
                <div id="datosMensajeError" style="display:none;">${mensajeError}</div>
                <% } %>
                <%-- B213 (pedido de Joel, 15/11/2026): el dato de un mensaje de exito que se
                     muestra UNA sola vez (hoy, la contrasena temporal de RF37). Solo viaja el
                     dato, no el mensaje: plantilla/pie.jsp lo usa para que ese modal no se cierre
                     con un clic afuera ni con Esc, y para ofrecer "Copiar". Ver
                     MensajesFlash.guardarExitoSensible. --%>
                <% if (request.getAttribute("mensajeExitoSensible") != null) { %>
                <div id="datosMensajeExitoSensible" style="display:none;">${mensajeExitoSensible}</div>
                <% } %>
                <%-- RF02 (pedido de Joel): tercer nivel amarillo -- misma mecánica que éxito/error,
                     ver MensajesFlash.guardarAdvertencia. --%>
                <% if (request.getAttribute("mensajeAdvertencia") != null) { %>
                <div id="datosMensajeAdvertencia" style="display:none;">${mensajeAdvertencia}</div>
                <% } %>
                <%
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, String> imprimiblesExitoCabecera =
                            (java.util.Map<String, String>) request.getAttribute("mensajeExitoImprimibles");
                    if (imprimiblesExitoCabecera != null && !imprimiblesExitoCabecera.isEmpty()) {
                %>
                <div id="datosMensajeExitoImprimibles" style="display:none;">
                    <% for (java.util.Map.Entry<String, String> entradaImprimibleCabecera : imprimiblesExitoCabecera.entrySet()) { %>
                    <a href="${pageContext.request.contextPath}<%= entradaImprimibleCabecera.getValue() %>"
                       target="_blank"><%= entradaImprimibleCabecera.getKey() %></a>
                    <% } %>
                </div>
                <% } %>
                <%-- Corrección (pedido de Joel, 10/10/2026): mismo criterio que mensajeExitoImprimibles,
                     pero para que un texto DENTRO del mensaje de error (ej. un código de
                     solicitud) se vuelva un enlace real -- el mensaje en sí nunca lleva HTML
                     (ver el comentario de más arriba); plantilla/pie.jsp hace el reemplazo. --%>
                <%
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, String> enlacesErrorCabecera =
                            (java.util.Map<String, String>) request.getAttribute("mensajeErrorEnlaces");
                    if (enlacesErrorCabecera != null && !enlacesErrorCabecera.isEmpty()) {
                %>
                <div id="datosMensajeErrorEnlaces" style="display:none;">
                    <% for (java.util.Map.Entry<String, String> entradaEnlaceErrorCabecera : enlacesErrorCabecera.entrySet()) { %>
                    <a href="${pageContext.request.contextPath}<%= entradaEnlaceErrorCabecera.getValue() %>"
                       data-texto="<%= entradaEnlaceErrorCabecera.getKey() %>"><%= entradaEnlaceErrorCabecera.getKey() %></a>
                    <% } %>
                </div>
                <% } %>
