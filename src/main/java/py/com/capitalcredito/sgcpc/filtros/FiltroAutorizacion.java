package py.com.capitalcredito.sgcpc.filtros;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAuditoria;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;

/**
 * RNF11: valida en el servidor que el perfil de la sesion tenga acceso
 * total al permiso de la ruta pedida (Doc. 00 §3.1, via
 * ServicioAutorizacion) — corre despues de FiltroAutenticacion, asi que ya
 * hay sesion. Una ruta sin entrada en RUTAS_PROTEGIDAS solo exige estar
 * autenticado (lo cubre FiltroAutenticacion); las etapas siguientes van
 * agregando entradas aca a medida que agregan pantallas.
 */
public class FiltroAutorizacion implements Filter {

    private static final Map<String, Permiso> RUTAS_PROTEGIDAS = new LinkedHashMap<>();

    static {
        // D-151 (pedido de Joel): dos permisos distintos -- Usuarios queda sin acceso para el
        // gerente (NINGUNO), Parámetros le queda en CONSULTA (ve valores/historial, no modifica).
        RUTAS_PROTEGIDAS.put("/usuarios", Permiso.ADMINISTRAR_USUARIOS);
        RUTAS_PROTEGIDAS.put("/parametros", Permiso.ADMINISTRAR_PARAMETROS);
        // E3 (decision 5 del plan): /clientes tiene perfiles con CONSULTA (la cajera), asi que
        // aca no alcanza con "tiene o no tiene": GET exige nivel != NINGUNO, el resto exige TOTAL.
        RUTAS_PROTEGIDAS.put("/clientes", Permiso.REGISTRAR_EDITAR_CLIENTE);
        RUTAS_PROTEGIDAS.put("/documentos", Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO);
        RUTAS_PROTEGIDAS.put("/cotizador", Permiso.COTIZAR);
        // E4 (D-60): dos rutas, dos permisos -- GESTIONAR_SOLICITUD es TOTAL solo para el
        // oficial, APROBAR_RECHAZAR_SOLICITUD solo para el gerente (ver ServicioAutorizacion).
        RUTAS_PROTEGIDAS.put("/solicitudes", Permiso.GESTIONAR_SOLICITUD);
        RUTAS_PROTEGIDAS.put("/aprobaciones", Permiso.APROBAR_RECHAZAR_SOLICITUD);
        // E5 (D-68): oficial Y gerente operan esta pantalla (los dos tienen TOTAL en este
        // permiso) -- la sub-accion "anular" (solo gerente) se valida aparte, en el controlador.
        RUTAS_PROTEGIDAS.put("/formalizacion", Permiso.GENERAR_DOCUMENTOS_FORMALIZACION);
        // E6 (plan): rutas mas especificas antes que "/caja" -- permisoDe() devuelve la primera
        // coincidencia por prefijo, y ENTREGAR_RECIBIR_EFECTIVO_CAJA/AUTORIZAR_REVERSION_O_DIFERENCIA
        // son permisos distintos de ABRIR_CERRAR_CAJA aunque compartan la misma pantalla. La
        // restriccion fina de que solo el gerente entrega efectivo (aunque el permiso de la ruta
        // tambien le de TOTAL a la cajera) se valida en ServicioCaja, no aca.
        RUTAS_PROTEGIDAS.put("/caja/entregar", Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA);
        RUTAS_PROTEGIDAS.put("/caja/revisar-diferencia", Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
        RUTAS_PROTEGIDAS.put("/caja", Permiso.ABRIR_CERRAR_CAJA);
        RUTAS_PROTEGIDAS.put("/desembolso", Permiso.DESEMBOLSAR);
        // E7 (plan): /cobranza es TOTAL solo para la cajera (Doc. 00 §3.1).
        RUTAS_PROTEGIDAS.put("/cobranza", Permiso.COBRAR);
        // Bloque 4 (E8, decisión 4): /mora es de oficial y gerente -- la cajera queda en NINGUNO
        // para este permiso (ver ServicioAutorizacion), no está en su menú.
        RUTAS_PROTEGIDAS.put("/mora", Permiso.REGISTRAR_GESTION_COBRANZA);
        // Bloque 4 (E8, decisión 2): /reportes es un permiso propio, distinto de /panel (que sigue
        // en VER_PANEL_REPORTES) -- gerente TOTAL, oficial CONSULTA (con el submenú acotado dentro
        // del controlador), cajera NINGUNO.
        RUTAS_PROTEGIDAS.put("/reportes", Permiso.VER_REPORTES);
        // B1 (Bloque 7/E10, pedido de Joel): mismo permiso que /reportes (gerente TOTAL, oficial
        // CONSULTA, cajera NINGUNO) -- las 3 rutas de ControladorPrestamosServlet son GET, así
        // que a oficial le alcanza con CONSULTA para la lista, el informe PDF y el CSV por igual.
        RUTAS_PROTEGIDAS.put("/prestamos", Permiso.VER_REPORTES);
        // E9/Bloque 5 (PO-13): /reversiones/solicitar es de la cajera; el resto (bandeja,
        // autorizar, rechazar) es solo del gerente -- mismo criterio de "ruta específica antes
        // que la general" que /caja.
        RUTAS_PROTEGIDAS.put("/reversiones/solicitar", Permiso.SOLICITAR_REVERSION);
        RUTAS_PROTEGIDAS.put("/reversiones/autorizar", Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
        RUTAS_PROTEGIDAS.put("/reversiones/rechazar", Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
        RUTAS_PROTEGIDAS.put("/reversiones", Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA);
    }

    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();

    private ServletContext contextoServlet;

    @Override
    public void init(FilterConfig filterConfig) {
        contextoServlet = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String ruta = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        Permiso permisoRequerido = permisoDe(ruta);
        if (permisoRequerido != null) {
            HttpSession sesion = httpRequest.getSession(false);
            Usuario usuario = sesion == null ? null
                    : (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
            // Decision 5 (plan E3): GET (ver) alcanza con CONSULTA; cualquier otro metodo (POST,
            // que es como el sistema hace toda alta/edicion/carga) exige TOTAL.
            boolean esLectura = "GET".equalsIgnoreCase(httpRequest.getMethod())
                    || "HEAD".equalsIgnoreCase(httpRequest.getMethod());
            NivelAcceso nivel = usuario == null ? NivelAcceso.NINGUNO
                    : servicioAutorizacion.nivelAcceso(usuario.getPerfilUsuario(), permisoRequerido);
            boolean autorizado = autorizaAcceso(nivel, esLectura);
            if (!autorizado) {
                auditarAccesoDenegado(usuario, ruta, httpRequest.getRemoteAddr());
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para esta acción.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    // D-146 (E-5, pedido de Joel): /cobranza/{idPrestamo}/recibo|factura|constancia|nota-anulacion|
    // reversion/{id} y /desembolso/{idPrestamo}/comprobante son todas GET, solo para ver/reimprimir
    // un PDF ya emitido -- el gerente controla la rendición y necesita estas, sin poder cobrar/
    // cancelar/adelantar/desembolsar. No se pueden aislar en RUTAS_PROTEGIDAS de abajo (match por
    // prefijo): el id de préstamo varía en medio de la ruta, no es un prefijo fijo como
    // "/caja/entregar". Se resuelven antes, por forma de los segmentos, con su propio permiso
    // (VER_COMPROBANTES_COBRANZA) -- el resto de /cobranza y /desembolso sigue exactamente igual.
    // "reversion" (RF25, pedido de Joel, 12/10/2026): la constancia de una reversión se reimprime
    // también desde el historial de /reversiones (gerente) -- sin agregar "reversion" acá, el
    // gerente recibiría 403 (caería en el permiso general de /cobranza, Permiso.COBRAR, solo cajera).
    private static final java.util.Set<String> SEGMENTOS_DOCUMENTO_COBRANZA = new java.util.HashSet<>(
            java.util.Arrays.asList("recibo", "factura", "constancia", "nota-anulacion", "reversion"));

    static Permiso permisoDeDocumento(String ruta) {
        String[] segmentos = (ruta.startsWith("/") ? ruta.substring(1) : ruta).split("/");
        if (segmentos.length == 4 && "cobranza".equals(segmentos[0]) && segmentos[1].matches("\\d+")
                && SEGMENTOS_DOCUMENTO_COBRANZA.contains(segmentos[2])) {
            return Permiso.VER_COMPROBANTES_COBRANZA;
        }
        if (segmentos.length == 3 && "desembolso".equals(segmentos[0]) && segmentos[1].matches("\\d+")
                && "comprobante".equals(segmentos[2])) {
            return Permiso.VER_COMPROBANTES_COBRANZA;
        }
        return null;
    }

    static Permiso permisoDe(String ruta) {
        Permiso permisoDocumento = permisoDeDocumento(ruta);
        if (permisoDocumento != null) {
            return permisoDocumento;
        }
        for (Map.Entry<String, Permiso> entrada : RUTAS_PROTEGIDAS.entrySet()) {
            if (ruta.equals(entrada.getKey()) || ruta.startsWith(entrada.getKey() + "/")) {
                return entrada.getValue();
            }
        }
        return null;
    }

    /** D-146 (E-5): la misma regla de "GET alcanza con CONSULTA, el resto exige TOTAL" (decisión
     * 5, plan E3) que ya aplicaba doFilter -- separada para poder probarla sin un
     * HttpServletRequest real (REGLAS_DESARROLLO.md §7 punto 9, pura, sin tocar la base). */
    static boolean autorizaAcceso(NivelAcceso nivel, boolean esLectura) {
        return esLectura ? nivel != NivelAcceso.NINGUNO : nivel == NivelAcceso.TOTAL;
    }

    /** Punto 8: audita el 403. Sin usuario en sesion no hay id_usuario valido para auditar (no debería pasar: ya pasó FiltroAutenticacion). */
    private void auditarAccesoDenegado(Usuario usuario, String ruta, String direccionIp) {
        if (usuario == null) {
            return;
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            servicioAuditoria.registrar(conexion, usuario.getIdUsuario(), "ACCESO_DENEGADO", "USUARIO",
                    usuario.getIdUsuario(), direccionIp, "Sin permiso para acceder a " + ruta + ".");
        } catch (SQLException error) {
            // No se interrumpe la respuesta 403 por un problema al auditar, pero se deja rastro.
            contextoServlet.log("No se pudo auditar el acceso denegado a " + ruta + ".", error);
        }
    }

    @Override
    public void destroy() {
        // Sin recursos que liberar.
    }
}
