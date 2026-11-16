package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** B2 (Bloque 7/E10, pedido de Joel): una fila del informe de Solicitudes (/reportes/solicitudes)
 * -- la MISMA fila alimenta la tabla en pantalla, el informe PDF y el CSV (mismo criterio que
 * FilaInformePrestamo/B1). montoSolicitado queda en BigDecimal (sin formatear) para los totales y
 * el CSV numérico; montoSolicitadoFormateado es el mismo valor en guaraníes para pantalla/PDF. */
public class FilaInformeSolicitud {

    private final long idSolicitud;
    private final String numero;
    private final String fecha;
    private final String clienteNombre;
    private final String clienteCedula;
    private final BigDecimal montoSolicitado;
    private final String montoSolicitadoFormateado;
    private final int plazoMeses;
    private final String estado;
    private final String oficialNombre;

    public FilaInformeSolicitud(long idSolicitud, String numero, String fecha, String clienteNombre,
            String clienteCedula, BigDecimal montoSolicitado, String montoSolicitadoFormateado, int plazoMeses,
            String estado, String oficialNombre) {
        this.idSolicitud = idSolicitud;
        this.numero = numero;
        this.fecha = fecha;
        this.clienteNombre = clienteNombre;
        this.clienteCedula = clienteCedula;
        this.montoSolicitado = montoSolicitado;
        this.montoSolicitadoFormateado = montoSolicitadoFormateado;
        this.plazoMeses = plazoMeses;
        this.estado = estado;
        this.oficialNombre = oficialNombre;
    }

    public long getIdSolicitud() {
        return idSolicitud;
    }

    public String getNumero() {
        return numero;
    }

    public String getFecha() {
        return fecha;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteCedula() {
        return clienteCedula;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public String getMontoSolicitadoFormateado() {
        return montoSolicitadoFormateado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public String getEstado() {
        return estado;
    }

    public String getOficialNombre() {
        return oficialNombre;
    }
}
