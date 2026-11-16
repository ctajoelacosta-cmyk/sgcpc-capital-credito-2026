<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Usuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario" %>
<%@ page import="py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion" %>
<%-- B234 (pedido de Joel, 16/11/2026): los accesos rapidos se dibujan preguntandole al DUENO
     de los permisos (ServicioAutorizacion), no copiando una condicion de menu.jsp. Es el mismo
     servicio que usa FiltroAutorizacion para bloquear, asi que un acceso dibujado no puede
     terminar en un 403 -- V5 punto 4, pero preguntando en vez de copiar. --%>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.Permiso" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.IndicadoresPanel" %>
<%-- B229 (pedido de Joel, 16/11/2026): import propio de FormateadorNumeros -- cabecera.jsp
     no lo trae (B169). --%>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.Reloj" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%!
    /** V5-bis (pedido de Joel): separa el sufijo " Gs." que agrega FormateadorNumeros.comoGuaranies
     * para poder mostrarlo más chico y gris, pegado al número -- sin tocar esa clase ni ningún
     * otro Java, esto es puro armado de HTML dentro de esta misma JSP. Si el texto no termina en
     * " Gs." (no debería pasar para los valores que la usan, pero por si acaso) lo devuelve tal
     * cual, sin el <span>. */
    private String conSufijoGuaranies(String monto) {
        if (monto == null) {
            return "";
        }
        if (!monto.endsWith(" Gs.")) {
            return monto;
        }
        return monto.substring(0, monto.length() - 4) + "<span class=\"panel-tarjeta-numero-sufijo\">Gs.</span>";
    }
%>
<%
    Usuario usuarioPanel = (Usuario) session.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
    IndicadoresPanel indicadoresPanel = (IndicadoresPanel) request.getAttribute("indicadores");
    // D7 (pedido de Joel): el administrador ve lo mismo que el gerente en el panel.
    boolean esGerentePanel = usuarioPanel.getPerfilUsuario() == PerfilUsuario.GERENTE_ADMINISTRADOR
            || usuarioPanel.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR;
    boolean esOficialPanel = usuarioPanel.getPerfilUsuario() == PerfilUsuario.OFICIAL_CREDITO;
    boolean esCajeroPanel = usuarioPanel.getPerfilUsuario() == PerfilUsuario.CAJERO;
    // V5 (pedido de Joel): ninguna de las tres de arriba cambia -- siguen decidiendo EXACTAMENTE
    // qué tarjetas ve cada perfil, igual que antes. Lo único nuevo es esAdministradorPanel, para
    // poder armar los enlaces de abajo con las MISMAS condiciones que menu.jsp (que sí distingue
    // gerente de administrador en varias rutas, ej. /cobranza y /desembolso son del cajero y el
    // administrador, pero NO del gerente).
    boolean esAdministradorPanel = usuarioPanel.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR;

    // V5, punto 4 (pedido de Joel): el enlace "Ir a..." de cada tarjeta se muestra SOLO si el
    // perfil tiene esa ruta en menu.jsp -- mismas condiciones, copiadas de ahí (no se tocó
    // menu.jsp ni FiltroAutorizacion, que sigue siendo el bloqueo real). Dos de estas quedan
    // MÁS estrictas que el "sin condición" de antes: /cobranza y /desembolso son del cajero y el
    // administrador en menu.jsp, pero NO del gerente -- hoy el panel le mostraba el enlace igual
    // (quedaba un enlace a una pantalla que el servidor le iba a bloquear). Las demás ya
    // coincidían (por eso no cambian de resultado para ningún perfil real), se dejan explícitas
    // acá para que las ocho queden con el mismo criterio, no algunas sí y otras por casualidad.
    boolean verEnlaceCobranza = esCajeroPanel || esAdministradorPanel;
    boolean verEnlaceMora = esOficialPanel || esGerentePanel;
    boolean verEnlaceDesembolso = esCajeroPanel || esAdministradorPanel;
    boolean verEnlaceCaja = esCajeroPanel || esGerentePanel;
    boolean verEnlaceAprobaciones = esGerentePanel;
    boolean verEnlaceReversiones = esGerentePanel;
    boolean verEnlaceSolicitudes = esOficialPanel || esAdministradorPanel;
    // B45/B76 (28/10/2026): mismas condiciones que menu.jsp para /formalizacion (verFormalizacion,
    // línea 118) y para "Devolución del pagaré" → /formalizacion#cierre-documental
    // (verDevolucionPagare, línea 125) -- los dos coinciden, así que un solo flag alcanza para los
    // dos enlaces nuevos. Mismo criterio V5 punto 4: el enlace no agrega ningún permiso, el
    // bloqueo real sigue siendo FiltroAutorizacion (/formalizacion →
    // GENERAR_DOCUMENTOS_FORMALIZACION, que el oficial tiene TOTAL por D-68).
    boolean verEnlaceFormalizacion = esOficialPanel || esGerentePanel || esAdministradorPanel;

    // B234: que accesos rapidos ve este perfil. NO se llaman "es...Panel" a proposito: ese
    // prefijo lo cuenta el test del proyecto hermano (TarjetasPanelApiTest) como una guarda de
    // visibilidad de tarjeta, y estos no lo son.
    //  - Cotizar: los cuatro perfiles lo tienen en TOTAL (medido en ServicioAutorizacion).
    //  - Nuevo cliente: exige TOTAL en REGISTRAR_EDITAR_CLIENTE, y la cajera lo tiene en
    //    CONSULTA -- /clientes deja pasar su GET pero el POST pide TOTAL (FiltroAutorizacion
    //    linea 42), asi que ofrecerselo seria mandarla a un formulario que no puede guardar.
    ServicioAutorizacion servicioAutorizacionPanel = new ServicioAutorizacion();
    boolean verAccesoCotizador = servicioAutorizacionPanel.tieneAccesoTotal(
            usuarioPanel.getPerfilUsuario(), Permiso.COTIZAR);
    boolean verAccesoNuevoCliente = servicioAutorizacionPanel.tieneAccesoTotal(
            usuarioPanel.getPerfilUsuario(), Permiso.REGISTRAR_EDITAR_CLIENTE);

    // V5, punto 2: cada bloque se oculta si ninguna de sus tarjetas aplica. "Hoy" y "Cartera"
    // siempre tienen al menos una (Cobros de hoy y Préstamos en mora no llevan condición, se ven
    // siempre) -- "Pendientes" sí puede quedar vacío según el perfil, por eso se calcula.
    boolean mostrarBloquePendientes = esCajeroPanel || esGerentePanel || esOficialPanel;

    // V5, punto 1: saludo según la hora del SERVIDOR (Reloj.ahora(), no la del navegador del
    // usuario -- mismo criterio que la fecha de la barra superior, V4).
    int horaServidorPanel = Reloj.ahora().getHour();
    String saludoHorarioPanel = horaServidorPanel < 12 ? "Buenos días"
            : horaServidorPanel < 19 ? "Buenas tardes" : "Buenas noches";
%>
<h1>Panel</h1>
<p class="panel-saludo"><%= saludoHorarioPanel %>, <%= usuarioPanel.getNombres() %></p>
<p class="panel-subtitulo">Este es el resumen de hoy.</p>

<%-- B234: lo que ARRANCA el circuito. Las 14 tarjetas son trabajo en curso; el oficial, que
     empieza el dia cotizando o cargando un cliente, no tenia desde aca ninguna forma de
     hacerlo y tenia que ir al menu. Si el perfil no tiene ninguno de los dos, el bloque no se
     dibuja (mismo criterio que V5 punto 2 con los bloques vacios). --%>
<% if (verAccesoCotizador || verAccesoNuevoCliente) { %>
<div class="panel-accesos">
    <% if (verAccesoCotizador) { %>
    <a href="${pageContext.request.contextPath}/cotizador" class="btn btn-sm btn-outline-primary">
        <i class="fas fa-calculator mr-1" aria-hidden="true"></i>Cotizador</a>
    <% } %>
    <% if (verAccesoNuevoCliente) { %>
    <a href="${pageContext.request.contextPath}/clientes/nuevo" class="btn btn-sm btn-outline-primary">
        <i class="fas fa-user-plus mr-1" aria-hidden="true"></i>Nuevo cliente</a>
    <% } %>
</div>
<% } %>

<% if (indicadoresPanel != null && !indicadoresPanel.getAvisosTimbrado().isEmpty()) { %>
<div class="alert alert-warning">
    <% for (String avisoTimbrado : indicadoresPanel.getAvisosTimbrado()) { %>
    <div><i class="fas fa-exclamation-triangle mr-1"></i><%= avisoTimbrado %></div>
    <% } %>
</div>
<% } %>

<%-- Continuidad del administrador (hallazgo de Joel): solo el administrador la ve -- ya viene
     resuelto por ControladorPanelServlet (cuenta <2 administradores ACTIVOS). --%>
<% if (request.getAttribute("avisoContinuidadAdministradores") != null) { %>
<div class="alert alert-warning">
    <i class="fas fa-exclamation-triangle mr-1"></i><%= request.getAttribute("avisoContinuidadAdministradores") %>
    <%-- B231 (pedido de Joel, 16/11/2026): el aviso decia QUE hacer y no DONDE. El enlace se
         agrega aca, como elemento aparte: la FRASE no se toca porque el proyecto hermano
         (../sgcpc-api, PanelApiServlet) tiene una copia de ese mismo texto para la
         aplicacion movil, y cambiarla las haria divergir. "Usuarios" es exclusivo del
         administrador (D-151), que es justo el unico perfil que ve este aviso, asi que el
         enlace nunca apunta a una pantalla que el servidor le bloquearia (V5 punto 4). --%>
    <a href="${pageContext.request.contextPath}/usuarios" class="alert-link ml-1">Ir a Usuarios <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
</div>
<% } %>

<% if (indicadoresPanel != null) { %>

<%-- Bloque "Hoy": cobros de hoy, promesas de hoy, caja. --%>
<h2 class="panel-bloque-titulo">Hoy</h2>
<div class="row">
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-azul">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-coins" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getCantidadCobrosHoy()) %></div>
                    <%-- B107 (B86, tramo 3): el monto es lo APLICADO A LA DEUDA
                         (pagoDAO.sumarAplicadoEntreFechas suma monto_aplicado), no el efectivo que
                         entró a la caja. Los dos pueden diferir en unos guaraníes por el redondeo a
                         favor del cliente (D-141/D-142): el 01/11/2026 acá decía 844.004 y la caja
                         844.000, los dos correctos, y quien los comparaba creía que la caja estaba
                         descuadrada. Solo la etiqueta: el cálculo no se toca. --%>
                    <div class="panel-tarjeta-descripcion">Cobro<%= indicadoresPanel.getCantidadCobrosHoy() == 1 ? "" : "s" %> de hoy, aplicado a la deuda (<%= indicadoresPanel.getMontoCobrosHoy() %>)</div>
                </div>
            </div>
            <%-- B232 (pedido de Joel, 16/11/2026): esta tarjeta ya NO lleva "Ir a Cobranza".
                 El mismo enlace estaba aca y en la tarjeta vecina ("Vencen hoy"), pegados y
                 al mismo lugar. Queda el de "Vencen hoy", que es el que manda a TRABAJAR --
                 para eso la creo B99, porque ninguna tarjeta llevaba a cobrar lo que vence
                 hoy. Esta es un resultado del dia, no una tarea. --%>
        </div>
    </div>
    <%-- B99 (30/10/2026, hallazgo de Joel en el paso 15): lo que FALTA cobrar hoy, al lado de lo
         ya cobrado. Mismo dato y mismo criterio que la lista de /cobranza (los préstamos ya en
         mora no se cuentan acá: tienen su propia tarjeta). --%>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-cian">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-calendar-day" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getVencenHoyCantidad()) %></div>
                    <div class="panel-tarjeta-descripcion">Vencen hoy<%= indicadoresPanel.getVencenHoyCantidad() == 0
                            ? "" : " (" + indicadoresPanel.getVencenHoyMonto() + ")" %></div>
                </div>
            </div>
            <% if (verEnlaceCobranza) { %>
            <a href="${pageContext.request.contextPath}/cobranza" class="panel-tarjeta-enlace">Ir a Cobranza <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <%-- B45 (FASE2_PLAN.md, confirmado en pantalla el 23/10/2026; decisión de Joel del
         28/10/2026): va en "Hoy", no en "Pendientes" -- el cliente está esperando su dinero, así
         que el oficial tiene que verla sin bajar la pantalla (mismo motivo por el que
         "Solicitudes pendientes de aprobar" se movió acá en RF07). Segunda de la fila, apenas
         después de "Cobros de hoy". Solo del oficial: el administrador no la ve, igual que no ve
         "Mis solicitudes en curso". --%>
    <% if (esOficialPanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-indigo">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-file-contract" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getAprobadasParaFormalizar()) %></div>
                    <div class="panel-tarjeta-descripcion">Solicitud<%= indicadoresPanel.getAprobadasParaFormalizar() == 1 ? "" : "es" %>
                        aprobada<%= indicadoresPanel.getAprobadasParaFormalizar() == 1 ? "" : "s" %> para formalizar</div>
                </div>
            </div>
            <% if (verEnlaceFormalizacion) { %>
            <a href="${pageContext.request.contextPath}/formalizacion" class="panel-tarjeta-enlace">Ir a Formalización <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <% if (esOficialPanel || esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-violeta">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-calendar-check" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getPromesasDeHoy()) %></div>
                    <div class="panel-tarjeta-descripcion">Promesa<%= indicadoresPanel.getPromesasDeHoy() == 1 ? "" : "s" %> de hoy</div>
                </div>
            </div>
            <% if (verEnlaceMora) { %>
            <a href="${pageContext.request.contextPath}/mora" class="panel-tarjeta-enlace">Ir a Seguimiento de mora <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <% if (esCajeroPanel || esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta <%= indicadoresPanel.isHayCajaAbierta() ? "panel-tarjeta-frambuesa" : "panel-tarjeta-gris" %>">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-cash-register" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <% if (indicadoresPanel.isHayCajaAbierta()) { %>
                    <div class="panel-tarjeta-numero panel-tarjeta-numero-monto"><%= conSufijoGuaranies(indicadoresPanel.getEfectivoEnCaja()) %></div>
                    <% } else { %>
                    <div class="panel-tarjeta-numero panel-tarjeta-numero-texto">Sin caja abierta</div>
                    <% } %>
                    <div class="panel-tarjeta-descripcion">Efectivo en caja</div>
                </div>
            </div>
            <% if (verEnlaceCaja) { %>
            <a href="${pageContext.request.contextPath}/caja" class="panel-tarjeta-enlace">Ir a Caja <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <%-- RF07 (pedido de Joel, 10/10/2026): movida acá arriba, a "Hoy", para el gerente y el
         administrador -- estaba más abajo, en "Pendientes" (después de "Hoy" y "Cartera"), y a
         Joel no le apareció en pantalla porque tenía que seguir bajando para encontrarla. No se
         encontró ningún error en esGerentePanel (ya incluye ADMINISTRADOR, D7) ni en el cálculo
         del número (ServicioPanel, dinámico) -- el cambio es solo de ubicación. --%>
    <% if (esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-indigo">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-stamp" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getSolicitudesPendientesAprobar()) %></div>
                    <div class="panel-tarjeta-descripcion">Solicitud<%= indicadoresPanel.getSolicitudesPendientesAprobar() == 1 ? "" : "es" %> pendiente<%= indicadoresPanel.getSolicitudesPendientesAprobar() == 1 ? "" : "s" %> de aprobar</div>
                </div>
            </div>
            <% if (verEnlaceAprobaciones) { %>
            <a href="${pageContext.request.contextPath}/aprobaciones" class="panel-tarjeta-enlace">Ir a Aprobaciones <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
</div>

<%-- Bloque "Pendientes": listos para desembolsar, solicitudes por aprobar, reversiones por
     autorizar. "Mis solicitudes en curso" (oficial) se agrega acá también -- Joel no la nombró
     en los 3 bloques del pedido, pero por significado ("en curso"/pendiente de avanzar) encaja
     mejor acá que en "Hoy" o "Cartera"; si prefieras otro lugar, se mueve fácil. --%>
<% if (mostrarBloquePendientes) { %>
<h2 class="panel-bloque-titulo">Pendientes</h2>
<div class="row">
    <% if (esCajeroPanel || esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-azulacero">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-hand-holding-usd" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <%-- V5-bis (pedido de Joel): el número va solo ("22"), sin la palabra
                         "pendiente(s)" -- se mueve a la descripción, que ya explicaba el resto
                         ("Y listos para desembolsar (Z)"). Mismos valores de siempre
                         (getDesembolsosPendientes/getDesembolsosListos/getTotalAProveer), nada
                         cambia salvo dónde cae cada palabra. (13/10/2026, pedido de Joel): la
                         descripción decía solo "pendiente(s)" sin decir de qué -- ahora dice
                         "préstamo(s) pendiente(s) de desembolso". Mismo bloque para cajera y
                         gerente (esCajeroPanel || esGerentePanel), así que el cambio ya cubre
                         los dos perfiles que ven esta tarjeta. --%>
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getDesembolsosPendientes()) %></div>
                    <div class="panel-tarjeta-descripcion">préstamo<%= indicadoresPanel.getDesembolsosPendientes() == 1 ? "" : "s" %>
                        pendiente<%= indicadoresPanel.getDesembolsosPendientes() == 1 ? "" : "s" %> de desembolso ·
                        <%= indicadoresPanel.getDesembolsosListos() %> listo<%= indicadoresPanel.getDesembolsosListos() == 1 ? "" : "s" %>
                        para desembolsar (<%= indicadoresPanel.getTotalAProveer() %>)</div>
                    <%-- B137 (11/11/2026, decisión de Joel en la revisión final): acá iba la lista
                         de lo que falta cargar, que puso B100 (01/11/2026, hallazgo suyo). Se saca de
                         la tarjeta por dos motivos que se vieron juntos: (1) la lista sale del
                         checklist de TODOS los préstamos que no están listos
                         (ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar agrega los
                         incompletos), así que pegada al "1 listo para desembolsar" se lee como si le
                         faltara a ÉSE; y (2) son tres renglones más, que dejaban a esta tarjeta en
                         siete -- la única del Panel con esa altura -- y empujaban su enlace abajo del
                         borde de la pantalla a 1280x800 en el panel de la cajera.
                         Lo que B100 quería que se supiera NO se pierde: el enlace de esta misma
                         tarjeta lleva a Formalización justo cuando falta cargar algo, y ahí el
                         checklist dice, préstamo por préstamo, qué documento falta.
                         `IndicadoresPanel.getFaltaParaDesembolsar()` y su armador se dejan en su
                         lugar, con su JUnit: no son código muerto, son el dato que decide a dónde
                         lleva el enlace de abajo. --%>
                </div>
            </div>
            <%-- B100: el enlace. Mismo criterio V5 punto 4 (las condiciones salen de menu.jsp, el
                 bloqueo real sigue siendo FiltroAutorizacion): /desembolso es del cajero y el
                 administrador, así que el gerente se quedaba SIN ningún enlace en esta tarjeta.
                 Cuando falta cargar algo, el paso siguiente no es Desembolsos: es Formalización,
                 que el gerente sí puede abrir (GENERAR_DOCUMENTOS_FORMALIZACION, D-68). --%>
            <% if (verEnlaceDesembolso) { %>
            <a href="${pageContext.request.contextPath}/desembolso" class="panel-tarjeta-enlace">Ir a Desembolsos <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } else if (verEnlaceFormalizacion && !indicadoresPanel.getFaltaParaDesembolsar().isEmpty()) { %>
            <a href="${pageContext.request.contextPath}/formalizacion" class="panel-tarjeta-enlace">Ir a Formalización <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <%-- B63 (FASE2_PLAN.md, confirmado en pantalla el 23/10/2026): al cerrar con diferencia, el
         sistema le dice a la cajera "Queda pendiente de revisión del gerente" -- pero al gerente
         nada se lo avisaba. Va pegada a la de desembolsos, para que las dos de caja queden
         juntas. El enlace apunta al ancla de la sección "Diferencias de arqueo sin revisar" de
         /caja (id agregado en caja/ficha.jsp, autorizado por Joel el 28/10/2026), que es donde
         el gerente las revisa. Gerente y administrador (D7), nunca la cajera: revisar una
         diferencia es AUTORIZAR_REVERSION_O_DIFERENCIA, permiso que ella no tiene. --%>
    <% if (esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-frambuesa">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-balance-scale" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getDiferenciasDeCajaSinRevisar()) %></div>
                    <div class="panel-tarjeta-descripcion">Diferencia<%= indicadoresPanel.getDiferenciasDeCajaSinRevisar() == 1 ? "" : "s" %>
                        de caja por revisar</div>
                </div>
            </div>
            <% if (verEnlaceCaja) { %>
            <a href="${pageContext.request.contextPath}/caja#diferencias-arqueo" class="panel-tarjeta-enlace">Ir a Caja <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <% if (esGerentePanel) { %>
    <%-- E9/Bloque 5 (PO-13): reemplaza el "-" placeholder del diseño del bloque 5 -- reversiones
         de cobro SOLICITADA esperando que el gerente autorice o rechace. La tarjeta de
         "Solicitudes pendientes de aprobar" que estaba acá se movió a "Hoy" (RF07, 10/10/2026). --%>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-ciruela">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-undo" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getReversionesPorAutorizar()) %></div>
                    <div class="panel-tarjeta-descripcion"><%= indicadoresPanel.getReversionesPorAutorizar() == 1 ? "Reversión" : "Reversiones" %> por autorizar</div>
                </div>
            </div>
            <% if (verEnlaceReversiones) { %>
            <a href="${pageContext.request.contextPath}/reversiones" class="panel-tarjeta-enlace">Ir a Reversiones <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
    <% if (esOficialPanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-azulacero">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-file-signature" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getMisSolicitudesEnCurso()) %></div>
                    <div class="panel-tarjeta-descripcion"><%= indicadoresPanel.getMisSolicitudesEnCurso() == 1 ? "Mi solicitud" : "Mis solicitudes" %> en curso</div>
                </div>
            </div>
            <% if (verEnlaceSolicitudes) { %>
            <a href="${pageContext.request.contextPath}/solicitudes" class="panel-tarjeta-enlace">Ir a Solicitudes <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <%-- B76 (FASE2_PLAN.md, confirmado en pantalla el 25/10/2026): el préstamo ya está pagado,
         pero el pagaré original sigue en la caja fuerte hasta que se devuelva (RF29) -- el panel
         del oficial no lo avisaba. Acá en "Pendientes" (decisión de Joel del 28/10/2026): es
         trabajo pendiente, no algo que apremie hoy como B45. El enlace usa el ancla
         #cierre-documental, que ya existe en formalizacion/_resultados.jsp y ya tiene su scroll
         suave y su resaltado de menú ("Cancelación" → "Devolución del pagaré") resueltos por el
         script V3h de pie.jsp -- dispara con el hash, sin importar de dónde se llegó. Solo del
         oficial, igual que B45. --%>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-ciruela">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-file-contract" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getCierresDocumentalesPendientes()) %></div>
                    <%-- B85 (28/10/2026, aprobado por Joel): el texto dice el trámite, no el
                         nombre interno del estado -- igual que el título de la pantalla y el
                         enlace del menú. --%>
                    <div class="panel-tarjeta-descripcion">Pagaré<%= indicadoresPanel.getCierresDocumentalesPendientes() == 1 ? "" : "s" %>
                        pendiente<%= indicadoresPanel.getCierresDocumentalesPendientes() == 1 ? "" : "s" %> de devolución</div>
                </div>
            </div>
            <% if (verEnlaceFormalizacion) { %>
            <a href="${pageContext.request.contextPath}/formalizacion#cierre-documental" class="panel-tarjeta-enlace">Ir a la devolución del pagaré <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% } %>
</div>
<% } %>

<%-- Bloque "Cartera": préstamos en mora, cartera en la calle, cobrado semana, cobrado mes. --%>
<h2 class="panel-bloque-titulo">Cartera</h2>
<div class="row">
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-rojo">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-user-clock" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero"><%= FormateadorNumeros.comoCantidad(indicadoresPanel.getPrestamosEnMora()) %></div>
                    <div class="panel-tarjeta-descripcion">Préstamo<%= indicadoresPanel.getPrestamosEnMora() == 1 ? "" : "s" %> en mora</div>
                </div>
            </div>
            <% if (verEnlaceMora) { %>
            <a href="${pageContext.request.contextPath}/mora" class="panel-tarjeta-enlace">Ir a Seguimiento de mora <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
            <% } %>
        </div>
    </div>
    <% if (esGerentePanel) { %>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-petroleo">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-money-bill-wave" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero panel-tarjeta-numero-monto"><%= conSufijoGuaranies(indicadoresPanel.getCarteraEnLaCalle()) %></div>
                    <div class="panel-tarjeta-descripcion">Cartera en la calle (capital pendiente)</div>
                </div>
            </div>
        </div>
    </div>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-indigo">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-coins" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero panel-tarjeta-numero-monto"><%= conSufijoGuaranies(indicadoresPanel.getCobradoSemana()) %></div>
                    <%-- B107: misma fuente que "Cobros de hoy" (monto_aplicado), misma aclaración. --%>
                    <div class="panel-tarjeta-descripcion">Cobrado esta semana (aplicado a la deuda)</div>
                </div>
            </div>
        </div>
    </div>
    <div class="col-lg-3 col-md-6 mb-3">
        <div class="panel-tarjeta panel-tarjeta-esmeralda">
            <div class="panel-tarjeta-cuerpo">
                <span class="panel-tarjeta-icono"><i class="fas fa-coins" aria-hidden="true"></i></span>
                <div class="panel-tarjeta-texto">
                    <div class="panel-tarjeta-numero panel-tarjeta-numero-monto"><%= conSufijoGuaranies(indicadoresPanel.getCobradoMes()) %></div>
                    <%-- B107: misma fuente que "Cobros de hoy" (monto_aplicado), misma aclaración. --%>
                    <div class="panel-tarjeta-descripcion">Cobrado este mes (aplicado a la deuda)</div>
                </div>
            </div>
        </div>
    </div>
    <% } %>
</div>


<% } else { %>
<%-- B230 (pedido de Joel, 16/11/2026): si la base falla, ControladorPanelServlet deja los
     indicadores en null y antes TODO el tablero desaparecia sin una palabra -- el error
     llegaba como modal y, al cerrarlo, quedaba el saludo sobre una pantalla vacia. Y es la
     primera pantalla despues de entrar. Este bloque dice el ESTADO y como salir; no repite
     la frase del modal (que es la del controlador), para no decir dos veces lo mismo con
     palabras distintas (B207). --%>
<div class="alert alert-danger">
    <i class="fas fa-exclamation-circle mr-1" aria-hidden="true"></i>
    <strong>Sin indicadores para mostrar.</strong>
    No se pudieron leer los datos del panel en este momento; el resto del sistema sigue
    disponible desde el menú de la izquierda.
    <a href="${pageContext.request.contextPath}/panel" class="alert-link ml-1">Volver a intentar <i class="fas fa-redo" aria-hidden="true"></i></a>
</div>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
