package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Consultas al buró" (C-4.4, pedido de Joel). Todo ya formateado -- mismo
 * molde que el resto de los informes de este bloque. Solo lectura: refleja lo que ya estaba
 * guardado en consultas_crediticias, nunca cambia nada. */
public class FilaConsultaBuro {

    private final long idPersona;
    private final String fecha;
    private final String cliente;
    private final String cedula;
    private final String solicitud;
    private final String resultado;
    private final String quienConsulto;
    private final String autorizacionVigente;

    public FilaConsultaBuro(long idPersona, String fecha, String cliente, String cedula, String solicitud,
            String resultado, String quienConsulto, String autorizacionVigente) {
        this.idPersona = idPersona;
        this.fecha = fecha;
        this.cliente = cliente;
        this.cedula = cedula;
        this.solicitud = solicitud;
        this.resultado = resultado;
        this.quienConsulto = quienConsulto;
        this.autorizacionVigente = autorizacionVigente;
    }

    /** Para el enlace de la fila a la ficha del cliente (/clientes/{idPersona}). */
    public long getIdPersona() {
        return idPersona;
    }

    public String getFecha() {
        return fecha;
    }

    public String getCliente() {
        return cliente;
    }

    public String getCedula() {
        return cedula;
    }

    /** "—" si la consulta todavía no está vinculada a ninguna solicitud. */
    public String getSolicitud() {
        return solicitud;
    }

    /** "Sin clasificación" si no se cargó clasificación. */
    public String getResultado() {
        return resultado;
    }

    public String getQuienConsulto() {
        return quienConsulto;
    }

    /** "Sí" / "No" / "(sin dato)" -- este último cuando no hay un valor de
     * "buro.dias_vigencia_consentimiento" con vigencia registrada para esa fecha. */
    public String getAutorizacionVigente() {
        return autorizacionVigente;
    }
}
