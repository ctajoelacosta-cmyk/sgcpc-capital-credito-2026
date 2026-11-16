package py.com.capitalcredito.sgcpc.dto;

/** B2 (Bloque 7/E10, pedido de Joel): una fila del resumen por estado del informe de Solicitudes
 * (cantidad y monto agrupados por estado_solicitud), debajo del <tfoot> de totales. */
public class ResumenEstadoSolicitud {

    private final String estado;
    private final int cantidad;
    private final String monto;

    public ResumenEstadoSolicitud(String estado, int cantidad, String monto) {
        this.estado = estado;
        this.cantidad = cantidad;
        this.monto = monto;
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
}
