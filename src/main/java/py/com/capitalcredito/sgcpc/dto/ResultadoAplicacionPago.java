package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import java.util.List;

/** Salida de ServicioMotorFinanciero.distribuirPago (Doc. 00 §6.5 / RN-10). */
public class ResultadoAplicacionPago {

    private final List<DetalleAplicacionPago> detalles;
    private final BigDecimal montoSobrante;

    public ResultadoAplicacionPago(List<DetalleAplicacionPago> detalles, BigDecimal montoSobrante) {
        this.detalles = detalles;
        this.montoSobrante = montoSobrante;
    }

    public List<DetalleAplicacionPago> getDetalles() {
        return detalles;
    }

    public BigDecimal getMontoSobrante() {
        return montoSobrante;
    }
}
