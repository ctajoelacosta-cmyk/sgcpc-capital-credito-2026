package py.com.capitalcredito.sgcpc.servicios;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * B218 (15/11/2026). Cuáles de los parámetros {@code ENTERO} son montos en guaraníes está escrito
 * en <b>dos</b> lugares, y cada uno decide algo distinto: la pantalla
 * ({@code parametros/administracion.jsp}) decide con cuáles pinta el campo con la clase
 * {@code campo-guaranies} --que mete los puntos de miles en el valor mientras se escribe-- y el
 * servicio decide cuáles interpreta con {@code aBigDecimalGuaranies} (que acepta esos puntos) en vez
 * de con {@code aEntero} (que los rechaza).
 *
 * <p>Si las dos listas no dicen lo mismo, el resultado es un parámetro que <b>no se puede
 * guardar</b>: la pantalla manda "1.000" y el servidor contesta <i>"1.000 no es un número entero
 * válido"</i>. Pasó dos veces: con {@code caja.monto_maximo_arqueo} (RF39/D3, 13/10/2026) y con
 * {@code credito.redondeo_efectivo} (B218), y las dos veces quedó escondido porque el valor vigente
 * tenía menos de cuatro cifras.</p>
 *
 * <p>Por eso este test no prueba una lista: prueba <b>que las dos sean iguales</b>, leyendo la de la
 * JSP del archivo. Es la misma idea que {@code ServicioParametrosReglaTest}, con otro par.</p>
 */
public class ParametrosEnGuaraniesTest {

    private static final File FUENTE_JSP =
            new File("src/main/webapp/WEB-INF/vistas/parametros/administracion.jsp");

    /** El inicializador de {@code clavesGuaranies} de la JSP: desde el nombre hasta el cierre. */
    private static final Pattern LISTA_DE_LA_JSP = Pattern.compile(
            "(?s)clavesGuaranies\\s*=.*?Arrays\\.asList\\((.*?)\\)\\s*\\)");

    private static final Pattern CLAVE = Pattern.compile("\"([a-z_]+\\.[a-z_]+)\"");

    private Set<String> clavesDeLaPantalla() throws IOException {
        assertTrue("No se encontró " + FUENTE_JSP.getPath() + ": correr mvn desde la raíz del proyecto.",
                FUENTE_JSP.isFile());
        String fuente = new String(Files.readAllBytes(FUENTE_JSP.toPath()), StandardCharsets.UTF_8);
        Matcher lista = LISTA_DE_LA_JSP.matcher(fuente);
        assertTrue("No se encontró el inicializador de \"clavesGuaranies\" en " + FUENTE_JSP.getPath()
                + ". Si se movió o se armó de otra forma, este test hay que actualizarlo: su trabajo es"
                + " cruzar esa lista con la del servicio (B218).", lista.find());
        Set<String> claves = new LinkedHashSet<>();
        Matcher clave = CLAVE.matcher(lista.group(1));
        while (clave.find()) {
            claves.add(clave.group(1));
        }
        assertTrue("La lista de la pantalla salió vacía: el patrón ya no la reconoce.", !claves.isEmpty());
        return claves;
    }

    @Test
    public void laPantallaYElServicioCoincidenEnQueParametrosSonEnGuaranies() throws IOException {
        Set<String> enLaPantalla = clavesDeLaPantalla();
        Set<String> enElServicio = new LinkedHashSet<>(ServicioParametros.clavesEnteroEnGuaranies());
        assertEquals("La pantalla pinta con puntos de miles un conjunto de parámetros y el servicio"
                + " interpreta otro. El que esté solo en la pantalla NO SE PUEDE GUARDAR si pasa de 999"
                + " (el POST llega \"1.000\" y ConversorNumeros.aEntero lo rechaza); el que esté solo en el"
                + " servicio se escribe sin puntos y se guarda igual, pero la pantalla no ayuda a leerlo"
                + " (B218/RF39 D3).", enLaPantalla, enElServicio);
    }

    /**
     * El valor que motivó B218. No es un caso más de la igualdad de arriba: fija que
     * {@code credito.redondeo_efectivo} está, porque es el que estuvo afuera y el que un barrido
     * futuro podría volver a dejar afuera "por ser chico" (su valor vigente son 50 Gs.).
     */
    @Test
    public void elRedondeoDelEfectivoSeEscribeEnGuaranies() {
        assertTrue("credito.redondeo_efectivo es un monto en guaraníes (D-141): tiene que estar en"
                + " CLAVES_ENTERO_GUARANIES o no se puede subir a 1.000 o más (B218).",
                ServicioParametros.clavesEnteroEnGuaranies().contains("credito.redondeo_efectivo"));
    }
}
