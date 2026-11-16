package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** B4 (Bloque 7/E10, pedido de Joel): una fila del informe de Desembolsos (/reportes/desembolsos)
 * -- la MISMA fila alimenta la tabla en pantalla, el informe PDF y el CSV. */
public class FilaInformeDesembolso {

    private final long idPrestamo;
    private final String fecha;
    private final String comprobante;
    private final String clienteNombre;
    private final String clienteCedula;
    private final String operacion;
    private final BigDecimal monto;
    private final String montoFormateado;
    private final String desembolsadoPorNombre;

    public FilaInformeDesembolso(long idPrestamo, String fecha, String comprobante, String clienteNombre,
            String clienteCedula, String operacion, BigDecimal monto, String montoFormateado,
            String desembolsadoPorNombre) {
        this.idPrestamo = idPrestamo;
        this.fecha = fecha;
        this.comprobante = comprobante;
        this.clienteNombre = clienteNombre;
        this.clienteCedula = clienteCedula;
        this.operacion = operacion;
        this.monto = monto;
        this.montoFormateado = montoFormateado;
        this.desembolsadoPorNombre = desembolsadoPorNombre;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getFecha() {
        return fecha;
    }

    public String getComprobante() {
        return comprobante;
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

    public BigDecimal getMonto() {
        return monto;
    }

    public String getMontoFormateado() {
        return montoFormateado;
    }

    public String getDesembolsadoPorNombre() {
        return desembolsadoPorNombre;
    }
}
