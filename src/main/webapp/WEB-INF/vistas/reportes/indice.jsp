<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%-- RF08 (11/10/2026, hallazgo de Joel): "?v=" de VersionDespliegue -- antes este .css y el
     .js de más abajo quedaban sin cache-busting, a diferencia de los demás .js propios
     (pie.jsp). --%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/css/indice-reportes.css?v=<%= VersionDespliegue.VALOR %>">
<%
    Usuario usuarioIndiceReportes = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    // D7 (pedido de Joel): el administrador ve los mismos reportes que el gerente.
    boolean esGerenteIndiceReportes = usuarioIndiceReportes.getPerfilUsuario() == PerfilUsuario.GERENTE_ADMINISTRADOR
            || usuarioIndiceReportes.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR;
%>
<h1>Reportes</h1>
<%-- B208 (15/11/2026, ítem 9 del informe de UX de Informes): el <p> estaba AFUERA del if, así que
     para el gerente se dibujaba vacío y dejaba un renglón en blanco bajo el título. Ahora el
     párrafo entero existe solo cuando tiene algo que decir. --%>
<% if (!esGerenteIndiceReportes) { %>
<p class="text-muted">Como oficial, ve 5 de los 21 reportes; el resto es exclusivo del gerente.</p>
<% } %>

<%-- V13 (pedido de Joel): buscador grande + píldoras de categoría, todo del lado del navegador
     (recursos/js/indice-reportes.js) -- no toca ninguna ruta, permiso ni qué reportes ve cada
     perfil, eso sigue decidido acá abajo por esGerenteIndiceReportes, igual que antes. --%>
<div class="indice-reportes-buscador-wrap">
    <div class="input-group input-group-lg indice-reportes-buscador">
        <div class="input-group-prepend">
            <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
        </div>
        <input type="search" id="buscadorReportes" class="form-control" placeholder="Buscar reporte…" autocomplete="off">
        <div class="input-group-append">
            <button type="button" class="btn indice-reportes-buscador-borrar d-none" aria-label="Borrar búsqueda">
                <i class="fas fa-times" aria-hidden="true"></i>
            </button>
        </div>
    </div>
</div>

<div class="indice-reportes-categorias">
    <button type="button" class="indice-reportes-categoria-boton active" data-categoria-boton="todos">Todos</button>
    <% if (esGerenteIndiceReportes) { %>
    <button type="button" class="indice-reportes-categoria-boton" data-categoria-boton="dueno">Para el dueño</button>
    <% } %>
    <button type="button" class="indice-reportes-categoria-boton" data-categoria-boton="operacion">Operación</button>
    <button type="button" class="indice-reportes-categoria-boton" data-categoria-boton="cobranza">Cobranza</button>
    <% if (esGerenteIndiceReportes) { %>
    <button type="button" class="indice-reportes-categoria-boton" data-categoria-boton="impuestos">Impuestos</button>
    <button type="button" class="indice-reportes-categoria-boton" data-categoria-boton="control">Control</button>
    <% } %>
</div>

<p id="indiceReportesSinResultados" class="text-muted indice-reportes-sin-resultados d-none">
    No encontramos reportes con «<span id="indiceReportesTextoBuscado"></span>». Pruebe con otra palabra.</p>

<%-- Bloque 7/E10 (D-129, Obs. 27): tarjetas agrupadas en secciones con título, para no tener que
     leer las 21 de corrido buscando la que hace falta. --%>
<% if (esGerenteIndiceReportes) { %>
<div data-categoria-seccion>
    <h5 class="indice-reportes-titulo-seccion">Para el dueño</h5>
    <div class="row">
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="dueno"
             data-palabras="ganancia rentabilidad negocio resultado">
            <a href="${pageContext.request.contextPath}/reportes/resumen-financiero" class="indice-reportes-tarjeta indice-reportes-tarjeta-azul">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-hand-holding-usd" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Resumen financiero</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Otorgado, en trámite, cobrado, ganancia, cartera y demanda por rango de fechas (CSV).</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="dueno"
             data-palabras="flujo caja futuro liquidez plata">
            <a href="${pageContext.request.contextPath}/reportes/proyeccion-fondos" class="indice-reportes-tarjeta indice-reportes-tarjeta-azul">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-chart-line" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Proyección de fondos</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Ingresos y egresos esperados a 30/60/90 días.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="dueno"
             data-palabras="mes mensual resumen balance">
            <a href="${pageContext.request.contextPath}/reportes/resumen-mensual" class="indice-reportes-tarjeta indice-reportes-tarjeta-azul">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-chart-pie" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Resumen mensual</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Otorgado, cobrado, ganancia y morosidad del mes (CSV).</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="dueno"
             data-palabras="prestamos capital saldo">
            <a href="${pageContext.request.contextPath}/reportes/cartera" class="indice-reportes-tarjeta indice-reportes-tarjeta-azul">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-layer-group" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Cartera</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Préstamos activos, capital pendiente e interés futuro.</span>
            </a>
        </div>
    </div>
</div>
<% } %>

<div data-categoria-seccion>
    <h5 class="indice-reportes-titulo-seccion">Operación</h5>
    <div class="row">
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="cuotas fechas calendario pagos proximos">
            <a href="${pageContext.request.contextPath}/reportes/vencimientos" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-calendar-alt" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Vencimientos</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Cuotas por vencer en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="cliente prestamo historial ficha reimprimir">
            <a href="${pageContext.request.contextPath}/reportes/estado-cuenta" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-file-invoice-dollar" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Estado de cuenta del cliente</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Historial completo de un préstamo, con reimpresión.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="buro informconf consulta crediticia riesgo">
            <a href="${pageContext.request.contextPath}/reportes/consultas-buro" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-search-dollar" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Consultas al buró</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Consultas registradas, resultado y autorización vigente en ese momento.</span>
            </a>
        </div>
        <% if (esGerenteIndiceReportes) { %>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="solicitud pedido credito nuevo">
            <a href="${pageContext.request.contextPath}/reportes/solicitudes" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-file-signature" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Solicitudes</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Solicitudes creadas en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="aprobado rechazado decision respuesta">
            <a href="${pageContext.request.contextPath}/reportes/aprobaciones" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-check-double" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Aprobaciones y rechazos</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Solicitudes decididas en un rango de fechas, con tiempo de respuesta.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="desembolso entrega dinero plata prestamo">
            <a href="${pageContext.request.contextPath}/reportes/desembolsos" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-hand-holding" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Desembolsos</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Desembolsos confirmados en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="cliente nuevo alta registro">
            <a href="${pageContext.request.contextPath}/reportes/clientes-nuevos" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-user-plus" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Clientes nuevos</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Altas de clientes en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="documento pagare contrato devolucion">
            <a href="${pageContext.request.contextPath}/reportes/cierres-documentales" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-folder-open" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Cierres documentales</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Cierres documentales completados en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="operacion"
             data-palabras="caja arqueo efectivo cajera">
            <a href="${pageContext.request.contextPath}/reportes/caja" class="indice-reportes-tarjeta indice-reportes-tarjeta-verde">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-cash-register" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Caja</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Sesiones de caja cerradas en un rango de fechas.</span>
            </a>
        </div>
        <% } %>
    </div>
</div>

<div data-categoria-seccion>
    <h5 class="indice-reportes-titulo-seccion">Cobranza</h5>
    <div class="row">
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="cobranza"
             data-palabras="mora atraso deuda vencido">
            <a href="${pageContext.request.contextPath}/reportes/morosidad" class="indice-reportes-tarjeta indice-reportes-tarjeta-rojo">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-user-clock" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Morosidad</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Foto del momento: préstamos en mora por tramo.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="cobranza"
             data-palabras="llamada mensaje visita contacto promesa mora">
            <a href="${pageContext.request.contextPath}/reportes/gestiones" class="indice-reportes-tarjeta indice-reportes-tarjeta-rojo">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-phone-alt" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Gestiones de cobranza</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Llamadas, mensajes y visitas registradas.</span>
            </a>
        </div>
        <% if (esGerenteIndiceReportes) { %>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="cobranza"
             data-palabras="cobro pago recibo plata ingreso">
            <a href="${pageContext.request.contextPath}/reportes/cobros" class="indice-reportes-tarjeta indice-reportes-tarjeta-rojo">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-money-bill-wave" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Cobros</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Cobros confirmados en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="cobranza"
             data-palabras="incobrable perdida castigo">
            <a href="${pageContext.request.contextPath}/reportes/incobrables" class="indice-reportes-tarjeta indice-reportes-tarjeta-rojo">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-ban" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Incobrables</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>En mora con más días de atraso que el límite.</span>
            </a>
        </div>
        <% } %>
    </div>
</div>

<% if (esGerenteIndiceReportes) { %>
<div data-categoria-seccion>
    <h5 class="indice-reportes-titulo-seccion">Impuestos</h5>
    <div class="row">
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="impuestos"
             data-palabras="impuesto factura set marangatu">
            <a href="${pageContext.request.contextPath}/reportes/iva" class="indice-reportes-tarjeta indice-reportes-tarjeta-ambar">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-file-invoice" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>IVA del mes</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Facturas emitidas del mes, para la liquidación (CSV).</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="impuestos"
             data-palabras="factura anulada baja set marangatu">
            <a href="${pageContext.request.contextPath}/reportes/facturas-anuladas" class="indice-reportes-tarjeta indice-reportes-tarjeta-ambar">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-file-excel" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Facturas anuladas del mes</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Anuladas por reversión de cobro, para la baja en Marangatu.</span>
            </a>
        </div>
    </div>
</div>

<div data-categoria-seccion>
    <h5 class="indice-reportes-titulo-seccion">Control</h5>
    <div class="row">
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="control"
             data-palabras="reversion anulacion correccion">
            <a href="${pageContext.request.contextPath}/reportes/reversiones" class="indice-reportes-tarjeta indice-reportes-tarjeta-gris">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-undo" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Reversiones</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Reversiones solicitadas en un rango de fechas.</span>
            </a>
        </div>
        <div class="col-lg-3 col-md-4 col-sm-6 mb-3" data-tarjeta-reporte data-categoria="control"
             data-palabras="auditoria historial registro log">
            <a href="${pageContext.request.contextPath}/reportes/auditoria" class="indice-reportes-tarjeta indice-reportes-tarjeta-gris">
                <span class="indice-reportes-tarjeta-icono"><i class="fas fa-history" aria-hidden="true"></i></span>
                <span class="indice-reportes-tarjeta-titulo" data-tarjeta-reporte-titulo>Auditoría</span>
                <span class="indice-reportes-tarjeta-descripcion" data-tarjeta-reporte-descripcion>Operaciones registradas en el sistema, con filtros.</span>
            </a>
        </div>
    </div>
</div>
<% } %>
<script src="${pageContext.request.contextPath}/recursos/js/indice-reportes.js?v=<%= VersionDespliegue.VALOR %>"></script>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
