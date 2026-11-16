package py.com.capitalcredito.sgcpc.modelos;

/**
 * Normalización de personas, grupo Referencias (15/10/2026): reemplaza a las columnas
 * referencia_nombre/referencia_telefono/referencia2_nombre/referencia2_telefono de personas (ver
 * docs/base_de_datos/02_analisis_y_problemas.md §1, 1FN). numeroReferencia es 1 o 2 hoy (la
 * aplicación sigue exigiendo exactamente esas dos, ServicioClientes.validarReferencias), pero el
 * esquema ya no tiene un límite fijo de columnas.
 */
public class PersonaReferencia {

    private Long idReferencia;
    private Long idPersona;
    private int numeroReferencia;
    private String nombre;
    private String telefono;

    public Long getIdReferencia() {
        return idReferencia;
    }

    public void setIdReferencia(Long idReferencia) {
        this.idReferencia = idReferencia;
    }

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public int getNumeroReferencia() {
        return numeroReferencia;
    }

    public void setNumeroReferencia(int numeroReferencia) {
        this.numeroReferencia = numeroReferencia;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
}
