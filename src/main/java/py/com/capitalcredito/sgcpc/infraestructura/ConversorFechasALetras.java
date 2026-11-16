package py.com.capitalcredito.sgcpc.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fecha completa en letras para imprimibles legales (D-70, contrato de préstamo): "veinticinco
 * días del mes de septiembre de dos mil veintiséis". Reutiliza ConversorNumerosALetras para el
 * día y el año -- no duplica esa conversión, solo agrega los nombres de los meses.
 */
public final class ConversorFechasALetras {

    private static final String[] MESES = {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    private ConversorFechasALetras() {
    }

    public static String comoDiaMesAnioEnLetras(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        int diaDelMes = fecha.getDayOfMonth();
        // Convención de los imprimibles legales: "al primer día", no "uno días".
        // B11 (01/11/2026, decisión de Joel): el día va APOCOPADO porque precede a un sustantivo
        // masculino -- "a los veintiún días", no "a los veintiuno días" (ni "treinta y uno días").
        String dia = diaDelMes == 1 ? "primer"
                : ConversorNumerosALetras.comoLetrasApocopado(BigDecimal.valueOf(diaDelMes));
        String mes = MESES[fecha.getMonthValue() - 1];
        String anio = ConversorNumerosALetras.comoLetras(BigDecimal.valueOf(fecha.getYear()));
        return dia + " día" + (diaDelMes == 1 ? "" : "s") + " del mes de " + mes + " de " + anio;
    }

    /**
     * La misma fecha, con la preposición y el artículo que le corresponden: "al primer día del mes
     * de noviembre de dos mil veintiséis", "a los veintiún días del mes de octubre de dos mil
     * veintiséis".
     *
     * <p>B103 (01/11/2026, decisión de Joel): el artículo sale del MISMO dueño que el día. Antes
     * el "a los " era texto fijo de la cláusula del contrato
     * ({@code ServicioFormalizacion.construirClausulasContrato}) y el día 1 es singular, así que un
     * préstamo formalizado un día 1 imprimía "a los primer día del mes de...". Quien escriba la
     * frase no tiene que acordarse de concordar: pide la fecha ya con su artículo.</p>
     */
    public static String comoFechaConArticulo(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return (fecha.getDayOfMonth() == 1 ? "al " : "a los ") + comoDiaMesAnioEnLetras(fecha);
    }
}
