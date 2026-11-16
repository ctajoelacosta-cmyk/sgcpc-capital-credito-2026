package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDesembolso;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;

/** Acceso a desembolsos (bd/01_esquema.sql / Doc. 00 §10.1, P08, E6). */
public class DesembolsoDAO {

    private static final String COLUMNAS = "id_desembolso, identificador_confirmacion, id_prestamo, "
            + "id_sesion_caja, monto_aprobado_snapshot, monto_neto_entregado, "
            + "fecha_hora_desembolso, desembolsado_por, estado_desembolso, id_archivo_comprobante, "
            + "numero_comprobante";

    public long insertar(Connection conexion, Desembolso desembolso) throws SQLException {
        String sql = "INSERT INTO desembolsos (identificador_confirmacion, id_prestamo, id_sesion_caja, "
                + "monto_aprobado_snapshot, monto_neto_entregado, fecha_hora_desembolso, "
                + "desembolsado_por, estado_desembolso, id_archivo_comprobante, numero_comprobante) "
                + "VALUES (?, ?, ?, ?, ?, now(), ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, desembolso.getIdentificadorConfirmacion());
            sentencia.setLong(2, desembolso.getIdPrestamo());
            sentencia.setLong(3, desembolso.getIdSesionCaja());
            sentencia.setBigDecimal(4, desembolso.getMontoAprobadoSnapshot());
            sentencia.setBigDecimal(5, desembolso.getMontoNetoEntregado());
            sentencia.setLong(6, desembolso.getDesembolsadoPor());
            sentencia.setString(7, EstadoDesembolso.CONFIRMADO.name());
            setLongNullable(sentencia, 8, desembolso.getIdArchivoComprobante());
            sentencia.setString(9, desembolso.getNumeroComprobante());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** Idempotencia (§14.3): si ya existe un desembolso con esta clave, es la misma operación repetida. */
    public Desembolso obtenerPorIdentificadorConfirmacion(Connection conexion, String identificadorConfirmacion)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM desembolsos WHERE identificador_confirmacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, identificadorConfirmacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public Desembolso obtenerPorId(Connection conexion, long idDesembolso) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM desembolsos WHERE id_desembolso = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idDesembolso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Índice único parcial ux_desembolsos_un_confirmado_por_prestamo: a lo sumo una fila. */
    public Desembolso obtenerConfirmadoPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM desembolsos WHERE id_prestamo = ? AND estado_desembolso = 'CONFIRMADO'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-94 (E8): los desembolsos CONFIRMADOs de una sesión de caja -- para el arqueo. */
    public java.util.List<Desembolso> listarConfirmadosPorSesion(Connection conexion, long idSesionCaja)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM desembolsos WHERE id_sesion_caja = ? "
                + "AND estado_desembolso = 'CONFIRMADO' ORDER BY id_desembolso";
        java.util.List<Desembolso> desembolsos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    desembolsos.add(mapear(resultado));
                }
            }
        }
        return desembolsos;
    }

    /** Bloque 4 (E8, Resumen mensual): desembolsos CONFIRMADO con fecha dentro de un rango. */
    public java.util.List<Desembolso> listarConfirmadosPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM desembolsos WHERE estado_desembolso = 'CONFIRMADO' "
                + "AND fecha_hora_desembolso::date BETWEEN ? AND ? ORDER BY id_desembolso";
        java.util.List<Desembolso> desembolsos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    desembolsos.add(mapear(resultado));
                }
            }
        }
        return desembolsos;
    }

    private void setLongNullable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }

    private Desembolso mapear(ResultSet resultado) throws SQLException {
        Desembolso desembolso = new Desembolso();
        desembolso.setIdDesembolso(resultado.getLong("id_desembolso"));
        desembolso.setIdentificadorConfirmacion(resultado.getString("identificador_confirmacion"));
        desembolso.setIdPrestamo(resultado.getLong("id_prestamo"));
        desembolso.setIdSesionCaja(resultado.getLong("id_sesion_caja"));
        desembolso.setMontoAprobadoSnapshot(resultado.getBigDecimal("monto_aprobado_snapshot"));
        desembolso.setMontoNetoEntregado(resultado.getBigDecimal("monto_neto_entregado"));
        Timestamp fechaHora = resultado.getTimestamp("fecha_hora_desembolso");
        desembolso.setFechaHoraDesembolso(fechaHora == null ? null : fechaHora.toLocalDateTime());
        desembolso.setDesembolsadoPor(resultado.getLong("desembolsado_por"));
        desembolso.setEstadoDesembolso(EstadoDesembolso.valueOf(resultado.getString("estado_desembolso")));
        long idArchivoComprobante = resultado.getLong("id_archivo_comprobante");
        desembolso.setIdArchivoComprobante(resultado.wasNull() ? null : idArchivoComprobante);
        desembolso.setNumeroComprobante(resultado.getString("numero_comprobante"));
        return desembolso;
    }
}
