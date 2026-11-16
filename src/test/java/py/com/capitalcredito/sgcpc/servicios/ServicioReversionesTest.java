package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion;
import py.com.capitalcredito.sgcpc.excepciones.CierreDocumentalCompletoException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;

/** RF25 (Opción A, pedido de Joel, 12/10/2026): reglas puras de validarPuedeSolicitarReversion. */
public class ServicioReversionesTest {

    private final ServicioReversiones servicio = new ServicioReversiones();

    private static ReversionPago reversionConEstado(EstadoReversion estado) {
        ReversionPago reversion = new ReversionPago();
        reversion.setEstado(estado);
        return reversion;
    }

    /** Punto 1: tras un rechazo, con un motivo nuevo, se puede volver a solicitar -- siempre que
     * siga siendo la última operación del préstamo. */
    @Test
    public void testPuedeVolverASolicitarTrasUnRechazoSiSigueSiendoLaUltimaOperacion() {
        servicio.validarPuedeSolicitarReversion(reversionConEstado(EstadoReversion.RECHAZADA), true, false);
    }

    @Test
    public void testPrimeraSolicitudSinReversionPreviaNoLanza() {
        servicio.validarPuedeSolicitarReversion(null, true, false);
    }

    /** Punto 1: no se puede tener dos solicitudes pendientes del mismo cobro. */
    @Test
    public void testNoPermiteDosSolicitudesPendientesDelMismoPago() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarPuedeSolicitarReversion(reversionConEstado(EstadoReversion.SOLICITADA), true, false));
        org.junit.Assert.assertTrue(error.getMessage().contains("pendiente"));
    }

    @Test
    public void testNoPermiteSolicitarSiYaFueAutorizada() {
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeSolicitarReversion(reversionConEstado(EstadoReversion.AUTORIZADA), true, false));
    }

    /** Punto 1: no se puede resolicitar si ya no es la última operación del préstamo (regla de
     * RF25_reversion_cobro.md §6c), aun viniendo de un rechazo. */
    @Test
    public void testNoPermiteResolicitarSiYaNoEsLaUltimaOperacion() {
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeSolicitarReversion(reversionConEstado(EstadoReversion.RECHAZADA), false, false));
    }

    @Test
    public void testNoPermitePrimeraSolicitudSiYaNoEsLaUltimaOperacion() {
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeSolicitarReversion(null, false, false));
    }

    /** 13/10/2026 (hallazgo de Joel): con el cierre documental ya COMPLETO (pagaré devuelto), no
     * se puede solicitar la reversión, aunque sea la última operación y no haya una previa. */
    @Test
    public void testNoPermiteSolicitarSiElCierreDocumentalYaEstaCompleto() {
        CierreDocumentalCompletoException error = assertThrows(CierreDocumentalCompletoException.class,
                () -> servicio.validarPuedeSolicitarReversion(null, true, true));
        org.junit.Assert.assertTrue(error.getMessage().contains("cierre documental"));
    }

    @Test
    public void testPermiteSolicitarSiElCierreDocumentalNoEstaCompletoAunqueHayaDevolucionGenerada() {
        // El bloqueo es por el estado COMPLETO, no por la sola existencia de la constancia DEV
        // (eso solo dispara el aviso en la bandeja, ver bandeja.jsp) -- "false" acá representa
        // ese caso (DEV generada, cierre todavía PENDIENTE).
        servicio.validarPuedeSolicitarReversion(null, true, false);
    }

    /** RF25, factura (pedido de Joel, 12/10/2026): solicitud de un cobro con factura sin la
     * casilla tildada -- rechazada. */
    @Test
    public void testSolicitarConFacturaSinLaCasillaSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarDeclaracionFacturaCajera(true, false));
        org.junit.Assert.assertTrue(error.getMessage().contains("factura"));
    }

    @Test
    public void testSolicitarConFacturaConLaCasillaNoLanza() {
        servicio.validarDeclaracionFacturaCajera(true, true);
    }

    @Test
    public void testSolicitarSinFacturaNoExigeLaCasilla() {
        servicio.validarDeclaracionFacturaCajera(false, false);
    }

    // ======================================================================
    // C30 (decisión de Joel, 03/11/2026): avisar, no bloquear. Los dos textos son del servicio
    // (un dato, un dueño) y las pantallas los muestran tal cual.
    // ======================================================================

    @Test
    public void testElAvisoNombraElReciboEnDudaYLaConsecuencia() {
        String aviso = ServicioReversiones.textoAvisoDeReversionPendiente("REC-2026-000014");

        assertEquals("Hay una reversión pendiente de autorizar sobre el recibo REC-2026-000014."
                + " Si confirma esta operación, esa reversión ya no podrá autorizarse: el gerente"
                + " solo podrá rechazarla.", aviso);
    }

    @Test
    public void testElAvisoNoPierdeSuPuntoEnElCartel() {
        // El cartel usa como TÍTULO un mensaje de una sola oración y a los títulos les quita el
        // punto final (pie.jsp, dividirEnTituloYDetalle, lección de B42): por eso va en dos
        // oraciones, y la segunda es la que lleva la consecuencia.
        String aviso = ServicioReversiones.textoAvisoDeReversionPendiente("REC-2026-000014");

        assertTrue(aviso.endsWith("."));
        assertFalse(aviso.endsWith(".."));
        int corte = aviso.indexOf(". ");
        assertTrue("una sola oración: el cartel lo mostraría sin el punto",
                corte > 0 && corte < aviso.length() - 2);
        assertTrue("la segunda oración empieza en mayúscula, que es lo que busca el cartel",
                Character.isUpperCase(aviso.charAt(corte + 2)));
    }

    @Test
    public void testElAvisoHablaDeUstedYNoDelSistema() {
        // B92/B96: el texto se le habla al usuario, en su idioma, no en el del modelo de datos.
        String aviso = ServicioReversiones.textoAvisoDeReversionPendiente("REC-2026-000014");

        assertTrue(aviso.contains("Si confirma esta operación"));
        assertFalse(aviso.toLowerCase().contains("esLaUltimaOperacion".toLowerCase()));
        assertFalse(aviso.contains("SOLICITADA"));
    }

    @Test
    public void testLaMarcaDeLaBandejaDiceQueSoloSePuedeRechazar() {
        String marca = ServicioReversiones.textoAvisoOperacionPosterior();

        assertEquals("Ya hay una operación posterior: solo se puede rechazar.", marca);
        assertTrue(marca.endsWith("."));
    }
}
