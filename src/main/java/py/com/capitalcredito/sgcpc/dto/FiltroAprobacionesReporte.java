package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/**
 * B3 (Bloque 7/E10, pedido de Joel): filtros de /reportes/aprobaciones -- solo el rango de fecha
 * de decisión (el informe lista las solicitudes ya decididas en ese período, APROBADA o
 * RECHAZADA; no hay filtro de estado/oficial porque Joel no pidió ninguno para este informe).
 */
public class FiltroAprobacionesReporte {

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

    /** 13/10/2026 (pedido de Joel): "Cliente u operación" -- nombre, C.I. o código de la
     * solicitud. Se aplica en Java (ServicioReportes.coincideClienteUOperacion), no en SQL. */
    public String getClienteUOperacion() {
        return clienteUOperacion;
    }

    public void setClienteUOperacion(String clienteUOperacion) {
        this.clienteUOperacion = clienteUOperacion;
    }
}
