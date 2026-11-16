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
import py.com.capitalcredito.sgcpc.modelos.GestionCobranza;

/** Acceso a gestiones_cobranza (bd/01_esquema.sql / RN-20, P09, E7; RF-37, Bloque 4, E8). */
public class GestionCobranzaDAO {

    private static final String COLUMNAS = "id_gestion_cobranza, id_prestamo, id_persona_contactada, tipo, "
            + "resultado, excusa, fecha_promesa, monto_prometido, cumplida, registrado_por, fecha_hora, id_pago";

    public long insertar(Connection conexion, GestionCobranza gestion) throws SQLException {
        String sql = "INSERT INTO gestiones_cobranza (id_prestamo, id_persona_contactada, tipo, resultado, "
                + "excusa, fecha_promesa, monto_prometido, cumplida, registrado_por, fecha_hora, id_pago) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, gestion.getIdPrestamo());
            sentencia.setLong(2, gestion.getIdPersonaContactada());
            sentencia.setString(3, gestion.getTipo().name());
            sentencia.setString(4, gestion.getResultado());
            sentencia.setString(5, gestion.getExcusa());
            if (gestion.getFechaPromesa() == null) {
                sentencia.setNull(6, Types.DATE);
            } else {
                sentencia.setDate(6, Date.valueOf(gestion.getFechaPromesa()));
            }
            sentencia.setBigDecimal(7, gestion.getMontoPrometido());
            sentencia.setBoolean(8, gestion.isCumplida());
            sentencia.setLong(9, gestion.getRegistradoPor());
            if (gestion.getIdPago() == null) {
                sentencia.setNull(10, Types.BIGINT);
            } else {
                sentencia.setLong(10, gestion.getIdPago());
            }
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /**
     * D-95 (E8): la gestión PAGO_PARCIAL vinculada a un pago, o null si no hay ninguna (pago
     * viejo de antes de esta columna, sin vínculo seguro -- ver bd/migraciones/E8_02_gestion_pago.sql).
     * El recibo de ese pago no muestra la línea de promesa cuando esto da null, nunca adivina.
     */
    public GestionCobranza obtenerPorPago(Connection conexion, long idPago) throws SQLException {
        String sql = "SELECT id_gestion_cobranza, id_prestamo, id_persona_contactada, tipo, resultado, excusa, "
                + "fecha_promesa, monto_prometido, cumplida, registrado_por, fecha_hora, id_pago "
                + "FROM gestiones_cobranza WHERE id_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Bloque 4 (E8, decisión 1): promesas abiertas (sin resolver todavía) de un préstamo, más
     * reciente primero -- la usa tanto el chequeo de "se cumplió" tras un cobro como la pantalla
     * de mora (que solo muestra la más reciente, `get(0)` si la lista no está vacía). */
    public List<GestionCobranza> listarAbiertasPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM gestiones_cobranza "
                + "WHERE id_prestamo = ? AND fecha_promesa IS NOT NULL AND cumplida = false "
                + "ORDER BY fecha_hora DESC";
        List<GestionCobranza> gestiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    gestiones.add(mapear(resultado));
                }
            }
        }
        return gestiones;
    }

    /** Bloque 4 (E8, decisión 1): marca una promesa como cumplida -- nunca se vuelve atrás. */
    public void marcarCumplida(Connection conexion, long idGestionCobranza) throws SQLException {
        String sql = "UPDATE gestiones_cobranza SET cumplida = true WHERE id_gestion_cobranza = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idGestionCobranza);
            sentencia.executeUpdate();
        }
    }

    /** Bloque 4 (E8): historial completo de un préstamo, más reciente primero (ficha de mora,
     * Estado de cuenta del cliente). */
    public List<GestionCobranza> listarPorPrestamo(Connection conexion, long idPrestamo) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM gestiones_cobranza WHERE id_prestamo = ? ORDER BY fecha_hora DESC";
        List<GestionCobranza> gestiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPrestamo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    gestiones.add(mapear(resultado));
                }
            }
        }
        return gestiones;
    }

    /** Bloque 4 (E8): reporte "Gestiones de cobranza" -- rango de fechas, tipo opcional, solo
     * incumplidas opcional. */
    public List<GestionCobranza> listarPorRango(Connection conexion, LocalDate desde, LocalDate hasta,
            String tipoFiltro, boolean soloIncumplidas) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS + " FROM gestiones_cobranza "
                + "WHERE fecha_hora::date BETWEEN ? AND ?");
        if (tipoFiltro != null) {
            sql.append(" AND tipo = ?");
        }
        if (soloIncumplidas) {
            sql.append(" AND fecha_promesa IS NOT NULL AND cumplida = false AND fecha_promesa < CURRENT_DATE");
        }
        sql.append(" ORDER BY fecha_hora DESC");
        List<GestionCobranza> gestiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            if (tipoFiltro != null) {
                sentencia.setString(3, tipoFiltro);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    gestiones.add(mapear(resultado));
                }
            }
        }
        return gestiones;
    }

    /** Bloque 4 (E8, Panel): cuántas promesas vencen hoy y siguen abiertas. */
    public int contarPromesasDeHoy(Connection conexion) throws SQLException {
        String sql = "SELECT count(*) FROM gestiones_cobranza WHERE fecha_promesa = CURRENT_DATE AND cumplida = false";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    /** E9/Bloque 5 (ajuste A de Joel): si la gestión vinculada al pago revertido se había marcado
     * cumplida POR ese pago, vuelve a cumplida = false -- "como si el cobro nunca hubiera existido". */
    public void marcarNoCumplidaPorPago(Connection conexion, long idPago) throws SQLException {
        String sql = "UPDATE gestiones_cobranza SET cumplida = false WHERE id_pago = ? AND cumplida = true";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            sentencia.executeUpdate();
        }
    }

    private GestionCobranza mapear(ResultSet resultado) throws SQLException {
        GestionCobranza gestion = new GestionCobranza();
        gestion.setIdGestionCobranza(resultado.getLong("id_gestion_cobranza"));
        gestion.setIdPrestamo(resultado.getLong("id_prestamo"));
        gestion.setIdPersonaContactada(resultado.getLong("id_persona_contactada"));
        gestion.setTipo(py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza.valueOf(resultado.getString("tipo")));
        gestion.setResultado(resultado.getString("resultado"));
        gestion.setExcusa(resultado.getString("excusa"));
        Date fechaPromesa = resultado.getDate("fecha_promesa");
        gestion.setFechaPromesa(fechaPromesa == null ? null : fechaPromesa.toLocalDate());
        gestion.setMontoPrometido(resultado.getBigDecimal("monto_prometido"));
        gestion.setCumplida(resultado.getBoolean("cumplida"));
        gestion.setRegistradoPor(resultado.getLong("registrado_por"));
        gestion.setFechaHora(resultado.getTimestamp("fecha_hora").toLocalDateTime());
        long idPago = resultado.getLong("id_pago");
        gestion.setIdPago(resultado.wasNull() ? null : idPago);
        return gestion;
    }
}
