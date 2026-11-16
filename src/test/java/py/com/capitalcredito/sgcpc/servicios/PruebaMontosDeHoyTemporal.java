package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;

/**
 * HERRAMIENTA TEMPORAL (01/11/2026) -- pregunta al MOTOR REAL cuánto cuesta hoy cada operación de
 * cobro de los dos préstamos activos, para armar el plan de prueba en pantalla sin adivinar
 * ningún número (ya pasó una vez: una predicción a mano erró el mínimo del adelanto).
 *
 * <ul>
 *   <li><b>Solo lectura</b>: ni un {@code UPDATE}, ni un {@code INSERT}, ningún commit. Los dos
 *       métodos que usa son las mismas vistas previas que pinta la ficha de cobranza
 *       ({@code calcularPreviewCancelacionTotal}, {@code calcularPreviewAnticipoParcial}), que por
 *       definición no persisten nada.</li>
 *   <li><b>No corre con {@code mvn test}</b>: el nombre no matchea {@code *Test}/{@code Test*}.
 *       Se invoca a propósito: {@code mvn -Dtest=PruebaMontosDeHoyTemporal test}.</li>
 *   <li><b>Guarda dura de DOGMA 2</b>, leída de la conexión abierta, no de un archivo.</li>
 * </ul>
 */
public class PruebaMontosDeHoyTemporal {

    private final ServicioCobranza servicioCobranza = new ServicioCobranza();

    @Test
    public void mostrarLosMontosDeHoy() throws Exception {
        LocalDate hoy = LocalDate.now();
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            String url = conexion.getMetaData().getURL();
            if (url == null || !url.endsWith("/sgcpc_db_pruebas")
                    || !"sgcpc_db_pruebas".equals(conexion.getCatalog())) {
                fail("DOGMA 2: la conexión no apunta a sgcpc_db_pruebas (url=" + url + ")");
            }
            System.out.println("=== Montos al " + hoy + " ===");
            for (long idPrestamo : new long[] {4L, 5L}) {
                System.out.println("--- id_prestamo " + idPrestamo + " ---");
                mostrarCancelacionTotal(conexion, idPrestamo, hoy);
                mostrarAnticipoMinimo(conexion, idPrestamo, hoy);
            }
        }
    }

    private void mostrarCancelacionTotal(Connection conexion, long idPrestamo, LocalDate hoy) {
        try {
            py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion r =
                    servicioCobranza.calcularPreviewCancelacionTotal(conexion, idPrestamo, hoy);
            System.out.println("  Cancelación total : " + FormateadorNumeros.comoGuaranies(r.getMontoTotalLiquidacion())
                    + "  (capital " + FormateadorNumeros.comoGuaranies(r.getSaldoCapitalFecha())
                    + " + interés " + FormateadorNumeros.comoGuaranies(r.getInteresDevengado())
                    + " + IVA " + FormateadorNumeros.comoGuaranies(r.getIvaInteresDevengado()) + ")");
            BigDecimal total = r.getMontoTotalLiquidacion();
            BigDecimal redondeado = total.divideToIntegralValue(BigDecimal.valueOf(50))
                    .multiply(BigDecimal.valueOf(50));
            System.out.println("  -> múltiplo de 50 hacia abajo: " + FormateadorNumeros.comoGuaranies(redondeado)
                    + "   (redondeo perdonado: -" + total.subtract(redondeado) + " Gs.)");
        } catch (Exception e) {
            System.out.println("  Cancelación total : NO DISPONIBLE -- " + e.getMessage());
        }
    }

    private void mostrarAnticipoMinimo(Connection conexion, long idPrestamo, LocalDate hoy) {
        try {
            py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion r =
                    servicioCobranza.calcularPreviewAnticipoParcial(conexion, idPrestamo, hoy);
            System.out.println("  Anticipo: interés del período " + FormateadorNumeros.comoGuaranies(r.getInteresDevengado())
                    + " + IVA " + FormateadorNumeros.comoGuaranies(r.getIvaInteresDevengado()));
        } catch (Exception e) {
            System.out.println("  Anticipo          : NO DISPONIBLE -- " + e.getMessage());
        }
    }
}
