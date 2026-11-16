/**
 * Reutilizable en cualquier pantalla con un <input type="password">: no
 * hace falta ninguna clase ni atributo especial en el campo, se aplica
 * solo a todo input[type="password"] de la pagina. Agrega automaticamente:
 * (a) un boton con icono de ojo (fa-eye/fa-eye-slash) para mostrar u
 * ocultar lo escrito, oculto por defecto; (b) un aviso en amarillo,
 * inicialmente oculto, cuando Bloq Mayus esta activado en ESE campo
 * (keydown/keyup con getModifierState('CapsLock')), que se apaga al salir
 * del campo (blur); y (c) B215/B216 (pedido de Joel, 15/11/2026): si el campo tiene
 * data-confirma-a="<id de otro campo>", avisa EN VIVO cuando lo escrito no coincide con ese
 * otro campo y no deja enviar el formulario hasta que coincidan (setCustomValidity, asi el
 * aviso sale por el mismo mecanismo de "Falta un dato" de plantilla/pie.jsp). Sin ese
 * atributo, nada de esto se activa: el resto de las pantallas con contrasena no cambia.
 */
(function () {
    'use strict';

    function alternarVisibilidad(campo, icono) {
        var oculto = campo.type === 'password';
        campo.type = oculto ? 'text' : 'password';
        icono.classList.toggle('fa-eye', !oculto);
        icono.classList.toggle('fa-eye-slash', oculto);
    }

    function agregarBotonOjo(campo, contenedor) {
        var icono = document.createElement('i');
        icono.className = 'fas fa-eye';

        var boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'btn btn-outline-secondary';
        boton.tabIndex = -1;
        boton.setAttribute('aria-label', 'Mostrar u ocultar la contraseña');
        boton.appendChild(icono);
        boton.addEventListener('click', function () {
            alternarVisibilidad(campo, icono);
        });

        var anexo = document.createElement('div');
        anexo.className = 'input-group-append';
        anexo.appendChild(boton);
        contenedor.appendChild(anexo);
    }

    // Bug encontrado en la prueba de E3: la clase de Bootstrap "d-block" es
    // "display: block !important", asi que aviso.style.display = 'none' (estilo en linea,
    // sin !important) nunca le ganaba a esa regla -- el aviso quedaba siempre visible, en
    // todos los campos, sin importar Bloq Mayus. Se fuerza el propio !important en linea
    // (unico nivel que le gana a un !important de una clase) en vez de usar esa clase.
    function mostrarAviso(aviso) {
        aviso.style.setProperty('display', 'block', 'important');
    }

    function ocultarAviso(aviso) {
        aviso.style.setProperty('display', 'none', 'important');
    }

    function agregarAvisoMayusculas(campo, contenedor) {
        var aviso = document.createElement('small');
        aviso.className = 'text-warning mt-1';
        aviso.textContent = 'Bloq Mayús está activado.';
        ocultarAviso(aviso);
        contenedor.parentNode.insertBefore(aviso, contenedor.nextSibling);

        function actualizar(evento) {
            var activado = typeof evento.getModifierState === 'function' && evento.getModifierState('CapsLock');
            if (activado) {
                mostrarAviso(aviso);
            } else {
                ocultarAviso(aviso);
            }
        }
        campo.addEventListener('keydown', actualizar);
        campo.addEventListener('keyup', actualizar);
        campo.addEventListener('blur', function () {
            ocultarAviso(aviso);
        });
    }

    /**
     * B216: el campo de confirmacion avisa mientras se escribe, en vez de esperar al servidor.
     * Antes, una confirmacion mal tipeada costaba un viaje completo y los tres campos volvian
     * vacios (un <input type="password"> no se rellena nunca: la contrasena no vuelve al
     * navegador dentro del HTML). Con el aviso en vivo el formulario ni se envia, asi que no hay
     * nada que volver a escribir.
     */
    function agregarVerificacionDeCoincidencia(campo, contenedor) {
        var otro = document.getElementById(campo.getAttribute('data-confirma-a'));
        if (!otro) {
            return;
        }
        var aviso = document.createElement('small');
        aviso.className = 'text-danger mt-1';
        aviso.setAttribute('role', 'status');
        aviso.textContent = 'Las dos contraseñas nuevas no coinciden.';
        ocultarAviso(aviso);
        contenedor.parentNode.insertBefore(aviso, contenedor.nextSibling);

        function revisar() {
            var difieren = campo.value !== '' && otro.value !== '' && campo.value !== otro.value;
            if (difieren) {
                mostrarAviso(aviso);
                campo.setCustomValidity('Las dos contraseñas nuevas no coinciden.');
            } else {
                ocultarAviso(aviso);
                campo.setCustomValidity('');
            }
        }
        campo.addEventListener('input', revisar);
        otro.addEventListener('input', revisar);
    }

    function inicializarCampo(campo) {
        var contenedor = campo.closest('.input-group');
        if (!contenedor) {
            contenedor = document.createElement('div');
            contenedor.className = 'input-group';
            campo.parentNode.insertBefore(contenedor, campo);
            contenedor.appendChild(campo);
        }
        agregarBotonOjo(campo, contenedor);
        agregarAvisoMayusculas(campo, contenedor);
        if (campo.hasAttribute('data-confirma-a')) {
            agregarVerificacionDeCoincidencia(campo, contenedor);
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('input[type="password"]');
        for (var i = 0; i < campos.length; i++) {
            inicializarCampo(campos[i]);
        }
    });
})();
