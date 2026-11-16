<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SesionCaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Desembolso" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    PrestamoCredito prestamoDesembolso = (PrestamoCredito) request.getAttribute("prestamo");
    Persona clienteDesembolso = (Persona) request.getAttribute("cliente");
    boolean yaDesembolsadoFicha = Boolean.TRUE.equals(request.getAttribute("yaDesembolsado"));
    Desembolso desembolsoConfirmadoFicha = (Desembolso) request.getAttribute("desembolsoConfirmado");
    // Bloque 7/E10 (D-130, pedido de Joel): el comprobante se imprime y firma ANTES de entregar
    // el efectivo -- "Confirmar desembolso" se habilita solo después de haberlo impreso.
    boolean comprobanteYaImpresoFicha = Boolean.TRUE.equals(request.getAttribute("comprobanteYaImpreso"));
    Long idTipoComprobanteFirmadoFicha = (Long) request.getAttribute("idTipoComprobanteFirmado");
    py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente comprobanteFirmadoFicha =
            (py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente) request.getAttribute("comprobanteFirmado");
    boolean checklistCompletoDesembolso = Boolean.TRUE.equals(request.getAttribute("checklistCompleto"));
    boolean documentosVigentesDesembolso = Boolean.TRUE.equals(request.getAttribute("documentosVigentes"));
    Integer diasValidezDesembolso = (Integer) request.getAttribute("diasValidezDocumentos");
    SesionCaja sesionAbiertaDesembolso = (SesionCaja) request.getAttribute("sesionAbierta");
    boolean cajaListaDesembolso = Boolean.TRUE.equals(request.getAttribute("cajaLista"));
    String mensajeCajaOtroDiaDesembolso = (String) request.getAttribute("mensajeCajaOtroDia");
    BigDecimal saldoTeoricoDesembolso = (BigDecimal) request.getAttribute("saldoTeorico");
    boolean saldoSuficienteDesembolso = Boolean.TRUE.equals(request.getAttribute("saldoSuficiente"));
    boolean puedeDesembolsarDesembolso = Boolean.TRUE.equals(request.getAttribute("puedeDesembolsar"));
    String identificadorConfirmacionDesembolso = (String) request.getAttribute("identificadorConfirmacion");
    BigDecimal montoAprobadoDesembolso = prestamoDesembolso.getMontoAprobado();
    BigDecimal saldoDespuesDesembolso = cajaListaDesembolso ? saldoTeoricoDesembolso.subtract(montoAprobadoDesembolso) : null;
    BigDecimal faltanteDesembolso = (cajaListaDesembolso && !saldoSuficienteDesembolso)
            ? montoAprobadoDesembolso.subtract(saldoTeoricoDesembolso) : null;
    // RF14 (13/10/2026, pedido de Joel): un ANULADO_ANTES_DESEMBOLSO nunca entregó efectivo --
    // usado para ocultar la fila "Monto entregado en efectivo" (que sería falsa) y para el
    // aviso de la sección "Desembolso" de arriba.
    boolean anuladoSinDesembolsoFicha = prestamoDesembolso.getEstadoPrestamo() == EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO;
    // B189 (15/11/2026): el "Volver a la bandeja" del pie conserva la búsqueda que la cajera
    // tenía. El valor lo arma ControladorDesembolsoServlet.mostrarLista y lo sirve mostrarFicha
    // (RETORNO_BANDEJA_EN_SESION); acá solo se dibuja, ya viene codificado. Vacío = /desembolso,
    // como antes -- también si esta JSP corre contra un .class viejo.
    String urlVolverBandejaDesembolso = (String) request.getAttribute("urlVolverBandeja");
    urlVolverBandejaDesembolso = urlVolverBandejaDesembolso == null || urlVolverBandejaDesembolso.isEmpty()
            ? "" : "?" + urlVolverBandejaDesembolso;
%>
<%-- B190 (15/11/2026): el encabezado también dice PARA QUIÉN es el dinero. Esta es la pantalla
     donde se cuenta y se entrega el efectivo, y el nombre del cliente estaba solo en el resumen,
     más abajo. El N.º de operación sigue primero y sin cambios (es lo que busca el humo en la
     bandeja y lo que la cajera canta en voz alta); el nombre va después, con el mismo separador
     "—" que usa el resto del sistema. Mismo criterio que B161: un aviso dice para quién es. --%>
<h1><%= prestamoDesembolso.getIdentificadorOperacion() %>
    — <%= clienteDesembolso.getNombres() %> <%= clienteDesembolso.getApellidos() %></h1>

<%-- E8 (Bloque 2, D-94): reimpresión del comprobante de desembolso ya confirmado. Corrección de
     Joel: si el préstamo ya se desembolsó, este bloque muestra el dato real (fecha y N° de
     comprobante) en vez de repetir "Condiciones para desembolsar" más abajo. --%>
<% if (yaDesembolsadoFicha) { %>
<div class="card">
    <div class="card-header"><h5 class="m-0">Desembolso</h5></div>
    <div class="card-body">
        <% if (desembolsoConfirmadoFicha != null) { %>
        <p class="mb-2">Desembolsado el <%= FormateadorFechas.comoDdMmAaaaHhMm(desembolsoConfirmadoFicha.getFechaHoraDesembolso()) %>
            — <%= desembolsoConfirmadoFicha.getNumeroComprobante() %></p>
        <a class="btn btn-outline-primary" target="_blank"
           href="${pageContext.request.contextPath}/desembolso/<%= prestamoDesembolso.getIdPrestamo() %>/comprobante">Reimprimir comprobante</a>
        <small class="text-muted d-block mt-1">Cada reimpresión queda marcada y registrada en Auditoría.</small>
        <%-- Bloque 7/E10 (D-130, pedido de Joel): escaneo firmado, opcional, mismo patrón que
             contrato/pagaré/constancia de devolución (carga genérica en /documentos/cargar). --%>
        <hr>
        <% if (comprobanteFirmadoFicha != null) { %>
        <%-- RF03 (pedido de Joel): "ver" (abre inline, target=_blank), ya no "descargar". --%>
        <p class="mb-0 text-success">✓ Escaneo firmado cargado
            (<a href="${pageContext.request.contextPath}/documentos/<%= comprobanteFirmadoFicha.getIdDocumento() %>" target="_blank">ver</a>).</p>
        <% } else { %>
        <form method="post" action="${pageContext.request.contextPath}/documentos/cargar" enctype="multipart/form-data" class="form-inline">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <input type="hidden" name="idPrestamo" value="<%= prestamoDesembolso.getIdPrestamo() %>">
            <input type="hidden" name="idTipoDocumento" value="<%= idTipoComprobanteFirmadoFicha %>">
            <%-- La cajera no tiene acceso a /formalizacion (ese sería el destino por defecto) --
                 volverA la trae de vuelta a su propia ficha de desembolso, mismo patrón que la
                 constancia de devolución firmada desde /formalizacion/{id}/cierre-documental. --%>
            <input type="hidden" name="volverA" value="/desembolso/<%= prestamoDesembolso.getIdPrestamo() %>">
            <label class="mr-2 mb-0">Comprobante firmado (escaneo, opcional):</label>
            <input type="file" name="archivo" class="form-control-file mr-2" accept="application/pdf,image/*" required>
            <button type="submit" class="btn btn-sm btn-outline-secondary">Cargar</button>
        </form>
        <% } %>
        <%-- RF14 (13/10/2026, pedido de Joel): un préstamo ANULADO_ANTES_DESEMBOLSO cae en esta
             misma rama (yaDesembolsadoFicha=true, sin desembolsoConfirmado) -- antes mostraba el
             mismo texto que "todavía no se generó el comprobante", que es falso acá: nunca se va
             a generar, el préstamo está anulado. --%>
        <% } else if (anuladoSinDesembolsoFicha) { %>
        <div class="alert alert-secondary mb-0">Este préstamo fue anulado antes del desembolso<%
            if (prestamoDesembolso.getFechaAnulacionSinDesembolso() != null) { %> el <%=
            FormateadorFechas.comoDdMmAaaaHhMm(prestamoDesembolso.getFechaAnulacionSinDesembolso()) %><% } %>.</div>
        <% } else { %>
        <p class="mb-0 text-muted">Este préstamo no tiene comprobante de desembolso registrado.</p>
        <% } %>
    </div>
</div>
<% } %>

<div class="card">
    <%-- RF14 (14/10/2026, pedido de Joel, Tanda 2): un prestamo ANULADO_ANTES_DESEMBOLSO nunca
         tuvo un desembolso que resumir -- el titulo pasa a ser generico. --%>
    <div class="card-header"><h5 class="m-0"><%= anuladoSinDesembolsoFicha ? "Resumen del préstamo" : "Resumen del desembolso" %></h5></div>
    <div class="card-body">
        <table class="table table-sm">
            <tr><th>Cliente</th>
                <td><%= clienteDesembolso.getNombres() %> <%= clienteDesembolso.getApellidos() %>
                    — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteDesembolso.getCedulaIdentidad()) %></td></tr>
            <tr><th>Monto aprobado</th>
                <td><%= FormateadorNumeros.comoGuaranies(montoAprobadoDesembolso) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(montoAprobadoDesembolso) %></small></td></tr>
            <tr><th>Deducciones</th><td><%= FormateadorNumeros.comoGuaranies(BigDecimal.ZERO) %></td></tr>
            <%-- RF14 (13/10/2026, pedido de Joel): un préstamo anulado antes del desembolso
                 nunca entregó efectivo -- "Monto entregado en efectivo" sería falso acá, y
                 "Monto aprobado" ya se muestra arriba, así que esta fila se oculta entera. --%>
            <% if (!anuladoSinDesembolsoFicha) { %>
            <tr><th><%= yaDesembolsadoFicha ? "Monto entregado en efectivo" : "Monto neto a entregar en efectivo" %></th>
                <td><strong><%= FormateadorNumeros.comoGuaranies(montoAprobadoDesembolso) %></strong></td></tr>
            <% } %>
            <% if (cajaListaDesembolso) { %>
            <tr><th>Caja: saldo teórico antes</th><td><%= FormateadorNumeros.comoGuaranies(saldoTeoricoDesembolso) %></td></tr>
            <tr><th>Caja: saldo teórico después</th>
                <td>
                    <% if (saldoDespuesDesembolso.signum() < 0) { %>
                    <span class="text-danger font-weight-bold">Faltan <%= FormateadorNumeros.comoGuaranies(saldoDespuesDesembolso.negate()) %></span>
                    <% } else { %>
                    <%= FormateadorNumeros.comoGuaranies(saldoDespuesDesembolso) %>
                    <% } %>
                </td></tr>
            <% } %>
        </table>
    </div>
</div>

<% if (!yaDesembolsadoFicha) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Condiciones para desembolsar</h5></div>
    <div class="card-body">
        <ul class="list-unstyled mb-2">
            <li><% if (checklistCompletoDesembolso) { %><span class="badge badge-success">✓</span>
                <% } else { %><span class="badge badge-danger">Pendiente</span><% } %> Checklist de documentos firmados completo</li>
            <li><% if (documentosVigentesDesembolso) { %><span class="badge badge-success">✓</span>
                <% } else { %><span class="badge badge-danger">Vencido</span><% } %> Documentos generados vigentes
                (validez: <%= diasValidezDesembolso %> días desde la última emisión)</li>
            <li><% if (cajaListaDesembolso) { %><span class="badge badge-success">✓</span>
                <% } else { %><span class="badge badge-danger">No disponible</span><% } %> Caja abierta y lista para operar hoy</li>
            <li><% if (saldoSuficienteDesembolso) { %><span class="badge badge-success">✓</span> Saldo suficiente en caja
                <% } else { %><span class="badge badge-danger">Insuficiente</span> Saldo suficiente en caja
                <% if (faltanteDesembolso != null) { %> — faltan <%= FormateadorNumeros.comoGuaranies(faltanteDesembolso) %><% } %>
                <% } %></li>
        </ul>
        <% if (!documentosVigentesDesembolso) { %>
        <div class="alert alert-warning">Los documentos generados están vencidos o no existen. Regénerelos antes de desembolsar.
            <a href="${pageContext.request.contextPath}/formalizacion/<%= prestamoDesembolso.getIdPrestamo() %>">Ir a formalización</a></div>
        <% } %>
        <% if (!checklistCompletoDesembolso) { %>
        <div class="alert alert-warning">Faltan documentos firmados cargados.
            <a href="${pageContext.request.contextPath}/formalizacion/<%= prestamoDesembolso.getIdPrestamo() %>">Ir a formalización</a></div>
        <% } %>
        <% if (sesionAbiertaDesembolso == null) { %>
        <div class="alert alert-warning">No hay caja abierta.
            <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto y color (alert-warning,
             no alert-danger -- A.8, bloqueo de tiempo, no error) que Caja/Cobranza. --%>
        <% } else if (!cajaListaDesembolso) { %>
        <div class="alert alert-warning"><%= mensajeCajaOtroDiaDesembolso %> <a href="${pageContext.request.contextPath}/caja">Ir a caja</a></div>
        <% } else if (!saldoSuficienteDesembolso) { %>
        <div class="alert alert-warning">Faltan <%= FormateadorNumeros.comoGuaranies(faltanteDesembolso) %> en caja.
            Pida al gerente que entregue efectivo.</div>
        <% } %>
    </div>
</div>


<%-- Bloque 7/E10 (D-130, pedido de Joel, prioridad máxima): el comprobante se imprime y firma
     ANTES de entregar el efectivo -- primero el botón de impresión, recién después el checkbox
     y "Confirmar desembolso" (deshabilitado hasta haber impreso al menos una vez).
     A.5 (consolidación, pedido de Joel): el botón de impresión necesita las mismas 4 condiciones
     que "Confirmar desembolso" -- antes se podía imprimir el comprobante "para firma" sin
     cumplirlas (el servidor ya las exige ahora, ver ServicioDesembolso.asegurarCondicionesDesembolso;
     esto es solo para no mostrar el botón cuando de todos modos va a rechazarlo). --%>
<% if (puedeDesembolsarDesembolso) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Comprobante de desembolso</h5></div>
    <div class="card-body">
        <p class="text-muted">Imprima el comprobante, haga firmar al cliente y recién después confirme el desembolso.</p>
        <a id="botonImprimirComprobante" class="btn btn-outline-primary" target="_blank"
           href="${pageContext.request.contextPath}/desembolso/<%= prestamoDesembolso.getIdPrestamo() %>/comprobante"
           onclick="document.getElementById('confirmoEntregaEfectivoCheck').disabled = false;
                    var avisoImpresion = document.getElementById('avisoComprobanteCheck');
                    if (avisoImpresion) { avisoImpresion.textContent = '✓ Comprobante impreso'; avisoImpresion.className = 'form-text text-success'; }">
            <i class="fas fa-print"></i> Imprimir comprobante para firma
        </a>
        <% if (comprobanteYaImpresoFicha) { %>
        <span class="text-success ml-2">✓ Comprobante N.º <%= prestamoDesembolso.getNumeroComprobanteDesembolso() %> ya impreso</span>
        <% } %>
    </div>
</div>
<% } else { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Comprobante de desembolso</h5></div>
    <div class="card-body">
        <p class="text-muted mb-0">No se puede imprimir todavía: complete las condiciones de la sección
            "Condiciones para desembolsar" de arriba.</p>
    </div>
</div>
<% } %>

<% if (puedeDesembolsarDesembolso) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Confirmar desembolso</h5></div>
    <div class="card-body">
        <form method="post" action="${pageContext.request.contextPath}/desembolso/<%= prestamoDesembolso.getIdPrestamo() %>/confirmar"
              data-confirmar-titulo="¿Confirmar el desembolso de <%= FormateadorNumeros.comoGuaranies(montoAprobadoDesembolso) %>?" data-confirmar=""
              data-confirmar-foco="cancelar" data-confirmar-boton="Sí, desembolsar">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <input type="hidden" name="identificadorConfirmacion" value="<%= identificadorConfirmacionDesembolso %>">
            <div class="form-check mb-3">
                <input type="checkbox" class="form-check-input" id="confirmoEntregaEfectivoCheck" required
                       <%= comprobanteYaImpresoFicha ? "" : "disabled" %>
                       onchange="document.getElementById('botonConfirmarDesembolso').disabled = !this.checked;
                                 document.getElementById('confirmoEntregaEfectivoOculto').value = this.checked ? 'true' : 'false';">
                <label class="form-check-label" for="confirmoEntregaEfectivoCheck">
                    El cliente firmó el comprobante, verifiqué su cédula y le entregué
                    <%= FormateadorNumeros.comoGuaranies(montoAprobadoDesembolso) %> en efectivo.
                </label>
                <% if (comprobanteYaImpresoFicha) { %>
                <small class="form-text text-success" id="avisoComprobanteCheck">✓ Comprobante impreso</small>
                <% } else { %>
                <%-- B87 (28/10/2026, dentro de B86): el motivo de un control deshabilitado se ve
                     igual en todo el sistema (.motivo-deshabilitado, sgcpc-tema.css §2b). El texto
                     es el mismo de antes -- solo cambia cómo se ve. --%>
                <small class="form-text motivo-deshabilitado" id="avisoComprobanteCheck">Imprima el comprobante (arriba) para habilitar esta casilla.</small>
                <% } %>
            </div>
            <input type="hidden" id="confirmoEntregaEfectivoOculto" name="confirmoEntregaEfectivo" value="false">
            <button type="submit" id="botonConfirmarDesembolso" class="btn btn-primary" disabled>Confirmar desembolso</button>
        </form>
    </div>
</div>
<% } %>
<% } %>

<a href="${pageContext.request.contextPath}/desembolso<%= urlVolverBandejaDesembolso %>" class="btn btn-default mt-3">Volver a la bandeja</a>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
