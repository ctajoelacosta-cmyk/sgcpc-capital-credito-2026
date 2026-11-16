package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio;

/** D-38: vigencia del consentimiento de consulta al buró. */
public class ServicioConsultaCrediticiaTest {

    private final ServicioConsultaCrediticia servicio = new ServicioConsultaCrediticia();

    private ConsentimientoCrediticio consentimiento(LocalDate fecha, EstadoDocumento estadoEvidencia) {
        ConsentimientoCrediticio consentimiento = new ConsentimientoCrediticio();
        consentimiento.setFechaConsentimiento(fecha.atStartOfDay());
        consentimiento.setEstadoDocumentoEvidencia(estadoEvidencia);
        return consentimiento;
    }

    @Test
    public void testCalcularFechaVencimiento() {
        assertEquals(LocalDate.parse("2026-01-31"),
                servicio.calcularFechaVencimiento(LocalDate.parse("2026-01-01"), 30));
    }

    @Test
    public void testSinConsentimientoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarConsultaPermitida(null, 30, LocalDate.parse("2026-01-15")));
    }

    @Test
    public void testConsentimientoVencidoSeRechazaConMensajeExacto() {
        ConsentimientoCrediticio vencido = consentimiento(LocalDate.parse("2026-01-01"), EstadoDocumento.VIGENTE);
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarConsultaPermitida(vencido, 30, LocalDate.parse("2026-03-01")));
        assertTrue(error.getMessage().contains("venció el 31/01/2026"));
        assertTrue(error.getMessage().contains("Cargue una autorización nueva firmada por el cliente."));
    }

    @Test
    public void testDocumentoDeEvidenciaNoVigenteSeRechaza() {
        ConsentimientoCrediticio conEvidenciaReemplazada = consentimiento(LocalDate.now(), EstadoDocumento.REEMPLAZADO);
        assertThrows(NegocioException.class,
                () -> servicio.validarConsultaPermitida(conEvidenciaReemplazada, 30, LocalDate.now()));
    }

    @Test
    public void testConsentimientoVigenteNoLanza() {
        ConsentimientoCrediticio vigente = consentimiento(LocalDate.parse("2026-01-01"), EstadoDocumento.VIGENTE);
        servicio.validarConsultaPermitida(vigente, 30, LocalDate.parse("2026-01-20"));
    }

    /** D-57: "¿Tiene mora?" es obligatorio -- E4 lo usa para evaluar la solicitud. */
    @Test
    public void testIndicadorMoraNuloSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarIndicadorMora(null));
    }

    @Test
    public void testIndicadorMoraSiONoNoLanza() {
        servicio.validarIndicadorMora(Boolean.TRUE);
        servicio.validarIndicadorMora(Boolean.FALSE);
    }

    /** D-58 (ajuste D del plan de E3): el resultado de la consulta exige la evidencia adjunta. */
    @Test
    public void testDocumentoResultadoNuloSeRechazaConMensajeExacto() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarDocumentoResultado(null));
        assertEquals("Primero cargue en Expediente / Documentos la respuesta de Informconf "
                + "(tipo Resultado de consulta al buró).", error.getMessage());
    }

    @Test
    public void testDocumentoResultadoPresenteNoLanza() {
        servicio.validarDocumentoResultado(1L);
    }
}
