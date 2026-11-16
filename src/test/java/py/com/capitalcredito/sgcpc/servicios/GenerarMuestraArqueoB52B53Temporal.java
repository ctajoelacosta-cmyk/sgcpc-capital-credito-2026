package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * HERRAMIENTA TEMPORAL (07/11/2026, B52 + B53) -- vista previa del arqueo de una sesión real, para
 * verificar los dos cambios del tramo 3 de B86 en un solo PDF: que un monto cero ya no salga
 * <i>"−0 Gs."</i> (B52) y que el detalle de movimientos muestre <b>día y hora</b>, no solo la hora
 * (B53), sin que la fecha quede cortada en seco por el ancho de la columna.
 *
 * <ul>
 *   <li><b>NO congela el arqueo.</b> Es la trampa de este documento: {@code obtenerPdfArqueo} pasa
 *       por {@code obtenerOGenerarPdf}, que lo guarda en {@code sesiones_caja.id_archivo_cierre} la
 *       primera vez que alguien lo pide y nunca más lo regenera (D-94, igual que recibo, factura y
 *       constancias). Por eso acá se llama al <b>generador</b>, {@code generarPdfArqueo}, que solo
 *       arma el PDF y no escribe nada. Es privado, así que se lo alcanza por reflexión: la
 *       alternativa era ampliarle la visibilidad a código de producción para que lo vea una
 *       herramienta temporal, y eso el proyecto no lo hace.</li>
 *   <li><b>Solo lectura</b>: la conexión va con {@code autoCommit=false} y termina en
 *       {@code rollback()}; el generador solo hace {@code SELECT}.</li>
 *   <li><b>Usa el {@code .jasper} del repositorio</b>, ya recompilado, no una compilación en
 *       memoria: así la vista previa muestra exactamente lo que va a emitir el sistema.</li>
 *   <li><b>No corre con {@code mvn test}</b>: el nombre no matchea {@code *Test}/{@code Test*}.
 *       Se invoca a propósito:
 *       {@code mvn -Dtest=GenerarMuestraArqueoB52B53Temporal -DidSesion=60 test}</li>
 *   <li><b>Guarda dura de DOGMA 2</b>, leída de la conexión abierta, no de un archivo.</li>
 * </ul>
 */
public class GenerarMuestraArqueoB52B53Temporal {

    private static final String CARPETA = "../pruebas/arqueo_caja_muestra";

    @Test
    public void generarVistaPreviaDelArqueo() throws Exception {
        long idSesion = Long.parseLong(System.getProperty("idSesion", "60"));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            String url = conexion.getMetaData().getURL();
            if (url == null || !url.endsWith("/sgcpc_db_pruebas")
                    || !"sgcpc_db_pruebas".equals(conexion.getCatalog())) {
                fail("DOGMA 2: la conexión no apunta a sgcpc_db_pruebas (url=" + url + ")");
            }
            conexion.setAutoCommit(false);
            try {
                Method generador = ServicioCaja.class.getDeclaredMethod(
                        "generarPdfArqueo", Connection.class, long.class);
                generador.setAccessible(true);
                byte[] pdf = (byte[]) generador.invoke(new ServicioCaja(), conexion, idSesion);

                File carpeta = new File(CARPETA);
                if (!carpeta.exists() && !carpeta.mkdirs()) {
                    fail("No se pudo crear " + carpeta.getAbsolutePath());
                }
                File salida = new File(carpeta, "arqueo_sesion_" + idSesion + "_B52_B53.pdf");
                try (FileOutputStream flujo = new FileOutputStream(salida)) {
                    flujo.write(pdf);
                }
                System.out.println("=== vista previa del arqueo de la sesion " + idSesion + " ===");
                System.out.println("  " + salida.getCanonicalPath() + " (" + pdf.length + " bytes)");

                // Para MIRAR el resultado sin depender de ninguna herramienta externa (todo
                // offline): se extrae el texto con OpenPDF, que ya viene con JasperReports. No
                // reemplaza a que Joel abra el PDF -- sirve para que esta herramienta no le pase
                // un documento con un texto cortado, que es justo lo que arriesga B53 (JasperReports
                // no recorta: corta en seco).
                com.lowagie.text.pdf.PdfReader lector = new com.lowagie.text.pdf.PdfReader(pdf);
                com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(lector);
                StringBuilder todo = new StringBuilder();
                for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                    todo.append(extractor.getTextFromPage(pagina)).append('\n');
                }
                lector.close();
                System.out.println("  paginas: " + lector.getNumberOfPages());
                System.out.println("--- DETALLE DE MOVIMIENTOS, tal como sale impreso ---");
                boolean enDetalle = false;
                for (String linea : todo.toString().split("\n")) {
                    if (linea.contains("DETALLE DE MOVIMIENTOS")) {
                        enDetalle = true;
                        continue;
                    }
                    if (enDetalle && !linea.trim().isEmpty()) {
                        System.out.println("  |" + linea);
                    }
                }
                System.out.println("--- comprobaciones de B52 y B53 ---");
                // B53 se puede comprobar sobre el texto extraido: o esta la fecha completa o no.
                System.out.println("  B53, la fecha completa dd/mm/aaaa hh:mm aparece impresa: "
                        + todo.toString().matches("(?s).*\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}.*"));
                // B52 NO se puede comprobar buscando "-0 Gs." en el texto extraido: OpenPDF
                // concatena las columnas sin separador, y las columnas "operacion" y "comprobante"
                // valen "-" cuando el movimiento no tiene referencia, asi que un "- " + "- " +
                // "0 Gs." se lee como "--0 Gs." y un contains("-0 Gs.") da un FALSO POSITIVO (pasó
                // en la primera corrida de esta herramienta). Se verifica sobre el valor que de
                // verdad va a la columna, pidiendolo al mismo metodo que usa el servicio.
                System.out.println("  B52, el monto que va a la columna, movimiento por movimiento:");
                try (java.sql.PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT tipo, signo, monto FROM movimientos_caja WHERE id_sesion_caja = ? "
                                + "ORDER BY id_movimiento_caja")) {
                    sentencia.setLong(1, idSesion);
                    try (java.sql.ResultSet filas = sentencia.executeQuery()) {
                        while (filas.next()) {
                            String formateado = ServicioCaja.montoConSigno(
                                    filas.getInt("signo"), filas.getBigDecimal("monto"));
                            System.out.println("    " + filas.getString("tipo")
                                    + " (signo " + filas.getInt("signo") + ") -> \"" + formateado + "\""
                                    + (formateado.startsWith("-0 ") ? "   <-- DEFECTO DE B52" : ""));
                        }
                    }
                }
            } finally {
                conexion.rollback();
            }
        }
    }
}
