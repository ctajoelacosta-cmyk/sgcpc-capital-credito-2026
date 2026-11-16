package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;

/**
 * Acceso a la tabla archivos_binarios (bd/01_esquema.sql / Doc. 08 Tabla 05,
 * REGLAS_DESARROLLO.md §4: el contenido vive solo en PostgreSQL). El contenido (byte[])
 * se maneja aparte de los metadatos: obtenerMetadatos no lo trae, solo
 * obtenerContenido lo hace, para no cargar binarios en cada listado.
 */
public class ArchivoBinarioDAO {

    public long insertar(Connection conexion, ArchivoBinario archivo, byte[] contenido) throws SQLException {
        String sql = "INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, "
                + "contenido, hash_sha256, origen_archivo, fecha_carga, cargado_por) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, archivo.getNombreArchivo());
            sentencia.setString(2, archivo.getMimeType());
            sentencia.setString(3, archivo.getExtension());
            sentencia.setLong(4, archivo.getTamanoBytes());
            sentencia.setBytes(5, contenido);
            sentencia.setString(6, archivo.getHashSha256());
            sentencia.setString(7, archivo.getOrigenArchivo().name());
            sentencia.setLong(8, archivo.getCargadoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public ArchivoBinario obtenerMetadatos(Connection conexion, long idArchivo) throws SQLException {
        String sql = "SELECT id_archivo, nombre_archivo, mime_type, extension, tamano_bytes, hash_sha256, "
                + "origen_archivo, fecha_carga, cargado_por FROM archivos_binarios WHERE id_archivo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idArchivo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapearMetadatos(resultado) : null;
            }
        }
    }

    /** Se pide aparte de los metadatos: solo cuando se va a descargar el archivo. */
    public byte[] obtenerContenido(Connection conexion, long idArchivo) throws SQLException {
        String sql = "SELECT contenido FROM archivos_binarios WHERE id_archivo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idArchivo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? resultado.getBytes("contenido") : null;
            }
        }
    }

    private ArchivoBinario mapearMetadatos(ResultSet resultado) throws SQLException {
        ArchivoBinario archivo = new ArchivoBinario();
        archivo.setIdArchivo(resultado.getLong("id_archivo"));
        archivo.setNombreArchivo(resultado.getString("nombre_archivo"));
        archivo.setMimeType(resultado.getString("mime_type"));
        archivo.setExtension(resultado.getString("extension"));
        archivo.setTamanoBytes(resultado.getLong("tamano_bytes"));
        archivo.setHashSha256(resultado.getString("hash_sha256"));
        archivo.setOrigenArchivo(OrigenArchivo.valueOf(resultado.getString("origen_archivo")));
        Timestamp fechaCarga = resultado.getTimestamp("fecha_carga");
        archivo.setFechaCarga(fechaCarga == null ? null : fechaCarga.toLocalDateTime());
        archivo.setCargadoPor(resultado.getLong("cargado_por"));
        return archivo;
    }
}
