package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
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
import py.com.capitalcredito.sgcpc.dto.FilaReciboCuota;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;

/**
 * HERRAMIENTA TEMPORAL (07/11/2026, B108 y medición de B59) -- vistas previas del recibo con el
 * <b>nombre más largo posible</b> en la línea de firma, para verificar que ya no se corta, y para
 * <b>medir</b> cuánto espacio en blanco queda al final de cada variante.
 *
 * <ul>
 *   <li><b>Compila el `.jrxml` REAL del repositorio</b> (ya con el cambio de B108), en memoria, así
 *       la muestra es exactamente lo que va a emitir el sistema.</li>
 *   <li><b>Solo lectura</b>: no toca la base ni ningún documento guardado. Los parámetros son los
 *       mismos valores que usa {@code GenerarMuestrasB83Temporal}, que ya se corrigió con las
 *       lecciones de la tanda 1 (el {@code (MUESTRA)} va en los identificadores y en el nombre del
 *       cliente, <b>nunca</b> en el nombre del emisor, que es justo el campo cuyo ancho se
 *       revisa).</li>
 *   <li><b>No corre con {@code mvn test}</b>: se invoca a propósito
 *       ({@code mvn -Dtest=GenerarMuestrasB108Temporal test}).</li>
 * </ul>
 */
public class GenerarMuestrasB108Temporal {

    /** El .jrxml del repositorio, no una copia: queremos ver lo que se va a emitir. */
    private static final String RECIBO = "src/main/resources/reportes/recibo.jrxml";
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/b108_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng", "target/b108_png"));
    private static final float ZOOM_PNG = 2.0f;

    /** El peor caso POSIBLE: el administrador tiene TOTAL en todos los permisos y puede cobrar. */
    private static final String EMISOR_MAS_LARGO = "Joel Jesús Acosta";

    @Test
    public void generarMuestras() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();
        JasperReport recibo = JasperCompileManager.compileReport(RECIBO);

        System.out.println("=== B108: la frase que va en la linea de firma ===");
        String frase = "Firma de la cajera (" + EMISOR_MAS_LARGO + ")";
        System.out.println("  \"" + frase + "\" -> " + frase.length() + " caracteres");

        Map<String, Object> normal = base();
        generar(recibo, "b108_1_cobro_normal_nombre_largo", normal, filas(3));

        Map<String, Object> anticipo = base();
        anticipo.put("ES_ANTICIPO", Boolean.TRUE);
        anticipo.put("TIENE_FACTURA", Boolean.FALSE);
        anticipo.put("FACTURA_NUMERO", "-");
        anticipo.put("ANTICIPO_INTERES", "12.450 Gs.");
        anticipo.put("ANTICIPO_IVA", "1.245 Gs.");
        anticipo.put("ANTICIPO_CAPITAL", "486.305 Gs.");
        anticipo.put("ANTICIPO_CUOTAS_RESTANTES", 7);
        anticipo.put("ANTICIPO_CUOTA_NUEVA", "289.470 Gs.");
        generar(recibo, "b108_2_anticipo_nombre_largo", anticipo, filas(0));

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

        // Medición para B59: hasta dónde llega el contenido impreso y cuánto queda en blanco.
        // El alto util es pageHeight menos los dos margenes; se mide el elemento impreso que
        // termina mas abajo, que es lo unico que decide donde "termina" el recibo en el papel.
        int utilDesde = impreso.getTopMargin();
        int utilHasta = impreso.getPageHeight() - impreso.getBottomMargin();
        int finDelContenido = 0;
        int cantidad = 0;
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            cantidad++;
            finDelContenido = Math.max(finDelContenido, elemento.getY() + elemento.getHeight());
        }
        // Y el hueco INTERNO mas grande: dos blancos distintos se confunden en B59. Uno es el que
        // queda al final de la hoja (abajo) y el otro es el agujero que dejan, dentro de la banda
        // summary, los elementos con printWhenExpression !ES_ANTICIPO cuando no se imprimen.
        List<int[]> tramos = new ArrayList<>();
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            if (elemento.getHeight() > 0 && elemento.getHeight() < 700) {
                tramos.add(new int[] {elemento.getY(), elemento.getY() + elemento.getHeight()});
            }
        }
        tramos.sort((a, b) -> Integer.compare(a[0], b[0]));
        int huecoMayor = 0;
        int huecoDesde = 0;
        int hasta = utilDesde;
        for (int[] tramo : tramos) {
            if (tramo[0] - hasta > huecoMayor) {
                huecoMayor = tramo[0] - hasta;
                huecoDesde = hasta;
            }
            hasta = Math.max(hasta, tramo[1]);
        }
        System.out.println(nombre + ": " + impreso.getPages().size() + " pagina(s), "
                + cantidad + " elementos impresos");
        System.out.println("    hueco interno mas grande: " + huecoMayor
                + " pt (entre y=" + huecoDesde + " y y=" + (huecoDesde + huecoMayor) + ")");
        System.out.println("    area util: " + utilDesde + " a " + utilHasta
                + " (" + (utilHasta - utilDesde) + " pt)");
        System.out.println("    el contenido termina en y=" + finDelContenido
                + " -> quedan " + (utilHasta - finDelContenido) + " pt en blanco al final");
    }

    /** Rama por defecto, con los MISMOS valores que la herramienta de B83 ya verificada. */
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
        p.put("FECHA_HORA", "07/11/2026 13:05");
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
        // La línea "Son:" del recibo imprime RECIBIDO_LETRAS, no APLICADO_LETRAS. El servicio real
        // setea los dos (ServicioCobranza:2630 y :2638); la herramienta de B83, de la que se copió
        // esta base, solo ponía el primero, así que su muestra salía con "Son: null." -- un defecto
        // de la MUESTRA, no del recibo. Es la trampa que la skill ya tenía anotada de la tanda 1.
        p.put("RECIBIDO_LETRAS", "TRESCIENTOS MIL GUARANÍES");
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
        // B108: el peor caso posible, SIN marca de muestra -- este es el campo cuyo ancho se revisa.
        p.put("USUARIO_EMISOR", EMISOR_MAS_LARGO);
        p.put("FECHA_EMISION", "07/11/2026 13:05");
        p.put("REVERTIDO", Boolean.FALSE);
        p.put("REVERSION_FECHA", "-");
        p.put("REVERSION_AUTORIZADO_POR", "-");
        p.put("REVERSION_MOTIVO", "-");
        p.put("MARCA_AGUA", null);
        return p;
    }
}
