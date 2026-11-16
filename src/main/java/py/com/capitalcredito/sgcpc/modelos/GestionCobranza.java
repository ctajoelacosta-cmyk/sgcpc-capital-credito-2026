package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;

/** Espejo de la tabla gestiones_cobranza (bd/01_esquema.sql, RN-20, P09/P11, E7). */
public class GestionCobranza {

    private Long idGestionCobranza;
    private long idPrestamo;
    private long idPersonaContactada;
    private TipoGestionCobranza tipo;
    private String resultado;
    private String excusa;
    private LocalDate fechaPromesa;
    private BigDecimal montoPrometido;
    private boolean cumplida;
    private long registradoPor;
    private LocalDateTime fechaHora;
    private Long idPago;

    public Long getIdGestionCobranza() {
        return idGestionCobranza;
    }

    public void setIdGestionCobranza(Long idGestionCobranza) {
        this.idGestionCobranza = idGestionCobranza;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public long getIdPersonaContactada() {
        return idPersonaContactada;
    }

    public void setIdPersonaContactada(long idPersonaContactada) {
        this.idPersonaContactada = idPersonaContactada;
    }

    public TipoGestionCobranza getTipo() {
        return tipo;
    }

    public void setTipo(TipoGestionCobranza tipo) {
        this.tipo = tipo;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getExcusa() {
        return excusa;
    }

    public void setExcusa(String excusa) {
        this.excusa = excusa;
    }

    public LocalDate getFechaPromesa() {
        return fechaPromesa;
    }

    public void setFechaPromesa(LocalDate fechaPromesa) {
        this.fechaPromesa = fechaPromesa;
    }

    public BigDecimal getMontoPrometido() {
        return montoPrometido;
    }

    public void setMontoPrometido(BigDecimal montoPrometido) {
        this.montoPrometido = montoPrometido;
    }

    public boolean isCumplida() {
        return cumplida;
    }

    public void setCumplida(boolean cumplida) {
        this.cumplida = cumplida;
    }

    public long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** D-95 (E8): qué pago parcial generó esta gestión (solo las de tipo PAGO_PARCIAL). */
    public Long getIdPago() {
        return idPago;
    }

    public void setIdPago(Long idPago) {
        this.idPago = idPago;
    }
}
