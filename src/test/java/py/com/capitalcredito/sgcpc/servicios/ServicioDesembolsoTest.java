package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/** E6 (P08): reglas puras de ServicioDesembolso. */
public class ServicioDesembolsoTest {

    private final ServicioDesembolso servicio = new ServicioDesembolso();

    @Test
    public void testDocumentoRecienGeneradoEstaVigente() {
        assertTrue(servicio.validarDocumentosVigentes(LocalDate.now(), 5));
    }

    @Test
    public void testDocumentoDentroDelPlazoEstaVigente() {
        assertTrue(servicio.validarDocumentosVigentes(LocalDate.now().minusDays(5), 5));
    }

    @Test
    public void testDocumentoVencidoNoEstaVigente() {
        assertFalse(servicio.validarDocumentosVigentes(LocalDate.now().minusDays(6), 5));
    }

    @Test
    public void testSinDocumentoGeneradoNoEstaVigente() {
        assertFalse(servicio.validarDocumentosVigentes(null, 5));
    }

    @Test
    public void testMensajeSaldoInsuficienteConLosTresMontos() {
        String mensaje = servicio.construirMensajeSaldoInsuficiente(new BigDecimal("2000000"), new BigDecimal("3000000"));
        assertEquals("Hay 2.000.000 Gs. en caja y el desembolso es de 3.000.000 Gs. Faltan 1.000.000 Gs. "
                + "Pedí al gerente que entregue efectivo.", mensaje);
    }

    // --- RF14 (pedido de Joel, 11/10/2026): carrera anular vs. desembolsar, mensaje con quién y cuándo ---

    @Test
    public void testConstruirMensajePrestamoYaAnuladoConUsuario() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setFechaAnulacionSinDesembolso(LocalDateTime.of(2026, 10, 11, 17, 8));
        Usuario gerente = new Usuario();
        gerente.setNombres("Ramón");
        gerente.setApellidos("Benítez");

        String mensaje = servicio.construirMensajePrestamoYaAnulado(prestamo, gerente);

        assertEquals("Este préstamo ya fue anulado por Ramón Benítez el 11/10/2026 17:08. "
                + "Actualice la página.", mensaje);
    }

    @Test
    public void testConstruirMensajePrestamoYaAnuladoSinUsuario() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setFechaAnulacionSinDesembolso(LocalDateTime.of(2026, 10, 11, 17, 8));

        String mensaje = servicio.construirMensajePrestamoYaAnulado(prestamo, null);

        assertEquals("Este préstamo ya fue anulado por otro usuario el 11/10/2026 17:08. Actualice la página.",
                mensaje);
    }
}
