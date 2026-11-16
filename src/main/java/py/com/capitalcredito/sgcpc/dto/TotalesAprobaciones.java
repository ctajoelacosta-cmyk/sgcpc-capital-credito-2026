package py.com.capitalcredito.sgcpc.dto;

/** B3 (Bloque 7/E10, pedido de Joel): totales del informe de Aprobaciones y rechazos -- aprobadas,
 * rechazadas y canceladas por separado (cantidad y monto; RF07, 10/10/2026: se agrega canceladas,
 * que antes el informe no traía) y el tiempo promedio de respuesta, sobre TODAS las filas del
 * período. */
public class TotalesAprobaciones {

    private final int cantidadAprobadas;
    private final String montoAprobadas;
    private final int cantidadRechazadas;
    private final String montoRechazadas;
    private final int cantidadCanceladas;
    private final String montoCanceladas;
    private final String tiempoPromedioRespuesta;

    public TotalesAprobaciones(int cantidadAprobadas, String montoAprobadas, int cantidadRechazadas,
            String montoRechazadas, int cantidadCanceladas, String montoCanceladas, String tiempoPromedioRespuesta) {
        this.cantidadAprobadas = cantidadAprobadas;
        this.montoAprobadas = montoAprobadas;
        this.cantidadRechazadas = cantidadRechazadas;
        this.montoRechazadas = montoRechazadas;
        this.cantidadCanceladas = cantidadCanceladas;
        this.montoCanceladas = montoCanceladas;
        this.tiempoPromedioRespuesta = tiempoPromedioRespuesta;
    }

    public int getCantidadAprobadas() {
        return cantidadAprobadas;
    }

    public String getMontoAprobadas() {
        return montoAprobadas;
    }

    public int getCantidadRechazadas() {
        return cantidadRechazadas;
    }

    public String getMontoRechazadas() {
        return montoRechazadas;
    }

    public int getCantidadCanceladas() {
        return cantidadCanceladas;
    }

    public String getMontoCanceladas() {
        return montoCanceladas;
    }

    public String getTiempoPromedioRespuesta() {
        return tiempoPromedioRespuesta;
    }

    public int getCantidadTotal() {
        return cantidadAprobadas + cantidadRechazadas + cantidadCanceladas;
    }
}
