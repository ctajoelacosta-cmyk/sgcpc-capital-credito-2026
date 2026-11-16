package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import py.com.capitalcredito.sgcpc.modelos.PersonaBaja;

/**
 * Acceso a personas_baja (normalización de personas, grupo Baja, 16/10/2026 --
 * bd/01_esquema.sql). SQL parametrizado, sin ORM, mismo patrón que el resto de los DAO (Doc. 00
 * §15): cada método recibe la Connection, la abre y la transacciona quien llama (el Servicio).
 */
public class PersonaBajaDAO {

    public PersonaBaja obtenerPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT id_persona, motivo_baja, detalle_baja, fecha_baja, dado_de_baja_por "
                + "FROM personas_baja WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                PersonaBaja baja = new PersonaBaja();
                baja.setIdPersona(resultado.getLong("id_persona"));
                baja.setMotivoBaja(resultado.getString("motivo_baja"));
                baja.setDetalleBaja(resultado.getString("detalle_baja"));
                java.sql.Timestamp fechaBaja = resultado.getTimestamp("fecha_baja");
                baja.setFechaBaja(fechaBaja == null ? null : fechaBaja.toLocalDateTime());
                baja.setDadoDeBajaPor(resultado.getLong("dado_de_baja_por"));
                return baja;
            }
        }
    }

    /**
     * Upsert (nunca DELETE, decisión de Joel): si la persona ya tiene una fila de una baja
     * anterior (dada de baja, reactivada, y dada de baja otra vez), la sobreescribe en vez de
     * duplicarla -- siempre una sola fila por persona, con los datos de la ÚLTIMA baja.
     * ServicioClientes.reactivar nunca llama a este DAO: la fila queda intacta como historial.
     */
    public void marcarBaja(Connection conexion, long idPersona, String motivoBaja, String detalleBaja,
            long idUsuarioGerente) throws SQLException {
        String sql = "INSERT INTO personas_baja (id_persona, motivo_baja, detalle_baja, fecha_baja, dado_de_baja_por) "
                + "VALUES (?, ?, ?, now(), ?) "
                + "ON CONFLICT (id_persona) DO UPDATE SET motivo_baja = EXCLUDED.motivo_baja, "
                + "detalle_baja = EXCLUDED.detalle_baja, fecha_baja = EXCLUDED.fecha_baja, "
                + "dado_de_baja_por = EXCLUDED.dado_de_baja_por";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.setString(2, motivoBaja);
            sentencia.setString(3, detalleBaja);
            sentencia.setLong(4, idUsuarioGerente);
            sentencia.executeUpdate();
        }
    }
}
