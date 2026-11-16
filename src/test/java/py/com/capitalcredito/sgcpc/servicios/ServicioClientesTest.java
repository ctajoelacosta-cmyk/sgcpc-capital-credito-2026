package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.ItemExpediente;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PersonaConyuge;
import py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio;
import py.com.capitalcredito.sgcpc.modelos.PersonaLaboral;
import py.com.capitalcredito.sgcpc.modelos.PersonaReferencia;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

public class ServicioClientesTest {

    private final ServicioClientes servicio = new ServicioClientes();

    private Persona personaValida() {
        Persona persona = new Persona();
        persona.setCedulaIdentidad("1234567");
        persona.setNombres("Ana");
        persona.setApellidos("Duarte");
        persona.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        persona.setEstadoCivil("Soltero/a");
        persona.setTelefonoWhatsapp("0981123456");
        // Bloque 7/E10, Tanda A (D-126)
        persona.setFechaVencimientoCedula(LocalDate.of(2030, 1, 1));
        return persona;
    }

    /** Normalización de personas, grupo Laborales (16/10/2026): reemplaza a
     * persona.setSituacionLaboral/setEmpleadorNombre (y el resto de los campos del empleador)/
     * setIngresoNetoComprobado/setPresentaComprobanteIngreso/setOtrosIngresosMensuales/
     * setGastosFijosMensuales/setCuotaOtrasDeudasMensual de personaValida() -- ahora se valida
     * aparte, con ServicioClientes.validarLaboral. */
    private PersonaLaboral laboralValido() {
        PersonaLaboral laboral = new PersonaLaboral();
        laboral.setSituacionLaboral("COMERCIANTE");
        laboral.setIngresoNetoComprobado(new BigDecimal("3000000"));
        laboral.setCuotaOtrasDeudasMensual(BigDecimal.ZERO);
        return laboral;
    }

    /** Normalización de personas, grupo Domicilio (16/10/2026): reemplaza a
     * persona.setIdDepartamento/setIdCiudad/setBarrio/setDireccionDomicilio/setTipoVivienda/
     * setAntiguedadDomicilioAnios de personaValida() -- ahora se valida aparte, con
     * ServicioClientes.validarDomicilio. */
    private PersonaDomicilio domicilioValido() {
        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdCiudad(1L);
        domicilio.setBarrio("Centro");
        domicilio.setDireccionDomicilio("Calle Falsa 123");
        domicilio.setTipoVivienda("PROPIA");
        domicilio.setAntiguedadDomicilioAnios(5);
        return domicilio;
    }

    /** Normalización de personas, grupo Referencias (15/10/2026): reemplaza a
     * persona.setReferenciaNombre/setReferenciaTelefono/setReferencia2Nombre/setReferencia2Telefono
     * de personaValida() -- las dos referencias obligatorias (alta Y edición, pedido de Joel) ahora
     * se validan aparte, con ServicioClientes.validarReferencias. */
    private List<PersonaReferencia> referenciasValidas() {
        List<PersonaReferencia> referencias = new java.util.ArrayList<>();
        PersonaReferencia referencia1 = new PersonaReferencia();
        referencia1.setNumeroReferencia(1);
        referencia1.setNombre("María López");
        referencia1.setTelefono("0981111111");
        referencias.add(referencia1);
        PersonaReferencia referencia2 = new PersonaReferencia();
        referencia2.setNumeroReferencia(2);
        referencia2.setNombre("Juan Pérez");
        referencia2.setTelefono("0981000000");
        referencias.add(referencia2);
        return referencias;
    }

    /** Normalización de personas, grupo Cónyuge (16/10/2026): reemplaza a
     * persona.setConyugeNombre/setConyugeCedula/setConyugeTelefono -- ahora se valida aparte, con
     * ServicioClientes.validarConyuge. */
    private PersonaConyuge conyugeValido() {
        PersonaConyuge conyuge = new PersonaConyuge();
        conyuge.setNombre("Pedro Duarte");
        conyuge.setCedula("2345678");
        conyuge.setTelefono("0981234567");
        return conyuge;
    }

    // --- normalizarCedula (ajuste B) ---

    @Test
    public void testNormalizarCedulaConPuntosEspaciosYGuiones() {
        assertEquals("1234567", servicio.normalizarCedula("1.234.567"));
        assertEquals("1234567", servicio.normalizarCedula(" 1 234 567 "));
        assertEquals("1234567", servicio.normalizarCedula("1234-567"));
    }

    @Test
    public void testNormalizarCedulaYaNormalizadaQuedaIgual() {
        assertEquals("1234567", servicio.normalizarCedula("1234567"));
    }

    // --- validarDatosPersona ---

    @Test
    public void testCedulaConLetrasSeRechaza() {
        Persona persona = personaValida();
        persona.setCedulaIdentidad("12A4567");
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testCedulaVaciaSeRechaza() {
        Persona persona = personaValida();
        persona.setCedulaIdentidad("");
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testNombresVaciosSeRechaza() {
        Persona persona = personaValida();
        persona.setNombres(" ");
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testApellidosVaciosSeRechaza() {
        Persona persona = personaValida();
        persona.setApellidos(null);
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testFechaNacimientoFuturaSeRechaza() {
        Persona persona = personaValida();
        persona.setFechaNacimiento(LocalDate.now().plusDays(1));
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    // --- Bloque 7/E10, Tanda A (D-126): campos nuevos ---

    @Test
    public void testFechaVencimientoCedulaNulaSeRechaza() {
        Persona persona = personaValida();
        persona.setFechaVencimientoCedula(null);
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testConyugeObligatorioSiCasado() {
        assertThrows(NegocioException.class, () -> servicio.validarConyuge("Casado/a", null));
    }

    @Test
    public void testConyugeObligatorioSiUnionDeHecho() {
        assertThrows(NegocioException.class, () -> servicio.validarConyuge("Unión de hecho", null));
    }

    @Test
    public void testConyugeCompletoConCasadoNoLanza() {
        servicio.validarConyuge("Casado/a", conyugeValido());
    }

    @Test
    public void testConyugeNoRequeridoSiSolteroSeDescarta() {
        assertNull(servicio.validarConyuge("Soltero/a", conyugeValido()));
    }

    // --- validarDomicilio (normalización de personas, grupo Domicilio, 16/10/2026 -- antes eran
    // 6 checks adentro de validarDatosTandaA/validarDatosPersona, sobre persona.idDepartamento/
    // idCiudad/barrio/direccionDomicilio/tipoVivienda/antiguedadDomicilioAnios). El de
    // idDepartamento no tiene equivalente puro acá: se validó que la ciudad le pertenezca
    // (validarCiudadPerteneceADepartamento) necesita la Connection, no se puede probar sin la base
    // (REGLAS_DESARROLLO.md §7) -- igual que validarPoliticaCreditoAlta/evaluarPoliticaCredito. ---

    @Test
    public void testIdCiudadNuloSeRechaza() {
        PersonaDomicilio domicilio = domicilioValido();
        domicilio.setIdCiudad(null);
        assertThrows(NegocioException.class, () -> servicio.validarDomicilio(domicilio));
    }

    /** Tanda 2 (14/10/2026, pedido de Joel, RF02 §10): faltaba del lado del servidor -- ya
     * estaba marcada "required" en el HTML, pero sin este chequeo se podía guardar vacía. */
    @Test
    public void testDireccionDomicilioVacioSeRechaza() {
        PersonaDomicilio domicilio = domicilioValido();
        domicilio.setDireccionDomicilio(" ");
        assertThrows(NegocioException.class, () -> servicio.validarDomicilio(domicilio));
    }

    @Test
    public void testBarrioVacioSeRechaza() {
        PersonaDomicilio domicilio = domicilioValido();
        domicilio.setBarrio(" ");
        assertThrows(NegocioException.class, () -> servicio.validarDomicilio(domicilio));
    }

    @Test
    public void testTipoViviendaVacioSeRechaza() {
        PersonaDomicilio domicilio = domicilioValido();
        domicilio.setTipoVivienda(null);
        assertThrows(NegocioException.class, () -> servicio.validarDomicilio(domicilio));
    }

    @Test
    public void testAntiguedadDomicilioNulaSeRechaza() {
        PersonaDomicilio domicilio = domicilioValido();
        domicilio.setAntiguedadDomicilioAnios(null);
        assertThrows(NegocioException.class, () -> servicio.validarDomicilio(domicilio));
    }

    @Test
    public void testDomicilioCompletoNoLanza() {
        servicio.validarDomicilio(domicilioValido());
    }

    // --- validarReferencias (normalización de personas, grupo Referencias, 15/10/2026 -- antes
    // eran 2 checks adentro de validarDatosTandaA/validarDatosPersona, sobre persona.referencia*) ---

    @Test
    public void testReferencia2IncompletaSeRechaza() {
        List<PersonaReferencia> referencias = referenciasValidas();
        referencias.get(1).setTelefono(null);
        assertThrows(NegocioException.class, () -> servicio.validarReferencias(referencias));
    }

    /** Corrección (pedido de Joel): la primera referencia ya no es un dato de política -- es
     * obligatoria igual que la segunda, en alta Y edición (antes solo bloqueaba el alta, vía
     * validarPoliticaCreditoAlta; ahora la valida acá, junto con la segunda). */
    @Test
    public void testReferenciaIncompletaSeRechaza() {
        List<PersonaReferencia> referencias = referenciasValidas();
        referencias.get(0).setNombre(null);
        assertThrows(NegocioException.class, () -> servicio.validarReferencias(referencias));
    }

    @Test
    public void testFaltaUnaReferenciaCompletaSeRechaza() {
        List<PersonaReferencia> referencias = referenciasValidas();
        referencias.remove(1);
        assertThrows(NegocioException.class, () -> servicio.validarReferencias(referencias));
    }

    // --- Precalificación: motivoSinComprobanteSiAplica, único texto y única condición que
    // comparten validarPoliticaCreditoAlta (bloqueo real del alta) y evaluarPrecalificacion
    // (indicador en vivo) -- corrección tras un incidente real: cada una tenía su propia copia
    // literal del mensaje, se actualizó una y la otra quedó mostrando el texto viejo.

    @Test
    public void testMotivoSinComprobanteSiAplicaCuandoFalta() {
        assertEquals(ServicioClientes.MOTIVO_SIN_COMPROBANTE,
                servicio.motivoSinComprobanteSiAplica(false));
    }

    @Test
    public void testMotivoSinComprobanteSiAplicaNullConComprobante() {
        assertNull(servicio.motivoSinComprobanteSiAplica(true));
    }

    // --- RF05/RF06/RF11 (pedido de Joel, 18/10/2026): motivoSinComprobanteSolicitudSiAplica,
    // texto único para los tres momentos de la solicitud (crear, enviar, formalizar) -- distinto
    // del de arriba, que es el del alta del cliente.

    @Test
    public void testMotivoSinComprobanteSolicitudSiAplicaCuandoFalta() {
        assertEquals(ServicioClientes.MOTIVO_SIN_COMPROBANTE_SOLICITUD,
                servicio.motivoSinComprobanteSolicitudSiAplica(false));
    }

    @Test
    public void testMotivoSinComprobanteSolicitudSiAplicaNullConComprobante() {
        assertNull(servicio.motivoSinComprobanteSolicitudSiAplica(true));
    }

    @Test
    public void testTelefonoVacioSeRechaza() {
        Persona persona = personaValida();
        persona.setTelefonoWhatsapp(null);
        assertThrows(NegocioException.class, () -> servicio.validarDatosPersona(persona));
    }

    @Test
    public void testDatosValidosNoLanza() {
        servicio.validarDatosPersona(personaValida());
    }

    // --- validarLaboral (normalización de personas, grupo Laborales, 16/10/2026 -- antes eran
    // checks dentro de validarDatosTandaB/validarDatosPersona, sobre persona.situacionLaboral/
    // empleador*/ingresoNetoComprobado/otrosIngresosMensuales/gastosFijosMensuales/
    // cuotaOtrasDeudasMensual) ---

    @Test
    public void testSituacionLaboralVaciaSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral(null);
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testSituacionLaboralInvalidaSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("VENDEDOR_AMBULANTE");
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testEmpleadorObligatorioSiAsalariado() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("ASALARIADO_SECTOR_PRIVADO");
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testEmpleadorObligatorioSiFuncionarioPublico() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("FUNCIONARIO_PUBLICO");
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testEmpleadorCompletoConAsalariadoNoLanza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("ASALARIADO_SECTOR_PRIVADO");
        laboral.setEmpleadorNombre("Comercial Paraguay S.A.");
        laboral.setEmpleadorDireccion("Avda. España 123");
        laboral.setEmpleadorTelefono("021123456");
        laboral.setCargo("Vendedor");
        laboral.setAntiguedadLaboralAnios(3);
        servicio.validarLaboral(laboral);
    }

    @Test
    public void testEmpleadorNoObligatorioSiIndependiente() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("INDEPENDIENTE");
        servicio.validarLaboral(laboral);
    }

    @Test
    public void testCuotaOtrasDeudasNulaSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setCuotaOtrasDeudasMensual(null);
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testCuotaOtrasDeudasNegativaSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setCuotaOtrasDeudasMensual(new BigDecimal("-1"));
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testOtrosIngresosNegativosSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setOtrosIngresosMensuales(new BigDecimal("-1"));
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testGastosFijosNegativosSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setGastosFijosMensuales(new BigDecimal("-1"));
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testIngresoNegativoSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setIngresoNetoComprobado(new BigDecimal("-1"));
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testLaboralCompletoNoLanza() {
        servicio.validarLaboral(laboralValido());
    }

    // --- Bloque 7/E10 (D-129, Obs. 18): formato del RUC del empleador ---

    @Test
    public void testRucEmpleadorFormatoValidoNoLanza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("INDEPENDIENTE");
        laboral.setEmpleadorRuc("80012345-6");
        servicio.validarLaboral(laboral);
    }

    @Test
    public void testRucEmpleadorSinDigitoVerificadorSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("INDEPENDIENTE");
        laboral.setEmpleadorRuc("80012345");
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testRucEmpleadorConLetrasSeRechaza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("INDEPENDIENTE");
        laboral.setEmpleadorRuc("ABC12345-6");
        assertThrows(NegocioException.class, () -> servicio.validarLaboral(laboral));
    }

    @Test
    public void testRucEmpleadorVacioNoLanza() {
        PersonaLaboral laboral = laboralValido();
        laboral.setSituacionLaboral("INDEPENDIENTE");
        laboral.setEmpleadorRuc(null);
        servicio.validarLaboral(laboral);
    }

    // --- RF03 Parte 3 (pedido de Joel): construirTextoDomicilio ---

    @Test
    public void testConstruirTextoDomicilioConCiudadYDepartamentoDistintos() {
        String resultado = servicio.construirTextoDomicilio("Avda. España 1234", "Centro",
                "Encarnación", "Itapúa", null);
        assertEquals("Avda. España 1234, Centro, Encarnación, Itapúa", resultado);
    }

    @Test
    public void testConstruirTextoDomicilioNombraUnaSolaVezSiCiudadYDepartamentoCoinciden() {
        // RF03 Parte 3: antes salía "Sajonia, Asunción, Asunción" (sin calle) -- Asunción es
        // ciudad Y departamento a la vez.
        String resultado = servicio.construirTextoDomicilio("Avda. Mcal. López 1500", "Sajonia",
                "Asunción", "Asunción", null);
        assertEquals("Avda. Mcal. López 1500, Sajonia, Asunción", resultado);
    }

    @Test
    public void testConstruirTextoDomicilioConReferenciaOpcional() {
        String resultado = servicio.construirTextoDomicilio("Avda. España 1234", "Sajonia",
                "Asunción", "Asunción", "casa celeste, portón negro");
        assertEquals("Avda. España 1234, Sajonia, Asunción (casa celeste, portón negro)", resultado);
    }

    @Test
    public void testConstruirTextoDomicilioSinDireccionNiBarrio() {
        // Defensivo: direccionDomicilio es obligatoria en la ficha, pero el método no debe
        // romperse si llegara vacía (ej. un registro viejo, previo a que existiera el campo).
        String resultado = servicio.construirTextoDomicilio(null, null, "Asunción", "Asunción", null);
        assertEquals("Asunción", resultado);
    }

    @Test
    public void testConstruirTextoDomicilioLimpiaComaDeMasEnElBarrio() {
        // B13 (fase 2): barrio cargado como "Barcequillo," -- no debe salir "Barcequillo,,".
        String resultado = servicio.construirTextoDomicilio("Avda. España 1234", "Barcequillo,",
                "Asunción", "Asunción", null);
        assertEquals("Avda. España 1234, Barcequillo, Asunción", resultado);
    }

    // --- B.15 (consolidación, pedido de Joel): domicilioSinReferencia ---

    @Test
    public void testDomicilioSinReferenciaQuitaElParentesisFinal() {
        String resultado = ServicioClientes.domicilioSinReferencia(
                "Avda. España 1234, Sajonia, Asunción (casa celeste, portón negro)");
        assertEquals("Avda. España 1234, Sajonia, Asunción", resultado);
    }

    @Test
    public void testDomicilioSinReferenciaSinParentesisNoCambiaNada() {
        String resultado = ServicioClientes.domicilioSinReferencia("Avda. España 1234, Sajonia, Asunción");
        assertEquals("Avda. España 1234, Sajonia, Asunción", resultado);
    }

    @Test
    public void testDomicilioSinReferenciaNuloDevuelveNulo() {
        assertNull(ServicioClientes.domicilioSinReferencia(null));
    }

    // --- Bloque 7/E10 (D-129, Obs. 31): camposModificados (detalle de auditoría) ---

    // Normalización de personas, grupo Domicilio (16/10/2026): camposModificados ahora recibe el
    // texto de domicilio ya armado aparte (ver ServicioClientes.actualizar) -- mismo texto en los
    // dos lados en estos tests (salvo el que prueba justo un cambio de domicilio).
    private static final String DOMICILIO_TEXTO_PRUEBA = "Calle Falsa 123, Centro, Asunción";

    @Test
    public void testCamposModificadosDetectaCambios() {
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        nueva.setNombres("Ana María");
        nueva.setTelefonoWhatsapp("0981999999");
        String resultado = servicio.camposModificados(anterior, nueva, DOMICILIO_TEXTO_PRUEBA, DOMICILIO_TEXTO_PRUEBA,
                laboralValido(), laboralValido());
        org.junit.Assert.assertTrue(resultado.contains("nombres"));
        org.junit.Assert.assertTrue(resultado.contains("teléfono"));
        org.junit.Assert.assertFalse(resultado.contains("apellidos"));
    }

    @Test
    public void testCamposModificadosSinCambiosQuedaVacio() {
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        assertEquals("", servicio.camposModificados(anterior, nueva, DOMICILIO_TEXTO_PRUEBA, DOMICILIO_TEXTO_PRUEBA,
                laboralValido(), laboralValido()));
    }

    @Test
    public void testCamposModificadosDetectaCambioDeVencimientoDeCedula() {
        // RF03 (pedido de Joel), Parte 1 -- reprueba: antes este cambio no quedaba en la
        // auditoría (ver actualizar(), que además invalida el escaneo anterior).
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        nueva.setFechaVencimientoCedula(LocalDate.of(2031, 3, 15));
        String resultado = servicio.camposModificados(anterior, nueva, DOMICILIO_TEXTO_PRUEBA, DOMICILIO_TEXTO_PRUEBA,
                laboralValido(), laboralValido());
        org.junit.Assert.assertTrue(resultado.contains("vencimiento de cédula"));
    }

    @Test
    public void testCamposModificadosDetectaCambioDeDomicilio() {
        // Normalización de personas, grupo Domicilio (16/10/2026): el domicilio ya no es un campo
        // de Persona -- camposModificados lo compara igual, recibiendo el texto antes/después ya
        // armado (ver ServicioClientes.actualizar).
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        String resultado = servicio.camposModificados(anterior, nueva, DOMICILIO_TEXTO_PRUEBA,
                "Avda. Mcal. López 1500, Sajonia, Asunción", laboralValido(), laboralValido());
        org.junit.Assert.assertTrue(resultado.contains("domicilio"));
    }

    @Test
    public void testCamposModificadosDetectaCambioDeSituacionLaboral() {
        // Normalización de personas, grupo Laborales (16/10/2026): situación laboral/empleador/
        // ingreso/cuota de otras deudas ya no son campos de Persona -- camposModificados los
        // compara igual, sobre el PersonaLaboral antes/después.
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setSituacionLaboral("EMPLEADO_DOMESTICO");
        String resultado = servicio.camposModificados(anterior, nueva, DOMICILIO_TEXTO_PRUEBA, DOMICILIO_TEXTO_PRUEBA,
                laboralAnterior, laboralNuevo);
        org.junit.Assert.assertTrue(resultado.contains("situación laboral"));
    }

    // --- 2c (pedido de Joel, 17/10/2026): cambiaronDatosDeEvaluacion / mensajeEvaluacionBloqueada ---

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaFechaNacimiento() {
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        nueva.setFechaNacimiento(LocalDate.of(1991, 1, 1));
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(anterior, nueva, laboralValido(), laboralValido(), 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaCiudad() {
        Persona persona = personaValida();
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralValido(), laboralValido(), 1L, 2L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaSituacionLaboral() {
        Persona persona = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setSituacionLaboral("EMPLEADO_DOMESTICO");
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralAnterior, laboralNuevo, 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaPresentaComprobante() {
        Persona persona = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setPresentaComprobanteIngreso(!laboralAnterior.isPresentaComprobanteIngreso());
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralAnterior, laboralNuevo, 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaIngresoNetoComprobado() {
        Persona persona = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setIngresoNetoComprobado(new BigDecimal("4000000"));
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralAnterior, laboralNuevo, 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaCuotaOtrasDeudas() {
        Persona persona = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setCuotaOtrasDeudasMensual(new BigDecimal("250000"));
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralAnterior, laboralNuevo, 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionDetectaEmpleador() {
        Persona persona = personaValida();
        PersonaLaboral laboralAnterior = laboralValido();
        PersonaLaboral laboralNuevo = laboralValido();
        laboralNuevo.setEmpleadorNombre("Otra Empresa S.A.");
        org.junit.Assert.assertTrue(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralAnterior, laboralNuevo, 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionSinCambiosEsFalse() {
        Persona persona = personaValida();
        org.junit.Assert.assertFalse(servicio.cambiaronDatosDeEvaluacion(persona, persona, laboralValido(), laboralValido(), 1L, 1L));
    }

    @Test
    public void testCambiaronDatosDeEvaluacionIgnoraCamposFueraDeLaLista() {
        // Nombre, teléfono, etc. no son datos de evaluación -- aunque cambien, no cuentan.
        Persona anterior = personaValida();
        Persona nueva = personaValida();
        nueva.setNombres("Otro Nombre");
        nueva.setTelefonoWhatsapp("0987000000");
        org.junit.Assert.assertFalse(servicio.cambiaronDatosDeEvaluacion(anterior, nueva, laboralValido(), laboralValido(), 1L, 1L));
    }

    // F2 (21/10/2026, pedido de Joel, punto 7): versión (a) -- sin el permiso -- sin cambios de
    // texto; versión (b) -- con el permiso -- nueva, acción directa por pantalla propia.

    @Test
    public void testMensajeEvaluacionBloqueadaPendienteSinPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0001", "está pendiente de aprobación", "la rechace", "rechazarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, false,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("la solicitud SOL-2026-0001 está pendiente de aprobación"));
        org.junit.Assert.assertTrue(mensaje.contains("informe al gerente para que la rechace, registrando el motivo."));
    }

    @Test
    public void testMensajeEvaluacionBloqueadaPendienteConPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0001", "está pendiente de aprobación", "la rechace", "rechazarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("la solicitud SOL-2026-0001 está pendiente de aprobación"));
        org.junit.Assert.assertTrue(mensaje.contains(
                "Puede rechazarla desde Aprobación, registrando el motivo, si los datos del cliente cambiaron."));
    }

    @Test
    public void testMensajeEvaluacionBloqueadaAprobadaSinPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0002", "ya está aprobada", "la cancele", "cancelarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, false,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("la solicitud SOL-2026-0002 ya está aprobada"));
        org.junit.Assert.assertTrue(mensaje.contains("para que la cancele, registrando el motivo."));
    }

    @Test
    public void testMensajeEvaluacionBloqueadaAprobadaConPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0002", "ya está aprobada", "la cancele", "cancelarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("la solicitud SOL-2026-0002 ya está aprobada"));
        org.junit.Assert.assertTrue(mensaje.contains(
                "Puede cancelarla desde Aprobación, registrando el motivo, si los datos del cliente cambiaron."));
    }

    @Test
    public void testMensajeEvaluacionBloqueadaPrestamoPendienteDesembolsoSinPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "CC-2026-00005", "está pendiente de desembolso", "lo anule", "anularlo", "Formalización",
                Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, false,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("el préstamo CC-2026-00005 está pendiente de desembolso"));
        org.junit.Assert.assertTrue(mensaje.contains("para que lo anule, registrando el motivo."));
    }

    @Test
    public void testMensajeEvaluacionBloqueadaPrestamoPendienteDesembolsoConPermiso() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "CC-2026-00005", "está pendiente de desembolso", "lo anule", "anularlo", "Formalización",
                Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.contains("el préstamo CC-2026-00005 está pendiente de desembolso"));
        org.junit.Assert.assertTrue(mensaje.contains(
                "Puede anularlo desde Formalización, registrando el motivo, si los datos del cliente cambiaron."));
    }

    // --- B91 (hallazgo de Joel del 29/10/2026): el aviso dice CUÁLES son los datos congelados.
    // En la ficha, los que se ven en gris (al lado hay campos que sí se editan); en el rechazo del
    // servidor no hay nada gris que señalar. Una sola redacción, dos arranques. ---

    @Test
    public void testAvisoDeLaFichaHablaDeLosDatosEnGris() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0006", "está pendiente de aprobación", "la rechace", "rechazarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, false,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        org.junit.Assert.assertTrue(mensaje.startsWith("Los datos en gris no se pueden modificar porque "
                + "la solicitud SOL-2026-0006 está pendiente de aprobación."));
        // Lo que sigue es exactamente el texto de siempre.
        org.junit.Assert.assertTrue(mensaje.endsWith("informe al gerente para que la rechace, "
                + "registrando el motivo."));
    }

    @Test
    public void testRechazoDelServidorHablaDeLosDatosDeLaEvaluacion() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "SOL-2026-0006", "está pendiente de aprobación", "la rechace", "rechazarla", "Aprobación",
                Permiso.APROBAR_RECHAZAR_SOLICITUD);
        String mensaje = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.RECHAZO_DEL_SERVIDOR);
        org.junit.Assert.assertTrue(mensaje.startsWith("Los datos de la evaluación no se pueden modificar "
                + "porque la solicitud SOL-2026-0006 está pendiente de aprobación."));
        // En un modal no hay campos a la vista: nunca debe hablar de gris.
        org.junit.Assert.assertFalse(mensaje.contains("gris"));
    }

    @Test
    public void testLosDosAvisosSoloSeDiferencianEnElArranque() {
        ServicioClientes.EvaluacionEnCurso evaluacion = new ServicioClientes.EvaluacionEnCurso(
                "CC-2026-00005", "está pendiente de desembolso", "lo anule", "anularlo", "Formalización",
                Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        String enLaFicha = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS);
        String enElServidor = servicio.mensajeEvaluacionBloqueada(evaluacion, true,
                ServicioClientes.ContextoAvisoEvaluacion.RECHAZO_DEL_SERVIDOR);
        String cola = " no se pueden modificar porque el préstamo CC-2026-00005 está pendiente de "
                + "desembolso. Puede anularlo desde Formalización, registrando el motivo, si los datos "
                + "del cliente cambiaron.";
        assertEquals("Los datos en gris" + cola, enLaFicha);
        assertEquals("Los datos de la evaluación" + cola, enElServidor);
    }

    // --- Bloque 7/E10 (D-130, pedido de Joel): etiqueta legible de situación laboral ---

    @Test
    public void testEtiquetaSituacionLaboralCodigosConocidos() {
        assertEquals("Asalariado (sector privado)", ServicioClientes.etiquetaSituacionLaboral("ASALARIADO_SECTOR_PRIVADO"));
        assertEquals("Funcionario público", ServicioClientes.etiquetaSituacionLaboral("FUNCIONARIO_PUBLICO"));
        assertEquals("Empleado/a doméstico/a", ServicioClientes.etiquetaSituacionLaboral("EMPLEADO_DOMESTICO"));
        assertEquals("Comerciante / negocio propio", ServicioClientes.etiquetaSituacionLaboral("COMERCIANTE"));
    }

    @Test
    public void testEtiquetaSituacionLaboralNuloDaGuion() {
        assertEquals("-", ServicioClientes.etiquetaSituacionLaboral(null));
    }

    @Test
    public void testEtiquetaSituacionLaboralCodigoDesconocidoDevuelveElMismoCodigo() {
        assertEquals("ALGO_INVENTADO", ServicioClientes.etiquetaSituacionLaboral("ALGO_INVENTADO"));
    }

    // --- Bloque 7/E10 (D-132, pedido de Joel): expediente en el orden real del trámite ---

    @Test
    public void testDescripcionComprobanteIngresoSegunSituacionLaboral() {
        assertEquals("Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses).",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("ASALARIADO_SECTOR_PRIVADO"));
        assertEquals("Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses).",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("FUNCIONARIO_PUBLICO"));
        assertEquals("Recibo de haberes jubilatorios (últimos 3 a 6 meses).",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("JUBILADO_PENSIONADO"));
        assertEquals("Declaraciones de IVA o facturas de los últimos 3 a 6 meses, y patente comercial.",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("COMERCIANTE"));
        assertEquals("Extracto de aportes IPS (últimos 3 a 6 meses).",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("EMPLEADO_DOMESTICO"));
        assertEquals("Comprobante de ingreso disponible (a criterio del oficial).",
                servicio.descripcionComprobanteIngresoSegunSituacionLaboral("DESEMPLEADO"));
    }

    private TipoDocumento tipoDocumentoDePrueba(long id, String codigo) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setIdTipoDocumento(id);
        tipo.setCodigoTipo(codigo);
        tipo.setNombreTipo(codigo);
        tipo.setContexto("PERSONA");
        return tipo;
    }

    private List<TipoDocumento> tiposExpedientePersonaDePrueba() {
        return Arrays.asList(
                tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"),
                tipoDocumentoDePrueba(2, "COMPROBANTE_INGRESO"),
                tipoDocumentoDePrueba(3, "COMPROBANTE_DOMICILIO"),
                tipoDocumentoDePrueba(4, "AUTORIZACION_CONSULTA_BURO"),
                tipoDocumentoDePrueba(5, "RESULTADO_CONSULTA_BURO"));
    }

    /** B90 (29/10/2026): las pruebas de abajo buscan su fila por código y no por posición -- el
     * orden de las filas es una decisión de presentación que ya cambió una vez (la autorización
     * pasó de última a primera) y puede volver a cambiar. El orden en sí se prueba aparte, en
     * testConstruirItemsExpedienteOrdenYCantidad. */
    private ItemExpediente itemPorCodigo(List<ItemExpediente> items, String codigoTipo) {
        for (ItemExpediente item : items) {
            if (codigoTipo.equals(item.getTipo().getCodigoTipo())) {
                return item;
            }
        }
        throw new AssertionError("No hay fila del expediente con código " + codigoTipo);
    }

    @Test
    public void testConstruirItemsExpedienteOrdenYCantidad() {
        // Bloque 7/E10 (D-133, pedido de Joel): el informe de Informconf salió de esta lista --
        // el expediente queda en 4 documentos, el 5.º ahora es construirItemInformeBuro.
        // B90 (decisión de Joel, 29/10/2026): la autorización del buró es la FILA 1 -- el buró es
        // el filtro que ahorra tiempo, se pide antes que el resto de los papeles (va con B88).
        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), null, false);
        assertEquals(4, items.size());
        assertEquals("AUTORIZACION_CONSULTA_BURO", items.get(0).getTipo().getCodigoTipo());
        assertEquals("CEDULA_IDENTIDAD", items.get(1).getTipo().getCodigoTipo());
        assertEquals("COMPROBANTE_INGRESO", items.get(2).getTipo().getCodigoTipo());
        assertEquals("COMPROBANTE_DOMICILIO", items.get(3).getTipo().getCodigoTipo());
        for (ItemExpediente item : items) {
            org.junit.Assert.assertFalse(item.isCumplido());
        }
    }

    @Test
    public void testConstruirItemInformeBuro() {
        ItemExpediente itemVacio = servicio.construirItemInformeBuro(tiposExpedientePersonaDePrueba(),
                java.util.Collections.emptyList());
        assertEquals("RESULTADO_CONSULTA_BURO", itemVacio.getTipo().getCodigoTipo());
        assertEquals("Respuesta de Informconf", itemVacio.getEtiqueta());
        org.junit.Assert.assertFalse(itemVacio.isCumplido());

        DocumentoExpediente informeVigente = new DocumentoExpediente();
        informeVigente.setIdDocumento(20L);
        informeVigente.setEstadoDocumento(EstadoDocumento.VIGENTE);
        informeVigente.setTipoDocumento(tipoDocumentoDePrueba(5, "RESULTADO_CONSULTA_BURO"));
        ItemExpediente itemCumplido = servicio.construirItemInformeBuro(tiposExpedientePersonaDePrueba(),
                Arrays.asList(informeVigente));
        org.junit.Assert.assertTrue(itemCumplido.isCumplido());
        assertEquals(Long.valueOf(20L), itemCumplido.getDocumentoVigente().getIdDocumento());
    }

    @Test
    public void testConstruirItemsExpedienteMarcaCumplidoSoloConDocumentoVigente() {
        DocumentoExpediente cedulaVigente = new DocumentoExpediente();
        cedulaVigente.setIdDocumento(10L);
        cedulaVigente.setEstadoDocumento(EstadoDocumento.VIGENTE);
        cedulaVigente.setTipoDocumento(tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"));

        DocumentoExpediente comprobanteReemplazado = new DocumentoExpediente();
        comprobanteReemplazado.setIdDocumento(11L);
        comprobanteReemplazado.setEstadoDocumento(EstadoDocumento.REEMPLAZADO);
        comprobanteReemplazado.setTipoDocumento(tipoDocumentoDePrueba(3, "COMPROBANTE_DOMICILIO"));

        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(cedulaVigente, comprobanteReemplazado),
                LocalDate.of(2030, 1, 1), false);

        assertEquals(true, itemPorCodigo(items, "CEDULA_IDENTIDAD").isCumplido());
        assertEquals(Long.valueOf(10L),
                itemPorCodigo(items, "CEDULA_IDENTIDAD").getDocumentoVigente().getIdDocumento());
        // Un documento REEMPLAZADO (no VIGENTE) no cuenta como cumplido -- hay que reemplazarlo de nuevo.
        assertEquals(false, itemPorCodigo(items, "COMPROBANTE_DOMICILIO").isCumplido());
    }

    @Test
    public void testCedulaVencida() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        org.junit.Assert.assertTrue(servicio.cedulaVencida(LocalDate.of(2026, 9, 1), hoy));
        org.junit.Assert.assertFalse(servicio.cedulaVencida(LocalDate.of(2030, 1, 1), hoy));
        // El mismo día del vencimiento todavía cuenta como vigente (isBefore, no isBefore-o-igual).
        org.junit.Assert.assertFalse(servicio.cedulaVencida(hoy, hoy));
        org.junit.Assert.assertFalse(servicio.cedulaVencida(null, hoy));
    }

    @Test
    public void testConstruirItemsExpedienteFilaCedulaVencida() {
        // RF03 (pedido de Joel), Parte 1: fila 1 en rojo y fuera de "cumplido" aunque el escaneo
        // siga VIGENTE en la tabla -- lo que venció es la cédula real de la persona, no el archivo.
        DocumentoExpediente cedulaVigente = new DocumentoExpediente();
        cedulaVigente.setIdDocumento(10L);
        cedulaVigente.setEstadoDocumento(EstadoDocumento.VIGENTE);
        cedulaVigente.setTipoDocumento(tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"));

        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(cedulaVigente), LocalDate.of(2026, 9, 1), false);

        ItemExpediente itemCedula = itemPorCodigo(items, "CEDULA_IDENTIDAD");
        org.junit.Assert.assertTrue(itemCedula.isVencida());
        assertEquals(false, itemCedula.isCumplido());
        assertEquals(LocalDate.of(2026, 9, 1), itemCedula.getFechaVencimiento());
        // El documento sigue disponible (para "Ver"/"Reemplazar" en la pantalla).
        assertEquals(Long.valueOf(10L), itemCedula.getDocumentoVigente().getIdDocumento());
        // Las demás filas no tienen noción de "vencida".
        org.junit.Assert.assertFalse(itemPorCodigo(items, "COMPROBANTE_INGRESO").isVencida());
    }

    @Test
    public void testConstruirItemsExpedienteFilaCedulaPendienteDeRenovacion() {
        // RF03 (pedido de Joel), Parte 1 -- reprueba: actualizar() marcó REEMPLAZADO el escaneo
        // anterior al cambiar la fecha de vencimiento, sin subir ninguno nuevo todavía -- no hay
        // ningún documento VIGENTE, pero SÍ un REEMPLAZADO de ese tipo en el historial (la señal
        // que lo distingue de "nunca se cargó la cédula").
        DocumentoExpediente cedulaReemplazada = new DocumentoExpediente();
        cedulaReemplazada.setIdDocumento(10L);
        cedulaReemplazada.setEstadoDocumento(EstadoDocumento.REEMPLAZADO);
        cedulaReemplazada.setTipoDocumento(tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"));

        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(cedulaReemplazada), LocalDate.of(2031, 3, 15), false);

        ItemExpediente itemCedula = itemPorCodigo(items, "CEDULA_IDENTIDAD");
        org.junit.Assert.assertTrue(itemCedula.isPendienteRenovacion());
        assertEquals(false, itemCedula.isCumplido());
        org.junit.Assert.assertFalse(itemCedula.isVencida());
        assertEquals(LocalDate.of(2031, 3, 15), itemCedula.getFechaVencimiento());
        assertEquals(null, itemCedula.getDocumentoVigente());
    }

    @Test
    public void testConstruirItemsExpedienteFilaCedulaNuncaCargadaNoEsPendienteDeRenovacion() {
        // Sin ningún documento en el historial (ni VIGENTE ni REEMPLAZADO): "Falta" genérico, no
        // el mensaje específico de renovación.
        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), LocalDate.of(2031, 3, 15), false);

        ItemExpediente itemCedula = itemPorCodigo(items, "CEDULA_IDENTIDAD");
        org.junit.Assert.assertFalse(itemCedula.isPendienteRenovacion());
        assertEquals(false, itemCedula.isCumplido());
    }

    @Test
    public void testConstruirItemsExpedienteDescripcionComprobanteIngresoCambiaSegunSituacion() {
        List<ItemExpediente> itemsAsalariado = servicio.construirItemsExpediente("ASALARIADO_SECTOR_PRIVADO",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), null, false);
        assertEquals("Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses).",
                itemPorCodigo(itemsAsalariado, "COMPROBANTE_INGRESO").getDescripcion());

        List<ItemExpediente> itemsJubilado = servicio.construirItemsExpediente("JUBILADO_PENSIONADO",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), null, false);
        assertEquals("Recibo de haberes jubilatorios (últimos 3 a 6 meses).",
                itemPorCodigo(itemsJubilado, "COMPROBANTE_INGRESO").getDescripcion());
    }

    @Test
    public void testConstruirItemsExpedienteFilaComprobanteIngresoPendienteDeRenovacion() {
        // RF03 (pedido de Joel): mismo agujero que la cédula, ahora con la situación laboral --
        // actualizar() marcó REEMPLAZADO el comprobante anterior al cambiar la situación laboral,
        // sin subir uno nuevo todavía -- no hay ningún documento VIGENTE, pero SÍ un REEMPLAZADO
        // de ese tipo en el historial (la señal que lo distingue de "nunca se cargó el comprobante").
        DocumentoExpediente comprobanteReemplazado = new DocumentoExpediente();
        comprobanteReemplazado.setIdDocumento(11L);
        comprobanteReemplazado.setEstadoDocumento(EstadoDocumento.REEMPLAZADO);
        comprobanteReemplazado.setTipoDocumento(tipoDocumentoDePrueba(2, "COMPROBANTE_INGRESO"));

        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(comprobanteReemplazado), null, false);

        ItemExpediente itemComprobante = itemPorCodigo(items, "COMPROBANTE_INGRESO");
        org.junit.Assert.assertTrue(itemComprobante.isPendienteRenovacion());
        assertEquals(false, itemComprobante.isCumplido());
        org.junit.Assert.assertFalse(itemComprobante.isVencida());
        // A diferencia de la cédula, este renglón no tiene fecha de vencimiento propia.
        assertEquals(null, itemComprobante.getFechaVencimiento());
        assertEquals(null, itemComprobante.getDocumentoVigente());
    }

    @Test
    public void testConstruirItemsExpedienteFilaComprobanteIngresoNuncaCargadoNoEsPendienteDeRenovacion() {
        // Sin ningún documento en el historial (ni VIGENTE ni REEMPLAZADO): "Falta" genérico, no
        // el mensaje específico de cambio de situación laboral.
        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), java.util.Collections.emptyList(), null, false);

        ItemExpediente itemComprobante = itemPorCodigo(items, "COMPROBANTE_INGRESO");
        org.junit.Assert.assertFalse(itemComprobante.isPendienteRenovacion());
        assertEquals(false, itemComprobante.isCumplido());
    }

    @Test
    public void testConstruirItemsExpedienteAutorizacionSoloCuentaConConsentimientoRegistrado() {
        // RF03 (pedido de Joel), punto 2: hallazgo de un PDF falso cargado como autorización desde
        // "Otros documentos" (ya quitado) -- un documento VIGENTE de este tipo no alcanza por sí
        // solo, hace falta además un consentimiento real registrado (ver
        // ServicioConsultaCrediticia.cargarAutorizacionFirmada, que los crea juntos).
        DocumentoExpediente autorizacionVigente = new DocumentoExpediente();
        autorizacionVigente.setIdDocumento(40L);
        autorizacionVigente.setEstadoDocumento(EstadoDocumento.VIGENTE);
        autorizacionVigente.setTipoDocumento(tipoDocumentoDePrueba(4, "AUTORIZACION_CONSULTA_BURO"));

        List<ItemExpediente> itemsSinConsentimiento = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(autorizacionVigente), null, false);
        ItemExpediente itemAutorizacionSinConsentimiento =
                itemPorCodigo(itemsSinConsentimiento, "AUTORIZACION_CONSULTA_BURO");
        org.junit.Assert.assertFalse(itemAutorizacionSinConsentimiento.isCumplido());
        assertEquals(null, itemAutorizacionSinConsentimiento.getDocumentoVigente());

        List<ItemExpediente> itemsConConsentimiento = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedientePersonaDePrueba(), Arrays.asList(autorizacionVigente), null, true);
        ItemExpediente itemAutorizacionConConsentimiento =
                itemPorCodigo(itemsConConsentimiento, "AUTORIZACION_CONSULTA_BURO");
        org.junit.Assert.assertTrue(itemAutorizacionConConsentimiento.isCumplido());
        assertEquals(Long.valueOf(40L), itemAutorizacionConConsentimiento.getDocumentoVigente().getIdDocumento());
    }

    // =====================================================================
    // B88 -- calcularSiguientePaso: el PRIMER paso pendiente del circuito del oficial
    // (pedido explícito de Joel del 28/10/2026; orden "opción B" decidido el 29/10).
    // Un test por cada uno de los 7 casos del orden decidido, sin base: el método es puro.
    // =====================================================================

    /** Los nombres reales de tipos_documento (bd/03_parametros.sql) -- de ahí sale, letra por
     * letra, el "Falta: …" que ve el oficial en pantalla. tiposExpedientePersonaDePrueba() usa el
     * código como nombre, que no sirve para probar el texto. */
    private List<TipoDocumento> tiposExpedienteConNombresReales() {
        return Arrays.asList(
                tipoDocumentoConNombre(1, "CEDULA_IDENTIDAD", "Cédula de identidad"),
                tipoDocumentoConNombre(2, "COMPROBANTE_INGRESO", "Comprobante de ingreso"),
                tipoDocumentoConNombre(3, "COMPROBANTE_DOMICILIO", "Comprobante de domicilio"),
                tipoDocumentoConNombre(4, "AUTORIZACION_CONSULTA_BURO", "Autorización de consulta al buró"));
    }

    private TipoDocumento tipoDocumentoConNombre(long id, String codigo, String nombre) {
        TipoDocumento tipo = tipoDocumentoDePrueba(id, codigo);
        tipo.setNombreTipo(nombre);
        return tipo;
    }

    private DocumentoExpediente documentoVigenteDePrueba(long idDocumento, long idTipo, String codigo) {
        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdDocumento(idDocumento);
        documento.setEstadoDocumento(EstadoDocumento.VIGENTE);
        documento.setTipoDocumento(tipoDocumentoDePrueba(idTipo, codigo));
        return documento;
    }

    /** Los ítems del expediente con los documentos indicados ya cargados (y el consentimiento
     * registrado si entre ellos va la autorización, igual que en la vida real). */
    private List<ItemExpediente> itemsConDocumentos(DocumentoExpediente... documentos) {
        boolean hayAutorizacion = false;
        for (DocumentoExpediente documento : documentos) {
            if ("AUTORIZACION_CONSULTA_BURO".equals(documento.getTipoDocumento().getCodigoTipo())) {
                hayAutorizacion = true;
            }
        }
        return servicio.construirItemsExpediente("COMERCIANTE", tiposExpedienteConNombresReales(),
                Arrays.asList(documentos), LocalDate.of(2030, 1, 1), hayAutorizacion);
    }

    private DocumentoExpediente autorizacionCargada() {
        return documentoVigenteDePrueba(40L, 4, "AUTORIZACION_CONSULTA_BURO");
    }

    private ConsultaCrediticia consultaBuro(boolean conMora) {
        ConsultaCrediticia consulta = new ConsultaCrediticia();
        consulta.setIndicadorMora(conMora);
        return consulta;
    }

    private SolicitudCredito solicitud(long idSolicitud, String codigo, EstadoSolicitud estado) {
        SolicitudCredito solicitudCredito = new SolicitudCredito();
        solicitudCredito.setIdSolicitud(idSolicitud);
        solicitudCredito.setCodigoSolicitud(codigo);
        solicitudCredito.setEstadoSolicitud(estado);
        return solicitudCredito;
    }

    @Test
    public void testPasoGuiadoCaso1FaltaLaAutorizacionFirmada() {
        // Cliente recién registrado: sin ningún documento, lo PRIMERO que se pide es la
        // autorización del buró (opción B) -- no "cargue sus documentos".
        PasoGuiado paso = servicio.calcularSiguientePaso(itemsConDocumentos(), "COMERCIANTE", false, null,
                null, false);
        assertEquals("Siguiente paso: generar la autorización, hacerla firmar y cargarla.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
        assertEquals("② Expediente", paso.getEtiquetaBoton());
        assertEquals("/clientes/7#tabDocumentos", paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoCaso1TambienSiLaAutorizacionVencio() {
        // Cargada y con consentimiento registrado, pero fuera de vigencia: el paso es el mismo
        // (hay que generar una nueva y hacerla firmar de nuevo).
        PasoGuiado paso = servicio.calcularSiguientePaso(itemsConDocumentos(autorizacionCargada()),
                "COMERCIANTE", false, consultaBuro(false), null, false);
        assertEquals("Siguiente paso: generar la autorización, hacerla firmar y cargarla.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoCaso2AutorizacionListaYBuroSinRegistrar() {
        // Con la autorización en mano lo que sigue es el buró, AUNQUE falten los otros 3 papeles:
        // es el filtro que ahorra tiempo (aclaración de Joel: el buró no se bloquea por el
        // expediente). Este es el caso (a) del 28/10, con el orden nuevo.
        PasoGuiado paso = servicio.calcularSiguientePaso(itemsConDocumentos(autorizacionCargada()),
                "COMERCIANTE", true, null, null, false);
        assertEquals("Siguiente paso: registrar el resultado del buró.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.BURO, paso.getDestino());
        assertEquals("③ Consulta al buró", paso.getEtiquetaBoton());
        assertEquals("/clientes/7#tabBuro", paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoCaso3BuroConMoraNoEmpujaANingunLado() {
        // RN-18: no hay nada que completar para subsanarlo -- ni destino ni botón, y el texto es
        // EL MISMO del bloqueo real de enviarAAprobacion (una sola fuente, nunca dos redacciones).
        PasoGuiado paso = servicio.calcularSiguientePaso(itemsConDocumentos(autorizacionCargada()),
                "COMERCIANTE", true, consultaBuro(true), null, false);
        assertEquals(ServicioSolicitudes.MENSAJE_MORA_INFORMCONF, paso.getTexto());
        assertEquals(PasoGuiado.Destino.NINGUNO, paso.getDestino());
        assertNull(paso.getEtiquetaBoton());
        assertNull(paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoCaso4FaltaUnDocumentoDelExpediente() {
        // Buró ya pasado y sin mora: recién ahora se piden los papeles, nombrando el que falta.
        // Este es el caso (b) del 28/10 (antes decía "registrar el buró", ya registrado).
        PasoGuiado paso = servicio.calcularSiguientePaso(
                itemsConDocumentos(autorizacionCargada(),
                        documentoVigenteDePrueba(10L, 1, "CEDULA_IDENTIDAD"),
                        documentoVigenteDePrueba(11L, 2, "COMPROBANTE_INGRESO")),
                "COMERCIANTE", true, consultaBuro(false), null, false);
        assertEquals("Falta: Comprobante de domicilio.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
        assertEquals("② Expediente", paso.getEtiquetaBoton());
    }

    @Test
    public void testPasoGuiadoCaso4ConVariosFaltantesLosNombraATodos() {
        // "Faltan: …" en plural, en el orden de las filas del expediente (B90).
        PasoGuiado paso = servicio.calcularSiguientePaso(
                itemsConDocumentos(autorizacionCargada(), documentoVigenteDePrueba(11L, 2, "COMPROBANTE_INGRESO")),
                "COMERCIANTE", true, consultaBuro(false), null, false);
        assertEquals("Faltan: Cédula de identidad, Comprobante de domicilio.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoCaso5ExpedienteCompletoYSinSolicitud() {
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), null, false);
        assertEquals("Siguiente paso: iniciar una solicitud de crédito.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.SOLICITUD_NUEVA, paso.getDestino());
        assertEquals("④ Solicitud", paso.getEtiquetaBoton());
        assertEquals("/solicitudes/nuevo?cedulaCliente=1234567", paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoCaso6SolicitudEnBorradorListaParaEnviar() {
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.BORRADOR), false);
        assertEquals("Siguiente paso: enviar SOL-2026-0005 a aprobación.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.SOLICITUD_FICHA, paso.getDestino());
        assertEquals("④ SOL-2026-0005", paso.getEtiquetaBoton());
        assertEquals("/solicitudes/55", paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoCaso7SolicitudPendienteDeAprobacion() {
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.PENDIENTE_APROBACION), false);
        assertEquals("Espera la decisión del gerente.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.NINGUNO, paso.getDestino());
        assertNull(paso.getEtiquetaBoton());
    }

    /** Los 4 documentos del expediente cargados (con consentimiento registrado). */
    private List<ItemExpediente> expedienteCompleto() {
        return itemsConDocumentos(autorizacionCargada(),
                documentoVigenteDePrueba(10L, 1, "CEDULA_IDENTIDAD"),
                documentoVigenteDePrueba(11L, 2, "COMPROBANTE_INGRESO"),
                documentoVigenteDePrueba(12L, 3, "COMPROBANTE_DOMICILIO"));
    }

    // --- B88, excepción de la acción previa (decisión de Joel del 29/10/2026, paso 3 de la prueba):
    // cargando papeles no se lo saca del Expediente. ---

    /** Los 3 papeles del expediente cargados menos el domicilio, con la autorización ya firmada. */
    private List<ItemExpediente> expedienteSinDomicilio() {
        return itemsConDocumentos(autorizacionCargada(),
                documentoVigenteDePrueba(10L, 1, "CEDULA_IDENTIDAD"),
                documentoVigenteDePrueba(11L, 2, "COMPROBANTE_INGRESO"));
    }

    @Test
    public void testPasoGuiadoTrasCargarUnDocumentoSeQuedaEnElExpediente() {
        // Con el orden B puro, cada papel que cargaba lo mandaba a ③ ("registrar el buró"). Ahora
        // se queda en ②, dice qué falta y recuerda el buró como pendiente.
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE", true, null,
                null, false, ServicioClientes.AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE);
        assertEquals("Falta: Comprobante de domicilio. Pendiente también: registrar el resultado del buró.",
                paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
        assertEquals("/clientes/7#tabDocumentos", paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoTrasCargarUnDocumentoConElBuroYaRegistradoNoRepiteElBuro() {
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE", true,
                consultaBuro(false), null, false, ServicioClientes.AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE);
        assertEquals("Falta: Comprobante de domicilio.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoTrasCargarLaAutorizacionSigueLlevandoAlBuro() {
        // La excepción NO toca el filtro: cargar la autorización lleva a ③ aunque falten papeles
        // (caso (a) del 28/10).
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE", true, null,
                null, false, ServicioClientes.AccionPrevia.CARGA_AUTORIZACION);
        assertEquals("Siguiente paso: registrar el resultado del buró.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.BURO, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoTrasCargarUnDocumentoConMoraNoInvitaASeguirJuntandoPapeles() {
        // RN-18 manda sobre la excepción: no tiene sentido pedirle más papeles para un crédito que
        // no se va a dar.
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE", true,
                consultaBuro(true), null, false, ServicioClientes.AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE);
        assertEquals(ServicioSolicitudes.MENSAJE_MORA_INFORMCONF, paso.getTexto());
        assertEquals(PasoGuiado.Destino.NINGUNO, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoTrasCargarElUltimoDocumentoSiSigueAlPasoQueCorresponde() {
        // La excepción solo aplica mientras FALTE otro documento. Con el expediente completo, el
        // paso vuelve a ser el del orden B: la solicitud (caso (b) del 28/10).
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.BORRADOR), false,
                ServicioClientes.AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE);
        assertEquals("Siguiente paso: enviar SOL-2026-0005 a aprobación.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.SOLICITUD_FICHA, paso.getDestino());
    }

    // --- B88, hallazgos de la prueba en pantalla del 29/10/2026 ---

    @Test
    public void testPasoGuiadoNoOfreceBotonEnLaPestanaDondeYaEsta() {
        // (a) Parado en ② con un documento pendiente, el botón decía "Siguiente: ② Expediente →":
        // llevaba a donde ya estaba. Ahí no se muestra; en las otras pestañas sí.
        PasoGuiado pasoAlExpediente = servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE",
                true, consultaBuro(false), null, false);
        org.junit.Assert.assertFalse(pasoAlExpediente.ofreceBotonEn(PasoGuiado.Destino.EXPEDIENTE));
        org.junit.Assert.assertTrue(pasoAlExpediente.ofreceBotonEn(PasoGuiado.Destino.DATOS));
        org.junit.Assert.assertTrue(pasoAlExpediente.ofreceBotonEn(PasoGuiado.Destino.BURO));

        // Lo mismo en ③ cuando lo que falta es registrar el resultado del buró.
        PasoGuiado pasoAlBuro = servicio.calcularSiguientePaso(itemsConDocumentos(autorizacionCargada()),
                "COMERCIANTE", true, null, null, false);
        org.junit.Assert.assertFalse(pasoAlBuro.ofreceBotonEn(PasoGuiado.Destino.BURO));
        org.junit.Assert.assertTrue(pasoAlBuro.ofreceBotonEn(PasoGuiado.Destino.EXPEDIENTE));

        // Un paso que lleva a la solicitud se ofrece en las tres pestañas de la ficha.
        PasoGuiado pasoALaSolicitud = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE",
                true, consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.BORRADOR), false);
        org.junit.Assert.assertTrue(pasoALaSolicitud.ofreceBotonEn(PasoGuiado.Destino.DATOS));
        org.junit.Assert.assertTrue(pasoALaSolicitud.ofreceBotonEn(PasoGuiado.Destino.EXPEDIENTE));
        org.junit.Assert.assertTrue(pasoALaSolicitud.ofreceBotonEn(PasoGuiado.Destino.BURO));

        // Sin botón que ofrecer (mora, espera del gerente), no se muestra en ninguna.
        PasoGuiado pasoConMora = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(true), null, false);
        org.junit.Assert.assertFalse(pasoConMora.ofreceBotonEn(PasoGuiado.Destino.DATOS));
        org.junit.Assert.assertFalse(pasoConMora.ofreceBotonEn(PasoGuiado.Destino.BURO));
    }

    @Test
    public void testPasoGuiadoAnclaDeLaPestanaParaLosEnlacesALaFicha() {
        // (b) El enlace "Ver ficha completa / documentos" de la solicitud abre la pestaña del
        // primer paso pendiente; si no hay nada pendiente en la ficha, queda ① Datos (ancla vacía).
        assertEquals("#tabDocumentos", servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE",
                true, consultaBuro(false), null, false).anclaPestanaFicha());
        assertEquals("#tabBuro", servicio.calcularSiguientePaso(itemsConDocumentos(autorizacionCargada()),
                "COMERCIANTE", true, null, null, false).anclaPestanaFicha());
        // Falta la autorización: también es una fila del Expediente (fila 1, B90).
        assertEquals("#tabDocumentos", servicio.calcularSiguientePaso(itemsConDocumentos(), "COMERCIANTE",
                false, null, null, false).anclaPestanaFicha());
        // Lo que sigue es la solicitud, no una pestaña de la ficha.
        assertEquals("", servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.BORRADOR), false)
                .anclaPestanaFicha());
        // Con mora no se lo manda a ninguna pestaña.
        assertEquals("", servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(true), null, false).anclaPestanaFicha());
    }

    @Test
    public void testPasoGuiadoAnclaSirveIgualParaElEnlaceDelGerente() {
        // (c) El "Ver ficha / expediente" de la bandeja de aprobación usa el MISMO ancla, sin
        // ninguna regla propia: el gerente entra con la solicitud ya PENDIENTE_APROBACION, y si al
        // cliente le falta un documento el enlace tiene que abrir ② Expediente igual que el del
        // oficial (decisión de Joel del 29/10/2026).
        SolicitudCredito pendiente = solicitud(55L, "SOL-2026-0006", EstadoSolicitud.PENDIENTE_APROBACION);
        assertEquals("#tabDocumentos", servicio.calcularSiguientePaso(expedienteSinDomicilio(), "COMERCIANTE",
                true, consultaBuro(false), pendiente, false).anclaPestanaFicha());
        // Con el expediente completo, lo que sigue es la decisión del gerente: ① Datos, como antes.
        assertEquals("", servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), pendiente, false).anclaPestanaFicha());
    }

    @Test
    public void testPasoGuiadoConPrestamoEnCursoNoEmpujaANingunLado() {
        // Préstamo no cerrado (RN-01): lo que sigue ya no es del circuito del oficial -- ni texto
        // ni destino; la ficha igual enlaza a la operación (textoCompromisoAbierto).
        PasoGuiado paso = servicio.calcularSiguientePaso(expedienteCompleto(), "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.APROBADA), true);
        assertNull(paso.getTexto());
        assertEquals(PasoGuiado.Destino.NINGUNO, paso.getDestino());
        org.junit.Assert.assertFalse(paso.tieneTexto());
    }

    @Test
    public void testPasoGuiadoSinExpedienteLeibleNoRompeElAviso() {
        // Si el llamador no pudo leer el expediente, el paso queda vacío: el aviso de éxito de la
        // acción que lo invocó sale igual, sin "siguiente paso" pegado.
        PasoGuiado paso = servicio.calcularSiguientePaso(null, "COMERCIANTE", true, consultaBuro(false),
                null, false);
        org.junit.Assert.assertFalse(paso.tieneTexto());
        assertEquals(PasoGuiado.Destino.NINGUNO, paso.getDestino());
        assertNull(paso.rutaRelativa(7L, "1234567"));
    }

    @Test
    public void testPasoGuiadoMantieneElTextoDeCedulaPendienteDeRenovacion() {
        // RF03 Parte 1 -- reprueba: un cambio de fecha en Datos invalidó el escaneo anterior. El
        // texto específico de ese caso se mantiene (no se vuelve un "Falta: Cédula de identidad").
        DocumentoExpediente cedulaReemplazada = new DocumentoExpediente();
        cedulaReemplazada.setIdDocumento(10L);
        cedulaReemplazada.setEstadoDocumento(EstadoDocumento.REEMPLAZADO);
        cedulaReemplazada.setTipoDocumento(tipoDocumentoDePrueba(1, "CEDULA_IDENTIDAD"));
        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedienteConNombresReales(),
                Arrays.asList(autorizacionCargada(), cedulaReemplazada,
                        documentoVigenteDePrueba(11L, 2, "COMPROBANTE_INGRESO"),
                        documentoVigenteDePrueba(12L, 3, "COMPROBANTE_DOMICILIO")),
                LocalDate.of(2031, 3, 15), true);

        PasoGuiado paso = servicio.calcularSiguientePaso(items, "COMERCIANTE", true, consultaBuro(false),
                null, false);
        assertEquals("Siguiente paso: cargar el escaneo de la cédula renovada en el Expediente.", paso.getTexto());
        assertEquals(PasoGuiado.Destino.EXPEDIENTE, paso.getDestino());
    }

    @Test
    public void testPasoGuiadoMantieneElTextoDeComprobanteDeIngresoPendienteConCompromisoAbierto() {
        // Tanda de usabilidad (19/10/2026, RNF_revision.md §10b): con una solicitud abierta el
        // texto no es el de un cliente nuevo.
        DocumentoExpediente comprobanteReemplazado = new DocumentoExpediente();
        comprobanteReemplazado.setIdDocumento(11L);
        comprobanteReemplazado.setEstadoDocumento(EstadoDocumento.REEMPLAZADO);
        comprobanteReemplazado.setTipoDocumento(tipoDocumentoDePrueba(2, "COMPROBANTE_INGRESO"));
        List<ItemExpediente> items = servicio.construirItemsExpediente("COMERCIANTE",
                tiposExpedienteConNombresReales(),
                Arrays.asList(autorizacionCargada(), documentoVigenteDePrueba(10L, 1, "CEDULA_IDENTIDAD"),
                        comprobanteReemplazado, documentoVigenteDePrueba(12L, 3, "COMPROBANTE_DOMICILIO")),
                LocalDate.of(2030, 1, 1), true);

        PasoGuiado pasoConSolicitud = servicio.calcularSiguientePaso(items, "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.BORRADOR), false);
        assertEquals("Siguiente paso: cargar el nuevo comprobante de ingreso. El cliente tiene una "
                + "solicitud abierta; su expediente debe quedar al día.", pasoConSolicitud.getTexto());

        PasoGuiado pasoConPrestamo = servicio.calcularSiguientePaso(items, "COMERCIANTE", true,
                consultaBuro(false), solicitud(55L, "SOL-2026-0005", EstadoSolicitud.APROBADA), true);
        assertEquals("Siguiente paso: cargar el nuevo comprobante de ingreso. El cliente tiene un "
                + "préstamo en curso; su expediente debe quedar al día.", pasoConPrestamo.getTexto());

        PasoGuiado pasoSinCompromiso = servicio.calcularSiguientePaso(items, "COMERCIANTE", true,
                consultaBuro(false), null, false);
        assertEquals("Siguiente paso: cargar el comprobante de ingreso que corresponde a "
                + "Comerciante / negocio propio en el Expediente.", pasoSinCompromiso.getTexto());
    }

    // RF02 (pedido de Joel): calcularEdad/evaluarPoliticaCredito/valoresSeparadosPorComa, movidos
    // acá desde ServicioSolicitudes (que ahora delega) -- ServicioSolicitudesTest sigue probando
    // el delegador sin cambios; estas pruebas documentan el nuevo dueño del cálculo.

    private static final List<String> DEPARTAMENTOS_HABILITADOS = Arrays.asList("Asunción", "Central");
    private static final List<String> SITUACIONES_ADMITIDAS = Arrays.asList(
            "ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO", "JUBILADO_PENSIONADO", "COMERCIANTE",
            "EMPLEADO_DOMESTICO");

    @Test
    public void testCalcularEdadCumpleanosPasado() {
        assertEquals(30, servicio.calcularEdad(LocalDate.of(1996, 1, 1), LocalDate.of(2026, 6, 1)));
    }

    @Test
    public void testCalcularEdadCumpleanosNoLlegoAun() {
        assertEquals(29, servicio.calcularEdad(LocalDate.of(1996, 12, 31), LocalDate.of(2026, 6, 1)));
    }

    @Test
    public void testPoliticaEdadFueraDeRangoMensajeConValoresConcretos() {
        // RF02 (pedido de Joel): mensaje exacto "La edad permitida es de 22 a 71 años; esta
        // persona tiene 20." -- antes decía "La edad del cliente (20 años) está fuera del rango...".
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                20, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, "COMERCIANTE", SITUACIONES_ADMITIDAS));
        assertEquals("La edad permitida es de 22 a 71 años; esta persona tiene 20.", error.getMessage());
    }

    @Test
    public void testPoliticaEdadEnLosBordesNoLanza() {
        servicio.evaluarPoliticaCredito(22, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "COMERCIANTE", SITUACIONES_ADMITIDAS);
        servicio.evaluarPoliticaCredito(71, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "COMERCIANTE", SITUACIONES_ADMITIDAS);
    }

    @Test
    public void testPoliticaDepartamentoNoHabilitadoSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, "Alto Paraná", DEPARTAMENTOS_HABILITADOS, "COMERCIANTE", SITUACIONES_ADMITIDAS));
        assertEquals(true, error.getMessage().contains("departamento"));
    }

    @Test
    public void testPoliticaSituacionLaboralNoAdmitidaSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class, () -> servicio.evaluarPoliticaCredito(
                30, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS, "DESEMPLEADO", SITUACIONES_ADMITIDAS));
        assertEquals(true, error.getMessage().contains("situación laboral"));
    }

    @Test
    public void testPoliticaCumplidaNoLanza() {
        servicio.evaluarPoliticaCredito(30, 22, 71, "Central", DEPARTAMENTOS_HABILITADOS,
                "COMERCIANTE", SITUACIONES_ADMITIDAS);
    }

    @Test
    public void testValoresSeparadosPorComaRecortaYQuitaVacios() {
        assertEquals(Arrays.asList("Asunción", "Central"), servicio.valoresSeparadosPorComa(" Asunción , Central "));
    }

    @Test
    public void testValoresSeparadosPorComaNuloDaListaVacia() {
        assertEquals(true, servicio.valoresSeparadosPorComa(null).isEmpty());
    }

    // ======================================================================
    // B13 (decision de Joel, 01/11/2026): la direccion y el barrio se guardan capitalizados,
    // con el mismo normalizador que los nombres (RF02). La coma duplicada ya estaba resuelta
    // aparte, al armar el texto compuesto (limpiarParte).
    // ======================================================================

    @Test
    public void testElDomicilioSeGuardaCapitalizado() {
        ServicioClientes servicio = new ServicioClientes();
        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdCiudad(1L);
        domicilio.setDireccionDomicilio("  mburicao 444 ");
        domicilio.setBarrio("jara");
        domicilio.setReferenciaDomicilio("porton verde");
        domicilio.setTipoVivienda("PROPIA");
        domicilio.setAntiguedadDomicilioAnios(3);

        servicio.validarDomicilio(domicilio);

        assertEquals("Mburicao 444", domicilio.getDireccionDomicilio());
        assertEquals("Jara", domicilio.getBarrio());
        // La referencia coloquial NO se capitaliza: no va en ningun documento legal (B.15).
        assertEquals("porton verde", domicilio.getReferenciaDomicilio());
    }

    @Test
    public void testElDomicilioEnMayusculasQuedaLegible() {
        ServicioClientes servicio = new ServicioClientes();
        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdCiudad(1L);
        domicilio.setDireccionDomicilio("AVDA. MARISCAL LOPEZ 1234");
        domicilio.setBarrio("BARCEQUILLO");
        domicilio.setTipoVivienda("ALQUILADA");
        domicilio.setAntiguedadDomicilioAnios(1);

        servicio.validarDomicilio(domicilio);

        assertEquals("Avda. Mariscal Lopez 1234", domicilio.getDireccionDomicilio());
        assertEquals("Barcequillo", domicilio.getBarrio());
    }

    @Test
    public void testElTextoDelDomicilioNoDuplicaLaComa() {
        // Ya resuelto antes de B13 (limpiarParte), pero queda fijado: el dato puede venir con una
        // coma pegada al final y el texto compuesto no la duplica.
        ServicioClientes servicio = new ServicioClientes();
        assertEquals("Avda. Mariscal López 1234, Barcequillo, San Lorenzo, Central",
                servicio.construirTextoDomicilio("Avda. Mariscal López 1234", "Barcequillo,",
                        "San Lorenzo", "Central", null));
    }
    // ==========================================================
    // App movil, ETAPA 2 (13/11/2026): "Mis pendientes"
    //
    // Lo que se prueba aca es la parte PURA: contar y listar los obligatorios que
    // faltan, y el orden. Que un documento este cumplido o no lo decide
    // construirItemsExpediente, que ya tiene sus pruebas mas arriba en este mismo
    // archivo -- aca no se vuelve a probar esa regla, se prueba lo que se arma con
    // su resultado.
    // ==========================================================

    private TipoDocumento tipoPendiente(String codigo, String nombre) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setCodigoTipo(codigo);
        tipo.setNombreTipo(nombre);
        tipo.setContexto("PERSONA");
        return tipo;
    }

    private DocumentoExpediente documentoCargado() {
        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdDocumento(1L);
        documento.setVersionDocumento(1);
        documento.setEstadoDocumento(EstadoDocumento.VIGENTE);
        return documento;
    }

    /** Un item obligatorio, cumplido o no. */
    private ItemExpediente itemObligatorio(String codigo, String etiqueta, boolean cumplido) {
        return new ItemExpediente(tipoPendiente(codigo, etiqueta), etiqueta, "descripcion",
                cumplido ? documentoCargado() : null, true);
    }

    @Test
    public void armarPendienteCuentaSoloLosObligatoriosYListaLosQueFaltan() {
        List<ItemExpediente> items = Arrays.asList(
                itemObligatorio("AUTORIZACION_CONSULTA_BURO", "Autorizacion de consulta al buro", true),
                itemObligatorio("CEDULA_IDENTIDAD", "Cedula de identidad", false),
                itemObligatorio("COMPROBANTE_INGRESO", "Comprobante de ingreso", false),
                itemObligatorio("COMPROBANTE_DOMICILIO", "Comprobante de domicilio", true));

        py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente pendiente =
                servicio.armarPendiente(personaValida(), items);

        assertEquals(2, pendiente.getCumplidos());
        assertEquals(4, pendiente.getObligatorios());
        assertEquals(2, pendiente.cuantosFaltan());
        // En el orden en que vinieron los items, que es el orden del tramite (B90).
        assertEquals(Arrays.asList("Cedula de identidad", "Comprobante de ingreso"),
                pendiente.getFaltantes());
    }

    @Test
    public void armarPendienteDevuelveNullCuandoElExpedienteEstaCompleto() {
        // El que no debe nada NO es un pendiente: no aparece en la lista.
        List<ItemExpediente> items = Arrays.asList(
                itemObligatorio("CEDULA_IDENTIDAD", "Cedula de identidad", true),
                itemObligatorio("COMPROBANTE_INGRESO", "Comprobante de ingreso", true));

        assertNull(servicio.armarPendiente(personaValida(), items));
    }

    @Test
    public void armarPendienteIgnoraLosItemsQueNoSonObligatorios() {
        // Un documento complementario sin cargar no es un pendiente: los obligatorios son los
        // mismos que pesan en "Enviar a aprobacion" (CODIGOS_EXPEDIENTE_OBLIGATORIOS).
        ItemExpediente complementarioSinCargar = new ItemExpediente(
                tipoPendiente("OTRO", "Otro documento"), "Otro documento", "descripcion", null, false);
        List<ItemExpediente> items = Arrays.asList(
                itemObligatorio("CEDULA_IDENTIDAD", "Cedula de identidad", true),
                complementarioSinCargar);

        assertNull(servicio.armarPendiente(personaValida(), items));
    }

    @Test
    public void armarPendienteConUnaCedulaVencidaLaCuentaComoFaltante() {
        // RF03 Parte 1: el escaneo esta cargado y VIGENTE, pero la cedula real vencio, asi que
        // isCumplido() es false. El pendiente tiene que decir que falta.
        ItemExpediente cedulaVencida = new ItemExpediente(
                tipoPendiente("CEDULA_IDENTIDAD", "Cedula de identidad"), "Cedula de identidad",
                "Ambos lados, vigente.", documentoCargado(), true, LocalDate.of(2026, 8, 30), true);

        py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente pendiente =
                servicio.armarPendiente(personaValida(), Arrays.asList(cedulaVencida));

        assertEquals(0, pendiente.getCumplidos());
        assertEquals(Arrays.asList("Cedula de identidad"), pendiente.getFaltantes());
    }

    @Test
    public void ordenarPorFaltantesPoneAlQueMasLeFaltaPrimeroYEsEstable() {
        Persona primera = personaValida();
        primera.setApellidos("Acosta");
        Persona segunda = personaValida();
        segunda.setApellidos("Benitez");
        Persona tercera = personaValida();
        tercera.setApellidos("Caceres");

        // Llegan en el orden del listado (apellidos): Acosta debe 1, Benitez 3, Caceres 1.
        java.util.List<py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente> pendientes =
                new java.util.ArrayList<>(Arrays.asList(
                        new py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente(
                                primera, 3, 4, Arrays.asList("uno")),
                        new py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente(
                                segunda, 1, 4, Arrays.asList("uno", "dos", "tres")),
                        new py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente(
                                tercera, 3, 4, Arrays.asList("uno"))));

        ServicioClientes.ordenarPorFaltantes(pendientes);

        // El que mas debe, primero.
        assertEquals("Benitez", pendientes.get(0).getPersona().getApellidos());
        // Y los dos que empatan conservan el orden de apellidos: la estabilidad del sort es lo
        // que evita que la lista se reordene sola entre una consulta y la siguiente.
        assertEquals("Acosta", pendientes.get(1).getPersona().getApellidos());
        assertEquals("Caceres", pendientes.get(2).getPersona().getApellidos());
    }
}
