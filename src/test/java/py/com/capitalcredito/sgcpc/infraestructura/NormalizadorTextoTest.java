package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NormalizadorTextoTest {

    @Test
    public void normalizarNombrePropioCapitalizaPalabraSimple() {
        assertEquals("Prueba", NormalizadorTexto.normalizarNombrePropio("prueba"));
        assertEquals("Prueba", NormalizadorTexto.normalizarNombrePropio("PRUEBA"));
    }

    @Test
    public void normalizarNombrePropioCasoDelClienteDePrueba142() {
        assertEquals("Edad Menor", NormalizadorTexto.normalizarNombrePropio("edad menor"));
        assertEquals("Prueba", NormalizadorTexto.normalizarNombrePropio("prueba"));
    }

    @Test
    public void normalizarNombrePropioPreservaTildesYEnie() {
        assertEquals("José Núñez", NormalizadorTexto.normalizarNombrePropio("josé núñez"));
        assertEquals("María Ñandú", NormalizadorTexto.normalizarNombrePropio("MARÍA ÑANDÚ"));
    }

    @Test
    public void normalizarNombrePropioCapitalizaCadaLadoDelGuion() {
        assertEquals("Pérez-López", NormalizadorTexto.normalizarNombrePropio("pérez-lópez"));
        assertEquals("Ana María Gómez-Ruiz", NormalizadorTexto.normalizarNombrePropio("ana maría gómez-ruiz"));
    }

    @Test
    public void normalizarNombrePropioDejaParticulasEnMinusculaSiNoSonLaPrimeraPalabra() {
        assertEquals("Juan de los Santos", NormalizadorTexto.normalizarNombrePropio("juan de los santos"));
        assertEquals("María de la Cruz", NormalizadorTexto.normalizarNombrePropio("maría de la cruz"));
        assertEquals("Juan y Pedro", NormalizadorTexto.normalizarNombrePropio("juan y pedro"));
    }

    @Test
    public void normalizarNombrePropioCapitalizaParticulaCuandoEsLaPrimeraPalabra() {
        // Solo la PRIMERA palabra del texto completo se capitaliza aunque sea una partícula --
        // "los" (segunda palabra) sigue en minúscula, mismo criterio que "Juan de los Santos".
        assertEquals("De los Santos", NormalizadorTexto.normalizarNombrePropio("de los santos"));
    }

    @Test
    public void normalizarNombrePropioRecortaYColapsaEspacios() {
        assertEquals("Juan Pérez", NormalizadorTexto.normalizarNombrePropio("  juan   pérez  "));
    }

    @Test
    public void normalizarNombrePropioNuloODevuelveNulo() {
        assertNull(NormalizadorTexto.normalizarNombrePropio(null));
    }

    @Test
    public void normalizarNombrePropioVacioDevuelveVacio() {
        assertEquals("", NormalizadorTexto.normalizarNombrePropio(""));
        assertEquals("", NormalizadorTexto.normalizarNombrePropio("   "));
    }

    // 13/10/2026 (pedido de Joel): normalizarParaBuscar, para el filtro "Cliente u operación" (RF32).
    @Test
    public void normalizarParaBuscarQuitaTildesYMayusculas() {
        assertEquals("jose perez", NormalizadorTexto.normalizarParaBuscar("José PÉREZ"));
    }

    @Test
    public void normalizarParaBuscarQuitaLaEnie() {
        assertEquals("nunez", NormalizadorTexto.normalizarParaBuscar("Núñez"));
    }

    @Test
    public void normalizarParaBuscarNuloDevuelveVacio() {
        assertEquals("", NormalizadorTexto.normalizarParaBuscar(null));
    }

    @Test
    public void normalizarParaBuscarRecortaEspacios() {
        assertEquals("cc-2026-00001", NormalizadorTexto.normalizarParaBuscar("  CC-2026-00001  "));
    }
}
