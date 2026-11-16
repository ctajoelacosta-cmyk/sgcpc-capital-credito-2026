package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio;

/** Acceso a consentimientos_crediticios (bd/01_esquema.sql / Doc. 08 Tabla 08, RN-24). */
public class ConsentimientoCrediticioDAO {

    public long insertar(Connection conexion, ConsentimientoCrediticio consentimiento) throws SQLException {
        String sql = "INSERT INTO consentimientos_crediticios (id_persona, id_documento_evidencia, finalidad, "
                + "fecha_consentimiento, registrado_por, fecha_registro) VALUES (?, ?, ?, now(), ?, now())";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, consentimiento.getIdPersona());
            sentencia.setLong(2, consentimiento.getIdDocumentoEvidencia());
            sentencia.setString(3, consentimiento.getFinalidad());
            sentencia.setLong(4, consentimiento.getRegistradoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /**
     * Último consentimiento de una persona junto con el estado actual de su
     * documento de evidencia (D-38: "vigente" exige ademas que la evidencia
     * siga VIGENTE, no REEMPLAZADA/ANULADA).
     */
    public ConsentimientoCrediticio obtenerUltimoPara(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT c.id_consentimiento, c.id_persona, c.id_solicitud, c.id_documento_evidencia, "
                + "c.finalidad, c.fecha_consentimiento, c.registrado_por, c.fecha_registro, "
                + "d.estado_documento AS estado_evidencia "
                + "FROM consentimientos_crediticios c "
                + "JOIN documentos_expediente d ON d.id_documento = c.id_documento_evidencia "
                + "WHERE c.id_persona = ? ORDER BY c.fecha_consentimiento DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * App móvil, ETAPA 2 (13/11/2026): de estas personas, cuáles tienen al menos un
     * consentimiento registrado. Una sola consulta, solo lectura.
     *
     * <p>Es exactamente lo que necesita {@code ServicioClientes.construirItemsExpediente}, que
     * recibe un {@code boolean consentimientoBuroRegistrado}: la ficha del sistema web se lo pasa
     * como {@code obtenerUltimoPara(...) != null}, o sea <b>existencia</b>, sin mirar el estado de
     * la evidencia. Esto responde la misma pregunta para muchas personas a la vez, para no hacer
     * una consulta por cliente al armar "Mis pendientes".</p>
     */
    public java.util.Set<Long> idsPersonaConConsentimiento(Connection conexion, java.util.List<Long> idsPersona)
            throws SQLException {
        java.util.Set<Long> conConsentimiento = new java.util.HashSet<>();
        if (idsPersona == null || idsPersona.isEmpty()) {
            return conConsentimiento;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT id_persona FROM consentimientos_crediticios WHERE id_persona IN (");
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
                    conConsentimiento.add(resultado.getLong("id_persona"));
                }
            }
        }
        return conConsentimiento;
    }

    private ConsentimientoCrediticio mapear(ResultSet resultado) throws SQLException {
        ConsentimientoCrediticio consentimiento = new ConsentimientoCrediticio();
        consentimiento.setIdConsentimiento(resultado.getLong("id_consentimiento"));
        consentimiento.setIdPersona(resultado.getLong("id_persona"));
        long idSolicitud = resultado.getLong("id_solicitud");
        consentimiento.setIdSolicitud(resultado.wasNull() ? null : idSolicitud);
        consentimiento.setIdDocumentoEvidencia(resultado.getLong("id_documento_evidencia"));
        consentimiento.setFinalidad(resultado.getString("finalidad"));
        consentimiento.setFechaConsentimiento(resultado.getTimestamp("fecha_consentimiento").toLocalDateTime());
        consentimiento.setRegistradoPor(resultado.getLong("registrado_por"));
        consentimiento.setFechaRegistro(resultado.getTimestamp("fecha_registro").toLocalDateTime());
        consentimiento.setEstadoDocumentoEvidencia(EstadoDocumento.valueOf(resultado.getString("estado_evidencia")));
        return consentimiento;
    }
}
