package py.com.capitalcredito.sgcpc.dto;

/** B4 (Bloque 7/E10, pedido de Joel): totales del informe de Cobros -- por columna (capital,
 * interés, moratorio, punitorio, IVA, total) sobre los cobros CONFIRMADO del período; los
 * REVERTIDO quedan afuera (pedido explícito de Joel). cantidadRevertidos es solo informativo
 * (cuántas filas de las mostradas no suman), no es parte de la suma. */
public class TotalesCobros {

    private final int cantidad;
    private final int cantidadRevertidos;
    private final String capitalTotal;
    private final String interesTotal;
    private final String moratorioTotal;
    private final String punitorioTotal;
    private final String ivaTotal;
    private final String total;

    public TotalesCobros(int cantidad, int cantidadRevertidos, String capitalTotal, String interesTotal,
            String moratorioTotal, String punitorioTotal, String ivaTotal, String total) {
        this.cantidad = cantidad;
        this.cantidadRevertidos = cantidadRevertidos;
        this.capitalTotal = capitalTotal;
        this.interesTotal = interesTotal;
        this.moratorioTotal = moratorioTotal;
        this.punitorioTotal = punitorioTotal;
        this.ivaTotal = ivaTotal;
        this.total = total;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getCantidadRevertidos() {
        return cantidadRevertidos;
    }

    public String getCapitalTotal() {
        return capitalTotal;
    }

    public String getInteresTotal() {
        return interesTotal;
    }

    public String getMoratorioTotal() {
        return moratorioTotal;
    }

    public String getPunitorioTotal() {
        return punitorioTotal;
    }

    public String getIvaTotal() {
        return ivaTotal;
    }

    public String getTotal() {
        return total;
    }
}
