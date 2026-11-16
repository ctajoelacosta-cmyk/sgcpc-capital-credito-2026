package py.com.capitalcredito.sgcpc.dto;

/** Una fila del plan de pagos de la Cotización/simulación imprimible (C-4.1, pedido de Joel). Todo
 * ya formateado -- mismo molde que el resto de los informes de este bloque. */
public class FilaPlanCotizacion {

    private final int numeroCuota;
    private final String vencimiento;
    private final String capital;
    private final String interes;
    private final String iva;
    private final String cuota;
    private final String saldo;

    public FilaPlanCotizacion(int numeroCuota, String vencimiento, String capital, String interes, String iva,
            String cuota, String saldo) {
        this.numeroCuota = numeroCuota;
        this.vencimiento = vencimiento;
        this.capital = capital;
        this.interes = interes;
        this.iva = iva;
        this.cuota = cuota;
        this.saldo = saldo;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public String getVencimiento() {
        return vencimiento;
    }

    public String getCapital() {
        return capital;
    }

    public String getInteres() {
        return interes;
    }

    public String getIva() {
        return iva;
    }

    public String getCuota() {
        return cuota;
    }

    /** Saldo de capital DESPUÉS de esta cuota (Cuota.saldoCapitalPosterior) -- no se muestra en
     * pantalla, pedido explícito de Joel para el PDF. */
    public String getSaldo() {
        return saldo;
    }
}
