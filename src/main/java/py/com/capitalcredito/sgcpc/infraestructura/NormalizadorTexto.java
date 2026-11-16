package py.com.capitalcredito.sgcpc.infraestructura;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Normaliza texto libre antes de guardarlo (destino del crédito, domicilio, motivos, nombres,
 * etc.): recorta espacios al principio/final y colapsa espacios internos dobles a uno solo.
 * Corrección de Joel (revisión de reportes E5): un dato como "Gastos de internación ." con un
 * espacio antes del punto se arrastraba tal cual a los imprimibles. Nunca agrega ni quita
 * puntuación que el usuario haya puesto -- solo espacios en blanco.
 */
public final class NormalizadorTexto {

    private static final Locale ESPANOL = new Locale("es");

    /** RF02 (pedido de Joel): nunca en mayúscula ni al principio de todo, salvo que sean la
     * primera palabra del nombre completo (ej. "De Los Santos" como apellido solo, sin nombre
     * antes, se capitaliza igual). */
    private static final Set<String> PARTICULAS_MINUSCULAS = new java.util.HashSet<>(
            Arrays.asList("de", "del", "la", "las", "los", "y"));

    private NormalizadorTexto() {
    }

    public static String normalizarTextoLibre(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.trim().replaceAll("\\s+", " ");
    }

    /**
     * RF02 (pedido de Joel): nombres y apellidos de clientes y usuarios, mayúscula inicial por
     * palabra al guardar (antes se guardaban tal cual se escribían -- "edad menor prueba" quedó
     * así en el cliente 142). Recorta/colapsa espacios primero (normalizarTextoLibre) y después
     * capitaliza cada palabra -- separando también por guión ("pérez-lópez" -> "Pérez-López") --
     * excepto las partículas de PARTICULAS_MINUSCULAS ("de", "del", "la", "las", "los", "y")
     * cuando no son la primera palabra del texto completo (ej. "juan de los santos" ->
     * "Juan de los Santos"). Nunca cambia tildes/ñ (toUpperCase/toLowerCase con Locale español
     * los conserva); no toca apóstrofos ni otra puntuación.
     */
    public static String normalizarNombrePropio(String texto) {
        String limpio = normalizarTextoLibre(texto);
        if (limpio == null || limpio.isEmpty()) {
            return limpio;
        }
        String[] palabras = limpio.split(" ");
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) {
                resultado.append(' ');
            }
            String palabra = palabras[i];
            if (i > 0 && PARTICULAS_MINUSCULAS.contains(palabra.toLowerCase(ESPANOL))) {
                resultado.append(palabra.toLowerCase(ESPANOL));
            } else {
                resultado.append(capitalizarConGuiones(palabra));
            }
        }
        return resultado.toString();
    }

    private static String capitalizarConGuiones(String palabra) {
        String[] segmentos = palabra.split("-", -1);
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < segmentos.length; i++) {
            if (i > 0) {
                resultado.append('-');
            }
            resultado.append(capitalizarSegmento(segmentos[i]));
        }
        return resultado.toString();
    }

    private static String capitalizarSegmento(String segmento) {
        if (segmento.isEmpty()) {
            return segmento;
        }
        return segmento.substring(0, 1).toUpperCase(ESPANOL) + segmento.substring(1).toLowerCase(ESPANOL);
    }

    /** 13/10/2026 (pedido de Joel): minúscula y sin tildes, para comparar texto libre contra un
     * nombre/cédula/código de operación sin que "José"/"jose" o "ñ"/"n" se traten como distintos
     * -- mismo criterio que la búsqueda de "Cliente u operación" de /prestamos (PrestamoDAO.
     * sinAcentos), pero en Java puro (java.text.Normalizer, sin tocar la base de datos) para que
     * el mismo filtro sirva en los 7 informes sin pasar por SQL. Nunca null (cadena vacía si el
     * texto es null). */
    public static String normalizarParaBuscar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("[\\p{M}]", "");
        return sinTildes.toLowerCase(ESPANOL).trim();
    }
}
