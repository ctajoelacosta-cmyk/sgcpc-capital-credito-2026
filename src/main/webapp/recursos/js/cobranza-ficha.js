/**
 * D-145 (pantalla única de cobranza, pedido de Joel): toda la interactividad de la ficha de
 * cobranza -- elegir "Pagar cuota"/"Adelantar a capital" o "Cancelar todo", calcular el vuelto en
 * vivo (mismo criterio de redondeo de D-141, solo para mostrarlo antes de confirmar) y el modal
 * de confirmación de 3 líneas. El vuelto siempre se devuelve (sin combinarlo con ningún adelanto).
 *
 * Importante (REGLAS_DESARROLLO.md §5): este archivo NO calcula nada que se guarde. El servidor (confirmarCobro/
 * confirmarCancelacionTotal/confirmarAnticipoCapital, sin cambios) es quien decide de verdad el
 * vuelto y el redondeo reales al procesar el formulario correspondiente -- acá solo se anticipa
 * esa misma cuenta para que la cajera la vea antes de tocar "Cobrar".
 *
 * No hace nada si la ficha no tiene la tarjeta unificada (#tarjetaCobranzaUnificada) -- mismo
 * criterio que campo-guaranies.js/listado-vivo.js: se carga en todas las páginas desde
 * plantilla/pie.jsp, pero solo actúa donde corresponde.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var tarjeta = document.getElementById('tarjetaCobranzaUnificada');
        if (!tarjeta) {
            return;
        }

        var redondeo = parseInt(tarjeta.getAttribute('data-redondeo'), 10) || 0;
        var montoPagarCuota = parseInt(tarjeta.getAttribute('data-monto-pagar-cuota'), 10) || 0;
        var pagarCuotaVia = tarjeta.getAttribute('data-pagar-cuota-via'); // "cobrar" | "anticipar"
        var montoCancelarTodo = parseInt(tarjeta.getAttribute('data-monto-cancelar-todo'), 10) || 0;
        // 12/10/2026 (pedido de Joel, ajustes chicos del Bloque Cancelación): precargar
        // "Recibido" con el monto EN EFECTIVO (múltiplo de "redondeo", D-141) en vez del exacto --
        // el exacto, cuando no es múltiplo, disparaba el aviso rojo "El efectivo va en múltiplos
        // de X" apenas se elegía el botón, sin que la cajera tocara nada. Mismo valor que ya se
        // muestra como "en efectivo: ..." debajo del botón (montoPagarCuotaRedondeadoFicha/
        // montoCancelarRedondeadoFicha); si no viene (sin redondeo configurado), cae en el exacto.
        var montoPagarCuotaRedondeado = parseInt(tarjeta.getAttribute('data-monto-pagar-cuota-redondeado'), 10)
                || montoPagarCuota;
        var montoCancelarTodoRedondeado = parseInt(tarjeta.getAttribute('data-monto-cancelar-todo-redondeado'), 10)
                || montoCancelarTodo;
        // D-145 (P-5, pedido de Joel): mínimo para "Adelantar a capital" -- interés devengado +
        // IVA del período, el mismo que ya exige confirmarAnticipoCapital (sin tocar el
        // servicio). No depende del monto, solo de la fecha, así que es fijo mientras la cajera
        // escribe distintos montos en "Recibido".
        var montoAnticipoMinimo = parseInt(tarjeta.getAttribute('data-monto-anticipo-minimo'), 10) || 0;
        // D-145 (P-6, pedido de Joel): "lo exigible" -- el mismo total que usa confirmarCobro
        // (sin cambios) para decidir si un cobro es parcial (RN-20); no es lo mismo que el monto
        // sugerido del botón, que puede incluir además la cuota que vence justo hoy (D-144).
        var montoExigible = parseInt(tarjeta.getAttribute('data-monto-exigible'), 10) || 0;
        // Corrección de diseño del 27/10/2026 (opción B, tras la prueba en pantalla de Joel): la
        // cajera elige cuántas cuotas paga, de 1 hasta las que le quedan (K). El monto de esa
        // cantidad lo busca el SERVIDOR (el mínimo que deja exactamente K − N cuotas) y llega por
        // AJAX -- el JS nunca arma un importe (REGLAS_DESARROLLO.md §5), y ya no existe el
        // "N × la cuota" de D-145 (P-7), que dejaba menos cuotas de las que la cajera decía.
        // Del monto de N = 1 se conserva el dato porque es el que el servidor ya usó para la
        // vista previa de esta página, y hay que poder volver a él sin ir y venir.
        var cuotasQueQuedan = parseInt(tarjeta.getAttribute('data-cuotas-que-quedan'), 10) || 0;
        var montoCuotasInicial = parseInt(tarjeta.getAttribute('data-monto-cuotas-inicial'), 10) || 0;
        // C30 (decisión de Joel, 03/11/2026): aviso --no bloqueo-- cuando hay una reversión
        // esperando autorización. El texto ya viene redactado por ServicioReversiones (el JS no
        // escribe ni una palabra, igual que B79/B92: la plantilla viaja en un data-), y se agrega
        // al final del cartel de los TRES caminos (cobrar, adelantar y cancelar todo), porque los
        // tres dejan la reversión imposible de autorizar.
        var avisoReversionPendiente = tarjeta.getAttribute('data-aviso-reversion-pendiente') || '';

        var botonPagarCuota = document.getElementById('botonPagarCuota');
        var botonOpcionMonto = document.getElementById('botonOpcionMonto');
        var botonCancelarTodo = document.getElementById('botonCancelarTodo');
        var campoRecibido = document.getElementById('campoRecibidoUnificado');
        var textoVuelto = document.getElementById('textoVueltoUnificado');
        var detalleAnticipo = document.getElementById('detalleAnticipo');
        // B203 (15/11/2026): el desglose de la cancelacion total, hermano del de arriba. Se
        // muestra solo con la opcion "Todo el prestamo" elegida -- el contenido ya viene
        // armado por el servidor (ServicioCobranza.desgloseCancelacionTotal), aca solo se
        // decide cuando se ve: este archivo no redacta ni calcula nada (regla de B92).
        var detalleCancelacion = document.getElementById('detalleCancelacion');
        var textoProximaCuotaAnticipo = document.getElementById('textoProximaCuotaAnticipo');
        var contenidoDetalleAnticipo = document.getElementById('contenidoDetalleAnticipo');
        var grupoFechaPromesa = document.getElementById('grupoFechaPromesaUnificado');
        var campoFechaPromesa = document.getElementById('campoFechaPromesaUnificado');
        var avisoFechaPromesaInvalida = document.getElementById('avisoFechaPromesaInvalida');
        var avisoRecibidoNoMultiplo = document.getElementById('avisoRecibidoNoMultiplo');
        var botonCobrar = document.getElementById('botonCobrarUnificado');
        var grupoCuotasAnticipo = document.getElementById('grupoCuotasAnticipo');
        var campoCantidadCuotas = document.getElementById('campoCantidadCuotas');
        var botonMenosCuotas = document.getElementById('botonMenosCuotas');
        var botonMasCuotas = document.getElementById('botonMasCuotas');
        var avisoAnticipoBloqueado = document.getElementById('avisoAnticipoBloqueado');
        var bloqueVistaPreviaAnticipo = document.getElementById('bloqueVistaPreviaAnticipo');
        // Mejora de pantalla del 27/10/2026 (diseño aprobado por Joel): la letra chica del
        // adelanto ("El monto va en múltiplos de 50 Gs.; ...") pasó al pie de la tarjeta. Como
        // sigue siendo información del ADELANTO, se oculta igual que el resto cuando la opción
        // elegida no lo es (misma razón que B74).
        var notaAnticipoPie = document.getElementById('notaAnticipoPie');
        var textoAhorroAnticipo = document.getElementById('textoAhorroAnticipo');
        var textoMontoPagarCuotaExacto = document.getElementById('textoMontoPagarCuotaExacto');
        var contenedorMontoPagarCuotaRedondeado = document.getElementById('contenedorMontoPagarCuotaRedondeado');
        var textoMontoPagarCuotaRedondeado = document.getElementById('textoMontoPagarCuotaRedondeado');
        // B81 (30/10/2026): el renglón chico del pago parcial ("de X exigibles — queda pendiente
        // Y"). Lo arma el servidor y viaja como plantilla en su data-; solo se ve en pago parcial.
        var contenedorDetalleCobroParcial = document.getElementById('contenedorDetalleCobroParcial');
        var textoDetalleCobroParcial = document.getElementById('textoDetalleCobroParcial');
        var campoMontoSugeridoFormAnticipar = document.getElementById('campoMontoSugeridoFormAnticipar');

        var cuotasElegidas = 1; // D-145 (P-7): 1 por defecto -- el comportamiento de siempre.
        // Cuál de las opciones de "¿Cuánto quiere pagar el cliente?" está elegida: 'cuotas'
        // (por cantidad de cuotas, o "Pagar lo vencido" cuando hay vencidas), 'monto' (lo que
        // trae el cliente) o 'todo' (todo el préstamo). Es distinto de accionElegida, que es el
        // flujo del servidor que se va a usar: con N = K la opción sigue siendo 'cuotas' (el
        // control de cantidad tiene que quedar a la vista para poder bajar de nuevo) pero la
        // acción pasa a 'cancelar', porque pagar todas las cuotas que quedan ES la cancelación
        // total (RF26/RF28, decisión de Joel del 27/10/2026).
        var opcionElegida = 'cuotas';
        // Motivo que devolvió el servidor por el que la cantidad/el monto elegidos no se pueden
        // adelantar (mínimo de interés del período, D-88, D-90). Mientras haya uno, "Cobrar"
        // queda en gris: el servidor es el único que decide si ese monto entra.
        var mensajeBloqueoAnticipo = null;
        // Hay una vista previa pedida y todavía sin respuesta. "Cobrar" no se habilita hasta que
        // llegue (condición de Joel, 27/10/2026): la cajera nunca confirma un monto que todavía
        // no vio en pantalla.
        var previewAnticipoPendiente = false;
        // Corrección de Joel (27/10/2026, segunda prueba en pantalla): se está esperando que el
        // SERVIDOR diga cuánto cuesta la cantidad de cuotas elegida. Mientras eso no llegue no se
        // pide ninguna otra vista previa (antes el recálculo disparaba un pedido por monto que
        // CANCELABA al de la cantidad, y la tarjeta quedaba con el monto de la cantidad anterior),
        // no se muestra ningún monto viejo y "Cobrar" queda en gris.
        var esperandoMontoDeCuotas = false;
        // Primera pasada de la carga de la ficha: la vista previa de 1 cuota ya viene renderizada
        // por el servidor, así que no hace falta pedirla. De ahí en adelante, cada vez que se
        // vuelve a "Por cantidad de cuotas" se le pide al servidor el monto de la cantidad que
        // esté elegida -- nunca se reusa un monto ni un texto de otra cantidad.
        var cargaInicial = true;
        // Al CAMBIAR de opción, "Recibido" se rellena con el monto de la opción nueva (corrección
        // de Joel, 27/10/2026): arrastrar el monto de la opción anterior es justo lo que dejaba la
        // pantalla contradictoria. Solo aplica a la familia del adelanto/cancelación (sin nada
        // vencido); con cuotas vencidas sigue vigente la regla del 13/10/2026 de no pisar lo que
        // la cajera escribió a mano. Dentro de una misma opción tampoco se pisa nunca.
        var forzarRecibidoAlCambiarOpcion = pagarCuotaVia === 'anticipar';
        // Número de pedido de vista previa: solo la respuesta del último pedido pinta la
        // pantalla (los pedidos por cantidad de cuotas no conocen el monto de antemano, así que
        // no se puede distinguir "vieja" comparando montos como antes).
        var secuenciaPreview = 0;

        var accionElegida = null; // "cobrar" | "cancelar" | "anticipar"
        var montoElegido = 0;
        // 13/10/2026 (pedido de Joel): el último monto que ESTE script escribió en "Recibido" --
        // distingue "la cajera no tocó nada" de "la cajera ya escribió un monto a mano", para
        // decidir si corresponde actualizar el campo al cambiar de acción (ver seleccionar()).
        var ultimoMontoAutocompletado = null;
        // D-145 (P-5): último monto de adelanto para el que ya se pidió/mostró la vista previa en
        // vivo -- arranca en "montoPagarCuota" porque, cuando el modo es "anticipar", es
        // exactamente el monto con el que el servidor ya calculó la vista previa que viene
        // renderizada en la página (ver ControladorCobranzaServlet.mostrarFicha); evita un pedido
        // AJAX redundante apenas se carga la ficha.
        var montoAnticipoAplicadoVigente = montoPagarCuota;
        var temporizadorPreviewAnticipo = null;
        var RETRASO_MS_PREVIEW = 300; // mismo criterio de campo-guaranies.js
        // A3c (pedido de Joel): el text-warning de Bootstrap (amarillo claro) casi no se lee
        // sobre fondo blanco -- ámbar oscuro a mano, vía estilo en línea (no hay hoja de
        // estilos propia en el proyecto, REGLAS_DESARROLLO.md §15, así que no hay dónde definir una clase).
        var AMBAR_OSCURO = '#a15c00';

        function formatearGs(numero) {
            var negativo = numero < 0;
            var texto = Math.abs(Math.round(numero)).toString();
            var conPuntos = '';
            for (var i = 0; i < texto.length; i++) {
                if (i > 0 && (texto.length - i) % 3 === 0) {
                    conPuntos += '.';
                }
                conPuntos += texto.charAt(i);
            }
            return (negativo ? '-' : '') + conPuntos + ' Gs.';
        }

        function leerRecibido() {
            var digitos = (campoRecibido.value || '').replace(/\D/g, '');
            return digitos ? parseInt(digitos, 10) : 0;
        }

        // Corrección de Joel (12/10/2026, RF27): el detalle del anticipo (contenidoDetalleAnticipo/
        // textoProximaCuotaAnticipo, cobranza/ficha.jsp, consolidado -- no se toca) es texto plano
        // con saltos de línea en el HTML, que el navegador colapsa en un solo párrafo. Se separa en
        // un <div> por renglón usando esos mismos saltos de línea (textContent los conserva aunque
        // no se vean al renderizar), sin recalcular ni reformatear ningún número. Un renglón que
        // empieza con "+" (la continuación de "Interés devengado hoy: X" con "+ IVA Y") se junta al
        // renglón anterior -- es un solo concepto. Sin HTML/cálculo nuevo: solo separa lo que ya
        // existe en líneas.
        // C30: para meter un texto del servidor dentro del HTML del cartel sin confiar en que no
        // traiga marcado. No reemplaza a nada: el resto del cartel sigue armándose igual.
        function escaparHtml(texto) {
            return String(texto)
                    .replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;')
                    .replace(/"/g, '&quot;');
        }

        function lineasDesdeTexto(elemento) {
            if (!elemento) {
                return '';
            }
            var renglones = [];
            elemento.textContent.split('\n').forEach(function (renglon) {
                var limpio = renglon.trim();
                if (!limpio) {
                    return;
                }
                if (limpio.charAt(0) === '+' && renglones.length > 0) {
                    renglones[renglones.length - 1] += ' ' + limpio;
                } else {
                    renglones.push(limpio);
                }
            });
            return renglones.map(function (renglon) {
                return '<div>' + renglon + '</div>';
            }).join('');
        }

        // Corrección de Joel (12/10/2026, RF27): los 2 últimos renglones del cartel van con el
        // mismo formato "dato: valor" que el resto ("Próxima cuota: FECHA por MONTO" / "Cuotas
        // que quedan: N"), no la oración de "textoProximaCuotaAnticipo" ("Si adelanta, su próxima
        // cuota será el FECHA por MONTO, queda/quedan N cuota(s)."). Los 3 valores reales (fecha,
        // monto, cantidad) se leen de los <strong> de ese mismo elemento -- cobranza/ficha.jsp
        // (consolidado, no se toca) los envuelve en ese orden -- sin volver a calcular nada.
        function renglonesProximaCuota(elemento) {
            if (!elemento) {
                return '';
            }
            var valores = elemento.querySelectorAll('strong');
            if (valores.length < 3) {
                // Red de seguridad si la estructura cambiara: muestra el texto tal cual, sin cortar.
                return '<div>' + elemento.textContent.replace(/\s+/g, ' ').trim() + '</div>';
            }
            var fecha = valores[0].textContent.trim();
            var monto = valores[1].textContent.trim();
            var cantidad = valores[2].textContent.trim().split(/\s+/)[0];
            return '<div>Próxima cuota: ' + fecha + ' por ' + monto + '</div>'
                    + '<div>Cuotas que quedan: ' + cantidad + '</div>';
        }

        // Mismo criterio de D-141 (piso al múltiplo del parámetro credito.redondeo_efectivo),
        // solo para la VISTA PREVIA -- el servidor vuelve a calcularlo de verdad al confirmar.
        // Solo se usa para "cobrar"/"cancelar" -- "anticipar" tiene su propia regla (D-145 P-5).
        function pisoRedondeo(monto) {
            if (redondeo <= 0) {
                return monto;
            }
            return monto - (monto % redondeo);
        }

        // D-145 (pedido de Joel, P-1): si falta efectivo, el campo "Vuelto" no muestra "0 Gs." --
        // muestra en rojo cuánto falta, con la misma cifra que usa el aviso de "Falta efectivo".
        function calcularFalta(recibido, minimo) {
            return Math.max(0, minimo - recibido);
        }

        // D-146 (P-15, pedido de Joel): el efectivo físico solo viene en múltiplos del parámetro
        // credito.redondeo_efectivo -- aplica a cualquier modo (cobrar/cancelar/anticipar), es
        // sobre lo que la cajera tiene en la mano, no sobre lo que se aplica a la deuda.
        function recibidoEsMultiploValido(recibido) {
            return redondeo <= 0 || recibido % redondeo === 0;
        }

        // D-145 (P-6, pedido de Joel): fecha de hoy/mañana en formato ISO (yyyy-mm-dd), para
        // comparar con el <input type="date"> (que ya trabaja en ese formato) y para el atributo
        // "min" del campo -- la validación real ("posterior a hoy") la vuelve a hacer
        // confirmarCobro en el servidor, esto es solo para no dejar cobrar con una fecha inválida
        // a simple vista.
        function formatearFechaIso(fecha) {
            var mes = ('0' + (fecha.getMonth() + 1)).slice(-2);
            var dia = ('0' + fecha.getDate()).slice(-2);
            return fecha.getFullYear() + '-' + mes + '-' + dia;
        }

        // B95 (30/10/2026, decision de Joel): "todavia no eligio" y "eligio una fecha que no
        // sirve" dejan de ser lo mismo. Antes un solo booleano habilitaba "Cobrar" y ademas
        // decidia el aviso, asi que con el campo vacio salia en rojo un texto que habla de una
        // fecha mal elegida -- un error de algo que la cajera no hizo.
        function hayFechaPromesa() {
            return !!(campoFechaPromesa && campoFechaPromesa.value);
        }

        function fechaPromesaValida() {
            var valor = campoFechaPromesa ? campoFechaPromesa.value : '';
            return !!valor && valor > formatearFechaIso(new Date());
        }

        function formatearFechaDdMmAaaa(iso) {
            var partes = iso.split('-');
            return partes[2] + '/' + partes[1] + '/' + partes[0];
        }

        function mostrarFechaPromesa() {
            if (!grupoFechaPromesa) {
                return;
            }
            grupoFechaPromesa.hidden = false;
            if (campoFechaPromesa && !campoFechaPromesa.min) {
                var fechaManana = new Date();
                fechaManana.setDate(fechaManana.getDate() + 1);
                campoFechaPromesa.min = formatearFechaIso(fechaManana);
            }
        }

        function ocultarFechaPromesa() {
            if (grupoFechaPromesa) {
                grupoFechaPromesa.hidden = true;
            }
            if (avisoFechaPromesaInvalida) {
                avisoFechaPromesaInvalida.hidden = true;
            }
        }

        // Mismo criterio que campo-guaranies.js (D-136): una sesión vencida nunca debe mostrar el
        // HTML crudo del login como si fuera una vista previa.
        function avisarSesionVencida() {
            if (typeof window.avisarSesionVencida === 'function') {
                window.avisarSesionVencida();
                return;
            }
            window.location.href = (window.CONTEXTO_APP || '') + '/acceso';
        }

        // D-145 (P-5, pedido de Joel): en "por cantidad de cuotas" el cliente puede adelantar
        // MENOS que lo sugerido -- lo que de verdad se aplica es el menor entre lo recibido y lo
        // sugerido (confirmado otra vez en el servlet, no solo acá).
        // Ampliación del 27/10/2026: en "por monto" se aplica TODO lo recibido, porque el monto
        // ES lo que trae el cliente -- no hay una cuota sugerida que lo recorte.
        function montoAplicadoAnticipo(recibido) {
            return opcionElegida === 'monto' ? recibido : Math.min(recibido, montoElegido);
        }

        // Entero en guaraníes que el servidor dejó en un <div> del fragmento (sin formato, para
        // no tener que interpretar "192.250 Gs." acá).
        function enteroDelFragmento(contenedor, selector) {
            var elemento = contenedor.querySelector(selector);
            if (!elemento) {
                return 0;
            }
            return parseInt((elemento.textContent || '').replace(/\D/g, ''), 10) || 0;
        }

        // Corrección de diseño del 27/10/2026 (aprobada por Joel): el botón grande de "Por
        // cantidad de cuotas" muestra UN solo monto, con la cantidad adelante. Ya no va el par
        // "exacto / en efectivo" de D-145 (P-2): acá el monto no es un precio con descuento sino
        // el mínimo que logra sacar esas cuotas del plan, y el servidor ya lo devuelve en
        // múltiplos de 50. El monto lo manda el servidor; acá solo se formatea.
        //
        // B92 (29/10/2026, decisión de Joel): el TEXTO también lo manda el servidor. "Paga N
        // cuotas" invitaba a comparar ese monto con la cuota del plan, que es otra cosa; ahora
        // dice "Adelanta N cuotas (sin intereses futuros)". La frase la arma
        // ServicioCobranza.etiquetaAdelantoCuotas y viaja en los data- del propio elemento (ver
        // cobranza/ficha.jsp): acá solo se reemplaza {n}. Un solo dueño para los tres lugares que
        // la muestran -- antes estaba escrita tres veces, una en la JSP y dos acá.
        function fraseAdelantoCuotas(cuotas) {
            var plantilla = textoMontoPagarCuotaExacto
                    ? textoMontoPagarCuotaExacto.getAttribute(cuotas === 1
                            ? 'data-adelanto-singular' : 'data-adelanto-plural')
                    : null;
            if (!plantilla) {
                // Sin el elemento (o sin los data-, en una pantalla vieja en caché) no se inventa
                // una redacción distinta: se muestra solo la cantidad, sin frase.
                return cuotas + (cuotas === 1 ? ' cuota' : ' cuotas');
            }
            return plantilla.replace('{n}', cuotas);
        }

        function etiquetaCuotas(cuotas) {
            return fraseAdelantoCuotas(cuotas) + ': ';
        }

        // B79/B81 (30/10/2026, decisión de Joel): UN SOLO PINTOR de la línea grande del "Resumen
        // del cobro". Antes la escribían cinco lugares sueltos (el primer render de la JSP y cuatro
        // puntos de este archivo) y ninguno sabía si ese cobro se podía hacer de verdad: con el
        // monto rechazado por el servidor la línea seguía afirmando "Adelantar a capital: 3.500.000
        // Gs." mientras el renglón rojo decía que no se podía cobrar y "Cobrar" estaba en gris
        // (B79), y en un pago parcial seguía mostrando lo exigible (180.696) aunque se estuvieran
        // cobrando 100.000 (B81). Ahora los cuatro dejan acá QUÉ querían decir y pintarResumen
        // decide, con dos reglas de Joel: si hay un motivo en rojo, la línea no afirma ningún monto;
        // si el cobro es parcial, el monto es el que se va a cobrar. Las frases las arma el servidor
        // (ServicioCobranza, en los data- de cobranza/ficha.jsp): acá solo se reemplazan números.
        // En null mientras nadie haya escrito nada: entonces manda el PRIMER RENDER del servidor,
        // tal cual lo dejó la JSP (incluido su "en efectivo" de D-141). Así el pintor nunca borra
        // lo que decidió el servidor al abrir la ficha, que es el estado en el que la cajera
        // encuentra la pantalla.
        var resumenPedido = null;
        var primerRenderTexto = textoMontoPagarCuotaExacto ? textoMontoPagarCuotaExacto.textContent : '';
        var primerRenderConEnEfectivo = contenedorMontoPagarCuotaRedondeado
                && !contenedorMontoPagarCuotaRedondeado.hidden;
        // Mientras el cobro es parcial: {recibido, exigible, pendiente}. Lo calcula recalcularCobrar
        // (los mismos números que ya mostraba en "Vuelto"), no este pintor.
        var cobroParcialVigente = null;
        // Hay un motivo en rojo a la vista y "Cobrar" está en gris.
        var bloqueoVigente = false;

        function plantillaDelResumen(nombreData, respaldo) {
            var plantilla = textoMontoPagarCuotaExacto
                    ? textoMontoPagarCuotaExacto.getAttribute(nombreData) : null;
            // Sin el data- (pantalla vieja en caché) no se inventa otra redacción: se usa el
            // respaldo mínimo, que es la misma forma sin el texto del servidor.
            return plantilla ? plantilla : respaldo;
        }

        // B98 (30/10/2026): en que situacion esta el cobro de hoy, decidido por el servidor
        // (ServicioCobranza.situacionCobroHoy). "true" = no hay nada vencido pero hay una cuota
        // que vence HOY: ahi ninguna palabra puede decir "vencido".
        var soloCuotaDeHoy = textoMontoPagarCuotaExacto
                && textoMontoPagarCuotaExacto.getAttribute('data-solo-cuota-de-hoy') === 'true';
        // Mientras se cobra MENOS que la cuota de hoy: {recibido, total, pendiente}. Lo marca
        // recalcularCobrar, igual que cobroParcialVigente en B81 -- sin esto la linea grande se
        // quedaba en el monto de la cuota mientras el boton decia otro (viola B79).
        var cobroACuentaVigente = null;

        // B98: las dos frases de la cuota de hoy, armadas en UN lugar con las plantillas del
        // servidor, para la tarjeta y para el cartel.
        function fraseCobroACuenta(monto) {
            return plantillaDelResumen('data-a-cuenta', 'Cobra {monto} (a cuenta de la cuota de hoy)')
                    .replace('{monto}', formatearGs(monto));
        }

        function fraseDetalleACuenta(totalCuota, pendiente) {
            return plantillaDelResumen('data-a-cuenta-detalle',
                            'de {total} \u2014 quedan {pendiente}, sin mora hasta ma\u00f1ana')
                    .replace('{total}', formatearGs(totalCuota))
                    .replace('{pendiente}', formatearGs(pendiente));
        }

        // B81/B96: las dos frases del pago parcial se arman en UN lugar, con las plantillas que
        // dejo el servidor, y las usan la tarjeta y el cartel de confirmacion. El JS solo
        // reemplaza numeros.
        function fraseCobroParcial(monto) {
            return plantillaDelResumen('data-parcial', 'Cobra {monto}')
                    .replace('{monto}', formatearGs(monto));
        }

        function fraseDetalleParcial(exigible, pendiente) {
            var plantilla = textoDetalleCobroParcial
                    ? textoDetalleCobroParcial.getAttribute('data-plantilla') : null;
            return (plantilla || 'de {exigible} — queda pendiente {pendiente}')
                    .replace('{exigible}', formatearGs(exigible))
                    .replace('{pendiente}', formatearGs(pendiente));
        }

        function pintarResumen() {
            if (!textoMontoPagarCuotaExacto) {
                return;
            }
            var hayEnEfectivo = false;
            var hayParcial = false;
            var hayACuenta = false;
            if (bloqueoVigente) {
                textoMontoPagarCuotaExacto.textContent = plantillaDelResumen('data-sin-monto', '—');
            } else if (cobroParcialVigente) {
                textoMontoPagarCuotaExacto.textContent = fraseCobroParcial(cobroParcialVigente.recibido);
                hayParcial = true;
            } else if (cobroACuentaVigente) {
                // B98: se cobra menos que la cuota de hoy -- el numero grande es el que se va a
                // cobrar (B79) y el renglon chico dice de cuanto era la cuota y que no hay mora.
                textoMontoPagarCuotaExacto.textContent = fraseCobroACuenta(cobroACuentaVigente.recibido);
                hayACuenta = true;
            } else if (!resumenPedido) {
                // Todavía manda el primer render del servidor (la ficha recién abierta).
                textoMontoPagarCuotaExacto.textContent = primerRenderTexto;
                hayEnEfectivo = primerRenderConEnEfectivo;
            } else if (resumenPedido.textoCrudo) {
                textoMontoPagarCuotaExacto.textContent = resumenPedido.textoCrudo;
            } else {
                textoMontoPagarCuotaExacto.textContent = resumenPedido.monto > 0
                        ? resumenPedido.etiqueta + ': ' + formatearGs(resumenPedido.monto)
                                + (resumenPedido.sufijo ? ' ' + resumenPedido.sufijo : '')
                        : resumenPedido.etiqueta;
                // El "en efectivo" de D-141: solo cuando el redondeo cambia de verdad el monto.
                hayEnEfectivo = resumenPedido.monto > 0 && resumenPedido.enEfectivo > 0
                        && resumenPedido.enEfectivo !== resumenPedido.monto;
            }
            if (contenedorMontoPagarCuotaRedondeado) {
                contenedorMontoPagarCuotaRedondeado.hidden = !hayEnEfectivo;
                // El texto del "en efectivo" solo se reescribe si hay un monto nuevo: el del primer
                // render ya lo puso la JSP con el mismo valor.
                if (hayEnEfectivo && resumenPedido && textoMontoPagarCuotaRedondeado) {
                    textoMontoPagarCuotaRedondeado.textContent = 'en efectivo: '
                            + formatearGs(resumenPedido.enEfectivo);
                }
            }
            if (contenedorDetalleCobroParcial) {
                contenedorDetalleCobroParcial.hidden = !(hayParcial || hayACuenta);
                if (hayACuenta && textoDetalleCobroParcial) {
                    // B98: el mismo renglon chico, con la frase de la cuota de hoy.
                    textoDetalleCobroParcial.textContent = fraseDetalleACuenta(
                            cobroACuentaVigente.total, cobroACuentaVigente.pendiente);
                    textoDetalleCobroParcial.style.color = AMBAR_OSCURO;
                }
                if (hayParcial && textoDetalleCobroParcial) {
                    textoDetalleCobroParcial.textContent = fraseDetalleParcial(
                            cobroParcialVigente.exigible, cobroParcialVigente.pendiente);
                    // A3a (#7): ámbar, igual que el aviso de pago parcial que ya estaba en
                    // "Vuelto" -- distingue a simple vista un cobro parcial de uno completo.
                    textoDetalleCobroParcial.style.color = AMBAR_OSCURO;
                }
            }
        }

        // B79/B81: cambia el estado del bloqueo y vuelve a pintar. Lo llaman los dos lugares que
        // muestran un motivo en rojo (mostrarAvisoAnticipo y el aviso de múltiplos). Corrección de
        // Joel (30/10/2026): además de la línea grande, refresca qué piezas se ven -- con bloqueo
        // tampoco puede quedar a la vista la vista previa del monto anterior.
        function fijarBloqueoResumen(hayBloqueo) {
            bloqueoVigente = !!hayBloqueo;
            pintarResumen();
            mostrarPiezasSegunOpcion();
        }

        // B79/B81: entra/sale del pago parcial. Lo llama un solo lugar (recalcularCobrar).
        function fijarCobroParcial(datos) {
            cobroParcialVigente = datos;
            pintarResumen();
        }

        // B98: entra/sale del cobro a cuenta de la cuota de hoy. Un solo lugar (recalcularCobrar).
        function fijarCobroACuenta(datos) {
            cobroACuentaVigente = datos;
            pintarResumen();
        }

        function mostrarMontoEnOpcionCuotas(cuotas, monto, sufijo) {
            resumenPedido = {etiqueta: fraseAdelantoCuotas(cuotas), monto: monto, enEfectivo: 0,
                sufijo: sufijo, textoCrudo: null};
            pintarResumen();
        }

        // Corrección de Joel (27/10/2026, segunda prueba): mientras el servidor calcula el monto
        // de la cantidad elegida, la tarjeta NO puede seguir mostrando el monto de la cantidad
        // anterior -- era la mitad de la pantalla contradictoria que encontró.
        function mostrarCalculandoEnOpcionCuotas(cuotas) {
            resumenPedido = {etiqueta: '', monto: 0, enEfectivo: 0, sufijo: null,
                textoCrudo: etiquetaCuotas(cuotas) + 'calculando…'};
            pintarResumen();
        }

        // Mejora de pantalla del 27/10/2026 (diseño aprobado por Joel): el monto de lo que se va
        // a cobrar ya no vive adentro del botón de cada opción -- vive arriba del "Resumen del
        // cobro" (textoMontoPagarCuotaExacto, el mismo elemento de siempre, ahora en el ticket).
        // Por eso las dos opciones que antes mostraban su monto en su propio botón ("Todo el
        // préstamo") o no mostraban ninguno ("Por monto") tienen que escribirlo acá. NO se
        // calcula nada nuevo: se muestra el mismo monto que ya decidió el servidor y que el
        // script venía usando (montoCancelarTodo / lo que la cajera escribe en "Recibido").
        function mostrarEnResumen(etiqueta, monto, montoEnEfectivo) {
            resumenPedido = {etiqueta: etiqueta, monto: monto, enEfectivo: montoEnEfectivo,
                sufijo: null, textoCrudo: null};
            pintarResumen();
        }

        function mostrarAhorroAnticipo(texto) {
            if (!textoAhorroAnticipo) {
                return;
            }
            var hay = !!texto;
            textoAhorroAnticipo.hidden = !hay;
            textoAhorroAnticipo.textContent = hay ? texto : '';
        }

        // 13/10/2026 (pedido de Joel): "Recibido" se actualiza al monto de la opción elegida,
        // salvo que la cajera ya haya escrito un monto a mano, que nunca se pisa. "A mano" = lo
        // que hay en el campo no es ni "vacío" ni el último valor que ESTE script puso ahí.
        // "forzar" = se acaba de cambiar de opción, y el monto que haya quedado en el campo es el
        // de la opción ANTERIOR (corrección de Joel, 27/10/2026): ahí sí se pisa, porque cada
        // opción define su propio monto y mezclarlos es lo que rompía la coherencia de la
        // pantalla. Sin "forzar" (cambios dentro de la misma opción) se mantiene la regla del
        // 13/10/2026: lo que la cajera escribió a mano no se toca.
        function autocompletarRecibido(monto, forzar) {
            var actual = leerRecibido();
            if (forzar || actual === 0 || actual === ultimoMontoAutocompletado) {
                campoRecibido.value = monto.toString();
                ultimoMontoAutocompletado = monto;
                campoRecibido.dispatchEvent(new Event('input', {bubbles: true}));
            }
        }

        // Un solo lugar para el renglón de aviso del adelanto: en rojo cuando el servidor rechaza
        // el monto/la cantidad, y en gris cuando solo falta que la cajera escriba el monto
        // ("Por monto" con "Recibido" vacío -- corrección del 27/10/2026: ahí antes quedaba a la
        // vista una vista previa vieja, de otro monto).
        function mostrarAvisoAnticipo(texto, esError) {
            var hay = !!texto;
            if (!avisoAnticipoBloqueado) {
                // B79: aunque no esté el renglón de aviso, el estado del bloqueo se fija igual --
                // la línea grande nunca puede quedar afirmando un monto que no se va a cobrar.
                fijarBloqueoResumen(hay && !!esError);
                return;
            }
            avisoAnticipoBloqueado.hidden = !hay;
            avisoAnticipoBloqueado.textContent = hay ? texto : '';
            avisoAnticipoBloqueado.classList.toggle('text-danger', hay && !!esError);
            avisoAnticipoBloqueado.classList.toggle('text-muted', hay && !esError);
            // B79 (30/10/2026, decisión de Joel): este es el único lugar que sabe si hay un motivo
            // en rojo, así que es el que le dice a la línea grande que no afirme ningún monto. El
            // aviso en gris ("Escriba el monto que trae el cliente") NO es un bloqueo: ahí la
            // etiqueta sin monto sigue siendo cierta.
            fijarBloqueoResumen(hay && !!esError);
        }

        // "Por monto" sin monto escrito todavía: no hay nada que previsualizar.
        function faltaEscribirElMonto() {
            return opcionElegida === 'monto' && leerRecibido() <= 0;
        }

        // Corrección de Joel (27/10/2026, tercera prueba en pantalla): en "Por cantidad de cuotas"
        // el monto de la tarjeta ES lo que se va a cobrar. Si lo recibido no alcanza, NO se cobra
        // "un poco menos" en silencio: la tarjeta diría "Paga 3 cuotas" y el cobro real sería de
        // otro monto (le pasó con 500.000 contra 697.950, y la vista previa se iba a 9 cuotas).
        // Se avisa y "Cobrar" queda en gris; para un monto libre está la opción "Por monto".
        // Devuelve el texto del aviso, o null si no corresponde.
        function faltaParaLasCuotas() {
            if (accionElegida !== 'anticipar' || opcionElegida !== 'cuotas' || esperandoMontoDeCuotas) {
                return null;
            }
            var recibido = leerRecibido();
            if (recibido <= 0 || montoElegido <= 0 || recibido >= montoElegido) {
                return null;
            }
            // B115 (05/11/2026): la frase la arma ServicioCobranza y viaja en las dos plantillas
            // del data- (singular y plural); acá solo se reemplazan los números. Antes estaba
            // escrita a mano acá Y en el servidor, y las dos ya decían cosas distintas: esta
            // nombraba la cantidad ("para 3 cuotas") y la del servidor no ("para la cantidad de
            // cuotas elegida"). El respaldo es la forma mínima, nunca la frase (regla de B106).
            return plantillaDelResumen(cuotasElegidas === 1
                            ? 'data-falta-cuotas-singular' : 'data-falta-cuotas-plural',
                            'El efectivo recibido no alcanza para esa cantidad.')
                    .replace('{recibido}', formatearGs(recibido))
                    .replace('{n}', cuotasElegidas)
                    .replace('{monto}', formatearGs(montoElegido));
        }

        // Corrección de Joel (27/10/2026, prueba de la pantalla nueva): "Todo el préstamo" con lo
        // recibido por debajo del monto en efectivo se trata IGUAL que "Por cantidad de cuotas"
        // (faltaParaLasCuotas, acá arriba) -- aviso rojo en el resumen y "Cobrar" en gris, en vez
        // de dejar el botón habilitado y sacar el cartel "Falta efectivo" recién al tocarlo. No
        // cambia ninguna regla: con ese monto el cobro nunca salía (el manejador del clic ya
        // cortaba, y el servidor también); solo se avisa antes, donde la cajera lo lee.
        // Vale también para "Por cantidad de cuotas" con N = K, que es la misma cancelación total.
        // Devuelve el texto del aviso, o null si no corresponde.
        function faltaParaCancelarTodo() {
            if (accionElegida !== 'cancelar') {
                return null;
            }
            var recibido = leerRecibido();
            var minimo = pisoRedondeo(montoElegido);
            if (recibido <= 0 || minimo <= 0 || recibido >= minimo) {
                return null;
            }
            return 'Lo recibido (' + formatearGs(recibido) + ') no alcanza para cancelar todo el '
                    + 'préstamo (' + formatearGs(minimo) + ').';
        }

        // Corrección de Joel (30/10/2026, prueba de B79): el mínimo del adelanto (interés del
        // período + IVA, D-88/D-90 -- el mismo que exige confirmarAnticipoCapital) es un bloqueo
        // como los otros dos, así que su motivo vive en el MISMO renglón rojo y pasa por el mismo
        // dueño. Solo en "Por monto": en "Por cantidad de cuotas" el monto lo decide el servidor y
        // nunca queda por debajo del mínimo, y si lo recibido no alcanza ya lo explica
        // faltaParaLasCuotas con una frase más útil (antes salían los dos motivos a la vez).
        // Devuelve el texto del aviso, o null si no corresponde.
        function faltaElMinimoDelAdelanto() {
            if (accionElegida !== 'anticipar' || opcionElegida !== 'monto') {
                return null;
            }
            var recibido = leerRecibido();
            if (recibido <= 0 || montoAnticipoMinimo <= 0 || recibido >= montoAnticipoMinimo) {
                return null;
            }
            // D-145 (P-8, pedido de Joel): formatearGs ya termina en "Gs." -- agregar otro "."
            // duplica el punto. Lo encontró Joel en pantalla el 30/10/2026 ("...1.880 Gs..").
            return 'El mínimo para adelantar hoy es ' + formatearGs(montoAnticipoMinimo);
        }

        // B95: en un pago parcial, mientras no haya fecha elegida el pedido va en GRIS en este
        // mismo renglon (igual que "Escriba el monto que trae el cliente"), no en rojo debajo del
        // campo. La frase la arma el servidor y viaja en un data- del grupo del campo.
        // "cobroParcialVigente" lo marca recalcularCobrar, que es el unico que sabe si este cobro
        // es parcial; por eso vuelve a llamar aca despues de marcarlo.
        function faltaElegirLaFechaDePromesa() {
            if (!cobroParcialVigente || hayFechaPromesa()) {
                return null;
            }
            var motivo = grupoFechaPromesa ? grupoFechaPromesa.getAttribute('data-motivo-falta') : null;
            return motivo || null;
        }

        function refrescarAvisoAnticipo() {
            var faltaTodo = faltaParaCancelarTodo();
            if (faltaTodo) {
                mostrarAvisoAnticipo(faltaTodo, true);
                return;
            }
            var faltaFecha = faltaElegirLaFechaDePromesa();
            if (faltaFecha) {
                mostrarAvisoAnticipo(faltaFecha, false);
                return;
            }
            if (accionElegida !== 'anticipar') {
                mostrarAvisoAnticipo(null, false);
                return;
            }
            // Antes que el motivo del servidor: el mínimo es una certeza local (con ese monto no
            // se pide ninguna vista previa, así que un mensajeBloqueoAnticipo que hubiera quedado
            // de un monto anterior no corresponde a lo que está escrito ahora).
            var faltaMinimo = faltaElMinimoDelAdelanto();
            if (faltaMinimo) {
                mostrarAvisoAnticipo(faltaMinimo, true);
                return;
            }
            if (mensajeBloqueoAnticipo) {
                mostrarAvisoAnticipo(mensajeBloqueoAnticipo, true);
                return;
            }
            var falta = faltaParaLasCuotas();
            if (falta) {
                mostrarAvisoAnticipo(falta, true);
                return;
            }
            mostrarAvisoAnticipo(faltaEscribirElMonto() ? 'Escriba el monto que trae el cliente.' : null, false);
        }

        // Vista previa en vivo del adelanto (GET, sin grabar nada) -- calcularPreviewAnticipoParcial
        // ya existe y no se toca. Actualiza la línea "Si adelanta...", "Ver detalle" y, cuando el
        // pedido es por cantidad de cuotas, también el MONTO de esa cantidad (que lo decidió el
        // servidor, nunca este script). Con atraso (debounce) para no pedirlo en cada tecla --
        // ni de "Recibido" ni de la caja de cantidad de cuotas (condición de Joel, 27/10/2026).
        //
        // "parametro" es {cuotas: N} o {monto: X}: por cantidad de cuotas se manda N y el monto
        // vuelve calculado; por monto libre se manda el monto, como antes de la ampliación.
        function pedirVistaPreviaAnticipo(parametro) {
            if (temporizadorPreviewAnticipo) {
                clearTimeout(temporizadorPreviewAnticipo);
            }
            var miSecuencia = ++secuenciaPreview;
            var porCuotas = parametro.cuotas !== undefined;
            var consulta = porCuotas ? 'cuotas=' + encodeURIComponent(parametro.cuotas)
                    : 'monto=' + encodeURIComponent(parametro.monto);
            temporizadorPreviewAnticipo = setTimeout(function () {
                fetch(window.location.pathname + '/vista-previa-anticipo?' + consulta, {
                    headers: {'X-Requested-With': 'XMLHttpRequest'}
                }).then(function (respuesta) {
                    if (respuesta.status === 401) {
                        avisarSesionVencida();
                        return null;
                    }
                    if (typeof window.reiniciarTemporizadorSesion === 'function') {
                        window.reiniciarTemporizadorSesion();
                    }
                    return respuesta.ok ? respuesta.text() : null;
                }).then(function (html) {
                    // Si mientras esperaba la respuesta la cajera cambió otra vez la cantidad o
                    // el monto, esta respuesta ya quedó vieja -- la más nueva (ya en camino) es
                    // la que actualiza la pantalla.
                    if (miSecuencia !== secuenciaPreview) {
                        return;
                    }
                    previewAnticipoPendiente = false;
                    if (porCuotas) {
                        esperandoMontoDeCuotas = false;
                    }
                    if (!html) {
                        // Error de red o respuesta vacía: no se inventa nada en pantalla, pero
                        // tampoco se deja "Cobrar" trabado para siempre.
                        recalcular();
                        return;
                    }
                    var temporal = document.createElement('div');
                    temporal.innerHTML = html;
                    var bloqueo = temporal.querySelector('#fragMensajeBloqueo');
                    if (bloqueo) {
                        // Ampliación del 27/10/2026: antes el servlet respondía vacío y la cajera
                        // se quedaba sin saber por qué no podía cobrar. Ahora se muestra el
                        // motivo exacto que da el servicio.
                        mensajeBloqueoAnticipo = (bloqueo.textContent || '').trim();
                        recalcular();
                        return;
                    }
                    mensajeBloqueoAnticipo = null;
                    var nuevoTexto = temporal.querySelector('#fragTextoProximaCuota');
                    var nuevoDetalle = temporal.querySelector('#fragContenidoDetalle');
                    var nuevoAhorro = temporal.querySelector('#fragAhorro');
                    if (nuevoTexto && textoProximaCuotaAnticipo) {
                        textoProximaCuotaAnticipo.innerHTML = nuevoTexto.innerHTML;
                    }
                    if (nuevoDetalle && contenidoDetalleAnticipo) {
                        contenidoDetalleAnticipo.innerHTML = nuevoDetalle.innerHTML;
                    }
                    // El ahorro (RN-11) solo viene cuando el pedido fue por cantidad de cuotas:
                    // en "por monto" no hay una cantidad con la que comparar.
                    mostrarAhorroAnticipo(nuevoAhorro ? (nuevoAhorro.textContent || '').trim() : '');
                    if (porCuotas) {
                        var monto = enteroDelFragmento(temporal, '#fragMonto');
                        if (monto > 0) {
                            // Las cinco piezas de la tarjeta se actualizan JUNTAS, acá, con el
                            // monto que decidió el servidor para esta cantidad: tarjeta, monto
                            // elegido, monto sugerido del formulario, "Recibido" y (vía
                            // recalcular, más abajo) el vuelto. Corrección de Joel del
                            // 27/10/2026: antes esto podía no ejecutarse nunca y la tarjeta
                            // quedaba con la cantidad anterior.
                            montoElegido = monto;
                            montoAnticipoAplicadoVigente = monto;
                            mostrarMontoEnOpcionCuotas(parametro.cuotas, monto, null);
                            if (campoMontoSugeridoFormAnticipar) {
                                campoMontoSugeridoFormAnticipar.value = monto.toString();
                            }
                            autocompletarRecibido(monto, !!parametro.fijarRecibido);
                        }
                    }
                    recalcular();
                }).catch(function () {
                    if (miSecuencia === secuenciaPreview) {
                        previewAnticipoPendiente = false;
                        if (porCuotas) {
                            esperandoMontoDeCuotas = false;
                        }
                        recalcular();
                    }
                });
            }, RETRASO_MS_PREVIEW);
        }

        // D-145 (P-5, pedido de Joel): en "Adelantar a capital", si lo recibido no llega al
        // mínimo (interés + IVA del período) no se puede cobrar; si llega, se aplica todo lo
        // recibido hasta el monto sugerido (sin vuelto) y lo que sobre de ahí es vuelto, como
        // antes.
        function recalcularAnticipo(recibido) {
            // Corrección de Joel (27/10/2026, segunda prueba): mientras el servidor no diga cuánto
            // cuesta la cantidad de cuotas elegida, no se calcula vuelto con el monto anterior ni
            // se dispara otro pedido (ese pedido cancelaba al de la cantidad y dejaba la tarjeta
            // con el monto viejo). "Cobrar" queda en gris hasta que llegue.
            if (esperandoMontoDeCuotas) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            if (recibido <= 0) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            // Corrección de Joel (30/10/2026, prueba de B79): este motivo se escribía acá, en
            // "Vuelto", y era el ÚNICO bloqueo que no pasaba por el renglón rojo -- así que la
            // línea grande no se enteraba y seguía diciendo "Adelantar a capital: 1.000 Gs.".
            // Ahora lo arma faltaElMinimoDelAdelanto() y lo muestra refrescarAvisoAnticipo, como
            // los otros dos bloqueos; acá queda solo lo que corresponde a "Vuelto": "—", porque
            // con ese monto no hay ningún cobro del que calcular vuelto (mismo criterio que
            // faltaParaLasCuotas, 27/10/2026).
            if (faltaElMinimoDelAdelanto()) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            // Ampliación del 27/10/2026: en "Por monto" lo que se aplica al adelanto es TODO lo
            // recibido, así que el monto sugerido pasa a ser ese mismo valor -- de esa forma el
            // min(recibido, montoSugerido) de procesarAnticipar (sin cambios) no lo recorta.
            if (opcionElegida === 'monto') {
                montoElegido = recibido;
                if (campoMontoSugeridoFormAnticipar) {
                    campoMontoSugeridoFormAnticipar.value = recibido.toString();
                }
            }
            // Corrección de Joel (27/10/2026, tercera prueba): en "Por cantidad de cuotas", si lo
            // recibido no alcanza para esa cantidad se corta ACÁ, antes de pedir cualquier vista
            // previa -- así la tarjeta y la vista previa siguen siendo las de la cantidad elegida
            // (antes se pedía la previa del monto recibido y quedaba "le quedan 9 cuotas" debajo
            // de una tarjeta que decía "Paga 3 cuotas"). El motivo lo pone refrescarAvisoAnticipo.
            if (faltaParaLasCuotas()) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            // El cambio de monto se detecta ANTES de mirar el bloqueo: si la cajera escribió otro
            // monto, el bloqueo anterior ya no corresponde y hay que limpiarlo (si no, quedaría
            // trabada con un motivo viejo).
            var aplicado = montoAplicadoAnticipo(recibido);
            if (aplicado !== montoAnticipoAplicadoVigente) {
                montoAnticipoAplicadoVigente = aplicado;
                previewAnticipoPendiente = true;
                mensajeBloqueoAnticipo = null;
                refrescarAvisoAnticipo();
                pedirVistaPreviaAnticipo({monto: aplicado});
            }
            // Corrección de Joel tras la prueba en pantalla del 27/10/2026: con el monto
            // rechazado por el servidor, "Vuelto" mostraba "Sin vuelto (0 Gs.)" como si el cobro
            // fuera a salir. Va "—", igual que con el campo vacío: el motivo ya está en el aviso
            // rojo de arriba, y acá no hay ningún vuelto que calcular.
            if (mensajeBloqueoAnticipo) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            textoVuelto.classList.remove('text-danger');
            textoVuelto.style.color = '';
            // Corrección de Joel (12/10/2026): "Sin vuelto (0 Gs.)" en pantalla, igual que en el
            // cartel de confirmación -- antes decía "Se aplica todo a su deuda", un texto distinto
            // para el mismo caso (vuelto 0).
            textoVuelto.textContent = recibido <= montoElegido ? 'Sin vuelto (0 Gs.)'
                    : formatearGs(recibido - montoElegido);
            // Ampliación del 27/10/2026 (condición de Joel): "Cobrar" nunca se habilita con una
            // vista previa todavía en camino -- lo que se va a cobrar tiene que estar en pantalla
            // antes de poder confirmarlo.
            botonCobrar.disabled = previewAnticipoPendiente;
        }

        // D-145 (P-6, pedido de Joel): regresión -- la pantalla anterior permitía el pago parcial
        // de lo exigible con fecha de promesa, y confirmarCobro (sin cambios) lo sigue aceptando
        // así. Si lo recibido no llega a lo exigible, ya no se bloquea: se aplica todo lo
        // recibido y se pide la fecha de promesa para el resto, igual que antes. D-146 (P-17): el
        // tramo entre "lo exigible" y el monto sugerido completo (solo existe si hay una cuota
        // que vence justo hoy además de lo vencido, D-144) ya NO avisa "Falta efectivo" -- se
        // cobra lo vencido sin promesa, sin mora sobre la cuota de hoy.
        function recalcularCobrar(recibido) {
            var minimoNormal = pisoRedondeo(montoElegido);
            if (recibido <= 0) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                ocultarFechaPromesa();
                botonCobrar.disabled = true;
                return;
            }
            if (recibido >= minimoNormal) {
                var vuelto = Math.max(0, recibido - minimoNormal);
                // Corrección de Joel (12/10/2026): "Sin vuelto (0 Gs.)" en pantalla, igual que en
                // el cartel -- antes decía "0 Gs." sin la aclaración.
                textoVuelto.textContent = vuelto === 0 ? 'Sin vuelto (0 Gs.)' : formatearGs(vuelto);
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                ocultarFechaPromesa();
                botonCobrar.disabled = false;
                return;
            }
            // D-146 (P-17, pedido de Joel): cubre lo vencido pero no la cuota que vence justo hoy
            // (solo existe esta franja si hay una, D-144) -- ya no es "Falta efectivo": se cobra
            // lo vencido sin pedir promesa (RN-20/confirmarCobro solo miran lo vencido, no la
            // cuota de hoy) y el resto se aplica a la cuota de hoy sin mora, porque todavía no
            // está vencida. Se manda lo recibido tal cual, como siempre -- confirmarCobro (sin
            // tocar) ya reparte vencidas primero y lo que sobra a la cuota de hoy.
            // A3a (#7, pedido de Joel): ámbar, no rojo -- no es un bloqueo, es un cobro normal
            // sin mora, pero distinto de un cobro completo (antes se veía igual que uno). A3c:
            // ámbar OSCURO (#a15c00) en vez de text-warning, que casi no se lee sobre blanco.
            if (recibido >= montoExigible) {
                ocultarFechaPromesa();
                // B98: si no hay nada vencido, esto es un cobro A CUENTA de la cuota de hoy: la
                // linea grande tiene que seguir al monto escrito (B79), no quedarse en el total de
                // la cuota. Con cuotas vencidas de verdad, la tarjeta no cambia (D-146 P-17).
                if (soloCuotaDeHoy) {
                    fijarCobroACuenta({recibido: recibido, total: montoElegido,
                        pendiente: montoElegido - recibido});
                }
                // Corrección de Joel (12/10/2026): "Sin vuelto (0 Gs.)" en pantalla, igual que en
                // el cartel -- antes decía "0 Gs." sin la aclaración.
                textoVuelto.textContent = 'Sin vuelto (0 Gs.)';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = AMBAR_OSCURO;
                botonCobrar.disabled = false;
                return;
            }
            mostrarFechaPromesa();
            var pendiente = montoExigible - recibido;
            // B81 (30/10/2026, decisión de Joel): el pago parcial se dice ARRIBA, en la línea
            // grande ("Cobra 100.000 Gs. (pago parcial)") y en su renglón de detalle ("de 180.696
            // Gs. exigibles — queda pendiente 80.696 Gs."), que es donde la cajera mira el monto.
            // Antes la línea grande seguía mostrando lo exigible y esto se explicaba acá abajo, en
            // "Vuelto" y más chico. El mismo dato no se escribe en los dos lugares: en "Vuelto"
            // queda lo que corresponde a ese rótulo -- no hay vuelto, porque se aplica todo.
            fijarCobroParcial({recibido: recibido, exigible: montoExigible, pendiente: pendiente});
            // B95: recien ahora se sabe que este cobro es parcial, asi que se vuelve a resolver el
            // renglon de motivos (puede tener que mostrar el pedido en gris de la fecha, o
            // limpiarlo si ya la eligio).
            refrescarAvisoAnticipo();
            // Corrección de Joel (12/10/2026): "Sin vuelto (0 Gs.)" en pantalla, igual que en el
            // cartel. A3a (#7): ámbar, para distinguir a simple vista un cobro parcial de uno
            // completo.
            textoVuelto.textContent = 'Sin vuelto (0 Gs.)';
            textoVuelto.classList.remove('text-danger');
            textoVuelto.style.color = AMBAR_OSCURO;
            var fechaValida = fechaPromesaValida();
            if (avisoFechaPromesaInvalida) {
                // B95: el rojo es para una fecha que de verdad no sirve (hoy o anterior). Con el
                // campo vacio queda oculto: ahi el pedido ya esta en gris arriba de "Cobrar".
                avisoFechaPromesaInvalida.hidden = !(hayFechaPromesa() && !fechaValida);
            }
            botonCobrar.disabled = !fechaValida;
        }

        // Mejora de pantalla del 27/10/2026: esta es la función de siempre, con el nombre
        // cambiado -- "recalcular()" ahora es el envoltorio de abajo, que además pone el monto
        // en el texto del botón. Así el botón se actualiza en TODOS los caminos, incluidos los
        // varios "return" tempranos de acá adentro, sin tocar ninguno de ellos.
        function recalcularEstado() {
            // B81/B98: cada pasada arranca sin pago parcial ni cobro a cuenta; solo
            // recalcularCobrar (mas abajo) sabe si los hay, y los vuelve a fijar con los numeros
            // de esta pasada.
            cobroParcialVigente = null;
            cobroACuentaVigente = null;
            // Ampliación del 27/10/2026: el aviso del adelanto y qué piezas se ven se refrescan
            // siempre, antes de cualquier otra cosa -- así nunca queda a la vista una vista previa
            // que no corresponde a lo que está escrito (lo que encontró Joel en la prueba).
            refrescarAvisoAnticipo();
            mostrarPiezasSegunOpcion();
            // "Por monto": el monto del resumen es lo que la cajera va escribiendo en "Recibido"
            // (en esa opción se aplica todo lo recibido, ver montoAplicadoAnticipo). Se escribe
            // acá, en el único lugar por el que pasan todos los cambios, para que el resumen
            // nunca quede mostrando el monto de otra opción.
            // B106 (05/11/2026): el rótulo lo decide ServicioCobranza.ACCION_ADELANTAR_A_CAPITAL y
            // llega en el data-; acá solo se pide el monto. Antes estaba escrito a mano.
            if (opcionElegida === 'monto') {
                mostrarEnResumen(plantillaDelResumen('data-opcion-anticipo', 'Adelantar'),
                        leerRecibido(), 0);
            }
            if (!accionElegida) {
                botonCobrar.disabled = true;
                ocultarFechaPromesa();
                return;
            }
            var recibido = leerRecibido();
            // D-146 (P-15, pedido de Joel): el efectivo físico tiene que venir en múltiplos del
            // parámetro -- aviso en rojo y no deja cobrar, en cualquier modo, antes de cualquier
            // otro cálculo.
            if (recibido > 0 && !recibidoEsMultiploValido(recibido)) {
                if (avisoRecibidoNoMultiplo) {
                    avisoRecibidoNoMultiplo.hidden = false;
                }
                // B79: el aviso de múltiplos es otro renglón (no el del adelanto), pero bloquea
                // igual -- la línea grande tampoco puede afirmar un monto acá.
                fijarBloqueoResumen(true);
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                ocultarFechaPromesa();
                botonCobrar.disabled = true;
                return;
            }
            if (avisoRecibidoNoMultiplo) {
                avisoRecibidoNoMultiplo.hidden = true;
            }
            if (accionElegida === 'anticipar') {
                ocultarFechaPromesa();
                recalcularAnticipo(recibido);
                return;
            }
            if (accionElegida === 'cobrar') {
                recalcularCobrar(recibido);
                return;
            }
            ocultarFechaPromesa();
            var minimo = pisoRedondeo(montoElegido);
            // Corrección de Joel (27/10/2026, prueba de la pantalla nueva): lo recibido por
            // debajo del monto en efectivo se avisa igual que en "Por cantidad de cuotas" -- el
            // motivo en rojo adentro del resumen (lo pone refrescarAvisoAnticipo con
            // faltaParaCancelarTodo) y "Cobrar" en gris. El "Falta X Gs." que iba acá, en
            // "Vuelto", queda en "—": el aviso de arriba ya dice lo recibido y lo que hace falta,
            // y acá no hay ningún vuelto que calcular (mismo criterio que el adelanto bloqueado).
            if (recibido > 0 && recibido < minimo) {
                textoVuelto.textContent = '—';
                textoVuelto.classList.remove('text-danger');
                textoVuelto.style.color = '';
                botonCobrar.disabled = true;
                return;
            }
            var vuelto = Math.max(0, recibido - minimo);
            // Corrección de Joel (12/10/2026): "Sin vuelto (0 Gs.)" en pantalla, igual que en
            // el cartel -- antes decía "0 Gs." sin la aclaración.
            textoVuelto.textContent = recibido <= 0 ? '—'
                    : (vuelto === 0 ? 'Sin vuelto (0 Gs.)' : formatearGs(vuelto));
            textoVuelto.classList.remove('text-danger');
            textoVuelto.style.color = '';
            botonCobrar.disabled = recibido <= 0;
        }

        // Mejora de pantalla del 27/10/2026 (diseño aprobado por Joel): el botón dice exactamente
        // qué va a pasar ("Cobrar 232.600 Gs.").
        // Decisión de Joel tras la prueba en pantalla del 27/10/2026: el monto del botón es el
        // que la cajera RECIBE EN EFECTIVO, es decir el mismo "montoAplicado" que calcula el
        // manejador del clic acá abajo, rama por rama -- no el monto exacto de la deuda. Con el
        // redondeo de D-141 los dos valores difieren (ej. en "Todo el préstamo": 2.548.415
        // exacto, 2.548.400 en efectivo), y lo que la cajera tiene en la mano es el segundo. El
        // resumen sigue mostrando los dos ("Todo el préstamo: 2.548.415 Gs." y, debajo, "en
        // efectivo: 2.548.400 Gs."). Si alguna vez cambia el reparto de abajo, tiene que cambiar
        // acá también:
        //  - "anticipar": montoAplicado = montoAplicadoAnticipo(recibido).
        //  - "cobrar"/"cancelar" con lo recibido suficiente: montoAplicado = el piso de D-141.
        //  - "cobrar" por debajo de eso (pago parcial con promesa, o solo lo vencido): se cobra
        //    todo lo recibido.
        function montoQueSeVaACobrar() {
            var recibido = leerRecibido();
            if (accionElegida === 'anticipar') {
                return montoAplicadoAnticipo(recibido);
            }
            if (accionElegida === 'cobrar' && recibido < pisoRedondeo(montoElegido)) {
                return recibido;
            }
            return pisoRedondeo(montoElegido);
        }

        // Con el botón en gris (sin monto válido, o bloqueado por el servidor) dice solo
        // "Cobrar": no hay ningún monto que prometer todavía.
        function actualizarTextoBotonCobrar() {
            if (!botonCobrar) {
                return;
            }
            var monto = botonCobrar.disabled ? 0 : montoQueSeVaACobrar();
            botonCobrar.textContent = monto > 0 ? 'Cobrar ' + formatearGs(monto) : 'Cobrar';
        }

        // El nombre de siempre, para no tocar ninguno de los lugares que ya lo llaman.
        function recalcular() {
            recalcularEstado();
            actualizarTextoBotonCobrar();
        }

        // Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel): marca cuál de las
        // opciones de "¿Cuánto quiere pagar el cliente?" está elegida. Al ser excluyentes, el
        // control de cantidad y el "Si adelanta..." dejan de verse cuando la elegida no es un
        // adelanto -- eso es lo que resuelve el hallazgo B74.
        function marcarOpcionActiva(botonActivo) {
            [botonPagarCuota, botonOpcionMonto, botonCancelarTodo].forEach(function (boton) {
                if (boton) {
                    boton.classList.toggle('active', boton === botonActivo);
                }
            });
        }

        // El control de cantidad se ve mientras la opción elegida sea "Por cantidad de cuotas"
        // (incluso con N = K, donde la acción pasa a la cancelación total: hay que poder volver a
        // bajar la cantidad). El bloque de vista previa del adelanto se ve solo cuando de verdad
        // hay un adelanto que mostrar -- eso es lo que cierra B74 y, además, lo que evita que
        // quede a la vista una previa que no corresponde a lo escrito.
        function mostrarPiezasSegunOpcion() {
            var esAnticipo = accionElegida === 'anticipar';
            // "esperandoMontoDeCuotas": la vista previa que está en pantalla es de otra cantidad,
            // así que se oculta hasta que llegue la nueva (corrección de Joel, 27/10/2026).
            // Corrección de Joel (30/10/2026, prueba de B79): antes la condición era
            // "!mensajeBloqueoAnticipo" -- solo el rechazo del SERVIDOR ocultaba la previa. Con un
            // monto no múltiplo de 50 o por debajo del mínimo, el bloqueo es local y la previa
            // quedaba a la vista con los datos del monto ANTERIOR ("le quedan 10 cuotas", "Capital
            // aplicado: 8.120"). Ahora pregunta por el mismo "hay bloqueo" que usa la línea grande
            // (un dato, un dueño): venga de donde venga, con bloqueo no se muestra ninguna previa.
            var hayPrevia = esAnticipo && !bloqueoVigente && !faltaEscribirElMonto()
                    && !esperandoMontoDeCuotas;
            if (grupoCuotasAnticipo) {
                grupoCuotasAnticipo.hidden = opcionElegida !== 'cuotas';
            }
            if (bloqueVistaPreviaAnticipo) {
                bloqueVistaPreviaAnticipo.hidden = !hayPrevia;
            }
            if (detalleAnticipo) {
                detalleAnticipo.hidden = !hayPrevia;
            }
            // B203: el desglose de la cancelacion no depende de ninguna vista previa en vivo
            // (el monto de la cancelacion total no cambia con lo que se teclea): se ve con la
            // opcion elegida y se oculta con cualquier otra.
            if (detalleCancelacion) {
                detalleCancelacion.hidden = accionElegida !== 'cancelar';
            }
            // La letra chica del pie habla del adelanto en general (los múltiplos de 50, que
            // reduce la cantidad de cuotas), no de una vista previa concreta: se ve mientras la
            // opción elegida sea un adelanto, aunque todavía falte escribir el monto.
            if (notaAnticipoPie) {
                notaAnticipoPie.hidden = !esAnticipo;
            }
        }

        // Corrección de diseño del 27/10/2026 (opción B): cambia cuántas cuotas paga el cliente,
        // de 1 hasta las que le quedan (K). Acá NO se calcula ningún monto: se le pide al servidor
        // (?cuotas=N) y el monto vuelve en la respuesta. El clamp es solo para no mandar un N
        // fuera de rango -- el servidor lo valida igual (nunca calcula con un N inválido).
        function seleccionarCuotas(n, cambioDeOpcion) {
            if (cuotasQueQuedan < 1) {
                return;
            }
            var cuotas = Math.round(n);
            if (!isFinite(cuotas) || cuotas < 1) {
                cuotas = 1;
            }
            if (cuotas > cuotasQueQuedan) {
                cuotas = cuotasQueQuedan;
            }
            var fijarRecibido = !!cambioDeOpcion && forzarRecibidoAlCambiarOpcion;
            cuotasElegidas = cuotas;
            opcionElegida = 'cuotas';
            marcarOpcionActiva(botonPagarCuota);
            if (campoCantidadCuotas && parseInt(campoCantidadCuotas.value, 10) !== cuotas) {
                campoCantidadCuotas.value = cuotas.toString();
            }
            if (botonMenosCuotas) {
                botonMenosCuotas.disabled = cuotas <= 1;
            }
            if (botonMasCuotas) {
                botonMasCuotas.disabled = cuotas >= cuotasQueQuedan;
            }
            if (cuotas >= cuotasQueQuedan) {
                // N = K (decisión de Joel, 27/10/2026): pagar todas las cuotas que le quedan no es
                // un adelanto, ES la cancelación total -- mismo monto y mismo formulario de "Todo
                // el préstamo" (confirmarCancelacionTotal, RF26/RF28, sin tocar nada). No se pide
                // vista previa de adelanto porque no hay plan nuevo que mostrar: no queda ninguna
                // cuota.
                accionElegida = 'cancelar';
                montoElegido = montoCancelarTodo;
                mensajeBloqueoAnticipo = null;
                previewAnticipoPendiente = false;
                esperandoMontoDeCuotas = false;
                mostrarMontoEnOpcionCuotas(cuotas, montoCancelarTodo, '— todo el préstamo');
                mostrarAhorroAnticipo('');
                autocompletarRecibido(montoCancelarTodoRedondeado, fijarRecibido);
                recalcular();
                return;
            }
            accionElegida = 'anticipar';
            previewAnticipoPendiente = true;
            // Corrección de Joel (27/10/2026, segunda prueba): hasta que el servidor responda, la
            // tarjeta dice "calculando…" en vez de mostrar el monto de la cantidad anterior, la
            // vista previa vieja se oculta y "Cobrar" queda en gris. El monto de esta cantidad lo
            // decide el servidor y llega todo junto en la respuesta.
            esperandoMontoDeCuotas = true;
            mensajeBloqueoAnticipo = null;
            mostrarCalculandoEnOpcionCuotas(cuotas);
            pedirVistaPreviaAnticipo({cuotas: cuotas, fijarRecibido: fijarRecibido});
            recalcular();
        }

        if (campoCantidadCuotas) {
            // Se escribe la cantidad directo (con 24 cuotas, llegar a 17 a fuerza de clics en
            // "+" es inviable). Mientras el campo esté vacío no se toca nada: la cajera todavía
            // está escribiendo. El pedido al servidor va con el mismo atraso de siempre
            // (RETRASO_MS_PREVIEW), así que escribir "17" pide una sola vista previa, no dos.
            campoCantidadCuotas.addEventListener('input', function () {
                var crudo = (campoCantidadCuotas.value || '').replace(/\D/g, '');
                if (!crudo) {
                    return;
                }
                seleccionarCuotas(parseInt(crudo, 10));
            });
            campoCantidadCuotas.addEventListener('change', function () {
                var crudo = (campoCantidadCuotas.value || '').replace(/\D/g, '');
                seleccionarCuotas(crudo ? parseInt(crudo, 10) : 1);
            });
        }
        if (botonMenosCuotas) {
            botonMenosCuotas.addEventListener('click', function () {
                seleccionarCuotas(cuotasElegidas - 1);
            });
        }
        if (botonMasCuotas) {
            botonMasCuotas.addEventListener('click', function () {
                seleccionarCuotas(cuotasElegidas + 1);
            });
        }

        function seleccionar(accion, monto, boton, montoEnEfectivo, cambioDeOpcion) {
            accionElegida = accion;
            montoElegido = monto;
            if (accion !== 'anticipar') {
                mensajeBloqueoAnticipo = null;
                previewAnticipoPendiente = false;
                esperandoMontoDeCuotas = false;
            }
            if (accion === 'anticipar' && boton === botonPagarCuota) {
                opcionElegida = 'cuotas';
                if (cargaInicial) {
                    // Primera pasada de la carga: el servidor ya dejó renderizada la vista previa
                    // de 1 cuota, así que no hace falta pedir nada.
                    cuotasElegidas = 1;
                    if (campoCantidadCuotas) {
                        campoCantidadCuotas.value = '1';
                    }
                    if (botonMenosCuotas) {
                        botonMenosCuotas.disabled = true;
                    }
                    if (botonMasCuotas) {
                        botonMasCuotas.disabled = cuotasQueQuedan <= 1;
                    }
                    mostrarMontoEnOpcionCuotas(1, montoCuotasInicial, null);
                    montoElegido = montoCuotasInicial;
                    montoAnticipoAplicadoVigente = montoCuotasInicial;
                    if (campoMontoSugeridoFormAnticipar) {
                        campoMontoSugeridoFormAnticipar.value = montoCuotasInicial.toString();
                    }
                    marcarOpcionActiva(botonPagarCuota);
                    mostrarPiezasSegunOpcion();
                    autocompletarRecibido(montoCuotasInicial, false);
                    recalcular();
                    return;
                }
                // Corrección de Joel (27/10/2026, segunda prueba en pantalla): volver a esta
                // opción CONSERVA la cantidad que estaba elegida (antes volvía a 1, y además
                // dejaba la tarjeta, la casilla, la vista previa y el vuelto con cantidades
                // distintas entre sí). El monto se lo vuelve a pedir al servidor para esa misma
                // cantidad, así las cinco piezas se actualizan juntas.
                marcarOpcionActiva(botonPagarCuota);
                seleccionarCuotas(cuotasElegidas, true);
                return;
            }
            if (accion === 'cobrar') {
                opcionElegida = 'cuotas';
                // Mejora de pantalla del 27/10/2026: con cuotas vencidas el monto no lo reescribe
                // nadie más (no hay vista previa que pedir), así que al volver a esta opción desde
                // "Todo el préstamo" hay que devolver el resumen a su monto. Son los mismos dos
                // valores que la JSP ya dejó renderizados al cargar la ficha.
                // B98 (corrección de Joel, 30/10/2026, segunda prueba): el rótulo NO puede estar
                // escrito acá. Esta línea corre también en la carga inicial (D-145 P-4 preselecciona
                // la opción 1), así que pisaba el primer render correcto de la JSP y la línea grande
                // volvía a decir "Pagar lo vencido" con una cuota que vence hoy. El rótulo lo decide
                // el servidor y llega en el data-; acá solo se pide el monto.
                mostrarEnResumen(plantillaDelResumen('data-accion-etiqueta', 'Cobrar'),
                        montoPagarCuota, montoPagarCuotaRedondeado);
            }
            marcarOpcionActiva(boton);
            mostrarPiezasSegunOpcion();
            // 13/10/2026 (pedido de Joel): al cambiar de opción, "Recibido" se actualiza al monto
            // en efectivo de la opción nueva. Corrección del 27/10/2026: con nada vencido el monto
            // de la opción anterior SÍ se pisa (ver autocompletarRecibido); con cuotas vencidas
            // sigue sin pisarse lo que la cajera escribió a mano.
            autocompletarRecibido(montoEnEfectivo || monto,
                    !!cambioDeOpcion && forzarRecibidoAlCambiarOpcion);
            recalcular();
        }

        if (botonPagarCuota) {
            botonPagarCuota.addEventListener('click', function () {
                seleccionar(pagarCuotaVia, montoPagarCuota, botonPagarCuota, montoPagarCuotaRedondeado, true);
            });
        }
        // Ampliación del 27/10/2026: "Por monto" -- el monto es lo que la cajera escriba en
        // "Recibido"; el servidor muestra en vivo qué cubre y cómo queda el préstamo. No se
        // borra lo que ya esté escrito: sirve igual como punto de partida.
        if (botonOpcionMonto) {
            botonOpcionMonto.addEventListener('click', function () {
                accionElegida = 'anticipar';
                opcionElegida = 'monto';
                mensajeBloqueoAnticipo = null;
                // Si venía esperando el monto de una cantidad de cuotas, ese pedido ya no manda:
                // acá el monto es el que escriba la cajera.
                esperandoMontoDeCuotas = false;
                marcarOpcionActiva(botonOpcionMonto);
                // "Recibido" NO se toca: en esta opción ese campo ES la respuesta de la cajera, y
                // lo que haya quedado del monto de la opción anterior le sirve de punto de partida
                // (lo confirmó Joel en pantalla el 27/10/2026).
                recalcular();
                campoRecibido.focus();
            });
        }
        botonCancelarTodo.addEventListener('click', function () {
            opcionElegida = 'todo';
            // Mejora de pantalla del 27/10/2026: el monto de esta opción (y su "en efectivo" de
            // D-141, si el redondeo lo cambia) se lee ahora en el resumen, no adentro del botón.
            // Son los mismos dos valores que ya traía la tarjeta en sus data-*.
            // B106 (05/11/2026): y el rótulo lo decide
            // ServicioCobranza.OPCION_TODO_EL_PRESTAMO, el mismo dueño que el texto del botón de
            // esta opción -- antes estaba escrito a mano acá y podía quedar diciendo otra cosa.
            mostrarEnResumen(plantillaDelResumen('data-opcion-todo', 'Cobrar'),
                    montoCancelarTodo, montoCancelarTodoRedondeado);
            seleccionar('cancelar', montoCancelarTodo, botonCancelarTodo, montoCancelarTodoRedondeado, true);
        });
        campoRecibido.addEventListener('input', recalcular);
        if (campoFechaPromesa) {
            // D-145 (P-6): habilita/deshabilita "Cobrar" apenas se elige o se borra la fecha.
            campoFechaPromesa.addEventListener('input', recalcular);
            campoFechaPromesa.addEventListener('change', recalcular);
        }

        botonCobrar.addEventListener('click', function () {
            if (!accionElegida) {
                return;
            }
            var recibido = leerRecibido();
            // D-146 (P-15, pedido de Joel): red de seguridad -- el botón ya queda deshabilitado
            // por recalcular(), esto es solo por si acaso (mismo criterio que fechaPromesaValida()
            // acá abajo).
            if (!recibidoEsMultiploValido(recibido)) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Monto inválido',
                    // A3c (pedido de Joel): formatearGs ya termina en "Gs." -- agregar otro "."
                    // duplicaba el punto.
                    text: 'El efectivo va en múltiplos de ' + formatearGs(redondeo),
                    confirmButtonText: 'Entendido'
                });
                return;
            }
            var montoAplicado; // lo que de verdad se aplica a la deuda
            // D-145 (P-6, pedido de Joel): "Pagar cuota" por debajo de lo exigible -- pago
            // parcial con fecha de promesa, igual que la pantalla anterior.
            var esParcialCobro = false;
            var fechaPromesaElegida = null;
            // D-146 (P-17, pedido de Joel): cubre lo vencido pero no la cuota de hoy -- se cobra
            // lo vencido sin promesa, el resto va a la cuota de hoy sin mora.
            var esSoloLoVencido = false;

            if (accionElegida === 'anticipar') {
                // Red de seguridad: el botón ya queda deshabilitado mientras el servidor no haya
                // dicho cuánto cuesta la cantidad elegida (corrección de Joel, 27/10/2026).
                if (esperandoMontoDeCuotas) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Un momento',
                        text: 'Se está calculando el monto de esa cantidad de cuotas. Intente de nuevo en un segundo.',
                        confirmButtonText: 'Entendido'
                    });
                    return;
                }
                // Ampliación del 27/10/2026: red de seguridad -- el botón ya queda deshabilitado
                // por recalcular() mientras el servidor rechace este monto; esto es solo por si
                // acaso (mismo criterio que el aviso de múltiplos de arriba).
                if (mensajeBloqueoAnticipo) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'No se puede adelantar ese monto',
                        text: mensajeBloqueoAnticipo,
                        confirmButtonText: 'Entendido'
                    });
                    return;
                }
                // Corrección de Joel (27/10/2026, tercera prueba): red de seguridad del aviso de
                // "no alcanza para N cuotas" -- el botón ya queda en gris, esto es por si acaso.
                var faltaCuotas = faltaParaLasCuotas();
                if (faltaCuotas) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Falta efectivo para esa cantidad de cuotas',
                        text: faltaCuotas,
                        confirmButtonText: 'Entendido'
                    });
                    return;
                }
                // D-145 (P-5, pedido de Joel): el mínimo de "Adelantar a capital" es el interés +
                // IVA del período (no la cuota sugerida completa) -- "Falta efectivo" no aplica
                // acá, el aviso es el mismo que ya se muestra en el campo "Vuelto".
                if (recibido < montoAnticipoMinimo) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Monto insuficiente',
                        // A3c (pedido de Joel): mismo motivo que arriba -- sin el "." de más.
                        text: 'El mínimo para adelantar hoy es ' + formatearGs(montoAnticipoMinimo),
                        confirmButtonText: 'Entendido'
                    });
                    return;
                }
                montoAplicado = montoAplicadoAnticipo(recibido);
            } else if (accionElegida === 'cobrar') {
                var minimoNormal = pisoRedondeo(montoElegido);
                if (recibido >= minimoNormal) {
                    montoAplicado = minimoNormal;
                } else if (recibido >= montoExigible) {
                    // D-146 (P-17): cubre lo vencido pero no la cuota de hoy -- ya no es "Falta
                    // efectivo". Se manda lo recibido tal cual (como siempre); confirmarCobro (sin
                    // tocar) reparte vencidas primero y el resto a la cuota de hoy, sin mora.
                    esSoloLoVencido = true;
                    montoAplicado = recibido;
                } else {
                    if (!fechaPromesaValida()) {
                        Swal.fire({
                            icon: 'warning',
                            title: 'Fecha de promesa requerida',
                            text: 'Ingrese una fecha de promesa posterior a hoy para el resto.',
                            confirmButtonText: 'Entendido'
                        });
                        return;
                    }
                    esParcialCobro = true;
                    fechaPromesaElegida = campoFechaPromesa.value;
                    montoAplicado = recibido;
                }
            } else {
                // 'cancelar' -- sin cambios.
                var minimo = pisoRedondeo(montoElegido);
                if (recibido < minimo) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Falta efectivo',
                        text: 'Falta ' + formatearGs(calcularFalta(recibido, minimo)) + ' (recibido '
                                + formatearGs(recibido) + ' de ' + formatearGs(minimo) + ').',
                        confirmButtonText: 'Entendido'
                    });
                    return;
                }
                montoAplicado = minimo;
            }

            var vuelto = Math.max(0, recibido - montoAplicado);
            // Aprobación de Joel (380b400, punto A2): título y texto del botón nombran la acción
            // -- igual en los 3 casos, incluidos los dos sub-casos de "cobrar" de abajo.
            // Ampliación del 27/10/2026: los rótulos del cartel acompañan a los de las opciones
            // nuevas ("Pagar lo vencido" / "Todo el préstamo"), si no la cajera elegía una cosa y
            // el cartel le confirmaba otra con otro nombre.
            // B98: el rotulo de la accion "cobrar" lo decide el servidor (puede ser "Pagar lo
            // vencido" o "Pagar la cuota de hoy").
            // B106 (05/11/2026): los otros dos tambien. Eran los dos ultimos rotulos escritos a
            // mano en este archivo: el de la cancelacion total estaba duplicado con el boton de su
            // opcion en la JSP, y el del adelanto no existia en ninguna parte del servidor. Los
            // tres salen ahora del mismo lugar, asi que el cartel nombra la opcion con las mismas
            // palabras que la cajera acaba de leer en la tarjeta (regla de B96).
            var etiquetaAccion = accionElegida === 'cobrar'
                            ? plantillaDelResumen('data-accion-etiqueta', 'Cobrar')
                    : accionElegida === 'cancelar'
                            ? plantillaDelResumen('data-opcion-todo', 'Cobrar')
                    : plantillaDelResumen('data-opcion-anticipo', 'Adelantar');
            var tituloConfirmacion = 'Confirmar: ' + etiquetaAccion;
            // B106 (ampliación aprobada por Joel el 05/11/2026): los tres textos de este botón
            // también salen del servidor, igual que etiquetaAccion de arriba. Eran los últimos que
            // este archivo redactaba por su cuenta. Los dos respaldos son la forma mínima, sin la
            // frase del servidor (mismo criterio que plantillaDelResumen): un respaldo que repita
            // la frase sería otra copia, justo lo que este ítem viene a sacar.
            var textoBotonConfirmar = accionElegida === 'cobrar'
                            ? plantillaDelResumen('data-boton-vencido', 'Sí, cobrar')
                    : accionElegida === 'cancelar'
                            ? plantillaDelResumen('data-boton-todo', 'Sí, cobrar')
                    : plantillaDelResumen('data-boton-anticipo', 'Sí, adelantar');
            var html;
            if (esParcialCobro) {
                // B96 (30/10/2026, decision de Joel): el cartel decia "Pago parcial: X" / "Queda
                // pendiente: Y", una tercera redaccion de los mismos dos numeros que el resumen ya
                // dice con dueno unico. Ahora usa las MISMAS frases, asi la cajera confirma lo que
                // acabo de leer. La linea de la promesa se queda: es otro dato, no una copia.
                html = '<div>' + fraseCobroParcial(montoAplicado) + '</div>'
                        + '<div>' + fraseDetalleParcial(montoExigible, montoExigible - recibido) + '</div>'
                        + '<div>Promesa: ' + formatearFechaDdMmAaaa(fechaPromesaElegida) + '</div>';
                // B96: y el boton no puede decir "Si, cobrar lo vencido" cuando el cliente NO paga
                // todo lo vencido (misma leccion de B92: el numero esta bien, las palabras
                // confunden). Nombra el monto que se cobra; el del cobro completo no cambia.
                textoBotonConfirmar = plantillaDelResumen('data-parcial-boton', 'Sí, cobrar {monto}')
                        .replace('{monto}', formatearGs(montoAplicado));
            } else if (esSoloLoVencido) {
                // D-146 (P-17, pedido de Joel, segunda vuelta): dos textos según si sobra algo de
                // lo recibido para la cuota de hoy (montoElegido = monto cobrable completo,
                // exacto; montoExigible = solo lo vencido -- la diferencia es la cuota de hoy).
                var excedenteCuotaHoy = recibido - montoExigible;
                var pendienteCuotaHoy = montoElegido - recibido;
                if (soloCuotaDeHoy) {
                    // B98: no hay nada vencido, asi que el cartel REPITE lo que dice la tarjeta
                    // (regla de B96) en vez de hablar de "lo vencido" y de "lo que sobra".
                    html = '<div>' + fraseCobroACuenta(recibido) + '</div>'
                            + '<div>' + fraseDetalleACuenta(montoElegido, pendienteCuotaHoy) + '</div>';
                    textoBotonConfirmar = plantillaDelResumen('data-parcial-boton', 'Sí, cobrar {monto}')
                            .replace('{monto}', formatearGs(recibido));
                } else if (excedenteCuotaHoy > 0) {
                    // D-146 (P-17): hay vencidas de verdad y lo recibido las cubre, pero no alcanza
                    // la cuota de hoy. Los dos textos los arma ServicioCobranza (B98): el JS solo
                    // reemplaza numeros.
                    // El respaldo (pantalla vieja en cache) NO repite la frase: no se redacta
                    // dos veces lo mismo ni aca ni en un fallback (B98).
                    html = '<div>' + plantillaDelResumen('data-solo-vencido-con-excedente',
                                    'Confirme el cobro.')
                            .replace('{excedente}', formatearGs(excedenteCuotaHoy))
                            .replace('{pendiente}', formatearGs(pendienteCuotaHoy)) + '</div>';
                } else {
                    html = '<div>' + plantillaDelResumen('data-solo-vencido-sin-excedente',
                                    'Confirme el cobro.')
                            .replace('{pendiente}', formatearGs(pendienteCuotaHoy)) + '</div>';
                }
            } else {
                // D-145 (P-5): para "cobrar"/"cancelar" se sigue mostrando el monto exacto del
                // botón (lo que se salda de la deuda, D-141); para "anticipar" se muestra lo que
                // de verdad se va a aplicar ahora, que puede ser menos que la cuota sugerida.
                var montoMostrado = accionElegida === 'anticipar' ? montoAplicado : montoElegido;
                // Aprobación de Joel (380b400, punto A3): "Sin vuelto (0 Gs.)" en las 3 acciones
                // -- antes "anticipar" decía "Se aplica todo a su deuda" y cobrar/cancelar
                // mostraban "Vuelto: 0 Gs.", dos textos distintos para el mismo caso.
                var textoVueltoConfirmacion = vuelto === 0 ? 'Sin vuelto (0 Gs.)'
                        : 'Vuelto: ' + formatearGs(vuelto);
                // Decisión de Joel (27/10/2026, tras la prueba de la pantalla nueva): cuando el
                // redondeo de efectivo (D-141) hace que no sean el mismo número, el cartel abre
                // con lo que la cajera COBRA EN EFECTIVO -- el mismo monto que dice el botón -- y
                // explica debajo cuánta deuda cancela ese cobro y de cuánto es el redondeo a
                // favor del cliente. Sin redondeo (los montos ya son múltiplos del parámetro
                // credito.redondeo_efectivo) el cartel queda exactamente como antes, con una sola
                // línea. "Adelantar a capital" nunca entra acá: su monto ya viene en múltiplos de
                // 50 desde el servidor y no tiene par exacto/en efectivo (RF27 §2a).
                // Nada de esto cambia lo que se cobra: montoAplicado y montoElegido son los dos
                // valores que el script ya tenía; el servidor vuelve a calcular todo al confirmar.
                var hayRedondeoEnElCobro = accionElegida !== 'anticipar' && montoAplicado !== montoElegido;
                var lineaDelMonto = hayRedondeoEnElCobro
                        ? '<div>Cobra en efectivo: ' + formatearGs(montoAplicado) + '</div>'
                                + '<div>Cancela la deuda de: ' + formatearGs(montoElegido)
                                + ' (redondeo a favor del cliente: '
                                + formatearGs(montoElegido - montoAplicado) + ')</div>'
                        : '<div>' + etiquetaAccion + ': ' + formatearGs(montoMostrado) + '</div>';
                // B98: cobrando la cuota de hoy COMPLETA, el cartel la nombra ("Cobra la cuota 1/10
                // de hoy: X") y el boton tambien -- el monto solo no le dice nada al cliente.
                if (soloCuotaDeHoy && accionElegida === 'cobrar') {
                    lineaDelMonto = '<div>' + plantillaDelResumen('data-cartel-cuota-de-hoy',
                                    'Cobra la cuota de hoy: {monto}')
                            .replace('{monto}', formatearGs(montoAplicado)) + '</div>';
                    textoBotonConfirmar = plantillaDelResumen('data-boton-cuota-de-hoy',
                            'Sí, cobrar la cuota de hoy');
                }
                html = lineaDelMonto
                        + '<div>Recibido: ' + formatearGs(recibido) + '</div>'
                        + '<div>' + textoVueltoConfirmacion + '</div>';
                // Corrección de Joel (27/10/2026, tercera prueba): el cartel arranca con las
                // mismas palabras que la cajera eligió, así confirma lo que pidió y no solo un
                // monto suelto. El resto del cartel no cambia. B92 (29/10/2026): esas palabras
                // salen de la misma frase que la tarjeta (fraseAdelantoCuotas), para que no puedan
                // decir una cosa distinta.
                if (accionElegida === 'anticipar' && opcionElegida === 'cuotas') {
                    html = '<div><strong>' + fraseAdelantoCuotas(cuotasElegidas) + '</strong></div>' + html;
                }
                // B199 + B200 (15/11/2026): la cancelacion total nombra de QUIEN es (arriba de todo,
                // como en B178/B192: lo primero que se lee es para quien) y avisa que se emite una
                // constancia NUMERADA (al final, que es lo que pasa despues de confirmar). Las dos
                // frases las arma ServicioCobranza y viajan en un data-: aca solo se muestran
                // (regla de B92). El respaldo es vacio a proposito -- con una pantalla vieja en
                // cache no se agrega nada, nunca una redaccion inventada (criterio de B98/B106).
                // Se escapan porque llevan el nombre del cliente, que es dato de la base (igual que
                // el aviso de reversion de C30).
                if (accionElegida === 'cancelar') {
                    var quienCancelacion = plantillaDelResumen('data-cancelar-quien', '');
                    var constanciaCancelacion = plantillaDelResumen('data-cancelar-constancia', '');
                    if (quienCancelacion) {
                        html = '<div>' + escaparHtml(quienCancelacion) + '</div>' + html;
                    }
                    if (constanciaCancelacion) {
                        html += '<div class="mt-2">' + escaparHtml(constanciaCancelacion) + '</div>';
                    }
                }
                // Aprobación de Joel (380b400, punto A1): en "Adelantar a capital" se reusa el
                // detalle ya calculado y visible en la tarjeta (contenidoDetalleAnticipo/
                // textoProximaCuotaAnticipo) -- nada nuevo que calcular, ni en el servidor ni acá.
                if (accionElegida === 'anticipar') {
                    // Pedido de Joel (12/10/2026): cada dato en su propio renglón, no en un
                    // párrafo -- mismo texto y mismos números de siempre (lineasDesdeTexto/
                    // renglonesProximaCuota no calculan nada, solo separan lo que ya existe).
                    html += '<hr>' + lineasDesdeTexto(contenidoDetalleAnticipo)
                            + renglonesProximaCuota(textoProximaCuotaAnticipo);
                }
            }

            // C30: el aviso de la reversión pendiente va al final del cartel, en amarillo, para
            // los tres caminos -- después del detalle de la operación, porque no la impide: la
            // cajera lee la consecuencia y decide. Se escapa por disciplina (el texto viene del
            // servidor y el número de recibo no trae marcado, pero nada en este archivo tiene que
            // confiar en eso para armar HTML).
            if (avisoReversionPendiente) {
                html += '<div class="alert alert-warning text-left mt-3 mb-0">'
                        + escaparHtml(avisoReversionPendiente) + '</div>';
            }

            Swal.fire({
                title: tituloConfirmacion,
                html: html,
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: textoBotonConfirmar,
                // B116 (05/11/2026, hallazgo de Joel al probar B114): "No, volver", igual que los
                // 24 carteles del mecanismo de plantilla/pie.jsp, que lo traen por defecto desde
                // B3/B31 (commit f585f28). Este Swal.fire es de la OTRA familia -- no pasa por
                // pie.jsp -- y por eso B3 no lo alcanzó. Acá "Cancelar" era además el peor caso:
                // en una pantalla de cobranza esa palabra ya significa "cancelación total del
                // préstamo", así que el botón de descartar se leía como el de una operación, y
                // encima al lado de un "Sí, cobrar 540.300 Gs." que sí nombra la operación.
                cancelButtonText: 'No, volver',
                // B105 (B86, tramo transversal): el cartel abre con el foco en "Cancelar", igual
                // que los carteles de caja (data-confirmar-foco="cancelar" de plantilla/pie.jsp,
                // que es este mismo focusCancel). Los tres caminos de este cartel mueven efectivo
                // -- cobrar, adelantar a capital y cancelar todo el préstamo -- y un Enter apurado
                // no puede confirmarlos sin leer. Solo cambia dónde arranca el foco: el cartel, los
                // textos y lo que se envía al servidor no se tocan.
                focusCancel: true
            }).then(function (resultado) {
                if (!resultado.isConfirmed) {
                    return;
                }
                var idFormulario = accionElegida === 'cobrar' ? 'formCobrarUnificado'
                        : accionElegida === 'cancelar' ? 'formCancelarUnificado' : 'formAnticiparUnificado';
                var formulario = document.getElementById(idFormulario);
                var campoMonto = formulario.querySelector('input[name="montoRecibido"]');
                // D-145 (P-5/P-6, pedido de Joel): a los tres formularios se les manda lo recibido
                // tal cual -- "Cobrar"/"Cancelación total" ya calculan su propio piso/vuelto
                // (D-141); "Adelantar a capital" manda también "montoSugerido" (ver
                // cobranza/ficha.jsp) y es el servlet el que aplica el menor entre los dos.
                campoMonto.value = recibido.toString();
                if (accionElegida === 'anticipar') {
                    // Corrección de Joel (27/10/2026, segunda prueba en pantalla): "montoSugerido"
                    // se escribe ACÁ, desde el mismo "montoElegido" con el que se armó el cartel,
                    // en vez de confiar en que alguien lo haya dejado al día. Así queda garantizado
                    // que lo que el servidor aplica -- min(montoRecibido, montoSugerido) en
                    // procesarAnticipar -- es exactamente el monto que la cajera acaba de leer y
                    // confirmar. Antes, un "montoSugerido" viejo de otra opción podía hacer que el
                    // servidor intentara aplicar un monto distinto del confirmado (en el caso que
                    // encontró Joel, 3.500.000 en vez de 228.050, y el motor lo rechazaba con
                    // D-90 sin cobrar nada).
                    var campoSugerido = formulario.querySelector('input[name="montoSugerido"]');
                    if (campoSugerido) {
                        campoSugerido.value = montoElegido.toString();
                    }
                    // Decisión de Joel (27/10/2026): se le dice al servidor cuál de las dos formas
                    // del adelanto se usó, para que en "Por cantidad de cuotas" rechace un monto
                    // menor al de esa cantidad en vez de cobrar menos en silencio. En "Por monto"
                    // sigue valiendo el min() de D-145 (P-5), sin cambios.
                    var campoModo = formulario.querySelector('input[name="modoAnticipo"]');
                    if (campoModo) {
                        campoModo.value = opcionElegida === 'monto' ? 'monto' : 'cuotas';
                    }
                }
                if (accionElegida === 'cobrar') {
                    // D-145 (P-6): la fecha de promesa solo importa si confirmarCobro determina
                    // que el cobro es parcial -- mandarla igual cuando no corresponde no cambia
                    // nada, el servicio (sin tocar) la ignora fuera de ese caso.
                    var campoFecha = formulario.querySelector('input[name="fechaPromesa"]');
                    campoFecha.value = esParcialCobro ? fechaPromesaElegida : '';
                }
                formulario.submit();
            });
        });

        // D-145 (P-4, pedido de Joel): la primera opción ("Pagar lo vencido" / "Por cantidad de
        // cuotas", 1 cuota) queda preseleccionada al cargar la ficha, con el mismo estilo que al
        // tocarla, para que el vuelto se calcule apenas se escribe en "Recibido" -- y sin pedir
        // ninguna vista previa, porque la de 1 cuota ya vino renderizada con la página
        // (ver "cargaInicial"). "Por monto" y "Todo el préstamo" se eligen con
        // un clic, como antes.
        if (botonPagarCuota) {
            seleccionar(pagarCuotaVia, montoPagarCuota, botonPagarCuota, montoPagarCuotaRedondeado, false);
        } else {
            recalcular();
        }
        // De acá en adelante, volver a "Por cantidad de cuotas" siempre le pide al servidor el
        // monto de la cantidad elegida (ver seleccionar()).
        cargaInicial = false;
    });
})();
