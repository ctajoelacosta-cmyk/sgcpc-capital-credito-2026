package py.com.capitalcredito.sgcpc.dto;

import java.util.List;

/**
 * RF20 (pedido de Joel, 13/10/2026): resultado de ServicioCobranza.armarListaTrabajo -- las 3
 * categorías de la lista de trabajo de /cobranza (en mora, vencen hoy, próximos 7 días). Un
 * préstamo EN_MORA aparece solo en "enMora": "vencenHoy"/"proximos7Dias" ya vienen sin los
 * préstamos que están en "enMora" (la regla vive en el servicio, no en la pantalla ni en el
 * controlador -- REGLAS_DESARROLLO.md §13 punto 4).
 */
public class ListaTrabajoCobranza {

    private final List<FilaMora> enMora;
    private final List<FilaVencimiento> vencenHoy;
    private final List<FilaVencimiento> proximos7Dias;

    public ListaTrabajoCobranza(List<FilaMora> enMora, List<FilaVencimiento> vencenHoy,
            List<FilaVencimiento> proximos7Dias) {
        this.enMora = enMora;
        this.vencenHoy = vencenHoy;
        this.proximos7Dias = proximos7Dias;
    }

    public List<FilaMora> getEnMora() {
        return enMora;
    }

    public List<FilaVencimiento> getVencenHoy() {
        return vencenHoy;
    }

    public List<FilaVencimiento> getProximos7Dias() {
        return proximos7Dias;
    }
}
