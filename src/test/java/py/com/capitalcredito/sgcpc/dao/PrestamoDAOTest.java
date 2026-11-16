package py.com.capitalcredito.sgcpc.dao;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * E-13 (pedido de Joel): las dos transiciones puras de estado_cierre_documental -- al finalizar un
 * préstamo (NO_APLICA -> PENDIENTE) y al revertir esa finalización (PENDIENTE -> NO_APLICA) --
 * espejo exacto del CASE SQL que usan PrestamoDAO.marcarFinalizado/revertirFinalizacion, para poder
 * probarlas sin tocar la base (mismo criterio que el resto del proyecto).
 */
public class PrestamoDAOTest {

    // siguienteCierreDocumentalAlFinalizar -- NO_APLICA -> PENDIENTE al finalizar un préstamo.

    @Test
    public void testSiguienteCierreDocumentalAlFinalizarDesdeNoAplicaPasaAPendiente() {
        assertEquals("PENDIENTE", PrestamoDAO.siguienteCierreDocumentalAlFinalizar("NO_APLICA"));
    }

    // No debería poder darse (marcarFinalizado solo se llama al transicionar DESDE ACTIVO/EN_MORA,
    // donde el cierre documental siempre sigue en NO_APLICA) -- pero si pasara, no se pisa hacia atrás.
    @Test
    public void testSiguienteCierreDocumentalAlFinalizarDesdePendienteQuedaIgual() {
        assertEquals("PENDIENTE", PrestamoDAO.siguienteCierreDocumentalAlFinalizar("PENDIENTE"));
    }

    @Test
    public void testSiguienteCierreDocumentalAlFinalizarDesdeCompletoQuedaIgual() {
        assertEquals("COMPLETO", PrestamoDAO.siguienteCierreDocumentalAlFinalizar("COMPLETO"));
    }

    // siguienteCierreDocumentalAlRevertir -- PENDIENTE -> NO_APLICA al revertir una finalización.

    @Test
    public void testSiguienteCierreDocumentalAlRevertirDesdePendientePasaANoAplica() {
        assertEquals("NO_APLICA", PrestamoDAO.siguienteCierreDocumentalAlRevertir("PENDIENTE"));
    }

    // Caso de Joel: "si ya estaba en COMPLETO, no lo toques" -- revertir con el pagaré ya
    // devuelto no debería poder darse, pero si pasara, el cierre documental queda tal cual
    // (ServicioReversiones es quien lo marca en el detalle de auditoría, no esta función).
    @Test
    public void testSiguienteCierreDocumentalAlRevertirDesdeCompletoQuedaIgual() {
        assertEquals("COMPLETO", PrestamoDAO.siguienteCierreDocumentalAlRevertir("COMPLETO"));
    }

    // No debería poder darse (un préstamo FINALIZADO nunca quedó en NO_APLICA desde E-13) -- pero
    // si pasara, no hay nada que revertir, queda igual.
    @Test
    public void testSiguienteCierreDocumentalAlRevertirDesdeNoAplicaQuedaIgual() {
        assertEquals("NO_APLICA", PrestamoDAO.siguienteCierreDocumentalAlRevertir("NO_APLICA"));
    }
}
