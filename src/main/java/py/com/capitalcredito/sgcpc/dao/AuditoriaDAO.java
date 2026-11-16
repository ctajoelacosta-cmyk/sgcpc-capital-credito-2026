package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.postgresql.util.PGobject;

/**
 * Acceso a auditoria_operaciones (bd/01_esquema.sql): solo INSERT — la
 * tabla ya bloquea UPDATE/DELETE con un trigger (D-11). RNF08: ningun
 * llamador debe pasar una contrasena o un hash en valorAnterior/valorNuevo
 * ni en detalle; este DAO no lo valida (no sabe que es una contrasena),
 * asi que esa responsabilidad es de quien arma el registro.
 */
public class AuditoriaDAO {

    public void insertar(Connection conexion, long idUsuario, String operacion, String tipoEntidad, long idEntidad,
            String valorAnteriorJson, String valorNuevoJson, String direccionIp, String detalle) throws SQLException {
        insertar(conexion, idUsuario, operacion, tipoEntidad, idEntidad, valorAnteriorJson, valorNuevoJson,
                direccionIp, null, detalle);
    }

    /** Bloque de seguridad (hallazgo de Joel): variante con navegador (User-Agent), para los
     * eventos de acceso y de sesión -- el resto de los llamadores (la gran mayoría) sigue usando
     * el de 8 parámetros, que ahora delega acá con navegador=null. */
    public void insertar(Connection conexion, long idUsuario, String operacion, String tipoEntidad, long idEntidad,
            String valorAnteriorJson, String valorNuevoJson, String direccionIp, String navegador, String detalle)
            throws SQLException {
        String sql = "INSERT INTO auditoria_operaciones "
                + "(id_usuario, fecha_hora, operacion, tipo_entidad, id_entidad, valor_anterior, valor_nuevo, "
                + "direccion_ip, navegador, detalle) VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuario);
            sentencia.setString(2, operacion);
            sentencia.setString(3, tipoEntidad);
            sentencia.setLong(4, idEntidad);
            sentencia.setObject(5, aJsonb(valorAnteriorJson));
            sentencia.setObject(6, aJsonb(valorNuevoJson));
            sentencia.setString(7, direccionIp);
            sentencia.setString(8, navegador);
            sentencia.setString(9, detalle);
            sentencia.executeUpdate();
        }
    }

    /**
     * Bloque de seguridad (hallazgo de Joel, decisión nueva -- cambia D-27): intento de acceso
     * con un nombre de usuario que NO existe -- antes no se auditaba nada (no había ninguna
     * cuenta real a la que atribuirle la fila). id_usuario queda NULL; id_entidad usa el mismo
     * criterio de "sin entidad real" que ya usa ServicioAuditoria.yaSeRegistroHoy (0, con
     * tipo_entidad='USUARIO' en vez de 'SISTEMA' porque el contexto es un intento de acceso, no
     * una marca de "corrió hoy"). usuarioEscrito ya debe venir recortado (ver
     * ServicioAutenticacion) -- este DAO no decide longitudes, solo guarda.
     */
    public void insertarIntentoUsuarioInexistente(Connection conexion, String usuarioEscrito, String direccionIp,
            String navegador, String detalle) throws SQLException {
        String sql = "INSERT INTO auditoria_operaciones "
                + "(id_usuario, fecha_hora, operacion, tipo_entidad, id_entidad, usuario_escrito, direccion_ip, "
                + "navegador, detalle) VALUES (NULL, now(), 'ACCESO_USUARIO_INEXISTENTE', 'USUARIO', 0, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, usuarioEscrito);
            sentencia.setString(2, direccionIp);
            sentencia.setString(3, navegador);
            sentencia.setString(4, detalle);
            sentencia.executeUpdate();
        }
    }

    /** Bloque 4 (E8, decisión 5): "¿ya corrió hoy el recálculo diario?" -- sin tabla nueva, la
     * propia auditoría hace de marca (una fila por día, tipo_entidad='SISTEMA', id_entidad=0). */
    public boolean existeHoy(Connection conexion, String operacion) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM auditoria_operaciones "
                + "WHERE operacion = ? AND fecha_hora::date = CURRENT_DATE)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, operacion);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        }
    }

    /** Bloque 4 (E8, reporte Auditoría): filtros opcionales (usuario/entidad/operación, texto
     * parcial en operación); siempre acotado a un rango de fechas. Ordenado por fecha más reciente
     * primero. contarPorFiltros (abajo) devuelve el total SIN el límite.
     *
     * <p><b>B205 (15/11/2026)</b>: además del {@code limite} ahora recibe un
     * {@code desplazamiento}, así la pantalla puede <b>paginar en SQL</b> en vez de quedarse con
     * las primeras 500 filas del rango. Acá tenía que ser en SQL y no con el
     * {@code recortarPagina} en Java de las otras cinco pantallas: con 18.381 registros
     * (medido el 15/11/2026) cortar en Java obliga a traerlos todos a memoria, que es exactamente
     * lo que el tope de 500 evitaba. El tope sigue existiendo, pero solo para el PDF
     * ({@code ServicioReportes.LIMITE_AUDITORIA_PDF}): un informe de 18.381 filas no lo quiere
     * nadie, y el CSV -- que es el camino para llevarse todo -- ya no tiene tope.</p>
     *
     * <p>El {@code ORDER BY} va antes del {@code LIMIT/OFFSET} y es <b>determinista por fecha</b>:
     * dos registros con la misma {@code fecha_hora} podrían alternar entre páginas, así que se
     * desempata por {@code id_auditoria} (la tabla es de solo inserción, así que ese id crece con
     * el tiempo y no cambia el orden visible).</p>
     */
    public java.util.List<py.com.capitalcredito.sgcpc.dto.FilaAuditoria> listarPorFiltros(Connection conexion,
            java.time.LocalDate desde, java.time.LocalDate hasta, Long idUsuario, String tipoEntidad,
            String operacionParcial, int limite, int desplazamiento) throws SQLException {
        // Bloque de seguridad (hallazgo de Joel): LEFT JOIN, no JOIN -- ACCESO_USUARIO_INEXISTENTE
        // tiene id_usuario NULL (nunca hubo una cuenta real), un JOIN interno lo hubiera excluido
        // en silencio del reporte entero.
        StringBuilder sql = new StringBuilder(
                "SELECT a.fecha_hora, u.nombres, u.apellidos, a.operacion, a.tipo_entidad, a.id_entidad, a.detalle, "
                        + "a.direccion_ip, a.navegador, a.usuario_escrito "
                        + "FROM auditoria_operaciones a LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario ");
        agregarWhere(sql, idUsuario, tipoEntidad, operacionParcial);
        sql.append(" ORDER BY a.fecha_hora DESC, a.id_auditoria DESC LIMIT ? OFFSET ?");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int indice = vincularWhere(sentencia, desde, hasta, idUsuario, tipoEntidad, operacionParcial,
                    py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria.buscarCodigosPorTexto(operacionParcial));
            sentencia.setInt(indice, limite);
            sentencia.setInt(indice + 1, desplazamiento);
            java.util.List<py.com.capitalcredito.sgcpc.dto.FilaAuditoria> filas = new java.util.ArrayList<>();
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    String nombres = resultado.getString("nombres");
                    String tipoEntidadFila = resultado.getString("tipo_entidad");
                    String operacionFila = resultado.getString("operacion");
                    // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 pasos 1 y 3): las
                    // filas del sistema muestran "Sistema" en vez del nombre real de quien haya
                    // disparado la operación -- id_usuario sigue guardando ese dato real sin
                    // cambios (solo presentación, nada de esquema ni de ServicioMora).
                    // EtiquetasAuditoria.esOperacionDelSistema es el único lugar con esta regla
                    // (paso 2 la reutiliza para IP/navegador, en ServicioAuditoria).
                    String usuarioMostrado = py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria
                            .esOperacionDelSistema(operacionFila, tipoEntidadFila) ? "Sistema"
                            : nombres == null ? "—" : nombres + " " + resultado.getString("apellidos");
                    filas.add(new py.com.capitalcredito.sgcpc.dto.FilaAuditoria(
                            py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaaHhMm(
                                    resultado.getTimestamp("fecha_hora").toLocalDateTime()),
                            usuarioMostrado, operacionFila, tipoEntidadFila,
                            resultado.getLong("id_entidad"), resultado.getString("detalle"),
                            resultado.getString("direccion_ip"), resultado.getString("navegador"),
                            resultado.getString("usuario_escrito")));
                }
            }
            return filas;
        }
    }

    /** Bloque C tanda 3 (pedido de Joel): cantidad REAL de registros del rango/filtros, sin el
     * límite de listarPorFiltros -- para el aviso "Se muestran las primeras N de M registros". */
    public int contarPorFiltros(Connection conexion, java.time.LocalDate desde, java.time.LocalDate hasta,
            Long idUsuario, String tipoEntidad, String operacionParcial) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auditoria_operaciones a ");
        agregarWhere(sql, idUsuario, tipoEntidad, operacionParcial);
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            vincularWhere(sentencia, desde, hasta, idUsuario, tipoEntidad, operacionParcial,
                    py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria.buscarCodigosPorTexto(operacionParcial));
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    /** E-15 (pedido de Joel, C-3b): totales por tipo de operación sobre TODO el rango filtrado --
     * antes se calculaban en Java sobre las filas ya cortadas por el tope, dando un recuento
     * parcial cuando el rango traía más de 500 registros. GROUP BY aparte, sin el límite ni el
     * desplazamiento de listarPorFiltros -- y por eso siguen siendo los del período completo
     * también ahora que la pantalla pagina (B205): el total de la sección de totales no es el de
     * la página que se está viendo. */
    public java.util.Map<String, Integer> contarPorOperacion(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta, Long idUsuario, String tipoEntidad, String operacionParcial) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT a.operacion, COUNT(*) AS cantidad FROM auditoria_operaciones a ");
        agregarWhere(sql, idUsuario, tipoEntidad, operacionParcial);
        sql.append(" GROUP BY a.operacion ORDER BY a.operacion");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            vincularWhere(sentencia, desde, hasta, idUsuario, tipoEntidad, operacionParcial,
                    py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria.buscarCodigosPorTexto(operacionParcial));
            java.util.Map<String, Integer> totales = new java.util.LinkedHashMap<>();
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    totales.put(resultado.getString("operacion"), resultado.getInt("cantidad"));
                }
            }
            return totales;
        }
    }

    /** RF40/F2 (pedido de Joel, 13/10/2026): valores reales de tipo_entidad para la lista
     * desplegable del filtro "Entidad" -- sin WHERE (son todos los valores posibles, no los del
     * rango de fechas elegido), así que no se recalcula en cada cambio de filtro. Sin consulta
     * pesada: un SELECT DISTINCT sobre una columna indexable, sin JOIN. */
    public java.util.List<String> listarEntidadesDistintas(Connection conexion) throws SQLException {
        String sql = "SELECT DISTINCT tipo_entidad FROM auditoria_operaciones ORDER BY tipo_entidad";
        java.util.List<String> entidades = new java.util.ArrayList<>();
        try (java.sql.Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                entidades.add(resultado.getString("tipo_entidad"));
            }
        }
        return entidades;
    }

    /** Mismo WHERE para listarPorFiltros/contarPorFiltros/contarPorOperacion -- evita que se
     * desincronicen. Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 3): además del
     * ILIKE de siempre sobre el código crudo (sigue encontrando un código escrito a mano, o
     * cualquier operación nueva que todavía no tenga nombre legible en EtiquetasAuditoria), suma
     * un OR con ANY(?) sobre los códigos que matchearon por NOMBRE LEGIBLE
     * (EtiquetasAuditoria.buscarCodigosPorTexto, ya resuelto por el llamador) -- así "mora"
     * encuentra RECALCULO_MORA_DIARIO/RECALCULAR_ESTADO_MORA por el nombre, sin dejar de
     * encontrar cualquier código que literalmente contenga "MORA". */
    private void agregarWhere(StringBuilder sql, Long idUsuario, String tipoEntidad, String operacionParcial) {
        sql.append("WHERE a.fecha_hora::date BETWEEN ? AND ?");
        if (idUsuario != null) {
            sql.append(" AND a.id_usuario = ?");
        }
        if (tipoEntidad != null) {
            sql.append(" AND a.tipo_entidad = ?");
        }
        if (operacionParcial != null) {
            sql.append(" AND (a.operacion ILIKE ? OR a.operacion = ANY(?))");
        }
    }

    private int vincularWhere(PreparedStatement sentencia, java.time.LocalDate desde, java.time.LocalDate hasta,
            Long idUsuario, String tipoEntidad, String operacionParcial,
            java.util.List<String> codigosPorNombreLegible) throws SQLException {
        int indice = 1;
        sentencia.setObject(indice++, desde);
        sentencia.setObject(indice++, hasta);
        if (idUsuario != null) {
            sentencia.setLong(indice++, idUsuario);
        }
        if (tipoEntidad != null) {
            sentencia.setString(indice++, tipoEntidad);
        }
        if (operacionParcial != null) {
            sentencia.setString(indice++, "%" + operacionParcial + "%");
            String[] codigosArray = codigosPorNombreLegible == null ? new String[0]
                    : codigosPorNombreLegible.toArray(new String[0]);
            sentencia.setArray(indice++, sentencia.getConnection().createArrayOf("text", codigosArray));
        }
        return indice;
    }

    /** Punto 12 (pedido de Joel): "quién rechazó" en el historial de /reversiones -- RECHAZADA no
     * guarda un usuario propio en reversiones_pago (solo motivo_rechazo), así que se recupera de
     * la propia auditoría de la operación RECHAZAR_REVERSION (ya se registra ahí, D-93). Devuelve
     * el nombre completo, o null si no se encontró (nunca debería pasar con datos reales). */
    public String obtenerNombreUsuarioDeOperacion(Connection conexion, String operacion, String tipoEntidad,
            long idEntidad) throws SQLException {
        String sql = "SELECT u.nombres, u.apellidos FROM auditoria_operaciones a "
                + "JOIN usuarios u ON u.id_usuario = a.id_usuario "
                + "WHERE a.operacion = ? AND a.tipo_entidad = ? AND a.id_entidad = ? "
                + "ORDER BY a.fecha_hora DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, operacion);
            sentencia.setString(2, tipoEntidad);
            sentencia.setLong(3, idEntidad);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? resultado.getString("nombres") + " " + resultado.getString("apellidos") : null;
            }
        }
    }

    /** RF03/RF04 (pedido de Joel): fecha/hora y valor (jsonDeValor) de la última vez que se
     * registró "operacion" para esta entidad -- sin tabla nueva. null si nunca se registró.
     * Reutilizable (no es específico de autorización al buró), mismo criterio que
     * obtenerNombreUsuarioDeOperacion. */
    public py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria obtenerUltima(Connection conexion,
            String operacion, String tipoEntidad, long idEntidad) throws SQLException {
        String sql = "SELECT fecha_hora, valor_nuevo, id_usuario FROM auditoria_operaciones "
                + "WHERE operacion = ? AND tipo_entidad = ? AND id_entidad = ? "
                + "ORDER BY fecha_hora DESC LIMIT 1";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, operacion);
            sentencia.setString(2, tipoEntidad);
            sentencia.setLong(3, idEntidad);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                // Corrección (pedido de Joel, 10/10/2026): wasNull() refleja el último getXxx()
                // llamado -- si se lo llama después de leer valor_nuevo/fecha_hora, dice si ESOS
                // son null, no id_usuario (bug real encontrado por Joel: una fila con id_usuario
                // = 4 y valor_nuevo NULL mostraba "Enviada por -" igual). Se lee y se guarda la
                // nulidad de id_usuario ANTES de tocar cualquier otra columna.
                long idUsuario = resultado.getLong("id_usuario");
                boolean idUsuarioEsNulo = resultado.wasNull();
                return new py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria(
                        resultado.getTimestamp("fecha_hora").toLocalDateTime(),
                        extraerValor(resultado.getString("valor_nuevo")),
                        idUsuarioEsNulo ? null : idUsuario);
            }
        }
    }

    /** Inverso de jsonDeValor: de {"valor":"..."} (con el mismo escape básico) vuelve a la
     * cadena original. null si json es null o no tiene esa forma. */
    private static String extraerValor(String json) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher coincidencia =
                java.util.regex.Pattern.compile("\"valor\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!coincidencia.find()) {
            return null;
        }
        return coincidencia.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private PGobject aJsonb(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        PGobject objeto = new PGobject();
        objeto.setType("jsonb");
        objeto.setValue(json);
        return objeto;
    }

    /** Arma {"valor":"..."} con escape basico, para snapshots simples de un parametro. */
    public static String jsonDeValor(String valor) {
        if (valor == null) {
            return null;
        }
        String escapado = valor.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"valor\":\"" + escapado + "\"}";
    }
}
