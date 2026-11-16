package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
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
import py.com.capitalcredito.sgcpc.dto.FilaReciboCuota;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.modelos.Cuota;

/**
 * TEMPORAL -- C39, 29/10/2026 (hallazgo de Joel del 27/10, decidido el 29/10).
 *
 * <p>Genera la vista previa del <b>recibo de un adelanto a capital</b> con el caso que hace
 * visible al hallazgo: la próxima cuota del plan nuevo tiene el ancla en <b>sábado 28/11/2026</b>
 * y se cobra el <b>lunes 30/11/2026</b>. La línea *"Próxima cuota (IVA incluido): …"* tiene que
 * mostrar el <b>30/11</b>. Datos de ejemplo marcados "(MUESTRA)".</p>
 *
 * <ul>
 *   <li><b>No toca la base</b> ni ningún {@code .jasper}: compila {@code recibo.jrxml} en memoria.</li>
 *   <li><b>El `.jrxml` no cambió con C39</b>: la línea ya decía "Próxima cuota (IVA incluido): {…}
 *       por {…}". Lo que cambió es el valor que le pasa Java, así que no hay nada que recompilar;
 *       esta muestra existe solo para ver el documento impreso con el dato nuevo.</li>
 *   <li><b>La fecha no se escribe a mano</b>: sale de
 *       {@link ServicioCobranza#fechaProximaCuotaNuevoPlan}, el mismo método que usa el sistema.</li>
 * </ul>
 *
 * <p>No matchea {@code *Test}/{@code Test*}: {@code mvn test} nunca la corre. Se invoca con
 * {@code mvn -Dtest=GenerarMuestraC39Temporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}: esta clase se borra antes de la
 * entrega final.</b></p>
 */
public class GenerarMuestraC39Temporal {

    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/c39_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_c39"));
    private static final float ZOOM_PNG = 2.0f;

    @Test
    public void generarReciboDeAdelanto() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        // El caso de CC-2026-00005: ancla sábado 28/11/2026, cobro lunes 30/11/2026.
        Cuota primeraCuotaNuevoPlan = new Cuota();
        primeraCuotaNuevoPlan.setNumeroCuota(1);
        primeraCuotaNuevoPlan.setFechaVencimientoTeorica(LocalDate.of(2026, 11, 28));
        primeraCuotaNuevoPlan.setFechaVencimiento(LocalDate.of(2026, 11, 30));
        String fechaProxima = ServicioCobranza.fechaProximaCuotaNuevoPlan(primeraCuotaNuevoPlan);
        System.out.println("Ancla (teorica): 28/11/2026 (SATURDAY)");
        System.out.println("Proxima cuota que muestra el recibo: " + fechaProxima);

        JasperReport compilado = JasperCompileManager.compileReport("src/main/resources/reportes/recibo.jrxml");
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros(fechaProxima),
                new JRBeanCollectionDataSource(Arrays.asList(
                        new FilaReciboCuota("1/12", "223.550 Gs.", "0 Gs.", "0 Gs.", "0 Gs.", "0 Gs.",
                                "223.550 Gs."))));

        File pdf = new File(CARPETA_PDF, "recibo_adelanto_muestra.pdf");
        Files.write(pdf.toPath(), JasperExportManager.exportReportToPdf(impreso));

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
            ImageIO.write(imagen, "png", new File(CARPETA_PNG, "recibo_adelanto_muestra_p" + (pagina + 1) + ".png"));
        }

        System.out.println("recibo_adelanto_muestra: " + impreso.getPages().size() + " pagina(s)");
        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private Map<String, Object> parametros(String fechaProximaCuota) {
        Map<String, Object> p = new HashMap<>();
        p.put("LOGO", GeneradorInformesPdf.logoComoBytes());
        p.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        p.put("EMPRESA_RUC", "80123456-5");
        p.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción");
        p.put("EMPRESA_TELEFONO", "(021) 941 890");
        p.put("EMPRESA_WHATSAPP", "(0972) 552 233");
        p.put("EMPRESA_CORREO", "atencion@capitalcredito.com.py");
        p.put("NUMERO_RECIBO", "REC-2026-000099 (MUESTRA)");
        p.put("FECHA_HORA", "29/10/2026 20:55");
        p.put("IDENTIFICADOR_OPERACION", "CC-2026-00099 (MUESTRA)");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        p.put("CLIENTE_CEDULA", "4.512.789");
        p.put("TIENE_FACTURA", Boolean.TRUE);
        p.put("FACTURA_NUMERO", "001-001-0000099 (MUESTRA)");
        p.put("EFECTIVO_RECIBIDO", "230.000 Gs.");
        p.put("TIENE_VUELTO", Boolean.TRUE);
        p.put("VUELTO", "6.450 Gs.");
        p.put("APLICADO", "223.550 Gs.");
        p.put("APLICADO_LETRAS", "DOSCIENTOS VEINTITRÉS MIL QUINIENTOS CINCUENTA GUARANÍES");
        p.put("TIENE_REDONDEO", Boolean.FALSE);
        p.put("REDONDEO", "0 Gs.");
        p.put("ES_CANCELACION", Boolean.FALSE);
        // La rama que viene a mirar esta muestra.
        p.put("ES_ANTICIPO", Boolean.TRUE);
        p.put("ANTICIPO_INTERES", "0 Gs.");
        p.put("ANTICIPO_IVA", "0 Gs.");
        p.put("ANTICIPO_CAPITAL", "223.550 Gs.");
        p.put("ANTICIPO_CUOTAS_RESTANTES", 11);
        p.put("ANTICIPO_CUOTA_NUEVA", "263.155 Gs. + IVA s/ interés");
        p.put("ANTICIPO_PROXIMA_CUOTA_FECHA", fechaProximaCuota);
        p.put("ANTICIPO_PROXIMA_CUOTA_MONTO", "289.470 Gs.");
        p.put("ANTICIPO_ULTIMO_VENCIMIENTO", "28/09/2027");
        p.put("TIENE_PROMESA", Boolean.FALSE);
        p.put("PROMESA_FECHA", "-");
        p.put("PROMESA_MONTO", "-");
        p.put("TOTAL_CAPITAL", "223.550 Gs.");
        p.put("TOTAL_INTERES", "0 Gs.");
        p.put("TOTAL_MORATORIO", "0 Gs.");
        p.put("TOTAL_PUNITORIO", "0 Gs.");
        p.put("TOTAL_IVA_CUOTAS", "0 Gs.");
        p.put("TOTAL_FILAS", 1);
        p.put("SALDO_CAPITAL_DESPUES", "2.776.450 Gs.");
        p.put("CUOTAS_PENDIENTES", 0);
        p.put("TIENE_PROXIMO_VENCIMIENTO", Boolean.FALSE);
        p.put("PROXIMA_CUOTA", "-");
        p.put("PROXIMO_VENCIMIENTO", "-");
        p.put("PROXIMA_CUOTA_VENCIDA", Boolean.FALSE);
        p.put("PROXIMA_CUOTA_RESTA", "-");
        p.put("USUARIO_EMISOR", "Sofía Benítez (MUESTRA)");
        p.put("FECHA_EMISION", "29/10/2026 20:55");
        p.put("REVERTIDO", Boolean.FALSE);
        p.put("REVERSION_FECHA", "-");
        p.put("REVERSION_AUTORIZADO_POR", "-");
        p.put("REVERSION_MOTIVO", "-");
        p.put("MARCA_AGUA", "");
        return p;
    }
}
