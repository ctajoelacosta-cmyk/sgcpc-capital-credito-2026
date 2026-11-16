package py.com.capitalcredito.sgcpc.herramientas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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
import py.com.capitalcredito.sgcpc.dto.FilaFacturaReporte;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;

/**
 * TEMPORAL -- rediseño de la factura, 27/10/2026 (pedido del docente y de Joel, RF23/RF33).
 *
 * <p>Genera vistas previas de {@code factura.jrxml} con DATOS DE EJEMPLO marcados "(MUESTRA)",
 * para que Joel vea el diseño nuevo antes de aprobarlo. Igual que la
 * {@code GenerarMuestrasRF23Temporal} de octubre (ya borrada), con dos diferencias importantes:</p>
 *
 * <ul>
 *   <li><b>No toca la base</b>: ni un {@code SELECT}. Todos los datos están escritos acá
 *       (decisión de Joel, 27/10/2026, punto 4).</li>
 *   <li><b>No toca {@code factura.jasper}</b>: compila el {@code .jrxml} EN MEMORIA
 *       ({@code JasperCompileManager.compileReport}), así que el {@code .jasper} commiteado
 *       queda intacto hasta que Joel apruebe el diseño y se corra
 *       {@code herramientas/compilar_reportes.sh factura}.</li>
 * </ul>
 *
 * <p>No graba nada en ninguna base, no consume numeración de timbrado y no regenera ningún
 * documento ya emitido (D-94). Genera, por cada escenario, el PDF (para Joel) y un PNG (para
 * revisar el diseño sin herramientas externas: lo exporta el propio JasperReports con
 * {@link JRGraphics2DExporter} sobre un {@link BufferedImage}, no hace falta instalar nada).</p>
 *
 * <p>El nombre NO matchea el patrón {@code *Test}/{@code Test*} de Surefire: {@code mvn test}
 * nunca la corre. Se invoca a propósito con
 * {@code mvn -Dtest=GenerarMuestrasFacturaTemporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}: esta clase se borra antes de la
 * entrega final.</b></p>
 */
public class GenerarMuestrasFacturaTemporal {

    private static final File JRXML = new File("src/main/resources/reportes/factura.jrxml");
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/factura_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_factura"));
    /** 2.0 = el PNG sale al doble del tamaño del PDF (1190x1684), legible para revisar el diseño. */
    private static final float ZOOM_PNG = 2.0f;

    @Test
    public void generarLasTresMuestras() throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(JRXML.getPath());
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        generar(compilado, "factura_muestra_1_renglon", lineas(1), false);
        generar(compilado, "factura_muestra_11_renglones", lineas(11), false);
        generar(compilado, "factura_muestra_anulada", lineas(1), true);

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private void generar(JasperReport compilado, String nombre, List<FilaFacturaReporte> filas,
            boolean anulada) throws Exception {
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros(filas, anulada),
                new JRBeanCollectionDataSource(filas));

        File pdf = new File(CARPETA_PDF, nombre + ".pdf");
        Files.write(pdf.toPath(), JasperExportManager.exportReportToPdf(impreso));

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
        configuracion.setPageIndex(0);
        configuracion.setZoomRatio(ZOOM_PNG);
        exportador.setConfiguration(configuracion);
        exportador.exportReport();
        lienzo.dispose();
        ImageIO.write(imagen, "png", new File(CARPETA_PNG, nombre + ".png"));

        System.out.println(nombre + ": " + filas.size() + " renglón(es), "
                + impreso.getPages().size() + " página(s)" + (anulada ? ", ANULADA" : ""));
    }

    /** Mismos valores que bd/03_parametros.sql, pero con el cliente, la operación, el recibo y
     * el número de factura marcados "(MUESTRA)" para que nadie los confunda con un documento
     * real. */
    private Map<String, Object> parametros(List<FilaFacturaReporte> filas, boolean anulada) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (int i = 0; i < filas.size(); i++) {
            total = total.add(totalDeLinea(i));
            iva = iva.add(ivaDeLinea(i));
        }

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        parametros.put("EMPRESA_RUC", "80123456-5");
        parametros.put("EMPRESA_ACTIVIDAD", "Otorgante de Crédito Dinerario (OCD) / Casa de Crédito");
        parametros.put("EMPRESA_DOMICILIO",
                "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        parametros.put("EMPRESA_TELEFONO", "(021) 941 890");
        parametros.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        parametros.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        parametros.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        parametros.put("TIMBRADO_NUMERO", "16842795");
        parametros.put("TIMBRADO_DESDE", "01/09/2026");
        parametros.put("TIMBRADO_HASTA", "31/08/2027");
        parametros.put("FACTURA_NUMERO", "001-001-0000099");
        parametros.put("FECHA_EMISION", "27/10/2026");
        parametros.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        parametros.put("CLIENTE_DOCUMENTO", "4.512.789");
        parametros.put("CLIENTE_DOMICILIO",
                "Avda. Mariscal López 3487 c/ Dr. Weiss, Barrio Villa Morra, Asunción");
        parametros.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        parametros.put("NUMERO_RECIBO", "REC-2026-000099 (MUESTRA)");
        parametros.put("TOTAL_EXENTA", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TOTAL_GRAVADA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("IVA_5", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TOTAL_GRAVADA_10", FormateadorNumeros.comoGuaranies(total));
        parametros.put("IVA_10", FormateadorNumeros.comoGuaranies(iva));
        parametros.put("TOTAL", FormateadorNumeros.comoGuaranies(total));
        parametros.put("TOTAL_LETRAS", enLetrasDeMuestra(total));
        parametros.put("ANULADA", anulada);
        parametros.put("FECHA_ANULACION_FACTURA", anulada ? "27/10/2026" : "-");
        parametros.put("MARCA_AGUA",
                anulada ? GeneradorInformesPdf.construirMarcaAguaDiagonal("ANULADA") : null);
        parametros.put("PIE_EMITIDO_POR", "Lorena Beatriz Cabral (MUESTRA)");
        parametros.put("PIE_FECHA_HORA", "27/10/2026 08:14");
        return parametros;
    }

    private List<FilaFacturaReporte> lineas(int cantidad) {
        List<FilaFacturaReporte> filas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            filas.add(new FilaFacturaReporte(descripcionDeLinea(i, cantidad),
                    FormateadorNumeros.comoGuaranies(netoDeLinea(i)),
                    FormateadorNumeros.comoGuaranies(ivaDeLinea(i)),
                    FormateadorNumeros.comoGuaranies(totalDeLinea(i))));
        }
        return filas;
    }

    /** La línea 3 es a propósito larga: así la muestra de 11 renglones prueba el caso de una
     * descripción que ocupa 2 renglones (corrección de Joel 380b400, punto C8). */
    private String descripcionDeLinea(int indice, int cantidad) {
        if (cantidad == 1) {
            return "Interés compensatorio cuota 3/12 (vencimiento 24/11/2026)";
        }
        if (indice == 2) {
            return "Interés compensatorio cuota " + (indice + 1) + "/12 (vencimiento 24/"
                    + (indice + 1) + "/2026) — cancelación total anticipada del préstamo,"
                    + " incluye el ajuste del interés devengado hasta la fecha del pago";
        }
        return "Interés compensatorio cuota " + (indice + 1) + "/12 (vencimiento 24/"
                + String.format("%02d", (indice % 12) + 1) + "/2027)";
    }

    private BigDecimal netoDeLinea(int indice) {
        return new BigDecimal(10059 + indice * 737);
    }

    private BigDecimal ivaDeLinea(int indice) {
        return new BigDecimal(1006 + indice * 74);
    }

    private BigDecimal totalDeLinea(int indice) {
        return netoDeLinea(indice).add(ivaDeLinea(indice));
    }

    /** Solo para la muestra: el sistema real usa ConversorNumerosALetras. Acá alcanza con un
     * texto largo y realista para ver si entra en la celda. */
    private String enLetrasDeMuestra(BigDecimal total) {
        if (total.intValue() == 11065) {
            return "ONCE MIL SESENTA Y CINCO GUARANÍES";
        }
        return "CIENTO CINCUENTA Y NUEVE MIL NOVECIENTOS CINCUENTA Y CINCO GUARANÍES (MUESTRA)";
    }
}
