package py.com.capitalcredito.sgcpc.excepciones;

/**
 * RF19 (pedido de Joel, 11/10/2026): subtipo de NegocioException para la carrera de dos
 * pestañas al cerrar caja -- `SesionCajaDAO.marcarCerrada` devolvió `false` porque otro usuario
 * ya cerró esta misma sesión en la angosta ventana entre la validación y el `UPDATE`. No es un
 * error del servidor, así que `ControladorCajaServlet` lo distingue de una `NegocioException`
 * cualquiera para mostrarlo como advertencia (`MensajesFlash.guardarAdvertencia`), no como
 * error -- mismo criterio que `SolicitudYaDecididaException` (RF09/RF10).
 */
public class CajaYaCerradaException extends NegocioException {

    public CajaYaCerradaException(String mensaje) {
        super(mensaje);
    }
}
