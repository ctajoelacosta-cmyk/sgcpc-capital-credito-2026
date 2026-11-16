package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/** Una fila del informe "Reversiones" desde-hasta (Bloque 7/E10, Tanda D, D-128; ampliado Bloque C
 * tanda 3 a pedido de Joel: recibo revertido, monto, quién solicitó, quién autorizó y nota de
 * anulación). Todo ya formateado; montoCrudo queda para los totales (solo las AUTORIZADA son
 * dinero que realmente salió de caja). */
public class FilaReversionReporte {

    private final String fecha;
    private final long idPrestamo;
    private final String numeroRecibo;
    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String montoFormateado;
    private final BigDecimal montoCrudo;
    private final String motivo;
    private final String estado;
    private final boolean autorizada;
    private final String solicitadoPorNombre;
    private final String autorizadoPorNombre;
    private final String notaAnulacion;

    public FilaReversionReporte(String fecha, long idPrestamo, String numeroRecibo, String identificadorOperacion,
            String clienteNombre, String montoFormateado, BigDecimal montoCrudo, String motivo, String estado,
            boolean autorizada, String solicitadoPorNombre, String autorizadoPorNombre, String notaAnulacion) {
        this.fecha = fecha;
        this.idPrestamo = idPrestamo;
        this.numeroRecibo = numeroRecibo;
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.montoFormateado = montoFormateado;
        this.montoCrudo = montoCrudo;
        this.motivo = motivo;
        this.estado = estado;
        this.autorizada = autorizada;
        this.solicitadoPorNombre = solicitadoPorNombre;
        this.autorizadoPorNombre = autorizadoPorNombre;
        this.notaAnulacion = notaAnulacion;
    }

    public String getFecha() {
        return fecha;
    }

    /** Clic en la fila -> estado de cuenta (mismo patrón que el resto de los informes). */
    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getMontoFormateado() {
        return montoFormateado;
    }

    public BigDecimal getMontoCrudo() {
        return montoCrudo;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getEstado() {
        return estado;
    }

    /** Pura si el dinero realmente salió de caja (AUTORIZADA) -- las totales solo suman estas. */
    public boolean isAutorizada() {
        return autorizada;
    }

    public String getSolicitadoPorNombre() {
        return solicitadoPorNombre;
    }

    /** "—" si todavía está SOLICITADA o si quedó RECHAZADA (nadie autoriza un rechazo). */
    public String getAutorizadoPorNombre() {
        return autorizadoPorNombre;
    }

    /** Número de la Nota de anulación (NAN-...) si la reversión anuló una factura, o "—". */
    public String getNotaAnulacion() {
        return notaAnulacion;
    }
}
