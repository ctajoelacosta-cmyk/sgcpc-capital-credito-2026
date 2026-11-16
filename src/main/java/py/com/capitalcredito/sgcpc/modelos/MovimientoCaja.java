package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;

/** Espejo de la tabla movimientos_caja (bd/01_esquema.sql, Doc. 00 §10.1, P07, E6). */
public class MovimientoCaja {

    private Long idMovimientoCaja;
    private long idSesionCaja;
    private TipoMovimientoCaja tipo;
    private BigDecimal monto;
    private int signo;
    private String referenciaTipo;
    private long idReferencia;
    private long registradoPor;
    private Long confirmadoPor;
    private LocalDateTime fechaHora;
    private String observacion;

    public Long getIdMovimientoCaja() {
        return idMovimientoCaja;
    }

    public void setIdMovimientoCaja(Long idMovimientoCaja) {
        this.idMovimientoCaja = idMovimientoCaja;
    }

    public long getIdSesionCaja() {
        return idSesionCaja;
    }

    public void setIdSesionCaja(long idSesionCaja) {
        this.idSesionCaja = idSesionCaja;
    }

    public TipoMovimientoCaja getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimientoCaja tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public int getSigno() {
        return signo;
    }

    public void setSigno(int signo) {
        this.signo = signo;
    }

    public String getReferenciaTipo() {
        return referenciaTipo;
    }

    public void setReferenciaTipo(String referenciaTipo) {
        this.referenciaTipo = referenciaTipo;
    }

    public long getIdReferencia() {
        return idReferencia;
    }

    public void setIdReferencia(long idReferencia) {
        this.idReferencia = idReferencia;
    }

    public long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public Long getConfirmadoPor() {
        return confirmadoPor;
    }

    public void setConfirmadoPor(Long confirmadoPor) {
        this.confirmadoPor = confirmadoPor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
