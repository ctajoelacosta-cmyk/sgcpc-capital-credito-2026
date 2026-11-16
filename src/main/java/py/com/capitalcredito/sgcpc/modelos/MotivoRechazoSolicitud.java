package py.com.capitalcredito.sgcpc.modelos;

/** Catálogo de motivos de rechazo de solicitud (Bloque 7/E10, Tanda A, D-126), semilla en bd/03_parametros.sql. Solo lectura. */
public class MotivoRechazoSolicitud {

    private Long idMotivoRechazo;
    private String codigo;
    private String nombre;
    private boolean requiereDetalle;
    private boolean activo;
    private int orden;

    public Long getIdMotivoRechazo() {
        return idMotivoRechazo;
    }

    public void setIdMotivoRechazo(Long idMotivoRechazo) {
        this.idMotivoRechazo = idMotivoRechazo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isRequiereDetalle() {
        return requiereDetalle;
    }

    public void setRequiereDetalle(boolean requiereDetalle) {
        this.requiereDetalle = requiereDetalle;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }
}
