package py.com.capitalcredito.sgcpc.infraestructura;

/**
 * Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 2, PA-28): IP y navegador de la
 * petición HTTP en curso, en un ThreadLocal -- FiltroAutenticacion.doFilter los llena al entrar
 * (misma forma de leerlos que ya usa ControladorAutenticacionServlet para el login:
 * request.getRemoteAddr()/request.getHeader("User-Agent"), sin helper propio, no había ninguno
 * que reutilizar) y los limpia en un finally que envuelve TODO el método, así ServicioAuditoria.
 * registrar (la variante de 6 parámetros, ~25 operaciones de negocio) y registrarCambioDeValor
 * los pueden leer sin que cada Servicio necesite recibir el HttpServletRequest. Valor por
 * defecto null (ningún request en curso en este hilo, o todavía no se llenó).
 *
 * Riesgo único real (Tomcat reutiliza hilos entre peticiones): un remove() olvidado dejaría un
 * valor pegado al hilo y podría aparecer en la operación de OTRO usuario -- por eso el llenado y
 * el limpiado viven juntos en el mismo filtro, en un try/finally que cubre el método entero
 * (cualquier return intermedio -- ruta pública, sesión inválida, redirect -- limpia igual).
 */
public final class ContextoPeticion {

    private ContextoPeticion() {
    }

    private static final ThreadLocal<String> IP = new ThreadLocal<>();
    private static final ThreadLocal<String> NAVEGADOR = new ThreadLocal<>();

    public static void establecer(String direccionIp, String navegador) {
        IP.set(direccionIp);
        NAVEGADOR.set(navegador);
    }

    public static String obtenerIp() {
        return IP.get();
    }

    public static String obtenerNavegador() {
        return NAVEGADOR.get();
    }

    public static void limpiar() {
        IP.remove();
        NAVEGADOR.remove();
    }
}
