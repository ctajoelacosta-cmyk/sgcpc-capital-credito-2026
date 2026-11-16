<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    PrestamoCredito prestamoCierre = (PrestamoCredito) request.getAttribute("prestamo");
    if (prestamoCierre != null) {
%>
<%
    Persona clienteCierre = (Persona) request.getAttribute("cliente");
    DocumentoExpediente docGeneradoCierre = (DocumentoExpediente) request.getAttribute("docGenerado");
    DocumentoExpediente docFirmadoCierre = (DocumentoExpediente) request.getAttribute("docFirmado");
    Long idTipoFirmadoCierre = (Long) request.getAttribute("idTipoDevolucionFirmado");
    String nombreCierreDocumentalPorCierre = (String) request.getAttribute("nombreCierreDocumentalPor");
    boolean cierreCompletoCierre = "COMPLETO".equals(prestamoCierre.getEstadoCierreDocumental());
    // B85/B77 (28/10/2026, hallazgo de Joel en la prueba de B76, aprobado el mismo día): antes
    // cada card aparecía recién cuando la anterior estaba resuelta, así que el oficial no sabía
    // de entrada que el trámite son 3 pasos. Ahora las 3 están siempre visibles y las que
    // todavía no se pueden hacer salen deshabilitadas con el motivo; esta variable marca cuál
    // es el paso que toca ahora (solo presentación: no cambia ninguna condición del servidor,
    // que sigue siendo "completarCierreDocumental" quien rechaza sin la constancia firmada).
    int pasoActualCierre = docGeneradoCierre == null ? 1 : (docFirmadoCierre == null ? 2 : 3);
    // B112 (10/11/2026): los dos datos del cartel del paso 1 que no son un getter directo.
    // El nombre sale del usuario de la sesión, que declara plantilla/cabecera.jsp (incluido
    // arriba), con la misma guarda de null que usa esa plantilla -- es el nombre que la
    // constancia imprime como quien entrega el pagaré (B78).
    String nombreEntregaCierre = usuarioSesion == null ? "-"
            : usuarioSesion.getNombres() + " " + usuarioSesion.getApellidos();
    // La fecha es la MISMA que muestra el encabezado de esta pantalla, no la que recalcula
    // el servicio: un cartel repite lo que el usuario acaba de leer.
    String canceladaElCierre = prestamoCierre.getFechaFinalizacionFinanciera() == null ? ""
            : ", cancelada el " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas
                    .comoDdMmAaaa(prestamoCierre.getFechaFinalizacionFinanciera().toLocalDate());
%>
<%-- B85: el título dice el trámite, no el nombre interno del estado ("Cierre documental" no se
     entendía). Queda alineado con el nombre por el que ya se la llama en el menú (módulo
     Cancelación -> "Devolución del pagaré") y en la tarjeta del panel del oficial (B76). --%>
<h1>Devolución del pagaré — <%= prestamoCierre.getIdentificadorOperacion() %></h1>
<p class="text-muted"><a href="${pageContext.request.contextPath}/formalizacion/<%= prestamoCierre.getIdPrestamo() %>">&laquo; Ficha del préstamo</a></p>
<p class="mb-1"><strong><%= clienteCierre.getNombres() %> <%= clienteCierre.getApellidos() %></strong></p>
<% if (prestamoCierre.getFechaFinalizacionFinanciera() != null) { %>
<p class="text-muted">Préstamo cancelado el
    <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(prestamoCierre.getFechaFinalizacionFinanciera().toLocalDate()) %></p>
<% } %>

<% if (!"FINALIZADO".equals(prestamoCierre.getEstadoPrestamo().name())) { %>
<%-- B85: el aviso también en el vocabulario nuevo. Antes: "Este préstamo todavía no está
     finalizado. El cierre documental (devolución del pagaré) recién corresponde una vez
     cancelado en su totalidad." --%>
<div class="alert alert-warning">Este préstamo todavía no está cancelado. La devolución del pagaré recién
    corresponde una vez que el cliente haya pagado la totalidad de su deuda.</div>
<% } else { %>

<% if (cierreCompletoCierre) { %>
<%-- B85: antes acá solo decía "COMPLETO — fecha por quién", el nombre del estado interno. --%>
<div class="alert alert-success"><strong>Préstamo cerrado.</strong> El pagaré original quedó devuelto al cliente el
    <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaaHhMm(prestamoCierre.getFechaCierreDocumental()) %>,
    registrado por <%= nombreCierreDocumentalPorCierre == null ? "-" : nombreCierreDocumentalPorCierre %>.
    No queda nada pendiente de esta operación.</div>
<% } else { %>
<%-- B85: la explicación de para qué sirve la pantalla, para que un empleado nuevo sepa qué hacer. --%>
<div class="alert alert-info"><strong>El cliente ya no debe nada.</strong> Para cerrar el préstamo, devuélvale el
    pagaré original y deje constancia en 3 pasos.</div>
<% } %>

<div class="card">
    <div class="card-header d-flex align-items-center justify-content-between flex-wrap">
        <h5 class="m-0">Paso 1 de 3 — Generar la constancia de devolución</h5>
        <% if (!cierreCompletoCierre && pasoActualCierre == 1) { %>
        <span class="badge badge-primary">Paso actual</span>
        <% } %>
    </div>
    <div class="card-body">
        <% if (docGeneradoCierre == null) { %>
        <p class="text-muted">Todavía no se generó.</p>
        <%-- B78 (aviso aprobado por Joel el 28/10/2026): la constancia lleva impreso el nombre de
             quien la genera como quien entrega el pagaré, así que conviene que el oficial lo sepa
             antes de generarla. --%>
        <p class="text-muted">La constancia sale a su nombre como quien entrega el pagaré.</p>
        <%-- B112 (observado el 25/10/2026 en la tercera pasada de recorrido/04_formalizacion.md,
             numerado el 05/11/2026 en el inventario de B105, aprobado por Joel el 10/11/2026):
             este paso emitía el DEV-aaaa-nnnnnn sin preguntar nada -- era el único <form> de la
             pantalla sin cartel, y por eso B105 no lo pudo cubrir: no se le pone el foco a un
             cartel que no existe. Usa el mecanismo que ya trae plantilla/pie.jsp; no se toca
             ServicioFormalizacion.generarConstanciaDevolucion.
             Tres cosas que el texto dice a propósito:
             - NO promete el número (B4/B43): el DEV lo asigna numeradorDAO.siguienteValor DENTRO
               de la transacción, al confirmar. Dice que la constancia "recibe su número".
             - Nombra al oficial, que es lo que queda fijo para siempre. El servicio es
               idempotente (solo numera si numero_constancia_devolucion es null y solo emite el
               PDF si no hay uno vigente), así que un segundo clic no numera de nuevo; lo que no
               se puede cambiar desde esta pantalla es el nombre impreso y la fecha. Es el mismo
               aviso de B78 que ya está arriba del botón, repetido donde se decide.
             - No lleva el N.º de pagaré: en estos préstamos es igual al identificador de
               operación, así que sería el mismo dato dos veces.
             Foco en "No, volver" por B105 (emite un documento numerado). El mensaje va en UNA
             sola línea de código: los saltos se escriben &#10; dentro del atributo -- un salto
             real en el fuente también entraría al texto (B4/B43). --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoCierre.getIdPrestamo() %>/generar-constancia-devolucion"
              data-confirmar="Cliente: <%= clienteCierre.getNombres() %> <%= clienteCierre.getApellidos() %> — C.I. <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoCedulaConPuntos(clienteCierre.getCedulaIdentidad()) %>&#10;Operación: <%= prestamoCierre.getIdentificadorOperacion() %><%= canceladaElCierre %>&#10;Entrega el pagaré: <%= nombreEntregaCierre %> — su nombre sale impreso en la constancia&#10;Se emite la constancia en PDF y recibe su número. Una vez generada, el número y el nombre impreso ya no se cambian desde esta pantalla."
              data-confirmar-titulo="¿Generar la constancia de devolución?"
              data-confirmar-boton="Sí, generar la constancia"
              data-confirmar-foco="cancelar">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <button type="submit" class="btn btn-primary">Generar la constancia de devolución</button>
        </form>
        <% } else { %>
        <%-- RF03 (pedido de Joel): "Ver" (abre inline, target=_blank), ya no "Ver/Descargar PDF" --
             mismo texto unificado en todas las pantallas que sirven /documentos/{id}. --%>
        <p>N.º <strong><%= prestamoCierre.getNumeroConstanciaDevolucion() %></strong> —
            <a href="${pageContext.request.contextPath}/documentos/<%= docGeneradoCierre.getIdDocumento() %>" target="_blank">Ver</a></p>
        <% if (!cierreCompletoCierre) { %>
        <p class="text-muted mb-0">Imprima este PDF, hágalo firmar por el cliente al entregarle el pagaré original,
            y cargue la constancia firmada en el paso 2.</p>
        <% } %>
        <% } %>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header d-flex align-items-center justify-content-between flex-wrap">
        <h5 class="m-0 <%= docGeneradoCierre == null ? "text-muted" : "" %>">Paso 2 de 3 — Cargar la constancia firmada por el cliente</h5>
        <% if (!cierreCompletoCierre && pasoActualCierre == 2) { %>
        <span class="badge badge-primary">Paso actual</span>
        <% } %>
    </div>
    <div class="card-body">
        <% if (docGeneradoCierre == null) { %>
        <%-- B77: visible pero deshabilitado, con el motivo -- antes esta card no existía todavía.
             Mismo patrón que el botón "Aprobar" deshabilitado de aprobaciones/ficha.jsp. --%>
        <p class="motivo-deshabilitado">Primero genere la constancia (paso 1).</p>
        <button type="button" class="btn btn-outline-primary" disabled
                title="Primero genere la constancia (paso 1).">Cargar la constancia firmada</button>
        <% } else { %>
        <% if (docFirmadoCierre != null) { %>
        <p><a href="${pageContext.request.contextPath}/documentos/<%= docFirmadoCierre.getIdDocumento() %>" target="_blank">Ver</a>
            (cargado <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(docFirmadoCierre.getFechaDocumento()) %>)</p>
        <% } else { %>
        <p class="text-muted">Todavía no se cargó la constancia firmada.</p>
        <% } %>
        <% if (!cierreCompletoCierre) { %>
        <form method="post" action="${pageContext.request.contextPath}/documentos/cargar" enctype="multipart/form-data" class="form-inline">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <input type="hidden" name="idPrestamo" value="<%= prestamoCierre.getIdPrestamo() %>">
            <input type="hidden" name="idTipoDocumento" value="<%= idTipoFirmadoCierre %>">
            <input type="hidden" name="volverA" value="/formalizacion/<%= prestamoCierre.getIdPrestamo() %>/cierre-documental">
            <input type="file" name="archivo" class="form-control-file mr-2" aria-label="la constancia firmada" required>
            <button type="submit" class="btn btn-outline-primary">Cargar la constancia firmada</button>
        </form>
        <% } %>
        <% } %>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header d-flex align-items-center justify-content-between flex-wrap">
        <h5 class="m-0 <%= !cierreCompletoCierre && docFirmadoCierre == null ? "text-muted" : "" %>">Paso 3 de 3 — Marcar el préstamo como cerrado</h5>
        <% if (!cierreCompletoCierre && pasoActualCierre == 3) { %>
        <span class="badge badge-primary">Paso actual</span>
        <% } %>
    </div>
    <div class="card-body">
        <% if (cierreCompletoCierre) { %>
        <%-- B77: la card queda visible después de cerrar (el mapa de los 3 pasos no desaparece),
             pero sin el botón: el cierre no se puede deshacer. --%>
        <p class="text-muted mb-0">Hecho el
            <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaaHhMm(prestamoCierre.getFechaCierreDocumental()) %>
            por <%= nombreCierreDocumentalPorCierre == null ? "-" : nombreCierreDocumentalPorCierre %>.</p>
        <% } else if (docFirmadoCierre == null) { %>
        <p class="motivo-deshabilitado">Primero cargue la constancia firmada (paso 2).</p>
        <button type="button" class="btn btn-success" disabled
                title="Primero cargue la constancia firmada (paso 2).">Marcar el préstamo como cerrado</button>
        <% } else { %>
        <%-- B85 (28/10/2026, aprobado por Joel): la confirmación también en el vocabulario de la
             pantalla. Antes: "¿Completar el cierre documental?" / "Sí, completar cierre"
             (probado el 13/10/2026, reemplazado a pedido de Joel).
             B179 (14/11/2026): el cuerpo decía solo "No se puede deshacer." -- sin nombrar la
             operación ni el cliente, al revés que el paso 1 de esta misma pantalla. Ahora repite
             las MISMAS expresiones del paso 1 (mismos getters, mismo formateador, mismo
             canceladaElCierre): un cartel repite lo que el usuario acaba de leer, no recalcula
             nada (B4/B43). El "No se puede deshacer." se queda, como última línea y junto a lo
             que sí queda hecho. Los saltos van como &#10; dentro del atributo -- un salto real en
             el fuente también entraría al texto. El foco sigue en "No, volver" (B105). --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoCierre.getIdPrestamo() %>/completar-cierre-documental"
              data-confirmar-titulo="¿Marcar el préstamo como cerrado?"
              data-confirmar="Cliente: <%= clienteCierre.getNombres() %> <%= clienteCierre.getApellidos() %> — C.I. <%= py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoCedulaConPuntos(clienteCierre.getCedulaIdentidad()) %>&#10;Operación: <%= prestamoCierre.getIdentificadorOperacion() %><%= canceladaElCierre %>&#10;El préstamo queda cerrado y la devolución del pagaré, terminada. No se puede deshacer."
              data-confirmar-foco="cancelar" data-confirmar-boton="Sí, cerrar el préstamo">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <button type="submit" class="btn btn-success">Marcar el préstamo como cerrado</button>
        </form>
        <% } %>
    </div>
</div>

<% } %>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
