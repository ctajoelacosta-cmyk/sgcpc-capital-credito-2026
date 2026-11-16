package py.com.capitalcredito.sgcpc.dto;

import java.util.List;

/**
 * El reporte "Resumen financiero" (Bloque 7/E10, Tanda D, D-128) -- igual que ResumenMensual pero
 * para un rango desde-hasta arbitrario (no solo un mes calendario), con créditos en trámite,
 * cartera y demanda por tramo de monto/plazo agregados. No es una lista de filas, es un solo
 * resumen. "Ganancia" es interés + moratorio + punitorio, SIN IVA (el IVA se retiene para el fisco).
 */
public class ResumenFinanciero {

    private int otorgadosCantidad;
    private String otorgadosMonto;
    private int enTramiteCantidad;
    private String enTramiteMonto;
    private int aprobadosSinDesembolsarCantidad;
    private String aprobadosSinDesembolsarMonto;
    private String cobradoCapital;
    private String cobradoInteres;
    private String cobradoMoratorio;
    private String cobradoPunitorio;
    private String cobradoIva;
    private String cobradoTotal;
    private String gananciaDelPeriodo;
    private String carteraCapitalPendiente;
    private String carteraInteresFuturo;
    private String morosidadPorcentajeCantidad;
    private String morosidadPorcentajeMonto;
    private String saldoPrestamosEnMoraPorcentaje;
    private List<SubtotalTramo> demandaPorMonto;
    private List<SubtotalTramo> demandaPorPlazo;

    public int getOtorgadosCantidad() {
        return otorgadosCantidad;
    }

    public void setOtorgadosCantidad(int otorgadosCantidad) {
        this.otorgadosCantidad = otorgadosCantidad;
    }

    public String getOtorgadosMonto() {
        return otorgadosMonto;
    }

    public void setOtorgadosMonto(String otorgadosMonto) {
        this.otorgadosMonto = otorgadosMonto;
    }

    public int getEnTramiteCantidad() {
        return enTramiteCantidad;
    }

    public void setEnTramiteCantidad(int enTramiteCantidad) {
        this.enTramiteCantidad = enTramiteCantidad;
    }

    public String getEnTramiteMonto() {
        return enTramiteMonto;
    }

    public void setEnTramiteMonto(String enTramiteMonto) {
        this.enTramiteMonto = enTramiteMonto;
    }

    public int getAprobadosSinDesembolsarCantidad() {
        return aprobadosSinDesembolsarCantidad;
    }

    public void setAprobadosSinDesembolsarCantidad(int aprobadosSinDesembolsarCantidad) {
        this.aprobadosSinDesembolsarCantidad = aprobadosSinDesembolsarCantidad;
    }

    public String getAprobadosSinDesembolsarMonto() {
        return aprobadosSinDesembolsarMonto;
    }

    public void setAprobadosSinDesembolsarMonto(String aprobadosSinDesembolsarMonto) {
        this.aprobadosSinDesembolsarMonto = aprobadosSinDesembolsarMonto;
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

    public String getGananciaDelPeriodo() {
        return gananciaDelPeriodo;
    }

    public void setGananciaDelPeriodo(String gananciaDelPeriodo) {
        this.gananciaDelPeriodo = gananciaDelPeriodo;
    }

    public String getCarteraCapitalPendiente() {
        return carteraCapitalPendiente;
    }

    public void setCarteraCapitalPendiente(String carteraCapitalPendiente) {
        this.carteraCapitalPendiente = carteraCapitalPendiente;
    }

    public String getCarteraInteresFuturo() {
        return carteraInteresFuturo;
    }

    public void setCarteraInteresFuturo(String carteraInteresFuturo) {
        this.carteraInteresFuturo = carteraInteresFuturo;
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
     * cartera (ver ServicioReportes.calcularResumenFinanciero) -- esta es la otra mirada que
     * Joel pidió agregar, aparte: el saldo TOTAL (vencido + no vencido) de los préstamos EN_MORA
     * sobre la cartera. */
    public String getSaldoPrestamosEnMoraPorcentaje() {
        return saldoPrestamosEnMoraPorcentaje;
    }

    public void setSaldoPrestamosEnMoraPorcentaje(String saldoPrestamosEnMoraPorcentaje) {
        this.saldoPrestamosEnMoraPorcentaje = saldoPrestamosEnMoraPorcentaje;
    }

    public List<SubtotalTramo> getDemandaPorMonto() {
        return demandaPorMonto;
    }

    public void setDemandaPorMonto(List<SubtotalTramo> demandaPorMonto) {
        this.demandaPorMonto = demandaPorMonto;
    }

    public List<SubtotalTramo> getDemandaPorPlazo() {
        return demandaPorPlazo;
    }

    public void setDemandaPorPlazo(List<SubtotalTramo> demandaPorPlazo) {
        this.demandaPorPlazo = demandaPorPlazo;
    }
}
