package py.com.capitalcredito.sgcpc.herramientas;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Control permanente de coherencia de la documentación (decisión de Joel, 28/10/2026). Mismo
 * espíritu que {@link ReportesCompiladosTest}: verifica una condición exacta que este proyecto ya
 * violó al menos una vez, para que no vuelva a pasar desapercibida.
 *
 * <p>Son dos chequeos, los únicos aprobados por Joel de los 7 propuestos — los dos elegidos por
 * ser <b>exactos</b> (sin falsos positivos) y por corresponder a errores <b>reales</b> del
 * proyecto. El resto quedó anotado en {@code docs/recorrido/FASE2_PLAN.md} para después.</p>
 *
 * <ol>
 *   <li><b>DOGMA 3</b>: cada skill propia de {@code .claude/skills/} tiene que ser idéntica a su
 *       copia de {@code docs/skills_respaldo/}. {@code .claude/} está en {@code .gitignore}, así
 *       que <b>solo la copia se versiona</b>: si se editó una y no la otra, lo que queda en el
 *       repositorio (y lo que verá la próxima sesión tras un clon) no es lo que se escribió.</li>
 *   <li><b>Numeración B/C</b>: la nota "Huecos de numeración" de {@code FASE2_PLAN.md} declara
 *       hasta qué número llega la numeración vigente; tiene que coincidir con el máximo
 *       realmente usado en todo {@code docs/}. Confiar en esa nota cuando ya estaba vieja fue lo
 *       que provocó el <b>B76 duplicado</b> del 27/10/2026.</li>
 * </ol>
 *
 * <p>Esta clase SÍ matchea el patrón {@code *Test} de Surefire: corre con cualquier
 * {@code mvn test}, sin que nadie tenga que acordarse de pedirla aparte. No toca ninguna base de
 * datos ni la red: solo lee archivos del propio repositorio.</p>
 *
 * <p><b>Lo que este control NO hace</b>, dicho de frente: verifica <b>estructura</b>, nunca que
 * lo escrito sea cierto. Que una fila diga "RESUELTO" con un hash al lado no prueba que el ítem
 * funcione — eso lo prueba Joel en pantalla (DOGMA 1).</p>
 */
public class DocumentacionCoherenteTest {

    private static final File SKILLS_VIVAS = new File(".claude/skills");
    private static final File SKILLS_RESPALDO = new File("docs/skills_respaldo");
    private static final File CARPETA_DOCS = new File("docs");
    private static final File FASE2_PLAN = new File("docs/recorrido/FASE2_PLAN.md");

    /** Las 4 propias del proyecto. Las externas (impeccable, ui-ux-pro-max) no se respaldan. */
    private static final String[] SKILLS_PROPIAS = {
        "sgcpc-mapa", "sgcpc-cambio-seguro", "sgcpc-diseno-ui", "sgcpc-reportes-jasper"
    };

    /** "La numeración vigente es ahora **B1-B90** y **C1-C39**" -- se leen los dos topes. */
    private static final Pattern NOTA_NUMERACION = Pattern.compile(
            "La numeración vigente es ahora \\*\\*B1-B(\\d+)\\*\\* y \\*\\*C1-C(\\d+)\\*\\*");

    // =====================================================================
    // 1) DOGMA 3: la skill viva y su copia versionada, idénticas
    // =====================================================================

    @Test
    public void cadaSkillPropiaEsIdenticaASuCopiaDeRespaldo() throws Exception {
        if (!SKILLS_VIVAS.isDirectory()) {
            // Un clon limpio no trae .claude/ (está en .gitignore). Sin skills vivas no hay nada
            // que comparar y el chequeo no aplica -- nunca se inventa un fallo por eso.
            System.out.println("DocumentacionCoherenteTest: no existe " + SKILLS_VIVAS.getPath()
                    + " (normal en un clon limpio); se omite el chequeo del DOGMA 3.");
            return;
        }
        List<String> problemas = new ArrayList<>();
        for (String skill : SKILLS_PROPIAS) {
            File viva = new File(SKILLS_VIVAS, skill + "/SKILL.md");
            File respaldo = new File(SKILLS_RESPALDO, skill + "_SKILL.md");
            if (!viva.isFile()) {
                problemas.add(skill + ": falta la skill viva (" + viva.getPath() + ")");
                continue;
            }
            if (!respaldo.isFile()) {
                problemas.add(skill + ": falta la copia versionada (" + respaldo.getPath()
                        + ") -- es la única que viaja en el repositorio");
                continue;
            }
            // Comparar CONTENIDO, no convención de fin de línea: este repositorio tiene
            // core.autocrlf=true, así que todo archivo que git deja en el disco (incluida la
            // copia de respaldo, en cualquier clon o `git checkout`) queda con CRLF, mientras
            // que la skill viva de .claude/ --que git nunca toca, está en .gitignore-- queda
            // con LF. Sin esta normalización, el control fallaría en todo clon limpio: un falso
            // positivo garantizado. Detectado el 28/10/2026 probando a propósito que el control
            // sepa fallar.
            String textoVivo = leerNormalizado(viva.toPath());
            String textoRespaldo = leerNormalizado(respaldo.toPath());
            if (!textoVivo.equals(textoRespaldo)) {
                problemas.add(skill + ": la skill viva y su copia de respaldo NO coinciden"
                        + " (viva: " + textoVivo.length() + " caracteres, respaldo: "
                        + textoRespaldo.length() + "; la diferencia NO es de fin de línea, ya se"
                        + " normaliza)");
            }
        }
        if (!problemas.isEmpty()) {
            fail("DOGMA 3 (REGLAS_DESARROLLO.md §0): las skills propias y sus copias de "
                    + "docs/skills_respaldo/ tienen que ser idénticas -- .claude/ está en "
                    + ".gitignore, así que solo la copia se versiona. Copiar la viva sobre la de "
                    + "respaldo y volver a comitear.\n  - " + String.join("\n  - ", problemas));
        }
    }

    // =====================================================================
    // 2) La nota de numeración de FASE2_PLAN.md, al día
    // =====================================================================

    @Test
    public void laNotaDeNumeracionDeclaraElMaximoBycRealmenteUsado() throws Exception {
        String plan = leer(FASE2_PLAN.toPath());
        Matcher nota = NOTA_NUMERACION.matcher(plan);
        if (!nota.find()) {
            fail("No se encontró la línea \"La numeración vigente es ahora **B1-Bnn** y "
                    + "**C1-Cnn**\" en " + FASE2_PLAN.getPath() + " § Huecos de numeración. Si el "
                    + "formato de esa nota cambió, actualizar también este control.");
        }
        // find() deja el matcher en la PRIMERA aparición; la nota se reescribe en cada
        // actualización, así que vale la ÚLTIMA.
        int bDeclarado = 0;
        int cDeclarado = 0;
        do {
            bDeclarado = Integer.parseInt(nota.group(1));
            cDeclarado = Integer.parseInt(nota.group(2));
        } while (nota.find());

        int bReal = maximoUsado("B");
        int cReal = maximoUsado("C");

        List<String> problemas = new ArrayList<>();
        if (bDeclarado != bReal) {
            problemas.add("B: la nota declara B1-B" + bDeclarado + " pero el máximo realmente "
                    + "usado en docs/ es B" + bReal);
        }
        if (cDeclarado != cReal) {
            problemas.add("C: la nota declara C1-C" + cDeclarado + " pero el máximo realmente "
                    + "usado en docs/ es C" + cReal);
        }
        if (!problemas.isEmpty()) {
            fail("La nota \"Huecos de numeración\" de " + FASE2_PLAN.getPath() + " quedó vieja. "
                    + "Confiar en esa nota desactualizada fue lo que provocó el B76 duplicado del "
                    + "27/10/2026: el número libre se busca con grep sobre TODOS los documentos y "
                    + "recién después se actualiza la nota.\n  - "
                    + String.join("\n  - ", problemas));
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /** El máximo Bnn/Cnn usado en cualquier .md de docs/ -- mismo criterio que el grep que manda
     * la skill: {@code grep -rhoE "\bB[0-9]{1,3}\b" docs/ --include=*.md | sort -u -V | tail}. */
    private int maximoUsado(String letra) throws IOException {
        Pattern patron = Pattern.compile("\\b" + letra + "(\\d{1,3})\\b");
        int maximo = 0;
        try (Stream<Path> archivos = Files.walk(CARPETA_DOCS.toPath())) {
            List<Path> markdowns = archivos
                    .filter(Files::isRegularFile)
                    .filter(ruta -> ruta.getFileName().toString().endsWith(".md"))
                    .collect(java.util.stream.Collectors.toList());
            for (Path markdown : markdowns) {
                Matcher coincidencia = patron.matcher(leer(markdown));
                while (coincidencia.find()) {
                    maximo = Math.max(maximo, Integer.parseInt(coincidencia.group(1)));
                }
            }
        }
        return maximo;
    }

    private String leer(Path ruta) throws IOException {
        return new String(Files.readAllBytes(ruta), StandardCharsets.UTF_8);
    }

    /** Igual que {@link #leer}, pero con los fines de línea normalizados a LF -- ver la nota en
     * {@link #cadaSkillPropiaEsIdenticaASuCopiaDeRespaldo}. */
    private String leerNormalizado(Path ruta) throws IOException {
        return leer(ruta).replace("\r\n", "\n");
    }
}
