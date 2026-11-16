package py.com.capitalcredito.sgcpc.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;

/** Acceso a reversiones_pago (bd/01_esquema.sql / Doc. 00 §7/PO-13, E9/Bloque 5). */
public class ReversionPagoDAO {

    private static final String COLUMNAS = "id_reversion_pago, identificador_confirmacion, id_pago, "
            + "motivo_reversion, fecha_hora_reversion, autorizado_por, procesado_por, id_sesion_caja, "
            + "monto_salida_caja, id_archivo_comprobante, estado, solicitado_por, fecha_hora_solicitud, "
            + "motivo_rechazo, factura_en_poder_confirmado";

    /** Solicitud (fase SOLICITADA): solo motivo/solicitado_por/fecha_hora_solicitud van completos --
     * el resto queda NULL hasta que el gerente decida (chk_reversion_pago_consistencia). */
    public long insertarSolicitud(Connection conexion, ReversionPago reversion) throws SQLException {
        String sql = "INSERT INTO reversiones_pago (identificador_confirmacion, id_pago, motivo_reversion, "
                + "estado, solicitado_por, fecha_hora_solicitud) VALUES (?, ?, ?, ?, ?, now())";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, reversion.getIdentificadorConfirmacion());
            sentencia.setLong(2, reversion.getIdPago());
            sentencia.setString(3, reversion.getMotivoReversion());
            sentencia.setString(4, EstadoReversion.SOLICITADA.name());
            sentencia.setLong(5, reversion.getSolicitadoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public ReversionPago obtenerPorId(Connection conexion, long idReversionPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE id_reversion_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idReversionPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** ServicioReversiones.autorizarReversion/rechazarReversion: bloquea la fila (FOR UPDATE,
     * dentro de la transacción del llamador) antes de decidir -- dos clics simultáneos sobre la
     * misma solicitud no pueden ejecutar dos veces la restauración de cuotas ni duplicar el
     * movimiento de caja (mismo criterio que NumeradorDAO.siguienteValor). */
    public ReversionPago obtenerPorIdConLock(Connection conexion, long idReversionPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE id_reversion_pago = ? FOR UPDATE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idReversionPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Idempotencia (§14.3): la solicitud usa su propio identificador_confirmacion. */
    public ReversionPago obtenerPorIdentificadorConfirmacion(Connection conexion, String identificadorConfirmacion)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE identificador_confirmacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, identificadorConfirmacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Un solo reverso por pago (id_pago UNIQUE): si ya hay una fila (en cualquier estado), no se
     * puede solicitar otra -- una SOLICITADA/AUTORIZADA ya existente, o una RECHAZADA que Joel
     * decidió no reintentar con una fila nueva (mismo criterio simple que el resto del sistema:
     * nada se borra, y una reversión rechazada queda como registro, no bloquea para siempre porque
     * el gerente puede haber rechazado por un motivo ya resuelto -- ver ServicioReversiones). */
    public ReversionPago obtenerPorIdPago(Connection conexion, long idPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE id_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Bandeja del gerente (/reversiones): las SOLICITADA, más antiguas primero (FIFO). */
    public List<ReversionPago> listarSolicitadas(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE estado = 'SOLICITADA' "
                + "ORDER BY fecha_hora_solicitud";
        List<ReversionPago> reversiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                reversiones.add(mapear(resultado));
            }
        }
        return reversiones;
    }

    /** Punto 12 (pedido de Joel): pestaña "Historial" de /reversiones -- AUTORIZADA/RECHAZADA,
     * más recientes primero (mismo criterio que SolicitudDAO.listarDecididas). */
    public List<ReversionPago> listarDecididas(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago WHERE estado IN ('AUTORIZADA', 'RECHAZADA') "
                + "ORDER BY COALESCE(fecha_hora_reversion, fecha_hora_solicitud) DESC";
        List<ReversionPago> reversiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                reversiones.add(mapear(resultado));
            }
        }
        return reversiones;
    }

    public void marcarAutorizada(Connection conexion, long idReversionPago, long autorizadoPor, long procesadoPor,
            long idSesionCaja, BigDecimal montoSalidaCaja, Boolean facturaEnPoderConfirmado) throws SQLException {
        String sql = "UPDATE reversiones_pago SET estado = 'AUTORIZADA', autorizado_por = ?, procesado_por = ?, "
                + "id_sesion_caja = ?, monto_salida_caja = ?, fecha_hora_reversion = now(), "
                + "factura_en_poder_confirmado = ? WHERE id_reversion_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, autorizadoPor);
            sentencia.setLong(2, procesadoPor);
            sentencia.setLong(3, idSesionCaja);
            sentencia.setBigDecimal(4, montoSalidaCaja);
            if (facturaEnPoderConfirmado == null) {
                sentencia.setNull(5, Types.BOOLEAN);
            } else {
                sentencia.setBoolean(5, facturaEnPoderConfirmado);
            }
            sentencia.setLong(6, idReversionPago);
            sentencia.executeUpdate();
        }
    }

    public void marcarRechazada(Connection conexion, long idReversionPago, String motivoRechazo) throws SQLException {
        // Punto 4 (RF25, pedido de Joel): fecha_hora_reversion pasa a significar "fecha de la
        // última decisión" (antes solo se llenaba al autorizar) -- así "Fecha de la decisión"
        // en el historial tiene un valor también para un rechazo, no solo para una autorización.
        String sql = "UPDATE reversiones_pago SET estado = 'RECHAZADA', motivo_rechazo = ?, "
                + "fecha_hora_reversion = now() WHERE id_reversion_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, motivoRechazo);
            sentencia.setLong(2, idReversionPago);
            sentencia.executeUpdate();
        }
    }

    /** Punto 1 (RF25, Opción A, pedido de Joel): tras un rechazo, un nuevo intento de solicitar
     * la reversión del MISMO pago actualiza esta misma fila (id_pago es UNIQUE, nunca se inserta
     * una segunda) -- nuevo motivo, nuevo identificador_confirmacion (el de ESTE intento), nueva
     * fecha de solicitud, vuelve a SOLICITADA y se borra el motivo/fecha de la decisión anterior
     * (ya no corresponden a un intento que todavía no se decidió). El historial completo de
     * intentos (cada solicitud, cada decisión) queda en auditoria_operaciones, no acá -- esta
     * tabla solo guarda el estado actual. */
    public void marcarResolicitada(Connection conexion, long idReversionPago, String identificadorConfirmacion,
            String motivoReversion, long solicitadoPor) throws SQLException {
        String sql = "UPDATE reversiones_pago SET estado = 'SOLICITADA', identificador_confirmacion = ?, "
                + "motivo_reversion = ?, solicitado_por = ?, fecha_hora_solicitud = now(), "
                + "motivo_rechazo = NULL, fecha_hora_reversion = NULL WHERE id_reversion_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, identificadorConfirmacion);
            sentencia.setString(2, motivoReversion);
            sentencia.setLong(3, solicitadoPor);
            sentencia.setLong(4, idReversionPago);
            sentencia.executeUpdate();
        }
    }

    /** Bloque 7/E10, Tanda D (D-128, informe "Reversiones" desde-hasta): todas, por la fecha en
     * que se SOLICITARON (no la de autorización, que puede ser null si sigue SOLICITADA/RECHAZADA). */
    public List<ReversionPago> listarPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM reversiones_pago "
                + "WHERE fecha_hora_solicitud::date BETWEEN ? AND ? ORDER BY fecha_hora_solicitud";
        List<ReversionPago> reversiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    reversiones.add(mapear(resultado));
                }
            }
        }
        return reversiones;
    }

    private ReversionPago mapear(ResultSet resultado) throws SQLException {
        ReversionPago reversion = new ReversionPago();
        reversion.setIdReversionPago(resultado.getLong("id_reversion_pago"));
        reversion.setIdentificadorConfirmacion(resultado.getString("identificador_confirmacion"));
        reversion.setIdPago(resultado.getLong("id_pago"));
        reversion.setMotivoReversion(resultado.getString("motivo_reversion"));
        Timestamp fechaHoraReversion = resultado.getTimestamp("fecha_hora_reversion");
        reversion.setFechaHoraReversion(fechaHoraReversion == null ? null : fechaHoraReversion.toLocalDateTime());
        long autorizadoPor = resultado.getLong("autorizado_por");
        reversion.setAutorizadoPor(resultado.wasNull() ? null : autorizadoPor);
        long procesadoPor = resultado.getLong("procesado_por");
        reversion.setProcesadoPor(resultado.wasNull() ? null : procesadoPor);
        long idSesionCaja = resultado.getLong("id_sesion_caja");
        reversion.setIdSesionCaja(resultado.wasNull() ? null : idSesionCaja);
        reversion.setMontoSalidaCaja(resultado.getBigDecimal("monto_salida_caja"));
        long idArchivoComprobante = resultado.getLong("id_archivo_comprobante");
        reversion.setIdArchivoComprobante(resultado.wasNull() ? null : idArchivoComprobante);
        reversion.setEstado(EstadoReversion.valueOf(resultado.getString("estado")));
        reversion.setSolicitadoPor(resultado.getLong("solicitado_por"));
        reversion.setFechaHoraSolicitud(resultado.getTimestamp("fecha_hora_solicitud").toLocalDateTime());
        reversion.setMotivoRechazo(resultado.getString("motivo_rechazo"));
        boolean facturaEnPoder = resultado.getBoolean("factura_en_poder_confirmado");
        reversion.setFacturaEnPoderConfirmado(resultado.wasNull() ? null : facturaEnPoder);
        return reversion;
    }
}
