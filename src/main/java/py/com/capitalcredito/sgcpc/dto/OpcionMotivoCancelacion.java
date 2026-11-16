package py.com.capitalcredito.sgcpc.dto;

/**
 * RF06 (pedido de Joel, 10/10/2026): una opción de la lista fija de motivos de cancelación de una
 * solicitud de crédito -- lista fija en código (ServicioSolicitudes.listarMotivosCancelacion), no
 * en una tabla como motivos_rechazo_solicitud (RF10): son pocas opciones y no hace falta editarlas
 * desde una pantalla, mismo criterio que ServicioClientes.MOTIVOS_BAJA.
 */
public class OpcionMotivoCancelacion {

    private final String codigo;
    private final String etiqueta;
    private final boolean requiereDetalle;

    public OpcionMotivoCancelacion(String codigo, String etiqueta, boolean requiereDetalle) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.requiereDetalle = requiereDetalle;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isRequiereDetalle() {
        return requiereDetalle;
    }
}
