package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.dao.AuditoriaDAO;
import py.com.capitalcredito.sgcpc.dao.ConsentimientoCrediticioDAO;
import py.com.capitalcredito.sgcpc.dao.ConsultaCrediticiaDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.infraestructura.TextoLegalAutorizacionBuro;
import py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.Persona;

/**
 * P03, parte autorización y consulta al buró (Doc. 10 §10.19: la maneja
 * ControladorClientesServlet, delegando aquí — Doc. 10 no nombra un
 * controlador aparte). RN-24: la consulta solo se registra con una
 * autorización firmada y vigente (D-38: fecha_consentimiento + días de
 * vigencia >= hoy, y el documento de evidencia sigue VIGENTE). D-39: la
 * consulta no tiene solicitud todavía en E3 (id_solicitud queda NULL; el
 * vínculo con una solicitud se resuelve en el plan de E4).
 */
public class ServicioConsultaCrediticia {

    private final ConsentimientoCrediticioDAO consentimientoDAO = new ConsentimientoCrediticioDAO();
    private final ConsultaCrediticiaDAO consultaDAO = new ConsultaCrediticiaDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioDocumentos servicioDocumentos = new ServicioDocumentos();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
    // Normalización de personas, grupo Domicilio (16/10/2026): para armar CLIENTE_DOMICILIO al
    // vuelo (antes leía persona.getDomicilio() directo). Sin dependencia circular: ServicioClientes
    // no depende de ServicioConsultaCrediticia.
    private final ServicioClientes servicioClientes = new ServicioClientes();

    /** RF03/RF04 (pedido de Joel): única operación de auditoría para "se generó el PDF de
     * autorización" -- sirve a la vez de ✓ de "Generar" en el Expediente y de marca de "mismo día"
     * para la carga de la firmada (cargarAutorizacionFirmada). Sin tabla ni columna nueva. */
    private static final String OPERACION_GENERAR_AUTORIZACION_BURO = "GENERAR_AUTORIZACION_BURO";

    /** D-38. Pura. */
    public LocalDate calcularFechaVencimiento(LocalDate fechaConsentimiento, int diasVigencia) {
        return fechaConsentimiento.plusDays(diasVigencia);
    }

    /**
     * D-38: valida que exista un consentimiento cuya evidencia siga VIGENTE
     * y cuya vigencia (fecha_consentimiento + dias) no haya vencido. Pura
     * (recibe el último consentimiento y el parámetro ya resueltos).
     */
    public void validarConsultaPermitida(ConsentimientoCrediticio ultimoConsentimiento, int diasVigencia, LocalDate hoy) {
        if (ultimoConsentimiento == null || ultimoConsentimiento.getEstadoDocumentoEvidencia() != EstadoDocumento.VIGENTE) {
            throw new NegocioException("No hay una autorización de consulta vigente. Cargue el documento firmado "
                    + "y registre el consentimiento antes de consultar.");
        }
        LocalDate vencimiento = calcularFechaVencimiento(ultimoConsentimiento.getFechaConsentimiento().toLocalDate(),
                diasVigencia);
        if (vencimiento.isBefore(hoy)) {
            throw new NegocioException("La autorización de consulta venció el " + FormateadorFechas.comoDdMmAaaa(vencimiento)
                    + ". Cargue una autorización nueva firmada por el cliente.");
        }
    }

    /** Registra el consentimiento firmado (evidencia ya cargada como documento del expediente). */
    public long registrarConsentimiento(long idPersona, long idDocumentoEvidencia, String finalidad,
            long idUsuarioQueRegistra) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                ConsentimientoCrediticio consentimiento = new ConsentimientoCrediticio();
                consentimiento.setIdPersona(idPersona);
                consentimiento.setIdDocumentoEvidencia(idDocumentoEvidencia);
                consentimiento.setFinalidad(finalidad);
                consentimiento.setRegistradoPor(idUsuarioQueRegistra);
                long idNuevo = consentimientoDAO.insertar(conexion, consentimiento);
                servicioAuditoria.registrar(conexion, idUsuarioQueRegistra, "REGISTRAR_CONSENTIMIENTO",
                        "CONSENTIMIENTO_CREDITICIO", idNuevo, "Registró el consentimiento de consulta al buró.");
                conexion.commit();
                return idNuevo;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * A pedido de Joel (D-57): "¿Tiene mora?" es obligatorio -- en E4 esta consulta alimenta la
     * evaluación de la solicitud, y un resultado sin ese dato no sirve para decidir. Pura.
     */
    public void validarIndicadorMora(Boolean indicadorMora) {
        if (indicadorMora == null) {
            throw new NegocioException("Debe indicar si el cliente tiene mora o no.");
        }
    }

    /**
     * Ajuste D del plan de E3 (acordado con Joel, ver D-58): el resultado de la consulta debe
     * adjuntar la evidencia (el informe de Informconf cargado como documento del expediente,
     * tipo RESULTADO_CONSULTA_BURO), igual que el consentimiento usa AUTORIZACION_CONSULTA_BURO.
     * Pura: no valida el tipo/estado del documento (eso ya lo filtra el <select> de la ficha,
     * mismo patrón que idDocumentoEvidencia en registrarConsentimiento), solo que se haya
     * elegido alguno.
     */
    public void validarDocumentoResultado(Long idDocumentoResultado) {
        if (idDocumentoResultado == null) {
            throw new NegocioException("Primero cargue en Expediente / Documentos la respuesta de Informconf "
                    + "(tipo Resultado de consulta al buró).");
        }
    }

    /** Registra el resultado de la consulta hecha a mano a Informconf (RN-18/PO-02 paso 6). */
    public long registrarConsulta(long idPersona, String proveedorBuro, BigDecimal puntajeObtenido,
            String clasificacionObtenida, Boolean indicadorMora, String resumenResultado, Long idDocumentoResultado,
            long idUsuarioQueConsulta) throws SQLException {
        validarIndicadorMora(indicadorMora);
        validarDocumentoResultado(idDocumentoResultado);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                int diasVigencia = servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento");
                ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
                validarConsultaPermitida(ultimo, diasVigencia, Reloj.hoy());

                ConsultaCrediticia consulta = new ConsultaCrediticia();
                consulta.setIdPersona(idPersona);
                consulta.setIdConsentimiento(ultimo.getIdConsentimiento());
                consulta.setProveedorBuro(proveedorBuro);
                consulta.setConsultadoPor(idUsuarioQueConsulta);
                consulta.setPuntajeObtenido(puntajeObtenido);
                consulta.setClasificacionObtenida(clasificacionObtenida);
                consulta.setIndicadorMora(indicadorMora);
                consulta.setResumenResultado(resumenResultado);
                consulta.setIdDocumentoResultado(idDocumentoResultado);
                long idNuevo = consultaDAO.insertar(conexion, consulta);

                servicioAuditoria.registrar(conexion, idUsuarioQueConsulta, "REGISTRAR_CONSULTA_BURO",
                        "CONSULTA_CREDITICIA", idNuevo, "Registró el resultado de la consulta a \"" + proveedorBuro
                                + "\".");
                conexion.commit();
                return idNuevo;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    public List<ConsultaCrediticia> listarConsultas(Connection conexion, long idPersona) throws SQLException {
        return consultaDAO.listarPorPersona(conexion, idPersona);
    }

    /** E4 (D-59): deja constancia de qué consulta sustentó la evaluación de una solicitud. */
    public void vincularASolicitud(Connection conexion, long idConsulta, long idSolicitud) throws SQLException {
        consultaDAO.vincularASolicitud(conexion, idConsulta, idSolicitud);
    }

    /** E4 (D-59): para mostrar en /aprobaciones/{id} el resultado del buró que sustentó la decisión. */
    public List<ConsultaCrediticia> listarPorSolicitud(Connection conexion, long idSolicitud) throws SQLException {
        return consultaDAO.listarPorSolicitud(conexion, idSolicitud);
    }

    /**
     * E4 (D-59/D-63, ajuste A del plan): la consulta que sustenta la evaluación de una solicitud
     * necesita autorización vigente (D-38), evidencia adjunta (D-58) y ser posterior a esa
     * autorización -- una consulta vieja, de una autorización ya vencida o reemplazada, no
     * cuenta aunque tenga evidencia. `etiquetaPersona` es "cliente", para el mensaje.
     */
    public ConsultaCrediticia obtenerParaVincularSolicitud(Connection conexion, long idPersona, String etiquetaPersona)
            throws SQLException {
        int diasVigencia = servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento");
        ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
        validarConsultaPermitida(ultimo, diasVigencia, Reloj.hoy());
        ConsultaCrediticia consulta = buscarMasRecienteConEvidencia(
                consultaDAO.listarPorPersona(conexion, idPersona), ultimo);
        if (consulta == null) {
            throw new NegocioException("No hay una consulta al buró con evidencia registrada para el " + etiquetaPersona
                    + " después de la autorización vigente. Registre el resultado de la consulta (con su evidencia) "
                    + "antes de enviar la solicitud a aprobación.");
        }
        return consulta;
    }

    /**
     * RF08 (pedido de Joel, 11/10/2026): la consulta que `obtenerParaVincularSolicitud` elegiría
     * si la solicitud se enviara HOY (más reciente con evidencia, posterior al consentimiento
     * vigente) -- para mostrarla en `aprobaciones/ficha.jsp` aunque la solicitud ya se haya
     * enviado hace tiempo con otra consulta vinculada (hallazgo del 11/10/2026: una consulta
     * nueva, registrada después del envío, quedaba invisible para el gerente porque la ficha
     * leía solo por `id_solicitud`, que nunca se actualiza). A diferencia de
     * `obtenerParaVincularSolicitud`, no lanza si no hay ninguna -- es solo para mostrar, nunca
     * para decidir si se puede enviar/aprobar.
     */
    public ConsultaCrediticia obtenerMasRecienteConEvidencia(Connection conexion, long idPersona) throws SQLException {
        ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
        if (ultimo == null) {
            return null;
        }
        return buscarMasRecienteConEvidencia(consultaDAO.listarPorPersona(conexion, idPersona), ultimo);
    }

    /** La primera (más reciente, por el orden de listarPorPersona) con evidencia y posterior al
     * consentimiento -- mismo criterio en los tres lugares que lo necesitan (RN-24/D-58). Pura. */
    private ConsultaCrediticia buscarMasRecienteConEvidencia(List<ConsultaCrediticia> consultas,
            ConsentimientoCrediticio ultimoConsentimiento) {
        for (ConsultaCrediticia consulta : consultas) {
            if (consulta.getIdDocumentoResultado() != null
                    && !consulta.getFechaHoraConsulta().isBefore(ultimoConsentimiento.getFechaConsentimiento())) {
                return consulta;
            }
        }
        return null;
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 19/22): versión sin lanzar de validarConsultaPermitida, para el
     * checklist (✓ / falta) que se muestra antes de "Enviar a aprobación" -- ahí no interesa el
     * mensaje puntual (ya lo da enviarAAprobacion si hace falta), solo si el ítem está cumplido.
     */
    public boolean tieneAutorizacionVigente(Connection conexion, long idPersona) throws SQLException {
        int diasVigencia = servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento");
        ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
        try {
            validarConsultaPermitida(ultimo, diasVigencia, Reloj.hoy());
            return true;
        } catch (NegocioException error) {
            return false;
        }
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 19/22, ítem "consulta a Informconf registrada"): mismo criterio
     * que obtenerParaVincularSolicitud (consulta con evidencia, posterior a la autorización
     * vigente), sin lanzar -- para el checklist.
     */
    public boolean tieneConsultaConEvidenciaRegistrada(Connection conexion, long idPersona) throws SQLException {
        ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
        if (ultimo == null) {
            return false;
        }
        return buscarMasRecienteConEvidencia(consultaDAO.listarPorPersona(conexion, idPersona), ultimo) != null;
    }

    /** Para mostrar "vigente hasta dd/mm/aaaa" en la pestaña de buró; null si no hay consentimiento cargado. */
    public LocalDate obtenerFechaVencimientoConsentimiento(Connection conexion, long idPersona) throws SQLException {
        ConsentimientoCrediticio ultimo = consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
        if (ultimo == null) {
            return null;
        }
        int diasVigencia = servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento");
        return calcularFechaVencimiento(ultimo.getFechaConsentimiento().toLocalDate(), diasVigencia);
    }

    /** RF03/RF04 (pedido de Joel): días que faltan para que venza (0 el último día que todavía
     * cuenta como vigente; negativo si ya venció). Pura. */
    public long diasRestantesVigencia(LocalDate vencimiento, LocalDate hoy) {
        return ChronoUnit.DAYS.between(hoy, vencimiento);
    }

    /** RF03/RF04 (pedido de Joel): última vez que se generó el PDF de autorización para esta
     * persona -- fecha/hora y número (AUT-aaaa-NNNNN), sin tabla nueva (auditoría). null si nunca
     * se generó. La usa el ✓ de "Generar" del Expediente, el estado del Paso A, y
     * cargarAutorizacionFirmada (validación de "mismo día"). */
    public UltimaOperacionAuditoria obtenerUltimaGeneracionAutorizacion(Connection conexion, long idPersona)
            throws SQLException {
        return auditoriaDAO.obtenerUltima(conexion, OPERACION_GENERAR_AUTORIZACION_BURO, "PERSONA", idPersona);
    }

    /** RF03/RF04: ¿la última autorización generada es de HOY? -- la usa el Expediente para elegir
     * "Reemplazar" (mismo escaneo, mismo día) vs. "Generar autorización nueva" (otro día), y
     * cargarAutorizacionFirmada para el mismo chequeo del lado del servidor. */
    public boolean autorizacionGeneradaHoy(Connection conexion, long idPersona) throws SQLException {
        UltimaOperacionAuditoria ultima = obtenerUltimaGeneracionAutorizacion(conexion, idPersona);
        return ultima != null && ultima.getFechaHora().toLocalDate().isEqual(Reloj.hoy());
    }

    /**
     * RF03 Parte 2 (pedido de Joel): registra la generación -- número único (numeradores, clave
     * AUTORIZACION) y la auditoría GENERAR_AUTORIZACION_BURO (✓ de "Generar" en el Expediente, y
     * la marca de "mismo día" para cargarAutorizacionFirmada) -- sin renderizar ningún PDF acá.
     * El controlador redirige con este número en el mensaje flash; el PDF se ve/imprime recién
     * con reimprimirAutorizacionBuroPdf (mismo patrón que Formalización: generar ≠ imprimir).
     * Transacción propia: numerador + auditoría van juntos o no va ninguno.
     */
    public String generarAutorizacionBuro(Persona persona, long idUsuarioEmisor) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                int anio = Reloj.hoy().getYear();
                long secuencia = numeradorDAO.siguienteValor(conexion, "AUTORIZACION", anio);
                String numero = String.format("AUT-%d-%05d", anio, secuencia);
                servicioAuditoria.registrarCambioDeValor(conexion, idUsuarioEmisor,
                        OPERACION_GENERAR_AUTORIZACION_BURO, "PERSONA", persona.getIdPersona(), null, numero,
                        "Generó la autorización de consulta al buró " + numero + " para \"" + persona.getNombres()
                                + " " + persona.getApellidos() + "\".");
                conexion.commit();
                return numero;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * RF03 Parte 2 (pedido de Joel): "Reimprimir autorización" -- re-renderiza el PDF con el
     * MISMO número y la MISMA fecha/hora de emisión de la última generación (ultimaGeneracion,
     * ver obtenerUltimaGeneracionAutorizacion), sin tocar numeradores ni auditoria_operaciones:
     * es exactamente el mismo documento, de nuevo. La usan tanto el botón "Reimprimir
     * autorización" (generada hoy, todavía sin cargar) como el enlace "Abrir para imprimir" del
     * modal de éxito justo después de generar.
     */
    public byte[] reimprimirAutorizacionBuroPdf(Persona persona, UltimaOperacionAuditoria ultimaGeneracion,
            String nombreUsuarioEmisor) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return construirPdfAutorizacionBuro(conexion, persona, ultimaGeneracion.getValor(),
                    ultimaGeneracion.getFechaHora(), nombreUsuarioEmisor);
        }
    }

    /** Arma el PDF "Autorización para consulta de información crediticia" -- lugar y fecha/hora
     * de emisión, "vigente hasta" (buro.dias_vigencia_consentimiento desde la fecha de EMISIÓN,
     * no desde hoy: una reimpresión de ayer no corre la vigencia) y el texto legal único de
     * TextoLegalAutorizacionBuro. Pura en el sentido de no escribir nada -- numero/fechaHoraEmision
     * ya vienen resueltos (nuevos, al generar, o reutilizados, al reimprimir). */
    private byte[] construirPdfAutorizacionBuro(Connection conexion, Persona persona, String numero,
            LocalDateTime fechaHoraEmision, String nombreUsuarioEmisor) throws SQLException {
        int diasVigencia = servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento");
        LocalDate vigenteHasta = fechaHoraEmision.toLocalDate().plusDays(diasVigencia);

        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros,
                nombreUsuarioEmisor);
        // Lote 2, L2-C (14/10/2026, pedido de Joel, autorizado): parametrosBase pone
        // FECHA_EMISION = Reloj.ahora(), pero el pie debe mostrar la fecha/hora REAL de emisión
        // (D-94) -- la misma que ya usa LUGAR_FECHA_EMISION más abajo. Se sobrescribe acá, sin
        // tocar parametrosBase (lo usan otros reportes).
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(fechaHoraEmision));
        // Lote 2, L2-C (14/10/2026, hallazgo de Joel): parametrosBase también pone los EMPRESA_*
        // vigentes HOY, pero la autorización es un documento firmado en papel por el cliente --
        // una reimpresión tiene que mostrar los datos de empresa vigentes el día de la emisión
        // (D-94), no los de hoy. Se sobrescriben acá, sin tocar parametrosBase.
        LocalDate fechaEmision = fechaHoraEmision.toLocalDate();
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fechaEmision));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fechaEmision));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fechaEmision));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fechaEmision));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fechaEmision));
        parametros.put("NUMERO_AUTORIZACION", numero);
        parametros.put("LUGAR_FECHA_EMISION", "Asunción, " + FormateadorFechas.comoDdMmAaaaHhMm(fechaHoraEmision));
        parametros.put("VIGENTE_HASTA", FormateadorFechas.comoDdMmAaaa(vigenteHasta));
        // RF03 Parte 3 (pedido de Joel): nombra a la empresa real -- EMPRESA_RAZON_SOCIAL ya está
        // en el mapa (GeneradorInformesPdf.parametrosBase, un poco más arriba), se reutiliza en
        // vez de leer el parámetro de nuevo.
        parametros.put("TEXTO_LEGAL", TextoLegalAutorizacionBuro.completo((String) parametros.get("EMPRESA_RAZON_SOCIAL")));
        parametros.put("CLIENTE_NOMBRE", persona.getNombres() + " " + persona.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(persona.getCedulaIdentidad()));
        // Normalización de personas, grupo Domicilio (16/10/2026): texto armado al vuelo (D-94:
        // este PDF en particular nunca se cachea -- se re-renderiza en cada reimpresión con los
        // datos ACTUALES de la persona, ver construirPdfAutorizacionBuro).
        String textoDomicilioPersona = servicioClientes.obtenerTextoDomicilio(conexion, persona.getIdPersona());
        parametros.put("CLIENTE_DOMICILIO", textoDomicilioPersona == null ? "" : textoDomicilioPersona);
        parametros.put("CLIENTE_TELEFONO", persona.getTelefonoWhatsapp() == null ? "" : persona.getTelefonoWhatsapp());

        return GeneradorInformesPdf.llenarYExportar("autorizacion_buro.jasper", parametros,
                new net.sf.jasperreports.engine.JREmptyDataSource());
    }

    /** RF03 Parte 2 (pedido de Joel): el consentimiento completo (no solo la fecha de
     * vencimiento, que ya daba obtenerFechaVencimientoConsentimiento) -- para mostrar en ③ quién
     * lo cargó (registradoPor) y cuándo se firmó (fechaConsentimiento). null si no hay ninguno. */
    public ConsentimientoCrediticio obtenerUltimoConsentimiento(Connection conexion, long idPersona)
            throws SQLException {
        return consentimientoDAO.obtenerUltimoPara(conexion, idPersona);
    }

    /**
     * RF03/RF04 (pedido de Joel): "Cargar autorización firmada" en un solo paso -- sube el
     * escaneo (ServicioDocumentos.cargarDocumento, mismo versionado de siempre) Y registra el
     * consentimiento con ese documento como evidencia, SIN pedir ninguna fecha (fecha_consentimiento
     * sigue siendo now(), como ya hacía registrarConsentimiento -- como solo se puede cargar el
     * mismo día que se generó el PDF, "ahora" YA es la fecha de emisión). Antes de tocar nada,
     * exige que la última autorización generada sea de HOY -- si no, rechaza sin guardar ningún
     * archivo (RN nueva, pedido de Joel): "Esta autorización es de otro día. Genere una nueva para
     * que la fecha coincida con la firma." Dos transacciones separadas (la de cargarDocumento, la
     * de registrarConsentimiento), mismo patrón que ya existían como dos pasos manuales -- ahora es
     * un solo clic, pero sigue siendo recuperable si algo falla entre medio (se puede volver a
     * intentar).
     */
    public long cargarAutorizacionFirmada(long idPersona, long idTipoDocumento, String nombreArchivo,
            String mimeType, byte[] contenido, long idUsuarioQueCarga) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            if (!autorizacionGeneradaHoy(conexion, idPersona)) {
                throw new NegocioException("Esta autorización es de otro día. Genere una nueva para que la fecha "
                        + "coincida con la firma.");
            }
        }
        long idDocumento = servicioDocumentos.cargarDocumento(idPersona, idTipoDocumento, nombreArchivo, mimeType,
                contenido, idUsuarioQueCarga, true);
        registrarConsentimiento(idPersona, idDocumento, "Consulta al buró de crédito para evaluación crediticia.",
                idUsuarioQueCarga);
        return idDocumento;
    }

    /**
     * RF03 (pedido de Joel), punto 2: "Cargar informe" del Paso B (RESULTADO_CONSULTA_BURO) -- ruta
     * dedicada aparte de la genérica, mismo motivo que cargarAutorizacionFirmada (hallazgo de un
     * PDF falso cargado como autorización: el tipo reservado no se puede subir por
     * "/documentos/cargar"). A diferencia de la autorización, no registra nada más acá: el informe
     * es solo evidencia -- "Registrar resultado de la consulta" (registrarConsulta) la vincula
     * después, con su propio idDocumentoResultado.
     */
    public long cargarInformeBuro(long idPersona, long idTipoDocumento, String nombreArchivo, String mimeType,
            byte[] contenido, long idUsuarioQueCarga) throws SQLException {
        return servicioDocumentos.cargarDocumento(idPersona, idTipoDocumento, nombreArchivo, mimeType, contenido,
                idUsuarioQueCarga, true);
    }
}
