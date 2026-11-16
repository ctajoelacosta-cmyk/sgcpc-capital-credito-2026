package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Feriado;

public class ServicioParametrosTest {

    private final ServicioParametros servicio = new ServicioParametros();

    @Test
    public void testTeaEmpresaSobreElTopeSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarTeaEmpresa(new BigDecimal("0.30"), new BigDecimal("0.2739")));
    }

    @Test
    public void testTeaEmpresaDentroDelTopeNoLanza() {
        servicio.validarTeaEmpresa(new BigDecimal("0.27"), new BigDecimal("0.2739"));
    }

    // RF39/D2 (pedido de Joel, 13/10/2026): hallazgo del análisis -- antes una TEA de 0 % quedaba
    // aceptada porque solo se comparaba contra el tope superior.
    @Test
    public void testTeaEmpresaCeroONegativaSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarTeaEmpresa(BigDecimal.ZERO, new BigDecimal("0.2739")));
        assertThrows(NegocioException.class,
                () -> servicio.validarTeaEmpresa(new BigDecimal("-0.05"), new BigDecimal("0.2739")));
    }

    // RF39/E1 (decisión de Joel, 13/10/2026): una sola fuente para el tope legal de TEA --
    // limites_tasa_usura, no credito.tea_tope_legal.
    @Test
    public void testResolverTopeLegalVigenteSinLimiteCargadoSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.resolverTopeLegalVigente(null));
        assertEquals("No hay un límite de tasa vigente cargado. Consulte al administrador.", error.getMessage());
    }

    @Test
    public void testResolverTopeLegalVigenteDevuelveLaTasaDelLimite() {
        LimiteTasaUsura limite = new LimiteTasaUsura();
        limite.setTasaMaximaTea(new BigDecimal("0.2739"));
        assertEquals(new BigDecimal("0.2739"), servicio.resolverTopeLegalVigente(limite));
    }

    @Test
    public void testFactorMoratorioFueraDeRangoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarFactorMoratorio(BigDecimal.ZERO));
        assertThrows(NegocioException.class, () -> servicio.validarFactorMoratorio(new BigDecimal("1.01")));
    }

    @Test
    public void testFactorMoratorioValidoNoLanza() {
        servicio.validarFactorMoratorio(BigDecimal.ONE);
        servicio.validarFactorMoratorio(new BigDecimal("0.5"));
    }

    @Test
    public void testPorcentajePunitorioSobreElMaximoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarPorcentajePunitorio(new BigDecimal("0.31")));
        assertThrows(NegocioException.class, () -> servicio.validarPorcentajePunitorio(BigDecimal.ZERO));
    }

    @Test
    public void testPorcentajePunitorioValidoNoLanza() {
        servicio.validarPorcentajePunitorio(new BigDecimal("0.30"));
    }

    @Test
    public void testLimiteAfectacionFueraDeRangoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarLimiteAfectacion(BigDecimal.ZERO));
        assertThrows(NegocioException.class, () -> servicio.validarLimiteAfectacion(new BigDecimal("1.5")));
    }

    @Test
    public void testMontoMinimoMayorOIgualAlMaximoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarMontos(new BigDecimal("15000000"), new BigDecimal("500000")));
        assertThrows(NegocioException.class,
                () -> servicio.validarMontos(new BigDecimal("500000"), new BigDecimal("500000")));
    }

    @Test
    public void testMontosValidosNoLanza() {
        servicio.validarMontos(new BigDecimal("500000"), new BigDecimal("15000000"));
    }

    // RF39/D2 (pedido de Joel, 13/10/2026): hallazgo del análisis -- antes un monto negativo o
    // cero quedaba aceptado porque solo se comparaban entre sí.
    @Test
    public void testMontoMinimoOMaximoNegativoOCeroSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarMontos(new BigDecimal("-500000"), new BigDecimal("15000000")));
        assertThrows(NegocioException.class, () -> servicio.validarMontos(BigDecimal.ZERO, new BigDecimal("15000000")));
        assertThrows(NegocioException.class, () -> servicio.validarMontos(new BigDecimal("500000"), BigDecimal.ZERO));
    }

    @Test
    public void testPlazoMinimoMayorOIgualAlMaximoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarPlazos(24, 3));
        assertThrows(NegocioException.class, () -> servicio.validarPlazos(12, 12));
    }

    @Test
    public void testPlazosValidosNoLanza() {
        servicio.validarPlazos(3, 24);
    }

    // RF39/D2 (pedido de Joel, 13/10/2026): credito.edad_minima/edad_maxima no tenian ninguna
    // validacion cruzada al cambiarlos.
    @Test
    public void testEdadMinimaMayorOIgualALaMaximaSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarEdades(71, 22));
        assertThrows(NegocioException.class, () -> servicio.validarEdades(30, 30));
    }

    @Test
    public void testEdadesValidasNoLanza() {
        servicio.validarEdades(22, 71);
    }

    // RF39/D1 (pedido de Joel, 13/10/2026): motivo obligatorio al cambiar un parametro.
    @Test
    public void testMotivoParametroCortoOAusenteSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarMotivoParametro(null));
        assertThrows(NegocioException.class, () -> servicio.validarMotivoParametro(""));
        assertThrows(NegocioException.class, () -> servicio.validarMotivoParametro("corto"));
    }

    @Test
    public void testMotivoParametroValidoNoLanza() {
        servicio.validarMotivoParametro("Ajuste según tope BCP de octubre.");
    }

    private LimiteTasaUsura limite(String desde, String hasta) {
        LimiteTasaUsura limite = new LimiteTasaUsura();
        limite.setVigenteDesde(LocalDate.parse(desde));
        limite.setVigenteHasta(hasta == null ? null : LocalDate.parse(hasta));
        return limite;
    }

    @Test
    public void testSuperposicionDeVigenciasSeRechaza() {
        List<LimiteTasaUsura> existentes = new ArrayList<>();
        existentes.add(limite("2026-01-01", "2026-12-31"));

        assertThrows(NegocioException.class, () -> servicio.validarSuperposicionLimitesTasa(
                LocalDate.parse("2026-06-01"), LocalDate.parse("2027-06-01"), existentes));
    }

    @Test
    public void testSuperposicionConVigenciaAbiertaSeRechaza() {
        List<LimiteTasaUsura> existentes = new ArrayList<>();
        existentes.add(limite("2026-01-01", null));

        assertThrows(NegocioException.class, () -> servicio.validarSuperposicionLimitesTasa(
                LocalDate.parse("2027-01-01"), null, existentes));
    }

    @Test
    public void testVigenciaSinSuperposicionNoLanza() {
        List<LimiteTasaUsura> existentes = new ArrayList<>();
        existentes.add(limite("2026-01-01", "2026-12-31"));

        servicio.validarSuperposicionLimitesTasa(LocalDate.parse("2027-01-01"), null, existentes);
    }

    // --- RF39 (corrección 2b, pedido de Joel, 13/10/2026): el detalle de auditoría de
    // CAMBIAR_PARAMETRO debe decir el valor anterior, el nuevo y el motivo. ---

    @Test
    public void testConstruirDetalleCambioParametro() {
        assertEquals("Cambió \"TEA de la empresa\": 27 % → 26 %. Motivo: Prueba RF39: cambio temporal de tasa.",
                servicio.construirDetalleCambioParametro("TEA de la empresa", TipoValorParametro.DECIMAL,
                        "credito.tea_empresa", "0.270000", "0.260000", "Prueba RF39: cambio temporal de tasa"));
    }

    @Test
    public void testConstruirDetalleCambioParametroClaveGuaraniesComoMoneda() {
        assertEquals("Cambió \"Monto mínimo de crédito (Gs.)\": 500.000 Gs. → 600.000 Gs. Motivo: ajuste.",
                servicio.construirDetalleCambioParametro("Monto mínimo de crédito (Gs.)", TipoValorParametro.ENTERO,
                        "credito.monto_minimo", "500000", "600000", "ajuste"));
    }

    // --- RF39/E2 (decisión de Joel, 13/10/2026): el detalle de auditoría de feriados y límites
    // de tasa debe decir el antes y el después. ---

    @Test
    public void testConstruirDetalleAltaFeriado() {
        assertEquals("Agregó el feriado 25/12/2026 (Navidad): no existía → activo.",
                servicio.construirDetalleAltaFeriado(LocalDate.parse("2026-12-25"), "Navidad"));
    }

    @Test
    public void testConstruirDetalleCambioActivoFeriadoDesactivar() {
        assertEquals("Desactivó el feriado 25/12/2026 (Navidad): activo → inactivo.",
                servicio.construirDetalleCambioActivoFeriado(false, LocalDate.parse("2026-12-25"), "Navidad", true));
    }

    @Test
    public void testConstruirDetalleCambioActivoFeriadoReactivar() {
        assertEquals("Reactivó el feriado 25/12/2026 (Navidad): inactivo → activo.",
                servicio.construirDetalleCambioActivoFeriado(true, LocalDate.parse("2026-12-25"), "Navidad", false));
    }

    @Test
    public void testConstruirDetalleAltaLimiteTasaSinLimiteAnterior() {
        LimiteTasaUsura nuevo = limite("2026-10-08", null);
        nuevo.setTasaMaximaTea(new BigDecimal("0.2739"));

        assertEquals("Nuevo límite de tasa: no había límite vigente → nuevo 27,39 % desde 08/10/2026.",
                servicio.construirDetalleAltaLimiteTasa(null, null, nuevo));
    }

    @Test
    public void testConstruirDetalleAltaLimiteTasaConLimiteAnterior() {
        LimiteTasaUsura anterior = limite("2026-01-01", null);
        anterior.setTasaMaximaTea(new BigDecimal("0.27"));
        LimiteTasaUsura nuevo = limite("2026-10-08", null);
        nuevo.setTasaMaximaTea(new BigDecimal("0.2739"));

        assertEquals("Nuevo límite de tasa: anterior 27 % (hasta 07/10/2026) → nuevo 27,39 % desde 08/10/2026.",
                servicio.construirDetalleAltaLimiteTasa(anterior, LocalDate.parse("2026-10-07"), nuevo));
    }

    // --- validarDiasVigenciaConsentimiento (D-38, E3) ---

    @Test
    public void testDiasVigenciaConsentimientoFueraDeRangoSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarDiasVigenciaConsentimiento(0));
        assertThrows(NegocioException.class, () -> servicio.validarDiasVigenciaConsentimiento(366));
    }

    @Test
    public void testDiasVigenciaConsentimientoValidoNoLanza() {
        servicio.validarDiasVigenciaConsentimiento(1);
        servicio.validarDiasVigenciaConsentimiento(30);
        servicio.validarDiasVigenciaConsentimiento(365);
    }

    // --- Cadena completa ConversorNumeros -> ServicioParametros, la misma que usa
    // actualizarValor/normalizarValorNuevo (Doc. 10 §10.18: la coma decimal nunca debe
    // terminar en un NumberFormatException crudo, siempre en NegocioException con mensaje
    // claro). Reportado por Joel tras probar E3; verificado que no se reproduce en el
    // codigo actual (ver AVANCE.md) — estas pruebas dejan la cobertura para el futuro. ---

    @Test
    public void testComaDecimalAceptadaParaUnDecimalComoFactorMoratorio() {
        // "1,5" (1,5 %) es un caso valido de factor_moratorio (0 % < x <= 100 %): igual
        // que en la pantalla, se interpreta como porcentaje y se guarda como fraccion.
        BigDecimal porcentaje = ConversorNumeros.aBigDecimal("1,5");
        BigDecimal fraccion = FormateadorNumeros.fraccionDesdePorcentaje(porcentaje, 6);
        servicio.validarFactorMoratorio(fraccion);
    }

    @Test
    public void testDiasVigenciaConsentimientoEnteroValidoAceptado() {
        int dias = ConversorNumeros.aEntero("30");
        servicio.validarDiasVigenciaConsentimiento(dias);
    }

    @Test
    public void testDiasVigenciaConsentimientoConComaSeRechazaConMensajeClaro() {
        // "30,5" no es un entero valido: ConversorNumeros.aEntero debe rechazarlo con
        // NegocioException (mensaje claro), nunca dejar pasar un NumberFormatException.
        NegocioException error = assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero("30,5"));
        assertEquals("\"30,5\" no es un número entero válido.", error.getMessage());
    }

    @Test
    public void testDiasVigenciaConsentimientoNoNumericoSeRechaza() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero("abc"));
    }

    @Test
    public void testDiasVigenciaConsentimientoVacioSeRechaza() {
        assertThrows(NegocioException.class, () -> ConversorNumeros.aEntero(""));
    }

    // --- D-84: parametros de tipo FECHA cargados en dd/mm/aaaa terminaban guardados tal cual
    // (sin validar), y un cobro que despues leia esa fecha terminaba en un 500 en vez de un
    // mensaje claro (incidente real, Joel probando /cobranza/23). ---

    @Test
    public void testNormalizarFechaIsoSeGuardaTalCual() {
        assertEquals("2026-10-01", ServicioParametros.normalizarFecha("2026-10-01"));
    }

    @Test
    public void testNormalizarFechaDiaMesAnioSeGuardaEnIso() {
        assertEquals("2026-09-01", ServicioParametros.normalizarFecha("01/09/2026"));
    }

    @Test
    public void testNormalizarFechaInexistenteSeRechaza() {
        // 31 de febrero no existe -- ResolverStyle.STRICT debe rechazarlo, no "corregirlo" a
        // otro dia (el comportamiento por defecto de DateTimeFormatter sin STRICT).
        NegocioException error = assertThrows(NegocioException.class,
                () -> ServicioParametros.normalizarFecha("31/02/2026"));
        assertEquals("Fecha inválida. Use el calendario o el formato DD/MM/AAAA.", error.getMessage());
    }

    @Test
    public void testNormalizarFechaTextoNoNumericoSeRechaza() {
        assertThrows(NegocioException.class, () -> ServicioParametros.normalizarFecha("hola"));
    }

    @Test
    public void testVigenciaTimbradoHastaAnteriorADesdeSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarVigenciaTimbrado(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    public void testVigenciaTimbradoHastaIgualOPosteriorADesdeNoLanza() {
        servicio.validarVigenciaTimbrado(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));
        servicio.validarVigenciaTimbrado(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
    }

    // D-89: mismo criterio que la vigencia por fecha, para el rango de numeración autorizado.

    @Test
    public void testRangoNumeroFacturaHastaMenorQueDesdeSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarRangoNumeroFactura(1, 0));
    }

    @Test
    public void testRangoNumeroFacturaHastaIgualOMayorQueDesdeNoLanza() {
        servicio.validarRangoNumeroFactura(1, 9999999);
        servicio.validarRangoNumeroFactura(500, 500);
    }

    @Test
    public void testParsearEnteroConValorInvalidoLanzaNegocioExceptionConLaClave() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> ServicioParametros.parsearEntero("credito.dias_gracia_mora", "no-es-un-numero"));
        assertEquals("El parámetro \"credito.dias_gracia_mora\" tiene un valor inválido (\"no-es-un-numero\"). "
                + "Corríjalo en Parámetros.", error.getMessage());
    }

    @Test
    public void testParsearDecimalConValorInvalidoLanzaNegocioExceptionConLaClave() {
        assertThrows(NegocioException.class,
                () -> ServicioParametros.parsearDecimal("credito.porcentaje_punitorio", "no-es-un-numero"));
    }

    @Test
    public void testParsearFechaConValorInvalidoLanzaNegocioExceptionConLaClave() {
        // El incidente real: 01/09/2026 quedo guardado tal cual (sin pasar por normalizarFecha,
        // que no existia todavia) y obtenerValorFecha lo leyo con LocalDate.parse crudo.
        NegocioException error = assertThrows(NegocioException.class,
                () -> ServicioParametros.parsearFecha("fiscal.timbrado_vigencia_desde", "01/09/2026"));
        assertEquals("El parámetro \"fiscal.timbrado_vigencia_desde\" tiene un valor inválido (\"01/09/2026\"). "
                + "Corríjalo en Parámetros.", error.getMessage());
    }

    // --- formatearValorParametro (C-4.3, pedido de Joel): mismo formato por tipo que ya usaba
    // administracion.jsp, extraido para reutilizar en el historial sin tocar esa pantalla. ---

    @Test
    public void testFormatearValorParametroDecimalComoPorcentaje() {
        assertEquals("5 %", ServicioParametros.formatearValorParametro("credito.porcentaje_punitorio",
                TipoValorParametro.DECIMAL, "0.05"));
    }

    @Test
    public void testFormatearValorParametroClaveGuaraniesComoMoneda() {
        assertEquals("500.000 Gs.", ServicioParametros.formatearValorParametro("credito.monto_minimo",
                TipoValorParametro.ENTERO, "500000"));
    }

    // RF39/E3 (pedido de Joel, 13/10/2026): caja.monto_maximo_arqueo tambien en la lista de
    // guaranies -- antes se mostraba "500000000" en vez de "500.000.000 Gs." en el historial.
    @Test
    public void testFormatearValorParametroMontoMaximoArqueoComoMoneda() {
        assertEquals("500.000.000 Gs.", ServicioParametros.formatearValorParametro("caja.monto_maximo_arqueo",
                TipoValorParametro.ENTERO, "500000000"));
    }

    @Test
    public void testFormatearValorParametroEnteroSinClaveGuaraniesQuedaPlano() {
        assertEquals("30", ServicioParametros.formatearValorParametro("credito.dias_gracia_mora",
                TipoValorParametro.ENTERO, "30"));
    }

    @Test
    public void testFormatearValorParametroFechaComoDdMmAaaa() {
        assertEquals("01/09/2026", ServicioParametros.formatearValorParametro("fiscal.timbrado_vigencia_desde",
                TipoValorParametro.FECHA, "2026-09-01"));
    }

    @Test
    public void testFormatearValorParametroBooleanoYTextoQuedanPlanos() {
        assertEquals("true", ServicioParametros.formatearValorParametro("alguna.clave", TipoValorParametro.BOOLEANO,
                "true"));
        assertEquals("Capital Crédito", ServicioParametros.formatearValorParametro("empresa.razon_social",
                TipoValorParametro.TEXTO, "Capital Crédito"));
    }

    @Test
    public void testFormatearValorParametroNuloDevuelveGuion() {
        assertEquals("—", ServicioParametros.formatearValorParametro("credito.monto_minimo",
                TipoValorParametro.ENTERO, null));
    }

    @Test
    public void testFormatearValorParametroFechaInvalidaNoRompeYAvisaFormatoInvalido() {
        assertEquals("no-es-una-fecha (formato inválido)", ServicioParametros.formatearValorParametro(
                "fiscal.timbrado_vigencia_desde", TipoValorParametro.FECHA, "no-es-una-fecha"));
    }
}
