package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;

/**
 * HERRAMIENTA TEMPORAL (05/11/2026, B115) -- le pregunta al MOTOR REAL cuánto cuesta adelantar 1,
 * 2 y 3 cuotas del préstamo que se va a usar para probar en pantalla el aviso <i>"lo recibido no
 * alcanza para N cuotas"</i>, para no decirle a Joel un número inventado (ya pasó: una predicción
 * a mano erró el mínimo del adelanto, ver {@code sgcpc-cambio-seguro}).
 *
 * <ul>
 *   <li><b>Solo lectura</b>: ni un {@code UPDATE}, ni un {@code INSERT}, ningún commit. Usa
 *       {@code calcularMontoMinimoParaDejarCuotas} y {@code calcularPreviewAnticipoParcial}, que
 *       son las mismas vistas previas que pinta la ficha y por definición no persisten nada.</li>
 *   <li><b>No corre con {@code mvn test}</b>: el nombre no matchea {@code *Test}/{@code Test*}.
 *       Se invoca a propósito: {@code mvn -Dtest=PruebaMontosB115Temporal test}.</li>
 *   <li><b>Guarda dura de DOGMA 2</b>, leída de la conexión abierta, no de un archivo.</li>
 * </ul>
 */
public class PruebaMontosB115Temporal {

    private static final long ID_PRESTAMO = 15L;

    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    private final ServicioParametros servicioParametros = new ServicioParametros();

    @Test
    public void mostrarLosMontosDelAdelantoPorCuotas() throws Exception {
        LocalDate hoy = LocalDate.now();
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            String url = conexion.getMetaData().getURL();
            if (url == null || !url.endsWith("/sgcpc_db_pruebas")
                    || !"sgcpc_db_pruebas".equals(conexion.getCatalog())) {
                fail("DOGMA 2: la conexión no apunta a sgcpc_db_pruebas (url=" + url + ")");
            }
            int redondeo = servicioParametros.obtenerValorEntero(conexion, "credito.redondeo_efectivo");
            int quedan = 0;
            for (py.com.capitalcredito.sgcpc.modelos.Cuota cuota
                    : servicioCobranza.cargarPrestamoParaMotor(conexion, ID_PRESTAMO).getCuotas()) {
                if (cuota.getEstadoPago() != py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago.PAGADA) {
                    quedan++;
                }
            }
            System.out.println("=== B115 / prestamo " + ID_PRESTAMO + " al " + hoy
                    + " (cuotas pendientes: " + quedan + ", redondeo_efectivo: " + redondeo + ") ===");
            for (int n = 1; n <= 3 && n < quedan; n++) {
                BigDecimal monto = servicioCobranza.calcularMontoMinimoParaDejarCuotas(
                        conexion, ID_PRESTAMO, hoy, quedan - n, redondeo);
                System.out.println("  adelantar " + n + (n == 1 ? " cuota : " : " cuotas: ")
                        + (monto == null ? "no hay monto que deje esa cantidad exacta"
                                : FormateadorNumeros.comoGuaranies(monto)));
            }
            BigDecimal minimo = servicioCobranza.calcularMontoMinimoParaDejarCuotas(
                    conexion, ID_PRESTAMO, hoy, quedan - 1, redondeo);
            if (minimo != null) {
                py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion preview =
                        servicioCobranza.calcularPreviewAnticipoParcial(conexion, ID_PRESTAMO, hoy, minimo);
                System.out.println("  minimo del adelanto (interes devengado + IVA, D-145 P-5): "
                        + FormateadorNumeros.comoGuaranies(preview.getInteresDevengado()
                                .add(preview.getIvaInteresDevengado())));
            }
        }
    }
}
