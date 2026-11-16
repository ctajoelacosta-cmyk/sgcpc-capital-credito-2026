package py.com.capitalcredito.sgcpc.dto;

import java.util.Collections;
import java.util.List;

/**
 * Los indicadores de /panel (P02, RF-43/PO-15, Bloque 4 de E8), ya formateados desde
 * ServicioPanel -- la JSP solo decide cuáles mostrar según el perfil (REGLAS_DESARROLLO.md §5). Un campo en
 * null significa "no aplica a este perfil" o "no corresponde ahora" (ej. sin caja abierta).
 */
public class IndicadoresPanel {

    private int cantidadCobrosHoy;
    private String montoCobrosHoy;
    /** B99 (30/10/2026, hallazgo de Joel): cuotas que VENCEN HOY y su total -- el trabajo del dia
     * de la cajera, que el panel no mostraba en ninguna tarjeta. Mismo dato que la lista de
     * /cobranza (ServicioReportes.listarVencimientos + ServicioCobranza.armarListaTrabajo). */
    private int vencenHoyCantidad;
    private String vencenHoyMonto;
    /**
     * B100 (01/11/2026): qué falta para que los préstamos pendientes se puedan desembolsar
     * ("falta cargar: Contrato firmado, Pagaré firmado"). Cadena vacía si no falta nada que
     * cargar. Lo arma ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar.
     */
    private String faltaParaDesembolsar = "";
    private int prestamosEnMora;
    private int promesasDeHoy;
    private int desembolsosPendientes;
    private int desembolsosListos;
    private String totalAProveer;
    private String efectivoEnCaja;
    private boolean hayCajaAbierta;
    private String carteraEnLaCalle;
    private String cobradoSemana;
    private String cobradoMes;
    private int solicitudesPendientesAprobar;
    private int misSolicitudesEnCurso;
    private int reversionesPorAutorizar;
    private int aprobadasParaFormalizar;
    private int diferenciasDeCajaSinRevisar;
    private int cierresDocumentalesPendientes;
    private List<String> avisosTimbrado = Collections.emptyList();

    public int getCantidadCobrosHoy() {
        return cantidadCobrosHoy;
    }

    public void setCantidadCobrosHoy(int cantidadCobrosHoy) {
        this.cantidadCobrosHoy = cantidadCobrosHoy;
    }

    public String getMontoCobrosHoy() {
        return montoCobrosHoy;
    }

    public void setMontoCobrosHoy(String montoCobrosHoy) {
        this.montoCobrosHoy = montoCobrosHoy;
    }

    /** B99: cuántas cuotas vencen hoy (sin contar las de préstamos ya en mora). */
    public int getVencenHoyCantidad() {
        return vencenHoyCantidad;
    }

    public void setVencenHoyCantidad(int vencenHoyCantidad) {
        this.vencenHoyCantidad = vencenHoyCantidad;
    }

    /** B99: el total de esas cuotas, ya formateado. */
    public String getVencenHoyMonto() {
        return vencenHoyMonto;
    }

    public String getFaltaParaDesembolsar() {
        return faltaParaDesembolsar;
    }

    public void setFaltaParaDesembolsar(String faltaParaDesembolsar) {
        this.faltaParaDesembolsar = faltaParaDesembolsar;
    }

    public void setVencenHoyMonto(String vencenHoyMonto) {
        this.vencenHoyMonto = vencenHoyMonto;
    }

    public int getPrestamosEnMora() {
        return prestamosEnMora;
    }

    public void setPrestamosEnMora(int prestamosEnMora) {
        this.prestamosEnMora = prestamosEnMora;
    }

    public int getPromesasDeHoy() {
        return promesasDeHoy;
    }

    public void setPromesasDeHoy(int promesasDeHoy) {
        this.promesasDeHoy = promesasDeHoy;
    }

    public int getDesembolsosPendientes() {
        return desembolsosPendientes;
    }

    public void setDesembolsosPendientes(int desembolsosPendientes) {
        this.desembolsosPendientes = desembolsosPendientes;
    }

    /** Bloque 4 (E8, corrección de Joel): cuántos de los pendientes tienen el checklist completo
     * (lo que realmente puede desembolsarse hoy) -- distinto de "pendientes" en general, para no
     * confundir "2 pendientes" con "hay que proveer para los 2". */
    public int getDesembolsosListos() {
        return desembolsosListos;
    }

    public void setDesembolsosListos(int desembolsosListos) {
        this.desembolsosListos = desembolsosListos;
    }

    public String getTotalAProveer() {
        return totalAProveer;
    }

    public void setTotalAProveer(String totalAProveer) {
        this.totalAProveer = totalAProveer;
    }

    public String getEfectivoEnCaja() {
        return efectivoEnCaja;
    }

    public void setEfectivoEnCaja(String efectivoEnCaja) {
        this.efectivoEnCaja = efectivoEnCaja;
    }

    public boolean isHayCajaAbierta() {
        return hayCajaAbierta;
    }

    public void setHayCajaAbierta(boolean hayCajaAbierta) {
        this.hayCajaAbierta = hayCajaAbierta;
    }

    public String getCarteraEnLaCalle() {
        return carteraEnLaCalle;
    }

    public void setCarteraEnLaCalle(String carteraEnLaCalle) {
        this.carteraEnLaCalle = carteraEnLaCalle;
    }

    public String getCobradoSemana() {
        return cobradoSemana;
    }

    public void setCobradoSemana(String cobradoSemana) {
        this.cobradoSemana = cobradoSemana;
    }

    public String getCobradoMes() {
        return cobradoMes;
    }

    public void setCobradoMes(String cobradoMes) {
        this.cobradoMes = cobradoMes;
    }

    public int getSolicitudesPendientesAprobar() {
        return solicitudesPendientesAprobar;
    }

    public void setSolicitudesPendientesAprobar(int solicitudesPendientesAprobar) {
        this.solicitudesPendientesAprobar = solicitudesPendientesAprobar;
    }

    public int getMisSolicitudesEnCurso() {
        return misSolicitudesEnCurso;
    }

    public void setMisSolicitudesEnCurso(int misSolicitudesEnCurso) {
        this.misSolicitudesEnCurso = misSolicitudesEnCurso;
    }

    /** E9/Bloque 5 (PO-13): solicitudes SOLICITADA esperando que el gerente autorice o rechace. */
    public int getReversionesPorAutorizar() {
        return reversionesPorAutorizar;
    }

    public void setReversionesPorAutorizar(int reversionesPorAutorizar) {
        this.reversionesPorAutorizar = reversionesPorAutorizar;
    }

    /** B45 (FASE2_PLAN.md, confirmado en pantalla el 23/10/2026): solicitudes APROBADA todavía
     * sin formalizar -- el trabajo que le queda al oficial después de que el gerente aprueba,
     * que antes no se le avisaba en ninguna parte del panel. Solo del perfil OFICIAL_CREDITO
     * (decisión de Joel, 28/10/2026). */
    public int getAprobadasParaFormalizar() {
        return aprobadasParaFormalizar;
    }

    public void setAprobadasParaFormalizar(int aprobadasParaFormalizar) {
        this.aprobadasParaFormalizar = aprobadasParaFormalizar;
    }

    /** B63 (FASE2_PLAN.md, confirmado en pantalla el 23/10/2026): cajas CERRADAS con diferencia
     * de arqueo que ningún gerente revisó todavía (RN-23) -- mismo dato de la tarjeta
     * "Diferencias de arqueo sin revisar" de /caja, que hasta ahora el gerente solo encontraba
     * si entraba a esa pantalla por su cuenta. */
    public int getDiferenciasDeCajaSinRevisar() {
        return diferenciasDeCajaSinRevisar;
    }

    public void setDiferenciasDeCajaSinRevisar(int diferenciasDeCajaSinRevisar) {
        this.diferenciasDeCajaSinRevisar = diferenciasDeCajaSinRevisar;
    }

    /** B76 (FASE2_PLAN.md, confirmado en pantalla el 25/10/2026): préstamos FINALIZADO cuyo
     * cierre documental (devolución del pagaré, RF29) todavía no está COMPLETO. Solo del perfil
     * OFICIAL_CREDITO (decisión de Joel, 28/10/2026). */
    public int getCierresDocumentalesPendientes() {
        return cierresDocumentalesPendientes;
    }

    public void setCierresDocumentalesPendientes(int cierresDocumentalesPendientes) {
        this.cierresDocumentalesPendientes = cierresDocumentalesPendientes;
    }

    /** Bloque 6 (PA-13): 0, 1 o 2 avisos (números de factura por agotarse, timbrado por vencer) --
     * ver ServicioCobranza.obtenerAvisosTimbrado. Nunca bloquea nada, solo informa. */
    public List<String> getAvisosTimbrado() {
        return avisosTimbrado;
    }

    public void setAvisosTimbrado(List<String> avisosTimbrado) {
        this.avisosTimbrado = avisosTimbrado;
    }
}
