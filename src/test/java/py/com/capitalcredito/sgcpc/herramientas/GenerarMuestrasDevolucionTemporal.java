package py.com.capitalcredito.sgcpc.herramientas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;

/**
 * TEMPORAL -- B78, constancia de devolución del pagaré, 28/10/2026 (hallazgo de Joel del
 * 25/10/2026 en la primera pasada de RF29, aprobado el 28/10).
 *
 * <p>Genera vistas previas de {@code constancia_devolucion.jrxml} con DATOS DE EJEMPLO marcados
 * "(MUESTRA)", para que Joel vea el tercer bloque de firma ("Entregó el pagaré") antes de
 * aprobarlo. Misma herramienta que {@code GenerarMuestrasFacturaTemporal} (27/10/2026), con sus
 * dos mismas propiedades:</p>
 *
 * <ul>
 *   <li><b>No toca la base</b>: ni un {@code SELECT}. Todos los datos están escritos acá (los
 *       valores de empresa son los de {@code bd/03_parametros.sql}).</li>
 *   <li><b>No toca {@code constancia_devolucion.jasper}</b>: compila el {@code .jrxml} EN MEMORIA
 *       ({@code JasperCompileManager.compileReport}), así que el {@code .jasper} commiteado queda
 *       intacto hasta que Joel apruebe el diseño y se corra
 *       {@code herramientas/compilar_reportes.sh constancia_devolucion}.</li>
 * </ul>
 *
 * <p>No graba nada en ninguna base, no consume el numerador {@code DEVOLUCION} y no regenera
 * ningún documento ya emitido (D-94: {@code DEV-2026-000001} y {@code DEV-2026-000002} conservan
 * el PDF que ya está guardado en PostgreSQL). Genera, por cada escenario, el PDF (para Joel) y un
 * PNG (para revisar el diseño sin herramientas externas: lo exporta el propio JasperReports con
 * {@link JRGraphics2DExporter} sobre un {@link BufferedImage}).</p>
 *
 * <p>Los dos escenarios son las dos ramas de {@code TEXTO_CIERRE_OPERACION} (RF29 §6e): con
 * cancelación total (cita el {@code CAN-}) y sin ella (el préstamo se FINALIZÓ pagando la última
 * cuota normal). El texto no se escribe acá: se pide a la misma
 * {@link ServicioFormalizacion#construirTextoCierreOperacionDevolucion} que usa el sistema.</p>
 *
 * <p>El nombre NO matchea el patrón {@code *Test}/{@code Test*} de Surefire: {@code mvn test}
 * nunca la corre. Se invoca a propósito con
 * {@code mvn -Dtest=GenerarMuestrasDevolucionTemporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}: esta clase se borra antes de la
 * entrega final.</b></p>
 */
public class GenerarMuestrasDevolucionTemporal {

    private static final File JRXML = new File("src/main/resources/reportes/constancia_devolucion.jrxml");
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/devolucion_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_devolucion"));
    /** 2.0 = el PNG sale al doble del tamaño del PDF (1190x1684), legible para revisar el diseño. */
    private static final float ZOOM_PNG = 2.0f;

    private final ServicioFormalizacion servicio = new ServicioFormalizacion();

    @Test
    public void generarLasDosMuestras() throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(JRXML.getPath());
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        generar(compilado, "devolucion_muestra_con_cancelacion", true);
        generar(compilado, "devolucion_muestra_sin_cancelacion", false);

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private void generar(JasperReport compilado, String nombre, boolean conCancelacion) throws Exception {
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros(conCancelacion),
                new JREmptyDataSource(1));

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

        System.out.println(nombre + ": " + impreso.getPages().size() + " página(s)"
                + (conCancelacion ? ", con CAN" : ", sin CAN"));
    }

    /** Mismos valores de empresa que {@code bd/03_parametros.sql}, pero con el cliente, la
     * operación y el número de constancia marcados "(MUESTRA)" para que nadie los confunda con un
     * documento real. */
    private Map<String, Object> parametros(boolean conCancelacion) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        parametros.put("EMPRESA_RUC", "80123456-5");
        parametros.put("EMPRESA_DOMICILIO",
                "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        parametros.put("EMPRESA_TELEFONO", "(021) 941 890");
        parametros.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        parametros.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        parametros.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        parametros.put("REPRESENTANTE_NOMBRE", "Ramón Benítez");
        parametros.put("REPRESENTANTE_CEDULA", "2.845.117");
        parametros.put("NUMERO_CONSTANCIA_DEVOLUCION", "DEV-2026-000099 (MUESTRA)");
        parametros.put("FECHA_DEVOLUCION", "28/10/2026");
        parametros.put("FECHA_DEVOLUCION_LARGA", "28 de octubre de 2026");
        parametros.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        parametros.put("CLIENTE_CEDULA", "4.512.789");
        parametros.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        parametros.put("NUMERO_PAGARE", "CC-2026-00099");
        // Ya formateado, igual que en el sistema real (FormateadorNumeros.comoGuaranies).
        parametros.put("MONTO_DESEMBOLSADO", "8.500.000 Gs.");
        parametros.put("FECHA_DESEMBOLSO", "14/02/2026");
        parametros.put("FECHA_CANCELACION", "27/10/2026");
        // El texto no se escribe acá: sale de la misma clase que usa el sistema (es un método
        // puro, sin Connection) -- así la muestra no puede mentir sobre el texto real.
        parametros.put("TEXTO_CIERRE_OPERACION", servicio.construirTextoCierreOperacionDevolucion(
                conCancelacion, conCancelacion ? "CAN-2026-000099" : "-"));
        // B78: lo que esta muestra viene a mirar -- el tercer bloque de firma.
        parametros.put("USUARIO_EMISOR", "Lorenzo Ramírez (MUESTRA)");
        parametros.put("USUARIO_EMISOR_CEDULA", "3.991.204");
        parametros.put("FECHA_EMISION", "28/10/2026 10:42");
        return parametros;
    }
}
