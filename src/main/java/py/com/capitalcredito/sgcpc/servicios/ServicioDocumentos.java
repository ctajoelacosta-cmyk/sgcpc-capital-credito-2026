package py.com.capitalcredito.sgcpc.servicios;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.ArchivoBinarioDAO;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaDAO;
import py.com.capitalcredito.sgcpc.dao.DocumentoExpedienteDAO;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * P03, parte expediente/documentos (Doc. 10 §10.19: ControladorDocumentosServlet
 * / ServicioDocumentos). Ajuste C del plan de E3: valida el tipo real del
 * archivo por sus primeros bytes (no solo por extensión/mimeType), y limita
 * el tamaño con el parámetro archivos.tamano_maximo_mb. Nunca guarda nada en
 * disco: el binario vive solo en archivos_binarios (REGLAS_DESARROLLO.md §4).
 */
public class ServicioDocumentos {

    private static final byte[] FIRMA_PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] FIRMA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final ArchivoBinarioDAO archivoBinarioDAO = new ArchivoBinarioDAO();
    private final DocumentoExpedienteDAO documentoExpedienteDAO = new DocumentoExpedienteDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    // C19 (ajuste de Joel, 07/11/2026): para que la auditoría diga de QUIÉN es el documento con
    // palabras y no con un id. Se usan los DAO y no los servicios a propósito: un campo Servicio*
    // dentro de otro servicio puede cerrar un ciclo de construcción (la trampa de B88), y acá solo
    // hacen falta tres lecturas por id.
    private final PersonaDAO personaDAO = new PersonaDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();

    /** RF03 (pedido de Joel), punto 2: hallazgo de un PDF falso cargado como autorización -- estos
     * dos tipos (contexto PERSONA) solo se cargan por su propia ruta dedicada (cargar-autorizacion-
     * firmada / cargar-informe-buro, ver ServicioConsultaCrediticia), nunca por la genérica
     * "/documentos/cargar": esas dos cargas tienen que ir siempre junto con un registro real
     * (consentimiento / consulta), no solo el archivo. */
    private static final java.util.Set<String> TIPOS_PERSONA_CON_RUTA_PROPIA = new java.util.HashSet<>(
            java.util.Arrays.asList("AUTORIZACION_CONSULTA_BURO", "RESULTADO_CONSULTA_BURO"));

    /**
     * App móvil (FASE 1, 12/11/2026): ¿este tipo se carga SOLO por su ruta dedicada? La pregunta
     * existe para que la aplicación móvil pueda mostrar esas dos filas del expediente como de
     * solo lectura, con un "Se carga en el sistema web", en vez de ofrecer un botón que esta misma
     * clase va a rechazar (decisión de Joel, opción a).
     *
     * <p>Es un <b>lector</b> de {@link #TIPOS_PERSONA_CON_RUTA_PROPIA}, no una regla nueva: la
     * alternativa era repetir los dos códigos en el paquete {@code api}, y una lista repetida es
     * una lista que mañana queda distinta. Pura.</p>
     */
    public boolean seCargaPorRutaPropia(String codigoTipo) {
        return TIPOS_PERSONA_CON_RUTA_PROPIA.contains(codigoTipo);
    }

    /** Ajuste C: primeros bytes del archivo, no la extensión que puso el usuario ni el mimeType del navegador. Pura. */
    public String detectarExtensionPorContenido(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_PDF)) {
            return "pdf";
        }
        if (empiezaCon(contenido, FIRMA_JPG)) {
            return "jpg";
        }
        if (empiezaCon(contenido, FIRMA_PNG)) {
            return "png";
        }
        // RF03 (pedido de Joel): título corto + ayuda -- pie.jsp separa "Frase corta. Detalle."
        // en título/cuerpo del modal; antes esta era una sola oración y salía entera como título.
        throw new NegocioException("No se pudo cargar el archivo. El archivo no es un PDF, JPG o PNG real. "
                + "Vuelva a escanear el documento y guárdelo como PDF.");
    }

    private boolean empiezaCon(byte[] contenido, byte[] firma) {
        if (contenido == null || contenido.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** Extensión declarada por el nombre del archivo, normalizada (jpeg se trata como jpg). Pura. */
    private String extensionDeclarada(String nombreArchivo) {
        int punto = nombreArchivo == null ? -1 : nombreArchivo.lastIndexOf('.');
        if (punto < 0 || punto == nombreArchivo.length() - 1) {
            throw new NegocioException("El archivo debe tener una extensión (.pdf, .jpg o .png).");
        }
        String extension = nombreArchivo.substring(punto + 1).toLowerCase();
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    /**
     * Ajuste C: valida extensión declarada, firma real de los primeros bytes
     * (deben coincidir) y tamaño contra archivos.tamano_maximo_mb. Pura
     * (recibe el máximo ya resuelto por el orquestador).
     */
    public void validarArchivo(String nombreArchivo, long tamanoBytes, byte[] contenido, long tamanoMaximoBytes) {
        String declarada = extensionDeclarada(nombreArchivo);
        if (!declarada.equals("pdf") && !declarada.equals("jpg") && !declarada.equals("png")) {
            throw new NegocioException("Solo se aceptan archivos PDF, JPG o PNG. Si tiene un documento Word, "
                    + "guárdelo como PDF antes de cargarlo.");
        }
        if (tamanoBytes > tamanoMaximoBytes) {
            // RF03 (pedido de Joel): título corto + ayuda -- el número sigue saliendo del
            // parámetro (archivos.tamano_maximo_mb), nunca fijo en el código (REGLAS_DESARROLLO.md §5).
            throw new NegocioException("El archivo supera " + (tamanoMaximoBytes / (1024 * 1024)) + " MB. "
                    + "Escanéelo con menor resolución o en blanco y negro para que pese menos.");
        }
        String real = detectarExtensionPorContenido(contenido);
        if (!real.equals(declarada)) {
            // RF03 (pedido de Joel): título corto + ayuda, mismo criterio que los dos de arriba.
            throw new NegocioException("El archivo no coincide con su tipo. Parece un archivo con la extensión "
                    + "cambiada. Guárdelo de nuevo como PDF, JPG o PNG y vuelva a cargarlo.");
        }
    }

    /**
     * Carga un documento del expediente de una persona. Si ya había un
     * documento VIGENTE del mismo tipo para esa persona, ese queda
     * REEMPLAZADO y este se guarda como la versión siguiente (versionado,
     * nunca se borra el anterior — REGLAS_DESARROLLO.md §5).
     */
    public long cargarDocumento(long idPersona, long idTipoDocumento, String nombreArchivo, String mimeType,
            byte[] contenido, long idUsuarioQueCarga) throws SQLException {
        return cargarDocumento(idPersona, idTipoDocumento, nombreArchivo, mimeType, contenido, idUsuarioQueCarga,
                false);
    }

    /**
     * RF03 (pedido de Joel), punto 2: variante con el tipo reservado permitido, paquete-visible --
     * solo la usan ServicioConsultaCrediticia.cargarAutorizacionFirmada/cargarInformeBuro, que
     * llegan acá con permitirTipoReservado=true después de haber llegado por su propia ruta
     * dedicada. La ruta genérica (ControladorDocumentosServlet, "/documentos/cargar") siempre usa
     * el método público de 6 parámetros, que llega acá con false.
     */
    long cargarDocumento(long idPersona, long idTipoDocumento, String nombreArchivo, String mimeType,
            byte[] contenido, long idUsuarioQueCarga, boolean permitirTipoReservado) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                if (!permitirTipoReservado) {
                    TipoDocumento tipoSolicitado = tipoDocumentoDAO.buscarPorId(conexion, idTipoDocumento);
                    if (tipoSolicitado != null && TIPOS_PERSONA_CON_RUTA_PROPIA.contains(tipoSolicitado.getCodigoTipo())) {
                        throw new NegocioException(tipoSolicitado.getNombreTipo()
                                + " se carga desde su propio paso, no desde acá.");
                    }
                }
                long tamanoMaximoBytes = servicioParametros.obtenerValorEntero(conexion, "archivos.tamano_maximo_mb")
                        * 1024L * 1024L;
                validarArchivo(nombreArchivo, contenido.length, contenido, tamanoMaximoBytes);

                DocumentoExpediente vigenteAnterior = documentoExpedienteDAO
                        .obtenerVigentePorPersonaYTipo(conexion, idPersona, idTipoDocumento);
                rechazarSiYaEstaCargadoIgual(conexion, idPersona, null, idTipoDocumento, contenido);

                ArchivoBinario archivo = new ArchivoBinario();
                archivo.setNombreArchivo(nombreArchivo);
                archivo.setMimeType(mimeType);
                archivo.setExtension(extensionDeclarada(nombreArchivo));
                archivo.setTamanoBytes(contenido.length);
                archivo.setHashSha256(calcularHashSha256(contenido));
                archivo.setOrigenArchivo(OrigenArchivo.CARGADO);
                archivo.setCargadoPor(idUsuarioQueCarga);
                long idArchivo = archivoBinarioDAO.insertar(conexion, archivo, contenido);

                DocumentoExpediente documento = new DocumentoExpediente();
                documento.setIdArchivo(idArchivo);
                documento.setIdTipoDocumento(idTipoDocumento);
                documento.setIdPersona(idPersona);
                documento.setEstadoDocumento(EstadoDocumento.VIGENTE);
                documento.setFechaDocumento(Reloj.hoy());
                String operacion;
                if (vigenteAnterior != null) {
                    documento.setVersionDocumento(vigenteAnterior.getVersionDocumento() + 1);
                    documento.setIdDocumentoReemplazado(vigenteAnterior.getIdDocumento());
                    operacion = "NUEVA_VERSION_DOCUMENTO";
                } else {
                    documento.setVersionDocumento(1);
                    operacion = "CARGAR_DOCUMENTO";
                }
                long idDocumento = documentoExpedienteDAO.insertar(conexion, documento);
                if (vigenteAnterior != null) {
                    documentoExpedienteDAO.marcarReemplazado(conexion, vigenteAnterior.getIdDocumento());
                }

                servicioAuditoria.registrar(conexion, idUsuarioQueCarga, operacion, "DOCUMENTO_EXPEDIENTE",
                        idDocumento, "Cargó \"" + nombreArchivo + "\" (versión " + documento.getVersionDocumento()
                                + ").");
                conexion.commit();
                return idDocumento;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    public List<DocumentoExpediente> listarPorPersona(Connection conexion, long idPersona) throws SQLException {
        return documentoExpedienteDAO.listarPorPersona(conexion, idPersona);
    }

    /**
     * E5 (P06): igual que cargarDocumento, pero para el checklist de un préstamo (contrato/pagaré
     * firmados) en vez del expediente de una persona -- mismo versionado (la anterior VIGENTE del
     * mismo tipo para ese préstamo queda REEMPLAZADA), misma validación de archivo.
     */
    public long cargarDocumentoPrestamo(long idPrestamo, long idTipoDocumento, String nombreArchivo, String mimeType,
            byte[] contenido, long idUsuarioQueCarga) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                long tamanoMaximoBytes = servicioParametros.obtenerValorEntero(conexion, "archivos.tamano_maximo_mb")
                        * 1024L * 1024L;
                validarArchivo(nombreArchivo, contenido.length, contenido, tamanoMaximoBytes);

                DocumentoExpediente vigenteAnterior = documentoExpedienteDAO
                        .obtenerVigentePorPrestamoYTipo(conexion, idPrestamo, idTipoDocumento);
                rechazarSiYaEstaCargadoIgual(conexion, null, idPrestamo, idTipoDocumento, contenido);

                ArchivoBinario archivo = new ArchivoBinario();
                archivo.setNombreArchivo(nombreArchivo);
                archivo.setMimeType(mimeType);
                archivo.setExtension(extensionDeclarada(nombreArchivo));
                archivo.setTamanoBytes(contenido.length);
                archivo.setHashSha256(calcularHashSha256(contenido));
                archivo.setOrigenArchivo(OrigenArchivo.CARGADO);
                archivo.setCargadoPor(idUsuarioQueCarga);
                long idArchivo = archivoBinarioDAO.insertar(conexion, archivo, contenido);

                DocumentoExpediente documento = new DocumentoExpediente();
                documento.setIdArchivo(idArchivo);
                documento.setIdTipoDocumento(idTipoDocumento);
                documento.setIdPrestamo(idPrestamo);
                documento.setEstadoDocumento(EstadoDocumento.VIGENTE);
                documento.setFechaDocumento(Reloj.hoy());
                String operacion;
                if (vigenteAnterior != null) {
                    documento.setVersionDocumento(vigenteAnterior.getVersionDocumento() + 1);
                    documento.setIdDocumentoReemplazado(vigenteAnterior.getIdDocumento());
                    operacion = "NUEVA_VERSION_DOCUMENTO_FORMALIZACION";
                } else {
                    documento.setVersionDocumento(1);
                    operacion = "CARGAR_DOCUMENTO_FORMALIZACION";
                }
                long idDocumento = documentoExpedienteDAO.insertar(conexion, documento);
                if (vigenteAnterior != null) {
                    documentoExpedienteDAO.marcarReemplazado(conexion, vigenteAnterior.getIdDocumento());
                }

                servicioAuditoria.registrar(conexion, idUsuarioQueCarga, operacion, "DOCUMENTO_EXPEDIENTE",
                        idDocumento, "Cargó \"" + nombreArchivo + "\" (versión " + documento.getVersionDocumento()
                                + ") para el préstamo id " + idPrestamo + ".");
                conexion.commit();
                return idDocumento;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** E5: catálogo de tipos de documento del checklist de formalización (contexto PRESTAMO). */
    public List<TipoDocumento> listarTiposDocumentoPrestamo(Connection conexion) throws SQLException {
        return tipoDocumentoDAO.listarActivosPorContexto(conexion, "PRESTAMO");
    }

    /** Catálogo para el desplegable de "tipo de documento" al cargar (Doc. 08 Tabla 06, semilla D-37). */
    public List<TipoDocumento> listarTiposDocumentoActivos(Connection conexion) throws SQLException {
        return tipoDocumentoDAO.listarActivosPorContexto(conexion, "PERSONA");
    }

    public DocumentoExpediente obtenerPorId(Connection conexion, long idDocumento) throws SQLException {
        return documentoExpedienteDAO.obtenerPorId(conexion, idDocumento);
    }

    /** Contenido binario para descargar; se pide aparte de los metadatos (no se carga en los listados). */
    public byte[] obtenerContenido(Connection conexion, long idArchivo) throws SQLException {
        return archivoBinarioDAO.obtenerContenido(conexion, idArchivo);
    }

    /** Público desde D-70: ServicioFormalizacion lo reutiliza para los PDF que genera (no se duplica). */
    // ======================================================================
    // B42 (01/11/2026, decisión de Joel): el mismo archivo cargado dos veces.
    //
    // Dos casos distintos, UNA sola consulta (DocumentoExpedienteDAO.listarVigentesConMismoHash,
    // acotada a esta persona o a este préstamo -- nunca global: un hash igual en el expediente de
    // otro cliente no es asunto de esta carga, y nombrarlo filtraría datos de un tercero):
    //
    //   - mismo tipo + mismo contenido -> NO se crea una versión nueva. Una "versión 2" byte a
    //     byte idéntica a la 1 no aporta nada y ensucia el historial de versiones.
    //   - otro tipo + mismo contenido -> se guarda y se avisa. Es legítimo: una sola hoja
    //     escaneada puede servir para dos tipos (cédula y comprobante de domicilio en la misma
    //     imagen), así que bloquearlo rompería el circuito.
    // ======================================================================

    /**
     * El primer caso: idéntico al que ya está cargado para ESE tipo. Rechaza antes de insertar
     * nada, así no queda ni el archivo ni una versión nueva.
     *
     * <p>Cubre los TRES caminos de carga (expediente nuevo, reemplazo de un documento del
     * expediente, y checklist de un préstamo) porque la comparación sale de la misma consulta que
     * el aviso del otro caso, {@code listarVigentesConMismoHash}, que trae el hash ya guardado.
     * Al reemplazar, el documento anterior del mismo tipo todavía está VIGENTE en este punto (se
     * marca REEMPLAZADO después de insertar el nuevo), así que la consulta lo encuentra.</p>
     *
     * <p><b>Primera versión, 01/11/2026: no bloqueaba nada.</b> Comparaba contra el documento
     * vigente devuelto por {@code obtenerVigentePorPersonaYTipo}/{@code ...PorPrestamoYTipo}, que
     * traen solo {@code id_archivo} y <b>no</b> hacen JOIN con {@code archivos_binarios}: el
     * {@code getArchivoBinario()} venía en null y el chequeo se salía sin comparar. Lo encontró
     * Joel en la prueba del 01/11 21:39-21:40, reemplazando dos veces el mismo
     * {@code ejemplo.pdf} en el expediente de Ana.</p>
     */
    private void rechazarSiYaEstaCargadoIgual(Connection conexion, Long idPersona, Long idPrestamo,
            long idTipoDocumento, byte[] contenido) throws SQLException {
        List<DocumentoExpediente> mismoContenido = documentoExpedienteDAO.listarVigentesConMismoHash(
                conexion, idPersona, idPrestamo, calcularHashSha256(contenido));
        String nombreDelMismoTipo = nombreDelTipoRepetido(mismoContenido, idTipoDocumento);
        if (nombreDelMismoTipo == null) {
            return;
        }
        throw new NegocioException(mensajeDeArchivoYaCargado(nombreDelMismoTipo));
    }

    /**
     * El texto del rechazo, con un solo dueño para que un test pueda pedirlo en vez de copiarlo.
     *
     * <p>Va en DOS oraciones a propósito: el cartel parte el mensaje en título y detalle por el
     * primer ". " seguido de mayúscula ({@code pie.jsp}, {@code dividirEnTituloYDetalle}) y al
     * título le saca el punto final, porque un título no lo lleva. Un mensaje de una sola oración
     * se muestra entero como título y pierde su punto -- que es lo que vio Joel el 01/11.</p>
     */
    public static String mensajeDeArchivoYaCargado(String nombreTipo) {
        return "Ese archivo ya está cargado. Es idéntico al que figura como «" + nombreTipo
                + "»: no hace falta volver a subirlo.";
    }

    /**
     * El nombre del tipo cuando entre los documentos con el mismo contenido hay uno de ESTE mismo
     * tipo (el caso que se bloquea); null si no hay ninguno. Pura.
     */
    static String nombreDelTipoRepetido(List<DocumentoExpediente> vigentesConMismoContenido, long idTipoDocumento) {
        for (DocumentoExpediente documento : vigentesConMismoContenido) {
            if (documento.getIdTipoDocumento() != null && documento.getIdTipoDocumento() == idTipoDocumento) {
                return documento.getTipoDocumento() == null ? "ese tipo"
                        : documento.getTipoDocumento().getNombreTipo();
            }
        }
        return null;
    }

    /**
     * Los tipos DISTINTOS del que se acaba de cargar que ya tienen ese mismo contenido -- el caso
     * que solo se avisa. Pura.
     */
    static List<String> otrosTiposConElMismoContenido(List<DocumentoExpediente> vigentesConMismoContenido,
            long idTipoDocumento) {
        List<String> otrosTipos = new ArrayList<>();
        for (DocumentoExpediente documento : vigentesConMismoContenido) {
            if (documento.getIdTipoDocumento() != null
                    && documento.getIdTipoDocumento() != idTipoDocumento
                    && documento.getTipoDocumento() != null
                    && !otrosTipos.contains(documento.getTipoDocumento().getNombreTipo())) {
                otrosTipos.add(documento.getTipoDocumento().getNombreTipo());
            }
        }
        return otrosTipos;
    }

    /**
     * El segundo caso: el mismo contenido ya cargado en OTRO tipo del mismo expediente. Devuelve
     * el aviso para la pantalla, o null si no hay nada que avisar.
     *
     * <p>Se consulta <b>después</b> de la carga, con el documento ya guardado, y por eso excluye
     * el tipo recién cargado. Un solo método para los dos caminos (persona y préstamo): el que no
     * corresponde va en null.</p>
     */
    public String avisoDeContenidoRepetido(Long idPersona, Long idPrestamo, long idTipoDocumentoRecienCargado,
            byte[] contenido) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return textoDelAvisoDeContenidoRepetido(otrosTiposConElMismoContenido(
                    documentoExpedienteDAO.listarVigentesConMismoHash(conexion, idPersona, idPrestamo,
                            calcularHashSha256(contenido)),
                    idTipoDocumentoRecienCargado));
        }
    }

    /**
     * El texto del aviso a partir de los tipos que ya tienen ese mismo contenido. Null si no hay
     * ninguno. Pura, para poder probarla con JUnit sin base de datos.
     */
    static String textoDelAvisoDeContenidoRepetido(List<String> otrosTipos) {
        if (otrosTipos.isEmpty()) {
            return null;
        }
        StringBuilder nombres = new StringBuilder();
        for (int i = 0; i < otrosTipos.size(); i++) {
            if (i > 0) {
                nombres.append(i == otrosTipos.size() - 1 ? " y " : ", ");
            }
            nombres.append("«").append(otrosTipos.get(i)).append("»");
        }
        return "Este archivo ya está cargado como " + nombres + ". Si es el mismo papel, está bien;"
                + " si se equivocó de archivo, vuelva a cargarlo.";
    }

    public static String calcularHashSha256(byte[] contenido) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido);
            StringBuilder hexadecimal = new StringBuilder();
            for (byte b : hash) {
                hexadecimal.append(String.format("%02x", b));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", error);
        }
    }

    /**
     * C19 (decisión de Joel, 07/11/2026; ajustado por él el mismo día): el texto que queda en la
     * auditoría cuando alguien abre un documento del expediente.
     *
     * <p>El primer intento decía <i>"de persona 3"</i> y Joel lo rechazó con razón: el gerente que
     * lee la auditoría meses después no sabe quién es "persona 3". Ahora dice el <b>nombre y la
     * C.I.</b> de la persona, el <b>N.º de operación</b> del préstamo o el <b>código</b> de la
     * solicitud — lo que esa misma persona ve en pantalla.</p>
     *
     * <p>Hace tres lecturas por id, una sola según el caso. Si el registro al que apunta no
     * existiera (no debería), cae al id: un detalle incompleto es mejor que una excepción que
     * impida ver el documento.</p>
     */
    public String detalleDeVisualizacion(Connection conexion, DocumentoExpediente documento) throws SQLException {
        return armarDetalleVisualizacion(documento, describirPertenencia(conexion, documento));
    }

    /** C19: a quién pertenece el documento, en palabras. Un documento cuelga de UNO de los tres. */
    private String describirPertenencia(Connection conexion, DocumentoExpediente documento) throws SQLException {
        if (documento.getIdPrestamo() != null) {
            PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, documento.getIdPrestamo());
            return prestamo == null || prestamo.getIdentificadorOperacion() == null
                    ? "préstamo " + documento.getIdPrestamo()
                    : "préstamo " + prestamo.getIdentificadorOperacion();
        }
        if (documento.getIdSolicitud() != null) {
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, documento.getIdSolicitud());
            return solicitud == null || solicitud.getCodigoSolicitud() == null
                    ? "solicitud " + documento.getIdSolicitud()
                    : "solicitud " + solicitud.getCodigoSolicitud();
        }
        if (documento.getIdPersona() != null) {
            Persona persona = personaDAO.buscarPorId(conexion, documento.getIdPersona());
            if (persona == null) {
                return "persona " + documento.getIdPersona();
            }
            // Apellidos y nombres, como se lee la columna del listado de clientes y como lo pidió
            // Joel ("López Rojas Mirtha Lucía"). La cédula se muestra con puntos con su único
            // dueño, FormateadorTexto.comoCedulaConPuntos: en la base se guarda normalizada (D-37).
            return persona.getApellidos() + " " + persona.getNombres()
                    + " (C.I. " + FormateadorTexto.comoCedulaConPuntos(persona.getCedulaIdentidad()) + ")";
        }
        return "sin vínculo";
    }

    /**
     * C19: el armado del texto, puro y de visibilidad de paquete para poder probarlo sin base.
     * Recibe la pertenencia ya resuelta, que es lo único que necesita ir a buscar a otra tabla.
     */
    static String armarDetalleVisualizacion(DocumentoExpediente documento, String pertenece) {
        String tipo = documento.getTipoDocumento() == null || documento.getTipoDocumento().getNombreTipo() == null
                ? "documento" : documento.getTipoDocumento().getNombreTipo();
        return "Abrió \"" + tipo + "\" (versión " + documento.getVersionDocumento() + ") de " + pertenece + ".";
    }

}
