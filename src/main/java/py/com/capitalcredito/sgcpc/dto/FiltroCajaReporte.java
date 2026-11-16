package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/** B5 (Bloque 7/E10, pedido de Joel): filtros de /reportes/caja -- solo el rango de fecha (por
 * fecha de cierre de la sesión), mismo criterio que ya tenía la pantalla antes de B5. */
public class FiltroCajaReporte {

    private LocalDate desde;
    private LocalDate hasta;

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }
}
