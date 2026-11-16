package py.com.capitalcredito.sgcpc.servicios;

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
import py.com.capitalcredito.sgcpc.dto.FilaMovimientoArqueo;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;

/**
 * TEMPORAL -- C22, acta de arqueo, 03/11/2026 (decisión de Joel: "el papel dice la verdad").
 *
 * <p>Genera las dos ramas de la línea de firma del gerente, con DATOS DE EJEMPLO, para revisar el
 * diseño antes de emitir un acta de verdad:</p>
 *
 * <ol>
 *   <li><b>sin revisor</b> (el caso de casi todos los cierres, y el de la sesión 14): nadie quedó
 *       registrado como receptor, así que el acta imprime <i>"Recibí (firma): ______________"</i>
 *       en blanco, para firmar a mano;</li>
 *   <li><b>con revisor</b> (el de la sesión 9, con una diferencia ya revisada): el acta nombra al
 *       gerente que el sistema sí tiene registrado.</li>
 * </ol>
 *
 * <p>Las dos líneas se le piden a {@link ServicioCaja#lineaRecibiDelArqueo} --la misma que usa el
 * sistema--, nunca se escriben acá; por eso esta clase vive en el paquete {@code servicios}, donde
 * ese método es visible (la regla es mover la prueba, no ampliar la visibilidad del método).</p>
 *
 * <ul>
 *   <li><b>No toca la base</b>: ni un {@code SELECT}. Todos los datos están escritos acá.</li>
 *   <li><b>Compila el {@code .jrxml} EN MEMORIA</b>, así que no depende del {@code .jasper}.</li>
 *   <li><b>El datasource usa el mismo DTO que el sistema</b> ({@link FilaMovimientoArqueo} dentro
 *       de un {@link JRBeanCollectionDataSource}, como {@code generarPdfArqueo}) y los montos van
 *       formateados con {@link FormateadorNumeros}: una muestra con otro datasource o otro formato
 *       inventa defectos que en pantalla no existen (lección de la tanda 1).</li>
 * </ul>
 *
 * <p>El nombre NO matchea {@code *Test}/{@code Test*}: {@code mvn test} nunca la corre. Se invoca
 * con {@code mvn -Dtest=GenerarMuestrasArqueoC22Temporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}: esta clase se borra antes de la
 * entrega final.</b></p>
 */
public class GenerarMuestrasArqueoC22Temporal {

    private static final File JRXML = new File("src/main/resources/reportes/arqueo_caja.jrxml");
    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/arqueo_c22_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_arqueo_c22"));
    /** 2.0 = el PNG sale al doble del tamaño del PDF, legible para revisar el diseño. */
    private static final float ZOOM_PNG = 2.0f;

    @Test
    public void generarLasDosMuestras() throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(JRXML.getPath());
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        generar(compilado, "arqueo_c22_sin_revisor", null);
        generar(compilado, "arqueo_c22_con_revisor", "Ramón Benítez");

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private void generar(JasperReport compilado, String nombre, String nombreGerenteRegistrado)
            throws Exception {
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros(nombreGerenteRegistrado),
                new JRBeanCollectionDataSource(movimientosDeEjemplo()));

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

        System.out.println(nombre + ": " + impreso.getPages().size() + " página(s) -- "
                + ServicioCaja.lineaRecibiDelArqueo(nombreGerenteRegistrado));
    }

    /** Dos movimientos, armados con el MISMO DTO que {@code generarPdfArqueo}. */
    private List<FilaMovimientoArqueo> movimientosDeEjemplo() {
        List<FilaMovimientoArqueo> filas = new ArrayList<>();
        filas.add(new FilaMovimientoArqueo("19:24", "Entrega del gerente", "-", "-",
                FormateadorNumeros.comoGuaranies(new BigDecimal("1000000"))));
        filas.add(new FilaMovimientoArqueo("21:06", "Cobro", "CC-2026-00099 (MUESTRA)",
                "REC-2026-000099", FormateadorNumeros.comoGuaranies(new BigDecimal("289919"))));
        return filas;
    }

    /**
     * Mismos valores de empresa que {@code bd/03_parametros.sql}. El escenario "con revisor" lleva
     * además una diferencia con su motivo, porque es la única forma en que hoy queda un gerente
     * registrado contra la sesión (la revisión de la diferencia): así la muestra no inventa un
     * estado que el sistema no puede producir.
     */
    private Map<String, Object> parametros(String nombreGerenteRegistrado) {
        boolean conDiferencia = nombreGerenteRegistrado != null;
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        parametros.put("EMPRESA_RUC", "80123456-5");
        parametros.put("EMPRESA_DOMICILIO",
                "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        parametros.put("EMPRESA_TELEFONO", "(021) 941 890");
        parametros.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        parametros.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        parametros.put("ID_SESION", 999L);
        parametros.put("CAJERA_NOMBRE", "Sofía Martínez");
        // C22: lo que esta muestra viene a mirar -- la línea sale del dueño, no de acá.
        parametros.put("RECIBI_LINEA", ServicioCaja.lineaRecibiDelArqueo(nombreGerenteRegistrado));
        parametros.put("APERTURA", "03/11/2026 08:10");
        parametros.put("CIERRE", "03/11/2026 18:40");
        parametros.put("FONDO_INICIAL", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("CANT_ENTREGAS", 1);
        parametros.put("MONTO_ENTREGAS", FormateadorNumeros.comoGuaranies(new BigDecimal("1000000")));
        parametros.put("CANT_COBROS", 1);
        parametros.put("MONTO_COBROS", FormateadorNumeros.comoGuaranies(new BigDecimal("289919")));
        parametros.put("TOTAL_REDONDEOS", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("TIENE_REDONDEOS", Boolean.FALSE);
        parametros.put("CANT_ANTICIPOS", 0);
        parametros.put("MONTO_ANTICIPOS", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("CANT_CANCELACIONES", 0);
        parametros.put("MONTO_CANCELACIONES", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("CANT_VUELTOS", 0);
        parametros.put("MONTO_VUELTOS", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("CANT_DESEMBOLSOS", 0);
        parametros.put("MONTO_DESEMBOLSOS", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("CANT_COMPENSACIONES", 0);
        parametros.put("MONTO_COMPENSACIONES", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("SALDO_TEORICO", FormateadorNumeros.comoGuaranies(new BigDecimal("1289919")));
        parametros.put("EFECTIVO_CONTADO", FormateadorNumeros.comoGuaranies(
                conDiferencia ? new BigDecimal("1279919") : new BigDecimal("1289919")));
        // El "Sobra/Falta" sale del mismo formateador que usa el sistema.
        parametros.put("DIFERENCIA", ServicioCaja.formatearDiferenciaArqueo(
                conDiferencia ? new BigDecimal("-10000") : BigDecimal.ZERO));
        parametros.put("MOTIVO_DIFERENCIA", conDiferencia
                ? "Faltante detectado al contar el efectivo; se revisó con el gerente." : "-");
        parametros.put("TIENE_DIFERENCIA", conDiferencia);
        parametros.put("TOTAL_APLICADO", FormateadorNumeros.comoGuaranies(new BigDecimal("289919")));
        parametros.put("EFECTIVO_NETO", FormateadorNumeros.comoGuaranies(new BigDecimal("289919")));
        parametros.put("COMPOSICION_CAPITAL", FormateadorNumeros.comoGuaranies(new BigDecimal("252919")));
        parametros.put("COMPOSICION_INTERES", FormateadorNumeros.comoGuaranies(new BigDecimal("33637")));
        parametros.put("COMPOSICION_MORATORIO", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("COMPOSICION_PUNITORIO", FormateadorNumeros.comoGuaranies(BigDecimal.ZERO));
        parametros.put("COMPOSICION_IVA", FormateadorNumeros.comoGuaranies(new BigDecimal("3363")));
        parametros.put("RANGO_RECIBOS", "REC-2026-000099");
        parametros.put("CANT_RECIBOS", 1);
        parametros.put("TIENE_RECIBOS", Boolean.TRUE);
        parametros.put("TIENE_FACTURAS", Boolean.TRUE);
        parametros.put("NUMEROS_FACTURAS", "001-001-0000099");
        parametros.put("CANT_FACTURAS", 1);
        parametros.put("TOTAL_FACTURAS", FormateadorNumeros.comoGuaranies(new BigDecimal("37000")));
        parametros.put("TIENE_CONSTANCIAS", Boolean.FALSE);
        parametros.put("NUMEROS_CONSTANCIAS", "-");
        parametros.put("CANT_CONSTANCIAS", 0);
        parametros.put("TIENE_DESEMBOLSOS", Boolean.FALSE);
        parametros.put("NUMEROS_DESEMBOLSOS", "-");
        parametros.put("CANT_DESEMBOLSOS_COMPROBANTE", 0);
        parametros.put("FECHA_EMISION", "03/11/2026 18:40");
        return parametros;
    }
}
