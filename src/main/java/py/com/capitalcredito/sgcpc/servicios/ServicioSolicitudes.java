package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.AuditoriaDAO;
import py.com.capitalcredito.sgcpc.dao.DocumentoExpedienteDAO;
import py.com.capitalcredito.sgcpc.dao.FeriadoDAO;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.dto.ItemChecklistSolicitud;
import py.com.capitalcredito.sgcpc.dto.OpcionMotivoCancelacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoCapacidadPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * P04, solicitud y evaluación crediticia (Doc. 10 §10.7: ControladorSolicitudesServlet /
 * ServicioSolicitudes / ServicioMotorFinanciero). Se omite una clase "ServicioEvaluacionCredito"
 * aparte (D-60): calcularCapacidadPago/validarLimiteTasa ya son puros y reutilizables, y esta
 * clase ya los orquesta directamente, igual que ServicioCotizacion hace con la cotización de P13.
 */
public class ServicioSolicitudes {

    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final DocumentoExpedienteDAO documentoExpedienteDAO = new DocumentoExpedienteDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final FeriadoDAO feriadoDAO = new FeriadoDAO();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ServicioCotizacion servicioCotizacion = new ServicioCotizacion();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    // RF06 (pedido de Joel, 10/10/2026): "quién envió" no tiene columna propia (a diferencia de
    // decidido_por/cancelado_por) -- se resuelve desde la auditoría, ver obtenerIdUsuarioQueEnvio.
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    // ------------------------------------------------------------------
    // Validaciones puras
    // ------------------------------------------------------------------

    /**
     * RF06 (pedido de Joel, 10/10/2026): lista fija de motivos de cancelación de una solicitud,
     * igual criterio que el rechazo (RF10) y la baja de clientes -- "Otro" exige detalle. Lista
     * fija en código (no en tabla, ver OpcionMotivoCancelacion), compartida por los dos caminos de
     * cancelación (ServicioSolicitudes.cancelar, oficial/BORRADOR; ServicioAprobacionCredito.
     * cancelar, gerente/PENDIENTE_APROBACION-APROBADA).
     */
    public static final String MOTIVO_CANCELACION_OTRO = "OTRO";

    private static final List<OpcionMotivoCancelacion> MOTIVOS_CANCELACION = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    new OpcionMotivoCancelacion("DESISTIO", "El cliente desistió", false),
                    new OpcionMotivoCancelacion("DATOS_INCORRECTOS", "Datos incorrectos, se carga de nuevo", false),
                    new OpcionMotivoCancelacion("CAMBIO_SITUACION", "Cambió la situación del cliente", false),
                    new OpcionMotivoCancelacion(MOTIVO_CANCELACION_OTRO, "Otro", true)));

    /** Pura. */
    public List<OpcionMotivoCancelacion> listarMotivosCancelacion() {
        return MOTIVOS_CANCELACION;
    }

    /** Pura. */
    private OpcionMotivoCancelacion buscarMotivoCancelacion(String codigo) {
        for (OpcionMotivoCancelacion opcion : MOTIVOS_CANCELACION) {
            if (opcion.getCodigo().equals(codigo)) {
                return opcion;
            }
        }
        return null;
    }

    /**
     * RF06 (pedido de Joel, 10/10/2026): valida el motivo elegido (lista fija) y el detalle
     * (obligatorio solo si el motivo lo exige, hoy solo "Otro") y devuelve el texto combinado a
     * guardar -- mismo patrón que ServicioAprobacionCredito.rechazar con motivos_rechazo_solicitud.
     * "detalle" ya viene normalizado (NormalizadorTexto) del llamador. Pura.
     */
    public String construirMotivoCancelacion(String codigoMotivo, String detalle) {
        OpcionMotivoCancelacion opcion = buscarMotivoCancelacion(codigoMotivo);
        if (opcion == null) {
            throw new NegocioException("Seleccione el motivo de la cancelación.");
        }
        if (opcion.isRequiereDetalle() && (detalle == null || detalle.isEmpty())) {
            throw new NegocioException("El motivo \"" + opcion.getEtiqueta() + "\" requiere un detalle.");
        }
        return (detalle == null || detalle.isEmpty()) ? opcion.getEtiqueta() : opcion.getEtiqueta() + " - " + detalle;
    }

    /**
     * RF05 (pedido de Joel, 10/10/2026): el 40 % de afectación se rechaza ya al crear/simular, no
     * solo al enviar a aprobación -- sin excepción. Mensaje con título y detalle
     * (REGLAS_DESARROLLO.md §12). Pura.
     */
    public void validarLimiteAfectacion(BigDecimal porcentajeAfectacion, BigDecimal limiteAfectacion) {
        if (porcentajeAfectacion.compareTo(limiteAfectacion) <= 0) {
            return;
        }
        String limiteTexto = FormateadorNumeros.comoPorcentaje(limiteAfectacion);
        throw new NegocioException("La cuota supera el límite de afectación del ingreso (" + limiteTexto
                + "). Baje el monto o alargue el plazo.");
    }

    /** Bloque 7/E10, Tanda B (D-127): suma tratando null como cero (persona sin el dato todavía).
     * Delegada a ServicioMotorFinanciero (RF11, 11/10/2026): RF05/RF06 y RF11 usan la misma fórmula. */
    public BigDecimal sumarCuotasOtrasDeudas(BigDecimal cuotaCliente) {
        return servicioMotorFinanciero.sumarCuotasOtrasDeudas(cuotaCliente);
    }

    /** RF02 (pedido de Joel): movido a ServicioClientes (dueño real de la política de
     * elegibilidad de la Persona, reutilizada ahora también al registrar/editar un cliente) --
     * delega, misma firma de siempre, para no romper nada de Solicitudes. Pura. */
    public int calcularEdad(LocalDate fechaNacimiento, LocalDate hoy) {
        return servicioClientes.calcularEdad(fechaNacimiento, hoy);
    }

    /** RF02 (pedido de Joel): ídem, delega en ServicioClientes.evaluarPoliticaCredito. Pura. */
    public void evaluarPoliticaCredito(int edad, int edadMinima, int edadMaxima, String nombreDepartamento,
            List<String> departamentosHabilitados, String situacionLaboral, List<String> situacionesAdmitidas) {
        servicioClientes.evaluarPoliticaCredito(edad, edadMinima, edadMaxima, nombreDepartamento,
                departamentosHabilitados, situacionLaboral, situacionesAdmitidas);
    }

    /** Compromiso abierto encontrado por buscarCompromisoAbierto: la solicitud y por cuál de los dos motivos. */
    /**
     * RN-18 ("no se presta a quien figura con deudas en Informconf", pedido de Joel del
     * 17/10/2026): el texto del bloqueo, en un solo lugar -- lo usa el bloqueo real de
     * {@link #enviarAAprobacion} y, desde B88 (29/10/2026), el paso guiado de la ficha del cliente
     * ({@code ServicioClientes.calcularSiguientePaso}), para que el oficial lea exactamente lo
     * mismo antes de intentar enviar y al intentarlo.
     */
    public static final String MENSAJE_MORA_INFORMCONF =
            "El cliente figura con deudas en Informconf. No se puede otorgar el crédito.";

    public static final class CompromisoAbierto {
        private final SolicitudCredito solicitud;
        private final boolean viaPrestamo;

        public CompromisoAbierto(SolicitudCredito solicitud, boolean viaPrestamo) {
            this.solicitud = solicitud;
            this.viaPrestamo = viaPrestamo;
        }

        public SolicitudCredito getSolicitud() {
            return solicitud;
        }

        /** true = por un préstamo no cerrado (RN-01); false = por una solicitud propia sin formalizar (RN-02). */
        public boolean isViaPrestamo() {
            return viaPrestamo;
        }
    }

    /**
     * Compromiso abierto con la empresa (RN-01/RN-02, D-68): una solicitud propia todavía
     * abierta (sin formalizar, E5) o un préstamo no cerrado. Una sola definición compartida por
     * las dos reglas, para que no se desincronicen entre sí. Sin lanzar: el llamador decide si
     * rechaza directo o solo lo muestra en pantalla.
     */
    public CompromisoAbierto buscarCompromisoAbierto(Connection conexion, long idPersona) throws SQLException {
        SolicitudCredito propia = solicitudDAO.buscarAbiertaPorCliente(conexion, idPersona);
        if (propia != null) {
            return new CompromisoAbierto(propia, false);
        }
        SolicitudCredito viaPrestamo = solicitudDAO.buscarPorClienteConPrestamoAbierto(conexion, idPersona);
        return viaPrestamo == null ? null : new CompromisoAbierto(viaPrestamo, true);
    }

    /** Mensaje RN-01 (préstamo no cerrado) o RN-02 (solicitud abierta) según el tipo de compromiso. Pura. */
    public String mensajeCompromisoAbierto(CompromisoAbierto compromiso) {
        return compromiso.isViaPrestamo()
                ? "El cliente ya tiene un préstamo no cerrado. No puede solicitar otro crédito hasta que finalice."
                : "El cliente ya tiene una solicitud abierta. Debe cancelarla o esperar la decisión antes de crear otra.";
    }

    private void validarEstadoBorrador(SolicitudCredito solicitud) {
        if (solicitud.getEstadoSolicitud() != EstadoSolicitud.BORRADOR) {
            throw new NegocioException("Solo se puede modificar una solicitud mientras está en borrador.");
        }
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    public SolicitudCredito obtenerPorId(Connection conexion, long idSolicitud) throws SQLException {
        return solicitudDAO.obtenerPorId(conexion, idSolicitud);
    }

    /** Ajuste 3 (Joel): filtro por estado opcional (null = todas). */
    public List<SolicitudCredito> listarPorOficial(Connection conexion, long idUsuario, EstadoSolicitud estadoFiltro)
            throws SQLException {
        return solicitudDAO.listarPorOficial(conexion, idUsuario, estadoFiltro);
    }

    /**
     * B167 (14/11/2026): la clave de orden por defecto del listado de solicitudes -- la fecha,
     * descendente, que es exactamente el {@code ORDER BY fecha_solicitud DESC} que la consulta
     * venía trayendo.
     */
    public static final String ORDEN_POR_DEFECTO = "fecha";

    /**
     * B167: las ÚNICAS claves por las que el listado se puede ordenar, como lista blanca. Son las
     * seis columnas de la tabla, y pueden ser las seis porque acá el orden es <b>en memoria</b>:
     * el nombre del cliente ya viene en el mapa que la pantalla usa, así que no falta ningún JOIN
     * (en Clientes, con el orden en SQL, solo podían ser cuatro -- ver B157).
     */
    private static final java.util.Set<String> ORDENES_PERMITIDOS = java.util.Collections
            .unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                    "codigo", "fecha", "cliente", "monto", "plazo", "estado")));

    /**
     * B167: el sentido que acompaña al orden por defecto -- descendente, o sea la fecha de la más
     * nueva a la más vieja.
     */
    public static final String SENTIDO_POR_DEFECTO = "desc";

    /** B167: lo que la pantalla puede pedir; cualquier otra cosa cae en el orden por defecto. */
    public static boolean esOrdenPermitido(String clave) {
        return clave != null && ORDENES_PERMITIDOS.contains(clave);
    }

    /**
     * B167: la clave que se va a usar de verdad. El controlador le pasa el parámetro crudo del
     * pedido y no decide nada.
     */
    public static String ordenAplicado(String clavePedida) {
        return esOrdenPermitido(clavePedida) ? clavePedida : ORDEN_POR_DEFECTO;
    }

    /**
     * B167: el sentido que se va a usar, y <b>acá vive toda la regla</b> (la primera versión la
     * tenía partida entre esto y un ternario del controlador, y el JUnit lo agarró: sin parámetros
     * ordenaba ascendente en vez de dejar el orden de siempre).
     *
     * <ul>
     *   <li>Sin columna pedida: el de siempre, {@link #SENTIDO_POR_DEFECTO} -- la fecha de la más
     *       nueva a la más vieja, igual que el {@code ORDER BY} de la consulta.</li>
     *   <li>Con columna pedida y sin sentido: ascendente, que es lo que espera el primer clic en
     *       un encabezado.</li>
     *   <li>Con sentido explícito: ese.</li>
     * </ul>
     */
    public static String sentidoAplicado(String clavePedida, String sentidoPedido) {
        if ("asc".equalsIgnoreCase(sentidoPedido)) {
            return "asc";
        }
        if ("desc".equalsIgnoreCase(sentidoPedido)) {
            return "desc";
        }
        return esOrdenPermitido(clavePedida) ? "asc" : SENTIDO_POR_DEFECTO;
    }

    /**
     * B167: ordena el listado de solicitudes. Puro (sin conexión), así que se prueba con JUnit al
     * lado de {@link #filtrarPorTexto}.
     *
     * <p><b>Se ordena la lista COMPLETA, antes de cortar la página.</b> Ordenar la página ya
     * cortada daría un orden falso -- mostraría "la primera por monto" cuando es la primera de
     * esa página (misma lección que B157 dejó escrita para el orden en SQL).</p>
     *
     * <p><b>Los nulos van al final en los dos sentidos</b> (B177, 14/11/2026): el sentido se
     * aplica adentro de cada comparación, no invirtiendo el resultado -- invirtiéndolo, una fila sin
     * ese dato encabezaba el listado en descendente.</p>
     *
     * <p>Detalles que importan: el nombre del cliente se compara <b>normalizado</b>
     * ({@code NormalizadorTexto.normalizarParaBuscar}, el mismo que usa el buscador), así que
     * "Ávila" ordena junto a "Avila"; el estado se ordena por su <b>etiqueta</b>, que es lo que el
     * usuario lee, no por el orden del enum; y todo orden que no sea por fecha <b>desempata por
     * fecha descendente</b>, para que dos filas con el mismo valor no se intercambien entre dos
     * cargas (lo mismo que el desempate en SQL de B157).</p>
     *
     * @param clave una de {@link #ORDENES_PERMITIDOS}; cualquier otra cosa (o null) usa
     *     {@link #ORDEN_POR_DEFECTO}
     * @param sentido "desc" para descendente; cualquier otra cosa es ascendente
     * @return una lista nueva ordenada; la recibida no se toca
     */
    public List<SolicitudCredito> ordenarParaListado(List<SolicitudCredito> solicitudes,
            java.util.Map<Long, Persona> clientesPorSolicitud, String clave, String sentido) {
        if (solicitudes == null) {
            return new ArrayList<>();
        }
        final String claveUsada = esOrdenPermitido(clave) ? clave : ORDEN_POR_DEFECTO;
        final boolean descendente = "desc".equalsIgnoreCase(sentido);
        final java.util.Map<Long, Persona> clientes = clientesPorSolicitud == null
                ? java.util.Collections.<Long, Persona>emptyMap() : clientesPorSolicitud;
        java.util.Comparator<SolicitudCredito> comparador = new java.util.Comparator<SolicitudCredito>() {
            @Override
            public int compare(SolicitudCredito una, SolicitudCredito otra) {
                // B177 (14/11/2026): el sentido se aplica ADENTRO de cada comparación, no acá
                // afuera. Invirtiendo el resultado completo se invertía también el criterio de
                // "nulos al final", así que en descendente una fila sin ese dato encabezaba el
                // listado -- lo contrario de lo que decía el javadoc de más abajo (la trampa de
                // C20, y el mismo defecto que B175 corrigió en la bandeja de Aprobaciones).
                int resultado = compararPorClave(una, otra, claveUsada, descendente, clientes);
                if (resultado == 0 && !ORDEN_POR_DEFECTO.equals(claveUsada)) {
                    // Desempate estable: la fecha, siempre de la más nueva a la más vieja.
                    resultado = compararPorClave(una, otra, ORDEN_POR_DEFECTO, true, clientes);
                }
                return resultado;
            }
        };
        List<SolicitudCredito> ordenadas = new ArrayList<>(solicitudes);
        java.util.Collections.sort(ordenadas, comparador);
        return ordenadas;
    }

    /** B177: compara por una clave <b>con el sentido ya aplicado</b> -- ver el comentario del
     * comparador: los nulos quedan afuera de la inversión. */
    private int compararPorClave(SolicitudCredito una, SolicitudCredito otra, String clave,
            boolean descendente, java.util.Map<Long, Persona> clientes) {
        switch (clave) {
            case "codigo":
                return compararTextos(una.getCodigoSolicitud(), otra.getCodigoSolicitud(), descendente);
            case "cliente":
                return compararTextos(nombreParaOrdenar(una, clientes), nombreParaOrdenar(otra, clientes),
                        descendente);
            case "monto":
                return comparar(una.getMontoSolicitado(), otra.getMontoSolicitado(), descendente);
            case "plazo":
                return comparar(Integer.valueOf(una.getPlazoSolicitadoMeses()),
                        Integer.valueOf(otra.getPlazoSolicitadoMeses()), descendente);
            case "estado":
                return compararTextos(una.getEstadoSolicitud() == null ? null : una.getEstadoSolicitud().getEtiqueta(),
                        otra.getEstadoSolicitud() == null ? null : otra.getEstadoSolicitud().getEtiqueta(),
                        descendente);
            default:
                return compararFechas(una, otra, descendente);
        }
    }

    /** El cliente, "apellidos nombres" y normalizado, para que las tildes no alteren el orden. */
    private String nombreParaOrdenar(SolicitudCredito solicitud, java.util.Map<Long, Persona> clientes) {
        Persona cliente = clientes.get(solicitud.getIdCliente());
        if (cliente == null) {
            return null;
        }
        return NormalizadorTexto.normalizarParaBuscar(cliente.getApellidos() + " " + cliente.getNombres());
    }

    private int compararFechas(SolicitudCredito una, SolicitudCredito otra, boolean descendente) {
        return comparar(una.getFechaSolicitud(), otra.getFechaSolicitud(), descendente);
    }

    /**
     * Nulos al final, en los dos sentidos: una fila incompleta nunca encabeza el listado.
     *
     * <p><b>B177</b>: por eso recibe el sentido y lo aplica acá adentro, <b>después</b> de resolver
     * los nulos. Hasta el 14/11/2026 el sentido se aplicaba afuera, invirtiendo el resultado
     * completo, y entonces este criterio valía solo en ascendente.</p>
     */
    private <T extends Comparable<T>> int comparar(T una, T otra, boolean descendente) {
        if (una == null && otra == null) {
            return 0;
        }
        if (una == null) {
            return 1;
        }
        if (otra == null) {
            return -1;
        }
        int resultado = una.compareTo(otra);
        return descendente ? -resultado : resultado;
    }

    /** Lo mismo que {@link #comparar}, para texto y sin distinguir mayúsculas. */
    private int compararTextos(String uno, String otro, boolean descendente) {
        if (uno == null && otro == null) {
            return 0;
        }
        if (uno == null) {
            return 1;
        }
        if (otro == null) {
            return -1;
        }
        int resultado = uno.compareToIgnoreCase(otro);
        return descendente ? -resultado : resultado;
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 3): buscador de
     * la bandeja (solicitudes/lista.jsp) -- a diferencia de Mora/Aprobaciones, acá no hizo falta
     * ensanchar ningún DTO: la fila ya es un SolicitudCredito/Persona crudo, con getNombres()/
     * getApellidos()/getCedulaIdentidad() directos (el formato se arma recién en la JSP). Pura
     * (sin conexión, fácil de probar con JUnit). Por N.º de solicitud, cédula (con o sin puntos,
     * ServicioClientes.normalizarCedula) o nombre del cliente, sin acentos y en los dos órdenes
     * (NormalizadorTexto.normalizarParaBuscar, misma normalización que Mora/Aprobaciones). Se
     * aplica DESPUÉS de listarPorOficial, encima del filtro de estado que ya existe (AND, nunca
     * lo reemplaza). */
    public List<SolicitudCredito> filtrarPorTexto(List<SolicitudCredito> solicitudes,
            java.util.Map<Long, Persona> clientesPorSolicitud, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return solicitudes;
        }
        String codigoBuscado = texto.trim().toUpperCase();
        String cedulaBuscada = servicioClientes.normalizarCedula(texto.trim());
        String nombreBuscado = NormalizadorTexto.normalizarParaBuscar(texto);
        List<SolicitudCredito> filtradas = new ArrayList<>();
        for (SolicitudCredito solicitud : solicitudes) {
            Persona cliente = clientesPorSolicitud == null ? null : clientesPorSolicitud.get(solicitud.getIdCliente());
            boolean coincideCodigo = solicitud.getCodigoSolicitud() != null
                    && solicitud.getCodigoSolicitud().toUpperCase().contains(codigoBuscado);
            boolean coincideCedula = !cedulaBuscada.isEmpty() && cliente != null && cliente.getCedulaIdentidad() != null
                    && servicioClientes.normalizarCedula(cliente.getCedulaIdentidad()).contains(cedulaBuscada);
            boolean coincideNombre = !nombreBuscado.isEmpty() && cliente != null && cliente.getNombres() != null
                    && cliente.getApellidos() != null
                    && (NormalizadorTexto.normalizarParaBuscar(cliente.getNombres() + " " + cliente.getApellidos())
                            .contains(nombreBuscado)
                        || NormalizadorTexto.normalizarParaBuscar(cliente.getApellidos() + " " + cliente.getNombres())
                            .contains(nombreBuscado));
            if (coincideCodigo || coincideCedula || coincideNombre) {
                filtradas.add(solicitud);
            }
        }
        return filtradas;
    }

    /** Ajuste 4 (Joel): para armar el enlace "Ver solicitud SOL-XXXX" cuando RN-02 rechaza el alta. */
    public SolicitudCredito buscarAbiertaPorCliente(Connection conexion, long idCliente) throws SQLException {
        return solicitudDAO.buscarAbiertaPorCliente(conexion, idCliente);
    }

    /** Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): para la pestaña
     * "Solicitudes y préstamos" de la ficha del cliente. */
    public List<SolicitudCredito> listarPorCliente(Connection conexion, long idCliente) throws SQLException {
        return solicitudDAO.listarPorCliente(conexion, idCliente);
    }

    /**
     * RF06 (pedido de Joel, 10/10/2026): "quién envió" la solicitud a aprobación, para la franja
     * de la ficha ("Enviada por... Espera la decisión del gerente") -- mismo patrón que
     * ServicioConsultaCrediticia.obtenerUltimaGeneracionAutorizacion (sin columna propia, se
     * resuelve desde la auditoría). null si nunca se envió.
     */
    public Long obtenerIdUsuarioQueEnvio(Connection conexion, long idSolicitud) throws SQLException {
        py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria ultima = auditoriaDAO.obtenerUltima(conexion,
                "ENVIAR_SOLICITUD_APROBACION", "SOLICITUD_CREDITO", idSolicitud);
        return ultima == null ? null : ultima.getIdUsuario();
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 19/22): checklist informativo (✓ / falta) para mostrar en la
     * ficha de la solicitud antes de "Enviar a aprobación" -- edad, zona y situación laboral
     * (política de crédito, Tanda B/D-127), cédula vigente, comprobante de ingresos y comprobante
     * de domicilio cargados (mismos criterios que agregarFaltantesExpediente) y
     * autorización/consulta al buró (mismos criterios que obtenerParaVincularSolicitud, en su
     * variante sin lanzar). Nunca reemplaza la validación real de enviarAAprobacion; es solo para
     * que el oficial vea de antemano qué falta.
     *
     * <p>Son 9 ítems desde el 28/10/2026 (antes 8): la decisión de Joel del 21/10/2026 sumó el
     * comprobante de domicilio. Lo ven el oficial (solicitudes/ficha.jsp) y el gerente
     * (aprobaciones/ficha.jsp).</p>
     */
    public List<ItemChecklistSolicitud> construirChecklistPrevioEnvio(Connection conexion, Persona cliente)
            throws SQLException {
        return construirChecklistPrevioEnvio(conexion, cliente, null);
    }

    /**
     * B122 (10/11/2026, hallazgo de Joel en SOL-2026-0197): misma lista, más el décimo ítem --
     * la capacidad de pago. Sin la solicitud no hay cuota que medir, así que la versión de dos
     * argumentos devuelve los 9 de siempre; las DOS pantallas que lo muestran (la ficha de la
     * solicitud y la bandeja de aprobación) tienen la solicitud a mano y pasan por acá.
     *
     * <p>Por qué hacía falta: {@code enviarAAprobacion} ya rechazaba el envío cuando la
     * afectación calculada con el ingreso ACTUAL supera el límite vigente (RF06, 10/10/2026),
     * pero ese chequeo no estaba en el checklist, así que la pantalla podía decir "Cumple
     * validaciones preliminares" y ofrecer el botón mientras el servidor iba a rechazar. El ítem
     * usa el mismo {@link #calcularAfectacionVigente} que el envío: un solo dueño del número.</p>
     */
    public List<ItemChecklistSolicitud> construirChecklistPrevioEnvio(Connection conexion, Persona cliente,
            SolicitudCredito solicitud) throws SQLException {
        List<ItemChecklistSolicitud> items = new ArrayList<>();
        int edadMinima = servicioClientes.obtenerEdadMinima(conexion);
        int edadMaxima = servicioClientes.obtenerEdadMaxima(conexion);
        int edad = calcularEdad(cliente.getFechaNacimiento(), Reloj.hoy());
        items.add(new ItemChecklistSolicitud("Edad dentro de la política (" + edadMinima + " a " + edadMaxima
                + " años): " + edad + " años", edad >= edadMinima && edad <= edadMaxima));

        List<String> departamentosHabilitados = servicioClientes.valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        // Normalización de personas, grupo Domicilio (16/10/2026): el departamento ya no está en
        // Persona -- se obtiene siempre por JOIN desde la ciudad guardada en personas_domicilio.
        py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioCliente =
                servicioClientes.obtenerDomicilio(conexion, cliente.getIdPersona());
        String nombreDepartamento = domicilioCliente == null ? null
                : servicioClientes.nombreDepartamentoDesdeCiudad(conexion, domicilioCliente.getIdCiudad());
        items.add(new ItemChecklistSolicitud("Zona (departamento) habilitada"
                + (nombreDepartamento == null ? "" : ": " + nombreDepartamento),
                nombreDepartamento != null && departamentosHabilitados.contains(nombreDepartamento)));

        List<String> situacionesAdmitidas = servicioClientes.valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        // Normalización de personas, grupo Laborales (16/10/2026): situación laboral y
        // comprobante de ingreso ya no están en Persona -- se leen de personas_laborales.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona());
        items.add(new ItemChecklistSolicitud("Situación laboral admitida por la política vigente",
                laboralCliente.getSituacionLaboral() != null
                        && situacionesAdmitidas.contains(laboralCliente.getSituacionLaboral())));

        TipoDocumento tipoCedula = tipoDocumentoDAO.obtenerPorCodigo(conexion, "CEDULA_IDENTIDAD");
        boolean cedulaCargada = documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion,
                cliente.getIdPersona(), tipoCedula.getIdTipoDocumento()) != null;
        // RF03 (pedido de Joel), Parte 1: mismo chequeo que agregarFaltantesExpediente -- cargada
        // no alcanza si la cédula real ya venció.
        boolean cedulaVencida = servicioClientes.cedulaVencida(cliente.getFechaVencimientoCedula(), Reloj.hoy());
        items.add(new ItemChecklistSolicitud("Cédula de identidad vigente cargada en el expediente"
                + (cedulaVencida ? " (vencida el " + FormateadorFechas.comoDdMmAaaa(cliente.getFechaVencimientoCedula())
                        + ")" : ""),
                cedulaCargada && !cedulaVencida));

        // RF05/RF06 (pedido de Joel, 18/10/2026): sin comprobante de ingresos el cliente no
        // califica (REGLAS_DESARROLLO.md §5) -- ya no es "no aplica", es un faltante real.
        boolean comprobanteOk;
        String etiquetaComprobante;
        if (laboralCliente.isPresentaComprobanteIngreso()) {
            TipoDocumento tipoComprobante = tipoDocumentoDAO.obtenerPorCodigo(conexion, "COMPROBANTE_INGRESO");
            comprobanteOk = documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, cliente.getIdPersona(),
                    tipoComprobante.getIdTipoDocumento()) != null;
            etiquetaComprobante = "Comprobante de ingresos cargado";
        } else {
            comprobanteOk = false;
            etiquetaComprobante = "Comprobante de ingresos (no presenta: no califica)";
        }
        items.add(new ItemChecklistSolicitud(etiquetaComprobante, comprobanteOk));

        // Decisión de Joel del 21/10/2026, implementada el 28/10/2026: el comprobante de
        // domicilio pasa a ser obligatorio -- mismo criterio que agregarFaltantesExpediente, en
        // el lugar que le corresponde por el orden del expediente (cédula, ingreso, domicilio,
        // autorización). El checklist pasa de 8 a 9 ítems.
        TipoDocumento tipoDomicilio = tipoDocumentoDAO.obtenerPorCodigo(conexion,
                ServicioClientes.CODIGO_COMPROBANTE_DOMICILIO);
        items.add(new ItemChecklistSolicitud("Comprobante de domicilio cargado",
                documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, cliente.getIdPersona(),
                        tipoDomicilio.getIdTipoDocumento()) != null));

        items.add(new ItemChecklistSolicitud("Autorización de consulta al buró vigente",
                servicioConsultaCrediticia.tieneAutorizacionVigente(conexion, cliente.getIdPersona())));
        items.add(new ItemChecklistSolicitud("Consulta al buró (Informconf) registrada con evidencia",
                servicioConsultaCrediticia.tieneConsultaConEvidenciaRegistrada(conexion, cliente.getIdPersona())));
        // RN-18 (pedido de Joel, 17/10/2026): "no se presta a quien figura con deudas en
        // Informconf" -- mismo criterio que el bloqueo real de enviarAAprobacion, para que el
        // oficial vea el bloqueo en pantalla antes de intentar enviar.
        ConsultaCrediticia consultaMasRecienteCliente = servicioConsultaCrediticia.obtenerMasRecienteConEvidencia(
                conexion, cliente.getIdPersona());
        items.add(new ItemChecklistSolicitud("Sin deudas registradas en Informconf",
                consultaMasRecienteCliente == null || !Boolean.TRUE.equals(consultaMasRecienteCliente.getIndicadorMora())));

        // B122: el décimo ítem -- la capacidad de pago, con el ingreso y las deudas de HOY, que
        // es exactamente lo que enviarAAprobacion va a revalidar. La etiqueta trae los dos
        // números para que el oficial vea POR QUÉ no cumple sin salir del cuadro.
        if (solicitud != null && solicitud.getCuotaSimulada() != null) {
            AfectacionVigente afectacion = calcularAfectacionVigente(conexion,
                    solicitud.getCuotaSimulada(), laboralCliente);
            items.add(new ItemChecklistSolicitud("Cuota dentro de la capacidad de pago (afectación "
                    + FormateadorNumeros.comoPorcentajeUnDecimal(afectacion.getPorcentaje())
                    + ", límite " + FormateadorNumeros.comoPorcentaje(afectacion.getLimite()) + ")",
                    !afectacion.isSupera()));
        }
        return items;
    }

    /**
     * B122 (10/11/2026): la afectación de HOY y el límite vigente, los dos juntos porque siempre
     * se usan juntos. Único dueño del número: lo usan {@code enviarAAprobacion} (que acepta o
     * rechaza el envío), el décimo ítem del checklist y la ficha de la solicitud en BORRADOR.
     * Nada de esto es nuevo: es el cálculo que ya hacía el envío, extraído para que la pantalla
     * no pueda mostrar otro.
     */
    public AfectacionVigente calcularAfectacionVigente(Connection conexion, BigDecimal cuotaSimulada,
            py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboral) throws SQLException {
        BigDecimal limite = servicioParametros.obtenerValorDecimal(conexion, "credito.limite_afectacion");
        BigDecimal cuotaOtrasDeudas = sumarCuotasOtrasDeudas(laboral.getCuotaOtrasDeudasMensual());
        ResultadoCapacidadPago capacidad = servicioMotorFinanciero.calcularCapacidadPago(cuotaSimulada,
                laboral.getIngresoNetoComprobado(), cuotaOtrasDeudas);
        return new AfectacionVigente(capacidad.getPorcentajeAfectacion(), limite);
    }

    /** B122: afectación calculada con los datos de hoy, con el límite contra el que se la mide. */
    public static final class AfectacionVigente {
        private final BigDecimal porcentaje;
        private final BigDecimal limite;

        public AfectacionVigente(BigDecimal porcentaje, BigDecimal limite) {
            this.porcentaje = porcentaje;
            this.limite = limite;
        }

        public BigDecimal getPorcentaje() {
            return porcentaje;
        }

        public BigDecimal getLimite() {
            return limite;
        }

        public boolean isSupera() {
            return porcentaje.compareTo(limite) > 0;
        }
    }

    /**
     * Ajuste 1 (Joel): el total a pagar mostrado en pantalla tiene que ser la suma EXACTA del
     * plan (el IVA sobre intereses baja en cada cuota, así que "cuota más alta × plazo" queda
     * inflado) -- se recalcula con las mismas condiciones ya guardadas (monto/plazo/fecha/TEA
     * simulada), nunca con el parámetro credito.tea_empresa actual (que pudo cambiar desde que
     * se simuló). Solo lectura, no persiste nada.
     */
    public ResultadoCotizacion recalcularPlan(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        return servicioMotorFinanciero.cotizar(solicitud.getMontoSolicitado(), solicitud.getPlazoSolicitadoMeses(),
                solicitud.getPrimeraFechaVencimientoSolicitada(), solicitud.getTasaTeaSimulada(),
                feriadoDAO.listarFechasActivas(conexion));
    }

    // ------------------------------------------------------------------
    // Orquestación
    // ------------------------------------------------------------------

    /**
     * PO-03 paso 1-2: crea la solicitud en BORRADOR con la primera simulación ya incluida (las
     * columnas *_simulado son NOT NULL en la base). RN-01/RN-02 (ajuste B): también acá, no solo
     * al enviar a aprobación.
     */
    /**
     * Bloque 7/E10, Tanda B (D-127): resuelve los parámetros de política vigentes y el nombre del
     * departamento del cliente, y delega la comparación en evaluarPoliticaCredito (pura).
     */
    private void validarPoliticaCredito(Connection conexion, Persona cliente,
            py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente) throws SQLException {
        int edadMinima = servicioClientes.obtenerEdadMinima(conexion);
        int edadMaxima = servicioClientes.obtenerEdadMaxima(conexion);
        List<String> departamentosHabilitados = servicioClientes.valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        List<String> situacionesAdmitidas = servicioClientes.valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        // Normalización de personas, grupo Domicilio (16/10/2026): departamento por JOIN desde la
        // ciudad (ya no existe cliente.getIdDepartamento()).
        py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioCliente =
                servicioClientes.obtenerDomicilio(conexion, cliente.getIdPersona());
        String nombreDepartamento = domicilioCliente == null ? null
                : servicioClientes.nombreDepartamentoDesdeCiudad(conexion, domicilioCliente.getIdCiudad());
        int edad = calcularEdad(cliente.getFechaNacimiento(), Reloj.hoy());
        evaluarPoliticaCredito(edad, edadMinima, edadMaxima, nombreDepartamento, departamentosHabilitados,
                laboralCliente.getSituacionLaboral(), situacionesAdmitidas);
        // RF05/RF06 (pedido de Joel, 18/10/2026): hueco preexistente destapado al quitar el
        // codeudor -- se bloquea acá, mismo método puro que ENVIAR (este mismo llamador, desde
        // crear) y FORMALIZAR (ServicioFormalizacion), un solo texto en los tres momentos.
        String motivoComprobante = servicioClientes.motivoSinComprobanteSolicitudSiAplica(
                laboralCliente.isPresentaComprobanteIngreso());
        if (motivoComprobante != null) {
            throw new NegocioException(motivoComprobante);
        }
    }

    public long crear(long idCliente, BigDecimal monto, int plazoMeses, String destino,
            LocalDate primeraFechaVencimiento, long idUsuarioCreador) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // RN-01/RN-02 (D-68): una sola búsqueda compartida con RN-28, ver buscarCompromisoAbierto.
                CompromisoAbierto compromiso = buscarCompromisoAbierto(conexion, idCliente);
                if (compromiso != null) {
                    throw new NegocioException(mensajeCompromisoAbierto(compromiso));
                }
                Persona cliente = servicioClientes.buscarPorId(conexion, idCliente);
                if (cliente == null) {
                    throw new NegocioException("El cliente no existe.");
                }
                // Bloque 7/E10 (D-129, Obs. 31): un cliente dado de baja no puede iniciar
                // solicitudes nuevas (su historial sigue visible, no se le oculta ni se borra nada).
                if (cliente.getEstadoPersona() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoPersona.INACTIVA) {
                    throw new NegocioException("El cliente está dado de baja. Reactívelo antes de crear una solicitud.");
                }
                // Normalización de personas, grupo Laborales (16/10/2026): ingreso y cuota de
                // otras deudas (datos de capacidad de pago) ya no están en Persona -- único
                // método que los lee, ServicioClientes.obtenerLaboral (1 de los 4 lugares que
                // lo usan: crear/simular/enviarAAprobacion de este archivo + la revalidación
                // RF11 de ServicioFormalizacion). Se resuelve una sola vez, antes de
                // validarPoliticaCredito (que también la necesita), para no leerla dos veces.
                py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                        servicioClientes.obtenerLaboral(conexion, idCliente);
                validarPoliticaCredito(conexion, cliente, laboralCliente);

                SolicitudCredito solicitud = new SolicitudCredito();
                solicitud.setIdCliente(idCliente);
                solicitud.setCreadoPor(idUsuarioCreador);
                aplicarSimulacion(conexion, solicitud, monto, plazoMeses, destino, primeraFechaVencimiento,
                        laboralCliente.getIngresoNetoComprobado(), laboralCliente.getCuotaOtrasDeudasMensual());
                solicitud.setCodigoSolicitud(generarCodigo(conexion));

                long idNuevo = solicitudDAO.insertar(conexion, solicitud);
                servicioAuditoria.registrar(conexion, idUsuarioCreador, "CREAR_SOLICITUD", "SOLICITUD_CREDITO",
                        idNuevo, "Creó la solicitud " + solicitud.getCodigoSolicitud() + " para el cliente id "
                                + idCliente + ".");
                conexion.commit();
                return idNuevo;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * D-63 (ajuste A): la única acción que puede cambiar monto/plazo/destino/fecha -- guarda y
     * recalcula siempre juntos, así la simulación guardada nunca queda desactualizada. No audita
     * (es solo un recálculo, igual que la cotización de P13).
     */
    public void simular(long idSolicitud, BigDecimal monto, int plazoMeses, String destino,
            LocalDate primeraFechaVencimiento) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarEstadoBorrador(solicitud);
                Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
                // Normalización de personas, grupo Laborales (16/10/2026): ver el comentario de
                // crear() -- mismo método único, 2.º de los 4 lugares.
                py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                        servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona());
                aplicarSimulacion(conexion, solicitud, monto, plazoMeses, destino, primeraFechaVencimiento,
                        laboralCliente.getIngresoNetoComprobado(), laboralCliente.getCuotaOtrasDeudasMensual());
                solicitudDAO.actualizarSimulacion(conexion, solicitud);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** PO-03 paso 5: checklist completo de D-63 (ajuste A) antes de pasar a PENDIENTE_APROBACION. */
    public void enviarAAprobacion(long idSolicitud, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarEstadoBorrador(solicitud);

                if (solicitudDAO.existeClienteConPrestamoAbierto(conexion, solicitud.getIdCliente())) {
                    throw new NegocioException("El cliente ya tiene un préstamo no cerrado. "
                            + "No se puede enviar a aprobación.");
                }

                Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
                // RF06 (pedido de Joel, 10/10/2026): el cliente activo solo se validaba al crear
                // la solicitud -- si se lo da de baja mientras la solicitud sigue en borrador, el
                // envío ahora también lo rechaza. Bloqueo directo (no hay nada que "completar").
                if (cliente.getEstadoPersona() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoPersona.INACTIVA) {
                    throw new NegocioException("El cliente está dado de baja. Reactívelo antes de enviar la "
                            + "solicitud a aprobación.");
                }
                // Normalización de personas, grupo Laborales (16/10/2026): ingreso/cuota de
                // otras deudas/comprobante de ingreso ya no están en Persona -- único método que
                // los lee, ServicioClientes.obtenerLaboral (3.º de los 4 lugares: crear/simular/
                // enviarAAprobacion de este archivo + la revalidación RF11 de
                // ServicioFormalizacion). Se resuelve una sola vez acá, se reutiliza para todo lo
                // que sigue en este método.
                py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                        servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona());

                // Ajuste 2 (Joel): se juntan TODOS los faltantes en un solo mensaje con viñetas,
                // en vez de cortar en el primero -- el oficial ve de una vez todo lo que falta.
                List<String> faltantes = new ArrayList<>();
                agregarFaltantesExpediente(conexion, cliente, "cliente", faltantes);

                // RF06 (pedido de Joel, 10/10/2026): la política de crédito (edad/zona/situación
                // laboral, comprobante de ingresos) solo se validaba al crear -- también puede
                // haber cambiado desde entonces (el cliente, o el parámetro vigente). Se agrega a
                // la lista de faltantes, igual que el resto.
                try {
                    validarPoliticaCredito(conexion, cliente, laboralCliente);
                } catch (NegocioException error) {
                    faltantes.add(error.getMessage());
                }

                // RF06 (pedido de Joel, 10/10/2026): la afectación se recalcula con el ingreso y
                // las deudas ACTUALES del cliente (mismo motor, misma cuota ya simulada --
                // monto/plazo no cambian acá) -- antes se comparaba contra la foto de la última
                // simulación, que podía quedar vieja si el oficial no volvía a simular después de
                // un cambio en la ficha del cliente. Si el envío se aprueba, el nuevo snapshot se
                // guarda más abajo para que quede coherente con lo que de verdad se usó para
                // decidir.
                // B122 (10/11/2026): el cálculo se mudó a calcularAfectacionVigente, sin cambiar
                // ni un número -- lo comparten este rechazo, el décimo ítem del checklist y la
                // ficha en BORRADOR, para que la pantalla no pueda decir algo distinto de lo que
                // el servidor va a hacer.
                AfectacionVigente afectacionActual = calcularAfectacionVigente(conexion,
                        solicitud.getCuotaSimulada(), laboralCliente);
                BigDecimal limiteAfectacionActual = afectacionActual.getLimite();
                ResultadoCapacidadPago capacidadActual =
                        new ResultadoCapacidadPago(afectacionActual.getPorcentaje());

                boolean superaLimiteAfectacion = afectacionActual.isSupera();
                if (superaLimiteAfectacion) {
                    faltantes.add("La cuota supera la capacidad de pago del cliente. Reduzca el monto o amplíe "
                            + "el plazo.");
                }

                // RF06 (pedido de Joel, 10/10/2026): los dos requisitos del buró (autorización
                // vigente, consulta con evidencia registrada) se revisan por separado, cada uno
                // con su propio texto para ESTE momento (enviar a aprobación) -- antes se
                // delegaba todo en obtenerParaVincularSolicitud, que tira una sola excepción en
                // el primer problema que encuentra (con un texto pensado para la pantalla de
                // "Consulta al buró", no para el envío) y nunca llegaba a decir que también
                // faltaba el otro. Mismos dos chequeos que ya usa construirChecklistPrevioEnvio (✓/falta
                // en pantalla), ahora también acá.
                ConsultaCrediticia consultaCliente = agregarFaltantesBuro(conexion, solicitud.getIdCliente(),
                        "cliente", faltantes);

                // RN-18 (pedido de Joel, 17/10/2026): "no se presta a quien figura con deudas en
                // Informconf" -- bloqueo directo (no es un "faltante": no hay nada que completar
                // para subsanarlo, hay que rechazar la solicitud), revisado antes de la lista de
                // faltantes para que el oficial vea este motivo aunque también falte algo más.
                if (consultaCliente != null && Boolean.TRUE.equals(consultaCliente.getIndicadorMora())) {
                    throw new NegocioException(MENSAJE_MORA_INFORMCONF);
                }

                if (!faltantes.isEmpty()) {
                    StringBuilder mensaje = new StringBuilder("Faltan completar estos puntos antes de enviar a aprobación:");
                    for (String item : faltantes) {
                        mensaje.append("\n- ").append(item);
                    }
                    throw new NegocioException(mensaje.toString());
                }

                // RF06 (pedido de Joel, 10/10/2026): se guarda el snapshot recalculado con los
                // datos actuales (ver más arriba) -- para que la ficha, el resumen del gerente
                // (RF08) y la revalidación al aprobar (RF09) vean el mismo número que de verdad
                // se usó para decidir el envío, no el de la simulación vieja.
                solicitud.setIngresoClienteEvaluado(laboralCliente.getIngresoNetoComprobado());
                solicitud.setLimiteAfectacionAplicado(limiteAfectacionActual);
                solicitud.setPorcentajeAfectacion(capacidadActual.getPorcentajeAfectacion());
                solicitudDAO.actualizarSimulacion(conexion, solicitud);

                solicitudDAO.marcarEnviada(conexion, idSolicitud);
                servicioConsultaCrediticia.vincularASolicitud(conexion, consultaCliente.getIdConsultaCrediticia(),
                        idSolicitud);

                servicioAuditoria.registrar(conexion, idUsuario, "ENVIAR_SOLICITUD_APROBACION", "SOLICITUD_CREDITO",
                        idSolicitud, "Envió la solicitud " + solicitud.getCodigoSolicitud() + " a aprobación.");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * P04: solo el oficial puede cancelar, y solo mientras está en BORRADOR (P05 cubre
     * PENDIENTE_APROBACION/APROBADA). RF06 (pedido de Joel, 10/10/2026): motivo de la lista fija
     * + detalle (obligatorio solo en "Otro"), igual que ServicioAprobacionCredito.cancelar.
     */
    public void cancelar(long idSolicitud, String codigoMotivo, String detalle, long idUsuario) throws SQLException {
        detalle = NormalizadorTexto.normalizarTextoLibre(detalle);
        String motivo = construirMotivoCancelacion(codigoMotivo, detalle);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                validarEstadoBorrador(solicitud);
                solicitudDAO.marcarCancelada(conexion, idSolicitud, idUsuario, motivo);
                servicioAuditoria.registrar(conexion, idUsuario, "CANCELAR_SOLICITUD", "SOLICITUD_CREDITO",
                        idSolicitud, "Canceló la solicitud en borrador. Motivo: " + motivo);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    // ------------------------------------------------------------------
    // Privados
    // ------------------------------------------------------------------

    /**
     * D-63 (ajuste A): cédula vigente siempre; comprobante de ingreso vigente solo si la ficha
     * dice que lo presenta (RF-08/RN-04a). Ajuste 2 (Joel): agrega a la lista en vez de cortar
     * en el primer faltante, para juntar todo en un solo mensaje.
     *
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): los 3 códigos de
     * acá (más AUTORIZACION_CONSULTA_BURO, exigido aparte por agregarFaltantesBuro) son,
     * literalmente, los que ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS marca como
     * obligatorios en la pestaña de Documentos -- referenciados por el nombre constante, no por
     * un literal repetido, para que no puedan desincronizarse.
     *
     * <p>28/10/2026 (decisión de Joel del 21/10/2026): se agrega el comprobante de domicilio.
     * A diferencia del comprobante de ingreso, no depende de ninguna condición de la ficha: se
     * exige siempre. Ver ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS.</p>
     */
    private void agregarFaltantesExpediente(Connection conexion, Persona persona, String etiqueta,
            List<String> faltantes) throws SQLException {
        TipoDocumento tipoCedula = tipoDocumentoDAO.obtenerPorCodigo(conexion, ServicioClientes.CODIGO_CEDULA_IDENTIDAD);
        if (documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, persona.getIdPersona(),
                tipoCedula.getIdTipoDocumento()) == null) {
            faltantes.add("Falta cargar la cédula de identidad vigente del " + etiqueta + " en su expediente.");
        } else if (servicioClientes.cedulaVencida(persona.getFechaVencimientoCedula(), Reloj.hoy())) {
            // RF03 (pedido de Joel), Parte 1: bloqueo real del lado servidor -- la pantalla ya
            // muestra la fila 2 en rojo, pero "Enviar a aprobación" no dependía de esto hasta
            // ahora (solo miraba si había un documento VIGENTE cargado, sin ver la fecha real).
            faltantes.add("La cédula de identidad del " + etiqueta + " venció el "
                    + FormateadorFechas.comoDdMmAaaa(persona.getFechaVencimientoCedula())
                    + ". Debe renovarla antes de solicitar un crédito.");
        }
        // Normalización de personas, grupo Laborales (16/10/2026): presenta comprobante de
        // ingreso ya no está en Persona -- se lee de personas_laborales.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboral =
                servicioClientes.obtenerLaboral(conexion, persona.getIdPersona());
        if (laboral.isPresentaComprobanteIngreso()) {
            TipoDocumento tipoComprobante = tipoDocumentoDAO.obtenerPorCodigo(conexion,
                    ServicioClientes.CODIGO_COMPROBANTE_INGRESO);
            if (documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, persona.getIdPersona(),
                    tipoComprobante.getIdTipoDocumento()) == null) {
                faltantes.add("El " + etiqueta + " indica que presenta comprobante de ingresos, pero no hay "
                        + "ninguno cargado en su expediente.");
            }
        }
        // Decisión de Joel del 21/10/2026, implementada el 28/10/2026: el comprobante de
        // domicilio pasa a ser obligatorio -- se exige siempre, sin condición previa.
        TipoDocumento tipoDomicilio = tipoDocumentoDAO.obtenerPorCodigo(conexion,
                ServicioClientes.CODIGO_COMPROBANTE_DOMICILIO);
        if (documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, persona.getIdPersona(),
                tipoDomicilio.getIdTipoDocumento()) == null) {
            faltantes.add("Falta cargar el comprobante de domicilio del " + etiqueta + " en su expediente.");
        }
    }

    /**
     * RF06 (pedido de Joel, 10/10/2026): los dos requisitos del buró, cada uno con su propio
     * texto para el envío a aprobación -- mismos dos chequeos (sin lanzar) que ya usa
     * construirChecklistPrevioEnvio para el checklist en pantalla. Devuelve la consulta a
     * vincular si los dos se cumplen, o null si falta alguno (y lo agrega a la lista).
     *
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): este método es el
     * que hace obligatoria la autorización de consulta al buró (ServicioClientes.
     * CODIGO_AUTORIZACION_CONSULTA_BURO, en CODIGOS_EXPEDIENTE_OBLIGATORIOS) -- sin literal propio
     * que reemplazar acá porque ServicioConsultaCrediticia ya resuelve el tipo de documento por su
     * cuenta, no recibe un código.
     */
    private ConsultaCrediticia agregarFaltantesBuro(Connection conexion, long idPersona, String etiqueta,
            List<String> faltantes) throws SQLException {
        boolean autorizacionVigente = servicioConsultaCrediticia.tieneAutorizacionVigente(conexion, idPersona);
        if (!autorizacionVigente) {
            faltantes.add("Falta la autorización de consulta al buró vigente del " + etiqueta + ".");
        }
        boolean consultaRegistrada = servicioConsultaCrediticia.tieneConsultaConEvidenciaRegistrada(conexion, idPersona);
        if (!consultaRegistrada) {
            faltantes.add("Falta registrar la consulta al buró del " + etiqueta + ".");
        }
        if (!autorizacionVigente || !consultaRegistrada) {
            return null;
        }
        return servicioConsultaCrediticia.obtenerParaVincularSolicitud(conexion, idPersona, etiqueta);
    }

    /**
     * RN-05 (límites), RN-15 (tope de tasa) y RN-03 (capacidad, "cuota más alta con IVA" RF-12):
     * simula el plan con ServicioMotorFinanciero (E1, ya probado) y deja el resultado en
     * `solicitud`. No persiste (eso lo hace el llamador).
     */
    private void aplicarSimulacion(Connection conexion, SolicitudCredito solicitud, BigDecimal monto, int plazoMeses,
            String destino, LocalDate primeraFechaVencimiento, BigDecimal ingresoCliente,
            BigDecimal cuotaOtrasDeudasCliente) throws SQLException {
        // Corrección de Joel (revisión de reportes E5): recortar espacios y colapsar dobles antes
        // de guardar, para que un dato como "Gastos de internación ." no arrastre el espacio de
        // más hasta el contrato.
        destino = NormalizadorTexto.normalizarTextoLibre(destino);
        if (destino == null || destino.isEmpty()) {
            throw new NegocioException("Ingrese el destino del crédito.");
        }
        BigDecimal montoMinimo = servicioParametros.obtenerValorDecimal(conexion, "credito.monto_minimo");
        BigDecimal montoMaximo = servicioParametros.obtenerValorDecimal(conexion, "credito.monto_maximo");
        servicioCotizacion.validarMontoDentroDeLimites(monto, montoMinimo, montoMaximo);
        int plazoMinimo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_minimo");
        int plazoMaximo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_maximo");
        servicioCotizacion.validarPlazoDentroDeLimites(plazoMeses, plazoMinimo, plazoMaximo);
        // RF05 (pedido de Joel, 10/10/2026): mismo rango de días que el cotizador (RF01-O) y la
        // formalización (RF11), con el mismo mensaje -- ya no alcanza con que la fecha no sea
        // pasada.
        int diasMinimoVencimiento = servicioParametros.obtenerValorEntero(conexion,
                "formalizacion.dias_min_primer_vencimiento");
        int diasMaximoVencimiento = servicioParametros.obtenerValorEntero(conexion,
                "formalizacion.dias_max_primer_vencimiento");
        servicioCotizacion.validarRangoFechaPrimerVencimiento(primeraFechaVencimiento, Reloj.hoy(),
                diasMinimoVencimiento, diasMaximoVencimiento);

        BigDecimal teaEmpresa = servicioParametros.obtenerValorDecimal(conexion, "credito.tea_empresa");
        LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
        servicioParametros.resolverTopeLegalVigente(limiteVigente);
        ResultadoLimiteTasa resultadoLimite = servicioMotorFinanciero.validarLimiteTasa(teaEmpresa,
                limiteVigente.getTasaMaximaTea());
        if (!resultadoLimite.isOk()) {
            throw new NegocioException("La TEA de la empresa (" + FormateadorNumeros.comoPorcentaje(teaEmpresa)
                    + ") supera el tope legal vigente (" + FormateadorNumeros.comoPorcentaje(limiteVigente.getTasaMaximaTea())
                    + ").");
        }

        ResultadoCotizacion cotizacion = servicioMotorFinanciero.cotizar(monto, plazoMeses, primeraFechaVencimiento,
                teaEmpresa, feriadoDAO.listarFechasActivas(conexion));
        BigDecimal cuotaMaxima = cuotaMasAlta(cotizacion);

        BigDecimal limiteAfectacion = servicioParametros.obtenerValorDecimal(conexion, "credito.limite_afectacion");
        // D-127 (Tanda B, Opción A elegida por Joel): la cuota de otras deudas del cliente se
        // suma a la cuota nueva antes de medir la afectación.
        BigDecimal cuotaOtrasDeudasTotal = sumarCuotasOtrasDeudas(cuotaOtrasDeudasCliente);
        ResultadoCapacidadPago capacidad = servicioMotorFinanciero.calcularCapacidadPago(cuotaMaxima, ingresoCliente,
                cuotaOtrasDeudasTotal);

        // RF05 (pedido de Joel, 10/10/2026): el 40 % ya se rechaza acá (crear/simular), sin
        // excepción, no solo al enviar a aprobación -- ver validarLimiteAfectacion.
        validarLimiteAfectacion(capacidad.getPorcentajeAfectacion(), limiteAfectacion);

        solicitud.setMontoSolicitado(monto);
        solicitud.setPlazoSolicitadoMeses(plazoMeses);
        solicitud.setDestinoCredito(destino);
        solicitud.setPrimeraFechaVencimientoSolicitada(primeraFechaVencimiento);
        solicitud.setTasaTnaPropuesta(cotizacion.getTna());
        solicitud.setTasaTeaSimulada(cotizacion.getTea());
        solicitud.setCftSimulado(cotizacion.getCft());
        solicitud.setCuotaSimulada(cuotaMaxima);
        solicitud.setIngresoClienteEvaluado(ingresoCliente);
        solicitud.setLimiteAfectacionAplicado(limiteAfectacion);
        solicitud.setPorcentajeAfectacion(capacidad.getPorcentajeAfectacion());
    }

    /** RN-03: "cuota más alta con IVA" del plan. Compartida por aplicarSimulacion y previsualizarAfectacion.
     * Delegada a ServicioMotorFinanciero (RF11, 11/10/2026): RF05/RF06 y RF11 usan la misma fórmula. */
    private BigDecimal cuotaMasAlta(ResultadoCotizacion cotizacion) {
        return servicioMotorFinanciero.cuotaMasAlta(cotizacion);
    }

    /** Resultado de previsualizarAfectacion: la cuota estimada junto con la capacidad de pago, para
     * que el indicador en vivo muestre las dos cosas (pedido de Joel, 10/10/2026). */
    public static final class PrevisualizacionAfectacion {
        private final BigDecimal cuotaEstimada;
        private final ResultadoCapacidadPago capacidad;

        public PrevisualizacionAfectacion(BigDecimal cuotaEstimada, ResultadoCapacidadPago capacidad) {
            this.cuotaEstimada = cuotaEstimada;
            this.capacidad = capacidad;
        }

        public BigDecimal getCuotaEstimada() {
            return cuotaEstimada;
        }

        public ResultadoCapacidadPago getCapacidad() {
            return capacidad;
        }
    }

    /**
     * RF05 (pedido de Joel, 10/10/2026): afectación en vivo mientras el oficial todavía está
     * escribiendo el monto/plazo, antes de guardar nada -- mismo motor y mismo parámetro
     * (credito.limite_afectacion) que aplicarSimulacion, pero de solo lectura: no valida límites
     * de monto/plazo/tasa ni persiste ni audita nada (para no interrumpir mientras escribe).
     * Devuelve null si todavía no hay datos suficientes para cotizar (el llamador decide qué
     * mostrar, igual que ServicioClientes.evaluarPrecalificacion).
     */
    public PrevisualizacionAfectacion previsualizarAfectacion(Connection conexion, Persona cliente,
            BigDecimal monto, int plazoMeses, LocalDate primeraFechaVencimiento) throws SQLException {
        if (cliente == null || monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || plazoMeses <= 0
                || plazoMeses > 600 || primeraFechaVencimiento == null) {
            return null;
        }
        BigDecimal teaEmpresa = servicioParametros.obtenerValorDecimal(conexion, "credito.tea_empresa");
        ResultadoCotizacion cotizacion = servicioMotorFinanciero.cotizar(monto, plazoMeses, primeraFechaVencimiento,
                teaEmpresa, feriadoDAO.listarFechasActivas(conexion));
        BigDecimal cuotaMaxima = cuotaMasAlta(cotizacion);
        // Normalización de personas, grupo Laborales (16/10/2026): ingreso/cuota de otras deudas
        // ya no están en Persona -- se leen de personas_laborales.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona());
        BigDecimal cuotaOtrasDeudasTotal = sumarCuotasOtrasDeudas(laboralCliente.getCuotaOtrasDeudasMensual());
        ResultadoCapacidadPago capacidad = servicioMotorFinanciero.calcularCapacidadPago(cuotaMaxima,
                laboralCliente.getIngresoNetoComprobado(), cuotaOtrasDeudasTotal);
        return new PrevisualizacionAfectacion(cuotaMaxima, capacidad);
    }

    private String generarCodigo(Connection conexion) throws SQLException {
        int anio = Reloj.hoy().getYear();
        long secuencia = numeradorDAO.siguienteValor(conexion, "SOLICITUD", anio);
        return String.format("SOL-%d-%04d", anio, secuencia);
    }
}
