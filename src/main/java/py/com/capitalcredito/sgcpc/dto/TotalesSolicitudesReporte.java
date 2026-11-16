package py.com.capitalcredito.sgcpc.dto;

/** B2 (Bloque 7/E10, pedido de Joel): fila de totales (<tfoot>) del informe de Solicitudes. */
public class TotalesSolicitudesReporte {

    private final int cantidad;
    private final String montoTotal;

    public TotalesSolicitudesReporte(int cantidad, String montoTotal) {
        this.cantidad = cantidad;
        this.montoTotal = montoTotal;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getMontoTotal() {
        return montoTotal;
    }
}
