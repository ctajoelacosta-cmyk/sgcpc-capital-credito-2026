package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;

public class FormateadorTextoTest {

    @Test
    public void testComoCedulaConPuntos() {
        assertEquals("1.234.567", FormateadorTexto.comoCedulaConPuntos("1234567"));
    }

    // --- comoListaConEspacios (V1/RF39, pedido de Joel) ---

    @Test
    public void testComoListaConEspaciosEnTextoAgregaEspacioDespuesDeCadaComa() {
        assertEquals("Asunción, Central",
                FormateadorTexto.comoListaConEspacios("Asunción,Central", TipoValorParametro.TEXTO));
        assertEquals("ASALARIADO_SECTOR_PRIVADO, FUNCIONARIO_PUBLICO, OTRO", FormateadorTexto.comoListaConEspacios(
                "ASALARIADO_SECTOR_PRIVADO,FUNCIONARIO_PUBLICO,OTRO", TipoValorParametro.TEXTO));
    }

    /** No debe duplicar el espacio si el valor ya lo tenía (ej. domicilio, "...Gauto, Barrio..."). */
    @Test
    public void testComoListaConEspaciosNoDuplicaEspacioYaExistente() {
        assertEquals("Gauto, Barrio Obrero", FormateadorTexto.comoListaConEspacios("Gauto, Barrio Obrero",
                TipoValorParametro.TEXTO));
    }

    @Test
    public void testComoListaConEspaciosEnDecimalNoCambiaNada() {
        assertEquals("27,39", FormateadorTexto.comoListaConEspacios("27,39", TipoValorParametro.DECIMAL));
    }

    @Test
    public void testComoListaConEspaciosEnOtrosTiposNoCambiaNada() {
        assertEquals("500.000", FormateadorTexto.comoListaConEspacios("500.000", TipoValorParametro.ENTERO));
        assertEquals("2026-10-07", FormateadorTexto.comoListaConEspacios("2026-10-07", TipoValorParametro.FECHA));
    }

    @Test
    public void testComoListaConEspaciosConNullDevuelveNull() {
        assertEquals(null, FormateadorTexto.comoListaConEspacios(null, TipoValorParametro.TEXTO));
    }

    // --- comoDireccionIpLegible (bloque de seguridad, hallazgo de Joel) ---

    @Test
    public void testComoDireccionIpLegibleReconoceElPropioServidor() {
        assertEquals("Este equipo (servidor)", FormateadorTexto.comoDireccionIpLegible("127.0.0.1"));
        assertEquals("Este equipo (servidor)", FormateadorTexto.comoDireccionIpLegible("::1"));
        assertEquals("Este equipo (servidor)", FormateadorTexto.comoDireccionIpLegible("0:0:0:0:0:0:0:1"));
    }

    @Test
    public void testComoDireccionIpLegibleConOtraIpLaDejaTalCual() {
        assertEquals("192.168.1.50", FormateadorTexto.comoDireccionIpLegible("192.168.1.50"));
    }

    @Test
    public void testComoDireccionIpLegibleConNullDevuelveVacio() {
        assertEquals("", FormateadorTexto.comoDireccionIpLegible(null));
    }

    // --- comoNavegadorResumido (bloque de seguridad, hallazgo de Joel) ---

    @Test
    public void testComoNavegadorResumidoReconoceChromeEnWindows() {
        assertEquals("Chrome · Windows", FormateadorTexto.comoNavegadorResumido(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/128.0.0.0 Safari/537.36"));
    }

    @Test
    public void testComoNavegadorResumidoReconoceFirefoxEnLinux() {
        assertEquals("Firefox · Linux", FormateadorTexto.comoNavegadorResumido(
                "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"));
    }

    @Test
    public void testComoNavegadorResumidoDistingueEdgeYOperaDeChrome() {
        assertEquals("Edge · Windows", FormateadorTexto.comoNavegadorResumido(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 "
                        + "Safari/537.36 Edg/128.0.0.0"));
        assertEquals("Opera · Windows", FormateadorTexto.comoNavegadorResumido(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 "
                        + "Safari/537.36 OPR/114.0.0.0"));
    }

    @Test
    public void testComoNavegadorResumidoConNullODesconocidoNoRompe() {
        assertEquals("", FormateadorTexto.comoNavegadorResumido(null));
        assertEquals("", FormateadorTexto.comoNavegadorResumido(""));
        assertEquals("Otro navegador", FormateadorTexto.comoNavegadorResumido("UnBotQueNoDiceNada/1.0"));
    }

    // ==================================================================
    // B188 (15/11/2026): conPuntoFinal. Nació del defecto que vio Joel en pantalla -- el motivo
    // que escribió la cajera ya terminaba en punto y el cartel decía "…a propósito..".
    // El caso del borde es el que importa: el texto YA cerrado.
    // ==================================================================

    @Test
    public void testConPuntoFinalAgregaElPuntoCuandoFalta() {
        assertEquals("Faltante detectado en la prueba de humo.",
                FormateadorTexto.conPuntoFinal("Faltante detectado en la prueba de humo"));
    }

    @Test
    public void testConPuntoFinalNoDuplicaElPuntoQueYaEstaba() {
        assertEquals("Lo dejé así a propósito.", FormateadorTexto.conPuntoFinal("Lo dejé así a propósito."));
    }

    /** Un motivo escrito por una persona puede cerrar de varias formas: ninguna se duplica. */
    @Test
    public void testConPuntoFinalRespetaLosOtrosCierres() {
        assertEquals("¿Faltó plata?", FormateadorTexto.conPuntoFinal("¿Faltó plata?"));
        assertEquals("¡Revisado!", FormateadorTexto.conPuntoFinal("¡Revisado!"));
        assertEquals("Queda pendiente…", FormateadorTexto.conPuntoFinal("Queda pendiente…"));
        assertEquals("Ver detalle:", FormateadorTexto.conPuntoFinal("Ver detalle:"));
    }

    /** Los espacios del final no deben esconder el punto que ya estaba. */
    @Test
    public void testConPuntoFinalIgnoraLosEspaciosDelFinal() {
        assertEquals("Ya estaba cerrado.", FormateadorTexto.conPuntoFinal("Ya estaba cerrado.   "));
        assertEquals("Sin cerrar.", FormateadorTexto.conPuntoFinal("Sin cerrar  "));
    }

    /** Un texto que no existe no se cierra con un punto suelto. */
    @Test
    public void testConPuntoFinalConNullOVacioDevuelveVacio() {
        assertEquals("", FormateadorTexto.conPuntoFinal(null));
        assertEquals("", FormateadorTexto.conPuntoFinal(""));
        assertEquals("", FormateadorTexto.conPuntoFinal("   "));
    }
}
