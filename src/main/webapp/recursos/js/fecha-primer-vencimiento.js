/**
 * Reutilizable: agregar la clase "campo-primer-vencimiento" a un
 * <input type="date"> (con los feriados activos en el atributo
 * data-feriados, como JSON: [{"fecha":"2026-12-25","descripcion":"Navidad"}, ...])
 * hace que, apenas el oficial elige una fecha que cae en sábado, domingo o
 * feriado, se avise en el elemento siguiente con la clase "aviso-dia-inhabil"
 * por qué y cuál sería la fecha real de cobro (RN-19: el vencimiento se
 * corre al siguiente día hábil). Solo informa -- el corrimiento real lo hace
 * el servidor (ServicioMotorFinanciero.fechaHabil) al generar/regenerar el
 * plan.
 *
 * B4 (05/11/2026): ademas, si el formulario del campo lleva data-confirmar, le
 * deja esa misma fecha ya corrida en data-confirmar-valor-fecha, para el
 * {fecha} del cartel de confirmacion (plantilla/pie.jsp). Escribe el valor,
 * nunca la frase.
 */
(function () {
    'use strict';

    var NOMBRES_DIA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

    function buscarFeriado(fecha, feriados) {
        var iso = fecha.toISOString().slice(0, 10);
        for (var i = 0; i < feriados.length; i++) {
            if (feriados[i].fecha === iso) {
                return feriados[i].descripcion;
            }
        }
        return null;
    }

    function motivoNoHabil(fecha, feriados) {
        var diaSemana = fecha.getUTCDay();
        if (diaSemana === 0) {
            return 'domingo';
        }
        if (diaSemana === 6) {
            return 'sábado';
        }
        var feriado = buscarFeriado(fecha, feriados);
        return feriado ? 'feriado (' + feriado + ')' : null;
    }

    function esDiaHabil(fecha, feriados) {
        return motivoNoHabil(fecha, feriados) === null;
    }

    function siguienteDiaHabil(fecha, feriados) {
        var resultado = new Date(fecha.getTime());
        while (!esDiaHabil(resultado, feriados)) {
            resultado.setUTCDate(resultado.getUTCDate() + 1);
        }
        return resultado;
    }

    // D.2 (reprueba Lote 1): escapa antes de armar el innerHTML del aviso con el texto resaltado
    // de "fuera de plazo" -- defensivo, aunque el texto viene de datos internos (feriados
    // cargados por un administrador), no de lo que escribe quien usa este campo.
    function escaparHtml(texto) {
        var contenedor = document.createElement('div');
        contenedor.textContent = texto;
        return contenedor.innerHTML;
    }

    function comoDdMmAaaa(fecha) {
        var dd = ('0' + fecha.getUTCDate()).slice(-2);
        var mm = ('0' + (fecha.getUTCMonth() + 1)).slice(-2);
        return dd + '/' + mm + '/' + fecha.getUTCFullYear();
    }

    function inicializarCampo(campo) {
        var aviso = campo.nextElementSibling;
        while (aviso && !aviso.classList.contains('aviso-dia-inhabil')) {
            aviso = aviso.nextElementSibling;
        }
        if (!aviso) {
            return;
        }
        var feriados = [];
        try {
            feriados = JSON.parse(campo.getAttribute('data-feriados') || '[]');
        } catch (error) {
            feriados = [];
        }

        // B4 (B86, tramo transversal, 05/11/2026): el cartel de confirmación de "Generar
        // documentos"/"Regenerar" resume qué va a pasar, y el primer vencimiento EFECTIVO depende
        // de la fecha que esté puesta en ESTE campo -- el servidor no la conoce al dibujar la
        // pantalla. La frase entera la arma la JSP (su data-confirmar, con {fecha}); acá se
        // escribe SOLO el valor, en data-confirmar-valor-fecha, y lo reemplaza plantilla/pie.jsp:
        // este archivo no redacta ni una palabra (regla de B92/B98). Es el mismo corrimiento al
        // día hábil que ya informa el aviso gris de abajo; el real, el que queda en el plan, lo
        // sigue haciendo el servidor (ServicioMotorFinanciero.fechaHabil).
        function escribirFechaDelCartel(elegida) {
            var formulario = campo.form;
            if (!formulario || !formulario.hasAttribute('data-confirmar')) {
                return;
            }
            // Campo vacío: el "required" ya impide el envío, así que este cartel no se abre
            // (pie.jsp llama a checkValidity antes de mostrarlo). Va el guion de "sin dato" del
            // sistema igual, para no dejar nunca el {fecha} crudo a la vista.
            var corregida = elegida ? siguienteDiaHabil(elegida, feriados) : null;
            formulario.setAttribute('data-confirmar-valor-fecha', corregida
                    ? NOMBRES_DIA[corregida.getUTCDay()] + ' ' + comoDdMmAaaa(corregida)
                    : '—');
        }

        function actualizar() {
            if (!campo.value) {
                aviso.textContent = '';
                escribirFechaDelCartel(null);
                return;
            }
            var partes = campo.value.split('-');
            var elegida = new Date(Date.UTC(+partes[0], +partes[1] - 1, +partes[2]));
            escribirFechaDelCartel(elegida);
            var motivo = motivoNoHabil(elegida, feriados);
            if (!motivo) {
                aviso.textContent = '';
                return;
            }
            var corregida = siguienteDiaHabil(elegida, feriados);
            var texto = 'El ' + comoDdMmAaaa(elegida) + ' es ' + motivo + '. El primer vencimiento '
                + 'quedará el ' + NOMBRES_DIA[corregida.getUTCDay()] + ' ' + comoDdMmAaaa(corregida) + '.';
            // A.6 (consolidación, pedido de Joel): si el corrimiento al día hábil siguiente empuja
            // la fecha FUERA del rango permitido (campo.max, ya puesto por el servidor), avisar acá
            // también -- antes solo se avisaba el corrimiento, nunca que terminaba fuera de plazo
            // (el usuario recién se enteraba al enviar el formulario y recibir el error del
            // servidor). Solo informa; el rechazo real sigue siendo del servidor.
            var textoFueraDePlazo = '';
            if (campo.max) {
                var partesMax = campo.max.split('-');
                var maxima = new Date(Date.UTC(+partesMax[0], +partesMax[1] - 1, +partesMax[2]));
                if (corregida.getTime() > maxima.getTime()) {
                    textoFueraDePlazo = ' Esa fecha queda fuera del plazo permitido (hasta ' + comoDdMmAaaa(maxima)
                        + '); elija una fecha anterior.';
                }
            }
            // D.2 (reprueba Lote 1, pedido de Joel): el aviso de corrimiento es solo informativo
            // (se queda en el gris de "ayuda", texto-muted del contenedor) -- "fuera de plazo" es
            // una advertencia real y necesita resaltarse (ámbar oscuro, mismo color que ya se usa
            // en caja/ficha.jsp para "sobra"), no perderse con el mismo gris. Los textos vienen de
            // datos internos (feriados cargados por un administrador), nunca de lo que el usuario
            // escribe en este campo -- no hay entrada de terceros en este innerHTML.
            // Corrección (reprueba del Lote 1, 2.f): seguía viéndose gris en nueva.jsp -- el
            // contenedor (aviso-dia-inhabil) tiene la clase "text-muted" puesta en el JSP; para
            // no depender de que el estilo en línea del <strong> le gane por herencia a un color
            // puesto con !important más arriba en la cascada, se le quita esa clase al propio
            // contenedor mientras se muestra el aviso de fuera de plazo (se la vuelve a poner si
            // la fecha cambia y deja de estar fuera de plazo).
            if (textoFueraDePlazo) {
                aviso.classList.remove('text-muted');
                aviso.innerHTML = escaparHtml(texto) + '<br>'
                    + '<strong style="color:#a15c00">' + escaparHtml(textoFueraDePlazo.trim()) + '</strong>';
            } else {
                aviso.classList.add('text-muted');
                aviso.textContent = texto;
            }
        }

        campo.addEventListener('change', actualizar);
        actualizar();
    }

    document.addEventListener('DOMContentLoaded', function () {
        var campos = document.querySelectorAll('.campo-primer-vencimiento');
        for (var i = 0; i < campos.length; i++) {
            inicializarCampo(campos[i]);
        }
    });
})();
