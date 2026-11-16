package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;

/** Una fila cruda del historial de parámetros (C-4.3, pedido de Joel), tal como la arma
 * ParametroDAO.listarHistorial -- el valor anterior/nuevo todavía sin formatear según el tipo
 * (ServicioReportes.construirFilasHistorialParametros hace esa parte, pura). "Sin pisar nada": el
 * dato ya estaba completo en parametros_sistema (las vigencias viejas se cierran con
 * vigente_hasta, nunca se pisan) -- esta consulta solo lo expone, no agrega ni cambia nada. */
public class FilaHistorialParametroCrudo {

    private final String claveParametro;
    private final String nombreParametro;
    private final String valorAnteriorCrudo;
    private final String valorNuevoCrudo;
    private final TipoValorParametro tipoValor;
    private final LocalDate vigenteDesde;
    private final LocalDate vigenteHasta;
    private final String modificadoPorNombre;
    private final LocalDateTime fechaModificacion;
    private final String observacion;

    public FilaHistorialParametroCrudo(String claveParametro, String nombreParametro, String valorAnteriorCrudo,
            String valorNuevoCrudo, TipoValorParametro tipoValor, LocalDate vigenteDesde, LocalDate vigenteHasta,
            String modificadoPorNombre, LocalDateTime fechaModificacion, String observacion) {
        this.claveParametro = claveParametro;
        this.nombreParametro = nombreParametro;
        this.valorAnteriorCrudo = valorAnteriorCrudo;
        this.valorNuevoCrudo = valorNuevoCrudo;
        this.tipoValor = tipoValor;
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
        this.modificadoPorNombre = modificadoPorNombre;
        this.fechaModificacion = fechaModificacion;
        this.observacion = observacion;
    }

    public String getClaveParametro() {
        return claveParametro;
    }

    public String getNombreParametro() {
        return nombreParametro;
    }

    /** null si esta es la primera fila del parámetro (no hay una vigencia anterior). */
    public String getValorAnteriorCrudo() {
        return valorAnteriorCrudo;
    }

    public String getValorNuevoCrudo() {
        return valorNuevoCrudo;
    }

    public TipoValorParametro getTipoValor() {
        return tipoValor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    /** null si esta sigue siendo la vigencia actual. */
    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public String getModificadoPorNombre() {
        return modificadoPorNombre;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public String getObservacion() {
        return observacion;
    }
}
