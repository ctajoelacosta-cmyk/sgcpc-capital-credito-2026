package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDate;

/** Espejo de la tabla feriados (bd/01_esquema.sql / Doc. 00 §10.1). */
public class Feriado {

    private Long idFeriado;
    private LocalDate fecha;
    private String descripcion;
    private boolean activo = true;

    public Long getIdFeriado() {
        return idFeriado;
    }

    public void setIdFeriado(Long idFeriado) {
        this.idFeriado = idFeriado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
