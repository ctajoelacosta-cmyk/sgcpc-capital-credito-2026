package py.com.capitalcredito.sgcpc.dto;

/**
 * El reporte "Resumen mensual" (§13, Bloque 4, E8) -- no es una lista de filas, es un solo
 * resumen. "Interés compensatorio cobrado" es el pedido explícito de Joel, basado en la misma
 * composición que ya usa el arqueo (ServicioCaja.generarPdfArqueo), generalizada a todo el mes.
 * "Ganancia" es interés + moratorio + punitorio, SIN IVA (el IVA se retiene para el fisco).
 */
public class ResumenMensual {

    private int prestamosOtorgadosCantidad;
    private String prestamosOtorgadosMonto;
    private String cobradoCapital;
    private String cobradoInteres;
    private String cobradoMoratorio;
    private String cobradoPunitorio;
    private String cobradoIva;
    private String cobradoTotal;
    private String gananciaDelMes;
    private String morosidadPorcentajeCantidad;
    private String morosidadPorcentajeMonto;
    private String saldoPrestamosEnMoraPorcentaje;

    public int getPrestamosOtorgadosCantidad() {
        return prestamosOtorgadosCantidad;
    }

    public void setPrestamosOtorgadosCantidad(int prestamosOtorgadosCantidad) {
        this.prestamosOtorgadosCantidad = prestamosOtorgadosCantidad;
    }

    public String getPrestamosOtorgadosMonto() {
        return prestamosOtorgadosMonto;
    }

    public void setPrestamosOtorgadosMonto(String prestamosOtorgadosMonto) {
        this.prestamosOtorgadosMonto = prestamosOtorgadosMonto;
    }

    public String getCobradoCapital() {
        return cobradoCapital;
    }

    public void setCobradoCapital(String cobradoCapital) {
        this.cobradoCapital = cobradoCapital;
    }

    public String getCobradoInteres() {
        return cobradoInteres;
    }

    public void setCobradoInteres(String cobradoInteres) {
        this.cobradoInteres = cobradoInteres;
    }

    public String getCobradoMoratorio() {
        return cobradoMoratorio;
    }

    public void setCobradoMoratorio(String cobradoMoratorio) {
        this.cobradoMoratorio = cobradoMoratorio;
    }

    public String getCobradoPunitorio() {
        return cobradoPunitorio;
    }

    public void setCobradoPunitorio(String cobradoPunitorio) {
        this.cobradoPunitorio = cobradoPunitorio;
    }

    public String getCobradoIva() {
        return cobradoIva;
    }

    public void setCobradoIva(String cobradoIva) {
        this.cobradoIva = cobradoIva;
    }

    public String getCobradoTotal() {
        return cobradoTotal;
    }

    public void setCobradoTotal(String cobradoTotal) {
        this.cobradoTotal = cobradoTotal;
    }

    public String getGananciaDelMes() {
        return gananciaDelMes;
    }

    public void setGananciaDelMes(String gananciaDelMes) {
        this.gananciaDelMes = gananciaDelMes;
    }

    public String getMorosidadPorcentajeCantidad() {
        return morosidadPorcentajeCantidad;
    }

    public void setMorosidadPorcentajeCantidad(String morosidadPorcentajeCantidad) {
        this.morosidadPorcentajeCantidad = morosidadPorcentajeCantidad;
    }

    public String getMorosidadPorcentajeMonto() {
        return morosidadPorcentajeMonto;
    }

    public void setMorosidadPorcentajeMonto(String morosidadPorcentajeMonto) {
        this.morosidadPorcentajeMonto = morosidadPorcentajeMonto;
    }

    /** 13/10/2026 (hallazgo de Joel): "morosidadPorcentajeMonto" es capital VENCIDO sobre la
     * cartera (ver ServicioReportes.calcularResumenMensual) -- esta es la otra mirada que Joel
     * pidió agregar, aparte: el saldo TOTAL (vencido + no vencido) de los préstamos EN_MORA sobre
     * la cartera. */
    public String getSaldoPrestamosEnMoraPorcentaje() {
        return saldoPrestamosEnMoraPorcentaje;
    }

    public void setSaldoPrestamosEnMoraPorcentaje(String saldoPrestamosEnMoraPorcentaje) {
        this.saldoPrestamosEnMoraPorcentaje = saldoPrestamosEnMoraPorcentaje;
    }
}
