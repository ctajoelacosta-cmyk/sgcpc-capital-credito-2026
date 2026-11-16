package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Cierres documentales" desde-hasta (Bloque 7/E10, Tanda D, D-128). Todo ya
 * formateado. E-12 (pedido de Joel): ahora lista TODOS los préstamos cancelados en el período, no
 * solo los que ya tienen el pagaré devuelto -- pagareDevuelto distingue los dos casos; constancia
 * de devolución/fecha de devolución/quién entregó quedan en "—" mientras está pendiente. */
public class FilaCierreDocumentalReporte {

    private final long idPrestamo;
    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String fechaCancelacion;
    private final String numeroConstanciaCancelacion;
    private final boolean pagareDevuelto;
    private final String numeroConstanciaDevolucion;
    private final String fechaDevolucionPagare;
    private final String entregadoPorNombre;

    public FilaCierreDocumentalReporte(long idPrestamo, String identificadorOperacion, String clienteNombre,
            String fechaCancelacion, String numeroConstanciaCancelacion, boolean pagareDevuelto,
            String numeroConstanciaDevolucion, String fechaDevolucionPagare, String entregadoPorNombre) {
        this.idPrestamo = idPrestamo;
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.fechaCancelacion = fechaCancelacion;
        this.numeroConstanciaCancelacion = numeroConstanciaCancelacion;
        this.pagareDevuelto = pagareDevuelto;
        this.numeroConstanciaDevolucion = numeroConstanciaDevolucion;
        this.fechaDevolucionPagare = fechaDevolucionPagare;
        this.entregadoPorNombre = entregadoPorNombre;
    }

    /** Clic en la fila -> estado de cuenta (pedido de Joel). */
    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getFechaCancelacion() {
        return fechaCancelacion;
    }

    /** CAN-aaaa-nnnnnn, de la liquidación TOTAL del préstamo. */
    public String getNumeroConstanciaCancelacion() {
        return numeroConstanciaCancelacion;
    }

    public boolean isPagareDevuelto() {
        return pagareDevuelto;
    }

    /** "Devuelto" / "Pendiente de devolución" -- la JSP/PDF/CSV muestran este mismo texto,
     * ninguno repite la lógica (REGLAS_DESARROLLO.md, sección 6). */
    public String getEstadoPagare() {
        return pagareDevuelto ? "Devuelto" : "Pendiente de devolución";
    }

    /** DEV-aaaa-nnnnnn, o "—" si está pendiente. */
    public String getNumeroConstanciaDevolucion() {
        return numeroConstanciaDevolucion;
    }

    /** o "—" si está pendiente. */
    public String getFechaDevolucionPagare() {
        return fechaDevolucionPagare;
    }

    /** o "—" si está pendiente. */
    public String getEntregadoPorNombre() {
        return entregadoPorNombre;
    }
}
