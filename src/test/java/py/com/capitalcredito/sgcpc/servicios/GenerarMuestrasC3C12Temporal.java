package py.com.capitalcredito.sgcpc.servicios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
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
import py.com.capitalcredito.sgcpc.dto.ClausulaReporte;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;

/**
 * TEMPORAL -- C3 y C12, 29/10/2026 (hallazgos del recorrido, decisión de Joel del 29/10).
 *
 * <p>Genera las vistas previas del <b>contrato</b> y del <b>comprobante de desembolso</b> con el
 * caso que hace visible a los dos hallazgos: <b>primer vencimiento elegido el SÁBADO 28/11/2026</b>,
 * que el motor corre al <b>lunes 30/11/2026</b>. Datos de ejemplo marcados "(MUESTRA)".</p>
 *
 * <ul>
 *   <li><b>No toca la base</b>: ni un {@code SELECT}. Los valores de empresa son los de
 *       {@code bd/03_parametros.sql}.</li>
 *   <li><b>No toca ningún {@code .jasper}</b>: compila los dos {@code .jrxml} EN MEMORIA
 *       ({@code JasperCompileManager.compileReport}). Igual, C3/C12 no cambian ningún
 *       {@code .jrxml}: lo que cambió es el texto que arma Java, así que no hay nada que
 *       recompilar.</li>
 *   <li><b>El texto no se copia a mano</b>: las 10 cláusulas salen de
 *       {@link ServicioFormalizacion#construirClausulasContrato}, el mismo método que usa el
 *       sistema, y la fecha efectiva de
 *       {@link ServicioFormalizacion#primeraFechaVencimientoEfectiva} sobre un plan generado por
 *       el {@link ServicioMotorFinanciero} real.</li>
 * </ul>
 *
 * <p>El nombre no matchea {@code *Test}/{@code Test*} de Surefire: {@code mvn test} nunca la
 * corre. Se invoca con {@code mvn -Dtest=GenerarMuestrasC3C12Temporal test}.</p>
 *
 * <p><b>Va a la lista "Antes de entregar" de {@code AVANCE.md}: esta clase se borra antes de la
 * entrega final.</b></p>
 */
public class GenerarMuestrasC3C12Temporal {

    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/c3_c12_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng",
            "target/muestras_c3_c12"));
    private static final float ZOOM_PNG = 2.0f;

    /** El caso del hallazgo: el oficial elige un sábado. */
    private static final LocalDate ELEGIDA = LocalDate.of(2026, 11, 28);

    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();

    @Test
    public void generarContratoYComprobante() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        // El plan real, con el motor real: de acá sale la fecha EFECTIVA de la cuota 1.
        List<py.com.capitalcredito.sgcpc.modelos.Cuota> plan = servicioMotorFinanciero.generarPlanPagos(
                new java.math.BigDecimal("5000000"), 12, new java.math.BigDecimal("0.27"), ELEGIDA,
                java.util.Collections.<LocalDate>emptySet());
        py.com.capitalcredito.sgcpc.modelos.PrestamoCredito prestamo =
                new py.com.capitalcredito.sgcpc.modelos.PrestamoCredito();
        prestamo.setPrimeraFechaVencimiento(ELEGIDA);
        LocalDate efectiva = servicioFormalizacion.primeraFechaVencimientoEfectiva(plan, prestamo);

        System.out.println("Elegida: " + ELEGIDA + " (" + ELEGIDA.getDayOfWeek() + ")");
        System.out.println("Efectiva cuota 1: " + efectiva + " (" + efectiva.getDayOfWeek() + ")");
        System.out.println("Cuota 2: " + plan.get(1).getFechaVencimiento());
        System.out.println("Ultima: " + plan.get(plan.size() - 1).getFechaVencimiento());

        Map<String, Object> parametros = parametrosComunes(efectiva, plan);
        List<ClausulaReporte> clausulas = servicioFormalizacion.construirClausulasContrato(parametros);
        for (ClausulaReporte clausula : clausulas) {
            if (clausula.getEncabezado() != null && clausula.getEncabezado().startsWith("SEGUNDA")) {
                System.out.println("CLAUSULA SEGUNDA -> " + clausula.getTexto());
            }
        }

        generar("contrato", "contrato_muestra_sabado", parametros, new JRBeanCollectionDataSource(clausulas));
        generar("comprobante_desembolso", "comprobante_muestra_sabado", parametrosComprobante(efectiva, plan),
                new JREmptyDataSource(1));

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    private void generar(String reporte, String nombre, Map<String, Object> parametros, JRDataSource datos)
            throws Exception {
        JasperReport compilado = JasperCompileManager.compileReport(
                "src/main/resources/reportes/" + reporte + ".jrxml");
        JasperPrint impreso = JasperFillManager.fillReport(compilado, parametros, datos);

        File pdf = new File(CARPETA_PDF, nombre + ".pdf");
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
            ImageIO.write(imagen, "png", new File(CARPETA_PNG, nombre + "_p" + (pagina + 1) + ".png"));
        }
        System.out.println(nombre + ": " + impreso.getPages().size() + " pagina(s)");
    }

    private Map<String, Object> parametrosComunes(LocalDate efectiva,
            List<py.com.capitalcredito.sgcpc.modelos.Cuota> plan) {
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
        p.put("FECHA_FORMALIZACION_LETRAS", "veintinueve días del mes de octubre de dos mil veintiséis");
        p.put("CLIENTE_NOMBRE", "(MUESTRA) Mirtha Elizabeth González Figueredo");
        p.put("CLIENTE_CEDULA", "4.512.789");
        p.put("CLIENTE_DOMICILIO", "Avda. Mariscal López 2345 c/ Boquerón, Asunción");
        p.put("CLIENTE_TELEFONO", "(0981) 123 456");
        p.put("DESTINO_CREDITO", "capital de trabajo para su despensa");
        p.put("MONTO_APROBADO", "5.000.000 Gs.");
        p.put("MONTO_APROBADO_LETRAS", "CINCO MILLONES DE GUARANÍES");
        p.put("PLAZO_MESES", 12);
        // Lo que viene a mirar esta muestra (C3/C12): la efectiva y el día elegido.
        p.put("PRIMERA_FECHA_VENCIMIENTO", formatear(efectiva));
        p.put("DIA_VENCIMIENTO_ELEGIDO", ELEGIDA.getDayOfMonth());
        p.put("ULTIMA_FECHA_VENCIMIENTO", formatear(plan.get(plan.size() - 1).getFechaVencimiento()));
        p.put("TEA", "27,00 %");
        p.put("TNA", "24,25 %");
        p.put("CFT", "30,61 %");
        p.put("CUOTA_1", "482.500 Gs.");
        p.put("TOTAL_A_PAGAR", "5.790.000 Gs.");
        p.put("TOTAL_A_PAGAR_LETRAS", "CINCO MILLONES SETECIENTOS NOVENTA MIL GUARANÍES");
        p.put("PORCENTAJE_PUNITORIO", "30,00 %");
        p.put("CUOTAS_ALERTA_GARANTE", 3);
        p.put("USUARIO_EMISOR", "Lorenzo Ramírez (MUESTRA)");
        p.put("USUARIO_EMISOR_CEDULA", "3.991.204");
        p.put("FECHA_EMISION", "29/10/2026 15:20");
        return p;
    }

    private Map<String, Object> parametrosComprobante(LocalDate efectiva,
            List<py.com.capitalcredito.sgcpc.modelos.Cuota> plan) {
        Map<String, Object> p = parametrosComunes(efectiva, plan);
        p.put("NUMERO_COMPROBANTE", "DES-2026-000099 (MUESTRA)");
        p.put("FECHA_HORA", "29/10/2026 15:20");
        p.put("CAJA_SESION", "Sesión 10 — 29/10/2026");
        p.put("CAJERA_NOMBRE", "Marta Ovelar (MUESTRA)");
        p.put("NUMERO_CONTRATO", "CC-2026-00099");
        p.put("NUMERO_PAGARE", "CC-2026-00099");
        p.put("MONTO_DEDUCCIONES", "0 Gs. (sin comisiones ni gastos)");
        p.put("MONTO_NETO", "5.000.000 Gs.");
        p.put("MONTO_NETO_LETRAS", "CINCO MILLONES DE GUARANÍES");
        p.put("FORMA_ENTREGA", "Efectivo");
        p.put("CUOTA_ULTIMA", "482.500 Gs.");
        p.put("CUOTA_ES_DECRECIENTE", Boolean.FALSE);
        // C12: la misma fecha efectiva que el contrato y el plan.
        p.put("PRIMER_VENCIMIENTO", formatear(efectiva));
        p.put("MARCA_REIMPRESION", "");
        return p;
    }

    private String formatear(LocalDate fecha) {
        return py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(fecha);
    }
}
