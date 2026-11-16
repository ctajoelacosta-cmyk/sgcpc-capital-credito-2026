package py.com.capitalcredito.sgcpc.controladores;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.util.List;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAuditoria;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioConsultaCrediticia;
import py.com.capitalcredito.sgcpc.servicios.ServicioDocumentos;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado;

/**
 * P03, expediente/documentos (Doc. 10 §10.17/§10.19: ControladorDocumentosServlet
 * / ServicioDocumentos). Sirve el contenido de archivos_binarios: el
 * navegador nunca recibe una ruta física del servidor (Doc. 10 §10.17).
 * Ajuste C del plan de E3: el techo de @MultipartConfig es solo una red de
 * seguridad del contenedor (20 MB); el límite real es archivos.tamano_maximo_mb,
 * validado en ServicioDocumentos con el mismo mecanismo de mensajeError rojo
 * de siempre — nunca la página técnica de Tomcat.
 */
@MultipartConfig(maxFileSize = 20L * 1024 * 1024, maxRequestSize = 21L * 1024 * 1024, fileSizeThreshold = 0)
public class ControladorDocumentosServlet extends HttpServlet {

    private final ServicioDocumentos servicioDocumentos = new ServicioDocumentos();
    /** C19: para registrar quien abre cada documento del expediente. */
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    // B88 (pedido de Joel, 28/10/2026): el siguiente paso del cliente lo decide un solo dueño.
    private final ServicioPasoGuiado servicioPasoGuiado = new ServicioPasoGuiado();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo) || pathInfo.length() < 2) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        long idDocumento;
        try {
            idDocumento = Long.parseLong(pathInfo.substring(1));
        } catch (NumberFormatException error) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // C19 (decisión de Joel, 07/11/2026): quién abre el documento, para poder auditarlo. El
        // filtro de autorización ya garantizó que haya sesión y que el perfil tenga el permiso
        // (la cajera tiene NINGUNO y no llega acá); esto solo recupera el usuario para el registro.
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        descargar(request, response, idDocumento, solicitante);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String pathInfo = request.getPathInfo();
        // RF03/RF04 (pedido de Joel): "Cargar autorización firmada" de la fila 1 del Expediente --
        // un solo paso (sube el escaneo Y registra el consentimiento), ruta dedicada aparte de
        // "/cargar" para no meterle un caso especial a la genérica (que siguen usando las otras 3
        // filas y "Otros documentos" tal cual).
        if ("/cargar-autorizacion-firmada".equals(pathInfo)) {
            procesarCargarAutorizacionFirmada(request, response, solicitante);
            return;
        }
        // RF03 (pedido de Joel), punto 2: "Cargar informe" del Paso B (RESULTADO_CONSULTA_BURO) --
        // ruta dedicada aparte de la genérica, mismo motivo que cargar-autorizacion-firmada.
        if ("/cargar-informe-buro".equals(pathInfo)) {
            procesarCargarInformeBuro(request, response, solicitante);
            return;
        }
        if (!"/cargar".equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Ajuste C: el primer acceso a un parametro/parte ya dispara el parseo completo del
        // multipart, asi que si algun archivo supera el techo de @MultipartConfig, el
        // IllegalStateException puede saltar desde esta misma linea (idPersona/idPrestamo
        // incluidos) — por eso todo va dentro del mismo try/catch.
        // E5: el formulario manda idPersona (expediente de E3/E4) O idPrestamo (checklist de
        // formalización, P06), nunca los dos -- mismo endpoint, mismo flujo multipart/CSRF ya
        // validado desde E3 (D-56/D-58), en vez de duplicarlo en ControladorFormalizacionServlet.
        Long idPersona = null;
        Long idPrestamo = null;
        // E9/Bloque 5 (cierre documental, P10): la constancia de devolución firmada se carga
        // desde /formalizacion/{id}/cierre-documental, no desde la ficha de Documentos -- si el
        // formulario manda "volverA", el PRG vuelve ahí en vez del destino de siempre.
        String volverA = request.getParameter("volverA");
        try {
            String parametroPersona = request.getParameter("idPersona");
            String parametroPrestamo = request.getParameter("idPrestamo");
            long idTipoDocumento = Long.parseLong(request.getParameter("idTipoDocumento"));
            Part parte = request.getPart("archivo");
            if (parte == null || parte.getSize() == 0) {
                throw new NegocioException("Seleccione un archivo para cargar.");
            }
            String nombreArchivo = nombreDeParte(parte);
            byte[] contenido = leerBytes(parte.getInputStream());
            if (parametroPrestamo != null) {
                idPrestamo = Long.parseLong(parametroPrestamo);
                servicioDocumentos.cargarDocumentoPrestamo(idPrestamo, idTipoDocumento, nombreArchivo,
                        parte.getContentType(), contenido, solicitante.getIdUsuario());
                // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 11): mismo patrón
                // de 3 casos que ya usa idPersona más abajo. Si ya viene con un volverA propio
                // (ej. la constancia de devolución de pagaré, cierre documental E9 -- otro tipo de
                // documento, en otra etapa del préstamo, donde "listo para desembolso" ya no tiene
                // sentido), se deja el mensaje genérico de siempre; el checklist de este bloque es
                // solo el de ANTES de desembolsar (contrato/pagaré/plan de pagos).
                boolean esChecklistPreDesembolso = volverA == null || volverA.trim().isEmpty();
                List<String> faltantesChecklist = esChecklistPreDesembolso
                        ? itemsFaltantesChecklistPrestamo(idPrestamo) : java.util.Collections.emptyList();
                if (!esChecklistPreDesembolso) {
                    MensajesFlash.guardarExito(sesion, "Documento cargado.");
                } else if (faltantesChecklist.isEmpty()) {
                    MensajesFlash.guardarExito(sesion, "Documento cargado. Checklist completo: listo para desembolso.");
                } else {
                    MensajesFlash.guardarExito(sesion, "Documento cargado. Faltan: "
                            + String.join(", ", faltantesChecklist) + ".");
                }
                avisarSiElContenidoYaEstaCargado(sesion, null, idPrestamo, idTipoDocumento, contenido);
            } else {
                idPersona = Long.parseLong(parametroPersona);
                servicioDocumentos.cargarDocumento(idPersona, idTipoDocumento, nombreArchivo, parte.getContentType(),
                        contenido, solicitante.getIdUsuario());
                // B88 (pedido explícito de Joel, 28/10/2026): el texto Y el destino salen del paso
                // guiado -- el PRIMER paso pendiente con el estado ya actualizado por esta carga
                // (ServicioClientes.calcularSiguientePaso, el único dueño de esa regla). Antes el
                // destino era fijo: con el expediente completo mandaba siempre a ③, incluso con el
                // buró ya registrado (caso visto el 28/10). Solo se usa cuando el formulario no
                // pidió ya un destino propio (las filas del Expediente y "Otros documentos" no lo
                // piden; los formularios de ③ sí, y ya van a #tabBuro).
                // 29/10/2026 (decisión de Joel sobre el paso 3 de la prueba): la acción se le dice
                // al dueño de la regla -- si todavía falta otro documento del expediente, se queda
                // en ② en vez de sacarlo de ahí con cada papel que carga.
                PasoGuiado pasoGuiado = pasoGuiadoDePersona(idPersona,
                        ServicioClientes.AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE);
                MensajesFlash.guardarExito(sesion, pasoGuiado.tieneTexto()
                        ? "Documento cargado. " + pasoGuiado.getTexto() : "Documento cargado.");
                if ((volverA == null || volverA.trim().isEmpty()) && pasoGuiado.getRuta() != null) {
                    volverA = pasoGuiado.getRuta();
                }
                avisarSiElContenidoYaEstaCargado(sesion, idPersona, null, idTipoDocumento, contenido);
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (IllegalStateException error) {
            // Ajuste C: un archivo que supera igual el techo de @MultipartConfig cae aca, nunca
            // en la pagina tecnica de Tomcat.
            getServletContext().log("Archivo cargado supera el techo del contenedor.", error);
            MensajesFlash.guardarError(sesion, "El archivo es demasiado grande.");
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cargar un documento.", error);
            MensajesFlash.guardarError(sesion, "No se pudo cargar el documento. Intente nuevamente.");
        }
        // Punto 3 del pedido de Joel: volver a la pestaña de Documentos, no a la de Ficha.
        if (volverA != null && !volverA.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + volverA);
        } else if (idPrestamo != null) {
            response.sendRedirect(request.getContextPath() + "/formalizacion/" + idPrestamo);
        } else {
            response.sendRedirect(request.getContextPath()
                    + (idPersona == null ? "/clientes" : "/clientes/" + idPersona + "#tabDocumentos"));
        }
    }

    /**
     * B42 (01/11/2026): aviso amarillo cuando el archivo recién cargado tiene el MISMO contenido
     * que otro ya vigente del mismo expediente, pero de otro tipo. Se guarda igual (una sola hoja
     * escaneada puede servir para dos tipos); esto solo lo dice. El caso "mismo tipo, mismo
     * contenido" ni llega acá: lo rechaza ServicioDocumentos antes de crear la versión.
     *
     * <p>Un solo método para los dos caminos de carga: el id que no corresponde va en null.</p>
     */
    private void avisarSiElContenidoYaEstaCargado(HttpSession sesion, Long idPersona, Long idPrestamo,
            long idTipoDocumento, byte[] contenido) throws SQLException {
        String aviso = servicioDocumentos.avisoDeContenidoRepetido(idPersona, idPrestamo, idTipoDocumento, contenido);
        if (aviso != null) {
            MensajesFlash.guardarAdvertencia(sesion, aviso);
        }
    }

    /**
     * RF03/RF04 (pedido de Joel): "Cargar autorización firmada" -- sube el escaneo y registra el
     * consentimiento en un solo paso (ServicioConsultaCrediticia.cargarAutorizacionFirmada, que
     * rechaza si la última autorización generada no es de hoy). Mismo parseo de multipart que
     * "/cargar" (nombreDeParte/leerBytes, reusados -- no se duplica esa lógica).
     */
    private void procesarCargarAutorizacionFirmada(HttpServletRequest request, HttpServletResponse response,
            Usuario solicitante) throws ServletException, IOException {
        HttpSession sesion = request.getSession();
        Long idPersona = null;
        PasoGuiado pasoGuiadoRedirect = null;
        try {
            idPersona = Long.parseLong(request.getParameter("idPersona"));
            long idTipoDocumento = Long.parseLong(request.getParameter("idTipoDocumento"));
            Part parte = request.getPart("archivo");
            if (parte == null || parte.getSize() == 0) {
                throw new NegocioException("Seleccione un archivo para cargar.");
            }
            String nombreArchivo = nombreDeParte(parte);
            byte[] contenido = leerBytes(parte.getInputStream());
            servicioConsultaCrediticia.cargarAutorizacionFirmada(idPersona, idTipoDocumento, nombreArchivo,
                    parte.getContentType(), contenido, solicitante.getIdUsuario());
            // B88 (pedido explícito de Joel, 28/10/2026): mismo dueño que la carga genérica -- el
            // texto y el destino son el PRIMER paso pendiente, no un "#tabBuro" fijo. Con el orden
            // "opción B" (decisión de Joel del 29/10) el buró va antes que el resto de los papeles,
            // así que cargar la autorización lleva a ③ aunque falte el comprobante de domicilio;
            // lo que ya no pasa es que lleve a ③ cuando lo que corresponde es otra cosa (buró ya
            // registrado, solicitud lista para enviar).
            pasoGuiadoRedirect = pasoGuiadoDePersona(idPersona, ServicioClientes.AccionPrevia.CARGA_AUTORIZACION);
            MensajesFlash.guardarExito(sesion, pasoGuiadoRedirect.tieneTexto()
                    ? "Autorización cargada y consentimiento registrado. " + pasoGuiadoRedirect.getTexto()
                    : "Autorización cargada y consentimiento registrado.");
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (IllegalStateException error) {
            getServletContext().log("Archivo cargado supera el techo del contenedor.", error);
            MensajesFlash.guardarError(sesion, "El archivo es demasiado grande.");
        } catch (NumberFormatException error) {
            MensajesFlash.guardarError(sesion, "Falta el cliente o el tipo de documento.");
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cargar la autorización firmada.", error);
            MensajesFlash.guardarError(sesion, "No se pudo cargar la autorización. Intente nuevamente.");
        }
        // B88: si la carga salió bien, el destino es el del paso guiado; si falló (o no hay
        // persona), queda el de siempre -- la pestaña ③, donde está el formulario que se usó.
        String destinoAutorizacion = pasoGuiadoRedirect == null || pasoGuiadoRedirect.getRuta() == null
                ? (idPersona == null ? "/clientes" : "/clientes/" + idPersona + "#tabBuro")
                : pasoGuiadoRedirect.getRuta();
        response.sendRedirect(request.getContextPath() + destinoAutorizacion);
    }

    /**
     * RF03 (pedido de Joel), punto 2: "Cargar informe" del Paso B (RESULTADO_CONSULTA_BURO) -- ruta
     * dedicada aparte de la genérica, mismo motivo que cargar-autorizacion-firmada (hallazgo de un
     * PDF falso cargado como autorización desde "Otros documentos", ya quitado: ese tipo no se
     * puede subir por "/cargar"). A diferencia de la autorización, no registra ningún
     * consentimiento acá -- el informe es solo evidencia, "Registrar resultado de la consulta" la
     * vincula después. Mismo parseo de multipart que "/cargar" (nombreDeParte/leerBytes, reusados).
     */
    private void procesarCargarInformeBuro(HttpServletRequest request, HttpServletResponse response,
            Usuario solicitante) throws ServletException, IOException {
        HttpSession sesion = request.getSession();
        Long idPersona = null;
        try {
            idPersona = Long.parseLong(request.getParameter("idPersona"));
            long idTipoDocumento = Long.parseLong(request.getParameter("idTipoDocumento"));
            Part parte = request.getPart("archivo");
            if (parte == null || parte.getSize() == 0) {
                throw new NegocioException("Seleccione un archivo para cargar.");
            }
            String nombreArchivo = nombreDeParte(parte);
            byte[] contenido = leerBytes(parte.getInputStream());
            servicioConsultaCrediticia.cargarInformeBuro(idPersona, idTipoDocumento, nombreArchivo,
                    parte.getContentType(), contenido, solicitante.getIdUsuario());
            // Hallazgo de Joel probando RF04 en pantalla: "Documento cargado." solo, sin decir
            // qué sigue, no alcanza -- el próximo paso es completar el formulario de abajo.
            MensajesFlash.guardarExito(sesion, "Documento cargado. Complete el puntaje, la clasificación "
                    + "y si tiene mora, y toque Registrar consulta.");
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (IllegalStateException error) {
            getServletContext().log("Archivo cargado supera el techo del contenedor.", error);
            MensajesFlash.guardarError(sesion, "El archivo es demasiado grande.");
        } catch (NumberFormatException error) {
            MensajesFlash.guardarError(sesion, "Falta el cliente o el tipo de documento.");
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cargar el informe del buró.", error);
            MensajesFlash.guardarError(sesion, "No se pudo cargar el informe. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath()
                + (idPersona == null ? "/clientes" : "/clientes/" + idPersona + "#tabBuro"));
    }

    /** B88 (pedido de Joel, 28/10/2026): el primer paso pendiente del cliente, releído con los
     * datos YA actualizados (justo después de guardar) -- de ahí salen el texto del modal y el
     * destino del redirect. Reemplaza al itemsFaltantesExpediente que esta clase tenía: la regla
     * de "qué falta y qué sigue" ahora vive en un solo lugar (ServicioClientes.calcularSiguientePaso,
     * vía ServicioPasoGuiado), no una copia por pantalla. Nunca rompe la carga si falla: un paso
     * sin texto ni destino ante cualquier error de lectura (el aviso de éxito sale igual). */
    private PasoGuiado pasoGuiadoDePersona(long idPersona, ServicioClientes.AccionPrevia accionPrevia) {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return servicioPasoGuiado.calcular(conexion, idPersona, accionPrevia);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al calcular el siguiente paso del cliente.", error);
            return new PasoGuiado(null, PasoGuiado.Destino.NINGUNO, null);
        }
    }

    /** Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 11): mismo criterio que
     * pasoGuiadoDePersona de arriba -- se vuelve a calcular justo después de guardar, para
     * decir en el modal qué falta del checklist de PRESTAMO (contrato/pagaré/plan de pagos,
     * obligatorio_desembolso) en vez de un "Documento cargado." siempre igual. Nunca rompe la
     * carga si falla: lista vacía ante cualquier error de lectura. */
    private List<String> itemsFaltantesChecklistPrestamo(long idPrestamo) {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<ServicioFormalizacion.ItemChecklist> checklist = servicioFormalizacion.obtenerChecklist(conexion, idPrestamo);
            List<String> faltantes = new java.util.ArrayList<>();
            for (ServicioFormalizacion.ItemChecklist item : checklist) {
                if (!item.isCumplido()) {
                    faltantes.add(item.getTipo().getNombreTipo());
                }
            }
            return faltantes;
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al revisar qué falta del checklist.", error);
            return java.util.Collections.emptyList();
        }
    }

    private void descargar(HttpServletRequest request, HttpServletResponse response, long idDocumento,
            Usuario solicitante) throws IOException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            DocumentoExpediente documento = servicioDocumentos.obtenerPorId(conexion, idDocumento);
            if (documento == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            byte[] contenido = servicioDocumentos.obtenerContenido(conexion, documento.getArchivoBinario().getIdArchivo());
            if (contenido == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            // C19 (decisión de Joel, 07/11/2026): se registra la apertura ANTES de servir el
            // contenido, para que no quede un documento entregado sin su fila. Se registra CADA
            // apertura y no solo la primera: un log de accesos que omite repeticiones no sirve
            // para saber quién miró qué y cuándo, y el informe ya filtra por operación para
            // aislarla. Solo el documento NO se audita (si no existe o no tiene contenido, no se
            // vio nada).
            //
            // Lo que NO se controla acá, a propósito y por decisión de Joel del mismo día: la
            // PERTENENCIA. En Capital Crédito todo oficial ve a todos los clientes --no hay
            // oficial asignado en "personas" ni filtro por oficial en el listado-- así que cambiar
            // el id en la URL no da acceso a nada que el usuario no pueda abrir navegando la
            // ficha. Restringir por pertenencia seria inventar una regla que el anteproyecto no
            // pide y que cambiaria el producto entero, no esta ruta.
            registrarVisualizacion(conexion, documento, solicitante);
            String nombreArchivo = documento.getArchivoBinario().getNombreArchivo();
            response.setContentType(documento.getArchivoBinario().getMimeType());
            response.setContentLength(contenido.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", contentDisposition(nombreArchivo));
            try (OutputStream salida = response.getOutputStream()) {
                salida.write(contenido);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al descargar el documento " + idDocumento + ".", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * C19: registra que alguien abrió un documento del expediente. Mismo {@code tipoEntidad} que
     * la carga ({@code DOCUMENTO_EXPEDIENTE}) y el mismo id, así el informe muestra las dos
     * operaciones del mismo documento juntas.
     *
     * <p>El texto del detalle lo arma {@code ServicioDocumentos}: es el dueño del documento y el
     * único que puede resolver de quién es con palabras (nombre y C.I., N.º de operación o código
     * de solicitud) en vez de con un id.</p>
     */
    private void registrarVisualizacion(Connection conexion, DocumentoExpediente documento, Usuario solicitante)
            throws SQLException {
        if (solicitante == null) {
            // No deberia pasar: el filtro de autenticacion corre antes. Si pasara, no se sirve el
            // documento sin saber quien lo pide.
            throw new IllegalStateException("Sin usuario en sesion al abrir el documento "
                    + documento.getIdDocumento() + ".");
        }
        servicioAuditoria.registrar(conexion, solicitante.getIdUsuario(), "VER_DOCUMENTO_EXPEDIENTE",
                "DOCUMENTO_EXPEDIENTE", documento.getIdDocumento(),
                servicioDocumentos.detalleDeVisualizacion(conexion, documento));
    }

    /** RF03 (pedido de Joel): "inline", no "attachment" -- que se abra en la pestaña (PDF/JPG/PNG,
     * los únicos tipos que esta ruta sirve), nunca que descargue un archivo a la carpeta
     * Descargas de la PC del mostrador (usabilidad y privacidad: cédulas, comprobantes). Nombre
     * original con tildes, codificado RFC 5987 (filename* UTF-8), con respaldo ASCII en filename
     * -- solo importa si el usuario decide guardarlo a mano desde el visor. */
    private String contentDisposition(String nombreArchivo) throws UnsupportedEncodingException {
        String ascii = nombreArchivo.replaceAll("[^\\x20-\\x7E]", "_");
        String codificado = URLEncoder.encode(nombreArchivo, "UTF-8").replace("+", "%20");
        return "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + codificado;
    }

    private String nombreDeParte(Part parte) {
        String encabezado = parte.getHeader("content-disposition");
        for (String segmento : encabezado.split(";")) {
            segmento = segmento.trim();
            if (segmento.startsWith("filename")) {
                String nombre = segmento.substring(segmento.indexOf('=') + 1).trim().replaceAll("^\"|\"$", "");
                int barra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
                return barra >= 0 ? nombre.substring(barra + 1) : nombre;
            }
        }
        throw new NegocioException("No se pudo leer el nombre del archivo.");
    }

    private byte[] leerBytes(InputStream entrada) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] trozo = new byte[8192];
        int leidos;
        while ((leidos = entrada.read(trozo)) != -1) {
            buffer.write(trozo, 0, leidos);
        }
        return buffer.toByteArray();
    }
}
