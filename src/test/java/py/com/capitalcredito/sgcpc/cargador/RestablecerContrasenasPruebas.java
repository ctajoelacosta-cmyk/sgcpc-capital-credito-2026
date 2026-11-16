package py.com.capitalcredito.sgcpc.cargador;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * D-117 (pedido de Joel): `bd/recrear_base.sh` deja gerente/oficial/cajera/administrador con los
 * hashes fijos del seed (`03_parametros.sql`) -- si Joel ya tiene otras contraseñas guardadas en
 * `~/.sgcpc_prueba` (las que `pruebas/humo_http.sh` usa para loguearse de verdad), el primer
 * login real después de recrear la base falla, y con RNF09 (3 intentos) puede terminar
 * bloqueando la cuenta. Esta clase reaplica esas contraseñas por SQL directo, en la base de
 * pruebas -- nunca imprime ningún valor, ni la contraseña ni el hash.
 *
 * Hallazgo (16/10/2026, incidente real de login): administrador quedaba afuera de este
 * restablecimiento -- al no reaplicarse, siguió con el hash del seed mientras el resto ya usaba
 * las contraseñas de `~/.sgcpc_prueba`, y terminó BLOQUEADA por intentos fallidos sin relación
 * con esto. Se agrega acá con el mismo criterio que oficial/cajera (no obligatorio: si no están
 * sus variables, se deja sin tocar).
 *
 * No usa "mvn test"/"mvn clean package" a propósito -- el nombre no matchea los patrones de
 * Surefire (mismo patrón que PruebaConexionBD/CargadorDatosPrueba).
 *
 * USO (desde sistema/, con la base de pruebas ya recreada y bd.properties apuntando a ella,
 * y SGCPC_USUARIO/SGCPC_CONTRASENA -- y opcionalmente los de oficial/cajera/administrador -- ya
 * exportadas, ej. por `source ~/.sgcpc_prueba`):
 *   mvn -Dtest=RestablecerContrasenasPruebas test
 *
 * Guarda de seguridad (mismo patrón que CargadorDatosPrueba, D-115): el primer paso confirma
 * FabricaConexionBD.esBaseDePruebas() -- si bd.properties no tiene la marca entorno=pruebas,
 * aborta sin tocar absolutamente nada.
 */
public class RestablecerContrasenasPruebas {

    @Test
    public void restablecer() throws Exception {
        if (!FabricaConexionBD.esBaseDePruebas()) {
            throw new IllegalStateException("ABORTADO: bd.properties no tiene la marca entorno=pruebas -- "
                    + "esta clase nunca debe tocar contraseñas fuera de la base de pruebas.");
        }
        aplicar("gerente", System.getenv("SGCPC_USUARIO"), System.getenv("SGCPC_CONTRASENA"), true);
        aplicar("oficial", System.getenv("SGCPC_USUARIO_OFICIAL"), System.getenv("SGCPC_CONTRASENA_OFICIAL"), false);
        aplicar("cajera", System.getenv("SGCPC_USUARIO_CAJERA"), System.getenv("SGCPC_CONTRASENA_CAJERA"), false);
        aplicar("administrador", System.getenv("SGCPC_USUARIO_ADMIN"), System.getenv("SGCPC_CONTRASENA_ADMIN"), false);
    }

    private void aplicar(String rol, String usuario, String contrasena, boolean obligatorio) throws Exception {
        if (usuario == null || contrasena == null || usuario.trim().isEmpty() || contrasena.trim().isEmpty()) {
            if (obligatorio) {
                throw new IllegalStateException("ABORTADO: no están SGCPC_USUARIO/SGCPC_CONTRASENA (gerente) "
                        + "en el entorno -- revise ~/.sgcpc_prueba.");
            }
            System.out.println("Sin variables para \"" + rol + "\" -- se deja su contraseña actual sin tocar.");
            return;
        }
        String hash = BCrypt.hashpw(contrasena, BCrypt.gensalt());
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "UPDATE usuarios SET contrasena_hash = ?, debe_cambiar_contrasena = FALSE, "
                                + "intentos_fallidos = 0, estado_cuenta = 'ACTIVA', fecha_cambio_contrasena = now() "
                                + "WHERE nombre_usuario = ?")) {
            sentencia.setString(1, hash);
            sentencia.setString(2, usuario);
            int filas = sentencia.executeUpdate();
            if (filas != 1) {
                throw new IllegalStateException("ABORTADO: no se encontró el usuario \"" + usuario
                        + "\" (rol \"" + rol + "\") en la base de pruebas -- ¿coincide con bd/03_parametros.sql?");
            }
        }
        System.out.println("Contraseña reaplicada para el usuario de prueba \"" + rol
                + "\" (nunca se imprime el valor).");
    }
}
