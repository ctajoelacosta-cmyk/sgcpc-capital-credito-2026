package py.com.capitalcredito.sgcpc.servicios;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import py.com.capitalcredito.sgcpc.dao.ArchivoBinarioDAO;
import py.com.capitalcredito.sgcpc.dao.DesembolsoDAO;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.DocumentoExpedienteDAO;
import py.com.capitalcredito.sgcpc.dao.FeriadoDAO;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dao.LiquidacionAnticipadaDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dao.PlanPagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.dto.ClausulaReporte;
import py.com.capitalcredito.sgcpc.dto.CuotaReporte;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.dto.ResultadoCapacidadPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.dto.ResultadoTasasMora;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorFechasALetras;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.Feriado;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.LiquidacionAnticipada;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PlanPago;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;
import py.com.capitalcredito.sgcpc.modelos.Desembolso;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P06, formalización (Doc. 10 §10.9: ControladorFormalizacionServlet / ControladorDocumentosServlet
 * / ServicioFormalizacion / ServicioDocumentos / ServicioMotorFinanciero). E5.
 */
public class ServicioFormalizacion {

    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final PlanPagoDAO planPagoDAO = new PlanPagoDAO();
    private final CuotaDAO cuotaDAO = new CuotaDAO();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final FeriadoDAO feriadoDAO = new FeriadoDAO();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    private final DocumentoExpedienteDAO documentoExpedienteDAO = new DocumentoExpedienteDAO();
    private final LiquidacionAnticipadaDAO liquidacionAnticipadaDAO = new LiquidacionAnticipadaDAO();
    private final ArchivoBinarioDAO archivoBinarioDAO = new ArchivoBinarioDAO();
    private final DesembolsoDAO desembolsoDAO = new DesembolsoDAO();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();

    // ------------------------------------------------------------------
    // Validaciones puras
    // ------------------------------------------------------------------

    /** Fecha por defecto del primer vencimiento al formalizar (ajuste de Joel): un mes después, mismo día. Pura. */
    public LocalDate proponerPrimerVencimiento(LocalDate fechaFormalizacion) {
        return fechaFormalizacion.plusMonths(1);
    }

    /**
     * La fecha propuesta/editada por el oficial debe caer dentro de un rango de días desde hoy
     * (parámetros formalizacion.dias_min/max_primer_vencimiento). No toca RN-07 (monto/plazo no
     * cambian, solo el calendario). Pura.
     */
    public void validarPrimerVencimientoFormalizacion(LocalDate fechaFormalizacion, LocalDate propuesta,
            int diasMinimo, int diasMaximo) {
        if (propuesta == null) {
            throw new NegocioException("Ingrese la fecha del primer vencimiento.");
        }
        long dias = ChronoUnit.DAYS.between(fechaFormalizacion, propuesta);
        if (dias < diasMinimo || dias > diasMaximo) {
            // Corrección de Joel: fechas concretas en el mensaje, no la cantidad de días. RF05
            // (pedido de Joel, 10/10/2026): título (fechas concretas) y detalle (qué hacer) en el
            // mismo mensaje -- cotizador, solicitud y formalización comparten este único método.
            LocalDate minima = fechaFormalizacion.plusDays(diasMinimo);
            LocalDate maxima = fechaFormalizacion.plusDays(diasMaximo);
            throw new NegocioException("La fecha del primer vencimiento debe estar entre "
                    + FormateadorFechas.comoDdMmAaaa(minima) + " y " + FormateadorFechas.comoDdMmAaaa(maxima)
                    + ". Elija una fecha entre " + diasMinimo + " y " + diasMaximo + " días desde hoy.");
        }
    }

    /**
     * RF11 (pedido de Joel, 11/10/2026): además de la fecha ELEGIDA (validarPrimerVencimientoFormalizacion,
     * arriba), vuelve a validar la fecha EFECTIVA (ya corrida al siguiente día hábil por
     * ServicioMotorFinanciero.fechaHabil) -- un corrimiento cerca del límite puede dejarla fuera
     * del rango sin que la validación de la fecha elegida lo note. Pura.
     */
    public void validarPrimerVencimientoEfectivoFormalizacion(LocalDate fechaFormalizacion, LocalDate efectiva,
            int diasMaximo, List<LocalDate> feriados) {
        LocalDate maxima = fechaFormalizacion.plusDays(diasMaximo);
        if (efectiva.isAfter(maxima)) {
            LocalDate sugerida = servicioMotorFinanciero.fechaHabilAnterior(maxima, feriados);
            throw new py.com.capitalcredito.sgcpc.excepciones.FechaFueraDePlazoException(
                    "La fecha elegida cae en un día no hábil y, al pasarla al siguiente día "
                    + "hábil, queda fuera del plazo permitido (hasta el " + FormateadorFechas.comoDdMmAaaa(maxima)
                    + "). Elija una fecha hábil anterior, por ejemplo el " + FormateadorFechas.comoDdMmAaaa(sugerida)
                    + ".");
        }
    }

    /** RF11: por qué una fecha puntual no es día hábil -- sábado, domingo, o el nombre del
     * feriado (tabla Parámetros → Feriados). Null si la fecha ya es hábil. Pura. Paquete (no
     * private) para que el test pueda probarla directamente, mismo criterio que
     * construirMensajeYaDecidida (RF09). */
    String describirMotivoNoHabil(LocalDate fecha, List<Feriado> feriadosActivos) {
        if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return "sábado";
        }
        if (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return "domingo";
        }
        for (Feriado feriado : feriadosActivos) {
            if (feriado.getFecha().equals(fecha)) {
                return "feriado (" + feriado.getDescripcion() + ")";
            }
        }
        return null;
    }

    /** RF11 (pedido de Joel, 11/10/2026): explica, para el aviso en pantalla y la auditoría, por
     * qué la fecha elegida terminó corrida a la fecha final -- uno o más motivos si el
     * corrimiento cruzó varios días no hábiles seguidos (ej. un feriado un viernes, seguido de
     * sábado y domingo). Null si no hubo corrimiento. Pura. */
    String describirCorrimiento(LocalDate elegida, LocalDate finalFecha, List<Feriado> feriadosActivos) {
        if (elegida.equals(finalFecha)) {
            return null;
        }
        List<String> motivos = new ArrayList<>();
        for (LocalDate actual = elegida; actual.isBefore(finalFecha); actual = actual.plusDays(1)) {
            String motivo = describirMotivoNoHabil(actual, feriadosActivos);
            if (motivo != null) {
                motivos.add(motivo);
            }
        }
        return String.join(", ", motivos);
    }

    /**
     * RF11 (pedido de Joel, 11/10/2026): al formalizar o regenerar con una fecha nueva, el 40 %
     * de afectación se vuelve a controlar con el ingreso y las deudas ACTUALES del cliente
     * (D-127), sin excepción -- mismo criterio que ya usa RF06 al revalidar el envío a aprobación
     * (ServicioSolicitudes). La cuota en sí no cambia según qué fecha de primer vencimiento se
     * elija (el motor no usa día-calendario real entre cuotas, solo el período secuencial -- ver
     * ServicioMotorFinanciero.calcularCft/generarPlanPagos): lo que de verdad puede hacer que
     * esta revalidación bloquee algo que la aprobación no bloqueó es que la situación financiera
     * del cliente haya empeorado desde entonces, no la fecha elegida.
     */
    private void revalidarCapacidadPagoFormalizacion(Connection conexion, SolicitudCredito solicitud,
            BigDecimal cuotaMasAlta, boolean puedeResolver) throws SQLException {
        // RF05/RF06/RF11 (pedido de Joel, 18/10/2026): hueco preexistente destapado al quitar el
        // codeudor -- mismo bloqueo directo que ahora hay en crear y enviar a aprobación
        // (ServicioSolicitudes.validarPoliticaCredito), revalidado acá con los datos ACTUALES del
        // cliente por la misma razón que el 40 % de abajo (pudo editarse entre medio).
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                servicioClientes.obtenerLaboral(conexion, solicitud.getIdCliente());
        String mensajeComprobante = construirMensajeSinComprobanteFormalizacion(
                laboralCliente.isPresentaComprobanteIngreso(), puedeResolver);
        if (mensajeComprobante != null) {
            throw new NegocioException(mensajeComprobante);
        }
        BigDecimal limiteAfectacion = servicioParametros.obtenerValorDecimal(conexion, "credito.limite_afectacion");
        ResultadoCapacidadPago capacidad = calcularCapacidadActualFormalizacion(conexion, solicitud.getIdCliente(),
                cuotaMasAlta);
        if (capacidad.getPorcentajeAfectacion().compareTo(limiteAfectacion) > 0) {
            throw new NegocioException(construirMensajeLimiteAfectacionFormalizacion(cuotaMasAlta,
                    capacidad.getPorcentajeAfectacion(), limiteAfectacion, puedeResolver));
        }
    }

    /**
     * B84 (decisión de Joel, 28/10/2026): RN-01 "un cliente, un préstamo a la vez" revalidada al
     * FORMALIZAR -- tercer momento, mismo criterio que ya se eligió para el comprobante de ingreso
     * (RF05/RF06/RF11, 18/10/2026: el mismo control en los tres momentos). Antes solo se aplicaba
     * al crear y al enviar a aprobación (`ServicioSolicitudes`), y al aprobar desde este mismo
     * cambio (`ServicioAprobacionCredito.validarSinPrestamoAbierto`).
     *
     * <p><b>Por qué NO se bloquea a sí misma</b> la solicitud que se está formalizando (la
     * pregunta de Joel): en vez del booleano `existeClienteConPrestamoAbierto` usa
     * `buscarPorClienteConPrestamoAbierto` -- la misma consulta, pero devolviendo la solicitud
     * dueña del préstamo abierto -- y descarta el caso de que esa dueña sea ESTA solicitud. Es la
     * tercera de tres capas, y las dos primeras ya lo garantizan solas: el control corre antes del
     * `prestamoDAO.insertar`, y dos líneas más arriba `generarDocumentos` ya probó con
     * `obtenerPorSolicitud` que esta solicitud todavía no tiene préstamo. La comparación es
     * redundante hoy a propósito: deja la garantía local y verificable por
     * `PruebaRN01AprobarFormalizar`, en vez de depender del orden de las líneas, que un cambio
     * futuro puede mover.</p>
     *
     * <p>Sin consulta nueva. Cuentan como préstamo abierto `PENDIENTE_DESEMBOLSO`, `ACTIVO` y
     * `EN_MORA` (RN-01, D-61). No se aplica a `regenerarDocumentos`: ahí el préstamo abierto es el
     * propio, y el control bloquearía el regenerar de RF11.</p>
     *
     * <p>Visible en el paquete, no privado, por el mismo motivo que
     * `construirMensajeSinComprobanteFormalizacion`: que la prueba lo ejercite con su propia
     * conexión en transacción. La regla es política de crédito del dueño, no un RF del
     * anteproyecto (nivel 2 de la jerarquía, reglas internas del proyecto, 18/10/2026).</p>
     */
    void validarSinOtroPrestamoAbierto(Connection conexion, long idCliente, long idSolicitud)
            throws SQLException {
        SolicitudCredito conPrestamoAbierto = solicitudDAO.buscarPorClienteConPrestamoAbierto(conexion, idCliente);
        if (conPrestamoAbierto != null
                && !Long.valueOf(idSolicitud).equals(conPrestamoAbierto.getIdSolicitud())) {
            throw new NegocioException(MENSAJE_PRESTAMO_ABIERTO_FORMALIZAR);
        }
    }

    /** B84: mismo texto base que los otros tres momentos de RN-01, sin la sigla interna (punto 11
     * del Bloque 7/E10), en usted y con el "qué sigue" que pidió Joel el 28/10/2026. Constante
     * visible en el paquete para poder verificarla con JUnit, sin base. */
    static final String MENSAJE_PRESTAMO_ABIERTO_FORMALIZAR =
            "El cliente ya tiene un préstamo no cerrado. No se puede formalizar. Para pedir otro "
            + "crédito, primero debe terminar de pagarlo o cancelarlo.";

    /** Tanda de usabilidad (20-21/10/2026, pedido de Joel, punto 7 paso F): texto de "qué sigue"
     * compartido por los mensajes 1, 2 y 5 (afectación, sin comprobante, tope de tasa cambiado) --
     * los 3 se resuelven de la misma forma (cancelar la solicitud desde Aprobación para evaluar
     * una nueva), así que la versión (a) es idéntica en los 3; solo cambia el detalle de la (b)
     * (detalleSiPuede), que cada llamador redacta según su propio caso. puedeResolver se calcula
     * en el controlador (nivelAccesoSesion(APROBAR_RECHAZAR_SOLICITUD) != NINGUNO), nunca por
     * nombre de rol. */
    private String construirQueSigueCancelarSolicitud(boolean puedeResolver, String detalleSiPuede) {
        return puedeResolver ? detalleSiPuede
                : "Consulte al gerente: la solicitud debe cancelarse para evaluar una nueva.";
    }

    /** RF11 (pedido de Joel, 11/10/2026): mensaje puro, separado de la revalidación (que necesita
     * Connection) para poder probarlo con JUnit sin base de datos.
     * Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso F1/F2): título corto ("No se
     * puede formalizar.") + detalle, mismo mecanismo de 2 partes que ya usa
     * construirMensajeSinComprobanteFormalizacion de abajo (MOTIVO_SIN_COMPROBANTE_SOLICITUD ya
     * viene así). Porcentajes con comoPorcentajeUnDecimal, no comoPorcentaje -- el mensaje mostraba
     * "154,622 %" con 3 decimales mientras la pantalla (Afectación actual) ya redondea a 1 ("154,6
     * %"); el límite también pasa a 1 decimal, mismo criterio. F2: se sacó la frase repetida ("No
     * se puede formalizar hasta que la situación del cliente lo permita.", el título ya lo dice) y
     * el "qué sigue" ahora depende de puedeResolver (ver construirQueSigueCancelarSolicitud). */
    String construirMensajeLimiteAfectacionFormalizacion(BigDecimal cuotaMasAlta, BigDecimal porcentajeAfectacion,
            BigDecimal limiteAfectacion, boolean puedeResolver) {
        return "No se puede formalizar. La cuota más alta (" + FormateadorNumeros.comoGuaranies(cuotaMasAlta)
                + ") representa el " + FormateadorNumeros.comoPorcentajeUnDecimal(porcentajeAfectacion)
                + " del ingreso del cliente (sumando sus otras deudas actuales), por encima del límite "
                + "permitido (" + FormateadorNumeros.comoPorcentajeUnDecimal(limiteAfectacion) + "). "
                + construirQueSigueCancelarSolicitud(puedeResolver, "Puede cancelar la solicitud desde Aprobación "
                        + "para evaluar una nueva con los datos actuales del cliente.");
    }

    /** RF05/RF06/RF11 (pedido de Joel, 18/10/2026): mismo texto base que ServicioSolicitudes usa
     * al crear y al enviar (ServicioClientes.motivoSinComprobanteSolicitudSiAplica). Pura, para
     * poder probarla con JUnit sin base de datos. null si no aplica (tiene comprobante).
     * F2 (21/10/2026): el "qué sigue" ahora depende de puedeResolver, mismo mecanismo que
     * construirMensajeLimiteAfectacionFormalizacion -- esta misma pantalla ("Comprobante de
     * ingreso" en nueva.jsp/ficha.jsp) muestra el mismo texto que el diálogo de error. */
    String construirMensajeSinComprobanteFormalizacion(boolean presentaComprobanteIngreso, boolean puedeResolver) {
        String motivo = servicioClientes.motivoSinComprobanteSolicitudSiAplica(presentaComprobanteIngreso);
        return motivo == null ? null
                : motivo + " " + construirQueSigueCancelarSolicitud(puedeResolver, "Puede cancelar la solicitud "
                        + "desde Aprobación para evaluar una nueva cuando el cliente presente el comprobante.");
    }

    /** RF11 (pedido de Joel, 11/10/2026): capacidad de pago con el ingreso y las deudas ACTUALES
     * del cliente (D-127) -- compartido por la revalidación (que bloquea) y el resumen de
     * pantalla (que solo informa, sección siguiente). */
    private ResultadoCapacidadPago calcularCapacidadActualFormalizacion(Connection conexion, long idCliente,
            BigDecimal cuotaMasAlta) throws SQLException {
        // Normalización de personas, grupo Laborales (16/10/2026): ingreso/cuota de otras deudas
        // (datos de capacidad de pago) ya no están en Persona -- único método que los lee,
        // ServicioClientes.obtenerLaboral (4.º de los 4 lugares: este, más crear/simular/
        // enviarAAprobacion de ServicioSolicitudes).
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                servicioClientes.obtenerLaboral(conexion, idCliente);
        BigDecimal cuotaOtrasDeudas = servicioMotorFinanciero.sumarCuotasOtrasDeudas(
                laboralCliente.getCuotaOtrasDeudasMensual());
        return servicioMotorFinanciero.calcularCapacidadPago(cuotaMasAlta, laboralCliente.getIngresoNetoComprobado(),
                cuotaOtrasDeudas);
    }

    /** RF11 (pedido de Joel, 11/10/2026): cuota más alta, total y CFT -- no dependen de qué fecha
     * de primer vencimiento se elija (el motor descuenta por período secuencial, no por
     * día-calendario real entre cuotas, ver ServicioMotorFinanciero.calcularCft) -- junto con la
     * afectación calculada con los datos ACTUALES del cliente, para mostrar en pantalla antes de
     * confirmar (nueva.jsp/ficha.jsp). */
    public static final class ResumenFormalizacion {
        private final BigDecimal cuotaMasAlta;
        private final BigDecimal montoTotalConIva;
        private final BigDecimal cft;
        private final BigDecimal porcentajeAfectacion;
        private final BigDecimal limiteAfectacion;
        private final String mensajeSinComprobante;

        public ResumenFormalizacion(BigDecimal cuotaMasAlta, BigDecimal montoTotalConIva, BigDecimal cft,
                BigDecimal porcentajeAfectacion, BigDecimal limiteAfectacion, String mensajeSinComprobante) {
            this.cuotaMasAlta = cuotaMasAlta;
            this.montoTotalConIva = montoTotalConIva;
            this.cft = cft;
            this.porcentajeAfectacion = porcentajeAfectacion;
            this.limiteAfectacion = limiteAfectacion;
            this.mensajeSinComprobante = mensajeSinComprobante;
        }

        public BigDecimal getCuotaMasAlta() {
            return cuotaMasAlta;
        }

        public BigDecimal getMontoTotalConIva() {
            return montoTotalConIva;
        }

        public BigDecimal getCft() {
            return cft;
        }

        public BigDecimal getPorcentajeAfectacion() {
            return porcentajeAfectacion;
        }

        public BigDecimal getLimiteAfectacion() {
            return limiteAfectacion;
        }

        public boolean isSuperaLimite() {
            return porcentajeAfectacion.compareTo(limiteAfectacion) > 0;
        }

        /** Tanda de usabilidad (19/10/2026, pedido de Joel, RF11_formalizacion.md §10): null si
         * el cliente presenta comprobante de ingreso; si no, el mismo mensaje que usa la
         * revalidación real al generar/regenerar (construirMensajeSinComprobanteFormalizacion) --
         * para mostrarlo en pantalla desde el principio, mismo criterio que isSuperaLimite(). */
        public String getMensajeSinComprobante() {
            return mensajeSinComprobante;
        }

        /**
         * B182 (14/11/2026): los motivos de bloqueo que esta pantalla conoce <b>de antemano</b>,
         * cada uno en una frase corta, para el renglón que va junto a "Generar documentos".
         *
         * <p>Son los únicos 2 que el resumen puede anticipar (RF11_formalizacion.md §10) y los
         * <b>mismos</b> que revalidarCapacidadPagoFormalizacion rechaza al confirmar: el límite de
         * afectación y el comprobante de ingreso. Las otras causas de rechazo de generarDocumentos
         * (tope de tasa cambiado, fecha fuera de rango, solicitud ya formalizada, otro préstamo
         * abierto) no tienen ningún dato equivalente acá y por eso no se nombran.</p>
         *
         * <p>Hasta B182 estas dos condiciones se evaluaban <b>tres</b> veces en nueva.jsp (la fila
         * de la tabla, la fila del comprobante y el {@code if} que decide si se pregunta antes de
         * enviar). Acá viven una sola vez: un dato, un dueño. Pura.</p>
         */
        public List<String> getMotivosDeBloqueo() {
            List<String> motivos = new ArrayList<>();
            if (isSuperaLimite()) {
                motivos.add("la afectación supera el límite");
            }
            if (mensajeSinComprobante != null) {
                motivos.add("falta el comprobante de ingreso");
            }
            return motivos;
        }

        /** B182: hay al menos un motivo de bloqueo de los que esta pantalla conoce de antemano --
         * o sea, el servidor va a rechazar el envío. Pura. */
        public boolean hayBloqueoConocido() {
            return !getMotivosDeBloqueo().isEmpty();
        }

        /**
         * B182: los motivos en una sola frase, unidos con " y " -- cadena vacía si no hay ninguno.
         *
         * <p>El armado vive acá y no en la JSP por la lección de B103/B117: un texto que concatena
         * algo fijo con algo que varía tiene que seguir siendo correcto para <b>todas</b> las
         * cantidades posibles (acá 0, 1 y 2), y eso se prueba con JUnit. Pura.</p>
         */
        public String nombrarMotivosDeBloqueo() {
            List<String> motivos = getMotivosDeBloqueo();
            StringBuilder texto = new StringBuilder();
            for (int i = 0; i < motivos.size(); i++) {
                if (i > 0) {
                    texto.append(" y ");
                }
                texto.append(motivos.get(i));
            }
            return texto.toString();
        }
    }

    /** RF11: resumen para la pantalla de "nueva"/"regenerar" -- ver ResumenFormalizacion. La
     * fecha de referencia para cotizar es irrelevante para estos valores (no cambian con la
     * fecha elegida); se usa "hoy + 1 mes" solo para tener una fecha concreta que pasarle al
     * motor. F2 (21/10/2026, pedido de Joel): puedeResolver decide la versión (a)/(b) del aviso
     * "Comprobante de ingreso" -- para que diga lo mismo que el diálogo de error si se llega a
     * confirmar el envío (nunca se calcula en esta clase, el controlador se lo pasa ya resuelto). */
    public ResumenFormalizacion calcularResumenParaMostrar(Connection conexion, BigDecimal monto, int plazoMeses,
            BigDecimal tea, long idCliente, boolean puedeResolver) throws SQLException {
        List<LocalDate> feriados = feriadoDAO.listarFechasActivas(conexion);
        ResultadoCotizacion resultado = servicioMotorFinanciero.cotizar(monto, plazoMeses,
                Reloj.hoy().plusMonths(1), tea, feriados);
        BigDecimal cuotaMasAlta = servicioMotorFinanciero.cuotaMasAlta(resultado);
        BigDecimal limiteAfectacion = servicioParametros.obtenerValorDecimal(conexion, "credito.limite_afectacion");
        ResultadoCapacidadPago capacidad = calcularCapacidadActualFormalizacion(conexion, idCliente, cuotaMasAlta);
        // Tanda de usabilidad (19/10/2026, pedido de Joel, RF11_formalizacion.md §10): mismo
        // chequeo que revalidarCapacidadPagoFormalizacion, pero sin lanzar -- solo para mostrar.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente = servicioClientes.obtenerLaboral(conexion, idCliente);
        String mensajeSinComprobante = construirMensajeSinComprobanteFormalizacion(
                laboralCliente.isPresentaComprobanteIngreso(), puedeResolver);
        return new ResumenFormalizacion(cuotaMasAlta, resultado.getMontoTotalConIva(), resultado.getCft(),
                capacidad.getPorcentajeAfectacion(), limiteAfectacion, mensajeSinComprobante);
    }

    /** RF11 (pedido de Joel, 11/10/2026): detalle de auditoría con la fecha estimada de la
     * solicitud, la fecha elegida, la fecha final (con el motivo si se corrió), la cuota más
     * alta y el CFT resultantes -- igual al formalizar que al regenerar con una fecha nueva. */
    String construirDetalleFechaFormalizacion(SolicitudCredito solicitud, LocalDate elegida,
            LocalDate finalFecha, List<Feriado> feriadosActivos, BigDecimal cuotaMasAlta, BigDecimal cft) {
        String motivo = describirCorrimiento(elegida, finalFecha, feriadosActivos);
        StringBuilder detalle = new StringBuilder("Fecha estimada en la solicitud: ")
                .append(FormateadorFechas.comoDdMmAaaa(solicitud.getPrimeraFechaVencimientoAprobada()))
                .append(". Fecha elegida: ").append(FormateadorFechas.comoDdMmAaaa(elegida))
                .append(". Fecha final: ").append(FormateadorFechas.comoDdMmAaaa(finalFecha));
        if (motivo != null) {
            detalle.append(" (se corrió por ").append(motivo).append(")");
        }
        detalle.append(". Cuota más alta: ").append(FormateadorNumeros.comoGuaranies(cuotaMasAlta))
                .append(". CFT: ").append(FormateadorNumeros.comoPorcentaje(cft)).append(".");
        return detalle.toString();
    }

    /** RF14 (pedido de Joel, 11/10/2026): mismo mínimo de caracteres reales que el motivo de
     * diferencia de arqueo (RF19, ServicioCaja.MOTIVO_DIFERENCIA_LARGO_MINIMO) -- no alcanza con
     * que el motivo exista, tiene que tener contenido. */
    static final int MOTIVO_ANULACION_LARGO_MINIMO = 10;

    /** RF14, no RF06: motivo en texto libre, sin lista fija (a diferencia de la cancelación de
     * solicitudes desde el 10/10/2026, que sí pasó a tener lista + detalle). Vacío y "menos de
     * 10 caracteres" son el mismo caso (mismo criterio que RF19
     * ServicioCaja.validarMotivoDiferencia): no es un error del servidor, es que falta
     * completar el motivo con contenido real -- advertencia, no error. Pura. */
    public void validarMotivoAnulacion(String motivo) {
        if (motivo == null || motivo.length() < MOTIVO_ANULACION_LARGO_MINIMO) {
            throw new py.com.capitalcredito.sgcpc.excepciones.MotivoAnulacionInsuficienteException(
                    construirMensajeMotivoAnulacionInsuficiente());
        }
    }

    /** Pura, separada de la validación para poder probarla con JUnit (mismo criterio que
     * ServicioCaja.construirMensajeMotivoInsuficiente, RF19). */
    String construirMensajeMotivoAnulacionInsuficiente() {
        return "Explique el motivo de la anulación con al menos " + MOTIVO_ANULACION_LARGO_MINIMO
                + " caracteres.";
    }

    /**
     * RF14 (pedido de Joel, 11/10/2026): cuando `marcarAnuladoAntesDesembolso` devuelve `false`
     * (alguien ya confirmó el desembolso primero, carrera de dos pestañas) -- relee el préstamo
     * ya actualizado (la misma transacción, bajo READ COMMITTED, ya ve el valor comiteado por la
     * otra, mismo criterio que ServicioAprobacionCredito.construirErrorYaDecidida/
     * ServicioCaja.construirErrorCajaYaCerrada) y busca el desembolso real para el nombre y la
     * hora exactos.
     */
    private py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException
            construirErrorPrestamoYaDesembolsado(Connection conexion, long idPrestamo) throws SQLException {
        Desembolso desembolso = desembolsoDAO.obtenerConfirmadoPorPrestamo(conexion, idPrestamo);
        Usuario quienDesembolso = desembolso == null ? null
                : servicioUsuarios.buscarPorId(conexion, desembolso.getDesembolsadoPor());
        return new py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException(
                construirMensajePrestamoYaDesembolsado(desembolso, quienDesembolso));
    }

    /** Pura: "Este préstamo ya fue desembolsado por NOMBRE el FECHA HORA. Actualice la página." */
    String construirMensajePrestamoYaDesembolsado(Desembolso desembolso, Usuario quienDesembolso) {
        String nombre = quienDesembolso == null ? "otro usuario"
                : quienDesembolso.getNombres() + " " + quienDesembolso.getApellidos();
        String fecha = desembolso == null ? "" : FormateadorFechas.comoDdMmAaaaHhMm(desembolso.getFechaHoraDesembolso());
        return "Este préstamo ya fue desembolsado por " + nombre + " el " + fecha + ". Actualice la página.";
    }

    /** PA-31 (14/10/2026, pedido de Joel, autorizado): ninguno de los 3 métodos que regeneran PDF
     * (regenerarDocumentos/regenerarSoloPdfs/generarPdfsFaltantes) validaba que el préstamo
     * siguiera PENDIENTE_DESEMBOLSO -- un POST directo a cualquiera de las 3 rutas sobre un
     * préstamo ya ACTIVO/EN_MORA/FINALIZADO/ANULADO dejaba un PAGARE_GENERADO/PLAN_PAGOS_GENERADO/
     * CONTRATO_GENERADO "vigente" con datos ACTUALES, que pueden no coincidir con lo que el
     * cliente firmó. ACTIVO/EN_MORA/FINALIZADO implican siempre un desembolso confirmado (el
     * único camino hacia esos 3 estados es PrestamoDAO.marcarDesembolsado, desde
     * ServicioDesembolso.confirmarDesembolso) -- para esos, reutiliza
     * construirErrorPrestamoYaDesembolsado (mensaje con nombre/fecha reales, consulta la base).
     * Para ANULADO_ANTES_DESEMBOLSO (o cualquier otro estado sin desembolso) un mensaje genérico,
     * sin tocar la base -- nunca hubo un desembolso que buscar. Paquete-visible (no private) para
     * poder probar con JUnit puro, sin conexión, los dos casos que no la necesitan. */
    void validarPendienteDesembolso(Connection conexion, PrestamoCredito prestamo) throws SQLException {
        EstadoPrestamo estado = prestamo.getEstadoPrestamo();
        if (estado == EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
            return;
        }
        if (estado == EstadoPrestamo.ACTIVO || estado == EstadoPrestamo.EN_MORA || estado == EstadoPrestamo.FINALIZADO) {
            throw construirErrorPrestamoYaDesembolsado(conexion, prestamo.getIdPrestamo());
        }
        throw new py.com.capitalcredito.sgcpc.excepciones.PrestamoNoPendienteDesembolsoException(
                "Este préstamo ya no está pendiente de desembolso. No se pueden regenerar sus documentos.");
    }

    // ------------------------------------------------------------------
    // Orquestación
    // ------------------------------------------------------------------

    public PrestamoCredito obtenerPorId(Connection conexion, long idPrestamo) throws SQLException {
        return prestamoDAO.obtenerPorId(conexion, idPrestamo);
    }

    public PrestamoCredito obtenerPorSolicitud(Connection conexion, long idSolicitud) throws SQLException {
        return prestamoDAO.obtenerPorSolicitud(conexion, idSolicitud);
    }

    /** Bandeja P06: solicitudes aprobadas listas para formalizar. */
    public List<SolicitudCredito> listarParaFormalizar(Connection conexion) throws SQLException {
        return solicitudDAO.listarAprobadasSinFormalizar(conexion);
    }

    // ======================================================================
    // B183 (15/11/2026): orden de las 3 secciones de la bandeja de Formalización.
    //
    // Mismo mecanismo que Clientes (B157), Solicitudes (B167) y Aprobaciones (B175), con dos
    // diferencias propias de esta pantalla: son TRES listados en una sola página (cada uno con su
    // lista blanca y su orden por defecto, porque no tienen las mismas columnas) y el orden va
    // EN MEMORIA, no en el SQL -- el filtro de texto ya es en memoria desde el 20/10/2026, así que
    // ordenar o cortar en la consulta aplicaría el filtro después del corte y las páginas saldrían
    // con menos filas de las que dicen (el mismo razonamiento de B167/B175).
    //
    // Todo lo de acá es PURO: se prueba con JUnit sin base (ServicioFormalizacionOrdenTest).
    // ======================================================================

    /** B183: cuál de las 3 secciones de la bandeja. Un enum y no una cadena, para que una sección
     * mal escrita no compile en vez de caer en silencio en el orden de otra. */
    public enum SeccionBandeja {
        /** Solicitudes aprobadas listas para formalizar. */
        FORMALIZAR,
        /** Préstamos ya formalizados, esperando el checklist y el desembolso. */
        DESEMBOLSO,
        /** Préstamos finalizados con el pagaré pendiente de devolución. */
        PAGARE
    }

    /**
     * B183: las únicas claves por las que se puede ordenar cada sección. Son exactamente las
     * columnas que esa tabla muestra, más --solo en PAGARE-- la fecha de cancelación, que es el
     * orden de siempre de esa sección y no tiene columna propia (igual que "Esperando" en
     * Aprobaciones, pero al revés: acá el dato ordena y no se muestra).
     *
     * <p><b>La columna "Checklist" de DESEMBOLSO no entra a propósito</b>: no sale de la consulta
     * --la arma el controlador leyendo el checklist de cada préstamo-- y es la regla de B157, que
     * un encabezado que parece clickeable y no ordena es peor que uno que no lo parece.</p>
     */
    private static final java.util.Map<SeccionBandeja, java.util.Set<String>> ORDENES_POR_SECCION;
    /** B183: la clave de orden de siempre de cada sección -- la que hoy escribe el {@code ORDER BY}
     * del DAO, para que sin parámetros la pantalla salga exactamente como salía antes. */
    private static final java.util.Map<SeccionBandeja, String> ORDEN_DEFECTO_POR_SECCION;

    static {
        java.util.Map<SeccionBandeja, java.util.Set<String>> ordenes =
                new java.util.EnumMap<>(SeccionBandeja.class);
        ordenes.put(SeccionBandeja.FORMALIZAR, java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                        "codigo", "aprobada", "cliente", "monto", "plazo"))));
        ordenes.put(SeccionBandeja.DESEMBOLSO, java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                        "operacion", "cliente", "monto", "formalizado"))));
        ordenes.put(SeccionBandeja.PAGARE, java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                        "operacion", "cliente", "monto", "cancelada"))));
        ORDENES_POR_SECCION = java.util.Collections.unmodifiableMap(ordenes);

        java.util.Map<SeccionBandeja, String> defectos = new java.util.EnumMap<>(SeccionBandeja.class);
        defectos.put(SeccionBandeja.FORMALIZAR, "aprobada");
        defectos.put(SeccionBandeja.DESEMBOLSO, "formalizado");
        defectos.put(SeccionBandeja.PAGARE, "cancelada");
        ORDEN_DEFECTO_POR_SECCION = java.util.Collections.unmodifiableMap(defectos);
    }

    /** B183: si esa clave se puede usar en esa sección. Lo desconocido --y lo tecleado a mano en la
     * URL-- cae en el orden de siempre, nunca llega a una comparación. */
    public static boolean esOrdenPermitido(String clave, SeccionBandeja seccion) {
        return clave != null && ORDENES_POR_SECCION.get(seccion).contains(clave);
    }

    /** B183: la clave de orden de siempre de la sección. */
    public static String ordenPorDefecto(SeccionBandeja seccion) {
        return ORDEN_DEFECTO_POR_SECCION.get(seccion);
    }

    /** B183: el orden que se va a usar de verdad. */
    public static String ordenAplicado(String clavePedida, SeccionBandeja seccion) {
        return esOrdenPermitido(clavePedida, seccion) ? clavePedida : ordenPorDefecto(seccion);
    }

    /**
     * B183: el sentido que se va a usar, y <b>acá vive toda la regla</b> -- la lección de B167, que
     * tenerla partida entre el servicio y un ternario del controlador hizo que la primera carga
     * ordenara al revés.
     *
     * <p>Con sentido explícito, ese; en cualquier otro caso <b>ascendente</b>. No hace falta
     * distinguir "con columna pedida" de "sin columna pedida" como en B175, porque acá las dos
     * respuestas son la misma: las 3 secciones son colas de trabajo y su orden de siempre ya es
     * ascendente (primero la que espera hace más tiempo), y ascendente es además lo que espera el
     * primer clic en un encabezado. Por eso el método recibe <b>solo</b> el sentido: un parámetro
     * que no cambia la respuesta sería un parámetro que miente.</p>
     */
    public static String sentidoAplicado(String sentidoPedido) {
        return "desc".equalsIgnoreCase(sentidoPedido) ? "desc" : "asc";
    }

    /**
     * B183: ordena la sección 1 (solicitudes aprobadas para formalizar). Pura.
     *
     * @param clientes mapa por {@code idCliente}, el mismo que ya arma el controlador
     */
    public List<SolicitudCredito> ordenarParaFormalizar(List<SolicitudCredito> solicitudes,
            java.util.Map<Long, Persona> clientes, String clave, String sentido) {
        if (solicitudes == null) {
            return new ArrayList<>();
        }
        final String claveUsada = ordenAplicado(clave, SeccionBandeja.FORMALIZAR);
        final boolean descendente = "desc".equalsIgnoreCase(
                sentidoAplicado(sentido));
        final java.util.Map<Long, Persona> clientesUsados = clientes == null
                ? java.util.Collections.<Long, Persona>emptyMap() : clientes;
        List<SolicitudCredito> ordenadas = new ArrayList<>(solicitudes);
        java.util.Collections.sort(ordenadas, new java.util.Comparator<SolicitudCredito>() {
            @Override
            public int compare(SolicitudCredito una, SolicitudCredito otra) {
                int resultado = compararPorValor(valorDeOrdenSolicitud(una, claveUsada, clientesUsados),
                        valorDeOrdenSolicitud(otra, claveUsada, clientesUsados), descendente);
                if (resultado == 0 && !ordenPorDefecto(SeccionBandeja.FORMALIZAR).equals(claveUsada)) {
                    String porDefecto = ordenPorDefecto(SeccionBandeja.FORMALIZAR);
                    resultado = compararPorValor(valorDeOrdenSolicitud(una, porDefecto, clientesUsados),
                            valorDeOrdenSolicitud(otra, porDefecto, clientesUsados), false);
                }
                return resultado;
            }
        });
        return ordenadas;
    }

    /**
     * B183: ordena las secciones 2 y 3, que son las dos de préstamos y comparten columnas. Pura.
     *
     * @param clientesPorPrestamo mapa por {@code idPrestamo} (así lo arma el controlador, no por
     *     id de cliente: un préstamo llega a su cliente por la solicitud)
     * @param seccion decide la lista blanca y el desempate; pasar la que corresponde a la lista
     */
    public List<PrestamoCredito> ordenarPrestamosDeBandeja(List<PrestamoCredito> prestamos,
            java.util.Map<Long, Persona> clientesPorPrestamo, String clave, String sentido,
            final SeccionBandeja seccion) {
        if (prestamos == null) {
            return new ArrayList<>();
        }
        final String claveUsada = ordenAplicado(clave, seccion);
        final boolean descendente = "desc".equalsIgnoreCase(sentidoAplicado(sentido));
        final java.util.Map<Long, Persona> clientesUsados = clientesPorPrestamo == null
                ? java.util.Collections.<Long, Persona>emptyMap() : clientesPorPrestamo;
        List<PrestamoCredito> ordenados = new ArrayList<>(prestamos);
        java.util.Collections.sort(ordenados, new java.util.Comparator<PrestamoCredito>() {
            @Override
            public int compare(PrestamoCredito uno, PrestamoCredito otro) {
                int resultado = compararPorValor(valorDeOrdenPrestamo(uno, claveUsada, clientesUsados),
                        valorDeOrdenPrestamo(otro, claveUsada, clientesUsados), descendente);
                if (resultado == 0 && !ordenPorDefecto(seccion).equals(claveUsada)) {
                    String porDefecto = ordenPorDefecto(seccion);
                    resultado = compararPorValor(valorDeOrdenPrestamo(uno, porDefecto, clientesUsados),
                            valorDeOrdenPrestamo(otro, porDefecto, clientesUsados), false);
                }
                return resultado;
            }
        });
        return ordenados;
    }

    /**
     * B183: compara dos valores ya extraídos, con el sentido aplicado <b>acá adentro</b>.
     *
     * <p>Los nulos quedan <b>afuera de la inversión</b>: la fila a la que le falta ese dato va al
     * final en los dos sentidos. Es la lección de B177 --invertir el resultado completo sube los
     * nulos al primer lugar en descendente-- y por eso el valor se extrae primero y se compara una
     * sola vez, en vez de tener un {@code switch} que devuelva {@code int}.</p>
     */
    private int compararPorValor(Comparable<?> valorUno, Comparable<?> valorOtro, boolean descendente) {
        if (valorUno == null || valorOtro == null) {
            if (valorUno == null && valorOtro == null) {
                return 0;
            }
            return valorUno == null ? 1 : -1;
        }
        int resultado = compararNoNulos(valorUno, valorOtro);
        return descendente ? -resultado : resultado;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compararNoNulos(Comparable<?> uno, Comparable<?> otro) {
        return ((Comparable) uno).compareTo(otro);
    }

    /** B183: el valor por el que ordena la sección 1. Null cuando esa fila no tiene ese dato. */
    private Comparable<?> valorDeOrdenSolicitud(SolicitudCredito solicitud, String clave,
            java.util.Map<Long, Persona> clientes) {
        switch (clave) {
            case "codigo":
                return textoParaOrdenar(solicitud.getCodigoSolicitud());
            case "aprobada":
                return solicitud.getFechaDecision();
            case "cliente":
                return nombreDePersonaParaOrdenar(clientes.get(solicitud.getIdCliente()));
            case "monto":
                return solicitud.getMontoAprobado();
            case "plazo":
                // El getter devuelve Integer (puede ser null): se devuelve tal cual, para que un
                // null caiga en la regla de "al final en los dos sentidos" en vez de desenvolverse
                // y tirar NullPointerException al ordenar. Lo mismo vale para el monto, que es
                // BigDecimal y también puede venir null.
                return solicitud.getPlazoAprobadoMeses();
            default:
                return null;
        }
    }

    /** B183: el valor por el que ordenan las secciones 2 y 3. */
    private Comparable<?> valorDeOrdenPrestamo(PrestamoCredito prestamo, String clave,
            java.util.Map<Long, Persona> clientesPorPrestamo) {
        switch (clave) {
            case "operacion":
                return textoParaOrdenar(prestamo.getIdentificadorOperacion());
            case "cliente":
                return nombreDePersonaParaOrdenar(clientesPorPrestamo.get(prestamo.getIdPrestamo()));
            case "monto":
                return prestamo.getMontoAprobado();
            case "formalizado":
                return prestamo.getFechaFormalizacion();
            case "cancelada":
                return prestamo.getFechaFinalizacionFinanciera();
            default:
                return null;
        }
    }

    /** B183: las personas se ordenan por <b>apellidos y nombres normalizados</b>
     * ({@code NormalizadorTexto.normalizarParaBuscar}, el mismo criterio que el buscador), así que
     * "Ávila" ordena junto a "Avila" -- igual que en B167 y B175, para que las pantallas ordenen
     * igual al mismo cliente. */
    private String nombreDePersonaParaOrdenar(Persona persona) {
        return persona == null ? null
                : py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto
                        .normalizarParaBuscar(persona.getApellidos() + " " + persona.getNombres());
    }

    /** B183: null se mantiene null (va al final); el resto, normalizado como el buscador. */
    private String textoParaOrdenar(String texto) {
        return texto == null ? null
                : py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto.normalizarParaBuscar(texto);
    }

    /** Corrección 1 (Joel): préstamos ya formalizados, esperando el checklist para poder desembolsar. */
    public List<PrestamoCredito> listarPendientesDesembolso(Connection conexion) throws SQLException {
        return prestamoDAO.listarPendientesDesembolso(conexion);
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): buscador de
     * la bandeja (formalizacion/lista.jsp) para las 2 secciones que son PrestamoCredito
     * ("pendientes de desembolso" y "cierre documental pendiente") -- la de "para formalizar"
     * (SolicitudCredito) usa ServicioSolicitudes.filtrarPorTexto directo, sin pasar por acá.
     * Pura (sin conexión, fácil de probar con JUnit). Por N.º de operación, cédula (con o sin
     * puntos) o nombre del cliente, sin acentos y en los dos órdenes -- misma normalización que
     * Mora/Aprobaciones/Solicitudes. */
    public List<PrestamoCredito> filtrarPrestamosPorTexto(List<PrestamoCredito> prestamos,
            Map<Long, Persona> clientesPorPrestamo, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return prestamos;
        }
        String operacionBuscada = texto.trim().toUpperCase();
        String cedulaBuscada = servicioClientes.normalizarCedula(texto.trim());
        String nombreBuscado = NormalizadorTexto.normalizarParaBuscar(texto);
        List<PrestamoCredito> filtrados = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamos) {
            Persona cliente = clientesPorPrestamo == null ? null : clientesPorPrestamo.get(prestamo.getIdPrestamo());
            boolean coincideOperacion = prestamo.getIdentificadorOperacion() != null
                    && prestamo.getIdentificadorOperacion().toUpperCase().contains(operacionBuscada);
            boolean coincideCedula = !cedulaBuscada.isEmpty() && cliente != null && cliente.getCedulaIdentidad() != null
                    && servicioClientes.normalizarCedula(cliente.getCedulaIdentidad()).contains(cedulaBuscada);
            boolean coincideNombre = !nombreBuscado.isEmpty() && cliente != null && cliente.getNombres() != null
                    && cliente.getApellidos() != null
                    && (NormalizadorTexto.normalizarParaBuscar(cliente.getNombres() + " " + cliente.getApellidos())
                            .contains(nombreBuscado)
                        || NormalizadorTexto.normalizarParaBuscar(cliente.getApellidos() + " " + cliente.getNombres())
                            .contains(nombreBuscado));
            if (coincideOperacion || coincideCedula || coincideNombre) {
                filtrados.add(prestamo);
            }
        }
        return filtrados;
    }

    /** Rango de días permitido, ya como fechas concretas (Corrección 2, Joel). */
    public static final class RangoFechas {
        private final LocalDate minima;
        private final LocalDate maxima;

        public RangoFechas(LocalDate minima, LocalDate maxima) {
            this.minima = minima;
            this.maxima = maxima;
        }

        public LocalDate getMinima() {
            return minima;
        }

        public LocalDate getMaxima() {
            return maxima;
        }
    }

    /** Corrección 2 (Joel): fechas concretas para mostrar debajo del campo, antes de que el oficial elija. */
    public RangoFechas calcularRangoPrimerVencimiento(Connection conexion, LocalDate fechaFormalizacion) throws SQLException {
        int diasMinimo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_min_primer_vencimiento");
        int diasMaximo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_max_primer_vencimiento");
        return new RangoFechas(fechaFormalizacion.plusDays(diasMinimo), fechaFormalizacion.plusDays(diasMaximo));
    }

    /** Corrección 2 (Joel): feriados activos, para que la pantalla avise en el momento si la fecha elegida cae en
     * día inhábil (RN-19). Con el nombre (Feriado, no solo la fecha) desde el 11/10/2026 (RF11, pedido de Joel):
     * el aviso en pantalla y el detalle de auditoría necesitan decir CUÁL feriado, no solo que hay uno. */
    public List<Feriado> listarFeriadosActivos(Connection conexion) throws SQLException {
        return feriadoDAO.listarActivos(conexion);
    }

    /**
     * D-70: id del documento VIGENTE de cada PDF generado (CONTRATO_GENERADO/PAGARE_GENERADO/
     * PLAN_PAGOS_GENERADO), para el enlace de descarga en la ficha -- null si todavía no existe
     * (por ejemplo, un préstamo formalizado antes de que se aprobara el diseño, D-68).
     */
    public Map<String, Long> obtenerDocumentosGeneradosVigentes(Connection conexion, long idPrestamo) throws SQLException {
        Map<String, Long> documentos = new HashMap<>();
        for (String codigoTipo : new String[] {"CONTRATO_GENERADO", "PAGARE_GENERADO", "PLAN_PAGOS_GENERADO"}) {
            TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, codigoTipo);
            DocumentoExpediente vigente = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                    tipo.getIdTipoDocumento());
            documentos.put(codigoTipo, vigente == null ? null : vigente.getIdDocumento());
        }
        return documentos;
    }

    /** Plan de pagos vigente de un préstamo, para mostrarlo en la ficha (aunque el PDF todavía no exista). */
    public List<py.com.capitalcredito.sgcpc.modelos.Cuota> listarCuotasVigentes(Connection conexion, long idPrestamo)
            throws SQLException {
        PlanPago vigente = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
        return vigente == null ? new ArrayList<>() : cuotaDAO.listarPorPlan(conexion, vigente.getIdPlanPago());
    }

    /** Tanda 2 (14/10/2026, pedido de Joel): el plan vigente en sí (no solo sus cuotas), para que
     * la ficha avise cuando ya no es el ORIGINAL (RF27 lo regeneró con un adelanto de capital). */
    public PlanPago obtenerPlanVigente(Connection conexion, long idPrestamo) throws SQLException {
        return planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
    }

    /**
     * PO-05 paso 1: crea el préstamo (PENDIENTE_DESEMBOLSO desde el inicio, D-68 -- el checklist
     * de firmados lo exige recién E6 para desembolsar, no acá) con el plan definitivo calculado
     * sobre las condiciones YA aprobadas de la solicitud, salvo la fecha de primer vencimiento
     * (D-68: se re-propone al formalizar, editable dentro de un rango). Si la solicitud ya tiene
     * préstamo, no reinserta nada.
     * F2 (21/10/2026, pedido de Joel, punto 7): puedeResolver -- calculado en el controlador con
     * nivelAccesoSesion(APROBAR_RECHAZAR_SOLICITUD), nunca por rol -- decide la versión (a)/(b) de
     * los mensajes de bloqueo (afectación, sin comprobante, tope de tasa cambiado).
     */
    public long generarDocumentos(long idSolicitud, LocalDate primerVencimiento, long idUsuario,
            boolean puedeResolver) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, idSolicitud);
                if (solicitud == null) {
                    throw new NegocioException("La solicitud no existe.");
                }
                if (solicitud.getEstadoSolicitud() != EstadoSolicitud.APROBADA) {
                    throw new NegocioException("Solo se puede formalizar una solicitud aprobada.");
                }
                PrestamoCredito existente = prestamoDAO.obtenerPorSolicitud(conexion, idSolicitud);
                if (existente != null) {
                    throw new NegocioException("Esta solicitud ya fue formalizada ("
                            + existente.getIdentificadorOperacion() + ").");
                }
                // B84 (decisión de Joel, 28/10/2026): RN-01 revalidada acá, el tercer momento.
                // Va justo después del control de arriba -- que es el que PRUEBA que esta solicitud
                // todavía no tiene préstamo, la garantía en la que se apoya -- y antes de
                // generarIdentificadorOperacion (que consume numeración) y de emitir cualquier
                // documento: si rechaza, no se numeró ni se emitió nada.
                validarSinOtroPrestamoAbierto(conexion, solicitud.getIdCliente(), idSolicitud);

                // RN-15: tercera revalidación (simular y aprobar ya la hicieron). El tope pudo
                // cambiar entre la aprobación y la formalización.
                LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
                servicioParametros.resolverTopeLegalVigente(limiteVigente);
                ResultadoLimiteTasa resultadoLimite = servicioMotorFinanciero.validarLimiteTasa(
                        solicitud.getTasaTeaAprobada(), limiteVigente.getTasaMaximaTea());
                if (!resultadoLimite.isOk()) {
                    // F2 (21/10/2026, pedido de Joel, punto 7, mensaje 5): mismo mecanismo de
                    // "qué sigue" que los mensajes 1 y 2 de construirQueSigueCancelarSolicitud --
                    // el arreglo real es el mismo (cancelar la solicitud para evaluar una nueva).
                    throw new NegocioException("El tope de tasa vigente cambió y la TEA aprobada ("
                            + FormateadorNumeros.comoPorcentaje(solicitud.getTasaTeaAprobada())
                            + ") ya no cumple (tope actual: " + FormateadorNumeros.comoPorcentaje(
                                    limiteVigente.getTasaMaximaTea()) + "). No se puede formalizar. "
                            + construirQueSigueCancelarSolicitud(puedeResolver,
                                    "Puede cancelar la solicitud desde Aprobación para evaluar una nueva."));
                }

                LocalDate hoy = Reloj.hoy();
                int diasMinimo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_min_primer_vencimiento");
                int diasMaximo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_max_primer_vencimiento");
                validarPrimerVencimientoFormalizacion(hoy, primerVencimiento, diasMinimo, diasMaximo);

                List<Feriado> feriadosActivos = feriadoDAO.listarActivos(conexion);
                List<LocalDate> feriados = new ArrayList<>();
                for (Feriado feriado : feriadosActivos) {
                    feriados.add(feriado.getFecha());
                }
                // RF11 (pedido de Joel, 11/10/2026): además de la fecha elegida (validada arriba),
                // la fecha YA CORRIDA al siguiente día hábil tiene que seguir dentro del rango --
                // un corrimiento cerca del límite puede empujarla fuera sin que nada lo avise.
                LocalDate primerVencimientoEfectivo = servicioMotorFinanciero.fechaHabil(primerVencimiento, feriados);
                validarPrimerVencimientoEfectivoFormalizacion(hoy, primerVencimientoEfectivo, diasMaximo, feriados);

                ResultadoCotizacion resultado = servicioMotorFinanciero.cotizar(solicitud.getMontoAprobado(),
                        solicitud.getPlazoAprobadoMeses(), primerVencimiento, solicitud.getTasaTeaAprobada(), feriados);
                // RF11 (pedido de Joel, 11/10/2026): el 40 % se vuelve a controlar con el ingreso y
                // las deudas ACTUALES del cliente, sin excepción -- igual que al enviar a
                // aprobación (RF06), porque pudo pasar tiempo desde que se aprobó.
                BigDecimal cuotaMasAlta = servicioMotorFinanciero.cuotaMasAlta(resultado);
                revalidarCapacidadPagoFormalizacion(conexion, solicitud, cuotaMasAlta, puedeResolver);

                String identificadorOperacion = generarIdentificadorOperacion(conexion, hoy.getYear());

                BigDecimal factorMoratorio = servicioParametros.obtenerValorDecimal(conexion, "credito.factor_moratorio");
                BigDecimal porcentajePunitorio = servicioParametros.obtenerValorDecimal(conexion, "credito.porcentaje_punitorio");
                ResultadoTasasMora tasasMora = ServicioMotorFinanciero.calcularTasasMora(
                        solicitud.getTasaTeaAprobada(), factorMoratorio, porcentajePunitorio);
                BigDecimal tasaMoratoria = tasasMora.getTasaMoratoria();
                BigDecimal tasaPunitoria = tasasMora.getTasaPunitoria();

                PrestamoCredito prestamo = new PrestamoCredito();
                prestamo.setIdentificadorOperacion(identificadorOperacion);
                prestamo.setIdSolicitud(idSolicitud);
                // D-68 (ajuste de Joel): numero_contrato/numero_pagare derivados del mismo
                // identificador_operacion -- una sola operación, tres documentos.
                prestamo.setNumeroContrato(identificadorOperacion);
                prestamo.setNumeroPagare(identificadorOperacion);
                prestamo.setMontoAprobado(solicitud.getMontoAprobado());
                prestamo.setPlazoMeses(solicitud.getPlazoAprobadoMeses());
                prestamo.setTasaTna(solicitud.getTasaTnaAprobada());
                prestamo.setTasaTea(solicitud.getTasaTeaAprobada());
                // RF11 (pedido de Joel, 11/10/2026): CFT del cotizador recién calculado con el
                // mismo motor (antes quedaba el de la solicitud, sin relación con esta cotización).
                prestamo.setCft(resultado.getCft());
                prestamo.setTasaMoratoria(tasaMoratoria);
                prestamo.setTasaPunitoria(tasaPunitoria);
                prestamo.setPrimeraFechaVencimiento(primerVencimiento);
                prestamo.setEstadoPrestamo(EstadoPrestamo.PENDIENTE_DESEMBOLSO);
                prestamo.setEstadoCierreDocumental("NO_APLICA");
                prestamo.setFormalizadoPor(idUsuario);
                prestamo.setIdLimiteTasa(limiteVigente.getIdLimiteTasa());
                prestamo.setTasaMensual(ServicioMotorFinanciero.calcularTasaMensual(solicitud.getTasaTeaAprobada()));
                prestamo.setCuotaContractual(resultado.getCuotaSinIva());
                long idPrestamo = prestamoDAO.insertar(conexion, prestamo);
                prestamo.setIdPrestamo(idPrestamo);

                PlanPago plan = new PlanPago();
                plan.setIdPrestamo(idPrestamo);
                plan.setVersionPlan(1);
                plan.setOrigenPlan("ORIGINAL");
                plan.setVigente(true);
                plan.setGeneradoPor(idUsuario);
                long idPlanPago = planPagoDAO.insertar(conexion, plan);
                cuotaDAO.insertarTodas(conexion, idPlanPago, resultado.getPlan());

                // D-68: la solicitud deja de contar como "abierta" para RN-01/RN-02/RN-28.
                solicitudDAO.marcarFormalizada(conexion, idSolicitud);

                servicioAuditoria.registrar(conexion, idUsuario, "GENERAR_DOCUMENTOS_FORMALIZACION", "PRESTAMO",
                        idPrestamo, "Formalizó la solicitud " + solicitud.getCodigoSolicitud() + " como "
                                + identificadorOperacion + ". "
                                + construirDetalleFechaFormalizacion(solicitud, primerVencimiento,
                                        primerVencimientoEfectivo, feriadosActivos, cuotaMasAlta, resultado.getCft()));

                // D-70 (diseño aprobado): los 3 PDF se generan en la misma transacción que crea el préstamo.
                generarSiFalta(conexion, prestamo, "CONTRATO_GENERADO", idUsuario);
                generarSiFalta(conexion, prestamo, "PAGARE_GENERADO", idUsuario);
                generarSiFalta(conexion, prestamo, "PLAN_PAGOS_GENERADO", idUsuario);

                conexion.commit();
                return idPrestamo;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                // Carrera real (doble clic sobre "Sí, generar" -- SweetAlert2 no deshabilita el
                // botón de confirmación durante el envío, punto 7 de la tanda de usabilidad,
                // hallazgo de Joel del 20/10/2026): el chequeo de existente == null de más arriba
                // no alcanza a frenar un segundo envío que llega antes de que el primero comitee;
                // la restricción UNIQUE de prestamos.id_solicitud lo frena acá. Se traduce al
                // mismo mensaje de negocio del chequeo de arriba, no a un error genérico (mismo
                // criterio que ServicioDesembolso/ServicioCobranza para identificador_confirmacion).
                if ("23505".equals(error.getSQLState())) {
                    PrestamoCredito existente = prestamoDAO.obtenerPorSolicitud(conexion, idSolicitud);
                    if (existente != null) {
                        throw new NegocioException("Esta solicitud ya fue formalizada ("
                                + existente.getIdentificadorOperacion() + ").");
                    }
                }
                throw error;
            }
        }
    }

    /**
     * Ajuste de Joel (regenerar): rechazado si ya hay un CONTRATO_FIRMADO o PAGARE_FIRMADO
     * vigente cargado. Si la fecha de primer vencimiento cambió, recalcula el plan como una
     * nueva versión (la anterior queda no vigente, mismo mecanismo que un anticipo parcial usa
     * en etapas siguientes) reutilizando el mismo identificador_operacion/numero_contrato/
     * numero_pagare.
     * F2 (21/10/2026, pedido de Joel, punto 7): mismo puedeResolver que generarDocumentos.
     *
     * <p>B125 (11/11/2026): devuelve <b>si hubo regeneración</b>. Con la MISMA fecha no se
     * reemplaza nada —ni plan, ni PDF, ni CFT— y hasta ahora el controlador igual decía
     * "Documentos regenerados." y se escribía una fila de auditoría: un no-evento registrado como
     * evento, en la operación que más tiene que poder reconstruirse desde la auditoría. Por eso el
     * {@code registrar} pasó a estar <b>dentro</b> del {@code if} (antes era la única línea que
     * corría siempre) y quien llama elige el mensaje. Las validaciones de antes —pendiente de
     * desembolso, sin firmados cargados y la fecha dentro del rango— siguen corriendo igual, con
     * fecha nueva o repetida.</p>
     *
     * @return {@code true} si la fecha cambió y se regeneraron el plan y los tres PDF;
     *         {@code false} si la fecha era la misma y no se tocó nada.
     */
    public boolean regenerarDocumentos(long idPrestamo, LocalDate primerVencimiento, long idUsuario,
            boolean puedeResolver) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                validarPendienteDesembolso(conexion, prestamo);
                asegurarSinDocumentosFirmados(conexion, idPrestamo);

                int diasMinimo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_min_primer_vencimiento");
                int diasMaximo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_max_primer_vencimiento");
                validarPrimerVencimientoFormalizacion(Reloj.hoy(), primerVencimiento, diasMinimo, diasMaximo);

                String detalleFecha = null;
                boolean cambioLaFecha = !primerVencimiento.equals(prestamo.getPrimeraFechaVencimiento());
                if (cambioLaFecha) {
                    List<Feriado> feriadosActivos = feriadoDAO.listarActivos(conexion);
                    List<LocalDate> feriados = new ArrayList<>();
                    for (Feriado feriado : feriadosActivos) {
                        feriados.add(feriado.getFecha());
                    }
                    // RF11 (pedido de Joel, 11/10/2026): mismas dos revalidaciones que al
                    // formalizar -- la fecha ya corrida sigue dentro del rango, y el 40 % se
                    // vuelve a controlar con la situación actual del cliente.
                    LocalDate primerVencimientoEfectivo = servicioMotorFinanciero.fechaHabil(primerVencimiento, feriados);
                    validarPrimerVencimientoEfectivoFormalizacion(Reloj.hoy(), primerVencimientoEfectivo, diasMaximo,
                            feriados);

                    ResultadoCotizacion resultado = servicioMotorFinanciero.cotizar(prestamo.getMontoAprobado(),
                            prestamo.getPlazoMeses(), primerVencimiento, prestamo.getTasaTea(), feriados);
                    BigDecimal cuotaMasAlta = servicioMotorFinanciero.cuotaMasAlta(resultado);
                    SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
                    revalidarCapacidadPagoFormalizacion(conexion, solicitud, cuotaMasAlta, puedeResolver);

                    PlanPago vigente = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
                    planPagoDAO.marcarNoVigente(conexion, vigente.getIdPlanPago());

                    PlanPago nuevo = new PlanPago();
                    nuevo.setIdPrestamo(idPrestamo);
                    nuevo.setVersionPlan(vigente.getVersionPlan() + 1);
                    nuevo.setOrigenPlan("ORIGINAL");
                    nuevo.setVigente(true);
                    nuevo.setGeneradoPor(idUsuario);
                    long idPlanNuevo = planPagoDAO.insertar(conexion, nuevo);
                    cuotaDAO.insertarTodas(conexion, idPlanNuevo, resultado.getPlan());

                    // RF11 (pedido de Joel, 11/10/2026): el CFT se recalcula con el mismo motor y
                    // se persiste junto con la fecha -- antes quedaba el CFT original, sin
                    // relación con el plan regenerado.
                    prestamoDAO.actualizarPrimerVencimientoYCft(conexion, idPrestamo, primerVencimiento, resultado.getCft());
                    prestamo.setPrimeraFechaVencimiento(primerVencimiento);
                    prestamo.setCft(resultado.getCft());

                    detalleFecha = construirDetalleFechaFormalizacion(solicitud, primerVencimiento,
                            primerVencimientoEfectivo, feriadosActivos, cuotaMasAlta, resultado.getCft());

                    // D-70: el plan cambió -- los PDF ya generados quedaron desactualizados.
                    regenerarPdfTipo(conexion, prestamo, "CONTRATO_GENERADO", idUsuario);
                    regenerarPdfTipo(conexion, prestamo, "PAGARE_GENERADO", idUsuario);
                    regenerarPdfTipo(conexion, prestamo, "PLAN_PAGOS_GENERADO", idUsuario);

                    // B125: la auditoría va ACÁ ADENTRO. Afuera, se registraba "Regeneró los
                    // documentos de ..." también cuando no se había regenerado nada.
                    String detalle = "Regeneró los documentos de " + prestamo.getIdentificadorOperacion() + "."
                            + (detalleFecha == null ? "" : " " + detalleFecha);
                    servicioAuditoria.registrar(conexion, idUsuario, "REGENERAR_DOCUMENTOS_FORMALIZACION",
                            "PRESTAMO", idPrestamo, detalle);
                }
                conexion.commit();
                return cambioLaFecha;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Ajuste de Joel (revisión de reportes E5): re-emite los 3 PDF con la plantilla/datos
     * actuales SIN tocar el plan ni el número de operación -- a diferencia de
     * regenerarDocumentos, que solo re-emite los PDF cuando cambia la fecha de primer
     * vencimiento. Sirve para aplicar una corrección de diseño de reporte a un préstamo ya
     * formalizado. PA-31 (14/10/2026, pedido de Joel, autorizado): SÍ exige que no haya firmados
     * cargados, igual que regenerarDocumentos -- aunque el plan no cambie, reemitir dejaría un
     * CONTRATO_GENERADO/PAGARE_GENERADO/PLAN_PAGOS_GENERADO "vigente" con datos actuales distinto
     * del documento que el cliente ya firmó (antes de PA-31 esta exigencia se dejaba afuera a
     * propósito; se revirtió esa decisión).
     */
    public void regenerarSoloPdfs(long idPrestamo, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                validarPendienteDesembolso(conexion, prestamo);
                asegurarSinDocumentosFirmados(conexion, idPrestamo);
                regenerarPdfTipo(conexion, prestamo, "CONTRATO_GENERADO", idUsuario);
                regenerarPdfTipo(conexion, prestamo, "PAGARE_GENERADO", idUsuario);
                regenerarPdfTipo(conexion, prestamo, "PLAN_PAGOS_GENERADO", idUsuario);
                servicioAuditoria.registrar(conexion, idUsuario, "REGENERAR_DOCUMENTOS_FORMALIZACION", "PRESTAMO",
                        idPrestamo, "Re-emitió los PDF de " + prestamo.getIdentificadorOperacion()
                                + " (sin cambiar plan ni número).");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private void asegurarSinDocumentosFirmados(Connection conexion, long idPrestamo) throws SQLException {
        TipoDocumento contratoFirmado = tipoDocumentoDAO.obtenerPorCodigo(conexion, "CONTRATO_FIRMADO");
        TipoDocumento pagareFirmado = tipoDocumentoDAO.obtenerPorCodigo(conexion, "PAGARE_FIRMADO");
        // Tanda 2 (14/10/2026, pedido de Joel, RF13 §10b): faltaba mirar PLAN_PAGOS_FIRMADO --
        // si solo el plan firmado estuviera cargado (sin contrato ni pagaré), antes se podía
        // regenerar igual, dejando ese escaneo desincronizado del plan nuevo.
        TipoDocumento planPagosFirmado = tipoDocumentoDAO.obtenerPorCodigo(conexion, "PLAN_PAGOS_FIRMADO");
        boolean hayContratoFirmado = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                contratoFirmado.getIdTipoDocumento()) != null;
        boolean hayPagareFirmado = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                pagareFirmado.getIdTipoDocumento()) != null;
        boolean hayPlanPagosFirmado = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                planPagosFirmado.getIdTipoDocumento()) != null;
        if (bloqueaRegenerar(hayContratoFirmado, hayPagareFirmado, hayPlanPagosFirmado)) {
            throw new NegocioException("No se puede regenerar: ya hay un documento firmado cargado para esta "
                    + "operación (regenerar dejaría firmado un documento distinto al vigente).");
        }
    }

    /** Pura, separada para poder probarla con JUnit sin base (Tanda 2, 14/10/2026). */
    public boolean bloqueaRegenerar(boolean hayContratoFirmado, boolean hayPagareFirmado, boolean hayPlanPagosFirmado) {
        return hayContratoFirmado || hayPagareFirmado || hayPlanPagosFirmado;
    }

    private String generarIdentificadorOperacion(Connection conexion, int anio) throws SQLException {
        long secuencia = numeradorDAO.siguienteValor(conexion, "OPERACION", anio);
        return String.format("CC-%d-%05d", anio, secuencia);
    }

    /** Un ítem del checklist de formalización: el tipo de documento, si ya está cubierto, su etiqueta y su documento vigente (si hay). */
    public static final class ItemChecklist {
        private final TipoDocumento tipo;
        private final boolean cumplido;
        private final String etiqueta;
        private final Long idDocumentoVigente;

        public ItemChecklist(TipoDocumento tipo, boolean cumplido, String etiqueta, Long idDocumentoVigente) {
            this.tipo = tipo;
            this.cumplido = cumplido;
            this.etiqueta = etiqueta;
            this.idDocumentoVigente = idDocumentoVigente;
        }

        public TipoDocumento getTipo() {
            return tipo;
        }

        public boolean isCumplido() {
            return cumplido;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        /** Corrección 3 (Joel): id del documento vigente cargado, para el enlace de Ver/Descargar. Null si no hay. */
        public Long getIdDocumentoVigente() {
            return idDocumentoVigente;
        }
    }

    // ======================================================================
    // B100 + B104 (01/11/2026, decisión de Joel): el flujo guiado del tramo
    // "solicitud aprobada -> formalizar -> firmados -> desembolso".
    //
    // Mismo patrón y MISMO DTO que B88 (PasoGuiado), pero dueño propio: el de B88
    // (ServicioClientes.calcularSiguientePaso) es del circuito del oficial sobre una PERSONA
    // (expediente -> buró -> solicitud) y recibe un idPersona; este tramo va sobre una solicitud
    // o un préstamo. Lo que se comparte es el DTO y la traducción destino -> pantalla, que siguen
    // viviendo en un solo lugar.
    //
    // Los tres métodos son PUROS: reciben lo ya cargado y no tocan la base, para poder probarlos
    // con JUnit sin base de datos (REGLAS_DESARROLLO.md §7 punto 9).
    // ======================================================================

    /**
     * Qué sigue después de aprobar una solicitud: formalizarla, o ver el préstamo si ya se
     * formalizó. B104: la ficha de una solicitud aprobada mostraba "Aprobada por ... Monto
     * aprobado: ..." y nada más, aunque el cartel de éxito promete "Ahora se formaliza en
     * Formalización" -- el mensaje nombraba el paso siguiente y dejaba al usuario buscándolo.
     *
     * @param prestamoYaFormalizado el préstamo de esa solicitud, o null si todavía no se formalizó
     */
    public PasoGuiado calcularPasoDeLaSolicitudAprobada(long idSolicitud, PrestamoCredito prestamoYaFormalizado) {
        if (prestamoYaFormalizado == null) {
            return new PasoGuiado("Ahora se formaliza: se generan el contrato, el pagaré y el plan de pagos.",
                    PasoGuiado.Destino.FORMALIZACION_NUEVA, "Formalizar esta solicitud", idSolicitud);
        }
        return PasoGuiado.haciaPrestamo(
                "Ya formalizada como " + prestamoYaFormalizado.getIdentificadorOperacion() + ".",
                "Ver el préstamo " + prestamoYaFormalizado.getIdentificadorOperacion(),
                prestamoYaFormalizado.getIdPrestamo());
    }

    /**
     * Los tipos de documento que todavía faltan, por su nombre y sin repetir, para nombrarlos en
     * un aviso: "Contrato firmado, Pagaré firmado".
     *
     * <p>Usa {@code getNombreTipo()} y <b>no</b> {@code getEtiqueta()}: la etiqueta del checklist
     * ya le agrega " (firmado por el cliente)" y en un aviso quedaría "Contrato firmado (firmado
     * por el cliente)". Pura.</p>
     */
    public static String nombrarLoQueFalta(List<ItemChecklist> checklist) {
        List<String> faltan = new ArrayList<>();
        for (ItemChecklist item : checklist) {
            if (!item.isCumplido() && !faltan.contains(item.getTipo().getNombreTipo())) {
                faltan.add(item.getTipo().getNombreTipo());
            }
        }
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < faltan.size(); i++) {
            if (i > 0) {
                texto.append(", ");
            }
            texto.append(faltan.get(i));
        }
        return texto.toString();
    }

    /**
     * El aviso de la tarjeta de desembolsos del panel (B100): qué falta para que los préstamos
     * pendientes se puedan desembolsar. Cadena vacía cuando no falta nada que cargar -- ahí la
     * tarjeta no tiene nada que agregar.
     *
     * <p>El dato no es nuevo: la diferencia entre "pendientes" y "listos" ES "les falta el
     * checklist", y {@code obtenerChecklist} ya sabe decir qué ítem falta (es lo que muestra la
     * ficha de Formalización). Pura.</p>
     */
    public static String avisoDeLoQueFaltaParaDesembolsar(Collection<List<ItemChecklist>> checklistsPendientes) {
        List<ItemChecklist> todos = new ArrayList<>();
        for (List<ItemChecklist> checklist : checklistsPendientes) {
            todos.addAll(checklist);
        }
        String faltan = nombrarLoQueFalta(todos);
        return faltan.isEmpty() ? "" : "falta cargar: " + faltan;
    }

    /** Corrección 3 (Joel): todos los ítems del checklist cumplidos -- listo para el desembolso. */
    public boolean esChecklistCompleto(List<ItemChecklist> checklist) {
        for (ItemChecklist item : checklist) {
            if (!item.isCumplido()) {
                return false;
            }
        }
        return true;
    }

    /** Checklist previo al desembolso (contexto PRESTAMO, obligatorio_desembolso = TRUE). */
    public List<ItemChecklist> obtenerChecklist(Connection conexion, long idPrestamo) throws SQLException {
        List<ItemChecklist> checklist = new ArrayList<>();
        for (TipoDocumento tipo : tipoDocumentoDAO.listarActivosPorContexto(conexion, "PRESTAMO")) {
            if (!tipo.isObligatorioDesembolso()) {
                continue;
            }
            DocumentoExpediente vigente = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                    tipo.getIdTipoDocumento());
            String etiqueta = tipo.getNombreTipo() + " (firmado por el cliente)";
            checklist.add(new ItemChecklist(tipo, vigente != null, etiqueta,
                    vigente == null ? null : vigente.getIdDocumento()));
        }
        return checklist;
    }

    /**
     * Bug encontrado por Joel en la prueba manual de E6: el fondo sugerido de caja sumaba TODOS
     * los préstamos PENDIENTE_DESEMBOLSO (sin exigir checklist completo) mientras que la bandeja
     * de desembolso solo mostraba los que sí lo tenían completo -- el fondo sugerido podía sumar
     * más de lo que en realidad se puede desembolsar hoy (Doc. 10 §10.11: "Elegibilidad"). Ahora
     * las dos pantallas comparten el mismo criterio: este método calcula el checklist de cada
     * préstamo una sola vez, y las funciones puras de abajo (`filtrarConChecklistCompleto`,
     * `sumarMontosAprobadosConChecklistCompleto`) garantizan que el resultado sea idéntico.
     */
    /**
     * B100: el aviso de la tarjeta de desembolsos, con los checklists de los préstamos que
     * todavía NO están listos -- los que ya lo están no tienen nada que falte. Mismo criterio y
     * mismos datos que {@link #listarListosParaDesembolsar}, sin una consulta nueva.
     */
    public String obtenerAvisoDeLoQueFaltaParaDesembolsar(Connection conexion) throws SQLException {
        List<List<ItemChecklist>> incompletos = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamoDAO.listarPendientesDesembolso(conexion)) {
            List<ItemChecklist> checklist = obtenerChecklist(conexion, prestamo.getIdPrestamo());
            if (!esChecklistCompleto(checklist)) {
                incompletos.add(checklist);
            }
        }
        return avisoDeLoQueFaltaParaDesembolsar(incompletos);
    }

    public List<PrestamoCredito> listarListosParaDesembolsar(Connection conexion) throws SQLException {
        List<PrestamoCredito> pendientes = prestamoDAO.listarPendientesDesembolso(conexion);
        return filtrarConChecklistCompleto(pendientes, calcularChecklistCompletoPorPrestamo(conexion, pendientes));
    }

    /** Fondo sugerido de caja (decisión 1 del plan de E6): mismo criterio que la bandeja de arriba. */
    public BigDecimal calcularFondoSugeridoDesembolso(Connection conexion) throws SQLException {
        List<PrestamoCredito> pendientes = prestamoDAO.listarPendientesDesembolso(conexion);
        return sumarMontosAprobadosConChecklistCompleto(pendientes, calcularChecklistCompletoPorPrestamo(conexion, pendientes));
    }

    /** Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, punto 7 paso E):
     * cuántos PENDIENTE_DESEMBOLSO hay con el checklist todavía incompleto -- para el aviso de
     * /desembolso ("Préstamos listos para desembolsar" no debería dejar pensar que no hay nada
     * pendiente en absoluto). Mismos datos que listarListosParaDesembolsar, por diferencia. */
    public int contarPendientesConChecklistIncompleto(Connection conexion) throws SQLException {
        List<PrestamoCredito> pendientes = prestamoDAO.listarPendientesDesembolso(conexion);
        int completos = filtrarConChecklistCompleto(pendientes, calcularChecklistCompletoPorPrestamo(conexion, pendientes)).size();
        return pendientes.size() - completos;
    }

    private Map<Long, Boolean> calcularChecklistCompletoPorPrestamo(Connection conexion, List<PrestamoCredito> prestamos)
            throws SQLException {
        Map<Long, Boolean> checklistCompletoPorPrestamo = new HashMap<>();
        for (PrestamoCredito prestamo : prestamos) {
            checklistCompletoPorPrestamo.put(prestamo.getIdPrestamo(),
                    esChecklistCompleto(obtenerChecklist(conexion, prestamo.getIdPrestamo())));
        }
        return checklistCompletoPorPrestamo;
    }

    /** Pura: filtra los préstamos cuyo checklist ya viene calculado como completo. */
    public List<PrestamoCredito> filtrarConChecklistCompleto(List<PrestamoCredito> prestamos,
            Map<Long, Boolean> checklistCompletoPorPrestamo) {
        List<PrestamoCredito> listos = new ArrayList<>();
        for (PrestamoCredito prestamo : prestamos) {
            if (Boolean.TRUE.equals(checklistCompletoPorPrestamo.get(prestamo.getIdPrestamo()))) {
                listos.add(prestamo);
            }
        }
        return listos;
    }

    /** Pura: suma el monto aprobado solo de los préstamos cuyo checklist ya viene calculado como completo. */
    public BigDecimal sumarMontosAprobadosConChecklistCompleto(List<PrestamoCredito> prestamos,
            Map<Long, Boolean> checklistCompletoPorPrestamo) {
        BigDecimal total = BigDecimal.ZERO;
        for (PrestamoCredito prestamo : prestamos) {
            if (Boolean.TRUE.equals(checklistCompletoPorPrestamo.get(prestamo.getIdPrestamo()))) {
                total = total.add(prestamo.getMontoAprobado());
            }
        }
        return total;
    }

    /** D-70: diseño aprobado por Joel (docs/diseno_reportes_e5.md) -- llena el .jasper ya compilado, sin calcular nada acá. */
    public byte[] generarPdfContrato(Connection conexion, long idPrestamo) throws SQLException {
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        Map<String, Object> parametros = construirParametrosReporte(conexion, prestamo, prestamo.getFormalizadoPor());
        List<ClausulaReporte> clausulas = construirClausulasContrato(parametros);
        return llenarYExportar("contrato.jasper", parametros, new JRBeanCollectionDataSource(clausulas));
    }

    /**
     * D-70: las 10 cláusulas del contrato -- el reporte solo itera esta lista, una fila por
     * cláusula, para que JasperReports pagine un documento largo correctamente.
     */
    /**
     * C12 (hallazgo del recorrido, decisión de Joel del 29/10/2026): la fecha EFECTIVA de la cuota
     * 1 -- la que de verdad se va a cobrar, ya corrida al siguiente día hábil si la elegida cayó
     * en sábado, domingo o feriado. Vive en la primera cuota del plan, porque
     * {@code prestamos.primera_fecha_vencimiento} guarda la fecha <b>elegida</b> al formalizar y
     * la efectiva no tiene columna propia.
     *
     * <p>Único dueño del cálculo: lo usan el contrato y el plan (desde antes, corrección de Joel)
     * y, desde hoy, el <b>comprobante de desembolso</b>, que mostraba la elegida y contradecía a
     * los dos documentos que el cliente ya había firmado -- mismo criterio que D-138 ("el cliente
     * firma acá el monto que realmente va a pagar, no uno distinto que nunca ve después").</p>
     *
     * <p>Si el plan no se pudo leer (lista vacía, caso defensivo) devuelve la elegida, que es lo
     * único que hay: nunca rompe la emisión del documento. Pura.</p>
     */
    public LocalDate primeraFechaVencimientoEfectiva(List<Cuota> cuotas, PrestamoCredito prestamo) {
        return cuotas == null || cuotas.isEmpty() ? prestamo.getPrimeraFechaVencimiento()
                : cuotas.get(0).getFechaVencimiento();
    }

    /**
     * C3 (hallazgo del recorrido, decisión de Joel del 29/10/2026): las dos frases de la cláusula
     * SEGUNDA que describen CUÁNDO vence cada cuota. Antes decía *"La primera cuota vence el {X} y
     * las siguientes <b>el mismo día</b> de cada mes"*, con {X} = la fecha EFECTIVA (ya corrida a
     * día hábil) -- y eso describía mal el cálculo: {@code ServicioMotorFinanciero.generarPlanPagos}
     * ancla cada cuota al día de la fecha <b>ELEGIDA</b>
     * ({@code fechaPrimerVencimiento.plusMonths(k-1)}) y recién sobre esa fecha teórica aplica el
     * corrimiento a día hábil, cuota por cuota. Con una elegida que cae sábado, el contrato
     * prometía el lunes de cada mes y el plan usaba el sábado corrido: dos documentos de la misma
     * carpeta diciendo cosas distintas.
     *
     * <p><b>El cálculo no se toca</b> (decisión de Joel): el plan hace lo correcto -- ancla al día
     * que el cliente eligió. Lo que se corrige es el texto, para que describa exactamente eso.</p>
     *
     * <p><b>Días 29, 30 y 31</b>: {@code LocalDate.plusMonths} resuelve una fecha inexistente al
     * ÚLTIMO día del mes (31/01 + 1 mes = 28/02, o 29/02 en año bisiesto), así que con un día
     * elegido de 29 o más la cláusula agrega esa aclaración -- y solo entonces, porque con 28 o
     * menos nunca puede pasar. Verificado con JUnit sobre el propio {@code generarPlanPagos}
     * ({@code ServicioMotorFinancieroTest}), no solo por lectura.</p>
     *
     * <p>Pura, para poder probar el texto sin base ni PDF.</p>
     */
    String textoVencimientosClausulaSegunda(String primeraFechaVencimientoEfectiva, int diaVencimientoElegido) {
        StringBuilder texto = new StringBuilder("La primera cuota vence el ")
                .append(primeraFechaVencimientoEfectiva)
                .append(" y las siguientes el día ")
                .append(diaVencimientoElegido)
                .append(" de cada mes");
        if (diaVencimientoElegido >= 29) {
            texto.append(", o el último día del mes si ese mes no lo tiene");
        }
        return texto.append(". Cuando un vencimiento cae en sábado, domingo o feriado, se traslada al "
                + "siguiente día hábil.").toString();
    }

    // Visibilidad de paquete (29/10/2026, C3): para que la muestra del contrato arme las cláusulas
    // con ESTE método, el mismo que usa el sistema, en vez de copiar el texto a mano
    // (sgcpc-reportes-jasper: "una muestra que miente sobre el texto real no sirve para aprobar").
    List<ClausulaReporte> construirClausulasContrato(Map<String, Object> p) {
        List<ClausulaReporte> clausulas = new ArrayList<>();
        // Corrección de Joel (revisión de reportes E5, segunda vuelta): la presentación de las
        // partes (ciudad/fecha, acreedor, deudor) y el cierre "convienen celebrar..." vivían en
        // la banda "title" del reporte -- una banda de una sola vez, cuya altura declarada es un
        // mínimo fijo que no se achica aunque el texto real sea más corto, dejando un hueco antes
        // de "PRIMERA". Al ser filas del mismo datasource que las cláusulas, el reporte las
        // ajusta al contenido real igual que a ellas (mismo mecanismo, ya probado, que empareja
        // el espacio entre cláusulas), sin encabezado en negrita (filas vacías que colapsan con
        // removeLineWhenBlank + positionType="Float").
        clausulas.add(new ClausulaReporte("",
                "En la ciudad de Asunción, República del Paraguay, " + p.get("FECHA_FORMALIZACION_LETRAS")
                        + ", entre:"));
        clausulas.add(new ClausulaReporte("",
                "EL ACREEDOR: " + p.get("EMPRESA_RAZON_SOCIAL") + ", RUC " + p.get("EMPRESA_RUC")
                        + ", con domicilio en " + p.get("EMPRESA_DOMICILIO") + ", representada en este acto por "
                        + p.get("EMPRESA_REPRESENTANTE") + "; y"));
        clausulas.add(new ClausulaReporte("",
                "EL DEUDOR: " + p.get("CLIENTE_NOMBRE") + ", C.I. N.º " + p.get("CLIENTE_CEDULA")
                        + ", con domicilio en " + p.get("CLIENTE_DOMICILIO") + ", teléfono "
                        + p.get("CLIENTE_TELEFONO") + ";"));
        clausulas.add(new ClausulaReporte("",
                "convienen celebrar el presente contrato de préstamo, sujeto a las siguientes cláusulas:"));
        clausulas.add(new ClausulaReporte("PRIMERA — Objeto.",
                "EL ACREEDOR otorga a EL DEUDOR un préstamo de dinero por la suma de " + p.get("MONTO_APROBADO")
                        + " (" + p.get("MONTO_APROBADO_LETRAS") + "), destinado a " + p.get("DESTINO_CREDITO")
                        + ". El importe se entrega en efectivo en el local de EL ACREEDOR en la fecha de desembolso, "
                        + "contra la firma del comprobante de desembolso, que forma parte de este contrato."));
        clausulas.add(new ClausulaReporte("SEGUNDA — Plazo y forma de pago.",
                "EL DEUDOR se obliga a devolver el préstamo en " + p.get("PLAZO_MESES") + " cuotas mensuales y "
                        + "consecutivas, calculadas por el sistema francés (cuota de capital e intereses constante, "
                        + "más el IVA sobre los intereses de cada período). "
                        + textoVencimientosClausulaSegunda(String.valueOf(p.get("PRIMERA_FECHA_VENCIMIENTO")),
                                (Integer) p.get("DIA_VENCIMIENTO_ELEGIDO"))
                        + " El detalle "
                        + "de cada cuota (fecha, capital, interés, IVA y total) consta en el Plan de Pagos N.º "
                        + p.get("IDENTIFICADOR_OPERACION") + ", que forma parte de este contrato. Los pagos se "
                        + "realizan en efectivo en el local de EL ACREEDOR."));
        clausulas.add(new ClausulaReporte("TERCERA — Tasas y costo del crédito.",
                "El préstamo devenga una Tasa Efectiva Anual (TEA) de " + p.get("TEA") + ", equivalente a una Tasa "
                        + "Nominal Anual (TNA) de " + p.get("TNA") + ", y un Costo Financiero Total (CFT) de "
                        + p.get("CFT") + " anual. La tasa pactada no supera la tasa máxima vigente publicada por el "
                        + "Banco Central del Paraguay a la fecha de este contrato. El total a pagar, incluyendo "
                        + "capital, intereses e IVA, asciende a " + p.get("TOTAL_A_PAGAR") + " ("
                        + p.get("TOTAL_A_PAGAR_LETRAS") + ")."));
        clausulas.add(new ClausulaReporte("CUARTA — Sin cargos adicionales.",
                "La presente operación no incluye comisiones, gastos administrativos ni cargos adicionales; el "
                        + "costo total del crédito está compuesto únicamente por intereses e IVA sobre intereses."));
        clausulas.add(new ClausulaReporte("QUINTA — Mora.",
                "La mora se produce de pleno derecho por el solo vencimiento de cualquier cuota impaga, sin "
                        + "necesidad de interpelación judicial ni extrajudicial. Desde ese momento y hasta el pago, "
                        + "el capital vencido devenga un interés moratorio a la misma tasa efectiva anual pactada ("
                        + p.get("TEA") + ") y un interés punitorio equivalente al " + p.get("PORCENTAJE_PUNITORIO")
                        + " del interés moratorio, ambos con su IVA. Los pagos se imputan en este orden: interés "
                        + "moratorio, interés punitorio, interés compensatorio y, por último, capital."));
        clausulas.add(new ClausulaReporte("SEXTA — Pago anticipado.",
                "EL DEUDOR puede cancelar total o parcialmente el préstamo en cualquier momento, sin penalidad y "
                        + "sin pagar intereses de períodos futuros (Ley 6624/2020). La cancelación total se liquida "
                        + "por el capital pendiente más los intereses devengados a la fecha de pago. El pago "
                        + "anticipado parcial se aplica al capital, mantiene el monto de la cuota y reduce la "
                        + "cantidad de cuotas."));
        clausulas.add(new ClausulaReporte("SÉPTIMA — Pagaré.",
                "En garantía de las obligaciones de este contrato, EL DEUDOR suscribe un pagaré a la orden de "
                        + "EL ACREEDOR por el total a pagar, con vencimiento en la fecha de la última cuota. "
                        + "Cancelado el préstamo, EL ACREEDOR devuelve el pagaré y entrega la constancia de "
                        + "cancelación."));
        clausulas.add(new ClausulaReporte("OCTAVA — Vencimiento anticipado.",
                "La falta de pago de " + p.get("CUOTAS_ALERTA_GARANTE") + " cuotas vencidas faculta a EL ACREEDOR a "
                        + "declarar vencidos todos los plazos y exigir el saldo total adeudado, con los intereses "
                        + "devengados hasta la fecha, pudiendo ejecutar el pagaré."));
        clausulas.add(new ClausulaReporte("NOVENA — Información crediticia.",
                "EL DEUDOR autoriza expresamente a EL ACREEDOR a consultar y reportar su información crediticia a "
                        + "burós de información (Informconf), conforme a la autorización firmada que obra en su "
                        + "legajo y a la Ley 6534/2020 de protección de datos crediticios."));
        clausulas.add(new ClausulaReporte("DÉCIMA — Domicilio y jurisdicción.",
                "Las partes constituyen domicilio en los indicados en este contrato, donde se tendrán por válidas "
                        + "todas las notificaciones, y se someten a los juzgados y tribunales de la ciudad de "
                        + "Asunción."));
        return clausulas;
    }

    public byte[] generarPdfPagare(Connection conexion, long idPrestamo) throws SQLException {
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        Map<String, Object> parametros = construirParametrosReporte(conexion, prestamo, prestamo.getFormalizadoPor());
        return llenarYExportar("pagare.jasper", parametros, new JREmptyDataSource(1));
    }

    public byte[] generarPdfPlanDePagos(Connection conexion, long idPrestamo) throws SQLException {
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        Map<String, Object> parametros = construirParametrosReporte(conexion, prestamo, prestamo.getFormalizadoPor());
        List<Cuota> cuotas = listarCuotasVigentes(conexion, idPrestamo);
        List<CuotaReporte> filas = new ArrayList<>();
        for (Cuota cuota : cuotas) {
            filas.add(new CuotaReporte(cuota.getNumeroCuota(), FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()),
                    FormateadorNumeros.comoGuaranies(cuota.getCapitalProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getInteresProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getTributosProgramados()),
                    FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getSaldoCapitalPosterior())));
        }
        return llenarYExportar("plan_pagos.jasper", parametros, new JRBeanCollectionDataSource(filas));
    }

    // =====================================================================
    // E9/Bloque 5 (P10, cierre documental): constancia de devolución de pagaré. Mismo patrón que
    // el checklist de contrato/pagaré (documentos_expediente + guardarPdfGenerado), no el patrón
    // D-94 de recibo/factura (una columna de archivo dedicada) -- Joel: "mismo patrón que
    // CONTRATO/PAGARE". Solo aplica a un préstamo ya FINALIZADO.
    // =====================================================================

    /** E9/Bloque 5 (P10): préstamos FINALIZADO con cierre documental pendiente, para la sección
     * propia de la bandeja de /formalizacion. */
    public List<PrestamoCredito> listarFinalizadosConCierrePendiente(Connection conexion) throws SQLException {
        return prestamoDAO.listarFinalizadosConCierrePendiente(conexion);
    }

    /** Genera (si todavía no existe) el número DEV-aaaa-nnnnnn y el PDF sistema-generado; idempotente. */
    public void generarConstanciaDevolucion(long idPrestamo, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                if (prestamo.getEstadoPrestamo() != EstadoPrestamo.FINALIZADO) {
                    throw new NegocioException("El préstamo todavía no está finalizado.");
                }
                if (prestamo.getNumeroConstanciaDevolucion() == null) {
                    int anio = Reloj.hoy().getYear();
                    long secuencia = numeradorDAO.siguienteValor(conexion, "DEVOLUCION", anio);
                    String numero = String.format("DEV-%d-%06d", anio, secuencia);
                    prestamoDAO.actualizarNumeroConstanciaDevolucion(conexion, idPrestamo, numero);
                    prestamo.setNumeroConstanciaDevolucion(numero);
                }
                TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, "DEVOLUCION_PAGARE_GENERADO");
                if (documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo,
                        tipo.getIdTipoDocumento()) == null) {
                    byte[] pdf = generarPdfConstanciaDevolucion(conexion, prestamo, idUsuario);
                    guardarPdfGenerado(conexion, prestamo, tipo, pdf,
                            "constancia_devolucion_" + prestamo.getIdentificadorOperacion() + ".pdf", idUsuario);
                }
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private byte[] generarPdfConstanciaDevolucion(Connection conexion, PrestamoCredito prestamo, long idUsuario)
            throws SQLException {
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        LiquidacionAnticipada cancelacion = liquidacionAnticipadaDAO.obtenerTotalVigentePorPrestamo(conexion,
                prestamo.getIdPrestamo());
        Usuario emisor = servicioUsuarios.buscarPorId(conexion, idUsuario);
        LocalDate fechaDevolucion = Reloj.hoy();

        Map<String, Object> parametros = new HashMap<>();
        // Punto 16b (pedido de Joel): si no hay versión vigente en la fecha, usa la más antigua
        // disponible en vez de rechazar (fechaDevolucion es Reloj.hoy(), así que en la práctica
        // esto casi nunca dispara -- se deja igual por consistencia con el resto de los PDF).
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fechaDevolucion));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fechaDevolucion));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fechaDevolucion));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fechaDevolucion));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fechaDevolucion));
        parametros.put("EMPRESA_CORREO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.correo", fechaDevolucion));
        parametros.put("REPRESENTANTE_NOMBRE",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_nombre", fechaDevolucion));
        parametros.put("REPRESENTANTE_CEDULA",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_cedula", fechaDevolucion));
        parametros.put("NUMERO_CONSTANCIA_DEVOLUCION", prestamo.getNumeroConstanciaDevolucion());
        parametros.put("FECHA_DEVOLUCION", FormateadorFechas.comoDdMmAaaa(fechaDevolucion));
        parametros.put("FECHA_DEVOLUCION_LARGA", FormateadorFechas.comoFechaLarga(fechaDevolucion));
        // Pedido de Joel (12/10/2026, ajustes chicos del Bloque Cancelación): nombre del cliente
        // sin mayúsculas forzadas -- mismo criterio que generarPdfConstanciaReversion/constancia.
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("NUMERO_PAGARE", prestamo.getNumeroPagare());
        parametros.put("MONTO_DESEMBOLSADO", FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()));
        parametros.put("FECHA_DESEMBOLSO",
                prestamo.getFechaDesembolso() == null ? "-" : FormateadorFechas.comoDdMmAaaa(prestamo.getFechaDesembolso()));
        // Puede no haber pasado por "Cancelar todo" (se finalizó con el último pago normal) --
        // en ese caso se usa fecha_finalizacion_financiera y no hay número de constancia CAN-.
        LocalDate fechaCancelacion = cancelacion != null ? cancelacion.getFechaCalculo().toLocalDate()
                : (prestamo.getFechaFinalizacionFinanciera() == null ? null
                        : prestamo.getFechaFinalizacionFinanciera().toLocalDate());
        parametros.put("FECHA_CANCELACION", fechaCancelacion == null ? "-" : FormateadorFechas.comoDdMmAaaa(fechaCancelacion));
        // 13/10/2026 (hallazgo de Joel): sin esto, el párrafo principal decía "...cancelada en su
        // totalidad... (Constancia de cancelación N.º -)" para un préstamo que se FINALIZÓ pagando
        // la última cuota (sin "Cancelar todo", sin CAN) -- mentía, citando una constancia
        // inexistente. El texto se arma en Java (no en el jrxml, para poder probarlo sin base de
        // datos, REGLAS_DESARROLLO.md §7 punto 9) -- ver construirTextoCierreOperacionDevolucion.
        parametros.put("TEXTO_CIERRE_OPERACION", construirTextoCierreOperacionDevolucion(cancelacion != null,
                cancelacion == null || cancelacion.getNumeroConstancia() == null ? "-" : cancelacion.getNumeroConstancia()));
        parametros.put("USUARIO_EMISOR", emisor == null ? "-" : emisor.getNombres() + " " + emisor.getApellidos());
        // B78 (28/10/2026, hallazgo de Joel del 25/10, aprobado el 28/10): la constancia no decía
        // quién entregó físicamente el pagaré. Ahora lleva un tercer bloque de firma "Entregó el
        // pagaré" con el nombre y la C.I. de quien la genera -- mismo patrón que la firma de
        // "quien atendió" de contrato.jrxml/plan_pagos.jrxml.
        parametros.put("USUARIO_EMISOR_CEDULA", emisor == null || emisor.getCedulaIdentidad() == null ? "-"
                : FormateadorTexto.comoCedulaConPuntos(emisor.getCedulaIdentidad()));
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(Reloj.ahora()));

        return llenarYExportar("constancia_devolucion.jasper", parametros, new JREmptyDataSource(1));
    }

    /**
     * 13/10/2026 (pedido de Joel, hallazgo en pantalla): cierre del párrafo principal de la
     * constancia de devolución -- pura (sin Connection), separada para poder probarla sin base
     * de datos (REGLAS_DESARROLLO.md §7 punto 9). Con cancelación total (CAN), cita su número;
     * sin ella (el préstamo se FINALIZÓ pagando la última cuota normal), no menciona ninguna
     * constancia -- antes decía "...cancelada en su totalidad... (Constancia de cancelación
     * N.º -)", citando una constancia inexistente (ver RF29_cierre_documental.md §6e).
     */
    public String construirTextoCierreOperacionDevolucion(boolean tieneCancelacion, String numeroConstanciaCancelacion) {
        return tieneCancelacion
                ? ", por haber sido cancelada en su totalidad la operación (Constancia de cancelación N.º "
                        + numeroConstanciaCancelacion + ")."
                : ", por haber sido pagada en su totalidad la operación.";
    }

    /** El documento sistema-generado vigente (para el enlace de descarga), o null si todavía no se generó. */
    public DocumentoExpediente obtenerConstanciaDevolucionGenerada(Connection conexion, long idPrestamo)
            throws SQLException {
        TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, "DEVOLUCION_PAGARE_GENERADO");
        return documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo, tipo.getIdTipoDocumento());
    }

    /** El escaneo firmado vigente, o null si todavía no se cargó. */
    public DocumentoExpediente obtenerConstanciaDevolucionFirmada(Connection conexion, long idPrestamo)
            throws SQLException {
        TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, "DEVOLUCION_PAGARE_FIRMADO");
        return documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, idPrestamo, tipo.getIdTipoDocumento());
    }

    /** El id_tipo_documento de DEVOLUCION_PAGARE_FIRMADO -- lo necesita el formulario de carga
     * (mismo endpoint genérico /documentos/cargar que usa el checklist de contrato/pagaré). */
    public long obtenerIdTipoDevolucionFirmado(Connection conexion) throws SQLException {
        return tipoDocumentoDAO.obtenerPorCodigo(conexion, "DEVOLUCION_PAGARE_FIRMADO").getIdTipoDocumento();
    }

    /** Completa el cierre documental (P10): exige que el escaneo firmado ya esté cargado. */
    public void completarCierreDocumental(long idPrestamo, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                if ("COMPLETO".equals(prestamo.getEstadoCierreDocumental())) {
                    conexion.commit();
                    return;
                }
                if (obtenerConstanciaDevolucionFirmada(conexion, idPrestamo) == null) {
                    // B85 (28/10/2026, aprobado por Joel): mismo vocabulario que la pantalla
                    // ("la constancia firmada", "marcar el préstamo como cerrado"), en vez de
                    // "el escaneo firmado" y "completar el cierre documental".
                    throw new NegocioException("Cargue la constancia firmada por el cliente "
                            + "antes de marcar el préstamo como cerrado.");
                }
                prestamoDAO.marcarCierreDocumentalCompleto(conexion, idPrestamo, idUsuario);
                servicioAuditoria.registrar(conexion, idUsuario, "COMPLETAR_CIERRE_DOCUMENTAL", "PRESTAMO", idPrestamo,
                        "Cierre documental completo de " + prestamo.getIdentificadorOperacion() + ".");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * D-70: todos los importes, fechas, tasas y letras ya calculados/formateados en Java antes
     * de pasarlos al reporte -- el .jrxml no hace ninguna cuenta, solo muestra (REGLAS_DESARROLLO.md §6).
     */
    private Map<String, Object> construirParametrosReporte(Connection conexion, PrestamoCredito prestamo, long idUsuario)
            throws SQLException {
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        Usuario usuario = servicioUsuarios.buscarPorId(conexion, idUsuario);
        List<Cuota> cuotas = listarCuotasVigentes(conexion, prestamo.getIdPrestamo());

        BigDecimal totalAPagar = BigDecimal.ZERO;
        BigDecimal totalCapital = BigDecimal.ZERO;
        BigDecimal totalInteres = BigDecimal.ZERO;
        BigDecimal totalIva = BigDecimal.ZERO;
        for (Cuota cuota : cuotas) {
            totalAPagar = totalAPagar.add(cuota.getTotalProgramado());
            totalCapital = totalCapital.add(cuota.getCapitalProgramado());
            totalInteres = totalInteres.add(cuota.getInteresProgramado());
            totalIva = totalIva.add(cuota.getTributosProgramados());
        }
        LocalDate ultimaFechaVencimiento = cuotas.isEmpty() ? prestamo.getPrimeraFechaVencimiento()
                : cuotas.get(cuotas.size() - 1).getFechaVencimiento();
        // Corrección de Joel: la fecha EFECTIVA de cobro de la cuota 1 (ya corrida al siguiente
        // día hábil por RN-19 si corresponde), no prestamo.getPrimeraFechaVencimiento() -- esa es
        // la fecha teórica que se eligió al formalizar, antes del corrimiento.
        // C12 (29/10/2026): el cálculo pasó a primeraFechaVencimientoEfectiva(), el único dueño --
        // el comprobante de desembolso lo necesita igual y mostraba la elegida.
        LocalDate primeraFechaVencimientoEfectiva = primeraFechaVencimientoEfectiva(cuotas, prestamo);
        // Corrección de Joel: la cuota 1 CON IVA (no cuotaContractual, que es sin IVA).
        BigDecimal cuota1ConIva = cuotas.isEmpty() ? prestamo.getCuotaContractual() : cuotas.get(0).getTotalProgramado();

        BigDecimal porcentajePunitorio = servicioParametros.obtenerValorDecimal(conexion, "credito.porcentaje_punitorio");
        int cuotasAlertaGarante = servicioParametros.obtenerValorEntero(conexion, "credito.cuotas_alerta_garante");

        // Lote 2, L2-C (14/10/2026, pedido de Joel, autorizado): D-94 -- datos de empresa vigentes
        // a la fecha de formalización, no a la fecha de reimpresión (obtenerValor usa el valor de
        // hoy). Mismo patrón que generarPdfConstanciaDevolucion.
        LocalDate fechaFormalizacion = prestamo.getFechaFormalizacion().toLocalDate();
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fechaFormalizacion));
        parametros.put("EMPRESA_NOMBRE_COMERCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.nombre_comercial", fechaFormalizacion));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fechaFormalizacion));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fechaFormalizacion));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fechaFormalizacion));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fechaFormalizacion));
        parametros.put("EMPRESA_CORREO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.correo", fechaFormalizacion));
        parametros.put("EMPRESA_REGISTRO_BCP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.registro_bcp", fechaFormalizacion));
        parametros.put("EMPRESA_REPRESENTANTE", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal", fechaFormalizacion));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("FECHA_FORMALIZACION", FormateadorFechas.comoDdMmAaaa(prestamo.getFechaFormalizacion().toLocalDate()));
        // B103: la fecha viaja CON su preposición y artículo ("al primer día..." / "a los
        // veintiún días..."), porque la concordancia depende del día y el día lo sabe el conversor.
        // Único consumidor: la cláusula de apertura del contrato (construirClausulasContrato); el
        // contrato.jrxml declara el parámetro pero no lo imprime en ningún textField.
        parametros.put("FECHA_FORMALIZACION_LETRAS",
                ConversorFechasALetras.comoFechaConArticulo(prestamo.getFechaFormalizacion().toLocalDate()));
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        // B.15 (consolidación, pedido de Joel): sin la referencia coloquial ("casa blanca con
        // portón verde") en el documento legal -- esa referencia queda solo en la ficha del
        // cliente (uso operativo).
        // Normalización de personas, grupo Domicilio (16/10/2026): el texto ya no está en
        // cliente.getDomicilio() -- se arma al vuelo con ServicioClientes.obtenerTextoDomicilio
        // (D-94: esto solo corre al generar/regenerar un PDF, nunca sobre uno ya emitido).
        String textoDomicilioCliente = servicioClientes.obtenerTextoDomicilio(conexion, cliente.getIdPersona());
        parametros.put("CLIENTE_DOMICILIO", textoDomicilioCliente == null ? "-"
                : ServicioClientes.domicilioSinReferencia(textoDomicilioCliente));
        parametros.put("CLIENTE_TELEFONO", cliente.getTelefonoWhatsapp() == null ? "-" : cliente.getTelefonoWhatsapp());
        parametros.put("MONTO_APROBADO", FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()));
        // Corrección de Joel: montos en letras completamente en MAYÚSCULAS en los imprimibles legales.
        parametros.put("MONTO_APROBADO_LETRAS", ConversorNumerosALetras.comoLetrasConGuaranies(prestamo.getMontoAprobado()).toUpperCase(new Locale("es")));
        parametros.put("DESTINO_CREDITO", solicitud.getDestinoCredito());
        parametros.put("PLAZO_MESES", prestamo.getPlazoMeses());
        parametros.put("PRIMERA_FECHA_VENCIMIENTO", FormateadorFechas.comoDdMmAaaa(primeraFechaVencimientoEfectiva));
        // C3 (29/10/2026): el día al que se anclan las cuotas SIGUIENTES es el de la fecha ELEGIDA
        // al formalizar, no el de la efectiva -- ver textoVencimientosClausulaSegunda y
        // ServicioMotorFinanciero.generarPlanPagos (fechaPrimerVencimiento.plusMonths(k-1)).
        parametros.put("DIA_VENCIMIENTO_ELEGIDO", prestamo.getPrimeraFechaVencimiento().getDayOfMonth());
        parametros.put("ULTIMA_FECHA_VENCIMIENTO", FormateadorFechas.comoDdMmAaaa(ultimaFechaVencimiento));
        parametros.put("TEA", FormateadorNumeros.comoPorcentaje(prestamo.getTasaTea()));
        parametros.put("TNA", FormateadorNumeros.comoPorcentaje(prestamo.getTasaTna()));
        parametros.put("CFT", prestamo.getCft() == null ? "-" : FormateadorNumeros.comoPorcentaje(prestamo.getCft()));
        parametros.put("CUOTA_1", FormateadorNumeros.comoGuaranies(cuota1ConIva));
        parametros.put("TOTAL_A_PAGAR", FormateadorNumeros.comoGuaranies(totalAPagar));
        parametros.put("TOTAL_A_PAGAR_LETRAS", ConversorNumerosALetras.comoLetrasConGuaranies(totalAPagar).toUpperCase(new Locale("es")));
        parametros.put("TOTAL_CAPITAL", FormateadorNumeros.comoGuaranies(totalCapital));
        parametros.put("TOTAL_INTERES", FormateadorNumeros.comoGuaranies(totalInteres));
        parametros.put("TOTAL_IVA", FormateadorNumeros.comoGuaranies(totalIva));
        parametros.put("PORCENTAJE_PUNITORIO", FormateadorNumeros.comoPorcentaje(porcentajePunitorio));
        parametros.put("CUOTAS_ALERTA_GARANTE", cuotasAlertaGarante);
        parametros.put("USUARIO_EMISOR", usuario == null ? "-" : usuario.getNombres() + " " + usuario.getApellidos());
        // E (reprueba Lote 1, pedido de Joel): firma de quien atendió -- contrato y plan de
        // pagos, además de la firma del representante/el "Recibí copia" del cliente.
        parametros.put("USUARIO_EMISOR_CEDULA", usuario == null || usuario.getCedulaIdentidad() == null ? "-"
                : FormateadorTexto.comoCedulaConPuntos(usuario.getCedulaIdentidad()));
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(prestamo.getFechaFormalizacion()));
        return parametros;
    }

    private byte[] llenarYExportar(String nombreArchivoJasper, Map<String, Object> parametros,
            net.sf.jasperreports.engine.JRDataSource datasource) {
        try (InputStream entrada = getClass().getResourceAsStream("/reportes/" + nombreArchivoJasper)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró \"" + nombreArchivoJasper + "\" en el classpath.");
            }
            parametros.put("LOGO", GeneradorInformesPdf.logoComoBytes());
            JasperPrint impreso = JasperFillManager.fillReport(entrada, parametros, datasource);
            return JasperExportManager.exportReportToPdf(impreso);
        } catch (JRException | IOException error) {
            throw new IllegalStateException("No se pudo generar el PDF (" + nombreArchivoJasper + ").", error);
        }
    }

    /**
     * D-70: genera los PDF de contrato/pagaré/plan de pagos que todavía no existan para este
     * préstamo (CONTRATO_GENERADO/PAGARE_GENERADO/PLAN_PAGOS_GENERADO, origen JASPER). No toca
     * el plan ni el número de operación -- por eso funciona aunque ya haya firmados cargados (a
     * diferencia de regenerarDocumentos). Idempotente: si un tipo ya tiene un VIGENTE, no hace
     * nada para ese tipo -- así sirve tanto para un préstamo recién formalizado como para uno
     * formalizado antes de que existiera el diseño de los reportes (D-68/D-70).
     */
    public void generarPdfsFaltantes(long idPrestamo, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                validarPendienteDesembolso(conexion, prestamo);
                generarSiFalta(conexion, prestamo, "CONTRATO_GENERADO", idUsuario);
                generarSiFalta(conexion, prestamo, "PAGARE_GENERADO", idUsuario);
                generarSiFalta(conexion, prestamo, "PLAN_PAGOS_GENERADO", idUsuario);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private void generarSiFalta(Connection conexion, PrestamoCredito prestamo, String codigoTipo, long idUsuario)
            throws SQLException {
        generarPdfTipo(conexion, prestamo, codigoTipo, idUsuario, false);
    }

    /** Regenerar (Joel): a diferencia de generarSiFalta, siempre reemplaza -- el plan cambió. */
    private void regenerarPdfTipo(Connection conexion, PrestamoCredito prestamo, String codigoTipo, long idUsuario)
            throws SQLException {
        generarPdfTipo(conexion, prestamo, codigoTipo, idUsuario, true);
    }

    private void generarPdfTipo(Connection conexion, PrestamoCredito prestamo, String codigoTipo, long idUsuario,
            boolean forzar) throws SQLException {
        TipoDocumento tipo = tipoDocumentoDAO.obtenerPorCodigo(conexion, codigoTipo);
        boolean yaExiste = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion, prestamo.getIdPrestamo(),
                tipo.getIdTipoDocumento()) != null;
        if (yaExiste && !forzar) {
            return;
        }
        byte[] pdf;
        String nombreArchivo;
        switch (codigoTipo) {
            case "CONTRATO_GENERADO":
                pdf = generarPdfContrato(conexion, prestamo.getIdPrestamo());
                nombreArchivo = "contrato_" + prestamo.getIdentificadorOperacion() + ".pdf";
                break;
            case "PAGARE_GENERADO":
                pdf = generarPdfPagare(conexion, prestamo.getIdPrestamo());
                nombreArchivo = "pagare_" + prestamo.getIdentificadorOperacion() + ".pdf";
                break;
            default:
                pdf = generarPdfPlanDePagos(conexion, prestamo.getIdPrestamo());
                nombreArchivo = "plan_pagos_" + prestamo.getIdentificadorOperacion() + ".pdf";
        }
        guardarPdfGenerado(conexion, prestamo, tipo, pdf, nombreArchivo, idUsuario);
    }

    private void guardarPdfGenerado(Connection conexion, PrestamoCredito prestamo, TipoDocumento tipo,
            byte[] contenidoPdf, String nombreArchivo, long idUsuario) throws SQLException {
        DocumentoExpediente vigenteAnterior = documentoExpedienteDAO.obtenerVigentePorPrestamoYTipo(conexion,
                prestamo.getIdPrestamo(), tipo.getIdTipoDocumento());

        ArchivoBinario archivo = new ArchivoBinario();
        archivo.setNombreArchivo(nombreArchivo);
        archivo.setMimeType("application/pdf");
        archivo.setExtension("pdf");
        archivo.setTamanoBytes(contenidoPdf.length);
        archivo.setHashSha256(ServicioDocumentos.calcularHashSha256(contenidoPdf));
        archivo.setOrigenArchivo(OrigenArchivo.JASPER);
        archivo.setCargadoPor(idUsuario);
        long idArchivo = archivoBinarioDAO.insertar(conexion, archivo, contenidoPdf);

        DocumentoExpediente documento = new DocumentoExpediente();
        documento.setIdArchivo(idArchivo);
        documento.setIdTipoDocumento(tipo.getIdTipoDocumento());
        documento.setIdPrestamo(prestamo.getIdPrestamo());
        documento.setEstadoDocumento(EstadoDocumento.VIGENTE);
        documento.setFechaDocumento(Reloj.hoy());
        if (vigenteAnterior != null) {
            documento.setVersionDocumento(vigenteAnterior.getVersionDocumento() + 1);
            documento.setIdDocumentoReemplazado(vigenteAnterior.getIdDocumento());
        } else {
            documento.setVersionDocumento(1);
        }
        long idDocumento = documentoExpedienteDAO.insertar(conexion, documento);
        if (vigenteAnterior != null) {
            documentoExpedienteDAO.marcarReemplazado(conexion, vigenteAnterior.getIdDocumento());
        }
        servicioAuditoria.registrar(conexion, idUsuario, "GENERAR_PDF_FORMALIZACION", "DOCUMENTO_EXPEDIENTE",
                idDocumento, "Generó \"" + nombreArchivo + "\" para " + prestamo.getIdentificadorOperacion() + ".");
    }

    /** PO-05: Gerente/Administrador puede cerrar un PENDIENTE_DESEMBOLSO como ANULADO_ANTES_DESEMBOLSO, con motivo. */
    public void anularAntesDeDesembolso(long idPrestamo, String motivo, long idUsuario) throws SQLException {
        motivo = NormalizadorTexto.normalizarTextoLibre(motivo);
        validarMotivoAnulacion(motivo);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamo == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                if (prestamo.getEstadoPrestamo() != EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
                    throw new NegocioException("Solo se puede anular un préstamo pendiente de desembolso.");
                }
                // RF14 (pedido de Joel, 11/10/2026): UPDATE condicionado a PENDIENTE_DESEMBOLSO --
                // si otra transacción ya confirmó el desembolso en la angosta ventana entre el
                // chequeo de arriba y este UPDATE (carrera de dos pestañas), no se actualiza
                // nada; se arma el mensaje con quién y cuándo y se corta acá -- el catch de abajo
                // hace rollback de todo (ni la auditoría de abajo queda).
                if (!prestamoDAO.marcarAnuladoAntesDesembolso(conexion, idPrestamo, idUsuario, motivo)) {
                    throw construirErrorPrestamoYaDesembolsado(conexion, idPrestamo);
                }
                servicioAuditoria.registrar(conexion, idUsuario, "ANULAR_PRESTAMO_PRE_DESEMBOLSO", "PRESTAMO",
                        idPrestamo, "Anuló " + prestamo.getIdentificadorOperacion() + " antes del desembolso. "
                                + "Motivo: " + motivo);
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }
}
