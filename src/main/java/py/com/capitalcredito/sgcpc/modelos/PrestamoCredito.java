package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;

/**
 * Préstamo formalizado (Doc. 08 Tabla 11, P06, E5). Espejo plano de
 * prestamos: id_solicitud/formalizado_por/etc. son ids, no objetos (mismo
 * patrón que SolicitudCredito). No confundir con modelos/Prestamo.java, que
 * es el DTO de cálculo interno de ServicioMotorFinanciero (muy pocos
 * campos, sin persistencia) -- esta clase es la fila completa de la tabla.
 */
public class PrestamoCredito {

    private Long idPrestamo;
    private String identificadorOperacion;
    private Long idSolicitud;
    private String numeroContrato;
    private String numeroPagare;
    private BigDecimal montoAprobado;
    private int plazoMeses;
    private BigDecimal tasaTna;
    private BigDecimal tasaTea;
    private BigDecimal cft;
    private BigDecimal tasaMoratoria;
    private BigDecimal tasaPunitoria;
    private LocalDate primeraFechaVencimiento;
    private LocalDateTime fechaFormalizacion;
    private LocalDate fechaDesembolso;
    private EstadoPrestamo estadoPrestamo;
    private LocalDateTime fechaFinalizacionFinanciera;
    private String motivoAnulacionSinDesembolso;
    private LocalDateTime fechaAnulacionSinDesembolso;
    private Long anuladoPor;
    private String estadoCierreDocumental;
    private LocalDateTime fechaCierreDocumental;
    private Long cierreDocumentalPor;
    private long formalizadoPor;
    private Long idLimiteTasa;
    private BigDecimal tasaMensual;
    private BigDecimal cuotaContractual;
    private String numeroConstanciaDevolucion;
    private String numeroComprobanteDesembolso;

    public Long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(Long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public void setIdentificadorOperacion(String identificadorOperacion) {
        this.identificadorOperacion = identificadorOperacion;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(Long idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public String getNumeroContrato() {
        return numeroContrato;
    }

    public void setNumeroContrato(String numeroContrato) {
        this.numeroContrato = numeroContrato;
    }

    public String getNumeroPagare() {
        return numeroPagare;
    }

    public void setNumeroPagare(String numeroPagare) {
        this.numeroPagare = numeroPagare;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public void setMontoAprobado(BigDecimal montoAprobado) {
        this.montoAprobado = montoAprobado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public void setPlazoMeses(int plazoMeses) {
        this.plazoMeses = plazoMeses;
    }

    public BigDecimal getTasaTna() {
        return tasaTna;
    }

    public void setTasaTna(BigDecimal tasaTna) {
        this.tasaTna = tasaTna;
    }

    public BigDecimal getTasaTea() {
        return tasaTea;
    }

    public void setTasaTea(BigDecimal tasaTea) {
        this.tasaTea = tasaTea;
    }

    public BigDecimal getCft() {
        return cft;
    }

    public void setCft(BigDecimal cft) {
        this.cft = cft;
    }

    public BigDecimal getTasaMoratoria() {
        return tasaMoratoria;
    }

    public void setTasaMoratoria(BigDecimal tasaMoratoria) {
        this.tasaMoratoria = tasaMoratoria;
    }

    public BigDecimal getTasaPunitoria() {
        return tasaPunitoria;
    }

    public void setTasaPunitoria(BigDecimal tasaPunitoria) {
        this.tasaPunitoria = tasaPunitoria;
    }

    public LocalDate getPrimeraFechaVencimiento() {
        return primeraFechaVencimiento;
    }

    public void setPrimeraFechaVencimiento(LocalDate primeraFechaVencimiento) {
        this.primeraFechaVencimiento = primeraFechaVencimiento;
    }

    public LocalDateTime getFechaFormalizacion() {
        return fechaFormalizacion;
    }

    public void setFechaFormalizacion(LocalDateTime fechaFormalizacion) {
        this.fechaFormalizacion = fechaFormalizacion;
    }

    public LocalDate getFechaDesembolso() {
        return fechaDesembolso;
    }

    public void setFechaDesembolso(LocalDate fechaDesembolso) {
        this.fechaDesembolso = fechaDesembolso;
    }

    public EstadoPrestamo getEstadoPrestamo() {
        return estadoPrestamo;
    }

    public void setEstadoPrestamo(EstadoPrestamo estadoPrestamo) {
        this.estadoPrestamo = estadoPrestamo;
    }

    public LocalDateTime getFechaFinalizacionFinanciera() {
        return fechaFinalizacionFinanciera;
    }

    public void setFechaFinalizacionFinanciera(LocalDateTime fechaFinalizacionFinanciera) {
        this.fechaFinalizacionFinanciera = fechaFinalizacionFinanciera;
    }

    public String getMotivoAnulacionSinDesembolso() {
        return motivoAnulacionSinDesembolso;
    }

    public void setMotivoAnulacionSinDesembolso(String motivoAnulacionSinDesembolso) {
        this.motivoAnulacionSinDesembolso = motivoAnulacionSinDesembolso;
    }

    public LocalDateTime getFechaAnulacionSinDesembolso() {
        return fechaAnulacionSinDesembolso;
    }

    public void setFechaAnulacionSinDesembolso(LocalDateTime fechaAnulacionSinDesembolso) {
        this.fechaAnulacionSinDesembolso = fechaAnulacionSinDesembolso;
    }

    public Long getAnuladoPor() {
        return anuladoPor;
    }

    public void setAnuladoPor(Long anuladoPor) {
        this.anuladoPor = anuladoPor;
    }

    public String getEstadoCierreDocumental() {
        return estadoCierreDocumental;
    }

    public void setEstadoCierreDocumental(String estadoCierreDocumental) {
        this.estadoCierreDocumental = estadoCierreDocumental;
    }

    public LocalDateTime getFechaCierreDocumental() {
        return fechaCierreDocumental;
    }

    public void setFechaCierreDocumental(LocalDateTime fechaCierreDocumental) {
        this.fechaCierreDocumental = fechaCierreDocumental;
    }

    public Long getCierreDocumentalPor() {
        return cierreDocumentalPor;
    }

    public void setCierreDocumentalPor(Long cierreDocumentalPor) {
        this.cierreDocumentalPor = cierreDocumentalPor;
    }

    public long getFormalizadoPor() {
        return formalizadoPor;
    }

    public void setFormalizadoPor(long formalizadoPor) {
        this.formalizadoPor = formalizadoPor;
    }

    public Long getIdLimiteTasa() {
        return idLimiteTasa;
    }

    public void setIdLimiteTasa(Long idLimiteTasa) {
        this.idLimiteTasa = idLimiteTasa;
    }

    public BigDecimal getTasaMensual() {
        return tasaMensual;
    }

    public void setTasaMensual(BigDecimal tasaMensual) {
        this.tasaMensual = tasaMensual;
    }

    public BigDecimal getCuotaContractual() {
        return cuotaContractual;
    }

    public void setCuotaContractual(BigDecimal cuotaContractual) {
        this.cuotaContractual = cuotaContractual;
    }

    /** E9/Bloque 5 (P10, cierre documental): mismo patrón que numeroContrato/numeroPagare. */
    public String getNumeroConstanciaDevolucion() {
        return numeroConstanciaDevolucion;
    }

    public void setNumeroConstanciaDevolucion(String numeroConstanciaDevolucion) {
        this.numeroConstanciaDevolucion = numeroConstanciaDevolucion;
    }

    /** Bloque 7/E10 (D-130): asignado al imprimir el comprobante "para firma", antes de confirmar. */
    public String getNumeroComprobanteDesembolso() {
        return numeroComprobanteDesembolso;
    }

    public void setNumeroComprobanteDesembolso(String numeroComprobanteDesembolso) {
        this.numeroComprobanteDesembolso = numeroComprobanteDesembolso;
    }
}
