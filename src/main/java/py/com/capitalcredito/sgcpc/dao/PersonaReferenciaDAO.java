package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.PersonaReferencia;

/**
 * Acceso a personas_referencias (normalización de personas, grupo Referencias, 15/10/2026 --
 * bd/01_esquema.sql). SQL parametrizado, sin ORM, mismo patrón que el resto de los DAO (Doc. 00
 * §15): cada método recibe la Connection, la abre y la transacciona quien llama (el Servicio).
 */
public class PersonaReferenciaDAO {

    private static final String COLUMNAS = "id_referencia, id_persona, numero_referencia, nombre, telefono";

    public List<PersonaReferencia> listarPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM personas_referencias WHERE id_persona = ? "
                + "ORDER BY numero_referencia";
        List<PersonaReferencia> referencias = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    referencias.add(mapear(resultado));
                }
            }
        }
        return referencias;
    }

    /**
     * Reemplaza TODAS las referencias de una persona por la lista recibida (DELETE + INSERT, en
     * la transacción del llamador) -- la ficha siempre envía el conjunto completo (hoy referencia
     * 1 y 2), nunca una edición parcial de una sola fila por id, así que no hace falta un UPDATE
     * fila por fila.
     */
    public void reemplazarTodas(Connection conexion, long idPersona, List<PersonaReferencia> referencias)
            throws SQLException {
        try (PreparedStatement eliminar = conexion.prepareStatement(
                "DELETE FROM personas_referencias WHERE id_persona = ?")) {
            eliminar.setLong(1, idPersona);
            eliminar.executeUpdate();
        }
        String sql = "INSERT INTO personas_referencias (id_persona, numero_referencia, nombre, telefono) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (PersonaReferencia referencia : referencias) {
                sentencia.setLong(1, idPersona);
                sentencia.setInt(2, referencia.getNumeroReferencia());
                sentencia.setString(3, referencia.getNombre());
                sentencia.setString(4, referencia.getTelefono());
                sentencia.addBatch();
            }
            sentencia.executeBatch();
        }
    }

    private PersonaReferencia mapear(ResultSet resultado) throws SQLException {
        PersonaReferencia referencia = new PersonaReferencia();
        referencia.setIdReferencia(resultado.getLong("id_referencia"));
        referencia.setIdPersona(resultado.getLong("id_persona"));
        referencia.setNumeroReferencia(resultado.getInt("numero_referencia"));
        referencia.setNombre(resultado.getString("nombre"));
        referencia.setTelefono(resultado.getString("telefono"));
        return referencia;
    }
}
