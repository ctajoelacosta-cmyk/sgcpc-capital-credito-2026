package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDateTime;

/** Cabecera de una versión del plan de amortización (Doc. 08 Tabla 13, P06, E5). Espejo plano de planes_pago. */
public class PlanPago {

    private Long idPlanPago;
    private long idPrestamo;
    private int versionPlan;
    private String origenPlan;
    private boolean vigente;
    private Long idLiquidacionOrigen;
    private LocalDateTime fechaGeneracion;
    private long generadoPor;

    public Long getIdPlanPago() {
        return idPlanPago;
    }

    public void setIdPlanPago(Long idPlanPago) {
        this.idPlanPago = idPlanPago;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public int getVersionPlan() {
        return versionPlan;
    }

    public void setVersionPlan(int versionPlan) {
        this.versionPlan = versionPlan;
    }

    public String getOrigenPlan() {
        return origenPlan;
    }

    public void setOrigenPlan(String origenPlan) {
        this.origenPlan = origenPlan;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public Long getIdLiquidacionOrigen() {
        return idLiquidacionOrigen;
    }

    public void setIdLiquidacionOrigen(Long idLiquidacionOrigen) {
        this.idLiquidacionOrigen = idLiquidacionOrigen;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }

    public long getGeneradoPor() {
        return generadoPor;
    }

    public void setGeneradoPor(long generadoPor) {
        this.generadoPor = generadoPor;
    }
}
