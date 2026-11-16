package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;

/**
 * Metadatos de un archivo guardado en PostgreSQL (Doc. 08 Tabla 05,
 * REGLAS_DESARROLLO.md §4: nunca en disco ni en el .war). El contenido (byte[]) no es
 * un campo de esta clase a propósito: los listados de documentos no cargan
 * el binario completo, solo se pide aparte al descargar
 * (ArchivoBinarioDAO.obtenerContenido).
 */
public class ArchivoBinario {

    private Long idArchivo;
    private String nombreArchivo;
    private String mimeType;
    private String extension;
    private long tamanoBytes;
    private String hashSha256;
    private OrigenArchivo origenArchivo;
    private LocalDateTime fechaCarga;
    private Long cargadoPor;

    public Long getIdArchivo() {
        return idArchivo;
    }

    public void setIdArchivo(Long idArchivo) {
        this.idArchivo = idArchivo;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public void setTamanoBytes(long tamanoBytes) {
        this.tamanoBytes = tamanoBytes;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public OrigenArchivo getOrigenArchivo() {
        return origenArchivo;
    }

    public void setOrigenArchivo(OrigenArchivo origenArchivo) {
        this.origenArchivo = origenArchivo;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }

    public void setFechaCarga(LocalDateTime fechaCarga) {
        this.fechaCarga = fechaCarga;
    }

    public Long getCargadoPor() {
        return cargadoPor;
    }

    public void setCargadoPor(Long cargadoPor) {
        this.cargadoPor = cargadoPor;
    }
}
