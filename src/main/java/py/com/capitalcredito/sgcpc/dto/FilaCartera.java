package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * Una fila del reporte Cartera (§13, Bloque 4, E8). "Interés futuro programado" -- rótulo pedido
 * por Joel -- es lo programado en las cuotas todavía pendientes, NUNCA exigible hoy (Ley
 * 6624/2020: sin intereses futuros en una cancelación anticipada); no confundir con el interés ya
 * vencido, que sale de ServicioCobranza.calcularDeudaExigible en el reporte de Morosidad.
 * Bloque C, tanda 1 (pedido de Joel): capitalPendienteCrudo/interesFuturoCrudo (sin formatear) se
 * suman en ServicioReportes.calcularTotalesCartera/calcularResumenPorEstadoCartera -- pura, sobre
 * las filas ya armadas, mismo criterio que FilaInformePrestamo/FilaMora (nunca se vuelve a
 * consultar la base para los totales).
 */
public class FilaCartera {

    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String montoAprobado;
    private final String capitalPendiente;
    private final BigDecimal capitalPendienteCrudo;
    private final String interesFuturoProgramado;
    private final BigDecimal interesFuturoCrudo;
    private final String estado;

    public FilaCartera(String identificadorOperacion, String clienteNombre, String montoAprobado,
            String capitalPendiente, BigDecimal capitalPendienteCrudo, String interesFuturoProgramado,
            BigDecimal interesFuturoCrudo, String estado) {
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.montoAprobado = montoAprobado;
        this.capitalPendiente = capitalPendiente;
        this.capitalPendienteCrudo = capitalPendienteCrudo;
        this.interesFuturoProgramado = interesFuturoProgramado;
        this.interesFuturoCrudo = interesFuturoCrudo;
        this.estado = estado;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getMontoAprobado() {
        return montoAprobado;
    }

    public String getCapitalPendiente() {
        return capitalPendiente;
    }

    /** Bloque C, tanda 1: el "saldo" que pide Joel para el total y el resumen al día/en mora --
     * mismo cálculo que el listado de Préstamos (ServicioReportes.sumarSaldoCapital). */
    public BigDecimal getCapitalPendienteCrudo() {
        return capitalPendienteCrudo;
    }

    public String getInteresFuturoProgramado() {
        return interesFuturoProgramado;
    }

    public BigDecimal getInteresFuturoCrudo() {
        return interesFuturoCrudo;
    }

    public String getEstado() {
        return estado;
    }
}
