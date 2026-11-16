<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
    B211 (15/11/2026, ítem 6 del informe de UX de Informes): EL renglón que dice qué período está
    mostrando un reporte, en un solo lugar.

    El defecto que cierra: el rango aplicado no se decía en ninguna parte del texto -- los títulos
    no lo nombraban y las fechas vivían solo dentro de los campos del formulario, así que una
    pantalla mirada de lejos, o una captura, no decía de qué período era. El PDF de Auditoría ya lo
    resolvía bien (construirTextoFiltrosAuditoria); faltaba la pantalla.

    Cómo se usa: el controlador deja el texto armado (ServicioReportes.textoRangoAplicado con las
    fechas EFECTIVAS, o textoMesAplicado para los reportes por mes) y la pantalla lo incluye justo
    debajo del enlace "« Reportes".

        <%@ include file="/WEB-INF/vistas/plantilla/rango_aplicado_reporte.jsp" %>

    Lee un ATRIBUTO y no los parámetros -- al contrario que aviso_rango_reporte.jsp, que habla de
    lo que la persona escribió. Acá hace falta el rango que el reporte está usando de verdad, y en
    la primera carga ese valor no está en ningún parámetro: lo resolvió el controlador con su valor
    por defecto, que es justo cuando más falta saber cuál es.

    Vacío = no se dibuja nada (un reporte sin rango, como Cartera o Morosidad, que son foto del
    momento).
--%>
<%
    String rangoAplicadoReporte = (String) request.getAttribute("rangoAplicado");
    if (rangoAplicadoReporte != null && !rangoAplicadoReporte.isEmpty()) {
%>
<p class="text-muted mb-2"><i class="fas fa-calendar-alt mr-1" aria-hidden="true"></i><%= rangoAplicadoReporte %></p>
<%
    }
%>
