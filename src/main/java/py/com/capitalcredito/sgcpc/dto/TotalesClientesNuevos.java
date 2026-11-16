package py.com.capitalcredito.sgcpc.dto;

import java.util.Map;

/** Totales del informe Clientes nuevos (Bloque C tanda 3, pedido de Joel): cantidad total y
 * subtotales por ciudad ("Sin ciudad" para quien no tiene una cargada). */
public class TotalesClientesNuevos {

    private final int cantidad;
    private final Map<String, Integer> cantidadPorCiudad;

    public TotalesClientesNuevos(int cantidad, Map<String, Integer> cantidadPorCiudad) {
        this.cantidad = cantidad;
        this.cantidadPorCiudad = cantidadPorCiudad;
    }

    public int getCantidad() {
        return cantidad;
    }

    public Map<String, Integer> getCantidadPorCiudad() {
        return cantidadPorCiudad;
    }
}
