package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/**
 * HERRAMIENTA TEMPORAL (11/11/2026, B127) — muestra del <b>pagaré</b> con un total que termina
 * en <b>1</b>, que es el caso que destapó el defecto: la frase decía
 * <i>"… DOSCIENTOS SETENTA Y <b>UNO</b> GUARANÍES"</i> en el documento que firma el cliente.
 *
 * <ul>
 *   <li><b>El texto se le pide a la misma clase que usa el sistema</b>
 *       ({@code ConversorNumerosALetras.comoLetrasConGuaranies(...).toUpperCase("es")}, igual que
 *       {@code ServicioFormalizacion}), nunca se copia a mano: una muestra con el texto escrito a
 *       mano no probaría nada del cambio.</li>
 *   <li><b>El monto es el de un préstamo real de la base de pruebas</b>: 800.000 Gs. a 12 meses
 *       (SOL-2026-0197), cuyo total con IVA da 919.271 Gs. El plan lo genera el mismo motor
 *       financiero del sistema, así que el total no está inventado.</li>
 *   <li><b>El {@code .jrxml} se compila EN MEMORIA</b> y no se toca ni él ni su {@code .jasper}:
 *       B127 no cambia la plantilla, solo el texto que Java le pasa en
 *       {@code TOTAL_A_PAGAR_LETRAS}.</li>
 *   <li><b>El {@code (MUESTRA)} va en el nombre del cliente</b>, nunca en el monto ni en el
 *       identificador — son los campos que se están mirando.</li>
 *   <li><b>No corre con {@code mvn test}</b>:
 *       {@code mvn -Dtest=GenerarMuestraPagareB127Temporal test}.</li>
 * </ul>
 */
public class GenerarMuestraPagareB127Temporal {

    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/pagare_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng", "target/b127_png"));
    private static final float ZOOM_PNG = 2.0f;
    private static final String RUTA_JRXML = "src/main/resources/reportes/pagare.jrxml";
    private static final LocalDate ELEGIDA = LocalDate.of(2026, 12, 22);

    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();

    @Test
    public void generarMuestraDelPagareConTotalTerminadoEnUno() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        // El plan REAL: el mismo motor, los mismos argumentos que una solicitud de 800.000 a 12
        // meses con la TEA vigente. El total sale de sumar las cuotas, igual que
        // ServicioFormalizacion.construirParametrosDocumentos.
        List<Cuota> plan = servicioMotorFinanciero.generarPlanPagos(
                new BigDecimal("800000"), 12, new BigDecimal("0.27"), ELEGIDA,
                Collections.<LocalDate>emptySet());
        BigDecimal totalAPagar = BigDecimal.ZERO;
        for (Cuota cuota : plan) {
            totalAPagar = totalAPagar.add(cuota.getTotalProgramado());
        }
        String letras = ConversorNumerosALetras.comoLetrasConGuaranies(totalAPagar)
                .toUpperCase(new Locale("es"));

        System.out.println("=== B127: el pagaré con un total terminado en 1 ===");
        System.out.println("  total a pagar      : " + FormateadorNumeros.comoGuaranies(totalAPagar));
        System.out.println("  en letras (AHORA)  : " + letras);
        if (!totalAPagar.toBigInteger().toString().endsWith("1")) {
            throw new IllegalStateException("La muestra no sirve: el total no termina en 1 ("
                    + totalAPagar + "). Hay que elegir otro monto o plazo.");
        }
        if (letras.contains("UNO GUARANÍES")) {
            throw new IllegalStateException("La apócope NO se aplicó: " + letras);
        }

        Map<String, Object> parametros = parametrosDelPagare(plan, totalAPagar, letras);
        avisarParametrosFaltantes(parametros);

        JasperReport compilado = JasperCompileManager.compileReport(RUTA_JRXML);
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros, new JREmptyDataSource(1));
        File pdf = new File(CARPETA_PDF, "pagare_b127_total_terminado_en_uno.pdf");
        Files.write(pdf.toPath(), JasperExportManager.exportReportToPdf(impreso));
        exportarPng(impreso, "pagare_b127");

        System.out.println("  PDF -> " + pdf.getCanonicalPath());
        System.out.println("  PNG -> " + CARPETA_PNG.getCanonicalPath());
    }

    /** Los mismos nombres y formatos que ServicioFormalizacion le pasa a pagare.jasper. */
    private Map<String, Object> parametrosDelPagare(List<Cuota> plan, BigDecimal totalAPagar,
            String letras) throws Exception {
        GenerarMuestrasC3C12Temporal fuente = new GenerarMuestrasC3C12Temporal();
        Method comunes = GenerarMuestrasC3C12Temporal.class.getDeclaredMethod(
                "parametrosComunes", LocalDate.class, List.class);
        comunes.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) comunes.invoke(
                fuente, plan.get(0).getFechaVencimiento(), plan);
        // Lo que esta muestra viene a mirar: el monto y su texto, calculados de verdad.
        p.put("TOTAL_A_PAGAR", FormateadorNumeros.comoGuaranies(totalAPagar));
        p.put("TOTAL_A_PAGAR_LETRAS", letras);
        p.put("MONTO_APROBADO", FormateadorNumeros.comoGuaranies(new BigDecimal("800000")));
        p.put("MONTO_APROBADO_LETRAS", ConversorNumerosALetras
                .comoLetrasConGuaranies(new BigDecimal("800000")).toUpperCase(new Locale("es")));
        p.put("PLAZO_MESES", 12);
        // Formato real del identificador (13 caracteres): es uno de los campos cuyo ancho se ve.
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099");
        p.put("FECHA_FORMALIZACION", "11/11/2026");
        return p;
    }

    /** El cruce del skill de reportes: ningún $P{...} del .jrxml sin valor en el mapa. */
    private void avisarParametrosFaltantes(Map<String, Object> parametros) throws Exception {
        String fuente = new String(Files.readAllBytes(Paths.get(RUTA_JRXML)), StandardCharsets.UTF_8);
        Matcher usados = Pattern.compile("[$]P[{]([A-Z_0-9]+)[}]").matcher(fuente);
        List<String> faltantes = new ArrayList<>();
        while (usados.find()) {
            if (!parametros.containsKey(usados.group(1)) && !faltantes.contains(usados.group(1))) {
                faltantes.add(usados.group(1));
            }
        }
        System.out.println("  parámetros sin valor: " + (faltantes.isEmpty() ? "ninguno" : faltantes));
    }

    private void exportarPng(JasperPrint impreso, String nombre) throws Exception {
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
    }
}
