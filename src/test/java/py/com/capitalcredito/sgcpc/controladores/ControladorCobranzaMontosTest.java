package py.com.capitalcredito.sgcpc.controladores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;

/**
 * Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel), con la corrección de diseño de
 * esa misma noche (opción B, tras la prueba en pantalla): validación de los parámetros de la vista
 * previa del adelanto y el ahorro informativo.
 * <p>
 * Son las funciones PURAS de {@link ControladorCobranzaServlet} -- sin base de datos y sin
 * contenedor de servlets (REGLAS_DESARROLLO.md §7 punto 9, mismo criterio que las pruebas de
 * {@code ServicioCobranza.calcularRedondeoEfectivo}).
 * <p>
 * El MONTO de "N cuotas" ya no se calcula acá: lo busca
 * {@code ServicioCobranza.buscarMontoMinimoParaDejarCuotas} con el cálculo del motor que ya
 * existe, y se prueba en {@code ServicioCobranzaTest}. El rechazo de D-90 ("El monto cubre todo el
 * saldo del préstamo. Use Cancelación total.") lo cubre {@code ServicioMotorFinancieroTest}.
 */
public class ControladorCobranzaMontosTest {

    /** Cuota de ejemplo: 192.298 Gs. de total programado, el número real de CC-2026-00002. */
    private static final BigDecimal CUOTA = new BigDecimal("192298");

    // ----------------------------------------------------------------
    // cuotasAnticipoValidas -- el servidor nunca calcula con un N inválido
    // ----------------------------------------------------------------

    @Test
    public void unaCantidadDentroDelRangoSeAcepta() {
        assertEquals(Integer.valueOf(1), ControladorCobranzaServlet.cuotasAnticipoValidas("1", 12));
        assertEquals(Integer.valueOf(4), ControladorCobranzaServlet.cuotasAnticipoValidas("4", 12));
        assertEquals(Integer.valueOf(11), ControladorCobranzaServlet.cuotasAnticipoValidas("11", 12));
        // N = K se acepta acá: es el llamador el que lo deriva a la cancelación total.
        assertEquals(Integer.valueOf(12), ControladorCobranzaServlet.cuotasAnticipoValidas("12", 12));
        assertEquals(Integer.valueOf(17), ControladorCobranzaServlet.cuotasAnticipoValidas("  17  ", 24));
    }

    @Test
    public void unaCantidadFueraDeRangoONoNumericaSeRechaza() {
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("0", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("-2", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("13", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("ocho", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("2,5", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("", 12));
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas(null, 12));
    }

    @Test
    public void conMaximoCeroNoSeAceptaNingunaCantidad() {
        assertNull(ControladorCobranzaServlet.cuotasAnticipoValidas("1", 0));
    }

    // ----------------------------------------------------------------
    // montoAnticipoLibreValido -- "por monto"
    // ----------------------------------------------------------------

    @Test
    public void unMontoLibrePositivoSeAcepta() {
        assertEquals(new BigDecimal("500000"), ControladorCobranzaServlet.montoAnticipoLibreValido("500000"));
        // El punto es separador de miles en guaraníes (ConversorNumeros, REGLAS_DESARROLLO.md §5).
        assertEquals(new BigDecimal("500000"), ControladorCobranzaServlet.montoAnticipoLibreValido("500.000"));
    }

    @Test
    public void unMontoLibreInvalidoONoPositivoSeRechaza() {
        // "Por monto" con "Recibido" vacío o en cero: no se calcula ninguna vista previa (lo que
        // encontró Joel el 27/10/2026 -- antes quedaba a la vista una previa de otro monto).
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido("0"));
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido("-500000"));
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido("quinientos mil"));
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido("500000,50"));
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido("   "));
        assertNull(ControladorCobranzaServlet.montoAnticipoLibreValido(null));
    }

    // ----------------------------------------------------------------
    // ahorroDeInteresesPorCuotas -- informativo (RN-11)
    // ----------------------------------------------------------------

    @Test
    public void elAhorroEsLoQueHabriaPagadoPorEsasCuotasMenosLoQuePagaAhora() {
        // 4 cuotas según el plan: 192.298 × 4 = 769.192. Si paga 712.350, se ahorra 56.842.
        assertEquals(new BigDecimal("56842"),
                ControladorCobranzaServlet.ahorroDeInteresesPorCuotas(CUOTA, 4, new BigDecimal("712350")));
    }

    @Test
    public void elAhorroNuncaEsNegativo() {
        // Si el monto fuera mayor que lo que costaban esas cuotas (no pasa en la práctica, porque
        // el adelanto no cobra intereses futuros), no se muestra un "ahorro" al revés.
        assertEquals(BigDecimal.ZERO,
                ControladorCobranzaServlet.ahorroDeInteresesPorCuotas(CUOTA, 1, new BigDecimal("300000")));
        assertEquals(BigDecimal.ZERO, ControladorCobranzaServlet.ahorroDeInteresesPorCuotas(CUOTA, 0, CUOTA));
        assertEquals(BigDecimal.ZERO, ControladorCobranzaServlet.ahorroDeInteresesPorCuotas(null, 4, CUOTA));
        assertEquals(BigDecimal.ZERO, ControladorCobranzaServlet.ahorroDeInteresesPorCuotas(CUOTA, 4, null));
    }

    // ----------------------------------------------------------------
    // recibidoNoAlcanzaParaLasCuotas -- decisión de Joel del 27/10/2026: en "Por cantidad de
    // cuotas" el servidor rechaza un monto menor al de esa cantidad, en vez de aplicar menos en
    // silencio. En "Por monto" sigue intacto el min() de D-145 (P-5).
    // ----------------------------------------------------------------

    @Test
    public void porCantidadDeCuotasSeRechazaSiLoRecibidoNoAlcanza() {
        assertTrue(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas",
                new BigDecimal("500000"), new BigDecimal("697950")));
    }

    @Test
    public void porCantidadDeCuotasSeAceptaSiLoRecibidoAlcanzaOSobra() {
        // Justo el monto: sin vuelto.
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas",
                new BigDecimal("697950"), new BigDecimal("697950")));
        // De más: el excedente es vuelto, como siempre.
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas",
                new BigDecimal("1000000"), new BigDecimal("697950")));
        // Un guaraní de menos sí alcanza para rechazar: el monto es el mínimo que logra esas cuotas.
        assertTrue(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas",
                new BigDecimal("697949"), new BigDecimal("697950")));
    }

    @Test
    public void porMontoNuncaSeRechaza() {
        // "Por monto" es justamente la opción donde la cajera aplica un monto libre: el min() de
        // D-145 (P-5) sigue valiendo sin cambios.
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("monto",
                new BigDecimal("500000"), new BigDecimal("697950")));
    }

    @Test
    public void unModoAusenteOInvalidoNoEndureceNada() {
        // Nunca se endurece un camino que ya funcionaba: sin modo (o con uno manipulado) se
        // comporta como antes de esta decisión.
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas(null,
                new BigDecimal("500000"), new BigDecimal("697950")));
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("",
                new BigDecimal("500000"), new BigDecimal("697950")));
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("CUOTAS",
                new BigDecimal("500000"), new BigDecimal("697950")));
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas", null,
                new BigDecimal("697950")));
        assertFalse(ControladorCobranzaServlet.recibidoNoAlcanzaParaLasCuotas("cuotas",
                new BigDecimal("500000"), null));
    }

    @Test
    public void elMensajeDelServidorDiceLosDosMontosYLaSalida() {
        assertEquals("Lo recibido (500.000 Gs.) no alcanza para la cantidad de cuotas elegida"
                        + " (697.950 Gs.). Elija menos cuotas o use «Por monto».",
                ControladorCobranzaServlet.mensajeNoAlcanzaParaLasCuotas(new BigDecimal("500000"),
                        new BigDecimal("697950")));
    }

    // ----------------------------------------------------------------
    // conNombresDeEstaPantalla -- corrección de Joel del 27/10/2026
    // ----------------------------------------------------------------

    @Test
    public void elRechazoDeD90NombraElBotonDeEstaPantalla() {
        // El motor dice "Use Cancelación total."; en esta pantalla ese botón se llama
        // «Todo el préstamo». Se traduce solo para la vista: el texto de la excepción no cambia,
        // porque D-90 protege a cualquier llamador del motor.
        assertEquals("El monto cubre todo el saldo del préstamo. Elija «Todo el préstamo».",
                ControladorCobranzaServlet.conNombresDeEstaPantalla(
                        "El monto cubre todo el saldo del préstamo. Use Cancelación total."));
    }

    @Test
    public void losDemasMensajesDeBloqueoNoSeTocan() {
        // D-88 y el mínimo de interés del período se muestran tal como los da el servicio.
        assertEquals("Primero hay que cobrar la cuota del 23/11/2026.",
                ControladorCobranzaServlet.conNombresDeEstaPantalla("Primero hay que cobrar la cuota del 23/11/2026."));
        assertNull(ControladorCobranzaServlet.conNombresDeEstaPantalla(null));
    }

    // ----------------------------------------------------------------
    // contarCuotasPendientes -- las cuotas que le quedan (el tope K del control)
    // ----------------------------------------------------------------

    @Test
    public void seCuentanLasCuotasQueNoEstanPagadas() {
        Prestamo prestamo = new Prestamo();
        prestamo.setCuotas(Arrays.asList(cuotaCon(EstadoPago.PAGADA), cuotaCon(EstadoPago.PAGADA),
                cuotaCon(EstadoPago.PARCIAL), cuotaCon(EstadoPago.PENDIENTE), cuotaCon(EstadoPago.PENDIENTE)));
        // Una cuota PARCIAL todavía se le debe: cuenta como pendiente.
        assertEquals(3, ControladorCobranzaServlet.contarCuotasPendientes(prestamo));
    }

    @Test
    public void sinCuotasOSinPrestamoSeCuentaCero() {
        Prestamo sinCuotas = new Prestamo();
        sinCuotas.setCuotas(new ArrayList<Cuota>());
        assertEquals(0, ControladorCobranzaServlet.contarCuotasPendientes(sinCuotas));
        assertEquals(0, ControladorCobranzaServlet.contarCuotasPendientes(null));

        Prestamo todasPagadas = new Prestamo();
        List<Cuota> cuotas = new ArrayList<>();
        cuotas.add(cuotaCon(EstadoPago.PAGADA));
        todasPagadas.setCuotas(cuotas);
        assertEquals(0, ControladorCobranzaServlet.contarCuotasPendientes(todasPagadas));
    }

    private Cuota cuotaCon(EstadoPago estado) {
        Cuota cuota = new Cuota();
        cuota.setEstadoPago(estado);
        return cuota;
    }
}
