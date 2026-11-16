package py.com.capitalcredito.sgcpc.herramientas;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.Test;

/**
 * B (reprueba Lote 1, pedido de Joel): cada reporte son DOS archivos que hay que mantener
 * sincronizados a mano -- el {@code .jrxml} (diseño editable) y el {@code .jasper} (compilado,
 * lo único que el sistema carga en tiempo de ejecución, ver {@code GeneradorInformesPdf}/
 * {@code ServicioFormalizacion.llenarYExportar}). Esta herramienta compila TODOS los
 * {@code .jrxml} de {@code src/main/resources/reportes/} a su {@code .jasper} correspondiente, y
 * guarda el hash SHA-256 de cada {@code .jrxml} usado en {@code herramientas/reportes_jasper.sha256}
 * -- {@link ReportesCompiladosTest} compara ese hash contra el {@code .jrxml} actual en cada
 * {@code mvn test}, para detectar (y hacer fallar la build) si alguien edita un {@code .jrxml} y
 * se olvida de recompilar, como pasó con {@code pagare.jrxml}/{@code plan_pagos.jrxml} en el
 * Lote 1 de la consolidación RF08-RF19.
 *
 * <p>El nombre de esta clase NO matchea el patrón {@code *Test}/{@code Test*} de Surefire (mismo
 * criterio que {@code PruebaD117PromesaPagoParcial}) -- {@code mvn test} nunca la corre sola;
 * se invoca a propósito con {@code herramientas/compilar_reportes.sh} (o
 * {@code mvn -Dtest=HerramientaCompilarReportes test}) después de editar cualquier {@code .jrxml},
 * documentado como paso obligatorio en {@code docs/REGLAS_DESARROLLO.md}.</p>
 *
 * <p>Hallazgo de Joel (13/10/2026, RF40/F3): recompilar TODOS los {@code .jrxml} aunque solo se
 * haya editado uno deja los otros 40 {@code .jasper} con bytes distintos a los commiteados (el
 * compilador de Jasper no es reproducible bit a bit entre corridas, aunque el diseño no cambie) --
 * ruido que después hay que distinguir de un cambio real antes de poder comitear, y un riesgo
 * innecesario para reportes de RF ya probados (`contrato`, `pagare`, etc.). Por eso ahora admite
 * compilar UN SOLO reporte: {@code -Dreporte=<nombre>} (con o sin ".jrxml"), que no toca ningún
 * otro {@code .jasper} ni ninguna otra línea del manifiesto -- ver
 * {@code herramientas/compilar_reportes.sh}.</p>
 */
public class HerramientaCompilarReportes {

    static final File CARPETA_REPORTES = new File("src/main/resources/reportes");
    static final File MANIFIESTO = new File("herramientas/reportes_jasper.sha256");

    @Test
    public void compilarTodosLosReportes() throws Exception {
        String soloReporte = System.getProperty("reporte");
        if (soloReporte != null && !soloReporte.trim().isEmpty()) {
            compilarUnReporte(soloReporte.trim());
            return;
        }
        File[] jrxmls = listarJrxmls();
        Properties hashes = new Properties();
        for (File jrxml : jrxmls) {
            String base = jrxml.getName().substring(0, jrxml.getName().length() - ".jrxml".length());
            File jasper = new File(CARPETA_REPORTES, base + ".jasper");
            JasperCompileManager.compileReportToFile(jrxml.getPath(), jasper.getPath());
            hashes.setProperty(jrxml.getName(), sha256(jrxml));
            System.out.println("Compilado: " + jrxml.getName() + " -> " + jasper.getName());
        }
        MANIFIESTO.getParentFile().mkdirs();
        try (OutputStream salida = new FileOutputStream(MANIFIESTO)) {
            hashes.store(salida, "Hash SHA-256 de cada .jrxml al momento de compilar su .jasper -- "
                    + "generado por HerramientaCompilarReportes, NO EDITAR A MANO. "
                    + "Ver docs/REGLAS_DESARROLLO.md.");
        }
        System.out.println(jrxmls.length + " reportes compilados; manifiesto actualizado en "
                + MANIFIESTO.getPath());
    }

    /** Pedido de Joel (13/10/2026, RF40/F3): compila SOLO el reporte nombrado -- ni toca el
     * .jasper de los demás, ni reescribe sus líneas del manifiesto (se carga el manifiesto
     * existente, se reemplaza una sola propiedad, se guarda). "nombre" acepta con o sin
     * ".jrxml". */
    static void compilarUnReporte(String nombre) throws Exception {
        String nombreJrxml = nombre.endsWith(".jrxml") ? nombre : nombre + ".jrxml";
        File jrxml = new File(CARPETA_REPORTES, nombreJrxml);
        if (!jrxml.isFile()) {
            throw new IllegalArgumentException("No se encontró " + jrxml.getPath() + ".");
        }
        String base = nombreJrxml.substring(0, nombreJrxml.length() - ".jrxml".length());
        File jasper = new File(CARPETA_REPORTES, base + ".jasper");
        JasperCompileManager.compileReportToFile(jrxml.getPath(), jasper.getPath());

        Properties hashes = new Properties();
        if (MANIFIESTO.isFile()) {
            try (InputStream entrada = new FileInputStream(MANIFIESTO)) {
                hashes.load(entrada);
            }
        }
        hashes.setProperty(nombreJrxml, sha256(jrxml));
        MANIFIESTO.getParentFile().mkdirs();
        try (OutputStream salida = new FileOutputStream(MANIFIESTO)) {
            hashes.store(salida, "Hash SHA-256 de cada .jrxml al momento de compilar su .jasper -- "
                    + "generado por HerramientaCompilarReportes, NO EDITAR A MANO. "
                    + "Ver docs/REGLAS_DESARROLLO.md.");
        }
        System.out.println("Compilado: " + nombreJrxml + " -> " + jasper.getName()
                + " (solo este reporte; el manifiesto actualizó solo su propia línea)");
    }

    static File[] listarJrxmls() {
        File[] jrxmls = CARPETA_REPORTES.listFiles((dir, nombre) -> nombre.endsWith(".jrxml"));
        if (jrxmls == null) {
            throw new IllegalStateException("No se encontró " + CARPETA_REPORTES.getPath() + ".");
        }
        Arrays.sort(jrxmls);
        return jrxmls;
    }

    static String sha256(File archivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(archivo.toPath()));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
