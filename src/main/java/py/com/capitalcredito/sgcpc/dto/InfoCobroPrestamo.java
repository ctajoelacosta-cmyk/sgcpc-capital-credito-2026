package py.com.capitalcredito.sgcpc.dto;

/**
 * D-146 (A1b, resultado de la búsqueda de Cobranza, pedido de Joel): lo que la cajera necesita
 * de un préstamo encontrado, para decidir si cobrar sin tener que abrir la ficha -- próxima
 * cuota, monto a cobrar hoy y si está al día o con atraso. Compuesto en el controlador a partir
 * de lo que YA calculan ServicioCobranza.obtenerProximaCuotaPendiente/cargarPrestamoParaMotor y
 * ServicioMora.listarPrestamosEnMora (lista de trabajo) -- ningún cálculo nuevo, todo ya viene
 * resuelto desde ahí (REGLAS_DESARROLLO.md §5).
 */
public class InfoCobroPrestamo {

    private final String proximaCuota;
    private final String proximoVencimiento;
    private final String aCobrarHoy;
    private final boolean alDia;
    private final int diasAtraso;

    public InfoCobroPrestamo(String proximaCuota, String proximoVencimiento, String aCobrarHoy, boolean alDia,
            int diasAtraso) {
        this.proximaCuota = proximaCuota;
        this.proximoVencimiento = proximoVencimiento;
        this.aCobrarHoy = aCobrarHoy;
        this.alDia = alDia;
        this.diasAtraso = diasAtraso;
    }

    public String getProximaCuota() {
        return proximaCuota;
    }

    public String getProximoVencimiento() {
        return proximoVencimiento;
    }

    public String getACobrarHoy() {
        return aCobrarHoy;
    }

    public boolean isAlDia() {
        return alDia;
    }

    public int getDiasAtraso() {
        return diasAtraso;
    }
}
