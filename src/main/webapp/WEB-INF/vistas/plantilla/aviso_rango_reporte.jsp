<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioReportes" %>
<%--
    B207 (15/11/2026, ítem 5 del informe de UX de Informes): EL aviso de rango invertido de los
    reportes, en un solo lugar.

    El defecto que cierra: ningún reporte validaba que "Desde" fuera anterior a "Hasta". Con el
    rango al revés la consulta sale vacía y la pantalla contestaba con su cartel de "no hay
    nada" -- "no hay datos" era indistinguible de "el filtro no tiene sentido", y el que lo lee es
    el dueño decidiendo sobre plata (misma familia que B161).

    Cómo se usa: una sola línea, justo DEBAJO de la tarjeta de filtros y ARRIBA de los resultados.

        <%@ include file="/WEB-INF/vistas/plantilla/aviso_rango_reporte.jsp" %>

    No recibe nada. Lee los PARÁMETROS del pedido ("desde"/"hasta"), que son iguales en los trece
    reportes con rango y son exactamente lo que la persona escribió -- ocho reportes dejan el rango
    como atributo del request y cinco lo guardan dentro de un DTO de filtro, así que leer el
    atributo habría servido para unos y no para otros. Por eso tampoco hace falta tocar ningún
    controlador.

    El texto lo decide ServicioReportes.avisoRangoInvertido (un dato, un dueño) y tiene su JUnit:
    la vista solo lo dibuja. Vacío = no hay nada que avisar (campo en blanco, texto que no es una
    fecha, o un rango correcto -- incluidas las fechas iguales, que son un día y es el rango por
    defecto de Auditoría).

    Nombre de variable con prefijo "avisoRango" a propósito: este fragmento se incluye (traducción
    estática) dentro del mismo _jspService de la página, así que un nombre repetido no compilaría.
--%>
<%
    String avisoRangoReporte = ServicioReportes.avisoRangoInvertido(
            request.getParameter("desde"), request.getParameter("hasta"));
    // B207, segunda vuelta (15/11/2026, hallazgo de Joel al probar): el fragmento deja dicho que el
    // rango esta invertido, para que el cartel de "no hay nada" de cada reporte NO se dibuje encima
    // de este aviso. Eran dos mensajes diciendo lo mismo, y el de abajo era el que sobraba: no
    // explica nada que este no haya dicho mejor. Se evalua una sola vez y lo leen los dos.
    request.setAttribute("rangoReporteInvertido", Boolean.valueOf(!avisoRangoReporte.isEmpty()));
    if (!avisoRangoReporte.isEmpty()) {
%>
<div class="alert alert-warning mt-3"><i class="fas fa-exclamation-triangle mr-1" aria-hidden="true"></i><%= avisoRangoReporte %></div>
<%
    }
%>
