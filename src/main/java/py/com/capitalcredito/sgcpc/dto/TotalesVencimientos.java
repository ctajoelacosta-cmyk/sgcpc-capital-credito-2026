package py.com.capitalcredito.sgcpc.dto;

/** Fila de totales al pie del reporte Vencimientos (§13, Bloque 4, E8). Bloque C, tanda 1 (pedido
 * de Joel): desglosa capital/interés/IVA además del total (antes solo tenía el total general). */
public class TotalesVencimientos {

    private final int cantidadCuotas;
    private final String capitalTotal;
    private final String interesTotal;
    private final String ivaTotal;
    private final String montoTotal;

    public TotalesVencimientos(int cantidadCuotas, String capitalTotal, String interesTotal, String ivaTotal,
            String montoTotal) {
        this.cantidadCuotas = cantidadCuotas;
        this.capitalTotal = capitalTotal;
        this.interesTotal = interesTotal;
        this.ivaTotal = ivaTotal;
        this.montoTotal = montoTotal;
    }

    public int getCantidadCuotas() {
        return cantidadCuotas;
    }

    public String getCapitalTotal() {
        return capitalTotal;
    }

    public String getInteresTotal() {
        return interesTotal;
    }

    public String getIvaTotal() {
        return ivaTotal;
    }

    public String getMontoTotal() {
        return montoTotal;
    }
}
