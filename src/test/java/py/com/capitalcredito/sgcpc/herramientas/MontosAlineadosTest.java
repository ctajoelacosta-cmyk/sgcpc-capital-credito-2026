package py.com.capitalcredito.sgcpc.herramientas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * B160 (16/11/2026, decision de Joel): la columna de cifras de una tabla va a la derecha, con su
 * encabezado y su total, y la UNICA forma de pedirlo es la clase {@code .cc-monto}.
 *
 * <p>El criterio, textual: "columna de cifras en una tabla -&gt; a la derecha, con su encabezado
 * (th) y su total (tfoot). Un monto suelto al lado de su etiqueta (par etiqueta/valor) no se
 * toca". Esta clase no puede verificar ese criterio -- distinguir una columna de un par
 * etiqueta/valor es una decision de pantalla, y la prueba de que quedo bien es que Joel lo vea
 * (DOGMA 1). Lo que SI puede, y es lo que hace falta para que el trabajo no se deshaga solo:</p>
 *
 * <ol>
 *   <li><b>Cerrar la forma vieja.</b> Antes de B160 habia celdas de cifras con el
 *       {@code text-right} de Bootstrap puesto a mano, pantalla por pantalla, y por eso el
 *       sistema tenia dos formas de decir lo mismo. Si manana alguien vuelve a escribir
 *       {@code text-right} en una celda que imprime plata, este test lo frena: es el "un dato, un
 *       dueno" de B88 aplicado al CSS.</li>
 *   <li><b>Que la clase siga existiendo una sola vez</b>, con sus dos declaraciones -- la
 *       alineacion sin {@code tabular-nums} no sirve de nada: la tipografia del sistema trae
 *       digitos de ancho variable, asi que los numeros no se apilan en columna aunque el borde
 *       derecho coincida.</li>
 *   <li><b>Que las pantallas ya alineadas no pierdan celdas</b> en una edicion posterior. El
 *       numero de cada pantalla se conto a mano al aplicar su tanda; si una tanda nueva agrega
 *       celdas a una de estas pantallas, se actualiza el numero aca y se dice en el resumen.</li>
 * </ol>
 */
public class MontosAlineadosTest {

    private static final File CARPETA_VISTAS = new File("src/main/webapp/WEB-INF/vistas");
    private static final File TEMA_CSS = new File("src/main/webapp/recursos/css/sgcpc-tema.css");

    /**
     * Una celda "de cifras" es la que imprime plata: el formateador de guaranies, o un getter
     * cuyo nombre es de dinero. Es el mismo criterio con el que se conto el inventario de B160.
     * No incluye {@code Cuota} ni {@code Cantidad} a proposito: "12 meses" y "3 prestamos" no son
     * importes, y {@code tituloConCantidad} aparece justo en los rotulos de subtotal que SI van
     * alineados a la derecha siendo texto (Morosidad y Cartera).
     */
    private static final Pattern IMPRIME_PLATA = Pattern.compile(
            "(comoGuaranies|formatearGs|conSufijoGuaranies"
            + "|get\\w*(Monto|Importe|Saldo|Total|Efectivo|Vuelto|Capital|Interes|Iva|Deuda|Mora"
            + "|Punitorio|Moratorio|Fondo|Diferencia|Sobrante|Faltante|Pagado|Cobrado)\\w*)\\s*\\(");

    /** Una celda completa, con sus atributos y su contenido (el contenido puede ocupar renglones). */
    private static final Pattern CELDA = Pattern.compile(
            "<(td|th)\\b([^>]*)>(.*?)</\\1>", Pattern.DOTALL);

    /** La forma vieja: el helper de Bootstrap puesto a mano en el atributo class de la celda. */
    private static final Pattern FORMA_VIEJA = Pattern.compile("\\btext-(right|end)\\b");

    /** Los comentarios de JSP se vacian conservando sus renglones, para no correr la numeracion. */
    private static final Pattern COMENTARIO_JSP = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    /**
     * Las pantallas alineadas hasta hoy, con la cantidad de veces que aparece la clase. Cuenta el
     * {@code <td>}, su {@code <th>} (en cuatro tablas el encabezado sale de un arreglo
     * {rotulo, orden, clase}, asi que la clase vive ahi) y el total del {@code <tfoot>}.
     */
    private static final Map<String, Integer> PANTALLAS_ALINEADAS = new LinkedHashMap<String, Integer>();

    static {
        // Tanda 1 (16/11/2026): el circuito de mostrador, los dos resumenes migrados y la pantalla
        // cuyo monto lee pruebas/humo_http.sh.
        PANTALLAS_ALINEADAS.put("desembolso/_resultados.jsp", 2);
        PANTALLAS_ALINEADAS.put("caja/ficha.jsp", 3);
        PANTALLAS_ALINEADAS.put("reportes/caja.jsp", 10);
        PANTALLAS_ALINEADAS.put("aprobaciones/_resultados.jsp", 3);
        PANTALLAS_ALINEADAS.put("solicitudes/_resultados.jsp", 2);
        PANTALLAS_ALINEADAS.put("mora/_resultados.jsp", 2);
        PANTALLAS_ALINEADAS.put("reportes/resumen-financiero.jsp", 13);
        PANTALLAS_ALINEADAS.put("reportes/resumen-mensual.jsp", 7);
        // Tanda 2 (16/11/2026): las fichas y los listados. El inventario de esta tanda se hizo a
        // mano, celda por celda, porque el regex del relevamiento se habia perdido tres celdas
        // (cobranza/ficha:421, formalizacion/ficha:325 y el "Monto a cobrar" de
        // _resultados_busqueda, cuyo getter se llama getACobrarHoy) y marcaba como columna cosas
        // que no lo son: los contadores "cuota X/Y", el Si/No del indicador de mora y los rotulos
        // de las filas de total.
        PANTALLAS_ALINEADAS.put("formalizacion/_resultados.jsp", 6);
        PANTALLAS_ALINEADAS.put("formalizacion/ficha.jsp", 14);
        PANTALLAS_ALINEADAS.put("cobranza/ficha.jsp", 20);
        PANTALLAS_ALINEADAS.put("cobranza/_resultados_busqueda.jsp", 8);
        PANTALLAS_ALINEADAS.put("cotizador/formulario.jsp", 14);
        PANTALLAS_ALINEADAS.put("prestamos/lista.jsp", 10);
        PANTALLAS_ALINEADAS.put("reversiones/bandeja.jsp", 4);
        // Tanda 3 (16/11/2026): los siete informes "A". Quedaron AFUERA a proposito, por
        // no ser columnas de cifras sino frases con un importe adentro, el
        // "N prestamos -- saldo X" de cartera.jsp y los tres "Aprobadas en el periodo: 5
        // (12.000.000 Gs.)" del tfoot de informe-aprobaciones.jsp, que combinan cantidad y
        // monto en una sola celda por RF07.
        PANTALLAS_ALINEADAS.put("reportes/informe-cobros.jsp", 18);
        PANTALLAS_ALINEADAS.put("reportes/cartera.jsp", 8);
        PANTALLAS_ALINEADAS.put("reportes/estado_cuenta_ficha.jsp", 10);
        PANTALLAS_ALINEADAS.put("reportes/informe-aprobaciones.jsp", 4);
        PANTALLAS_ALINEADAS.put("reportes/informe-solicitudes.jsp", 5);
        PANTALLAS_ALINEADAS.put("reportes/informe-desembolsos.jsp", 3);
        PANTALLAS_ALINEADAS.put("reportes/informe-reversiones.jsp", 2);
        // Tanda 4 (16/11/2026): los ultimos informes. Dos hallazgos del inventario a mano:
        // iva.jsp tiene cuatro <td>0 Gs.</td> LITERALES (Gravada 5% e IVA 5%, que hoy son
        // siempre cero) que son parte de las columnas y ningun regex de getters encontraba;
        // y consultas-buro.jsp NO entro porque no tiene ni un monto -- el relevamiento la
        // marcaba por getCantidadTotal, que es una cantidad. Queda afuera, por ser una frase
        // con un importe adentro, el "Saldo X" del pie de incobrables.jsp.
        PANTALLAS_ALINEADAS.put("reportes/iva.jsp", 18);
        PANTALLAS_ALINEADAS.put("reportes/morosidad.jsp", 7);
        PANTALLAS_ALINEADAS.put("reportes/vencimientos.jsp", 7);
        PANTALLAS_ALINEADAS.put("reportes/incobrables.jsp", 2);
        PANTALLAS_ALINEADAS.put("reportes/facturas-anuladas.jsp", 3);
    }

    @Test
    public void laClaseDelMontoEstaDefinidaUnaSolaVezYCompleta() throws IOException {
        String css = leer(TEMA_CSS);
        int definiciones = 0;
        Matcher m = Pattern.compile("\\.cc-monto\\s*\\{([^}]*)\\}").matcher(css);
        String cuerpo = "";
        while (m.find()) {
            definiciones++;
            cuerpo = m.group(1);
        }
        assertEquals("la clase .cc-monto tiene que estar definida UNA sola vez en sgcpc-tema.css",
                1, definiciones);
        assertTrue("a .cc-monto le falta text-align: right", cuerpo.contains("text-align: right"));
        assertTrue("a .cc-monto le falta font-variant-numeric: tabular-nums -- sin eso los digitos"
                + " no se apilan en columna, que es la mitad de para que existe la clase",
                cuerpo.contains("font-variant-numeric: tabular-nums"));
    }

    @Test
    public void ningunaCeldaDeCifrasUsaLaFormaVieja() throws IOException {
        List<String> infractoras = new ArrayList<String>();
        for (File jsp : buscarJsp(CARPETA_VISTAS)) {
            String texto = vaciarComentarios(leer(jsp));
            Matcher m = CELDA.matcher(texto);
            while (m.find()) {
                if (!FORMA_VIEJA.matcher(m.group(2)).find()) {
                    continue;
                }
                if (!IMPRIME_PLATA.matcher(m.group(3)).find()) {
                    continue;   // un rotulo alineado a la derecha (los subtotales de Morosidad)
                }
                infractoras.add(ruta(jsp) + ":" + renglon(texto, m.start())
                        + " -> usa text-right/text-end en una celda de cifras; va .cc-monto");
            }
        }
        assertTrue("B160: la columna de cifras se alinea SOLO con .cc-monto, nunca con el"
                + " text-right de Bootstrap puesto a mano:\n" + String.join("\n", infractoras),
                infractoras.isEmpty());
    }

    @Test
    public void lasPantallasYaAlineadasConservanSusCeldas() throws IOException {
        for (Map.Entry<String, Integer> esperada : PANTALLAS_ALINEADAS.entrySet()) {
            File jsp = new File(CARPETA_VISTAS, esperada.getKey());
            assertTrue("falta la pantalla " + esperada.getKey(), jsp.isFile());
            String texto = leer(jsp);
            int veces = 0;
            int desde = texto.indexOf("cc-monto");
            while (desde >= 0) {
                veces++;
                desde = texto.indexOf("cc-monto", desde + 1);
            }
            assertEquals(esperada.getKey() + ": cambio la cantidad de celdas con .cc-monto."
                    + " Si el cambio es correcto, actualizar el numero en PANTALLAS_ALINEADAS y"
                    + " decirlo en el resumen; si no, la pantalla perdio la alineacion",
                    esperada.getValue().intValue(), veces);
        }
    }

    private static String leer(File archivo) throws IOException {
        return new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
    }

    private static String vaciarComentarios(String texto) {
        Matcher m = COMENTARIO_JSP.matcher(texto);
        StringBuffer salida = new StringBuffer();
        while (m.find()) {
            StringBuilder saltos = new StringBuilder();
            for (int i = 0; i < m.group().length(); i++) {
                if (m.group().charAt(i) == '\n') {
                    saltos.append('\n');
                }
            }
            m.appendReplacement(salida, Matcher.quoteReplacement(saltos.toString()));
        }
        m.appendTail(salida);
        return salida.toString();
    }

    private static int renglon(String texto, int posicion) {
        int n = 1;
        for (int i = 0; i < posicion; i++) {
            if (texto.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
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
