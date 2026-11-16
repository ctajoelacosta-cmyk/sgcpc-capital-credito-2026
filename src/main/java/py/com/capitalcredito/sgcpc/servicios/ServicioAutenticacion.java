package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.mindrot.jbcrypt.BCrypt;
import py.com.capitalcredito.sgcpc.dao.AuditoriaDAO;
import py.com.capitalcredito.sgcpc.dao.UsuarioDAO;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso.Tipo;
import py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P01 (Doc. 00 §12 / Doc. 10 §10.5), RF36/RNF09. intentarAcceso es puro: recibe
 * el usuario ya leido de la base (null si el nombre de usuario no existe)
 * y decide, sin tocar JDBC. Quien orquesta (con UsuarioDAO/AuditoriaDAO,
 * en dao/servicios que si conectan) persiste intentosFallidosNuevos /
 * estadoCuentaNuevo del resultado y registra la auditoria, todo en la
 * misma transaccion.
 */
public class ServicioAutenticacion {

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    /** RF36/RF37 (hallazgo de Joel): un bloqueo automático (intentos fallidos) escribe esta
     * operación en auditoria_operaciones; un bloqueo manual (administrador, ABM Usuarios)
     * escribe BLOQUEAR_USUARIO -- ya están distinguidos, no hace falta ninguna columna nueva. */
    private static final String OPERACION_BLOQUEO_AUTOMATICO = "CUENTA_BLOQUEADA";
    private static final String OPERACION_BLOQUEO_MANUAL = "BLOQUEAR_USUARIO";
    private static final String OPERACION_DESBLOQUEO_AUTOMATICO = "CUENTA_DESBLOQUEADA_AUTOMATICAMENTE";

    /**
     * RNF09 (hallazgo de Joel durante la prueba del bloqueo temporal): un hash de BCrypt válido,
     * pero que no corresponde a ninguna cuenta real -- se usa SOLO para que un usuario inexistente
     * tarde lo mismo que uno real (BCrypt es deliberadamente lento; sin este chequeo, el usuario
     * inexistente respondía casi al instante, un canal lateral de tiempo que permitía adivinar qué
     * nombres de usuario existen aunque el mensaje sea igual). El resultado de este chequeo nunca
     * se usa -- solo gastar el mismo tiempo.
     */
    private static final String HASH_DUMMY_PARA_USUARIO_INEXISTENTE =
            "$2a$10$YxVWCcDb.ZIR6qQlJNWgGuus1UzjkuvKwyd.EEyvmxu8T8zDo1dIO";

    /**
     * RNF09 (hallazgo de Joel): un solo mensaje, textualmente idéntico, para los 5 casos que antes
     * se distinguían (usuario inexistente, contraseña incorrecta, se acaba de bloquear, ya estaba
     * bloqueada, y contraseña correcta pero la cuenta está bloqueada) -- cualquier texto distinto
     * entre ellos le permite a quien prueba contraseñas al azar deducir si una cuenta existe o si
     * está bloqueada. El límite de intentos y los minutos de bloqueo son información general de la
     * política (el mismo texto para todos, D-45), nunca un dato de esta cuenta puntual (ni su
     * conteo real de intentos, ni si está bloqueada ahora mismo).
     */
    public static String mensajeAccesoRechazado(int limiteIntentos, int minutosBloqueo) {
        return "Usuario o contraseña incorrectos. Por seguridad, después de "
                + formatoPlural(limiteIntentos, "intento fallido", "intentos fallidos") + " la cuenta se bloquea por "
                + formatoPlural(minutosBloqueo, "minuto", "minutos")
                + ". Si olvidó su contraseña, pídasela a un administrador.";
    }

    /** RF36/RF37 (hallazgo de Joel): "1 minuto", nunca "1 minutos" -- mismo criterio para
     * cualquier cantidad (intentos, minutos). Pura. */
    public static String formatoPlural(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    public static final String MENSAJE_DEBE_CAMBIAR_CONTRASENA =
            "Debe cambiar su contraseña antes de continuar.";
    public static final String MENSAJE_ACCESO_CONCEDIDO = "Acceso concedido.";

    public ResultadoAcceso intentarAcceso(Usuario usuarioActual, String contrasenaIngresada, int limiteIntentos,
            int minutosBloqueo) {
        if (usuarioActual == null) {
            // Nunca se usa el resultado -- solo tarda lo mismo que un BCrypt.checkpw real (ver
            // HASH_DUMMY_PARA_USUARIO_INEXISTENTE).
            BCrypt.checkpw(contrasenaIngresada == null ? "" : contrasenaIngresada, HASH_DUMMY_PARA_USUARIO_INEXISTENTE);
            return new ResultadoAcceso(Tipo.CREDENCIALES_INVALIDAS,
                    mensajeAccesoRechazado(limiteIntentos, minutosBloqueo), null, null);
        }

        if (usuarioActual.getEstadoCuenta() == EstadoCuenta.BLOQUEADA) {
            // Punto 4 del hallazgo: ni siquiera se evalúa la contraseña -- si fuera la correcta,
            // tampoco entraría ni se diría que la cuenta está bloqueada (mismo mensaje genérico).
            return new ResultadoAcceso(Tipo.CUENTA_BLOQUEADA, mensajeAccesoRechazado(limiteIntentos, minutosBloqueo),
                    null, null);
        }

        if (usuarioActual.getEstadoCuenta() == EstadoCuenta.INACTIVA) {
            return new ResultadoAcceso(Tipo.CREDENCIALES_INVALIDAS,
                    mensajeAccesoRechazado(limiteIntentos, minutosBloqueo), null, null);
        }

        boolean contrasenaCorrecta = BCrypt.checkpw(
                contrasenaIngresada == null ? "" : contrasenaIngresada, usuarioActual.getContrasenaHash());

        if (!contrasenaCorrecta) {
            int intentosNuevos = usuarioActual.getIntentosFallidos() + 1;
            if (intentosNuevos >= limiteIntentos) {
                return new ResultadoAcceso(Tipo.CUENTA_BLOQUEADA, mensajeAccesoRechazado(limiteIntentos, minutosBloqueo),
                        intentosNuevos, EstadoCuenta.BLOQUEADA);
            }
            return new ResultadoAcceso(Tipo.CREDENCIALES_INVALIDAS,
                    mensajeAccesoRechazado(limiteIntentos, minutosBloqueo), intentosNuevos, null);
        }

        if (usuarioActual.isDebeCambiarContrasena()) {
            return new ResultadoAcceso(Tipo.DEBE_CAMBIAR_CONTRASENA, MENSAJE_DEBE_CAMBIAR_CONTRASENA, 0, null);
        }

        return new ResultadoAcceso(Tipo.ACCESO_CONCEDIDO, MENSAJE_ACCESO_CONCEDIDO, 0, null);
    }

    /**
     * RF36/RF37 (hallazgo de Joel): decide si un bloqueo BLOQUEADA actual se libera por tiempo.
     * Pura -- recibe ya resueltos los instantes de la última vez que se bloqueó automáticamente
     * (null si nunca) y manualmente (null si nunca); el llamador (desbloquearPorTiempoSiCorresponde)
     * los lee de auditoria_operaciones. Si nunca hubo un bloqueo automático, o si el bloqueo
     * manual es igual o más reciente que el automático, el bloqueo actual es manual (o no
     * corresponde liberar): nunca se libera solo, sin importar cuánto tiempo pase.
     */
    public boolean debeDesbloquearPorTiempo(LocalDateTime ultimoBloqueoAutomatico, LocalDateTime ultimoBloqueoManual,
            LocalDateTime ahora, int minutosBloqueo) {
        if (ultimoBloqueoAutomatico == null) {
            return false;
        }
        if (ultimoBloqueoManual != null && !ultimoBloqueoManual.isBefore(ultimoBloqueoAutomatico)) {
            return false;
        }
        long minutosTranscurridos = Duration.between(ultimoBloqueoAutomatico, ahora).toMinutes();
        return minutosTranscurridos >= minutosBloqueo;
    }

    /**
     * Orquesta un intento de acceso completo: lee el usuario y el limite de
     * intentos, decide con intentarAcceso, persiste lo que corresponda y
     * audita, todo en una transaccion (REGLAS_DESARROLLO.md §5). Si el usuario no
     * existe no se audita (no hay id_usuario valido para la FK de
     * auditoria_operaciones): no hay una cuenta real a la que atribuirle
     * el intento.
     */
    public ResultadoAcceso autenticar(String nombreUsuario, String contrasenaIngresada, String direccionIp,
            String navegador) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Usuario usuarioActual = usuarioDAO.buscarPorNombreUsuario(conexion, normalizarNombreUsuario(nombreUsuario));
                int limiteIntentos = servicioParametros.obtenerValorEntero(conexion, "seguridad.intentos_login");
                int minutosBloqueo = servicioParametros.obtenerValorEntero(conexion, "seguridad.minutos_bloqueo");

                if (usuarioActual != null && usuarioActual.getEstadoCuenta() == EstadoCuenta.BLOQUEADA) {
                    desbloquearPorTiempoSiCorresponde(conexion, usuarioActual, minutosBloqueo, direccionIp, navegador);
                }

                ResultadoAcceso resultado = intentarAcceso(usuarioActual, contrasenaIngresada, limiteIntentos,
                        minutosBloqueo);

                if (usuarioActual != null) {
                    persistirYAuditar(conexion, usuarioActual, resultado, limiteIntentos, direccionIp, navegador);
                } else {
                    // Bloque de seguridad (hallazgo de Joel, decisión nueva -- cambia D-27): antes
                    // no se auditaba nada acá (sin id_usuario real, no había FK válida). Ahora
                    // id_usuario queda NULL y se guarda el nombre tal cual se escribió, recortado.
                    servicioAuditoria.registrarIntentoUsuarioInexistente(conexion,
                            recortarUsuarioEscrito(nombreUsuario), direccionIp, navegador,
                            "Intento de acceso con un usuario que no existe.");
                }

                conexion.commit();
                return resultado;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private static final int LONGITUD_MAXIMA_USUARIO_ESCRITO = 60;

    /** Recorta lo que se escribió en el campo "usuario" a una longitud razonable antes de
     * guardarlo -- es texto que no controla la aplicación (puede venir de cualquier largo). */
    private static String recortarUsuarioEscrito(String nombreUsuario) {
        if (nombreUsuario == null) {
            return null;
        }
        String recortado = nombreUsuario.trim();
        return recortado.length() > LONGITUD_MAXIMA_USUARIO_ESCRITO
                ? recortado.substring(0, LONGITUD_MAXIMA_USUARIO_ESCRITO) : recortado;
    }

    /**
     * RF36/RF37 (hallazgo de Joel): si el bloqueo actual es automático (no manual) y ya pasaron
     * seguridad.minutos_bloqueo minutos, lo libera en esta misma transacción, antes de evaluar la
     * contraseña -- así, si la contraseña es correcta, el acceso se concede en el mismo intento
     * (no hace falta "un intento para desbloquear y otro para entrar"). Muta usuarioActual (en
     * memoria) para que intentarAcceso ya lo vea ACTIVA con 0 intentos.
     */
    private void desbloquearPorTiempoSiCorresponde(Connection conexion, Usuario usuarioActual, int minutosBloqueo,
            String direccionIp, String navegador) throws SQLException {
        long idUsuario = usuarioActual.getIdUsuario();
        UltimaOperacionAuditoria ultimoAutomatico =
                auditoriaDAO.obtenerUltima(conexion, OPERACION_BLOQUEO_AUTOMATICO, "USUARIO", idUsuario);
        UltimaOperacionAuditoria ultimoManual =
                auditoriaDAO.obtenerUltima(conexion, OPERACION_BLOQUEO_MANUAL, "USUARIO", idUsuario);
        boolean libera = debeDesbloquearPorTiempo(
                ultimoAutomatico == null ? null : ultimoAutomatico.getFechaHora(),
                ultimoManual == null ? null : ultimoManual.getFechaHora(),
                Reloj.ahora(), minutosBloqueo);
        if (!libera) {
            return;
        }
        usuarioDAO.actualizarTrasIntentoFallido(conexion, idUsuario, 0, EstadoCuenta.ACTIVA);
        servicioAuditoria.registrar(conexion, idUsuario, OPERACION_DESBLOQUEO_AUTOMATICO, "USUARIO", idUsuario,
                direccionIp, navegador, "Se liberó el bloqueo automático después de "
                        + formatoPlural(minutosBloqueo, "minuto", "minutos") + ".");
        usuarioActual.setEstadoCuenta(EstadoCuenta.ACTIVA);
        usuarioActual.setIntentosFallidos(0);
    }

    private void persistirYAuditar(Connection conexion, Usuario usuarioActual, ResultadoAcceso resultado,
            int limiteIntentos, String direccionIp, String navegador) throws SQLException {
        long idUsuario = usuarioActual.getIdUsuario();
        switch (resultado.getTipo()) {
            case ACCESO_CONCEDIDO:
            case DEBE_CAMBIAR_CONTRASENA:
                usuarioDAO.registrarAccesoExitoso(conexion, idUsuario);
                servicioAuditoria.registrar(conexion, idUsuario, "ACCESO_CONCEDIDO", "USUARIO", idUsuario,
                        direccionIp, navegador, "Inicio de sesión.");
                break;
            case CUENTA_BLOQUEADA:
                if (resultado.getIntentosFallidosNuevos() != null) {
                    // se acaba de bloquear en este intento
                    usuarioDAO.actualizarTrasIntentoFallido(conexion, idUsuario,
                            resultado.getIntentosFallidosNuevos(), resultado.getEstadoCuentaNuevo());
                    servicioAuditoria.registrar(conexion, idUsuario, "CUENTA_BLOQUEADA", "USUARIO", idUsuario,
                            direccionIp, navegador, "Se alcanzó el límite de "
                                    + formatoPlural(limiteIntentos, "intento fallido", "intentos fallidos") + ".");
                } else {
                    // ya estaba bloqueada de antes
                    servicioAuditoria.registrar(conexion, idUsuario, "ACCESO_CUENTA_BLOQUEADA", "USUARIO", idUsuario,
                            direccionIp, navegador, "Intento de acceso a una cuenta bloqueada.");
                }
                break;
            case CREDENCIALES_INVALIDAS:
                if (resultado.getIntentosFallidosNuevos() != null) {
                    usuarioDAO.actualizarTrasIntentoFallido(conexion, idUsuario,
                            resultado.getIntentosFallidosNuevos(), null);
                    servicioAuditoria.registrar(conexion, idUsuario, "ACCESO_FALLIDO", "USUARIO", idUsuario,
                            direccionIp, navegador, "Contraseña incorrecta (intento "
                                    + resultado.getIntentosFallidosNuevos() + " de " + limiteIntentos + ").");
                }
                // usuario INACTIVA: sin cambios, se audita igual para dejar rastro del intento
                else {
                    servicioAuditoria.registrar(conexion, idUsuario, "ACCESO_CUENTA_INACTIVA", "USUARIO", idUsuario,
                            direccionIp, navegador, "Intento de acceso a una cuenta inactiva.");
                }
                break;
            default:
                break;
        }
    }

    /** Para guardar en sesion tras un acceso concedido (perfil, para el menu y FiltroAutorizacion). */
    public Usuario buscarUsuarioParaSesion(String nombreUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return usuarioDAO.buscarPorNombreUsuario(conexion, normalizarNombreUsuario(nombreUsuario));
        }
    }

    /** El nombre de usuario se guarda siempre en minúsculas y sin espacios (punto 5); la búsqueda se normaliza igual. */
    private static String normalizarNombreUsuario(String nombreUsuario) {
        return nombreUsuario == null ? null : nombreUsuario.trim().toLowerCase().replaceAll("\\s+", "");
    }

    public void registrarCierreSesion(long idUsuario, String direccionIp, String navegador) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioAuditoria.registrar(conexion, idUsuario, "CERRAR_SESION", "USUARIO", idUsuario, direccionIp,
                    navegador, "Cierre de sesión.");
        }
    }

    /** Bloque de seguridad (hallazgo de Joel): tras varios fallos seguidos de la contraseña
     * actual al cambiarla, ControladorCambioContrasenaServlet cierra la sesión -- se audita
     * aparte de cada CAMBIAR_CONTRASENA_FALLIDO individual, como su propio evento. */
    public void registrarCierreSesionPorIntentosFallidos(long idUsuario, String direccionIp, String navegador)
            throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioAuditoria.registrar(conexion, idUsuario, "CERRAR_SESION_POR_INTENTOS_FALLIDOS", "USUARIO",
                    idUsuario, direccionIp, navegador,
                    "Se cerró la sesión tras varios intentos fallidos de la contraseña actual al cambiarla.");
        }
    }

    /**
     * Punto pedido tras probar E2: verifica que la contraseña actual escrita
     * coincida con el hash guardado (cambio voluntario, no solo el
     * obligatorio del primer ingreso). Pura — un rechazo acá nunca toca
     * intentos_fallidos ni bloquea la cuenta: eso solo lo hace el flujo de
     * login (autenticar/persistirYAuditar), completamente separado.
     */
    public void validarContrasenaActual(String contrasenaActual, String hashActual) {
        boolean correcta = BCrypt.checkpw(contrasenaActual == null ? "" : contrasenaActual, hashActual);
        if (!correcta) {
            throw new py.com.capitalcredito.sgcpc.excepciones.ContrasenaActualIncorrectaException();
        }
    }

    /** Confirma que la contraseña nueva y su confirmación coincidan (cambio propio). Pura. */
    public void validarConfirmacionCoincide(String contrasenaNueva, String contrasenaConfirmacion) {
        if (contrasenaNueva == null || !contrasenaNueva.equals(contrasenaConfirmacion)) {
            throw new NegocioException("Las contraseñas ingresadas no coinciden.");
        }
    }

    /**
     * Cambio de la contraseña propia: se reutiliza para el cambio
     * obligatorio del primer ingreso/restablecimiento y para el voluntario
     * (mismo servicio, misma pantalla — no se duplica lógica). Exige la
     * contraseña actual, la nueva y su confirmación.
     */
    public void cambiarContrasenaPropia(long idUsuario, String contrasenaActual, String contrasenaNueva,
            String contrasenaConfirmacion) throws SQLException {
        validarConfirmacionCoincide(contrasenaNueva, contrasenaConfirmacion);
        Usuario usuario;
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            usuario = usuarioDAO.buscarPorId(conexion, idUsuario);
        }
        try {
            validarContrasenaActual(contrasenaActual, usuario.getContrasenaHash());
        } catch (py.com.capitalcredito.sgcpc.excepciones.ContrasenaActualIncorrectaException error) {
            // Bloque de seguridad (hallazgo de Joel): se audita el intento fallido aparte, en su
            // propia transacción corta -- si esto quedara dentro de la transacción principal (más
            // abajo), un rollback por este mismo error borraría la auditoría que justamente
            // necesita quedar registrada.
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                servicioAuditoria.registrar(conexion, idUsuario, "CAMBIAR_CONTRASENA_FALLIDO", "USUARIO", idUsuario,
                        "Intentó cambiar su contraseña con la actual incorrecta.");
            }
            throw error;
        }
        new ServicioUsuarios().validarContrasenaNueva(contrasenaNueva, usuario.getContrasenaHash(), usuario.getNombreUsuario());
        String hashNuevo = BCrypt.hashpw(contrasenaNueva, BCrypt.gensalt());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                usuarioDAO.actualizarContrasena(conexion, idUsuario, hashNuevo, false);
                servicioAuditoria.registrar(conexion, idUsuario, "CAMBIAR_CONTRASENA", "USUARIO", idUsuario,
                        "Cambió su propia contraseña.");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }
}
