package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.MotivoRechazoSolicitud;

/** Acceso a motivos_rechazo_solicitud (bd/01_esquema.sql, Bloque 7/E10 Tanda A, D-126). Catálogo de solo lectura. */
public class MotivoRechazoSolicitudDAO {

    private static final String COLUMNAS = "id_motivo_rechazo, codigo, nombre, requiere_detalle, activo, orden";

    public List<MotivoRechazoSolicitud> listarActivos(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM motivos_rechazo_solicitud WHERE activo = TRUE ORDER BY orden";
        List<MotivoRechazoSolicitud> motivos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                motivos.add(mapear(resultado));
            }
        }
        return motivos;
    }

    public MotivoRechazoSolicitud buscarPorId(Connection conexion, long idMotivoRechazo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM motivos_rechazo_solicitud WHERE id_motivo_rechazo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idMotivoRechazo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    private MotivoRechazoSolicitud mapear(ResultSet resultado) throws SQLException {
        MotivoRechazoSolicitud motivo = new MotivoRechazoSolicitud();
        motivo.setIdMotivoRechazo(resultado.getLong("id_motivo_rechazo"));
        motivo.setCodigo(resultado.getString("codigo"));
        motivo.setNombre(resultado.getString("nombre"));
        motivo.setRequiereDetalle(resultado.getBoolean("requiere_detalle"));
        motivo.setActivo(resultado.getBoolean("activo"));
        motivo.setOrden(resultado.getInt("orden"));
        return motivo;
    }
}
