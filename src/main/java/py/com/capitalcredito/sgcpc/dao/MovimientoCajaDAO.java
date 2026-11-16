package py.com.capitalcredito.sgcpc.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;

/** Acceso a movimientos_caja, el libro único de la caja (bd/01_esquema.sql / Doc. 00 §10.1, P07, E6). */
public class MovimientoCajaDAO {

    private static final String COLUMNAS = "id_movimiento_caja, id_sesion_caja, tipo, monto, signo, "
            + "referencia_tipo, id_referencia, registrado_por, confirmado_por, fecha_hora, observacion";

    public long insertar(Connection conexion, MovimientoCaja movimiento) throws SQLException {
        String sql = "INSERT INTO movimientos_caja (id_sesion_caja, tipo, monto, signo, referencia_tipo, "
                + "id_referencia, registrado_por, confirmado_por, fecha_hora, observacion) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, movimiento.getIdSesionCaja());
            sentencia.setString(2, movimiento.getTipo().name());
            sentencia.setBigDecimal(3, movimiento.getMonto());
            sentencia.setInt(4, movimiento.getSigno());
            sentencia.setString(5, movimiento.getReferenciaTipo());
            sentencia.setLong(6, movimiento.getIdReferencia());
            sentencia.setLong(7, movimiento.getRegistradoPor());
            setLongNullable(sentencia, 8, movimiento.getConfirmadoPor());
            sentencia.setString(9, movimiento.getObservacion());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public List<MovimientoCaja> listarPorSesion(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM movimientos_caja WHERE id_sesion_caja = ? ORDER BY fecha_hora";
        List<MovimientoCaja> movimientos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    movimientos.add(mapear(resultado));
                }
            }
        }
        return movimientos;
    }

    /** Suma firmada (monto * signo) de los movimientos de una sesión; 0 si todavía no hay ninguno. */
    public BigDecimal sumarMovimientos(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto * signo), 0) AS suma FROM movimientos_caja WHERE id_sesion_caja = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBigDecimal("suma");
            }
        }
    }

    private void setLongNullable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }

    private MovimientoCaja mapear(ResultSet resultado) throws SQLException {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setIdMovimientoCaja(resultado.getLong("id_movimiento_caja"));
        movimiento.setIdSesionCaja(resultado.getLong("id_sesion_caja"));
        movimiento.setTipo(TipoMovimientoCaja.valueOf(resultado.getString("tipo")));
        movimiento.setMonto(resultado.getBigDecimal("monto"));
        movimiento.setSigno(resultado.getInt("signo"));
        movimiento.setReferenciaTipo(resultado.getString("referencia_tipo"));
        movimiento.setIdReferencia(resultado.getLong("id_referencia"));
        movimiento.setRegistradoPor(resultado.getLong("registrado_por"));
        long confirmadoPor = resultado.getLong("confirmado_por");
        movimiento.setConfirmadoPor(resultado.wasNull() ? null : confirmadoPor);
        movimiento.setFechaHora(resultado.getTimestamp("fecha_hora").toLocalDateTime());
        movimiento.setObservacion(resultado.getString("observacion"));
        return movimiento;
    }
}
