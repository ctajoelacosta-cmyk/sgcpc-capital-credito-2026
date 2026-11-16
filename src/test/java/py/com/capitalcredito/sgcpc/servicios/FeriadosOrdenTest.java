package py.com.capitalcredito.sgcpc.servicios;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Feriado;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * B221 (15/11/2026). El orden con el que /parametros muestra los feriados:
 * {@link ServicioParametros#comparadorFeriados(LocalDate)}. Antes salían de la fecha más vieja a la
 * más nueva, así que con 24 cargados —10 ya pasados, medido en {@code sgcpc_db_pruebas}— la pestaña
 * arrancaba por los que ya no sirven.
 *
 * <p>El comparador recibe {@code hoy} justamente para que estos casos no dependan del día en que se
 * corran: el borde que importa es el feriado de HOY, que tiene que quedar con los próximos.</p>
 */
public class FeriadosOrdenTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);

    private Feriado feriado(String fechaIso, String descripcion) {
        Feriado f = new Feriado();
        f.setFecha(LocalDate.parse(fechaIso));
        f.setDescripcion(descripcion);
        return f;
    }

    private List<String> ordenados(Feriado... feriados) {
        List<Feriado> lista = new ArrayList<>();
        Collections.addAll(lista, feriados);
        Collections.sort(lista, ServicioParametros.comparadorFeriados(HOY));
        List<String> descripciones = new ArrayList<>();
        for (Feriado f : lista) {
            descripciones.add(f.getDescripcion());
        }
        return descripciones;
    }

    @Test
    public void losProximosVanPrimeroYDelMasCercanoAlMasLejano() {
        assertEquals(java.util.Arrays.asList("8 dic", "25 dic", "1 may"),
                ordenados(feriado("2027-05-01", "1 may"),
                        feriado("2026-12-08", "8 dic"),
                        feriado("2026-12-25", "25 dic")));
    }

    @Test
    public void losPasadosVanDespuesYDelMasRecienteAlMasViejo() {
        assertEquals(java.util.Arrays.asList("8 dic", "15 ago", "1 ene"),
                ordenados(feriado("2026-01-01", "1 ene"),
                        feriado("2026-08-15", "15 ago"),
                        feriado("2026-12-08", "8 dic")));
    }

    /** El borde: un feriado que cae HOY todavía importa, así que va con los próximos. */
    @Test
    public void elFeriadoDeHoyVaConLosProximos() {
        assertEquals(java.util.Arrays.asList("hoy", "ayer"),
                ordenados(feriado("2026-11-14", "ayer"), feriado("2026-11-15", "hoy")));
    }

    /** Dos feriados el mismo día: el orden tiene que ser el mismo en cada carga de la pantalla. */
    @Test
    public void dosFeriadosElMismoDiaSeDesempatanPorDescripcion() {
        assertEquals(java.util.Arrays.asList("Asunción", "Fundación"),
                ordenados(feriado("2026-12-08", "Fundación"), feriado("2026-12-08", "Asunción")));
        assertEquals("También entre los pasados.", java.util.Arrays.asList("Carnaval", "Héroes"),
                ordenados(feriado("2026-03-01", "Héroes"), feriado("2026-03-01", "Carnaval")));
    }
}
