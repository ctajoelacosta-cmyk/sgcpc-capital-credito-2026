package py.com.capitalcredito.sgcpc.infraestructura;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Mensaje "flash": para cuando un Servlet hace sendRedirect (no forward) y
 * necesita mostrar un mensaje una sola vez despues del redirect (el
 * request original se pierde, pero la sesion no). Se guarda en sesion con
 * guardarExito/guardarError antes del redirect; plantilla/cabecera.jsp lo
 * traslada a request ("mensajeExito"/"mensajeError", los mismos nombres
 * que ya usan las pantallas con forward directo) y lo borra de la sesion,
 * asi que se muestra una unica vez. Reutilizable para cualquier pantalla
 * de las etapas siguientes que redirija tras una accion.
 */
public final class MensajesFlash {

    private static final String CLAVE_EXITO = "flashMensajeExito";
    private static final String CLAVE_ERROR = "flashMensajeError";
    private static final String CLAVE_ADVERTENCIA = "flashMensajeAdvertencia";
    private static final String CLAVE_VALORES = "flashValoresFormulario";
    private static final String CLAVE_EXITO_IMPRIMIBLES = "flashMensajeExitoImprimibles";
    private static final String CLAVE_ERROR_ENLACES = "flashMensajeErrorEnlaces";
    private static final String CLAVE_EXITO_SENSIBLE = "flashMensajeExitoSensible";

    private MensajesFlash() {
    }

    public static void guardarExito(HttpSession sesion, String mensaje) {
        sesion.setAttribute(CLAVE_EXITO, mensaje);
        sesion.removeAttribute(CLAVE_EXITO_IMPRIMIBLES);
    }

    /**
     * Bloque 7/E10 (D-131, pedido de Joel): cuando la acción deja algo para imprimir (comprobante
     * de desembolso, recibo, factura, constancia), el modal de éxito trae el botón -- etiqueta
     * visible -> URL (relativa, sin el contextPath: plantilla/cabecera.jsp se lo agrega), en el
     * orden en que deban aparecer los botones.
     */
    public static void guardarExito(HttpSession sesion, String mensaje, Map<String, String> documentosImprimibles) {
        sesion.setAttribute(CLAVE_EXITO, mensaje);
        if (documentosImprimibles == null || documentosImprimibles.isEmpty()) {
            sesion.removeAttribute(CLAVE_EXITO_IMPRIMIBLES);
        } else {
            sesion.setAttribute(CLAVE_EXITO_IMPRIMIBLES, documentosImprimibles);
        }
    }

    /**
     * B213 (pedido de Joel, 15/11/2026): para un mensaje de exito que trae un dato que se muestra
     * UNA sola vez y no se puede volver a consultar -- hoy, la contrasena temporal que genera un
     * alta o un restablecimiento de usuario (RF37). "valorACopiar" es ese dato solo (no el mensaje
     * entero): plantilla/pie.jsp usa su presencia para que el modal no se cierre con un clic
     * afuera ni con Esc, y para ofrecer el boton "Copiar". Sin este valor, el modal de exito se
     * comporta exactamente como siempre.
     */
    public static void guardarExitoSensible(HttpSession sesion, String mensaje, String valorACopiar) {
        sesion.setAttribute(CLAVE_EXITO, mensaje);
        sesion.removeAttribute(CLAVE_EXITO_IMPRIMIBLES);
        if (valorACopiar == null || valorACopiar.trim().isEmpty()) {
            sesion.removeAttribute(CLAVE_EXITO_SENSIBLE);
        } else {
            sesion.setAttribute(CLAVE_EXITO_SENSIBLE, valorACopiar);
        }
    }

    public static void guardarError(HttpSession sesion, String mensaje) {
        sesion.setAttribute(CLAVE_ERROR, mensaje);
        sesion.removeAttribute(CLAVE_ERROR_ENLACES);
    }

    /**
     * Corrección (pedido de Joel, 10/10/2026): mismo criterio que guardarExito con
     * documentosImprimibles -- cuando el mensaje de error nombra un dato que conviene que sea un
     * enlace (ej. el código de una solicitud abierta), "enlaces" es texto visible -> ruta
     * relativa (sin contextPath: plantilla/cabecera.jsp se lo agrega); plantilla/pie.jsp
     * reemplaza ese texto por un enlace real. El mensaje en sí sigue siendo texto plano (nunca
     * HTML).
     */
    public static void guardarError(HttpSession sesion, String mensaje, Map<String, String> enlaces) {
        sesion.setAttribute(CLAVE_ERROR, mensaje);
        if (enlaces == null || enlaces.isEmpty()) {
            sesion.removeAttribute(CLAVE_ERROR_ENLACES);
        } else {
            sesion.setAttribute(CLAVE_ERROR_ENLACES, enlaces);
        }
    }

    /**
     * RF02 (pedido de Joel): tercer nivel, amarillo -- para cuando una acción SÍ se completó
     * (se guardó, a diferencia de guardarError) pero hay algo para atender (ej. al editar un
     * cliente que dejó de cumplir la política de crédito vigente). Nunca un modal rojo cuando la
     * acción salió bien: antes solo existían éxito (verde) y error (rojo), y un caso así quedaba
     * mal representado con cualquiera de los dos.
     */
    public static void guardarAdvertencia(HttpSession sesion, String mensaje) {
        sesion.setAttribute(CLAVE_ADVERTENCIA, mensaje);
    }

    /**
     * D-89: para que un formulario que redirige tras un rechazo (PRG) pueda volver a mostrar lo
     * que el usuario ya había escrito, en vez de vaciarse -- misma idea que guardarExito/Error,
     * una sola lectura (plantilla/cabecera.jsp la traslada a request y la borra de la sesión).
     */
    public static void guardarValores(HttpSession sesion, Map<String, String> valores) {
        sesion.setAttribute(CLAVE_VALORES, valores);
    }

    /** Se llama una vez por pedido, desde plantilla/cabecera.jsp. */
    public static void trasladarARequest(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return;
        }
        Object exito = sesion.getAttribute(CLAVE_EXITO);
        if (exito != null) {
            request.setAttribute("mensajeExito", exito);
            sesion.removeAttribute(CLAVE_EXITO);
        }
        Object error = sesion.getAttribute(CLAVE_ERROR);
        if (error != null) {
            request.setAttribute("mensajeError", error);
            sesion.removeAttribute(CLAVE_ERROR);
        }
        Object enlacesError = sesion.getAttribute(CLAVE_ERROR_ENLACES);
        if (enlacesError != null) {
            request.setAttribute("mensajeErrorEnlaces", enlacesError);
            sesion.removeAttribute(CLAVE_ERROR_ENLACES);
        }
        Object advertencia = sesion.getAttribute(CLAVE_ADVERTENCIA);
        if (advertencia != null) {
            request.setAttribute("mensajeAdvertencia", advertencia);
            sesion.removeAttribute(CLAVE_ADVERTENCIA);
        }
        Object valores = sesion.getAttribute(CLAVE_VALORES);
        if (valores != null) {
            request.setAttribute("valoresFormulario", valores);
            sesion.removeAttribute(CLAVE_VALORES);
        }
        Object sensible = sesion.getAttribute(CLAVE_EXITO_SENSIBLE);
        if (sensible != null) {
            request.setAttribute("mensajeExitoSensible", sensible);
            sesion.removeAttribute(CLAVE_EXITO_SENSIBLE);
        }
        Object imprimibles = sesion.getAttribute(CLAVE_EXITO_IMPRIMIBLES);
        if (imprimibles != null) {
            request.setAttribute("mensajeExitoImprimibles", imprimibles);
            sesion.removeAttribute(CLAVE_EXITO_IMPRIMIBLES);
        }
    }
}
