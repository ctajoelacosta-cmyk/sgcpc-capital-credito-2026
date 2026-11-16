package py.com.capitalcredito.sgcpc.reportes;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.Test;

/**
 * Compila un .jrxml puntual a .jasper, con la misma versión de JasperReports (6.20.6) que usa la
 * aplicación en tiempo de ejecución -- mismo patrón que CargadorDatosPrueba/PruebaConexionBD (no
 * termina en "Test", no corre con mvn test normal, solo con mvn -Dtest=CompilarReportesJasper
 * test). No toca ninguna base de datos. A propósito compila UN archivo a la vez (variable
 * NOMBRE_REPORTE, abajo) -- no todos los .jrxml de la carpeta, para no regenerar de refilón
 * el .jasper de reportes que no se tocaron (ver D-75, AVANCE.md: no tocar lo que no se está
 * cambiando en esa acción).
 */
public class CompilarReportesJasper {

    private static final String NOMBRE_REPORTE = "constancia_devolucion";

    @Test
    public void compilar() throws Exception {
        File carpeta = new File("src/main/resources/reportes");
        File jrxml = new File(carpeta, NOMBRE_REPORTE + ".jrxml");
        if (!jrxml.exists()) {
            throw new IllegalStateException("No se encontró " + jrxml.getAbsolutePath());
        }
        File jasper = new File(carpeta, NOMBRE_REPORTE + ".jasper");
        JasperCompileManager.compileReportToFile(jrxml.getAbsolutePath(), jasper.getAbsolutePath());
        System.out.println("Compilado: " + jasper.getName());
    }

    /**
     * Prueba de humo de la constancia de devolución de pagaré (D-139), con datos ficticios
     * (ninguno toca la base): confirma que llena/exporta sin lanzar nada con los mismos
     * parámetros reales que arma ServicioFormalizacion, agregado el "C.I. N°" debajo de la firma
     * del cliente (antes solo lo tenía la firma del representante). El PDF de salida queda en
     * target/ (no se comitea).
     */
    @Test
    public void llenarConDatosFicticios() throws Exception {
        File jasper = new File("src/main/resources/reportes", NOMBRE_REPORTE + ".jasper");
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("EMPRESA_RAZON_SOCIAL", "Capital Crédito Paraguay S.R.L.");
        parametros.put("EMPRESA_RUC", "80123456-5");
        parametros.put("EMPRESA_DOMICILIO", "Tte. Rojas Silva 1245, Asunción");
        parametros.put("EMPRESA_TELEFONO", "(021) 941 890");
        parametros.put("EMPRESA_WHATSAPP", "0981 123 456");
        parametros.put("EMPRESA_CORREO", "info@capitalcredito.com.py");
        parametros.put("REPRESENTANTE_NOMBRE", "Carlos Representante");
        parametros.put("REPRESENTANTE_CEDULA", "2.345.678");
        parametros.put("NUMERO_CONSTANCIA_DEVOLUCION", "DEV-2026-000001");
        parametros.put("FECHA_DEVOLUCION", "01/10/2026");
        parametros.put("FECHA_DEVOLUCION_LARGA", "1 de octubre de 2026");
        parametros.put("CLIENTE_NOMBRE", "ANDREA BENEGAS");
        parametros.put("CLIENTE_CEDULA", "1.234.567");
        parametros.put("IDENTIFICADOR_OPERACION", "CC-2026-00009");
        parametros.put("NUMERO_PAGARE", "PAG-2026-000009");
        parametros.put("MONTO_DESEMBOLSADO", "6.000.000 Gs.");
        parametros.put("FECHA_DESEMBOLSO", "01/10/2026");
        parametros.put("FECHA_CANCELACION", "01/10/2026");
        parametros.put("NUMERO_CONSTANCIA_CANCELACION", "CAN-2026-000001");
        parametros.put("USUARIO_EMISOR", "María Cajera");
        parametros.put("FECHA_EMISION", "01/10/2026 15:00");

        JasperPrint original = JasperFillManager.fillReport(jasper.getAbsolutePath(), parametros, new JREmptyDataSource(1));
        File salida = new File("target", NOMBRE_REPORTE + "_prueba.pdf");
        JasperExportManager.exportReportToPdfFile(original, salida.getAbsolutePath());
        System.out.println("PDF de prueba: " + salida.getAbsolutePath() + " -- " + original.getPages().size() + " página(s)");
    }
}
