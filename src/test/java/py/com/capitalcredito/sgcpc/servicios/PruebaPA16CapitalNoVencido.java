package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dao.AplicacionPagoDAO;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoPago;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.AplicacionPago;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Pago;

/**
 * D-111/PA-16 (Bloque 7/E10) -- a pedido de Joel: llama al código REAL que usa la constancia
 * (AplicacionPagoDAO.sumarCapitalNoVencidoPorPago) contra la base real, no solo un cálculo en
 * memoria como ServicioMotorFinancieroTest. Inserta cuotas/pago/aplicaciones de prueba dentro de
 * una transacción que NUNCA se confirma (rollback en el finally, D-75: no toca ni deja ningún
 * dato real) -- reusa por FK un préstamo/plan/sesión/usuario ya existentes en la base (cualquiera
 * sirve, esta prueba no depende de sus datos, solo necesita que las FK sean válidas).
 *
 * No corre con "mvn clean package"/"mvn test" a propósito (el nombre no matchea los patrones de
 * Surefire, mismo patrón que PruebaConexionBD): se ejecuta explícitamente con
 * "mvn -Dtest=PruebaPA16CapitalNoVencido test", con la base ya arriba.
 *
 * También confirma el criterio del límite (punto 2 de Joel): una cuota que vence EXACTAMENTE el
 * día del cálculo tiene que contarse como NO vencida, igual que
 * ServicioMotorFinanciero.calcularCancelacionTotal ("vencida = fechaVencimiento.isBefore(fecha)",
 * estrictamente antes) -- la consulta SQL usa "fecha_vencimiento >= ?", el mismo corte.
 */
public class PruebaPA16CapitalNoVencido {

    @Test
    public void sumarCapitalNoVencidoPorPagoCoincideConElMotorEnElLimiteExacto() throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                long idPrestamo = obtenerUnId(conexion, "SELECT id_prestamo FROM prestamos LIMIT 1");
                long idPlanPago = obtenerUnId(conexion, "SELECT id_plan_pago FROM planes_pago LIMIT 1");
                long idSesionCaja = obtenerUnId(conexion, "SELECT id_sesion_caja FROM sesiones_caja LIMIT 1");
                long idUsuario = obtenerUnId(conexion, "SELECT id_usuario FROM usuarios LIMIT 1");

                LocalDate fechaCalculo = LocalDate.of(2026, 1, 15);

                // 3 cuotas de prueba: una vencida (antes), una EXACTO el día del cálculo (el
                // límite que pide confirmar Joel) y una claramente no vencida (después).
                List<Cuota> cuotasPrueba = new ArrayList<>();
                cuotasPrueba.add(cuotaDePrueba(9901, fechaCalculo.minusDays(1)));
                cuotasPrueba.add(cuotaDePrueba(9902, fechaCalculo));
                cuotasPrueba.add(cuotaDePrueba(9903, fechaCalculo.plusDays(1)));
                new CuotaDAO().insertarTodas(conexion, idPlanPago, cuotasPrueba);

                long idCuotaVencida = obtenerIdCuota(conexion, idPlanPago, 9901);
                long idCuotaLimite = obtenerIdCuota(conexion, idPlanPago, 9902);
                long idCuotaNoVencida = obtenerIdCuota(conexion, idPlanPago, 9903);

                BigDecimal capitalVencida = new BigDecimal("100000");
                BigDecimal capitalLimite = new BigDecimal("200000");
                BigDecimal capitalNoVencida = new BigDecimal("300000");

                Pago pagoPrueba = new Pago();
                pagoPrueba.setIdentificadorConfirmacion("PA16-" + UUID.randomUUID());
                pagoPrueba.setIdPrestamo(idPrestamo);
                pagoPrueba.setIdSesionCaja(idSesionCaja);
                pagoPrueba.setNumeroRecibo("PA16-" + UUID.randomUUID().toString().substring(0, 8));
                BigDecimal totalAplicado = capitalVencida.add(capitalLimite).add(capitalNoVencida);
                pagoPrueba.setMontoRecibido(totalAplicado);
                pagoPrueba.setMontoVuelto(BigDecimal.ZERO);
                pagoPrueba.setMontoAplicado(totalAplicado);
                pagoPrueba.setRegistradoPor(idUsuario);
                pagoPrueba.setTipo(TipoPago.CANCELACION);
                long idPago = new PagoDAO().insertar(conexion, pagoPrueba);

                AplicacionPagoDAO aplicacionPagoDAO = new AplicacionPagoDAO();
                aplicacionPagoDAO.insertar(conexion, aplicacionDePrueba(idPago, idCuotaVencida, capitalVencida));
                aplicacionPagoDAO.insertar(conexion, aplicacionDePrueba(idPago, idCuotaLimite, capitalLimite));
                aplicacionPagoDAO.insertar(conexion, aplicacionDePrueba(idPago, idCuotaNoVencida, capitalNoVencida));

                // El código REAL que usa la constancia (D-111).
                BigDecimal capitalNoVencidoReal = aplicacionPagoDAO.sumarCapitalNoVencidoPorPago(conexion, idPago,
                        fechaCalculo);

                // Cálculo independiente, mismo criterio que el motor (isBefore, estrictamente
                // antes): la cuota límite (vence EXACTO ese día) cuenta como NO vencida.
                BigDecimal capitalNoVencidoEsperado = capitalLimite.add(capitalNoVencida);

                assertEquals(capitalNoVencidoEsperado, capitalNoVencidoReal);
            } finally {
                conexion.rollback();
            }
        }
    }

    private Cuota cuotaDePrueba(int numeroCuota, LocalDate fechaVencimiento) {
        Cuota cuota = new Cuota();
        cuota.setNumeroCuota(numeroCuota);
        cuota.setFechaVencimiento(fechaVencimiento);
        cuota.setFechaVencimientoTeorica(fechaVencimiento);
        cuota.setCapitalProgramado(BigDecimal.ZERO);
        cuota.setInteresProgramado(BigDecimal.ZERO);
        cuota.setTributosProgramados(BigDecimal.ZERO);
        cuota.setTotalProgramado(BigDecimal.ZERO);
        cuota.setSaldoCapitalPosterior(BigDecimal.ZERO);
        cuota.setEstadoPago(EstadoPago.PAGADA);
        return cuota;
    }

    private AplicacionPago aplicacionDePrueba(long idPago, long idCuota, BigDecimal capital) {
        AplicacionPago aplicacion = new AplicacionPago();
        aplicacion.setIdPago(idPago);
        aplicacion.setTipoAplicacion(TipoAplicacion.CUOTA);
        aplicacion.setIdCuota(idCuota);
        aplicacion.setMontoCapital(capital);
        aplicacion.setMontoInteres(BigDecimal.ZERO);
        aplicacion.setMontoMoratorio(BigDecimal.ZERO);
        aplicacion.setMontoPunitorio(BigDecimal.ZERO);
        aplicacion.setMontoTributos(BigDecimal.ZERO);
        aplicacion.setSubtotalAplicado(capital);
        return aplicacion;
    }

    private long obtenerUnId(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            if (!resultado.next()) {
                throw new IllegalStateException(
                        "No hay préstamos de prueba en la base: corra el cargador o el humo antes.");
            }
            return resultado.getLong(1);
        }
    }

    private long obtenerIdCuota(Connection conexion, long idPlanPago, int numeroCuota) throws SQLException {
        String sql = "SELECT id_cuota FROM cuotas WHERE id_plan_pago = ? AND numero_cuota = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPlanPago);
            sentencia.setInt(2, numeroCuota);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }
}
