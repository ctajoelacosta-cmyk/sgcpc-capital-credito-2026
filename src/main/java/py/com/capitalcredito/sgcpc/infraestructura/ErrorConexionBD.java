package py.com.capitalcredito.sgcpc.infraestructura;

/**
 * Portabilidad (pedido de Joel): no se pudo abrir ninguna conexión a PostgreSQL (host/puerto/
 * usuario/contraseña/nombre de base mal configurados, o el servicio caído) -- un problema de
 * configuración del servidor, no el error de una operación puntual. RuntimeException a propósito
 * (no SQLException): así atraviesa los catch (SQLException) de los controladores sin que ninguno
 * la trate como un error de negocio común, y llega directo al <error-page> dedicado de web.xml
 * (error-bd.jsp), que dice cuál de los 3 niveles de configuración está activo
 * (FabricaConexionBD.descripcionFuenteConfiguracionActiva) para que se sepa dónde corregir.
 */
public class ErrorConexionBD extends RuntimeException {

    public ErrorConexionBD(Throwable causa) {
        super("No se pudo conectar a la base de datos.", causa);
    }
}
