package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;

/** Espejo de la tabla aplicaciones_pago (bd/01_esquema.sql, RN-10, P09, E7). */
public class AplicacionPago {

    private Long idAplicacionPago;
    private long idPago;
    private TipoAplicacion tipoAplicacion;
    private Long idCuota;
    private BigDecimal montoMoratorio = BigDecimal.ZERO;
    private BigDecimal montoPunitorio = BigDecimal.ZERO;
    private BigDecimal montoInteres = BigDecimal.ZERO;
    private BigDecimal montoTributos = BigDecimal.ZERO;
    private BigDecimal montoCapital = BigDecimal.ZERO;
    private BigDecimal subtotalAplicado;

    public Long getIdAplicacionPago() {
        return idAplicacionPago;
    }

    public void setIdAplicacionPago(Long idAplicacionPago) {
        this.idAplicacionPago = idAplicacionPago;
    }

    public long getIdPago() {
        return idPago;
    }

    public void setIdPago(long idPago) {
        this.idPago = idPago;
    }

    public TipoAplicacion getTipoAplicacion() {
        return tipoAplicacion;
    }

    public void setTipoAplicacion(TipoAplicacion tipoAplicacion) {
        this.tipoAplicacion = tipoAplicacion;
    }

    public Long getIdCuota() {
        return idCuota;
    }

    public void setIdCuota(Long idCuota) {
        this.idCuota = idCuota;
    }

    public BigDecimal getMontoMoratorio() {
        return montoMoratorio;
    }

    public void setMontoMoratorio(BigDecimal montoMoratorio) {
        this.montoMoratorio = montoMoratorio;
    }

    public BigDecimal getMontoPunitorio() {
        return montoPunitorio;
    }

    public void setMontoPunitorio(BigDecimal montoPunitorio) {
        this.montoPunitorio = montoPunitorio;
    }

    public BigDecimal getMontoInteres() {
        return montoInteres;
    }

    public void setMontoInteres(BigDecimal montoInteres) {
        this.montoInteres = montoInteres;
    }

    public BigDecimal getMontoTributos() {
        return montoTributos;
    }

    public void setMontoTributos(BigDecimal montoTributos) {
        this.montoTributos = montoTributos;
    }

    public BigDecimal getMontoCapital() {
        return montoCapital;
    }

    public void setMontoCapital(BigDecimal montoCapital) {
        this.montoCapital = montoCapital;
    }

    public BigDecimal getSubtotalAplicado() {
        return subtotalAplicado;
    }

    public void setSubtotalAplicado(BigDecimal subtotalAplicado) {
        this.subtotalAplicado = subtotalAplicado;
    }
}
