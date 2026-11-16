package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDesembolso;

/** Espejo de la tabla desembolsos (bd/01_esquema.sql, Doc. 00 §10.1, P08, E6). */
public class Desembolso {

    private Long idDesembolso;
    private String identificadorConfirmacion;
    private long idPrestamo;
    private long idSesionCaja;
    private BigDecimal montoAprobadoSnapshot;
    private BigDecimal montoNetoEntregado;
    private LocalDateTime fechaHoraDesembolso;
    private long desembolsadoPor;
    private EstadoDesembolso estadoDesembolso;
    private Long idArchivoComprobante;
    private String numeroComprobante;

    public Long getIdDesembolso() {
        return idDesembolso;
    }

    public void setIdDesembolso(Long idDesembolso) {
        this.idDesembolso = idDesembolso;
    }

    public String getIdentificadorConfirmacion() {
        return identificadorConfirmacion;
    }

    public void setIdentificadorConfirmacion(String identificadorConfirmacion) {
        this.identificadorConfirmacion = identificadorConfirmacion;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public long getIdSesionCaja() {
        return idSesionCaja;
    }

    public void setIdSesionCaja(long idSesionCaja) {
        this.idSesionCaja = idSesionCaja;
    }

    public BigDecimal getMontoAprobadoSnapshot() {
        return montoAprobadoSnapshot;
    }

    public void setMontoAprobadoSnapshot(BigDecimal montoAprobadoSnapshot) {
        this.montoAprobadoSnapshot = montoAprobadoSnapshot;
    }

    public BigDecimal getMontoNetoEntregado() {
        return montoNetoEntregado;
    }

    public void setMontoNetoEntregado(BigDecimal montoNetoEntregado) {
        this.montoNetoEntregado = montoNetoEntregado;
    }

    public LocalDateTime getFechaHoraDesembolso() {
        return fechaHoraDesembolso;
    }

    public void setFechaHoraDesembolso(LocalDateTime fechaHoraDesembolso) {
        this.fechaHoraDesembolso = fechaHoraDesembolso;
    }

    public long getDesembolsadoPor() {
        return desembolsadoPor;
    }

    public void setDesembolsadoPor(long desembolsadoPor) {
        this.desembolsadoPor = desembolsadoPor;
    }

    public EstadoDesembolso getEstadoDesembolso() {
        return estadoDesembolso;
    }

    public void setEstadoDesembolso(EstadoDesembolso estadoDesembolso) {
        this.estadoDesembolso = estadoDesembolso;
    }

    public Long getIdArchivoComprobante() {
        return idArchivoComprobante;
    }

    public void setIdArchivoComprobante(Long idArchivoComprobante) {
        this.idArchivoComprobante = idArchivoComprobante;
    }

    /** D-93/D-94 (E8): DES-aaaa-nnnnnn, asignado en la misma transacción del desembolso. */
    public String getNumeroComprobante() {
        return numeroComprobante;
    }

    public void setNumeroComprobante(String numeroComprobante) {
        this.numeroComprobante = numeroComprobante;
    }
}
