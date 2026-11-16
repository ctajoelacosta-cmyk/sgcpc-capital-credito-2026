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
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;

/**
 * B167 (14/11/2026): el orden del listado de solicitudes. Es puro (sin base), así que se prueba
 * acá, al lado de {@code filtrarPorTexto}.
 *
 * <p>Lo que estas pruebas cuidan, que es lo que se puede romper de verdad: que la lista blanca no
 * deje pasar nada más, que el orden por defecto siga siendo el de siempre (fecha descendente), que
 * el desempate exista (sin él, dos filas iguales se intercambian entre dos cargas y con páginas se
 * ve como una fila repetida), que el nombre del cliente se compare sin tildes, y que la lista que
 * llega no se modifique.</p>
 *
 * <p><b>Lo que NO prueba</b>: que la pantalla ordene. Eso lo prueba Joel (DOGMA 1).</p>
 */
public class ServicioSolicitudesOrdenTest {

    private final ServicioSolicitudes servicio = new ServicioSolicitudes();

    private SolicitudCredito solicitud(String codigo, String fecha, long idCliente, String monto,
            int plazo, EstadoSolicitud estado) {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setCodigoSolicitud(codigo);
        solicitud.setFechaSolicitud(LocalDateTime.parse(fecha));
        solicitud.setIdCliente(idCliente);
        solicitud.setMontoSolicitado(new BigDecimal(monto));
        solicitud.setPlazoSolicitadoMeses(plazo);
        solicitud.setEstadoSolicitud(estado);
        return solicitud;
    }

    private Persona persona(String nombres, String apellidos) {
        Persona persona = new Persona();
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        return persona;
    }

    /** Tres solicitudes con todo distinto, para poder ordenar por cualquier columna. */
    private List<SolicitudCredito> tresSolicitudes() {
        return new ArrayList<>(Arrays.asList(
                solicitud("SOL-2026-0002", "2026-11-10T09:00:00", 1L, "5000000", 12, EstadoSolicitud.APROBADA),
                solicitud("SOL-2026-0001", "2026-11-12T09:00:00", 2L, "1000000", 24, EstadoSolicitud.BORRADOR),
                solicitud("SOL-2026-0003", "2026-11-11T09:00:00", 3L, "9000000", 6, EstadoSolicitud.RECHAZADA)));
    }

    private Map<Long, Persona> tresClientes() {
        Map<Long, Persona> clientes = new HashMap<>();
        clientes.put(1L, persona("Juan", "Zárate"));
        clientes.put(2L, persona("Ana", "Ávila"));
        clientes.put(3L, persona("Luis", "Méndez"));
        return clientes;
    }

    private List<String> codigos(List<SolicitudCredito> solicitudes) {
        List<String> codigos = new ArrayList<>();
        for (SolicitudCredito solicitud : solicitudes) {
            codigos.add(solicitud.getCodigoSolicitud());
        }
        return codigos;
    }

    /**
     * Ordena como la pantalla: con los parámetros CRUDOS del pedido, pasados por las dos reglas del
     * servicio. Es el camino que recorre el controlador, así que es el que hay que probar -- probar
     * {@code ordenarParaListado} con valores ya resueltos no diría nada sobre el orden que se ve.
     */
    private List<SolicitudCredito> ordenarComoLaPantalla(List<SolicitudCredito> solicitudes,
            Map<Long, Persona> clientes, String clavePedida, String sentidoPedido) {
        return servicio.ordenarParaListado(solicitudes, clientes,
                ServicioSolicitudes.ordenAplicado(clavePedida),
                ServicioSolicitudes.sentidoAplicado(clavePedida, sentidoPedido));
    }

    // ---- El orden por defecto es el de siempre: fecha descendente ----

    @Test
    public void testSinParametrosOrdenaPorFechaDescendente() {
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0003", "SOL-2026-0002"),
                codigos(ordenarComoLaPantalla(tresSolicitudes(), tresClientes(), null, null)));
    }

    @Test
    public void testUnaClaveDesconocidaCaeEnElOrdenPorDefecto() {
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0003", "SOL-2026-0002"),
                codigos(ordenarComoLaPantalla(tresSolicitudes(), tresClientes(), "monto; DROP TABLE", null)));
    }

    /** El primer clic en un encabezado es ascendente, aunque no mande sentido. */
    @Test
    public void testUnaColumnaPedidaSinSentidoEsAscendente() {
        assertEquals("asc", ServicioSolicitudes.sentidoAplicado("monto", null));
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002", "SOL-2026-0003"),
                codigos(ordenarComoLaPantalla(tresSolicitudes(), tresClientes(), "monto", null)));
    }

    @Test
    public void testSinColumnaElSentidoEsElDeSiempre() {
        assertEquals("desc", ServicioSolicitudes.sentidoAplicado(null, null));
        assertEquals("desc", ServicioSolicitudes.sentidoAplicado("destino", null));
        assertEquals("desc", ServicioSolicitudes.SENTIDO_POR_DEFECTO);
    }

    @Test
    public void testUnSentidoExplicitoGana() {
        assertEquals("asc", ServicioSolicitudes.sentidoAplicado(null, "asc"));
        assertEquals("desc", ServicioSolicitudes.sentidoAplicado("monto", "DESC"));
        assertEquals("asc", ServicioSolicitudes.sentidoAplicado("monto", "descendente; --"));
    }

    @Test
    public void testOrdenAplicadoFiltraPorLaListaBlanca() {
        assertEquals("monto", ServicioSolicitudes.ordenAplicado("monto"));
        assertEquals("fecha", ServicioSolicitudes.ordenAplicado(null));
        assertEquals("fecha", ServicioSolicitudes.ordenAplicado("fecha_solicitud"));
    }

    @Test
    public void testFechaAscendenteInvierteElOrden() {
        assertEquals(Arrays.asList("SOL-2026-0002", "SOL-2026-0003", "SOL-2026-0001"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "fecha", "asc")));
    }

    // ---- Las seis columnas ----

    @Test
    public void testOrdenPorCodigo() {
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002", "SOL-2026-0003"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "codigo", "asc")));
    }

    @Test
    public void testOrdenPorMontoDescendente() {
        assertEquals(Arrays.asList("SOL-2026-0003", "SOL-2026-0002", "SOL-2026-0001"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "monto", "desc")));
    }

    @Test
    public void testOrdenPorPlazo() {
        assertEquals(Arrays.asList("SOL-2026-0003", "SOL-2026-0002", "SOL-2026-0001"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "plazo", "asc")));
    }

    /** El estado se ordena por su ETIQUETA, que es lo que el usuario lee: Aprobada, Borrador, Rechazada. */
    @Test
    public void testOrdenPorEstadoUsaLaEtiquetaYNoElOrdenDelEnum() {
        assertEquals(Arrays.asList("SOL-2026-0002", "SOL-2026-0001", "SOL-2026-0003"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "estado", "asc")));
    }

    /** Ávila, Méndez, Zárate: si las tildes contaran, "Ávila" se iría al final. */
    @Test
    public void testOrdenPorClienteIgnoraLasTildes() {
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0003", "SOL-2026-0002"),
                codigos(servicio.ordenarParaListado(tresSolicitudes(), tresClientes(), "cliente", "asc")));
    }

    @Test
    public void testUnaSolicitudSinClienteEnElMapaVaAlFinal() {
        List<SolicitudCredito> solicitudes = tresSolicitudes();
        Map<Long, Persona> clientes = tresClientes();
        clientes.remove(2L);   // SOL-2026-0001 se queda sin cliente
        List<String> ordenadas = codigos(servicio.ordenarParaListado(solicitudes, clientes, "cliente", "asc"));
        assertEquals("SOL-2026-0001", ordenadas.get(2));
    }

    // ---- El desempate, que es lo que nadie escribe y lo que se ve como "fila repetida" ----

    @Test
    public void testConElMismoValorDesempataPorFechaDescendente() {
        List<SolicitudCredito> mismoMonto = new ArrayList<>(Arrays.asList(
                solicitud("SOL-A", "2026-11-10T09:00:00", 1L, "5000000", 12, EstadoSolicitud.APROBADA),
                solicitud("SOL-B", "2026-11-12T09:00:00", 1L, "5000000", 12, EstadoSolicitud.APROBADA),
                solicitud("SOL-C", "2026-11-11T09:00:00", 1L, "5000000", 12, EstadoSolicitud.APROBADA)));
        assertEquals(Arrays.asList("SOL-B", "SOL-C", "SOL-A"),
                codigos(servicio.ordenarParaListado(mismoMonto, tresClientes(), "monto", "asc")));
    }

    // ---- La lista que llega no se toca, y los bordes ----

    @Test
    public void testNoModificaLaListaRecibida() {
        List<SolicitudCredito> original = tresSolicitudes();
        List<String> antes = codigos(original);
        servicio.ordenarParaListado(original, tresClientes(), "codigo", "asc");
        assertEquals(antes, codigos(original));
    }

    @Test
    public void testListaNulaDevuelveVacia() {
        assertTrue(servicio.ordenarParaListado(null, tresClientes(), "codigo", "asc").isEmpty());
    }

    @Test
    public void testMapaDeClientesNuloNoRompe() {
        assertEquals(3, servicio.ordenarParaListado(tresSolicitudes(), null, "cliente", "asc").size());
    }

    // ---- La lista blanca ----

    @Test
    public void testEsOrdenPermitidoSoloAceptaLasSeisColumnas() {
        for (String clave : new String[] {"codigo", "fecha", "cliente", "monto", "plazo", "estado"}) {
            assertTrue(clave + " tendría que estar permitida", ServicioSolicitudes.esOrdenPermitido(clave));
        }
        assertFalse(ServicioSolicitudes.esOrdenPermitido(null));
        assertFalse(ServicioSolicitudes.esOrdenPermitido(""));
        assertFalse(ServicioSolicitudes.esOrdenPermitido("CODIGO"));
        assertFalse(ServicioSolicitudes.esOrdenPermitido("destino"));
        assertFalse(ServicioSolicitudes.esOrdenPermitido("fecha_solicitud"));
    }

    @Test
    public void testElOrdenPorDefectoEsLaFecha() {
        assertEquals("fecha", ServicioSolicitudes.ORDEN_POR_DEFECTO);
        assertTrue(ServicioSolicitudes.esOrdenPermitido(ServicioSolicitudes.ORDEN_POR_DEFECTO));
    }

    // ---- B177 (14/11/2026): los nulos, en los DOS sentidos ----

    /**
     * B177: lo que estas dos pruebas fijan, y que ninguno de los 19 casos anteriores miraba: en
     * descendente el sentido se aplicaba invirtiendo el resultado completo, así que invertía
     * también el criterio de "nulos al final" y una fila sin ese dato pasaba a <b>encabezar</b> el
     * listado. Son las dos pruebas que agarraron el mismo defecto en Aprobaciones (B175).
     */
    @Test
    public void testSinClienteEnElMapaLaFilaVaAlFinalEnLosDosSentidos() {
        SolicitudCredito conCliente = solicitud("SOL-2026-0001", "2026-11-12T09:00:00", 1L, "1000000",
                12, EstadoSolicitud.APROBADA);
        SolicitudCredito sinCliente = solicitud("SOL-2026-0002", "2026-11-10T09:00:00", 99L, "2000000",
                24, EstadoSolicitud.APROBADA);
        Map<Long, Persona> clientes = new HashMap<>();
        clientes.put(1L, persona("Ana", "Ávila"));
        List<SolicitudCredito> entrada = Arrays.asList(sinCliente, conCliente);
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002"),
                codigos(servicio.ordenarParaListado(entrada, clientes, "cliente", "asc")));
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002"),
                codigos(servicio.ordenarParaListado(entrada, clientes, "cliente", "desc")));
    }

    @Test
    public void testSinMontoLaFilaVaAlFinalEnLosDosSentidos() {
        SolicitudCredito conMonto = solicitud("SOL-2026-0001", "2026-11-12T09:00:00", 1L, "1000000",
                12, EstadoSolicitud.APROBADA);
        SolicitudCredito sinMonto = solicitud("SOL-2026-0002", "2026-11-10T09:00:00", 2L, "2000000",
                24, EstadoSolicitud.APROBADA);
        sinMonto.setMontoSolicitado(null);
        List<SolicitudCredito> entrada = Arrays.asList(sinMonto, conMonto);
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002"),
                codigos(servicio.ordenarParaListado(entrada, tresClientes(), "monto", "asc")));
        assertEquals(Arrays.asList("SOL-2026-0001", "SOL-2026-0002"),
                codigos(servicio.ordenarParaListado(entrada, tresClientes(), "monto", "desc")));
    }

    /** Y el desempate sigue siendo el de siempre (la fecha, de la más nueva a la más vieja),
     * ahora que pasa por el mismo camino que el resto de las comparaciones. */
    @Test
    public void testElDesempatePorFechaSigueSiendoDescendente() {
        SolicitudCredito vieja = solicitud("SOL-2026-0001", "2026-11-10T09:00:00", 1L, "1000000",
                12, EstadoSolicitud.APROBADA);
        SolicitudCredito nueva = solicitud("SOL-2026-0002", "2026-11-14T09:00:00", 2L, "1000000",
                12, EstadoSolicitud.APROBADA);
        assertEquals(Arrays.asList("SOL-2026-0002", "SOL-2026-0001"),
                codigos(servicio.ordenarParaListado(Arrays.asList(vieja, nueva), tresClientes(), "monto", "asc")));
        assertEquals(Arrays.asList("SOL-2026-0002", "SOL-2026-0001"),
                codigos(servicio.ordenarParaListado(Arrays.asList(nueva, vieja), tresClientes(), "monto", "desc")));
    }
}
