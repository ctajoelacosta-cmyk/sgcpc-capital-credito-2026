package py.com.capitalcredito.sgcpc.dao;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * D-77 punto 14: demuestra que NumeradorDAO.siguienteValor (SELECT ... FOR UPDATE dentro de la
 * transaccion del llamador) no deja numeros salteados si la transaccion que lo llamó falla y
 * hace rollback -- el UPDATE del numerador se deshace junto con el resto. No se puede probar
 * como JUnit puro (la regla del proyecto es "sin base de datos en JUnit", salvo esta excepcion,
 * igual que PruebaConexionBD) ni por pruebas/humo_http.sh (no se puede forzar un fallo a mitad
 * de una transaccion HTTP real). Usa una clave/anio de numerador que no se usa en ninguna
 * anio que ninguna pantalla usa nunca (numeradores.clave tiene un CHECK con los 4 valores reales
 * -- FACTURA/OPERACION/RECIBO/SOLICITUD -- asi que la clave tiene que ser una de esas; se usa
 * RECIBO con anio=1900, un anio que ningun cobro real puede generar, para no poder tocar nunca
 * el numerador real de ningun anio real aunque algo salga mal).
 *
 * No corre con "mvn clean package" a proposito (el nombre no matchea los patrones por defecto
 * de Surefire): se ejecuta explicitamente con
 * "mvn -Dtest=PruebaNumeradorSinSaltos test", una vez que la base "sgcpc_db" ya existe. Todo el
 * test ocurre dentro de una unica transaccion con autoCommit=false que termina siempre con
 * rollback (en el finally) -- no deja ningun dato en numeradores, ni siquiera el de la prueba.
 */
public class PruebaNumeradorSinSaltos {

    private static final String CLAVE = "RECIBO";
    private static final int ANIO = 1900;

    @Test
    public void unaTransaccionFallidaNoDejaSalteadoElSiguienteNumero() throws Exception {
        NumeradorDAO numeradorDAO = new NumeradorDAO();
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                long primerNumero = numeradorDAO.siguienteValor(conexion, CLAVE, ANIO);
                assertEquals(1L, primerNumero);

                try {
                    // Fallo simulado a mitad de la transaccion (ej.: violacion de una restriccion
                    // mas adelante, como pasaria en confirmarCobro/confirmarCancelacionTotal/
                    // confirmarAnticipoCapital si algo falla despues de tomar el numero).
                    throw new RuntimeException("Fallo simulado para probar que no queda numero salteado.");
                } catch (RuntimeException fallosimulado) {
                    conexion.rollback();
                }

                // Tras el rollback, el numerador debe seguir en 0 -- el proximo numero vuelve a
                // ser el mismo que se intento antes del fallo, no el siguiente.
                long numeroTrasRollback = numeradorDAO.siguienteValor(conexion, CLAVE, ANIO);
                assertEquals("el numero no debe quedar salteado tras el rollback", 1L, numeroTrasRollback);
            } finally {
                // Nunca se comitea nada de esta prueba (ni siquiera el numerador de prueba).
                conexion.rollback();
            }
        }
    }
}
