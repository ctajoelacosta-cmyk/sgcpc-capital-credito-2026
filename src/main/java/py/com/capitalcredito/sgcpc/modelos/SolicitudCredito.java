package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;

/**
 * Solicitud de crédito (Doc. 08 Tabla 10, P04/P05, E4). Espejo plano de
 * solicitudes_credito: id_cliente es un id, no un objeto Persona
 * (igual que Usuario/Persona) -- el controlador busca esa Persona por
 * separado, como ya hace ControladorClientesServlet.
 */
public class SolicitudCredito {

    private Long idSolicitud;
    private String codigoSolicitud;
    private long idCliente;
    private long creadoPor;
    private LocalDateTime fechaSolicitud;
    private BigDecimal montoSolicitado;
    private int plazoSolicitadoMeses;
    private String destinoCredito;
    private LocalDate primeraFechaVencimientoSolicitada;
    private BigDecimal tasaTnaPropuesta;
    private BigDecimal tasaTeaSimulada;
    private BigDecimal cftSimulado;
    private BigDecimal cuotaSimulada;
    private BigDecimal ingresoClienteEvaluado;
    private BigDecimal limiteAfectacionAplicado;
    private BigDecimal porcentajeAfectacion;
    private EstadoSolicitud estadoSolicitud;
    private LocalDateTime fechaEnvioAprobacion;
    private BigDecimal montoAprobado;
    private Integer plazoAprobadoMeses;
    private BigDecimal tasaTnaAprobada;
    private BigDecimal tasaTeaAprobada;
    private BigDecimal cftAprobado;
    private LocalDate primeraFechaVencimientoAprobada;
    private Long idLimiteTasaAplicado;
    private Long decididoPor;
    private LocalDateTime fechaDecision;
    private String motivoRechazo;
    /** Bloque 7/E10, Tanda A (D-126): el motivo elegido de motivos_rechazo_solicitud; motivoRechazo (arriba) es el texto combinado nombre + detalle. */
    private Long idMotivoRechazo;
    private Long canceladoPor;
    private LocalDateTime fechaCancelacion;
    private String motivoCancelacion;
    /** E5/D-68: en TRUE una vez que existe un préstamo para esta solicitud (RN-01/02/28). */
    private boolean formalizada;

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(Long idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public void setCodigoSolicitud(String codigoSolicitud) {
        this.codigoSolicitud = codigoSolicitud;
    }

    public long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(long idCliente) {
        this.idCliente = idCliente;
    }

    public long getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(long creadoPor) {
        this.creadoPor = creadoPor;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public void setMontoSolicitado(BigDecimal montoSolicitado) {
        this.montoSolicitado = montoSolicitado;
    }

    public int getPlazoSolicitadoMeses() {
        return plazoSolicitadoMeses;
    }

    public void setPlazoSolicitadoMeses(int plazoSolicitadoMeses) {
        this.plazoSolicitadoMeses = plazoSolicitadoMeses;
    }

    public String getDestinoCredito() {
        return destinoCredito;
    }

    public void setDestinoCredito(String destinoCredito) {
        this.destinoCredito = destinoCredito;
    }

    public LocalDate getPrimeraFechaVencimientoSolicitada() {
        return primeraFechaVencimientoSolicitada;
    }

    public void setPrimeraFechaVencimientoSolicitada(LocalDate primeraFechaVencimientoSolicitada) {
        this.primeraFechaVencimientoSolicitada = primeraFechaVencimientoSolicitada;
    }

    public BigDecimal getTasaTnaPropuesta() {
        return tasaTnaPropuesta;
    }

    public void setTasaTnaPropuesta(BigDecimal tasaTnaPropuesta) {
        this.tasaTnaPropuesta = tasaTnaPropuesta;
    }

    public BigDecimal getTasaTeaSimulada() {
        return tasaTeaSimulada;
    }

    public void setTasaTeaSimulada(BigDecimal tasaTeaSimulada) {
        this.tasaTeaSimulada = tasaTeaSimulada;
    }

    public BigDecimal getCftSimulado() {
        return cftSimulado;
    }

    public void setCftSimulado(BigDecimal cftSimulado) {
        this.cftSimulado = cftSimulado;
    }

    public BigDecimal getCuotaSimulada() {
        return cuotaSimulada;
    }

    public void setCuotaSimulada(BigDecimal cuotaSimulada) {
        this.cuotaSimulada = cuotaSimulada;
    }

    public BigDecimal getIngresoClienteEvaluado() {
        return ingresoClienteEvaluado;
    }

    public void setIngresoClienteEvaluado(BigDecimal ingresoClienteEvaluado) {
        this.ingresoClienteEvaluado = ingresoClienteEvaluado;
    }

    public BigDecimal getLimiteAfectacionAplicado() {
        return limiteAfectacionAplicado;
    }

    public void setLimiteAfectacionAplicado(BigDecimal limiteAfectacionAplicado) {
        this.limiteAfectacionAplicado = limiteAfectacionAplicado;
    }

    public BigDecimal getPorcentajeAfectacion() {
        return porcentajeAfectacion;
    }

    public void setPorcentajeAfectacion(BigDecimal porcentajeAfectacion) {
        this.porcentajeAfectacion = porcentajeAfectacion;
    }

    public EstadoSolicitud getEstadoSolicitud() {
        return estadoSolicitud;
    }

    public void setEstadoSolicitud(EstadoSolicitud estadoSolicitud) {
        this.estadoSolicitud = estadoSolicitud;
    }

    public LocalDateTime getFechaEnvioAprobacion() {
        return fechaEnvioAprobacion;
    }

    public void setFechaEnvioAprobacion(LocalDateTime fechaEnvioAprobacion) {
        this.fechaEnvioAprobacion = fechaEnvioAprobacion;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public void setMontoAprobado(BigDecimal montoAprobado) {
        this.montoAprobado = montoAprobado;
    }

    public Integer getPlazoAprobadoMeses() {
        return plazoAprobadoMeses;
    }

    public void setPlazoAprobadoMeses(Integer plazoAprobadoMeses) {
        this.plazoAprobadoMeses = plazoAprobadoMeses;
    }

    public BigDecimal getTasaTnaAprobada() {
        return tasaTnaAprobada;
    }

    public void setTasaTnaAprobada(BigDecimal tasaTnaAprobada) {
        this.tasaTnaAprobada = tasaTnaAprobada;
    }

    public BigDecimal getTasaTeaAprobada() {
        return tasaTeaAprobada;
    }

    public void setTasaTeaAprobada(BigDecimal tasaTeaAprobada) {
        this.tasaTeaAprobada = tasaTeaAprobada;
    }

    public BigDecimal getCftAprobado() {
        return cftAprobado;
    }

    public void setCftAprobado(BigDecimal cftAprobado) {
        this.cftAprobado = cftAprobado;
    }

    public LocalDate getPrimeraFechaVencimientoAprobada() {
        return primeraFechaVencimientoAprobada;
    }

    public void setPrimeraFechaVencimientoAprobada(LocalDate primeraFechaVencimientoAprobada) {
        this.primeraFechaVencimientoAprobada = primeraFechaVencimientoAprobada;
    }

    public Long getIdLimiteTasaAplicado() {
        return idLimiteTasaAplicado;
    }

    public void setIdLimiteTasaAplicado(Long idLimiteTasaAplicado) {
        this.idLimiteTasaAplicado = idLimiteTasaAplicado;
    }

    public Long getDecididoPor() {
        return decididoPor;
    }

    public void setDecididoPor(Long decididoPor) {
        this.decididoPor = decididoPor;
    }

    public LocalDateTime getFechaDecision() {
        return fechaDecision;
    }

    public void setFechaDecision(LocalDateTime fechaDecision) {
        this.fechaDecision = fechaDecision;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Long getIdMotivoRechazo() {
        return idMotivoRechazo;
    }

    public void setIdMotivoRechazo(Long idMotivoRechazo) {
        this.idMotivoRechazo = idMotivoRechazo;
    }

    public Long getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(Long canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public LocalDateTime getFechaCancelacion() {
        return fechaCancelacion;
    }

    public void setFechaCancelacion(LocalDateTime fechaCancelacion) {
        this.fechaCancelacion = fechaCancelacion;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }

    public boolean isFormalizada() {
        return formalizada;
    }

    public void setFormalizada(boolean formalizada) {
        this.formalizada = formalizada;
    }
}
