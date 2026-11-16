<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaLaboral" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ItemChecklistSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.OpcionMotivoCancelacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.GlosarioFinanciero" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    SolicitudCredito solicitudFicha = (SolicitudCredito) request.getAttribute("solicitud");
    Persona clienteFicha = (Persona) request.getAttribute("cliente");
    // Normalización de personas, grupo Laborales (16/10/2026): ingreso/comprobante de ingreso ya
    // no están en Persona -- se leen de personas_laborales (ControladorSolicitudesServlet.mostrarFicha).
    PersonaLaboral laboralClienteFicha = (PersonaLaboral) request.getAttribute("laboralCliente");
    ResultadoCotizacion resultadoFicha = (ResultadoCotizacion) request.getAttribute("resultadoCotizacion");
    Usuario usuarioDecisorFicha = (Usuario) request.getAttribute("usuarioDecisor");
    Usuario usuarioCanceladorFicha = (Usuario) request.getAttribute("usuarioCancelador");
    Usuario usuarioEnvioFicha = (Usuario) request.getAttribute("usuarioEnvio");
    boolean esBorrador = Boolean.TRUE.equals(request.getAttribute("esBorrador"));
    @SuppressWarnings("unchecked")
    List<ItemChecklistSolicitud> checklistPrevioEnvio = (List<ItemChecklistSolicitud>) request.getAttribute("checklistPrevioEnvio");
    @SuppressWarnings("unchecked")
    List<OpcionMotivoCancelacion> motivosCancelacionFicha = (List<OpcionMotivoCancelacion>) request.getAttribute("motivosCancelacion");
    // B89 (hallazgo de Joel en la prueba del 28/10/2026, resuelto el 29/10): el cartel nombra lo
    // que falta, no solo "No cumple". Mismos ítems que la tabla de ✓/✗ de más abajo
    // (ServicioSolicitudes.construirChecklistPrevioEnvio, el único dueño de esas etiquetas) y el
    // mismo texto de la capacidad que ya usa enviarAAprobacion -- ningún chequeo nuevo: solo se
    // muestra el que ya decidía el color del cartel.
    List<String> faltantesPreliminares = new java.util.ArrayList<>();
    if (checklistPrevioEnvio != null) {
        for (ItemChecklistSolicitud item : checklistPrevioEnvio) {
            if (!item.isCumple()) {
                faltantesPreliminares.add(item.getEtiqueta());
            }
        }
    }
    boolean checklistCompleto = faltantesPreliminares.isEmpty();
    // B122 (10/11/2026, hallazgo de Joel en SOL-2026-0197): mientras la solicitud está en
    // BORRADOR, la afectación que se muestra y se compara es la de HOY -- el mismo número y el
    // mismo límite vigente que va a usar enviarAAprobacion para aceptar o rechazar
    // (ServicioSolicitudes.calcularAfectacionVigente, servido por el controlador). Antes acá se
    // comparaba la FOTO guardada al simular contra el límite de esa foto, así que con el ingreso
    // del cliente ya cambiado la pantalla mostraba 2,6 % y decía "Cumple" mientras el servidor
    // rechazaba con 154,6 %. Ya enviada, la foto es lo correcto: es la base sobre la que se
    // decidió, y por eso el controlador no manda el atributo en ese caso.
    ServicioSolicitudes.AfectacionVigente afectacionVigenteFicha =
            (ServicioSolicitudes.AfectacionVigente) request.getAttribute("afectacionVigente");
    java.math.BigDecimal porcentajeAfectacionFicha = afectacionVigenteFicha == null
            ? solicitudFicha.getPorcentajeAfectacion() : afectacionVigenteFicha.getPorcentaje();
    java.math.BigDecimal limiteAfectacionFicha = afectacionVigenteFicha == null
            ? solicitudFicha.getLimiteAfectacionAplicado() : afectacionVigenteFicha.getLimite();
    boolean superaLimite = porcentajeAfectacionFicha.compareTo(limiteAfectacionFicha) > 0;
    // B122: el faltante de la capacidad ya NO se agrega acá. Ahora llega como un ítem más del
    // checklist (ServicioSolicitudes.construirChecklistPrevioEnvio, el décimo), que es el único
    // dueño de esas etiquetas (B89) -- agregarlo también acá lo mostraría dos veces en el mismo
    // cartel.
    // B123 (11/11/2026, decisión de Joel): el cuadro rojo aparte YA NO EXISTE. Decía lo mismo que
    // este cartel con otras palabras y repetía los dos números: el oficial leía el problema tres
    // veces (el aviso en vivo debajo de "Monto", este cartel y el cuadro). Lo único que el cuadro
    // aportaba -- QUÉ HACER -- se mudó acá, como última oración, y se escribe una sola vez. El
    // aviso en vivo se queda, porque es el único que sirve MIENTRAS se escribe el monto (los
    // otros no existen hasta recargar).
    // "Cumple validaciones preliminares" depende de si TODAVÍA hace falta algo para enviar.
    // Bloque 7/E10 (D-129, Obs. 19/22): "el verde solo si está todo" -- el checklist completo
    // (edad, zona, situación laboral, documentos, buró) y, desde B122, la capacidad de pago.
    boolean cumpleValidaciones = checklistCompleto;
    StringBuilder textoValidacionesPreliminares = new StringBuilder();
    if (cumpleValidaciones) {
        textoValidacionesPreliminares.append("Cumple validaciones preliminares");
    } else {
        textoValidacionesPreliminares.append("No cumple validaciones preliminares. ")
                .append(faltantesPreliminares.size() == 1 ? "Falta: " : "Faltan: ");
        for (int indiceFaltante = 0; indiceFaltante < faltantesPreliminares.size(); indiceFaltante++) {
            if (indiceFaltante > 0) {
                textoValidacionesPreliminares.append(", ");
            }
            textoValidacionesPreliminares.append(faltantesPreliminares.get(indiceFaltante));
        }
        textoValidacionesPreliminares.append(".");
        // B123: el "qué hacer" del cuadro rojo que se eliminó, ahora acá y una sola vez. Va
        // después del punto de la enumeración (nunca pegado a un faltante, que lo dejaría en el
        // medio de la lista) y solo cuando el problema es la capacidad de pago.
        if (superaLimite) {
            textoValidacionesPreliminares.append(" Reduzca el monto o amplíe el plazo.");
        }
    }
    // Bloque 7/E10, Tanda A (D-126): aviso (no bloquea) si la cédula del cliente ya venció.
    boolean cedulaClienteVencida = Boolean.TRUE.equals(request.getAttribute("cedulaClienteVencida"));
    // B88 (29/10/2026): "#tabDocumentos"/"#tabBuro" según el primer paso pendiente del cliente, o
    // cadena vacía (① Datos) si no hay nada pendiente -- lo resuelve PasoGuiado.anclaPestanaFicha.
    String anclaFichaClienteFicha = (String) request.getAttribute("anclaFichaCliente");
    if (anclaFichaClienteFicha == null) {
        anclaFichaClienteFicha = "";
    }
    // B163 (14/11/2026): el "Volver a la lista" del pie conserva la búsqueda y el filtro de estado
    // que el oficial tenía. El valor lo arma ControladorSolicitudesServlet.mostrarLista y lo sirve
    // mostrarFicha (RETORNO_LISTA_EN_SESION); acá solo se dibuja, ya viene codificado. Vacío =
    // /solicitudes, como antes -- también si esta JSP corre contra un .class viejo.
    String urlVolverListaFicha = (String) request.getAttribute("urlVolverLista");
    urlVolverListaFicha = urlVolverListaFicha == null || urlVolverListaFicha.isEmpty()
            ? "" : "?" + urlVolverListaFicha;
%>
<h1>Solicitud <%= solicitudFicha.getCodigoSolicitud() %></h1>
<p><span class="badge badge-secondary"><%= solicitudFicha.getEstadoSolicitud().getEtiqueta() %></span></p>

<% if (cedulaClienteVencida) { %>
<div class="alert alert-warning">
    La cédula de identidad del cliente venció el <%= FormateadorFechas.comoDdMmAaaa(clienteFicha.getFechaVencimientoCedula()) %>.
    Pida una cédula vigente antes de continuar.
</div>
<% } %>

<%-- Ajuste 6 (Joel): quién decidió/canceló y cuándo, con el motivo cuando corresponde. --%>
<% if (solicitudFicha.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_APROBACION) { %>
<%-- RF06 (pedido de Joel, 10/10/2026): misma franja que aprobada/rechazada/cancelada, "qué sigue".
     Corrección (pedido de Joel): si de verdad no hay forma de saber quién la envió (caso borde,
     ver AuditoriaDAO), se omite "por -" en vez de mostrar un guion sin sentido. --%>
<div class="alert alert-info">
    <% if (usuarioEnvioFicha != null) { %>
    Enviada por <%= usuarioEnvioFicha.getNombres() %> <%= usuarioEnvioFicha.getApellidos() %>
    <% } else { %>
    Enviada
    <% } %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudFicha.getFechaEnvioAprobacion()) %>.
    Espera la decisión del gerente.
</div>
<% } else if (solicitudFicha.getEstadoSolicitud() == EstadoSolicitud.APROBADA) { %>
<div class="alert alert-success">
    Aprobada por <%= usuarioDecisorFicha == null ? "-" : usuarioDecisorFicha.getNombres() + " " + usuarioDecisorFicha.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudFicha.getFechaDecision()) %>.
    Monto aprobado: <%= FormateadorNumeros.comoGuaranies(solicitudFicha.getMontoAprobado()) %>,
    plazo: <%= solicitudFicha.getPlazoAprobadoMeses() %> meses.
    <%-- B104: el botón del paso siguiente, en UN solo lugar para las dos fichas (esta y
         aprobaciones/ficha.jsp) -- ver el fragmento. --%>
    <%@ include file="/WEB-INF/vistas/plantilla/paso_formalizacion.jsp" %>
</div>
<% } else if (solicitudFicha.getEstadoSolicitud() == EstadoSolicitud.RECHAZADA) { %>
<div class="alert alert-danger">
    Rechazada por <%= usuarioDecisorFicha == null ? "-" : usuarioDecisorFicha.getNombres() + " " + usuarioDecisorFicha.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudFicha.getFechaDecision()) %>.
    Motivo: <%= solicitudFicha.getMotivoRechazo() %>
</div>
<% } else if (solicitudFicha.getEstadoSolicitud() == EstadoSolicitud.CANCELADA) { %>
<div class="alert alert-secondary">
    Cancelada por <%= usuarioCanceladorFicha == null ? "-" : usuarioCanceladorFicha.getNombres() + " " + usuarioCanceladorFicha.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudFicha.getFechaCancelacion()) %>.
    Motivo: <%= solicitudFicha.getMotivoCancelacion() %>
</div>
<% } %>

<div class="card">
    <div class="card-header"><h5 class="m-0">Cliente</h5></div>
    <div class="card-body">
        <p>
            <strong><%= clienteFicha.getNombres() %> <%= clienteFicha.getApellidos() %></strong>
            — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFicha.getCedulaIdentidad()) %>
            <%-- 2d (pedido de Joel, 17/10/2026): mientras es BORRADOR se muestra el ingreso ACTUAL
                 (todavía no hay nada congelado); una vez enviada, el congelado al enviar -- para
                 que este número siempre coincida con el que de verdad se usó para la afectación
                 de más abajo, aunque el ingreso del cliente haya cambiado después. --%>
            <br><%= laboralClienteFicha.isPresentaComprobanteIngreso() ? "Ingreso neto comprobado" : "Ingreso declarado (sin comprobante)" %><%= esBorrador ? "" : " (evaluado al enviar)" %>:
            <%= FormateadorNumeros.comoGuaranies(esBorrador ? laboralClienteFicha.getIngresoNetoComprobado() : solicitudFicha.getIngresoClienteEvaluado()) %>
            <br>Presenta comprobante de ingresos: <%= laboralClienteFicha.isPresentaComprobanteIngreso() ? "Sí" : "No" %>
            <%-- B88 (hallazgo de Joel en la prueba del 29/10/2026): abre la ficha en la pestaña del
                 primer paso pendiente (ej. ② Expediente si falta un documento), no siempre en
                 ① Datos. El ancla la resuelve el dueño de la regla (PasoGuiado.anclaPestanaFicha,
                 vía ControladorSolicitudesServlet.mostrarFicha); vacía = ① Datos, como antes. --%>
            <br><a href="${pageContext.request.contextPath}/clientes/<%= clienteFicha.getIdPersona() %><%= anclaFichaClienteFicha %>">Ver ficha completa / documentos</a>
        </p>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Condiciones y simulación</h5></div>
    <div class="card-body">
        <% if (esBorrador) { %>
        <form method="post" action="${pageContext.request.contextPath}/solicitudes/<%= solicitudFicha.getIdSolicitud() %>/simular" class="mb-4">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-row">
                <div class="form-group col-md-3">
                    <label>Monto (Gs.)</label>
                    <input type="text" name="monto" id="campoMontoSimular" class="form-control campo-guaranies"
                           value="<%= solicitudFicha.getMontoSolicitado().toPlainString() %>" required>
                </div>
                <div class="form-group col-md-2">
                    <label>Plazo (meses)</label>
                    <%-- B164 (14/11/2026): inputmode="numeric" -- teclado numérico en la tablet,
                         cero cambio visual. El tercero de los tres campos de plazo del sistema. --%>
                    <input type="text" inputmode="numeric" name="plazoMeses" id="campoPlazoSimular" class="form-control"
                           value="<%= solicitudFicha.getPlazoSolicitadoMeses() %>" required>
                </div>
                <div class="form-group col-md-4">
                    <label>Destino del crédito</label>
                    <input type="text" name="destinoCredito" class="form-control"
                           value="<%= solicitudFicha.getDestinoCredito() %>" required>
                </div>
                <div class="form-group col-md-3">
                    <label>Primer vencimiento estimado</label>
                    <input type="date" name="primeraFechaVencimiento" id="campoFechaSimular" class="form-control"
                           value="<%= solicitudFicha.getPrimeraFechaVencimientoSolicitada() %>" required>
                </div>
            </div>
            <%-- RF05 (pedido de Joel, 10/10/2026): afectación en vivo mientras se edita la
                 simulación -- mismo endpoint y criterio que solicitudes/nueva.jsp, con idSolicitud
                 en vez de cédula. --%>
            <div id="indicadorAfectacionSimular" class="mb-2"></div>
            <button type="submit" class="btn btn-primary">Simular</button>
            <small class="text-muted ml-2">Guarda las condiciones y recalcula la cuota y la capacidad de pago.</small>
        </form>
        <script>
            (function () {
                var campoMonto = document.getElementById('campoMontoSimular');
                var campoPlazo = document.getElementById('campoPlazoSimular');
                var campoFecha = document.getElementById('campoFechaSimular');
                var indicador = document.getElementById('indicadorAfectacionSimular');
                var temporizador = null;
                function actualizarIndicadorSimular() {
                    if (temporizador) { clearTimeout(temporizador); }
                    temporizador = setTimeout(function () {
                        var monto = campoMonto.value.trim();
                        var plazo = campoPlazo.value.trim();
                        var fecha = campoFecha.value.trim();
                        if (!monto || !plazo || !fecha) {
                            indicador.innerHTML = '';
                            return;
                        }
                        var parametros = 'idSolicitud=<%= solicitudFicha.getIdSolicitud() %>'
                                + '&monto=' + encodeURIComponent(monto)
                                + '&plazoMeses=' + encodeURIComponent(plazo)
                                + '&primeraFechaVencimiento=' + encodeURIComponent(fecha);
                        fetch('${pageContext.request.contextPath}/solicitudes/previsualizar-afectacion?' + parametros)
                                .then(function (respuesta) { return respuesta.text(); })
                                .then(function (texto) {
                                    if (!texto) { indicador.innerHTML = ''; return; }
                                    var partes = texto.split('|');
                                    var supera = partes[0] === '1';
                                    indicador.innerHTML = '<small class="'
                                            + (supera ? 'text-danger font-weight-bold' : 'text-muted') + '">'
                                            + partes[1] + '</small>';
                                })
                                .catch(function () { indicador.innerHTML = ''; });
                    }, 400);
                }
                [campoMonto, campoPlazo, campoFecha].forEach(function (campo) {
                    campo.addEventListener('input', actualizarIndicadorSimular);
                    campo.addEventListener('change', actualizarIndicadorSimular);
                });
                actualizarIndicadorSimular();
            })();
        </script>
        <% } %>

        <%-- Corrección (pedido de Joel, 10/10/2026): "condiciones y números" se ve siempre, sin
             importar el estado -- lo que se oculta fuera de BORRADOR es todo lo que es sobre
             VALIDACIONES (checklist, "cumple preliminares", aviso de capacidad), que ya no
             tiene sentido para una solicitud cancelada/aprobada/rechazada/pendiente. --%>
        <%
            // RF06 (pedido de Joel, 10/10/2026): ícono ⓘ con el glosario de RF01 -- mismos
            // valores dinámicos que ControladorCotizadorServlet/cotizador/formulario.jsp, para
            // que el texto completo de TEA/TNA sea idéntico al del cotizador con estos datos.
            java.math.BigDecimal topeTeaVigenteFicha = (java.math.BigDecimal) request.getAttribute("topeTeaVigente");
            String topeTeaFormateadoFicha = topeTeaVigenteFicha == null ? "no disponible"
                    : FormateadorNumeros.comoPorcentaje(topeTeaVigenteFicha);
            String tnaFormateadaFicha = FormateadorNumeros.comoPorcentaje(solicitudFicha.getTasaTnaPropuesta());
            String tasaMensualFormateadaFicha = FormateadorNumeros.comoPorcentaje(
                    solicitudFicha.getTasaTnaPropuesta().divide(new java.math.BigDecimal("12"), 6,
                            java.math.RoundingMode.HALF_UP));
            // B122: en BORRADOR, el límite vigente (el que va a aplicar el servidor); ya
            // enviada, el que se aplicó en su momento. Lo resuelve el bloque de arriba.
            String limiteAfectacionFormateadoFicha = FormateadorNumeros.comoPorcentaje(limiteAfectacionFicha);
        %>
        <table class="table table-sm">
            <tr><th>Cuota más alta (con IVA) <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.CUOTA_MAXIMA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.CUOTA_MAXIMA.completo() %>"></i></th>
                <td><%= FormateadorNumeros.comoGuaranies(solicitudFicha.getCuotaSimulada()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(solicitudFicha.getCuotaSimulada()) %></small>
                </td></tr>
            <tr><th>TEA <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TEA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TEA.completo(topeTeaFormateadoFicha) %>"></i>
                    / TNA <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TNA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TNA.completo(tasaMensualFormateadaFicha, tnaFormateadaFicha) %>"></i></th>
                <td><%= FormateadorNumeros.comoPorcentaje(solicitudFicha.getTasaTeaSimulada()) %>
                    / <%= tnaFormateadaFicha %></td></tr>
            <tr><th>CFT simulado <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.CFT.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.CFT.completo() %>"></i></th>
                <td><%= solicitudFicha.getCftSimulado() == null ? "-" : FormateadorNumeros.comoPorcentaje(solicitudFicha.getCftSimulado()) %></td></tr>
            <tr><th>Total a pagar (con IVA) <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TOTAL_A_PAGAR.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TOTAL_A_PAGAR.completo() %>"></i></th>
                <td><%= FormateadorNumeros.comoGuaranies(resultadoFicha.getMontoTotalConIva()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(resultadoFicha.getMontoTotalConIva()) %></small>
                </td></tr>
            <tr class="<%= superaLimite ? "table-warning" : "" %>">
                <th>Afectación al ingreso <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.AFECTACION.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.AFECTACION.completo(limiteAfectacionFormateadoFicha) %>"></i></th>
                <%-- Corrección (pedido de Joel, 10/10/2026): un decimal, igual que el indicador en
                     vivo (antes acá se veía "16,39 %" y arriba, al simular, "16,4 %"). --%>
                <td><%= FormateadorNumeros.comoPorcentajeUnDecimal(porcentajeAfectacionFicha) %>
                    (límite: <%= limiteAfectacionFormateadoFicha %>)</td>
            </tr>
        </table>

        <% if (esBorrador) { %>
        <%-- Bloque 7/E10 (D-129, Obs. 19/22): requisitos con su estado, antes de "Enviar a
             aprobación" -- solo informativo, no reemplaza la validación real del servidor. --%>
        <% if (checklistPrevioEnvio != null) { %>
        <table class="table table-sm mb-2">
            <tbody>
            <% for (ItemChecklistSolicitud item : checklistPrevioEnvio) { %>
            <tr>
                <td style="width: 2rem;">
                    <% if (item.isCumple()) { %>
                    <span class="text-success">&#10003;</span>
                    <% } else { %>
                    <span class="text-danger">&#10007;</span>
                    <% } %>
                </td>
                <td><%= item.getEtiqueta() %></td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>

        <div class="alert <%= cumpleValidaciones ? "alert-success" : "alert-warning" %>">
            <%= textoValidacionesPreliminares.toString() %>
        </div>

        <% } %>
    </div>
</div>

<% if (esBorrador) { %>
<div class="mt-3">
    <%-- Corrección (pedido de Joel, 10/10/2026): si la pantalla ya sabe que no cumple, no pide
         confirmación -- va directo al servidor, que sigue validando igual y muestra la lista
         completa de lo que falta. El "¿Enviar...?" solo tiene sentido cuando sí cumple. --%>
    <%
        // B124 (dentro de B129, 11/11/2026): este cartel era el único de la familia sin resumen.
        // Los cinco datos son los MISMOS que la ficha ya muestra arriba, con las MISMAS
        // expresiones (B4/B43: un cartel no redacta ni recalcula, repite). Los renglones van con
        // &#10; dentro del atributo, que es lo que plantilla/pie.jsp convierte en saltos de línea.
        // Y abre con el foco en "No, volver" (decisión de Joel al probarlo, 11/11/2026): para el
        // oficial enviar ES irreversible -- enviarAAprobacion exige BORRADOR y, una vez enviada,
        // solo el gerente puede cancelarla (ServicioAprobacionCredito.cancelar). Es el tercer
        // criterio de B105, el mismo que ya aplican anular el préstamo y cancelar la solicitud.
        String resumenEnvioFicha = "Cliente: " + clienteFicha.getNombres() + " " + clienteFicha.getApellidos()
                + " — C.I. " + FormateadorTexto.comoCedulaConPuntos(clienteFicha.getCedulaIdentidad())
                + "&#10;Monto: " + FormateadorNumeros.comoGuaranies(solicitudFicha.getMontoSolicitado())
                + " a " + solicitudFicha.getPlazoSolicitadoMeses() + " meses"
                + "&#10;Cuota más alta: " + FormateadorNumeros.comoGuaranies(solicitudFicha.getCuotaSimulada())
                + "&#10;Afectación al ingreso: "
                + FormateadorNumeros.comoPorcentajeUnDecimal(porcentajeAfectacionFicha)
                + " (límite: " + limiteAfectacionFormateadoFicha + ")";
    %>
    <form method="post" action="${pageContext.request.contextPath}/solicitudes/<%= solicitudFicha.getIdSolicitud() %>/enviar"
          class="d-inline"<%= cumpleValidaciones
                ? " data-confirmar-titulo=\"¿Enviar esta solicitud a aprobación?\""
                        + " data-confirmar=\"" + resumenEnvioFicha + "\""
                        + " data-confirmar-foco=\"cancelar\""
                        + " data-confirmar-boton=\"Sí, enviar\"" : "" %>>
        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
        <%-- B131 (11/11/2026, decisión de Joel): mientras el checklist no cierra, el botón DEJA DE
             SER el verde primario y pasa al azul de marca con contorno. Sigue activo y sigue
             enviando (si no cumple, el servidor lista todo lo que falta, que es la decisión del
             10/10/2026 que explica el comentario de arriba); lo que cambia es que la pantalla ya
             no invita con el botón más llamativo a una acción que ella misma acaba de decir que va
             a ser rechazada.
             Por qué ESTA variante y no otra, medido en pantalla sobre el fondo real (#EEF3F8):
             - `btn-outline-success` (lo primero que se probó) deja el rótulo en 2,81:1, peor que
               cualquier otro botón del sistema, y además gasta el verde en algo que NO está
               confirmado, contra el significado fijo de los colores de GUIA_VISUAL.md.
             - `btn-outline-secondary` da 4,2:1 pero es gris: se lee como deshabilitado (B87) y
               este botón NO lo está.
             - `btn-outline-primary` es el azul de marca (#0C325C), 11,57:1, y es la misma variante
               que el sistema ya usa para una acción secundaria viva ("Regenerar", en
               formalizacion/ficha.jsp). --%>
        <button type="submit" class="btn <%= cumpleValidaciones ? "btn-success" : "btn-outline-primary" %>">Enviar a aprobación</button>
    </form>
    <%-- B131 (11/11/2026, decisión de Joel al probarlo): con "Enviar a aprobación" en contorno,
         este botón pasaba a ser el más llamativo de la fila -- y abrir la cancelación no es lo que
         se espera que el oficial haga. Pasa a contorno rojo, que además es como ya se ve el mismo
         botón en aprobaciones/ficha.jsp. El "Confirmar cancelación" de adentro del desplegable se
         queda en rojo pleno: ahí sí es la acción principal, y es la que no se deshace. --%>
    <%-- B168 (14/11/2026): abre la ventana del final de esta página. Antes era un
         data-toggle="collapse" que desplegaba el formulario acá mismo, debajo de los botones. --%>
    <button type="button" class="btn btn-outline-danger" data-toggle="modal" data-target="#modalCancelarSolicitud">Cancelar solicitud</button>
</div>
<script>
    // RF06 (pedido de Joel, 10/10/2026): mismo criterio que el rechazo (RF10) -- el detalle es
    // obligatorio solo cuando el motivo elegido lo exige (hoy, "Otro").
    (function () {
        var selectMotivoCancelacion = document.getElementById('selectMotivoCancelacion');
        var textareaDetalleCancelacion = document.getElementById('textareaDetalleCancelacion');
        if (!selectMotivoCancelacion || !textareaDetalleCancelacion) { return; }
        function actualizarDetalleCancelacion() {
            var opcionElegida = selectMotivoCancelacion.options[selectMotivoCancelacion.selectedIndex];
            var requiereDetalle = opcionElegida && opcionElegida.getAttribute('data-requiere-detalle') === 'true';
            textareaDetalleCancelacion.required = requiereDetalle;
            textareaDetalleCancelacion.placeholder = requiereDetalle ? 'Detalle (obligatorio)' : 'Detalle (opcional)';
        }
        selectMotivoCancelacion.addEventListener('change', actualizarDetalleCancelacion);
        actualizarDetalleCancelacion();
    })();
</script>
<% } %>

<%-- B168 (14/11/2026): la cancelación, en ventana. Antes el formulario se desplegaba dentro de la
     página (data-toggle="collapse"), debajo de la fila de botones -- el mismo patrón que la baja de
     cliente dejó de usar en B155. La ventana va acá, al final del cuerpo, por el mismo criterio que
     mora/_resultados.jsp: fuera de las tarjetas, para que ningún contenedor con contexto propio la
     afecte.
     El formulario es el MISMO: misma ruta POST, mismos codigoMotivoCancelacion/detalleCancelacion,
     mismo CSRF, y los MISMOS id -- así el script de más arriba, que vuelve obligatorio el detalle
     cuando el motivo lo exige (RF06, hoy "Otro"), sigue funcionando sin tocarlo.
     NO lleva data-confirmar: la ventana ES la confirmación (título, la advertencia de que no se
     deshace, y el botón que dice la operación); dejarlo apilaría dos diálogos, igual que en B155.
     SÍ lleva el foco en descartar, al revés que la baja de un cliente: cancelar una solicitud NO
     se deshace, que es el tercer criterio de B105 -- el autofocus va en "No, volver" para que un
     Enter de más no cancele nada. "Confirmar cancelación" se queda en rojo pleno (B131: ahí sí es
     la acción principal). --%>
<%-- La misma guarda que el botón: solo en BORRADOR (el bloque de arriba, línea del if esBorrador).
     Si la solicitud ya salió de borrador, no hay botón ni ventana. --%>
<% if (esBorrador) { %>
<div class="modal fade" id="modalCancelarSolicitud" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post" action="${pageContext.request.contextPath}/solicitudes/<%= solicitudFicha.getIdSolicitud() %>/cancelar">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <div class="modal-header">
                    <h5 class="modal-title">¿Cancelar la solicitud <%= solicitudFicha.getCodigoSolicitud() %>?</h5>
                </div>
                <div class="modal-body">
                    <p class="text-muted mb-3"><small>No se puede deshacer. La solicitud queda cancelada
                        y hay que crear una nueva para volver a evaluar al cliente.</small></p>
                    <div class="form-group">
                        <label>Motivo de la cancelación *</label>
                        <select name="codigoMotivoCancelacion" id="selectMotivoCancelacion" class="form-control" required>
                            <option value="">Seleccione...</option>
                            <% if (motivosCancelacionFicha != null) { for (OpcionMotivoCancelacion motivoCancel : motivosCancelacionFicha) { %>
                            <option value="<%= motivoCancel.getCodigo() %>" data-requiere-detalle="<%= motivoCancel.isRequiereDetalle() %>"><%= motivoCancel.getEtiqueta() %></option>
                            <% } } %>
                        </select>
                    </div>
                    <div class="form-group mb-0">
                        <label>Detalle</label>
                        <textarea name="detalleCancelacion" id="textareaDetalleCancelacion" class="form-control" rows="2"
                                  placeholder="Detalle (opcional)"></textarea>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                    <button type="submit" class="btn btn-danger">Confirmar cancelación</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>

<a href="${pageContext.request.contextPath}/solicitudes<%= urlVolverListaFicha %>" class="btn btn-default mt-3">Volver a la lista</a>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
