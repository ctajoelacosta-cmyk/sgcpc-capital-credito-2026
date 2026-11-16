package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDateTime;

/** Una fila cruda del informe "Consultas al buró" (C-4.4, pedido de Joel), tal como la arma
 * ConsultaCrediticiaDAO.listarParaInforme -- todavía sin formatear para pantalla/PDF/CSV
 * (ServicioReportes.construirFilasConsultasBuro hace esa parte, pura). Solo lectura: no cambia
 * ninguna consulta ni consentimiento. */
public class FilaConsultaBuroCrudo {

    private final LocalDateTime fechaHoraConsulta;
    private final long idPersona;
    private final String nombreCompletoPersona;
    private final String cedulaPersona;
    private final String codigoSolicitud;
    private final String clasificacionObtenida;
    private final String nombreCompletoUsuario;

    /** null si no hay un valor de "buro.dias_vigencia_consentimiento" vigente para la fecha de
     * esta consulta (ver ServicioReportes.construirFilasConsultasBuro: "(sin dato)" en ese caso). */
    private final Boolean autorizacionVigenteEnEseMomento;

    public FilaConsultaBuroCrudo(LocalDateTime fechaHoraConsulta, long idPersona, String nombreCompletoPersona,
            String cedulaPersona, String codigoSolicitud, String clasificacionObtenida, String nombreCompletoUsuario,
            Boolean autorizacionVigenteEnEseMomento) {
        this.fechaHoraConsulta = fechaHoraConsulta;
        this.idPersona = idPersona;
        this.nombreCompletoPersona = nombreCompletoPersona;
        this.cedulaPersona = cedulaPersona;
        this.codigoSolicitud = codigoSolicitud;
        this.clasificacionObtenida = clasificacionObtenida;
        this.nombreCompletoUsuario = nombreCompletoUsuario;
        this.autorizacionVigenteEnEseMomento = autorizacionVigenteEnEseMomento;
    }

    public LocalDateTime getFechaHoraConsulta() {
        return fechaHoraConsulta;
    }

    public long getIdPersona() {
        return idPersona;
    }

    public String getNombreCompletoPersona() {
        return nombreCompletoPersona;
    }

    public String getCedulaPersona() {
        return cedulaPersona;
    }

    /** null si la consulta todavía no está vinculada a ninguna solicitud. */
    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    /** null o vacío si no se cargó clasificación. */
    public String getClasificacionObtenida() {
        return clasificacionObtenida;
    }

    public String getNombreCompletoUsuario() {
        return nombreCompletoUsuario;
    }

    public Boolean getAutorizacionVigenteEnEseMomento() {
        return autorizacionVigenteEnEseMomento;
    }
}
