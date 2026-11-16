package py.com.capitalcredito.sgcpc.infraestructura;

/**
 * RF01-E (pedido de Joel): única fuente de los textos de TEA/TNA/CFT/IVA/Capital/Interés/Cuota más
 * alta/Total a pagar/Afectación al ingreso en todo el sistema (cotizador, solicitud, aprobación;
 * pensada para que cualquier pantalla nueva la reutilice sin duplicar el texto). Cada término
 * tiene un texto corto (siempre visible junto al dato) y uno completo (se muestra recién al hacer
 * clic en el ícono ⓘ). Textos EXACTOS que pasó Joel -- no se vuelven a redactar por cuenta propia,
 * se copian tal cual. Dos correcciones de Joel ya aplicadas, registradas por si vuelve a pasar:
 * (1) la primera versión de TEA usaba "capitalizan/capitalizar", justamente la palabra técnica que
 * se quería evitar, y nunca se avisó el apartamiento antes de implementar; (2) RF06 (10/10/2026):
 * TEA/CFT/Capital/Interés tenían voseo/tuteo ("pagás", "debés", "te cuesta", "devolvés",
 * "prestarte") -- REGLAS_DESARROLLO.md §12 exige usted siempre; corregidos a la forma impersonal o
 * con "usted"/"le", según lo que pasó Joel. Esta clase no calcula ni consulta nada -- los valores
 * dinámicos (tope BCP vigente, tasa mensual, TNA, límite de afectación) los resuelve quien llama y
 * los pasa a completo(...), que los interpola con String.format.
 */
public final class GlosarioFinanciero {

    private GlosarioFinanciero() {
    }

    public static final class Termino {
        private final String sigla;
        private final String nombre;
        private final String corto;
        private final String completoFormato;

        private Termino(String sigla, String nombre, String corto, String completoFormato) {
            this.sigla = sigla;
            this.nombre = nombre;
            this.corto = corto;
            this.completoFormato = completoFormato;
        }

        public String getSigla() {
            return sigla;
        }

        /** "TEA – Tasa Efectiva Anual" (sigla y nombre, pedido de Joel); solo la sigla cuando el
         * término no tiene un nombre expandido aparte (Capital, Interés). */
        public String getTitulo() {
            return nombre == null ? sigla : sigla + " – " + nombre;
        }

        public String getCorto() {
            return corto;
        }

        public String completo(Object... valoresDinamicos) {
            return String.format(completoFormato, valoresDinamicos);
        }
    }

    public static final Termino TEA = new Termino("TEA", "Tasa Efectiva Anual",
            "Interés real por año",
            "Es el interés que realmente se paga en un año, porque el interés se calcula mes a mes "
            + "sobre lo que todavía se debe. Es la tasa que controla la ley: no puede superar el "
            // FormateadorNumeros.comoPorcentaje ya agrega el " %" al valor -- un "%%" más acá
            // duplicaba el signo ("27,39 % %"), corrección de Joel.
            + "tope del BCP (hoy %s).");

    public static final Termino TNA = new Termino("TNA", "Tasa Nominal Anual",
            "Tasa mensual × 12",
            "Es la tasa de cada mes multiplicada por 12. En esta simulación: %s por mes × 12 = "
            + "%s. Es menor que la TEA porque no suma el efecto mes a mes.");

    public static final Termino CFT = new Termino("CFT", "Costo Financiero Total",
            "Costo total por año, con IVA",
            "Es todo lo que cuesta el préstamo en un año: intereses más el IVA. Es el número que "
            + "conviene usar para comparar con otras financieras.");

    public static final Termino IVA = new Termino("IVA", "Impuesto al Valor Agregado",
            "10 % sobre los intereses",
            "Por ley se cobra el 10 %% de IVA sobre los intereses, nunca sobre el capital. Por eso "
            + "la cuota baja un poco cada mes: el interés baja y, con él, el IVA.");

    public static final Termino CAPITAL = new Termino("Capital", null,
            "Lo que se devuelve del préstamo.",
            "Lo que se devuelve del préstamo.");

    public static final Termino INTERES = new Termino("Interés", null,
            "Lo que cobra la financiera por prestarle.",
            "Lo que cobra la financiera por prestarle.");

    /** RF06 (pedido de Joel, 10/10/2026): tres términos nuevos para Solicitud y Aprobación --
     * mismo criterio de los de arriba (texto corto siempre visible, completo al hacer clic en
     * ⓘ), redactados para que los entienda un cliente o una cajera, no solo quien ya sabe
     * finanzas. Mostrados a Joel antes de guardarlos; corrigió el voseo de la primera versión
     * (ver el comentario de la clase) y pasó estos textos definitivos, que se copian tal cual. */
    public static final Termino CUOTA_MAXIMA = new Termino("Cuota más alta", null,
            "La cuota de mayor monto del plan.",
            "Mostramos la cuota más alta de todo el plan de pagos, no siempre la primera: como el "
            + "interés se calcula sobre los días reales de cada mes, las cuotas pueden variar un "
            + "poco entre sí. Así usted sabe que ninguna cuota del préstamo va a ser mayor que ese "
            + "monto.");

    public static final Termino TOTAL_A_PAGAR = new Termino("Total a pagar", null,
            "Suma de todas las cuotas, con IVA.",
            "Es la suma de todas las cuotas del plan de pagos: capital, interés e IVA. Indica "
            + "cuánto se paga en total por el préstamo, del principio al final.");

    /** El %s interpola el límite vigente (credito.limite_afectacion, formateado como
     * porcentaje) -- quien llama lo resuelve, igual que TEA con el tope del BCP. */
    public static final Termino AFECTACION = new Termino("Afectación al ingreso", null,
            "Qué parte del ingreso se va en cuotas.",
            "Es el porcentaje del ingreso mensual del cliente que se "
            + "destina a pagar cuotas: la de este crédito más las de sus otras deudas. Por "
            + "política de la empresa no puede superar el %s, para que siempre le quede margen "
            + "para los demás gastos.");
}
