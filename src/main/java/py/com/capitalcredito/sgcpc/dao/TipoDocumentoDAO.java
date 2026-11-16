package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/** Acceso a tipos_documento (bd/01_esquema.sql / Doc. 08 Tabla 06). Catálogo de solo lectura en E3 (D-37). */
public class TipoDocumentoDAO {

    private static final String COLUMNAS = "id_tipo_documento, codigo_tipo, nombre_tipo, contexto, "
            + "obligatorio_desembolso, activo";

    public List<TipoDocumento> listarActivosPorContexto(Connection conexion, String contexto) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM tipos_documento WHERE contexto = ? AND activo = TRUE "
                + "ORDER BY nombre_tipo";
        List<TipoDocumento> tipos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, contexto);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    tipos.add(mapear(resultado));
                }
            }
        }
        return tipos;
    }

    public TipoDocumento buscarPorId(Connection conexion, long idTipoDocumento) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM tipos_documento WHERE id_tipo_documento = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idTipoDocumento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** E4 (D-63): para resolver CEDULA_IDENTIDAD/COMPROBANTE_INGRESO por código, sin ids fijos en el código. */
    public TipoDocumento obtenerPorCodigo(Connection conexion, String codigoTipo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM tipos_documento WHERE codigo_tipo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, codigoTipo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    private TipoDocumento mapear(ResultSet resultado) throws SQLException {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setIdTipoDocumento(resultado.getLong("id_tipo_documento"));
        tipo.setCodigoTipo(resultado.getString("codigo_tipo"));
        tipo.setNombreTipo(resultado.getString("nombre_tipo"));
        tipo.setContexto(resultado.getString("contexto"));
        tipo.setObligatorioDesembolso(resultado.getBoolean("obligatorio_desembolso"));
        tipo.setActivo(resultado.getBoolean("activo"));
        return tipo;
    }
}
