package py.com.capitalcredito.sgcpc.dto;

/** Una fila de subtotal por tramo de atraso del reporte Morosidad (§13, Bloque 4, E8) -- también
 * se reusa para la fila de "Total general" al pie (tramo = "Total general"). */
public class SubtotalTramo {

    private final String tramo;
    private final int cantidad;
    private final String monto;

    public SubtotalTramo(String tramo, int cantidad, String monto) {
        this.tramo = tramo;
        this.cantidad = cantidad;
        this.monto = monto;
    }

    public String getTramo() {
        return tramo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getMonto() {
        return monto;
    }
}
