/**
 * 13/10/2026 (pedido de Joel): el campo "Cliente u operación" (RF32, 6 informes) no aplicaba
 * los filtros al presionar Enter -- con varios botones "submit" asociados al mismo formulario
 * (Aplicar filtros / PDF / Excel-CSV, algunos fuera del <form> vía el atributo form="...", ver
 * informe-cobros.jsp y similares), el botón "por defecto" que el navegador usa para el envío
 * implícito de Enter no es siempre el primero en el HTML -- varía según navegador. Se ata un
 * manejador directo (mismo criterio que campo-guaranies.js) que fuerza el envío por GET del
 * propio formulario, nunca por un botón de exportar.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('input[name="clienteUOperacion"]');
        for (var i = 0; i < campos.length; i++) {
            (function (campo) {
                campo.addEventListener('keydown', function (evento) {
                    if (evento.key !== 'Enter') {
                        return;
                    }
                    evento.preventDefault();
                    var formulario = campo.form;
                    if (formulario) {
                        formulario.submit();
                    }
                });
            })(campos[i]);
        }
    });
})();
