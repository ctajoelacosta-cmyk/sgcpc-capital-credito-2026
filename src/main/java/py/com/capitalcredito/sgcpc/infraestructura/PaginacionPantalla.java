package py.com.capitalcredito.sgcpc.infraestructura;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * B219 (15/11/2026): la paginación de una pantalla que no tiene búsqueda en vivo, del lado del
 * servidor — el compañero de {@code plantilla/paginacion.jsp}, que es el marcado.
 *
 * <p>Nació al paginar el historial de parámetros. La lógica ya existía, escrita dentro de
 * {@code ControladorReportesServlet} para los dieciséis reportes de B210; copiarla acá habría sido
 * la duplicación que este proyecto evita, así que vive en un solo lugar y el que la necesite la
 * llama. <b>{@code ControladorReportesServlet} todavía tiene su copia</b>: pasarlo a delegar acá
 * está registrado como <b>B224</b> y espera la decisión de Joel, porque toca el controlador de
 * dieciséis pantallas ya probadas en pantalla (el mismo criterio con que B183 decidió no tocar
 * {@code listado-vivo.js}).</p>
 *
 * <p><b>No recibe ni la ruta ni los filtros, a propósito</b>: la URL base sale de
 * {@code getRequestURI()} y de los parámetros del propio pedido menos {@code pagina}. Así un filtro
 * nuevo viaja solo al cambiar de página, sin que nadie se acuerde de agregarlo — que es el olvido
 * que B147/B163/B170/B175 fueron corrigiendo pantalla por pantalla.</p>
 */
public final class PaginacionPantalla {

    /** El mismo tamaño de página que el resto del sistema (B148/B205/B210). */
    public static final int FILAS_POR_PAGINA = 20;

    private PaginacionPantalla() {
    }

    /** Cuántas páginas hacen falta para esa cantidad de filas; nunca menos de 1 (una lista vacía
     * sigue siendo "página 1 de 1", para que la pantalla no diga "de 0"). */
    public static int totalPaginas(int totalFilas) {
        return Math.max(1, (int) Math.ceil(totalFilas / (double) FILAS_POR_PAGINA));
    }

    /** La página pedida, acotada al rango real. Cualquier cosa que no sea un número en rango cae
     * en 1: un {@code ?pagina=0}, {@code ?pagina=-3} o {@code ?pagina=hola} escritos a mano no
     * pueden dejar la pantalla vacía ni tirar una excepción. */
    public static int paginaPedida(String texto, int totalPaginas) {
        int pagina = 1;
        if (texto != null && !texto.trim().isEmpty()) {
            try {
                pagina = Integer.parseInt(texto.trim());
            } catch (NumberFormatException error) {
                pagina = 1;
            }
        }
        if (pagina < 1) {
            pagina = 1;
        }
        if (pagina > totalPaginas) {
            pagina = totalPaginas;
        }
        return pagina;
    }

    /**
     * La URL base que pide {@code plantilla/paginacion.jsp}: la ruta con todos los parámetros del
     * pedido menos {@code pagina}, y terminada en {@code ?} o en {@code &} (el fragmento le pega
     * {@code pagina=N} y nada más). Los parámetros vacíos no viajan, para no arrastrar
     * {@code desde=&hasta=} en cada enlace.
     */
    public static String urlBase(String ruta, Map<String, String[]> parametros) {
        StringBuilder query = new StringBuilder();
        if (parametros != null) {
            for (Map.Entry<String, String[]> entrada : parametros.entrySet()) {
                String nombre = entrada.getKey();
                String[] valores = entrada.getValue();
                if ("pagina".equals(nombre) || valores == null || valores.length == 0
                        || valores[0] == null || valores[0].trim().isEmpty()) {
                    continue;
                }
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(nombre).append('=').append(valores[0].trim());
            }
        }
        return ruta + (query.length() > 0 ? "?" + query + "&" : "?");
    }

    /**
     * Recorta la lista a la página pedida y deja en el request todo lo que
     * {@code plantilla/paginacion.jsp} necesita, más {@code totalFilas}.
     *
     * <p><b>{@code filas} tiene que ser el listado COMPLETO</b>, y los totales de la pantalla hay
     * que calcularlos ANTES de llamar acá: contar lo que quedó dibujado diría "20" en cada página,
     * la trampa que B191 ya encontró en Caja.</p>
     *
     * <p>Los enlaces navegan de verdad ({@code paginacionConListadoVivo} en FALSE): esta pantalla
     * no tiene búsqueda en vivo, y {@code listado-vivo.js} le pega {@code pagina=N} fijo al pedido
     * que arma desde el formulario (misma razón que B183 y que los dieciséis reportes de B210).</p>
     */
    public static <T> List<T> paginar(HttpServletRequest request, List<T> filas) {
        int total = filas.size();
        int totalPaginas = totalPaginas(total);
        int pagina = paginaPedida(request.getParameter("pagina"), totalPaginas);
        request.setAttribute("pagina", Integer.valueOf(pagina));
        request.setAttribute("totalPaginas", Integer.valueOf(totalPaginas));
        request.setAttribute("totalFilas", Integer.valueOf(total));
        request.setAttribute("paginacionUrlBase", urlBase(request.getRequestURI(), parametrosDe(request)));
        request.setAttribute("paginacionConListadoVivo", Boolean.FALSE);
        int desde = (pagina - 1) * FILAS_POR_PAGINA;
        return filas.subList(desde, Math.min(total, desde + FILAS_POR_PAGINA));
    }

    /** Los parámetros del pedido como mapa, para que {@link #urlBase} sea pura y testeable. */
    private static Map<String, String[]> parametrosDe(HttpServletRequest request) {
        Map<String, String[]> parametros = new java.util.LinkedHashMap<>();
        Enumeration<String> nombres = request.getParameterNames();
        while (nombres.hasMoreElements()) {
            String nombre = nombres.nextElement();
            parametros.put(nombre, request.getParameterValues(nombre));
        }
        return parametros;
    }
}
