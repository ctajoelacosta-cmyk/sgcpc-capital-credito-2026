package py.com.capitalcredito.sgcpc.herramientas;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.Test;

/**
 * B (reprueba Lote 1, pedido de Joel): control permanente para que un {@code .jrxml} editado sin
 * recompilar su {@code .jasper} (como pasó con {@code pagare.jrxml}/{@code plan_pagos.jrxml} en
 * el Lote 1) nunca más pase desapercibido -- compara el hash SHA-256 guardado de cada
 * {@code .jrxml} (en {@code herramientas/reportes_jasper.sha256}, escrito por
 * {@link HerramientaCompilarReportes} la última vez que se compiló) contra el {@code .jrxml}
 * actual. Si no coinciden, el {@code .jasper} que el sistema carga en tiempo de ejecución NO
 * tiene el cambio -- la prueba falla y nombra cuáles.
 *
 * <p>Esta clase SÍ matchea el patrón {@code *Test} de Surefire: corre con cualquier
 * {@code mvn test}, sin que nadie tenga que acordarse de pedirla aparte.</p>
 */
public class ReportesCompiladosTest {

    @Test
    public void todosLosJasperCorrespondenASuJrxmlActual() throws Exception {
        if (!HerramientaCompilarReportes.MANIFIESTO.exists()) {
            fail("Falta " + HerramientaCompilarReportes.MANIFIESTO.getPath() + " -- corra "
                    + "herramientas/compilar_reportes.sh antes de \"mvn test\".");
        }
        Properties hashes = new Properties();
        try (InputStream entrada = new FileInputStream(HerramientaCompilarReportes.MANIFIESTO)) {
            hashes.load(entrada);
        }
        List<String> desactualizados = new ArrayList<>();
        for (File jrxml : HerramientaCompilarReportes.listarJrxmls()) {
            String hashActual = HerramientaCompilarReportes.sha256(jrxml);
            String hashGuardado = hashes.getProperty(jrxml.getName());
            if (!hashActual.equals(hashGuardado)) {
                desactualizados.add(jrxml.getName());
            }
        }
        if (!desactualizados.isEmpty()) {
            fail("El .jasper de estos reportes no corresponde a su .jrxml actual -- corra "
                    + "herramientas/compilar_reportes.sh y vuelva a confirmar el cambio: "
                    + desactualizados);
        }
    }
}
