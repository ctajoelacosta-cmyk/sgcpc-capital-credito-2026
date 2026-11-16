package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/** Salida de ServicioMotorFinanciero.cotizar (Doc. 00 §6.8). */
public class ResultadoCotizacion {

    private final BigDecimal cuotaSinIva;
    private final BigDecimal montoTotalConIva;
    private final BigDecimal tea;
    private final BigDecimal tna;
    private final BigDecimal cft;
    private final List<Cuota> plan;

    public ResultadoCotizacion(BigDecimal cuotaSinIva, BigDecimal montoTotalConIva, BigDecimal tea,
            BigDecimal tna, BigDecimal cft, List<Cuota> plan) {
        this.cuotaSinIva = cuotaSinIva;
        this.montoTotalConIva = montoTotalConIva;
        this.tea = tea;
        this.tna = tna;
        this.cft = cft;
        this.plan = plan;
    }

    public BigDecimal getCuotaSinIva() {
        return cuotaSinIva;
    }

    public BigDecimal getMontoTotalConIva() {
        return montoTotalConIva;
    }

    public BigDecimal getTea() {
        return tea;
    }

    public BigDecimal getTna() {
        return tna;
    }

    public BigDecimal getCft() {
        return cft;
    }

    public List<Cuota> getPlan() {
        return plan;
    }
}
