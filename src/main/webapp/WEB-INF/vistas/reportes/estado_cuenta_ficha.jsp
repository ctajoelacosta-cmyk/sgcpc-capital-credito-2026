<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaCuotaPendiente" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.TotalesEstadoCuenta" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.GestionCobranza" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PrestamoCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    PrestamoCredito prestamoEstadoCuenta = (PrestamoCredito) request.getAttribute("prestamo");
    Persona clienteEstadoCuenta = (Persona) request.getAttribute("cliente");
    @SuppressWarnings("unchecked")
    List<FilaHistorialCobranza> historialEstadoCuenta = (List<FilaHistorialCobranza>) request.getAttribute("historialCobros");
    @SuppressWarnings("unchecked")
    List<FilaCuotaPendiente> cuotasPendientesEstadoCuenta = (List<FilaCuotaPendiente>) request.getAttribute("cuotasPendientes");
    TotalesEstadoCuenta totalesEstadoCuenta = (TotalesEstadoCuenta) request.getAttribute("totales");
    @SuppressWarnings("unchecked")
    List<GestionCobranza> gestionesEstadoCuenta = (List<GestionCobranza>) request.getAttribute("gestiones");
    @SuppressWarnings("unchecked")
    Map<Long, String> nombrePorPersonaGestionEc = (Map<Long, String>) request.getAttribute("nombrePorPersonaGestion");
    @SuppressWarnings("unchecked")
    Map<Long, String> nombrePorUsuarioGestionEc = (Map<Long, String>) request.getAttribute("nombrePorUsuarioGestion");
    // Bloque 7/E10 (D-130, pedido de Joel): reimprimir el comprobante de desembolso también
    // desde la ficha del préstamo, solo consulta (no reemplaza el flujo de /desembolso).
    // B94 (30/10/2026, decisión de Joel): antes esto decía "== CAJERO", escrito a mano. El
    // gerente tiene desde D-146 el permiso de la ruta del PDF (VER_COMPROBANTES_COBRANZA, ver
    // FiltroAutorizacion.permisoDeDocumento) para controlar la rendición, pero NINGUNA pantalla le
    // ofrecía el botón: lo encontró Joel el 30/10 entrando como gerente. Ahora la JSP pregunta por
    // el MISMO permiso que exige el filtro, en vez de nombrar perfiles: un dato, un dueño. Esto no
    // abre /desembolso (sigue pidiendo DESEMBOLSAR, que el gerente no tiene) ni la impresión "para
    // firma" (el botón de abajo exige además que el préstamo ya esté desembolsado).
    py.com.capitalcredito.sgcpc.modelos.Usuario usuarioEstadoCuenta = (py.com.capitalcredito.sgcpc.modelos.Usuario)
            session.getAttribute(py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion.ATRIBUTO_USUARIO);
    boolean puedeReimprimirComprobanteEc = usuarioEstadoCuenta != null
            && new py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion().tienePermiso(
                    usuarioEstadoCuenta.getPerfilUsuario(),
                    py.com.capitalcredito.sgcpc.enumeraciones.Permiso.VER_COMPROBANTES_COBRANZA);
%>
<% if (prestamoEstadoCuenta != null) {
    String estadoNombre = prestamoEstadoCuenta.getEstadoPrestamo().name();
    // E-9b (pedido de Joel): sin desembolso no hay deuda -- Saldo de capital y Monto exigible hoy
    // van "—" (nunca ocultos, a diferencia de FINALIZADO), y el plan de pagos lleva un aviso de
    // que es proyectado (PENDIENTE_DESEMBOLSO) o que el préstamo no generó deuda (ANULADO_ANTES_
    // DESEMBOLSO). Misma decisión que ServicioReportes.tieneDeuda, nunca una copia.
    boolean sinDesembolsoEstadoCuenta = "PENDIENTE_DESEMBOLSO".equals(estadoNombre)
            || "ANULADO_ANTES_DESEMBOLSO".equals(estadoNombre);
    String claseBadgeEstado = "badge-success";
    if ("EN_MORA".equals(estadoNombre)) {
        claseBadgeEstado = "badge-danger";
    } else if ("FINALIZADO".equals(estadoNombre) || "ANULADO_ANTES_DESEMBOLSO".equals(estadoNombre)) {
        claseBadgeEstado = "badge-secondary";
    }
%>
<div class="no-imprimir mb-2">
    <a href="${pageContext.request.contextPath}/reportes/estado-cuenta">&laquo; Nueva búsqueda</a>
    <% if (puedeReimprimirComprobanteEc && !"PENDIENTE_DESEMBOLSO".equals(estadoNombre)
            && !"ANULADO_ANTES_DESEMBOLSO".equals(estadoNombre)) { %>
    <a class="btn btn-sm btn-outline-primary float-right ml-2" target="_blank"
       href="${pageContext.request.contextPath}/desembolso/<%= prestamoEstadoCuenta.getIdPrestamo() %>/comprobante">
        <i class="fas fa-print mr-1"></i>Comprobante de desembolso</a>
    <% } %>
    <a class="btn btn-sm btn-outline-primary float-right ml-2" target="_blank"
       href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/csv">
        <i class="fas fa-file-csv mr-1"></i>Excel/CSV</a>
    <a class="btn btn-sm btn-outline-primary float-right" target="_blank"
       href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/informe">
        <i class="fas fa-file-pdf mr-1"></i>Informe PDF</a>
</div>
<h1><%= prestamoEstadoCuenta.getIdentificadorOperacion() %>
    <small class="badge <%= claseBadgeEstado %>"><%= prestamoEstadoCuenta.getEstadoPrestamo().getEtiqueta() %></small></h1>

<%-- Corrección de Joel: el cierre documental (devolución del pagaré) no tenía ningún acceso
     visible -- acá es donde oficial/gerente ya están mirando un préstamo FINALIZADO. --%>
<% if ("FINALIZADO".equals(estadoNombre) && !"COMPLETO".equals(prestamoEstadoCuenta.getEstadoCierreDocumental())) { %>
<div class="alert alert-info no-imprimir">
    <%-- B85 (28/10/2026, aprobado por Joel): el trámite, no el nombre interno del estado. --%>
    Pagaré pendiente de devolución —
    <a href="${pageContext.request.contextPath}/formalizacion/<%= prestamoEstadoCuenta.getIdPrestamo() %>/cierre-documental">Ir a la devolución del pagaré</a>
</div>
<% } %>

<p class="text-muted"><%= clienteEstadoCuenta.getNombres() %> <%= clienteEstadoCuenta.getApellidos() %>
    — C.I. <%= FormateadorTexto.comoCedulaConPuntos(clienteEstadoCuenta.getCedulaIdentidad()) %>
    — Tel. <%= clienteEstadoCuenta.getTelefonoWhatsapp() %></p>
<p>Monto aprobado: <strong><%= FormateadorNumeros.comoGuaranies(prestamoEstadoCuenta.getMontoAprobado()) %></strong>
    — Plazo: <%= prestamoEstadoCuenta.getPlazoMeses() %> cuota<%= prestamoEstadoCuenta.getPlazoMeses() == 1 ? "" : "s" %>
    — TEA: <strong><%= FormateadorNumeros.comoPorcentaje(prestamoEstadoCuenta.getTasaTea()) %></strong></p>

<% if (totalesEstadoCuenta != null) { %>
<div class="row">
    <div class="col-md-4">
        <div class="small-box bg-secondary">
            <div class="inner"><h3><%= totalesEstadoCuenta.getSaldoCapital() %></h3><p>Saldo de capital</p>
                <% if (sinDesembolsoEstadoCuenta) { %><p class="mb-0"><small>Sin desembolso</small></p><% } %></div>
        </div>
    </div>
    <div class="col-md-4">
        <div class="small-box bg-secondary">
            <div class="inner"><h3><%= totalesEstadoCuenta.getTotalPagado() %></h3><p>Total pagado a la fecha</p></div>
        </div>
    </div>
    <%-- E-9b: a diferencia de FINALIZADO (que sigue ocultando esta tarjeta), PENDIENTE_DESEMBOLSO/
         ANULADO_ANTES_DESEMBOLSO la muestran igual, con "—" (pedido explícito de Joel). --%>
    <% if (totalesEstadoCuenta.getMontoExigibleHoy() != null || sinDesembolsoEstadoCuenta) { %>
    <%-- Corrección de Joel: en 0 no es una alerta -- amarillo queda solo para cuando SÍ hay algo exigible. --%>
    <div class="col-md-4">
        <div class="small-box <%= sinDesembolsoEstadoCuenta ? "bg-secondary"
                : ("0 Gs.".equals(totalesEstadoCuenta.getMontoExigibleHoy()) ? "bg-success" : "bg-warning") %>">
            <div class="inner"><h3><%= sinDesembolsoEstadoCuenta ? "—" : totalesEstadoCuenta.getMontoExigibleHoy() %></h3>
                <p>Monto exigible hoy</p></div>
        </div>
    </div>
    <% } %>
</div>
<% } %>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Plan de pagos</h5></div>
    <div class="card-body">
        <% if ("PENDIENTE_DESEMBOLSO".equals(estadoNombre)) { %>
        <div class="alert alert-info">Plan proyectado: el préstamo todavía no fue desembolsado.</div>
        <% } else if ("ANULADO_ANTES_DESEMBOLSO".equals(estadoNombre)) { %>
        <div class="alert alert-secondary">Préstamo anulado antes del desembolso; no generó deuda.</div>
        <% } %>
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead><tr><th>Cuota</th><th>Vencimiento efectivo</th><th class="cc-monto">Capital</th><th class="cc-monto">Interés</th><th class="cc-monto">IVA</th><th class="cc-monto">Total</th><th>Estado</th><th>Pagada el / Recibo</th></tr></thead>
                <tbody>
                <% if (cuotasPendientesEstadoCuenta != null) { for (FilaCuotaPendiente cuotaPendiente : cuotasPendientesEstadoCuenta) { %>
                <tr>
                    <td><%= cuotaPendiente.getNumeroCuota() %></td>
                    <td><%= cuotaPendiente.getFechaVencimiento() %></td>
                    <td class="cc-monto"><%= cuotaPendiente.getCapital() %></td>
                    <td class="cc-monto"><%= cuotaPendiente.getInteres() %></td>
                    <td class="cc-monto"><%= cuotaPendiente.getIva() %></td>
                    <td class="cc-monto"><%= cuotaPendiente.getTotal() %></td>
                    <td><% String claseBadgeCuota = "Pagada".equals(cuotaPendiente.getEstado()) ? "badge-success"
                            : "Vencida".equals(cuotaPendiente.getEstado()) ? "badge-danger"
                            : "Parcial".equals(cuotaPendiente.getEstado()) ? "badge-warning" : "badge-secondary"; %>
                        <span class="badge <%= claseBadgeCuota %>"><%= cuotaPendiente.getEstado() %></span></td>
                    <td><% if (cuotaPendiente.getFechaPago() != null) { %><%= cuotaPendiente.getFechaPago() %> — <%= cuotaPendiente.getNumeroRecibo() %><% } %></td>
                </tr>
                <% } } %>
                <% if (cuotasPendientesEstadoCuenta == null || cuotasPendientesEstadoCuenta.isEmpty()) { %>
                <tr><td colspan="8" class="text-muted">Sin cuotas.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Historial de cobros</h5></div>
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead><tr><th>Fecha/hora</th><th>Tipo</th><th>Recibo</th><th class="cc-monto">Monto</th><th class="no-imprimir">Comprobantes</th></tr></thead>
                <tbody>
                <% if (historialEstadoCuenta != null) { for (FilaHistorialCobranza filaHistorialEc : historialEstadoCuenta) { %>
                <tr>
                    <td><%= filaHistorialEc.getFechaHora() %></td>
                    <td><%= filaHistorialEc.getEtiquetaTipo() %>
                        <% if (filaHistorialEc.isRevertido()) { %> <span class="badge badge-secondary">REVERTIDO</span><% } %></td>
                    <td><%= filaHistorialEc.getNumeroRecibo() %></td>
                    <td class="cc-monto"><%= filaHistorialEc.getMontoAplicado() %></td>
                    <td class="no-imprimir">
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/recibo/<%= filaHistorialEc.getIdPago() %>">Recibo</a>
                        <% if (filaHistorialEc.getIdFactura() != null) { %>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/factura/<%= filaHistorialEc.getIdFactura() %>">Factura <%= filaHistorialEc.getNumeroFactura() %></a>
                        <% if (filaHistorialEc.isFacturaAnulada()) { %>
                        <span class="badge badge-secondary">ANULADA</span>
                        <a class="btn btn-sm btn-outline-secondary"
                           href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/nota-anulacion/<%= filaHistorialEc.getIdFactura() %>">Nota de anulación</a>
                        <% } %>
                        <% } %>
                        <% if (filaHistorialEc.getIdLiquidacion() != null) { %>
                        <a class="btn btn-sm btn-outline-primary"
                           href="${pageContext.request.contextPath}/reportes/estado-cuenta/<%= prestamoEstadoCuenta.getIdPrestamo() %>/constancia/<%= filaHistorialEc.getIdLiquidacion() %>">Constancia <%= filaHistorialEc.getNumeroConstancia() %></a>
                        <% } %>
                    </td>
                </tr>
                <% } } %>
                <% if (historialEstadoCuenta == null || historialEstadoCuenta.isEmpty()) { %>
                <tr><td colspan="5" class="text-muted">Sin cobros registrados.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div class="card mt-3">
    <div class="card-header"><h5 class="m-0">Gestiones de cobranza</h5></div>
    <div class="card-body">
        <div class="table-responsive">
            <table class="table table-sm table-striped">
                <thead><tr><th>Fecha/hora</th><th>Contactado</th><th>Tipo</th><th>Resultado</th><th>Promesa</th><th>Cumplida</th><th>Registrado por</th></tr></thead>
                <tbody>
                <% if (gestionesEstadoCuenta != null) { for (GestionCobranza gestionEc : gestionesEstadoCuenta) { %>
                <tr>
                    <td><%= FormateadorFechas.comoDdMmAaaaHhMm(gestionEc.getFechaHora()) %></td>
                    <td><%= nombrePorPersonaGestionEc == null ? "" : nombrePorPersonaGestionEc.get(gestionEc.getIdPersonaContactada()) %></td>
                    <td><%= gestionEc.getTipo().getEtiqueta() %></td>
                    <td><%= gestionEc.getResultado() == null ? "" : gestionEc.getResultado() %></td>
                    <td><% if (gestionEc.getFechaPromesa() != null) { %>
                        <%= FormateadorFechas.comoDdMmAaaa(gestionEc.getFechaPromesa()) %> — <%= FormateadorNumeros.comoGuaranies(gestionEc.getMontoPrometido()) %>
                        <% } %></td>
                    <td><% if (gestionEc.getFechaPromesa() != null) { %>
                        <span class="badge <%= gestionEc.isCumplida() ? "badge-success" : "badge-secondary" %>"><%= gestionEc.isCumplida() ? "Sí" : "No" %></span>
                        <% } else { %>—<% } %></td>
                    <td><%= nombrePorUsuarioGestionEc == null ? "" : nombrePorUsuarioGestionEc.get(gestionEc.getRegistradoPor()) %></td>
                </tr>
                <% } } %>
                <% if (gestionesEstadoCuenta == null || gestionesEstadoCuenta.isEmpty()) { %>
                <tr><td colspan="7" class="text-muted">Sin gestiones registradas.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </div>
</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
