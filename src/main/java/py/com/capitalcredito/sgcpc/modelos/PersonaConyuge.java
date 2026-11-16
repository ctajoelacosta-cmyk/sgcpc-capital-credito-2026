package py.com.capitalcredito.sgcpc.modelos;

/**
 * Normalización de personas, grupo Cónyuge (16/10/2026): reemplaza a las columnas
 * conyuge_nombre/conyuge_cedula/conyuge_telefono de personas (ver
 * docs/base_de_datos/02_analisis_y_problemas.md). 1-1 con personas, opcional -- solo existe
 * cuando el estado civil lo exige (ServicioClientes.validarConyuge).
 */
public class PersonaConyuge {

    private Long idPersona;
    private String nombre;
    private String cedula;
    private String telefono;

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
}
