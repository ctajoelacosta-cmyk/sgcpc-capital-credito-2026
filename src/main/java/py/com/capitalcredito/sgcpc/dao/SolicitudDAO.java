package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;

/** Acceso a solicitudes_credito (bd/01_esquema.sql / Doc. 08 Tabla 10, P04/P05, E4). */
public class SolicitudDAO {

    private static final String COLUMNAS = "id_solicitud, codigo_solicitud, id_cliente, creado_por, "
            + "fecha_solicitud, monto_solicitado, plazo_solicitado_meses, destino_credito, "
            + "primera_fecha_vencimiento_solicitada, tasa_tna_propuesta, tasa_tea_simulada, cft_simulado, "
            + "cuota_simulada, ingreso_cliente_evaluado, limite_afectacion_aplicado, "
            + "porcentaje_afectacion, estado_solicitud, fecha_envio_aprobacion, monto_aprobado, "
            + "plazo_aprobado_meses, tasa_tna_aprobada, tasa_tea_aprobada, cft_aprobado, "
            + "primera_fecha_vencimiento_aprobada, id_limite_tasa_aplicado, decidido_por, fecha_decision, "
            + "motivo_rechazo, id_motivo_rechazo, cancelado_por, fecha_cancelacion, motivo_cancelacion, formalizada";

    /** Igual que COLUMNAS pero con el alias "s." -- para el JOIN con prestamos (monto_aprobado e
     * id_solicitud existen en las dos tablas, sin alias Postgres tira "column reference ambiguous"). */
    private static final String COLUMNAS_S = "s.id_solicitud, s.codigo_solicitud, s.id_cliente, "
            + "s.creado_por, s.fecha_solicitud, s.monto_solicitado, s.plazo_solicitado_meses, s.destino_credito, "
            + "s.primera_fecha_vencimiento_solicitada, s.tasa_tna_propuesta, s.tasa_tea_simulada, s.cft_simulado, "
            + "s.cuota_simulada, s.ingreso_cliente_evaluado, "
            + "s.limite_afectacion_aplicado, s.porcentaje_afectacion, s.estado_solicitud, "
            + "s.fecha_envio_aprobacion, s.monto_aprobado, s.plazo_aprobado_meses, s.tasa_tna_aprobada, "
            + "s.tasa_tea_aprobada, s.cft_aprobado, s.primera_fecha_vencimiento_aprobada, "
            + "s.id_limite_tasa_aplicado, s.decidido_por, s.fecha_decision, s.motivo_rechazo, s.id_motivo_rechazo, "
            + "s.cancelado_por, s.fecha_cancelacion, s.motivo_cancelacion, s.formalizada";

    /** BORRADOR inicial: ya incluye la primera simulación (las columnas *_simulado son NOT NULL). */
    public long insertar(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        String sql = "INSERT INTO solicitudes_credito (codigo_solicitud, id_cliente, creado_por, fecha_solicitud, "
                + "monto_solicitado, plazo_solicitado_meses, destino_credito, primera_fecha_vencimiento_solicitada, "
                + "tasa_tna_propuesta, tasa_tea_simulada, cft_simulado, cuota_simulada, ingreso_cliente_evaluado, "
                + "limite_afectacion_aplicado, porcentaje_afectacion, estado_solicitud) "
                + "VALUES (?, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, solicitud.getCodigoSolicitud());
            sentencia.setLong(2, solicitud.getIdCliente());
            sentencia.setLong(3, solicitud.getCreadoPor());
            sentencia.setBigDecimal(4, solicitud.getMontoSolicitado());
            sentencia.setInt(5, solicitud.getPlazoSolicitadoMeses());
            sentencia.setString(6, solicitud.getDestinoCredito());
            sentencia.setDate(7, Date.valueOf(solicitud.getPrimeraFechaVencimientoSolicitada()));
            sentencia.setBigDecimal(8, solicitud.getTasaTnaPropuesta());
            sentencia.setBigDecimal(9, solicitud.getTasaTeaSimulada());
            setBigDecimalNullable(sentencia, 10, solicitud.getCftSimulado());
            sentencia.setBigDecimal(11, solicitud.getCuotaSimulada());
            sentencia.setBigDecimal(12, solicitud.getIngresoClienteEvaluado());
            sentencia.setBigDecimal(13, solicitud.getLimiteAfectacionAplicado());
            sentencia.setBigDecimal(14, solicitud.getPorcentajeAfectacion());
            sentencia.setString(15, EstadoSolicitud.BORRADOR.name());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** Condiciones + resultado de la simulación, siempre juntos (D-63, ajuste A). */
    public void actualizarSimulacion(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        String sql = "UPDATE solicitudes_credito SET monto_solicitado = ?, "
                + "plazo_solicitado_meses = ?, destino_credito = ?, primera_fecha_vencimiento_solicitada = ?, "
                + "tasa_tna_propuesta = ?, tasa_tea_simulada = ?, cft_simulado = ?, cuota_simulada = ?, "
                + "ingreso_cliente_evaluado = ?, limite_afectacion_aplicado = ?, "
                + "porcentaje_afectacion = ? WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setBigDecimal(1, solicitud.getMontoSolicitado());
            sentencia.setInt(2, solicitud.getPlazoSolicitadoMeses());
            sentencia.setString(3, solicitud.getDestinoCredito());
            sentencia.setDate(4, Date.valueOf(solicitud.getPrimeraFechaVencimientoSolicitada()));
            sentencia.setBigDecimal(5, solicitud.getTasaTnaPropuesta());
            sentencia.setBigDecimal(6, solicitud.getTasaTeaSimulada());
            setBigDecimalNullable(sentencia, 7, solicitud.getCftSimulado());
            sentencia.setBigDecimal(8, solicitud.getCuotaSimulada());
            sentencia.setBigDecimal(9, solicitud.getIngresoClienteEvaluado());
            sentencia.setBigDecimal(10, solicitud.getLimiteAfectacionAplicado());
            sentencia.setBigDecimal(11, solicitud.getPorcentajeAfectacion());
            sentencia.setLong(12, solicitud.getIdSolicitud());
            sentencia.executeUpdate();
        }
    }

    public void marcarEnviada(Connection conexion, long idSolicitud) throws SQLException {
        String sql = "UPDATE solicitudes_credito SET estado_solicitud = ?, fecha_envio_aprobacion = now() "
                + "WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoSolicitud.PENDIENTE_APROBACION.name());
            sentencia.setLong(2, idSolicitud);
            sentencia.executeUpdate();
        }
    }

    /**
     * Aprobar (estado APROBADA, condiciones aprobadas = las solicitadas, RN-07) o rechazar
     * (motivo obligatorio). RF09/RF10 (pedido de Joel, 11/10/2026): el `WHERE` exige que la
     * solicitud SIGA `PENDIENTE_APROBACION` -- cierra la carrera de dos pestañas (aprobar en
     * una, rechazar en la otra, casi al mismo instante): bajo READ COMMITTED, si la otra
     * transacción ya comiteó un cambio de estado sobre esta misma fila, Postgres espera su lock
     * y vuelve a evaluar el `WHERE` contra el valor ya comiteado -- si ya no es
     * `PENDIENTE_APROBACION`, este `UPDATE` no actualiza nada. Devuelve `true` si sí se decidió
     * (0 filas = alguien más decidió primero; el llamador revisa el resultado y responde según
     * corresponda, sin asumir que la decisión se aplicó).
     */
    public boolean marcarDecidida(Connection conexion, SolicitudCredito solicitud) throws SQLException {
        String sql = "UPDATE solicitudes_credito SET estado_solicitud = ?, monto_aprobado = ?, "
                + "plazo_aprobado_meses = ?, tasa_tna_aprobada = ?, tasa_tea_aprobada = ?, cft_aprobado = ?, "
                + "primera_fecha_vencimiento_aprobada = ?, id_limite_tasa_aplicado = ?, decidido_por = ?, "
                + "fecha_decision = now(), motivo_rechazo = ?, id_motivo_rechazo = ? "
                + "WHERE id_solicitud = ? AND estado_solicitud = 'PENDIENTE_APROBACION'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, solicitud.getEstadoSolicitud().name());
            setBigDecimalNullable(sentencia, 2, solicitud.getMontoAprobado());
            setIntNullable(sentencia, 3, solicitud.getPlazoAprobadoMeses());
            setBigDecimalNullable(sentencia, 4, solicitud.getTasaTnaAprobada());
            setBigDecimalNullable(sentencia, 5, solicitud.getTasaTeaAprobada());
            setBigDecimalNullable(sentencia, 6, solicitud.getCftAprobado());
            if (solicitud.getPrimeraFechaVencimientoAprobada() == null) {
                sentencia.setNull(7, Types.DATE);
            } else {
                sentencia.setDate(7, Date.valueOf(solicitud.getPrimeraFechaVencimientoAprobada()));
            }
            setLongNullable(sentencia, 8, solicitud.getIdLimiteTasaAplicado());
            sentencia.setLong(9, solicitud.getDecididoPor());
            sentencia.setString(10, solicitud.getMotivoRechazo());
            setLongNullable(sentencia, 11, solicitud.getIdMotivoRechazo());
            sentencia.setLong(12, solicitud.getIdSolicitud());
            return sentencia.executeUpdate() > 0;
        }
    }

    public void marcarCancelada(Connection conexion, long idSolicitud, long idUsuarioQueCancela, String motivo)
            throws SQLException {
        String sql = "UPDATE solicitudes_credito SET estado_solicitud = ?, cancelado_por = ?, "
                + "fecha_cancelacion = now(), motivo_cancelacion = ? WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoSolicitud.CANCELADA.name());
            sentencia.setLong(2, idUsuarioQueCancela);
            sentencia.setString(3, motivo);
            sentencia.setLong(4, idSolicitud);
            sentencia.executeUpdate();
        }
    }

    public SolicitudCredito obtenerPorId(Connection conexion, long idSolicitud) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSolicitud);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * RN-02: máximo una solicitud abierta por cliente -- BORRADOR/PENDIENTE_APROBACION, o
     * APROBADA todavía sin formalizar (E5/D-68: una vez formalizada, ya no cuenta como "abierta"
     * aunque la fila siga en estado APROBADA para siempre -- lo que manda es el préstamo, no la
     * solicitud vieja). Mismo criterio que el índice único parcial de bd/02_indices.sql.
     */
    public SolicitudCredito buscarAbiertaPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE id_cliente = ? "
                + "AND (estado_solicitud IN ('BORRADOR', 'PENDIENTE_APROBACION') "
                + "OR (estado_solicitud = 'APROBADA' AND NOT formalizada))";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): TODAS las
     * solicitudes del cliente, cualquiera sea su estado -- a diferencia de buscarAbiertaPorCliente
     * (solo la abierta) o buscarPorClienteConPrestamoAbierto (solo si hay préstamo sin cerrar),
     * para la pestaña "Solicitudes y préstamos" de la ficha. Más reciente primero.
     */
    public List<SolicitudCredito> listarPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE id_cliente = ? "
                + "ORDER BY fecha_solicitud DESC";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                List<SolicitudCredito> solicitudes = new ArrayList<>();
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
                return solicitudes;
            }
        }
    }

    /**
     * 2c (pedido de Joel, 17/10/2026): la solicitud EN EVALUACIÓN de un cliente, si tiene (o
     * null) -- para bloquear la edición de los datos de evaluación. A diferencia de
     * buscarAbiertaPorCliente (que incluye BORRADOR, donde el oficial todavía puede volver a
     * simular libremente), esta deja afuera BORRADOR a propósito: solo PENDIENTE_APROBACION o
     * APROBADA sin formalizar cuentan como "en curso" para este bloqueo.
     */
    public SolicitudCredito buscarEnEvaluacionPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE id_cliente = ? "
                + "AND (estado_solicitud = 'PENDIENTE_APROBACION' "
                + "OR (estado_solicitud = 'APROBADA' AND NOT formalizada))";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** E5/D-68: se pone en TRUE en la misma transacción que crea el préstamo (ServicioFormalizacion). */
    public void marcarFormalizada(Connection conexion, long idSolicitud) throws SQLException {
        String sql = "UPDATE solicitudes_credito SET formalizada = TRUE WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSolicitud);
            sentencia.executeUpdate();
        }
    }

    /**
     * RN-01 (D-61): sin construir todavía PrestamoDAO completo (eso es de E5/E6) -- solo lo que
     * hace falta para esta regla, uniendo con solicitudes_credito porque prestamos no tiene
     * id_cliente propio.
     */
    public boolean existeClienteConPrestamoAbierto(Connection conexion, long idCliente) throws SQLException {
        return buscarPorClienteConPrestamoAbierto(conexion, idCliente) != null;
    }

    /**
     * Igual que existeClienteConPrestamoAbierto, pero devuelve la solicitud (para el código
     * SOL-XXXX del mensaje) en vez de un booleano -- RN-01/RN-02 (D-68).
     */
    public SolicitudCredito buscarPorClienteConPrestamoAbierto(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT " + COLUMNAS_S + " FROM solicitudes_credito s JOIN prestamos p "
                + "ON p.id_solicitud = s.id_solicitud "
                + "WHERE s.id_cliente = ? AND p.estado_prestamo NOT IN ('FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO') "
                + "LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Bandeja P04: solicitudes que creó un oficial, más recientes primero. estadoFiltro null = todas (ajuste 3). */
    public List<SolicitudCredito> listarPorOficial(Connection conexion, long idUsuario, EstadoSolicitud estadoFiltro)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE creado_por = ?"
                + (estadoFiltro == null ? "" : " AND estado_solicitud = ?")
                + " ORDER BY fecha_solicitud DESC";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuario);
            if (estadoFiltro != null) {
                sentencia.setString(2, estadoFiltro.name());
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
            }
        }
        return solicitudes;
    }

    /** Bandeja P06 (E5): solicitudes APROBADA todavía sin formalizar, más antiguas primero. */
    public List<SolicitudCredito> listarAprobadasSinFormalizar(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito "
                + "WHERE estado_solicitud = 'APROBADA' AND NOT formalizada ORDER BY fecha_decision";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                solicitudes.add(mapear(resultado));
            }
        }
        return solicitudes;
    }

    /** Bandeja P05: todas las PENDIENTE_APROBACION, más antiguas primero (orden de atención). */
    public List<SolicitudCredito> listarPendientesAprobacion(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE estado_solicitud = 'PENDIENTE_APROBACION' "
                + "ORDER BY fecha_envio_aprobacion";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                solicitudes.add(mapear(resultado));
            }
        }
        return solicitudes;
    }

    /**
     * Pestaña "Historial" de la bandeja P05 (corrección 4, Joel): solicitudes ya decididas
     * (APROBADA/RECHAZADA/CANCELADA), más recientes primero.
     */
    public List<SolicitudCredito> listarDecididas(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito "
                + "WHERE estado_solicitud IN ('APROBADA', 'RECHAZADA', 'CANCELADA') "
                + "ORDER BY COALESCE(fecha_decision, fecha_cancelacion) DESC";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                solicitudes.add(mapear(resultado));
            }
        }
        return solicitudes;
    }

    /**
     * RF07 (pedido de Joel, 10/10/2026): misma pestaña "Historial" de arriba, pero con filtros de
     * estado y rango de fechas (sobre la fecha de la decisión) -- estado null = las tres
     * (APROBADA/RECHAZADA/CANCELADA); desde/hasta null = sin ese límite. Mismo criterio que
     * listarConFiltros (B2) de más abajo.
     */
    public List<SolicitudCredito> listarDecididasConFiltro(Connection conexion, String estado,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS + " FROM solicitudes_credito "
                + "WHERE estado_solicitud IN ('APROBADA', 'RECHAZADA', 'CANCELADA') ");
        if (estado != null) {
            sql.append("AND estado_solicitud = ? ");
        }
        if (desde != null) {
            sql.append("AND COALESCE(fecha_decision, fecha_cancelacion)::date >= ? ");
        }
        if (hasta != null) {
            sql.append("AND COALESCE(fecha_decision, fecha_cancelacion)::date <= ? ");
        }
        sql.append("ORDER BY COALESCE(fecha_decision, fecha_cancelacion) DESC");
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int indice = 1;
            if (estado != null) {
                sentencia.setString(indice++, estado);
            }
            if (desde != null) {
                sentencia.setDate(indice++, Date.valueOf(desde));
            }
            if (hasta != null) {
                sentencia.setDate(indice++, Date.valueOf(hasta));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
            }
        }
        return solicitudes;
    }

    /** Bloque 7/E10, Tanda D (D-128, informe "Solicitudes" desde-hasta): todas, sin importar el estado. */
    public List<SolicitudCredito> listarPorRangoFecha(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito "
                + "WHERE fecha_solicitud::date BETWEEN ? AND ? ORDER BY fecha_solicitud";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
            }
        }
        return solicitudes;
    }

    /**
     * B2 (Bloque 7/E10, pedido de Joel): listado único de /reportes/solicitudes -- misma consulta
     * para la pantalla, el informe PDF y el CSV (mismo criterio que D-135/PrestamoDAO.
     * listarConFiltros de B1), por fecha_solicitud (fecha en que se creó, igual que
     * listarPorRangoFecha de arriba, al que reemplaza). estado null = todos; idOficial null =
     * todos los oficiales (filtra por creado_por).
     */
    public List<SolicitudCredito> listarConFiltros(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte filtro) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS + " FROM solicitudes_credito WHERE 1 = 1 ");
        if (filtro.getDesde() != null) {
            sql.append("AND fecha_solicitud::date >= ? ");
        }
        if (filtro.getHasta() != null) {
            sql.append("AND fecha_solicitud::date <= ? ");
        }
        if (filtro.getEstado() != null) {
            sql.append("AND estado_solicitud = ? ");
        }
        if (filtro.getIdOficial() != null) {
            sql.append("AND creado_por = ? ");
        }
        sql.append("ORDER BY fecha_solicitud");
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int indice = 1;
            if (filtro.getDesde() != null) {
                sentencia.setDate(indice++, Date.valueOf(filtro.getDesde()));
            }
            if (filtro.getHasta() != null) {
                sentencia.setDate(indice++, Date.valueOf(filtro.getHasta()));
            }
            if (filtro.getEstado() != null) {
                sentencia.setString(indice++, filtro.getEstado());
            }
            if (filtro.getIdOficial() != null) {
                sentencia.setLong(indice++, filtro.getIdOficial());
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
            }
        }
        return solicitudes;
    }

    /**
     * B3 (Bloque 7/E10, pedido de Joel): solicitudes decididas (APROBADA, RECHAZADA o CANCELADA)
     * por fecha de la decisión, para el informe de Aprobaciones y rechazos. RF07 (pedido de Joel,
     * 10/10/2026): se agrega CANCELADA -- desde que RF06 permite que el propio gerente cancele
     * desde la ficha de aprobación, ya no es cierto que "solo el oficial/cliente cancela", así que
     * el informe también la cuenta. Por COALESCE(fecha_decision, fecha_cancelacion) porque las
     * canceladas no tienen fecha_decision.
     */
    public List<SolicitudCredito> listarDecididasPorRangoFecha(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM solicitudes_credito "
                + "WHERE estado_solicitud IN ('APROBADA', 'RECHAZADA', 'CANCELADA') "
                + "AND COALESCE(fecha_decision, fecha_cancelacion)::date BETWEEN ? AND ? "
                + "ORDER BY COALESCE(fecha_decision, fecha_cancelacion)";
        List<SolicitudCredito> solicitudes = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    solicitudes.add(mapear(resultado));
                }
            }
        }
        return solicitudes;
    }

    private void setBigDecimalNullable(PreparedStatement sentencia, int indice, java.math.BigDecimal valor)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.NUMERIC);
        } else {
            sentencia.setBigDecimal(indice, valor);
        }
    }

    private void setLongNullable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }

    private void setIntNullable(PreparedStatement sentencia, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.SMALLINT);
        } else {
            sentencia.setInt(indice, valor);
        }
    }

    private SolicitudCredito mapear(ResultSet resultado) throws SQLException {
        SolicitudCredito solicitud = new SolicitudCredito();
        solicitud.setIdSolicitud(resultado.getLong("id_solicitud"));
        solicitud.setCodigoSolicitud(resultado.getString("codigo_solicitud"));
        solicitud.setIdCliente(resultado.getLong("id_cliente"));
        solicitud.setCreadoPor(resultado.getLong("creado_por"));
        solicitud.setFechaSolicitud(resultado.getTimestamp("fecha_solicitud").toLocalDateTime());
        solicitud.setMontoSolicitado(resultado.getBigDecimal("monto_solicitado"));
        solicitud.setPlazoSolicitadoMeses(resultado.getInt("plazo_solicitado_meses"));
        solicitud.setDestinoCredito(resultado.getString("destino_credito"));
        solicitud.setPrimeraFechaVencimientoSolicitada(resultado.getDate("primera_fecha_vencimiento_solicitada").toLocalDate());
        solicitud.setTasaTnaPropuesta(resultado.getBigDecimal("tasa_tna_propuesta"));
        solicitud.setTasaTeaSimulada(resultado.getBigDecimal("tasa_tea_simulada"));
        solicitud.setCftSimulado(resultado.getBigDecimal("cft_simulado"));
        solicitud.setCuotaSimulada(resultado.getBigDecimal("cuota_simulada"));
        solicitud.setIngresoClienteEvaluado(resultado.getBigDecimal("ingreso_cliente_evaluado"));
        solicitud.setLimiteAfectacionAplicado(resultado.getBigDecimal("limite_afectacion_aplicado"));
        solicitud.setPorcentajeAfectacion(resultado.getBigDecimal("porcentaje_afectacion"));
        solicitud.setEstadoSolicitud(EstadoSolicitud.valueOf(resultado.getString("estado_solicitud")));
        Timestamp fechaEnvio = resultado.getTimestamp("fecha_envio_aprobacion");
        solicitud.setFechaEnvioAprobacion(fechaEnvio == null ? null : fechaEnvio.toLocalDateTime());
        solicitud.setMontoAprobado(resultado.getBigDecimal("monto_aprobado"));
        int plazoAprobado = resultado.getInt("plazo_aprobado_meses");
        solicitud.setPlazoAprobadoMeses(resultado.wasNull() ? null : plazoAprobado);
        solicitud.setTasaTnaAprobada(resultado.getBigDecimal("tasa_tna_aprobada"));
        solicitud.setTasaTeaAprobada(resultado.getBigDecimal("tasa_tea_aprobada"));
        solicitud.setCftAprobado(resultado.getBigDecimal("cft_aprobado"));
        java.sql.Date fechaAprobada = resultado.getDate("primera_fecha_vencimiento_aprobada");
        solicitud.setPrimeraFechaVencimientoAprobada(fechaAprobada == null ? null : fechaAprobada.toLocalDate());
        long idLimiteTasa = resultado.getLong("id_limite_tasa_aplicado");
        solicitud.setIdLimiteTasaAplicado(resultado.wasNull() ? null : idLimiteTasa);
        long decididoPor = resultado.getLong("decidido_por");
        solicitud.setDecididoPor(resultado.wasNull() ? null : decididoPor);
        Timestamp fechaDecision = resultado.getTimestamp("fecha_decision");
        solicitud.setFechaDecision(fechaDecision == null ? null : fechaDecision.toLocalDateTime());
        solicitud.setMotivoRechazo(resultado.getString("motivo_rechazo"));
        long idMotivoRechazo = resultado.getLong("id_motivo_rechazo");
        solicitud.setIdMotivoRechazo(resultado.wasNull() ? null : idMotivoRechazo);
        long canceladoPor = resultado.getLong("cancelado_por");
        solicitud.setCanceladoPor(resultado.wasNull() ? null : canceladoPor);
        Timestamp fechaCancelacion = resultado.getTimestamp("fecha_cancelacion");
        solicitud.setFechaCancelacion(fechaCancelacion == null ? null : fechaCancelacion.toLocalDateTime());
        solicitud.setMotivoCancelacion(resultado.getString("motivo_cancelacion"));
        solicitud.setFormalizada(resultado.getBoolean("formalizada"));
        return solicitud;
    }
}
