package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

public class ServicioDocumentosTest {

    private final ServicioDocumentos servicio = new ServicioDocumentos();

    private static final byte[] CONTENIDO_PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] CONTENIDO_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] CONTENIDO_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n'};
    private static final byte[] CONTENIDO_IRRECONOCIBLE = {'h', 'o', 'l', 'a'};

    // --- detectarExtensionPorContenido (ajuste C) ---

    @Test
    public void testDetectaPdfPorFirma() {
        assertEquals("pdf", servicio.detectarExtensionPorContenido(CONTENIDO_PDF));
    }

    @Test
    public void testDetectaJpgPorFirma() {
        assertEquals("jpg", servicio.detectarExtensionPorContenido(CONTENIDO_JPG));
    }

    @Test
    public void testDetectaPngPorFirma() {
        assertEquals("png", servicio.detectarExtensionPorContenido(CONTENIDO_PNG));
    }

    @Test
    public void testBytesIrreconociblesSeRechazan() {
        assertThrows(NegocioException.class, () -> servicio.detectarExtensionPorContenido(CONTENIDO_IRRECONOCIBLE));
    }

    // --- validarArchivo ---

    @Test
    public void testExtensionNoPermitidaSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarArchivo("documento.txt", CONTENIDO_PDF.length, CONTENIDO_PDF, 5L * 1024 * 1024));
    }

    @Test
    public void testTamanoExcedidoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarArchivo("cedula.pdf", 10L * 1024 * 1024, CONTENIDO_PDF, 5L * 1024 * 1024));
    }

    @Test
    public void testExtensionDeclaradaQueNoCoincideConElContenidoSeRechaza() {
        // Un archivo JPG renombrado a .pdf: la extension dice "pdf" pero la firma real es JPG.
        assertThrows(NegocioException.class,
                () -> servicio.validarArchivo("cedula.pdf", CONTENIDO_JPG.length, CONTENIDO_JPG, 5L * 1024 * 1024));
    }

    @Test
    public void testArchivoValidoNoLanza() {
        servicio.validarArchivo("cedula.pdf", CONTENIDO_PDF.length, CONTENIDO_PDF, 5L * 1024 * 1024);
    }

    // ======================================================================
    // B42 (decision de Joel, 01/11/2026): el mismo archivo cargado dos veces.
    // El bloqueo del caso "mismo tipo" necesita base (es una comparacion contra el vigente
    // guardado); lo que se prueba aca es el TEXTO del aviso del otro caso, que es puro.
    // ======================================================================

    @Test
    public void testElAvisoNombraElTipoQueYaTieneEseMismoArchivo() {
        assertEquals("Este archivo ya está cargado como «Contrato firmado». Si es el mismo papel,"
                + " está bien; si se equivocó de archivo, vuelva a cargarlo.",
                ServicioDocumentos.textoDelAvisoDeContenidoRepetido(
                        java.util.Arrays.asList("Contrato firmado")));
    }

    @Test
    public void testElAvisoSePuedeLeerEnteroEnElCartel() {
        // El cartel usa como TÍTULO cualquier mensaje de una sola oración y a los títulos les
        // quita el punto final (pie.jsp, dividirEnTituloYDetalle) -- por eso los mensajes que el
        // usuario ve van en dos oraciones. Lo mismo se verifica para el rechazo en
        // TextosDePantallaTest.
        String aviso = ServicioDocumentos.textoDelAvisoDeContenidoRepetido(
                Arrays.asList("Contrato firmado"));

        assertTrue(aviso.endsWith("."));
        assertTrue("una sola oración: el cartel lo mostraría sin el punto",
                aviso.indexOf(". ") > 0 && aviso.indexOf(". ") < aviso.length() - 2);
    }

    @Test
    public void testConVariosTiposLosEnumeraConY() {
        assertEquals("Este archivo ya está cargado como «Contrato firmado», «Pagaré firmado» y"
                + " «Plan de pagos firmado». Si es el mismo papel, está bien; si se equivocó de"
                + " archivo, vuelva a cargarlo.",
                ServicioDocumentos.textoDelAvisoDeContenidoRepetido(java.util.Arrays.asList(
                        "Contrato firmado", "Pagaré firmado", "Plan de pagos firmado")));
    }

    @Test
    public void testSinOtroTipoConEseContenidoNoHayAviso() {
        assertNull(ServicioDocumentos.textoDelAvisoDeContenidoRepetido(
                java.util.Collections.<String>emptyList()));
    }

    @Test
    public void testReemplazarPorElMISMOArchivoSeBloquea() {
        // El caso que falló en la prueba del 01/11 21:39-21:40: "Reemplazar" con el mismo
        // ejemplo.pdf creaba la versión 2 y la 3, las tres con el mismo hash. Al reemplazar, el
        // documento anterior del MISMO tipo sigue VIGENTE cuando se hace el chequeo, así que la
        // consulta por hash lo encuentra y la carga se rechaza.
        List<DocumentoExpediente> conMismoContenido = Arrays.asList(
                documento(4L, "Comprobante de domicilio"), documento(2L, "Cédula de identidad"));

        assertEquals("Comprobante de domicilio",
                ServicioDocumentos.nombreDelTipoRepetido(conMismoContenido, 4L));
    }

    @Test
    public void testElMismoContenidoEnOtroTipoNoSeBloquea() {
        // Una sola hoja escaneada puede servir para dos tipos: se guarda y solo se avisa.
        List<DocumentoExpediente> conMismoContenido = Arrays.asList(
                documento(2L, "Cédula de identidad"), documento(3L, "Comprobante de ingreso"));

        assertNull(ServicioDocumentos.nombreDelTipoRepetido(conMismoContenido, 4L));
        assertEquals(Arrays.asList("Cédula de identidad", "Comprobante de ingreso"),
                ServicioDocumentos.otrosTiposConElMismoContenido(conMismoContenido, 4L));
    }

    @Test
    public void testElTipoQueSeAcabaDeCargarNoSeNombraEnElAviso() {
        List<DocumentoExpediente> conMismoContenido = Arrays.asList(
                documento(4L, "Comprobante de domicilio"), documento(2L, "Cédula de identidad"));

        assertEquals(Arrays.asList("Cédula de identidad"),
                ServicioDocumentos.otrosTiposConElMismoContenido(conMismoContenido, 4L));
    }

    private static DocumentoExpediente documento(long idTipo, String nombreTipo) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setIdTipoDocumento(idTipo);
        tipo.setNombreTipo(nombreTipo);
        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdTipoDocumento(idTipo);
        documento.setTipoDocumento(tipo);
        return documento;
    }

    @Test
    public void testElHashEsElQueDecideSiElContenidoEsElMismo() {
        // La comparacion de los dos casos se hace por SHA-256 del contenido, no por nombre de
        // archivo: el mismo papel renombrado sigue siendo el mismo papel.
        byte[] contenido = "una hoja escaneada".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(ServicioDocumentos.calcularHashSha256(contenido),
                ServicioDocumentos.calcularHashSha256(
                        "una hoja escaneada".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNotEquals(ServicioDocumentos.calcularHashSha256(contenido),
                ServicioDocumentos.calcularHashSha256(
                        "otra hoja".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
