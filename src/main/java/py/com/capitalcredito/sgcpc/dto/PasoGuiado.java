package py.com.capitalcredito.sgcpc.dto;

/**
 * B88 (pedido explícito de Joel, 28/10/2026; orden "opción B" decidido el 29/10): el PRIMER paso
 * pendiente del circuito del oficial -- qué decirle y a dónde llevarlo después de cada acción.
 *
 * <p>Lo calcula un solo dueño, {@code ServicioClientes.calcularSiguientePaso} (regla "un dato, un
 * dueño"): antes cada pantalla tenía su propio destino fijo en el código, sin mirar el estado real
 * -- de ahí los dos casos que vio Joel el 28/10 (cargar la autorización con el domicilio pendiente
 * saltaba a ③; cargar el domicilio con el buró ya registrado seguía diciendo "registrar el
 * buró").</p>
 *
 * <p><b>Nunca cambia lo que se PERMITE hacer</b> (el oficial puede cargar cualquier documento o
 * crear la solicitud cuando quiera, y la consulta al buró no se bloquea por el expediente): solo
 * dice a dónde lleva el sistema y qué texto muestra.</p>
 */
public class PasoGuiado {

    /**
     * A dónde llevar al oficial. {@code NINGUNO} = no se lo empuja a ninguna pantalla (el paso no
     * es una pantalla a completar: hay un bloqueo de política, o hay que esperar a otra persona).
     */
    public enum Destino {
        EXPEDIENTE,
        BURO,
        SOLICITUD_NUEVA,
        SOLICITUD_FICHA,
        /**
         * B104 (01/11/2026): formalizar una solicitud ya aprobada -- {@code /formalizacion/nueva/{id}}.
         * Usa {@link PasoGuiado#getIdSolicitud()}.
         */
        FORMALIZACION_NUEVA,
        /**
         * B100/B104 (01/11/2026): la ficha del préstamo ya formalizado, donde se cargan los
         * firmados -- {@code /formalizacion/{idPrestamo}}. Usa {@link PasoGuiado#getIdPrestamo()}.
         */
        FORMALIZACION_FICHA,
        NINGUNO,
        /**
         * La pestaña ① Datos. <b>El dueño nunca devuelve este destino</b> (el alta y la edición
         * dejan al oficial en la ficha, no lo mandan a ningún lado): existe para que cada pestaña
         * pueda decir "estoy acá" en {@link PasoGuiado#ofreceBotonEn}.
         */
        DATOS
    }

    private final String texto;
    private final Destino destino;
    private final String etiquetaBoton;
    private final Long idSolicitud;
    private final Long idPrestamo;
    private final String ruta;

    public PasoGuiado(String texto, Destino destino, String etiquetaBoton) {
        this(texto, destino, etiquetaBoton, null, null, null);
    }

    public PasoGuiado(String texto, Destino destino, String etiquetaBoton, Long idSolicitud) {
        this(texto, destino, etiquetaBoton, idSolicitud, null, null);
    }

    /** B100/B104: el paso que lleva a la ficha de un préstamo ya formalizado. */
    public static PasoGuiado haciaPrestamo(String texto, String etiquetaBoton, Long idPrestamo) {
        return new PasoGuiado(texto, Destino.FORMALIZACION_FICHA, etiquetaBoton, null, idPrestamo, null);
    }

    private PasoGuiado(String texto, Destino destino, String etiquetaBoton, Long idSolicitud, Long idPrestamo,
            String ruta) {
        this.texto = texto;
        this.destino = destino;
        this.etiquetaBoton = etiquetaBoton;
        this.idSolicitud = idSolicitud;
        this.idPrestamo = idPrestamo;
        this.ruta = ruta;
    }

    /**
     * El mismo paso con su ruta ya resuelta (ver {@link #rutaRelativa}) -- lo arma
     * {@code ServicioPasoGuiado}, que es el único que tiene a mano la cédula del cliente; así cada
     * pantalla usa {@link #getRuta()} sin volver a leer nada ni repetir el mapeo.
     */
    public PasoGuiado conRuta(String rutaResuelta) {
        return new PasoGuiado(texto, destino, etiquetaBoton, idSolicitud, idPrestamo, rutaResuelta);
    }

    /** Lo que se le dice al oficial; null cuando no hay nada que avisar (todo al día, nada que empujar). */
    public String getTexto() {
        return texto;
    }

    public Destino getDestino() {
        return destino;
    }

    /**
     * Texto corto del botón "Siguiente: …" de la ficha del cliente (ej. "② Expediente",
     * "④ SOL-2026-0005"); null cuando no corresponde ofrecer ningún botón.
     */
    public String getEtiquetaBoton() {
        return etiquetaBoton;
    }

    /** La solicitud a la que lleva el paso; null salvo en {@code SOLICITUD_FICHA}/{@code FORMALIZACION_NUEVA}. */
    public Long getIdSolicitud() {
        return idSolicitud;
    }

    /** El préstamo al que lleva el paso; null salvo en {@code FORMALIZACION_FICHA}. */
    public Long getIdPrestamo() {
        return idPrestamo;
    }

    /**
     * La ruta del destino, sin el context path, ya resuelta por {@code ServicioPasoGuiado} -- null
     * si no hay que llevar a ningún lado. El llamador le pone adelante
     * {@code request.getContextPath()}.
     */
    public String getRuta() {
        return ruta;
    }

    public boolean tieneTexto() {
        return texto != null && !texto.trim().isEmpty();
    }

    /**
     * ¿Se ofrece el botón "Siguiente: …" en una pestaña que muestra {@code pestanaActual}?
     *
     * <p><b>No, si el paso pendiente es esa misma pestaña</b> (hallazgo de Joel en la prueba del
     * 29/10/2026: parado en ② el botón decía "Siguiente: ② Expediente →", y en ③ "Siguiente:
     * ③ Consulta al buró →" -- un botón que lleva a donde ya estás). En ese caso el aviso de la
     * acción y las filas marcadas "Falta" ya guían solos. Pura.</p>
     */
    public boolean ofreceBotonEn(Destino pestanaActual) {
        return etiquetaBoton != null && destino != pestanaActual;
    }

    /**
     * El ancla de la pestaña de la ficha del cliente donde está este paso pendiente
     * ({@code "#tabDocumentos"} / {@code "#tabBuro"}), para que un enlace a la ficha abra
     * directamente ahí -- cadena vacía si el paso no vive en una pestaña de la ficha (o no hay nada
     * pendiente): la ficha abre en ① Datos, como siempre.
     *
     * <p>Decisión de Joel del 29/10/2026 (prueba de B88): el enlace "Ver ficha completa /
     * documentos" de la solicitud abría siempre ① Datos, aunque lo que faltara fuera un documento.
     * Mismas anclas que {@link #rutaRelativa}, en el mismo lugar. Pura.</p>
     */
    public String anclaPestanaFicha() {
        if (destino == Destino.EXPEDIENTE) {
            return "#tabDocumentos";
        }
        if (destino == Destino.BURO) {
            return "#tabBuro";
        }
        return "";
    }

    /**
     * La ruta a la que lleva este paso, <b>sin</b> el context path (el llamador le pone adelante
     * {@code request.getContextPath()}, igual que el resto del sistema) -- null cuando no hay que
     * llevar a ningún lado ({@code NINGUNO}, o un destino sin el dato que necesita su URL).
     *
     * <p>Acá, y en un solo lugar, vive la traducción "destino → pantalla": la usan el redirect de
     * cada carga de documento, el del registro del buró y los botones "Siguiente: …" de la ficha.
     * Pura.</p>
     */
    public String rutaRelativa(long idPersona, String cedulaCliente) {
        if (destino == null) {
            return null;
        }
        switch (destino) {
            case EXPEDIENTE:
                return "/clientes/" + idPersona + "#tabDocumentos";
            case BURO:
                return "/clientes/" + idPersona + "#tabBuro";
            case SOLICITUD_NUEVA:
                return cedulaCliente == null ? null : "/solicitudes/nuevo?cedulaCliente=" + cedulaCliente;
            case SOLICITUD_FICHA:
                return idSolicitud == null ? null : "/solicitudes/" + idSolicitud;
            case FORMALIZACION_NUEVA:
                return idSolicitud == null ? null : "/formalizacion/nueva/" + idSolicitud;
            case FORMALIZACION_FICHA:
                return idPrestamo == null ? null : "/formalizacion/" + idPrestamo;
            default:
                return null;
        }
    }
}
