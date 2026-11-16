package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.GestionCobranzaDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;
import py.com.capitalcredito.sgcpc.excepciones.MontoPrometidoFaltanteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.excepciones.ResultadoGestionFaltanteException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.GestionCobranza;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;

/**
 * P14, seguimiento de mora (Doc. 00 §12 / RF-37/RF-38, Bloque 4 de E8). "Días de atraso" y
 * "cuotas vencidas" usan la MISMA regla que ya decide EN_MORA (ServicioCobranza.
 * calcularEstadoSegunCuotas: fecha_vencimiento efectiva < hoy, cuota no PAGADA) -- nunca una
 * comparación de fechas nueva en SQL (corrección de Joel). El monto exigible sí usa
 * ServicioCobranza.calcularDeudaExigible, que respeta los días de gracia igual que el motor.
 */
public class ServicioMora {

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final GestionCobranzaDAO gestionCobranzaDAO = new GestionCobranzaDAO();
    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();

    private static final String OPERACION_RECALCULO_DIARIO = "RECALCULO_MORA_DIARIO";

    /**
     * Decisión 5 (Bloque 4, E8): recalcula estado_prestamo de todos los ACTIVO/EN_MORA -- PO-15
     * paso 1 ("el sistema actualiza estados del día"). Nunca toca FINALIZADO/PENDIENTE_DESEMBOLSO/
     * ANULADO (ni siquiera los mira: listarActivosOEnMora ya los excluye). Una vez por día alcanza:
     * sin tabla nueva, se usa la propia auditoría como marca de "ya corrió hoy" (operación
     * RECALCULO_MORA_DIARIO, tipo_entidad SISTEMA, id_entidad 0). La llaman /panel, /mora y los
     * reportes de Morosidad/Cartera/Incobrables -- lo que se abra primero en el día.
     */
    public void recalcularMorosidadDiaria(Connection conexion, long idUsuario) throws SQLException {
        if (servicioAuditoria.yaSeRegistroHoy(conexion, OPERACION_RECALCULO_DIARIO)) {
            return;
        }
        LocalDate hoy = Reloj.hoy();
        int cambiados = 0;
        for (PrestamoCredito prestamo : prestamoDAO.listarActivosOEnMora(conexion)) {
            EstadoPrestamo estadoAntes = prestamo.getEstadoPrestamo();
            servicioCobranza.recalcularEstadoPrestamo(conexion, prestamo.getIdPrestamo(), hoy);
            PrestamoCredito actualizado = prestamoDAO.obtenerPorId(conexion, prestamo.getIdPrestamo());
            if (actualizado.getEstadoPrestamo() != estadoAntes) {
                cambiados++;
                servicioAuditoria.registrar(conexion, idUsuario, "RECALCULAR_ESTADO_MORA", "PRESTAMO",
                        prestamo.getIdPrestamo(), detalleCambioDeEstado(prestamo.getIdentificadorOperacion(),
                                estadoAntes, actualizado.getEstadoPrestamo()));
            }
        }
        servicioAuditoria.registrar(conexion, idUsuario, OPERACION_RECALCULO_DIARIO, "SISTEMA", 0,
                detalleRecalculoDiario(cambiados));
    }

    /**
     * B121 (11/11/2026): el detalle que el gerente va a leer meses después, cuando ya nadie se
     * acuerde del préstamo 116. Antes decía solo <i>"Activo -&gt; En mora"</i>: sin el préstamo,
     * con flecha ASCII y sin punto final — la ÚNICA de las ~100 llamadas a
     * {@code servicioAuditoria.registrar} del sistema que se salía de la convención que ya usaba
     * {@code ServicioParametros} (nombra la entidad, flecha tipográfica, punto final). El id de
     * entidad de la fila no cambia: sigue siendo el del préstamo. Pura, para poder probarla sin
     * base (mismo criterio que C19).
     */
    static String detalleCambioDeEstado(String identificadorOperacion, EstadoPrestamo antes,
            EstadoPrestamo despues) {
        return "Cambió el estado del préstamo " + identificadorOperacion + ": "
                + antes.getEtiqueta() + " → " + despues.getEtiqueta() + ".";
    }

    /**
     * B121: el resumen del recálculo diario. Antes decía <i>"N préstamo(s) cambiaron de estado."</i>
     * — el mismo "(s)" que B132 sacó de la pantalla de Clientes, una línea más abajo en este mismo
     * método. Pura.
     */
    static String detalleRecalculoDiario(int cambiados) {
        return cambiados == 1 ? "1 préstamo cambió de estado."
                : cambiados + " préstamos cambiaron de estado.";
    }

    /** P14: listado completo, orden -- promesa vencida primero, después por días de atraso descendente. */
    public List<FilaMora> listarPrestamosEnMora(Connection conexion) throws SQLException {
        List<FilaMora> filas = construirFilas(conexion, prestamoDAO.listarEnMora(conexion));
        filas.sort(COMPARADOR_PRIORIDAD);
        return filas;
    }

    // ======================================================================
    // B197 (15/11/2026): orden de la bandeja de /mora. Mismo mecanismo que B157/B167/B175/B183,
    // con una particularidad: el orden DE SIEMPRE de esta pantalla no es una columna, es una
    // prioridad de trabajo -- "promesa vencida primero, después por días de atraso descendente",
    // que es lo que promete el subtítulo. Esa sigue siendo la clave por defecto ("prioridad") y
    // es también el desempate de cualquier otro orden.
    // Todo lo de acá es PURO: se prueba con JUnit sin base.
    // ======================================================================

    /** B197: el orden de siempre, como comparador reutilizable (lo usa el listado y el desempate). */
    private static final java.util.Comparator<FilaMora> COMPARADOR_PRIORIDAD = (a, b) -> {
        if (a.isPromesaVencida() != b.isPromesaVencida()) {
            return a.isPromesaVencida() ? -1 : 1;
        }
        return Integer.compare(b.getDiasAtraso(), a.getDiasAtraso());
    };

    /**
     * B197: las únicas claves por las que se puede ordenar la bandeja de mora.
     *
     * <p><b>"Promesa", "Teléfono" y "Última gestión" no entran a propósito</b>: las tres llegan a la
     * pantalla como texto ya formateado (una fecha "dd/mm/aaaa" ordena mal como cadena, y el
     * teléfono y la gestión no son criterios de trabajo), y la regla de B157 es que un encabezado
     * que parece clickeable tiene que poder hacer lo que promete.</p>
     */
    private static final java.util.Set<String> ORDENES_MORA = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                    "prioridad", "operacion", "cliente", "dias", "cuotas", "exigible")));

    /** B197: la clave de orden de siempre -- la prioridad de trabajo que promete el subtítulo. */
    public static final String ORDEN_MORA_POR_DEFECTO = "prioridad";

    /** B197: si esa clave se puede usar. Lo desconocido --y lo tecleado a mano en la URL-- cae en
     * el orden de siempre y nunca llega a una comparación. */
    public static boolean esOrdenPermitido(String clave) {
        return clave != null && ORDENES_MORA.contains(clave);
    }

    /** B197: el orden que se va a usar de verdad. */
    public static String ordenAplicado(String clavePedida) {
        return esOrdenPermitido(clavePedida) ? clavePedida : ORDEN_MORA_POR_DEFECTO;
    }

    /**
     * B197: el sentido que se va a usar, y acá vive toda la regla (la lección de B167).
     *
     * <p>Con sentido explícito, ese. Sin él: <b>descendente</b> para los tres criterios donde
     * "más" es "más urgente" (días de atraso, cuotas vencidas y monto exigible), y
     * <b>ascendente</b> para los alfabéticos (operación y cliente), que es lo que espera el primer
     * clic. La prioridad no tiene sentido: es un orden compuesto y fijo.</p>
     */
    public static String sentidoAplicado(String clavePedida, String sentidoPedido) {
        if ("asc".equalsIgnoreCase(sentidoPedido)) {
            return "asc";
        }
        if ("desc".equalsIgnoreCase(sentidoPedido)) {
            return "desc";
        }
        String clave = ordenAplicado(clavePedida);
        return "dias".equals(clave) || "cuotas".equals(clave) || "exigible".equals(clave) ? "desc" : "asc";
    }

    /**
     * B197: ordena la bandeja de mora. Pura.
     *
     * <p>Los nulos van <b>al final en los dos sentidos</b> (B177) y todo orden que no sea el de
     * siempre <b>desempata por la prioridad</b>, para que dos filas con el mismo valor no se
     * intercambien entre dos cargas -- con páginas, eso se ve como una fila repetida o una que
     * desaparece (B157).</p>
     */
    public List<FilaMora> ordenarParaBandeja(List<FilaMora> filas, String clave, String sentido) {
        if (filas == null) {
            return new ArrayList<>();
        }
        final String claveUsada = ordenAplicado(clave);
        final boolean descendente = "desc".equalsIgnoreCase(sentidoAplicado(clave, sentido));
        List<FilaMora> ordenadas = new ArrayList<>(filas);
        if (ORDEN_MORA_POR_DEFECTO.equals(claveUsada)) {
            ordenadas.sort(COMPARADOR_PRIORIDAD);
            return ordenadas;
        }
        ordenadas.sort((a, b) -> {
            int resultado = compararPorValor(valorDeOrden(a, claveUsada), valorDeOrden(b, claveUsada), descendente);
            return resultado != 0 ? resultado : COMPARADOR_PRIORIDAD.compare(a, b);
        });
        return ordenadas;
    }

    /** B197: el sentido se aplica adentro y los nulos quedan AFUERA de la inversión -- una fila a
     * la que le falta ese dato va al final en los dos sentidos (B177). */
    private int compararPorValor(Comparable<?> valorUno, Comparable<?> valorOtro, boolean descendente) {
        if (valorUno == null || valorOtro == null) {
            if (valorUno == null && valorOtro == null) {
                return 0;
            }
            return valorUno == null ? 1 : -1;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        int resultado = ((Comparable) valorUno).compareTo(valorOtro);
        return descendente ? -resultado : resultado;
    }

    /** B197: el valor por el que se ordena, crudo -- nunca el texto ya formateado de la fila. El
     * cliente se compara por apellidos y nombres normalizados, el mismo criterio del buscador y de
     * B167/B175/B183, así que "Ávila" ordena junto a "Avila". */
    private Comparable<?> valorDeOrden(FilaMora fila, String clave) {
        switch (clave) {
            case "operacion":
                return fila.getIdentificadorOperacion() == null ? null
                        : py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto
                                .normalizarParaBuscar(fila.getIdentificadorOperacion());
            case "cliente":
                return py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(
                        (fila.getClienteApellidos() == null ? "" : fila.getClienteApellidos()) + " "
                                + (fila.getClienteNombres() == null ? "" : fila.getClienteNombres()));
            case "dias":
                return Integer.valueOf(fila.getDiasAtraso());
            case "cuotas":
                return Integer.valueOf(fila.getCuotasVencidas());
            case "exigible":
                return fila.getMontoExigibleCrudo();
            default:
                return null;
        }
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): mismo
     * listado, con el filtro de texto del buscador de /mora -- sobre la lista ya construida (el
     * listado de mora siempre es chico, no amerita llevar el filtro a la consulta SQL). */
    public List<FilaMora> listarPrestamosEnMora(Connection conexion, String texto) throws SQLException {
        return filtrarPorTexto(listarPrestamosEnMora(conexion), texto);
    }

    /** Pura, visibilidad de paquete para poder probarla con JUnit sin conexión. Por número de
     * operación, cédula (con o sin puntos, mismo criterio que ServicioClientes.normalizarCedula)
     * o nombre del cliente. Corrección de Joel (20/10/2026): el nombre ahora compara sin acentos
     * y en los dos órdenes ("nombre apellido" y "apellido nombre", como ya se ve la columna
     * "Cliente") -- misma normalización que los 7 informes (NormalizadorTexto.normalizarParaBuscar),
     * reutilizada acá en vez de inventar una nueva (REGLAS_DESARROLLO.md §13); PersonaDAO hace lo
     * mismo pero en SQL (paso 0), este es el equivalente en Java porque el nombre ya viene armado
     * en memoria, no por consulta. */
    List<FilaMora> filtrarPorTexto(List<FilaMora> filas, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return filas;
        }
        String textoMayusculas = texto.trim().toUpperCase();
        String cedulaBuscada = servicioClientes.normalizarCedula(texto.trim());
        String nombreBuscado = NormalizadorTexto.normalizarParaBuscar(texto);
        List<FilaMora> filtradas = new ArrayList<>();
        for (FilaMora fila : filas) {
            boolean coincideOperacion = fila.getIdentificadorOperacion() != null
                    && fila.getIdentificadorOperacion().toUpperCase().contains(textoMayusculas);
            boolean coincideCedula = !cedulaBuscada.isEmpty() && fila.getClienteCedula() != null
                    && servicioClientes.normalizarCedula(fila.getClienteCedula()).contains(cedulaBuscada);
            boolean coincideNombre = !nombreBuscado.isEmpty() && fila.getClienteNombres() != null
                    && fila.getClienteApellidos() != null
                    && (NormalizadorTexto.normalizarParaBuscar(fila.getClienteNombres() + " " + fila.getClienteApellidos())
                            .contains(nombreBuscado)
                        || NormalizadorTexto.normalizarParaBuscar(fila.getClienteApellidos() + " " + fila.getClienteNombres())
                            .contains(nombreBuscado));
            if (coincideOperacion || coincideCedula || coincideNombre) {
                filtradas.add(fila);
            }
        }
        return filtradas;
    }

    /** Reporte Incobrables (§13): EN_MORA con más de credito.dias_incobrable días de atraso. */
    public List<FilaMora> listarIncobrables(Connection conexion) throws SQLException {
        int diasIncobrable = servicioParametros.obtenerValorEntero(conexion, "credito.dias_incobrable");
        List<FilaMora> filas = new ArrayList<>();
        for (FilaMora fila : listarPrestamosEnMora(conexion)) {
            if (fila.getDiasAtraso() > diasIncobrable) {
                filas.add(fila);
            }
        }
        return filas;
    }

    private List<FilaMora> construirFilas(Connection conexion, List<PrestamoCredito> prestamos) throws SQLException {
        LocalDate hoy = Reloj.hoy();
        int diasIncobrable = servicioParametros.obtenerValorEntero(conexion, "credito.dias_incobrable");
        List<FilaMora> filas = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamos) {
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            Prestamo prestamoMotor = servicioCobranza.cargarPrestamoParaMotor(conexion, prestamo.getIdPrestamo());

            int diasAtraso = 0;
            int cuotasVencidas = 0;
            Integer numeroCuotaMasAntigua = null;
            LocalDate fechaVencimientoMasAntigua = null;
            for (Cuota cuota : prestamoMotor.getCuotas()) {
                if (cuota.getEstadoPago() != EstadoPago.PAGADA && cuota.getFechaVencimiento().isBefore(hoy)) {
                    cuotasVencidas++;
                    int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), hoy);
                    if (dias > diasAtraso) {
                        diasAtraso = dias;
                        // D-146 (lista de trabajo de /cobranza, pedido de Joel): la cuota más
                        // antigua sin pagar es la que manda en "Cuota (N/M)"/"Vencimiento" de esa
                        // fila -- misma que ya determina diasAtraso, no una consulta nueva.
                        numeroCuotaMasAntigua = cuota.getNumeroCuota();
                        fechaVencimientoMasAntigua = cuota.getFechaVencimiento();
                    }
                }
            }
            int totalCuotas = prestamoMotor.getCuotas().size();

            ResultadoDeudaExigible exigible = servicioCobranza.calcularDeudaExigible(conexion,
                    prestamo.getIdPrestamo(), hoy);
            // Bloque C, tanda 1 (pedido de Joel): capital vencido (solo cuotas ya vencidas, parte
            // capital) y saldo (capital pendiente de TODO el préstamo) -- para los subtotales por
            // tramo del informe de Morosidad. saldo usa el mismo cálculo que el listado de
            // Préstamos/Estado de cuenta (sumarSaldoCapital, package-private, mismo paquete
            // "servicios"), nunca una copia.
            BigDecimal capitalVencido = BigDecimal.ZERO;
            for (py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible detalle : exigible.getDetalles()) {
                capitalVencido = capitalVencido.add(detalle.getCapitalPendiente());
            }
            BigDecimal saldo = ServicioReportes.sumarSaldoCapital(prestamoMotor.getCuotas());

            List<GestionCobranza> abiertas = gestionCobranzaDAO.listarAbiertasPorPrestamo(conexion,
                    prestamo.getIdPrestamo());
            GestionCobranza promesaAbierta = abiertas.isEmpty() ? null : abiertas.get(0);
            List<GestionCobranza> historial = gestionCobranzaDAO.listarPorPrestamo(conexion, prestamo.getIdPrestamo());
            GestionCobranza ultimaGestion = historial.isEmpty() ? null : historial.get(0);

            filas.add(new FilaMora(prestamo.getIdPrestamo(), prestamo.getIdentificadorOperacion(),
                    cliente.getNombres() + " " + cliente.getApellidos(), cliente.getNombres(), cliente.getApellidos(),
                    cliente.getTelefonoWhatsapp(),
                    FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                    diasAtraso, cuotasVencidas, numeroCuotaMasAntigua, totalCuotas,
                    fechaVencimientoMasAntigua == null ? null : FormateadorFechas.comoDdMmAaaa(fechaVencimientoMasAntigua),
                    FormateadorNumeros.comoGuaranies(exigible.getTotalGeneral()),
                    exigible.getTotalGeneral(),
                    promesaAbierta != null, promesaAbierta == null ? null : FormateadorFechas.comoDdMmAaaa(promesaAbierta.getFechaPromesa()),
                    promesaAbierta == null ? null : FormateadorNumeros.comoGuaranies(promesaAbierta.getMontoPrometido()),
                    promesaAbierta != null && promesaAbierta.getFechaPromesa().isBefore(hoy),
                    ultimaGestion == null ? null : ultimaGestion.getTipo().getEtiqueta(),
                    ultimaGestion == null ? null : FormateadorFechas.comoDdMmAaaaHhMm(ultimaGestion.getFechaHora()),
                    // Punto 1 de la mejora chica de RF24 (pedido de Joel): el resultado de la
                    // última gestión, para no obligar a abrir el estado de cuenta solo para verlo.
                    ultimaGestion == null ? null : ultimaGestion.getResultado(),
                    diasAtraso > diasIncobrable, capitalVencido, saldo));
        }
        return filas;
    }

    /** Historial completo de gestiones de un préstamo, más reciente primero (ficha de mora /
     * Estado de cuenta del cliente). */
    public List<GestionCobranza> listarGestionesDelPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        return gestionCobranzaDAO.listarPorPrestamo(conexion, idPrestamo);
    }

    /**
     * P14 paso 2: registra una gestión manual (llamada, mensaje o visita) -- nunca PAGO_PARCIAL,
     * eso lo genera solo confirmarCobro (D-95). Transacción propia y corta, como el resto de las
     * operaciones que no son de dinero.
     */
    public void registrarGestion(long idPrestamo, long idPersonaContactada, TipoGestionCobranza tipo,
            String resultado, String excusa, LocalDate fechaPromesa, BigDecimal montoPrometido, long idUsuario)
            throws SQLException {
        if (tipo == TipoGestionCobranza.PAGO_PARCIAL) {
            throw new NegocioException("Ese tipo de gestión se genera solo al cobrar.");
        }
        // RF24 §10 (Tanda 2, 14/10/2026, pedido de Joel): "Resultado" obligatorio -- mismo
        // patrón que el monto prometido de abajo (dato faltante, no error del servidor).
        if (resultado == null || resultado.trim().isEmpty()) {
            throw new ResultadoGestionFaltanteException("Ingrese el resultado de la gestión.");
        }
        if (fechaPromesa != null && montoPrometido == null) {
            // RF24 (pedido de Joel, 12/10/2026): falta un dato, no un error del servidor --
            // ControladorMoraServlet la distingue para mostrarla como advertencia (mismo
            // patrón que MotivoDiferenciaInsuficienteException, RF19).
            throw new MontoPrometidoFaltanteException("Si hay fecha de promesa, ingrese el monto prometido.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                GestionCobranza gestion = new GestionCobranza();
                gestion.setIdPrestamo(idPrestamo);
                gestion.setIdPersonaContactada(idPersonaContactada);
                gestion.setTipo(tipo);
                gestion.setResultado(resultado);
                gestion.setExcusa(excusa);
                gestion.setFechaPromesa(fechaPromesa);
                gestion.setMontoPrometido(montoPrometido);
                gestion.setRegistradoPor(idUsuario);
                long idGestion = gestionCobranzaDAO.insertar(conexion, gestion);
                servicioAuditoria.registrar(conexion, idUsuario, "REGISTRAR_GESTION_COBRANZA", "GESTION_COBRANZA",
                        idGestion, "Gestión " + tipo.getEtiqueta() + " sobre " + prestamo.getIdentificadorOperacion() + ".");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }
}
