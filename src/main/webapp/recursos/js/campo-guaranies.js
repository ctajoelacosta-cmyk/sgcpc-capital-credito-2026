/**
 * Reutilizable en cualquier pantalla: agregar la clase "campo-guaranies" a
 * un <input type="text"> de monto en guaraníes hace que (a) se vea con
 * puntos de miles mientras se escribe (3000000 -> 3.000.000) y (b) debajo
 * del campo aparezca, en gris y con un pequeño retraso, el monto en letras
 * ("tres millones de guaraníes"), pedido a GET /utilidades/letras.
 *
 * Requiere que window.CONTEXTO_APP este definido (ver plantilla/pie.jsp)
 * con el contextPath de la aplicacion, para armar la URL del pedido.
 */
(function () {
    'use strict';

    var RETRASO_MS = 300;

    // Encontrado en la prueba de E3: con la sesion vencida, FiltroAutenticacion redirigia
    // fetch() a /acceso y el JS mostraba el HTML entero del login como si fuera el texto en
    // letras. El filtro ahora reconoce una peticion AJAX (encabezado X-Requested-With) y
    // responde 401 en vez de redirigir. El aviso en si (SweetAlert + ir al login) lo da
    // aviso-sesion.js, que ya lo tiene (window.avisarSesionVencida) -- no se duplica aca.
    function avisarSesionVencida() {
        if (typeof window.avisarSesionVencida === 'function') {
            window.avisarSesionVencida();
            return;
        }
        window.location.href = (window.CONTEXTO_APP || '') + '/acceso';
    }

    function soloDigitos(texto) {
        return (texto || '').replace(/\D/g, '');
    }

    function conPuntosDeMiles(digitos) {
        if (digitos === '') {
            return '';
        }
        return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }

    function elementoLetras(campo) {
        var siguiente = campo.nextElementSibling;
        if (siguiente && siguiente.classList.contains('campo-guaranies-letras')) {
            return siguiente;
        }
        var nuevo = document.createElement('small');
        nuevo.className = 'campo-guaranies-letras form-text text-muted';
        campo.parentNode.insertBefore(nuevo, campo.nextSibling);
        return nuevo;
    }

    function pedirLetras(campo, etiquetaLetras, digitos) {
        if (digitos === '') {
            etiquetaLetras.textContent = '';
            return;
        }
        var contextoApp = window.CONTEXTO_APP || '';
        fetch(contextoApp + '/utilidades/letras?monto=' + encodeURIComponent(digitos), {
            headers: {'X-Requested-With': 'XMLHttpRequest'}
        })
            .then(function (respuesta) {
                if (respuesta.status === 401) {
                    avisarSesionVencida();
                    return null;
                }
                // Punto 5 (aviso-sesion.js): cualquier peticion exitosa al servidor reinicia el
                // temporizador de la sesion, igual que una carga de pagina nueva.
                if (typeof window.reiniciarTemporizadorSesion === 'function') {
                    window.reiniciarTemporizadorSesion();
                }
                return respuesta.ok ? respuesta.text() : null;
            })
            .then(function (texto) {
                // null: sesion vencida (ya se avisa aparte) o cualquier otro error -- no se
                // muestra nada debajo del campo, nunca el cuerpo crudo de la respuesta.
                etiquetaLetras.textContent = texto === null ? '' : texto;
            })
            .catch(function () {
                etiquetaLetras.textContent = '';
            });
    }

    function inicializarCampo(campo) {
        var etiquetaLetras = elementoLetras(campo);
        var temporizador = null;

        // Si el campo ya trae un valor (ej. al reabrir un formulario con error), formatear
        // y pedir las letras una vez al cargar la pagina.
        if (campo.value) {
            campo.value = conPuntosDeMiles(soloDigitos(campo.value));
            pedirLetras(campo, etiquetaLetras, soloDigitos(campo.value));
        }

        campo.addEventListener('input', function () {
            var posicionDesdeElFinal = campo.value.length - campo.selectionStart;
            var digitos = soloDigitos(campo.value);
            campo.value = conPuntosDeMiles(digitos);
            var nuevaPosicion = Math.max(0, campo.value.length - posicionDesdeElFinal);
            campo.setSelectionRange(nuevaPosicion, nuevaPosicion);

            if (temporizador) {
                clearTimeout(temporizador);
            }
            temporizador = setTimeout(function () {
                pedirLetras(campo, etiquetaLetras, digitos);
            }, RETRASO_MS);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('.campo-guaranies');
        for (var i = 0; i < campos.length; i++) {
            inicializarCampo(campos[i]);
        }
    });
})();
