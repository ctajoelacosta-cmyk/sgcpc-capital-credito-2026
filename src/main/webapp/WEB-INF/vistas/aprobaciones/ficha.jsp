<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaLaboral" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.MotivoRechazoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ItemChecklistSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.OpcionMotivoCancelacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.GlosarioFinanciero" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    SolicitudCredito solicitudRevision = (SolicitudCredito) request.getAttribute("solicitud");
    Persona clienteRevision = (Persona) request.getAttribute("cliente");
    // Normalización de personas, grupo Laborales (16/10/2026): ingreso/comprobante de ingreso/
    // situación laboral/empleador/cuota de otras deudas ya no están en Persona -- se leen de
    // personas_laborales (ControladorAprobacionesServlet.mostrarFicha).
    PersonaLaboral laboralClienteRevision = (PersonaLaboral) request.getAttribute("laboralCliente");
    ResultadoCotizacion resultadoRevision = (ResultadoCotizacion) request.getAttribute("resultadoCotizacion");
    // B88 (29/10/2026): "#tabDocumentos"/"#tabBuro" según el primer paso pendiente del cliente, o
    // cadena vacía (① Datos) si no hay nada pendiente -- lo resuelve PasoGuiado.anclaPestanaFicha.
    String anclaFichaClienteRevision = (String) request.getAttribute("anclaFichaCliente");
    if (anclaFichaClienteRevision == null) {
        anclaFichaClienteRevision = "";
    }
    Usuario usuarioDecisorRevision = (Usuario) request.getAttribute("usuarioDecisor");
    Usuario usuarioCanceladorRevision = (Usuario) request.getAttribute("usuarioCancelador");
    @SuppressWarnings("unchecked")
    List<FilaBuroSolicitud> listaConsultasRevision = (List<FilaBuroSolicitud>) request.getAttribute("filasConsultaBuro");
    py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud avisoBuroRevision =
            (py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud) request.getAttribute("avisoBuro");
    boolean pendiente = solicitudRevision.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_APROBACION;
    // C20 (29/10/2026): una solicitud YA FORMALIZADA no se puede cancelar -- antes esta condición
    // solo miraba el estado, y una solicitud formalizada se queda en APROBADA (no hay un estado
    // "FORMALIZADA"), así que el botón "Cancelar solicitud" seguía apareciendo y el servidor lo
    // aceptaba, dejando la solicitud CANCELADA con su préstamo CC-... vivo. El gate real vive en
    // ServicioAprobacionCredito.validarCancelable; esto es para no ofrecer lo que va a rechazar.
    boolean cancelable = pendiente || (solicitudRevision.getEstadoSolicitud() == EstadoSolicitud.APROBADA
            && !solicitudRevision.isFormalizada());
    boolean superaLimiteRevision = solicitudRevision.getPorcentajeAfectacion().compareTo(solicitudRevision.getLimiteAfectacionAplicado()) > 0;
    // Bloque 7/E10, Tanda A (D-126): lista referencial de motivos de rechazo.
    @SuppressWarnings("unchecked")
    List<MotivoRechazoSolicitud> listaMotivosRechazo = (List<MotivoRechazoSolicitud>) request.getAttribute("motivosRechazo");
    // RF06 (pedido de Joel, 10/10/2026): lista fija de motivos de cancelación.
    @SuppressWarnings("unchecked")
    List<OpcionMotivoCancelacion> listaMotivosCancelacion = (List<OpcionMotivoCancelacion>) request.getAttribute("motivosCancelacion");
    // Bloque 7/E10 (D-129, Obs. 26): resumen del cliente para decidir sin entrar a la ficha.
    Integer edadClienteRevision = (Integer) request.getAttribute("edadCliente");
    String departamentoClienteRevision = (String) request.getAttribute("departamentoCliente");
    String ciudadClienteRevision = (String) request.getAttribute("ciudadCliente");
    @SuppressWarnings("unchecked")
    List<ItemChecklistSolicitud> checklistClienteRevision = (List<ItemChecklistSolicitud>) request.getAttribute("checklistCliente");
    // B170 (14/11/2026): el "Volver a la bandeja" del pie conserva la PESTAÑA y los filtros que el
    // gerente tenía. El valor lo arma ControladorAprobacionesServlet.mostrarLista y lo sirve
    // mostrarFicha (RETORNO_BANDEJA_EN_SESION); acá solo se dibuja, ya viene codificado. Vacío =
    // /aprobaciones, que es Pendientes -- también si esta JSP corre contra un .class viejo.
    String urlVolverBandejaRevision = (String) request.getAttribute("urlVolverBandeja");
    urlVolverBandejaRevision = urlVolverBandejaRevision == null || urlVolverBandejaRevision.isEmpty()
            ? "" : "?" + urlVolverBandejaRevision;
%>
<h1>Solicitud <%= solicitudRevision.getCodigoSolicitud() %></h1>
<p><span class="badge badge-secondary"><%= solicitudRevision.getEstadoSolicitud().getEtiqueta() %></span></p>

<%-- RF08 (pedido de Joel, 11/10/2026): franja arriba de todo, antes de la tarjeta Cliente --
     mora (roja) o, si no hay ninguna mora, clasificación de riesgo alto D/E (naranja). Si hay
     mora Y D/E a la vez, se muestra solo la roja (con la clasificación incluida en su línea) --
     ver ServicioAprobacionCredito.construirAvisoBuro. --%>
<% if (avisoBuroRevision != null && (avisoBuroRevision.isHayMora() || avisoBuroRevision.isHayRiesgoAlto())) { %>
<div class="alert <%= avisoBuroRevision.isHayMora() ? "alert-danger" : "alert-riesgo-alto" %>">
    <% for (String lineaAvisoBuro : avisoBuroRevision.getLineas()) { %>
    <div><%= lineaAvisoBuro %></div>
    <% } %>
</div>
<% } %>

<%-- Ajuste 6 (Joel): quién decidió/canceló y cuándo, con el motivo cuando corresponde. --%>
<% if (solicitudRevision.getEstadoSolicitud() == EstadoSolicitud.APROBADA) { %>
<div class="alert alert-success">
    Aprobada por <%= usuarioDecisorRevision == null ? "-" : usuarioDecisorRevision.getNombres() + " " + usuarioDecisorRevision.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudRevision.getFechaDecision()) %>.
    Monto aprobado: <%= FormateadorNumeros.comoGuaranies(solicitudRevision.getMontoAprobado()) %>,
    plazo: <%= solicitudRevision.getPlazoAprobadoMeses() %> meses.
    <%-- B104 (01/11/2026): ESTA es la pantalla donde el gerente aprueba y donde sale el cartel
         "Ahora se formaliza en Formalización" -- el botón del paso siguiente va acá, con el mismo
         fragmento y el mismo dueño del texto que la ficha del oficial. --%>
    <%@ include file="/WEB-INF/vistas/plantilla/paso_formalizacion.jsp" %>
</div>
<% } else if (solicitudRevision.getEstadoSolicitud() == EstadoSolicitud.RECHAZADA) { %>
<div class="alert alert-danger">
    Rechazada por <%= usuarioDecisorRevision == null ? "-" : usuarioDecisorRevision.getNombres() + " " + usuarioDecisorRevision.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudRevision.getFechaDecision()) %>.
    Motivo: <%= solicitudRevision.getMotivoRechazo() %>
</div>
<% } else if (solicitudRevision.getEstadoSolicitud() == EstadoSolicitud.CANCELADA) { %>
<div class="alert alert-secondary">
    Cancelada por <%= usuarioCanceladorRevision == null ? "-" : usuarioCanceladorRevision.getNombres() + " " + usuarioCanceladorRevision.getApellidos() %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(solicitudRevision.getFechaCancelacion()) %>.
    Motivo: <%= solicitudRevision.getMotivoCancelacion() %>
</div>
<% } %>

<div class="card">
    <div class="card-header"><h5 class="m-0">Cliente</h5></div>
    <div class="card-body">
        <p>
            <strong><%= clienteRevision.getNombres() %> <%= clienteRevision.getApellidos() %></strong>
            — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteRevision.getCedulaIdentidad()) %>
            <%-- 2d (pedido de Joel, 17/10/2026): esta ficha nunca se ve en BORRADOR -- siempre
                 muestra el ingreso EVALUADO (congelado al enviar a aprobación), nunca el actual,
                 para que coincida con la afectación de más abajo. --%>
            <br><%= laboralClienteRevision.isPresentaComprobanteIngreso() ? "Ingreso neto comprobado (evaluado al enviar)" : "Ingreso declarado (sin comprobante, evaluado al enviar)" %>:
            <%= FormateadorNumeros.comoGuaranies(solicitudRevision.getIngresoClienteEvaluado()) %>
            <%-- B88 (decisión de Joel, 29/10/2026): abre la ficha en la pestaña del primer paso
                 pendiente (ej. ② Expediente si falta un documento), no siempre en ① Datos -- mismo
                 arreglo que el enlace de la ficha de la solicitud. El ancla la resuelve el dueño de
                 la regla (PasoGuiado.anclaPestanaFicha, vía ControladorAprobacionesServlet.
                 mostrarFicha); vacía = ① Datos, como antes. --%>
            <br><a href="${pageContext.request.contextPath}/clientes/<%= clienteRevision.getIdPersona() %><%= anclaFichaClienteRevision %>">Ver ficha / expediente</a>
        </p>
        <%-- Bloque 7/E10 (D-129, Obs. 26): lo que el gerente necesita para decidir, sin entrar a
             la ficha -- edad, zona, situación laboral, empleador y antigüedad, cuota de otras
             deudas, y el checklist de documentos (mismo criterio que ve el oficial al enviar). --%>
        <hr>
        <p class="mb-2">
            Edad: <strong><%= edadClienteRevision == null ? "-" : edadClienteRevision + " años" %></strong>
            &nbsp;|&nbsp; Zona: <strong><%= departamentoClienteRevision == null ? "-" : departamentoClienteRevision %><%= ciudadClienteRevision == null ? "" : " / " + ciudadClienteRevision %></strong>
            &nbsp;|&nbsp; Situación laboral: <strong><%= py.com.capitalcredito.sgcpc.servicios.ServicioClientes.etiquetaSituacionLaboral(laboralClienteRevision.getSituacionLaboral()) %></strong>
            <br>Empleador: <strong><%= laboralClienteRevision.getEmpleadorNombre() == null ? "-" : laboralClienteRevision.getEmpleadorNombre() %></strong>
            <%= laboralClienteRevision.getAntiguedadLaboralAnios() == null ? "" : " (antigüedad: " + laboralClienteRevision.getAntiguedadLaboralAnios() + " años)" %>
            &nbsp;|&nbsp; Cuota de otras deudas: <strong><%= laboralClienteRevision.getCuotaOtrasDeudasMensual() == null
                ? "-" : FormateadorNumeros.comoGuaranies(laboralClienteRevision.getCuotaOtrasDeudasMensual()) %></strong>
        </p>
        <% if (checklistClienteRevision != null) { %>
        <table class="table table-sm mb-0">
            <tbody>
            <% for (ItemChecklistSolicitud item : checklistClienteRevision) { %>
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
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Condiciones solicitadas y simulación</h5></div>
    <div class="card-body">
        <%
            // RF06 (pedido de Joel, 10/10/2026): ícono ⓘ con el glosario de RF01 -- mismos
            // valores dinámicos que ControladorCotizadorServlet/cotizador/formulario.jsp.
            java.math.BigDecimal topeTeaVigenteRevision = (java.math.BigDecimal) request.getAttribute("topeTeaVigente");
            String topeTeaFormateadoRevision = topeTeaVigenteRevision == null ? "no disponible"
                    : FormateadorNumeros.comoPorcentaje(topeTeaVigenteRevision);
            String tnaFormateadaRevision = FormateadorNumeros.comoPorcentaje(solicitudRevision.getTasaTnaPropuesta());
            String tasaMensualFormateadaRevision = FormateadorNumeros.comoPorcentaje(
                    solicitudRevision.getTasaTnaPropuesta().divide(new java.math.BigDecimal("12"), 6,
                            java.math.RoundingMode.HALF_UP));
            String limiteAfectacionFormateadoRevision = FormateadorNumeros.comoPorcentaje(solicitudRevision.getLimiteAfectacionAplicado());
        %>
        <table class="table table-sm">
            <tr><th>Monto solicitado</th>
                <td><%= FormateadorNumeros.comoGuaranies(solicitudRevision.getMontoSolicitado()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(solicitudRevision.getMontoSolicitado()) %></small></td></tr>
            <tr><th>Plazo</th><td><%= solicitudRevision.getPlazoSolicitadoMeses() %> meses</td></tr>
            <tr><th>Destino</th><td><%= solicitudRevision.getDestinoCredito() %></td></tr>
            <tr><th>Primer vencimiento estimado</th><td><%= FormateadorFechas.comoDdMmAaaa(solicitudRevision.getPrimeraFechaVencimientoSolicitada()) %></td></tr>
            <tr><th>Cuota más alta (con IVA) <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.CUOTA_MAXIMA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.CUOTA_MAXIMA.completo() %>"></i></th>
                <td><strong><%= FormateadorNumeros.comoGuaranies(solicitudRevision.getCuotaSimulada()) %></strong>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(solicitudRevision.getCuotaSimulada()) %></small></td></tr>
            <tr><th>TEA <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TEA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TEA.completo(topeTeaFormateadoRevision) %>"></i>
                    / TNA <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TNA.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TNA.completo(tasaMensualFormateadaRevision, tnaFormateadaRevision) %>"></i></th>
                <td><%= FormateadorNumeros.comoPorcentaje(solicitudRevision.getTasaTeaSimulada()) %>
                    / <%= tnaFormateadaRevision %></td></tr>
            <tr><th>CFT <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.CFT.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.CFT.completo() %>"></i></th>
                <td><%= solicitudRevision.getCftSimulado() == null ? "-" : FormateadorNumeros.comoPorcentaje(solicitudRevision.getCftSimulado()) %></td></tr>
            <tr><th>Total a pagar (con IVA) <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.TOTAL_A_PAGAR.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.TOTAL_A_PAGAR.completo() %>"></i></th>
                <td><%= FormateadorNumeros.comoGuaranies(resultadoRevision.getMontoTotalConIva()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(resultadoRevision.getMontoTotalConIva()) %></small></td></tr>
            <tr class="<%= superaLimiteRevision ? "table-warning" : "table-success" %>">
                <th>Afectación al ingreso <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                        data-glosario-titulo="<%= GlosarioFinanciero.AFECTACION.getTitulo() %>"
                        data-glosario-texto="<%= GlosarioFinanciero.AFECTACION.completo(limiteAfectacionFormateadoRevision) %>"></i></th>
                <%-- Corrección (pedido de Joel, 10/10/2026): un decimal, mismo criterio que la
                     ficha de la solicitud (RF06). --%>
                <td><%= FormateadorNumeros.comoPorcentajeUnDecimal(solicitudRevision.getPorcentajeAfectacion()) %>
                    (límite: <%= limiteAfectacionFormateadoRevision %>)</td>
            </tr>
        </table>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Resultado del buró (Informconf)</h5></div>
    <div class="card-body">
        <% if (listaConsultasRevision == null || listaConsultasRevision.isEmpty()) { %>
        <p class="text-muted">Sin consultas vinculadas.</p>
        <% } else { %>
        <table class="table table-sm">
            <thead><tr><th>Persona</th><th>Fecha</th><th>Puntaje</th><th>Clasificación</th><th>¿Mora?</th><th>Resumen</th><th>Evidencia</th></tr></thead>
            <tbody>
            <%-- RF08 (pedido de Joel, 11/10/2026): cada fila ya viene formateada entera desde
                 ServicioAprobacionCredito.construirFilasConsultaBuro -- incluye, además de la
                 consulta vinculada al enviar, la más reciente de cada persona si es distinta
                 (hallazgo del 11/10/2026: una consulta nueva, registrada después del envío,
                 quedaba invisible), marcada con posteriorAlEnvio. --%>
            <% for (FilaBuroSolicitud consulta : listaConsultasRevision) { %>
            <tr class="<%= consulta.isMora() ? "table-danger" : "" %>">
                <td><%= consulta.getPersonaTexto() %>
                    <%-- Corrección de Joel (11/10/2026): gris en vez de badge-info (que en el
                         tema del sistema se lee como un tono positivo) -- esto no es una buena
                         noticia, es algo que el gerente tiene que notar con cautela. --%>
                    <% if (consulta.isPosteriorAlEnvio()) { %>
                    <span class="badge badge-secondary">Registrada después del envío</span>
                    <% } %></td>
                <td><%= consulta.getFechaFormateada() %></td>
                <td><%= consulta.getPuntajeFormateado() %></td>
                <td><%= consulta.getClasificacion() %></td>
                <td><strong><%= consulta.isMora() ? "SÍ" : "No" %></strong></td>
                <td><%= consulta.getResumen() %></td>
                <td><% if (consulta.getIdDocumentoResultado() != null) { %>
                    <%-- RF03 (pedido de Joel): "Ver" (abre inline, target=_blank), ya no "Descargar". --%>
                    <a href="${pageContext.request.contextPath}/documentos/<%= consulta.getIdDocumentoResultado() %>" class="btn btn-sm btn-info" target="_blank">Ver</a>
                    <% } %></td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
        <%-- RN-18 (pedido de Joel, 17/10/2026): con mora ya no es "revise antes de decidir" --
             es un bloqueo, el aviso lo dice directo y señala la salida (rechazar). Con la
             solicitud ya decidida no tiene sentido pedir que se revise algo que ya se decidió --
             la franja y la tabla siguen mostrándose igual, este aviso puntual sigue condicionado
             a pendiente. --%>
        <% if (pendiente && avisoBuroRevision != null && avisoBuroRevision.isHayMora()) { %>
        <div class="alert alert-danger">El buró indica mora. No se puede aprobar esta solicitud —
            rechácela con el motivo "Informconf: figura con deudas".</div>
        <% } %>
    </div>
</div>

<% if (pendiente) { %>
<div class="mt-3">
    <% boolean bloqueadoPorMora = avisoBuroRevision != null && avisoBuroRevision.isHayMora(); %>
    <% boolean riesgoAltoRevision = avisoBuroRevision != null && avisoBuroRevision.isHayRiesgoAlto(); %>
    <% if (bloqueadoPorMora) { %>
    <%-- RN-18 (pedido de Joel, 17/10/2026): "no se presta a quien figura con deudas en
         Informconf" pasa de confirmación reforzada ("¿Aprobar igualmente?") a bloqueo real --
         el botón se deshabilita y dice por qué (RNF_revision.md §10e). --%>
    <button type="button" class="btn btn-success" disabled title="El buró indica mora: no se puede aprobar.">Aprobar</button>
    <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso H, ⓘ 5): mismo contenido
         que el title="" nativo de arriba, pasado al mecanismo de clic (funciona también en
         pantallas táctiles, donde no hay :hover) -- se deja el title nativo igual, no hace daño. --%>
    <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
       data-glosario-titulo="¿Por qué no puedo aprobar?"
       data-glosario-texto="El buró indica mora vigente del cliente: la política de crédito no permite aprobar en ese caso."></i>
    <% } else { %>
    <form method="post" action="${pageContext.request.contextPath}/aprobaciones/<%= solicitudRevision.getIdSolicitud() %>/aprobar"
          class="d-inline"
          <%-- RF08 (pedido de Joel, 11/10/2026): refuerzo para el riesgo alto D/E sin mora --
               "Sí, aprobar" en vez del "Sí" genérico (data-confirmar-boton, mecanismo de
               pie.jsp). Con mora el formulario ni se renderiza (rama de arriba).
               B43 (B86, tramo transversal, 05/11/2026): el cartel resume cliente, monto, plazo,
               cuota y afectación -- antes preguntaba "¿Aprobar esta solicitud?" sin ningún dato, y
               el gerente decide justamente por esos cuatro números. Cada renglón repite la misma
               expresión que su fila de las tablas de arriba, así que el cartel no recalcula nada;
               el monto y el plazo son los solicitados porque RN-07 aprueba esos y no otros
               (ServicioAprobacionCredito.aprobar:357-359). El refuerzo del riesgo alto pasa a ser
               el TÍTULO, conservando el literal "¿Aprobar igualmente?" que pruebas/humo_http.sh
               verifica ausente en el caso sin riesgo (línea 2082): cambiarlo dejaría esa
               verificación siempre en verde, mirando algo que ya no existe.
               B105 (05/11/2026) dejó este cartel afuera porque aprobar no mueve dinero ni emite un
               documento; Joel decidió tratar su foco acá, con el texto. --%>
          data-confirmar-boton="Sí, aprobar" data-confirmar-foco="cancelar"
          data-confirmar-titulo="<%= riesgoAltoRevision ? "¿Aprobar igualmente?" : "¿Aprobar esta solicitud?" %>"
          data-confirmar="<%= riesgoAltoRevision ? "Informconf indica clasificación de riesgo alto (D/E).&#10;" : "" %>Cliente: <%= clienteRevision.getNombres() %> <%= clienteRevision.getApellidos() %> — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteRevision.getCedulaIdentidad()) %>&#10;Monto: <%= FormateadorNumeros.comoGuaranies(solicitudRevision.getMontoSolicitado()) %> a <%= solicitudRevision.getPlazoSolicitadoMeses() %> meses&#10;Cuota más alta (con IVA): <%= FormateadorNumeros.comoGuaranies(solicitudRevision.getCuotaSimulada()) %>&#10;Afectación al ingreso: <%= FormateadorNumeros.comoPorcentajeUnDecimal(solicitudRevision.getPorcentajeAfectacion()) %> (límite: <%= limiteAfectacionFormateadoRevision %>)">
        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
        <button type="submit" class="btn btn-success">Aprobar</button>
    </form>
    <% } %>
    <%-- B171 (14/11/2026): abre la ventana del final de la página. Antes desplegaba el formulario
         acá mismo (data-toggle="collapse"), debajo de los botones de decisión. --%>
    <button type="button" class="btn btn-danger" data-toggle="modal" data-target="#modalRechazar">Rechazar</button>
</div>
<script>
    // D-126: el detalle es obligatorio solo cuando el motivo elegido lo exige (hoy, "Otro").
    (function () {
        var selectMotivo = document.getElementById('selectMotivoRechazo');
        var textareaDetalle = document.getElementById('textareaDetalleMotivo');
        if (!selectMotivo || !textareaDetalle) { return; }
        function actualizarDetalle() {
            var opcionElegida = selectMotivo.options[selectMotivo.selectedIndex];
            var requiereDetalle = opcionElegida && opcionElegida.getAttribute('data-requiere-detalle') === 'true';
            textareaDetalle.required = requiereDetalle;
            textareaDetalle.placeholder = requiereDetalle ? 'Detalle (obligatorio)' : 'Detalle (opcional)';
            // B171 (14/11/2026): acá estaba la línea que escribía data-confirmar-valor-motivo para
            // que el cartel de B43 nombrara el motivo elegido. Con el rechazo en ventana ya no hace
            // falta -- el motivo se elige y se confirma en la MISMA ventana, a la vista--, así que
            // se borró junto con el data-confirmar: mover una pieza es borrar la vieja, no dejarla
            // (B111). Lo que B43 agregó y SÍ se conserva es que el cliente esté nombrado: ahora lo
            // dice el cuerpo de la ventana.
        }
        selectMotivo.addEventListener('change', actualizarDetalle);
        actualizarDetalle();
    })();
</script>
<% } %>
<% if (cancelable) { %>
<div class="mt-3">
    <%-- B171 (14/11/2026): abre la ventana del final de la página, igual que "Rechazar". --%>
    <button type="button" class="btn btn-outline-danger" data-toggle="modal" data-target="#modalCancelarAprobacion">Cancelar solicitud</button>
</div>
<script>
    // RF06 (pedido de Joel, 10/10/2026): mismo criterio que el rechazo -- el detalle es
    // obligatorio solo cuando el motivo elegido lo exige (hoy, "Otro").
    (function () {
        var selectMotivoCancelacionAprobacion = document.getElementById('selectMotivoCancelacionAprobacion');
        var textareaDetalleCancelacionAprobacion = document.getElementById('textareaDetalleCancelacionAprobacion');
        if (!selectMotivoCancelacionAprobacion || !textareaDetalleCancelacionAprobacion) { return; }
        function actualizarDetalleCancelacionAprobacion() {
            var opcionElegida = selectMotivoCancelacionAprobacion.options[selectMotivoCancelacionAprobacion.selectedIndex];
            var requiereDetalle = opcionElegida && opcionElegida.getAttribute('data-requiere-detalle') === 'true';
            textareaDetalleCancelacionAprobacion.required = requiereDetalle;
            textareaDetalleCancelacionAprobacion.placeholder = requiereDetalle ? 'Detalle (obligatorio)' : 'Detalle (opcional)';
        }
        selectMotivoCancelacionAprobacion.addEventListener('change', actualizarDetalleCancelacionAprobacion);
        actualizarDetalleCancelacionAprobacion();
    })();
</script>
<% } %>

<%-- B171 (14/11/2026): el rechazo y la cancelación, en ventana. Antes los dos desplegaban su
     formulario dentro de la página (data-toggle="collapse"), el mismo patrón que Clientes dejó en
     B155 y Solicitudes en B168. Las ventanas van acá, al final del cuerpo y fuera de las tarjetas,
     por el criterio de mora/_resultados.jsp: un ancestro con transform/filter convertiría el
     position: fixed del modal en relativo a ese ancestro.
     Los formularios son los MISMOS: mismas rutas POST, mismos campos, mismo CSRF y los MISMOS id,
     así que los dos scripts que vuelven obligatorio el detalle cuando el motivo lo exige (D-126 el
     del rechazo, RF06 el de la cancelación) siguen funcionando sin tocarlos.
     NO llevan data-confirmar: la ventana ES la confirmación. Y las dos abren con el foco en
     descartar (autofocus en "No, volver"), porque ninguna de las dos se deshace -- una solicitud
     RECHAZADA no vuelve a pendiente (B43) y la cancelación tampoco (B105, tercer criterio). En una
     ventana de Bootstrap eso es autofocus: no existe el focusCancel de SweetAlert2.
     Lo que B43 agregó al cartel del rechazo y acá se conserva: el cliente, nombrado en el cuerpo.
     El {motivo} que ese cartel inyectaba ya no hace falta, porque el motivo se elige en la misma
     ventana donde se confirma. --%>
<%-- La misma guarda que el botón: "pendiente", que es la que envuelve a Aprobar/Rechazar más
     arriba. Si la solicitud ya se decidió, no hay botón ni ventana. --%>
<% if (pendiente) { %>
<div class="modal fade" id="modalRechazar" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post" action="${pageContext.request.contextPath}/aprobaciones/<%= solicitudRevision.getIdSolicitud() %>/rechazar">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <div class="modal-header">
                    <h5 class="modal-title">¿Rechazar la solicitud <%= solicitudRevision.getCodigoSolicitud() %>?</h5>
                </div>
                <div class="modal-body">
                    <p class="text-muted mb-3"><small>Cliente: <%= clienteRevision.getNombres() %> <%= clienteRevision.getApellidos() %>
                        — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteRevision.getCedulaIdentidad()) %>.
                        Una solicitud rechazada no vuelve a quedar pendiente.</small></p>
                    <div class="form-group">
                        <label>Motivo del rechazo *</label>
                        <select name="idMotivoRechazo" id="selectMotivoRechazo" class="form-control" required>
                            <option value="">Seleccione...</option>
                            <% if (listaMotivosRechazo != null) { for (MotivoRechazoSolicitud motivo : listaMotivosRechazo) { %>
                            <option value="<%= motivo.getIdMotivoRechazo() %>" data-requiere-detalle="<%= motivo.isRequiereDetalle() %>"><%= motivo.getNombre() %></option>
                            <% } } %>
                        </select>
                    </div>
                    <div class="form-group mb-0">
                        <label>Detalle</label>
                        <textarea name="detalleMotivo" id="textareaDetalleMotivo" class="form-control" rows="2"
                                  placeholder="Detalle (opcional)"></textarea>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                    <button type="submit" class="btn btn-danger">Confirmar rechazo</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>
<% if (cancelable) { %>
<div class="modal fade" id="modalCancelarAprobacion" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post" action="${pageContext.request.contextPath}/aprobaciones/<%= solicitudRevision.getIdSolicitud() %>/cancelar">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <div class="modal-header">
                    <h5 class="modal-title">¿Cancelar la solicitud <%= solicitudRevision.getCodigoSolicitud() %>?</h5>
                </div>
                <div class="modal-body">
                    <p class="text-muted mb-3"><small>No se puede deshacer. La solicitud queda cancelada
                        y hay que crear una nueva para volver a evaluar al cliente.</small></p>
                    <div class="form-group">
                        <label>Motivo de la cancelación *</label>
                        <select name="codigoMotivoCancelacion" id="selectMotivoCancelacionAprobacion" class="form-control" required>
                            <option value="">Seleccione...</option>
                            <% if (listaMotivosCancelacion != null) { for (OpcionMotivoCancelacion motivoCancelAprobacion : listaMotivosCancelacion) { %>
                            <option value="<%= motivoCancelAprobacion.getCodigo() %>" data-requiere-detalle="<%= motivoCancelAprobacion.isRequiereDetalle() %>"><%= motivoCancelAprobacion.getEtiqueta() %></option>
                            <% } } %>
                        </select>
                    </div>
                    <div class="form-group mb-0">
                        <label>Detalle</label>
                        <textarea name="detalleCancelacion" id="textareaDetalleCancelacionAprobacion" class="form-control" rows="2"
                                  placeholder="Detalle (opcional)"></textarea>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                    <button type="submit" class="btn btn-outline-danger">Confirmar cancelación</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>

<a href="${pageContext.request.contextPath}/aprobaciones<%= urlVolverBandejaRevision %>" class="btn btn-default mt-3">Volver a la bandeja</a>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
