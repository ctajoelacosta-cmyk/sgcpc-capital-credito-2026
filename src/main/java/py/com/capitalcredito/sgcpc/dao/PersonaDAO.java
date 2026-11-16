package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPersona;
import py.com.capitalcredito.sgcpc.modelos.Persona;

/**
 * Acceso a la tabla personas (bd/01_esquema.sql / Doc. 08 Tabla 04). SQL
 * parametrizado, sin ORM. Cada metodo recibe la Connection: la abre y la
 * transacciona quien llama (el Servicio), nunca el DAO (Doc. 00 §15).
 */
public class PersonaDAO {

    private static final String COLUMNAS = "id_persona, cedula_identidad, ruc, nombres, apellidos, "
            + "fecha_nacimiento, estado_civil, telefono_whatsapp, "
            + "correo_electronico, estado_persona, registrado_por, "
            + "fecha_registro, fecha_actualizacion, telefono_alternativo, "
            + "fecha_vencimiento_cedula";

    /** cedulaNormalizada: ya sin puntos/espacios/guiones (ServicioClientes.normalizarCedula). */
    public Persona buscarPorCedula(Connection conexion, String cedulaNormalizada) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM personas WHERE cedula_identidad = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cedulaNormalizada);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public Persona buscarPorId(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM personas WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** Bloque 7/E10, Tanda D (D-128, informe "Clientes nuevos" desde-hasta): altas en el rango. */
    public List<Persona> listarPorRangoFechaRegistro(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM personas WHERE fecha_registro::date BETWEEN ? AND ? "
                + "ORDER BY fecha_registro";
        List<Persona> personas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    personas.add(mapear(resultado));
                }
            }
        }
        return personas;
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 31): listado paginado, ordenado por apellido -- ABM completo de
     * Clientes (antes solo había buscador). filtroEstado: "ACTIVA"/"INACTIVA"/null (todos).
     */
    /**
     * Bloque 7/E10 (D-135, pedido de Joel): listado único con todos los filtros de /clientes --
     * texto, estado, fecha de alta, departamento/ciudad, situación laboral y préstamo activo --
     * la MISMA consulta que usan el listado en pantalla, el informe PDF y el CSV (paginaOTexto
     * null = sin LIMIT/OFFSET, para el informe/CSV, que necesitan TODAS las filas que calzan).
     */
    public List<Persona> listarConFiltros(Connection conexion, FiltroClientes filtro, Integer pagina, Integer porPagina)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM personas " + construirWhere(filtro)
                + construirOrderBy(filtro)
                + (pagina != null && porPagina != null ? " LIMIT ? OFFSET ?" : "");
        List<Persona> personas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            int indice = enlazarParametros(sentencia, filtro, 1);
            if (pagina != null && porPagina != null) {
                sentencia.setInt(indice++, porPagina);
                sentencia.setInt(indice, (pagina - 1) * porPagina);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    personas.add(mapear(resultado));
                }
            }
        }
        return personas;
    }

    /**
     * B157 (14/11/2026): las ÚNICAS cuatro formas de ordenar el listado de clientes, como lista
     * blanca de clave → fragmento SQL. Nada de lo que llegue del navegador entra en la consulta:
     * una clave desconocida (o null) cae en el orden de siempre, {@code apellidos, nombres}.
     *
     * <p><b>Por qué solo cuatro</b>: son las columnas que viven en {@code personas}. "Ciudad",
     * "Situación laboral" y "Préstamos activos" NO salen de esta consulta — los resuelve
     * {@code ControladorClientesServlet.mostrarLista} con tres mapas aparte—, así que ordenar por
     * ellas exigiría JOINs en una consulta que comparten la pantalla, el informe PDF y el CSV.
     * Decisión de Joel (14/11/2026): esas tres se quedan sin control de orden, y la pantalla
     * tampoco lo ofrece — un encabezado que parece clickeable y no ordena es peor que uno que no
     * lo parece.</p>
     */
    private static final Map<String, String> ORDEN_PERMITIDO;

    static {
        Map<String, String> permitido = new HashMap<>();
        permitido.put("ci", "cedula_identidad");
        permitido.put("nombre", "apellidos, nombres");
        permitido.put("telefono", "telefono_whatsapp");
        permitido.put("estado", "estado_persona");
        ORDEN_PERMITIDO = Collections.unmodifiableMap(permitido);
    }

    /** La clave del orden por defecto: el de siempre, apellido y después nombre. */
    public static final String ORDEN_POR_DEFECTO = "nombre";

    /** Las claves que la pantalla puede ofrecer; cualquier otra cosa es un error de programación. */
    public static boolean esOrdenPermitido(String clave) {
        return clave != null && ORDEN_PERMITIDO.containsKey(clave);
    }

    /**
     * El {@code ORDER BY} de {@link #listarConFiltros}, armado SOLO con fragmentos de
     * {@link #ORDEN_PERMITIDO} — nunca con texto del pedido. El sentido admite exactamente
     * "desc"; cualquier otro valor (incluido null) es ascendente.
     *
     * <p>El desempate por {@code apellidos, nombres} va siempre que no se esté ordenando ya por
     * ahí: sin él, dos clientes con el mismo estado (o el mismo teléfono vacío) quedan en un
     * orden que PostgreSQL puede cambiar entre dos consultas, y con LIMIT/OFFSET eso se ve como
     * una fila repetida o faltante al pasar de página.</p>
     */
    static String construirOrderBy(FiltroClientes filtro) {
        String clave = filtro == null ? null : filtro.getOrden();
        String columnas = ORDEN_PERMITIDO.get(clave);
        if (columnas == null) {
            columnas = ORDEN_PERMITIDO.get(ORDEN_POR_DEFECTO);
            clave = ORDEN_POR_DEFECTO;
        }
        boolean descendente = filtro != null && "desc".equalsIgnoreCase(filtro.getSentido());
        String orderBy = "ORDER BY " + columnas + (descendente ? " DESC" : " ASC");
        if (!ORDEN_POR_DEFECTO.equals(clave)) {
            orderBy += ", apellidos ASC, nombres ASC";
        }
        return orderBy;
    }

    /** Mismo filtro que listarConFiltros, para la paginación en pantalla. */
    public int contarConFiltros(Connection conexion, FiltroClientes filtro) throws SQLException {
        String sql = "SELECT count(*) FROM personas " + construirWhere(filtro);
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            enlazarParametros(sentencia, filtro, 1);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    /** Sin extensión "unaccent" (no se toca la estructura de sgcpc_db): translate() ya viene con
     * PostgreSQL. Bloque 7/E10 (D-136, pedido de Joel): la búsqueda en vivo tiene que encontrar
     * "jose"/"José" por igual -- ILIKE ya ignora mayúsculas, esto además ignora tildes/diéresis. */
    private static final String ACENTOS_ORIGEN = "áéíóúÁÉÍÓÚñÑüÜ";
    private static final String ACENTOS_DESTINO = "aeiouAEIOUnNuU";

    private String sinAcentos(String expresion) {
        return "translate(" + expresion + ", '" + ACENTOS_ORIGEN + "', '" + ACENTOS_DESTINO + "')";
    }

    private String construirWhere(FiltroClientes filtro) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
        if (filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty()) {
            // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 0): además
            // de cada columna por separado, "nombres apellidos" juntos -- "Miguel Sosa" no es
            // substring de "Miguel" ni de "Sosa" por separado, pero sí de la concatenación. Y al
            // revés también ("apellidos nombres", como se lee la columna "Apellidos y Nombres").
            where.append("AND (cedula_identidad LIKE ? OR ").append(sinAcentos("nombres")).append(" ILIKE ")
                    .append(sinAcentos("?")).append(" OR ").append(sinAcentos("apellidos")).append(" ILIKE ")
                    .append(sinAcentos("?")).append(" OR ").append(sinAcentos("nombres || ' ' || apellidos")).append(" ILIKE ")
                    .append(sinAcentos("?")).append(" OR ").append(sinAcentos("apellidos || ' ' || nombres")).append(" ILIKE ")
                    .append(sinAcentos("?")).append(") ");
        }
        if (filtro.getEstado() != null) {
            where.append("AND estado_persona = ? ");
        }
        if (filtro.getAltaDesde() != null) {
            where.append("AND fecha_registro::date >= ? ");
        }
        if (filtro.getAltaHasta() != null) {
            where.append("AND fecha_registro::date <= ? ");
        }
        // Normalización de personas, grupo Domicilio (16/10/2026): id_departamento/id_ciudad ya
        // no están en personas -- se filtra por EXISTS contra personas_domicilio (y, para
        // departamento, un JOIN a ciudades, porque el departamento solo se obtiene por ciudad).
        if (filtro.getIdDepartamento() != null) {
            where.append("AND EXISTS (SELECT 1 FROM personas_domicilio pd JOIN ciudades c ON c.id_ciudad = pd.id_ciudad "
                    + "WHERE pd.id_persona = personas.id_persona AND c.id_departamento = ?) ");
        }
        if (filtro.getIdCiudad() != null) {
            where.append("AND EXISTS (SELECT 1 FROM personas_domicilio pd "
                    + "WHERE pd.id_persona = personas.id_persona AND pd.id_ciudad = ?) ");
        }
        // Normalización de personas, grupo Laborales (16/10/2026): situacion_laboral ya no está
        // en personas -- se filtra por EXISTS contra personas_laborales (mismo patrón que
        // id_departamento/id_ciudad, grupo Domicilio).
        if (filtro.getSituacionLaboral() != null) {
            where.append("AND EXISTS (SELECT 1 FROM personas_laborales pl "
                    + "WHERE pl.id_persona = personas.id_persona AND pl.situacion_laboral = ?) ");
        }
        if ("SI".equals(filtro.getPrestamoActivo())) {
            where.append("AND EXISTS (SELECT 1 FROM prestamos pr JOIN solicitudes_credito sc "
                    + "ON sc.id_solicitud = pr.id_solicitud "
                    + "WHERE sc.id_cliente = personas.id_persona AND pr.estado_prestamo IN ('ACTIVO', 'EN_MORA')) ");
        } else if ("NO".equals(filtro.getPrestamoActivo())) {
            where.append("AND NOT EXISTS (SELECT 1 FROM prestamos pr JOIN solicitudes_credito sc "
                    + "ON sc.id_solicitud = pr.id_solicitud "
                    + "WHERE sc.id_cliente = personas.id_persona AND pr.estado_prestamo IN ('ACTIVO', 'EN_MORA')) ");
        }
        return where.toString();
    }

    private int enlazarParametros(PreparedStatement sentencia, FiltroClientes filtro, int indiceInicial)
            throws SQLException {
        int indice = indiceInicial;
        if (filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty()) {
            sentencia.setString(indice++, "%" + filtro.getCedulaNormalizada() + "%");
            sentencia.setString(indice++, "%" + filtro.getTexto().trim() + "%");
            sentencia.setString(indice++, "%" + filtro.getTexto().trim() + "%");
            sentencia.setString(indice++, "%" + filtro.getTexto().trim() + "%");
            sentencia.setString(indice++, "%" + filtro.getTexto().trim() + "%");
        }
        if (filtro.getEstado() != null) {
            sentencia.setString(indice++, filtro.getEstado());
        }
        if (filtro.getAltaDesde() != null) {
            sentencia.setDate(indice++, Date.valueOf(filtro.getAltaDesde()));
        }
        if (filtro.getAltaHasta() != null) {
            sentencia.setDate(indice++, Date.valueOf(filtro.getAltaHasta()));
        }
        if (filtro.getIdDepartamento() != null) {
            sentencia.setLong(indice++, filtro.getIdDepartamento());
        }
        if (filtro.getIdCiudad() != null) {
            sentencia.setLong(indice++, filtro.getIdCiudad());
        }
        if (filtro.getSituacionLaboral() != null) {
            sentencia.setString(indice++, filtro.getSituacionLaboral());
        }
        return indice;
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 31): baja lógica -- nunca DELETE (REGLAS_DESARROLLO.md §5). El
     * motivo/detalle/fecha/usuario de la baja viven en personas_baja desde el grupo Baja de la
     * normalización (16/10/2026, PersonaBajaDAO.marcarBaja) -- este método solo cambia el estado.
     */
    public void marcarBaja(Connection conexion, long idPersona) throws SQLException {
        String sql = "UPDATE personas SET estado_persona = 'INACTIVA', fecha_actualizacion = now() WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.executeUpdate();
        }
    }

    /** Bloque 7/E10 (D-129, Obs. 31): reactivar -- conserva el motivo/detalle/fecha/usuario de la última baja como historial. */
    public void marcarReactivada(Connection conexion, long idPersona) throws SQLException {
        String sql = "UPDATE personas SET estado_persona = 'ACTIVA', fecha_actualizacion = now() WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.executeUpdate();
        }
    }

    public long insertar(Connection conexion, Persona persona) throws SQLException {
        String sql = "INSERT INTO personas (cedula_identidad, ruc, nombres, apellidos, fecha_nacimiento, "
                + "estado_civil, telefono_whatsapp, correo_electronico, "
                + "estado_persona, registrado_por, fecha_registro, "
                + "fecha_actualizacion, telefono_alternativo, "
                + "fecha_vencimiento_cedula) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            cargarParametrosComunes(sentencia, persona);
            sentencia.setString(9, EstadoPersona.ACTIVA.name());
            sentencia.setLong(10, persona.getRegistradoPor());
            sentencia.setString(11, persona.getTelefonoAlternativo());
            setDateNullable(sentencia, 12, persona.getFechaVencimientoCedula());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public void actualizar(Connection conexion, Persona persona) throws SQLException {
        String sql = "UPDATE personas SET cedula_identidad = ?, ruc = ?, nombres = ?, apellidos = ?, "
                + "fecha_nacimiento = ?, estado_civil = ?, "
                + "telefono_whatsapp = ?, correo_electronico = ?, "
                + "fecha_actualizacion = now(), telefono_alternativo = ?, "
                + "fecha_vencimiento_cedula = ? "
                + "WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            cargarParametrosComunes(sentencia, persona);
            sentencia.setString(9, persona.getTelefonoAlternativo());
            setDateNullable(sentencia, 10, persona.getFechaVencimientoCedula());
            sentencia.setLong(11, persona.getIdPersona());
            sentencia.executeUpdate();
        }
    }

    /** Los primeros 8 parametros (cedula..correo_electronico) son iguales en insertar/actualizar. */
    private void cargarParametrosComunes(PreparedStatement sentencia, Persona persona) throws SQLException {
        sentencia.setString(1, persona.getCedulaIdentidad());
        sentencia.setString(2, persona.getRuc());
        sentencia.setString(3, persona.getNombres());
        sentencia.setString(4, persona.getApellidos());
        sentencia.setDate(5, Date.valueOf(persona.getFechaNacimiento()));
        sentencia.setString(6, persona.getEstadoCivil());
        sentencia.setString(7, persona.getTelefonoWhatsapp());
        sentencia.setString(8, persona.getCorreoElectronico());
    }

    private void setDateNullable(PreparedStatement sentencia, int indice, java.time.LocalDate valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, java.sql.Types.DATE);
        } else {
            sentencia.setDate(indice, Date.valueOf(valor));
        }
    }

    private Persona mapear(ResultSet resultado) throws SQLException {
        Persona persona = new Persona();
        persona.setIdPersona(resultado.getLong("id_persona"));
        persona.setCedulaIdentidad(resultado.getString("cedula_identidad"));
        persona.setRuc(resultado.getString("ruc"));
        persona.setNombres(resultado.getString("nombres"));
        persona.setApellidos(resultado.getString("apellidos"));
        persona.setFechaNacimiento(resultado.getDate("fecha_nacimiento").toLocalDate());
        persona.setEstadoCivil(resultado.getString("estado_civil"));
        persona.setTelefonoWhatsapp(resultado.getString("telefono_whatsapp"));
        persona.setCorreoElectronico(resultado.getString("correo_electronico"));
        persona.setEstadoPersona(EstadoPersona.valueOf(resultado.getString("estado_persona")));
        persona.setRegistradoPor(resultado.getLong("registrado_por"));
        persona.setFechaRegistro(aLocalDateTime(resultado.getTimestamp("fecha_registro")));
        persona.setFechaActualizacion(aLocalDateTime(resultado.getTimestamp("fecha_actualizacion")));
        persona.setTelefonoAlternativo(resultado.getString("telefono_alternativo"));
        java.sql.Date fechaVencimientoCedula = resultado.getDate("fecha_vencimiento_cedula");
        persona.setFechaVencimientoCedula(fechaVencimientoCedula == null ? null : fechaVencimientoCedula.toLocalDate());
        return persona;
    }

    private static LocalDateTime aLocalDateTime(Timestamp marca) {
        return marca == null ? null : marca.toLocalDateTime();
    }
}
