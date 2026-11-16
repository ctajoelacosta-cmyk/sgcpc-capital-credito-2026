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
 * B215 (15/11/2026). La pantalla de parametros muestra, debajo del campo "Cambiar", la frase que
 * devuelve {@link ServicioParametros#reglaDeClave(String)}. Esa frase <b>promete una regla</b>, y
 * una promesa sin verificacion es el defecto de C20: el dia que alguien agregue, saque o cambie una
 * validacion cruzada, la pantalla seguiria diciendo lo de antes.
 *
 * <p>Por eso este test no comprueba el texto: comprueba <b>la relacion</b> entre las dos listas,
 * leyendo el propio {@code ServicioParametros.java}. Las claves con regla real son las que
 * aparecen en los dos {@code switch (clave)} de {@code validarFraccionSegunClave} /
 * {@code validarEnteroSegunClave} y en los dos {@code equals(clave)} de
 * {@code validarFechaSegunClave} -- no hay otro {@code switch} ni otro {@code equals(clave)} en el
 * archivo, y el test tambien lo afirma, asi que un tercero no pasa desapercibido.</p>
 *
 * <p>Las dos direcciones importan y agarran errores distintos: una regla sin frase deja a la
 * pantalla sin decir el limite (el defecto que B215 vino a cerrar), y una frase sin regla es la
 * pantalla prometiendo algo que el servidor no valida.</p>
 */
public class ServicioParametrosReglaTest {

    private static final File FUENTE_SERVICIO =
            new File("src/main/java/py/com/capitalcredito/sgcpc/servicios/ServicioParametros.java");

    /** Cantidad de claves con validacion cruzada al 15/11/2026: 15 {@code case} + 2 {@code equals}. */
    private static final int CLAVES_CON_REGLA_ESPERADAS = 17;

    private static final Pattern CLAVE_EN_CASE = Pattern.compile("case\\s+\"([a-z_]+\\.[a-z_]+)\"\\s*:");
    private static final Pattern CLAVE_EN_EQUALS = Pattern.compile("\"([a-z_]+\\.[a-z_]+)\"\\.equals\\(clave\\)");

    private Set<String> clavesConValidacionCruzada() throws IOException {
        assertTrue("No se encontro " + FUENTE_SERVICIO.getPath() + ": correr mvn desde la raiz del proyecto.",
                FUENTE_SERVICIO.isFile());
        String fuente = new String(Files.readAllBytes(FUENTE_SERVICIO.toPath()), StandardCharsets.UTF_8);
        Set<String> claves = new LinkedHashSet<>();
        for (Pattern patron : new Pattern[] {CLAVE_EN_CASE, CLAVE_EN_EQUALS}) {
            Matcher coincidencia = patron.matcher(fuente);
            while (coincidencia.find()) {
                claves.add(coincidencia.group(1));
            }
        }
        return claves;
    }

    @Test
    public void todaClaveConValidacionCruzadaTieneSuFraseEnPantalla() throws IOException {
        for (String clave : clavesConValidacionCruzada()) {
            assertTrue("El parametro \"" + clave + "\" tiene una validacion cruzada en ServicioParametros"
                    + " y ninguna frase en reglaDeClave: la pantalla de /parametros no va a decir su limite"
                    + " y el administrador lo va a descubrir cuando el servidor rechace el cambio (B215).",
                    ServicioParametros.reglaDeClave(clave) != null);
        }
    }

    @Test
    public void ningunaFrasePrometeUnaReglaQueNoExiste() throws IOException {
        Set<String> conValidacion = clavesConValidacionCruzada();
        for (String clave : ServicioParametros.clavesConRegla()) {
            assertTrue("reglaDeClave tiene una frase para \"" + clave + "\", que ya no tiene ninguna"
                    + " validacion cruzada en ServicioParametros: la pantalla estaria prometiendo un limite"
                    + " que el servidor no verifica (C20).",
                    conValidacion.contains(clave));
        }
    }

    /**
     * Si manana se agrega un tercer {@code switch (clave)} o un {@code equals(clave)} en otro
     * metodo, los dos test de arriba lo tomarian como si fuera una validacion cruzada. Este fija
     * cuantas hay hoy: al cambiar, hay que mirar que la cuenta siga queriendo decir lo mismo.
     */
    @Test
    public void laCuentaDeClavesConReglaEsLaEsperada() throws IOException {
        assertEquals("Cambio la cantidad de parametros con validacion cruzada. Si se agrego una regla,"
                + " agregarle su frase en reglaDeClave y actualizar este numero; si se saco, sacar la frase.",
                CLAVES_CON_REGLA_ESPERADAS, clavesConValidacionCruzada().size());
    }
}
