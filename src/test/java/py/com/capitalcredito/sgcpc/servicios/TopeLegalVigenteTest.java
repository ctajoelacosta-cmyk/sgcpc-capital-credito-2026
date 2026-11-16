package py.com.capitalcredito.sgcpc.servicios;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * B224 y B223 (opción C), 15/11/2026. Las dos reglas del <b>tope legal de usura</b>, que es el dato
 * más sensible de {@code /parametros}: es la <b>única</b> fuente del tope (RF39/E1) y lo leen
 * <b>siete</b> lugares del circuito de crédito, así que una regla equivocada acá no se nota en una
 * pantalla: se nota en qué créditos se pueden otorgar.
 *
 * <p><b>B224</b>: cuál es el límite en vigor en una fecha. Antes la consulta era "el último abierto,
 * sin mirar la fecha", así que un límite cargado con {@code vigente_desde} futuro se aplicaba
 * <b>desde hoy</b> — y encima, como el alta cierra automáticamente el anterior el día previo a esa
 * fecha futura, el tope de hoy pasaba a ser el del futuro.</p>
 *
 * <p><b>B223 opción C</b>: con qué fecha se cierra un límite que está siendo reemplazado, para que
 * uno cargado con la tasa equivocada se pueda corregir <b>el mismo día</b>. Antes no se podía: el
 * cierre no se aplicaba y la validación de superposición rechazaba el alta, así que el error de hoy
 * se tapaba recién mañana.</p>
 */
public class TopeLegalVigenteTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);

    private LimiteTasaUsura limite(long id, String desde, String hasta, String tasa) {
        LimiteTasaUsura l = new LimiteTasaUsura();
        l.setIdLimiteTasa(id);
        l.setVigenteDesde(LocalDate.parse(desde));
        l.setVigenteHasta(hasta == null ? null : LocalDate.parse(hasta));
        l.setTasaMaximaTea(new java.math.BigDecimal(tasa));
        return l;
    }

    private List<LimiteTasaUsura> lista(LimiteTasaUsura... limites) {
        List<LimiteTasaUsura> todos = new ArrayList<>();
        Collections.addAll(todos, limites);
        return todos;
    }

    // ---- B224: cuál es el tope en vigor ----

    @Test
    public void elLimiteDeHoySeAplicaHoy() {
        LimiteTasaUsura hoy = limite(1, "2026-11-15", null, "0.273900");
        assertEquals(hoy, LimiteTasaUsuraDAO.elVigenteEn(lista(hoy), HOY));
    }

    @Test
    public void elLimiteYaEmpezadoYSinCierreSigueSiendoElVigente() {
        LimiteTasaUsura actual = limite(1, "2026-10-21", null, "0.273900");
        assertEquals("Es el caso real al 15/11/2026: una sola fila, abierta, desde el 21/10.",
                actual, LimiteTasaUsuraDAO.elVigenteEn(lista(actual), HOY));
    }

    /** El defecto de B224: un tope programado para el futuro NO rige hoy. */
    @Test
    public void unLimiteConFechaFuturaNoSeAplicaHoy() {
        LimiteTasaUsura futuro = limite(2, "2027-01-01", null, "0.200000");
        assertNull("Un límite que empieza el 01/01/2027 no puede ser el tope del 15/11/2026:"
                + " las columnas \"Vigente desde\"/\"Vigente hasta\" de la pantalla prometen"
                + " exactamente eso (B224).", LimiteTasaUsuraDAO.elVigenteEn(lista(futuro), HOY));
    }

    /**
     * El caso que amarra las dos mitades de la condición, y el que Joel pidió verificar: al cargar
     * un tope futuro, el alta <b>cierra</b> el actual el día anterior a esa fecha. Si
     * {@code elVigenteEn} mirara solo {@code vigente_hasta IS NULL}, hoy no habría ningún tope
     * vigente y el circuito de crédito entero se bloquearía con <i>"No hay un límite de tasa
     * vigente cargado"</i>. El que rige hoy es el cerrado que todavía cubre hoy.
     */
    @Test
    public void conUnTopeFuturoProgramadoElDeHoySigueSiendoElCerradoQueCubreHoy() {
        LimiteTasaUsura actual = limite(1, "2026-10-21", "2026-12-31", "0.273900");
        LimiteTasaUsura futuro = limite(2, "2027-01-01", null, "0.200000");
        assertEquals("El cierre automático del alta tiene que seguir funcionando: hoy rige el de"
                + " 27,39 %, no el programado para 2027.",
                actual, LimiteTasaUsuraDAO.elVigenteEn(lista(actual, futuro), HOY));
    }

    @Test
    public void eseMismoTopeFuturoSiRigeCuandoLlegaSuFecha() {
        LimiteTasaUsura actual = limite(1, "2026-10-21", "2026-12-31", "0.273900");
        LimiteTasaUsura futuro = limite(2, "2027-01-01", null, "0.200000");
        assertEquals(futuro, LimiteTasaUsuraDAO.elVigenteEn(lista(actual, futuro),
                LocalDate.of(2027, 1, 1)));
    }

    @Test
    public void unLimiteYaTerminadoNoRigeMas() {
        LimiteTasaUsura terminado = limite(1, "2025-01-01", "2026-10-20", "0.300000");
        assertNull(LimiteTasaUsuraDAO.elVigenteEn(lista(terminado), HOY));
    }

    @Test
    public void sinNingunLimiteCargadoNoHayVigente() {
        assertNull(LimiteTasaUsuraDAO.elVigenteEn(new ArrayList<LimiteTasaUsura>(), HOY));
    }

    /**
     * Dos límites que empiezan el mismo día: el orden tiene que ser determinista o el tope legal
     * cambiaría entre dos consultas (la lección de B205 con {@code fecha_hora}). Gana el último
     * cargado, que es el id más alto.
     */
    @Test
    public void dosLimitesDelMismoDiaSeDesempatanPorElUltimoCargado() {
        LimiteTasaUsura primero = limite(1, "2026-11-15", null, "0.273900");
        LimiteTasaUsura segundo = limite(2, "2026-11-15", null, "0.250000");
        assertEquals(segundo, LimiteTasaUsuraDAO.elVigenteEn(lista(primero, segundo), HOY));
        assertEquals("Y el resultado no puede depender del orden en que vengan de la base.",
                segundo, LimiteTasaUsuraDAO.elVigenteEn(lista(segundo, primero), HOY));
    }

    // ---- B223 opción C: poder reemplazar un límite el mismo día ----

    @Test
    public void unLimiteQueYaRigioSeCierraElDiaAnteriorAlNuevo() {
        assertEquals("Rigió hasta el día antes de que empiece el nuevo.",
                LocalDate.of(2026, 11, 14),
                ServicioParametros.cierreParaLimiteReemplazado(
                        LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 15)));
    }

    /** El caso de B223: se cargó mal un límite HOY y hoy mismo se carga el corregido. */
    @Test
    public void unLimiteCargadoHoyYReemplazadoHoyQuedaSinVigencia() {
        assertEquals("Nunca rigió: se cierra el día anterior a su propio inicio, así queda con la"
                + " vigencia vacía en vez de bloquear el alta del corregido (B223 opción C).",
                LocalDate.of(2026, 11, 14),
                ServicioParametros.cierreParaLimiteReemplazado(
                        LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));
    }

    /** Y el que estaba programado para el futuro también se tiene que poder reemplazar. */
    @Test
    public void unLimiteProgramadoParaElFuturoTambienQuedaSinVigencia() {
        assertEquals(LocalDate.of(2026, 11, 19),
                ServicioParametros.cierreParaLimiteReemplazado(
                        LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 15)));
    }

    /**
     * La prueba de que la opción C realmente destraba el alta: con el límite equivocado cerrado el
     * día anterior a su propio inicio, la validación de superposición —que es la que hoy rechaza el
     * reemplazo— ya no se queja.
     */
    @Test
    public void conElCierreNuevoElAltaDelCorregidoYaNoSeSuperpone() {
        LocalDate desde = LocalDate.of(2026, 11, 15);
        LimiteTasaUsura equivocado = limite(1, "2026-11-15", "2026-11-14", "0.020000");
        new ServicioParametros().validarSuperposicionLimitesTasa(desde, null, Arrays.asList(equivocado));
    }

    /**
     * El otro caso que la opcion C tiene que destrabar, y que no sale de la aritmetica sola: un
     * limite PROGRAMADO para el futuro, reemplazado hoy. Cerrado el dia anterior a su propio
     * inicio queda con la vigencia vacia, y un rango vacio no se superpone con nada -- sin esa
     * regla en validarSuperposicionLimitesTasa, el alta del corregido seguiria rechazada.
     */
    @Test
    public void unLimiteProgramadoYReemplazadoHoyTampocoBloqueaElAlta() {
        LimiteTasaUsura programado = limite(1, "2026-11-20", "2026-11-19", "0.020000");
        new ServicioParametros().validarSuperposicionLimitesTasa(
                LocalDate.of(2026, 11, 15), null, Arrays.asList(programado));
    }

    /** Y el corregido queda siendo el tope de hoy, que es el punto de todo el ejercicio. */
    @Test
    public void despuesDelReemplazoElTopeDeHoyEsElCorregido() {
        LimiteTasaUsura equivocado = limite(1, "2026-11-15", "2026-11-14", "0.020000");
        LimiteTasaUsura corregido = limite(2, "2026-11-15", null, "0.273900");
        assertEquals(corregido, LimiteTasaUsuraDAO.elVigenteEn(lista(equivocado, corregido), HOY));
    }
}
