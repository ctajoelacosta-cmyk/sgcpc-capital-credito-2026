package py.com.capitalcredito.sgcpc.dto;

/** Totales del reporte Incobrables (§13, Bloque 4, E8). Bloque C, tanda 1 (pedido de Joel): el
 * total pasa a ser el SALDO (capital pendiente, mismo cálculo que el listado de Préstamos) --
 * antes sumaba el monto exigible completo (capital + interés + mora + punitorio + IVA de las
 * cuotas ya vencidas), que no es lo que Joel pidió para este total. */
public class TotalesIncobrables {

    private final int cantidad;
    private final String saldo;

    public TotalesIncobrables(int cantidad, String saldo) {
        this.cantidad = cantidad;
        this.saldo = saldo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getSaldo() {
        return saldo;
    }
}
