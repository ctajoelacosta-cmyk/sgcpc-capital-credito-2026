package py.com.capitalcredito.sgcpc.dto;

/**
 * Una fila de la tabla de cuotas del recibo de dinero (docs/diseno_comprobantes_E8.md sección 2).
 * A diferencia de la factura, acá no hace falta derivar nada: cada columna es una lectura directa
 * de aplicaciones_pago (capital/interés/moratorio/punitorio netos, IVA ya combinado por cuota).
 * Todos los valores ya vienen formateados desde Java -- el reporte no calcula nada (REGLAS_DESARROLLO.md §6).
 */
public class FilaReciboCuota {

    private final String cuota;
    private final String capital;
    private final String interes;
    private final String moratorio;
    private final String punitorio;
    private final String iva;
    private final String total;

    public FilaReciboCuota(String cuota, String capital, String interes, String moratorio, String punitorio,
            String iva, String total) {
        this.cuota = cuota;
        this.capital = capital;
        this.interes = interes;
        this.moratorio = moratorio;
        this.punitorio = punitorio;
        this.iva = iva;
        this.total = total;
    }

    public String getCuota() {
        return cuota;
    }

    public String getCapital() {
        return capital;
    }

    public String getInteres() {
        return interes;
    }

    public String getMoratorio() {
        return moratorio;
    }

    public String getPunitorio() {
        return punitorio;
    }

    public String getIva() {
        return iva;
    }

    public String getTotal() {
        return total;
    }
}
