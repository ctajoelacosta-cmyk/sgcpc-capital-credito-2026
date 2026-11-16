package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Departamento;

/** Acceso a departamentos (bd/01_esquema.sql, Bloque 7/E10 Tanda A, D-126). Catálogo de solo lectura. */
public class DepartamentoDAO {

    private static final String COLUMNAS = "id_departamento, nombre, activo";

    public List<Departamento> listarActivos(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM departamentos WHERE activo = TRUE ORDER BY nombre";
        List<Departamento> departamentos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                departamentos.add(mapear(resultado));
            }
        }
        return departamentos;
    }

    public Departamento buscarPorId(Connection conexion, long idDepartamento) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM departamentos WHERE id_departamento = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idDepartamento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    private Departamento mapear(ResultSet resultado) throws SQLException {
        Departamento departamento = new Departamento();
        departamento.setIdDepartamento(resultado.getLong("id_departamento"));
        departamento.setNombre(resultado.getString("nombre"));
        departamento.setActivo(resultado.getBoolean("activo"));
        return departamento;
    }
}
