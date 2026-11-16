package py.com.capitalcredito.sgcpc.dto;

import java.util.Map;

/** Totales del reporte Gestiones de cobranza (§13, Bloque 4 E8; ampliado Bloque C tanda 3, pedido
 * de Joel; C-3b): cantidad por tipo (etiqueta -> cantidad, ej. "Llamada" -> 5), cuántas promesas se
 * hicieron, cuántas se cumplieron, cuántas quedaron vencidas sin cumplir y cuántas siguen
 * pendientes (todavía no vencen), en el rango. Las tres últimas son mutuamente excluyentes y entre
 * las tres suman exactamente cantidadPromesasHechas. */
public class TotalesGestiones {

    private final int cantidadTotal;
    private final Map<String, Integer> cantidadPorTipo;
    private final int cantidadPromesasHechas;
    private final int cantidadCumplidas;
    private final int cantidadVencidasSinCumplir;
    private final int cantidadPendientes;

    public TotalesGestiones(int cantidadTotal, Map<String, Integer> cantidadPorTipo, int cantidadPromesasHechas,
            int cantidadCumplidas, int cantidadVencidasSinCumplir, int cantidadPendientes) {
        this.cantidadTotal = cantidadTotal;
        this.cantidadPorTipo = cantidadPorTipo;
        this.cantidadPromesasHechas = cantidadPromesasHechas;
        this.cantidadCumplidas = cantidadCumplidas;
        this.cantidadVencidasSinCumplir = cantidadVencidasSinCumplir;
        this.cantidadPendientes = cantidadPendientes;
    }

    public int getCantidadTotal() {
        return cantidadTotal;
    }

    public Map<String, Integer> getCantidadPorTipo() {
        return cantidadPorTipo;
    }

    public int getCantidadPromesasHechas() {
        return cantidadPromesasHechas;
    }

    public int getCantidadCumplidas() {
        return cantidadCumplidas;
    }

    public int getCantidadVencidasSinCumplir() {
        return cantidadVencidasSinCumplir;
    }

    /** C-3b (pedido de Joel): promesas que todavía no llegaron a su fecha -- ni cumplidas ni
     * vencidas sin cumplir. */
    public int getCantidadPendientes() {
        return cantidadPendientes;
    }
}
