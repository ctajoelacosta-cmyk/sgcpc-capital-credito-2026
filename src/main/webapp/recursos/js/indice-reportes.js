/**
 * V13 (pedido de Joel): índice de Reportes -- buscador instantáneo (título + descripción +
 * palabras clave) combinado con píldoras de categoría, todo del lado del navegador. No toca
 * ninguna ruta, permiso ni qué reportes ve cada perfil -- eso lo sigue decidiendo el propio JSP
 * (los <% if %> de siempre, antes de que esta página llegue al navegador).
 *
 * Convención (solo dentro de reportes/indice.jsp):
 *   - El buscador: <input type="search" id="buscadorReportes">.
 *   - Cada tarjeta: un contenedor con data-tarjeta-reporte, data-categoria="clave" y
 *     data-palabras="sinónimos sin tildes" -- el título (data-tarjeta-reporte-titulo) y la
 *     descripción (data-tarjeta-reporte-descripcion) ya se leen solos, no hace falta repetirlos
 *     en data-palabras.
 *   - Cada sección: un contenedor con data-categoria-seccion que envuelve el título Y la fila de
 *     tarjetas de esa categoría -- se oculta entera si ninguna de sus tarjetas queda visible.
 *   - Cada píldora: data-categoria-boton="clave" ("todos" para la de "Todos").
 */
(function () {
    'use strict';

    function normalizar(texto) {
        return (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campoBuscador = document.getElementById('buscadorReportes');
        if (!campoBuscador) {
            return;
        }
        var botonBorrar = document.querySelector('.indice-reportes-buscador-borrar');
        var tarjetas = document.querySelectorAll('[data-tarjeta-reporte]');
        var secciones = document.querySelectorAll('[data-categoria-seccion]');
        var botonesCategoria = document.querySelectorAll('[data-categoria-boton]');
        var avisoSinResultados = document.getElementById('indiceReportesSinResultados');
        var spanTextoBuscado = document.getElementById('indiceReportesTextoBuscado');
        var categoriaActiva = 'todos';
        var textoAnterior = '';
        var i, s, t, b, k;

        function activarCategoria(clave) {
            categoriaActiva = clave;
            for (k = 0; k < botonesCategoria.length; k++) {
                botonesCategoria[k].classList.toggle('active', botonesCategoria[k].getAttribute('data-categoria-boton') === clave);
            }
        }

        // Texto normalizado de cada tarjeta, calculado una sola vez (no en cada tecla).
        for (i = 0; i < tarjetas.length; i++) {
            var tituloEl = tarjetas[i].querySelector('[data-tarjeta-reporte-titulo]');
            var descripcionEl = tarjetas[i].querySelector('[data-tarjeta-reporte-descripcion]');
            var palabrasClave = tarjetas[i].getAttribute('data-palabras') || '';
            var textoCompleto = (tituloEl ? tituloEl.textContent : '') + ' '
                    + (descripcionEl ? descripcionEl.textContent : '') + ' ' + palabrasClave;
            tarjetas[i].setAttribute('data-texto-normalizado', normalizar(textoCompleto));
        }

        function aplicarFiltros() {
            var textoBuscado = normalizar(campoBuscador.value.trim());
            var algunaTarjetaVisible = false;

            for (i = 0; i < tarjetas.length; i++) {
                var tarjeta = tarjetas[i];
                var coincideCategoria = categoriaActiva === 'todos'
                        || tarjeta.getAttribute('data-categoria') === categoriaActiva;
                var coincideTexto = !textoBuscado
                        || tarjeta.getAttribute('data-texto-normalizado').indexOf(textoBuscado) !== -1;
                var visible = coincideCategoria && coincideTexto;
                tarjeta.classList.toggle('d-none', !visible);
                if (visible) {
                    algunaTarjetaVisible = true;
                }
            }

            for (s = 0; s < secciones.length; s++) {
                var tarjetasDeSeccion = secciones[s].querySelectorAll('[data-tarjeta-reporte]');
                var algunaEnSeccion = false;
                for (t = 0; t < tarjetasDeSeccion.length; t++) {
                    if (!tarjetasDeSeccion[t].classList.contains('d-none')) {
                        algunaEnSeccion = true;
                        break;
                    }
                }
                secciones[s].classList.toggle('d-none', !algunaEnSeccion);
            }

            if (avisoSinResultados) {
                avisoSinResultados.classList.toggle('d-none', algunaTarjetaVisible);
                if (!algunaTarjetaVisible && spanTextoBuscado) {
                    spanTextoBuscado.textContent = campoBuscador.value.trim();
                }
            }

            if (botonBorrar) {
                botonBorrar.classList.toggle('d-none', !campoBuscador.value);
            }
        }

        // V13-bis (pedido de Joel): apenas aparece texto donde antes no había nada (primera
        // letra escrita), la categoría vuelve a "Todos" -- así buscar con una categoría distinta
        // ya elegida no da "No encontramos" solo porque esa categoría no tiene el reporte
        // buscado. Si el usuario elige una categoría CON texto ya escrito, se combinan igual que
        // siempre (esto no se dispara de nuevo mientras sigue escribiendo con una categoría ya
        // activa -- solo en la transición de "vacío" a "con texto").
        campoBuscador.addEventListener('input', function () {
            var textoActual = campoBuscador.value;
            if (!textoAnterior && textoActual && categoriaActiva !== 'todos') {
                activarCategoria('todos');
            }
            textoAnterior = textoActual;
            aplicarFiltros();
        });

        if (botonBorrar) {
            botonBorrar.addEventListener('click', function () {
                campoBuscador.value = '';
                textoAnterior = '';
                aplicarFiltros();
                campoBuscador.focus();
            });
        }

        for (b = 0; b < botonesCategoria.length; b++) {
            botonesCategoria[b].addEventListener('click', function (evento) {
                activarCategoria(evento.currentTarget.getAttribute('data-categoria-boton'));
                aplicarFiltros();
            });
        }

        // Punto 1 (pedido de Joel): "/" enfoca el buscador (si no se está escribiendo en otro
        // campo); Escape, con foco en el buscador y con texto, lo borra.
        document.addEventListener('keydown', function (evento) {
            var elementoActivo = document.activeElement;
            var escribiendoEnOtroCampo = elementoActivo && elementoActivo !== campoBuscador
                    && (elementoActivo.tagName === 'INPUT' || elementoActivo.tagName === 'TEXTAREA'
                            || elementoActivo.tagName === 'SELECT' || elementoActivo.isContentEditable);

            if (evento.key === '/' && elementoActivo !== campoBuscador && !escribiendoEnOtroCampo) {
                evento.preventDefault();
                campoBuscador.focus();
            } else if (evento.key === 'Escape' && elementoActivo === campoBuscador && campoBuscador.value) {
                campoBuscador.value = '';
                textoAnterior = '';
                aplicarFiltros();
            }
        });

        aplicarFiltros();
    });
})();
