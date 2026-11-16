package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.MotivoAnulacionInsuficienteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;
import py.com.capitalcredito.sgcpc.modelos.Feriado;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/** E5 (P06): reglas puras de ServicioFormalizacion. */
public class ServicioFormalizacionTest {

    private final ServicioFormalizacion servicio = new ServicioFormalizacion();

    @Test
    public void testProponerPrimerVencimientoEsUnMesDespues() {
        LocalDate fechaFormalizacion = LocalDate.of(2026, 9, 25);
        assertEquals(LocalDate.of(2026, 10, 25), servicio.proponerPrimerVencimiento(fechaFormalizacion));
    }

    @Test
    public void testPrimerVencimientoDentroDelRangoNoLanza() {
        LocalDate hoy = LocalDate.of(2026, 9, 25);
        servicio.validarPrimerVencimientoFormalizacion(hoy, hoy.plusDays(30), 20, 45);
    }

    @Test
    public void testPrimerVencimientoAntesDelMinimoSeRechaza() {
        LocalDate hoy = LocalDate.of(2026, 9, 25);
        assertThrows(NegocioException.class,
                () -> servicio.validarPrimerVencimientoFormalizacion(hoy, hoy.plusDays(19), 20, 45));
    }

    @Test
    public void testPrimerVencimientoDespuesDelMaximoSeRechaza() {
        LocalDate hoy = LocalDate.of(2026, 9, 25);
        assertThrows(NegocioException.class,
                () -> servicio.validarPrimerVencimientoFormalizacion(hoy, hoy.plusDays(46), 20, 45));
    }

    @Test
    public void testPrimerVencimientoNuloSeRechaza() {
        LocalDate hoy = LocalDate.of(2026, 9, 25);
        assertThrows(NegocioException.class,
                () -> servicio.validarPrimerVencimientoFormalizacion(hoy, null, 20, 45));
    }

    /** RF11 (pedido de Joel, 11/10/2026): además de la fecha elegida, la fecha ya CORRIDA al
     * siguiente día hábil tiene que seguir dentro del rango -- corrimiento por fin de semana que
     * se mantiene dentro del rango no lanza. */
    @Test
    public void testPrimerVencimientoEfectivoCorridoPorFinDeSemanaDentroDelRangoNoLanza() {
        LocalDate hoy = LocalDate.of(2026, 10, 11);
        servicio.validarPrimerVencimientoEfectivoFormalizacion(hoy, LocalDate.of(2026, 11, 9), 45,
                Collections.emptyList());
    }

    /** Corrimiento por feriado que se mantiene dentro del rango tampoco lanza (la validación solo
     * mira la fecha YA corrida contra el máximo, no por qué se corrió). */
    @Test
    public void testPrimerVencimientoEfectivoCorridoPorFeriadoDentroDelRangoNoLanza() {
        LocalDate hoy = LocalDate.of(2026, 10, 11);
        servicio.validarPrimerVencimientoEfectivoFormalizacion(hoy, LocalDate.of(2026, 12, 9), 59,
                Arrays.asList(LocalDate.of(2026, 12, 8)));
    }

    @Test
    public void testPrimerVencimientoEfectivoFueraDelRangoTrasCorrerSeRechazaConFechaSugerida() {
        LocalDate hoy = LocalDate.of(2026, 10, 11);
        List<LocalDate> feriados = Arrays.asList(LocalDate.of(2026, 11, 25));

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarPrimerVencimientoEfectivoFormalizacion(hoy, LocalDate.of(2026, 11, 26), 45,
                        feriados));

        assertEquals("La fecha elegida cae en un día no hábil y, al pasarla al siguiente día hábil, "
                + "queda fuera del plazo permitido (hasta el 25/11/2026). Elija una fecha hábil anterior, "
                + "por ejemplo el 24/11/2026.", error.getMessage());
    }

    @Test
    public void testDescribirMotivoNoHabilSabadoYDomingo() {
        assertEquals("sábado", servicio.describirMotivoNoHabil(LocalDate.of(2026, 11, 7), Collections.<Feriado>emptyList()));
        assertEquals("domingo", servicio.describirMotivoNoHabil(LocalDate.of(2026, 11, 8), Collections.<Feriado>emptyList()));
    }

    @Test
    public void testDescribirMotivoNoHabilFeriadoConNombre() {
        Feriado feriado = new Feriado();
        feriado.setFecha(LocalDate.of(2026, 12, 8));
        feriado.setDescripcion("Día de la Virgen de Caacupé");

        assertEquals("feriado (Día de la Virgen de Caacupé)",
                servicio.describirMotivoNoHabil(LocalDate.of(2026, 12, 8), Arrays.asList(feriado)));
    }

    @Test
    public void testDescribirMotivoNoHabilDiaHabilEsNull() {
        assertNull(servicio.describirMotivoNoHabil(LocalDate.of(2026, 11, 9), Collections.<Feriado>emptyList()));
    }

    @Test
    public void testDescribirCorrimientoSinCambioEsNull() {
        LocalDate fecha = LocalDate.of(2026, 11, 9);
        assertNull(servicio.describirCorrimiento(fecha, fecha, Collections.<Feriado>emptyList()));
    }

    @Test
    public void testDescribirCorrimientoPorFinDeSemana() {
        String motivo = servicio.describirCorrimiento(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9),
                Collections.<Feriado>emptyList());

        assertEquals("sábado, domingo", motivo);
    }

    @Test
    public void testDescribirCorrimientoPorFeriado() {
        Feriado feriado = new Feriado();
        feriado.setFecha(LocalDate.of(2026, 12, 8));
        feriado.setDescripcion("Día de la Virgen de Caacupé");

        String motivo = servicio.describirCorrimiento(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9),
                Arrays.asList(feriado));

        assertEquals("feriado (Día de la Virgen de Caacupé)", motivo);
    }

    /** RF11 (pedido de Joel, 11/10/2026): mensaje del 40 % de afectación, separado de la
     * revalidación (que necesita Connection) para poder probarlo sin base de datos. */
    /** RF11: el 40 % de afectación bloquea también al formalizar (no solo al crear/simular).
     * F2 (21/10/2026, pedido de Joel, punto 7): versión (a), sin el permiso -- sin cambios de
     * fondo salvo que ya no repite "No se puede formalizar hasta que...". */
    @Test
    public void testConstruirMensajeLimiteAfectacionFormalizacionSuperaElLimiteSinPermiso() {
        String mensaje = servicio.construirMensajeLimiteAfectacionFormalizacion(new BigDecimal("1500000"),
                new BigDecimal("0.45"), new BigDecimal("0.40"), false);

        assertEquals("No se puede formalizar. La cuota más alta (1.500.000 Gs.) representa el 45,0 % "
                + "del ingreso del cliente (sumando sus otras deudas actuales), por encima del límite "
                + "permitido (40,0 %). Consulte al gerente: la solicitud debe cancelarse para evaluar "
                + "una nueva.", mensaje);
    }

    /** F2: versión (b), con el permiso. */
    @Test
    public void testConstruirMensajeLimiteAfectacionFormalizacionSuperaElLimiteConPermiso() {
        String mensaje = servicio.construirMensajeLimiteAfectacionFormalizacion(new BigDecimal("1500000"),
                new BigDecimal("0.45"), new BigDecimal("0.40"), true);

        assertEquals("No se puede formalizar. La cuota más alta (1.500.000 Gs.) representa el 45,0 % "
                + "del ingreso del cliente (sumando sus otras deudas actuales), por encima del límite "
                + "permitido (40,0 %). Puede cancelar la solicitud desde Aprobación para evaluar una "
                + "nueva con los datos actuales del cliente.", mensaje);
    }

    // --- RF05/RF06/RF11 (pedido de Joel, 18/10/2026): hueco preexistente destapado al quitar el
    // codeudor -- mismo bloqueo directo también al formalizar.

    @Test
    public void testConstruirMensajeSinComprobanteFormalizacionCuandoFaltaSinPermiso() {
        assertEquals("El cliente no presenta comprobante de ingresos. Solo se otorgan créditos a "
                + "personas con ingreso fijo comprobable. Consulte al gerente: la solicitud debe "
                + "cancelarse para evaluar una nueva.",
                servicio.construirMensajeSinComprobanteFormalizacion(false, false));
    }

    /** F2 (21/10/2026, pedido de Joel, punto 7): versión (b), con el permiso. */
    @Test
    public void testConstruirMensajeSinComprobanteFormalizacionCuandoFaltaConPermiso() {
        assertEquals("El cliente no presenta comprobante de ingresos. Solo se otorgan créditos a "
                + "personas con ingreso fijo comprobable. Puede cancelar la solicitud desde Aprobación "
                + "para evaluar una nueva cuando el cliente presente el comprobante.",
                servicio.construirMensajeSinComprobanteFormalizacion(false, true));
    }

    @Test
    public void testConstruirMensajeSinComprobanteFormalizacionNullConComprobante() {
        assertNull(servicio.construirMensajeSinComprobanteFormalizacion(true, false));
        assertNull(servicio.construirMensajeSinComprobanteFormalizacion(true, true));
    }

    @Test
    public void testMotivoAnulacionVacioSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarMotivoAnulacion("  "));
        assertThrows(NegocioException.class, () -> servicio.validarMotivoAnulacion(null));
    }

    @Test
    public void testMotivoAnulacionConTextoNoLanza() {
        servicio.validarMotivoAnulacion("El cliente desistió antes de firmar.");
    }

    private PrestamoCredito prestamoDePrueba(long idPrestamo, String montoAprobado) {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(idPrestamo);
        prestamo.setMontoAprobado(new BigDecimal(montoAprobado));
        return prestamo;
    }

    /**
     * Bug encontrado por Joel en la prueba manual de E6 (fondo sugerido de caja sumaba préstamos
     * sin checklist completo): sumarMontosAprobadosConChecklistCompleto suma solo los que el
     * checklist (ya calculado aparte) marca como completos.
     */
    @Test
    public void testSumaSoloElPrestamoConChecklistCompleto() {
        PrestamoCredito completo = prestamoDePrueba(13, "3000000");
        PrestamoCredito incompleto = prestamoDePrueba(14, "3000000");
        Map<Long, Boolean> checklistCompletoPorPrestamo = new HashMap<>();
        checklistCompletoPorPrestamo.put(13L, true);
        checklistCompletoPorPrestamo.put(14L, false);

        BigDecimal total = servicio.sumarMontosAprobadosConChecklistCompleto(
                Arrays.asList(completo, incompleto), checklistCompletoPorPrestamo);

        assertEquals(new BigDecimal("3000000"), total);
    }

    @Test
    public void testSumaConListaVaciaEsCero() {
        BigDecimal total = servicio.sumarMontosAprobadosConChecklistCompleto(
                Collections.emptyList(), Collections.emptyMap());

        assertEquals(BigDecimal.ZERO, total);
    }

    @Test
    public void testSumaConTodosIncompletosEsCero() {
        PrestamoCredito incompleto1 = prestamoDePrueba(13, "3000000");
        PrestamoCredito incompleto2 = prestamoDePrueba(14, "3000000");
        Map<Long, Boolean> checklistCompletoPorPrestamo = new HashMap<>();
        checklistCompletoPorPrestamo.put(13L, false);
        checklistCompletoPorPrestamo.put(14L, false);

        BigDecimal total = servicio.sumarMontosAprobadosConChecklistCompleto(
                Arrays.asList(incompleto1, incompleto2), checklistCompletoPorPrestamo);

        assertEquals(BigDecimal.ZERO, total);
    }

    // --- RF13 (reprueba Lote 1, hallazgo de Joel): checklist con 3 tipos obligatorios, no 2 ---

    @Test
    public void testChecklistConDosDeTresCumplidosNoEstaCompleto() {
        List<ServicioFormalizacion.ItemChecklist> checklist = Arrays.asList(
                new ServicioFormalizacion.ItemChecklist(null, true, "Contrato firmado", 1L),
                new ServicioFormalizacion.ItemChecklist(null, true, "Pagaré firmado", 2L),
                new ServicioFormalizacion.ItemChecklist(null, false, "Plan de pagos firmado", null));

        assertFalse(servicio.esChecklistCompleto(checklist));
    }

    @Test
    public void testChecklistConTresDeTresCumplidosEstaCompleto() {
        List<ServicioFormalizacion.ItemChecklist> checklist = Arrays.asList(
                new ServicioFormalizacion.ItemChecklist(null, true, "Contrato firmado", 1L),
                new ServicioFormalizacion.ItemChecklist(null, true, "Pagaré firmado", 2L),
                new ServicioFormalizacion.ItemChecklist(null, true, "Plan de pagos firmado", 3L));

        assertTrue(servicio.esChecklistCompleto(checklist));
    }

    // --- RF13 §10b (Tanda 2, 14/10/2026, pedido de Joel): bloqueaRegenerar ahora mira también
    // PLAN_PAGOS_FIRMADO, no solo contrato/pagaré ---

    @Test
    public void testBloqueaRegenerarSinNingunFirmadoNoBloquea() {
        assertFalse(servicio.bloqueaRegenerar(false, false, false));
    }

    @Test
    public void testBloqueaRegenerarConContratoFirmadoBloquea() {
        assertTrue(servicio.bloqueaRegenerar(true, false, false));
    }

    @Test
    public void testBloqueaRegenerarConPagareFirmadoBloquea() {
        assertTrue(servicio.bloqueaRegenerar(false, true, false));
    }

    /** El hallazgo de RF13 §10b: antes, con SOLO el plan de pagos firmado cargado (sin contrato
     * ni pagaré), no bloqueaba -- dejaba ese escaneo desincronizado del plan nuevo si se
     * regeneraba. */
    @Test
    public void testBloqueaRegenerarConSoloPlanPagosFirmadoBloquea() {
        assertTrue(servicio.bloqueaRegenerar(false, false, true));
    }

    // --- RF14 (pedido de Joel, 11/10/2026): motivo de anulación, mínimo 10 caracteres ---

    @Test
    public void testValidarMotivoAnulacionNuloLanzaAdvertencia() {
        assertThrows(MotivoAnulacionInsuficienteException.class, () -> servicio.validarMotivoAnulacion(null));
    }

    @Test
    public void testValidarMotivoAnulacionCortoLanzaAdvertencia() {
        assertThrows(MotivoAnulacionInsuficienteException.class,
                () -> servicio.validarMotivoAnulacion("muy corto"));
    }

    @Test
    public void testValidarMotivoAnulacionConDiezCaracteresNoLanza() {
        servicio.validarMotivoAnulacion("1234567890");
    }

    @Test
    public void testConstruirMensajeMotivoAnulacionInsuficiente() {
        assertEquals("Explique el motivo de la anulación con al menos 10 caracteres.",
                servicio.construirMensajeMotivoAnulacionInsuficiente());
    }

    // --- RF14: carrera anular vs. desembolsar -- mensaje con quién y cuándo ---

    @Test
    public void testConstruirMensajePrestamoYaDesembolsadoConUsuario() {
        Desembolso desembolso = new Desembolso();
        desembolso.setFechaHoraDesembolso(LocalDateTime.of(2026, 10, 11, 17, 8));
        Usuario cajera = new Usuario();
        cajera.setNombres("Sofía");
        cajera.setApellidos("Martínez");

        String mensaje = servicio.construirMensajePrestamoYaDesembolsado(desembolso, cajera);

        assertEquals("Este préstamo ya fue desembolsado por Sofía Martínez el 11/10/2026 17:08. "
                + "Actualice la página.", mensaje);
    }

    @Test
    public void testConstruirMensajePrestamoYaDesembolsadoSinUsuario() {
        String mensaje = servicio.construirMensajePrestamoYaDesembolsado(null, null);

        assertEquals("Este préstamo ya fue desembolsado por otro usuario el . Actualice la página.", mensaje);
    }

    /** 13/10/2026 (hallazgo de Joel): la constancia de devolución, sin CAN (el préstamo se
     * FINALIZÓ pagando la última cuota normal, no con "Cancelar todo") -- no debe mencionar
     * ninguna constancia de cancelación. No hay caso real en pantalla hoy para probar este texto
     * exacto, por eso la prueba unitaria. */
    @Test
    public void testTextoCierreDevolucionSinCancelacionNoMencionaNingunaConstancia() {
        String texto = servicio.construirTextoCierreOperacionDevolucion(false, "-");

        assertEquals(", por haber sido pagada en su totalidad la operación.", texto);
    }

    @Test
    public void testTextoCierreDevolucionConCancelacionCitaElNumeroReal() {
        String texto = servicio.construirTextoCierreOperacionDevolucion(true, "CAN-2026-000001");

        assertEquals(", por haber sido cancelada en su totalidad la operación "
                + "(Constancia de cancelación N.º CAN-2026-000001).", texto);
    }

    // ==================================================================
    // PA-31 (14/10/2026, pedido de Joel, autorizado): validarPendienteDesembolso. Solo los 2
    // caminos que NO tocan la base (PENDIENTE_DESEMBOLSO -- no rechaza; ANULADO_ANTES_DESEMBOLSO
    // -- rechaza con mensaje genérico, sin consultar desembolsoDAO). El camino "ya desembolsado"
    // (ACTIVO/EN_MORA/FINALIZADO) consulta la base para el nombre/fecha reales -- queda fuera de
    // JUnit a propósito (convención de esta sesión: JUnit nunca toca la base), verificado en
    // cambio con la herramienta temporal de rechazo real contra sgcpc_db_pruebas.

    @Test
    public void testValidarPendienteDesembolsoNoRechazaSiEstaPendiente() throws Exception {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(1L);
        prestamo.setEstadoPrestamo(py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo.PENDIENTE_DESEMBOLSO);

        servicio.validarPendienteDesembolso(null, prestamo);
        // No lanza -- si llegara a tocar "conexion" (null), habría explotado con NullPointerException.
    }

    @Test
    public void testValidarPendienteDesembolsoRechazaAnuladoConMensajeGenerico() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(1L);
        prestamo.setEstadoPrestamo(py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO);

        py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException error = assertThrows(
                py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException.class,
                () -> servicio.validarPendienteDesembolso(null, prestamo));

        assertEquals("Este préstamo ya no está pendiente de desembolso. No se pueden regenerar sus documentos.",
                error.getMessage());
    }

    // ======================================================================
    // B84 (decisión de Joel, 28/10/2026): RN-01 al formalizar -- el texto exacto
    // ======================================================================

    /**
     * Lo único de B84 que se puede aislar sin base: que el mensaje no se desvíe del acordado con
     * Joel el 28/10/2026 (en usted, con el "qué sigue", sin la sigla interna "RN-01" -- punto 11
     * del Bloque 7/E10). El bloqueo en sí, y que la solicitud que se está formalizando no se
     * bloquee a sí misma, necesitan base y los cubre {@link PruebaRN01AprobarFormalizar}.
     */
    @Test
    public void testMensajePrestamoAbiertoAlFormalizar() {
        assertEquals("El cliente ya tiene un préstamo no cerrado. No se puede formalizar. Para pedir "
                + "otro crédito, primero debe terminar de pagarlo o cancelarlo.",
                ServicioFormalizacion.MENSAJE_PRESTAMO_ABIERTO_FORMALIZAR);
    }

    /**
     * Los dos momentos nuevos de RN-01 (aprobar y formalizar) comparten el texto base y el "qué
     * sigue": solo cambia la acción. Si alguien reescribe uno de los dos, acá se nota.
     */
    @Test
    public void testLosDosMensajesNuevosDeRN01SoloSeDiferencianEnLaAccion() {
        String aprobar = ServicioAprobacionCredito.MENSAJE_PRESTAMO_ABIERTO_APROBAR;
        String formalizar = ServicioFormalizacion.MENSAJE_PRESTAMO_ABIERTO_FORMALIZAR;

        assertTrue(aprobar.startsWith("El cliente ya tiene un préstamo no cerrado."));
        assertTrue(formalizar.startsWith("El cliente ya tiene un préstamo no cerrado."));
        assertEquals(aprobar.replace("No se puede aprobar.", "No se puede formalizar."), formalizar);
        assertFalse(formalizar.contains("RN-01"));
    }

    // ======================================================================
    // C3 (29/10/2026): la cláusula SEGUNDA del contrato tiene que describir EXACTAMENTE cómo
    // ancla el plan las cuotas siguientes -- antes decía "el mismo día de cada mes", que es falso
    // cuando la fecha elegida cae en día no hábil (el plan ancla al día ELEGIDO, no al corrido).
    // ======================================================================

    @Test
    public void testClausulaSegundaNombraElDiaElegidoNoLaFechaCorrida() {
        // El caso que encontró el recorrido: elegida sábado 21/11, efectiva lunes 23/11. El
        // contrato dice que las siguientes vencen el día 21, que es lo que de verdad hace el plan.
        assertEquals("La primera cuota vence el 23/11/2026 y las siguientes el día 21 de cada mes. "
                + "Cuando un vencimiento cae en sábado, domingo o feriado, se traslada al siguiente "
                + "día hábil.",
                servicio.textoVencimientosClausulaSegunda("23/11/2026", 21));
    }

    @Test
    public void testClausulaSegundaSinAclaracionDeFinDeMesHastaElDia28() {
        // Con 28 o menos, "el último día del mes si ese mes no lo tiene" nunca puede aplicar
        // (todos los meses tienen 28): la aclaración sería ruido en un texto legal.
        assertFalse(servicio.textoVencimientosClausulaSegunda("28/02/2027", 28).contains("último día del mes"));
        assertFalse(servicio.textoVencimientosClausulaSegunda("15/12/2026", 15).contains("último día del mes"));
    }

    @Test
    public void testClausulaSegundaAclaraElFinDeMesDesdeElDia29() {
        // Días 29, 30 y 31: LocalDate.plusMonths resuelve la fecha inexistente al último día del
        // mes (lo verifica ServicioMotorFinancieroTest sobre el plan real), y el contrato lo dice.
        for (int dia : new int[] {29, 30, 31}) {
            String texto = servicio.textoVencimientosClausulaSegunda("31/01/2027", dia);
            assertTrue("Falta la aclaración de fin de mes para el día " + dia,
                    texto.contains("y las siguientes el día " + dia + " de cada mes, o el último día del mes "
                            + "si ese mes no lo tiene."));
            assertTrue(texto.endsWith("Cuando un vencimiento cae en sábado, domingo o feriado, se traslada "
                    + "al siguiente día hábil."));
        }
    }

    // ======================================================================
    // C12 (29/10/2026): la fecha EFECTIVA de la cuota 1, un solo dueño para los tres documentos.
    // ======================================================================

    @Test
    public void testPrimeraFechaVencimientoEfectivaSaleDeLaCuota1() {
        PrestamoCredito prestamo = new PrestamoCredito();
        // La ELEGIDA al formalizar: sábado.
        prestamo.setPrimeraFechaVencimiento(LocalDate.of(2026, 11, 28));
        py.com.capitalcredito.sgcpc.modelos.Cuota cuota1 = new py.com.capitalcredito.sgcpc.modelos.Cuota();
        cuota1.setNumeroCuota(1);
        // La EFECTIVA, ya corrida por el motor: lunes.
        cuota1.setFechaVencimiento(LocalDate.of(2026, 11, 30));
        py.com.capitalcredito.sgcpc.modelos.Cuota cuota2 = new py.com.capitalcredito.sgcpc.modelos.Cuota();
        cuota2.setNumeroCuota(2);
        cuota2.setFechaVencimiento(LocalDate.of(2026, 12, 28));

        assertEquals(LocalDate.of(2026, 11, 30),
                servicio.primeraFechaVencimientoEfectiva(Arrays.asList(cuota1, cuota2), prestamo));
    }

    @Test
    public void testPrimeraFechaVencimientoEfectivaSinPlanDevuelveLaElegida() {
        // Caso defensivo: sin plan legible, lo único que hay es la elegida -- nunca rompe la
        // emisión del documento.
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setPrimeraFechaVencimiento(LocalDate.of(2026, 11, 28));
        assertEquals(LocalDate.of(2026, 11, 28),
                servicio.primeraFechaVencimientoEfectiva(Collections.emptyList(), prestamo));
        assertEquals(LocalDate.of(2026, 11, 28), servicio.primeraFechaVencimientoEfectiva(null, prestamo));
    }

    // ======================================================================
    // B103 (decision de Joel, 01/11/2026): la FRASE COMPLETA ARMADA de la clausula de apertura.
    // El defecto no estaba en ninguna de las dos mitades por separado -- estaba en la union:
    // el "a los " era fijo aca y el dia 1 viene en singular ("a los primer dia del mes de...").
    // El parametro lo pone el mismo conversor que usa el sistema, no un texto copiado a mano.
    // ======================================================================

    @Test
    public void testLaClausulaDeAperturaConcuerdaElArticuloConElDia() {
        assertEquals("En la ciudad de Asunción, República del Paraguay, al primer día del mes de "
                + "noviembre de dos mil veintiséis, entre:",
                aperturaDelContrato(LocalDate.of(2026, 11, 1)));
        assertEquals("En la ciudad de Asunción, República del Paraguay, a los veintiún días del "
                + "mes de octubre de dos mil veintiséis, entre:",
                aperturaDelContrato(LocalDate.of(2026, 10, 21)));
        assertEquals("En la ciudad de Asunción, República del Paraguay, a los treinta y un días "
                + "del mes de octubre de dos mil veintiséis, entre:",
                aperturaDelContrato(LocalDate.of(2026, 10, 31)));
    }

    private static String aperturaDelContrato(LocalDate formalizacion) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        // Única clave numérica que el armador desreferencia (cláusula SEGUNDA); las demás solo se
        // concatenan y no intervienen en la cláusula de apertura, que es la que se verifica acá.
        p.put("DIA_VENCIMIENTO_ELEGIDO", Integer.valueOf(28));
        p.put("FECHA_FORMALIZACION_LETRAS",
                py.com.capitalcredito.sgcpc.infraestructura.ConversorFechasALetras
                        .comoFechaConArticulo(formalizacion));
        return new ServicioFormalizacion().construirClausulasContrato(p).get(0).getTexto();
    }

    // ======================================================================
    // B100 + B104 (decision de Joel, 01/11/2026): el flujo guiado del tramo
    // "solicitud aprobada -> formalizar -> firmados -> desembolso".
    // ======================================================================

    @Test
    public void testLaSolicitudAprobadaSinFormalizarLlevaAFormalizar() {
        // B104: la ficha decia "Aprobada por ... Monto aprobado: ..." y nada mas, aunque el cartel
        // de exito promete "Ahora se formaliza en Formalizacion".
        PasoGuiado paso = new ServicioFormalizacion().calcularPasoDeLaSolicitudAprobada(7L, null);

        assertEquals(PasoGuiado.Destino.FORMALIZACION_NUEVA, paso.getDestino());
        assertEquals("Formalizar esta solicitud", paso.getEtiquetaBoton());
        assertEquals("/formalizacion/nueva/7", paso.rutaRelativa(1L, "3637936"));
    }

    @Test
    public void testLaSolicitudYaFormalizadaLlevaAlPrestamo() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(6L);
        prestamo.setIdentificadorOperacion("CC-2026-00006");

        PasoGuiado paso = new ServicioFormalizacion().calcularPasoDeLaSolicitudAprobada(7L, prestamo);

        assertEquals(PasoGuiado.Destino.FORMALIZACION_FICHA, paso.getDestino());
        assertEquals("Ver el préstamo CC-2026-00006", paso.getEtiquetaBoton());
        assertEquals("Ya formalizada como CC-2026-00006.", paso.getTexto());
        assertEquals("/formalizacion/6", paso.rutaRelativa(1L, "3637936"));
    }

    @Test
    public void testElAvisoDelPanelNombraLoQueFaltaUnaSolaVez() {
        // B100: la tarjeta decia "1 pendiente - 0 listos" sin nombrar lo que falta. Con dos
        // prestamos a los que les falta lo mismo, el tipo se nombra UNA vez.
        List<ServicioFormalizacion.ItemChecklist> unPrestamo = Arrays.asList(
                item("Contrato firmado", false), item("Pagaré firmado", true));
        List<ServicioFormalizacion.ItemChecklist> otroPrestamo = Arrays.asList(
                item("Contrato firmado", false), item("Plan de pagos firmado", false));

        assertEquals("falta cargar: Contrato firmado, Plan de pagos firmado",
                ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar(
                        Arrays.asList(unPrestamo, otroPrestamo)));
    }

    @Test
    public void testElAvisoUsaElNombreDelTipoYNoLaEtiquetaDelChecklist() {
        // La etiqueta del checklist ya trae " (firmado por el cliente)": en un aviso quedaria
        // "Contrato firmado (firmado por el cliente)".
        assertEquals("Contrato firmado",
                ServicioFormalizacion.nombrarLoQueFalta(Arrays.asList(item("Contrato firmado", false))));
    }

    @Test
    public void testSinNadaPendienteElPanelNoAgregaNingunAviso() {
        assertEquals("", ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar(
                Arrays.asList(Arrays.asList(item("Contrato firmado", true)))));
        assertEquals("", ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar(
                Collections.<List<ServicioFormalizacion.ItemChecklist>>emptyList()));
    }

    // ==================================================================
    // B182 (14/11/2026): el renglón de motivos de "Generar documentos".
    // Los cuatro casos posibles de cardinalidad (0, 1 por cada motivo y 2), que es lo que la
    // lección de B103/B117 pide probar en un texto armado por concatenación, más el borde del
    // límite (igual al límite NO es superarlo).
    // ==================================================================

    @Test
    public void testSinBloqueoElRenglonDeMotivosNoDiceNada() {
        ServicioFormalizacion.ResumenFormalizacion resumen = resumen("0.20", null);
        assertFalse(resumen.hayBloqueoConocido());
        assertEquals("", resumen.nombrarMotivosDeBloqueo());
        assertTrue(resumen.getMotivosDeBloqueo().isEmpty());
    }

    @Test
    public void testAfectacionIgualAlLimiteNoEsBloqueo() {
        // El servidor rechaza con compareTo > 0 (revalidarCapacidadPagoFormalizacion): justo en el
        // límite se puede formalizar, así que la pantalla no puede decir lo contrario.
        assertFalse(resumen("0.40", null).hayBloqueoConocido());
    }

    @Test
    public void testSoloElLimiteDaUnMotivo() {
        ServicioFormalizacion.ResumenFormalizacion resumen = resumen("0.55", null);
        assertTrue(resumen.hayBloqueoConocido());
        assertEquals("la afectación supera el límite", resumen.nombrarMotivosDeBloqueo());
    }

    @Test
    public void testSoloElComprobanteDaUnMotivo() {
        ServicioFormalizacion.ResumenFormalizacion resumen = resumen("0.20",
                "El cliente no presenta comprobante de ingreso.");
        assertTrue(resumen.hayBloqueoConocido());
        assertEquals("falta el comprobante de ingreso", resumen.nombrarMotivosDeBloqueo());
    }

    @Test
    public void testLosDosMotivosSeUnenConUnaY() {
        ServicioFormalizacion.ResumenFormalizacion resumen = resumen("0.55",
                "El cliente no presenta comprobante de ingreso.");
        assertEquals(2, resumen.getMotivosDeBloqueo().size());
        assertEquals("la afectación supera el límite y falta el comprobante de ingreso",
                resumen.nombrarMotivosDeBloqueo());
    }

    /** B182: límite fijo en 40 %, que es el valor de credito.limite_afectacion; lo que varía es la
     * afectación y si hay mensaje de comprobante -- los dos únicos datos que deciden el renglón. */
    private static ServicioFormalizacion.ResumenFormalizacion resumen(String porcentajeAfectacion,
            String mensajeSinComprobante) {
        return new ServicioFormalizacion.ResumenFormalizacion(new BigDecimal("300000"),
                new BigDecimal("3600000"), new BigDecimal("0.62"), new BigDecimal(porcentajeAfectacion),
                new BigDecimal("0.40"), mensajeSinComprobante);
    }

    private static ServicioFormalizacion.ItemChecklist item(String nombreTipo, boolean cumplido) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setNombreTipo(nombreTipo);
        return new ServicioFormalizacion.ItemChecklist(tipo, cumplido,
                nombreTipo + " (firmado por el cliente)", null);
    }
}
