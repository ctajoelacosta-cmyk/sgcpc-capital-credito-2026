package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;

/** Espejo de la tabla pagos (bd/01_esquema.sql, Doc. 00 §10.1/§10.2, P09, E7). */
public class Pago {

    private Long idPago;
    private String identificadorConfirmacion;
    private long idPrestamo;
    private long idSesionCaja;
    private Long idLiquidacionAnticipada;
    private String numeroRecibo;
    private LocalDateTime fechaHoraPago;
    private BigDecimal montoRecibido;
    private BigDecimal montoVuelto;
    private BigDecimal montoAplicado;
    /** D-141 (SEDECO): descuento a favor del cliente por redondeo del efectivo; 0 si no corresponde. */
    private BigDecimal montoRedondeo = BigDecimal.ZERO;
    private EstadoConfirmacionPago estadoPago;
    private long registradoPor;
    private Long idArchivoRecibo;
    private TipoPago tipo;
    /**
     * D-89: solo en memoria, no mapeado por PagoDAO (no hay columna ni falta -- el estado real
     * vive en prestamos.estado_prestamo). Lo llena cada confirmar* con el estado del préstamo
     * DESPUÉS de la operación, para que ControladorCobranzaServlet decida a dónde redirigir tras
     * el PRG sin volver a leer la base (evita el 404 de cancelación total: no puede redirigir a
     * la ficha de un préstamo ya FINALIZADO).
     */
    private EstadoPrestamo estadoPrestamoResultante;
    /**
     * D-89 (PA-12): solo en memoria, no mapeado por PagoDAO. La factura ya vive en su propia
     * tabla (facturas.id_pago); esto es solo para que el servlet arme el mensaje de éxito con el
     * número sin una segunda consulta -- null si la operación no generó factura (D-78: sin
     * conceptos gravados, RN-25).
     */
    private Factura facturaEmitida;

    public Long getIdPago() {
        return idPago;
    }

    public void setIdPago(Long idPago) {
        this.idPago = idPago;
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

    public Long getIdLiquidacionAnticipada() {
        return idLiquidacionAnticipada;
    }

    public void setIdLiquidacionAnticipada(Long idLiquidacionAnticipada) {
        this.idLiquidacionAnticipada = idLiquidacionAnticipada;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public void setNumeroRecibo(String numeroRecibo) {
        this.numeroRecibo = numeroRecibo;
    }

    public LocalDateTime getFechaHoraPago() {
        return fechaHoraPago;
    }

    public void setFechaHoraPago(LocalDateTime fechaHoraPago) {
        this.fechaHoraPago = fechaHoraPago;
    }

    public BigDecimal getMontoRecibido() {
        return montoRecibido;
    }

    public void setMontoRecibido(BigDecimal montoRecibido) {
        this.montoRecibido = montoRecibido;
    }

    public BigDecimal getMontoVuelto() {
        return montoVuelto;
    }

    public void setMontoVuelto(BigDecimal montoVuelto) {
        this.montoVuelto = montoVuelto;
    }

    public BigDecimal getMontoRedondeo() {
        return montoRedondeo;
    }

    public void setMontoRedondeo(BigDecimal montoRedondeo) {
        this.montoRedondeo = montoRedondeo;
    }

    public BigDecimal getMontoAplicado() {
        return montoAplicado;
    }

    public void setMontoAplicado(BigDecimal montoAplicado) {
        this.montoAplicado = montoAplicado;
    }

    public EstadoConfirmacionPago getEstadoPago() {
        return estadoPago;
    }

    public void setEstadoPago(EstadoConfirmacionPago estadoPago) {
        this.estadoPago = estadoPago;
    }

    public long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public Long getIdArchivoRecibo() {
        return idArchivoRecibo;
    }

    public void setIdArchivoRecibo(Long idArchivoRecibo) {
        this.idArchivoRecibo = idArchivoRecibo;
    }

    public TipoPago getTipo() {
        return tipo;
    }

    public void setTipo(TipoPago tipo) {
        this.tipo = tipo;
    }

    public EstadoPrestamo getEstadoPrestamoResultante() {
        return estadoPrestamoResultante;
    }

    public void setEstadoPrestamoResultante(EstadoPrestamo estadoPrestamoResultante) {
        this.estadoPrestamoResultante = estadoPrestamoResultante;
    }

    public Factura getFacturaEmitida() {
        return facturaEmitida;
    }

    public void setFacturaEmitida(Factura facturaEmitida) {
        this.facturaEmitida = facturaEmitida;
    }
}
