package py.com.capitalcredito.sgcpc.infraestructura;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatea fechas para mostrar en pantalla en dd/mm/aaaa (Doc. 10 §10.18).
 * Reutilizable para cualquier pantalla de las etapas siguientes.
 */
public final class FormateadorFechas {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CON_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_CORTO_CON_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Bloque 2 (E8, constancia de cancelación): día numérico + mes en letras, para el encabezado
     * estilo carta ("Asunción, 27 de septiembre de 2026") -- distinto de
     * ConversorFechasALetras.comoDiaMesAnioEnLetras, que deletrea TODO (día y año incluidos) para
     * los imprimibles legales de E5 (contrato/pagaré). */
    private static final String[] MESES = {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    /** Reprueba Lote 1 (pedido de Joel, D.1): mismo orden/nombres que NOMBRES_DIA en
     * fecha-primer-vencimiento.js (0=domingo). */
    private static final String[] DIAS = {
        "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"
    };

    private FormateadorFechas() {
    }

    /** "lunes 23/11/2026" -- para mostrar una fecha ya corrida al siguiente día hábil junto con
     * el nombre del día (D.1, reprueba Lote 1: la fecha elegida puede diferir de la efectiva). */
    public static String comoDiaYFecha(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return DIAS[fecha.getDayOfWeek().getValue() % 7] + " " + comoDdMmAaaa(fecha);
    }

    public static String comoDdMmAaaa(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FORMATO);
    }

    /** E4 (ajuste 6, Joel): "quién decidió/canceló y cuándo" con hora, ej. "Aprobada por... el dd/mm/aaaa hh:mm". */
    public static String comoDdMmAaaaHhMm(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : fechaHora.format(FORMATO_CON_HORA);
    }

    /** RF03/RF04 (pedido de Joel): "generada el dd/mm hh:mm" -- sin año, para un dato que siempre
     * se refiere al año en curso (el ✓ de "Generar" de la autorización de consulta al buró). */
    public static String comoDdMmHhMm(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : fechaHora.format(FORMATO_CORTO_CON_HORA);
    }

    /** "27 de septiembre de 2026". */
    public static String comoFechaLarga(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** PA-25 (14/10/2026, hallazgo de Joel, L2-D): "octubre de 2026" -- ServicioInformes
     * concatenaba un java.time.YearMonth directo en el texto de filtros de Resumen mensual/IVA/
     * Facturas anuladas ("Resumen de 2026-10", el toString() por defecto de YearMonth). */
    public static String comoMesYAnio(java.time.YearMonth mes) {
        if (mes == null) {
            return "";
        }
        return MESES[mes.getMonthValue() - 1] + " de " + mes.getYear();
    }

    /** RF07 (pedido de Joel, 10/10/2026): "cuánto hace" -- "45 min" / "3 h" / "2 h 45 min" /
     * "1 día" / "2 días 4 h" según la magnitud (mismo cálculo que
     * ServicioReportes.formatearDuracionMinutos, que delega acá para no duplicarlo). Pura. */
    public static String comoDuracion(long minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        long horas = minutos / 60;
        long minutosResto = minutos % 60;
        if (horas < 24) {
            return minutosResto == 0 ? horas + " h" : horas + " h " + minutosResto + " min";
        }
        long dias = horas / 24;
        long horasResto = horas % 24;
        String textoDias = dias == 1 ? "1 día" : dias + " días";
        return horasResto == 0 ? textoDias : textoDias + " " + horasResto + " h";
    }
}
