package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del reporte Vencimientos (§13, Bloque 4, E8). Todo ya formateado desde el servicio. */
public class FilaVencimiento {

    private final long idPrestamo;
    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String clienteTelefono;
    private final String clienteCedula;
    private final int numeroCuota;
    private final int totalCuotas;
    private final String fechaVencimiento;
    private final LocalDate fechaExacta;
    private final String monto;
    private final BigDecimal montoExacto;
    private final String estado;

    public FilaVencimiento(long idPrestamo, String identificadorOperacion, String clienteNombre,
            String clienteTelefono, String clienteCedula, int numeroCuota, int totalCuotas, String fechaVencimiento,
            LocalDate fechaExacta, String monto, BigDecimal montoExacto, String estado) {
        this.idPrestamo = idPrestamo;
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.clienteTelefono = clienteTelefono;
        this.clienteCedula = clienteCedula;
        this.numeroCuota = numeroCuota;
        this.totalCuotas = totalCuotas;
        this.fechaVencimiento = fechaVencimiento;
        this.fechaExacta = fechaExacta;
        this.monto = monto;
        this.montoExacto = montoExacto;
        this.estado = estado;
    }

    /** D-146 (lista de trabajo de /cobranza, pedido de Joel): para el botón "Cobrar". */
    public long getIdPrestamo() {
        return idPrestamo;
    }

    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    /** D-146 (lista de trabajo de /cobranza, pedido de Joel): columna "C.I.". */
    public String getClienteCedula() {
        return clienteCedula;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    /** D-146 (lista de trabajo de /cobranza, pedido de Joel): columna "Cuota (N/M)". */
    public int getTotalCuotas() {
        return totalCuotas;
    }

    /**
     * B210, segunda vuelta (15/11/2026, defecto que Joel encontró al probar): la fecha
     * <b>sin formatear</b>, para ordenar.
     *
     * <p>El listado se ordenaba por {@link #getFechaVencimiento()}, que es el texto
     * {@code "dd/MM/yyyy"}: comparado como String eso ordena por <b>día, después mes, después
     * año</b>, así que {@code "01/12/2026"} iba antes que {@code "15/11/2026"}. Con el listado
     * entero en una pantalla el desorden pasaba desapercibido; al paginarlo en B210 las cuotas que
     * vencen hoy dejaron de estar en la página 1, que es exactamente para lo que se abre el
     * reporte.</p>
     *
     * <p>Mismo patrón que {@link #getMontoExacto()}, que B99 agregó al lado del monto formateado
     * por la misma razón: <b>el dato formateado es para mostrar, nunca para calcular ni para
     * ordenar</b>.</p>
     */
    public LocalDate getFechaExacta() {
        return fechaExacta;
    }

    public String getFechaVencimiento() {
        return fechaVencimiento;
    }

    public String getMonto() {
        return monto;
    }

    /**
     * B99 (30/10/2026, hallazgo de Joel): el mismo monto <b>sin formatear</b>, para poder sumarlo.
     * La tarjeta "Vencen hoy" del panel muestra el total del día, y sumar textos ya formateados
     * ("289.011 Gs.") no es una opción. El formateado ({@link #getMonto()}) se sigue usando tal
     * cual en las tablas de la lista y del informe.
     */
    public BigDecimal getMontoExacto() {
        return montoExacto;
    }

    public String getEstado() {
        return estado;
    }
}
