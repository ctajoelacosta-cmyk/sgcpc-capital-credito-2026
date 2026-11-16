package py.com.capitalcredito.sgcpc.dto;

/**
 * Una fila (un horizonte: 30/60/90 días) del reporte "Proyección de fondos" (Bloque 7/E10, Tanda
 * D, D-128). "Egresos comprometidos" es el monto ya aprobado y todavía sin desembolsar (solicitudes
 * APROBADA sin formalizar + préstamos PENDIENTE_DESEMBOLSO) -- no tiene una fecha programada propia
 * (RF no la exige), así que se muestra igual en los tres horizontes como "lo que ya está
 * comprometido a hoy, en cualquier momento". Todo ya formateado.
 * Bloque 7/E10 (D-129, Obs. 30): "posibles egresos" es distinto -- solicitudes todavía
 * PENDIENTE_APROBACION (en trámite, ni siquiera decididas todavía), una columna aparte, solo
 * informativa: NO se resta del flujo neto (a diferencia de los egresos comprometidos, que sí).
 */
public class FilaProyeccionFondos {

    private final int horizonteDias;
    private final String ingresosEsperados;
    private final String egresosComprometidos;
    private final String posiblesEgresosEnTramite;
    private final String flujoNeto;
    private final boolean flujoNetoNegativo;

    public FilaProyeccionFondos(int horizonteDias, String ingresosEsperados, String egresosComprometidos,
            String posiblesEgresosEnTramite, String flujoNeto, boolean flujoNetoNegativo) {
        this.horizonteDias = horizonteDias;
        this.ingresosEsperados = ingresosEsperados;
        this.egresosComprometidos = egresosComprometidos;
        this.posiblesEgresosEnTramite = posiblesEgresosEnTramite;
        this.flujoNeto = flujoNeto;
        this.flujoNetoNegativo = flujoNetoNegativo;
    }

    public int getHorizonteDias() {
        return horizonteDias;
    }

    public String getIngresosEsperados() {
        return ingresosEsperados;
    }

    public String getEgresosComprometidos() {
        return egresosComprometidos;
    }

    public String getPosiblesEgresosEnTramite() {
        return posiblesEgresosEnTramite;
    }

    public String getFlujoNeto() {
        return flujoNeto;
    }

    public boolean isFlujoNetoNegativo() {
        return flujoNetoNegativo;
    }
}
