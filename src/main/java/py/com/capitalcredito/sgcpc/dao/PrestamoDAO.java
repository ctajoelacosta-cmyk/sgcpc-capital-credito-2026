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
import py.com.capitalcredito.sgcpc.dto.FiltroPrestamos;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;

/** Acceso a prestamos (bd/01_esquema.sql / Doc. 08 Tabla 11, P06, E5). */
public class PrestamoDAO {

    private static final String COLUMNAS = "id_prestamo, identificador_operacion, id_solicitud, numero_contrato, "
            + "numero_pagare, monto_aprobado, plazo_meses, tasa_tna, tasa_tea, cft, tasa_moratoria, "
            + "tasa_punitoria, primera_fecha_vencimiento, fecha_formalizacion, fecha_desembolso, "
            + "estado_prestamo, fecha_finalizacion_financiera, motivo_anulacion_sin_desembolso, "
            + "fecha_anulacion_sin_desembolso, anulado_por, estado_cierre_documental, fecha_cierre_documental, "
            + "cierre_documental_por, formalizado_por, id_limite_tasa, tasa_mensual, cuota_contractual, "
            + "numero_constancia_devolucion, numero_comprobante_desembolso";

    public long insertar(Connection conexion, PrestamoCredito prestamo) throws SQLException {
        String sql = "INSERT INTO prestamos (identificador_operacion, id_solicitud, numero_contrato, "
                + "numero_pagare, monto_aprobado, plazo_meses, tasa_tna, tasa_tea, cft, tasa_moratoria, "
                + "tasa_punitoria, primera_fecha_vencimiento, fecha_formalizacion, estado_prestamo, "
                + "estado_cierre_documental, formalizado_por, id_limite_tasa, tasa_mensual, cuota_contractual) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, prestamo.getIdentificadorOperacion());
            sentencia.setLong(2, prestamo.getIdSolicitud());
            sentencia.setString(3, prestamo.getNumeroContrato());
            sentencia.setString(4, prestamo.getNumeroPagare());
            sentencia.setBigDecimal(5, prestamo.getMontoAprobado());
            sentencia.setInt(6, prestamo.getPlazoMeses());
            sentencia.setBigDecimal(7, prestamo.getTasaTna());
            sentencia.setBigDecimal(8, prestamo.getTasaTea());
            setBigDecimalNullable(sentencia, 9, prestamo.getCft());
            sentencia.setBigDecimal(10, prestamo.getTasaMoratoria());
            sentencia.setBigDecimal(11, prestamo.getTasaPunitoria());
            sentencia.setDate(12, Date.valueOf(prestamo.getPrimeraFechaVencimiento()));
            sentencia.setString(13, prestamo.getEstadoPrestamo().name());
            sentencia.setString(14, prestamo.getEstadoCierreDocumental());
            sentencia.setLong(15, prestamo.getFormalizadoPor());
            setLongNullable(sentencia, 16, prestamo.getIdLimiteTasa());
            sentencia.setBigDecimal(17, prestamo.getTasaMensual());
            sentencia.setBigDecimal(18, prestamo.getCuotaContractual());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public PrestamoCredito obtenerPorId(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** prestamos.id_solicitud es UNIQUE: como máximo un préstamo por solicitud. */
    public PrestamoCredito obtenerPorSolicitud(Connection conexion, long idSolicitud) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE id_solicitud = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSolicitud);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Corrección 1 (Joel): bandeja de préstamos ya formalizados, esperando el checklist para desembolsar. */
    public List<PrestamoCredito> listarPendientesDesembolso(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE estado_prestamo = 'PENDIENTE_DESEMBOLSO' "
                + "ORDER BY fecha_formalizacion";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                prestamos.add(mapear(resultado));
            }
        }
        return prestamos;
    }

    /**
     * RF14 (pedido de Joel, 11/10/2026): UPDATE condicionado a PENDIENTE_DESEMBOLSO -- si otra
     * transacción ya desembolsó este mismo préstamo en la angosta ventana entre la lectura y
     * este UPDATE (carrera de dos pestañas, anular vs. desembolsar), no se actualiza nada; el
     * llamador revisa el valor devuelto (mismo patrón que SesionCajaDAO.marcarCerrada, RF19, y
     * SolicitudDAO.marcarDecidida, RF09). Devuelve true si de verdad se anuló.
     */
    public boolean marcarAnuladoAntesDesembolso(Connection conexion, long idPrestamo, long idUsuario, String motivo)
            throws SQLException {
        String sql = "UPDATE prestamos SET estado_prestamo = ?, motivo_anulacion_sin_desembolso = ?, "
                + "fecha_anulacion_sin_desembolso = now(), anulado_por = ? "
                + "WHERE id_prestamo = ? AND estado_prestamo = 'PENDIENTE_DESEMBOLSO'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO.name());
            sentencia.setString(2, motivo);
            sentencia.setLong(3, idUsuario);
            sentencia.setLong(4, idPrestamo);
            return sentencia.executeUpdate() > 0;
        }
    }

    /** E5 (ajuste de Joel, regenerar): si cambió la fecha de primer vencimiento, se recalcula el plan. */
    /** RF11 (pedido de Joel, 11/10/2026): además de la fecha, persiste el CFT recalculado con
     * ella -- antes quedaba el CFT viejo de la solicitud, sin relación con el plan regenerado. */
    public void actualizarPrimerVencimientoYCft(Connection conexion, long idPrestamo,
            java.time.LocalDate primeraFechaVencimiento, java.math.BigDecimal cft) throws SQLException {
        String sql = "UPDATE prestamos SET primera_fecha_vencimiento = ?, cft = ? WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(primeraFechaVencimiento));
            sentencia.setBigDecimal(2, cft);
            sentencia.setLong(3, idPrestamo);
            sentencia.executeUpdate();
        }
    }

    /**
     * P08 (E6): al confirmar el desembolso, el préstamo pasa a ACTIVO. RF14 (pedido de Joel,
     * 11/10/2026): UPDATE condicionado a PENDIENTE_DESEMBOLSO -- misma carrera que
     * marcarAnuladoAntesDesembolso, en sentido contrario (alguien anuló el préstamo mientras se
     * confirmaba el desembolso). Devuelve true si de verdad se desembolsó.
     */
    public boolean marcarDesembolsado(Connection conexion, long idPrestamo, java.time.LocalDate fechaDesembolso)
            throws SQLException {
        String sql = "UPDATE prestamos SET estado_prestamo = ?, fecha_desembolso = ? "
                + "WHERE id_prestamo = ? AND estado_prestamo = 'PENDIENTE_DESEMBOLSO'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoPrestamo.ACTIVO.name());
            sentencia.setDate(2, Date.valueOf(fechaDesembolso));
            sentencia.setLong(3, idPrestamo);
            return sentencia.executeUpdate() > 0;
        }
    }

    public PrestamoCredito obtenerPorIdentificadorOperacion(Connection conexion, String identificadorOperacion)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE identificador_operacion = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, identificadorOperacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * B82/B93 (30/10/2026, decisión de Joel): préstamos cuyo número de operación <b>contiene</b>
     * lo que se escribió -- escribir "00005" tiene que encontrar "CC-2026-00005", igual que ya
     * pasaba en /prestamos (ver {@link #listarConFiltros}, misma columna, mismo {@code ILIKE}).
     * {@code ILIKE} ya ignora mayúsculas, así que el fragmento se usa tal cual se escribió.
     *
     * <p>Sin filtro de estado, a propósito: lo mismo que hace la búsqueda exacta de arriba. Cada
     * pantalla aplica después su propia regla (Cobranza explica el préstamo pendiente de desembolso
     * o ya finalizado en vez de decir "no se encontró nada"). Con tope, porque un fragmento corto
     * como "2026" entra en todos: el llamador dice cuántos como máximo
     * ({@code ServicioCobranza.MAXIMO_COINCIDENCIAS_AMBIGUAS}).</p>
     */
    public List<PrestamoCredito> buscarPorFragmentoIdentificadorOperacion(Connection conexion, String fragmento,
            int limite) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE identificador_operacion ILIKE ? "
                + "ORDER BY id_prestamo DESC LIMIT ?";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, "%" + fragmento + "%");
            sentencia.setInt(2, limite);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    prestamos.add(mapear(resultado));
                }
            }
        }
        return prestamos;
    }

    /** P09 (E7, D-77 decisión 1): búsqueda de cobranza por cédula -- puede haber más de uno. Solo
     * ACTIVO/EN_MORA a propósito: nunca tiene sentido "cobrar" sobre un préstamo ya cerrado. */
    public List<PrestamoCredito> buscarActivosOEnMoraPorCedula(Connection conexion, String cedula) throws SQLException {
        return buscarPorCedulaYEstados(conexion, cedula, "'ACTIVO', 'EN_MORA'");
    }

    /** D-117 (punto 21, pedido de Joel): estado de cuenta -- a diferencia de la búsqueda de
     * cobranza de arriba, acá sí interesa poder consultar el historial de un préstamo ya
     * FINALIZADO (antes: "Ningún préstamo con esos datos" al buscar por la cédula de un cliente
     * con el préstamo ya cancelado). */
    public List<PrestamoCredito> buscarActivosEnMoraOFinalizadosPorCedula(Connection conexion, String cedula)
            throws SQLException {
        return buscarPorCedulaYEstados(conexion, cedula, "'ACTIVO', 'EN_MORA', 'FINALIZADO'");
    }

    /** D-139 (pedido de Joel): diagnóstico de la búsqueda de cobranza por cédula -- todos los
     * préstamos del cliente, cualquier estado, para poder explicar por qué no aparece en
     * /cobranza (sin préstamos, pendiente de desembolso, ya finalizado, etc.), en vez de un
     * genérico "no se encontró nada". */
    public List<PrestamoCredito> listarTodosPorCedula(Connection conexion, String cedula) throws SQLException {
        String sql = "SELECT p.* FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "JOIN personas per ON per.id_persona = s.id_cliente "
                + "WHERE per.cedula_identidad = ? "
                + "ORDER BY p.fecha_formalizacion DESC";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cedula);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    prestamos.add(mapear(resultado));
                }
            }
        }
        return prestamos;
    }

    /**
     * B1 (Bloque 7/E10, pedido de Joel): listado único de /prestamos -- misma consulta para la
     * pantalla, el informe PDF y el CSV (mismo criterio que PersonaDAO.listarConFiltros, D-135).
     * Un préstamo sin fecha_desembolso (todavía PENDIENTE_DESEMBOLSO o ANULADO_ANTES_DESEMBOLSO)
     * entra en el filtro de fechas solo cuando no hay rango elegido, o cuando el estado elegido
     * es exactamente PENDIENTE_DESEMBOLSO (pedido explícito de Joel) -- en cualquier otro caso
     * (p. ej. "todos los estados" + un rango de fechas), el filtro se aplica normal y esos
     * préstamos, sin fecha, no entran.
     */
    public List<PrestamoCredito> listarConFiltros(Connection conexion, FiltroPrestamos filtro) throws SQLException {
        boolean hayRangoFechas = filtro.getDesembolsoDesde() != null || filtro.getDesembolsoHasta() != null;
        boolean estadoEsPendiente = EstadoPrestamo.PENDIENTE_DESEMBOLSO.name().equals(filtro.getEstado());
        boolean aplicaRangoFechas = hayRangoFechas && !estadoEsPendiente;
        boolean hayTexto = filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty();

        StringBuilder sql = new StringBuilder("SELECT p.* FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "JOIN personas per ON per.id_persona = s.id_cliente WHERE 1 = 1 ");
        if (filtro.getEstado() != null) {
            sql.append("AND p.estado_prestamo = ? ");
        }
        if (aplicaRangoFechas) {
            sql.append("AND p.fecha_desembolso IS NOT NULL ");
            if (filtro.getDesembolsoDesde() != null) {
                sql.append("AND p.fecha_desembolso >= ? ");
            }
            if (filtro.getDesembolsoHasta() != null) {
                sql.append("AND p.fecha_desembolso <= ? ");
            }
        }
        if (hayTexto) {
            sql.append("AND (p.identificador_operacion ILIKE ? OR per.cedula_identidad LIKE ? OR ")
                    .append(sinAcentos("per.nombres")).append(" ILIKE ").append(sinAcentos("?"))
                    .append(" OR ").append(sinAcentos("per.apellidos")).append(" ILIKE ").append(sinAcentos("?"))
                    .append(") ");
        }
        sql.append("ORDER BY p.id_prestamo DESC");

        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int indice = 1;
            if (filtro.getEstado() != null) {
                sentencia.setString(indice++, filtro.getEstado());
            }
            if (aplicaRangoFechas) {
                if (filtro.getDesembolsoDesde() != null) {
                    sentencia.setDate(indice++, Date.valueOf(filtro.getDesembolsoDesde()));
                }
                if (filtro.getDesembolsoHasta() != null) {
                    sentencia.setDate(indice++, Date.valueOf(filtro.getDesembolsoHasta()));
                }
            }
            if (hayTexto) {
                String comodinOperacionCedula = "%" + filtro.getTexto().trim() + "%";
                String comodinNombre = "%" + filtro.getTexto().trim() + "%";
                sentencia.setString(indice++, comodinOperacionCedula);
                sentencia.setString(indice++, comodinOperacionCedula);
                sentencia.setString(indice++, comodinNombre);
                sentencia.setString(indice++, comodinNombre);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    prestamos.add(mapear(resultado));
                }
            }
        }
        return prestamos;
    }

    /** Sin extensión "unaccent" (no se toca la estructura de sgcpc_db): translate() ya viene con
     * PostgreSQL -- mismo criterio que PersonaDAO.sinAcentos (D-136), para que "jose"/"José"
     * encuentren por igual al cliente del préstamo. */
    private static final String ACENTOS_ORIGEN = "áéíóúÁÉÍÓÚñÑüÜ";
    private static final String ACENTOS_DESTINO = "aeiouAEIOUnNuU";

    private String sinAcentos(String expresion) {
        return "translate(" + expresion + ", '" + ACENTOS_ORIGEN + "', '" + ACENTOS_DESTINO + "')";
    }

    private List<PrestamoCredito> buscarPorCedulaYEstados(Connection conexion, String cedula, String estadosEnLista)
            throws SQLException {
        String sql = "SELECT p.* FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "JOIN personas per ON per.id_persona = s.id_cliente "
                + "WHERE per.cedula_identidad = ? AND p.estado_prestamo IN (" + estadosEnLista + ") "
                + "ORDER BY p.fecha_formalizacion";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cedula);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    prestamos.add(mapear(resultado));
                }
            }
        }
        return prestamos;
    }

    /** C-4 paso 1 (pedido de Joel): el préstamo MÁS RECIENTE de un cliente, cualquiera sea su
     * estado (o null si nunca tuvo uno) -- para la columna "Préstamo" del informe de Clientes
     * nuevos, que antes mostraba "Sí"/"No" sobre ACTIVO/EN_MORA nada más y confundía con un
     * PENDIENTE_DESEMBOLSO o un FINALIZADO (mostraban "No" igual que alguien sin préstamo).
     * id_prestamo DESC (no fecha_formalizacion) -- orden de creación real, sin empates posibles. */
    public PrestamoCredito obtenerUltimoPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT p.* FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "WHERE s.id_cliente = ? ORDER BY p.id_prestamo DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** 2c (pedido de Joel, 17/10/2026): el préstamo PENDIENTE_DESEMBOLSO de un cliente, si tiene
     * (o null) -- para bloquear la edición de los datos de evaluación mientras el préstamo
     * formalizado todavía no se desembolsó. A diferencia de obtenerUltimoPorCliente, filtra solo
     * ese estado (ACTIVO/EN_MORA/FINALIZADO/ANULADO_ANTES_DESEMBOLSO no bloquean: la evaluación ya
     * no está "en curso"). */
    public PrestamoCredito buscarPendienteDesembolsoPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT p.* FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "WHERE s.id_cliente = ? AND p.estado_prestamo = 'PENDIENTE_DESEMBOLSO' LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Bloque 7/E10 (D-129, Obs. 31): cantidad de préstamos ACTIVO/EN_MORA de un cliente -- para
     * la columna del listado de Clientes y para bloquear la baja si tiene préstamos activos. */
    public int contarActivosOEnMoraPorCliente(Connection conexion, long idCliente) throws SQLException {
        String sql = "SELECT count(*) FROM prestamos p "
                + "JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud "
                + "WHERE s.id_cliente = ? AND p.estado_prestamo IN ('ACTIVO', 'EN_MORA')";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idCliente);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    /** Bloque 4 (E8, decisión 5): todos los préstamos que el recálculo diario tiene que mirar --
     * nunca FINALIZADO/PENDIENTE_DESEMBOLSO/ANULADO (un ACTIVO puede pasar a EN_MORA hoy). */
    public List<PrestamoCredito> listarActivosOEnMora(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE estado_prestamo IN ('ACTIVO', 'EN_MORA') "
                + "ORDER BY id_prestamo";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                prestamos.add(mapear(resultado));
            }
        }
        return prestamos;
    }

    /** E9/Bloque 5 (P10, corrección de Joel): préstamos FINALIZADO cuyo cierre documental (la
     * devolución del pagaré) todavía no está COMPLETO -- para la sección propia de /formalizacion,
     * sin la cual el cierre documental no tenía ningún acceso visible desde la bandeja. */
    public List<PrestamoCredito> listarFinalizadosConCierrePendiente(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE estado_prestamo = 'FINALIZADO' "
                + "AND estado_cierre_documental <> 'COMPLETO' ORDER BY fecha_finalizacion_financiera";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                prestamos.add(mapear(resultado));
            }
        }
        return prestamos;
    }

    /** E-12 (pedido de Joel): TODOS los préstamos FINALIZADO (cancelados) en el rango, por la
     * fecha en que se cancelaron -- reemplaza a listarCierresDocumentalesCompletosPorRango
     * (que solo traía los que YA tenían el pagaré devuelto, dejando afuera a los que seguían
     * pendientes de devolución, como el caso de Jorge/CC-2025-00001 que motivó este pedido). El
     * estado del pagaré (devuelto o pendiente) lo decide ServicioReportes con estado_cierre_
     * documental, fila por fila -- esta consulta no filtra por eso. */
    public List<PrestamoCredito> listarFinalizadosPorRangoCancelacion(Connection conexion,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE estado_prestamo = 'FINALIZADO' "
                + "AND fecha_finalizacion_financiera::date BETWEEN ? AND ? ORDER BY fecha_finalizacion_financiera";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, java.sql.Date.valueOf(desde));
            sentencia.setDate(2, java.sql.Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    prestamos.add(mapear(resultado));
                }
            }
        }
        return prestamos;
    }

    /** Bloque 4 (E8, P14/Morosidad/Incobrables): solo los que ya están EN_MORA -- para las
     * pantallas de mora, distinto del recálculo diario de arriba, que también mira los ACTIVO. */
    public List<PrestamoCredito> listarEnMora(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM prestamos WHERE estado_prestamo = 'EN_MORA' "
                + "ORDER BY id_prestamo";
        List<PrestamoCredito> prestamos = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                prestamos.add(mapear(resultado));
            }
        }
        return prestamos;
    }

    /** P09 (E7, D-77 decisión 5): recalculo de EN_MORA/ACTIVO al aplicar un pago o al abrir la ficha de cobranza. */
    public void actualizarEstadoMora(Connection conexion, long idPrestamo, EstadoPrestamo nuevoEstado)
            throws SQLException {
        String sql = "UPDATE prestamos SET estado_prestamo = ? WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nuevoEstado.name());
            sentencia.setLong(2, idPrestamo);
            sentencia.executeUpdate();
        }
    }

    /** E-13: misma regla que el CASE SQL de revertirFinalizacion, en Java puro -- para poder
     * probarla con JUnit sin tocar la base (PrestamoDAOTest). Si el SQL de abajo cambia, actualizar
     * también acá: PENDIENTE (puesto por marcarFinalizado) vuelve a NO_APLICA; COMPLETO (el pagaré
     * ya se devolvió) queda igual -- revertir con el pagaré ya entregado no debería poder darse. */
    public static String siguienteCierreDocumentalAlRevertir(String estadoCierreDocumentalActual) {
        return "PENDIENTE".equals(estadoCierreDocumentalActual) ? "NO_APLICA" : estadoCierreDocumentalActual;
    }

    /** PO-10 (E7, RN-12): cancelación total -- saldo en cero, préstamo FINALIZADO. */
    /** E9/Bloque 5: al revertir una cancelación total -- el préstamo deja de estar FINALIZADO y
     * vuelve a ACTIVO/EN_MORA (lo que corresponda, recalculado por ServicioCobranza como siempre);
     * fecha_finalizacion_financiera se limpia, ya no aplica.
     * E-13 (pedido de Joel): el cierre documental vuelve a NO_APLICA junto con la reversión --
     * pero SOLO si estaba en PENDIENTE (puesto por marcarFinalizado); ver
     * siguienteCierreDocumentalAlRevertir arriba, misma regla. Si ya estaba en COMPLETO (el pagaré
     * ya se devolvió) no se toca -- revertir una finalización con el pagaré ya en manos del
     * cliente es un caso que no debería poder darse; devuelve true en ese caso para que el
     * llamador lo dibuje en la auditoría, en vez de hacerlo desaparecer en silencio. */
    public boolean revertirFinalizacion(Connection conexion, long idPrestamo, EstadoPrestamo nuevoEstado)
            throws SQLException {
        String sql = "UPDATE prestamos SET estado_prestamo = ?, fecha_finalizacion_financiera = NULL, "
                + "estado_cierre_documental = CASE WHEN estado_cierre_documental = 'PENDIENTE' THEN 'NO_APLICA' "
                + "ELSE estado_cierre_documental END "
                + "WHERE id_prestamo = ? RETURNING estado_cierre_documental";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nuevoEstado.name());
            sentencia.setLong(2, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return "COMPLETO".equals(resultado.getString(1));
            }
        }
    }

    /** E9/Bloque 5 (P10, cierre documental): mismo patrón que numero_contrato/numero_pagare. */
    public void actualizarNumeroConstanciaDevolucion(Connection conexion, long idPrestamo, String numero)
            throws SQLException {
        String sql = "UPDATE prestamos SET numero_constancia_devolucion = ? WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, numero);
            sentencia.setLong(2, idPrestamo);
            sentencia.executeUpdate();
        }
    }

    /** Bloque 7/E10 (D-130): asignado al imprimir el comprobante "para firma", antes de confirmar el desembolso. */
    public void actualizarNumeroComprobanteDesembolso(Connection conexion, long idPrestamo, String numero)
            throws SQLException {
        String sql = "UPDATE prestamos SET numero_comprobante_desembolso = ? WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, numero);
            sentencia.setLong(2, idPrestamo);
            sentencia.executeUpdate();
        }
    }

    /** E9/Bloque 5 (P10): al cargar el escaneo firmado de la constancia de devolución del pagaré. */
    public void marcarCierreDocumentalCompleto(Connection conexion, long idPrestamo, long idUsuario)
            throws SQLException {
        String sql = "UPDATE prestamos SET estado_cierre_documental = 'COMPLETO', fecha_cierre_documental = now(), "
                + "cierre_documental_por = ? WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuario);
            sentencia.setLong(2, idPrestamo);
            sentencia.executeUpdate();
        }
    }

    /** E-13: misma regla que el CASE SQL de marcarFinalizado, en Java puro -- para poder probarla
     * con JUnit sin tocar la base (PrestamoDAOTest). Si el SQL de abajo cambia, actualizar también
     * acá: NO_APLICA (sembrado al crear el préstamo) pasa a PENDIENTE; cualquier otro valor (no
     * debería pasar) queda igual, nunca se pisa hacia atrás. */
    public static String siguienteCierreDocumentalAlFinalizar(String estadoCierreDocumentalActual) {
        return "NO_APLICA".equals(estadoCierreDocumentalActual) ? "PENDIENTE" : estadoCierreDocumentalActual;
    }

    /** E-13 (pedido de Joel): junto con la finalización, el cierre documental pasa de NO_APLICA
     * (el valor sembrado al crear el préstamo, D-68) a PENDIENTE -- recién ahí hay un pagaré real
     * esperando devolución; ver siguienteCierreDocumentalAlFinalizar arriba, misma regla. Si por
     * algún motivo ya estuviera en PENDIENTE o COMPLETO (no debería pasar: marcarFinalizado solo se
     * llama al transicionar DESDE ACTIVO/EN_MORA), el CASE lo deja tal cual, nunca lo pisa hacia
     * atrás. */
    public void marcarFinalizado(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "UPDATE prestamos SET estado_prestamo = ?, fecha_finalizacion_financiera = now(), "
                + "estado_cierre_documental = CASE WHEN estado_cierre_documental = 'NO_APLICA' THEN 'PENDIENTE' "
                + "ELSE estado_cierre_documental END "
                + "WHERE id_prestamo = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoPrestamo.FINALIZADO.name());
            sentencia.setLong(2, idPrestamo);
            sentencia.executeUpdate();
        }
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

    private PrestamoCredito mapear(ResultSet resultado) throws SQLException {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(resultado.getLong("id_prestamo"));
        prestamo.setIdentificadorOperacion(resultado.getString("identificador_operacion"));
        long idSolicitud = resultado.getLong("id_solicitud");
        prestamo.setIdSolicitud(resultado.wasNull() ? null : idSolicitud);
        prestamo.setNumeroContrato(resultado.getString("numero_contrato"));
        prestamo.setNumeroPagare(resultado.getString("numero_pagare"));
        prestamo.setMontoAprobado(resultado.getBigDecimal("monto_aprobado"));
        prestamo.setPlazoMeses(resultado.getInt("plazo_meses"));
        prestamo.setTasaTna(resultado.getBigDecimal("tasa_tna"));
        prestamo.setTasaTea(resultado.getBigDecimal("tasa_tea"));
        prestamo.setCft(resultado.getBigDecimal("cft"));
        prestamo.setTasaMoratoria(resultado.getBigDecimal("tasa_moratoria"));
        prestamo.setTasaPunitoria(resultado.getBigDecimal("tasa_punitoria"));
        prestamo.setPrimeraFechaVencimiento(resultado.getDate("primera_fecha_vencimiento").toLocalDate());
        prestamo.setFechaFormalizacion(resultado.getTimestamp("fecha_formalizacion").toLocalDateTime());
        java.sql.Date fechaDesembolso = resultado.getDate("fecha_desembolso");
        prestamo.setFechaDesembolso(fechaDesembolso == null ? null : fechaDesembolso.toLocalDate());
        prestamo.setEstadoPrestamo(EstadoPrestamo.valueOf(resultado.getString("estado_prestamo")));
        Timestamp fechaFinalizacion = resultado.getTimestamp("fecha_finalizacion_financiera");
        prestamo.setFechaFinalizacionFinanciera(fechaFinalizacion == null ? null : fechaFinalizacion.toLocalDateTime());
        prestamo.setMotivoAnulacionSinDesembolso(resultado.getString("motivo_anulacion_sin_desembolso"));
        Timestamp fechaAnulacion = resultado.getTimestamp("fecha_anulacion_sin_desembolso");
        prestamo.setFechaAnulacionSinDesembolso(fechaAnulacion == null ? null : fechaAnulacion.toLocalDateTime());
        long anuladoPor = resultado.getLong("anulado_por");
        prestamo.setAnuladoPor(resultado.wasNull() ? null : anuladoPor);
        prestamo.setEstadoCierreDocumental(resultado.getString("estado_cierre_documental"));
        Timestamp fechaCierre = resultado.getTimestamp("fecha_cierre_documental");
        prestamo.setFechaCierreDocumental(fechaCierre == null ? null : fechaCierre.toLocalDateTime());
        long cierreDocumentalPor = resultado.getLong("cierre_documental_por");
        prestamo.setCierreDocumentalPor(resultado.wasNull() ? null : cierreDocumentalPor);
        prestamo.setFormalizadoPor(resultado.getLong("formalizado_por"));
        long idLimiteTasa = resultado.getLong("id_limite_tasa");
        prestamo.setIdLimiteTasa(resultado.wasNull() ? null : idLimiteTasa);
        prestamo.setTasaMensual(resultado.getBigDecimal("tasa_mensual"));
        prestamo.setCuotaContractual(resultado.getBigDecimal("cuota_contractual"));
        prestamo.setNumeroConstanciaDevolucion(resultado.getString("numero_constancia_devolucion"));
        prestamo.setNumeroComprobanteDesembolso(resultado.getString("numero_comprobante_desembolso"));
        return prestamo;
    }
}
