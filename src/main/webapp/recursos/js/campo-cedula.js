/**
 * Reutilizable: agregar la clase "campo-cedula" a un <input type="text">
 * hace que se vea con puntos de miles mientras se escribe (4150164 ->
 * 4.150.164), igual que los campos de monto (campo-guaranies.js). El
 * servidor ya normaliza la cédula (ServicioClientes.normalizarCedula quita
 * puntos/espacios/guiones antes de guardar y de buscar), así que esto es
 * solo de presentación — no hace ningún pedido al servidor.
 */
(function () {
    'use strict';

    function soloDigitos(texto) {
        return (texto || '').replace(/\D/g, '');
    }

    function conPuntosDeMiles(digitos) {
        if (digitos === '') {
            return '';
        }
        return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }

    function inicializarCampo(campo) {
        if (campo.value) {
            campo.value = conPuntosDeMiles(soloDigitos(campo.value));
        }
        campo.addEventListener('input', function () {
            var posicionDesdeElFinal = campo.value.length - campo.selectionStart;
            campo.value = conPuntosDeMiles(soloDigitos(campo.value));
            var nuevaPosicion = Math.max(0, campo.value.length - posicionDesdeElFinal);
            campo.setSelectionRange(nuevaPosicion, nuevaPosicion);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('.campo-cedula');
        for (var i = 0; i < campos.length; i++) {
            inicializarCampo(campos[i]);
        }
    });
})();
