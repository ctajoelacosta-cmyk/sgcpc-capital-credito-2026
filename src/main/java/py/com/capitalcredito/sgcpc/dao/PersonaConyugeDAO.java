package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import py.com.capitalcredito.sgcpc.modelos.PersonaConyuge;

/**
 * Acceso a personas_conyuge (normalización de personas, grupo Cónyuge, 16/10/2026 --
 * bd/01_esquema.sql). SQL parametrizado, sin ORM, mismo patrón que el resto de los DAO (Doc. 00
 * §15): cada método recibe la Connection, la abre y la transacciona quien llama (el Servicio).
 */
public class PersonaConyugeDAO {

    public PersonaConyuge obtenerPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT id_persona, nombre, cedula, telefono FROM personas_conyuge WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                PersonaConyuge conyuge = new PersonaConyuge();
                conyuge.setIdPersona(resultado.getLong("id_persona"));
                conyuge.setNombre(resultado.getString("nombre"));
                conyuge.setCedula(resultado.getString("cedula"));
                conyuge.setTelefono(resultado.getString("telefono"));
                return conyuge;
            }
        }
    }

    /**
     * Reemplaza la fila de cónyuge de una persona (DELETE + INSERT si corresponde, en la
     * transacción del llamador) -- mismo criterio que PersonaReferenciaDAO.reemplazarTodas: la
     * ficha siempre envía el estado completo, nunca una edición parcial. conyuge == null borra la
     * fila sin insertar ninguna (estado civil que ya no la requiere, o nunca la tuvo).
     */
    public void guardar(Connection conexion, long idPersona, PersonaConyuge conyuge) throws SQLException {
        try (PreparedStatement eliminar = conexion.prepareStatement(
                "DELETE FROM personas_conyuge WHERE id_persona = ?")) {
            eliminar.setLong(1, idPersona);
            eliminar.executeUpdate();
        }
        if (conyuge == null) {
            return;
        }
        String sql = "INSERT INTO personas_conyuge (id_persona, nombre, cedula, telefono) VALUES (?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.setString(2, conyuge.getNombre());
            sentencia.setString(3, conyuge.getCedula());
            sentencia.setString(4, conyuge.getTelefono());
            sentencia.executeUpdate();
        }
    }
}
