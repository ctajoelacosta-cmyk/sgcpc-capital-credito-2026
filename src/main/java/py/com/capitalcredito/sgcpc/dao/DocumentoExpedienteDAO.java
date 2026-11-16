package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * Acceso a documentos_expediente (bd/01_esquema.sql / Doc. 08 Tabla 07). En E3/E4 siempre con
 * idPersona (Doc. 10 §10.6); desde E5 también con idPrestamo (checklist de formalización, P06)
 * -- el CHECK de la tabla ya exige al menos uno de los tres contextos, nunca los dos a la vez
 * en este DAO (cada fila es de una persona O de un préstamo, no de ambos).
 */
public class DocumentoExpedienteDAO {

    public long insertar(Connection conexion, DocumentoExpediente documento) throws SQLException {
        String sql = "INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, id_prestamo, "
                + "version_documento, estado_documento, id_documento_reemplazado, fecha_documento) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, documento.getIdArchivo());
            sentencia.setLong(2, documento.getIdTipoDocumento());
            if (documento.getIdPersona() == null) {
                sentencia.setNull(3, java.sql.Types.BIGINT);
            } else {
                sentencia.setLong(3, documento.getIdPersona());
            }
            if (documento.getIdPrestamo() == null) {
                sentencia.setNull(4, java.sql.Types.BIGINT);
            } else {
                sentencia.setLong(4, documento.getIdPrestamo());
            }
            sentencia.setInt(5, documento.getVersionDocumento());
            sentencia.setString(6, documento.getEstadoDocumento().name());
            if (documento.getIdDocumentoReemplazado() == null) {
                sentencia.setNull(7, java.sql.Types.BIGINT);
            } else {
                sentencia.setLong(7, documento.getIdDocumentoReemplazado());
            }
            sentencia.setDate(8, Date.valueOf(documento.getFechaDocumento()));
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** Al cargar una nueva version: la anterior queda REEMPLAZADO (nunca se borra, REGLAS_DESARROLLO.md §5). */
    public void marcarReemplazado(Connection conexion, long idDocumento) throws SQLException {
        String sql = "UPDATE documentos_expediente SET estado_documento = ? WHERE id_documento = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoDocumento.REEMPLAZADO.name());
            sentencia.setLong(2, idDocumento);
            sentencia.executeUpdate();
        }
    }

    public DocumentoExpediente obtenerPorId(Connection conexion, long idDocumento) throws SQLException {
        String sql = "SELECT d.id_documento, d.id_archivo, d.id_tipo_documento, d.id_persona, d.id_solicitud, "
                + "d.id_prestamo, d.version_documento, d.estado_documento, d.id_documento_reemplazado, "
                + "d.fecha_documento, d.fecha_cierre, d.cerrado_por, d.observacion, "
                + "a.id_archivo AS a_id, a.nombre_archivo, a.mime_type, a.extension, a.tamano_bytes, "
                + "a.hash_sha256, a.origen_archivo, a.fecha_carga, a.cargado_por, "
                + "t.id_tipo_documento AS t_id, t.codigo_tipo, t.nombre_tipo, t.contexto, "
                + "t.obligatorio_desembolso, t.activo "
                + "FROM documentos_expediente d "
                + "JOIN archivos_binarios a ON a.id_archivo = d.id_archivo "
                + "JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento "
                + "WHERE d.id_documento = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idDocumento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapearConDetalle(resultado) : null;
            }
        }
    }

    /**
     * B42 (01/11/2026): los documentos VIGENTES de ESTE expediente cuyo archivo tiene el mismo
     * contenido (mismo SHA-256) que el que se está cargando.
     *
     * <p><b>La búsqueda es acotada a la persona o al préstamo, nunca global</b>: un hash igual en
     * el expediente de otro cliente no es asunto de esta carga, y nombrarlo filtraría datos de un
     * tercero en un aviso de pantalla. Exactamente uno de {@code idPersona}/{@code idPrestamo}
     * viene con valor, según el camino de carga.</p>
     */
    public List<DocumentoExpediente> listarVigentesConMismoHash(Connection conexion, Long idPersona,
            Long idPrestamo, String hashSha256) throws SQLException {
        String sql = "SELECT d.id_documento, d.id_archivo, d.id_tipo_documento, d.id_persona, d.id_solicitud, "
                + "d.id_prestamo, d.version_documento, d.estado_documento, d.id_documento_reemplazado, "
                + "d.fecha_documento, d.fecha_cierre, d.cerrado_por, d.observacion, "
                + "a.id_archivo AS a_id, a.nombre_archivo, a.mime_type, a.extension, a.tamano_bytes, "
                + "a.hash_sha256, a.origen_archivo, a.fecha_carga, a.cargado_por, "
                + "t.id_tipo_documento AS t_id, t.codigo_tipo, t.nombre_tipo, t.contexto, "
                + "t.obligatorio_desembolso, t.activo "
                + "FROM documentos_expediente d "
                + "JOIN archivos_binarios a ON a.id_archivo = d.id_archivo "
                + "JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento "
                + "WHERE d.estado_documento = ? AND a.hash_sha256 = ? "
                + (idPrestamo != null ? "AND d.id_prestamo = ? " : "AND d.id_prestamo IS NULL AND d.id_persona = ? ")
                + "ORDER BY t.nombre_tipo";
        List<DocumentoExpediente> documentos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoDocumento.VIGENTE.name());
            sentencia.setString(2, hashSha256);
            sentencia.setLong(3, idPrestamo != null ? idPrestamo : idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    documentos.add(mapearConDetalle(resultado));
                }
            }
        }
        return documentos;
    }

    public List<DocumentoExpediente> listarPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT d.id_documento, d.id_archivo, d.id_tipo_documento, d.id_persona, d.id_solicitud, "
                + "d.id_prestamo, d.version_documento, d.estado_documento, d.id_documento_reemplazado, "
                + "d.fecha_documento, d.fecha_cierre, d.cerrado_por, d.observacion, "
                + "a.id_archivo AS a_id, a.nombre_archivo, a.mime_type, a.extension, a.tamano_bytes, "
                + "a.hash_sha256, a.origen_archivo, a.fecha_carga, a.cargado_por, "
                + "t.id_tipo_documento AS t_id, t.codigo_tipo, t.nombre_tipo, t.contexto, "
                + "t.obligatorio_desembolso, t.activo "
                + "FROM documentos_expediente d "
                + "JOIN archivos_binarios a ON a.id_archivo = d.id_archivo "
                + "JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento "
                + "WHERE d.id_persona = ? ORDER BY t.nombre_tipo, d.version_documento DESC";
        List<DocumentoExpediente> documentos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    documentos.add(mapearConDetalle(resultado));
                }
            }
        }
        return documentos;
    }

    /**
     * App móvil, ETAPA 2 (13/11/2026): los documentos de VARIAS personas en una sola consulta,
     * agrupados por persona. Solo lectura.
     *
     * <p><b>Por qué hace falta</b>: "Mis pendientes" evalúa el expediente de todos los clientes
     * activos con la regla de verdad ({@code ServicioClientes.construirItemsExpediente}), y esa
     * regla necesita los documentos de cada uno. Pidiéndolos de a uno serían 236 consultas (los
     * clientes activos al 12/11/2026) en vez de una.</p>
     *
     * <p>Devuelve exactamente las mismas filas y el mismo orden que
     * {@link #listarPorPersona(Connection, long)} -- una persona sin documentos simplemente no
     * aparece en el mapa, igual que ahí devolvería una lista vacía.</p>
     *
     * <p><b>Nota de deuda, no de este cambio</b>: este SELECT es la quinta copia del mismo bloque
     * de columnas en esta clase (ya estaba repetido 4 veces antes de la app móvil). Se mantiene la
     * convención del archivo en vez de factorizarlo: sacar una constante tocaría los otros cuatro
     * métodos, todos probados y en uso en el circuito del expediente, y no es el cambio que esta
     * etapa necesita.</p>
     */
    public Map<Long, List<DocumentoExpediente>> listarPorPersonas(Connection conexion, List<Long> idsPersona)
            throws SQLException {
        Map<Long, List<DocumentoExpediente>> porPersona = new HashMap<>();
        if (idsPersona == null || idsPersona.isEmpty()) {
            return porPersona;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT d.id_documento, d.id_archivo, d.id_tipo_documento, d.id_persona, d.id_solicitud, "
                + "d.id_prestamo, d.version_documento, d.estado_documento, d.id_documento_reemplazado, "
                + "d.fecha_documento, d.fecha_cierre, d.cerrado_por, d.observacion, "
                + "a.id_archivo AS a_id, a.nombre_archivo, a.mime_type, a.extension, a.tamano_bytes, "
                + "a.hash_sha256, a.origen_archivo, a.fecha_carga, a.cargado_por, "
                + "t.id_tipo_documento AS t_id, t.codigo_tipo, t.nombre_tipo, t.contexto, "
                + "t.obligatorio_desembolso, t.activo "
                + "FROM documentos_expediente d "
                + "JOIN archivos_binarios a ON a.id_archivo = d.id_archivo "
                + "JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento "
                + "WHERE d.id_persona IN (");
        for (int i = 0; i < idsPersona.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") ORDER BY d.id_persona, t.nombre_tipo, d.version_documento DESC");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < idsPersona.size(); i++) {
                sentencia.setLong(i + 1, idsPersona.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    DocumentoExpediente documento = mapearConDetalle(resultado);
                    porPersona.computeIfAbsent(documento.getIdPersona(), id -> new ArrayList<>())
                            .add(documento);
                }
            }
        }
        return porPersona;
    }

    /** Última versión VIGENTE de un tipo de documento para una persona (para saber si hay que crear o versionar). */
    public DocumentoExpediente obtenerVigentePorPersonaYTipo(Connection conexion, long idPersona, long idTipoDocumento)
            throws SQLException {
        String sql = "SELECT id_documento, id_archivo, id_tipo_documento, id_persona, version_documento, "
                + "estado_documento FROM documentos_expediente "
                + "WHERE id_persona = ? AND id_tipo_documento = ? AND estado_documento = 'VIGENTE'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.setLong(2, idTipoDocumento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                DocumentoExpediente documento = new DocumentoExpediente();
                documento.setIdDocumento(resultado.getLong("id_documento"));
                documento.setIdArchivo(resultado.getLong("id_archivo"));
                documento.setIdTipoDocumento(resultado.getLong("id_tipo_documento"));
                documento.setIdPersona(resultado.getLong("id_persona"));
                documento.setVersionDocumento(resultado.getInt("version_documento"));
                documento.setEstadoDocumento(EstadoDocumento.valueOf(resultado.getString("estado_documento")));
                return documento;
            }
        }
    }

    /** E5: documentos del checklist de formalización de un préstamo (contrato/pagaré generados y firmados). */
    public List<DocumentoExpediente> listarPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT d.id_documento, d.id_archivo, d.id_tipo_documento, d.id_persona, d.id_solicitud, "
                + "d.id_prestamo, d.version_documento, d.estado_documento, d.id_documento_reemplazado, "
                + "d.fecha_documento, d.fecha_cierre, d.cerrado_por, d.observacion, "
                + "a.id_archivo AS a_id, a.nombre_archivo, a.mime_type, a.extension, a.tamano_bytes, "
                + "a.hash_sha256, a.origen_archivo, a.fecha_carga, a.cargado_por, "
                + "t.id_tipo_documento AS t_id, t.codigo_tipo, t.nombre_tipo, t.contexto, "
                + "t.obligatorio_desembolso, t.activo "
                + "FROM documentos_expediente d "
                + "JOIN archivos_binarios a ON a.id_archivo = d.id_archivo "
                + "JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento "
                + "WHERE d.id_prestamo = ? ORDER BY t.nombre_tipo, d.version_documento DESC";
        List<DocumentoExpediente> documentos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    documentos.add(mapearConDetalle(resultado));
                }
            }
        }
        return documentos;
    }

    /** Última versión VIGENTE de un tipo de documento para un préstamo (checklist de formalización, E5). */
    public DocumentoExpediente obtenerVigentePorPrestamoYTipo(Connection conexion, long idPrestamo, long idTipoDocumento)
            throws SQLException {
        // fecha_documento (E6, vigencia de documentos antes de desembolsar): se refresca a
        // Reloj.hoy() cada vez que se regenera el PDF (ver ServicioFormalizacion), así que
        // sirve para saber "hace cuántos días se emitió la última versión vigente".
        String sql = "SELECT id_documento, id_archivo, id_tipo_documento, id_prestamo, version_documento, "
                + "estado_documento, fecha_documento FROM documentos_expediente "
                + "WHERE id_prestamo = ? AND id_tipo_documento = ? AND estado_documento = 'VIGENTE'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            sentencia.setLong(2, idTipoDocumento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                DocumentoExpediente documento = new DocumentoExpediente();
                documento.setIdDocumento(resultado.getLong("id_documento"));
                documento.setIdArchivo(resultado.getLong("id_archivo"));
                documento.setIdTipoDocumento(resultado.getLong("id_tipo_documento"));
                documento.setIdPrestamo(resultado.getLong("id_prestamo"));
                documento.setVersionDocumento(resultado.getInt("version_documento"));
                documento.setEstadoDocumento(EstadoDocumento.valueOf(resultado.getString("estado_documento")));
                Date fechaDocumento = resultado.getDate("fecha_documento");
                documento.setFechaDocumento(fechaDocumento == null ? null : fechaDocumento.toLocalDate());
                return documento;
            }
        }
    }

    private DocumentoExpediente mapearConDetalle(ResultSet resultado) throws SQLException {
        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdDocumento(resultado.getLong("id_documento"));
        documento.setIdArchivo(resultado.getLong("id_archivo"));
        documento.setIdTipoDocumento(resultado.getLong("id_tipo_documento"));
        documento.setIdPersona(resultado.getLong("id_persona"));
        long idSolicitud = resultado.getLong("id_solicitud");
        documento.setIdSolicitud(resultado.wasNull() ? null : idSolicitud);
        long idPrestamo = resultado.getLong("id_prestamo");
        documento.setIdPrestamo(resultado.wasNull() ? null : idPrestamo);
        documento.setVersionDocumento(resultado.getInt("version_documento"));
        documento.setEstadoDocumento(EstadoDocumento.valueOf(resultado.getString("estado_documento")));
        long idReemplazado = resultado.getLong("id_documento_reemplazado");
        documento.setIdDocumentoReemplazado(resultado.wasNull() ? null : idReemplazado);
        documento.setFechaDocumento(resultado.getDate("fecha_documento").toLocalDate());
        Timestamp fechaCierre = resultado.getTimestamp("fecha_cierre");
        documento.setFechaCierre(fechaCierre == null ? null : fechaCierre.toLocalDateTime());
        long cerradoPor = resultado.getLong("cerrado_por");
        documento.setCerradoPor(resultado.wasNull() ? null : cerradoPor);
        documento.setObservacion(resultado.getString("observacion"));

        ArchivoBinario archivo = new ArchivoBinario();
        archivo.setIdArchivo(resultado.getLong("a_id"));
        archivo.setNombreArchivo(resultado.getString("nombre_archivo"));
        archivo.setMimeType(resultado.getString("mime_type"));
        archivo.setExtension(resultado.getString("extension"));
        archivo.setTamanoBytes(resultado.getLong("tamano_bytes"));
        archivo.setHashSha256(resultado.getString("hash_sha256"));
        archivo.setOrigenArchivo(py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo.valueOf(
                resultado.getString("origen_archivo")));
        Timestamp fechaCarga = resultado.getTimestamp("fecha_carga");
        archivo.setFechaCarga(fechaCarga == null ? null : fechaCarga.toLocalDateTime());
        archivo.setCargadoPor(resultado.getLong("cargado_por"));
        documento.setArchivoBinario(archivo);

        TipoDocumento tipo = new TipoDocumento();
        tipo.setIdTipoDocumento(resultado.getLong("t_id"));
        tipo.setCodigoTipo(resultado.getString("codigo_tipo"));
        tipo.setNombreTipo(resultado.getString("nombre_tipo"));
        tipo.setContexto(resultado.getString("contexto"));
        tipo.setObligatorioDesembolso(resultado.getBoolean("obligatorio_desembolso"));
        tipo.setActivo(resultado.getBoolean("activo"));
        documento.setTipoDocumento(tipo);

        return documento;
    }
}
