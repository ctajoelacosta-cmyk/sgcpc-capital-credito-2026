package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import py.com.capitalcredito.sgcpc.dto.ItemExpediente;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PersonaLaboral;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * B88 (pedido de Joel, 28/10/2026): junta el estado real del cliente y se lo pasa al dueño de la
 * regla, {@link ServicioClientes#calcularSiguientePaso} -- que es puro y no lee nada.
 *
 * <p>Existe por dos motivos. Uno: la lectura también tiene que tener un solo dueño; antes cada
 * pantalla leía lo que le hacía falta y sacaba su propia conclusión (de ahí que la carga de un
 * documento dijera "registrar el buró" cuando el buró ya estaba registrado). Dos: la lectura no
 * puede vivir en {@code ServicioClientes}, porque {@code ServicioConsultaCrediticia} ya tiene un
 * {@code ServicioClientes} adentro -- agregarle la dependencia inversa haría que construir
 * cualquiera de los dos se llamara a sí mismo sin fin. Acá no hay ciclo: nadie tiene un
 * {@code ServicioPasoGuiado} como campo.</p>
 */
public class ServicioPasoGuiado {

    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final ServicioDocumentos servicioDocumentos = new ServicioDocumentos();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();

    /**
     * El primer paso pendiente del cliente para un llamador que no viene de cargar un documento
     * (alta y edición de la ficha, registro del resultado del buró, y la ficha al dibujar los
     * botones "Siguiente: …").
     */
    public PasoGuiado calcular(Connection conexion, long idPersona) throws SQLException {
        return calcular(conexion, idPersona, ServicioClientes.AccionPrevia.NINGUNA);
    }

    /**
     * El primer paso pendiente del cliente, con el estado leído de la base en esta conexión --
     * siempre después de guardar, para que la respuesta mire los datos ya actualizados.
     *
     * <p>Devuelve un paso sin texto ni destino si la persona no existe; cualquier error de lectura
     * sale como {@link SQLException} y lo maneja el llamador (que nunca debe romper por esto el
     * aviso de éxito de la acción que acaba de hacer el usuario).</p>
     *
     * @param accionPrevia qué acaba de hacer el oficial -- cambia a dónde se lo lleva, ver
     *        {@link ServicioClientes#calcularSiguientePaso}
     */
    public PasoGuiado calcular(Connection conexion, long idPersona, ServicioClientes.AccionPrevia accionPrevia)
            throws SQLException {
        Persona persona = servicioClientes.buscarPorId(conexion, idPersona);
        if (persona == null) {
            return new PasoGuiado(null, PasoGuiado.Destino.NINGUNO, null);
        }
        PersonaLaboral laboral = servicioClientes.obtenerLaboral(conexion, idPersona);
        String situacionLaboral = laboral == null ? null : laboral.getSituacionLaboral();
        List<TipoDocumento> tipos = servicioDocumentos.listarTiposDocumentoActivos(conexion);
        List<DocumentoExpediente> documentos = servicioDocumentos.listarPorPersona(conexion, idPersona);
        // La fila de la autorización solo cuenta como cumplida si además hay un consentimiento
        // registrado (ServicioClientes.construirItemDocumento, hallazgo de Joel en RF03 punto 2).
        ConsentimientoCrediticio ultimoConsentimiento =
                servicioConsultaCrediticia.obtenerUltimoConsentimiento(conexion, idPersona);
        List<ItemExpediente> items = servicioClientes.construirItemsExpediente(situacionLaboral, tipos, documentos,
                persona.getFechaVencimientoCedula(), ultimoConsentimiento != null);
        // "Buró registrado" = consulta con evidencia posterior a la autorización vigente: el mismo
        // criterio que el checklist de "Enviar a aprobación" (RF06), para que el paso guiado y el
        // envío nunca se contradigan.
        ConsultaCrediticia consultaConEvidencia =
                servicioConsultaCrediticia.obtenerMasRecienteConEvidencia(conexion, idPersona);
        ServicioSolicitudes.CompromisoAbierto compromisoAbierto =
                servicioSolicitudes.buscarCompromisoAbierto(conexion, idPersona);
        PasoGuiado paso = servicioClientes.calcularSiguientePaso(items, situacionLaboral,
                servicioConsultaCrediticia.tieneAutorizacionVigente(conexion, idPersona), consultaConEvidencia,
                compromisoAbierto == null ? null : compromisoAbierto.getSolicitud(),
                compromisoAbierto != null && compromisoAbierto.isViaPrestamo(), accionPrevia);
        return paso.conRuta(paso.rutaRelativa(idPersona, persona.getCedulaIdentidad()));
    }
}
