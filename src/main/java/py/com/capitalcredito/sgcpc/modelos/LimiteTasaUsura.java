package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Espejo de la tabla limites_tasa_usura (bd/01_esquema.sql / Doc. 08 Tabla 03). */
public class LimiteTasaUsura {

    private Long idLimiteTasa;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    /** Fraccion (0.273900 = 27,39 %), D-12. */
    private BigDecimal tasaMaximaTea;
    private String fuenteReferencia;
    private Long registradoPor;
    private LocalDateTime fechaRegistro;

    public Long getIdLimiteTasa() {
        return idLimiteTasa;
    }

    public void setIdLimiteTasa(Long idLimiteTasa) {
        this.idLimiteTasa = idLimiteTasa;
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

    public BigDecimal getTasaMaximaTea() {
        return tasaMaximaTea;
    }

    public void setTasaMaximaTea(BigDecimal tasaMaximaTea) {
        this.tasaMaximaTea = tasaMaximaTea;
    }

    public String getFuenteReferencia() {
        return fuenteReferencia;
    }

    public void setFuenteReferencia(String fuenteReferencia) {
        this.fuenteReferencia = fuenteReferencia;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
