package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;

/** Matriz de permisos completa del Doc. 00 §3.1. */
public class ServicioAutorizacionTest {

    private final ServicioAutorizacion servicio = new ServicioAutorizacion();

    private void assertNivel(NivelAcceso esperado, PerfilUsuario perfil, Permiso permiso) {
        assertEquals(perfil + " / " + permiso, esperado, servicio.nivelAcceso(perfil, permiso));
    }

    @Test
    public void testCotizarEsParaTodos() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.COTIZAR);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.COTIZAR);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.COTIZAR);
    }

    @Test
    public void testRegistrarEditarCliente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.REGISTRAR_EDITAR_CLIENTE);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.REGISTRAR_EDITAR_CLIENTE);
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.CAJERO, Permiso.REGISTRAR_EDITAR_CLIENTE);
    }

    @Test
    public void testDocumentosYConsultaBuro() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO);
    }

    @Test
    public void testGestionarSolicitudEsSoloDelOficial() {
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.GESTIONAR_SOLICITUD);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.GESTIONAR_SOLICITUD);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.GESTIONAR_SOLICITUD);
    }

    @Test
    public void testAprobarRechazarEsSoloDelGerente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.APROBAR_RECHAZAR_SOLICITUD);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.APROBAR_RECHAZAR_SOLICITUD);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.APROBAR_RECHAZAR_SOLICITUD);
    }

    @Test
    public void testGenerarDocumentosFormalizacion() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.GENERAR_DOCUMENTOS_FORMALIZACION);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.GENERAR_DOCUMENTOS_FORMALIZACION);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.GENERAR_DOCUMENTOS_FORMALIZACION);
    }

    @Test
    public void testAnularPrestamoEsSoloDelGerente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
    }

    @Test
    public void testAbrirCerrarCaja() {
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ABRIR_CERRAR_CAJA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.ABRIR_CERRAR_CAJA);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.ABRIR_CERRAR_CAJA);
    }

    @Test
    public void testEntregarRecibirEfectivoCaja() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA);
    }

    @Test
    public void testDesembolsarEsSoloDeCajero() {
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.DESEMBOLSAR);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.DESEMBOLSAR);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.DESEMBOLSAR);
    }

    @Test
    public void testCobrarEsSoloDeCajero() {
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.COBRAR);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.COBRAR);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.COBRAR);
    }

    /** D-146 (E-5, pedido de Joel): el gerente controla la rendición -- puede ver/reimprimir los
     * PDF de cobranza/desembolso ya emitidos (CONSULTA alcanza, son todas rutas GET), pero sigue
     * sin poder cobrar/desembolsar de verdad (COBRAR/DESEMBOLSAR siguen en NINGUNO para él). */
    @Test
    public void testVerComprobantesCobranzaEsConsultaParaGerenteYCajeroPeroNoParaOficial() {
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.VER_COMPROBANTES_COBRANZA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.VER_COMPROBANTES_COBRANZA);
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.CAJERO, Permiso.VER_COMPROBANTES_COBRANZA);
        // El permiso nuevo es solo para VER -- cobrar/desembolsar de verdad sigue bloqueado.
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.COBRAR);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.DESEMBOLSAR);
    }

    @Test
    public void testSolicitarReversionEsSoloDeCajero() {
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.SOLICITAR_REVERSION);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.SOLICITAR_REVERSION);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.CAJERO, Permiso.SOLICITAR_REVERSION);
    }

    @Test
    public void testAutorizarReversionEsSoloDelGerente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
    }

    /** Decisión 4 (Bloque 4, E8): la cajera queda sin acceso a /mora -- la gestión PAGO_PARCIAL
     * que genera confirmarCobro es una llamada interna, no pasa por este permiso. */
    @Test
    public void testRegistrarGestionCobranzaEsDeOficialYGerente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.REGISTRAR_GESTION_COBRANZA);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.OFICIAL_CREDITO, Permiso.REGISTRAR_GESTION_COBRANZA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.REGISTRAR_GESTION_COBRANZA);
    }

    @Test
    public void testVerPanelReportes() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.VER_PANEL_REPORTES);
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.OFICIAL_CREDITO, Permiso.VER_PANEL_REPORTES);
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.CAJERO, Permiso.VER_PANEL_REPORTES);
    }

    /** Decisión 2 (Bloque 4, E8): /reportes es distinto del Panel -- el oficial entra con
     * CONSULTA (el controlador limita a qué sub-reportes), la cajera no entra. */
    @Test
    public void testVerReportes() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.VER_REPORTES);
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.OFICIAL_CREDITO, Permiso.VER_REPORTES);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.VER_REPORTES);
    }

    /** D-151 (pedido de Joel): deja sin efecto, para este permiso, lo que decidía D7 -- Usuarios
     * pasa a ser exclusivo del administrador (responde legalmente, actúa en presencia del
     * dueño); el gerente queda en NINGUNO. */
    @Test
    public void testAdministrarUsuariosEsExclusivoDelAdministrador() {
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.ADMINISTRAR_USUARIOS);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.ADMINISTRAR_USUARIOS);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS);

        // Lo que de verdad protege FiltroAutorizacion en /usuarios esta etapa:
        org.junit.Assert.assertFalse(servicio.tienePermiso(PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS));
        org.junit.Assert.assertFalse(servicio.tienePermiso(PerfilUsuario.OFICIAL_CREDITO, Permiso.ADMINISTRAR_USUARIOS));
        org.junit.Assert.assertFalse(servicio.tienePermiso(PerfilUsuario.CAJERO, Permiso.ADMINISTRAR_USUARIOS));
        org.junit.Assert.assertTrue(servicio.tieneAccesoTotal(PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS));
    }

    /** D-151 (pedido de Joel): el gerente pasa a CONSULTA en Parámetros (ve valores/historial,
     * nunca modifica); solo el administrador tiene TOTAL. */
    @Test
    public void testAdministrarParametrosEsConsultaParaElGerenteYTotalParaElAdministrador() {
        assertNivel(NivelAcceso.CONSULTA, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.ADMINISTRAR_PARAMETROS);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.ADMINISTRAR_PARAMETROS);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS);

        // Lo que de verdad protege FiltroAutorizacion en /parametros esta etapa: el gerente pasa
        // (GET, autorizaAcceso exige != NINGUNO) pero no puede modificar (POST exige TOTAL).
        org.junit.Assert.assertTrue(servicio.tienePermiso(PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS));
        org.junit.Assert.assertFalse(servicio.tieneAccesoTotal(PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS));
        org.junit.Assert.assertFalse(servicio.tienePermiso(PerfilUsuario.OFICIAL_CREDITO, Permiso.ADMINISTRAR_PARAMETROS));
        org.junit.Assert.assertFalse(servicio.tienePermiso(PerfilUsuario.CAJERO, Permiso.ADMINISTRAR_PARAMETROS));
        org.junit.Assert.assertTrue(servicio.tieneAccesoTotal(PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS));
    }

    @Test
    public void testConsultarAuditoriaEsSoloDelGerente() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.CONSULTAR_AUDITORIA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.OFICIAL_CREDITO, Permiso.CONSULTAR_AUDITORIA);
        assertNivel(NivelAcceso.NINGUNO, PerfilUsuario.CAJERO, Permiso.CONSULTAR_AUDITORIA);
    }

    // ======================================================================
    // D7 (pedido de Joel, anteproyecto entregado): perfil nuevo ADMINISTRADOR, acceso a TODO el
    // sistema -- TOTAL en todos los permisos, sin excepción. GERENTE_ADMINISTRADOR no se toca (de
    // ahí que ninguno de los tests de arriba haya cambiado).
    // ======================================================================

    @Test
    public void testAdministradorTieneTotalEnTodosLosPermisosSinExcepcion() {
        for (Permiso permiso : Permiso.values()) {
            assertNivel(NivelAcceso.TOTAL, PerfilUsuario.ADMINISTRADOR, permiso);
            org.junit.Assert.assertTrue(permiso + ": tieneAccesoTotal debería ser true para ADMINISTRADOR",
                    servicio.tieneAccesoTotal(PerfilUsuario.ADMINISTRADOR, permiso));
            org.junit.Assert.assertTrue(permiso + ": tienePermiso debería ser true para ADMINISTRADOR",
                    servicio.tienePermiso(PerfilUsuario.ADMINISTRADOR, permiso));
        }
    }

    /** D-151 deja sin efecto, solo para Usuarios y Parámetros, lo que decidía D7 para
     * GERENTE_ADMINISTRADOR -- el administrador sigue con TOTAL en los dos (el bucle de
     * construirMatriz le da TOTAL en todo Permiso sin excepción, D7, sin tocar), pero el gerente
     * ya no comparte ese mismo nivel en ninguno de los dos. */
    @Test
    public void testAdministradorSigueConTotalEnAmbosAunqueElGerenteYaNoComparta() {
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS);
        assertNivel(NivelAcceso.TOTAL, PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS);
        org.junit.Assert.assertNotEquals(
                servicio.nivelAcceso(PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS),
                servicio.nivelAcceso(PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_USUARIOS));
        org.junit.Assert.assertNotEquals(
                servicio.nivelAcceso(PerfilUsuario.ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS),
                servicio.nivelAcceso(PerfilUsuario.GERENTE_ADMINISTRADOR, Permiso.ADMINISTRAR_PARAMETROS));
    }
}
