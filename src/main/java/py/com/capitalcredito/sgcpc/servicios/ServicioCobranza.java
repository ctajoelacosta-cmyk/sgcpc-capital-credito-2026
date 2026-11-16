package py.com.capitalcredito.sgcpc.servicios;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.ArchivoBinarioDAO;
import py.com.capitalcredito.sgcpc.dao.ArchivoComprobanteDAO;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.FacturaDAO;
import py.com.capitalcredito.sgcpc.dao.FeriadoDAO;
import py.com.capitalcredito.sgcpc.dao.GestionCobranzaDAO;
import py.com.capitalcredito.sgcpc.dao.LiquidacionAnticipadaDAO;
import py.com.capitalcredito.sgcpc.dao.MovimientoCajaDAO;
import py.com.capitalcredito.sgcpc.dao.NumeradorDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.ParametroDAO;
import py.com.capitalcredito.sgcpc.dao.PlanPagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.ReversionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.SesionCajaDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dto.CoincidenciaBusquedaCobranza;
import py.com.capitalcredito.sgcpc.dto.DetalleAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible;
import py.com.capitalcredito.sgcpc.dto.FilaFacturaReporte;
import py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaReciboCuota;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;
import py.com.capitalcredito.sgcpc.dto.ListaTrabajoCobranza;
import py.com.capitalcredito.sgcpc.dto.LineaFactura;
import py.com.capitalcredito.sgcpc.dto.ResultadoAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoBusquedaCobranza;
import py.com.capitalcredito.sgcpc.dto.ResultadoRedondeoEfectivo;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.dto.TotalesLineasFactura;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoFactura;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoReversion;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoGestionCobranza;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoMovimientoCaja;
import py.com.capitalcredito.sgcpc.enumeraciones.OrigenArchivo;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;
import py.com.capitalcredito.sgcpc.modelos.ArchivoBinario;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.GestionCobranza;
import py.com.capitalcredito.sgcpc.modelos.LiquidacionAnticipada;
import py.com.capitalcredito.sgcpc.modelos.MovimientoCaja;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.ParametroSistema;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PlanPago;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.modelos.ReversionPago;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;

/**
 * P09, cobranza (Doc. 00 §12: cobro, parcial con promesa, cancelación total, anticipo de
 * capital, factura y recibo). Bloque 1 de E7: el "puente" entre el préstamo persistido
 * (PrestamoCredito + Cuota de la base) y el DTO que usa ServicioMotorFinanciero (Prestamo +
 * Cuota en memoria). Bloque 2: cobro normal y parcial (PO-08/PO-09). Cancelación total y
 * anticipo de capital (PO-10) se agregan en el bloque 3.
 */
public class ServicioCobranza {

    /**
     * C39 (hallazgo de Joel del 27/10/2026 en la prueba del adelanto, decidido el 29/10): la fecha
     * de la próxima cuota del plan nuevo que se le MUESTRA al cliente -- la del día en que de
     * verdad se cobra ({@code getFechaVencimiento()}, ya corrida al siguiente día hábil), nunca la
     * teórica.
     *
     * <p>Antes, los tres lugares que la muestran leían {@code getFechaVencimientoTeorica()}, que es
     * el <b>ancla</b> interna del plan (el día elegido al formalizar, de donde cuelga cada
     * vencimiento antes del corrimiento, D-86/D-87). El resultado era que el recibo que el cliente
     * se lleva decía "su próxima cuota: 21/11/2026" (sábado) mientras el estado de cuenta y el plan
     * del mismo préstamo decían 23/11 (lunes) -- que es cuando el sistema la cobra. El motor nunca
     * estuvo mal: guarda las dos fechas, y esto era solo presentación.</p>
     *
     * <p><b>Un solo dueño</b> para los tres: el recibo del adelanto
     * ({@code ANTICIPO_PROXIMA_CUOTA_FECHA}), la línea "Su próxima cuota será el…" de
     * {@code cobranza/ficha.jsp} y la vista previa en vivo
     * ({@code fragmento_vista_previa_anticipo.jsp}). Mismo criterio que C12 (29/10/2026): los
     * documentos muestran la fecha de cobro, no el dato interno del cálculo. Estática y pura, para
     * que las dos JSP la llamen igual que el servicio y para poder probarla sin base.</p>
     */
    public static String fechaProximaCuotaNuevoPlan(Cuota primeraCuotaNuevoPlan) {
        return FormateadorFechas.comoDdMmAaaa(primeraCuotaNuevoPlan.getFechaVencimiento());
    }

    /**
     * B92 (hallazgo de Joel del 29/10/2026, decidido el mismo día): cómo se nombra en pantalla la
     * opción "Por cantidad de cuotas" del adelanto a capital.
     *
     * <p>Antes decía *"Paga 1 cuota: 228.050 Gs."*, y el verbo invitaba a comparar ese monto con
     * la cuota del plan (289.470), que es otra cosa: el monto de acá es el **mínimo que saca esa
     * cantidad de cuotas del plan**, y cuesta menos porque el adelanto va íntegro a capital y no
     * paga intereses futuros (RN-11, Ley 6624/2020) y porque la cuota que desaparece es la última,
     * no la primera. El texto nuevo lo dice en cuatro palabras.</p>
     *
     * <p><b>Un solo dueño para los tres lugares que lo muestran</b>: el primer render de
     * {@code cobranza/ficha.jsp}, la tarjeta en vivo y el cartel de confirmación. Los dos últimos
     * son JavaScript, así que no pueden llamar a este método: la JSP les deja la frase ya armada
     * en un {@code data-} del propio elemento (con {@code {n}} donde va la cantidad) y
     * {@code cobranza-ficha.js} solo reemplaza el número. Antes el texto estaba escrito tres
     * veces, una en la JSP y dos en el JS.</p>
     *
     * <p>Devuelve la frase <b>sin</b> el monto ni los dos puntos: el llamador agrega
     * {@code ": " + monto} donde corresponde (en el cartel de confirmación va sola). Pura.</p>
     *
     * @param cantidad cuántas cuotas salen del plan, o {@code "{n}"} para la plantilla del JS
     * @param plural si la frase va en plural (el llamador lo sabe; con la plantilla, lo decide el JS)
     */
    public static String etiquetaAdelantoCuotas(String cantidad, boolean plural) {
        return "Adelanta " + cantidad + (plural ? " cuotas" : " cuota") + " (sin intereses futuros)";
    }

    /** B92: la misma frase para una cantidad concreta, que es como la usa la JSP. Pura. */
    public static String etiquetaAdelantoCuotas(int cantidad) {
        return etiquetaAdelantoCuotas(String.valueOf(cantidad), cantidad != 1);
    }

    /**
     * B79 (30/10/2026, decisión de Joel): qué dice la línea grande del "Resumen del cobro" cuando
     * ese cobro <b>no se puede hacer</b> — el servidor rechazó el monto del adelanto, lo recibido
     * no alcanza para la cantidad de cuotas o para cancelar todo, o el efectivo no viene en
     * múltiplos (D-146).
     *
     * <p>Antes la línea seguía mostrando el monto tecleado (<i>"Adelantar a capital: 3.500.000
     * Gs."</i>) mientras el renglón rojo de abajo decía que ese monto no se podía cobrar y
     * "Cobrar" estaba en gris: el número más grande de la tarjeta afirmaba un cobro imposible.
     * La regla es que <b>la línea grande dice el monto que se va a cobrar; si no se va a cobrar
     * nada, no dice ningún monto</b>, y el motivo lo sigue dando el renglón rojo (único dueño del
     * motivo, sin cambios).</p>
     *
     * <p>Es el mismo guión que la JSP ya escribía en el primer render cuando el préstamo no admite
     * cobro; acá existe como constante para que {@code cobranza-ficha.js} escriba exactamente ese
     * carácter y no uno parecido.</p>
     */
    public static final String LINEA_RESUMEN_SIN_MONTO = "—";

    /**
     * B81 (30/10/2026, decisión de Joel): la línea grande del "Resumen del cobro" en un
     * <b>pago parcial</b>.
     *
     * <p>Antes seguía diciendo <i>"Pagar lo vencido: 180.696 Gs."</i> (el total exigible de la
     * opción) aunque la cajera estuviera cobrando 100.000: el cobro salía bien, pero el número más
     * grande de la tarjeta no era el que se iba a cobrar — ese estaba dos renglones más abajo, en
     * "Vuelto" y más chico. Ahora la línea grande dice lo que se cobra y
     * {@link #detalleCobroParcial(String, String)} explica, debajo, de cuánto era lo exigible y
     * cuánto queda pendiente.</p>
     *
     * <p><b>Un solo dueño</b>, mismo patrón que B92: esta frase solo la muestra
     * {@code cobranza-ficha.js} (el primer render de la ficha nunca está en pago parcial, porque
     * "Recibido" arranca vacío), así que la JSP le deja la plantilla con {@code {monto}} en un
     * {@code data-} del propio elemento y el JS <b>solo reemplaza el número</b>. Pura.</p>
     *
     * @param monto el monto ya formateado como lo muestra el sistema ("100.000 Gs."), o
     *              {@code "{monto}"} para la plantilla del JS
     */
    public static String lineaCobroParcial(String monto) {
        return "Cobra " + monto + " (pago parcial)";
    }

    /**
     * B81: el renglón chico debajo de {@link #lineaCobroParcial(String)} — de cuánto era lo
     * exigible y cuánto queda pendiente. Los dos montos ya los tenía la pantalla: lo exigible es
     * el mismo total que usa {@code confirmarCobro} para decidir si un cobro es parcial (RN-20) y
     * el pendiente es la resta que ya se mostraba en "Vuelto". Pura.
     *
     * @param exigible  monto exigible ya formateado, o {@code "{exigible}"} para la plantilla
     * @param pendiente lo que queda pendiente ya formateado, o {@code "{pendiente}"}
     */
    public static String detalleCobroParcial(String exigible, String pendiente) {
        return "de " + exigible + " exigibles — queda pendiente " + pendiente;
    }

    /**
     * B98 (30/10/2026, hallazgo y decisión de Joel): en qué situación está el cobro de hoy. Es la
     * distinción que a la pantalla le faltaba: <b>"¿hay algo vencido?"</b> no es lo mismo que
     * <b>"¿hay algo cobrable hoy?"</b>.
     *
     * <p>Con una cuota que <b>vence hoy</b> no hay nada vencido —{@code calcularDeudaExigible} usa
     * {@code isBefore} y la mora es 0 ({@code dias = 0 <= dias_gracia})— pero sí hay algo que
     * cobrar: la cuota del día, que es el cobro contractual de siempre (D-144). La ficha preguntaba
     * solo lo segundo y por eso decía "vencido" cuatro veces en la misma pantalla que mostraba
     * "Sin cuotas vencidas": el rótulo de la opción, la nota, la línea grande y el cartel.</p>
     */
    public enum SituacionCobroHoy {
        /** Hay al menos una cuota con vencimiento anterior a hoy. */
        HAY_VENCIDO,
        /** Nada vencido, pero hay una cuota que vence HOY (cobrable, sin mora). */
        SOLO_CUOTA_DE_HOY,
        /** Nada que cobrar hoy: recién acá tiene sentido ofrecer el adelanto. */
        NADA_PARA_COBRAR
    }

    /**
     * B98: la situación sale de los dos datos que la ficha ya tenía por separado --
     * {@code hayAlgoParaCobrar} ({@code montoCobrableNormal > 0}, que incluye la cuota de hoy por
     * D-144) y {@code hayVencido} ({@code exigible > 0}). Pura.
     */
    public static SituacionCobroHoy situacionCobroHoy(boolean hayAlgoParaCobrar, boolean hayVencido) {
        if (!hayAlgoParaCobrar) {
            return SituacionCobroHoy.NADA_PARA_COBRAR;
        }
        return hayVencido ? SituacionCobroHoy.HAY_VENCIDO : SituacionCobroHoy.SOLO_CUOTA_DE_HOY;
    }

    /**
     * B102 (01/11/2026, decisión de Joel): los tres conceptos gravados que puede llevar una
     * factura (RN-25: el capital nunca entra). Las etiquetas viven acá una sola vez — antes cada
     * una estaba escrita en los <b>tres</b> armadores de líneas (el de emisión, el de impresión de
     * una cuota y el de impresión de un anticipo).
     */
    public enum ConceptoGravado {
        INTERES("Interés compensatorio"),
        MORATORIO("Interés moratorio"),
        PUNITORIO("Interés punitorio");

        private final String etiqueta;

        ConceptoGravado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getEtiqueta() {
            return etiqueta;
        }
    }

    /**
     * B102: el concepto de una línea de factura que corresponde a una <b>cuota</b>. Es
     * <b>exactamente el texto que hoy se imprime</b> — se eligió ese y no el de emisión para que
     * ninguna factura ya emitida cambie al reimprimirse (sus renglones se derivan de
     * {@code aplicaciones_pago}, D-93/D-94). Pura.
     */
    public static String conceptoFacturaDeCuota(ConceptoGravado concepto, int numeroCuota, int totalCuotas) {
        return concepto.getEtiqueta() + " cuota " + etiquetaNumeroCuota(numeroCuota, totalCuotas);
    }

    /**
     * B102: el concepto de una línea de factura de un <b>anticipo de capital</b> (D-145 E-2: un
     * anticipo sí puede llevar el interés del período en curso). Mismo texto que hoy. Pura.
     *
     * @param fechaFormateada la fecha ya formateada dd/mm/aaaa
     */
    public static String conceptoFacturaDeAnticipo(ConceptoGravado concepto, String fechaFormateada) {
        return concepto.getEtiqueta() + " hasta el " + fechaFormateada + " (anticipo de capital)";
    }

    /** B98: "N/M" -- cómo se nombra una cuota dentro de su plan, en toda la pantalla. Pura. */
    public static String etiquetaNumeroCuota(int numeroCuota, int totalCuotas) {
        return numeroCuota + "/" + totalCuotas;
    }

    /**
     * B98: el rótulo de la primera opción de pago y, con el monto detrás, la línea grande del
     * "Resumen del cobro". Antes era un ternario en la JSP que solo conocía dos casos
     * ("Pagar lo vencido" / "Por cantidad de cuotas"), así que con la cuota de hoy decía
     * "Pagar lo vencido" sin que hubiera nada vencido. Pura.
     */
    public static String etiquetaOpcionCobro(SituacionCobroHoy situacion) {
        switch (situacion) {
            case HAY_VENCIDO:
                return "Pagar lo vencido";
            case SOLO_CUOTA_DE_HOY:
                return "Pagar la cuota de hoy";
            default:
                return "Por cantidad de cuotas";
        }
    }

    /**
     * B106 (05/11/2026): el rótulo de la <b>tercera</b> opción de pago, la cancelación total
     * (RF26/RF28). Es el nombre que la cajera lee en el botón, en la línea grande del "Resumen del
     * cobro", en el título del cartel de confirmación y en los dos mensajes del servidor que mandan
     * a esa opción.
     *
     * <p>Estaba escrito <b>seis</b> veces: el botón y la nota de {@code cobranza/ficha.jsp}, dos
     * puntos de {@code cobranza-ficha.js} (la línea grande y el título del cartel) y dos mensajes de
     * {@code ControladorCobranzaServlet}. Los dos del JavaScript eran los que el navegador
     * <b>redactaba por su cuenta</b>, así que una corrección de redacción acá no los alcanzaba —
     * mismo defecto que B98 ya había corregido para la primera opción. Ahora los seis salen de esta
     * constante: la JSP la imprime y además la deja en un {@code data-} del propio elemento, y el JS
     * solo la lee (regla de B92: el JavaScript escribe valores, nunca frases).</p>
     *
     * <p>No renombra nada: es el mismo texto que ya está en pantalla (rótulo aprobado por Joel el
     * 27/10/2026, reemplazó "Cancelar todo", que se confundía con "cancelar la operación" —
     * familia B3).</p>
     */
    public static final String OPCION_TODO_EL_PRESTAMO = "Todo el préstamo";

    /**
     * B201 (15/11/2026): el valor del parámetro {@code origen} con el que el menú entra a
     * "Cancelación anticipada" (D3/RF38).
     *
     * <p>Estaba escrito como literal en <b>cuatro</b> lugares —el aviso de {@code busqueda.jsp}, el
     * enlace de cada resultado, el aviso de la ficha y su "Volver"—, y cada copia es una
     * oportunidad de que una deje de coincidir, que es exactamente lo que le pasó al rótulo del
     * botón (B106 → B198). Un dato, un dueño.</p>
     */
    public static final String ORIGEN_CANCELACION_ANTICIPADA = "cancelacion-anticipada";

    /** B201: si este pedido viene del menú "Cancelación anticipada". Pura; null y cualquier otro
     * valor son "no" (la pantalla entonces queda exactamente como la de Cobranza). */
    public static boolean esOrigenCancelacionAnticipada(String origen) {
        return ORIGEN_CANCELACION_ANTICIPADA.equals(origen);
    }

    /**
     * B199 (15/11/2026): <b>de quién</b> es la cancelación total que se está confirmando.
     *
     * <p>El cartel de esta operación —la más grande de la pantalla— decía solo el nombre de la
     * opción y los montos: ni el cliente ni el N.º de operación. Es lo mismo que Joel pidió para
     * anular un préstamo (B178), la devolución del pagaré (B179), la diferencia de arqueo (B188) y
     * la reversión (B192); acá faltaba.</p>
     *
     * <p>La frase la arma el servidor y viaja en un {@code data-} para que
     * {@code cobranza-ficha.js} solo la muestre (regla de B92: el JavaScript escribe valores,
     * nunca frases). La cédula llega <b>ya formateada</b> por su dueño
     * ({@code FormateadorTexto.comoCedulaConPuntos}): en la base se guarda normalizada. Pura.</p>
     */
    public static String detalleQuienCancelacionTotal(String identificadorOperacion, String nombreCliente,
            String cedulaConPuntos) {
        return "Operación " + (identificadorOperacion == null ? "-" : identificadorOperacion)
                + " — " + (nombreCliente == null ? "-" : nombreCliente)
                + ", C.I. " + (cedulaConPuntos == null ? "-" : cedulaConPuntos) + ".";
    }

    /**
     * B200 (15/11/2026): el aviso de que la cancelación total <b>emite una constancia numerada</b>.
     *
     * <p>Hasta ahora la constancia aparecía recién después, como botón <i>"Constancia N.º …"</i> en
     * el historial. El paso 1 de la devolución del pagaré sí lo avisa antes de emitir (B112), y
     * este es el mismo caso: un documento numerado que nace al confirmar.</p>
     *
     * <p><b>No promete el número</b>, y eso es a propósito (B4/B43 + B112): el {@code CAN-AAAA-NNNNNN}
     * lo asigna {@code confirmarCancelacionTotal} con {@code numeradorDAO.siguienteValor} <b>dentro
     * de la transacción</b>, al confirmar, así que antes no existe. Dice que la constancia recibe su
     * número, no cuál.</p>
     */
    public static final String AVISO_CONSTANCIA_CANCELACION =
            "Se emite la constancia de cancelación en PDF y recibe su número. "
                    + "Queda para reimprimir desde el historial de cobros.";

    /**
     * B203 (15/11/2026): un renglón del desglose de la cancelación total — el rótulo y su importe.
     *
     * <p>Existe para que el desglose tenga <b>un solo dueño</b> y la vista solo dibuje: la JSP no
     * suma ni arma importes (REGLAS_DESARROLLO.md §5), y el orden y los rótulos no quedan
     * repartidos entre el servicio y el HTML.</p>
     */
    public static final class RenglonDesgloseCancelacion {
        private final String etiqueta;
        private final BigDecimal importe;

        RenglonDesgloseCancelacion(String etiqueta, BigDecimal importe) {
            this.etiqueta = etiqueta;
            this.importe = importe;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public BigDecimal getImporte() {
            return importe;
        }
    }

    /** B203: el rótulo del renglón final del desglose, el único que no es un sumando. */
    public static final String DESGLOSE_TOTAL_CANCELACION = "Total a cancelar hoy";

    /**
     * B203: la frase que cierra el desglose, y que es el motivo de que el desglose exista — lo que
     * la Ley 6624/2020 obliga a no cobrar hacia adelante (RF26 §6).
     */
    public static final String DESGLOSE_SIN_INTERES_FUTURO =
            "No se cobra ningún interés de las cuotas que faltan (Ley 6624/2020).";

    /**
     * B203 (15/11/2026): el desglose de la cancelación total, para el {@code <details>} de la ficha
     * — el mismo que el adelanto tiene desde RF27 §2a y que a la cancelación le faltaba, cuando es
     * justo acá donde el cliente pregunta qué le están cobrando.
     *
     * <p><b>No calcula nada nuevo</b>: reparte en renglones las cifras que
     * {@code calcularPreviewCancelacionTotal} ya devolvió (es el mismo {@code ResultadoLiquidacion}
     * que alimenta {@code data-monto-cancelar-todo}). Pura: no abre conexión ni toca la base.</p>
     *
     * <p><b>Por qué hace falta el {@code exigible} además de la liquidación</b>, y es el detalle que
     * un desglose "obvio" habría errado: en {@code calcularCancelacionTotal} el monto total sale de
     * {@code exigible.getTotalGeneral() + capital no vencido + interés devengado + su IVA}, y
     * {@code getInteresDevengado()} <b>solo</b> trae el interés del período en curso y el de las
     * cuotas con fecha teórica pasada (D-88) — el interés de las cuotas <b>vencidas</b> viaja dentro
     * del total del exigible. Sumar los getters de la liquidación a secas deja ese interés afuera
     * (medido el 15/11/2026 en {@code CC-2026-00035}: faltarían 12.190 de interés más 1.219 de IVA)
     * y el desglose no daría el total. Los dos vienen del <b>mismo</b> {@code hoy} y los mismos
     * {@code credito.dias_gracia_mora}, y el criterio de "vencida" es idéntico en los dos métodos
     * del motor, así que la suma cierra exacta — y eso es lo que comprueba su JUnit, que es el
     * verdadero valor de este método: un desglose que no suma el total miente.</p>
     *
     * <p>Los renglones en cero no se muestran, salvo el capital (es el sumando principal: en una
     * cancelación siempre hay algo de capital, y un desglose que arranca sin él se lee mal). La
     * mora y los punitorios <b>se nombran</b> cuando los hay, en vez de quedar escondidos en el
     * total (RF26 §6; la prueba del 27/10 con {@code CC-2026-00001} los trajo).</p>
     *
     * @return los sumandos en orden, y al final el renglón del total ({@link #DESGLOSE_TOTAL_CANCELACION}).
     *         Lista vacía si no hay liquidación que mostrar (préstamo ya finalizado, D-146 E-4).
     */
    public static List<RenglonDesgloseCancelacion> desgloseCancelacionTotal(ResultadoLiquidacion liquidacion,
            ResultadoDeudaExigible exigible) {
        List<RenglonDesgloseCancelacion> renglones = new ArrayList<>();
        if (liquidacion == null) {
            return renglones;
        }
        BigDecimal interesVencidas = BigDecimal.ZERO;
        BigDecimal ivaInteresVencidas = BigDecimal.ZERO;
        if (exigible != null && exigible.getDetalles() != null) {
            for (DetalleCuotaExigible detalle : exigible.getDetalles()) {
                interesVencidas = interesVencidas.add(noNulo(detalle.getInteresPendiente()));
                ivaInteresVencidas = ivaInteresVencidas.add(noNulo(detalle.getIvaInteresPendiente()));
            }
        }
        renglones.add(new RenglonDesgloseCancelacion("Capital que debe hoy",
                noNulo(liquidacion.getSaldoCapitalFecha())));
        agregarSiHay(renglones, "Interés devengado hasta hoy",
                interesVencidas.add(noNulo(liquidacion.getInteresDevengado())));
        agregarSiHay(renglones, "IVA del interés",
                ivaInteresVencidas.add(noNulo(liquidacion.getIvaInteresDevengado())));
        agregarSiHay(renglones, "Mora", noNulo(liquidacion.getMoratorioPendiente()));
        agregarSiHay(renglones, "IVA de la mora", noNulo(liquidacion.getIvaMoratorioPendiente()));
        agregarSiHay(renglones, "Punitorios", noNulo(liquidacion.getPunitorioPendiente()));
        agregarSiHay(renglones, "IVA de los punitorios", noNulo(liquidacion.getIvaPunitorioPendiente()));
        renglones.add(new RenglonDesgloseCancelacion(DESGLOSE_TOTAL_CANCELACION,
                noNulo(liquidacion.getMontoTotalLiquidacion())));
        return renglones;
    }

    /** B203: un sumando se nombra solo si tiene algo; en cero sería ruido en el desglose. */
    private static void agregarSiHay(List<RenglonDesgloseCancelacion> renglones, String etiqueta,
            BigDecimal importe) {
        if (importe.compareTo(BigDecimal.ZERO) > 0) {
            renglones.add(new RenglonDesgloseCancelacion(etiqueta, importe));
        }
    }

    /** B203: ningún renglón del desglose puede quedar en null — un desglose incompleto miente. */
    private static BigDecimal noNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /**
     * B204 (15/11/2026, ítem 5 del informe de UX de Cancelación): la frase del ahorro, último
     * renglón del desglose de B203.
     *
     * <p>El adelanto decía <i>"El cliente se ahorra X de intereses por pagar por adelantado"</i>
     * desde RF27 y la cancelación —que por la Ley 6624/2020 no cobra <b>ningún</b> interés
     * futuro, o sea donde más se ahorra— no mostraba nada. Venía anotado en {@code RF26 §10} como
     * "opcional" desde el 12/10/2026; el 15/11 Joel decidió que entra.</p>
     *
     * <p>El número lo calcula el <b>motor</b>, dentro de {@code calcularCancelacionTotal}, y viaja
     * en {@code ResultadoLiquidacion.getInteresFuturoNoCobrado()}: acá solo se redacta. Es la misma
     * separación de B92 llevada al servidor — la vista no suma ni formatea reglas, y el único lugar
     * donde se decide <i>cómo se dice</i> es este.</p>
     *
     * <p><b>Sin ahorro no hay frase</b> (cadena vacía, y la vista no dibuja el renglón): pasa
     * cuando todas las cuotas que quedan ya están vencidas, y ahí "se ahorra 0" sería peor que no
     * decir nada. Devuelve vacío también con null —el caso de una liquidación PARCIAL, que tiene
     * otra cuenta— para que nunca se escriba un ahorro que no corresponde.</p>
     *
     * <p>Ojo con el punto: {@code comoGuaranies} ya termina en "Gs.", así que el monto va en medio
     * de la oración y el punto final es el de la frase, no un segundo punto pegado al monto (la
     * lección de B178/B188/B192 y las cuatro redes de {@code TextosDePantallaTest}).</p>
     */
    public static String fraseAhorroCancelacionTotal(BigDecimal interesFuturoNoCobrado) {
        if (interesFuturoNoCobrado == null || interesFuturoNoCobrado.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return "El cliente se ahorra " + FormateadorNumeros.comoGuaranies(interesFuturoNoCobrado)
                + " de intereses por cancelar hoy.";
    }

    /**
     * B106 (05/11/2026): el nombre de la operación de adelanto de capital (RF27), que es lo que
     * dice la línea grande del "Resumen del cobro" y el título del cartel cuando la cajera elige la
     * opción "Por monto". El botón de la opción se llama "Por monto" —eso es la <i>forma</i> de
     * elegir el monto—; esta frase nombra la <i>operación</i> que se va a hacer con él, y es el
     * mismo nombre que lleva en el recibo.
     *
     * <p>Era el otro texto que {@code cobranza-ficha.js} redactaba a mano, y el único de los dos que
     * <b>no existía en ninguna parte del servidor</b>: vivía solo en el navegador.</p>
     */
    public static final String ACCION_ADELANTAR_A_CAPITAL = "Adelantar a capital";

    /**
     * B106 (05/11/2026, ampliación aprobada por Joel): el rótulo de la <b>segunda</b> opción de
     * pago — la cajera escribe en "Recibido" lo que trae el cliente y el servidor muestra qué cubre.
     *
     * <p>Estaba escrito en el botón de {@code cobranza/ficha.jsp} y otra vez en el mensaje del
     * servidor que manda a esa opción cuando lo recibido no alcanza para la cantidad de cuotas
     * elegida ({@code ControladorCobranzaServlet}). Los dos salen ahora de acá.</p>
     */
    public static final String OPCION_POR_MONTO = "Por monto";

    /**
     * B106 (05/11/2026, ampliación aprobada por Joel): los <b>tres</b> botones del cartel de
     * confirmación del cobro, uno por acción. Eran los últimos textos de usuario que
     * {@code cobranza-ficha.js} redactaba por su cuenta — un ternario con las tres frases escritas
     * a mano, al lado del {@code etiquetaAccion} que ya venía del servidor.
     *
     * <p>Los otros dos botones del mismo cartel ya tenían dueño acá desde B96/B98
     * ({@link #botonCobroParcial(String)} y {@link #BOTON_COBRAR_CUOTA_DE_HOY}), que son los que
     * <b>pisan</b> a estos tres cuando el cobro es parcial o es la cuota que vence hoy. Con estos
     * tres, el JavaScript ya no redacta ninguna frase del cartel: la elige.</p>
     *
     * <p>No renombran nada: son las mismas palabras que ya están en pantalla. Cada una nombra la
     * operación real y no la opción elegida (regla de B96).</p>
     */
    public static final String BOTON_COBRAR_LO_VENCIDO = "Sí, cobrar lo vencido";

    /** B106: el botón del cartel en la cancelación total (RF26/RF28). */
    public static final String BOTON_COBRAR_TODO_EL_PRESTAMO = "Sí, cobrar todo el préstamo";

    /** B106: el botón del cartel en el adelanto de capital (RF27). */
    public static final String BOTON_ADELANTAR_A_CAPITAL = "Sí, adelantar a capital";

    /**
     * B98: la nota que explica por qué el adelanto todavía no se ofrece. Con la cuota de hoy
     * nombra la cuota en vez de hablar de "lo vencido", y dice la condición real: el adelanto
     * aparece cuando no hay <b>ninguna cuota para cobrar</b> (D-144; RN-11/RF27 §2b: el adelanto es
     * para capital que todavía no es exigible). Pura.
     *
     * @param etiquetaCuotaDeHoy "N/M" de la cuota que vence hoy, o {@code null} si no hay
     */
    public static String notaAntesDeAdelantar(SituacionCobroHoy situacion, String etiquetaCuotaDeHoy) {
        if (situacion == SituacionCobroHoy.HAY_VENCIDO) {
            return "Primero hay que cobrar lo vencido. Adelantar por cantidad de cuotas o por monto "
                    + "queda disponible cuando el préstamo no tenga nada vencido.";
        }
        if (situacion == SituacionCobroHoy.SOLO_CUOTA_DE_HOY) {
            return "Hoy vence la cuota " + etiquetaCuotaDeHoy + ". Adelantar por cantidad de cuotas o "
                    + "por monto queda disponible cuando no haya ninguna cuota para cobrar.";
        }
        return "";
    }

    /**
     * B98: la línea grande cuando la cajera escribe <b>menos</b> que la cuota de hoy. Mismo
     * criterio que B79 (el número grande es el que se va a cobrar) y misma forma que B81, pero
     * <b>no dice "pago parcial"</b> a propósito: para el servidor esto no es un pago parcial de
     * RN-20 (lo exigible es 0, no se pide promesa de pago), aunque la cuota quede en PARCIAL.
     * Pura.
     *
     * @param monto el monto ya formateado, o {@code "{monto}"} para la plantilla del JS
     */
    public static String lineaCobroACuenta(String monto) {
        return "Cobra " + monto + " (a cuenta de la cuota de hoy)";
    }

    /**
     * B98: el renglón chico debajo de {@link #lineaCobroACuenta(String)}. "Sin mora hasta mañana"
     * es el dato útil que ya daba el cartel de D-146 (P-17) y que la tarjeta no decía. Pura.
     */
    public static String detalleCobroACuenta(String totalCuota, String pendiente) {
        return "de " + totalCuota + " \u2014 quedan " + pendiente + ", sin mora hasta mañana";
    }

    /**
     * B98: la línea del cartel de confirmación cuando se cobra la cuota de hoy <b>completa</b>.
     * Nombra la cuota, que es lo que la cajera le dice al cliente. Pura.
     *
     * @param etiquetaCuota "N/M", o {@code "{cuota}"} para la plantilla del JS
     * @param monto         monto ya formateado, o {@code "{monto}"}
     */
    public static String lineaCartelCuotaDeHoy(String etiquetaCuota, String monto) {
        return "Cobra la cuota " + etiquetaCuota + " de hoy: " + monto;
    }

    /** B98: el botón del cartel cuando se cobra la cuota de hoy completa. */
    public static final String BOTON_COBRAR_CUOTA_DE_HOY = "S\u00ed, cobrar la cuota de hoy";

    /**
     * B98: los dos textos de D-146 (P-17) -- lo recibido cubre todo lo vencido pero no la cuota
     * que vence hoy. Venían escritos dentro de {@code cobranza-ficha.js}; se mudan acá sin cambiar
     * una palabra, para que el JS no redacte (regla de B92) y para que un test pueda fijarlos.
     * Puras.
     */
    public static String textoSoloLoVencidoConExcedente(String excedente, String pendiente) {
        return "Se cobra lo vencido. Lo que sobra (" + excedente + ") se aplica a la cuota de hoy; "
                + "quedan " + pendiente + " pendientes, sin mora hasta mañana.";
    }

    /** B98: la variante sin excedente del texto de D-146 (P-17), tal cual estaba. Pura. */
    public static String textoSoloLoVencidoSinExcedente(String pendienteCuotaHoy) {
        return "Se cobra lo vencido. La cuota de hoy (" + pendienteCuotaHoy + ") queda pendiente, "
                + "sin mora hasta mañana.";
    }

    /**
     * B95 (30/10/2026, decisión de Joel): qué dice la pantalla cuando el cobro es parcial y la
     * cajera <b>todavía no eligió</b> la fecha de promesa.
     *
     * <p>Antes, apenas aparecía el campo —vacío y sin que ella lo tocara— ya salía en rojo
     * <i>"La fecha de promesa debe ser posterior a hoy"</i>, que se lee como un error de algo que
     * no hizo: un solo booleano decidía el aviso y además habilitaba "Cobrar", así que "todavía no
     * elegiste" y "elegiste mal" compartían un texto que solo describe el segundo caso. Ahora el
     * vacío es un <b>pedido en gris</b> en el mismo renglón de motivos donde ya vive
     * <i>"Escriba el monto que trae el cliente"</i>, y el rojo queda para una fecha que de verdad
     * no sirve (hoy o anterior).</p>
     *
     * <p>Lo muestra {@code cobranza-ficha.js}, que no puede llamar al servicio: la JSP le deja la
     * frase en un {@code data-} del propio grupo del campo (regla de B92, el JS nunca redacta).</p>
     */
    public static final String MOTIVO_FALTA_FECHA_PROMESA =
            "Elija la fecha de promesa para habilitar «Cobrar».";

    /**
     * B96 (30/10/2026, decisión de Joel): el botón del cartel de confirmación cuando el cobro es
     * <b>parcial</b>.
     *
     * <p>Decía <i>"Sí, cobrar lo vencido"</i> igual que en un cobro completo, pero en un pago
     * parcial el cliente <b>no</b> paga todo lo vencido: el monto estaba bien y las palabras
     * confundían — la misma lección de B92. Nombra el monto que se va a cobrar, que es lo que la
     * cajera acaba de leer en la tarjeta y en el cartel.</p>
     *
     * <p>El botón del cobro completo <b>no cambia</b> (decisión de Joel). Pura; el JS recibe la
     * plantilla con {@code {monto}} en un {@code data-} y solo reemplaza el número.</p>
     *
     * @param monto el monto ya formateado como lo muestra el sistema ("100.000 Gs."), o
     *              {@code "{monto}"} para la plantilla del JS
     */
    public static String botonCobroParcial(String monto) {
        return "Sí, cobrar " + monto;
    }

    /**
     * B82/B93 (30/10/2026, decisión de Joel): <b>único dueño de la normalización del número de
     * operación</b> que se escribe en un buscador, para las dos búsquedas del sistema que lo
     * aceptan (la de Cobranza y la del Estado de cuenta).
     *
     * <p>Los identificadores los genera el sistema siempre en mayúsculas ("CC-2026-00005"), así
     * que alcanza con recortar y pasar a mayúsculas para que la comparación exacta siga usando el
     * índice de la columna. Antes el Estado de cuenta comparaba el texto tal cual se escribía
     * ({@code ServicioReportes.buscarPrestamoParaEstadoCuenta}), de modo que "cc-2026-00005" en
     * minúsculas no encontraba nada ahí y sí en Cobranza. Pura.</p>
     */
    public static String normalizarNumeroOperacion(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase();
    }

    /**
     * B82/B93: el aviso cuando un <b>fragmento</b> de número de operación coincide con más de un
     * préstamo ("2026" entra en todos). Mismo texto para las dos búsquedas, un solo dueño. Pura.
     *
     * @param cantidad cuántos préstamos coincidieron (ya recortada al tope de la búsqueda)
     */
    public static String mensajeNumeroOperacionAmbiguo(int cantidad) {
        return "Hay " + cantidad + " préstamos cuyo número contiene lo que escribió. "
                + "Escriba el número completo.";
    }

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final PlanPagoDAO planPagoDAO = new PlanPagoDAO();
    private final CuotaDAO cuotaDAO = new CuotaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();
    private final FacturaDAO facturaDAO = new FacturaDAO();
    private final GestionCobranzaDAO gestionCobranzaDAO = new GestionCobranzaDAO();
    private final SesionCajaDAO sesionCajaDAO = new SesionCajaDAO();
    private final MovimientoCajaDAO movimientoCajaDAO = new MovimientoCajaDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final NumeradorDAO numeradorDAO = new NumeradorDAO();
    private final LiquidacionAnticipadaDAO liquidacionAnticipadaDAO = new LiquidacionAnticipadaDAO();
    private final ReversionPagoDAO reversionPagoDAO = new ReversionPagoDAO();
    private final FeriadoDAO feriadoDAO = new FeriadoDAO();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ArchivoComprobanteDAO archivoComprobanteDAO = new ArchivoComprobanteDAO();
    private final ArchivoBinarioDAO archivoBinarioDAO = new ArchivoBinarioDAO();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    // RF20: para decidir si corresponde el enlace a la ficha de Formalización en el mensaje de
    // un préstamo anulado antes del desembolso (solo si el perfil tiene permiso de verla). Pura
    // (sin Connection, sin DAO), no arma ningún ciclo de construcción con ServicioMora/
    // ServicioReportes (los dos ya dependen de ServicioCobranza -- ver armarListaTrabajo).
    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();

    /**
     * Arma el Prestamo del motor a partir del préstamo persistido y las cuotas de su plan
     * vigente (con id_cuota y las columnas de mora ya cargadas por CuotaDAO.listarPorPlan).
     * Pura: no toca la base, solo copia campos -- testeable con datos fijos (D-77, punto 18).
     */
    public Prestamo construirPrestamoParaMotor(PrestamoCredito prestamoCredito, List<Cuota> cuotas) {
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDesembolso(prestamoCredito.getFechaDesembolso());
        prestamo.setTasaTna(prestamoCredito.getTasaTna());
        prestamo.setTasaTea(prestamoCredito.getTasaTea());
        prestamo.setTasaMensual(prestamoCredito.getTasaMensual());
        prestamo.setTasaMoratoria(prestamoCredito.getTasaMoratoria());
        prestamo.setTasaPunitoria(prestamoCredito.getTasaPunitoria());
        prestamo.setCuotas(cuotas);
        return prestamo;
    }

    /** Igual que construirPrestamoParaMotor, pero cargando el préstamo y su plan vigente desde la base. */
    public Prestamo cargarPrestamoParaMotor(Connection conexion, long idPrestamo) throws SQLException {
        PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        if (prestamoCredito == null) {
            throw new NegocioException("El préstamo no existe.");
        }
        PlanPago plan = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
        if (plan == null) {
            throw new NegocioException("El préstamo no tiene un plan de pagos vigente.");
        }
        List<Cuota> cuotas = cuotaDAO.listarPorPlan(conexion, plan.getIdPlanPago());
        Prestamo prestamo = construirPrestamoParaMotor(prestamoCredito, cuotas);
        // D-87: origen/fecha de generación del plan vigente, solo en memoria -- los necesita
        // inicioPeriodoTeorico para la cuota 1 de un plan regenerado por anticipo.
        prestamo.setOrigenPlanVigente(plan.getOrigenPlan());
        if (plan.getFechaGeneracion() != null) {
            prestamo.setFechaGeneracionPlanVigente(plan.getFechaGeneracion().toLocalDate());
        }
        return prestamo;
    }

    /** RF20 (pedido de Joel, 13/10/2026): arma la lista de trabajo de /cobranza a partir de las
     * 3 listas YA TRAÍDAS por el controlador (`enMora` de ServicioMora.listarPrestamosEnMora,
     * RF24; `vencenHoy`/`proximos7Dias` de ServicioReportes.listarVencimientos, RF32, que NO se
     * toca -- sigue mostrando todo para el informe de Vencimientos). La regla de negocio --
     * "un préstamo EN_MORA aparece solo en 'en mora'", nunca repetido en las otras dos -- vive
     * acá, no en el controlador (REGLAS_DESARROLLO.md §13 punto 4): "vencenHoy"/"proximos7Dias"
     * salen filtrados, sin los préstamos que ya están en "enMora" (análisis
     * RF20_lista_trabajo_cobranza.md §6/§7, Decisión 2). Pura (sin Connection): ServicioCobranza
     * no puede depender de ServicioMora ni de ServicioReportes como campo -- los dos YA
     * dependen de ServicioCobranza (ciclo de construcción, StackOverflowError al instanciar
     * cualquiera de los tres -- confirmado al correr `mvn test`), mismo problema y misma
     * solución que ya documentó ServicioMotorFinanciero.sumarCuotasOtrasDeudas. */
    public ListaTrabajoCobranza armarListaTrabajo(List<FilaMora> enMora, List<FilaVencimiento> vencenHoy,
            List<FilaVencimiento> proximos7Dias) {
        Set<Long> idsEnMora = new HashSet<>();
        for (FilaMora fila : enMora) {
            idsEnMora.add(fila.getIdPrestamo());
        }
        return new ListaTrabajoCobranza(enMora, sinPrestamosEnMora(vencenHoy, idsEnMora),
                sinPrestamosEnMora(proximos7Dias, idsEnMora));
    }

    /** RF20: pura, visibilidad de paquete a propósito, para el test (mismo criterio que
     * limiteCoincidenciasAMostrar/capitalizar de más abajo). */
    static List<FilaVencimiento> sinPrestamosEnMora(List<FilaVencimiento> filas, Set<Long> idsEnMora) {
        List<FilaVencimiento> resultado = new ArrayList<>();
        for (FilaVencimiento fila : filas) {
            if (!idsEnMora.contains(fila.getIdPrestamo())) {
                resultado.add(fila);
            }
        }
        return resultado;
    }

    /**
     * D-77 punto 1: por cédula o por número de operación; por cédula puede haber más de un
     * préstamo. D-139 (pedido de Joel): la cédula se comparaba tal cual se escribía, sin
     * normalizar -- "800.000.014" no encontraba nada aunque "800000014" sí (mismo bug ya
     * corregido en D-117 para el estado de cuenta, ServicioReportes.buscarPrestamoParaEstadoCuenta
     * -- misma utilidad, ServicioClientes.normalizarCedula). Si no se encuentra nada por cédula,
     * explica el motivo en vez de un genérico "no se encontró nada": sin cliente, cliente sin
     * préstamos, o préstamo pendiente de desembolso (que todavía no admite cobros).
     */
    /**
     * B82/B93 (30/10/2026, decisión de Joel): <b>único buscador de préstamo por número de
     * operación</b>, compartido por la búsqueda de Cobranza y la del Estado de cuenta
     * ({@code ServicioReportes.buscarPrestamoParaEstadoCuenta}).
     *
     * <p>Antes cada una hacía lo suyo y las dos se comportaban distinto ante lo mismo: ninguna
     * aceptaba un fragmento ("00005" no encontraba "CC-2026-00005", aunque /prestamos sí lo
     * acepta, B82), y el Estado de cuenta además comparaba el texto tal cual se escribía, así que
     * fallaba en minúsculas y Cobranza no (B93).</p>
     *
     * <p>La regla, una sola vez: <b>exacto primero, sin importar mayúsculas</b> (si el número
     * completo existe, es ese y nada más) y <b>si no hay, fragmento</b> con tope
     * {@link #MAXIMO_COINCIDENCIAS_AMBIGUAS} (un fragmento corto como "2026" entra en todos los
     * préstamos). Sin filtro de estado: cada pantalla aplica después su propia regla.</p>
     *
     * @return 0 préstamos (nada coincide), 1 (el caso normal) o varios (fragmento ambiguo)
     */
    public List<PrestamoCredito> buscarPrestamosPorNumeroOperacion(Connection conexion, String textoBusqueda)
            throws SQLException {
        String numero = normalizarNumeroOperacion(textoBusqueda);
        if (numero.isEmpty()) {
            return Collections.<PrestamoCredito>emptyList();
        }
        PrestamoCredito exacto = prestamoDAO.obtenerPorIdentificadorOperacion(conexion, numero);
        if (exacto != null) {
            return Collections.singletonList(exacto);
        }
        return prestamoDAO.buscarPorFragmentoIdentificadorOperacion(conexion, numero,
                MAXIMO_COINCIDENCIAS_AMBIGUAS);
    }

    public ResultadoBusquedaCobranza buscarParaCobranza(Connection conexion, String textoBusqueda,
            PerfilUsuario perfilSolicitante) throws SQLException {
        String texto = textoBusqueda == null ? "" : textoBusqueda.trim();
        if (texto.isEmpty()) {
            return ResultadoBusquedaCobranza.encontrados(Collections.<PrestamoCredito>emptyList(),
                    Collections.<Long, Persona>emptyMap());
        }
        // RF20 (pedido de Joel): el enlace a la ficha de Formalización de un préstamo anulado
        // antes del desembolso solo se ofrece si el perfil puede verla (GENERAR_DOCUMENTOS_
        // FORMALIZACION != NINGUNO) -- la cajera no tiene acceso a /formalizacion, así que para
        // ella queda sin enlace.
        boolean puedeVerFormalizacion = servicioAutorizacion.tienePermiso(perfilSolicitante,
                Permiso.GENERAR_DOCUMENTOS_FORMALIZACION);

        // 1) Número de operación: exacto y, si no hay, por fragmento (B82/B93) -- el dueño único
        // de esa regla es buscarPrestamosPorNumeroOperacion, el mismo que usa el Estado de cuenta.
        List<PrestamoCredito> porOperacion = buscarPrestamosPorNumeroOperacion(conexion, texto);
        if (porOperacion.size() == 1) {
            return resolverUnPrestamo(conexion, porOperacion.get(0), puedeVerFormalizacion);
        }
        if (porOperacion.size() > 1) {
            // B82: el fragmento coincide con varios préstamos. Se ofrecen los cobrables, que es a
            // lo que vino la cajera; si ninguno lo es, se le pide el número completo en vez de
            // seguir al diagnóstico por cédula, que hablaría de un "cliente" que ella no buscó.
            List<PrestamoCredito> cobrables = new ArrayList<>();
            Map<Long, Persona> clientesPorPrestamo = new HashMap<>();
            for (PrestamoCredito prestamo : porOperacion) {
                if (prestamo.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                        && prestamo.getEstadoPrestamo() != EstadoPrestamo.EN_MORA) {
                    continue;
                }
                SolicitudCredito solicitudCobrable = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
                cobrables.add(prestamo);
                clientesPorPrestamo.put(prestamo.getIdPrestamo(),
                        servicioClientes.buscarPorId(conexion, solicitudCobrable.getIdCliente()));
            }
            if (!cobrables.isEmpty()) {
                return ResultadoBusquedaCobranza.encontrados(cobrables, clientesPorPrestamo);
            }
            return ResultadoBusquedaCobranza.sinResultados(mensajeNumeroOperacionAmbiguo(porOperacion.size()),
                    null, false, false);
        }

        // 2) D-146 (pedido de Joel): búsqueda en vivo por cédula (con o sin puntos, D-136 ya la
        // normaliza), nombre o apellido -- MISMA consulta que ya usa /clientes
        // (ServicioClientes.listarConFiltros + FiltroClientes.prestamoActivo="SI", que ya filtra
        // exactamente por ACTIVO/EN_MORA), no una nueva. Un cliente puede tener a lo sumo un
        // préstamo cobrable (RN F3: no se puede cargar una solicitud nueva con uno ya abierto).
        FiltroClientes filtroActivos = new FiltroClientes();
        filtroActivos.setTexto(texto);
        filtroActivos.setPrestamoActivo("SI");
        List<Persona> clientesConPrestamoActivo = servicioClientes.listarConFiltros(conexion, filtroActivos, null,
                null);
        if (!clientesConPrestamoActivo.isEmpty()) {
            List<PrestamoCredito> prestamos = new ArrayList<>();
            Map<Long, Persona> clientesPorPrestamo = new HashMap<>();
            for (Persona cliente : clientesConPrestamoActivo) {
                for (PrestamoCredito prestamo : prestamoDAO.buscarActivosOEnMoraPorCedula(conexion,
                        cliente.getCedulaIdentidad())) {
                    prestamos.add(prestamo);
                    clientesPorPrestamo.put(prestamo.getIdPrestamo(), cliente);
                }
            }
            return ResultadoBusquedaCobranza.encontrados(prestamos, clientesPorPrestamo);
        }

        // 3) Nada cobrable -- averiguar el motivo exacto (D-146, mensajes claros) en vez de un
        // "sin resultados" genérico: ¿el cliente existe pero sin préstamo activo, o no existe?
        List<Persona> cualquierCliente = servicioClientes.listarConFiltros(conexion, filtroSoloTexto(texto), null,
                null);
        if (cualquierCliente.size() == 1) {
            return resolverSinPrestamoActivo(conexion, cualquierCliente.get(0), puedeVerFormalizacion);
        }
        if (cualquierCliente.isEmpty()) {
            return ResultadoBusquedaCobranza.sinResultados("No existe un cliente con esos datos.", null, false,
                    false);
        }
        // A3b (#6, pedido de Joel): varios clientes coinciden (nombre ambiguo) y ninguno tiene
        // préstamo activo -- en vez del mensaje genérico, se lista cada coincidencia (nombre,
        // C.I.) con la misma situación que ya resuelve el caso de un solo cliente arriba (último
        // préstamo cancelado, o pendiente de desembolso), recortada a MAXIMO_COINCIDENCIAS_AMBIGUAS.
        List<CoincidenciaBusquedaCobranza> coincidencias = new ArrayList<>();
        int limite = limiteCoincidenciasAMostrar(cualquierCliente.size());
        for (int i = 0; i < limite; i++) {
            Persona cliente = cualquierCliente.get(i);
            SituacionCliente situacion = calcularSituacionCliente(conexion, cliente, puedeVerFormalizacion);
            coincidencias.add(new CoincidenciaBusquedaCobranza(cliente.getNombres() + " " + cliente.getApellidos(),
                    FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()),
                    situacion.nuncaTuvoPrestamos ? null : capitalizar(situacion.texto), situacion.idPrestamoEnlace,
                    situacion.enlaceADesembolso, situacion.enlaceAFormalizacion));
        }
        return ResultadoBusquedaCobranza.variasCoincidencias("Ninguno tiene un préstamo para cobrar.", coincidencias,
                hayMasCoincidenciasQueElLimite(cualquierCliente.size()));
    }

    /** A3b (#6, pedido de Joel): tope de la lista de coincidencias cuando la búsqueda es
     * ambigua -- más allá de esto, se le pide a la cajera que afine la búsqueda. */
    static final int MAXIMO_COINCIDENCIAS_AMBIGUAS = 10;

    /** A3b (#6): puras -- separadas de buscarParaCobranza (que sí necesita la base, para traer
     * las coincidencias y la situación de cada una) para poder probar con JUnit el recorte a
     * MAXIMO_COINCIDENCIAS_AMBIGUAS y el aviso de "hay más", sin tocar la base (REGLAS_DESARROLLO.md §7
     * punto 9). Visibilidad de paquete a propósito, para el test. */
    static int limiteCoincidenciasAMostrar(int totalSinPrestamoCobrable) {
        return Math.min(totalSinPrestamoCobrable, MAXIMO_COINCIDENCIAS_AMBIGUAS);
    }

    static boolean hayMasCoincidenciasQueElLimite(int totalSinPrestamoCobrable) {
        return totalSinPrestamoCobrable > MAXIMO_COINCIDENCIAS_AMBIGUAS;
    }

    static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private FiltroClientes filtroSoloTexto(String texto) {
        FiltroClientes filtro = new FiltroClientes();
        filtro.setTexto(texto);
        return filtro;
    }

    /** D-146: resuelve un préstamo encontrado por número de operación exacto, según su estado. */
    private ResultadoBusquedaCobranza resolverUnPrestamo(Connection conexion, PrestamoCredito prestamo,
            boolean puedeVerFormalizacion) throws SQLException {
        if (prestamo.getEstadoPrestamo() == EstadoPrestamo.ACTIVO
                || prestamo.getEstadoPrestamo() == EstadoPrestamo.EN_MORA) {
            SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            return ResultadoBusquedaCobranza.encontrados(Collections.singletonList(prestamo),
                    Collections.singletonMap(prestamo.getIdPrestamo(), cliente));
        }
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        if (prestamo.getEstadoPrestamo() == EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
            String mensaje = cliente.getNombres() + " " + cliente.getApellidos() + " tiene el préstamo "
                    + prestamo.getIdentificadorOperacion() + " pendiente de desembolso.";
            return ResultadoBusquedaCobranza.sinResultados(mensaje, prestamo.getIdPrestamo(), true, false);
        }
        // FINALIZADO o ANULADO_ANTES_DESEMBOLSO: mismo tratamiento que "sin préstamo activo",
        // reusando el cliente que ya tenemos (no hace falta volver a buscarlo por texto).
        return resolverSinPrestamoActivo(conexion, cliente, puedeVerFormalizacion);
    }

    /** D-146: cliente existe pero no tiene (o ya no tiene) un préstamo cobrable -- distingue
     * pendiente de desembolso / último finalizado / anulado antes del desembolso (RF20) / nunca
     * tuvo préstamos. */
    private ResultadoBusquedaCobranza resolverSinPrestamoActivo(Connection conexion, Persona cliente,
            boolean puedeVerFormalizacion) throws SQLException {
        String nombreCompleto = cliente.getNombres() + " " + cliente.getApellidos();
        SituacionCliente situacion = calcularSituacionCliente(conexion, cliente, puedeVerFormalizacion);
        return ResultadoBusquedaCobranza.sinResultados(nombreCompleto + " " + situacion.texto,
                situacion.idPrestamoEnlace, situacion.enlaceADesembolso, situacion.enlaceAFormalizacion);
    }

    /** A3b (#6, pedido de Joel): extraído de resolverSinPrestamoActivo para reusarlo también
     * cuando la búsqueda es ambigua (varios clientes, ninguno cobrable) -- misma situación
     * (pendiente de desembolso / último finalizado / nunca tuvo préstamos), un cliente a la vez.
     * "texto" arranca en minúscula a propósito: resolverSinPrestamoActivo lo concatena después
     * del nombre ("Juan Pérez " + texto); buscarParaCobranza lo capitaliza para mostrarlo solo
     * (ver capitalizar). */
    private SituacionCliente calcularSituacionCliente(Connection conexion, Persona cliente,
            boolean puedeVerFormalizacion) throws SQLException {
        List<PrestamoCredito> todos = prestamoDAO.listarTodosPorCedula(conexion, cliente.getCedulaIdentidad());
        PrestamoCredito pendiente = null;
        PrestamoCredito ultimoFinalizado = null;
        PrestamoCredito ultimoAnulado = null;
        for (PrestamoCredito prestamo : todos) {
            if (prestamo.getEstadoPrestamo() == EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
                pendiente = prestamo;
            } else if (prestamo.getEstadoPrestamo() == EstadoPrestamo.FINALIZADO
                    && (ultimoFinalizado == null
                            || prestamo.getFechaDesembolso().isAfter(ultimoFinalizado.getFechaDesembolso()))) {
                ultimoFinalizado = prestamo;
            } else if (prestamo.getEstadoPrestamo() == EstadoPrestamo.ANULADO_ANTES_DESEMBOLSO
                    && (ultimoAnulado == null || prestamo.getFechaAnulacionSinDesembolso()
                            .isAfter(ultimoAnulado.getFechaAnulacionSinDesembolso()))) {
                ultimoAnulado = prestamo;
            }
        }
        if (pendiente != null) {
            String texto = "tiene el préstamo " + pendiente.getIdentificadorOperacion() + " pendiente de desembolso.";
            return new SituacionCliente(texto, pendiente.getIdPrestamo(), true, false, false);
        }
        if (ultimoFinalizado != null) {
            LocalDate fechaCancelacion = obtenerFechaFinalizacion(conexion, ultimoFinalizado.getIdPrestamo());
            String texto = "no tiene préstamos activos. Último: " + ultimoFinalizado.getIdentificadorOperacion()
                    + (fechaCancelacion == null ? "" : " cancelado el " + FormateadorFechas.comoDdMmAaaa(fechaCancelacion))
                    + ".";
            return new SituacionCliente(texto, ultimoFinalizado.getIdPrestamo(), false, false, false);
        }
        // RF20 (pedido de Joel, 13/10/2026): antes caía al genérico "no tiene préstamos activos."
        // sin decir que el préstamo existe y fue anulado -- hallazgo del análisis
        // (RF20_lista_trabajo_cobranza.md §6b, préstamo CC-2026-00002). El enlace a la ficha de
        // Formalización solo se ofrece si el perfil puede verla (la cajera no puede).
        if (ultimoAnulado != null) {
            Usuario quienAnulo = ultimoAnulado.getAnuladoPor() == null ? null
                    : servicioUsuarios.buscarPorId(conexion, ultimoAnulado.getAnuladoPor());
            String texto = construirTextoAnulado(ultimoAnulado, quienAnulo);
            // Sin permiso de ver Formalización (la cajera), idPrestamoEnlace queda en null -- la
            // JSP ya no muestra ningún enlace en ese caso (mismo mecanismo que "nuncaTuvoPrestamos").
            Long idEnlace = puedeVerFormalizacion ? ultimoAnulado.getIdPrestamo() : null;
            return new SituacionCliente(texto, idEnlace, false, puedeVerFormalizacion, false);
        }
        return new SituacionCliente("no tiene préstamos activos.", null, false, false, true);
    }

    /** RF20 (pedido de Joel, 13/10/2026): "tiene la operación X, anulada antes del desembolso
     * por NOMBRE el FECHA HORA. Motivo: MOTIVO. No tiene préstamos activos." -- mismo criterio
     * que ServicioDesembolso.construirMensajePrestamoYaAnulado ("otro usuario" si no hay dato),
     * con el motivo agregado porque acá es información para la cajera, no una advertencia de
     * carrera. Pura (sin Connection, visibilidad de paquete), testeable sin base. */
    String construirTextoAnulado(PrestamoCredito anulado, Usuario quienAnulo) {
        String nombre = quienAnulo == null ? "otro usuario" : quienAnulo.getNombres() + " " + quienAnulo.getApellidos();
        String motivo = anulado.getMotivoAnulacionSinDesembolso();
        // Sin esto, un motivo que ya termina en "." (lo normal, texto libre) deja un punto
        // doble antes de "No tiene préstamos activos." -- se escribió una sola vez a propósito,
        // sin duplicar el criterio en cada llamador.
        if (motivo != null && motivo.endsWith(".")) {
            motivo = motivo.substring(0, motivo.length() - 1);
        }
        return "tiene la operación " + anulado.getIdentificadorOperacion() + ", anulada antes del desembolso por "
                + nombre + " el " + FormateadorFechas.comoDdMmAaaaHhMm(anulado.getFechaAnulacionSinDesembolso())
                + ". Motivo: " + motivo + ". No tiene préstamos activos.";
    }

    /** A3b (#6): resultado puro de calcularSituacionCliente -- ver su comentario. */
    private static final class SituacionCliente {
        private final String texto;
        private final Long idPrestamoEnlace;
        private final boolean enlaceADesembolso;
        private final boolean enlaceAFormalizacion;
        private final boolean nuncaTuvoPrestamos;

        SituacionCliente(String texto, Long idPrestamoEnlace, boolean enlaceADesembolso,
                boolean enlaceAFormalizacion, boolean nuncaTuvoPrestamos) {
            this.texto = texto;
            this.idPrestamoEnlace = idPrestamoEnlace;
            this.enlaceADesembolso = enlaceADesembolso;
            this.enlaceAFormalizacion = enlaceAFormalizacion;
            this.nuncaTuvoPrestamos = nuncaTuvoPrestamos;
        }
    }

    /** D-146: fecha en la que un préstamo FINALIZADO quedó cerrado -- la de la cancelación total
     * si existió (liquidaciones_anticipadas), o si no, la del último pago CONFIRMADO (se
     * finalizó con un cobro normal que agotó el saldo, sin pasar por "Cancelar todo"; pagoDAO ya
     * devuelve del más reciente al más viejo, ver listarHistorialCobros). Solo lectura, para
     * mostrar -- ningún cálculo. */
    private LocalDate obtenerFechaFinalizacion(Connection conexion, long idPrestamo) throws SQLException {
        LiquidacionAnticipada cancelacion = liquidacionAnticipadaDAO.obtenerTotalVigentePorPrestamo(conexion,
                idPrestamo);
        if (cancelacion != null) {
            return cancelacion.getFechaCalculo().toLocalDate();
        }
        for (Pago pago : pagoDAO.listarTodosPorPrestamo(conexion, idPrestamo)) {
            if (pago.getEstadoPago() != EstadoConfirmacionPago.REVERTIDO) {
                return pago.getFechaHoraPago().toLocalDate();
            }
        }
        return null;
    }

    /** Deuda al día (Doc. 10 ficha de cobranza): siempre a partir de las fechas, nunca del estado guardado. */
    public ResultadoDeudaExigible calcularDeudaExigible(Connection conexion, long idPrestamo, LocalDate fecha)
            throws SQLException {
        int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        return servicioMotorFinanciero.calcularDeudaExigible(prestamoMotor, fecha, diasGracia);
    }

    /**
     * D-144 (corrección de Joel sobre D-140): todo lo vencido + la cuota que vence JUSTO hoy, si
     * la hay (nunca una cuota futura -- "Cobrar" no la toca, ver distribuirPago/D-144).
     */
    public BigDecimal calcularMontoCobrableNormal(Connection conexion, long idPrestamo, LocalDate fecha)
            throws SQLException {
        int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        ResultadoDeudaExigible exigible = servicioMotorFinanciero.calcularDeudaExigible(prestamoMotor, fecha, diasGracia);
        return calcularMontoCobrableNormal(prestamoMotor, exigible, fecha);
    }

    /** Misma cuenta que la de arriba, reusada dentro de confirmarCobro (que ya tiene prestamoMotor/exigible cargados). */
    private BigDecimal calcularMontoCobrableNormal(Prestamo prestamoMotor, ResultadoDeudaExigible exigible, LocalDate fecha) {
        BigDecimal total = exigible.getTotalGeneral();
        for (Cuota cuota : prestamoMotor.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA && !cuota.getFechaVencimiento().isBefore(fecha)) {
                if (!cuota.getFechaVencimiento().isAfter(fecha)) {
                    total = total.add(cuota.getTotalProgramado());
                }
                break;
            }
        }
        return total;
    }

    /**
     * D-144 (pedido de Joel): el próximo pago pendiente (vencida o no), para sugerir como monto
     * de "Adelantar capital" en la ficha -- null si el préstamo no tiene ninguna cuota pendiente.
     */
    public Cuota obtenerProximaCuotaPendiente(Connection conexion, long idPrestamo) throws SQLException {
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        for (Cuota cuota : prestamoMotor.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                return cuota;
            }
        }
        return null;
    }

    /**
     * D-141 (SEDECO, pedido de Joel): redondeo del efectivo hacia abajo, siempre a favor del
     * cliente, en cobros en efectivo que saldan algo por completo (cobro normal cuando cubre todo
     * lo exigible, cancelación total, adelantar capital). Pura -- no toca la base, solo
     * aritmética sobre lo que ya se calculó en otro lado (para poder probarla sin JUnit tocando
     * la base, REGLAS_DESARROLLO.md §7 punto 9).
     * <p>
     * "montoACobrar" (el redondeado) pasa a ser la referencia tanto para si alcanza el efectivo
     * como para el vuelto -- nunca el exacto -- porque en la práctica nunca se le pide al cliente
     * el monto exacto (no hay moneda para los últimos guaraníes, ese es el motivo de la norma).
     * "montoAplicado" (el exacto) es lo que se le pasa al motor financiero para que la deuda
     * quede saldada por completo -- la diferencia es "montoRedondeo", el descuento a favor del
     * cliente. Si redondeoParam es 0 o el efectivo no alcanza ni para el monto ya redondeado, no
     * corresponde redondeo -- se deja el monto exacto de siempre, sin tocar el camino existente
     * (pago parcial con promesa, etc.).
     */
    /**
     * B115 (05/11/2026, redacción elegida por Joel): el aviso de que lo recibido <b>no alcanza</b>
     * para la cantidad de cuotas elegida en "Por cantidad de cuotas".
     *
     * <p>Estaba escrito dos veces —en {@code ControladorCobranzaServlet} y en
     * {@code cobranza-ficha.js}— y las dos redacciones ya habían divergido: la del navegador
     * nombraba la cantidad (<i>"no alcanza para 3 cuotas"</i>) y la del servidor no
     * (<i>"para la cantidad de cuotas elegida"</i>). Joel eligió la que nombra la cantidad.</p>
     *
     * <p><b>Una redacción, dos arranques</b> (mismo patrón que B91): la frase es una sola y vive
     * acá; lo único que puede cambiar es <b>ese</b> fragmento, porque la cantidad de cuotas
     * <b>solo existe en el navegador</b> — el formulario de confirmación manda {@code montoSugerido}
     * y {@code modoAnticipo}, nunca la cantidad. El servidor no puede nombrarla sin un campo oculto
     * nuevo que además no podría cruzar contra el monto: si alguien lo manipulara, el mensaje
     * diría una cantidad falsa, que es peor que una genérica. Así que la pantalla —lo que la cajera
     * ve de verdad— usa {@link #textoNoAlcanzaParaLasCuotas(String, String, boolean, String)} con
     * la cantidad, y el control del servidor usa
     * {@link #textoNoAlcanzaParaLasCuotas(String, String)} sin ella.
     * {@code ServicioCobranzaTest} cruza las dos y fija que no puedan diferir en nada más.</p>
     *
     * <p>Dos oraciones a propósito (B42): el título queda <i>"Lo recibido (…) no alcanza para N
     * cuotas (…)"</i> y el detalle, <i>"Elija menos cuotas o use «Por monto».",</i> con su punto.
     * Puras.</p>
     *
     * @param recibido lo recibido ya formateado, o {@code "{recibido}"} para la plantilla del JS
     * @param cantidad cuántas cuotas se eligieron, o {@code "{n}"} para la plantilla del JS
     * @param plural   si la frase va en plural (el JS lo decide eligiendo una de las dos plantillas)
     * @param monto    el monto de esa cantidad de cuotas ya formateado, o {@code "{monto}"}
     */
    public static String textoNoAlcanzaParaLasCuotas(String recibido, String cantidad, boolean plural,
            String monto) {
        // B117: con 1 cuota (plural == false) no se puede "elegir menos", así que el consejo queda
        // solo en «Por monto». El JS ya elige entre la plantilla singular y la plural según
        // cuotasElegidas === 1, así que no hace falta ningún parámetro nuevo ni tocar el JavaScript.
        return armarNoAlcanzaParaLasCuotas(recibido, cantidad + (plural ? " cuotas" : " cuota"), monto,
                plural);
    }

    /**
     * B115: la misma frase para el control del <b>servidor</b>, que no conoce la cantidad de cuotas
     * (ver el javadoc de arriba). Única diferencia permitida con la de la pantalla.
     */
    public static String textoNoAlcanzaParaLasCuotas(String recibido, String monto) {
        // B117: el consejo general -- no conoce la cantidad, así que "elija menos cuotas" sigue
        // siendo el consejo correcto para una cantidad desconocida.
        return armarNoAlcanzaParaLasCuotas(recibido, "la cantidad de cuotas elegida", monto, true);
    }

    /**
     * B115: el armador único — las dos formas de arriba solo cambian "cuantasCuotas" y el consejo.
     *
     * <p>B117 (05/11/2026, detalle que vio Joel al probar B115): <b>el consejo depende de la
     * cantidad</b>. Con 1 cuota elegida, <i>"Elija menos cuotas"</i> aconseja algo imposible —menos
     * de 1 no existe— y la única salida real es «Por monto». Es la lección de B103: un pedazo
     * <b>fijo</b> pegado a uno que <b>varía</b> es una suposición sobre todos los valores posibles
     * del que varía, y acá fallaba justo en el borde (N = 1), el valor que no se había probado
     * hasta la pasada de B115.</p>
     *
     * @param sePuedeElegirMenos si tiene sentido ofrecer "elija menos cuotas", o sea si la cantidad
     *                           elegida es mayor que 1. El respaldo del servidor pasa {@code true}
     *                           porque no conoce la cantidad (ver B115) y el consejo general sigue
     *                           siendo el correcto para una cantidad desconocida
     */
    private static String armarNoAlcanzaParaLasCuotas(String recibido, String cuantasCuotas, String monto,
            boolean sePuedeElegirMenos) {
        return "Lo recibido (" + recibido + ") no alcanza para " + cuantasCuotas + " (" + monto + "). "
                + (sePuedeElegirMenos ? "Elija menos cuotas o use «" : "Use «")
                + OPCION_POR_MONTO + "».";
    }

    /**
     * B114 (05/11/2026, hallazgo de Joel en pantalla): ¿este cobro es un <b>pago parcial</b> de
     * RN-20? Pura, y extraída a propósito de {@code confirmarCobro} para poder probarla sin base
     * (ese método abre su propia conexión).
     *
     * <p>Un cobro es parcial cuando el efectivo recibido no llega a lo exigible <b>y no se le
     * aplica el redondeo de D-141</b>. Esa segunda condición es la que faltaba: el monto redondeado
     * está por definición entre 1 y {@code redondeoParam - 1} Gs. por debajo del exacto, así que
     * sin ella el propio redondeo que la pantalla ofrece se clasificaba como pago parcial y queda
     * bloqueado pidiendo una fecha de promesa. Con el redondeo aplicándose la deuda queda
     * <b>saldada por completo</b> (lo que se distribuye es el monto exacto), así que no hay nada
     * que prometer.</p>
     *
     * <p>Es la misma forma que {@code confirmarCancelacionTotal} ya usaba
     * ({@code if (!aplicaRedondeo && recibido < exacto)}).</p>
     *
     * @param montoRecibido  el efectivo que entregó el cliente
     * @param totalExigible  lo exigible hoy ({@code ResultadoDeudaExigible.getTotalGeneral()})
     * @param aplicaRedondeo si {@link #calcularRedondeoEfectivo} resolvió que corresponde redondeo
     */
    static boolean esCobroParcial(BigDecimal montoRecibido, BigDecimal totalExigible,
            boolean aplicaRedondeo) {
        return !aplicaRedondeo && montoRecibido.compareTo(totalExigible) < 0;
    }

    ResultadoRedondeoEfectivo calcularRedondeoEfectivo(BigDecimal montoExacto, BigDecimal montoRecibido,
            int redondeoParam) {
        if (redondeoParam <= 0) {
            return new ResultadoRedondeoEfectivo(false, montoExacto, montoExacto, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal paso = BigDecimal.valueOf(redondeoParam);
        BigDecimal montoRedondeado = montoExacto.subtract(montoExacto.remainder(paso));
        if (montoRecibido.compareTo(montoRedondeado) < 0) {
            return new ResultadoRedondeoEfectivo(false, montoExacto, montoExacto, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal montoVuelto = montoRecibido.subtract(montoRedondeado);
        BigDecimal montoRedondeo = montoExacto.subtract(montoRedondeado);
        return new ResultadoRedondeoEfectivo(montoRedondeo.compareTo(BigDecimal.ZERO) > 0, montoRedondeado,
                montoExacto, montoVuelto, montoRedondeo);
    }

    /**
     * D-145 (E-1, pedido de Joel): capital pendiente real de un plan -- suma de
     * capital_programado - capital_pagado de todas sus cuotas (misma cuenta que ya usa
     * ServicioReportes.calcularTotalesEstadoCuenta). Pura -- no toca la base, solo aritmética
     * sobre cuotas ya cargadas (para poder probarla con JUnit sin base de datos, REGLAS_DESARROLLO.md §7
     * punto 9). A diferencia de cuota.saldo_capital_posterior (fija, calculada una sola vez al
     * generar el plan, el saldo que quedaría si esa cuota se pagara completa), esto siempre
     * refleja lo que de verdad se pagó hasta ahora, incluidos los pagos parciales.
     */
    BigDecimal calcularSaldoCapitalPendiente(List<Cuota> cuotasDelPlan) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Cuota cuota : cuotasDelPlan) {
            saldo = saldo.add(cuota.getCapitalProgramado()).subtract(cuota.getCapitalPagado());
        }
        return saldo;
    }

    /** Vista previa (sin persistir nada) de lo que costaría cancelar todo hoy, para la ficha. */
    public ResultadoLiquidacion calcularPreviewCancelacionTotal(Connection conexion, long idPrestamo, LocalDate fecha)
            throws SQLException {
        int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        return servicioMotorFinanciero.calcularCancelacionTotal(prestamoMotor, fecha, diasGracia);
    }

    /**
     * Vista previa (sin persistir nada) del anticipo de capital -- no depende del monto real
     * (se llama con 0, D-77/RN-11), así que se puede mostrar en la ficha antes de que la cajera
     * escriba cuánto va a cobrar. D-90: además del interés mínimo del período en curso, trae
     * `getSaldoCapitalFecha()` (el capital pendiente total), que el llamador usa para calcular el
     * monto máximo que un anticipo puede aceptar sin caer en el rechazo de D-90 ("el monto cubre
     * todo el saldo... use Cancelación total").
     */
    public ResultadoLiquidacion calcularPreviewAnticipoParcial(Connection conexion, long idPrestamo, LocalDate fecha)
            throws SQLException {
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        List<LocalDate> feriados = feriadoDAO.listarFechasActivas(conexion);
        return servicioMotorFinanciero.calcularAnticipoParcial(prestamoMotor, BigDecimal.ZERO, fecha, feriados);
    }

    /**
     * D-144 (pedido de Joel): vista previa CON el monto real que la cajera escribió -- a diferencia
     * de la de arriba (siempre con 0, solo para mostrar los límites antes de que escriba nada), acá
     * se usa el monto real para mostrar en la ficha, antes de confirmar, el interés devengado + IVA,
     * el capital que se aplica, el saldo que queda y el plan nuevo (cuotas restantes, próximo
     * vencimiento). Misma validación que confirmarAnticipoCapital ("el monto no alcanza ni para el
     * interés del período en curso"), sin persistir nada -- calcularAnticipoParcial ya valida sola
     * las otras dos (sin vencidas lo garantiza el llamador, igual que confirmarAnticipoCapital; "el
     * monto cubre todo el saldo" la rechaza el motor mismo).
     */
    public ResultadoLiquidacion calcularPreviewAnticipoParcial(Connection conexion, long idPrestamo, LocalDate fecha,
            BigDecimal monto) throws SQLException {
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        List<LocalDate> feriados = feriadoDAO.listarFechasActivas(conexion);
        ResultadoLiquidacion liquidacion = servicioMotorFinanciero.calcularAnticipoParcial(prestamoMotor, monto, fecha,
                feriados);
        if (liquidacion.getMontoAnticipoCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El monto no alcanza a cubrir siquiera el interés del período en curso ("
                    + FormateadorNumeros.comoGuaranies(liquidacion.getInteresDevengado()
                            .add(liquidacion.getIvaInteresDevengado())) + ").");
        }
        return liquidacion;
    }

    /**
     * Ampliación del 27/10/2026 (pedido del tutor, aprobado por Joel — opción B): el monto
     * MÍNIMO que, aplicado como adelanto a capital, deja exactamente
     * {@code cuotasRestantesObjetivo} cuotas en el plan nuevo.
     * <p>
     * Reemplaza el "N × el monto de la cuota" de D-145 (P-7), que era lo que confundía a la
     * cajera y al cliente: pagar 4 cuotas de un plan de 12 dejaba 7, no 8, porque el adelanto no
     * cobra los intereses futuros (RN-11, Ley 6624/2020) y por lo tanto rinde más que la suma de
     * las cuotas. Acá se invierte la pregunta: la cajera elige cuántas cuotas quiere sacar del
     * plan y el sistema dice cuánto cuesta exactamente eso.
     * <p>
     * <b>No hay ninguna fórmula nueva.</b> Se busca el monto llamando al cálculo que ya existe
     * ({@link ServicioMotorFinanciero#calcularAnticipoParcial}, sin tocarlo) sobre el préstamo
     * cargado UNA sola vez: ninguna iteración vuelve a la base. La búsqueda es binaria y es
     * válida porque la cantidad de cuotas del plan regenerado (el {@code n'} de
     * {@code regenerarPlanAnticipo}) es monótona no creciente respecto del monto — a más monto,
     * menos saldo, y nunca más cuotas.
     * <p>
     * La búsqueda recorre <b>solo múltiplos de {@code pasoEfectivo}</b>
     * ({@code credito.redondeo_efectivo}, 50 Gs.), así el monto que devuelve ya es el menor
     * múltiplo de 50 que logra esa cantidad de cuotas — no hace falta redondearlo después (un
     * piso a 50 lo dejaría por debajo y agregaría una cuota de vuelta; eso era el defecto que
     * encontró Joel). Lo que pasa del interés devengado va íntegro a capital, nunca es un cargo,
     * así que D-141 como descuento a favor del cliente no interviene acá (sí sigue igual en
     * "Pagar lo vencido" y en la cancelación total, donde el monto exacto sí es un precio).
     *
     * @param cuotasRestantesObjetivo cuántas cuotas tiene que quedar en el plan nuevo
     * @param pasoEfectivo múltiplo en el que viene el efectivo; 0 o menos = de guaraní en guaraní
     * @return el monto, o {@code null} si ningún monto posible deja exactamente esa cantidad de
     *         cuotas (el llamador avisa en pantalla en vez de inventar un número)
     */
    public BigDecimal calcularMontoMinimoParaDejarCuotas(Connection conexion, long idPrestamo, LocalDate fecha,
            int cuotasRestantesObjetivo, int pasoEfectivo) throws SQLException {
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        List<LocalDate> feriados = feriadoDAO.listarFechasActivas(conexion);
        int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
        return buscarMontoMinimoParaDejarCuotas(prestamoMotor, feriados, fecha, diasGracia, cuotasRestantesObjetivo,
                pasoEfectivo);
    }

    /**
     * La búsqueda en sí, sobre un préstamo YA cargado y sin tocar la base -- así se puede probar
     * con JUnit contra el motor real, sin base de datos (REGLAS_DESARROLLO.md §7 punto 9). Ver el
     * javadoc del método público de arriba para el criterio completo.
     */
    BigDecimal buscarMontoMinimoParaDejarCuotas(Prestamo prestamoMotor, List<LocalDate> feriados, LocalDate fecha,
            int diasGracia, int cuotasRestantesObjetivo, int pasoEfectivo) {
        if (cuotasRestantesObjetivo < 1) {
            return null;
        }
        BigDecimal paso = BigDecimal.valueOf(pasoEfectivo > 0 ? pasoEfectivo : 1);

        // Piso de la búsqueda: el interés devengado + IVA del período. Por debajo de eso el
        // motor no deja nada para capital y rechaza (mismo límite que confirmarAnticipoCapital).
        // Esta llamada con monto 0 es la que ya hace calcularPreviewAnticipoParcial(3 args) y, si
        // el préstamo tiene una cuota con fecha teórica ya pasada (D-88), lanza acá con su
        // mensaje exacto, antes de empezar a buscar.
        ResultadoLiquidacion liquidacionBase = servicioMotorFinanciero.calcularAnticipoParcial(prestamoMotor,
                BigDecimal.ZERO, fecha, feriados);
        BigDecimal minimoExcluido = liquidacionBase.getInteresDevengado()
                .add(liquidacionBase.getIvaInteresDevengado());
        // Techo de la búsqueda: el monto de cancelar todo, exclusivo -- de ahí para arriba es
        // D-90 ("el monto cubre todo el saldo... use Cancelación total.") y ya no es un adelanto.
        BigDecimal cancelarTodoExcluido = servicioMotorFinanciero
                .calcularCancelacionTotal(prestamoMotor, fecha, diasGracia).getMontoTotalLiquidacion();

        long pasoBajo = minimoExcluido.divideToIntegralValue(paso).longValue() + 1;
        long pasoAlto = cancelarTodoExcluido.subtract(BigDecimal.ONE).divideToIntegralValue(paso).longValue();
        if (pasoBajo > pasoAlto) {
            return null;
        }
        // Si ni el monto más alto posible baja hasta esa cantidad de cuotas, no existe.
        if (cuotasDelPlanTrasAnticipo(prestamoMotor, paso.multiply(BigDecimal.valueOf(pasoAlto)), fecha, feriados)
                > cuotasRestantesObjetivo) {
            return null;
        }
        while (pasoBajo < pasoAlto) {
            long medio = pasoBajo + (pasoAlto - pasoBajo) / 2;
            if (cuotasDelPlanTrasAnticipo(prestamoMotor, paso.multiply(BigDecimal.valueOf(medio)), fecha, feriados)
                    <= cuotasRestantesObjetivo) {
                pasoAlto = medio;
            } else {
                pasoBajo = medio + 1;
            }
        }
        BigDecimal monto = paso.multiply(BigDecimal.valueOf(pasoBajo));
        // El mínimo encontrado puede dejar MENOS cuotas que las pedidas si el plan nunca pasa por
        // ese número exacto (un salto de n'). En ese caso no se devuelve un monto que diría una
        // cosa y haría otra.
        return cuotasDelPlanTrasAnticipo(prestamoMotor, monto, fecha, feriados) == cuotasRestantesObjetivo
                ? monto : null;
    }

    /**
     * Cuántas cuotas quedarían en el plan nuevo si se adelantara {@code monto} -- una llamada al
     * motor, sin tocar la base ni el préstamo cargado ({@code calcularAnticipoParcial} solo lee y
     * devuelve cuotas nuevas, nunca modifica las del préstamo que recibe).
     * <p>
     * Un rechazo del motor acá solo puede ser D-90 (ese monto ya cubre todo el saldo): para la
     * búsqueda equivale a "no quedaría ninguna cuota", así que hay que seguir buscando más abajo.
     * D-88 ya se descartó antes de empezar, con la llamada de monto 0.
     */
    private int cuotasDelPlanTrasAnticipo(Prestamo prestamoMotor, BigDecimal monto, LocalDate fecha,
            List<LocalDate> feriados) {
        try {
            return servicioMotorFinanciero.calcularAnticipoParcial(prestamoMotor, monto, fecha, feriados)
                    .getNuevoPlan().size();
        } catch (NegocioException error) {
            return 0;
        }
    }

    /**
     * D-77 punto 5: la deuda exigible siempre se calcula a partir de las fechas -- esto solo
     * mantiene prestamos.estado_prestamo al día para el panel/las bandejas. Se llama al abrir la
     * ficha de cobranza y, dentro de confirmarCobro, después de aplicar un pago.
     */
    public void recalcularEstadoPrestamo(Connection conexion, long idPrestamo, LocalDate fecha) throws SQLException {
        PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        if (prestamoCredito == null || (prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                && prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.EN_MORA)) {
            return;
        }
        Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
        aplicarEstadoSegunCuotas(conexion, idPrestamo, prestamoCredito.getEstadoPrestamo(), prestamoMotor, fecha);
    }

    /**
     * PO-08/PO-09: cobro normal o parcial, en una sola transacción, con idempotencia (§14.3). Si
     * el monto no cubre todo lo vencido (RN-20), exige fecha de promesa y registra la gestión de
     * cobranza; si lo cubre, es un cobro normal (puede alcanzar también la próxima cuota a
     * vencer, D-77 punto 2). El excedente por encima de lo exigible siempre es vuelto (D-77
     * punto 4, sin aplicarlo nunca como anticipo acá). La fecha de cálculo se recibe como
     * parámetro (D-77 punto 16): ningún cálculo usa Reloj.hoy() por dentro.
     */
    public Pago confirmarCobro(long idPrestamo, String identificadorConfirmacion, BigDecimal montoRecibido,
            LocalDate fechaPromesa, LocalDate fecha, long idUsuarioCajero) throws SQLException {
        if (identificadorConfirmacion == null || identificadorConfirmacion.trim().isEmpty()) {
            throw new NegocioException("Falta la clave de confirmación del cobro.");
        }
        if (montoRecibido == null || montoRecibido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingrese el efectivo recibido.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamoCredito == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                Pago existente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                if (existente != null) {
                    Pago resultado = pagoIdempotente(conexion, existente, prestamoCredito.getEstadoPrestamo());
                    conexion.commit();
                    return resultado;
                }
                if (prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                        && prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.EN_MORA) {
                    throw new NegocioException("Este préstamo no está activo.");
                }

                SesionCaja sesion = validarCajaAbiertaHoy(conexion);

                int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
                Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
                ResultadoDeudaExigible exigible = servicioMotorFinanciero.calcularDeudaExigible(prestamoMotor, fecha,
                        diasGracia);

                // D-141 (SEDECO, redondeo del efectivo): si corresponde, el monto que se distribuye
                // entre las cuotas es el exacto (perdonando la diferencia de redondeo, mismo
                // criterio de "forgiveness" que calcularCancelacionTotal), y el vuelto se calcula
                // sobre el monto redondeado, no sobre el exacto.
                //
                // B114 (05/11/2026, hallazgo de Joel en pantalla): el redondeo se calcula ACÁ
                // ARRIBA, antes de decidir si el cobro es parcial, y es el mismo orden que
                // confirmarCancelacionTotal ya usaba. Antes el orden estaba invertido --
                // "esParcial" se decidía primero y el redondeo se calculaba solo "esParcial ? null
                // : ..." -- y eso era circular: el monto redondeado está por definición entre 1 y
                // (redondeoParam - 1) Gs. por DEBAJO del exacto, así que se clasificaba como pago
                // parcial de RN-20 y el redondeo, que existe justamente para permitir ese monto,
                // nunca llegaba a correr. La pantalla ofrecía "Cancela la deuda de 540.316 Gs.
                // (redondeo a favor del cliente: 16 Gs.)" y el servidor contestaba "El pago no
                // cubre lo exigible"; si la cajera ponía una fecha de promesa para pasar el
                // bloqueo, el cobro entraba como PARCIAL, se distribuían 540.300 (la cuota quedaba
                // PARCIAL y el préstamo EN MORA) y se insertaba una gestión de cobranza con una
                // promesa de 16 Gs. -- justo los que la empresa iba a perdonar.
                //
                // El defecto solo se veía con algo REALMENTE vencido: con una cuota que vence hoy
                // (D-144/B98) lo exigible es 0 y el control era vacío, que es el único caso de
                // cobro normal con monto redondeado que se había probado en pantalla (30/10/2026).
                int redondeoParam = servicioParametros.obtenerValorEntero(conexion, "credito.redondeo_efectivo");
                ResultadoRedondeoEfectivo redondeo = calcularRedondeoEfectivo(
                        calcularMontoCobrableNormal(prestamoMotor, exigible, fecha), montoRecibido, redondeoParam);
                boolean aplicaRedondeo = redondeo.isAplicaRedondeo();

                // B114: con el redondeo aplicándose, el cobro NO es parcial -- la deuda queda
                // saldada por completo (montoADistribuir es el exacto, más abajo). "esParcial"
                // decide cinco cosas, y por eso el arreglo va acá y no en el mensaje de error: la
                // fecha de promesa, el TipoPago, la gestión de cobranza, el texto de auditoría y
                // qué se distribuye. Corregir solo el mensaje habría dejado el pago tipado PARCIAL
                // y el redondeo sin correr.
                //
                // Cota: aplicaRedondeo exige montoRecibido >= el exacto ya redondeado hacia abajo,
                // y ese exacto nunca es menor que lo exigible, así que lo único que se pasa a
                // aceptar está a lo sumo (redondeoParam - 1) Gs. por debajo de lo exigible -- la
                // ventana de perdón de D-141, nunca un pago parcial real de RN-20. Con
                // credito.redondeo_efectivo en 0, o con efectivo que no cubra ni el redondeado,
                // aplicaRedondeo es false y esto se comporta exactamente como antes.
                boolean esParcial = esCobroParcial(montoRecibido, exigible.getTotalGeneral(), aplicaRedondeo);
                if (esParcial && (fechaPromesa == null || !fechaPromesa.isAfter(fecha))) {
                    throw new NegocioException("El pago no cubre lo exigible ("
                            + FormateadorNumeros.comoGuaranies(exigible.getTotalGeneral())
                            + "); ingrese una fecha de promesa posterior a hoy.");
                }
                BigDecimal montoADistribuir = aplicaRedondeo ? redondeo.getMontoAplicado() : montoRecibido;

                ResultadoAplicacionPago aplicacion = servicioMotorFinanciero.distribuirPago(prestamoMotor,
                        montoADistribuir, fecha, diasGracia);
                if (aplicacion.getDetalles().isEmpty()) {
                    // D-144: "Cobrar" nunca toca una cuota futura (ver distribuirPago) -- si no hay
                    // nada vencido ni ninguna cuota que venza justo hoy, no hay nada que cobrar acá.
                    throw new NegocioException("Este préstamo no tiene cuotas vencidas por cobrar. Si el "
                            + "cliente quiere pagar antes del vencimiento, use \"Adelantar capital\".");
                }

                BigDecimal montoVuelto = aplicaRedondeo ? redondeo.getMontoVuelto() : aplicacion.getMontoSobrante();
                BigDecimal montoAplicado = aplicaRedondeo ? redondeo.getMontoAplicado() : montoRecibido.subtract(montoVuelto);
                BigDecimal montoRedondeo = aplicaRedondeo ? redondeo.getMontoRedondeo() : BigDecimal.ZERO;

                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamoCredito.getIdSolicitud());
                Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());

                BigDecimal totalGravado = BigDecimal.ZERO;
                BigDecimal totalIva = BigDecimal.ZERO;
                List<LineaFactura> lineasFactura = new ArrayList<>();
                for (DetalleAplicacionPago detalle : aplicacion.getDetalles()) {
                    totalGravado = totalGravado.add(detalle.getInteresConIva()).add(detalle.getMoratorioConIva())
                            .add(detalle.getPunitorioConIva());
                    totalIva = totalIva.add(detalle.getIvaInteres()).add(detalle.getIvaMoratorio())
                            .add(detalle.getIvaPunitorio());
                    lineasFactura.addAll(construirLineasCuota(
                            detalle.getCuotaResultante(), prestamoMotor.getCuotas().size(),
                            detalle.getMontoInteres(), detalle.getIvaInteres(),
                            detalle.getMontoMoratorio(), detalle.getIvaMoratorio(), detalle.getMontoPunitorio(),
                            detalle.getIvaPunitorio()));
                }
                // D-77 punto 15: el timbrado se valida acá -- ANTES de tomar cualquier número --
                // no dentro de emitirFactura, que ya se llama después del número de recibo.
                if (correspondeEmitirFactura(totalGravado)) {
                    validarTimbradoVigente(conexion, fecha);
                }

                int anio = fecha.getYear();
                long numeroReciboSecuencia = numeradorDAO.siguienteValor(conexion, "RECIBO", anio);
                String numeroRecibo = String.format("REC-%d-%06d", anio, numeroReciboSecuencia);

                Pago pago = new Pago();
                pago.setIdentificadorConfirmacion(identificadorConfirmacion);
                pago.setIdPrestamo(idPrestamo);
                pago.setIdSesionCaja(sesion.getIdSesionCaja());
                pago.setNumeroRecibo(numeroRecibo);
                pago.setMontoRecibido(montoRecibido);
                pago.setMontoVuelto(montoVuelto);
                pago.setMontoAplicado(montoAplicado);
                pago.setMontoRedondeo(montoRedondeo);
                pago.setRegistradoPor(idUsuarioCajero);
                pago.setTipo(esParcial ? TipoPago.PARCIAL : TipoPago.NORMAL);
                long idPago = pagoDAO.insertar(conexion, pago);
                pago.setIdPago(idPago);

                for (DetalleAplicacionPago detalle : aplicacion.getDetalles()) {
                    AplicacionPago aplicacionRow = new AplicacionPago();
                    aplicacionRow.setIdPago(idPago);
                    aplicacionRow.setTipoAplicacion(TipoAplicacion.CUOTA);
                    aplicacionRow.setIdCuota(detalle.getCuotaResultante().getIdCuota());
                    aplicacionRow.setMontoMoratorio(detalle.getMontoMoratorio());
                    aplicacionRow.setMontoPunitorio(detalle.getMontoPunitorio());
                    aplicacionRow.setMontoInteres(detalle.getMontoInteres());
                    aplicacionRow.setMontoTributos(detalle.getIvaMoratorio().add(detalle.getIvaPunitorio())
                            .add(detalle.getIvaInteres()));
                    aplicacionRow.setMontoCapital(detalle.getMontoCapital());
                    aplicacionRow.setSubtotalAplicado(detalle.getSubtotalAplicado());
                    aplicacionPagoDAO.insertar(conexion, aplicacionRow);

                    cuotaDAO.actualizarTrasPago(conexion, detalle.getCuotaResultante());
                }

                MovimientoCaja movimientoCobro = new MovimientoCaja();
                movimientoCobro.setIdSesionCaja(sesion.getIdSesionCaja());
                movimientoCobro.setTipo(TipoMovimientoCaja.COBRO);
                movimientoCobro.setMonto(montoRecibido);
                movimientoCobro.setSigno(1);
                movimientoCobro.setReferenciaTipo("PAGO");
                movimientoCobro.setIdReferencia(idPago);
                movimientoCobro.setRegistradoPor(idUsuarioCajero);
                movimientoCobro.setObservacion("Cobro de " + prestamoCredito.getIdentificadorOperacion() + ".");
                movimientoCajaDAO.insertar(conexion, movimientoCobro);

                if (montoVuelto.compareTo(BigDecimal.ZERO) > 0) {
                    MovimientoCaja movimientoVuelto = new MovimientoCaja();
                    movimientoVuelto.setIdSesionCaja(sesion.getIdSesionCaja());
                    movimientoVuelto.setTipo(TipoMovimientoCaja.VUELTO);
                    movimientoVuelto.setMonto(montoVuelto);
                    movimientoVuelto.setSigno(-1);
                    movimientoVuelto.setReferenciaTipo("PAGO");
                    movimientoVuelto.setIdReferencia(idPago);
                    movimientoVuelto.setRegistradoPor(idUsuarioCajero);
                    movimientoVuelto.setObservacion("Vuelto de " + prestamoCredito.getIdentificadorOperacion() + ".");
                    movimientoCajaDAO.insertar(conexion, movimientoVuelto);
                }

                // D-78 (decisión 10): sin conceptos gravados, no se emite factura ni se consume
                // número. El timbrado ya se validó más arriba, antes de tomar el número de recibo.
                if (correspondeEmitirFactura(totalGravado)) {
                    pago.setFacturaEmitida(emitirFactura(conexion, idPago, cliente, fecha, lineasFactura,
                            totalGravado, totalIva));
                }

                if (esParcial) {
                    GestionCobranza gestion = new GestionCobranza();
                    gestion.setIdPrestamo(idPrestamo);
                    gestion.setIdPersonaContactada(cliente.getIdPersona());
                    gestion.setTipo(TipoGestionCobranza.PAGO_PARCIAL);
                    gestion.setFechaPromesa(fechaPromesa);
                    gestion.setMontoPrometido(exigible.getTotalGeneral().subtract(montoRecibido));
                    gestion.setRegistradoPor(idUsuarioCajero);
                    gestion.setIdPago(idPago); // D-95: vínculo seguro para el recibo (no se adivina).
                    gestionCobranzaDAO.insertar(conexion, gestion);
                }

                Prestamo prestamoConCuotasActualizadas = reconstruirConCuotasActualizadas(prestamoMotor, aplicacion);
                aplicarEstadoSegunCuotas(conexion, idPrestamo, prestamoCredito.getEstadoPrestamo(),
                        prestamoConCuotasActualizadas, fecha);
                pago.setEstadoPrestamoResultante(calcularEstadoSegunCuotas(prestamoConCuotasActualizadas, fecha));
                actualizarPromesasCumplidas(conexion, idPrestamo,
                        pago.getEstadoPrestamoResultante() != EstadoPrestamo.EN_MORA, fecha);

                servicioAuditoria.registrar(conexion, idUsuarioCajero, "COBRAR", "PAGO", idPago,
                        "Cobró " + FormateadorNumeros.comoGuaranies(montoRecibido) + " de "
                                + prestamoCredito.getIdentificadorOperacion() + " ("
                                + (esParcial ? "parcial" : "normal") + ")."
                                + (aplicaRedondeo ? " Redondeo a favor del cliente: -"
                                        + FormateadorNumeros.comoGuaranies(montoRedondeo) : ""));
                conexion.commit();
                return pago;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                // Carrera real (dos clics simultáneos con la misma clave): la restricción UNIQUE
                // de identificador_confirmacion la frena acá. Éxito idempotente, no error
                // (mismo criterio que ServicioDesembolso.confirmarDesembolso, §14.3).
                if ("23505".equals(error.getSQLState())) {
                    Pago pagoExistente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                    if (pagoExistente != null) {
                        return pagoExistente;
                    }
                }
                throw error;
            }
        }
    }

    /**
     * PO-10 "Cancelar todo": liquida todo lo vencido + capital no vencido + interés del período
     * en curso (RN-11, sin intereses futuros ni penalidad), en una sola transacción con
     * idempotencia. Si sobra efectivo por encima de lo liquidado, el excedente es vuelto. Al
     * llegar a saldo cero el préstamo queda FINALIZADO (RN-12).
     */
    public Pago confirmarCancelacionTotal(long idPrestamo, String identificadorConfirmacion, BigDecimal montoRecibido,
            LocalDate fecha, long idUsuarioCajero) throws SQLException {
        if (identificadorConfirmacion == null || identificadorConfirmacion.trim().isEmpty()) {
            throw new NegocioException("Falta la clave de confirmación de la cancelación.");
        }
        if (montoRecibido == null || montoRecibido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingrese el efectivo recibido.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamoCredito == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                Pago existente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                if (existente != null) {
                    Pago resultado = pagoIdempotente(conexion, existente, prestamoCredito.getEstadoPrestamo());
                    conexion.commit();
                    return resultado;
                }
                if (prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                        && prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.EN_MORA) {
                    throw new NegocioException("Este préstamo no está activo.");
                }

                SesionCaja sesion = validarCajaAbiertaHoy(conexion);
                int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
                Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);
                ResultadoDeudaExigible exigibleVencidas = servicioMotorFinanciero.calcularDeudaExigible(prestamoMotor,
                        fecha, diasGracia);
                ResultadoLiquidacion liquidacion = servicioMotorFinanciero.calcularCancelacionTotal(prestamoMotor,
                        fecha, diasGracia);

                // D-141 (SEDECO, redondeo del efectivo): mismo criterio que confirmarCobro -- el
                // monto exacto de la liquidación se "perdona" hasta el redondeado, el vuelto se
                // calcula sobre ese redondeado. Si no corresponde (parámetro en 0 o no alcanza ni
                // el redondeado), se sigue exactamente como antes.
                int redondeoParam = servicioParametros.obtenerValorEntero(conexion, "credito.redondeo_efectivo");
                ResultadoRedondeoEfectivo redondeo = calcularRedondeoEfectivo(liquidacion.getMontoTotalLiquidacion(),
                        montoRecibido, redondeoParam);
                boolean aplicaRedondeo = redondeo.isAplicaRedondeo();

                if (!aplicaRedondeo && montoRecibido.compareTo(liquidacion.getMontoTotalLiquidacion()) < 0) {
                    throw new NegocioException("El efectivo no alcanza para la cancelación total ("
                            + FormateadorNumeros.comoGuaranies(liquidacion.getMontoTotalLiquidacion()) + ").");
                }
                BigDecimal montoVuelto = aplicaRedondeo ? redondeo.getMontoVuelto()
                        : montoRecibido.subtract(liquidacion.getMontoTotalLiquidacion());
                BigDecimal montoAplicado = aplicaRedondeo ? redondeo.getMontoAplicado()
                        : liquidacion.getMontoTotalLiquidacion();
                BigDecimal montoRedondeo = aplicaRedondeo ? redondeo.getMontoRedondeo() : BigDecimal.ZERO;

                Map<Integer, Cuota> cuotasPorNumero = new HashMap<>();
                for (Cuota cuota : prestamoMotor.getCuotas()) {
                    cuotasPorNumero.put(cuota.getNumeroCuota(), cuota);
                }

                // D-77 punto 15: el total gravado (y por lo tanto si hará falta factura) se
                // conoce sin tocar la base -- se valida el timbrado acá, antes de tomar ningún
                // número (ni el de recibo ni el de factura). D-85: se arman acá mismo las líneas
                // de la factura, para validar más abajo (en emitirFactura) que su suma cuadra
                // exacto con este mismo total.
                BigDecimal totalGravado = BigDecimal.ZERO;
                BigDecimal totalIva = BigDecimal.ZERO;
                List<LineaFactura> lineasFactura = new ArrayList<>();
                List<Integer> numerosYaAplicados = new ArrayList<>();
                for (DetalleCuotaExigible detalle : exigibleVencidas.getDetalles()) {
                    totalGravado = totalGravado.add(detalle.getInteresPendiente()).add(detalle.getIvaInteresPendiente())
                            .add(detalle.getMoratorioTotal()).add(detalle.getIvaMoratorioTotal())
                            .add(detalle.getPunitorioTotal()).add(detalle.getIvaPunitorioTotal());
                    totalIva = totalIva.add(detalle.getIvaInteresPendiente()).add(detalle.getIvaMoratorioTotal())
                            .add(detalle.getIvaPunitorioTotal());
                    lineasFactura.addAll(construirLineasCuota(
                            cuotasPorNumero.get(detalle.getNumeroCuota()), prestamoMotor.getCuotas().size(),
                            detalle.getInteresPendiente(),
                            detalle.getIvaInteresPendiente(), detalle.getMoratorioTotal(),
                            detalle.getIvaMoratorioTotal(), detalle.getPunitorioTotal(),
                            detalle.getIvaPunitorioTotal()));
                    numerosYaAplicados.add(detalle.getNumeroCuota());
                }
                totalGravado = totalGravado.add(liquidacion.getInteresDevengado()).add(liquidacion.getIvaInteresDevengado());
                totalIva = totalIva.add(liquidacion.getIvaInteresDevengado());
                if (liquidacion.getInteresDevengado().compareTo(BigDecimal.ZERO) > 0) {
                    for (Cuota cuota : prestamoMotor.getCuotas()) {
                        if (cuota.getEstadoPago() != EstadoPago.PAGADA && !numerosYaAplicados.contains(cuota.getNumeroCuota())) {
                            lineasFactura.addAll(construirLineasCuota(cuota, prestamoMotor.getCuotas().size(),
                                    liquidacion.getInteresDevengado(), liquidacion.getIvaInteresDevengado(),
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                            break;
                        }
                    }
                }
                if (correspondeEmitirFactura(totalGravado)) {
                    validarTimbradoVigente(conexion, fecha);
                }

                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamoCredito.getIdSolicitud());
                Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());

                // D-93/D-94 (E8): número de constancia asignado ACÁ, en la misma transacción que
                // la cancelación -- mismo criterio que numero_recibo, para que la correlatividad
                // siga el orden real de las operaciones (nunca el orden en que alguien las
                // imprime después, que es lo que D-94 sí difiere para el PDF en sí).
                int anioConstancia = fecha.getYear();
                long numeroConstanciaSecuencia = numeradorDAO.siguienteValor(conexion, "CONSTANCIA", anioConstancia);
                String numeroConstancia = String.format("CAN-%d-%06d", anioConstancia, numeroConstanciaSecuencia);

                LiquidacionAnticipada liquidacionRow = new LiquidacionAnticipada();
                liquidacionRow.setIdPrestamo(idPrestamo);
                liquidacionRow.setTipoLiquidacion(liquidacion.getTipoLiquidacion());
                liquidacionRow.setSaldoCapitalFecha(liquidacion.getSaldoCapitalFecha());
                liquidacionRow.setInteresDevengado(liquidacion.getInteresDevengado());
                liquidacionRow.setTributosAplicables(liquidacion.getIvaInteresDevengado());
                liquidacionRow.setInteresMoratorio(liquidacion.getMoratorioPendiente());
                liquidacionRow.setInteresPunitorio(liquidacion.getPunitorioPendiente());
                liquidacionRow.setMontoTotalLiquidacion(liquidacion.getMontoTotalLiquidacion());
                liquidacionRow.setCalculadoPor(idUsuarioCajero);
                liquidacionRow.setNumeroConstancia(numeroConstancia);
                liquidacionRow.setMontoVencidasImpagas(exigibleVencidas.getTotalGeneral());
                liquidacionRow.setIvaMoratorio(liquidacion.getIvaMoratorioPendiente());
                liquidacionRow.setIvaPunitorio(liquidacion.getIvaPunitorioPendiente());
                long idLiquidacion = liquidacionAnticipadaDAO.insertar(conexion, liquidacionRow);

                int anio = fecha.getYear();
                long numeroReciboSecuencia = numeradorDAO.siguienteValor(conexion, "RECIBO", anio);
                String numeroRecibo = String.format("REC-%d-%06d", anio, numeroReciboSecuencia);

                Pago pago = new Pago();
                pago.setIdentificadorConfirmacion(identificadorConfirmacion);
                pago.setIdPrestamo(idPrestamo);
                pago.setIdSesionCaja(sesion.getIdSesionCaja());
                pago.setIdLiquidacionAnticipada(idLiquidacion);
                pago.setNumeroRecibo(numeroRecibo);
                pago.setMontoRecibido(montoRecibido);
                pago.setMontoVuelto(montoVuelto);
                pago.setMontoAplicado(montoAplicado);
                pago.setMontoRedondeo(montoRedondeo);
                pago.setRegistradoPor(idUsuarioCajero);
                pago.setTipo(TipoPago.CANCELACION);
                long idPago = pagoDAO.insertar(conexion, pago);
                pago.setIdPago(idPago);

                for (DetalleCuotaExigible detalle : exigibleVencidas.getDetalles()) {
                    insertarAplicacionCancelacion(conexion, idPago, cuotasPorNumero.get(detalle.getNumeroCuota()),
                            detalle.getCapitalPendiente(), detalle.getInteresPendiente(),
                            detalle.getIvaInteresPendiente(), detalle.getMoratorioTotal(),
                            detalle.getIvaMoratorioTotal(), detalle.getPunitorioTotal(),
                            detalle.getIvaPunitorioTotal(), detalle.getTotalExigible());
                }
                // El resto de las cuotas pendientes (no vencidas): la primera ("en curso") lleva
                // el interés devengado del período en curso; las demás, solo su capital (RN-11,
                // sin intereses futuros).
                boolean primeraNoVencidaProcesada = false;
                for (Cuota cuota : prestamoMotor.getCuotas()) {
                    if (cuota.getEstadoPago() == EstadoPago.PAGADA || numerosYaAplicados.contains(cuota.getNumeroCuota())) {
                        continue;
                    }
                    BigDecimal interes = BigDecimal.ZERO;
                    BigDecimal ivaInteres = BigDecimal.ZERO;
                    if (!primeraNoVencidaProcesada) {
                        interes = liquidacion.getInteresDevengado();
                        ivaInteres = liquidacion.getIvaInteresDevengado();
                        primeraNoVencidaProcesada = true;
                    }
                    insertarAplicacionCancelacion(conexion, idPago, cuota, cuota.capitalPendiente(), interes,
                            ivaInteres, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            cuota.capitalPendiente().add(interes).add(ivaInteres));
                }

                // Todas las cuotas pendientes quedan PAGADA (liquidacion.getCuotasResultantes()).
                for (Cuota resultante : liquidacion.getCuotasResultantes()) {
                    cuotaDAO.actualizarTrasPago(conexion, resultante);
                }

                registrarMovimientosCobro(conexion, sesion.getIdSesionCaja(), idPago, montoRecibido, montoVuelto,
                        idUsuarioCajero, prestamoCredito.getIdentificadorOperacion(), "Cancelación total");

                if (correspondeEmitirFactura(totalGravado)) {
                    pago.setFacturaEmitida(emitirFactura(conexion, idPago, cliente, fecha, lineasFactura,
                            totalGravado, totalIva));
                }

                prestamoDAO.marcarFinalizado(conexion, idPrestamo);
                pago.setEstadoPrestamoResultante(EstadoPrestamo.FINALIZADO);
                // Cancelación total: sin vencidas por definición (RN-11), así que resuelve
                // cualquier promesa abierta (decisión 1, Bloque 4).
                actualizarPromesasCumplidas(conexion, idPrestamo, true, fecha);

                // D-86: comoGuaranies ya termina en "Gs." -- agregar otro "." duplica el punto.
                servicioAuditoria.registrar(conexion, idUsuarioCajero, "CANCELAR_TOTAL", "PAGO", idPago,
                        "Canceló " + prestamoCredito.getIdentificadorOperacion() + " por "
                                + FormateadorNumeros.comoGuaranies(montoAplicado)
                                + (aplicaRedondeo ? " Redondeo a favor del cliente: -"
                                        + FormateadorNumeros.comoGuaranies(montoRedondeo) : ""));
                conexion.commit();
                return pago;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                if ("23505".equals(error.getSQLState())) {
                    Pago pagoExistente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                    if (pagoExistente != null) {
                        return pagoExistente;
                    }
                }
                throw error;
            }
        }
    }

    /**
     * PO-10 "Adelantar capital": precondición RN-11 (sin cuotas vencidas -- si las hay, se
     * cobran antes con confirmarCobro) validada acá, ya que el motor no la valida. Cobra el
     * interés devengado del período en curso y aplica el resto a capital. Nunca hay redondeo acá
     * (D-141 no aplica a este camino, sin cambios) -- pero D-146 (pedido de Joel) sí puede haber
     * vuelto real: "montoRecibido" (el efectivo físico que entrega el cliente) y "montoAplicado"
     * (lo que de verdad se liquida -- interés del período + capital) llegan como DOS valores
     * separados, igual que confirmarCobro separa "lo recibido" de "lo aplicado"; el vuelto es la
     * diferencia. `montoAplicado` es el que entra a calcularAnticipoParcial (el motor financiero
     * no cambia), `montoRecibido` solo se guarda y se registra en caja (COBRO por lo recibido,
     * VUELTO por la diferencia -- mismo patrón que confirmarCobro, vía registrarMovimientosCobro,
     * ya compartido). Genera un plan nuevo (versión siguiente, origen ANTICIPO_PARCIAL) con la
     * cuota contractual constante y menos cuotas restantes.
     */
    public Pago confirmarAnticipoCapital(long idPrestamo, String identificadorConfirmacion, BigDecimal montoRecibido,
            BigDecimal montoAplicado, LocalDate fecha, long idUsuarioCajero) throws SQLException {
        if (identificadorConfirmacion == null || identificadorConfirmacion.trim().isEmpty()) {
            throw new NegocioException("Falta la clave de confirmación del anticipo.");
        }
        if (montoRecibido == null || montoRecibido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingrese el efectivo recibido.");
        }
        if (montoAplicado == null || montoAplicado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingrese el monto a aplicar al adelanto.");
        }
        // D-146 (pedido de Joel): el efectivo recibido nunca puede ser menor que lo que se va a
        // aplicar -- si no alcanza, no se cobra (el vuelto nunca puede ser negativo).
        if (montoRecibido.compareTo(montoAplicado) < 0) {
            throw new NegocioException("El efectivo recibido (" + FormateadorNumeros.comoGuaranies(montoRecibido)
                    + ") es menor que el monto a aplicar (" + FormateadorNumeros.comoGuaranies(montoAplicado) + ").");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                PrestamoCredito prestamoCredito = prestamoDAO.obtenerPorId(conexion, idPrestamo);
                if (prestamoCredito == null) {
                    throw new NegocioException("El préstamo no existe.");
                }
                Pago existente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                if (existente != null) {
                    Pago resultado = pagoIdempotente(conexion, existente, prestamoCredito.getEstadoPrestamo());
                    conexion.commit();
                    return resultado;
                }
                if (prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.ACTIVO
                        && prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.EN_MORA) {
                    throw new NegocioException("Este préstamo no está activo.");
                }

                SesionCaja sesion = validarCajaAbiertaHoy(conexion);
                int diasGracia = servicioParametros.obtenerValorEntero(conexion, "credito.dias_gracia_mora");
                Prestamo prestamoMotor = cargarPrestamoParaMotor(conexion, idPrestamo);

                ResultadoDeudaExigible exigible = servicioMotorFinanciero.calcularDeudaExigible(prestamoMotor, fecha,
                        diasGracia);
                if (exigible.getTotalGeneral().compareTo(BigDecimal.ZERO) > 0) {
                    throw new NegocioException("Hay cuotas vencidas; cóbrelas antes de adelantar capital.");
                }

                List<LocalDate> feriados = feriadoDAO.listarFechasActivas(conexion);
                // D-146: el motor financiero recibe "lo aplicado" (nunca "lo recibido") -- sin
                // cambios en calcularAnticipoParcial, solo se le pasa el parámetro correcto.
                ResultadoLiquidacion liquidacion = servicioMotorFinanciero.calcularAnticipoParcial(prestamoMotor,
                        montoAplicado, fecha, feriados);
                if (liquidacion.getMontoAnticipoCapital().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new NegocioException("El monto no alcanza a cubrir siquiera el interés del período en curso ("
                            + FormateadorNumeros.comoGuaranies(liquidacion.getInteresDevengado()
                                    .add(liquidacion.getIvaInteresDevengado())) + ").");
                }
                // D-77 punto 15: se valida el timbrado (si corresponderá factura) antes de tomar
                // ningún número. D-85: la línea de factura se arma acá mismo (un solo concepto,
                // el interés del período en curso -- sin cuota vencida a la que atribuirla en la
                // base, ver AplicacionPago.idCuota=null más abajo, pero sí una cuota "en curso"
                // conceptual para la descripción de la línea: la primera no pagada).
                BigDecimal totalGravado = liquidacion.getInteresDevengado().add(liquidacion.getIvaInteresDevengado());
                List<LineaFactura> lineasFactura = new ArrayList<>();
                if (correspondeEmitirFactura(totalGravado)) {
                    validarTimbradoVigente(conexion, fecha);
                    for (Cuota cuota : prestamoMotor.getCuotas()) {
                        if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                            lineasFactura.addAll(construirLineasCuota(cuota, prestamoMotor.getCuotas().size(),
                                    liquidacion.getInteresDevengado(), liquidacion.getIvaInteresDevengado(),
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                            break;
                        }
                    }
                }

                SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamoCredito.getIdSolicitud());
                Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());

                LiquidacionAnticipada liquidacionRow = new LiquidacionAnticipada();
                liquidacionRow.setIdPrestamo(idPrestamo);
                liquidacionRow.setTipoLiquidacion(liquidacion.getTipoLiquidacion());
                liquidacionRow.setSaldoCapitalFecha(liquidacion.getSaldoCapitalFecha());
                liquidacionRow.setInteresDevengado(liquidacion.getInteresDevengado());
                liquidacionRow.setTributosAplicables(liquidacion.getIvaInteresDevengado());
                liquidacionRow.setInteresMoratorio(BigDecimal.ZERO);
                liquidacionRow.setInteresPunitorio(BigDecimal.ZERO);
                liquidacionRow.setMontoTotalLiquidacion(liquidacion.getMontoTotalLiquidacion());
                liquidacionRow.setMontoAnticipoCapital(liquidacion.getMontoAnticipoCapital());
                liquidacionRow.setNuevoSaldoCapital(liquidacion.getNuevoSaldoCapital());
                liquidacionRow.setCalculadoPor(idUsuarioCajero);
                long idLiquidacion = liquidacionAnticipadaDAO.insertar(conexion, liquidacionRow);

                // Plan nuevo: la versión anterior queda histórica (RN-13), no se borra nada.
                PlanPago planAnterior = planPagoDAO.obtenerVigentePorPrestamo(conexion, idPrestamo);
                planPagoDAO.marcarNoVigente(conexion, planAnterior.getIdPlanPago());
                PlanPago planNuevo = new PlanPago();
                planNuevo.setIdPrestamo(idPrestamo);
                planNuevo.setVersionPlan(planAnterior.getVersionPlan() + 1);
                planNuevo.setOrigenPlan("ANTICIPO_PARCIAL");
                planNuevo.setVigente(true);
                planNuevo.setIdLiquidacionOrigen(idLiquidacion);
                planNuevo.setGeneradoPor(idUsuarioCajero);
                long idPlanNuevo = planPagoDAO.insertar(conexion, planNuevo);
                cuotaDAO.insertarTodas(conexion, idPlanNuevo, liquidacion.getNuevoPlan());

                int anio = fecha.getYear();
                long numeroReciboSecuencia = numeradorDAO.siguienteValor(conexion, "RECIBO", anio);
                String numeroRecibo = String.format("REC-%d-%06d", anio, numeroReciboSecuencia);

                // D-146 (pedido de Joel): mismo patrón que confirmarCobro -- "recibido" es lo
                // físico, "aplicado" es lo que de verdad reduce la deuda, y el vuelto es la
                // diferencia (nunca negativo, ya validado arriba).
                BigDecimal montoVuelto = montoRecibido.subtract(montoAplicado);

                Pago pago = new Pago();
                pago.setIdentificadorConfirmacion(identificadorConfirmacion);
                pago.setIdPrestamo(idPrestamo);
                pago.setIdSesionCaja(sesion.getIdSesionCaja());
                pago.setIdLiquidacionAnticipada(idLiquidacion);
                pago.setNumeroRecibo(numeroRecibo);
                pago.setMontoRecibido(montoRecibido);
                pago.setMontoVuelto(montoVuelto);
                pago.setMontoAplicado(montoAplicado);
                pago.setRegistradoPor(idUsuarioCajero);
                pago.setTipo(TipoPago.ANTICIPO);
                long idPago = pagoDAO.insertar(conexion, pago);
                pago.setIdPago(idPago);

                AplicacionPago aplicacionRow = new AplicacionPago();
                aplicacionRow.setIdPago(idPago);
                aplicacionRow.setTipoAplicacion(TipoAplicacion.ANTICIPO_CAPITAL);
                aplicacionRow.setIdCuota(null);
                aplicacionRow.setMontoInteres(liquidacion.getInteresDevengado());
                aplicacionRow.setMontoTributos(liquidacion.getIvaInteresDevengado());
                aplicacionRow.setMontoCapital(liquidacion.getMontoAnticipoCapital());
                aplicacionRow.setSubtotalAplicado(liquidacion.getMontoTotalLiquidacion());
                aplicacionPagoDAO.insertar(conexion, aplicacionRow);

                // D-146: COBRO por lo recibido + VUELTO por la diferencia (si hay), mismo patrón
                // que confirmarCobro/confirmarCancelacionTotal -- registrarMovimientosCobro ya
                // hacía exactamente esto, solo que antes se la llamaba con "recibido"=aplicado y
                // vuelto fijo en 0.
                registrarMovimientosCobro(conexion, sesion.getIdSesionCaja(), idPago, montoRecibido, montoVuelto,
                        idUsuarioCajero, prestamoCredito.getIdentificadorOperacion(), "Anticipo de capital");

                if (correspondeEmitirFactura(totalGravado)) {
                    pago.setFacturaEmitida(emitirFactura(conexion, idPago, cliente, fecha, lineasFactura,
                            totalGravado, liquidacion.getIvaInteresDevengado()));
                }

                // El préstamo sigue ACTIVO (no hay vencidas, por precondición de arriba).
                if (prestamoCredito.getEstadoPrestamo() != EstadoPrestamo.ACTIVO) {
                    prestamoDAO.actualizarEstadoMora(conexion, idPrestamo, EstadoPrestamo.ACTIVO);
                }
                pago.setEstadoPrestamoResultante(EstadoPrestamo.ACTIVO);
                // Anticipo: ya no tenía vencidas antes (precondición de arriba) y no las genera,
                // así que también resuelve cualquier promesa abierta (decisión 1, Bloque 4).
                actualizarPromesasCumplidas(conexion, idPrestamo, true, fecha);

                servicioAuditoria.registrar(conexion, idUsuarioCajero, "ANTICIPAR_CAPITAL", "PAGO", idPago,
                        "Adelantó " + FormateadorNumeros.comoGuaranies(liquidacion.getMontoAnticipoCapital())
                                + " a capital de " + prestamoCredito.getIdentificadorOperacion() + ".");
                conexion.commit();
                return pago;
            } catch (RuntimeException error) {
                conexion.rollback();
                throw error;
            } catch (SQLException error) {
                conexion.rollback();
                if ("23505".equals(error.getSQLState())) {
                    Pago pagoExistente = pagoDAO.obtenerPorIdentificadorConfirmacion(conexion, identificadorConfirmacion);
                    if (pagoExistente != null) {
                        return pagoExistente;
                    }
                }
                throw error;
            }
        }
    }

    private SesionCaja validarCajaAbiertaHoy(Connection conexion) throws SQLException {
        SesionCaja sesion = sesionCajaDAO.obtenerAbierta(conexion);
        if (sesion == null) {
            throw new NegocioException("No hay una caja abierta.");
        }
        // Duplicaciones y parches (Commit 1): el mensaje y la excepción (CajaDeOtroDiaException,
        // no NegocioException genérica) ahora salen de ServicioCaja, único lugar.
        servicioCaja.validarSesionDeHoy(sesion);
        return sesion;
    }

    private void insertarAplicacionCancelacion(Connection conexion, long idPago, Cuota cuota, BigDecimal capital,
            BigDecimal interes, BigDecimal ivaInteres, BigDecimal moratorio, BigDecimal ivaMoratorio,
            BigDecimal punitorio, BigDecimal ivaPunitorio, BigDecimal subtotal) throws SQLException {
        AplicacionPago aplicacion = new AplicacionPago();
        aplicacion.setIdPago(idPago);
        aplicacion.setTipoAplicacion(TipoAplicacion.CUOTA);
        aplicacion.setIdCuota(cuota.getIdCuota());
        aplicacion.setMontoCapital(capital);
        aplicacion.setMontoInteres(interes);
        aplicacion.setMontoMoratorio(moratorio);
        aplicacion.setMontoPunitorio(punitorio);
        aplicacion.setMontoTributos(ivaInteres.add(ivaMoratorio).add(ivaPunitorio));
        aplicacion.setSubtotalAplicado(subtotal);
        aplicacionPagoDAO.insertar(conexion, aplicacion);
    }

    /**
     * D-85: arma las líneas de factura de una cuota (RN-25/D-78 -- una línea por concepto
     * gravado, solo si tiene importe; capital nunca entra acá). Pura, sin tocar la base.
     *
     * <p><b>B102 (01/11/2026, decisión de Joel)</b>: el concepto sale del <b>único dueño</b>
     * ({@link #conceptoFacturaDeCuota}), el mismo que usa la impresión. Antes esta versión decía
     * <i>"Intereses cuota 1 — CC-2026-00004 — vto. 30/10/2026"</i> y la impresión
     * <i>"Interés compensatorio cuota 1/6"</i>: dos redacciones del mismo concepto, y la única que
     * alguien ve es la de impresión (la factura <b>no guarda renglones</b>, se reconstruyen de
     * {@code aplicaciones_pago}, D-93/D-94). Se adoptó <b>el texto que hoy se imprime</b>, a
     * propósito, para que <b>ninguna factura ya emitida cambie al reimprimirse</b>. Estas líneas
     * siguen sirviendo solo para los totales y el control de D-85.</p>
     *
     * <p>Visibilidad de paquete (no private) para que el test pueda cruzarla contra
     * {@link #derivarLineasCuotaFactura}, que es la que de verdad se ve en el papel.</p>
     */
    List<LineaFactura> construirLineasCuota(Cuota cuota, int totalCuotas, BigDecimal interes,
            BigDecimal ivaInteres, BigDecimal moratorio, BigDecimal ivaMoratorio, BigDecimal punitorio,
            BigDecimal ivaPunitorio) {
        List<LineaFactura> lineas = new ArrayList<>();
        int numeroCuota = cuota.getNumeroCuota();
        if (interes.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(
                    conceptoFacturaDeCuota(ConceptoGravado.INTERES, numeroCuota, totalCuotas), interes, ivaInteres));
        }
        if (moratorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(
                    conceptoFacturaDeCuota(ConceptoGravado.MORATORIO, numeroCuota, totalCuotas),
                    moratorio, ivaMoratorio));
        }
        if (punitorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(
                    conceptoFacturaDeCuota(ConceptoGravado.PUNITORIO, numeroCuota, totalCuotas),
                    punitorio, ivaPunitorio));
        }
        return lineas;
    }

    /**
     * D-89: `descripcionOperacion` distingue en el libro de caja qué tipo de operación generó el
     * movimiento (ej. "Cobro", "Cancelación total", "Anticipo de capital") -- antes decía
     * siempre "Cobro de X.", igual para las tres, sin poder diferenciarlas a simple vista.
     */
    private void registrarMovimientosCobro(Connection conexion, long idSesionCaja, long idPago,
            BigDecimal montoRecibido, BigDecimal montoVuelto, long idUsuarioCajero, String identificadorOperacion,
            String descripcionOperacion) throws SQLException {
        MovimientoCaja movimientoCobro = new MovimientoCaja();
        movimientoCobro.setIdSesionCaja(idSesionCaja);
        movimientoCobro.setTipo(TipoMovimientoCaja.COBRO);
        movimientoCobro.setMonto(montoRecibido);
        movimientoCobro.setSigno(1);
        movimientoCobro.setReferenciaTipo("PAGO");
        movimientoCobro.setIdReferencia(idPago);
        movimientoCobro.setRegistradoPor(idUsuarioCajero);
        movimientoCobro.setObservacion(descripcionOperacion + " de " + identificadorOperacion + ".");
        movimientoCajaDAO.insertar(conexion, movimientoCobro);

        if (montoVuelto.compareTo(BigDecimal.ZERO) > 0) {
            MovimientoCaja movimientoVuelto = new MovimientoCaja();
            movimientoVuelto.setIdSesionCaja(idSesionCaja);
            movimientoVuelto.setTipo(TipoMovimientoCaja.VUELTO);
            movimientoVuelto.setMonto(montoVuelto);
            movimientoVuelto.setSigno(-1);
            movimientoVuelto.setReferenciaTipo("PAGO");
            movimientoVuelto.setIdReferencia(idPago);
            movimientoVuelto.setRegistradoPor(idUsuarioCajero);
            movimientoVuelto.setObservacion("Vuelto de " + identificadorOperacion + ".");
            movimientoCajaDAO.insertar(conexion, movimientoVuelto);
        }
    }

    /**
     * Duplicaciones y parches (Commit 3): único lugar que lee fiscal.timbrado_vigencia_desde/
     * hasta vigente EN una fecha de referencia -- antes se repetía esta misma llamada en 3
     * lugares (validarTimbradoVigente, emitirFactura, agregarTimbradoDeFactura), uno de ellos con
     * un comentario propio advirtiendo el riesgo de desincronización si se tocaba de nuevo. Dos
     * métodos (no uno que devuelva los dos) porque emitirFactura solo necesita "desde".
     */
    private LocalDate obtenerTimbradoVigenteDesde(Connection conexion, LocalDate fechaReferencia) throws SQLException {
        return ServicioParametros.parsearFecha("fiscal.timbrado_vigencia_desde",
                servicioParametros.obtenerValorAFecha(conexion, "fiscal.timbrado_vigencia_desde", fechaReferencia));
    }

    private LocalDate obtenerTimbradoVigenteHasta(Connection conexion, LocalDate fechaReferencia) throws SQLException {
        return ServicioParametros.parsearFecha("fiscal.timbrado_vigencia_hasta",
                servicioParametros.obtenerValorAFecha(conexion, "fiscal.timbrado_vigencia_hasta", fechaReferencia));
    }

    /**
     * D-77 punto 15/D-89: se llama ANTES de tomar cualquier número (recibo o factura) de la
     * operación que la origina -- si el timbrado no está vigente (por fecha) o agotado (por
     * cantidad), se rechaza sin haber consumido ningún numerador.
     *
     * D-116: resuelve el timbrado vigente EN LA FECHA del cobro (`obtenerValorAFecha`, D-94),
     * nunca "hoy" (`obtenerValorFecha`) -- para que un cobro histórico se valide y facture con el
     * timbrado que realmente regía ese día (puede ser uno ya vencido), no con el actual. Mismo
     * patrón que ya usaban `reconstruirLineasFactura`/`generarPdfNotaAnulacion` para reimprimir
     * documentos viejos (D-94) -- acá se aplica también a EMITIR uno nuevo. En el uso real desde
     * la pantalla, `fecha` siempre es hoy, así que el comportamiento no cambia para nada; el único
     * caso donde `fecha` puede ser pasada es el cargador de datos de demostración (7.4, D-115).
     */
    void validarTimbradoVigente(Connection conexion, LocalDate fecha) throws SQLException {
        LocalDate timbradoDesde = obtenerTimbradoVigenteDesde(conexion, fecha);
        LocalDate timbradoHasta = obtenerTimbradoVigenteHasta(conexion, fecha);
        validarTimbradoVigente(fecha, timbradoDesde, timbradoHasta);

        // D-89/D-116: mismo clave/año que emitirFactura usa para consumir el número de verdad --
        // el chequeo tiene que mirar exactamente la misma fila del numerador (el timbrado vigente
        // EN LA FECHA del cobro, no el de hoy).
        int anioNumeradorFactura = timbradoDesde.getYear();
        long valorActual = numeradorDAO.obtenerValorActual(conexion, "FACTURA", anioNumeradorFactura);
        long numeroDesde = ServicioParametros.parsearEntero("fiscal.factura_numero_desde",
                servicioParametros.obtenerValorAFecha(conexion, "fiscal.factura_numero_desde", fecha));
        long numeroHasta = ServicioParametros.parsearEntero("fiscal.factura_numero_hasta",
                servicioParametros.obtenerValorAFecha(conexion, "fiscal.factura_numero_hasta", fecha));
        validarTimbradoDisponible(valorActual + 1, numeroDesde, numeroHasta);
    }

    /**
     * D-78/D-89: única condición que decide si una operación de cobranza factura algo (y por lo
     * tanto valida el timbrado y consume un número de FACTURA) -- pura, sin DB, para poder
     * testear con certeza exactamente esta decisión (D-89: "un cobro con intereses llama 1 vez a
     * siguienteValor(FACTURA); un anticipo o una cancelación sin conceptos gravados, nunca").
     */
    boolean correspondeEmitirFactura(BigDecimal totalGravado) {
        return totalGravado.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * D-89: completa los dos campos transitorios de un Pago ya existente (repetición idempotente
     * de identificador_confirmacion, §14.3) -- `estadoActual` ya refleja el estado ACTUAL del
     * préstamo (se lee al principio de cada confirmar*, en esta misma request de reintento, así
     * que ya es el resultante de la operación original), y la factura (si hubo) se busca por
     * id_pago porque acá no la tenemos en memoria como en el camino normal.
     */
    private Pago pagoIdempotente(Connection conexion, Pago existente, EstadoPrestamo estadoActual) throws SQLException {
        existente.setEstadoPrestamoResultante(estadoActual);
        existente.setFacturaEmitida(facturaDAO.obtenerPorPago(conexion, existente.getIdPago()));
        return existente;
    }

    /** Pura (D-77 punto 15): separada de la que lee los parámetros, para testear con fechas fijas. */
    public void validarTimbradoVigente(LocalDate fecha, LocalDate timbradoDesde, LocalDate timbradoHasta) {
        if (fecha.isBefore(timbradoDesde) || fecha.isAfter(timbradoHasta)) {
            throw new NegocioException("El timbrado no está vigente en la fecha del cobro (vigente del "
                    + FormateadorFechas.comoDdMmAaaa(timbradoDesde) + " al "
                    + FormateadorFechas.comoDdMmAaaa(timbradoHasta) + ").");
        }
    }

    /**
     * D-89: pura -- separada de la que lee el numerador/los parámetros, para testear con valores
     * fijos (mismo criterio que validarTimbradoVigente/D-77 punto 15). `proximoNumero` es el que
     * `emitirFactura` tomaría a continuación (valor actual + 1), nunca el ya emitido.
     */
    public void validarTimbradoDisponible(long proximoNumero, long numeroDesde, long numeroHasta) {
        if (proximoNumero > numeroHasta) {
            throw new NegocioException("Timbrado agotado: se alcanzó el último número autorizado (hasta "
                    + numeroHasta + "). Solicite un nuevo timbrado.");
        }
        if (proximoNumero < numeroDesde) {
            throw new NegocioException("El numerador de facturas quedó por debajo del rango autorizado (desde "
                    + numeroDesde + "); revise la configuración del timbrado.");
        }
    }

    /**
     * Bloque 6 (PA-13): aviso previo al rechazo duro de validarTimbradoDisponible -- nunca
     * bloquea, solo informa mientras todavía se puede pedir un timbrado nuevo con tiempo. Pura
     * (mismo criterio que validarTimbradoDisponible), null si no corresponde avisar todavía.
     * `proximoNumero`/`umbralAviso` en la misma unidad ("números"), para poder comparar contra el
     * límite exacto en los tests.
     */
    public String evaluarAvisoNumeroFactura(long proximoNumero, long numeroHasta, long umbralAviso) {
        long restantes = numeroHasta - proximoNumero + 1;
        if (restantes <= umbralAviso) {
            return "Quedan " + restantes + " números de factura (hasta " + numeroHasta
                    + "). Solicite un nuevo timbrado.";
        }
        return null;
    }

    /**
     * Bloque 6 (PA-13): mismo criterio de aviso que evaluarAvisoNumeroFactura, pero por la
     * vigencia del timbrado (fiscal.timbrado_dias_aviso) en vez de por cantidad de números. Pura,
     * null si no corresponde avisar todavía.
     */
    public String evaluarAvisoVigenciaTimbrado(LocalDate hoy, LocalDate timbradoHasta, long diasAviso) {
        long diasRestantes = ChronoUnit.DAYS.between(hoy, timbradoHasta);
        if (diasRestantes <= diasAviso) {
            return "El timbrado vence el " + FormateadorFechas.comoDdMmAaaa(timbradoHasta) + " (faltan "
                    + diasRestantes + " días). Solicite la renovación.";
        }
        return null;
    }

    /**
     * Bloque 6 (PA-13): junta los dos avisos de arriba (0, 1 o los 2 juntos) leyendo el numerador
     * y los parámetros reales -- para el panel y la ficha de cobranza, siempre informativo, nunca
     * bloquea nada (eso lo sigue haciendo validarTimbradoVigente al momento real de facturar).
     */
    public List<String> obtenerAvisosTimbrado(Connection conexion, LocalDate hoy) throws SQLException {
        List<String> avisos = new ArrayList<>();
        LocalDate timbradoDesde = servicioParametros.obtenerValorFecha(conexion, "fiscal.timbrado_vigencia_desde");
        LocalDate timbradoHasta = servicioParametros.obtenerValorFecha(conexion, "fiscal.timbrado_vigencia_hasta");
        int anioNumeradorFactura = timbradoDesde.getYear();
        long valorActual = numeradorDAO.obtenerValorActual(conexion, "FACTURA", anioNumeradorFactura);
        long numeroHasta = servicioParametros.obtenerValorEntero(conexion, "fiscal.factura_numero_hasta");
        long umbralNumero = servicioParametros.obtenerValorEntero(conexion, "fiscal.factura_numero_aviso");
        String avisoNumero = evaluarAvisoNumeroFactura(valorActual + 1, numeroHasta, umbralNumero);
        if (avisoNumero != null) {
            avisos.add(avisoNumero);
        }
        long diasAviso = servicioParametros.obtenerValorEntero(conexion, "fiscal.timbrado_dias_aviso");
        String avisoVigencia = evaluarAvisoVigenciaTimbrado(hoy, timbradoHasta, diasAviso);
        if (avisoVigencia != null) {
            avisos.add(avisoVigencia);
        }
        return avisos;
    }

    /**
     * D-78: solo conceptos gravados (interés, moratorio, punitorio); el capital nunca entra acá.
     * El timbrado ya se validó antes de tomar cualquier número (validarTimbradoVigente, llamada
     * por el método que invoca a este antes de tomar el número de recibo).
     */
    /**
     * D-85: recibe las LÍNEAS de la factura (una por concepto gravado y cuota, RN-25/D-78) en
     * vez de los totales sueltos -- los totales se calculan sumando las líneas, y se validan
     * contra `totalGravadoEsperado`/`totalIvaEsperado` (los mismos que ya gatillaron la
     * decisión de emitir factura y la validación del timbrado, más arriba en el llamador) antes
     * de guardar nada. Si no cuadran, no se emite la factura -- ni siquiera se toma su número.
     */
    /* Visibilidad de paquete (no private) a pedido de Joel, 27/10/2026, mismo criterio que
     * validarTimbradoVigente: PruebaEmisionFacturaPdf necesita recorrer el circuito real de
     * emisión dentro de UNA transacción que después revierte. Las puertas públicas
     * (confirmarCobro, obtenerPdfFactura) abren su propia conexión y commitean, así que no
     * sirven para una prueba sin rastro. No lo llama nadie fuera de este paquete. */
    Factura emitirFactura(Connection conexion, long idPago, Persona cliente, LocalDate fecha,
            List<LineaFactura> lineas, BigDecimal totalGravadoEsperado, BigDecimal totalIvaEsperado)
            throws SQLException {
        TotalesLineasFactura totales = calcularTotalesFactura(lineas);
        BigDecimal totalGravado = totales.getTotalGravado();
        BigDecimal totalIva = totales.getTotalIva();
        validarLineasFactura(lineas, totalGravado, totalIva, totalGravadoEsperado, totalIvaEsperado);

        // D-89: la numeración de facturas en Paraguay es correlativa durante TODO el timbrado,
        // sin reiniciarse por año calendario -- a diferencia del recibo (documento interno, sin
        // esa regla), acá NO se puede usar fecha.getYear(). Se usa el año en que arrancó la
        // vigencia del timbrado como clave del numerador: se mantiene fijo mientras el timbrado
        // siga siendo el mismo (fiscal.timbrado_vigencia_desde sin cambios) y arranca en 0 solo
        // cuando se carga un timbrado nuevo (una vigencia_desde distinta), que es exactamente
        // cuándo corresponde reiniciar la numeración. validarTimbradoVigente (ya llamada antes que
        // este método, D-77 punto 15) usa esta misma clave para el chequeo de "agotado" -- tienen
        // que quedar sincronizadas si esto se toca de nuevo.
        // D-116: el timbrado (y por lo tanto su vigencia_desde) es el vigente EN `fecha`, no "hoy"
        // -- para que un cobro histórico numere y facture con el timbrado que regía ese día.
        LocalDate timbradoDesdeFactura = obtenerTimbradoVigenteDesde(conexion, fecha);
        int anioNumeradorFactura = timbradoDesdeFactura.getYear();
        long numeroFacturaSecuencia = numeradorDAO.siguienteValor(conexion, "FACTURA", anioNumeradorFactura);

        Factura factura = new Factura();
        factura.setIdPago(idPago);
        factura.setTimbrado(servicioParametros.obtenerValorAFecha(conexion, "fiscal.timbrado_numero", fecha));
        factura.setEstablecimiento(servicioParametros.obtenerValor(conexion, "fiscal.establecimiento"));
        factura.setPuntoExpedicion(servicioParametros.obtenerValor(conexion, "fiscal.punto_expedicion"));
        factura.setNumero(String.format("%07d", numeroFacturaSecuencia));
        factura.setFecha(fecha);
        factura.setClienteNombre(cliente.getNombres() + " " + cliente.getApellidos());
        factura.setClienteDocumento(cliente.getCedulaIdentidad());
        factura.setTotalExenta(BigDecimal.ZERO);
        factura.setTotalGravada10(totalGravado);
        factura.setIva10(totalIva);
        factura.setTotal(totalGravado);
        long idFactura = facturaDAO.insertar(conexion, factura);
        factura.setIdFactura(idFactura);
        return factura;
    }

    /**
     * D-85 (hallazgo de Joel, 12/10/2026): total gravado al 10 % (neto + IVA de TODAS las
     * líneas -- "total" de una línea ya incluye su IVA, ver LineaFactura.getTotal()) y total de
     * IVA. Pura y pública para que cualquier otra construcción de una factura use el MISMO
     * cálculo que facturas.total_gravada_10/total, nunca uno rearmado a mano.
     */
    public static TotalesLineasFactura calcularTotalesFactura(List<LineaFactura> lineas) {
        BigDecimal totalGravado = BigDecimal.ZERO;
        BigDecimal totalIva = BigDecimal.ZERO;
        for (LineaFactura linea : lineas) {
            totalGravado = totalGravado.add(linea.getTotal());
            totalIva = totalIva.add(linea.getIva());
        }
        return new TotalesLineasFactura(totalGravado, totalIva);
    }

    /**
     * D-85: control antes de armar la factura -- la suma de las líneas (y de sus IVA) tiene que
     * coincidir EXACTO con los totales ya calculados (los que gatillaron la validación del
     * timbrado). Pura y estática para poder testearla sin base de datos.
     */
    static void validarLineasFactura(List<LineaFactura> lineas, BigDecimal totalGravadoLineas,
            BigDecimal totalIvaLineas, BigDecimal totalGravadoEsperado, BigDecimal totalIvaEsperado) {
        if (totalGravadoLineas.compareTo(totalGravadoEsperado) != 0
                || totalIvaLineas.compareTo(totalIvaEsperado) != 0) {
            throw new NegocioException("Las líneas de la factura (" + lineas.size() + ") no coinciden con el "
                    + "total calculado: gravado " + totalGravadoLineas + " (esperado " + totalGravadoEsperado
                    + "), IVA " + totalIvaLineas + " (esperado " + totalIvaEsperado + "). No se emite la factura.");
        }
    }

    /**
     * D-93 (E8): reconstruye las líneas de una factura YA EMITIDA para reimprimirla -- nunca se
     * guardaron (D-85: LineaFactura solo se usaba en memoria para validar antes de emitir, ver
     * validarLineasFactura). El neto de cada concepto SÍ está guardado por separado
     * (aplicaciones_pago.monto_{interes,moratorio,punitorio}), pero el IVA solo se guardó
     * combinado por cuota (monto_tributos) -- se deriva con la MISMA fórmula fija que ya se usó
     * al emitir (ServicioMotorFinanciero.iva, 10 % HALF_UP), nunca una recalculada de negocio.
     * Control (decisión de Joel): la suma de los 3 IVA derivados de una cuota tiene que coincidir
     * EXACTO con el monto_tributos guardado de esa cuota -- si no coincide, no se imprime un
     * número inventado, se corta con error.
     */
    public List<LineaFactura> reconstruirLineasFactura(Connection conexion, long idPago) throws SQLException {
        List<AplicacionPago> aplicaciones = aplicacionPagoDAO.listarPorPago(conexion, idPago);
        List<LineaFactura> lineas = new ArrayList<>();
        if (aplicaciones.isEmpty()) {
            return lineas;
        }
        // D-91: NO asumir el plan vigente -- un pago viejo puede haber tocado cuotas de un plan
        // ya superado por un anticipo posterior (queda congelado tal cual, nunca se muta), así
        // que el plan correcto es el de la cuota que el propio pago tocó, no "el de hoy".
        Long idCuotaDeReferencia = null;
        for (AplicacionPago aplicacion : aplicaciones) {
            if (aplicacion.getTipoAplicacion() == TipoAplicacion.CUOTA) {
                idCuotaDeReferencia = aplicacion.getIdCuota();
                break;
            }
        }
        if (idCuotaDeReferencia != null) {
            long idPlanPago = cuotaDAO.obtenerIdPlan(conexion, idCuotaDeReferencia);
            List<Cuota> cuotasDelPlan = cuotaDAO.listarPorPlan(conexion, idPlanPago);
            int totalCuotas = cuotasDelPlan.size();
            Map<Long, Integer> numeroCuotaPorId = new HashMap<>();
            for (Cuota cuota : cuotasDelPlan) {
                numeroCuotaPorId.put(cuota.getIdCuota(), cuota.getNumeroCuota());
            }
            for (AplicacionPago aplicacion : aplicaciones) {
                if (aplicacion.getTipoAplicacion() != TipoAplicacion.CUOTA) {
                    continue;
                }
                int numeroCuota = numeroCuotaPorId.get(aplicacion.getIdCuota());
                lineas.addAll(derivarLineasCuotaFactura(numeroCuota, totalCuotas, aplicacion.getMontoInteres(),
                        aplicacion.getMontoMoratorio(), aplicacion.getMontoPunitorio(), aplicacion.getMontoTributos()));
            }
        } else {
            // D-145 (E-2, pedido de Joel): sin ninguna aplicación de tipo CUOTA, este pago fue un
            // adelanto de capital (TipoAplicacion.ANTICIPO_CAPITAL) -- y SÍ puede llevar un
            // renglón de interés del período en curso: confirmarAnticipoCapital ya lo factura así
            // al emitir (D-77/D-85, construirLineasCuota), con el timbrado validado y el número
            // tomado para ESE renglón. El comentario que decía "ANTICIPO_CAPITAL nunca lleva
            // conceptos gravados (D-88)" ya no era cierto -- esta reconstrucción (usada para
            // mostrar/reimprimir, D-93/D-94) saltaba esas aplicaciones enteras, dejando la
            // factura sin ningún renglón aunque el total ya mostrara el IVA cobrado (bug real:
            // factura 001-001-0000011, CC-2026-00004). No es un cálculo nuevo: reusa el mismo
            // monto/IVA ya guardado en aplicaciones_pago, con el mismo control de IVA derivado
            // que ya usa derivarLineasCuotaFactura.
            for (AplicacionPago aplicacion : aplicaciones) {
                if (aplicacion.getTipoAplicacion() != TipoAplicacion.ANTICIPO_CAPITAL) {
                    continue;
                }
                Pago pagoDelAnticipo = pagoDAO.obtenerPorId(conexion, idPago);
                lineas.addAll(derivarLineasAnticipoFactura(pagoDelAnticipo.getFechaHoraPago().toLocalDate(),
                        aplicacion.getMontoInteres(), aplicacion.getMontoMoratorio(), aplicacion.getMontoPunitorio(),
                        aplicacion.getMontoTributos()));
            }
        }
        return lineas;
    }

    /** Pura -- ver reconstruirLineasFactura. Paquete para poder testearla sin base de datos. */
    static List<LineaFactura> derivarLineasCuotaFactura(int numeroCuota, int totalCuotas, BigDecimal montoInteres,
            BigDecimal montoMoratorio, BigDecimal montoPunitorio, BigDecimal montoTributosGuardado) {
        BigDecimal ivaInteres = ServicioMotorFinanciero.iva(montoInteres);
        BigDecimal ivaMoratorio = ServicioMotorFinanciero.iva(montoMoratorio);
        BigDecimal ivaPunitorio = ServicioMotorFinanciero.iva(montoPunitorio);
        BigDecimal sumaIvaDerivado = ivaInteres.add(ivaMoratorio).add(ivaPunitorio);
        if (sumaIvaDerivado.compareTo(montoTributosGuardado) != 0) {
            throw new IllegalStateException("El IVA derivado de la cuota " + numeroCuota + "/" + totalCuotas + " ("
                    + sumaIvaDerivado + ") no coincide con el guardado (" + montoTributosGuardado
                    + "). No se reimprime la factura.");
        }
        List<LineaFactura> lineas = new ArrayList<>();
        // B102: el concepto sale del dueño; el texto es el mismo de siempre.
        if (montoInteres.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeCuota(ConceptoGravado.INTERES, numeroCuota, totalCuotas),
                    montoInteres, ivaInteres));
        }
        if (montoMoratorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeCuota(ConceptoGravado.MORATORIO, numeroCuota, totalCuotas),
                    montoMoratorio, ivaMoratorio));
        }
        if (montoPunitorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeCuota(ConceptoGravado.PUNITORIO, numeroCuota, totalCuotas),
                    montoPunitorio, ivaPunitorio));
        }
        return lineas;
    }

    /**
     * D-145 (E-2, pedido de Joel): mismo criterio que derivarLineasCuotaFactura (IVA derivado con
     * ServicioMotorFinanciero.iva, control exacto contra el guardado), para un adelanto de
     * capital -- que no está atado a una cuota (D-88), así que la línea se describe con la fecha
     * del pago en vez de un número de cuota. Pura -- ver reconstruirLineasFactura. Paquete para
     * poder testearla sin base de datos.
     */
    static List<LineaFactura> derivarLineasAnticipoFactura(LocalDate fecha, BigDecimal montoInteres,
            BigDecimal montoMoratorio, BigDecimal montoPunitorio, BigDecimal montoTributosGuardado) {
        BigDecimal ivaInteres = ServicioMotorFinanciero.iva(montoInteres);
        BigDecimal ivaMoratorio = ServicioMotorFinanciero.iva(montoMoratorio);
        BigDecimal ivaPunitorio = ServicioMotorFinanciero.iva(montoPunitorio);
        BigDecimal sumaIvaDerivado = ivaInteres.add(ivaMoratorio).add(ivaPunitorio);
        if (sumaIvaDerivado.compareTo(montoTributosGuardado) != 0) {
            throw new IllegalStateException("El IVA derivado del anticipo de capital (" + sumaIvaDerivado
                    + ") no coincide con el guardado (" + montoTributosGuardado + "). No se reimprime la factura.");
        }
        List<LineaFactura> lineas = new ArrayList<>();
        // B102: mismo dueño, mismo texto de siempre.
        String fechaFormateada = FormateadorFechas.comoDdMmAaaa(fecha);
        if (montoInteres.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeAnticipo(ConceptoGravado.INTERES, fechaFormateada),
                    montoInteres, ivaInteres));
        }
        if (montoMoratorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeAnticipo(ConceptoGravado.MORATORIO, fechaFormateada),
                    montoMoratorio, ivaMoratorio));
        }
        if (montoPunitorio.compareTo(BigDecimal.ZERO) > 0) {
            lineas.add(new LineaFactura(conceptoFacturaDeAnticipo(ConceptoGravado.PUNITORIO, fechaFormateada),
                    montoPunitorio, ivaPunitorio));
        }
        return lineas;
    }

    /**
     * E8 (reimpresión, Bloque 2): historial de cobros de un préstamo, con los datos que necesita
     * la ficha para ofrecer "reimprimir" cada recibo y, si corresponde, su factura o su constancia
     * de cancelación (Pago.idLiquidacionAnticipada solo se llena en CANCELACION/ANTICIPO, D-93/D-94;
     * la constancia como documento aparte solo existe para CANCELACION -- confirmarCancelacionTotal
     * es la única que asigna numero_constancia).
     */
    public List<FilaHistorialCobranza> listarHistorialCobros(Connection conexion, long idPrestamo)
            throws SQLException {
        List<FilaHistorialCobranza> historial = new ArrayList<>();
        // Punto 15 (pedido de Joel): con más de un timbrado a lo largo del tiempo, dos facturas de
        // timbrados distintos pueden compartir el mismo número (ej. "001-001-0000002") -- la
        // etiqueta muestra el timbrado cuando NO es el vigente hoy, para no verlas idénticas.
        String timbradoVigenteHoy = servicioParametros.obtenerValor(conexion, "fiscal.timbrado_numero");
        // E9/Bloque 5 (RN-13): "puede solicitar reversión" es solo la PRIMERA fila CONFIRMADO que
        // aparece recorriendo la lista (ya viene del más reciente al más viejo) -- si la más
        // reciente en el tiempo ya está REVERTIDA, el botón pasa a la que sigue siendo, en los
        // hechos, la última operación vigente del préstamo (mismo criterio que
        // ServicioReversiones.esLaUltimaOperacion). Nunca se ofrece si ya hay una solicitud viva
        // (SOLICITADA o AUTORIZADA) para ese mismo pago.
        boolean yaEncontroLaUltimaConfirmada = false;
        // 13/10/2026 (pedido de Joel, reprueba en pantalla): con el cierre documental ya
        // COMPLETO, la columna "Reversión" no debe ofrecer ni casilla ni motivo ni botón -- el
        // pagaré ya se devolvió al cliente (ver ServicioReversiones.validarPuedeSolicitarReversion).
        PrestamoCredito prestamoDelHistorial = prestamoDAO.obtenerPorId(conexion, idPrestamo);
        boolean cierreDocumentalCompletoHistorial = prestamoDelHistorial != null
                && "COMPLETO".equals(prestamoDelHistorial.getEstadoCierreDocumental());
        for (Pago pago : pagoDAO.listarTodosPorPrestamo(conexion, idPrestamo)) {
            boolean revertido = pago.getEstadoPago() == EstadoConfirmacionPago.REVERTIDO;
            Factura factura = facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
            Long idLiquidacion = null;
            String numeroConstancia = null;
            if (pago.getTipo() == TipoPago.CANCELACION && pago.getIdLiquidacionAnticipada() != null) {
                LiquidacionAnticipada liquidacion = liquidacionAnticipadaDAO.obtenerPorId(conexion,
                        pago.getIdLiquidacionAnticipada());
                if (liquidacion != null && liquidacion.getNumeroConstancia() != null) {
                    idLiquidacion = liquidacion.getIdLiquidacion();
                    numeroConstancia = liquidacion.getNumeroConstancia();
                }
            }
            // Corrección de Joel (tras la prueba de la parte A): la columna "Reversión" no puede
            // quedar vacía mientras hay una solicitud pendiente o rechazada -- se lee la fila UNA
            // sola vez y se deriva todo de ahí (nunca dos consultas separadas que podrían
            // desincronizarse entre sí).
            ReversionPago reversionDelPago = reversionPagoDAO.obtenerPorIdPago(conexion, pago.getIdPago());
            boolean puedeSolicitarReversion = false;
            boolean noAdmiteReversionPorCierreDocumental = false;
            if (!revertido && !yaEncontroLaUltimaConfirmada) {
                yaEncontroLaUltimaConfirmada = true;
                // Punto 1 (RF25, Opción A, pedido de Joel): tras un rechazo se puede volver a
                // solicitar con un motivo nuevo -- ServicioReversiones.solicitarReversion
                // reutiliza la misma fila (reversiones_pago.id_pago es UNIQUE). Solo una SOLICITADA
                // o una AUTORIZADA bloquean un nuevo pedido.
                boolean sinSolicitudViva = reversionDelPago == null
                        || reversionDelPago.getEstado() == EstadoReversion.RECHAZADA;
                if (sinSolicitudViva && cierreDocumentalCompletoHistorial) {
                    noAdmiteReversionPorCierreDocumental = true;
                } else {
                    puedeSolicitarReversion = sinSolicitudViva;
                }
            }
            // AUTORIZADA no se repite acá: revertido/isRevertido() ya lo muestra con su propio badge.
            String estadoReversionSolicitud = reversionDelPago == null || reversionDelPago.getEstado() == EstadoReversion.AUTORIZADA
                    ? null : reversionDelPago.getEstado().name();
            String motivoRechazoReversion = reversionDelPago != null
                    && reversionDelPago.getEstado() == EstadoReversion.RECHAZADA ? reversionDelPago.getMotivoRechazo() : null;
            String numeroFacturaMostrado = null;
            if (factura != null) {
                numeroFacturaMostrado = factura.getTimbrado().equals(timbradoVigenteHoy)
                        ? factura.getNumeroCompleto()
                        : "Timbrado " + factura.getTimbrado() + " – " + factura.getNumeroCompleto();
            }
            // Punto 2 (RF25, pedido de Joel): reimprimir la constancia de reversión desde acá --
            // solo tiene sentido si ya hay una reversión AUTORIZADA (revertido == true implica eso).
            Long idReversionAutorizada = revertido && reversionDelPago != null
                    ? reversionDelPago.getIdReversionPago() : null;
            historial.add(new FilaHistorialCobranza(pago.getIdPago(), pago.getNumeroRecibo(),
                    FormateadorFechas.comoDdMmAaaaHhMm(pago.getFechaHoraPago()), pago.getTipo().getEtiqueta(),
                    FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()),
                    factura == null ? null : factura.getIdFactura(),
                    numeroFacturaMostrado, idLiquidacion, numeroConstancia,
                    revertido, factura != null && factura.getEstado() == EstadoFactura.ANULADA,
                    puedeSolicitarReversion, estadoReversionSolicitud, motivoRechazoReversion,
                    idReversionAutorizada, noAdmiteReversionPorCierreDocumental));
        }
        return historial;
    }

    // =====================================================================
    // D-94 (E8): recibo, factura y constancia -- "generar al primer pedido y guardar". La
    // generación NUNCA va dentro de la transacción de cobro/desembolso/cierre: si Jasper falla,
    // la operación de dinero ya quedó hecha y se puede reintentar la impresión aparte.
    // =====================================================================

    @FunctionalInterface
    private interface GeneradorPdf {
        byte[] generar(Connection conexion) throws SQLException;
    }

    /**
     * Bloquea la fila dueña (FOR UPDATE, transacción propia, corta) y entrega el archivo si ya
     * está vinculado; si no, lo genera, lo guarda en archivos_binarios y lo vincula recién ahí.
     * Si dos pedidos llegan a la vez, el segundo espera este lock y usa el archivo que generó el
     * primero -- nunca quedan dos PDF guardados para la misma operación.
     */
    private byte[] obtenerOGenerarPdf(String tabla, String columnaId, String columnaArchivo, long id,
            long idUsuario, String nombreArchivo, GeneradorPdf generador) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Long idArchivo = archivoComprobanteDAO.obtenerIdArchivoConLock(conexion, tabla, columnaId,
                        columnaArchivo, id);
                byte[] pdf;
                if (idArchivo != null) {
                    pdf = archivoBinarioDAO.obtenerContenido(conexion, idArchivo);
                } else {
                    pdf = generador.generar(conexion);
                    ArchivoBinario archivo = new ArchivoBinario();
                    archivo.setNombreArchivo(nombreArchivo);
                    archivo.setMimeType("application/pdf");
                    archivo.setExtension("pdf");
                    archivo.setTamanoBytes(pdf.length);
                    archivo.setHashSha256(ServicioDocumentos.calcularHashSha256(pdf));
                    archivo.setOrigenArchivo(OrigenArchivo.JASPER);
                    archivo.setCargadoPor(idUsuario);
                    long nuevoIdArchivo = archivoBinarioDAO.insertar(conexion, archivo, pdf);
                    archivoComprobanteDAO.vincular(conexion, tabla, columnaId, columnaArchivo, id, nuevoIdArchivo);
                }
                conexion.commit();
                return pdf;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private byte[] llenarYExportar(String nombreArchivoJasper, Map<String, Object> parametros,
            JRDataSource datasource) {
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

    /** D-94: datos de empresa vigentes en la fecha del DOCUMENTO, nunca la de hoy. Punto 16b
     * (pedido de Joel): empresa.* no se graba en ninguna entidad (a diferencia del timbrado, que
     * la factura sí guarda) -- si no hay versión vigente en esa fecha, se usa la más antigua
     * disponible en vez de rechazar la reimpresión (obtenerValorAFechaOMasAntiguo). */
    private Map<String, Object> parametrosEmpresaAFecha(Connection conexion, LocalDate fecha) throws SQLException {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.razon_social", fecha));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.ruc", fecha));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.domicilio", fecha));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.telefono", fecha));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.whatsapp", fecha));
        parametros.put("EMPRESA_CORREO", servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.correo", fecha));
        return parametros;
    }

    /**
     * Punto 16b (pedido de Joel): TIMBRADO_NUMERO/DESDE/HASTA para el PDF de una factura o su nota
     * de anulación -- usa SIEMPRE el número de timbrado que la propia factura ya tiene grabado
     * (factura.getTimbrado(), nunca se vuelve a resolver por fecha: no puede fallar). Para mostrar
     * la VIGENCIA de ESE timbrado puntual (no grabada en la factura), busca la fila de
     * fiscal.timbrado_numero cuyo VALOR es ese mismo número y usa SU PROPIO vigente_desde como
     * fecha de referencia para leer timbrado_vigencia_desde/hasta -- por construcción, esa fecha
     * cae siempre dentro del período correcto (nunca en el hueco que causaba el bug real, factura
     * 001-001-0000001 del timbrado anterior).
     */
    private void agregarTimbradoDeFactura(Connection conexion, Factura factura, Map<String, Object> parametros)
            throws SQLException {
        parametros.put("TIMBRADO_NUMERO", factura.getTimbrado());
        ParametroSistema filaTimbrado = parametroDAO.obtenerPorClaveYValor(conexion, "fiscal.timbrado_numero",
                factura.getTimbrado());
        LocalDate fechaReferencia = filaTimbrado != null ? filaTimbrado.getVigenteDesde() : factura.getFecha();
        LocalDate timbradoDesde = obtenerTimbradoVigenteDesde(conexion, fechaReferencia);
        LocalDate timbradoHasta = obtenerTimbradoVigenteHasta(conexion, fechaReferencia);
        parametros.put("TIMBRADO_DESDE", FormateadorFechas.comoDdMmAaaa(timbradoDesde));
        parametros.put("TIMBRADO_HASTA", FormateadorFechas.comoDdMmAaaa(timbradoHasta));
    }

    /** D-94: PDF del recibo de un pago -- la primera vez lo genera, después entrega el guardado.
     * Hallazgo de Joel (RF25, 12/10/2026): un recibo REVERTIDO nunca sirve el PDF cacheado -- ese
     * PDF puede ser de ANTES de la reversión (el caso normal: se imprimió al cobrar, mucho antes
     * de que se revirtiera) y mostraría el cobro como válido, sin la marca de ANULADO. Se
     * regenera siempre que el pago esté REVERTIDO (nunca se congela esa versión tampoco: si en
     * el futuro cambiara algo del texto de la marca, el próximo pedido ya saldría actualizado). */
    public byte[] obtenerPdfRecibo(long idPago, long idUsuario) throws SQLException {
        try (Connection conexionRevisar = FabricaConexionBD.obtenerConexion()) {
            Pago pagoRevisar = pagoDAO.obtenerPorId(conexionRevisar, idPago);
            if (pagoRevisar != null && pagoRevisar.getEstadoPago() == EstadoConfirmacionPago.REVERTIDO) {
                return generarPdfRecibo(conexionRevisar, idPago);
            }
        }
        return obtenerOGenerarPdf("pagos", "id_pago", "id_archivo_recibo", idPago, idUsuario,
                "recibo_" + idPago + ".pdf", conexion -> generarPdfRecibo(conexion, idPago));
    }

    private byte[] generarPdfRecibo(Connection conexion, long idPago) throws SQLException {
        Pago pago = pagoDAO.obtenerPorId(conexion, idPago);
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        Usuario emisor = servicioUsuarios.buscarPorId(conexion, pago.getRegistradoPor());
        LocalDate fechaPago = pago.getFechaHoraPago().toLocalDate();
        Factura factura = facturaDAO.obtenerPorPago(conexion, idPago);

        boolean esCancelacion = pago.getTipo() == TipoPago.CANCELACION;
        boolean esAnticipo = pago.getTipo() == TipoPago.ANTICIPO;
        boolean esParcial = pago.getTipo() == TipoPago.PARCIAL;

        Map<String, Object> parametros = parametrosEmpresaAFecha(conexion, fechaPago);
        parametros.put("NUMERO_RECIBO", pago.getNumeroRecibo());
        parametros.put("FECHA_HORA", FormateadorFechas.comoDdMmAaaaHhMm(pago.getFechaHoraPago()));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        parametros.put("TIENE_FACTURA", factura != null);
        parametros.put("FACTURA_NUMERO", factura == null ? "" : factura.getNumeroCompleto());
        parametros.put("EFECTIVO_RECIBIDO", FormateadorNumeros.comoGuaranies(pago.getMontoRecibido()));
        parametros.put("TIENE_VUELTO", pago.getMontoVuelto().compareTo(BigDecimal.ZERO) > 0);
        parametros.put("VUELTO", FormateadorNumeros.comoGuaranies(pago.getMontoVuelto()));
        parametros.put("APLICADO", FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()));
        parametros.put("APLICADO_LETRAS",
                ConversorNumerosALetras.comoLetrasConGuaranies(pago.getMontoAplicado()).toUpperCase(new Locale("es")));
        // C37 (01/11/2026, decisión de Joel, opción a): el "Son:" del recibo pasa a ser el EFECTIVO
        // RECIBIDO, que es lo que el cliente firma haber entregado -- antes repetía el monto
        // aplicado a la deuda y, con redondeo (D-141), los dos no son el mismo número (recibió
        // 180.600 y se aplicaron 180.609). Lo aplicado no se saca: sigue en su propio renglón
        // ("Aplicado: ..."), junto con el vuelto y el redondeo. Ningún monto cambia: es el mismo
        // pago.getMontoRecibido() que ya se muestra en EFECTIVO_RECIBIDO, ahora también en letras.
        parametros.put("RECIBIDO_LETRAS",
                ConversorNumerosALetras.comoLetrasConGuaranies(pago.getMontoRecibido()).toUpperCase(new Locale("es")));
        // D-141 (SEDECO, redondeo del efectivo): montoRedondeo es 0 si no corresponde (incluida la
        // variante ANTICIPO, que no aplica redondeo -- ver confirmarAnticipoCapital).
        parametros.put("TIENE_REDONDEO", pago.getMontoRedondeo().compareTo(BigDecimal.ZERO) > 0);
        parametros.put("REDONDEO", FormateadorNumeros.comoGuaranies(pago.getMontoRedondeo()));
        parametros.put("ES_CANCELACION", esCancelacion);
        parametros.put("ES_ANTICIPO", esAnticipo);
        parametros.put("USUARIO_EMISOR", emisor == null ? "-" : emisor.getNombres() + " " + emisor.getApellidos());
        // Corrección de Joel (Bloque 2, revisión): "Emitido por" tiene que mostrar la fecha/hora de
        // LA OPERACIÓN, no la del momento en que se generó/imprimió el PDF por primera vez (D-94)
        // -- antes usaba Reloj.ahora(), por eso un recibo de un pago de hace días mostraba la
        // hora de hoy. La constancia ya usaba fecha_calculo (el momento real), este debía usar
        // fecha_hora_pago igual.
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(pago.getFechaHoraPago()));

        // Punto 1 (RF25, hallazgo de Joel, 12/10/2026): marca grande "ANULADO POR REVERSIÓN" --
        // nunca sale como un recibo válido.
        boolean pagoRevertido = pago.getEstadoPago() == EstadoConfirmacionPago.REVERTIDO;
        parametros.put("REVERTIDO", pagoRevertido);
        // Punto 7 (RF25, pedido de Joel): sello diagonal, además de la franja (ahora en el
        // pageHeader del .jrxml, condicionado por REVERTIDO -- nunca desplaza nada en un recibo
        // normal, sin este sello).
        parametros.put("MARCA_AGUA", pagoRevertido ? GeneradorInformesPdf.construirMarcaAguaDiagonal("ANULADO") : null);
        if (pagoRevertido) {
            ReversionPago reversionDelRecibo = reversionPagoDAO.obtenerPorIdPago(conexion, idPago);
            Usuario autorizadorReversion = reversionDelRecibo == null || reversionDelRecibo.getAutorizadoPor() == null
                    ? null : servicioUsuarios.buscarPorId(conexion, reversionDelRecibo.getAutorizadoPor());
            parametros.put("REVERSION_FECHA", reversionDelRecibo == null
                    || reversionDelRecibo.getFechaHoraReversion() == null ? "-"
                    : FormateadorFechas.comoDdMmAaaaHhMm(reversionDelRecibo.getFechaHoraReversion()));
            parametros.put("REVERSION_AUTORIZADO_POR", autorizadorReversion == null ? "-"
                    : autorizadorReversion.getNombres() + " " + autorizadorReversion.getApellidos());
            parametros.put("REVERSION_MOTIVO", reversionDelRecibo == null ? "-" : reversionDelRecibo.getMotivoReversion());
        } else {
            parametros.put("REVERSION_FECHA", "");
            parametros.put("REVERSION_AUTORIZADO_POR", "");
            parametros.put("REVERSION_MOTIVO", "");
        }

        // PARCIAL: promesa -- solo si hay una gestión vinculada con seguridad (D-95); nunca se
        // adivina por cercanía de fecha.
        GestionCobranza gestion = esParcial ? gestionCobranzaDAO.obtenerPorPago(conexion, idPago) : null;
        parametros.put("TIENE_PROMESA", gestion != null);
        if (gestion != null) {
            parametros.put("PROMESA_FECHA", FormateadorFechas.comoDdMmAaaa(gestion.getFechaPromesa()));
            parametros.put("PROMESA_MONTO", FormateadorNumeros.comoGuaranies(gestion.getMontoPrometido()));
        }

        List<FilaReciboCuota> filas = new ArrayList<>();
        if (esAnticipo) {
            // Variante anticipo (sin tabla de cuotas): interés del período + resumen del plan nuevo.
            LiquidacionAnticipada liquidacion = liquidacionAnticipadaDAO.obtenerPorId(conexion,
                    pago.getIdLiquidacionAnticipada());
            parametros.put("ANTICIPO_INTERES", FormateadorNumeros.comoGuaranies(liquidacion.getInteresDevengado()));
            parametros.put("ANTICIPO_IVA", FormateadorNumeros.comoGuaranies(liquidacion.getTributosAplicables()));
            BigDecimal montoACapital = pago.getMontoAplicado().subtract(liquidacion.getInteresDevengado())
                    .subtract(liquidacion.getTributosAplicables());
            parametros.put("ANTICIPO_CAPITAL", FormateadorNumeros.comoGuaranies(montoACapital));
            // Corrección de Joel (Bloque 2, revisión): un recibo de anticipo SIN el plan
            // regenerado (dato obligatorio para el resumen) nunca se imprime -- antes esto se
            // salteaba en silencio y el PDF quedaba congelado con "null cuotas restantes, cuota
            // null...". La excepción sube por obtenerOGenerarPdf, que ya hace rollback antes de
            // guardar nada en archivos_binarios (D-94): "nunca congelar un comprobante incompleto".
            PlanPago planNuevo = planPagoDAO.obtenerPorLiquidacionOrigen(conexion, liquidacion.getIdLiquidacion());
            if (planNuevo == null) {
                throw new IllegalStateException("No se encontró el plan regenerado por este anticipo "
                        + "(liquidación " + liquidacion.getIdLiquidacion() + "); no se genera el recibo.");
            }
            List<Cuota> cuotasNuevoPlan = cuotaDAO.listarPorPlan(conexion, planNuevo.getIdPlanPago());
            if (cuotasNuevoPlan.isEmpty()) {
                throw new IllegalStateException("El plan regenerado por este anticipo (id "
                        + planNuevo.getIdPlanPago() + ") no tiene cuotas; no se genera el recibo.");
            }
            parametros.put("ANTICIPO_CUOTAS_RESTANTES", cuotasNuevoPlan.size());
            // Pedido de Joel: la cuota se rotula igual que en el comprobante de desembolso --
            // capital + interés por separado, con el IVA de interés aparte (nunca el total con IVA
            // ya incluido, que confundiría con el monto realmente exigible cada mes).
            Cuota primeraCuotaNuevoPlan = cuotasNuevoPlan.get(0);
            BigDecimal cuotaCapitalMasInteres = primeraCuotaNuevoPlan.getCapitalProgramado()
                    .add(primeraCuotaNuevoPlan.getInteresProgramado());
            parametros.put("ANTICIPO_CUOTA_NUEVA",
                    FormateadorNumeros.comoGuaranies(cuotaCapitalMasInteres) + " + IVA s/ interés");
            // D-145 (P-12, pedido de Joel): además del resumen de arriba, una línea aparte con la
            // fecha y el monto YA con IVA incluido de la próxima cuota (getTotalProgramado(), lo
            // mismo que usa la ficha en "Si adelanta..." -- misma fuente, un solo lugar que calcula
            // esto) -- más clara que "ANTICIPO_CUOTA_NUEVA" (capital+interés sin IVA).
            // C39 (29/10/2026): la fecha sale de fechaProximaCuotaNuevoPlan, el único dueño.
            parametros.put("ANTICIPO_PROXIMA_CUOTA_FECHA",
                    fechaProximaCuotaNuevoPlan(primeraCuotaNuevoPlan));
            parametros.put("ANTICIPO_PROXIMA_CUOTA_MONTO",
                    FormateadorNumeros.comoGuaranies(primeraCuotaNuevoPlan.getTotalProgramado()));
            parametros.put("ANTICIPO_ULTIMO_VENCIMIENTO",
                    FormateadorFechas.comoDdMmAaaa(cuotasNuevoPlan.get(cuotasNuevoPlan.size() - 1).getFechaVencimiento()));
            // Pedido de Joel: faltaba el saldo de capital después del anticipo (sí se calcula y
            // guarda en la liquidación desde D-93, solo faltaba mostrarlo acá).
            parametros.put("SALDO_CAPITAL_DESPUES", FormateadorNumeros.comoGuaranies(liquidacion.getNuevoSaldoCapital()));
            parametros.put("CUOTAS_PENDIENTES", 0);
            parametros.put("TIENE_PROXIMO_VENCIMIENTO", false);
            parametros.put("PROXIMA_CUOTA_VENCIDA", false);
        } else {
            // NORMAL/PARCIAL/CANCELACION: tabla de cuotas leída de aplicaciones_pago (sin
            // derivar nada -- cada columna ya está guardada por separado, a diferencia de la
            // factura). El plan usado es el que este pago realmente tocó (D-91), no el vigente.
            List<AplicacionPago> aplicaciones = aplicacionPagoDAO.listarPorPago(conexion, idPago);
            Long idCuotaReferencia = null;
            for (AplicacionPago aplicacion : aplicaciones) {
                if (aplicacion.getTipoAplicacion() == TipoAplicacion.CUOTA) {
                    idCuotaReferencia = aplicacion.getIdCuota();
                    break;
                }
            }
            Map<Long, Integer> numeroCuotaPorId = new HashMap<>();
            Map<Long, Cuota> cuotaPorId = new HashMap<>();
            List<Cuota> cuotasDelPlan = Collections.emptyList();
            if (idCuotaReferencia != null) {
                long idPlanPago = cuotaDAO.obtenerIdPlan(conexion, idCuotaReferencia);
                cuotasDelPlan = cuotaDAO.listarPorPlan(conexion, idPlanPago);
                for (Cuota cuota : cuotasDelPlan) {
                    numeroCuotaPorId.put(cuota.getIdCuota(), cuota.getNumeroCuota());
                    cuotaPorId.put(cuota.getIdCuota(), cuota);
                }
            }
            int totalCuotasPlan = cuotasDelPlan.size();
            BigDecimal totalCapital = BigDecimal.ZERO;
            BigDecimal totalInteres = BigDecimal.ZERO;
            BigDecimal totalMoratorio = BigDecimal.ZERO;
            BigDecimal totalPunitorio = BigDecimal.ZERO;
            BigDecimal totalIvaCuotas = BigDecimal.ZERO;
            BigDecimal totalFilas = BigDecimal.ZERO;
            Cuota ultimaCuotaTocada = null;
            for (AplicacionPago aplicacion : aplicaciones) {
                if (aplicacion.getTipoAplicacion() != TipoAplicacion.CUOTA) {
                    continue;
                }
                Integer numeroCuota = numeroCuotaPorId.get(aplicacion.getIdCuota());
                filas.add(new FilaReciboCuota(numeroCuota + "/" + totalCuotasPlan,
                        FormateadorNumeros.comoGuaranies(aplicacion.getMontoCapital()),
                        FormateadorNumeros.comoGuaranies(aplicacion.getMontoInteres()),
                        FormateadorNumeros.comoGuaranies(aplicacion.getMontoMoratorio()),
                        FormateadorNumeros.comoGuaranies(aplicacion.getMontoPunitorio()),
                        FormateadorNumeros.comoGuaranies(aplicacion.getMontoTributos()),
                        FormateadorNumeros.comoGuaranies(aplicacion.getSubtotalAplicado())));
                totalCapital = totalCapital.add(aplicacion.getMontoCapital());
                totalInteres = totalInteres.add(aplicacion.getMontoInteres());
                totalMoratorio = totalMoratorio.add(aplicacion.getMontoMoratorio());
                totalPunitorio = totalPunitorio.add(aplicacion.getMontoPunitorio());
                totalIvaCuotas = totalIvaCuotas.add(aplicacion.getMontoTributos());
                totalFilas = totalFilas.add(aplicacion.getSubtotalAplicado());
                ultimaCuotaTocada = cuotaPorId.get(aplicacion.getIdCuota());
            }
            parametros.put("TOTAL_CAPITAL", FormateadorNumeros.comoGuaranies(totalCapital));
            parametros.put("TOTAL_INTERES", FormateadorNumeros.comoGuaranies(totalInteres));
            parametros.put("TOTAL_MORATORIO", FormateadorNumeros.comoGuaranies(totalMoratorio));
            parametros.put("TOTAL_PUNITORIO", FormateadorNumeros.comoGuaranies(totalPunitorio));
            parametros.put("TOTAL_IVA_CUOTAS", FormateadorNumeros.comoGuaranies(totalIvaCuotas));
            parametros.put("TOTAL_FILAS", FormateadorNumeros.comoGuaranies(totalFilas));

            if (!esCancelacion && ultimaCuotaTocada != null) {
                // D-145 (E-1, pedido de Joel): el saldo de capital después del pago se calcula en
                // vivo -- suma de capital_programado - capital_pagado de TODAS las cuotas del
                // plan que este pago realmente tocó (D-91, mismas cuotasDelPlan ya cargadas
                // arriba; misma cuenta que ya usa ServicioReportes.calcularTotalesEstadoCuenta
                // para el estado de cuenta). Antes se leía cuota.saldo_capital_posterior, una
                // columna fija, calculada una sola vez al generar el plan (el saldo que quedaría
                // si ESA cuota se pagara COMPLETA) -- un pago parcial nunca la actualiza
                // (CuotaDAO.actualizarTrasPago no la toca), así que dos pagos parciales seguidos
                // sobre la misma cuota (ej. CC-2026-00005) mostraban el mismo saldo congelado en
                // los dos recibos.
                parametros.put("SALDO_CAPITAL_DESPUES",
                        FormateadorNumeros.comoGuaranies(calcularSaldoCapitalPendiente(cuotasDelPlan)));
                int pendientes = 0;
                Cuota proximaCuota = null;
                for (Cuota cuota : cuotasDelPlan) {
                    if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                        pendientes++;
                        if (proximaCuota == null) {
                            proximaCuota = cuota;
                        }
                    }
                }
                parametros.put("CUOTAS_PENDIENTES", pendientes);
                parametros.put("TIENE_PROXIMO_VENCIMIENTO", proximaCuota != null);
                if (proximaCuota != null) {
                    parametros.put("PROXIMA_CUOTA", proximaCuota.getNumeroCuota() + "/" + totalCuotasPlan);
                    parametros.put("PROXIMO_VENCIMIENTO", FormateadorFechas.comoDdMmAaaa(proximaCuota.getFechaVencimiento()));
                    // D-145 (P-9, pedido de Joel): si la cuota que queda pendiente ya estaba
                    // vencida a la fecha de este pago (típico después de un pago parcial, D-145
                    // P-6), el recibo lo dice en vez de llamarla "próximo vencimiento" -- con lo
                    // que de verdad resta de ESA cuota (capital+interés+mora+IVA pendientes),
                    // reusando calcularDeudaExigible (sin tocarlo) en vez de rearmar esa cuenta
                    // acá. "Vencida" con el mismo criterio que el resto del sistema:
                    // fechaVencimiento anterior a la fecha de este pago (D-77 punto 16, nunca
                    // Reloj.hoy() dentro de un cálculo ya hecho).
                    boolean proximaCuotaVencida = proximaCuota.getFechaVencimiento().isBefore(fechaPago);
                    parametros.put("PROXIMA_CUOTA_VENCIDA", proximaCuotaVencida);
                    if (proximaCuotaVencida) {
                        int diasGraciaResta = servicioParametros.obtenerValorEntero(conexion,
                                "credito.dias_gracia_mora");
                        Prestamo prestamoMotorResta = cargarPrestamoParaMotor(conexion, pago.getIdPrestamo());
                        ResultadoDeudaExigible exigibleResta = servicioMotorFinanciero.calcularDeudaExigible(
                                prestamoMotorResta, fechaPago, diasGraciaResta);
                        BigDecimal restaProximaCuota = BigDecimal.ZERO;
                        for (DetalleCuotaExigible detalleResta : exigibleResta.getDetalles()) {
                            if (detalleResta.getNumeroCuota() == proximaCuota.getNumeroCuota()) {
                                restaProximaCuota = detalleResta.getTotalExigible();
                                break;
                            }
                        }
                        parametros.put("PROXIMA_CUOTA_RESTA", FormateadorNumeros.comoGuaranies(restaProximaCuota));
                    }
                }
            } else {
                parametros.put("SALDO_CAPITAL_DESPUES", "0 Gs.");
                parametros.put("CUOTAS_PENDIENTES", 0);
                parametros.put("TIENE_PROXIMO_VENCIMIENTO", false);
                parametros.put("PROXIMA_CUOTA_VENCIDA", false);
            }
        }

        return llenarYExportar("recibo.jasper", parametros, new JRBeanCollectionDataSource(filas));
    }

    /** D-94: PDF de una factura ya emitida. Punto 3 (RF25, hallazgo de Joel, 12/10/2026): mismo
     * criterio que obtenerPdfRecibo -- una factura ANULADA nunca sirve el PDF cacheado (puede ser
     * de antes de la anulación). */
    public byte[] obtenerPdfFactura(long idFactura, long idUsuario) throws SQLException {
        try (Connection conexionRevisar = FabricaConexionBD.obtenerConexion()) {
            Factura facturaRevisar = facturaDAO.obtenerPorId(conexionRevisar, idFactura);
            if (facturaRevisar != null && facturaRevisar.getEstado() == EstadoFactura.ANULADA) {
                return generarPdfFactura(conexionRevisar, idFactura);
            }
        }
        return obtenerOGenerarPdf("facturas", "id_factura", "id_archivo", idFactura, idUsuario,
                "factura_" + idFactura + ".pdf", conexion -> generarPdfFactura(conexion, idFactura));
    }

    /* Visibilidad de paquete (no private) por el mismo motivo que emitirFactura: ver el
     * comentario de ese método. */
    byte[] generarPdfFactura(Connection conexion, long idFactura) throws SQLException {
        Factura factura = facturaDAO.obtenerPorId(conexion, idFactura);
        Pago pago = pagoDAO.obtenerPorId(conexion, factura.getIdPago());
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        // Lote 2, L2-A Parte 2, punto 3 (14/10/2026, pedido de Joel, autorizado): "Emitido por"
        // del pie -- mismo dato que recibo.jrxml (línea ~1884), el usuario que registró el pago,
        // nunca el usuario que reimprime. Parámetros NUEVOS (PIE_*), sin tocar FECHA_EMISION
        // (dato legal del cuerpo, "Fecha de emisión", sin hora).
        Usuario emisorPie = servicioUsuarios.buscarPorId(conexion, pago.getRegistradoPor());

        List<LineaFactura> lineas = reconstruirLineasFactura(conexion, factura.getIdPago());
        List<FilaFacturaReporte> filas = new ArrayList<>();
        for (LineaFactura linea : lineas) {
            filas.add(new FilaFacturaReporte(linea.getDescripcion(),
                    FormateadorNumeros.comoGuaranies(linea.getImporteNeto()),
                    FormateadorNumeros.comoGuaranies(linea.getIva()),
                    FormateadorNumeros.comoGuaranies(linea.getTotal())));
        }

        Map<String, Object> parametros = parametrosEmpresaAFecha(conexion, factura.getFecha());
        // Rediseño de la factura (27/10/2026, decisión de Joel, opción B): la actividad económica
        // es un dato PREIMPRESO obligatorio (Decreto 6539/05, art. 19) y ya estaba cargada en
        // empresa.actividad -- antes el .jrxml imprimía el texto fijo "Préstamos personales".
        // Va acá y no en parametrosEmpresaAFecha a propósito: ese método lo comparten el recibo,
        // la constancia de cancelación, la nota de anulación y la constancia de reversión, y
        // ninguno de esos documentos lleva actividad económica. Misma fecha de referencia que el
        // resto de los datos de empresa (la del documento, nunca la de hoy, D-94).
        parametros.put("EMPRESA_ACTIVIDAD",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.actividad", factura.getFecha()));
        agregarTimbradoDeFactura(conexion, factura, parametros);
        parametros.put("FACTURA_NUMERO", factura.getNumeroCompleto());
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaa(factura.getFecha()));
        // Punto 3 (pedido de Joel, 14/10/2026, autorizado): parámetros NUEVOS para el pie, nunca
        // se toca FECHA_EMISION (de arriba, dato legal del cuerpo). PIE_FECHA_HORA es la fecha y
        // hora REAL del registro del pago (dato guardado, D-94) -- nunca Reloj.ahora(): la misma
        // factura reimprimida hoy o en un mes muestra siempre lo mismo.
        parametros.put("PIE_EMITIDO_POR", emisorPie == null ? "-" : emisorPie.getNombres() + " " + emisorPie.getApellidos());
        parametros.put("PIE_FECHA_HORA", FormateadorFechas.comoDdMmAaaaHhMm(pago.getFechaHoraPago()));
        parametros.put("CLIENTE_NOMBRE", factura.getClienteNombre());
        parametros.put("CLIENTE_DOCUMENTO", FormateadorTexto.comoCedulaConPuntos(factura.getClienteDocumento()));
        // Corrección de Joel (380b400, punto C7): la factura es un documento legal -- la
        // referencia coloquial entre paréntesis (B.15, domicilioSinReferencia) no corresponde
        // acá, mismo criterio ya usado en ServicioDesembolso/ServicioFormalizacion.
        // Normalización de personas, grupo Domicilio (16/10/2026): texto armado al vuelo (D-94:
        // solo corre al generar la factura, nunca sobre una ya emitida).
        String textoDomicilioCliente = servicioClientes.obtenerTextoDomicilio(conexion, cliente.getIdPersona());
        parametros.put("CLIENTE_DOMICILIO", textoDomicilioCliente == null ? "-"
                : ServicioClientes.domicilioSinReferencia(textoDomicilioCliente));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("NUMERO_RECIBO", pago.getNumeroRecibo());
        // Corrección de Joel (Bloque 2, revisión, error fiscal): la columna "5 %" siempre va en
        // 0 -- este sistema nunca tiene conceptos gravados al 5 %, solo al 10 % (RN-25) -- pero la
        // factura tiene que mostrar las 3 columnas (Exentas | 5 % | 10 %) igual que el modelo
        // oficial (Financiera Paraguayo Japonesa, D-78), no solo las que este negocio usa.
        parametros.put("TOTAL_EXENTA", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TOTAL_GRAVADA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("IVA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TOTAL_GRAVADA_10", FormateadorNumeros.comoGuaranies(factura.getTotalGravada10()));
        parametros.put("IVA_10", FormateadorNumeros.comoGuaranies(factura.getIva10()));
        parametros.put("TOTAL", FormateadorNumeros.comoGuaranies(factura.getTotal()));
        parametros.put("TOTAL_LETRAS",
                ConversorNumerosALetras.comoLetrasConGuaranies(factura.getTotal()).toUpperCase(new Locale("es")));

        // Punto 3 (RF25, hallazgo de Joel, 12/10/2026): marca "ANULADA" con fecha.
        boolean facturaAnulada = factura.getEstado() == EstadoFactura.ANULADA;
        parametros.put("ANULADA", facturaAnulada);
        parametros.put("MARCA_AGUA", facturaAnulada ? GeneradorInformesPdf.construirMarcaAguaDiagonal("ANULADA") : null);
        parametros.put("FECHA_ANULACION_FACTURA", "-");
        if (facturaAnulada && factura.getIdReversionPago() != null) {
            ReversionPago reversionDeFactura = reversionPagoDAO.obtenerPorId(conexion, factura.getIdReversionPago());
            if (reversionDeFactura != null && reversionDeFactura.getFechaHoraReversion() != null) {
                parametros.put("FECHA_ANULACION_FACTURA",
                        FormateadorFechas.comoDdMmAaaa(reversionDeFactura.getFechaHoraReversion().toLocalDate()));
            }
        }

        return llenarYExportar("factura.jasper", parametros, new JRBeanCollectionDataSource(filas));
    }

    /** D-94: PDF de la constancia de una cancelación total. */
    public byte[] obtenerPdfConstancia(long idLiquidacion, long idUsuario) throws SQLException {
        return obtenerOGenerarPdf("liquidaciones_anticipadas", "id_liquidacion", "id_archivo_liquidacion",
                idLiquidacion, idUsuario, "constancia_" + idLiquidacion + ".pdf",
                conexion -> generarPdfConstancia(conexion, idLiquidacion));
    }

    private byte[] generarPdfConstancia(Connection conexion, long idLiquidacion) throws SQLException {
        LiquidacionAnticipada liquidacion = liquidacionAnticipadaDAO.obtenerPorId(conexion, idLiquidacion);
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, liquidacion.getIdPrestamo());
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        LocalDate fechaCancelacion = liquidacion.getFechaCalculo().toLocalDate();
        Pago pago = pagoDAO.obtenerPorLiquidacion(conexion, idLiquidacion);
        Factura factura = pago == null ? null : facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
        Usuario emisor = servicioUsuarios.buscarPorId(conexion, liquidacion.getCalculadoPor());

        Map<String, Object> parametros = parametrosEmpresaAFecha(conexion, fechaCancelacion);
        parametros.put("REPRESENTANTE_NOMBRE",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_nombre", fechaCancelacion));
        parametros.put("REPRESENTANTE_CEDULA",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_cedula", fechaCancelacion));
        parametros.put("NUMERO_CONSTANCIA", liquidacion.getNumeroConstancia());
        parametros.put("FECHA_CANCELACION", FormateadorFechas.comoDdMmAaaa(fechaCancelacion));
        // Pedido de Joel: el encabezado estilo carta ("Asunción, ...") va en formato largo; el resto
        // de los usos de FECHA_CANCELACION (dato de la tabla) sigue en dd/mm/aaaa.
        parametros.put("FECHA_CANCELACION_LARGA", FormateadorFechas.comoFechaLarga(fechaCancelacion));
        // Pedido de Joel (12/10/2026, ajustes chicos del Bloque Cancelación): nombre del cliente
        // sin mayúsculas forzadas -- mismo criterio que generarPdfConstanciaReversion (línea 2388).
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("MONTO_DESEMBOLSADO", FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()));
        parametros.put("FECHA_DESEMBOLSO",
                prestamo.getFechaDesembolso() == null ? "-" : FormateadorFechas.comoDdMmAaaa(prestamo.getFechaDesembolso()));
        parametros.put("PLAZO_MESES", prestamo.getPlazoMeses());
        // D-111 (PA-16): "Capital no vencido" ya NO usa liquidacion.getSaldoCapitalFecha()
        // (capitalVencido + capitalNoVencido combinado, D-93) -- ese campo se guardó así desde
        // E8, pero imprimirlo bajo esta etiqueta duplicaba el capital vencido, que YA está dentro
        // de "Vencidas impagas". Se reconstruye el capital NO vencido desde aplicaciones_pago
        // (una fila por cuota que tocó esta cancelación, con su capital real, D-85), sumando solo
        // las cuotas cuya fecha_vencimiento seguía sin llegar el día del cálculo -- sin tocar el
        // esquema ni la columna ya guardada (sigue existiendo, solo no se usa para esta etiqueta).
        BigDecimal capitalNoVencido = pago == null ? liquidacion.getSaldoCapitalFecha()
                : aplicacionPagoDAO.sumarCapitalNoVencidoPorPago(conexion, pago.getIdPago(), fechaCancelacion);
        parametros.put("VENCIDAS_IMPAGAS", FormateadorNumeros.comoGuaranies(liquidacion.getMontoVencidasImpagas()));
        parametros.put("CAPITAL_NO_VENCIDO", FormateadorNumeros.comoGuaranies(capitalNoVencido));
        parametros.put("INTERES_PERIODO", FormateadorNumeros.comoGuaranies(liquidacion.getInteresDevengado()));
        parametros.put("IVA_INTERESES", FormateadorNumeros.comoGuaranies(liquidacion.getTributosAplicables()));
        parametros.put("TOTAL_CANCELADO", FormateadorNumeros.comoGuaranies(liquidacion.getMontoTotalLiquidacion()));
        // Pedido de Joel (12/10/2026): total en letras, mismo criterio que
        // generarPdfConstanciaReversion (línea 2394).
        parametros.put("TOTAL_CANCELADO_LETRAS",
                ConversorNumerosALetras.comoLetrasConGuaranies(liquidacion.getMontoTotalLiquidacion()).toUpperCase(new Locale("es")));
        parametros.put("NUMERO_RECIBO", pago == null ? "-" : pago.getNumeroRecibo());
        parametros.put("TIENE_FACTURA", factura != null);
        parametros.put("FACTURA_NUMERO", factura == null ? "" : factura.getNumeroCompleto());
        parametros.put("USUARIO_EMISOR", emisor == null ? "-" : emisor.getNombres() + " " + emisor.getApellidos());
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(liquidacion.getFechaCalculo()));

        return llenarYExportar("constancia_cancelacion.jasper", parametros, new JREmptyDataSource(1));
    }

    /** E9/Bloque 5 (RF-41, D-94): PDF de la Nota de anulación de una factura -- se genera recién
     * al primer pedido (nunca dentro de la transacción de dinero de ServicioReversiones.
     * autorizarReversion, que solo reserva el número NAN-aaaa-nnnnnn). */
    public byte[] obtenerPdfNotaAnulacion(long idFactura, long idUsuario) throws SQLException {
        return obtenerOGenerarPdf("facturas", "id_factura", "id_archivo_nota_anulacion", idFactura, idUsuario,
                "nota_anulacion_" + idFactura + ".pdf", conexion -> generarPdfNotaAnulacion(conexion, idFactura));
    }

    private byte[] generarPdfNotaAnulacion(Connection conexion, long idFactura) throws SQLException {
        Factura factura = facturaDAO.obtenerPorId(conexion, idFactura);
        if (factura.getNumeroNotaAnulacion() == null || factura.getIdReversionPago() == null) {
            throw new IllegalStateException("La factura " + idFactura + " no tiene una anulación registrada; "
                    + "no se genera la nota.");
        }
        Pago pago = pagoDAO.obtenerPorId(conexion, factura.getIdPago());
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
        ReversionPago reversion = reversionPagoDAO.obtenerPorId(conexion, factura.getIdReversionPago());
        LocalDate fechaAnulacion = reversion.getFechaHoraReversion().toLocalDate();
        Usuario autorizador = servicioUsuarios.buscarPorId(conexion, reversion.getAutorizadoPor());
        Usuario solicitante = servicioUsuarios.buscarPorId(conexion, reversion.getSolicitadoPor());

        // Corrección de Joel (tras la prueba de la parte A): el detalle línea por concepto/cuota
        // (con IVA), igual que en la factura original -- mismo reconstrucción desde el ledger,
        // nunca un total inventado (D-93/D-85).
        List<LineaFactura> lineas = reconstruirLineasFactura(conexion, factura.getIdPago());
        List<FilaFacturaReporte> filas = new ArrayList<>();
        for (LineaFactura linea : lineas) {
            filas.add(new FilaFacturaReporte(linea.getDescripcion(),
                    FormateadorNumeros.comoGuaranies(linea.getImporteNeto()),
                    FormateadorNumeros.comoGuaranies(linea.getIva()),
                    FormateadorNumeros.comoGuaranies(linea.getTotal())));
        }

        Map<String, Object> parametros = parametrosEmpresaAFecha(conexion, fechaAnulacion);
        agregarTimbradoDeFactura(conexion, factura, parametros);
        parametros.put("REPRESENTANTE_NOMBRE",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_nombre", fechaAnulacion));
        parametros.put("REPRESENTANTE_CEDULA",
                servicioParametros.obtenerValorAFechaOMasAntiguo(conexion, "empresa.representante_legal_cedula", fechaAnulacion));
        parametros.put("NUMERO_NOTA_ANULACION", factura.getNumeroNotaAnulacion());
        parametros.put("FECHA_ANULACION", FormateadorFechas.comoDdMmAaaa(fechaAnulacion));
        parametros.put("FECHA_ANULACION_LARGA", FormateadorFechas.comoFechaLarga(fechaAnulacion));
        parametros.put("CLIENTE_NOMBRE", factura.getClienteNombre().toUpperCase(new Locale("es")));
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(factura.getClienteDocumento()));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("FACTURA_NUMERO", factura.getNumeroCompleto());
        parametros.put("FACTURA_FECHA", FormateadorFechas.comoDdMmAaaa(factura.getFecha()));
        parametros.put("FACTURA_TOTAL", FormateadorNumeros.comoGuaranies(factura.getTotal()));
        parametros.put("NUMERO_RECIBO", pago.getNumeroRecibo());
        parametros.put("MONTO_REVERTIDO", FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()));
        parametros.put("MOTIVO_REVERSION", reversion.getMotivoReversion());
        parametros.put("SOLICITADO_POR", solicitante == null ? "-" : solicitante.getNombres() + " " + solicitante.getApellidos());
        parametros.put("AUTORIZADO_POR", autorizador == null ? "-" : autorizador.getNombres() + " " + autorizador.getApellidos());
        // D-86: acá nunca hay 5 %, mismo criterio que la factura original (RN-25).
        parametros.put("TOTAL_GRAVADA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("IVA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TOTAL_GRAVADA_10", FormateadorNumeros.comoGuaranies(factura.getTotalGravada10()));
        parametros.put("IVA_10", FormateadorNumeros.comoGuaranies(factura.getIva10()));
        parametros.put("USUARIO_EMISOR", autorizador == null ? "-" : autorizador.getNombres() + " " + autorizador.getApellidos());
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(reversion.getFechaHoraReversion()));

        return llenarYExportar("nota_anulacion.jasper", parametros, new JRBeanCollectionDataSource(filas));
    }

    /**
     * Punto 2 (RF25, pedido de Joel, 12/10/2026): PDF de la constancia de una reversión
     * autorizada -- documento interno (no fiscal, a diferencia de la Nota de anulación), con el
     * detalle de la devolución en efectivo. Se genera al primer pedido (al imprimir desde el
     * cartel de "Reversión autorizada", o al reimprimir después desde el historial de cobros o
     * desde el historial de reversiones), igual que el resto de los comprobantes de este bloque
     * (D-94). Reusa <code>reversiones_pago.id_archivo_comprobante</code> -- columna que ya
     * existía en el esquema, sin usar hasta ahora -- en vez de agregar una columna nueva.
     * Número propio sin numerador aparte (cambio mínimo, Joel permitió "o el que ya exista"):
     * REV-aaaa-NNNNNN con <code>id_reversion_pago</code> como secuencia -- ya es único y
     * siempre creciente, y una reversión autorizada nunca se vuelve a autorizar (regla de
     * RF25_reversion_cobro.md §6c: el pago revertido nunca vuelve a ser "la última operación" de
     * nada).
     */
    public byte[] obtenerPdfConstanciaReversion(long idReversionPago, long idUsuario) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // Punto 6 (RF25, pedido de Joel, 12/10/2026): "Reimpreso por ... el ..." necesita
                // la fecha/hora REAL de cada impresión -- a diferencia del resto de los
                // comprobantes de D-94, esta constancia ya NO sirve el PDF cacheado después de la
                // primera vez (mismo criterio que ServicioDesembolso.obtenerPdfComprobanteDesembolso,
                // que tampoco cachea). id_archivo_comprobante pasa a ser solo la marca de "ya se
                // imprimió una vez" (se guarda un PDF, pero nunca se vuelve a leer) -- se podría
                // sacar del todo, se deja por si en el futuro hace falta auditar el primer PDF
                // exacto que se entregó.
                ReversionPago reversion = reversionPagoDAO.obtenerPorId(conexion, idReversionPago);
                boolean esReimpresion = reversion != null && reversion.getIdArchivoComprobante() != null;
                Usuario quienImprime = servicioUsuarios.buscarPorId(conexion, idUsuario);
                String marcaReimpresion = esReimpresion ? construirMarcaReimpresionConstancia(quienImprime) : null;
                byte[] pdf = generarPdfConstanciaReversion(conexion, idReversionPago, marcaReimpresion);
                if (!esReimpresion) {
                    ArchivoBinario archivo = new ArchivoBinario();
                    archivo.setNombreArchivo("constancia_reversion_" + idReversionPago + ".pdf");
                    archivo.setMimeType("application/pdf");
                    archivo.setExtension("pdf");
                    archivo.setTamanoBytes(pdf.length);
                    archivo.setHashSha256(ServicioDocumentos.calcularHashSha256(pdf));
                    archivo.setOrigenArchivo(OrigenArchivo.JASPER);
                    archivo.setCargadoPor(idUsuario);
                    long idArchivo = archivoBinarioDAO.insertar(conexion, archivo, pdf);
                    archivoComprobanteDAO.vincular(conexion, "reversiones_pago", "id_reversion_pago",
                            "id_archivo_comprobante", idReversionPago, idArchivo);
                }
                conexion.commit();
                return pdf;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Punto 6 (RF25, pedido de Joel): mismo texto/criterio que
     * ServicioDesembolso.construirMarcaReimpresion (RF17/RF18). */
    private String construirMarcaReimpresionConstancia(Usuario usuario) {
        return "Reimpreso por " + (usuario == null ? "-" : usuario.getNombres() + " " + usuario.getApellidos())
                + " el " + FormateadorFechas.comoDdMmAaaaHhMm(Reloj.ahora()) + ".";
    }

    private byte[] generarPdfConstanciaReversion(Connection conexion, long idReversionPago, String marcaReimpresion)
            throws SQLException {
        ReversionPago reversion = reversionPagoDAO.obtenerPorId(conexion, idReversionPago);
        if (reversion == null || reversion.getEstado() != EstadoReversion.AUTORIZADA) {
            throw new IllegalStateException("La reversión " + idReversionPago
                    + " no está autorizada; no se genera la constancia.");
        }
        Pago pago = pagoDAO.obtenerPorId(conexion, reversion.getIdPago());
        PrestamoCredito prestamo = prestamoDAO.obtenerPorId(conexion, pago.getIdPrestamo());
        SolicitudCredito solicitud = solicitudDAO.obtenerPorId(conexion, prestamo.getIdSolicitud());
        Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
        Factura factura = facturaDAO.obtenerPorPago(conexion, pago.getIdPago());
        Usuario solicitante = servicioUsuarios.buscarPorId(conexion, reversion.getSolicitadoPor());
        Usuario autorizador = reversion.getAutorizadoPor() == null ? null
                : servicioUsuarios.buscarPorId(conexion, reversion.getAutorizadoPor());
        LocalDate fechaAutorizacion = reversion.getFechaHoraReversion().toLocalDate();

        Map<String, Object> parametros = parametrosEmpresaAFecha(conexion, fechaAutorizacion);
        parametros.put("NUMERO_CONSTANCIA", construirNumeroConstanciaReversion(fechaAutorizacion.getYear(), idReversionPago));
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(reversion.getFechaHoraReversion()));
        parametros.put("FECHA_EMISION_LARGA", FormateadorFechas.comoFechaLarga(fechaAutorizacion));
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        parametros.put("NUMERO_RECIBO", pago.getNumeroRecibo());
        parametros.put("MONTO_DEVUELTO", FormateadorNumeros.comoGuaranies(pago.getMontoAplicado()));
        // Punto 3 (RF25, pedido de Joel): monto en letras, mismo conversor que el recibo.
        parametros.put("MONTO_DEVUELTO_LETRAS",
                ConversorNumerosALetras.comoLetrasConGuaranies(pago.getMontoAplicado()).toUpperCase(new Locale("es")));
        parametros.put("MOTIVO_SOLICITUD", reversion.getMotivoReversion());
        parametros.put("SOLICITADO_POR", solicitante == null ? "-" : solicitante.getNombres() + " " + solicitante.getApellidos());
        parametros.put("AUTORIZADO_POR", autorizador == null ? "-" : autorizador.getNombres() + " " + autorizador.getApellidos());
        parametros.put("TIENE_FACTURA", factura != null);
        parametros.put("FACTURA_NUMERO", factura == null ? "" : factura.getNumeroCompleto());
        parametros.put("USUARIO_EMISOR", autorizador == null ? "-" : autorizador.getNombres() + " " + autorizador.getApellidos());
        // Punto 6 (RF25, pedido de Joel): "Emitido por" siempre es el gerente/fecha de la
        // autorización (arriba); esta marca aparte es sobre QUIÉN/CUÁNDO se está reimprimiendo.
        parametros.put("MARCA_REIMPRESION", marcaReimpresion == null ? "" : marcaReimpresion);

        return llenarYExportar("constancia_reversion.jasper", parametros, new JREmptyDataSource(1));
    }

    /** Pura (REGLAS_DESARROLLO.md §7 punto 9): "REV-aaaa-NNNNNN" con id_reversion_pago como
     * secuencia (ver el comentario de generarPdfConstanciaReversion sobre por qué no hace falta
     * un numerador aparte). Visibilidad de paquete para poder probarla desde JUnit. */
    static String construirNumeroConstanciaReversion(int anio, long idReversionPago) {
        return "REV-" + anio + "-" + String.format("%06d", idReversionPago);
    }

    /**
     * Pura (D-77 punto 5): ACTIVO si no queda ninguna cuota vencida; EN_MORA si hay alguna
     * vencida; FINALIZADO si no queda ninguna cuota pendiente (RN-12).
     */
    public EstadoPrestamo calcularEstadoSegunCuotas(Prestamo prestamoMotor, LocalDate fecha) {
        boolean hayPendientes = false;
        boolean hayVencidas = false;
        for (Cuota cuota : prestamoMotor.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                hayPendientes = true;
                if (cuota.getFechaVencimiento().isBefore(fecha)) {
                    hayVencidas = true;
                }
            }
        }
        if (!hayPendientes) {
            return EstadoPrestamo.FINALIZADO;
        }
        return hayVencidas ? EstadoPrestamo.EN_MORA : EstadoPrestamo.ACTIVO;
    }

    /**
     * Decisión 1 (Bloque 4, E8): marca cumplida=true las promesas ABIERTAS de este préstamo que
     * se resuelven con la operación que se acaba de confirmar -- (a) el préstamo queda sin
     * ninguna cuota vencida, o (b) lo aplicado desde la gestión hasta su fecha de promesa
     * (inclusive) ya alcanza el monto prometido. `gestiones_cobranza.cumplida` nunca se escribía
     * (bug encontrado al diseñar este bloque): sin esto, toda promesa vencida quedaba vencida
     * para siempre, aunque el cliente ya hubiera pagado. Se llama al final de confirmarCobro/
     * confirmarCancelacionTotal/confirmarAnticipoCapital, dentro de la misma transacción.
     */
    private void actualizarPromesasCumplidas(Connection conexion, long idPrestamo, boolean sinCuotasVencidas,
            LocalDate fecha) throws SQLException {
        for (GestionCobranza gestion : gestionCobranzaDAO.listarAbiertasPorPrestamo(conexion, idPrestamo)) {
            BigDecimal sumaEnVentana = pagoDAO.sumarAplicadoEnVentana(conexion, idPrestamo, gestion.getFechaHora(),
                    gestion.getFechaPromesa().atTime(java.time.LocalTime.MAX), gestion.getIdPago());
            if (evaluarPromesaCumplida(gestion, sinCuotasVencidas, sumaEnVentana)) {
                gestionCobranzaDAO.marcarCumplida(conexion, gestion.getIdGestionCobranza());
            }
        }
    }

    /**
     * Regla pura de la decisión 1 (Bloque 4, E8) -- sin conexión a la base, para poder probarla
     * con JUnit. Visibilidad de paquete (no private), mismo criterio que
     * reconstruirConCuotasActualizadas.
     */
    static boolean evaluarPromesaCumplida(GestionCobranza gestion, boolean sinCuotasVencidas,
            BigDecimal sumaEnVentana) {
        if (sinCuotasVencidas) {
            return true;
        }
        return sumaEnVentana.compareTo(gestion.getMontoPrometido()) >= 0;
    }

    private void aplicarEstadoSegunCuotas(Connection conexion, long idPrestamo, EstadoPrestamo estadoActual,
            Prestamo prestamoMotor, LocalDate fecha) throws SQLException {
        EstadoPrestamo nuevoEstado = calcularEstadoSegunCuotas(prestamoMotor, fecha);
        if (nuevoEstado == EstadoPrestamo.FINALIZADO) {
            if (estadoActual != EstadoPrestamo.FINALIZADO) {
                prestamoDAO.marcarFinalizado(conexion, idPrestamo);
            }
        } else if (nuevoEstado != estadoActual) {
            prestamoDAO.actualizarEstadoMora(conexion, idPrestamo, nuevoEstado);
        }
    }

    /**
     * Reemplaza, en una copia del Prestamo, las cuotas que un pago tocó por su versión resultante.
     * Visibilidad de paquete (no private) para poder probar desde JUnit el mismo encadenamiento que
     * usa confirmarCobro: distribuirPago -> reconstruirConCuotasActualizadas -> calcularEstadoSegunCuotas.
     */
    Prestamo reconstruirConCuotasActualizadas(Prestamo original, ResultadoAplicacionPago aplicacion) {
        List<Cuota> actualizadas = new ArrayList<>();
        for (Cuota cuota : original.getCuotas()) {
            Cuota reemplazo = cuota;
            for (DetalleAplicacionPago detalle : aplicacion.getDetalles()) {
                if (detalle.getNumeroCuota() == cuota.getNumeroCuota()) {
                    reemplazo = detalle.getCuotaResultante();
                    break;
                }
            }
            actualizadas.add(reemplazo);
        }
        Prestamo actualizado = new Prestamo();
        actualizado.setFechaDesembolso(original.getFechaDesembolso());
        actualizado.setTasaTna(original.getTasaTna());
        actualizado.setTasaTea(original.getTasaTea());
        actualizado.setTasaMensual(original.getTasaMensual());
        actualizado.setTasaMoratoria(original.getTasaMoratoria());
        actualizado.setTasaPunitoria(original.getTasaPunitoria());
        actualizado.setCuotas(actualizadas);
        return actualizado;
    }
}
