package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dto.FilaAuditoria;
import py.com.capitalcredito.sgcpc.dto.FilaCuotaPendiente;
import py.com.capitalcredito.sgcpc.dto.FilaGestionReporte;
import py.com.capitalcredito.sgcpc.dto.FilaHistorialCobranza;
import py.com.capitalcredito.sgcpc.dto.FilaInformeAprobacion;
import py.com.capitalcredito.sgcpc.dto.FilaMora;
import py.com.capitalcredito.sgcpc.dto.FilaInformeCliente;
import py.com.capitalcredito.sgcpc.dto.FilaInformeCobro;
import py.com.capitalcredito.sgcpc.dto.FilaInformeDesembolso;
import py.com.capitalcredito.sgcpc.dto.FilaInformePrestamo;
import py.com.capitalcredito.sgcpc.dto.FilaInformeSolicitud;
import py.com.capitalcredito.sgcpc.dto.FiltroAprobacionesReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;
import py.com.capitalcredito.sgcpc.dto.FiltroCobrosReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroDesembolsosReporte;
import py.com.capitalcredito.sgcpc.dto.FiltroPrestamos;
import py.com.capitalcredito.sgcpc.dto.FiltroSolicitudesReporte;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoPrestamo;
import py.com.capitalcredito.sgcpc.dto.ResumenEstadoSolicitud;
import py.com.capitalcredito.sgcpc.dto.TotalesAprobaciones;
import py.com.capitalcredito.sgcpc.dto.TotalesCobros;
import py.com.capitalcredito.sgcpc.dto.TotalesDesembolsos;
import py.com.capitalcredito.sgcpc.dto.TotalesEstadoCuenta;
import py.com.capitalcredito.sgcpc.dto.TotalesPrestamos;
import py.com.capitalcredito.sgcpc.dto.TotalesSolicitudesReporte;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.GlosarioFinanciero;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;

/**
 * Bloque 7/E10 (D-135, pedido de Joel): "Informes" imprimibles con filtros -- distinto de un
 * "Reporte" (la consulta en pantalla que ya existe en /reportes): un Informe es el documento
 * formal en PDF que refleja EXACTAMENTE los filtros elegidos, con cabecera de empresa, la línea
 * "Filtros aplicados", totales/subtotales y pie con paginación, y queda registrado en Auditoría.
 * En esta tanda, solo Clientes -- el resto de los métodos de este Servicio (construirTextoFiltros*,
 * generarInforme*) son el molde para los próximos módulos (punto 4 del pedido de Joel).
 */
public class ServicioInformes {

    private final ServicioParametros servicioParametros = new ServicioParametros();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();

    // RF34 (estado de cuenta): los dos subreportes de "Historial de cobros"/"Gestiones de
    // cobranza" se cargan del classpath una sola vez y se cachean como JasperReport -- mismo
    // criterio que el LOGO cacheado de GeneradorInformesPdf (nunca releer el .jasper del disco en
    // cada PDF generado).
    private static final Map<String, net.sf.jasperreports.engine.JasperReport> CACHE_SUBREPORTES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static net.sf.jasperreports.engine.JasperReport cargarSubreporteEstadoCuenta(String nombreArchivo) {
        return CACHE_SUBREPORTES.computeIfAbsent(nombreArchivo, archivo -> {
            try (java.io.InputStream entrada = ServicioInformes.class.getResourceAsStream("/reportes/" + archivo)) {
                if (entrada == null) {
                    throw new IllegalStateException("No se encontró \"" + archivo + "\" en el classpath.");
                }
                return (net.sf.jasperreports.engine.JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(entrada);
            } catch (java.io.IOException | net.sf.jasperreports.engine.JRException error) {
                throw new IllegalStateException("No se pudo cargar el subreporte \"" + archivo + "\".", error);
            }
        });
    }

    /** 13/10/2026 (pedido de Joel): agrega la cláusula "Cliente/operación: ..." al texto de
     * filtros de un informe, para que el PDF/CSV muestre en el encabezado el filtro aplicado --
     * un solo lugar para los 6 informes que lo usan (Caja no aplica, ver RF32 §10). Sin texto
     * (null o vacío), no cambia nada. */
    public String agregarClausulaClienteUOperacion(String textoBase, String clienteUOperacion) {
        return clienteUOperacion == null || clienteUOperacion.trim().isEmpty()
                ? textoBase
                : textoBase + " · Cliente/operación: " + clienteUOperacion.trim();
    }

    /**
     * Texto legible de "Filtros aplicados:" (pura, sin base) -- mismo orden que el formulario de
     * /clientes. Cuando un filtro no se eligió, dice "Todos/as" (pedido explícito de Joel), nunca
     * se omite en silencio.
     */
    public String construirTextoFiltrosClientes(FiltroClientes filtro, String etiquetaEstado,
            String nombreDepartamento, String nombreCiudad) {
        List<String> partes = new ArrayList<>();
        partes.add(construirTextoRangoFechasAlta(filtro.getAltaDesde(), filtro.getAltaHasta()));
        partes.add(etiquetaEstado);
        if (nombreDepartamento != null) {
            partes.add(nombreDepartamento + (nombreCiudad != null ? " / " + nombreCiudad : ""));
        } else {
            partes.add("Todos los departamentos");
        }
        partes.add(filtro.getSituacionLaboral() != null
                ? ServicioClientes.etiquetaSituacionLaboral(filtro.getSituacionLaboral())
                : "Todas las situaciones laborales");
        if ("SI".equals(filtro.getPrestamoActivo())) {
            partes.add("Con préstamo activo");
        } else if ("NO".equals(filtro.getPrestamoActivo())) {
            partes.add("Sin préstamo activo");
        } else {
            partes.add("Con o sin préstamo activo");
        }
        if (filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty()) {
            partes.add("Texto: \"" + filtro.getTexto().trim() + "\"");
        }
        return String.join(" · ", partes);
    }

    private String construirTextoRangoFechasAlta(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return "Alta del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
        } else if (desde != null) {
            return "Alta desde " + FormateadorFechas.comoDdMmAaaa(desde);
        } else if (hasta != null) {
            return "Alta hasta " + FormateadorFechas.comoDdMmAaaa(hasta);
        }
        return "Todas las fechas de alta";
    }

    /** Filas del detalle, ya formateadas -- el .jrxml no calcula nada (REGLAS_DESARROLLO.md §6). */
    public List<FilaInformeCliente> construirFilasInformeClientes(List<Persona> personas,
            Map<Long, Long> idCiudadPorPersona, Map<Long, String> ciudadPorId,
            Map<Long, String> situacionLaboralPorPersona, Map<Long, Integer> prestamosActivosPorId) {
        List<FilaInformeCliente> filas = new ArrayList<>();
        for (Persona persona : personas) {
            // Normalización de personas, grupo Domicilio (16/10/2026): la ciudad ya no está en
            // Persona -- idCiudadPorPersona la resuelve (PersonaDomicilioDAO.mapaIdCiudadPorPersona).
            Long idCiudad = idCiudadPorPersona.get(persona.getIdPersona());
            String ciudad = idCiudad == null ? null : ciudadPorId.get(idCiudad);
            // Normalización de personas, grupo Laborales (16/10/2026): la situación laboral ya no
            // está en Persona -- situacionLaboralPorPersona la resuelve (mismo patrón que la ciudad).
            String situacionLaboral = situacionLaboralPorPersona.get(persona.getIdPersona());
            Integer prestamosActivos = prestamosActivosPorId.get(persona.getIdPersona());
            filas.add(new FilaInformeCliente(
                    py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto.comoCedulaConPuntos(persona.getCedulaIdentidad()),
                    persona.getApellidos() + " " + persona.getNombres(),
                    persona.getTelefonoWhatsapp() == null ? "-" : persona.getTelefonoWhatsapp(),
                    ciudad == null ? "-" : ciudad,
                    ServicioClientes.etiquetaSituacionLaboral(situacionLaboral),
                    persona.getFechaRegistro() == null ? "-" : FormateadorFechas.comoDdMmAaaa(persona.getFechaRegistro().toLocalDate()),
                    prestamosActivos == null ? 0 : prestamosActivos,
                    persona.getEstadoPersona().getEtiqueta()));
        }
        return filas;
    }

    /** Subtotales por situación laboral, orden alfabético por la etiqueta legible (pura). */
    public Map<String, Integer> calcularSubtotalesPorSituacionLaboral(List<Persona> personas,
            Map<Long, String> situacionLaboralPorPersona) {
        Map<String, Integer> subtotales = new TreeMap<>();
        for (Persona persona : personas) {
            String etiqueta = ServicioClientes.etiquetaSituacionLaboral(situacionLaboralPorPersona.get(persona.getIdPersona()));
            subtotales.merge(etiqueta, 1, Integer::sum);
        }
        return subtotales;
    }

    /** Subtotales por ciudad, orden alfabético (pura). */
    public Map<String, Integer> calcularSubtotalesPorCiudad(List<Persona> personas,
            Map<Long, Long> idCiudadPorPersona, Map<Long, String> ciudadPorId) {
        Map<String, Integer> subtotales = new TreeMap<>();
        for (Persona persona : personas) {
            Long idCiudad = idCiudadPorPersona.get(persona.getIdPersona());
            String ciudad = idCiudad == null ? null : ciudadPorId.get(idCiudad);
            subtotales.merge(ciudad == null ? "Sin ciudad" : ciudad, 1, Integer::sum);
        }
        return subtotales;
    }

    /** "Etiqueta: N" por línea, para el bloque de subtotales del PDF (pura). */
    public String construirTextoSubtotales(Map<String, Integer> subtotales) {
        if (subtotales.isEmpty()) {
            return "(sin datos)";
        }
        List<String> lineas = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : subtotales.entrySet()) {
            lineas.add(entrada.getKey() + ": " + entrada.getValue());
        }
        return String.join("\n", lineas);
    }

    /**
     * Arma el PDF (mismo mecanismo común de GeneradorInformesPdf) y audita la emisión (quién y
     * con qué filtros -- pedido explícito de Joel). No recibe la Connection desde afuera para el
     * llenado del PDF en sí (no toca la base), pero sí para auditar.
     */
    public byte[] generarInformeClientesPdf(Connection conexion, List<Persona> personas,
            Map<Long, Long> idCiudadPorPersona, Map<Long, String> ciudadPorId,
            Map<Long, String> situacionLaboralPorPersona, Map<Long, Integer> prestamosActivosPorId,
            String textoFiltros, long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        List<FilaInformeCliente> filas = construirFilasInformeClientes(personas, idCiudadPorPersona, ciudadPorId,
                situacionLaboralPorPersona, prestamosActivosPorId);
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_CLIENTES", personas.size());
        parametros.put("SUBTOTALES_SITUACION_LABORAL_TEXTO",
                construirTextoSubtotales(calcularSubtotalesPorSituacionLaboral(personas, situacionLaboralPorPersona)));
        parametros.put("SUBTOTALES_CIUDAD_TEXTO",
                construirTextoSubtotales(calcularSubtotalesPorCiudad(personas, idCiudadPorPersona, ciudadPorId)));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_clientes.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CLIENTES", 0,
                textoFiltros + " · " + personas.size() + " cliente(s).");
        return pdf;
    }

    /** Auditoría del CSV -- mismo criterio que el PDF (Joel: "queda registrado en Auditoría"). */
    public void auditarExportacionCsvClientes(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CLIENTES", 0,
                textoFiltros + " · " + cantidad + " cliente(s).");
    }

    // ======================================================================
    // B1 (Bloque 7/E10, pedido de Joel): Informe de Préstamos -- mismo molde que Clientes de
    // arriba, aplicado al próximo módulo (punto 4 del pedido original de D-135).
    // ======================================================================

    /** Texto legible de "Filtros aplicados:" para /prestamos -- mismo criterio que
     * construirTextoFiltrosClientes: cuando un filtro no se eligió, dice "Todos/as", nunca se
     * omite en silencio. */
    public String construirTextoFiltrosPrestamos(FiltroPrestamos filtro, String etiquetaEstado) {
        List<String> partes = new ArrayList<>();
        partes.add(construirTextoRangoFechasDesembolso(filtro.getDesembolsoDesde(), filtro.getDesembolsoHasta()));
        partes.add(etiquetaEstado);
        if (filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty()) {
            partes.add("Texto: \"" + filtro.getTexto().trim() + "\"");
        }
        return String.join(" · ", partes);
    }

    private String construirTextoRangoFechasDesembolso(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return "Desembolso del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
        } else if (desde != null) {
            return "Desembolso desde " + FormateadorFechas.comoDdMmAaaa(desde);
        } else if (hasta != null) {
            return "Desembolso hasta " + FormateadorFechas.comoDdMmAaaa(hasta);
        }
        return "Todas las fechas de desembolso";
    }

    /** "Estado: cantidad — monto X — saldo Y" por línea, para el bloque de resumen por estado
     * del PDF (mismo criterio de texto plano que construirTextoSubtotales, de arriba). */
    public String construirTextoResumenPorEstadoPrestamos(List<ResumenEstadoPrestamo> resumenPorEstado) {
        if (resumenPorEstado.isEmpty()) {
            return "(sin datos)";
        }
        List<String> lineas = new ArrayList<>();
        for (ResumenEstadoPrestamo fila : resumenPorEstado) {
            lineas.add(fila.getEstado() + ": " + fila.getCantidad() + " — monto " + fila.getMonto()
                    + " — saldo " + fila.getSaldo());
        }
        return String.join("\n", lineas);
    }

    /** Arma el PDF (mismo mecanismo común de GeneradorInformesPdf, hoja horizontal) y audita la
     * emisión -- filas/totales/resumen por estado ya vienen calculados por ServicioReportes
     * (la misma cuenta que usa la pantalla de /prestamos, nunca una copia). */
    public byte[] generarInformePrestamosPdf(Connection conexion, List<FilaInformePrestamo> filas,
            TotalesPrestamos totales, List<ResumenEstadoPrestamo> resumenPorEstado, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_PRESTAMOS", totales.getCantidad());
        parametros.put("MONTO_TOTAL", totales.getMontoTotal());
        parametros.put("SALDO_TOTAL", totales.getSaldoTotal());
        parametros.put("RESUMEN_POR_ESTADO_TEXTO", construirTextoResumenPorEstadoPrestamos(resumenPorEstado));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_prestamos.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_PRESTAMOS", 0,
                textoFiltros + " · " + filas.size() + " préstamo(s).");
        return pdf;
    }

    /** Auditoría del CSV de Préstamos -- mismo criterio que Clientes. */
    public void auditarExportacionCsvPrestamos(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_PRESTAMOS", 0,
                textoFiltros + " · " + cantidad + " préstamo(s).");
    }

    // ======================================================================
    // B2 (Bloque 7/E10, pedido de Joel): Informe de Solicitudes -- mismo molde que Clientes/
    // Préstamos de arriba.
    // ======================================================================

    /** Texto legible de "Filtros aplicados:" para /reportes/solicitudes -- desde/hasta siempre
     * tienen valor acá (el controlador ya les pone un rango por defecto, mismo criterio que el
     * resto de /reportes), así que nunca hace falta un "Todas las fechas". */
    public String construirTextoFiltrosSolicitudes(FiltroSolicitudesReporte filtro, String etiquetaEstado,
            String etiquetaOficial) {
        List<String> partes = new ArrayList<>();
        partes.add("Solicitud del " + FormateadorFechas.comoDdMmAaaa(filtro.getDesde()) + " al "
                + FormateadorFechas.comoDdMmAaaa(filtro.getHasta()));
        partes.add(etiquetaEstado);
        partes.add(etiquetaOficial);
        return String.join(" · ", partes);
    }

    /** "Estado: cantidad — monto X" por línea, para el resumen por estado del PDF. */
    public String construirTextoResumenPorEstadoSolicitudes(List<ResumenEstadoSolicitud> resumenPorEstado) {
        if (resumenPorEstado.isEmpty()) {
            return "(sin datos)";
        }
        List<String> lineas = new ArrayList<>();
        for (ResumenEstadoSolicitud fila : resumenPorEstado) {
            lineas.add(fila.getEstado() + ": " + fila.getCantidad() + " — monto " + fila.getMonto());
        }
        return String.join("\n", lineas);
    }

    public byte[] generarInformeSolicitudesPdf(Connection conexion, List<FilaInformeSolicitud> filas,
            TotalesSolicitudesReporte totales, List<ResumenEstadoSolicitud> resumenPorEstado, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_SOLICITUDES", totales.getCantidad());
        parametros.put("MONTO_TOTAL", totales.getMontoTotal());
        parametros.put("RESUMEN_POR_ESTADO_TEXTO", construirTextoResumenPorEstadoSolicitudes(resumenPorEstado));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_solicitudes.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_SOLICITUDES", 0,
                textoFiltros + " · " + filas.size() + " solicitud(es).");
        return pdf;
    }

    public void auditarExportacionCsvSolicitudes(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_SOLICITUDES", 0,
                textoFiltros + " · " + cantidad + " solicitud(es).");
    }

    // ======================================================================
    // B3 (Bloque 7/E10, pedido de Joel): Informe de Aprobaciones y rechazos -- mismo molde que
    // Solicitudes de arriba.
    // ======================================================================

    /** Texto legible de "Filtros aplicados:" para /reportes/aprobaciones -- solo rango de fecha
     * de decisión (desde/hasta siempre tienen valor, mismo criterio que el resto de /reportes). */
    public String construirTextoFiltrosAprobaciones(FiltroAprobacionesReporte filtro) {
        return "Decisión del " + FormateadorFechas.comoDdMmAaaa(filtro.getDesde()) + " al "
                + FormateadorFechas.comoDdMmAaaa(filtro.getHasta());
    }

    public byte[] generarInformeAprobacionesPdf(Connection conexion, List<FilaInformeAprobacion> filas,
            TotalesAprobaciones totales, String textoFiltros, long idUsuarioEmisor, String nombreUsuarioEmisor)
            throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_APROBADAS", totales.getCantidadAprobadas());
        parametros.put("MONTO_APROBADAS", totales.getMontoAprobadas());
        parametros.put("CANTIDAD_RECHAZADAS", totales.getCantidadRechazadas());
        parametros.put("MONTO_RECHAZADAS", totales.getMontoRechazadas());
        // RF07 (pedido de Joel, 10/10/2026): tercera categoría del informe.
        parametros.put("CANTIDAD_CANCELADAS", totales.getCantidadCanceladas());
        parametros.put("MONTO_CANCELADAS", totales.getMontoCanceladas());
        parametros.put("TIEMPO_PROMEDIO_RESPUESTA", totales.getTiempoPromedioRespuesta());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_aprobaciones.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_APROBACIONES", 0,
                textoFiltros + " · " + filas.size() + " solicitud(es) decidida(s).");
        return pdf;
    }

    public void auditarExportacionCsvAprobaciones(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_APROBACIONES", 0,
                textoFiltros + " · " + cantidad + " solicitud(es) decidida(s).");
    }

    // ======================================================================
    // B4 (Bloque 7/E10, pedido de Joel): Informes de Cobros y Desembolsos -- mismo molde que
    // Solicitudes/Aprobaciones de arriba.
    // ======================================================================

    public String construirTextoFiltrosCobros(FiltroCobrosReporte filtro) {
        return "Cobro del " + FormateadorFechas.comoDdMmAaaa(filtro.getDesde()) + " al "
                + FormateadorFechas.comoDdMmAaaa(filtro.getHasta());
    }

    public byte[] generarInformeCobrosPdf(Connection conexion, List<FilaInformeCobro> filas, TotalesCobros totales,
            String textoFiltros, long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_COBROS", totales.getCantidad());
        parametros.put("CANTIDAD_REVERTIDOS", totales.getCantidadRevertidos());
        parametros.put("CAPITAL_TOTAL", totales.getCapitalTotal());
        parametros.put("INTERES_TOTAL", totales.getInteresTotal());
        parametros.put("MORATORIO_TOTAL", totales.getMoratorioTotal());
        parametros.put("PUNITORIO_TOTAL", totales.getPunitorioTotal());
        parametros.put("IVA_TOTAL", totales.getIvaTotal());
        parametros.put("TOTAL_GENERAL", totales.getTotal());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_cobros.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_COBROS", 0,
                textoFiltros + " · " + filas.size() + " cobro(s).");
        return pdf;
    }

    public void auditarExportacionCsvCobros(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_COBROS", 0,
                textoFiltros + " · " + cantidad + " cobro(s).");
    }

    public String construirTextoFiltrosDesembolsos(FiltroDesembolsosReporte filtro) {
        return "Desembolso del " + FormateadorFechas.comoDdMmAaaa(filtro.getDesde()) + " al "
                + FormateadorFechas.comoDdMmAaaa(filtro.getHasta());
    }

    public byte[] generarInformeDesembolsosPdf(Connection conexion, List<FilaInformeDesembolso> filas,
            TotalesDesembolsos totales, String textoFiltros, long idUsuarioEmisor, String nombreUsuarioEmisor)
            throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_DESEMBOLSOS", totales.getCantidad());
        parametros.put("MONTO_TOTAL", totales.getMontoTotal());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_desembolsos.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_DESEMBOLSOS", 0,
                textoFiltros + " · " + filas.size() + " desembolso(s).");
        return pdf;
    }

    public void auditarExportacionCsvDesembolsos(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_DESEMBOLSOS", 0,
                textoFiltros + " · " + cantidad + " desembolso(s).");
    }

    // ======================================================================
    // B5 (Bloque 7/E10, pedido de Joel): Informe de Caja -- mismo molde que los anteriores.
    // ======================================================================

    public String construirTextoFiltrosCaja(py.com.capitalcredito.sgcpc.dto.FiltroCajaReporte filtro) {
        return "Cierre del " + FormateadorFechas.comoDdMmAaaa(filtro.getDesde()) + " al "
                + FormateadorFechas.comoDdMmAaaa(filtro.getHasta());
    }

    public byte[] generarInformeCajaPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeCaja> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesCaja totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_SESIONES", totales.getCantidadSesiones());
        parametros.put("SUMA_SOBRANTES", totales.getSumaSobrantes());
        parametros.put("SUMA_FALTANTES", totales.getSumaFaltantes());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_caja.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CAJA", 0,
                textoFiltros + " · " + filas.size() + " sesión(es).");
        return pdf;
    }

    public void auditarExportacionCsvCaja(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CAJA", 0,
                textoFiltros + " · " + cantidad + " sesión(es).");
    }

    // ======================================================================
    // Bloque C, tanda 1 (pedido de Joel): Cartera y riesgo -- Morosidad, Cartera, Incobrables,
    // Vencimientos, Proyección de fondos. Mismo molde que el Bloque B, agregado a 5 reportes que
    // YA existían en pantalla (las filas/totales las sigue armando ServicioReportes/ServicioMora,
    // la misma cuenta que la pantalla, nunca una copia para el PDF/CSV).
    // ======================================================================

    /** Morosidad (§13): es una foto del momento (sin filtro de fecha, corrección de Joel) -- el
     * texto de "Filtros aplicados" deja eso explícito, con la fecha de emisión. */
    public String construirTextoFiltrosMorosidad(java.time.LocalDate fecha) {
        return "Situación de morosidad al " + FormateadorFechas.comoDdMmAaaa(fecha) + " (foto del momento)";
    }

    /** "Tramo: cantidad — capital vencido X — saldo Y" por línea, mismo criterio de texto plano
     * que construirTextoResumenPorEstadoPrestamos (arriba). */
    public String construirTextoSubtotalesPorTramoMorosidad(
            List<py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad> subtotales) {
        if (subtotales.isEmpty()) {
            return "(sin datos)";
        }
        List<String> lineas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad fila : subtotales) {
            lineas.add(fila.getTramo() + ": " + fila.getCantidad() + " — capital vencido " + fila.getCapitalVencido()
                    + " — saldo " + fila.getSaldo());
        }
        return String.join("\n", lineas);
    }

    public byte[] generarInformeMorosidadPdf(Connection conexion, List<FilaMora> filas,
            List<py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad> subtotalesPorTramo,
            py.com.capitalcredito.sgcpc.dto.SubtotalTramoMorosidad totalGeneral, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totalGeneral.getCantidad());
        parametros.put("CAPITAL_VENCIDO_TOTAL", totalGeneral.getCapitalVencido());
        parametros.put("SALDO_TOTAL", totalGeneral.getSaldo());
        parametros.put("SUBTOTALES_POR_TRAMO_TEXTO", construirTextoSubtotalesPorTramoMorosidad(subtotalesPorTramo));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_morosidad.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_MOROSIDAD", 0,
                textoFiltros + " · " + filas.size() + " préstamo(s).");
        return pdf;
    }

    public void auditarExportacionCsvMorosidad(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_MOROSIDAD", 0,
                textoFiltros + " · " + cantidad + " préstamo(s).");
    }

    /** Cartera (§13): como Morosidad, es una foto del momento (ACTIVO/EN_MORA de hoy). */
    public String construirTextoFiltrosCartera(java.time.LocalDate fecha) {
        return "Cartera (préstamos activos y en mora) al " + FormateadorFechas.comoDdMmAaaa(fecha);
    }

    /** "Estado: cantidad — saldo X" por línea, mismo criterio que construirTextoResumenPorEstadoPrestamos. */
    public String construirTextoResumenPorEstadoCartera(
            List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera> resumenPorEstado) {
        if (resumenPorEstado.isEmpty()) {
            return "(sin datos)";
        }
        List<String> lineas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera fila : resumenPorEstado) {
            lineas.add(fila.getEstado() + ": " + fila.getCantidad() + " — saldo " + fila.getSaldo());
        }
        return String.join("\n", lineas);
    }

    public byte[] generarInformeCarteraPdf(Connection conexion, List<py.com.capitalcredito.sgcpc.dto.FilaCartera> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesCartera totales,
            List<py.com.capitalcredito.sgcpc.dto.ResumenEstadoCartera> resumenPorEstado, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_PRESTAMOS", totales.getCantidadPrestamos());
        parametros.put("SALDO_TOTAL", totales.getCapitalPendiente());
        parametros.put("INTERES_FUTURO_TOTAL", totales.getInteresFuturo());
        parametros.put("RESUMEN_POR_ESTADO_TEXTO", construirTextoResumenPorEstadoCartera(resumenPorEstado));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_cartera.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CARTERA", 0,
                textoFiltros + " · " + filas.size() + " préstamo(s).");
        return pdf;
    }

    public void auditarExportacionCsvCartera(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CARTERA", 0,
                textoFiltros + " · " + cantidad + " préstamo(s).");
    }

    /** Incobrables (§13): préstamos EN_MORA con más de credito.dias_incobrable días de atraso,
     * foto del momento como Morosidad/Cartera. */
    public String construirTextoFiltrosIncobrables(java.time.LocalDate fecha) {
        return "Incobrables al " + FormateadorFechas.comoDdMmAaaa(fecha) + " (foto del momento)";
    }

    public byte[] generarInformeIncobrablesPdf(Connection conexion, List<FilaMora> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesIncobrables totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidad());
        parametros.put("SALDO_TOTAL", totales.getSaldo());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_incobrables.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_INCOBRABLES", 0,
                textoFiltros + " · " + filas.size() + " préstamo(s).");
        return pdf;
    }

    public void auditarExportacionCsvIncobrables(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_INCOBRABLES", 0,
                textoFiltros + " · " + cantidad + " préstamo(s).");
    }

    /** Vencimientos (§13): único informe de esta tanda con filtro desde/hasta real (los otros
     * cuatro son foto del momento). */
    public String construirTextoFiltrosVencimientos(java.time.LocalDate desde, java.time.LocalDate hasta) {
        return "Vencimiento del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
    }

    public byte[] generarInformeVencimientosPdf(Connection conexion, List<py.com.capitalcredito.sgcpc.dto.FilaVencimiento> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesVencimientos totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_CUOTAS", totales.getCantidadCuotas());
        parametros.put("CAPITAL_TOTAL", totales.getCapitalTotal());
        parametros.put("INTERES_TOTAL", totales.getInteresTotal());
        parametros.put("IVA_TOTAL", totales.getIvaTotal());
        parametros.put("MONTO_TOTAL", totales.getMontoTotal());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_vencimientos.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_VENCIMIENTOS", 0,
                textoFiltros + " · " + filas.size() + " cuota(s).");
        return pdf;
    }

    public void auditarExportacionCsvVencimientos(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_VENCIMIENTOS", 0,
                textoFiltros + " · " + cantidad + " cuota(s).");
    }

    /** Proyección de fondos (§13, D-128): foto del momento, como Morosidad/Cartera/Incobrables. */
    public String construirTextoFiltrosProyeccionFondos(java.time.LocalDate fecha) {
        return "Proyección de fondos al " + FormateadorFechas.comoDdMmAaaa(fecha) + " (foto del momento)";
    }

    /** Pura (sin base): busca la fila de un horizonte puntual -- extraída para poder probarla, y
     * para que generarInformeProyeccionFondosPdf no dependa del orden de la lista. */
    static py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos buscarHorizonte(
            List<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos> filas, int horizonteDias) {
        for (py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos fila : filas) {
            if (fila.getHorizonteDias() == horizonteDias) {
                return fila;
            }
        }
        return null;
    }

    /** Bloque C, tanda 1 (pedido de Joel): "30/60/90 días con subtotal por horizonte y total" --
     * cada fila YA es un subtotal acumulado hasta ese horizonte (D-128: ingresos de 60 días
     * incluyen los de 30, los de 90 incluyen los de 60), así que no hay un "total general" que
     * sumar sin contar dos veces el mismo cobro -- el horizonte de 90 días, al ser superconjunto
     * de los otros dos, ES el total completo. El resumen del PDF lo repite como "Total a 90 días"
     * para que quede explícito, en vez de forzar un total que Joel no pidió y que sería incorrecto. */
    public byte[] generarInformeProyeccionFondosPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos> filas, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        py.com.capitalcredito.sgcpc.dto.FilaProyeccionFondos totalA90Dias = buscarHorizonte(filas, 90);
        parametros.put("INGRESOS_A_90_DIAS", totalA90Dias == null ? "—" : totalA90Dias.getIngresosEsperados());
        parametros.put("EGRESOS_A_90_DIAS", totalA90Dias == null ? "—" : totalA90Dias.getEgresosComprometidos());
        parametros.put("FLUJO_NETO_A_90_DIAS", totalA90Dias == null ? "—" : totalA90Dias.getFlujoNeto());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_proyeccion_fondos.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_PROYECCION_FONDOS", 0,
                textoFiltros + " · " + filas.size() + " horizonte(s).");
        return pdf;
    }

    public void auditarExportacionCsvProyeccionFondos(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_PROYECCION_FONDOS", 0,
                textoFiltros + " · " + cantidad + " horizonte(s).");
    }

    // ======================================================================
    // Bloque C, tanda 2 (pedido de Joel): Fiscal y financiero -- IVA del mes, Facturas anuladas,
    // Resumen financiero, Resumen mensual, Cierres documentales.
    // ======================================================================

    public String construirTextoFiltrosIva(java.time.YearMonth mes) {
        return "IVA de " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoMesYAnio(mes);
    }

    public byte[] generarInformeIvaPdf(Connection conexion, List<py.com.capitalcredito.sgcpc.dto.FilaIva> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesIva totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_FACTURAS", totales.getCantidadFacturas());
        parametros.put("EXENTA_TOTAL", totales.getExenta());
        parametros.put("GRAVADA_10_TOTAL", totales.getGravada10());
        parametros.put("IVA_10_TOTAL", totales.getIva10());
        parametros.put("TOTAL_GENERAL", totales.getTotal());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_iva.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_IVA", 0,
                textoFiltros + " · " + filas.size() + " factura(s).");
        return pdf;
    }

    public void auditarExportacionCsvIva(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_IVA", 0,
                textoFiltros + " · " + cantidad + " factura(s).");
    }

    public String construirTextoFiltrosFacturasAnuladas(java.time.YearMonth mes) {
        return "Facturas anuladas de " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoMesYAnio(mes);
    }

    public byte[] generarInformeFacturasAnuladasPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaFacturaAnulada> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesFacturasAnuladas totales, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidad());
        parametros.put("MONTO_TOTAL", totales.getMontoTotal());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_facturas_anuladas.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_FACTURAS_ANULADAS", 0,
                textoFiltros + " · " + filas.size() + " factura(s).");
        return pdf;
    }

    public void auditarExportacionCsvFacturasAnuladas(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_FACTURAS_ANULADAS", 0,
                textoFiltros + " · " + cantidad + " factura(s).");
    }

    public String construirTextoFiltrosResumenMensual(java.time.YearMonth mes) {
        return "Resumen de " + py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas.comoMesYAnio(mes);
    }

    /** Bloque C, tanda 2 (pedido de Joel): "el PDF con el mismo contenido que la pantalla" -- sin
     * lista de filas (un solo resumen), mismo patrón de un solo registro que usan los comprobantes
     * de un solo documento (constancia_devolucion.jrxml). */
    public byte[] generarInformeResumenMensualPdf(Connection conexion, py.com.capitalcredito.sgcpc.dto.ResumenMensual resumen,
            String textoFiltros, long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("PRESTAMOS_OTORGADOS_CANTIDAD", resumen.getPrestamosOtorgadosCantidad());
        parametros.put("PRESTAMOS_OTORGADOS_MONTO", resumen.getPrestamosOtorgadosMonto());
        parametros.put("MOROSIDAD_PORCENTAJE_CANTIDAD", resumen.getMorosidadPorcentajeCantidad());
        parametros.put("MOROSIDAD_PORCENTAJE_MONTO", resumen.getMorosidadPorcentajeMonto());
        parametros.put("SALDO_PRESTAMOS_EN_MORA_PORCENTAJE", resumen.getSaldoPrestamosEnMoraPorcentaje());
        parametros.put("COBRADO_CAPITAL", resumen.getCobradoCapital());
        parametros.put("COBRADO_INTERES", resumen.getCobradoInteres());
        parametros.put("COBRADO_MORATORIO", resumen.getCobradoMoratorio());
        parametros.put("COBRADO_PUNITORIO", resumen.getCobradoPunitorio());
        parametros.put("COBRADO_IVA", resumen.getCobradoIva());
        parametros.put("COBRADO_TOTAL", resumen.getCobradoTotal());
        parametros.put("GANANCIA_DEL_MES", resumen.getGananciaDelMes());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_resumen_mensual.jasper", parametros,
                new JREmptyDataSource(1));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_RESUMEN_MENSUAL", 0, textoFiltros);
        return pdf;
    }

    /** 13/10/2026 (hallazgo de Joel): faltaba, a diferencia de los demás informes -- la
     * exportación CSV del resumen mensual no se auditaba. */
    public void auditarExportacionCsvResumenMensual(Connection conexion, long idUsuarioEmisor, String textoFiltros)
            throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_RESUMEN_MENSUAL", 0, textoFiltros);
    }

    /** "Tramo: cantidad — monto X" por línea, mismo criterio que construirTextoResumenPorEstadoPrestamos
     * -- reusado para demanda por tramo de monto/plazo de Resumen financiero. */
    public String construirTextoTramos(List<py.com.capitalcredito.sgcpc.dto.SubtotalTramo> tramos) {
        if (tramos == null || tramos.isEmpty()) {
            return "Sin solicitudes en el período.";
        }
        List<String> lineas = new ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.SubtotalTramo tramo : tramos) {
            lineas.add(tramo.getTramo() + ": " + tramo.getCantidad() + " — " + tramo.getMonto());
        }
        return String.join("\n", lineas);
    }

    public String construirTextoFiltrosResumenFinanciero(java.time.LocalDate desde, java.time.LocalDate hasta) {
        return "Período del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
    }

    /** Bloque C, tanda 2 (pedido de Joel): "el PDF con el mismo contenido que la pantalla" -- sin
     * lista de filas (un solo resumen), mismo patrón de un solo registro. Las dos listas (demanda
     * por tramo de monto/plazo) se mandan como texto plano, mismo criterio que
     * construirTextoResumenPorEstadoPrestamos/construirTextoSubtotalesPorTramoMorosidad. */
    public byte[] generarInformeResumenFinancieroPdf(Connection conexion,
            py.com.capitalcredito.sgcpc.dto.ResumenFinanciero resumen, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("OTORGADOS_CANTIDAD", resumen.getOtorgadosCantidad());
        parametros.put("OTORGADOS_MONTO", resumen.getOtorgadosMonto());
        parametros.put("EN_TRAMITE_CANTIDAD", resumen.getEnTramiteCantidad());
        parametros.put("EN_TRAMITE_MONTO", resumen.getEnTramiteMonto());
        parametros.put("APROBADOS_SIN_DESEMBOLSAR_CANTIDAD", resumen.getAprobadosSinDesembolsarCantidad());
        parametros.put("APROBADOS_SIN_DESEMBOLSAR_MONTO", resumen.getAprobadosSinDesembolsarMonto());
        parametros.put("MOROSIDAD_PORCENTAJE_CANTIDAD", resumen.getMorosidadPorcentajeCantidad());
        parametros.put("MOROSIDAD_PORCENTAJE_MONTO", resumen.getMorosidadPorcentajeMonto());
        parametros.put("SALDO_PRESTAMOS_EN_MORA_PORCENTAJE", resumen.getSaldoPrestamosEnMoraPorcentaje());
        parametros.put("COBRADO_CAPITAL", resumen.getCobradoCapital());
        parametros.put("COBRADO_INTERES", resumen.getCobradoInteres());
        parametros.put("COBRADO_MORATORIO", resumen.getCobradoMoratorio());
        parametros.put("COBRADO_PUNITORIO", resumen.getCobradoPunitorio());
        parametros.put("COBRADO_IVA", resumen.getCobradoIva());
        parametros.put("COBRADO_TOTAL", resumen.getCobradoTotal());
        parametros.put("GANANCIA_DEL_PERIODO", resumen.getGananciaDelPeriodo());
        parametros.put("CARTERA_CAPITAL_PENDIENTE", resumen.getCarteraCapitalPendiente());
        parametros.put("CARTERA_INTERES_FUTURO", resumen.getCarteraInteresFuturo());
        parametros.put("DEMANDA_POR_MONTO_TEXTO", construirTextoTramos(resumen.getDemandaPorMonto()));
        parametros.put("DEMANDA_POR_PLAZO_TEXTO", construirTextoTramos(resumen.getDemandaPorPlazo()));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_resumen_financiero.jasper", parametros,
                new JREmptyDataSource(1));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_RESUMEN_FINANCIERO", 0, textoFiltros);
        return pdf;
    }

    /** 13/10/2026 (hallazgo de Joel): faltaba, a diferencia de los demás informes -- la
     * exportación CSV del resumen financiero no se auditaba. */
    public void auditarExportacionCsvResumenFinanciero(Connection conexion, long idUsuarioEmisor, String textoFiltros)
            throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_RESUMEN_FINANCIERO", 0, textoFiltros);
    }

    /** E-12 (pedido de Joel): ahora filtra por fecha de CANCELACIÓN (antes era por fecha de
     * devolución del pagaré, que dejaba afuera a los préstamos todavía sin devolver -- ver
     * ServicioReportes.listarCierresDocumentalesPorRango); el texto incluye además el filtro de
     * estado del pagaré elegido ("Todos" cuando no se restringió, nunca se omite en silencio). */
    public String construirTextoFiltrosCierresDocumentales(java.time.LocalDate desde, java.time.LocalDate hasta,
            py.com.capitalcredito.sgcpc.servicios.ServicioReportes.FiltroEstadoPagare filtroEstadoPagare) {
        String etiquetaPagare;
        switch (filtroEstadoPagare) {
            case DEVUELTOS:
                etiquetaPagare = "Pagaré devuelto";
                break;
            case PENDIENTES:
                etiquetaPagare = "Pagaré pendiente de devolución";
                break;
            default:
                etiquetaPagare = "Todos";
        }
        return "Cancelación del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta)
                + " · " + etiquetaPagare;
    }

    public byte[] generarInformeCierresDocumentalesPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaCierreDocumentalReporte> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesCierresDocumentales totales, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_CANCELADOS", totales.getCantidadCancelados());
        parametros.put("CANTIDAD_DEVUELTOS", totales.getCantidadDevueltos());
        parametros.put("CANTIDAD_PENDIENTES", totales.getCantidadPendientes());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_cierres_documentales.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CIERRES_DOCUMENTALES", 0,
                textoFiltros + " · " + filas.size() + " cierre(s).");
        return pdf;
    }

    public void auditarExportacionCsvCierresDocumentales(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CIERRES_DOCUMENTALES", 0,
                textoFiltros + " · " + cantidad + " cierre(s).");
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): Gestiones de cobranza -- mismo molde que el resto.
    // ======================================================================

    public String construirTextoFiltrosGestiones(LocalDate desde, LocalDate hasta, String tipoEtiqueta,
            boolean soloIncumplidas) {
        String texto = "Gestión del " + FormateadorFechas.comoDdMmAaaa(desde) + " al "
                + FormateadorFechas.comoDdMmAaaa(hasta) + " · " + (tipoEtiqueta == null ? "Todos los tipos" : tipoEtiqueta);
        return soloIncumplidas ? texto + " · Solo promesas incumplidas" : texto;
    }

    public byte[] generarInformeGestionesPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaGestionReporte> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesGestiones totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidadTotal());
        parametros.put("CANTIDAD_PROMESAS_HECHAS", totales.getCantidadPromesasHechas());
        parametros.put("CANTIDAD_CUMPLIDAS", totales.getCantidadCumplidas());
        parametros.put("CANTIDAD_VENCIDAS_SIN_CUMPLIR", totales.getCantidadVencidasSinCumplir());
        parametros.put("CANTIDAD_PENDIENTES", totales.getCantidadPendientes());
        parametros.put("POR_TIPO_TEXTO", construirTextoPorTipo(totales.getCantidadPorTipo()));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_gestiones.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_GESTIONES", 0,
                textoFiltros + " · " + filas.size() + " gestión(es).");
        return pdf;
    }

    /** "Tipo: cantidad" por línea -- mismo criterio que construirTextoResumenPorEstadoPrestamos. */
    public String construirTextoPorTipo(Map<String, Integer> cantidadPorTipo) {
        if (cantidadPorTipo == null || cantidadPorTipo.isEmpty()) {
            return "(sin datos)";
        }
        StringBuilder texto = new StringBuilder();
        for (Map.Entry<String, Integer> entrada : cantidadPorTipo.entrySet()) {
            if (texto.length() > 0) {
                texto.append("\n");
            }
            texto.append(entrada.getKey()).append(": ").append(entrada.getValue());
        }
        return texto.toString();
    }

    public void auditarExportacionCsvGestiones(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_GESTIONES", 0,
                textoFiltros + " · " + cantidad + " gestión(es).");
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): Reversiones -- mismo molde que el resto.
    // ======================================================================

    public String construirTextoFiltrosReversiones(LocalDate desde, LocalDate hasta) {
        return "Reversión del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
    }

    /** C-3b (pedido de Joel): resumen desglosado por estado -- un solo texto, igual en pantalla,
     * PDF y CSV, construido acá una sola vez para que los tres no puedan decir cosas distintas. */
    public String construirResumenReversiones(py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales) {
        return totales.getCantidad() + " revers" + (totales.getCantidad() == 1 ? "ión" : "iones")
                + " (" + pluralFemenino(totales.getCantidadAutorizadas(), "autorizada")
                + ", " + pluralFemenino(totales.getCantidadRechazadas(), "rechazada")
                + ", " + pluralFemenino(totales.getCantidadSolicitadas(), "solicitada") + ")"
                + " — Monto revertido (solo autorizadas): " + totales.getMontoTotalRevertido();
    }

    private static String pluralFemenino(int cantidad, String singular) {
        return cantidad + " " + singular + (cantidad == 1 ? "" : "s");
    }

    public byte[] generarInformeReversionesPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaReversionReporte> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesReversiones totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidad());
        parametros.put("RESUMEN_REVERSIONES", construirResumenReversiones(totales));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_reversiones.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_REVERSIONES", 0,
                textoFiltros + " · " + filas.size() + " reversión(es).");
        return pdf;
    }

    public void auditarExportacionCsvReversiones(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_REVERSIONES", 0,
                textoFiltros + " · " + cantidad + " reversión(es).");
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): Auditoría -- mismo molde que el resto. B205
    // (15/11/2026): el tope de filas (ServicioReportes.LIMITE_AUDITORIA_PDF) se aplica antes de
    // llegar acá y ahora es SOLO del PDF -- la pantalla pagina y el CSV trae todo.
    // ======================================================================

    public String construirTextoFiltrosAuditoria(LocalDate desde, LocalDate hasta, String nombreUsuario,
            String tipoEntidad, String operacion) {
        StringBuilder texto = new StringBuilder(
                "Auditoría del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta));
        // RF40/F1 (pedido de Joel, 13/10/2026): el filtro de usuario ya se aplicaba a los datos
        // (ControladorReportesServlet ya leía "idUsuario"), pero no se mencionaba en el texto de
        // filtros del PDF/CSV.
        if (nombreUsuario != null) {
            texto.append(" · Usuario: ").append(nombreUsuario);
        }
        if (tipoEntidad != null) {
            texto.append(" · Entidad: ").append(tipoEntidad);
        }
        if (operacion != null) {
            texto.append(" · Operación contiene: \"").append(operacion).append("\"");
        }
        return texto.toString();
    }

    /**
     * El aviso del <b>informe PDF</b> de Auditoría cuando el tope le cortó filas, o null si entró
     * todo.
     *
     * <p><b>B205 (15/11/2026)</b>, dos correcciones al texto anterior
     * (<i>"…registros; acotá el filtro."</i>):</p>
     * <ul>
     *   <li><b>Decía "acotá"</b>, el <b>único</b> imperativo en voseo de todo {@code src/main} —
     *       el resto del sistema le habla de usted (<i>"Revise"</i>, <i>"Elija"</i>,
     *       <i>"Busque"</i>, <i>"Ingrese"</i>), como exige {@code REGLAS_DESARROLLO.md} §12. Era
     *       lo que quedó afuera de la pasada de voseo → usted del 10/10/2026 (RF04 §9).</li>
     *   <li><b>Y mandaba a hacer algo que podía ser imposible</b>: acotar un filtro que ya puede
     *       estar en un solo día (es el rango por defecto de la pantalla). Ahora dice <b>dónde
     *       está el resto</b> — el CSV, que desde B205 no tiene tope.</li>
     * </ul>
     *
     * <p>Ya no se usa en la pantalla: desde B205 la pantalla pagina y no corta nada.</p>
     */
    public String construirAvisoLimiteAuditoria(int cantidadMostrada, int cantidadTotal, int limite) {
        if (cantidadTotal <= limite) {
            return null;
        }
        return "Este informe muestra los primeros " + cantidadMostrada + " de " + cantidadTotal
                + " registros. Para verlos todos, use «Excel/CSV», que los trae completos, "
                + "o acote el filtro.";
    }

    /**
     * B209 (15/11/2026, ítem 7 del informe de UX de Informes): los textos del ⓘ de los botones
     * <i>"Informe PDF"</i> y <i>"Excel/CSV"</i> de los reportes, y el aviso de cuando no hay nada
     * que exportar.
     *
     * <p><b>El defecto</b>: los veinte reportes ofrecían los dos botones <b>sin decir qué va a
     * pasar</b> — ninguno usaba el mecanismo del ⓘ ({@code data-glosario-titulo}/
     * {@code data-glosario-texto}, SweetAlert2 desde {@code plantilla/pie.jsp}), que es justo lo
     * que se cerró en Formalización con <b>B184</b> y en la devolución del pagaré con <b>B112</b>.
     * Y además seguían habilitados con <b>cero filas</b>: se podía exportar un PDF vacío sin que
     * nada lo advirtiera.</p>
     *
     * <p>Los textos viven acá, con un solo dueño, porque si no serían <b>cuarenta copias</b> (dos
     * botones × veinte reportes) y la primera corrección las dejaría divergiendo — la razón de ser
     * de B167 con la paginación. Lo que dicen es solo lo que es cierto en los veinte: que respetan
     * los filtros de arriba. El aviso de que la <i>búsqueda dentro de la tabla</i> no afecta la
     * exportación es otra cosa, ya existe en los cinco reportes que tienen ese buscador (V15) y no
     * se toca.</p>
     */
    public static final String AYUDA_EXPORTAR_TITULO = "Informe PDF y Excel/CSV";

    /**
     * B209: ver {@link #AYUDA_EXPORTAR_TITULO}. <b>Un solo texto para el par</b>, porque el ⓘ es
     * uno para los dos botones: explica la diferencia entre ellos, que es justo lo que había que
     * decir. Dice <b>solo lo que es cierto en los veinte reportes</b> — que los dos respetan los
     * filtros de arriba; el aviso de que la <i>búsqueda dentro de la tabla</i> no afecta la
     * exportación es otra cosa, ya existe en los cinco reportes que tienen ese buscador (V15) y no
     * se toca.
     */
    public static final String AYUDA_EXPORTAR_TEXTO =
            "«Informe PDF» abre un PDF listo para imprimir, en otra pestaña. «Excel/CSV» descarga "
                    + "un archivo para abrir en Excel. Los dos usan los mismos filtros que están "
                    + "aplicados arriba, y ninguno de los dos se guarda en el sistema.";

    /**
     * B210 (15/11/2026, ítems 2+3 del informe de UX de Informes): el título de un reporte con su
     * cantidad, <i>"Vencimientos (8 cuotas)"</i>.
     *
     * <p><b>El defecto</b>: ninguno de los reportes decía cuántas filas hay. Los títulos eran
     * <i>"Vencimientos"</i>, <i>"Cartera"</i>, <i>"Cobros"</i> a secas y la cantidad había que ir a
     * buscarla al <b>pie de la tabla</b>, después de bajar 190 o 292 filas — cuando en las otras
     * siete pantallas Joel lo pidió arriba y en todas quedó así.</p>
     *
     * <p>Existe para que el formato tenga <b>un solo dueño</b> y no dieciséis: el separador de
     * miles ({@link FormateadorNumeros#comoCantidad}, la segunda vuelta de B205), el paréntesis y
     * el singular/plural. Dieciséis copias de ese ternario son dieciséis lugares donde una se
     * escribe mal.</p>
     *
     * <p>El total es el del listado <b>completo</b>, no el de la página: con el de la página diría
     * "20" en todas, la trampa que B191 ya había encontrado en Caja. Sin total (la pantalla todavía
     * no consultó), devuelve el título pelado en vez de un "(0)" que mentiría.</p>
     */
    public static String tituloConCantidad(String titulo, Integer total, String singular, String plural) {
        if (total == null) {
            return titulo;
        }
        return titulo + " (" + FormateadorNumeros.comoCantidad(total.intValue()) + " "
                + (total.intValue() == 1 ? singular : plural) + ")";
    }

    /** B209: por qué los botones están en gris. Se dice, en vez de dejar que alguien los toque y
     * reciba un PDF en blanco. */
    public static final String AVISO_SIN_FILAS_PARA_EXPORTAR =
            "No hay filas para exportar con este filtro.";

    /** B209: la clase que apaga un botón de exportar cuando no hay filas. Bootstrap 4 necesita
     * {@code disabled} en el {@code <a>} para pintarlo en gris, y además hay que sacarlo del
     * tabulador y marcarlo para los lectores de pantalla: un {@code <a href>} en gris sigue siendo
     * un enlace navegable si solo se le cambia el color. */
    public static String claseBotonExportar(boolean hayFilas) {
        return hayFilas ? "" : " disabled";
    }

    /** B209: los atributos que de verdad lo dejan inerte, no solo gris. */
    public static String atributosBotonExportar(boolean hayFilas) {
        return hayFilas ? "" : " tabindex=\"-1\" aria-disabled=\"true\"";
    }

    /** E-15 (pedido de Joel, C-3b): título de la sección de totales -- deja explícito que están
     * calculados sobre TODO el período filtrado, no sobre las filas que se ven en el detalle
     * (que en el PDF pueden estar cortadas por LIMITE_AUDITORIA_PDF y en la pantalla son las de
     * UNA página desde B205). */
    public String construirTituloTotalesAuditoria(int cantidadTotalPeriodo) {
        return "Totales del período (" + cantidadTotalPeriodo + " registro" + (cantidadTotalPeriodo == 1 ? "" : "s") + ")";
    }

    public byte[] generarInformeAuditoriaPdf(Connection conexion, List<FilaAuditoria> filas,
            Map<String, Integer> totalesPorOperacion, int cantidadTotalPeriodo, String textoFiltros,
            String avisoLimite, long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", filas.size());
        parametros.put("AVISO_LIMITE", avisoLimite == null ? "" : avisoLimite);
        parametros.put("TITULO_TOTALES", construirTituloTotalesAuditoria(cantidadTotalPeriodo));
        parametros.put("POR_OPERACION_TEXTO", construirTextoPorTipo(totalesPorOperacion));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_auditoria.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_AUDITORIA", 0,
                textoFiltros + " · " + filas.size() + " registro(s).");
        return pdf;
    }

    public void auditarExportacionCsvAuditoria(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_AUDITORIA", 0,
                textoFiltros + " · " + cantidad + " registro(s).");
    }

    // ======================================================================
    // Bloque C, tanda 3 (pedido de Joel): Clientes nuevos -- mismo molde que el resto.
    // ======================================================================

    public String construirTextoFiltrosClientesNuevos(LocalDate desde, LocalDate hasta) {
        return "Alta del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta);
    }

    public byte[] generarInformeClientesNuevosPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaClienteNuevo> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesClientesNuevos totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidad());
        parametros.put("POR_CIUDAD_TEXTO", construirTextoPorTipo(totales.getCantidadPorCiudad()));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_clientes_nuevos.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CLIENTES_NUEVOS", 0,
                textoFiltros + " · " + filas.size() + " cliente(s).");
        return pdf;
    }

    public void auditarExportacionCsvClientesNuevos(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CLIENTES_NUEVOS", 0,
                textoFiltros + " · " + cantidad + " cliente(s).");
    }

    // ======================================================================
    // C-4.1 (pedido de Joel): Cotización/simulación imprimible -- no es un "Informe" con filtros
    // sobre datos guardados (RF-33 sigue sin persistir nada), pero usa el mismo mecanismo de PDF
    // (GeneradorInformesPdf) y queda igual registrada en Auditoría, solo la emisión.
    // ======================================================================

    /** Días de validez de la simulación (pedido de Joel: "usá un parámetro existente o 7 días
     * fijo, y decime cuál elegiste"). No hay ningún parámetro de este tipo en parametros_sistema
     * (revisado bd/03_parametros.sql) -- se usan 7 días fijos. */
    public static final int DIAS_VALIDEZ_COTIZACION = 7;

    /** RF01-J (pedido de Joel): cada dato del interesado se trata por separado -- si el campo
     * vino vacío, una línea para completar a mano ("Nombre: ______________" / "C.I.:
     * ______________"); si vino, se muestra con su etiqueta y su dato. Reemplaza el "—" único que
     * usaba antes construirTextoOpcional (ya sin otros llamadores, se borra junto con esto). Pura. */
    public String construirLineaInteresado(String etiqueta, String valor) {
        return valor == null || valor.trim().isEmpty() ? etiqueta + ": ______________" : etiqueta + ": " + valor.trim();
    }

    /** Leyenda de la simulación (pedido explícito de Joel, texto literal + los días de validez). Pura. */
    public String construirLeyendaCotizacion(int diasValidez) {
        return "Simulación informativa sin validez contractual. Las condiciones finales dependen de la "
                + "evaluación crediticia y pueden variar según la fecha de desembolso. Validez: " + diasValidez
                + " días.";
    }

    /** C-4.2 Paso 0 (pedido de Joel): "Tasa del período" salía con 8 decimales (2,01177635 %) --
     * ServicioMotorFinanciero.calcularTasaMensual devuelve una escala intermedia de 10 decimales
     * (pensada para encadenar cálculos, no para mostrar). La TNA que ya se ve en pantalla usa
     * exactamente esta misma escala 6 antes de formatearse (Sec. 6.1 del motor, donde se calcula
     * "tna = i * 12", redondeada a escala 6 ANTES de guardarse en ResultadoCotizacion) -- mismo
     * redondeo acá, para la tasa mensual, sin tocar el motor. Pura. */
    public java.math.BigDecimal redondearTasaPeriodo(java.math.BigDecimal tasaMensual) {
        return tasaMensual.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    /** Filas del plan de pagos de la cotización, ya formateadas -- el .jrxml no calcula nada
     * (REGLAS_DESARROLLO.md §6). Pura: sobre el plan que ya devolvió ServicioMotorFinanciero.cotizar (vía
     * ServicioCotizacion.cotizar), nunca recalcula ningún importe. */
    public List<py.com.capitalcredito.sgcpc.dto.FilaPlanCotizacion> construirFilasPlanCotizacion(List<Cuota> plan) {
        List<py.com.capitalcredito.sgcpc.dto.FilaPlanCotizacion> filas = new ArrayList<>();
        for (Cuota cuota : plan) {
            filas.add(new py.com.capitalcredito.sgcpc.dto.FilaPlanCotizacion(cuota.getNumeroCuota(),
                    FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()),
                    FormateadorNumeros.comoGuaranies(cuota.getCapitalProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getInteresProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getTributosProgramados()),
                    FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()),
                    FormateadorNumeros.comoGuaranies(cuota.getSaldoCapitalPosterior())));
        }
        return filas;
    }

    /** C-4.1 (pedido de Joel): el PDF de la cotización -- recalculado en el servidor con los
     * mismos datos que ya se ven en pantalla (ServicioCotizacion.cotizar, el mismo motor, nunca se
     * toca). No persiste ningún registro de la cotización en sí (RF-33 se mantiene); solo queda la
     * auditoría de que se emitió el PDF. */
    public byte[] generarInformeCotizacionPdf(Connection conexion, java.math.BigDecimal monto, int plazoMeses,
            String nombreInteresado, String cedulaInteresado,
            py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion resultado, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        // RF01-K (pedido de Joel): teléfono/WhatsApp vigentes -- a diferencia de los comprobantes
        // que se reimprimen (constancia, pagaré, etc.), la cotización siempre se genera al
        // momento (RF-33, nunca se guarda), así que usa el valor vigente HOY, igual que
        // parametrosBase ya hace con razón social/RUC/domicilio -- no el valor histórico de una
        // fecha de operación, que acá no existe.
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValor(conexion, "empresa.telefono"));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValor(conexion, "empresa.whatsapp"));
        parametros.put("NOMBRE_INTERESADO", construirLineaInteresado("Nombre", nombreInteresado));
        parametros.put("CEDULA_INTERESADO", construirLineaInteresado("C.I.", cedulaInteresado));
        parametros.put("MONTO", FormateadorNumeros.comoGuaranies(monto));
        parametros.put("PLAZO", plazoMeses + " meses");
        parametros.put("PERIODICIDAD", "Mensual");
        String teaFormateada = FormateadorNumeros.comoPorcentaje(resultado.getTea());
        parametros.put("TEA", teaFormateada);
        // RF01-H (pedido de Joel): TNA, antes calculada pero nunca mostrada en el PDF.
        String tnaFormateada = FormateadorNumeros.comoPorcentaje(resultado.getTna());
        parametros.put("TNA", tnaFormateada);
        String tasaPeriodoFormateada = FormateadorNumeros.comoPorcentaje(
                redondearTasaPeriodo(ServicioMotorFinanciero.calcularTasaMensual(resultado.getTea())));
        parametros.put("TASA_PERIODO", tasaPeriodoFormateada);
        Cuota primeraCuota = resultado.getPlan().get(0);
        parametros.put("CUOTA", FormateadorNumeros.comoGuaranies(primeraCuota.getTotalProgramado()));
        parametros.put("TOTAL_A_PAGAR", FormateadorNumeros.comoGuaranies(resultado.getMontoTotalConIva()));
        String cftFormateada = FormateadorNumeros.comoPorcentaje(resultado.getCft());
        parametros.put("CFT", cftFormateada);
        parametros.put("LEYENDA", construirLeyendaCotizacion(DIAS_VALIDEZ_COTIZACION));

        // RF01-H (pedido de Joel): caja "¿Qué significa cada término?" al pie del PDF, con los 6
        // términos (antes faltaban IVA/Capital/Interés) -- misma fuente que la pantalla
        // (GlosarioFinanciero, Grupo 6). "Tasa mensual" reutiliza tasaPeriodoFormateada (ya
        // calculado arriba con la fórmula oficial, no una reconstrucción aparte) para que el
        // texto de la caja y la fila "Tasa mensual" de más arriba nunca puedan mostrar números
        // distintos en el mismo documento. Los textos de GlosarioFinanciero ya no se
        // autoidentifican ("TEA (Tasa Efectiva Anual): ...") como antes -- acá, sin el ícono ⓘ
        // ni el título del modal de la pantalla, se antepone getTitulo() a mano para que cada
        // párrafo del PDF siga diciendo de qué término habla.
        LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
        String topeTeaFormateado = limiteVigente == null ? "no disponible"
                : FormateadorNumeros.comoPorcentaje(limiteVigente.getTasaMaximaTea());
        parametros.put("GLOSARIO_TEA",
                GlosarioFinanciero.TEA.getTitulo() + ": " + GlosarioFinanciero.TEA.completo(topeTeaFormateado));
        parametros.put("GLOSARIO_TNA", GlosarioFinanciero.TNA.getTitulo() + ": "
                + GlosarioFinanciero.TNA.completo(tasaPeriodoFormateada, tnaFormateada));
        parametros.put("GLOSARIO_CFT",
                GlosarioFinanciero.CFT.getTitulo() + ": " + GlosarioFinanciero.CFT.completo());
        parametros.put("GLOSARIO_IVA",
                GlosarioFinanciero.IVA.getTitulo() + ": " + GlosarioFinanciero.IVA.completo());
        parametros.put("GLOSARIO_CAPITAL",
                GlosarioFinanciero.CAPITAL.getTitulo() + ": " + GlosarioFinanciero.CAPITAL.completo());
        parametros.put("GLOSARIO_INTERES",
                GlosarioFinanciero.INTERES.getTitulo() + ": " + GlosarioFinanciero.INTERES.completo());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("cotizacion.jasper", parametros,
                new JRBeanCollectionDataSource(construirFilasPlanCotizacion(resultado.getPlan())));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_COTIZACION_PDF", "COTIZACION", 0,
                "Monto " + FormateadorNumeros.comoGuaranies(monto) + " · " + plazoMeses + " meses"
                        + (nombreInteresado == null || nombreInteresado.trim().isEmpty() ? ""
                                : " · Interesado: " + nombreInteresado.trim()));
        return pdf;
    }

    // ======================================================================
    // C-4.2 (pedido de Joel): Usuarios y accesos -- mismo molde que el resto. Sin filtros (la
    // pantalla /usuarios no tiene ninguno): el informe siempre trae a todos los usuarios.
    // ======================================================================

    public String construirTextoFiltrosUsuarios() {
        return "Todos los usuarios registrados";
    }

    public byte[] generarInformeUsuariosPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaInformeUsuario> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesUsuarios totales, long idUsuarioEmisor, String nombreUsuarioEmisor)
            throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        String textoFiltros = construirTextoFiltrosUsuarios();
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidadTotal());
        parametros.put("POR_PERFIL_TEXTO", construirTextoPorTipo(totales.getCantidadPorPerfil()));
        parametros.put("POR_ESTADO_TEXTO", construirTextoPorTipo(totales.getCantidadPorEstado()));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_usuarios.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_USUARIOS", 0,
                textoFiltros + " · " + filas.size() + " usuario(s).");
        return pdf;
    }

    public void auditarExportacionCsvUsuarios(Connection conexion, long idUsuarioEmisor, int cantidad)
            throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_USUARIOS", 0,
                construirTextoFiltrosUsuarios() + " · " + cantidad + " usuario(s).");
    }

    // ======================================================================
    // C-4.3 (pedido de Joel): Historial de parámetros -- mismo molde que el resto. Solo lectura:
    // no cambia ningún parámetro, solo audita que se emitió el informe.
    // ======================================================================

    public String construirTextoFiltrosHistorialParametros(LocalDate desde, LocalDate hasta,
            String nombreParametroEtiqueta, boolean incluirIniciales) {
        String texto = "Modificación del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta)
                + " · " + (nombreParametroEtiqueta == null ? "Todos los parámetros" : nombreParametroEtiqueta);
        return incluirIniciales ? texto + " · Incluye valores iniciales" : texto;
    }

    public byte[] generarInformeHistorialParametrosPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaHistorialParametro> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesHistorialParametros totales, String textoFiltros,
            long idUsuarioEmisor, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidadTotal());
        // RF39 (pantalla rediseñada, pedido de Joel): el resumen final del PDF pasa a ser el
        // mismo total corto de la pantalla ("N cambios en el período · M parámetros
        // modificados") -- ya no usa POR_PARAMETRO_TEXTO/construirTextoPorTipo (ese helper
        // genérico sigue igual, lo usan otros informes; acá solo se deja de llamarlo).
        parametros.put("CANTIDAD_PARAMETROS_DISTINTOS", totales.getCantidadParametrosDistintos());
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_historial_parametros.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_HISTORIAL_PARAMETROS", 0,
                textoFiltros + " · " + filas.size() + " cambio(s).");
        return pdf;
    }

    public void auditarExportacionCsvHistorialParametros(Connection conexion, long idUsuarioEmisor,
            String textoFiltros, int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_HISTORIAL_PARAMETROS", 0,
                textoFiltros + " · " + cantidad + " cambio(s).");
    }

    // ======================================================================
    // C-4.4 (pedido de Joel): Consultas al buró -- último informe del bloque C. Solo lectura:
    // nunca cambia ninguna consulta ni consentimiento, solo audita la emisión.
    // ======================================================================

    public String construirTextoFiltrosConsultasBuro(LocalDate desde, LocalDate hasta, String resultadoEtiqueta) {
        return "Consulta del " + FormateadorFechas.comoDdMmAaaa(desde) + " al " + FormateadorFechas.comoDdMmAaaa(hasta)
                + " · " + (resultadoEtiqueta == null ? "Todos los resultados" : resultadoEtiqueta);
    }

    public byte[] generarInformeConsultasBuroPdf(Connection conexion,
            List<py.com.capitalcredito.sgcpc.dto.FilaConsultaBuro> filas,
            py.com.capitalcredito.sgcpc.dto.TotalesConsultasBuro totales, String textoFiltros, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("FILTROS_APLICADOS", textoFiltros);
        parametros.put("CANTIDAD_TOTAL", totales.getCantidadTotal());
        parametros.put("CANTIDAD_CLIENTES_DISTINTOS", totales.getCantidadClientesDistintos());
        parametros.put("POR_RESULTADO_TEXTO", construirTextoPorTipo(totales.getCantidadPorResultado()));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("informe_consultas_buro.jasper", parametros,
                new JRBeanCollectionDataSource(filas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "INFORME_CONSULTAS_BURO", 0,
                textoFiltros + " · " + filas.size() + " consulta(s).");
        return pdf;
    }

    public void auditarExportacionCsvConsultasBuro(Connection conexion, long idUsuarioEmisor, String textoFiltros,
            int cantidad) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "INFORME_CONSULTAS_BURO", 0,
                textoFiltros + " · " + cantidad + " consulta(s).");
    }

    // ======================================================================
    // RF34 (auditoría de impresión, pedido de Joel): el estado de cuenta dejó de imprimirse con
    // window.print() (Decisión 3, Bloque 4/E8) -- ahora es un Informe Jasper más, con el mismo
    // encabezado común (empresa + LOGO) que el resto. A diferencia de los demás informes de este
    // bloque, no tiene "filtros" (es siempre un préstamo puntual): el encabezado muestra la
    // operación en su lugar.
    // ======================================================================

    public byte[] generarEstadoCuentaPdf(Connection conexion, PrestamoCredito prestamo, Persona cliente,
            TotalesEstadoCuenta totales, String avisoPlan, List<FilaCuotaPendiente> cuotas,
            List<FilaHistorialCobranza> historial, List<FilaGestionReporte> gestiones, long idUsuarioEmisor,
            String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("IDENTIFICADOR_OPERACION", prestamo.getIdentificadorOperacion());
        parametros.put("ESTADO_ETIQUETA", prestamo.getEstadoPrestamo().getEtiqueta());
        parametros.put("CLIENTE_NOMBRE", cliente.getNombres() + " " + cliente.getApellidos());
        parametros.put("CLIENTE_CEDULA", FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad()));
        parametros.put("CLIENTE_TELEFONO", cliente.getTelefonoWhatsapp());
        parametros.put("MONTO_APROBADO", FormateadorNumeros.comoGuaranies(prestamo.getMontoAprobado()));
        parametros.put("PLAZO_TEXTO", prestamo.getPlazoMeses() + (prestamo.getPlazoMeses() == 1 ? " cuota" : " cuotas"));
        parametros.put("TEA", FormateadorNumeros.comoPorcentaje(prestamo.getTasaTea()));
        parametros.put("SALDO_CAPITAL", totales.getSaldoCapital());
        parametros.put("TOTAL_PAGADO", totales.getTotalPagado());
        parametros.put("MONTO_EXIGIBLE_HOY", totales.getMontoExigibleHoy() == null ? "—" : totales.getMontoExigibleHoy());
        parametros.put("AVISO_PLAN", avisoPlan == null ? "" : avisoPlan);
        parametros.put("HISTORIAL", historial);
        parametros.put("GESTIONES", gestiones);
        parametros.put("SUBREPORT_HISTORIAL", cargarSubreporteEstadoCuenta("estado_cuenta_historial.jasper"));
        parametros.put("SUBREPORT_GESTIONES", cargarSubreporteEstadoCuenta("estado_cuenta_gestiones.jasper"));
        byte[] pdf = GeneradorInformesPdf.llenarYExportar("estado_cuenta.jasper", parametros,
                new JRBeanCollectionDataSource(cuotas));
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EMITIR_INFORME_PDF", "ESTADO_CUENTA",
                prestamo.getIdPrestamo(), "Operación " + prestamo.getIdentificadorOperacion() + " · "
                        + cuotas.size() + " cuota(s), " + historial.size() + " cobro(s), " + gestiones.size()
                        + " gestión(es).");
        return pdf;
    }

    public void auditarExportacionCsvEstadoCuenta(Connection conexion, long idUsuarioEmisor, PrestamoCredito prestamo,
            int cantidadCuotas, int cantidadCobros, int cantidadGestiones) throws SQLException {
        servicioAuditoria.registrar(conexion, idUsuarioEmisor, "EXPORTAR_CSV", "ESTADO_CUENTA", prestamo.getIdPrestamo(),
                "Operación " + prestamo.getIdentificadorOperacion() + " · " + cantidadCuotas + " cuota(s), "
                        + cantidadCobros + " cobro(s), " + cantidadGestiones + " gestión(es).");
    }
}
