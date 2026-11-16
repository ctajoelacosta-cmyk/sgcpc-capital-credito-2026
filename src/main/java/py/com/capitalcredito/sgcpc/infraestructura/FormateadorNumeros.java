package py.com.capitalcredito.sgcpc.infraestructura;

import java.math.BigDecimal;

/**
 * Formatea numeros para mostrar en pantalla (nunca para calcular: el motor
 * financiero sigue trabajando con la fraccion, D-12). Reutilizable para
 * cualquier pantalla de las etapas siguientes.
 */
public final class FormateadorNumeros {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private FormateadorNumeros() {
    }

    /** 0.273900 -> "27,39 %"; 1.000000 -> "100 %" (sin ceros de mas). */
    public static String comoPorcentaje(BigDecimal fraccion) {
        if (fraccion == null) {
            return "";
        }
        BigDecimal porcentaje = fraccion.multiply(CIEN).stripTrailingZeros();
        return porcentaje.toPlainString().replace(".", ",") + " %";
    }

    /** RF05 (pedido de Joel, 10/10/2026): igual que comoPorcentaje, pero siempre con un decimal
     * ("66,9 %") -- para el indicador en vivo de afectación, donde la fracción exacta del cálculo
     * (ej. 0.6686953333) daría "66,86953333 %" con comoPorcentaje, demasiado detalle para mientras
     * se escribe. 0.400000 -> "40,0 %" (a diferencia de comoPorcentaje, que no muestra el decimal
     * si es cero). */
    public static String comoPorcentajeUnDecimal(BigDecimal fraccion) {
        if (fraccion == null) {
            return "";
        }
        BigDecimal porcentaje = fraccion.multiply(CIEN).setScale(1, java.math.RoundingMode.HALF_UP);
        return porcentaje.toPlainString().replace(".", ",") + " %";
    }

    /** Fraccion equivalente a un porcentaje ya parseado (27,39 -> 0.2739). */
    public static BigDecimal fraccionDesdePorcentaje(BigDecimal porcentaje, int escala) {
        return porcentaje.divide(CIEN, escala, java.math.RoundingMode.HALF_UP);
    }

    /** 850.00 -> "850" (punto 6, D-57: el puntaje del buró se guarda sin decimales aunque la columna los admita). */
    public static String comoEntero(BigDecimal numero) {
        if (numero == null) {
            return "";
        }
        return numero.toBigInteger().toString();
    }

    /** 1500000 -> "1.500.000 Gs." (separador de miles, sin decimales: el motor ya redondea a guarani entero). */
    public static String comoGuaranies(BigDecimal monto) {
        if (monto == null) {
            return "";
        }
        String digitos = monto.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        boolean negativo = digitos.startsWith("-");
        if (negativo) {
            digitos = digitos.substring(1);
        }
        StringBuilder conPuntos = new StringBuilder();
        int contador = 0;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            conPuntos.append(digitos.charAt(i));
            contador++;
            if (contador % 3 == 0 && i > 0) {
                conPuntos.append('.');
            }
        }
        return (negativo ? "-" : "") + conPuntos.reverse().toString() + " Gs.";
    }

    /**
     * 2230 -> "2.230". El separador de miles de una <b>cantidad</b> (registros, filas, cuotas),
     * sin la unidad monetaria.
     *
     * <p><b>B205, segunda vuelta (15/11/2026, hallazgo de Joel al probar)</b>: el contador del
     * título de Auditoría salía <i>"Auditoría (2230 registros)"</i>, pegado, porque imprimía el
     * {@code int} tal cual. Todos los contadores de la fase 2 lo hacían igual —y ninguno se había
     * notado, porque hasta ahora ninguno había pasado de 999—: Sesiones cerradas, Movimientos,
     * Historial de cobros, y los seis <i>"N encontrados"</i> de las bandejas. Con 2.230 registros
     * el defecto apareció solo.</p>
     *
     * <p>Comparte el agrupado con {@link #comoGuaranies} a propósito: es el <b>mismo</b> separador
     * (el punto) que el resto del sistema, así que un número no se lee de dos formas distintas
     * según de qué hable. Lo único que no lleva es el <i>"Gs."</i>, porque una cantidad no es
     * plata — y de paso eso evita la trampa del punto duplicado que persiguieron B178 y B188.</p>
     */
    public static String comoCantidad(int cantidad) {
        return comoCantidad(Long.valueOf(cantidad));
    }

    /** Ver {@link #comoCantidad(int)}. Null -> "" (un contador que no llegó no inventa un cero). */
    public static String comoCantidad(Long cantidad) {
        if (cantidad == null) {
            return "";
        }
        String digitos = Long.toString(Math.abs(cantidad.longValue()));
        StringBuilder conPuntos = new StringBuilder();
        int contador = 0;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            conPuntos.append(digitos.charAt(i));
            contador++;
            if (contador % 3 == 0 && i > 0) {
                conPuntos.append('.');
            }
        }
        return (cantidad.longValue() < 0 ? "-" : "") + conPuntos.reverse().toString();
    }
}
