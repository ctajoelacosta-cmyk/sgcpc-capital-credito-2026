package py.com.capitalcredito.sgcpc.herramientas;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Que ningun atributo HTML termine saliendo como TEXTO VISIBLE en la pantalla.
 *
 * <p>Existe por un defecto real que Joel vio el 16/11/2026 en <i>Informes → Caja</i>: donde tenia
 * que haber un boton <i>PDF</i> aparecia, en otra tipografia y dentro de un recuadro, el texto
 * {@code class="btn btn-outline-secondary btn-sm" title="Exporta segun los filtros
 * aplicados...">PDF}. La causa: el {@code <%@ include %>} del icono de ayuda de B209 habia quedado
 * escrito <b>adentro</b> de la etiqueta {@code <button ...>} todavia abierta, entre
 * {@code formtarget="_blank"} y {@code class=}. Ese include emite un {@code <i ...></i>}, y su
 * {@code >} cierra el {@code <button>} antes de tiempo: todo lo que venia despues --los atributos
 * de verdad-- pasa a ser contenido del boton, o sea texto que el usuario lee.</p>
 *
 * <p><b>Por que un test estatico y no uno que pida la pagina por HTTP</b>: {@code mvn test} no
 * puede depender de que Tomcat este levantado (y el humo, que si pide paginas, lo corre Joel). Asi
 * que esta clase <b>reconstruye</b> el marcado que la pantalla va a servir --expande los
 * {@code <%@ include %>}, borra comentarios y scriptlets, y deja un hueco donde cada
 * {@code <%= ... %>} va a escribir su valor-- y despues lo recorre con un automata que sabe si
 * esta adentro o afuera de una etiqueta. Si un {@code class="}, un {@code title="} o un
 * {@code href="} aparece <b>afuera</b>, es texto visible y es el defecto.</p>
 *
 * <p>No mira el anidamiento ni el balanceo de etiquetas a proposito: {@code cabecera.jsp} abre
 * etiquetas que {@code pie.jsp} cierra, asi que ninguna pantalla esta balanceada por si sola. El
 * estado "adentro/afuera de una etiqueta" si es local, y es justo el que delata este defecto.</p>
 */
public class EtiquetasHtmlDePantallaTest {

    private static final File CARPETA_WEBAPP = new File("src/main/webapp");
    private static final File CARPETA_VISTAS = new File("src/main/webapp/WEB-INF/vistas");

    /**
     * Los atributos que se buscan en posicion de texto. Son los tres que no tienen ninguna razon
     * para aparecer en una frase que el usuario lee, y los que delatan una etiqueta cerrada antes
     * de tiempo -- {@code class} es el que vio Joel.
     */
    private static final List<String> ATRIBUTOS = Arrays.asList("class=\"", "title=\"", "href=\"");

    private static final Pattern COMENTARIO_JSP = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern INCLUDE_ESTATICO = Pattern.compile(
            "<%@\\s*include\\s+file\\s*=\\s*\"([^\"]+)\"\\s*%>");
    /** Expresion de JSP: deja un hueco, porque en la pagina servida ahi va a haber un valor. */
    private static final Pattern EXPRESION_JSP = Pattern.compile("<%=.*?%>", Pattern.DOTALL);
    /** Scriptlets, declaraciones y directivas: no escriben nada en el HTML. */
    private static final Pattern SCRIPTLET_JSP = Pattern.compile("<%[^=].*?%>", Pattern.DOTALL);
    private static final Pattern SCRIPTLET_PELADO = Pattern.compile("<%.*?%>", Pattern.DOTALL);

    @Test
    public void ningunAtributoSaleComoTextoVisible() throws IOException {
        List<String> defectos = new ArrayList<String>();
        for (File jsp : buscarJsp(CARPETA_VISTAS)) {
            String marcado = reconstruirMarcado(jsp, new LinkedHashSet<String>());
            for (int posicion : atributosEnTextoVisible(marcado)) {
                defectos.add(ruta(jsp) + ": sale como TEXTO VISIBLE -> "
                        + resumirAlrededor(marcado, posicion));
            }
        }
        assertTrue("Hay atributos HTML que el usuario va a LEER en la pantalla, en vez de que el"
                + " navegador los aplique. Casi siempre es una etiqueta cerrada antes de tiempo:"
                + " algo que emite HTML (un <%@ include %>, un <%= %> que trae marcado) quedo"
                + " escrito adentro de un <a ...>/<button ...> todavia abierto.\n"
                + String.join("\n", defectos), defectos.isEmpty());
    }

    /**
     * La misma familia de defecto, agarrada una etapa antes y con el dedo en la causa: una
     * etiqueta que tiene otro {@code <} adentro. Eso solo pasa cuando algo que escribe HTML --un
     * {@code <%@ include %>}, un {@code <%= %>} que trae marcado-- quedo escrito en medio de los
     * atributos de un {@code <a>} o un {@code <button>}. Es mas util que el otro test porque
     * nombra la etiqueta culpable y no el sintoma, y porque atrapa tambien los casos donde lo que
     * se escapa no es un atributo con comillas.
     */
    @Test
    public void ningunaEtiquetaTieneOtraAdentro() throws IOException {
        List<String> defectos = new ArrayList<String>();
        for (File jsp : buscarJsp(CARPETA_VISTAS)) {
            String marcado = reconstruirMarcado(jsp, new LinkedHashSet<String>());
            for (int posicion : etiquetasConOtraAdentro(marcado)) {
                defectos.add(ruta(jsp) + ": etiqueta con otra adentro -> "
                        + resumirAlrededor(marcado, posicion));
            }
        }
        assertTrue("Hay etiquetas HTML con otra etiqueta escrita adentro de sus atributos. El"
                + " navegador cierra la primera en el > de la de adentro, y el resto de los"
                + " atributos pasa a ser texto visible:\n" + String.join("\n", defectos),
                defectos.isEmpty());
    }

    /**
     * Reconstruye el HTML que la pantalla va a servir: expande los includes estaticos (que es
     * traduccion literal, igual que en el servidor), borra lo que no escribe nada y deja un hueco
     * por cada expresion.
     */
    private static String reconstruirMarcado(File jsp, Set<String> yaIncluidos) throws IOException {
        String texto = new String(Files.readAllBytes(jsp.toPath()), StandardCharsets.UTF_8);
        texto = COMENTARIO_JSP.matcher(texto).replaceAll(" ");

        Matcher include = INCLUDE_ESTATICO.matcher(texto);
        StringBuffer conIncludes = new StringBuffer();
        while (include.find()) {
            String destino = include.group(1);
            File incluido = new File(CARPETA_WEBAPP, destino);
            String contenido = " ";
            if (incluido.isFile() && yaIncluidos.add(destino)) {
                contenido = reconstruirMarcado(incluido, yaIncluidos);
            }
            include.appendReplacement(conIncludes, Matcher.quoteReplacement(contenido));
        }
        include.appendTail(conIncludes);
        texto = conIncludes.toString();

        texto = EXPRESION_JSP.matcher(texto).replaceAll("HUECO");
        texto = SCRIPTLET_JSP.matcher(texto).replaceAll(" ");
        texto = SCRIPTLET_PELADO.matcher(texto).replaceAll(" ");
        return texto;
    }

    /**
     * Recorre el marcado y devuelve las posiciones donde un atributo aparece en posicion de texto.
     * Saltea los comentarios HTML y el cuerpo de {@code <script>} y {@code <style>}: ahi un
     * {@code class="} es una cadena de JavaScript o un selector de CSS, no texto que se lea.
     */
    private static List<Integer> atributosEnTextoVisible(String marcado) {
        List<Integer> posiciones = new ArrayList<Integer>();
        int i = 0;
        int n = marcado.length();
        boolean enEtiqueta = false;
        char comilla = 0;
        while (i < n) {
            char c = marcado.charAt(i);
            if (enEtiqueta) {
                if (comilla != 0) {
                    if (c == comilla) {
                        comilla = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    comilla = c;
                } else if (c == '>') {
                    enEtiqueta = false;
                }
                i++;
                continue;
            }
            if (c == '<') {
                if (marcado.startsWith("<!--", i)) {
                    int cierre = marcado.indexOf("-->", i);
                    i = cierre < 0 ? n : cierre + 3;
                    continue;
                }
                int saltoCuerpo = finDeCuerpoLiteral(marcado, i, "script");
                if (saltoCuerpo < 0) {
                    saltoCuerpo = finDeCuerpoLiteral(marcado, i, "style");
                }
                if (saltoCuerpo >= 0) {
                    i = saltoCuerpo;
                    continue;
                }
                enEtiqueta = true;
                i++;
                continue;
            }
            for (String atributo : ATRIBUTOS) {
                if (marcado.regionMatches(true, i, atributo, 0, atributo.length())) {
                    posiciones.add(Integer.valueOf(i));
                    break;
                }
            }
            i++;
        }
        return posiciones;
    }

    /**
     * Recorre el marcado con el mismo automata y devuelve el comienzo de cada etiqueta que tiene
     * un {@code <} suelto adentro (fuera de comillas).
     */
    private static List<Integer> etiquetasConOtraAdentro(String marcado) {
        List<Integer> posiciones = new ArrayList<Integer>();
        int i = 0;
        int n = marcado.length();
        int comienzoEtiqueta = -1;
        char comilla = 0;
        while (i < n) {
            char c = marcado.charAt(i);
            if (comienzoEtiqueta >= 0) {
                if (comilla != 0) {
                    if (c == comilla) {
                        comilla = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    comilla = c;
                } else if (c == '>') {
                    comienzoEtiqueta = -1;
                } else if (c == '<') {
                    posiciones.add(Integer.valueOf(comienzoEtiqueta));
                    comienzoEtiqueta = i;   // se sigue desde la de adentro, para no repetir
                }
                i++;
                continue;
            }
            if (c == '<') {
                if (marcado.startsWith("<!--", i)) {
                    int cierre = marcado.indexOf("-->", i);
                    i = cierre < 0 ? n : cierre + 3;
                    continue;
                }
                int saltoCuerpo = finDeCuerpoLiteral(marcado, i, "script");
                if (saltoCuerpo < 0) {
                    saltoCuerpo = finDeCuerpoLiteral(marcado, i, "style");
                }
                if (saltoCuerpo >= 0) {
                    i = saltoCuerpo;
                    continue;
                }
                comienzoEtiqueta = i;
            }
            i++;
        }
        return posiciones;
    }

    /**
     * Si en {@code inicio} empieza {@code <etiqueta ...>}, devuelve la posicion despues de su
     * {@code </etiqueta>}; si no, -1.
     */
    private static int finDeCuerpoLiteral(String marcado, int inicio, String etiqueta) {
        if (!marcado.regionMatches(true, inicio + 1, etiqueta, 0, etiqueta.length())) {
            return -1;
        }
        char siguiente = inicio + 1 + etiqueta.length() < marcado.length()
                ? marcado.charAt(inicio + 1 + etiqueta.length()) : '>';
        if (siguiente != '>' && siguiente != ' ' && siguiente != '\n' && siguiente != '\r'
                && siguiente != '\t') {
            return -1;
        }
        String cierre = "</" + etiqueta;
        int posicionCierre = marcado.toLowerCase().indexOf(cierre, inicio);
        if (posicionCierre < 0) {
            return marcado.length();
        }
        int finEtiqueta = marcado.indexOf('>', posicionCierre);
        return finEtiqueta < 0 ? marcado.length() : finEtiqueta + 1;
    }

    private static String resumirAlrededor(String marcado, int posicion) {
        int desde = Math.max(0, posicion - 10);
        int hasta = Math.min(marcado.length(), posicion + 70);
        return marcado.substring(desde, hasta).replaceAll("\\s+", " ").trim();
    }

    private static String ruta(File jsp) {
        return jsp.getPath().replace(CARPETA_VISTAS.getPath() + File.separator, "")
                .replace(File.separatorChar, '/');
    }

    private static List<File> buscarJsp(File carpeta) {
        List<File> encontradas = new ArrayList<File>();
        File[] hijos = carpeta.listFiles();
        if (hijos == null) {
            return encontradas;
        }
        for (File hijo : hijos) {
            if (hijo.isDirectory()) {
                encontradas.addAll(buscarJsp(hijo));
            } else if (hijo.getName().endsWith(".jsp")) {
                encontradas.add(hijo);
            }
        }
        return encontradas;
    }
}
