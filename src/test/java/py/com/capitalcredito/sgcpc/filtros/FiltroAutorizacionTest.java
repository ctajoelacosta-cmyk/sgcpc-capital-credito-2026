package py.com.capitalcredito.sgcpc.filtros;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;

/**
 * D-146 (E-5, pedido de Joel): el enrutamiento de permisos de FiltroAutorizacion es puro (solo
 * texto/segmentos de ruta, sin servlet ni base) -- se prueba en aislamiento, sin un
 * HttpServletRequest real, mismo criterio que ServicioMotorFinanciero (REGLAS_DESARROLLO.md §7 punto 9).
 */
public class FiltroAutorizacionTest {

    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();

    @Test
    public void testPermisoDeDocumentoReconoceLasRutasDePdfDeCobranzaYDesembolso() {
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA, FiltroAutorizacion.permisoDe("/cobranza/5/recibo/33"));
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA, FiltroAutorizacion.permisoDe("/cobranza/5/factura/12"));
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA, FiltroAutorizacion.permisoDe("/cobranza/5/constancia/7"));
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA,
                FiltroAutorizacion.permisoDe("/cobranza/5/nota-anulacion/9"));
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA, FiltroAutorizacion.permisoDe("/desembolso/5/comprobante"));
        // RF25 (pedido de Joel, 12/10/2026): constancia de reversión, mismo criterio -- el gerente
        // la reimprime desde /reversiones sin tener Permiso.COBRAR.
        assertEquals(Permiso.VER_COMPROBANTES_COBRANZA, FiltroAutorizacion.permisoDe("/cobranza/5/reversion/14"));
    }

    /** El resto de /cobranza y /desembolso sigue exactamente igual que antes de D-146. */
    @Test
    public void testPermisoDeNoCambioParaLasRutasDeCobroYDesembolsoReal() {
        assertEquals(Permiso.COBRAR, FiltroAutorizacion.permisoDe("/cobranza"));
        assertEquals(Permiso.COBRAR, FiltroAutorizacion.permisoDe("/cobranza/5"));
        assertEquals(Permiso.COBRAR, FiltroAutorizacion.permisoDe("/cobranza/5/cobrar"));
        assertEquals(Permiso.COBRAR, FiltroAutorizacion.permisoDe("/cobranza/5/cancelar"));
        assertEquals(Permiso.COBRAR, FiltroAutorizacion.permisoDe("/cobranza/5/anticipar"));
        assertEquals(Permiso.DESEMBOLSAR, FiltroAutorizacion.permisoDe("/desembolso"));
        assertEquals(Permiso.DESEMBOLSAR, FiltroAutorizacion.permisoDe("/desembolso/5"));
        assertEquals(Permiso.DESEMBOLSAR, FiltroAutorizacion.permisoDe("/desembolso/5/confirmar"));
    }

    @Test
    public void testPermisoDeDocumentoNoReconoceUnaRutaSinIdNumericoOConUnSegmentoDistinto() {
        assertNull(FiltroAutorizacion.permisoDeDocumento("/cobranza/abc/recibo/33"));
        assertNull(FiltroAutorizacion.permisoDeDocumento("/cobranza/5/cobrar/33"));
        assertNull(FiltroAutorizacion.permisoDeDocumento("/desembolso/abc/comprobante"));
    }

    @Test
    public void testAutorizaAccesoGetAlcanzaConConsultaYPostExigeTotal() {
        assertTrue(FiltroAutorizacion.autorizaAcceso(NivelAcceso.CONSULTA, true));
        assertTrue(FiltroAutorizacion.autorizaAcceso(NivelAcceso.TOTAL, true));
        assertFalse(FiltroAutorizacion.autorizaAcceso(NivelAcceso.NINGUNO, true));
        assertFalse(FiltroAutorizacion.autorizaAcceso(NivelAcceso.CONSULTA, false));
        assertTrue(FiltroAutorizacion.autorizaAcceso(NivelAcceso.TOTAL, false));
    }

    /**
     * El caso pedido por Joel: el gerente puede hacer GET del PDF del recibo (ve la pantalla de
     * Caja y controla la rendición), pero no puede hacer POST de un cobro -- combinando la
     * matriz real (ServicioAutorizacion) con el enrutamiento real (permisoDe) y la regla de
     * acceso (autorizaAcceso), sin necesitar un HttpServletRequest ni Tomcat.
     */
    @Test
    public void testGerentePuedeGetDelReciboPeroNoPostDeCobro() {
        NivelAcceso nivelDocumento = servicioAutorizacion.nivelAcceso(PerfilUsuario.GERENTE_ADMINISTRADOR,
                FiltroAutorizacion.permisoDe("/cobranza/5/recibo/33"));
        assertTrue("GET /cobranza/5/recibo/33 (gerente)",
                FiltroAutorizacion.autorizaAcceso(nivelDocumento, true));

        NivelAcceso nivelCobro = servicioAutorizacion.nivelAcceso(PerfilUsuario.GERENTE_ADMINISTRADOR,
                FiltroAutorizacion.permisoDe("/cobranza/5/cobrar"));
        assertFalse("POST /cobranza/5/cobrar (gerente)",
                FiltroAutorizacion.autorizaAcceso(nivelCobro, false));
        // Ni siquiera un GET a la ruta de cobro real lo deja pasar -- COBRAR sigue en NINGUNO.
        assertFalse("GET /cobranza/5/cobrar (gerente)",
                FiltroAutorizacion.autorizaAcceso(nivelCobro, true));
    }

    /**
     * B94 (30/10/2026, decisión de Joel): el botón "Comprobante de desembolso" de la ficha de
     * Estado de cuenta pasó a preguntar por el permiso que exige el filtro
     * (VER_COMPROBANTES_COBRANZA) en vez de nombrar el perfil CAJERO a mano, porque el gerente
     * tenía el permiso de la ruta y ninguna pantalla le ofrecía el botón. Este test fija las dos
     * mitades: quiénes ven el botón, y que eso NO abre ninguna otra puerta de /desembolso.
     */
    @Test
    public void testElBotonDelComprobanteSeOfreceSoloAQuienYaPuedeVerloYNoAbreElDesembolso() {
        // Los que ven el botón: los mismos que el filtro ya deja pasar a la ruta del PDF.
        for (PerfilUsuario perfil : new PerfilUsuario[] {PerfilUsuario.GERENTE_ADMINISTRADOR,
                PerfilUsuario.CAJERO, PerfilUsuario.ADMINISTRADOR}) {
            assertTrue("ve el botón y pasa el filtro: " + perfil,
                    servicioAutorizacion.tienePermiso(perfil, Permiso.VER_COMPROBANTES_COBRANZA));
            NivelAcceso nivel = servicioAutorizacion.nivelAcceso(perfil,
                    FiltroAutorizacion.permisoDe("/desembolso/5/comprobante"));
            assertTrue("GET /desembolso/5/comprobante: " + perfil,
                    FiltroAutorizacion.autorizaAcceso(nivel, true));
        }
        // El oficial de crédito no lo ve ni podría abrirlo.
        assertFalse(servicioAutorizacion.tienePermiso(PerfilUsuario.OFICIAL_CREDITO,
                Permiso.VER_COMPROBANTES_COBRANZA));
        assertFalse(FiltroAutorizacion.autorizaAcceso(servicioAutorizacion.nivelAcceso(
                PerfilUsuario.OFICIAL_CREDITO, FiltroAutorizacion.permisoDe("/desembolso/5/comprobante")), true));
        // Y el gerente sigue sin poder entrar al módulo de Desembolso ni confirmar un desembolso:
        // el botón nuevo solo lleva al PDF.
        assertFalse(FiltroAutorizacion.autorizaAcceso(servicioAutorizacion.nivelAcceso(
                PerfilUsuario.GERENTE_ADMINISTRADOR, FiltroAutorizacion.permisoDe("/desembolso/5")), true));
        assertFalse(FiltroAutorizacion.autorizaAcceso(servicioAutorizacion.nivelAcceso(
                PerfilUsuario.GERENTE_ADMINISTRADOR,
                FiltroAutorizacion.permisoDe("/desembolso/5/confirmar")), false));
    }

    /** La cajera no pierde el acceso que ya tenía a estos PDF (antes resuelto por COBRAR/
     * DESEMBOLSAR, ahora por VER_COMPROBANTES_COBRANZA). */
    @Test
    public void testCajeraSigueAccediendoAlReciboYAlComprobanteDeDesembolso() {
        NivelAcceso nivelRecibo = servicioAutorizacion.nivelAcceso(PerfilUsuario.CAJERO,
                FiltroAutorizacion.permisoDe("/cobranza/5/recibo/33"));
        assertTrue(FiltroAutorizacion.autorizaAcceso(nivelRecibo, true));
        NivelAcceso nivelComprobante = servicioAutorizacion.nivelAcceso(PerfilUsuario.CAJERO,
                FiltroAutorizacion.permisoDe("/desembolso/5/comprobante"));
        assertTrue(FiltroAutorizacion.autorizaAcceso(nivelComprobante, true));
    }
}
