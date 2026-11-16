package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import py.com.capitalcredito.sgcpc.dao.CuotaDAO;
import py.com.capitalcredito.sgcpc.dao.GestionCobranzaDAO;
import py.com.capitalcredito.sgcpc.dao.PagoDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dto.FilaVencimiento;
import py.com.capitalcredito.sgcpc.dto.IndicadoresPanel;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;

/**
 * P02, panel del día (Doc. 00 §12 / RF-43 / PO-15, Bloque 4 de E8). Cada indicador reusa el
 * servicio que ya lo calcula en su propio módulo (ninguna cuenta financiera nueva acá, REGLAS_DESARROLLO.md
 * §5) -- este servicio solo arma qué mostrar según el perfil.
 */
public class ServicioPanel {

    private final PagoDAO pagoDAO = new PagoDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final CuotaDAO cuotaDAO = new CuotaDAO();
    private final GestionCobranzaDAO gestionCobranzaDAO = new GestionCobranzaDAO();
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();
    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioMora servicioMora = new ServicioMora();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    private final ServicioReversiones servicioReversiones = new ServicioReversiones();
    private final ServicioCobranza servicioCobranza = new ServicioCobranza();
    // B99 (30/10/2026): para la tarjeta "Vencen hoy", que usa el MISMO dato que la lista de
    // /cobranza. Sin ciclo de construccion: ServicioReportes no referencia a ServicioPanel
    // (el ciclo que ya documento armarListaTrabajo es entre Cobranza/Mora/Reportes).
    private final ServicioReportes servicioReportes = new ServicioReportes();

    public IndicadoresPanel calcularIndicadores(Connection conexion, long idUsuario, PerfilUsuario perfil)
            throws SQLException {
        // Decisión 5 (Bloque 4, E8): el panel es, junto con /mora, quien dispara el recálculo
        // diario -- PO-15 paso 1 ("el sistema actualiza estados del día").
        servicioMora.recalcularMorosidadDiaria(conexion, idUsuario);

        LocalDate hoy = Reloj.hoy();
        IndicadoresPanel indicadores = new IndicadoresPanel();

        // Los 3 perfiles: cobros de hoy y préstamos en mora.
        indicadores.setCantidadCobrosHoy(pagoDAO.contarConfirmadosEntreFechas(conexion, hoy, hoy));
        indicadores.setMontoCobrosHoy(FormateadorNumeros.comoGuaranies(pagoDAO.sumarAplicadoEntreFechas(conexion, hoy, hoy)));
        indicadores.setPrestamosEnMora(prestamoDAO.listarEnMora(conexion).size());
        // B99 (30/10/2026, hallazgo de Joel probando el paso 15): con una cuota venciendo hoy,
        // ninguna tarjeta se lo decia a la cajera -- "Cobros de hoy" es lo ya cobrado. El dato sale
        // del MISMO lugar que la lista de /cobranza, no de una consulta nueva: los vencimientos del
        // dia (ServicioReportes.listarVencimientos) filtrados por ServicioCobranza.armarListaTrabajo,
        // que saca los prestamos ya contados en "En mora" para no mostrarlos dos veces.
        List<FilaVencimiento> vencenHoy = servicioCobranza.armarListaTrabajo(
                servicioMora.listarPrestamosEnMora(conexion),
                servicioReportes.listarVencimientos(conexion, hoy, hoy),
                Collections.<FilaVencimiento>emptyList()).getVencenHoy();
        indicadores.setVencenHoyCantidad(vencenHoy.size());
        BigDecimal totalVencenHoy = BigDecimal.ZERO;
        for (FilaVencimiento fila : vencenHoy) {
            totalVencenHoy = totalVencenHoy.add(fila.getMontoExacto());
        }
        indicadores.setVencenHoyMonto(FormateadorNumeros.comoGuaranies(totalVencenHoy));
        // Bloque 6 (PA-13): los 3 perfiles lo ven -- la cajera es quien primero se topa con el
        // rechazo duro real al cobrar, así que no tiene sentido ocultárselo acá.
        indicadores.setAvisosTimbrado(servicioCobranza.obtenerAvisosTimbrado(conexion, hoy));

        // D7 (pedido de Joel): el administrador ve lo mismo que el gerente en el panel.
        if (perfil == PerfilUsuario.OFICIAL_CREDITO || perfil == PerfilUsuario.GERENTE_ADMINISTRADOR
                || perfil == PerfilUsuario.ADMINISTRADOR) {
            indicadores.setPromesasDeHoy(gestionCobranzaDAO.contarPromesasDeHoy(conexion));
        }

        if (perfil == PerfilUsuario.CAJERO || perfil == PerfilUsuario.GERENTE_ADMINISTRADOR
                || perfil == PerfilUsuario.ADMINISTRADOR) {
            indicadores.setDesembolsosPendientes(prestamoDAO.listarPendientesDesembolso(conexion).size());
            indicadores.setDesembolsosListos(servicioFormalizacion.listarListosParaDesembolsar(conexion).size());
            // B100 (01/11/2026): la tarjeta decía "N pendientes · 0 listos" sin nombrar lo que
            // falta. La diferencia entre los dos números ES "les falta el checklist", así que el
            // aviso sale del mismo checklist que ya pinta la ficha de Formalización.
            indicadores.setFaltaParaDesembolsar(
                    servicioFormalizacion.obtenerAvisoDeLoQueFaltaParaDesembolsar(conexion));
            indicadores.setTotalAProveer(FormateadorNumeros.comoGuaranies(servicioCaja.calcularFondoSugerido(conexion)));
            SesionCaja sesionAbierta = servicioCaja.obtenerSesionAbierta(conexion);
            indicadores.setHayCajaAbierta(sesionAbierta != null);
            if (sesionAbierta != null) {
                indicadores.setEfectivoEnCaja(
                        FormateadorNumeros.comoGuaranies(servicioCaja.calcularSaldoTeorico(conexion, sesionAbierta.getIdSesionCaja())));
            }
        }

        if (perfil == PerfilUsuario.GERENTE_ADMINISTRADOR || perfil == PerfilUsuario.ADMINISTRADOR) {
            indicadores.setCarteraEnLaCalle(FormateadorNumeros.comoGuaranies(cuotaDAO.sumarCapitalPendienteActivos(conexion)));
            LocalDate inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate inicioMes = hoy.withDayOfMonth(1);
            indicadores.setCobradoSemana(FormateadorNumeros.comoGuaranies(pagoDAO.sumarAplicadoEntreFechas(conexion, inicioSemana, hoy)));
            indicadores.setCobradoMes(FormateadorNumeros.comoGuaranies(pagoDAO.sumarAplicadoEntreFechas(conexion, inicioMes, hoy)));
            indicadores.setSolicitudesPendientesAprobar(solicitudDAO.listarPendientesAprobacion(conexion).size());
            indicadores.setReversionesPorAutorizar(servicioReversiones.listarSolicitadas(conexion).size());
            // B63 (FASE2_PLAN.md, confirmado en pantalla el 23/10/2026): al cerrar con diferencia
            // el sistema le dice a la cajera "Queda pendiente de revisión del gerente", pero el
            // panel del gerente no lo avisaba -- el único camino era entrar a /caja por su cuenta.
            // Mismo dato (RN-23) que ya alimenta la tarjeta de esa pantalla, sin consulta nueva.
            indicadores.setDiferenciasDeCajaSinRevisar(servicioCaja.listarConDiferenciaSinRevisar(conexion).size());
        }

        if (perfil == PerfilUsuario.OFICIAL_CREDITO) {
            int enCurso = 0;
            for (py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitud
                    : solicitudDAO.listarPorOficial(conexion, idUsuario, null)) {
                if (solicitud.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.BORRADOR
                        || solicitud.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.PENDIENTE_APROBACION) {
                    enCurso++;
                }
            }
            indicadores.setMisSolicitudesEnCurso(enCurso);
            // B45 y B76 (FASE2_PLAN.md, "principio de paneles" de recorrido/00_LEEME.md): en
            // cuanto el gerente aprueba, "Mis solicitudes en curso" deja de contarla (el bucle de
            // arriba solo cuenta BORRADOR y PENDIENTE_APROBACION) y nada le avisaba al oficial
            // que le quedaba trabajo pendiente -- ni en Formalización (B45) ni en el cierre
            // documental de RF29 (B76). Las dos consultas son las mismas que ya alimentan las
            // secciones de /formalizacion, sin ninguna consulta nueva.
            indicadores.setAprobadasParaFormalizar(solicitudDAO.listarAprobadasSinFormalizar(conexion).size());
            indicadores.setCierresDocumentalesPendientes(
                    servicioFormalizacion.listarFinalizadosConCierrePendiente(conexion).size());
        }

        return indicadores;
    }
}
