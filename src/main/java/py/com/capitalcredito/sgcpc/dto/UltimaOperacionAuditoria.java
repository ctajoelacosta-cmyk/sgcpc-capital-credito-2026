package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDateTime;

/** RF03/RF04 (pedido de Joel): fecha/hora y valor (si tiene) de la última vez que se registró una
 * operación de auditoría para una entidad -- lo usa ServicioConsultaCrediticia para saber cuándo
 * se generó la última autorización de consulta al buró y con qué número, sin tabla nueva (AuditoriaDAO.
 * obtenerUltima). */
public class UltimaOperacionAuditoria {

    private final LocalDateTime fechaHora;
    private final String valor;
    private final Long idUsuario;

    public UltimaOperacionAuditoria(LocalDateTime fechaHora, String valor, Long idUsuario) {
        this.fechaHora = fechaHora;
        this.valor = valor;
        this.idUsuario = idUsuario;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** El "valor" guardado en valor_nuevo (AuditoriaDAO.jsonDeValor), ya extraído -- null si esa
     * operación no guardó ningún valor. */
    public String getValor() {
        return valor;
    }

    /** RF06 (pedido de Joel, 10/10/2026): quién hizo la operación -- null en el caso raro de una
     * fila de auditoría sin cuenta real asociada (ver AuditoriaDAO). */
    public Long getIdUsuario() {
        return idUsuario;
    }
}
