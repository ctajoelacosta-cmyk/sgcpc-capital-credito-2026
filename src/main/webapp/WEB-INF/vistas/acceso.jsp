<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD" %>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Acceso - ${empty nombreComercial ? 'Capital Crédito' : nombreComercial}</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/recursos/imagenes/favicon_64.png">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/fuentes/fuente-source-sans-pro.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/fontawesome/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/adminlte/dist/css/adminlte.min.css">
    <%-- V1 (pedido de Joel): rediseño solo visual de esta pantalla -- estilo propio, sin tocar
         ningún otro .jsp ni archivo CSS compartido, para no afectar el resto del sistema (que
         sigue usando el AdminLTE de siempre). Nada de fuentes ni CDN externos (REGLAS_DESARROLLO.md §3). --%>
    <style>
        /* V1f (pedido de Joel): Montserrat local (archivo propio, licencia OFL, nada de Google
           Fonts ni CDN -- recursos/fuentes/montserrat/, junto con OFL.txt), con ruta armada con
           el contextPath porque esto es un <style> embebido en el JSP, no un .css estático (un
           .css no puede usar EL). font-display:swap -- el texto se ve de entrada con la fuente
           de respaldo (la misma de siempre, Source Sans Pro) y cambia sola cuando termina de
           cargar Montserrat; si el archivo no carga nunca, se queda con el respaldo sin romper
           nada. Solo se usa en los elementos que pidió Joel (etiquetas, botón, VER/OCULTAR y la
           píldora) -- el resto de la pantalla sigue con la fuente de siempre. */
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

        /* V1 ajuste 1 (pedido de Joel): nada de position:absolute -- un contenedor flex en
           columna, con la tarjeta centrada por margin:auto y el pie de página dentro del flujo
           normal, debajo. Así nunca se encima con el banner ni con el pie, y si la pantalla es
           chica el contenido simplemente empuja y aparece scroll (no hay nada "fixed" en el
           contenido -- sí lo hay en las capas de fondo de V1d, que no ocupan espacio). */
        /* V1f (pedido de Joel): azul exacto de la palabra "Capital" del logo (#0C325C) en vez
           del azul grisáceo anterior, con un degradé propio (más claro al centro, más oscuro en
           los bordes) independiente de la luz que sigue al mouse -- esta es la base que se ve
           SIEMPRE, la luz de abajo se superpone encima. */
        body.pantalla-ingreso {
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            margin: 0;
            background-color: #0C325C;
            background-image: radial-gradient(circle at center, #134477 0%, #0C325C 55%, #071F3B 100%);
        }

        /* V1d (pedido de Joel): dos capas de fondo fijas, una detrás de otra, en este orden
           (así se pintan, de atrás para adelante, por el orden en el HTML -- ninguna necesita
           z-index porque ninguna otra parte de la página usa position/z-index que las pueda
           desordenar):
           1) dos burbujas difusas (verde y azul) que pulsan muy lento.
           2) el patrón de líneas finas de siempre, por ENCIMA de las burbujas.
           Todas con pointer-events:none para no interferir con los clics. V1g (pedido de Joel):
           se sacó la tercera capa, la luz que seguía al mouse -- el fondo ahora es fijo (el
           degradé propio de body.pantalla-ingreso, más abajo, las burbujas y el patrón). El
           borde luminoso de la tarjeta (--bx/--by, que es otra cosa, el .halo-tarjeta/
           .borde-luminoso de más abajo) no se tocó. */
        .fondo-burbujas,
        .fondo-patron {
            position: fixed;
            inset: 0;
            pointer-events: none;
        }

        .fondo-burbujas {
            overflow: hidden;
        }

        .fondo-burbujas::before,
        .fondo-burbujas::after {
            content: "";
            position: absolute;
            width: 46vmax;
            height: 46vmax;
            border-radius: 50%;
            filter: blur(60px);
            opacity: .35;
            animation: pulsoBurbuja 7s ease-in-out infinite;
        }

        /* V1f: la verde queda más tenue (.55 -> .35 en el propio gradiente, no en la opacidad
           del elemento, que sigue compartida con la azul para que las dos pulsen igual). */
        .fondo-burbujas::before {
            top: -12%;
            left: -10%;
            background: radial-gradient(circle, rgba(27, 155, 106, .35) 0%, rgba(27, 155, 106, 0) 70%);
        }

        /* V1f: azul nuevo (#1D5A9E, en rgb). */
        .fondo-burbujas::after {
            bottom: -16%;
            right: -12%;
            background: radial-gradient(circle, rgba(29, 90, 158, .55) 0%, rgba(29, 90, 158, 0) 70%);
            animation-delay: 2.5s;
        }

        @keyframes pulsoBurbuja {
            0%, 100% { opacity: .3; transform: scale(1); }
            50% { opacity: .55; transform: scale(1.08); }
        }

        /* V1f: líneas blancas al 3.5% -- el alpha ya está en el propio color de la línea, así
           que el elemento no necesita (ni debe) una opacity aparte encima. */
        .fondo-patron {
            background-image: repeating-linear-gradient(45deg,
                rgba(255, 255, 255, .035) 0, rgba(255, 255, 255, .035) 1px,
                transparent 1px, transparent 14px);
        }

        /* V1d (pedido de Joel): reemplaza SOLO en esta pantalla al banner naranja -- misma
           condición (FabricaConexionBD.esBaseDePruebas()), discreta, arriba a la izquierda. El
           banner de las pantallas internas (cabecera.jsp) no se toca. V1f: Montserrat (peso 700). */
        .pantalla-ingreso .pildora-pruebas {
            position: fixed;
            z-index: 2;
            top: 16px;
            left: 16px;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(255, 255, 255, .08);
            color: rgba(255, 255, 255, .8);
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: .05em;
            -webkit-backdrop-filter: blur(6px);
            backdrop-filter: blur(6px);
        }

        .pantalla-ingreso .punto-pildora {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #D9A404;
            animation: parpadeoPunto 2s ease-in-out infinite;
        }

        @keyframes parpadeoPunto {
            0%, 100% { opacity: 1; }
            50% { opacity: .35; }
        }

        .pantalla-ingreso .login-box {
            position: relative;
            z-index: 2;
            margin: auto;
            width: 420px;
            max-width: 92vw;
        }

        /* V1e (pedido de Joel): envoltorio de la tarjeta -- position:relative (sin overflow)
           para que las dos capas de abajo (el halo difuso y el borde luminoso, que necesitan
           sobresalir un poco de la tarjeta) no queden recortadas por el overflow:hidden que la
           tarjeta sí necesita para la franja de marca y el efecto vidrio. V1f: la elevación al
           pasar el mouse va ACÁ, en el envoltorio (mueve tarjeta + halo + borde juntos) -- la
           sacudida de error sigue viviendo en la tarjeta interna (.tarjeta-ingreso.sacudida,
           más abajo), así los dos transform nunca se pisan por estar en elementos distintos. */
        .pantalla-ingreso .envoltorio-tarjeta {
            position: relative;
            transition: transform .3s ease;
        }

        .pantalla-ingreso .envoltorio-tarjeta:hover {
            transform: translateY(-4px);
        }

        .pantalla-ingreso .envoltorio-tarjeta:hover .card.tarjeta-ingreso {
            box-shadow: 0 30px 60px -12px rgba(0, 0, 0, .45),
                        0 10px 20px -5px rgba(27, 155, 106, .15) !important;
        }

        .pantalla-ingreso .halo-tarjeta,
        .pantalla-ingreso .borde-luminoso {
            position: absolute;
            inset: 0;
            border-radius: 20px;
            pointer-events: none;
            background: radial-gradient(250px circle at var(--bx, 50%) var(--by, 50%),
                rgba(52, 211, 153, .9), rgba(27, 155, 106, .35) 40%, transparent 70%);
        }

        /* El "borde" propiamente dicho: 2px más grande que la tarjeta en cada lado: la tarjeta
           (opaca, encima) tapa todo el centro de este círculo y solo deja ver un anillo fino de
           2px alrededor -- más brillante del lado donde está el mouse, porque el centro del
           gradiente sigue su posición relativa a la tarjeta. */
        .pantalla-ingreso .borde-luminoso {
            inset: -2px;
            border-radius: 22px;
        }

        /* El halo exterior, difuso -- mismo gradiente, pero sin agrandar el elemento: el blur
           ya lo hace sobresalir solo. */
        .pantalla-ingreso .halo-tarjeta {
            filter: blur(20px);
            opacity: .55;
            transition: opacity .3s ease;
        }

        /* V1 ajuste 2 / V1d (pedido de Joel): con !important porque el .card de AdminLTE/
           Bootstrap pisaba border-radius/box-shadow -- acá es un solo .jsp aislado, no un CSS
           compartido, así que forzar gana sin arriesgar otras pantallas. V1d: efecto vidrio
           (fondo semitransparente + blur de lo que queda atrás) en vez de fondo sólido. */
        /* V1e: position+z-index explícitos -- sin esto, al ser la tarjeta un bloque normal (no
           posicionado) y el halo/borde luminoso de abajo sí estar posicionados, el navegador los
           pintaría arriba de la tarjeta -- el efecto contrario al buscado (que el centro quede
           tapado y solo se vea el anillo alrededor). */
        .pantalla-ingreso .card.tarjeta-ingreso {
            position: relative;
            z-index: 1;
            background: rgba(255, 255, 255, .94) !important;
            -webkit-backdrop-filter: blur(20px) !important;
            backdrop-filter: blur(20px) !important;
            border: 1px solid rgba(255, 255, 255, .7) !important;
            border-radius: 20px !important;
            overflow: hidden !important;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, .35),
                        0 10px 20px -5px rgba(27, 155, 106, .15) !important;
        }

        .pantalla-ingreso .card.tarjeta-ingreso:hover {
            transform: none !important;
        }

        /* V1c (pedido de Joel): sacudida corta de la tarjeta cuando la página carga con un
           mensaje de error (o cuando falla la validación de campos vacíos de V1d), y entrada
           suave (fade-in + sube 10px) cuando carga sin error -- una sola vez al cargar, nunca
           las dos juntas (lo decide el script de abajo, agregando una sola de estas dos clases). */
        @keyframes sacudidaTarjeta {
            10%, 90% { transform: translateX(-1px); }
            20%, 80% { transform: translateX(2px); }
            30%, 50%, 70% { transform: translateX(-4px); }
            40%, 60% { transform: translateX(4px); }
        }

        .pantalla-ingreso .tarjeta-ingreso.sacudida {
            animation: sacudidaTarjeta .4s ease-in-out;
        }

        @keyframes entradaTarjeta {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .pantalla-ingreso .tarjeta-ingreso.entrada-suave {
            animation: entradaTarjeta .4s ease-out;
        }

        /* Franja de marca: al quedar como primer elemento dentro de una tarjeta con
           overflow:hidden y bordes redondeados, sus propias esquinas superiores quedan
           recortadas automáticamente con la misma curva de la tarjeta. */
        .pantalla-ingreso .card.tarjeta-ingreso::before {
            content: "";
            display: block;
            height: 6px;
            background: linear-gradient(to right, #1F3B63 0 70%, #1B9B6A 70%);
        }

        .pantalla-ingreso .login-card-body {
            padding: 32px;
        }

        /* V1c (pedido de Joel): mismo texto (el servlet lo arma, o el script de abajo para la
           validación de campos vacíos de V1d), solo estilo -- más suave, con borde izquierdo y
           un ícono por CSS (::before con el glyph de Font Awesome), sin tocar el
           <div class="alert ..."> que ya arma el JSP. alert-success/alert-info quedan listos
           en el mismo formato por si algún día esta pantalla muestra un mensaje informativo. */
        .pantalla-ingreso .alert {
            display: flex;
            align-items: flex-start;
            gap: 8px;
            border: 0;
            border-left: 4px solid #DC3545;
            border-radius: 8px;
            background: #FDECEC;
            color: #9B1C1C;
            font-size: 13px;
            padding: 10px 14px;
            margin-bottom: 20px;
        }

        .pantalla-ingreso .alert::before {
            font-family: "Font Awesome 5 Free";
            font-weight: 900;
            content: "\f071";
            flex: 0 0 auto;
            line-height: 1.4;
        }

        .pantalla-ingreso .alert-success,
        .pantalla-ingreso .alert-info {
            border-left-color: #1B9B6A;
            background: #E8F6F0;
            color: #0F5C3C;
        }

        .pantalla-ingreso .alert-success::before,
        .pantalla-ingreso .alert-info::before {
            content: "\f058";
        }

        .pantalla-ingreso .login-logo {
            margin-bottom: 20px;
            text-align: center;
        }

        .pantalla-ingreso .login-logo img {
            width: 170px;
            max-width: 100%;
            height: auto;
            transition: transform .4s ease;
        }

        /* V1d (pedido de Joel): el logo crece apenas al pasar el mouse. */
        .pantalla-ingreso .login-logo img:hover {
            transform: scale(1.04);
        }

        .pantalla-ingreso .campo-con-etiqueta {
            margin-bottom: 18px;
        }

        /* V1d (pedido de Joel): etiqueta arriba de cada campo, conectada con <label for> al
           input -- además del placeholder, que se deja igual que en V1/V1c. V1f: Montserrat
           (peso 600) -- el texto que escribe el usuario (el input en sí) se queda con la fuente
           de siempre. */
        .pantalla-ingreso .etiqueta-campo {
            display: block;
            margin-bottom: 6px;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: .06em;
            color: #475569;
        }

        /* V1 ajuste 3 (pedido de Joel): se abandona el ícono superpuesto en position:absolute
           (ahí quedaba invisible) por el input-group-prepend normal de Bootstrap -- el mismo
           mecanismo, ya probado, que ya usa el botón del ojo del lado derecho -- solo que sin
           borde ni fondo propio, para que se vea "dentro" del campo en vez de en una caja aparte. */
        .pantalla-ingreso .campo-ingreso {
            display: flex;
            align-items: stretch;
            border: 1px solid #D1D5DB;
            border-radius: 8px;
            background: #fff;
            overflow: hidden;
            transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
        }

        /* V1c/V1e (pedido de Joel): hover oscurece el borde, agrega una sombra suave y el campo
           sube 2px -- mismo efecto de elevación que el botón principal; al ser el contenedor
           flex el que sube, el ícono, el texto y el botón VER/OCULTAR suben con él (todo el
           recuadro, no solo el input). */
        .pantalla-ingreso .campo-ingreso:hover {
            border-color: #B6BEC7;
            box-shadow: 0 6px 16px rgba(27, 66, 87, .12);
            transform: translateY(-2px);
        }

        /* Foco: se mantiene elevado igual que en hover, y además el borde/anillo verde. V1h
           (pedido de Joel): anillo un poco más visible (3px -> 4px). */
        .pantalla-ingreso .campo-ingreso:focus-within {
            border-color: #1B9B6A;
            box-shadow: 0 6px 16px rgba(27, 66, 87, .12), 0 0 0 4px rgba(27, 155, 106, 0.15);
            transform: translateY(-2px);
        }

        .pantalla-ingreso .campo-ingreso:hover .input-group-prepend .input-group-text,
        .pantalla-ingreso .campo-ingreso:focus-within .input-group-prepend .input-group-text {
            color: #1B9B6A;
            background: rgba(27, 155, 106, .18);
        }

        /* V1c/V1d: borde rojo suave mientras hay error (del servidor o de la validación de
           campos vacíos), aun con foco -- va DESPUÉS de :focus-within a propósito, para ganarle
           en el empate de especificidad mientras esta clase esté. El script de abajo la saca
           apenas el usuario empieza a escribir. */
        .pantalla-ingreso .campo-ingreso.campo-con-error {
            border-color: #DC3545;
            box-shadow: 0 0 0 3px rgba(220, 53, 69, .12);
        }

        /* V1f (pedido de Joel): el ícono ahora va dentro de un cuadradito propio, en vez de
           suelto sobre el fondo del campo. */
        .pantalla-ingreso .campo-ingreso .input-group-prepend {
            display: flex;
            align-items: center;
            padding-left: 9px;
        }

        .pantalla-ingreso .campo-ingreso .input-group-prepend .input-group-text {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 30px;
            height: 30px;
            border: 0;
            border-radius: 8px;
            background: rgba(27, 155, 106, .10);
            color: #2B4257;
            font-size: 15px;
            padding: 0;
            transition: color .2s ease, background-color .2s ease;
        }

        .pantalla-ingreso .campo-ingreso .form-control {
            height: 48px;
            padding-left: 10px;
            border: 0;
            border-radius: 0;
            background: transparent;
            box-shadow: none;
        }

        .pantalla-ingreso .campo-ingreso .form-control::placeholder {
            color: #6C757D;
            opacity: 1;
        }

        .pantalla-ingreso .campo-ingreso .form-control:focus {
            box-shadow: none;
        }

        /* V1h (pedido de Joel): mientras se envía el formulario, los campos pasan a readonly
           (NO disabled -- un campo disabled no manda su valor y el ingreso fallaría) con
           aspecto atenuado. El script de abajo agrega/saca el atributo. */
        .pantalla-ingreso .campo-ingreso .form-control:read-only {
            opacity: .7;
            cursor: not-allowed;
        }

        /* V1 ajuste 4 (pedido de Joel): el autocompletado de Chrome pinta el campo de celeste
           con su propio fondo interno -- el único truco que le gana es este box-shadow inset. */
        .pantalla-ingreso .campo-ingreso .form-control:-webkit-autofill,
        .pantalla-ingreso .campo-ingreso .form-control:-webkit-autofill:hover,
        .pantalla-ingreso .campo-ingreso .form-control:-webkit-autofill:focus {
            -webkit-box-shadow: inset 0 0 0 1000px #fff;
            -webkit-text-fill-color: #212529;
            caret-color: #212529;
            transition: background-color 9999s ease-in-out 0s;
        }

        /* V1d (pedido de Joel): el botón del ojo ahora lleva texto ("VER"/"OCULTAR") además del
           ícono. V1f: Montserrat (peso 600). */
        .pantalla-ingreso .campo-ingreso .input-group-append .btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            height: 48px;
            padding: 0 14px;
            border: 0;
            border-radius: 0;
            background: transparent;
            color: #6C757D;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 11px;
            font-weight: 600;
            letter-spacing: .04em;
            text-transform: uppercase;
        }

        .pantalla-ingreso .campo-ingreso .input-group-append .btn:hover,
        .pantalla-ingreso .campo-ingreso .input-group-append .btn:focus {
            background: transparent;
            color: #1B9B6A;
            box-shadow: none;
        }

        /* V1c (pedido de Joel): aviso de Bloq Mayús propio de esta pantalla, con ícono -- usa el
           atributo "hidden" nativo (el script de abajo solo lo prende/apaga), sin !important. */
        .pantalla-ingreso .aviso-mayus {
            display: block;
            margin-top: 6px;
            font-size: 12px;
            color: #92650A;
        }

        .pantalla-ingreso .aviso-mayus i {
            margin-right: 4px;
            color: #D9A404;
        }

        .pantalla-ingreso .texto-olvido {
            text-align: right;
            font-size: 13px;
            color: #6C757D;
            margin: 8px 0 20px;
        }

        /* V1f: Montserrat (peso 700). */
        .pantalla-ingreso .boton-ingresar {
            width: 100%;
            height: 50px;
            background: #1B9B6A;
            border: 0;
            border-radius: 25px;
            color: #fff;
            font-family: 'Montserrat', 'Source Sans Pro', -apple-system, BlinkMacSystemFont,
                'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 15px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: .5px;
            box-shadow: 0 8px 20px rgba(27, 155, 106, .30);
            transition: transform .2s ease, box-shadow .2s ease, background-color .2s ease;
        }

        .pantalla-ingreso .boton-ingresar:hover,
        .pantalla-ingreso .boton-ingresar:focus {
            background: #14724E;
            color: #fff;
            transform: translateY(-2px);
            box-shadow: 0 12px 28px rgba(20, 114, 78, .40);
        }

        /* V1d (pedido de Joel): se "hunde" al hacer clic -- va después de :hover/:focus para
           ganarles mientras el botón está presionado. */
        .pantalla-ingreso .boton-ingresar:active {
            transform: scale(.99);
            box-shadow: 0 6px 14px rgba(20, 114, 78, .35);
        }

        /* V1c (pedido de Joel): "INGRESANDO…" con spinner al enviar, deshabilitado para evitar
           doble clic (el script de abajo agrega/saca disabled -- acá solo el look). */
        .pantalla-ingreso .boton-ingresar:disabled {
            opacity: .85;
            cursor: not-allowed;
            transform: none;
        }

        .pantalla-ingreso .spinner-ingresar {
            display: inline-block;
            width: 14px;
            height: 14px;
            margin-right: 6px;
            vertical-align: -2px;
            border: 2px solid rgba(255, 255, 255, .5);
            border-top-color: #fff;
            border-radius: 50%;
            animation: girarSpinner .6s linear infinite;
        }

        @keyframes girarSpinner {
            to { transform: rotate(360deg); }
        }

        /* V1e (pedido de Joel): más contraste (blanco al 75%), "SGCPC" en negrita y, sobre todo,
           por ENCIMA de las capas de fondo -- position+z-index explícitos, no solo el orden en
           el HTML (que ya alcanzaba, pero lo pidió explícito y así queda a prueba de dudas). */
        .pantalla-ingreso .pie-ingreso {
            position: relative;
            z-index: 2;
            margin: 8px 0 16px;
            padding: 0 16px;
            text-align: center;
            font-size: 12px;
            color: rgba(255, 255, 255, .75);
        }

        .pantalla-ingreso .pie-ingreso strong {
            font-weight: 700;
        }

        @media (max-width: 420px) {
            .pantalla-ingreso .login-card-body {
                padding: 24px 20px;
            }
        }

        /* Respeta "prefers-reduced-motion": el script de abajo ya evita agregar las clases de
           sacudida/entrada y no escucha mousemove para el borde luminoso de la tarjeta cuando
           está activado; esto además apaga cualquier animación o transform por CSS puro (hover/
           foco de campos, botón, logo, burbujas, punto de la píldora y giro del spinner) -- el
           "*::before,*::after" hace falta porque las burbujas y la franja de marca son
           pseudo-elementos, y un simple "*" no los alcanza. V1e: el halo y el borde luminoso de
           la tarjeta quedan directamente ocultos -- sin JS que actualice --bx/--by se quedarían
           fijos en el centro (50% 50%), que no es "el borde normal" que pidió Joel. */
        @media (prefers-reduced-motion: reduce) {
            .pantalla-ingreso *,
            .pantalla-ingreso *::before,
            .pantalla-ingreso *::after {
                animation: none !important;
                transition: none !important;
            }

            .pantalla-ingreso .envoltorio-tarjeta,
            .pantalla-ingreso .tarjeta-ingreso,
            .pantalla-ingreso .campo-ingreso,
            .pantalla-ingreso .boton-ingresar,
            .pantalla-ingreso .login-logo img {
                transform: none !important;
            }

            .pantalla-ingreso .halo-tarjeta,
            .pantalla-ingreso .borde-luminoso {
                display: none !important;
            }
        }
    </style>
</head>
<%-- V1c/V1d (pedido de Joel): data attribute de solo lectura para que el script de abajo sepa si
     hay mensaje de error -- no cambia el texto ni la lógica del servlet, solo lo expone al JS
     propio de esta página. --%>
<body class="hold-transition login-page pantalla-ingreso"
      data-con-error="<%= request.getAttribute("mensajeError") != null %>">
<div class="fondo-burbujas" aria-hidden="true"></div>
<div class="fondo-patron" aria-hidden="true"></div>
<% if (FabricaConexionBD.esBaseDePruebas()) { %>
<div class="pildora-pruebas" role="status">
    <span class="punto-pildora" aria-hidden="true"></span>
    Base de pruebas
</div>
<% } %>
<div class="login-box">
    <%-- V1e (pedido de Joel): envoltorio que aloja, detrás de la tarjeta, el halo difuso y el
         borde luminoso que siguen al mouse -- ver el comentario de .envoltorio-tarjeta en el
         <style>. --%>
    <div class="envoltorio-tarjeta">
        <div class="halo-tarjeta" aria-hidden="true"></div>
        <div class="borde-luminoso" aria-hidden="true"></div>
        <div class="card tarjeta-ingreso">
            <div class="card-body login-card-body">
            <%-- L1 Paso 1 (pedido de Joel): el logo ya trae el nombre y el eslogan adentro de la
                 imagen -- acá va solo la imagen, sin texto repetido. --%>
            <div class="login-logo">
                <img src="${pageContext.request.contextPath}/recursos/imagenes/logo_capital_credito_400.png"
                     alt="Capital Crédito" width="170">
            </div>

            <% if (request.getAttribute("mensajeError") != null) { %>
            <%
                // RNF09 (hallazgo de Joel): ServicioAutenticacion.mensajeAccesoRechazado devuelve
                // un solo texto "Título. Detalle." -- mismo criterio de corte que ya usa pie.jsp
                // (dividirEnTituloYDetalle): el primer ". " seguido de mayúscula separa las dos
                // partes, para mostrar el título en negrita y el detalle debajo. Si el mensaje no
                // tiene ese patrón (no debería pasar hoy), queda todo como título, sin detalle.
                String mensajeErrorFicha = (String) request.getAttribute("mensajeError");
                java.util.regex.Matcher corteMensajeError = java.util.regex.Pattern
                        .compile("\\.\\s+(?=[A-ZÁÉÍÓÚÑ])").matcher(mensajeErrorFicha);
                String tituloMensajeError = mensajeErrorFicha;
                String detalleMensajeError = null;
                if (corteMensajeError.find()) {
                    tituloMensajeError = mensajeErrorFicha.substring(0, corteMensajeError.start() + 1);
                    detalleMensajeError = mensajeErrorFicha.substring(corteMensajeError.end());
                }
            %>
            <div class="alert alert-danger">
                <div>
                    <strong><%= tituloMensajeError %></strong>
                    <% if (detalleMensajeError != null) { %>
                    <div><%= detalleMensajeError %></div>
                    <% } %>
                </div>
            </div>
            <% } %>

            <%
                // RF36/RF37 (hallazgo de Joel): tras un error, se mantiene escrito el usuario --
                // nunca la contraseña, que ni siquiera llega a tener un atributo "value" más abajo.
                // Esto es un forward (no un redirect), así que el parámetro del POST original
                // sigue disponible. Se escapa a mano (sin librería de escape en el proyecto): es
                // texto que un atacante controla y se refleja en el mismo HTML.
                String nombreUsuarioReenviado = request.getParameter("nombreUsuario");
                String nombreUsuarioEscapado = nombreUsuarioReenviado == null ? "" : nombreUsuarioReenviado
                        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        .replace("\"", "&quot;").replace("'", "&#39;");
                // Bloque de seguridad (hallazgo de Joel): "destino" ya viene validado por
                // ControladorAutenticacionServlet (solo rutas internas de /sgcpc) como atributo de
                // request -- igual se escapa para el atributo HTML (puede traer "&" de su query string).
                String destinoFicha = (String) request.getAttribute("destino");
                String destinoEscapado = destinoFicha == null ? "" : destinoFicha
                        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        .replace("\"", "&quot;").replace("'", "&#39;");
            %>
            <%-- V1d (pedido de Joel): novalidate -- la validación de campos vacíos la maneja el
                 script de abajo (misma alerta suave + sacudida + borde rojo que el resto de los
                 mensajes de esta pantalla) en vez de la burbuja nativa del navegador. Si los dos
                 campos vienen completos, el formulario se envía exactamente igual que antes. --%>
            <form action="${pageContext.request.contextPath}/acceso" method="post" novalidate>
                <input type="hidden" name="tokenCsrf" value="${sessionScope.tokenCsrf}">
                <% if (!destinoEscapado.isEmpty()) { %>
                <input type="hidden" name="destino" value="<%= destinoEscapado %>">
                <% } %>
                <div class="campo-con-etiqueta">
                    <label for="campoUsuario" class="etiqueta-campo">Usuario</label>
                    <div class="input-group campo-ingreso">
                        <div class="input-group-prepend">
                            <span class="input-group-text"><i class="fas fa-user"></i></span>
                        </div>
                        <input type="text" id="campoUsuario" name="nombreUsuario" class="form-control"
                               placeholder="Usuario" value="<%= nombreUsuarioEscapado %>" autocomplete="username" required>
                    </div>
                </div>
                <div class="campo-con-etiqueta">
                    <label for="campoContrasena" class="etiqueta-campo">Contraseña</label>
                    <div class="input-group campo-ingreso">
                        <div class="input-group-prepend">
                            <span class="input-group-text"><i class="fas fa-lock"></i></span>
                        </div>
                        <input type="password" id="campoContrasena" name="contrasena" class="form-control"
                               placeholder="Contraseña" autocomplete="current-password" required>
                    </div>
                </div>
                <%-- RF36/RF37 (hallazgo de Joel): quedó mal desde D-151 -- restablecer contraseñas
                     es exclusivo del administrador, el gerente ya no tiene acceso a Usuarios. --%>
                <p class="texto-olvido">¿Olvidó su contraseña? Pídasela a un administrador.</p>

                <button type="submit" class="btn boton-ingresar">Ingresar al sistema</button>
            </form>
        </div>
        </div>
    </div>
</div>
<p class="pie-ingreso"><strong>SGCPC</strong> · Sistema de Gestión de Créditos Personales y Cobranzas</p>

<script src="${pageContext.request.contextPath}/recursos/jquery/dist/jquery.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/bootstrap/dist/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/adminlte/dist/js/adminlte.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/sweetalert2/dist/sweetalert2.all.min.js"></script>
<%--
    V1c/V1d (pedido de Joel): script propio de esta pantalla (no el recursos/js/campo-contrasena.js
    compartido -- ese lo usan otras pantallas con otro texto/estilo para el aviso de Bloq Mayús,
    y acá se pidió uno nuevo con ícono y palabras distintas; tocar el archivo compartido
    afectaría esas otras pantallas). Reimplementa el botón del ojo (con texto VER/OCULTAR) y
    agrega: luz que sigue al mouse, sacudida/entrada de la tarjeta, borde rojo de los campos
    hasta que se escribe, aviso de Bloq Mayús propio, foco inicial, validación de campos vacíos
    y el botón "Ingresando..." con spinner sin cancelar el submit.
--%>
<script>
    (function () {
        'use strict';

        function prefiereMenosMovimiento() {
            return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        }

        document.addEventListener('DOMContentLoaded', function () {
            var tarjeta = document.querySelector('.tarjeta-ingreso');
            var campoUsuario = document.querySelector('#campoUsuario');
            var campoContrasena = document.querySelector('#campoContrasena');
            var formulario = document.querySelector('form');
            var boton = document.querySelector('.boton-ingresar');
            var cuerpoTarjeta = document.querySelector('.login-card-body');
            var botonOjo; // V1h: se asigna más abajo, al crear el botón del ojo -- lo necesitan
                           // el submit y el pageshow para deshabilitarlo/rehabilitarlo.
            var hayError = document.body.getAttribute('data-con-error') === 'true';
            var sinMovimiento = prefiereMenosMovimiento();

            // V1e: el borde luminoso de la tarjeta sigue al mouse con su posición RELATIVA a la
            // tarjeta (getBoundingClientRect en cada movimiento, aunque el mouse esté lejos -- el
            // propio radial-gradient se encarga de que, lejos, no se vea nada ahí). Si se pidió
            // menos movimiento, ni se escucha el evento: el borde queda fijo. V1g: la luz de
            // fondo que seguía al mouse se sacó -- el fondo ahora es fijo, así que este listener
            // ya no la actualiza.
            if (!sinMovimiento) {
                document.addEventListener('mousemove', function (evento) {
                    var envoltorio = tarjeta && tarjeta.closest('.envoltorio-tarjeta');
                    if (envoltorio) {
                        var rect = tarjeta.getBoundingClientRect();
                        envoltorio.style.setProperty('--bx', (evento.clientX - rect.left) + 'px');
                        envoltorio.style.setProperty('--by', (evento.clientY - rect.top) + 'px');
                    }
                });
            }

            function sacudirTarjeta() {
                if (!tarjeta || sinMovimiento) {
                    return;
                }
                tarjeta.classList.remove('sacudida');
                void tarjeta.offsetWidth; // fuerza un reflow para poder repetir la animación
                tarjeta.classList.add('sacudida');
            }

            function marcarCampoConError(campo) {
                if (!campo) {
                    return;
                }
                var grupo = campo.closest('.campo-ingreso');
                if (grupo) {
                    grupo.classList.add('campo-con-error');
                }
            }

            function limpiarErrorAlEscribir(campo) {
                if (!campo) {
                    return;
                }
                campo.addEventListener('input', function () {
                    var grupo = campo.closest('.campo-ingreso');
                    if (grupo) {
                        grupo.classList.remove('campo-con-error');
                    }
                });
            }

            function mostrarAlertaValidacion(texto) {
                if (!cuerpoTarjeta || !formulario) {
                    return;
                }
                var alerta = cuerpoTarjeta.querySelector('.alert');
                if (!alerta) {
                    alerta = document.createElement('div');
                    alerta.className = 'alert alert-danger';
                    cuerpoTarjeta.insertBefore(alerta, formulario);
                }
                alerta.textContent = texto;
            }

            // 5. Entrada suave sin error; sacudida corta con error -- nunca las dos juntas.
            if (tarjeta && !sinMovimiento) {
                tarjeta.classList.add(hayError ? 'sacudida' : 'entrada-suave');
            }

            // 1. Borde rojo en los dos campos hasta que el usuario empiece a escribir.
            if (hayError) {
                marcarCampoConError(campoUsuario);
                marcarCampoConError(campoContrasena);
            }
            limpiarErrorAlEscribir(campoUsuario);
            limpiarErrorAlEscribir(campoContrasena);

            // 6. Foco automático: Usuario si está vacío; Contraseña si ya tiene texto (autofill).
            if (campoUsuario && campoUsuario.value.trim() === '') {
                campoUsuario.focus();
            } else if (campoContrasena) {
                campoContrasena.focus();
            }

            // 3. Aviso de Bloq Mayús propio de esta pantalla (ícono + texto pedidos por Joel).
            if (campoContrasena) {
                var aviso = document.createElement('small');
                aviso.className = 'aviso-mayus';
                aviso.hidden = true;
                aviso.innerHTML = '<i class="fas fa-exclamation-triangle" aria-hidden="true"></i> Bloq Mayús activado';
                var grupoContrasena = campoContrasena.closest('.campo-ingreso');
                if (grupoContrasena && grupoContrasena.parentNode) {
                    grupoContrasena.parentNode.insertBefore(aviso, grupoContrasena.nextSibling);
                }
                var actualizarAvisoMayus = function (evento) {
                    aviso.hidden = !(typeof evento.getModifierState === 'function'
                        && evento.getModifierState('CapsLock'));
                };
                campoContrasena.addEventListener('keydown', actualizarAvisoMayus);
                campoContrasena.addEventListener('keyup', actualizarAvisoMayus);
                campoContrasena.addEventListener('blur', function () {
                    aviso.hidden = true;
                });
            }

            // Botón del ojo (mismo comportamiento que recursos/js/campo-contrasena.js, propio de
            // esta pantalla para no depender del archivo compartido) -- V1d: con texto VER/OCULTAR.
            if (campoContrasena) {
                var grupoOjo = campoContrasena.closest('.campo-ingreso');
                if (grupoOjo) {
                    var icono = document.createElement('i');
                    icono.className = 'fas fa-eye';
                    var textoOjo = document.createElement('span');
                    textoOjo.textContent = 'Ver';
                    botonOjo = document.createElement('button');
                    botonOjo.type = 'button';
                    botonOjo.className = 'btn';
                    botonOjo.tabIndex = -1;
                    botonOjo.setAttribute('aria-label', 'Mostrar/Ocultar contraseña');
                    botonOjo.appendChild(icono);
                    botonOjo.appendChild(textoOjo);
                    botonOjo.addEventListener('click', function () {
                        var oculto = campoContrasena.type === 'password';
                        campoContrasena.type = oculto ? 'text' : 'password';
                        icono.classList.toggle('fa-eye', !oculto);
                        icono.classList.toggle('fa-eye-slash', oculto);
                        textoOjo.textContent = oculto ? 'Ocultar' : 'Ver';
                    });
                    var anexoOjo = document.createElement('div');
                    anexoOjo.className = 'input-group-append';
                    anexoOjo.appendChild(botonOjo);
                    grupoOjo.appendChild(anexoOjo);
                }
            }

            // 7 (V1d) + 4 (V1c): valida que los dos campos tengan texto antes de enviar: si
            // falta alguno, NO se envía -- misma alerta suave, sacudida y borde rojo en el campo
            // vacío. Si están completos, sigue el flujo de siempre: deshabilitar DESPUÉS de que
            // arranca el submit (sin cancelarlo) y volver a habilitar si el usuario vuelve atrás
            // (bfcache / pageshow). V1h: mientras se envía, los campos pasan a readonly (NO
            // disabled -- un campo disabled no manda su valor, y el ingreso fallaría) y el botón
            // del ojo queda deshabilitado; todo vuelve a la normalidad en pageshow, junto con el
            // botón principal.
            if (formulario && boton) {
                formulario.addEventListener('submit', function (evento) {
                    var usuarioVacio = campoUsuario && campoUsuario.value.trim() === '';
                    var contrasenaVacia = campoContrasena && campoContrasena.value.trim() === '';
                    if (usuarioVacio || contrasenaVacia) {
                        evento.preventDefault();
                        mostrarAlertaValidacion('Ingrese su usuario y contraseña.');
                        sacudirTarjeta();
                        if (usuarioVacio) {
                            marcarCampoConError(campoUsuario);
                        }
                        if (contrasenaVacia) {
                            marcarCampoConError(campoContrasena);
                        }
                        return;
                    }
                    window.setTimeout(function () {
                        boton.disabled = true;
                        boton.innerHTML = '<span class="spinner-ingresar" aria-hidden="true"></span>Ingresando…';
                        if (campoUsuario) {
                            campoUsuario.readOnly = true;
                        }
                        if (campoContrasena) {
                            campoContrasena.readOnly = true;
                        }
                        if (botonOjo) {
                            botonOjo.disabled = true;
                        }
                    }, 0);
                });
            }
            window.addEventListener('pageshow', function () {
                if (boton) {
                    boton.disabled = false;
                    boton.textContent = 'Ingresar al sistema';
                }
                if (campoUsuario) {
                    campoUsuario.readOnly = false;
                }
                if (campoContrasena) {
                    campoContrasena.readOnly = false;
                }
                if (botonOjo) {
                    botonOjo.disabled = false;
                }
            });
        });
    })();
</script>
</body>
</html>
