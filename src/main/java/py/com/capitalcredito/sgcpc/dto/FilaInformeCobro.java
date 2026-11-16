package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** B4 (Bloque 7/E10, pedido de Joel): una fila del informe de Cobros (/reportes/cobros) -- la
 * MISMA fila alimenta la tabla en pantalla, el informe PDF y el CSV (mismo criterio que
 * FilaInformeSolicitud/B2). capital/interes/moratorio/punitorio/iva son la composición de ESE
 * pago (suma de sus aplicaciones_pago, ServicioReportes.sumarAplicaciones); total es
 * pago.montoAplicado. revertido = true para un REVERTIDO: la fila se sigue mostrando (nunca
 * desaparece, RN-13), pero calcularTotalesCobros la excluye de los totales. */
public class FilaInformeCobro {

    private final long idPrestamo;
    private final String fecha;
    private final String recibo;
    private final String clienteNombre;
    private final String clienteCedula;
    private final String operacion;
    private final String tipo;
    private final BigDecimal capital;
    private final String capitalFormateado;
    private final BigDecimal interes;
    private final String interesFormateado;
    private final BigDecimal moratorio;
    private final String moratorioFormateado;
    private final BigDecimal punitorio;
    private final String punitorioFormateado;
    private final BigDecimal iva;
    private final String ivaFormateado;
    private final BigDecimal total;
    private final String totalFormateado;
    private final String cajeraNombre;
    private final boolean revertido;

    public FilaInformeCobro(long idPrestamo, String fecha, String recibo, String clienteNombre, String clienteCedula,
            String operacion, String tipo, BigDecimal capital, String capitalFormateado, BigDecimal interes,
            String interesFormateado, BigDecimal moratorio, String moratorioFormateado, BigDecimal punitorio,
            String punitorioFormateado, BigDecimal iva, String ivaFormateado, BigDecimal total,
            String totalFormateado, String cajeraNombre, boolean revertido) {
        this.idPrestamo = idPrestamo;
        this.fecha = fecha;
        this.recibo = recibo;
        this.clienteNombre = clienteNombre;
        this.clienteCedula = clienteCedula;
        this.operacion = operacion;
        this.tipo = tipo;
        this.capital = capital;
        this.capitalFormateado = capitalFormateado;
        this.interes = interes;
        this.interesFormateado = interesFormateado;
        this.moratorio = moratorio;
        this.moratorioFormateado = moratorioFormateado;
        this.punitorio = punitorio;
        this.punitorioFormateado = punitorioFormateado;
        this.iva = iva;
        this.ivaFormateado = ivaFormateado;
        this.total = total;
        this.totalFormateado = totalFormateado;
        this.cajeraNombre = cajeraNombre;
        this.revertido = revertido;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getFecha() {
        return fecha;
    }

    public String getRecibo() {
        return recibo;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteCedula() {
        return clienteCedula;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getTipo() {
        return tipo;
    }

    public BigDecimal getCapital() {
        return capital;
    }

    public String getCapitalFormateado() {
        return capitalFormateado;
    }

    public BigDecimal getInteres() {
        return interes;
    }

    public String getInteresFormateado() {
        return interesFormateado;
    }

    public BigDecimal getMoratorio() {
        return moratorio;
    }

    public String getMoratorioFormateado() {
        return moratorioFormateado;
    }

    public BigDecimal getPunitorio() {
        return punitorio;
    }

    public String getPunitorioFormateado() {
        return punitorioFormateado;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public String getIvaFormateado() {
        return ivaFormateado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getTotalFormateado() {
        return totalFormateado;
    }

    public String getCajeraNombre() {
        return cajeraNombre;
    }

    public boolean isRevertido() {
        return revertido;
    }
}
