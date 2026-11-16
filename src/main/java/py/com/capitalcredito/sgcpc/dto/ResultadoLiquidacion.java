package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import java.util.List;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoLiquidacion;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/**
 * Salida de ServicioMotorFinanciero.calcularCancelacionTotal (§6.6) y
 * calcularAnticipoParcial (§6.7): misma forma que liquidaciones_anticipadas,
 * con tipoLiquidacion TOTAL o PARCIAL.
 *
 * Para TOTAL: cuotasResultantes trae el estado final (PAGADA) de cada
 * cuota que la cancelación salda, para que E7 solo persista.
 * Para PARCIAL: el plan anterior queda histórico tal cual (nunca se muta);
 * nuevoPlan trae las cuotas a insertar como versión nueva del plan.
 */
public class ResultadoLiquidacion {

    private final TipoLiquidacion tipoLiquidacion;
    private final BigDecimal saldoCapitalFecha;
    private final BigDecimal interesDevengado;
    private final BigDecimal ivaInteresDevengado;
    private final BigDecimal moratorioPendiente;
    private final BigDecimal ivaMoratorioPendiente;
    private final BigDecimal punitorioPendiente;
    private final BigDecimal ivaPunitorioPendiente;
    private final BigDecimal montoTotalLiquidacion;
    private final BigDecimal montoAnticipoCapital;
    private final BigDecimal nuevoSaldoCapital;
    private final List<Cuota> cuotasResultantes;
    private final List<Cuota> nuevoPlan;
    private final BigDecimal interesFuturoNoCobrado;

    public ResultadoLiquidacion(TipoLiquidacion tipoLiquidacion, BigDecimal saldoCapitalFecha,
            BigDecimal interesDevengado, BigDecimal ivaInteresDevengado, BigDecimal moratorioPendiente,
            BigDecimal ivaMoratorioPendiente, BigDecimal punitorioPendiente, BigDecimal ivaPunitorioPendiente,
            BigDecimal montoTotalLiquidacion, BigDecimal montoAnticipoCapital, BigDecimal nuevoSaldoCapital,
            List<Cuota> cuotasResultantes, List<Cuota> nuevoPlan, BigDecimal interesFuturoNoCobrado) {
        this.tipoLiquidacion = tipoLiquidacion;
        this.saldoCapitalFecha = saldoCapitalFecha;
        this.interesDevengado = interesDevengado;
        this.ivaInteresDevengado = ivaInteresDevengado;
        this.moratorioPendiente = moratorioPendiente;
        this.ivaMoratorioPendiente = ivaMoratorioPendiente;
        this.punitorioPendiente = punitorioPendiente;
        this.ivaPunitorioPendiente = ivaPunitorioPendiente;
        this.montoTotalLiquidacion = montoTotalLiquidacion;
        this.montoAnticipoCapital = montoAnticipoCapital;
        this.nuevoSaldoCapital = nuevoSaldoCapital;
        this.cuotasResultantes = cuotasResultantes;
        this.nuevoPlan = nuevoPlan;
        this.interesFuturoNoCobrado = interesFuturoNoCobrado;
    }

    public TipoLiquidacion getTipoLiquidacion() {
        return tipoLiquidacion;
    }

    public BigDecimal getSaldoCapitalFecha() {
        return saldoCapitalFecha;
    }

    public BigDecimal getInteresDevengado() {
        return interesDevengado;
    }

    public BigDecimal getIvaInteresDevengado() {
        return ivaInteresDevengado;
    }

    public BigDecimal getMoratorioPendiente() {
        return moratorioPendiente;
    }

    public BigDecimal getIvaMoratorioPendiente() {
        return ivaMoratorioPendiente;
    }

    public BigDecimal getPunitorioPendiente() {
        return punitorioPendiente;
    }

    public BigDecimal getIvaPunitorioPendiente() {
        return ivaPunitorioPendiente;
    }

    public BigDecimal getMontoTotalLiquidacion() {
        return montoTotalLiquidacion;
    }

    public BigDecimal getMontoAnticipoCapital() {
        return montoAnticipoCapital;
    }

    public BigDecimal getNuevoSaldoCapital() {
        return nuevoSaldoCapital;
    }

    public List<Cuota> getCuotasResultantes() {
        return cuotasResultantes;
    }

    public List<Cuota> getNuevoPlan() {
        return nuevoPlan;
    }

    /**
     * B204 (15/11/2026, ítem 5 del informe de UX de Cancelación): el interés que el cliente
     * <b>no va a pagar</b> por cancelar hoy — el interés de las cuotas que faltan, con su IVA,
     * menos el que sí se le cobra en esta liquidación. Es lo que la Ley 6624/2020 prohíbe cobrar
     * hacia adelante, puesto en un número.
     *
     * <p><b>Solo tiene sentido en una liquidación TOTAL: en una PARCIAL devuelve null</b>, a
     * propósito. El ahorro de un adelanto es otra cuenta y tiene otro dueño
     * ({@code ControladorCobranzaServlet.ahorroDeInteresesPorCuotas}, que compara contra el plan
     * que queda); devolver cero acá habría hecho que una pantalla pudiera decir "se ahorra 0"
     * donde la cuenta correcta es otra.</p>
     *
     * <p>Nunca negativo: lo que se cobra es un subconjunto de lo que estaba programado (el interés
     * del período en curso se prorratea por días sobre el interés de esa misma cuota), y de todos
     * modos el motor lo acota en cero.</p>
     *
     * <p><b>No confundir con el "Interés futuro programado" del reporte de Cartera</b>
     * ({@code CuotaDAO.sumarInteresFuturoPendiente}, Bloque 4): ese es
     * {@code SUM(interes_programado)} de las cuotas no pagadas —sin restar lo ya pagado de una
     * cuota PARCIAL, sin IVA y <b>sin</b> descontar el interés que hoy sí es exigible—, porque
     * responde otra pregunta: cuánto interés tiene la cartera por delante. Este campo responde la
     * del cliente parado frente al mostrador: cuánto interés <b>no</b> va a pagar si cancela hoy. Son
     * dos números distintos a propósito, cada uno con su dueño.</p>
     */
    public BigDecimal getInteresFuturoNoCobrado() {
        return interesFuturoNoCobrado;
    }
}
