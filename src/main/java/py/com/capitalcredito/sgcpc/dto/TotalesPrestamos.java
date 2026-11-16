package py.com.capitalcredito.sgcpc.dto;

/** B1 (Bloque 7/E10, pedido de Joel): fila de totales (<tfoot>) del listado de Préstamos. */
public class TotalesPrestamos {

    private final int cantidad;
    private final String montoTotal;
    private final String saldoTotal;

    public TotalesPrestamos(int cantidad, String montoTotal, String saldoTotal) {
        this.cantidad = cantidad;
        this.montoTotal = montoTotal;
        this.saldoTotal = saldoTotal;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getMontoTotal() {
        return montoTotal;
    }

    public String getSaldoTotal() {
        return saldoTotal;
    }
}
