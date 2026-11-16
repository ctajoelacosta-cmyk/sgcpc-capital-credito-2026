package py.com.capitalcredito.sgcpc.dto;

/** Una fila del "Plan de pagos" del Estado de cuenta del cliente (§13, Bloque 4/E8; punto 9,
 * Bloque 7/E10: ahora con TODAS las cuotas, no solo las pendientes). "Vencimiento efectivo" --
 * fecha_vencimiento (con el corrimiento de RN-19), no la teórica. fechaPago/numeroRecibo van null
 * salvo que estado sea "Pagada". */
public class FilaCuotaPendiente {

    private final int numeroCuota;
    private final String fechaVencimiento;
    private final String capital;
    private final String interes;
    private final String iva;
    private final String total;
    private final String estado;
    private final String fechaPago;
    private final String numeroRecibo;

    public FilaCuotaPendiente(int numeroCuota, String fechaVencimiento, String capital, String interes,
            String iva, String total, String estado, String fechaPago, String numeroRecibo) {
        this.numeroCuota = numeroCuota;
        this.fechaVencimiento = fechaVencimiento;
        this.capital = capital;
        this.interes = interes;
        this.iva = iva;
        this.total = total;
        this.estado = estado;
        this.fechaPago = fechaPago;
        this.numeroRecibo = numeroRecibo;
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

    public String getEstado() {
        return estado;
    }

    public String getFechaPago() {
        return fechaPago;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }
}
