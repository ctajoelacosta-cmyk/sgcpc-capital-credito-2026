package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.ItemChecklistSolicitud;
import py.com.capitalcredito.sgcpc.dto.ItemExpediente;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * Comprobante de domicilio OBLIGATORIO -- decisión de Joel del 21/10/2026, implementada el
 * 28/10/2026 (ver {@code docs/funcionamiento/RF03_expediente.md} §7 y la fila de la decisión en
 * {@code docs/recorrido/FASE2_PLAN.md}). Cierra además C14.
 *
 * <p>Qué verifica, que es lo que puede romperse en silencio:</p>
 * <ol>
 *   <li>Que {@code CODIGOS_EXPEDIENTE_OBLIGATORIOS} -- la ÚNICA lista -- tenga los 4 y que el
 *       domicilio esté adentro. Si alguien la vuelve a dejar en 3, acá se cae.</li>
 *   <li>Que {@code construirItemsExpediente} marque {@code isObligatorio()} en las 4 filas: de
 *       ahí salen el contador "N de 4", la numeración y el badge "Falta" de la pestaña.</li>
 *   <li>Que el checklist previo al envío tenga <b>9</b> ítems (antes 8) y que el del domicilio
 *       refleje de verdad si está cargado -- contra la base, con una persona que lo tiene y otra
 *       que no, y además quitándoselo a la primera dentro de la transacción.</li>
 * </ol>
 *
 * <h3>Seguridad (DOGMA 1 y 2 de {@code docs/REGLAS_DESARROLLO.md})</h3>
 * <ul>
 *   <li>Aborta si la base no es {@code sgcpc_db_pruebas}, leído de la conexión abierta y no de un
 *       archivo que alguien pudo mover -- misma guarda que {@link PruebaRN01AprobarFormalizar}.</li>
 *   <li>Una sola conexión, {@code autoCommit=false} y {@code rollback()} en un {@code finally}:
 *       revierte siempre, también si una verificación falla.</li>
 *   <li>Solo hace {@code UPDATE} sobre una fila que ya existe (el estado de un documento ya
 *       cargado) -- ningún {@code INSERT} ni {@code DELETE}, así que no toca los 13 triggers
 *       {@code BEFORE DELETE} de {@code REGLAS_DESARROLLO.md} §14 ni consume numeración.</li>
 *   <li>Al final abre una <b>conexión nueva</b> y verifica contra la base ya comiteada que el
 *       documento volvió a quedar como estaba.</li>
 * </ul>
 *
 * <h3>Qué NO cubre, dicho de frente</h3>
 * <p>El bloqueo real de "Enviar a aprobación" vive en
 * {@code ServicioSolicitudes.agregarFaltantesExpediente}, que es <b>privado</b> y lo llama
 * {@code enviarAAprobacion}, que abre <b>su propia conexión</b> -- un escenario armado en una
 * transacción sin commit le es invisible, y además hoy no hay ninguna solicitud en estado
 * BORRADOR en la base. Esta prueba cubre entonces la lista de obligatorios, los ítems de la
 * pestaña y el checklist que el oficial y el gerente ven en pantalla, que es el mismo criterio;
 * <b>el bloqueo de punta a punta lo prueba Joel en pantalla</b> con un cliente nuevo sin
 * comprobante de domicilio (DOGMA 1: "verificado por código" no es "probado").</p>
 *
 * <p>No corre con {@code mvn test} (el nombre no matchea {@code *Test}/{@code Test*} de Surefire,
 * igual que el resto de las pruebas con base de este proyecto). Se ejecuta a propósito con
 * {@code mvn -Dtest=PruebaDomicilioObligatorio test}.</p>
 */
public class PruebaDomicilioObligatorio {

    private static final String BASE_PERMITIDA = "sgcpc_db_pruebas";
    private static final String CODIGO_DOMICILIO = "COMPROBANTE_DOMICILIO";
    /** La etiqueta del ítem nuevo del checklist, escrita de nuevo acá a propósito: si esta prueba
     * la leyera del mismo código que la produce, no podría detectar que cambió. */
    private static final String ETIQUETA_DOMICILIO = "Comprobante de domicilio cargado";

    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();

    // =====================================================================
    // 1) La única lista
    // =====================================================================

    @Test
    public void laListaDeObligatoriosTieneLosCuatroDocumentosDelExpediente() {
        assertTrue("El comprobante de domicilio tiene que estar en la lista de obligatorios "
                + "(decisión de Joel del 21/10/2026).",
                ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS.contains(CODIGO_DOMICILIO));
        assertEquals("Los obligatorios del expediente son 4: cédula, comprobante de ingreso, "
                + "comprobante de domicilio y autorización de buró.",
                4, ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS.size());
        // Los 4 del expediente son exactamente los que se muestran, sin ninguno de más.
        assertEquals(4, ServicioClientes.CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN.length);
        for (String codigo : ServicioClientes.CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN) {
            assertTrue("Quedó un documento del expediente que no es obligatorio: " + codigo,
                    ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS.contains(codigo));
        }
    }

    // =====================================================================
    // 2) Los ítems de la pestaña (de acá salen "N de 4", la numeración y el badge)
    // =====================================================================

    @Test
    public void losCuatroItemsDelExpedienteSalenMarcadosComoObligatorios() {
        List<ItemExpediente> items = servicioClientes.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), null, false);

        assertEquals(4, items.size());
        // B90 (decisión de Joel, 29/10/2026): la autorización pasó a ser la fila 1, así que el
        // comprobante de domicilio quedó último. El orden en sí se prueba en ServicioClientesTest.
        assertEquals(CODIGO_DOMICILIO, items.get(3).getTipo().getCodigoTipo());
        for (ItemExpediente item : items) {
            assertTrue("El contador \"N de 4\", la numeración y el badge \"Falta\" de la pestaña "
                    + "salen de isObligatorio(): " + item.getTipo().getCodigoTipo(),
                    item.isObligatorio());
            assertFalse("Sin ningún documento cargado, ninguna fila puede estar cumplida.",
                    item.isCumplido());
        }
    }

    // =====================================================================
    // 3) El checklist previo al envío, contra la base y con rollback
    // =====================================================================

    @Test
    public void elChecklistTieneNueveItemsYElDomicilioReflejaSiEstaCargado() throws Exception {
        long idPersonaConDomicilio;
        long idDocumentoDomicilio;
        String estadoAntes;

        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            idPersonaConDomicilio = unId(conexion, "SELECT d.id_persona FROM documentos_expediente d"
                    + " JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento"
                    + " WHERE t.codigo_tipo = '" + CODIGO_DOMICILIO + "'"
                    + " AND d.estado_documento = 'VIGENTE' ORDER BY d.id_persona LIMIT 1");
            idDocumentoDomicilio = unId(conexion, "SELECT d.id_documento FROM documentos_expediente d"
                    + " JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento"
                    + " WHERE t.codigo_tipo = '" + CODIGO_DOMICILIO + "'"
                    + " AND d.estado_documento = 'VIGENTE' AND d.id_persona = " + idPersonaConDomicilio
                    + " ORDER BY d.id_documento LIMIT 1");
            estadoAntes = unTexto(conexion, "SELECT estado_documento FROM documentos_expediente"
                    + " WHERE id_documento = " + idDocumentoDomicilio);

            try {
                Persona conDomicilio = servicioClientes.buscarPorId(conexion, idPersonaConDomicilio);

                // (a) Con el comprobante cargado: 9 ítems y el del domicilio en ✓.
                List<ItemChecklistSolicitud> antes =
                        servicioSolicitudes.construirChecklistPrevioEnvio(conexion, conDomicilio);
                assertEquals("El checklist previo al envío pasó de 8 a 9 ítems (decisión del "
                        + "21/10/2026).", 9, antes.size());
                assertTrue("Con el comprobante de domicilio cargado, su ítem tiene que estar "
                        + "cumplido.", itemCumplido(antes, ETIQUETA_DOMICILIO));

                // (b) Se le quita el comprobante (mismo camino que usa el sistema al reemplazar
                // un documento: cambia el estado, nunca borra la fila).
                int filas = actualizarEstadoDocumento(conexion, idDocumentoDomicilio, "REEMPLAZADO");
                assertEquals("Se esperaba tocar exactamente una fila.", 1, filas);

                List<ItemChecklistSolicitud> despues =
                        servicioSolicitudes.construirChecklistPrevioEnvio(conexion, conDomicilio);
                assertEquals(9, despues.size());
                assertFalse("Sin comprobante de domicilio vigente, su ítem NO puede estar cumplido: "
                        + "es el que ahora bloquea el envío a aprobación.",
                        itemCumplido(despues, ETIQUETA_DOMICILIO));

                System.out.println("Persona con domicilio: id " + idPersonaConDomicilio
                        + " -- ítem del checklist ✓ antes, ✗ después de quitárselo.");
            } finally {
                conexion.rollback();
            }
        }

        // Verificación por fuera de la transacción: el documento volvió a quedar como estaba.
        try (Connection verificacion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(verificacion);
            String estadoDespues = unTexto(verificacion, "SELECT estado_documento FROM "
                    + "documentos_expediente WHERE id_documento = " + idDocumentoDomicilio);
            System.out.println("Base DESPUÉS del rollback: documento " + idDocumentoDomicilio
                    + " en estado " + estadoDespues + " (antes: " + estadoAntes + ")");
            assertEquals("El rollback no devolvió el estado del documento como estaba.",
                    estadoAntes, estadoDespues);
        }
    }

    @Test
    public void unaPersonaSinComprobanteDeDomicilioLoTieneComoFaltanteEnElChecklist() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);
            try {
                long idPersonaSinDomicilio = unId(conexion, "SELECT p.id_persona FROM personas p"
                        + " WHERE NOT EXISTS (SELECT 1 FROM documentos_expediente d"
                        + " JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento"
                        + " WHERE d.id_persona = p.id_persona AND t.codigo_tipo = '" + CODIGO_DOMICILIO + "'"
                        + " AND d.estado_documento = 'VIGENTE') ORDER BY p.id_persona LIMIT 1");

                Persona sinDomicilio = servicioClientes.buscarPorId(conexion, idPersonaSinDomicilio);
                List<ItemChecklistSolicitud> checklist =
                        servicioSolicitudes.construirChecklistPrevioEnvio(conexion, sinDomicilio);

                assertEquals(9, checklist.size());
                assertFalse("La persona " + idPersonaSinDomicilio + " no tiene comprobante de "
                        + "domicilio vigente: su ítem no puede estar cumplido.",
                        itemCumplido(checklist, ETIQUETA_DOMICILIO));
                System.out.println("Persona sin domicilio: id " + idPersonaSinDomicilio
                        + " -- ítem del checklist ✗, como corresponde.");
            } finally {
                conexion.rollback();
            }
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /** Guarda dura: la URL sale de la conexión abierta, no de un archivo que alguien pudo mover. */
    private void abortarSiNoEsLaBaseDePruebas(Connection conexion) throws Exception {
        String url = conexion.getMetaData().getURL();
        String catalogo = conexion.getCatalog();
        if (url == null || !url.endsWith("/" + BASE_PERMITIDA) || !BASE_PERMITIDA.equals(catalogo)) {
            fail("ABORTADO: esta prueba solo corre contra " + BASE_PERMITIDA
                    + ". La conexión activa apunta a \"" + url + "\" (catálogo \"" + catalogo
                    + "\"). Revisar bd.properties.");
        }
    }

    private boolean itemCumplido(List<ItemChecklistSolicitud> items, String etiqueta) {
        for (ItemChecklistSolicitud item : items) {
            if (etiqueta.equals(item.getEtiqueta())) {
                return item.isCumple();
            }
        }
        fail("El checklist no tiene ningún ítem con la etiqueta \"" + etiqueta + "\".");
        return false;
    }

    private int actualizarEstadoDocumento(Connection conexion, long idDocumento, String estado) throws Exception {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE documentos_expediente SET estado_documento = ? WHERE id_documento = ?")) {
            sentencia.setString(1, estado);
            sentencia.setLong(2, idDocumento);
            return sentencia.executeUpdate();
        }
    }

    private long unId(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            if (!resultado.next()) {
                fail("No hay ninguna fila para la condición de la prueba: " + sql);
            }
            return resultado.getLong(1);
        }
    }

    private String unTexto(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    private List<TipoDocumento> tiposExpedientePersonaDePrueba() {
        return Arrays.asList(
                tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"),
                tipoDocumentoDePrueba(2, "COMPROBANTE_INGRESO"),
                tipoDocumentoDePrueba(3, CODIGO_DOMICILIO),
                tipoDocumentoDePrueba(4, "AUTORIZACION_CONSULTA_BURO"),
                tipoDocumentoDePrueba(5, "RESULTADO_CONSULTA_BURO"));
    }

    private TipoDocumento tipoDocumentoDePrueba(long id, String codigo) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setIdTipoDocumento(id);
        tipo.setCodigoTipo(codigo);
        tipo.setNombreTipo(codigo);
        tipo.setContexto("PERSONA");
        return tipo;
    }
}
