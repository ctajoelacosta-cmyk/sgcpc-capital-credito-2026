package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

public class ServicioUsuariosTest {

    private final ServicioUsuarios servicio = new ServicioUsuarios();

    private Usuario usuario(long id, PerfilUsuario perfil, EstadoCuenta estado) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setPerfilUsuario(perfil);
        usuario.setEstadoCuenta(estado);
        return usuario;
    }

    // --- validarContrasenaNueva ---

    @Test
    public void testContrasenaCortaSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("Ab1", null, "cajera"));
    }

    @Test
    public void testContrasenaSinNumeroSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("SoloLetras", null, "cajera"));
    }

    @Test
    public void testContrasenaSinLetraSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("12345678", null, "cajera"));
    }

    @Test
    public void testContrasenaInicialSeRechaza() {
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("Cambiar@2026", null, "cajera"));
    }

    @Test
    public void testContrasenaIgualALaActualSeRechaza() {
        String hashActual = BCrypt.hashpw("Nueva@2027", BCrypt.gensalt(4));
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("Nueva@2027", hashActual, "cajera"));
    }

    @Test
    public void testContrasenaValidaNoLanza() {
        servicio.validarContrasenaNueva("Nueva@2027", null, "cajera");
        // no lanza NegocioException: ok
    }

    @Test
    public void testContrasenaIgualAlNombreDeUsuarioSeRechaza() {
        // Bloque de seguridad (hallazgo de Joel): sin distinguir mayúsculas/minúsculas, mismo
        // criterio que la normalización del nombre de usuario en el login.
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("cajera123", null, "cajera123"));
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("CAJERA123", null, "cajera123"));
    }

    @Test
    public void testContrasenaQueContieneElNombreDeUsuarioSeRechaza() {
        // Hallazgo de Joel probando en pantalla: "cajera123" se aceptaba para el usuario
        // "cajera" porque la regla anterior solo rechazaba la igualdad exacta (ya cubierta de
        // todos modos por el mínimo de 8 caracteres). Ahora se rechaza si la contiene, en
        // cualquier posición y sin distinguir mayúsculas/minúsculas.
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("cajera123", null, "cajera"));
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("CAJERA123", null, "cajera"));
        assertThrows(NegocioException.class, () -> servicio.validarContrasenaNueva("123cajera", null, "cajera"));
        servicio.validarContrasenaNueva("Nueva@2027", null, "cajera"); // no la contiene: no lanza
    }

    // --- generarContrasenaTemporal (bloque de seguridad, hallazgo de Joel) ---

    @Test
    public void testContrasenaTemporalCumpleElFormatoYLaPolitica() {
        String temporal = servicio.generarContrasenaTemporal();
        assertEquals(13, temporal.length());
        org.junit.Assert.assertTrue(temporal.startsWith("Temp"));
        org.junit.Assert.assertTrue(temporal.endsWith("1"));
        // Misma política que validarContrasenaNueva exige (letra + número, 8+ caracteres) --
        // nunca debería rechazarla ella misma.
        servicio.validarContrasenaNueva(temporal, null, "otro_usuario");
    }

    @Test
    public void testContrasenaTemporalNuncaRepiteEntreLlamadas() {
        // No es una prueba estadística de aleatoriedad -- solo confirma que no hay nada fijo
        // (ej. una semilla reusada) que genere siempre el mismo valor.
        java.util.Set<String> generadas = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            generadas.add(servicio.generarContrasenaTemporal());
        }
        assertEquals(20, generadas.size());
    }

    // --- validarPuedeBloquearseOInactivarse ---

    @Test
    public void testNoPuedeBloquearseASiMismo() {
        Usuario gerente = usuario(1L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeBloquearseOInactivarse(gerente, gerente, 2));
    }

    /** D-151 (pedido de Joel): deja sin efecto lo que decidía D7 -- GERENTE_ADMINISTRADOR ya NO
     * cuenta para esta guarda ("no alcanza con un gerente"). Bloquear al único gerente activo del
     * sistema ya no lanza (nada en la base exige que quede un gerente). */
    @Test
    public void testBloquearAlUltimoGerenteActivoYaNoLanza() {
        Usuario gerenteObjetivo = usuario(1L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        Usuario administrador = usuario(2L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        servicio.validarPuedeBloquearseOInactivarse(gerenteObjetivo, administrador, 1);
        // no lanza: el objetivo es gerente, no administrador -- esAdministradorActivo es false.
    }

    @Test
    public void testBloqueoNormalNoLanza() {
        Usuario cajera = usuario(3L, PerfilUsuario.CAJERO, EstadoCuenta.ACTIVA);
        Usuario gerente = usuario(1L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        servicio.validarPuedeBloquearseOInactivarse(cajera, gerente, 1);
        // no lanza: ok
    }

    // ======================================================================
    // D-151 (pedido de Joel): deja sin efecto, para esta guarda, lo que decidía D7 -- ya NO
    // alcanza con un gerente activo, solo ADMINISTRADOR cuenta para "debe quedar al menos uno".
    // ======================================================================

    @Test
    public void testNoPuedeQuedarSinAdministradoresActivos() {
        Usuario administradorObjetivo = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        Usuario otroAdministrador = usuario(2L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeBloquearseOInactivarse(administradorObjetivo, otroAdministrador, 1));
    }

    @Test
    public void testBloquearOtroAdministradorCuandoHayMasDeUnoNoLanza() {
        Usuario administradorObjetivo = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        Usuario otroAdministrador = usuario(2L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        servicio.validarPuedeBloquearseOInactivarse(administradorObjetivo, otroAdministrador, 2);
        // no lanza: ok
    }

    /** D-151: el gerente activo ya NO cuenta para la guarda del administrador -- bloquear al
     * único administrador activo lanza igual, aunque haya un gerente activo en el sistema. */
    @Test
    public void testGerenteActivoYaNoCuentaParaLaGuardaDelAdministrador() {
        Usuario administradorObjetivo = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        Usuario gerenteQueBloquea = usuario(2L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeBloquearseOInactivarse(administradorObjetivo, gerenteQueBloquea, 1));
    }

    // ======================================================================
    // Plan "Editar usuario" (aprobado por Joel): validarDatosBasicos, validarPuedeCambiarPerfil,
    // resolverPerfilAUsar y construirTextoCambios -- todos puros, sin tocar la base.
    // ======================================================================

    // --- validarDatosBasicos (punto 6) ---

    @Test
    public void testNombreVacioSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarDatosBasicos("  ", "Pérez", "1234567", PerfilUsuario.CAJERO));
    }

    @Test
    public void testApellidoVacioSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarDatosBasicos("Ana", "", "1234567", PerfilUsuario.CAJERO));
    }

    @Test
    public void testCedulaConLetrasSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarDatosBasicos("Ana", "Pérez", "12A4567", PerfilUsuario.CAJERO));
    }

    @Test
    public void testPerfilNuloSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarDatosBasicos("Ana", "Pérez", "1234567", null));
    }

    @Test
    public void testDatosBasicosValidosNoLanza() {
        servicio.validarDatosBasicos("Ana", "Pérez", "1234567", PerfilUsuario.CAJERO);
        // no lanza: ok
    }

    // --- validarNombreUsuarioDisponible (hallazgo de Joel probando RF37 en pantalla) ---

    @Test
    public void testNombreUsuarioDuplicadoSeRechaza() {
        Usuario existente = usuario(5L, PerfilUsuario.CAJERO, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class, () -> servicio.validarNombreUsuarioDisponible(existente));
    }

    @Test
    public void testNombreUsuarioDisponibleNoLanza() {
        servicio.validarNombreUsuarioDisponible(null);
        // no lanza: ok
    }

    // --- validarPuedeCambiarPerfil (punto 3) ---

    /** D-151 (pedido de Joel): deja sin efecto lo que decidía D7 -- un gerente nunca cuenta para
     * esta guarda, cambiarle el perfil (aunque sea el único gerente activo) ya no lanza. */
    @Test
    public void testCambiarPerfilDeUnGerenteActivoYaNoLanza() {
        Usuario anterior = usuario(1L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.CAJERO, 0);
        // no lanza: el anterior es gerente, no administrador -- eraAdministradorActivo es false.
    }

    @Test
    public void testNoPuedeSacarAlUltimoAdministradorActivoDeEsePerfil() {
        Usuario anterior = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.CAJERO, 1));
    }

    @Test
    public void testPuedeCambiarPerfilSiHayMasDeUnAdministradorActivo() {
        Usuario anterior = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.CAJERO, 2);
        // no lanza: ok
    }

    /** D-151: a diferencia de D7, pasar de ADMINISTRADOR a GERENTE_ADMINISTRADOR ya NO conserva
     * el conteo -- el gerente no cuenta, así que si era el único administrador activo, se rechaza. */
    @Test
    public void testCambiarDeAdministradorAGerenteSiEraElUnicoActivoLanza() {
        Usuario anterior = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.ACTIVA);
        assertThrows(NegocioException.class,
                () -> servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.GERENTE_ADMINISTRADOR, 1));
    }

    @Test
    public void testCambiarPerfilDeUnoQueNoEraAdministradorActivoNoLanza() {
        Usuario anterior = usuario(1L, PerfilUsuario.OFICIAL_CREDITO, EstadoCuenta.ACTIVA);
        servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.CAJERO, 0);
        // no lanza: nunca fue administrador activo.
    }

    @Test
    public void testCambiarPerfilDeUnAdministradorBloqueadoNoLanza() {
        Usuario anterior = usuario(1L, PerfilUsuario.ADMINISTRADOR, EstadoCuenta.BLOQUEADA);
        servicio.validarPuedeCambiarPerfil(anterior, PerfilUsuario.CAJERO, 1);
        // no lanza: no estaba activo, no cuenta para la regla.
    }

    // --- resolverPerfilAUsar (punto 2, pedido explícito de Joel: "probalo con un test") ---

    @Test
    public void testEditarUnoMismoConservaElPerfilAnteriorAunqueProponganOtro() {
        Usuario anterior = usuario(1L, PerfilUsuario.GERENTE_ADMINISTRADOR, EstadoCuenta.ACTIVA);
        PerfilUsuario resultado = servicio.resolverPerfilAUsar(anterior, PerfilUsuario.CAJERO, 1L);
        assertEquals(PerfilUsuario.GERENTE_ADMINISTRADOR, resultado);
    }

    @Test
    public void testEditarUnoMismoConservaElPerfilAnteriorSiNoLlegaNinguno() {
        // El <select> llega deshabilitado del navegador cuando alguien edita su propio
        // usuario -- ni siquiera se manda el parámetro, así que el perfil propuesto es null.
        Usuario anterior = usuario(1L, PerfilUsuario.OFICIAL_CREDITO, EstadoCuenta.ACTIVA);
        PerfilUsuario resultado = servicio.resolverPerfilAUsar(anterior, null, 1L);
        assertEquals(PerfilUsuario.OFICIAL_CREDITO, resultado);
    }

    @Test
    public void testEditarAOtroUsuarioUsaElPerfilPropuesto() {
        Usuario anterior = usuario(1L, PerfilUsuario.OFICIAL_CREDITO, EstadoCuenta.ACTIVA);
        PerfilUsuario resultado = servicio.resolverPerfilAUsar(anterior, PerfilUsuario.CAJERO, 2L);
        assertEquals(PerfilUsuario.CAJERO, resultado);
    }

    // --- construirTextoCambios (punto 3) ---

    @Test
    public void testConstruirTextoCambiosSinCambiosDaVacio() {
        Usuario anterior = usuario(1L, PerfilUsuario.CAJERO, EstadoCuenta.ACTIVA);
        anterior.setNombres("Ana");
        anterior.setApellidos("Pérez");
        anterior.setCedulaIdentidad("1234567");
        String resultado = servicio.construirTextoCambios(anterior, "Ana", "Pérez", "1234567", PerfilUsuario.CAJERO);
        assertEquals("", resultado);
    }

    @Test
    public void testConstruirTextoCambiosUnSoloCampo() {
        Usuario anterior = usuario(1L, PerfilUsuario.CAJERO, EstadoCuenta.ACTIVA);
        anterior.setNombres("Ana");
        anterior.setApellidos("Pérez");
        anterior.setCedulaIdentidad("1234567");
        String resultado = servicio.construirTextoCambios(anterior, "Ana", "Pérez", "7654321", PerfilUsuario.CAJERO);
        assertEquals("cédula (1234567 → 7654321)", resultado);
    }

    @Test
    public void testConstruirTextoCambiosVariosCamposIncluidoPerfil() {
        Usuario anterior = usuario(1L, PerfilUsuario.OFICIAL_CREDITO, EstadoCuenta.ACTIVA);
        anterior.setNombres("Ana");
        anterior.setApellidos("Pérez");
        anterior.setCedulaIdentidad("1234567");
        String resultado = servicio.construirTextoCambios(anterior, "Ana", "González", "1234567",
                PerfilUsuario.GERENTE_ADMINISTRADOR);
        assertEquals("apellidos (Pérez → González); perfil (Oficial de crédito → Gerente)", resultado);
    }

    // --- recortar (E2, encontrado probando D-151) ---

    @Test
    public void testRecortarQuitaEspaciosAlPrincipioYFinal() {
        assertEquals("Ana", servicio.recortar(" Ana "));
    }

    @Test
    public void testRecortarConNullDevuelveNull() {
        assertEquals(null, servicio.recortar(null));
    }

    /** E2: alta() y actualizar() recortan con este mismo método antes de validar/guardar -- "
     * Ana " queda "Ana", igual en las dos. Pura, sin pasar por alta()/actualizar() (que abren
     * Connection): se prueba la composición recortar + validarDatosBasicos directamente. */
    @Test
    public void testRecortarAntesDeValidarNoRompeConEspaciosDeMas() {
        servicio.validarDatosBasicos(servicio.recortar(" Ana "), servicio.recortar(" Pérez "),
                servicio.recortar(" 1234567 "), PerfilUsuario.CAJERO);
        // no lanza: "Ana"/"Pérez"/"1234567" recortados son válidos.
    }

    // --- cédula con puntos (Tanda 2, 14/10/2026, pedido de Joel) ---

    /** alta()/actualizar() normalizan con ServicioClientes.normalizarCedula (Ajuste B) antes de
     * validar/guardar, igual que ya hace ServicioClientes -- mismo patrón que el test de arriba
     * (recortar + validarDatosBasicos), sin pasar por alta()/actualizar() (que abren Connection). */
    @Test
    public void testCedulaConPuntosNormalizadaPasaLaValidacion() {
        String cedulaNormalizada = new ServicioClientes().normalizarCedula("789.456");
        assertEquals("789456", cedulaNormalizada);
        servicio.validarDatosBasicos("Carlos", "Mancuello", cedulaNormalizada, PerfilUsuario.CAJERO);
        // no lanza: "789456" (sin puntos) es válida.
    }

    /** Confirma el bug que se corrige: SIN normalizar antes, la cédula con puntos se rechaza
     * ("La cédula debe contener solo números") -- por eso alta()/actualizar() deben normalizar
     * primero. */
    @Test
    public void testCedulaConPuntosSinNormalizarSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarDatosBasicos("Carlos", "Mancuello", "789.456", PerfilUsuario.CAJERO));
    }

    /** E2: si la única diferencia con el valor anterior son espacios, construirTextoCambios ya
     * no debe verlo como un cambio real -- mismo cálculo que hace actualizar() tras recortar(). */
    @Test
    public void testSoloEspaciosDeDiferenciaDaNoHuboCambios() {
        Usuario anterior = usuario(1L, PerfilUsuario.CAJERO, EstadoCuenta.ACTIVA);
        anterior.setNombres("Ana");
        anterior.setApellidos("Pérez");
        anterior.setCedulaIdentidad("1234567");
        String resultado = servicio.construirTextoCambios(anterior, servicio.recortar("  Ana "),
                servicio.recortar(" Pérez  "), servicio.recortar(" 1234567 "), PerfilUsuario.CAJERO);
        assertEquals("", resultado);
    }
}
