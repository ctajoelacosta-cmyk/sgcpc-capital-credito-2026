package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * D-94 (E8): soporte genérico para el patrón "generar al primer pedido y guardar" de los 5
 * comprobantes (recibo, factura, constancia, comprobante de desembolso, arqueo). Cada uno vive en
 * su propia tabla, con su propia columna nullable a archivos_binarios (pagos.id_archivo_recibo,
 * facturas.id_archivo, liquidaciones_anticipadas.id_archivo_liquidacion,
 * desembolsos.id_archivo_comprobante, sesiones_caja.id_archivo_cierre -- las 5 ya existían, ninguna
 * es nueva) -- en vez de 5 copias del mismo par de consultas, esta clase las parametriza por
 * nombre de tabla/columna. Esos nombres SIEMPRE los fija el código que llama (nunca un valor que
 * venga de afuera), así que concatenarlos en el SQL es seguro (no hay inyección posible).
 */
public class ArchivoComprobanteDAO {

    /**
     * Bloquea la fila de la operación (FOR UPDATE, dentro de una transacción corta propia -- NUNCA
     * la del cobro/desembolso/cierre) y devuelve el id_archivo ya vinculado, o null si todavía no
     * se generó. Si dos pedidos llegan a la vez, el segundo espera este lock y al despertar ya
     * encuentra vinculado el archivo que generó el primero.
     */
    public Long obtenerIdArchivoConLock(Connection conexion, String tabla, String columnaId, String columnaArchivo,
            long id) throws SQLException {
        String sql = "SELECT " + columnaArchivo + " FROM " + tabla + " WHERE " + columnaId + " = ? FOR UPDATE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                long idArchivo = resultado.getLong(1);
                return resultado.wasNull() ? null : idArchivo;
            }
        }
    }

    /** Vincula el archivo recién generado a la fila de la operación (misma transacción que el lock). */
    public void vincular(Connection conexion, String tabla, String columnaId, String columnaArchivo, long id,
            long idArchivo) throws SQLException {
        String sql = "UPDATE " + tabla + " SET " + columnaArchivo + " = ? WHERE " + columnaId + " = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idArchivo);
            sentencia.setLong(2, id);
            sentencia.executeUpdate();
        }
    }
}
