package py.com.capitalcredito.sgcpc.dto;

/** Totales del Estado de cuenta del cliente (§13, Bloque 4, E8). "Monto exigible hoy" es null
 * para un préstamo FINALIZADO/ANULADO/PENDIENTE_DESEMBOLSO -- no aplica, no es "0 Gs." */
public class TotalesEstadoCuenta {

    private final String saldoCapital;
    private final String totalPagado;
    private final String montoExigibleHoy;

    public TotalesEstadoCuenta(String saldoCapital, String totalPagado, String montoExigibleHoy) {
        this.saldoCapital = saldoCapital;
        this.totalPagado = totalPagado;
        this.montoExigibleHoy = montoExigibleHoy;
    }

    public String getSaldoCapital() {
        return saldoCapital;
    }

    public String getTotalPagado() {
        return totalPagado;
    }

    public String getMontoExigibleHoy() {
        return montoExigibleHoy;
    }
}
