package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion;

/** Espejo de la tabla reversiones_pago (bd/01_esquema.sql, Doc. 00 §7/PO-13, E9/Bloque 5). */
public class ReversionPago {

    private Long idReversionPago;
    private String identificadorConfirmacion;
    private long idPago;
    private String motivoReversion;
    private LocalDateTime fechaHoraReversion;
    private Long autorizadoPor;
    private Long procesadoPor;
    private Long idSesionCaja;
    private BigDecimal montoSalidaCaja;
    private Long idArchivoComprobante;
    private EstadoReversion estado;
    private long solicitadoPor;
    private LocalDateTime fechaHoraSolicitud;
    private String motivoRechazo;
    private Boolean facturaEnPoderConfirmado;

    public Long getIdReversionPago() {
        return idReversionPago;
    }

    public void setIdReversionPago(Long idReversionPago) {
        this.idReversionPago = idReversionPago;
    }

    public String getIdentificadorConfirmacion() {
        return identificadorConfirmacion;
    }

    public void setIdentificadorConfirmacion(String identificadorConfirmacion) {
        this.identificadorConfirmacion = identificadorConfirmacion;
    }

    public long getIdPago() {
        return idPago;
    }

    public void setIdPago(long idPago) {
        this.idPago = idPago;
    }

    public String getMotivoReversion() {
        return motivoReversion;
    }

    public void setMotivoReversion(String motivoReversion) {
        this.motivoReversion = motivoReversion;
    }

    public LocalDateTime getFechaHoraReversion() {
        return fechaHoraReversion;
    }

    public void setFechaHoraReversion(LocalDateTime fechaHoraReversion) {
        this.fechaHoraReversion = fechaHoraReversion;
    }

    public Long getAutorizadoPor() {
        return autorizadoPor;
    }

    public void setAutorizadoPor(Long autorizadoPor) {
        this.autorizadoPor = autorizadoPor;
    }

    public Long getProcesadoPor() {
        return procesadoPor;
    }

    public void setProcesadoPor(Long procesadoPor) {
        this.procesadoPor = procesadoPor;
    }

    public Long getIdSesionCaja() {
        return idSesionCaja;
    }

    public void setIdSesionCaja(Long idSesionCaja) {
        this.idSesionCaja = idSesionCaja;
    }

    public BigDecimal getMontoSalidaCaja() {
        return montoSalidaCaja;
    }

    public void setMontoSalidaCaja(BigDecimal montoSalidaCaja) {
        this.montoSalidaCaja = montoSalidaCaja;
    }

    public Long getIdArchivoComprobante() {
        return idArchivoComprobante;
    }

    public void setIdArchivoComprobante(Long idArchivoComprobante) {
        this.idArchivoComprobante = idArchivoComprobante;
    }

    public EstadoReversion getEstado() {
        return estado;
    }

    public void setEstado(EstadoReversion estado) {
        this.estado = estado;
    }

    public long getSolicitadoPor() {
        return solicitadoPor;
    }

    public void setSolicitadoPor(long solicitadoPor) {
        this.solicitadoPor = solicitadoPor;
    }

    public LocalDateTime getFechaHoraSolicitud() {
        return fechaHoraSolicitud;
    }

    public void setFechaHoraSolicitud(LocalDateTime fechaHoraSolicitud) {
        this.fechaHoraSolicitud = fechaHoraSolicitud;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Boolean getFacturaEnPoderConfirmado() {
        return facturaEnPoderConfirmado;
    }

    public void setFacturaEnPoderConfirmado(Boolean facturaEnPoderConfirmado) {
        this.facturaEnPoderConfirmado = facturaEnPoderConfirmado;
    }
}
