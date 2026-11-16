package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;

/** Acceso a aplicaciones_pago (bd/01_esquema.sql / RN-10, P09, E7). */
public class AplicacionPagoDAO {

    public void insertar(Connection conexion, AplicacionPago aplicacion) throws SQLException {
        String sql = "INSERT INTO aplicaciones_pago (id_pago, tipo_aplicacion, id_cuota, monto_moratorio, "
                + "monto_punitorio, monto_interes, monto_tributos, monto_capital, subtotal_aplicado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, aplicacion.getIdPago());
            sentencia.setString(2, aplicacion.getTipoAplicacion().name());
            if (aplicacion.getIdCuota() == null) {
                sentencia.setNull(3, Types.BIGINT);
            } else {
                sentencia.setLong(3, aplicacion.getIdCuota());
            }
            sentencia.setBigDecimal(4, aplicacion.getMontoMoratorio());
            sentencia.setBigDecimal(5, aplicacion.getMontoPunitorio());
            sentencia.setBigDecimal(6, aplicacion.getMontoInteres());
            sentencia.setBigDecimal(7, aplicacion.getMontoTributos());
            sentencia.setBigDecimal(8, aplicacion.getMontoCapital());
            sentencia.setBigDecimal(9, aplicacion.getSubtotalAplicado());
            sentencia.executeUpdate();
        }
    }

    /**
     * Bloque 4 (E8, Resumen mensual): misma composición que sumarPorSesion, generalizada a un
     * rango de fechas -- pedido de Joel ("intereses cobrados" del mes, basado en la composición
     * del arqueo). Mismos 5 índices: 0 capital, 1 interés, 2 moratorio, 3 punitorio, 4 tributos.
     */
    public java.math.BigDecimal[] sumarPorRangoFechas(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ap.monto_capital),0), COALESCE(SUM(ap.monto_interes),0), "
                + "COALESCE(SUM(ap.monto_moratorio),0), COALESCE(SUM(ap.monto_punitorio),0), "
                + "COALESCE(SUM(ap.monto_tributos),0) "
                + "FROM aplicaciones_pago ap JOIN pagos p ON p.id_pago = ap.id_pago "
                + "WHERE p.estado_pago = 'CONFIRMADO' AND p.fecha_hora_pago::date BETWEEN ? AND ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                java.math.BigDecimal[] sumas = new java.math.BigDecimal[5];
                for (int i = 0; i < 5; i++) {
                    sumas[i] = resultado.getBigDecimal(i + 1);
                }
                return sumas;
            }
        }
    }

    /**
     * D-94 (E8): suma capital/interés/moratorio/punitorio/IVA de todas las aplicaciones de pago de
     * una sesión de caja -- para la "composición de lo cobrado" del arqueo. Índices del arreglo:
     * 0 capital, 1 interés, 2 moratorio, 3 punitorio, 4 tributos (IVA combinado).
     * E9/Bloque 5 (ajuste de Joel, "arqueo sin el cobro revertido"): CONFIRMADO nada más, mismo
     * filtro que sumarPorRangoFechas -- un pago revertido (aunque sea DÍAS después, en otra
     * sesión, decisión 2 de E9: "sin importar la sesión") no debe seguir sumando acá. El resto del
     * arqueo (MONTO_COBROS/CANT_COBROS de ServicioCaja.generarPdfArqueo, armado desde
     * movimientos_caja) sigue mostrando el movimiento COBRO original tal cual ocurrió ese día --
     * es historia de esa sesión, no se reescribe -- y el efecto de la reversión (COMPENSACION)
     * aparece como su propio movimiento en la sesión donde se autorizó, sin tocar ninguna otra.
     */
    public java.math.BigDecimal[] sumarPorSesion(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ap.monto_capital),0), COALESCE(SUM(ap.monto_interes),0), "
                + "COALESCE(SUM(ap.monto_moratorio),0), COALESCE(SUM(ap.monto_punitorio),0), "
                + "COALESCE(SUM(ap.monto_tributos),0) "
                + "FROM aplicaciones_pago ap JOIN pagos p ON p.id_pago = ap.id_pago "
                + "WHERE p.id_sesion_caja = ? AND p.estado_pago = 'CONFIRMADO'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                java.math.BigDecimal[] sumas = new java.math.BigDecimal[5];
                for (int i = 0; i < 5; i++) {
                    sumas[i] = resultado.getBigDecimal(i + 1);
                }
                return sumas;
            }
        }
    }

    /** E9/Bloque 5: fecha_corte_mora restaurada -- la fecha del último pago CONFIRMADO (excluyendo
     * el que se revierte) que todavía toca esta cuota, o null si ninguno la tocó nunca (el motor
     * siempre sobrescribe fecha_corte_mora al valor de CADA pago que toca la cuota, D-77/E9). */
    public java.time.LocalDate obtenerUltimaFechaTocada(Connection conexion, long idCuota, long idPagoExcluir)
            throws SQLException {
        String sql = "SELECT MAX(p.fecha_hora_pago::date) FROM aplicaciones_pago ap "
                + "JOIN pagos p ON p.id_pago = ap.id_pago "
                + "WHERE ap.id_cuota = ? AND ap.id_pago <> ? AND p.estado_pago = 'CONFIRMADO'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCuota);
            sentencia.setLong(2, idPagoExcluir);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                java.sql.Date fecha = resultado.getDate(1);
                return fecha == null ? null : fecha.toLocalDate();
            }
        }
    }

    /**
     * PA-16/D-111 (Bloque 7/E10): capital NO vencido de una cancelación total, reconstruido desde
     * lo ya guardado -- ServicioCobranza.confirmarCancelacionTotal inserta una aplicación por
     * cuota (vencida o no) con su monto_capital real; acá se suma solo el de las cuotas cuya
     * fecha_vencimiento seguía sin llegar el día del cálculo (mismo corte que
     * ServicioMotorFinanciero.calcularCancelacionTotal: "vencida" = fecha_vencimiento antes de esa
     * fecha). Sin tocar el esquema -- ver AVANCE.md D-111 para el porqué (PA-16 mostraba el
     * capital vencido dos veces: una acá y otra ya en "vencidas impagas").
     */
    public java.math.BigDecimal sumarCapitalNoVencidoPorPago(Connection conexion, long idPago,
            java.time.LocalDate fechaCalculo) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ap.monto_capital),0) FROM aplicaciones_pago ap "
                + "JOIN cuotas c ON c.id_cuota = ap.id_cuota "
                + "WHERE ap.id_pago = ? AND ap.tipo_aplicacion = 'CUOTA' AND c.fecha_vencimiento >= ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            sentencia.setDate(2, java.sql.Date.valueOf(fechaCalculo));
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBigDecimal(1);
            }
        }
    }

    /**
     * Punto 9 (pedido de Joel): "pagada el X, recibo Y" en el Plan de pagos del estado de cuenta --
     * el último pago CONFIRMADO que tocó esta cuota (mismo filtro que obtenerUltimaFechaTocada,
     * pero acá también hace falta el id_pago/numero_recibo, no solo la fecha).
     */
    public py.com.capitalcredito.sgcpc.dto.PagoDeCuota obtenerPagoQueTocoLaCuota(Connection conexion, long idCuota)
            throws SQLException {
        String sql = "SELECT p.id_pago, p.numero_recibo, p.fecha_hora_pago FROM aplicaciones_pago ap "
                + "JOIN pagos p ON p.id_pago = ap.id_pago "
                + "WHERE ap.id_cuota = ? AND p.estado_pago = 'CONFIRMADO' "
                + "ORDER BY p.fecha_hora_pago DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCuota);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                return new py.com.capitalcredito.sgcpc.dto.PagoDeCuota(resultado.getLong("id_pago"),
                        resultado.getString("numero_recibo"), resultado.getTimestamp("fecha_hora_pago").toLocalDateTime());
            }
        }
    }

    public List<AplicacionPago> listarPorPago(Connection conexion, long idPago) throws SQLException {
        String sql = "SELECT id_aplicacion_pago, id_pago, tipo_aplicacion, id_cuota, monto_moratorio, "
                + "monto_punitorio, monto_interes, monto_tributos, monto_capital, subtotal_aplicado "
                + "FROM aplicaciones_pago WHERE id_pago = ? ORDER BY id_aplicacion_pago";
        List<AplicacionPago> aplicaciones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    AplicacionPago aplicacion = new AplicacionPago();
                    aplicacion.setIdAplicacionPago(resultado.getLong("id_aplicacion_pago"));
                    aplicacion.setIdPago(resultado.getLong("id_pago"));
                    aplicacion.setTipoAplicacion(TipoAplicacion.valueOf(resultado.getString("tipo_aplicacion")));
                    long idCuota = resultado.getLong("id_cuota");
                    aplicacion.setIdCuota(resultado.wasNull() ? null : idCuota);
                    aplicacion.setMontoMoratorio(resultado.getBigDecimal("monto_moratorio"));
                    aplicacion.setMontoPunitorio(resultado.getBigDecimal("monto_punitorio"));
                    aplicacion.setMontoInteres(resultado.getBigDecimal("monto_interes"));
                    aplicacion.setMontoTributos(resultado.getBigDecimal("monto_tributos"));
                    aplicacion.setMontoCapital(resultado.getBigDecimal("monto_capital"));
                    aplicacion.setSubtotalAplicado(resultado.getBigDecimal("subtotal_aplicado"));
                    aplicaciones.add(aplicacion);
                }
            }
        }
        return aplicaciones;
    }
}
