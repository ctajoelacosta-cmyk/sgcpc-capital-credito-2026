package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Gestiones de cobranza" desde-hasta (Bloque C, tanda 3, pedido de Joel).
 * Todo ya formateado; los tres campos crudos (tienePromesa/cumplida/vencidaSinCumplir) son los que
 * necesitan los totales, calculados acá (contra la fecha de hoy) para que calcularTotalesGestiones
 * sea pura, sin volver a comparar fechas. */
public class FilaGestionReporte {

    private final String fechaHora;
    private final long idPrestamo;
    private final String identificadorOperacion;
    private final String contactadoNombre;
    private final String tipoEtiqueta;
    private final String resultado;
    private final boolean tienePromesa;
    private final String promesaTexto;
    private final boolean cumplida;
    private final boolean vencidaSinCumplir;
    private final String registradoPorNombre;

    public FilaGestionReporte(String fechaHora, long idPrestamo, String identificadorOperacion,
            String contactadoNombre, String tipoEtiqueta, String resultado, boolean tienePromesa,
            String promesaTexto, boolean cumplida, boolean vencidaSinCumplir, String registradoPorNombre) {
        this.fechaHora = fechaHora;
        this.idPrestamo = idPrestamo;
        this.identificadorOperacion = identificadorOperacion;
        this.contactadoNombre = contactadoNombre;
        this.tipoEtiqueta = tipoEtiqueta;
        this.resultado = resultado;
        this.tienePromesa = tienePromesa;
        this.promesaTexto = promesaTexto;
        this.cumplida = cumplida;
        this.vencidaSinCumplir = vencidaSinCumplir;
        this.registradoPorNombre = registradoPorNombre;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    /** Clic en la fila -> estado de cuenta (mismo patrón que el resto de los informes). */
    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getContactadoNombre() {
        return contactadoNombre;
    }

    public String getTipoEtiqueta() {
        return tipoEtiqueta;
    }

    public String getResultado() {
        return resultado;
    }

    public boolean isTienePromesa() {
        return tienePromesa;
    }

    /** "dd/mm/aaaa — monto Gs." o "—" si no hubo promesa. */
    public String getPromesaTexto() {
        return promesaTexto;
    }

    public boolean isCumplida() {
        return cumplida;
    }

    /** "Sí" / "No" / "—" (sin promesa) -- mismo texto para pantalla, PDF y CSV. */
    public String getCumplidaTexto() {
        if (!tienePromesa) {
            return "—";
        }
        return cumplida ? "Sí" : "No";
    }

    public boolean isVencidaSinCumplir() {
        return vencidaSinCumplir;
    }

    public String getRegistradoPorNombre() {
        return registradoPorNombre;
    }
}
