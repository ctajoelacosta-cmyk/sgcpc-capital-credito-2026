package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * C19 (decisión de Joel, 07/11/2026): abrir un documento del expediente queda registrado en la
 * auditoría. Acá se prueba el <b>texto</b> que queda guardado — lo único puro de ese camino; el
 * registro contra la base lo probó Joel en pantalla (DOGMA 1).
 *
 * <p>Importa porque es lo que alguien va a leer meses después: el primer intento decía
 * <i>"de persona 3"</i> y Joel lo rechazó — el gerente no sabe quién es "persona 3". La
 * pertenencia se resuelve ahora en {@code describirPertenencia} (nombre y C.I., N.º de operación
 * o código de solicitud) y llega acá ya convertida en palabras.</p>
 */
public class ServicioDocumentosAuditoriaTest {

    private DocumentoExpediente documento(String nombreTipo) {
        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdDocumento(25L);
        documento.setVersionDocumento(1);
        if (nombreTipo != null) {
            TipoDocumento tipo = new TipoDocumento();
            tipo.setNombreTipo(nombreTipo);
            documento.setTipoDocumento(tipo);
        }
        return documento;
    }

    @Test
    public void elDetalleDiceElNombreYLaCedula_noUnId() {
        // El caso exacto que Joel rechazó, ahora con palabras.
        assertEquals("Abrió \"Cédula de identidad\" (versión 1) de"
                        + " López Rojas Mirtha Lucía (C.I. 4.150.164).",
                ServicioDocumentos.armarDetalleVisualizacion(documento("Cédula de identidad"),
                        "López Rojas Mirtha Lucía (C.I. 4.150.164)"));
    }

    @Test
    public void paraUnPrestamoDiceElNumeroDeOperacion() {
        assertEquals("Abrió \"Contrato firmado\" (versión 1) de préstamo CC-2026-00013.",
                ServicioDocumentos.armarDetalleVisualizacion(documento("Contrato firmado"),
                        "préstamo CC-2026-00013"));
    }

    @Test
    public void paraUnaSolicitudDiceSuCodigo() {
        assertEquals("Abrió \"Comprobante de ingreso\" (versión 1) de solicitud SOL-2026-0003.",
                ServicioDocumentos.armarDetalleVisualizacion(documento("Comprobante de ingreso"),
                        "solicitud SOL-2026-0003"));
    }

    @Test
    public void sinTipoNoDejaUnNullEnLaAuditoria() {
        String detalle = ServicioDocumentos.armarDetalleVisualizacion(documento(null), "persona 3");
        assertTrue("no puede quedar un null en el texto: " + detalle, !detalle.contains("null"));
        assertEquals("Abrió \"documento\" (versión 1) de persona 3.", detalle);
    }

    @Test
    public void elDetalleTerminaEnPunto() {
        // Misma regla que el resto de los textos del sistema.
        assertTrue(ServicioDocumentos.armarDetalleVisualizacion(documento("Cédula de identidad"),
                "López Rojas Mirtha Lucía (C.I. 4.150.164)").endsWith("."));
    }
}
