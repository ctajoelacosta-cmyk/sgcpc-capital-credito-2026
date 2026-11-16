package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * B175 (14/11/2026): el orden de la bandeja de aprobación, en sus dos pestañas. Es puro (sin
 * base), así que se prueba acá, igual que {@code ServicioSolicitudesOrdenTest} para B167.
 *
 * <p>Lo que estas pruebas cuidan, que es lo que se puede romper de verdad: que cada pestaña tenga
 * su propia lista blanca (y no deje ordenar por una columna que ahí no existe), que los dos órdenes
 * por defecto sigan siendo los que traía el SQL -- Pendientes por fecha de envío <b>ascendente</b>
 * (la que más espera, primero) y Historial por fecha de decisión <b>descendente</b> --, que la
 * fecha de decisión de una CANCELADA sea la de la cancelación, que el desempate exista (sin él,
 * dos filas iguales se intercambian entre dos cargas y con páginas se ve como una fila repetida o
 * una que falta), que los nombres se comparen sin tildes y que la lista que llega no se modifique.</p>
 *
 * <p><b>Lo que NO prueba</b>: que la pantalla ordene y pagine. Eso lo prueba Joel (DOGMA 1).</p>
 */
public class ServicioAprobacionCreditoOrdenTest {

    private final ServicioAprobacionCredito servicio = new ServicioAprobacionCredito();

    private static final boolean HISTORIAL = true;
    private static final boolean PENDIENTES = false;

    private SolicitudCredito pendiente(String codigo, String fechaEnvio, long idCliente,
            String monto, int plazo, String afectacion, long creadoPor) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setCodigoSolicitud(codigo);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_APROBACION);
        solicitud.setFechaEnvioAprobacion(fechaEnvio == null ? null : LocalDateTime.parse(fechaEnvio));
        solicitud.setIdCliente(idCliente);
        solicitud.setMontoSolicitado(new BigDecimal(monto));
        solicitud.setPlazoSolicitadoMeses(plazo);
        solicitud.setPorcentajeAfectacion(new BigDecimal(afectacion));
        solicitud.setCreadoPor(creadoPor);
        return solicitud;
    }

    private SolicitudCredito decidida(String codigo, EstadoSolicitud estado, String fechaDecision,
            long idDecisor) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setCodigoSolicitud(codigo);
        solicitud.setEstadoSolicitud(estado);
        solicitud.setFechaEnvioAprobacion(LocalDateTime.parse("2026-11-01T08:00"));
        solicitud.setMontoSolicitado(new BigDecimal("1000000"));
        solicitud.setPlazoSolicitadoMeses(12);
        solicitud.setPorcentajeAfectacion(new BigDecimal("20"));
        if (estado == EstadoSolicitud.CANCELADA) {
            // La fecha y el responsable de una CANCELADA viven en OTRAS dos columnas.
            solicitud.setFechaCancelacion(fechaDecision == null ? null : LocalDateTime.parse(fechaDecision));
            solicitud.setCanceladoPor(idDecisor);
        } else {
            solicitud.setFechaDecision(fechaDecision == null ? null : LocalDateTime.parse(fechaDecision));
            solicitud.setDecididoPor(idDecisor);
        }
        return solicitud;
    }

    private Persona cliente(long id, String nombres, String apellidos) {
        Persona persona = new Persona();
        persona.setIdPersona(id);
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        return persona;
    }

    private Usuario usuario(String nombres, String apellidos) {
        Usuario usuario = new Usuario();
        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        return usuario;
    }

    private List<String> codigos(List<SolicitudCredito> solicitudes) {
        List<String> salida = new ArrayList<>();
        for (SolicitudCredito solicitud : solicitudes) {
            salida.add(solicitud.getCodigoSolicitud());
        }
        return salida;
    }

    // ======================================================================
    // Lista blanca: cada pestaña tiene la suya
    // ======================================================================

    @Test
    public void testPendientesPermiteSusSieteColumnas() {
        for (String clave : Arrays.asList("codigo", "enviada", "cliente", "monto", "plazo",
                "afectacion", "enviadapor")) {
            assertTrue(clave, ServicioAprobacionCredito.esOrdenPermitido(clave, PENDIENTES));
        }
    }

    @Test
    public void testHistorialPermiteSusNueveColumnas() {
        for (String clave : Arrays.asList("codigo", "enviada", "cliente", "monto", "plazo",
                "afectacion", "decision", "decididapor", "fechadecision")) {
            assertTrue(clave, ServicioAprobacionCredito.esOrdenPermitido(clave, HISTORIAL));
        }
    }

    /** Lo importante de tener dos listas: una columna que en esa pestaña no se dibuja no ordena. */
    @Test
    public void testCadaPestanaRechazaLasColumnasDeLaOtra() {
        for (String clave : Arrays.asList("decision", "decididapor", "fechadecision")) {
            assertFalse(clave, ServicioAprobacionCredito.esOrdenPermitido(clave, PENDIENTES));
        }
        assertFalse(ServicioAprobacionCredito.esOrdenPermitido("enviadapor", HISTORIAL));
    }

    /** "Esperando" no ordena a propósito: es el mismo dato que "Enviada" contado de otra manera. */
    @Test
    public void testEsperandoNoEsUnaClaveDeOrden() {
        assertFalse(ServicioAprobacionCredito.esOrdenPermitido("esperando", PENDIENTES));
        assertFalse(ServicioAprobacionCredito.esOrdenPermitido("esperando", HISTORIAL));
    }

    @Test
    public void testLaListaBlancaNoDejaPasarNadaMas() {
        for (boolean pestana : new boolean[] {PENDIENTES, HISTORIAL}) {
            assertFalse(ServicioAprobacionCredito.esOrdenPermitido(null, pestana));
            assertFalse(ServicioAprobacionCredito.esOrdenPermitido("", pestana));
            assertFalse(ServicioAprobacionCredito.esOrdenPermitido("CODIGO", pestana));
            assertFalse(ServicioAprobacionCredito.esOrdenPermitido("monto; DROP TABLE", pestana));
            assertFalse(ServicioAprobacionCredito.esOrdenPermitido("fecha_envio_aprobacion", pestana));
        }
    }

    // ======================================================================
    // Orden y sentido por defecto de cada pestaña
    // ======================================================================

    @Test
    public void testOrdenPorDefectoDeCadaPestana() {
        assertEquals("enviada", ServicioAprobacionCredito.ordenAplicado(null, PENDIENTES));
        assertEquals("fechadecision", ServicioAprobacionCredito.ordenAplicado(null, HISTORIAL));
        // Una clave inválida cae en el de la pestaña, no en el de la otra.
        assertEquals("enviada", ServicioAprobacionCredito.ordenAplicado("fechadecision", PENDIENTES));
        assertEquals("fechadecision", ServicioAprobacionCredito.ordenAplicado("enviadapor", HISTORIAL));
    }

    @Test
    public void testSinColumnaPedidaElSentidoEsElDeSiempre() {
        assertEquals("asc", ServicioAprobacionCredito.sentidoAplicado(null, null, PENDIENTES));
        assertEquals("desc", ServicioAprobacionCredito.sentidoAplicado(null, null, HISTORIAL));
    }

    @Test
    public void testConColumnaPedidaYSinSentidoEsAscendente() {
        assertEquals("asc", ServicioAprobacionCredito.sentidoAplicado("monto", null, PENDIENTES));
        assertEquals("asc", ServicioAprobacionCredito.sentidoAplicado("decididapor", null, HISTORIAL));
    }

    @Test
    public void testConSentidoExplicitoManda() {
        assertEquals("desc", ServicioAprobacionCredito.sentidoAplicado("monto", "desc", PENDIENTES));
        assertEquals("asc", ServicioAprobacionCredito.sentidoAplicado(null, "ASC", HISTORIAL));
    }

    // ======================================================================
    // Pendientes
    // ======================================================================

    /** El orden de siempre: ORDER BY fecha_envio_aprobacion, o sea la que más espera primero. */
    @Test
    public void testPendientesPorDefectoEsLaQueMasEspera() {
        SolicitudCredito vieja = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 7);
        SolicitudCredito nueva = pendiente("SOL-2", "2026-11-14T09:00", 2, "2000000", 12, "20", 7);
        List<SolicitudCredito> ordenadas = servicio.ordenarParaBandeja(Arrays.asList(nueva, vieja),
                null, null, null, null, ServicioAprobacionCredito.SENTIDO_POR_DEFECTO_PENDIENTES, PENDIENTES);
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(ordenadas));
    }

    @Test
    public void testPendientesPorMonto() {
        SolicitudCredito chica = pendiente("SOL-1", "2026-11-10T09:00", 1, "900000", 6, "10", 7);
        SolicitudCredito grande = pendiente("SOL-2", "2026-11-11T09:00", 2, "15000000", 12, "20", 7);
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(grande, chica), null, null, null, "monto", "asc", PENDIENTES)));
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(chica, grande), null, null, null, "monto", "desc", PENDIENTES)));
    }

    /** El riesgo que justifica ordenar el modelo y no la fila ya formateada: "9.000.000 Gs." es
     * alfabéticamente mayor que "15.000.000 Gs.", así que ordenar el texto daría vuelta el orden. */
    @Test
    public void testElMontoSeOrdenaComoNumeroNoComoTexto() {
        SolicitudCredito nueve = pendiente("SOL-9", "2026-11-10T09:00", 1, "9000000", 6, "10", 7);
        SolicitudCredito quince = pendiente("SOL-15", "2026-11-11T09:00", 2, "15000000", 12, "20", 7);
        assertEquals(Arrays.asList("SOL-9", "SOL-15"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(quince, nueve), null, null, null, "monto", "asc", PENDIENTES)));
    }

    @Test
    public void testPendientesPorAfectacionYPorPlazo() {
        SolicitudCredito baja = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 24, "15.4", 7);
        SolicitudCredito alta = pendiente("SOL-2", "2026-11-11T09:00", 2, "2000000", 6, "154.6", 7);
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(alta, baja), null, null, null, "afectacion", "asc", PENDIENTES)));
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(baja, alta), null, null, null, "plazo", "asc", PENDIENTES)));
    }

    /** La columna "Enviada por" ordena por el mapa de oficiales, por apellido. */
    @Test
    public void testPendientesPorQuienLaEnvio() {
        SolicitudCredito deRamirez = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 70);
        SolicitudCredito deAcosta = pendiente("SOL-2", "2026-11-11T09:00", 2, "2000000", 12, "20", 71);
        Map<Long, Usuario> oficiales = new HashMap<>();
        oficiales.put(70L, usuario("Lorenzo", "Ramírez"));
        oficiales.put(71L, usuario("Zulma", "Acosta"));
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(deRamirez, deAcosta), null, null, oficiales, "enviadapor", "asc", PENDIENTES)));
    }

    /** Sin mapa de oficiales (o con un id que no está), esa fila va al final y no revienta. */
    @Test
    public void testSinNombreDeOficialLaFilaVaAlFinal() {
        SolicitudCredito conNombre = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 70);
        SolicitudCredito sinNombre = pendiente("SOL-2", "2026-11-11T09:00", 2, "2000000", 12, "20", 99);
        Map<Long, Usuario> oficiales = new HashMap<>();
        oficiales.put(70L, usuario("Lorenzo", "Ramírez"));
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(sinNombre, conNombre), null, null, oficiales, "enviadapor", "asc", PENDIENTES)));
        // Y también al final en descendente: una fila incompleta nunca encabeza el listado.
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(sinNombre, conNombre), null, null, oficiales, "enviadapor", "desc", PENDIENTES)));
    }

    // ======================================================================
    // Historial
    // ======================================================================

    /** El orden de siempre del Historial, y el ítem 9 del informe: la decisión más nueva, primero. */
    @Test
    public void testHistorialPorDefectoEsLaDecisionMasNueva() {
        SolicitudCredito vieja = decidida("SOL-1", EstadoSolicitud.APROBADA, "2026-11-10T10:00", 5);
        SolicitudCredito nueva = decidida("SOL-2", EstadoSolicitud.RECHAZADA, "2026-11-14T10:00", 5);
        List<SolicitudCredito> ordenadas = servicio.ordenarParaBandeja(Arrays.asList(vieja, nueva),
                null, null, null, null, ServicioAprobacionCredito.SENTIDO_POR_DEFECTO_HISTORIAL, HISTORIAL);
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(ordenadas));
    }

    /** La fecha de decisión de una CANCELADA es la de la cancelación, igual que en la columna. */
    @Test
    public void testLaCanceladaOrdenaPorSuFechaDeCancelacion() {
        SolicitudCredito aprobada = decidida("SOL-1", EstadoSolicitud.APROBADA, "2026-11-12T10:00", 5);
        SolicitudCredito cancelada = decidida("SOL-2", EstadoSolicitud.CANCELADA, "2026-11-13T10:00", 6);
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(aprobada, cancelada), null, null, null, "fechadecision", "desc", HISTORIAL)));
    }

    /** Y quién decidió sale de canceladoPor en ese caso: es la regla de idDecisorDe. */
    @Test
    public void testHistorialPorQuienDecidio() {
        SolicitudCredito aprobada = decidida("SOL-1", EstadoSolicitud.APROBADA, "2026-11-12T10:00", 5);
        SolicitudCredito cancelada = decidida("SOL-2", EstadoSolicitud.CANCELADA, "2026-11-13T10:00", 6);
        Map<Long, Usuario> decisores = new HashMap<>();
        decisores.put(5L, usuario("Ramón", "Zárate"));
        decisores.put(6L, usuario("Sofía", "Benítez"));
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(aprobada, cancelada), null, decisores, null, "decididapor", "asc", HISTORIAL)));
    }

    @Test
    public void testIdDecisorSegunElEstado() {
        assertEquals(Long.valueOf(5L), ServicioAprobacionCredito.idDecisorDe(
                decidida("SOL-1", EstadoSolicitud.APROBADA, "2026-11-12T10:00", 5)));
        assertEquals(Long.valueOf(6L), ServicioAprobacionCredito.idDecisorDe(
                decidida("SOL-2", EstadoSolicitud.CANCELADA, "2026-11-13T10:00", 6)));
    }

    /** El estado ordena por su ETIQUETA, que es lo que el gerente lee, no por el orden del enum. */
    @Test
    public void testHistorialPorDecisionUsaLaEtiqueta() {
        SolicitudCredito aprobada = decidida("SOL-A", EstadoSolicitud.APROBADA, "2026-11-12T10:00", 5);
        SolicitudCredito cancelada = decidida("SOL-C", EstadoSolicitud.CANCELADA, "2026-11-13T10:00", 5);
        SolicitudCredito rechazada = decidida("SOL-R", EstadoSolicitud.RECHAZADA, "2026-11-11T10:00", 5);
        List<String> esperado = new ArrayList<>();
        for (EstadoSolicitud estado : Arrays.asList(EstadoSolicitud.APROBADA, EstadoSolicitud.CANCELADA,
                EstadoSolicitud.RECHAZADA)) {
            esperado.add(estado.getEtiqueta());
        }
        Collections.sort(esperado, String.CASE_INSENSITIVE_ORDER);
        List<String> obtenido = new ArrayList<>();
        for (SolicitudCredito solicitud : servicio.ordenarParaBandeja(
                Arrays.asList(cancelada, rechazada, aprobada), null, null, null, "decision", "asc", HISTORIAL)) {
            obtenido.add(solicitud.getEstadoSolicitud().getEtiqueta());
        }
        assertEquals(esperado, obtenido);
    }

    // ======================================================================
    // Cliente, desempate y la lista que llega
    // ======================================================================

    @Test
    public void testPorClienteSinTildesYPorApellido() {
        SolicitudCredito deAvila = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 7);
        SolicitudCredito deAyala = pendiente("SOL-2", "2026-11-11T09:00", 2, "2000000", 12, "20", 7);
        Map<Long, Persona> clientes = new HashMap<>();
        clientes.put(1L, cliente(1, "Zulma", "Ávila"));
        clientes.put(2L, cliente(2, "Ana", "Ayala"));
        // Con la tilde sin normalizar, "Ávila" ordenaría DESPUÉS de "Ayala".
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(deAyala, deAvila), clientes, null, null, "cliente", "asc", PENDIENTES)));
    }

    /** Sin desempate, dos filas con el mismo valor se intercambian entre dos cargas -- y con
     * páginas eso se ve como una fila repetida en la página 2 o una que desapareció. */
    @Test
    public void testDesempateEstablePorElOrdenDeLaPestana() {
        SolicitudCredito vieja = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 7);
        SolicitudCredito nueva = pendiente("SOL-2", "2026-11-14T09:00", 2, "1000000", 6, "10", 7);
        // Mismo monto: desempata por la fecha de envío ascendente, el orden de Pendientes.
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(nueva, vieja), null, null, null, "monto", "asc", PENDIENTES)));
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(vieja, nueva), null, null, null, "monto", "asc", PENDIENTES)));
        // En el Historial el desempate es el suyo: la decisión más nueva primero.
        SolicitudCredito decidioAyer = decidida("SOL-3", EstadoSolicitud.APROBADA, "2026-11-13T10:00", 5);
        SolicitudCredito decidioHoy = decidida("SOL-4", EstadoSolicitud.APROBADA, "2026-11-14T10:00", 5);
        assertEquals(Arrays.asList("SOL-4", "SOL-3"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(decidioAyer, decidioHoy), null, null, null, "monto", "asc", HISTORIAL)));
    }

    @Test
    public void testSinFechaDeEnvioLaFilaVaAlFinal() {
        SolicitudCredito conFecha = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 7);
        SolicitudCredito sinFecha = pendiente("SOL-2", null, 2, "2000000", 12, "20", 7);
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(sinFecha, conFecha), null, null, null, "enviada", "asc", PENDIENTES)));
        assertEquals(Arrays.asList("SOL-1", "SOL-2"), codigos(servicio.ordenarParaBandeja(
                Arrays.asList(sinFecha, conFecha), null, null, null, "enviada", "desc", PENDIENTES)));
    }

    @Test
    public void testLaListaQueLlegaNoSeModifica() {
        SolicitudCredito vieja = pendiente("SOL-1", "2026-11-10T09:00", 1, "1000000", 6, "10", 7);
        SolicitudCredito nueva = pendiente("SOL-2", "2026-11-14T09:00", 2, "2000000", 12, "20", 7);
        List<SolicitudCredito> original = new ArrayList<>(Arrays.asList(nueva, vieja));
        servicio.ordenarParaBandeja(original, null, null, null, "enviada", "asc", PENDIENTES);
        assertEquals(Arrays.asList("SOL-2", "SOL-1"), codigos(original));
    }

    @Test
    public void testListaNulaDevuelveVacia() {
        assertTrue(servicio.ordenarParaBandeja(null, null, null, null, "monto", "asc", PENDIENTES).isEmpty());
    }
}
