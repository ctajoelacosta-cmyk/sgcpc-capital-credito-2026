package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;

/** E4 (P04): reglas puras de ServicioSolicitudes. El cálculo de capacidad en sí ya está probado en ServicioMotorFinancieroTest (E1). */
public class ServicioSolicitudesTest {

    private final ServicioSolicitudes servicio = new ServicioSolicitudes();

    private static final BigDecimal LIMITE = new BigDecimal("0.400000");

    // --- RF06 (pedido de Joel, 10/10/2026): motivo de cancelación, lista fija + detalle. ---

    @Test
    public void testMotivoCancelacionSinSeleccionarSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.construirMotivoCancelacion(null, null));
        assertThrows(NegocioException.class, () -> servicio.construirMotivoCancelacion("", null));
    }

    @Test
    public void testMotivoCancelacionCodigoInexistenteSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.construirMotivoCancelacion("NO_EXISTE", null));
    }

    @Test
    public void testMotivoCancelacionSinDetalleNoLoExigeNoLanza() {
        assertEquals("El cliente desistió", servicio.construirMotivoCancelacion("DESISTIO", null));
        assertEquals("El cliente desistió", servicio.construirMotivoCancelacion("DESISTIO", ""));
    }

    @Test
    public void testMotivoCancelacionConDetalleOpcionalSeAgrega() {
        assertEquals("El cliente desistió - se mudó de ciudad",
                servicio.construirMotivoCancelacion("DESISTIO", "se mudó de ciudad"));
    }

    @Test
    public void testMotivoCancelacionOtroSinDetalleSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.construirMotivoCancelacion(ServicioSolicitudes.MOTIVO_CANCELACION_OTRO, null));
        assertTrue(error.getMessage().contains("requiere un detalle"));
        assertThrows(NegocioException.class,
                () -> servicio.construirMotivoCancelacion(ServicioSolicitudes.MOTIVO_CANCELACION_OTRO, ""));
    }

    @Test
    public void testMotivoCancelacionOtroConDetalleNoLanza() {
        assertEquals("Otro - garantía insuficiente",
                servicio.construirMotivoCancelacion(ServicioSolicitudes.MOTIVO_CANCELACION_OTRO, "garantía insuficiente"));
    }

    @Test
    public void testListarMotivosCancelacionTieneLasCuatroOpciones() {
        List<String> codigos = new java.util.ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.OpcionMotivoCancelacion opcion : servicio.listarMotivosCancelacion()) {
            codigos.add(opcion.getCodigo());
        }
        assertEquals(Arrays.asList("DESISTIO", "DATOS_INCORRECTOS", "CAMBIO_SITUACION", "OTRO"), codigos);
    }

    private SolicitudCredito solicitudDePrueba(String codigo) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setCodigoSolicitud(codigo);
        return solicitud;
    }

    /** E5 (D-68): mensaje según el tipo de compromiso abierto (RN-01 préstamo vs RN-02 solicitud).
     * Punto 11 (Bloque 7/E10, pedido de Joel): el texto visible al usuario ya no incluye la
     * referencia interna "(RN-01)"/"(RN-02)" -- se verifica el contenido, no la sigla. */
    @Test
    public void testMensajeCompromisoAbiertoViaPrestamoEsRN01() {
        ServicioSolicitudes.CompromisoAbierto compromiso = new ServicioSolicitudes.CompromisoAbierto(
                solicitudDePrueba("SOL-2026-0001"), true);
        String mensaje = servicio.mensajeCompromisoAbierto(compromiso);
        assertTrue(mensaje.contains("préstamo no cerrado"));
    }

    @Test
    public void testMensajeCompromisoAbiertoViaSolicitudEsRN02() {
        ServicioSolicitudes.CompromisoAbierto compromiso = new ServicioSolicitudes.CompromisoAbierto(
                solicitudDePrueba("SOL-2026-0002"), false);
        String mensaje = servicio.mensajeCompromisoAbierto(compromiso);
        assertTrue(mensaje.contains("solicitud abierta"));
    }

    // --- Bloque 7/E10, Tanda B (D-127): política de crédito parametrizable ---

    private static final List<String> DEPARTAMENTOS_HABILITADOS = Arrays.asList("Asunción", "Central");
    private static final List<String> SITUACIONES_ADMITIDAS = Arrays.asList(
            "ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO", "JUBILADO_PENSIONADO", "COMERCIANTE",
            "EMPLEADO_DOMESTICO");

    @Test
    public void testSumarCuotasOtrasDeudasNulaEsCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.sumarCuotasOtrasDeudas(null)));
    }

    @Test
    public void testSumarCuotasOtrasDeudasConValor() {
        assertEquals(0, new BigDecimal("300000").compareTo(
                servicio.sumarCuotasOtrasDeudas(new BigDecimal("300000"))));
    }

    @Test
    public void testCalcularEdadCumpleanosPasado() {
        assertEquals(30, servicio.calcularEdad(LocalDate.of(1996, 1, 1), LocalDate.of(2026, 6, 1)));
    }

    @Test
    public void testCalcularEdadCumpleanosNoLlegoAun() {
        assertEquals(29, servicio.calcularEdad(LocalDate.of(1996, 12, 31), LocalDate.of(2026, 6, 1)));
    }

    @Test
    public void testPoliticaEdadFueraDeRangoMenorSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                18, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS));
        assertTrue(error.getMessage().contains("edad"));
    }

    @Test
    public void testPoliticaEdadFueraDeRangoMayorSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                75, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS));
    }

    @Test
    public void testPoliticaDepartamentoNoHabilitadoSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, "Alto Paraná", DEPARTAMENTOS_HABILITADOS, "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS));
        assertTrue(error.getMessage().contains("departamento"));
    }

    @Test
    public void testPoliticaDepartamentoSinCompletarSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, null, DEPARTAMENTOS_HABILITADOS, "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS));
    }

    @Test
    public void testPoliticaSituacionLaboralNoAdmitidaSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, "DESEMPLEADO", SITUACIONES_ADMITIDAS));
        assertTrue(error.getMessage().contains("situación laboral"));
    }

    @Test
    public void testPoliticaSituacionLaboralSinCompletarSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, null, SITUACIONES_ADMITIDAS));
    }

    @Test
    public void testPoliticaCumplidaNoLanza() {
        servicio.evaluarPoliticaCredito(30, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS);
    }

    @Test
    public void testPoliticaEdadEnLosBordesNoLanza() {
        servicio.evaluarPoliticaCredito(22, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS);
        servicio.evaluarPoliticaCredito(71, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "ASALARIADO_SECTOR_PRIVADO", SITUACIONES_ADMITIDAS);
    }

    // --- RF05 (pedido de Joel, 10/10/2026): el 40 % se rechaza ya al crear/simular, sin
    // excepción. ---

    @Test
    public void testLimiteAfectacionDentroDelLimiteNoLanza() {
        servicio.validarLimiteAfectacion(new BigDecimal("0.350000"), LIMITE);
    }

    @Test
    public void testLimiteAfectacionEnElBordeNoLanza() {
        servicio.validarLimiteAfectacion(LIMITE, LIMITE);
    }

    @Test
    public void testLimiteAfectacionSuperaSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.validarLimiteAfectacion(
                new BigDecimal("0.450000"), LIMITE));
        assertTrue(error.getMessage().contains("Baje el monto o alargue el plazo"));
    }
}
