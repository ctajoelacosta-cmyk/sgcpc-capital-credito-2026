package py.com.capitalcredito.sgcpc.infraestructura;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Abre conexiones JDBC a PostgreSQL. Sin pool y sin DataSource JNDI (Doc. 00 Sec. 14.4): cada
 * llamada abre una conexion nueva, que el que la usa debe cerrar.
 *
 * Portabilidad (pedido de Joel): el docente despliega el mismo .war en su propia PostgreSQL 17,
 * con otro usuario/contraseña/nombre de base, sin recompilar -- bd.properties empaquetado (con
 * los datos de ESTA PC) no le sirve. La configuración se resuelve en 3 niveles, de más a menos
 * específico, sin romper nada de lo que ya funciona en esta PC (que cae siempre al nivel 3):
 *   1. Variables de entorno (SGCPC_BD_URL/SGCPC_BD_USUARIO/SGCPC_BD_CONTRASENA/SGCPC_BD_ENTORNO).
 *   2. Archivo externo en ${catalina.base}/conf/sgcpc.properties -- catalina.base lo define
 *      Tomcat solo, así que esta ruta es la misma fórmula en cualquier PC/servidor, sin que haga
 *      falta escribir una ruta fija de Windows ni de Linux.
 *   3. bd.properties empaquetado en el .war (el de siempre).
 * descripcionFuenteConfiguracionActiva()/urlSinContrasena() son para error-bd.jsp, que necesita
 * decir CUÁL de los tres se está usando, sin tocar la base (ver ErrorConexionBD).
 *
 * Registra el driver con Class.forName en vez de confiar solo en el
 * autoregistro por ServiceLoader (JDBC 4): Tomcat desregistra los drivers
 * de un webapp al desplegarlo (WebappClassLoaderBase.clearReferencesJdbc,
 * para evitar fugas de memoria entre redespliegues), y en NetBeans, con
 * varios redespliegues seguidos dentro del mismo proceso de Tomcat, eso
 * puede dejar a DriverManager sin el driver registrado para el classloader
 * del webapp actual. Class.forName fuerza la carga de la clase (y su
 * bloque estatico, que llama DriverManager.registerDriver) con el
 * classloader de esta aplicacion, sin depender de ese registro automatico.
 */
public final class FabricaConexionBD {

    private static final String ARCHIVO_PROPIEDADES_CLASSPATH = "configuracion/bd.properties";
    private static final String CLASE_DRIVER = "org.postgresql.Driver";

    private static String fuenteConfiguracionActiva;
    private static final Properties PROPIEDADES = cargarPropiedades();

    static {
        try {
            Class.forName(CLASE_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se encontro el driver JDBC " + CLASE_DRIVER
                    + " (revisar que postgresql-42.7.1.jar este en WEB-INF/lib).", e);
        }
    }

    private FabricaConexionBD() {
    }

    /**
     * D-113 (Bloque 7/E10, separación del humo): true solo si la configuración activa trae la
     * marca "entorno=pruebas" -- nunca compara el nombre de la base (a pedido de Joel: la marca
     * es explícita, no se infiere). La usa cabecera.jsp para mostrar el cartel "BASE DE PRUEBAS";
     * sin la marca, nunca aparece, ni siquiera contra sgcpc_db_restaurada o cualquier otra base.
     */
    public static boolean esBaseDePruebas() {
        return "pruebas".equals(PROPIEDADES.getProperty("entorno"));
    }

    /**
     * D-110 (Bloque 7/E10, Sec. 14.4): fija la zona horaria de la sesión a America/Asuncion en
     * cada conexión -- por conexión, no en postgresql.conf ni en la base (esto es defensa
     * primaria; el ALTER DATABASE de bd/01_esquema.sql es la segunda capa, por si algo abre una
     * conexión sin pasar por acá, ej. pgAdmin). Sin esto, "ahora" en SQL (now()/CURRENT_TIMESTAMP/
     * CURRENT_DATE, usado en varios DAO) queda en la zona horaria por defecto del servidor de
     * PostgreSQL, que puede no coincidir con la de Reloj (Java) -- ver AVANCE.md D-110.
     *
     * Portabilidad (pedido de Joel): si DriverManager.getConnection falla (host/puerto/usuario/
     * contraseña/nombre de base mal configurados, o PostgreSQL caído), no se deja como un
     * SQLException más -- se envuelve en ErrorConexionBD (RuntimeException a propósito) para que
     * atraviese los catch (SQLException) de los controladores sin que lo confundan con el error
     * de una operación puntual, y llegue directo al <error-page> dedicado de web.xml (error-bd.jsp).
     */
    public static Connection obtenerConexion() throws SQLException {
        String url = PROPIEDADES.getProperty("bd.url");
        String usuario = PROPIEDADES.getProperty("bd.usuario");
        String contrasena = PROPIEDADES.getProperty("bd.contrasena");
        Connection conexion;
        try {
            conexion = DriverManager.getConnection(url, usuario, contrasena);
        } catch (SQLException error) {
            throw new ErrorConexionBD(error);
        }
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET TIME ZONE 'America/Asuncion'");
        }
        return conexion;
    }

    /** Para error-bd.jsp: cuál de los 3 niveles está activo, sin exponer la contraseña. */
    public static String descripcionFuenteConfiguracionActiva() {
        return fuenteConfiguracionActiva;
    }

    /** Para error-bd.jsp: a dónde está intentando conectarse (url + usuario), nunca la contraseña. */
    public static String descripcionDestinoConexion() {
        String url = PROPIEDADES.getProperty("bd.url");
        String usuario = PROPIEDADES.getProperty("bd.usuario");
        return (url == null ? "(sin bd.url)" : url) + " — usuario " + (usuario == null ? "(sin bd.usuario)" : usuario);
    }

    /** Ruta de ${catalina.base}/conf/sgcpc.properties -- expuesta para que error-bd.jsp pueda
     * decir exactamente qué archivo revisar, sin adivinar nada distinto de lo que ya resolvió
     * acá. catalina.base lo define Tomcat solo: misma fórmula en cualquier PC o servidor. */
    public static String rutaArchivoExterno() {
        String catalinaBase = System.getProperty("catalina.base", ".");
        return catalinaBase + File.separator + "conf" + File.separator + "sgcpc.properties";
    }

    private static Properties cargarPropiedades() {
        Properties porVariablesEntorno = cargarDesdeVariablesEntorno();
        if (porVariablesEntorno != null) {
            fuenteConfiguracionActiva = "variables de entorno (SGCPC_BD_URL/SGCPC_BD_USUARIO/SGCPC_BD_CONTRASENA)";
            return porVariablesEntorno;
        }
        Properties porArchivoExterno = cargarDesdeArchivoExterno();
        if (porArchivoExterno != null) {
            fuenteConfiguracionActiva = "archivo externo (" + rutaArchivoExterno() + ")";
            return porArchivoExterno;
        }
        fuenteConfiguracionActiva = "bd.properties empaquetado en el .war ("
                + ARCHIVO_PROPIEDADES_CLASSPATH + ")";
        return cargarDesdeClasspath();
    }

    /** Nivel 1. null si SGCPC_BD_URL no está seteada -- recién ahí se consideran las otras dos
     * variables (vacías cuentan como "no seteadas", nunca rompen el arranque). */
    private static Properties cargarDesdeVariablesEntorno() {
        String url = System.getenv("SGCPC_BD_URL");
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        Properties propiedades = new Properties();
        propiedades.setProperty("bd.url", url.trim());
        propiedades.setProperty("bd.usuario", valorOVacio(System.getenv("SGCPC_BD_USUARIO")));
        propiedades.setProperty("bd.contrasena", valorOVacio(System.getenv("SGCPC_BD_CONTRASENA")));
        String entorno = System.getenv("SGCPC_BD_ENTORNO");
        if (entorno != null && !entorno.trim().isEmpty()) {
            propiedades.setProperty("entorno", entorno.trim());
        }
        return propiedades;
    }

    private static String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    /** Nivel 2. null si el archivo no existe o no se puede leer -- en ese caso sigue al nivel 3
     * (bd.properties empaquetado), nunca rompe el arranque por un archivo externo mal armado. */
    private static Properties cargarDesdeArchivoExterno() {
        File archivo = new File(rutaArchivoExterno());
        if (!archivo.isFile()) {
            return null;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = new FileInputStream(archivo)) {
            propiedades.load(entrada);
            return propiedades;
        } catch (IOException error) {
            return null;
        }
    }

    /** Nivel 3 (el de siempre): bd.properties empaquetado dentro del .war. */
    private static Properties cargarDesdeClasspath() {
        Properties propiedades = new Properties();
        try (InputStream entrada = FabricaConexionBD.class.getClassLoader()
                .getResourceAsStream(ARCHIVO_PROPIEDADES_CLASSPATH)) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "No se encontro " + ARCHIVO_PROPIEDADES_CLASSPATH + " en el classpath. "
                        + "Copiar bd.properties.ejemplo a bd.properties y completar los datos.");
            }
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ARCHIVO_PROPIEDADES_CLASSPATH, e);
        }
        return propiedades;
    }
}
