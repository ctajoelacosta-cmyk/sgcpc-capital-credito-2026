package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * Acceso a la tabla usuarios (bd/01_esquema.sql). SQL parametrizado, sin
 * ORM. Cada metodo recibe la Connection: la abre y la transacciona quien
 * llama (el Servicio), nunca el DAO (Doc. 00 §15).
 */
public class UsuarioDAO {

    public Usuario buscarPorNombreUsuario(Connection conexion, String nombreUsuario) throws SQLException {
        String sql = "SELECT id_usuario, nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad, "
                + "perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena, "
                + "fecha_ultimo_acceso, fecha_cambio_contrasena, fecha_creacion, creado_por, fecha_inactivacion "
                + "FROM usuarios WHERE nombre_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nombreUsuario);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public Usuario buscarPorId(Connection conexion, long idUsuario) throws SQLException {
        String sql = "SELECT id_usuario, nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad, "
                + "perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena, "
                + "fecha_ultimo_acceso, fecha_cambio_contrasena, fecha_creacion, creado_por, fecha_inactivacion "
                + "FROM usuarios WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuario);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** "Editar usuario" (plan aprobado por Joel) y alta: para el aviso claro de cédula
     * duplicada, en vez de dejar que reviente la restricción UNIQUE de la base como un
     * SQLException genérico. */
    public Usuario buscarPorCedula(Connection conexion, String cedulaIdentidad) throws SQLException {
        String sql = "SELECT id_usuario, nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad, "
                + "perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena, "
                + "fecha_ultimo_acceso, fecha_cambio_contrasena, fecha_creacion, creado_por, fecha_inactivacion "
                + "FROM usuarios WHERE cedula_identidad = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cedulaIdentidad);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public List<Usuario> listar(Connection conexion) throws SQLException {
        String sql = "SELECT id_usuario, nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad, "
                + "perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena, "
                + "fecha_ultimo_acceso, fecha_cambio_contrasena, fecha_creacion, creado_por, fecha_inactivacion "
                + "FROM usuarios ORDER BY nombre_usuario";
        List<Usuario> usuarios = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                usuarios.add(mapear(resultado));
            }
        }
        return usuarios;
    }

    // D-151 (pedido de Joel): deja sin efecto, para esta guarda, lo que decidía D7 -- ya NO
    // cuenta GERENTE_ADMINISTRADOR (el gerente perdió el acceso a Usuarios por completo; "no
    // alcanza con un gerente"). Solo ADMINISTRADOR activo cuenta para la regla "debe quedar al
    // menos uno". Antes se llamaba contarGerentesActivos.
    public long contarAdministradoresActivos(Connection conexion) throws SQLException {
        String sql = "SELECT count(*) FROM usuarios "
                + "WHERE perfil_usuario = 'ADMINISTRADOR' AND estado_cuenta = 'ACTIVA'";
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    public long insertar(Connection conexion, Usuario usuario) throws SQLException {
        String sql = "INSERT INTO usuarios (nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad, "
                + "perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena, fecha_creacion, creado_por) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, TRUE, now(), ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, usuario.getNombreUsuario());
            sentencia.setString(2, usuario.getContrasenaHash());
            sentencia.setString(3, usuario.getNombres());
            sentencia.setString(4, usuario.getApellidos());
            sentencia.setString(5, usuario.getCedulaIdentidad());
            sentencia.setString(6, usuario.getPerfilUsuario().name());
            sentencia.setString(7, EstadoCuenta.ACTIVA.name());
            sentencia.setLong(8, usuario.getCreadoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    /** "Editar usuario" (plan aprobado por Joel): nombres, apellidos, cédula y perfil -- nunca
     * nombre_usuario (login) ni contrasena_hash, que tienen su propio camino (restablecer). */
    public void actualizarDatos(Connection conexion, Usuario usuario) throws SQLException {
        String sql = "UPDATE usuarios SET nombres = ?, apellidos = ?, cedula_identidad = ?, perfil_usuario = ? "
                + "WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, usuario.getNombres());
            sentencia.setString(2, usuario.getApellidos());
            sentencia.setString(3, usuario.getCedulaIdentidad());
            sentencia.setString(4, usuario.getPerfilUsuario().name());
            sentencia.setLong(5, usuario.getIdUsuario());
            sentencia.executeUpdate();
        }
    }

    /** Tras un intento de acceso fallido (o que deja la cuenta bloqueada). */
    public void actualizarTrasIntentoFallido(Connection conexion, long idUsuario, int intentosFallidosNuevos,
            EstadoCuenta estadoCuentaNuevo) throws SQLException {
        String sql = "UPDATE usuarios SET intentos_fallidos = ?, estado_cuenta = ? WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, intentosFallidosNuevos);
            sentencia.setString(2, (estadoCuentaNuevo == null ? EstadoCuenta.ACTIVA : estadoCuentaNuevo).name());
            sentencia.setLong(3, idUsuario);
            sentencia.executeUpdate();
        }
    }

    /** Acceso concedido: resetea intentos y marca fecha_ultimo_acceso. */
    public void registrarAccesoExitoso(Connection conexion, long idUsuario) throws SQLException {
        String sql = "UPDATE usuarios SET intentos_fallidos = 0, fecha_ultimo_acceso = now() WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idUsuario);
            sentencia.executeUpdate();
        }
    }

    public void actualizarContrasena(Connection conexion, long idUsuario, String nuevoHash,
            boolean debeCambiarContrasena) throws SQLException {
        String sql = "UPDATE usuarios SET contrasena_hash = ?, debe_cambiar_contrasena = ?, "
                + "fecha_cambio_contrasena = now() WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nuevoHash);
            sentencia.setBoolean(2, debeCambiarContrasena);
            sentencia.setLong(3, idUsuario);
            sentencia.executeUpdate();
        }
    }

    public void actualizarEstadoCuenta(Connection conexion, long idUsuario, EstadoCuenta nuevoEstado)
            throws SQLException {
        String sql = "UPDATE usuarios SET estado_cuenta = ?, intentos_fallidos = 0";
        boolean esInactivacion = nuevoEstado == EstadoCuenta.INACTIVA;
        if (esInactivacion) {
            sql += ", fecha_inactivacion = now()";
        }
        sql += " WHERE id_usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nuevoEstado.name());
            sentencia.setLong(2, idUsuario);
            sentencia.executeUpdate();
        }
    }

    private Usuario mapear(ResultSet resultado) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(resultado.getLong("id_usuario"));
        usuario.setNombreUsuario(resultado.getString("nombre_usuario"));
        usuario.setContrasenaHash(resultado.getString("contrasena_hash"));
        usuario.setNombres(resultado.getString("nombres"));
        usuario.setApellidos(resultado.getString("apellidos"));
        usuario.setCedulaIdentidad(resultado.getString("cedula_identidad"));
        usuario.setPerfilUsuario(PerfilUsuario.valueOf(resultado.getString("perfil_usuario")));
        usuario.setEstadoCuenta(EstadoCuenta.valueOf(resultado.getString("estado_cuenta")));
        usuario.setIntentosFallidos(resultado.getInt("intentos_fallidos"));
        usuario.setDebeCambiarContrasena(resultado.getBoolean("debe_cambiar_contrasena"));
        usuario.setFechaUltimoAcceso(aLocalDateTime(resultado.getTimestamp("fecha_ultimo_acceso")));
        usuario.setFechaCambioContrasena(aLocalDateTime(resultado.getTimestamp("fecha_cambio_contrasena")));
        usuario.setFechaCreacion(aLocalDateTime(resultado.getTimestamp("fecha_creacion")));
        long creadoPor = resultado.getLong("creado_por");
        usuario.setCreadoPor(resultado.wasNull() ? null : creadoPor);
        usuario.setFechaInactivacion(aLocalDateTime(resultado.getTimestamp("fecha_inactivacion")));
        return usuario;
    }

    private static java.time.LocalDateTime aLocalDateTime(Timestamp marca) {
        return marca == null ? null : marca.toLocalDateTime();
    }
}
