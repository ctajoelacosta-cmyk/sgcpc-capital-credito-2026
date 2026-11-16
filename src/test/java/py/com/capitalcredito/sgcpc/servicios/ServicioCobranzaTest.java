package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.CoincidenciaBusquedaCobranza;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.LineaFactura;
import py.com.capitalcredito.sgcpc.dto.TotalesLineasFactura;
import py.com.capitalcredito.sgcpc.dto.ListaTrabajoCobranza;
import py.com.capitalcredito.sgcpc.dto.ResultadoAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoBusquedaCobranza;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * E7 (P09, D-77 punto 18): el puente PrestamoCredito+Cuotas (base) -> Prestamo (motor), con el
 * plan REAL de CC-2026-00014 (sgcpc_db, id_prestamo 14, leído por SELECT de solo lectura al
 * planificar E7 -- ver AVANCE.md), no datos inventados. Valores exactos de cada cuota: capital,
 * interés, IVA, total y saldo posterior.
 */
public class ServicioCobranzaTest {

    private final ServicioCobranza servicio = new ServicioCobranza();

    // Datos reales de prestamos (id_prestamo = 14, identificador_operacion = CC-2026-00014).
    private static final BigDecimal MONTO_APROBADO = new BigDecimal("3000000");
    private static final BigDecimal TASA_TNA = new BigDecimal("0.241413");
    private static final BigDecimal TASA_TEA = new BigDecimal("0.270000");
    private static final BigDecimal TASA_MENSUAL = new BigDecimal("0.020118");
    private static final BigDecimal TASA_MORATORIA = new BigDecimal("0.270000");
    private static final BigDecimal TASA_PUNITORIA = new BigDecimal("0.081000");
    private static final LocalDate FECHA_DESEMBOLSO = LocalDate.of(2026, 9, 26);

    // Datos reales de cuotas (numero, capital, interes, tributos, total, saldo_capital_posterior).
    private static final long[][] PLAN_REAL = {
        {1, 223531, 60353, 6035, 289919, 2776469},
        {2, 228028, 55856, 5586, 289470, 2548441},
        {3, 232615, 51269, 5127, 289011, 2315826},
        {4, 237295, 46589, 4659, 288543, 2078531},
        {5, 242069, 41815, 4182, 288066, 1836462},
        {6, 246938, 36946, 3695, 287579, 1589524},
        {7, 251906, 31978, 3198, 287082, 1337618},
        {8, 256974, 26910, 2691, 286575, 1080644},
        {9, 262144, 21740, 2174, 286058, 818500},
        {10, 267418, 16466, 1647, 285531, 551082},
        {11, 272797, 11087, 1109, 284993, 278285},
        {12, 278285, 5598, 560, 284443, 0},
    };
    private static final long TOTAL_A_PAGAR_REAL = 3447270;

    private PrestamoCredito prestamoCreditoCC14() {
        PrestamoCredito prestamoCredito = new PrestamoCredito();
        prestamoCredito.setMontoAprobado(MONTO_APROBADO);
        prestamoCredito.setTasaTna(TASA_TNA);
        prestamoCredito.setTasaTea(TASA_TEA);
        prestamoCredito.setTasaMensual(TASA_MENSUAL);
        prestamoCredito.setTasaMoratoria(TASA_MORATORIA);
        prestamoCredito.setTasaPunitoria(TASA_PUNITORIA);
        prestamoCredito.setFechaDesembolso(FECHA_DESEMBOLSO);
        return prestamoCredito;
    }

    private List<Cuota> cuotasCC14() {
        List<Cuota> cuotas = new ArrayList<>();
        for (long[] fila : PLAN_REAL) {
            Cuota cuota = new Cuota();
            cuota.setIdCuota(fila[0]);
            cuota.setNumeroCuota((int) fila[0]);
            cuota.setCapitalProgramado(BigDecimal.valueOf(fila[1]));
            cuota.setInteresProgramado(BigDecimal.valueOf(fila[2]));
            cuota.setTributosProgramados(BigDecimal.valueOf(fila[3]));
            cuota.setTotalProgramado(BigDecimal.valueOf(fila[4]));
            cuota.setSaldoCapitalPosterior(BigDecimal.valueOf(fila[5]));
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            cuotas.add(cuota);
        }
        return cuotas;
    }

    @Test
    public void testConstruirPrestamoParaMotorCopiaLasTasasYLaFechaDeDesembolso() {
        Prestamo prestamo = servicio.construirPrestamoParaMotor(prestamoCreditoCC14(), cuotasCC14());

        assertEquals(FECHA_DESEMBOLSO, prestamo.getFechaDesembolso());
        assertEquals(0, TASA_TNA.compareTo(prestamo.getTasaTna()));
        assertEquals(0, TASA_TEA.compareTo(prestamo.getTasaTea()));
        assertEquals(0, TASA_MENSUAL.compareTo(prestamo.getTasaMensual()));
        assertEquals(0, TASA_MORATORIA.compareTo(prestamo.getTasaMoratoria()));
        assertEquals(0, TASA_PUNITORIA.compareTo(prestamo.getTasaPunitoria()));
    }

    @Test
    public void testConstruirPrestamoParaMotorConservaCapitalInteresIvaYTotalDeCadaCuota() {
        Prestamo prestamo = servicio.construirPrestamoParaMotor(prestamoCreditoCC14(), cuotasCC14());

        assertEquals(12, prestamo.getCuotas().size());
        BigDecimal totalPlan = BigDecimal.ZERO;
        for (int i = 0; i < PLAN_REAL.length; i++) {
            long[] fila = PLAN_REAL[i];
            Cuota cuota = prestamo.getCuotas().get(i);
            assertEquals("cuota " + fila[0] + ": capital", fila[1], cuota.getCapitalProgramado().longValueExact());
            assertEquals("cuota " + fila[0] + ": interés", fila[2], cuota.getInteresProgramado().longValueExact());
            assertEquals("cuota " + fila[0] + ": IVA", fila[3], cuota.getTributosProgramados().longValueExact());
            assertEquals("cuota " + fila[0] + ": total", fila[4], cuota.getTotalProgramado().longValueExact());
            assertEquals("cuota " + fila[0] + ": saldo posterior", fila[5],
                    cuota.getSaldoCapitalPosterior().longValueExact());
            totalPlan = totalPlan.add(cuota.getTotalProgramado());
        }
        assertEquals(TOTAL_A_PAGAR_REAL, totalPlan.longValueExact());
    }

    private Cuota cuota(int numero, LocalDate vencimiento, EstadoPago estado) {
        Cuota cuota = new Cuota();
        cuota.setIdCuota((long) numero);
        cuota.setNumeroCuota(numero);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setFechaVencimientoTeorica(vencimiento);
        cuota.setCapitalProgramado(BigDecimal.valueOf(100000));
        cuota.setInteresProgramado(BigDecimal.ZERO);
        cuota.setTributosProgramados(BigDecimal.ZERO);
        cuota.setTotalProgramado(BigDecimal.valueOf(100000));
        cuota.setSaldoCapitalPosterior(BigDecimal.ZERO);
        cuota.setEstadoPago(estado);
        if (estado == EstadoPago.PAGADA) {
            cuota.setCapitalPagado(BigDecimal.valueOf(100000));
        }
        return cuota;
    }

    private Prestamo prestamoConCuotas(Cuota... cuotas) {
        Prestamo prestamo = new Prestamo();
        prestamo.setCuotas(new ArrayList<>(Arrays.asList(cuotas)));
        return prestamo;
    }

    /** D-77 punto 5: sin ninguna cuota vencida -> ACTIVO. */
    @Test
    public void testEstadoActivoSinCuotasVencidas() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        Prestamo prestamo = prestamoConCuotas(
                cuota(1, LocalDate.of(2026, 10, 1), EstadoPago.PAGADA),
                cuota(2, LocalDate.of(2026, 12, 1), EstadoPago.PENDIENTE));

        assertEquals(EstadoPrestamo.ACTIVO, servicio.calcularEstadoSegunCuotas(prestamo, hoy));
    }

    /** D-77 punto 5: con alguna cuota vencida y sin pagar -> EN_MORA. */
    @Test
    public void testEstadoEnMoraConCuotaVencida() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        Prestamo prestamo = prestamoConCuotas(
                cuota(1, LocalDate.of(2026, 10, 1), EstadoPago.PENDIENTE),
                cuota(2, LocalDate.of(2026, 12, 1), EstadoPago.PENDIENTE));

        assertEquals(EstadoPrestamo.EN_MORA, servicio.calcularEstadoSegunCuotas(prestamo, hoy));
    }

    /** RN-12: sin ninguna cuota pendiente -> FINALIZADO. */
    @Test
    public void testEstadoFinalizadoSinCuotasPendientes() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        Prestamo prestamo = prestamoConCuotas(
                cuota(1, LocalDate.of(2026, 10, 1), EstadoPago.PAGADA),
                cuota(2, LocalDate.of(2026, 9, 1), EstadoPago.PAGADA));

        assertEquals(EstadoPrestamo.FINALIZADO, servicio.calcularEstadoSegunCuotas(prestamo, hoy));
    }

    /**
     * Prueba de pregunta de Joel (2026-09-28): no alcanza con probar calcularEstadoSegunCuotas en
     * aislamiento (ya cubierto arriba) -- hay que probar el mismo encadenamiento que usa
     * confirmarCobro: distribuirPago (motor real) -> reconstruirConCuotasActualizadas ->
     * calcularEstadoSegunCuotas, para un cobro normal que paga la última cuota pendiente.
     */
    @Test
    public void testCobroQuePagaLaUltimaCuotaPendienteDaEstadoFinalizado() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        Cuota cuota1 = cuota(1, LocalDate.of(2026, 10, 1), EstadoPago.PAGADA);
        Cuota cuota2 = cuota(2, LocalDate.of(2026, 10, 20), EstadoPago.PENDIENTE);
        Prestamo prestamo = prestamoConCuotas(cuota1, cuota2);
        prestamo.setTasaMoratoria(BigDecimal.ZERO);
        prestamo.setTasaPunitoria(BigDecimal.ZERO);

        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        ResultadoAplicacionPago aplicacion = motor.distribuirPago(prestamo, cuota2.getTotalProgramado(), hoy, 0);
        Prestamo prestamoActualizado = servicio.reconstruirConCuotasActualizadas(prestamo, aplicacion);

        assertEquals(EstadoPago.PAGADA, prestamoActualizado.getCuotas().get(1).getEstadoPago());
        assertEquals(EstadoPrestamo.FINALIZADO, servicio.calcularEstadoSegunCuotas(prestamoActualizado, hoy));
    }

    // D-77 punto 15 (antes caso 9 de humo_http.sh: mover a JUnit con fechas fijas, no se puede
    // forzar por HTTP sin cambiar parametros_sistema reales).
    private static final LocalDate TIMBRADO_DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate TIMBRADO_HASTA = LocalDate.of(2026, 12, 31);

    @Test
    public void testTimbradoVigenteDentroDelRangoNoLanza() {
        servicio.validarTimbradoVigente(LocalDate.of(2026, 6, 15), TIMBRADO_DESDE, TIMBRADO_HASTA);
    }

    @Test
    public void testTimbradoVigenteEnLosBordesNoLanza() {
        servicio.validarTimbradoVigente(TIMBRADO_DESDE, TIMBRADO_DESDE, TIMBRADO_HASTA);
        servicio.validarTimbradoVigente(TIMBRADO_HASTA, TIMBRADO_DESDE, TIMBRADO_HASTA);
    }

    @Test(expected = NegocioException.class)
    public void testTimbradoAntesDeLaVigenciaLanza() {
        servicio.validarTimbradoVigente(LocalDate.of(2025, 12, 31), TIMBRADO_DESDE, TIMBRADO_HASTA);
    }

    @Test(expected = NegocioException.class)
    public void testTimbradoDespuesDeLaVigenciaLanza() {
        servicio.validarTimbradoVigente(LocalDate.of(2027, 1, 1), TIMBRADO_DESDE, TIMBRADO_HASTA);
    }

    // D-89: timbrado "agotado" (cantidad), separado de "vencido" (fecha) de arriba -- misma idea,
    // pura y con valores fijos. proximoNumero es el que emitirFactura tomaría a continuación
    // (valorActual + 1 del numerador), nunca el ya emitido.

    private static final long NUMERO_DESDE = 1L;
    private static final long NUMERO_HASTA = 9999999L;

    @Test
    public void testTimbradoDisponibleDentroDelRangoNoLanza() {
        servicio.validarTimbradoDisponible(500L, NUMERO_DESDE, NUMERO_HASTA);
    }

    @Test
    public void testTimbradoDisponibleEnLosBordesNoLanza() {
        servicio.validarTimbradoDisponible(NUMERO_DESDE, NUMERO_DESDE, NUMERO_HASTA);
        servicio.validarTimbradoDisponible(NUMERO_HASTA, NUMERO_DESDE, NUMERO_HASTA);
    }

    @Test
    public void testTimbradoAgotadoLanzaConElMensajeExacto() {
        try {
            servicio.validarTimbradoDisponible(NUMERO_HASTA + 1, NUMERO_DESDE, NUMERO_HASTA);
            org.junit.Assert.fail("Debía lanzar NegocioException.");
        } catch (NegocioException error) {
            assertEquals("Timbrado agotado: se alcanzó el último número autorizado (hasta " + NUMERO_HASTA
                    + "). Solicite un nuevo timbrado.", error.getMessage());
        }
    }

    @Test(expected = NegocioException.class)
    public void testTimbradoPorDebajoDelRangoLanza() {
        // ej.: un timbrado nuevo autorizado a partir de un numero_desde > 1 y el numerador
        // todavía en 0 (arranque, D-89 punto "validá también el límite desde").
        servicio.validarTimbradoDisponible(500L, 501L, NUMERO_HASTA);
    }

    // Bloque 6 (PA-13): aviso previo al rechazo duro de arriba -- misma idea de pura + valores
    // fijos, límite justo en umbralAviso (restantes == umbral avisa; restantes == umbral + 1 no).

    @Test
    public void testAvisoNumeroFacturaUnAntesDelLimiteNoAvisa() {
        // restantes = NUMERO_HASTA - proximoNumero + 1 = 501 (uno más que el umbral de 500).
        assertEquals(null, servicio.evaluarAvisoNumeroFactura(NUMERO_HASTA - 500L, NUMERO_HASTA, 500L));
    }

    @Test
    public void testAvisoNumeroFacturaJustoEnElLimiteAvisa() {
        // restantes = 500, igual al umbral -- ya avisa.
        String aviso = servicio.evaluarAvisoNumeroFactura(NUMERO_HASTA - 499L, NUMERO_HASTA, 500L);
        assertEquals("Quedan 500 números de factura (hasta " + NUMERO_HASTA + "). Solicite un nuevo timbrado.",
                aviso);
    }

    @Test
    public void testAvisoNumeroFacturaUnDespuesDelLimiteAvisaConMenosRestantes() {
        // restantes = 499, por debajo del umbral -- sigue avisando, con el número exacto.
        String aviso = servicio.evaluarAvisoNumeroFactura(NUMERO_HASTA - 498L, NUMERO_HASTA, 500L);
        assertEquals("Quedan 499 números de factura (hasta " + NUMERO_HASTA + "). Solicite un nuevo timbrado.",
                aviso);
    }

    @Test
    public void testAvisoVigenciaTimbradoUnDiaAntesDelLimiteNoAvisa() {
        // diasRestantes = 31, uno más que el umbral de 30.
        assertEquals(null, servicio.evaluarAvisoVigenciaTimbrado(
                TIMBRADO_HASTA.minusDays(31), TIMBRADO_HASTA, 30L));
    }

    @Test
    public void testAvisoVigenciaTimbradoJustoEnElLimiteAvisa() {
        // diasRestantes = 30, igual al umbral -- ya avisa.
        String aviso = servicio.evaluarAvisoVigenciaTimbrado(TIMBRADO_HASTA.minusDays(30), TIMBRADO_HASTA, 30L);
        assertEquals("El timbrado vence el " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(TIMBRADO_HASTA)
                + " (faltan 30 días). Solicite la renovación.", aviso);
    }

    @Test
    public void testAvisoVigenciaTimbradoUnDiaDespuesDelLimiteAvisaConMenosDias() {
        // diasRestantes = 29, por debajo del umbral -- sigue avisando, con el número exacto.
        String aviso = servicio.evaluarAvisoVigenciaTimbrado(TIMBRADO_HASTA.minusDays(29), TIMBRADO_HASTA, 30L);
        assertEquals("El timbrado vence el " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(TIMBRADO_HASTA)
                + " (faltan 29 días). Solicite la renovación.", aviso);
    }

    // D-89: única condición que decide si una operación factura algo -- la prueba directamente
    // en vez de fakear NumeradorDAO/las otras DAO que confirmarCobro/confirmarCancelacionTotal/
    // confirmarAnticipoCapital tocan (alternativa más chica, aprobada por Joel).

    @Test
    public void testCorrespondeEmitirFacturaConInteresEsVerdadero() {
        // Ej.: un cobro con intereses -- llamaría 1 vez a numeradorDAO.siguienteValor(FACTURA).
        org.junit.Assert.assertTrue(servicio.correspondeEmitirFactura(new BigDecimal("12345")));
    }

    @Test
    public void testCorrespondeEmitirFacturaSinConceptosGravadosEsFalso() {
        // Ej.: un anticipo con el período en curso ya pagado (D-86/D-88), o una cancelación sin
        // conceptos gravados -- nunca llaman a siguienteValor(FACTURA).
        org.junit.Assert.assertFalse(servicio.correspondeEmitirFactura(BigDecimal.ZERO));
    }

    // D-85 (b): control antes de emitir la factura -- la suma de las líneas (y de sus IVA)
    // tiene que coincidir EXACTO con los totales ya calculados, o no se emite nada.

    @Test
    public void testValidarLineasFacturaQueCuadranNoLanza() {
        List<LineaFactura> lineas = Arrays.asList(
                new LineaFactura("Intereses cuota 1 — CC-2026-00023", new BigDecimal("60353"), new BigDecimal("6035")),
                new LineaFactura("Interés moratorio cuota 1 — CC-2026-00023", new BigDecimal("5632"), new BigDecimal("563")));
        BigDecimal totalGravadoLineas = new BigDecimal("72583"); // 60353+6035+5632+563
        BigDecimal totalIvaLineas = new BigDecimal("6598"); // 6035+563

        ServicioCobranza.validarLineasFactura(lineas, totalGravadoLineas, totalIvaLineas,
                totalGravadoLineas, totalIvaLineas);
    }

    @Test(expected = NegocioException.class)
    public void testValidarLineasFacturaQueNoCuadranEnElGravadoLanza() {
        List<LineaFactura> lineas = Arrays.asList(
                new LineaFactura("Intereses cuota 1 — CC-2026-00023", new BigDecimal("60353"), new BigDecimal("6035")));

        // Las líneas suman 66.388, pero el total esperado (el que ya gatilló la validación del
        // timbrado, más arriba) dice 66.389 -- un guaraní de diferencia alcanza para rechazar.
        ServicioCobranza.validarLineasFactura(lineas, new BigDecimal("66388"), new BigDecimal("6035"),
                new BigDecimal("66389"), new BigDecimal("6035"));
    }

    @Test(expected = NegocioException.class)
    public void testValidarLineasFacturaQueNoCuadranEnElIvaLanza() {
        List<LineaFactura> lineas = Arrays.asList(
                new LineaFactura("Intereses cuota 1 — CC-2026-00023", new BigDecimal("60353"), new BigDecimal("6035")));

        ServicioCobranza.validarLineasFactura(lineas, new BigDecimal("66388"), new BigDecimal("6035"),
                new BigDecimal("66388"), new BigDecimal("6036"));
    }

    // D-93 (E8, reimpresión de factura): derivarLineasCuotaFactura reconstruye las líneas de una
    // factura ya emitida a partir de lo guardado en aplicaciones_pago -- estos 3 casos usan los
    // valores REALES de la factura 001-001-0000003 (pago id 5 de CC-2026-90001, verificados por
    // SELECT antes de escribir el código), tal como aparecen en docs/diseno_comprobantes_E8.md
    // sección 1: cada línea derivada tiene que coincidir exacta con el ejemplo del documento.

    @Test
    public void testDerivarLineasCuotaFacturaCuota1DeCC90001ReproduceElEjemploDelDocumento() {
        List<LineaFactura> lineas = ServicioCobranza.derivarLineasCuotaFactura(1, 12,
                new BigDecimal("60353"), new BigDecimal("5782"), new BigDecimal("1735"), new BigDecimal("6787"));

        assertEquals(3, lineas.size());
        assertEquals("Interés compensatorio cuota 1/12", lineas.get(0).getDescripcion());
        assertEquals(0, new BigDecimal("66388").compareTo(lineas.get(0).getTotal())); // 60.353 + 6.035
        assertEquals("Interés moratorio cuota 1/12", lineas.get(1).getDescripcion());
        assertEquals(0, new BigDecimal("6360").compareTo(lineas.get(1).getTotal())); // 5.782 + 578
        assertEquals("Interés punitorio cuota 1/12", lineas.get(2).getDescripcion());
        assertEquals(0, new BigDecimal("1909").compareTo(lineas.get(2).getTotal())); // 1.735 + 174
    }

    @Test
    public void testDerivarLineasCuotaFacturaCuota2DeCC90001ReproduceElEjemploDelDocumento() {
        List<LineaFactura> lineas = ServicioCobranza.derivarLineasCuotaFactura(2, 12,
                new BigDecimal("55856"), new BigDecimal("898"), new BigDecimal("269"), new BigDecimal("5703"));

        assertEquals(3, lineas.size());
        assertEquals(0, new BigDecimal("61442").compareTo(lineas.get(0).getTotal())); // 55.856 + 5.586
        assertEquals(0, new BigDecimal("988").compareTo(lineas.get(1).getTotal())); // 898 + 90
        assertEquals(0, new BigDecimal("296").compareTo(lineas.get(2).getTotal())); // 269 + 27
    }

    @Test
    public void testDerivarLineasCuotaFacturaSinMoraOmiteLasLineasEnCero() {
        // Cuota 3 de CC-2026-90001: sin moratorio ni punitorio (nunca estuvo vencida) -- una
        // línea se omite si su monto es 0 (regla del documento), no una línea con "0 Gs.".
        List<LineaFactura> lineas = ServicioCobranza.derivarLineasCuotaFactura(3, 12,
                new BigDecimal("51269"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5127"));

        assertEquals(1, lineas.size());
        assertEquals("Interés compensatorio cuota 3/12", lineas.get(0).getDescripcion());
        assertEquals(0, new BigDecimal("56396").compareTo(lineas.get(0).getTotal())); // 51.269 + 5.127
    }

    @Test(expected = IllegalStateException.class)
    public void testDerivarLineasCuotaFacturaConIvaGuardadoQueNoCuadraLanza() {
        // El IVA derivado de 60.353 (interés) es 6.035 -- si lo guardado dice 6.036 (un guaraní
        // de diferencia), no se inventa el número: corta con error antes de reimprimir nada.
        ServicioCobranza.derivarLineasCuotaFactura(1, 12, new BigDecimal("60353"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("6036"));
    }

    // ==================================================================
    // D-145 (E-2, pedido de Joel): un adelanto de capital con interés del período en curso SÍ
    // factura un renglón ("Interés compensatorio hasta el dd/mm/aaaa (anticipo de capital)") --
    // antes, reconstruirLineasFactura saltaba por completo las aplicaciones ANTICIPO_CAPITAL al
    // reimprimir/mostrar (bug real: factura 001-001-0000011, CC-2026-00004 -- subtotal e IVA
    // correctos en los totales, pero sin ningún renglón en la tabla).
    // ==================================================================

    @Test
    public void testDerivarLineasAnticipoFacturaConInteresGeneraUnRenglonQueSumaElTotal() {
        LocalDate fecha = LocalDate.of(2026, 10, 2);
        BigDecimal interes = new BigDecimal("23983");
        BigDecimal ivaInteres = new BigDecimal("2398"); // ServicioMotorFinanciero.iva(23983) = 2398

        List<LineaFactura> lineas = ServicioCobranza.derivarLineasAnticipoFactura(fecha, interes, BigDecimal.ZERO,
                BigDecimal.ZERO, ivaInteres);

        assertEquals(1, lineas.size());
        assertEquals("Interés compensatorio hasta el 02/10/2026 (anticipo de capital)",
                lineas.get(0).getDescripcion());

        BigDecimal sumaRenglones = BigDecimal.ZERO;
        for (LineaFactura linea : lineas) {
            sumaRenglones = sumaRenglones.add(linea.getTotal());
        }
        assertEquals(0, interes.add(ivaInteres).compareTo(sumaRenglones));
    }

    @Test
    public void testDerivarLineasAnticipoFacturaConMoratorioYPunitorioGeneraUnRenglonCadaUno() {
        // D-90 hace que un adelanto normalmente no tenga vencidas (y por lo tanto, sin mora),
        // pero la reconstrucción admite moratorio/punitorio con el mismo criterio que un cobro
        // normal, por si alguna vez corresponde.
        LocalDate fecha = LocalDate.of(2026, 10, 2);
        BigDecimal interes = new BigDecimal("23983");
        BigDecimal moratorio = new BigDecimal("1000");
        BigDecimal punitorio = new BigDecimal("300");
        BigDecimal ivaGuardado = new BigDecimal("2528"); // 2398 (interés) + 100 (morat.) + 30 (punit.)

        List<LineaFactura> lineas = ServicioCobranza.derivarLineasAnticipoFactura(fecha, interes, moratorio,
                punitorio, ivaGuardado);

        assertEquals(3, lineas.size());
        assertEquals("Interés compensatorio hasta el 02/10/2026 (anticipo de capital)",
                lineas.get(0).getDescripcion());
        assertEquals("Interés moratorio hasta el 02/10/2026 (anticipo de capital)", lineas.get(1).getDescripcion());
        assertEquals("Interés punitorio hasta el 02/10/2026 (anticipo de capital)", lineas.get(2).getDescripcion());

        BigDecimal sumaRenglones = BigDecimal.ZERO;
        for (LineaFactura linea : lineas) {
            sumaRenglones = sumaRenglones.add(linea.getTotal());
        }
        assertEquals(0, interes.add(moratorio).add(punitorio).add(ivaGuardado).compareTo(sumaRenglones));
    }

    @Test(expected = IllegalStateException.class)
    public void testDerivarLineasAnticipoFacturaConIvaGuardadoQueNoCuadraLanza() {
        // El IVA derivado de 23.983 (interés) es 2.398 -- si lo guardado dice 2.399 (un guaraní
        // de diferencia), no se inventa el número: corta con error antes de reimprimir nada.
        ServicioCobranza.derivarLineasAnticipoFactura(LocalDate.of(2026, 10, 2), new BigDecimal("23983"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2399"));
    }

    // Bloque 4 (E8, decisión 1, ampliada por Joel): promesa cumplida = (a) tras el cobro el
    // préstamo queda sin cuotas vencidas, O (b) lo pagado entre la gestión y la fecha de promesa
    // (inclusive) alcanza el monto prometido. montoPrometido/sumaEnVentana son solo relevantes
    // para (b) -- si (a) ya es true, ni se miran (evaluarPromesaCumplida corta antes).

    @Test
    public void testEvaluarPromesaCumplidaPorCondicionASinCuotasVencidas() {
        py.com.capitalcredito.sgcpc.modelos.GestionCobranza gestion = new py.com.capitalcredito.sgcpc.modelos.GestionCobranza();
        gestion.setMontoPrometido(new BigDecimal("500000"));

        // sinCuotasVencidas = true alcanza por sí solo, aunque lo pagado en la ventana sea 0.
        assertEquals(true, ServicioCobranza.evaluarPromesaCumplida(gestion, true, BigDecimal.ZERO));
    }

    @Test
    public void testEvaluarPromesaCumplidaPorCondicionBMontoAlcanzado() {
        py.com.capitalcredito.sgcpc.modelos.GestionCobranza gestion = new py.com.capitalcredito.sgcpc.modelos.GestionCobranza();
        gestion.setMontoPrometido(new BigDecimal("300000"));

        // Sigue con cuotas vencidas (sinCuotasVencidas = false), pero lo pagado en la ventana
        // (300.000) alcanza exactamente lo prometido -- cumple por (b).
        assertEquals(true,
                ServicioCobranza.evaluarPromesaCumplida(gestion, false, new BigDecimal("300000")));
        // También por encima del monto prometido.
        assertEquals(true,
                ServicioCobranza.evaluarPromesaCumplida(gestion, false, new BigDecimal("350000")));
    }

    @Test
    public void testEvaluarPromesaCumplidaPagoMenorQueLoPrometidoSigueVencida() {
        py.com.capitalcredito.sgcpc.modelos.GestionCobranza gestion = new py.com.capitalcredito.sgcpc.modelos.GestionCobranza();
        gestion.setMontoPrometido(new BigDecimal("300000"));

        // Pagó menos de lo prometido y el préstamo sigue con cuotas vencidas: no cumple.
        assertEquals(false,
                ServicioCobranza.evaluarPromesaCumplida(gestion, false, new BigDecimal("299999")));
    }

    // ==================================================================
    // D-141 (SEDECO, pedido de Joel): redondeo del efectivo hacia abajo, a favor del cliente.
    // ==================================================================

    @Test
    public void testCalcularRedondeoEfectivo_ParametroEnCero_SinRedondeo() {
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo resultado =
                servicio.calcularRedondeoEfectivo(new BigDecimal("289919"), new BigDecimal("289919"), 0);
        assertEquals(false, resultado.isAplicaRedondeo());
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoACobrar()));
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoAplicado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getMontoVuelto()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getMontoRedondeo()));
    }

    @Test
    public void testCalcularRedondeoEfectivo_ClientePagaElMontoYaRedondeado() {
        // 289.919 redondeado hacia abajo al múltiplo de 50 = 289.900 (resto 19).
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo resultado =
                servicio.calcularRedondeoEfectivo(new BigDecimal("289919"), new BigDecimal("289900"), 50);
        assertEquals(true, resultado.isAplicaRedondeo());
        assertEquals(0, new BigDecimal("289900").compareTo(resultado.getMontoACobrar()));
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoAplicado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getMontoVuelto()));
        assertEquals(0, new BigDecimal("19").compareTo(resultado.getMontoRedondeo()));
    }

    @Test
    public void testCalcularRedondeoEfectivo_ClientePagaDeMasVueltoSobreElRedondeado() {
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo resultado =
                servicio.calcularRedondeoEfectivo(new BigDecimal("289919"), new BigDecimal("300000"), 50);
        assertEquals(true, resultado.isAplicaRedondeo());
        assertEquals(0, new BigDecimal("289900").compareTo(resultado.getMontoACobrar()));
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoAplicado()));
        // Vuelto sobre el monto YA redondeado (300.000 - 289.900), no sobre el exacto.
        assertEquals(0, new BigDecimal("10100").compareTo(resultado.getMontoVuelto()));
        assertEquals(0, new BigDecimal("19").compareTo(resultado.getMontoRedondeo()));
    }

    @Test
    public void testCalcularRedondeoEfectivo_ExactoMultiploDelParametro_SinRedondeo() {
        // 300.000 ya es múltiplo de 50 -- nada que redondear.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo resultado =
                servicio.calcularRedondeoEfectivo(new BigDecimal("300000"), new BigDecimal("300000"), 50);
        assertEquals(false, resultado.isAplicaRedondeo());
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.getMontoRedondeo()));
    }

    @Test
    public void testCalcularRedondeoEfectivo_NoAlcanzaNiElMontoRedondeado_SinRedondeo() {
        // Pagó menos que el propio monto redondeado (289.900) -- sigue el camino de siempre
        // (parcial/con promesa), sin redondeo.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo resultado =
                servicio.calcularRedondeoEfectivo(new BigDecimal("289919"), new BigDecimal("200000"), 50);
        assertEquals(false, resultado.isAplicaRedondeo());
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoACobrar()));
        assertEquals(0, new BigDecimal("289919").compareTo(resultado.getMontoAplicado()));
    }

    // ==================================================================
    // B115 (05/11/2026, redacción elegida por Joel): "lo recibido no alcanza para N cuotas" tiene
    // un solo dueño. Antes estaba escrita en ControladorCobranzaServlet Y en cobranza-ficha.js, y
    // las dos ya decían cosas distintas. La única diferencia permitida entre la de la pantalla y
    // la del servidor es el fragmento de la cantidad -- eso es lo que fija el último test de acá.
    // ==================================================================

    @Test
    public void testTextoNoAlcanzaParaLasCuotas_NombraLaCantidadEnSingularYEnPlural() {
        assertEquals("Lo recibido (500.000 Gs.) no alcanza para 3 cuotas (697.950 Gs.)."
                        + " Elija menos cuotas o use «Por monto».",
                ServicioCobranza.textoNoAlcanzaParaLasCuotas("500.000 Gs.", "3", true, "697.950 Gs."));
        // B117 (05/11/2026): con 1 cuota el consejo NO ofrece elegir menos -- menos de 1 no existe.
        assertEquals("Lo recibido (100.000 Gs.) no alcanza para 1 cuota (289.470 Gs.)."
                        + " Use «Por monto».",
                ServicioCobranza.textoNoAlcanzaParaLasCuotas("100.000 Gs.", "1", false, "289.470 Gs."));
    }

    /**
     * B117: el caso del borde, el que nunca se habia probado hasta la pasada de B115. Con 1 cuota,
     * la frase no puede contener el consejo imposible.
     */
    @Test
    public void testTextoNoAlcanzaParaLasCuotas_ConUnaSolaCuotaNoAconsejaElegirMenos() {
        String conUnaCuota = ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                "100.000 Gs.", "1", false, "154.700 Gs.");
        assertFalse("con 1 cuota no se puede elegir menos: " + conUnaCuota,
                conUnaCuota.contains("Elija menos cuotas"));
        // Y la de 2 o mas sigue ofreciendolo, que es correcto.
        assertTrue(ServicioCobranza.textoNoAlcanzaParaLasCuotas("100.000 Gs.", "2", true, "309.750 Gs.")
                .contains("Elija menos cuotas"));
    }

    @Test
    public void testTextoNoAlcanzaParaLasCuotas_LaPlantillaDelJsCompletadaDaLaMismaFrase() {
        // El JS recibe la plantilla con {recibido}/{n}/{monto} y solo reemplaza números: una vez
        // completada tiene que dar EXACTAMENTE la frase del primer render (regla de B92).
        String plantilla = ServicioCobranza.textoNoAlcanzaParaLasCuotas("{recibido}", "{n}", true, "{monto}");
        String completada = plantilla.replace("{recibido}", "500.000 Gs.")
                .replace("{n}", "3").replace("{monto}", "697.950 Gs.");
        assertEquals(ServicioCobranza.textoNoAlcanzaParaLasCuotas("500.000 Gs.", "3", true, "697.950 Gs."),
                completada);
        // B117: y la plantilla SINGULAR, que es la que el JS elige con cuotasElegidas === 1 y la
        // única que lleva el consejo corto. Sin esto, el cambio de B117 quedaba sin cubrir del lado
        // de la plantilla, que es justo la que ve la cajera.
        String plantillaSingular = ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                "{recibido}", "{n}", false, "{monto}");
        assertEquals(ServicioCobranza.textoNoAlcanzaParaLasCuotas("100.000 Gs.", "1", false, "154.700 Gs."),
                plantillaSingular.replace("{recibido}", "100.000 Gs.")
                        .replace("{n}", "1").replace("{monto}", "154.700 Gs."));
    }

    @Test
    public void testTextoNoAlcanzaParaLasCuotas_PantallaYServidorSoloDifierenEnLaCantidad() {
        // "Una redacción, dos arranques" (B91): el servidor no conoce la cantidad de cuotas -- el
        // formulario de confirmación manda montoSugerido y modoAnticipo, nunca la cantidad -- así
        // que usa la forma genérica. Este test fija que no puedan diferir en nada que no sea eso.
        //
        // B117 (05/11/2026): ahora el CONSEJO también puede diferir (con 1 cuota no se ofrece
        // "elija menos cuotas"), así que la comparación pasa a ser POR PARTES -- título y detalle
        // por separado, el mismo corte que hace pie.jsp -- en vez de un replace sobre la frase
        // entera. Comparar las frases completas habría dejado de servir justo acá.
        String pantallaPlural = ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                "500.000 Gs.", "3", true, "697.950 Gs.");
        String pantallaSingular = ServicioCobranza.textoNoAlcanzaParaLasCuotas(
                "100.000 Gs.", "1", false, "154.700 Gs.");
        String servidor = ServicioCobranza.textoNoAlcanzaParaLasCuotas("500.000 Gs.", "697.950 Gs.");

        // (1) El TÍTULO solo puede diferir en el fragmento de la cantidad.
        assertEquals(titulo(servidor),
                titulo(pantallaPlural).replace("3 cuotas", "la cantidad de cuotas elegida"));
        // (2) El DETALLE del servidor es EXACTAMENTE el de la forma plural: el consejo general.
        assertEquals(detalle(servidor), detalle(pantallaPlural));
        // (3) Y el de la forma singular es el único que cambia, y solo en el consejo.
        assertEquals("Use «Por monto».", detalle(pantallaSingular));
        assertEquals("Elija menos cuotas o use «Por monto».", detalle(pantallaPlural));

        // Las tres terminan en punto y se parten en título + detalle (B42).
        for (String texto : new String[] {pantallaPlural, pantallaSingular, servidor}) {
            assertTrue("termina mal: " + texto, texto.endsWith("."));
            assertTrue("no se parte en título y detalle: " + texto, texto.contains("). "));
            assertFalse("el título no puede quedar vacío: " + texto, titulo(texto).isEmpty());
        }
    }

    /** B117: el mismo corte que hace {@code pie.jsp} -- el primer "). " de la frase. */
    private static String titulo(String texto) {
        return texto.substring(0, texto.indexOf("). ") + 1);
    }

    private static String detalle(String texto) {
        return texto.substring(texto.indexOf("). ") + 3);
    }

    // ==================================================================
    // B114 (05/11/2026, hallazgo de Joel en pantalla): el redondeo de D-141 que la pantalla ofrece
    // NO convierte el cobro en un pago parcial de RN-20. Antes "esParcial" se decidía antes del
    // redondeo (y el redondeo solo se calculaba si no era parcial), así que el monto redondeado
    // --que está por definición 1..49 Gs. por debajo del exacto-- quedaba clasificado como parcial
    // y el servidor pedía una fecha de promesa por los pocos guaraníes que iba a perdonar.
    //
    // El caso real: CC-2026-00013 EN MORA, exigible 540.316, la ficha autocompleta "Recibido" con
    // 540.300 y el cartel promete "redondeo a favor del cliente: 16 Gs.".
    // ==================================================================

    @Test
    public void testEsCobroParcial_MontoRedondeadoConCuotasVencidas_NoEsParcial() {
        // Lo exigible es 540.316 y el cliente entrega 540.300 (el redondeado que ofrece la
        // pantalla): el redondeo aplica, la deuda queda saldada por completo y NO es parcial.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo redondeo =
                servicio.calcularRedondeoEfectivo(new BigDecimal("540316"), new BigDecimal("540300"), 50);
        assertEquals(true, redondeo.isAplicaRedondeo());
        assertEquals(0, new BigDecimal("16").compareTo(redondeo.getMontoRedondeo()));
        assertEquals(false, ServicioCobranza.esCobroParcial(new BigDecimal("540300"),
                new BigDecimal("540316"), redondeo.isAplicaRedondeo()));
    }

    @Test
    public void testEsCobroParcial_PagoParcialDeVerdad_SigueSiendoParcial() {
        // 100.000 contra 540.316 exigibles: no alcanza ni para el monto redondeado (540.300), así
        // que no hay redondeo y el cobro sigue siendo parcial -- RN-20 le sigue pidiendo promesa.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo redondeo =
                servicio.calcularRedondeoEfectivo(new BigDecimal("540316"), new BigDecimal("100000"), 50);
        assertEquals(false, redondeo.isAplicaRedondeo());
        assertEquals(true, ServicioCobranza.esCobroParcial(new BigDecimal("100000"),
                new BigDecimal("540316"), redondeo.isAplicaRedondeo()));
    }

    @Test
    public void testEsCobroParcial_UnGuaraniPorDebajoDelRedondeado_SigueSiendoParcial() {
        // El borde exacto de la ventana de perdón: 540.299 es 1 Gs. menos que el redondeado, así
        // que ya NO hay redondeo y vuelve a ser un pago parcial. Así se comprueba que la cota es
        // de (redondeoParam - 1) Gs. y que no se aceptó nada más ancho que eso.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo redondeo =
                servicio.calcularRedondeoEfectivo(new BigDecimal("540316"), new BigDecimal("540299"), 50);
        assertEquals(false, redondeo.isAplicaRedondeo());
        assertEquals(true, ServicioCobranza.esCobroParcial(new BigDecimal("540299"),
                new BigDecimal("540316"), redondeo.isAplicaRedondeo()));
    }

    @Test
    public void testEsCobroParcial_CubreTodoLoExigible_NoEsParcialConOSinRedondeo() {
        // El caso que el humo ya ejercitaba (paga el monto exacto o más): no era parcial antes y
        // sigue sin serlo -- el arreglo no cambia este camino.
        assertEquals(false, ServicioCobranza.esCobroParcial(new BigDecimal("540316"),
                new BigDecimal("540316"), false));
        assertEquals(false, ServicioCobranza.esCobroParcial(new BigDecimal("600000"),
                new BigDecimal("540316"), true));
    }

    @Test
    public void testEsCobroParcial_ParametroDeRedondeoEnCero_SeComportaComoAntes() {
        // Con credito.redondeo_efectivo en 0 no hay redondeo posible, así que la regla queda
        // exactamente como la de antes de B114: recibido < exigible es parcial.
        py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo redondeo =
                servicio.calcularRedondeoEfectivo(new BigDecimal("540316"), new BigDecimal("540300"), 0);
        assertEquals(false, redondeo.isAplicaRedondeo());
        assertEquals(true, ServicioCobranza.esCobroParcial(new BigDecimal("540300"),
                new BigDecimal("540316"), redondeo.isAplicaRedondeo()));
    }

    // ==================================================================
    // D-145 (E-1, pedido de Joel): el "Saldo de capital después del pago" del recibo se calcula
    // en vivo (capital_programado - capital_pagado de todas las cuotas del plan), no con
    // cuota.saldo_capital_posterior -- esa columna es fija (calculada una sola vez al generar el
    // plan) y un pago parcial nunca la actualiza. Bug real: CC-2026-00005, dos pagos parciales
    // seguidos sobre la misma cuota (REC-2026-000018 y REC-2026-000019) mostraron el mismo saldo
    // congelado en los dos recibos, en vez de bajar con el segundo pago.
    // ==================================================================

    @Test
    public void testCalcularSaldoCapitalPendienteEsLaSumaDelCapitalQueFaltaDeCadaCuota() {
        List<Cuota> cuotas = cuotasCC14();
        BigDecimal saldo = servicio.calcularSaldoCapitalPendiente(cuotas);
        // Ninguna cuota tiene todavía capital_pagado (fixture recién creado, EstadoPago.PENDIENTE
        // en las 12) -- el saldo pendiente es el monto aprobado completo.
        assertEquals(0, MONTO_APROBADO.compareTo(saldo));
    }

    @Test
    public void testCalcularSaldoCapitalPendienteBajaConCadaPagoParcialSobreLaMismaCuota() {
        // Reproduce el bug real de CC-2026-00005: dos pagos parciales seguidos sobre la cuota 1,
        // ninguno la termina de cubrir (queda PARCIAL, nunca PAGADA) -- igual que
        // REC-2026-000018 (200.000) y REC-2026-000019 (10.000, todo a capital).
        List<Cuota> cuotas = cuotasCC14();
        Cuota cuota1 = cuotas.get(0);
        BigDecimal saldoAntesDePagar = servicio.calcularSaldoCapitalPendiente(cuotas);

        // Primer pago parcial: abona 150.000 de capital a la cuota 1 (su capital programado es
        // 223.531, no alcanza a cubrirla -- sigue PARCIAL).
        cuota1.setCapitalPagado(new BigDecimal("150000"));
        cuota1.setEstadoPago(EstadoPago.PARCIAL);
        BigDecimal saldoTrasPrimerPago = servicio.calcularSaldoCapitalPendiente(cuotas);
        assertEquals(0, saldoAntesDePagar.subtract(new BigDecimal("150000")).compareTo(saldoTrasPrimerPago));

        // Segundo pago parcial sobre la MISMA cuota (todavía sin cubrirla): otros 10.000 de
        // capital -- el recibo de este segundo pago tiene que mostrar un saldo MENOR que el del
        // primero, no el mismo (eso era el bug).
        cuota1.setCapitalPagado(new BigDecimal("160000"));
        BigDecimal saldoTrasSegundoPago = servicio.calcularSaldoCapitalPendiente(cuotas);
        assertEquals(0, saldoTrasPrimerPago.subtract(new BigDecimal("10000")).compareTo(saldoTrasSegundoPago));
        assertTrue("El saldo tiene que bajar entre los dos recibos, no quedar igual",
                saldoTrasSegundoPago.compareTo(saldoTrasPrimerPago) < 0);

        // Contraste con el campo viejo (cuota.saldo_capital_posterior): sigue siendo el valor fijo
        // del fixture (2.776.469) después de los dos pagos -- ese es justamente el valor congelado
        // que el recibo ya NO usa más para este dato.
        assertEquals(0, new BigDecimal("2776469").compareTo(cuota1.getSaldoCapitalPosterior()));
    }

    // ==================================================================
    // D-146 (PA-19, pedido de Joel, autorizado a tocar el servicio): confirmarAnticipoCapital
    // ahora recibe "lo recibido" y "lo aplicado" por separado (antes un solo monto, guardado como
    // recibido=aplicado y vuelto fijo en 0) -- mismo patrón que confirmarCobro: guarda
    // monto_recibido/monto_vuelto/monto_aplicado reales y registra COBRO+VUELTO en caja (vía
    // registrarMovimientosCobro, ya compartido, sin cambios en su propio código).
    //
    // Solo el caso de rechazo (recibido < aplicado) se puede probar con JUnit puro: las 4
    // validaciones de confirmarAnticipoCapital (identificadorConfirmacion vacío, recibido <= 0,
    // aplicado <= 0, recibido < aplicado) están TODAS antes de abrir la conexión
    // (`try (Connection conexion = FabricaConexionBD.obtenerConexion())`), así que lanzan sin
    // tocar la base -- confirmado leyendo el código, mismo criterio que D-89 (las otras
    // "Falta..."/"Ingrese..." de confirmarCobro/confirmarCancelacionTotal, nunca testeadas así
    // porque nadie las necesitó hasta ahora).
    //
    // Los otros 2 casos que pidió Joel (adelanto con vuelto: campos del pago + los 2 movimientos
    // de caja + el neto; adelanto sin vuelto) verifican estado YA GUARDADO en `pagos`/
    // `movimientos_caja` -- igual que D-95 (dos pagos parciales con su propia promesa), son
    // intrínsecamente de base de datos y no se pueden probar con JUnit puro bajo la convención de
    // esta sesión (JUnit nunca toca la base). No agrego un JUnit que no probaría nada de verdad;
    // quedan para que Joel los pruebe en el navegador, y puedo verificarlos después con un SELECT
    // de solo lectura contra sgcpc_db_pruebas si los corre.
    // ==================================================================

    @Test(expected = NegocioException.class)
    public void testConfirmarAnticipoCapital_RecibidoMenorQueAplicado_RechazaSinTocarLaBase() throws SQLException {
        // No hay caja abierta, no hay préstamo id 1 en esta base de pruebas (si la hubiera, da
        // igual: esta validación corta ANTES de buscar nada) -- si este test llegara a necesitar
        // una conexión real, fallaría con un error de conexión, no con NegocioException, así que
        // el propio tipo de excepción esperado ya confirma que no se tocó la base.
        servicio.confirmarAnticipoCapital(1L, "identificador-de-prueba", new BigDecimal("100000"),
                new BigDecimal("200000"), LocalDate.of(2026, 10, 2), 1L);
    }

    @Test(expected = NegocioException.class)
    public void testConfirmarAnticipoCapital_MontoAplicadoCero_RechazaSinTocarLaBase() throws SQLException {
        servicio.confirmarAnticipoCapital(1L, "identificador-de-prueba", new BigDecimal("100000"), BigDecimal.ZERO,
                LocalDate.of(2026, 10, 2), 1L);
    }

    // ==================================================================
    // A3b (#6, pedido de Joel): búsqueda ambigua de Cobranza -- "0 coincidencias, 1, varias sin
    // activo, más de 10". Los 4 casos, de punta a punta, viven en buscarParaCobranza, que
    // necesita la base de verdad (ServicioClientes.listarConFiltros +, por cada coincidencia,
    // PrestamoDAO.listarTodosPorCedula/obtenerFechaFinalizacion para su situación) -- igual que
    // los otros métodos de este archivo que tocan la base, no se puede probar con JUnit puro bajo
    // la convención de esta sesión (D-95/D-89, ver los comentarios de arriba).
    //
    // Lo que SÍ es puro -- se separó a propósito de buscarParaCobranza para esto -- es el recorte
    // a MAXIMO_COINCIDENCIAS_AMBIGUAS y el aviso de "hay más", que es exactamente la parte de
    // "0/1/varias/más de 10" que depende solo de un número, no de la base. Eso se prueba acá,
    // más el DTO que lo expone a la JSP (ResultadoBusquedaCobranza.variasCoincidencias).
    // ==================================================================

    @Test
    public void testLimiteCoincidenciasAMostrar() {
        assertEquals(0, ServicioCobranza.limiteCoincidenciasAMostrar(0));
        assertEquals(1, ServicioCobranza.limiteCoincidenciasAMostrar(1));
        assertEquals(5, ServicioCobranza.limiteCoincidenciasAMostrar(5));
        assertEquals(10, ServicioCobranza.limiteCoincidenciasAMostrar(10));
        assertEquals(10, ServicioCobranza.limiteCoincidenciasAMostrar(11));
        assertEquals(10, ServicioCobranza.limiteCoincidenciasAMostrar(50));
    }

    @Test
    public void testHayMasCoincidenciasQueElLimite() {
        assertFalse(ServicioCobranza.hayMasCoincidenciasQueElLimite(0));
        assertFalse(ServicioCobranza.hayMasCoincidenciasQueElLimite(1));
        assertFalse(ServicioCobranza.hayMasCoincidenciasQueElLimite(10));
        assertTrue(ServicioCobranza.hayMasCoincidenciasQueElLimite(11));
        assertTrue(ServicioCobranza.hayMasCoincidenciasQueElLimite(50));
    }

    @Test
    public void testCapitalizar() {
        assertEquals("Tiene el préstamo CC-2026-00001 pendiente de desembolso.",
                ServicioCobranza.capitalizar("tiene el préstamo CC-2026-00001 pendiente de desembolso."));
        assertEquals("No tiene préstamos activos.", ServicioCobranza.capitalizar("no tiene préstamos activos."));
        assertEquals("", ServicioCobranza.capitalizar(""));
    }

    @Test
    public void testResultadoBusquedaCobranza_VariasCoincidencias_ExponeListaYHayMas() {
        List<CoincidenciaBusquedaCobranza> coincidencias = Arrays.asList(
                new CoincidenciaBusquedaCobranza("Ana Duarte", "800.000.003",
                        "Último: CC-2025-00001 cancelado el 01/10/2026.", 3L, false, false),
                new CoincidenciaBusquedaCobranza("Ana Gómez", "800.000.099", null, null, false, false));
        ResultadoBusquedaCobranza resultado = ResultadoBusquedaCobranza.variasCoincidencias(
                "Ninguno tiene un préstamo para cobrar.", coincidencias, true);

        assertTrue(resultado.getPrestamos().isEmpty());
        assertEquals("Ninguno tiene un préstamo para cobrar.", resultado.getMensajeSinResultados());
        assertEquals(2, resultado.getCoincidencias().size());
        assertEquals("Ana Duarte", resultado.getCoincidencias().get(0).getNombreCompleto());
        assertEquals("800.000.003", resultado.getCoincidencias().get(0).getCedula());
        assertEquals(Long.valueOf(3L), resultado.getCoincidencias().get(0).getIdPrestamoEnlace());
        assertNull("sin préstamos anteriores, sin situación que mostrar",
                resultado.getCoincidencias().get(1).getSituacion());
        assertTrue(resultado.isHayMasCoincidencias());
    }

    @Test
    public void testResultadoBusquedaCobranza_EncontradosYSinResultados_CoincidenciasSiemprePresenteYVacia() {
        // Regresión: agregar "coincidencias"/"hayMasCoincidencias" al DTO no puede dejar null esa
        // lista para los dos casos que ya existían (la JSP llama .isEmpty() sin chequear null).
        ResultadoBusquedaCobranza encontrados = ResultadoBusquedaCobranza.encontrados(Collections.emptyList(),
                Collections.emptyMap());
        assertTrue(encontrados.getCoincidencias().isEmpty());
        assertFalse(encontrados.isHayMasCoincidencias());

        ResultadoBusquedaCobranza sinResultados = ResultadoBusquedaCobranza.sinResultados(
                "No existe un cliente con esos datos.", null, false, false);
        assertTrue(sinResultados.getCoincidencias().isEmpty());
        assertFalse(sinResultados.isHayMasCoincidencias());
    }

    // A3b: procesarCerrar (ControladorCajaServlet) -- "si es posible sin servlet real". No lo es
    // bajo esta convención: necesita un HttpServletRequest/HttpServletResponse/HttpSession reales
    // (o implementarlos a mano, sin un framework de mocks, que el proyecto no usa) y, para llegar
    // al catch que se quiere probar, servicioCaja.cerrarCaja ya abrió una conexión real. No hay
    // ningún test de un Controlador en todo el proyecto (src/test no tiene ese paquete) -- el
    // mismo patrón guardarValoresCobro, que éste copia, tampoco lo tiene. Queda, como el resto de
    // lo que toca la base/el servlet, para que Joel lo pruebe a mano o con humo_http.sh.

    // ==================================================================
    // RF20 (pedido de Joel, 13/10/2026): Decisión 2 -- un préstamo EN_MORA aparece solo en "en
    // mora", nunca repetido en "vencen hoy"/"próximos 7 días". sinPrestamosEnMora es la parte
    // pura (sin Connection) de armarListaTrabajo, separada a propósito para poder probarla con
    // JUnit sin tocar la base (igual que limiteCoincidenciasAMostrar/capitalizar de arriba).
    // ==================================================================

    private static FilaVencimiento filaVencimiento(long idPrestamo) {
        // B99: el monto tambien va sin formatear (lo suma la tarjeta "Vencen hoy" del panel).
        return new FilaVencimiento(idPrestamo, "CC-2026-" + idPrestamo, "Cliente de prueba", "0981123456",
                "4.111.222", 1, 12, "15/10/2026", LocalDate.of(2026, 10, 15), "193.280 Gs.",
                new BigDecimal("193280"), "Pendiente");
    }

    private static FilaMora filaMoraDelPrestamo(long idPrestamo) {
        return new FilaMora(idPrestamo, "CC-2026-" + idPrestamo, "Cliente de prueba", "Cliente", "de prueba",
                "0981123456", "4.111.222",
                15, 1, 1, 12, "28/09/2026", "210.000 Gs.", new BigDecimal("210000"),
                false, null, null, false, null, null, null, false, new BigDecimal("210000"), new BigDecimal("1800000"));
    }

    @Test
    public void testArmarListaTrabajo_PrestamoEnMoraConCuotaQueVenceHoyYEnLosProximos7Dias_SoloEnMora() {
        // Mismo caso que pide Joel: el préstamo 7 está EN_MORA (por una cuota vieja) y ADEMÁS
        // tiene una cuota que vence hoy y otra dentro de los próximos 7 días -- tiene que
        // aparecer únicamente en "enMora", nunca repetido en las otras dos tarjetas.
        List<FilaMora> enMora = Arrays.asList(filaMoraDelPrestamo(7L));
        List<FilaVencimiento> vencenHoy = Arrays.asList(filaVencimiento(7L), filaVencimiento(9L));
        List<FilaVencimiento> proximos7Dias = Arrays.asList(filaVencimiento(7L), filaVencimiento(11L));

        ListaTrabajoCobranza lista = servicio.armarListaTrabajo(enMora, vencenHoy, proximos7Dias);

        assertEquals(1, lista.getEnMora().size());
        assertEquals(7L, lista.getEnMora().get(0).getIdPrestamo());
        assertEquals(1, lista.getVencenHoy().size());
        assertEquals(9L, lista.getVencenHoy().get(0).getIdPrestamo());
        assertEquals(1, lista.getProximos7Dias().size());
        assertEquals(11L, lista.getProximos7Dias().get(0).getIdPrestamo());
    }

    @Test
    public void testSinPrestamosEnMora_PrestamoEnMoraConCuotaQueVenceHoy_QuedaExcluido() {
        // Préstamo 7: EN_MORA (cuota vieja vencida) Y además su cuota siguiente vence hoy --
        // tiene que desaparecer de "vencen hoy" (queda solo en la tarjeta "En mora").
        List<FilaVencimiento> vencenHoy = Arrays.asList(filaVencimiento(7L), filaVencimiento(9L));
        Set<Long> idsEnMora = new HashSet<>(Arrays.asList(7L));

        List<FilaVencimiento> resultado = ServicioCobranza.sinPrestamosEnMora(vencenHoy, idsEnMora);

        assertEquals(1, resultado.size());
        assertEquals(9L, resultado.get(0).getIdPrestamo());
    }

    @Test
    public void testSinPrestamosEnMora_PrestamoEnMoraConCuotaEnLosProximos7Dias_QuedaExcluido() {
        // Mismo caso que arriba pero con la lista de "próximos 7 días" -- la función es la misma
        // para las dos tarjetas (armarListaTrabajo la llama dos veces).
        List<FilaVencimiento> proximos7Dias = Arrays.asList(filaVencimiento(7L));
        Set<Long> idsEnMora = new HashSet<>(Arrays.asList(7L));

        List<FilaVencimiento> resultado = ServicioCobranza.sinPrestamosEnMora(proximos7Dias, idsEnMora);

        assertTrue(resultado.isEmpty());
    }

    @Test
    public void testSinPrestamosEnMora_SinPrestamosEnMora_DevuelveTodoIgual() {
        List<FilaVencimiento> vencenHoy = Arrays.asList(filaVencimiento(1L), filaVencimiento(2L));

        List<FilaVencimiento> resultado = ServicioCobranza.sinPrestamosEnMora(vencenHoy, Collections.<Long>emptySet());

        assertEquals(2, resultado.size());
    }

    // ==================================================================
    // RF20: hallazgo del análisis -- buscar un préstamo ANULADO_ANTES_DESEMBOLSO (ej.
    // CC-2026-00002) mostraba "no tiene préstamos activos." sin decir que existe y fue anulado.
    // construirTextoAnulado es la parte pura (ya con el Usuario resuelto) de ese mensaje nuevo.
    // ==================================================================

    private static PrestamoCredito prestamoAnulado() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(2L);
        prestamo.setIdentificadorOperacion("CC-2026-00002");
        prestamo.setEstadoPrestamo(EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO);
        prestamo.setFechaAnulacionSinDesembolso(LocalDateTime.of(2026, 10, 12, 0, 11));
        prestamo.setMotivoAnulacionSinDesembolso("El cliente desistió del préstamo.");
        return prestamo;
    }

    @Test
    public void testConstruirTextoAnulado_ConUsuario_IncluyeOperacionQuienCuandoYMotivo() {
        Usuario quienAnulo = new Usuario();
        quienAnulo.setNombres("Joel Jesús");
        quienAnulo.setApellidos("Acosta");

        String texto = servicio.construirTextoAnulado(prestamoAnulado(), quienAnulo);

        assertEquals("tiene la operación CC-2026-00002, anulada antes del desembolso por Joel Jesús Acosta "
                + "el 12/10/2026 00:11. Motivo: El cliente desistió del préstamo. No tiene préstamos activos.",
                texto);
    }

    @Test
    public void testConstruirTextoAnulado_SinUsuario_DiceOtroUsuario() {
        String texto = servicio.construirTextoAnulado(prestamoAnulado(), null);

        assertTrue(texto.contains("anulada antes del desembolso por otro usuario el"));
    }

    /** Punto 2 (RF25, pedido de Joel, 12/10/2026): REV-aaaa-NNNNNN, con id_reversion_pago
     * rellenado a 6 dígitos. */
    @Test
    public void testConstruirNumeroConstanciaReversion() {
        assertEquals("REV-2026-000014", ServicioCobranza.construirNumeroConstanciaReversion(2026, 14));
    }

    @Test
    public void testConstruirNumeroConstanciaReversion_IdDeMasDeSeisDigitosNoSeTrunca() {
        assertEquals("REV-2026-1234567", ServicioCobranza.construirNumeroConstanciaReversion(2026, 1234567));
    }

    /** RF23 (hallazgo urgente de Joel, 12/10/2026): la muestra de factura mostraba 2.286 Gs. en
     * SUBTOTALES/TOTAL A PAGAR cuando la fila decía 2.515 Gs. (IVA incluido) -- el error estaba
     * en el script de la muestra (armaba los totales a mano, con el neto en vez del neto+IVA),
     * no en el servicio real ni en el .jrxml. Esta prueba fija la regla para que no se repita:
     * total = subtotal 10 % = suma de las filas CON IVA incluido (LineaFactura.getTotal() ya
     * suma neto+iva por línea, "lo que suma a total_gravada_10" -- ver su propio comentario). */
    @Test
    public void testCalcularTotalesFactura_TotalGravadoEsNetoMasIva() {
        LineaFactura linea = new LineaFactura("Interés compensatorio", new BigDecimal("2286"), new BigDecimal("229"));

        TotalesLineasFactura totales = ServicioCobranza.calcularTotalesFactura(java.util.Collections.singletonList(linea));

        assertEquals(0, new BigDecimal("2515").compareTo(totales.getTotalGravado()));
        assertEquals(0, new BigDecimal("229").compareTo(totales.getTotalIva()));
        // La propiedad que pidió Joel, explícita: total (gravado) = suma de filas con IVA
        // incluido = importeNeto + iva de la única línea.
        assertEquals(0, linea.getTotal().compareTo(totales.getTotalGravado()));
    }

    @Test
    public void testCalcularTotalesFactura_SumaVariasLineas() {
        LineaFactura linea1 = new LineaFactura("Interés cuota 1", new BigDecimal("1000"), new BigDecimal("100"));
        LineaFactura linea2 = new LineaFactura("Interés cuota 2", new BigDecimal("2000"), new BigDecimal("200"));

        TotalesLineasFactura totales = ServicioCobranza.calcularTotalesFactura(java.util.Arrays.asList(linea1, linea2));

        assertEquals(0, new BigDecimal("3300").compareTo(totales.getTotalGravado()));
        assertEquals(0, new BigDecimal("300").compareTo(totales.getTotalIva()));
    }

    // ----------------------------------------------------------------
    // Corrección de diseño del 27/10/2026 (opción B, tras la prueba en pantalla de Joel):
    // buscarMontoMinimoParaDejarCuotas -- "pagar N cuotas" ya no es "N × la cuota" (eso dejaba
    // menos cuotas de las que la cajera decía: pagar 4 de 12 dejaba 7, no 8), sino el monto
    // MÍNIMO que deja exactamente K − N cuotas en el plan nuevo.
    //
    // Se prueba contra el motor REAL, sin base de datos: el plan lo genera generarPlanPagos con
    // los datos de CC-2026-00001 (3.000.000 Gs., 12 cuotas, TEA 27 %, desembolsado el
    // 22/10/2026, primera cuota el 23/11/2026) y el adelanto se calcula al 27/10/2026.
    // ----------------------------------------------------------------

    private static final int PASO_EFECTIVO = 50; // credito.redondeo_efectivo
    private static final LocalDate FECHA_ANTICIPO = LocalDate.of(2026, 10, 27);
    private static final int DIAS_GRACIA = 0; // credito.dias_gracia_mora, hoy en 0 (D-77)

    private Prestamo prestamoCC1DoceCuotas() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(LocalDate.of(2026, 10, 22));
        prestamo.setTasaTea(new BigDecimal("0.270000"));
        prestamo.setTasaMoratoria(new BigDecimal("0.270000"));
        prestamo.setTasaPunitoria(new BigDecimal("0.300000"));
        prestamo.setCuotas(motor.generarPlanPagos(new BigDecimal("3000000"), 12, new BigDecimal("0.270000"),
                LocalDate.of(2026, 11, 23), Collections.<LocalDate>emptySet()));
        return prestamo;
    }

    private BigDecimal montoParaPagar(int cuotasAPagar) {
        Prestamo prestamo = prestamoCC1DoceCuotas();
        return new ServicioCobranza().buscarMontoMinimoParaDejarCuotas(prestamo,
                Collections.<LocalDate>emptyList(), FECHA_ANTICIPO, DIAS_GRACIA,
                prestamo.getCuotas().size() - cuotasAPagar, PASO_EFECTIVO);
    }

    private int cuotasQueQuedanConMonto(BigDecimal monto) {
        return new ServicioMotorFinanciero().calcularAnticipoParcial(prestamoCC1DoceCuotas(), monto, FECHA_ANTICIPO,
                Collections.<LocalDate>emptySet()).getNuevoPlan().size();
    }

    @Test
    public void testAnticipoPorCuotas_DejaExactamenteLaCantidadDeCuotasPedida() {
        // Lo que ve la cajera: paga N cuotas de 12 y le quedan 12 − N. Antes (N × cuota) pagar 4
        // dejaba 7, y con 11 el sistema ni dejaba intentarlo.
        assertEquals(11, cuotasQueQuedanConMonto(montoParaPagar(1)));
        assertEquals(8, cuotasQueQuedanConMonto(montoParaPagar(4)));
        assertEquals(1, cuotasQueQuedanConMonto(montoParaPagar(11)));
    }

    @Test
    public void testAnticipoPorCuotas_UnPasoDeEfectivoMenosDejaUnaCuotaMas() {
        // Es el MÍNIMO: 50 Gs. menos (un paso de efectivo) y ya no alcanza para sacar esa cuota.
        // Esto es lo que hace que el piso de D-141 no sirva acá y la búsqueda vaya directamente
        // sobre múltiplos de 50 (decisión de Joel del 27/10/2026).
        for (int cuotasAPagar : new int[] {1, 4, 11}) {
            BigDecimal monto = montoParaPagar(cuotasAPagar);
            int quedanConElMonto = cuotasQueQuedanConMonto(monto);
            int quedanConUnPasoMenos = cuotasQueQuedanConMonto(monto.subtract(BigDecimal.valueOf(PASO_EFECTIVO)));
            assertEquals("pagando " + cuotasAPagar + " cuotas", quedanConElMonto + 1, quedanConUnPasoMenos);
        }
    }

    @Test
    public void testAnticipoPorCuotas_ElMontoEsMultiploDelPasoDeEfectivo() {
        // Un solo monto, ya en múltiplos de 50 (aprobado por Joel): no hay que redondearlo
        // después, y por eso no se muestra el par "exacto / en efectivo".
        for (int cuotasAPagar : new int[] {1, 4, 11}) {
            assertEquals("pagando " + cuotasAPagar + " cuotas", 0,
                    montoParaPagar(cuotasAPagar).remainder(BigDecimal.valueOf(PASO_EFECTIVO))
                            .compareTo(BigDecimal.ZERO));
        }
    }

    @Test
    public void testAnticipoPorCuotas_MontosCrecientesYSiempreMenoresQueLaSumaDeLasCuotas() {
        Prestamo prestamo = prestamoCC1DoceCuotas();
        BigDecimal cuotaProgramada = prestamo.getCuotas().get(0).getTotalProgramado();
        BigDecimal anterior = BigDecimal.ZERO;
        for (int cuotasAPagar = 1; cuotasAPagar <= 11; cuotasAPagar++) {
            BigDecimal monto = montoParaPagar(cuotasAPagar);
            // Monótono: a más cuotas, más monto (nunca al revés ni igual).
            assertTrue("pagando " + cuotasAPagar + " cuotas", monto.compareTo(anterior) > 0);
            // Y siempre menos que lo que esas cuotas costaban en el plan: la diferencia es el
            // interés futuro que el cliente deja de pagar (RN-11, Ley 6624/2020) -- el "se ahorra
            // Y Gs." de la pantalla.
            assertTrue("pagando " + cuotasAPagar + " cuotas",
                    monto.compareTo(cuotaProgramada.multiply(BigDecimal.valueOf(cuotasAPagar))) < 0);
            anterior = monto;
        }
    }

    @Test
    public void testAnticipoPorCuotas_LaProximaCuotaQuedaCercaDeLaProgramada() {
        // RN-11 / cláusula SEXTA: el adelanto reduce la CANTIDAD de cuotas, no el monto de cada
        // una. Condición que pidió Joel el 27/10/2026 después de la prueba en pantalla. La cuota
        // nueva 1 no es idéntica (su interés va por los días que quedan del período en curso, no
        // por un mes entero), así que se exige "cerca": nunca menos del 60 % ni más que la
        // programada.
        Prestamo prestamo = prestamoCC1DoceCuotas();
        BigDecimal cuotaProgramada = prestamo.getCuotas().get(0).getTotalProgramado();
        for (int cuotasAPagar : new int[] {1, 4, 11}) {
            List<Cuota> planNuevo = new ServicioMotorFinanciero().calcularAnticipoParcial(prestamoCC1DoceCuotas(),
                    montoParaPagar(cuotasAPagar), FECHA_ANTICIPO, Collections.<LocalDate>emptySet()).getNuevoPlan();
            BigDecimal proxima = planNuevo.get(0).getTotalProgramado();
            assertTrue("pagando " + cuotasAPagar + " cuotas, próxima cuota " + proxima,
                    proxima.compareTo(cuotaProgramada) <= 0);
            assertTrue("pagando " + cuotasAPagar + " cuotas, próxima cuota " + proxima,
                    proxima.multiply(BigDecimal.valueOf(100))
                            .compareTo(cuotaProgramada.multiply(BigDecimal.valueOf(60))) >= 0);
        }
    }

    @Test
    public void testAnticipoPorCuotas_PagarTodasLasCuotasNoEsUnAdelanto() {
        Prestamo prestamo = prestamoCC1DoceCuotas();
        // N = K (12 de 12): dejar 0 cuotas no es un adelanto -- la ficha pasa al flujo de "Todo
        // el préstamo" (confirmarCancelacionTotal, RF26/RF28), así que la búsqueda no devuelve
        // ningún monto de adelanto.
        assertNull(new ServicioCobranza().buscarMontoMinimoParaDejarCuotas(prestamo,
                Collections.<LocalDate>emptyList(), FECHA_ANTICIPO, DIAS_GRACIA, 0, PASO_EFECTIVO));
        // Y el monto de esa opción (el de cancelar todo) es mayor que el de pagar 11 de 12: la
        // cantidad de cuotas y el monto siguen subiendo juntos hasta el final, sin saltos raros.
        BigDecimal cancelarTodo = new ServicioMotorFinanciero()
                .calcularCancelacionTotal(prestamoCC1DoceCuotas(), FECHA_ANTICIPO, DIAS_GRACIA)
                .getMontoTotalLiquidacion();
        assertTrue(cancelarTodo.compareTo(montoParaPagar(11)) > 0);
    }

    // ======================================================================
    // C39 (hallazgo de Joel del 27/10/2026, decidido el 29/10): la fecha que se le MUESTRA al
    // cliente como "su próxima cuota" es la de COBRO, no el ancla teórica del plan. Un solo dueño
    // para el recibo del adelanto, la ficha de cobranza y la vista previa en vivo.
    // ======================================================================

    private Cuota cuotaConFechas(LocalDate teorica, LocalDate cobro) {
        Cuota cuota = new Cuota();
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimientoTeorica(teorica);
        cuota.setFechaVencimiento(cobro);
        return cuota;
    }

    @Test
    public void testLaProximaCuotaSeMuestraConLaFechaDeCobroYNoConLaTeorica() {
        // El caso que encontró Joel en CC-2026-00001: ancla sábado 21/11, cobro lunes 23/11. El
        // recibo decía 21/11 mientras el estado de cuenta del mismo préstamo decía 23/11.
        assertEquals("23/11/2026", ServicioCobranza.fechaProximaCuotaNuevoPlan(
                cuotaConFechas(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 23))));
    }

    @Test
    public void testCuandoElAnclaYaEsDiaHabilLasDosFechasCoinciden() {
        // Sin corrimiento no hay diferencia que mostrar: el cambio de C39 no altera este caso,
        // que es el de la mayoría de los préstamos.
        assertEquals("21/09/2027", ServicioCobranza.fechaProximaCuotaNuevoPlan(
                cuotaConFechas(LocalDate.of(2027, 9, 21), LocalDate.of(2027, 9, 21))));
    }

    @Test
    public void testLaFechaVaFormateadaComoEnElRestoDelSistema() {
        // dd/mm/aaaa, el formato de FormateadorFechas -- la muestran un PDF y dos pantallas, y
        // tienen que verse iguales.
        String fecha = ServicioCobranza.fechaProximaCuotaNuevoPlan(
                cuotaConFechas(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 30)));
        assertEquals("30/11/2026", fecha);
        assertTrue(fecha.matches("\\d{2}/\\d{2}/\\d{4}"));
    }

    // ======================================================================
    // B92 (hallazgo de Joel del 29/10/2026): "Paga N cuotas" invitaba a comparar ese monto con la
    // cuota del plan, que es otra cosa. Un solo dueno del texto para los tres lugares que lo
    // muestran (la JSP, la tarjeta en vivo y el cartel de confirmacion).
    // ======================================================================

    @Test
    public void testLaEtiquetaDelAdelantoDiceQueNoSePaganInteresesFuturos() {
        assertEquals("Adelanta 1 cuota (sin intereses futuros)",
                ServicioCobranza.etiquetaAdelantoCuotas(1));
        assertEquals("Adelanta 3 cuotas (sin intereses futuros)",
                ServicioCobranza.etiquetaAdelantoCuotas(3));
    }

    @Test
    public void testLaEtiquetaNoDiceNiPagaNiElMonto() {
        // "Paga" era la palabra que confundia; y la frase va sin monto ni dos puntos, porque el
        // cartel de confirmacion la usa sola.
        String etiqueta = ServicioCobranza.etiquetaAdelantoCuotas(2);
        assertFalse(etiqueta.contains("Paga"));
        assertFalse(etiqueta.contains(":"));
        assertFalse(etiqueta.contains("Gs."));
    }

    @Test
    public void testLasPlantillasParaElJavaScriptSonLaMismaFrase() {
        // La JSP deja estas dos plantillas en un data- y el JS solo reemplaza {n}: tienen que ser
        // la misma frase que ve el primer render, con el numero afuera.
        assertEquals("Adelanta {n} cuota (sin intereses futuros)",
                ServicioCobranza.etiquetaAdelantoCuotas("{n}", false));
        assertEquals("Adelanta {n} cuotas (sin intereses futuros)",
                ServicioCobranza.etiquetaAdelantoCuotas("{n}", true));
        assertEquals(ServicioCobranza.etiquetaAdelantoCuotas(1),
                ServicioCobranza.etiquetaAdelantoCuotas("{n}", false).replace("{n}", "1"));
        assertEquals(ServicioCobranza.etiquetaAdelantoCuotas(7),
                ServicioCobranza.etiquetaAdelantoCuotas("{n}", true).replace("{n}", "7"));
    }

    // ======================================================================
    // B79/B81 (decision de Joel, 30/10/2026): la linea grande del "Resumen del cobro" dice el
    // monto que se va a cobrar. Si no se va a cobrar nada no dice ningun monto (B79); si el cobro
    // es parcial, dice lo que se cobra y el renglon de abajo explica lo exigible y lo pendiente
    // (B81). Un solo dueno de los tres textos; el JS solo reemplaza numeros.
    // ======================================================================

    @Test
    public void testLaLineaSinMontoEsElMismoGuionQueYaUsabaLaPantalla() {
        // La JSP ya escribia &mdash; en el primer render cuando el prestamo no admite cobro: el
        // guion del bloqueo tiene que ser ESE caracter, no un guion comun ni dos.
        assertEquals("—", ServicioCobranza.LINEA_RESUMEN_SIN_MONTO);
        assertFalse(ServicioCobranza.LINEA_RESUMEN_SIN_MONTO.contains("-"));
    }

    @Test
    public void testLaLineaDelPagoParcialNombraLaOperacionYElMontoQueSeCobra() {
        assertEquals("Cobra 100.000 Gs. (pago parcial)",
                ServicioCobranza.lineaCobroParcial("100.000 Gs."));
        // No puede decir "Pagar lo vencido" (el total exigible), que es justo lo que mostraba
        // antes el renglon grande con otro monto.
        assertFalse(ServicioCobranza.lineaCobroParcial("100.000 Gs.").contains("vencido"));
    }

    @Test
    public void testElDetalleDelPagoParcialDiceLoExigibleYLoPendiente() {
        assertEquals("de 180.696 Gs. exigibles — queda pendiente 80.696 Gs.",
                ServicioCobranza.detalleCobroParcial("180.696 Gs.", "80.696 Gs."));
    }

    @Test
    public void testNingunTextoDeEsteDuenoDuplicaElPuntoDelMonto() {
        // Defecto encontrado por Joel en pantalla el 30/10/2026: "...1.880 Gs..". comoGuaranies ya
        // termina en "Gs.", asi que una frase que le agregue otro "." duplica el punto (D-145
        // punto 8). Se completan las plantillas con montos formateados DE VERDAD, como los arma el
        // sistema, y se verifica que ninguna termine en ".." ni lo contenga.
        String monto = FormateadorNumeros.comoGuaranies(new BigDecimal("1880"));
        assertEquals("1.880 Gs.", monto);
        String[] textos = {
            ServicioCobranza.lineaCobroParcial(monto),
            ServicioCobranza.detalleCobroParcial(monto, FormateadorNumeros.comoGuaranies(new BigDecimal("80696"))),
            ServicioCobranza.etiquetaAdelantoCuotas(1) + ": " + monto,
            ServicioCobranza.mensajeNumeroOperacionAmbiguo(4),
            ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA,
            ServicioCobranza.botonCobroParcial(monto),
            ServicioCobranza.lineaCobroACuenta(monto),
            ServicioCobranza.detalleCobroACuenta(monto, monto),
            ServicioCobranza.lineaCartelCuotaDeHoy("1/10", monto),
            ServicioCobranza.notaAntesDeAdelantar(ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY, "1/10"),
            ServicioCobranza.textoSoloLoVencidoConExcedente(monto, monto),
            ServicioCobranza.textoSoloLoVencidoSinExcedente(monto)
        };
        for (String texto : textos) {
            assertFalse("punto duplicado en: " + texto, texto.contains(".."));
            assertFalse("termina en \"..\": " + texto, texto.endsWith(".."));
        }
    }

    @Test
    public void testLasPlantillasDelPagoParcialSonLaMismaFraseQueElPrimerRender() {
        // Mismo cruce que B92: la JSP deja estas plantillas en un data- y cobranza-ficha.js solo
        // reemplaza los numeros, asi que completadas tienen que dar EXACTAMENTE la misma frase.
        assertEquals(ServicioCobranza.lineaCobroParcial("100.000 Gs."),
                ServicioCobranza.lineaCobroParcial("{monto}").replace("{monto}", "100.000 Gs."));
        assertEquals(ServicioCobranza.detalleCobroParcial("180.696 Gs.", "80.696 Gs."),
                ServicioCobranza.detalleCobroParcial("{exigible}", "{pendiente}")
                        .replace("{exigible}", "180.696 Gs.")
                        .replace("{pendiente}", "80.696 Gs."));
    }

    // ======================================================================
    // B82/B93 (decision de Joel, 30/10/2026): un solo buscador de prestamo por numero de
    // operacion para Cobranza y para el Estado de cuenta -- exacto sin importar mayusculas y, si
    // no hay, por fragmento con tope.
    // ======================================================================

    @Test
    public void testElNumeroDeOperacionSeNormalizaIgualParaLasDosBusquedas() {
        // El Estado de cuenta comparaba el texto tal cual se escribia: "cc-2026-00005" no
        // encontraba nada ahi y si en Cobranza (B93).
        assertEquals("CC-2026-00005", ServicioCobranza.normalizarNumeroOperacion("cc-2026-00005"));
        assertEquals("CC-2026-00005", ServicioCobranza.normalizarNumeroOperacion("  CC-2026-00005 "));
        assertEquals("00005", ServicioCobranza.normalizarNumeroOperacion(" 00005"));
        assertEquals("", ServicioCobranza.normalizarNumeroOperacion(null));
        assertEquals("", ServicioCobranza.normalizarNumeroOperacion("   "));
    }

    @Test
    public void testElAvisoDeFragmentoAmbiguoPideElNumeroCompleto() {
        String aviso = ServicioCobranza.mensajeNumeroOperacionAmbiguo(4);
        assertTrue(aviso.contains("4"));
        assertTrue(aviso.contains("completo"));
        // No puede hablar de "cliente": la cajera escribio un numero de prestamo, no un nombre
        // (ese era el mensaje que daba el diagnostico por cedula).
        assertFalse(aviso.contains("cliente"));
    }

    @Test
    public void testElTopeDelFragmentoEsElMismoQueElDeLasCoincidenciasAmbiguas() {
        // Un fragmento corto como "2026" entra en todos los prestamos: el tope existe y es el
        // mismo que ya usaba la busqueda ambigua por nombre.
        assertEquals(10, ServicioCobranza.MAXIMO_COINCIDENCIAS_AMBIGUAS);
    }

    // ======================================================================
    // B95/B96 (decision de Joel, 30/10/2026): el pago parcial de lo vencido -- lo que falta
    // elegir se pide en gris (no en rojo, que es para lo que esta mal), y el cartel de
    // confirmacion usa las MISMAS frases que el resumen, boton incluido.
    // ======================================================================

    @Test
    public void testElMotivoDeLaFechaDePromesaPideElegirla() {
        // Lo que la cajera lee cuando el campo todavia esta vacio: un pedido, no un error.
        assertEquals("Elija la fecha de promesa para habilitar \u00abCobrar\u00bb.",
                ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA);
        // No puede hablar de que la fecha este mal: ese es el otro caso, el del aviso en rojo
        // ("La fecha de promesa debe ser posterior a hoy").
        assertFalse(ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA.contains("debe"));
        assertFalse(ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA.contains("posterior"));
        // Nombra el boton que habilita, con las comillas del resto del sistema.
        assertTrue(ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA.contains("Cobrar"));
    }

    @Test
    public void testElBotonDelCartelEnUnPagoParcialNombraElMontoQueSeCobra() {
        assertEquals("S\u00ed, cobrar 100.000 Gs.", ServicioCobranza.botonCobroParcial("100.000 Gs."));
        // Decia "Si, cobrar lo vencido" igual que en un cobro completo, pero en un pago parcial
        // el cliente NO paga todo lo vencido (misma leccion que B92).
        assertFalse(ServicioCobranza.botonCobroParcial("100.000 Gs.").contains("lo vencido"));
    }

    @Test
    public void testLaPlantillaDelBotonParcialEsLaMismaFrase() {
        // Igual que B92: la JSP deja la plantilla en un data- y el JS solo reemplaza el numero.
        assertEquals(ServicioCobranza.botonCobroParcial("100.000 Gs."),
                ServicioCobranza.botonCobroParcial("{monto}").replace("{monto}", "100.000 Gs."));
    }

    // ======================================================================
    // B98 (hallazgo y decision de Joel, 30/10/2026): una cuota que VENCE HOY no esta vencida.
    // "\u00bfHay algo vencido?" y "\u00bfhay algo cobrable hoy?" son dos preguntas distintas, y la
    // pantalla hacia solo la segunda: decia "vencido" cuatro veces con nada vencido.
    // ======================================================================

    @Test
    public void testLaSituacionDelCobroDistingueVencidoDeCuotaDeHoy() {
        assertEquals(ServicioCobranza.SituacionCobroHoy.HAY_VENCIDO,
                ServicioCobranza.situacionCobroHoy(true, true));
        // Hay algo cobrable pero nada vencido: es la cuota del dia (D-144).
        assertEquals(ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY,
                ServicioCobranza.situacionCobroHoy(true, false));
        assertEquals(ServicioCobranza.SituacionCobroHoy.NADA_PARA_COBRAR,
                ServicioCobranza.situacionCobroHoy(false, false));
    }

    @Test
    public void testElRotuloDeLaOpcionNoDiceVencidoCuandoLaCuotaVenceHoy() {
        assertEquals("Pagar lo vencido",
                ServicioCobranza.etiquetaOpcionCobro(ServicioCobranza.SituacionCobroHoy.HAY_VENCIDO));
        assertEquals("Pagar la cuota de hoy",
                ServicioCobranza.etiquetaOpcionCobro(ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY));
        assertEquals("Por cantidad de cuotas",
                ServicioCobranza.etiquetaOpcionCobro(ServicioCobranza.SituacionCobroHoy.NADA_PARA_COBRAR));
        assertFalse(ServicioCobranza.etiquetaOpcionCobro(
                ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY).contains("vencido"));
    }

    @Test
    public void testLaNotaDelAdelantoNombraLaCuotaDeHoyYNoHablaDeVencido() {
        String nota = ServicioCobranza.notaAntesDeAdelantar(
                ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY, "1/10");
        assertEquals("Hoy vence la cuota 1/10. Adelantar por cantidad de cuotas o por monto queda "
                + "disponible cuando no haya ninguna cuota para cobrar.", nota);
        assertFalse(nota.contains("vencido"));
        // Con cuotas vencidas de verdad, el texto es el de siempre.
        assertTrue(ServicioCobranza.notaAntesDeAdelantar(
                ServicioCobranza.SituacionCobroHoy.HAY_VENCIDO, null).startsWith("Primero hay que cobrar lo vencido."));
        // Sin nada que cobrar no hay nota: la opcion del adelanto ya esta a la vista.
        assertEquals("", ServicioCobranza.notaAntesDeAdelantar(
                ServicioCobranza.SituacionCobroHoy.NADA_PARA_COBRAR, null));
    }

    @Test
    public void testLaLineaDelCobroACuentaNoDicePagoParcialNiVencido() {
        assertEquals("Cobra 100.000 Gs. (a cuenta de la cuota de hoy)",
                ServicioCobranza.lineaCobroACuenta("100.000 Gs."));
        // No es un pago parcial de RN-20 (lo exigible es 0 y no se pide promesa), asi que no puede
        // llamarse asi; y nada esta vencido.
        assertFalse(ServicioCobranza.lineaCobroACuenta("100.000 Gs.").contains("parcial"));
        assertFalse(ServicioCobranza.lineaCobroACuenta("100.000 Gs.").contains("vencido"));
    }

    @Test
    public void testElDetalleDelCobroACuentaDiceLoQueQuedaYQueNoHayMora() {
        assertEquals("de 289.011 Gs. \u2014 quedan 189.011 Gs., sin mora hasta ma\u00f1ana",
                ServicioCobranza.detalleCobroACuenta("289.011 Gs.", "189.011 Gs."));
    }

    @Test
    public void testElCartelYElBotonDeLaCuotaDeHoyLaNombran() {
        assertEquals("Cobra la cuota 1/10 de hoy: 289.000 Gs.",
                ServicioCobranza.lineaCartelCuotaDeHoy("1/10", "289.000 Gs."));
        assertEquals("S\u00ed, cobrar la cuota de hoy", ServicioCobranza.BOTON_COBRAR_CUOTA_DE_HOY);
        assertFalse(ServicioCobranza.BOTON_COBRAR_CUOTA_DE_HOY.contains("lo vencido"));
    }

    @Test
    public void testLosTextosDeDe146P17QuedaronIgualesAlMudarseAlServicio() {
        // Venian escritos dentro de cobranza-ficha.js y ya estaban probados en pantalla: se mudan
        // sin cambiar una palabra (ahora el JS no redacta y un test puede fijarlos).
        assertEquals("Se cobra lo vencido. Lo que sobra (50.000 Gs.) se aplica a la cuota de hoy; "
                + "quedan 239.011 Gs. pendientes, sin mora hasta ma\u00f1ana.",
                ServicioCobranza.textoSoloLoVencidoConExcedente("50.000 Gs.", "239.011 Gs."));
        assertEquals("Se cobra lo vencido. La cuota de hoy (289.011 Gs.) queda pendiente, "
                + "sin mora hasta ma\u00f1ana.",
                ServicioCobranza.textoSoloLoVencidoSinExcedente("289.011 Gs."));
    }

    @Test
    public void testLasPlantillasDeB98SonLaMismaFraseQueElRenderDirecto() {
        // Mismo cruce que B92/B96: la JSP deja la plantilla en un data- y el JS solo reemplaza
        // numeros, asi que completada tiene que dar EXACTAMENTE la misma frase.
        assertEquals(ServicioCobranza.lineaCobroACuenta("100.000 Gs."),
                ServicioCobranza.lineaCobroACuenta("{monto}").replace("{monto}", "100.000 Gs."));
        assertEquals(ServicioCobranza.detalleCobroACuenta("289.011 Gs.", "189.011 Gs."),
                ServicioCobranza.detalleCobroACuenta("{total}", "{pendiente}")
                        .replace("{total}", "289.011 Gs.").replace("{pendiente}", "189.011 Gs."));
        assertEquals(ServicioCobranza.lineaCartelCuotaDeHoy("1/10", "289.000 Gs."),
                ServicioCobranza.lineaCartelCuotaDeHoy("1/10", "{monto}").replace("{monto}", "289.000 Gs."));
        assertEquals(ServicioCobranza.textoSoloLoVencidoConExcedente("50.000 Gs.", "239.011 Gs."),
                ServicioCobranza.textoSoloLoVencidoConExcedente("{excedente}", "{pendiente}")
                        .replace("{excedente}", "50.000 Gs.").replace("{pendiente}", "239.011 Gs."));
        assertEquals(ServicioCobranza.textoSoloLoVencidoSinExcedente("289.011 Gs."),
                ServicioCobranza.textoSoloLoVencidoSinExcedente("{pendiente}")
                        .replace("{pendiente}", "289.011 Gs."));
    }

    @Test
    public void testLaLineaGrandeDeLaCuotaDeHoyNoDiceVencido() {
        // La falla que Joel encontro en la segunda prueba: la linea grande volvia a decir "Pagar lo
        // vencido: 289.011 Gs." porque el rotulo estaba escrito en el JS. La linea es
        // "<rotulo>: <monto>", y para la cuota de hoy el rotulo es el de SOLO_CUOTA_DE_HOY: se fija
        // la frase COMPLETA, que es lo que se lee en pantalla.
        String monto = FormateadorNumeros.comoGuaranies(new BigDecimal("289011"));
        String lineaCuotaDeHoy = ServicioCobranza.etiquetaOpcionCobro(
                ServicioCobranza.SituacionCobroHoy.SOLO_CUOTA_DE_HOY) + ": " + monto;
        assertEquals("Pagar la cuota de hoy: 289.011 Gs.", lineaCuotaDeHoy);
        assertFalse(lineaCuotaDeHoy.contains("vencido"));
        // Y con cuotas vencidas de verdad, la misma linea sigue siendo la de siempre.
        assertEquals("Pagar lo vencido: 289.011 Gs.", ServicioCobranza.etiquetaOpcionCobro(
                ServicioCobranza.SituacionCobroHoy.HAY_VENCIDO) + ": " + monto);
    }

    @Test
    public void testLaCuotaSeNombraIgualEnTodaLaPantalla() {
        assertEquals("1/10", ServicioCobranza.etiquetaNumeroCuota(1, 10));
        assertEquals("3/6", ServicioCobranza.etiquetaNumeroCuota(3, 6));
    }

    // ======================================================================
    // B102 (decision de Joel, 01/11/2026): un solo dueno del concepto de la linea de factura,
    // con el texto que HOY se imprime, para que ninguna factura ya emitida cambie al
    // reimprimirse (sus renglones se derivan de aplicaciones_pago, D-93/D-94).
    // ======================================================================

    @Test
    public void testElConceptoDeLaFacturaEsElTextoQueHoySeImprime() {
        assertEquals("Interés compensatorio cuota 1/6", ServicioCobranza.conceptoFacturaDeCuota(
                ServicioCobranza.ConceptoGravado.INTERES, 1, 6));
        assertEquals("Interés moratorio cuota 3/12", ServicioCobranza.conceptoFacturaDeCuota(
                ServicioCobranza.ConceptoGravado.MORATORIO, 3, 12));
        assertEquals("Interés punitorio cuota 3/12", ServicioCobranza.conceptoFacturaDeCuota(
                ServicioCobranza.ConceptoGravado.PUNITORIO, 3, 12));
        assertEquals("Interés compensatorio hasta el 30/10/2026 (anticipo de capital)",
                ServicioCobranza.conceptoFacturaDeAnticipo(
                        ServicioCobranza.ConceptoGravado.INTERES, "30/10/2026"));
    }

    @Test
    public void testElConceptoDeEmisionYElDeImpresionSonElMismoTexto() {
        // El cruce que faltaba: la linea que calcula los totales al emitir y la que se imprime
        // salian de dos redacciones distintas ("Intereses cuota 1 - CC-2026-00004 - vto. ..." vs
        // "Interés compensatorio cuota 1/6"), y la unica que alguien ve es la de impresion.
        Cuota cuota = new Cuota();
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(LocalDate.of(2026, 10, 30));
        cuota.setCapitalProgramado(new BigDecimal("158479"));
        cuota.setInteresProgramado(new BigDecimal("20118"));

        List<LineaFactura> emision = new ServicioCobranza().construirLineasCuota(cuota, 6,
                new BigDecimal("20118"), new BigDecimal("2012"),
                new BigDecimal("610"), new BigDecimal("61"),
                new BigDecimal("183"), new BigDecimal("18"));
        List<LineaFactura> impresion = ServicioCobranza.derivarLineasCuotaFactura(1, 6,
                new BigDecimal("20118"), new BigDecimal("610"), new BigDecimal("183"),
                new BigDecimal("2091"));

        assertEquals(impresion.size(), emision.size());
        for (int i = 0; i < emision.size(); i++) {
            assertEquals(impresion.get(i).getDescripcion(), emision.get(i).getDescripcion());
        }
        assertEquals("Interés compensatorio cuota 1/6", emision.get(0).getDescripcion());
    }

    // ======================================================================
    // C37 (decision de Joel, 01/11/2026, opcion a): el "Son:" del recibo es el EFECTIVO
    // RECIBIDO, no el monto aplicado -- con el redondeo de D-141 no son el mismo numero.
    // ======================================================================

    @Test
    public void testElReciboDeletreaElEfectivoRecibidoYNoElAplicado() throws Exception {
        String recibo = new String(java.nio.file.Files.readAllBytes(
                new java.io.File("src/main/resources/reportes/recibo.jrxml").toPath()), "UTF-8");
        // Los dos ejemplares del talonario deletrean lo RECIBIDO.
        assertEquals(2, contarOcurrencias(recibo, "\"Son: \" + $P{RECIBIDO_LETRAS}"));
        assertFalse(recibo.contains("\"Son: \" + $P{APLICADO_LETRAS}"));
        // Y el monto aplicado sigue estando en el recibo, en su propio renglon.
        assertTrue(recibo.contains("$P{APLICADO}"));
    }

    private static int contarOcurrencias(String texto, String buscado) {
        int total = 0;
        int desde = 0;
        while (true) {
            int i = texto.indexOf(buscado, desde);
            if (i < 0) {
                return total;
            }
            total++;
            desde = i + buscado.length();
        }
    }

    // ==================================================================
    // B199 + B200 + B201 (15/11/2026): lo que le faltaba al cartel de la cancelacion total, y el
    // origen del menu "Cancelacion anticipada" con un solo dueno.
    // ==================================================================

    /** B199: el cartel de la operacion mas grande de la pantalla tiene que decir DE QUIEN es. */
    @Test
    public void testDetalleQuienCancelacionTotalNombraOperacionClienteYCedula() {
        assertEquals("Operación CC-2026-00035 — Juan Pérez, C.I. 1.234.567.",
                ServicioCobranza.detalleQuienCancelacionTotal("CC-2026-00035", "Juan Pérez", "1.234.567"));
    }

    /** La cedula llega YA formateada por su dueno (FormateadorTexto.comoCedulaConPuntos): este
     * metodo no la vuelve a tocar, para que no haya dos criterios de formato. */
    @Test
    public void testDetalleQuienCancelacionTotalNoReformateaLaCedula() {
        assertTrue(ServicioCobranza.detalleQuienCancelacionTotal("CC-1", "Ana", "987.654").contains("C.I. 987.654."));
    }

    /** Un dato que falte no puede dejar el cartel diciendo "null": cae en "-", como el resto de las
     * filas formateadas del sistema. */
    @Test
    public void testDetalleQuienCancelacionTotalConDatosFaltantesNoDiceNull() {
        String detalle = ServicioCobranza.detalleQuienCancelacionTotal(null, null, null);
        assertFalse(detalle.contains("null"));
        assertEquals("Operación - — -, C.I. -.", detalle);
    }

    /** B200: el aviso de la constancia NO promete el numero, porque lo asigna
     * confirmarCancelacionTotal dentro de la transaccion (B4/B43 + B112). */
    @Test
    public void testElAvisoDeLaConstanciaNoPrometeElNumero() {
        assertTrue(ServicioCobranza.AVISO_CONSTANCIA_CANCELACION.contains("recibe su número"));
        assertFalse(ServicioCobranza.AVISO_CONSTANCIA_CANCELACION.contains("CAN-"));
    }

    /** B201: el origen del menu, con un solo dueno -- antes el literal estaba en cuatro vistas. */
    @Test
    public void testElOrigenDeCancelacionAnticipadaTieneUnSoloDueno() {
        assertEquals("cancelacion-anticipada", ServicioCobranza.ORIGEN_CANCELACION_ANTICIPADA);
        assertTrue(ServicioCobranza.esOrigenCancelacionAnticipada("cancelacion-anticipada"));
    }

    /** Null y cualquier otro valor son "no": ahi la pantalla queda igual que la de Cobranza. */
    @Test
    public void testUnOrigenDesconocidoNoActivaCancelacionAnticipada() {
        assertFalse(ServicioCobranza.esOrigenCancelacionAnticipada(null));
        assertFalse(ServicioCobranza.esOrigenCancelacionAnticipada(""));
        assertFalse(ServicioCobranza.esOrigenCancelacionAnticipada("cobranza"));
        assertFalse(ServicioCobranza.esOrigenCancelacionAnticipada("CANCELACION-ANTICIPADA"));
    }

    // ======================================================================
    // B203 (15/11/2026, item 6 del informe de UX de Cancelacion): el desglose de la cancelacion
    // total. Lo que de verdad hay que comprobar no son los rotulos: es que los sumandos DEN el
    // total. Un desglose que no suma el total miente, y el cliente lo esta leyendo justo cuando
    // pregunta que le estan cobrando.
    // ======================================================================

    /** Fecha con 2 cuotas vencidas del plan de prestamoCC1DoceCuotas (vencen el 23 de cada mes
     * desde el 23/11/2026): ahi la cancelacion suma capital vencido y no vencido, interes de las
     * vencidas, interes del periodo en curso, mora y punitorios -- todos los sumandos a la vez. */
    private static final LocalDate FECHA_CON_VENCIDAS = LocalDate.of(2027, 1, 15);

    private BigDecimal sumaDeLosSumandos(List<ServicioCobranza.RenglonDesgloseCancelacion> desglose) {
        BigDecimal suma = BigDecimal.ZERO;
        for (ServicioCobranza.RenglonDesgloseCancelacion renglon : desglose) {
            if (!ServicioCobranza.DESGLOSE_TOTAL_CANCELACION.equals(renglon.getEtiqueta())) {
                suma = suma.add(renglon.getImporte());
            }
        }
        return suma;
    }

    private List<String> etiquetasDe(List<ServicioCobranza.RenglonDesgloseCancelacion> desglose) {
        List<String> etiquetas = new ArrayList<String>();
        for (ServicioCobranza.RenglonDesgloseCancelacion renglon : desglose) {
            etiquetas.add(renglon.getEtiqueta());
        }
        return etiquetas;
    }

    /** Sin cuotas vencidas: capital + interes devengado + su IVA, y nada de mora ni punitorios. */
    @Test
    public void testDesgloseCancelacion_SinVencidas_LosSumandosDanElTotal() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = prestamoCC1DoceCuotas();
        ResultadoLiquidacion liquidacion = motor.calcularCancelacionTotal(prestamo, FECHA_ANTICIPO, DIAS_GRACIA);
        ResultadoDeudaExigible exigible = motor.calcularDeudaExigible(prestamo, FECHA_ANTICIPO, DIAS_GRACIA);

        List<ServicioCobranza.RenglonDesgloseCancelacion> desglose =
                ServicioCobranza.desgloseCancelacionTotal(liquidacion, exigible);

        assertEquals(0, liquidacion.getMontoTotalLiquidacion().compareTo(sumaDeLosSumandos(desglose)));
        assertEquals("Capital que debe hoy", desglose.get(0).getEtiqueta());
        assertEquals(ServicioCobranza.DESGLOSE_TOTAL_CANCELACION,
                desglose.get(desglose.size() - 1).getEtiqueta());
        assertFalse(etiquetasDe(desglose).contains("Mora"));
        assertFalse(etiquetasDe(desglose).contains("Punitorios"));
    }

    /** Con cuotas vencidas: el caso completo. Los sumandos siguen dando el total, y la mora y los
     * punitorios se NOMBRAN en vez de quedar escondidos en el total (RF26 §6, pedido de Joel). */
    @Test
    public void testDesgloseCancelacion_ConVencidas_LosSumandosDanElTotalYSeNombraLaMora() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = prestamoCC1DoceCuotas();
        ResultadoLiquidacion liquidacion = motor.calcularCancelacionTotal(prestamo, FECHA_CON_VENCIDAS, DIAS_GRACIA);
        ResultadoDeudaExigible exigible = motor.calcularDeudaExigible(prestamo, FECHA_CON_VENCIDAS, DIAS_GRACIA);

        List<ServicioCobranza.RenglonDesgloseCancelacion> desglose =
                ServicioCobranza.desgloseCancelacionTotal(liquidacion, exigible);
        List<String> etiquetas = etiquetasDe(desglose);

        assertEquals("el desglose tiene que dar el total: " + liquidacion.getMontoTotalLiquidacion()
                + " vs " + sumaDeLosSumandos(desglose),
                0, liquidacion.getMontoTotalLiquidacion().compareTo(sumaDeLosSumandos(desglose)));
        assertTrue(etiquetas.contains("Mora"));
        assertTrue(etiquetas.contains("Punitorios"));
        assertTrue(etiquetas.contains("Interés devengado hasta hoy"));
    }

    /** La razon de ser del segundo parametro, escrita como prueba para que nadie lo "simplifique":
     * el interes de las cuotas VENCIDAS viaja dentro del total del exigible, no en
     * getInteresDevengado(). Sin el exigible el desglose queda CORTO -- exactamente el error que
     * un desglose armado a ojo con los getters de la liquidacion habria cometido. */
    @Test
    public void testDesgloseCancelacion_SinElExigibleQuedariaCortoConVencidas() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = prestamoCC1DoceCuotas();
        ResultadoLiquidacion liquidacion = motor.calcularCancelacionTotal(prestamo, FECHA_CON_VENCIDAS, DIAS_GRACIA);

        BigDecimal sumaSinExigible = sumaDeLosSumandos(
                ServicioCobranza.desgloseCancelacionTotal(liquidacion, null));

        assertTrue("sin el exigible la suma tiene que quedar por debajo del total",
                sumaSinExigible.compareTo(liquidacion.getMontoTotalLiquidacion()) < 0);
    }

    /** Prestamo ya finalizado (D-146 E-4): no hay liquidacion y no hay desglose -- nunca un
     * desglose en cero, que se leeria como si no debiera nada. */
    @Test
    public void testDesgloseCancelacion_SinLiquidacionNoHayDesglose() {
        assertTrue(ServicioCobranza.desgloseCancelacionTotal(null, null).isEmpty());
    }

    /** La frase que cierra el desglose es la razon por la que existe: la Ley 6624 no deja cobrar
     * interes futuro. No promete ningun monto (eso es B204). */
    @Test
    public void testLaFraseDelDesgloseNombraLaLeyYNoPrometeUnMonto() {
        assertTrue(ServicioCobranza.DESGLOSE_SIN_INTERES_FUTURO.contains("6624"));
        assertFalse(ServicioCobranza.DESGLOSE_SIN_INTERES_FUTURO.contains("Gs."));
    }

    // ======================================================================
    // B204 (15/11/2026, item 5 del informe de UX de Cancelacion; decision de Joel: "entra, no se
    // descarta"): el ahorro de la cancelacion total. El numero lo calcula el MOTOR dentro de
    // calcularCancelacionTotal y viaja en ResultadoLiquidacion.getInteresFuturoNoCobrado(); las
    // pruebas viven aca, con las de B203, porque la verificacion que importa cruza la liquidacion
    // con el exigible -- el mismo par que usa el desglose -- y el fixture ya esta armado aca.
    // ======================================================================

    /** El ahorro, calculado de la forma LARGA e independiente (todo el interes programado que
     * queda, con su IVA, menos todo el interes que esta liquidacion cobra). El motor lo calcula de
     * la forma corta (las vencidas se cancelan solas de la cuenta); si las dos no dan lo mismo, una
     * de las dos esta mal. */
    private BigDecimal ahorroCalculadoAparte(Prestamo prestamo, ResultadoLiquidacion liq,
            ResultadoDeudaExigible exi) {
        BigDecimal programado = BigDecimal.ZERO;
        for (Cuota c : prestamo.getCuotas()) {
            if (c.getEstadoPago() == EstadoPago.PAGADA) {
                continue;
            }
            BigDecimal interes = c.interesPendiente();
            programado = programado.add(interes).add(ServicioMotorFinanciero.iva(interes));
        }
        BigDecimal cobrado = liq.getInteresDevengado().add(liq.getIvaInteresDevengado());
        for (py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible d : exi.getDetalles()) {
            cobrado = cobrado.add(d.getInteresPendiente()).add(d.getIvaInteresPendiente());
        }
        return programado.subtract(cobrado);
    }

    /** Sin cuotas vencidas: el ahorro es todo el interes futuro del plan menos el devengado de
     * estos dias. Medido el 15/11/2026 con este mismo plan: 436.897 Gs. al 27/10. */
    @Test
    public void testAhorroCancelacion_SinVencidas_EsElInteresFuturoDelPlan() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = prestamoCC1DoceCuotas();
        ResultadoLiquidacion liq = motor.calcularCancelacionTotal(prestamo, FECHA_ANTICIPO, DIAS_GRACIA);
        ResultadoDeudaExigible exi = motor.calcularDeudaExigible(prestamo, FECHA_ANTICIPO, DIAS_GRACIA);

        assertEquals(0, ahorroCalculadoAparte(prestamo, liq, exi)
                .compareTo(liq.getInteresFuturoNoCobrado()));
        assertEquals(0, new BigDecimal("436897").compareTo(liq.getInteresFuturoNoCobrado()));
    }

    /** Con cuotas vencidas, que es el caso que decidio la formula (75 de 190 prestamos activos la
     * tenian el 15/11/2026): el ahorro NO le descuenta la mora. La opcion que se descarto ("lo que
     * faltaba del plan menos el total a cancelar") da menos, y la diferencia es EXACTAMENTE la mora
     * con sus punitorios e IVA -- un castigo, que no es un ahorro menos. */
    @Test
    public void testAhorroCancelacion_ConVencidas_NoLeDescuentaLaMora() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        Prestamo prestamo = prestamoCC1DoceCuotas();
        ResultadoLiquidacion liq = motor.calcularCancelacionTotal(prestamo, FECHA_CON_VENCIDAS, DIAS_GRACIA);
        ResultadoDeudaExigible exi = motor.calcularDeudaExigible(prestamo, FECHA_CON_VENCIDAS, DIAS_GRACIA);

        assertEquals(0, ahorroCalculadoAparte(prestamo, liq, exi)
                .compareTo(liq.getInteresFuturoNoCobrado()));

        BigDecimal planQueFaltaba = BigDecimal.ZERO;
        for (Cuota c : prestamo.getCuotas()) {
            if (c.getEstadoPago() != EstadoPago.PAGADA) {
                planQueFaltaba = planQueFaltaba.add(c.getTotalProgramado())
                        .subtract(c.getCapitalPagado()).subtract(c.getInteresPagado());
            }
        }
        BigDecimal opcionDescartada = planQueFaltaba.subtract(liq.getMontoTotalLiquidacion());
        BigDecimal mora = liq.getMoratorioPendiente().add(liq.getIvaMoratorioPendiente())
                .add(liq.getPunitorioPendiente()).add(liq.getIvaPunitorioPendiente());

        assertTrue("con mora, el ahorro tiene que ser mayor que plan - total",
                liq.getInteresFuturoNoCobrado().compareTo(opcionDescartada) > 0);
        assertTrue("la mora de este caso no puede ser cero, o la prueba no prueba nada",
                mora.compareTo(BigDecimal.ZERO) > 0);
        assertEquals("la diferencia entre las dos formulas es exactamente la mora",
                0, liq.getInteresFuturoNoCobrado().compareTo(opcionDescartada.add(mora)));
    }

    /** El ahorro nunca es negativo, en ninguna fecha del prestamo. */
    @Test
    public void testAhorroCancelacion_NuncaEsNegativo() {
        ServicioMotorFinanciero motor = new ServicioMotorFinanciero();
        for (LocalDate hoy = LocalDate.of(2026, 10, 23); hoy.isBefore(LocalDate.of(2028, 1, 1));
                hoy = hoy.plusDays(7)) {
            BigDecimal ahorro = motor.calcularCancelacionTotal(prestamoCC1DoceCuotas(), hoy, DIAS_GRACIA)
                    .getInteresFuturoNoCobrado();
            assertTrue("negativo el " + hoy + ": " + ahorro, ahorro.compareTo(BigDecimal.ZERO) >= 0);
        }
    }

    /** En un ADELANTO el campo es null, no cero: esa cuenta es otra y tiene otro dueno
     * (ControladorCobranzaServlet.ahorroDeInteresesPorCuotas). */
    @Test
    public void testAhorroCancelacion_EnUnAnticipoEsNull() {
        ResultadoLiquidacion anticipo = new ServicioMotorFinanciero().calcularAnticipoParcial(
                prestamoCC1DoceCuotas(), montoParaPagar(4), FECHA_ANTICIPO,
                Collections.<LocalDate>emptySet());
        assertNull(anticipo.getInteresFuturoNoCobrado());
    }

    /** La frase: la redaccion del adelanto, con el monto en medio de la oracion -- comoGuaranies ya
     * termina en "Gs.", asi que el punto final es el de la frase y NO queda un "Gs..". */
    @Test
    public void testLaFraseDelAhorroDiceElMontoYNoDuplicaElPunto() {
        String frase = ServicioCobranza.fraseAhorroCancelacionTotal(new BigDecimal("277598"));
        assertEquals("El cliente se ahorra 277.598 Gs. de intereses por cancelar hoy.", frase);
        assertFalse(frase.contains("Gs.."));
    }

    /** Sin ahorro no hay frase: "se ahorra 0" seria peor que no decir nada, y null es el caso del
     * adelanto, que nunca tiene que escribir este texto. */
    @Test
    public void testSinAhorroNoHayFrase() {
        assertEquals("", ServicioCobranza.fraseAhorroCancelacionTotal(null));
        assertEquals("", ServicioCobranza.fraseAhorroCancelacionTotal(BigDecimal.ZERO));
        assertEquals("", ServicioCobranza.fraseAhorroCancelacionTotal(new BigDecimal("-5")));
    }
}
