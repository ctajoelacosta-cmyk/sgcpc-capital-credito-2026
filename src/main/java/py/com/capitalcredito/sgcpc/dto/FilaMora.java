package py.com.capitalcredito.sgcpc.dto;

import java.math.BigDecimal;

/**
 * Una fila de /mora (P14, RF-37) y de los reportes Morosidad/Incobrables (Bloque 4, E8). Todo ya
 * viene calculado/formateado desde el servicio -- la JSP no calcula nada (REGLAS_DESARROLLO.md §5).
 */
public class FilaMora {

    private final long idPrestamo;
    private final String identificadorOperacion;
    private final String clienteNombre;
    private final String clienteNombres;
    private final String clienteApellidos;
    private final String clienteTelefono;
    private final String clienteCedula;
    private final int diasAtraso;
    private final int cuotasVencidas;
    private final Integer numeroCuotaMasAntigua;
    private final int totalCuotas;
    private final String fechaVencimientoMasAntigua;
    private final String montoExigible;
    private final BigDecimal montoExigibleCrudo;
    private final boolean tienePromesaAbierta;
    private final String promesaFecha;
    private final String promesaMonto;
    private final boolean promesaVencida;
    private final String ultimaGestionTipo;
    private final String ultimaGestionFecha;
    private final String ultimaGestionResultado;
    private final boolean incobrable;
    private final BigDecimal capitalVencidoCrudo;
    private final BigDecimal saldoCrudo;

    public FilaMora(long idPrestamo, String identificadorOperacion, String clienteNombre, String clienteNombres,
            String clienteApellidos, String clienteTelefono,
            String clienteCedula, int diasAtraso, int cuotasVencidas, Integer numeroCuotaMasAntigua, int totalCuotas,
            String fechaVencimientoMasAntigua, String montoExigible, BigDecimal montoExigibleCrudo,
            boolean tienePromesaAbierta, String promesaFecha, String promesaMonto, boolean promesaVencida,
            String ultimaGestionTipo, String ultimaGestionFecha, String ultimaGestionResultado, boolean incobrable,
            BigDecimal capitalVencidoCrudo, BigDecimal saldoCrudo) {
        this.idPrestamo = idPrestamo;
        this.identificadorOperacion = identificadorOperacion;
        this.clienteNombre = clienteNombre;
        this.clienteNombres = clienteNombres;
        this.clienteApellidos = clienteApellidos;
        this.clienteTelefono = clienteTelefono;
        this.clienteCedula = clienteCedula;
        this.diasAtraso = diasAtraso;
        this.cuotasVencidas = cuotasVencidas;
        this.numeroCuotaMasAntigua = numeroCuotaMasAntigua;
        this.totalCuotas = totalCuotas;
        this.fechaVencimientoMasAntigua = fechaVencimientoMasAntigua;
        this.montoExigible = montoExigible;
        this.montoExigibleCrudo = montoExigibleCrudo;
        this.tienePromesaAbierta = tienePromesaAbierta;
        this.promesaFecha = promesaFecha;
        this.promesaMonto = promesaMonto;
        this.promesaVencida = promesaVencida;
        this.ultimaGestionTipo = ultimaGestionTipo;
        this.ultimaGestionFecha = ultimaGestionFecha;
        this.ultimaGestionResultado = ultimaGestionResultado;
        this.incobrable = incobrable;
        this.capitalVencidoCrudo = capitalVencidoCrudo;
        this.saldoCrudo = saldoCrudo;
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

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): nombres y
     * apellidos por separado, solo para que el buscador de /mora pueda comparar "nombre apellido"
     * Y "apellido nombre" (mismo criterio que PersonaDAO.construirWhere, paso 0) -- clienteNombre
     * sigue siendo el único que usa la columna de la tabla, siempre en ese orden fijo. */
    public String getClienteNombres() {
        return clienteNombres;
    }

    public String getClienteApellidos() {
        return clienteApellidos;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    /** D-146 (lista de trabajo de /cobranza, pedido de Joel): columna "C.I.". */
    public String getClienteCedula() {
        return clienteCedula;
    }

    public int getDiasAtraso() {
        return diasAtraso;
    }

    public int getCuotasVencidas() {
        return cuotasVencidas;
    }

    /** D-146 (lista de trabajo de /cobranza, pedido de Joel): la cuota más antigua sin pagar,
     * para la columna "Cuota (N/M)" -- nunca null si cuotasVencidas &gt; 0 (siempre lo es acá,
     * esta fila solo existe para préstamos EN_MORA). */
    public Integer getNumeroCuotaMasAntigua() {
        return numeroCuotaMasAntigua;
    }

    public int getTotalCuotas() {
        return totalCuotas;
    }

    /** D-146: fecha de vencimiento de esa misma cuota más antigua, ya formateada, para la
     * columna "Vencimiento" de la lista de trabajo unificada. */
    public String getFechaVencimientoMasAntigua() {
        return fechaVencimientoMasAntigua;
    }

    public String getMontoExigible() {
        return montoExigible;
    }

    /** Bloque 4 (E8, reporte Morosidad): valor sin formatear, solo para sumar subtotales por
     * tramo en el servicio -- la JSP nunca lo toca directo (sigue mostrando montoExigible). */
    public BigDecimal getMontoExigibleCrudo() {
        return montoExigibleCrudo;
    }

    public boolean isTienePromesaAbierta() {
        return tienePromesaAbierta;
    }

    public String getPromesaFecha() {
        return promesaFecha;
    }

    public String getPromesaMonto() {
        return promesaMonto;
    }

    public boolean isPromesaVencida() {
        return promesaVencida;
    }

    public String getUltimaGestionTipo() {
        return ultimaGestionTipo;
    }

    public String getUltimaGestionFecha() {
        return ultimaGestionFecha;
    }

    /** Punto 1 de la mejora chica de RF24 (pedido de Joel): el resultado de la última gestión,
     * junto al tipo/fecha en la columna "Última gestión" -- antes había que abrir el estado de
     * cuenta del cliente para verlo. Null si la gestión no tiene resultado cargado. */
    public String getUltimaGestionResultado() {
        return ultimaGestionResultado;
    }

    /** Punto 6 (pedido de Joel): marca visible en /mora, mismo criterio que ServicioMora.listarIncobrables
     * (credito.dias_incobrable) -- antes solo se veía en /reportes/incobrables. */
    public boolean isIncobrable() {
        return incobrable;
    }

    /** Tramo de atraso, Doc. 00 §13 ("Morosidad"): 1–30 / 31–60 / 61–90 / 91–180 / más de 180. */
    public String getTramo() {
        if (diasAtraso <= 30) {
            return "1–30";
        } else if (diasAtraso <= 60) {
            return "31–60";
        } else if (diasAtraso <= 90) {
            return "61–90";
        } else if (diasAtraso <= 180) {
            return "91–180";
        }
        return "Más de 180";
    }

    /** Bloque C, tanda 1 (pedido de Joel): solo la porción de CAPITAL de las cuotas ya vencidas
     * (suma de DetalleCuotaExigible.getCapitalPendiente() de ServicioCobranza.calcularDeudaExigible)
     * -- distinto de montoExigibleCrudo, que también incluye interés/moratorio/punitorio/IVA. Para
     * los subtotales por tramo del informe de Morosidad. */
    public BigDecimal getCapitalVencidoCrudo() {
        return capitalVencidoCrudo;
    }

    /** Bloque C, tanda 1 (pedido de Joel): saldo de capital de TODO el préstamo (cuotas vencidas y
     * por vencer), con el mismo cálculo que el listado de Préstamos y Estado de cuenta
     * (ServicioReportes.sumarSaldoCapital) -- para que el total de Morosidad coincida con el saldo
     * "En mora" de /prestamos. */
    public BigDecimal getSaldoCrudo() {
        return saldoCrudo;
    }
}
