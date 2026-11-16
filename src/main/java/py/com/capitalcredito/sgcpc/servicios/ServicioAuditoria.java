package py.com.capitalcredito.sgcpc.servicios;

import java.sql.Connection;
import java.sql.SQLException;
import py.com.capitalcredito.sgcpc.dao.AuditoriaDAO;
import py.com.capitalcredito.sgcpc.infraestructura.ContextoPeticion;
import py.com.capitalcredito.sgcpc.infraestructura.EtiquetasAuditoria;

/**
 * Envoltorio fino de AuditoriaDAO usado por los demas Servicios de esta
 * etapa (RNF08). Regla dura: nunca recibe ni guarda una contrasena o un
 * hash, ni en detalle ni en los valores JSON — quien llama arma un detalle
 * generico para cambios de contrasena.
 */
public class ServicioAuditoria {

    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    /** Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 2, PA-28): toma IP/navegador
     * de ContextoPeticion (los llena FiltroAutenticacion al entrar la petición) -- null para las
     * operaciones del sistema (EtiquetasAuditoria.esOperacionDelSistema), igual que antes. */
    public void registrar(Connection conexion, long idUsuario, String operacion, String tipoEntidad, long idEntidad,
            String detalle) throws SQLException {
        boolean esOperacionDelSistema = EtiquetasAuditoria.esOperacionDelSistema(operacion, tipoEntidad);
        String ip = esOperacionDelSistema ? null : ContextoPeticion.obtenerIp();
        String navegador = esOperacionDelSistema ? null : ContextoPeticion.obtenerNavegador();
        auditoriaDAO.insertar(conexion, idUsuario, operacion, tipoEntidad, idEntidad, null, null, ip, navegador,
                detalle);
    }

    public void registrar(Connection conexion, long idUsuario, String operacion, String tipoEntidad, long idEntidad,
            String direccionIp, String detalle) throws SQLException {
        auditoriaDAO.insertar(conexion, idUsuario, operacion, tipoEntidad, idEntidad, null, null, direccionIp, detalle);
    }

    /** Bloque de seguridad (hallazgo de Joel): variante con navegador (User-Agent), para los
     * eventos de acceso y de sesión. */
    public void registrar(Connection conexion, long idUsuario, String operacion, String tipoEntidad, long idEntidad,
            String direccionIp, String navegador, String detalle) throws SQLException {
        auditoriaDAO.insertar(conexion, idUsuario, operacion, tipoEntidad, idEntidad, null, null, direccionIp,
                navegador, detalle);
    }

    /** Bloque de seguridad (hallazgo de Joel, decisión nueva -- cambia D-27): intento de acceso
     * con un usuario que no existe -- ver AuditoriaDAO.insertarIntentoUsuarioInexistente. */
    public void registrarIntentoUsuarioInexistente(Connection conexion, String usuarioEscrito, String direccionIp,
            String navegador, String detalle) throws SQLException {
        auditoriaDAO.insertarIntentoUsuarioInexistente(conexion, usuarioEscrito, direccionIp, navegador, detalle);
    }

    /** Bloque 4 (E8, decisión 5): ¿ya se registró hoy esta operación? (para el recálculo diario). */
    public boolean yaSeRegistroHoy(Connection conexion, String operacion) throws SQLException {
        return auditoriaDAO.existeHoy(conexion, operacion);
    }

    /** Para cambios de parametros: guarda el valor anterior y el nuevo (nunca contraseñas).
     * Tanda de usabilidad (21/10/2026, punto 8 paso 2, PA-28): misma IP/navegador de
     * ContextoPeticion que el resto de registrar(...) -- CAMBIAR_PARAMETRO y
     * GENERAR_AUTORIZACION_BURO son operaciones de negocio, nunca del sistema. */
    public void registrarCambioDeValor(Connection conexion, long idUsuario, String operacion, String tipoEntidad,
            long idEntidad, String valorAnterior, String valorNuevo, String detalle) throws SQLException {
        boolean esOperacionDelSistema = EtiquetasAuditoria.esOperacionDelSistema(operacion, tipoEntidad);
        String ip = esOperacionDelSistema ? null : ContextoPeticion.obtenerIp();
        String navegador = esOperacionDelSistema ? null : ContextoPeticion.obtenerNavegador();
        auditoriaDAO.insertar(conexion, idUsuario, operacion, tipoEntidad, idEntidad,
                AuditoriaDAO.jsonDeValor(valorAnterior), AuditoriaDAO.jsonDeValor(valorNuevo), ip, navegador, detalle);
    }
}
