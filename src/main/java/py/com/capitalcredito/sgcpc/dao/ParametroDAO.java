package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;
import py.com.capitalcredito.sgcpc.modelos.ParametroSistema;

/**
 * Acceso a parametros_sistema (bd/01_esquema.sql). Un cambio de valor
 * nunca hace UPDATE de una vigencia vieja: cierra esa fila
 * (vigente_hasta) e inserta una fila nueva, para conservar el historico
 * (Doc. 08 §8.11).
 */
public class ParametroDAO {

    public List<ParametroSistema> listarVigentes(Connection conexion) throws SQLException {
        String sql = "SELECT id_parametro, clave_parametro, nombre_parametro, valor_parametro, tipo_valor, "
                + "vigente_desde, vigente_hasta, modificado_por, fecha_modificacion, observacion "
                + "FROM parametros_sistema WHERE vigente_hasta IS NULL ORDER BY clave_parametro";
        List<ParametroSistema> parametros = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                parametros.add(mapear(resultado));
            }
        }
        return parametros;
    }

    public ParametroSistema obtenerVigentePorClave(Connection conexion, String clave) throws SQLException {
        String sql = "SELECT id_parametro, clave_parametro, nombre_parametro, valor_parametro, tipo_valor, "
                + "vigente_desde, vigente_hasta, modificado_por, fecha_modificacion, observacion "
                + "FROM parametros_sistema WHERE clave_parametro = ? AND vigente_hasta IS NULL";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * D-94 (E8): el valor vigente en una fecha PASADA, no la de hoy -- para que un comprobante
     * (recibo/factura/constancia/desembolso/arqueo) reimprima los datos de empresa/timbrado tal
     * como estaban el día de la operación, aunque hoy ya hayan cambiado. El historial completo ya
     * está en la tabla (ver el comentario de la clase); esto solo agrega el filtro por fecha.
     */
    public ParametroSistema obtenerVigenteAFecha(Connection conexion, String clave, LocalDate fecha)
            throws SQLException {
        String sql = "SELECT id_parametro, clave_parametro, nombre_parametro, valor_parametro, tipo_valor, "
                + "vigente_desde, vigente_hasta, modificado_por, fecha_modificacion, observacion "
                + "FROM parametros_sistema WHERE clave_parametro = ? AND vigente_desde <= ? "
                + "AND (vigente_hasta IS NULL OR vigente_hasta > ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            sentencia.setDate(2, Date.valueOf(fecha));
            sentencia.setDate(3, Date.valueOf(fecha));
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * Punto 16b (pedido de Joel): la fila del parámetro cuyo VALOR coincide con uno dado (ej. el
     * número de timbrado grabado en una factura) -- para reconstruir en qué período regía ESE
     * timbrado puntual, sin depender de qué fecha tenga la factura. Si hubiera más de una
     * coincidencia (no debería pasar con timbrados, cada número es único), la más reciente.
     */
    public ParametroSistema obtenerPorClaveYValor(Connection conexion, String clave, String valor) throws SQLException {
        String sql = "SELECT id_parametro, clave_parametro, nombre_parametro, valor_parametro, tipo_valor, "
                + "vigente_desde, vigente_hasta, modificado_por, fecha_modificacion, observacion "
                + "FROM parametros_sistema WHERE clave_parametro = ? AND valor_parametro = ? "
                + "ORDER BY vigente_desde DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            sentencia.setString(2, valor);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Punto 16b (pedido de Joel): la versión MÁS ANTIGUA de un parámetro -- respaldo cuando no
     * hay ninguna vigente en la fecha pedida (datos de empresa.* que no se graban en la propia
     * factura/recibo/etc.), en vez de rechazar la reimpresión de un documento viejo.
     * <p>Mismo riesgo que el LAG de listarHistorial (encontrado probando RF39, D-151): si esa
     * clave se editó el mismo día de su carga inicial, dos filas empatan en vigente_desde (DATE,
     * sin hora) y el orden entre ellas no está garantizado. Desempate por fecha_modificacion e
     * id_parametro, en el mismo sentido ASC (se busca la más vieja, no la más nueva). */
    public ParametroSistema obtenerMasAntiguo(Connection conexion, String clave) throws SQLException {
        String sql = "SELECT id_parametro, clave_parametro, nombre_parametro, valor_parametro, tipo_valor, "
                + "vigente_desde, vigente_hasta, modificado_por, fecha_modificacion, observacion "
                + "FROM parametros_sistema WHERE clave_parametro = ? "
                + "ORDER BY vigente_desde ASC, fecha_modificacion ASC, id_parametro ASC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, clave);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public void cerrarVigencia(Connection conexion, long idParametro, LocalDate vigenteHasta) throws SQLException {
        String sql = "UPDATE parametros_sistema SET vigente_hasta = ? WHERE id_parametro = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(vigenteHasta));
            sentencia.setLong(2, idParametro);
            sentencia.executeUpdate();
        }
    }

    /** C-4.3 (pedido de Joel): historial completo de cambios -- cada fila de parametros_sistema YA
     * es un evento del historial (la vigencia que empezó en esa fecha); el valor anterior se
     * calcula con LAG() sobre TODO el historial de esa clave (antes de aplicar el filtro de fecha,
     * para que el valor anterior sea correcto aunque esa vigencia previa haya quedado fuera del
     * rango pedido). "Quién lo modificó" sale de un JOIN directo con usuarios (mismo criterio que
     * AuditoriaDAO.listarPorFiltros), no de una resolución aparte por fila. Solo lectura: esta
     * consulta nunca escribe nada.
     * <p>Encontrado probando RF39: el LAG ordenaba solo por vigente_desde (DATE, sin hora) -- con
     * dos o más cambios reales del mismo parámetro el mismo día (ej. humo_http.sh fuerza y
     * revierte varias veces), esas filas empataban y Postgres no garantiza ningún orden entre
     * filas empatadas de una window function, así que el "valor anterior" podía salir de la fila
     * equivocada (confirmado con dos parámetros reales de sgcpc_db_pruebas que tuvieron varios
     * cambios el mismo día). Corregido a vigente_desde, fecha_modificacion, id_parametro -- en ese
     * orden, y no "fecha_modificacion primero": vigente_desde sigue siendo el criterio principal
     * porque ya es correcto en un caso real que fecha_modificacion solo rompería -- la migración
     * de "timbrado anterior" (D-113/7.4) inserta una fila con vigente_desde de un período PASADO
     * (2025) después, en el reloj real, que la fila del período actual (2026); ordenar por
     * fecha_modificacion primero invertiría ese par (nuevo→viejo en vez de viejo→nuevo). Con
     * vigente_desde primero, fecha_modificacion solo entra a desempatar cuando dos filas caen en
     * el mismo día (el caso real que se estaba rompiendo), e id_parametro es el último desempate,
     * por si dos cambios cayeran en el mismo microsegundo.
     * <p>RF39 (encontrado probando D-151): "nombre_parametro" ya NO sale directo de la fila --
     * cada fila lo copiaba tal cual estaba en el momento de ese cambio (ServicioParametros.
     * actualizarValor), así que una corrección de texto posterior (ej. D-151's "ficticio") deja
     * el historial viejo con el nombre congelado de antes, mostrando "dos nombres" para el mismo
     * parámetro. Se resuelve con el nombre ACTUAL (de la fila vigente_hasta IS NULL) por
     * clave_parametro, agrupando siempre por la clave (que nunca cambia) y no por el nombre (que
     * sí puede cambiar). COALESCE defensivo: si por algún motivo esa clave ya no tiene fila
     * vigente, cae al nombre congelado en vez de mostrar null.
     * <p>"incluirIniciales" (pedido de Joel): oculta por defecto las cargas iniciales del script
     * de siembra (la primera fila de cada clave, sin vigencia anterior -- valor_anterior IS NULL
     * por el LAG) -- no son "cambios" hechos por una persona. */
    public java.util.List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo> listarHistorial(
            Connection conexion, LocalDate desde, LocalDate hasta, String claveParametro, boolean incluirIniciales)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT t.clave_parametro, "
                        + "COALESCE((SELECT np.nombre_parametro FROM parametros_sistema np "
                        + "WHERE np.clave_parametro = t.clave_parametro AND np.vigente_hasta IS NULL), "
                        + "t.nombre_parametro) AS nombre_parametro, "
                        + "t.valor_anterior, t.valor_parametro, t.tipo_valor, "
                        + "t.vigente_desde, t.vigente_hasta, t.fecha_modificacion, t.observacion, t.nombres, t.apellidos "
                        + "FROM (SELECT p.*, u.nombres, u.apellidos, "
                        + "LAG(p.valor_parametro) OVER (PARTITION BY p.clave_parametro "
                        + "ORDER BY p.vigente_desde, p.fecha_modificacion, p.id_parametro) AS valor_anterior "
                        + "FROM parametros_sistema p JOIN usuarios u ON u.id_usuario = p.modificado_por) t "
                        + "WHERE t.fecha_modificacion::date BETWEEN ? AND ?");
        if (claveParametro != null) {
            sql.append(" AND t.clave_parametro = ?");
        }
        if (!incluirIniciales) {
            sql.append(" AND t.valor_anterior IS NOT NULL");
        }
        sql.append(" ORDER BY t.fecha_modificacion DESC");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int indice = 1;
            sentencia.setObject(indice++, desde);
            sentencia.setObject(indice++, hasta);
            if (claveParametro != null) {
                sentencia.setString(indice++, claveParametro);
            }
            java.util.List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo> filas = new ArrayList<>();
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    Date vigenteHasta = resultado.getDate("vigente_hasta");
                    filas.add(new py.com.capitalcredito.sgcpc.dto.FilaHistorialParametroCrudo(
                            resultado.getString("clave_parametro"), resultado.getString("nombre_parametro"),
                            resultado.getString("valor_anterior"), resultado.getString("valor_parametro"),
                            TipoValorParametro.valueOf(resultado.getString("tipo_valor")),
                            resultado.getDate("vigente_desde").toLocalDate(),
                            vigenteHasta == null ? null : vigenteHasta.toLocalDate(),
                            resultado.getString("nombres") + " " + resultado.getString("apellidos"),
                            resultado.getTimestamp("fecha_modificacion").toLocalDateTime(),
                            resultado.getString("observacion")));
                }
            }
            return filas;
        }
    }

    public long insertar(Connection conexion, ParametroSistema parametro) throws SQLException {
        String sql = "INSERT INTO parametros_sistema "
                + "(clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion, observacion) "
                + "VALUES (?, ?, ?, ?, ?, ?, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, parametro.getClaveParametro());
            sentencia.setString(2, parametro.getNombreParametro());
            sentencia.setString(3, parametro.getValorParametro());
            sentencia.setString(4, parametro.getTipoValor().name());
            sentencia.setDate(5, Date.valueOf(parametro.getVigenteDesde()));
            sentencia.setLong(6, parametro.getModificadoPor());
            sentencia.setString(7, parametro.getObservacion());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    private ParametroSistema mapear(ResultSet resultado) throws SQLException {
        ParametroSistema parametro = new ParametroSistema();
        parametro.setIdParametro(resultado.getLong("id_parametro"));
        parametro.setClaveParametro(resultado.getString("clave_parametro"));
        parametro.setNombreParametro(resultado.getString("nombre_parametro"));
        parametro.setValorParametro(resultado.getString("valor_parametro"));
        parametro.setTipoValor(TipoValorParametro.valueOf(resultado.getString("tipo_valor")));
        parametro.setVigenteDesde(resultado.getDate("vigente_desde").toLocalDate());
        Date vigenteHasta = resultado.getDate("vigente_hasta");
        parametro.setVigenteHasta(vigenteHasta == null ? null : vigenteHasta.toLocalDate());
        parametro.setModificadoPor(resultado.getLong("modificado_por"));
        Timestamp fechaModificacion = resultado.getTimestamp("fecha_modificacion");
        parametro.setFechaModificacion(fechaModificacion == null ? null : fechaModificacion.toLocalDateTime());
        parametro.setObservacion(resultado.getString("observacion"));
        return parametro;
    }
}
