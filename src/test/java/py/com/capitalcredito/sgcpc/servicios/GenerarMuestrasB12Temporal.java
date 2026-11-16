package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRPrintElement;
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
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;

/**
 * HERRAMIENTA TEMPORAL (07/11/2026, B12) -- mide el <b>hueco de la página 1 del contrato</b> y
 * compara las dos opciones de arreglo, para que Joel decida con números y con imágenes.
 *
 * <ul>
 *   <li><b>ANTES</b>: el `.jrxml` del repositorio, tal cual está.</li>
 *   <li><b>Opción (a), subreporte — SIMULADA</b>: el `pageHeader` en altura 0. Así quedaría la
 *       <b>página 1</b> con el subreporte. <b>Lo que la simulación NO muestra</b>: el subreporte
 *       además le devuelve el membrete a las páginas 2 y siguientes, que acá quedan sin él.</li>
 *   <li><b>Opción (b), membrete de continuación</b>: el membrete repetido pasa de 5 líneas (80 pt)
 *       a una sola (28 pt) — logo chico, razón social y "CONTRATO N.º ...". La página 1 ya trae el
 *       membrete completo en la banda "title", así que en las siguientes alcanza con decir de qué
 *       documento se trata.</li>
 *   <li><b>Las dos opciones son COPIAS fuera del repositorio</b>
 *       ({@code ../pruebas/b12_opciones/}), compiladas en memoria: el `.jrxml` y el `.jasper` del
 *       repositorio no se tocan y {@code mvn test} nunca se pone en rojo mientras Joel revisa.</li>
 *   <li><b>Los parámetros y las cláusulas son los REALES</b>: se reusan los de
 *       {@code GenerarMuestrasC3C12Temporal} (por reflexión, porque son privados) y
 *       {@code ServicioFormalizacion.construirClausulasContrato}, en vez de copiarlos a mano
 *       -- una muestra con parámetros inventados ya costó una revisión entera (tanda 1).</li>
 *   <li><b>No corre con {@code mvn test}</b>: {@code mvn -Dtest=GenerarMuestrasB12Temporal test}.</li>
 * </ul>
 */
public class GenerarMuestrasB12Temporal {

    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/b12_opciones"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng", "target/b12_png"));
    private static final float ZOOM_PNG = 2.0f;
    private static final LocalDate ELEGIDA = LocalDate.of(2026, 11, 28);

    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();

    @Test
    public void compararLasDosOpciones() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        List<Cuota> plan = servicioMotorFinanciero.generarPlanPagos(
                new java.math.BigDecimal("5000000"), 12, new java.math.BigDecimal("0.27"), ELEGIDA,
                java.util.Collections.<LocalDate>emptySet());
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setPrimeraFechaVencimiento(ELEGIDA);
        LocalDate efectiva = servicioFormalizacion.primeraFechaVencimientoEfectiva(plan, prestamo);

        // Los MISMOS parametros que la muestra ya verificada de C3/C12.
        GenerarMuestrasC3C12Temporal fuente = new GenerarMuestrasC3C12Temporal();
        Method constructor = GenerarMuestrasC3C12Temporal.class.getDeclaredMethod(
                "parametrosComunes", LocalDate.class, List.class);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> parametros = (Map<String, Object>) constructor.invoke(fuente, efectiva, plan);
        // B12: el identificador va SIN la marca "(MUESTRA)" a proposito. Es el campo cuyo ancho se
        // esta revisando (el numero del membrete de continuacion), y la marca le agrega 10
        // caracteres que no existen en produccion: con ella la muestra mostraria un texto cortado
        // que en el papel real no se corta. La marca sigue estando en el nombre del cliente.
        // Formato real, fijo: CC-AAAA-NNNNN, 13 caracteres.
        parametros.put("IDENTIFICADOR_OPERACION", "CC-2026-00099");
        List<ClausulaReporte> clausulas = servicioFormalizacion.construirClausulasContrato(parametros);

        System.out.println("=== B12: el hueco de la pagina 1 del contrato ===");
        medir("b12_0_antes", "src/main/resources/reportes/contrato.jrxml",
                parametros, new JRBeanCollectionDataSource(clausulas));
        medir("b12_a_subreporte_simulado", "../pruebas/b12_opciones/contrato_opcion_a_subreporte_simulado.jrxml",
                parametros, new JRBeanCollectionDataSource(clausulas));
        medir("b12_b_membrete_corto", "../pruebas/b12_opciones/contrato_opcion_b_membrete_corto.jrxml",
                parametros, new JRBeanCollectionDataSource(clausulas));

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private void medir(String nombre, String rutaJrxml, Map<String, Object> parametros, JRDataSource datos)
            throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(rutaJrxml);
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros, datos);
        Files.write(new File(CARPETA_PDF, nombre + ".pdf").toPath(),
                JasperExportManager.exportReportToPdf(impreso));

        // La pagina 1 es la del hueco; la 2 hace falta para ver el membrete de continuacion de
        // la opcion (b), que es su unico riesgo: si queda feo o pierde informacion, no sirve.
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

        // El hueco: el mayor tramo vertical sin ningun elemento impreso, en la pagina 1.
        List<int[]> tramos = new ArrayList<>();
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            if (elemento.getHeight() > 0 && elemento.getHeight() < 700) {
                tramos.add(new int[] {elemento.getY(), elemento.getY() + elemento.getHeight()});
            }
        }
        tramos.sort((a, b) -> Integer.compare(a[0], b[0]));
        int huecoMayor = 0;
        int huecoDesde = 0;
        int hasta = impreso.getTopMargin();
        for (int[] tramo : tramos) {
            if (tramo[0] - hasta > huecoMayor) {
                huecoMayor = tramo[0] - hasta;
                huecoDesde = hasta;
            }
            hasta = Math.max(hasta, tramo[1]);
        }
        // El hueco de B12 es el de ARRIBA: entre el recuadro del resumen y "En la ciudad de...".
        // El "hueco mayor" de toda la pagina es otro (el corte natural al pie), por eso se busca
        // el texto concreto en vez de quedarse con el maximo.
        int yDelTexto = -1;
        int finDelRecuadro = 0;
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            String texto = elemento instanceof net.sf.jasperreports.engine.JRPrintText
                    ? ((net.sf.jasperreports.engine.JRPrintText) elemento).getFullText() : null;
            if (texto != null && texto.startsWith("En la ciudad de") && yDelTexto < 0) {
                yDelTexto = elemento.getY();
            }
        }
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            int fin = elemento.getY() + elemento.getHeight();
            if (yDelTexto > 0 && fin <= yDelTexto && elemento.getHeight() < 700) {
                finDelRecuadro = Math.max(finDelRecuadro, fin);
            }
        }
        System.out.println("  " + nombre + ": " + impreso.getPages().size() + " paginas"
                + " | \"En la ciudad de...\" arranca en y=" + yDelTexto
                + ", lo anterior termina en y=" + finDelRecuadro
                + " -> HUECO DE B12 = " + (yDelTexto - finDelRecuadro) + " pt"
                + " | hueco mayor de toda la pagina (el corte al pie): " + huecoMayor + " pt");
    }
}
