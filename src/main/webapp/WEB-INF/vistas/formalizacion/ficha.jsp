<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.SolicitudCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Cuota" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PlanPago" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Feriado" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    PrestamoCredito prestamoFicha = (PrestamoCredito) request.getAttribute("prestamo");
    SolicitudCredito solicitudFormalizacion = (SolicitudCredito) request.getAttribute("solicitud");
    Persona clienteFormalizacion = (Persona) request.getAttribute("cliente");
    @SuppressWarnings("unchecked")
    List<ServicioFormalizacion.ItemChecklist> checklistFicha =
            (List<ServicioFormalizacion.ItemChecklist>) request.getAttribute("checklist");
    @SuppressWarnings("unchecked")
    List<Cuota> cuotasFicha = (List<Cuota>) request.getAttribute("cuotas");
    PlanPago planVigenteFicha = (PlanPago) request.getAttribute("planVigente");
    boolean hayFirmadoCargado = false;
    if (checklistFicha != null) {
        for (ServicioFormalizacion.ItemChecklist item : checklistFicha) {
            if (item.isCumplido()) {
                hayFirmadoCargado = true;
            }
        }
    }
    // Corrección 3 (Joel): checklist COMPLETO (todos, no solo alguno) -> cartel verde.
    boolean checklistCompletoFicha = checklistFicha != null
            && new ServicioFormalizacion().esChecklistCompleto(checklistFicha);
    boolean pendienteDesembolso = prestamoFicha.getEstadoPrestamo() == EstadoPrestamo.PENDIENTE_DESEMBOLSO;
    // D.1 (reprueba Lote 1, pedido de Joel): prestamoFicha.getPrimeraFechaVencimiento() es la
    // fecha ELEGIDA (antes del corrimiento a día hábil); la 1.ª cuota real del plan vigente ya
    // tiene la fecha EFECTIVA -- mostrar las dos cuando difieren, para no decir "21/11/2026"
    // mientras el contrato (que sí usa la efectiva) dice "23/11/2026".
    LocalDate primerVencimientoEfectivoFicha = (cuotasFicha != null && !cuotasFicha.isEmpty())
            ? cuotasFicha.get(0).getFechaVencimiento() : prestamoFicha.getPrimeraFechaVencimiento();
    // D.3 (reprueba Lote 1, pedido de Joel): si "Regenerar" se rechazó por fecha fuera de plazo,
    // mostrar lo que se había tecleado, no la fecha actual del préstamo (mismo mecanismo que
    // nueva.jsp).
    @SuppressWarnings("unchecked")
    Map<String, String> valoresFormularioFicha = (Map<String, String>) request.getAttribute("valoresFormulario");
    String primerVencimientoPrevioFicha = valoresFormularioFicha != null
            ? valoresFormularioFicha.get("primerVencimiento") : null;
    String valorCampoPrimerVencimientoFicha = primerVencimientoPrevioFicha != null && !primerVencimientoPrevioFicha.isEmpty()
            ? primerVencimientoPrevioFicha : String.valueOf(prestamoFicha.getPrimeraFechaVencimiento());
    Usuario usuarioFormalizacion = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    // D7 (pedido de Joel): el administrador también ve el botón de anular.
    boolean esGerenteFormalizacion = usuarioFormalizacion != null
            && (usuarioFormalizacion.getPerfilUsuario() == PerfilUsuario.GERENTE_ADMINISTRADOR
                    || usuarioFormalizacion.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR);
    ServicioFormalizacion.RangoFechas rangoFechasFicha = (ServicioFormalizacion.RangoFechas) request.getAttribute("rangoFechas");
    ServicioFormalizacion.ResumenFormalizacion resumenFicha =
            (ServicioFormalizacion.ResumenFormalizacion) request.getAttribute("resumenFormalizacion");
    @SuppressWarnings("unchecked")
    List<Feriado> feriadosFicha = (List<Feriado>) request.getAttribute("feriados");
    StringBuilder feriadosJsonFicha = new StringBuilder("[");
    if (feriadosFicha != null) {
        for (int i = 0; i < feriadosFicha.size(); i++) {
            if (i > 0) { feriadosJsonFicha.append(','); }
            Feriado feriadoJsonFicha = feriadosFicha.get(i);
            feriadosJsonFicha.append("{\"fecha\":\"").append(feriadoJsonFicha.getFecha())
                    .append("\",\"descripcion\":\"").append(feriadoJsonFicha.getDescripcion().replace("\"", "\\\""))
                    .append("\"}");
        }
    }
    feriadosJsonFicha.append(']');
    // B180 (14/11/2026): el "Volver a la bandeja" del pie conserva la búsqueda que el usuario
    // tenía. El valor lo arma ControladorFormalizacionServlet.mostrarLista y lo sirve
    // mostrarFicha (RETORNO_BANDEJA_EN_SESION); acá solo se dibuja, ya viene codificado. Vacío =
    // /formalizacion, como antes -- también si esta JSP corre contra un .class viejo.
    String urlVolverBandejaFicha = (String) request.getAttribute("urlVolverBandeja");
    urlVolverBandejaFicha = urlVolverBandejaFicha == null || urlVolverBandejaFicha.isEmpty()
            ? "" : "?" + urlVolverBandejaFicha;
    @SuppressWarnings("unchecked")
    Map<String, Long> documentosGeneradosFicha = (Map<String, Long>) request.getAttribute("documentosGenerados");
    Long idContratoGenerado = documentosGeneradosFicha == null ? null : documentosGeneradosFicha.get("CONTRATO_GENERADO");
    Long idPagareGenerado = documentosGeneradosFicha == null ? null : documentosGeneradosFicha.get("PAGARE_GENERADO");
    Long idPlanPagosGenerado = documentosGeneradosFicha == null ? null : documentosGeneradosFicha.get("PLAN_PAGOS_GENERADO");
    boolean faltaAlgunPdf = idContratoGenerado == null || idPagareGenerado == null || idPlanPagosGenerado == null;
%>
<h1><%= prestamoFicha.getIdentificadorOperacion() %></h1>
<p><span class="badge badge-secondary"><%= prestamoFicha.getEstadoPrestamo().getEtiqueta() %></span></p>

<% if (prestamoFicha.getEstadoPrestamo() == EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO) { %>
<div class="alert alert-secondary">
    <%-- RF14 (pedido de Joel, 12/10/2026): quién y cuándo, no solo el motivo -- mismo criterio
         que "nombreCierreDocumentalPor" (cierre documental). --%>
    Anulado antes del desembolso por <%= request.getAttribute("nombreAnuladoPor") != null
            ? request.getAttribute("nombreAnuladoPor") : "-" %>
    el <%= FormateadorFechas.comoDdMmAaaaHhMm(prestamoFicha.getFechaAnulacionSinDesembolso()) %>.
    Motivo: <%= prestamoFicha.getMotivoAnulacionSinDesembolso() %>
</div>
<% } %>

<%-- E9/Bloque 5 (P10): cierre documental -- devolución del pagaré, solo corresponde una vez FINALIZADO. --%>
<% if (prestamoFicha.getEstadoPrestamo() == EstadoPrestamo.FINALIZADO) { %>
<div class="alert <%= "COMPLETO".equals(prestamoFicha.getEstadoCierreDocumental()) ? "alert-success" : "alert-info" %>">
    <%-- B85 (28/10/2026, aprobado por Joel): el trámite, no el nombre interno del estado. --%>
    Devolución del pagaré: <%= "COMPLETO".equals(prestamoFicha.getEstadoCierreDocumental()) ? "COMPLETO" : "pendiente" %> —
    <a href="${pageContext.request.contextPath}/formalizacion/<%= prestamoFicha.getIdPrestamo() %>/cierre-documental">Ir a la devolución del pagaré</a>
</div>
<% } %>

<div class="card">
    <div class="card-header"><h5 class="m-0">Condiciones</h5></div>
    <div class="card-body">
        <table class="table table-sm">
            <tr><th>Cliente</th>
                <td><%= clienteFormalizacion.getNombres() %> <%= clienteFormalizacion.getApellidos() %>
                    — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFormalizacion.getCedulaIdentidad()) %></td></tr>
            <tr><th>Monto</th>
                <td><%= FormateadorNumeros.comoGuaranies(prestamoFicha.getMontoAprobado()) %>
                    <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(prestamoFicha.getMontoAprobado()) %></small></td></tr>
            <tr><th>Plazo</th><td><%= prestamoFicha.getPlazoMeses() %> meses</td></tr>
            <tr><th>TEA / TNA</th>
                <td><%= FormateadorNumeros.comoPorcentaje(prestamoFicha.getTasaTea()) %>
                    / <%= FormateadorNumeros.comoPorcentaje(prestamoFicha.getTasaTna()) %></td></tr>
            <tr><th>Primer vencimiento</th>
                <td><% if (primerVencimientoEfectivoFicha.equals(prestamoFicha.getPrimeraFechaVencimiento())) { %>
                    <%= FormateadorFechas.comoDdMmAaaa(prestamoFicha.getPrimeraFechaVencimiento()) %>
                    <% } else { %>
                    <%= FormateadorFechas.comoDdMmAaaa(prestamoFicha.getPrimeraFechaVencimiento()) %>
                    → <%= FormateadorFechas.comoDiaYFecha(primerVencimientoEfectivoFicha) %>
                    <% } %></td></tr>
            <tr><th>Formalizado</th><td><%= FormateadorFechas.comoDdMmAaaaHhMm(prestamoFicha.getFechaFormalizacion()) %></td></tr>
        </table>
        <p class="text-muted mb-0">La presente operación no incluye comisiones, gastos administrativos ni cargos
            adicionales; el costo total del crédito está compuesto únicamente por intereses e IVA sobre intereses.</p>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Documentos generados</h5></div>
    <div class="card-body">
        <%-- D-70: diseño aprobado -- enlace real cuando ya existe el PDF. RF03 (pedido de Joel):
             "Ver" (abre inline, target=_blank), ya no "Descargar". --%>
        <p><strong>Contrato:</strong>
            <% if (idContratoGenerado != null) { %>
            <a href="${pageContext.request.contextPath}/documentos/<%= idContratoGenerado %>" class="btn btn-sm btn-info" target="_blank">Ver</a>
            <% } else { %><span class="badge badge-warning">Todavía no generado</span><% } %></p>
        <p><strong>Pagaré:</strong>
            <% if (idPagareGenerado != null) { %>
            <a href="${pageContext.request.contextPath}/documentos/<%= idPagareGenerado %>" class="btn btn-sm btn-info" target="_blank">Ver</a>
            <% } else { %><span class="badge badge-warning">Todavía no generado</span><% } %></p>
        <p class="mb-0"><strong>Plan de pagos:</strong>
            <% if (idPlanPagosGenerado != null) { %>
            <a href="${pageContext.request.contextPath}/documentos/<%= idPlanPagosGenerado %>" class="btn btn-sm btn-info" target="_blank">Ver</a>
            <% } else { %><span class="badge badge-warning">Todavía no generado</span><% } %></p>
        <% if (faltaAlgunPdf && pendienteDesembolso) { %>
        <%-- D-70: para préstamos formalizados antes de que existiera el diseño (p. ej. CC-2026-00002) -- no toca el plan ni el número. --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoFicha.getIdPrestamo() %>/generar-pdf" class="mt-2">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <button type="submit" class="btn btn-sm btn-outline-primary">Generar los PDF que faltan</button>
            <%-- B184 (15/11/2026): la tercera acción de PDF de esta ficha era la única sin su ⓘ, y
                 las tres se parecen demasiado como para distinguirlas por el nombre. Mismo
                 mecanismo (data-glosario-titulo/data-glosario-texto, click + SweetAlert2 desde
                 pie.jsp) y mismo lugar que las otras dos. El texto sale del javadoc de
                 ControladorFormalizacionServlet.procesarGenerarPdf y de ServicioFormalizacion.
                 generarPdfsFaltantes, que ya lo tenían escrito: es idempotente (si ya existen, no
                 hace nada), no toca el plan ni el número de operación, y por eso NO es un
                 "regenerar". El botón solo aparece cuando falta alguno (faltaAlgunPdf). --%>
            <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
               data-glosario-titulo="Generar los PDF que faltan"
               data-glosario-texto="Emite únicamente los documentos que todavía no existen (contrato, pagaré o plan de pagos), sin tocar el plan de pagos ni el número de operación. Los que ya están no se reemplazan. Sirve para una operación formalizada antes de que el sistema generara los tres, y por eso el botón solo aparece cuando falta alguno."></i>
        </form>
        <% } %>
        <% if (pendienteDesembolso && !hayFirmadoCargado) { %>
        <%-- Ajuste de Joel (revisión de reportes E5): re-emitir los 3 PDF con la plantilla/datos
             actuales, sin tocar el plan ni el número, para aplicar una corrección de diseño.
             PA-31 (14/10/2026, pedido de Joel, autorizado): ahora SÍ se bloquea por documentos
             firmados cargados, igual que "Regenerar documentos" -- para que pantalla y servidor
             digan lo mismo (antes solo se chequeaba en el servidor de "Regenerar", no acá). --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoFicha.getIdPrestamo() %>/regenerar-pdf"
              data-confirmar-titulo="¿Re-emitir los 3 PDF con la plantilla actual?" data-confirmar="No cambia el plan ni el número."
              data-confirmar-foco="cancelar" data-confirmar-boton="Sí, re-emitir" class="mt-2">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <button type="submit" class="btn btn-sm btn-outline-primary">Re-emitir los PDF (sin cambiar plan)</button>
            <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso H, ⓘ 1): distingue
                 este botón de "Regenerar" (más abajo) -- mismo mecanismo data-glosario-titulo/
                 data-glosario-texto que ya usa clientes/ficha.jsp (click, SweetAlert2, pie.jsp). --%>
            <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
               data-glosario-titulo="Re-emitir los PDF"
               data-glosario-texto="Genera de nuevo los 3 PDF (contrato, pagaré, plan de pagos) con los datos actuales del préstamo, sin tocar el plan de pagos ni el número de operación. Úselo si el PDF que ya existe se perdió, se dañó, o necesita una copia nueva porque cambió algo en cómo el sistema los genera. Ya no se puede usar una vez que haya un contrato o pagaré firmado cargado."></i>
        </form>
        <% } else if (pendienteDesembolso && hayFirmadoCargado) { %>
        <p class="text-muted mb-0 mt-2">No se puede re-emitir: ya hay documentos firmados cargados.</p>
        <% } %>
    </div>
</div>

<%-- B185 (15/11/2026): el id es NUEVO (no se renombró ninguno) y existe para que el enlace de la
     barra fija de abajo baje hasta acá. El scroll-margin-top que ya tiene cabecera.jsp para
     #cierre-documental vale para cualquier ancla de la página. --%>
<div class="card mt-3" id="checklist-firmados">
    <div class="card-header"><h5 class="m-0">Checklist antes del desembolso</h5></div>
    <div class="card-body">
        <%-- Corrección 3 (Joel): cartel verde cuando el checklist ya está completo. RF14 (pedido
             de Joel, 12/10/2026): con el préstamo ya ANULADO_ANTES_DESEMBOLSO, "listo para
             desembolso" es falso -- ya no se puede desembolsar. Los firmados cargados se siguen
             viendo abajo (con su "Ver"), solo se oculta este aviso puntual.
             Tanda de usabilidad (19/10/2026, pedido de Joel, RF11_formalizacion.md §10): el
             aviso no decía qué sigue (REGLAS §12.4) -- agregado el siguiente paso, con el nombre
             real del menú ("Desembolso y caja" → "Desembolsos", plantilla/menu.jsp). --%>
        <% if (checklistCompletoFicha && prestamoFicha.getEstadoPrestamo() != EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO) { %>
        <div class="alert alert-success">Documentación completa: listo para desembolso. Siguiente paso: la cajera lo desembolsa desde Desembolso y caja → Desembolsos.</div>
        <% } %>
        <ul class="list-unstyled mb-3">
            <% if (checklistFicha != null) { for (ServicioFormalizacion.ItemChecklist item : checklistFicha) { %>
            <li>
                <% if (item.isCumplido()) { %><span class="badge badge-success">✓</span>
                <% } else if (!pendienteDesembolso) { %>
                <%-- A) RF13 (reprueba Lote 1, pedido de Joel): un préstamo que ya pasó la etapa
                     de desembolso (CC-2026-00001, formalizado antes de que existiera este tipo
                     de documento) no tiene ningún ítem realmente "Pendiente" -- ya no se le va a
                     volver a exigir nada, decir "Pendiente" sería falso. (13/10/2026, pedido de
                     Joel): el badge vuelve a ser corto, igual que "✓"/"Pendiente" -- la
                     explicación se movió a una nota chica aparte, para no romper el mismo
                     formato visual del resto del checklist. --%>
                <span class="badge badge-light text-muted">No exigido</span>
                <% } else { %><span class="badge badge-secondary">Pendiente</span><% } %>
                <%= item.getEtiqueta() %>
                <%-- Corrección 3 (Joel): enlace de "Ver" cuando ya hay uno cargado -- RF03 (pedido
                     de Joel): antes "Ver/Descargar", abre inline (target=_blank), ya no descarga. --%>
                <% if (item.isCumplido() && item.getIdDocumentoVigente() != null) { %>
                <a href="${pageContext.request.contextPath}/documentos/<%= item.getIdDocumentoVigente() %>" class="ml-1" target="_blank">Ver</a>
                <% } %>
                <% if (!item.isCumplido() && !pendienteDesembolso) { %>
                <small class="text-muted">(requisito agregado el 11/10/2026)</small>
                <% } %>
            </li>
            <% } } %>
        </ul>
        <%-- Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7): con el checklist ya
             completo no queda ningún documento pendiente de cargar -- antes el formulario
             seguía visible igual (solo miraba pendienteDesembolso, nunca el checklist). --%>
        <% if (pendienteDesembolso && !checklistCompletoFicha) { %>
        <form method="post" action="${pageContext.request.contextPath}/documentos/cargar" enctype="multipart/form-data">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <input type="hidden" name="idPrestamo" value="<%= prestamoFicha.getIdPrestamo() %>">
            <div class="form-row align-items-end">
                <div class="form-group col-md-4">
                    <label>Tipo de documento</label>
                    <select name="idTipoDocumento" class="form-control" required>
                        <option value="">Seleccione...</option>
                        <% if (checklistFicha != null) { for (ServicioFormalizacion.ItemChecklist item : checklistFicha) { %>
                        <option value="<%= item.getTipo().getIdTipoDocumento() %>"><%= item.getTipo().getNombreTipo() %></option>
                        <% } } %>
                    </select>
                </div>
                <%-- B139 (11/11/2026, revisión final): la columna era col-md-4 y el control nativo
                     de archivo quedaba en 297 px cuando necesita 343, así que Chrome cortaba su
                     texto en el medio de la palabra ("Ningún ar…ccionado"). Medido en pantalla a
                     1280x800. Con col-md-5 son ~384 px y entra entero; la fila queda en 4+5+2 de 12,
                     así que sigue en un solo renglón. --%>
                <div class="form-group col-md-5">
                    <label>Archivo escaneado (PDF, JPG o PNG)</label>
                    <input type="file" name="archivo" class="form-control-file" required>
                </div>
                <div class="form-group col-md-2">
                    <button type="submit" class="btn btn-primary">Cargar</button>
                </div>
            </div>
        </form>
        <% } %>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Plan de pagos</h5></div>
    <div class="card-body">
        <%-- Tanda 2 (14/10/2026, pedido de Joel): si el plan vigente no es el ORIGINAL (un
             adelanto de capital de RF27 lo regeneró), avisar con la fecha real -- mismo criterio
             que estado_cuenta_ficha.jsp para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_DESEMBOLSO. --%>
        <% if (planVigenteFicha != null && !"ORIGINAL".equals(planVigenteFicha.getOrigenPlan())) { %>
        <div class="alert alert-info">Plan vigente después del adelanto de capital del
            <%= FormateadorFechas.comoDdMmAaaa(planVigenteFicha.getFechaGeneracion().toLocalDate()) %>.</div>
        <% } %>
        <div class="table-responsive">
            <table class="table table-sm">
                <thead><tr><th>Cuota</th><th>Vencimiento</th><th class="cc-monto">Capital</th><th class="cc-monto">Interés</th><th class="cc-monto">IVA</th><th class="cc-monto">Total</th><th class="cc-monto">Saldo según plan</th></tr></thead>
                <tbody>
                <%
                    BigDecimal totalCapitalFicha = BigDecimal.ZERO;
                    BigDecimal totalInteresFicha = BigDecimal.ZERO;
                    BigDecimal totalIvaFicha = BigDecimal.ZERO;
                    BigDecimal totalCuotaFicha = BigDecimal.ZERO;
                    if (cuotasFicha != null) { for (Cuota cuota : cuotasFicha) {
                        totalCapitalFicha = totalCapitalFicha.add(cuota.getCapitalProgramado());
                        totalInteresFicha = totalInteresFicha.add(cuota.getInteresProgramado());
                        totalIvaFicha = totalIvaFicha.add(cuota.getTributosProgramados());
                        totalCuotaFicha = totalCuotaFicha.add(cuota.getTotalProgramado());
                %>
                <tr>
                    <td><%= cuota.getNumeroCuota() %></td>
                    <td><%= FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getCapitalProgramado()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getInteresProgramado()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getTributosProgramados()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()) %></td>
                    <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getSaldoCapitalPosterior()) %></td>
                </tr>
                <% } } %>
                </tbody>
                <%-- Corrección 5 (Joel): fila de totales. --%>
                <% if (cuotasFicha != null && !cuotasFicha.isEmpty()) { %>
                <tfoot>
                <tr class="font-weight-bold">
                    <td colspan="2">Total</td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalCapitalFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalInteresFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalIvaFicha) %></td>
                    <td class="cc-monto cc-sin-partir"><%= FormateadorNumeros.comoGuaranies(totalCuotaFicha) %></td>
                    <td></td>
                </tr>
                </tfoot>
                <% } %>
            </table>
        </div>
        <%-- D-145 (P-10, pedido de Joel): "Saldo según plan" es el saldo programado del plan
             original (lo que quedaría si cada cuota se pagara completa y en fecha) -- nunca se
             actualiza con pagos reales. --%>
        <p class="text-muted small mb-0">No refleja pagos realizados; ver estado de cuenta.</p>
    </div>
</div>

<% if (pendienteDesembolso) { %>
<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Regenerar documentos
        <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso H, ⓘ 2): distingue este
             botón de "Re-emitir los PDF" (más arriba). --%>
        <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
           data-glosario-titulo="Regenerar documentos"
           data-glosario-texto="Recalcula el plan de pagos con una fecha de primer vencimiento distinta (queda como una nueva versión del plan, con cuotas nuevas). El número de operación, el contrato y el pagaré no cambian. Ya no se puede usar una vez que haya un contrato o pagaré firmado cargado."></i></h5></div>
    <div class="card-body">
        <% if (hayFirmadoCargado) { %>
        <%-- Corrección 4 (Joel): explicar por qué no está disponible, en vez de solo ocultarlo. --%>
        <p class="text-muted mb-0">No se puede regenerar: ya hay documentos firmados cargados.</p>
        <% } else { %>
        <p class="text-muted">Recalcula el plan si cambia la fecha del primer vencimiento (reutiliza el mismo
            número de operación). Ya no se puede una vez que haya un contrato o pagaré firmado cargado.</p>
        <%-- RF11 (pedido de Joel, 11/10/2026): cuota más alta, total y CFT no cambian con la fecha
             elegida; la afectación se recalcula con los datos ACTUALES del cliente. --%>
        <table class="table table-sm w-auto">
            <tr><th>Cuota más alta</th><td><%= FormateadorNumeros.comoGuaranies(resumenFicha.getCuotaMasAlta()) %></td></tr>
            <tr><th>Total a pagar</th><td><%= FormateadorNumeros.comoGuaranies(resumenFicha.getMontoTotalConIva()) %></td></tr>
            <tr><th>CFT</th><td><%= FormateadorNumeros.comoPorcentaje(resumenFicha.getCft()) %></td></tr>
            <tr><th>Afectación actual</th>
                <td class="<%= resumenFicha.isSuperaLimite() ? "text-danger font-weight-bold" : "" %>">
                    <%= FormateadorNumeros.comoPorcentajeUnDecimal(resumenFicha.getPorcentajeAfectacion()) %>
                    <%-- B138 (11/11/2026, revisión final): el límite va con comoPorcentaje y con los
                         dos puntos, igual que en la solicitud y en la aprobación ("(límite: 40 %)").
                         Acá salía "(límite 40,0 %)" -- el mismo parámetro escrito de dos formas en
                         dos pantallas que el oficial ve una detrás de la otra. La AFECTACIÓN sigue
                         con un decimal: ésa sí lo necesita. --%>
                    (límite: <%= FormateadorNumeros.comoPorcentaje(resumenFicha.getLimiteAfectacion()) %>)
                    <% if (resumenFicha.isSuperaLimite()) { %>
                        — supera el límite: no se podrá regenerar con otra fecha hasta que la situación del
                        cliente lo permita.
                    <% } %>
                </td></tr>
            <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RF11_formalizacion.md §10): mismo
                 criterio que "Afectación actual" de arriba -- rojo desde el principio, sin
                 deshabilitar "Regenerar" (el bloqueo real sigue siendo el del servidor). --%>
            <% if (resumenFicha.getMensajeSinComprobante() != null) { %>
            <tr><th>Comprobante de ingreso</th><td class="text-danger font-weight-bold"><%= resumenFicha.getMensajeSinComprobante() %></td></tr>
            <% } %>
        </table>
        <%-- B.12 (consolidación, pedido de Joel): el botón "Regenerar" se corría a la derecha
             cuando el aviso de día inhábil era largo (compartía la misma fila flex de
             "form-inline" que el aviso). Reordenado: el botón queda pegado a la fecha, en la
             misma línea siempre; "w-100" en el texto del rango fuerza el salto de línea del
             bootstrap flex de "form-inline", así el aviso (que recorre recursos/js/fecha-primer-
             vencimiento.js, que sigue buscando el primer hermano siguiente con la clase
             "aviso-dia-inhabil" -- sigue siendo hermano del campo, solo más atrás) queda debajo,
             sin importar cuán largo sea, sin mover el botón. --%>
        <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoFicha.getIdPrestamo() %>/regenerar"
              <%-- B4 (B86, tramo transversal, 05/11/2026): misma familia que el cartel de
                   "Generar documentos" de nueva.jsp -- resume cliente, monto, plazo, cuota y el
                   primer vencimiento efectivo (el {fecha} lo completa recursos/js/fecha-primer-
                   vencimiento.js con la fecha del campo ya corrida al día hábil; la frase es de
                   acá, regla de B92). Acá el número de operación SÍ existe y va en el título.
                   La última línea es condicional a propósito: ServicioFormalizacion.
                   regenerarDocumentos solo recalcula el plan y re-emite los 3 PDF SI la fecha
                   cambió (línea 793) -- con la misma fecha no reemplaza nada, solo audita, así que
                   un "se reemplazan" pelado mentiría en la mitad de los casos. --%>
              data-confirmar-titulo="¿Regenerar los documentos de <%= prestamoFicha.getIdentificadorOperacion() %>?"
              data-confirmar="Cliente: <%= clienteFormalizacion.getNombres() %> <%= clienteFormalizacion.getApellidos() %> — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFormalizacion.getCedulaIdentidad()) %>&#10;Monto aprobado: <%= FormateadorNumeros.comoGuaranies(prestamoFicha.getMontoAprobado()) %> a <%= prestamoFicha.getPlazoMeses() %> meses&#10;Cuota más alta: <%= FormateadorNumeros.comoGuaranies(resumenFicha.getCuotaMasAlta()) %>&#10;Primer vencimiento: {fecha}&#10;Si cambia la fecha se recalcula el plan y se vuelven a emitir el contrato, el pagaré y el plan de pagos. El número de operación no cambia."
              data-confirmar-foco="cancelar"
              data-confirmar-boton="Sí, regenerar" class="form-inline">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <input type="date" name="primerVencimiento" class="form-control mr-2 campo-primer-vencimiento"
                   value="<%= valorCampoPrimerVencimientoFicha %>" data-feriados='<%= feriadosJsonFicha %>'
                   min="<%= rangoFechasFicha.getMinima() %>" max="<%= rangoFechasFicha.getMaxima() %>" required>
            <button type="submit" class="btn btn-outline-primary">Regenerar</button>
            <small class="text-muted w-100 d-block mt-1">Entre <%= FormateadorFechas.comoDdMmAaaa(rangoFechasFicha.getMinima()) %>
                y <%= FormateadorFechas.comoDdMmAaaa(rangoFechasFicha.getMaxima()) %>.</small>
            <div class="aviso-dia-inhabil text-muted small mb-2"></div>
        </form>
        <% } %>
    </div>
</div>
<% } %>

<% if (pendienteDesembolso && esGerenteFormalizacion) { %>
<div class="mt-3">
    <%-- B178 (14/11/2026): abre la ventana del final de la página. Antes desplegaba el formulario
         acá mismo (data-toggle="collapse"), el patrón que ya se reemplazó en B155 (Clientes),
         B168 (Solicitudes) y B171 (Aprobaciones) -- y esta acción tampoco se deshace. --%>
    <button type="button" class="btn btn-outline-danger" data-toggle="modal" data-target="#modalAnularPrestamo">Anular préstamo</button>
</div>

<%-- B178 (14/11/2026): la anulación, en ventana. Va acá, al final del cuerpo y fuera de las
     tarjetas, por el criterio de mora/_resultados.jsp: un ancestro con transform/filter
     convertiría el position: fixed del modal en relativo a ese ancestro.
     El formulario es el MISMO: misma ruta POST, mismo campo "motivo" con su minlength, mismo
     CSRF y el MISMO id (campoMotivoAnular), así que el cartel de pie.jsp para el evento
     "invalid" sigue funcionando sin tocarlo.
     NO lleva data-confirmar: la ventana ES la confirmación, y ahora nombra la operación, el
     cliente y el monto (B4/B43) en vez del "No se puede deshacer." pelado de antes. Abre con el
     foco en descartar (autofocus en "No, volver"), porque anular no se deshace -- en una ventana
     de Bootstrap eso es autofocus, no existe el focusCancel de SweetAlert2 (B171). --%>
<div class="modal fade" id="modalAnularPrestamo" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post" action="${pageContext.request.contextPath}/formalizacion/<%= prestamoFicha.getIdPrestamo() %>/anular">
                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                <div class="modal-header">
                    <h5 class="modal-title">¿Anular el préstamo <%= prestamoFicha.getIdentificadorOperacion() %>?</h5>
                </div>
                <div class="modal-body">
                    <p class="text-muted mb-3"><small>Cliente: <%= clienteFormalizacion.getNombres() %> <%= clienteFormalizacion.getApellidos() %>
                        — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteFormalizacion.getCedulaIdentidad()) %>.
                        <%-- B178 (14/11/2026, defecto que Joel vio en pantalla): acá iba un "." después
                             del monto y salía "2.000.000 Gs..". comoGuaranies ya termina en "Gs."
                             (D-145 punto 8), y ese punto cierra la oración igual. Lo cubre ahora
                             TextosDePantallaTest.ningunaJspLePegaUnPuntoAlMontoYaFormateado. --%>
                        Monto aprobado: <%= FormateadorNumeros.comoGuaranies(prestamoFicha.getMontoAprobado()) %>
                        Un préstamo anulado no vuelve a quedar pendiente de desembolso.</small></p>
                    <%-- RF14 (pedido de Joel, 11/10/2026): mismo criterio que el motivo de diferencia
                         de RF19 -- minlength real en pantalla (antes solo "required", no vacío), el
                         cartel propio del sistema (pie.jsp, evento "invalid") reemplaza el popup
                         nativo. B178: en la ventana hay lugar para la etiqueta de verdad, así que el
                         <label class="sr-only"> que había en la fila pasó a ser visible -- sigue
                         siendo lo que le da al cartel de pie.jsp una etiqueta real para nombrar el
                         campo (sin esto caía en el "este campo" por defecto). --%>
                    <div class="form-group mb-0">
                        <label for="campoMotivoAnular">Motivo de la anulación *</label>
                        <input type="text" id="campoMotivoAnular" name="motivo" class="form-control"
                               placeholder="Motivo de la anulación (mínimo 10 caracteres)" minlength="10" required>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal" autofocus>No, volver</button>
                    <button type="submit" class="btn btn-outline-danger">Confirmar anulación</button>
                </div>
            </form>
        </div>
    </div>
</div>
<% } %>

<%-- B185 (15/11/2026): esta ficha es larga (condiciones, documentos, checklist, plan de 12-24
     cuotas, regenerar, anular) y el "Volver a la bandeja" quedaba abajo del todo, fuera de la
     pantalla. Ahora la barra se pega al borde de abajo mientras se recorre la ficha, con el mismo
     mecanismo de B152 en la ficha del cliente (sgcpc-tema.css §15, position: sticky).
     El marcado del enlace NO cambió: es el mismo <a> de siempre, con el retorno de B180.
     Al lado va QUÉ falta para desembolsar, que es lo que esta ficha tiene que poder contestar sin
     scrollear -- y sale del MISMO dueño que la bandeja (ServicioFormalizacion.nombrarLoQueFalta,
     B181/B100), así que las dos pantallas no pueden decir cosas distintas. El enlace baja al
     checklist, donde están los botones de carga. Con el préstamo ya anulado no se ofrece nada:
     ahí no falta nada que cargar. --%>
<%
    String faltaParaDesembolsarFicha = "";
    if (pendienteDesembolso && !checklistCompletoFicha && checklistFicha != null) {
        faltaParaDesembolsarFicha = ServicioFormalizacion.nombrarLoQueFalta(checklistFicha);
    }
%>
<div class="d-flex align-items-center flex-wrap cc-ficha-formalizacion-acciones">
    <a href="${pageContext.request.contextPath}/formalizacion<%= urlVolverBandejaFicha %>" class="btn btn-default">Volver a la bandeja</a>
    <% if (!faltaParaDesembolsarFicha.isEmpty()) { %>
    <small class="cc-ficha-formalizacion-falta ml-3">Falta para desembolsar: <%= faltaParaDesembolsarFicha %>.
        <a href="#checklist-firmados">Ir al checklist</a></small>
    <% } %>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
