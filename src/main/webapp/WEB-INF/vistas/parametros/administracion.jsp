<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%-- B220-bis: VersionDespliegue para el "?v=" del CSS de las pildoras (RF08). --%>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.ParametroSistema" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Feriado" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioParametros" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%-- B220-bis (pedido de Joel, 15/11/2026): las pildoras de grupo usan el MISMO patron y el mismo
     estilo que las categorias del indice de /reportes, asi que se carga ESE archivo en vez de
     copiar las reglas: una sola definicion del estilo, y la pantalla del indice no se toca. --%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/recursos/css/indice-reportes.css?v=<%= VersionDespliegue.VALOR %>">
<%
    @SuppressWarnings("unchecked")
    List<ParametroSistema> listaParametros = (List<ParametroSistema>) request.getAttribute("parametros");
    @SuppressWarnings("unchecked")
    List<Feriado> listaFeriados = (List<Feriado>) request.getAttribute("feriados");
    @SuppressWarnings("unchecked")
    List<LimiteTasaUsura> listaLimites = (List<LimiteTasaUsura>) request.getAttribute("limitesTasa");
    // D-151 (pedido de Joel): el gerente ve esta pantalla en CONSULTA -- sin los 5 formularios
    // que modifican (actualizar valor, alta/activar/desactivar feriado, alta de límite de tasa).
    boolean puedeModificar = Boolean.TRUE.equals(request.getAttribute("puedeModificar"));
%>
<h1>Parámetros, feriados y límites de tasa</h1>

<a href="${pageContext.request.contextPath}/parametros/historial" class="btn btn-outline-secondary mb-3">
    <i class="fas fa-history"></i> Historial de cambios
</a>

<% if (!puedeModificar) { %>
<div class="alert alert-info"><i class="fas fa-lock"></i> Solo el administrador puede modificar los parámetros.</div>
<% } %>

<div class="card">
    <div class="card-header p-2">
        <ul class="nav nav-pills" id="pestanasAdmin" role="tablist">
            <li class="nav-item"><a class="nav-link active" data-toggle="pill" href="#tabParametros">Parámetros</a></li>
            <li class="nav-item"><a class="nav-link" data-toggle="pill" href="#tabFeriados">Feriados</a></li>
            <li class="nav-item"><a class="nav-link" data-toggle="pill" href="#tabLimites">Límites de tasa</a></li>
        </ul>
    </div>
    <div class="card-body">
        <div class="tab-content">

            <div class="tab-pane active" id="tabParametros">
                <%-- B220 (pedido de Joel, 15/11/2026): 52 parámetros en una tabla plana eran
                     imposibles de recorrer. Ahora van agrupados por el prefijo de la clave —que ya
                     es el orden de la consulta— y con el buscador en tabla de V11, el mismo que ya
                     usan el historial de al lado y siete reportes.
                     B220-bis (pedido de Joel al probarlo, el mismo día): la página seguía siendo
                     demasiado larga, así que (a) estas píldoras de grupo filtran la tabla sin
                     recargar, con el MISMO patrón y estilo que el índice de /reportes —de ahí sale
                     el CSS, no hay una segunda copia— y (b) cada fila muestra el valor y un botón
                     "Cambiar" que despliega su formulario debajo, uno a la vez. El orden de las
                     píldoras lo decide ServicioParametros.gruposConEtiqueta: la pantalla no
                     mantiene ninguna lista de grupos. --%>
                <div class="indice-reportes-categorias parametros-grupos">
                    <button type="button" class="indice-reportes-categoria-boton active"
                            data-buscar-en-tabla-grupo-boton="tablaParametros" data-grupo="todos">Todos</button>
                    <% for (Map.Entry<String, String> grupo : ServicioParametros.gruposConEtiqueta().entrySet()) { %>
                    <button type="button" class="indice-reportes-categoria-boton"
                            data-buscar-en-tabla-grupo-boton="tablaParametros"
                            data-grupo="<%= grupo.getKey() %>"><%= grupo.getValue() %></button>
                    <% } %>
                </div>
                <div class="d-flex justify-content-end align-items-center flex-wrap mb-2">
                    <div class="input-group input-group-sm barra-resultados-buscar-tabla">
                        <div class="input-group-prepend">
                            <span class="input-group-text"><i class="fas fa-search" aria-hidden="true"></i></span>
                        </div>
                        <input type="search" class="form-control" aria-label="Buscar en la tabla de parámetros"
                               placeholder="Buscar en la tabla…" data-buscar-en-tabla="tablaParametros">
                    </div>
                    <span class="barra-resultados-conteo-visible d-none ml-2" data-buscar-en-tabla-conteo="tablaParametros"></span>
                </div>
                <div class="table-responsive">
                <table class="table table-sm table-striped" id="tablaParametros">
                    <thead><tr><th>Parámetro</th><th>Valor</th><th class="text-nowrap">Vigente desde</th><% if (puedeModificar) { %><th>Cambiar</th><% } %></tr></thead>
                    <tbody>
                    <%
                        // Formato de importes (AVANCE.md D-44 / REGLAS_DESARROLLO.md §5): los unicos parametros ENTERO
                        // que son montos en guaranies (el resto de los ENTERO son dias, cuotas, intentos, etc.).
                        // RF39/D3 (pedido de Joel, 13/10/2026): caja.monto_maximo_arqueo se mostraba
                        // como "500000000" en vez de "Gs. 500.000.000" -- faltaba en esta lista.
                        // Tanda 2 (14/10/2026, pedido de Joel): credito.redondeo_efectivo agregada --
                        // mismo hueco, se mostraba "50" en vez de "50 Gs.".
                        java.util.Set<String> clavesGuaranies = new java.util.HashSet<>(java.util.Arrays.asList(
                                "credito.monto_minimo", "credito.monto_maximo", "caja.diferencia_tolerada",
                                "caja.monto_maximo_arqueo", "credito.redondeo_efectivo"));
                        // B220: el grupo cambia cuando cambia el prefijo de la clave, y la
                        // consulta ya viene ordenada por clave (ParametroDAO: ORDER BY
                        // clave_parametro), asi que un solo String alcanza para saber cuando
                        // dibujar el encabezado. El nombre legible lo da ServicioParametros.
                        String grupoDibujado = null;
                        int columnasTabla = puedeModificar ? 4 : 3;
                        if (listaParametros != null) {
                            for (ParametroSistema p : listaParametros) {
                                String grupoDeEsteParametro = ServicioParametros.grupoDeClave(p.getClaveParametro());
                                String prefijoDeEsteParametro = ServicioParametros.prefijoDeClave(p.getClaveParametro());
                                // B220-bis: el id de la fila de detalle sale de la clave (con
                                // guiones en vez de puntos, que no son validos en un id).
                                String idDetalle = "detalleParametro-" + p.getClaveParametro().replace('.', '-');
                                boolean esPorcentaje = p.getTipoValor() == TipoValorParametro.DECIMAL;
                                boolean esGuaranies = clavesGuaranies.contains(p.getClaveParametro());
                                boolean esFecha = p.getTipoValor() == TipoValorParametro.FECHA;
                                String valorMostrado;
                                if (esPorcentaje) {
                                    valorMostrado = FormateadorNumeros.comoPorcentaje(new java.math.BigDecimal(p.getValorParametro()));
                                } else if (esGuaranies) {
                                    valorMostrado = FormateadorNumeros.comoGuaranies(new java.math.BigDecimal(p.getValorParametro()));
                                } else if (esFecha) {
                                    // D-84: se guarda en ISO, pero se muestra DD/MM/AAAA como el resto del
                                    // sistema. Defensivo: si quedó un valor viejo mal cargado (no ISO), se
                                    // muestra tal cual en vez de romper la página -- se corrige con "Guardar".
                                    try {
                                        valorMostrado = FormateadorFechas.comoDdMmAaaa(java.time.LocalDate.parse(p.getValorParametro()));
                                    } catch (java.time.format.DateTimeParseException errorFormatoFecha) {
                                        valorMostrado = p.getValorParametro() + " (formato inválido -- corregir)";
                                    }
                                } else {
                                    // V1/RF39 (pedido de Joel): FormateadorTexto.comoListaConEspacios es el
                                    // UNICO lugar que decide esto (lo comparte con el historial y su PDF) --
                                    // solo agrega el espacio para TEXTO (listas como "A,B,C"), nunca para otro
                                    // tipo. p.getValorParametro() (el valor real, sin el espacio) es el que se
                                    // guarda y el que usaria el input de "Cambiar" si alguna vez mostrara un
                                    // valor por defecto -- hoy ese input siempre arranca vacio, asi que esto
                                    // no lo toca.
                                    valorMostrado = FormateadorTexto.comoListaConEspacios(p.getValorParametro(), p.getTipoValor());
                                }
                    %>
                    <% if (!grupoDeEsteParametro.equals(grupoDibujado)) {
                           grupoDibujado = grupoDeEsteParametro; %>
                    <%-- B220: fila de encabezado de grupo. La clase "buscar-en-tabla-grupo" le dice
                         a recursos/js/buscar-en-tabla.js que esto no es un dato: no se cuenta en
                         "Mostrando X de N" y se oculta si la busqueda no deja ninguna fila de su
                         grupo. --%>
                    <tr class="buscar-en-tabla-grupo" data-grupo="<%= prefijoDeEsteParametro %>">
                        <th colspan="<%= columnasTabla %>" class="bg-light"><%= grupoDibujado %></th>
                    </tr>
                    <% } %>
                    <tr data-grupo="<%= prefijoDeEsteParametro %>">
                        <td><%= p.getNombreParametro() %><br><small class="text-muted"><%= p.getClaveParametro() %></small></td>
                        <td class="valor-parametro"><%= valorMostrado %></td>
                        <td class="text-nowrap"><%= FormateadorFechas.comoDdMmAaaa(p.getVigenteDesde()) %></td>
                        <% if (puedeModificar) { %>
                        <td>
                            <%-- B220-bis: solo el boton. El formulario sigue en el HTML, en la fila
                                 de detalle de abajo, escondido por CSS -- ver el comentario de esa
                                 fila: ahi esta el token que lee el humo. --%>
                            <button type="button" class="btn btn-sm btn-outline-primary text-nowrap"
                                    data-buscar-en-tabla-detalle-boton="<%= idDetalle %>"
                                    aria-controls="<%= idDetalle %>" aria-expanded="false">Cambiar</button>
                        </td>
                        <% } %>
                    </tr>
                    <% if (puedeModificar) { %>
                    <%-- B220-bis: la fila de detalle con el formulario de ESTE parametro. Se
                         despliega con el boton de arriba (uno a la vez) y se oculta por CSS
                         (sgcpc-tema.css), NUNCA se saca del HTML: cada formulario lleva el token
                         CSRF, y pruebas/humo_http.sh lo lee del GET /parametros para sus cuatro
                         POST a /parametros/actualizar-valor. --%>
                    <tr class="buscar-en-tabla-detalle" id="<%= idDetalle %>" data-grupo="<%= prefijoDeEsteParametro %>">
                        <td colspan="<%= columnasTabla %>">
                            <%-- B215 (pedido de Joel, 15/11/2026): el campo arranca vacio y antes
                                 no decia ni cuanto vale hoy ni que valores se aceptan, asi que el
                                 limite se descubria cuando el servidor rechazaba el cambio.
                                 "Actual" usa el MISMO valorMostrado de la columna Valor (un solo
                                 formato, no una segunda verdad) y solo donde el formato importa
                                 --DECIMAL, ENTERO y FECHA--: en los TEXTO la columna ya muestra el
                                 valor entero. La regla la da ServicioParametros.reglaDeClave, que
                                 es el dueño de las validaciones; devuelve null para las claves sin
                                 regla cruzada y entonces la pantalla no promete nada.
                                 El defecto que Joel vio el 15/11/2026: "Actual: 500.000.000 Gs.."
                                 con DOS puntos -- el valor de los cinco parametros en guaranies YA
                                 termina en "Gs.", asi que el punto lo pone conPuntoFinal solo si
                                 falta (B188). --%>
                            <%
                                String reglaParametro = ServicioParametros.reglaDeClave(p.getClaveParametro());
                                boolean muestraActual = esPorcentaje || esGuaranies || esFecha
                                        || p.getTipoValor() == TipoValorParametro.ENTERO;
                            %>
                            <p class="text-muted mb-2">
                                <% if (muestraActual) { %><%= FormateadorTexto.conPuntoFinal("Actual: " + valorMostrado) %><% } %>
                                <% if (reglaParametro != null) { %><%= reglaParametro %><% } %>
                            </p>
                            <form method="post" action="${pageContext.request.contextPath}/parametros/actualizar-valor" class="form-inline">
                                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                                <input type="hidden" name="clave" value="<%= p.getClaveParametro() %>">
                                <% if (esFecha) { %>
                                <input type="date" name="valorNuevo" class="form-control form-control-sm mr-1" style="width: 160px;" required>
                                <% } else { %>
                                <input type="text" name="valorNuevo" class="form-control form-control-sm mr-1<%= esGuaranies ? " campo-guaranies" : "" %>" style="width: 160px;"
                                       placeholder="<%= esPorcentaje ? "valor nuevo (%)" : (esGuaranies ? "valor nuevo (Gs.)" : "valor nuevo") %>" required>
                                <% } %>
                                <%-- RF39/D1 (pedido de Joel, 13/10/2026): motivo obligatorio, se guarda en
                                     "observacion" y se muestra en /parametros/historial. Sin <label> propio
                                     (form-inline de tabla): aria-label para que pie.jsp nombre bien el campo
                                     en el aviso "Falta un dato" / "Entendido". --%>
                                <input type="text" name="motivo" class="form-control form-control-sm mr-1" style="width: 260px;"
                                       aria-label="Motivo del cambio" placeholder="Motivo del cambio" required minlength="10">
                                <button type="submit" class="btn btn-sm btn-primary">Guardar</button>
                            </form>
                        </td>
                    </tr>
                    <% } %>
                    <% } } %>
                    <tr class="buscar-en-tabla-sin-coincidencias d-none">
                        <td colspan="<%= columnasTabla %>" class="text-center text-muted">Sin coincidencias.</td>
                    </tr>
                    </tbody>
                </table>
                </div>
            </div>

            <div class="tab-pane" id="tabFeriados">
                <% if (puedeModificar) { %>
                <form method="post" action="${pageContext.request.contextPath}/parametros/feriados/nuevo" class="form-inline mb-3">
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                    <input type="date" name="fecha" class="form-control mr-2" required>
                    <input type="text" name="descripcion" class="form-control mr-2" placeholder="Descripción" required>
                    <button type="submit" class="btn btn-primary">Agregar feriado</button>
                </form>
                <% } %>
                <div class="table-responsive">
                <table class="table table-sm table-striped">
                    <thead><tr><th>Fecha</th><th>Descripción</th><th>Activo</th><% if (puedeModificar) { %><th>Acción</th><% } %></tr></thead>
                    <tbody>
                    <%
                        // B221 (pedido de Joel, 15/11/2026): la lista viene con los PROXIMOS
                        // primero (ServicioParametros.comparadorFeriados) y aca se dibuja el corte,
                        // una sola vez, cuando se pasa de los que vienen a los que ya pasaron. El
                        // "hoy" es el mismo Reloj que uso el servicio para ordenar.
                        boolean feriadosProximosDibujados = false;
                        boolean corteFeriadosDibujado = false;
                        java.time.LocalDate hoyFeriados = py.com.capitalcredito.sgcpc.infraestructura.Reloj.hoy();
                        int columnasFeriados = puedeModificar ? 4 : 3;
                        // B221, segunda vuelta (pedido de Joel, 15/11/2026): la seccion "Ya
                        // pasados" arranca PLEGADA, con un boton que dice cuantos son -- los 10
                        // vencidos de los 24 cargados hacian la lista larga sin aportar nada.
                        // Se cuentan antes del bucle porque el rotulo del boton se dibuja en el
                        // encabezado de la seccion, o sea ANTES de recorrer esas filas.
                        int cantidadFeriadosPasados = 0;
                        if (listaFeriados != null) {
                            for (Feriado f : listaFeriados) {
                                if (f.getFecha().isBefore(hoyFeriados)) {
                                    cantidadFeriadosPasados++;
                                }
                            }
                        }
                        if (listaFeriados != null) {
                        for (Feriado f : listaFeriados) {
                            boolean esteFeriadoViene = !f.getFecha().isBefore(hoyFeriados);
                            if (esteFeriadoViene && !feriadosProximosDibujados) {
                                feriadosProximosDibujados = true; %>
                    <tr><th colspan="<%= columnasFeriados %>" class="bg-light">Próximos</th></tr>
                    <%          }
                            if (!esteFeriadoViene && !corteFeriadosDibujado) {
                                corteFeriadosDibujado = true; %>
                    <tr><th colspan="<%= columnasFeriados %>" class="bg-light">
                        Ya pasados
                        <%-- B221, segunda vuelta: el boton despliega las filas de esta seccion, que
                             nacen con "d-none" desde el servidor (asi no hay un parpadeo en el que
                             se vean y despues desaparezcan). El rotulo lo cambia el script del pie
                             de esta misma pantalla. --%>
                        <button type="button" class="btn btn-sm btn-link p-0 ml-2" id="botonFeriadosPasados"
                                aria-expanded="false">Ver los <%= cantidadFeriadosPasados %> ya pasados</button>
                    </th></tr>
                    <%          } %>
                    <tr<%= esteFeriadoViene ? "" : " class=\"feriado-pasado d-none\"" %>>
                        <td><%= FormateadorFechas.comoDdMmAaaa(f.getFecha()) %></td>
                        <td><%= f.getDescripcion() %></td>
                        <td><%= f.isActivo() ? "Sí" : "No" %></td>
                        <% if (puedeModificar) { %>
                        <td>
                            <% if (f.isActivo()) { %>
                            <form method="post" action="${pageContext.request.contextPath}/parametros/feriados/desactivar" class="d-inline"
                                  data-confirmar-titulo="¿Desactivar el feriado &quot;<%= f.getDescripcion() %>&quot;?" data-confirmar=""
                                  data-confirmar-boton="Sí, desactivar">
                                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                                <input type="hidden" name="idFeriado" value="<%= f.getIdFeriado() %>">
                                <button type="submit" class="btn btn-sm btn-secondary">Desactivar</button>
                            </form>
                            <% } else { %>
                            <%-- B217 (pedido de Joel, 15/11/2026): "Activar" pedía confirmación y
                                 "Desactivar" no, en la misma pestaña y para la misma clase de
                                 cambio. Mismo molde que su gemelo: el título pregunta y el cuerpo
                                 va vacío; sin foco en cancelar, porque es reversible (B129/B105). --%>
                            <form method="post" action="${pageContext.request.contextPath}/parametros/feriados/activar" class="d-inline"
                                  data-confirmar-titulo="¿Activar el feriado &quot;<%= f.getDescripcion() %>&quot;?" data-confirmar=""
                                  data-confirmar-boton="Sí, activar">
                                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                                <input type="hidden" name="idFeriado" value="<%= f.getIdFeriado() %>">
                                <button type="submit" class="btn btn-sm btn-secondary">Activar</button>
                            </form>
                            <% } %>
                        </td>
                        <% } %>
                    </tr>
                    <% } } %>
                    </tbody>
                </table>
                </div>
                <p class="text-muted"><small>Revisar esta lista cada año: el Poder Ejecutivo a veces traslada feriados por decreto.</small></p>
            </div>

            <div class="tab-pane" id="tabLimites">
                <% if (puedeModificar) { %>
                <form method="post" action="${pageContext.request.contextPath}/parametros/limites-tasa/nuevo" class="form-inline mb-3">
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                    <input type="date" name="vigenteDesde" class="form-control mr-2" required>
                    <input type="text" name="tasaMaximaTea" class="form-control mr-2" style="width: 120px;" placeholder="27,39 (%)" aria-label="Tasa máxima (TEA)" required>
                    <input type="text" name="fuenteReferencia" class="form-control mr-2" placeholder="Fuente/referencia" required>
                    <button type="submit" class="btn btn-primary">Agregar límite</button>
                </form>
                <% } %>
                <div class="table-responsive">
                <table class="table table-sm table-striped">
                    <thead><tr><th>Vigente desde</th><th>Vigente hasta</th><th>Tasa máxima TEA</th><th>Fuente</th></tr></thead>
                    <tbody>
                    <%
                        // B224 y B223 (opcion C), 15/11/2026: la columna decia "(vigente)" para
                        // cualquier limite sin fecha de cierre, incluido uno PROGRAMADO para el
                        // futuro -- que ahora, correctamente, no rige hoy. Y la opcion C deja filas
                        // con la vigencia vacia (cerradas el dia anterior a su propio inicio), que
                        // son las que se cargaron mal y se reemplazaron el mismo dia: en vez de
                        // mostrar una vigencia al reves, la pantalla dice que no rigio.
                        java.time.LocalDate hoyLimites = py.com.capitalcredito.sgcpc.infraestructura.Reloj.hoy();
                        if (listaLimites != null) {
                        for (LimiteTasaUsura l : listaLimites) {
                            boolean limiteNoRigio = l.getVigenteHasta() != null
                                    && l.getVigenteHasta().isBefore(l.getVigenteDesde());
                            boolean limiteProgramado = l.getVigenteHasta() == null
                                    && l.getVigenteDesde().isAfter(hoyLimites);
                            String vigenciaMostrada;
                            if (limiteNoRigio) {
                                vigenciaMostrada = "No rigió (reemplazado)";
                            } else if (limiteProgramado) {
                                vigenciaMostrada = "Rige desde el "
                                        + FormateadorFechas.comoDdMmAaaa(l.getVigenteDesde());
                            } else if (l.getVigenteHasta() == null) {
                                vigenciaMostrada = "(vigente)";
                            } else {
                                vigenciaMostrada = FormateadorFechas.comoDdMmAaaa(l.getVigenteHasta());
                            }
                    %>
                    <tr<%= limiteNoRigio ? " class=\"text-muted\"" : "" %>>
                        <td><%= FormateadorFechas.comoDdMmAaaa(l.getVigenteDesde()) %></td>
                        <td><%= vigenciaMostrada %></td>
                        <td><%= FormateadorNumeros.comoPorcentaje(l.getTasaMaximaTea()) %></td>
                        <td><%= l.getFuenteReferencia() %></td>
                    </tr>
                    <% } } %>
                    </tbody>
                </table>
                </div>
            </div>

        </div>
    </div>
</div>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
<script>
    // B.13 (consolidación, pedido de Joel): agregar/desactivar un feriado volvía siempre a la
    // pestaña "Parámetros" -- ControladorParametrosServlet ahora redirige con "#tabFeriados"
    // (y similar para otras pestañas en el futuro); esto activa la pestaña indicada por el hash
    // al cargar la página, en vez de la primera por defecto. Va después de pie.jsp porque ahí
    // se carga jQuery/Bootstrap (al final del body).
    $(function () {
        if (window.location.hash) {
            $('#pestanasAdmin a[href="' + window.location.hash + '"]').tab('show');
        }
    });

    // B221, segunda vuelta (pedido de Joel, 15/11/2026): "Ya pasados" arranca plegada. El boton
    // despliega y vuelve a plegar, y dice en los dos estados qué va a hacer. Vive acá, en la
    // pantalla, y no en un script compartido: es un plegado de una sección de ESTA tabla, que
    // además no tiene buscador (así que el mecanismo de filas de detalle de buscar-en-tabla.js
    // no corre para ella).
    $(function () {
        var botonPasados = document.getElementById('botonFeriadosPasados');
        if (!botonPasados) {
            return;
        }
        var filasPasadas = document.querySelectorAll('tr.feriado-pasado');
        var rotuloVer = botonPasados.textContent.trim();
        var rotuloOcultar = rotuloVer.replace('Ver los', 'Ocultar los');
        botonPasados.addEventListener('click', function () {
            var estabanOcultas = filasPasadas.length > 0 && filasPasadas[0].classList.contains('d-none');
            for (var i = 0; i < filasPasadas.length; i++) {
                filasPasadas[i].classList.toggle('d-none', !estabanOcultas);
            }
            botonPasados.textContent = estabanOcultas ? rotuloOcultar : rotuloVer;
            botonPasados.setAttribute('aria-expanded', estabanOcultas ? 'true' : 'false');
        });
    });
</script>
