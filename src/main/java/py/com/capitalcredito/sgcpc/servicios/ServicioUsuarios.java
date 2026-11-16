package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.mindrot.jbcrypt.BCrypt;
import py.com.capitalcredito.sgcpc.dao.UsuarioDAO;
import py.com.capitalcredito.sgcpc.dto.ResultadoAltaUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P12, parte usuarios (Doc. 00 §12 / Doc. 10 §10.15): alta, bloqueo,
 * reactivacion, restablecimiento de contrasena. Los metodos de validacion
 * de esta clase son puros (sin DAO); los que persisten usan UsuarioDAO y
 * ServicioAuditoria en una sola transaccion.
 */
public class ServicioUsuarios {

    /** Contraseña inicial de bd/03_parametros.sql: nunca se puede reusar (punto 5). */
    private static final String CONTRASENA_INICIAL = "Cambiar@2026";
    private static final int LONGITUD_MINIMA = 8;

    /** Plan "Editar usuario" (pedido de Joel), punto 5: mismo mensaje en alta y en edición. */
    private static final String MENSAJE_CEDULA_DUPLICADA = "Ya existe otro usuario con esa cédula.";

    /** Hallazgo de Joel probando RF37 en pantalla: "Frase corta. Detalle." -- el mecanismo
     * genérico de mensajeError (plantilla/pie.jsp) lo muestra con el título en negrita y el
     * detalle debajo, sin que esta pantalla necesite nada especial. */
    private static final String MENSAJE_NOMBRE_USUARIO_DUPLICADO = "Ese nombre de usuario ya existe. Elija otro.";

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    // Tanda 2 (14/10/2026, pedido de Joel): reutiliza ServicioClientes.normalizarCedula (Ajuste
    // B) en vez de duplicar la lógica -- acepta la cédula con o sin puntos y la guarda sin ellos.
    private final ServicioClientes servicioClientes = new ServicioClientes();

    /**
     * Punto 5: minimo 8 caracteres, al menos una letra y un numero,
     * distinta de la contraseña inicial y de la actual, y que no contenga el
     * nombre de usuario (bloque de seguridad, hallazgo de Joel: "cajera123" para el
     * usuario "cajera" no debe aceptarse -- la regla anterior solo rechazaba la
     * igualdad exacta, que ya frenaba el mínimo de 8 caracteres). Pura.
     */
    public void validarContrasenaNueva(String contrasenaNueva, String hashActual, String nombreUsuario) {
        if (contrasenaNueva == null || contrasenaNueva.length() < LONGITUD_MINIMA) {
            throw new NegocioException("La contraseña debe tener al menos 8 caracteres.");
        }
        boolean tieneLetra = contrasenaNueva.chars().anyMatch(Character::isLetter);
        boolean tieneNumero = contrasenaNueva.chars().anyMatch(Character::isDigit);
        if (!tieneLetra || !tieneNumero) {
            throw new NegocioException("La contraseña debe tener al menos una letra y un número.");
        }
        if (contrasenaNueva.equals(CONTRASENA_INICIAL)) {
            throw new NegocioException("No puede usar la contraseña inicial. Elija una contraseña nueva.");
        }
        if (nombreUsuario != null && !nombreUsuario.isEmpty()
                && contrasenaNueva.toLowerCase().contains(nombreUsuario.toLowerCase())) {
            throw new NegocioException("La contraseña no puede contener el nombre de usuario.");
        }
        if (hashActual != null && BCrypt.checkpw(contrasenaNueva, hashActual)) {
            throw new NegocioException("La contraseña nueva debe ser distinta de la actual.");
        }
    }

    /**
     * Plan "Editar usuario" (pedido de Joel), punto 6: nombres y apellidos obligatorios, cédula
     * solo números, perfil válido -- mismas reglas para alta y edición. Pura.
     */
    /**
     * E2 (pedido de Joel, encontrado probando D-151): recorta espacios al principio/final --
     * se usa ANTES de validar y de comparar contra el valor anterior, para que "   Ana  " y
     * "Ana" se traten como el mismo valor (si es la única diferencia, "No hubo cambios" no debe
     * registrar auditoría). Pura; null se devuelve tal cual (lo rechaza validarDatosBasicos).
     */
    String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }

    public void validarDatosBasicos(String nombres, String apellidos, String cedulaIdentidad, PerfilUsuario perfil) {
        if (nombres == null || nombres.trim().isEmpty()) {
            throw new NegocioException("El nombre es obligatorio.");
        }
        if (apellidos == null || apellidos.trim().isEmpty()) {
            throw new NegocioException("El apellido es obligatorio.");
        }
        if (cedulaIdentidad == null || !cedulaIdentidad.matches("\\d+")) {
            throw new NegocioException("La cédula debe contener solo números.");
        }
        if (perfil == null) {
            throw new NegocioException("Debe elegir un perfil válido.");
        }
    }

    /**
     * Hallazgo de Joel probando RF37 en pantalla: mismo criterio que la cédula -- un mensaje
     * claro en vez de dejar que la restricción UNIQUE de la base (más
     * `ux_usuarios_nombre_usuario_minusculas`) reviente como un SQLException genérico. Sin
     * distinguir mayúsculas, igual que ese índice: alcanza con una comparación simple porque
     * `alta()` ya normaliza el nombre de usuario a minúsculas ANTES de buscar con él (y todo
     * nombre de usuario guardado en la base está, por construcción, siempre en minúsculas).
     * Pura, visibilidad de paquete (mismo criterio que resolverPerfilAUsar/construirTextoCambios)
     * para poder probarla directo, sin pasar por alta() (que necesita Connection).
     */
    void validarNombreUsuarioDisponible(Usuario usuarioConMismoNombre) {
        if (usuarioConMismoNombre != null) {
            throw new NegocioException(MENSAJE_NOMBRE_USUARIO_DUPLICADO);
        }
    }

    /**
     * Plan "Editar usuario" (pedido de Joel), punto 3, actualizado por D-151: si el cambio de
     * perfil saca a alguien de ADMINISTRADOR y era el único activo, se rechaza -- GERENTE_ADMINISTRADOR
     * ya NO cuenta para esta guarda (D-151 deja sin efecto, acá, lo que decidía D7: "no alcanza
     * con un gerente"). Si el perfil nuevo es igual al anterior (o si el anterior no era
     * administrador activo), no hay nada que validar. Pura (recibe el conteo ya consultado por
     * el DAO).
     */
    public void validarPuedeCambiarPerfil(Usuario anterior, PerfilUsuario perfilNuevo, long cantidadAdministradoresActivos) {
        boolean eraAdministradorActivo = anterior.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR
                && anterior.getEstadoCuenta() == EstadoCuenta.ACTIVA;
        boolean sigueSiendoAdministrador = perfilNuevo == PerfilUsuario.ADMINISTRADOR;
        if (eraAdministradorActivo && !sigueSiendoAdministrador && cantidadAdministradoresActivos <= 1) {
            throw new NegocioException("Debe quedar al menos un administrador activo.");
        }
    }

    /**
     * Punto 4, actualizado por D-151 (pedido de Joel): nadie se bloquea ni se inactiva a si
     * mismo, y siempre debe quedar al menos un ADMINISTRADOR ACTIVO -- GERENTE_ADMINISTRADOR ya
     * NO cuenta para esta guarda (D7 queda sin efecto acá: "no alcanza con un gerente"). Pura
     * (recibe el conteo ya consultado por el DAO).
     */
    public void validarPuedeBloquearseOInactivarse(Usuario objetivo, Usuario solicitante, long cantidadAdministradoresActivos) {
        if (objetivo.getIdUsuario().equals(solicitante.getIdUsuario())) {
            throw new NegocioException("No puede bloquear ni inactivar su propio usuario.");
        }
        boolean esAdministradorActivo = objetivo.getPerfilUsuario() == PerfilUsuario.ADMINISTRADOR
                && objetivo.getEstadoCuenta() == EstadoCuenta.ACTIVA;
        if (esAdministradorActivo && cantidadAdministradoresActivos <= 1) {
            throw new NegocioException("Debe quedar al menos un administrador activo.");
        }
    }

    /** E4 (ajuste 6, Joel): para mostrar quién aprobó/rechazó/canceló una solicitud. */
    public Usuario buscarPorId(Connection conexion, long idUsuario) throws SQLException {
        return usuarioDAO.buscarPorId(conexion, idUsuario);
    }

    /** Continuidad del administrador (hallazgo de Joel): para el aviso del panel -- expone el
     * mismo conteo que ya usan validarPuedeBloquearseOInactivarse/validarPuedeCambiarPerfil, sin
     * duplicar la consulta. */
    public long contarAdministradoresActivos(Connection conexion) throws SQLException {
        return usuarioDAO.contarAdministradoresActivos(conexion);
    }

    public List<Usuario> listar(Connection conexion) throws SQLException {
        return usuarioDAO.listar(conexion);
    }

    /**
     * Alta (P12). Hallazgo de Joel probando RF37 en pantalla: la cuenta nacía con la contraseña
     * inicial fija ("Cambiar@2026", la misma para todas las cuentas nuevas y escrita en el
     * propio formulario) -- cualquiera que la conociera podía entrar a una cuenta recién creada
     * antes que su dueño. Ahora genera una temporal aleatoria (mismo generador que
     * restablecerContrasena, `SecureRandom`), que el controlador muestra una sola vez al
     * administrador; igual que siempre, exige cambiarla en el próximo ingreso.
     */
    public ResultadoAltaUsuario alta(Usuario nuevoUsuario, long idUsuarioQueRegistra) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // Punto 5: el nombre de usuario se guarda siempre en minusculas y sin espacios.
                nuevoUsuario.setNombreUsuario(nuevoUsuario.getNombreUsuario().trim().toLowerCase().replaceAll("\\s+", ""));
                // E2 (pedido de Joel): recorta ANTES de validar/comparar/guardar. RF02 (pedido de
                // Joel): nombres/apellidos además con mayúscula inicial por palabra al guardar.
                nuevoUsuario.setNombres(NormalizadorTexto.normalizarNombrePropio(nuevoUsuario.getNombres()));
                nuevoUsuario.setApellidos(NormalizadorTexto.normalizarNombrePropio(nuevoUsuario.getApellidos()));
                nuevoUsuario.setCedulaIdentidad(servicioClientes.normalizarCedula(nuevoUsuario.getCedulaIdentidad()));
                // Plan "Editar usuario" (pedido de Joel), puntos 5 y 6: mismas validaciones y
                // mismo mensaje de cédula duplicada que la edición, en vez de dejar que la
                // restricción UNIQUE de la base reviente como un SQLException genérico.
                validarDatosBasicos(nuevoUsuario.getNombres(), nuevoUsuario.getApellidos(),
                        nuevoUsuario.getCedulaIdentidad(), nuevoUsuario.getPerfilUsuario());
                // Hallazgo de Joel probando RF37 en pantalla: faltaba este chequeo -- un nombre de
                // usuario repetido reventaba la restricción UNIQUE de la base como un SQLException
                // genérico ("No se pudo completar la operación"), en vez de un mensaje claro como
                // el de la cédula, de abajo.
                validarNombreUsuarioDisponible(usuarioDAO.buscarPorNombreUsuario(conexion, nuevoUsuario.getNombreUsuario()));
                if (usuarioDAO.buscarPorCedula(conexion, nuevoUsuario.getCedulaIdentidad()) != null) {
                    throw new NegocioException(MENSAJE_CEDULA_DUPLICADA);
                }
                String contrasenaTemporal = generarContrasenaTemporal();
                nuevoUsuario.setContrasenaHash(BCrypt.hashpw(contrasenaTemporal, BCrypt.gensalt()));
                nuevoUsuario.setCreadoPor(idUsuarioQueRegistra);
                long idNuevo = usuarioDAO.insertar(conexion, nuevoUsuario);
                servicioAuditoria.registrar(conexion, idUsuarioQueRegistra, "ALTA_USUARIO", "USUARIO", idNuevo,
                        "Creó el usuario \"" + nuevoUsuario.getNombreUsuario() + "\".");
                conexion.commit();
                return new ResultadoAltaUsuario(idNuevo, contrasenaTemporal);
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Edición (plan "Editar usuario", aprobado por Joel): nombres, apellidos, cédula y perfil --
     * nunca el nombre de usuario (login) ni la contraseña, que tienen su propio camino.
     * {@code datosNuevos} solo necesita idUsuario/nombres/apellidos/cedulaIdentidad/perfilUsuario
     * (los demás campos del modelo no se usan acá). Punto 2: si quien edita es el mismo usuario,
     * el perfil SIEMPRE se conserva -- el <select> llega deshabilitado del navegador (no se
     * envía), pero el servidor no confía en eso: ignora lo que haya llegado o no en
     * "perfilUsuario" y usa el que ya tenía, sin excepción.
     */
    public void actualizar(Usuario datosNuevos, long idUsuarioQueModifica) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Usuario anterior = usuarioDAO.buscarPorId(conexion, datosNuevos.getIdUsuario());
                if (anterior == null) {
                    throw new NegocioException("El usuario no existe.");
                }
                // E2 (pedido de Joel): recorta ANTES de validar/comparar/guardar -- si la única
                // diferencia con el valor anterior son espacios, construirTextoCambios ya no la
                // ve como un cambio real. RF02 (pedido de Joel): nombres/apellidos además con
                // mayúscula inicial por palabra.
                String nombresNuevos = NormalizadorTexto.normalizarNombrePropio(datosNuevos.getNombres());
                String apellidosNuevos = NormalizadorTexto.normalizarNombrePropio(datosNuevos.getApellidos());
                String cedulaNueva = servicioClientes.normalizarCedula(datosNuevos.getCedulaIdentidad());

                PerfilUsuario perfilAUsar = resolverPerfilAUsar(anterior, datosNuevos.getPerfilUsuario(),
                        idUsuarioQueModifica);

                validarDatosBasicos(nombresNuevos, apellidosNuevos, cedulaNueva, perfilAUsar);

                long administradoresActivos = usuarioDAO.contarAdministradoresActivos(conexion);
                validarPuedeCambiarPerfil(anterior, perfilAUsar, administradoresActivos);

                Usuario conMismaCedula = usuarioDAO.buscarPorCedula(conexion, cedulaNueva);
                if (conMismaCedula != null && !conMismaCedula.getIdUsuario().equals(anterior.getIdUsuario())) {
                    throw new NegocioException(MENSAJE_CEDULA_DUPLICADA);
                }

                String cambios = construirTextoCambios(anterior, nombresNuevos, apellidosNuevos, cedulaNueva, perfilAUsar);
                if (cambios.isEmpty()) {
                    throw new NegocioException("No hubo cambios.");
                }

                Usuario aGuardar = new Usuario();
                aGuardar.setIdUsuario(anterior.getIdUsuario());
                aGuardar.setNombres(nombresNuevos);
                aGuardar.setApellidos(apellidosNuevos);
                aGuardar.setCedulaIdentidad(cedulaNueva);
                aGuardar.setPerfilUsuario(perfilAUsar);
                usuarioDAO.actualizarDatos(conexion, aGuardar);

                servicioAuditoria.registrar(conexion, idUsuarioQueModifica, "ACTUALIZAR_USUARIO", "USUARIO",
                        anterior.getIdUsuario(), "Actualizó al usuario \"" + anterior.getNombreUsuario()
                                + "\". Cambios: " + cambios + ".");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Plan "Editar usuario" (pedido de Joel), punto 2: si quien edita es el mismo usuario, el
     * perfil SIEMPRE se conserva -- ignora lo que haya llegado (o no) en el perfil propuesto
     * (el <select> llega deshabilitado del navegador cuando alguien edita su propio usuario, así
     * que ni siquiera se manda, pero el servidor no confía en eso). Pura, visibilidad de
     * paquete para poder probarla directo, sin pasar por actualizar() (que necesita Connection).
     */
    PerfilUsuario resolverPerfilAUsar(Usuario anterior, PerfilUsuario perfilPropuesto, long idUsuarioQueModifica) {
        boolean editaAsiMismo = anterior.getIdUsuario().equals(idUsuarioQueModifica);
        return editaAsiMismo ? anterior.getPerfilUsuario() : perfilPropuesto;
    }

    /**
     * Plan "Editar usuario" (pedido de Joel), punto 3: "anterior → nuevo" de los campos que
     * realmente cambiaron, para el detalle de auditoría en texto plano (un solo registro).
     * Visibilidad de paquete -- mismo criterio que ServicioClientes.camposModificados -- para
     * poder probarla directo, sin pasar por actualizar() (que necesita Connection).
     */
    String construirTextoCambios(Usuario anterior, String nombresNuevos, String apellidosNuevos, String cedulaNueva,
            PerfilUsuario perfilNuevo) {
        List<String> cambios = new ArrayList<>();
        agregarSiCambio(cambios, "nombres", anterior.getNombres(), nombresNuevos);
        agregarSiCambio(cambios, "apellidos", anterior.getApellidos(), apellidosNuevos);
        agregarSiCambio(cambios, "cédula", anterior.getCedulaIdentidad(), cedulaNueva);
        agregarSiCambio(cambios, "perfil", anterior.getPerfilUsuario().getEtiqueta(), perfilNuevo.getEtiqueta());
        return String.join("; ", cambios);
    }

    private void agregarSiCambio(List<String> cambios, String etiqueta, Object valorAnterior, Object valorNuevo) {
        if (!Objects.equals(valorAnterior, valorNuevo)) {
            cambios.add(etiqueta + " (" + valorAnterior + " → " + valorNuevo + ")");
        }
    }

    public void bloquear(long idUsuarioObjetivo, long idUsuarioSolicitante) throws SQLException {
        cambiarEstadoConGuarda(idUsuarioObjetivo, idUsuarioSolicitante, EstadoCuenta.BLOQUEADA, "BLOQUEAR_USUARIO",
                "Bloqueó el usuario.");
    }

    public void inactivar(long idUsuarioObjetivo, long idUsuarioSolicitante) throws SQLException {
        cambiarEstadoConGuarda(idUsuarioObjetivo, idUsuarioSolicitante, EstadoCuenta.INACTIVA, "INACTIVAR_USUARIO",
                "Inactivó el usuario.");
    }

    public void reactivar(long idUsuarioObjetivo, long idUsuarioSolicitante) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                usuarioDAO.actualizarEstadoCuenta(conexion, idUsuarioObjetivo, EstadoCuenta.ACTIVA);
                servicioAuditoria.registrar(conexion, idUsuarioSolicitante, "REACTIVAR_USUARIO", "USUARIO",
                        idUsuarioObjetivo, "Reactivó el usuario.");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private void cambiarEstadoConGuarda(long idUsuarioObjetivo, long idUsuarioSolicitante, EstadoCuenta estadoNuevo,
            String operacion, String detalle) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Usuario objetivo = usuarioDAO.buscarPorId(conexion, idUsuarioObjetivo);
                Usuario solicitante = usuarioDAO.buscarPorId(conexion, idUsuarioSolicitante);
                long administradoresActivos = usuarioDAO.contarAdministradoresActivos(conexion);
                validarPuedeBloquearseOInactivarse(objetivo, solicitante, administradoresActivos);

                usuarioDAO.actualizarEstadoCuenta(conexion, idUsuarioObjetivo, estadoNuevo);
                servicioAuditoria.registrar(conexion, idUsuarioSolicitante, operacion, "USUARIO", idUsuarioObjetivo,
                        detalle);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * RF37: el administrador restablece la contraseña de otro usuario -- queda una aleatoria y
     * exige cambio. Hallazgo de Joel: si la cuenta estaba BLOQUEADA, el restablecimiento la
     * desbloquea en la misma operación (ACTIVA, intentos en 0) -- de otro modo la contraseña
     * nueva no serviría de nada hasta un segundo paso aparte ("Reactivar"). Si está INACTIVA
     * (dada de baja), eso es una decisión distinta: no se reactiva acá, solo se le cambia la
     * contraseña.
     */
    public py.com.capitalcredito.sgcpc.dto.ResultadoRestablecerContrasena restablecerContrasena(
            long idUsuarioObjetivo, long idUsuarioSolicitante) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Usuario objetivo = usuarioDAO.buscarPorId(conexion, idUsuarioObjetivo);
                if (objetivo == null) {
                    throw new NegocioException("El usuario no existe.");
                }
                String contrasenaTemporal = generarContrasenaTemporal();
                String hash = BCrypt.hashpw(contrasenaTemporal, BCrypt.gensalt());
                usuarioDAO.actualizarContrasena(conexion, idUsuarioObjetivo, hash, true);
                servicioAuditoria.registrar(conexion, idUsuarioSolicitante, "RESTABLECER_CONTRASENA", "USUARIO",
                        idUsuarioObjetivo, "Restableció la contraseña; el usuario debe cambiarla en el próximo acceso.");

                boolean seDesbloqueoLaCuenta = objetivo.getEstadoCuenta() == EstadoCuenta.BLOQUEADA;
                if (seDesbloqueoLaCuenta) {
                    usuarioDAO.actualizarTrasIntentoFallido(conexion, idUsuarioObjetivo, 0, EstadoCuenta.ACTIVA);
                    servicioAuditoria.registrar(conexion, idUsuarioSolicitante, "REACTIVAR_USUARIO", "USUARIO",
                            idUsuarioObjetivo, "Se desbloqueó la cuenta junto con el restablecimiento de la contraseña.");
                }

                conexion.commit();
                return new py.com.capitalcredito.sgcpc.dto.ResultadoRestablecerContrasena(contrasenaTemporal,
                        seDesbloqueoLaCuenta);
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Bloque de seguridad (hallazgo de Joel): antes usaba UUID.randomUUID() -- en la práctica ya
     * usa SecureRandom por debajo (java.util.UUID lo documenta así), pero de forma implícita y
     * limitada a caracteres hexadecimales en minúsculas. Ahora usa SecureRandom explícito y un
     * alfabeto sin caracteres ambiguos (sin 0/O, 1/l/I), más fácil de leer en voz alta o a mano. */
    private static final java.security.SecureRandom GENERADOR_ALEATORIO_SEGURO = new java.security.SecureRandom();
    private static final String ALFABETO_CONTRASENA_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** Paquete-visible (no private) para poder probarla directo desde ServicioUsuariosTest, mismo
     * criterio que camposModificados/esDeHoy en otros servicios. */
    String generarContrasenaTemporal() {
        // "Temp" (letras) + 8 caracteres aleatorios + "1" (numero): cumple letra+numero+8 caracteres siempre.
        StringBuilder parteAleatoria = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            parteAleatoria.append(ALFABETO_CONTRASENA_TEMPORAL.charAt(
                    GENERADOR_ALEATORIO_SEGURO.nextInt(ALFABETO_CONTRASENA_TEMPORAL.length())));
        }
        return "Temp" + parteAleatoria + "1";
    }
}
