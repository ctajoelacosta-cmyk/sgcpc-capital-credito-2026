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
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;

/**
 * HERRAMIENTA TEMPORAL (07/11/2026, B120) -- muestras de {@code plan_pagos} y {@code pagare} con el
 * membrete de continuación, la extensión de la opción (b) de B12 a los otros dos reportes.
 *
 * <ul>
 *   <li><b>El plan se genera con el MOTOR REAL</b> ({@code generarPlanPagos}) y con un plazo largo
 *       a propósito, para que el documento pase de una página y se vea la franja repetida.</li>
 *   <li><b>Compila los `.jrxml` del repositorio</b> en memoria: la muestra es lo que va a emitir el
 *       sistema. No toca la base ni ningún documento guardado.</li>
 *   <li><b>No corre con {@code mvn test}</b>:
 *       {@code mvn -Dtest=GenerarMuestrasB120Temporal test}</li>
 * </ul>
 */
public class GenerarMuestrasB120Temporal {

    private static final File CARPETA_PDF = new File(System.getProperty("salidaPdf",
            "../pruebas/b120_muestra"));
    private static final File CARPETA_PNG = new File(System.getProperty("salidaPng", "target/b120_png"));
    private static final float ZOOM_PNG = 2.0f;
    /** Plazo largo a proposito: con 12 cuotas el plan entra en una pagina y no se ve la franja. */
    private static final int PLAZO_LARGO = 36;
    private static final LocalDate ELEGIDA = LocalDate.of(2026, 11, 28);

    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();

    @Test
    public void generarMuestras() throws Exception {
        CARPETA_PDF.mkdirs();
        CARPETA_PNG.mkdirs();

        List<Cuota> plan = servicioMotorFinanciero.generarPlanPagos(
                new java.math.BigDecimal("5000000"), PLAZO_LARGO, new java.math.BigDecimal("0.27"),
                ELEGIDA, java.util.Collections.<LocalDate>emptySet());
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setPrimeraFechaVencimiento(ELEGIDA);
        LocalDate efectiva = servicioFormalizacion.primeraFechaVencimientoEfectiva(plan, prestamo);

        GenerarMuestrasC3C12Temporal fuente = new GenerarMuestrasC3C12Temporal();
        Method constructor = GenerarMuestrasC3C12Temporal.class.getDeclaredMethod(
                "parametrosComunes", LocalDate.class, List.class);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> parametros = (Map<String, Object>) constructor.invoke(fuente, efectiva, plan);
        // Sin la marca "(MUESTRA)": es el campo cuyo ancho se esta revisando (misma leccion de B12).
        parametros.put("IDENTIFICADOR_OPERACION", "CC-2026-00099");
        parametros.put("PLAZO_MESES", PLAZO_LARGO);
        // FECHA_FORMALIZACION: la base viene de la muestra del CONTRATO, que solo setea la
        // variante _LETRAS, asi que la primera corrida mostro "Fecha de formalizacion: null".
        // El servicio real la pone en ServicioFormalizacion:1527. Tercera vez que heredar una
        // base de parametros hereda tambien sus huecos: por eso abajo va el cruce automatico.
        parametros.put("FECHA_FORMALIZACION", "29/10/2026");
        // El registro del BCP de la base heredada era inventado ("Registro BCP N.o 1234"), y
        // junto al literal "Registro BCP: " del reporte se leia duplicado. El valor real sale de
        // parametros_sistema (empresa.registro_bcp). Mismo largo, asi que no cambia la medicion
        // del ancho; se corrige para que la muestra no haga dudar de algo que en el papel real
        // no pasa.
        parametros.put("EMPRESA_REGISTRO_BCP", "Res. SB N.º 0147/2026");
        // Los totales del plan: la base viene de la muestra del CONTRATO, que no los setea, asi
        // que la primera corrida mostro "null" en la fila Total. Es la muestra, no el reporte: el
        // servicio real los pone en ServicioFormalizacion (TOTAL_CAPITAL/TOTAL_INTERES/TOTAL_IVA).
        // Se suman del plan que acaba de devolver el motor, no se inventan.
        java.math.BigDecimal totalCapital = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalInteres = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalIva = java.math.BigDecimal.ZERO;
        for (Cuota cuota : plan) {
            totalCapital = totalCapital.add(cuota.getCapitalProgramado());
            totalInteres = totalInteres.add(cuota.getInteresProgramado());
            totalIva = totalIva.add(cuota.getTributosProgramados());
        }
        parametros.put("TOTAL_CAPITAL",
                py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(totalCapital));
        parametros.put("TOTAL_INTERES",
                py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(totalInteres));
        parametros.put("TOTAL_IVA",
                py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(totalIva));
        parametros.put("TOTAL_A_PAGAR",
                py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                        totalCapital.add(totalInteres).add(totalIva)));

        System.out.println("=== B120: membrete de continuacion en plan_pagos y pagare ===");
        System.out.println("  plan generado con el motor real: " + plan.size() + " cuotas");

        medir("b120_plan_pagos", "src/main/resources/reportes/plan_pagos.jrxml",
                parametros, new JRBeanCollectionDataSource(filasDelPlan(plan)));
        medir("b120_pagare", "src/main/resources/reportes/pagare.jrxml",
                parametros, new JREmptyDataSource(1));
        // El MISMO plan con el plan_pagos.jrxml de HEAD (antes de B120), para ver si la direccion
        // del membrete ya se partia en dos lineas o lo causo este cambio.
        medir("b120_plan_pagos_ANTES_de_B120", "../pruebas/b120_muestra/plan_pagos_HEAD.jrxml",
                parametros, new JRBeanCollectionDataSource(filasDelPlan(plan)));

        System.out.println("PDF  -> " + CARPETA_PDF.getCanonicalPath());
        System.out.println("PNG  -> " + CARPETA_PNG.getCanonicalPath());
    }

    /**
     * El datasource del plan, armado con el MISMO DTO y los mismos valores que el servicio
     * (ServicioFormalizacion, el bucle que arma CuotaReporte antes de llenar plan_pagos.jasper).
     * El primer intento le paso la lista de Cuota directamente y JasperReports fallo con "Error
     * retrieving field value from bean: capital": el reporte espera CuotaReporte, con los montos
     * YA formateados. Es la regla de la skill: buscar el "new Fila..." real y copiar la forma.
     */
    private List<py.com.capitalcredito.sgcpc.dto.CuotaReporte> filasDelPlan(List<Cuota> plan) {
        List<py.com.capitalcredito.sgcpc.dto.CuotaReporte> filas = new ArrayList<>();
        for (Cuota cuota : plan) {
            filas.add(new py.com.capitalcredito.sgcpc.dto.CuotaReporte(
                    cuota.getNumeroCuota(),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoDdMmAaaa(
                            cuota.getFechaVencimiento()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                            cuota.getCapitalProgramado()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                            cuota.getInteresProgramado()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                            cuota.getTributosProgramados()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                            cuota.getTotalProgramado()),
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoGuaranies(
                            cuota.getSaldoCapitalPosterior())));
        }
        return filas;
    }

    private void medir(String nombre, String rutaJrxml, Map<String, Object> parametros, JRDataSource datos)
            throws Exception {
        // Cruce automatico: todos los $P{...} que el .jrxml imprime tienen que estar en el mapa.
        // Nace de haber pisado tres veces el mismo pozo (Son: null, los totales, la fecha de
        // formalizacion): heredar una base de parametros de otra muestra hereda sus huecos, y el
        // null recien se ve en el PDF. Se avisa, no se falla: hay parametros opcionales.
        String fuenteJrxml = new String(Files.readAllBytes(new File(rutaJrxml).toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher usados = java.util.regex.Pattern
                .compile("[$]P[{]([A-Z_0-9]+)[}]").matcher(fuenteJrxml);
        java.util.Set<String> faltantes = new java.util.TreeSet<>();
        while (usados.find()) {
            if (!parametros.containsKey(usados.group(1))) {
                faltantes.add(usados.group(1));
            }
        }
        if (!faltantes.isEmpty()) {
            System.out.println("  OJO, " + nombre + " usa parametros que la muestra NO setea"
                    + " (saldrian en null): " + faltantes);
        }

        JasperReport compilado = JasperCompileManager.compileReport(rutaJrxml);
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

        // Donde arranca el contenido de la pagina 1: el hueco de B12 es lo que haya antes.
        int primerY = Integer.MAX_VALUE;
        List<int[]> tramos = new ArrayList<>();
        for (JRPrintElement elemento : impreso.getPages().get(0).getElements()) {
            if (elemento.getHeight() > 0 && elemento.getHeight() < 700) {
                tramos.add(new int[] {elemento.getY(), elemento.getY() + elemento.getHeight()});
                primerY = Math.min(primerY, elemento.getY());
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
        System.out.println("  " + nombre + ": " + impreso.getPages().size() + " paginas"
                + " | el contenido de la pagina 1 arranca en y=" + primerY
                + " | hueco mayor de la pagina 1: " + huecoMayor + " pt (entre y=" + huecoDesde
                + " y y=" + (huecoDesde + huecoMayor) + ")");
    }
}
