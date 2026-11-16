package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDateTime;

/**
 * Normalización de personas, grupo Baja (16/10/2026): reemplaza a las columnas
 * motivo_baja/detalle_baja/fecha_baja/dado_de_baja_por de personas (ver
 * docs/base_de_datos/02_analisis_y_problemas.md). 1-1 con personas, pero a diferencia de
 * PersonaConyuge esta fila NUNCA se borra al reactivar -- conserva el motivo/detalle/fecha/
 * usuario de la ÚLTIMA baja como historial (ServicioClientes.reactivar no la toca).
 */
public class PersonaBaja {

    private Long idPersona;
    private String motivoBaja;
    private String detalleBaja;
    private LocalDateTime fechaBaja;
    private Long dadoDeBajaPor;

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getMotivoBaja() {
        return motivoBaja;
    }

    public void setMotivoBaja(String motivoBaja) {
        this.motivoBaja = motivoBaja;
    }

    public String getDetalleBaja() {
        return detalleBaja;
    }

    public void setDetalleBaja(String detalleBaja) {
        this.detalleBaja = detalleBaja;
    }

    public LocalDateTime getFechaBaja() {
        return fechaBaja;
    }

    public void setFechaBaja(LocalDateTime fechaBaja) {
        this.fechaBaja = fechaBaja;
    }

    public Long getDadoDeBajaPor() {
        return dadoDeBajaPor;
    }

    public void setDadoDeBajaPor(Long dadoDeBajaPor) {
        this.dadoDeBajaPor = dadoDeBajaPor;
    }
}
