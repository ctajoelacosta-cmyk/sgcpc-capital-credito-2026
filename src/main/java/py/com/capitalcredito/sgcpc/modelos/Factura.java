package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoFactura;

/**
 * Espejo de la tabla facturas (bd/01_esquema.sql, RN-25, P09, E7). Decisión de Joel (D-78,
 * AVANCE.md): solo lleva conceptos gravados (interés, moratorio, punitorio) -- el capital no es
 * venta de servicio y se documenta en el recibo, no acá; por eso totalExenta en la práctica queda
 * siempre en cero para las facturas de cobranza (la columna existe por si alguna vez hiciera
 * falta, pero E7 no la usa).
 */
public class Factura {

    private Long idFactura;
    private long idPago;
    private String timbrado;
    private String establecimiento;
    private String puntoExpedicion;
    private String numero;
    private LocalDate fecha;
    private String clienteNombre;
    private String clienteDocumento;
    private BigDecimal totalExenta = BigDecimal.ZERO;
    private BigDecimal totalGravada10;
    private BigDecimal iva10;
    private BigDecimal total;
    private EstadoFactura estado;
    private Long idArchivo;
    private Long idReversionPago;
    private String numeroNotaAnulacion;
    private Long idArchivoNotaAnulacion;

    public Long getIdFactura() {
        return idFactura;
    }

    public void setIdFactura(Long idFactura) {
        this.idFactura = idFactura;
    }

    public long getIdPago() {
        return idPago;
    }

    public void setIdPago(long idPago) {
        this.idPago = idPago;
    }

    public String getTimbrado() {
        return timbrado;
    }

    public void setTimbrado(String timbrado) {
        this.timbrado = timbrado;
    }

    public String getEstablecimiento() {
        return establecimiento;
    }

    public void setEstablecimiento(String establecimiento) {
        this.establecimiento = establecimiento;
    }

    public String getPuntoExpedicion() {
        return puntoExpedicion;
    }

    public void setPuntoExpedicion(String puntoExpedicion) {
        this.puntoExpedicion = puntoExpedicion;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteDocumento() {
        return clienteDocumento;
    }

    public void setClienteDocumento(String clienteDocumento) {
        this.clienteDocumento = clienteDocumento;
    }

    public BigDecimal getTotalExenta() {
        return totalExenta;
    }

    public void setTotalExenta(BigDecimal totalExenta) {
        this.totalExenta = totalExenta;
    }

    public BigDecimal getTotalGravada10() {
        return totalGravada10;
    }

    public void setTotalGravada10(BigDecimal totalGravada10) {
        this.totalGravada10 = totalGravada10;
    }

    public BigDecimal getIva10() {
        return iva10;
    }

    public void setIva10(BigDecimal iva10) {
        this.iva10 = iva10;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public EstadoFactura getEstado() {
        return estado;
    }

    public void setEstado(EstadoFactura estado) {
        this.estado = estado;
    }

    public Long getIdArchivo() {
        return idArchivo;
    }

    public void setIdArchivo(Long idArchivo) {
        this.idArchivo = idArchivo;
    }

    public Long getIdReversionPago() {
        return idReversionPago;
    }

    public void setIdReversionPago(Long idReversionPago) {
        this.idReversionPago = idReversionPago;
    }

    public String getNumeroNotaAnulacion() {
        return numeroNotaAnulacion;
    }

    public void setNumeroNotaAnulacion(String numeroNotaAnulacion) {
        this.numeroNotaAnulacion = numeroNotaAnulacion;
    }

    public Long getIdArchivoNotaAnulacion() {
        return idArchivoNotaAnulacion;
    }

    public void setIdArchivoNotaAnulacion(Long idArchivoNotaAnulacion) {
        this.idArchivoNotaAnulacion = idArchivoNotaAnulacion;
    }

    /** PA-12: número tal como se muestra al usuario (establecimiento-puntoExpedicion-numero). */
    public String getNumeroCompleto() {
        return establecimiento + "-" + puntoExpedicion + "-" + numero;
    }
}
