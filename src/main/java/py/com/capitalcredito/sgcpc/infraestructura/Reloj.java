package py.com.capitalcredito.sgcpc.infraestructura;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * D-110 (Bloque 7/E10, Sec. 14.4): única fuente de "hoy"/"ahora" del lado Java, siempre en
 * America/Asuncion -- nunca TimeZone.setDefault (afectaría a todo Tomcat, no solo esta app);
 * cada llamada pasa la zona explícita. El lado PostgreSQL se fija aparte (FabricaConexionBD +
 * ALTER DATABASE sgcpc_db), para que las dos fuentes de "ahora" coincidan sin depender de la
 * zona horaria del sistema operativo del servidor.
 *
 * D-115 (Bloque 7/E10, 7.4): agrega un instante simulado, por hilo, de uso EXCLUSIVO del
 * cargador de datos de demostración (`cargador.CargadorDatosPrueba`, en src/test/java -- nunca
 * se empaqueta en el .war, corre como programa aparte con `mvn -Dtest=...`, nunca dentro de
 * Tomcat). Ningún Servlet/Filtro llama nunca a fijarParaCargador: el .war desplegado y el
 * cargador son procesos de JVM completamente separados, así que un ThreadLocal ya alcanza para
 * que esto nunca contamine una petición real -- ver RelojTest, que confirma que sin haber
 * llamado nunca a fijarParaCargador, hoy()/ahora() siguen devolviendo el reloj real.
 */
public final class Reloj {

    public static final ZoneId ZONA = ZoneId.of("America/Asuncion");

    private static final ThreadLocal<LocalDateTime> INSTANTE_SIMULADO = new ThreadLocal<>();

    private Reloj() {
    }

    public static LocalDate hoy() {
        return ahora().toLocalDate();
    }

    public static LocalDateTime ahora() {
        LocalDateTime fijo = INSTANTE_SIMULADO.get();
        return fijo != null ? fijo : LocalDateTime.now(ZONA);
    }

    /** Uso exclusivo del cargador de datos de demostración (D-115) -- ver el comentario de arriba. */
    public static void fijarParaCargador(LocalDateTime instanteSimulado) {
        INSTANTE_SIMULADO.set(instanteSimulado);
    }

    /** Uso exclusivo del cargador de datos de demostración (D-115). */
    public static void liberarCargador() {
        INSTANTE_SIMULADO.remove();
    }
}
