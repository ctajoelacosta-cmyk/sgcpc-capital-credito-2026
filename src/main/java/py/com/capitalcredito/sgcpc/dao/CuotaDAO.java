package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/** Acceso a cuotas (bd/01_esquema.sql / Doc. 08 Tabla 14, P06, E5). */
public class CuotaDAO {

    /** El plan recién generado siempre tiene estado_pago = PENDIENTE y nada pagado todavía. */
    public void insertarTodas(Connection conexion, long idPlanPago, List<Cuota> cuotas) throws SQLException {
        String sql = "INSERT INTO cuotas (id_plan_pago, numero_cuota, fecha_vencimiento, "
                + "fecha_vencimiento_teorica, capital_programado, interes_programado, tributos_programados, "
                + "total_programado, saldo_capital_posterior, estado_pago) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (Cuota cuota : cuotas) {
                sentencia.setLong(1, idPlanPago);
                sentencia.setInt(2, cuota.getNumeroCuota());
                sentencia.setDate(3, Date.valueOf(cuota.getFechaVencimiento()));
                sentencia.setDate(4, Date.valueOf(cuota.getFechaVencimientoTeorica()));
                sentencia.setBigDecimal(5, cuota.getCapitalProgramado());
                sentencia.setBigDecimal(6, cuota.getInteresProgramado());
                sentencia.setBigDecimal(7, cuota.getTributosProgramados());
                sentencia.setBigDecimal(8, cuota.getTotalProgramado());
                sentencia.setBigDecimal(9, cuota.getSaldoCapitalPosterior());
                sentencia.setString(10, cuota.getEstadoPago().name());
                sentencia.addBatch();
            }
            sentencia.executeBatch();
        }
    }

    private static final String COLUMNAS = "id_cuota, numero_cuota, fecha_vencimiento, fecha_vencimiento_teorica, "
            + "capital_programado, interes_programado, tributos_programados, total_programado, "
            + "saldo_capital_posterior, estado_pago, capital_pagado, interes_pagado, moratorio_pendiente, "
            + "punitorio_pendiente, iva_moratorio_pendiente, iva_punitorio_pendiente, fecha_corte_mora";

    /**
     * Plan de pagos completo, en orden -- para la ficha de formalización, el reporte de plan de
     * pagos y el puente con el motor financiero (E7): incluye id_cuota y las columnas de mora
     * (capital_pagado/interes_pagado/moratorio_pendiente/etc.), no solo lo "programado".
     */
    public List<Cuota> listarPorPlan(Connection conexion, long idPlanPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM cuotas WHERE id_plan_pago = ? ORDER BY numero_cuota";
        List<Cuota> cuotas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPlanPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    cuotas.add(mapear(resultado));
                }
            }
        }
        return cuotas;
    }

    /**
     * Tras aplicar un pago (E7, RN-10): guarda lo que devolvió el motor
     * (ServicioMotorFinanciero.distribuirPago/calcularCancelacionTotal) para la cuota resultante,
     * identificada por su id_cuota (ver Cuota.idCuota, cargado por listarPorPlan).
     */
    public void actualizarTrasPago(Connection conexion, Cuota cuota) throws SQLException {
        String sql = "UPDATE cuotas SET capital_pagado = ?, interes_pagado = ?, moratorio_pendiente = ?, "
                + "punitorio_pendiente = ?, iva_moratorio_pendiente = ?, iva_punitorio_pendiente = ?, "
                + "fecha_corte_mora = ?, estado_pago = ? WHERE id_cuota = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setBigDecimal(1, cuota.getCapitalPagado());
            sentencia.setBigDecimal(2, cuota.getInteresPagado());
            sentencia.setBigDecimal(3, cuota.getMoratorioPendiente());
            sentencia.setBigDecimal(4, cuota.getPunitorioPendiente());
            sentencia.setBigDecimal(5, cuota.getIvaMoratorioPendiente());
            sentencia.setBigDecimal(6, cuota.getIvaPunitorioPendiente());
            sentencia.setDate(7, cuota.getFechaCorteMora() == null ? null : Date.valueOf(cuota.getFechaCorteMora()));
            sentencia.setString(8, cuota.getEstadoPago().name());
            sentencia.setLong(9, cuota.getIdCuota());
            sentencia.executeUpdate();
        }
    }

    /**
     * D-93 (E8): a qué plan pertenece una cuota -- para reconstruir, al reimprimir una factura,
     * el resto de las cuotas de ESE MISMO plan (número de cuota y total, para "cuota N/M"), sin
     * asumir que es el plan vigente (D-91: un plan viejo, superado por un anticipo, queda
     * congelado tal cual estaba, así que sigue siendo la fuente correcta para ese pago viejo).
     */
    /** Bloque 4 (E8, Panel/Cartera): capital pendiente de TODOS los préstamos ACTIVO/EN_MORA (planes
     * vigentes), la "cartera en la calle". */
    public java.math.BigDecimal sumarCapitalPendienteActivos(Connection conexion) throws SQLException {
        String sql = "SELECT COALESCE(SUM(c.capital_programado - c.capital_pagado), 0) "
                + "FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago = c.id_plan_pago "
                + "JOIN prestamos p ON p.id_prestamo = pp.id_prestamo "
                + "WHERE pp.vigente = true AND p.estado_prestamo IN ('ACTIVO', 'EN_MORA') "
                + "AND c.estado_pago <> 'PAGADA'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            resultado.next();
            return resultado.getBigDecimal(1);
        }
    }

    /** Bloque 4 (E8, Cartera): interés programado (nunca exigible, Ley 6624) de las cuotas
     * pendientes de un préstamo -- futuro, no lo que ya venció. */
    public java.math.BigDecimal sumarInteresFuturoPendiente(Connection conexion, long idPlanPago) throws SQLException {
        String sql = "SELECT COALESCE(SUM(interes_programado), 0) FROM cuotas "
                + "WHERE id_plan_pago = ? AND estado_pago <> 'PAGADA'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPlanPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBigDecimal(1);
            }
        }
    }

    public long obtenerIdPlan(Connection conexion, long idCuota) throws SQLException {
        String sql = "SELECT id_plan_pago FROM cuotas WHERE id_cuota = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCuota);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private Cuota mapear(ResultSet resultado) throws SQLException {
        Cuota cuota = new Cuota();
        cuota.setIdCuota(resultado.getLong("id_cuota"));
        cuota.setNumeroCuota(resultado.getInt("numero_cuota"));
        cuota.setFechaVencimiento(resultado.getDate("fecha_vencimiento").toLocalDate());
        cuota.setFechaVencimientoTeorica(resultado.getDate("fecha_vencimiento_teorica").toLocalDate());
        cuota.setCapitalProgramado(resultado.getBigDecimal("capital_programado"));
        cuota.setInteresProgramado(resultado.getBigDecimal("interes_programado"));
        cuota.setTributosProgramados(resultado.getBigDecimal("tributos_programados"));
        cuota.setTotalProgramado(resultado.getBigDecimal("total_programado"));
        cuota.setSaldoCapitalPosterior(resultado.getBigDecimal("saldo_capital_posterior"));
        cuota.setEstadoPago(EstadoPago.valueOf(resultado.getString("estado_pago")));
        cuota.setCapitalPagado(resultado.getBigDecimal("capital_pagado"));
        cuota.setInteresPagado(resultado.getBigDecimal("interes_pagado"));
        cuota.setMoratorioPendiente(resultado.getBigDecimal("moratorio_pendiente"));
        cuota.setPunitorioPendiente(resultado.getBigDecimal("punitorio_pendiente"));
        cuota.setIvaMoratorioPendiente(resultado.getBigDecimal("iva_moratorio_pendiente"));
        cuota.setIvaPunitorioPendiente(resultado.getBigDecimal("iva_punitorio_pendiente"));
        Date fechaCorteMora = resultado.getDate("fecha_corte_mora");
        cuota.setFechaCorteMora(fechaCorteMora == null ? null : fechaCorteMora.toLocalDate());
        return cuota;
    }
}
