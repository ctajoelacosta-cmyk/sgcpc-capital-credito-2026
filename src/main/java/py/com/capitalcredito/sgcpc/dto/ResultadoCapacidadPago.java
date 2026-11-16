package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** Salida de ServicioMotorFinanciero.calcularCapacidadPago (Doc. 00 §6.3). */
public class ResultadoCapacidadPago {

    /** Fracción (0.058141 = 5,8141 %), no porcentaje entero — D-12. */
    private final BigDecimal porcentajeAfectacion;

    public ResultadoCapacidadPago(BigDecimal porcentajeAfectacion) {
        this.porcentajeAfectacion = porcentajeAfectacion;
    }

    public BigDecimal getPorcentajeAfectacion() {
        return porcentajeAfectacion;
    }
}
