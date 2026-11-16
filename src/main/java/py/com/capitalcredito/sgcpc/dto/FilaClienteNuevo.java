package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Clientes nuevos" desde-hasta (Bloque 7/E10, Tanda D, D-128; ampliado
 * Bloque C tanda 3 a pedido de Joel: ciudad, situación laboral y préstamo). Todo ya formateado.
 * C-4 paso 1 (pedido de Joel): la columna de préstamo pasó de "Sí"/"No" (ACTIVO/EN_MORA nada más,
 * que mostraba "No" para un cliente con un préstamo PENDIENTE_DESEMBOLSO o ya FINALIZADO) a mostrar
 * el estado real del último préstamo del cliente -- "—" si nunca tuvo uno. */
public class FilaClienteNuevo {

    private final long idPersona;
    private final String cedula;
    private final String nombre;
    private final String fechaRegistro;
    private final String telefono;
    private final String ciudad;
    private final String situacionLaboral;
    private final String estadoUltimoPrestamo;

    public FilaClienteNuevo(long idPersona, String cedula, String nombre, String fechaRegistro, String telefono,
            String ciudad, String situacionLaboral, String estadoUltimoPrestamo) {
        this.idPersona = idPersona;
        this.cedula = cedula;
        this.nombre = nombre;
        this.fechaRegistro = fechaRegistro;
        this.telefono = telefono;
        this.ciudad = ciudad;
        this.situacionLaboral = situacionLaboral;
        this.estadoUltimoPrestamo = estadoUltimoPrestamo;
    }

    /** Clic en la fila -> ficha del cliente (mismo patrón que el resto de los informes). */
    public long getIdPersona() {
        return idPersona;
    }

    public String getCedula() {
        return cedula;
    }

    public String getNombre() {
        return nombre;
    }

    public String getFechaRegistro() {
        return fechaRegistro;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getSituacionLaboral() {
        return situacionLaboral;
    }

    /** "Activo" / "En mora" / "Pendiente de desembolso" / "Finalizado" / "Anulado antes del
     * desembolso" (estado real del último préstamo, EstadoPrestamo.getEtiqueta()) o "—" si el
     * cliente nunca tuvo un préstamo. Mismo texto en pantalla, PDF y CSV. */
    public String getEstadoUltimoPrestamo() {
        return estadoUltimoPrestamo;
    }
}
