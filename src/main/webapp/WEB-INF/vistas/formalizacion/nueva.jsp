<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Feriado" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    SolicitudCredito solicitudNueva = (SolicitudCredito) request.getAttribute("solicitud");
    Persona clienteNuevaFormalizacion = (Persona) request.getAttribute("cliente");
    LocalDate propuestaNueva = (LocalDate) request.getAttribute("primerVencimientoPropuesto");
    // D.3 (reprueba Lote 1, pedido de Joel): si el rechazo anterior fue por fecha fuera de
    // plazo, mostrar lo que el usuario había elegido -- no la propuesta por defecto, como si no
    // hubiera escrito nada.
    @SuppressWarnings("unchecked")
    Map<String, String> valoresFormularioNueva = (Map<String, String>) request.getAttribute("valoresFormulario");
    String primerVencimientoPrevioNueva = valoresFormularioNueva != null
            ? valoresFormularioNueva.get("primerVencimiento") : null;
    String valorCampoPrimerVencimientoNueva = primerVencimientoPrevioNueva != null && !primerVencimientoPrevioNueva.isEmpty()
            ? primerVencimientoPrevioNueva : String.valueOf(propuestaNueva);
    ServicioFormalizacion.RangoFechas rangoFechasNueva = (ServicioFormalizacion.RangoFechas) request.getAttribute("rangoFechas");
    ServicioFormalizacion.ResumenFormalizacion resumenNueva =
            (ServicioFormalizacion.ResumenFormalizacion) request.getAttribute("resumenFormalizacion");
    @SuppressWarnings("unchecked")
    List<Feriado> feriadosNueva = (List<Feriado>) request.getAttribute("feriados");
    StringBuilder feriadosJsonNueva = new StringBuilder("[");
    if (feriadosNueva != null) {
        for (int i = 0; i < feriadosNueva.size(); i++) {
            if (i > 0) { feriadosJsonNueva.append(','); }
            Feriado feriadoJsonNueva = feriadosNueva.get(i);
            feriadosJsonNueva.append("{\"fecha\":\"").append(feriadoJsonNueva.getFecha())
                    .append("\",\"descripcion\":\"").append(feriadoJsonNueva.getDescripcion().replace("\"", "\\\""))
                    .append("\"}");
        }
    }
    feriadosJsonNueva.append(']');
    // B180 (14/11/2026): el "Volver a la bandeja" del pie conserva la búsqueda que el usuario
    // tenía. El valor lo arma ControladorFormalizacionServlet.mostrarLista y lo sirve
    // mostrarNueva (RETORNO_BANDEJA_EN_SESION); acá solo se dibuja, ya viene codificado. Vacío =
    // /formalizacion, como antes -- también si esta JSP corre contra un .class viejo.
    String urlVolverBandejaNueva = (String) request.getAttribute("urlVolverBandeja");
    urlVolverBandejaNueva = urlVolverBandejaNueva == null || urlVolverBandejaNueva.isEmpty()
            ? "" : "?" + urlVolverBandejaNueva;
%>
<h1>Formalizar <%= solicitudNueva.getCodigoSolicitud() %></h1>

<div class="card">
    <div class="card-header"><h5 class="m-0">Condiciones aprobadas (no editables)</h5></div>
    <div class="card-body">
        <table class="table table-sm">
            <tr><th>Cliente</th>
                <td><%= clienteNuevaFormalizacion.getNombres() %> <%= clienteNuevaFormalizacion.getApellidos() %>
                    — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteNuevaFormalizacion.getCedulaIdentidad()) %></td></tr>
            <tr><th>Monto aprobado</th>
                <td><%= FormateadorNumeros.comoGuaranies(solicitudNueva.getMontoAprobado()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(solicitudNueva.getMontoAprobado()) %></small></td></tr>
            <tr><th>Plazo</th><td><%= solicitudNueva.getPlazoAprobadoMeses() %> meses</td></tr>
            <tr><th>TEA / TNA</th>
                <td><%= FormateadorNumeros.comoPorcentaje(solicitudNueva.getTasaTeaAprobada()) %>
                    / <%= FormateadorNumeros.comoPorcentaje(solicitudNueva.getTasaTnaAprobada()) %></td></tr>
            <%-- RF11 (pedido de Joel, 11/10/2026): cuota más alta, total y CFT no cambian según la
                 fecha del primer vencimiento que se elija -- la afectación sí puede diferir de la
                 de la solicitud porque se calcula con los datos ACTUALES del cliente. --%>
            <tr><th>Cuota más alta</th><td><%= FormateadorNumeros.comoGuaranies(resumenNueva.getCuotaMasAlta()) %></td></tr>
            <tr><th>Total a pagar</th><td><%= FormateadorNumeros.comoGuaranies(resumenNueva.getMontoTotalConIva()) %></td></tr>
            <tr><th>CFT</th><td><%= FormateadorNumeros.comoPorcentaje(resumenNueva.getCft()) %></td></tr>
            <tr><th>Afectación actual</th>
                <td class="<%= resumenNueva.isSuperaLimite() ? "text-danger font-weight-bold" : "" %>">
                    <%= FormateadorNumeros.comoPorcentajeUnDecimal(resumenNueva.getPorcentajeAfectacion()) %>
                    (límite <%= FormateadorNumeros.comoPorcentajeUnDecimal(resumenNueva.getLimiteAfectacion()) %>)
                    <% if (resumenNueva.isSuperaLimite()) { %>
                        — supera el límite: no se podrá formalizar hasta que la situación del cliente lo permita.
                    <% } %>
                </td></tr>
            <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RF11_formalizacion.md §10): mismo
                 criterio que "Afectación actual" de arriba -- rojo desde el principio, sin
                 deshabilitar "Generar documentos" (el bloqueo real sigue siendo el del servidor). --%>
            <% if (resumenNueva.getMensajeSinComprobante() != null) { %>
            <tr><th>Comprobante de ingreso</th><td class="text-danger font-weight-bold"><%= resumenNueva.getMensajeSinComprobante() %></td></tr>
            <% } %>
        </table>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Generar documentos</h5></div>
    <div class="card-body">
        <p class="text-muted">La fecha aprobada en la solicitud puede haber quedado desactualizada si se formaliza
            días después. Se propone el primer vencimiento un mes desde hoy; puede ajustarla dentro del rango permitido.</p>
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7 paso G, variante G2): si la
             pantalla YA muestra un bloqueo seguro -- supera el límite de afectación O no presenta
             comprobante de ingreso (los únicos 2 que esta JSP conoce de antemano, RF11_
             formalizacion.md §10) --, el servidor va a rechazar el envío de todos modos -- se
             saca la pregunta de confirmación para que vaya directo a mostrar ese bloqueo, sin la
             pregunta previa de en el medio. El botón sigue habilitado (mismo criterio que el
             comentario de arriba, 19/10/2026: el bloqueo real es del servidor). Otras causas de
             rechazo de generarDocumentos (tope de tasa vigente cambiado, fecha de primer
             vencimiento fuera de rango, solicitud ya formalizada) no tienen ningún dato
             equivalente en ResumenFormalizacion -- esta pantalla no las puede anticipar, quedan
             con la confirmación de siempre. --%>
        <%-- B182 (14/11/2026): la condición de arriba ("supera el límite O no presenta comprobante")
             ya no se escribe acá: la resuelve resumenNueva.hayBloqueoConocido(), el mismo dueño
             que alimenta el renglón de motivos de abajo. --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/nueva/<%= solicitudNueva.getIdSolicitud() %>/generar"
              <% if (!resumenNueva.hayBloqueoConocido()) { %>
              <%-- B4 (B86, tramo transversal, 05/11/2026): la confirmación resume QUÉ VA A PASAR,
                   no pregunta "¿con estas condiciones?" sin decir cuáles. Cada renglón repite la
                   misma expresión que su fila de la tabla de arriba (mismo getter, mismo
                   formateador): el cartel no recalcula ni redondea nada.
                   El primer vencimiento va como {fecha} porque el EFECTIVO depende de lo que el
                   oficial tenga puesto en el campo de abajo, que el servidor no conoce al dibujar
                   la pantalla: recursos/js/fecha-primer-vencimiento.js escribe SOLO ese valor en
                   data-confirmar-valor-fecha y plantilla/pie.jsp lo reemplaza (regla de B92).
                   El número de operación NO se puede nombrar acá: lo asigna
                   ServicioFormalizacion.generarDocumentos dentro de la transacción, al confirmar
                   (generarIdentificadorOperacion, que consume numeración) -- por eso el cartel
                   dice que la operación recibe su número, en vez de inventarlo. --%>
              data-confirmar-titulo="¿Generar los documentos de formalización?"
              data-confirmar="Cliente: <%= clienteNuevaFormalizacion.getNombres() %> <%= clienteNuevaFormalizacion.getApellidos() %> — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteNuevaFormalizacion.getCedulaIdentidad()) %>&#10;Monto aprobado: <%= FormateadorNumeros.comoGuaranies(solicitudNueva.getMontoAprobado()) %> a <%= solicitudNueva.getPlazoAprobadoMeses() %> meses&#10;Cuota más alta: <%= FormateadorNumeros.comoGuaranies(resumenNueva.getCuotaMasAlta()) %>&#10;Primer vencimiento: {fecha}&#10;Se emiten el contrato, el pagaré y el plan de pagos, y la operación recibe su número."
              data-confirmar-foco="cancelar" data-confirmar-boton="Sí, generar"
              <% } %>>
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-group col-md-3 p-0">
                <label>Primer vencimiento</label>
                <input type="date" name="primerVencimiento" class="form-control campo-primer-vencimiento"
                       value="<%= valorCampoPrimerVencimientoNueva %>" data-feriados='<%= feriadosJsonNueva %>'
                       min="<%= rangoFechasNueva.getMinima() %>" max="<%= rangoFechasNueva.getMaxima() %>" required>
                <%-- Corrección 2 (Joel): rango concreto y aviso si la fecha cae en día inhábil (RN-19).
                     RF11 (11/10/2026): el calendario ahora además limita min/max -- el servidor sigue
                     siendo el control real. --%>
                <small class="text-muted">Entre <%= FormateadorFechas.comoDdMmAaaa(rangoFechasNueva.getMinima()) %>
                    y <%= FormateadorFechas.comoDdMmAaaa(rangoFechasNueva.getMaxima()) %>.</small>
                <div class="aviso-dia-inhabil text-muted small mt-1"></div>
            </div>
            <%-- B182 (14/11/2026): el motivo del bloqueo, junto al botón. Los dos avisos en rojo
                 estaban arriba, dentro de la tabla de condiciones, y "Generar documentos" no decía
                 nada: el oficial apretaba y recién ahí se enteraba. Mismo criterio que el renglón
                 de motivos de "Cobrar" (B79/B95, D-145): el motivo se lee en el mismo lugar donde
                 se busca el botón. El botón NO se deshabilita -- decisión de Joel del 19/10/2026,
                 el bloqueo real es del servidor. La frase la arma ResumenFormalizacion
                 (nombrarMotivosDeBloqueo), no esta JSP, y el detalle completo de cada motivo sigue
                 arriba, sin repetirse acá (B123). --%>
            <% if (resumenNueva.hayBloqueoConocido()) { %>
            <p class="small text-danger mt-2 mb-2">No se va a poder generar: <%= resumenNueva.nombrarMotivosDeBloqueo() %>.
                El detalle está arriba, en las condiciones aprobadas.</p>
            <% } %>
            <button type="submit" class="btn btn-success mt-2">Generar documentos</button>
        </form>
    </div>
</div>

<a href="${pageContext.request.contextPath}/formalizacion<%= urlVolverBandejaNueva %>" class="btn btn-default mt-3">Volver a la bandeja</a>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
