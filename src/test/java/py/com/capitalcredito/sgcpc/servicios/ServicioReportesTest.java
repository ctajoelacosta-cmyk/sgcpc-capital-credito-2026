package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.FilaInformePrestamo;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo;
import py.com.capitalcredito.sgcpc.dto.TotalesGestiones;
import py.com.capitalcredito.sgcpc.dto.TotalesPrestamos;
import py.com.capitalcredito.sgcpc.dto.TotalesVencimientos;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/**
 * B1 (Bloque 7/E10, pedido de Joel): métodos puros de ServicioReportes para el listado de
 * Préstamos (filas y totales, con el resumen por estado) -- ninguno toca la base, mismo criterio
 * que ServicioInformesTest.
 */
public class ServicioReportesTest {

    private final ServicioReportes servicio = new ServicioReportes();

    private static Cuota cuota(String capitalProgramado, String capitalPagado, EstadoPago estado) {
        Cuota cuota = new Cuota();
        cuota.setCapitalProgramado(new BigDecimal(capitalProgramado));
        cuota.setCapitalPagado(new BigDecimal(capitalPagado));
        cuota.setEstadoPago(estado);
        return cuota;
    }

    private static FilaInformePrestamo fila(String identificadorOperacion, String estado, String monto, String saldo) {
        return new FilaInformePrestamo(1L, identificadorOperacion, "Cliente de Prueba", "1.234.567",
                new BigDecimal(monto), monto + " Gs.", 12, "01/10/2026", estado, new BigDecimal(saldo),
                saldo + " Gs.", "3/12");
    }

    // E-9 (pedido de Joel): sin desembolso no hay deuda -- PENDIENTE_DESEMBOLSO/ANULADO_ANTES_
    // DESEMBOLSO no tienen saldo (null, nunca "0" ni el monto completo), mismo criterio que
    // ServicioReportes.listarInformePrestamos.
    private static FilaInformePrestamo filaSinDeuda(String identificadorOperacion, String estado, String monto) {
        return new FilaInformePrestamo(1L, identificadorOperacion, "Cliente de Prueba", "1.234.567",
                new BigDecimal(monto), monto + " Gs.", 12, "—", estado, null, "—", "—");
    }

    // sumarSaldoCapital (package-private, extraído de calcularTotalesEstadoCuenta): B1 (listado
    // de Préstamos) la reusa tal cual -- pedido explícito de Joel de que el saldo salga del
    // mismo cálculo que ya usa Estado de cuenta. Probada acá, sobre cuotas ya cargadas (sin
    // base), con los mismos tres casos que importan: nada pagado, parcial, todo pagado.
    @Test
    public void testSumarSaldoCapitalNadaPagado() {
        List<Cuota> cuotas = Arrays.asList(
                cuota("500000", "0", EstadoPago.PENDIENTE),
                cuota("500000", "0", EstadoPago.PENDIENTE));
        assertEquals(0, new BigDecimal("1000000").compareTo(ServicioReportes.sumarSaldoCapital(cuotas)));
    }

    @Test
    public void testSumarSaldoCapitalParcial() {
        List<Cuota> cuotas = Arrays.asList(
                cuota("500000", "500000", EstadoPago.PAGADA),
                cuota("500000", "200000", EstadoPago.PARCIAL),
                cuota("500000", "0", EstadoPago.PENDIENTE));
        assertEquals(0, new BigDecimal("800000").compareTo(ServicioReportes.sumarSaldoCapital(cuotas)));
    }

    @Test
    public void testSumarSaldoCapitalTodoPagado() {
        List<Cuota> cuotas = Arrays.asList(
                cuota("500000", "500000", EstadoPago.PAGADA),
                cuota("500000", "500000", EstadoPago.PAGADA));
        assertEquals(0, BigDecimal.ZERO.compareTo(ServicioReportes.sumarSaldoCapital(cuotas)));
    }

    @Test
    public void testSumarSaldoCapitalSinCuotas() {
        assertEquals(0, BigDecimal.ZERO.compareTo(ServicioReportes.sumarSaldoCapital(java.util.Collections.<Cuota>emptyList())));
    }

    @Test
    public void testCalcularTotalesPrestamos() {
        List<FilaInformePrestamo> filas = Arrays.asList(
                fila("CC-2026-00001", "Activo", "6000000", "4000000"),
                fila("CC-2026-00002", "En mora", "3000000", "2500000"));
        TotalesPrestamos totales = servicio.calcularTotalesPrestamos(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals("9.000.000 Gs.", totales.getMontoTotal());
        assertEquals("6.500.000 Gs.", totales.getSaldoTotal());
    }

    @Test
    public void testCalcularTotalesPrestamosSinFilas() {
        TotalesPrestamos totales = servicio.calcularTotalesPrestamos(java.util.Collections.<FilaInformePrestamo>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals("0 Gs.", totales.getMontoTotal());
        assertEquals("0 Gs.", totales.getSaldoTotal());
    }

    @Test
    public void testCalcularResumenPorEstadoPrestamosAgrupaYSuma() {
        List<FilaInformePrestamo> filas = Arrays.asList(
                fila("CC-2026-00001", "Activo", "6000000", "4000000"),
                fila("CC-2026-00002", "Activo", "2000000", "1000000"),
                fila("CC-2026-00003", "En mora", "3000000", "2500000"));
        List<ResumenEstadoPrestamo> resumen = servicio.calcularResumenPorEstadoPrestamos(filas);
        assertEquals(2, resumen.size());
        ResumenEstadoPrestamo activo = resumen.get(0);
        assertEquals("Activo", activo.getEstado());
        assertEquals(2, activo.getCantidad());
        assertEquals("8.000.000 Gs.", activo.getMonto());
        assertEquals("5.000.000 Gs.", activo.getSaldo());
        ResumenEstadoPrestamo enMora = resumen.get(1);
        assertEquals("En mora", enMora.getEstado());
        assertEquals(1, enMora.getCantidad());
    }

    @Test
    public void testCalcularResumenPorEstadoPrestamosSoloEstadosConFilas() {
        List<FilaInformePrestamo> filas = Arrays.asList(fila("CC-2026-00001", "Finalizado", "1000000", "0"));
        List<ResumenEstadoPrestamo> resumen = servicio.calcularResumenPorEstadoPrestamos(filas);
        assertEquals(1, resumen.size());
        assertEquals("Finalizado", resumen.get(0).getEstado());
    }

    @Test
    public void testCalcularResumenPorEstadoPrestamosVacio() {
        assertTrue(servicio.calcularResumenPorEstadoPrestamos(java.util.Collections.<FilaInformePrestamo>emptyList()).isEmpty());
    }

    // E-9 (pedido de Joel, encontrado probando B1 en el navegador): "Pendiente de desembolso" y
    // "Anulado antes del desembolso" no tienen deuda -- no pueden sumar al saldo total ni al
    // saldo del resumen por estado, aunque el monto total los siga sumando a todos.

    @Test
    public void testCalcularTotalesPrestamosExcluyePendienteYAnuladoDelSaldo() {
        List<FilaInformePrestamo> filas = Arrays.asList(
                fila("CC-2026-00001", "Activo", "6000000", "4000000"),
                filaSinDeuda("CC-2026-00002", "Pendiente de desembolso", "6000000"),
                filaSinDeuda("CC-2026-00003", "Anulado antes del desembolso", "5000000"));
        TotalesPrestamos totales = servicio.calcularTotalesPrestamos(filas);
        assertEquals(3, totales.getCantidad());
        // El monto total sigue sumando TODO (pedido explícito de Joel).
        assertEquals("17.000.000 Gs.", totales.getMontoTotal());
        // El saldo total excluye las dos filas sin deuda: solo queda el Activo.
        assertEquals("4.000.000 Gs.", totales.getSaldoTotal());
    }

    @Test
    public void testCalcularResumenPorEstadoPrestamosSinDeudaMuestraGuion() {
        List<FilaInformePrestamo> filas = Arrays.asList(
                filaSinDeuda("CC-2026-00002", "Pendiente de desembolso", "6000000"),
                filaSinDeuda("CC-2026-00003", "Anulado antes del desembolso", "5000000"));
        List<ResumenEstadoPrestamo> resumen = servicio.calcularResumenPorEstadoPrestamos(filas);
        assertEquals(2, resumen.size());
        for (ResumenEstadoPrestamo filaResumen : resumen) {
            // Nunca "0 Gs." -- ninguna fila de ese estado tiene saldo, así que el resumen dice "—".
            assertEquals("—", filaResumen.getSaldo());
            // El monto sigue siendo real (no "—"): el monto total sí los suma a todos.
            assertTrue(filaResumen.getMonto().endsWith(" Gs."));
        }
    }

    @Test
    public void testCalcularResumenPorEstadoPrestamosMezclaConDeudaYSinDeuda() {
        List<FilaInformePrestamo> filas = Arrays.asList(
                fila("CC-2026-00001", "Activo", "6000000", "4000000"),
                fila("CC-2026-00002", "Activo", "2000000", "1000000"),
                filaSinDeuda("CC-2026-00003", "Pendiente de desembolso", "6000000"));
        List<ResumenEstadoPrestamo> resumen = servicio.calcularResumenPorEstadoPrestamos(filas);
        assertEquals(2, resumen.size());
        ResumenEstadoPrestamo pendiente = resumen.get(0);
        assertEquals("Pendiente de desembolso", pendiente.getEstado());
        assertEquals("—", pendiente.getSaldo());
        assertEquals("6.000.000 Gs.", pendiente.getMonto());
        ResumenEstadoPrestamo activo = resumen.get(1);
        assertEquals("Activo", activo.getEstado());
        assertEquals("5.000.000 Gs.", activo.getSaldo());
    }

    // E-9b (pedido de Joel): calcularSaldoCapital tiene que devolver EXACTAMENTE lo mismo que ya
    // prueba E-9 para listarInformePrestamos -- null para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_
    // DESEMBOLSO, para que la lista de Préstamos y la ficha de Estado de cuenta nunca muestren
    // números distintos. tieneDeuda() corta ANTES de tocar la conexión -- se le pasa null a
    // propósito: si algún día alguien rompiera ese orden y la rama "sin deuda" intentara
    // consultar algo, esta prueba explotaría con NullPointerException en vez de pasar igual.

    @Test
    public void testCalcularSaldoCapitalPendienteDesembolsoEsNuloSinTocarLaBase() throws java.sql.SQLException {
        assertNull(servicio.calcularSaldoCapital(null, 1L, EstadoPrestamo.PENDIENTE_DESEMBOLSO));
    }

    @Test
    public void testCalcularSaldoCapitalAnuladoAntesDesembolsoEsNuloSinTocarLaBase() throws java.sql.SQLException {
        assertNull(servicio.calcularSaldoCapital(null, 1L, EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO));
    }

    @Test
    public void testTieneDeudaSoloPendienteYAnuladoQuedanAfuera() {
        assertFalse(ServicioReportes.tieneDeuda(EstadoPrestamo.PENDIENTE_DESEMBOLSO));
        assertFalse(ServicioReportes.tieneDeuda(EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO));
        assertTrue(ServicioReportes.tieneDeuda(EstadoPrestamo.ACTIVO));
        assertTrue(ServicioReportes.tieneDeuda(EstadoPrestamo.EN_MORA));
        assertTrue(ServicioReportes.tieneDeuda(EstadoPrestamo.FINALIZADO));
    }

    // ======================================================================
    // B2 (Bloque 7/E10, pedido de Joel): métodos puros del informe de Solicitudes -- mismo
    // criterio que B1 (filas ya armadas en memoria, nada toca la base).
    // ======================================================================

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud filaSolicitud(String numero, String estado,
            String monto) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud(1L, numero, "01/10/2026",
                "Cliente de Prueba", "1.234.567", new BigDecimal(monto), monto + " Gs.", 12, estado,
                "Oficial de Prueba");
    }

    @Test
    public void testCalcularTotalesSolicitudes() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas = Arrays.asList(
                filaSolicitud("SOL-2026-000001", "Aprobada", "6000000"),
                filaSolicitud("SOL-2026-000002", "Rechazada", "3000000"));
        py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte totales = servicio.calcularTotalesSolicitudes(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals("9.000.000 Gs.", totales.getMontoTotal());
    }

    @Test
    public void testCalcularTotalesSolicitudesSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte totales = servicio.calcularTotalesSolicitudes(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals("0 Gs.", totales.getMontoTotal());
    }

    @Test
    public void testCalcularResumenPorEstadoSolicitudesAgrupaYSuma() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas = Arrays.asList(
                filaSolicitud("SOL-2026-000001", "Aprobada", "6000000"),
                filaSolicitud("SOL-2026-000002", "Aprobada", "2000000"),
                filaSolicitud("SOL-2026-000003", "Rechazada", "3000000"));
        List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud> resumen =
                servicio.calcularResumenPorEstadoSolicitudes(filas);
        // Orden del enum EstadoSolicitud: Aprobada antes que Rechazada, aunque en las filas
        // aparezca después -- mismo criterio que el resumen por estado de B1.
        assertEquals(2, resumen.size());
        py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud aprobada = resumen.get(0);
        assertEquals("Aprobada", aprobada.getEstado());
        assertEquals(2, aprobada.getCantidad());
        assertEquals("8.000.000 Gs.", aprobada.getMonto());
        py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud rechazada = resumen.get(1);
        assertEquals("Rechazada", rechazada.getEstado());
        assertEquals(1, rechazada.getCantidad());
        assertEquals("3.000.000 Gs.", rechazada.getMonto());
    }

    @Test
    public void testCalcularResumenPorEstadoSolicitudesSoloEstadosConFilas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud> filas =
                Arrays.asList(filaSolicitud("SOL-2026-000001", "Cancelada", "1000000"));
        List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud> resumen =
                servicio.calcularResumenPorEstadoSolicitudes(filas);
        assertEquals(1, resumen.size());
        assertEquals("Cancelada", resumen.get(0).getEstado());
    }

    @Test
    public void testCalcularResumenPorEstadoSolicitudesVacio() {
        assertTrue(servicio.calcularResumenPorEstadoSolicitudes(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud>emptyList()).isEmpty());
    }

    // ======================================================================
    // B3 (Bloque 7/E10, pedido de Joel): métodos puros del informe de Aprobaciones y rechazos --
    // mismo criterio que B2, nada toca la base.
    // ======================================================================

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion filaAprobada(String numero, String monto) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion(1L, numero, "Cliente de Prueba",
                new BigDecimal(monto), monto + " Gs.", new BigDecimal(monto), monto + " Gs.", "Aprobada",
                "Gerente de Prueba", "01/09/2026 09:00", "02/09/2026 09:00", 1440, "1 día", "—");
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion filaRechazada(String numero, String monto,
            long minutos, String tiempoFormateado) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion(2L, numero, "Cliente de Prueba",
                new BigDecimal(monto), monto + " Gs.", null, "—", "Rechazada", "Gerente de Prueba",
                "01/09/2026 09:00", "01/09/2026 09:00", minutos, tiempoFormateado, "Capacidad insuficiente");
    }

    /** RF07 (pedido de Joel, 10/10/2026): tercera categoría del informe -- cancelada por el
     * gerente desde la ficha de aprobación (RF06). */
    private static py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion filaCancelada(String numero, String monto) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion(3L, numero, "Cliente de Prueba",
                new BigDecimal(monto), monto + " Gs.", null, "—", "Cancelada", "Gerente de Prueba",
                "01/09/2026 09:00", "01/09/2026 10:00", 60, "1 h", "Desistió");
    }

    @Test
    public void testFormatearDuracionMinutosMenosDeUnaHora() {
        assertEquals("45 min", ServicioReportes.formatearDuracionMinutos(45));
    }

    @Test
    public void testFormatearDuracionMinutosHorasExactas() {
        assertEquals("3 h", ServicioReportes.formatearDuracionMinutos(180));
    }

    @Test
    public void testFormatearDuracionMinutosHorasYMinutos() {
        assertEquals("2 h 45 min", ServicioReportes.formatearDuracionMinutos(165));
    }

    @Test
    public void testFormatearDuracionMinutosUnDiaExacto() {
        assertEquals("1 día", ServicioReportes.formatearDuracionMinutos(1440));
    }

    @Test
    public void testFormatearDuracionMinutosVariosDiasConHoras() {
        assertEquals("2 días 4 h", ServicioReportes.formatearDuracionMinutos(2 * 1440 + 4 * 60));
    }

    @Test
    public void testCalcularTotalesAprobacionesSepararAprobadasYRechazadas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas = Arrays.asList(
                filaAprobada("SOL-2026-000001", "6000000"),
                filaAprobada("SOL-2026-000002", "2000000"),
                filaRechazada("SOL-2026-000003", "3000000", 165, "2 h 45 min"));
        py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones totales = servicio.calcularTotalesAprobaciones(filas);
        assertEquals(2, totales.getCantidadAprobadas());
        assertEquals("8.000.000 Gs.", totales.getMontoAprobadas());
        assertEquals(1, totales.getCantidadRechazadas());
        assertEquals("3.000.000 Gs.", totales.getMontoRechazadas());
        assertEquals(3, totales.getCantidadTotal());
        // Promedio de (1440 + 1440 + 165) / 3 = 1015 min = 16 h 55 min.
        assertEquals("16 h 55 min", totales.getTiempoPromedioRespuesta());
    }

    @Test
    public void testCalcularTotalesAprobacionesSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones totales = servicio.calcularTotalesAprobaciones(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion>emptyList());
        assertEquals(0, totales.getCantidadAprobadas());
        assertEquals("0 Gs.", totales.getMontoAprobadas());
        assertEquals(0, totales.getCantidadRechazadas());
        assertEquals("0 Gs.", totales.getMontoRechazadas());
        assertEquals(0, totales.getCantidadCanceladas());
        assertEquals("0 Gs.", totales.getMontoCanceladas());
        assertEquals("—", totales.getTiempoPromedioRespuesta());
    }

    /** RF07 (pedido de Joel, 10/10/2026): CANCELADA ahora es una tercera categoría separada,
     * contada aparte de aprobadas/rechazadas y sumando el monto SOLICITADO (nunca se aprobó monto). */
    @Test
    public void testCalcularTotalesAprobacionesConCanceladas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas = Arrays.asList(
                filaAprobada("SOL-2026-000001", "6000000"),
                filaRechazada("SOL-2026-000002", "3000000", 165, "2 h 45 min"),
                filaCancelada("SOL-2026-000003", "4000000"));
        py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones totales = servicio.calcularTotalesAprobaciones(filas);
        assertEquals(1, totales.getCantidadAprobadas());
        assertEquals(1, totales.getCantidadRechazadas());
        assertEquals(1, totales.getCantidadCanceladas());
        assertEquals("4.000.000 Gs.", totales.getMontoCanceladas());
        assertEquals(3, totales.getCantidadTotal());
        // Corrección (pedido de Joel, 11/10/2026): el promedio se calcula SOLO con aprobadas y
        // rechazadas -- la cancelada (60 min) NO entra. Promedio de (1440 + 165) / 2 = 802 min.
        assertEquals(ServicioReportes.formatearDuracionMinutos(802), totales.getTiempoPromedioRespuesta());
    }

    /** Corrección (pedido de Joel, 11/10/2026): sin ninguna aprobada ni rechazada (solo
     * canceladas), el promedio tiene que decir "—", no "0 min". */
    @Test
    public void testCalcularTotalesAprobacionesSoloCanceladasPromedioGuion() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion> filas = Arrays.asList(
                filaCancelada("SOL-2026-000001", "4000000"),
                filaCancelada("SOL-2026-000002", "2000000"));
        py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones totales = servicio.calcularTotalesAprobaciones(filas);
        assertEquals(0, totales.getCantidadAprobadas());
        assertEquals(0, totales.getCantidadRechazadas());
        assertEquals(2, totales.getCantidadCanceladas());
        assertEquals("—", totales.getTiempoPromedioRespuesta());
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): métodos puros de los informes de Cobros y Desembolsos --
    // mismo criterio que B1-B3, nada toca la base.
    // ======================================================================

    private static AplicacionPago aplicacion(String capital, String interes, String moratorio, String punitorio,
            String tributos) {
        AplicacionPago aplicacion = new AplicacionPago();
        aplicacion.setMontoCapital(new BigDecimal(capital));
        aplicacion.setMontoInteres(new BigDecimal(interes));
        aplicacion.setMontoMoratorio(new BigDecimal(moratorio));
        aplicacion.setMontoPunitorio(new BigDecimal(punitorio));
        aplicacion.setMontoTributos(new BigDecimal(tributos));
        return aplicacion;
    }

    @Test
    public void testSumarAplicacionesVacio() {
        BigDecimal[] sumas = ServicioReportes.sumarAplicaciones(java.util.Collections.<AplicacionPago>emptyList());
        for (BigDecimal suma : sumas) {
            assertEquals(0, BigDecimal.ZERO.compareTo(suma));
        }
    }

    @Test
    public void testSumarAplicacionesUnaSola() {
        BigDecimal[] sumas = ServicioReportes.sumarAplicaciones(
                Arrays.asList(aplicacion("400000", "60000", "0", "0", "6000")));
        assertEquals(0, new BigDecimal("400000").compareTo(sumas[0]));
        assertEquals(0, new BigDecimal("60000").compareTo(sumas[1]));
        assertEquals(0, new BigDecimal("6000").compareTo(sumas[4]));
    }

    /** Verificación explícita pedida por Joel para B4: los totales del informe de Cobros de un
     * día tienen que coincidir con la "Composición de lo cobrado" del arqueo de esa sesión.
     * AplicacionPagoDAO.sumarPorSesion/sumarPorRangoFechas hacen esa suma con SQL SUM() sobre
     * TODAS las aplicaciones_pago de la sesión/rango de una sola vez; ServicioReportes.
     * listarInformeCobros arma la composición de CADA fila con sumarAplicaciones (una llamada por
     * pago) y calcularTotalesCobros suma esas filas. Sumar primero por pago y después sumar esas
     * filas es matemáticamente lo mismo que sumar todas las aplicaciones de una sola vez (es solo
     * reagrupar la misma suma) -- esta prueba lo demuestra con números concretos: arma dos "pagos"
     * de una sesión ficticia, suma cada uno por separado y los combina (como hace el informe), y
     * por separado suma TODAS sus aplicaciones juntas (como hace sumarPorSesion con un solo SUM()
     * sobre la sesión) -- el resultado tiene que ser idéntico. */
    @Test
    public void testSumarAplicacionesComposicionIgualQueElArqueo() {
        List<AplicacionPago> pago1 = Arrays.asList(
                aplicacion("300000", "40000", "0", "0", "4000"),
                aplicacion("100000", "10000", "5000", "0", "1500"));
        List<AplicacionPago> pago2 = Arrays.asList(
                aplicacion("250000", "35000", "0", "2000", "3700"));

        // Camino del informe de Cobros: por pago, después sumadas.
        BigDecimal[] sumaPago1 = ServicioReportes.sumarAplicaciones(pago1);
        BigDecimal[] sumaPago2 = ServicioReportes.sumarAplicaciones(pago2);
        BigDecimal[] totalPorFilas = new BigDecimal[5];
        for (int i = 0; i < 5; i++) {
            totalPorFilas[i] = sumaPago1[i].add(sumaPago2[i]);
        }

        // Camino del arqueo (sumarPorSesion/sumarPorRangoFechas): todas las aplicaciones de la
        // sesión de una sola vez.
        List<AplicacionPago> todasLasAplicacionesDeLaSesion = new java.util.ArrayList<>();
        todasLasAplicacionesDeLaSesion.addAll(pago1);
        todasLasAplicacionesDeLaSesion.addAll(pago2);
        BigDecimal[] totalDeLaSesion = ServicioReportes.sumarAplicaciones(todasLasAplicacionesDeLaSesion);

        assertEquals(5, totalPorFilas.length);
        for (int i = 0; i < 5; i++) {
            assertEquals("Índice " + i + " de la composición no coincide con el arqueo",
                    0, totalPorFilas[i].compareTo(totalDeLaSesion[i]));
        }
        // Y con los números concretos: capital 300.000+100.000+250.000 = 650.000.
        assertEquals(0, new BigDecimal("650000").compareTo(totalPorFilas[0]));
    }

    @Test
    public void testCalcularTotalesCobrosExcluyeRevertidos() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro> filas = Arrays.asList(
                filaCobro("REC-001", "500000", "80000", false),
                filaCobro("REC-002", "300000", "40000", true));
        py.com.capitalcredito.sgcpc.dto.TotalesCobros totales = servicio.calcularTotalesCobros(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals(1, totales.getCantidadRevertidos());
        // Solo suma el cobro no revertido: capital 500.000, interés 80.000.
        assertEquals("500.000 Gs.", totales.getCapitalTotal());
        assertEquals("80.000 Gs.", totales.getInteresTotal());
    }

    @Test
    public void testCalcularTotalesCobrosSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesCobros totales = servicio.calcularTotalesCobros(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeCobro>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals(0, totales.getCantidadRevertidos());
        assertEquals("0 Gs.", totales.getTotal());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeCobro filaCobro(String recibo, String capital,
            String interes, boolean revertido) {
        BigDecimal capitalBd = new BigDecimal(capital);
        BigDecimal interesBd = new BigDecimal(interes);
        BigDecimal total = capitalBd.add(interesBd);
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeCobro(1L, "01/09/2026", recibo, "Cliente de Prueba",
                "1.234.567", "CC-2026-00001", revertido ? "Cobro normal — Revertido" : "Cobro normal",
                capitalBd, capital + " Gs.", interesBd, interes + " Gs.", BigDecimal.ZERO, "0 Gs.",
                BigDecimal.ZERO, "0 Gs.", BigDecimal.ZERO, "0 Gs.", total, total + " Gs.", "Cajera de Prueba",
                revertido);
    }

    @Test
    public void testCalcularTotalesDesembolsos() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso> filas = Arrays.asList(
                filaDesembolso("6000000"), filaDesembolso("3000000"));
        py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos totales = servicio.calcularTotalesDesembolsos(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals("9.000.000 Gs.", totales.getMontoTotal());
    }

    @Test
    public void testCalcularTotalesDesembolsosSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos totales = servicio.calcularTotalesDesembolsos(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals("0 Gs.", totales.getMontoTotal());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso filaDesembolso(String monto) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso(1L, "01/09/2026", "DES-2026-000001",
                "Cliente de Prueba", "1.234.567", "CC-2026-00001", new BigDecimal(monto), monto + " Gs.",
                "Cajera de Prueba");
    }

    // B5 (pedido de Joel): sobrantes y faltantes se suman POR SEPARADO, nunca netos entre sí --
    // una sesión que sobra 15.000 y otra que falta 8.000 no se "cancelan": el informe tiene que
    // mostrar ambos totales, no una diferencia neta de 7.000.
    @Test
    public void testCalcularTotalesCajaSobrantesYFaltantesPorSeparado() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas = Arrays.asList(
                filaCaja("15000", true), filaCaja("-8000", false), filaCaja("0", true));
        py.com.capitalcredito.sgcpc.dto.TotalesCaja totales = servicio.calcularTotalesCaja(filas);
        assertEquals(3, totales.getCantidadSesiones());
        assertEquals("15.000 Gs.", totales.getSumaSobrantes());
        assertEquals("8.000 Gs.", totales.getSumaFaltantes());
    }

    @Test
    public void testCalcularTotalesCajaSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesCaja totales = servicio.calcularTotalesCaja(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja>emptyList());
        assertEquals(0, totales.getCantidadSesiones());
        assertEquals("0 Gs.", totales.getSumaSobrantes());
        assertEquals("0 Gs.", totales.getSumaFaltantes());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeCaja filaCaja(String diferencia, boolean revisada) {
        BigDecimal diferenciaBd = new BigDecimal(diferencia);
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeCaja(1L, "Cajera de Prueba",
                "01/10/2026 08:00", "01/10/2026 17:30", new BigDecimal("2000000"), "2.000.000 Gs.",
                new BigDecimal("2000000").add(diferenciaBd), "2.000.000 Gs.", new BigDecimal("2000000"),
                "2.000.000 Gs.", diferenciaBd, "— Gs.", revisada);
    }

    // Bloque C, tanda 1 (pedido de Joel): subtotales por tramo de Morosidad -- cantidad, capital
    // vencido y saldo (ya NO el monto exigible completo con interés/mora/punitorio/IVA), para que
    // el total general se pueda cruzar contra el saldo "En mora" de /prestamos.
    @Test
    public void testCalcularSubtotalesPorTramoMorosidad() {
        List<FilaMora> filas = Arrays.asList(
                filaMora(10, "300000", "1200000"), // tramo 1-30
                filaMora(45, "100000", "900000"),  // tramo 31-60
                filaMora(20, "250000", "1500000")); // tramo 1-30
        List<py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad> subtotales =
                servicio.calcularSubtotalesPorTramo(filas);
        assertEquals(2, subtotales.size());
        py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad tramo1a30 = subtotales.get(0);
        assertEquals("1–30", tramo1a30.getTramo());
        assertEquals(2, tramo1a30.getCantidad());
        assertEquals("550.000 Gs.", tramo1a30.getCapitalVencido());
        assertEquals("2.700.000 Gs.", tramo1a30.getSaldo());
    }

    @Test
    public void testCalcularTotalGeneralMorosidad() {
        List<FilaMora> filas = Arrays.asList(
                filaMora(10, "300000", "1200000"),
                filaMora(45, "100000", "900000"));
        py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad total = servicio.calcularTotalGeneralMorosidad(filas);
        assertEquals("Total general", total.getTramo());
        assertEquals(2, total.getCantidad());
        assertEquals("400.000 Gs.", total.getCapitalVencido());
        assertEquals("2.100.000 Gs.", total.getSaldo());
    }

    private static FilaMora filaMora(int diasAtraso, String capitalVencido, String saldo) {
        return new FilaMora(1L, "CC-2026-00001", "Cliente de Prueba", "Cliente", "de Prueba", "0981123456", "1234567",
                diasAtraso, 1, 1, 12, "01/09/2026", "999.999 Gs.", new BigDecimal("999999"),
                false, null, null, false, null, null, null, false,
                new BigDecimal(capitalVencido), new BigDecimal(saldo));
    }

    // Bloque C, tanda 1 (pedido de Joel): totales y resumen al día/en mora de Cartera -- pura,
    // sobre las filas ya armadas (antes volvía a consultar la base, con riesgo de diverger de lo
    // que se ve en pantalla).
    @Test
    public void testCalcularTotalesCartera() {
        List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas = Arrays.asList(
                filaCartera("CC-1", "Activo", "1000000", "200000"),
                filaCartera("CC-2", "En mora", "500000", "50000"));
        py.com.capitalcredito.sgcpc.dto.TotalesCartera totales = servicio.calcularTotalesCartera(filas);
        assertEquals(2, totales.getCantidadPrestamos());
        assertEquals("1.500.000 Gs.", totales.getCapitalPendiente());
        assertEquals("250.000 Gs.", totales.getInteresFuturo());
    }

    @Test
    public void testCalcularResumenPorEstadoCarteraAlDiaYEnMora() {
        List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas = Arrays.asList(
                filaCartera("CC-1", "Activo", "1000000", "200000"),
                filaCartera("CC-2", "Activo", "300000", "10000"),
                filaCartera("CC-3", "En mora", "500000", "50000"));
        List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera> resumen =
                servicio.calcularResumenPorEstadoCartera(filas);
        assertEquals(2, resumen.size());
        assertEquals("Activo", resumen.get(0).getEstado());
        assertEquals(2, resumen.get(0).getCantidad());
        assertEquals("1.300.000 Gs.", resumen.get(0).getSaldo());
        assertEquals("En mora", resumen.get(1).getEstado());
        assertEquals(1, resumen.get(1).getCantidad());
        assertEquals("500.000 Gs.", resumen.get(1).getSaldo());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaCartera filaCartera(String identificadorOperacion,
            String estado, String capitalPendiente, String interesFuturo) {
        return new py.com.capitalcredito.sgcpc.dto.FilaCartera(identificadorOperacion, "Cliente de Prueba",
                "9.999.999 Gs.", capitalPendiente + " Gs.", new BigDecimal(capitalPendiente),
                interesFuturo + " Gs.", new BigDecimal(interesFuturo), estado);
    }

    // Bloque C, tanda 1 (pedido de Joel): el total de Incobrables pasa a ser el SALDO (saldoCrudo),
    // no el monto exigible completo (capital + interés + mora + punitorio + IVA de las cuotas
    // vencidas, montoExigibleCrudo) -- distintos montos a propósito, para probar que no se mezclan.
    @Test
    public void testCalcularTotalesIncobrablesUsaSaldoNoMontoExigible() {
        List<FilaMora> filas = Arrays.asList(filaMora(200, "300000", "1200000"), filaMora(250, "100000", "900000"));
        py.com.capitalcredito.sgcpc.dto.TotalesIncobrables totales = servicio.calcularTotalesIncobrables(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals("2.100.000 Gs.", totales.getSaldo());
    }

    // Bloque C, tanda 1 (pedido de Joel): totales de Vencimientos desglosados en capital/interés/
    // IVA/total -- extraído a una función pura (sumarTotalesVencimientos) justamente para poder
    // probarlo sin base, mismo criterio que sumarSaldoCapital/sumarAplicaciones.
    @Test
    public void testSumarTotalesVencimientos() {
        List<Cuota> cuotas = Arrays.asList(
                cuotaVencimiento("500000", "80000", "16000"), cuotaVencimiento("300000", "50000", "10000"));
        TotalesVencimientos totales = ServicioReportes.sumarTotalesVencimientos(cuotas);
        assertEquals(2, totales.getCantidadCuotas());
        assertEquals("800.000 Gs.", totales.getCapitalTotal());
        assertEquals("130.000 Gs.", totales.getInteresTotal());
        assertEquals("26.000 Gs.", totales.getIvaTotal());
        assertEquals("956.000 Gs.", totales.getMontoTotal());
    }

    @Test
    public void testSumarTotalesVencimientosSinCuotas() {
        TotalesVencimientos totales = ServicioReportes.sumarTotalesVencimientos(java.util.Collections.<Cuota>emptyList());
        assertEquals(0, totales.getCantidadCuotas());
        assertEquals("0 Gs.", totales.getCapitalTotal());
        assertEquals("0 Gs.", totales.getMontoTotal());
    }

    // Bloque C, tanda 2 (pedido de Joel): IVA del mes ahora incluye las facturas ANULADAS (antes
    // quedaban completamente afuera) pero calcularTotalesIva las salta -- probado con montos
    // deliberadamente distintos para confirmar que la anulada no entra en el total.
    @Test
    public void testCalcularTotalesIvaExcluyeAnuladas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaIva> filas = Arrays.asList(
                filaIva("500000", "50000", "550000", false),
                filaIva("300000", "30000", "330000", true));
        py.com.capitalcredito.sgcpc.dto.TotalesIva totales = servicio.calcularTotalesIva(filas);
        assertEquals(1, totales.getCantidadFacturas());
        assertEquals("500.000 Gs.", totales.getGravada10());
        assertEquals("50.000 Gs.", totales.getIva10());
        assertEquals("550.000 Gs.", totales.getTotal());
    }

    @Test
    public void testCalcularTotalesIvaSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesIva totales = servicio.calcularTotalesIva(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaIva>emptyList());
        assertEquals(0, totales.getCantidadFacturas());
        assertEquals("0 Gs.", totales.getTotal());
    }

    // Bloque C, tanda 2 (pedido de Joel): total de montos anulados del mes.
    @Test
    public void testCalcularTotalesFacturasAnuladas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas = Arrays.asList(
                filaFacturaAnulada("500000"), filaFacturaAnulada("300000"));
        py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas totales = servicio.calcularTotalesFacturasAnuladas(filas);
        assertEquals(2, totales.getCantidad());
        assertEquals("800.000 Gs.", totales.getMontoTotal());
    }

    @Test
    public void testCalcularTotalesFacturasAnuladasSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas totales = servicio.calcularTotalesFacturasAnuladas(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals("0 Gs.", totales.getMontoTotal());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada filaFacturaAnulada(String total) {
        return new py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada(1L, "001-001-0000001", "01/10/2026",
                "Cliente de Prueba", "1.234.567", total + " Gs.", new BigDecimal(total), "CAN-2026-000001",
                "12345678", "02/10/2026", "DNIT y caja", "Carlos Gerente");
    }

    // E-12 (pedido de Joel): totales de Cierres documentales ahora son tres números -- cancelados,
    // devueltos y pendientes de devolución -- sobre las filas que estén mostrándose (si hay un
    // filtro de estado del pagaré aplicado, estos reflejan ESE subconjunto, no el total real).
    @Test
    public void testCalcularTotalesCierresDocumentales() {
        List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas = Arrays.asList(
                filaCierreDocumental(1L, "CC-2026-00001", true),
                filaCierreDocumental(2L, "CC-2026-00002", false));
        py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales totales =
                servicio.calcularTotalesCierresDocumentales(filas);
        assertEquals(2, totales.getCantidadCancelados());
        assertEquals(1, totales.getCantidadDevueltos());
        assertEquals(1, totales.getCantidadPendientes());
    }

    @Test
    public void testCalcularTotalesCierresDocumentalesSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales totales = servicio.calcularTotalesCierresDocumentales(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte>emptyList());
        assertEquals(0, totales.getCantidadCancelados());
        assertEquals(0, totales.getCantidadDevueltos());
        assertEquals(0, totales.getCantidadPendientes());
    }

    // E-12 (pedido de Joel, caso de Jorge CC-2025-00001): un préstamo cancelado cuyo
    // estado_cierre_documental nunca llegó a COMPLETO cuenta como "pendiente", no se excluye del
    // informe -- getEstadoPagare() tiene que decir exactamente "Pendiente de devolución" y los
    // campos de devolución quedan en "—", nunca null ni vacíos.
    @Test
    public void testFilaCierreDocumentalPendienteMuestraGuionesYEtiqueta() {
        py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte fila = filaCierreDocumental(1L,
                "CC-2025-00001", false);
        assertEquals("Pendiente de devolución", fila.getEstadoPagare());
        assertEquals("—", fila.getNumeroConstanciaDevolucion());
        assertEquals("—", fila.getFechaDevolucionPagare());
        assertEquals("—", fila.getEntregadoPorNombre());
    }

    @Test
    public void testFilaCierreDocumentalDevueltoMuestraEtiqueta() {
        py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte fila = filaCierreDocumental(1L,
                "CC-2026-00001", true);
        assertEquals("Devuelto", fila.getEstadoPagare());
        assertEquals("DEV-2026-000001", fila.getNumeroConstanciaDevolucion());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte filaCierreDocumental(long idPrestamo,
            String identificadorOperacion, boolean pagareDevuelto) {
        return new py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte(idPrestamo, identificadorOperacion,
                "Cliente de Prueba", "01/09/2026", "CAN-2026-000001", pagareDevuelto,
                pagareDevuelto ? "DEV-2026-000001" : "—", pagareDevuelto ? "02/09/2026" : "—",
                pagareDevuelto ? "Carlos Gerente" : "—");
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaIva filaIva(String gravada10, String iva10, String total,
            boolean anulada) {
        return new py.com.capitalcredito.sgcpc.dto.FilaIva("001-001-0000001", "01/10/2026", "Cliente de Prueba",
                "1.234.567", "0 Gs.", BigDecimal.ZERO, gravada10 + " Gs.", new BigDecimal(gravada10),
                iva10 + " Gs.", new BigDecimal(iva10), total + " Gs.", new BigDecimal(total), anulada);
    }

    private static Cuota cuotaVencimiento(String capital, String interes, String iva) {
        Cuota cuota = new Cuota();
        cuota.setCapitalProgramado(new BigDecimal(capital));
        cuota.setInteresProgramado(new BigDecimal(interes));
        cuota.setTributosProgramados(new BigDecimal(iva));
        cuota.setTotalProgramado(new BigDecimal(capital).add(new BigDecimal(interes)).add(new BigDecimal(iva)));
        return cuota;
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): totales de Gestiones de cobranza -- cantidad por tipo,
    // promesas hechas, cumplidas y vencidas sin cumplir. Pura: sobre filas ya armadas (con los tres
    // flags de promesa ya resueltos por listarGestiones, que sí necesita la fecha de hoy).
    // ======================================================================

    @Test
    public void testCalcularTotalesGestionesPorTipoYPromesas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas = Arrays.asList(
                filaGestion("Llamada", false, false, false), // sin promesa
                filaGestion("Llamada", true, true, false), // promesa cumplida
                filaGestion("Visita al local", true, false, true), // promesa vencida sin cumplir
                filaGestion("Mensaje", true, false, false)); // promesa, ni cumplida ni vencida (pendiente aún)
        TotalesGestiones totales = servicio.calcularTotalesGestiones(filas);
        assertEquals(4, totales.getCantidadTotal());
        assertEquals(Integer.valueOf(2), totales.getCantidadPorTipo().get("Llamada"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorTipo().get("Visita al local"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorTipo().get("Mensaje"));
        assertEquals(3, totales.getCantidadPromesasHechas());
        assertEquals(1, totales.getCantidadCumplidas());
        assertEquals(1, totales.getCantidadVencidasSinCumplir());
        // C-3b (pedido de Joel): el "Mensaje" de arriba tiene promesa, no está cumplida y no está
        // vencida -- es exactamente el caso "pendiente (aún no vence)".
        assertEquals(1, totales.getCantidadPendientes());
    }

    @Test
    public void testCalcularTotalesGestionesSinFilas() {
        TotalesGestiones totales = servicio.calcularTotalesGestiones(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte>emptyList());
        assertEquals(0, totales.getCantidadTotal());
        assertTrue(totales.getCantidadPorTipo().isEmpty());
        assertEquals(0, totales.getCantidadPromesasHechas());
        assertEquals(0, totales.getCantidadCumplidas());
        assertEquals(0, totales.getCantidadVencidasSinCumplir());
        assertEquals(0, totales.getCantidadPendientes());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaGestionReporte filaGestion(String tipoEtiqueta,
            boolean tienePromesa, boolean cumplida, boolean vencidaSinCumplir) {
        return new py.com.capitalcredito.sgcpc.dto.FilaGestionReporte("01/10/2026 10:00", 1L, "CC-2026-00001",
                "Cliente de Prueba", tipoEtiqueta, "Contactado", tienePromesa,
                tienePromesa ? "05/10/2026 — 500.000 Gs." : "—", cumplida, vencidaSinCumplir, "Cajera de Prueba");
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): total de montos revertidos de Reversiones -- solo las
    // AUTORIZADA, nunca las SOLICITADA (pendientes) ni las RECHAZADA (nunca movieron plata).
    // ======================================================================

    @Test
    public void testCalcularTotalesReversionesSoloSumaAutorizadas() {
        List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas = Arrays.asList(
                filaReversion("500000", "Autorizada", true),
                filaReversion("100000", "Autorizada", true),
                filaReversion("300000", "Solicitada", false),
                filaReversion("200000", "Rechazada", false),
                filaReversion("200000", "Rechazada", false));
        py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales = servicio.calcularTotalesReversiones(filas);
        assertEquals(5, totales.getCantidad());
        assertEquals(2, totales.getCantidadAutorizadas());
        assertEquals(2, totales.getCantidadRechazadas());
        assertEquals(1, totales.getCantidadSolicitadas());
        assertEquals("600.000 Gs.", totales.getMontoTotalRevertido());
    }

    @Test
    public void testCalcularTotalesReversionesSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales = servicio.calcularTotalesReversiones(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte>emptyList());
        assertEquals(0, totales.getCantidad());
        assertEquals(0, totales.getCantidadAutorizadas());
        assertEquals(0, totales.getCantidadRechazadas());
        assertEquals(0, totales.getCantidadSolicitadas());
        assertEquals("0 Gs.", totales.getMontoTotalRevertido());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaReversionReporte filaReversion(String monto, String estado,
            boolean autorizada) {
        return new py.com.capitalcredito.sgcpc.dto.FilaReversionReporte("01/10/2026", 1L, "REC-2026-000001",
                "CC-2026-00001", "Cliente de Prueba", monto + " Gs.", new BigDecimal(monto), "Motivo de prueba",
                estado, autorizada, "Cajera de Prueba", autorizada ? "Carlos Gerente" : "—", "—");
    }

    // E-15 (pedido de Joel, C-3b): los totales por tipo de operación de Auditoría pasaron a ser
    // una consulta GROUP BY (AuditoriaDAO.contarPorOperacion, vía ServicioReportes.
    // contarTotalesPorOperacion) -- ya no una función pura sobre una lista en memoria, así que no
    // se prueba acá con JUnit (toca la base, mismo criterio que el resto de los métodos con
    // Connection). Ver ServicioInformesTest.testConstruirTituloTotalesAuditoria* para el texto
    // del título, que sí es puro.

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): total y subtotales por ciudad de Clientes nuevos -- pura,
    // sobre las filas ya armadas.
    // ======================================================================

    @Test
    public void testCalcularTotalesClientesNuevosPorCiudad() {
        List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas = Arrays.asList(
                filaClienteNuevo("Luque", "Activo"), filaClienteNuevo("Luque", "—"),
                filaClienteNuevo("San Lorenzo", "Finalizado"));
        py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos totales = servicio.calcularTotalesClientesNuevos(filas);
        assertEquals(3, totales.getCantidad());
        assertEquals(Integer.valueOf(2), totales.getCantidadPorCiudad().get("Luque"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorCiudad().get("San Lorenzo"));
    }

    @Test
    public void testCalcularTotalesClientesNuevosSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos totales = servicio.calcularTotalesClientesNuevos(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo>emptyList());
        assertEquals(0, totales.getCantidad());
        assertTrue(totales.getCantidadPorCiudad().isEmpty());
    }

    // C-4 paso 1 (pedido de Joel): la columna "Préstamo" del informe de Clientes nuevos muestra el
    // estado real del último préstamo (texto ya resuelto por ServicioReportes.
    // listarClientesNuevosPorRango, que sí toca la base -- acá solo se confirma que la fila
    // devuelve exactamente lo que recibió, sin inventar ningún texto propio).
    @Test
    public void testFilaClienteNuevoDevuelveElEstadoDelUltimoPrestamoTalCual() {
        assertEquals("Activo", filaClienteNuevo("Luque", "Activo").getEstadoUltimoPrestamo());
        assertEquals("En mora", filaClienteNuevo("Luque", "En mora").getEstadoUltimoPrestamo());
        assertEquals("Pendiente de desembolso", filaClienteNuevo("Luque", "Pendiente de desembolso").getEstadoUltimoPrestamo());
        assertEquals("Finalizado", filaClienteNuevo("Luque", "Finalizado").getEstadoUltimoPrestamo());
        assertEquals("—", filaClienteNuevo("Luque", "—").getEstadoUltimoPrestamo());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo filaClienteNuevo(String ciudad,
            String estadoUltimoPrestamo) {
        return new py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo(1L, "1.234.567", "Cliente de Prueba",
                "01/10/2026", "0981123456", ciudad, "Comerciante / negocio propio", estadoUltimoPrestamo);
    }

    // ======================================================================
    // C-4.2 (pedido de Joel): Usuarios y accesos -- pura, sobre la misma List<Usuario> que ya arma
    // ServicioUsuarios.listar; nunca toca la base. Nunca incluye el hash de la contraseña.
    // ======================================================================

    @Test
    public void testConstruirFilasInformeUsuariosConUltimoAcceso() {
        py.com.capitalcredito.sgcpc.modelos.Usuario usuario = usuarioDePrueba("mgonzalez", "María", "González",
                "1234567", py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario.CAJERO,
                py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta.ACTIVA, false,
                java.time.LocalDateTime.of(2026, 10, 4, 15, 30));
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas =
                servicio.construirFilasInformeUsuarios(Arrays.asList(usuario));
        assertEquals(1, filas.size());
        py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario fila = filas.get(0);
        assertEquals("mgonzalez", fila.getNombreUsuario());
        assertEquals("María González", fila.getNombreCompleto());
        assertEquals("1.234.567", fila.getCedula());
        assertEquals("Cajera", fila.getPerfil());
        assertEquals("Activa", fila.getEstado());
        assertEquals("No", fila.getDebeCambiarContrasena());
        assertEquals("04/10/2026 15:30", fila.getUltimoAcceso());
    }

    // Caso real que motivó el pedido: un usuario que nunca entró -- "Nunca", no una fecha vacía
    // ni un error.
    @Test
    public void testConstruirFilasInformeUsuariosNuncaIngreso() {
        py.com.capitalcredito.sgcpc.modelos.Usuario usuario = usuarioDePrueba("nuevo", "Usuario", "Nuevo",
                "7654321", py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario.OFICIAL_CREDITO,
                py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta.ACTIVA, true, null);
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas =
                servicio.construirFilasInformeUsuarios(Arrays.asList(usuario));
        assertEquals("Nunca", filas.get(0).getUltimoAcceso());
        assertEquals("Sí", filas.get(0).getDebeCambiarContrasena());
    }

    @Test
    public void testCalcularTotalesUsuariosPorPerfilYEstado() {
        List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas = Arrays.asList(
                filaInformeUsuario("Cajera", "Activa"), filaInformeUsuario("Cajera", "Bloqueada"),
                filaInformeUsuario("Gerente", "Activa"));
        py.com.capitalcredito.sgcpc.dto.TotalesUsuarios totales = servicio.calcularTotalesUsuarios(filas);
        assertEquals(3, totales.getCantidadTotal());
        assertEquals(Integer.valueOf(2), totales.getCantidadPorPerfil().get("Cajera"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorPerfil().get("Gerente"));
        assertEquals(Integer.valueOf(2), totales.getCantidadPorEstado().get("Activa"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorEstado().get("Bloqueada"));
    }

    @Test
    public void testCalcularTotalesUsuariosSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesUsuarios totales = servicio.calcularTotalesUsuarios(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario>emptyList());
        assertEquals(0, totales.getCantidadTotal());
        assertTrue(totales.getCantidadPorPerfil().isEmpty());
        assertTrue(totales.getCantidadPorEstado().isEmpty());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario filaInformeUsuario(String perfil, String estado) {
        return new py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario("usuario", "Usuario de Prueba", "1.234.567",
                perfil, estado, "No", "Nunca");
    }

    private static py.com.capitalcredito.sgcpc.modelos.Usuario usuarioDePrueba(String nombreUsuario, String nombres,
            String apellidos, String cedula, py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario perfil,
            py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta estado, boolean debeCambiarContrasena,
            java.time.LocalDateTime fechaUltimoAcceso) {
        py.com.capitalcredito.sgcpc.modelos.Usuario usuario = new py.com.capitalcredito.sgcpc.modelos.Usuario();
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        usuario.setCedulaIdentidad(cedula);
        usuario.setPerfilUsuario(perfil);
        usuario.setEstadoCuenta(estado);
        usuario.setDebeCambiarContrasena(debeCambiarContrasena);
        usuario.setFechaUltimoAcceso(fechaUltimoAcceso);
        return usuario;
    }

    // ======================================================================
    // C-4.3 (pedido de Joel): Historial de parámetros -- pura, sobre FilaHistorialParametroCrudo
    // tal como la arma ParametroDAO.listarHistorial; nunca toca la base. Solo lectura: no cambia
    // ningún parámetro.
    // ======================================================================

    @Test
    public void testConstruirFilasHistorialParametrosFormateaPorTipoYMarcaPrimeraVigencia() {
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo cruda =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.monto_minimo",
                        "Monto mínimo de crédito", null, "500000",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.ENTERO,
                        java.time.LocalDate.of(2026, 1, 1), null, "Joel Gerente",
                        java.time.LocalDateTime.of(2026, 1, 1, 8, 0), null);
        List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas =
                servicio.construirFilasHistorialParametros(Arrays.asList(cruda));
        assertEquals(1, filas.size());
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila = filas.get(0);
        assertEquals("credito.monto_minimo", fila.getClaveParametro());
        assertEquals("Monto mínimo de crédito", fila.getParametro());
        assertEquals("—", fila.getValorAnterior());
        assertEquals("500.000 Gs.", fila.getValorNuevo());
        assertEquals("01/01/2026", fila.getVigenteDesde());
        assertEquals("Vigente", fila.getVigenteHasta());
        assertEquals("Joel Gerente", fila.getModificadoPor());
        assertEquals("01/01/2026 08:00", fila.getFechaModificacion());
        assertEquals("—", fila.getObservacion());
    }

    @Test
    public void testConstruirFilasHistorialParametrosVigenciaCerradaYObservacionPropia() {
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo cruda =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.porcentaje_punitorio",
                        "Porcentaje punitorio", "0.04", "0.05",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.DECIMAL,
                        java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 10, 4), "María Cajera",
                        java.time.LocalDateTime.of(2026, 3, 1, 9, 0), "Ajuste según circular del BCP.");
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila =
                servicio.construirFilasHistorialParametros(Arrays.asList(cruda)).get(0);
        assertEquals("credito.porcentaje_punitorio", fila.getClaveParametro());
        assertEquals("4 %", fila.getValorAnterior());
        assertEquals("5 %", fila.getValorNuevo());
        assertEquals("04/10/2026", fila.getVigenteHasta());
        assertEquals("Ajuste según circular del BCP.", fila.getObservacion());
    }

    /** RF39 (pantalla rediseñada, pedido de Joel): el orden cronológico descendente lo pone
     * ParametroDAO.listarHistorial (ORDER BY fecha_modificacion DESC, verificado leyendo la
     * consulta -- no se puede probar con JUnit sin tocar la base, REGLAS_DESARROLLO.md §7). Esta prueba
     * confirma la otra mitad, puramente en Java: construirFilasHistorialParametros nunca reordena
     * lo que ya le llega -- si el orden de entrada viniera "roto" (por un cambio futuro en el
     * DAO), esta prueba lo detectaría. */
    @Test
    public void testConstruirFilasHistorialParametrosConservaElOrdenDeEntrada() {
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo masReciente =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.monto_minimo",
                        "Monto mínimo de crédito", "500000", "600000",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.ENTERO,
                        java.time.LocalDate.of(2026, 10, 7), null, "Joel Gerente",
                        java.time.LocalDateTime.of(2026, 10, 7, 10, 0), null);
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo masVieja =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.monto_minimo",
                        "Monto mínimo de crédito", null, "500000",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.ENTERO,
                        java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 10, 7), "Joel Gerente",
                        java.time.LocalDateTime.of(2026, 1, 1, 8, 0), null);
        List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas =
                servicio.construirFilasHistorialParametros(Arrays.asList(masReciente, masVieja));
        assertEquals("07/10/2026 10:00", filas.get(0).getFechaModificacion());
        assertEquals("01/01/2026 08:00", filas.get(1).getFechaModificacion());
    }

    @Test
    public void testCalcularTotalesHistorialParametrosPorParametro() {
        List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas = Arrays.asList(
                filaHistorialParametro("mora.tasa_diaria", "Tasa de mora diaria"),
                filaHistorialParametro("mora.tasa_diaria", "Tasa de mora diaria"),
                filaHistorialParametro("credito.monto_minimo", "Monto mínimo de crédito"));
        py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros totales =
                servicio.calcularTotalesHistorialParametros(filas);
        assertEquals(3, totales.getCantidadTotal());
        assertEquals(2, totales.getCantidadParametrosDistintos());
        assertEquals(Integer.valueOf(2), totales.getCantidadPorParametro().get("Tasa de mora diaria"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorParametro().get("Monto mínimo de crédito"));
    }

    /** RF39 (pantalla rediseñada): la cantidad de parámetros distintos se cuenta por CLAVE (nunca
     * cambia), no por nombre (sí puede cambiar con el tiempo, D-151) -- dos filas con la misma
     * clave pero nombres distintos (ej. una vieja con el nombre congelado de antes de una
     * corrección de texto) cuentan como UN solo parámetro modificado, no dos. */
    @Test
    public void testCalcularTotalesHistorialParametrosCuentaPorClaveNoPorNombre() {
        List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas = Arrays.asList(
                filaHistorialParametro("formalizacion.dias_validez_documentos",
                        "Días de vigencia de los documentos generados antes de exigir regenerarlos (a usar desde E6)"),
                filaHistorialParametro("formalizacion.dias_validez_documentos",
                        "Días de vigencia de los documentos generados antes de exigir regenerarlos"));
        py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros totales =
                servicio.calcularTotalesHistorialParametros(filas);
        assertEquals(2, totales.getCantidadTotal());
        assertEquals(1, totales.getCantidadParametrosDistintos());
    }

    @Test
    public void testCalcularTotalesHistorialParametrosSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros totales = servicio.calcularTotalesHistorialParametros(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro>emptyList());
        assertEquals(0, totales.getCantidadTotal());
        assertEquals(0, totales.getCantidadParametrosDistintos());
        assertTrue(totales.getCantidadPorParametro().isEmpty());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro filaHistorialParametro(String claveParametro,
            String parametro) {
        return new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro(parametro, "—", "1", "01/01/2026",
                "Vigente", "Joel Gerente", "01/01/2026 08:00", "—", claveParametro, "—", "1");
    }

    // --- "valorMostrado" (V1/RF39, pedido de Joel): comoListaConEspacios solo para TEXTO,
    // CSV (getValorAnterior/getValorNuevo) siempre con el valor original, sin tocar ---

    @Test
    public void testConstruirFilasHistorialParametrosEnListaDeTextoAgregaEspaciosSoloEnMostrado() {
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo cruda =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.departamentos_habilitados",
                        "Departamentos habilitados para otorgar crédito", "Asunción", "Asunción,Central",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.TEXTO,
                        java.time.LocalDate.of(2026, 10, 7), null, "Joel Gerente",
                        java.time.LocalDateTime.of(2026, 10, 7, 9, 0), null);
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila =
                servicio.construirFilasHistorialParametros(Arrays.asList(cruda)).get(0);
        // "Mostrado" (pantalla del historial y PDF): con el espacio.
        assertEquals("Asunción, Central", fila.getValorNuevoMostrado());
        // El que usa el CSV (ControladorParametrosServlet.exportarHistorialCsv): sin tocar.
        assertEquals("Asunción,Central", fila.getValorNuevo());
    }

    @Test
    public void testConstruirFilasHistorialParametrosEnDecimalNoAgregaEspacios() {
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo cruda =
                new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo("credito.porcentaje_punitorio",
                        "Porcentaje punitorio", "0.04", "0.273900",
                        py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro.DECIMAL,
                        java.time.LocalDate.of(2026, 10, 7), null, "Joel Gerente",
                        java.time.LocalDateTime.of(2026, 10, 7, 9, 0), null);
        py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro fila =
                servicio.construirFilasHistorialParametros(Arrays.asList(cruda)).get(0);
        // "27,39 %" tiene coma decimal -- nunca se toca, ni en el valor "para mostrar".
        assertEquals("27,39 %", fila.getValorNuevoMostrado());
        assertEquals("27,39 %", fila.getValorNuevo());
    }

    // ======================================================================
    // C-4.4 (pedido de Joel): Consultas al buró -- pura, sobre FilaConsultaBuroCrudo tal como la
    // arma ConsultaCrediticiaDAO.listarParaInforme; nunca toca la base. Solo lectura: no cambia
    // ninguna consulta ni consentimiento.
    // ======================================================================

    @Test
    public void testConstruirFilasConsultasBuroConClasificacionYAutorizacionVigente() {
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo cruda = new py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo(
                java.time.LocalDateTime.of(2026, 10, 4, 15, 52), 7L, "Andrea Benegas", "1234567", "CC-2026-00009",
                "B", "María Cajera", Boolean.TRUE);
        List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas = servicio.construirFilasConsultasBuro(Arrays.asList(cruda), null);
        assertEquals(1, filas.size());
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro fila = filas.get(0);
        assertEquals(7L, fila.getIdPersona());
        assertEquals("04/10/2026 15:52", fila.getFecha());
        assertEquals("Andrea Benegas", fila.getCliente());
        assertEquals("1.234.567", fila.getCedula());
        assertEquals("CC-2026-00009", fila.getSolicitud());
        assertEquals("B", fila.getResultado());
        assertEquals("María Cajera", fila.getQuienConsulto());
        assertEquals("Sí", fila.getAutorizacionVigente());
    }

    // Casos reales en los datos de prueba: 105 de 124 consultas no tienen clasificación cargada
    // (texto libre o vacío), y sin solicitud vinculada todavía (D-39: id_solicitud es NULL hasta E4).
    @Test
    public void testConstruirFilasConsultasBuroSinClasificacionNiSolicitudNiAutorizacion() {
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo cruda = new py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo(
                java.time.LocalDateTime.of(2025, 11, 6, 8, 20), 3L, "Patricia López", "7654321", null, "",
                "Joel Gerente", null);
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro fila =
                servicio.construirFilasConsultasBuro(Arrays.asList(cruda), null).get(0);
        assertEquals("—", fila.getSolicitud());
        assertEquals("Sin clasificación", fila.getResultado());
        // autorizacionVigenteEnEseMomento == null: no hay valor histórico del parámetro para esa
        // fecha (el caso real: consultas de 2025 anteriores a la primera vigencia registrada de
        // "buro.dias_vigencia_consentimiento", que arranca el 2026-09-30).
        assertEquals("(sin dato)", fila.getAutorizacionVigente());
    }

    @Test
    public void testConstruirFilasConsultasBuroAutorizacionVencida() {
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo cruda = new py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo(
                java.time.LocalDateTime.of(2026, 10, 4, 10, 0), 9L, "Cliente de Prueba", "9999999", null, null,
                "Joel Gerente", Boolean.FALSE);
        py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro fila =
                servicio.construirFilasConsultasBuro(Arrays.asList(cruda), null).get(0);
        assertEquals("No", fila.getAutorizacionVigente());
    }

    @Test
    public void testCalcularTotalesConsultasBuroPorResultadoYClientesDistintos() {
        List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas = Arrays.asList(
                filaConsultaBuro(7L, "B"), filaConsultaBuro(7L, "B"), filaConsultaBuro(3L, "Sin clasificación"));
        py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro totales = servicio.calcularTotalesConsultasBuro(filas);
        assertEquals(3, totales.getCantidadTotal());
        assertEquals(Integer.valueOf(2), totales.getCantidadPorResultado().get("B"));
        assertEquals(Integer.valueOf(1), totales.getCantidadPorResultado().get("Sin clasificación"));
        // Cliente 7 aparece dos veces (dos consultas), cliente 3 una vez -- 2 clientes distintos, no 3.
        assertEquals(2, totales.getCantidadClientesDistintos());
    }

    @Test
    public void testCalcularTotalesConsultasBuroSinFilas() {
        py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro totales = servicio.calcularTotalesConsultasBuro(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro>emptyList());
        assertEquals(0, totales.getCantidadTotal());
        assertTrue(totales.getCantidadPorResultado().isEmpty());
        assertEquals(0, totales.getCantidadClientesDistintos());
    }

    private static py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro filaConsultaBuro(long idPersona, String resultado) {
        return new py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro(idPersona, "04/10/2026 10:00", "Cliente de Prueba",
                "1.234.567", "—", resultado, "Joel Gerente", "Sí");
    }

    // ======================================================================
    // 13/10/2026 (pedido de Joel): "Cliente u operación", filtro común de los 7 informes
    // (RF32) -- coincideClienteUOperacion.
    // ======================================================================

    @Test
    public void testCoincideClienteUOperacionSinTextoCoincideSiempre() {
        assertTrue(servicio.coincideClienteUOperacion(null, "CC-2026-00001", "1234567", "Juan Pérez"));
        assertTrue(servicio.coincideClienteUOperacion("  ", "CC-2026-00001", "1234567", "Juan Pérez"));
    }

    @Test
    public void testCoincideClienteUOperacionPorNombreSinTildesNiMayusculas() {
        assertTrue(servicio.coincideClienteUOperacion("jose", "CC-2026-00001", "1234567", "José Pérez"));
    }

    // La cédula que se compara es siempre la cruda (Persona.getCedulaIdentidad(), sin puntos --
    // el formato "1.234.567" es solo de pantalla, FormateadorTexto.comoCedulaConPuntos).
    @Test
    public void testCoincideClienteUOperacionPorCedula() {
        assertTrue(servicio.coincideClienteUOperacion("234567", "CC-2026-00001", "1234567", "Juan Pérez"));
    }

    @Test
    public void testCoincideClienteUOperacionPorCodigoDeOperacion() {
        assertTrue(servicio.coincideClienteUOperacion("cc-2026-00001", "CC-2026-00001", "1234567", "Juan Pérez"));
    }

    @Test
    public void testCoincideClienteUOperacionSinNingunaCoincidencia() {
        assertFalse(servicio.coincideClienteUOperacion("Maria", "CC-2026-00001", "1234567", "Juan Pérez"));
    }

    @Test
    public void testCoincideClienteUOperacionConCamposNulos() {
        assertFalse(servicio.coincideClienteUOperacion("juan", null, null, null));
    }

    // RF34 (auditoría de impresión, pedido de Joel): el mismo aviso que ya mostraba
    // estado_cuenta_ficha.jsp (if/else por estado), ahora reusado también en el PDF/CSV.
    @Test
    public void testConstruirAvisoPlanEstadoCuentaPendienteDesembolso() {
        assertEquals("Plan proyectado: el préstamo todavía no fue desembolsado.",
                servicio.construirAvisoPlanEstadoCuenta(EstadoPrestamo.PENDIENTE_DESEMBOLSO));
    }

    @Test
    public void testConstruirAvisoPlanEstadoCuentaAnuladoAntesDesembolso() {
        assertEquals("Préstamo anulado antes del desembolso; no generó deuda.",
                servicio.construirAvisoPlanEstadoCuenta(EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO));
    }

    @Test
    public void testConstruirAvisoPlanEstadoCuentaSinAvisoParaLosDemasEstados() {
        assertEquals("", servicio.construirAvisoPlanEstadoCuenta(EstadoPrestamo.ACTIVO));
        assertEquals("", servicio.construirAvisoPlanEstadoCuenta(EstadoPrestamo.EN_MORA));
        assertEquals("", servicio.construirAvisoPlanEstadoCuenta(EstadoPrestamo.FINALIZADO));
    }

    // ======================================================================
    // B205 (15/11/2026, item 1 del informe de UX de Informes): Auditoria pagina en vez de quedarse
    // con las primeras 500 filas del rango.
    // ======================================================================

    /** Las filas por pagina son las 20 de todo el sistema, y el tope de 500 quedo SOLO para el PDF:
     * la pantalla y el CSV ya no cortan nada. */
    @Test
    public void testAuditoria_FilasPorPaginaYTopeDelPdf() {
        assertEquals(20, ServicioReportes.FILAS_POR_PAGINA_AUDITORIA);
        assertEquals(500, ServicioReportes.LIMITE_AUDITORIA_PDF);
    }

    /** El total de paginas: nunca menos de 1 (con cero filas la pantalla dice "Pagina 1 de 1",
     * igual que las otras cinco paginaciones), y la ultima pagina incompleta cuenta igual. */
    @Test
    public void testAuditoria_TotalDePaginas() {
        assertEquals(1, ServicioReportes.totalPaginasAuditoria(0));
        assertEquals(1, ServicioReportes.totalPaginasAuditoria(1));
        assertEquals(1, ServicioReportes.totalPaginasAuditoria(20));
        assertEquals(2, ServicioReportes.totalPaginasAuditoria(21));
        // El dia que descubrio el defecto: 2.226 registros de un solo dia, el rango por defecto.
        assertEquals(112, ServicioReportes.totalPaginasAuditoria(2226));
        // Y el mes completo, que antes se veia como "500 de 18.381".
        assertEquals(920, ServicioReportes.totalPaginasAuditoria(18381));
    }

    // ======================================================================
    // B207 (15/11/2026, item 5): el rango de fechas al revés se avisa, en vez de salir vacío y
    // dejar que "no hay datos" se confunda con "el filtro no tiene sentido" (familia de B161).
    // ======================================================================

    @Test
    public void testAvisoRangoInvertido_LoAvisaConLasDosFechas() {
        assertEquals("Revise el rango: «Desde» (20/11/2026) es posterior a «Hasta» (15/11/2026), "
                + "así que el listado sale vacío.",
                ServicioReportes.avisoRangoInvertido("2026-11-20", "2026-11-15"));
    }

    /** Un rango correcto no avisa nada. Y las fechas IGUALES son un rango válido -- un solo día,
     * que es justo el que Auditoría trae por defecto. */
    @Test
    public void testAvisoRangoInvertido_NoMolestaConUnRangoValido() {
        assertEquals("", ServicioReportes.avisoRangoInvertido("2026-11-01", "2026-11-30"));
        assertEquals("", ServicioReportes.avisoRangoInvertido("2026-11-15", "2026-11-15"));
    }

    /** Lo que no se puede leer no se avisa: el fragmento se dibuja en TODAS las cargas de los trece
     * reportes, incluida la primera (sin parámetros), así que un null no puede ensuciar la
     * pantalla ni, menos, hacerla fallar. */
    @Test
    public void testAvisoRangoInvertido_NoAvisaNadaQueNoPuedaLeer() {
        assertEquals("", ServicioReportes.avisoRangoInvertido(null, null));
        assertEquals("", ServicioReportes.avisoRangoInvertido("", ""));
        assertEquals("", ServicioReportes.avisoRangoInvertido("   ", "2026-11-15"));
        assertEquals("", ServicioReportes.avisoRangoInvertido("2026-11-20", null));
        assertEquals("", ServicioReportes.avisoRangoInvertido("ayer", "hoy"));
        assertEquals("", ServicioReportes.avisoRangoInvertido("20/11/2026", "15/11/2026"));
    }

    // ======================================================================
    // B211 (15/11/2026, item 6): el rango aplicado, escrito debajo del titulo.
    // ======================================================================

    @Test
    public void testTextoRangoAplicado_DiceElPeriodo() {
        assertEquals("Del 01/11/2026 al 15/11/2026.",
                ServicioReportes.textoRangoAplicado(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));
    }

    /** Un solo dia no se escribe como un rango: "Del 15/11/2026 al 15/11/2026" se lee como un
     * error, y es el rango por defecto de Auditoria. */
    @Test
    public void testTextoRangoAplicado_UnSoloDiaNoEsUnRango() {
        assertEquals("Del 15/11/2026.",
                ServicioReportes.textoRangoAplicado(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));
    }

    /** Sin fechas no hay renglon: los reportes que son foto del momento (Cartera, Morosidad,
     * Incobrables) incluyen el mismo fragmento y no tienen que dibujar nada. */
    @Test
    public void testTextoRangoAplicado_SinFechasNoDiceNada() {
        assertEquals("", ServicioReportes.textoRangoAplicado(null, null));
        assertEquals("", ServicioReportes.textoRangoAplicado(LocalDate.of(2026, 11, 1), null));
        assertEquals("", ServicioReportes.textoRangoAplicado(null, LocalDate.of(2026, 11, 1)));
    }

    /** Los tres reportes por mes (IVA, Facturas anuladas, Resumen mensual) usan el mismo renglon
     * con otro texto. */
    @Test
    public void testTextoMesAplicado() {
        assertEquals("Mes de noviembre de 2026.",
                ServicioReportes.textoMesAplicado(java.time.YearMonth.of(2026, 11)));
        assertEquals("", ServicioReportes.textoMesAplicado(null));
    }

    // ======================================================================
    // B210, segunda vuelta (15/11/2026, defecto que Joel encontro al probar): Vencimientos se
    // ordenaba por la fecha FORMATEADA ("dd/MM/yyyy"), o sea por dia, despues mes, despues año.
    // Con el rango 15/11 a 31/12 la pagina 1 arrancaba en 01/12 y las cuotas de HOY caian varias
    // paginas mas adelante. Estas pruebas fallan con el codigo viejo.
    // ======================================================================

    private static FilaVencimiento filaDeVencimiento(String operacion, int numeroCuota, LocalDate fecha) {
        return new FilaVencimiento(1L, operacion, "Cliente de prueba", "0981123456", "4.111.222",
                numeroCuota, 12, FormateadorFechas.comoDdMmAaaa(fecha), fecha,
                "193.280 Gs.", new BigDecimal("193280"), "Pendiente");
    }

    /** EL caso que encontro Joel: "01/12/2026" es MENOR que "15/11/2026" como texto, y mayor como
     * fecha. Si el comparador vuelve a mirar el texto, esta prueba se pone en rojo. */
    @Test
    public void testOrdenVencimientos_LoQueVencePrimeroVaPrimero() {
        List<FilaVencimiento> filas = new java.util.ArrayList<FilaVencimiento>(Arrays.asList(
                filaDeVencimiento("CC-2026-00002", 3, LocalDate.of(2026, 12, 1)),
                filaDeVencimiento("CC-2026-00003", 4, LocalDate.of(2026, 12, 2)),
                filaDeVencimiento("CC-2026-00001", 2, LocalDate.of(2026, 11, 15)),
                filaDeVencimiento("CC-2026-00004", 5, LocalDate.of(2026, 11, 30))));

        filas.sort(ServicioReportes.COMPARADOR_VENCIMIENTOS);

        assertEquals("15/11/2026", filas.get(0).getFechaVencimiento());
        assertEquals("30/11/2026", filas.get(1).getFechaVencimiento());
        assertEquals("01/12/2026", filas.get(2).getFechaVencimiento());
        assertEquals("02/12/2026", filas.get(3).getFechaVencimiento());
    }

    /** Y el año tambien: como texto, "31/12/2026" va antes que "01/01/2027". */
    @Test
    public void testOrdenVencimientos_CruzandoElAño() {
        List<FilaVencimiento> filas = new java.util.ArrayList<FilaVencimiento>(Arrays.asList(
                filaDeVencimiento("CC-2026-00002", 1, LocalDate.of(2027, 1, 1)),
                filaDeVencimiento("CC-2026-00001", 1, LocalDate.of(2026, 12, 31))));

        filas.sort(ServicioReportes.COMPARADOR_VENCIMIENTOS);

        assertEquals("31/12/2026", filas.get(0).getFechaVencimiento());
        assertEquals("01/01/2027", filas.get(1).getFechaVencimiento());
    }

    /** Varias cuotas del MISMO dia necesitan un desempate estable, o pueden alternar entre paginas
     * en dos cargas distintas (la leccion de B205 con fecha_hora). */
    @Test
    public void testOrdenVencimientos_DesempateEstableEnElMismoDia() {
        LocalDate mismoDia = LocalDate.of(2026, 11, 15);
        List<FilaVencimiento> filas = new java.util.ArrayList<FilaVencimiento>(Arrays.asList(
                filaDeVencimiento("CC-2026-00009", 2, mismoDia),
                filaDeVencimiento("CC-2026-00001", 7, mismoDia),
                filaDeVencimiento("CC-2026-00001", 3, mismoDia)));

        filas.sort(ServicioReportes.COMPARADOR_VENCIMIENTOS);

        assertEquals("CC-2026-00001", filas.get(0).getIdentificadorOperacion());
        assertEquals(3, filas.get(0).getNumeroCuota());
        assertEquals("CC-2026-00001", filas.get(1).getIdentificadorOperacion());
        assertEquals(7, filas.get(1).getNumeroCuota());
        assertEquals("CC-2026-00009", filas.get(2).getIdentificadorOperacion());
    }

    /** El dato formateado es para mostrar: el DTO lleva la fecha cruda al lado, igual que B99 hizo
     * con el monto. Si alguien saca getFechaExacta, el comparador no compila. */
    @Test
    public void testFilaVencimiento_LlevaLaFechaSinFormatearAlLadoDeLaFormateada() {
        FilaVencimiento fila = filaDeVencimiento("CC-2026-00001", 1, LocalDate.of(2026, 11, 15));
        assertEquals("15/11/2026", fila.getFechaVencimiento());
        assertEquals(LocalDate.of(2026, 11, 15), fila.getFechaExacta());
    }
}
