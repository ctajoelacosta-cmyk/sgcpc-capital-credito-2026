package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** RF01 (pedido de Joel): una fila de la tabla "Comparar plazos" del cotizador -- misma cuota
 * (1.ª, con IVA) y total a pagar (con IVA) que devuelve ServicioCotizacion.cotizar para ese
 * plazo, mismo monto y fecha que la cotización actual. Pura (ningún cálculo acá). */
public class FilaComparacionPlazo {

    private final int plazoMeses;
    private final BigDecimal cuotaConIva;
    private final BigDecimal totalConIva;
    private final boolean actual;

    public FilaComparacionPlazo(int plazoMeses, BigDecimal cuotaConIva, BigDecimal totalConIva, boolean actual) {
        this.plazoMeses = plazoMeses;
        this.cuotaConIva = cuotaConIva;
        this.totalConIva = totalConIva;
        this.actual = actual;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public BigDecimal getCuotaConIva() {
        return cuotaConIva;
    }

    public BigDecimal getTotalConIva() {
        return totalConIva;
    }

    public boolean isActual() {
        return actual;
    }
}
