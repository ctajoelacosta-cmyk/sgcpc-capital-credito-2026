package py.com.capitalcredito.sgcpc.dto;

/** Fila de totales al pie del reporte Cartera (§13, Bloque 4, E8). */
public class TotalesCartera {

    private final int cantidadPrestamos;
    private final String capitalPendiente;
    private final String interesFuturo;

    public TotalesCartera(int cantidadPrestamos, String capitalPendiente, String interesFuturo) {
        this.cantidadPrestamos = cantidadPrestamos;
        this.capitalPendiente = capitalPendiente;
        this.interesFuturo = interesFuturo;
    }

    public int getCantidadPrestamos() {
        return cantidadPrestamos;
    }

    public String getCapitalPendiente() {
        return capitalPendiente;
    }

    public String getInteresFuturo() {
        return interesFuturo;
    }
}
