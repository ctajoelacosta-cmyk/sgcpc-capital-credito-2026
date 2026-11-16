package py.com.capitalcredito.sgcpc.infraestructura;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import py.com.capitalcredito.sgcpc.servicios.ServicioParametros;

/**
 * Bloque 7/E10 (D-135, pedido de Joel): mecanismo COMÚN para los "informes" PDF con filtros
 * (punto 4 del pedido -- "para aplicarlo después a los demás módulos sin repetir código"). Cada
 * módulo sigue con su propio .jrxml (las columnas del detalle son distintas en cada uno, eso no
 * se puede compartir), pero la cabecera de empresa, el usuario/fecha de emisión y el llenado +
 * exportación a PDF son la MISMA convención de parámetros y el MISMO código para todos -- igual
 * que ya usan (cada uno por separado, sin compartirlo) ServicioCaja/ServicioCobranza/
 * ServicioDesembolso/ServicioFormalizacion para sus propios comprobantes; esta clase no los toca,
 * solo evita que el próximo módulo (Solicitudes, Préstamos, etc.) tenga que volver a escribir lo
 * mismo.
 */
public final class GeneradorInformesPdf {

    private GeneradorInformesPdf() {
    }

    // L1 Paso 3a (pedido de Joel): el escudo (solo el escudo, sin el nombre ni el eslogan -- esos
    // ya van en $P{EMPRESA_RAZON_SOCIAL} como texto) para el encabezado de los PDF. Se lee del
    // classpath UNA sola vez, cacheado en bytes (nunca un InputStream: no se puede reusar en
    // llamados siguientes); si no está, LOGO_BYTES queda en null y cada PDF se sigue generando
    // igual, sin logo y sin que nada lo note -- ver el comentario de llenarYExportar.
    private static final byte[] LOGO_BYTES = cargarLogoBytes();

    private static byte[] cargarLogoBytes() {
        try (InputStream entrada = GeneradorInformesPdf.class.getResourceAsStream("/reportes/logo_icono_transparente.png")) {
            if (entrada == null) {
                return null;
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
            return salida.toByteArray();
        } catch (IOException error) {
            return null;
        }
    }

    /** Cabecera común a cualquier informe: datos de la empresa (Parámetros) + quién/cuándo lo emitió.
     * Lote 2, L2-D (14/10/2026, pedido de Joel, autorizado): EMPRESA_TELEFONO/EMPRESA_WHATSAPP
     * agregados acá -- los informes muestran siempre el valor vigente HOY (D-94: no son
     * documentos firmados, no se guardan, no hace falta la fecha histórica). Los 3 llamadores que
     * necesitan un valor distinto (autorización del buró, requisitos, cotización) ya sobrescriben
     * estas 2 claves por su cuenta justo después de llamar a este método -- confirmado antes de
     * tocar, no les cambia nada. */
    public static Map<String, Object> parametrosBase(Connection conexion, ServicioParametros servicioParametros,
            String usuarioEmisor) throws SQLException {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", servicioParametros.obtenerValor(conexion, "empresa.razon_social"));
        parametros.put("EMPRESA_RUC", servicioParametros.obtenerValor(conexion, "empresa.ruc"));
        parametros.put("EMPRESA_DOMICILIO", servicioParametros.obtenerValor(conexion, "empresa.domicilio"));
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValor(conexion, "empresa.telefono"));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValor(conexion, "empresa.whatsapp"));
        parametros.put("USUARIO_EMISOR", usuarioEmisor);
        parametros.put("FECHA_EMISION", FormateadorFechas.comoDdMmAaaaHhMm(Reloj.ahora()));
        return parametros;
    }

    /**
     * L1 Paso 3a-ter (pedido de Joel): el escudo como byte[] -- no como InputStream -- porque un
     * .jrxml puede evaluar $P{LOGO} más de una vez en la misma pasada (p. ej. contrato.jrxml: una
     * vez en el title de la página 1 y otra vez en el pageHeader repetido de la página 2 en
     * adelante). Un InputStream ya leído en la primera evaluación llega vacío a la segunda (el
     * escudo desaparecía en la página 2); un byte[] no tiene posición de lectura, así que se
     * puede evaluar cualquier cantidad de veces sin agotarse. LOGO_BYTES nunca se modifica, así
     * que devolver la misma referencia a quien la pida es seguro. null si el archivo no estaba
     * (nunca rompe la generación).
     */
    public static byte[] logoComoBytes() {
        return LOGO_BYTES;
    }

    /** Mismo llenado + exportación a PDF que ya usan (duplicado, uno por Servicio) los 4 módulos de comprobantes. */
    public static byte[] llenarYExportar(String nombreArchivoJasper, Map<String, Object> parametros,
            JRDataSource datasource) {
        try (InputStream entrada = GeneradorInformesPdf.class.getResourceAsStream("/reportes/" + nombreArchivoJasper)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró \"" + nombreArchivoJasper + "\" en el classpath.");
            }
            // L1 Paso 3a (pedido de Joel): LOGO para los .jrxml que ya lo declaran como parámetro
            // -- los que todavía no lo declaran simplemente ignoran esta clave (JasperReports no
            // se queja de parámetros de más en el mapa).
            parametros.put("LOGO", logoComoBytes());
            JasperPrint impreso = JasperFillManager.fillReport(entrada, parametros, datasource);
            return JasperExportManager.exportReportToPdf(impreso);
        } catch (JRException | IOException error) {
            throw new IllegalStateException("No se pudo generar el PDF (" + nombreArchivoJasper + ").", error);
        }
    }

    /**
     * RF25 (hallazgo de Joel, 12/10/2026, punto 7): sello diagonal suave sobre el cuerpo de un
     * comprobante anulado/revertido -- la franja horizontal (arriba, movida al pageHeader del
     * .jrxml) se puede recortar; este sello cubre el cuerpo entero y no. JasperReports solo rota
     * texto en pasos de 90° (`textElement rotation`), así que para un ángulo libre se genera la
     * imagen acá, con AWT, y se manda como parámetro `byte[]` (mismo patrón que LOGO) -- la fuente
     * lógica SANS_SERIF (no "DejaVu Sans", que JasperReports resuelve internamente para el PDF
     * pero que AWT no tiene por qué tener registrada en el servidor) para no depender de qué
     * fuentes están instaladas. Devuelve null si algo falla (nunca rompe la generación del PDF
     * por el sello, igual que logoComoBytes).
     */
    public static byte[] construirMarcaAguaDiagonal(String texto) {
        try {
            int ancho = 481;
            int alto = 762;
            java.awt.image.BufferedImage imagen = new java.awt.image.BufferedImage(ancho, alto,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = imagen.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 70));
            g.setColor(new java.awt.Color(139, 0, 0, 45));
            g.translate(ancho / 2.0, alto / 2.0);
            g.rotate(-Math.toRadians(30));
            java.awt.FontMetrics metricas = g.getFontMetrics();
            int anchoTexto = metricas.stringWidth(texto);
            g.drawString(texto, -anchoTexto / 2.0f, 0);
            g.dispose();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
