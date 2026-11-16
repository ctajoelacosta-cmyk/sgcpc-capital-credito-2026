package py.com.capitalcredito.sgcpc.infraestructura;

import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;

/**
 * Formatea texto para mostrar en pantalla (nunca para guardar: la cédula se
 * guarda siempre normalizada, ver ServicioClientes.normalizarCedula, D-37).
 * Reutilizable para cualquier pantalla de las etapas siguientes.
 */
public final class FormateadorTexto {

    private FormateadorTexto() {
    }

    /**
     * B188 (15/11/2026, defecto que Joel vio en pantalla): cierra una oración con punto, pero
     * <b>solo si hace falta</b>.
     *
     * <p>Nació del cuerpo de la ventana de "Marcar revisada", que pegaba un "." después del motivo
     * que había escrito la cajera: con un motivo que ya terminaba en punto, el cartel decía
     * <i>"…a propósito.."</i>. Es el mismo defecto de D-145 punto 8 (el "Gs..") con otra fuente:
     * acá el texto viene de <b>la base</b>, escrito por una persona, así que
     * {@code TextosDePantallaTest} no puede verlo — ninguna de sus tres redes lee la base. Por eso
     * la regla vive acá, con su JUnit.</p>
     *
     * <p>No agrega nada si el texto ya termina en <code>.</code>, <code>!</code>, <code>?</code>,
     * <code>…</code> o <code>:</code>, ni si viene vacío o null (devuelve cadena vacía: un texto que
     * no existe no se cierra con un punto suelto). Ignora los espacios del final para decidir.</p>
     *
     * <p><b>Dónde NO se usa, a propósito</b>: {@code ServicioReversiones} y
     * {@code ServicioSolicitudes} terminan sus detalles de auditoría con el motivo del usuario y
     * <b>no</b> le agregan punto, así que no tienen el defecto. Uniformarlos es otra decisión, de
     * Joel (ya estaba anotada así en las reglas internas).</p>
     */
    public static String conPuntoFinal(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replaceAll("\\s+$", "");
        if (limpio.isEmpty()) {
            return "";
        }
        char ultimo = limpio.charAt(limpio.length() - 1);
        if (ultimo == '.' || ultimo == '!' || ultimo == '?' || ultimo == '…' || ultimo == ':') {
            return limpio;
        }
        return limpio + ".";
    }

    /** "1234567" -> "1.234.567". Si no es solo dígitos (dato viejo/atípico), se muestra tal cual. */
    public static String comoCedulaConPuntos(String cedulaNormalizada) {
        if (cedulaNormalizada == null || !cedulaNormalizada.matches("\\d+")) {
            return cedulaNormalizada == null ? "" : cedulaNormalizada;
        }
        StringBuilder conPuntos = new StringBuilder();
        int contador = 0;
        for (int i = cedulaNormalizada.length() - 1; i >= 0; i--) {
            conPuntos.append(cedulaNormalizada.charAt(i));
            contador++;
            if (contador % 3 == 0 && i > 0) {
                conPuntos.append('.');
            }
        }
        return conPuntos.reverse().toString();
    }

    /** Bloque de seguridad (hallazgo de Joel, solo para la pantalla de Auditoría -- el PDF/CSV
     * quedan con el valor crudo hasta que se revise el RF40): "127.0.0.1"/"::1"/"0:0:0:0:0:0:0:1"
     * (localhost, con o sin IPv4-mapped) son siempre el propio servidor, nunca un cliente remoto. */
    public static String comoDireccionIpLegible(String direccionIp) {
        if (direccionIp == null) {
            return "";
        }
        if ("127.0.0.1".equals(direccionIp) || "::1".equals(direccionIp) || "0:0:0:0:0:0:0:1".equals(direccionIp)) {
            return "Este equipo (servidor)";
        }
        return direccionIp;
    }

    /** Bloque de seguridad (hallazgo de Joel, solo para la pantalla de Auditoría): resume el
     * User-Agent crudo a "Navegador · Sistema" (ej. "Chrome · Windows") -- el texto completo sigue
     * disponible en el "title" de la celda. El orden de los "contains" importa: Edge y Opera
     * también contienen "Chrome", y Chrome también contiene "Safari". */
    public static String comoNavegadorResumido(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "";
        }
        String navegador;
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) {
            navegador = "Edge";
        } else if (userAgent.contains("OPR/") || userAgent.contains("Opera")) {
            navegador = "Opera";
        } else if (userAgent.contains("Firefox")) {
            navegador = "Firefox";
        } else if (userAgent.contains("Chrome")) {
            navegador = "Chrome";
        } else if (userAgent.contains("Safari")) {
            navegador = "Safari";
        } else {
            navegador = "Otro navegador";
        }
        String sistema;
        if (userAgent.contains("Windows")) {
            sistema = "Windows";
        } else if (userAgent.contains("Android")) {
            sistema = "Android";
        } else if (userAgent.contains("Mac OS")) {
            sistema = "Mac";
        } else if (userAgent.contains("Linux")) {
            sistema = "Linux";
        } else {
            sistema = null;
        }
        return sistema == null ? navegador : navegador + " · " + sistema;
    }

    /** V1/RF39 (pedido de Joel): agrega un espacio después de cada coma -- SOLO para parámetros
     * TEXTO (listas de códigos separados por coma, ej. "Asunción,Central" -> "Asunción, Central"),
     * para que el texto se parta ahí en vez de necesitar romper una palabra. Nunca para DECIMAL
     * (ahí la coma es el separador decimal, ej. "27,39") ni para ningún otro tipo -- se devuelve
     * tal cual. Un solo lugar compartido por Parámetros, el historial y su PDF; el valor que se
     * guarda y el que se exporta a CSV nunca pasan por acá. */
    public static String comoListaConEspacios(String valor, TipoValorParametro tipo) {
        if (valor == null || tipo != TipoValorParametro.TEXTO) {
            return valor;
        }
        return valor.replaceAll(",\\s*", ", ");
    }
}
