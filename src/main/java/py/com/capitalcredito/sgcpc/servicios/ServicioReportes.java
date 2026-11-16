package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.AuditoriaDAO;
import py.com.capitalcredito.sgcpc.dao.CiudadDAO;
import py.com.capitalcredito.sgcpc.dao.ConsultaCrediticiaDAO;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.ParametroDAO;
import py.com.capitalcredito.sgcpc.dao.DesembolsoDAO;
import py.com.capitalcredito.sgcpc.dao.FacturaDAO;
import py.com.capitalcredito.sgcpc.dao.GestionCobranzaDAO;
import py.com.capitalcredito.sgcpc.dao.LiquidacionAnticipadaDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaDAO;
import py.com.capitalcredito.sgcpc.dao.PlanPagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.ReversionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.SesionCajaDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dto.FilaAuditoria;
import py.com.capitalcredito.sgcpc.dto.FilaCartera;
import py.com.capitalcredito.sgcpc.dto.FilaCuotaPendiente;
import py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza;
import py.com.capitalcredito.sgcpc.dto.FilaInformePrestamo;
import py.com.capitalcredito.sgcpc.dto.FilaIva;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.FiltroPrestamos;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo;
import py.com.capitalcredito.sgcpc.dto.ResumenFinanciero;
import py.com.capitalcredito.sgcpc.dto.ResumenMensual;
import py.com.capitalcredito.sgcpc.dto.SubtotalTramo;
import py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad;
import py.com.capitalcredito.sgcpc.dto.TotalesCaja;
import py.com.capitalcredito.sgcpc.dto.TotalesCartera;
import py.com.capitalcredito.sgcpc.dto.TotalesEstadoCuenta;
import py.com.capitalcredito.sgcpc.dto.TotalesGestiones;
import py.com.capitalcredito.sgcpc.dto.TotalesIncobrables;
import py.com.capitalcredito.sgcpc.dto.TotalesIva;
import py.com.capitalcredito.sgcpc.dto.TotalesPrestamos;
import py.com.capitalcredito.sgcpc.dto.TotalesVencimientos;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.GestionCobranza;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PlanPago;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;

/**
 * P11, reportes (Doc. 00 §13, Bloque 4 de E8). Nunca recalcula nada financiero: reusa siempre lo
 * que ya calculan ServicioCobranza/ServicioMora/ServicioCaja (REGLAS_DESARROLLO.md §5).
 */
public class ServicioReportes {

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final PlanPagoDAO planPagoDAO = new PlanPagoDAO();
    private final CuotaDAO cuotaDAO = new CuotaDAO();
    private final DesembolsoDAO desembolsoDAO = new DesembolsoDAO();
    private final FacturaDAO facturaDAO = new FacturaDAO();
    private final AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();
    private final SesionCajaDAO sesionCajaDAO = new SesionCajaDAO();
    private final GestionCobranzaDAO gestionCobranzaDAO = new GestionCobranzaDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final ReversionPagoDAO reversionPagoDAO = new ReversionPagoDAO();
    private final LiquidacionAnticipadaDAO liquidacionAnticipadaDAO = new LiquidacionAnticipadaDAO();
    private final PersonaDAO personaDAO = new PersonaDAO();
    private final CiudadDAO ciudadDAO = new CiudadDAO();
    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final ConsultaCrediticiaDAO consultaCrediticiaDAO = new ConsultaCrediticiaDAO();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();

    /**
     * 13/10/2026 (pedido de Joel): filtro "Cliente u operación" (nombre, C.I. o código de
     * operación, ej. CC-aaaa-nnnnn/SOL-aaaa-nnnn) reutilizable en los 7 informes que lo
     * necesitan (Caja, Aprobaciones, Cobros, Desembolsos, Solicitudes, Gestiones, Consultas al
     * buró) -- pura, sin Connection (REGLAS_DESARROLLO.md §7 punto 9), un solo lugar en vez de un
     * filtro SQL repetido 7 veces. Sin texto, coincide siempre (filtro vacío = todos). Compara
     * sin tildes ni mayúsculas (NormalizadorTexto.normalizarParaBuscar), "contiene", no exacto.
     */
    public boolean coincideClienteUOperacion(String textoFiltro, String identificadorOperacion,
            String clienteCedula, String clienteNombre) {
        if (textoFiltro == null || textoFiltro.trim().isEmpty()) {
            return true;
        }
        String buscado = py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(textoFiltro);
        return py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(identificadorOperacion)
                        .contains(buscado)
                || py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(clienteCedula)
                        .contains(buscado)
                || py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(clienteNombre)
                        .contains(buscado);
    }

    /** Vencimientos (§13): cuotas de préstamos ACTIVO/EN_MORA que vencen en el rango, sin
     * recalcular nada -- solo lee fecha_vencimiento de las cuotas ya generadas. */
    /**
     * B210, segunda vuelta (15/11/2026, defecto que Joel encontró al probar): el orden del reporte
     * de Vencimientos — por fecha de vencimiento, de la más próxima a la más lejana.
     *
     * <p><b>Lo que estaba mal</b>: se ordenaba por {@code getFechaVencimiento()}, que es el texto
     * {@code "dd/MM/yyyy"}. Comparado como String, eso ordena por <b>día, después mes, después
     * año</b>: con el rango 15/11 a 31/12 la página 1 arrancaba en {@code "01/12/2026"} y las
     * cuotas del 15/11 —las que vencen HOY— caían varias páginas más adelante. <b>El defecto era
     * preexistente</b>: con las 205 filas en una sola pantalla el desorden era una molestia; al
     * paginar en B210 pasó a esconder justo lo que hay que cobrar.</p>
     *
     * <p><b>Público y estático a propósito</b>: así su JUnit lo prueba sin base, con dos filas
     * armadas a mano cuyo orden por texto es el inverso del orden por fecha. Mismo criterio que
     * {@code ServicioMora.COMPARADOR_PRIORIDAD} (B197).</p>
     *
     * <p>Y <b>desempata</b> por operación y número de cuota: varias cuotas del mismo día, sin
     * desempate, pueden alternar entre páginas en dos cargas distintas (la lección de B205 con
     * {@code fecha_hora}).</p>
     */
    public static final java.util.Comparator<FilaVencimiento> COMPARADOR_VENCIMIENTOS =
            new java.util.Comparator<FilaVencimiento>() {
                @Override
                public int compare(FilaVencimiento a, FilaVencimiento b) {
                    int porFecha = a.getFechaExacta().compareTo(b.getFechaExacta());
                    if (porFecha != 0) {
                        return porFecha;
                    }
                    int porOperacion = a.getIdentificadorOperacion().compareTo(b.getIdentificadorOperacion());
                    return porOperacion != 0 ? porOperacion
                            : Integer.compare(a.getNumeroCuota(), b.getNumeroCuota());
                }
            };

    public List<FilaVencimiento> listarVencimientos(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        List<FilaVencimiento> filas = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarActivosOEnMora(conexion)) {
            PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            if (plan == null) {
                continue;
            }
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            List<Cuota> cuotasDelPlan = cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago());
            int totalCuotas = cuotasDelPlan.size();
            for (Cuota cuota : cuotasDelPlan) {
                if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                    continue;
                }
                if (!cuota.getFechaVencimiento().isBefore(desde) && !cuota.getFechaVencimiento().isAfter(hasta)) {
                    filas.add(new FilaVencimiento(prestamo.getIdPrestamo(), prestamo.getIdentificadorOperacion(),
                            cliente.getNombres() + " " + cliente.getApellidos(), cliente.getTelefonoWhatsapp(),
                            FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                            cuota.getNumeroCuota(), totalCuotas, FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()),
                            // B210, segunda vuelta: la fecha sin formatear viaja al lado de la
                            // formateada, para que el orden sea por fecha y no por texto.
                            cuota.getFechaVencimiento(),
                            FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()),
                            // B99 (30/10/2026): el mismo monto sin formatear, para que el panel
                            // pueda sumar el total del dia sin repetir la consulta.
                            cuota.getTotalProgramado(),
                            cuota.getEstadoPago() == EstadoPago.PARCIAL ? "Parcial" : "Pendiente"));
                }
            }
        }
        filas.sort(COMPARADOR_VENCIMIENTOS);
        return filas;
    }

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 1 (pedido de Joel): totales al pie de
     * Vencimientos -- mismo criterio que listarVencimientos (misma ventana, mismas cuotas), sin
     * volver a formatear cada monto. Desglosa capital/interés/IVA (Cuota.tributosProgramados, el
     * IVA sobre el interés programado) además del total, pedido explícito de Joel. */
    public TotalesVencimientos calcularTotalesVencimientos(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        List<Cuota> cuotasEnRango = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarActivosOEnMora(conexion)) {
            PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            if (plan == null) {
                continue;
            }
            for (Cuota cuota : cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago())) {
                if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                    continue;
                }
                if (!cuota.getFechaVencimiento().isBefore(desde) && !cuota.getFechaVencimiento().isAfter(hasta)) {
                    cuotasEnRango.add(cuota);
                }
            }
        }
        return sumarTotalesVencimientos(cuotasEnRango);
    }

    /** Extraído de calcularTotalesVencimientos (pedido de Joel, Bloque C tanda 1) para poder
     * probarlo sin base -- pura, sobre cuotas ya filtradas por el rango de fechas. */
    static TotalesVencimientos sumarTotalesVencimientos(List<Cuota> cuotasEnRango) {
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal interes = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Cuota cuota : cuotasEnRango) {
            capital = capital.add(cuota.getCapitalProgramado());
            interes = interes.add(cuota.getInteresProgramado());
            iva = iva.add(cuota.getTributosProgramados());
            total = total.add(cuota.getTotalProgramado());
        }
        return new TotalesVencimientos(cuotasEnRango.size(), FormateadorNumeros.comoGuaranies(capital),
                FormateadorNumeros.comoGuaranies(interes), FormateadorNumeros.comoGuaranies(iva),
                FormateadorNumeros.comoGuaranies(total));
    }

    /** Cartera (§13): préstamos ACTIVO/EN_MORA, capital pendiente ("saldo", pedido de Joel) e
     * interés futuro programado. Bloque C, tanda 1: el saldo ahora sale de sumarSaldoCapital (el
     * mismo método que usan el listado de Préstamos y Estado de cuenta), no de una suma duplicada
     * -- así el total de Cartera queda matemáticamente igual al saldo de /prestamos (misma cuenta,
     * no solo la misma fórmula). tieneDeuda(estado) queda como guarda explícita (pedido de Joel):
     * hoy es siempre true acá, porque prestamoDAO.listarActivosOEnMora ya solo trae ACTIVO/EN_MORA,
     * pero si ese filtro cambiara el día de mañana, esta fila nunca entraría sin deuda real. */
    public List<FilaCartera> listarCartera(Connection conexion) throws SQLException {
        List<FilaCartera> filas = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarActivosOEnMora(conexion)) {
            if (!tieneDeuda(prestamo.getEstadoPrestamo())) {
                continue;
            }
            PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            if (plan == null) {
                continue;
            }
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            BigDecimal capitalPendiente = sumarSaldoCapital(cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago()));
            BigDecimal interesFuturo = cuotaDAO.sumarInteresFuturoPendiente(conexion, plan.getIdPlanPago());
            filas.add(new FilaCartera(prestamo.getIdentificadorOperacion(),
                    cliente.getNombres() + " " + cliente.getApellidos(),
                    FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()),
                    FormateadorNumeros.comoGuaranies(capitalPendiente), capitalPendiente,
                    FormateadorNumeros.comoGuaranies(interesFuturo), interesFuturo,
                    prestamo.getEstadoPrestamo().getEtiqueta()));
        }
        return filas;
    }

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 1 (pedido de Joel): totales al pie de
     * Cartera -- pura, sobre las filas ya armadas por listarCartera (antes volvía a consultar la
     * base por separado, con riesgo de diverger de lo que se ve en pantalla; mismo criterio que
     * calcularTotalesPrestamos). */
    public TotalesCartera calcularTotalesCartera(List<FilaCartera> filas) {
        BigDecimal capitalPendienteTotal = BigDecimal.ZERO;
        BigDecimal interesFuturoTotal = BigDecimal.ZERO;
        for (FilaCartera fila : filas) {
            capitalPendienteTotal = capitalPendienteTotal.add(fila.getCapitalPendienteCrudo());
            interesFuturoTotal = interesFuturoTotal.add(fila.getInteresFuturoCrudo());
        }
        return new TotalesCartera(filas.size(), FormateadorNumeros.comoGuaranies(capitalPendienteTotal),
                FormateadorNumeros.comoGuaranies(interesFuturoTotal));
    }

    /** Bloque C, tanda 1 (pedido de Joel): resumen "al día" (Activo) / "en mora" -- cantidad y
     * saldo por estado, mismo criterio que ResumenEstadoPrestamo (solo los estados que de verdad
     * aparecen en las filas, en el orden del ciclo de vida del préstamo). */
    public List<ResumenEstadoCartera> calcularResumenPorEstadoCartera(List<FilaCartera> filas) {
        java.util.Map<String, Integer> cantidadPorEstado = new LinkedHashMap<>();
        java.util.Map<String, BigDecimal> saldoPorEstado = new LinkedHashMap<>();
        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            cantidadPorEstado.put(estado.getEtiqueta(), 0);
            saldoPorEstado.put(estado.getEtiqueta(), BigDecimal.ZERO);
        }
        for (FilaCartera fila : filas) {
            cantidadPorEstado.merge(fila.getEstado(), 1, Integer::sum);
            saldoPorEstado.merge(fila.getEstado(), fila.getCapitalPendienteCrudo(), BigDecimal::add);
        }
        List<ResumenEstadoCartera> resumen = new ArrayList<>();
        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            String etiqueta = estado.getEtiqueta();
            int cantidad = cantidadPorEstado.get(etiqueta);
            if (cantidad > 0) {
                resumen.add(new ResumenEstadoCartera(etiqueta, cantidad,
                        FormateadorNumeros.comoGuaranies(saldoPorEstado.get(etiqueta))));
            }
        }
        return resumen;
    }

    /** Resumen mensual (§13): un mes calendario (1.º al último día), con la composición de lo
     * cobrado (pedido de Joel: "intereses cobrados"), igual que el arqueo pero para todo el mes. */
    public ResumenMensual calcularResumenMensual(Connection conexion, LocalDate primerDiaDelMes) throws SQLException {
        LocalDate desde = primerDiaDelMes.withDayOfMonth(1);
        LocalDate hasta = desde.withDayOfMonth(desde.lengthOfMonth());

        List<py.com.capitalcredito.sgcpc.modelos.Desembolso> desembolsos =
                desembolsoDAO.listarConfirmadosPorRango(conexion, desde, hasta);
        BigDecimal montoOtorgado = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.modelos.Desembolso desembolso : desembolsos) {
            montoOtorgado = montoOtorgado.add(desembolso.getMontoNetoEntregado());
        }

        BigDecimal[] composicion = aplicacionPagoDAO.sumarPorRangoFechas(conexion, desde, hasta);
        BigDecimal capital = composicion[0];
        BigDecimal interes = composicion[1];
        BigDecimal moratorio = composicion[2];
        BigDecimal punitorio = composicion[3];
        BigDecimal iva = composicion[4];
        BigDecimal total = capital.add(interes).add(moratorio).add(punitorio).add(iva);
        BigDecimal ganancia = interes.add(moratorio).add(punitorio);

        int prestamosEnMora = prestamoDAO.listarEnMora(conexion).size();
        int prestamosActivosOMora = prestamoDAO.listarActivosOEnMora(conexion).size();
        BigDecimal capitalCartera = cuotaDAO.sumarCapitalPendienteActivos(conexion);
        BigDecimal capitalVencido = BigDecimal.ZERO;
        // 13/10/2026 (hallazgo de Joel): "capitalVencido" de abajo es solo lo VENCIDO -- esta
        // suma aparte es el saldo TOTAL (vencido + no vencido) de los préstamos EN_MORA, la otra
        // mirada que pidió agregar (ver ResumenMensual.getSaldoPrestamosEnMoraPorcentaje).
        BigDecimal capitalEnMora = BigDecimal.ZERO;
        for (PrestamoCredito prestamo : prestamoDAO.listarEnMora(conexion)) {
            for (py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible detalle : servicioCobranza
                    .calcularDeudaExigible(conexion, prestamo.getIdPrestamo(), Reloj.hoy()).getDetalles()) {
                capitalVencido = capitalVencido.add(detalle.getCapitalPendiente());
            }
            capitalEnMora = capitalEnMora.add(calcularSaldoCapital(conexion, prestamo.getIdPrestamo(),
                    prestamo.getEstadoPrestamo()));
        }

        ResumenMensual resumen = new ResumenMensual();
        resumen.setPrestamosOtorgadosCantidad(desembolsos.size());
        resumen.setPrestamosOtorgadosMonto(FormateadorNumeros.comoGuaranies(montoOtorgado));
        resumen.setCobradoCapital(FormateadorNumeros.comoGuaranies(capital));
        resumen.setCobradoInteres(FormateadorNumeros.comoGuaranies(interes));
        resumen.setCobradoMoratorio(FormateadorNumeros.comoGuaranies(moratorio));
        resumen.setCobradoPunitorio(FormateadorNumeros.comoGuaranies(punitorio));
        resumen.setCobradoIva(FormateadorNumeros.comoGuaranies(iva));
        resumen.setCobradoTotal(FormateadorNumeros.comoGuaranies(total));
        resumen.setGananciaDelMes(FormateadorNumeros.comoGuaranies(ganancia));
        resumen.setMorosidadPorcentajeCantidad(prestamosActivosOMora == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(BigDecimal.valueOf(prestamosEnMora)
                        .divide(BigDecimal.valueOf(prestamosActivosOMora), 4, java.math.RoundingMode.HALF_UP)));
        resumen.setMorosidadPorcentajeMonto(capitalCartera.compareTo(BigDecimal.ZERO) == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(capitalVencido.divide(capitalCartera, 4, java.math.RoundingMode.HALF_UP)));
        resumen.setSaldoPrestamosEnMoraPorcentaje(capitalCartera.compareTo(BigDecimal.ZERO) == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(capitalEnMora.divide(capitalCartera, 4, java.math.RoundingMode.HALF_UP)));
        return resumen;
    }

    /**
     * B5 (Bloque 7/E10, pedido de Joel): informe de Caja -- misma fila para pantalla, Informe PDF
     * y CSV (mismo criterio que B1-B4), reusando SesionCajaDAO.listarCerradasEntreFechas (ya
     * construido en el Bloque 2 para el filtro de /caja) y ServicioCaja.formatearDiferenciaArqueo
     * (A3d) para el "Sobra/Falta" -- nunca una copia de esa cuenta.
     */
    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> listarInformeCaja(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas = new ArrayList<>();
        for (SesionCaja sesion : sesionCajaDAO.listarCerradasEntreFechas(conexion, filtro.getDesde(), filtro.getHasta())) {
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeCaja(sesion.getIdSesionCaja(),
                    obtenerNombreCajero(conexion, sesion.getIdCajero()),
                    FormateadorFechas.comoDdMmAaaaHhMm(sesion.getFechaHoraApertura()),
                    sesion.getFechaHoraCierre() == null ? "—" : FormateadorFechas.comoDdMmAaaaHhMm(sesion.getFechaHoraCierre()),
                    sesion.getSaldoInicial(), FormateadorNumeros.comoGuaranies(sesion.getSaldoInicial()),
                    sesion.getSaldoTeoricoCierre(),
                    sesion.getSaldoTeoricoCierre() == null ? "—" : FormateadorNumeros.comoGuaranies(sesion.getSaldoTeoricoCierre()),
                    sesion.getSaldoFisico(),
                    sesion.getSaldoFisico() == null ? "—" : FormateadorNumeros.comoGuaranies(sesion.getSaldoFisico()),
                    sesion.getDiferenciaArqueo(),
                    sesion.getDiferenciaArqueo() == null ? "—" : ServicioCaja.formatearDiferenciaArqueo(sesion.getDiferenciaArqueo()),
                    sesion.getIdGerenteRevisor() != null));
        }
        return filas;
    }

    /** Totales (<tfoot>) del informe de Caja -- pura, sobre las filas ya armadas arriba. Sobrantes
     * y faltantes se suman POR SEPARADO (pedido explícito de Joel): nunca se netean entre sí. */
    public TotalesCaja calcularTotalesCaja(List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas) {
        BigDecimal sobrantes = BigDecimal.ZERO;
        BigDecimal faltantes = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeCaja fila : filas) {
            if (fila.getDiferencia() == null) {
                continue;
            }
            int signo = fila.getDiferencia().signum();
            if (signo > 0) {
                sobrantes = sobrantes.add(fila.getDiferencia());
            } else if (signo < 0) {
                faltantes = faltantes.add(fila.getDiferencia().abs());
            }
        }
        return new TotalesCaja(filas.size(), FormateadorNumeros.comoGuaranies(sobrantes),
                FormateadorNumeros.comoGuaranies(faltantes));
    }

    public String obtenerNombreCajero(Connection conexion, long idUsuario) throws SQLException {
        py.com.capitalcredito.sgcpc.modelos.Usuario usuario = servicioUsuarios.buscarPorId(conexion, idUsuario);
        return usuario == null ? "-" : usuario.getNombres() + " " + usuario.getApellidos();
    }

    /** IVA del mes (§13): facturas EMITIDAS y ANULADAS del mes, sin recalcular ningún IVA
     * (REGLAS_DESARROLLO.md §5). Bloque C, tanda 2 (pedido de Joel): antes solo traía las EMITIDAS -- ahora
     * también lista las ANULADAS (marcadas con isAnulada(), ver FilaIva) para que la numeración
     * completa del mes quede visible, aunque calcularTotalesIva las salte. Mezcladas y ordenadas
     * por número de factura (mismo orden que las dos consultas por separado). */
    public List<FilaIva> listarIva(Connection conexion, LocalDate primerDiaDelMes) throws SQLException {
        LocalDate desde = primerDiaDelMes.withDayOfMonth(1);
        LocalDate hasta = desde.withDayOfMonth(desde.lengthOfMonth());
        List<Factura> facturas = new ArrayList<>(facturaDAO.listarPorRango(conexion, desde, hasta));
        List<Factura> anuladas = facturaDAO.listarAnuladasPorRango(conexion, desde, hasta);
        facturas.addAll(anuladas);
        facturas.sort((a, b) -> a.getNumero().compareTo(b.getNumero()));
        java.util.Set<Long> idsAnuladas = new java.util.HashSet<>();
        for (Factura anulada : anuladas) {
            idsAnuladas.add(anulada.getIdFactura());
        }
        List<FilaIva> filas = new ArrayList<>();
        for (Factura factura : facturas) {
            filas.add(new FilaIva(factura.getNumeroCompleto(), FormateadorFechas.comoDdMmAaaa(factura.getFecha()),
                    factura.getClienteNombre(), FormateadorTexto.comoCedulaConPuntos(factura.getClienteDocumento()),
                    FormateadorNumeros.comoGuaranies(factura.getTotalExenta()), factura.getTotalExenta(),
                    FormateadorNumeros.comoGuaranies(factura.getTotalGravada10()), factura.getTotalGravada10(),
                    FormateadorNumeros.comoGuaranies(factura.getIva10()), factura.getIva10(),
                    FormateadorNumeros.comoGuaranies(factura.getTotal()), factura.getTotal(),
                    idsAnuladas.contains(factura.getIdFactura())));
        }
        return filas;
    }

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 2 (pedido de Joel): totales al pie de
     * IVA del mes -- pura, sobre las filas ya armadas por listarIva, saltando las anuladas (no
     * suman, pedido explícito de Joel). */
    public TotalesIva calcularTotalesIva(List<FilaIva> filas) {
        int cantidad = 0;
        BigDecimal exenta = BigDecimal.ZERO;
        BigDecimal gravada10 = BigDecimal.ZERO;
        BigDecimal iva10 = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (FilaIva fila : filas) {
            if (fila.isAnulada()) {
                continue;
            }
            cantidad++;
            exenta = exenta.add(fila.getTotalExentaCrudo());
            gravada10 = gravada10.add(fila.getTotalGravada10Crudo());
            iva10 = iva10.add(fila.getIva10Crudo());
            total = total.add(fila.getTotalCrudo());
        }
        return new TotalesIva(cantidad, FormateadorNumeros.comoGuaranies(exenta),
                FormateadorNumeros.comoGuaranies(gravada10), FormateadorNumeros.comoGuaranies(iva10),
                FormateadorNumeros.comoGuaranies(total));
    }

    /** E9/Bloque 5 (gerente, para la baja en Marangatu): facturas ANULADA del mes -- excluidas de
     * IVA del mes (que sigue filtrando solo EMITIDA, ver listarIva) y de los totales del resumen
     * mensual/arqueo (que suman por aplicaciones_pago de pagos CONFIRMADO, ver AplicacionPagoDAO). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> listarFacturasAnuladas(Connection conexion,
            LocalDate primerDiaDelMes) throws SQLException {
        LocalDate desde = primerDiaDelMes.withDayOfMonth(1);
        LocalDate hasta = desde.withDayOfMonth(desde.lengthOfMonth());
        List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas = new ArrayList<>();
        for (Factura factura : facturaDAO.listarAnuladasPorRango(conexion, desde, hasta)) {
            // RG 118/2017: la comunicación de baja en Marangatu pide también la fecha de la
            // anulación (distinta de la fecha de emisión de la factura) y el motivo -- ambos
            // viven en la reversión que la anuló, nunca en la factura misma.
            ReversionPago reversion = factura.getIdReversionPago() == null ? null
                    : reversionPagoDAO.obtenerPorId(conexion, factura.getIdReversionPago());
            String fechaAnulacion = reversion == null || reversion.getFechaHoraReversion() == null ? "-"
                    : FormateadorFechas.comoDdMmAaaa(reversion.getFechaHoraReversion().toLocalDate());
            String motivo = reversion == null || reversion.getMotivoReversion() == null ? "-"
                    : reversion.getMotivoReversion();
            // Bloque C, tanda 2 (pedido de Joel): "quién anuló" -- el gerente que autorizó la
            // reversión que anuló esta factura (obtenerNombreCajero resuelve cualquier usuario
            // por id, no solo cajeras, ver su uso con solicitudes/desembolsos/pagos más arriba).
            String anuladoPorNombre = reversion == null || reversion.getAutorizadoPor() == null ? "-"
                    : obtenerNombreCajero(conexion, reversion.getAutorizadoPor());
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada(factura.getIdFactura(),
                    factura.getNumeroCompleto(), FormateadorFechas.comoDdMmAaaa(factura.getFecha()),
                    factura.getClienteNombre(), FormateadorTexto.comoCedulaConPuntos(factura.getClienteDocumento()),
                    FormateadorNumeros.comoGuaranies(factura.getTotal()), factura.getTotal(),
                    factura.getNumeroNotaAnulacion(),
                    factura.getTimbrado(), fechaAnulacion, motivo, anuladoPorNombre));
        }
        return filas;
    }

    /** Bloque C, tanda 2 (pedido de Joel): total de montos anulados del mes -- pura, sobre las
     * filas ya armadas por listarFacturasAnuladas. */
    public py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas calcularTotalesFacturasAnuladas(
            List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas) {
        BigDecimal total = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada fila : filas) {
            total = total.add(fila.getTotalCrudo());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas(filas.size(),
                FormateadorNumeros.comoGuaranies(total));
    }

    /** Gestiones de cobranza (§13, ampliado Bloque C tanda 3 a pedido de Joel): rango de fechas,
     * tipo y "solo incumplidas" opcionales -- ya como filas formateadas (operación, contactado y
     * quién registró resueltos acá), mismo molde que el resto de los informes de este bloque. */
    public List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> listarGestiones(Connection conexion,
            LocalDate desde, LocalDate hasta, String tipoFiltro, boolean soloIncumplidas, String clienteUOperacion)
            throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas = new ArrayList<>();
        LocalDate hoy = Reloj.hoy();
        for (GestionCobranza gestion : gestionCobranzaDAO.listarPorRango(conexion, desde, hasta, tipoFiltro, soloIncumplidas)) {
            PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, gestion.getIdPrestamo());
            Persona contactado = servicioClientes.buscarPorId(conexion, gestion.getIdPersonaContactada());
            // 13/10/2026 (pedido de Joel): "Cliente u operación" -- antes de armar la fila, ya
            // con el préstamo y el contactado a mano (evita un segundo recorrido/consulta).
            if (!coincideClienteUOperacion(clienteUOperacion, prestamo == null ? null : prestamo.getIdentificadorOperacion(),
                    contactado == null ? null : contactado.getCedulaIdentidad(),
                    contactado == null ? null : contactado.getNombres() + " " + contactado.getApellidos())) {
                continue;
            }
            boolean tienePromesa = gestion.getFechaPromesa() != null;
            String promesaTexto = tienePromesa
                    ? FormateadorFechas.comoDdMmAaaa(gestion.getFechaPromesa()) + " — "
                            + FormateadorNumeros.comoGuaranies(gestion.getMontoPrometido())
                    : "—";
            boolean vencidaSinCumplir = tienePromesa && !gestion.isCumplida() && gestion.getFechaPromesa().isBefore(hoy);
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaGestionReporte(
                    FormateadorFechas.comoDdMmAaaaHhMm(gestion.getFechaHora()), gestion.getIdPrestamo(),
                    prestamo == null ? "—" : prestamo.getIdentificadorOperacion(),
                    contactado == null ? "—" : contactado.getNombres() + " " + contactado.getApellidos(),
                    gestion.getTipo().getEtiqueta(), gestion.getResultado() == null ? "—" : gestion.getResultado(),
                    tienePromesa, promesaTexto, gestion.isCumplida(), vencidaSinCumplir,
                    obtenerNombreCajero(conexion, gestion.getRegistradoPor())));
        }
        return filas;
    }

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 3 (pedido de Joel) + C-3b: totales de
     * Gestiones de cobranza -- cantidad por tipo, promesas hechas, cumplidas, vencidas sin cumplir
     * y pendientes (aún no vencen), sobre el rango ya filtrado (misma lista que se muestra). Pura:
     * los tres conteos de promesa ya vienen resueltos en cada fila (listarGestiones), nada vuelve
     * a comparar fechas acá. Cumplidas + vencidas sin cumplir + pendientes = promesas hechas,
     * siempre (son mutuamente excluyentes). */
    public TotalesGestiones calcularTotalesGestiones(List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas) {
        Map<String, Integer> porTipo = new LinkedHashMap<>();
        int promesasHechas = 0;
        int cumplidas = 0;
        int vencidasSinCumplir = 0;
        int pendientes = 0;
        for (py.com.capitalcredito.sgcpc.dto.FilaGestionReporte fila : filas) {
            porTipo.merge(fila.getTipoEtiqueta(), 1, Integer::sum);
            if (fila.isTienePromesa()) {
                promesasHechas++;
                if (fila.isCumplida()) {
                    cumplidas++;
                } else if (fila.isVencidaSinCumplir()) {
                    vencidasSinCumplir++;
                } else {
                    pendientes++;
                }
            }
        }
        return new TotalesGestiones(filas.size(), porTipo, promesasHechas, cumplidas, vencidasSinCumplir, pendientes);
    }

    /**
     * Tope de filas del <b>informe PDF</b> de Auditoría.
     *
     * <p><b>B205 (15/11/2026)</b>: hasta hoy este tope era de los tres caminos (pantalla, PDF y
     * CSV) y por eso la pantalla abría truncada — el rango por defecto es <b>un solo día</b> y ese
     * día tenía <b>2.226</b> registros, así que el aviso decía <i>"se muestran las primeras 500 de
     * 2.226; acote el filtro"</i> con el filtro ya en un día, y de la fila 501 en adelante no se
     * llegaba. Ahora la <b>pantalla pagina</b> (sin tope) y el <b>CSV trae todo</b> (es el camino
     * para llevarse los datos completos); el tope queda <b>solo en el PDF</b>, donde sí tiene
     * sentido: un informe de 18.381 filas no lo quiere nadie y es el que puede tumbar la memoria
     * de Jasper. El PDF sigue imprimiendo su aviso, que ahora además dice de dónde sacar el
     * resto.</p>
     */
    public static final int LIMITE_AUDITORIA_PDF = 500;

    /** B205: filas por página de la pantalla de Auditoría — las 20 de todo el sistema
     * (B148/B167/B175/B183/B191/B197). */
    public static final int FILAS_POR_PAGINA_AUDITORIA = 20;

    /** B205: tope para los caminos que NO tienen tope (la pantalla paginada y el CSV). Se manda un
     * límite igual porque la consulta lo pide; con {@code Integer.MAX_VALUE} el {@code LIMIT} no
     * recorta nada real. */
    private static final int SIN_TOPE = Integer.MAX_VALUE;

    /**
     * Auditoría (§13): filtros opcionales, siempre acotado a un rango de fechas.
     *
     * <p>B205: {@code limite} y {@code desplazamiento} los decide el llamador, porque los tres
     * caminos quieren cosas distintas — la pantalla una página, el CSV todo, el PDF las primeras
     * {@link #LIMITE_AUDITORIA_PDF}. {@code contarAuditoria} da el total real en los tres.</p>
     */
    public List<FilaAuditoria> listarAuditoria(Connection conexion, LocalDate desde, LocalDate hasta, Long idUsuario,
            String tipoEntidad, String operacionParcial, int limite, int desplazamiento) throws SQLException {
        return auditoriaDAO.listarPorFiltros(conexion, desde, hasta, idUsuario, tipoEntidad, operacionParcial,
                limite, desplazamiento);
    }

    /** B205: la página que pide la pantalla (1 en adelante), con las filas por página del sistema. */
    public List<FilaAuditoria> listarAuditoriaPagina(Connection conexion, LocalDate desde, LocalDate hasta,
            Long idUsuario, String tipoEntidad, String operacionParcial, int pagina) throws SQLException {
        int paginaValida = Math.max(1, pagina);
        return listarAuditoria(conexion, desde, hasta, idUsuario, tipoEntidad, operacionParcial,
                FILAS_POR_PAGINA_AUDITORIA, (paginaValida - 1) * FILAS_POR_PAGINA_AUDITORIA);
    }

    /** B205: todas las filas del rango/filtros, sin tope — para el CSV, que es el camino por el
     * que el gerente se lleva los datos completos. */
    public List<FilaAuditoria> listarAuditoriaCompleta(Connection conexion, LocalDate desde, LocalDate hasta,
            Long idUsuario, String tipoEntidad, String operacionParcial) throws SQLException {
        return listarAuditoria(conexion, desde, hasta, idUsuario, tipoEntidad, operacionParcial, SIN_TOPE, 0);
    }

    /** B205: cuántas páginas tiene el listado de la pantalla, mínimo 1 (con cero filas la pantalla
     * sigue diciendo "Página 1 de 1", igual que las otras cinco paginaciones). */
    public static int totalPaginasAuditoria(int totalFilas) {
        return Math.max(1, (int) Math.ceil(totalFilas / (double) FILAS_POR_PAGINA_AUDITORIA));
    }

    /**
     * B207 (15/11/2026, ítem 5 del informe de UX de Informes): el aviso de que el rango de fechas
     * está al revés, o cadena vacía si está bien.
     *
     * <p><b>El defecto</b>: ningún reporte validaba que <i>Desde</i> fuera anterior a <i>Hasta</i>.
     * Con el rango invertido la consulta sale vacía y la pantalla contestaba con su cartel de "no
     * hay nada" — o sea que <b>"no hay datos" era indistinguible de "el filtro no tiene
     * sentido"</b>, y el que lo lee es el dueño decidiendo sobre plata. Es la misma familia que
     * <b>B161</b>: un resultado vacío que se come la explicación.</p>
     *
     * <p><b>Recibe los parámetros como texto, no fechas ya parseadas</b>, a propósito: cada
     * reporte guarda su rango de forma distinta (ocho lo dejan como atributo del request, los de
     * Caja/Solicitudes/Aprobaciones/Desembolsos/Cobros dentro de un DTO de filtro), pero los
     * <b>parámetros del pedido</b> son iguales en todos y son exactamente lo que la persona
     * escribió. Así el fragmento {@code plantilla/aviso_rango_reporte.jsp} sirve para los trece
     * sin tocar un solo controlador.</p>
     *
     * <p>Devuelve vacío ante cualquier cosa que no sea un rango invertido legible: campo en
     * blanco, texto que no es una fecha, o fechas iguales (un solo día es un rango válido — es el
     * que trae Auditoría por defecto).</p>
     */
    public static String avisoRangoInvertido(String desdeTexto, String hastaTexto) {
        LocalDate desde = fechaIsoONull(desdeTexto);
        LocalDate hasta = fechaIsoONull(hastaTexto);
        if (desde == null || hasta == null || !desde.isAfter(hasta)) {
            return "";
        }
        return "Revise el rango: «Desde» (" + FormateadorFechas.comoDdMmAaaa(desde)
                + ") es posterior a «Hasta» (" + FormateadorFechas.comoDdMmAaaa(hasta)
                + "), así que el listado sale vacío.";
    }

    /**
     * B211 (15/11/2026, ítem 6 del informe de UX de Informes): el rango que el reporte está
     * mostrando, en texto, para ponerlo debajo del título.
     *
     * <p><b>El defecto</b>: el rango aplicado no se decía en ninguna parte del texto. Los títulos
     * no lo nombraban y las fechas vivían solo dentro de los campos del formulario, así que una
     * pantalla mirada de lejos —o una captura, o un informe impreso de la pantalla— no decía de
     * qué período era. El PDF de Auditoría ya lo resolvía bien
     * ({@link ServicioInformes#construirTextoFiltrosAuditoria}); lo que faltaba era que la
     * <b>pantalla</b> hiciera lo mismo.</p>
     *
     * <p><b>Recibe las fechas EFECTIVAS, no los parámetros</b>, y esa es la diferencia con B207:
     * en la primera carga no hay ningún parámetro y el reporte igual está mostrando su rango por
     * defecto — que es justo cuando más falta saber cuál es. Por eso lo deja el controlador, que
     * es el que resolvió el valor, y la vista solo lo dibuja.</p>
     *
     * <p>Un solo día no se escribe como un rango: dice <i>"Del 15/11/2026"</i> en vez de
     * <i>"Del 15/11/2026 al 15/11/2026"</i>, que se lee como un error.</p>
     */
    public static String textoRangoAplicado(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return "";
        }
        if (desde.isEqual(hasta)) {
            return "Del " + FormateadorFechas.comoDdMmAaaa(desde) + ".";
        }
        return "Del " + FormateadorFechas.comoDdMmAaaa(desde) + " al "
                + FormateadorFechas.comoDdMmAaaa(hasta) + ".";
    }

    /** B211: lo mismo para los reportes que se piden por MES (IVA, Facturas anuladas, Resumen
     * mensual), que no tienen rango sino un {@code YearMonth}. */
    public static String textoMesAplicado(java.time.YearMonth mes) {
        return mes == null ? "" : "Mes de " + FormateadorFechas.comoMesYAnio(mes) + ".";
    }

    /** B207: la fecha de un parámetro {@code <input type="date">} (ISO), o null si no se puede
     * leer. No usa el {@code parsearFecha} del controlador porque acá un valor ilegible no cae en
     * un valor por defecto: cae en "no hay nada que avisar". */
    private static LocalDate fechaIsoONull(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (java.time.format.DateTimeParseException error) {
            return null;
        }
    }

    /** Bloque C tanda 3 (pedido de Joel): cantidad real de registros del rango/filtros, sin el
     * límite -- el contador del título de la pantalla (B205) y el aviso del PDF. */
    public int contarAuditoria(Connection conexion, LocalDate desde, LocalDate hasta, Long idUsuario,
            String tipoEntidad, String operacionParcial) throws SQLException {
        return auditoriaDAO.contarPorFiltros(conexion, desde, hasta, idUsuario, tipoEntidad, operacionParcial);
    }

    /** E-15 (pedido de Joel, C-3b): totales por tipo de operación sobre TODO el rango/filtros --
     * antes se calculaban en Java sobre la lista ya cortada por el tope (recuento
     * parcial con rangos grandes); ahora es una consulta GROUP BY aparte (AuditoriaDAO.
     * contarPorOperacion), sin el límite de filas del detalle. */
    public Map<String, Integer> contarTotalesPorOperacion(Connection conexion, LocalDate desde, LocalDate hasta,
            Long idUsuario, String tipoEntidad, String operacionParcial) throws SQLException {
        return auditoriaDAO.contarPorOperacion(conexion, desde, hasta, idUsuario, tipoEntidad, operacionParcial);
    }

    /** RF40/F2 (pedido de Joel, 13/10/2026): valores de "Entidad" para el <select>, en vez de
     * texto libre. */
    public List<String> listarEntidadesAuditoria(Connection conexion) throws SQLException {
        return auditoriaDAO.listarEntidadesDistintas(conexion);
    }

    /** Estado de cuenta del cliente (§13): mismos datos que la ficha de /cobranza/{id} (D-97),
     * más el historial de gestiones -- accesible también al oficial (que no entra a /cobranza). */
    public List<FilaHistorialCobranza> obtenerHistorialCobros(Connection conexion, long idPrestamo)
            throws SQLException {
        return servicioCobranza.listarHistorialCobros(conexion, idPrestamo);
    }

    public List<GestionCobranza> obtenerGestionesDelPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        return gestionCobranzaDAO.listarPorPrestamo(conexion, idPrestamo);
    }

    /** Estado de cuenta (RF34, PDF/CSV, pedido de Joel): mismas gestiones que
     * obtenerGestionesDelPrestamo, ya resueltas a FilaGestionReporte (contactado/registrado por por
     * nombre) -- mismo molde de fila que /reportes/gestiones, para no inventar un DTO nuevo solo
     * para este informe. Duplica la construcción de la fila de listarGestiones (en vez de
     * compartirla) para no tocar ese método -- ya probado y tagueado (rf32-probado). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> listarGestionesEstadoCuenta(Connection conexion,
            long idPrestamo) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas = new ArrayList<>();
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        String identificador = prestamo == null ? "—" : prestamo.getIdentificadorOperacion();
        LocalDate hoy = Reloj.hoy();
        for (GestionCobranza gestion : obtenerGestionesDelPrestamo(conexion, idPrestamo)) {
            Persona contactado = servicioClientes.buscarPorId(conexion, gestion.getIdPersonaContactada());
            boolean tienePromesa = gestion.getFechaPromesa() != null;
            String promesaTexto = tienePromesa
                    ? FormateadorFechas.comoDdMmAaaa(gestion.getFechaPromesa()) + " — "
                            + FormateadorNumeros.comoGuaranies(gestion.getMontoPrometido())
                    : "—";
            boolean vencidaSinCumplir = tienePromesa && !gestion.isCumplida() && gestion.getFechaPromesa().isBefore(hoy);
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaGestionReporte(
                    FormateadorFechas.comoDdMmAaaaHhMm(gestion.getFechaHora()), idPrestamo, identificador,
                    contactado == null ? "—" : contactado.getNombres() + " " + contactado.getApellidos(),
                    gestion.getTipo().getEtiqueta(), gestion.getResultado() == null ? "—" : gestion.getResultado(),
                    tienePromesa, promesaTexto, gestion.isCumplida(), vencidaSinCumplir,
                    obtenerNombreCajero(conexion, gestion.getRegistradoPor())));
        }
        return filas;
    }

    /** Estado de cuenta (RF34, PDF/CSV): mismo aviso que ya muestra la ficha sobre el plan de
     * pagos cuando el préstamo todavía no tiene deuda real -- pura, para reusarla en el PDF/CSV
     * sin repetir el if/else que ya tiene estado_cuenta_ficha.jsp. */
    public String construirAvisoPlanEstadoCuenta(EstadoPrestamo estado) {
        if (estado == EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
            return "Plan proyectado: el préstamo todavía no fue desembolsado.";
        }
        if (estado == EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO) {
            return "Préstamo anulado antes del desembolso; no generó deuda.";
        }
        return "";
    }

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 1 (pedido de Joel): total de SALDO de
     * Incobrables -- reusa saldoCrudo, que ya trae cada FilaMora (calculado una sola vez en
     * ServicioMora con sumarSaldoCapital, la misma cuenta que el listado de Préstamos), sin volver
     * a consultar. Antes sumaba montoExigibleCrudo (capital + interés + mora + punitorio + IVA de
     * las cuotas vencidas) -- no es lo que Joel pidió para este total. */
    public TotalesIncobrables calcularTotalesIncobrables(List<FilaMora> filas) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (FilaMora fila : filas) {
            saldo = saldo.add(fila.getSaldoCrudo());
        }
        return new TotalesIncobrables(filas.size(), FormateadorNumeros.comoGuaranies(saldo));
    }

    private static final String[] ORDEN_TRAMOS = {"1–30", "31–60", "61–90", "91–180", "Más de 180"};

    /** Bloque 4 (E8, corrección de Joel) + Bloque C tanda 1 (pedido de Joel): subtotales por tramo
     * de atraso de Morosidad, en el orden fijo de Doc. 00 §13 (no el orden en que aparecen en la
     * lista). Cantidad, capital vencido (solo cuotas ya vencidas) y saldo (capital pendiente de
     * TODO el préstamo, mismo cálculo que /prestamos) -- antes solo mostraba el monto exigible
     * completo (con interés/mora/punitorio/IVA); Joel pidió separar en estas tres columnas para
     * poder cruzar el total con /prestamos. */
    public List<SubtotalTramoMorosidad> calcularSubtotalesPorTramo(List<FilaMora> filas) {
        List<SubtotalTramoMorosidad> subtotales = new ArrayList<>();
        for (String tramo : ORDEN_TRAMOS) {
            int cantidad = 0;
            BigDecimal capitalVencido = BigDecimal.ZERO;
            BigDecimal saldo = BigDecimal.ZERO;
            for (FilaMora fila : filas) {
                if (fila.getTramo().equals(tramo)) {
                    cantidad++;
                    capitalVencido = capitalVencido.add(fila.getCapitalVencidoCrudo());
                    saldo = saldo.add(fila.getSaldoCrudo());
                }
            }
            if (cantidad > 0) {
                subtotales.add(new SubtotalTramoMorosidad(tramo, cantidad,
                        FormateadorNumeros.comoGuaranies(capitalVencido), FormateadorNumeros.comoGuaranies(saldo)));
            }
        }
        return subtotales;
    }

    public SubtotalTramoMorosidad calcularTotalGeneralMorosidad(List<FilaMora> filas) {
        BigDecimal capitalVencido = BigDecimal.ZERO;
        BigDecimal saldo = BigDecimal.ZERO;
        for (FilaMora fila : filas) {
            capitalVencido = capitalVencido.add(fila.getCapitalVencidoCrudo());
            saldo = saldo.add(fila.getSaldoCrudo());
        }
        return new SubtotalTramoMorosidad("Total general", filas.size(),
                FormateadorNumeros.comoGuaranies(capitalVencido), FormateadorNumeros.comoGuaranies(saldo));
    }

    /** Bloque 4 (E8, corrección de Joel) + punto 9 (Bloque 7/E10, pedido de Joel): "Plan de pagos"
     * del Estado de cuenta -- TODAS las cuotas del plan vigente, pagadas incluidas (con su fecha
     * de pago y N.º de recibo), no solo las pendientes. Mismos valores PROGRAMADOS que
     * Vencimientos (no el neto de un pago parcial); "estado" ya dice si una cuota está Parcial. */
    public List<FilaCuotaPendiente> listarPlanDePagos(Connection conexion, long idPrestamo) throws SQLException {
        List<FilaCuotaPendiente> filas = new ArrayList<>();
        PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
        if (plan == null) {
            return filas;
        }
        LocalDate hoy = Reloj.hoy();
        for (Cuota cuota : cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago())) {
            String fechaPago = null;
            String numeroRecibo = null;
            String estado;
            if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                estado = "Pagada";
                py.com.capitalcredito.sgcpc.dto.PagoDeCuota pagoDeCuota = aplicacionPagoDAO.obtenerPagoQueTocoLaCuota(
                        conexion, cuota.getIdCuota());
                if (pagoDeCuota != null) {
                    fechaPago = FormateadorFechas.comoDdMmAaaa(pagoDeCuota.getFechaHoraPago().toLocalDate());
                    numeroRecibo = pagoDeCuota.getNumeroRecibo();
                }
            } else if (cuota.getEstadoPago() == EstadoPago.PARCIAL) {
                // Bloque 4 (E8, corrección de Joel): "Vencida" usa la MISMA regla que ya decide
                // EN_MORA y calcularDeudaExigible -- fecha_vencimiento efectiva < hoy, cuota no
                // PAGADA (el motor solo usa los días de gracia para no cobrar moratorio dentro de
                // esa ventana, nunca para correr la fecha en la que una cuota pasa a estar
                // vencida; D-77). Una cuota PARCIAL muestra "Parcial" aunque esté vencida -- ya
                // dice que algo se cobró, no hace falta duplicar el aviso.
                estado = "Parcial";
            } else if (cuota.getFechaVencimiento().isBefore(hoy)) {
                estado = "Vencida";
            } else {
                estado = "Pendiente";
            }
            filas.add(new FilaCuotaPendiente(cuota.getNumeroCuota(), FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()),
                    FormateadorNumeros.comoGuaranies(cuota.getCapitalProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getInteresProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getTributosProgramados()),
                    FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()),
                    estado, fechaPago, numeroRecibo));
        }
        return filas;
    }

    /** Bloque 4 (E8, corrección de Joel) + E-9b (pedido de Joel): saldo de capital, total pagado
     * y monto exigible hoy del Estado de cuenta. "Monto exigible hoy" solo tiene sentido para
     * ACTIVO/EN_MORA -- para un préstamo FINALIZADO viene null (la JSP no muestra esa fila, sin
     * cambios); para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_DESEMBOLSO la JSP SÍ la muestra, con "—"
     * (pedido explícito de Joel, distinto de FINALIZADO). Mismo criterio que la ficha de
     * /cobranza/{id} (D-97), que tampoco llama a calcularDeudaExigible cuando el préstamo ya
     * está FINALIZADO. */
    public TotalesEstadoCuenta calcularTotalesEstadoCuenta(Connection conexion, PrestamoCredito prestamo)
            throws SQLException {
        BigDecimal saldoCapital = calcularSaldoCapital(conexion, prestamo.getIdPrestamo(), prestamo.getEstadoPrestamo());
        BigDecimal totalPagado = BigDecimal.ZERO;
        for (Pago pago : pagoDAO.listarPorPrestamo(conexion, prestamo.getIdPrestamo())) {
            if (pago.getEstadoPago() == EstadoConfirmacionPago.CONFIRMADO) {
                totalPagado = totalPagado.add(pago.getMontoAplicado());
            }
        }
        String montoExigibleHoy = null;
        if (prestamo.getEstadoPrestamo() == EstadoPrestamo.ACTIVO || prestamo.getEstadoPrestamo() == EstadoPrestamo.EN_MORA) {
            montoExigibleHoy = FormateadorNumeros.comoGuaranies(
                    servicioCobranza.calcularDeudaExigible(conexion, prestamo.getIdPrestamo(), Reloj.hoy())
                            .getTotalGeneral());
        }
        return new TotalesEstadoCuenta(saldoCapital == null ? "—" : FormateadorNumeros.comoGuaranies(saldoCapital),
                FormateadorNumeros.comoGuaranies(totalPagado), montoExigibleHoy);
    }

    /** Saldo de capital pendiente de un préstamo -- extraído de calcularTotalesEstadoCuenta para
     * que B1 (listado de Préstamos) use EXACTAMENTE la misma cuenta, nunca una copia (pedido
     * explícito de Joel). E-9b: null (no 0, no el monto) para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_
     * DESEMBOLSO -- sin desembolso no hay deuda; tieneDeuda() es la MISMA decisión que usa
     * listarInformePrestamos, así que la lista y la ficha nunca pueden mostrar números distintos. */
    public BigDecimal calcularSaldoCapital(Connection conexion, long idPrestamo, EstadoPrestamo estado)
            throws SQLException {
        if (!tieneDeuda(estado)) {
            return null;
        }
        PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
        return plan == null ? BigDecimal.ZERO : sumarSaldoCapital(cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago()));
    }

    /** E-9/E-9b (pedido de Joel): sin desembolso no hay deuda -- pura, paquete-visible para
     * probarla sin la base. Único lugar que decide esto; calcularSaldoCapital y
     * listarInformePrestamos la llaman a ella, nunca duplican la condición. */
    static boolean tieneDeuda(EstadoPrestamo estado) {
        return estado != EstadoPrestamo.PENDIENTE_DESEMBOLSO && estado != EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO;
    }

    /** Misma cuenta que arriba, pura, sobre cuotas ya cargadas -- paquete-visible para probarla
     * sin la base (REGLAS_DESARROLLO.md §7 punto 9) y para que listarInformePrestamos no repita la consulta
     * de cuotas que ya hizo para contar cuotas pagadas/total. */
    static BigDecimal sumarSaldoCapital(List<Cuota> cuotas) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Cuota cuota : cuotas) {
            saldo = saldo.add(cuota.getCapitalProgramado()).subtract(cuota.getCapitalPagado());
        }
        return saldo;
    }

    // ======================================================================
    // B1 (Bloque 7/E10, pedido de Joel): listado de Préstamos (/prestamos) -- misma
    // consulta/filas para la pantalla, el informe PDF y el CSV (mismo criterio que D-135/
    // Clientes). El saldo es calcularSaldoCapital de arriba, el mismo que usa Estado de cuenta.
    // ======================================================================

    public List<FilaInformePrestamo> listarInformePrestamos(Connection conexion, FiltroPrestamos filtro)
            throws SQLException {
        List<FilaInformePrestamo> filas = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarConFiltros(conexion, filtro)) {
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // E-9/E-9b (pedido de Joel): sin desembolso no hay deuda -- misma decisión que
            // calcularSaldoCapital (tieneDeuda), nunca una copia, para que la lista y la ficha de
            // Estado de cuenta jamás muestren números distintos.
            BigDecimal saldo = null;
            String saldoFormateado = "—";
            String cuotasTexto = "—";
            if (tieneDeuda(prestamo.getEstadoPrestamo())) {
                PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
                saldo = BigDecimal.ZERO;
                int cuotasPagadas = 0;
                int cuotasTotal = 0;
                if (plan != null) {
                    List<Cuota> cuotas = cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago());
                    cuotasTotal = cuotas.size();
                    saldo = sumarSaldoCapital(cuotas);
                    for (Cuota cuota : cuotas) {
                        if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                            cuotasPagadas++;
                        }
                    }
                }
                saldoFormateado = FormateadorNumeros.comoGuaranies(saldo);
                cuotasTexto = cuotasPagadas + "/" + cuotasTotal;
            }
            filas.add(new FilaInformePrestamo(prestamo.getIdPrestamo(), prestamo.getIdentificadorOperacion(),
                    cliente.getNombres() + " " + cliente.getApellidos(),
                    FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()), prestamo.getMontoAprobado(),
                    FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()), prestamo.getPlazoMeses(),
                    prestamo.getFechaDesembolso() == null ? "—" : FormateadorFechas.comoDdMmAaaa(prestamo.getFechaDesembolso()),
                    prestamo.getEstadoPrestamo().getEtiqueta(), saldo, saldoFormateado, cuotasTexto));
        }
        return filas;
    }

    /** Totales (<tfoot>) del listado de Préstamos -- pura, sobre las filas ya armadas arriba
     * (sin volver a consultar la base, mismo criterio que calcularTotalesGestiones/Incobrables).
     * E-9: el monto total suma TODAS las filas (pedido explícito de Joel); el saldo total suma
     * solo las que tienen saldo (getSaldo() != null) -- un préstamo sin desembolsar queda afuera,
     * no aporta 0 ni el monto. */
    public TotalesPrestamos calcularTotalesPrestamos(List<FilaInformePrestamo> filas) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        BigDecimal saldoTotal = BigDecimal.ZERO;
        for (FilaInformePrestamo fila : filas) {
            montoTotal = montoTotal.add(fila.getMontoAprobado());
            if (fila.getSaldo() != null) {
                saldoTotal = saldoTotal.add(fila.getSaldo());
            }
        }
        return new TotalesPrestamos(filas.size(), FormateadorNumeros.comoGuaranies(montoTotal),
                FormateadorNumeros.comoGuaranies(saldoTotal));
    }

    /** Resumen por estado (cantidad, monto, saldo) del listado de Préstamos -- pura, en el orden
     * del ciclo de vida del préstamo (EstadoPrestamo.values()), no alfabético; solo los estados
     * que de verdad aparecen en las filas. E-9: el saldo de un estado sin deuda (PENDIENTE_
     * DESEMBOLSO/ANULADO_ANTES_DESEMBOLSO, donde ninguna fila tiene saldo) queda "—", no "0 Gs."
     * -- Map.merge trata una clave sin mapear (o mapeada a null) como ausente y simplemente pone
     * el primer valor, así que saldoPorEstado arranca en null y solo se completa con filas reales. */
    public List<ResumenEstadoPrestamo> calcularResumenPorEstadoPrestamos(List<FilaInformePrestamo> filas) {
        Map<String, Integer> cantidadPorEstado = new LinkedHashMap<>();
        Map<String, BigDecimal> montoPorEstado = new LinkedHashMap<>();
        Map<String, BigDecimal> saldoPorEstado = new LinkedHashMap<>();
        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            cantidadPorEstado.put(estado.getEtiqueta(), 0);
            montoPorEstado.put(estado.getEtiqueta(), BigDecimal.ZERO);
            saldoPorEstado.put(estado.getEtiqueta(), null);
        }
        for (FilaInformePrestamo fila : filas) {
            cantidadPorEstado.merge(fila.getEstado(), 1, Integer::sum);
            montoPorEstado.merge(fila.getEstado(), fila.getMontoAprobado(), BigDecimal::add);
            if (fila.getSaldo() != null) {
                saldoPorEstado.merge(fila.getEstado(), fila.getSaldo(), BigDecimal::add);
            }
        }
        List<ResumenEstadoPrestamo> resumen = new ArrayList<>();
        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            String etiqueta = estado.getEtiqueta();
            int cantidad = cantidadPorEstado.get(etiqueta);
            if (cantidad > 0) {
                BigDecimal saldoEstado = saldoPorEstado.get(etiqueta);
                resumen.add(new ResumenEstadoPrestamo(etiqueta, cantidad,
                        FormateadorNumeros.comoGuaranies(montoPorEstado.get(etiqueta)),
                        saldoEstado == null ? "—" : FormateadorNumeros.comoGuaranies(saldoEstado)));
            }
        }
        return resumen;
    }

    // ======================================================================
    // Bloque 7/E10, Tanda D (D-128): resumen financiero, informes desde-hasta
    // y proyección de fondos.
    // ======================================================================

    private static final BigDecimal[] LIMITES_TRAMOS_MONTO =
            {new BigDecimal("2000000"), new BigDecimal("5000000"), new BigDecimal("10000000")};
    private static final String[] ETIQUETAS_TRAMOS_MONTO =
            {"500.000–2.000.000", "2.000.001–5.000.000", "5.000.001–10.000.000", "10.000.001–15.000.000"};
    private static final int[] LIMITES_TRAMOS_PLAZO = {6, 12, 24};
    private static final String[] ETIQUETAS_TRAMOS_PLAZO = {"3–6 meses", "7–12 meses", "13–24 meses", "Más de 24 meses"};

    /**
     * Resumen financiero (D-128): igual que Resumen mensual (§13) pero para un rango desde-hasta
     * arbitrario, con créditos en trámite, cartera (foto de hoy, no del rango -- la cartera no es
     * un concepto "del período") y demanda por tramo de monto/plazo agregados.
     */
    public ResumenFinanciero calcularResumenFinanciero(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        List<py.com.capitalcredito.sgcpc.modelos.Desembolso> desembolsos =
                desembolsoDAO.listarConfirmadosPorRango(conexion, desde, hasta);
        BigDecimal montoOtorgado = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.modelos.Desembolso desembolso : desembolsos) {
            montoOtorgado = montoOtorgado.add(desembolso.getMontoNetoEntregado());
        }

        List<SolicitudCredito> pendientesDecision = solicitudDAO.listarPendientesAprobacion(conexion);
        BigDecimal montoEnTramite = BigDecimal.ZERO;
        for (SolicitudCredito solicitud : pendientesDecision) {
            montoEnTramite = montoEnTramite.add(solicitud.getMontoSolicitado());
        }

        // Bloque 7/E10 (D-129, Obs. 28): "Aprobados sin desembolsar" -- foto de hoy, mismo
        // criterio (y mismas dos fuentes) que "egresos comprometidos" de Proyección de fondos:
        // solicitudes APROBADA sin formalizar + préstamos PENDIENTE_DESEMBOLSO.
        List<SolicitudCredito> aprobadasSinFormalizar = solicitudDAO.listarAprobadasSinFormalizar(conexion);
        List<PrestamoCredito> pendientesDesembolso = prestamoDAO.listarPendientesDesembolso(conexion);
        BigDecimal montoAprobadoSinDesembolsar = BigDecimal.ZERO;
        for (SolicitudCredito solicitud : aprobadasSinFormalizar) {
            montoAprobadoSinDesembolsar = montoAprobadoSinDesembolsar.add(solicitud.getMontoAprobado());
        }
        for (PrestamoCredito prestamo : pendientesDesembolso) {
            montoAprobadoSinDesembolsar = montoAprobadoSinDesembolsar.add(prestamo.getMontoAprobado());
        }

        BigDecimal[] composicion = aplicacionPagoDAO.sumarPorRangoFechas(conexion, desde, hasta);
        BigDecimal capital = composicion[0];
        BigDecimal interes = composicion[1];
        BigDecimal moratorio = composicion[2];
        BigDecimal punitorio = composicion[3];
        BigDecimal iva = composicion[4];
        BigDecimal total = capital.add(interes).add(moratorio).add(punitorio).add(iva);
        BigDecimal ganancia = interes.add(moratorio).add(punitorio);

        int prestamosEnMora = prestamoDAO.listarEnMora(conexion).size();
        List<PrestamoCredito> activosOMora = prestamoDAO.listarActivosOEnMora(conexion);
        int prestamosActivosOMora = activosOMora.size();
        BigDecimal capitalCarteraPendiente = BigDecimal.ZERO;
        BigDecimal interesFuturoCartera = BigDecimal.ZERO;
        BigDecimal capitalVencido = BigDecimal.ZERO;
        // 13/10/2026 (hallazgo de Joel): "capitalVencido" de abajo es solo lo VENCIDO -- esta
        // suma aparte es el saldo TOTAL (vencido + no vencido) de los préstamos EN_MORA, la otra
        // mirada que pidió agregar (ver ResumenFinanciero.getSaldoPrestamosEnMoraPorcentaje).
        BigDecimal capitalEnMora = BigDecimal.ZERO;
        for (PrestamoCredito prestamo : activosOMora) {
            PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            if (plan == null) {
                continue;
            }
            BigDecimal capitalPendientePrestamo = BigDecimal.ZERO;
            for (Cuota cuota : cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago())) {
                if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                    capitalPendientePrestamo = capitalPendientePrestamo.add(cuota.getCapitalProgramado())
                            .subtract(cuota.getCapitalPagado());
                }
            }
            capitalCarteraPendiente = capitalCarteraPendiente.add(capitalPendientePrestamo);
            if (prestamo.getEstadoPrestamo() == EstadoPrestamo.EN_MORA) {
                capitalEnMora = capitalEnMora.add(capitalPendientePrestamo);
            }
            interesFuturoCartera = interesFuturoCartera.add(cuotaDAO.sumarInteresFuturoPendiente(conexion, plan.getIdPlanPago()));
        }
        for (PrestamoCredito prestamo : prestamoDAO.listarEnMora(conexion)) {
            for (py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible detalle : servicioCobranza
                    .calcularDeudaExigible(conexion, prestamo.getIdPrestamo(), Reloj.hoy()).getDetalles()) {
                capitalVencido = capitalVencido.add(detalle.getCapitalPendiente());
            }
        }

        ResumenFinanciero resumen = new ResumenFinanciero();
        resumen.setOtorgadosCantidad(desembolsos.size());
        resumen.setOtorgadosMonto(FormateadorNumeros.comoGuaranies(montoOtorgado));
        resumen.setEnTramiteCantidad(pendientesDecision.size());
        resumen.setEnTramiteMonto(FormateadorNumeros.comoGuaranies(montoEnTramite));
        resumen.setAprobadosSinDesembolsarCantidad(aprobadasSinFormalizar.size() + pendientesDesembolso.size());
        resumen.setAprobadosSinDesembolsarMonto(FormateadorNumeros.comoGuaranies(montoAprobadoSinDesembolsar));
        resumen.setCobradoCapital(FormateadorNumeros.comoGuaranies(capital));
        resumen.setCobradoInteres(FormateadorNumeros.comoGuaranies(interes));
        resumen.setCobradoMoratorio(FormateadorNumeros.comoGuaranies(moratorio));
        resumen.setCobradoPunitorio(FormateadorNumeros.comoGuaranies(punitorio));
        resumen.setCobradoIva(FormateadorNumeros.comoGuaranies(iva));
        resumen.setCobradoTotal(FormateadorNumeros.comoGuaranies(total));
        resumen.setGananciaDelPeriodo(FormateadorNumeros.comoGuaranies(ganancia));
        resumen.setCarteraCapitalPendiente(FormateadorNumeros.comoGuaranies(capitalCarteraPendiente));
        resumen.setCarteraInteresFuturo(FormateadorNumeros.comoGuaranies(interesFuturoCartera));
        resumen.setMorosidadPorcentajeCantidad(prestamosActivosOMora == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(BigDecimal.valueOf(prestamosEnMora)
                        .divide(BigDecimal.valueOf(prestamosActivosOMora), 4, java.math.RoundingMode.HALF_UP)));
        resumen.setMorosidadPorcentajeMonto(capitalCarteraPendiente.compareTo(BigDecimal.ZERO) == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(capitalVencido.divide(capitalCarteraPendiente, 4, java.math.RoundingMode.HALF_UP)));
        resumen.setSaldoPrestamosEnMoraPorcentaje(capitalCarteraPendiente.compareTo(BigDecimal.ZERO) == 0 ? "0 %"
                : FormateadorNumeros.comoPorcentaje(capitalEnMora.divide(capitalCarteraPendiente, 4, java.math.RoundingMode.HALF_UP)));

        List<SolicitudCredito> solicitudesDelPeriodo = solicitudDAO.listarPorRangoFecha(conexion, desde, hasta);
        resumen.setDemandaPorMonto(calcularDemandaPorTramoMonto(solicitudesDelPeriodo));
        resumen.setDemandaPorPlazo(calcularDemandaPorTramoPlazo(solicitudesDelPeriodo));
        return resumen;
    }

    private List<SubtotalTramo> calcularDemandaPorTramoMonto(List<SolicitudCredito> solicitudes) {
        int[] cantidades = new int[ETIQUETAS_TRAMOS_MONTO.length];
        BigDecimal[] montos = new BigDecimal[ETIQUETAS_TRAMOS_MONTO.length];
        java.util.Arrays.fill(montos, BigDecimal.ZERO);
        for (SolicitudCredito solicitud : solicitudes) {
            int tramo = 0;
            while (tramo < LIMITES_TRAMOS_MONTO.length
                    && solicitud.getMontoSolicitado().compareTo(LIMITES_TRAMOS_MONTO[tramo]) > 0) {
                tramo++;
            }
            cantidades[tramo]++;
            montos[tramo] = montos[tramo].add(solicitud.getMontoSolicitado());
        }
        List<SubtotalTramo> resultado = new ArrayList<>();
        for (int i = 0; i < ETIQUETAS_TRAMOS_MONTO.length; i++) {
            if (cantidades[i] > 0) {
                resultado.add(new SubtotalTramo(ETIQUETAS_TRAMOS_MONTO[i], cantidades[i],
                        FormateadorNumeros.comoGuaranies(montos[i])));
            }
        }
        return resultado;
    }

    private List<SubtotalTramo> calcularDemandaPorTramoPlazo(List<SolicitudCredito> solicitudes) {
        int[] cantidades = new int[ETIQUETAS_TRAMOS_PLAZO.length];
        BigDecimal[] montos = new BigDecimal[ETIQUETAS_TRAMOS_PLAZO.length];
        java.util.Arrays.fill(montos, BigDecimal.ZERO);
        for (SolicitudCredito solicitud : solicitudes) {
            int tramo = 0;
            while (tramo < LIMITES_TRAMOS_PLAZO.length
                    && solicitud.getPlazoSolicitadoMeses() > LIMITES_TRAMOS_PLAZO[tramo]) {
                tramo++;
            }
            cantidades[tramo]++;
            montos[tramo] = montos[tramo].add(solicitud.getMontoSolicitado());
        }
        List<SubtotalTramo> resultado = new ArrayList<>();
        for (int i = 0; i < ETIQUETAS_TRAMOS_PLAZO.length; i++) {
            if (cantidades[i] > 0) {
                resultado.add(new SubtotalTramo(ETIQUETAS_TRAMOS_PLAZO[i], cantidades[i],
                        FormateadorNumeros.comoGuaranies(montos[i])));
            }
        }
        return resultado;
    }

    // ======================================================================
    // B2 (Bloque 7/E10, pedido de Joel): informe de Solicitudes (/reportes/solicitudes) -- misma
    // consulta/filas para la pantalla, el informe PDF y el CSV (mismo criterio que B1/D-135).
    // Reemplaza al viejo "Informe Solicitudes" (D-128), que no tenía C.I./oficial/filtros.
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> listarInformeSolicitudes(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas = new ArrayList<>();
        for (SolicitudCredito solicitud : solicitudDAO.listarConFiltros(conexion, filtro)) {
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // 13/10/2026 (pedido de Joel): "Cliente u operación" -- la "operación" acá es el
            // código de la solicitud (CC-... todavía no existe mientras no se formaliza).
            if (!coincideClienteUOperacion(filtro.getClienteUOperacion(), solicitud.getCodigoSolicitud(),
                    cliente == null ? null : cliente.getCedulaIdentidad(),
                    cliente == null ? null : cliente.getNombres() + " " + cliente.getApellidos())) {
                continue;
            }
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud(solicitud.getIdSolicitud(),
                    solicitud.getCodigoSolicitud(), FormateadorFechas.comoDdMmAaaa(solicitud.getFechaSolicitud().toLocalDate()),
                    cliente == null ? "-" : cliente.getNombres() + " " + cliente.getApellidos(),
                    cliente == null ? "-" : FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                    solicitud.getMontoSolicitado(), FormateadorNumeros.comoGuaranies(solicitud.getMontoSolicitado()),
                    solicitud.getPlazoSolicitadoMeses(), solicitud.getEstadoSolicitud().getEtiqueta(),
                    obtenerNombreCajero(conexion, solicitud.getCreadoPor())));
        }
        return filas;
    }

    /** Totales (<tfoot>) del informe de Solicitudes -- pura, sobre las filas ya armadas arriba. */
    public py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte calcularTotalesSolicitudes(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud fila : filas) {
            montoTotal = montoTotal.add(fila.getMontoSolicitado());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte(filas.size(),
                FormateadorNumeros.comoGuaranies(montoTotal));
    }

    /** Resumen por estado (cantidad, monto) del informe de Solicitudes -- pura, en el orden del
     * CHECK de estado_solicitud (EstadoSolicitud.values()); solo los estados que de verdad
     * aparecen en las filas (mismo criterio que B1/ResumenEstadoPrestamo). */
    public List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud> calcularResumenPorEstadoSolicitudes(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas) {
        Map<String, Integer> cantidadPorEstado = new LinkedHashMap<>();
        Map<String, BigDecimal> montoPorEstado = new LinkedHashMap<>();
        for (py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud estado
                : py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.values()) {
            cantidadPorEstado.put(estado.getEtiqueta(), 0);
            montoPorEstado.put(estado.getEtiqueta(), BigDecimal.ZERO);
        }
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud fila : filas) {
            cantidadPorEstado.merge(fila.getEstado(), 1, Integer::sum);
            montoPorEstado.merge(fila.getEstado(), fila.getMontoSolicitado(), BigDecimal::add);
        }
        List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud> resumen = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud estado
                : py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.values()) {
            String etiqueta = estado.getEtiqueta();
            int cantidad = cantidadPorEstado.get(etiqueta);
            if (cantidad > 0) {
                resumen.add(new py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud(etiqueta, cantidad,
                        FormateadorNumeros.comoGuaranies(montoPorEstado.get(etiqueta))));
            }
        }
        return resumen;
    }

    /** Oficiales de crédito, para el <select> del filtro de /reportes/solicitudes -- alfabético. */
    public List<py.com.capitalcredito.sgcpc.modelos.Usuario> listarOficiales(Connection conexion) throws SQLException {
        List<py.com.capitalcredito.sgcpc.modelos.Usuario> oficiales = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.modelos.Usuario usuario : servicioUsuarios.listar(conexion)) {
            if (usuario.getPerfilUsuario() == py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario.OFICIAL_CREDITO) {
                oficiales.add(usuario);
            }
        }
        oficiales.sort((a, b) -> (a.getApellidos() + a.getNombres()).compareToIgnoreCase(b.getApellidos() + b.getNombres()));
        return oficiales;
    }

    // ======================================================================
    // B3 (Bloque 7/E10, pedido de Joel): informe de Aprobaciones y rechazos (/reportes/
    // aprobaciones, pantalla nueva) -- solicitudes decididas (APROBADA/RECHAZADA) en el período,
    // por fecha_decision. No hace falta migración: fecha_envio_aprobacion, fecha_decision,
    // decidido_por y motivo_rechazo ya existen y ya se guardan (SolicitudDAO.marcarDecidida,
    // llamado desde ServicioAprobacionCredito.aprobar/rechazar).
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> listarInformeAprobaciones(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte filtro) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas = new ArrayList<>();
        for (SolicitudCredito solicitud : solicitudDAO.listarDecididasPorRangoFecha(conexion, filtro.getDesde(),
                filtro.getHasta())) {
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // 13/10/2026 (pedido de Joel): "Cliente u operación".
            if (!coincideClienteUOperacion(filtro.getClienteUOperacion(), solicitud.getCodigoSolicitud(),
                    cliente == null ? null : cliente.getCedulaIdentidad(),
                    cliente == null ? null : cliente.getNombres() + " " + cliente.getApellidos())) {
                continue;
            }
            py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud estado = solicitud.getEstadoSolicitud();
            boolean aprobada = estado == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.APROBADA;
            // RF07 (pedido de Joel, 10/10/2026): el informe ahora también incluye CANCELADA (antes
            // solo aprobada/rechazada) -- esa decisión usa fecha_cancelacion/cancelado_por/
            // motivo_cancelacion en vez de fecha_decision/decidido_por/motivo_rechazo.
            boolean cancelada = estado == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.CANCELADA;
            java.time.LocalDateTime fechaDeLaDecision = cancelada ? solicitud.getFechaCancelacion() : solicitud.getFechaDecision();
            Long idDeQuienDecidio = cancelada ? solicitud.getCanceladoPor() : solicitud.getDecididoPor();
            String motivo = aprobada ? "—" : cancelada ? solicitud.getMotivoCancelacion() : solicitud.getMotivoRechazo();
            // RF07 (pedido de Joel, 11/10/2026): una CANCELADA puede venir de un BORRADOR que
            // nunca se envió a aprobación (ver RF07_bandeja_aprobacion.md, hallazgo del
            // 11/10/2026, SOL-2026-0003) -- fecha_envio_aprobacion queda null, y antes esto
            // tiraba NullPointerException acá (Duration.between con un extremo null). Sin envío
            // no hay "tiempo de respuesta" que calcular: queda en 0/"—", no entra al promedio.
            java.time.LocalDateTime fechaEnvioAprobacion = solicitud.getFechaEnvioAprobacion();
            long minutos = fechaEnvioAprobacion == null ? 0
                    : java.time.Duration.between(fechaEnvioAprobacion, fechaDeLaDecision).toMinutes();
            String tiempoFormateado = fechaEnvioAprobacion == null ? "—" : formatearDuracionMinutos(minutos);
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion(solicitud.getIdSolicitud(),
                    solicitud.getCodigoSolicitud(), cliente == null ? "-" : cliente.getNombres() + " " + cliente.getApellidos(),
                    solicitud.getMontoSolicitado(), FormateadorNumeros.comoGuaranies(solicitud.getMontoSolicitado()),
                    solicitud.getMontoAprobado(),
                    solicitud.getMontoAprobado() == null ? "—" : FormateadorNumeros.comoGuaranies(solicitud.getMontoAprobado()),
                    estado.getEtiqueta(), obtenerNombreCajero(conexion, idDeQuienDecidio),
                    // Corrección (pedido de Joel, 11/10/2026): "—" en vez de vacío, igual que las
                    // demás columnas sin dato (mismo criterio que FECHA_HORA_CIERRE en B1/Caja).
                    fechaEnvioAprobacion == null ? "—" : FormateadorFechas.comoDdMmAaaaHhMm(fechaEnvioAprobacion),
                    FormateadorFechas.comoDdMmAaaaHhMm(fechaDeLaDecision), minutos,
                    tiempoFormateado, motivo));
        }
        return filas;
    }

    /** Totales (<tfoot>) del informe de Aprobaciones y rechazos -- aprobadas/rechazadas/canceladas
     * por separado (cantidad y monto) y el tiempo promedio de respuesta. Corrección (pedido de
     * Joel, 11/10/2026): el promedio se calcula SOLO con aprobadas y rechazadas -- una cancelada
     * no tiene un "tiempo de respuesta" real que promediar (si nunca se envió a aprobación, su
     * tiempo ya queda en 0/"—" en la fila; si sí se envió, el tiempo hasta la cancelación no es
     * una "respuesta" del gerente). Si no hay ninguna aprobada ni rechazada, "—" en vez de "0
     * min". Pura. */
    public py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones calcularTotalesAprobaciones(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas) {
        int cantidadAprobadas = 0;
        int cantidadRechazadas = 0;
        int cantidadCanceladas = 0;
        BigDecimal montoAprobadas = BigDecimal.ZERO;
        BigDecimal montoRechazadas = BigDecimal.ZERO;
        BigDecimal montoCanceladas = BigDecimal.ZERO;
        long sumaMinutos = 0;
        int cantidadParaPromedio = 0;
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion fila : filas) {
            if ("Aprobada".equals(fila.getDecision())) {
                cantidadAprobadas++;
                montoAprobadas = montoAprobadas.add(fila.getMontoAprobado());
                sumaMinutos += fila.getTiempoRespuestaMinutos();
                cantidadParaPromedio++;
            } else if ("Cancelada".equals(fila.getDecision())) {
                // RF07 (pedido de Joel, 10/10/2026): tercera categoría -- antes el informe no
                // incluía CANCELADA (ver SolicitudDAO.listarDecididasPorRangoFecha).
                cantidadCanceladas++;
                montoCanceladas = montoCanceladas.add(fila.getMontoSolicitado());
            } else {
                cantidadRechazadas++;
                montoRechazadas = montoRechazadas.add(fila.getMontoSolicitado());
                sumaMinutos += fila.getTiempoRespuestaMinutos();
                cantidadParaPromedio++;
            }
        }
        String tiempoPromedio = cantidadParaPromedio == 0 ? "—" : formatearDuracionMinutos(sumaMinutos / cantidadParaPromedio);
        return new py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones(cantidadAprobadas,
                FormateadorNumeros.comoGuaranies(montoAprobadas), cantidadRechazadas,
                FormateadorNumeros.comoGuaranies(montoRechazadas), cantidadCanceladas,
                FormateadorNumeros.comoGuaranies(montoCanceladas), tiempoPromedio);
    }

    /** "N min" / "N h [M min]" / "D día(s) [H h]" según la magnitud -- package-private y pura,
     * la usan tanto cada fila (tiempo de respuesta individual) como el promedio de arriba. */
    static String formatearDuracionMinutos(long minutos) {
        return FormateadorFechas.comoDuracion(minutos);
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): informe de Desembolsos (/reportes/desembolsos) --
    // reemplaza al viejo "Informe Desembolsos" (D-128), que no tenía C.I./comprobante/quién
    // desembolsó ni clic a estado de cuenta.
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> listarInformeDesembolsos(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte filtro) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.modelos.Desembolso desembolso : desembolsoDAO.listarConfirmadosPorRango(
                conexion, filtro.getDesde(), filtro.getHasta())) {
            PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, desembolso.getIdPrestamo());
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // 13/10/2026 (pedido de Joel): "Cliente u operación".
            if (!coincideClienteUOperacion(filtro.getClienteUOperacion(), prestamo.getIdentificadorOperacion(),
                    cliente == null ? null : cliente.getCedulaIdentidad(),
                    cliente == null ? null : cliente.getNombres() + " " + cliente.getApellidos())) {
                continue;
            }
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso(prestamo.getIdPrestamo(),
                    FormateadorFechas.comoDdMmAaaa(desembolso.getFechaHoraDesembolso().toLocalDate()),
                    desembolso.getNumeroComprobante(),
                    cliente == null ? "-" : cliente.getNombres() + " " + cliente.getApellidos(),
                    cliente == null ? "-" : FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                    prestamo.getIdentificadorOperacion(), desembolso.getMontoNetoEntregado(),
                    FormateadorNumeros.comoGuaranies(desembolso.getMontoNetoEntregado()),
                    obtenerNombreCajero(conexion, desembolso.getDesembolsadoPor())));
        }
        return filas;
    }

    /** Totales (<tfoot>) del informe de Desembolsos -- pura. */
    public py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos calcularTotalesDesembolsos(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso fila : filas) {
            montoTotal = montoTotal.add(fila.getMonto());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos(filas.size(), FormateadorNumeros.comoGuaranies(montoTotal));
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): informe de Cobros (/reportes/cobros) -- reemplaza al
    // viejo "Informe Cobros" (D-128), que no tenía la composición (capital/interés/moratorio/
    // punitorio/IVA), ni mostraba los REVERTIDO, ni tenía C.I./cajera/clic a estado de cuenta.
    // ======================================================================

    /** Suma capital/interés/moratorio/punitorio/IVA(tributos) de una lista de aplicaciones_pago --
     * pura, sin base. Es la MISMA composición que AplicacionPagoDAO.sumarPorSesion/
     * sumarPorRangoFechas (esas sumas las hace Postgres con SUM() sobre las mismas columnas; acá
     * se suma en Java fila por fila) -- por eso el total de este informe para un día coincide
     * siempre con la "Composición de lo cobrado" del arqueo de esa sesión: sumar la composición de
     * cada pago y después sumar esas filas da lo mismo que sumar todas las aplicaciones_pago de
     * una vez (es solo reagrupar la misma suma), y las dos excluyen los REVERTIDO. Verificado con
     * un test (ServicioReportesTest), no con una consulta contra la base (REGLAS_DESARROLLO.md: no tocar la
     * base sin que haga falta). Índices del arreglo: 0 capital, 1 interés, 2 moratorio,
     * 3 punitorio, 4 tributos (IVA) -- mismo orden que sumarPorSesion. */
    static BigDecimal[] sumarAplicaciones(List<py.com.capitalcredito.sgcpc.modelos.AplicacionPago> aplicaciones) {
        BigDecimal[] sumas = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        for (py.com.capitalcredito.sgcpc.modelos.AplicacionPago aplicacion : aplicaciones) {
            sumas[0] = sumas[0].add(aplicacion.getMontoCapital());
            sumas[1] = sumas[1].add(aplicacion.getMontoInteres());
            sumas[2] = sumas[2].add(aplicacion.getMontoMoratorio());
            sumas[3] = sumas[3].add(aplicacion.getMontoPunitorio());
            sumas[4] = sumas[4].add(aplicacion.getMontoTributos());
        }
        return sumas;
    }

    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> listarInformeCobros(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte filtro) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas = new ArrayList<>();
        for (Pago pago : pagoDAO.listarTodosPorRango(conexion, filtro.getDesde(), filtro.getHasta())) {
            PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // 13/10/2026 (pedido de Joel): "Cliente u operación".
            if (!coincideClienteUOperacion(filtro.getClienteUOperacion(), prestamo.getIdentificadorOperacion(),
                    cliente == null ? null : cliente.getCedulaIdentidad(),
                    cliente == null ? null : cliente.getNombres() + " " + cliente.getApellidos())) {
                continue;
            }
            BigDecimal[] composicion = sumarAplicaciones(aplicacionPagoDAO.listarPorPago(conexion, pago.getIdPago()));
            boolean revertido = pago.getEstadoPago() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago.REVERTIDO;
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeCobro(prestamo.getIdPrestamo(),
                    FormateadorFechas.comoDdMmAaaa(pago.getFechaHoraPago().toLocalDate()), pago.getNumeroRecibo(),
                    cliente == null ? "-" : cliente.getNombres() + " " + cliente.getApellidos(),
                    cliente == null ? "-" : FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                    prestamo.getIdentificadorOperacion(),
                    pago.getTipo().getEtiqueta() + (revertido ? " — Revertido" : ""),
                    composicion[0], FormateadorNumeros.comoGuaranies(composicion[0]),
                    composicion[1], FormateadorNumeros.comoGuaranies(composicion[1]),
                    composicion[2], FormateadorNumeros.comoGuaranies(composicion[2]),
                    composicion[3], FormateadorNumeros.comoGuaranies(composicion[3]),
                    composicion[4], FormateadorNumeros.comoGuaranies(composicion[4]),
                    pago.getMontoAplicado(), FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()),
                    obtenerNombreCajero(conexion, pago.getRegistradoPor()), revertido));
        }
        return filas;
    }

    /** Totales (<tfoot>) del informe de Cobros -- pura, por columna; los REVERTIDO quedan afuera
     * de la suma (pedido de Joel), aunque se siguen contando/mostrando en la lista de filas. */
    public py.com.capitalcredito.sgcpc.dto.TotalesCobros calcularTotalesCobros(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas) {
        int cantidadRevertidos = 0;
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal interes = BigDecimal.ZERO;
        BigDecimal moratorio = BigDecimal.ZERO;
        BigDecimal punitorio = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeCobro fila : filas) {
            if (fila.isRevertido()) {
                cantidadRevertidos++;
                continue;
            }
            capital = capital.add(fila.getCapital());
            interes = interes.add(fila.getInteres());
            moratorio = moratorio.add(fila.getMoratorio());
            punitorio = punitorio.add(fila.getPunitorio());
            iva = iva.add(fila.getIva());
            total = total.add(fila.getTotal());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesCobros(filas.size(), cantidadRevertidos,
                FormateadorNumeros.comoGuaranies(capital), FormateadorNumeros.comoGuaranies(interes),
                FormateadorNumeros.comoGuaranies(moratorio), FormateadorNumeros.comoGuaranies(punitorio),
                FormateadorNumeros.comoGuaranies(iva), FormateadorNumeros.comoGuaranies(total));
    }

    /** Informe "Reversiones" (D-128, ampliado Bloque C tanda 3 a pedido de Joel): todas las
     * solicitadas en el rango, cualquiera sea su estado -- con el recibo revertido, el monto,
     * quién solicitó, quién autorizó (si corresponde) y la nota de anulación (si la reversión
     * anuló una factura). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> listarReversionesPorRango(Connection conexion,
            LocalDate desde, LocalDate hasta) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas = new ArrayList<>();
        for (ReversionPago reversion : reversionPagoDAO.listarPorRango(conexion, desde, hasta)) {
            Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
            PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            boolean autorizada = reversion.getEstado() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion.AUTORIZADA;
            py.com.capitalcredito.sgcpc.modelos.Factura factura = facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
            String notaAnulacion = factura == null || factura.getNumeroNotaAnulacion() == null
                    ? "—" : factura.getNumeroNotaAnulacion();
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaReversionReporte(
                    FormateadorFechas.comoDdMmAaaa(reversion.getFechaHoraSolicitud().toLocalDate()),
                    prestamo.getIdPrestamo(), pago.getNumeroRecibo(), prestamo.getIdentificadorOperacion(),
                    cliente == null ? "-" : cliente.getNombres() + " " + cliente.getApellidos(),
                    FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()), pago.getMontoAplicado(),
                    reversion.getMotivoReversion(), reversion.getEstado().getEtiqueta(), autorizada,
                    obtenerNombreCajero(conexion, reversion.getSolicitadoPor()),
                    reversion.getAutorizadoPor() == null ? "—" : obtenerNombreCajero(conexion, reversion.getAutorizadoPor()),
                    notaAnulacion));
        }
        return filas;
    }

    /** Bloque C, tanda 3 (pedido de Joel; desglosado en C-3b): cantidad por estado y total de
     * montos REVERTIDOS -- pura, solo suma las AUTORIZADA (únicas donde el dinero realmente salió
     * de caja), sobre las filas ya armadas. */
    public py.com.capitalcredito.sgcpc.dto.TotalesReversiones calcularTotalesReversiones(
            List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        int autorizadas = 0;
        int rechazadas = 0;
        int solicitadas = 0;
        for (py.com.capitalcredito.sgcpc.dto.FilaReversionReporte fila : filas) {
            if (fila.isAutorizada()) {
                montoTotal = montoTotal.add(fila.getMontoCrudo());
                autorizadas++;
            } else if (py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion.RECHAZADA.getEtiqueta()
                    .equals(fila.getEstado())) {
                rechazadas++;
            } else if (py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion.SOLICITADA.getEtiqueta()
                    .equals(fila.getEstado())) {
                solicitadas++;
            }
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesReversiones(filas.size(), autorizadas, rechazadas, solicitadas,
                FormateadorNumeros.comoGuaranies(montoTotal));
    }

    /** Informe "Clientes nuevos" (D-128, ampliado Bloque C tanda 3 a pedido de Joel: ciudad y
     * situación laboral; C-4 paso 1: el estado real del último préstamo del cliente -- antes
     * mostraba "Sí"/"No" sobre ACTIVO/EN_MORA nada más, que confundía con un PENDIENTE_DESEMBOLSO
     * o un FINALIZADO (mostraban "No" igual que alguien sin ningún préstamo)). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> listarClientesNuevosPorRango(Connection conexion,
            LocalDate desde, LocalDate hasta) throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas = new ArrayList<>();
        for (Persona persona : personaDAO.listarPorRangoFechaRegistro(conexion, desde, hasta)) {
            // Normalización de personas, grupo Domicilio (16/10/2026): la ciudad ya no está en
            // Persona -- se lee de personas_domicilio.
            py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioPersona =
                    servicioClientes.obtenerDomicilio(conexion, persona.getIdPersona());
            py.com.capitalcredito.sgcpc.modelos.Ciudad ciudad = domicilioPersona == null
                    ? null : ciudadDAO.buscarPorId(conexion, domicilioPersona.getIdCiudad());
            // Normalización de personas, grupo Laborales (16/10/2026): la situación laboral ya no
            // está en Persona -- se lee de personas_laborales.
            py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralPersona =
                    servicioClientes.obtenerLaboral(conexion, persona.getIdPersona());
            PrestamoCredito ultimoPrestamo = prestamoDAO.obtenerUltimoPorCliente(conexion, persona.getIdPersona());
            String estadoUltimoPrestamo = ultimoPrestamo == null ? "—" : ultimoPrestamo.getEstadoPrestamo().getEtiqueta();
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo(persona.getIdPersona(),
                    FormateadorTexto.comoCedulaConPuntos(persona.getCedulaIdentidad()),
                    persona.getNombres() + " " + persona.getApellidos(),
                    FormateadorFechas.comoDdMmAaaa(persona.getFechaRegistro().toLocalDate()),
                    persona.getTelefonoWhatsapp(), ciudad == null ? "Sin ciudad" : ciudad.getNombre(),
                    ServicioClientes.etiquetaSituacionLaboral(laboralPersona == null ? null : laboralPersona.getSituacionLaboral()),
                    estadoUltimoPrestamo));
        }
        return filas;
    }

    /** Bloque C, tanda 3 (pedido de Joel): total y subtotales por ciudad de Clientes nuevos --
     * pura, sobre las filas ya armadas. Orden alfabético (TreeMap), mismo criterio que
     * ServicioInformes.calcularSubtotalesPorCiudad (informe de Clientes). */
    public py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos calcularTotalesClientesNuevos(
            List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas) {
        Map<String, Integer> porCiudad = new java.util.TreeMap<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo fila : filas) {
            porCiudad.merge(fila.getCiudad(), 1, Integer::sum);
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos(filas.size(), porCiudad);
    }

    /** E-12 (pedido de Joel): "TODOS / Devueltos / Pendientes de devolución" -- un valor de este
     * enum por cada opción del filtro de la pantalla. */
    public enum FiltroEstadoPagare { TODOS, DEVUELTOS, PENDIENTES }

    /** Informe "Cierres documentales" (D-128, ampliado E-12 a pedido de Joel): TODOS los
     * préstamos cancelados en el rango, con o sin el pagaré ya devuelto -- antes
     * (listarCierresDocumentalesCompletosPorRango) solo traía los que ya tenían
     * estado_cierre_documental = 'COMPLETO', dejando afuera a los que seguían PENDIENTE (o, como
     * de hecho pasa siempre hasta que se completa el cierre, en el estado inicial NO_APLICA --
     * ningún código de este sistema escribe 'PENDIENTE' en esa columna, ver E-12: la fila de
     * Jorge/CC-2025-00001 lo confirmó). "Devuelto" es exactamente estado_cierre_documental ==
     * 'COMPLETO'; cualquier otro valor ('NO_APLICA' o 'PENDIENTE') cuenta como pendiente, mismo
     * criterio que PrestamoDAO.listarFinalizadosConCierrePendiente (E9/Bloque 5). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> listarCierresDocumentalesPorRango(
            Connection conexion, LocalDate desde, LocalDate hasta, FiltroEstadoPagare filtroEstadoPagare)
            throws SQLException {
        List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarFinalizadosPorRangoCancelacion(conexion, desde, hasta)) {
            boolean pagareDevuelto = "COMPLETO".equals(prestamo.getEstadoCierreDocumental());
            if (filtroEstadoPagare == FiltroEstadoPagare.DEVUELTOS && !pagareDevuelto) {
                continue;
            }
            if (filtroEstadoPagare == FiltroEstadoPagare.PENDIENTES && pagareDevuelto) {
                continue;
            }
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            String fechaCancelacion = prestamo.getFechaFinalizacionFinanciera() == null ? "—"
                    : FormateadorFechas.comoDdMmAaaa(prestamo.getFechaFinalizacionFinanciera().toLocalDate());
            py.com.capitalcredito.sgcpc.modelos.LiquidacionAnticipada liquidacionTotal =
                    liquidacionAnticipadaDAO.obtenerTotalVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            String numeroConstanciaCancelacion = liquidacionTotal == null || liquidacionTotal.getNumeroConstancia() == null
                    ? "—" : liquidacionTotal.getNumeroConstancia();
            String numeroConstanciaDevolucion = pagareDevuelto
                    ? (prestamo.getNumeroConstanciaDevolucion() == null ? "—" : prestamo.getNumeroConstanciaDevolucion())
                    : "—";
            String fechaDevolucionPagare = pagareDevuelto && prestamo.getFechaCierreDocumental() != null
                    ? FormateadorFechas.comoDdMmAaaa(prestamo.getFechaCierreDocumental().toLocalDate()) : "—";
            String entregadoPorNombre = pagareDevuelto && prestamo.getCierreDocumentalPor() != null
                    ? obtenerNombreCajero(conexion, prestamo.getCierreDocumentalPor()) : "—";
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte(prestamo.getIdPrestamo(),
                    prestamo.getIdentificadorOperacion(),
                    cliente == null ? "—" : cliente.getNombres() + " " + cliente.getApellidos(),
                    fechaCancelacion, numeroConstanciaCancelacion, pagareDevuelto, numeroConstanciaDevolucion,
                    fechaDevolucionPagare, entregadoPorNombre));
        }
        return filas;
    }

    /** E-12 (pedido de Joel): cancelados/devueltos/pendientes -- pura, sobre las filas ya armadas
     * (si hay un filtro de estado del pagaré aplicado, refleja ESE subconjunto, mismo criterio
     * que el resumen por estado de Préstamos/Solicitudes, que también se arma sobre lo filtrado). */
    public py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales calcularTotalesCierresDocumentales(
            List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas) {
        int devueltos = 0;
        for (py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte fila : filas) {
            if (fila.isPagareDevuelto()) {
                devueltos++;
            }
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales(filas.size(), devueltos, filas.size() - devueltos);
    }

    private static final int[] HORIZONTES_PROYECCION = {30, 60, 90};

    /**
     * Proyección de fondos a 30/60/90 días (D-128): ingresos esperados = cuotas por vencer de
     * préstamos ACTIVO/EN_MORA (sin recalcular nada, mismos importes programados que Vencimientos)
     * dentro de cada horizonte, en forma acumulada. Egresos comprometidos = lo ya aprobado y
     * todavía sin desembolsar (solicitudes APROBADA sin formalizar + préstamos PENDIENTE_
     * DESEMBOLSO) -- sin fecha programada propia, se muestra igual en los tres horizontes (ver
     * FilaProyeccionFondos).
     */
    public List<FilaProyeccionFondos> calcularProyeccionFondos(Connection conexion) throws SQLException {
        LocalDate hoy = Reloj.hoy();
        BigDecimal[] ingresosPorHorizonte = new BigDecimal[HORIZONTES_PROYECCION.length];
        java.util.Arrays.fill(ingresosPorHorizonte, BigDecimal.ZERO);
        for (PrestamoCredito prestamo : prestamoDAO.listarActivosOEnMora(conexion)) {
            PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, prestamo.getIdPrestamo());
            if (plan == null) {
                continue;
            }
            for (Cuota cuota : cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago())) {
                if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                    continue;
                }
                if (cuota.getFechaVencimiento().isBefore(hoy)) {
                    continue;
                }
                for (int i = 0; i < HORIZONTES_PROYECCION.length; i++) {
                    if (!cuota.getFechaVencimiento().isAfter(hoy.plusDays(HORIZONTES_PROYECCION[i]))) {
                        ingresosPorHorizonte[i] = ingresosPorHorizonte[i].add(cuota.getTotalProgramado());
                    }
                }
            }
        }

        BigDecimal egresosComprometidos = BigDecimal.ZERO;
        for (SolicitudCredito solicitud : solicitudDAO.listarAprobadasSinFormalizar(conexion)) {
            egresosComprometidos = egresosComprometidos.add(solicitud.getMontoAprobado());
        }
        for (PrestamoCredito prestamo : prestamoDAO.listarPendientesDesembolso(conexion)) {
            egresosComprometidos = egresosComprometidos.add(prestamo.getMontoAprobado());
        }
        String egresosFormateados = FormateadorNumeros.comoGuaranies(egresosComprometidos);

        // Bloque 7/E10 (D-129, Obs. 30a): "posibles egresos" -- solicitudes todavía en trámite
        // (PENDIENTE_APROBACION, ni siquiera decididas), solo informativo -- NO entra en el
        // flujo neto (a diferencia de egresosComprometidos, que sí ya está aprobado).
        BigDecimal posiblesEgresosEnTramite = BigDecimal.ZERO;
        for (SolicitudCredito solicitud : solicitudDAO.listarPendientesAprobacion(conexion)) {
            posiblesEgresosEnTramite = posiblesEgresosEnTramite.add(solicitud.getMontoSolicitado());
        }
        String posiblesEgresosFormateados = FormateadorNumeros.comoGuaranies(posiblesEgresosEnTramite);

        List<FilaProyeccionFondos> filas = new ArrayList<>();
        for (int i = 0; i < HORIZONTES_PROYECCION.length; i++) {
            BigDecimal flujoNeto = ingresosPorHorizonte[i].subtract(egresosComprometidos);
            filas.add(new FilaProyeccionFondos(HORIZONTES_PROYECCION[i],
                    FormateadorNumeros.comoGuaranies(ingresosPorHorizonte[i]), egresosFormateados,
                    posiblesEgresosFormateados, FormateadorNumeros.comoGuaranies(flujoNeto),
                    flujoNeto.compareTo(BigDecimal.ZERO) < 0));
        }
        return filas;
    }

    public PrestamoCredito buscarPrestamoParaEstadoCuenta(Connection conexion, String textoBusqueda) throws SQLException {
        // D-117 (punto 21, pedido de Joel): dos bugs encontrados buscando por cédula --
        // (a) la búsqueda excluía FINALIZADO (el estado de cuenta SÍ tiene que poder consultar un
        // préstamo ya cancelado, a diferencia de la búsqueda de cobranza); (b) la cédula se
        // comparaba tal cual se escribía, sin normalizar (ServicioClientes.normalizarCedula, la
        // misma utilidad que ya usa el resto del sistema) -- "800.000.008" no encontraba nada
        // aunque "800000008" sí.
        List<PrestamoCredito> resultados = prestamoDAO.buscarActivosEnMoraOFinalizadosPorCedula(conexion,
                servicioClientes.normalizarCedula(textoBusqueda));
        if (!resultados.isEmpty()) {
            return resultados.get(0);
        }
        // B93 (30/10/2026, decisión de Joel): el número de operación se busca con el MISMO dueño
        // que usa Cobranza -- exacto sin importar mayúsculas y, si no hay, por fragmento con tope.
        // Antes acá se comparaba el texto tal cual se escribía y sin fragmento: "cc-2026-00005" no
        // encontraba nada (y sí en Cobranza, que lo pasaba a mayúsculas), y "00005" tampoco.
        List<PrestamoCredito> porOperacion = servicioCobranza.buscarPrestamosPorNumeroOperacion(conexion,
                textoBusqueda);
        if (porOperacion.size() == 1) {
            return porOperacion.get(0);
        }
        if (porOperacion.size() > 1) {
            // No se elige uno al azar: el fragmento es ambiguo y se le pide el número completo.
            throw new NegocioException(ServicioCobranza.mensajeNumeroOperacionAmbiguo(porOperacion.size()));
        }
        throw new NegocioException("Ningún préstamo con esos datos.");
    }

    // ======================================================================
    // C-4.2 (pedido de Joel): Usuarios y accesos -- pura, sobre la misma List<Usuario> que ya arma
    // ServicioUsuarios.listar (la que usa /usuarios); no hace ninguna consulta nueva a la base.
    // Nunca incluye el hash de la contraseña ni ningún otro dato de seguridad (pedido explícito).
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> construirFilasInformeUsuarios(
            List<py.com.capitalcredito.sgcpc.modelos.Usuario> usuarios) {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.modelos.Usuario usuario : usuarios) {
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario(usuario.getNombreUsuario(),
                    usuario.getNombres() + " " + usuario.getApellidos(),
                    FormateadorTexto.comoCedulaConPuntos(usuario.getCedulaIdentidad()),
                    usuario.getPerfilUsuario().getEtiqueta(), usuario.getEstadoCuenta().getEtiqueta(),
                    usuario.isDebeCambiarContrasena() ? "Sí" : "No",
                    usuario.getFechaUltimoAcceso() == null ? "Nunca"
                            : FormateadorFechas.comoDdMmAaaaHhMm(usuario.getFechaUltimoAcceso())));
        }
        return filas;
    }

    /** Totales por perfil y por estado de cuenta -- pura, sobre las filas ya armadas. Orden
     * alfabético (TreeMap), mismo criterio que el resto de los "por tipo" de este bloque. */
    public py.com.capitalcredito.sgcpc.dto.TotalesUsuarios calcularTotalesUsuarios(
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas) {
        Map<String, Integer> porPerfil = new java.util.TreeMap<>();
        Map<String, Integer> porEstado = new java.util.TreeMap<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario fila : filas) {
            porPerfil.merge(fila.getPerfil(), 1, Integer::sum);
            porEstado.merge(fila.getEstado(), 1, Integer::sum);
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesUsuarios(filas.size(), porPerfil, porEstado);
    }

    // ======================================================================
    // C-4.3 (pedido de Joel): Historial de parámetros -- solo lectura, nunca cambia nada en
    // parametros_sistema. El dato ya estaba completo (vigencias viejas cerradas con
    // vigente_hasta, nunca pisadas); esto solo lo expone con filtros, totales y PDF/CSV.
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> listarHistorialParametros(
            Connection conexion, LocalDate desde, LocalDate hasta, String claveParametro, boolean incluirIniciales)
            throws SQLException {
        return construirFilasHistorialParametros(
                parametroDAO.listarHistorial(conexion, desde, hasta, claveParametro, incluirIniciales));
    }

    /** Resuelve el formato de cada valor (ServicioParametros.formatearValorParametro, según el
     * tipo del parámetro) y los "—" de los campos opcionales -- pura, sobre las filas ya armadas
     * por ParametroDAO.listarHistorial (que sí toca la base).
     * <p>V1/RF39 (pedido de Joel): "valorAnteriorMostrado"/"valorNuevoMostrado" son los mismos
     * valores con el espacio después de cada coma (FormateadorTexto.comoListaConEspacios, único
     * lugar compartido con administracion.jsp) -- solo para la pantalla del historial y su PDF.
     * "valorAnterior"/"valorNuevo" quedan intactos (los usa el CSV, que exporta el valor
     * original, sin esta transformación). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> construirFilasHistorialParametros(
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo> crudas) {
        List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo cruda : crudas) {
            String valorAnterior = ServicioParametros.formatearValorParametro(cruda.getClaveParametro(),
                    cruda.getTipoValor(), cruda.getValorAnteriorCrudo());
            String valorNuevo = ServicioParametros.formatearValorParametro(cruda.getClaveParametro(),
                    cruda.getTipoValor(), cruda.getValorNuevoCrudo());
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro(cruda.getNombreParametro(),
                    valorAnterior, valorNuevo,
                    FormateadorFechas.comoDdMmAaaa(cruda.getVigenteDesde()),
                    cruda.getVigenteHasta() == null ? "Vigente" : FormateadorFechas.comoDdMmAaaa(cruda.getVigenteHasta()),
                    cruda.getModificadoPorNombre(), FormateadorFechas.comoDdMmAaaaHhMm(cruda.getFechaModificacion()),
                    cruda.getObservacion() == null || cruda.getObservacion().trim().isEmpty() ? "—" : cruda.getObservacion(),
                    cruda.getClaveParametro(),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoListaConEspacios(valorAnterior,
                            cruda.getTipoValor()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoListaConEspacios(valorNuevo,
                            cruda.getTipoValor())));
        }
        return filas;
    }

    /** Total de cambios y cantidad por parámetro -- pura, sobre las filas ya armadas (si hay un
     * filtro de parámetro aplicado, refleja ESE subconjunto). Orden alfabético (TreeMap). RF39
     * (pantalla rediseñada): la cantidad de parámetros distintos se cuenta por CLAVE (nunca
     * cambia), no por nombre (sí puede cambiar) -- para el total corto "N cambios en el período ·
     * M parámetros modificados". */
    public py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros calcularTotalesHistorialParametros(
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas) {
        Map<String, Integer> porParametro = new java.util.TreeMap<>();
        java.util.Set<String> clavesDistintas = new java.util.HashSet<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila : filas) {
            porParametro.merge(fila.getParametro(), 1, Integer::sum);
            clavesDistintas.add(fila.getClaveParametro());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros(filas.size(), clavesDistintas.size(),
                porParametro);
    }

    // ======================================================================
    // C-4.4 (pedido de Joel): Consultas al buró -- último informe del bloque C. Solo lectura:
    // nunca cambia ninguna consulta ni consentimiento.
    // ======================================================================

    public List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> listarConsultasBuro(Connection conexion,
            LocalDate desde, LocalDate hasta, String clasificacionObtenida, String clienteUOperacion) throws SQLException {
        return construirFilasConsultasBuro(
                consultaCrediticiaDAO.listarParaInforme(conexion, desde, hasta, clasificacionObtenida), clienteUOperacion);
    }

    public List<String> listarClasificacionesBuroDistintas(Connection conexion) throws SQLException {
        return consultaCrediticiaDAO.listarClasificacionesDistintas(conexion);
    }

    /** Pura: formatea cada fila cruda para pantalla/PDF/CSV. "Sin clasificación" si no se cargó
     * clasificación; "(sin dato)" en autorización vigente cuando no hay un valor histórico del
     * parámetro para esa fecha (ver el comentario de ConsultaCrediticiaDAO.listarParaInforme). */
    public List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> construirFilasConsultasBuro(
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo> crudas, String clienteUOperacion) {
        List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo cruda : crudas) {
            // 13/10/2026 (pedido de Joel): "Cliente u operación" -- la "operación" acá es el
            // código de la SOLICITUD (todavía no hay préstamo al momento de la consulta al buró).
            if (!coincideClienteUOperacion(clienteUOperacion, cruda.getCodigoSolicitud(), cruda.getCedulaPersona(),
                    cruda.getNombreCompletoPersona())) {
                continue;
            }
            String resultado = cruda.getClasificacionObtenida() == null || cruda.getClasificacionObtenida().trim().isEmpty()
                    ? "Sin clasificación" : cruda.getClasificacionObtenida();
            String autorizacionVigente = cruda.getAutorizacionVigenteEnEseMomento() == null ? "(sin dato)"
                    : (cruda.getAutorizacionVigenteEnEseMomento() ? "Sí" : "No");
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro(cruda.getIdPersona(),
                    FormateadorFechas.comoDdMmAaaaHhMm(cruda.getFechaHoraConsulta()), cruda.getNombreCompletoPersona(),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoCedulaConPuntos(cruda.getCedulaPersona()),
                    cruda.getCodigoSolicitud() == null ? "—" : cruda.getCodigoSolicitud(), resultado,
                    cruda.getNombreCompletoUsuario(), autorizacionVigente));
        }
        return filas;
    }

    /** Cantidad total, por resultado (TreeMap, orden alfabético) y de clientes distintos -- pura,
     * sobre las filas ya armadas (idPersona ya viaja en FilaConsultaBuro para el enlace a la
     * ficha del cliente, así que alcanza con esta sola lista). */
    public py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro calcularTotalesConsultasBuro(
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas) {
        Map<String, Integer> porResultado = new java.util.TreeMap<>();
        java.util.Set<Long> clientesDistintos = new java.util.HashSet<>();
        for (py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro fila : filas) {
            porResultado.merge(fila.getResultado(), 1, Integer::sum);
            clientesDistintos.add(fila.getIdPersona());
        }
        return new py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro(filas.size(), porResultado, clientesDistintos.size());
    }
}
