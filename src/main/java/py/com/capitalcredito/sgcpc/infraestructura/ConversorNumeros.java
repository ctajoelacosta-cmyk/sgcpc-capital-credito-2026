package py.com.capitalcredito.sgcpc.infraestructura;

import java.math.BigDecimal;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;

/**
 * Convierte texto ingresado por el usuario a numeros, aceptando tanto coma
 * como punto de separador decimal (en Paraguay se escribe "1,5"). Nunca
 * lanza NumberFormatException: ante un valor invalido lanza
 * NegocioException con un mensaje claro (Doc. 10 §10.18). Reutilizable
 * para cualquier campo numerico de las etapas siguientes.
 */
public final class ConversorNumeros {

    private ConversorNumeros() {
    }

    public static BigDecimal aBigDecimal(String texto) {
        String normalizado = normalizar(texto);
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            throw new NegocioException("\"" + texto + "\" no es un número válido.");
        }
    }

    public static int aEntero(String texto) {
        String normalizado = normalizar(texto);
        try {
            return Integer.parseInt(normalizado);
        } catch (NumberFormatException e) {
            throw new NegocioException("\"" + texto + "\" no es un número entero válido.");
        }
    }

    /**
     * Campos de monto en guaraníes (cotizador, ingreso del cliente, y los
     * que vengan en etapas siguientes): el punto es separador de miles
     * ("3.000.000" -> 3000000), nunca decimal — los guaraníes no llevan
     * decimales (REGLAS_DESARROLLO.md §5: NUMERIC(18,0)). Distinto de aBigDecimal, que
     * sigue usando la coma como decimal para los campos de porcentaje.
     */
    public static BigDecimal aBigDecimalGuaranies(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese un monto.");
        }
        String sinPuntos = texto.trim().replace(".", "");
        if (!sinPuntos.matches("-?\\d+")) {
            throw new NegocioException("\"" + texto + "\" no es un monto válido (los guaraníes no llevan decimales).");
        }
        return new BigDecimal(sinPuntos);
    }

    private static String normalizar(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese un valor.");
        }
        return texto.trim().replace(",", ".");
    }
}
