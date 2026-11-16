package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Estado de un préstamo para el motor financiero: solo lo que
 * ServicioMotorFinanciero necesita para calcular, no todas las columnas de
 * la tabla prestamos.
 */
public class Prestamo {

    /** Fecha de desembolso; inicio del período de la cuota 1 (D-23). */
    private LocalDate fechaDesembolso;
    private BigDecimal tasaTna;
    private BigDecimal tasaTea;
    private BigDecimal tasaMensual;
    /** Tasa moratoria anual (fracción), ya resuelta al formalizar (D-20: TEA x factor_moratorio). */
    private BigDecimal tasaMoratoria;
    /**
     * Fracción del moratorio que se cobra como punitorio (ej. 0.30 = 30 % del
     * moratorio calculado), NO una tasa anual — no confundir con tasaTea ni
     * tasaMoratoria. Espejo de prestamos.tasa_punitoria (Doc. 08 Tabla 11).
     */
    private BigDecimal tasaPunitoria;
    private List<Cuota> cuotas = new ArrayList<>();
    /**
     * D-87: origen del plan vigente (espejo de planes_pago.origen_plan, ej. "ORIGINAL" /
     * "ANTICIPO_PARCIAL"), solo en memoria -- lo necesita inicioPeriodoTeorico para saber si la
     * cuota 1 de un plan regenerado por anticipo debe calcular su propio inicio de período en
     * vez de usar fechaDesembolso (que ya no aplica: ese plan no arranca en el desembolso
     * original del préstamo).
     */
    private String origenPlanVigente;
    /**
     * D-87: fecha de generación del plan vigente (espejo de planes_pago.fecha_generacion, sin
     * hora), solo en memoria -- es la fecha del anticipo que generó el plan cuando
     * origenPlanVigente = "ANTICIPO_PARCIAL".
     */
    private LocalDate fechaGeneracionPlanVigente;

    public LocalDate getFechaDesembolso() {
        return fechaDesembolso;
    }

    public void setFechaDesembolso(LocalDate fechaDesembolso) {
        this.fechaDesembolso = fechaDesembolso;
    }

    public BigDecimal getTasaTna() {
        return tasaTna;
    }

    public void setTasaTna(BigDecimal tasaTna) {
        this.tasaTna = tasaTna;
    }

    public BigDecimal getTasaTea() {
        return tasaTea;
    }

    public void setTasaTea(BigDecimal tasaTea) {
        this.tasaTea = tasaTea;
    }

    public BigDecimal getTasaMensual() {
        return tasaMensual;
    }

    public void setTasaMensual(BigDecimal tasaMensual) {
        this.tasaMensual = tasaMensual;
    }

    public BigDecimal getTasaMoratoria() {
        return tasaMoratoria;
    }

    public void setTasaMoratoria(BigDecimal tasaMoratoria) {
        this.tasaMoratoria = tasaMoratoria;
    }

    public BigDecimal getTasaPunitoria() {
        return tasaPunitoria;
    }

    public void setTasaPunitoria(BigDecimal tasaPunitoria) {
        this.tasaPunitoria = tasaPunitoria;
    }

    public List<Cuota> getCuotas() {
        return cuotas;
    }

    public void setCuotas(List<Cuota> cuotas) {
        this.cuotas = cuotas;
    }

    public String getOrigenPlanVigente() {
        return origenPlanVigente;
    }

    public void setOrigenPlanVigente(String origenPlanVigente) {
        this.origenPlanVigente = origenPlanVigente;
    }

    public LocalDate getFechaGeneracionPlanVigente() {
        return fechaGeneracionPlanVigente;
    }

    public void setFechaGeneracionPlanVigente(LocalDate fechaGeneracionPlanVigente) {
        this.fechaGeneracionPlanVigente = fechaGeneracionPlanVigente;
    }
}
