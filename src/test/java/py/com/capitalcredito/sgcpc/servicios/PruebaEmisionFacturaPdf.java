package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dto.LineaFactura;
import py.com.capitalcredito.sgcpc.dto.TotalesLineasFactura;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoConfirmacionPago;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;
import py.com.capitalcredito.sgcpc.modelos.Factura;
import py.com.capitalcredito.sgcpc.modelos.Pago;
import py.com.capitalcredito.sgcpc.modelos.Persona;

/**
 * Prueba de emisión de factura de punta a punta (27/10/2026, pedido de Joel; DOGMA 1 de
 * {@code docs/REGLAS_DESARROLLO.md}: ningún cambio se da por probado sin una prueba del circuito
 * real que se pueda ver el mismo día). Es una prueba PERMANENTE del proyecto, no una herramienta
 * temporal.
 *
 * <p>Recorre el circuito REAL de emisión de una factura y verifica el PDF que sale, para poder
 * comprobar HOY que la factura nueva sale bien, sin esperar a que haya interés devengado por
 * cobrar: arma un pago con interés &gt; 0 sobre un préstamo existente de prueba →
 * {@code emitirFactura} (timbrado, numerador, totales, validación de líneas, fila en
 * {@code facturas}) → {@code reconstruirLineasFactura} → {@code generarPdfFactura} contra el
 * {@code factura.jasper} compilado → PDF.</p>
 *
 * <h3>Por qué no arranca en confirmarCobro</h3>
 * <p>Porque no se puede sin dejar rastro: {@code confirmarCobro} y {@code obtenerPdfFactura}
 * abren su PROPIA conexión con {@code FabricaConexionBD.obtenerConexion()} y hacen
 * {@code commit()} (y la segunda, además, guarda el PDF en {@code archivos_binarios}).
 * {@code FabricaConexionBD} no tiene punto de inyección, así que una prueba no puede darles su
 * conexión ni revertir lo que hacen. De {@code emitirFactura} para adelante, en cambio, todo
 * recibe la {@code Connection} y cuelga de UNA transacción -- la que esta prueba revierte.</p>
 *
 * <p><b>Qué NO cubre, entonces</b>: el cobro en sí ({@code distribuirPago}, la caja, la
 * auditoría y el recibo). Eso se prueba en pantalla, con un cobro real.</p>
 *
 * <h3>Seguridad</h3>
 * <ul>
 *   <li>Aborta si la base no es {@code sgcpc_db_pruebas} (lo lee de la conexión real, no de un
 *       archivo).</li>
 *   <li>Una sola conexión, {@code autoCommit=false}, y {@code rollback()} en un {@code finally}:
 *       siempre revierte, también si una verificación falla.</li>
 *   <li>El numerador de facturas es la tabla {@code numeradores} ({@code UPDATE}, transaccional):
 *       el rollback lo devuelve a su valor, no se consume ningún número de timbrado. Lo único
 *       que no vuelve atrás son los contadores de las columnas {@code IDENTITY} (en PostgreSQL
 *       {@code nextval} no es transaccional): quedan huecos en id internos, invisibles y sin
 *       efecto sobre ningún número fiscal. Mismo criterio que
 *       {@link PruebaD116TimbradoPorFecha}.</li>
 *   <li>Al final vuelve a contar facturas, pagos y el numerador y verifica que quedaron igual.</li>
 * </ul>
 *
 * <p>No corre con {@code mvn test} (el nombre no matchea {@code *Test}/{@code Test*} de
 * Surefire). Se ejecuta a propósito con {@code mvn -Dtest=PruebaEmisionFacturaPdf test}.</p>
 *
 * <p>Para leer el texto del PDF usa {@code pdftotext} (viene con Git Bash). Es la única
 * dependencia externa de esta clase, y es aceptable porque es una herramienta de desarrollo que
 * nadie corre en la entrega: meter un lector de PDF en {@code pom.xml} obligaría al docente a
 * tener ese .jar para compilar, y escribir un parser de PDF a mano adentro de una prueba es
 * peor. Si {@code pdftotext} no está, la prueba falla diciéndolo, nunca saltea la verificación
 * en silencio.</p>
 */
public class PruebaEmisionFacturaPdf {

    private static final String BASE_PERMITIDA = "sgcpc_db_pruebas";
    private static final String OPERACION_DE_PRUEBA = "CC-2026-00001";
    private static final File CARPETA_PDF = new File("../pruebas/factura_muestra");
    private static final String NOMBRE_PDF = "factura_prueba_emision.pdf";
    /** Interés del renglón gravado. El IVA se deriva con el mismo método que usa el sistema. */
    private static final BigDecimal INTERES = new BigDecimal("23456");

    @Test
    public void emiteLaFacturaYElPdfTieneTodosLosDatosLegales() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            ServicioParametros servicioParametros = new ServicioParametros();
            int anioNumerador = servicioParametros
                    .obtenerValorFecha(conexion, "fiscal.timbrado_vigencia_desde").getYear();
            long facturasAntes = contarFacturas(conexion);
            long pagosAntes = contarPagosDeLaOperacion(conexion);
            long numeradorAntes = ultimoValorNumeradorFactura(conexion, anioNumerador);
            System.out.println("Base ANTES: " + facturasAntes + " facturas, " + pagosAntes
                    + " pagos de " + OPERACION_DE_PRUEBA + ", numerador FACTURA en " + numeradorAntes);

            try {
                verificarEmision(conexion, numeradorAntes);
            } finally {
                // SIEMPRE, también si una verificación falló: nada de esta prueba queda en la base.
                conexion.rollback();
            }

            long facturasDespues = contarFacturas(conexion);
            long pagosDespues = contarPagosDeLaOperacion(conexion);
            long numeradorDespues = ultimoValorNumeradorFactura(conexion, anioNumerador);
            long aplicacionesHuerfanas = unValor(conexion, "SELECT count(*) FROM aplicaciones_pago a"
                    + " LEFT JOIN pagos p ON p.id_pago = a.id_pago WHERE p.id_pago IS NULL");
            System.out.println("Base DESPUÉS del rollback: " + facturasDespues + " facturas, "
                    + pagosDespues + " pagos de " + OPERACION_DE_PRUEBA
                    + ", numerador FACTURA en " + numeradorDespues);

            assertEquals("El rollback dejó facturas de más.", facturasAntes, facturasDespues);
            assertEquals("El rollback dejó pagos de más.", pagosAntes, pagosDespues);
            assertEquals("El rollback NO devolvió el numerador de facturas: se consumió un número.",
                    numeradorAntes, numeradorDespues);
            assertEquals("Quedaron aplicaciones_pago sin su pago.", 0, aplicacionesHuerfanas);
        }
    }

    /** Guarda dura: la URL sale de la conexión abierta, no de un archivo que alguien pudo mover. */
    private void abortarSiNoEsLaBaseDePruebas(Connection conexion) throws Exception {
        String url = conexion.getMetaData().getURL();
        String catalogo = conexion.getCatalog();
        if (url == null || !url.endsWith("/" + BASE_PERMITIDA) || !BASE_PERMITIDA.equals(catalogo)) {
            fail("ABORTADO: esta prueba solo corre contra " + BASE_PERMITIDA
                    + ". La conexión activa apunta a \"" + url + "\" (catálogo \"" + catalogo
                    + "\"). Revisar bd.properties.");
        }
    }

    private long contarFacturas(Connection conexion) throws Exception {
        return unValor(conexion, "SELECT count(*) FROM facturas");
    }

    private long contarPagosDeLaOperacion(Connection conexion) throws Exception {
        return unValor(conexion, "SELECT count(*) FROM pagos p JOIN prestamos pr"
                + " ON pr.id_prestamo = p.id_prestamo WHERE pr.identificador_operacion = '"
                + OPERACION_DE_PRUEBA + "'");
    }

    private long ultimoValorNumeradorFactura(Connection conexion, int anio) throws Exception {
        return unValor(conexion, "SELECT coalesce(max(ultimo_valor), -1) FROM numeradores"
                + " WHERE clave = 'FACTURA' AND anio = " + anio);
    }

    private void verificarEmision(Connection conexion, long numeradorAntes) throws Exception {
        ServicioCobranza servicioCobranza = new ServicioCobranza();
        ServicioClientes servicioClientes = new ServicioClientes();
        ServicioParametros servicioParametros = new ServicioParametros();
        PagoDAO pagoDAO = new PagoDAO();
        AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();

        LocalDate fecha = LocalDate.now();
        LocalDate timbradoDesde = servicioParametros.obtenerValorFecha(conexion,
                "fiscal.timbrado_vigencia_desde");
        LocalDate timbradoHasta = servicioParametros.obtenerValorFecha(conexion,
                "fiscal.timbrado_vigencia_hasta");

        // --- Fixture: un pago con interés > 0 sobre el préstamo de prueba -------------------
        long idPrestamo = unValor(conexion, "SELECT id_prestamo FROM prestamos"
                + " WHERE identificador_operacion = '" + OPERACION_DE_PRUEBA + "'");
        long idCliente = unValor(conexion, "SELECT s.id_cliente FROM prestamos p"
                + " JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud"
                + " WHERE p.id_prestamo = " + idPrestamo);
        long idCuota = unValor(conexion, "SELECT c.id_cuota FROM cuotas c"
                + " JOIN planes_pago pl ON pl.id_plan_pago = c.id_plan_pago"
                + " WHERE pl.id_prestamo = " + idPrestamo + " AND pl.vigente"
                + " ORDER BY c.numero_cuota LIMIT 1");
        long idSesionCaja = unValor(conexion,
                "SELECT max(id_sesion_caja) FROM sesiones_caja");
        long idUsuario = unValor(conexion, "SELECT id_cajero FROM sesiones_caja"
                + " WHERE id_sesion_caja = " + idSesionCaja);

        Persona cliente = servicioClientes.buscarPorId(conexion, idCliente);
        BigDecimal iva = ServicioMotorFinanciero.iva(INTERES);
        BigDecimal subtotal = INTERES.add(iva);

        // El número de recibo tiene que tener el LARGO de uno real ("REC-2026-000002"): con uno
        // más largo, la celda "Recibo asociado" lo corta y la prueba no lo vería. Arranca en 9
        // para no chocar nunca con uno real, y de todos modos se revierte.
        String numeroRecibo = String.format("REC-%d-9%05d", fecha.getYear(),
                System.nanoTime() % 100000);
        Pago pago = new Pago();
        pago.setIdentificadorConfirmacion("PRUEBA-EMISION-" + System.nanoTime());
        pago.setIdPrestamo(idPrestamo);
        pago.setIdSesionCaja(idSesionCaja);
        pago.setNumeroRecibo(numeroRecibo);
        pago.setFechaHoraPago(LocalDateTime.now());
        pago.setMontoRecibido(subtotal);
        pago.setMontoVuelto(BigDecimal.ZERO);
        pago.setMontoRedondeo(BigDecimal.ZERO);
        pago.setMontoAplicado(subtotal);
        pago.setEstadoPago(EstadoConfirmacionPago.CONFIRMADO);
        pago.setRegistradoPor(idUsuario);
        pago.setTipo(TipoPago.NORMAL);
        long idPago = pagoDAO.insertar(conexion, pago);

        AplicacionPago aplicacion = new AplicacionPago();
        aplicacion.setIdPago(idPago);
        aplicacion.setTipoAplicacion(TipoAplicacion.CUOTA);
        aplicacion.setIdCuota(idCuota);
        aplicacion.setMontoMoratorio(BigDecimal.ZERO);
        aplicacion.setMontoPunitorio(BigDecimal.ZERO);
        aplicacion.setMontoInteres(INTERES);
        aplicacion.setMontoTributos(iva);
        aplicacion.setMontoCapital(BigDecimal.ZERO);
        aplicacion.setSubtotalAplicado(subtotal);
        aplicacionPagoDAO.insertar(conexion, aplicacion);

        // --- Circuito real de emisión --------------------------------------------------------
        List<LineaFactura> lineas = servicioCobranza.reconstruirLineasFactura(conexion, idPago);
        assertEquals("El pago de prueba tiene que dar exactamente un renglón gravado.",
                1, lineas.size());
        assertTrue("El renglón tiene que tener interés > 0.",
                lineas.get(0).getImporteNeto().compareTo(BigDecimal.ZERO) > 0);

        TotalesLineasFactura totales = ServicioCobranza.calcularTotalesFactura(lineas);
        Factura factura = servicioCobranza.emitirFactura(conexion, idPago, cliente, fecha, lineas,
                totales.getTotalGravado(), totales.getTotalIva());

        String numeroEsperado = "001-001-" + String.format("%07d", numeradorAntes + 1);
        assertEquals("El número de factura tiene que ser el siguiente del numerador.",
                numeroEsperado, factura.getNumeroCompleto());
        assertEquals("El total de la factura es neto + IVA de todas las líneas.",
                0, factura.getTotal().compareTo(subtotal));
        assertEquals("El IVA de la factura es el derivado del interés.",
                0, factura.getIva10().compareTo(iva));

        byte[] pdf = servicioCobranza.generarPdfFactura(conexion, factura.getIdFactura());
        assertTrue("El PDF salió vacío.", pdf.length > 1000);
        assertEquals("Lo generado no es un PDF.", "%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));

        CARPETA_PDF.mkdirs();
        File archivoPdf = new File(CARPETA_PDF, NOMBRE_PDF);
        Files.write(archivoPdf.toPath(), pdf);
        System.out.println("PDF de la prueba: " + archivoPdf.getCanonicalPath());

        // --- Verificación del contenido del PDF ----------------------------------------------
        String texto = textoDelPdf(archivoPdf);
        String nombreCliente = cliente.getNombres() + " " + cliente.getApellidos();

        exigir(texto, "FACTURA");
        exigir(texto, "RUC: 80123456-5");
        exigir(texto, "TIMBRADO");
        exigir(texto, servicioParametros.obtenerValor(conexion, "fiscal.timbrado_numero"));
        exigir(texto, "Vigencia: " + FormateadorFechas.comoDdMmAaaa(timbradoDesde)
                + " al " + FormateadorFechas.comoDdMmAaaa(timbradoHasta));
        exigir(texto, numeroEsperado);
        exigir(texto, "Otorgante de Crédito Dinerario");
        exigir(texto, "Fecha de emisión: " + FormateadorFechas.comoDdMmAaaa(fecha));
        exigir(texto, "Condición de venta");
        exigir(texto, "Moneda: Guaraníes");
        exigir(texto, "Tipo de transacción: Prestación de servicios");
        exigir(texto, "Operación: " + OPERACION_DE_PRUEBA);
        // Completo, no cortado: la celda no estira, así que si alguien la angosta, esto falla.
        exigir(texto, "Recibo asociado: " + numeroRecibo);
        exigir(texto, nombreCliente);
        exigir(texto, FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        exigir(texto, "Interés compensatorio");
        exigir(texto, "Exentas");
        exigir(texto, "5 %");
        exigir(texto, "10 %");
        exigir(texto, "SUBTOTALES");
        exigir(texto, "TOTAL DE LA OPERACIÓN");
        exigir(texto, FormateadorNumeros.comoGuaranies(subtotal));
        exigir(texto, "Son (en letras):");
        exigir(texto, totalEnLetras(subtotal));
        exigir(texto, "LIQUIDACIÓN DEL IVA");
        exigir(texto, "TOTAL IVA");
        exigir(texto, FormateadorNumeros.comoGuaranies(iva));
        exigir(texto, "ORIGINAL");
        exigir(texto, "CLIENTE");
        exigir(texto, "COPIA");
        exigir(texto, "ARCHIVO TRIBUTARIO");

        System.out.println("Factura emitida en la transacción: " + numeroEsperado
                + " — total " + FormateadorNumeros.comoGuaranies(subtotal)
                + " — IVA " + FormateadorNumeros.comoGuaranies(iva)
                + " — cliente " + nombreCliente);
    }

    /** El mismo texto en letras que arma generarPdfFactura (nunca uno rearmado a mano). */
    private String totalEnLetras(BigDecimal total) {
        return ConversorNumerosALetras.comoLetrasConGuaranies(total).toUpperCase(new Locale("es"));
    }

    private void exigir(String texto, String esperado) {
        String normalizado = normalizar(texto);
        String buscado = normalizar(esperado);
        assertTrue("El PDF no contiene \"" + esperado + "\".", normalizado.contains(buscado));
    }

    /** pdftotext mete saltos y columnas de espacios: se compara con los blancos colapsados. */
    private String normalizar(String texto) {
        return texto.replaceAll("\\s+", " ").trim();
    }

    /** Texto del PDF con pdftotext (ver el javadoc de la clase). Nunca saltea en silencio. */
    private String textoDelPdf(File pdf) throws Exception {
        ProcessBuilder constructor = new ProcessBuilder("pdftotext", "-layout", "-enc", "UTF-8",
                pdf.getCanonicalPath(), "-");
        constructor.redirectErrorStream(true);
        Process proceso;
        try {
            proceso = constructor.start();
        } catch (Exception error) {
            fail("No se pudo ejecutar \"pdftotext\" (viene con Git Bash, en /mingw64/bin): "
                    + error.getMessage() + ". Sin él esta prueba no puede leer el PDF; correrla"
                    + " desde una consola que lo tenga en el PATH.");
            return null;
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (InputStream entrada = proceso.getInputStream()) {
            byte[] bloque = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(bloque)) > 0) {
                salida.write(bloque, 0, leidos);
            }
        }
        int codigo = proceso.waitFor();
        String texto = new String(salida.toByteArray(), StandardCharsets.UTF_8);
        if (codigo != 0) {
            fail("pdftotext terminó con código " + codigo + ": " + texto);
        }
        return texto;
    }

    private long unValor(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery(sql)) {
            if (!fila.next()) {
                fail("La consulta no devolvió nada: " + sql);
            }
            long valor = fila.getLong(1);
            if (fila.wasNull()) {
                fail("La consulta devolvió NULL (¿falta el dato de prueba?): " + sql);
            }
            return valor;
        }
    }
}
