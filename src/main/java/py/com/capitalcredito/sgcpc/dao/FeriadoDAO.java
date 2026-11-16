package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Feriado;

/** Acceso a feriados (bd/01_esquema.sql / Doc. 00 §10.1). */
public class FeriadoDAO {

    public List<Feriado> listar(Connection conexion) throws SQLException {
        String sql = "SELECT id_feriado, fecha, descripcion, activo FROM feriados ORDER BY fecha";
        List<Feriado> feriados = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                feriados.add(mapear(resultado));
            }
        }
        return feriados;
    }

    public List<java.time.LocalDate> listarFechasActivas(Connection conexion) throws SQLException {
        String sql = "SELECT fecha FROM feriados WHERE activo = TRUE";
        List<java.time.LocalDate> fechas = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                fechas.add(resultado.getDate("fecha").toLocalDate());
            }
        }
        return fechas;
    }

    /** RF11 (pedido de Joel, 11/10/2026): con la descripción, para explicar en pantalla y en la
     * auditoría por qué se corrió una fecha (a diferencia de listarFechasActivas, que solo trae
     * la fecha para el motor). */
    public List<Feriado> listarActivos(Connection conexion) throws SQLException {
        String sql = "SELECT id_feriado, fecha, descripcion, activo FROM feriados WHERE activo = TRUE ORDER BY fecha";
        List<Feriado> feriados = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                feriados.add(mapear(resultado));
            }
        }
        return feriados;
    }

    public long insertar(Connection conexion, Feriado feriado) throws SQLException {
        String sql = "INSERT INTO feriados (fecha, descripcion, activo) VALUES (?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setDate(1, Date.valueOf(feriado.getFecha()));
            sentencia.setString(2, feriado.getDescripcion());
            sentencia.setBoolean(3, feriado.isActivo());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** RF39/E2 (pedido de Joel, 13/10/2026): para que el historial de auditoría pueda decir el
     * estado/fecha/descripción ANTES de cambiar activo -- null si no existe (se rechaza con
     * NegocioException desde el servicio). */
    public Feriado obtenerPorId(Connection conexion, long idFeriado) throws SQLException {
        String sql = "SELECT id_feriado, fecha, descripcion, activo FROM feriados WHERE id_feriado = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idFeriado);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public void actualizarActivo(Connection conexion, long idFeriado, boolean activo) throws SQLException {
        String sql = "UPDATE feriados SET activo = ? WHERE id_feriado = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setBoolean(1, activo);
            sentencia.setLong(2, idFeriado);
            sentencia.executeUpdate();
        }
    }

    private Feriado mapear(ResultSet resultado) throws SQLException {
        Feriado feriado = new Feriado();
        feriado.setIdFeriado(resultado.getLong("id_feriado"));
        feriado.setFecha(resultado.getDate("fecha").toLocalDate());
        feriado.setDescripcion(resultado.getString("descripcion"));
        feriado.setActivo(resultado.getBoolean("activo"));
        return feriado;
    }
}
