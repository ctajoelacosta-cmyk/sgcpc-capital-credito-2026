package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * B1 (Bloque 7/E10, pedido de Joel): una fila del listado de Préstamos (/prestamos) -- la MISMA
 * fila alimenta la tabla en pantalla, el informe PDF (vía JRBeanCollectionDataSource) y el CSV.
 * Monto y saldo quedan en BigDecimal (sin formatear) para poder sumarlos en los totales y para
 * que el CSV los exporte como número (Joel: "para que se puedan sumar en Excel"); los getters
 * *Formateado son los mismos valores ya en guaraníes ("1.234.567 Gs."), para la pantalla y el
 * .jrxml, que nunca calculan nada (REGLAS_DESARROLLO.md, sección 6).
 * <p>
 * E-9 (pedido de Joel): saldo es null para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_DESEMBOLSO -- sin
 * desembolso no hay deuda, así que "no aplica" (nunca 0, que significaría "ya cancelado"); en
 * ese caso saldoFormateado y cuotasTexto ya vienen resueltos en "—" desde ServicioReportes, y el
 * saldo (null) queda afuera de cualquier suma (ServicioReportes.calcularTotalesPrestamos/
 * calcularResumenPorEstadoPrestamos). montoAprobado, en cambio, SIEMPRE tiene valor: el monto
 * total sigue sumando todo (pedido explícito de Joel).
 */
public class FilaInformePrestamo {

    private final long idPrestamo;
    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String clienteCedula;
    private final BigDecimal montoAprobado;
    private final String montoAprobadoFormateado;
    private final int plazoMeses;
    private final String fechaDesembolso;
    private final String estado;
    private final BigDecimal saldo;
    private final String saldoFormateado;
    private final String cuotasTexto;

    public FilaInformePrestamo(long idPrestamo, String identificadorOperacion, String clienteNombre,
            String clienteCedula, BigDecimal montoAprobado, String montoAprobadoFormateado, int plazoMeses,
            String fechaDesembolso, String estado, BigDecimal saldo, String saldoFormateado, String cuotasTexto) {
        this.idPrestamo = idPrestamo;
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.clienteCedula = clienteCedula;
        this.montoAprobado = montoAprobado;
        this.montoAprobadoFormateado = montoAprobadoFormateado;
        this.plazoMeses = plazoMeses;
        this.fechaDesembolso = fechaDesembolso;
        this.estado = estado;
        this.saldo = saldo;
        this.saldoFormateado = saldoFormateado;
        this.cuotasTexto = cuotasTexto;
    }

    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteCedula() {
        return clienteCedula;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public String getMontoAprobadoFormateado() {
        return montoAprobadoFormateado;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    /** "—" cuando el préstamo todavía no se desembolsó (sin fecha_desembolso). */
    public String getFechaDesembolso() {
        return fechaDesembolso;
    }

    public String getEstado() {
        return estado;
    }

    /** null (no "no aplica", no 0) para PENDIENTE_DESEMBOLSO/ANULADO_ANTES_DESEMBOLSO -- E-9. */
    public BigDecimal getSaldo() {
        return saldo;
    }

    /** "—" cuando getSaldo() es null; si no, el mismo valor en guaraníes. */
    public String getSaldoFormateado() {
        return saldoFormateado;
    }

    /** "N/M" (cuotas pagadas/total), o "—" cuando getSaldo() es null -- E-9. */
    public String getCuotasTexto() {
        return cuotasTexto;
    }
}
