package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * D-141 (SEDECO, pedido de Joel): resultado de aplicar el redondeo del efectivo a favor del
 * cliente en un cobro que salda algo por completo (cobro normal, cancelación total, adelantar
 * capital). Si el cliente no llega a entregar ni el monto ya redondeado, no corresponde
 * redondeo -- aplicaRedondeo queda en false y montoACobrar/montoAplicado son el monto exacto de
 * siempre (sin tocar nada del flujo ya establecido para un pago parcial/con promesa).
 */
public class ResultadoRedondeoEfectivo {

    private final boolean aplicaRedondeo;
    private final BigDecimal montoACobrar;
    private final BigDecimal montoAplicado;
    private final BigDecimal montoVuelto;
    private final BigDecimal montoRedondeo;

    public ResultadoRedondeoEfectivo(boolean aplicaRedondeo, BigDecimal montoACobrar, BigDecimal montoAplicado,
            BigDecimal montoVuelto, BigDecimal montoRedondeo) {
        this.aplicaRedondeo = aplicaRedondeo;
        this.montoACobrar = montoACobrar;
        this.montoAplicado = montoAplicado;
        this.montoVuelto = montoVuelto;
        this.montoRedondeo = montoRedondeo;
    }

    public boolean isAplicaRedondeo() {
        return aplicaRedondeo;
    }

    /** Lo que efectivamente hay que pedirle al cliente (redondeado hacia abajo si corresponde). */
    public BigDecimal getMontoACobrar() {
        return montoACobrar;
    }

    /** Lo que se aplica a la deuda -- el monto exacto, aunque se haya cobrado menos (redondeo). */
    public BigDecimal getMontoAplicado() {
        return montoAplicado;
    }

    public BigDecimal getMontoVuelto() {
        return montoVuelto;
    }

    /** El descuento a favor del cliente (0 si no corresponde redondeo). */
    public BigDecimal getMontoRedondeo() {
        return montoRedondeo;
    }
}
