package py.com.capitalcredito.sgcpc.dto;

/**
 * RF07 (pedido de Joel, 11/10/2026): una fila YA FORMATEADA de la bandeja de aprobación
 * (Pendientes o Historial, aprobaciones/lista.jsp) -- se calcula entera en
 * ServicioAprobacionCredito.construirFilaBandeja, antes de llegar a la JSP, para que un dato
 * faltante (ej. una solicitud CANCELADA que nunca se llegó a enviar a aprobación, sin
 * fecha_envio_aprobacion) no deje la página dibujada a medias (ver RF07_bandeja_aprobacion.md,
 * hallazgo del 11/10/2026: NullPointerException en lista.jsp con SOL-2026-0003). Los campos de
 * Historial (decisionEtiqueta/decididaPorNombre/fechaDecisionFormateada) quedan en null para
 * las filas de Pendientes, y viceversa con esperando.
 */
public class FilaBandejaAprobacion {

    private final long idSolicitud;
    private final String codigo;
    private final String fechaEnvioFormateada;
    private final String esperando;
    private final String clienteTexto;
    private final String clienteNombres;
    private final String clienteApellidos;
    private final String clienteCedula;
    private final String montoSolicitadoFormateado;
    private final String plazoTexto;
    private final String afectacionFormateada;
    private final String decisionEtiqueta;
    private final String decididaPorNombre;
    private final String fechaDecisionFormateada;
    /**
     * B174 (14/11/2026): el oficial que envió la solicitud. Solo se llena en la vista de
     * Pendientes -- en el Historial la columna que corresponde es "Decidida por", y poner las dos
     * sería ruido. En Pendientes es el dato que el gerente nombra primero cuando una solicitud le
     * llama la atención: a quién preguntarle.
     */
    private final String enviadaPorNombre;

    public FilaBandejaAprobacion(long idSolicitud, String codigo, String fechaEnvioFormateada, String esperando,
            String clienteTexto, String clienteNombres, String clienteApellidos, String clienteCedula,
            String montoSolicitadoFormateado, String plazoTexto, String afectacionFormateada,
            String decisionEtiqueta, String decididaPorNombre, String fechaDecisionFormateada,
            String enviadaPorNombre) {
        this.idSolicitud = idSolicitud;
        this.codigo = codigo;
        this.fechaEnvioFormateada = fechaEnvioFormateada;
        this.esperando = esperando;
        this.clienteTexto = clienteTexto;
        this.clienteNombres = clienteNombres;
        this.clienteApellidos = clienteApellidos;
        this.clienteCedula = clienteCedula;
        this.montoSolicitadoFormateado = montoSolicitadoFormateado;
        this.plazoTexto = plazoTexto;
        this.afectacionFormateada = afectacionFormateada;
        this.decisionEtiqueta = decisionEtiqueta;
        this.decididaPorNombre = decididaPorNombre;
        this.fechaDecisionFormateada = fechaDecisionFormateada;
        this.enviadaPorNombre = enviadaPorNombre;
    }

    /** B174: el oficial que la envió; null en el Historial. */
    public String getEnviadaPorNombre() {
        return enviadaPorNombre;
    }

    public long getIdSolicitud() {
        return idSolicitud;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getFechaEnvioFormateada() {
        return fechaEnvioFormateada;
    }

    public String getEsperando() {
        return esperando;
    }

    public String getClienteTexto() {
        return clienteTexto;
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): nombres,
     * apellidos y cédula por separado, solo para que el buscador de /aprobaciones pueda comparar
     * "nombre apellido" Y "apellido nombre" y la cédula con o sin puntos -- mismo criterio que
     * FilaMora.getClienteNombres/getClienteApellidos. clienteTexto sigue siendo el único que usa
     * la columna "Cliente" de la tabla, con su formato fijo de siempre. Null si no se pudo
     * resolver el cliente (mismo caso que deja clienteTexto en "-"). */
    public String getClienteNombres() {
        return clienteNombres;
    }

    public String getClienteApellidos() {
        return clienteApellidos;
    }

    public String getClienteCedula() {
        return clienteCedula;
    }

    public String getMontoSolicitadoFormateado() {
        return montoSolicitadoFormateado;
    }

    public String getPlazoTexto() {
        return plazoTexto;
    }

    public String getAfectacionFormateada() {
        return afectacionFormateada;
    }

    public String getDecisionEtiqueta() {
        return decisionEtiqueta;
    }

    public String getDecididaPorNombre() {
        return decididaPorNombre;
    }

    public String getFechaDecisionFormateada() {
        return fechaDecisionFormateada;
    }
}
