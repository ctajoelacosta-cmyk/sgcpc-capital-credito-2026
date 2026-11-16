package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;

/**
 * Normalización de personas, grupo Laborales (16/10/2026): reemplaza a las columnas
 * situacion_laboral/empleador_nombre/empleador_ruc/empleador_direccion/empleador_telefono/cargo/
 * antiguedad_laboral_anios/ingreso_neto_comprobado/presenta_comprobante_ingreso/
 * otros_ingresos_mensuales/gastos_fijos_mensuales/cuota_otras_deudas_mensual de personas (ver
 * docs/base_de_datos/02_analisis_y_problemas.md). 1-1 con personas, SIEMPRE obligatoria (mismo
 * patrón que PersonaDomicilio).
 */
public class PersonaLaboral {

    private Long idPersona;
    private String situacionLaboral;
    private String empleadorNombre;
    private String empleadorRuc;
    private String empleadorDireccion;
    private String empleadorTelefono;
    private String cargo;
    private Integer antiguedadLaboralAnios;
    private BigDecimal ingresoNetoComprobado;
    private boolean presentaComprobanteIngreso;
    private BigDecimal otrosIngresosMensuales;
    private BigDecimal gastosFijosMensuales;
    private BigDecimal cuotaOtrasDeudasMensual;

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getSituacionLaboral() {
        return situacionLaboral;
    }

    public void setSituacionLaboral(String situacionLaboral) {
        this.situacionLaboral = situacionLaboral;
    }

    public String getEmpleadorNombre() {
        return empleadorNombre;
    }

    public void setEmpleadorNombre(String empleadorNombre) {
        this.empleadorNombre = empleadorNombre;
    }

    public String getEmpleadorRuc() {
        return empleadorRuc;
    }

    public void setEmpleadorRuc(String empleadorRuc) {
        this.empleadorRuc = empleadorRuc;
    }

    public String getEmpleadorDireccion() {
        return empleadorDireccion;
    }

    public void setEmpleadorDireccion(String empleadorDireccion) {
        this.empleadorDireccion = empleadorDireccion;
    }

    public String getEmpleadorTelefono() {
        return empleadorTelefono;
    }

    public void setEmpleadorTelefono(String empleadorTelefono) {
        this.empleadorTelefono = empleadorTelefono;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public Integer getAntiguedadLaboralAnios() {
        return antiguedadLaboralAnios;
    }

    public void setAntiguedadLaboralAnios(Integer antiguedadLaboralAnios) {
        this.antiguedadLaboralAnios = antiguedadLaboralAnios;
    }

    public BigDecimal getIngresoNetoComprobado() {
        return ingresoNetoComprobado;
    }

    public void setIngresoNetoComprobado(BigDecimal ingresoNetoComprobado) {
        this.ingresoNetoComprobado = ingresoNetoComprobado;
    }

    public boolean isPresentaComprobanteIngreso() {
        return presentaComprobanteIngreso;
    }

    public void setPresentaComprobanteIngreso(boolean presentaComprobanteIngreso) {
        this.presentaComprobanteIngreso = presentaComprobanteIngreso;
    }

    public BigDecimal getOtrosIngresosMensuales() {
        return otrosIngresosMensuales;
    }

    public void setOtrosIngresosMensuales(BigDecimal otrosIngresosMensuales) {
        this.otrosIngresosMensuales = otrosIngresosMensuales;
    }

    public BigDecimal getGastosFijosMensuales() {
        return gastosFijosMensuales;
    }

    public void setGastosFijosMensuales(BigDecimal gastosFijosMensuales) {
        this.gastosFijosMensuales = gastosFijosMensuales;
    }

    public BigDecimal getCuotaOtrasDeudasMensual() {
        return cuotaOtrasDeudasMensual;
    }

    public void setCuotaOtrasDeudasMensual(BigDecimal cuotaOtrasDeudasMensual) {
        this.cuotaOtrasDeudasMensual = cuotaOtrasDeudasMensual;
    }
}
