package py.com.capitalcredito.sgcpc.infraestructura;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Fija java.awt.headless=true al arrancar la aplicacion, requisito para que
 * JasperReports funcione sin pantalla en el servidor Ubuntu de la defensa
 * (Doc. 00 Sec. 14.4). Se registra explicitamente en web.xml (sin @WebListener)
 * para no duplicar el registro.
 */
public class EscuchaContextoAplicacion implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void contextDestroyed(ServletContextEvent evento) {
        // Nada que liberar: no se usa pool ni recursos globales.
    }
}
