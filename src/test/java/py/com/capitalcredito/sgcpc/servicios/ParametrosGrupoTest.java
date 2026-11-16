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
 * B220 (15/11/2026). La pantalla de parámetros agrupa las 52 filas por el prefijo de la clave y
 * nombra cada grupo con {@link ServicioParametros#grupoDeClave(String)}. Un prefijo sin nombre no
 * rompe nada —cae en el prefijo técnico, a propósito— y justamente por eso <b>nadie se enteraría</b>:
 * la pantalla mostraría "buro" en vez de "Buró de crédito" y el defecto viviría ahí para siempre.
 *
 * <p>Este test lo evita leyendo los prefijos de {@code bd/03_parametros.sql}, que es el archivo con
 * el que nace una base nueva: si alguien agrega una familia de parámetros (por ejemplo
 * {@code notificaciones.*}) y no le pone nombre, falla y lo dice.</p>
 */
public class ParametrosGrupoTest {

    private static final File SEMILLA = new File("bd/03_parametros.sql");

    private static final Pattern CLAVE = Pattern.compile("'([a-z_]+)\\.[a-z_]+'");

    private Set<String> prefijosDeLaSemilla() throws IOException {
        assertTrue("No se encontró " + SEMILLA.getPath() + ": correr mvn desde la raíz del proyecto.",
                SEMILLA.isFile());
        String sql = new String(Files.readAllBytes(SEMILLA.toPath()), StandardCharsets.UTF_8);
        Set<String> prefijos = new LinkedHashSet<>();
        Matcher clave = CLAVE.matcher(sql);
        while (clave.find()) {
            prefijos.add(clave.group(1));
        }
        assertTrue("No se reconoció ninguna clave de parámetro en " + SEMILLA.getPath()
                + ": si el archivo cambió de forma, este test hay que actualizarlo.", !prefijos.isEmpty());
        return prefijos;
    }

    @Test
    public void todoGrupoDeParametrosTieneNombreEnPantalla() throws IOException {
        for (String prefijo : prefijosDeLaSemilla()) {
            assertTrue("El grupo de parámetros \"" + prefijo + "\" no tiene nombre legible en"
                    + " ServicioParametros.grupoDeClave, así que /parametros va a titularlo con el prefijo"
                    + " técnico (B220).", ServicioParametros.prefijosConEtiqueta().contains(prefijo));
        }
    }

    @Test
    public void laClaveSeTraduceAlNombreDeSuGrupo() {
        assertEquals("Política de crédito", ServicioParametros.grupoDeClave("credito.monto_minimo"));
        assertEquals("Buró de crédito", ServicioParametros.grupoDeClave("buro.dias_vigencia_consentimiento"));
    }

    /**
     * El respaldo que hace que un prefijo nuevo no esconda el parámetro: sin nombre, la pantalla
     * muestra el prefijo tal cual. El test de arriba es el que avisa que hay que ponerle nombre.
     */
    @Test
    public void unPrefijoSinNombreCaeEnElPrefijoTecnico() {
        assertEquals("notificaciones", ServicioParametros.grupoDeClave("notificaciones.correo_diario"));
        assertEquals("Una clave sin punto no tiene grupo, y no puede tirar una excepción.",
                "", ServicioParametros.grupoDeClave("suelta"));
        assertEquals("", ServicioParametros.grupoDeClave(null));
    }
}
