package py.com.capitalcredito.sgcpc.dto;

/** Bloque C, tanda 1 (pedido de Joel): una fila de subtotal por tramo de atraso del INFORME de
 * Morosidad -- cantidad, capital vencido (solo la parte capital de las cuotas ya vencidas) y
 * saldo (capital pendiente de todo el préstamo, mismo cálculo que el listado de Préstamos). Se
 * reusa también para la fila de "Total general" (tramo = "Total general"). DTO propio de este
 * informe -- no SubtotalTramo (ese sigue con tramo/cantidad/monto, compartido con los tramos de
 * monto/plazo de otros reportes, D-128; no se toca). */
public class SubtotalTramoMorosidad {

    private final String tramo;
    private final int cantidad;
    private final String capitalVencido;
    private final String saldo;

    public SubtotalTramoMorosidad(String tramo, int cantidad, String capitalVencido, String saldo) {
        this.tramo = tramo;
        this.cantidad = cantidad;
        this.capitalVencido = capitalVencido;
        this.saldo = saldo;
    }

    public String getTramo() {
        return tramo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getCapitalVencido() {
        return capitalVencido;
    }

    public String getSaldo() {
        return saldo;
    }
}
