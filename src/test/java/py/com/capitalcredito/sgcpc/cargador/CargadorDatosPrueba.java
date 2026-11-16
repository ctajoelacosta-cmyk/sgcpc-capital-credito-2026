package py.com.capitalcredito.sgcpc.cargador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import py.com.capitalcredito.sgcpc.dao.CiudadDAO;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dao.MotivoRechazoSolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.ParametroDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaDAO;
import py.com.capitalcredito.sgcpc.modelos.PersonaReferencia;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.dao.UsuarioDAO;
import py.com.capitalcredito.sgcpc.modelos.Ciudad;
import py.com.capitalcredito.sgcpc.modelos.MotivoRechazoSolicitud;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.ParametroSistema;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PersonaConyuge;
import py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio;
import py.com.capitalcredito.sgcpc.modelos.PersonaLaboral;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;
import py.com.capitalcredito.sgcpc.servicios.ServicioAprobacionCredito;
import py.com.capitalcredito.sgcpc.servicios.ServicioCaja;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioCobranza;
import py.com.capitalcredito.sgcpc.servicios.ServicioConsultaCrediticia;
import py.com.capitalcredito.sgcpc.servicios.ServicioDesembolso;
import py.com.capitalcredito.sgcpc.servicios.ServicioDocumentos;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioMora;
import py.com.capitalcredito.sgcpc.servicios.ServicioReversiones;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * D-115 (Bloque 7/E10, 7.4, Doc. 00 §16.2): carga los 11 escenarios de demostración + la caja
 * cerrada con arqueo + la reversión ya autorizada, usando SIEMPRE los servicios reales (nunca un
 * INSERT directo) para que cálculos, numeración, auditoría y PDFs salgan exactamente como en una
 * operación real. Corre como programa aparte (nunca dentro de Tomcat): `Reloj.fijarParaCargador`
 * es de uso exclusivo de esta clase, jamás alcanzable desde un Servlet (ver Reloj.java/RelojTest).
 *
 * USO (desde sistema/, con la base ya recreada y bd.properties apuntando a sgcpc_db_pruebas):
 *   mvn -Dtest=CargadorDatosPrueba test [-Dcargador.fecha=AAAA-MM-DD]
 *
 * No usa "mvn test"/"mvn clean package" a propósito -- el nombre no matchea los patrones de
 * Surefire (mismo patrón que PruebaConexionBD/PruebaPA16CapitalNoVencido).
 *
 * Guarda de seguridad (D-113/D-115, pedido explícito de Joel: "No toques sgcpc_db hasta que yo
 * apruebe"): el primer paso, antes de crear nada, confirma FabricaConexionBD.esBaseDePruebas() --
 * si bd.properties no tiene la marca entorno=pruebas, el cargador aborta sin tocar absolutamente
 * nada.
 */
public class CargadorDatosPrueba {

    private static final byte[] CONTENIDO_PDF_DEMO =
            "%PDF-1.4 datos de demostracion SGCPC (7.4/D-115)".getBytes(StandardCharsets.UTF_8);
    private static final String DESTINO_DEMO = "Capital de trabajo";

    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioDocumentos servicioDocumentos = new ServicioDocumentos();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioAprobacionCredito servicioAprobacionCredito = new ServicioAprobacionCredito();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioDesembolso servicioDesembolso = new ServicioDesembolso();
    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    private final ServicioReversiones servicioReversiones = new ServicioReversiones();
    private final ServicioMora servicioMora = new ServicioMora();
    private final PersonaDAO personaDAO = new PersonaDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final CiudadDAO ciudadDAO = new CiudadDAO();
    private final MotivoRechazoSolicitudDAO motivoRechazoSolicitudDAO = new MotivoRechazoSolicitudDAO();
    // Bloque 7/E10, Tanda A (D-126): cache de ciudades por nombre (evita una consulta por persona) --
    // se llena una sola vez, en el primer crearPersona().
    private Map<String, Ciudad> cacheCiudadesPorNombre;
    private Map<String, Long> cacheMotivosRechazoPorCodigo;

    private static final String[] CLAVES_FISCALES_TIMBRADO = {
        "fiscal.timbrado_numero", "fiscal.timbrado_vigencia_desde", "fiscal.timbrado_vigencia_hasta",
        "fiscal.factura_numero_desde", "fiscal.factura_numero_hasta"
    };

    private long idGerente;
    private long idOficial;
    private long idCajera;
    // Punto 14 (pedido de Joel): una sesión de caja por día hábil (08:00-17:30) con todas las
    // operaciones de ese día adentro -- estado de "qué día está abierto ahora mismo".
    private Long idSesionCajaActual;
    private LocalDate fechaSesionCajaActual;
    private LocalDate H;
    private final Map<String, Long> tiposDocumento = new HashMap<>();
    private final List<String[]> reporte = new ArrayList<>();

    @Test
    public void cargarDatosDeDemostracion() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            verificarBaseAutorizada(conexion);
        }
        H = determinarFechaReferencia();
        System.out.println("== Cargador de datos de demostracion (7.4/D-115) -- H = " + H + " ==");

        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            resolverUsuarios(lectura);
            verificarLimiteTasaVigente(lectura);
            verificarCedulasLibres(lectura);
            resolverTiposDocumento(lectura);
        }
        insertarTimbradoHistorico();

        // ---- Fase 1: originacion (solicitud -> aprobacion -> formalizacion, SIN desembolsar),
        // de la mas vieja a la mas nueva, para que SOLICITUD/OPERACION salgan crecientes. El
        // desembolso se hace en la Fase 2, intercalado con los cobros en orden cronológico real
        // (punto 14: así la caja se agrupa por día real sin importar el tipo de operación).
        // Puntos 2/5 (pedido de Joel): montos, plazos, ingresos, ocupaciones y teléfonos variados
        // -- como clientes reales distintos, no la misma persona repetida 11 veces. ----

        BigDecimal montoE4 = new BigDecimal("8000000");
        // Punto 7: E4 rediseñado -- paga las cuotas 1 a 3 a tiempo y despues deja de pagar, para
        // llegar a la cuota 4 con ~200 dias de atraso (antes nunca pagaba nada: menos realista).
        long idPrestamoE4 = originarSinDesembolsar("800000005", "Jorge", "Villalba", true, false,
                new BigDecimal("4500000"), "Transportista (flete)", "0981234567",
                "Av. Fernando de la Mora 1834, Barrio San Pablo, Asunción", montoE4, 18,
                H.minusDays(328), H.minusDays(325), H.minusDays(320), H.minusDays(290));

        BigDecimal montoE7 = new BigDecimal("2200000");
        long idPrestamoE7 = originarSinDesembolsar("800000008", "Elena", "Rojas", true, false,
                new BigDecimal("2800000"), "Empleada doméstica", "0982345678",
                "Ruta Mcal. Estigarribia km 12, Barrio San Lorenzo, San Lorenzo", montoE7, 3,
                H.minusDays(108), H.minusDays(105), H.minusDays(100), H.minusDays(70));

        // D-125 (pedido de Joel, PASO 3): E3 deja de necesitar garante -- la financiera solo
        // presta a quien comprueba ingresos, sin codeudor de respaldo. Ana Duarte pasa a cliente
        // normal, con comprobante de ingresos; las mismas fechas de antes (primerVenc H-45, cuotas
        // mensuales) ya dejaban 2 cuotas vencidas (H-45 y H-15) sin tocar nada más.
        BigDecimal montoE3 = new BigDecimal("5000000");
        long idPrestamoE3 = originarSinDesembolsar("800000003", "Ana", "Duarte", true, false,
                new BigDecimal("2800000"), "Costurera (independiente)", "0991456789",
                "Calle 15 de Agosto 456, Barrio Obrero, Asunción", montoE3, 10,
                H.minusDays(83), H.minusDays(80), H.minusDays(75), H.minusDays(45));
        long idPersonaAna = obtenerIdClienteDePrestamo(idPrestamoE3);

        BigDecimal montoE6 = new BigDecimal("4500000");
        long idPrestamoE6 = originarSinDesembolsar("800000007", "Luis", "Aquino", true, false,
                new BigDecimal("5000000"), "Electricista independiente", "0985678901",
                "Calle Concordia 789, Barrio Trinidad, Asunción", montoE6, 9,
                H.minusDays(68), H.minusDays(65), H.minusDays(60), H.minusDays(30));

        BigDecimal montoE2 = new BigDecimal("2500000");
        long idPrestamoE2 = originarSinDesembolsar("800000002", "Carlos", "Benítez", true, false,
                new BigDecimal("2800000"), "Mecánico independiente", "0972789012",
                "Av. Mcal. López 3421, Barrio Recoleta, Asunción", montoE2, 6,
                H.minusDays(53), H.minusDays(50), H.minusDays(45), H.minusDays(15));

        // Punto 14 (colisión con E7 cuota 2, ambas en H-40): E5 se corre un día, a H-41, para que
        // cada día real tenga una sola sesión de caja aunque el desembolso de una y el cobro de
        // otra coincidan de casualidad.
        BigDecimal montoE5 = new BigDecimal("3500000");
        long idPrestamoE5 = originarSinDesembolsar("800000006", "Rosa", "Cáceres", true, false,
                new BigDecimal("3000000"), "Peluquera (salón propio)", "0981890123",
                "Calle Perú 567, Barrio Republicano, Luque", montoE5, 7,
                H.minusDays(49), H.minusDays(46), H.minusDays(41), H.minusDays(11));

        BigDecimal montoRev = new BigDecimal("2000000");
        long idPrestamoRev = originarSinDesembolsar("800000012", "Norma", "Espínola", true, false,
                new BigDecimal("2500000"), "Feriante (puesto de mercado)", "0991901234",
                "Calle Yegros 234, Barrio Ricardo Brugada (La Chacarita), Asunción", montoRev, 3,
                H.minusDays(46), H.minusDays(43), H.minusDays(38), H.minusDays(8));

        BigDecimal montoE1 = new BigDecimal("3000000");
        long idPrestamoE1 = originarSinDesembolsar("800000001", "María", "González", true, false,
                new BigDecimal("3500000"), "Vendedora en comercio", "0982012345",
                "Av. Dr. Luis María Argaña 1122, Barrio Villa Adela, Lambaré", montoE1, 8,
                H.minusDays(43), H.minusDays(40), H.minusDays(35), H.minusDays(5));

        // ---- Etapa solicitudes recientes (no depende de caja -- puede correr siempre, aunque la
        // etapa cobros/caja de abajo esté omitida): E9 (aprobada, se formaliza al final de esta
        // misma etapa), E10 (rechazada), E11 (pendiente de aprobación). ----
        long idPrestamoE9 = solicitarYAprobar("800000010", "Patricia", "Giménez", true, false,
                new BigDecimal("4500000"), "Docente", "0985123456",
                "Calle Cerro Corá 890, Barrio Villa Morra, Asunción", new BigDecimal("6000000"), 12,
                H.minusDays(8), H.minusDays(5));

        solicitarYRechazar("800000011", "Diego", "Fernández", true, true,
                new BigDecimal("3000000"), "Comerciante (kiosco)", "0971234890",
                "Ruta Transchaco km 8, Barrio Ita Enramada, Mariano Roque Alonso", new BigDecimal("4000000"), 8,
                H.minusDays(3), H.minusDays(1),
                "INFORMCONF", "El cliente registra mora vigente en el buró crediticio.");

        solicitarYDejarPendiente("800000009", "Miguel", "Sosa", true, false,
                new BigDecimal("3200000"), "Plomero independiente", "0972345901",
                "Calle Teniente Fariña 345, Barrio San Antonio, Ñemby", new BigDecimal("3000000"), 6,
                H.minusDays(1));

        formalizarSinDesembolsar(idPrestamoE9, H.plusDays(30), H, "800000010", "Patricia", "Giménez");

        // ---- Etapa cobros/caja: desembolsos, cobros, cancelación, reversión, gestiones de
        // cobranza y arqueo. PENDIENTE DE RE-COORDINAR (15/10/2026, ver "Pendientes para la etapa
        // de uso diario" en AVANCE.md): las fechas de cobro de los 11 escenarios eran offsets
        // fijos de 30 días (H.minusDays(290), H.minusDays(260), ...), pero el calendario real de
        // cuotas es mensual (28-31 días según el mes) -- con el tiempo (H se recalcula desde HOY
        // en cada corrida) el desfase crece hasta que calcularMontoCobrableNormal ya no encuentra
        // una cuota que venza justo ese día (D-144: "Cobrar" solo cobra lo vencido, nunca una
        // cuota futura) y el cobro falla. Usar la fecha REAL de la próxima cuota pendiente
        // (ServicioCobranza.obtenerProximaCuotaPendiente, ya corregido en cobrarCuotaATiempo) es
        // la parte fácil -- pero eso desacopla un cobro del desembolso con el que antes coincidía
        // "de casualidad" en el mismo día de caja (punto 14 del diseño original), y un desembolso
        // se queda sin fondo en su propia sesión. Arreglarlo de punta a punta exige re-coordinar
        // fechas Y montos de los 11 escenarios para que la caja vuelva a cuadrar -- no es un
        // cambio de una línea, se deja para la etapa de datos realistas (pedido explícito de
        // Joel: no re-coordinar la caja ni subir el fondo del día como atajo).
        // Omitida por defecto -- opción explícita para no dejarla fuera en silencio.
        if ("true".equals(System.getProperty("cargador.incluirCobrosYCaja"))) {
            cargarCobrosYCaja(idPrestamoE4, montoE4, idPrestamoE7, montoE7, idPrestamoE3, montoE3, idPersonaAna,
                    idPrestamoE6, montoE6, idPrestamoE2, montoE2, idPrestamoE5, montoE5, idPrestamoRev, montoRev,
                    idPrestamoE1, montoE1);
            // verificarInvariantes chequea que la cuota 1 de E1 no quede pegada a H -- una
            // condición pensada para la demostración de cobros/mora (que recién tiene sentido
            // revisar cuando esa etapa corrió de verdad); mismo motivo de fondo que el resto de
            // esta etapa (offsets fijos vs calendario real), así que queda adentro del if.
            verificarInvariantes(idPrestamoE1);
        } else {
            System.out.println("== Etapa cobros/caja OMITIDA a propósito (pendiente de re-coordinar, ver "
                    + "AVANCE.md) -- pase -Dcargador.incluirCobrosYCaja=true para incluirla igual (hoy puede "
                    + "fallar por el desfase de fechas explicado arriba). ==");
        }

        Reloj.liberarCargador();
        recalcularMoraDeTodos();
        imprimirReporte();

        System.out.println("== Cargador terminado sin errores. Redespliegue y revise los escenarios en el navegador. ==");
    }

    /**
     * Etapa cobros/caja (15/10/2026): todo lo que antes era Fase 2 (desembolsos, cobros,
     * cancelación, reversión, en orden cronológico real -- punto 14, para que la caja quede
     * agrupada por día real) + Fase 2.5 (gestiones de cobranza) + el arqueo de Fase 3, sin
     * cambios de contenido -- solo se movió a un método aparte para poder omitirla. Ver el
     * comentario de arriba (cargarDatosDeDemostracion) para el motivo por el que está pendiente
     * de re-coordinar.
     */
    private void cargarCobrosYCaja(long idPrestamoE4, BigDecimal montoE4, long idPrestamoE7, BigDecimal montoE7,
            long idPrestamoE3, BigDecimal montoE3, long idPersonaAna, long idPrestamoE6, BigDecimal montoE6,
            long idPrestamoE2, BigDecimal montoE2, long idPrestamoE5, BigDecimal montoE5, long idPrestamoRev,
            BigDecimal montoRev, long idPrestamoE1, BigDecimal montoE1) throws SQLException {
        desembolsar(idPrestamoE4, montoE4, H.minusDays(320));
        cobrarCuotaATiempo(idPrestamoE4, H.minusDays(290), 9);
        cobrarCuotaATiempo(idPrestamoE4, H.minusDays(260), 9);
        cobrarCuotaATiempo(idPrestamoE4, H.minusDays(230), 9);
        // La cuota 4 de E4 vence H-200 y queda sin pagar a propósito (punto 7): ~200 días de
        // atraso al día H -- suficiente para que /mora marque "Incobrable" (punto 6).

        desembolsar(idPrestamoE7, montoE7, H.minusDays(100));
        desembolsar(idPrestamoE3, montoE3, H.minusDays(75));
        cobrarCuotaATiempo(idPrestamoE7, H.minusDays(70), 9);
        desembolsar(idPrestamoE6, montoE6, H.minusDays(60));
        desembolsar(idPrestamoE2, montoE2, H.minusDays(45));
        desembolsar(idPrestamoE5, montoE5, H.minusDays(41));
        cobrarCuotaATiempo(idPrestamoE7, H.minusDays(40), 9);
        desembolsar(idPrestamoRev, montoRev, H.minusDays(38));
        desembolsar(idPrestamoE1, montoE1, H.minusDays(35));
        cobrarCuotaATiempo(idPrestamoE6, H.minusDays(30), 9);
        cancelarTotal(idPrestamoE6, H.minusDays(20), 10);
        // Punto 10: a los pocos días de la cancelación, el pagaré vuelve firmado y se completa el
        // cierre documental de E6 -- queda solo E7 pendiente ("para mostrar los dos estados").
        completarCierreDocumentalE6(idPrestamoE6, H.minusDays(16), H.minusDays(14), H.minusDays(12));
        cobrarCuotaATiempo(idPrestamoE7, H.minusDays(10), 9); // finaliza E7 (pagare aun no devuelto: cierre documental pendiente, a propósito)

        // Punto 20 (pedido de Joel, reemplaza el diseño anterior de "monto mal digitado" -- el
        // motor aplica solo lo adeudado y da vuelto, así que un monto mal digitado nunca
        // justificaba una reversión de verdad): cobro duplicado -- la cajera registra el mismo
        // pago de la cuota 1 dos veces; el segundo se aplica de más a la cuota siguiente, con
        // dinero que nunca entró en caja. Se detecta en el arqueo y se revierte el duplicado (el
        // más reciente). Norma queda al día con el único cobro válido, el primero.
        long idPagoCuota1Norma = cobrarCuotaATiempo(idPrestamoRev, H.minusDays(8), 9);
        String reciboCuota1Norma = leerNumeroRecibo(idPagoCuota1Norma);
        long idPagoDuplicadoNorma = cobrarCuotaATiempo(idPrestamoRev, H.minusDays(8), 15);
        autorizarReversion(idPrestamoRev, idPagoDuplicadoNorma, H.minusDays(6), 9, 10,
                "Cobro duplicado: se registró dos veces el pago de la cuota 1 (" + reciboCuota1Norma
                        + "). Se revierte el segundo registro.");

        cobrarCuotaATiempo(idPrestamoE1, H.minusDays(5), 9);
        cobrarParcialConPromesa(idPrestamoE5, H.minusDays(5), 11, H.plusDays(2));

        // ---- Fase 2.5 (punto 8, pedido de Joel): historial de gestiones de cobranza sobre los
        // préstamos en mora -- variadas en tipo, resultado y usuario que las registra; antes no
        // existía ninguna. ----
        long idPersonaE4 = obtenerIdClienteDePrestamo(idPrestamoE4);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.LLAMADA, "No contesta", null,
                null, null, idOficial, H.minusDays(193), 10);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.MENSAJE,
                "Se envió recordatorio por WhatsApp, sin respuesta", null, null, null, idCajera, H.minusDays(177), 15);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.LLAMADA, "No contesta", null,
                null, null, idOficial, H.minusDays(142), 11);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.VISITA_LOCAL,
                "Domicilio cerrado; un vecino indica que sigue viviendo ahí", null, null, null, idOficial,
                H.minusDays(106), 16);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.LLAMADA, "Atiende y promete pagar",
                null, H.minusDays(50), new BigDecimal("600000"), idCajera, H.minusDays(59), 10);
        registrarGestionBackdateada(idPrestamoE4, idPersonaE4, TipoGestionCobranza.LLAMADA,
                "Atiende, dice que va a pagar pero no da fecha concreta", null, null, null, idOficial,
                H.minusDays(14), 9);

        // D-125: ya no hay garante a quien contactar -- las 3 gestiones quedan todas sobre Ana.
        registrarGestionBackdateada(idPrestamoE3, idPersonaAna, TipoGestionCobranza.LLAMADA,
                "No atiende, buzón de voz", null, null, null, idOficial, H.minusDays(40), 10);
        registrarGestionBackdateada(idPrestamoE3, idPersonaAna, TipoGestionCobranza.MENSAJE,
                "Se envió recordatorio por WhatsApp, sin respuesta", null, null, null, idOficial, H.minusDays(35), 11);
        registrarGestionBackdateada(idPrestamoE3, idPersonaAna, TipoGestionCobranza.LLAMADA, "No atiende", null,
                null, null, idCajera, H.minusDays(10), 9);

        long idPersonaE2 = obtenerIdClienteDePrestamo(idPrestamoE2);
        registrarGestionBackdateada(idPrestamoE2, idPersonaE2, TipoGestionCobranza.LLAMADA, "No contesta", null,
                null, null, idOficial, H.minusDays(10), 10);
        registrarGestionBackdateada(idPrestamoE2, idPersonaE2, TipoGestionCobranza.LLAMADA,
                "Atiende, dice que paga la semana que viene", null, H.plusDays(5), new BigDecimal("450000"), idCajera,
                H.minusDays(4), 14);

        cajaCerradaConArqueo(H.minusDays(1));
    }

    // =====================================================================
    // Guardas de seguridad y preparacion
    // =====================================================================

    /**
     * D-115 / D-121 (pedido de Joel): dos caminos autorizados para correr este cargador, nunca
     * ninguno por defecto.
     *   1) bd.properties con la marca entorno=pruebas (sgcpc_db_pruebas) -- el camino de siempre.
     *   2) -Dcargador.baseOficialDemo=true, SOLO si la base está recién recreada (sin ninguna
     *      persona/solicitud/préstamo todavía) -- para sembrar la base OFICIAL de demostración
     *      antes de la defensa (D-121). La marca entorno=pruebas NUNCA se pone en esa base a
     *      propósito: no hay que confundirla con sgcpc_db_pruebas, y evita que
     *      RestablecerContrasenasPruebas se dispare solo la próxima vez que se corra
     *      recrear_base.sh sobre ella (D-118) -- ahí las contraseñas quedan las del seed, con
     *      cambio obligatorio, tal como pidió Joel.
     * Sin ninguna de las dos, o con la segunda pero la base ya con datos, aborta sin tocar nada.
     */
    private void verificarBaseAutorizada(Connection conexion) throws SQLException {
        if (FabricaConexionBD.esBaseDePruebas()) {
            return;
        }
        if (!"true".equals(System.getProperty("cargador.baseOficialDemo"))) {
            throw new IllegalStateException("ABORTADO: bd.properties no tiene la marca entorno=pruebas -- este "
                    + "cargador nunca debe correr contra una base sin esa marca, salvo que se pase explícitamente "
                    + "-Dcargador.baseOficialDemo=true (D-121, para sembrar la base oficial de demostración antes "
                    + "de la defensa) Y la base esté recién recreada, sin datos todavía.");
        }
        long personas = contarFilas(conexion, "personas");
        long solicitudes = contarFilas(conexion, "solicitudes_credito");
        long prestamos = contarFilas(conexion, "prestamos");
        if (personas > 0 || solicitudes > 0 || prestamos > 0) {
            throw new IllegalStateException("ABORTADO: -Dcargador.baseOficialDemo=true exige una base recién "
                    + "recreada (bd/recrear_base.sh), sin ninguna persona/solicitud/préstamo todavía -- esta "
                    + "base ya tiene datos (personas=" + personas + ", solicitudes=" + solicitudes + ", "
                    + "prestamos=" + prestamos + "). Nunca se carga la demostración encima de datos reales o ya "
                    + "cargados.");
        }
        System.out.println("== AVISO: corriendo con -Dcargador.baseOficialDemo=true (sin la marca "
                + "entorno=pruebas) -- base confirmada recién recreada, sin personas/solicitudes/préstamos. ==");
    }

    private long contarFilas(Connection conexion, String tabla) throws SQLException {
        try (java.sql.Statement sentencia = conexion.createStatement();
                java.sql.ResultSet resultado = sentencia.executeQuery("SELECT count(*) FROM " + tabla)) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    private LocalDate determinarFechaReferencia() {
        String propiedad = System.getProperty("cargador.fecha");
        return (propiedad == null || propiedad.trim().isEmpty()) ? Reloj.hoy() : LocalDate.parse(propiedad.trim());
    }

    private void resolverUsuarios(Connection conexion) throws SQLException {
        Usuario gerente = usuarioDAO.buscarPorNombreUsuario(conexion, "gerente");
        Usuario oficial = usuarioDAO.buscarPorNombreUsuario(conexion, "oficial");
        Usuario cajera = usuarioDAO.buscarPorNombreUsuario(conexion, "cajera");
        if (gerente == null || oficial == null || cajera == null) {
            throw new IllegalStateException("ABORTADO: no se encontraron los usuarios gerente/oficial/cajera "
                    + "-- ¿se corrió bd/recrear_base.sh?");
        }
        idGerente = gerente.getIdUsuario();
        idOficial = oficial.getIdUsuario();
        idCajera = cajera.getIdUsuario();
    }

    private void verificarLimiteTasaVigente(Connection conexion) throws SQLException {
        LimiteTasaUsura vigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
        if (vigente == null) {
            throw new IllegalStateException("ABORTADO: no hay un límite de tasa de usura vigente en "
                    + "limites_tasa_usura (vigente_hasta IS NULL). El cargador nunca inventa este valor -- "
                    + "cárguelo primero (por pantalla, /parametros, o en bd/03_parametros.sql antes de "
                    + "recrear la base) y vuelva a correr el cargador.");
        }
        // Nota (para el informe a Joel): LimiteTasaUsuraDAO.obtenerVigente() NO filtra por fecha --
        // siempre devuelve la unica fila con vigente_hasta IS NULL, sin importar que fecha simule el
        // Reloj del cargador. No hace falta (ni existe en el codigo actual) un limite por cada mes
        // historico que toquen los 11 escenarios -- un solo limite vigente alcanza para todos.
    }

    private void verificarCedulasLibres(Connection conexion) throws SQLException {
        String[] cedulas = {"800000001", "800000002", "800000003", "800000004", "800000005", "800000006",
                "800000007", "800000008", "800000009", "800000010", "800000011", "800000012"};
        for (String cedula : cedulas) {
            if (personaDAO.buscarPorCedula(conexion, cedula) != null) {
                throw new IllegalStateException("ABORTADO: la cédula " + cedula + " ya existe en esta base -- "
                        + "el cargador espera una base recién recreada (bd/recrear_base.sh), sin datos de "
                        + "humo ni de una corrida anterior del cargador. Recree sgcpc_db_pruebas y vuelva a "
                        + "correr.");
            }
        }
    }

    private void resolverTiposDocumento(Connection conexion) throws SQLException {
        String[] codigos = {"CEDULA_IDENTIDAD", "COMPROBANTE_INGRESO", "AUTORIZACION_CONSULTA_BURO",
                "RESULTADO_CONSULTA_BURO", "CONTRATO_FIRMADO", "PAGARE_FIRMADO", "PLAN_PAGOS_FIRMADO"};
        for (String codigo : codigos) {
            tiposDocumento.put(codigo, tipoDocumentoDAO.obtenerPorCodigo(conexion, codigo).getIdTipoDocumento());
        }
    }

    /**
     * D-116 (pedido de Joel): timbrado ANTERIOR, ya vencido (01/09/2025-31/08/2026, número
     * ficticio distinto), como dato de DEMOSTRACIÓN -- nunca en bd/03_parametros.sql, que se
     * instala en el servidor real del cliente.
     *
     * Encontrado en la primera corrida real: el seed pone `vigente_desde` (la columna de
     * versionado del PARÁMETRO, no el valor fiscal que guarda) en `CURRENT_DATE` -- el día que
     * corrió `recrear_base.sh` -- no en 01/09/2026 (cuándo el timbrado actual rige de verdad). Con
     * eso, ni la fila ACTUAL respondía por una fecha simulada anterior a "hoy real" (ej.
     * 09/09/2026, si el cargador corrió más tarde ese mismo mes). Se corrige retrasando también el
     * `vigente_desde` de la fila ACTUAL a 01/09/2026 antes de insertar la anterior -- recién así
     * las dos quedan encadenadas exacto en el límite real (01/09/2026), sin agujero.
     */
    private void insertarTimbradoHistorico() throws SQLException {
        LocalDate limiteEntreTimbrados = LocalDate.of(2026, 9, 1);
        LocalDate inicioTimbradoAnterior = LocalDate.of(2025, 9, 1);

        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                for (String clave : CLAVES_FISCALES_TIMBRADO) {
                    try (java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                            "UPDATE parametros_sistema SET vigente_desde = ? "
                                    + "WHERE clave_parametro = ? AND vigente_hasta IS NULL")) {
                        sentencia.setDate(1, java.sql.Date.valueOf(limiteEntreTimbrados));
                        sentencia.setString(2, clave);
                        sentencia.executeUpdate();
                    }
                }

                String[] valoresTimbradoAnterior = {"15679234", "2025-09-01", "2026-08-31", "1", "9999999"};
                String[] nombres = {"Número de timbrado (anterior, demostración)",
                        "Timbrado vigente desde (anterior, demostración)",
                        "Timbrado vigente hasta (anterior, demostración)",
                        "Numeración de factura desde (anterior, demostración)",
                        "Numeración de factura hasta (anterior, demostración)"};
                for (int i = 0; i < CLAVES_FISCALES_TIMBRADO.length; i++) {
                    ParametroSistema historico = new ParametroSistema();
                    historico.setClaveParametro(CLAVES_FISCALES_TIMBRADO[i]);
                    historico.setNombreParametro(nombres[i]);
                    historico.setValorParametro(valoresTimbradoAnterior[i]);
                    historico.setTipoValor(i == 0 ? TipoValorParametro.TEXTO
                            : (i <= 2 ? TipoValorParametro.FECHA : TipoValorParametro.ENTERO));
                    historico.setVigenteDesde(inicioTimbradoAnterior);
                    historico.setModificadoPor(idOficial);
                    historico.setObservacion("Timbrado anterior, dato de demostración (7.4/D-116) -- "
                            + "nunca en 03_parametros.sql.");
                    long idHistorico = parametroDAO.insertar(conexion, historico);
                    parametroDAO.cerrarVigencia(conexion, idHistorico, limiteEntreTimbrados);
                }
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    // =====================================================================
    // Helpers de persona / expediente / buro (PO-02)
    // =====================================================================

    /** Normalización de personas, grupo Cónyuge (16/10/2026, pedido de Joel): Luis Aquino (E6,
     * PENDIENTE_DESEMBOLSO), Miguel Sosa (solicitud pendiente de aprobación) y Diego Fernández
     * (solicitud rechazada) -- variedad de estados de préstamo, no solo de estado civil. */
    private static final java.util.Set<String> CEDULAS_SOLTERAS =
            new java.util.HashSet<>(java.util.Arrays.asList("800000007", "800000009", "800000011"));

    private long crearPersona(String cedula, String nombres, String apellidos, boolean presentaIngreso,
            boolean indicadorMora, BigDecimal ingreso, String ocupacion, String telefono, String domicilio,
            LocalDate fecha) throws SQLException {
        LocalDateTime instanteAlta = enHora(fecha, 8, 0);
        Reloj.fijarParaCargador(instanteAlta);

        Persona persona = new Persona();
        persona.setCedulaIdentidad(cedula);
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        persona.setFechaNacimiento(LocalDate.of(1985, 3, 15));
        // Normalización de personas, grupo Cónyuge (16/10/2026, pedido de Joel): antes las 11
        // personas eran siempre "Casado/a" -- CEDULAS_SOLTERAS deja algunas Soltero/a, para que la
        // prueba en pantalla cubra los dos casos (con y sin fila en personas_conyuge).
        persona.setEstadoCivil(CEDULAS_SOLTERAS.contains(cedula) ? "Soltero/a" : "Casado/a");
        persona.setTelefonoWhatsapp(telefono);
        List<PersonaReferencia> referencias = completarCamposTandaA(persona, cedula, nombres, telefono, fecha);
        // Antes se completaba sobre la propia Persona dentro de completarCamposTandaA -- ahora es
        // un objeto aparte (personas_conyuge), que solo corresponde armar si el estado civil lo exige.
        PersonaConyuge conyuge = CEDULAS_SOLTERAS.contains(cedula) ? null : construirConyuge(cedula, nombres);
        // Normalización de personas, grupo Domicilio (16/10/2026): antes se completaba sobre la
        // propia Persona dentro de completarCamposTandaA -- ahora es un objeto aparte
        // (personas_domicilio), siempre obligatorio. idDepartamentoDomicilio es el mismo
        // departamento real de la ciudad elegida (el cargador no simula un desacierto del
        // usuario, así que siempre pasa validarCiudadPerteneceADepartamento).
        Ciudad ciudadDomicilio = ciudadDeDomicilioLibre(domicilio);
        PersonaDomicilio domicilioPersona = construirDomicilio(cedula, domicilio, ciudadDomicilio);
        // Normalización de personas, grupo Laborales (16/10/2026): antes se completaba sobre la
        // propia Persona (completarCamposTandaB) -- ahora es un objeto aparte (personas_laborales),
        // siempre obligatorio.
        PersonaLaboral laboralPersona = construirLaboral(cedula, ocupacion, domicilio, ingreso, presentaIngreso);
        long idPersona = servicioClientes.alta(persona, referencias, conyuge, domicilioPersona,
                ciudadDomicilio.getIdDepartamento(), laboralPersona, idOficial);
        // generarAutorizacionBuro (más abajo) necesita persona.getIdPersona() -- alta() no lo
        // setea de vuelta sobre el objeto recibido, solo devuelve el id.
        persona.setIdPersona(idPersona);
        // D-117 (punto 1): personas.fecha_registro/fecha_actualizacion usan now() de SQL,
        // independiente del Reloj -- "fecha de alta del cliente" quedaba siempre en hoy real.
        backdatearColumna("personas", "fecha_registro", "id_persona", idPersona, instanteAlta);
        backdatearColumna("personas", "fecha_actualizacion", "id_persona", idPersona, instanteAlta);

        long idDocCedula = servicioDocumentos.cargarDocumento(idPersona, tiposDocumento.get("CEDULA_IDENTIDAD"),
                "cedula.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idDocCedula, instanteAlta);
        if (presentaIngreso) {
            long idDocIngreso = servicioDocumentos.cargarDocumento(idPersona, tiposDocumento.get("COMPROBANTE_INGRESO"),
                    "comprobante_ingreso.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
            backdatearArchivoDeDocumento(idDocIngreso, instanteAlta);
        }

        LocalDateTime instanteConsentimiento = enHora(fecha, 8, 10);
        Reloj.fijarParaCargador(instanteConsentimiento);
        // Hallazgo (15/10/2026, corriendo el cargador tras recrear la base): RF03/RF04 reemplazaron
        // el camino viejo (cargarDocumento genérico + registrarConsentimiento aparte, bloqueado
        // ahora con "Autorización de consulta al buró se carga desde su propio paso, no desde
        // acá") por un flujo de 2 pasos: generarAutorizacionBuro (numera + audita "generó hoy")
        // y recién después cargarAutorizacionFirmada (exige que se haya generado el mismo día,
        // y adentro llama a registrarConsentimiento sola -- ya no se llama aparte).
        // auditoria_operaciones es de solo inserción (trg_bloquear_actualizacion) -- a diferencia
        // del resto de las tablas, su fecha_hora (now() real) NO se puede backdatear. Se libera el
        // Reloj un instante (vuelve a la fecha/hora REAL) solo para estos 2 pasos, para que
        // autorizacionGeneradaHoy (que compara esa fecha real contra Reloj.hoy()) encuentre "hoy"
        // real = now() real, y se vuelve a fijar la fecha simulada enseguida para el resto.
        Reloj.liberarCargador();
        servicioConsultaCrediticia.generarAutorizacionBuro(persona, idOficial);
        long idAutorizacion = servicioConsultaCrediticia.cargarAutorizacionFirmada(idPersona,
                tiposDocumento.get("AUTORIZACION_CONSULTA_BURO"), "autorizacion_buro.pdf", "application/pdf",
                CONTENIDO_PDF_DEMO, idOficial);
        Reloj.fijarParaCargador(instanteConsentimiento);
        backdatearArchivoDeDocumento(idAutorizacion, instanteConsentimiento);
        long idConsentimiento = idUltimoConsentimiento(idPersona);
        // ConsentimientoCrediticioDAO.insertar usa now() de SQL para fecha_consentimiento/
        // fecha_registro, independiente del Reloj -- si no se corrige, la vigencia de 30 días
        // (buro.dias_vigencia_consentimiento) se cuenta desde HOY real en vez de la fecha simulada,
        // y con un H lejano (pasado o futuro) el consentimiento aparece vencido o recién emitido sin
        // sentido (encontrado probando el cargador con -Dcargador.fecha en una fecha futura).
        backdatearColumna("consentimientos_crediticios", "fecha_consentimiento", "id_consentimiento",
                idConsentimiento, instanteConsentimiento);
        backdatearColumna("consentimientos_crediticios", "fecha_registro", "id_consentimiento",
                idConsentimiento, instanteConsentimiento);

        LocalDateTime instanteConsulta = enHora(fecha, 8, 20);
        Reloj.fijarParaCargador(instanteConsulta);
        // Mismo hallazgo que la autorización, un paso más adelante: RF03 también sacó
        // RESULTADO_CONSULTA_BURO de cargarDocumento genérico -- va por cargarInformeBuro
        // (sin el chequeo de "mismo día" de la autorización, no hace falta liberar el Reloj acá).
        long idResultado = servicioConsultaCrediticia.cargarInformeBuro(idPersona,
                tiposDocumento.get("RESULTADO_CONSULTA_BURO"), "resultado_buro.pdf", "application/pdf",
                CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idResultado, instanteConsulta);
        long idConsulta = servicioConsultaCrediticia.registrarConsulta(idPersona, "Informconf", new BigDecimal("650"),
                indicadorMora ? "Con antecedentes" : "Sin antecedentes", indicadorMora,
                indicadorMora ? "Registra mora vigente (dato de demostración)." : "Sin deudas registradas (dato de demostración).",
                idResultado, idOficial);
        backdatearColumna("consultas_crediticias", "fecha_hora_consulta", "id_consulta_crediticia", idConsulta,
                instanteConsulta);

        return idPersona;
    }

    /**
     * Bloque 7/E10, Tanda A (D-126): completa los campos nuevos, obligatorios desde ahora, a
     * partir de datos que el cargador ya tenía más algunos valores sintéticos de demostración
     * (cónyuge, segunda referencia) derivados de la cédula para que cada persona tenga los suyos,
     * sin datos reales. estadoCivil es "Casado/a" salvo las de CEDULAS_SOLTERAS (grupo Cónyuge,
     * 16/10/2026) -- el cónyuge solo es obligatorio para las que quedan Casado/a.
     * Normalización de personas, grupo Domicilio (16/10/2026): el domicilio (que venía como un
     * solo texto libre "Calle N, Barrio X, Ciudad", partido en sus componentes) se armó acá hasta
     * ahora -- ahora es construirDomicilio/ciudadDeDomicilioLibre, aparte, porque personas_domicilio
     * ya no es parte de Persona.
     */
    private List<PersonaReferencia> completarCamposTandaA(Persona persona, String cedula, String nombres,
            String telefono, LocalDate fecha) throws SQLException {
        persona.setFechaVencimientoCedula(fecha.plusYears(5));

        String colaCedula = cedula.length() >= 6 ? cedula.substring(cedula.length() - 6) : cedula;

        // Normalización de personas, grupo Referencias (15/10/2026): antes esta persona quedaba
        // SIN la primera referencia (solo se completaba la segunda) -- servicioClientes.alta
        // la exige desde siempre (ServicioClientes.validarDatosTandaA/validarReferencias), así
        // que el cargador ya venía roto en este punto; de paso que se arma la lista nueva, se
        // agrega la que faltaba.
        List<PersonaReferencia> referencias = new ArrayList<>();
        PersonaReferencia referencia1 = new PersonaReferencia();
        referencia1.setNumeroReferencia(1);
        referencia1.setNombre("Referencia personal 1 de " + nombres + " (demostración)");
        referencia1.setTelefono("09" + colaCedula + "3");
        referencias.add(referencia1);
        PersonaReferencia referencia2 = new PersonaReferencia();
        referencia2.setNumeroReferencia(2);
        referencia2.setNombre("Referencia personal 2 de " + nombres + " (demostración)");
        referencia2.setTelefono("09" + colaCedula + "2");
        referencias.add(referencia2);
        return referencias;
    }

    /** Normalización de personas, grupo Domicilio (16/10/2026): el domicilio venía como un solo
     * texto libre "Calle N, Barrio X, Ciudad" -- este helper resuelve solo la ciudad (la parte que
     * también necesita crearPersona, para el idDepartamentoDomicilio de alta()), compartido con
     * construirDomicilio para no repetir el split. */
    private Ciudad ciudadDeDomicilioLibre(String domicilioLibre) throws SQLException {
        String[] partesDomicilio = domicilioLibre.split(",\\s*");
        String nombreCiudad = partesDomicilio[partesDomicilio.length - 1].trim();
        return ciudadPorNombre(nombreCiudad);
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): dato de domicilio, antes completado
     * sobre la propia Persona dentro de completarCamposTandaA -- ahora un PersonaDomicilio aparte
     * (personas_domicilio), siempre obligatorio. Mismo criterio de siempre: "calle" a
     * direccionDomicilio (lo que es), referenciaDomicilio queda sin usar (opcional, el cargador no
     * tiene un dato de ese tipo), tipoVivienda/antigüedad sintéticos derivados de la cédula.
     */
    private PersonaDomicilio construirDomicilio(String cedula, String domicilioLibre, Ciudad ciudad) {
        String[] partesDomicilio = domicilioLibre.split(",\\s*");
        String calle = partesDomicilio[0].trim();
        String barrio = partesDomicilio.length > 1 ? partesDomicilio[1].trim().replaceFirst("^Barrio ", "") : "Centro";

        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdCiudad(ciudad.getIdCiudad());
        domicilio.setBarrio(barrio);
        domicilio.setDireccionDomicilio(calle);
        int digito = Math.abs(cedula.hashCode()) % 3;
        domicilio.setTipoVivienda(digito == 0 ? "PROPIA" : digito == 1 ? "ALQUILADA" : "FAMILIAR");
        domicilio.setAntiguedadDomicilioAnios((Math.abs(cedula.hashCode()) % 15) + 1);
        return domicilio;
    }

    /**
     * Normalización de personas, grupo Cónyuge (16/10/2026): dato sintético de demostración
     * derivado de la cédula (mismo criterio que antes, cuando vivía en completarCamposTandaA) --
     * ahora un PersonaConyuge aparte en vez de 3 setters sobre Persona. Solo se llama para las
     * personas que quedan "Casado/a" (ver CEDULAS_SOLTERAS en crearPersona).
     */
    private PersonaConyuge construirConyuge(String cedula, String nombres) {
        String colaCedula = cedula.length() >= 6 ? cedula.substring(cedula.length() - 6) : cedula;
        PersonaConyuge conyuge = new PersonaConyuge();
        conyuge.setNombre("Cónyuge de " + nombres + " (demostración)");
        conyuge.setCedula(String.valueOf(Long.parseLong(cedula) + 1));
        conyuge.setTelefono("09" + colaCedula + "1");
        return conyuge;
    }

    /**
     * Normalización de personas, grupo Laborales (16/10/2026): antes completaba situación laboral
     * (derivada de la ocupación que el cargador ya tenía, para no duplicar ese dato) + empleador
     * donde corresponde sobre la propia Persona (completarCamposTandaB) -- ahora arma un
     * PersonaLaboral aparte, con ingreso/comprobante de ingreso (que antes se seteaban en
     * crearPersona) y una cuota de otras deudas modesta de demostración (0 para la mayoría, un
     * monto chico para un par de personas -- útil cuando la fórmula de capacidad de pago la
     * empiece a usar de verdad). Situación laboral: se evita a propósito INDEPENDIENTE/
     * DESEMPLEADO (excluidos por defecto de credito.situaciones_laborales_admitidas) -- si no, el
     * cargador fallaría al crear la solicitud de esa persona.
     */
    private PersonaLaboral construirLaboral(String cedula, String ocupacion, String domicilioLibre,
            BigDecimal ingreso, boolean presentaIngreso) {
        PersonaLaboral laboral = new PersonaLaboral();
        String situacionLaboral;
        if (ocupacion.contains("doméstica")) {
            situacionLaboral = "EMPLEADO_DOMESTICO";
        } else if (ocupacion.contains("en comercio")) {
            situacionLaboral = "ASALARIADO_SECTOR_PRIVADO";
        } else if (ocupacion.equals("Docente")) {
            situacionLaboral = "FUNCIONARIO_PUBLICO";
        } else {
            // Transportista, costurera/electricista/mecánico/plomero "independiente", peluquera con
            // salón propio, feriante, comerciante con kiosco: todos cuentapropistas con actividad
            // propia -- COMERCIANTE, no INDEPENDIENTE (D-127: INDEPENDIENTE no está admitido por
            // defecto y el cargador dejaría de poder crear la solicitud).
            situacionLaboral = "COMERCIANTE";
        }
        laboral.setSituacionLaboral(situacionLaboral);
        if (SITUACIONES_LABORALES_CON_EMPLEADOR_CARGADOR.contains(situacionLaboral)) {
            String ciudad = domicilioLibre.substring(domicilioLibre.lastIndexOf(',') + 1).trim();
            laboral.setEmpleadorNombre(ocupacion.equals("Docente") ? "Ministerio de Educación y Ciencias"
                    : "Comercio local (demostración)");
            laboral.setEmpleadorDireccion("Zona céntrica, " + ciudad);
            laboral.setEmpleadorTelefono("021" + cedula.substring(Math.max(0, cedula.length() - 6)));
            laboral.setCargo(ocupacion);
            laboral.setAntiguedadLaboralAnios((Math.abs(cedula.hashCode()) % 10) + 1);
        }
        laboral.setIngresoNetoComprobado(ingreso);
        laboral.setPresentaComprobanteIngreso(presentaIngreso);
        // Cuota de otras deudas: 0 para la mayoría; un par con un monto chico de demostración.
        int digitoFinal = Character.getNumericValue(cedula.charAt(cedula.length() - 1));
        laboral.setCuotaOtrasDeudasMensual(digitoFinal % 4 == 0 ? new BigDecimal("300000") : BigDecimal.ZERO);
        return laboral;
    }

    private static final java.util.Set<String> SITUACIONES_LABORALES_CON_EMPLEADOR_CARGADOR =
            new java.util.HashSet<>(java.util.Arrays.asList("ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO"));

    private Ciudad ciudadPorNombre(String nombreCiudad) throws SQLException {
        if (cacheCiudadesPorNombre == null) {
            cacheCiudadesPorNombre = new HashMap<>();
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                for (Ciudad ciudad : ciudadDAO.listarActivas(conexion)) {
                    cacheCiudadesPorNombre.put(ciudad.getNombre(), ciudad);
                }
            }
        }
        Ciudad ciudad = cacheCiudadesPorNombre.get(nombreCiudad);
        if (ciudad == null) {
            throw new IllegalStateException("Ciudad no encontrada en el catálogo (bd/03_parametros.sql): \"" + nombreCiudad + "\".");
        }
        return ciudad;
    }

    /** Bloque 7/E10, Tanda A (D-126): resuelve el id de un motivo de rechazo por su código. */
    private long idMotivoRechazoPorCodigo(String codigo) throws SQLException {
        if (cacheMotivosRechazoPorCodigo == null) {
            cacheMotivosRechazoPorCodigo = new HashMap<>();
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                for (MotivoRechazoSolicitud motivo : motivoRechazoSolicitudDAO.listarActivos(conexion)) {
                    cacheMotivosRechazoPorCodigo.put(motivo.getCodigo(), motivo.getIdMotivoRechazo());
                }
            }
        }
        Long id = cacheMotivosRechazoPorCodigo.get(codigo);
        if (id == null) {
            throw new IllegalStateException("Motivo de rechazo no encontrado (bd/03_parametros.sql): \"" + codigo + "\".");
        }
        return id;
    }

    // =====================================================================
    // Helpers de originacion (solicitud -> aprobacion -> formalizacion; el desembolso se hace
    // aparte, en la Fase 2 del método principal -- punto 14, para poder intercalarlo con los
    // cobros en orden cronológico real y así agrupar la caja por día real, sin importar si el
    // evento es un desembolso o un cobro).
    // =====================================================================

    private long originarSinDesembolsar(String cedula, String nombres, String apellidos, boolean presentaIngreso,
            boolean indicadorMora, BigDecimal ingreso, String ocupacion, String telefono, String domicilio,
            BigDecimal monto, int plazoMeses, LocalDate fechaSolicitud, LocalDate fechaAprobacion,
            LocalDate fechaFormalizacion, LocalDate primerVencimiento) throws SQLException {
        long idPersona = crearPersona(cedula, nombres, apellidos, presentaIngreso, indicadorMora, ingreso, ocupacion,
                telefono, domicilio, fechaSolicitud);
        long idPrestamo = solicitarAprobarYFormalizar(idPersona, monto, plazoMeses, fechaSolicitud,
                fechaAprobacion, fechaFormalizacion, primerVencimiento);
        registrarEnReporte("Préstamo", nombres + " " + apellidos, cedula, idPrestamo, true);
        return idPrestamo;
    }

    private long solicitarAprobarYFormalizar(long idCliente, BigDecimal monto,
            int plazoMeses, LocalDate fechaSolicitud, LocalDate fechaAprobacion, LocalDate fechaFormalizacion,
            LocalDate primerVencimiento) throws SQLException {
        LocalDateTime instanteSolicitud = enHora(fechaSolicitud, 9, 0);
        Reloj.fijarParaCargador(instanteSolicitud);
        long idSolicitud = servicioSolicitudes.crear(idCliente, monto, plazoMeses, DESTINO_DEMO, primerVencimiento,
                idOficial);
        // D-117 (punto 1): solicitudes_credito.fecha_solicitud/fecha_envio_aprobacion/fecha_decision
        // usan now() de SQL, independiente del Reloj -- "Enviada hoy" en toda solicitud, sin
        // importar la fecha simulada.
        backdatearColumna("solicitudes_credito", "fecha_solicitud", "id_solicitud", idSolicitud, instanteSolicitud);

        LocalDateTime instanteEnvio = enHora(fechaSolicitud, 9, 20);
        Reloj.fijarParaCargador(instanteEnvio);
        servicioSolicitudes.enviarAAprobacion(idSolicitud, idOficial);
        backdatearColumna("solicitudes_credito", "fecha_envio_aprobacion", "id_solicitud", idSolicitud, instanteEnvio);

        LocalDateTime instanteAprobacion = enHora(fechaAprobacion, 10, 0);
        Reloj.fijarParaCargador(instanteAprobacion);
        servicioAprobacionCredito.aprobar(idSolicitud, idGerente);
        backdatearColumna("solicitudes_credito", "fecha_decision", "id_solicitud", idSolicitud, instanteAprobacion);

        LocalDateTime instanteFormalizacion = enHora(fechaFormalizacion, 9, 0);
        Reloj.fijarParaCargador(instanteFormalizacion);
        // F2 (21/10/2026): puedeResolver sin efecto acá -- esta carga siempre formaliza sin
        // bloqueos, nunca llega a usar el texto (a)/(b).
        long idPrestamo = servicioFormalizacion.generarDocumentos(idSolicitud, primerVencimiento, idOficial, false);
        // ServicioFormalizacion.generarDocumentos usa now() de SQL para prestamos.fecha_formalizacion,
        // independiente del Reloj -- se corrige acotado a esta fila, mismo patron que humo_http.sh (D-113).
        backdatearColumna("prestamos", "fecha_formalizacion", "id_prestamo", idPrestamo, instanteFormalizacion);
        subirDocumentosFirmados(idPrestamo, fechaFormalizacion);
        return idPrestamo;
    }

    private long solicitarYAprobar(String cedula, String nombres, String apellidos, boolean presentaIngreso,
            boolean indicadorMora, BigDecimal ingreso, String ocupacion, String telefono, String domicilio,
            BigDecimal monto, int plazoMeses, LocalDate fechaSolicitud, LocalDate fechaAprobacion)
            throws SQLException {
        long idPersona = crearPersona(cedula, nombres, apellidos, presentaIngreso, indicadorMora, ingreso, ocupacion,
                telefono, domicilio, fechaSolicitud);
        // primerVencimiento provisorio -- se recalcula recien al formalizar (fase 3), con la fecha
        // real de hoy; aca solo hace falta un valor futuro razonable para poder crear/enviar/aprobar.
        LocalDate primerVencimientoProvisorio = fechaSolicitud.plusDays(40);

        LocalDateTime instanteSolicitud = enHora(fechaSolicitud, 9, 0);
        Reloj.fijarParaCargador(instanteSolicitud);
        long idSolicitud = servicioSolicitudes.crear(idPersona, monto, plazoMeses, DESTINO_DEMO,
                primerVencimientoProvisorio, idOficial);
        backdatearColumna("solicitudes_credito", "fecha_solicitud", "id_solicitud", idSolicitud, instanteSolicitud);

        LocalDateTime instanteEnvio = enHora(fechaSolicitud, 9, 20);
        Reloj.fijarParaCargador(instanteEnvio);
        servicioSolicitudes.enviarAAprobacion(idSolicitud, idOficial);
        backdatearColumna("solicitudes_credito", "fecha_envio_aprobacion", "id_solicitud", idSolicitud, instanteEnvio);

        LocalDateTime instanteAprobacion = enHora(fechaAprobacion, 10, 0);
        Reloj.fijarParaCargador(instanteAprobacion);
        servicioAprobacionCredito.aprobar(idSolicitud, idGerente);
        backdatearColumna("solicitudes_credito", "fecha_decision", "id_solicitud", idSolicitud, instanteAprobacion);

        registrarEnReporte("Solicitud aprobada (sin formalizar aún)", nombres + " " + apellidos, cedula, idSolicitud,
                false);
        return idSolicitud; // en realidad es idSolicitud -- se formaliza en formalizarSinDesembolsar
    }

    private void formalizarSinDesembolsar(long idSolicitud, LocalDate primerVencimiento, LocalDate fechaFormalizacion,
            String cedula, String nombres, String apellidos) throws SQLException {
        LocalDateTime instanteFormalizacion = enHora(fechaFormalizacion, 11, 0);
        Reloj.fijarParaCargador(instanteFormalizacion);
        // F2 (21/10/2026): puedeResolver sin efecto acá (ver nota de más arriba).
        long idPrestamo = servicioFormalizacion.generarDocumentos(idSolicitud, primerVencimiento, idOficial, false);
        backdatearColumna("prestamos", "fecha_formalizacion", "id_prestamo", idPrestamo, instanteFormalizacion);
        subirDocumentosFirmados(idPrestamo, fechaFormalizacion);
        // No se desembolsa (E9: "Aprobado, sin desembolsar" -- Joel lo completa en vivo).
        actualizarFilaReportePorCedula(cedula, "Préstamo formalizado, sin desembolsar", idPrestamo);
    }

    private void solicitarYRechazar(String cedula, String nombres, String apellidos, boolean presentaIngreso,
            boolean indicadorMora, BigDecimal ingreso, String ocupacion, String telefono, String domicilio,
            BigDecimal monto, int plazoMeses, LocalDate fechaSolicitud, LocalDate fechaRechazo,
            String codigoMotivoRechazo, String detalleMotivoRechazo) throws SQLException {
        long idPersona = crearPersona(cedula, nombres, apellidos, presentaIngreso, indicadorMora, ingreso, ocupacion,
                telefono, domicilio, fechaSolicitud);
        LocalDate primerVencimiento = fechaSolicitud.plusDays(40);

        LocalDateTime instanteSolicitud = enHora(fechaSolicitud, 9, 0);
        Reloj.fijarParaCargador(instanteSolicitud);
        long idSolicitud = servicioSolicitudes.crear(idPersona, monto, plazoMeses, DESTINO_DEMO, primerVencimiento,
                idOficial);
        backdatearColumna("solicitudes_credito", "fecha_solicitud", "id_solicitud", idSolicitud, instanteSolicitud);

        LocalDateTime instanteEnvio = enHora(fechaSolicitud, 9, 20);
        Reloj.fijarParaCargador(instanteEnvio);
        servicioSolicitudes.enviarAAprobacion(idSolicitud, idOficial);
        backdatearColumna("solicitudes_credito", "fecha_envio_aprobacion", "id_solicitud", idSolicitud, instanteEnvio);

        LocalDateTime instanteRechazo = enHora(fechaRechazo, 10, 0);
        Reloj.fijarParaCargador(instanteRechazo);
        servicioAprobacionCredito.rechazar(idSolicitud, idMotivoRechazoPorCodigo(codigoMotivoRechazo),
                detalleMotivoRechazo, idGerente);
        backdatearColumna("solicitudes_credito", "fecha_decision", "id_solicitud", idSolicitud, instanteRechazo);

        registrarEnReporte("Solicitud RECHAZADA", nombres + " " + apellidos, cedula, idSolicitud, false);
    }

    private void solicitarYDejarPendiente(String cedula, String nombres, String apellidos, boolean presentaIngreso,
            boolean indicadorMora, BigDecimal ingreso, String ocupacion, String telefono, String domicilio,
            BigDecimal monto, int plazoMeses, LocalDate fechaSolicitud) throws SQLException {
        long idPersona = crearPersona(cedula, nombres, apellidos, presentaIngreso, indicadorMora, ingreso, ocupacion,
                telefono, domicilio, fechaSolicitud);
        LocalDate primerVencimiento = fechaSolicitud.plusDays(40);

        LocalDateTime instanteSolicitud = enHora(fechaSolicitud, 9, 0);
        Reloj.fijarParaCargador(instanteSolicitud);
        long idSolicitud = servicioSolicitudes.crear(idPersona, monto, plazoMeses, DESTINO_DEMO, primerVencimiento,
                idOficial);
        backdatearColumna("solicitudes_credito", "fecha_solicitud", "id_solicitud", idSolicitud, instanteSolicitud);

        LocalDateTime instanteEnvio = enHora(fechaSolicitud, 9, 20);
        Reloj.fijarParaCargador(instanteEnvio);
        servicioSolicitudes.enviarAAprobacion(idSolicitud, idOficial);
        backdatearColumna("solicitudes_credito", "fecha_envio_aprobacion", "id_solicitud", idSolicitud, instanteEnvio);

        registrarEnReporte("Solicitud PENDIENTE_APROBACION", nombres + " " + apellidos, cedula, idSolicitud, false);
    }

    private void subirDocumentosFirmados(long idPrestamo, LocalDate fecha) throws SQLException {
        LocalDateTime instante = enHora(fecha, 11, 30);
        Reloj.fijarParaCargador(instante);
        long idContrato = servicioDocumentos.cargarDocumentoPrestamo(idPrestamo, tiposDocumento.get("CONTRATO_FIRMADO"),
                "contrato_firmado.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idContrato, instante);
        long idPagare = servicioDocumentos.cargarDocumentoPrestamo(idPrestamo, tiposDocumento.get("PAGARE_FIRMADO"),
                "pagare_firmado.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idPagare, instante);
        // Reprueba Lote 1 (hallazgo de Joel, RF13): el checklist ahora exige también el plan de
        // pagos firmado.
        long idPlanPagos = servicioDocumentos.cargarDocumentoPrestamo(idPrestamo, tiposDocumento.get("PLAN_PAGOS_FIRMADO"),
                "plan_pagos_firmado.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idPlanPagos, instante);
    }

    // =====================================================================
    // Helpers de caja / desembolso / cobranza / reversion
    // =====================================================================

    /**
     * D-115: varios DAO (sesiones_caja, pagos, desembolsos, movimientos_caja, reversiones_pago)
     * usan now() de SQL para su columna de fecha/hora principal, independiente de Reloj -- se
     * corrige acotado a la fila recién creada, mismo patrón que humo_http.sh usa para el fixture
     * del Bloque 4 (D-113): nunca se toca ninguna otra fila, nunca se inventa un valor, solo se
     * reemplaza el "ahora real" por el instante simulado de ESTA operación puntual.
     */
    private void backdatearColumna(String tabla, String columna, String columnaId, long id, LocalDateTime instante)
            throws SQLException {
        String sql = "UPDATE " + tabla + " SET " + columna + " = ? WHERE " + columnaId + " = ?";
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setTimestamp(1, java.sql.Timestamp.valueOf(instante));
            sentencia.setLong(2, id);
            sentencia.executeUpdate();
        }
    }

    /** D-117 (punto 1): archivos_binarios.fecha_carga usa now() de SQL, independiente del Reloj --
     * cada documento subido (cédula, comprobante, autorización/resultado de buró, contrato/pagaré
     * firmados) quedaba con la fecha real de carga en vez de la simulada. ServicioDocumentos.
     * cargarDocumento/cargarDocumentoPrestamo devuelven el id de documentos_expediente, no el de
     * archivos_binarios -- se resuelve con una lectura chica antes de corregir. */
    /** cargarAutorizacionFirmada ya registra el consentimiento adentro (no devuelve su id) --
     * se relee el último de la persona para poder backdatear sus 2 columnas con now(). */
    private long idUltimoConsentimiento(long idPersona) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return servicioConsultaCrediticia.obtenerUltimoConsentimiento(conexion, idPersona).getIdConsentimiento();
        }
    }

    private void backdatearArchivoDeDocumento(long idDocumento, LocalDateTime instante) throws SQLException {
        long idArchivo;
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT id_archivo FROM documentos_expediente WHERE id_documento = ?")) {
            sentencia.setLong(1, idDocumento);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                idArchivo = resultado.getLong(1);
            }
        }
        backdatearColumna("archivos_binarios", "fecha_carga", "id_archivo", idArchivo, instante);
    }

    /**
     * Punto 14 (pedido de Joel): antes, cada operación de caja abría y cerraba su propia sesión de
     * 10 minutos (varias "cajas" el mismo día) -- una caja real se abre una vez por día hábil y
     * queda abierta hasta la tarde, con todas las operaciones del día adentro. Reutiliza la sesión
     * ya abierta si la fecha pedida es la misma que la sesión actual; si es una fecha distinta,
     * cierra la anterior (a las 17:30 de SU día) y abre una nueva a las 08:00 del día pedido, con
     * `fondoDelDia` como entrega inicial del gerente (si es mayor a cero).
     */
    private long obtenerOAbrirSesionDelDia(LocalDate fecha, BigDecimal fondoDelDia) throws SQLException {
        if (idSesionCajaActual != null && fecha.equals(fechaSesionCajaActual)) {
            return idSesionCajaActual;
        }
        cerrarSesionActualSiHayUna();
        LocalDateTime instanteApertura = enHora(fecha, 8, 0);
        long idSesion = abrirCajaEn(BigDecimal.ZERO, instanteApertura);
        if (fondoDelDia.compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime instanteEntrega = enHora(fecha, 8, 15);
            Reloj.fijarParaCargador(instanteEntrega);
            servicioCaja.registrarEntregaGerente(idGerente, PerfilUsuario.GERENTE_ADMINISTRADOR, fondoDelDia,
                    "Fondo del día (dato de demostración).");
            backdatearUltimoMovimiento(idSesion, instanteEntrega);
        }
        idSesionCajaActual = idSesion;
        fechaSesionCajaActual = fecha;
        return idSesion;
    }

    private void cerrarSesionActualSiHayUna() throws SQLException {
        if (idSesionCajaActual != null) {
            cerrarCajaEn(idSesionCajaActual, enHora(fechaSesionCajaActual, 17, 30));
            idSesionCajaActual = null;
            fechaSesionCajaActual = null;
        }
    }

    /** Abre la caja y corrige de inmediato fecha_hora_apertura -- antes de cualquier operación que
     * valide ServicioCaja.esDeHoy (todas: desembolso, cobro, cancelación, anticipo, reversión). */
    private long abrirCajaEn(BigDecimal saldoInicial, LocalDateTime instante) throws SQLException {
        Reloj.fijarParaCargador(instante);
        long idSesion = servicioCaja.abrirCaja(idCajera, saldoInicial);
        backdatearColumna("sesiones_caja", "fecha_hora_apertura", "id_sesion_caja", idSesion, instante);
        return idSesion;
    }

    private void cerrarCajaEn(long idSesion, LocalDateTime instante) throws SQLException {
        BigDecimal saldoTeorico;
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            saldoTeorico = servicioCaja.calcularSaldoTeorico(lectura, idSesion);
        }
        Reloj.fijarParaCargador(instante);
        servicioCaja.cerrarCaja(idCajera, saldoTeorico, null);
        backdatearColumna("sesiones_caja", "fecha_hora_cierre", "id_sesion_caja", idSesion, instante);
        // Solo el movimiento RENDICION_GERENTE que acaba de crear cerrarCaja -- punto 14: con una
        // sesión por día (varios movimientos adentro), un UPDATE masivo por id_sesion_caja pisaría
        // también la hora real de los desembolsos/cobros/entregas anteriores del mismo día con la
        // hora de cierre (17:30 para todos). Cada movimiento se backdatea ahora individualmente,
        // apenas se crea (ver backdatearUltimoMovimiento).
        backdatearUltimoMovimiento(idSesion, instante);
    }

    /** D-115/punto 14: movimientos_caja.fecha_hora usa now() de SQL -- cada operación de caja crea
     * exactamente un movimiento nuevo; se resuelve por "el último de esta sesión" (id autoincremental),
     * igual que registrarGestionBackdateada resuelve la gestión recién creada. */
    private void backdatearUltimoMovimiento(long idSesion, LocalDateTime instante) throws SQLException {
        long idMovimiento;
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT id_movimiento_caja FROM movimientos_caja WHERE id_sesion_caja = ? "
                                + "ORDER BY id_movimiento_caja DESC LIMIT 1")) {
            sentencia.setLong(1, idSesion);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                idMovimiento = resultado.getLong(1);
            }
        }
        backdatearColumna("movimientos_caja", "fecha_hora", "id_movimiento_caja", idMovimiento, instante);
    }

    private void desembolsar(long idPrestamo, BigDecimal monto, LocalDate fecha) throws SQLException {
        LocalDateTime instante = enHora(fecha, 10, 0);
        long idSesion = obtenerOAbrirSesionDelDia(fecha, monto);
        Reloj.fijarParaCargador(instante);
        // Bloque 7/E10 (D-130, pedido de Joel): desde ahora el comprobante se imprime "para
        // firma" ANTES de confirmar -- confirmarDesembolso ya exige que el número exista.
        servicioDesembolso.generarComprobantePreDesembolso(idPrestamo, idCajera);
        servicioDesembolso.confirmarDesembolso(idPrestamo, UUID.randomUUID().toString(), true, idCajera);
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            long idDesembolso = servicioDesembolso.obtenerConfirmadoPorPrestamo(lectura, idPrestamo).getIdDesembolso();
            backdatearColumna("desembolsos", "fecha_hora_desembolso", "id_desembolso", idDesembolso, instante);
        }
        backdatearUltimoMovimiento(idSesion, instante);
    }

    private long cobrarCuotaATiempo(long idPrestamo, LocalDate fecha, int hora) throws SQLException {
        // Hallazgo (15/10/2026, corriendo el cargador tras recrear la base): los offsets fijos de
        // los 11 escenarios (H.minusDays(N), separados por 30 días redondos) no coinciden exacto
        // con el vencimiento real de cada cuota (el calendario de cuotas es mensual real, 28-31
        // días según el mes) -- calcularMontoCobrableNormal exige fecha == vencimiento real para
        // contar la cuota como cobrable (si no, da 0 -- D-144, "Cobrar" ya no cobra nada no
        // vencido). Con el tiempo (según qué día real sea "H", todos los offsets se recalculan
        // desde HOY) el desfase de 1-3 días por cuota se acumula hasta que algún mes lo cruza.
        // Se usa siempre la fecha REAL de la próxima cuota pendiente (ya existe en el sistema,
        // ServicioCobranza.obtenerProximaCuotaPendiente, D-144) en vez del offset recibido -- el
        // parámetro "fecha" queda sin usar para el cálculo, se conserva en la firma para no tocar
        // los ~10 call sites de este método.
        LocalDate fechaReal = fechaProximaCuotaPendiente(idPrestamo);
        BigDecimal monto;
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            monto = servicioCobranza.calcularMontoCobrableNormal(lectura, idPrestamo, fechaReal);
        }
        LocalDateTime instante = enHora(fechaReal, hora, 0);
        long idSesion = obtenerOAbrirSesionDelDia(fechaReal, new BigDecimal("300000"));
        Reloj.fijarParaCargador(instante);
        Pago pago = servicioCobranza.confirmarCobro(idPrestamo, UUID.randomUUID().toString(), monto, null, fechaReal,
                idCajera);
        backdatearColumna("pagos", "fecha_hora_pago", "id_pago", pago.getIdPago(), instante);
        backdatearUltimoMovimiento(idSesion, instante);
        backdatearFinalizacionSiCorresponde(idPrestamo, instante);
        return pago.getIdPago();
    }

    /** Ver el hallazgo de arriba (cobrarCuotaATiempo) -- misma causa, mismo arreglo lo necesitan
     * otros helpers de cobro que reciben una fecha fija como si fuera el vencimiento exacto. */
    private LocalDate fechaProximaCuotaPendiente(long idPrestamo) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            py.com.capitalcredito.sgcpc.modelos.Cuota proxima =
                    servicioCobranza.obtenerProximaCuotaPendiente(conexion, idPrestamo);
            if (proxima == null) {
                throw new IllegalStateException("El préstamo " + idPrestamo + " no tiene ninguna cuota pendiente.");
            }
            return proxima.getFechaVencimiento();
        }
    }

    /** Punto 3/4 (pedido de Joel): el 50% se calculaba sobre calcularMontoCobrableNormal (lo vencido
     * MÁS la próxima cuota, casi el doble de lo realmente vencido) -- al pagar la mitad de eso se
     * cancelaba casi toda la deuda exigible real, dejando un residuo ínfimo (el "monto exigible" de
     * 1.537 Gs. que reportó Joel) y de paso cumplía sin querer la promesa en el mismo acto
     * (sinCuotasVencidas pasaba a true, vaciando la columna "Promesa" de /mora). Ahora el 50% se
     * calcula sobre calcularDeudaExigible (solo lo estrictamente vencido), que sí deja una mora real
     * pendiente y la promesa visible. */
    private void cobrarParcialConPromesa(long idPrestamo, LocalDate fecha, int hora, LocalDate fechaPromesa)
            throws SQLException {
        BigDecimal montoExigible;
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            montoExigible = servicioCobranza.calcularDeudaExigible(lectura, idPrestamo, fecha).getTotalGeneral();
        }
        BigDecimal montoParcial = montoExigible.divide(new BigDecimal("2"), 0, RoundingMode.DOWN);
        LocalDateTime instante = enHora(fecha, hora, 0);
        long idSesion = obtenerOAbrirSesionDelDia(fecha, new BigDecimal("300000"));
        Reloj.fijarParaCargador(instante);
        Pago pago = servicioCobranza.confirmarCobro(idPrestamo, UUID.randomUUID().toString(), montoParcial,
                fechaPromesa, fecha, idCajera);
        backdatearColumna("pagos", "fecha_hora_pago", "id_pago", pago.getIdPago(), instante);
        backdatearUltimoMovimiento(idSesion, instante);
        // D-95: confirmarCobro genera sola una gestión PAGO_PARCIAL con la promesa -- su fecha_hora
        // también usa now() de SQL (punto 4, mismo bug que el resto de gestiones_cobranza).
        backdatearGestionDelPago(pago.getIdPago(), instante);
    }

    private void cancelarTotal(long idPrestamo, LocalDate fecha, int hora) throws SQLException {
        BigDecimal monto;
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            ResultadoLiquidacion resultado = servicioCobranza.calcularPreviewCancelacionTotal(lectura, idPrestamo, fecha);
            monto = resultado.getMontoTotalLiquidacion();
        }
        LocalDateTime instante = enHora(fecha, hora, 0);
        long idSesion = obtenerOAbrirSesionDelDia(fecha, new BigDecimal("300000"));
        Reloj.fijarParaCargador(instante);
        Pago pago = servicioCobranza.confirmarCancelacionTotal(idPrestamo, UUID.randomUUID().toString(), monto, fecha,
                idCajera);
        backdatearColumna("pagos", "fecha_hora_pago", "id_pago", pago.getIdPago(), instante);
        backdatearUltimoMovimiento(idSesion, instante);
        if (pago.getIdLiquidacionAnticipada() != null) {
            backdatearColumna("liquidaciones_anticipadas", "fecha_calculo", "id_liquidacion",
                    pago.getIdLiquidacionAnticipada(), instante);
            backdatearColumna("liquidaciones_anticipadas", "fecha_aplicacion", "id_liquidacion",
                    pago.getIdLiquidacionAnticipada(), instante);
        }
        backdatearFinalizacionSiCorresponde(idPrestamo, instante);
    }

    /** Punto 20 (pedido de Joel): número de recibo del primer cobro, para nombrarlo en el motivo
     * de la reversión del cobro duplicado ("se registró dos veces el pago de la cuota 1, REC-..."). */
    private String leerNumeroRecibo(long idPago) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT numero_recibo FROM pagos WHERE id_pago = ?")) {
            sentencia.setLong(1, idPago);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getString(1);
            }
        }
    }

    private void autorizarReversion(long idPrestamo, long idPago, LocalDate fecha, int horaSolicitud,
            int horaAutorizacion, String motivo) throws SQLException {
        LocalDateTime instanteSolicitud = enHora(fecha, horaSolicitud, 0);
        Reloj.fijarParaCargador(instanteSolicitud);
        py.com.capitalcredito.sgcpc.modelos.ReversionPago reversion = servicioReversiones.solicitarReversion(idPago,
                motivo, idCajera, UUID.randomUUID().toString(), true);
        long idReversion = reversion.getIdReversionPago();
        backdatearColumna("reversiones_pago", "fecha_hora_solicitud", "id_reversion_pago", idReversion,
                instanteSolicitud);

        LocalDateTime instanteAutorizacion = enHora(fecha, horaAutorizacion, 0);
        // Fondeo generoso y fijo: alcanza para cualquier monto de los préstamos de demostración,
        // sin depender de recalcular "lo exigible" sobre un préstamo que ya tiene el pago que se
        // está revirtiendo.
        long idSesion = obtenerOAbrirSesionDelDia(fecha, new BigDecimal("3000000"));
        Reloj.fijarParaCargador(instanteAutorizacion);
        servicioReversiones.autorizarReversion(idReversion, idGerente, Boolean.TRUE, fecha);
        backdatearColumna("reversiones_pago", "fecha_hora_reversion", "id_reversion_pago", idReversion,
                instanteAutorizacion);
        backdatearUltimoMovimiento(idSesion, instanteAutorizacion);
    }

    private void cajaCerradaConArqueo(LocalDate fecha) throws SQLException {
        cerrarSesionActualSiHayUna();
        LocalDateTime instanteApertura = enHora(fecha, 8, 0);
        long idSesion = abrirCajaEn(new BigDecimal("2000000"), instanteApertura);
        cerrarCajaEn(idSesion, enHora(fecha, 17, 30));
    }

    /** Doc. 00 §10.1: prestamos.fecha_finalizacion_financiera usa now() de SQL (PrestamoDAO.
     * marcarFinalizado), independiente del Reloj -- se corrige solo cuando el cobro/cancelación
     * efectivamente dejó el préstamo FINALIZADO (E6 vía cancelarTotal, E7 vía su última cuota). */
    private void backdatearFinalizacionSiCorresponde(long idPrestamo, LocalDateTime instante) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT estado_prestamo FROM prestamos WHERE id_prestamo = ?")) {
            sentencia.setLong(1, idPrestamo);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                if ("FINALIZADO".equals(resultado.getString(1))) {
                    backdatearColumna("prestamos", "fecha_finalizacion_financiera", "id_prestamo", idPrestamo,
                            instante);
                }
            }
        }
    }

    /** Punto 4 (pedido de Joel): la gestión PAGO_PARCIAL que D-95 crea sola al confirmar un cobro
     * parcial con promesa también usa now() de SQL en fecha_hora -- se resuelve por id_pago, el
     * mismo camino que ya usa el recibo (GestionCobranzaDAO.obtenerPorPago). */
    private void backdatearGestionDelPago(long idPago, LocalDateTime instante) throws SQLException {
        long idGestion;
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT id_gestion_cobranza FROM gestiones_cobranza WHERE id_pago = ?")) {
            sentencia.setLong(1, idPago);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                idGestion = resultado.getLong(1);
            }
        }
        backdatearColumna("gestiones_cobranza", "fecha_hora", "id_gestion_cobranza", idGestion, instante);
    }

    /** Punto 8 (pedido de Joel): historial de gestiones de cobranza manuales (llamada, mensaje,
     * visita) sobre los préstamos en mora -- antes no existía ninguna. gestiones_cobranza.fecha_hora
     * también usa now() de SQL, independiente del Reloj. */
    private void registrarGestionBackdateada(long idPrestamo, long idPersonaContactada, TipoGestionCobranza tipo,
            String resultado, String excusa, LocalDate fechaPromesa, BigDecimal montoPrometido, long idUsuario,
            LocalDate fecha, int hora) throws SQLException {
        LocalDateTime instante = enHora(fecha, hora, 0);
        Reloj.fijarParaCargador(instante);
        servicioMora.registrarGestion(idPrestamo, idPersonaContactada, tipo, resultado, excusa, fechaPromesa,
                montoPrometido, idUsuario);
        long idGestion;
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT id_gestion_cobranza FROM gestiones_cobranza WHERE id_prestamo = ? "
                                + "ORDER BY id_gestion_cobranza DESC LIMIT 1")) {
            sentencia.setLong(1, idPrestamo);
            try (java.sql.ResultSet resultado2 = sentencia.executeQuery()) {
                resultado2.next();
                idGestion = resultado2.getLong(1);
            }
        }
        backdatearColumna("gestiones_cobranza", "fecha_hora", "id_gestion_cobranza", idGestion, instante);
    }

    private long obtenerIdClienteDePrestamo(long idPrestamo) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion();
                java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT sc.id_cliente FROM prestamos p JOIN solicitudes_credito sc "
                                + "ON sc.id_solicitud = p.id_solicitud WHERE p.id_prestamo = ?")) {
            sentencia.setLong(1, idPrestamo);
            try (java.sql.ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    /** Punto 10 (pedido de Joel): cierre documental completo de E6 -- el pagaré cancelado se
     * devuelve a los pocos días de la cancelación, con su constancia DEV y fecha simulada, dejando
     * solo E7 con el cierre pendiente ("para mostrar los dos estados"). */
    private void completarCierreDocumentalE6(long idPrestamo, LocalDate fechaConstancia, LocalDate fechaFirmado,
            LocalDate fechaCierre) throws SQLException {
        Reloj.fijarParaCargador(enHora(fechaConstancia, 9, 0));
        servicioFormalizacion.generarConstanciaDevolucion(idPrestamo, idOficial);

        LocalDateTime instanteFirmado = enHora(fechaFirmado, 10, 0);
        Reloj.fijarParaCargador(instanteFirmado);
        long idTipoFirmado;
        try (Connection lectura = FabricaConexionBD.obtenerConexion()) {
            idTipoFirmado = servicioFormalizacion.obtenerIdTipoDevolucionFirmado(lectura);
        }
        long idDocFirmado = servicioDocumentos.cargarDocumentoPrestamo(idPrestamo, idTipoFirmado,
                "constancia_devolucion_firmada.pdf", "application/pdf", CONTENIDO_PDF_DEMO, idOficial);
        backdatearArchivoDeDocumento(idDocFirmado, instanteFirmado);

        LocalDateTime instanteCierre = enHora(fechaCierre, 11, 0);
        Reloj.fijarParaCargador(instanteCierre);
        servicioFormalizacion.completarCierreDocumental(idPrestamo, idOficial);
        backdatearColumna("prestamos", "fecha_cierre_documental", "id_prestamo", idPrestamo, instanteCierre);
    }

    // =====================================================================
    // Cierre: mora, invariantes, reporte
    // =====================================================================

    private void recalcularMoraDeTodos() throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                for (String[] fila : reporte) {
                    long idPrestamo = Long.parseLong(fila[3]);
                    servicioCobranza.recalcularEstadoPrestamo(conexion, idPrestamo, H);
                }
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private void verificarInvariantes(long idPrestamoE1) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, idPrestamoE1);
            for (py.com.capitalcredito.sgcpc.modelos.Cuota cuota : servicioFormalizacion.listarCuotasVigentes(conexion,
                    prestamo.getIdPrestamo())) {
                long dias = java.time.temporal.ChronoUnit.DAYS.between(H, cuota.getFechaVencimiento());
                if (Math.abs(dias) <= 3) {
                    throw new IllegalStateException("VERIFICACIÓN FALLIDA: la cuota " + cuota.getNumeroCuota()
                            + " de E1 (María González) vence el " + cuota.getFechaVencimiento()
                            + ", a " + dias + " días de H (" + H + ") -- debía quedar fuera de la ventana de 3 días.");
                }
            }
        }
        System.out.println("Verificación OK: ninguna cuota de E1 vence dentro de los 3 días de H.");
    }

    private void registrarEnReporte(String escenario, String cliente, String cedula, long id, boolean esPrestamo) {
        reporte.add(new String[]{escenario, cliente, cedula, String.valueOf(id), String.valueOf(esPrestamo)});
    }

    /** Busca por cédula en vez de asumir "la última fila" -- E9 deja de ser la última apenas se
     * registran E10/E8 después de ella (encontrado en la primera corrida real: Patricia y Miguel
     * terminaron compartiendo el mismo número de operación en el reporte impreso). */
    private void actualizarFilaReportePorCedula(String cedula, String escenario, long idPrestamo) {
        for (String[] fila : reporte) {
            if (fila[2].equals(cedula)) {
                fila[0] = escenario;
                fila[3] = String.valueOf(idPrestamo);
                fila[4] = String.valueOf(true);
                return;
            }
        }
        throw new IllegalStateException("No se encontró la fila de reporte para la cédula " + cedula + ".");
    }

    private void imprimirReporte() throws SQLException {
        System.out.println();
        System.out.println("== Escenarios cargados (revisar en el navegador tras redesplegar) ==");
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            for (String[] fila : reporte) {
                long id = Long.parseLong(fila[3]);
                boolean esPrestamo = Boolean.parseBoolean(fila[4]);
                String identificador;
                if (esPrestamo) {
                    PrestamoCredito prestamo = servicioFormalizacion.obtenerPorId(conexion, id);
                    identificador = prestamo.getIdentificadorOperacion() + " (" + prestamo.getEstadoPrestamo() + ")";
                } else {
                    identificador = "solicitud id " + id + " (sin formalizar)";
                }
                System.out.println(" - " + fila[0] + " | " + fila[1] + " (C.I. " + fila[2] + ") -> " + identificador);
            }
        }
        System.out.println(" - E11 (nuevo interesado): no se carga nada -- Joel lo hace en vivo con el cotizador.");
    }

    private LocalDateTime enHora(LocalDate fecha, int hora, int minuto) {
        return LocalDateTime.of(fecha, LocalTime.of(hora, minuto));
    }
}
