package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Ciudad;

/** Acceso a ciudades (bd/01_esquema.sql, Bloque 7/E10 Tanda A, D-126). Catálogo de solo lectura. */
public class CiudadDAO {

    private static final String COLUMNAS = "id_ciudad, id_departamento, nombre, activo";

    /** Para el <select> de ciudad en la ficha de cliente, ya filtrado por el departamento elegido. */
    public List<Ciudad> listarActivasPorDepartamento(Connection conexion, long idDepartamento) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM ciudades WHERE id_departamento = ? AND activo = TRUE ORDER BY nombre";
        List<Ciudad> ciudades = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idDepartamento);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ciudades.add(mapear(resultado));
                }
            }
        }
        return ciudades;
    }

    /** Todas, activas, para armar en el navegador el <select> en cascada sin ida y vuelta al servidor. */
    public List<Ciudad> listarActivas(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM ciudades WHERE activo = TRUE ORDER BY nombre";
        List<Ciudad> ciudades = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                ciudades.add(mapear(resultado));
            }
        }
        return ciudades;
    }

    public Ciudad buscarPorId(Connection conexion, long idCiudad) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM ciudades WHERE id_ciudad = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCiudad);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    private Ciudad mapear(ResultSet resultado) throws SQLException {
        Ciudad ciudad = new Ciudad();
        ciudad.setIdCiudad(resultado.getLong("id_ciudad"));
        ciudad.setIdDepartamento(resultado.getLong("id_departamento"));
        ciudad.setNombre(resultado.getString("nombre"));
        ciudad.setActivo(resultado.getBoolean("activo"));
        return ciudad;
    }
}
