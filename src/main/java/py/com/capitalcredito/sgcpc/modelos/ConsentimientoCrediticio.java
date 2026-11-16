package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;

/** Evidencia de la autorización firmada del titular para consultar el buró (Doc. 08 Tabla 08, RN-24). */
public class ConsentimientoCrediticio {

    private Long idConsentimiento;
    private Long idPersona;
    private Long idSolicitud;
    private Long idDocumentoEvidencia;
    private String finalidad;
    private LocalDateTime fechaConsentimiento;
    private Long registradoPor;
    private LocalDateTime fechaRegistro;

    /** No es columna propia: se completa al leer con ConsentimientoCrediticioDAO.obtenerUltimoPara (D-38). */
    private EstadoDocumento estadoDocumentoEvidencia;

    public EstadoDocumento getEstadoDocumentoEvidencia() {
        return estadoDocumentoEvidencia;
    }

    public void setEstadoDocumentoEvidencia(EstadoDocumento estadoDocumentoEvidencia) {
        this.estadoDocumentoEvidencia = estadoDocumentoEvidencia;
    }

    public Long getIdConsentimiento() {
        return idConsentimiento;
    }

    public void setIdConsentimiento(Long idConsentimiento) {
        this.idConsentimiento = idConsentimiento;
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

    public Long getIdDocumentoEvidencia() {
        return idDocumentoEvidencia;
    }

    public void setIdDocumentoEvidencia(Long idDocumentoEvidencia) {
        this.idDocumentoEvidencia = idDocumentoEvidencia;
    }

    public String getFinalidad() {
        return finalidad;
    }

    public void setFinalidad(String finalidad) {
        this.finalidad = finalidad;
    }

    public LocalDateTime getFechaConsentimiento() {
        return fechaConsentimiento;
    }

    public void setFechaConsentimiento(LocalDateTime fechaConsentimiento) {
        this.fechaConsentimiento = fechaConsentimiento;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
