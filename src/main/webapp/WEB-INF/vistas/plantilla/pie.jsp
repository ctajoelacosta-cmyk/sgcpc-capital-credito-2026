<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue" %>
<%!
    // D-146 (P-16, pedido de Joel): cache-busting de los .js propios -- "?v=" con un valor fijo
    // mientras el servidor esté arriba, para que el navegador baje la versión nueva después de
    // un redeploy sin necesitar Ctrl+Shift+R. RF08 (11/10/2026): el valor ahora viene de
    // VersionDespliegue, compartido con cabecera.jsp y reportes/indice.jsp -- antes cada JSP que
    // necesitaba cache-busting tenía que declarar el suyo (solo este archivo lo hacía).
    private static final String VERSION_ASSETS = VersionDespliegue.VALOR;
%>
<%
    Integer minutosInactividadSesion = (Integer) session.getAttribute(FiltroAutenticacion.ATRIBUTO_MINUTOS_INACTIVIDAD);
%>
            </div>
        </div>
    </div>

    <footer class="main-footer">
        <strong>&copy; <%= java.time.Year.now() %> ${nombreComercialResuelto}.</strong>
    </footer>
</div>

<script src="${pageContext.request.contextPath}/recursos/jquery/dist/jquery.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/bootstrap/dist/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/adminlte/dist/js/adminlte.min.js"></script>
<script src="${pageContext.request.contextPath}/recursos/sweetalert2/dist/sweetalert2.all.min.js"></script>
<script>
    window.CONTEXTO_APP = "${pageContext.request.contextPath}";
    window.MINUTOS_INACTIVIDAD = <%= minutosInactividadSesion == null ? 30 : minutosInactividadSesion %>;
</script>
<script src="${pageContext.request.contextPath}/recursos/js/campo-guaranies.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/campo-cedula.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/campo-contrasena.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/aviso-sesion.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/listado-vivo.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/buscar-en-tabla.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/fecha-primer-vencimiento.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/cobranza-ficha.js?v=<%= VERSION_ASSETS %>"></script>
<script src="${pageContext.request.contextPath}/recursos/js/filtro-cliente-operacion.js?v=<%= VERSION_ASSETS %>"></script>
<script>
    // Colores de SweetAlert2 configurados una sola vez, de forma global, con los
    // mismos del sistema (V5, azul de marca #0C325C / gris secundario) en vez del
    // violeta por defecto. Reasigna el propio "Swal": cualquier Swal.fire(...) de
    // esta pantalla o de las que se agreguen en etapas siguientes ya sale con
    // estos colores, sin tener que repetirlos en cada llamada.
    Swal = Swal.mixin({
        confirmButtonColor: '#0C325C',
        cancelButtonColor: '#6c757d'
    });

    // Punto 6: cualquier formulario con data-confirmar="mensaje" pide confirmacion
    // (SweetAlert2) antes de enviarse. Reutilizable para las etapas siguientes.
    // D-136: delegado en document (no en los form[data-confirmar] de ese momento) para que
    // tambien funcione con formularios insertados despues, ej. una fila nueva que trae
    // recursos/js/listado-vivo.js al reemplazar el contenido de un listado por AJAX.
    $(function () {
        $(document).on('submit', 'form[data-confirmar]', function (evento) {
            var formulario = this;
            if ($(formulario).data('confirmado')) {
                return true;
            }
            evento.preventDefault();
            // RF14 (pedido de Joel, 12/10/2026): revalida acá, explícitamente, antes de mostrar
            // la confirmación -- un campo con required/minlength que arranca dentro de un
            // contenedor oculto (ej. el formulario de anular, colapsado hasta que se hace clic en
            // "Anular préstamo") puede quedar sin la validación implícita del navegador al
            // enviarse, aunque el campo ya esté visible en ese momento (el motivo exacto depende
            // del navegador; este chequeo no depende de ese detalle). Sin esto, la confirmación
            // aparecía ANTES que el aviso de "Falta un dato" -- reportValidity() dispara el mismo
            // evento "invalid" de más abajo, así que el cartel es el mismo en todos los casos. No
            // cambia nada para un formulario que ya era válido al enviarse (el caso de siempre,
            // ej. RF19).
            if (!formulario.checkValidity()) {
                formulario.reportValidity();
                return;
            }
            // A3c (pedido de Joel): data-confirmar-foco="cancelar" en el formulario -- para las
            // confirmaciones irreversibles (cerrar caja, entregar efectivo, solicitar/autorizar
            // una reversión), SweetAlert2 arranca con el foco en "Cancelar" (focusCancel), así
            // Enter no confirma de un toque sin querer.
            // B105 (B86, tramo transversal, 05/11/2026): el atributo se extendió a TODOS los
            // carteles que mueven dinero o emiten documentos -- se sumaron abrir caja, confirmar
            // el desembolso, generar los documentos de formalización, regenerarlos y re-emitir los
            // 3 PDF. Sigue siendo una opción POR cartel, no un cambio global: un cartel que no
            // mueve plata ni emite nada (dar de baja un cliente, desactivar un feriado) arranca
            // como siempre, en "Sí". El cobro, el adelanto a capital y la cancelación total no
            // pasan por acá -- comparten un Swal.fire propio en recursos/js/cobranza-ficha.js, que
            // desde B105 lleva focusCancel: true por su cuenta.
            // RF08/RF09 (pedido de Joel, 11/10/2026): data-confirmar-boton="texto" en el
            // formulario -- cambia el texto del botón de confirmación (ej. "Sí, aprobar" en vez
            // de "Sí"), sin tocar el "Sí" por defecto de todos los demás data-confirmar del
            // sistema (si el formulario no trae el atributo, sigue siendo "Sí").
            // RF25 (pedido de Joel): data-confirmar-titulo="texto" en el formulario -- título
            // propio en vez del genérico "Confirmar" (ej. "Confirmar: Solicitar reversión"), sin
            // tocar el resto de los data-confirmar del sistema (si no lo traen, sigue "Confirmar").
            // B4/B43 (B86, tramo transversal, 05/11/2026): una confirmación que RESUME qué va a
            // pasar (cliente, monto, plazo, cuota, qué se emite) necesita dos cosas que este
            // mecanismo no tenía:
            //   (a) Un valor que recién se conoce al confirmar -- la fecha que el usuario tiene
            //       puesta en el campo, el motivo elegido en un <select>. La FRASE la arma siempre
            //       la JSP (regla de B92/B106/B98): deja {clave} en su data-confirmar y el script
            //       de la pantalla escribe SOLO el valor en data-confirmar-valor-<clave>. Se lee
            //       con getAttribute a propósito, NO con .data(): jQuery cachea el valor del
            //       atributo la primera vez que se lo pide, así que si el script lo escribiera ahí,
            //       un cambio de fecha después del primer "No, volver" quedaría invisible.
            //   (b) Renglones. SweetAlert2 pinta "text" con textContent y su contenedor viene con
            //       white-space normal, así que los saltos del mensaje se colapsan en un párrafo:
            //       la clase cartel-resumen (sgcpc-tema.css §8) los respeta y alinea a la
            //       izquierda. Se agrega SOLO si el mensaje trae saltos -- los demás carteles del
            //       sistema (una sola línea, sin llaves) salen exactamente igual que antes.
            var mensajeConfirmar = String($(formulario).data('confirmar'));
            mensajeConfirmar = mensajeConfirmar.replace(/\{([a-zA-Z]+)\}/g, function (llaves, clave) {
                var valor = formulario.getAttribute('data-confirmar-valor-' + clave);
                return valor === null ? llaves : valor;
            });
            Swal.fire({
                title: $(formulario).data('confirmarTitulo') || 'Confirmar',
                text: mensajeConfirmar,
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: $(formulario).data('confirmarBoton') || 'Sí',
                cancelButtonText: $(formulario).data('confirmarCancelar') || 'No, volver',
                focusCancel: $(formulario).data('confirmarFoco') === 'cancelar',
                customClass: mensajeConfirmar.indexOf('\n') >= 0 ? { htmlContainer: 'cartel-resumen' } : {}
            }).then(function (resultado) {
                if (resultado.isConfirmed) {
                    $(formulario).data('confirmado', true);
                    // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 13): el botón
                    // de confirmación se deshabilita junto con el/los botón(es) de envío del
                    // formulario, acá mismo, ANTES de formulario.submit() -- sin esto, una vez que
                    // el modal se cierra, el botón original queda clickeable de nuevo mientras el
                    // POST todavía no volvió (caso real: doble envío de "Generar documentos" en
                    // formalización, ver AVANCE.md). [type="submit"] cubre tanto <button> como
                    // <input>, en cualquier formulario del sistema que use data-confirmar.
                    $(formulario).find('[type="submit"]').prop('disabled', true);
                    formulario.submit();
                }
            });
        });
    });

    // A.9 (consolidación, pedido de Joel): reemplaza el popup nativo del navegador ("Completa
    // este campo", en tú) por el cartel propio del sistema (de usted) en TODO el sistema, para
    // cualquier campo con "required"/"minlength" -- sin tener que agregar nada en cada JSP.
    // "invalid" no burbujea (hay que escucharlo en fase de captura) y se dispara una vez por
    // cada campo inválido cuando el navegador intenta enviar el formulario; evento.preventDefault()
    // en cada uno evita el globo nativo, y se muestra un solo cartel por intento de envío (si
    // varios campos fallan a la vez, solo el primero, enfocado, igual que haría el navegador).
    (function () {
        var formulariosConAvisoPendiente = new WeakSet();

        function etiquetaDeCampo(campo) {
            var contenedor = campo.closest('.form-group, .form-check') || campo.parentElement;
            var etiqueta = contenedor ? contenedor.querySelector('label, .form-check-label') : null;
            var texto = etiqueta ? etiqueta.textContent : '';
            texto = texto.replace(/\s+/g, ' ').trim();
            // D.5 (reprueba Lote 1, pedido de Joel): varias etiquetas llevan una aclaración entre
            // paréntesis al final (ej. "Motivo de la diferencia (obligatorio si ...)") -- el
            // cartel repetía la etiqueta COMPLETA, mismo patrón que domicilioSinReferencia
            // (quitar solo el paréntesis final, no tocar el resto).
            texto = texto.replace(/\s*\([^()]*\)\s*$/, '').replace(/[:*]\s*$/, '').trim();
            if (texto) {
                return texto;
            }
            // Punto 5 (RF25, pedido de Joel): sin <label> (ej. un campo dentro de un form-inline
            // de una tabla), usa el aria-label o el placeholder como nombre del campo -- antes
            // siempre caía en el genérico "este campo". No cambia nada para un campo que ya tiene
            // <label> (ese caso ya devolvió arriba). aria-label primero: cuando el desarrollador
            // lo puso a propósito es el nombre real del campo, mientras que el placeholder puede
            // ser solo un valor de EJEMPLO (ej. "27,39 (%)" en tasaMaximaTea, corregido con un
            // aria-label propio -- RF25 §7, 12/10/2026).
            var alternativa = (campo.getAttribute('aria-label') || campo.getAttribute('placeholder') || '').trim();
            alternativa = alternativa.replace(/\s*\([^()]*\)\s*$/, '').replace(/[:*]\s*$/, '').trim();
            return alternativa || 'este campo';
        }

        /** D.5: "Motivo de la diferencia" -> "el motivo de la diferencia", para armar
         * "El [etiqueta] necesita...". */
        function etiquetaEnMinuscula(etiqueta) {
            return etiqueta.charAt(0).toLowerCase() + etiqueta.slice(1);
        }

        function mensajeParaCampo(campo) {
            var etiqueta = etiquetaDeCampo(campo);
            // B216 (15/11/2026): si alguien le puso al campo un mensaje propio
            // (setCustomValidity -- hoy, la confirmacion de contrasena que no coincide), ese
            // mensaje YA explica el problema mejor que cualquier frase armada aca. Sin esta
            // rama, un customError caia en el "Revise «campo»." genérico del final y el aviso
            // perdía justo lo que lo hacía útil.
            if (campo.validity.customError && campo.validationMessage) {
                return campo.validationMessage;
            }
            if (campo.validity.valueMissing) {
                return 'Falta completar «' + etiqueta + '».';
            }
            if (campo.validity.tooShort) {
                // D.5 (reprueba Lote 1, pedido de Joel): "El motivo de la diferencia necesita al
                // menos 10 caracteres (tiene 3)." -- antes repetía la etiqueta completa entre
                // guillemets.
                return 'El ' + etiquetaEnMinuscula(etiqueta) + ' necesita al menos ' + campo.minLength
                        + ' caracteres (tiene ' + campo.value.trim().length + ').';
            }
            if (campo.validity.typeMismatch || campo.validity.patternMismatch || campo.validity.rangeUnderflow
                    || campo.validity.rangeOverflow) {
                return 'Revise el dato de «' + etiqueta + '».';
            }
            return 'Revise «' + etiqueta + '».';
        }

        document.addEventListener('invalid', function (evento) {
            evento.preventDefault();
            var campo = evento.target;
            var formulario = campo.form;
            if (!formulario || formulariosConAvisoPendiente.has(formulario)) {
                return;
            }
            formulariosConAvisoPendiente.add(formulario);
            setTimeout(function () { formulariosConAvisoPendiente.delete(formulario); }, 0);
            Swal.fire({
                icon: 'warning',
                title: 'Falta un dato',
                text: mensajeParaCampo(campo),
                confirmButtonText: 'Entendido'
            }).then(function () {
                campo.focus();
            });
        }, true);
    })();

    // RF01-E (pedido de Joel): cualquier ícono con data-glosario-titulo="..." y
    // data-glosario-texto="..." abre, al hacer clic (no al pasar el mouse, para que funcione
    // igual en pantallas táctiles), un SweetAlert2 con la explicación completa del término --
    // mismo patrón delegado en document que data-confirmar, así funciona también con contenido
    // agregado después (p.ej. una fila nueva de listado-vivo.js). Reutilizable por cualquier
    // pantalla que use GlosarioFinanciero (hoy solo el cotizador).
    $(document).on('click', '[data-glosario-titulo]', function () {
        // Corrección de Joel: sin ícono (el de SweetAlert2 es celeste, no el azul de marca) y
        // botón "Entendido" en vez del "OK" por defecto, igual que el resto de los modales.
        Swal.fire({
            title: $(this).data('glosarioTitulo'),
            html: $(this).data('glosarioTexto'),
            confirmButtonText: 'Entendido'
        });
    });

    // Bloque 7/E10 (D-131, pedido de Joel): el resultado de una acción (registrar, guardar,
    // aprobar, desembolsar, cobrar, etc.) se muestra en un modal -- nunca un cartel que pasa
    // desapercibido o, peor, nada -- con el mismo estilo que los modales de confirmación de
    // arriba. Un solo mecanismo para todo el sistema: lee los <div> ocultos que dejó
    // plantilla/cabecera.jsp (mensajeExito/mensajeError, los mismos de siempre) y arma el modal
    // -- ninguna pantalla nueva necesita agregar nada para tenerlo.
    $(function () {
        // Título = hasta el primer punto de fin de frase; detalle = lo que sigue (si hay). La
        // mayoría de los mensajes del sistema ya vienen escritos como "Frase corta. Detalle." --
        // este mismo criterio separa bien un título claro sin tener que tocar cada controlador
        // uno por uno. "Punto de fin de frase" es un punto seguido de espacio y mayúscula --
        // nunca el punto de separador de miles ("6.000.000") ni el de una abreviatura como "Gs."
        // en medio de la oración (esos nunca van seguidos de espacio+mayúscula), así que un monto
        // en guaraníes nunca corta el título a la mitad.
        function dividirEnTituloYDetalle(texto) {
            var coincidencia = texto.match(/\.\s+[A-ZÁÉÍÓÚÑ]/);
            if (!coincidencia) {
                return {titulo: texto.replace(/\.\s*$/, ''), detalle: ''};
            }
            var indice = coincidencia.index;
            return {titulo: texto.substring(0, indice), detalle: texto.substring(indice + 1).trim()};
        }

        // B213: no se confia en navigator.clipboard -- no esta disponible en todo contexto
        // (depende del navegador y de como se sirva la aplicacion), y el sistema se instala en el
        // servidor de la institucion, no solo en esta maquina. Si no esta, o si falla, se copia
        // con un campo temporal (document.execCommand), que es el camino que funciona sin
        // depender de esa API. Nada de esto sale a internet: las dos cosas son del navegador.
        function copiarConCampoTemporal(texto) {
            var campo = document.createElement('textarea');
            campo.value = texto;
            campo.setAttribute('readonly', '');
            campo.style.position = 'fixed';
            campo.style.top = '-1000px';
            document.body.appendChild(campo);
            campo.select();
            var copiado = false;
            try {
                copiado = document.execCommand('copy');
            } catch (errorCopia) {
                copiado = false;
            }
            document.body.removeChild(campo);
            return copiado;
        }

        function copiarTexto(texto, alTerminar) {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(texto).then(function () {
                    alTerminar(true);
                }, function () {
                    alTerminar(copiarConCampoTemporal(texto));
                });
                return;
            }
            alTerminar(copiarConCampoTemporal(texto));
        }

        var datosExito = document.getElementById('datosMensajeExito');
        if (datosExito) {
            var particionExito = dividirEnTituloYDetalle(datosExito.textContent.trim());
            var htmlExito = particionExito.detalle ? '<div>' + particionExito.detalle + '</div>' : '';
            var datosImprimibles = document.getElementById('datosMensajeExitoImprimibles');
            if (datosImprimibles) {
                var botonesImprimibles = datosImprimibles.innerHTML.replace(/<a /g,
                        '<a class="btn btn-outline-primary btn-sm mr-2 mb-1" ');
                htmlExito += '<div class="mt-3">' + botonesImprimibles + '</div>';
            }
            // B213 (pedido de Joel, 15/11/2026): un dato que se muestra UNA sola vez -- hoy la
            // contrasena temporal de un alta o un restablecimiento (RF37) -- no puede perderse
            // por un clic afuera o un Esc, y no se deberia tener que transcribir a mano. Esto
            // vale SOLO para los mensajes que traen ese dato (datosMensajeExitoSensible, que lo
            // pone MensajesFlash.guardarExitoSensible): todos los demas avisos de exito del
            // sistema siguen abriendo y cerrandose igual que siempre.
            var datosSensible = document.getElementById('datosMensajeExitoSensible');
            var valorSensible = datosSensible ? datosSensible.textContent.trim() : '';
            if (valorSensible) {
                htmlExito += '<div class="mt-3">'
                        + '<button type="button" id="botonCopiarSensible" class="btn btn-outline-primary btn-sm">'
                        + '<i class="fas fa-copy mr-1" aria-hidden="true"></i>Copiar</button>'
                        + '<span id="avisoCopiarSensible" class="ml-2" role="status"></span>'
                        + '</div>';
            }
            var opcionesExito = {
                icon: 'success',
                title: particionExito.titulo || 'Listo',
                html: htmlExito || undefined,
                confirmButtonText: 'Aceptar'
            };
            if (valorSensible) {
                opcionesExito.allowOutsideClick = false;
                opcionesExito.allowEscapeKey = false;
                opcionesExito.didOpen = function () {
                    var botonCopiar = document.getElementById('botonCopiarSensible');
                    var avisoCopiar = document.getElementById('avisoCopiarSensible');
                    if (!botonCopiar) {
                        return;
                    }
                    botonCopiar.addEventListener('click', function () {
                        copiarTexto(valorSensible, function (copiado) {
                            if (!avisoCopiar) {
                                return;
                            }
                            avisoCopiar.className = copiado ? 'ml-2 text-success' : 'ml-2 text-danger';
                            avisoCopiar.textContent = copiado
                                    ? 'Copiado.'
                                    : 'No se pudo copiar: anotela antes de cerrar.';
                        });
                    });
                };
            }
            Swal.fire(opcionesExito);
        }

        // Corrección (pedido de Joel, 10/10/2026): el mensaje de error en sí nunca lleva HTML
        // (datosMensajeError.textContent ya lo descartaría igual), pero algunos mensajes
        // mencionan un dato que conviene que sea un enlace (ej. el código de una solicitud
        // abierta) -- plantilla/cabecera.jsp deja ese enlace armado aparte en
        // datosMensajeErrorEnlaces (mismo criterio que mensajeExitoImprimibles); acá se reemplaza
        // el texto visible por el enlace real, solo en el HTML que entra por .html (nunca en el
        // título, que SweetAlert2 siempre muestra como texto plano).
        function aplicarEnlacesError(html) {
            var datosEnlacesError = document.getElementById('datosMensajeErrorEnlaces');
            if (!datosEnlacesError || !html) {
                return html;
            }
            var enlacesError = datosEnlacesError.querySelectorAll('a');
            for (var indiceEnlace = 0; indiceEnlace < enlacesError.length; indiceEnlace++) {
                var enlaceError = enlacesError[indiceEnlace];
                var textoEnlace = enlaceError.getAttribute('data-texto');
                if (textoEnlace && html.indexOf(textoEnlace) !== -1) {
                    html = html.split(textoEnlace).join(
                            '<a href="' + enlaceError.getAttribute('href') + '">' + textoEnlace + '</a>');
                }
            }
            return html;
        }

        // Ajuste 2 (E4, Joel): algunos mensajes de error juntan varios puntos en una lista con
        // viñetas ("\n- punto" por línea, ver ServicioSolicitudes.enviarAAprobacion) -- el título
        // es la primera línea (sin ":" final) y el resto entra en el modal como lista. Los
        // mensajes normales (una sola línea) usan el mismo criterio de punto que el éxito.
        var datosError = document.getElementById('datosMensajeError');
        if (datosError) {
            var textoError = datosError.textContent.trim();
            var tituloError, htmlError;
            if (textoError.indexOf('\n') !== -1) {
                var lineasError = textoError.split('\n');
                tituloError = lineasError[0].replace(/:\s*$/, '');
                htmlError = '<ul class="text-left mb-0">';
                for (var indiceLinea = 1; indiceLinea < lineasError.length; indiceLinea++) {
                    var linea = lineasError[indiceLinea].replace(/^-\s*/, '').trim();
                    if (linea) {
                        htmlError += '<li>' + linea + '</li>';
                    }
                }
                htmlError += '</ul>';
            } else {
                var particionError = dividirEnTituloYDetalle(textoError);
                tituloError = particionError.titulo || 'Atención';
                htmlError = particionError.detalle ? '<div>' + particionError.detalle + '</div>' : '';
            }
            htmlError = aplicarEnlacesError(htmlError);
            Swal.fire({
                icon: 'error',
                title: tituloError,
                html: htmlError || undefined,
                confirmButtonText: 'Entendido'
            });
        }

        // RF02 (pedido de Joel): tercer nivel, amarillo -- la acción SÍ se completó (a diferencia
        // de mensajeError), pero hay algo para atender (ej. un cliente editado que ya no cumple
        // la política de crédito vigente). Mismo criterio de título/lista que mensajeError, nunca
        // el ícono rojo cuando lo que pasó es que se guardó bien.
        var datosAdvertencia = document.getElementById('datosMensajeAdvertencia');
        if (datosAdvertencia) {
            var textoAdvertencia = datosAdvertencia.textContent.trim();
            var tituloAdvertencia, htmlAdvertencia;
            if (textoAdvertencia.indexOf('\n') !== -1) {
                var lineasAdvertencia = textoAdvertencia.split('\n');
                tituloAdvertencia = lineasAdvertencia[0].replace(/:\s*$/, '');
                htmlAdvertencia = '<ul class="text-left mb-0">';
                for (var indiceLineaAdvertencia = 1; indiceLineaAdvertencia < lineasAdvertencia.length; indiceLineaAdvertencia++) {
                    var lineaAdvertencia = lineasAdvertencia[indiceLineaAdvertencia].replace(/^-\s*/, '').trim();
                    if (lineaAdvertencia) {
                        htmlAdvertencia += '<li>' + lineaAdvertencia + '</li>';
                    }
                }
                htmlAdvertencia += '</ul>';
            } else {
                var particionAdvertencia = dividirEnTituloYDetalle(textoAdvertencia);
                tituloAdvertencia = particionAdvertencia.titulo || 'Atención';
                htmlAdvertencia = particionAdvertencia.detalle ? '<div>' + particionAdvertencia.detalle + '</div>' : '';
            }
            Swal.fire({
                icon: 'warning',
                title: tituloAdvertencia,
                html: htmlAdvertencia || undefined,
                confirmButtonText: 'Entendido'
            });
        }
    });

    // Punto 3 (D-57): tras el redirect que sigue a varias acciones de la ficha del cliente, la
    // URL vuelve con un fragmento (ej. "#tabDocumentos") -- el navegador no activa solo la
    // pestaña de Bootstrap por eso (el fragmento no se manda al servidor). Este script corre en
    // TODAS las pantallas (incluidas las de perfiles sin acceso a esas pestañas, ver D-56), así
    // que a propósito no nombra acá ningún dato sensible: si hay un fragmento y coincide con una
    // pestaña de nav-pills presente en la página, se activa; si esa pestaña no está (porque el
    // perfil no debía verla), no hay ningún elemento que coincida y no pasa nada.
    $(function () {
        if (window.location.hash) {
            var pestanaDestino = $('a[data-toggle="pill"][href="' + window.location.hash + '"]');
            if (pestanaDestino.length) {
                pestanaDestino.tab('show');
            }
        }
    });

    // V3h (pedido de Joel): "Devolución del pagaré" (menú, módulo Cancelación) apunta a
    // /formalizacion#cierre-documental -- mismo /formalizacion que "Formalización" (módulo
    // Formalización), así que el menú marcaba ese módulo en vez del que realmente se usó para
    // llegar. Solo con el hash presente: abre "Cancelación" y marca "Devolución del pagaré" como
    // activa, cierra "Formalización" y le saca el activo. Sin el hash, no corre nada: todo queda
    // exactamente como antes de V3h.
    $(function () {
        if (window.location.hash !== '#cierre-documental'
                || window.location.pathname !== window.CONTEXTO_APP + '/formalizacion') {
            return;
        }
        var moduloCancelacion = $('.nav-sidebar a.nav-link-modulo:contains("Cancelación")').closest('.nav-item');
        var enlaceDevolucion = $('.nav-sidebar a.nav-link[href$="/formalizacion#cierre-documental"]');
        var moduloFormalizacion = $('.nav-sidebar a.nav-link-modulo:contains("Formalización")').closest('.nav-item');
        var enlaceFormalizacion = $('.nav-sidebar a.nav-link[href$="/formalizacion"]');

        moduloCancelacion.addClass('menu-open');
        enlaceDevolucion.addClass('active');
        moduloFormalizacion.removeClass('menu-open');
        enlaceFormalizacion.removeClass('active');

        // RF38/D4 (13/10/2026, pedido de Joel, causa real investigada): el salto nativo del
        // navegador al ancla #cierre-documental ocurría apenas el HTML terminaba de parsear --
        // ANTES de que terminen de cargar las fuentes web (Montserrat/Source Sans Pro, link en
        // cabecera.jsp) e imágenes de la página. Esas cargas tardías reacomodan el alto del
        // documento (reflow), corriendo la sección fuera de vista aunque el salto inicial haya
        // sido al lugar correcto -- por eso la página "abría arriba". El scrollIntoView de antes
        // corría en $(document).ready() (DOMContentLoaded), que también es ANTES de que terminen
        // de cargar fuentes/imágenes -- mismo problema, no lo resolvía. Repetirlo en "load" (que
        // sí espera a que todo haya terminado de cargar) corrige el reacomodo sin tocar nada más
        // del mecanismo que ya existe (scroll-margin-top de cabecera.jsp sigue siendo el que
        // evita que la barra superior fija tape la sección).
        window.addEventListener('load', function () {
            var destino = document.getElementById('cierre-documental');
            if (destino) {
                destino.scrollIntoView({behavior: 'smooth'});
            }
        });
    });

    // RF38/D3 (13/10/2026, pedido de Joel): mismo problema que V3h, de arriba, pero para
    // "Cancelación anticipada" -- apunta a /cobranza?origen=cancelacion-anticipada, mismo
    // /cobranza que "Cobranza" (módulo Cobranza y mora), así que el menú marcaba ese módulo en
    // vez de "Cancelación". Solo con el parámetro presente: abre "Cancelación" y marca
    // "Cancelación anticipada" como activa, cierra "Cobranza y mora" y le saca el activo. Sin el
    // parámetro (entrada por "Cobranza"), no corre nada: la pantalla queda exactamente igual que
    // siempre. Sin scroll -- a diferencia de "Devolución del pagaré", acá no hay ninguna sección
    // más abajo a la que bajar, el aviso ya queda arriba de todo (busqueda.jsp).
    //
    // B201, tercera vuelta (15/11/2026, detalle que Joel encontro probando): este bloque comparaba
    // la ruta con IGUALDAD exacta contra "/cobranza", asi que dentro de la FICHA -- donde la ruta
    // es "/cobranza/8" -- no corria y el menu volvia a resaltar "Cobranza". El resto de B201 ya
    // hacia viajar el origen hasta la ficha (el aviso y el "Volver" lo usan); lo unico que faltaba
    // era el resaltado. Ahora el criterio es el MISMO que usa el servidor en menu.jsp
    // (coincideRuta: ruta exacta o primer segmento), para que las dos capas no puedan discrepar.
    $(function () {
        var rutaCobranza = window.CONTEXTO_APP + '/cobranza';
        var enCobranza = window.location.pathname === rutaCobranza
                || window.location.pathname.indexOf(rutaCobranza + '/') === 0;
        if (!enCobranza || window.location.search.indexOf('origen=cancelacion-anticipada') === -1) {
            return;
        }
        var moduloCancelacion = $('.nav-sidebar a.nav-link-modulo:contains("Cancelación")').closest('.nav-item');
        var enlaceCancelacionAnticipada = $('.nav-sidebar a.nav-link[href$="/cobranza?origen=cancelacion-anticipada"]');
        var moduloCobranzaMora = $('.nav-sidebar a.nav-link-modulo:contains("Cobranza y mora")').closest('.nav-item');
        var enlaceCobranza = $('.nav-sidebar a.nav-link[href$="/cobranza"]');

        moduloCancelacion.addClass('menu-open');
        enlaceCancelacionAnticipada.addClass('active');
        moduloCobranzaMora.removeClass('menu-open');
        enlaceCobranza.removeClass('active');
    });
</script>
</body>
</html>
