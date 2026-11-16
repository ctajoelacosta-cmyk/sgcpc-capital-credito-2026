package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/**
 * B2 (Bloque 7/E10, pedido de Joel): filtros de /reportes/solicitudes -- mismo criterio que
 * FiltroPrestamos/FiltroClientes (D-135/B1): un único objeto compartido por la pantalla, el
 * informe PDF y el CSV. estado es el name() de EstadoSolicitud, o null = todos; idOficial es
 * id_usuario de creado_por, o null = todos los oficiales.
 */
public class FiltroSolicitudesReporte {

    private LocalDate desde;
    private LocalDate hasta;
    private String estado;
    private Long idOficial;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getIdOficial() {
        return idOficial;
    }

    public void setIdOficial(Long idOficial) {
        this.idOficial = idOficial;
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
