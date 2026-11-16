package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.DetalleAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCapacidadPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoMora;
import py.com.capitalcredito.sgcpc.dto.ResultadoTasasMora;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;

/**
 * Casos financieros de referencia del Anexo C (Doc. 00 §18.1): BF-01 a
 * BF-10 tal como estan documentados, mas BF-11 y BF-12 agregados por Joel
 * para cubrir anticipo a mitad de periodo y mora por tramos. Los importes
 * esperados vienen literales del Anexo C / de la instruccion de Joel; si
 * un calculo no coincide, el error esta en ServicioMotorFinanciero, no en
 * el valor esperado.
 */
public class ServicioMotorFinancieroTest {

    private static final BigDecimal TEA = new BigDecimal("0.27");
    private static final BigDecimal TASA_PUNITORIO = new BigDecimal("0.30");
    private static final BigDecimal LIMITE_AFECTACION = new BigDecimal("0.40");

    private final ServicioMotorFinanciero servicio = new ServicioMotorFinanciero();

    private static void assertMonto(long esperado, BigDecimal obtenido) {
        assertEquals("esperado " + esperado + " obtenido " + obtenido,
                0, BigDecimal.valueOf(esperado).compareTo(obtenido));
    }

    private List<Cuota> planBF01() {
        return servicio.generarPlanPagos(new BigDecimal("500000"), 3, TEA, LocalDate.of(2026, 11, 2),
                Collections.<LocalDate>emptySet());
    }

    private Prestamo prestamoBF01(List<Cuota> cuotas) {
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(LocalDate.of(2026, 10, 2));
        prestamo.setTasaTea(TEA);
        prestamo.setTasaMoratoria(TEA);
        prestamo.setTasaPunitoria(TASA_PUNITORIO);
        prestamo.setCuotas(cuotas);
        return prestamo;
    }

    /**
     * D-83: plan REAL de CC-2026-00023 (sgcpc_db, id_prestamo 23 -- préstamo de prueba con
     * cuotas vencidas del bloque 4 de E7, AVANCE.md), leído por SELECT de solo lectura, no
     * inventado. Mismo monto/plazo/tasas que CC-2026-00014 (3.000.000, 12 meses): mismos
     * importes por cuota, fechas propias (desembolso 18/07/2026).
     */
    private static final Object[][] PLAN_CC23 = {
        // numero, vtoEfectiva, vtoTeorica, capital, interes, tributos, total, saldoPosterior
        {1, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19), 223531L, 60353L, 6035L, 289919L, 2776469L},
        {2, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 19), 228028L, 55856L, 5586L, 289470L, 2548441L},
        {3, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19), 232615L, 51269L, 5127L, 289011L, 2315826L},
        {4, LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), 237295L, 46589L, 4659L, 288543L, 2078531L},
        {5, LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 20), 242069L, 41815L, 4182L, 288066L, 1836462L},
        {6, LocalDate.of(2027, 1, 18), LocalDate.of(2027, 1, 17), 246938L, 36946L, 3695L, 287579L, 1589524L},
        {7, LocalDate.of(2027, 2, 17), LocalDate.of(2027, 2, 17), 251906L, 31978L, 3198L, 287082L, 1337618L},
        {8, LocalDate.of(2027, 3, 19), LocalDate.of(2027, 3, 19), 256974L, 26910L, 2691L, 286575L, 1080644L},
        {9, LocalDate.of(2027, 4, 19), LocalDate.of(2027, 4, 19), 262144L, 21740L, 2174L, 286058L, 818500L},
        {10, LocalDate.of(2027, 5, 19), LocalDate.of(2027, 5, 19), 267418L, 16466L, 1647L, 285531L, 551082L},
        {11, LocalDate.of(2027, 6, 21), LocalDate.of(2027, 6, 19), 272797L, 11087L, 1109L, 284993L, 278285L},
        {12, LocalDate.of(2027, 7, 20), LocalDate.of(2027, 7, 20), 278285L, 5598L, 560L, 284443L, 0L},
    };

    private List<Cuota> planCC23() {
        List<Cuota> cuotas = new ArrayList<>();
        for (Object[] fila : PLAN_CC23) {
            Cuota cuota = new Cuota();
            cuota.setNumeroCuota((Integer) fila[0]);
            cuota.setFechaVencimiento((LocalDate) fila[1]);
            cuota.setFechaVencimientoTeorica((LocalDate) fila[2]);
            cuota.setCapitalProgramado(BigDecimal.valueOf((Long) fila[3]));
            cuota.setInteresProgramado(BigDecimal.valueOf((Long) fila[4]));
            cuota.setTributosProgramados(BigDecimal.valueOf((Long) fila[5]));
            cuota.setTotalProgramado(BigDecimal.valueOf((Long) fila[6]));
            cuota.setSaldoCapitalPosterior(BigDecimal.valueOf((Long) fila[7]));
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            cuotas.add(cuota);
        }
        return cuotas;
    }

    private Prestamo prestamoCC23() {
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(LocalDate.of(2026, 7, 18));
        prestamo.setTasaTea(new BigDecimal("0.270000"));
        prestamo.setTasaMoratoria(new BigDecimal("0.270000"));
        prestamo.setTasaPunitoria(new BigDecimal("0.300000"));
        prestamo.setCuotas(planCC23());
        return prestamo;
    }

    private Cuota marcarPagada(Cuota original) {
        Cuota copia = new Cuota(original);
        copia.setCapitalPagado(copia.getCapitalProgramado());
        copia.setInteresPagado(copia.getInteresProgramado());
        copia.setEstadoPago(EstadoPago.PAGADA);
        return copia;
    }

    /**
     * D-88 (barrido de fechas): plan ORIGINAL real de CC-2026-00014 (sgcpc_db, leído por SELECT
     * de solo lectura), sin ningún pago -- mismo monto/plazo/tasas que CC-2026-00023 (D-83), pero
     * con sus propias fechas (desembolso 26/09/2026), y sin ningún anticipo de por medio: sirve
     * de fixture "simple" (plan ORIGINAL) para contrastar con el plan ya regenerado de CC-23.
     */
    private static final Object[][] PLAN_CC14 = {
        // numero, vtoEfectiva, vtoTeorica, capital, interes, tributos, total, saldoPosterior
        {1, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 26), 223531L, 60353L, 6035L, 289919L, 2776469L},
        {2, LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26), 228028L, 55856L, 5586L, 289470L, 2548441L},
        {3, LocalDate.of(2026, 12, 28), LocalDate.of(2026, 12, 26), 232615L, 51269L, 5127L, 289011L, 2315826L},
        {4, LocalDate.of(2027, 1, 26), LocalDate.of(2027, 1, 26), 237295L, 46589L, 4659L, 288543L, 2078531L},
        {5, LocalDate.of(2027, 2, 26), LocalDate.of(2027, 2, 26), 242069L, 41815L, 4182L, 288066L, 1836462L},
        {6, LocalDate.of(2027, 3, 29), LocalDate.of(2027, 3, 26), 246938L, 36946L, 3695L, 287579L, 1589524L},
        {7, LocalDate.of(2027, 4, 26), LocalDate.of(2027, 4, 26), 251906L, 31978L, 3198L, 287082L, 1337618L},
        {8, LocalDate.of(2027, 5, 26), LocalDate.of(2027, 5, 26), 256974L, 26910L, 2691L, 286575L, 1080644L},
        {9, LocalDate.of(2027, 6, 28), LocalDate.of(2027, 6, 26), 262144L, 21740L, 2174L, 286058L, 818500L},
        {10, LocalDate.of(2027, 7, 26), LocalDate.of(2027, 7, 26), 267418L, 16466L, 1647L, 285531L, 551082L},
        {11, LocalDate.of(2027, 8, 26), LocalDate.of(2027, 8, 26), 272797L, 11087L, 1109L, 284993L, 278285L},
        {12, LocalDate.of(2027, 9, 27), LocalDate.of(2027, 9, 26), 278285L, 5598L, 560L, 284443L, 0L},
    };

    private List<Cuota> planCC14SinPagos() {
        List<Cuota> cuotas = new ArrayList<>();
        for (Object[] fila : PLAN_CC14) {
            Cuota cuota = new Cuota();
            cuota.setNumeroCuota((Integer) fila[0]);
            cuota.setFechaVencimiento((LocalDate) fila[1]);
            cuota.setFechaVencimientoTeorica((LocalDate) fila[2]);
            cuota.setCapitalProgramado(BigDecimal.valueOf((Long) fila[3]));
            cuota.setInteresProgramado(BigDecimal.valueOf((Long) fila[4]));
            cuota.setTributosProgramados(BigDecimal.valueOf((Long) fila[5]));
            cuota.setTotalProgramado(BigDecimal.valueOf((Long) fila[6]));
            cuota.setSaldoCapitalPosterior(BigDecimal.valueOf((Long) fila[7]));
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            cuotas.add(cuota);
        }
        return cuotas;
    }

    private Prestamo prestamoCC14SinPagos() {
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(LocalDate.of(2026, 9, 26));
        prestamo.setTasaTea(new BigDecimal("0.270000"));
        prestamo.setTasaMoratoria(new BigDecimal("0.270000"));
        prestamo.setTasaPunitoria(new BigDecimal("0.300000"));
        prestamo.setCuotas(planCC14SinPagos());
        return prestamo;
    }

    @Test
    public void testBF01_CotizacionYPlanDePagos() {
        ResultadoCotizacion resultado = servicio.cotizar(new BigDecimal("500000"), 3, LocalDate.of(2026, 11, 2),
                TEA, Collections.<LocalDate>emptySet());

        assertMonto(173417, resultado.getCuotaSinIva());
        assertMonto(522276, resultado.getMontoTotalConIva());

        List<Cuota> plan = resultado.getPlan();
        assertEquals(3, plan.size());

        assertMonto(10059, plan.get(0).getInteresProgramado());
        assertMonto(6772, plan.get(1).getInteresProgramado());
        assertMonto(3420, plan.get(2).getInteresProgramado());

        assertMonto(163358, plan.get(0).getCapitalProgramado());
        assertMonto(166645, plan.get(1).getCapitalProgramado());
        assertMonto(169997, plan.get(2).getCapitalProgramado());

        assertMonto(1006, plan.get(0).getTributosProgramados());
        assertMonto(677, plan.get(1).getTributosProgramados());
        assertMonto(342, plan.get(2).getTributosProgramados());

        assertMonto(174423, plan.get(0).getTotalProgramado());
        assertMonto(174094, plan.get(1).getTotalProgramado());
        assertMonto(173759, plan.get(2).getTotalProgramado());

        assertMonto(336642, plan.get(0).getSaldoCapitalPosterior());
        assertMonto(169997, plan.get(1).getSaldoCapitalPosterior());
        assertMonto(0, plan.get(2).getSaldoCapitalPosterior());

        // BF-10: la cuota 3 vence teoricamente sabado 02/01/2027 -> se corre al lunes 04/01/2027 (RN-19)
        assertEquals(LocalDate.of(2027, 1, 2), plan.get(2).getFechaVencimientoTeorica());
        assertEquals(LocalDate.of(2027, 1, 4), plan.get(2).getFechaVencimiento());
    }

    @Test
    public void testBF02_RedondeoUltimaCuota() {
        List<Cuota> plan = planBF01();
        Cuota ultima = plan.get(plan.size() - 1);

        assertMonto(0, ultima.getSaldoCapitalPosterior());
        assertEquals(0, plan.get(1).getSaldoCapitalPosterior().compareTo(ultima.getCapitalProgramado()));
    }

    @Test
    public void testBF03_CapacidadDePagoDentroDelLimite() {
        ResultadoCapacidadPago resultado = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("3000000"), null);

        assertTrue(resultado.getPorcentajeAfectacion().compareTo(LIMITE_AFECTACION) <= 0);
        assertEquals(0, new BigDecimal("5.8").compareTo(
                resultado.getPorcentajeAfectacion().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
    }

    @Test
    public void testBF04_CapacidadDePagoSuperaElLimite() {
        ResultadoCapacidadPago resultado = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("400000"), null);

        assertTrue(resultado.getPorcentajeAfectacion().compareTo(LIMITE_AFECTACION) > 0);
        assertEquals(0, new BigDecimal("43.6").compareTo(
                resultado.getPorcentajeAfectacion().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
    }

    // --- Bloque 7/E10, Tanda B (D-127, Opción A elegida por Joel): cuota de otras deudas ---

    @Test
    public void testCapacidadSumaCuotaDeOtrasDeudas() {
        // BF-03 (174.423 / 3.000.000 = 5,8%) + 1.025.577 de otras deudas -> exactamente 40%.
        ResultadoCapacidadPago resultado = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("3000000"), new BigDecimal("1025577"));
        assertTrue(resultado.getPorcentajeAfectacion().compareTo(LIMITE_AFECTACION) <= 0);
        assertEquals(0, new BigDecimal("40.0").compareTo(
                resultado.getPorcentajeAfectacion().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
    }

    @Test
    public void testCapacidadConCuotaDeOtrasDeudasSuperaElLimite() {
        ResultadoCapacidadPago resultado = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("3000000"), new BigDecimal("1100000"));
        assertTrue(resultado.getPorcentajeAfectacion().compareTo(LIMITE_AFECTACION) > 0);
    }

    @Test
    public void testCapacidadConCuotaDeOtrasDeudasNulaEsIgualQueCero() {
        ResultadoCapacidadPago conNull = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("3000000"), null);
        ResultadoCapacidadPago conCero = servicio.calcularCapacidadPago(new BigDecimal("174423"),
                new BigDecimal("3000000"), BigDecimal.ZERO);
        assertEquals(0, conNull.getPorcentajeAfectacion().compareTo(conCero.getPorcentajeAfectacion()));
    }

    @Test
    public void testBF05_BloqueoPorTopeDeTasa() {
        ResultadoLimiteTasa resultado = servicio.validarLimiteTasa(new BigDecimal("0.385"), new BigDecimal("0.2739"));

        assertFalse(resultado.isOk());
    }

    @Test
    public void testBF06_MoraDeLaCuota1() {
        Cuota cuota1 = planBF01().get(0);

        ResultadoMora mora = servicio.calcularMora(cuota1, LocalDate.of(2026, 11, 17), TEA, TASA_PUNITORIO, 0);

        assertEquals(15, mora.getDias());
        assertMonto(1613, mora.getMoratorioTramo());
        assertMonto(161, mora.getIvaMoratorioTramo());
        assertMonto(484, mora.getPunitorioTramo());
        assertMonto(48, mora.getIvaPunitorioTramo());

        BigDecimal totalAPagar = cuota1.getTotalProgramado()
                .add(mora.getMoratorioTramo()).add(mora.getIvaMoratorioTramo())
                .add(mora.getPunitorioTramo()).add(mora.getIvaPunitorioTramo());
        assertMonto(176729, totalAPagar);
    }

    /** D-77 (ajuste de Joel): dentro de los días de gracia, la mora del tramo es cero. */
    @Test
    public void testBF06b_DiasDeGraciaAnulaLaMoraDentroDelPlazo() {
        Cuota cuota1 = planBF01().get(0);

        ResultadoMora mora = servicio.calcularMora(cuota1, LocalDate.of(2026, 11, 7), TEA, TASA_PUNITORIO, 5);

        assertEquals(5, mora.getDias());
        assertMonto(0, mora.getMoratorioTramo());
        assertMonto(0, mora.getIvaMoratorioTramo());
        assertMonto(0, mora.getPunitorioTramo());
        assertMonto(0, mora.getIvaPunitorioTramo());
    }

    /**
     * D-77 (ajuste de Joel): pasados los días de gracia, la mora se cuenta COMPLETA desde la
     * fecha de vencimiento (no se restan los días de gracia) -- mismo resultado que BF06 con
     * dias_gracia=0, para los mismos 15 días transcurridos.
     */
    @Test
    public void testBF06c_DiasDeGraciaNoReduceLaMoraDespuesDelPlazo() {
        Cuota cuota1 = planBF01().get(0);

        ResultadoMora mora = servicio.calcularMora(cuota1, LocalDate.of(2026, 11, 17), TEA, TASA_PUNITORIO, 5);

        assertEquals(15, mora.getDias());
        assertMonto(1613, mora.getMoratorioTramo());
        assertMonto(161, mora.getIvaMoratorioTramo());
        assertMonto(484, mora.getPunitorioTramo());
        assertMonto(48, mora.getIvaPunitorioTramo());
    }

    /**
     * D-82: la función que arma las tasas de mora al formalizar (RN-16/D-20) -- tasaMoratoria =
     * TEA aprobada × factor_moratorio; tasaPunitoria es el porcentaje_punitorio TAL CUAL, sin
     * multiplicarlo por la tasaMoratoria (el bug real: ServicioFormalizacion guardaba
     * tasaMoratoria × porcentajePunitorio = 0,081 en vez de 0,30).
     */
    @Test
    public void testCalcularTasasMoraDevuelveMoratoriaYPunitoriaSegunLosParametros() {
        ResultadoTasasMora tasas = ServicioMotorFinanciero.calcularTasasMora(TEA, new BigDecimal("1.000000"),
                TASA_PUNITORIO);

        assertEquals(0, new BigDecimal("0.270000").compareTo(tasas.getTasaMoratoria()));
        assertEquals(0, new BigDecimal("0.300000").compareTo(tasas.getTasaPunitoria()));
    }

    /**
     * D-82: encadena calcularTasasMora con calcularMora (con los parámetros reales de
     * credito.factor_moratorio=1 y credito.porcentaje_punitorio=0,30) y reproduce BF-06 --
     * demuestra que el CAMINO REAL de producción (armar las tasas al formalizar, no pasarlas ya
     * corregidas a mano como en BF-06) también da el punitorio correcto (484, no 8,1 % de 1.613).
     */
    @Test
    public void testBF06d_TasasArmadasAlFormalizarReproducenElPunitorioDeBF06() {
        ResultadoTasasMora tasas = ServicioMotorFinanciero.calcularTasasMora(TEA, new BigDecimal("1.000000"),
                TASA_PUNITORIO);
        Cuota cuota1 = planBF01().get(0);

        ResultadoMora mora = servicio.calcularMora(cuota1, LocalDate.of(2026, 11, 17), tasas.getTasaMoratoria(),
                tasas.getTasaPunitoria(), 0);

        assertMonto(1613, mora.getMoratorioTramo());
        assertMonto(484, mora.getPunitorioTramo());
        assertMonto(48, mora.getIvaPunitorioTramo());
    }

    @Test
    public void testBF07_AplicacionDePagoParcialConMora() {
        Prestamo prestamo = prestamoBF01(new ArrayList<>(Arrays.asList(planBF01().get(0))));

        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("100000"),
                LocalDate.of(2026, 11, 17), 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);

        assertMonto(1613, detalle.getMontoMoratorio());
        assertMonto(161, detalle.getIvaMoratorio());
        assertMonto(1774, detalle.getMoratorioConIva());

        assertMonto(484, detalle.getMontoPunitorio());
        assertMonto(48, detalle.getIvaPunitorio());
        assertMonto(532, detalle.getPunitorioConIva());

        assertMonto(10059, detalle.getMontoInteres());
        assertMonto(1006, detalle.getIvaInteres());
        assertMonto(11065, detalle.getInteresConIva());

        assertMonto(86629, detalle.getMontoCapital());
        assertMonto(0, resultado.getMontoSobrante());
        assertEquals(EstadoPago.PARCIAL, detalle.getCuotaResultante().getEstadoPago());
    }

    // --- D-85: el reparto de un remanente insuficiente entre neto e IVA (Sec. 6.5 punto 3)
    // tiene que garantizar SIEMPRE que iva == round(neto × 10 %) -- antes, con 16 Gs
    // disponibles, daba neto=15/iva=1 (round(15×10%)=2≠1): al reconstruir la línea de la
    // factura sumando round(neto×10%) en vez del iva realmente aplicado, la línea no cuadraba
    // con lo cobrado. ---

    @Test
    public void testAplicarConceptoConRemanente16GuaraniesDaIvaConsistenteConElNeto() {
        BigDecimal[] resultado = servicio.aplicarConcepto(new BigDecimal("16"), new BigDecimal("1000"),
                new BigDecimal("100"));

        assertMonto(14, resultado[0]); // neto
        assertMonto(1, resultado[1]); // iva == round(14 x 10%) = round(1,4) = 1
        assertMonto(1, resultado[2]); // el guaraní que sobra pasa al siguiente concepto (RN-10)
    }

    @Test
    public void testAplicarConceptoConRemanente6GuaraniesDaIvaConsistenteConElNeto() {
        BigDecimal[] resultado = servicio.aplicarConcepto(new BigDecimal("6"), new BigDecimal("1000"),
                new BigDecimal("100"));

        assertMonto(5, resultado[0]);
        assertMonto(1, resultado[1]); // round(5 x 10%) = round(0,5) = 1
        assertMonto(0, resultado[2]); // acá no sobra nada (ya coincidía antes del fix)
    }

    @Test
    public void testAplicarConceptoConRemanente958GuaraniesDaIvaConsistenteConElNeto() {
        BigDecimal[] resultado = servicio.aplicarConcepto(new BigDecimal("958"), new BigDecimal("2000"),
                new BigDecimal("200"));

        assertMonto(871, resultado[0]);
        assertMonto(87, resultado[1]); // round(871 x 10%) = round(87,1) = 87
        assertMonto(0, resultado[2]); // tampoco sobra nada acá (ya coincidía antes del fix)
    }

    /**
     * D-85: el guaraní que sobra de un concepto insuficiente no se pierde -- sigue el orden
     * RN-10 (moratorio → punitorio → interés → capital). Cuota con capital ya saldado (para que
     * el "tramo" de mora dé siempre cero, sin importar los días -- calcularMora multiplica por
     * el capital pendiente) y moratorio_pendiente/iva en cero (sin nada que cobrar ahí):
     * el pago de 16 Gs le sobra a punitorio (mucho más caro que eso) exactamente 1 Gs, que
     * termina aplicado como interés (neto=1, iva=0, redondeado hacia abajo).
     */
    @Test
    public void testDistribuirPagoConSobranteDeUnConceptoLoAplicaAlSiguienteEnElOrdenRN10() {
        Cuota cuota = new Cuota();
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(LocalDate.of(2026, 11, 2));
        cuota.setFechaVencimientoTeorica(LocalDate.of(2026, 11, 2));
        cuota.setCapitalProgramado(new BigDecimal("500000"));
        cuota.setCapitalPagado(new BigDecimal("500000")); // capital ya saldado -> tramo de mora = 0 siempre
        cuota.setInteresProgramado(new BigDecimal("1000"));
        cuota.setInteresPagado(BigDecimal.ZERO);
        cuota.setTributosProgramados(new BigDecimal("100"));
        cuota.setTotalProgramado(new BigDecimal("1100"));
        cuota.setSaldoCapitalPosterior(BigDecimal.ZERO);
        cuota.setEstadoPago(EstadoPago.PENDIENTE);
        cuota.setMoratorioPendiente(BigDecimal.ZERO);
        cuota.setIvaMoratorioPendiente(BigDecimal.ZERO);
        cuota.setPunitorioPendiente(new BigDecimal("1000"));
        cuota.setIvaPunitorioPendiente(new BigDecimal("100"));

        Prestamo prestamo = prestamoBF01(new ArrayList<>(Arrays.asList(cuota)));

        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("16"),
                LocalDate.of(2026, 11, 17), 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);

        assertMonto(0, detalle.getMontoMoratorio());
        assertMonto(14, detalle.getMontoPunitorio());
        assertMonto(1, detalle.getIvaPunitorio());
        assertMonto(1, detalle.getMontoInteres()); // el guaraní que sobró de punitorio
        assertMonto(0, detalle.getIvaInteres()); // round(1 x 10%) = round(0,1) = 0
        assertMonto(0, detalle.getMontoCapital());
        assertMonto(16, detalle.getSubtotalAplicado()); // nada se pierde: 14+1+1+0 = 16
        assertMonto(0, resultado.getMontoSobrante());
    }

    @Test
    public void testBF08_AnticipoParcialRecalculaCuotas() {
        LocalDate fechaAnticipo = LocalDate.of(2026, 6, 2);

        Cuota cuota1 = new Cuota();
        cuota1.setNumeroCuota(1);
        cuota1.setFechaVencimientoTeorica(fechaAnticipo);
        cuota1.setFechaVencimiento(fechaAnticipo);
        cuota1.setCapitalProgramado(new BigDecimal("473140"));
        cuota1.setInteresProgramado(BigDecimal.ZERO);
        cuota1.setTributosProgramados(BigDecimal.ZERO);
        cuota1.setTotalProgramado(new BigDecimal("473140"));
        cuota1.setSaldoCapitalPosterior(BigDecimal.ZERO);
        cuota1.setEstadoPago(EstadoPago.PAGADA);
        cuota1.setCapitalPagado(new BigDecimal("473140"));

        Cuota cuota2 = new Cuota();
        cuota2.setNumeroCuota(2);
        LocalDate finPeriodo = fechaAnticipo.plusMonths(1);
        cuota2.setFechaVencimientoTeorica(finPeriodo);
        cuota2.setFechaVencimiento(finPeriodo);
        cuota2.setCapitalProgramado(new BigDecimal("3473140"));
        cuota2.setInteresProgramado(BigDecimal.ZERO);
        cuota2.setTributosProgramados(BigDecimal.ZERO);
        cuota2.setTotalProgramado(new BigDecimal("3473140"));
        cuota2.setSaldoCapitalPosterior(BigDecimal.ZERO);
        cuota2.setEstadoPago(EstadoPago.PENDIENTE);

        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(fechaAnticipo.minusMonths(1));
        prestamo.setTasaTea(TEA);
        prestamo.setTasaMoratoria(TEA);
        prestamo.setTasaPunitoria(TASA_PUNITORIO);
        prestamo.setCuotas(new ArrayList<>(Arrays.asList(cuota1, cuota2)));

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("473140"),
                fechaAnticipo, Collections.<LocalDate>emptySet());

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(3000000, resultado.getNuevoSaldoCapital());
        assertEquals(7, resultado.getNuevoPlan().size());

        Cuota primeraNueva = resultado.getNuevoPlan().get(0);
        assertMonto(473140, primeraNueva.getInteresProgramado().add(primeraNueva.getCapitalProgramado()));
    }

    @Test
    public void testBF09_CancelacionTotalAntesDeTiempo() {
        List<Cuota> plan = planBF01();
        List<Cuota> cuotas = new ArrayList<>();
        cuotas.add(marcarPagada(plan.get(0)));
        cuotas.add(plan.get(1));
        cuotas.add(plan.get(2));
        Prestamo prestamo = prestamoBF01(cuotas);

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, LocalDate.of(2026, 11, 12), 0);

        assertMonto(336642, resultado.getSaldoCapitalFecha());
        assertMonto(2257, resultado.getInteresDevengado());
        assertMonto(226, resultado.getIvaInteresDevengado());
        assertMonto(339125, resultado.getMontoTotalLiquidacion());
    }

    /**
     * D-83 (bug real, encontrado por Joel probando /cobranza/23 al 26/09/2026): cancelación
     * total con cuotas VENCIDAS. Antes del fix, cuotaEnCurso devolvía la cuota 1 (vencida, "la
     * primera no pagada") y anclaba el período en el suyo (desde el desembolso, 32 días, con
     * 70 días transcurridos -- ¡más días que los que tiene el propio período!), cobrando de más
     * un interés que ya estaba cubierto por la mora de las cuotas 1 y 2 (doble cobro, RN-11).
     * Con el fix, el período en curso es el de la cuota 3 (la primera NO vencida): del
     * vencimiento teórico de la cuota 2 (19/09) al de la cuota 3 (19/10), 30 días, de los que
     * transcurrieron 7 al 26/09/2026. Total esperado: 588.512 (cuotas 1 y 2 vencidas, con mora
     * e IVA) + 2.548.441 (capital no vencido) + 11.963 (interés del período, 7/30 días) + 1.196
     * (su IVA) = 3.150.112.
     */
    @Test
    public void testBF13_CancelacionTotalConCuotasVencidasNoDuplicaElInteresDelPeriodoEnCurso() {
        Prestamo prestamo = prestamoCC23();

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, LocalDate.of(2026, 9, 26), 0);

        assertMonto(11963, resultado.getInteresDevengado());
        assertMonto(1196, resultado.getIvaInteresDevengado());
        assertMonto(3150112, resultado.getMontoTotalLiquidacion());
    }

    /**
     * PA-16/D-111 (Bloque 7/E10): mismo préstamo y fecha que BF-13 (cuotas 1 y 2 VENCIDAS de
     * verdad, no un caso armado). La constancia de cancelación imprime 4 líneas -- "Vencidas
     * impagas", "Capital no vencido", "Interés del período" e IVA -- que un lector sumaría para
     * confirmar el "Total cancelado" impreso al pie. Dos partes:
     *
     * 1) Reproduce el bug tal como estaba (D-93): usando saldoCapitalFecha (capitalVencido +
     * capitalNoVencido combinado, ServicioMotorFinanciero línea 701) como "Capital no vencido",
     * la suma de las 4 líneas da 451.559 Gs MÁS que el total real -- exactamente el capital
     * vencido de las cuotas 1 y 2, contado dos veces (una acá, otra ya dentro de "Vencidas
     * impagas"). El total real (montoTotalLiquidacion) siempre fue correcto: la plata cobrada al
     * cliente nunca estuvo mal, solo el desglose impreso.
     *
     * 2) Verifica la corrección (D-111, ServicioCobranza.generarPdfConstancia +
     * AplicacionPagoDAO.sumarCapitalNoVencidoPorPago): con el capital no vencido recalculado de
     * forma INDEPENDIENTE directo desde el plan de cuotas (mismo filtro que usa la reconstrucción
     * real desde aplicaciones_pago -- solo cuotas pendientes cuya fecha de vencimiento todavía no
     * llegó), la suma de las 4 líneas cierra EXACTO contra el total real.
     */
    @Test
    public void testPA16_ConstanciaCancelacionConVencidasCierraAlGuaraniTrasLaCorreccion() {
        Prestamo prestamo = prestamoCC23();
        LocalDate fecha = LocalDate.of(2026, 9, 26);

        ResultadoDeudaExigible exigibleVencidas = servicio.calcularDeudaExigible(prestamo, fecha, 0);
        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, fecha, 0);

        // 1) Antes de la corrección: "Capital no vencido" = saldoCapitalFecha (con capitalVencido
        // adentro) -- no cierra.
        BigDecimal sumaConElCampoViejo = exigibleVencidas.getTotalGeneral()
                .add(resultado.getSaldoCapitalFecha())
                .add(resultado.getInteresDevengado())
                .add(resultado.getIvaInteresDevengado());
        assertNotEquals(resultado.getMontoTotalLiquidacion(), sumaConElCampoViejo);
        assertMonto(451559, sumaConElCampoViejo.subtract(resultado.getMontoTotalLiquidacion()));

        // 2) Después de la corrección: "Capital no vencido" recalculado de forma independiente
        // desde el plan de cuotas (mismo criterio que D-111 reconstruye desde aplicaciones_pago:
        // capital pendiente de cada cuota PENDIENTE cuya fecha de vencimiento >= la fecha de
        // cálculo) -- nunca reusa saldoCapitalFecha ni ningún total ya armado por el motor.
        BigDecimal capitalNoVencidoIndependiente = BigDecimal.ZERO;
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA && !cuota.getFechaVencimiento().isBefore(fecha)) {
                capitalNoVencidoIndependiente = capitalNoVencidoIndependiente.add(cuota.capitalPendiente());
            }
        }
        BigDecimal sumaConElCampoCorregido = exigibleVencidas.getTotalGeneral()
                .add(capitalNoVencidoIndependiente)
                .add(resultado.getInteresDevengado())
                .add(resultado.getIvaInteresDevengado());
        assertEquals(resultado.getMontoTotalLiquidacion(), sumaConElCampoCorregido);
    }

    /**
     * D-83: ningún día de interés se cuenta dos veces. En el mismo préstamo de BF-13 (cuotas 1 y
     * 2 vencidas), si la fecha de cálculo es exactamente el vencimiento teórico de la cuota 3 --
     * el período en curso transcurrió completo, 30 de 30 días -- el interés del período en curso
     * tiene que coincidir EXACTO con el interés ya programado de la propia cuota 3 (51.269): ni
     * un día de más (lo que pasaba antes del fix) ni de menos.
     */
    @Test
    public void testBF14_InteresDelPeriodoCompletoCoincideConElProgramadoDeLaProximaCuota() {
        Prestamo prestamo = prestamoCC23();

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, LocalDate.of(2026, 10, 19), 0);

        assertMonto(51269, resultado.getInteresDevengado());
    }

    /** D-86: cuotas 1, 2 y 3 del plan de CC-2026-00023, pagadas por adelantado. */
    private List<Cuota> planCC23ConCuota3PagadaPorAdelantado() {
        List<Cuota> cuotas = planCC23();
        List<Cuota> resultado = new ArrayList<>();
        resultado.add(marcarPagada(cuotas.get(0)));
        resultado.add(marcarPagada(cuotas.get(1)));
        resultado.add(marcarPagada(cuotas.get(2)));
        for (int indice = 3; indice < cuotas.size(); indice++) {
            resultado.add(cuotas.get(indice));
        }
        return resultado;
    }

    /**
     * D-86 (bug real, encontrado por Joel probando /cobranza/23 al 26/09/2026): con la cuota 3
     * pagada por adelantado (vence el 19/10, todavía no vencida) y sin ninguna cuota vencida, la
     * vieja "cuotaEnCurso" (D-83: primera no pagada y no vencida) devolvía la cuota 4 -- cuyo
     * período (19/10→19/11) TODAVÍA NO EMPEZÓ al 26/09 -- dando días transcurridos NEGATIVOS
     * (-23) y un interés del período NEGATIVO (~-38.023), que la cancelación restaba en vez de
     * sumar. Con el fix (D-86: el período en curso es el que CONTIENE la fecha, sea la cuota
     * PAGADA o no), el período en curso es el de la cuota 3 -- pero como esa cuota ya está
     * pagada, el interés del período es CERO (ya se cobró como parte de la cuota 3). El costo de
     * cancelar tiene que ser exactamente el capital pendiente (2.315.826), sin restar ni sumar
     * nada de interés.
     */
    @Test
    public void testBF15_CancelacionConLaCuotaDelPeriodoEnCursoPagadaPorAdelantadoDaInteresCero() {
        Prestamo prestamo = prestamoCC23();
        prestamo.setCuotas(planCC23ConCuota3PagadaPorAdelantado());

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, LocalDate.of(2026, 9, 26), 0);

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        assertMonto(2315826, resultado.getMontoTotalLiquidacion());
    }

    /**
     * D-86/D-87: mismo préstamo de BF-15 (cuota 3 pagada por adelantado), ahora con un anticipo
     * de capital. El interés del período en curso también es cero (misma razón que BF-15) -- todo
     * el monto entregado va a capital. D-87 (bug real encontrado por Joel probando CC-2026-00023):
     * como el período de la cuota 3 (19/09→19/10) ya está cerrado (pagado por adelantado), la
     * cuota 1 nueva arranca en el período SIGUIENTE -- vence el 19/11, no el 19/10 (eso habría
     * vuelto a cobrar el mismo período 19/09→19/10 que la cuota 3 ya saldó por completo). Es un
     * período COMPLETO normal (no un resto parcial: la cuota 3 ya está saldada del todo), con el
     * interés calculado sobre el saldo YA reducido por el anticipo.
     */
    @Test
    public void testBF16_AnticipoConLaCuotaDelPeriodoEnCursoPagadaPorAdelantadoArrancaUnPeriodoCompleto() {
        Prestamo prestamo = prestamoCC23();
        prestamo.setCuotas(planCC23ConCuota3PagadaPorAdelantado());

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("500000"),
                LocalDate.of(2026, 9, 26), Collections.<LocalDate>emptySet());

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        assertMonto(500000, resultado.getMontoAnticipoCapital());
        assertMonto(1815826, resultado.getNuevoSaldoCapital());

        Cuota primeraCuotaNueva = resultado.getNuevoPlan().get(0);
        assertEquals(LocalDate.of(2026, 11, 19), primeraCuotaNueva.getFechaVencimientoTeorica());
        // Período COMPLETO sobre el saldo nuevo (1.815.826 × tasa mensual), no un resto parcial
        // de la cuota 3 (que ya está saldada del todo, sin nada pendiente que prorratear).
        assertMonto(36530, primeraCuotaNueva.getInteresProgramado());
    }

    /**
     * D-88: plan 26 vigente REAL de CC-2026-00023 (sgcpc_db), tal como quedó tras el anticipo de
     * BF-16 y el fix de D-87 (leído por SELECT de solo lectura): 7 cuotas, todas PENDIENTE,
     * `origen_plan = 'ANTICIPO_PARCIAL'`, `fecha_generacion` = la fecha del anticipo (27/09/2026).
     */
    private static final Object[][] PLAN_CC23_REGENERADO_TRAS_ANTICIPO = {
        // numero, vtoEfectiva, vtoTeorica, capital, interes, tributos, total, saldoPosterior
        {1, LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), 247354L, 36530L, 3653L, 287537L, 1568472L},
        {2, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 19), 252330L, 31554L, 3155L, 287039L, 1316142L},
        {3, LocalDate.of(2027, 1, 19), LocalDate.of(2027, 1, 19), 257406L, 26478L, 2648L, 286532L, 1058736L},
        {4, LocalDate.of(2027, 2, 19), LocalDate.of(2027, 2, 19), 262585L, 21299L, 2130L, 286014L, 796151L},
        {5, LocalDate.of(2027, 3, 19), LocalDate.of(2027, 3, 19), 267867L, 16017L, 1602L, 285486L, 528284L},
        {6, LocalDate.of(2027, 4, 19), LocalDate.of(2027, 4, 19), 273256L, 10628L, 1063L, 284947L, 255028L},
        {7, LocalDate.of(2027, 5, 19), LocalDate.of(2027, 5, 19), 255028L, 5131L, 513L, 260672L, 0L},
    };

    private List<Cuota> planCC23RegeneradoTrasAnticipo() {
        List<Cuota> cuotas = new ArrayList<>();
        for (Object[] fila : PLAN_CC23_REGENERADO_TRAS_ANTICIPO) {
            Cuota cuota = new Cuota();
            cuota.setNumeroCuota((Integer) fila[0]);
            cuota.setFechaVencimiento((LocalDate) fila[1]);
            cuota.setFechaVencimientoTeorica((LocalDate) fila[2]);
            cuota.setCapitalProgramado(BigDecimal.valueOf((Long) fila[3]));
            cuota.setInteresProgramado(BigDecimal.valueOf((Long) fila[4]));
            cuota.setTributosProgramados(BigDecimal.valueOf((Long) fila[5]));
            cuota.setTotalProgramado(BigDecimal.valueOf((Long) fila[6]));
            cuota.setSaldoCapitalPosterior(BigDecimal.valueOf((Long) fila[7]));
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            cuotas.add(cuota);
        }
        return cuotas;
    }

    private Prestamo prestamoCC23RegeneradoTrasAnticipo() {
        Prestamo prestamo = prestamoCC23();
        prestamo.setCuotas(planCC23RegeneradoTrasAnticipo());
        prestamo.setOrigenPlanVigente("ANTICIPO_PARCIAL");
        prestamo.setFechaGeneracionPlanVigente(LocalDate.of(2026, 9, 27));
        return prestamo;
    }

    /**
     * D-87 (caso real, CC-2026-00023): con el plan regenerado por el anticipo de BF-16 ya
     * vigente (origenPlanVigente = "ANTICIPO_PARCIAL", fecha de generación = la fecha del
     * anticipo, 27/09/2026), la cuota 1 nueva vence el 19/11/2026. inicioPeriodoTeorico da
     * MAX(19/11 - 1 mes = 19/10, fecha del anticipo = 27/09) = 19/10 -- el período de esa cuota
     * es entonces (19/10, 19/11]. La fecha de cálculo (27/09) es ANTERIOR a ese inicio (19/10):
     * con el fix de D-88, periodoActual devuelve ese primer período con cero días transcurridos
     * (en vez de null) -- el interés del período en curso es cero, exactamente como D-86 exige
     * cuando la fecha cae antes de que empiece un período. La cancelación de hoy es entonces
     * solo el capital pendiente: 1.815.826.
     */
    @Test
    public void testBF17_CancelacionConPlanYaRegeneradoPorAnticipoNoRecobraElPeriodoYaPagado() {
        Prestamo prestamo = prestamoCC23RegeneradoTrasAnticipo();

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, LocalDate.of(2026, 9, 27), 0);

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        assertMonto(1815826, resultado.getMontoTotalLiquidacion());
    }

    /**
     * D-88 (bug real, encontrado por Joel al redesplegar D-87 y abrir /cobranza/23 como cajera):
     * la ficha muestra "Interés mínimo del período en curso" llamando a calcularAnticipoParcial
     * con monto=0 -- antes del fix, regenerarPlanAnticipo desreferenciaba periodo.cuotaActual sin
     * comprobar null, y periodoActual daba null exactamente en este caso (fecha 27/09, anterior
     * al inicio 19/10 del período de la cuota 1 nueva): NullPointerException, 500 al abrir la
     * ficha (nunca llegó a cobrar nada). Con el fix, el mismo caso de BF-17 pero pasando por
     * calcularAnticipoParcial (el camino que realmente usa la ficha) no lanza nada y da interés
     * cero -- el plan que devuelve (irrelevante para este preview, RN-11) conserva la cuota 1 sin
     * cambios, 19/11/2026.
     */
    @Test
    public void testBF20_AnticipoPreviewAntesDelInicioDelPrimerPeriodoDelPlanRegeneradoNoFallaYDaInteresCero() {
        Prestamo prestamo = prestamoCC23RegeneradoTrasAnticipo();

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, BigDecimal.ZERO,
                LocalDate.of(2026, 9, 27), Collections.<LocalDate>emptySet());

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        assertEquals(LocalDate.of(2026, 11, 19), resultado.getNuevoPlan().get(0).getFechaVencimientoTeorica());
    }

    /**
     * D-88/D-89: caso de humo #14 llevado a JUnit -- cuota 1 de CC-2026-00023 (teórica 19/08/2026)
     * todavía no pagada y con la teórica ya pasada al 20/08/2026 (un día después): el anticipo se
     * rechaza con el mensaje exacto de Joel, "primero hay que cobrar la cuota del DD/MM".
     */
    @Test
    public void testAnticipoRechazadoPorCuotaConTeoricaPasadaSinPagarDaElMensajeExacto() {
        Prestamo prestamo = prestamoCC23();

        try {
            servicio.calcularAnticipoParcial(prestamo, new BigDecimal("500000"), LocalDate.of(2026, 8, 20),
                    Collections.<LocalDate>emptySet());
            fail("Debía rechazar (cuota 1, teórica 19/08/2026, ya pasada y sin pagar).");
        } catch (NegocioException error) {
            assertEquals("Primero hay que cobrar la cuota del 19/08/2026.", error.getMessage());
        }
    }

    /**
     * D-90 (bug real, CC-2026-00028): un anticipo cuyo monto a capital cubre (o supera) todo el
     * saldo pendiente dejaba el préstamo ACTIVO con saldo 0 para siempre (confirmarAnticipoCapital
     * nunca lo pasa a FINALIZADO) y generaba un plan degenerado de 1 cuota con todo en cero.
     * Con el fixture de BF17/BF20 (interés del período en curso = 0 al 27/09/2026, capital
     * pendiente total = 1.815.826, verificado con el motor real): un monto que IGUALA el saldo
     * se rechaza con el mensaje exacto de Joel -- "use Cancelación total" en vez de un anticipo.
     */
    @Test
    public void testAnticipoQueCubreTodoElSaldoSeRechaza() {
        Prestamo prestamo = prestamoCC23RegeneradoTrasAnticipo();

        try {
            servicio.calcularAnticipoParcial(prestamo, new BigDecimal("1815826"), LocalDate.of(2026, 9, 27),
                    Collections.<LocalDate>emptySet());
            fail("Debía rechazar (el monto cubre todo el saldo pendiente, 1.815.826).");
        } catch (NegocioException error) {
            assertEquals("El monto cubre todo el saldo del préstamo. Use Cancelación total.", error.getMessage());
        }
    }

    /**
     * D-90: un guaraní menos que el saldo total SÍ se acepta -- y el plan que resulta (pedido de
     * Joel, "verificá también el plan regenerado") tiene que ser una única cuota VÁLIDA: capital
     * 1 (el resto de guaraníes que faltan por cobrar), sin quedar en cero, sin ningún monto
     * negativo, y con la fecha teórica correcta según D-87 -- como el interés del período en
     * curso ya es cero (BF17/BF20: la fecha 27/09 es anterior al inicio del período de la cuota
     * 1, D-88), la cuota nueva conserva la MISMA fecha teórica de la cuota de referencia (D-87,
     * rama "período todavía no consumido"), 19/11/2026 -- no un mes después (esa rama es solo
     * para cuando la cuota de referencia YA estaba pagada, D-86, que no es este caso).
     */
    @Test
    public void testAnticipoUnGuaraniMenosQueElSaldoSeAceptaYElPlanQuedaValido() {
        Prestamo prestamo = prestamoCC23RegeneradoTrasAnticipo();

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("1815825"),
                LocalDate.of(2026, 9, 27), Collections.<LocalDate>emptySet());

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        assertMonto(1815825, resultado.getMontoAnticipoCapital());
        assertMonto(1, resultado.getNuevoSaldoCapital());

        List<Cuota> nuevoPlan = resultado.getNuevoPlan();
        assertEquals("una sola cuota, no un plan de varias cuotas en cero", 1, nuevoPlan.size());
        Cuota unica = nuevoPlan.get(0);
        assertMonto(1, unica.getCapitalProgramado());
        assertMonto(0, unica.getInteresProgramado());
        assertMonto(0, unica.getTributosProgramados());
        assertMonto(1, unica.getTotalProgramado());
        assertMonto(0, unica.getSaldoCapitalPosterior());
        assertTrue("el capital no puede quedar negativo", unica.getCapitalProgramado().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue("el interés no puede quedar negativo", unica.getInteresProgramado().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(LocalDate.of(2026, 11, 19), unica.getFechaVencimientoTeorica());
    }

    /**
     * D-88 (pedido de Joel, para no seguir encontrando estos casos de a uno): recorre TODOS los
     * días desde el desembolso/generación del plan hasta 30 días después del último vencimiento,
     * para dos préstamos reales (uno con un plan ORIGINAL sin pagos, otro con un plan ya
     * regenerado por un anticipo, D-87), llamando cada día a calcularDeudaExigible,
     * calcularCancelacionTotal, calcularAnticipoParcial (monto 0, el mismo camino que usa la
     * ficha para el "interés mínimo") y periodoActual directo -- verificando en cada uno que
     * ninguna combinación de fecha/plan rompe las invariantes básicas del motor. Junta TODAS las
     * violaciones en vez de frenar en la primera, para poder mostrar el panorama completo antes
     * de corregir nada (pedido explícito de Joel).
     */
    private List<String> barridoDeFechas(String nombreFixture, Prestamo prestamo, LocalDate desde, LocalDate hasta) {
        List<String> violaciones = new ArrayList<>();
        BigDecimal i = ServicioMotorFinanciero.calcularTasaMensual(prestamo.getTasaTea());
        BigDecimal cancelacionAnterior = null;

        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            String contexto = nombreFixture + " en " + fecha;

            // D-88 (regla de Joel): existe una cuota no pagada con teórica ya pasada -- adelantar
            // capital se rechaza ("primero hay que cobrar la cuota del DD/MM"), un
            // NegocioException ahí es el comportamiento ESPERADO, no una violación.
            boolean seEsperaRechazoDeAnticipo = false;
            for (Cuota cuota : prestamo.getCuotas()) {
                if (cuota.getEstadoPago() != EstadoPago.PAGADA && cuota.getFechaVencimientoTeorica().isBefore(fecha)) {
                    seEsperaRechazoDeAnticipo = true;
                    break;
                }
            }

            ResultadoDeudaExigible exigible;
            ResultadoLiquidacion cancelacion;
            try {
                exigible = servicio.calcularDeudaExigible(prestamo, fecha, 0);
                cancelacion = servicio.calcularCancelacionTotal(prestamo, fecha, 0);
            } catch (RuntimeException error) {
                violaciones.add(contexto + ": calcularDeudaExigible/calcularCancelacionTotal lanzó " + error);
                continue;
            }

            ResultadoLiquidacion anticipoPreview = null;
            try {
                anticipoPreview = servicio.calcularAnticipoParcial(prestamo, BigDecimal.ZERO, fecha,
                        Collections.<LocalDate>emptySet());
                if (seEsperaRechazoDeAnticipo) {
                    violaciones.add(contexto + ": calcularAnticipoParcial debía rechazar (teórica pasada sin pagar) "
                            + "y no lo hizo");
                }
            } catch (NegocioException error) {
                if (!seEsperaRechazoDeAnticipo) {
                    violaciones.add(contexto + ": calcularAnticipoParcial rechazó sin motivo (" + error.getMessage() + ")");
                }
            } catch (RuntimeException error) {
                violaciones.add(contexto + ": calcularAnticipoParcial lanzó " + error);
            }

            // Interés del período >= 0 (nunca negativo, D-86/D-87), en los dos caminos que lo usan.
            if (cancelacion.getInteresDevengado().compareTo(BigDecimal.ZERO) < 0) {
                violaciones.add(contexto + ": interesDevengado de cancelación < 0 (" + cancelacion.getInteresDevengado() + ")");
            }
            if (anticipoPreview != null && anticipoPreview.getInteresDevengado().compareTo(BigDecimal.ZERO) < 0) {
                violaciones.add(contexto + ": interesDevengado de anticipo preview < 0 (" + anticipoPreview.getInteresDevengado() + ")");
            }

            // Días transcurridos entre 0 y días del período (D-88: ni negativos, D-86, ni un
            // período "null" desreferenciado, D-87/D-88).
            ServicioMotorFinanciero.PeriodoActual periodo = servicio.periodoActual(prestamo, fecha);
            if (periodo != null) {
                if (periodo.diasTranscurridos < 0) {
                    violaciones.add(contexto + ": diasTranscurridos negativo (" + periodo.diasTranscurridos + ")");
                }
                if (periodo.diasTranscurridos > periodo.diasDelPeriodo) {
                    violaciones.add(contexto + ": diasTranscurridos (" + periodo.diasTranscurridos
                            + ") mayor que diasDelPeriodo (" + periodo.diasDelPeriodo + ")");
                }
            }

            // Capital pendiente total (vencido + no vencido), calculado acá sin tocar internos.
            BigDecimal capitalPendienteTotal = BigDecimal.ZERO;
            for (Cuota cuota : prestamo.getCuotas()) {
                if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                    capitalPendienteTotal = capitalPendienteTotal.add(cuota.capitalPendiente());
                }
            }

            // Cota inferior: cancelar nunca puede costar menos que el capital pendiente.
            if (cancelacion.getMontoTotalLiquidacion().compareTo(capitalPendienteTotal) < 0) {
                violaciones.add(contexto + ": montoTotalLiquidacion (" + cancelacion.getMontoTotalLiquidacion()
                        + ") menor que el capital pendiente (" + capitalPendienteTotal + ")");
            }

            // Cota superior (holgada a propósito: capital pendiente vencido queda contado dos
            // veces -- una en capitalPendienteTotal, otra dentro de exigible.getTotalGeneral() --
            // para no depender de la separación interna vencido/no vencido; sigue siendo una cota
            // válida y ya detecta un desborde real como el de D-88 (30.678/1.846.504)).
            BigDecimal interesUnPeriodoCompleto = capitalPendienteTotal.multiply(i).setScale(0, RoundingMode.HALF_UP);
            BigDecimal ivaUnPeriodoCompleto = interesUnPeriodoCompleto.multiply(new BigDecimal("0.10"))
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal cotaSuperior = capitalPendienteTotal.add(interesUnPeriodoCompleto).add(ivaUnPeriodoCompleto)
                    .add(exigible.getTotalGeneral());
            if (cancelacion.getMontoTotalLiquidacion().compareTo(cotaSuperior) > 0) {
                violaciones.add(contexto + ": montoTotalLiquidacion (" + cancelacion.getMontoTotalLiquidacion()
                        + ") mayor que la cota superior (" + cotaSuperior + ")");
            }

            // D-88 (nueva, pedida por Joel): sin pagos de por medio, la cancelación de un día
            // nunca puede ser menor que la del día anterior (el interés solo se acumula).
            if (cancelacionAnterior != null
                    && cancelacion.getMontoTotalLiquidacion().compareTo(cancelacionAnterior) < 0) {
                violaciones.add(contexto + ": montoTotalLiquidacion (" + cancelacion.getMontoTotalLiquidacion()
                        + ") bajó respecto del día anterior (" + cancelacionAnterior + ")");
            }
            cancelacionAnterior = cancelacion.getMontoTotalLiquidacion();
        }
        return violaciones;
    }

    @Test
    public void testBarridoDeFechas_NingunDiaRompeLasInvariantesDelMotorFinanciero() {
        List<String> violaciones = new ArrayList<>();
        violaciones.addAll(barridoDeFechas("CC-2026-00014 (plan ORIGINAL, sin pagos)", prestamoCC14SinPagos(),
                LocalDate.of(2026, 9, 26), LocalDate.of(2027, 9, 27).plusDays(30)));
        violaciones.addAll(barridoDeFechas("CC-2026-00023 (plan 26 regenerado tras el anticipo, D-87)",
                prestamoCC23RegeneradoTrasAnticipo(), LocalDate.of(2026, 9, 27), LocalDate.of(2027, 5, 19).plusDays(30)));

        if (!violaciones.isEmpty()) {
            StringBuilder mensaje = new StringBuilder();
            mensaje.append(violaciones.size()).append(" violaciones encontradas:\n");
            for (String violacion : violaciones) {
                mensaje.append(" - ").append(violacion).append('\n');
            }
            fail(mensaje.toString());
        }
    }

    /** D-87 (caso D-80): cuotas 1 y 2 pagadas, cuota 3 PENDIENTE (vence 19/10, período 19/09→19/10). */
    private List<Cuota> planCC23Cuotas1y2PagadasPorAdelantado() {
        List<Cuota> cuotas = planCC23();
        List<Cuota> resultado = new ArrayList<>();
        resultado.add(marcarPagada(cuotas.get(0)));
        resultado.add(marcarPagada(cuotas.get(1)));
        for (int indice = 2; indice < cuotas.size(); indice++) {
            resultado.add(cuotas.get(indice));
        }
        return resultado;
    }

    /**
     * D-87 (caso D-80, aprobado por Joel): anticipo a MITAD del período de la cuota 3 (día 15 de
     * 30, 04/10/2026) y, sin esperar a que venza la cuota 1 del plan regenerado, una cancelación
     * total DENTRO DEL MISMO período (día 24, 13/10/2026 -- 9 días después del anticipo). Con el
     * fix de inicioPeriodoTeorico (D-87: MAX(fecha del anticipo, teórica cuota1 − 1 mes)), el
     * período de la cuota 1 nueva pasa a ser (04/10, 19/10] -- NO (19/09, 19/10] como antes del
     * fix, que habría vuelto a cobrar los primeros 15 días ya cobrados por el anticipo. Por eso
     * la cancelación de hoy prorratea solo 9 de 15 días (9/15), sobre el saldo YA reducido por el
     * anticipo (1.576.638) -- ni un día de los 24 transcurridos se cuenta dos veces: 15 los cobró
     * el anticipo (sobre 2.548.441) y 9 los cobra la cancelación (sobre 1.576.638).
     */
    @Test
    public void testBF18_AnticipoAMitadDePeriodoYCancelacionEnElMismoPeriodoNoDuplicaDias() {
        Prestamo prestamo1 = prestamoCC23();
        prestamo1.setCuotas(planCC23Cuotas1y2PagadasPorAdelantado());

        ResultadoLiquidacion liquidacion1 = servicio.calcularAnticipoParcial(prestamo1, new BigDecimal("1000000"),
                LocalDate.of(2026, 10, 4), Collections.<LocalDate>emptySet());

        assertMonto(25634, liquidacion1.getInteresDevengado());
        assertMonto(2563, liquidacion1.getIvaInteresDevengado());
        assertMonto(971803, liquidacion1.getMontoAnticipoCapital());
        assertMonto(1576638, liquidacion1.getNuevoSaldoCapital());

        Prestamo prestamo2 = new Prestamo();
        prestamo2.setFechaDesembolso(prestamo1.getFechaDesembolso());
        prestamo2.setTasaTea(prestamo1.getTasaTea());
        prestamo2.setTasaMoratoria(prestamo1.getTasaMoratoria());
        prestamo2.setTasaPunitoria(prestamo1.getTasaPunitoria());
        prestamo2.setCuotas(liquidacion1.getNuevoPlan());
        prestamo2.setOrigenPlanVigente("ANTICIPO_PARCIAL");
        prestamo2.setFechaGeneracionPlanVigente(LocalDate.of(2026, 10, 4));

        ResultadoLiquidacion liquidacion2 = servicio.calcularCancelacionTotal(prestamo2, LocalDate.of(2026, 10, 13), 0);

        // Solo 9 días (13/10 - 04/10) sobre el saldo nuevo (1.576.638), no 24 ni 0.
        assertMonto(19031, liquidacion2.getInteresDevengado());
        assertMonto(1903, liquidacion2.getIvaInteresDevengado());
        assertMonto(1597572, liquidacion2.getMontoTotalLiquidacion());
    }

    /**
     * D-87 (segundo anticipo sobre un plan ya regenerado): mismo punto de partida que BF-18
     * (anticipo día 15, plan regenerado con origenPlanVigente = "ANTICIPO_PARCIAL"), pero en vez
     * de cancelar el día 24 se hace un SEGUNDO anticipo. La fórmula MAX debe seguir dando el
     * mismo período (04/10, 19/10] -- 9 de 15 días -- y el interés de este segundo anticipo debe
     * coincidir exacto con el de la cancelación de BF-18 (19.031/1.903), porque en ambos casos se
     * prorratea el mismo tramo (9/15) sobre el mismo saldo (1.576.638). El plan que resulta de
     * este segundo anticipo también respeta la fórmula: su cuota 1 nueva conserva la fecha 19/10
     * (todavía dentro del mismo período, D-80) con su propio interés parcial sobre el saldo
     * reducido por este segundo anticipo.
     */
    @Test
    public void testBF19_SegundoAnticipoSobreUnPlanYaRegeneradoPorAnticipo() {
        Prestamo prestamo1 = prestamoCC23();
        prestamo1.setCuotas(planCC23Cuotas1y2PagadasPorAdelantado());

        ResultadoLiquidacion liquidacion1 = servicio.calcularAnticipoParcial(prestamo1, new BigDecimal("1000000"),
                LocalDate.of(2026, 10, 4), Collections.<LocalDate>emptySet());

        Prestamo prestamo2 = new Prestamo();
        prestamo2.setFechaDesembolso(prestamo1.getFechaDesembolso());
        prestamo2.setTasaTea(prestamo1.getTasaTea());
        prestamo2.setTasaMoratoria(prestamo1.getTasaMoratoria());
        prestamo2.setTasaPunitoria(prestamo1.getTasaPunitoria());
        prestamo2.setCuotas(liquidacion1.getNuevoPlan());
        prestamo2.setOrigenPlanVigente("ANTICIPO_PARCIAL");
        prestamo2.setFechaGeneracionPlanVigente(LocalDate.of(2026, 10, 4));

        ResultadoLiquidacion liquidacion3 = servicio.calcularAnticipoParcial(prestamo2, new BigDecimal("500000"),
                LocalDate.of(2026, 10, 13), Collections.<LocalDate>emptySet());

        assertMonto(19031, liquidacion3.getInteresDevengado());
        assertMonto(1903, liquidacion3.getIvaInteresDevengado());
        assertMonto(479066, liquidacion3.getMontoAnticipoCapital());
        assertMonto(1097572, liquidacion3.getNuevoSaldoCapital());

        Cuota primeraCuotaTercerPlan = liquidacion3.getNuevoPlan().get(0);
        assertEquals(LocalDate.of(2026, 10, 19), primeraCuotaTercerPlan.getFechaVencimientoTeorica());
        assertMonto(8832, primeraCuotaTercerPlan.getInteresProgramado());
        assertMonto(275052, primeraCuotaTercerPlan.getCapitalProgramado());
    }

    @Test
    public void testBF10_VencimientoEnSabadoSeCorreAlLunes() {
        LocalDate resultado = servicio.fechaHabil(LocalDate.of(2027, 1, 2), Collections.<LocalDate>emptySet());

        assertEquals(LocalDate.of(2027, 1, 4), resultado);
    }

    /** RF11 (pedido de Joel, 11/10/2026): fechaHabilAnterior es fechaHabil pero hacia atrás, para
     * sugerir la última fecha hábil válida cuando el corrimiento hacia adelante se sale del rango. */
    @Test
    public void testFechaHabilAnteriorConFechaYaHabilNoCambia() {
        LocalDate resultado = servicio.fechaHabilAnterior(LocalDate.of(2026, 11, 25), Collections.<LocalDate>emptySet());

        assertEquals(LocalDate.of(2026, 11, 25), resultado);
    }

    @Test
    public void testFechaHabilAnteriorEnDomingoRetrocedeAlViernes() {
        LocalDate resultado = servicio.fechaHabilAnterior(LocalDate.of(2026, 11, 22), Collections.<LocalDate>emptySet());

        assertEquals(LocalDate.of(2026, 11, 20), resultado);
    }

    @Test
    public void testFechaHabilAnteriorConFeriadoSiguienteALunesRetrocedeAlViernesAnterior() {
        List<LocalDate> feriados = Arrays.asList(LocalDate.of(2026, 12, 8));

        LocalDate resultado = servicio.fechaHabilAnterior(LocalDate.of(2026, 12, 8), feriados);

        assertEquals(LocalDate.of(2026, 12, 7), resultado);
    }

    /** RF11: la cuota más alta y el CFT no dependen de qué fecha de primer vencimiento se elija
     * -- el motor descuenta por período secuencial (k=1,2,...), no por día-calendario real entre
     * cuotas, así que da lo mismo cotizar con una fecha en día hábil o una que se corre varios
     * días (confirma, con JUnit, el hallazgo del análisis de RF11 sobre por qué "recalcular el
     * CFT con la fecha nueva" siempre da el mismo número salvo que cambien monto/plazo/tea). */
    @Test
    public void testCftYCuotaMasAltaNoCambianConLaFechaDelPrimerVencimiento() {
        BigDecimal monto = new BigDecimal("5000000");
        BigDecimal tea = new BigDecimal("0.3");
        List<LocalDate> sinFeriados = Collections.emptyList();

        ResultadoCotizacion conFechaHabil = servicio.cotizar(monto, 12, LocalDate.of(2026, 11, 9), tea, sinFeriados);
        ResultadoCotizacion conFechaDeFinDeSemana = servicio.cotizar(monto, 12, LocalDate.of(2026, 11, 7), tea, sinFeriados);

        assertEquals(conFechaHabil.getCft(), conFechaDeFinDeSemana.getCft());
        assertEquals(servicio.cuotaMasAlta(conFechaHabil), servicio.cuotaMasAlta(conFechaDeFinDeSemana));
        assertEquals(conFechaHabil.getMontoTotalConIva(), conFechaDeFinDeSemana.getMontoTotalConIva());
    }

    /** RF11: cuota más alta = máximo total con IVA del plan (RN-03), movido acá desde
     * ServicioSolicitudes para que RF05/RF06 y RF11 compartan la misma fórmula. */
    @Test
    public void testCuotaMasAltaEsElMaximoDelPlan() {
        BigDecimal monto = new BigDecimal("5000000");
        BigDecimal tea = new BigDecimal("0.3");
        ResultadoCotizacion resultado = servicio.cotizar(monto, 12, LocalDate.of(2026, 11, 9), tea, Collections.emptyList());

        BigDecimal esperado = resultado.getPlan().stream()
                .map(Cuota::getTotalProgramado)
                .max(BigDecimal::compareTo)
                .orElseThrow(AssertionError::new);

        assertEquals(esperado, servicio.cuotaMasAlta(resultado));
    }

    /** RF11 (pedido de Joel, 11/10/2026): sumarCuotasOtrasDeudas, movido acá desde
     * ServicioSolicitudes (D-127) -- RF05/RF06 y RF11 ahora comparten la misma fórmula; el
     * traslado fue necesario para no formar un ciclo de construcción entre ServicioCotizacion
     * (que ya depende de ServicioFormalizacion) y ServicioFormalizacion (que ahora también
     * necesita esta suma). */
    @Test
    public void testSumarCuotasOtrasDeudasConValor() {
        BigDecimal total = servicio.sumarCuotasOtrasDeudas(new BigDecimal("100000"));

        assertEquals(new BigDecimal("100000"), total);
    }

    @Test
    public void testSumarCuotasOtrasDeudasNulaEsCero() {
        assertEquals(BigDecimal.ZERO, servicio.sumarCuotasOtrasDeudas(null));
    }

    @Test
    public void testBF11_AnticipoAMitadDePeriodo() {
        List<Cuota> plan = planBF01();
        List<Cuota> cuotas = new ArrayList<>();
        cuotas.add(marcarPagada(plan.get(0)));
        cuotas.add(plan.get(1));
        cuotas.add(plan.get(2));
        Prestamo prestamo = prestamoBF01(cuotas);

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("200000"),
                LocalDate.of(2026, 11, 12), Collections.<LocalDate>emptySet());

        assertMonto(2257, resultado.getInteresDevengado());
        assertMonto(226, resultado.getIvaInteresDevengado());
        assertMonto(197517, resultado.getMontoAnticipoCapital());
        assertMonto(139125, resultado.getNuevoSaldoCapital());

        assertEquals(1, resultado.getNuevoPlan().size());
        Cuota nueva = resultado.getNuevoPlan().get(0);
        assertEquals(LocalDate.of(2026, 12, 2), nueva.getFechaVencimientoTeorica());
        assertMonto(1866, nueva.getInteresProgramado());
        assertMonto(139125, nueva.getCapitalProgramado());
        assertMonto(140991, nueva.getInteresProgramado().add(nueva.getCapitalProgramado()));
        assertMonto(187, nueva.getTributosProgramados());
        assertMonto(141178, nueva.getTotalProgramado());
    }

    @Test
    public void testBF12_MoraPorTramosDespuesDePagoParcial() {
        List<Cuota> plan = planBF01();
        Prestamo prestamoParaPago = prestamoBF01(new ArrayList<>(Arrays.asList(plan.get(0))));
        ResultadoAplicacionPago pagoBF07 = servicio.distribuirPago(prestamoParaPago, new BigDecimal("100000"),
                LocalDate.of(2026, 11, 17), 0);
        Cuota cuota1TrasBF07 = pagoBF07.getDetalles().get(0).getCuotaResultante();

        List<Cuota> cuotas = new ArrayList<>();
        cuotas.add(cuota1TrasBF07);
        cuotas.add(plan.get(1));
        cuotas.add(plan.get(2));
        Prestamo prestamo = prestamoBF01(cuotas);

        ResultadoDeudaExigible resultado = servicio.calcularDeudaExigible(prestamo, LocalDate.of(2026, 11, 27), 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleCuotaExigible detalle = resultado.getDetalles().get(0);

        assertMonto(76729, detalle.getCapitalPendiente());
        assertMonto(504, detalle.getMoratorioTotal());
        assertMonto(50, detalle.getIvaMoratorioTotal());
        assertMonto(151, detalle.getPunitorioTotal());
        assertMonto(15, detalle.getIvaPunitorioTotal());
        assertMonto(77449, detalle.getTotalExigible());
        assertMonto(77449, resultado.getTotalGeneral());
    }

    /**
     * E4 (ajuste 1, Joel): "Total a pagar (con IVA)" en /aprobaciones/{id} y /solicitudes/{id}
     * daba un valor inflado porque se aproximaba como cuota más alta × plazo -- el IVA sobre
     * intereses baja en cada cuota (sistema francés), así que esa multiplicación no es el total
     * real. Este caso confirma que montoTotalConIva ya es la suma EXACTA del plan, para que las
     * pantallas lo usen directamente en vez de aproximar.
     */
    @Test
    public void testMontoTotalConIvaEsLaSumaExactaDelPlan() {
        ResultadoCotizacion resultado = servicio.cotizar(new BigDecimal("2000000"), 12, LocalDate.of(2026, 11, 2),
                TEA, Collections.<LocalDate>emptySet());

        BigDecimal sumaDelPlan = BigDecimal.ZERO;
        for (Cuota cuota : resultado.getPlan()) {
            sumaDelPlan = sumaDelPlan.add(cuota.getTotalProgramado());
        }

        assertEquals(0, sumaDelPlan.compareTo(resultado.getMontoTotalConIva()));

        // La cuota más alta × 12 NO es el total real (por eso "aprox." estaba mal): confirma que
        // son valores distintos, para que quede documentado el porqué de la corrección.
        BigDecimal cuotaMasAlta = resultado.getPlan().stream().map(Cuota::getTotalProgramado)
                .max(BigDecimal::compareTo).orElseThrow(IllegalStateException::new);
        BigDecimal aproximacionInflada = cuotaMasAlta.multiply(BigDecimal.valueOf(12));
        assertTrue(aproximacionInflada.compareTo(resultado.getMontoTotalConIva()) > 0);
    }

    // =====================================================================
    // E9/Bloque 5 (PO-13): revertirAplicacionEnCuota -- invariante de Joel, "como si el cobro
    // nunca hubiera existido". Para cada tipo pedido (cobro normal, parcial con promesa, con
    // vuelto, anticipo, cancelación): foto de la cuota ANTES de aplicar el pago -> aplicar (motor
    // puro, distribuirPago) -> revertir (revertirAplicacionEnCuota, con los netos que quedaron en
    // aplicaciones_pago y la fecha del touque anterior) -> la foto DESPUÉS tiene que ser idéntica.
    // Usa plan real de CC-2026-00023 (mismos importes que CC-2026-00014/CC-2026-90001, ya
    // verificados en otros tests de este archivo), plazo que revertirAplicacionEnCuota generaliza
    // para cualquier operación que haya producido el mismo tipo de touque por aplicaciones_pago
    // (cobro, parcial, anticipo o cancelación): la reversión opera sobre el LEDGER (los netos que
    // ya quedaron guardados), no sobre qué método del motor los generó -- no hace falta un test
    // aparte contra calcularCancelacionTotal, un pago que toca varias cuotas (ver el caso de
    // "cancelación" más abajo) ejercita exactamente la misma función por cada cuota tocada.
    // =====================================================================

    private void assertCuotaRestauradaIgualOriginal(Cuota original, Cuota restaurada) {
        assertEquals(0, original.getCapitalPagado().compareTo(restaurada.getCapitalPagado()));
        assertEquals(0, original.getInteresPagado().compareTo(restaurada.getInteresPagado()));
        assertEquals(0, original.getMoratorioPendiente().compareTo(restaurada.getMoratorioPendiente()));
        assertEquals(0, original.getPunitorioPendiente().compareTo(restaurada.getPunitorioPendiente()));
        assertEquals(0, original.getIvaMoratorioPendiente().compareTo(restaurada.getIvaMoratorioPendiente()));
        assertEquals(0, original.getIvaPunitorioPendiente().compareTo(restaurada.getIvaPunitorioPendiente()));
        assertEquals(original.getFechaCorteMora(), restaurada.getFechaCorteMora());
        assertEquals(original.getEstadoPago(), restaurada.getEstadoPago());
    }

    /** Atajo: revertir con la tasa/gracia de prestamoCC23() (TEA 0.27 / punitorio 0.30 / gracia 0). */
    private Cuota revertirCC23(DetalleAplicacionPago detalle, LocalDate fechaCorteMoraAnterior,
            LocalDate fechaPagoRevertido) {
        return servicio.revertirAplicacionEnCuota(detalle.getCuotaResultante(), detalle.getMontoCapital(),
                detalle.getMontoInteres(), detalle.getMontoMoratorio(), detalle.getMontoPunitorio(),
                fechaCorteMoraAnterior, new BigDecimal("0.270000"), new BigDecimal("0.300000"), 0, fechaPagoRevertido);
    }

    @Test
    public void testE9RevertirCobroNormalRestauraCuotaIdentica() {
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1Original = new Cuota(prestamo.getCuotas().get(0));

        // Cobro normal, exacto, el mismo día del vencimiento -- sin mora.
        LocalDate fechaPago = LocalDate.of(2026, 8, 19);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("289919"), fechaPago, 0);
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertEquals(EstadoPago.PAGADA, detalle.getCuotaResultante().getEstadoPago());

        Cuota restaurada = revertirCC23(detalle, null, fechaPago);

        assertCuotaRestauradaIgualOriginal(cuota1Original, restaurada);
    }

    @Test
    public void testE9RevertirParcialConPromesaRestauraCuotaIdentica() {
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1Original = new Cuota(prestamo.getCuotas().get(0));

        // Pago parcial, insuficiente para el capital completo, 27 días tarde -- deja la cuota
        // PARCIAL (moratorio/punitorio/interés se cubren, el capital no) -- el caso típico donde
        // la cajera registra una promesa de pago por el resto.
        LocalDate fechaPago = LocalDate.of(2026, 9, 15);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("100000"), fechaPago, 0);
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertEquals(EstadoPago.PARCIAL, detalle.getCuotaResultante().getEstadoPago());

        Cuota restaurada = revertirCC23(detalle, null, fechaPago);

        assertCuotaRestauradaIgualOriginal(cuota1Original, restaurada);
    }

    @Test
    public void testE9RevertirCobroConVueltoRestauraCuotaIdentica() {
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1Original = new Cuota(prestamo.getCuotas().get(0));

        // Paga de más (300.000 contra una cuota de 289.919) -- el sobrante es el vuelto, no toca
        // ninguna otra cuota (no hay más vencidas ni cuotas futuras que absorban un sobrante que
        // ya cubrió la única vencida). El vuelto en sí es un dato de ServicioCobranza/Pago, ajeno
        // a la cuota -- acá solo importa que lo efectivamente APLICADO revierte igual que siempre.
        LocalDate fechaPago = LocalDate.of(2026, 8, 19);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("300000"), fechaPago, 0);
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertEquals(EstadoPago.PAGADA, detalle.getCuotaResultante().getEstadoPago());
        assertMonto(10081, resultado.getMontoSobrante()); // 300.000 - 289.919 = vuelto

        Cuota restaurada = revertirCC23(detalle, null, fechaPago);

        assertCuotaRestauradaIgualOriginal(cuota1Original, restaurada);
    }

    @Test
    public void testE9RevertirAnticipoRestauraCuotaIdentica() {
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1Original = new Cuota(prestamo.getCuotas().get(0));

        // Un anticipo cobra primero el interés mínimo del período en curso (RN-11) -- acá, solo
        // interés + su IVA, sin capital: la cuota queda PARCIAL (interés completo, capital en 0),
        // antes de que ServicioCobranza arme la ANTICIPO_CAPITAL aparte (id_cuota NULL, ver
        // aplicaciones_pago -- esa parte no toca ninguna cuota, se revierte con el cambio de
        // versión de plan, a nivel de servicio/DAO, no acá).
        LocalDate fechaPago = LocalDate.of(2026, 8, 19);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("66388"), fechaPago, 0);
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertMonto(60353, detalle.getMontoInteres());
        assertMonto(0, detalle.getMontoCapital());
        assertEquals(EstadoPago.PARCIAL, detalle.getCuotaResultante().getEstadoPago());

        Cuota restaurada = revertirCC23(detalle, null, fechaPago);

        assertCuotaRestauradaIgualOriginal(cuota1Original, restaurada);
    }

    @Test
    public void testE9RevertirCancelacionRestauraVariasCuotasIdenticas() {
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1Original = new Cuota(prestamo.getCuotas().get(0));
        Cuota cuota2Original = new Cuota(prestamo.getCuotas().get(1));

        // Una cancelación total (calcularCancelacionTotal) toca varias cuotas a la vez, pero cada
        // una queda en aplicaciones_pago con su propio neto -- mismo contrato de entrada que acá:
        // un pago único que alcanza para las 2 primeras cuotas vencidas (fecha posterior a ambos
        // vencimientos) ejercita la misma situación de "revertir toca más de una cuota". Se
        // recorren TODOS los detalles que haya (no se asume la cantidad exacta), cada uno contra
        // la cuota original que le corresponde por número.
        LocalDate fechaPago = LocalDate.of(2026, 10, 1);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("579389"), fechaPago, 0);
        assertTrue("tiene que tocar más de una cuota", resultado.getDetalles().size() >= 2);

        for (DetalleAplicacionPago detalle : resultado.getDetalles()) {
            Cuota original = detalle.getNumeroCuota() == 1 ? cuota1Original : cuota2Original;
            Cuota restaurada = revertirCC23(detalle, null, fechaPago);
            assertCuotaRestauradaIgualOriginal(original, restaurada);
        }
    }

    @Test
    public void testE9RevertirSegundoTouqueRestauraEstadoDelPrimero() {
        // Dos pagos sobre la MISMA cuota: el primero (parcial, tarde) deja moratorio pendiente;
        // el segundo (el que se revierte) la completa. Al revertir el segundo, tiene que volver
        // exactamente al estado que dejó el PRIMERO -- no al original sin tocar -- incluida
        // fecha_corte_mora, que el motor siempre pisa con la fecha de cada touque (D-77/E9). Este
        // es el caso real más delicado: una resta ingenua del neto aplicado NO alcanza acá (ver
        // el comentario de revertirAplicacionEnCuota).
        Prestamo prestamo = prestamoCC23();

        LocalDate fechaPrimerPago = LocalDate.of(2026, 9, 15);
        ResultadoAplicacionPago primerPago = servicio.distribuirPago(prestamo, new BigDecimal("100000"),
                fechaPrimerPago, 0);
        Cuota cuotaTrasPrimerPago = primerPago.getDetalles().get(0).getCuotaResultante();
        assertEquals(EstadoPago.PARCIAL, cuotaTrasPrimerPago.getEstadoPago());

        prestamo.getCuotas().set(0, cuotaTrasPrimerPago);
        LocalDate fechaSegundoPago = LocalDate.of(2026, 9, 20);
        ResultadoAplicacionPago segundoPago = servicio.distribuirPago(prestamo, new BigDecimal("300000"),
                fechaSegundoPago, 0);
        DetalleAplicacionPago detalleSegundo = segundoPago.getDetalles().get(0);
        assertEquals(EstadoPago.PAGADA, detalleSegundo.getCuotaResultante().getEstadoPago());

        Cuota restaurada = revertirCC23(detalleSegundo, fechaPrimerPago, fechaSegundoPago);

        assertCuotaRestauradaIgualOriginal(cuotaTrasPrimerPago, restaurada);
    }

    // ==================================================================
    // D-144 (Ley 6624/2020, corrección de Joel sobre D-140/D-143): "Cobrar" (cobro normal) dejó
    // de cerrar cuotas NO vencidas -- el diseño de D-140 (interés prorrateado por días, cuota
    // marcada PAGADA) dejaba un hueco de días sin generar interés sobre el saldo que seguía
    // siendo deuda vigente (caso real: María González, CC-2026-00007, ~75.763 Gs. de interés
    // perdido). Ahora un cobro normal solo toca lo vencido + la cuota que vence JUSTO hoy; todo
    // pago anticipado de capital pasa por "Adelantar capital" (calcularAnticipoParcial), que
    // recalcula el plan con interés continuo sobre el saldo que queda. Fixture:
    // prestamoCC23()/planCC23() (desembolso 18/07/2026, TEA 0,27, cuota 1: capital 223.531,
    // interés programado 60.353, vencimiento teórica y efectiva 19/08/2026; cuota 2: capital
    // 228.028, interés programado 55.856, vencimiento teórica 19/09/2026, efectiva 21/09/2026).
    // ==================================================================

    @Test
    public void testDistribuirPago_CuotaQueVenceHoy_CobroContractualCompletoSinCambios() {
        // Una cuota que vence JUSTO hoy sigue siendo "lo vencido" para Cobrar -- cobro contractual
        // completo, sin prorrateo (nunca fue un caso de "pago anticipado").
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1 = prestamo.getCuotas().get(0);
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("300000"),
                cuota1.getFechaVencimiento(), 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertMonto(60353, detalle.getMontoInteres());
        assertMonto(6035, detalle.getIvaInteres());
    }

    @Test
    public void testDistribuirPago_SinVencidasYSinCuotaDeHoy_NoCobraNada() {
        // Caso (a) pedido por Joel: préstamo sin vencidas -- "Cobrar" no toca ninguna cuota futura
        // (ServicioCobranza.confirmarCobro usa este mismo resultado vacío para rechazar el cobro
        // con un mensaje que indica usar "Adelantar capital").
        Prestamo prestamo = prestamoCC23();
        LocalDate fechaPago = prestamo.getFechaDesembolso(); // cuota 1 vence recién el 19/08, no hoy.
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("300000"), fechaPago, 0);

        assertTrue("no se toca ninguna cuota", resultado.getDetalles().isEmpty());
        assertMonto(300000, resultado.getMontoSobrante());
    }

    @Test
    public void testDistribuirPago_CuotaVencidaMasExcedente_SoloCobraLaVencida() {
        // Caso (d) pedido por Joel: cuota 1 vencida + dinero de sobra que alcanzaría para la
        // cuota 2 (no vencida) -- se cobra SOLO la vencida; el resto queda como vuelto, nunca
        // toca la cuota 2 (antes de D-144, esto habría "anticipado" la cuota 2 con interés
        // prorrateado -- exactamente el bug que se retiró).
        Prestamo prestamo = prestamoCC23();
        LocalDate fechaPago = LocalDate.of(2026, 9, 5); // cuota 1 (vto. 19/08) vencida; cuota 2 (vto. teórica 19/09) no
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, new BigDecimal("700000"), fechaPago, 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleAplicacionPago detalleCuota1 = resultado.getDetalles().get(0);
        assertEquals(1, detalleCuota1.getNumeroCuota());
        // Vencida: se cobra igual que siempre, interés contractual completo, con su mora.
        assertMonto(60353, detalleCuota1.getMontoInteres());
        assertEquals(EstadoPago.PAGADA, detalleCuota1.getCuotaResultante().getEstadoPago());
        // Sobra mucho más que el total programado de la cuota 2 (289.470) -- antes de D-144 esto
        // habría alcanzado de sobra para "anticiparla"; ahora queda intacto como vuelto.
        assertTrue("el vuelto alcanzaría de sobra para la cuota 2 (prueba de que no se tocó)",
                resultado.getMontoSobrante().compareTo(new BigDecimal("289470")) > 0);
    }

    // ==================================================================
    // D-146 (escenarios A1, A4 y P-17 de Joel, probados con fecha simulada porque hoy, en
    // sgcpc_db_pruebas, ningún préstamo real tiene una cuota que venza justo hoy): no se toca
    // ningún dato, solo se llama al motor con la fecha que corresponde a cada caso. Fixture:
    // prestamoCC23() (cuota 1: vto. 19/08/2026, total 289.919; cuota 2: vto. 21/09/2026, total
    // 289.470, capital 228.028, interés 55.856, IVA 5.586).
    // ==================================================================

    @Test
    public void testA1_CuotaQueVenceHoyPagadaConMontoJustoQuedaPagadaSinMora() {
        // A1: cuota que vence hoy, pagada con el monto EXACTO del plan (sin excedente) -- cobro
        // contractual completo, sin mora (dias=0), PAGADA. montoInteres/ivaInteres son
        // exactamente lo que confirmarCobro factura (no se toca ese servicio: esto prueba la
        // base del motor que reusa tal cual).
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1 = prestamo.getCuotas().get(0);
        BigDecimal montoExacto = cuota1.getTotalProgramado(); // 289.919 (223.531 + 60.353 + 6.035)

        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, montoExacto,
                cuota1.getFechaVencimiento(), 0);

        assertEquals(1, resultado.getDetalles().size());
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertEquals(1, detalle.getNumeroCuota());
        assertMonto(223531, detalle.getMontoCapital());
        assertMonto(60353, detalle.getMontoInteres());
        assertMonto(6035, detalle.getIvaInteres());
        assertMonto(0, detalle.getMontoMoratorio());
        assertMonto(0, detalle.getMontoPunitorio());
        assertEquals(EstadoPago.PAGADA, detalle.getCuotaResultante().getEstadoPago());
        assertMonto(0, resultado.getMontoSobrante());
    }

    @Test
    public void testA4_CuotaQueVenceHoySinVencidas_EsCobrableSinMoraNiPromesaAunQuedandoParcial() {
        // A4: sin cuotas vencidas, cuota 1 vence justo hoy -- "Pagar cuota" la toma como cobrable
        // (D-144, distribuirPago procesa "lo vencido y/o la cuota que vence justo hoy") aunque
        // calcularDeudaExigible (la base de "lo vencido" que usa confirmarCobro para esParcial/
        // RN-20) siga dando CERO -- por eso tampoco exige promesa, ni siquiera si lo recibido no
        // la cubre entera y queda PARCIAL.
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1 = prestamo.getCuotas().get(0);
        LocalDate hoy = cuota1.getFechaVencimiento();

        ResultadoDeudaExigible exigible = servicio.calcularDeudaExigible(prestamo, hoy, 0);
        assertTrue("sin vencidas: la cuota que vence HOY no es 'vencida' para calcularDeudaExigible",
                exigible.getDetalles().isEmpty());
        assertMonto(0, exigible.getTotalGeneral());

        BigDecimal recibidoParcial = new BigDecimal("150000"); // menos que el total de la cuota (289.919)
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, recibidoParcial, hoy, 0);

        assertEquals("la toma como cobrable aunque no sea 'vencida'", 1, resultado.getDetalles().size());
        DetalleAplicacionPago detalle = resultado.getDetalles().get(0);
        assertEquals(1, detalle.getNumeroCuota());
        assertMonto(0, detalle.getMontoMoratorio());
        assertMonto(0, detalle.getMontoPunitorio());
        assertEquals("queda PARCIAL, pero sin pedir promesa -- exigible.getTotalGeneral() sigue en 0, "
                + "que es lo único que mira confirmarCobro para esParcial (RN-20)",
                EstadoPago.PARCIAL, detalle.getCuotaResultante().getEstadoPago());
    }

    @Test
    public void testP17_VencidaMasCuotaDeHoy_RecibidoCubreLaVencidaYParteDeHoy_HoyQuedaParcialSinMoraNiPromesa() {
        // P-17: cuota 1 vencida (desde el 19/08) + cuota 2 que vence justo hoy (21/09) -- lo
        // recibido cubre la vencida entera (con su mora real) y una parte de la de hoy. La
        // vencida queda PAGADA (con mora, porque es una vencida de verdad); la de hoy queda
        // PARCIAL pero sin mora ni punitorio (todavía no es "vencida") -- el mismo mecanismo de
        // A4, ahora con una vencida real antes en el mismo cobro.
        Prestamo prestamo = prestamoCC23();
        Cuota cuota1 = prestamo.getCuotas().get(0);
        Cuota cuota2 = prestamo.getCuotas().get(1);
        LocalDate hoy = cuota2.getFechaVencimiento(); // 21/09/2026 -- cuota 1 (vto. 19/08) ya vencida

        ResultadoDeudaExigible exigibleVencidas = servicio.calcularDeudaExigible(prestamo, hoy, 0);
        assertEquals(1, exigibleVencidas.getDetalles().size());
        assertEquals(1, exigibleVencidas.getDetalles().get(0).getNumeroCuota());
        BigDecimal totalVencida = exigibleVencidas.getTotalGeneral();
        assertTrue("la vencida ya generó mora real, no es solo el total programado",
                totalVencida.compareTo(cuota1.getTotalProgramado()) > 0);

        BigDecimal parteDeHoy = new BigDecimal("100000"); // menos que el total de la cuota 2 (289.470)
        BigDecimal recibido = totalVencida.add(parteDeHoy);

        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, recibido, hoy, 0);

        assertEquals(2, resultado.getDetalles().size());
        DetalleAplicacionPago detalleVencida = resultado.getDetalles().get(0);
        assertEquals(1, detalleVencida.getNumeroCuota());
        assertEquals(EstadoPago.PAGADA, detalleVencida.getCuotaResultante().getEstadoPago());
        assertTrue("la vencida sí paga mora", detalleVencida.getMontoMoratorio().compareTo(BigDecimal.ZERO) > 0);

        DetalleAplicacionPago detalleHoy = resultado.getDetalles().get(1);
        assertEquals(2, detalleHoy.getNumeroCuota());
        assertEquals(EstadoPago.PARCIAL, detalleHoy.getCuotaResultante().getEstadoPago());
        assertMonto(0, detalleHoy.getMontoMoratorio());
        assertMonto(0, detalleHoy.getMontoPunitorio());
        assertMonto(55856, detalleHoy.getMontoInteres());
        assertMonto(5586, detalleHoy.getIvaInteres());
        assertMonto(38558, detalleHoy.getMontoCapital());

        assertMonto(0, resultado.getMontoSobrante());
    }

    @Test
    public void testP17_DiaSiguiente_LaParteQueQuedoDeLaCuotaDeHoyGeneraMoraDesdeSuVencimiento() {
        // Continuación de P-17 (pedido de Joel): un día después, lo que quedó PARCIAL de la
        // cuota que ayer vencía ya es una vencida de verdad -- la mora arranca justo en su fecha
        // de vencimiento (calcularMora usa fechaCorteMora como inicio del tramo solo si es
        // POSTERIOR a fechaVencimiento; acá coinciden -- aplicarPagoACuota dejó fechaCorteMora en
        // esa misma fecha -- así que no la corre ni un día de más ni de menos).
        Prestamo prestamo = prestamoCC23();
        Cuota cuota2 = prestamo.getCuotas().get(1);
        LocalDate hoy = cuota2.getFechaVencimiento();

        ResultadoDeudaExigible exigibleVencidas = servicio.calcularDeudaExigible(prestamo, hoy, 0);
        BigDecimal recibido = exigibleVencidas.getTotalGeneral().add(new BigDecimal("100000"));
        ResultadoAplicacionPago resultado = servicio.distribuirPago(prestamo, recibido, hoy, 0);
        Cuota cuotaHoyParcial = resultado.getDetalles().get(1).getCuotaResultante();

        LocalDate diaSiguiente = hoy.plusDays(1);
        ResultadoMora moraDiaSiguiente = servicio.calcularMora(cuotaHoyParcial, diaSiguiente,
                prestamo.getTasaMoratoria(), prestamo.getTasaPunitoria(), 0);

        assertEquals(1, moraDiaSiguiente.getDias());
        assertTrue("al día siguiente del vencimiento ya genera mora",
                moraDiaSiguiente.getMoratorioTramo().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void testCalcularAnticipoParcial_UnaCuotaAdelantadaUnosDiasAntes_SaldoSigueDevengandoInteres() {
        // Caso (b) pedido por Joel: adelantar (vía "Adelantar capital") el monto de la cuota 1
        // unos días antes de su vencimiento (15/08, 4 días antes del 19/08) -- el saldo que queda
        // tiene que seguir generando interés en cada período del plan nuevo (nunca un hueco en
        // cero), y el total de intereses de todo el préstamo con el adelanto tiene que ser MENOR
        // que pagando todo en fecha, pero MAYOR que cero.
        Prestamo prestamo = prestamoCC23();
        LocalDate fechaAnticipo = LocalDate.of(2026, 8, 15);
        BigDecimal montoAnticipo = new BigDecimal("300000"); // cubre el interés del período + el capital de la cuota 1.
        ResultadoLiquidacion liquidacion = servicio.calcularAnticipoParcial(prestamo, montoAnticipo, fechaAnticipo,
                Collections.<LocalDate>emptySet());

        assertTrue("interés devengado del período en curso > 0 (no es el día del desembolso)",
                liquidacion.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);
        assertTrue("capital aplicado > 0", liquidacion.getMontoAnticipoCapital().compareTo(BigDecimal.ZERO) > 0);

        BigDecimal interesTotalConAnticipo = liquidacion.getInteresDevengado();
        for (Cuota cuota : liquidacion.getNuevoPlan()) {
            assertTrue("la cuota " + cuota.getNumeroCuota() + " del plan nuevo sigue devengando interés",
                    cuota.getInteresProgramado().compareTo(BigDecimal.ZERO) > 0);
            interesTotalConAnticipo = interesTotalConAnticipo.add(cuota.getInteresProgramado());
        }

        BigDecimal interesTotalSinAnticipo = BigDecimal.ZERO;
        for (Cuota cuota : planCC23()) {
            interesTotalSinAnticipo = interesTotalSinAnticipo.add(cuota.getInteresProgramado());
        }

        assertTrue("con el adelanto, el total de intereses del préstamo es menor que pagando en fecha",
                interesTotalConAnticipo.compareTo(interesTotalSinAnticipo) < 0);
        assertTrue("pero nunca cero mientras haya saldo (nunca un hueco de días sin interés, D-144)",
                interesTotalConAnticipo.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void testCalcularAnticipoParcial_DosCuotasAdelantadasElMismoDia_SaldoSigueDevengandoInteres() {
        // Caso (c) pedido por Joel: mismo chequeo, adelantando de una sola vez el monto
        // equivalente a dos cuotas (cuota 1 + cuota 2: 223.531 + 228.028 = 451.559 de capital).
        Prestamo prestamo = prestamoCC23();
        LocalDate fechaAnticipo = LocalDate.of(2026, 8, 15);
        BigDecimal montoAnticipo = new BigDecimal("600000");
        ResultadoLiquidacion liquidacion = servicio.calcularAnticipoParcial(prestamo, montoAnticipo, fechaAnticipo,
                Collections.<LocalDate>emptySet());

        assertTrue("interés devengado del período en curso > 0",
                liquidacion.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);
        assertTrue("capital aplicado cubre más de una cuota (> 228.028)",
                liquidacion.getMontoAnticipoCapital().compareTo(new BigDecimal("228028")) > 0);

        BigDecimal interesTotalConAnticipo = liquidacion.getInteresDevengado();
        for (Cuota cuota : liquidacion.getNuevoPlan()) {
            assertTrue("la cuota " + cuota.getNumeroCuota() + " del plan nuevo sigue devengando interés",
                    cuota.getInteresProgramado().compareTo(BigDecimal.ZERO) > 0);
            interesTotalConAnticipo = interesTotalConAnticipo.add(cuota.getInteresProgramado());
        }

        BigDecimal interesTotalSinAnticipo = BigDecimal.ZERO;
        for (Cuota cuota : planCC23()) {
            interesTotalSinAnticipo = interesTotalSinAnticipo.add(cuota.getInteresProgramado());
        }

        assertTrue("con el adelanto, el total de intereses del préstamo es menor que pagando en fecha",
                interesTotalConAnticipo.compareTo(interesTotalSinAnticipo) < 0);
        assertTrue("pero nunca cero mientras haya saldo (nunca un hueco de días sin interés, D-144)",
                interesTotalConAnticipo.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void testCalcularCancelacionTotal_CuotaAnticipadaElMismoDia_InteresDelPeriodoCero() {
        // D-143: pagar la cuota 2 anticipada (vía Adelantar capital) y pedir cancelación total el
        // mismo día -- calcularCancelacionTotal da 0 de interés del período en curso (la cuota que
        // contiene la fecha ya está pagada, D-86) -- regresión para que ningún cambio futuro rompa
        // la consistencia entre los caminos que sí manejan el pago anticipado de capital.
        Prestamo prestamo = prestamoCC23();
        List<Cuota> cuotas = planCC23();
        List<Cuota> cuotasPagadas = new ArrayList<>();
        cuotasPagadas.add(marcarPagada(cuotas.get(0)));
        cuotasPagadas.add(marcarPagada(cuotas.get(1)));
        for (int indice = 2; indice < cuotas.size(); indice++) {
            cuotasPagadas.add(cuotas.get(indice));
        }
        prestamo.setCuotas(cuotasPagadas);

        ResultadoLiquidacion liquidacion = servicio.calcularCancelacionTotal(prestamo, prestamo.getFechaDesembolso(), 0);

        assertMonto(0, liquidacion.getInteresDevengado());
        assertMonto(0, liquidacion.getIvaInteresDevengado());
    }

    // ==================================================================
    // D-145 (E-3, pedido de Joel): el "hueco" de D-87 (una cuota PAGADA cuyo período contiene
    // "hoy", sin prorratear nada de interés) solo da un hueco REAL cuando la fecha de cálculo
    // es ANTERIOR al vencimiento teórico de esa cuota -- "pagada por adelantado" (planCC23ConCuota3PagadaPorAdelantado,
    // BF-15/BF-16, más arriba). El caso de María (CC-2026-00007, AVANCE.md): su cuota 3 quedó
    // PAGADA con vencimiento todavía futuro, dato heredado de ANTES de D-144.
    //
    // Bajo D-144 ("Cobrar" nunca marca PAGADA una cuota antes de su vencimiento -- ver
    // ServicioMotorFinanciero.distribuirPago), una cuota solo puede llegar a PAGADA con
    // fecha_del_pago >= su propio vencimiento. Para cualquier fecha de cálculo POSTERIOR a esa
    // cuota (el único caso real posible), periodoActual ya encuentra la cuota SIGUIENTE
    // (todavía pendiente) como referencia -- nunca vuelve a entrar a la cuota ya pagada -- así
    // que el interés prorratea normalmente los días transcurridos, sin huecos. El único caso
    // límite es fecha == vencimiento teórico exacto (pagada el mismo día que vence): ahí el
    // interés 0 es CORRECTO (ya se cobró como parte de esa misma cuota) y el período del plan
    // nuevo arranca ese mismo día, sin saltar un mes (inicioPeriodoTeorico: unMesAntes nunca
    // puede ganarle a fechaAnticipo si fechaAnticipo == vencimiento teórico, como mucho empatan).
    // Los 4 casos de abajo (pedidos por Joel) confirman esto con el motor puro, sin base de
    // datos -- si alguno fallara, el arreglo iría en inicioPeriodoTeorico (D-87), no acá.
    // ==================================================================

    @Test
    public void testE3a_AnticipoDiezDiasDespuesDeUnaCuotaVencidaYaPagadaCobraLosDiezDias() {
        // Cuota 1 vencida (19/08) y pagada por completo -- tal como D-144 exige, nunca antes de
        // su propio vencimiento. Diez días después (29/08) se anticipa capital.
        Prestamo prestamo = prestamoCC23();
        List<Cuota> cuotas = planCC23();
        cuotas.set(0, marcarPagada(cuotas.get(0)));
        prestamo.setCuotas(cuotas);

        LocalDate vencimientoCuota1 = LocalDate.of(2026, 8, 19);
        LocalDate hoy = vencimientoCuota1.plusDays(10);

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("300000"), hoy,
                Collections.<LocalDate>emptySet());

        assertTrue("el interés tiene que cubrir los 10 días desde el vencimiento de la cuota 1 -- no puede ser 0",
                resultado.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);
        assertMonto(18018, resultado.getInteresDevengado());
        assertMonto(1802, resultado.getIvaInteresDevengado());
        // El período de la cuota 1 del plan nuevo sigue siendo el de la cuota 2 original
        // (19/08→19/09) -- no salta a 19/10 (eso sería el hueco de D-87/D-86, que acá no aplica
        // porque la cuota de referencia, cuota 2, sigue PENDIENTE).
        assertEquals(LocalDate.of(2026, 9, 19), resultado.getNuevoPlan().get(0).getFechaVencimientoTeorica());
    }

    @Test
    public void testE3b_AnticipoElMismoDiaQueVenceLaCuotaRecienPagadaDaInteresCeroCorrectamente() {
        // Cuota 1 vence hoy mismo (19/08) y se paga hoy -- el anticipo es el mismo día. Interés 0
        // es correcto acá: ya se cobró como parte del pago de la cuota 1 (D-86).
        Prestamo prestamo = prestamoCC23();
        List<Cuota> cuotas = planCC23();
        cuotas.set(0, marcarPagada(cuotas.get(0)));
        prestamo.setCuotas(cuotas);

        LocalDate hoy = LocalDate.of(2026, 8, 19);

        ResultadoLiquidacion resultado = servicio.calcularAnticipoParcial(prestamo, new BigDecimal("300000"), hoy,
                Collections.<LocalDate>emptySet());

        assertMonto(0, resultado.getInteresDevengado());
        assertMonto(0, resultado.getIvaInteresDevengado());
        // Como la cuota de referencia (cuota 1) ya está PAGADA, el plan nuevo arranca un período
        // ENTERO después (19/09, un mes después de su teórica) -- igual que BF-16. Acá
        // "fechaAnticipo" (19/08, HOY) empata exacto con "unMesAntes" (19/09 − 1 mes = 19/08) en
        // la fórmula MAX de inicioPeriodoTeorico -- si se anticipara de nuevo sobre este plan
        // nuevo, el período de SU cuota 1 arrancaría ese mismo 19/08, sin hueco (a diferencia del
        // hueco real, donde unMesAntes gana por semanas, no por un empate).
        assertEquals(LocalDate.of(2026, 9, 19), resultado.getNuevoPlan().get(0).getFechaVencimientoTeorica());
    }

    @Test
    public void testE3c_SegundoAnticipoCincoDiasDespuesCobraSoloEsosCincoDiasSinHuecoNiDuplicado() {
        // Mismo punto de partida que E3a (cuota 1 vencida y pagada, anticipo a los 10 días).
        // Cinco días más tarde, sobre el plan ya regenerado, un segundo anticipo: tiene que
        // cobrar justo esos 5 días -- ni 0 (hueco) ni volver a cobrar los primeros 10.
        Prestamo prestamo1 = prestamoCC23();
        List<Cuota> cuotas1 = planCC23();
        cuotas1.set(0, marcarPagada(cuotas1.get(0)));
        prestamo1.setCuotas(cuotas1);

        LocalDate fechaAnticipo1 = LocalDate.of(2026, 8, 19).plusDays(10);
        ResultadoLiquidacion liquidacion1 = servicio.calcularAnticipoParcial(prestamo1, new BigDecimal("300000"),
                fechaAnticipo1, Collections.<LocalDate>emptySet());
        assertTrue(liquidacion1.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);

        Prestamo prestamo2 = new Prestamo();
        prestamo2.setFechaDesembolso(prestamo1.getFechaDesembolso());
        prestamo2.setTasaTea(prestamo1.getTasaTea());
        prestamo2.setTasaMoratoria(prestamo1.getTasaMoratoria());
        prestamo2.setTasaPunitoria(prestamo1.getTasaPunitoria());
        prestamo2.setCuotas(liquidacion1.getNuevoPlan());
        prestamo2.setOrigenPlanVigente("ANTICIPO_PARCIAL");
        prestamo2.setFechaGeneracionPlanVigente(fechaAnticipo1);

        LocalDate fechaAnticipo2 = fechaAnticipo1.plusDays(5);
        ResultadoLiquidacion liquidacion2 = servicio.calcularAnticipoParcial(prestamo2, new BigDecimal("100000"),
                fechaAnticipo2, Collections.<LocalDate>emptySet());

        assertTrue("el segundo anticipo tiene que cobrar los 5 días transcurridos -- no puede ser 0",
                liquidacion2.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);
        assertMonto(11957, liquidacion2.getInteresDevengado());
        assertMonto(1196, liquidacion2.getIvaInteresDevengado());
    }

    @Test
    public void testE3d_CancelacionDiezDiasDespuesDeUnaCuotaVencidaYaPagadaCobraLosDiezDias() {
        // Mismo escenario que E3a, pero con Cancelación total en vez de Adelantar capital.
        Prestamo prestamo = prestamoCC23();
        List<Cuota> cuotas = planCC23();
        cuotas.set(0, marcarPagada(cuotas.get(0)));
        prestamo.setCuotas(cuotas);

        LocalDate vencimientoCuota1 = LocalDate.of(2026, 8, 19);
        LocalDate hoy = vencimientoCuota1.plusDays(10);

        ResultadoLiquidacion resultado = servicio.calcularCancelacionTotal(prestamo, hoy, 0);

        assertTrue("el interés de la cancelación tiene que cubrir los 10 días -- no puede ser 0",
                resultado.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0);
        assertMonto(18018, resultado.getInteresDevengado());
        assertMonto(1802, resultado.getIvaInteresDevengado());
    }

    // ======================================================================
    // C3 (29/10/2026): el texto del contrato tiene que describir EXACTAMENTE este anclaje, así que
    // queda verificado acá, sobre el plan real -- no por lectura de plusMonths.
    // ======================================================================

    @Test
    public void testElPlanAnclaCadaCuotaAlDiaDeLaFechaElegidaYNoAlCorrido() {
        // Elegida: sábado 28/11/2026 -> la cuota 1 se corre al lunes 30/11, pero la cuota 2 NO
        // vence el 30/12: vuelve al día 28 (lunes, hábil). Esto es lo que el contrato tenía mal
        // descrito como "las siguientes el mismo día de cada mes".
        List<Cuota> plan = servicio.generarPlanPagos(new BigDecimal("1000000"), 3, TEA,
                LocalDate.of(2026, 11, 28), Collections.<LocalDate>emptySet());
        assertEquals(LocalDate.of(2026, 11, 30), plan.get(0).getFechaVencimiento());
        assertEquals(LocalDate.of(2026, 11, 28), plan.get(0).getFechaVencimientoTeorica());
        assertEquals(LocalDate.of(2026, 12, 28), plan.get(1).getFechaVencimiento());
        assertEquals(LocalDate.of(2027, 1, 28), plan.get(2).getFechaVencimiento());
    }

    @Test
    public void testConDia31ElPlanUsaElUltimoDiaDeLosMesesQueNoLoTienen() {
        // Elegida: 31/12/2026 (jueves). Enero tiene 31; febrero no, y plusMonths resuelve al
        // último día del mes (28/02/2027, domingo -> se corre al lunes 01/03). De ahí la
        // aclaración "o el último día del mes si ese mes no lo tiene" de la cláusula SEGUNDA.
        List<Cuota> plan = servicio.generarPlanPagos(new BigDecimal("1000000"), 3, TEA,
                LocalDate.of(2026, 12, 31), Collections.<LocalDate>emptySet());
        assertEquals(LocalDate.of(2026, 12, 31), plan.get(0).getFechaVencimiento());
        assertEquals(LocalDate.of(2027, 1, 31), plan.get(1).getFechaVencimientoTeorica());
        // 31/01/2027 es domingo: se corre al lunes 01/02.
        assertEquals(LocalDate.of(2027, 2, 1), plan.get(1).getFechaVencimiento());
        // Febrero no tiene 31: la teórica es el último día del mes.
        assertEquals(LocalDate.of(2027, 2, 28), plan.get(2).getFechaVencimientoTeorica());
        assertEquals(LocalDate.of(2027, 3, 1), plan.get(2).getFechaVencimiento());
    }
}
