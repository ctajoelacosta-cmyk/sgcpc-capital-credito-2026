package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * Salida de ServicioMotorFinanciero.calcularTasasMora: las dos tasas de mora que se "congelan"
 * (snapshot) al formalizar un préstamo (RN-16, D-20), para que no cambien retroactivamente si
 * los parámetros del sistema cambian después.
 */
public class ResultadoTasasMora {

    private final BigDecimal tasaMoratoria;
    private final BigDecimal tasaPunitoria;

    public ResultadoTasasMora(BigDecimal tasaMoratoria, BigDecimal tasaPunitoria) {
        this.tasaMoratoria = tasaMoratoria;
        this.tasaPunitoria = tasaPunitoria;
    }

    public BigDecimal getTasaMoratoria() {
        return tasaMoratoria;
    }

    public BigDecimal getTasaPunitoria() {
        return tasaPunitoria;
    }
}
