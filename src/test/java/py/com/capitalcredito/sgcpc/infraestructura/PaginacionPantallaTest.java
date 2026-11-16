package py.com.capitalcredito.sgcpc.infraestructura;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * B219 (15/11/2026). Las tres partes puras de {@link PaginacionPantalla}, probadas sin servlet y
 * sin base. Lo que miden no es aritmética por gusto: cada caso es una forma de dejar al usuario
 * mirando una pantalla vacía o un enlace que pierde los filtros.
 */
public class PaginacionPantallaTest {

    @Test
    public void unaListaVaciaSigueSiendoUnaPaginaDeUna() {
        assertEquals("Con 0 filas la pantalla no puede decir \"Página 1 de 0\".",
                1, PaginacionPantalla.totalPaginas(0));
    }

    @Test
    public void elTotalDePaginasRedondeaHaciaArriba() {
        assertEquals(1, PaginacionPantalla.totalPaginas(20));
        assertEquals("21 filas son dos páginas: la 21 tiene que estar en alguna.",
                2, PaginacionPantalla.totalPaginas(21));
        assertEquals("Las 164 filas del historial de parámetros al 15/11/2026.",
                9, PaginacionPantalla.totalPaginas(164));
    }

    @Test
    public void laPaginaPedidaSeAcotaAlRangoReal() {
        assertEquals("Sin parámetro, la primera.", 1, PaginacionPantalla.paginaPedida(null, 9));
        assertEquals(5, PaginacionPantalla.paginaPedida("5", 9));
        assertEquals("Más allá de la última cae en la última, no en una pantalla vacía.",
                9, PaginacionPantalla.paginaPedida("99", 9));
        assertEquals("Un 0 escrito a mano en la URL no puede dejar la pantalla vacía.",
                1, PaginacionPantalla.paginaPedida("0", 9));
        assertEquals("Ni un negativo.", 1, PaginacionPantalla.paginaPedida("-3", 9));
        assertEquals("Ni un texto: nunca una excepción en la cara del usuario.",
                1, PaginacionPantalla.paginaPedida("hola", 9));
    }

    @Test
    public void laUrlBaseLlevaLosFiltrosYNuncaLaPaginaVieja() {
        Map<String, String[]> parametros = new LinkedHashMap<>();
        parametros.put("desde", new String[] {"2026-10-01"});
        parametros.put("hasta", new String[] {"2026-11-15"});
        parametros.put("parametro", new String[] {"credito.tea_empresa"});
        parametros.put("pagina", new String[] {"4"});
        assertEquals("Los filtros tienen que viajar al cambiar de página (B147/B163/B170/B175) y la"
                + " página vieja NO, o el enlace pelearía contra el que arma el fragmento.",
                "/sgcpc/parametros/historial?desde=2026-10-01&hasta=2026-11-15"
                        + "&parametro=credito.tea_empresa&",
                PaginacionPantalla.urlBase("/sgcpc/parametros/historial", parametros));
    }

    @Test
    public void unFiltroVacioNoViajaEnLaUrl() {
        Map<String, String[]> parametros = new LinkedHashMap<>();
        parametros.put("desde", new String[] {"2026-10-01"});
        parametros.put("hasta", new String[] {"   "});
        parametros.put("parametro", new String[] {""});
        assertEquals("/sgcpc/parametros/historial?desde=2026-10-01&",
                PaginacionPantalla.urlBase("/sgcpc/parametros/historial", parametros));
    }

    @Test
    public void sinNingunFiltroLaUrlQuedaTerminadaEnInterrogacion() {
        assertEquals("El fragmento le pega \"pagina=N\" al final, así que la URL tiene que terminar"
                + " en \"?\" o en \"&\" (su contrato).",
                "/sgcpc/parametros/historial?",
                PaginacionPantalla.urlBase("/sgcpc/parametros/historial", new LinkedHashMap<String, String[]>()));
    }
    /**
     * B241 (16/11/2026): que ninguna pantalla calcule sus totales SOBRE LA PAGINA.
     *
     * <p>Es la trampa que este proyecto ya piso dos veces --B191 en Caja y B210 en los
     * reportes--: si primero se corta la pagina y despues se suman las filas que quedaron, cada
     * pagina dice "20 prestamos" y sus totales son los de esa pagina, no los del filtro. El
     * javadoc de {@code paginar} lo advierte, pero una advertencia no frena un error; un test si.
     *
     * <p>Se lee el fuente del controlador porque la unica forma de probarlo de verdad seria
     * levantar un servlet: lo que se verifica es el ORDEN de las llamadas, que es exactamente
     * donde esta el defecto posible.</p>
     */
    @Test
    public void ningunControladorCalculaSusTotalesDespuesDeCortarLaPagina() throws IOException {
        List<String> defectos = new ArrayList<String>();
        for (File fuente : javaDe(new File("src/main/java"))) {
            String texto = new String(Files.readAllBytes(fuente.toPath()), StandardCharsets.UTF_8);
            int corte = texto.indexOf("PaginacionPantalla.paginar(");
            while (corte >= 0) {
                // Solo dentro del MISMO metodo: los dos controladores que paginan vuelven a
                // calcular totales mas abajo, en los metodos del PDF y del CSV, y esas llamadas
                // son correctas -- cada exportacion relee todo lo filtrado. Comparar posiciones en
                // el archivo entero marcaba esas y no es el defecto que se busca.
                String metodo = texto.substring(comienzoDeMetodo(texto, corte),
                        finDeMetodo(texto, corte));
                int corteEnMetodo = metodo.indexOf("PaginacionPantalla.paginar(");
                for (String calculo : new String[] {"calcularTotales", "calcularResumen"}) {
                    int ultimo = metodo.lastIndexOf(calculo);
                    if (ultimo > corteEnMetodo) {
                        defectos.add(fuente.getName() + ": " + calculo + " se llama DESPUES de"
                                + " PaginacionPantalla.paginar en el mismo metodo, asi que sumaria"
                                + " solo la pagina");
                    }
                }
                corte = texto.indexOf("PaginacionPantalla.paginar(", corte + 1);
            }
        }
        assertTrue("Los totales de una pantalla paginada se calculan sobre la lista COMPLETA y la"
                + " pagina se corta despues (B191, B210): " + String.join(" | ", defectos),
                defectos.isEmpty());
    }

    /**
     * El comienzo del metodo que contiene {@code posicion}: la firma mas cercana hacia atras. Se
     * reconoce por la sangria de cuatro espacios de un miembro de la clase, que es la convencion
     * del proyecto entero.
     */
    private static int comienzoDeMetodo(String texto, int posicion) {
        int mejor = 0;
        for (String firma : new String[] {"\n    private ", "\n    protected ", "\n    public "}) {
            int donde = texto.lastIndexOf(firma, posicion);
            if (donde > mejor) {
                mejor = donde;
            }
        }
        return mejor;
    }

    /** El fin del metodo: la firma siguiente, o el fin del archivo. */
    private static int finDeMetodo(String texto, int posicion) {
        int mejor = texto.length();
        for (String firma : new String[] {"\n    private ", "\n    protected ", "\n    public "}) {
            int donde = texto.indexOf(firma, posicion);
            if (donde >= 0 && donde < mejor) {
                mejor = donde;
            }
        }
        return mejor;
    }

    private static List<File> javaDe(File carpeta) {
        List<File> encontrados = new ArrayList<File>();
        File[] hijos = carpeta.listFiles();
        if (hijos == null) {
            return encontrados;
        }
        for (File hijo : hijos) {
            if (hijo.isDirectory()) {
                encontrados.addAll(javaDe(hijo));
            } else if (hijo.getName().endsWith(".java")) {
                encontrados.add(hijo);
            }
        }
        return encontrados;
    }
}
