package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * B5 (Bloque 7/E10, pedido de Joel): una fila del informe de Caja (/reportes/caja) -- la MISMA
 * fila alimenta la pantalla, el informe PDF y el CSV. fondoInicial/saldoTeorico/efectivoContado/
 * diferencia quedan en BigDecimal (sin formatear) para poder sumarlos (totales, CSV numérico);
 * los *Formateado son los mismos valores en guaraníes, para pantalla y PDF. diferenciaFormateada
 * ya viene con el signo en palabras ("Sobra X Gs." / "Falta X Gs." / "0 Gs.", A3d/ServicioCaja.
 * formatearDiferenciaArqueo) -- este DTO no calcula nada (REGLAS_DESARROLLO.md, sección 6).
 */
public class FilaInformeCaja {

    private final long idSesionCaja;
    private final String cajeraNombre;
    private final String apertura;
    private final String cierre;
    private final BigDecimal fondoInicial;
    private final String fondoInicialFormateado;
    private final BigDecimal saldoTeorico;
    private final String saldoTeoricoFormateado;
    private final BigDecimal efectivoContado;
    private final String efectivoContadoFormateado;
    private final BigDecimal diferencia;
    private final String diferenciaFormateada;
    private final boolean revisada;

    public FilaInformeCaja(long idSesionCaja, String cajeraNombre, String apertura, String cierre,
            BigDecimal fondoInicial, String fondoInicialFormateado, BigDecimal saldoTeorico,
            String saldoTeoricoFormateado, BigDecimal efectivoContado, String efectivoContadoFormateado,
            BigDecimal diferencia, String diferenciaFormateada, boolean revisada) {
        this.idSesionCaja = idSesionCaja;
        this.cajeraNombre = cajeraNombre;
        this.apertura = apertura;
        this.cierre = cierre;
        this.fondoInicial = fondoInicial;
        this.fondoInicialFormateado = fondoInicialFormateado;
        this.saldoTeorico = saldoTeorico;
        this.saldoTeoricoFormateado = saldoTeoricoFormateado;
        this.efectivoContado = efectivoContado;
        this.efectivoContadoFormateado = efectivoContadoFormateado;
        this.diferencia = diferencia;
        this.diferenciaFormateada = diferenciaFormateada;
        this.revisada = revisada;
    }

    public long getIdSesionCaja() {
        return idSesionCaja;
    }

    public String getCajeraNombre() {
        return cajeraNombre;
    }

    public String getApertura() {
        return apertura;
    }

    public String getCierre() {
        return cierre;
    }

    public BigDecimal getFondoInicial() {
        return fondoInicial;
    }

    public String getFondoInicialFormateado() {
        return fondoInicialFormateado;
    }

    public BigDecimal getSaldoTeorico() {
        return saldoTeorico;
    }

    public String getSaldoTeoricoFormateado() {
        return saldoTeoricoFormateado;
    }

    public BigDecimal getEfectivoContado() {
        return efectivoContado;
    }

    public String getEfectivoContadoFormateado() {
        return efectivoContadoFormateado;
    }

    /** Con signo: positiva = sobra, negativa = falta. */
    public BigDecimal getDiferencia() {
        return diferencia;
    }

    /** "Sobra X Gs." / "Falta X Gs." / "0 Gs." (A3d, ServicioCaja.formatearDiferenciaArqueo). */
    public String getDiferenciaFormateada() {
        return diferenciaFormateada;
    }

    public boolean isRevisada() {
        return revisada;
    }
}
