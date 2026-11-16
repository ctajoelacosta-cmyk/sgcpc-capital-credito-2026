package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import py.com.capitalcredito.sgcpc.modelos.PlanPago;

/** Acceso a planes_pago (bd/01_esquema.sql / Doc. 08 Tabla 13, P06, E5). */
public class PlanPagoDAO {

    /**
     * D-96 (encontrado al construir el recibo de anticipo de E8/D-94): el INSERT no guardaba
     * id_liquidacion_origen aunque el modelo lo trae y ServicioCobranza ya lo seteaba antes de
     * llamar acá -- quedaba siempre NULL en la base, rompiendo el vínculo entre el plan nuevo de
     * un anticipo y la liquidación que lo generó (lo necesita el recibo para mostrar el resumen
     * del plan regenerado).
     */
    public long insertar(Connection conexion, PlanPago plan) throws SQLException {
        String sql = "INSERT INTO planes_pago (id_prestamo, version_plan, origen_plan, vigente, "
                + "id_liquidacion_origen, fecha_generacion, generado_por) VALUES (?, ?, ?, ?, ?, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, plan.getIdPrestamo());
            sentencia.setInt(2, plan.getVersionPlan());
            sentencia.setString(3, plan.getOrigenPlan());
            sentencia.setBoolean(4, plan.isVigente());
            if (plan.getIdLiquidacionOrigen() == null) {
                sentencia.setNull(5, Types.BIGINT);
            } else {
                sentencia.setLong(5, plan.getIdLiquidacionOrigen());
            }
            sentencia.setLong(6, plan.getGeneradoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** E5 (ajuste de Joel, regenerar): la versión anterior queda no vigente al recalcular el plan. */
    public void marcarNoVigente(Connection conexion, long idPlanPago) throws SQLException {
        String sql = "UPDATE planes_pago SET vigente = FALSE WHERE id_plan_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPlanPago);
            sentencia.executeUpdate();
        }
    }

    /** E9/Bloque 5: revertir un anticipo devuelve la versión anterior a vigente (ux_planes_pago_una_vigente_por_prestamo
     * exige que la que se está reemplazando ya haya quedado en FALSE antes de este UPDATE). */
    public void marcarVigente(Connection conexion, long idPlanPago) throws SQLException {
        String sql = "UPDATE planes_pago SET vigente = TRUE WHERE id_plan_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPlanPago);
            sentencia.executeUpdate();
        }
    }

    /** E9/Bloque 5: la versión de plan anterior a la que generó un anticipo (version_plan - 1),
     * para restaurarla como vigente al revertir. */
    public PlanPago obtenerPorPrestamoYVersion(Connection conexion, long idPrestamo, int versionPlan) throws SQLException {
        String sql = "SELECT id_plan_pago, id_prestamo, version_plan, origen_plan, vigente, "
                + "id_liquidacion_origen, fecha_generacion, generado_por FROM planes_pago "
                + "WHERE id_prestamo = ? AND version_plan = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            sentencia.setInt(2, versionPlan);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Versión vigente de un préstamo (ORIGINAL la primera vez; puede cambiar si se regenera, E5, o hay un anticipo). */
    public PlanPago obtenerVigentePorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT id_plan_pago, id_prestamo, version_plan, origen_plan, vigente, "
                + "id_liquidacion_origen, fecha_generacion, generado_por FROM planes_pago "
                + "WHERE id_prestamo = ? AND vigente = TRUE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * D-94 (E8): el plan ORIGINAL de un préstamo -- el que firmó el cliente al desembolsar, nunca
     * uno regenerado por un anticipo posterior. Solo existe uno por préstamo (origen_plan es
     * ORIGINAL una sola vez, en el momento de formalizar), así que no importa si sigue vigente o
     * no: el comprobante de desembolso siempre muestra este, no el vigente de hoy.
     */
    public PlanPago obtenerOriginalPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT id_plan_pago, id_prestamo, version_plan, origen_plan, vigente, "
                + "id_liquidacion_origen, fecha_generacion, generado_por FROM planes_pago "
                + "WHERE id_prestamo = ? AND origen_plan = 'ORIGINAL'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-93/E8: el plan nuevo que un anticipo de capital generó (para el recibo de ese anticipo). */
    public PlanPago obtenerPorLiquidacionOrigen(Connection conexion, long idLiquidacion) throws SQLException {
        String sql = "SELECT id_plan_pago, id_prestamo, version_plan, origen_plan, vigente, "
                + "id_liquidacion_origen, fecha_generacion, generado_por FROM planes_pago "
                + "WHERE id_liquidacion_origen = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idLiquidacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    private PlanPago mapear(ResultSet resultado) throws SQLException {
        PlanPago plan = new PlanPago();
        plan.setIdPlanPago(resultado.getLong("id_plan_pago"));
        plan.setIdPrestamo(resultado.getLong("id_prestamo"));
        plan.setVersionPlan(resultado.getInt("version_plan"));
        plan.setOrigenPlan(resultado.getString("origen_plan"));
        plan.setVigente(resultado.getBoolean("vigente"));
        long idLiquidacion = resultado.getLong("id_liquidacion_origen");
        plan.setIdLiquidacionOrigen(resultado.wasNull() ? null : idLiquidacion);
        plan.setFechaGeneracion(resultado.getTimestamp("fecha_generacion").toLocalDateTime());
        plan.setGeneradoPor(resultado.getLong("generado_por"));
        return plan;
    }
}
