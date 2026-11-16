package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;

/** Espejo de la tabla parametros_sistema (bd/01_esquema.sql / Doc. 08 Tabla 02). */
public class ParametroSistema {

    private Long idParametro;
    private String claveParametro;
    private String nombreParametro;
    private String valorParametro;
    private TipoValorParametro tipoValor;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private Long modificadoPor;
    private LocalDateTime fechaModificacion;
    private String observacion;

    public Long getIdParametro() {
        return idParametro;
    }

    public void setIdParametro(Long idParametro) {
        this.idParametro = idParametro;
    }

    public String getClaveParametro() {
        return claveParametro;
    }

    public void setClaveParametro(String claveParametro) {
        this.claveParametro = claveParametro;
    }

    public String getNombreParametro() {
        return nombreParametro;
    }

    public void setNombreParametro(String nombreParametro) {
        this.nombreParametro = nombreParametro;
    }

    public String getValorParametro() {
        return valorParametro;
    }

    public void setValorParametro(String valorParametro) {
        this.valorParametro = valorParametro;
    }

    public TipoValorParametro getTipoValor() {
        return tipoValor;
    }

    public void setTipoValor(TipoValorParametro tipoValor) {
        this.tipoValor = tipoValor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(LocalDate vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public Long getModificadoPor() {
        return modificadoPor;
    }

    public void setModificadoPor(Long modificadoPor) {
        this.modificadoPor = modificadoPor;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(LocalDateTime fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
