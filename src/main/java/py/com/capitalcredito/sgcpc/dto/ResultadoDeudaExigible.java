package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import java.util.List;

/** Salida de ServicioMotorFinanciero.calcularDeudaExigible (Doc. 00 §6.8). */
public class ResultadoDeudaExigible {

    private final List<DetalleCuotaExigible> detalles;
    private final BigDecimal totalGeneral;

    public ResultadoDeudaExigible(List<DetalleCuotaExigible> detalles, BigDecimal totalGeneral) {
        this.detalles = detalles;
        this.totalGeneral = totalGeneral;
    }

    public List<DetalleCuotaExigible> getDetalles() {
        return detalles;
    }

    public BigDecimal getTotalGeneral() {
        return totalGeneral;
    }
}
