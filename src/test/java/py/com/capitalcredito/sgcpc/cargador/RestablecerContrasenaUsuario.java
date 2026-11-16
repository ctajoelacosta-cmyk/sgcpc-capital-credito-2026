package py.com.capitalcredito.sgcpc.cargador;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * D-117 (pedido de Joel): respaldo de `bd/desbloquear_usuario.sh --restablecer` -- genera una
 * contraseña temporal nueva para un usuario que no tiene otro GERENTE_ADMINISTRADOR activo para
 * restablecérsela desde la pantalla de Usuarios (mismo patrón que
 * ServicioUsuarios.restablecerContrasena: "Temp" + 6 caracteres aleatorios + "1", hasheada con
 * BCrypt, exige cambio en el próximo acceso). El motivo obligatorio lo valida y anota el script
 * que llama a esto (bd/desbloquear_usuario.log) -- esta clase solo genera, aplica y muestra la
 * contraseña UNA sola vez, nunca la guarda en ningún archivo.
 *
 * No usa "mvn test"/"mvn clean package" a propósito -- el nombre no matchea los patrones de
 * Surefire (mismo patrón que PruebaConexionBD/CargadorDatosPrueba).
 *
 * USO (desde sistema/, con bd.properties apuntando a la base donde vive el usuario):
 *   mvn -Dtest=RestablecerContrasenaUsuario -DnombreUsuario=gerente test
 *
 * A diferencia de RestablecerContrasenasPruebas (D-117), esta clase NO exige
 * FabricaConexionBD.esBaseDePruebas() -- a propósito: bd/desbloquear_usuario.sh es la vía de
 * recuperación aprobada (D-25) también para la base real, cuando un gerente real queda
 * bloqueado sin otro gerente activo para desbloquearlo desde la pantalla.
 */
public class RestablecerContrasenaUsuario {

    @Test
    public void restablecer() throws Exception {
        String nombreUsuario = requerido("nombreUsuario");

        String contrasenaTemporal = generarContrasenaTemporal();
        String hash = BCrypt.hashpw(contrasenaTemporal, BCrypt.gensalt());
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "UPDATE usuarios SET contrasena_hash = ?, debe_cambiar_contrasena = TRUE, "
                                + "intentos_fallidos = 0, estado_cuenta = 'ACTIVA', fecha_cambio_contrasena = now() "
                                + "WHERE nombre_usuario = ?")) {
            sentencia.setString(1, hash);
            sentencia.setString(2, nombreUsuario);
            int filas = sentencia.executeUpdate();
            if (filas != 1) {
                throw new IllegalStateException("No se encontró el usuario \"" + nombreUsuario + "\".");
            }
        }
        // Único lugar donde se imprime -- el script que llama a esto la captura de esta línea
        // y se la entrega a la persona; nunca queda en ningún archivo ni en otro log.
        System.out.println("CONTRASENA_TEMPORAL=" + contrasenaTemporal);
    }

    private String requerido(String propiedad) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalStateException("Falta -D" + propiedad + "=... .");
        }
        return valor.trim();
    }

    private static final java.security.SecureRandom GENERADOR_ALEATORIO_SEGURO = new java.security.SecureRandom();
    private static final String ALFABETO_CONTRASENA_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** Mismo patrón que ServicioUsuarios.generarContrasenaTemporal: letra+número+8 caracteres siempre. */
    private String generarContrasenaTemporal() {
        StringBuilder parteAleatoria = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            parteAleatoria.append(ALFABETO_CONTRASENA_TEMPORAL.charAt(
                    GENERADOR_ALEATORIO_SEGURO.nextInt(ALFABETO_CONTRASENA_TEMPORAL.length())));
        }
        return "Temp" + parteAleatoria + "1";
    }
}
