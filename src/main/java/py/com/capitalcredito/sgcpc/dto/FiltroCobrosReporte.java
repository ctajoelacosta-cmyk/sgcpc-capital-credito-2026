package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/** B4 (Bloque 7/E10, pedido de Joel): filtros de /reportes/cobros -- solo el rango de fecha
 * (mismo criterio que FiltroAprobacionesReporte/B3: Joel no pidió otros filtros para este informe). */
public class FiltroCobrosReporte {

    private LocalDate desde;
    private LocalDate hasta;
    private String clienteUOperacion;

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

    /** 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o CC-aaaa-nnnnn. Se
     * aplica en Java (ServicioReportes.coincideClienteUOperacion), no en SQL. */
    public String getClienteUOperacion() {
        return clienteUOperacion;
    }

    public void setClienteUOperacion(String clienteUOperacion) {
        this.clienteUOperacion = clienteUOperacion;
    }
}
