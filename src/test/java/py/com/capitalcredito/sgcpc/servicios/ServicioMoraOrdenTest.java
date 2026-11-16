package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.FilaMora;

/**
 * B197 (15/11/2026): el orden de la bandeja de /mora.
 *
 * <p>Lo que esta pantalla tiene de propio y hay que cuidar: su orden DE SIEMPRE no es una columna,
 * es una <b>prioridad de trabajo</b> ("promesa vencida primero, despues por dias de atraso
 * descendente") que es lo que promete el subtitulo. Esa sigue siendo la clave por defecto y es
 * tambien el <b>desempate</b> de cualquier otro orden, asi que dos filas con el mismo valor no se
 * intercambian entre dos cargas -- con paginas, eso se ve como una fila repetida o una que
 * desaparece (B157).</p>
 *
 * <p>Y los casos que B175/B177 dejaron escritos: el nulo al final en los DOS sentidos, y la clave
 * desconocida cayendo en el orden de siempre sin llegar a una comparacion.</p>
 */
public class ServicioMoraOrdenTest {

    private final ServicioMora servicio = new ServicioMora();

    @Test
    public void sinOrdenPedidoMandaLaPrioridadDeTrabajo() {
        assertEquals("prioridad", ServicioMora.ordenAplicado(null));
        assertEquals("prioridad", ServicioMora.ordenAplicado(""));
        assertEquals("prioridad", ServicioMora.ORDEN_MORA_POR_DEFECTO);
    }

    /** Lo que viaja del navegador es una CLAVE, no un nombre de columna (B157). */
    @Test
    public void unaClaveDesconocidaOPeligrosaCaeEnElOrdenDeSiempre() {
        assertEquals("prioridad", ServicioMora.ordenAplicado("dias_atraso; DROP TABLE prestamos"));
        assertEquals("prioridad", ServicioMora.ordenAplicado("telefono"));
        assertFalse(ServicioMora.esOrdenPermitido("promesa"));
        assertFalse(ServicioMora.esOrdenPermitido("ultimagestion"));
        assertTrue(ServicioMora.esOrdenPermitido("dias"));
    }

    /**
     * El sentido por defecto depende de la columna: donde "mas" es "mas urgente" (dias, cuotas,
     * monto) el primer clic ordena descendente; en los alfabeticos, ascendente.
     */
    @Test
    public void elSentidoPorDefectoDependeDeLaColumna() {
        assertEquals("desc", ServicioMora.sentidoAplicado("dias", null));
        assertEquals("desc", ServicioMora.sentidoAplicado("cuotas", null));
        assertEquals("desc", ServicioMora.sentidoAplicado("exigible", null));
        assertEquals("asc", ServicioMora.sentidoAplicado("operacion", null));
        assertEquals("asc", ServicioMora.sentidoAplicado("cliente", null));
        // Con sentido explicito, ese -- en las dos familias.
        assertEquals("asc", ServicioMora.sentidoAplicado("dias", "asc"));
        assertEquals("desc", ServicioMora.sentidoAplicado("cliente", "desc"));
    }

    @Test
    public void sinParametrosLaPromesaVencidaVaPrimeroYDespuesElMasAtrasado() {
        FilaMora sinPromesaMuyAtrasado = fila(1L, "CC-1", "Alvarez", "Ana", 90, 3, "900000", false);
        FilaMora conPromesaPocoAtrasado = fila(2L, "CC-2", "Benitez", "Bruno", 5, 1, "100000", true);
        FilaMora sinPromesaPocoAtrasado = fila(3L, "CC-3", "Cardozo", "Carlos", 10, 1, "200000", false);
        List<FilaMora> ordenadas = servicio.ordenarParaBandeja(
                Arrays.asList(sinPromesaPocoAtrasado, sinPromesaMuyAtrasado, conPromesaPocoAtrasado), null, null);
        assertEquals("CC-2", ordenadas.get(0).getIdentificadorOperacion());
        assertEquals("CC-1", ordenadas.get(1).getIdentificadorOperacion());
        assertEquals("CC-3", ordenadas.get(2).getIdentificadorOperacion());
    }

    @Test
    public void ordenarPorDiasAscendenteInvierteElDeSiempre() {
        List<FilaMora> ordenadas = servicio.ordenarParaBandeja(
                Arrays.asList(fila(1L, "CC-90", "Alvarez", "Ana", 90, 3, "900000", false),
                        fila(2L, "CC-5", "Benitez", "Bruno", 5, 1, "100000", false)),
                "dias", "asc");
        assertEquals("CC-5", ordenadas.get(0).getIdentificadorOperacion());
    }

    @Test
    public void elMontoExigibleOrdenaComoNumeroYNoComoTexto() {
        List<FilaMora> ordenadas = servicio.ordenarParaBandeja(
                Arrays.asList(fila(1L, "CC-9", "A", "A", 10, 1, "9000000", false),
                        fila(2L, "CC-10", "B", "B", 10, 1, "10000000", false),
                        fila(3L, "CC-2", "C", "C", 10, 1, "2000000", false)),
                "exigible", "asc");
        assertEquals("CC-2", ordenadas.get(0).getIdentificadorOperacion());
        assertEquals("CC-9", ordenadas.get(1).getIdentificadorOperacion());
        assertEquals("CC-10", ordenadas.get(2).getIdentificadorOperacion());
    }

    /** B177, acá: la fila sin monto exigible va al final en los DOS sentidos. */
    @Test
    public void laFilaSinMontoExigibleVaAlFinalEnLosDosSentidos() {
        FilaMora sinMonto = fila(1L, "CC-SIN", "A", "A", 10, 1, null, false);
        FilaMora conMonto = fila(2L, "CC-CON", "B", "B", 10, 1, "500000", false);
        FilaMora otroConMonto = fila(3L, "CC-CON2", "C", "C", 10, 1, "700000", false);
        List<FilaMora> entrada = Arrays.asList(sinMonto, conMonto, otroConMonto);
        assertEquals("CC-SIN", servicio.ordenarParaBandeja(entrada, "exigible", "asc").get(2).getIdentificadorOperacion());
        assertEquals("CC-SIN", servicio.ordenarParaBandeja(entrada, "exigible", "desc").get(2).getIdentificadorOperacion());
    }

    /** El cliente ordena por apellidos y nombres normalizados: "Ávila" junto a "Avila". */
    @Test
    public void elClienteOrdenaPorApellidoSinQueLaTildeLoMueva() {
        List<FilaMora> ordenadas = servicio.ordenarParaBandeja(
                Arrays.asList(fila(1L, "CC-Z", "Zarate", "Zulema", 10, 1, "100000", false),
                        fila(2L, "CC-A", "Ávila", "Ana", 10, 1, "100000", false),
                        fila(3L, "CC-B", "Benitez", "Bruno", 10, 1, "100000", false)),
                "cliente", "asc");
        assertEquals("CC-A", ordenadas.get(0).getIdentificadorOperacion());
        assertEquals("CC-B", ordenadas.get(1).getIdentificadorOperacion());
        assertEquals("CC-Z", ordenadas.get(2).getIdentificadorOperacion());
    }

    /**
     * El desempate es la prioridad de trabajo: con el mismo monto, primero la promesa vencida y
     * despues el mas atrasado. Sin esto, dos filas iguales se intercambian entre dos cargas y con
     * paginas se ve como una fila repetida o una que falta (B157).
     */
    @Test
    public void dosFilasConElMismoMontoDesempatanPorLaPrioridad() {
        FilaMora conPromesa = fila(1L, "CC-PROMESA", "Zarate", "Zulema", 3, 1, "500000", true);
        FilaMora masAtrasado = fila(2L, "CC-ATRASADO", "Alvarez", "Ana", 40, 2, "500000", false);
        FilaMora menosAtrasado = fila(3L, "CC-NUEVO", "Benitez", "Bruno", 2, 1, "500000", false);
        List<FilaMora> ordenadas = servicio.ordenarParaBandeja(
                Arrays.asList(menosAtrasado, masAtrasado, conPromesa), "exigible", "asc");
        assertEquals("CC-PROMESA", ordenadas.get(0).getIdentificadorOperacion());
        assertEquals("CC-ATRASADO", ordenadas.get(1).getIdentificadorOperacion());
        assertEquals("CC-NUEVO", ordenadas.get(2).getIdentificadorOperacion());
    }

    @Test
    public void ordenarNoModificaLaListaQueRecibeYUnaListaNulaNoRompe() {
        List<FilaMora> entrada = new java.util.ArrayList<>(Arrays.asList(
                fila(1L, "CC-1", "A", "A", 5, 1, "100000", false),
                fila(2L, "CC-2", "B", "B", 50, 2, "200000", false)));
        servicio.ordenarParaBandeja(entrada, "dias", "desc");
        assertEquals("CC-1", entrada.get(0).getIdentificadorOperacion());
        assertTrue(servicio.ordenarParaBandeja(null, "dias", "asc").isEmpty());
    }

    private static FilaMora fila(long idPrestamo, String operacion, String apellidos, String nombres,
            int diasAtraso, int cuotasVencidas, String montoExigible, boolean promesaVencida) {
        return new FilaMora(idPrestamo, operacion, nombres + " " + apellidos, nombres, apellidos,
                "0981000000", "1234567", diasAtraso, cuotasVencidas, Integer.valueOf(1), 12,
                "01/10/2026", montoExigible == null ? null : montoExigible + " Gs.",
                montoExigible == null ? null : new BigDecimal(montoExigible),
                promesaVencida, promesaVencida ? "01/11/2026" : null, promesaVencida ? "100.000 Gs." : null,
                promesaVencida, null, null, null, false, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
