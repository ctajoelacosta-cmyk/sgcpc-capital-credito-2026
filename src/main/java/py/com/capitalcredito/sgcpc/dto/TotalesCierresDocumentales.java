package py.com.capitalcredito.sgcpc.dto;

/** E-12 (pedido de Joel): totales del informe de Cierres documentales -- cancelados, devueltos y
 * pendientes de devolución, sobre las filas que estén mostrándose (si hay un filtro de estado del
 * pagaré aplicado, los tres números reflejan ESE subconjunto, mismo criterio que el resumen por
 * estado de Préstamos/Solicitudes). */
public class TotalesCierresDocumentales {

    private final int cantidadCancelados;
    private final int cantidadDevueltos;
    private final int cantidadPendientes;

    public TotalesCierresDocumentales(int cantidadCancelados, int cantidadDevueltos, int cantidadPendientes) {
        this.cantidadCancelados = cantidadCancelados;
        this.cantidadDevueltos = cantidadDevueltos;
        this.cantidadPendientes = cantidadPendientes;
    }

    public int getCantidadCancelados() {
        return cantidadCancelados;
    }

    public int getCantidadDevueltos() {
        return cantidadDevueltos;
    }

    public int getCantidadPendientes() {
        return cantidadPendientes;
    }
}
