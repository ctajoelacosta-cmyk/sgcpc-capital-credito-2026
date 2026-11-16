/**
 * V11 (pedido de Joel): "Buscar en la tabla..." -- reusable para cualquier reporte/lista que NO
 * tenga buscador de texto del lado del servidor. Filtra, SOLO en el navegador, las filas que YA
 * están cargadas en la tabla -- no manda ningún pedido nuevo, no toca la consulta ni los
 * totales (si hay <tfoot>, nunca se oculta: este script solo recorre <tbody>).
 *
 * Convención, mismo estilo que recursos/js/listado-vivo.js:
 *   - El campo: <input type="search" data-buscar-en-tabla="idDeLaTabla" placeholder="Buscar en
 *     la tabla…">
 *   - La tabla: <table id="idDeLaTabla">...<tbody> filas normales +, como ÚLTIMA fila del
 *     tbody (arriba del <tfoot>, nunca afuera de la tabla), ya en el HTML (oculta):
 *       <tr class="buscar-en-tabla-sin-coincidencias d-none">
 *         <td colspan="N" class="text-center text-muted">Sin coincidencias.</td>
 *       </tr>
 *     Este script solo muestra/oculta esa fila -- nunca la crea, así el colspan siempre es el
 *     correcto para cada tabla (lo decide el propio JSP, que conoce su cantidad real de
 *     columnas).
 *   - Opcional: un elemento con data-buscar-en-tabla-conteo="idDeLaTabla" (mismo id, en
 *     cualquier parte de la página, no necesita estar al lado del campo) -- V11 punto 5 (pedido
 *     de Joel): mientras hay texto buscado, se completa con "Mostrando X de N" (X = filas
 *     visibles, N = total de filas de esa tabla); sin texto, queda oculto. Así, si la tabla
 *     tiene además una fila de totales (tfoot) calculada por el servidor para TODO el período,
 *     queda claro que esos totales no son solo de lo que se ve filtrado en este momento.
 *   - Opcional: un elemento con data-buscar-en-tabla-aviso-exportar="idDeLaTabla" y, adentro,
 *     un elemento con data-buscar-en-tabla-aviso-exportar-texto -- V15 (pedido de Joel): mismo
 *     criterio que el conteo de arriba (visible solo mientras hay texto buscado), pero con el
 *     aviso "PDF y Excel incluyen las N filas filtradas, no solo las X de la búsqueda." (mismos
 *     X/N que "Mostrando X de N"), para que quede claro que exportar no exporta solo lo que se
 *     ve filtrado en la tabla en ese momento.
 *   - Opcional: cualquier enlace "Limpiar" de la página con el atributo
 *     data-buscar-en-tabla-limpiar (sin valor) -- V11-bis (pedido de Joel): al hacer clic, borra
 *     también el texto guardado en sessionStorage para esta página (ver abajo), así una página
 *     recién "limpiada" no vuelve a aplicar un filtro de tabla viejo.
 *
 * V11-bis (pedido de Joel): el texto buscado se guarda en sessionStorage con una clave por RUTA
 * de la página ("buscarTabla:" + location.pathname, sin query string -- una sola caja de
 * búsqueda por pantalla en todo el sistema, no hace falta nada más específico) para que no se
 * pierda si el usuario aprieta el botón "Aplicar filtros"/"Buscar" del formulario (que recarga
 * la página entera). Al cargar, si hay algo guardado para esta ruta, se restaura en el campo Y
 * se aplica el filtro de una -- así la tabla ya aparece filtrada, sin que el usuario tenga que
 * volver a escribir. Si el campo queda vacío (el usuario lo borra, o usa la "x" nativa del
 * input type="search", que ya dispara su propio evento "input") se borra también de
 * sessionStorage. Todo con try/catch: en una ventana privada o con el almacenamiento
 * deshabilitado, sessionStorage puede no estar disponible o tirar una excepción -- si eso pasa,
 * el campo sigue funcionando igual, solo que no recuerda nada entre una recarga y la otra.
 */
(function () {
    'use strict';

    function claveAlmacenamiento() {
        return 'buscarTabla:' + window.location.pathname;
    }

    function leerTextoGuardado() {
        try {
            return window.sessionStorage.getItem(claveAlmacenamiento());
        } catch (error) {
            return null;
        }
    }

    function guardarTexto(texto) {
        try {
            if (texto) {
                window.sessionStorage.setItem(claveAlmacenamiento(), texto);
            } else {
                window.sessionStorage.removeItem(claveAlmacenamiento());
            }
        } catch (error) {
            // Sin sessionStorage disponible (ventana privada, almacenamiento deshabilitado) --
            // el filtro en sí ya se aplicó antes de llegar acá, solo no queda guardado.
        }
    }

    function borrarTextoGuardado() {
        try {
            window.sessionStorage.removeItem(claveAlmacenamiento());
        } catch (error) {
            // Nada para hacer -- ver guardarTexto.
        }
    }

    /**
     * B220-bis (pedido de Joel, 15/11/2026): pildoras de grupo y filas que despliegan su detalle.
     * Las dos cosas viven aca, y no en un script de la pantalla, porque este script es el que ya
     * decide QUE SE VE y QUE SE CUENTA en esta tabla -- con dos dueños, el buscador y el filtro de
     * grupo se pisarian y el "Mostrando X de N" diria cualquier cosa.
     *
     * Convencion, toda OPCIONAL (sin estas marcas, el script hace exactamente lo de antes):
     *   - Las pildoras: <button data-buscar-en-tabla-grupo-boton="idDeLaTabla" data-grupo="clave">,
     *     con data-grupo="todos" para la de "Todos".
     *   - Las filas de datos y las de encabezado de grupo llevan data-grupo="clave".
     *   - Las filas de DETALLE (el formulario que se despliega) llevan la clase
     *     buscar-en-tabla-detalle, data-grupo de su fila, y el boton que las abre lleva
     *     data-buscar-en-tabla-detalle-boton="idDelDetalle". El detalle se muestra solo con la
     *     clase buscar-en-tabla-detalle-abierta (el CSS esta en sgcpc-tema.css), y se abre UNO A
     *     LA VEZ. Cualquier cambio de filtro los cierra todos: un formulario abierto que el filtro
     *     acaba de esconder es peor que ninguno.
     *
     * IMPORTANTE: el detalle se oculta por CSS, nunca se saca del HTML. Los formularios de
     * /parametros llevan el token CSRF, y pruebas/humo_http.sh lo lee del GET /parametros para sus
     * cuatro POST a /parametros/actualizar-valor: si el formulario no estuviera en el HTML, el
     * token desapareceria y el humo fallaria por el andamio, no por el producto (la trampa de
     * B197).
     */
    // B220-bis: el grupo elegido se recuerda igual que el texto (V11-bis), con una clave por
    // RUTA y por tabla -- asi volver del guardado no pierde el grupo que se estaba mirando. Mismo
    // try/catch: sin sessionStorage disponible, el filtro sigue funcionando, solo no recuerda.
    function claveGrupo(idTabla) {
        return 'buscarTablaGrupo:' + window.location.pathname + ':' + idTabla;
    }

    function leerGrupoGuardado(idTabla) {
        try {
            return window.sessionStorage.getItem(claveGrupo(idTabla)) || 'todos';
        } catch (error) {
            return 'todos';
        }
    }

    function guardarGrupo(idTabla, grupo) {
        try {
            if (grupo && grupo !== 'todos') {
                window.sessionStorage.setItem(claveGrupo(idTabla), grupo);
            } else {
                window.sessionStorage.removeItem(claveGrupo(idTabla));
            }
        } catch (error) {
            // Ver guardarTexto.
        }
    }

    function inicializarBuscarEnTabla(campo) {
        var idTabla = campo.getAttribute('data-buscar-en-tabla');
        var tabla = document.getElementById(idTabla);
        if (!tabla) {
            return;
        }
        var filaSinCoincidencias = tabla.querySelector('.buscar-en-tabla-sin-coincidencias');
        // B220 (15/11/2026): las filas de ENCABEZADO DE GRUPO (buscar-en-tabla-grupo) no son
        // datos: no se filtran ni se cuentan en "Mostrando X de N" -- si se contaran, la pantalla
        // de parametros diria "de 60" donde hay 52 parametros. Se muestran u ocultan segun si
        // quedo alguna fila visible en su grupo (ver actualizarEncabezadosDeGrupo). En las
        // pantallas que no usan esa clase, filasGrupo queda vacio y nada cambia. B220-bis: lo
        // mismo para las filas de DETALLE, que son el formulario de una fila y no otra fila.
        var filas = tabla.querySelectorAll('tbody tr:not(.buscar-en-tabla-sin-coincidencias)'
                + ':not(.buscar-en-tabla-grupo):not(.buscar-en-tabla-detalle)');
        var filasGrupo = tabla.querySelectorAll('tbody tr.buscar-en-tabla-grupo');
        var filasDetalle = tabla.querySelectorAll('tbody tr.buscar-en-tabla-detalle');
        var botonesGrupo = document.querySelectorAll('[data-buscar-en-tabla-grupo-boton="' + idTabla + '"]');
        var contadorVisibles = document.querySelector('[data-buscar-en-tabla-conteo="' + idTabla + '"]');
        var avisoExportar = document.querySelector('[data-buscar-en-tabla-aviso-exportar="' + idTabla + '"]');
        var textoAvisoExportar = avisoExportar ? avisoExportar.querySelector('[data-buscar-en-tabla-aviso-exportar-texto]') : null;
        var grupoActivo = 'todos';

        function cerrarDetalles() {
            for (var d = 0; d < filasDetalle.length; d++) {
                filasDetalle[d].classList.remove('buscar-en-tabla-detalle-abierta');
            }
        }

        function actualizarEncabezadosDeGrupo() {
            for (var g = 0; g < filasGrupo.length; g++) {
                var encabezado = filasGrupo[g];
                // B220-bis, segunda vuelta (prueba de Joel, 15/11/2026): con una pildora elegida,
                // el encabezado repite palabra por palabra lo que ya dice la pildora activa. Los
                // encabezados solo tienen sentido cuando se ven varios grupos juntos, o sea en
                // "Todos".
                if (grupoActivo !== 'todos') {
                    encabezado.classList.add('d-none');
                    continue;
                }
                var algunaVisible = false;
                var fila = encabezado.nextElementSibling;
                while (fila && !fila.classList.contains('buscar-en-tabla-grupo')) {
                    if (!fila.classList.contains('buscar-en-tabla-sin-coincidencias')
                            && !fila.classList.contains('buscar-en-tabla-detalle')
                            && !fila.classList.contains('d-none')) {
                        algunaVisible = true;
                        break;
                    }
                    fila = fila.nextElementSibling;
                }
                encabezado.classList.toggle('d-none', !algunaVisible);
            }
        }

        function aplicarFiltro(textoBuscado) {
            var algunaFilaVisible = false;
            var cantidadVisible = 0;
            // B220-bis: el total con el que se compara ("de N") es el del GRUPO elegido, no el de
            // la tabla entera: con la pildora "Caja" puesta, "Mostrando 1 de 52" seria mentira.
            var totalDelGrupo = 0;
            for (var i = 0; i < filas.length; i++) {
                var grupoDeLaFila = filas[i].getAttribute('data-grupo');
                var coincideGrupo = grupoActivo === 'todos' || grupoDeLaFila === grupoActivo;
                if (coincideGrupo) {
                    totalDelGrupo++;
                }
                var coincide = coincideGrupo
                        && (!textoBuscado || filas[i].textContent.toLowerCase().indexOf(textoBuscado) !== -1);
                filas[i].classList.toggle('d-none', !coincide);
                if (coincide) {
                    algunaFilaVisible = true;
                    cantidadVisible++;
                }
            }
            // Las filas de detalle siguen la visibilidad de su grupo: una que el filtro escondio no
            // puede quedar dibujada. (Ademas estan cerradas: cualquier cambio de filtro las cierra.)
            for (var e = 0; e < filasDetalle.length; e++) {
                var grupoDelDetalle = filasDetalle[e].getAttribute('data-grupo');
                filasDetalle[e].classList.toggle('d-none',
                        !(grupoActivo === 'todos' || grupoDelDetalle === grupoActivo));
            }
            actualizarEncabezadosDeGrupo();
            if (filaSinCoincidencias) {
                filaSinCoincidencias.classList.toggle('d-none',
                        algunaFilaVisible || (!textoBuscado && grupoActivo === 'todos'));
            }
            if (contadorVisibles) {
                // El contador aparece solo al BUSCAR, como siempre: con una pildora activa y sin
                // texto diria "Mostrando 16 de 16", que es ruido (la pildora ya dice el grupo).
                // Lo que cambio con B220-bis es la N: es el total del GRUPO elegido, no de la tabla.
                if (textoBuscado) {
                    contadorVisibles.textContent = 'Mostrando ' + cantidadVisible + ' de ' + totalDelGrupo;
                    contadorVisibles.classList.remove('d-none');
                } else {
                    contadorVisibles.classList.add('d-none');
                }
            }
            if (avisoExportar) {
                if (textoBuscado) {
                    if (textoAvisoExportar) {
                        textoAvisoExportar.textContent = 'PDF y Excel incluyen las ' + totalDelGrupo
                                + ' filas filtradas, no solo las ' + cantidadVisible + ' de la búsqueda.';
                    }
                    avisoExportar.classList.remove('d-none');
                } else {
                    avisoExportar.classList.add('d-none');
                }
            }
        }

        function activarGrupo(clave) {
            grupoActivo = clave || 'todos';
            for (var b = 0; b < botonesGrupo.length; b++) {
                botonesGrupo[b].classList.toggle('active',
                        botonesGrupo[b].getAttribute('data-grupo') === grupoActivo);
            }
        }

        campo.addEventListener('input', function () {
            var textoBuscado = campo.value.trim().toLowerCase();
            cerrarDetalles();
            aplicarFiltro(textoBuscado);
            guardarTexto(textoBuscado);
        });

        for (var b = 0; b < botonesGrupo.length; b++) {
            botonesGrupo[b].addEventListener('click', function (evento) {
                activarGrupo(evento.currentTarget.getAttribute('data-grupo'));
                cerrarDetalles();
                aplicarFiltro(campo.value.trim().toLowerCase());
                guardarGrupo(idTabla, grupoActivo);
            });
        }

        // B220-bis: "uno abierto a la vez". El boton abre el detalle de SU fila y cierra el resto;
        // tocarlo de nuevo lo cierra. aria-expanded acompaña, para que no sea solo visual.
        var botonesDetalle = tabla.querySelectorAll('[data-buscar-en-tabla-detalle-boton]');
        for (var p = 0; p < botonesDetalle.length; p++) {
            botonesDetalle[p].addEventListener('click', function (evento) {
                var boton = evento.currentTarget;
                var detalle = document.getElementById(boton.getAttribute('data-buscar-en-tabla-detalle-boton'));
                if (!detalle) {
                    return;
                }
                var estabaAbierto = detalle.classList.contains('buscar-en-tabla-detalle-abierta');
                cerrarDetalles();
                for (var q = 0; q < botonesDetalle.length; q++) {
                    botonesDetalle[q].setAttribute('aria-expanded', 'false');
                }
                if (!estabaAbierto) {
                    detalle.classList.add('buscar-en-tabla-detalle-abierta');
                    boton.setAttribute('aria-expanded', 'true');
                    var primerCampo = detalle.querySelector('input:not([type="hidden"]), select, textarea');
                    if (primerCampo) {
                        primerCampo.focus();
                    }
                }
            });
        }

        // V11-bis + B220-bis: se restauran el grupo Y el texto guardados para esta pantalla, en ese
        // orden, y se aplica el filtro una sola vez -- asi volver del guardado (PRG, recarga
        // completa) no pierde el grupo que el administrador estaba mirando.
        if (botonesGrupo.length > 0) {
            activarGrupo(leerGrupoGuardado(idTabla));
        }
        var textoGuardado = leerTextoGuardado();
        if (textoGuardado) {
            campo.value = textoGuardado;
        }
        aplicarFiltro((campo.value || '').trim().toLowerCase());
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('[data-buscar-en-tabla]');
        for (var i = 0; i < campos.length; i++) {
            inicializarBuscarEnTabla(campos[i]);
        }

        var enlacesLimpiar = document.querySelectorAll('[data-buscar-en-tabla-limpiar]');
        for (var j = 0; j < enlacesLimpiar.length; j++) {
            enlacesLimpiar[j].addEventListener('click', function () {
                borrarTextoGuardado();
            });
        }
    });
})();
