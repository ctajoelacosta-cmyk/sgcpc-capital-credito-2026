package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSesionCaja;

/** Espejo de la tabla sesiones_caja (bd/01_esquema.sql, Doc. 00 §10.1, P07, E6). */
public class SesionCaja {

    private Long idSesionCaja;
    private long idCajero;
    private LocalDateTime fechaHoraApertura;
    private BigDecimal saldoInicial;
    private LocalDateTime fechaHoraCierre;
    private BigDecimal saldoTeoricoCierre;
    private BigDecimal saldoFisico;
    private BigDecimal diferenciaArqueo;
    private EstadoSesionCaja estadoSesion;
    private Long idArchivoCierre;
    private String motivoDiferencia;
    private Long idGerenteRevisor;

    public Long getIdSesionCaja() {
        return idSesionCaja;
    }

    public void setIdSesionCaja(Long idSesionCaja) {
        this.idSesionCaja = idSesionCaja;
    }

    public long getIdCajero() {
        return idCajero;
    }

    public void setIdCajero(long idCajero) {
        this.idCajero = idCajero;
    }

    public LocalDateTime getFechaHoraApertura() {
        return fechaHoraApertura;
    }

    public void setFechaHoraApertura(LocalDateTime fechaHoraApertura) {
        this.fechaHoraApertura = fechaHoraApertura;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public LocalDateTime getFechaHoraCierre() {
        return fechaHoraCierre;
    }

    public void setFechaHoraCierre(LocalDateTime fechaHoraCierre) {
        this.fechaHoraCierre = fechaHoraCierre;
    }

    public BigDecimal getSaldoTeoricoCierre() {
        return saldoTeoricoCierre;
    }

    public void setSaldoTeoricoCierre(BigDecimal saldoTeoricoCierre) {
        this.saldoTeoricoCierre = saldoTeoricoCierre;
    }

    public BigDecimal getSaldoFisico() {
        return saldoFisico;
    }

    public void setSaldoFisico(BigDecimal saldoFisico) {
        this.saldoFisico = saldoFisico;
    }

    public BigDecimal getDiferenciaArqueo() {
        return diferenciaArqueo;
    }

    public void setDiferenciaArqueo(BigDecimal diferenciaArqueo) {
        this.diferenciaArqueo = diferenciaArqueo;
    }

    public EstadoSesionCaja getEstadoSesion() {
        return estadoSesion;
    }

    public void setEstadoSesion(EstadoSesionCaja estadoSesion) {
        this.estadoSesion = estadoSesion;
    }

    public Long getIdArchivoCierre() {
        return idArchivoCierre;
    }

    public void setIdArchivoCierre(Long idArchivoCierre) {
        this.idArchivoCierre = idArchivoCierre;
    }

    public String getMotivoDiferencia() {
        return motivoDiferencia;
    }

    public void setMotivoDiferencia(String motivoDiferencia) {
        this.motivoDiferencia = motivoDiferencia;
    }

    public Long getIdGerenteRevisor() {
        return idGerenteRevisor;
    }

    public void setIdGerenteRevisor(Long idGerenteRevisor) {
        this.idGerenteRevisor = idGerenteRevisor;
    }
}
