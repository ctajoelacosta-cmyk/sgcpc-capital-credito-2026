<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Departamento" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Ciudad" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FiltroClientes" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    Boolean puedeEditarLista = (Boolean) request.getAttribute("puedeEditar");
    // V11: "puedeEmitirInformes" ahora se lee directo en _resultados.jsp (misma clave de
    // request.setAttribute de siempre, ControladorClientesServlet.mostrarLista) -- los botones
    // de informe se movieron a la barra de resultados, ver ese archivo.
    String filtroEstadoActual = (String) request.getAttribute("filtroEstado");
    FiltroClientes filtro = (FiltroClientes) request.getAttribute("filtro");
    // B156 (14/11/2026): cuántos de los 6 filtros plegables vienen con valor. Lo decide el
    // SERVIDOR, con el mismo FiltroClientes que ya está en el request, y de eso depende que el
    // bloque se dibuje desplegado: un filtro aplicado nunca puede quedar escondido sin que se
    // vea por qué la lista está recortada. "Estado" no cuenta -- queda siempre a la vista y
    // tiene valor por defecto (Activos), así que sumarlo diría "1 filtro" en una lista sin
    // filtrar. El número de después, en vivo, lo mantiene el JS del pie (ver ahí por qué).
    int cantidadFiltrosAvanzados = 0;
    if (filtro != null) {
        if (filtro.getAltaDesde() != null) { cantidadFiltrosAvanzados++; }
        if (filtro.getAltaHasta() != null) { cantidadFiltrosAvanzados++; }
        if (filtro.getPrestamoActivo() != null && !filtro.getPrestamoActivo().isEmpty()) { cantidadFiltrosAvanzados++; }
        if (filtro.getIdDepartamento() != null) { cantidadFiltrosAvanzados++; }
        if (filtro.getIdCiudad() != null) { cantidadFiltrosAvanzados++; }
        if (filtro.getSituacionLaboral() != null && !filtro.getSituacionLaboral().isEmpty()) { cantidadFiltrosAvanzados++; }
    }
    boolean hayFiltrosAvanzados = cantidadFiltrosAvanzados > 0;
    // B157 (14/11/2026): el orden vigente, que ya viene validado contra la lista blanca del DAO.
    String ordenActualLista = (String) request.getAttribute("ordenActual");
    String sentidoActualLista = (String) request.getAttribute("sentidoActual");
    if (ordenActualLista == null) { ordenActualLista = "nombre"; }
    if (sentidoActualLista == null) { sentidoActualLista = "asc"; }
    @SuppressWarnings("unchecked")
    List<Departamento> listaDepartamentos = (List<Departamento>) request.getAttribute("departamentos");
    @SuppressWarnings("unchecked")
    List<Ciudad> listaCiudades = (List<Ciudad>) request.getAttribute("ciudades");
    // RF02 (pedido de Joel): ya no son las 8 fijas -- las admitidas por la política vigente MÁS
    // cualquier otra que ya tenga algún cliente cargado (ServicioClientes.listarSituacionesLaboralesParaFiltro).
    @SuppressWarnings("unchecked")
    List<String[]> situacionesLaboralesFiltro = (List<String[]>) request.getAttribute("situacionesLaboralesFiltro");
%>
<h1>Clientes</h1>

<% if (Boolean.TRUE.equals(puedeEditarLista)) { %>
<a href="${pageContext.request.contextPath}/clientes/nuevo" class="btn btn-primary mb-3">
    <i class="fas fa-plus"></i> Nuevo cliente
</a>
<% } %>

<%-- V11 (pedido de Joel), piloto en Clientes: filtros en filas de 4 columnas iguales
     (col-md-3, 12÷4) y, debajo, Limpiar + Buscar alineados a la derecha en una sola línea --
     mismos names, mismo method="get"/action, mismo id de formulario y mismo
     data-listado-vivo de siempre (recursos/js/listado-vivo.js no se tocó). "Informe PDF" y
     "Excel/CSV" eran botones type="submit" con formaction DENTRO de este mismo <form> (así
     toman los filtros actuales sin ningún JS propio armando la URL) -- se movieron a la barra
     de resultados, justo arriba de la tabla (_resultados.jsp), fuera de este <form>: siguen
     siendo type="submit" con el mismo formaction/formtarget de siempre, ahora asociados por
     form="formularioFiltrosClientes" (atributo HTML nativo, funciona igual estando fuera del
     <form> que adentro). --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/clientes" id="formularioFiltrosClientes"
              data-listado-vivo="resultadosClientes">
            <%-- B157 (14/11/2026): el orden viaja DENTRO del formulario, no solo en los enlaces de
                 los encabezados. Así lo conservan las tres cosas que salen de este <form>: la
                 búsqueda en vivo (listado-vivo.js manda el FormData), "Informe PDF" y
                 "Excel/CSV" (los dos son type="submit" de este mismo formulario, con su
                 formaction) -- o sea que el PDF y el CSV salen en el orden de la pantalla, que es
                 lo que decidió Joel. "Limpiar" no los toca (limpiarFormulario solo vacía
                 text/search/date/select), así que limpiar filtros conserva el orden, y el href de
                 respaldo sin JavaScript lo lleva escrito para que haga lo mismo. --%>
            <input type="hidden" name="orden" value="<%= ordenActualLista %>">
            <input type="hidden" name="sentido" value="<%= sentidoActualLista %>">
            <%-- B156, ajuste del 14/11/2026 (Joel, probándolo en pantalla): "Limpiar" y "Buscar"
                 estaban en una fila propia y, con los filtros plegados, quedaban solos con mucho
                 espacio vacío arriba. Van en ESTA fila, a la derecha (ml-auto), junto al botón de
                 "Más filtros". align-items-end alinea los botones con el borde de abajo de los
                 campos, que son más altos porque llevan etiqueta. --%>
            <div class="form-row align-items-end">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Cédula o nombre</label>
                    <input type="text" name="texto" class="form-control"
                           placeholder="Cédula o nombre y apellido"
                           value="<%= filtro != null && filtro.getTexto() != null ? filtro.getTexto() : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Estado</label>
                    <select name="estado" class="form-control" data-valor-defecto="ACTIVOS">
                        <option value="ACTIVOS" <%= "ACTIVOS".equals(filtroEstadoActual) ? "selected" : "" %>>Activos</option>
                        <option value="INACTIVOS" <%= "INACTIVOS".equals(filtroEstadoActual) ? "selected" : "" %>>Inactivos</option>
                        <option value="TODOS" <%= "TODOS".equals(filtroEstadoActual) ? "selected" : "" %>>Todos</option>
                    </select>
                </div>
                <%-- B156 (14/11/2026): los 8 filtros estaban siempre desplegados en dos filas y
                     empujaban la tabla fuera de la primera pantalla. Los dos de todos los días
                     ("Cédula o nombre" y "Estado") se quedan acá; los otros 6 pasan al bloque
                     plegable de abajo. El botón es Bootstrap puro (data-toggle="collapse"), el
                     mecanismo que ya usan otras 4 pantallas -- el widget data-card-widget de
                     AdminLTE no se usa en NINGUNA del sistema, así que no se introduce acá.
                     Abre DESPLEGADO si alguno de esos 6 viene con valor (lo decide el servidor,
                     más arriba, con el mismo FiltroClientes que ya está en el request): un filtro
                     aplicado nunca puede quedar escondido sin que se vea por qué. --%>
                <div class="form-group col-sm-6 col-md-3">
                    <button type="button" class="btn btn-outline-secondary cc-filtros-toggle"
                            data-toggle="collapse" data-target="#filtrosAvanzadosClientes"
                            aria-expanded="<%= hayFiltrosAvanzados ? "true" : "false" %>"
                            aria-controls="filtrosAvanzadosClientes">
                        <i class="fas fa-chevron-down"></i> Más filtros<%-- El texto es de la JSP; el
                        JS de abajo solo reemplaza el NÚMERO (regla de B92). --%>
                        <span id="cuentaFiltrosAvanzados"><%= hayFiltrosAvanzados
                                ? " (" + cantidadFiltrosAvanzados + ")" : "" %></span>
                    </button>
                </div>
                <%-- B156, ajuste del 14/11/2026: los dos botones de siempre, ahora en esta fila y
                     pegados a la derecha. El indicador "Buscando…" sigue DENTRO del formulario,
                     que es donde listado-vivo.js lo busca (form.querySelector), y "Limpiar"
                     conserva su clase y su data-listado-vivo-de: el mecanismo no cambió. --%>
                <div class="form-group col-auto ml-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/clientes?orden=<%= ordenActualLista %>&sentido=<%= sentidoActualLista %>"
                       class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosClientes">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
            <div class="collapse<%= hayFiltrosAvanzados ? " show" : "" %>" id="filtrosAvanzadosClientes">
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Alta desde</label>
                    <input type="date" name="altaDesde" class="form-control"
                           value="<%= filtro != null && filtro.getAltaDesde() != null ? filtro.getAltaDesde() : "" %>">
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Alta hasta</label>
                    <input type="date" name="altaHasta" class="form-control"
                           value="<%= filtro != null && filtro.getAltaHasta() != null ? filtro.getAltaHasta() : "" %>">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Préstamo activo</label>
                    <select name="prestamoActivo" class="form-control">
                        <option value="" <%= filtro == null || filtro.getPrestamoActivo() == null ? "selected" : "" %>>Todos</option>
                        <option value="SI" <%= filtro != null && "SI".equals(filtro.getPrestamoActivo()) ? "selected" : "" %>>Sí</option>
                        <option value="NO" <%= filtro != null && "NO".equals(filtro.getPrestamoActivo()) ? "selected" : "" %>>No</option>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Departamento</label>
                    <select name="idDepartamento" id="selectDepartamentoFiltro" class="form-control">
                        <option value="">Todos</option>
                        <% Long idDepartamentoActual = filtro == null ? null : filtro.getIdDepartamento();
                           if (listaDepartamentos != null) {
                               for (Departamento departamento : listaDepartamentos) { %>
                        <option value="<%= departamento.getIdDepartamento() %>"
                                <%= departamento.getIdDepartamento().equals(idDepartamentoActual) ? "selected" : "" %>>
                            <%= departamento.getNombre() %></option>
                        <% } } %>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Ciudad</label>
                    <select name="idCiudad" id="selectCiudadFiltro" class="form-control">
                        <option value="">Todas</option>
                    </select>
                </div>
                <div class="form-group col-sm-6 col-md-3">
                    <label class="mb-0">Situación laboral</label>
                    <select name="situacionLaboral" class="form-control">
                        <option value="">Todas</option>
                        <% String situacionLaboralActual = filtro == null || filtro.getSituacionLaboral() == null ? "" : filtro.getSituacionLaboral();
                           if (situacionesLaboralesFiltro != null) {
                               for (String[] situacion : situacionesLaboralesFiltro) { %>
                        <option value="<%= situacion[0] %>" <%= situacion[0].equals(situacionLaboralActual) ? "selected" : "" %>><%= situacion[1] %></option>
                        <% } } %>
                    </select>
                </div>
            </div>
            </div><%-- cierra #filtrosAvanzadosClientes (B156) --%>
        </form>
    </div>
</div>

<div id="resultadosClientes">
    <%@ include file="/WEB-INF/vistas/clientes/_resultados.jsp" %>
</div>
<script>
    // D-135: departamento/ciudad en cascada (mismo mecanismo que D-126 en la ficha del cliente),
    // con "Todas" agregado porque acá es un filtro, no un dato obligatorio.
    (function () {
        var selectDepartamento = document.getElementById('selectDepartamentoFiltro');
        var selectCiudad = document.getElementById('selectCiudadFiltro');
        if (!selectDepartamento || !selectCiudad) { return; }
        var ciudades = [
            <% if (listaCiudades != null) { boolean primeraCiudad = true;
                for (Ciudad ciudad : listaCiudades) {
                    if (!primeraCiudad) { %>,<% }
                    primeraCiudad = false; %>
            {id: <%= ciudad.getIdCiudad() %>, idDepartamento: <%= ciudad.getIdDepartamento() %>, nombre: "<%= ciudad.getNombre().replace("\"", "'") %>"}
            <% } } %>
        ];
        var idCiudadActual = <%= filtro == null || filtro.getIdCiudad() == null ? "null" : filtro.getIdCiudad() %>;
        function actualizarCiudades() {
            var idDepartamentoElegido = parseInt(selectDepartamento.value, 10);
            selectCiudad.innerHTML = '';
            selectCiudad.appendChild(new Option('Todas', ''));
            if (!idDepartamentoElegido) { return; }
            ciudades.filter(function (ciudad) { return ciudad.idDepartamento === idDepartamentoElegido; })
                .forEach(function (ciudad) {
                    var opcion = new Option(ciudad.nombre, ciudad.id);
                    if (idCiudadActual === ciudad.id) { opcion.selected = true; }
                    selectCiudad.appendChild(opcion);
                });
        }
        selectDepartamento.addEventListener('change', actualizarCiudades);
        actualizarCiudades();
    })();

    // B156 (14/11/2026): el número del botón "Más filtros". El TEXTO lo escribe la JSP; esto
    // reemplaza SOLO el número (regla de B92). Y el recuento vive acá, no en el servidor, por un
    // motivo concreto: recursos/js/listado-vivo.js reemplaza únicamente #resultadosClientes, así
    // que la tarjeta de filtros NO se vuelve a dibujar en una búsqueda en vivo -- un número
    // calculado en el servidor quedaría diciendo la cantidad de la carga anterior. Entre dos
    // cargas de página, cuántos filtros están tocados solo lo sabe el navegador (mismo criterio
    // que B115).
    (function () {
        var formularioFiltros = document.getElementById('formularioFiltrosClientes');
        var cuentaFiltros = document.getElementById('cuentaFiltrosAvanzados');
        if (!formularioFiltros || !cuentaFiltros) { return; }
        var nombresPlegables = ['altaDesde', 'altaHasta', 'prestamoActivo', 'idDepartamento',
            'idCiudad', 'situacionLaboral'];
        function actualizarCuentaFiltros() {
            var activos = 0;
            for (var i = 0; i < nombresPlegables.length; i++) {
                var campo = formularioFiltros.elements[nombresPlegables[i]];
                if (campo && campo.value) { activos++; }
            }
            cuentaFiltros.textContent = activos === 0 ? '' : ' (' + activos + ')';
        }
        // Diferido un tick: "Limpiar" (listado-vivo.js) vacía los campos y dispara 'change' sobre
        // los <select>; contando en el momento se leería el valor de antes de ese vaciado.
        function actualizarCuentaDiferido() { setTimeout(actualizarCuentaFiltros, 0); }
        formularioFiltros.addEventListener('input', actualizarCuentaDiferido);
        formularioFiltros.addEventListener('change', actualizarCuentaDiferido);
        actualizarCuentaFiltros();
    })();
</script>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
