package py.com.capitalcredito.sgcpc.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoLiquidacionAnticipada;
import py.com.capitalcredito.sgcpc.modelos.LiquidacionAnticipada;

/** Acceso a liquidaciones_anticipadas (bd/01_esquema.sql / RN-11, PO-10, E7). */
public class LiquidacionAnticipadaDAO {

    public long insertar(Connection conexion, LiquidacionAnticipada liquidacion) throws SQLException {
        String sql = "INSERT INTO liquidaciones_anticipadas (id_prestamo, tipo_liquidacion, fecha_calculo, "
                + "saldo_capital_fecha, interes_devengado, tributos_aplicables, interes_moratorio, "
                + "interes_punitorio, monto_total_liquidacion, monto_anticipo_capital, nuevo_saldo_capital, "
                + "estado_liquidacion, calculado_por, fecha_aplicacion, id_archivo_liquidacion, "
                + "numero_constancia, monto_vencidas_impagas, iva_moratorio, iva_punitorio) "
                + "VALUES (?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, liquidacion.getIdPrestamo());
            sentencia.setString(2, liquidacion.getTipoLiquidacion().name());
            sentencia.setBigDecimal(3, liquidacion.getSaldoCapitalFecha());
            sentencia.setBigDecimal(4, liquidacion.getInteresDevengado());
            sentencia.setBigDecimal(5, liquidacion.getTributosAplicables());
            sentencia.setBigDecimal(6, liquidacion.getInteresMoratorio());
            sentencia.setBigDecimal(7, liquidacion.getInteresPunitorio());
            sentencia.setBigDecimal(8, liquidacion.getMontoTotalLiquidacion());
            setBigDecimalNullable(sentencia, 9, liquidacion.getMontoAnticipoCapital());
            setBigDecimalNullable(sentencia, 10, liquidacion.getNuevoSaldoCapital());
            sentencia.setString(11, EstadoLiquidacionAnticipada.APLICADA.name());
            sentencia.setLong(12, liquidacion.getCalculadoPor());
            setLongNullable(sentencia, 13, liquidacion.getIdArchivoLiquidacion());
            sentencia.setString(14, liquidacion.getNumeroConstancia());
            sentencia.setBigDecimal(15, liquidacion.getMontoVencidasImpagas());
            sentencia.setBigDecimal(16, liquidacion.getIvaMoratorio());
            sentencia.setBigDecimal(17, liquidacion.getIvaPunitorio());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    private static final String COLUMNAS = "id_liquidacion, id_prestamo, tipo_liquidacion, fecha_calculo, "
            + "saldo_capital_fecha, interes_devengado, tributos_aplicables, interes_moratorio, interes_punitorio, "
            + "monto_total_liquidacion, monto_anticipo_capital, nuevo_saldo_capital, estado_liquidacion, "
            + "calculado_por, fecha_aplicacion, id_archivo_liquidacion, numero_constancia, "
            + "monto_vencidas_impagas, iva_moratorio, iva_punitorio";

    /** D-94 (E8): para reconstruir la constancia de cancelación al reimprimir. */
    public LiquidacionAnticipada obtenerPorId(Connection conexion, long idLiquidacion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM liquidaciones_anticipadas WHERE id_liquidacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idLiquidacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-94 (E8): las constancias de cancelación (TOTAL) emitidas en pagos de una sesión -- para el arqueo. */
    public java.util.List<LiquidacionAnticipada> listarConstanciasPorSesion(Connection conexion, long idSesionCaja)
            throws SQLException {
        String sql = "SELECT la.id_liquidacion, la.id_prestamo, la.tipo_liquidacion, la.fecha_calculo, "
                + "la.saldo_capital_fecha, la.interes_devengado, la.tributos_aplicables, la.interes_moratorio, "
                + "la.interes_punitorio, la.monto_total_liquidacion, la.monto_anticipo_capital, "
                + "la.nuevo_saldo_capital, la.estado_liquidacion, la.calculado_por, la.fecha_aplicacion, "
                + "la.id_archivo_liquidacion, la.numero_constancia, la.monto_vencidas_impagas, la.iva_moratorio, "
                + "la.iva_punitorio "
                + "FROM liquidaciones_anticipadas la JOIN pagos p ON p.id_liquidacion_anticipada = la.id_liquidacion "
                + "WHERE p.id_sesion_caja = ? AND la.tipo_liquidacion = 'TOTAL' ORDER BY la.id_liquidacion";
        java.util.List<LiquidacionAnticipada> liquidaciones = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    liquidaciones.add(mapear(resultado));
                }
            }
        }
        return liquidaciones;
    }

    /** E9/Bloque 5 (P10, cierre documental): la liquidación TOTAL vigente (no REVERTIDA) más
     * reciente de un préstamo -- para la Constancia de devolución de pagaré, que necesita la
     * fecha y el número de constancia de la cancelación real que finalizó el préstamo. Null si
     * el préstamo se finalizó con el último pago normal, sin pasar por "Cancelar todo". */
    public LiquidacionAnticipada obtenerTotalVigentePorPrestamo(Connection conexion, long idPrestamo)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM liquidaciones_anticipadas "
                + "WHERE id_prestamo = ? AND tipo_liquidacion = 'TOTAL' AND estado_liquidacion <> 'REVERTIDA' "
                + "ORDER BY id_liquidacion DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** E9/Bloque 5: al revertir el pago que aplicó esta liquidación (anticipo o cancelación) --
     * no se borra, queda como registro (RN-13, "nada se borra"), su constancia (si tenía) sigue
     * siendo reimprimible tal cual. */
    public void marcarRevertida(Connection conexion, long idLiquidacion) throws SQLException {
        String sql = "UPDATE liquidaciones_anticipadas SET estado_liquidacion = 'REVERTIDA' WHERE id_liquidacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idLiquidacion);
            sentencia.executeUpdate();
        }
    }

    private LiquidacionAnticipada mapear(ResultSet resultado) throws SQLException {
        LiquidacionAnticipada liquidacion = new LiquidacionAnticipada();
        liquidacion.setIdLiquidacion(resultado.getLong("id_liquidacion"));
        liquidacion.setIdPrestamo(resultado.getLong("id_prestamo"));
        liquidacion.setTipoLiquidacion(py.com.capitalcredito.sgcpc.enumeraciones.TipoLiquidacion
                .valueOf(resultado.getString("tipo_liquidacion")));
        liquidacion.setFechaCalculo(resultado.getTimestamp("fecha_calculo").toLocalDateTime());
        liquidacion.setSaldoCapitalFecha(resultado.getBigDecimal("saldo_capital_fecha"));
        liquidacion.setInteresDevengado(resultado.getBigDecimal("interes_devengado"));
        liquidacion.setTributosAplicables(resultado.getBigDecimal("tributos_aplicables"));
        liquidacion.setInteresMoratorio(resultado.getBigDecimal("interes_moratorio"));
        liquidacion.setInteresPunitorio(resultado.getBigDecimal("interes_punitorio"));
        liquidacion.setMontoTotalLiquidacion(resultado.getBigDecimal("monto_total_liquidacion"));
        liquidacion.setMontoAnticipoCapital(resultado.getBigDecimal("monto_anticipo_capital"));
        liquidacion.setNuevoSaldoCapital(resultado.getBigDecimal("nuevo_saldo_capital"));
        liquidacion.setEstadoLiquidacion(EstadoLiquidacionAnticipada.valueOf(resultado.getString("estado_liquidacion")));
        liquidacion.setCalculadoPor(resultado.getLong("calculado_por"));
        Timestamp fechaAplicacion = resultado.getTimestamp("fecha_aplicacion");
        liquidacion.setFechaAplicacion(fechaAplicacion == null ? null : fechaAplicacion.toLocalDateTime());
        long idArchivo = resultado.getLong("id_archivo_liquidacion");
        liquidacion.setIdArchivoLiquidacion(resultado.wasNull() ? null : idArchivo);
        liquidacion.setNumeroConstancia(resultado.getString("numero_constancia"));
        liquidacion.setMontoVencidasImpagas(resultado.getBigDecimal("monto_vencidas_impagas"));
        liquidacion.setIvaMoratorio(resultado.getBigDecimal("iva_moratorio"));
        liquidacion.setIvaPunitorio(resultado.getBigDecimal("iva_punitorio"));
        return liquidacion;
    }

    private void setBigDecimalNullable(PreparedStatement sentencia, int indice, BigDecimal valor)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.NUMERIC);
        } else {
            sentencia.setBigDecimal(indice, valor);
        }
    }

    private void setLongNullable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }
}
