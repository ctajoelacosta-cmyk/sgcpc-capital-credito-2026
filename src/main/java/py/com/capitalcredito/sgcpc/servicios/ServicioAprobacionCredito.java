package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dao.MotivoRechazoSolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dto.FilaBandejaAprobacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.MotivoRechazoSolicitud;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P05, aprobación de solicitud (Doc. 10 §10.8: ControladorAprobacionesServlet /
 * ServicioAprobacionCredito / ServicioMotorFinanciero). Solo GERENTE_ADMINISTRADOR
 * (APROBAR_RECHAZAR_SOLICITUD, RN-06).
 */
public class ServicioAprobacionCredito {

    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final MotivoRechazoSolicitudDAO motivoRechazoSolicitudDAO = new MotivoRechazoSolicitudDAO();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    /** RN-06: "el rechazo exige motivo". Pura. */
    public void validarMotivoRechazo(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new NegocioException("Ingrese el motivo del rechazo.");
        }
    }

    /** Bloque 7/E10, Tanda A (D-126): lista referencial para el <select> de aprobaciones/ficha.jsp. */
    public List<MotivoRechazoSolicitud> listarMotivosRechazo(Connection conexion) throws SQLException {
        return motivoRechazoSolicitudDAO.listarActivos(conexion);
    }

    /**
     * RF09/RF10 (pedido de Joel, 11/10/2026, cerrado tras la prueba en pantalla del mismo día):
     * usa el mismo mensaje rico que la carrera de dos pestañas (ver `construirErrorYaDecidida`
     * más abajo) -- es el mismo problema de fondo (alguien más ya decidió o canceló la
     * solicitud), así que muestra siempre la misma advertencia con quién y cuándo, en vez del
     * mensaje genérico anterior.
     */
    private void validarPendienteAprobacion(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE_APROBACION) {
            Usuario quienDecidio = resolverQuienDecidio(conexion, solicitud);
            throw new py.com.capitalcredito.sgcpc.excepciones.SolicitudYaDecididaException(
                    construirMensajeYaDecidida(solicitud, quienDecidio));
        }
    }

    /**
     * B84 (decisión de Joel, 28/10/2026): RN-01 "un cliente, un préstamo a la vez" revalidada al
     * APROBAR -- tercer momento, con el mismo criterio que ya se eligió para el comprobante de
     * ingreso (RF05/RF06/RF11, 18/10/2026: el mismo control en los tres momentos). Antes solo se
     * aplicaba al crear (`ServicioSolicitudes.crear`) y al enviar a aprobación
     * (`ServicioSolicitudes.enviarAAprobacion`, línea 489), que es de donde sale este texto.
     *
     * <p>Reusa `SolicitudDAO.existeClienteConPrestamoAbierto` (RN-01, D-61): cuentan como préstamo
     * abierto `PENDIENTE_DESEMBOLSO`, `ACTIVO` y `EN_MORA` -- o sea todo lo que no sea
     * `FINALIZADO` ni `ANULADO_ANTES_DESEMBOLSO`. Sin consulta nueva.</p>
     *
     * <p>El booleano alcanza acá (a diferencia de la versión de `ServicioFormalizacion`, que filtra
     * el préstamo propio): una solicitud `PENDIENTE_APROBACION` nunca tiene préstamo todavía, así
     * que cualquier préstamo abierto del cliente es por fuerza de OTRA solicitud.</p>
     *
     * <p>Visible en el paquete, no privado, para que `PruebaRN01AprobarFormalizar` lo ejercite con
     * su propia conexión en transacción (mismo motivo por el que `ServicioFormalizacion` ya expone
     * así `construirMensajeSinComprobanteFormalizacion`).</p>
     *
     * <p>La regla NO está respaldada por ningún RF del anteproyecto (RF05 habla de la cuota y del
     * 40 %, RF06 de los requisitos previos): es política de crédito del dueño, nivel 2 de la
     * jerarquía de requisitos (reglas internas del proyecto, regla del 18/10/2026).</p>
     */
    void validarSinPrestamoAbierto(Connection conexion, long idCliente) throws SQLException {
        if (solicitudDAO.existeClienteConPrestamoAbierto(conexion, idCliente)) {
            throw new NegocioException(MENSAJE_PRESTAMO_ABIERTO_APROBAR);
        }
    }

    /** B84: mismo texto base que los otros dos momentos de RN-01, sin la sigla interna (punto 11
     * del Bloque 7/E10), en usted y con el "qué sigue" que pidió Joel el 28/10/2026. Constante
     * visible en el paquete para poder verificarla con JUnit, sin base. */
    static final String MENSAJE_PRESTAMO_ABIERTO_APROBAR =
            "El cliente ya tiene un préstamo no cerrado. No se puede aprobar. Para pedir otro "
            + "crédito, primero debe terminar de pagarlo o cancelarlo.";

    /** Usuario que decidió o canceló la solicitud, según su estado actual. Null si ese usuario
     * no quedó registrado (no debería pasar en este sistema, pero el mensaje lo cubre igual). */
    private Usuario resolverQuienDecidio(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        boolean cancelada = solicitud.getEstadoSolicitud() == EstadoSolicitud.CANCELADA;
        Long idQuienDecidio = cancelada ? solicitud.getCanceladoPor() : solicitud.getDecididoPor();
        return idQuienDecidio == null ? null : servicioUsuarios.buscarPorId(conexion, idQuienDecidio);
    }

    /**
     * RF09/RF10 (pedido de Joel, 11/10/2026): cuando `marcarDecidida` devuelve `false` (alguien
     * más decidió primero, carrera de dos pestañas) -- relee la solicitud ya actualizada (la
     * misma transacción, bajo READ COMMITTED, ya ve el valor comiteado por la otra, porque
     * `marcarDecidida` esperó su lock antes de revisar el `WHERE`) para armar un mensaje con
     * quién y cuándo. Nunca debería devolver null salvo que la solicitud se haya borrado, algo
     * que este sistema no hace.
     */
    private py.com.capitalcredito.sgcpc.excepciones.SolicitudYaDecididaException construirErrorYaDecidida(
            Connection conexion, long idSolicitud) throws SQLException {
        SolicitudCredito actual = solicitudDAO.obtenerPorId(conexion, idSolicitud);
        Usuario quienDecidio = resolverQuienDecidio(conexion, actual);
        return new py.com.capitalcredito.sgcpc.excepciones.SolicitudYaDecididaException(
                construirMensajeYaDecidida(actual, quienDecidio));
    }

    /** Pura: "Esta solicitud ya fue decidida/cancelada por NOMBRE el FECHA HORA. Actualice la
     * página para ver su estado actual." (texto exacto pedido por Joel, 11/10/2026). La
     * solicitud CANCELADA usa "cancelada" y fecha_cancelacion/cancelado_por; cualquier otro
     * estado ya decidido (APROBADA/RECHAZADA) usa "decidida" y fecha_decision/decidido_por. */
    String construirMensajeYaDecidida(SolicitudCredito actual, Usuario quienDecidio) {
        boolean cancelada = actual.getEstadoSolicitud() == EstadoSolicitud.CANCELADA;
        LocalDateTime fecha = cancelada ? actual.getFechaCancelacion() : actual.getFechaDecision();
        String nombre = quienDecidio == null ? "otro usuario" : quienDecidio.getNombres() + " " + quienDecidio.getApellidos();
        String verbo = cancelada ? "cancelada" : "decidida";
        return "Esta solicitud ya fue " + verbo + " por " + nombre + " el " + FormateadorFechas.comoDdMmAaaaHhMm(fecha)
                + ". Actualice la página para ver su estado actual.";
    }

    public SolicitudCredito obtenerPorId(Connection conexion, long idSolicitud) throws SQLException {
        return solicitudDAO.obtenerPorId(conexion, idSolicitud);
    }

    public List<SolicitudCredito> listarPendientes(Connection conexion) throws SQLException {
        return solicitudDAO.listarPendientesAprobacion(conexion);
    }

    /** Pestaña "Historial" (corrección 4, Joel): solicitudes ya decididas. */
    public List<SolicitudCredito> listarDecididas(Connection conexion) throws SQLException {
        return solicitudDAO.listarDecididas(conexion);
    }

    /** RF07 (pedido de Joel, 10/10/2026): Historial con filtro de estado y rango de fechas. */
    public List<SolicitudCredito> listarDecididasConFiltro(Connection conexion, String estado,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        return solicitudDAO.listarDecididasConFiltro(conexion, estado, desde, hasta);
    }

    // ==========================================================================================
    // B175 (14/11/2026): orden y paginación de la bandeja.
    //
    // Mismo patrón que B167 en Solicitudes -- lista blanca, orden EN MEMORIA, y el servicio como
    // único dueño de la regla (el controlador solo le pasa los parámetros crudos) -- con dos
    // diferencias que son propias de esta pantalla:
    //
    //   1. La bandeja tiene DOS pestañas con columnas distintas, así que cada una tiene su lista
    //      blanca y su orden por defecto. Una sola lista habría dejado ordenar "Pendientes" por
    //      "fecha de decisión", que en esa pestaña no existe.
    //   2. Se ordena el SolicitudCredito, NO la FilaBandejaAprobacion: la fila ya viene formateada
    //      ("15.000.000 Gs.", "12 meses", "154,6 %", "dd/mm/aaaa"), así que ordenar por esos textos
    //      ordenaría alfabéticamente un número -- 9.000.000 quedaría después de 15.000.000 y la
    //      fecha ordenaría por día. Por eso el orden va ANTES de construir las filas.
    // ==========================================================================================

    /**
     * B175: el orden por defecto de <b>Pendientes</b> -- la fecha de envío, ascendente. Es
     * exactamente el {@code ORDER BY fecha_envio_aprobacion} que la consulta ya traía, y es lo que
     * promete el subtítulo de la pantalla ("en orden de llegada"): la que más espera, primero.
     */
    public static final String ORDEN_POR_DEFECTO_PENDIENTES = "enviada";

    /** B175: el sentido del orden por defecto de Pendientes -- de la más vieja a la más nueva. */
    public static final String SENTIDO_POR_DEFECTO_PENDIENTES = "asc";

    /**
     * B175 (ítem 9 del informe de Aprobaciones, pedido de Joel): el orden por defecto del
     * <b>Historial</b> -- la fecha de la decisión, <b>descendente</b>. Es el
     * {@code ORDER BY COALESCE(fecha_decision, fecha_cancelacion) DESC} que la consulta ya traía:
     * lo que cambia no es el orden, es que ahora está <b>declarado en un solo lugar</b> y la
     * pantalla lo muestra con su flecha, en vez de ser un orden invisible que vivía en el SQL.
     */
    public static final String ORDEN_POR_DEFECTO_HISTORIAL = "fechadecision";

    /** B175: el sentido del orden por defecto del Historial -- la decisión más nueva, primero. */
    public static final String SENTIDO_POR_DEFECTO_HISTORIAL = "desc";

    /**
     * B175: las únicas claves por las que se puede ordenar <b>Pendientes</b>, como lista blanca.
     *
     * <p><b>"Esperando" no está, a propósito</b>: es el mismo dato que "Enviada" contado de otra
     * manera (el tiempo desde esa fecha), así que ordenar por una es ordenar por la otra. Dos
     * controles de orden sobre el mismo dato es la duplicación que B123 dejó escrita, y las flechas
     * de los dos encabezados se contradirían entre sí.</p>
     */
    private static final java.util.Set<String> ORDENES_PENDIENTES = java.util.Collections
            .unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                    "codigo", "enviada", "cliente", "monto", "plazo", "afectacion", "enviadapor")));

    /** B175: las únicas claves por las que se puede ordenar el <b>Historial</b>. Son las de
     * Pendientes menos "enviadapor" (esa columna no se dibuja ahí) más las tres de la decisión. */
    private static final java.util.Set<String> ORDENES_HISTORIAL = java.util.Collections
            .unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                    "codigo", "enviada", "cliente", "monto", "plazo", "afectacion",
                    "decision", "decididapor", "fechadecision")));

    /** B175: lo que la pestaña puede pedir; cualquier otra cosa cae en su orden por defecto. */
    public static boolean esOrdenPermitido(String clave, boolean vistaHistorial) {
        return clave != null && (vistaHistorial ? ORDENES_HISTORIAL : ORDENES_PENDIENTES).contains(clave);
    }

    /** B175: la clave de orden por defecto de la pestaña. */
    public static String ordenPorDefecto(boolean vistaHistorial) {
        return vistaHistorial ? ORDEN_POR_DEFECTO_HISTORIAL : ORDEN_POR_DEFECTO_PENDIENTES;
    }

    /** B175: el sentido por defecto de la pestaña. */
    public static String sentidoPorDefecto(boolean vistaHistorial) {
        return vistaHistorial ? SENTIDO_POR_DEFECTO_HISTORIAL : SENTIDO_POR_DEFECTO_PENDIENTES;
    }

    /** B175: la clave que se va a usar de verdad. El controlador le pasa el parámetro crudo del
     * pedido y no decide nada. */
    public static String ordenAplicado(String clavePedida, boolean vistaHistorial) {
        return esOrdenPermitido(clavePedida, vistaHistorial) ? clavePedida : ordenPorDefecto(vistaHistorial);
    }

    /**
     * B175: el sentido que se va a usar, y <b>acá vive toda la regla</b> -- igual que en B167, donde
     * tenerla partida entre el servicio y un ternario del controlador hizo que la primera carga
     * ordenara al revés (lo agarró el JUnit).
     *
     * <ul>
     *   <li>Sin columna pedida: el de siempre de esa pestaña ({@link #sentidoPorDefecto}).</li>
     *   <li>Con columna pedida y sin sentido: ascendente, que es lo que espera el primer clic.</li>
     *   <li>Con sentido explícito: ese.</li>
     * </ul>
     */
    public static String sentidoAplicado(String clavePedida, String sentidoPedido, boolean vistaHistorial) {
        if ("asc".equalsIgnoreCase(sentidoPedido)) {
            return "asc";
        }
        if ("desc".equalsIgnoreCase(sentidoPedido)) {
            return "desc";
        }
        return esOrdenPermitido(clavePedida, vistaHistorial) ? "asc" : sentidoPorDefecto(vistaHistorial);
    }

    /**
     * B175: quién decidió esta solicitud -- el que canceló si está CANCELADA, el que aprobó o
     * rechazó en cualquier otro caso. Estaba escrito dos veces en el controlador (el mapa de
     * decisores y el armado de cada fila) y ahora lo necesitaba también el comparador: tres copias
     * de la misma regla, así que pasa a tener un dueño (la regla de "un dato, un dueño" aplicada a
     * una condición de dos líneas).
     */
    public static Long idDecisorDe(SolicitudCredito solicitud) {
        return solicitud.getEstadoSolicitud() == EstadoSolicitud.CANCELADA
                ? solicitud.getCanceladoPor() : solicitud.getDecididoPor();
    }

    /** B175: la fecha que la pestaña Historial muestra como "Fecha de decisión" -- la de la
     * cancelación si está CANCELADA. Mismo criterio que construirFilaBandeja. */
    private LocalDateTime fechaDecisionDe(SolicitudCredito solicitud) {
        return solicitud.getEstadoSolicitud() == EstadoSolicitud.CANCELADA
                ? solicitud.getFechaCancelacion() : solicitud.getFechaDecision();
    }

    /**
     * B175: ordena la bandeja. Puro (sin conexión), así que se prueba con JUnit
     * ({@code ServicioAprobacionCreditoOrdenTest}).
     *
     * <p><b>Se ordena la lista COMPLETA, antes de construir las filas y de cortar la página.</b>
     * Ordenar la página ya cortada daría un orden falso -- "la primera por monto" sería la primera
     * de esa página (la lección que B157 dejó escrita para el orden en SQL).</p>
     *
     * <p>Detalles que importan: las personas (cliente, quien envió, quien decidió) se comparan por
     * <b>apellidos y nombres, normalizados</b> ({@code NormalizadorTexto.normalizarParaBuscar}, el
     * mismo que usa el buscador), así que "Ávila" ordena junto a "Avila"; el estado se ordena por su
     * <b>etiqueta</b>, que es lo que el gerente lee, no por el orden del enum; los nulos van
     * <b>al final</b> en los dos sentidos; y todo orden que no sea el de la pestaña <b>desempata por
     * el de la pestaña</b>, para que dos filas con el mismo valor no se intercambien entre dos
     * cargas -- con páginas, eso se ve como una fila repetida o una fila que desaparece.</p>
     *
     * @param clientes mapa por {@code idCliente}, el mismo que ya arma el controlador
     * @param decisores mapa por id de decisor ({@link #idDecisorDe}); puede venir vacío en Pendientes
     * @param oficiales mapa por {@code creadoPor}; puede venir vacío en el Historial
     * @return una lista nueva ordenada; la recibida no se toca
     */
    public List<SolicitudCredito> ordenarParaBandeja(List<SolicitudCredito> solicitudes,
            java.util.Map<Long, Persona> clientes, java.util.Map<Long, Usuario> decisores,
            java.util.Map<Long, Usuario> oficiales, String clave, String sentido,
            final boolean vistaHistorial) {
        if (solicitudes == null) {
            return new java.util.ArrayList<>();
        }
        final String claveUsada = ordenAplicado(clave, vistaHistorial);
        final boolean descendente = "desc".equalsIgnoreCase(sentido);
        final java.util.Map<Long, Persona> clientesUsados = clientes == null
                ? java.util.Collections.<Long, Persona>emptyMap() : clientes;
        final java.util.Map<Long, Usuario> decisoresUsados = decisores == null
                ? java.util.Collections.<Long, Usuario>emptyMap() : decisores;
        final java.util.Map<Long, Usuario> oficialesUsados = oficiales == null
                ? java.util.Collections.<Long, Usuario>emptyMap() : oficiales;
        java.util.Comparator<SolicitudCredito> comparador = new java.util.Comparator<SolicitudCredito>() {
            @Override
            public int compare(SolicitudCredito una, SolicitudCredito otra) {
                int resultado = compararPorClaveBandeja(una, otra, claveUsada, descendente,
                        clientesUsados, decisoresUsados, oficialesUsados);
                if (resultado == 0 && !ordenPorDefecto(vistaHistorial).equals(claveUsada)) {
                    // Desempate estable: el orden de siempre de la pestaña, con su propio sentido.
                    resultado = compararPorClaveBandeja(una, otra, ordenPorDefecto(vistaHistorial),
                            "desc".equals(sentidoPorDefecto(vistaHistorial)),
                            clientesUsados, decisoresUsados, oficialesUsados);
                }
                return resultado;
            }
        };
        List<SolicitudCredito> ordenadas = new java.util.ArrayList<>(solicitudes);
        java.util.Collections.sort(ordenadas, comparador);
        return ordenadas;
    }

    /**
     * B175: compara dos filas por una clave, ya con el sentido aplicado.
     *
     * <p><b>El sentido se aplica acá adentro, y los nulos quedan afuera de la inversión</b>: una
     * fila a la que le falta ese dato va al final en los dos sentidos, porque una fila incompleta
     * nunca encabeza el listado. La primera versión invertía el resultado completo (como hace B167
     * en Solicitudes) y entonces "descendente" subía los nulos al primer lugar -- justo lo contrario
     * de lo que decía el javadoc. Lo agarraron dos pruebas nuevas en su primera corrida.</p>
     */
    private int compararPorClaveBandeja(SolicitudCredito una, SolicitudCredito otra, String clave,
            boolean descendente, java.util.Map<Long, Persona> clientes,
            java.util.Map<Long, Usuario> decisores, java.util.Map<Long, Usuario> oficiales) {
        Comparable<?> valorUna = valorDeOrden(una, clave, clientes, decisores, oficiales);
        Comparable<?> valorOtra = valorDeOrden(otra, clave, clientes, decisores, oficiales);
        if (valorUna == null || valorOtra == null) {
            if (valorUna == null && valorOtra == null) {
                return 0;
            }
            return valorUna == null ? 1 : -1;
        }
        int resultado = compararNoNulos(valorUna, valorOtra);
        return descendente ? -resultado : resultado;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compararNoNulos(Comparable<?> una, Comparable<?> otra) {
        return ((Comparable) una).compareTo(otra);
    }

    /**
     * B175: el valor por el que se ordena, crudo -- el del modelo, no el texto ya formateado de la
     * fila. Null cuando esa fila no tiene ese dato (y entonces va al final, ver arriba).
     *
     * <p>Los textos se devuelven <b>normalizados</b> ({@code NormalizadorTexto.normalizarParaBuscar},
     * el mismo criterio que el buscador: sin tildes y en minúsculas), así que la comparación ya es
     * insensible a mayúsculas y "Ávila" ordena junto a "Avila". Las personas se ordenan por
     * <b>apellidos y nombres</b>, igual que en B167, para que las dos pantallas ordenen igual al
     * cliente.</p>
     */
    private Comparable<?> valorDeOrden(SolicitudCredito solicitud, String clave,
            java.util.Map<Long, Persona> clientes, java.util.Map<Long, Usuario> decisores,
            java.util.Map<Long, Usuario> oficiales) {
        switch (clave) {
            case "codigo":
                return textoParaOrdenar(solicitud.getCodigoSolicitud());
            case "cliente":
                return nombreDePersonaParaOrdenar(clientes.get(solicitud.getIdCliente()));
            case "monto":
                return solicitud.getMontoSolicitado();
            case "plazo":
                return Integer.valueOf(solicitud.getPlazoSolicitadoMeses());
            case "afectacion":
                return solicitud.getPorcentajeAfectacion();
            case "enviadapor":
                return nombreDeUsuarioParaOrdenar(oficiales.get(solicitud.getCreadoPor()));
            case "decision":
                return solicitud.getEstadoSolicitud() == null ? null
                        : textoParaOrdenar(solicitud.getEstadoSolicitud().getEtiqueta());
            case "decididapor":
                return nombreDeUsuarioParaOrdenar(decisores.get(idDecisorDe(solicitud)));
            case "fechadecision":
                return fechaDecisionDe(solicitud);
            default:
                // "enviada", y la red de seguridad de cualquier clave que no esté en la lista.
                return solicitud.getFechaEnvioAprobacion();
        }
    }

    /** B175: las personas se ordenan por apellidos y nombres, sin tildes -- mismo criterio que
     * B167 en Solicitudes, para que las dos pantallas ordenen igual al cliente. */
    private String nombreDePersonaParaOrdenar(Persona persona) {
        return persona == null ? null
                : NormalizadorTexto.normalizarParaBuscar(persona.getApellidos() + " " + persona.getNombres());
    }

    /** B175: los usuarios del sistema (quien envió, quien decidió), con el mismo criterio. */
    private String nombreDeUsuarioParaOrdenar(Usuario usuario) {
        return usuario == null ? null
                : NormalizadorTexto.normalizarParaBuscar(usuario.getApellidos() + " " + usuario.getNombres());
    }

    /** Null se mantiene null (va al final); el resto, normalizado como el buscador. */
    private String textoParaOrdenar(String texto) {
        return texto == null ? null : NormalizadorTexto.normalizarParaBuscar(texto);
    }

    /**
     * RF07 (pedido de Joel, 11/10/2026): formatea ACÁ, entera, una fila de la bandeja (Pendientes
     * o Historial) -- para que un dato faltante no llegue crudo a la JSP y deje la página
     * dibujada a medias. Hallazgo del 11/10/2026: SOL-2026-0003 es CANCELADA pero nunca se llegó
     * a enviar a aprobación (se canceló directo desde BORRADOR, ver
     * solicitudes/ficha.jsp/esBorrador) -- su fecha_envio_aprobacion es null, y
     * lista.jsp la usaba sin chequear null (<%= fila.getFechaEnvioAprobacion().toLocalDate() %>),
     * lo que tiraba NullPointerException a mitad de la fila. Acá cada campo nulo se resuelve a
     * "-" (o queda en null para el campo que no aplica a esta pestaña) antes de construir la fila.
     * Pura: cliente/decisor ya vienen resueltos (el controlador hizo esas consultas).
     */
    public FilaBandejaAprobacion construirFilaBandeja(SolicitudCredito solicitud, Persona cliente, Usuario decisor,
            Usuario enviadaPor, boolean vistaHistorial) {
        boolean cancelada = solicitud.getEstadoSolicitud() == EstadoSolicitud.CANCELADA;
        String clienteTexto = cliente == null ? "-"
                : cliente.getNombres() + " " + cliente.getApellidos() + " — "
                        + FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad());
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): por
        // separado, solo para el buscador (filtrarPorTexto, más abajo) -- clienteTexto sigue
        // siendo el único que arma la columna "Cliente".
        String clienteNombres = cliente == null ? null : cliente.getNombres();
        String clienteApellidos = cliente == null ? null : cliente.getApellidos();
        String clienteCedula = cliente == null ? null : FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad());
        LocalDateTime fechaEnvio = solicitud.getFechaEnvioAprobacion();
        String fechaEnvioFormateada = fechaEnvio == null ? "-" : FormateadorFechas.comoDdMmAaaa(fechaEnvio.toLocalDate());
        String esperando = null;
        if (!vistaHistorial) {
            // PENDIENTE_APROBACION siempre tiene fecha_envio_aprobacion (se setea en el mismo
            // paso que marca ese estado) -- el "-" es solo una red de seguridad, no se espera.
            esperando = fechaEnvio == null ? "-"
                    : FormateadorFechas.comoDuracion(Duration.between(fechaEnvio, Reloj.ahora()).toMinutes());
        }
        // B174 (14/11/2026): el oficial que envió, solo para Pendientes (en el Historial la
        // columna es "Decidida por"). El "-" es la misma red que el resto de los campos de esta
        // fila: un dato faltante no tiene que tirar una excepción a mitad de la tabla.
        String enviadaPorNombre = vistaHistorial ? null
                : (enviadaPor == null ? "-" : enviadaPor.getNombres() + " " + enviadaPor.getApellidos());
        String decisionEtiqueta = null;
        String decididaPorNombre = null;
        String fechaDecisionFormateada = null;
        if (vistaHistorial) {
            decisionEtiqueta = solicitud.getEstadoSolicitud().getEtiqueta();
            decididaPorNombre = decisor == null ? "-" : decisor.getNombres() + " " + decisor.getApellidos();
            LocalDateTime fechaDecision = cancelada ? solicitud.getFechaCancelacion() : solicitud.getFechaDecision();
            fechaDecisionFormateada = fechaDecision == null ? "-" : FormateadorFechas.comoDdMmAaaaHhMm(fechaDecision);
        }
        return new FilaBandejaAprobacion(solicitud.getIdSolicitud(), solicitud.getCodigoSolicitud(),
                fechaEnvioFormateada, esperando, clienteTexto, clienteNombres, clienteApellidos, clienteCedula,
                FormateadorNumeros.comoGuaranies(solicitud.getMontoSolicitado()),
                solicitud.getPlazoSolicitadoMeses() + " meses",
                FormateadorNumeros.comoPorcentajeUnDecimal(solicitud.getPorcentajeAfectacion()), decisionEtiqueta,
                decididaPorNombre, fechaDecisionFormateada, enviadaPorNombre);
    }

    /** Pura (sin conexión, fácil de probar con JUnit). Por N.º de solicitud, cédula (con o sin
     * puntos, mismo criterio que ServicioClientes.normalizarCedula) o nombre del cliente -- sin
     * acentos y en los dos órdenes (NormalizadorTexto.normalizarParaBuscar), mismo patrón que
     * ServicioMora.filtrarPorTexto (paso 1); pública (no de paquete, a diferencia de Mora) porque
     * acá quien arma las filas es el controlador, no este servicio. Se aplica DESPUÉS de
     * construir las filas, encima del filtro de estado/fecha de Historial que ya existe (AND,
     * nunca lo reemplaza). */
    public List<FilaBandejaAprobacion> filtrarPorTexto(List<FilaBandejaAprobacion> filas, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return filas;
        }
        String codigoBuscado = texto.trim().toUpperCase();
        String cedulaBuscada = servicioClientes.normalizarCedula(texto.trim());
        String nombreBuscado = NormalizadorTexto.normalizarParaBuscar(texto);
        List<FilaBandejaAprobacion> filtradas = new java.util.ArrayList<>();
        for (FilaBandejaAprobacion fila : filas) {
            boolean coincideCodigo = fila.getCodigo() != null && fila.getCodigo().toUpperCase().contains(codigoBuscado);
            boolean coincideCedula = !cedulaBuscada.isEmpty() && fila.getClienteCedula() != null
                    && servicioClientes.normalizarCedula(fila.getClienteCedula()).contains(cedulaBuscada);
            boolean coincideNombre = !nombreBuscado.isEmpty() && fila.getClienteNombres() != null
                    && fila.getClienteApellidos() != null
                    && (NormalizadorTexto.normalizarParaBuscar(fila.getClienteNombres() + " " + fila.getClienteApellidos())
                            .contains(nombreBuscado)
                        || NormalizadorTexto.normalizarParaBuscar(fila.getClienteApellidos() + " " + fila.getClienteNombres())
                            .contains(nombreBuscado));
            if (coincideCodigo || coincideCedula || coincideNombre) {
                filtradas.add(fila);
            }
        }
        return filtradas;
    }

    /** Las consultas que sustentan esta solicitud, para mostrar el resultado del buró (D-59). */
    public List<ConsultaCrediticia> listarConsultasDeLaSolicitud(Connection conexion, long idSolicitud) throws SQLException {
        return servicioConsultaCrediticia.listarPorSolicitud(conexion, idSolicitud);
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): franja de arriba de `aprobaciones/ficha.jsp` -- mora e
     * clasificación de riesgo alto (D/E), ya formateadas, para que la JSP no calcule nada. Si
     * alguna consulta tiene mora, esa es la única franja que se muestra (roja): sus líneas
     * incluyen la clasificación cuando esa misma consulta también es D/E, y además se agregan
     * las líneas de "riesgo alto" de cualquier otra consulta D/E que no tenga mora -- nada se
     * pierde, pero no se duplica una segunda franja naranja aparte. Solo si NINGUNA consulta
     * tiene mora, la clasificación D/E se muestra en su propia franja naranja (`hayRiesgoAlto`).
     * Pura.
     */
    public py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud construirAvisoBuro(List<ConsultaCrediticia> consultas,
            Persona cliente) {
        List<String> lineasMora = new java.util.ArrayList<>();
        List<String> lineasRiesgoAltoSinMora = new java.util.ArrayList<>();
        for (ConsultaCrediticia consulta : consultas) {
            boolean esMora = Boolean.TRUE.equals(consulta.getIndicadorMora());
            String clasificacion = consulta.getClasificacionObtenida();
            boolean esRiesgoAlto = "D".equals(clasificacion) || "E".equals(clasificacion);
            String persona = etiquetaPersonaConsulta(consulta, cliente);
            String fecha = FormateadorFechas.comoDdMmAaaa(consulta.getFechaHoraConsulta().toLocalDate());
            if (esMora) {
                String sufijoClasificacion = esRiesgoAlto ? " (clasificación " + clasificacion + ")" : "";
                lineasMora.add("El buró indica mora" + sufijoClasificacion + ": " + persona + " — Informconf (" + fecha + ").");
            } else if (esRiesgoAlto) {
                lineasRiesgoAltoSinMora.add("Riesgo alto según Informconf: clasificación " + clasificacion + " — " + persona + ".");
            }
        }
        boolean hayMora = !lineasMora.isEmpty();
        boolean hayRiesgoAlto = !lineasRiesgoAltoSinMora.isEmpty();
        List<String> lineas = new java.util.ArrayList<>(lineasMora);
        lineas.addAll(lineasRiesgoAltoSinMora);
        return new py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud(hayMora, hayRiesgoAlto, lineas);
    }

    /** "Nombre Apellido (cliente)" -- mismo criterio que la tabla de consultas de
     * `aprobaciones/ficha.jsp` (corrección 1, Joel, E4). */
    private String etiquetaPersonaConsulta(ConsultaCrediticia consulta, Persona cliente) {
        if (cliente != null && cliente.getIdPersona().equals(consulta.getIdPersona())) {
            return cliente.getNombres() + " " + cliente.getApellidos() + " (cliente)";
        }
        return "-";
    }

    /**
     * PO-04 paso 2: revalida el tope de tasa vigente (RN-15) y la capacidad, y congela las
     * condiciones solicitadas como aprobadas (RN-07: nunca otro monto/plazo). RN-18 (pedido de
     * Joel, 17/10/2026, antes D-62 permitía aprobar igual con confirmación): si el buró indica
     * mora, bloquea -- "no se presta a quien figura con deudas en Informconf" pasa de juicio del
     * gerente a validación del servidor.
     */
    public void aprobar(long idSolicitud, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarPendienteAprobacion(conexion, solicitud);

                // B84 (decisión de Joel, 28/10/2026): RN-01 revalidada acá, el tercer momento --
                // mismo lugar relativo que en ServicioSolicitudes.enviarAAprobacion (justo después
                // de validar el estado) y ANTES de revincularConsultasMasRecientes, que escribe en
                // la base: si va a rechazar, no escribe nada.
                validarSinPrestamoAbierto(conexion, solicitud.getIdCliente());

                // RN-15, revalidado al momento de aprobar (ajuste D): el tope pudo cambiar desde
                // que se simuló.
                LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
                servicioParametros.resolverTopeLegalVigente(limiteVigente);
                ResultadoLimiteTasa resultadoLimite = servicioMotorFinanciero.validarLimiteTasa(
                        solicitud.getTasaTeaSimulada(), limiteVigente.getTasaMaximaTea());
                if (!resultadoLimite.isOk()) {
                    // A.8 (consolidación, pedido de Joel): no es un error del servidor, es que el
                    // tope cambió desde que se simuló -- advertencia, no error (mismo criterio
                    // que FechaFueraDePlazoException, RF11).
                    throw new py.com.capitalcredito.sgcpc.excepciones.CondicionesCambiaronException(
                            "El tope de tasa vigente cambió y la TEA simulada ("
                            + FormateadorNumeros.comoPorcentaje(solicitud.getTasaTeaSimulada())
                            + ") ya no cumple (tope actual: "
                            + FormateadorNumeros.comoPorcentaje(limiteVigente.getTasaMaximaTea())
                            + "). Vuelva a simular la solicitud antes de aprobar.");
                }

                // RN-03, revalidado (PO-04 paso 2 "revalida... la capacidad"): las condiciones
                // quedan congeladas, no se recalcula desde cero -- solo se confirma que lo que ya
                // se validó al enviar sigue vigente.
                if (solicitud.getPorcentajeAfectacion().compareTo(solicitud.getLimiteAfectacionAplicado()) > 0) {
                    throw new py.com.capitalcredito.sgcpc.excepciones.CondicionesCambiaronException(
                            "La capacidad de pago supera el límite. Vuelva a simular la solicitud "
                            + "antes de aprobar.");
                }

                // RN-18 (pedido de Joel, 17/10/2026): revincula la consulta MÁS RECIENTE con
                // evidencia de cada persona (misma función que ya usaba RF08, movida acá ANTES de
                // decidir nada -- antes corría después de marcarDecidida) y, si la mora vinculada
                // ya incluye esa consulta recién revinculada, bloquea sin aprobar nada. Si
                // marcarDecidida pierde la carrera de dos pestañas más abajo, esta revinculación
                // queda dentro de la misma transacción y se revierte con el resto (ver el catch).
                List<ConsultaCrediticia> consultasVistas = revincularConsultasMasRecientes(conexion, solicitud);
                if (tieneMoraVinculada(conexion, idSolicitud)) {
                    throw new NegocioException("El cliente figura con deudas en Informconf. No se puede "
                            + "aprobar: rechace la solicitud con el motivo \"Informconf: figura con deudas\".");
                }

                solicitud.setEstadoSolicitud(EstadoSolicitud.APROBADA);
                // RN-07: el monto y el plazo aprobados son los solicitados, nunca otros.
                solicitud.setMontoAprobado(solicitud.getMontoSolicitado());
                solicitud.setPlazoAprobadoMeses(solicitud.getPlazoSolicitadoMeses());
                solicitud.setTasaTnaAprobada(solicitud.getTasaTnaPropuesta());
                solicitud.setTasaTeaAprobada(solicitud.getTasaTeaSimulada());
                solicitud.setCftAprobado(solicitud.getCftSimulado());
                solicitud.setPrimeraFechaVencimientoAprobada(solicitud.getPrimeraFechaVencimientoSolicitada());
                solicitud.setIdLimiteTasaAplicado(limiteVigente.getIdLimiteTasa());
                solicitud.setDecididoPor(idUsuario);
                solicitud.setMotivoRechazo(null);
                if (!solicitudDAO.marcarDecidida(conexion, solicitud)) {
                    // RF09 (pedido de Joel, 11/10/2026): alguien más decidió esta solicitud en la
                    // angosta ventana entre el validarPendienteAprobacion de arriba y este UPDATE
                    // (carrera de dos pestañas) -- la revinculación de consultas de más arriba
                    // queda dentro de esta misma transacción, el catch de abajo hace rollback de
                    // todo (no se llegó a cambiar nada más).
                    throw construirErrorYaDecidida(conexion, idSolicitud);
                }

                servicioAuditoria.registrar(conexion, idUsuario, "APROBAR_SOLICITUD", "SOLICITUD_CREDITO",
                        idSolicitud, "Aprobó la solicitud " + solicitud.getCodigoSolicitud() + "."
                                + formatearConsultasVistasParaAuditoria(consultasVistas));
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * RN-06: motivo obligatorio, de la lista referencial (Bloque 7/E10, Tanda A, D-126).
     * detalle es obligatorio solo si el motivo elegido lo exige (hoy, solo "Otro") -- el texto
     * combinado (nombre del motivo + detalle, si hay) sigue guardándose en motivo_rechazo (TEXT),
     * para no romper las pantallas/reportes que ya lo muestran tal cual.
     */
    public void rechazar(long idSolicitud, Long idMotivoRechazo, String detalle, long idUsuario) throws SQLException {
        detalle = NormalizadorTexto.normalizarTextoLibre(detalle);
        if (idMotivoRechazo == null) {
            throw new NegocioException("Seleccione el motivo del rechazo.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                MotivoRechazoSolicitud motivo = motivoRechazoSolicitudDAO.buscarPorId(conexion, idMotivoRechazo);
                if (motivo == null) {
                    throw new NegocioException("El motivo de rechazo seleccionado no existe.");
                }
                if (motivo.isRequiereDetalle() && (detalle == null || detalle.isEmpty())) {
                    throw new NegocioException("El motivo \"" + motivo.getNombre() + "\" requiere un detalle.");
                }
                String motivoCompleto = (detalle == null || detalle.isEmpty())
                        ? motivo.getNombre() : motivo.getNombre() + " - " + detalle;
                validarMotivoRechazo(motivoCompleto);

                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarPendienteAprobacion(conexion, solicitud);
                solicitud.setEstadoSolicitud(EstadoSolicitud.RECHAZADA);
                solicitud.setDecididoPor(idUsuario);
                solicitud.setIdMotivoRechazo(idMotivoRechazo);
                solicitud.setMotivoRechazo(motivoCompleto);
                if (!solicitudDAO.marcarDecidida(conexion, solicitud)) {
                    // RF10 (pedido de Joel, 11/10/2026): mismo criterio que aprobar -- ver el
                    // comentario de esa rama.
                    throw construirErrorYaDecidida(conexion, idSolicitud);
                }

                // RF08 (pedido de Joel, 11/10/2026): mismo criterio que aprobar -- deja en el
                // detalle qué consulta vio el gerente al decidir.
                List<ConsultaCrediticia> consultasVistas = revincularConsultasMasRecientes(conexion, solicitud);
                servicioAuditoria.registrar(conexion, idUsuario, "RECHAZAR_SOLICITUD", "SOLICITUD_CREDITO",
                        idSolicitud, "Rechazó la solicitud " + solicitud.getCodigoSolicitud() + ". Motivo: " + motivoCompleto
                                + formatearConsultasVistasParaAuditoria(consultasVistas));
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * P05: el gerente puede cancelar una PENDIENTE_APROBACION o una APROBADA todavía no
     * formalizada. RF06 (pedido de Joel, 10/10/2026): motivo de la lista fija + detalle
     * (obligatorio solo en "Otro"), igual que ServicioSolicitudes.cancelar (los dos caminos
     * comparten la misma lista, ver ServicioSolicitudes.listarMotivosCancelacion).
     */
    public void cancelar(long idSolicitud, String codigoMotivo, String detalle, long idUsuario) throws SQLException {
        detalle = NormalizadorTexto.normalizarTextoLibre(detalle);
        String motivo = servicioSolicitudes.construirMotivoCancelacion(codigoMotivo, detalle);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarCancelable(solicitud);
                solicitudDAO.marcarCancelada(conexion, idSolicitud, idUsuario, motivo);
                servicioAuditoria.registrar(conexion, idUsuario, "CANCELAR_SOLICITUD", "SOLICITUD_CREDITO",
                        idSolicitud, "Canceló la solicitud " + solicitud.getCodigoSolicitud() + ". Motivo: " + motivo);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * C20 (29/10/2026): una solicitud YA FORMALIZADA no se puede cancelar.
     *
     * <p>El control de estado estaba, y su propio mensaje prometía "(todavía no formalizada)" --
     * pero nadie miraba la columna {@code formalizada}: una solicitud formalizada se queda en
     * estado {@code APROBADA} (no existe un estado "FORMALIZADA"), así que pasaba el filtro y
     * {@code marcarCancelada} la dejaba CANCELADA con su préstamo {@code CC-…} vivo, sus
     * documentos ya emitidos y, si ya se había desembolsado, con cobros encima --
     * {@code marcarCancelada} solo toca {@code solicitudes_credito}, nunca el préstamo. Sin forma
     * de deshacerlo. Demostrado en pantalla por Joel el 29/10/2026 (SOL-2026-0006 formalizada como
     * CC-2026-00005: el botón "Cancelar solicitud" seguía ahí; no se apretó, para no dejar datos
     * contradictorios en la base).</p>
     *
     * <p>El camino legítimo para deshacer un préstamo ya formalizado sigue siendo el que existe:
     * anularlo antes del desembolso (permiso {@code ANULAR_PRESTAMO_PRE_DESEMBOLSO}), que sí anula
     * el préstamo. <b>El texto del rechazo no cambia</b>: ya decía la regla correcta.</p>
     *
     * <p>Pura, para poder probarla sin base (cancelar abre su propia conexión): visibilidad de
     * paquete, mismo criterio que {@code validarSinPrestamoAbierto}.</p>
     */
    void validarCancelable(SolicitudCredito solicitud) {
        if ((solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE_APROBACION
                && solicitud.getEstadoSolicitud() != EstadoSolicitud.APROBADA)
                || solicitud.isFormalizada()) {
            throw new NegocioException("Solo se puede cancelar una solicitud pendiente de aprobación o "
                    + "aprobada (todavía no formalizada).");
        }
    }

    private boolean tieneMoraVinculada(Connection conexion, long idSolicitud) throws SQLException {
        for (ConsultaCrediticia consulta : servicioConsultaCrediticia.listarPorSolicitud(conexion, idSolicitud)) {
            if (Boolean.TRUE.equals(consulta.getIndicadorMora())) {
                return true;
            }
        }
        return false;
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): la consulta más reciente con evidencia del cliente --
     * no la vinculada al enviar, sino la que `obtenerParaVincularSolicitud` elegiría si la
     * solicitud se enviara HOY. Alimenta tanto la franja de arriba de la ficha
     * (`construirAvisoBuro`) como el refuerzo del botón "Aprobar" — las dos usan siempre esta
     * misma lista, nunca la vinculada al enviar.
     */
    public List<ConsultaCrediticia> listarConsultasMasRecientes(Connection conexion, Persona cliente)
            throws SQLException {
        List<ConsultaCrediticia> resultado = new java.util.ArrayList<>();
        if (cliente != null) {
            ConsultaCrediticia delCliente = servicioConsultaCrediticia.obtenerMasRecienteConEvidencia(conexion,
                    cliente.getIdPersona());
            if (delCliente != null) {
                resultado.add(delCliente);
            }
        }
        return resultado;
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): filas de la tabla "Resultado del buró" -- las
     * vinculadas al enviar MÁS, si el cliente tiene una consulta más reciente que todavía no
     * está entre esas (una registrada después del envío), también esa, marcada
     * (`posteriorAlEnvio`). Nunca se pierde la vinculada original (trazabilidad del envío).
     */
    public List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> construirFilasConsultaBuro(Connection conexion,
            SolicitudCredito solicitud, Persona cliente) throws SQLException {
        List<ConsultaCrediticia> vinculadas = listarConsultasDeLaSolicitud(conexion, solicitud.getIdSolicitud());
        List<ConsultaCrediticia> masRecientes = listarConsultasMasRecientes(conexion, cliente);
        return combinarFilasConsultaBuro(vinculadas, masRecientes, solicitud.getFechaEnvioAprobacion(), cliente);
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): la parte PURA de construirFilasConsultaBuro -- combina
     * las vinculadas al enviar con las más recientes (sin duplicar por id) y marca
     * `posteriorAlEnvio` en las que son nuevas. Separada de construirFilasConsultaBuro (que sí
     * toca la base) para poder probarla con JUnit sin conexión.
     */
    public List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> combinarFilasConsultaBuro(
            List<ConsultaCrediticia> vinculadas, List<ConsultaCrediticia> masRecientes,
            LocalDateTime fechaEnvioAprobacion, Persona cliente) {
        List<ConsultaCrediticia> todas = new java.util.ArrayList<>(vinculadas);
        for (ConsultaCrediticia masReciente : masRecientes) {
            boolean yaEsta = false;
            for (ConsultaCrediticia existente : todas) {
                if (existente.getIdConsultaCrediticia().equals(masReciente.getIdConsultaCrediticia())) {
                    yaEsta = true;
                    break;
                }
            }
            if (!yaEsta) {
                todas.add(masReciente);
            }
        }
        List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> filas = new java.util.ArrayList<>();
        for (ConsultaCrediticia consulta : todas) {
            boolean posteriorAlEnvio = fechaEnvioAprobacion != null
                    && consulta.getFechaHoraConsulta().isAfter(fechaEnvioAprobacion);
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud(
                    etiquetaPersonaConsulta(consulta, cliente),
                    // Corrección de Joel (RF08, 11/10/2026): con la hora -- dos consultas del
                    // mismo día (la vinculada y una nueva) se veían con la misma fecha, sin
                    // forma de distinguir cuál era la más vieja a simple vista.
                    FormateadorFechas.comoDdMmAaaaHhMm(consulta.getFechaHoraConsulta()),
                    consulta.getPuntajeObtenido() == null ? "-" : FormateadorNumeros.comoEntero(consulta.getPuntajeObtenido()),
                    consulta.getClasificacionObtenida() == null ? "-" : consulta.getClasificacionObtenida(),
                    Boolean.TRUE.equals(consulta.getIndicadorMora()),
                    consulta.getResumenResultado() == null ? "-" : consulta.getResumenResultado(),
                    consulta.getIdDocumentoResultado(), posteriorAlEnvio));
        }
        return filas;
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): al Aprobar o Rechazar, vincula también la consulta más
     * reciente con evidencia del cliente si es distinta de la ya vinculada al enviar -- esa
     * sigue vinculada (nada se desvincula), así la trazabilidad de "qué se vio al decidir" queda
     * completa. Devuelve las consultas que el gerente vio al decidir, para el detalle de la
     * auditoría. Misma transacción que aprobar/rechazar.
     */
    private List<ConsultaCrediticia> revincularConsultasMasRecientes(Connection conexion, SolicitudCredito solicitud)
            throws SQLException {
        List<ConsultaCrediticia> vistas = new java.util.ArrayList<>();
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        ConsultaCrediticia vistaCliente = revincularSiEsNueva(conexion, cliente, solicitud.getIdSolicitud());
        if (vistaCliente != null) {
            vistas.add(vistaCliente);
        }
        return vistas;
    }

    private ConsultaCrediticia revincularSiEsNueva(Connection conexion, Persona persona, long idSolicitud)
            throws SQLException {
        if (persona == null) {
            return null;
        }
        ConsultaCrediticia masReciente = servicioConsultaCrediticia.obtenerMasRecienteConEvidencia(conexion,
                persona.getIdPersona());
        if (masReciente == null) {
            return null;
        }
        if (masReciente.getIdSolicitud() == null || masReciente.getIdSolicitud() != idSolicitud) {
            servicioConsultaCrediticia.vincularASolicitud(conexion, masReciente.getIdConsultaCrediticia(), idSolicitud);
        }
        return masReciente;
    }

    /** "Consulta(s) vista(s) al decidir: [id 12, 11/10/2026, clasificación E, mora sí] ..." --
     * pedido de Joel (11/10/2026), para el detalle de la auditoría de aprobar/rechazar. */
    private String formatearConsultasVistasParaAuditoria(List<ConsultaCrediticia> consultas) {
        if (consultas.isEmpty()) {
            return "";
        }
        StringBuilder detalle = new StringBuilder(" Consulta(s) vista(s) al decidir:");
        for (ConsultaCrediticia consulta : consultas) {
            detalle.append(" [id ").append(consulta.getIdConsultaCrediticia())
                    .append(", ").append(FormateadorFechas.comoDdMmAaaa(consulta.getFechaHoraConsulta().toLocalDate()))
                    .append(", clasificación ")
                    .append(consulta.getClasificacionObtenida() == null ? "sin clasificación" : consulta.getClasificacionObtenida())
                    .append(", mora ").append(Boolean.TRUE.equals(consulta.getIndicadorMora()) ? "sí" : "no")
                    .append("]");
        }
        return detalle.toString();
    }
}
