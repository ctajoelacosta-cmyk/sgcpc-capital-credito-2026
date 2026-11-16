package py.com.capitalcredito.sgcpc.infraestructura;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte un monto en guaraníes a su texto en español (Paraguay), en
 * minúsculas y sin la palabra "guaraníes": esa la agrega
 * {@link #comoLetrasConGuaranies}, que es la que usan las pantallas y los documentos
 * (el recibo y las constancias la imprimen con "Son: …", el pagaré dentro de su cláusula).
 * Única fuente de esta conversión en el sistema — no se duplica en
 * JavaScript. Soporta 0 a 999.999.999.999.
 */
public final class ConversorNumerosALetras {

    private static final long MAXIMO = 999_999_999_999L;

    private static final String[] UNIDADES = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve"};
    private static final String[] DIECIS = {"diez", "once", "doce", "trece", "catorce", "quince", "dieciséis",
            "diecisiete", "dieciocho", "diecinueve"};
    private static final String[] VEINTIS = {"veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
            "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] DECENAS = {"", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta",
            "ochenta", "noventa"};
    private static final String[] CENTENAS = {"", "ciento", "doscientos", "trescientos", "cuatrocientos",
            "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"};

    private ConversorNumerosALetras() {
    }

    /** "tres millones", "un millón", "ciento un mil", "cero" (sin la palabra "guaraníes"). */
    public static String comoLetras(BigDecimal monto) {
        if (monto == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo.");
        }
        long valor = monto.setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (valor < 0 || valor > MAXIMO) {
            throw new IllegalArgumentException("Solo se admiten montos entre 0 y " + MAXIMO + ".");
        }
        if (valor == 0) {
            return "cero";
        }

        long milMillones = valor / 1_000_000_000L;
        long resto1 = valor % 1_000_000_000L;
        long millones = resto1 / 1_000_000L;
        long resto2 = resto1 % 1_000_000L;
        long miles = resto2 / 1000L;
        long unidades = resto2 % 1000L;

        StringBuilder resultado = new StringBuilder();
        if (milMillones > 0) {
            agregar(resultado, milMillones == 1 ? "mil millones"
                    : apocopar(convertirGrupo((int) milMillones)) + " mil millones");
        }
        if (millones > 0) {
            // B127 (11/11/2026): el apocopar() faltaba acá y sí estaba en las dos ramas vecinas
            // (mil millones y miles), así que 21.000.000 daba "veintiuno millones" mientras que
            // 21.000 daba "veintiún mil". Lo encontró el test nuevo de la apócope, no una pantalla.
            agregar(resultado, millones == 1 ? "un millón"
                    : apocopar(convertirGrupo((int) millones)) + " millones");
        }
        if (miles > 0) {
            agregar(resultado, miles == 1 ? "mil" : apocopar(convertirGrupo((int) miles)) + " mil");
        }
        if (unidades > 0) {
            agregar(resultado, convertirGrupo((int) unidades));
        }
        return resultado.toString();
    }

    /**
     * "tres millones de guaraníes", "quinientos mil guaraníes", "setenta y un guaraníes",
     * "un guaraní" — para la vista previa mientras se escribe y para los documentos, que la usan
     * con la palabra ya incluida.
     *
     * <p>B127 (11/11/2026, revisión final de pantallas): acá el número va <b>delante</b> del
     * sustantivo masculino "guaraníes" en los seis documentos que la usan (recibo, factura,
     * constancias, comprobante de desembolso, contrato y pagaré) y en las seis pantallas, así que
     * un monto terminado en 1 pide la <b>apócope</b> — "setenta y un guaraníes", no "setenta y uno
     * guaraníes". La apócope ya existía acá para "mil" y "mil millones" ({@link #apocopar}) —no
     * para "millones", que fue el segundo sitio que corrigió este mismo ítem— y B11 la había
     * expuesto para las fechas; lo que faltaba era aplicarla también a esta frase. No toca
     * los documentos que usan el patrón "Son guaraníes: …", donde el sustantivo va antes.</p>
     */
    public static String comoLetrasConGuaranies(BigDecimal monto) {
        String letras = comoLetras(monto);
        boolean terminaEnMillon = letras.endsWith("millón") || letras.endsWith("millones")
                || letras.endsWith("mil millones");
        if (terminaEnMillon) {
            return letras + " de guaraníes";
        }
        // 1 Gs. es el único caso en singular: "un guaraní", no "un guaraníes".
        if ("uno".equals(letras)) {
            return "un guaraní";
        }
        return apocopar(letras) + " guaraníes";
    }

    private static void agregar(StringBuilder resultado, String texto) {
        if (resultado.length() > 0) {
            resultado.append(' ');
        }
        resultado.append(texto);
    }

    /** 0 a 999. Nunca antepone "uno" (esa apocopación la resuelve el llamador con apocopar()). */
    private static String convertirGrupo(int n) {
        if (n == 0) {
            return "";
        }
        if (n < 10) {
            return UNIDADES[n];
        }
        if (n < 20) {
            return DIECIS[n - 10];
        }
        if (n < 30) {
            return VEINTIS[n - 20];
        }
        if (n < 100) {
            int decena = n / 10;
            int unidad = n % 10;
            return unidad == 0 ? DECENAS[decena] : DECENAS[decena] + " y " + UNIDADES[unidad];
        }
        if (n == 100) {
            return "cien";
        }
        int centena = n / 100;
        int resto = n % 100;
        return resto == 0 ? CENTENAS[centena] : CENTENAS[centena] + " " + convertirGrupo(resto);
    }

    /**
     * B11 (01/11/2026, decisión de Joel): el mismo número en letras pero <b>apocopado</b>, para
     * cuando va delante de un sustantivo masculino — "veintiún días", no "veintiuno días". La
     * apócope ya existía acá para "mil"/"millones" ({@link #apocopar}); lo que faltaba era poder
     * pedirla desde afuera: {@code ConversorFechasALetras} deletreaba el día con
     * {@link #comoLetras} y devolvía "a los veintiuno días" para cualquier fecha terminada en 21.
     * Pura.
     */
    public static String comoLetrasApocopado(BigDecimal numero) {
        return apocopar(comoLetras(numero));
    }

    /** "uno" -> "un", "veintiuno" -> "veintiún" (delante de "mil"/"millones"). */
    private static String apocopar(String texto) {
        if (texto.endsWith("veintiuno")) {
            return texto.substring(0, texto.length() - "veintiuno".length()) + "veintiún";
        }
        if (texto.endsWith("uno")) {
            return texto.substring(0, texto.length() - 1);
        }
        return texto;
    }
}
