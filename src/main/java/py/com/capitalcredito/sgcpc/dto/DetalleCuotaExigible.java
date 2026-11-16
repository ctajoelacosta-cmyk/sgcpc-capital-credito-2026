package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** Una fila de ResultadoDeudaExigible: lo que se debe hoy de una cuota vencida. */
public class DetalleCuotaExigible {

    private final int numeroCuota;
    private final BigDecimal capitalPendiente;
    private final BigDecimal interesPendiente;
    private final BigDecimal ivaInteresPendiente;
    private final BigDecimal moratorioTotal;
    private final BigDecimal ivaMoratorioTotal;
    private final BigDecimal punitorioTotal;
    private final BigDecimal ivaPunitorioTotal;
    private final BigDecimal totalExigible;

    public DetalleCuotaExigible(int numeroCuota, BigDecimal capitalPendiente, BigDecimal interesPendiente,
            BigDecimal ivaInteresPendiente, BigDecimal moratorioTotal, BigDecimal ivaMoratorioTotal,
            BigDecimal punitorioTotal, BigDecimal ivaPunitorioTotal, BigDecimal totalExigible) {
        this.numeroCuota = numeroCuota;
        this.capitalPendiente = capitalPendiente;
        this.interesPendiente = interesPendiente;
        this.ivaInteresPendiente = ivaInteresPendiente;
        this.moratorioTotal = moratorioTotal;
        this.ivaMoratorioTotal = ivaMoratorioTotal;
        this.punitorioTotal = punitorioTotal;
        this.ivaPunitorioTotal = ivaPunitorioTotal;
        this.totalExigible = totalExigible;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public BigDecimal getCapitalPendiente() {
        return capitalPendiente;
    }

    public BigDecimal getInteresPendiente() {
        return interesPendiente;
    }

    public BigDecimal getIvaInteresPendiente() {
        return ivaInteresPendiente;
    }

    public BigDecimal getMoratorioTotal() {
        return moratorioTotal;
    }

    public BigDecimal getIvaMoratorioTotal() {
        return ivaMoratorioTotal;
    }

    public BigDecimal getPunitorioTotal() {
        return punitorioTotal;
    }

    public BigDecimal getIvaPunitorioTotal() {
        return ivaPunitorioTotal;
    }

    public BigDecimal getTotalExigible() {
        return totalExigible;
    }
}
