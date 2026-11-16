package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/**
 * Una fila de ResultadoAplicacionPago: lo que un pago aplicó a una cuota
 * (misma forma que después va a aplicaciones_pago) y el estado resultante
 * completo de esa cuota, para que quien persista (E7) no tenga que
 * recalcular nada.
 */
public class DetalleAplicacionPago {

    private final TipoAplicacion tipoAplicacion;
    private final int numeroCuota;
    private final BigDecimal montoMoratorio;
    private final BigDecimal ivaMoratorio;
    private final BigDecimal montoPunitorio;
    private final BigDecimal ivaPunitorio;
    private final BigDecimal montoInteres;
    private final BigDecimal ivaInteres;
    private final BigDecimal montoCapital;
    private final BigDecimal subtotalAplicado;
    private final Cuota cuotaResultante;

    public DetalleAplicacionPago(TipoAplicacion tipoAplicacion, int numeroCuota, BigDecimal montoMoratorio,
            BigDecimal ivaMoratorio, BigDecimal montoPunitorio, BigDecimal ivaPunitorio, BigDecimal montoInteres,
            BigDecimal ivaInteres, BigDecimal montoCapital, BigDecimal subtotalAplicado, Cuota cuotaResultante) {
        this.tipoAplicacion = tipoAplicacion;
        this.numeroCuota = numeroCuota;
        this.montoMoratorio = montoMoratorio;
        this.ivaMoratorio = ivaMoratorio;
        this.montoPunitorio = montoPunitorio;
        this.ivaPunitorio = ivaPunitorio;
        this.montoInteres = montoInteres;
        this.ivaInteres = ivaInteres;
        this.montoCapital = montoCapital;
        this.subtotalAplicado = subtotalAplicado;
        this.cuotaResultante = cuotaResultante;
    }

    public TipoAplicacion getTipoAplicacion() {
        return tipoAplicacion;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public BigDecimal getMontoMoratorio() {
        return montoMoratorio;
    }

    public BigDecimal getIvaMoratorio() {
        return ivaMoratorio;
    }

    public BigDecimal getMontoPunitorio() {
        return montoPunitorio;
    }

    public BigDecimal getIvaPunitorio() {
        return ivaPunitorio;
    }

    public BigDecimal getMontoInteres() {
        return montoInteres;
    }

    public BigDecimal getIvaInteres() {
        return ivaInteres;
    }

    public BigDecimal getMontoCapital() {
        return montoCapital;
    }

    public BigDecimal getSubtotalAplicado() {
        return subtotalAplicado;
    }

    public Cuota getCuotaResultante() {
        return cuotaResultante;
    }

    /** Moratorio + su IVA, agrupados (así los reporta el Anexo C). */
    public BigDecimal getMoratorioConIva() {
        return montoMoratorio.add(ivaMoratorio);
    }

    /** Punitorio + su IVA, agrupados. */
    public BigDecimal getPunitorioConIva() {
        return montoPunitorio.add(ivaPunitorio);
    }

    /** Interés + su IVA, agrupados. */
    public BigDecimal getInteresConIva() {
        return montoInteres.add(ivaInteres);
    }
}
