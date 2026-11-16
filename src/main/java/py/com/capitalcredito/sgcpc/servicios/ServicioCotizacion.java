package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.FeriadoDAO;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;

/**
 * P13, cotizador (Doc. 00 §12/RF-33, Anexo A: ServicioCotizacion). Capa fina
 * de solo lectura sobre ServicioMotorFinanciero.cotizar (ya construido y
 * probado en E1): valida límites y delega el cálculo. No escribe nada ni
 * audita (RF-33: "no guarda registros").
 */
public class ServicioCotizacion {

    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioMotorFinanciero servicioMotorFinanciero = new ServicioMotorFinanciero();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final FeriadoDAO feriadoDAO = new FeriadoDAO();

    /** Pura. */
    public void validarMontoDentroDeLimites(BigDecimal monto, BigDecimal montoMinimo, BigDecimal montoMaximo) {
        if (monto.compareTo(montoMinimo) < 0 || monto.compareTo(montoMaximo) > 0) {
            // D-86 (revisado) + RF05 (pedido de Joel, 10/10/2026): pie.jsp (dividirEnTituloYDetalle)
            // corta en el primer punto seguido de espacio+mayúscula -- "Gs." + ". Ajuste..." deja
            // el título limpio ("...15.000.000 Gs.") y el detalle como una oración real ("Ajuste
            // el monto..."), sin perder el punto de "Gs." en el título (el primero de los dos
            // puntos consecutivos es el que separa título y detalle; el segundo, el de "Gs.", queda).
            throw new NegocioException("El monto debe estar entre " + FormateadorNumeros.comoGuaranies(montoMinimo)
                    + " y " + FormateadorNumeros.comoGuaranies(montoMaximo)
                    + ". Ajuste el monto dentro de ese rango.");
        }
    }

    /** Pura. */
    public void validarPlazoDentroDeLimites(int plazoMeses, int plazoMinimo, int plazoMaximo) {
        if (plazoMeses < plazoMinimo || plazoMeses > plazoMaximo) {
            // RF05 (pedido de Joel, 10/10/2026): título (rango concreto) y detalle (qué hacer).
            throw new NegocioException("El plazo debe estar entre " + plazoMinimo + " y " + plazoMaximo
                    + " meses. Ajuste el plazo dentro de ese rango.");
        }
    }

    /** RF01-O (pedido de Joel): el cotizador reutiliza la misma regla de ServicioFormalizacion
     * (rango de días desde hoy, parámetros formalizacion.dias_min/max_primer_vencimiento) en vez
     * de solo rechazar fechas pasadas -- usa "hoy" como referencia porque el cotizador no tiene
     * una fecha de formalización real todavía. RF05 (pedido de Joel, 10/10/2026): ServicioSolicitudes
     * pasó a usar también este mismo método (antes tenía su propia regla, más laxa, de solo
     * rechazar fechas pasadas) -- mismo rango y mismo mensaje en las tres pantallas (cotizador,
     * solicitud y formalización). Pura. */
    public void validarRangoFechaPrimerVencimiento(LocalDate fecha, LocalDate hoy, int diasMinimo, int diasMaximo) {
        servicioFormalizacion.validarPrimerVencimientoFormalizacion(hoy, fecha, diasMinimo, diasMaximo);
    }

    /** RF01 "Comparar plazos" (pedido de Joel): 6/12/18/24 meses, recortado al rango vigente
     * (credito.plazo_minimo/maximo) -- nunca se muestra un plazo fuera de rango. Si el plazo
     * cotizado no está entre esos cuatro, se agrega en su lugar ordenado (ej. 9 meses entre 6 y
     * 12). Pura: no calcula cuotas, solo arma la lista de plazos a cotizar. */
    public List<Integer> construirPlazosComparacion(int plazoMinimo, int plazoMaximo, int plazoActual) {
        List<Integer> plazos = new ArrayList<>();
        for (int candidato : Arrays.asList(6, 12, 18, 24)) {
            if (candidato >= plazoMinimo && candidato <= plazoMaximo) {
                plazos.add(candidato);
            }
        }
        if (!plazos.contains(plazoActual) && plazoActual >= plazoMinimo && plazoActual <= plazoMaximo) {
            int posicion = 0;
            while (posicion < plazos.size() && plazos.get(posicion) < plazoActual) {
                posicion++;
            }
            plazos.add(posicion, plazoActual);
        }
        return plazos;
    }

    /** PO-01: cotización rápida, sin datos del cliente, sin persistir nada. */
    public ResultadoCotizacion cotizar(BigDecimal monto, int plazoMeses, LocalDate fechaPrimerVencimiento)
            throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            int diasMinimoVencimiento = servicioParametros.obtenerValorEntero(conexion,
                    "formalizacion.dias_min_primer_vencimiento");
            int diasMaximoVencimiento = servicioParametros.obtenerValorEntero(conexion,
                    "formalizacion.dias_max_primer_vencimiento");
            validarRangoFechaPrimerVencimiento(fechaPrimerVencimiento, Reloj.hoy(), diasMinimoVencimiento,
                    diasMaximoVencimiento);

            BigDecimal montoMinimo = servicioParametros.obtenerValorDecimal(conexion, "credito.monto_minimo");
            BigDecimal montoMaximo = servicioParametros.obtenerValorDecimal(conexion, "credito.monto_maximo");
            validarMontoDentroDeLimites(monto, montoMinimo, montoMaximo);

            int plazoMinimo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_minimo");
            int plazoMaximo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_maximo");
            validarPlazoDentroDeLimites(plazoMeses, plazoMinimo, plazoMaximo);

            BigDecimal teaEmpresa = servicioParametros.obtenerValorDecimal(conexion, "credito.tea_empresa");
            LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
            servicioParametros.resolverTopeLegalVigente(limiteVigente);
            ResultadoLimiteTasa resultadoLimite = servicioMotorFinanciero.validarLimiteTasa(teaEmpresa,
                    limiteVigente.getTasaMaximaTea());
            if (!resultadoLimite.isOk()) {
                throw new NegocioException("La TEA de la empresa (" + FormateadorNumeros.comoPorcentaje(teaEmpresa)
                        + ") supera el tope legal vigente (" + FormateadorNumeros.comoPorcentaje(limiteVigente.getTasaMaximaTea())
                        + ").");
            }

            return servicioMotorFinanciero.cotizar(monto, plazoMeses, fechaPrimerVencimiento, teaEmpresa,
                    feriadoDAO.listarFechasActivas(conexion));
        }
    }
}
