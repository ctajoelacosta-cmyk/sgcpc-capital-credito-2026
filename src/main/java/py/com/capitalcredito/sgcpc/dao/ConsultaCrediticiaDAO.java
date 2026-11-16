package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.dto.FilaConsultaBuroCrudo;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;

/** Acceso a consultas_crediticias (bd/01_esquema.sql / Doc. 08 Tabla 09). Resultado registrado a mano (RN-18). */
public class ConsultaCrediticiaDAO {

    public long insertar(Connection conexion, ConsultaCrediticia consulta) throws SQLException {
        String sql = "INSERT INTO consultas_crediticias (id_persona, id_solicitud, id_consentimiento, "
                + "proveedor_buro, fecha_hora_consulta, consultado_por, puntaje_obtenido, "
                + "clasificacion_obtenida, indicador_mora, resumen_resultado, id_documento_resultado) "
                + "VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, consulta.getIdPersona());
            // D-39: id_solicitud es NULL en E3 (todavia no hay solicitudes, ajuste A del plan de E3).
            if (consulta.getIdSolicitud() == null) {
                sentencia.setNull(2, Types.BIGINT);
            } else {
                sentencia.setLong(2, consulta.getIdSolicitud());
            }
            sentencia.setLong(3, consulta.getIdConsentimiento());
            sentencia.setString(4, consulta.getProveedorBuro());
            sentencia.setLong(5, consulta.getConsultadoPor());
            if (consulta.getPuntajeObtenido() == null) {
                sentencia.setNull(6, Types.NUMERIC);
            } else {
                sentencia.setBigDecimal(6, consulta.getPuntajeObtenido());
            }
            sentencia.setString(7, consulta.getClasificacionObtenida());
            if (consulta.getIndicadorMora() == null) {
                sentencia.setNull(8, Types.BOOLEAN);
            } else {
                sentencia.setBoolean(8, consulta.getIndicadorMora());
            }
            sentencia.setString(9, consulta.getResumenResultado());
            if (consulta.getIdDocumentoResultado() == null) {
                sentencia.setNull(10, Types.BIGINT);
            } else {
                sentencia.setLong(10, consulta.getIdDocumentoResultado());
            }
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** E4 (D-59): deja constancia de qué consulta sustentó la evaluación de una solicitud. */
    public void vincularASolicitud(Connection conexion, long idConsulta, long idSolicitud) throws SQLException {
        String sql = "UPDATE consultas_crediticias SET id_solicitud = ? WHERE id_consulta_crediticia = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSolicitud);
            sentencia.setLong(2, idConsulta);
            sentencia.executeUpdate();
        }
    }

    /** RF04 (pedido de Joel, hallazgo de la revisión del 10/10/2026): el historial de la ficha
     * ahora también muestra "Consultado por" -- LEFT JOIN (no INNER) para que una consulta
     * nunca desaparezca del historial si el usuario que la hizo ya no existiera. */
    public List<ConsultaCrediticia> listarPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT cc.id_consulta_crediticia, cc.id_persona, cc.id_solicitud, cc.id_consentimiento, "
                + "cc.proveedor_buro, cc.fecha_hora_consulta, cc.consultado_por, cc.puntaje_obtenido, "
                + "cc.clasificacion_obtenida, cc.indicador_mora, cc.resumen_resultado, cc.id_documento_resultado, "
                + "u.nombres AS usuario_nombres, u.apellidos AS usuario_apellidos "
                + "FROM consultas_crediticias cc LEFT JOIN usuarios u ON u.id_usuario = cc.consultado_por "
                + "WHERE cc.id_persona = ? ORDER BY cc.fecha_hora_consulta DESC";
        List<ConsultaCrediticia> consultas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    ConsultaCrediticia consulta = mapear(resultado);
                    String nombresUsuario = resultado.getString("usuario_nombres");
                    consulta.setNombreConsultadoPor(
                            nombresUsuario == null ? null : nombresUsuario + " " + resultado.getString("usuario_apellidos"));
                    consultas.add(consulta);
                }
            }
        }
        return consultas;
    }

    /** E4 (D-59): las consultas del cliente que quedaron vinculadas a una solicitud. */
    public List<ConsultaCrediticia> listarPorSolicitud(Connection conexion, long idSolicitud) throws SQLException {
        String sql = "SELECT id_consulta_crediticia, id_persona, id_solicitud, id_consentimiento, proveedor_buro, "
                + "fecha_hora_consulta, consultado_por, puntaje_obtenido, clasificacion_obtenida, indicador_mora, "
                + "resumen_resultado, id_documento_resultado FROM consultas_crediticias "
                + "WHERE id_solicitud = ? ORDER BY fecha_hora_consulta DESC";
        List<ConsultaCrediticia> consultas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSolicitud);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    consultas.add(mapear(resultado));
                }
            }
        }
        return consultas;
    }

    /**
     * Informe "Consultas al buró" (C-4.4, pedido de Joel): una fila por consulta, con el cliente,
     * la solicitud asociada (si hay), quién consultó y si la autorización seguía vigente EN ESE
     * MOMENTO -- no hoy. Esto último se calcula con el valor de "buro.dias_vigencia_consentimiento"
     * que regía el día de la consulta (LATERAL contra parametros_sistema, mismo criterio de
     * historial que ParametroDAO.obtenerVigenteAFecha): si no hay un valor registrado para esa
     * fecha (el histórico del parámetro no llega tan atrás), autorizacion_vigente queda NULL --
     * "(sin dato)", nunca "No" por defecto, para no insinuar una irregularidad que no se puede
     * confirmar. No valida el estado del documento de evidencia en ese momento (no queda
     * registrado históricamente en este sistema, solo el estado actual).
     */
    public List<FilaConsultaBuroCrudo> listarParaInforme(Connection conexion, LocalDate desde, LocalDate hasta,
            String clasificacionObtenida) throws SQLException {
        String sql = "SELECT cc.fecha_hora_consulta, p.id_persona, p.nombres, p.apellidos, p.cedula_identidad, "
                + "sc.codigo_solicitud, cc.clasificacion_obtenida, u.nombres AS usuario_nombres, "
                + "u.apellidos AS usuario_apellidos, "
                + "CASE WHEN pv.valor_parametro IS NULL THEN NULL "
                + "ELSE (con.fecha_consentimiento::date + (pv.valor_parametro::int) * INTERVAL '1 day')::date "
                + ">= cc.fecha_hora_consulta::date END AS autorizacion_vigente "
                + "FROM consultas_crediticias cc "
                + "JOIN personas p ON p.id_persona = cc.id_persona "
                + "JOIN usuarios u ON u.id_usuario = cc.consultado_por "
                + "JOIN consentimientos_crediticios con ON con.id_consentimiento = cc.id_consentimiento "
                + "LEFT JOIN solicitudes_credito sc ON sc.id_solicitud = cc.id_solicitud "
                + "LEFT JOIN LATERAL (SELECT valor_parametro FROM parametros_sistema "
                + "WHERE clave_parametro = 'buro.dias_vigencia_consentimiento' "
                + "AND vigente_desde <= cc.fecha_hora_consulta::date "
                + "AND (vigente_hasta IS NULL OR vigente_hasta > cc.fecha_hora_consulta::date) LIMIT 1) pv ON true "
                + "WHERE cc.fecha_hora_consulta::date BETWEEN ? AND ? "
                + "AND (? IS NULL OR COALESCE(cc.clasificacion_obtenida, '') = ?) "
                + "ORDER BY cc.fecha_hora_consulta DESC";
        List<FilaConsultaBuroCrudo> filas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            sentencia.setString(3, clasificacionObtenida);
            sentencia.setString(4, clasificacionObtenida);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    // wasNull() refleja la última columna leída -- hay que capturarla ACA, antes
                    // de leer cualquier otra columna (si no, terminaría reflejando la nulidad de
                    // "usuario_apellidos" en vez de la de "autorizacion_vigente").
                    boolean autorizacionVigente = resultado.getBoolean("autorizacion_vigente");
                    Boolean autorizacionVigenteONulo = resultado.wasNull() ? null : autorizacionVigente;
                    filas.add(new FilaConsultaBuroCrudo(resultado.getTimestamp("fecha_hora_consulta").toLocalDateTime(),
                            resultado.getLong("id_persona"),
                            resultado.getString("nombres") + " " + resultado.getString("apellidos"),
                            resultado.getString("cedula_identidad"), resultado.getString("codigo_solicitud"),
                            resultado.getString("clasificacion_obtenida"),
                            resultado.getString("usuario_nombres") + " " + resultado.getString("usuario_apellidos"),
                            autorizacionVigenteONulo));
                }
            }
        }
        return filas;
    }

    /** Para el filtro "por resultado" de la pantalla (C-4.4): los valores de clasificación que
     * realmente existen en los datos, no una lista fija -- en la práctica hay más que A-E (se ve
     * texto libre cargado antes de que la pantalla ofreciera el <select>). "" representa "sin
     * clasificación" (NULL o vacío). */
    public List<String> listarClasificacionesDistintas(Connection conexion) throws SQLException {
        String sql = "SELECT DISTINCT COALESCE(clasificacion_obtenida, '') AS clasificacion "
                + "FROM consultas_crediticias ORDER BY 1";
        List<String> clasificaciones = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                clasificaciones.add(resultado.getString("clasificacion"));
            }
        }
        return clasificaciones;
    }

    private ConsultaCrediticia mapear(ResultSet resultado) throws SQLException {
        ConsultaCrediticia consulta = new ConsultaCrediticia();
        consulta.setIdConsultaCrediticia(resultado.getLong("id_consulta_crediticia"));
        consulta.setIdPersona(resultado.getLong("id_persona"));
        long idSolicitud = resultado.getLong("id_solicitud");
        consulta.setIdSolicitud(resultado.wasNull() ? null : idSolicitud);
        consulta.setIdConsentimiento(resultado.getLong("id_consentimiento"));
        consulta.setProveedorBuro(resultado.getString("proveedor_buro"));
        consulta.setFechaHoraConsulta(resultado.getTimestamp("fecha_hora_consulta").toLocalDateTime());
        consulta.setConsultadoPor(resultado.getLong("consultado_por"));
        consulta.setPuntajeObtenido(resultado.getBigDecimal("puntaje_obtenido"));
        consulta.setClasificacionObtenida(resultado.getString("clasificacion_obtenida"));
        boolean indicadorMora = resultado.getBoolean("indicador_mora");
        consulta.setIndicadorMora(resultado.wasNull() ? null : indicadorMora);
        consulta.setResumenResultado(resultado.getString("resumen_resultado"));
        long idDocumentoResultado = resultado.getLong("id_documento_resultado");
        consulta.setIdDocumentoResultado(resultado.wasNull() ? null : idDocumentoResultado);
        return consulta;
    }
}
