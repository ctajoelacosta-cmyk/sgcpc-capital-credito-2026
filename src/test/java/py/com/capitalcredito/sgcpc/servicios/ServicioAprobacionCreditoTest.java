package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud;
import py.com.capitalcredito.sgcpc.dto.FilaBandejaAprobacion;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/** E4 (P05, RN-06): "el rechazo exige motivo". */
public class ServicioAprobacionCreditoTest {

    private final ServicioAprobacionCredito servicio = new ServicioAprobacionCredito();

    @Test
    public void testMotivoRechazoVacioSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarMotivoRechazo("  "));
        assertThrows(NegocioException.class, () -> servicio.validarMotivoRechazo(null));
    }

    @Test
    public void testMotivoRechazoConTextoNoLanza() {
        servicio.validarMotivoRechazo("Informconf: figura con deudas");
    }

    // ======================================================================
    // RF07 (pedido de Joel, 11/10/2026): construirFilaBandeja -- hallazgo del Historial con
    // SOL-2026-0003 (CANCELADA desde BORRADOR, sin fecha_envio_aprobacion) tirando
    // NullPointerException en lista.jsp. Una fila cancelada, una aprobada y una rechazada.
    // ======================================================================

    private static SolicitudCredito solicitudBase(long id, String codigo, EstadoSolicitud estado) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setIdSolicitud(id);
        solicitud.setCodigoSolicitud(codigo);
        solicitud.setIdCliente(1L);
        solicitud.setMontoSolicitado(new BigDecimal("3000000"));
        solicitud.setPlazoSolicitadoMeses(12);
        solicitud.setPorcentajeAfectacion(new BigDecimal("0.25"));
        solicitud.setEstadoSolicitud(estado);
        return solicitud;
    }

    /** SOL-2026-0003: cancelada directo desde BORRADOR -- nunca se envió a aprobación, así que
     * fecha_envio_aprobacion queda null. Antes de la corrección, esto tiraba
     * NullPointerException al construir la fila (lista.jsp llamaba
     * fila.getFechaEnvioAprobacion().toLocalDate() sin chequear null). */
    @Test
    public void testConstruirFilaBandejaCanceladaSinEnvioAAprobacion() {
        SolicitudCredito solicitud = solicitudBase(3L, "SOL-2026-0003", EstadoSolicitud.CANCELADA);
        solicitud.setFechaEnvioAprobacion(null);
        solicitud.setCanceladoPor(10L);
        solicitud.setFechaCancelacion(LocalDateTime.of(2026, 10, 10, 9, 0));
        solicitud.setMotivoCancelacion("Desistió");

        Persona cliente = new Persona();
        cliente.setNombres("Ana");
        cliente.setApellidos("Gómez");
        cliente.setCedulaIdentidad("1234567");

        Usuario cancelador = new Usuario();
        cancelador.setNombres("Joel");
        cancelador.setApellidos("Acosta");

        FilaBandejaAprobacion fila = servicio.construirFilaBandeja(solicitud, cliente, cancelador, null, true);

        assertEquals("-", fila.getFechaEnvioFormateada());
        assertNull(fila.getEsperando());
        assertEquals("Cancelada", fila.getDecisionEtiqueta());
        assertEquals("Joel Acosta", fila.getDecididaPorNombre());
        assertEquals("10/10/2026 09:00", fila.getFechaDecisionFormateada());
    }

    @Test
    public void testConstruirFilaBandejaAprobadaEnHistorial() {
        SolicitudCredito solicitud = solicitudBase(1L, "SOL-2026-0001", EstadoSolicitud.APROBADA);
        solicitud.setFechaEnvioAprobacion(LocalDateTime.of(2026, 10, 10, 8, 0));
        solicitud.setDecididoPor(20L);
        solicitud.setFechaDecision(LocalDateTime.of(2026, 10, 10, 10, 0));

        Usuario gerente = new Usuario();
        gerente.setNombres("Mirtha");
        gerente.setApellidos("Lopez");

        FilaBandejaAprobacion fila = servicio.construirFilaBandeja(solicitud, null, gerente, null, true);

        assertEquals("10/10/2026", fila.getFechaEnvioFormateada());
        assertEquals("Aprobada", fila.getDecisionEtiqueta());
        assertEquals("Mirtha Lopez", fila.getDecididaPorNombre());
        assertEquals("10/10/2026 10:00", fila.getFechaDecisionFormateada());
        assertNull(fila.getEsperando());
    }

    @Test
    public void testConstruirFilaBandejaRechazadaEnHistorial() {
        SolicitudCredito solicitud = solicitudBase(2L, "SOL-2026-0002", EstadoSolicitud.RECHAZADA);
        solicitud.setFechaEnvioAprobacion(LocalDateTime.of(2026, 10, 10, 8, 0));
        solicitud.setDecididoPor(20L);
        solicitud.setFechaDecision(LocalDateTime.of(2026, 10, 10, 9, 30));

        FilaBandejaAprobacion fila = servicio.construirFilaBandeja(solicitud, null, null, null, true);

        assertEquals("Rechazada", fila.getDecisionEtiqueta());
        assertEquals("-", fila.getDecididaPorNombre());
        assertEquals("10/10/2026 09:30", fila.getFechaDecisionFormateada());
    }

    /** Pendientes: sin decisión todavía, pero sí "esperando" (contado desde el envío). */
    @Test
    public void testConstruirFilaBandejaPendienteCalculaEsperando() {
        SolicitudCredito solicitud = solicitudBase(4L, "SOL-2026-0004", EstadoSolicitud.PENDIENTE_APROBACION);
        solicitud.setFechaEnvioAprobacion(py.com.capitalcredito.sgcpc.infraestructura.Reloj.ahora().minusHours(3));

        FilaBandejaAprobacion fila = servicio.construirFilaBandeja(solicitud, null, null, null, false);

        assertEquals("3 h", fila.getEsperando());
        assertNull(fila.getDecisionEtiqueta());
        assertNull(fila.getDecididaPorNombre());
        assertNull(fila.getFechaDecisionFormateada());
    }

    // ======================================================================
    // RF08 (pedido de Joel, 11/10/2026): construirAvisoBuro -- franja de arriba de
    // aprobaciones/ficha.jsp. Mora, clasificación de riesgo alto (D/E), las dos juntas, y
    // ninguna.
    // ======================================================================

    private static Persona personaDePrueba(long idPersona, String nombres, String apellidos) {
        Persona persona = new Persona();
        persona.setIdPersona(idPersona);
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        return persona;
    }

    private static ConsultaCrediticia consultaDePrueba(long idPersona, String clasificacion, Boolean indicadorMora) {
        ConsultaCrediticia consulta = new ConsultaCrediticia();
        consulta.setIdPersona(idPersona);
        consulta.setClasificacionObtenida(clasificacion);
        consulta.setIndicadorMora(indicadorMora);
        consulta.setFechaHoraConsulta(LocalDateTime.of(2026, 10, 11, 9, 0));
        return consulta;
    }

    @Test
    public void testConstruirAvisoBuroSinMoraNiRiesgoAlto() {
        Persona cliente = personaDePrueba(1L, "Ana", "Gómez");
        ConsultaCrediticia consulta = consultaDePrueba(1L, "B", false);

        AvisoBuroSolicitud aviso = servicio.construirAvisoBuro(Collections.singletonList(consulta), cliente);

        assertFalse(aviso.isHayMora());
        assertFalse(aviso.isHayRiesgoAlto());
        assertTrue(aviso.getLineas().isEmpty());
    }

    @Test
    public void testConstruirAvisoBuroSoloMora() {
        Persona cliente = personaDePrueba(1L, "Ana", "Gómez");
        ConsultaCrediticia consulta = consultaDePrueba(1L, "B", true);

        AvisoBuroSolicitud aviso = servicio.construirAvisoBuro(Collections.singletonList(consulta), cliente);

        assertTrue(aviso.isHayMora());
        assertFalse(aviso.isHayRiesgoAlto());
        assertEquals(1, aviso.getLineas().size());
        assertEquals("El buró indica mora: Ana Gómez (cliente) — Informconf (11/10/2026).", aviso.getLineas().get(0));
    }

    /** Clasificación D/E sin mora: franja naranja (hayRiesgoAlto), nunca la roja. */
    @Test
    public void testConstruirAvisoBuroSoloRiesgoAlto() {
        Persona cliente = personaDePrueba(1L, "Ana", "Gómez");
        ConsultaCrediticia consulta = consultaDePrueba(1L, "E", false);

        AvisoBuroSolicitud aviso = servicio.construirAvisoBuro(Collections.singletonList(consulta), cliente);

        assertFalse(aviso.isHayMora());
        assertTrue(aviso.isHayRiesgoAlto());
        assertEquals(1, aviso.getLineas().size());
        assertEquals("Riesgo alto según Informconf: clasificación E — Ana Gómez (cliente).", aviso.getLineas().get(0));
    }

    /** Mora Y clasificación D/E a la vez (misma consulta): hayMora gana -- la línea de mora
     * incluye la clasificación, hayRiesgoAlto queda en false (no se duplica una segunda franja). */
    @Test
    public void testConstruirAvisoBuroMoraYRiesgoAltoJuntos() {
        Persona cliente = personaDePrueba(1L, "Ana", "Gómez");
        ConsultaCrediticia consulta = consultaDePrueba(1L, "D", true);

        AvisoBuroSolicitud aviso = servicio.construirAvisoBuro(Collections.singletonList(consulta), cliente);

        assertTrue(aviso.isHayMora());
        assertFalse(aviso.isHayRiesgoAlto());
        assertEquals(1, aviso.getLineas().size());
        assertEquals("El buró indica mora (clasificación D): Ana Gómez (cliente) — Informconf (11/10/2026).",
                aviso.getLineas().get(0));
    }

    // ======================================================================
    // RF08 (pedido de Joel, 11/10/2026): combinarFilasConsultaBuro -- hallazgo del gerente que
    // no veía una consulta con mora registrada después del envío. Parte pura (sin conexión) de
    // construirFilasConsultaBuro: combina la vinculada al enviar con la más reciente por
    // persona, sin duplicar, marcando posteriorAlEnvio en la que es nueva.
    // ======================================================================

    private static ConsultaCrediticia consultaConId(long idConsulta, long idPersona, String clasificacion,
            Boolean indicadorMora, java.time.LocalDateTime fecha) {
        ConsultaCrediticia consulta = new ConsultaCrediticia();
        consulta.setIdConsultaCrediticia(idConsulta);
        consulta.setIdPersona(idPersona);
        consulta.setClasificacionObtenida(clasificacion);
        consulta.setIndicadorMora(indicadorMora);
        consulta.setFechaHoraConsulta(fecha);
        return consulta;
    }

    /** Caso que rompía en pantalla: SOL-2026-0004 se envió con una consulta sin mora (vinculada);
     * después, con la solicitud ya PENDIENTE, se registró una consulta nueva con mora. La fila
     * nueva tiene que aparecer marcada "posterior al envío", y alimentar construirAvisoBuro con
     * la franja roja (no la vinculada vieja, que seguiría dando sin mora). */
    @Test
    public void testCombinarFilasConsultaBuroConsultaNuevaConMora() {
        Persona cliente = personaDePrueba(1L, "Luis", "Cabrera Núñez");
        LocalDateTime fechaEnvio = LocalDateTime.of(2026, 10, 11, 10, 0);
        ConsultaCrediticia vinculadaSinMora = consultaConId(10L, 1L, "E", false,
                LocalDateTime.of(2026, 10, 11, 8, 0));
        ConsultaCrediticia nuevaConMora = consultaConId(11L, 1L, "E", true,
                LocalDateTime.of(2026, 10, 11, 12, 0));

        List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> filas = servicio.combinarFilasConsultaBuro(
                Collections.singletonList(vinculadaSinMora), Collections.singletonList(nuevaConMora),
                fechaEnvio, cliente);

        assertEquals(2, filas.size());
        assertFalse(filas.get(0).isPosteriorAlEnvio());
        assertTrue(filas.get(1).isPosteriorAlEnvio());
        assertTrue(filas.get(1).isMora());

        // La franja tiene que usar la más reciente (con mora), no la vinculada (sin mora).
        AvisoBuroSolicitud aviso = servicio.construirAvisoBuro(Collections.singletonList(nuevaConMora), cliente);
        assertTrue(aviso.isHayMora());
    }

    /** Sin consulta nueva (la más reciente es la misma que la vinculada): una sola fila, sin
     * marca -- igual que antes de este cambio. */
    @Test
    public void testCombinarFilasConsultaBuroSinConsultaNueva() {
        Persona cliente = personaDePrueba(1L, "Ana", "Gómez");
        LocalDateTime fechaEnvio = LocalDateTime.of(2026, 10, 11, 10, 0);
        ConsultaCrediticia unica = consultaConId(10L, 1L, "B", false, LocalDateTime.of(2026, 10, 11, 8, 0));

        List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> filas = servicio.combinarFilasConsultaBuro(
                Collections.singletonList(unica), Collections.singletonList(unica), fechaEnvio, cliente);

        assertEquals(1, filas.size());
        assertFalse(filas.get(0).isPosteriorAlEnvio());
    }

    // ======================================================================
    // RF09/RF10 (pedido de Joel, 11/10/2026): construirMensajeYaDecidida -- carrera de dos
    // pestañas. Parte pura (sin conexión) de lo que pasa cuando marcarDecidida devuelve false.
    // ======================================================================

    private static Usuario usuarioDePrueba(String nombres, String apellidos) {
        Usuario usuario = new Usuario();
        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        return usuario;
    }

    @Test
    public void testConstruirMensajeYaDecididaAprobada() {
        SolicitudCredito actual = new SolicitudCredito();
        actual.setEstadoSolicitud(EstadoSolicitud.APROBADA);
        actual.setFechaDecision(LocalDateTime.of(2026, 10, 11, 14, 30));
        Usuario quienDecidio = usuarioDePrueba("Mirtha", "Lopez");

        String mensaje = servicio.construirMensajeYaDecidida(actual, quienDecidio);

        assertEquals("Esta solicitud ya fue decidida por Mirtha Lopez el 11/10/2026 14:30. "
                + "Actualice la página para ver su estado actual.", mensaje);
    }

    @Test
    public void testConstruirMensajeYaDecididaRechazada() {
        SolicitudCredito actual = new SolicitudCredito();
        actual.setEstadoSolicitud(EstadoSolicitud.RECHAZADA);
        actual.setFechaDecision(LocalDateTime.of(2026, 10, 11, 9, 5));
        Usuario quienDecidio = usuarioDePrueba("Joel", "Acosta");

        String mensaje = servicio.construirMensajeYaDecidida(actual, quienDecidio);

        assertEquals("Esta solicitud ya fue decidida por Joel Acosta el 11/10/2026 09:05. "
                + "Actualice la página para ver su estado actual.", mensaje);
    }

    /** CANCELADA usa fecha_cancelacion/cancelado_por, no fecha_decision/decidido_por -- el
     * llamador (construirErrorYaDecidida) ya resuelve cuál usuario pasar; esta prueba confirma
     * que el mensaje toma la fecha de cancelación cuando el estado es CANCELADA. */
    @Test
    public void testConstruirMensajeYaDecididaCancelada() {
        SolicitudCredito actual = new SolicitudCredito();
        actual.setEstadoSolicitud(EstadoSolicitud.CANCELADA);
        actual.setFechaCancelacion(LocalDateTime.of(2026, 10, 11, 16, 0));
        Usuario quienCancelo = usuarioDePrueba("Ana", "Gómez");

        String mensaje = servicio.construirMensajeYaDecidida(actual, quienCancelo);

        assertEquals("Esta solicitud ya fue cancelada por Ana Gómez el 11/10/2026 16:00. "
                + "Actualice la página para ver su estado actual.", mensaje);
    }

    @Test
    public void testConstruirMensajeYaDecididaSinUsuario() {
        SolicitudCredito actual = new SolicitudCredito();
        actual.setEstadoSolicitud(EstadoSolicitud.APROBADA);
        actual.setFechaDecision(LocalDateTime.of(2026, 10, 11, 14, 30));

        String mensaje = servicio.construirMensajeYaDecidida(actual, null);

        assertEquals("Esta solicitud ya fue decidida por otro usuario el 11/10/2026 14:30. "
                + "Actualice la página para ver su estado actual.", mensaje);
    }

    // ======================================================================
    // B84 (decisión de Joel, 28/10/2026): RN-01 al aprobar -- el texto exacto
    // ======================================================================

    /**
     * Lo único de B84 que se puede aislar sin base: que el mensaje no se desvíe del acordado con
     * Joel el 28/10/2026 (en usted, con el "qué sigue", sin la sigla interna "RN-01" -- punto 11
     * del Bloque 7/E10). El bloqueo en sí necesita base y lo cubre
     * {@link PruebaRN01AprobarFormalizar}.
     */
    @Test
    public void testMensajePrestamoAbiertoAlAprobar() {
        assertEquals("El cliente ya tiene un préstamo no cerrado. No se puede aprobar. Para pedir "
                + "otro crédito, primero debe terminar de pagarlo o cancelarlo.",
                ServicioAprobacionCredito.MENSAJE_PRESTAMO_ABIERTO_APROBAR);
    }

    /** Mismo texto base que los otros momentos de RN-01, y sin la sigla interna a la vista. */
    @Test
    public void testMensajePrestamoAbiertoAlAprobarComparteElTextoBaseDeRN01() {
        assertTrue(ServicioAprobacionCredito.MENSAJE_PRESTAMO_ABIERTO_APROBAR
                .startsWith("El cliente ya tiene un préstamo no cerrado."));
        assertFalse(ServicioAprobacionCredito.MENSAJE_PRESTAMO_ABIERTO_APROBAR.contains("RN-01"));
    }

    // ======================================================================
    // C20 (29/10/2026): no se puede cancelar una solicitud YA FORMALIZADA.
    // El bug lo demostro Joel en pantalla el 29/10/2026 (SOL-2026-0006, formalizada como
    // CC-2026-00005: el boton "Cancelar solicitud" seguia apareciendo). validarCancelable es pura,
    // asi que la guarda se prueba aca sin base; que el boton ya no se dibuje lo prueba Joel.
    // ======================================================================

    private SolicitudCredito solicitudParaCancelar(EstadoSolicitud estado, boolean formalizada) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setIdSolicitud(55L);
        solicitud.setCodigoSolicitud("SOL-2026-0006");
        solicitud.setEstadoSolicitud(estado);
        solicitud.setFormalizada(formalizada);
        return solicitud;
    }

    private static final String MENSAJE_NO_CANCELABLE = "Solo se puede cancelar una solicitud "
            + "pendiente de aprobación o aprobada (todavía no formalizada).";

    @Test
    public void testNoSePuedeCancelarUnaSolicitudYaFormalizada() {
        // El caso de C20: estado APROBADA (no existe un estado "FORMALIZADA"), pero con su
        // prestamo ya creado. El texto no cambio: ya prometia esta regla.
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarCancelable(solicitudParaCancelar(EstadoSolicitud.APROBADA, true)));
        assertEquals(MENSAJE_NO_CANCELABLE, error.getMessage());
    }

    @Test
    public void testSePuedeCancelarUnaAprobadaTodaviaSinFormalizar() {
        servicio.validarCancelable(solicitudParaCancelar(EstadoSolicitud.APROBADA, false));
    }

    @Test
    public void testSePuedeCancelarUnaPendienteDeAprobacion() {
        servicio.validarCancelable(solicitudParaCancelar(EstadoSolicitud.PENDIENTE_APROBACION, false));
    }

    @Test
    public void testLosDemasEstadosNoSeCancelanDesdeAprobaciones() {
        // Sin cambio de comportamiento, queda cubierto: BORRADOR se cancela por la otra puerta
        // (ServicioSolicitudes.cancelar, validarEstadoBorrador), y RECHAZADA/CANCELADA ya no.
        for (EstadoSolicitud estado : new EstadoSolicitud[] {EstadoSolicitud.BORRADOR,
                EstadoSolicitud.RECHAZADA, EstadoSolicitud.CANCELADA}) {
            NegocioException error = assertThrows(NegocioException.class,
                    () -> servicio.validarCancelable(solicitudParaCancelar(estado, false)));
            assertEquals(MENSAJE_NO_CANCELABLE, error.getMessage());
        }
    }
}
