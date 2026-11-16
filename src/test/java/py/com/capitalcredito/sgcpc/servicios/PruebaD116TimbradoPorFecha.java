package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.time.LocalDate;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dao.ParametroDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.ParametroSistema;

/**
 * D-116 (Bloque 7/E10, 7.4): confirma que ServicioCobranza.validarTimbradoVigente (y, por lo
 * tanto, emitirFactura) resuelve el timbrado VIGENTE EN LA FECHA del cobro, no el de hoy -- pedido
 * explícito de Joel, con estos 3 casos: fecha dentro del timbrado anterior -> usa el anterior;
 * fecha de hoy -> usa el actual; fecha fuera de los dos -> rechazo.
 *
 * No corre con "mvn test"/"mvn clean package" a propósito (el nombre no matchea los patrones de
 * Surefire, mismo patrón que PruebaConexionBD/PruebaPA16CapitalNoVencido): se ejecuta
 * explícitamente con "mvn -Dtest=PruebaD116TimbradoPorFecha test", con la base ya arriba. Nunca
 * hace commit -- todo lo que inserta (el timbrado anterior, de prueba) se revierte con rollback,
 * sin dejar rastro en la base real.
 */
public class PruebaD116TimbradoPorFecha {

    private static final String[] CLAVES_FISCALES = {
        "fiscal.timbrado_numero", "fiscal.timbrado_vigencia_desde", "fiscal.timbrado_vigencia_hasta",
        "fiscal.factura_numero_desde", "fiscal.factura_numero_hasta"
    };

    @Test
    public void resuelveElTimbradoVigenteEnLaFechaDelCobroNoElDeHoy() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                ParametroDAO parametroDAO = new ParametroDAO();
                ServicioParametros servicioParametros = new ServicioParametros();
                ServicioCobranza servicioCobranza = new ServicioCobranza();
                long idUsuario = obtenerUnId(conexion, "SELECT id_usuario FROM usuarios LIMIT 1");

                // El timbrado ACTUAL (sembrado por 03_parametros.sql) empezó a regir -- como
                // PARÁMETRO -- el día que se corrió recrear_base.sh. El anterior (de prueba) se
                // encadena exacto hasta ahí, sin agujero ni superposición.
                ParametroSistema actualVigenciaDesde = parametroDAO.obtenerVigentePorClave(conexion,
                        "fiscal.timbrado_vigencia_desde");
                LocalDate limiteEntreTimbrados = actualVigenciaDesde.getVigenteDesde();
                LocalDate inicioTimbradoAnterior = limiteEntreTimbrados.minusDays(400);

                String[] valoresTimbradoAnterior = {
                    "15679234-DEMO", "2025-09-01", "2026-08-31", "1", "9999999"
                };
                for (int i = 0; i < CLAVES_FISCALES.length; i++) {
                    ParametroSistema historico = new ParametroSistema();
                    historico.setClaveParametro(CLAVES_FISCALES[i]);
                    historico.setNombreParametro("(prueba D-116) " + CLAVES_FISCALES[i]);
                    historico.setValorParametro(valoresTimbradoAnterior[i]);
                    historico.setTipoValor(i == 0 ? TipoValorParametro.TEXTO
                            : (i <= 2 ? TipoValorParametro.FECHA : TipoValorParametro.ENTERO));
                    historico.setVigenteDesde(inicioTimbradoAnterior);
                    historico.setModificadoPor(idUsuario);
                    historico.setObservacion("Prueba D-116, revertido con rollback.");
                    long idHistorico = parametroDAO.insertar(conexion, historico);
                    parametroDAO.cerrarVigencia(conexion, idHistorico, limiteEntreTimbrados);
                }

                // 1. Fecha dentro del timbrado ANTERIOR -> usa el anterior.
                LocalDate fechaAntigua = LocalDate.of(2026, 3, 15);
                assertEquals("15679234-DEMO",
                        servicioParametros.obtenerValorAFecha(conexion, "fiscal.timbrado_numero", fechaAntigua));
                servicioCobranza.validarTimbradoVigente(conexion, fechaAntigua); // no debe lanzar

                // 2. Fecha de HOY -> usa el actual (el sembrado por 03_parametros.sql, sin tocar).
                LocalDate hoy = py.com.capitalcredito.sgcpc.infraestructura.Reloj.hoy();
                String timbradoActual = servicioParametros.obtenerValor(conexion, "fiscal.timbrado_numero");
                assertEquals(timbradoActual,
                        servicioParametros.obtenerValorAFecha(conexion, "fiscal.timbrado_numero", hoy));
                servicioCobranza.validarTimbradoVigente(conexion, hoy); // no debe lanzar

                // 3. Fecha fuera de los dos (después de que vence el timbrado actual) -> rechazo.
                LocalDate timbradoActualHasta = servicioParametros.obtenerValorFecha(conexion,
                        "fiscal.timbrado_vigencia_hasta");
                LocalDate fechaFueraDeAmbos = timbradoActualHasta.plusDays(30);
                try {
                    servicioCobranza.validarTimbradoVigente(conexion, fechaFueraDeAmbos);
                    fail("Debía rechazar una fecha posterior a la vigencia del timbrado actual.");
                } catch (NegocioException error) {
                    // esperado
                }
            } finally {
                conexion.rollback();
            }
        }
    }

    private long obtenerUnId(Connection conexion, String sql) throws Exception {
        try (java.sql.Statement sentencia = conexion.createStatement();
                java.sql.ResultSet resultado = sentencia.executeQuery(sql)) {
            resultado.next();
            return resultado.getLong(1);
        }
    }
}
