package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** B3 (Bloque 7/E10, pedido de Joel): una fila del informe de Aprobaciones y rechazos
 * (/reportes/aprobaciones) -- la MISMA fila alimenta la tabla en pantalla, el informe PDF y el
 * CSV (mismo criterio que FilaInformeSolicitud/B2). montoAprobado es null para las rechazadas
 * (RN-07: solo se aprueba con el monto solicitado, y rechazar nunca lo toca -- ver
 * ServicioAprobacionCredito.rechazar); montoAprobadoFormateado queda en "—" en ese caso. */
public class FilaInformeAprobacion {

    private final long idSolicitud;
    private final String numero;
    private final String clienteNombre;
    private final BigDecimal montoSolicitado;
    private final String montoSolicitadoFormateado;
    private final BigDecimal montoAprobado;
    private final String montoAprobadoFormateado;
    private final String decision;
    private final String decididoPorNombre;
    private final String fechaEnvio;
    private final String fechaDecision;
    private final long tiempoRespuestaMinutos;
    private final String tiempoRespuestaFormateado;
    private final String motivo;

    public FilaInformeAprobacion(long idSolicitud, String numero, String clienteNombre, BigDecimal montoSolicitado,
            String montoSolicitadoFormateado, BigDecimal montoAprobado, String montoAprobadoFormateado,
            String decision, String decididoPorNombre, String fechaEnvio, String fechaDecision,
            long tiempoRespuestaMinutos, String tiempoRespuestaFormateado, String motivo) {
        this.idSolicitud = idSolicitud;
        this.numero = numero;
        this.clienteNombre = clienteNombre;
        this.montoSolicitado = montoSolicitado;
        this.montoSolicitadoFormateado = montoSolicitadoFormateado;
        this.montoAprobado = montoAprobado;
        this.montoAprobadoFormateado = montoAprobadoFormateado;
        this.decision = decision;
        this.decididoPorNombre = decididoPorNombre;
        this.fechaEnvio = fechaEnvio;
        this.fechaDecision = fechaDecision;
        this.tiempoRespuestaMinutos = tiempoRespuestaMinutos;
        this.tiempoRespuestaFormateado = tiempoRespuestaFormateado;
        this.motivo = motivo;
    }

    public long getIdSolicitud() {
        return idSolicitud;
    }

    public String getNumero() {
        return numero;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public String getMontoSolicitadoFormateado() {
        return montoSolicitadoFormateado;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public String getMontoAprobadoFormateado() {
        return montoAprobadoFormateado;
    }

    public String getDecision() {
        return decision;
    }

    public String getDecididoPorNombre() {
        return decididoPorNombre;
    }

    public String getFechaEnvio() {
        return fechaEnvio;
    }

    public String getFechaDecision() {
        return fechaDecision;
    }

    public long getTiempoRespuestaMinutos() {
        return tiempoRespuestaMinutos;
    }

    public String getTiempoRespuestaFormateado() {
        return tiempoRespuestaFormateado;
    }

    public String getMotivo() {
        return motivo;
    }
}
