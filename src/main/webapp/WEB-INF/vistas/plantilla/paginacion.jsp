<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
    B167 (14/11/2026, decisión de Joel: opción (b)): LA paginación del sistema, en un solo lugar.

    Nació en clientes/_resultados.jsp con B148 y se extrajo acá cuando Solicitudes necesitó la
    misma: una copia habría divergido en el primer cambio (es la regla de "un dato, un dueño"
    aplicada al marcado, y la razón por la que B151 descartó arreglar una pantalla sola). El
    marcado es EXACTAMENTE el de B148 -- lo único que cambia es de dónde sale la URL.

    Cómo se usa: antes del include, la pantalla deja en el request la URL base ya armada, con todo
    lo que tiene que viajar (filtros, orden) y terminada en "?" o en "&", porque este fragmento le
    pega "pagina=N" al final y nada más:

        <% request.setAttribute("paginacionUrlBase",
                   request.getContextPath() + "/solicitudes" + prefijoDeLaPantalla); %>
        <%@ include file="/WEB-INF/vistas/plantilla/paginacion.jsp" %>

    "pagina" y "totalPaginas" los pone el controlador, como ya lo hacían las dos pantallas.

    Contrato con recursos/js/listado-vivo.js (no se toca): lo navegable lleva
    class="listado-vivo-pagina", data-pagina y un href real de respaldo sin JavaScript; lo NO
    navegable -- el anterior en la página 1, el siguiente en la última, los "…" y la página
    actual -- es <span> SIN esa clase, así que no hay clic posible a una página inexistente.

    Nombres de variable con prefijo "pag" a propósito: este fragmento se incluye (traducción
    estática) DENTRO del mismo _jspService que el fragmento de resultados de cada listado, así que
    un nombre repetido no compilaría.

    B183 (15/11/2026): dos atributos OPCIONALES, los dos con el comportamiento de siempre por
    defecto, para que Formalización pueda tener TRES paginaciones en una sola pantalla sin tocar
    nada de las otras cuatro:

      - "paginacionParamPagina": cómo se llama el parámetro de página. Por defecto "pagina"; en
        Formalización cada sección tiene el suyo (paginaFormalizar / paginaDesembolso /
        paginaPagare), porque un solo "pagina" movería las tres secciones a la vez.
      - "paginacionConListadoVivo": si los enlaces llevan la clase que intercepta listado-vivo.js.
        Por defecto TRUE (las 4 pantallas de siempre, sin un solo cambio). Formalización lo pone en
        FALSE y sus enlaces navegan de verdad: ese script arma el pedido AJAX desde el formulario y
        le pega "pagina=N" fijo (listado-vivo.js:82), así que con tres secciones mandaría el
        parámetro equivocado y movería la sección que no se tocó. Hacer que entienda tres
        paginaciones obligaba a cambiar un script del que dependen Clientes, Solicitudes,
        Aprobaciones y Mora -- las cuatro ya probadas en pantalla --, así que acá se recarga la
        página, igual que ya hacen los encabezados que ordenan en TODAS las pantallas. El href
        lleva el estado de las 3 secciones, así que no se pierde nada al navegar.

    IMPORTANTE: este fragmento se incluye con <jsp:include> (dinámico) cuando hay más de una
    paginación en la misma pantalla. Con el <%@ include %> estático, la segunda copia redeclararía
    estas variables y no compilaría.
--%>
<%
    Integer pagPaginaActual = (Integer) request.getAttribute("pagina");
    Integer pagTotalPaginas = (Integer) request.getAttribute("totalPaginas");
    String pagUrlBase = (String) request.getAttribute("paginacionUrlBase");
    String pagParamPagina = (String) request.getAttribute("paginacionParamPagina");
    Boolean pagConListadoVivo = (Boolean) request.getAttribute("paginacionConListadoVivo");
    if (pagParamPagina == null || pagParamPagina.isEmpty()) { pagParamPagina = "pagina"; }
    String pagClaseEnlace = "page-link" + (Boolean.FALSE.equals(pagConListadoVivo) ? "" : " listado-vivo-pagina");
    if (pagPaginaActual == null) { pagPaginaActual = 1; }
    if (pagUrlBase == null) { pagUrlBase = ""; }
    if (pagTotalPaginas != null && pagTotalPaginas > 1) {
        // Ventana de números: la actual y dos a cada lado, más la primera y la última siempre.
        int pagPrimeraDeLaVentana = Math.max(1, pagPaginaActual - 2);
        int pagUltimaDeLaVentana = Math.min(pagTotalPaginas, pagPaginaActual + 2);
        java.util.List<Integer> pagNumeros = new java.util.ArrayList<Integer>();
        pagNumeros.add(1);
        if (pagPrimeraDeLaVentana > 2) { pagNumeros.add(0); }   // 0 = el separador "…"
        for (int p = Math.max(2, pagPrimeraDeLaVentana); p <= Math.min(pagTotalPaginas - 1, pagUltimaDeLaVentana); p++) {
            pagNumeros.add(p);
        }
        if (pagUltimaDeLaVentana < pagTotalPaginas - 1) { pagNumeros.add(0); }
        pagNumeros.add(pagTotalPaginas);
%>
<nav class="d-flex align-items-center flex-wrap" aria-label="Páginas de resultados">
    <ul class="pagination mb-2 mr-3">
        <li class="page-item <%= pagPaginaActual <= 1 ? "disabled" : "" %>">
            <% if (pagPaginaActual <= 1) { %>
            <span class="page-link">&laquo; Anterior</span>
            <% } else { %>
            <a class="<%= pagClaseEnlace %>" data-pagina="<%= pagPaginaActual - 1 %>"
               href="<%= pagUrlBase %><%= pagParamPagina %>=<%= pagPaginaActual - 1 %>">&laquo; Anterior</a>
            <% } %>
        </li>
        <%-- intValue() a propósito en las dos comparaciones: los dos son Integer, así que "=="
             entre ellos compararía REFERENCIAS -- andaría por el caché de Integer hasta 127 y
             fallaría en la página 128 en adelante, justo donde nadie mira. --%>
        <% for (Integer pagNumero : pagNumeros) {
               if (pagNumero.intValue() == 0) { %>
        <li class="page-item disabled"><span class="page-link">&hellip;</span></li>
        <%     } else if (pagNumero.intValue() == pagPaginaActual.intValue()) { %>
        <li class="page-item active"><span class="page-link"><%= pagNumero %></span></li>
        <%     } else { %>
        <li class="page-item">
            <a class="<%= pagClaseEnlace %>" data-pagina="<%= pagNumero %>"
               href="<%= pagUrlBase %><%= pagParamPagina %>=<%= pagNumero %>"><%= pagNumero %></a>
        </li>
        <%     } %>
        <% } %>
        <li class="page-item <%= pagPaginaActual >= pagTotalPaginas ? "disabled" : "" %>">
            <% if (pagPaginaActual >= pagTotalPaginas) { %>
            <span class="page-link">Siguiente &raquo;</span>
            <% } else { %>
            <a class="<%= pagClaseEnlace %>" data-pagina="<%= pagPaginaActual + 1 %>"
               href="<%= pagUrlBase %><%= pagParamPagina %>=<%= pagPaginaActual + 1 %>">Siguiente &raquo;</a>
            <% } %>
        </li>
    </ul>
    <small class="text-muted mb-2">Página <%= pagPaginaActual %> de <%= pagTotalPaginas %></small>
</nav>
<% } %>
