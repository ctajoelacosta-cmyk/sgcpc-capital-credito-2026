package py.com.capitalcredito.sgcpc.dto;

import java.util.Map;

/** Totales del informe "Consultas al buró" (C-4.4, pedido de Joel): cantidad total, cantidad por
 * resultado/clasificación y cantidad de clientes distintos (una misma persona puede aparecer en
 * más de una fila). */
public class TotalesConsultasBuro {

    private final int cantidadTotal;
    private final Map<String, Integer> cantidadPorResultado;
    private final int cantidadClientesDistintos;

    public TotalesConsultasBuro(int cantidadTotal, Map<String, Integer> cantidadPorResultado,
            int cantidadClientesDistintos) {
        this.cantidadTotal = cantidadTotal;
        this.cantidadPorResultado = cantidadPorResultado;
        this.cantidadClientesDistintos = cantidadClientesDistintos;
    }

    public int getCantidadTotal() {
        return cantidadTotal;
    }

    public Map<String, Integer> getCantidadPorResultado() {
        return cantidadPorResultado;
    }

    public int getCantidadClientesDistintos() {
        return cantidadClientesDistintos;
    }
}
