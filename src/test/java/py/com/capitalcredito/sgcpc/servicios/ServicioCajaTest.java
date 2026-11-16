package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.excepciones.CajaDeOtroDiaException;
import py.com.capitalcredito.sgcpc.excepciones.MotivoDiferenciaInsuficienteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/** E6 (P07): reglas puras de ServicioCaja. */
public class ServicioCajaTest {

    private final ServicioCaja servicio = new ServicioCaja();

    @Test
    public void testDiferenciaDentroDeLaToleranciaNoExigeMotivo() {
        servicio.validarMotivoDiferencia(new BigDecimal("500"), new BigDecimal("500"), null);
    }

    @Test
    public void testDiferenciaFueraDeLaToleranciaSinMotivoSeRechaza() {
        assertThrows(MotivoDiferenciaInsuficienteException.class,
                () -> servicio.validarMotivoDiferencia(new BigDecimal("1000"), BigDecimal.ZERO, ""));
    }

    @Test
    public void testDiferenciaFueraDeLaToleranciaConMotivoNoLanza() {
        servicio.validarMotivoDiferencia(new BigDecimal("-1000"), BigDecimal.ZERO, "Faltante por vuelto mal dado.");
    }

    /** RF19 (pedido de Joel, 11/10/2026): mínimo de 10 caracteres reales -- ni "." ni "aaa"
     * (ambos mucho más cortos) alcanzan, y el límite exacto (9 vs. 10) está probado aparte. */
    @Test
    public void testDiferenciaFueraDeLaToleranciaConUnPuntoSeRechaza() {
        assertThrows(MotivoDiferenciaInsuficienteException.class,
                () -> servicio.validarMotivoDiferencia(new BigDecimal("10000"), BigDecimal.ZERO, "."));
    }

    @Test
    public void testDiferenciaFueraDeLaToleranciaConNueveCaracteresSeRechaza() {
        assertThrows(MotivoDiferenciaInsuficienteException.class,
                () -> servicio.validarMotivoDiferencia(new BigDecimal("10000"), BigDecimal.ZERO, "123456789"));
    }

    @Test
    public void testDiferenciaFueraDeLaToleranciaConDiezCaracteresNoLanza() {
        servicio.validarMotivoDiferencia(new BigDecimal("10000"), BigDecimal.ZERO, "1234567890");
    }

    @Test
    public void testMensajeMotivoInsuficienteSobrante() {
        String mensaje = servicio.construirMensajeMotivoInsuficiente(new BigDecimal("10000"));

        assertEquals("Hay una diferencia de 10.000 Gs. (Sobra). Explique el motivo con al menos 10 caracteres.",
                mensaje);
    }

    @Test
    public void testMensajeMotivoInsuficienteFaltante() {
        String mensaje = servicio.construirMensajeMotivoInsuficiente(new BigDecimal("-5000"));

        assertEquals("Hay una diferencia de 5.000 Gs. (Falta). Explique el motivo con al menos 10 caracteres.",
                mensaje);
    }

    /** RF19 (pedido de Joel, 11/10/2026): mensaje puro de la carrera de dos pestañas al cerrar
     * caja -- separado de construirErrorCajaYaCerrada (que necesita Connection) para poder
     * probarlo sin base de datos, mismo criterio que construirMensajeYaDecidida (RF09). */
    @Test
    public void testMensajeCajaYaCerradaConUsuario() {
        SesionCaja sesion = new SesionCaja();
        sesion.setFechaHoraCierre(LocalDateTime.of(2026, 10, 11, 18, 30));
        Usuario quienCerro = new Usuario();
        quienCerro.setNombres("Sofía");
        quienCerro.setApellidos("Martínez");

        String mensaje = servicio.construirMensajeCajaYaCerrada(sesion, quienCerro);

        assertEquals("Esta caja ya fue cerrada por Sofía Martínez el 11/10/2026 18:30. Actualice la página.",
                mensaje);
    }

    @Test
    public void testMensajeCajaYaCerradaSinUsuario() {
        SesionCaja sesion = new SesionCaja();
        sesion.setFechaHoraCierre(LocalDateTime.of(2026, 10, 11, 18, 30));

        String mensaje = servicio.construirMensajeCajaYaCerrada(sesion, null);

        assertEquals("Esta caja ya fue cerrada por otro usuario el 11/10/2026 18:30. Actualice la página.",
                mensaje);
    }

    @Test
    public void testSesionAbiertaHoyEsDeHoy() {
        SesionCaja sesion = new SesionCaja();
        sesion.setFechaHoraApertura(LocalDateTime.now());
        assertTrue(servicio.esDeHoy(sesion));
    }

    @Test
    public void testSesionAbiertaAyerNoEsDeHoy() {
        SesionCaja sesion = new SesionCaja();
        sesion.setFechaHoraApertura(LocalDate.now().minusDays(1).atTime(9, 0));
        assertFalse(servicio.esDeHoy(sesion));
    }

    /** Duplicaciones y parches (Commit 1): único lugar que decide el mensaje y lanza
     * CajaDeOtroDiaException -- antes ServicioCobranza/ServicioDesembolso reconstruían el mismo
     * mensaje a mano, y ServicioCobranza llegaba a lanzar NegocioException genérica en vez de
     * CajaDeOtroDiaException. */
    @Test
    public void testValidarSesionDeHoyConSesionDeAyerLanza() {
        SesionCaja sesion = new SesionCaja();
        LocalDate ayer = LocalDate.now().minusDays(1);
        sesion.setFechaHoraApertura(ayer.atTime(9, 0));

        CajaDeOtroDiaException error = assertThrows(CajaDeOtroDiaException.class,
                () -> servicio.validarSesionDeHoy(sesion));

        assertEquals("La caja del " + FormateadorFechas.comoDdMmAaaa(ayer)
                + " sigue abierta: ciérrela y rinda antes de operar hoy.", error.getMessage());
    }

    @Test
    public void testValidarSesionDeHoyConSesionDeHoyNoLanza() {
        SesionCaja sesion = new SesionCaja();
        sesion.setFechaHoraApertura(LocalDateTime.now());
        servicio.validarSesionDeHoy(sesion);
    }

    private static MovimientoCaja movimiento(TipoMovimientoCaja tipo, String monto, int signo) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setTipo(tipo);
        movimiento.setMonto(new BigDecimal(monto));
        movimiento.setSigno(signo);
        return movimiento;
    }

    /**
     * E-6 (pedido de Joel): el RESUMEN del PDF del arqueo no cuadraba contra SALDO TEÓRICO
     * cuando había una entrega del gerente, porque ENTREGA_GERENTE no tenía línea propia en el
     * resumen aunque sí afecta el saldo teórico (signo +1, movimientos_caja.signo). Esto prueba
     * la propiedad que el resumen tiene que cumplir SIEMPRE: fondo inicial + suma de las líneas
     * "(+)" − suma de las líneas "(−)" = saldo teórico -- la misma cuenta que calcularSaldoTeorico/
     * cerrarCaja hacen con SUM(monto*signo) contra la base real, replicada acá en memoria para
     * poder probarla sin tocarla (REGLAS_DESARROLLO.md §7 punto 9). Con los 6 tipos reales de
     * TipoMovimientoCaja, incluida una entrega del gerente (el caso reportado) y una
     * compensación por reversión (el mismo problema, encontrado al revisar todos los tipos).
     * <p>
     * RENDICION_GERENTE queda deliberadamente afuera de las dos sumas: no es parte del saldo
     * teórico del día, es la salida del cierre -- cerrarCaja calcula y guarda
     * saldo_teorico_cierre ANTES de insertar ese movimiento (ServicioCaja.cerrarCaja, líneas
     * 237-255), así que nunca lo restó. Esta prueba no reemplaza una corrida real del arqueo
     * (eso necesita la base, ver generarPdfArqueo) -- confirma la propiedad aritmética que el
     * resumen tiene que respetar para que Joel no vuelva a encontrar esta diferencia.
     */
    @Test
    public void testResumenDelArqueoCuadraConSaldoTeorico_ConEntregaDelGerente() {
        BigDecimal saldoInicial = new BigDecimal("500000");
        List<MovimientoCaja> movimientosDelDia = new ArrayList<>();
        movimientosDelDia.add(movimiento(TipoMovimientoCaja.ENTREGA_GERENTE, "100000", 1));
        movimientosDelDia.add(movimiento(TipoMovimientoCaja.COBRO, "80000", 1));
        movimientosDelDia.add(movimiento(TipoMovimientoCaja.VUELTO, "5000", -1));
        movimientosDelDia.add(movimiento(TipoMovimientoCaja.DESEMBOLSO, "150000", -1));
        movimientosDelDia.add(movimiento(TipoMovimientoCaja.COMPENSACION, "20000", -1));

        // Lo mismo que sesion.getSaldoInicial().add(movimientoCajaDAO.sumarMovimientos(...)) de
        // calcularSaldoTeorico/cerrarCaja -- la fuente de verdad, por signo, sin agrupar por tipo.
        BigDecimal saldoTeoricoPorSigno = saldoInicial;
        for (MovimientoCaja movimiento : movimientosDelDia) {
            saldoTeoricoPorSigno = saldoTeoricoPorSigno.add(
                    movimiento.getMonto().multiply(BigDecimal.valueOf(movimiento.getSigno())));
        }

        // Lo mismo que el RESUMEN del PDF: fondo inicial + cada línea "(+)" − cada línea "(−)".
        BigDecimal sumaLineasPositivas = BigDecimal.ZERO;
        BigDecimal sumaLineasNegativas = BigDecimal.ZERO;
        for (MovimientoCaja movimiento : movimientosDelDia) {
            if (movimiento.getSigno() > 0) {
                sumaLineasPositivas = sumaLineasPositivas.add(movimiento.getMonto());
            } else {
                sumaLineasNegativas = sumaLineasNegativas.add(movimiento.getMonto());
            }
        }
        BigDecimal saldoTeoricoPorResumen = saldoInicial.add(sumaLineasPositivas).subtract(sumaLineasNegativas);

        assertEquals("fondo inicial + (+) − (−) tiene que dar lo mismo que SUM(monto*signo)",
                0, saldoTeoricoPorSigno.compareTo(saldoTeoricoPorResumen));
        assertEquals(0, new BigDecimal("505000").compareTo(saldoTeoricoPorSigno));

        // RENDICION_GERENTE no participa del saldo teórico del día -- si se la sumara (mal) acá,
        // este valor ya no coincidiría con el calculado arriba.
        MovimientoCaja rendicion = movimiento(TipoMovimientoCaja.RENDICION_GERENTE, "505000", -1);
        BigDecimal siSeContaraLaRendicion = saldoTeoricoPorSigno
                .add(rendicion.getMonto().multiply(BigDecimal.valueOf(rendicion.getSigno())));
        assertTrue("si se contara la rendición, el total ya no cuadraría con el teórico real",
                siSeContaraLaRendicion.compareTo(saldoTeoricoPorSigno) != 0);
    }

    // ======================================================================
    // C22 (decisión de Joel, 03/11/2026): el acta de arqueo no puede afirmar una recepción que
    // nadie hizo. El nombre del gerente sale SOLO de id_gerente_revisor (lo único que el sistema
    // registra de verdad sobre esta sesión); sin eso, la línea va en blanco para firmar a mano.
    // ======================================================================

    @Test
    public void testElActaNombraAlGerenteSoloSiEstaRegistradoEnLaSesion() {
        assertEquals("Recibí: Ramón Benítez", ServicioCaja.lineaRecibiDelArqueo("Ramón Benítez"));
    }

    @Test
    public void testSinGerenteRegistradoElActaDejaLaFirmaEnBlanco() {
        // El caso de TODA sesión sin diferencia (la gran mayoría) y también el de una diferencia
        // todavía sin revisar: antes las dos imprimían el nombre del parámetro
        // caja.gerente_predeterminado, como si ese gerente hubiera contado el efectivo.
        String sinNadie = ServicioCaja.lineaRecibiDelArqueo(null);

        // Sin guiones bajos (decisión de Joel del 03/11/2026, sobre la muestra): el pie del acta
        // ya trae su raya dibujada arriba -- una línea de guiones duplicaba el lugar de la firma.
        assertEquals("Recibí (firma):", sinNadie);
        assertTrue("la línea en blanco no puede nombrar a nadie", sinNadie.indexOf(':') == sinNadie.lastIndexOf(':'));
        assertFalse("la raya de arriba es el lugar de la firma, no hacen falta guiones bajos",
                sinNadie.contains("_"));
    }

    @Test
    public void testUnNombreVacioOEnBlancoCuentaComoSinGerente() {
        assertEquals(ServicioCaja.lineaRecibiDelArqueo(null), ServicioCaja.lineaRecibiDelArqueo(""));
        assertEquals(ServicioCaja.lineaRecibiDelArqueo(null), ServicioCaja.lineaRecibiDelArqueo("   "));
    }

    @Test
    public void testLaLineaDeFirmaNoTerminaEnPunto() {
        // Es el pie de una firma, no una oración: un punto ahí quedaría pegado a la línea.
        assertTrue(!ServicioCaja.lineaRecibiDelArqueo("Ramón Benítez").endsWith("."));
    }

    // ==================================================================
    // B52 (B86, tramo 3): un monto cero no lleva signo. La expresión anterior miraba solo el signo
    // del movimiento, así que una rendición al gerente de cero salía "-0 Gs." y se leía como un
    // error de cuentas. En la base hay 11 movimientos con monto cero y signo negativo
    // (RENDICION_GERENTE), y la misma expresión estaba escrita dos veces: en la tabla de
    // movimientos de la pantalla y en el detalle del arqueo en PDF.
    // ==================================================================

    @Test
    public void testMontoConSigno_CeroNoLlevaSigno() {
        // El caso del defecto: signo negativo pero monto cero.
        assertEquals("0 Gs.", ServicioCaja.montoConSigno(-1, BigDecimal.ZERO));
        assertFalse(ServicioCaja.montoConSigno(-1, BigDecimal.ZERO).startsWith("-"));
        // Y da lo MISMO que con signo positivo: un cero es un cero.
        assertEquals(ServicioCaja.montoConSigno(1, BigDecimal.ZERO),
                ServicioCaja.montoConSigno(-1, BigDecimal.ZERO));
    }

    @Test
    public void testMontoConSigno_LoQueSaleLlevaMenosYLoQueEntraNo() {
        // Lo que no cambió: el signo sigue estando donde corresponde.
        assertEquals("-1.377.000 Gs.", ServicioCaja.montoConSigno(-1, new BigDecimal("1377000")));
        assertEquals("1.377.000 Gs.", ServicioCaja.montoConSigno(1, new BigDecimal("1377000")));
    }

    @Test
    public void testMontoConSigno_UnMontoNuloNoRompe() {
        // Defensa: ningún movimiento de la base tiene monto nulo, pero el formateador decide qué
        // mostrar -- lo que no puede pasar es que se le pegue un "-" adelante.
        assertFalse(ServicioCaja.montoConSigno(-1, null).startsWith("-"));
    }
}
