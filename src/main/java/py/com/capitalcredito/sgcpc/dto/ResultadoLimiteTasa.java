package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * Salida de ServicioMotorFinanciero.validarLimiteTasa (RN-15). Quién límite
 * histórico de limites_tasa_usura rige a una fecha es una consulta (DAO),
 * no una fórmula: este método recibe la tasa máxima ya resuelta y solo
 * compara (ver AVANCE.md, decisión D-22).
 */
public class ResultadoLimiteTasa {

    private final boolean ok;
    private final BigDecimal tasaMaximaAplicada;

    public ResultadoLimiteTasa(boolean ok, BigDecimal tasaMaximaAplicada) {
        this.ok = ok;
        this.tasaMaximaAplicada = tasaMaximaAplicada;
    }

    public boolean isOk() {
        return ok;
    }

    public BigDecimal getTasaMaximaAplicada() {
        return tasaMaximaAplicada;
    }
}
