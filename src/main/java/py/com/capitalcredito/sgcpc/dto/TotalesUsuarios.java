package py.com.capitalcredito.sgcpc.dto;

import java.util.Map;

/** Totales del informe "Usuarios y accesos" (C-4.2, pedido de Joel): cantidad total, por perfil y
 * por estado de cuenta. */
public class TotalesUsuarios {

    private final int cantidadTotal;
    private final Map<String, Integer> cantidadPorPerfil;
    private final Map<String, Integer> cantidadPorEstado;

    public TotalesUsuarios(int cantidadTotal, Map<String, Integer> cantidadPorPerfil,
            Map<String, Integer> cantidadPorEstado) {
        this.cantidadTotal = cantidadTotal;
        this.cantidadPorPerfil = cantidadPorPerfil;
        this.cantidadPorEstado = cantidadPorEstado;
    }

    public int getCantidadTotal() {
        return cantidadTotal;
    }

    public Map<String, Integer> getCantidadPorPerfil() {
        return cantidadPorPerfil;
    }

    public Map<String, Integer> getCantidadPorEstado() {
        return cantidadPorEstado;
    }
}
