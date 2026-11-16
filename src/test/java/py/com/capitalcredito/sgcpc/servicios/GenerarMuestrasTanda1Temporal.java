package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.ClausulaReporte;
import py.com.capitalcredito.sgcpc.dto.FilaFacturaReporte;
import py.com.capitalcredito.sgcpc.dto.FilaReciboCuota;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorFechasALetras;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio;

/**
 * HERRAMIENTA TEMPORAL (01/11/2026) -- muestras de la tanda B11/B13/C37/B102, para mirarlas en PNG
 * antes de que Joel las revise y antes del Clean and Build. Anotada en la lista "Antes de entregar"
 * de {@code AVANCE.md}; se borra al cerrar la tanda.
 *
 * <ul>
 *   <li><b>No corre con {@code mvn test}</b>: el nombre no matchea {@code *Test}/{@code Test*} de
 *       Surefire. Se invoca a propósito:
 *       {@code mvn -Dtest=GenerarMuestrasTanda1Temporal test}.</li>
 *   <li><b>No toca la base ni consume numeración</b>: ningún {@code numeradorDAO}, ninguna
 *       conexión. Los números de documento llevan "(MUESTRA)" y valores de los datos reales de
 *       {@code CC-2026-00004} (cuota 1/6 de 180.609, cobrada con 180.600).</li>
 *   <li><b>Compila el {@code .jrxml} en memoria</b> ({@code JasperCompileManager.compileReport}),
 *       así que no depende del {@code .jasper} ni lo toca.</li>
 *   <li>Los textos que se vienen a revisar los pide a <b>los mismos dueños que usa el sistema</b>
 *       ({@code ServicioCobranza.conceptoFacturaDeCuota}, {@code ConversorFechasALetras},
 *       {@code ServicioClientes.validarDomicilio}/{@code construirTextoDomicilio},
 *       {@code ConversorNumerosALetras}), nunca copiados a mano.</li>
 * </ul>
 */
public class GenerarMuestrasTanda1Temporal {

    private static final File CARPETA_REPORTES = new File("src/main/resources/reportes");
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/tanda1_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_tanda1"));
    private static final float ZOOM_PNG = 2.0f;

    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioClientes servicioClientes = new ServicioClientes();

    @Test
    public void generarLasSieteMuestras() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        // ---------------- C37: el "Son:" del recibo, tres ramas
        generar("recibo", "c37_recibo_con_redondeo",
                parametrosRecibo(new BigDecimal("180600"), new BigDecimal("180609"),
                        BigDecimal.ZERO, new BigDecimal("9")),
                filasDelRecibo());
        generar("recibo", "c37_recibo_con_vuelto",
                parametrosRecibo(new BigDecimal("200000"), new BigDecimal("180609"),
                        new BigDecimal("19400"), BigDecimal.ZERO),
                filasDelRecibo());
        generar("recibo", "c37_recibo_exacto",
                parametrosRecibo(new BigDecimal("180609"), new BigDecimal("180609"),
                        BigDecimal.ZERO, BigDecimal.ZERO),
                filasDelRecibo());

        // ---------------- B102: el concepto de la factura, cobro y cancelación total
        List<FilaFacturaReporte> cobro = new ArrayList<>();
        cobro.add(filaDeCuota(1, 6, new BigDecimal("20118")));
        generar("factura", "b102_factura_cobro", parametrosFactura(cobro), new JRBeanCollectionDataSource(cobro));

        List<FilaFacturaReporte> cancelacion = new ArrayList<>();
        for (int cuota = 2; cuota <= 6; cuota++) {
            cancelacion.add(filaDeCuota(cuota, 6, new BigDecimal(String.valueOf(20118 - (cuota - 1) * 3188))));
        }
        generar("factura", "b102_factura_cancelacion_total", parametrosFactura(cancelacion),
                new JRBeanCollectionDataSource(cancelacion));

        // ---------------- B11 + B13: el contrato, día 21 y día 1, con el domicilio capitalizado
        generar("contrato", "b11_b13_contrato_dia_21", parametrosContrato(LocalDate.of(2026, 10, 21)),
                clausulas(LocalDate.of(2026, 10, 21)));
        generar("contrato", "b11_b13_contrato_dia_1", parametrosContrato(LocalDate.of(2026, 11, 1)),
                clausulas(LocalDate.of(2026, 11, 1)));

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    // =====================================================================
    // C37 -- recibo
    // =====================================================================

    private Map<String, Object> parametrosRecibo(BigDecimal recibido, BigDecimal aplicado,
            BigDecimal vuelto, BigDecimal redondeo) {
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        p.put("NUMERO_RECIBO", "REC-2026-000099 (MUESTRA)");
        p.put("FECHA_HORA", "01/11/2026 10:00");
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Joel Jesús Acosta");
        p.put("CLIENTE_CEDULA", "3.637.936");
        p.put("TIENE_FACTURA", Boolean.TRUE);
        p.put("FACTURA_NUMERO", "001-001-0000099");
        p.put("EFECTIVO_RECIBIDO", FormateadorNumeros.comoGuaranies(recibido));
        p.put("TIENE_VUELTO", vuelto.compareTo(BigDecimal.ZERO) > 0);
        p.put("VUELTO", FormateadorNumeros.comoGuaranies(vuelto));
        p.put("APLICADO", FormateadorNumeros.comoGuaranies(aplicado));
        p.put("APLICADO_LETRAS", enLetras(aplicado));
        // C37: lo que viene a mirar esta muestra -- el "Son:" sale de acá.
        p.put("RECIBIDO_LETRAS", enLetras(recibido));
        p.put("TIENE_REDONDEO", redondeo.compareTo(BigDecimal.ZERO) > 0);
        p.put("REDONDEO", FormateadorNumeros.comoGuaranies(redondeo));
        p.put("ES_CANCELACION", Boolean.FALSE);
        p.put("ES_ANTICIPO", Boolean.FALSE);
        p.put("TIENE_PROMESA", Boolean.FALSE);
        p.put("PROMESA_FECHA", "-");
        p.put("PROMESA_MONTO", "-");
        p.put("ANTICIPO_INTERES", "0 Gs.");
        p.put("ANTICIPO_IVA", "0 Gs.");
        p.put("ANTICIPO_CAPITAL", "0 Gs.");
        p.put("ANTICIPO_CUOTAS_RESTANTES", 0);
        p.put("ANTICIPO_CUOTA_NUEVA", "-");
        p.put("ANTICIPO_PROXIMA_CUOTA_FECHA", "27/11/2026");
        p.put("ANTICIPO_PROXIMA_CUOTA_MONTO", "180.290 Gs.");
        p.put("ANTICIPO_ULTIMO_VENCIMIENTO", "28/03/2027");
        p.put("TOTAL_CAPITAL", "158.479 Gs.");
        p.put("TOTAL_INTERES", "20.118 Gs.");
        p.put("TOTAL_MORATORIO", "0 Gs.");
        p.put("TOTAL_PUNITORIO", "0 Gs.");
        p.put("TOTAL_IVA_CUOTAS", "2.012 Gs.");
        p.put("TOTAL_FILAS", "180.609 Gs.");
        p.put("SALDO_CAPITAL_DESPUES", "841.521 Gs.");
        p.put("CUOTAS_PENDIENTES", 5);
        p.put("TIENE_PROXIMO_VENCIMIENTO", Boolean.TRUE);
        p.put("PROXIMA_CUOTA", ServicioCobranza.etiquetaNumeroCuota(2, 6));
        p.put("PROXIMO_VENCIMIENTO", "27/11/2026");
        p.put("PROXIMA_CUOTA_VENCIDA", Boolean.FALSE);
        p.put("PROXIMA_CUOTA_RESTA", "-");
        p.put("USUARIO_EMISOR", "Sofía Martínez");
        p.put("FECHA_EMISION", "01/11/2026 10:00");
        p.put("REVERTIDO", Boolean.FALSE);
        p.put("REVERSION_FECHA", "-");
        p.put("REVERSION_AUTORIZADO_POR", "-");
        p.put("REVERSION_MOTIVO", "-");
        p.put("MARCA_AGUA", null);
        return p;
    }

    // =====================================================================
    // B102 -- factura
    // =====================================================================

    /** La descripción sale del DUEÑO, igual que en el documento real. */
    private FilaFacturaReporte filaDeCuota(int numeroCuota, int totalCuotas, BigDecimal neto) {
        BigDecimal iva = ServicioMotorFinanciero.iva(neto);
        return new FilaFacturaReporte(
                ServicioCobranza.conceptoFacturaDeCuota(ServicioCobranza.ConceptoGravado.INTERES,
                        numeroCuota, totalCuotas),
                FormateadorNumeros.comoGuaranies(neto),
                FormateadorNumeros.comoGuaranies(iva),
                FormateadorNumeros.comoGuaranies(neto.add(iva)));
    }

    private Map<String, Object> parametrosFactura(List<FilaFacturaReporte> filas) {
        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (FilaFacturaReporte fila : filas) {
            neto = neto.add(desformatear(fila.getImporteNeto()));
            iva = iva.add(desformatear(fila.getIva()));
        }
        BigDecimal total = neto.add(iva);
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_ACTIVIDAD", "Otorgante de Crédito Dinerario (OCD) / Casa de Crédito");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        p.put("TIMBRADO_NUMERO", "16842795");
        p.put("TIMBRADO_DESDE", "01/09/2026");
        p.put("TIMBRADO_HASTA", "31/08/2027");
        p.put("FACTURA_NUMERO", "001-001-0000099");
        p.put("FECHA_EMISION", "01/11/2026");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Joel Jesús Acosta");
        p.put("CLIENTE_DOCUMENTO", "3.637.936");
        p.put("CLIENTE_DOMICILIO", domicilioCapitalizado());
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        p.put("NUMERO_RECIBO", "REC-2026-000099 (MUESTRA)");
        p.put("TOTAL_EXENTA", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        p.put("TOTAL_GRAVADA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        p.put("IVA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        p.put("TOTAL_GRAVADA_10", FormateadorNumeros.comoGuaranies(total));
        p.put("IVA_10", FormateadorNumeros.comoGuaranies(iva));
        p.put("TOTAL", FormateadorNumeros.comoGuaranies(total));
        p.put("TOTAL_LETRAS", enLetras(total));
        p.put("ANULADA", Boolean.FALSE);
        p.put("FECHA_ANULACION_FACTURA", "-");
        p.put("MARCA_AGUA", null);
        p.put("PIE_EMITIDO_POR", "Sofía Martínez (MUESTRA)");
        p.put("PIE_FECHA_HORA", "01/11/2026 10:00");
        return p;
    }

    // =====================================================================
    // B11 + B13 -- contrato
    // =====================================================================

    private JRDataSource clausulas(LocalDate formalizacion) {
        Map<String, Object> p = parametrosContrato(formalizacion);
        List<ClausulaReporte> lista = servicioFormalizacion.construirClausulasContrato(p);
        return new JRBeanCollectionDataSource(lista);
    }

    private Map<String, Object> parametrosContrato(LocalDate formalizacion) {
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        p.put("EMPRESA_REGISTRO_BCP", "Registro BCP N.º 1234");
        p.put("EMPRESA_REPRESENTANTE", "Ramón Benítez, C.I. N.º 2.845.117");
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        // B11: lo que viene a mirar esta muestra -- el día apocopado, del mismo dueño.
        p.put("FECHA_FORMALIZACION_LETRAS", ConversorFechasALetras.comoFechaConArticulo(formalizacion));
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Joel Jesús Acosta");
        p.put("CLIENTE_CEDULA", "3.637.936");
        // B13: el domicilio capitalizado, armado con los mismos métodos del sistema.
        p.put("CLIENTE_DOMICILIO", domicilioCapitalizado());
        p.put("CLIENTE_TELEFONO", "(0981) 123 456");
        p.put("DESTINO_CREDITO", "capital de trabajo para su despensa");
        p.put("MONTO_APROBADO", "1.000.000 Gs.");
        p.put("MONTO_APROBADO_LETRAS", enLetras(new BigDecimal("1000000")));
        p.put("PLAZO_MESES", 6);
        p.put("PRIMERA_FECHA_VENCIMIENTO", "30/11/2026");
        p.put("DIA_VENCIMIENTO_ELEGIDO", 28);
        p.put("ULTIMA_FECHA_VENCIMIENTO", "28/04/2027");
        p.put("TEA", "27,00 %");
        p.put("TNA", "24,25 %");
        p.put("CFT", "30,61 %");
        p.put("CUOTA_1", "180.609 Gs.");
        p.put("TOTAL_A_PAGAR", "1.078.740 Gs.");
        p.put("TOTAL_A_PAGAR_LETRAS", enLetras(new BigDecimal("1078740")));
        p.put("PORCENTAJE_PUNITORIO", "30,00 %");
        p.put("CUOTAS_ALERTA_GARANTE", 3);
        p.put("USUARIO_EMISOR", "Ramón Benítez (MUESTRA)");
        p.put("USUARIO_EMISOR_CEDULA", "2.845.117");
        p.put("FECHA_EMISION", "01/11/2026 10:00");
        return p;
    }

    /**
     * B13: el domicilio como va a quedar guardado de ahora en adelante -- se pasa por el MISMO
     * validarDomicilio que normaliza al guardar y después por el MISMO construirTextoDomicilio que
     * arma el texto legal. El dato de entrada es el que hay hoy en la base ("mburicao 444", "Jara").
     */
    private String domicilioCapitalizado() {
        PersonaDomicilio domicilio = new PersonaDomicilio();
        domicilio.setIdCiudad(1L);
        domicilio.setDireccionDomicilio("mburicao 444");
        domicilio.setBarrio("Jara,");
        domicilio.setReferenciaDomicilio("porton verde");
        domicilio.setTipoVivienda("PROPIA");
        domicilio.setAntiguedadDomicilioAnios(3);
        servicioClientes.validarDomicilio(domicilio);
        return servicioClientes.construirTextoDomicilio(domicilio.getDireccionDomicilio(),
                domicilio.getBarrio(), "Asunción", "Asunción", null);
    }

    // =====================================================================

    private void generar(String reporte, String nombre, Map<String, Object> parametros, JRDataSource datos)
            throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(
                new File(CARPETA_REPORTES, reporte + ".jrxml").getPath());
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros, datos);

        File pdf = new File(CARPETA_PDF, nombre + ".pdf");
        Files.write(pdf.toPath(), JasperExportManager.exportReportToPdf(impreso));

        int ancho = Math.round(impreso.getPageWidth() * ZOOM_PNG);
        int alto = Math.round(impreso.getPageHeight() * ZOOM_PNG);
        for (int pagina = 0; pagina < impreso.getPages().size(); pagina++) {
            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D lienzo = imagen.createGraphics();
            lienzo.setColor(Color.WHITE);
            lienzo.fillRect(0, 0, ancho, alto);
            JRGraphics2DExporter exportador = new JRGraphics2DExporter();
            exportador.setExporterInput(new SimpleExporterInput(impreso));
            SimpleGraphics2DExporterOutput salida = new SimpleGraphics2DExporterOutput();
            salida.setGraphics2D(lienzo);
            exportador.setExporterOutput(salida);
            SimpleGraphics2DReportConfiguration configuracion = new SimpleGraphics2DReportConfiguration();
            configuracion.setPageIndex(pagina);
            configuracion.setZoomRatio(ZOOM_PNG);
            exportador.setConfiguration(configuracion);
            exportador.exportReport();
            lienzo.dispose();
            ImageIO.write(imagen, "png", new File(CARPETA_PNG, nombre + "_p" + (pagina + 1) + ".png"));
        }
        System.out.println(nombre + ": " + impreso.getPages().size() + " pág. -> " + pdf.getName());
    }

    private static String enLetras(BigDecimal monto) {
        return ConversorNumerosALetras.comoLetrasConGuaranies(monto).toUpperCase(new Locale("es"));
    }

    private static BigDecimal desformatear(String montoFormateado) {
        return new BigDecimal(montoFormateado.replace(" Gs.", "").replace(".", "").trim());
    }

    /**
     * El detalle del recibo, igual que lo arma el sistema: una fila por cuota aplicada
     * ({@code ServicioCobranza:2586}, con {@link FilaReciboCuota}). Los valores son los del cobro
     * real de CC-2026-00004, cuota 1/6.
     */
    private JRDataSource filasDelRecibo() {
        List<FilaReciboCuota> filas = new ArrayList<>();
        filas.add(new FilaReciboCuota("1/6",
                FormateadorNumeros.comoGuaranies(new BigDecimal("158479")),
                FormateadorNumeros.comoGuaranies(new BigDecimal("20118")),
                FormateadorNumeros.comoGuaranies(BigDecimal.ZERO),
                FormateadorNumeros.comoGuaranies(BigDecimal.ZERO),
                FormateadorNumeros.comoGuaranies(new BigDecimal("2012")),
                FormateadorNumeros.comoGuaranies(new BigDecimal("180609"))));
        return new JRBeanCollectionDataSource(filas);
    }
}
