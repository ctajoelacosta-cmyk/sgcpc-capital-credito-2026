package py.com.capitalcredito.sgcpc.dto;

/**
 * RF08 (pedido de Joel, 11/10/2026): una fila YA FORMATEADA de la tabla "Resultado del buró" en
 * `aprobaciones/ficha.jsp` -- armada por `ServicioAprobacionCredito.construirFilasConsultaBuro`.
 * `posteriorAlEnvio` marca una consulta registrada después de `fecha_envio_aprobacion` (hallazgo
 * del 11/10/2026: una consulta así quedaba invisible para el gerente porque la ficha leía solo
 * la vinculada al enviar) -- la JSP le agrega la leyenda "Registrada después del envío". No
 * confundir con `FilaConsultaBuro` (C-4.4), que es la fila del informe general
 * `/reportes/consultas-buro` -- distinta pantalla, distintas columnas.
 */
public class FilaBuroSolicitud {

    private final String personaTexto;
    private final String fechaFormateada;
    private final String puntajeFormateado;
    private final String clasificacion;
    private final boolean mora;
    private final String resumen;
    private final Long idDocumentoResultado;
    private final boolean posteriorAlEnvio;

    public FilaBuroSolicitud(String personaTexto, String fechaFormateada, String puntajeFormateado,
            String clasificacion, boolean mora, String resumen, Long idDocumentoResultado, boolean posteriorAlEnvio) {
        this.personaTexto = personaTexto;
        this.fechaFormateada = fechaFormateada;
        this.puntajeFormateado = puntajeFormateado;
        this.clasificacion = clasificacion;
        this.mora = mora;
        this.resumen = resumen;
        this.idDocumentoResultado = idDocumentoResultado;
        this.posteriorAlEnvio = posteriorAlEnvio;
    }

    public String getPersonaTexto() {
        return personaTexto;
    }

    public String getFechaFormateada() {
        return fechaFormateada;
    }

    public String getPuntajeFormateado() {
        return puntajeFormateado;
    }

    public String getClasificacion() {
        return clasificacion;
    }

    public boolean isMora() {
        return mora;
    }

    public String getResumen() {
        return resumen;
    }

    public Long getIdDocumentoResultado() {
        return idDocumentoResultado;
    }

    public boolean isPosteriorAlEnvio() {
        return posteriorAlEnvio;
    }
}
