package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion.SeccionBandeja;

/**
 * B183 (15/11/2026): el orden de las 3 secciones de la bandeja de Formalizacion.
 *
 * <p>Los casos que valen, que son los que B175 y B177 dejaron escritos: <b>el nulo en los DOS
 * sentidos</b> (el que encontro el defecto de B177, donde descendente subia los nulos al primer
 * lugar), el desempate entre dos filas iguales, y que una clave desconocida --o tecleada a mano en
 * la URL-- caiga en el orden de siempre en vez de llegar a una comparacion.</p>
 *
 * <p>Las pruebas ordenan pasando los parametros CRUDOS, como los manda la pantalla, y no valores
 * ya resueltos: es la leccion de B167, que un test que le pasa al metodo el resultado de la regla
 * no prueba la regla.</p>
 */
public class ServicioFormalizacionOrdenTest {

    private final ServicioFormalizacion servicio = new ServicioFormalizacion();

    // ==================================================================
    // Listas blancas y valores por defecto
    // ==================================================================

    @Test
    public void cadaSeccionTieneSuOrdenDeSiempre() {
        assertEquals("aprobada", ServicioFormalizacion.ordenPorDefecto(SeccionBandeja.FORMALIZAR));
        assertEquals("formalizado", ServicioFormalizacion.ordenPorDefecto(SeccionBandeja.DESEMBOLSO));
        assertEquals("cancelada", ServicioFormalizacion.ordenPorDefecto(SeccionBandeja.PAGARE));
    }

    @Test
    public void sinOrdenPedidoSeUsaElDeSiempreDeEsaSeccion() {
        assertEquals("aprobada", ServicioFormalizacion.ordenAplicado(null, SeccionBandeja.FORMALIZAR));
        assertEquals("formalizado", ServicioFormalizacion.ordenAplicado("", SeccionBandeja.DESEMBOLSO));
        assertEquals("cancelada", ServicioFormalizacion.ordenAplicado(null, SeccionBandeja.PAGARE));
    }

    /** Lo que viaja del navegador es una CLAVE, no un nombre de columna (B157): lo desconocido
     * nunca llega a una comparacion, cae en el orden de siempre. */
    @Test
    public void unaClaveDesconocidaOPeligrosaCaeEnElOrdenDeSiempre() {
        assertEquals("aprobada", ServicioFormalizacion.ordenAplicado(
                "cedula_identidad; DROP TABLE personas", SeccionBandeja.FORMALIZAR));
        assertEquals("formalizado", ServicioFormalizacion.ordenAplicado("loquesea", SeccionBandeja.DESEMBOLSO));
        assertFalse(ServicioFormalizacion.esOrdenPermitido("' OR 1=1 --", SeccionBandeja.PAGARE));
    }

    /** Cada seccion tiene SUS columnas: una clave valida en una no lo es en otra. */
    @Test
    public void laListaBlancaEsDeCadaSeccion() {
        assertTrue(ServicioFormalizacion.esOrdenPermitido("plazo", SeccionBandeja.FORMALIZAR));
        assertFalse(ServicioFormalizacion.esOrdenPermitido("plazo", SeccionBandeja.DESEMBOLSO));
        assertTrue(ServicioFormalizacion.esOrdenPermitido("formalizado", SeccionBandeja.DESEMBOLSO));
        assertFalse(ServicioFormalizacion.esOrdenPermitido("formalizado", SeccionBandeja.PAGARE));
        assertTrue(ServicioFormalizacion.esOrdenPermitido("cancelada", SeccionBandeja.PAGARE));
        assertFalse(ServicioFormalizacion.esOrdenPermitido("cancelada", SeccionBandeja.FORMALIZAR));
    }

    @Test
    public void elSentidoEsAscendenteSalvoQueSePidaDesc() {
        assertEquals("asc", ServicioFormalizacion.sentidoAplicado(null));
        assertEquals("asc", ServicioFormalizacion.sentidoAplicado(""));
        assertEquals("asc", ServicioFormalizacion.sentidoAplicado("cualquier cosa"));
        assertEquals("desc", ServicioFormalizacion.sentidoAplicado("desc"));
        assertEquals("desc", ServicioFormalizacion.sentidoAplicado("DESC"));
        assertEquals("asc", ServicioFormalizacion.sentidoAplicado("asc"));
    }

    // ==================================================================
    // Seccion 1: solicitudes para formalizar
    // ==================================================================

    @Test
    public void sinParametrosLaSeccionUnoOrdenaPorFechaDeAprobacionAscendente() {
        SolicitudCredito vieja = solicitud("SOL-3", 1L, dia(1), "1000000", 12);
        SolicitudCredito nueva = solicitud("SOL-1", 2L, dia(9), "2000000", 24);
        List<SolicitudCredito> ordenadas = servicio.ordenarParaFormalizar(
                Arrays.asList(nueva, vieja), new HashMap<Long, Persona>(), null, null);
        assertEquals("SOL-3", ordenadas.get(0).getCodigoSolicitud());
        assertEquals("SOL-1", ordenadas.get(1).getCodigoSolicitud());
    }

    /** B177: el nulo va al final en los DOS sentidos -- una fila a la que le falta ese dato nunca
     * encabeza el listado. Este es el caso que encontro el defecto en Solicitudes. */
    @Test
    public void laSolicitudSinFechaDeAprobacionVaAlFinalEnLosDosSentidos() {
        SolicitudCredito conFecha = solicitud("SOL-CON", 1L, dia(5), "1000000", 12);
        SolicitudCredito otraConFecha = solicitud("SOL-CON2", 2L, dia(7), "1000000", 12);
        SolicitudCredito sinFecha = solicitud("SOL-SIN", 3L, null, "1000000", 12);
        List<SolicitudCredito> entrada = Arrays.asList(sinFecha, conFecha, otraConFecha);

        List<SolicitudCredito> ascendente = servicio.ordenarParaFormalizar(
                entrada, new HashMap<Long, Persona>(), "aprobada", "asc");
        assertEquals("SOL-SIN", ascendente.get(2).getCodigoSolicitud());

        List<SolicitudCredito> descendente = servicio.ordenarParaFormalizar(
                entrada, new HashMap<Long, Persona>(), "aprobada", "desc");
        assertEquals("SOL-SIN", descendente.get(2).getCodigoSolicitud());
    }

    /** El plazo es Integer y puede venir null: tiene que ir al final, no tirar NullPointerException
     * al desenvolverlo (se encontro escribiendo este mismo item). */
    @Test
    public void laSolicitudSinPlazoNoRompeElOrdenYVaAlFinal() {
        SolicitudCredito sinPlazo = solicitud("SOL-SIN", 1L, dia(1), "1000000", null);
        SolicitudCredito conPlazo = solicitud("SOL-CON", 2L, dia(2), "1000000", 12);
        List<SolicitudCredito> entrada = Arrays.asList(sinPlazo, conPlazo);
        assertEquals("SOL-SIN", servicio.ordenarParaFormalizar(
                entrada, new HashMap<Long, Persona>(), "plazo", "asc").get(1).getCodigoSolicitud());
        assertEquals("SOL-SIN", servicio.ordenarParaFormalizar(
                entrada, new HashMap<Long, Persona>(), "plazo", "desc").get(1).getCodigoSolicitud());
    }

    /** Las personas se ordenan por apellidos y nombres normalizados: "Avila" y "Ávila" juntos. */
    @Test
    public void elClienteSeOrdenaPorApellidoSinQueLaTildeLoMueva() {
        Map<Long, Persona> clientes = new HashMap<>();
        clientes.put(1L, persona("Zulema", "Zarate"));
        clientes.put(2L, persona("Ana", "Ávila"));
        clientes.put(3L, persona("Bruno", "Benitez"));
        List<SolicitudCredito> ordenadas = servicio.ordenarParaFormalizar(
                Arrays.asList(solicitud("SOL-Z", 1L, dia(1), "1000000", 12),
                        solicitud("SOL-A", 2L, dia(2), "1000000", 12),
                        solicitud("SOL-B", 3L, dia(3), "1000000", 12)),
                clientes, "cliente", "asc");
        assertEquals("SOL-A", ordenadas.get(0).getCodigoSolicitud());
        assertEquals("SOL-B", ordenadas.get(1).getCodigoSolicitud());
        assertEquals("SOL-Z", ordenadas.get(2).getCodigoSolicitud());
    }

    /** Un cliente que no esta en el mapa es un dato que falta: al final en los dos sentidos. */
    @Test
    public void laSolicitudSinClienteVaAlFinalEnLosDosSentidos() {
        Map<Long, Persona> clientes = new HashMap<>();
        clientes.put(1L, persona("Ana", "Avila"));
        List<SolicitudCredito> entrada = Arrays.asList(
                solicitud("SOL-SIN", 99L, dia(1), "1000000", 12),
                solicitud("SOL-CON", 1L, dia(2), "1000000", 12));
        assertEquals("SOL-SIN", servicio.ordenarParaFormalizar(entrada, clientes, "cliente", "asc")
                .get(1).getCodigoSolicitud());
        assertEquals("SOL-SIN", servicio.ordenarParaFormalizar(entrada, clientes, "cliente", "desc")
                .get(1).getCodigoSolicitud());
    }

    /**
     * Dos filas con el mismo valor desempatan por el orden de siempre de la seccion, y NO quedan
     * en el orden que a PostgreSQL se le ocurra: con paginas, dos filas que se intercambian entre
     * dos cargas se ven como una fila repetida o una que desaparece (B157).
     */
    @Test
    public void dosSolicitudesConElMismoMontoDesempatanPorLaFechaDeAprobacion() {
        SolicitudCredito nueva = solicitud("SOL-NUEVA", 1L, dia(9), "5000000", 12);
        SolicitudCredito vieja = solicitud("SOL-VIEJA", 2L, dia(2), "5000000", 12);
        List<SolicitudCredito> ordenadas = servicio.ordenarParaFormalizar(
                Arrays.asList(nueva, vieja), new HashMap<Long, Persona>(), "monto", "asc");
        assertEquals("SOL-VIEJA", ordenadas.get(0).getCodigoSolicitud());
        assertEquals("SOL-NUEVA", ordenadas.get(1).getCodigoSolicitud());
    }

    @Test
    public void ordenarNoModificaLaListaQueRecibe() {
        SolicitudCredito una = solicitud("SOL-1", 1L, dia(9), "1000000", 12);
        SolicitudCredito otra = solicitud("SOL-2", 2L, dia(1), "1000000", 12);
        List<SolicitudCredito> entrada = new ArrayList<>(Arrays.asList(una, otra));
        servicio.ordenarParaFormalizar(entrada, new HashMap<Long, Persona>(), "aprobada", "asc");
        assertEquals("SOL-1", entrada.get(0).getCodigoSolicitud());
    }

    @Test
    public void unaListaNulaDevuelveListaVaciaYNoRompeLaPantalla() {
        assertTrue(servicio.ordenarParaFormalizar(null, null, "monto", "asc").isEmpty());
        assertTrue(servicio.ordenarPrestamosDeBandeja(null, null, "monto", "asc",
                SeccionBandeja.DESEMBOLSO).isEmpty());
    }

    // ==================================================================
    // Secciones 2 y 3: prestamos
    // ==================================================================

    @Test
    public void sinParametrosLaSeccionDosOrdenaPorFechaDeFormalizacionAscendente() {
        PrestamoCredito nuevo = prestamo(1L, "CC-2026-00099", "1000000", dia(9), null);
        PrestamoCredito viejo = prestamo(2L, "CC-2026-00001", "1000000", dia(1), null);
        List<PrestamoCredito> ordenados = servicio.ordenarPrestamosDeBandeja(
                Arrays.asList(nuevo, viejo), new HashMap<Long, Persona>(), null, null,
                SeccionBandeja.DESEMBOLSO);
        assertEquals("CC-2026-00001", ordenados.get(0).getIdentificadorOperacion());
    }

    @Test
    public void sinParametrosLaSeccionTresOrdenaPorFechaDeCancelacionAscendente() {
        PrestamoCredito nuevo = prestamo(1L, "CC-2026-00099", "1000000", null, dia(9));
        PrestamoCredito viejo = prestamo(2L, "CC-2026-00001", "1000000", null, dia(1));
        List<PrestamoCredito> ordenados = servicio.ordenarPrestamosDeBandeja(
                Arrays.asList(nuevo, viejo), new HashMap<Long, Persona>(), null, null,
                SeccionBandeja.PAGARE);
        assertEquals("CC-2026-00001", ordenados.get(0).getIdentificadorOperacion());
    }

    /** B177, del lado de los prestamos. */
    @Test
    public void elPrestamoSinFechaDeFormalizacionVaAlFinalEnLosDosSentidos() {
        PrestamoCredito sinFecha = prestamo(1L, "CC-SIN", "1000000", null, null);
        PrestamoCredito conFecha = prestamo(2L, "CC-CON", "1000000", dia(3), null);
        PrestamoCredito otroConFecha = prestamo(3L, "CC-CON2", "1000000", dia(5), null);
        List<PrestamoCredito> entrada = Arrays.asList(sinFecha, conFecha, otroConFecha);
        assertEquals("CC-SIN", servicio.ordenarPrestamosDeBandeja(entrada, null, "formalizado", "asc",
                SeccionBandeja.DESEMBOLSO).get(2).getIdentificadorOperacion());
        assertEquals("CC-SIN", servicio.ordenarPrestamosDeBandeja(entrada, null, "formalizado", "desc",
                SeccionBandeja.DESEMBOLSO).get(2).getIdentificadorOperacion());
    }

    /** El cliente de un prestamo se busca por ID DE PRESTAMO, no por id de cliente: asi arma el
     * mapa el controlador, porque un prestamo llega a su cliente por la solicitud. */
    @Test
    public void elClienteDelPrestamoSeBuscaPorElIdDelPrestamo() {
        Map<Long, Persona> porPrestamo = new HashMap<>();
        porPrestamo.put(10L, persona("Ana", "Avila"));
        porPrestamo.put(20L, persona("Zulema", "Zarate"));
        List<PrestamoCredito> ordenados = servicio.ordenarPrestamosDeBandeja(
                Arrays.asList(prestamo(20L, "CC-Z", "1000000", dia(1), null),
                        prestamo(10L, "CC-A", "1000000", dia(2), null)),
                porPrestamo, "cliente", "asc", SeccionBandeja.DESEMBOLSO);
        assertEquals("CC-A", ordenados.get(0).getIdentificadorOperacion());
        assertEquals("CC-Z", ordenados.get(1).getIdentificadorOperacion());
    }

    @Test
    public void elMontoDelPrestamoOrdenaComoNumeroYNoComoTexto() {
        List<PrestamoCredito> ordenados = servicio.ordenarPrestamosDeBandeja(
                Arrays.asList(prestamo(1L, "CC-9", "9000000", dia(1), null),
                        prestamo(2L, "CC-10", "10000000", dia(2), null),
                        prestamo(3L, "CC-2", "2000000", dia(3), null)),
                null, "monto", "asc", SeccionBandeja.DESEMBOLSO);
        assertEquals("CC-2", ordenados.get(0).getIdentificadorOperacion());
        assertEquals("CC-9", ordenados.get(1).getIdentificadorOperacion());
        assertEquals("CC-10", ordenados.get(2).getIdentificadorOperacion());
    }

    /** En PAGARE el desempate es la fecha de cancelacion, que no tiene columna propia pero sigue
     * siendo el orden de siempre de esa seccion. */
    @Test
    public void dosPrestamosConElMismoMontoDesempatanPorElOrdenDeSuSeccion() {
        PrestamoCredito nuevo = prestamo(1L, "CC-NUEVO", "5000000", null, dia(9));
        PrestamoCredito viejo = prestamo(2L, "CC-VIEJO", "5000000", null, dia(2));
        List<PrestamoCredito> ordenados = servicio.ordenarPrestamosDeBandeja(
                Arrays.asList(nuevo, viejo), null, "monto", "asc", SeccionBandeja.PAGARE);
        assertEquals("CC-VIEJO", ordenados.get(0).getIdentificadorOperacion());
    }

    // ==================================================================
    // Ayudantes
    // ==================================================================

    private static LocalDateTime dia(int dia) {
        return LocalDateTime.of(2026, 11, dia, 10, 0);
    }

    private static Persona persona(String nombres, String apellidos) {
        Persona persona = new Persona();
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        return persona;
    }

    private static SolicitudCredito solicitud(String codigo, long idCliente, LocalDateTime decision,
            String monto, Integer plazo) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setCodigoSolicitud(codigo);
        solicitud.setIdCliente(idCliente);
        solicitud.setFechaDecision(decision);
        solicitud.setMontoAprobado(new BigDecimal(monto));
        solicitud.setPlazoAprobadoMeses(plazo);
        return solicitud;
    }

    private static PrestamoCredito prestamo(long idPrestamo, String identificador, String monto,
            LocalDateTime formalizacion, LocalDateTime cancelacion) {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(idPrestamo);
        prestamo.setIdentificadorOperacion(identificador);
        prestamo.setMontoAprobado(new BigDecimal(monto));
        prestamo.setFechaFormalizacion(formalizacion);
        prestamo.setFechaFinalizacionFinanciera(cancelacion);
        return prestamo;
    }
}
