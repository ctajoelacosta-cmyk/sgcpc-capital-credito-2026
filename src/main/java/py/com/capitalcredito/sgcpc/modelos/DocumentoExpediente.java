package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;

/**
 * Vincula un archivo_binario con su contexto de negocio (Doc. 08 Tabla 07).
 * En E3 solo se usa con idPersona (idSolicitud/idPrestamo son de etapas
 * siguientes). Versionado: al cargar una nueva versión, la anterior queda
 * REEMPLAZADO y idDocumentoReemplazado apunta a ella.
 */
public class DocumentoExpediente {

    private Long idDocumento;
    private Long idArchivo;
    private Long idTipoDocumento;
    private Long idPersona;
    private Long idSolicitud;
    private Long idPrestamo;
    private int versionDocumento;
    private EstadoDocumento estadoDocumento;
    private Long idDocumentoReemplazado;
    private LocalDate fechaDocumento;
    private LocalDateTime fechaCierre;
    private Long cerradoPor;
    private String observacion;

    public Long getIdDocumento() {
        return idDocumento;
    }

    public void setIdDocumento(Long idDocumento) {
        this.idDocumento = idDocumento;
    }

    public Long getIdArchivo() {
        return idArchivo;
    }

    public void setIdArchivo(Long idArchivo) {
        this.idArchivo = idArchivo;
    }

    public Long getIdTipoDocumento() {
        return idTipoDocumento;
    }

    public void setIdTipoDocumento(Long idTipoDocumento) {
        this.idTipoDocumento = idTipoDocumento;
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

    public Long getIdPrestamo() {
        return idPrestamo;
    }

    public void setIdPrestamo(Long idPrestamo) {
        this.idPrestamo = idPrestamo;
    }

    public int getVersionDocumento() {
        return versionDocumento;
    }

    public void setVersionDocumento(int versionDocumento) {
        this.versionDocumento = versionDocumento;
    }

    public EstadoDocumento getEstadoDocumento() {
        return estadoDocumento;
    }

    public void setEstadoDocumento(EstadoDocumento estadoDocumento) {
        this.estadoDocumento = estadoDocumento;
    }

    public Long getIdDocumentoReemplazado() {
        return idDocumentoReemplazado;
    }

    public void setIdDocumentoReemplazado(Long idDocumentoReemplazado) {
        this.idDocumentoReemplazado = idDocumentoReemplazado;
    }

    public LocalDate getFechaDocumento() {
        return fechaDocumento;
    }

    public void setFechaDocumento(LocalDate fechaDocumento) {
        this.fechaDocumento = fechaDocumento;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public Long getCerradoPor() {
        return cerradoPor;
    }

    public void setCerradoPor(Long cerradoPor) {
        this.cerradoPor = cerradoPor;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    /** Datos del tipo de documento y del archivo, para mostrar en la tabla del expediente sin otra consulta aparte. */
    private TipoDocumento tipoDocumento;
    private ArchivoBinario archivoBinario;

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public ArchivoBinario getArchivoBinario() {
        return archivoBinario;
    }

    public void setArchivoBinario(ArchivoBinario archivoBinario) {
        this.archivoBinario = archivoBinario;
    }
}
