package py.com.capitalcredito.sgcpc.dto;

/** B1 (Bloque 7/E10, pedido de Joel): una fila del resumen por estado del listado de Préstamos
 * (cantidad, monto y saldo agrupados por estado_prestamo), debajo del <tfoot> de totales. */
public class ResumenEstadoPrestamo {

    private final String estado;
    private final int cantidad;
    private final String monto;
    private final String saldo;

    public ResumenEstadoPrestamo(String estado, int cantidad, String monto, String saldo) {
        this.estado = estado;
        this.cantidad = cantidad;
        this.monto = monto;
        this.saldo = saldo;
    }

    public String getEstado() {
        return estado;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getMonto() {
        return monto;
    }

    public String getSaldo() {
        return saldo;
    }
}
