/**
 * Bloque 7/E10 (D-136, pedido de Joel): mecanismo reutilizable de "listado vivo" -- búsqueda en
 * vivo (300 ms de debounce en campos de texto, inmediata en selects/fechas) + paginación, todo
 * por AJAX contra la MISMA ruta y los MISMOS filtros que ya usa el formulario: el servidor arma
 * la consulta una sola vez (ControladorClientesServlet.mostrarLista) y decide, según
 * PeticionAjax.esAjax, si devuelve la página completa o solo el fragmento de resultados -- este
 * script no duplica ninguna lógica de filtrado ni de armado de filas, solo reemplaza el
 * contenido de un contenedor con el HTML que el servidor ya sabe generar.
 *
 * Uso en cualquier otro listado del sistema:
 *   - El <form> de filtros lleva method="get", su action apunta a la ruta del listado, y el
 *     atributo data-listado-vivo="idDelContenedorDeResultados".
 *   - Un <div id="idDelContenedorDeResultados"> con el fragmento inicial ya renderizado por el
 *     servidor (para que funcione igual sin JavaScript).
 *   - Opcional, dentro del form: un elemento con class="listado-vivo-indicador" (se muestra/oculta
 *     mientras hay un pedido en curso, ej. "Buscando...").
 *   - Opcional: un enlace class="listado-vivo-limpiar" (dentro del form, o afuera con
 *     data-listado-vivo-de="idDelForm") que vacía los campos a su valor por defecto y dispara la
 *     búsqueda al toque -- en cada <select> cuya opción vacía NO sea "sin filtro" (ej. un Estado
 *     que por defecto es "Activos"), agregar data-valor-defecto con ese valor.
 *   - Los enlaces de paginación que arma el servidor llevan class="listado-vivo-pagina"
 *     data-pagina="N" y un href real (respaldo sin JavaScript); este script los intercepta con
 *     un solo listener delegado en el contenedor (sigue funcionando después de cada reemplazo).
 *   - Los botones que no son de búsqueda (ej. "Informe PDF"/"Excel-CSV" con formaction propio)
 *     se detectan por el atributo formaction del botón que disparó el submit (evento.submitter) y
 *     se dejan pasar sin interceptar -- toman los filtros actuales del formulario, como siempre.
 *   - Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c): cada vez que el
 *     contenedor se actualiza dispara un evento "listado-vivo:actualizado" (bubbles) sobre sí
 *     mismo -- para una pantalla que necesite re-atar algo propio del fragmento nuevo (ej. Mora,
 *     el formulario de gestión de cada fila), sin que este script tenga que saber nada de eso.
 */
(function () {
    'use strict';

    var RETRASO_MS = 300;

    function avisarSesionVencida() {
        if (typeof window.avisarSesionVencida === 'function') {
            window.avisarSesionVencida();
            return;
        }
        window.location.href = (window.CONTEXTO_APP || '') + '/acceso';
    }

    function limpiarFormulario(form) {
        var campos = form.querySelectorAll('input[type="text"], input[type="search"], input[type="date"], select');
        for (var i = 0; i < campos.length; i++) {
            var campo = campos[i];
            if (campo.tagName === 'SELECT') {
                campo.value = campo.getAttribute('data-valor-defecto') || '';
                // Dispara 'change' aunque este script ya va a buscar de una: si el select tiene
                // su propia lógica en cascada (ej. ciudad según departamento, D-126), que se
                // actualice igual -- sin esto quedaban opciones de un departamento que ya no
                // está más seleccionado.
                campo.dispatchEvent(new Event('change', {bubbles: true}));
            } else {
                campo.value = '';
            }
        }
    }

    function inicializarListado(form) {
        var idContenedor = form.getAttribute('data-listado-vivo');
        var contenedor = document.getElementById(idContenedor);
        if (!contenedor) {
            return;
        }
        var indicador = form.querySelector('.listado-vivo-indicador');
        var temporizador = null;
        var pedidoEnCurso = 0;

        function mostrarBuscando(mostrar) {
            if (indicador) {
                indicador.classList.toggle('d-none', !mostrar);
            }
        }

        function buscar(pagina) {
            var idPedido = ++pedidoEnCurso;
            mostrarBuscando(true);
            var datos = new URLSearchParams(new FormData(form));
            datos.set('pagina', pagina || '1');
            fetch(form.getAttribute('action') + '?' + datos.toString(), {
                headers: {'X-Requested-With': 'XMLHttpRequest'}
            }).then(function (respuesta) {
                if (respuesta.status === 401) {
                    avisarSesionVencida();
                    return null;
                }
                // Punto 5 (aviso-sesion.js): cualquier pedido exitoso reinicia el temporizador de
                // sesión, igual que una carga de página nueva -- mismo criterio que campo-guaranies.js.
                if (typeof window.reiniciarTemporizadorSesion === 'function') {
                    window.reiniciarTemporizadorSesion();
                }
                return respuesta.ok ? respuesta.text() : null;
            }).then(function (html) {
                if (html === null || idPedido !== pedidoEnCurso) {
                    return;
                }
                contenedor.innerHTML = html;
                mostrarBuscando(false);
                // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1):
                // aviso para quien necesite re-atar algo que dependía de los elementos viejos
                // (ej. Mora, el formulario de gestión) -- este script sigue sin saber nada de lo
                // que hay adentro del fragmento, solo avisa que cambió.
                contenedor.dispatchEvent(new CustomEvent('listado-vivo:actualizado', {bubbles: true}));
            }).catch(function () {
                if (idPedido === pedidoEnCurso) {
                    mostrarBuscando(false);
                }
            });
        }

        function dispararConDebounce() {
            if (temporizador) {
                clearTimeout(temporizador);
            }
            temporizador = setTimeout(function () { buscar(1); }, RETRASO_MS);
        }

        function dispararDeInmediato() {
            if (temporizador) {
                clearTimeout(temporizador);
            }
            buscar(1);
        }

        var textos = form.querySelectorAll('input[type="text"], input[type="search"]');
        for (var i = 0; i < textos.length; i++) {
            textos[i].addEventListener('input', dispararConDebounce);
        }

        var inmediatos = form.querySelectorAll('select, input[type="date"]');
        for (var j = 0; j < inmediatos.length; j++) {
            inmediatos[j].addEventListener('change', dispararDeInmediato);
        }

        // El botón "Buscar" (o Enter en un campo de texto) también va por AJAX, salvo que el
        // botón que disparó el submit tenga su propio formaction (Informe PDF / Excel-CSV): esos
        // se dejan pasar como un submit normal, con los filtros actuales del formulario.
        form.addEventListener('submit', function (evento) {
            var boton = evento.submitter;
            if (boton && boton.hasAttribute('formaction')) {
                return;
            }
            evento.preventDefault();
            dispararDeInmediato();
        });

        // Paginación: un solo listener delegado en el contenedor, sigue funcionando después de
        // cada reemplazo de innerHTML (el contenedor en sí nunca se reemplaza, solo su contenido).
        contenedor.addEventListener('click', function (evento) {
            var enlace = evento.target.closest ? evento.target.closest('.listado-vivo-pagina') : null;
            if (!enlace) {
                return;
            }
            evento.preventDefault();
            if (temporizador) {
                clearTimeout(temporizador);
            }
            buscar(enlace.getAttribute('data-pagina'));
        });

        var enlacesLimpiar = document.querySelectorAll('.listado-vivo-limpiar');
        for (var k = 0; k < enlacesLimpiar.length; k++) {
            var enlace = enlacesLimpiar[k];
            var formularioAsociado = enlace.closest('form') === form
                    || enlace.getAttribute('data-listado-vivo-de') === form.id;
            if (!formularioAsociado) {
                continue;
            }
            enlace.addEventListener('click', function (evento) {
                evento.preventDefault();
                limpiarFormulario(form);
                dispararDeInmediato();
            });
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        var formularios = document.querySelectorAll('[data-listado-vivo]');
        for (var i = 0; i < formularios.length; i++) {
            inicializarListado(formularios[i]);
        }
    });
})();
