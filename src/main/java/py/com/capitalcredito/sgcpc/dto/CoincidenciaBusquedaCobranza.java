package py.com.capitalcredito.sgcpc.dto;

/**
 * D-146 (A3b, #6, búsqueda ambigua de Cobranza, pedido de Joel): una fila de la lista de
 * coincidencias cuando el texto buscado coincide con 2 o más clientes y ninguno tiene préstamo
 * cobrable -- la misma situación que ya resuelve el caso de un solo cliente (último préstamo
 * cancelado, o pendiente de desembolso), una por cada cliente. Todo ya viene armado desde el
 * servicio -- la JSP no decide nada (REGLAS_DESARROLLO.md §5).
 */
public class CoincidenciaBusquedaCobranza {

    private final String nombreCompleto;
    private final String cedula;
    private final String situacion;
    private final Long idPrestamoEnlace;
    private final boolean enlaceADesembolso;
    private final boolean enlaceAFormalizacion;

    /** situacion: nullable -- null si el cliente nunca tuvo ningún préstamo. idPrestamoEnlace:
     * nullable, igual que en ResultadoBusquedaCobranza. enlaceAFormalizacion: RF20, préstamo
     * anulado antes del desembolso, solo si el perfil tiene permiso de ver Formalización. */
    public CoincidenciaBusquedaCobranza(String nombreCompleto, String cedula, String situacion,
            Long idPrestamoEnlace, boolean enlaceADesembolso, boolean enlaceAFormalizacion) {
        this.nombreCompleto = nombreCompleto;
        this.cedula = cedula;
        this.situacion = situacion;
        this.idPrestamoEnlace = idPrestamoEnlace;
        this.enlaceADesembolso = enlaceADesembolso;
        this.enlaceAFormalizacion = enlaceAFormalizacion;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCedula() {
        return cedula;
    }

    public String getSituacion() {
        return situacion;
    }

    public Long getIdPrestamoEnlace() {
        return idPrestamoEnlace;
    }

    public boolean isEnlaceADesembolso() {
        return enlaceADesembolso;
    }

    public boolean isEnlaceAFormalizacion() {
        return enlaceAFormalizacion;
    }
}
