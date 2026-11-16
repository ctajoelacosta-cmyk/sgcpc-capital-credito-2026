<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Persona" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaReferencia" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaConyuge" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaLaboral" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.PersonaBaja" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.TipoDocumento" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Departamento" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Ciudad" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ItemExpediente" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.PasoGuiado" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaHistorialCredito" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud" %>
<%@ page import="py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioClientes" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    Persona personaFicha = (Persona) request.getAttribute("persona");
    // Atributo explicito (no "personaFicha == null"): tras un alta rechazada, personaFicha trae
    // lo que la persona ya habia escrito aunque todavia no exista en la base (ver
    // ControladorClientesServlet.mostrarFicha). Los valores de los campos, mas abajo, se basan
    // en "personaFicha == null" (no en esNueva): asi se repueblan igual en ese caso.
    // Normalización de personas, grupo Referencias (15/10/2026): ya resueltas por el controlador
    // (de personas_referencias, o de lo tipeado si el guardado se rechazó) -- nunca null acá.
    PersonaReferencia referencia1Ficha = (PersonaReferencia) request.getAttribute("referencia1");
    PersonaReferencia referencia2Ficha = (PersonaReferencia) request.getAttribute("referencia2");
    // Normalización de personas, grupo Cónyuge (16/10/2026): puede ser null (sin cónyuge
    // registrado, o ficha nueva) -- cada campo más abajo revisa null, mismo criterio que
    // referencia1Ficha/referencia2Ficha.
    PersonaConyuge conyugeFicha = (PersonaConyuge) request.getAttribute("conyuge");
    // Normalización de personas, grupo Domicilio (16/10/2026): nunca null salvo en el alta
    // (todavía no se guardó nada) -- cada campo más abajo revisa null, mismo criterio que
    // conyugeFicha.
    PersonaDomicilio domicilioFicha = (PersonaDomicilio) request.getAttribute("domicilio");
    // Normalización de personas, grupo Laborales (16/10/2026): nunca null salvo en el alta
    // (todavía no se guardó nada) -- mismo criterio que domicilioFicha.
    PersonaLaboral laboralFicha = (PersonaLaboral) request.getAttribute("laboral");
    // Normalización de personas, grupo Domicilio (16/10/2026): el departamento ya no vive en
    // Persona -- lo resuelve el controlador (ControladorClientesServlet.mostrarFicha), por JOIN
    // desde la ciudad o, tras un reintento, con lo que la persona ya había elegido.
    Long idDepartamentoActualFicha = (Long) request.getAttribute("idDepartamentoActualFicha");
    // Normalización de personas, grupo Baja (16/10/2026): null salvo que el cliente esté
    // INACTIVA (ControladorClientesServlet.mostrarFicha solo la lee en ese caso).
    PersonaBaja bajaFicha = (PersonaBaja) request.getAttribute("baja");
    boolean esNueva = Boolean.TRUE.equals(request.getAttribute("esNueva"));
    // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): null en el alta
    // (todavía no se guardó nada, igual que domicilioFicha/laboralFicha).
    @SuppressWarnings("unchecked")
    List<FilaHistorialCredito> historialCreditoFicha = (List<FilaHistorialCredito>) request.getAttribute("historialCredito");
    // RF03 Parte 4 (pedido de Joel), punto c: el aviso en vivo de cédula vencida compara contra
    // esta fecha (Reloj.hoy() del servidor, nunca el reloj del navegador) -- así nunca se
    // contradice con la validación real, ni siquiera con una fecha simulada.
    String fechaHoyServidorFicha = (String) request.getAttribute("fechaHoyServidor");
    // B147 (14/11/2026): el "Volver" de abajo devuelve a la búsqueda que el usuario tenía en la
    // lista. El valor lo arma ControladorClientesServlet.mostrarLista y lo sirve mostrarFicha
    // (RETORNO_LISTA_EN_SESION): acá solo se dibuja, nunca se recalcula. Ya viene codificado con
    // URLEncoder, así que entra tal cual en el href. Vacío = /clientes, como antes.
    String urlVolverListaFicha = (String) request.getAttribute("urlVolverLista");
    urlVolverListaFicha = urlVolverListaFicha == null || urlVolverListaFicha.isEmpty()
            ? "" : "?" + urlVolverListaFicha;
    boolean puedeEditarFicha = Boolean.TRUE.equals(request.getAttribute("puedeEditar"));
    // 2c (pedido de Joel, 17/10/2026): con una evaluación en curso (solicitud pendiente/aprobada
    // sin formalizar, o préstamo pendiente de desembolso), los 7 datos de evaluación quedan de
    // solo lectura -- el resto de la ficha sigue editable igual que siempre.
    ServicioClientes.EvaluacionEnCurso evaluacionEnCursoFicha =
            (ServicioClientes.EvaluacionEnCurso) request.getAttribute("evaluacionEnCurso");
    boolean datosEvaluacionBloqueados = evaluacionEnCursoFicha != null;
    String mensajeEvaluacionBloqueadaFicha = (String) request.getAttribute("mensajeEvaluacionBloqueada");
    boolean puedeDarDeBajaFicha = Boolean.TRUE.equals(request.getAttribute("puedeDarDeBaja"));
    boolean puedeCargarDocs = Boolean.TRUE.equals(request.getAttribute("puedeCargarDocumentos"));
    // A pedido de Joel: la cajera (CARGAR_DOCUMENTOS_CONSULTA_BURO = NINGUNO) no debe ver las
    // pestañas de Documentos ni de Buró -- son informacion crediticia sensible, no alcanza con
    // ocultar los botones de carga (como ya hacia puedeCargarDocs). El controlador ni siquiera
    // trae esos datos de la base para este perfil (ver ControladorClientesServlet.mostrarFicha).
    boolean puedeVerDocumentosBuro = Boolean.TRUE.equals(request.getAttribute("puedeVerDocumentosBuro"));
    // Bloque 7/E10 (D-132, pedido de Joel): indicador de pasos e checklist del expediente en el
    // orden real del trámite (antes, desplegable alfabético sin indicar qué faltaba).
    boolean puedeGestionarSolicitudFicha = Boolean.TRUE.equals(request.getAttribute("puedeGestionarSolicitud"));
    // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 14): si ya hay una solicitud
    // abierta o un préstamo sin cerrar (RN-01/RN-02), la pestaña ④ y el botón "Siguiente" enlazan
    // a eso en vez de prometer "Nueva solicitud" -- ver ControladorClientesServlet.mostrarFicha.
    String textoCompromisoAbiertoFicha = (String) request.getAttribute("textoCompromisoAbierto");
    String enlaceCompromisoAbiertoFicha = (String) request.getAttribute("enlaceCompromisoAbierto");
    @SuppressWarnings("unchecked")
    List<ItemExpediente> itemsExpedienteFicha = (List<ItemExpediente>) request.getAttribute("itemsExpediente");
    Integer expedienteCumplidosFicha = (Integer) request.getAttribute("expedienteCumplidos");
    Integer expedienteTotalFicha = (Integer) request.getAttribute("expedienteTotal");
    boolean pasoExpedienteCompletoFicha = Boolean.TRUE.equals(request.getAttribute("pasoExpedienteCompleto"));
    boolean pasoBuroCompletoFicha = Boolean.TRUE.equals(request.getAttribute("pasoBuroCompleto"));
    // Bloque 7/E10 (D-133, pedido de Joel): informe de Informconf, Paso B de "③ Autorización y buró".
    ItemExpediente itemInformeBuroFicha = (ItemExpediente) request.getAttribute("itemInformeBuro");
    // RF03/RF04 (pedido de Joel), punto 4: la pestaña ③ se bloquea con un aviso mientras la fila 1
    // del Expediente (autorización firmada) no esté cargada -- calculado acá, antes del nav-pills
    // de más arriba, para no duplicar este loop también en la pestaña ③ más abajo.
    ItemExpediente itemAutorizacionBuroFicha = null;
    if (itemsExpedienteFicha != null) {
        for (ItemExpediente itemBusquedaBuro : itemsExpedienteFicha) {
            if ("AUTORIZACION_CONSULTA_BURO".equals(itemBusquedaBuro.getTipo().getCodigoTipo())) {
                itemAutorizacionBuroFicha = itemBusquedaBuro;
                break;
            }
        }
    }
    boolean autorizacionBuroCumplida = itemAutorizacionBuroFicha != null && itemAutorizacionBuroFicha.isCumplido();
    // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10f): para la confirmación
    // antes de guardar un cambio de situación laboral (ver el script de la pestaña Datos) -- si
    // hoy hay un comprobante de ingreso VIGENTE, cambiarla lo deja REEMPLAZADO sin avisar
    // (ServicioClientes.actualizar). Mismo criterio de búsqueda que itemAutorizacionBuroFicha.
    ItemExpediente itemComprobanteIngresoFicha = null;
    if (itemsExpedienteFicha != null) {
        for (ItemExpediente itemBusquedaComprobante : itemsExpedienteFicha) {
            if ("COMPROBANTE_INGRESO".equals(itemBusquedaComprobante.getTipo().getCodigoTipo())) {
                itemComprobanteIngresoFicha = itemBusquedaComprobante;
                break;
            }
        }
    }
    boolean comprobanteIngresoVigenteFicha =
            itemComprobanteIngresoFicha != null && itemComprobanteIngresoFicha.isCumplido();
    @SuppressWarnings("unchecked")
    List<ConsultaCrediticia> listaConsultas = (List<ConsultaCrediticia>) request.getAttribute("consultas");
    LocalDate fechaVencimientoConsentimiento = (LocalDate) request.getAttribute("fechaVencimientoConsentimiento");
    // RF03/RF04 (pedido de Joel): "quedan N días" / vencida en rojo, y el ✓ de "Generar" (fila 1
    // del Expediente / Paso A), vía auditoría, sin tabla nueva.
    Long diasRestantesVigenciaConsentimiento = (Long) request.getAttribute("diasRestantesVigenciaConsentimiento");
    UltimaOperacionAuditoria ultimaGeneracionAutorizacion =
            (UltimaOperacionAuditoria) request.getAttribute("ultimaGeneracionAutorizacion");
    boolean autorizacionGeneradaHoy = Boolean.TRUE.equals(request.getAttribute("autorizacionGeneradaHoy"));
    // RF03 Parte 2 (pedido de Joel): hace falta acá arriba (fila 1 del Expediente), no solo en
    // ③ -- "cargada y vigente" (sin botón) es distinto de "cargada pero vencida" (igual pide
    // "Generar nueva").
    boolean consentimientoVigenteFicha = Boolean.TRUE.equals(request.getAttribute("consentimientoVigenteFicha"));
    ConsentimientoCrediticio ultimoConsentimientoFicha =
            (ConsentimientoCrediticio) request.getAttribute("ultimoConsentimiento");
    String nombreUsuarioCargoConsentimiento = (String) request.getAttribute("nombreUsuarioCargoConsentimiento");
    Integer diasVigenciaConsentimientoFicha = (Integer) request.getAttribute("diasVigenciaConsentimiento");
    @SuppressWarnings("unchecked")
    List<Departamento> listaDepartamentos = (List<Departamento>) request.getAttribute("departamentos");
    @SuppressWarnings("unchecked")
    List<Ciudad> listaCiudades = (List<Ciudad>) request.getAttribute("ciudades");
    // RF38/D2 (13/10/2026, pedido de Joel): nombre real de Departamento/Ciudad, para mostrarlo
    // como texto en Domicilio cuando no se puede editar (Precalificación, donde vive el <select>
    // de Departamento, queda oculta para ese perfil) -- mismas listas de arriba, sin otra
    // consulta. Nunca se usa si puedeEditarFicha (ahí sigue el <select> en cascada de siempre).
    String nombreDepartamentoActualFicha = "—";
    String nombreCiudadActualFicha = "—";
    if (idDepartamentoActualFicha != null && listaDepartamentos != null) {
        for (Departamento departamentoActualBusqueda : listaDepartamentos) {
            if (departamentoActualBusqueda.getIdDepartamento().equals(idDepartamentoActualFicha)) {
                nombreDepartamentoActualFicha = departamentoActualBusqueda.getNombre();
                break;
            }
        }
    }
    if (domicilioFicha != null && domicilioFicha.getIdCiudad() != null && listaCiudades != null) {
        for (Ciudad ciudadActualBusqueda : listaCiudades) {
            if (ciudadActualBusqueda.getIdCiudad().equals(domicilioFicha.getIdCiudad())) {
                nombreCiudadActualFicha = ciudadActualBusqueda.getNombre();
                break;
            }
        }
    }
    @SuppressWarnings("unchecked")
    Map<String, List<String>> documentosSugeridosPorSituacion =
            (Map<String, List<String>>) request.getAttribute("documentosSugeridosPorSituacion");
    @SuppressWarnings("unchecked")
    List<String[]> situacionesLaboralesFormulario =
            (List<String[]>) request.getAttribute("situacionesLaboralesParaFormulario");
    Integer edadMinimaFicha = (Integer) request.getAttribute("edadMinimaFicha");
    Integer edadMaximaFicha = (Integer) request.getAttribute("edadMaximaFicha");
    String zonaHabilitadaTextoFicha = (String) request.getAttribute("zonaHabilitadaTextoFicha");
    // Se arma con request.getContextPath() (metodo Java), no con la expresion de JSP: esa
    // expresion nunca se evalua dentro de un scriptlet, solo en texto de plantilla o atributos.
    String accionGuardar = esNueva ? request.getContextPath() + "/clientes/nuevo"
            : request.getContextPath() + "/clientes/" + personaFicha.getIdPersona() + "/guardar";
    // B88/B28/B34 (pedido de Joel, 28/10/2026; orden "opción B" del 29/10): el botón
    // "Siguiente: …" de las pestañas ①②③ es SIEMPRE el primer paso pendiente del cliente -- el
    // mismo que avisa cada acción (ControladorClientesServlet.mostrarFicha pide el paso al mismo
    // ServicioPasoGuiado). Antes cada pestaña ofrecía un destino fijo: ② ofrecía ③ aunque ya
    // hubiera una solicitud en curso (B34), y ③ ofrecía ④ con el expediente incompleto (B28).
    // Acá solo se resuelve CÓMO se muestra (pestaña o enlace) y el permiso; el destino y el texto
    // ya vienen decididos.
    PasoGuiado pasoGuiadoFicha = (PasoGuiado) request.getAttribute("pasoGuiado");
    String botonPasoGuiadoPestana = null;
    String botonPasoGuiadoHref = null;
    boolean botonPasoGuiadoVisible = false;
    if (!esNueva && pasoGuiadoFicha != null && pasoGuiadoFicha.getEtiquetaBoton() != null) {
        switch (pasoGuiadoFicha.getDestino()) {
            case EXPEDIENTE:
                botonPasoGuiadoPestana = "enlacePestanaExpediente";
                botonPasoGuiadoVisible = puedeVerDocumentosBuro;
                break;
            case BURO:
                botonPasoGuiadoPestana = "enlacePestanaBuro";
                botonPasoGuiadoVisible = puedeVerDocumentosBuro;
                break;
            case SOLICITUD_NUEVA:
                botonPasoGuiadoHref = pasoGuiadoFicha.getRuta() == null ? null
                        : request.getContextPath() + pasoGuiadoFicha.getRuta();
                botonPasoGuiadoVisible = puedeGestionarSolicitudFicha && botonPasoGuiadoHref != null;
                break;
            case SOLICITUD_FICHA:
                // Tanda de usabilidad (21/10/2026, punto 7 ítem 14): el enlace del compromiso
                // abierto ya viene resuelto por el permiso real de la sesión (null si el usuario no
                // tiene acceso a ninguna pantalla de destino) -- se respeta ese criterio.
                botonPasoGuiadoHref = enlaceCompromisoAbiertoFicha;
                botonPasoGuiadoVisible = puedeGestionarSolicitudFicha && botonPasoGuiadoHref != null;
                break;
            default:
                botonPasoGuiadoVisible = false;
                break;
        }
    }
%>
<h1><%= esNueva ? "Nuevo cliente" : "Ficha de cliente" %></h1>

<div class="card">
    <%-- Bloque 7/E10 (D-132, pedido de Joel): indicador de pasos -- ① Datos ② Expediente
         ③ Autorización y buró ④ Solicitud -- con ✓ en los completos; los tres primeros son a la
         vez la navegación entre pestañas de siempre (mismo mecanismo de nav-pills), el cuarto
         lleva a crear la solicitud del cliente (solo el oficial, GESTIONAR_SOLICITUD). --%>
    <div class="card-header p-2">
        <ul class="nav nav-pills" role="tablist">
            <li class="nav-item"><a class="nav-link active" data-toggle="pill" href="#tabFicha">
                <% if (!esNueva) { %><span class="text-success">① ✓</span><% } else { %>①<% } %> Datos</a></li>
            <% if (!esNueva && puedeVerDocumentosBuro) { %>
            <li class="nav-item"><a class="nav-link" id="enlacePestanaExpediente" data-toggle="pill" href="#tabDocumentos">
                <% if (pasoExpedienteCompletoFicha) { %><span class="text-success">② ✓</span><% } else { %>②<% } %> Expediente</a></li>
            <%-- RF03/RF04 (pedido de Joel), punto 4: sin la autorización firmada cargada (fila 1
                 del Expediente), la pestaña avisa en vez de cambiar -- id para el JS de más abajo. --%>
            <li class="nav-item"><a class="nav-link" id="enlacePestanaBuro" data-toggle="pill" href="#tabBuro">
                <% if (pasoBuroCompletoFicha) { %><span class="text-success">③ ✓</span><% } else { %>③<% } %> Consulta al buró</a></li>
            <% if (puedeGestionarSolicitudFicha) { %>
            <li class="nav-item">
                <%-- Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 14): con una
                     solicitud abierta o un préstamo sin cerrar (RN-01/RN-02), la pestaña lleva a
                     ESO en vez de prometer "Nueva solicitud" (que el servidor va a rechazar). --%>
                <% if (textoCompromisoAbiertoFicha != null) { %>
                <% if (enlaceCompromisoAbiertoFicha != null) { %>
                <a class="nav-link text-muted" href="<%= enlaceCompromisoAbiertoFicha %>">
                    ④ <%= textoCompromisoAbiertoFicha %> →</a>
                <% } else { %>
                <span class="nav-link text-muted disabled">④ <%= textoCompromisoAbiertoFicha %></span>
                <% } %>
                <% } else { %>
                <a class="nav-link <%= (pasoExpedienteCompletoFicha && pasoBuroCompletoFicha) ? "text-primary font-weight-bold" : "text-muted" %>"
                   href="${pageContext.request.contextPath}/solicitudes/nuevo?cedulaCliente=<%= personaFicha.getCedulaIdentidad() %>"
                   title="<%= (pasoExpedienteCompletoFicha && pasoBuroCompletoFicha) ? "Crear la solicitud de este cliente" : "Complete el expediente y la autorización primero" %>">
                    ④ Solicitud<%= (pasoExpedienteCompletoFicha && pasoBuroCompletoFicha) ? " →" : "" %></a>
                <% } %>
            </li>
            <% } %>
            <% } %>
            <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): sin
                 número -- no es un paso del trámite, es historial; visible para cualquiera que
                 pueda ver la ficha, a diferencia de Expediente/Buró. --%>
            <% if (!esNueva) { %>
            <li class="nav-item"><a class="nav-link" data-toggle="pill" href="#tabHistorialCredito">Solicitudes y préstamos</a></li>
            <% } %>
        </ul>
    </div>
    <div class="card-body">
        <div class="tab-content">

            <div class="tab-pane active" id="tabFicha">
                <% if (!esNueva && personaFicha != null && personaFicha.getEstadoPersona() != null
                        && personaFicha.getEstadoPersona().name().equals("INACTIVA") && bajaFicha != null) { %>
                <div class="alert alert-secondary">
                    Cliente <strong>inactivo</strong> desde <%= FormateadorFechas.comoDdMmAaaaHhMm(bajaFicha.getFechaBaja()) %>
                    (motivo: <%= bajaFicha.getMotivoBaja() %><%= bajaFicha.getDetalleBaja() == null || bajaFicha.getDetalleBaja().isEmpty() ? "" : " - " + bajaFicha.getDetalleBaja() %>).
                    No puede iniciar solicitudes nuevas mientras esté inactivo.
                    <% if (puedeDarDeBajaFicha) { %>
                    <form method="post" action="${pageContext.request.contextPath}/clientes/<%= personaFicha.getIdPersona() %>/reactivar" class="d-inline">
                        <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                        <button type="submit" class="btn btn-sm btn-outline-success ml-2">Reactivar</button>
                    </form>
                    <% } %>
                </div>
                <% } %>
                <% if (esNueva) { %>
                <p class="text-muted"><small>Expediente y Autorización se habilitan después de registrar al cliente.</small></p>
                <% } %>
                <%-- RF38/D2 (13/10/2026, pedido de Joel): sin sentido en consulta (solo la cajera
                     hoy, REGISTRAR_EDITAR_CLIENTE = CONSULTA) -- mismo patrón que ya oculta la
                     pestaña de documentos/buró, puedeEditarFicha. --%>
                <% if (puedeEditarFicha) { %>
                <p class="text-muted"><small>Los campos marcados con <strong>*</strong> son obligatorios.</small></p>
                <% } %>
                <%-- RF02 (pedido de Joel), punto h: novalidate + un solo SweetAlert con la lista de
                     lo que falta (ver el script al pie de esta pestaña), en vez de los globos del
                     navegador de a uno. El servidor sigue validando igual (red de seguridad real;
                     esto es solo para que la persona vea TODO lo que falta de una vez). --%>
                <form method="post" action="<%= accionGuardar %>" id="formularioFichaCliente" novalidate>
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>

                    <%-- Mejora de pantalla (Precalificación, pedido de Joel): primera caja del
                         formulario -- el oficial sabe si la persona califica ANTES de cargar el
                         resto de los datos. En el alta, "restoFormularioFicha" (más abajo) arranca
                         deshabilitado y solo se habilita si estos 4 datos están completos Y la
                         política vigente los admite (GET /clientes/precalificar, ver el script al
                         pie de esta pestaña); en la edición nunca se deshabilita nada. --%>
                    <%-- RF38/D2 (13/10/2026, pedido de Joel): el bloque completo de Precalificación
                         (indicador "Califica" incluido) no se dibuja en servidor para quien solo
                         tiene consulta -- mismo puedeEditarFicha de siempre. --%>
                    <% if (puedeEditarFicha) { %>
                    <div class="card border-primary mb-3">
                        <div class="card-header bg-primary text-white"><strong>Precalificación</strong></div>
                        <div class="card-body">
                            <div class="form-row">
                                <div class="form-group col-md-3">
                                    <label>Fecha de nacimiento *</label>
                                    <input type="date" name="fechaNacimiento" id="campoFechaNacimientoFicha" class="form-control"
                                           value="<%= personaFicha == null || personaFicha.getFechaNacimiento() == null ? "" : personaFicha.getFechaNacimiento() %>"
                                           <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "required" : "readonly" %>>
                                    <%-- RF02 (pedido de Joel), punto c: ayuda leída de credito.edad_minima/maxima.
                                         Mejora de pantalla (punto 2): edad calculada en vivo al lado, verde si está
                                         dentro del rango y roja si no -- JS en el bloque de scripts de más abajo. --%>
                                    <% if (edadMinimaFicha != null && edadMaximaFicha != null) { %>
                                    <small class="form-text text-muted">Edad permitida: de <%= edadMinimaFicha %> a <%= edadMaximaFicha %> años.
                                        <span id="edadCalculadaFicha"></span></small>
                                    <% } %>
                                </div>
                                <div class="form-group col-md-3">
                                    <label>Departamento *</label>
                                    <select name="idDepartamento" id="selectDepartamento" class="form-control" <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "required" : "disabled" %>>
                                        <option value="">Seleccione...</option>
                                        <% if (listaDepartamentos != null) { for (Departamento departamento : listaDepartamentos) { %>
                                        <option value="<%= departamento.getIdDepartamento() %>" <%= departamento.getIdDepartamento().equals(idDepartamentoActualFicha) ? "selected" : "" %>><%= departamento.getNombre() %></option>
                                        <% } } %>
                                    </select>
                                    <%-- 2c (pedido de Joel, 17/10/2026): mismo motivo que el <select> de Ciudad --
                                         si queda editable, el JS de cascada repuebla Ciudad y la ciudad bloqueada
                                         (conservada por su propio oculto) deja de pertenecer al departamento que
                                         se terminaría enviando. Deshabilitado + oculto con el valor real. --%>
                                    <% if (datosEvaluacionBloqueados) { %>
                                    <input type="hidden" name="idDepartamento" value="<%= idDepartamentoActualFicha == null ? "" : idDepartamentoActualFicha %>">
                                    <% } %>
                                    <%-- RF02 (pedido de Joel), punto c: ayuda leída de credito.departamentos_habilitados. --%>
                                    <% if (zonaHabilitadaTextoFicha != null && !zonaHabilitadaTextoFicha.isEmpty()) { %>
                                    <small class="form-text text-muted">Solo <%= zonaHabilitadaTextoFicha %>.</small>
                                    <% } %>
                                </div>
                                <div class="form-group col-md-3">
                                    <label>Situación laboral *</label>
                                    <select name="situacionLaboral" id="selectSituacionLaboral" class="form-control" <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "required" : "disabled" %>>
                                        <option value="">Seleccione...</option>
                                        <%-- RF02 (pedido de Joel): solo las situaciones admitidas por la política
                                             vigente (credito.situaciones_laborales_admitidas), más la actual de
                                             esta persona si ya no está admitida (no se pierde el dato real). --%>
                                        <% String situacionLaboralActual = laboralFicha == null || laboralFicha.getSituacionLaboral() == null ? "" : laboralFicha.getSituacionLaboral();
                                           if (situacionesLaboralesFormulario != null) {
                                               for (String[] situacion : situacionesLaboralesFormulario) { %>
                                        <option value="<%= situacion[0] %>" <%= situacion[0].equals(situacionLaboralActual) ? "selected" : "" %>><%= situacion[1] %></option>
                                        <% } } %>
                                    </select>
                                    <%-- 2c (pedido de Joel, 17/10/2026): el <select> disabled no se envía en el
                                         POST -- este oculto mantiene el valor real para que el servidor no vea
                                         un "cambio" donde no lo hubo. --%>
                                    <% if (datosEvaluacionBloqueados) { %>
                                    <input type="hidden" name="situacionLaboral" value="<%= situacionLaboralActual %>">
                                    <% } %>
                                </div>
                                <div class="form-group col-md-3">
                                    <label>¿El cliente puede presentar un comprobante de su ingreso? *</label>
                                    <div>
                                        <div class="form-check form-check-inline">
                                            <%-- Mejora de pantalla (pedido de Joel): reemplaza la casilla
                                                 "Presenta comprobante de ingresos" -- mismo name y mismo valor
                                                 "on" para Sí, así ControladorClientesServlet.armarPersonaDesdeRequest
                                                 no cambia ("on".equals(...)). Sin "checked" por defecto en el
                                                 alta (personaFicha null): la pregunta no viene preseleccionada. --%>
                                            <input type="radio" name="presentaComprobanteIngreso" id="radioComprobanteSi" value="on" class="form-check-input"
                                                   <%= laboralFicha != null && laboralFicha.isPresentaComprobanteIngreso() ? "checked" : "" %>
                                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "disabled" %>>
                                            <label class="form-check-label" for="radioComprobanteSi">Sí</label>
                                        </div>
                                        <div class="form-check form-check-inline">
                                            <input type="radio" name="presentaComprobanteIngreso" id="radioComprobanteNo" value="no" class="form-check-input"
                                                   <%= laboralFicha != null && !laboralFicha.isPresentaComprobanteIngreso() ? "checked" : "" %>
                                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "disabled" %>>
                                            <label class="form-check-label" for="radioComprobanteNo">No</label>
                                        </div>
                                        <%-- 2c: mismo motivo que el <select> de arriba -- los radios disabled
                                             no se envían. --%>
                                        <% if (datosEvaluacionBloqueados && laboralFicha != null && laboralFicha.isPresentaComprobanteIngreso()) { %>
                                        <input type="hidden" name="presentaComprobanteIngreso" value="on">
                                        <% } %>
                                    </div>
                                </div>
                            </div>
                            <% if (datosEvaluacionBloqueados) { %>
                            <div class="alert alert-warning mt-2"><%= mensajeEvaluacionBloqueadaFicha %></div>
                            <% } %>
                            <%-- Corrección (pedido de Joel): se saca la línea gris fija de más abajo --
                                 todo el aviso de comprobante vive ahora en el indicador en vivo. --%>
                            <%-- Indicador en vivo: gris (incompleto), verde (califica) o rojo (no califica
                                 y por qué) -- JS al pie de esta pestaña, GET /clientes/precalificar. --%>
                            <div id="indicadorPrecalificacion" class="font-weight-bold text-muted mb-2">Complete la precalificación para continuar.</div>
                            <% if (puedeEditarFicha) { %>
                            <button type="button" id="botonImprimirRequisitos" class="btn btn-outline-secondary btn-sm" disabled>
                                <i class="fas fa-print"></i> Imprimir requisitos
                            </button>
                            <% if (esNueva) { %>
                            <%-- Mejora de pantalla (pedido de Joel), punto c: "Limpiar", solo en el alta --
                                 vacía precalificación y el resto del formulario, y lo vuelve a dejar
                                 bloqueado (mismo estado que al entrar a "Nuevo cliente"). --%>
                            <button type="button" id="botonLimpiarFormulario" class="btn btn-outline-secondary btn-sm">
                                <i class="fas fa-eraser"></i> Limpiar
                            </button>
                            <% } %>
                            <% } %>
                        </div>
                    </div>
                    <% } %>

                    <%-- Mejora de pantalla (pedido de Joel): el resto del formulario -- deshabilitado
                         de entrada SOLO en el alta (se habilita cuando Precalificación califica);
                         en la edición de un cliente existente nunca se toca (se puede seguir
                         editando y guardando aunque no califique, con el aviso amarillo de siempre). --%>
                    <fieldset id="restoFormularioFicha" <%= esNueva ? "disabled" : "" %>>
                    <h5 class="mt-2">Datos personales</h5>
                    <div class="form-row">
                        <div class="form-group col-md-3">
                            <label>Cédula de identidad<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="cedulaIdentidad" id="campoCedulaIdentidad" class="form-control campo-cedula"
                                   value="<%= personaFicha == null ? "" : FormateadorTexto.comoCedulaConPuntos(personaFicha.getCedulaIdentidad()) %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-3">
                            <label>Vencimiento de la cédula<%= puedeEditarFicha ? " *" : "" %></label>
                            <%-- RF03 Parte 4 (pedido de Joel), punto c: aviso en vivo si la fecha
                                 elegida ya pasó -- mismo criterio que la edad en vivo de
                                 Precalificación, pero sin ir al servidor (no depende de ningún
                                 parámetro): compara contra data-hoy-servidor, nunca el reloj del
                                 navegador (ver ControladorClientesServlet.mostrarFicha, Reloj.hoy()). --%>
                            <input type="date" name="fechaVencimientoCedula" id="campoFechaVencimientoCedulaFicha"
                                   class="form-control" data-hoy-servidor="<%= fechaHoyServidorFicha %>"
                                   value="<%= personaFicha == null || personaFicha.getFechaVencimientoCedula() == null ? "" : personaFicha.getFechaVencimientoCedula() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                            <small id="avisoCedulaVencidaFicha" class="text-danger d-none">Cédula vencida</small>
                        </div>
                        <div class="form-group col-md-3">
                            <label>RUC<%= puedeEditarFicha ? " (opcional)" : "" %></label>
                            <input type="text" name="ruc" class="form-control"
                                   value="<%= personaFicha == null || personaFicha.getRuc() == null ? "" : personaFicha.getRuc() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Nombres<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="nombres" class="form-control"
                                   value="<%= personaFicha == null ? "" : personaFicha.getNombres() %>" <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Apellidos<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="apellidos" class="form-control"
                                   value="<%= personaFicha == null ? "" : personaFicha.getApellidos() %>" <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Estado civil<%= puedeEditarFicha ? " *" : "" %></label>
                            <select name="estadoCivil" id="selectEstadoCivil" class="form-control" <%= puedeEditarFicha ? "required" : "disabled" %>>
                                <% String[] estadosCiviles = {"Soltero/a", "Casado/a", "Unión de hecho", "Divorciado/a", "Viudo/a"};
                                   String estadoCivilActual = personaFicha == null ? "" : personaFicha.getEstadoCivil();
                                   for (String estado : estadosCiviles) { %>
                                <option value="<%= estado %>" <%= estado.equals(estadoCivilActual) ? "selected" : "" %>><%= estado %></option>
                                <% } %>
                            </select>
                        </div>
                    </div>

                    <%-- D-126/RF02 (punto a, corrección de Joel): la sección entera (título incluido)
                         solo visible con estado civil Casado/a o Unión de hecho -- antes el JS solo
                         escondía la fila de campos y el título "Cónyuge" quedaba solo, vacío, con
                         Soltero/a (selectEstadoCivil.js, mas abajo, ahora esconde seccionConyuge).
                         RF38/D2 (13/10/2026, pedido de Joel): además, nada de esto se dibuja en
                         servidor para quien solo tiene consulta. --%>
                    <% if (puedeEditarFicha) { %>
                    <div id="seccionConyuge">
                        <h5 class="mt-3">Cónyuge</h5>
                        <div class="form-row" id="filaConyuge">
                            <div class="form-group col-md-4">
                                <label>Cónyuge: nombre *</label>
                                <input type="text" name="conyugeNombre" class="form-control"
                                       value="<%= conyugeFicha == null || conyugeFicha.getNombre() == null ? "" : conyugeFicha.getNombre() %>"
                                       <%= puedeEditarFicha ? "" : "readonly" %>>
                            </div>
                            <div class="form-group col-md-4">
                                <label>Cónyuge: cédula *</label>
                                <input type="text" name="conyugeCedula" class="form-control campo-cedula"
                                       value="<%= conyugeFicha == null || conyugeFicha.getCedula() == null ? "" : FormateadorTexto.comoCedulaConPuntos(conyugeFicha.getCedula()) %>"
                                       <%= puedeEditarFicha ? "" : "readonly" %>>
                            </div>
                            <div class="form-group col-md-4">
                                <label>Cónyuge: teléfono *</label>
                                <input type="text" name="conyugeTelefono" class="form-control"
                                       value="<%= conyugeFicha == null || conyugeFicha.getTelefono() == null ? "" : conyugeFicha.getTelefono() %>"
                                       <%= puedeEditarFicha ? "" : "readonly" %>>
                            </div>
                        </div>
                    </div>
                    <% } %>

                    <%-- D-126/D-129: domicilio por partes -- ServicioClientes arma el texto final para
                         contrato/pagaré/reportes (armarDomicilioDesdePartes). --%>
                    <%-- Mejora de pantalla (pedido de Joel): el Departamento se movió a
                         Precalificación (más arriba); acá queda Ciudad en cascada, Barrio y
                         Dirección -- selectDepartamento sigue existiendo (en la caja de arriba)
                         cuando puedeEditarFicha, el script de cascada de más abajo lo sigue
                         encontrando por su mismo id. RF38/D2 (13/10/2026): en consulta,
                         Precalificación (y su selectDepartamento) queda oculta -- ver el bloque
                         de Departamento/Ciudad como texto, más abajo. --%>
                    <h5 class="mt-3">Domicilio</h5>
                    <div class="form-row">
                        <% if (puedeEditarFicha) { %>
                        <div class="form-group col-md-4">
                            <label>Ciudad *</label>
                            <select name="idCiudad" id="selectCiudad" class="form-control" <%= datosEvaluacionBloqueados ? "disabled" : "required" %>>
                                <option value="">Elija primero el departamento</option>
                            </select>
                            <%-- B153 (14/11/2026): el departamento elegido, acá al lado de la ciudad
                                 que depende de él. Antes el <option> vacío decía "Elija el
                                 departamento" señalando un campo que está ~190 líneas más arriba, en
                                 el recuadro de Precalificación, y fuera de la pantalla.
                                 Es un ESPEJO, no un segundo dato: el <select> de Precalificación
                                 sigue siendo el único dueño (no lleva name, no viaja nada nuevo al
                                 servidor) y lo escribe la misma cascada que llena las ciudades
                                 (actualizarCiudades, más abajo), así que no puede quedar
                                 desactualizado. El campo NO se movió a propósito: subir el <select>
                                 de Ciudad al recuadro de Precalificación lo sacaría del
                                 <fieldset id="restoFormularioFicha"> y quedaría editable antes de
                                 precalificar (decisión del alta: no se escribe nada hasta calificar).
                                 Mismo patrón de ayuda que "Edad permitida:" de Precalificación. --%>
                            <small class="form-text text-muted">Departamento:
                                <span id="espejoDepartamentoDomicilio"><%= nombreDepartamentoActualFicha %></span>
                                — se elige arriba, en Precalificación.</small>
                            <%-- 2c (pedido de Joel, 17/10/2026): la ciudad define la zona evaluada --
                                 el <select> disabled no se envía, este oculto conserva el valor real. --%>
                            <% if (datosEvaluacionBloqueados) { %>
                            <input type="hidden" name="idCiudad" value="<%= domicilioFicha == null || domicilioFicha.getIdCiudad() == null ? "" : domicilioFicha.getIdCiudad() %>">
                            <% } %>
                        </div>
                        <% } else { %>
                        <%-- RF38/D2 (13/10/2026, pedido de Joel): en consulta no hay <select> de
                             Departamento (vive en Precalificación, oculta para este perfil) --
                             departamento y ciudad reales como texto, sin dibujar nada más de
                             Precalificación. --%>
                        <div class="form-group col-md-4">
                            <label>Departamento</label>
                            <input type="text" class="form-control" value="<%= nombreDepartamentoActualFicha %>" readonly>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Ciudad</label>
                            <input type="text" class="form-control" value="<%= nombreCiudadActualFicha %>" readonly>
                        </div>
                        <% } %>
                        <div class="form-group col-md-4">
                            <label>Barrio<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="barrio" class="form-control"
                                   value="<%= domicilioFicha == null || domicilioFicha.getBarrio() == null ? "" : domicilioFicha.getBarrio() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <%-- D-129 (Obs. 16): antes la calle terminaba escrita en Barrio; ahora tiene su propio campo. --%>
                            <label>Dirección (calle y número)<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="direccionDomicilio" class="form-control" placeholder="Ej.: Avda. España 1234"
                                   value="<%= domicilioFicha == null || domicilioFicha.getDireccionDomicilio() == null ? "" : domicilioFicha.getDireccionDomicilio() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Referencia del domicilio<%= puedeEditarFicha ? " (opcional)" : "" %></label>
                            <input type="text" name="referenciaDomicilio" class="form-control" placeholder="Ej.: casa celeste, portón negro"
                                   value="<%= domicilioFicha == null || domicilioFicha.getReferenciaDomicilio() == null ? "" : domicilioFicha.getReferenciaDomicilio() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Tipo de vivienda<%= puedeEditarFicha ? " *" : "" %></label>
                            <select name="tipoVivienda" class="form-control" <%= puedeEditarFicha ? "required" : "disabled" %>>
                                <% String tipoViviendaActual = domicilioFicha == null || domicilioFicha.getTipoVivienda() == null ? "" : domicilioFicha.getTipoVivienda();
                                   String[][] tiposVivienda = {{"PROPIA", "Propia"}, {"ALQUILADA", "Alquilada"}, {"FAMILIAR", "Familiar"}};
                                   for (String[] tipo : tiposVivienda) { %>
                                <option value="<%= tipo[0] %>" <%= tipo[0].equals(tipoViviendaActual) ? "selected" : "" %>><%= tipo[1] %></option>
                                <% } %>
                            </select>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Antigüedad en el domicilio (años)<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="number" name="antiguedadDomicilioAnios" class="form-control" min="0" step="1"
                                   value="<%= domicilioFicha == null || domicilioFicha.getAntiguedadDomicilioAnios() == null ? "" : domicilioFicha.getAntiguedadDomicilioAnios() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                    </div>

                    <h5 class="mt-3">Contacto</h5>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Teléfono/WhatsApp<%= puedeEditarFicha ? " *" : "" %></label>
                            <input type="text" name="telefonoWhatsapp" class="form-control"
                                   value="<%= personaFicha == null ? "" : personaFicha.getTelefonoWhatsapp() %>" <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Teléfono alternativo<%= puedeEditarFicha ? " (opcional)" : "" %></label>
                            <input type="text" name="telefonoAlternativo" class="form-control"
                                   value="<%= personaFicha == null || personaFicha.getTelefonoAlternativo() == null ? "" : personaFicha.getTelefonoAlternativo() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Correo electrónico<%= puedeEditarFicha ? " (opcional)" : "" %></label>
                            <input type="email" name="correoElectronico" class="form-control"
                                   value="<%= personaFicha == null || personaFicha.getCorreoElectronico() == null ? "" : personaFicha.getCorreoElectronico() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                    </div>
                    <%-- Mejora de pantalla (pedido de Joel): la situación laboral se movió a
                         Precalificación (más arriba) -- acá queda el empleador (obligatorio solo
                         con Asalariado del sector privado o Funcionario público) e ingresos.
                         selectSituacionLaboral sigue existiendo (en la caja de arriba), el script
                         de empleador/documentos sugeridos de más abajo lo sigue encontrando por
                         su mismo id.
                         RF38/D2 (13/10/2026, pedido de Joel): toda la sección (empleador, ingreso
                         neto, otros ingresos, gastos fijos, cuota de otras deudas, documentos
                         sugeridos y la nota de capacidad de pago) no se dibuja en servidor para
                         quien solo tiene consulta. --%>
                    <% if (puedeEditarFicha) { %>
                    <h5 class="mt-3">Trabajo e ingresos</h5>
                    <div class="form-row" id="filaEmpleador">
                        <div class="form-group col-md-3">
                            <label>Empleador: nombre *</label>
                            <input type="text" name="empleadorNombre" class="form-control"
                                   value="<%= laboralFicha == null || laboralFicha.getEmpleadorNombre() == null ? "" : laboralFicha.getEmpleadorNombre() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-2">
                            <label>RUC (opcional)</label>
                            <input type="text" name="empleadorRuc" class="form-control" placeholder="80012345-6"
                                   value="<%= laboralFicha == null || laboralFicha.getEmpleadorRuc() == null ? "" : laboralFicha.getEmpleadorRuc() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                        <%-- D-129 (Obs. 17, normalización de personas, grupo Laborales, 16/10/2026):
                             el fallback a lugarTrabajo se quitó -- esa columna legado ya no existe en
                             personas (sin dato real que preservar), queda solo empleadorDireccion. --%>
                        <div class="form-group col-md-3">
                            <label>Dirección laboral *</label>
                            <input type="text" name="empleadorDireccion" class="form-control"
                                   value="<%= laboralFicha == null || laboralFicha.getEmpleadorDireccion() == null ? "" : laboralFicha.getEmpleadorDireccion() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-2">
                            <label>Teléfono laboral *</label>
                            <input type="text" name="empleadorTelefono" class="form-control"
                                   value="<%= laboralFicha == null || laboralFicha.getEmpleadorTelefono() == null ? "" : laboralFicha.getEmpleadorTelefono() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-2">
                            <label>Antigüedad laboral (años) *</label>
                            <input type="number" name="antiguedadLaboralAnios" class="form-control" min="0" step="1"
                                   value="<%= laboralFicha == null || laboralFicha.getAntiguedadLaboralAnios() == null ? "" : laboralFicha.getAntiguedadLaboralAnios() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-3">
                            <label>Cargo *</label>
                            <input type="text" name="cargo" class="form-control"
                                   value="<%= laboralFicha == null || laboralFicha.getCargo() == null ? "" : laboralFicha.getCargo() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "" : "readonly" %>>
                        </div>
                    </div>
                    <% if (datosEvaluacionBloqueados) { %>
                    <div class="alert alert-warning"><%= mensajeEvaluacionBloqueadaFicha %></div>
                    <% } %>
                    <div class="form-row" id="filaDocumentosSugeridos">
                        <div class="col-md-12">
                            <p class="text-muted mb-1"><small><strong>Documentos sugeridos para esta situación laboral:</strong> <span id="textoDocumentosSugeridos"></span></small></p>
                        </div>
                    </div>

                    <%-- Mejora de pantalla (pedido de Joel): la casilla "Presenta comprobante de
                         ingresos" se movió a Precalificación (más arriba, como radios Sí/No,
                         mismo name="presentaComprobanteIngreso") -- acá queda solo el monto. --%>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <%-- Corrección visual 2 (Joel): la etiqueta refleja si hay comprobante o no. --%>
                            <label><%= (laboralFicha != null && !laboralFicha.isPresentaComprobanteIngreso())
                                    ? "Ingreso declarado (sin comprobante) (Gs.)" : "Ingreso neto comprobado (Gs.)" %> *</label>
                            <input type="text" name="ingresoNetoComprobado" class="form-control campo-guaranies"
                                   value="<%= laboralFicha == null || laboralFicha.getIngresoNetoComprobado() == null ? "" : laboralFicha.getIngresoNetoComprobado().toPlainString() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "required" : "readonly" %>>
                        </div>
                    </div>
                    <%-- D-127: otros ingresos y gastos fijos son solo informativos (no entran en la
                         fórmula de capacidad de pago); la cuota de otras deudas sí entra. --%>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Otros ingresos mensuales (opcional, informativo, Gs.)</label>
                            <input type="text" name="otrosIngresosMensuales" class="form-control campo-guaranies"
                                   value="<%= laboralFicha == null || laboralFicha.getOtrosIngresosMensuales() == null ? "" : laboralFicha.getOtrosIngresosMensuales().toPlainString() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Gastos fijos mensuales (opcional, informativo, Gs.)</label>
                            <input type="text" name="gastosFijosMensuales" class="form-control campo-guaranies"
                                   value="<%= laboralFicha == null || laboralFicha.getGastosFijosMensuales() == null ? "" : laboralFicha.getGastosFijosMensuales().toPlainString() %>"
                                   <%= puedeEditarFicha ? "" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Cuota mensual de otras deudas (Gs., 0 si no tiene) *</label>
                            <input type="text" name="cuotaOtrasDeudasMensual" class="form-control campo-guaranies"
                                   value="<%= laboralFicha == null || laboralFicha.getCuotaOtrasDeudasMensual() == null ? "" : laboralFicha.getCuotaOtrasDeudasMensual().toPlainString() %>"
                                   <%= puedeEditarFicha && !datosEvaluacionBloqueados ? "required" : "readonly" %>>
                        </div>
                    </div>
                    <p class="text-muted"><small>La cuota de otras deudas se resta de la capacidad de pago al evaluar una solicitud; los otros ingresos y gastos fijos son solo información.</small></p>
                    <% } %>

                    <%-- RF38/D2 (13/10/2026, pedido de Joel): "Referencias" (las dos) no se dibuja
                         en servidor para quien solo tiene consulta. --%>
                    <% if (puedeEditarFicha) { %>
                    <h5 class="mt-3">Referencias</h5>
                    <%-- Normalización de personas, grupo Referencias (15/10/2026): nombres de campo
                         sin cambios (referenciaNombre/referenciaTelefono/referencia2Nombre/
                         referencia2Telefono) -- ControladorClientesServlet.armarReferenciasDesdeRequest
                         los arma en una lista, ya no son columnas de Persona. Dos referencias
                         obligatorias en alta Y edición por igual (ServicioClientes.validarReferencias).
                         Se sacó "Otras referencias" (texto libre, columna personas.referencias
                         eliminada -- normalización de personas, decisión de Joel). --%>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Primera referencia: nombre *</label>
                            <input type="text" name="referenciaNombre" class="form-control"
                                   value="<%= referencia1Ficha == null || referencia1Ficha.getNombre() == null ? "" : referencia1Ficha.getNombre() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Primera referencia: teléfono *</label>
                            <input type="text" name="referenciaTelefono" class="form-control"
                                   value="<%= referencia1Ficha == null || referencia1Ficha.getTelefono() == null ? "" : referencia1Ficha.getTelefono() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-4">
                            <label>Segunda referencia: nombre *</label>
                            <input type="text" name="referencia2Nombre" class="form-control"
                                   value="<%= referencia2Ficha == null || referencia2Ficha.getNombre() == null ? "" : referencia2Ficha.getNombre() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                        <div class="form-group col-md-4">
                            <label>Segunda referencia: teléfono *</label>
                            <input type="text" name="referencia2Telefono" class="form-control"
                                   value="<%= referencia2Ficha == null || referencia2Ficha.getTelefono() == null ? "" : referencia2Ficha.getTelefono() %>"
                                   <%= puedeEditarFicha ? "required" : "readonly" %>>
                        </div>
                    </div>
                    <% } %>
                    <%-- Corrección (pedido de Joel), punto f: "Guardar cambios"/"Registrar cliente" y
                         "Volver" uno al lado del otro -- el <a> queda DENTRO del fieldset para que los
                         dos queden en la misma fila flex, en vez de "Volver" cayendo en su propia línea
                         por ser hermano de un fieldset (bloque que ocupa todo el ancho).
                         B109 (10/11/2026): acá decía que "un enlace nunca se deshabilita por fieldset
                         disabled". Era FALSO -- Bootstrap y AdminLTE traen los dos
                         `fieldset:disabled a.btn{pointer-events:none}`, así que en el alta sin calificar
                         este "Volver" se veía pero no respondía al clic, y era la única salida de la
                         pantalla. Lo que lo mantiene vivo es la excepción de la sección 2b de
                         recursos/css/sgcpc-tema.css, no el navegador: si esa regla se toca, el enlace
                         se muere otra vez. --%>
                    <%-- B152 (14/11/2026): la misma barra de siempre, ahora pegada al borde de
                         abajo mientras se recorre el formulario (sgcpc-tema.css §10). El marcado
                         NO se movió: el <a> de "Volver" sigue DENTRO del fieldset, que es lo que
                         B109 dejó funcionando. --%>
                    <div class="d-flex align-items-center cc-ficha-acciones">
                        <% if (puedeEditarFicha) { %>
                        <button type="submit" class="btn btn-primary mr-2"><%= esNueva ? "Registrar cliente" : "Guardar cambios" %></button>
                        <% } %>
                        <a href="${pageContext.request.contextPath}/clientes<%= urlVolverListaFicha %>" class="btn btn-default">Volver</a>
                        <%-- B152: cuántos obligatorios faltan, en vivo. Lo llena el script del pie
                             de esta pestaña con la MISMA regla que usa el cartel al enviar, así
                             que no pueden decir números distintos. Arranca oculto: el número
                             aparece solo cuando hay algo que falta y el formulario está
                             habilitado. --%>
                        <% if (puedeEditarFicha) { %>
                        <small id="faltantesFicha" class="cc-ficha-faltantes ml-3 d-none"></small>
                        <% } %>
                        <%-- Navegación entre pasos (pedido de Joel, 10/10/2026): hace exactamente lo
                             mismo que tocar la pestaña de destino -- un clic programático sobre el
                             mismo enlace de siempre, nunca una lógica duplicada.
                             B88 (29/10/2026): el destino ya no es fijo "② Expediente", es el primer
                             paso pendiente (ver el scriptlet de arriba). --%>
                        <% if (botonPasoGuiadoVisible && pasoGuiadoFicha.ofreceBotonEn(PasoGuiado.Destino.DATOS)) { %>
                        <% if (botonPasoGuiadoHref != null) { %>
                        <a class="btn btn-outline-primary ml-auto" href="<%= botonPasoGuiadoHref %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</a>
                        <% } else { %>
                        <button type="button" class="btn btn-outline-primary ml-auto"
                                data-ir-a-pestana="<%= botonPasoGuiadoPestana %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</button>
                        <% } %>
                        <% } %>
                    </div>
                    </fieldset>
                </form>

                <%-- Mejora de pantalla (Precalificación, pedido de Joel): botón "Imprimir requisitos" --
                     un <form> aparte (no puede anidarse dentro de formularioFichaCliente), oculto,
                     que el botón de la caja de Precalificación completa y envía por JS (ver el
                     script de más abajo). Se abre en una pestaña nueva (target="_blank") y no
                     guarda nada -- POST solo porque el PDF lo genera el servidor, igual que
                     /cotizador/informe. --%>
                <form method="post" action="${pageContext.request.contextPath}/clientes/requisitos"
                      id="formularioRequisitos" target="_blank" style="display:none;">
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                    <input type="hidden" name="situacionLaboral" id="campoOcultoSituacionLaboralRequisitos">
                    <input type="hidden" name="nombreCompleto" id="campoOcultoNombreRequisitos">
                    <input type="hidden" name="cedulaIdentidad" id="campoOcultoCedulaRequisitos">
                </form>
                <script>
                    // Corrección (pedido de Joel): "contextoApp" calculado acá, en el servidor --
                    // no window.CONTEXTO_APP (definido en plantilla/pie.jsp, que se incluye AL
                    // FINAL de esta página). En la edición, los campos de Precalificación ya vienen
                    // cargados y su chequeo corre apenas se parsea este script -- antes de que
                    // pie.jsp llegue a ejecutarse -- así que window.CONTEXTO_APP todavía era
                    // undefined ahí, y el fetch salía con una URL relativa que terminaba pidiendo
                    // otra ruta (404, HTML completo mostrado como "motivo"). En el alta no se nota
                    // porque esa primera llamada corta antes del fetch (faltan datos), y para
                    // cuando el usuario completa todo a mano la página ya terminó de cargar. Mismo
                    // criterio que "accionGuardar", más abajo: calculado una sola vez acá, visible
                    // para los tres usos de esta pestaña (Precalificación, verificar cédula, Volver
                    // a la ficha de la cédula duplicada).
                    var contextoApp = '<%= request.getContextPath() %>';

                    // D-126/RF02 (punto a, corrección de Joel): cónyuge obligatorio/visible solo con
                    // estado civil "Casado/a" o "Unión de hecho" -- se esconde la SECCIÓN entera
                    // (título "Cónyuge" incluido), no solo la fila de campos: con Soltero/a el
                    // título quedaba solo, vacío, sin nada debajo.
                    (function () {
                        var selectEstadoCivil = document.getElementById('selectEstadoCivil');
                        var seccionConyuge = document.getElementById('seccionConyuge');
                        var filaConyuge = document.getElementById('filaConyuge');
                        if (!selectEstadoCivil || !seccionConyuge || !filaConyuge) { return; }
                        var camposConyuge = filaConyuge.querySelectorAll('input');
                        var estadosConConyuge = ['Casado/a', 'Unión de hecho'];
                        function actualizarConyuge() {
                            var requiere = estadosConConyuge.indexOf(selectEstadoCivil.value) !== -1;
                            seccionConyuge.style.display = requiere ? '' : 'none';
                            camposConyuge.forEach(function (campo) { campo.required = requiere; });
                        }
                        selectEstadoCivil.addEventListener('change', actualizarConyuge);
                        actualizarConyuge();
                    })();

                    // Mejora de pantalla (punto 2, pedido de Joel): edad calculada en vivo al lado de
                    // "Edad permitida: de X a Y años" -- verde "33 años" si entra en el rango leído de
                    // credito.edad_minima/maxima, roja "20 años: fuera de la política" si no. Mismo
                    // cálculo de ServicioClientes.calcularEdad (años completos, resta uno si todavía no
                    // llegó el cumpleaños este año). En alta y edición -- se recalcula al elegir/cambiar
                    // la fecha, y también al cargar la página si ya hay una fecha puesta.
                    (function () {
                        var campoFecha = document.getElementById('campoFechaNacimientoFicha');
                        var spanEdad = document.getElementById('edadCalculadaFicha');
                        var edadMinima = <%= edadMinimaFicha != null ? edadMinimaFicha : "null" %>;
                        var edadMaxima = <%= edadMaximaFicha != null ? edadMaximaFicha : "null" %>;
                        if (!campoFecha || !spanEdad || edadMinima === null || edadMaxima === null) { return; }
                        function calcularEdad(fechaTexto) {
                            var nacimiento = new Date(fechaTexto + 'T00:00:00');
                            var hoy = new Date();
                            var edad = hoy.getFullYear() - nacimiento.getFullYear();
                            var noLlegoElCumpleanios = (hoy.getMonth() < nacimiento.getMonth())
                                    || (hoy.getMonth() === nacimiento.getMonth() && hoy.getDate() < nacimiento.getDate());
                            if (noLlegoElCumpleanios) { edad--; }
                            return edad;
                        }
                        function actualizarEdadCalculada() {
                            if (!campoFecha.value) {
                                spanEdad.textContent = '';
                                return;
                            }
                            var edad = calcularEdad(campoFecha.value);
                            var dentroDeRango = edad >= edadMinima && edad <= edadMaxima;
                            spanEdad.className = dentroDeRango ? 'text-success font-weight-bold' : 'text-danger font-weight-bold';
                            spanEdad.textContent = ' ' + edad + (dentroDeRango ? ' años' : ' años: fuera de la política');
                        }
                        campoFecha.addEventListener('change', actualizarEdadCalculada);
                        actualizarEdadCalculada();
                    })();

                    // D-126: ciudad en cascada según el departamento elegido (sin ida y vuelta al servidor).
                    (function () {
                        var selectDepartamento = document.getElementById('selectDepartamento');
                        var selectCiudad = document.getElementById('selectCiudad');
                        if (!selectDepartamento || !selectCiudad) { return; }
                        var ciudades = [
                            <% if (listaCiudades != null) { boolean primeraCiudad = true;
                                for (Ciudad ciudad : listaCiudades) {
                                    if (!primeraCiudad) { %>,<% }
                                    primeraCiudad = false; %>
                            {id: <%= ciudad.getIdCiudad() %>, idDepartamento: <%= ciudad.getIdDepartamento() %>, nombre: "<%= ciudad.getNombre().replace("\"", "'") %>"}
                            <% } } %>
                        ];
                        var idCiudadActual = <%= domicilioFicha == null || domicilioFicha.getIdCiudad() == null ? "null" : domicilioFicha.getIdCiudad() %>;
                        // B153 (14/11/2026): el espejo del departamento que vive al lado de Ciudad.
                        // Puede no existir (en consulta la cajera tiene su propio campo de solo
                        // lectura, RF38/D2), así que siempre se pregunta antes de escribirlo.
                        var espejoDepartamento = document.getElementById('espejoDepartamentoDomicilio');
                        function actualizarCiudades() {
                            var idDepartamentoElegido = parseInt(selectDepartamento.value, 10);
                            // Se escribe acá, en la MISMA función que llena las ciudades: así el
                            // espejo y las opciones no pueden salir de un departamento distinto.
                            if (espejoDepartamento) {
                                espejoDepartamento.textContent = selectDepartamento.value
                                        ? selectDepartamento.options[selectDepartamento.selectedIndex].text
                                        : '—';
                            }
                            selectCiudad.innerHTML = '';
                            if (!idDepartamentoElegido) {
                                selectCiudad.appendChild(new Option('Elija primero el departamento', ''));
                                return;
                            }
                            selectCiudad.appendChild(new Option('Seleccione...', ''));
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

                    // D-127: empleador obligatorio/visible solo con Asalariado (sector privado) o
                    // Funcionario público; documentos sugeridos según la situación laboral elegida.
                    (function () {
                        var selectSituacionLaboral = document.getElementById('selectSituacionLaboral');
                        var filaEmpleador = document.getElementById('filaEmpleador');
                        var textoDocumentosSugeridos = document.getElementById('textoDocumentosSugeridos');
                        if (!selectSituacionLaboral || !filaEmpleador) { return; }
                        var camposEmpleador = filaEmpleador.querySelectorAll('input[name="empleadorNombre"], '
                                + 'input[name="empleadorDireccion"], input[name="empleadorTelefono"], '
                                + 'input[name="cargo"], input[name="antiguedadLaboralAnios"]');
                        var situacionesConEmpleador = ['ASALARIADO_SECTOR_PRIVADO', 'FUNCIONARIO_PUBLICO'];
                        var documentosPorSituacion = {
                            <% if (documentosSugeridosPorSituacion != null) { boolean primeraSituacion = true;
                                for (Map.Entry<String, List<String>> entrada : documentosSugeridosPorSituacion.entrySet()) {
                                    if (!primeraSituacion) { %>,<% }
                                    primeraSituacion = false; %>
                            "<%= entrada.getKey() %>": "<%= String.join(", ", entrada.getValue()).replace("\"", "'") %>"
                            <% } } %>
                        };
                        function actualizarSegunSituacionLaboral() {
                            var requiereEmpleador = situacionesConEmpleador.indexOf(selectSituacionLaboral.value) !== -1;
                            filaEmpleador.style.display = requiereEmpleador ? '' : 'none';
                            camposEmpleador.forEach(function (campo) { campo.required = requiereEmpleador; });
                            if (textoDocumentosSugeridos) {
                                textoDocumentosSugeridos.textContent = documentosPorSituacion[selectSituacionLaboral.value] || '(elija una situación laboral)';
                            }
                        }
                        selectSituacionLaboral.addEventListener('change', actualizarSegunSituacionLaboral);
                        actualizarSegunSituacionLaboral();
                    })();

                    // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10f):
                    // confirmación ANTES de guardar si la situación laboral cambió y el comprobante
                    // de ingreso está vigente hoy -- ServicioClientes.actualizar lo deja REEMPLAZADO
                    // sin avisar (RF03). Mismo mecanismo que caja/ficha.jsp (data-confirmar dinámico,
                    // REGLAS §13 punto 3): se agrega/quita el atributo según corresponda, nunca queda
                    // puesto a mitad de camino. El cartel pide decidir (REGLAS §12.3): botones
                    // "Sí, cambiar"/"Cancelar".
                    (function () {
                        var selectSituacionLaboralAviso = document.getElementById('selectSituacionLaboral');
                        var formularioFichaAviso = document.getElementById('formularioFichaCliente');
                        if (!selectSituacionLaboralAviso || !formularioFichaAviso) { return; }
                        var comprobanteIngresoVigenteJs = <%= comprobanteIngresoVigenteFicha %>;
                        var valorOriginalSituacionLaboral = selectSituacionLaboralAviso.value;
                        function actualizarAvisoComprobante() {
                            if (comprobanteIngresoVigenteJs
                                    && selectSituacionLaboralAviso.value !== valorOriginalSituacionLaboral) {
                                formularioFichaAviso.setAttribute('data-confirmar', 'Cambiar la situación '
                                        + 'laboral invalidará el comprobante de ingreso vigente. Deberá '
                                        + 'cargar uno nuevo.');
                                formularioFichaAviso.setAttribute('data-confirmar-boton', 'Sí, cambiar');
                            } else {
                                formularioFichaAviso.removeAttribute('data-confirmar');
                                formularioFichaAviso.removeAttribute('data-confirmar-boton');
                            }
                        }
                        selectSituacionLaboralAviso.addEventListener('change', actualizarAvisoComprobante);
                        actualizarAvisoComprobante();
                    })();

                    // Mejora de pantalla (Precalificación, pedido de Joel): indicador en vivo de los
                    // 4 campos de la caja de arriba (fecha de nacimiento, departamento, situación
                    // laboral, comprobante) -- gris mientras falte alguno (incluida la respuesta
                    // Sí/No, que no viene preseleccionada), verde "Califica" o rojo "No califica:
                    // <motivo>" una vez completos (GET /clientes/precalificar, mismo motivo que usaría
                    // el bloqueo real del alta). En el alta, mientras no califica, deshabilita
                    // "restoFormularioFicha"; en la edición nunca toca ese fieldset (ver su atributo
                    // "disabled" inicial, puesto por el servidor). También habilita "Imprimir
                    // requisitos" en cuanto hay una situación laboral elegida, y lo manda con los
                    // datos actuales a un <form> aparte (formularioRequisitos, fuera de este form).
                    (function () {
                        var esNuevaFicha = <%= esNueva %>;
                        var campoFecha = document.getElementById('campoFechaNacimientoFicha');
                        var selectDepartamento = document.getElementById('selectDepartamento');
                        var selectSituacionLaboral = document.getElementById('selectSituacionLaboral');
                        var radioComprobanteSi = document.getElementById('radioComprobanteSi');
                        var radioComprobanteNo = document.getElementById('radioComprobanteNo');
                        var indicador = document.getElementById('indicadorPrecalificacion');
                        var fieldsetResto = document.getElementById('restoFormularioFicha');
                        var botonImprimir = document.getElementById('botonImprimirRequisitos');
                        var formularioRequisitos = document.getElementById('formularioRequisitos');
                        var botonLimpiar = document.getElementById('botonLimpiarFormulario');
                        var formularioFicha = document.getElementById('formularioFichaCliente');
                        if (!campoFecha || !selectDepartamento || !selectSituacionLaboral
                                || !radioComprobanteSi || !radioComprobanteNo || !indicador) { return; }

                        function pintarIndicador(texto, clases) {
                            indicador.textContent = texto;
                            indicador.className = clases + ' mb-2';
                        }

                        function actualizarBotonImprimir() {
                            if (botonImprimir) {
                                botonImprimir.disabled = !selectSituacionLaboral.value;
                            }
                        }

                        function actualizarPrecalificacion() {
                            actualizarBotonImprimir();
                            var comprobanteElegido = radioComprobanteSi.checked || radioComprobanteNo.checked;
                            if (!campoFecha.value || !selectDepartamento.value || !selectSituacionLaboral.value || !comprobanteElegido) {
                                pintarIndicador('Complete la precalificación para continuar.', 'font-weight-bold text-muted');
                                if (esNuevaFicha && fieldsetResto) { fieldsetResto.disabled = true; }
                                return;
                            }
                            // B134 (11/11/2026, revisión final): con los cuatro datos cargados, el renglón
                            // decía "Complete la precalificación para continuar." hasta que contestaba el
                            // fetch -- o sea, en la ficha de un cliente YA registrado la pantalla abría
                            // afirmando algo falso. Ahora dice que está preguntando. Es el estado real: ni
                            // "falta completar" ni "califica" todavía.
                            pintarIndicador('Verificando la precalificación…', 'font-weight-bold text-muted');
                            var parametros = 'fechaNacimiento=' + encodeURIComponent(campoFecha.value)
                                    + '&idDepartamento=' + encodeURIComponent(selectDepartamento.value)
                                    + '&situacionLaboral=' + encodeURIComponent(selectSituacionLaboral.value)
                                    + '&presentaComprobante=' + (radioComprobanteSi.checked ? 'true' : 'false');
                            fetch(contextoApp + '/clientes/precalificar?' + parametros)
                                .then(function (respuesta) {
                                    if (!respuesta.ok) { throw new Error('http ' + respuesta.status); }
                                    return respuesta.text();
                                })
                                .then(function (motivo) {
                                    // Corrección (pedido de Joel), punto 2: si el servidor responde
                                    // algo que no es el texto plano esperado (sesión vencida que
                                    // redirige al login, una página de error, etc.), nunca mostrar
                                    // ese contenido tal cual -- se trata igual que una falla de red.
                                    if (motivo && motivo.trim().indexOf('<') === 0) { throw new Error('respuesta inesperada'); }
                                    if (!motivo) {
                                        pintarIndicador('Califica: puede continuar con el registro', 'font-weight-bold text-success');
                                        if (esNuevaFicha && fieldsetResto) { fieldsetResto.disabled = false; }
                                    } else {
                                        pintarIndicador('No califica: ' + motivo, 'font-weight-bold text-danger');
                                        if (esNuevaFicha && fieldsetResto) { fieldsetResto.disabled = true; }
                                    }
                                })
                                .catch(function () {
                                    pintarIndicador('No se pudo verificar la precalificación. Intente de nuevo.', 'font-weight-bold text-muted');
                                    if (esNuevaFicha && fieldsetResto) { fieldsetResto.disabled = true; }
                                });
                        }

                        campoFecha.addEventListener('change', actualizarPrecalificacion);
                        selectDepartamento.addEventListener('change', actualizarPrecalificacion);
                        selectSituacionLaboral.addEventListener('change', actualizarPrecalificacion);
                        radioComprobanteSi.addEventListener('change', actualizarPrecalificacion);
                        radioComprobanteNo.addEventListener('change', actualizarPrecalificacion);
                        actualizarPrecalificacion();

                        if (botonImprimir && formularioRequisitos) {
                            botonImprimir.addEventListener('click', function () {
                                var campoNombres = document.querySelector('input[name="nombres"]');
                                var campoApellidos = document.querySelector('input[name="apellidos"]');
                                var campoCedula = document.getElementById('campoCedulaIdentidad');
                                var nombreCompleto = ((campoNombres ? campoNombres.value : '') + ' '
                                        + (campoApellidos ? campoApellidos.value : '')).trim();
                                document.getElementById('campoOcultoSituacionLaboralRequisitos').value = selectSituacionLaboral.value;
                                document.getElementById('campoOcultoNombreRequisitos').value = nombreCompleto;
                                document.getElementById('campoOcultoCedulaRequisitos').value = campoCedula ? campoCedula.value : '';
                                formularioRequisitos.submit();
                            });
                        }

                        // Mejora de pantalla (pedido de Joel), punto c: "Limpiar" (solo alta) -- confirma,
                        // vacía TODO el formulario (form.reset(), valores iniciales en blanco por ser una
                        // ficha nueva) y dispara "change" en los campos que ya tienen su propio listener
                        // (cónyuge, ciudad en cascada, empleador/documentos sugeridos, edad en vivo,
                        // precalificación) para que cada sección se reacomode sola -- sin duplicar esa
                        // lógica acá. Al quedar todos los campos vacíos, actualizarPrecalificacion() ya
                        // deja el indicador en gris y restoFormularioFicha deshabilitado por su cuenta.
                        if (botonLimpiar && formularioFicha) {
                            botonLimpiar.addEventListener('click', function () {
                                Swal.fire({
                                    icon: 'warning',
                                    title: '¿Borrar los datos cargados?',
                                    showCancelButton: true,
                                    confirmButtonText: 'Sí',
                                    cancelButtonText: 'No, volver'
                                }).then(function (resultado) {
                                    if (!resultado.isConfirmed) { return; }
                                    formularioFicha.reset();
                                    ['selectEstadoCivil', 'selectDepartamento', 'selectSituacionLaboral', 'campoFechaNacimientoFicha',
                                     'campoFechaVencimientoCedulaFicha']
                                        .forEach(function (id) {
                                            var campo = document.getElementById(id);
                                            if (campo) { campo.dispatchEvent(new Event('change')); }
                                        });
                                });
                            });
                        }
                    })();

                    // RF03 Parte 4 (pedido de Joel), punto c: aviso en vivo si la cédula ya venció
                    // -- mismo criterio visual que la edad en vivo (rojo, debajo del campo), pero
                    // sin ir al servidor: alcanza con comparar contra data-hoy-servidor (Reloj.hoy()
                    // del servidor, nunca el reloj del navegador, para no contradecir nunca la
                    // validación real -- ver ServicioClientes.cedulaVencida). Funciona igual en el
                    // alta y en la edición: el campo vive en el mismo fieldset que el resto de
                    // "Datos personales" (deshabilitado en el alta hasta que califica la
                    // Precalificación, habilitado siempre en la edición).
                    (function () {
                        var campoFechaCedula = document.getElementById('campoFechaVencimientoCedulaFicha');
                        var avisoCedula = document.getElementById('avisoCedulaVencidaFicha');
                        if (!campoFechaCedula || !avisoCedula) { return; }
                        var hoyServidor = campoFechaCedula.getAttribute('data-hoy-servidor');

                        function actualizarAvisoCedulaVencida() {
                            var vencida = hoyServidor && campoFechaCedula.value && campoFechaCedula.value < hoyServidor;
                            avisoCedula.classList.toggle('d-none', !vencida);
                        }
                        campoFechaCedula.addEventListener('change', actualizarAvisoCedulaVencida);
                        campoFechaCedula.addEventListener('input', actualizarAvisoCedulaVencida);
                        actualizarAvisoCedulaVencida();
                    })();

                    <% if (puedeEditarFicha) { %>
                    // RF02 (pedido de Joel), punto g: al salir del campo C.I., avisa enseguida si ya
                    // está registrada -- sin esperar a completar todo el formulario y mandarlo. El
                    // servidor sigue validando igual al guardar (esto es solo para avisar antes).
                    (function () {
                        var campoCedula = document.getElementById('campoCedulaIdentidad');
                        if (!campoCedula) { return; }
                        var idPersonaActual = <%= esNueva ? "null" : personaFicha.getIdPersona() %>;
                        campoCedula.addEventListener('blur', function () {
                            var cedula = campoCedula.value.trim();
                            if (!cedula) { return; }
                            var url = contextoApp + '/clientes/verificar-cedula?cedula=' + encodeURIComponent(cedula)
                                    + (idPersonaActual ? '&idPersonaActual=' + idPersonaActual : '');
                            fetch(url).then(function (respuesta) { return respuesta.text(); }).then(function (texto) {
                                if (!texto) { return; }
                                var partes = texto.split('|');
                                var idEncontrado = partes[0];
                                var nombreEncontrado = partes.slice(1).join('|');
                                Swal.fire({
                                    icon: 'warning',
                                    title: 'Cédula ya registrada',
                                    text: 'Pertenece a ' + nombreEncontrado + '.',
                                    showCancelButton: true,
                                    confirmButtonText: 'Ir a su ficha',
                                    cancelButtonText: 'Cerrar'
                                }).then(function (resultado) {
                                    if (resultado.isConfirmed) {
                                        window.location.href = contextoApp + '/clientes/' + idEncontrado;
                                    }
                                });
                            });
                        });
                    })();
                    <% } %>

                    <% if (puedeEditarFicha) { %>
                    // RF02 (pedido de Joel), punto h: un solo SweetAlert con la lista de lo que
                    // falta, en vez de los globos del navegador de a uno -- mismo criterio que el
                    // formulario del Cotizador. Recorre los campos "required" al momento de
                    // enviar (los toggles de arriba ya mantienen .required sincronizado con lo que
                    // está visible: cónyuge, empleador, primera referencia en el alta), así que un
                    // campo escondido nunca entra en la lista. El servidor sigue validando igual.
                    (function () {
                        var formularioFicha = document.getElementById('formularioFichaCliente');
                        if (!formularioFicha) { return; }
                        var avisoFaltantes = document.getElementById('faltantesFicha');
                        var fieldsetRestoContador = document.getElementById('restoFormularioFicha');

                        // B152 (14/11/2026): UN solo dueño de la regla "qué falta". La calcula
                        // esta función y la usan los DOS que la muestran -- el contador en vivo de
                        // la barra de acciones y el cartel al enviar. Si cada uno la contara por su
                        // lado, en dos semanas dirían números distintos (B122: la pantalla no puede
                        // prometer algo que el otro aviso desmiente).
                        function nombresDeLoQueFalta() {
                            var faltantes = [];
                            formularioFicha.querySelectorAll('[required]').forEach(function (campo) {
                                var vacio = campo.tagName === 'SELECT' ? !campo.value : !campo.value.trim();
                                if (!vacio) { return; }
                                var grupo = campo.closest('.form-group');
                                var etiqueta = grupo ? grupo.querySelector('label') : null;
                                var texto = etiqueta ? etiqueta.textContent.replace(/\s*\*\s*$/, '').trim() : 'Un campo obligatorio';
                                if (faltantes.indexOf(texto) === -1) { faltantes.push(texto); }
                            });
                            return faltantes;
                        }

                        function actualizarContadorFaltantes() {
                            if (!avisoFaltantes) { return; }
                            // En el alta, antes de precalificar, el fieldset está deshabilitado:
                            // decir "faltan 25" cuando todavía no se puede escribir nada es ruido,
                            // no ayuda.
                            if (fieldsetRestoContador && fieldsetRestoContador.disabled) {
                                avisoFaltantes.classList.add('d-none');
                                return;
                            }
                            var cuantosFaltan = nombresDeLoQueFalta().length;
                            avisoFaltantes.classList.toggle('d-none', cuantosFaltan === 0);
                            avisoFaltantes.textContent = cuantosFaltan === 1
                                    ? 'Falta 1 dato obligatorio.'
                                    : 'Faltan ' + cuantosFaltan + ' datos obligatorios.';
                        }

                        // Diferido un tick A PROPÓSITO: los toggles de más arriba (cónyuge,
                        // empleador, primera referencia) sincronizan .required escuchando el MISMO
                        // evento 'change', así que contar en el momento daría el número de antes
                        // de ese ajuste.
                        function actualizarContadorDiferido() {
                            setTimeout(actualizarContadorFaltantes, 0);
                        }
                        formularioFicha.addEventListener('input', actualizarContadorDiferido);
                        formularioFicha.addEventListener('change', actualizarContadorDiferido);

                        // El fieldset lo habilita la precalificación DENTRO de un fetch (ver su
                        // bloque más arriba), o sea después de cualquier evento del formulario: sin
                        // esto el contador se quedaría oculto hasta la próxima tecla. Se escucha el
                        // atributo en vez de llamar a este bloque desde allá, para no acoplar los
                        // dos scripts.
                        if (fieldsetRestoContador && typeof MutationObserver === 'function') {
                            new MutationObserver(actualizarContadorFaltantes).observe(
                                    fieldsetRestoContador, {attributes: true, attributeFilter: ['disabled']});
                        }
                        actualizarContadorFaltantes();

                        formularioFicha.addEventListener('submit', function (evento) {
                            var faltantes = nombresDeLoQueFalta();
                            if (faltantes.length === 0) { return; }
                            evento.preventDefault();
                            Swal.fire({
                                icon: 'warning',
                                title: 'Faltan datos obligatorios',
                                html: '<ul class="text-left mb-0">' + faltantes.map(function (falta) {
                                    return '<li>' + falta + '</li>';
                                }).join('') + '</ul>',
                                confirmButtonText: 'Entendido'
                            });
                        });
                    })();
                    <% } %>
                </script>
            </div>

            <% if (!esNueva && puedeVerDocumentosBuro) { %>
            <div class="tab-pane" id="tabDocumentos">
                <%-- Bloque 7/E10 (D-132, pedido de Joel): checklist en el orden real del trámite,
                     con barra de progreso y un botón "Cargar"/"Reemplazar" por fila -- reemplaza
                     el desplegable alfabético + tabla vacía de antes, que no decía qué faltaba ni
                     en qué orden. La lista de requeridos y la descripción de cada uno salen de
                     ServicioClientes.construirItemsExpediente (misma regla que ya usaba "Documentos
                     sugeridos para esta situación laboral" en la pestaña de Datos). --%>
                <%
                    int totalExpedienteTab = expedienteTotalFicha == null ? 0 : expedienteTotalFicha;
                    int cumplidosExpedienteTab = expedienteCumplidosFicha == null ? 0 : expedienteCumplidosFicha;
                    int porcentajeExpedienteTab = totalExpedienteTab == 0 ? 0 : (cumplidosExpedienteTab * 100 / totalExpedienteTab);
                %>
                <h5 class="mb-1">Expediente: <%= cumplidosExpedienteTab %> de <%= totalExpedienteTab %> documentos obligatorios</h5>
                <div class="progress mb-2" style="height: 22px;">
                    <div class="progress-bar <%= pasoExpedienteCompletoFicha ? "bg-success" : "bg-warning" %>" role="progressbar"
                         style="width: <%= porcentajeExpedienteTab %>%;"><%= porcentajeExpedienteTab %> %</div>
                </div>
                <p class="text-muted mb-3">Documentos para: <strong><%= ServicioClientes.etiquetaSituacionLaboral(laboralFicha.getSituacionLaboral()) %></strong></p>

                <% if (itemsExpedienteFicha != null) {
                    int numeroFilaExpediente = 1;
                    for (ItemExpediente itemExp : itemsExpedienteFicha) {
                        boolean esFilaAutorizacion = "AUTORIZACION_CONSULTA_BURO".equals(itemExp.getTipo().getCodigoTipo());
                        if (esFilaAutorizacion) { %>
                <%-- RF03/RF04 (pedido de Joel), punto 1: 3 sub-pasos visibles con su propio ✓ cada
                     uno -- Generar (✓ vía auditoría, sin tabla nueva) → firma en papel (no es un
                     paso del sistema, solo informativo) → Cargar autorización firmada (✓ como las
                     otras filas, documento VIGENTE). --%>
                <div class="card mb-2 <%= itemExp.isCumplido() ? "border-success" : "border-warning" %>">
                    <div class="card-body py-2">
                        <strong><%= numeroFilaExpediente %>. <%= itemExp.getEtiqueta() %> (obligatorio)</strong>
                        <%-- RF03 Parte 2 (pedido de Joel), punto 4: Ley 6534/2020, con los días del
                             parámetro buro.dias_vigencia_consentimiento (nunca un número fijo). --%>
                        <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
                           data-glosario-titulo="Autorización de consulta al buró"
                           data-glosario-texto="Por la Ley 6534/2020, la empresa solo puede consultar el historial crediticio del cliente con su autorización firmada. La autorización vale <%= diasVigenciaConsentimientoFicha %> días desde la firma."></i>
                        <br><small class="text-muted"><%= itemExp.getDescripcion() %></small>
                        <div class="mt-2 pl-2" style="border-left: 3px solid #E2E8F0;">
                            <div class="d-flex align-items-center justify-content-between flex-wrap mb-2">
                                <div>
                                    <% if (ultimaGeneracionAutorizacion != null) { %>
                                    <span class="badge badge-success">✓ Generada el <%= FormateadorFechas.comoDdMmHhMm(ultimaGeneracionAutorizacion.getFechaHora()) %></span>
                                    <small class="text-muted ml-1">N.º <%= ultimaGeneracionAutorizacion.getValor() %></small>
                                    <% } else { %>
                                    <span class="badge badge-warning">Falta</span>
                                    <small class="text-muted ml-1">1. Generar la autorización</small>
                                    <% } %>
                                    <%-- RF03 Parte 4 (pedido de Joel), punto 6: solo cuando hay
                                         botón Generar o Reimprimir -- mismo criterio que decide ese
                                         botón, para no mostrar la ⓘ cuando no hay nada que generar. --%>
                                    <% if (puedeCargarDocs && !(itemExp.isCumplido() && consentimientoVigenteFicha)) { %>
                                    <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
                                       data-glosario-titulo="Generar autorización"
                                       data-glosario-texto="Genera el formulario de autorización con los datos del cliente, la fecha y un número único. Imprímalo y hágalo firmar en el momento. Si se traba la impresora, 'Reimprimir' saca el mismo documento sin cambiar el número."></i>
                                    <% } %>
                                </div>
                                <%-- RF03 Parte 2 (pedido de Joel), punto 1: 3 estados -- cargada y
                                     vigente, sin botón (nada que hacer); generada hoy y todavía sin
                                     cargar, "Reimprimir" (mismo número, GET de solo lectura); el
                                     resto (nunca generada, otro día sin cargar, o vencida aunque
                                     haya un documento cargado), "Generar [...] nueva" (POST, número
                                     nuevo) -- ya no abre el PDF directo (target=_blank): redirige
                                     con el modal de éxito y el enlace "Abrir para imprimir". --%>
                                <% if (puedeCargarDocs) { %>
                                <% if (itemExp.isCumplido() && consentimientoVigenteFicha) { %>
                                <% } else if (autorizacionGeneradaHoy && !itemExp.isCumplido()) { %>
                                <a href="<%= request.getContextPath() %>/clientes/<%= personaFicha.getIdPersona() %>/autorizacion-buro"
                                   class="btn btn-primary btn-sm" target="_blank">Reimprimir autorización</a>
                                <% } else { %>
                                <form method="post" action="<%= request.getContextPath() %>/clientes/<%= personaFicha.getIdPersona() %>/autorizacion-buro">
                                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                                    <button type="submit" class="btn btn-primary btn-sm"><%= ultimaGeneracionAutorizacion == null
                                            ? "Generar autorización" : "Generar autorización nueva" %></button>
                                </form>
                                <% } %>
                                <% } %>
                            </div>
                            <p class="text-muted mb-2"><small>2. El cliente firma la autorización impresa.</small></p>
                            <div class="d-flex align-items-center justify-content-between flex-wrap">
                                <div>
                                    <% if (itemExp.isCumplido()) { %>
                                    <span class="badge badge-success">✓ Cargada <%= FormateadorFechas.comoDdMmAaaa(itemExp.getDocumentoVigente().getFechaDocumento()) %></span>
                                    <% } else { %>
                                    <span class="badge badge-warning">Falta</span>
                                    <small class="text-muted ml-1">3. Cargar autorización firmada</small>
                                    <% } %>
                                    <i class="fas fa-info-circle text-muted ml-1" style="cursor:pointer"
                                       data-glosario-titulo="Cargar autorización firmada"
                                       data-glosario-texto="Escanee la hoja firmada por el cliente y cárguela acá. Debe ser el mismo día en que se generó. Con la autorización cargada, se habilita la consulta al buró."></i>
                                </div>
                                <%-- RF03 Parte 4, nuevo ajuste (pedido de Joel): mismo tamaño que
                                     los botones de las filas 1-3 (sin "btn-sm"). --%>
                                <% if (puedeCargarDocs) { %>
                                <div class="mt-2 mt-md-0 text-nowrap">
                                    <% if (itemExp.isCumplido()) { %>
                                    <a href="<%= request.getContextPath() %>/documentos/<%= itemExp.getDocumentoVigente().getIdDocumento() %>"
                                       class="btn btn-info" target="_blank">Ver</a>
                                    <% if (autorizacionGeneradaHoy) { %>
                                    <button type="button" class="btn btn-outline-secondary boton-fila-expediente"
                                            data-codigo="AUTORIZACION_CONSULTA_BURO">Reemplazar</button>
                                    <% } %>
                                    <% } else if (autorizacionGeneradaHoy) { %>
                                    <button type="button" class="btn btn-primary boton-fila-expediente"
                                            data-codigo="AUTORIZACION_CONSULTA_BURO">Cargar autorización firmada</button>
                                    <% } %>
                                </div>
                                <% } %>
                            </div>
                        </div>
                    </div>
                </div>
                <% } else { %>
                <%-- RF03 (pedido de Joel), Parte 1: fila 2 (cédula) puede quedar en un 3er estado,
                     "vencida" -- un documento VIGENTE cargado (itemExp.getDocumentoVigente() != null)
                     ya no alcanza para contar como cumplida si la cédula real venció
                     (itemExp.isVencida()); "Ver"/"Reemplazar" siguen disponibles porque el escaneo
                     sigue existiendo, solo que hay que cargar uno nuevo con la cédula renovada. --%>
                <%-- RF03 Parte 4 (pedido de Joel), punto 1: título+descripción separados de la fila
                     de botones (antes compartían un mismo flex row -- si la descripción ocupaba 2
                     líneas, el botón quedaba centrado respecto a esa altura, en una posición
                     distinta en cada fila). Mismo criterio que la fila 1 (Autorización): el botón
                     va en su propia fila corta, siempre a la misma altura relativa. --%>
                <div class="card mb-2 <%= itemExp.isVencida() ? "border-danger" : (itemExp.isCumplido() ? "border-success" : "border-warning") %>">
                    <div class="card-body py-2">
                        <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md
                             §10): la numeración es solo de los documentos obligatorios (ver
                             ControladorClientesServlet). 28/10/2026 (decisión de Joel del 21/10):
                             con el comprobante de domicilio ya obligatorio, los 4 ítems del
                             expediente llevan número y cuentan en "N de 4". Esta línea no cambió:
                             lee isObligatorio(), que sale de la única lista. --%>
                        <strong><%= itemExp.isObligatorio() ? numeroFilaExpediente + ". " : "" %><%= itemExp.getEtiqueta() %> (<%= itemExp.isObligatorio() ? "obligatorio" : "complementario" %>)</strong>
                        <br>
                        <% if (itemExp.isVencida()) { %>
                        <small class="text-danger">Cédula vencida (<%= FormateadorFechas.comoDdMmAaaa(itemExp.getFechaVencimiento()) %>): cargar la cédula renovada.</small>
                        <% } else if (itemExp.isPendienteRenovacion() && "CEDULA_IDENTIDAD".equals(itemExp.getTipo().getCodigoTipo())) { %>
                        <%-- RF03 (pedido de Joel), Parte 1 -- reprueba: se cambió el vencimiento en
                             Datos sin cargar el escaneo nuevo todavía (actualizar() ya marcó
                             REEMPLAZADO el anterior). --%>
                        <small class="text-muted">Cargar la cédula renovada (nuevo vencimiento <%= FormateadorFechas.comoDdMmAaaa(itemExp.getFechaVencimiento()) %>).</small>
                        <% } else if (itemExp.isPendienteRenovacion()) { %>
                        <%-- RF03 (pedido de Joel): mismo agujero, ahora con la situación laboral --
                             se cambió en Datos sin cargar el comprobante nuevo todavía (actualizar()
                             ya marcó REEMPLAZADO el anterior, que era de la situación vieja). --%>
                        <small class="text-muted">Cargar el comprobante de ingreso de
                            <%= ServicioClientes.etiquetaSituacionLaboral(laboralFicha.getSituacionLaboral()) %>.</small>
                        <% } else { %>
                        <small class="text-muted"><%= itemExp.getDescripcion() %></small>
                        <% } %>
                        <div class="mt-2 d-flex align-items-center justify-content-between flex-wrap">
                            <div>
                                <% if (itemExp.isVencida()) { %>
                                <span class="badge badge-danger">🔴 Vencida</span>
                                <% } else if (itemExp.isCumplido()) { %>
                                <span class="badge badge-success">✓ Cargado <%= FormateadorFechas.comoDdMmAaaa(itemExp.getDocumentoVigente().getFechaDocumento()) %></span>
                                <% } else if (itemExp.isObligatorio()) { %>
                                <span class="badge badge-warning">Falta</span>
                                <% } else { %>
                                <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md
                                     §10): "No cargado" en gris para el complementario -- "Falta" (en
                                     amarillo) queda reservado a lo que de verdad bloquea el envío a
                                     aprobación. 28/10/2026 (decisión de Joel del 21/10): ya no queda
                                     ningún documento complementario en el expediente, así que esta
                                     rama no se alcanza hoy; se deja porque el criterio sigue siendo
                                     el correcto si alguna vez vuelve a haber uno. --%>
                                <span class="badge badge-secondary">No cargado</span>
                                <% } %>
                            </div>
                            <div class="mt-2 mt-md-0 text-nowrap">
                                <% if (itemExp.getDocumentoVigente() != null) { %>
                                <a href="<%= request.getContextPath() %>/documentos/<%= itemExp.getDocumentoVigente().getIdDocumento() %>"
                                   class="btn btn-info" target="_blank">Ver</a>
                                <% if (puedeCargarDocs) { %>
                                <button type="button" class="btn btn-outline-secondary boton-fila-expediente"
                                        data-codigo="<%= itemExp.getTipo().getCodigoTipo() %>">Reemplazar</button>
                                <% } %>
                                <% } else if (puedeCargarDocs) { %>
                                <%-- Corrección (pedido de Joel), punto 6: "Cargar" en azul de marca
                                     (btn-primary), ya no btn-warning -- "Falta" sigue amarillo. --%>
                                <button type="button" class="btn btn-primary boton-fila-expediente"
                                        data-codigo="<%= itemExp.getTipo().getCodigoTipo() %>">Cargar</button>
                                <% } %>
                            </div>
                        </div>
                    </div>
                </div>
                <% } if (itemExp.isObligatorio()) { numeroFilaExpediente++; } } } %>

                <% if (pasoExpedienteCompletoFicha) { %>
                <div class="alert alert-success mt-3 mb-2">✓ Expediente completo</div>
                <% } %>
                <%-- Navegación entre pasos (pedido de Joel, 10/10/2026): reemplaza el texto sin
                     botón de RF03 Parte 4 -- cuando lleva a una pestaña, el botón hace exactamente
                     lo mismo que tocarla (incluido su aviso si falta la autorización firmada),
                     porque dispara un clic sobre ese mismo enlace.
                     B88/B34 (29/10/2026): el destino es el primer paso pendiente, no un fijo
                     "③ Consulta al buró" -- con una solicitud en curso lleva a ella, y si el
                     expediente sigue incompleto no ofrece nada que saltee lo que falta. Si el paso
                     pendiente es ESTA pestaña, no se muestra ningún botón (ofreceBotonEn): las
                     filas con "Falta" de acá arriba ya guían. --%>
                <% if (botonPasoGuiadoVisible && pasoGuiadoFicha.ofreceBotonEn(PasoGuiado.Destino.EXPEDIENTE)) { %>
                <div class="text-right mb-3">
                    <% if (botonPasoGuiadoHref != null) { %>
                    <a class="btn btn-outline-primary" href="<%= botonPasoGuiadoHref %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</a>
                    <% } else { %>
                    <button type="button" class="btn btn-outline-primary"
                            data-ir-a-pestana="<%= botonPasoGuiadoPestana %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</button>
                    <% } %>
                </div>
                <% } %>

                <%-- Bloque 7/E10 (D-132): un formulario oculto por fila del checklist, con el tipo
                     fijo de esa fila -- "Cargar"/"Reemplazar" solo dispara el selector de archivo
                     del <input type="file"> de su propio formulario; elegido el archivo, se sube
                     solo, sin pasar por ningún desplegable (carga en un solo paso, pedido de Joel). --%>
                <%-- RF03/RF04 (pedido de Joel), punto 2: la fila de la autorización va a la ruta
                     nueva dedicada (sube el escaneo Y registra el consentimiento en un solo paso),
                     las otras 3 filas siguen con la genérica. Mismo id de siempre
                     ("formularioFilaExpediente_[código]"/"archivoFilaExpediente_[código]"), así el
                     script de más abajo (clic en "Cargar"/"Reemplazar", confirmar reemplazo) no
                     necesita saber cuál es cuál. request.getContextPath() (método Java), no la
                     expresión de JSP: esa expresión nunca se evalúa dentro de un String de un
                     scriptlet, solo en texto de plantilla o atributos. --%>
                <% if (puedeCargarDocs && itemsExpedienteFicha != null) { for (ItemExpediente itemForm : itemsExpedienteFicha) {
                       boolean esFilaAutorizacionBuro = "AUTORIZACION_CONSULTA_BURO".equals(itemForm.getTipo().getCodigoTipo());
                       String accionFilaExpediente = request.getContextPath()
                               + (esFilaAutorizacionBuro ? "/documentos/cargar-autorizacion-firmada" : "/documentos/cargar"); %>
                <form method="post" action="<%= accionFilaExpediente %>" enctype="multipart/form-data"
                      id="formularioFilaExpediente_<%= itemForm.getTipo().getCodigoTipo() %>" style="display:none;">
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                    <input type="hidden" name="idPersona" value="<%= personaFicha.getIdPersona() %>">
                    <input type="hidden" name="idTipoDocumento" value="<%= itemForm.getTipo().getIdTipoDocumento() %>">
                    <input type="file" name="archivo" accept=".pdf,.jpg,.jpeg,.png" id="archivoFilaExpediente_<%= itemForm.getTipo().getCodigoTipo() %>">
                </form>
                <% } } %>

                <script>
                    // Punto 7 del pedido de Joel (ya existía para el desplegable de "Otros
                    // documentos"; acá se reutiliza el mismo criterio por fila): si ya hay un
                    // documento VIGENTE de ese tipo, confirmar antes de reemplazarlo (queda
                    // REEMPLAZADO, nunca se pierde, REGLAS_DESARROLLO.md §5 -- pero conviene que sea a propósito).
                    var documentosVigentesPorCodigoExpediente = {
                        <% if (itemsExpedienteFicha != null) { boolean primerItemConVigente = true;
                            for (ItemExpediente itemVigenteJs : itemsExpedienteFicha) {
                                if (itemVigenteJs.isCumplido()) {
                                    if (!primerItemConVigente) { %>,<% }
                                    primerItemConVigente = false; %>
                        "<%= itemVigenteJs.getTipo().getCodigoTipo() %>": "<%= itemVigenteJs.getDocumentoVigente().getArchivoBinario().getNombreArchivo().replace("\"", "'") %>"
                        <% } } } %>
                    };
                    (function () {
                        document.querySelectorAll('.boton-fila-expediente').forEach(function (boton) {
                            boton.addEventListener('click', function () {
                                var campoArchivo = document.getElementById('archivoFilaExpediente_' + boton.dataset.codigo);
                                if (campoArchivo) { campoArchivo.click(); }
                            });
                        });
                        document.querySelectorAll('[id^="archivoFilaExpediente_"]').forEach(function (campoArchivo) {
                            campoArchivo.addEventListener('change', function () {
                                if (!campoArchivo.files || !campoArchivo.files.length) { return; }
                                var codigo = campoArchivo.id.replace('archivoFilaExpediente_', '');
                                var formulario = document.getElementById('formularioFilaExpediente_' + codigo);
                                var nombreArchivoExistente = documentosVigentesPorCodigoExpediente[codigo];
                                if (nombreArchivoExistente) {
                                    Swal.fire({
                                        title: 'Confirmar',
                                        text: 'Ya existe un documento vigente de este tipo (' + nombreArchivoExistente
                                                + '). Si continúa, quedará reemplazado. ¿Continuar?',
                                        icon: 'warning',
                                        showCancelButton: true,
                                        confirmButtonText: 'Sí',
                                        cancelButtonText: 'No, volver'
                                    }).then(function (resultado) {
                                        if (resultado.isConfirmed) { formulario.submit(); }
                                    });
                                } else {
                                    formulario.submit();
                                }
                            });
                        });
                    })();
                </script>
            </div>

            <div class="tab-pane" id="tabBuro">
                <%-- Bloque 7/E10 (D-133, pedido de Joel): dos pasos separados, con el mismo
                     criterio de UX que "② Expediente" -- antes era una sola hoja que mezclaba
                     autorización y consulta, con el informe de Informconf cargado en otra
                     pestaña. MISMOS tipos de documento internos, mismas tablas y mismo chequeo de
                     "Enviar a aprobación" de siempre (ServicioSolicitudes.enviarAAprobacion /
                     ServicioConsultaCrediticia.obtenerParaVincularSolicitud). --%>
                <%
                    // consentimientoVigenteFicha e itemAutorizacionBuroFicha ya se calcularon arriba
                    // del todo (línea ~48/~71), antes del nav-pills -- no se repiten acá.
                    ConsultaCrediticia ultimaConsultaFicha = (listaConsultas != null && !listaConsultas.isEmpty())
                            ? listaConsultas.get(0) : null;
                %>

                <%-- Paso A · Autorización firmada por el cliente -- RF03/RF04 (pedido de Joel): ya
                     no tiene botón "Registrar"; solo muestra el estado. La generación y la carga
                     del escaneo firmado se hacen en ② Expediente, fila 1 (un solo lugar para esa
                     acción, mismo criterio que "una sola acción para registrar"). --%>
                <div class="card mb-3 <%= consentimientoVigenteFicha ? "border-success" : (autorizacionBuroCumplida ? "border-danger" : "border-warning") %>">
                    <%-- RF03 Parte 4 (pedido de Joel), punto 4: "ml-auto" en el badge en vez de
                         depender de justify-content-between -- con un título largo, el badge
                         quedaba a ~2/3 del ancho de la tarjeta, no en el borde. margin-left:auto
                         empuja el badge al borde derecho sin depender de que sea exactamente el
                         2.º elemento del flex, más robusto. --%>
                    <div class="card-header d-flex align-items-center">
                        <strong>Autorización firmada por el cliente</strong>
                        <% if (consentimientoVigenteFicha) { %>
                        <span class="badge badge-success ml-auto">✅ Vigente</span>
                        <% } else if (autorizacionBuroCumplida) { %>
                        <span class="badge badge-danger ml-auto">🔴 Vencida</span>
                        <% } else { %>
                        <span class="badge badge-warning ml-auto">🟠 Pendiente</span>
                        <% } %>
                    </div>
                    <div class="card-body">
                        <% if (consentimientoVigenteFicha) { %>
                        <%-- RF03 Parte 2 (pedido de Joel), punto 3: una sola línea con todo (quién
                             firmó, número, fecha de firma, quién cargó el escaneo) -- sin mostrar
                             el nombre del archivo. --%>
                        <p class="mb-1">
                            <span class="text-success">✓ Firmada por <%= personaFicha.getNombres() %> <%= personaFicha.getApellidos() %>
                                <% if (ultimaGeneracionAutorizacion != null) { %> · N.º <%= ultimaGeneracionAutorizacion.getValor() %><% } %>
                                <% if (itemAutorizacionBuroFicha != null && itemAutorizacionBuroFicha.getDocumentoVigente() != null) { %>
                                 · firmada el <%= FormateadorFechas.comoDdMmAaaa(itemAutorizacionBuroFicha.getDocumentoVigente().getFechaDocumento()) %>
                                <% } %>
                                <% if (nombreUsuarioCargoConsentimiento != null) { %> · cargada por <%= nombreUsuarioCargoConsentimiento %><% } %></span>
                        </p>
                        <p class="mb-0">
                            <% if (itemAutorizacionBuroFicha != null && itemAutorizacionBuroFicha.getDocumentoVigente() != null) { %>
                            <a href="<%= request.getContextPath() %>/documentos/<%= itemAutorizacionBuroFicha.getDocumentoVigente().getIdDocumento() %>"
                               class="btn btn-sm btn-info" target="_blank">Ver documento firmado</a>
                            <% } %>
                            <span class="text-success ml-2">Vigente hasta <%= FormateadorFechas.comoDdMmAaaa(fechaVencimientoConsentimiento) %>
                                <% if (diasRestantesVigenciaConsentimiento != null) { %>(quedan <%= diasRestantesVigenciaConsentimiento %> día<%= diasRestantesVigenciaConsentimiento == 1 ? "" : "s" %>)<% } %></span>
                        </p>
                        <% } else if (autorizacionBuroCumplida) { %>
                        <p class="mb-0">
                            <span class="text-danger">Vencida el <%= FormateadorFechas.comoDdMmAaaa(fechaVencimientoConsentimiento) %>.</span>
                            No se puede reutilizar la autorización vieja: genere y cargue una nueva en
                            <a href="#tabDocumentos" data-toggle="pill">② Expediente, fila 1</a>.
                        </p>
                        <% } else { %>
                        <p class="text-muted mb-0">
                            Sin autorización firmada no se puede consultar el historial crediticio.
                            Genérela y cárguela en <a href="#tabDocumentos" data-toggle="pill">② Expediente, fila 1</a>.
                        </p>
                        <% } %>
                    </div>
                </div>

                <%-- Paso B · Resultado de la consulta a Informconf -- bloqueado hasta que el Paso A esté completo. --%>
                <div class="card mb-3 <%= !consentimientoVigenteFicha ? "bg-light" : (pasoBuroCompletoFicha ? "border-success" : "border-warning") %>">
                    <%-- RF03 Parte 4 (pedido de Joel), punto 4: mismo criterio que el Paso A --
                         "ml-auto" en el badge en vez de justify-content-between. --%>
                    <div class="card-header d-flex align-items-center">
                        <strong class="<%= consentimientoVigenteFicha ? "" : "text-muted" %>">Paso B · Resultado de la consulta a Informconf</strong>
                        <% if (pasoBuroCompletoFicha) { %>
                        <span class="badge badge-success ml-auto">✅ Registrada</span>
                        <% } else { %>
                        <span class="badge badge-warning ml-auto">🟠 Pendiente</span>
                        <% } %>
                    </div>
                    <% if (!consentimientoVigenteFicha) { %>
                    <div class="card-body text-muted">
                        Primero registre la autorización (Paso A).
                    </div>
                    <% } else { %>
                    <div class="card-body">
                        <p class="mb-2">
                            <strong>1. Respuesta de Informconf (informe en PDF):</strong>
                            <% if (itemInformeBuroFicha != null && itemInformeBuroFicha.isCumplido()) { %>
                            <%= itemInformeBuroFicha.getDocumentoVigente().getArchivoBinario().getNombreArchivo() %>
                            <a href="${pageContext.request.contextPath}/documentos/<%= itemInformeBuroFicha.getDocumentoVigente().getIdDocumento() %>"
                               class="btn btn-sm btn-info" target="_blank">Ver</a>
                            <% if (puedeCargarDocs) { %>
                            <button type="button" class="btn btn-sm btn-outline-secondary" id="botonCargarInformeBuro">Reemplazar</button>
                            <% } %>
                            <% } else { %>
                            <span class="badge badge-warning">Falta</span>
                            <% if (puedeCargarDocs) { %>
                            <%-- RF03 Parte 4 (pedido de Joel), punto 4: azul de marca, mismo
                                 criterio que el resto de los botones "Cargar". --%>
                            <button type="button" class="btn btn-sm btn-primary" id="botonCargarInformeBuro">Cargar respuesta</button>
                            <% } %>
                            <% } %>
                            <% if (puedeCargarDocs) { %>
                            <%-- RF03 (pedido de Joel), punto 2: ruta dedicada, ya no la genérica --
                                 "/documentos/cargar" rechaza RESULTADO_CONSULTA_BURO (hallazgo del PDF
                                 falso cargado como autorización desde "Otros documentos", ya quitado). --%>
                            <form method="post" action="${pageContext.request.contextPath}/documentos/cargar-informe-buro" enctype="multipart/form-data"
                                  id="formularioInformeBuro" style="display:none;">
                                <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                                <input type="hidden" name="idPersona" value="<%= personaFicha.getIdPersona() %>">
                                <% if (itemInformeBuroFicha != null) { %>
                                <input type="hidden" name="idTipoDocumento" value="<%= itemInformeBuroFicha.getTipo().getIdTipoDocumento() %>">
                                <% } %>
                                <input type="file" name="archivo" accept=".pdf,.jpg,.jpeg,.png" id="archivoInformeBuro">
                            </form>
                            <% } %>
                        </p>

                        <%-- Hallazgo de Joel probando RF04 en pantalla: con la consulta ya
                             registrada, el formulario completo (puntaje/clasificación/mora/
                             resumen) quedaba visible igual, duplicando el resumen de más abajo.
                             Ahora queda oculto, con un botón para abrirlo de nuevo solo si hace
                             falta una consulta nueva (ej. una más reciente). --%>
                        <% if (pasoBuroCompletoFicha) { %>
                        <%-- Corrección (pedido de Joel, 10/10/2026): el resumen va dentro de esta
                             tarjeta (Paso B), no como un cartel aparte después de las dos tarjetas. --%>
                        <% if (ultimaConsultaFicha != null) { %>
                        <div class="alert alert-success">
                            Consulta registrada:
                            <%= ultimaConsultaFicha.getPuntajeObtenido() == null ? "sin puntaje" : FormateadorNumeros.comoEntero(ultimaConsultaFicha.getPuntajeObtenido()) %>
                            · <%= ultimaConsultaFicha.getClasificacionObtenida() == null || ultimaConsultaFicha.getClasificacionObtenida().isEmpty()
                                    ? "sin clasificación" : ultimaConsultaFicha.getClasificacionObtenida() %>
                            · <%= Boolean.TRUE.equals(ultimaConsultaFicha.getIndicadorMora()) ? "con mora" : "sin mora" %>
                        </div>
                        <% } %>
                        <div class="text-right mb-2" id="contenedorBotonNuevaConsulta">
                            <%-- Corrección (pedido de Joel, 10/10/2026): secundario, con borde y sin relleno. --%>
                            <button type="button" class="btn btn-outline-secondary" id="botonNuevaConsulta">Registrar una nueva consulta</button>
                        </div>
                        <% } %>
                        <form method="post" action="${pageContext.request.contextPath}/clientes/<%= personaFicha.getIdPersona() %>/consulta-buro" id="formularioConsultaBuro"
                              <%= pasoBuroCompletoFicha ? "style=\"display:none\"" : "" %>>
                            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                            <input type="hidden" name="proveedorBuro" value="Informconf">
                            <input type="hidden" name="idDocumentoResultado"
                                   value="<%= (itemInformeBuroFicha != null && itemInformeBuroFicha.isCumplido())
                                           ? itemInformeBuroFicha.getDocumentoVigente().getIdDocumento() : "" %>">
                            <input type="hidden" name="indicadorMora" id="campoIndicadorMora" value="">
                            <div class="form-row">
                                <div class="form-group col-md-3">
                                    <label>2. Puntaje</label>
                                    <input type="text" name="puntajeObtenido" class="form-control" inputmode="numeric">
                                </div>
                                <div class="form-group col-md-4">
                                    <label>Clasificación</label>
                                    <select name="clasificacionObtenida" id="selectClasificacionBuro" class="form-control">
                                        <option value="">Sin clasificación</option>
                                        <option value="A">A</option>
                                        <option value="B">B</option>
                                        <option value="C">C</option>
                                        <option value="D">D</option>
                                        <option value="E">E</option>
                                    </select>
                                </div>
                            </div>
                            <div class="form-group">
                                <label class="d-block">3. ¿Tiene deudas en mora?</label>
                                <button type="button" class="btn btn-outline-secondary" id="botonMoraNo">No</button>
                                <button type="button" class="btn btn-outline-secondary" id="botonMoraSi">Sí</button>
                                <%-- Corrección de Joel (RF08, 11/10/2026): más concreto -- dice
                                     qué pasa con el dato, no solo "tenerlo en cuenta". --%>
                                <div class="alert alert-warning mt-2 mb-0 d-none" id="avisoMora">El gerente verá este atraso al revisar la solicitud.</div>
                            </div>
                            <div class="form-group">
                                <label>4. Resumen (opcional)</label>
                                <textarea name="resumenResultado" class="form-control" rows="2"
                                          placeholder="Ej.: sin deudas vigentes, dos créditos cancelados en los últimos 2 años."></textarea>
                            </div>
                            <div class="text-right">
                                <button type="submit" class="btn btn-lg btn-primary" id="botonRegistrarConsulta" disabled>Registrar consulta</button>
                            </div>
                        </form>
                    </div>
                    <% } %>
                </div>

                <%-- Navegación entre pasos (pedido de Joel, 10/10/2026): mismo texto/enlace que la
                     pestaña ④ -- "tocar la pestaña" y este botón hacen literalmente lo mismo.
                     Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 14): mismo
                     criterio que la pestaña -- con un compromiso abierto, enlaza a ESO en vez de
                     "Nueva solicitud"; sin un destino con permiso, no se muestra ningún botón
                     (nada que ofrecer, a diferencia de la pestaña, que sigue mostrando el texto
                     sin enlace).
                     B88/B28 (29/10/2026): el botón es el primer paso pendiente, el mismo de las
                     otras dos pestañas -- si todavía falta un documento del expediente, lleva a ②
                     en vez de ofrecer ④ (antes solo miraba el buró, así que invitaba a la solicitud
                     con el expediente incompleto). Si el paso pendiente es ESTA pestaña (registrar
                     el resultado del buró), no se muestra ningún botón: el formulario está acá. --%>
                <% if (botonPasoGuiadoVisible && pasoGuiadoFicha.ofreceBotonEn(PasoGuiado.Destino.BURO)) { %>
                <%-- B149 (14/11/2026): el mismo control ("Siguiente: el primer paso pendiente", el
                     mecanismo único de B88) tenía tres pesos distintos según la pestaña --
                     btn-outline-primary en ① y ②, btn-lg btn-primary acá. Queda el de ①/②: un
                     control que hace lo mismo se ve igual. "Registrar consulta", más arriba, SÍ
                     sigue siendo btn-lg: es la acción principal de esta pestaña, no un paso. --%>
                <div class="text-right mb-3">
                    <% if (botonPasoGuiadoHref != null) { %>
                    <a class="btn btn-outline-primary" href="<%= botonPasoGuiadoHref %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</a>
                    <% } else { %>
                    <button type="button" class="btn btn-outline-primary"
                            data-ir-a-pestana="<%= botonPasoGuiadoPestana %>">Siguiente: <%= pasoGuiadoFicha.getEtiquetaBoton() %> →</button>
                    <% } %>
                </div>
                <% } %>

                <p class="mt-3 mb-1">
                    <a href="#" data-toggle="collapse" data-target="#historialConsultasBuro">
                        Historial de consultas (<%= listaConsultas == null ? 0 : listaConsultas.size() %>)</a>
                </p>
                <div id="historialConsultasBuro" class="collapse">
                    <table class="table table-sm table-striped">
                        <thead><tr><th>Fecha</th><th>Proveedor</th><th>Puntaje</th><th>Clasificación</th><th>¿Mora?</th><th>Resumen</th><th>Evidencia</th><th>Consultado por</th></tr></thead>
                        <tbody>
                        <% if (listaConsultas != null) { for (ConsultaCrediticia consulta : listaConsultas) { %>
                        <tr>
                            <td><%= FormateadorFechas.comoDdMmAaaa(consulta.getFechaHoraConsulta().toLocalDate()) %></td>
                            <td><%= consulta.getProveedorBuro() %></td>
                            <td><%= consulta.getPuntajeObtenido() == null ? "-" : FormateadorNumeros.comoEntero(consulta.getPuntajeObtenido()) %></td>
                            <td><%= consulta.getClasificacionObtenida() == null || consulta.getClasificacionObtenida().isEmpty() ? "-" : consulta.getClasificacionObtenida() %></td>
                            <td><%= consulta.getIndicadorMora() == null ? "(sin dato)" : (consulta.getIndicadorMora() ? "Sí" : "No") %></td>
                            <td><%= consulta.getResumenResultado() == null ? "-" : consulta.getResumenResultado() %></td>
                            <td><% if (consulta.getIdDocumentoResultado() == null) { %>-<% } else { %><a href="${pageContext.request.contextPath}/documentos/<%= consulta.getIdDocumentoResultado() %>" class="btn btn-sm btn-info" target="_blank">Ver</a><% } %></td>
                            <td><%= consulta.getNombreConsultadoPor() == null ? "-" : consulta.getNombreConsultadoPor() %></td>
                        </tr>
                        <% } }
                        if (listaConsultas == null || listaConsultas.isEmpty()) { %>
                        <tr><td colspan="8" class="text-muted">Sin consultas registradas.</td></tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>

                <script>
                    (function () {
                        <%-- RF03/RF04 (pedido de Joel), punto 4: sin la autorización firmada vigente
                             no se entra a ③ -- aviso en vez de cambiar de pestaña. autorizacionBuroCumplida
                             (arriba del todo) es el mismo ítem de fila 1 que "② Expediente". --%>
                        var autorizacionBuroCumplidaJs = <%= autorizacionBuroCumplida %>;
                        var enlacePestanaBuro = document.getElementById('enlacePestanaBuro');
                        if (enlacePestanaBuro && !autorizacionBuroCumplidaJs) {
                            enlacePestanaBuro.addEventListener('click', function (evento) {
                                evento.preventDefault();
                                Swal.fire({
                                    icon: 'warning',
                                    title: 'Falta la autorización firmada',
                                    text: 'Antes de registrar el resultado del buró, cargue la autorización firmada en ② Expediente, fila 1.',
                                    confirmButtonText: 'Entendido'
                                });
                            });
                        }

                        // Paso B, ítem 1: mismo mecanismo, con confirmación si ya hay un informe
                        // vigente (D-132, "Punto 7 del pedido de Joel": no reemplazar sin avisar).
                        var botonInforme = document.getElementById('botonCargarInformeBuro');
                        var archivoInforme = document.getElementById('archivoInformeBuro');
                        var yaHayInforme = <%= (itemInformeBuroFicha != null && itemInformeBuroFicha.isCumplido()) ? "true" : "false" %>;
                        var nombreInformeExistente = <%= (itemInformeBuroFicha != null && itemInformeBuroFicha.isCumplido())
                                ? "\"" + itemInformeBuroFicha.getDocumentoVigente().getArchivoBinario().getNombreArchivo().replace("\"", "'") + "\""
                                : "null" %>;
                        if (botonInforme && archivoInforme) {
                            botonInforme.addEventListener('click', function () { archivoInforme.click(); });
                            archivoInforme.addEventListener('change', function () {
                                if (!archivoInforme.files || !archivoInforme.files.length) { return; }
                                var formularioInforme = document.getElementById('formularioInformeBuro');
                                if (yaHayInforme) {
                                    Swal.fire({
                                        title: 'Confirmar',
                                        text: 'Ya existe una respuesta de Informconf vigente (' + nombreInformeExistente
                                                + '). Si continúa, quedará reemplazada. ¿Continuar?',
                                        icon: 'warning',
                                        showCancelButton: true,
                                        confirmButtonText: 'Sí',
                                        cancelButtonText: 'No, volver'
                                    }).then(function (resultado) {
                                        if (resultado.isConfirmed) { formularioInforme.submit(); }
                                    });
                                } else {
                                    formularioInforme.submit();
                                }
                            });
                        }

                        // Paso B, ítem 3: dos botones grandes en vez de un <select> -- guardan la
                        // elección en el campo oculto "indicadorMora" que ya espera el servidor
                        // (ServicioConsultaCrediticia.validarIndicadorMora lo sigue exigiendo).
                        var botonMoraNo = document.getElementById('botonMoraNo');
                        var botonMoraSi = document.getElementById('botonMoraSi');
                        var campoMora = document.getElementById('campoIndicadorMora');
                        var avisoMora = document.getElementById('avisoMora');
                        var botonRegistrarConsulta = document.getElementById('botonRegistrarConsulta');
                        function actualizarBotonRegistrar() {
                            if (!botonRegistrarConsulta) { return; }
                            botonRegistrarConsulta.disabled = !(yaHayInforme && campoMora.value !== '');
                        }
                        function elegirMora(esMora) {
                            campoMora.value = esMora ? 'true' : 'false';
                            if (botonMoraNo) { botonMoraNo.className = 'btn ' + (esMora ? 'btn-outline-secondary' : 'btn-success'); }
                            if (botonMoraSi) { botonMoraSi.className = 'btn ' + (esMora ? 'btn-danger' : 'btn-outline-secondary'); }
                            if (avisoMora) { avisoMora.classList.toggle('d-none', !esMora); }
                            actualizarBotonRegistrar();
                        }
                        // RF08 (pedido de Joel, 11/10/2026): si la clasificación es D o E y se
                        // toca "No" en mora, confirmar -- no bloquea, solo avisa (la clasificación
                        // y la mora siguen siendo dos datos independientes, el oficial puede
                        // confirmar que Informconf de verdad no informa mora para ese cliente).
                        var selectClasificacionBuro = document.getElementById('selectClasificacionBuro');
                        if (botonMoraNo) {
                            botonMoraNo.addEventListener('click', function () {
                                var clasificacionElegida = selectClasificacionBuro ? selectClasificacionBuro.value : '';
                                if (clasificacionElegida === 'D' || clasificacionElegida === 'E') {
                                    Swal.fire({
                                        icon: 'warning',
                                        title: 'Confirmar',
                                        text: 'La clasificación ' + clasificacionElegida + ' suele indicar atrasos. '
                                                + '¿Confirma que Informconf no informa mora?',
                                        showCancelButton: true,
                                        confirmButtonText: 'Sí, no hay mora',
                                        cancelButtonText: 'No, volver'
                                    }).then(function (resultado) {
                                        if (resultado.isConfirmed) { elegirMora(false); }
                                    });
                                } else {
                                    elegirMora(false);
                                }
                            });
                        }
                        if (botonMoraSi) { botonMoraSi.addEventListener('click', function () { elegirMora(true); }); }
                        actualizarBotonRegistrar();

                        // Hallazgo de Joel probando RF04 en pantalla: con la consulta ya
                        // registrada, el formulario arranca oculto -- este botón lo muestra para
                        // registrar una consulta nueva (ej. una más reciente).
                        var botonNuevaConsulta = document.getElementById('botonNuevaConsulta');
                        var contenedorBotonNuevaConsulta = document.getElementById('contenedorBotonNuevaConsulta');
                        var formularioConsultaBuro = document.getElementById('formularioConsultaBuro');
                        if (botonNuevaConsulta && formularioConsultaBuro) {
                            botonNuevaConsulta.addEventListener('click', function () {
                                formularioConsultaBuro.style.display = '';
                                contenedorBotonNuevaConsulta.style.display = 'none';
                            });
                        }
                    })();
                </script>
            </div>
            <% } %>

            <%-- Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): historial
                 de solicitudes y préstamos del cliente -- antes había que ir a buscarlo aparte en
                 Formalización o en el listado de Préstamos. Cada fila trae su enlace ya resuelto
                 por ControladorClientesServlet.construirEnlaceHistorial según el permiso real del
                 usuario en sesión (mismo criterio que FiltroAutorizacion) -- null si no tiene
                 acceso a ninguna pantalla para esa fila, nunca un enlace que le daría 403. --%>
            <% if (!esNueva) { %>
            <div class="tab-pane" id="tabHistorialCredito">
                <% if (historialCreditoFicha == null || historialCreditoFicha.isEmpty()) { %>
                <p class="text-muted">El cliente todavía no tiene solicitudes.</p>
                <% } else { %>
                <table class="table table-sm">
                    <thead><tr><th>Solicitud</th><th>Fecha</th><th>Estado</th><th>Operación</th><th>Estado del préstamo</th><th></th></tr></thead>
                    <tbody>
                        <% for (FilaHistorialCredito filaHistorial : historialCreditoFicha) { %>
                        <tr>
                            <td><%= filaHistorial.getCodigoSolicitud() %></td>
                            <td><%= FormateadorFechas.comoDdMmAaaa(filaHistorial.getFechaSolicitud().toLocalDate()) %></td>
                            <td><%= filaHistorial.getEstadoSolicitud().getEtiqueta() %></td>
                            <td><%= filaHistorial.getIdentificadorOperacion() == null ? "—" : filaHistorial.getIdentificadorOperacion() %></td>
                            <td><%= filaHistorial.getEstadoPrestamo() == null ? "—" : filaHistorial.getEstadoPrestamo().getEtiqueta() %></td>
                            <td><% if (filaHistorial.getEnlace() != null) { %><a href="<%= filaHistorial.getEnlace() %>" class="btn btn-sm btn-outline-primary">Ver</a><% } %></td>
                        </tr>
                        <% } %>
                    </tbody>
                </table>
                <% } %>
            </div>
            <% } %>

        </div>
    </div>
</div>
<script>
    // B88 (pedido de Joel, 28/10/2026): un solo mecanismo para los botones "Siguiente: …" que
    // llevan a otra pestaña -- dispara un clic sobre el enlace real de esa pestaña, igual que
    // hacían los dos scripts separados que había antes (uno por pestaña), para heredar también
    // cualquier aviso que esa pestaña tenga (ej. "Falta la autorización firmada" en ③). Al final
    // de la página: así ya están en el DOM los botones de las tres pestañas.
    (function () {
        var botonesPaso = document.querySelectorAll('[data-ir-a-pestana]');
        for (var indiceBoton = 0; indiceBoton < botonesPaso.length; indiceBoton++) {
            botonesPaso[indiceBoton].addEventListener('click', function (evento) {
                var enlacePestanaDestino = document.getElementById(
                        evento.currentTarget.getAttribute('data-ir-a-pestana'));
                if (enlacePestanaDestino) {
                    enlacePestanaDestino.click();
                }
            });
        }
    })();
</script>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
