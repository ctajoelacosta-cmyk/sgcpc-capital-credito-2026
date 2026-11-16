package py.com.capitalcredito.sgcpc.modelos;

/** Catálogo de ciudades/distritos de Paraguay (Bloque 7/E10, Tanda A, D-126), semilla en bd/03_parametros.sql. Solo lectura. */
public class Ciudad {

    private Long idCiudad;
    private Long idDepartamento;
    private String nombre;
    private boolean activo;

    public Long getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(Long idCiudad) {
        this.idCiudad = idCiudad;
    }

    public Long getIdDepartamento() {
        return idDepartamento;
    }

    public void setIdDepartamento(Long idDepartamento) {
        this.idDepartamento = idDepartamento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
