package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a numeradores (bd/01_esquema.sql / Doc. 00 §10: "Numeración
 * correlativa con bloqueo de fila"). Primer uso: codigo_solicitud (E4,
 * D-64); las claves FACTURA/OPERACION/RECIBO quedan para las etapas que
 * las necesiten.
 */
public class NumeradorDAO {

    /**
     * Bloquea la fila (clave, anio) con SELECT ... FOR UPDATE dentro de la
     * transaccion del llamador, la crea en 0 si no existe todavia, suma 1 y
     * devuelve el nuevo valor. Debe llamarse con conexion.setAutoCommit(false)
     * ya activo (el bloqueo se libera recien al commit/rollback).
     */
    public long siguienteValor(Connection conexion, String clave, int anio) throws SQLException {
        String sqlSeleccionar = "SELECT ultimo_valor FROM numeradores WHERE clave = ? AND anio = ? FOR UPDATE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sqlSeleccionar)) {
            sentencia.setString(1, clave);
            sentencia.setInt(2, anio);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    insertarFila(conexion, clave, anio);
                    return actualizarYDevolver(conexion, clave, anio);
                }
            }
        }
        return actualizarYDevolver(conexion, clave, anio);
    }

    /**
     * D-89: lectura simple (sin `FOR UPDATE`, no consume ni bloquea nada) del último valor
     * emitido para (clave, año) -- 0 si todavía no se generó ninguno. Para chequeos previos a
     * `siguienteValor` (ej. "timbrado agotado": ¿el próximo número superaría el límite
     * autorizado?) que deben decidir ANTES de tomar el número real.
     */
    public long obtenerValorActual(Connection conexion, String clave, int anio) throws SQLException {
        String sql = "SELECT ultimo_valor FROM numeradores WHERE clave = ? AND anio = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            sentencia.setInt(2, anio);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? resultado.getLong(1) : 0L;
            }
        }
    }

    private void insertarFila(Connection conexion, String clave, int anio) throws SQLException {
        String sql = "INSERT INTO numeradores (clave, anio, ultimo_valor) VALUES (?, ?, 0) "
                + "ON CONFLICT (clave, anio) DO NOTHING";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            sentencia.setInt(2, anio);
            sentencia.executeUpdate();
        }
    }

    private long actualizarYDevolver(Connection conexion, String clave, int anio) throws SQLException {
        String sql = "UPDATE numeradores SET ultimo_valor = ultimo_valor + 1 WHERE clave = ? AND anio = ? "
                + "RETURNING ultimo_valor";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            sentencia.setInt(2, anio);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }
}
