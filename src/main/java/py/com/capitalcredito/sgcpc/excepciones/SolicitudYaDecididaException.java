package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF09/RF10 (pedido de Joel, 11/10/2026): subtipo de NegocioException para la carrera de dos
 * pestañas -- `SolicitudDAO.marcarDecidida` devolvió `false` porque otro usuario ya decidió
 * (aprobó/rechazó/canceló) esta misma solicitud en la angosta ventana entre la validación y el
 * `UPDATE`. No es un error del servidor (nada salió mal, nadie hizo nada indebido), así que
 * `ControladorAprobacionesServlet` lo distingue de una `NegocioException` cualquiera para
 * mostrarlo como advertencia (`MensajesFlash.guardarAdvertencia`), no como error.
 */
public class SolicitudYaDecididaException extends NegocioException {

    public SolicitudYaDecididaException(String mensaje) {
        super(mensaje);
    }
}
