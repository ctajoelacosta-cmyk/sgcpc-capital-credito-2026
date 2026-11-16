package py.com.capitalcredito.sgcpc.dto;

/** Bloque C, tanda 1 (pedido de Joel): resumen "al día" (Activo) / "en mora" del reporte Cartera
 * -- cantidad y saldo agrupados por estado_prestamo, mismo criterio que ResumenEstadoPrestamo
 * (B1), debajo del total general. */
public class ResumenEstadoCartera {

    private final String estado;
    private final int cantidad;
    private final String saldo;

    public ResumenEstadoCartera(String estado, int cantidad, String saldo) {
        this.estado = estado;
        this.cantidad = cantidad;
        this.saldo = saldo;
    }

    public String getEstado() {
        return estado;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getSaldo() {
        return saldo;
    }
}
