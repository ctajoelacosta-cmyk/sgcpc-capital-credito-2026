package py.com.capitalcredito.sgcpc.dto;

/**
 * Una fila del "detalle de movimientos" del arqueo de caja (docs/diseno_comprobantes_E8.md
 * sección 5). El tipo mostrado sale de una fuente confiable (movimientos_caja.tipo + el tipo del
 * pago que referencia, D-89), nunca de parsear la observación -- decisión de Joel. Todo ya viene
 * formateado desde Java (REGLAS_DESARROLLO.md §6).
 */
public class FilaMovimientoArqueo {

    private final String hora;
    private final String tipo;
    private final String operacion;
    private final String comprobante;
    private final String monto;

    public FilaMovimientoArqueo(String hora, String tipo, String operacion, String comprobante, String monto) {
        this.hora = hora;
        this.tipo = tipo;
        this.operacion = operacion;
        this.comprobante = comprobante;
        this.monto = monto;
    }

    public String getHora() {
        return hora;
    }

    public String getTipo() {
        return tipo;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getComprobante() {
        return comprobante;
    }

    public String getMonto() {
        return monto;
    }
}
