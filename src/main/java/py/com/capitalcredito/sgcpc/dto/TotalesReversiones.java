package py.com.capitalcredito.sgcpc.dto;

/** Totales del informe Reversiones (Bloque C tanda 3, pedido de Joel; desglosado en C-3b):
 * cantidad de reversiones en el rango por estado (autorizadas/rechazadas/solicitadas) y el total
 * de montos REVERTIDOS -- solo las AUTORIZADA, porque son las únicas donde el dinero realmente
 * salió de caja (una SOLICITADA sigue pendiente, una RECHAZADA nunca movió plata). */
public class TotalesReversiones {

    private final int cantidad;
    private final int cantidadAutorizadas;
    private final int cantidadRechazadas;
    private final int cantidadSolicitadas;
    private final String montoTotalRevertido;

    public TotalesReversiones(int cantidad, int cantidadAutorizadas, int cantidadRechazadas, int cantidadSolicitadas,
            String montoTotalRevertido) {
        this.cantidad = cantidad;
        this.cantidadAutorizadas = cantidadAutorizadas;
        this.cantidadRechazadas = cantidadRechazadas;
        this.cantidadSolicitadas = cantidadSolicitadas;
        this.montoTotalRevertido = montoTotalRevertido;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getCantidadAutorizadas() {
        return cantidadAutorizadas;
    }

    public int getCantidadRechazadas() {
        return cantidadRechazadas;
    }

    public int getCantidadSolicitadas() {
        return cantidadSolicitadas;
    }

    public String getMontoTotalRevertido() {
        return montoTotalRevertido;
    }
}
