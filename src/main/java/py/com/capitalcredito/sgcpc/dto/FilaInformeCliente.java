package py.com.capitalcredito.sgcpc.dto;

/** Una fila del Informe de Clientes en PDF (D-135) -- ya formateada, el .jrxml no calcula nada. */
public class FilaInformeCliente {

    private final String cedula;
    private final String nombreCompleto;
    private final String telefono;
    private final String ciudad;
    private final String situacionLaboral;
    private final String fechaAlta;
    private final int prestamosActivos;
    private final String estado;

    public FilaInformeCliente(String cedula, String nombreCompleto, String telefono, String ciudad,
            String situacionLaboral, String fechaAlta, int prestamosActivos, String estado) {
        this.cedula = cedula;
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.ciudad = ciudad;
        this.situacionLaboral = situacionLaboral;
        this.fechaAlta = fechaAlta;
        this.prestamosActivos = prestamosActivos;
        this.estado = estado;
    }

    public String getCedula() {
        return cedula;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getSituacionLaboral() {
        return situacionLaboral;
    }

    public String getFechaAlta() {
        return fechaAlta;
    }

    public int getPrestamosActivos() {
        return prestamosActivos;
    }

    public String getEstado() {
        return estado;
    }
}
