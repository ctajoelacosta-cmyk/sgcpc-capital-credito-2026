package py.com.capitalcredito.sgcpc.dto;

/**
 * Una fila de la tabla de cuotas del reporte "Plan de pagos" (D-70). Todos los valores ya vienen
 * formateados por FormateadorNumeros/FormateadorFechas desde Java -- el reporte no calcula ni
 * formatea nada, solo muestra (REGLAS_DESARROLLO.md §6).
 */
public class CuotaReporte {

    private final int numeroCuota;
    private final String fechaVencimiento;
    private final String capital;
    private final String interes;
    private final String iva;
    private final String total;
    private final String saldoPosterior;

    public CuotaReporte(int numeroCuota, String fechaVencimiento, String capital, String interes, String iva,
            String total, String saldoPosterior) {
        this.numeroCuota = numeroCuota;
        this.fechaVencimiento = fechaVencimiento;
        this.capital = capital;
        this.interes = interes;
        this.iva = iva;
        this.total = total;
        this.saldoPosterior = saldoPosterior;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public String getFechaVencimiento() {
        return fechaVencimiento;
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

    public String getTotal() {
        return total;
    }

    public String getSaldoPosterior() {
        return saldoPosterior;
    }
}
