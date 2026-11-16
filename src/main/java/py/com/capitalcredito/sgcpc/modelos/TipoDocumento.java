package py.com.capitalcredito.sgcpc.modelos;

/** Catálogo de tipos de documento (Doc. 08 Tabla 06), semilla en bd/03_parametros.sql (D-37). Solo lectura en E3. */
public class TipoDocumento {

    private Long idTipoDocumento;
    private String codigoTipo;
    private String nombreTipo;
    private String contexto;
    private boolean obligatorioDesembolso;
    private boolean activo;

    public Long getIdTipoDocumento() {
        return idTipoDocumento;
    }

    public void setIdTipoDocumento(Long idTipoDocumento) {
        this.idTipoDocumento = idTipoDocumento;
    }

    public String getCodigoTipo() {
        return codigoTipo;
    }

    public void setCodigoTipo(String codigoTipo) {
        this.codigoTipo = codigoTipo;
    }

    public String getNombreTipo() {
        return nombreTipo;
    }

    public void setNombreTipo(String nombreTipo) {
        this.nombreTipo = nombreTipo;
    }

    public String getContexto() {
        return contexto;
    }

    public void setContexto(String contexto) {
        this.contexto = contexto;
    }

    public boolean isObligatorioDesembolso() {
        return obligatorioDesembolso;
    }

    public void setObligatorioDesembolso(boolean obligatorioDesembolso) {
        this.obligatorioDesembolso = obligatorioDesembolso;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
