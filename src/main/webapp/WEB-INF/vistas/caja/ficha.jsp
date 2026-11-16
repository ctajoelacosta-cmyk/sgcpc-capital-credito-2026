<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SesionCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaMovimientoCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%-- B188 (15/11/2026): import propio -- cabecera.jsp trae FormateadorNumeros y FormateadorFechas,
     pero no FormateadorTexto (la lección de B169: no hay forma de saberlo de memoria, lo dice
     mvn test con el número de línea). --%>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    SesionCaja sesionCajaFicha = (SesionCaja) request.getAttribute("sesionAbierta");
    BigDecimal fondoSugeridoFicha = (BigDecimal) request.getAttribute("fondoSugerido");
    Boolean esDeHoyFicha = (Boolean) request.getAttribute("esDeHoy");
    String mensajeCajaOtroDiaFicha = (String) request.getAttribute("mensajeCajaOtroDia");
    boolean puedeVerComprobantesFicha = Boolean.TRUE.equals(request.getAttribute("puedeVerComprobantes"));
    BigDecimal saldoTeoricoFicha = (BigDecimal) request.getAttribute("saldoTeorico");
    @SuppressWarnings("unchecked")
    List<FilaMovimientoCaja> movimientosFicha = (List<FilaMovimientoCaja>) request.getAttribute("movimientos");
    @SuppressWarnings("unchecked")
    List<SesionCaja> sesionesConDiferenciaFicha =
            (List<SesionCaja>) request.getAttribute("sesionesConDiferenciaSinRevisar");
    @SuppressWarnings("unchecked")
    Map<Long, String> nombreCajeroPorSesionFicha = (Map<Long, String>) request.getAttribute("nombreCajeroPorSesion");
    @SuppressWarnings("unchecked")
    List<SesionCaja> sesionesCerradasRecientesFicha = (List<SesionCaja>) request.getAttribute("sesionesCerradasRecientes");
    String filtroDesdeFicha = (String) request.getAttribute("filtroDesde");
    String filtroHastaFicha = (String) request.getAttribute("filtroHasta");
    // A3b (#1, pedido de Joel): si el cierre se rechaza (ej. falta el motivo), el servidor
    // devuelve lo que la cajera tecleó -- mismo patrón que guardarValoresCobro en la ficha de
    // cobranza (D-89, PRG), ahora también para "Cerrar caja".
    @SuppressWarnings("unchecked")
    Map<String, String> valoresFormularioCierreFicha = (Map<String, String>) request.getAttribute("valoresFormulario");
    String saldoFisicoPrevioFicha = valoresFormularioCierreFicha != null ? valoresFormularioCierreFicha.get("saldoFisico") : null;
    String motivoDiferenciaPrevioFicha = valoresFormularioCierreFicha != null ? valoresFormularioCierreFicha.get("motivoDiferencia") : null;
    Usuario usuarioCaja = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    // D7 (pedido de Joel): el administrador también ve el formulario de entrega de efectivo.
    boolean esGerenteCaja = usuarioCaja != null
            && (usuarioCaja.getPerfilUsuario() == PerfilUsuario.GERENTE_ADMINISTRADOR
                    || usuarioCaja.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR);
    boolean sesionEsDeHoyFicha = esDeHoyFicha != null && esDeHoyFicha;
    // B191 (15/11/2026): contador y paginacion de los dos listados de esta pantalla. Los totales y
    // las URL los deja ControladorCajaServlet.mostrarFicha; aca solo se dibujan. Los respaldos (?:)
    // son para esta JSP contra un .class viejo, antes del Clean and Build: ahi sale como salia
    // antes, sin contador ni paginacion, en vez de romperse (punto 11b).
    Integer totalMovimientosFicha = (Integer) request.getAttribute("totalMovimientos");
    Integer paginaMovimientosFicha = (Integer) request.getAttribute("paginaMovimientos");
    Integer totalSesionesCerradasFicha = (Integer) request.getAttribute("totalSesionesCerradas");
    if (totalMovimientosFicha == null) {
        totalMovimientosFicha = movimientosFicha == null ? 0 : movimientosFicha.size();
    }
    if (paginaMovimientosFicha == null) {
        paginaMovimientosFicha = 1;
    }
    if (totalSesionesCerradasFicha == null) {
        totalSesionesCerradasFicha = sesionesCerradasRecientesFicha == null ? 0 : sesionesCerradasRecientesFicha.size();
    }
%>
<h1>Caja</h1>

<% if (sesionCajaFicha == null) { %>
<div class="card">
    <div class="card-header"><h5 class="m-0">Apertura de caja</h5></div>
    <div class="card-body">
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7): aclara que suma solo los
             préstamos con checklist completo (mismo criterio que /desembolso, D-74/D-91) -- antes
             decía "pendientes de desembolso", sin esa aclaración. --%>
        <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso H, ⓘ 3): el ícono va
             DESPUÉS del monto a propósito -- pruebas/humo_http.sh:extraer_monto_etiqueta busca el
             primer número después de "Fondo sugerido" con un grep tolerante a HTML en el medio;
             poner el ícono antes del monto (aunque su texto no tenga dígitos hoy) arriesgaría ese
             patrón si el texto cambia en el futuro. --%>
        <p class="text-muted">Fondo sugerido (suma de los préstamos listos para desembolsar, con checklist completo): <strong><%= FormateadorNumeros.comoGuaranies(fondoSugeridoFicha) %></strong>
            <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
               data-glosario-titulo="Fondo sugerido"
               data-glosario-texto="Suma el monto aprobado de los préstamos que ya tienen el checklist de firmados completo (listos para desembolsar hoy). No incluye préstamos pendientes de desembolso cuyo checklist todavía está incompleto, porque todavía no se les puede entregar el efectivo."></i></p>
        <%-- Ajuste de Joel (E6, prueba manual): solo la cajera abre la caja (Doc. 00 §3.1, PO-06);
             al gerente antes se le mostraba el formulario aunque el servidor ya lo rechazaba (403). --%>
        <% if (esGerenteCaja) { %>
        <p class="text-muted mb-0">No hay ninguna caja abierta. Solo la cajera puede abrirla.</p>
        <% } else { %>
        <form method="post" action="${pageContext.request.contextPath}/caja/abrir" class="form-inline"
              id="formAbrirCaja" data-confirmar-foco="cancelar" data-confirmar-boton="Sí, abrir caja">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-group mr-2">
                <label class="mr-2">Fondo inicial</label>
                <%-- A3a (#10, pedido de Joel): precargado con el fondo sugerido (de arriba),
                     editable -- antes había que volver a escribirlo a mano. --%>
                <input type="text" name="saldoInicial" id="campoSaldoInicialAbrir" class="form-control campo-guaranies"
                       value="<%= fondoSugeridoFicha == null ? "" : fondoSugeridoFicha.toBigInteger() %>" required>
            </div>
            <button type="submit" class="btn btn-primary">Abrir caja</button>
        </form>
        <%-- RF19 (pedido de Joel, 11/10/2026): confirmación con el fondo tecleado, mismo patrón
             que "Entregar efectivo" (más abajo) -- el monto puede cambiar respecto del sugerido. --%>
        <script>
            (function () {
                var formularioAbrirCaja = document.getElementById('formAbrirCaja');
                var campoSaldoInicialAbrir = document.getElementById('campoSaldoInicialAbrir');
                if (!formularioAbrirCaja || !campoSaldoInicialAbrir) {
                    return;
                }
                function formatearGsAbrirCaja(digitos) {
                    var texto = digitos || '0';
                    var conPuntos = '';
                    for (var i = 0; i < texto.length; i++) {
                        if (i > 0 && (texto.length - i) % 3 === 0) {
                            conPuntos += '.';
                        }
                        conPuntos += texto.charAt(i);
                    }
                    return conPuntos + ' Gs.';
                }
                function actualizarConfirmacionAbrirCaja() {
                    var digitos = (campoSaldoInicialAbrir.value || '').replace(/\D/g, '');
                    formularioAbrirCaja.setAttribute('data-confirmar',
                            '¿Abrir la caja con un fondo inicial de ' + formatearGsAbrirCaja(digitos) + '?');
                }
                campoSaldoInicialAbrir.addEventListener('input', actualizarConfirmacionAbrirCaja);
                actualizarConfirmacionAbrirCaja();
            })();
        </script>
        <% } %>
    </div>
</div>
<% } else { %>

<% if (!sesionEsDeHoyFicha) { %>
<%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto y color (alert-warning, no
     alert-danger -- A.8, bloqueo de tiempo, no error) que Cobranza/Desembolso. --%>
<div class="alert alert-warning"><%= mensajeCajaOtroDiaFicha %></div>
<% } %>

<div class="card">
    <div class="card-header"><h5 class="m-0">Sesión abierta</h5></div>
    <div class="card-body">
        <table class="table table-sm">
            <tr><th>Apertura</th><td><%= FormateadorFechas.comoDdMmAaaaHhMm(sesionCajaFicha.getFechaHoraApertura()) %></td></tr>
            <tr><th>Fondo inicial</th><td><%= FormateadorNumeros.comoGuaranies(sesionCajaFicha.getSaldoInicial()) %></td></tr>
            <%-- B107 (B86, tramo 3): este saldo es EFECTIVO -- fondo inicial más los movimientos de
                 caja. Lo que el Panel muestra como cobrado es lo APLICADO A LA DEUDA, y los dos
                 pueden diferir en unos guaraníes. Solo la etiqueta y la nota: el cálculo no se
                 toca. --%>
            <tr><th>Saldo teórico actual (efectivo)</th><td><strong><%= FormateadorNumeros.comoGuaranies(saldoTeoricoFicha) %></strong></td></tr>
        </table>
        <%-- La aclaración vive SOLO acá, que es la pantalla donde la cajera cuenta su efectivo y
             donde surgió la confusión (01/11/2026). Dos oraciones, como todo texto del sistema
             (B42): la primera dice qué es este número, la segunda por qué puede no coincidir con el
             Panel. No se repite en el Panel: ahí las etiquetas ya dicen "aplicado a la deuda". --%>
        <p class="text-muted small mb-0">Este saldo es el efectivo. Lo cobrado que figura en el
            Panel puede ser unos guaraníes mayor, porque incluye el redondeo a favor del cliente.</p>
    </div>
</div>

<div class="card mt-3">
    <%-- B191 (15/11/2026): el contador cuenta los movimientos REALES de la sesion; el fondo
         inicial no es uno (vive en sesiones_caja.saldo_inicial, B.14) y por eso no se suma. --%>
    <div class="card-header"><h5 class="m-0">Movimientos (<%= FormateadorNumeros.comoCantidad(totalMovimientosFicha) %>)</h5></div>
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead><tr><th>Fecha/hora</th><th>Tipo</th><th class="cc-monto">Monto</th><th>Observación</th><th>Comprobante</th><th></th></tr></thead>
                <tbody>
                <%-- B.14 (consolidación, pedido de Joel): el fondo inicial no es una fila de
                     movimientos_caja (vive en sesiones_caja.saldo_inicial), así que antes no
                     aparecía en esta tabla -- se agrega acá, siempre primera, como dato fijo de
                     la sesión abierta. --%>
                <%-- B191: la fila del fondo inicial va SOLO en la pagina 1. Sin esa condicion se
                     repetiria en cada pagina, y seria la unica fila que aparece dos veces. --%>
                <% if (sesionCajaFicha != null && paginaMovimientosFicha == 1) { %>
                <tr>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(sesionCajaFicha.getFechaHoraApertura()) %></td>
                    <td>Apertura (fondo inicial)</td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(sesionCajaFicha.getSaldoInicial()) %></td>
                    <td></td>
                    <td>-</td>
                    <td></td>
                </tr>
                <% } %>
                <% if (movimientosFicha != null) { for (FilaMovimientoCaja movimiento : movimientosFicha) { %>
                <tr>
                    <td><%= movimiento.getFechaHora() %></td>
                    <td><%= movimiento.getTipoEtiqueta() %></td>
                    <td class="cc-monto"><%= movimiento.getMontoConSigno() %></td>
                    <td><%= movimiento.getObservacion() == null ? "" : movimiento.getObservacion() %></td>
                    <td><%= movimiento.getNumeroComprobante() == null ? "-" : movimiento.getNumeroComprobante() %></td>
                    <td>
                        <% if (movimiento.getUrlReimprimir() != null && puedeVerComprobantesFicha) { %>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}<%= movimiento.getUrlReimprimir() %>">Reimprimir</a>
                        <% } %>
                    </td>
                </tr>
                <% } } %>
                <% if (sesionCajaFicha != null && (movimientosFicha == null || movimientosFicha.isEmpty())) { %>
                <%-- D.4 (reprueba Lote 1, pedido de Joel): con la fila de apertura (B.14) de
                     arriba, "Todavía no hay movimientos" quedaba confuso -- ya hay una fila. --%>
                <tr><td colspan="6" class="text-muted">Sin movimientos desde la apertura.</td></tr>
                <% } else if (sesionCajaFicha == null && (movimientosFicha == null || movimientosFicha.isEmpty())) { %>
                <tr><td colspan="6" class="text-muted">Todavía no hay movimientos.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <%-- B191: la paginacion va con <jsp:include> (dinamico) porque esta pantalla tiene DOS, y
             con el include estatico la segunda copia redeclararia las variables del fragmento y no
             compilaria. paginacionConListadoVivo=false: /caja no tiene "listado vivo", sus enlaces
             navegan de verdad, igual que el filtro de fechas de mas abajo. --%>
        <%
            request.setAttribute("pagina", request.getAttribute("paginaMovimientos"));
            request.setAttribute("totalPaginas", request.getAttribute("totalPaginasMovimientos"));
            request.setAttribute("paginacionUrlBase", request.getAttribute("urlPaginacionMovimientos"));
            request.setAttribute("paginacionParamPagina", "paginaMovimientos");
            request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
        %>
        <jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />
    </div>
</div>

<% if (sesionEsDeHoyFicha && esGerenteCaja) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Entregar efectivo a la caja</h5></div>
    <div class="card-body">
        <%-- A3c (pedido de Joel): foco por defecto en "Cancelar" -- es una confirmación
             irreversible (entrega de efectivo a la caja). --%>
        <form method="post" action="${pageContext.request.contextPath}/caja/entregar" class="form-inline"
              id="formEntregarEfectivoCaja" data-confirmar-foco="cancelar" data-confirmar-boton="Sí, entregar">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-group mr-2">
                <label class="mr-2">Monto</label>
                <input type="text" name="monto" id="campoMontoEntregarCaja" class="form-control campo-guaranies mr-2" required>
            </div>
            <div class="form-group mr-2">
                <input type="text" name="observacion" class="form-control" placeholder="Observación (opcional)" style="width: 260px;">
            </div>
            <button type="submit" class="btn btn-primary">Entregar</button>
        </form>
        <%-- A3a (#8, pedido de Joel): "Entregar efectivo" no pedía confirmación, a diferencia de
             "Cerrar caja" -- el monto solo se conoce cuando el gerente lo escribe, así que el
             texto de la confirmación (que lee pie.jsp al enviar el formulario, D-57) se arma en
             vivo con el mismo formato de siempre (puntos de miles + "Gs."). No depende del orden
             de los listeners de campo-guaranies.js: vuelve a calcular los puntos por su cuenta a
             partir de los dígitos tecleados. --%>
        <script>
            (function () {
                var formularioEntregarCaja = document.getElementById('formEntregarEfectivoCaja');
                var campoMontoEntregarCaja = document.getElementById('campoMontoEntregarCaja');
                if (!formularioEntregarCaja || !campoMontoEntregarCaja) {
                    return;
                }
                function formatearGsEntregarCaja(digitos) {
                    var texto = digitos || '0';
                    var conPuntos = '';
                    for (var i = 0; i < texto.length; i++) {
                        if (i > 0 && (texto.length - i) % 3 === 0) {
                            conPuntos += '.';
                        }
                        conPuntos += texto.charAt(i);
                    }
                    return conPuntos + ' Gs.';
                }
                function actualizarConfirmacionEntregarCaja() {
                    var digitos = (campoMontoEntregarCaja.value || '').replace(/\D/g, '');
                    formularioEntregarCaja.setAttribute('data-confirmar',
                            '¿Entregar ' + formatearGsEntregarCaja(digitos) + ' a la caja?');
                }
                campoMontoEntregarCaja.addEventListener('input', actualizarConfirmacionEntregarCaja);
                actualizarConfirmacionEntregarCaja();
            })();
        </script>
    </div>
</div>
<% } %>

<%-- Ajuste de Joel (E6, prueba manual): solo la cajera cierra la caja (Doc. 00 §3.1, PO-12); antes
     se mostraba el formulario también al gerente aunque el servidor ya lo rechazaba (403). --%>
<% if (!esGerenteCaja) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Cierre y rendición</h5></div>
    <div class="card-body">
        <%-- A3c (pedido de Joel): foco por defecto en "Cancelar" -- es una confirmación
             irreversible (una caja cerrada no se puede modificar). --%>
        <form method="post" action="${pageContext.request.contextPath}/caja/cerrar" id="formCerrarCaja"
              data-saldo-teorico="<%= saldoTeoricoFicha == null ? "0" : saldoTeoricoFicha.toBigInteger() %>"
              <%-- B187 (15/11/2026): el título nombra el DÍA de la caja que se cierra. El cuerpo ya
                   estaba bien (el script de abajo lo reescribe con el efectivo contado y la
                   diferencia, con "falta"/"sobra"), pero con una caja de OTRO día abierta --el caso
                   que más importa, porque es el que bloquea el circuito-- el cartel no decía cuál
                   se estaba cerrando. Es el criterio de B179: la confirmación nombra la operación.
                   Va en el TÍTULO y no en el cuerpo a propósito: el cuerpo lo pisa el script en cada
                   tecla (setAttribute('data-confirmar', ...)), el título no lo toca nadie. --%>
              data-confirmar-titulo="¿Cerrar la caja del <%= FormateadorFechas.comoDdMmAaaa(sesionCajaFicha.getFechaHoraApertura().toLocalDate()) %> con el efectivo contado?"
              data-confirmar="Una caja cerrada no se puede modificar."
              data-confirmar-foco="cancelar" data-confirmar-boton="Sí, cerrar caja">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-row align-items-end">
                <div class="form-group col-md-4">
                    <label>Efectivo contado</label>
                    <input type="text" name="saldoFisico" id="campoSaldoFisicoCierre" class="form-control campo-guaranies"
                           value="<%= saldoFisicoPrevioFicha != null ? saldoFisicoPrevioFicha : "" %>" required>
                    <%-- A3b (#1, pedido de Joel): diferencia en vivo contra el saldo teórico ya
                         calculado por el servidor (data-saldo-teorico arriba) -- JS no recalcula
                         nada, solo compara. Mismo patrón visual que "Vuelto" en cobranza-ficha.js.
                         A3c: tamaño normal y negrita (antes se veía chica, "form-text" de
                         Bootstrap la achica y la pone en gris). --%>
                    <div id="textoDiferenciaCierre" class="font-weight-bold mt-1"></div>
                </div>
                <div class="form-group col-md-6">
                    <label>Motivo de la diferencia (obligatorio si el efectivo contado no coincide con el saldo teórico)</label>
                    <%-- A.9 (consolidación, pedido de Joel): minlength se activa por JS junto con
                         "required" (más abajo) -- solo cuando hay diferencia y el motivo es
                         obligatorio. Antes la pantalla solo controlaba que no estuviera vacío (el
                         mínimo de caracteres solo lo exigía el servidor); el contador en vivo de
                         abajo ya avisaba, pero no bloqueaba el envío. El popup nativo que dispara
                         minlength se reemplaza por el cartel propio del sistema (pie.jsp, "invalid"). --%>
                    <input type="text" name="motivoDiferencia" id="campoMotivoDiferenciaCierre" class="form-control"
                           value="<%= motivoDiferenciaPrevioFicha != null ? motivoDiferenciaPrevioFicha : "" %>">
                    <%-- RF19 (pedido de Joel, 11/10/2026): contador en vivo -- mínimo 10
                         caracteres reales cuando hay diferencia (el servidor vuelve a exigirlo
                         igual, este aviso solo informa mientras escribe). --%>
                    <small id="avisoMotivoCierre" class="form-text"></small>
                </div>
                <div class="form-group col-md-2">
                    <button type="submit" class="btn btn-outline-danger">Cerrar caja</button>
                </div>
            </div>
        </form>
        <script>
            (function () {
                var formularioCierre = document.getElementById('formCerrarCaja');
                var campoSaldoFisicoCierre = document.getElementById('campoSaldoFisicoCierre');
                var campoMotivoCierre = document.getElementById('campoMotivoDiferenciaCierre');
                var textoDiferenciaCierre = document.getElementById('textoDiferenciaCierre');
                var avisoMotivoCierre = document.getElementById('avisoMotivoCierre');
                if (!formularioCierre || !campoSaldoFisicoCierre || !textoDiferenciaCierre) {
                    return;
                }
                var saldoTeoricoCierre = parseInt(formularioCierre.getAttribute('data-saldo-teorico'), 10) || 0;
                // RF19 (pedido de Joel, 11/10/2026): mismo mínimo que
                // ServicioCaja.MOTIVO_DIFERENCIA_LARGO_MINIMO. A.9 (consolidación, pedido de Joel):
                // ya no solo informa -- también se activa como minlength real del campo (más
                // abajo), así que bloquea el envío en pantalla, no solo en el servidor.
                var MOTIVO_LARGO_MINIMO_CIERRE = 10;
                var motivoRequeridoCierre = false;

                function actualizarAvisoMotivoCierre() {
                    if (!avisoMotivoCierre) {
                        return;
                    }
                    if (!motivoRequeridoCierre) {
                        avisoMotivoCierre.textContent = '';
                        avisoMotivoCierre.className = 'form-text';
                        return;
                    }
                    var largoReal = (campoMotivoCierre.value || '').trim().length;
                    if (largoReal >= MOTIVO_LARGO_MINIMO_CIERRE) {
                        avisoMotivoCierre.textContent = '✓ ' + largoReal + ' caracteres';
                        avisoMotivoCierre.className = 'form-text text-success';
                    } else {
                        avisoMotivoCierre.textContent = largoReal + ' de ' + MOTIVO_LARGO_MINIMO_CIERRE
                                + ' caracteres mínimos';
                        avisoMotivoCierre.className = 'form-text text-danger';
                    }
                }
                // A3c (pedido de Joel): el text-warning de Bootstrap (amarillo claro) casi no se
                // lee sobre fondo blanco -- ámbar oscuro a mano, vía estilo en línea (no hay
                // hoja de estilos propia en el proyecto, REGLAS_DESARROLLO.md §15, así que no hay dónde
                // definir una clase nueva).
                var AMBAR_OSCURO_CIERRE = '#a15c00';

                function formatearGsCierre(numero) {
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

                function leerSaldoFisicoCierre() {
                    var digitos = (campoSaldoFisicoCierre.value || '').replace(/\D/g, '');
                    return digitos ? parseInt(digitos, 10) : null;
                }

                function actualizarDiferenciaCierre() {
                    textoDiferenciaCierre.classList.remove('text-success', 'text-danger');
                    textoDiferenciaCierre.style.color = '';
                    var saldoFisicoCierre = leerSaldoFisicoCierre();
                    if (saldoFisicoCierre === null) {
                        textoDiferenciaCierre.textContent = 'Saldo teórico: ' + formatearGsCierre(saldoTeoricoCierre);
                        if (campoMotivoCierre) {
                            campoMotivoCierre.required = false;
                            campoMotivoCierre.minLength = 0;
                        }
                        motivoRequeridoCierre = false;
                        actualizarAvisoMotivoCierre();
                        return;
                    }
                    var diferenciaCierre = saldoFisicoCierre - saldoTeoricoCierre;
                    motivoRequeridoCierre = diferenciaCierre !== 0;
                    var textoConfirmarCierre;
                    if (diferenciaCierre === 0) {
                        textoDiferenciaCierre.textContent = 'Saldo teórico: ' + formatearGsCierre(saldoTeoricoCierre)
                                + ' · Coincide';
                        textoDiferenciaCierre.classList.add('text-success');
                        if (campoMotivoCierre) {
                            campoMotivoCierre.required = false;
                            campoMotivoCierre.minLength = 0;
                        }
                        textoConfirmarCierre = '¿Cerrar caja con ' + formatearGsCierre(saldoFisicoCierre)
                                + ' contados? Coincide con el saldo teórico.';
                    } else if (diferenciaCierre < 0) {
                        textoDiferenciaCierre.textContent = 'Saldo teórico: ' + formatearGsCierre(saldoTeoricoCierre)
                                + ' · Diferencia: Falta ' + formatearGsCierre(Math.abs(diferenciaCierre));
                        textoDiferenciaCierre.classList.add('text-danger');
                        if (campoMotivoCierre) {
                            campoMotivoCierre.required = true;
                            campoMotivoCierre.minLength = MOTIVO_LARGO_MINIMO_CIERRE;
                        }
                        // A3c (pedido de Joel): formatearGsCierre ya termina en "Gs." -- agregar
                        // otro "." duplicaba el punto ("falta 50.000 Gs..").
                        textoConfirmarCierre = '¿Cerrar caja con ' + formatearGsCierre(saldoFisicoCierre)
                                + ' contados? Diferencia: falta ' + formatearGsCierre(Math.abs(diferenciaCierre));
                    } else {
                        textoDiferenciaCierre.textContent = 'Saldo teórico: ' + formatearGsCierre(saldoTeoricoCierre)
                                + ' · Diferencia: Sobra ' + formatearGsCierre(diferenciaCierre);
                        // A3c (pedido de Joel): ámbar oscuro legible en vez de text-warning.
                        textoDiferenciaCierre.style.color = AMBAR_OSCURO_CIERRE;
                        if (campoMotivoCierre) {
                            campoMotivoCierre.required = true;
                            campoMotivoCierre.minLength = MOTIVO_LARGO_MINIMO_CIERRE;
                        }
                        textoConfirmarCierre = '¿Cerrar caja con ' + formatearGsCierre(saldoFisicoCierre)
                                + ' contados? Diferencia: sobra ' + formatearGsCierre(diferenciaCierre);
                    }
                    formularioCierre.setAttribute('data-confirmar', textoConfirmarCierre);
                    actualizarAvisoMotivoCierre();
                }

                campoSaldoFisicoCierre.addEventListener('input', actualizarDiferenciaCierre);
                if (campoMotivoCierre) {
                    campoMotivoCierre.addEventListener('input', actualizarAvisoMotivoCierre);
                }
                actualizarDiferenciaCierre();
            })();
        </script>
    </div>
</div>
<% } %>
<% } %>

<% if (esGerenteCaja && sesionesConDiferenciaFicha != null && !sesionesConDiferenciaFicha.isEmpty()) { %>
<div class="card mt-3">
    <%-- B63 (28/10/2026, autorizado por Joel): el id es el destino del enlace "Ir a Caja" de la
         tarjeta nueva "Diferencias de caja por revisar (N)" del panel del gerente
         (/caja#diferencias-arqueo) -- mismo patrón que #cierre-documental en
         formalizacion/_resultados.jsp. Solo agrega el ancla: la sección, su condición
         (esGerenteCaja) y su contenido no cambian. --%>
    <div class="card-header"><h5 class="m-0" id="diferencias-arqueo">Diferencias de arqueo sin revisar (<%= sesionesConDiferenciaFicha.size() %>)</h5></div>
    <div class="card-body">
        <% for (SesionCaja sesionDiferencia : sesionesConDiferenciaFicha) {
            BigDecimal diferenciaFicha = sesionDiferencia.getDiferenciaArqueo();
            // A3d (pedido de Joel): el verde del sistema significa "todo bien" -- un sobrante
            // también es una diferencia a explicar, no algo bueno. Mismos colores que el cierre
            // (A3c): sobrante en ámbar oscuro (sin clase, el proyecto no tiene hoja de estilos
            // propia donde definir una -- REGLAS_DESARROLLO.md §15), faltante en rojo (text-danger).
            boolean esSobranteFicha = diferenciaFicha.signum() > 0;
            String etiquetaDiferenciaFicha = esSobranteFicha ? "sobrante" : "faltante";
            String claseDiferenciaFicha = esSobranteFicha ? "" : "text-danger";
            String estiloDiferenciaFicha = esSobranteFicha ? "color:#a15c00;" : "";
            String nombreCajeroFicha = nombreCajeroPorSesionFicha == null ? ""
                    : nombreCajeroPorSesionFicha.get(sesionDiferencia.getIdSesionCaja());
        %>
        <div class="border-bottom pb-2 mb-2">
            <p class="mb-1">Cajera <%= nombreCajeroFicha %> — <%= FormateadorFechas.comoDdMmAaaaHhMm(sesionDiferencia.getFechaHoraApertura()) %>
                a <%= FormateadorFechas.comoDdMmAaaaHhMm(sesionDiferencia.getFechaHoraCierre()) %>
                — <span class="<%= claseDiferenciaFicha %>" style="<%= estiloDiferenciaFicha %>"><strong><%= etiquetaDiferenciaFicha %>
                de <%= FormateadorNumeros.comoGuaranies(diferenciaFicha.abs()) %></strong></span>
                — motivo: <%= sesionDiferencia.getMotivoDiferencia() %></p>
            <%-- B188 (15/11/2026): abre la ventana del final del bloque. Antes el formulario estaba
                 acá mismo, en línea y sin confirmación, y marcar revisada NO se deshace
                 (ServicioCaja.marcarDiferenciaRevisada tira DiferenciaYaRevisadaException al
                 segundo intento). Es una acción de gerente sobre dinero: mismo patrón que
                 B155/B168/B171/B178. --%>
            <button type="button" class="btn btn-outline-primary" data-toggle="modal"
                    data-target="#modalRevisarDiferencia<%= sesionDiferencia.getIdSesionCaja() %>">Marcar revisada</button>
        </div>
        <% } %>

        <%-- B188: las ventanas, una por diferencia, después del for que dibuja las filas -- el mismo
             lugar y el mismo patrón que la baja de cliente en B155 (que Joel probó en pantalla el
             14/11), para que el marcado de cada fila quede con un botón y nada más. Ningún ancestro
             de acá tiene transform/filter, que es lo que rompería el position: fixed del modal
             (criterio de mora/_resultados.jsp).
             El formulario es el MISMO: misma ruta POST, mismo campo "observacion" con su required,
             mismo CSRF y el MISMO id por sesión, así que el cartel de "invalid" de pie.jsp sigue
             andando y el scraping del humo (grep de /caja/revisar-diferencia/[0-9]*) encuentra la
             ruta igual, porque sigue siendo el action de este form.
             NO lleva data-confirmar: la ventana ES la confirmación, y abre con el foco en descartar
             (autofocus en "No, volver"), porque no se deshace. --%>
        <% for (SesionCaja sesionDiferenciaModal : sesionesConDiferenciaFicha) {
            BigDecimal diferenciaModal = sesionDiferenciaModal.getDiferenciaArqueo();
            boolean esSobranteModal = diferenciaModal.signum() > 0;
            String nombreCajeroModal = nombreCajeroPorSesionFicha == null ? ""
                    : nombreCajeroPorSesionFicha.get(sesionDiferenciaModal.getIdSesionCaja());
        %>
        <div class="modal fade" id="modalRevisarDiferencia<%= sesionDiferenciaModal.getIdSesionCaja() %>" tabindex="-1">
            <div class="modal-dialog">
                <div class="modal-content">
                    <form method="post" action="${pageContext.request.contextPath}/caja/revisar-diferencia/<%= sesionDiferenciaModal.getIdSesionCaja() %>">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <div class="modal-header">
                            <h5 class="modal-title">¿Marcar revisada la diferencia del
                                <%= FormateadorFechas.comoDdMmAaaa(sesionDiferenciaModal.getFechaHoraApertura().toLocalDate()) %>?</h5>
                        </div>
                        <div class="modal-body">
                            <%-- B4/B43: el cartel repite lo que la fila ya muestra (cajera, el día, el
                                 signo y el monto), con las MISMAS expresiones -- no recalcula nada. --%>
                            <%-- comoGuaranies ya termina en "Gs.", así que ese punto cierra la
                                 oración: agregarle otro daría "50.000 Gs.." (D-145 punto 8, B178). --%>
                            <p class="text-muted mb-3"><small>Cajera <%= nombreCajeroModal %> —
                                <%= esSobranteModal ? "sobrante" : "faltante" %> de
                                <%= FormateadorNumeros.comoGuaranies(diferenciaModal.abs()) %>
                                <%-- B188 (15/11/2026, segundo defecto, también visto en pantalla por
                                     Joel): acá iba un "." pegado al motivo, y con un motivo que ya
                                     terminaba en punto el cartel decía "…a propósito..". El motivo lo
                                     escribe la cajera y vive en la base, así que lo cierra
                                     FormateadorTexto.conPuntoFinal, que agrega el punto solo si falta.
                                     La red del "Gs.." no puede ver este caso: lee src/main, no la base. --%>
                                Motivo que dejó la cajera: <%= FormateadorTexto.conPuntoFinal(sesionDiferenciaModal.getMotivoDiferencia()) %>
                                Una diferencia revisada no vuelve a quedar pendiente.</small></p>
                            <%-- (13/10/2026, pedido de Joel): mismo arreglo ya aplicado en RF14 para
                                 campoMotivoAnular -- la etiqueta le da al cartel genérico de pie.jsp
                                 ("Falta un dato") un nombre real para el campo, en vez de caer en
                                 "este campo". B188: en la ventana hay lugar, así que deja de ser
                                 sr-only. Un id por sesión porque puede haber más de una. --%>
                            <div class="form-group mb-0">
                                <label for="campoObservacionDiferencia<%= sesionDiferenciaModal.getIdSesionCaja() %>">Qué se hizo con la diferencia *</label>
                                <input type="text" id="campoObservacionDiferencia<%= sesionDiferenciaModal.getIdSesionCaja() %>"
                                       name="observacion" class="form-control"
                                       placeholder="Qué se hizo con la diferencia (obligatorio)" required>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                            <button type="submit" class="btn btn-outline-primary">Confirmar revisión</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        <% } %>
    </div>
</div>
<% } %>

<%-- E8 (Bloque 2, D-94): reimpresión del arqueo. El filtro de fecha (corrección de Joel) sirve
     para llegar a una sesión vieja sin pasar páginas.
     B191 (15/11/2026): antes eran "las últimas 10" y el resto solo se alcanzaba con el filtro --
     hoy son 136. Ahora se traen todas, de la más nueva a la más vieja, con contador y paginación de
     20, así que la página 1 trae las mismas 10 de siempre y 10 más. --%>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Sesiones cerradas (<%= FormateadorNumeros.comoCantidad(totalSesionesCerradasFicha) %>)</h5></div>
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/caja" class="form-inline mb-3">
            <div class="form-group mr-2">
                <label class="mr-2">Cierre entre</label>
                <input type="date" name="desde" class="form-control mr-2" value="<%= filtroDesdeFicha != null ? filtroDesdeFicha : "" %>">
                <span class="mr-2">y</span>
                <input type="date" name="hasta" class="form-control mr-2" value="<%= filtroHastaFicha != null ? filtroHastaFicha : "" %>">
            </div>
            <%-- B132 (11/11/2026): decía "Filtrar", y es el único de los listados de trabajo que no
                 decía "Buscar" (Clientes, Solicitudes, Mora y Préstamos ya lo decían, con la misma
                 lupa). Los informes se quedan con "Aplicar filtros" y su embudo: son otra familia,
                 con filtros y sin texto de búsqueda. --%>
            <button type="submit" class="btn btn-primary mr-2">
                <i class="fas fa-search"></i> Buscar</button>
            <% if (filtroDesdeFicha != null) { %>
            <a href="${pageContext.request.contextPath}/caja" class="btn btn-link">Quitar filtro (últimas 10)</a>
            <% } %>
        </form>
        <% if (sesionesCerradasRecientesFicha == null || sesionesCerradasRecientesFicha.isEmpty()) { %>
        <p class="text-muted mb-0">No hay sesiones cerradas <%= filtroDesdeFicha != null ? "en ese rango de fechas." : "todavía." %></p>
        <% } else { %>
        <div class="table-responsive">
            <table class="table table-sm">
                <thead><tr><th>Cajera</th><th>Apertura</th><th>Cierre</th><th>Diferencia</th><th></th></tr></thead>
                <tbody>
                <% for (SesionCaja sesionCerradaFicha : sesionesCerradasRecientesFicha) {
                    String nombreCajeroCerradaFicha = nombreCajeroPorSesionFicha == null ? ""
                            : nombreCajeroPorSesionFicha.get(sesionCerradaFicha.getIdSesionCaja());
                    // A3d (pedido de Joel): antes mostraba el monto sin signo ("50.000 Gs."), sin
                    // decir si sobró o faltó -- mismo criterio de colores que el cierre y las
                    // diferencias sin revisar (arriba): 0 en texto normal, sobra en ámbar oscuro,
                    // falta en rojo.
                    BigDecimal diferenciaCerradaFicha = sesionCerradaFicha.getDiferenciaArqueo();
                    int signoCerradaFicha = diferenciaCerradaFicha.signum();
                    String textoDiferenciaCerradaFicha;
                    String claseDiferenciaCerradaFicha = "";
                    String estiloDiferenciaCerradaFicha = "";
                    if (signoCerradaFicha == 0) {
                        textoDiferenciaCerradaFicha = FormateadorNumeros.comoGuaranies(diferenciaCerradaFicha);
                    } else if (signoCerradaFicha > 0) {
                        textoDiferenciaCerradaFicha = "Sobra " + FormateadorNumeros.comoGuaranies(diferenciaCerradaFicha);
                        estiloDiferenciaCerradaFicha = "color:#a15c00;";
                    } else {
                        textoDiferenciaCerradaFicha = "Falta " + FormateadorNumeros.comoGuaranies(diferenciaCerradaFicha.abs());
                        claseDiferenciaCerradaFicha = "text-danger";
                    }
                %>
                <tr>
                    <td><%= nombreCajeroCerradaFicha %></td>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(sesionCerradaFicha.getFechaHoraApertura()) %></td>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(sesionCerradaFicha.getFechaHoraCierre()) %></td>
                    <td><span class="<%= claseDiferenciaCerradaFicha %>" style="<%= estiloDiferenciaCerradaFicha %>"><%= textoDiferenciaCerradaFicha %></span>
                        <%-- B61 (B86, tramo 3): antes el único estado escrito era "(revisada)", así
                             que la ausencia cubría DOS casos distintos -- "no hace falta revisarla"
                             y "falta que el gerente la revise" -- y no había forma de distinguirlos.
                             La regla real: solo una diferencia DISTINTA DE CERO se revisa (RN-23;
                             SesionCajaDAO.listarConDiferenciaSinRevisar filtra
                             "diferencia_arqueo <> 0" y ServicioCaja.marcarDiferenciaRevisada
                             rechaza una caja sin diferencia). Son tres estados y ahora los tres se
                             leen; el de 0 Gs. no dice nada porque la columna "Diferencia" ya
                             muestra "0 Gs." -- no se agrega una segunda copia de lo que la pantalla
                             ya dice (criterio de B87). Ámbar = pendiente/esperando, el mismo
                             significado fijo que en todo el sistema (GUIA_VISUAL.md §1). --%>
                        <% if (sesionCerradaFicha.getIdGerenteRevisor() != null) { %>
                        <small class="text-muted">(revisada)</small>
                        <% } else if (signoCerradaFicha != 0) { %>
                        <small style="color:#a15c00;">(pendiente de revisión del gerente)</small>
                        <% } %>
                    </td>
                    <td>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/caja/<%= sesionCerradaFicha.getIdSesionCaja() %>/arqueo">Ver arqueo</a>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <%-- B191: la segunda paginación de esta pantalla, también con <jsp:include> y también sin
             pasar por listado-vivo.js (ver el comentario de la de Movimientos). Su URL base conserva
             el filtro de fechas y la página de Movimientos, así que mover una no mueve a la otra. --%>
        <%
            request.setAttribute("pagina", request.getAttribute("paginaSesiones"));
            request.setAttribute("totalPaginas", request.getAttribute("totalPaginasSesiones"));
            request.setAttribute("paginacionUrlBase", request.getAttribute("urlPaginacionSesiones"));
            request.setAttribute("paginacionParamPagina", "paginaSesiones");
            request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
        %>
        <jsp:include page="/WEB-INF/vistas/plantilla/paginacion.jsp" />
        <% } %>
    </div>
</div>

<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
