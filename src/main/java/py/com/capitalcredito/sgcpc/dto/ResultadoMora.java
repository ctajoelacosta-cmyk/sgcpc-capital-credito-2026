package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * Salida de ServicioMotorFinanciero.calcularMora (Doc. 00 §6.4). Trae el
 * tramo (desde el último corte hasta la fecha de cálculo) y el total a la
 * fecha (tramo + lo que ya estaba pendiente en la cuota).
 */
public class ResultadoMora {

    private final long dias;
    private final BigDecimal moratorioTramo;
    private final BigDecimal ivaMoratorioTramo;
    private final BigDecimal punitorioTramo;
    private final BigDecimal ivaPunitorioTramo;
    private final BigDecimal moratorioTotal;
    private final BigDecimal ivaMoratorioTotal;
    private final BigDecimal punitorioTotal;
    private final BigDecimal ivaPunitorioTotal;

    public ResultadoMora(long dias, BigDecimal moratorioTramo, BigDecimal ivaMoratorioTramo,
            BigDecimal punitorioTramo, BigDecimal ivaPunitorioTramo, BigDecimal moratorioTotal,
            BigDecimal ivaMoratorioTotal, BigDecimal punitorioTotal, BigDecimal ivaPunitorioTotal) {
        this.dias = dias;
        this.moratorioTramo = moratorioTramo;
        this.ivaMoratorioTramo = ivaMoratorioTramo;
        this.punitorioTramo = punitorioTramo;
        this.ivaPunitorioTramo = ivaPunitorioTramo;
        this.moratorioTotal = moratorioTotal;
        this.ivaMoratorioTotal = ivaMoratorioTotal;
        this.punitorioTotal = punitorioTotal;
        this.ivaPunitorioTotal = ivaPunitorioTotal;
    }

    public long getDias() {
        return dias;
    }

    public BigDecimal getMoratorioTramo() {
        return moratorioTramo;
    }

    public BigDecimal getIvaMoratorioTramo() {
        return ivaMoratorioTramo;
    }

    public BigDecimal getPunitorioTramo() {
        return punitorioTramo;
    }

    public BigDecimal getIvaPunitorioTramo() {
        return ivaPunitorioTramo;
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
}
