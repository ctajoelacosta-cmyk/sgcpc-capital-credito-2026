package py.com.capitalcredito.sgcpc.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;

/** Acceso a sesiones_caja (bd/01_esquema.sql / Doc. 00 §10.1, P07, E6). */
public class SesionCajaDAO {

    private static final String COLUMNAS = "id_sesion_caja, id_cajero, fecha_hora_apertura, saldo_inicial, "
            + "fecha_hora_cierre, saldo_teorico_cierre, saldo_fisico, diferencia_arqueo, estado_sesion, "
            + "id_archivo_cierre, motivo_diferencia, id_gerente_revisor";

    public long insertar(Connection conexion, SesionCaja sesion) throws SQLException {
        String sql = "INSERT INTO sesiones_caja (id_cajero, fecha_hora_apertura, saldo_inicial, estado_sesion) "
                + "VALUES (?, now(), ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, sesion.getIdCajero());
            sentencia.setBigDecimal(2, sesion.getSaldoInicial());
            sentencia.setString(3, EstadoSesionCaja.ABIERTA.name());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** Índice único parcial ux_sesiones_caja_una_abierta: a lo sumo una fila. */
    public SesionCaja obtenerAbierta(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE estado_sesion = 'ABIERTA'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            return resultado.next() ? mapear(resultado) : null;
        }
    }

    public SesionCaja obtenerPorId(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE id_sesion_caja = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * Cierre y arqueo (PO-12): todo en un solo UPDATE, en la misma transacción que la rendición.
     * RF19 (pedido de Joel, 11/10/2026): condicionado a `estado_sesion = 'ABIERTA'` -- cierra la
     * ventana de la carrera de dos pestañas, mismo patrón que `SolicitudDAO.marcarDecidida`
     * (RF09): si otra transacción ya cerró esta sesión, `executeUpdate()` devuelve 0 y el
     * llamador (ServicioCaja.cerrarCaja) no inserta la rendición ni audita nada.
     */
    public boolean marcarCerrada(Connection conexion, long idSesionCaja, BigDecimal saldoTeoricoCierre,
            BigDecimal saldoFisico, BigDecimal diferenciaArqueo, String motivoDiferencia) throws SQLException {
        String sql = "UPDATE sesiones_caja SET estado_sesion = ?, fecha_hora_cierre = now(), "
                + "saldo_teorico_cierre = ?, saldo_fisico = ?, diferencia_arqueo = ?, motivo_diferencia = ? "
                + "WHERE id_sesion_caja = ? AND estado_sesion = 'ABIERTA'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, EstadoSesionCaja.CERRADA.name());
            sentencia.setBigDecimal(2, saldoTeoricoCierre);
            sentencia.setBigDecimal(3, saldoFisico);
            sentencia.setBigDecimal(4, diferenciaArqueo);
            sentencia.setString(5, motivoDiferencia);
            sentencia.setLong(6, idSesionCaja);
            return sentencia.executeUpdate() > 0;
        }
    }

    /** Decisión 4 del plan de E6: anotación de una sola vez, no toca ningún otro campo. */
    public void marcarRevisadaPorGerente(Connection conexion, long idSesionCaja, long idUsuarioGerente)
            throws SQLException {
        String sql = "UPDATE sesiones_caja SET id_gerente_revisor = ? WHERE id_sesion_caja = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuarioGerente);
            sentencia.setLong(2, idSesionCaja);
            sentencia.executeUpdate();
        }
    }

    /** Cajas CERRADAS con diferencia todavía sin revisar por el gerente (RN-23). */
    public List<SesionCaja> listarConDiferenciaSinRevisar(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE estado_sesion = 'CERRADA' "
                + "AND diferencia_arqueo <> 0 AND id_gerente_revisor IS NULL ORDER BY fecha_hora_cierre";
        List<SesionCaja> sesiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                sesiones.add(mapear(resultado));
            }
        }
        return sesiones;
    }

    /** E8 (reimpresión del arqueo, D-94): últimas sesiones CERRADAS, de la más reciente a la más vieja. */
    /**
     * B191 (15/11/2026): todas las cerradas, de la más nueva a la más vieja -- lo mismo que
     * {@link #listarCerradas(Connection, int)} pero sin el {@code LIMIT}, para que la pantalla
     * pueda paginar sobre el total en vez de mostrar "las últimas 10" y esconder el resto.
     *
     * <p>Mismo {@code ORDER BY} que el método con límite, así que la página 1 trae exactamente las
     * 10 que se veían antes (más 10 más). No hay versión "sin tope" encubierta: el que necesita un
     * tope sigue llamando al otro método, que lo usa el conteo del Panel.</p>
     */
    public List<SesionCaja> listarCerradas(Connection conexion) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE estado_sesion = 'CERRADA' "
                + "ORDER BY fecha_hora_cierre DESC";
        List<SesionCaja> sesiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    sesiones.add(mapear(resultado));
                }
            }
        }
        return sesiones;
    }

    public List<SesionCaja> listarCerradas(Connection conexion, int limite) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE estado_sesion = 'CERRADA' "
                + "ORDER BY fecha_hora_cierre DESC LIMIT ?";
        List<SesionCaja> sesiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, limite);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    sesiones.add(mapear(resultado));
                }
            }
        }
        return sesiones;
    }

    /** E8 (Bloque 2, corrección de Joel): sesiones CERRADAS con cierre dentro de un rango de fechas
     * -- para llegar a una sesión vieja que las "últimas 10" no alcanzan a mostrar. */
    public List<SesionCaja> listarCerradasEntreFechas(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM sesiones_caja WHERE estado_sesion = 'CERRADA' "
                + "AND fecha_hora_cierre::date BETWEEN ? AND ? ORDER BY fecha_hora_cierre DESC";
        List<SesionCaja> sesiones = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, desde);
            sentencia.setObject(2, hasta);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    sesiones.add(mapear(resultado));
                }
            }
        }
        return sesiones;
    }

    private SesionCaja mapear(ResultSet resultado) throws SQLException {
        SesionCaja sesion = new SesionCaja();
        sesion.setIdSesionCaja(resultado.getLong("id_sesion_caja"));
        sesion.setIdCajero(resultado.getLong("id_cajero"));
        sesion.setFechaHoraApertura(resultado.getTimestamp("fecha_hora_apertura").toLocalDateTime());
        sesion.setSaldoInicial(resultado.getBigDecimal("saldo_inicial"));
        Timestamp fechaCierre = resultado.getTimestamp("fecha_hora_cierre");
        sesion.setFechaHoraCierre(fechaCierre == null ? null : fechaCierre.toLocalDateTime());
        sesion.setSaldoTeoricoCierre(resultado.getBigDecimal("saldo_teorico_cierre"));
        sesion.setSaldoFisico(resultado.getBigDecimal("saldo_fisico"));
        sesion.setDiferenciaArqueo(resultado.getBigDecimal("diferencia_arqueo"));
        sesion.setEstadoSesion(EstadoSesionCaja.valueOf(resultado.getString("estado_sesion")));
        long idArchivoCierre = resultado.getLong("id_archivo_cierre");
        sesion.setIdArchivoCierre(resultado.wasNull() ? null : idArchivoCierre);
        sesion.setMotivoDiferencia(resultado.getString("motivo_diferencia"));
        long idGerenteRevisor = resultado.getLong("id_gerente_revisor");
        sesion.setIdGerenteRevisor(resultado.wasNull() ? null : idGerenteRevisor);
        return sesion;
    }
}
