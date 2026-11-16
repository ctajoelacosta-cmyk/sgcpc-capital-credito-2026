package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.FilaInformeCliente;
import py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;
import py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroPrestamos;
import py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPersona;
import py.com.capitalcredito.sgcpc.modelos.Persona;

/**
 * Bloque 7/E10 (D-135): métodos puros de ServicioInformes -- ninguno toca la base (los que
 * generan el PDF/auditan sí la necesitan, y esos no se prueban acá, mismo criterio que el resto
 * del proyecto).
 */
public class ServicioInformesTest {

    private ServicioInformes servicio;

    @Before
    public void antesDeCadaPrueba() {
        servicio = new ServicioInformes();
    }

    @Test
    public void testConstruirTextoFiltrosClientesConTodo() {
        FiltroClientes filtro = new FiltroClientes();
        filtro.setAltaDesde(LocalDate.of(2026, 9, 1));
        filtro.setAltaHasta(LocalDate.of(2026, 9, 30));
        filtro.setSituacionLaboral("COMERCIANTE");
        filtro.setPrestamoActivo("SI");
        filtro.setTexto("andrea");
        String texto = servicio.construirTextoFiltrosClientes(filtro, "Activos", "Central", "San Lorenzo");
        assertEquals("Alta del 01/09/2026 al 30/09/2026 · Activos · Central / San Lorenzo · "
                + "Comerciante / negocio propio · Con préstamo activo · Texto: \"andrea\"", texto);
    }

    @Test
    public void testConstruirTextoFiltrosClientesSinNada() {
        FiltroClientes filtro = new FiltroClientes();
        String texto = servicio.construirTextoFiltrosClientes(filtro, "Activos", null, null);
        assertEquals("Todas las fechas de alta · Activos · Todos los departamentos · "
                + "Todas las situaciones laborales · Con o sin préstamo activo", texto);
    }

    @Test
    public void testConstruirTextoFiltrosClientesSoloDesde() {
        FiltroClientes filtro = new FiltroClientes();
        filtro.setAltaDesde(LocalDate.of(2026, 9, 1));
        filtro.setPrestamoActivo("NO");
        String texto = servicio.construirTextoFiltrosClientes(filtro, "Todos", "Central", null);
        assertEquals("Alta desde 01/09/2026 · Todos · Central · Todas las situaciones laborales · Sin préstamo activo", texto);
    }

    @Test
    public void testCalcularSubtotalesPorSituacionLaboral() {
        List<Persona> personas = Arrays.asList(
                personaDePrueba(1L),
                personaDePrueba(2L),
                personaDePrueba(3L));
        // Normalización de personas, grupo Laborales (16/10/2026): la situación laboral ya no
        // está en Persona -- se arma aparte el Map<Long, String> situacionLaboralPorPersona.
        Map<Long, String> situacionLaboralPorPersona = new HashMap<>();
        situacionLaboralPorPersona.put(1L, "COMERCIANTE");
        situacionLaboralPorPersona.put(2L, "COMERCIANTE");
        situacionLaboralPorPersona.put(3L, "ASALARIADO_SECTOR_PRIVADO");
        Map<String, Integer> subtotales = servicio.calcularSubtotalesPorSituacionLaboral(personas, situacionLaboralPorPersona);
        assertEquals(2, subtotales.size());
        assertEquals(Integer.valueOf(1), subtotales.get("Asalariado (sector privado)"));
        assertEquals(Integer.valueOf(2), subtotales.get("Comerciante / negocio propio"));
    }

    @Test
    public void testCalcularSubtotalesPorCiudadConNulos() {
        List<Persona> personas = Arrays.asList(
                personaDePrueba(1L),
                personaDePrueba(2L));
        Map<Long, Long> idCiudadPorPersona = new HashMap<>();
        idCiudadPorPersona.put(1L, 10L);
        Map<Long, String> ciudadPorId = new HashMap<>();
        ciudadPorId.put(10L, "Luque");
        Map<String, Integer> subtotales = servicio.calcularSubtotalesPorCiudad(personas, idCiudadPorPersona, ciudadPorId);
        assertEquals(Integer.valueOf(1), subtotales.get("Luque"));
        assertEquals(Integer.valueOf(1), subtotales.get("Sin ciudad"));
    }

    @Test
    public void testConstruirTextoSubtotalesVacio() {
        assertEquals("(sin datos)", servicio.construirTextoSubtotales(new java.util.TreeMap<String, Integer>()));
    }

    @Test
    public void testConstruirTextoSubtotalesConDatos() {
        Map<String, Integer> subtotales = new java.util.TreeMap<>();
        subtotales.put("Luque", 1);
        subtotales.put("San Lorenzo", 2);
        assertEquals("Luque: 1\nSan Lorenzo: 2", servicio.construirTextoSubtotales(subtotales));
    }

    @Test
    public void testConstruirFilasInformeClientes() {
        Persona persona = personaDePrueba(5L);
        persona.setCedulaIdentidad("1234567");
        persona.setNombres("Andrea");
        persona.setApellidos("Benegas");
        persona.setTelefonoWhatsapp("0981111222");
        persona.setFechaRegistro(LocalDateTime.of(2026, 9, 5, 10, 0));
        Map<Long, Long> idCiudadPorPersona = new HashMap<>();
        idCiudadPorPersona.put(5L, 10L);
        Map<Long, String> ciudadPorId = new HashMap<>();
        ciudadPorId.put(10L, "Luque");
        // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que la ciudad.
        Map<Long, String> situacionLaboralPorPersona = new HashMap<>();
        situacionLaboralPorPersona.put(5L, "COMERCIANTE");
        Map<Long, Integer> prestamosActivosPorId = new HashMap<>();
        prestamosActivosPorId.put(5L, 2);

        List<FilaInformeCliente> filas = servicio.construirFilasInformeClientes(
                Arrays.asList(persona), idCiudadPorPersona, ciudadPorId, situacionLaboralPorPersona, prestamosActivosPorId);

        assertEquals(1, filas.size());
        FilaInformeCliente fila = filas.get(0);
        assertEquals("1.234.567", fila.getCedula());
        assertEquals("Benegas Andrea", fila.getNombreCompleto());
        assertEquals("Luque", fila.getCiudad());
        assertEquals("Comerciante / negocio propio", fila.getSituacionLaboral());
        assertEquals("05/09/2026", fila.getFechaAlta());
        assertEquals(2, fila.getPrestamosActivos());
        assertEquals("Activa", fila.getEstado());
    }

    @Test
    public void testConstruirFilasInformeClientesSinCiudadNiPrestamos() {
        Persona persona = personaDePrueba(6L);
        persona.setCedulaIdentidad("7654321");
        persona.setNombres("Juan");
        persona.setApellidos("Pérez");

        List<FilaInformeCliente> filas = servicio.construirFilasInformeClientes(
                Arrays.asList(persona), new HashMap<Long, Long>(), new HashMap<Long, String>(),
                new HashMap<Long, String>(), new HashMap<Long, Integer>());

        FilaInformeCliente fila = filas.get(0);
        assertEquals("-", fila.getCiudad());
        assertEquals(0, fila.getPrestamosActivos());
        assertEquals("-", fila.getFechaAlta());
        assertEquals("-", fila.getSituacionLaboral());
    }

    // B1 (Bloque 7/E10, pedido de Joel): texto de filtros y resumen por estado de /prestamos --
    // mismo criterio que los de Clientes de arriba, ninguno toca la base.

    @Test
    public void testConstruirTextoFiltrosPrestamosConTodo() {
        FiltroPrestamos filtro = new FiltroPrestamos();
        filtro.setDesembolsoDesde(LocalDate.of(2026, 9, 1));
        filtro.setDesembolsoHasta(LocalDate.of(2026, 9, 30));
        filtro.setTexto("andrea");
        String texto = servicio.construirTextoFiltrosPrestamos(filtro, "Activo");
        assertEquals("Desembolso del 01/09/2026 al 30/09/2026 · Activo · Texto: \"andrea\"", texto);
    }

    @Test
    public void testConstruirTextoFiltrosPrestamosSinNada() {
        FiltroPrestamos filtro = new FiltroPrestamos();
        String texto = servicio.construirTextoFiltrosPrestamos(filtro, "Todos los estados");
        assertEquals("Todas las fechas de desembolso · Todos los estados", texto);
    }

    @Test
    public void testConstruirTextoFiltrosPrestamosSoloDesde() {
        FiltroPrestamos filtro = new FiltroPrestamos();
        filtro.setDesembolsoDesde(LocalDate.of(2026, 9, 1));
        String texto = servicio.construirTextoFiltrosPrestamos(filtro, "En mora");
        assertEquals("Desembolso desde 01/09/2026 · En mora", texto);
    }

    @Test
    public void testConstruirTextoFiltrosPrestamosSoloHasta() {
        FiltroPrestamos filtro = new FiltroPrestamos();
        filtro.setDesembolsoHasta(LocalDate.of(2026, 9, 30));
        String texto = servicio.construirTextoFiltrosPrestamos(filtro, "Todos los estados");
        assertEquals("Desembolso hasta 30/09/2026 · Todos los estados", texto);
    }

    @Test
    public void testConstruirTextoResumenPorEstadoPrestamosVacio() {
        assertEquals("(sin datos)",
                servicio.construirTextoResumenPorEstadoPrestamos(java.util.Collections.<ResumenEstadoPrestamo>emptyList()));
    }

    @Test
    public void testConstruirTextoResumenPorEstadoPrestamosConDatos() {
        List<ResumenEstadoPrestamo> resumen = Arrays.asList(
                new ResumenEstadoPrestamo("Activo", 3, "9.000.000 Gs.", "6.000.000 Gs."),
                new ResumenEstadoPrestamo("En mora", 1, "2.000.000 Gs.", "1.500.000 Gs."));
        String texto = servicio.construirTextoResumenPorEstadoPrestamos(resumen);
        assertEquals("Activo: 3 — monto 9.000.000 Gs. — saldo 6.000.000 Gs.\n"
                + "En mora: 1 — monto 2.000.000 Gs. — saldo 1.500.000 Gs.", texto);
    }

    // B2 (Bloque 7/E10, pedido de Joel): texto de filtros y resumen por estado de /reportes/
    // solicitudes -- mismo criterio que los de Préstamos de arriba, ninguno toca la base.

    @Test
    public void testConstruirTextoFiltrosSolicitudesConTodo() {
        FiltroSolicitudesReporte filtro = new FiltroSolicitudesReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        String texto = servicio.construirTextoFiltrosSolicitudes(filtro, "Aprobada", "Pérez Carlos");
        assertEquals("Solicitud del 01/09/2026 al 30/09/2026 · Aprobada · Pérez Carlos", texto);
    }

    @Test
    public void testConstruirTextoFiltrosSolicitudesTodosLosEstadosYOficiales() {
        FiltroSolicitudesReporte filtro = new FiltroSolicitudesReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        String texto = servicio.construirTextoFiltrosSolicitudes(filtro, "Todos los estados", "Todos los oficiales");
        assertEquals("Solicitud del 01/09/2026 al 30/09/2026 · Todos los estados · Todos los oficiales", texto);
    }

    @Test
    public void testConstruirTextoResumenPorEstadoSolicitudesVacio() {
        assertEquals("(sin datos)",
                servicio.construirTextoResumenPorEstadoSolicitudes(java.util.Collections.<ResumenEstadoSolicitud>emptyList()));
    }

    @Test
    public void testConstruirTextoResumenPorEstadoSolicitudesConDatos() {
        List<ResumenEstadoSolicitud> resumen = Arrays.asList(
                new ResumenEstadoSolicitud("Aprobada", 2, "12.000.000 Gs."),
                new ResumenEstadoSolicitud("Rechazada", 1, "6.000.000 Gs."));
        String texto = servicio.construirTextoResumenPorEstadoSolicitudes(resumen);
        assertEquals("Aprobada: 2 — monto 12.000.000 Gs.\nRechazada: 1 — monto 6.000.000 Gs.", texto);
    }

    // B3 (Bloque 7/E10, pedido de Joel): texto de filtros de /reportes/aprobaciones -- mismo
    // criterio que Solicitudes de arriba, no toca la base.

    @Test
    public void testConstruirTextoFiltrosAprobaciones() {
        FiltroAprobacionesReporte filtro = new FiltroAprobacionesReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        String texto = servicio.construirTextoFiltrosAprobaciones(filtro);
        assertEquals("Decisión del 01/09/2026 al 30/09/2026", texto);
    }

    // B4 (Bloque 7/E10, pedido de Joel): texto de filtros de /reportes/cobros y /reportes/
    // desembolsos -- mismo criterio que arriba, no toca la base.

    @Test
    public void testConstruirTextoFiltrosCobros() {
        FiltroCobrosReporte filtro = new FiltroCobrosReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        assertEquals("Cobro del 01/09/2026 al 30/09/2026", servicio.construirTextoFiltrosCobros(filtro));
    }

    @Test
    public void testConstruirTextoFiltrosDesembolsos() {
        FiltroDesembolsosReporte filtro = new FiltroDesembolsosReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        assertEquals("Desembolso del 01/09/2026 al 30/09/2026", servicio.construirTextoFiltrosDesembolsos(filtro));
    }

    // B5 (Bloque 7/E10, pedido de Joel): texto de filtros de /reportes/caja -- mismo criterio
    // que arriba, no toca la base.
    @Test
    public void testConstruirTextoFiltrosCaja() {
        FiltroCajaReporte filtro = new FiltroCajaReporte();
        filtro.setDesde(LocalDate.of(2026, 9, 1));
        filtro.setHasta(LocalDate.of(2026, 9, 30));
        assertEquals("Cierre del 01/09/2026 al 30/09/2026", servicio.construirTextoFiltrosCaja(filtro));
    }

    // Bloque C, tanda 1 (pedido de Joel): Morosidad -- foto del momento, sin filtro de fecha.
    @Test
    public void testConstruirTextoFiltrosMorosidad() {
        assertEquals("Situación de morosidad al 04/10/2026 (foto del momento)",
                servicio.construirTextoFiltrosMorosidad(LocalDate.of(2026, 10, 4)));
    }

    @Test
    public void testConstruirTextoSubtotalesPorTramoMorosidad() {
        List<py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad> subtotales = java.util.Arrays.asList(
                new py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad("1–30", 2, "550.000 Gs.", "2.700.000 Gs."),
                new py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad("31–60", 1, "100.000 Gs.", "900.000 Gs."));
        assertEquals("1–30: 2 — capital vencido 550.000 Gs. — saldo 2.700.000 Gs.\n"
                + "31–60: 1 — capital vencido 100.000 Gs. — saldo 900.000 Gs.",
                servicio.construirTextoSubtotalesPorTramoMorosidad(subtotales));
    }

    // Bloque C, tanda 1 (pedido de Joel): Cartera -- foto del momento, como Morosidad.
    @Test
    public void testConstruirTextoFiltrosCartera() {
        assertEquals("Cartera (préstamos activos y en mora) al 04/10/2026",
                servicio.construirTextoFiltrosCartera(LocalDate.of(2026, 10, 4)));
    }

    @Test
    public void testConstruirTextoResumenPorEstadoCartera() {
        List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera> resumen = java.util.Arrays.asList(
                new py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera("Activo", 2, "1.300.000 Gs."),
                new py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera("En mora", 1, "500.000 Gs."));
        assertEquals("Activo: 2 — saldo 1.300.000 Gs.\nEn mora: 1 — saldo 500.000 Gs.",
                servicio.construirTextoResumenPorEstadoCartera(resumen));
    }

    // Bloque C, tanda 1 (pedido de Joel): Incobrables -- foto del momento, como Morosidad/Cartera.
    @Test
    public void testConstruirTextoFiltrosIncobrables() {
        assertEquals("Incobrables al 04/10/2026 (foto del momento)",
                servicio.construirTextoFiltrosIncobrables(LocalDate.of(2026, 10, 4)));
    }

    // Bloque C, tanda 1 (pedido de Joel): Vencimientos -- único informe de esta tanda con filtro
    // desde/hasta real.
    @Test
    public void testConstruirTextoFiltrosVencimientos() {
        assertEquals("Vencimiento del 01/09/2026 al 30/09/2026",
                servicio.construirTextoFiltrosVencimientos(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    // Bloque C, tanda 1 (pedido de Joel): Proyección de fondos -- "subtotal por horizonte y
    // total". Cada fila YA es un subtotal acumulado (D-128); el horizonte de 90 días, al incluir
    // los otros dos, ES el total -- buscarHorizonte prueba que se toma esa fila y no otra.
    @Test
    public void testConstruirTextoFiltrosProyeccionFondos() {
        assertEquals("Proyección de fondos al 04/10/2026 (foto del momento)",
                servicio.construirTextoFiltrosProyeccionFondos(LocalDate.of(2026, 10, 4)));
    }

    @Test
    public void testBuscarHorizonteDevuelveElDe90Dias() {
        List<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos> filas = java.util.Arrays.asList(
                new py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos(30, "1.000.000 Gs.", "500.000 Gs.",
                        "0 Gs.", "500.000 Gs.", false),
                new py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos(60, "2.000.000 Gs.", "500.000 Gs.",
                        "0 Gs.", "1.500.000 Gs.", false),
                new py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos(90, "3.000.000 Gs.", "500.000 Gs.",
                        "0 Gs.", "2.500.000 Gs.", false));
        py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos encontrada = ServicioInformes.buscarHorizonte(filas, 90);
        assertEquals(90, encontrada.getHorizonteDias());
        assertEquals("3.000.000 Gs.", encontrada.getIngresosEsperados());
        assertEquals("2.500.000 Gs.", encontrada.getFlujoNeto());
    }

    @Test
    public void testBuscarHorizonteDevuelveNullSiNoExiste() {
        assertEquals(null, ServicioInformes.buscarHorizonte(
                java.util.Collections.<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos>emptyList(), 90));
    }

    // Bloque C, tanda 2 (pedido de Joel): IVA del mes.
    // PA-25 (14/10/2026, hallazgo de Joel, L2-D): "octubre de 2026", no el toString() de
    // YearMonth ("2026-10").
    @Test
    public void testConstruirTextoFiltrosIva() {
        assertEquals("IVA de octubre de 2026", servicio.construirTextoFiltrosIva(java.time.YearMonth.of(2026, 10)));
    }

    // Bloque C, tanda 2 (pedido de Joel): Facturas anuladas.
    @Test
    public void testConstruirTextoFiltrosFacturasAnuladas() {
        assertEquals("Facturas anuladas de octubre de 2026",
                servicio.construirTextoFiltrosFacturasAnuladas(java.time.YearMonth.of(2026, 10)));
    }

    // Bloque C, tanda 2 (pedido de Joel): Resumen mensual y Resumen financiero -- "el PDF con el
    // mismo contenido que la pantalla", sin lista de filas.
    @Test
    public void testConstruirTextoFiltrosResumenMensual() {
        assertEquals("Resumen de octubre de 2026", servicio.construirTextoFiltrosResumenMensual(java.time.YearMonth.of(2026, 10)));
    }

    @Test
    public void testConstruirTextoFiltrosResumenFinanciero() {
        assertEquals("Período del 01/09/2026 al 30/09/2026",
                servicio.construirTextoFiltrosResumenFinanciero(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    public void testConstruirTextoTramos() {
        List<py.com.capitalcredito.sgcpc.dto.SubtotalTramo> tramos = java.util.Arrays.asList(
                new py.com.capitalcredito.sgcpc.dto.SubtotalTramo("Hasta 2.000.000 Gs.", 3, "4.500.000 Gs."),
                new py.com.capitalcredito.sgcpc.dto.SubtotalTramo("2.000.001 a 5.000.000 Gs.", 1, "3.000.000 Gs."));
        assertEquals("Hasta 2.000.000 Gs.: 3 — 4.500.000 Gs.\n2.000.001 a 5.000.000 Gs.: 1 — 3.000.000 Gs.",
                servicio.construirTextoTramos(tramos));
    }

    @Test
    public void testConstruirTextoTramosVacio() {
        assertEquals("Sin solicitudes en el período.",
                servicio.construirTextoTramos(java.util.Collections.<py.com.capitalcredito.sgcpc.dto.SubtotalTramo>emptyList()));
        assertEquals("Sin solicitudes en el período.", servicio.construirTextoTramos(null));
    }

    // E-12 (pedido de Joel): ahora el texto de filtros es por fecha de CANCELACIÓN (no de
    // devolución) y siempre nombra el filtro de estado del pagaré aplicado, aunque sea "Todos".
    @Test
    public void testConstruirTextoFiltrosCierresDocumentalesTodos() {
        assertEquals("Cancelación del 01/09/2026 al 30/09/2026 · Todos",
                servicio.construirTextoFiltrosCierresDocumentales(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        ServicioReportes.FiltroEstadoPagare.TODOS));
    }

    @Test
    public void testConstruirTextoFiltrosCierresDocumentalesDevueltos() {
        assertEquals("Cancelación del 01/09/2026 al 30/09/2026 · Pagaré devuelto",
                servicio.construirTextoFiltrosCierresDocumentales(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        ServicioReportes.FiltroEstadoPagare.DEVUELTOS));
    }

    @Test
    public void testConstruirTextoFiltrosCierresDocumentalesPendientes() {
        assertEquals("Cancelación del 01/09/2026 al 30/09/2026 · Pagaré pendiente de devolución",
                servicio.construirTextoFiltrosCierresDocumentales(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        ServicioReportes.FiltroEstadoPagare.PENDIENTES));
    }

    // Bloque C, tanda 3 (pedido de Joel): Gestiones de cobranza.
    @Test
    public void testConstruirTextoFiltrosGestionesConTodo() {
        assertEquals("Gestión del 01/09/2026 al 30/09/2026 · Llamada · Solo promesas incumplidas",
                servicio.construirTextoFiltrosGestiones(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        "Llamada", true));
    }

    @Test
    public void testConstruirTextoFiltrosGestionesSinTipoNiSoloIncumplidas() {
        assertEquals("Gestión del 01/09/2026 al 30/09/2026 · Todos los tipos",
                servicio.construirTextoFiltrosGestiones(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, false));
    }

    @Test
    public void testConstruirTextoPorTipoVacio() {
        assertEquals("(sin datos)", servicio.construirTextoPorTipo(new java.util.LinkedHashMap<String, Integer>()));
    }

    @Test
    public void testConstruirTextoPorTipoConDatos() {
        Map<String, Integer> porTipo = new java.util.LinkedHashMap<>();
        porTipo.put("Llamada", 3);
        porTipo.put("Visita al local", 1);
        assertEquals("Llamada: 3\nVisita al local: 1", servicio.construirTextoPorTipo(porTipo));
    }

    // Bloque C, tanda 3 (pedido de Joel): Reversiones.
    @Test
    public void testConstruirTextoFiltrosReversiones() {
        assertEquals("Reversión del 01/09/2026 al 30/09/2026",
                servicio.construirTextoFiltrosReversiones(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    // C-3b (pedido de Joel): resumen desglosado por estado -- caso real que reportó ("17
    // reversiones (12 autorizadas, 4 rechazadas, 1 solicitada) -- Monto revertido (solo
    // autorizadas): 49.805.905 Gs.") y el caso singular (1 reversión / 1 solicitada).
    @Test
    public void testConstruirResumenReversionesCasoDeJoel() {
        py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales =
                new py.com.capitalcredito.sgcpc.dto.TotalesReversiones(17, 12, 4, 1, "49.805.905 Gs.");
        assertEquals("17 reversiones (12 autorizadas, 4 rechazadas, 1 solicitada) — "
                + "Monto revertido (solo autorizadas): 49.805.905 Gs.", servicio.construirResumenReversiones(totales));
    }

    @Test
    public void testConstruirResumenReversionesSingular() {
        py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales =
                new py.com.capitalcredito.sgcpc.dto.TotalesReversiones(1, 0, 0, 1, "0 Gs.");
        assertEquals("1 reversión (0 autorizadas, 0 rechazadas, 1 solicitada) — "
                + "Monto revertido (solo autorizadas): 0 Gs.", servicio.construirResumenReversiones(totales));
    }

    // Bloque C, tanda 3 (pedido de Joel): Auditoría.
    @Test
    public void testConstruirTextoFiltrosAuditoriaConTodo() {
        assertEquals("Auditoría del 01/09/2026 al 30/09/2026 · Usuario: Ramón Benítez · Entidad: PRESTAMO"
                        + " · Operación contiene: \"cobr\"",
                servicio.construirTextoFiltrosAuditoria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        "Ramón Benítez", "PRESTAMO", "cobr"));
    }

    @Test
    public void testConstruirTextoFiltrosAuditoriaSinNada() {
        assertEquals("Auditoría del 01/09/2026 al 30/09/2026",
                servicio.construirTextoFiltrosAuditoria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null,
                        null, null));
    }

    @Test
    public void testConstruirAvisoLimiteAuditoriaSinCortar() {
        assertEquals(null, servicio.construirAvisoLimiteAuditoria(120, 120, 500));
    }

    /** B205: el aviso es del PDF (la pantalla ya pagina), habla de USTED -- era el unico "acota"
     * de todo src/main, contra REGLAS_DESARROLLO.md §12 -- y dice DONDE esta el resto, porque
     * "acote el filtro" puede ser imposible: el rango por defecto de la pantalla es un solo dia. */
    @Test
    public void testConstruirAvisoLimiteAuditoriaConCorte() {
        String aviso = servicio.construirAvisoLimiteAuditoria(500, 734, 500);
        assertEquals("Este informe muestra los primeros 500 de 734 registros. Para verlos todos, "
                + "use «Excel/CSV», que los trae completos, o acote el filtro.", aviso);
        assertFalse("ningun imperativo en voseo (REGLAS_DESARROLLO.md §12)", aviso.contains("acotá"));
    }

    // E-15 (pedido de Joel, C-3b): el título deja explícito que los totales son del período
    // completo (2878, el caso real que reportó), no de las filas que entran en el detalle.
    @Test
    public void testConstruirTituloTotalesAuditoriaPlural() {
        assertEquals("Totales del período (2878 registros)", servicio.construirTituloTotalesAuditoria(2878));
    }

    @Test
    public void testConstruirTituloTotalesAuditoriaSingular() {
        assertEquals("Totales del período (1 registro)", servicio.construirTituloTotalesAuditoria(1));
    }

    // Bloque C, tanda 3 (pedido de Joel): Clientes nuevos.
    @Test
    public void testConstruirTextoFiltrosClientesNuevos() {
        assertEquals("Alta del 01/09/2026 al 30/09/2026",
                servicio.construirTextoFiltrosClientesNuevos(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    // ======================================================================
    // C-4.1 (pedido de Joel): Cotización/simulación imprimible -- armado de datos del PDF, pura
    // (nunca toca la base ni recalcula nada del motor; ServicioCotizacion.cotizar ya hizo eso).
    // ======================================================================

    @Test
    public void testConstruirLineaInteresadoConValor() {
        assertEquals("Nombre: Juan Pérez", servicio.construirLineaInteresado("Nombre", "Juan Pérez"));
        assertEquals("Nombre: Juan Pérez", servicio.construirLineaInteresado("Nombre", "  Juan Pérez  "));
        assertEquals("C.I.: 1.234.567", servicio.construirLineaInteresado("C.I.", "1.234.567"));
    }

    @Test
    public void testConstruirLineaInteresadoVacioONulo() {
        assertEquals("Nombre: ______________", servicio.construirLineaInteresado("Nombre", null));
        assertEquals("Nombre: ______________", servicio.construirLineaInteresado("Nombre", ""));
        assertEquals("C.I.: ______________", servicio.construirLineaInteresado("C.I.", "   "));
    }

    @Test
    public void testConstruirLeyendaCotizacion() {
        assertEquals("Simulación informativa sin validez contractual. Las condiciones finales dependen de la "
                + "evaluación crediticia y pueden variar según la fecha de desembolso. Validez: 7 días.",
                servicio.construirLeyendaCotizacion(7));
    }

    // C-4.2 Paso 0 (pedido de Joel): "Tasa del período" con 4 decimales, igual que la TNA en
    // pantalla -- antes salía con 8 (2,01177635 %), la escala intermedia de calcularTasaMensual
    // (pensada para encadenar cálculos, no para mostrar).
    @Test
    public void testRedondearTasaPeriodo() {
        java.math.BigDecimal tasaConEscalaIntermedia = new java.math.BigDecimal("0.0201177635");
        assertEquals("2,0118 %",
                py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoPorcentaje(
                        servicio.redondearTasaPeriodo(tasaConEscalaIntermedia)));
    }

    @Test
    public void testRedondearTasaPeriodoSinDecimalesDeSobra() {
        // Ya viene con menos de 6 decimales -- setScale agrega ceros, comoPorcentaje los recorta
        // igual (pedido ya confirmado para TEA/CFT): no debe aparecer ningún cero de más.
        java.math.BigDecimal tasaExacta = new java.math.BigDecimal("0.02");
        assertEquals("2 %", py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoPorcentaje(
                servicio.redondearTasaPeriodo(tasaExacta)));
    }

    @Test
    public void testConstruirFilasPlanCotizacion() {
        List<py.com.capitalcredito.sgcpc.modelos.Cuota> plan = Arrays.asList(
                cuotaCotizacion(1, LocalDate.of(2026, 11, 1), "500000", "54000", "5400", "2000000"),
                cuotaCotizacion(2, LocalDate.of(2026, 12, 1), "510000", "44000", "4400", "1490000"));
        List<py.com.capitalcredito.sgcpc.dto.FilaPlanCotizacion> filas = servicio.construirFilasPlanCotizacion(plan);
        assertEquals(2, filas.size());
        py.com.capitalcredito.sgcpc.dto.FilaPlanCotizacion primera = filas.get(0);
        assertEquals(1, primera.getNumeroCuota());
        assertEquals("01/11/2026", primera.getVencimiento());
        assertEquals("500.000 Gs.", primera.getCapital());
        assertEquals("54.000 Gs.", primera.getInteres());
        assertEquals("5.400 Gs.", primera.getIva());
        assertEquals("2.000.000 Gs.", primera.getSaldo());
    }

    @Test
    public void testConstruirFilasPlanCotizacionVacio() {
        assertTrue(servicio.construirFilasPlanCotizacion(
                java.util.Collections.<py.com.capitalcredito.sgcpc.modelos.Cuota>emptyList()).isEmpty());
    }

    private static py.com.capitalcredito.sgcpc.modelos.Cuota cuotaCotizacion(int numero, LocalDate vencimiento,
            String capital, String interes, String iva, String saldoPosterior) {
        py.com.capitalcredito.sgcpc.modelos.Cuota cuota = new py.com.capitalcredito.sgcpc.modelos.Cuota();
        cuota.setNumeroCuota(numero);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setCapitalProgramado(new java.math.BigDecimal(capital));
        cuota.setInteresProgramado(new java.math.BigDecimal(interes));
        cuota.setTributosProgramados(new java.math.BigDecimal(iva));
        cuota.setTotalProgramado(new java.math.BigDecimal(capital).add(new java.math.BigDecimal(interes))
                .add(new java.math.BigDecimal(iva)));
        cuota.setSaldoCapitalPosterior(new java.math.BigDecimal(saldoPosterior));
        return cuota;
    }

    // C-4.2 (pedido de Joel): Usuarios y accesos -- sin filtros, siempre trae a todos.
    @Test
    public void testConstruirTextoFiltrosUsuarios() {
        assertEquals("Todos los usuarios registrados", servicio.construirTextoFiltrosUsuarios());
    }

    // C-4.3 (pedido de Joel): Historial de parámetros.
    @Test
    public void testConstruirTextoFiltrosHistorialParametrosConUnParametro() {
        assertEquals("Modificación del 01/09/2026 al 30/09/2026 · Tasa de mora diaria",
                servicio.construirTextoFiltrosHistorialParametros(LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30), "Tasa de mora diaria", false));
    }

    @Test
    public void testConstruirTextoFiltrosHistorialParametrosSinParametroEligeTodos() {
        assertEquals("Modificación del 01/09/2026 al 30/09/2026 · Todos los parámetros",
                servicio.construirTextoFiltrosHistorialParametros(LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30), null, false));
    }

    /** RF39 (pantalla rediseñada): "incluirIniciales" se suma al texto, para que el PDF/CSV deje
     * claro que el filtro por defecto (ocultarlas) no estaba aplicado en esa exportación. */
    @Test
    public void testConstruirTextoFiltrosHistorialParametrosIncluyeValoresIniciales() {
        assertEquals("Modificación del 01/09/2026 al 30/09/2026 · Todos los parámetros · Incluye valores iniciales",
                servicio.construirTextoFiltrosHistorialParametros(LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30), null, true));
    }

    // C-4.4 (pedido de Joel): Consultas al buró, último informe del bloque C.
    @Test
    public void testConstruirTextoFiltrosConsultasBuroConResultado() {
        assertEquals("Consulta del 01/10/2026 al 31/10/2026 · B",
                servicio.construirTextoFiltrosConsultasBuro(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "B"));
    }

    @Test
    public void testConstruirTextoFiltrosConsultasBuroSinResultadoEligeTodos() {
        assertEquals("Consulta del 01/10/2026 al 31/10/2026 · Todos los resultados",
                servicio.construirTextoFiltrosConsultasBuro(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null));
    }

    // 13/10/2026 (pedido de Joel): agregarClausulaClienteUOperacion, usado en el encabezado del
    // PDF/CSV de los 6 informes con el filtro "Cliente u operación" (RF32).
    @Test
    public void testAgregarClausulaClienteUOperacionSinTextoNoCambiaNada() {
        assertEquals("Período del 01/10/2026 al 31/10/2026",
                servicio.agregarClausulaClienteUOperacion("Período del 01/10/2026 al 31/10/2026", null));
        assertEquals("Período del 01/10/2026 al 31/10/2026",
                servicio.agregarClausulaClienteUOperacion("Período del 01/10/2026 al 31/10/2026", "  "));
    }

    @Test
    public void testAgregarClausulaClienteUOperacionConTextoAgregaLaClausula() {
        assertEquals("Período del 01/10/2026 al 31/10/2026 · Cliente/operación: CC-2026-00001",
                servicio.agregarClausulaClienteUOperacion("Período del 01/10/2026 al 31/10/2026", "CC-2026-00001"));
    }

    /** Normalización de personas, grupo Domicilio (16/10/2026) y grupo Laborales (16/10/2026): la
     * ciudad y la situación laboral ya no están en Persona -- los tests que las necesitan arman
     * aparte los Map<Long, Long>/Map<Long, String> idCiudadPorPersona/situacionLaboralPorPersona
     * que ahora reciben calcularSubtotalesPorCiudad/calcularSubtotalesPorSituacionLaboral/
     * construirFilasInformeClientes. */
    private Persona personaDePrueba(long idPersona) {
        Persona persona = new Persona();
        persona.setIdPersona(idPersona);
        persona.setCedulaIdentidad("100000" + idPersona);
        persona.setNombres("Nombre" + idPersona);
        persona.setApellidos("Apellido" + idPersona);
        persona.setEstadoPersona(EstadoPersona.ACTIVA);
        return persona;
    }

    // ======================================================================
    // B209 (15/11/2026, item 7 del informe de UX de Informes): el ⓘ de los botones de exportar y
    // los botones en gris cuando no hay nada que exportar.
    // ======================================================================

    /** Con filas, ni clase ni atributos de apagado: el boton queda exactamente como estaba en los
     * veinte reportes. */
    @Test
    public void testBotonExportar_ConFilasNoCambiaNada() {
        assertEquals("", ServicioInformes.claseBotonExportar(true));
        assertEquals("", ServicioInformes.atributosBotonExportar(true));
    }

    /** Sin filas no alcanza con pintarlo gris: un <a href> en gris sigue siendo navegable, asi que
     * tambien sale del tabulador y queda marcado para los lectores de pantalla. */
    @Test
    public void testBotonExportar_SinFilasQuedaInerteYNoSoloGris() {
        assertEquals(" disabled", ServicioInformes.claseBotonExportar(false));
        assertEquals(" tabindex=\"-1\" aria-disabled=\"true\"",
                ServicioInformes.atributosBotonExportar(false));
    }

    /** El texto del ⓘ explica los DOS botones (es uno para el par) y no promete nada que sea falso
     * en alguno de los veinte reportes: no habla de la busqueda dentro de la tabla, que solo
     * existe en cinco (V15) y ya tiene su propio aviso. */
    @Test
    public void testAyudaExportar_ExplicaLosDosBotonesYNadaMas() {
        assertTrue(ServicioInformes.AYUDA_EXPORTAR_TEXTO.contains("Informe PDF"));
        assertTrue(ServicioInformes.AYUDA_EXPORTAR_TEXTO.contains("Excel/CSV"));
        assertTrue(ServicioInformes.AYUDA_EXPORTAR_TEXTO.contains("filtros"));
        assertFalse(ServicioInformes.AYUDA_EXPORTAR_TEXTO.contains("búsqueda"));
    }

    /** Y el porque del gris se dice, en vez de dejar que alguien lo toque y reciba un PDF en blanco. */
    @Test
    public void testAvisoSinFilasParaExportar() {
        assertEquals("No hay filas para exportar con este filtro.",
                ServicioInformes.AVISO_SIN_FILAS_PARA_EXPORTAR);
    }

    // ======================================================================
    // B210 (15/11/2026, items 2+3): el contador del titulo de los reportes. El formato tiene un
    // solo dueño porque son diecisies pantallas, y el total es del listado COMPLETO.
    // ======================================================================

    @Test
    public void testTituloConCantidad_PluralYSeparadorDeMiles() {
        assertEquals("Vencimientos (292 cuotas)",
                ServicioInformes.tituloConCantidad("Vencimientos", Integer.valueOf(292), "cuota", "cuotas"));
        assertEquals("Auditoría (2.230 registros)",
                ServicioInformes.tituloConCantidad("Auditoría", Integer.valueOf(2230), "registro", "registros"));
    }

    /** El singular no se arma pegandole una "s": "1 gestión", no "1 gestiones" ni "1 gestións". */
    @Test
    public void testTituloConCantidad_EnSingular() {
        assertEquals("Gestiones de cobranza (1 gestión)",
                ServicioInformes.tituloConCantidad("Gestiones de cobranza", Integer.valueOf(1), "gestión", "gestiones"));
    }

    /** Cero es plural, y no se esconde: "0 préstamos" dice que se consulto y no habia. */
    @Test
    public void testTituloConCantidad_ConCero() {
        assertEquals("Cartera (0 préstamos)",
                ServicioInformes.tituloConCantidad("Cartera", Integer.valueOf(0), "préstamo", "préstamos"));
    }

    /** Sin total (la pantalla todavia no consulto) devuelve el titulo pelado, no un "(0)" que
     * mentiria diciendo que no hay nada. */
    @Test
    public void testTituloConCantidad_SinTotalNoInventaUnCero() {
        assertEquals("Cartera", ServicioInformes.tituloConCantidad("Cartera", null, "préstamo", "préstamos"));
    }
}
