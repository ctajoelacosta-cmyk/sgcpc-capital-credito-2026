package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * D-85 (hallazgo de Joel, 12/10/2026): total gravado al 10 % (neto + IVA, "lo que paga el
 * cliente") y total de IVA, sumados de las líneas de una factura -- extraído de
 * ServicioCobranza.emitirFactura para que cualquier otro lugar que arme una factura (real o una
 * muestra de PDF) use EXACTAMENTE el mismo cálculo, nunca uno rearmado a mano
 * (REGLAS_DESARROLLO.md §7 punto 9).
 */
public class TotalesLineasFactura {

    private final BigDecimal totalGravado;
    private final BigDecimal totalIva;

    public TotalesLineasFactura(BigDecimal totalGravado, BigDecimal totalIva) {
        this.totalGravado = totalGravado;
        this.totalIva = totalIva;
    }

    /** Neto + IVA de todas las líneas -- esto es lo que guarda facturas.total_gravada_10 Y
     * facturas.total (son el mismo valor, ver ServicioCobranza.emitirFactura). */
    public BigDecimal getTotalGravado() {
        return totalGravado;
    }

    public BigDecimal getTotalIva() {
        return totalIva;
    }
}
