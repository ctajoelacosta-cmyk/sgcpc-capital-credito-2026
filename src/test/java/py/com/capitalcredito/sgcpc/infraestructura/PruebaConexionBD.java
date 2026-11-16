package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Test;

/**
 * Confirma que la aplicacion se conecta a PostgreSQL usando bd.properties
 * (Doc. 00 E0, criterio de terminado). No corre con "mvn clean package"
 * a proposito (el nombre no matchea los patrones por defecto de Surefire):
 * se ejecuta explicitamente con "mvn -Dtest=PruebaConexionBD test", una vez
 * que la base "sgcpc" ya existe.
 */
public class PruebaConexionBD {

    @Test
    public void seConectaYEjecutaUnaConsultaSimple() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
             Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery("SELECT 1")) {
            assertTrue(resultado.next());
            assertTrue(resultado.getInt(1) == 1);
        }
    }
}
