/**
 * Reutilizable: cargado una sola vez en plantilla/pie.jsp (todas las
 * pantallas con sesión; nunca en acceso.jsp, el login). 2 minutos antes de
 * que venza la sesión por inactividad (window.MINUTOS_INACTIVIDAD, seteado
 * desde la JSP a partir de seguridad.minutos_inactividad) muestra un
 * SweetAlert con cuenta regresiva y dos botones: "Seguir conectado" (llama
 * a GET /sesion/renovar sin recargar la página, así no se pierde nada de
 * lo que el usuario ya escribió) o "Salir" (cierra sesión de verdad, vía
 * /salir). Si la cuenta regresiva llega a 0 sin respuesta, avisa que la
 * sesión venció y manda al login.
 *
 * window.reiniciarTemporizadorSesion queda expuesta para que cualquier
 * fetch/AJAX del sistema (hoy: campo-guaranies.js) reinicie el temporizador
 * después de cada petición al servidor, igual que ya lo hace cada carga de
 * página nueva.
 */
(function () {
    'use strict';

    var MS_POR_MINUTO = 60 * 1000;
    var AVISO_ANTES_MS = 2 * MS_POR_MINUTO;

    var temporizadorAviso = null;
    var intervaloCuentaRegresiva = null;
    var alertaAbierta = false;

    function contextoApp() {
        return window.CONTEXTO_APP || '';
    }

    function duracionSesionMs() {
        var minutos = window.MINUTOS_INACTIVIDAD;
        return (typeof minutos === 'number' && minutos > 0 ? minutos : 30) * MS_POR_MINUTO;
    }

    function formatearMmSs(ms) {
        var segundosTotales = Math.max(0, Math.ceil(ms / 1000));
        var minutos = Math.floor(segundosTotales / 60);
        var segundos = segundosTotales % 60;
        return (minutos < 10 ? '0' : '') + minutos + ':' + (segundos < 10 ? '0' : '') + segundos;
    }

    function irA(ruta) {
        window.location.href = contextoApp() + ruta;
    }

    function avisarSesionVencida() {
        if (typeof Swal === 'undefined') {
            irA('/acceso');
            return;
        }
        Swal.fire({
            title: 'Sesión vencida',
            text: 'Su sesión venció por inactividad. Vuelva a ingresar.',
            icon: 'warning',
            allowOutsideClick: false,
            allowEscapeKey: false,
            confirmButtonText: 'Entendido'
        }).then(function () {
            irA('/acceso');
        });
    }

    function renovarSesion() {
        fetch(contextoApp() + '/sesion/renovar', {headers: {'X-Requested-With': 'XMLHttpRequest'}})
            .then(function (respuesta) {
                if (respuesta.status === 401) {
                    avisarSesionVencida();
                    return;
                }
                reiniciarTemporizador();
            })
            .catch(function () {
                // Sin conexion momentanea: no se asume vencida por un solo intento fallido,
                // se vuelve a armar el temporizador para no dejar al usuario sin ningun aviso.
                reiniciarTemporizador();
            });
    }

    function mostrarAvisoDeVencimiento() {
        if (typeof Swal === 'undefined') {
            return;
        }
        alertaAbierta = true;
        var restanteMs = AVISO_ANTES_MS;

        Swal.fire({
            title: 'Su sesión está por vencer por inactividad',
            html: '<div id="contadorSesion" style="font-size:1.5rem;"></div>',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Seguir conectado',
            cancelButtonText: 'Salir',
            allowOutsideClick: false,
            allowEscapeKey: false,
            didOpen: function () {
                var elementoContador = document.getElementById('contadorSesion');
                elementoContador.textContent = formatearMmSs(restanteMs);
                intervaloCuentaRegresiva = setInterval(function () {
                    restanteMs -= 1000;
                    if (restanteMs <= 0) {
                        Swal.close();
                        return;
                    }
                    elementoContador.textContent = formatearMmSs(restanteMs);
                }, 1000);
            },
            willClose: function () {
                if (intervaloCuentaRegresiva) {
                    clearInterval(intervaloCuentaRegresiva);
                    intervaloCuentaRegresiva = null;
                }
            }
        }).then(function (resultado) {
            alertaAbierta = false;
            if (resultado.isConfirmed) {
                renovarSesion();
            } else if (restanteMs <= 0) {
                // Se cerro sola al llegar a 0, sin que el usuario respondiera.
                avisarSesionVencida();
            } else {
                // "Salir": cierra sesion de verdad (auditado), no solo navega al login.
                irA('/salir');
            }
        });
    }

    function reiniciarTemporizador() {
        if (temporizadorAviso) {
            clearTimeout(temporizadorAviso);
        }
        if (alertaAbierta) {
            // No pisar un aviso ya abierto (ej. si otra pestaña/peticion dispara un reinicio).
            return;
        }
        var esperaHastaElAviso = duracionSesionMs() - AVISO_ANTES_MS;
        temporizadorAviso = setTimeout(mostrarAvisoDeVencimiento, Math.max(0, esperaHastaElAviso));
    }

    window.reiniciarTemporizadorSesion = reiniciarTemporizador;
    // Reutilizable por cualquier otro fetch/AJAX del sistema (hoy: campo-guaranies.js) ante
    // un 401: mismo aviso y mismo destino que el que dispara esta propia cuenta regresiva.
    window.avisarSesionVencida = avisarSesionVencida;

    document.addEventListener('DOMContentLoaded', reiniciarTemporizador);
})();
