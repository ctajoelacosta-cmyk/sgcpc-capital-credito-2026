package py.com.capitalcredito.sgcpc.modelos;

/**
 * Normalización de personas, grupo Domicilio (16/10/2026): reemplaza a las columnas
 * domicilio/direccion_domicilio/barrio/id_ciudad/id_departamento/referencia_domicilio/
 * tipo_vivienda/antiguedad_domicilio_anios de personas (ver
 * docs/base_de_datos/02_analisis_y_problemas.md). 1-1 con personas, SIEMPRE obligatoria (a
 * diferencia de PersonaConyuge/PersonaBaja, nunca opcional ni historial).
 *
 * Sin idDepartamento ni el texto compuesto "domicilio" -- el departamento se obtiene siempre por
 * JOIN desde idCiudad (ver ServicioClientes), y el texto legal se arma al vuelo con
 * ServicioClientes.construirTextoDomicilio/obtenerTextoDomicilio, nunca se persiste.
 */
public class PersonaDomicilio {

    private Long idPersona;
    private Long idCiudad;
    private String barrio;
    private String direccionDomicilio;
    private String referenciaDomicilio;
    private String tipoVivienda;
    private Integer antiguedadDomicilioAnios;

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public Long getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(Long idCiudad) {
        this.idCiudad = idCiudad;
    }

    public String getBarrio() {
        return barrio;
    }

    public void setBarrio(String barrio) {
        this.barrio = barrio;
    }

    public String getDireccionDomicilio() {
        return direccionDomicilio;
    }

    public void setDireccionDomicilio(String direccionDomicilio) {
        this.direccionDomicilio = direccionDomicilio;
    }

    public String getReferenciaDomicilio() {
        return referenciaDomicilio;
    }

    public void setReferenciaDomicilio(String referenciaDomicilio) {
        this.referenciaDomicilio = referenciaDomicilio;
    }

    public String getTipoVivienda() {
        return tipoVivienda;
    }

    public void setTipoVivienda(String tipoVivienda) {
        this.tipoVivienda = tipoVivienda;
    }

    public Integer getAntiguedadDomicilioAnios() {
        return antiguedadDomicilioAnios;
    }

    public void setAntiguedadDomicilioAnios(Integer antiguedadDomicilioAnios) {
        this.antiguedadDomicilioAnios = antiguedadDomicilioAnios;
    }
}
