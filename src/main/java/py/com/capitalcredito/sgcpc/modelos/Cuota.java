package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;

/**
 * Estado de una cuota de un plan de pagos. Espejo de las columnas de la
 * tabla cuotas (bd/01_esquema.sql) más fechaVencimientoTeorica (D-23: fecha
 * de vencimiento SIN el corrimiento de RN-19, usada solo para prorratear
 * interés en 6.6/6.7) y el IVA de mora pendiente separado en moratorio y
 * punitorio (ivaMoratorioPendiente/ivaPunitorioPendiente) en vez de una
 * sola columna combinada, para poder aplicar el orden de RN-10 sin
 * ambigüedad. ServicioMotorFinanciero nunca modifica una Cuota que recibe
 * como entrada: cuando corresponde, devuelve una copia con los campos
 * actualizados (ver Cuota(Cuota)).
 */
public class Cuota {

    /** Null para una cuota recién generada en memoria (E1/E5); seteado al leerla de la base (E7). */
    private Long idCuota;
    private int numeroCuota;
    private LocalDate fechaVencimiento;
    private LocalDate fechaVencimientoTeorica;
    private BigDecimal capitalProgramado;
    private BigDecimal interesProgramado;
    private BigDecimal tributosProgramados;
    private BigDecimal totalProgramado;
    private BigDecimal saldoCapitalPosterior;
    private EstadoPago estadoPago;
    private BigDecimal capitalPagado = BigDecimal.ZERO;
    private BigDecimal interesPagado = BigDecimal.ZERO;
    private BigDecimal moratorioPendiente = BigDecimal.ZERO;
    private BigDecimal punitorioPendiente = BigDecimal.ZERO;
    private BigDecimal ivaMoratorioPendiente = BigDecimal.ZERO;
    private BigDecimal ivaPunitorioPendiente = BigDecimal.ZERO;
    private LocalDate fechaCorteMora;

    public Cuota() {
    }

    public Cuota(Cuota otra) {
        this.idCuota = otra.idCuota;
        this.numeroCuota = otra.numeroCuota;
        this.fechaVencimiento = otra.fechaVencimiento;
        this.fechaVencimientoTeorica = otra.fechaVencimientoTeorica;
        this.capitalProgramado = otra.capitalProgramado;
        this.interesProgramado = otra.interesProgramado;
        this.tributosProgramados = otra.tributosProgramados;
        this.totalProgramado = otra.totalProgramado;
        this.saldoCapitalPosterior = otra.saldoCapitalPosterior;
        this.estadoPago = otra.estadoPago;
        this.capitalPagado = otra.capitalPagado;
        this.interesPagado = otra.interesPagado;
        this.moratorioPendiente = otra.moratorioPendiente;
        this.punitorioPendiente = otra.punitorioPendiente;
        this.ivaMoratorioPendiente = otra.ivaMoratorioPendiente;
        this.ivaPunitorioPendiente = otra.ivaPunitorioPendiente;
        this.fechaCorteMora = otra.fechaCorteMora;
    }

    public Long getIdCuota() {
        return idCuota;
    }

    public void setIdCuota(Long idCuota) {
        this.idCuota = idCuota;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public void setNumeroCuota(int numeroCuota) {
        this.numeroCuota = numeroCuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDate getFechaVencimientoTeorica() {
        return fechaVencimientoTeorica;
    }

    public void setFechaVencimientoTeorica(LocalDate fechaVencimientoTeorica) {
        this.fechaVencimientoTeorica = fechaVencimientoTeorica;
    }

    public BigDecimal getCapitalProgramado() {
        return capitalProgramado;
    }

    public void setCapitalProgramado(BigDecimal capitalProgramado) {
        this.capitalProgramado = capitalProgramado;
    }

    public BigDecimal getInteresProgramado() {
        return interesProgramado;
    }

    public void setInteresProgramado(BigDecimal interesProgramado) {
        this.interesProgramado = interesProgramado;
    }

    public BigDecimal getTributosProgramados() {
        return tributosProgramados;
    }

    public void setTributosProgramados(BigDecimal tributosProgramados) {
        this.tributosProgramados = tributosProgramados;
    }

    public BigDecimal getTotalProgramado() {
        return totalProgramado;
    }

    public void setTotalProgramado(BigDecimal totalProgramado) {
        this.totalProgramado = totalProgramado;
    }

    public BigDecimal getSaldoCapitalPosterior() {
        return saldoCapitalPosterior;
    }

    public void setSaldoCapitalPosterior(BigDecimal saldoCapitalPosterior) {
        this.saldoCapitalPosterior = saldoCapitalPosterior;
    }

    public EstadoPago getEstadoPago() {
        return estadoPago;
    }

    public void setEstadoPago(EstadoPago estadoPago) {
        this.estadoPago = estadoPago;
    }

    public BigDecimal getCapitalPagado() {
        return capitalPagado;
    }

    public void setCapitalPagado(BigDecimal capitalPagado) {
        this.capitalPagado = capitalPagado;
    }

    public BigDecimal getInteresPagado() {
        return interesPagado;
    }

    public void setInteresPagado(BigDecimal interesPagado) {
        this.interesPagado = interesPagado;
    }

    public BigDecimal getMoratorioPendiente() {
        return moratorioPendiente;
    }

    public void setMoratorioPendiente(BigDecimal moratorioPendiente) {
        this.moratorioPendiente = moratorioPendiente;
    }

    public BigDecimal getPunitorioPendiente() {
        return punitorioPendiente;
    }

    public void setPunitorioPendiente(BigDecimal punitorioPendiente) {
        this.punitorioPendiente = punitorioPendiente;
    }

    public BigDecimal getIvaMoratorioPendiente() {
        return ivaMoratorioPendiente;
    }

    public void setIvaMoratorioPendiente(BigDecimal ivaMoratorioPendiente) {
        this.ivaMoratorioPendiente = ivaMoratorioPendiente;
    }

    public BigDecimal getIvaPunitorioPendiente() {
        return ivaPunitorioPendiente;
    }

    public void setIvaPunitorioPendiente(BigDecimal ivaPunitorioPendiente) {
        this.ivaPunitorioPendiente = ivaPunitorioPendiente;
    }

    public LocalDate getFechaCorteMora() {
        return fechaCorteMora;
    }

    public void setFechaCorteMora(LocalDate fechaCorteMora) {
        this.fechaCorteMora = fechaCorteMora;
    }

    /** Capital adeudado a la fecha: programado menos lo ya pagado. */
    public BigDecimal capitalPendiente() {
        return capitalProgramado.subtract(capitalPagado);
    }

    /** Interés contractual adeudado a la fecha: programado menos lo ya pagado. */
    public BigDecimal interesPendiente() {
        return interesProgramado.subtract(interesPagado);
    }
}
