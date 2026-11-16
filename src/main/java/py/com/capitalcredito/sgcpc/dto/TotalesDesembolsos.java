package py.com.capitalcredito.sgcpc.dto;

/** B4 (Bloque 7/E10, pedido de Joel): totales del informe de Desembolsos -- cantidad y monto total. */
public class TotalesDesembolsos {

    private final int cantidad;
    private final String montoTotal;

    public TotalesDesembolsos(int cantidad, String montoTotal) {
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
