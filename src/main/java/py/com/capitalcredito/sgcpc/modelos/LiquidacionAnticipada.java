package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoLiquidacionAnticipada;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoLiquidacion;

/** Espejo de la tabla liquidaciones_anticipadas (bd/01_esquema.sql, RN-11, PO-10, E7). */
public class LiquidacionAnticipada {

    private Long idLiquidacion;
    private long idPrestamo;
    private TipoLiquidacion tipoLiquidacion;
    private LocalDateTime fechaCalculo;
    private BigDecimal saldoCapitalFecha;
    private BigDecimal interesDevengado;
    private BigDecimal tributosAplicables;
    private BigDecimal interesMoratorio;
    private BigDecimal interesPunitorio;
    private BigDecimal montoTotalLiquidacion;
    private BigDecimal montoAnticipoCapital;
    private BigDecimal nuevoSaldoCapital;
    private EstadoLiquidacionAnticipada estadoLiquidacion;
    private long calculadoPor;
    private LocalDateTime fechaAplicacion;
    private Long idArchivoLiquidacion;
    // D-93/D-94 (E8): numero_constancia solo para tipo_liquidacion=TOTAL; los otros 3, el
    // desglose de "vencidas con mora e IVA" que antes se descartaba al persistir.
    private String numeroConstancia;
    private BigDecimal montoVencidasImpagas = BigDecimal.ZERO;
    private BigDecimal ivaMoratorio = BigDecimal.ZERO;
    private BigDecimal ivaPunitorio = BigDecimal.ZERO;

    public Long getIdLiquidacion() {
        return idLiquidacion;
    }

    public void setIdLiquidacion(Long idLiquidacion) {
        this.idLiquidacion = idLiquidacion;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public TipoLiquidacion getTipoLiquidacion() {
        return tipoLiquidacion;
    }

    public void setTipoLiquidacion(TipoLiquidacion tipoLiquidacion) {
        this.tipoLiquidacion = tipoLiquidacion;
    }

    public LocalDateTime getFechaCalculo() {
        return fechaCalculo;
    }

    public void setFechaCalculo(LocalDateTime fechaCalculo) {
        this.fechaCalculo = fechaCalculo;
    }

    public BigDecimal getSaldoCapitalFecha() {
        return saldoCapitalFecha;
    }

    public void setSaldoCapitalFecha(BigDecimal saldoCapitalFecha) {
        this.saldoCapitalFecha = saldoCapitalFecha;
    }

    public BigDecimal getInteresDevengado() {
        return interesDevengado;
    }

    public void setInteresDevengado(BigDecimal interesDevengado) {
        this.interesDevengado = interesDevengado;
    }

    public BigDecimal getTributosAplicables() {
        return tributosAplicables;
    }

    public void setTributosAplicables(BigDecimal tributosAplicables) {
        this.tributosAplicables = tributosAplicables;
    }

    public BigDecimal getInteresMoratorio() {
        return interesMoratorio;
    }

    public void setInteresMoratorio(BigDecimal interesMoratorio) {
        this.interesMoratorio = interesMoratorio;
    }

    public BigDecimal getInteresPunitorio() {
        return interesPunitorio;
    }

    public void setInteresPunitorio(BigDecimal interesPunitorio) {
        this.interesPunitorio = interesPunitorio;
    }

    public BigDecimal getMontoTotalLiquidacion() {
        return montoTotalLiquidacion;
    }

    public void setMontoTotalLiquidacion(BigDecimal montoTotalLiquidacion) {
        this.montoTotalLiquidacion = montoTotalLiquidacion;
    }

    public BigDecimal getMontoAnticipoCapital() {
        return montoAnticipoCapital;
    }

    public void setMontoAnticipoCapital(BigDecimal montoAnticipoCapital) {
        this.montoAnticipoCapital = montoAnticipoCapital;
    }

    public BigDecimal getNuevoSaldoCapital() {
        return nuevoSaldoCapital;
    }

    public void setNuevoSaldoCapital(BigDecimal nuevoSaldoCapital) {
        this.nuevoSaldoCapital = nuevoSaldoCapital;
    }

    public EstadoLiquidacionAnticipada getEstadoLiquidacion() {
        return estadoLiquidacion;
    }

    public void setEstadoLiquidacion(EstadoLiquidacionAnticipada estadoLiquidacion) {
        this.estadoLiquidacion = estadoLiquidacion;
    }

    public long getCalculadoPor() {
        return calculadoPor;
    }

    public void setCalculadoPor(long calculadoPor) {
        this.calculadoPor = calculadoPor;
    }

    public LocalDateTime getFechaAplicacion() {
        return fechaAplicacion;
    }

    public void setFechaAplicacion(LocalDateTime fechaAplicacion) {
        this.fechaAplicacion = fechaAplicacion;
    }

    public Long getIdArchivoLiquidacion() {
        return idArchivoLiquidacion;
    }

    public void setIdArchivoLiquidacion(Long idArchivoLiquidacion) {
        this.idArchivoLiquidacion = idArchivoLiquidacion;
    }

    public String getNumeroConstancia() {
        return numeroConstancia;
    }

    public void setNumeroConstancia(String numeroConstancia) {
        this.numeroConstancia = numeroConstancia;
    }

    public BigDecimal getMontoVencidasImpagas() {
        return montoVencidasImpagas;
    }

    public void setMontoVencidasImpagas(BigDecimal montoVencidasImpagas) {
        this.montoVencidasImpagas = montoVencidasImpagas;
    }

    public BigDecimal getIvaMoratorio() {
        return ivaMoratorio;
    }

    public void setIvaMoratorio(BigDecimal ivaMoratorio) {
        this.ivaMoratorio = ivaMoratorio;
    }

    public BigDecimal getIvaPunitorio() {
        return ivaPunitorio;
    }

    public void setIvaPunitorio(BigDecimal ivaPunitorio) {
        this.ivaPunitorio = ivaPunitorio;
    }
}
