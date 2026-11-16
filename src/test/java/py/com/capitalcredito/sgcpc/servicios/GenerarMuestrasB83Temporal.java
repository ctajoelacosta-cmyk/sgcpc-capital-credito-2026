package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JREmptyDataSource;
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
import py.com.capitalcredito.sgcpc.dto.FilaReciboCuota;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;

/**
 * TEMPORAL -- B83 + B58, 29/10/2026 (pedido del docente y de Joel).
 *
 * <p>Genera las vistas previas del <b>recibo rediseñado como talonario</b>, <b>una por rama</b>
 * (1 cuota, varias cuotas, anticipo, cancelación total, sin factura, revertido), y la del
 * <b>comprobante de desembolso</b> con las palabras nuevas de los ejemplares (B58). Datos de
 * ejemplo marcados "(MUESTRA)".</p>
 *
 * <ul>
 *   <li><b>No toca el repositorio</b>: compila los {@code .jrxml} <b>propuestos</b>, que viven
 *       fuera de {@code sistema/} en {@code ../pruebas/b83_muestra/}. Los
 *       {@code src/main/resources/reportes/*.jrxml} y sus {@code .jasper} quedan intactos hasta
 *       que Joel apruebe el diseño, así que {@code mvn test} sigue en verde y nadie puede emitir
 *       un documento con un {@code .jasper} desincronizado.</li>
 *   <li><b>No toca la base</b>: ni un {@code SELECT}.</li>
 *   <li><b>No cambia ningún dato</b>: el rediseño es solo presentación, así que los parámetros de
 *       estas muestras son los mismos que arma {@code ServicioCobranza.generarPdfRecibo}.</li>
 * </ul>
 *
 * <p>No matchea {@code *Test}/{@code Test*}: {@code mvn test} nunca la corre. Se invoca con
 * {@code mvn -Dtest=GenerarMuestrasB83Temporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}.</b></p>
 */
public class GenerarMuestrasB83Temporal {

    private static final String RECIBO = "../pruebas/b83_muestra/recibo_propuesto.jrxml";
    private static final String DESEMBOLSO = "../pruebas/b83_muestra/comprobante_desembolso_propuesto.jrxml";
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/b83_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_b83"));
    private static final float ZOOM_PNG = 2.0f;

    @Test
    public void generarUnaMuestraPorRama() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();
        JasperReport recibo = JasperCompileManager.compileReport(RECIBO);

        // 1) Cobro normal de UNA cuota, con factura asociada.
        Map<String, Object> unaCuota = base();
        generar(recibo, "b83_1_una_cuota", unaCuota, filas(1));

        // 2) Cobro de VARIAS cuotas (3), sin factura asociada, con promesa de pago.
        Map<String, Object> varias = base();
        varias.put("TIENE_FACTURA", Boolean.FALSE);
        varias.put("APLICADO", "868.410 Gs.");
        varias.put("APLICADO_LETRAS", "OCHOCIENTOS SESENTA Y OCHO MIL CUATROCIENTOS DIEZ GUARANÍES");
        varias.put("EFECTIVO_RECIBIDO", "900.000 Gs.");
        varias.put("VUELTO", "31.590 Gs.");
        varias.put("TOTAL_CAPITAL", "700.000 Gs.");
        varias.put("TOTAL_INTERES", "153.100 Gs.");
        varias.put("TOTAL_IVA_CUOTAS", "15.310 Gs.");
        varias.put("TOTAL_FILAS", "868.410 Gs.");
        varias.put("CUOTAS_PENDIENTES", 8);
        varias.put("TIENE_PROMESA", Boolean.TRUE);
        varias.put("PROMESA_FECHA", "15/12/2026");
        varias.put("PROMESA_MONTO", "289.470 Gs.");
        generar(recibo, "b83_2_varias_cuotas_sin_factura", varias, filas(3));

        // 3) Adelanto a capital (sin tabla de cuotas).
        Map<String, Object> anticipo = base();
        anticipo.put("ES_ANTICIPO", Boolean.TRUE);
        anticipo.put("TIENE_FACTURA", Boolean.FALSE);
        anticipo.put("APLICADO", "223.550 Gs.");
        anticipo.put("APLICADO_LETRAS", "DOSCIENTOS VEINTITRÉS MIL QUINIENTOS CINCUENTA GUARANÍES");
        anticipo.put("EFECTIVO_RECIBIDO", "230.000 Gs.");
        anticipo.put("VUELTO", "6.450 Gs.");
        anticipo.put("ANTICIPO_CAPITAL", "223.550 Gs.");
        anticipo.put("ANTICIPO_CUOTAS_RESTANTES", 11);
        anticipo.put("SALDO_CAPITAL_DESPUES", "2.776.450 Gs.");
        generar(recibo, "b83_3_anticipo", anticipo, filas(0));

        // 4) Cancelación total del préstamo.
        Map<String, Object> cancelacion = base();
        cancelacion.put("ES_CANCELACION", Boolean.TRUE);
        cancelacion.put("APLICADO", "2.834.600 Gs.");
        cancelacion.put("APLICADO_LETRAS", "DOS MILLONES OCHOCIENTOS TREINTA Y CUATRO MIL SEISCIENTOS GUARANÍES");
        cancelacion.put("EFECTIVO_RECIBIDO", "2.835.000 Gs.");
        cancelacion.put("VUELTO", "400 Gs.");
        cancelacion.put("TOTAL_CAPITAL", "2.776.450 Gs.");
        cancelacion.put("TOTAL_INTERES", "52.864 Gs.");
        cancelacion.put("TOTAL_IVA_CUOTAS", "5.286 Gs.");
        cancelacion.put("TOTAL_FILAS", "2.834.600 Gs.");
        cancelacion.put("SALDO_CAPITAL_DESPUES", "0 Gs.");
        cancelacion.put("CUOTAS_PENDIENTES", 0);
        cancelacion.put("TIENE_PROXIMO_VENCIMIENTO", Boolean.FALSE);
        generar(recibo, "b83_4_cancelacion_total", cancelacion, filas(2));

        // 5) Cobro con redondeo y cuota ya vencida (la rama de D-141 + D-145 P-9).
        Map<String, Object> vencida = base();
        vencida.put("TIENE_REDONDEO", Boolean.TRUE);
        vencida.put("REDONDEO", "450 Gs.");
        vencida.put("PROXIMA_CUOTA_VENCIDA", Boolean.TRUE);
        vencida.put("PROXIMA_CUOTA_RESTA", "120.000 Gs.");
        generar(recibo, "b83_5_redondeo_y_cuota_vencida", vencida, filas(1));

        // 6) Recibo REVERTIDO: franja roja (pageHeader) + sello diagonal.
        Map<String, Object> revertido = base();
        revertido.put("REVERTIDO", Boolean.TRUE);
        revertido.put("REVERSION_FECHA", "29/10/2026 22:10");
        revertido.put("REVERSION_AUTORIZADO_POR", "Ramón Benítez (MUESTRA)");
        revertido.put("REVERSION_MOTIVO", "La cajera cargó un monto equivocado.");
        revertido.put("MARCA_AGUA", GeneradorInformesPdf.construirMarcaAguaDiagonal("ANULADO"));
        generar(recibo, "b83_6_revertido", revertido, filas(1));

        // 7) Comprobante de desembolso: solo cambian las palabras de los ejemplares (B58).
        JasperReport desembolso = JasperCompileManager.compileReport(DESEMBOLSO);
        generar(desembolso, "b83_7_comprobante_desembolso", parametrosDesembolso(), new JREmptyDataSource(1));

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private JRDataSource filas(int cantidad) {
        List<FilaReciboCuota> filas = new ArrayList<>();
        String[] numeros = {"1/12", "2/12", "3/12"};
        for (int i = 0; i < cantidad; i++) {
            filas.add(new FilaReciboCuota(numeros[i % numeros.length], "233.334 Gs.", "51.033 Gs.",
                    "0 Gs.", "0 Gs.", "5.103 Gs.", "289.470 Gs."));
        }
        return filas.isEmpty() ? new JREmptyDataSource(1) : new JRBeanCollectionDataSource(filas);
    }

    private void generar(JasperReport compilado, String nombre, Map<String, Object> parametros, JRDataSource datos)
            throws Exception {
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros, datos);
        Files.write(new File(CARPETA_PDF, nombre + ".pdf").toPath(),
                JasperExportManager.exportReportToPdf(impreso));
        for (int pagina = 0; pagina < impreso.getPages().size(); pagina++) {
            int ancho = Math.round(impreso.getPageWidth() * ZOOM_PNG);
            int alto = Math.round(impreso.getPageHeight() * ZOOM_PNG);
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
        System.out.println(nombre + ": " + impreso.getPages().size() + " pagina(s)");
    }

    /** Rama por defecto: cobro normal de una cuota, con factura, sin promesa ni reversión. */
    private Map<String, Object> base() {
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        p.put("NUMERO_RECIBO", "REC-2026-000099");
        p.put("FECHA_HORA", "29/10/2026 21:40");
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        p.put("CLIENTE_CEDULA", "4.512.789");
        p.put("TIENE_FACTURA", Boolean.TRUE);
        p.put("FACTURA_NUMERO", "001-001-0000099");
        p.put("EFECTIVO_RECIBIDO", "300.000 Gs.");
        p.put("TIENE_VUELTO", Boolean.TRUE);
        p.put("VUELTO", "10.530 Gs.");
        p.put("APLICADO", "289.470 Gs.");
        p.put("APLICADO_LETRAS", "DOSCIENTOS OCHENTA Y NUEVE MIL CUATROCIENTOS SETENTA GUARANÍES");
        p.put("TIENE_REDONDEO", Boolean.FALSE);
        p.put("REDONDEO", "0 Gs.");
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
        p.put("ANTICIPO_PROXIMA_CUOTA_FECHA", "30/11/2026");
        p.put("ANTICIPO_PROXIMA_CUOTA_MONTO", "289.470 Gs.");
        p.put("ANTICIPO_ULTIMO_VENCIMIENTO", "28/09/2027");
        p.put("TOTAL_CAPITAL", "233.334 Gs.");
        p.put("TOTAL_INTERES", "51.033 Gs.");
        p.put("TOTAL_MORATORIO", "0 Gs.");
        p.put("TOTAL_PUNITORIO", "0 Gs.");
        p.put("TOTAL_IVA_CUOTAS", "5.103 Gs.");
        p.put("TOTAL_FILAS", "289.470 Gs.");
        p.put("SALDO_CAPITAL_DESPUES", "2.543.116 Gs.");
        p.put("CUOTAS_PENDIENTES", 10);
        p.put("TIENE_PROXIMO_VENCIMIENTO", Boolean.TRUE);
        p.put("PROXIMA_CUOTA", "2/12");
        p.put("PROXIMO_VENCIMIENTO", "28/12/2026");
        p.put("PROXIMA_CUOTA_VENCIDA", Boolean.FALSE);
        p.put("PROXIMA_CUOTA_RESTA", "-");
        p.put("USUARIO_EMISOR", "Sofía Martínez");
        p.put("FECHA_EMISION", "29/10/2026 21:40");
        p.put("REVERTIDO", Boolean.FALSE);
        p.put("REVERSION_FECHA", "-");
        p.put("REVERSION_AUTORIZADO_POR", "-");
        p.put("REVERSION_MOTIVO", "-");
        p.put("MARCA_AGUA", null);
        return p;
    }

    private Map<String, Object> parametrosDesembolso() {
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("NUMERO_COMPROBANTE", "DES-2026-000099 (MUESTRA)");
        p.put("FECHA_HORA", "29/10/2026 16:42");
        p.put("CAJA_SESION", "Sesión 10 — 29/10/2026");
        p.put("CAJERA_NOMBRE", "Sofía Martínez (MUESTRA)");
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        p.put("NUMERO_CONTRATO", "CC-2026-00099");
        p.put("NUMERO_PAGARE", "CC-2026-00099");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        p.put("CLIENTE_CEDULA", "4.512.789");
        p.put("CLIENTE_DOMICILIO", "Avda. Mariscal López 2345 c/ Boquerón, Asunción");
        p.put("MONTO_APROBADO", "3.000.000 Gs.");
        p.put("MONTO_DEDUCCIONES", "0 Gs. (sin comisiones ni gastos)");
        p.put("MONTO_NETO", "3.000.000 Gs.");
        p.put("MONTO_NETO_LETRAS", "TRES MILLONES DE GUARANÍES");
        p.put("FORMA_ENTREGA", "Efectivo");
        p.put("PLAZO_MESES", 12);
        p.put("CUOTA_1", "289.470 Gs.");
        p.put("CUOTA_ULTIMA", "289.470 Gs.");
        p.put("CUOTA_ES_DECRECIENTE", Boolean.FALSE);
        p.put("TEA", "27,00 %");
        p.put("CFT", "30,61 %");
        p.put("PRIMER_VENCIMIENTO", "30/11/2026");
        p.put("USUARIO_EMISOR", "Sofía Martínez (MUESTRA)");
        p.put("FECHA_EMISION", "29/10/2026 16:42");
        p.put("MARCA_REIMPRESION", "");
        return p;
    }
}
