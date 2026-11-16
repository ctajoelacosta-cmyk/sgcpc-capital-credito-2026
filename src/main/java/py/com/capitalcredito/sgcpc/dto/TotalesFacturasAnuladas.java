package py.com.capitalcredito.sgcpc.dto;

/** Bloque C, tanda 2 (pedido de Joel): total de montos anulados del mes -- cantidad y monto. */
public class TotalesFacturasAnuladas {

    private final int cantidad;
    private final String montoTotal;

    public TotalesFacturasAnuladas(int cantidad, String montoTotal) {
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
