package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.modelos.Pago;

/** Acceso a pagos (bd/01_esquema.sql / Doc. 00 §10.1/§10.2, P09, E7). */
public class PagoDAO {

    private static final String COLUMNAS = "id_pago, identificador_confirmacion, id_prestamo, id_sesion_caja, "
            + "id_liquidacion_anticipada, numero_recibo, fecha_hora_pago, monto_recibido, monto_vuelto, "
            + "monto_aplicado, estado_pago, registrado_por, id_archivo_recibo, tipo, monto_redondeo";

    public long insertar(Connection conexion, Pago pago) throws SQLException {
        String sql = "INSERT INTO pagos (identificador_confirmacion, id_prestamo, id_sesion_caja, "
                + "id_liquidacion_anticipada, numero_recibo, fecha_hora_pago, monto_recibido, monto_vuelto, "
                + "monto_aplicado, estado_pago, registrado_por, id_archivo_recibo, tipo, monto_redondeo) "
                + "VALUES (?, ?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, pago.getIdentificadorConfirmacion());
            sentencia.setLong(2, pago.getIdPrestamo());
            sentencia.setLong(3, pago.getIdSesionCaja());
            setLongNullable(sentencia, 4, pago.getIdLiquidacionAnticipada());
            sentencia.setString(5, pago.getNumeroRecibo());
            sentencia.setBigDecimal(6, pago.getMontoRecibido());
            sentencia.setBigDecimal(7, pago.getMontoVuelto());
            sentencia.setBigDecimal(8, pago.getMontoAplicado());
            sentencia.setString(9, EstadoConfirmacionPago.CONFIRMADO.name());
            sentencia.setLong(10, pago.getRegistradoPor());
            setLongNullable(sentencia, 11, pago.getIdArchivoRecibo());
            sentencia.setString(12, pago.getTipo().name());
            sentencia.setBigDecimal(13, pago.getMontoRedondeo() == null ? java.math.BigDecimal.ZERO : pago.getMontoRedondeo());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** Idempotencia (§14.3): si ya existe un pago con esta clave, es la misma operación repetida. */
    public Pago obtenerPorIdentificadorConfirmacion(Connection conexion, String identificadorConfirmacion)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE identificador_confirmacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, identificadorConfirmacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public Pago obtenerPorId(Connection conexion, long idPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-94 (E8): el pago (anticipo o cancelación) que generó esta liquidación -- para su comprobante. */
    public Pago obtenerPorLiquidacion(Connection conexion, long idLiquidacion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_liquidacion_anticipada = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idLiquidacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-94 (E8): todos los pagos de una sesión de caja, en orden -- para armar el arqueo. */
    public java.util.List<Pago> listarPorSesion(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_sesion_caja = ? ORDER BY id_pago";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapear(resultado));
                }
            }
        }
        return pagos;
    }

    /** E8 (reimpresión, D-94): todos los pagos confirmados de un préstamo, del más reciente al más
     * viejo -- CONFIRMADO nada más, así "el primero de la lista" siempre es la última operación
     * vigente (ServicioReversiones.esLaUltimaOperacion depende de este filtro: un pago REVERTIDO
     * nunca puede volver a ser "la última operación" de nada). Para el historial que SÍ tiene que
     * mostrar también los REVERTIDO (con su badge), ver listarTodosPorPrestamo. */
    public java.util.List<Pago> listarPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_prestamo = ? AND estado_pago = 'CONFIRMADO' "
                + "ORDER BY fecha_hora_pago DESC, id_pago DESC";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapear(resultado));
                }
            }
        }
        return pagos;
    }

    /** Bloque 7/E10, Tanda D (D-128, informe "Cobros" desde-hasta): pagos CONFIRMADO en el rango,
     * de todos los préstamos -- mismo criterio "CONFIRMADO nada más" que listarPorPrestamo. */
    public java.util.List<Pago> listarConfirmadosPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE estado_pago = 'CONFIRMADO' "
                + "AND fecha_hora_pago::date BETWEEN ? AND ? ORDER BY fecha_hora_pago";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapear(resultado));
                }
            }
        }
        return pagos;
    }

    /** B4 (Bloque 7/E10, pedido de Joel): TODOS los pagos del rango, CONFIRMADO y REVERTIDO --
     * el informe de Cobros tiene que seguir mostrando (con la marca "Revertido") un cobro que se
     * revirtió, nunca hacerlo desaparecer (mismo criterio que listarTodosPorPrestamo), aunque no
     * sume a los totales (eso lo filtra calcularTotalesCobros, sobre las filas ya armadas). */
    public java.util.List<Pago> listarTodosPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE fecha_hora_pago::date BETWEEN ? AND ? "
                + "ORDER BY fecha_hora_pago";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapear(resultado));
                }
            }
        }
        return pagos;
    }

    /** E9/Bloque 5 (RN-13, "nada se borra"): todos los pagos de un préstamo SIN filtrar por
     * estado, del más reciente al más viejo -- para el historial de cobros, que tiene que seguir
     * mostrando (con el badge REVERTIDO) un pago ya revertido, nunca hacerlo desaparecer. */
    public java.util.List<Pago> listarTodosPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM pagos WHERE id_prestamo = ? "
                + "ORDER BY fecha_hora_pago DESC, id_pago DESC";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    pagos.add(mapear(resultado));
                }
            }
        }
        return pagos;
    }

    /** E9/Bloque 5: al autorizar una reversión -- el pago nunca se borra, solo cambia de estado. */
    public void marcarRevertido(Connection conexion, long idPago) throws SQLException {
        String sql = "UPDATE pagos SET estado_pago = 'REVERTIDO' WHERE id_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            sentencia.executeUpdate();
        }
    }

    /**
     * Bloque 4 (E8, decisión 1.b): suma de lo aplicado en un préstamo entre dos instantes
     * (inclusive), para ver si una promesa se cumplió por monto aunque el préstamo siga con
     * alguna otra cuota vencida distinta de la que motivó la promesa.
     *
     * D-117 (punto 18, pedido de Joel): `idPagoExcluido` deja afuera de la suma el propio pago
     * que generó la promesa -- now() de PostgreSQL no avanza dentro de una transacción, así que
     * ese pago y la gestión que crea quedan con el MISMO fecha_hora, y sin esta exclusión caían
     * siempre dentro de la ventana (borde inferior inclusivo): la promesa se autocumplía en el
     * mismo acto en que se creaba, sin que ningún pago posterior real la hubiera cumplido todavía
     * (encontrado con el caso real de Rosa Cáceres: la columna "Promesa" de /mora quedaba vacía).
     * `null` no excluye nada -- las gestiones manuales (LLAMADA/MENSAJE/VISITA_LOCAL) no tienen
     * pago asociado, mismo comportamiento que antes.
     */
    public java.math.BigDecimal sumarAplicadoEnVentana(Connection conexion, long idPrestamo,
            java.time.LocalDateTime desde, java.time.LocalDateTime hasta, Long idPagoExcluido) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto_aplicado), 0) FROM pagos "
                + "WHERE id_prestamo = ? AND estado_pago = 'CONFIRMADO' AND fecha_hora_pago BETWEEN ? AND ? "
                + "AND id_pago IS DISTINCT FROM ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            sentencia.setTimestamp(2, Timestamp.valueOf(desde));
            sentencia.setTimestamp(3, Timestamp.valueOf(hasta));
            if (idPagoExcluido == null) {
                sentencia.setNull(4, Types.BIGINT);
            } else {
                sentencia.setLong(4, idPagoExcluido);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBigDecimal(1);
            }
        }
    }

    /** Bloque 4 (E8): "cobrado hoy/semana/mes" del panel y el resumen mensual -- suma de lo
     * aplicado en un rango de fechas, sin filtrar por préstamo. */
    public java.math.BigDecimal sumarAplicadoEntreFechas(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto_aplicado), 0) FROM pagos "
                + "WHERE estado_pago = 'CONFIRMADO' AND fecha_hora_pago::date BETWEEN ? AND ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBigDecimal(1);
            }
        }
    }

    /** Bloque 4 (E8, Panel): cantidad de cobros confirmados en un rango de fechas. */
    public int contarConfirmadosEntreFechas(Connection conexion, java.time.LocalDate desde, java.time.LocalDate hasta)
            throws SQLException {
        String sql = "SELECT count(*) FROM pagos WHERE estado_pago = 'CONFIRMADO' AND fecha_hora_pago::date BETWEEN ? AND ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
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

    private Pago mapear(ResultSet resultado) throws SQLException {
        Pago pago = new Pago();
        pago.setIdPago(resultado.getLong("id_pago"));
        pago.setIdentificadorConfirmacion(resultado.getString("identificador_confirmacion"));
        pago.setIdPrestamo(resultado.getLong("id_prestamo"));
        pago.setIdSesionCaja(resultado.getLong("id_sesion_caja"));
        long idLiquidacion = resultado.getLong("id_liquidacion_anticipada");
        pago.setIdLiquidacionAnticipada(resultado.wasNull() ? null : idLiquidacion);
        pago.setNumeroRecibo(resultado.getString("numero_recibo"));
        Timestamp fechaHora = resultado.getTimestamp("fecha_hora_pago");
        pago.setFechaHoraPago(fechaHora == null ? null : fechaHora.toLocalDateTime());
        pago.setMontoRecibido(resultado.getBigDecimal("monto_recibido"));
        pago.setMontoVuelto(resultado.getBigDecimal("monto_vuelto"));
        pago.setMontoAplicado(resultado.getBigDecimal("monto_aplicado"));
        pago.setEstadoPago(EstadoConfirmacionPago.valueOf(resultado.getString("estado_pago")));
        pago.setRegistradoPor(resultado.getLong("registrado_por"));
        long idArchivoRecibo = resultado.getLong("id_archivo_recibo");
        pago.setIdArchivoRecibo(resultado.wasNull() ? null : idArchivoRecibo);
        pago.setTipo(TipoPago.valueOf(resultado.getString("tipo")));
        pago.setMontoRedondeo(resultado.getBigDecimal("monto_redondeo"));
        return pago;
    }
}
