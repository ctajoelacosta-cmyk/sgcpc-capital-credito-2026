package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso;
import py.com.capitalcredito.sgcpc.dto.ResultadoAcceso.Tipo;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

public class ServicioAutenticacionTest {

    private static final int LIMITE_INTENTOS = 3;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final String CONTRASENA_CORRECTA = "Correcta@123";

    private final ServicioAutenticacion servicio = new ServicioAutenticacion();

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setNombreUsuario("cajera");
        usuario.setContrasenaHash(BCrypt.hashpw(CONTRASENA_CORRECTA, BCrypt.gensalt(4)));
        usuario.setPerfilUsuario(PerfilUsuario.CAJERO);
        usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
        usuario.setIntentosFallidos(0);
        usuario.setDebeCambiarContrasena(false);
        return usuario;
    }

    @Test
    public void testMismoMensajeParaUsuarioInexistenteYContrasenaIncorrecta() {
        ResultadoAcceso usuarioInexistente = servicio.intentarAcceso(null, "cualquiera", LIMITE_INTENTOS, MINUTOS_BLOQUEO);
        ResultadoAcceso contrasenaIncorrecta = servicio.intentarAcceso(usuarioActivo(), "otra-cosa", LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.CREDENCIALES_INVALIDAS, usuarioInexistente.getTipo());
        assertEquals(Tipo.CREDENCIALES_INVALIDAS, contrasenaIncorrecta.getTipo());
        assertEquals(usuarioInexistente.getMensaje(), contrasenaIncorrecta.getMensaje());
    }

    @Test
    public void testIntentarAccesoConUsuarioInexistenteNuncaTiraNullPointer() {
        // RNF09 (hallazgo de Joel): el chequeo de BCrypt contra el hash dummy también corre con
        // contraseña null (igual que el camino real, que ya convertía null a "").
        servicio.intentarAcceso(null, null, LIMITE_INTENTOS, MINUTOS_BLOQUEO);
    }

    @Test
    public void testCredencialesCorrectasConceden() {
        ResultadoAcceso resultado = servicio.intentarAcceso(usuarioActivo(), CONTRASENA_CORRECTA, LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.ACCESO_CONCEDIDO, resultado.getTipo());
        assertEquals(Integer.valueOf(0), resultado.getIntentosFallidosNuevos());
        assertNull(resultado.getEstadoCuentaNuevo());
    }

    @Test
    public void testDebeCambiarContrasenaEnElPrimerIngreso() {
        Usuario usuario = usuarioActivo();
        usuario.setDebeCambiarContrasena(true);

        ResultadoAcceso resultado = servicio.intentarAcceso(usuario, CONTRASENA_CORRECTA, LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.DEBE_CAMBIAR_CONTRASENA, resultado.getTipo());
    }

    @Test
    public void testCuentaYaBloqueadaNoDejaEntrarAunqueLaContrasenaSeaCorrecta() {
        // Punto 4 del hallazgo (Joel): ni con la contraseña CORRECTA entra, ni se revela que está
        // bloqueada -- mismo mensaje genérico que cualquier otro rechazo.
        Usuario usuario = usuarioActivo();
        usuario.setEstadoCuenta(EstadoCuenta.BLOQUEADA);

        ResultadoAcceso resultado = servicio.intentarAcceso(usuario, CONTRASENA_CORRECTA, LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.CUENTA_BLOQUEADA, resultado.getTipo());
        assertNull(resultado.getIntentosFallidosNuevos());
        assertEquals(ServicioAutenticacion.mensajeAccesoRechazado(LIMITE_INTENTOS, MINUTOS_BLOQUEO), resultado.getMensaje());
    }

    @Test
    public void testCuentaInactivaDaElMensajeGenerico() {
        Usuario usuario = usuarioActivo();
        usuario.setEstadoCuenta(EstadoCuenta.INACTIVA);

        ResultadoAcceso resultado = servicio.intentarAcceso(usuario, CONTRASENA_CORRECTA, LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.CREDENCIALES_INVALIDAS, resultado.getTipo());
        assertEquals(ServicioAutenticacion.mensajeAccesoRechazado(LIMITE_INTENTOS, MINUTOS_BLOQUEO), resultado.getMensaje());
    }

    @Test
    public void testTercerIntentoFallidoBloquea() {
        Usuario usuario = usuarioActivo();
        usuario.setIntentosFallidos(LIMITE_INTENTOS - 1);

        ResultadoAcceso resultado = servicio.intentarAcceso(usuario, "incorrecta", LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.CUENTA_BLOQUEADA, resultado.getTipo());
        assertEquals(Integer.valueOf(LIMITE_INTENTOS), resultado.getIntentosFallidosNuevos());
        assertEquals(EstadoCuenta.BLOQUEADA, resultado.getEstadoCuentaNuevo());
    }

    @Test
    public void testLosCincoCasosDanElMismoMensaje() {
        // RNF09 (hallazgo de Joel): usuario inexistente, contraseña incorrecta, se acaba de
        // bloquear, ya estaba bloqueada, y contraseña correcta pero bloqueada -- los 5 casos que
        // antes se distinguían por el texto. D-45: el límite/los minutos son la política general
        // (el mismo texto para todos), nunca un dato de esta cuenta puntual.
        String mensaje = ServicioAutenticacion.mensajeAccesoRechazado(LIMITE_INTENTOS, MINUTOS_BLOQUEO);
        assertEquals("Usuario o contraseña incorrectos. Por seguridad, después de 3 intentos fallidos la cuenta "
                + "se bloquea por 15 minutos. Si olvidó su contraseña, pídasela a un administrador.", mensaje);

        ResultadoAcceso usuarioInexistente = servicio.intentarAcceso(null, "cualquiera", LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        Usuario conUnIntentoPrevio = usuarioActivo();
        conUnIntentoPrevio.setIntentosFallidos(0);
        ResultadoAcceso contrasenaIncorrecta = servicio.intentarAcceso(conUnIntentoPrevio, "otra-cosa", LIMITE_INTENTOS,
                MINUTOS_BLOQUEO);

        Usuario alBordeDelLimite = usuarioActivo();
        alBordeDelLimite.setIntentosFallidos(LIMITE_INTENTOS - 1);
        ResultadoAcceso seAcabaDeBloquear = servicio.intentarAcceso(alBordeDelLimite, "otra-cosa", LIMITE_INTENTOS,
                MINUTOS_BLOQUEO);

        Usuario yaBloqueada = usuarioActivo();
        yaBloqueada.setEstadoCuenta(EstadoCuenta.BLOQUEADA);
        ResultadoAcceso yaEstabaBloqueada = servicio.intentarAcceso(yaBloqueada, "otra-cosa", LIMITE_INTENTOS,
                MINUTOS_BLOQUEO);
        ResultadoAcceso correctaPeroBloqueada = servicio.intentarAcceso(yaBloqueada, CONTRASENA_CORRECTA, LIMITE_INTENTOS,
                MINUTOS_BLOQUEO);

        assertEquals(mensaje, usuarioInexistente.getMensaje());
        assertEquals(mensaje, contrasenaIncorrecta.getMensaje());
        assertEquals(mensaje, seAcabaDeBloquear.getMensaje());
        assertEquals(mensaje, yaEstabaBloqueada.getMensaje());
        assertEquals(mensaje, correctaPeroBloqueada.getMensaje());
    }

    @Test
    public void testFormatoPluralSingularYPlural() {
        assertEquals("1 minuto", ServicioAutenticacion.formatoPlural(1, "minuto", "minutos"));
        assertEquals("15 minutos", ServicioAutenticacion.formatoPlural(15, "minuto", "minutos"));
        assertEquals("1 intento fallido", ServicioAutenticacion.formatoPlural(1, "intento fallido", "intentos fallidos"));
        assertEquals("3 intentos fallidos", ServicioAutenticacion.formatoPlural(3, "intento fallido", "intentos fallidos"));
    }

    @Test
    public void testMensajeAccesoRechazadoConSingular() {
        assertEquals("Usuario o contraseña incorrectos. Por seguridad, después de 1 intento fallido la cuenta "
                + "se bloquea por 1 minuto. Si olvidó su contraseña, pídasela a un administrador.",
                ServicioAutenticacion.mensajeAccesoRechazado(1, 1));
    }

    @Test
    public void testSegundoIntentoFallidoTodaviaNoBloquea() {
        Usuario usuario = usuarioActivo();
        usuario.setIntentosFallidos(0);

        ResultadoAcceso resultado = servicio.intentarAcceso(usuario, "incorrecta", LIMITE_INTENTOS, MINUTOS_BLOQUEO);

        assertEquals(Tipo.CREDENCIALES_INVALIDAS, resultado.getTipo());
        assertEquals(Integer.valueOf(1), resultado.getIntentosFallidosNuevos());
        assertNull(resultado.getEstadoCuentaNuevo());
    }

    // --- RF36/RF37 (hallazgo de Joel): desbloqueo por tiempo, solo para bloqueos automáticos. ---

    @Test
    public void testSeLiberaPorTiempoSiFueBloqueoAutomaticoYYaPasoElPlazo() {
        LocalDateTime bloqueoAutomatico = LocalDateTime.of(2026, 10, 9, 10, 0);
        LocalDateTime ahora = bloqueoAutomatico.plusMinutes(MINUTOS_BLOQUEO);

        assertTrue(servicio.debeDesbloquearPorTiempo(bloqueoAutomatico, null, ahora, MINUTOS_BLOQUEO));
    }

    @Test
    public void testNoSeLiberaPorTiempoSiTodaviaNoPasoElPlazo() {
        LocalDateTime bloqueoAutomatico = LocalDateTime.of(2026, 10, 9, 10, 0);
        LocalDateTime ahora = bloqueoAutomatico.plusMinutes(MINUTOS_BLOQUEO - 1);

        assertFalse(servicio.debeDesbloquearPorTiempo(bloqueoAutomatico, null, ahora, MINUTOS_BLOQUEO));
    }

    @Test
    public void testNoSeLiberaPorTiempoSiNuncaHuboBloqueoAutomatico() {
        // Bloqueo puramente manual, sin ningún CUENTA_BLOQUEADA en el historial.
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 9, 10, 0);

        assertFalse(servicio.debeDesbloquearPorTiempo(null, LocalDateTime.of(2026, 10, 9, 9, 0), ahora, MINUTOS_BLOQUEO));
        assertFalse(servicio.debeDesbloquearPorTiempo(null, null, ahora, MINUTOS_BLOQUEO));
    }

    @Test
    public void testSecuenciaAutoReactivadoManualNuncaSeLiberaSolo() {
        // Caso pedido por Joel: se bloqueó solo (intentos fallidos), un administrador lo
        // reactivó, y después un administrador lo volvió a bloquear A MANO -- aunque exista un
        // CUENTA_BLOQUEADA viejo en el historial, el bloqueo ACTUAL es el manual (más reciente):
        // nunca se libera solo, sin importar cuánto tiempo pase.
        LocalDateTime bloqueoAutomaticoViejo = LocalDateTime.of(2026, 10, 1, 8, 0);
        // (entre medio: REACTIVAR_USUARIO -- no hace falta pasarlo, no participa de la decisión)
        LocalDateTime bloqueoManualNuevo = LocalDateTime.of(2026, 10, 9, 9, 0);
        LocalDateTime muchoDespues = bloqueoManualNuevo.plusDays(30);

        assertFalse(servicio.debeDesbloquearPorTiempo(bloqueoAutomaticoViejo, bloqueoManualNuevo, muchoDespues,
                MINUTOS_BLOQUEO));
    }

    // --- cambio de contraseña propia (voluntario, reutiliza el mismo servicio que el
    // obligatorio): validarContrasenaActual/validarConfirmacionCoincide son puras. ---

    @Test
    public void testContrasenaActualIncorrectaSeRechaza() {
        String hashActual = BCrypt.hashpw(CONTRASENA_CORRECTA, BCrypt.gensalt(4));
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.validarContrasenaActual("otra-cosa", hashActual));
        assertEquals("La contraseña actual no es correcta.", error.getMessage());
    }

    @Test
    public void testContrasenaActualIncorrectaLanzaElTipoEspecifico() {
        // Bloque de seguridad (hallazgo de Joel): ControladorCambioContrasenaServlet necesita
        // distinguir este caso puntual (para contar intentos y cerrar la sesión a los 3 fallos)
        // de las otras validaciones del cambio de contraseña.
        String hashActual = BCrypt.hashpw(CONTRASENA_CORRECTA, BCrypt.gensalt(4));
        assertThrows(py.com.capitalcredito.sgcpc.excepciones.ContrasenaActualIncorrectaException.class,
                () -> servicio.validarContrasenaActual("otra-cosa", hashActual));
    }

    @Test
    public void testContrasenaActualCorrectaNoLanza() {
        String hashActual = BCrypt.hashpw(CONTRASENA_CORRECTA, BCrypt.gensalt(4));
        servicio.validarContrasenaActual(CONTRASENA_CORRECTA, hashActual);
    }

    @Test
    public void testConfirmacionDistintaSeRechaza() {
        assertThrows(NegocioException.class,
                () -> servicio.validarConfirmacionCoincide("Nueva@2027", "Otra@2027"));
    }

    @Test
    public void testConfirmacionIgualNoLanza() {
        servicio.validarConfirmacionCoincide("Nueva@2027", "Nueva@2027");
    }

    // Nota: "nueva igual a la actual" ya está cubierto por
    // ServicioUsuariosTest.testContrasenaIgualALaActualSeRechaza — es la misma
    // validación (ServicioUsuarios.validarContrasenaNueva) que cambiarContrasenaPropia
    // reutiliza, no se duplica la prueba acá.
}
