package py.com.capitalcredito.sgcpc.modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Resultado de una consulta al buró, registrada a mano tras consultar Informconf fuera del sistema (Doc. 08 Tabla 09). */
public class ConsultaCrediticia {

    private Long idConsultaCrediticia;
    private Long idPersona;
    private Long idSolicitud;
    private Long idConsentimiento;
    private String proveedorBuro;
    private LocalDateTime fechaHoraConsulta;
    private Long consultadoPor;
    private BigDecimal puntajeObtenido;
    private String clasificacionObtenida;
    private Boolean indicadorMora;
    private String resumenResultado;
    private Long idDocumentoResultado;
    /** RF04 (pedido de Joel): nombre completo de quien consultó -- solo lo completa
     * ConsultaCrediticiaDAO.listarPorPersona (historial de la ficha); null en el resto de los
     * caminos (insertar, listarPorSolicitud), que no lo necesitan. */
    private String nombreConsultadoPor;

    public Long getIdConsultaCrediticia() {
        return idConsultaCrediticia;
    }

    public void setIdConsultaCrediticia(Long idConsultaCrediticia) {
        this.idConsultaCrediticia = idConsultaCrediticia;
    }

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(Long idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public Long getIdConsentimiento() {
        return idConsentimiento;
    }

    public void setIdConsentimiento(Long idConsentimiento) {
        this.idConsentimiento = idConsentimiento;
    }

    public String getProveedorBuro() {
        return proveedorBuro;
    }

    public void setProveedorBuro(String proveedorBuro) {
        this.proveedorBuro = proveedorBuro;
    }

    public LocalDateTime getFechaHoraConsulta() {
        return fechaHoraConsulta;
    }

    public void setFechaHoraConsulta(LocalDateTime fechaHoraConsulta) {
        this.fechaHoraConsulta = fechaHoraConsulta;
    }

    public Long getConsultadoPor() {
        return consultadoPor;
    }

    public void setConsultadoPor(Long consultadoPor) {
        this.consultadoPor = consultadoPor;
    }

    public BigDecimal getPuntajeObtenido() {
        return puntajeObtenido;
    }

    public void setPuntajeObtenido(BigDecimal puntajeObtenido) {
        this.puntajeObtenido = puntajeObtenido;
    }

    public String getClasificacionObtenida() {
        return clasificacionObtenida;
    }

    public void setClasificacionObtenida(String clasificacionObtenida) {
        this.clasificacionObtenida = clasificacionObtenida;
    }

    public Boolean getIndicadorMora() {
        return indicadorMora;
    }

    public void setIndicadorMora(Boolean indicadorMora) {
        this.indicadorMora = indicadorMora;
    }

    public String getResumenResultado() {
        return resumenResultado;
    }

    public void setResumenResultado(String resumenResultado) {
        this.resumenResultado = resumenResultado;
    }

    public Long getIdDocumentoResultado() {
        return idDocumentoResultado;
    }

    public void setIdDocumentoResultado(Long idDocumentoResultado) {
        this.idDocumentoResultado = idDocumentoResultado;
    }

    public String getNombreConsultadoPor() {
        return nombreConsultadoPor;
    }

    public void setNombreConsultadoPor(String nombreConsultadoPor) {
        this.nombreConsultadoPor = nombreConsultadoPor;
    }
}
