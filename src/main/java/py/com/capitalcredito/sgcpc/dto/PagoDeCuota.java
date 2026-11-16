package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDateTime;

/** Punto 9 (pedido de Joel): qué pago canceló una cuota, para mostrarlo en el Plan de pagos del
 * estado de cuenta ("pagada el X, recibo Y"). */
public class PagoDeCuota {

    private final long idPago;
    private final String numeroRecibo;
    private final LocalDateTime fechaHoraPago;

    public PagoDeCuota(long idPago, String numeroRecibo, LocalDateTime fechaHoraPago) {
        this.idPago = idPago;
        this.numeroRecibo = numeroRecibo;
        this.fechaHoraPago = fechaHoraPago;
    }

    public long getIdPago() {
        return idPago;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public LocalDateTime getFechaHoraPago() {
        return fechaHoraPago;
    }
}
