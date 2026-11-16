<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioInformes" %>
<%--
    B209 (15/11/2026, ítem 7 del informe de UX de Informes): EL ⓘ de los botones de exportar de
    los reportes, y el aviso de cuando no hay nada que exportar.

    El defecto que cierra: los veinte reportes ofrecían "Informe PDF" y "Excel/CSV" sin decir qué
    va a pasar -- ninguno usaba el mecanismo del ⓘ que ya existe (data-glosario-titulo/
    data-glosario-texto, SweetAlert2 desde plantilla/pie.jsp), el mismo que cerró B184 en
    Formalización y B112 en la devolución del pagaré.

    Cómo se usa: antes del include, la pantalla deja en el request si hay filas; va justo ANTES de
    los dos botones.

        <% request.setAttribute("exportarHayFilas", Boolean.valueOf(filasLoQueSea != null
                   && !filasLoQueSea.isEmpty())); %>
        <%@ include file="/WEB-INF/vistas/plantilla/ayuda_exportar_reporte.jsp" %>

    Un solo ⓘ para el par, no uno por botón: explica los dos de una vez y deja la fila de acciones
    con el mismo aire que tenía. Los textos los decide ServicioInformes (un dato, un dueño): si
    estuvieran acá serían una copia, y si estuvieran en cada reporte serían cuarenta.

    El apagado de los botones NO lo hace este fragmento -- lo hacen los propios <a>, con
    ServicioInformes.claseBotonExportar/atributosBotonExportar, porque la clase va adentro de su
    atributo class. Acá va solo el porqué, en texto, que es lo que faltaba: un botón en gris sin
    explicación obliga a adivinar.

    Prefijo "ayudaExportar" en las variables a propósito: el include es traducción estática y
    comparte el _jspService de la página, así que un nombre repetido no compilaría.
--%>
<%
    boolean ayudaExportarHayFilas = !Boolean.FALSE.equals(request.getAttribute("exportarHayFilas"));
%>
<i class="fas fa-info-circle text-muted mr-1" style="cursor:pointer"
   data-glosario-titulo="<%= ServicioInformes.AYUDA_EXPORTAR_TITULO %>"
   data-glosario-texto="<%= ServicioInformes.AYUDA_EXPORTAR_TEXTO %>" aria-hidden="true"></i>
<% if (!ayudaExportarHayFilas) { %>
<small class="text-muted mr-2"><%= ServicioInformes.AVISO_SIN_FILAS_PARA_EXPORTAR %></small>
<% } %>
