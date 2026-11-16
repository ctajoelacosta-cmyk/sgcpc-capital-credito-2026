package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio;

/**
 * Acceso a personas_domicilio (normalización de personas, grupo Domicilio, 16/10/2026 --
 * bd/01_esquema.sql). SQL parametrizado, sin ORM, mismo patrón que el resto de los DAO (Doc. 00
 * §15): cada método recibe la Connection, la abre y la transacciona quien llama (el Servicio).
 */
public class PersonaDomicilioDAO {

    public PersonaDomicilio obtenerPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT id_persona, id_ciudad, barrio, direccion_domicilio, referencia_domicilio, "
                + "tipo_vivienda, antiguedad_domicilio_anios FROM personas_domicilio WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * Upsert -- a diferencia de personas_conyuge (opcional) y personas_baja (historial), acá
     * SIEMPRE corresponde una fila (toda persona tiene domicilio): el alta inserta, la edición
     * actualiza, con el mismo método (`INSERT ... ON CONFLICT (id_persona) DO UPDATE`).
     */
    public void guardar(Connection conexion, long idPersona, PersonaDomicilio domicilio) throws SQLException {
        String sql = "INSERT INTO personas_domicilio (id_persona, id_ciudad, barrio, direccion_domicilio, "
                + "referencia_domicilio, tipo_vivienda, antiguedad_domicilio_anios) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id_persona) DO UPDATE SET id_ciudad = EXCLUDED.id_ciudad, "
                + "barrio = EXCLUDED.barrio, direccion_domicilio = EXCLUDED.direccion_domicilio, "
                + "referencia_domicilio = EXCLUDED.referencia_domicilio, tipo_vivienda = EXCLUDED.tipo_vivienda, "
                + "antiguedad_domicilio_anios = EXCLUDED.antiguedad_domicilio_anios";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.setLong(2, domicilio.getIdCiudad());
            sentencia.setString(3, domicilio.getBarrio());
            sentencia.setString(4, domicilio.getDireccionDomicilio());
            sentencia.setString(5, domicilio.getReferenciaDomicilio());
            sentencia.setString(6, domicilio.getTipoVivienda());
            sentencia.setInt(7, domicilio.getAntiguedadDomicilioAnios());
            sentencia.executeUpdate();
        }
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): id_ciudad de un lote de personas
     * de una sola consulta -- para informes/listados que antes leían `persona.getIdCiudad()`
     * directo (`ServicioInformes`, `ControladorClientesServlet`). Vacío (nunca null) si
     * `idsPersona` está vacía.
     */
    public Map<Long, Long> mapaIdCiudadPorPersona(Connection conexion, List<Long> idsPersona) throws SQLException {
        Map<Long, Long> mapa = new HashMap<>();
        if (idsPersona.isEmpty()) {
            return mapa;
        }
        StringBuilder sql = new StringBuilder("SELECT id_persona, id_ciudad FROM personas_domicilio WHERE id_persona IN (");
        for (int i = 0; i < idsPersona.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < idsPersona.size(); i++) {
                sentencia.setLong(i + 1, idsPersona.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    mapa.put(resultado.getLong("id_persona"), resultado.getLong("id_ciudad"));
                }
            }
        }
        return mapa;
    }

    private PersonaDomicilio mapear(ResultSet resultado) throws SQLException {
        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdPersona(resultado.getLong("id_persona"));
        domicilio.setIdCiudad(resultado.getLong("id_ciudad"));
        domicilio.setBarrio(resultado.getString("barrio"));
        domicilio.setDireccionDomicilio(resultado.getString("direccion_domicilio"));
        domicilio.setReferenciaDomicilio(resultado.getString("referencia_domicilio"));
        domicilio.setTipoVivienda(resultado.getString("tipo_vivienda"));
        domicilio.setAntiguedadDomicilioAnios(resultado.getInt("antiguedad_domicilio_anios"));
        return domicilio;
    }
}
