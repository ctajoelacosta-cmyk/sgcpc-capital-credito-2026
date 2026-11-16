package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * B84 (decisión de Joel, 28/10/2026, {@code docs/recorrido/FASE2_PLAN.md} área E): RN-01 "un
 * cliente, un préstamo a la vez" revalidada en los dos momentos que le faltaban, APROBAR
 * ({@code ServicioAprobacionCredito.validarSinPrestamoAbierto}) y FORMALIZAR
 * ({@code ServicioFormalizacion.validarSinOtroPrestamoAbierto}). Los otros dos momentos (crear y
 * enviar a aprobación) ya estaban desde D-61/D-68.
 *
 * <p>Qué verifica, que es lo que puede romperse en silencio:</p>
 * <ol>
 *   <li>Que las dos guardas <b>rechacen</b> cuando el cliente tiene un préstamo abierto.</li>
 *   <li>Que la guarda de formalizar <b>no se bloquee a sí misma</b>: con el préstamo abierto siendo
 *       el de la PROPIA solicitud, tiene que dejar pasar. Es la pregunta de Joel al aprobar el plan
 *       y la única parte del cambio que no se puede ver en pantalla.</li>
 *   <li>Que el <b>caso normal</b> (sin ningún préstamo abierto) siga pasando por las dos.</li>
 *   <li>Que el criterio de "préstamo abierto" sea el de RN-01 y no otro: bloquean los tres estados
 *       {@code PENDIENTE_DESEMBOLSO}, {@code ACTIVO} y {@code EN_MORA}, y no bloquean
 *       {@code FINALIZADO} ni {@code ANULADO_ANTES_DESEMBOLSO}.</li>
 * </ol>
 *
 * <h3>Seguridad (DOGMA 1 y 2 de {@code docs/REGLAS_DESARROLLO.md})</h3>
 * <ul>
 *   <li>Aborta si la base no es {@code sgcpc_db_pruebas}, leído de la conexión abierta y no de un
 *       archivo que alguien pudo mover -- misma guarda que {@link PruebaPanelTarjetasNuevas}.</li>
 *   <li>Una sola conexión por prueba, {@code autoCommit=false} y {@code rollback()} en un
 *       {@code finally}: revierte siempre, también si una verificación falla.</li>
 *   <li>Solo hace {@code UPDATE} sobre filas que ya existen -- ningún {@code INSERT} ni
 *       {@code DELETE}, así que no toca los 13 triggers {@code BEFORE DELETE} de
 *       {@code REGLAS_DESARROLLO.md} §14 ni consume ninguna numeración.</li>
 *   <li>Al final abre una <b>conexión nueva</b> y verifica contra la base ya comiteada que el
 *       estado del préstamo y el conteo de préstamos abiertos quedaron idénticos.</li>
 * </ul>
 *
 * <h3>Qué NO cubre, dicho de frente</h3>
 * <p>{@code aprobar()} y {@code generarDocumentos()} abren <b>su propia conexión</b>
 * ({@code FabricaConexionBD.obtenerConexion()}), así que un escenario armado en una transacción sin
 * commit les es invisible. Esta prueba ejercita entonces <b>el método de guarda que esos dos
 * llaman</b>, con la conexión de la prueba: es el mismo código que corre en producción, pero no la
 * llamada completa. La alternativa sería comitear datos de prueba (contra el rollback total) o
 * cambiarle la firma a los dos servicios (no es cambio mínimo). El circuito completo lo prueba Joel
 * en pantalla -- DOGMA 1: "verificado por código" no es "probado".</p>
 *
 * <p>No corre con {@code mvn test} (el nombre no matchea {@code *Test}/{@code Test*} de Surefire,
 * igual que el resto de las pruebas con base de este proyecto). Se ejecuta a propósito con
 * {@code mvn -Dtest=PruebaRN01AprobarFormalizar test}.</p>
 */
public class PruebaRN01AprobarFormalizar {

    private static final String BASE_PERMITIDA = "sgcpc_db_pruebas";

    /** El criterio de RN-01, escrito de nuevo acá a propósito: si esta prueba llamara al mismo
     * método que las guardas, no podría detectar que el criterio cambió. */
    private static final String SQL_PRESTAMOS_ABIERTOS = "SELECT count(*) FROM prestamos"
            + " WHERE estado_prestamo NOT IN ('FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO')";

    private static final String MENSAJE_APROBAR =
            "El cliente ya tiene un préstamo no cerrado. No se puede aprobar. Para pedir otro "
            + "crédito, primero debe terminar de pagarlo o cancelarlo.";
    private static final String MENSAJE_FORMALIZAR =
            "El cliente ya tiene un préstamo no cerrado. No se puede formalizar. Para pedir otro "
            + "crédito, primero debe terminar de pagarlo o cancelarlo.";

    private final ServicioAprobacionCredito servicioAprobacion = new ServicioAprobacionCredito();
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();

    // =====================================================================
    // 1) Rechaza, deja pasar al propio, y deja pasar el caso normal
    // =====================================================================

    @Test
    public void lasDosGuardasRechazanElPrestamoAbiertoYDejanPasarElPropio() throws Exception {
        long abiertosAntes;
        long idPrestamo;
        String estadoAntes;

        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            abiertosAntes = unValor(conexion, SQL_PRESTAMOS_ABIERTOS);

            // Un préstamo CERRADO cuyo cliente no tenga ningún otro préstamo abierto: es el único
            // punto de partida desde el que se puede probar el caso normal Y, con un solo UPDATE,
            // el caso bloqueado.
            idPrestamo = unId(conexion, "SELECT p.id_prestamo FROM prestamos p"
                    + " JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud"
                    + " WHERE p.estado_prestamo IN ('FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO')"
                    + " AND NOT EXISTS (SELECT 1 FROM prestamos o"
                    + "   JOIN solicitudes_credito os ON os.id_solicitud = o.id_solicitud"
                    + "   WHERE os.id_cliente = s.id_cliente AND o.id_prestamo <> p.id_prestamo"
                    + "   AND o.estado_prestamo NOT IN ('FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO'))"
                    + " ORDER BY p.id_prestamo LIMIT 1");
            if (idPrestamo < 0) {
                fail("No hay ningún préstamo cerrado cuyo cliente esté libre de otros préstamos"
                        + " abiertos. Hace falta al menos un crédito FINALIZADO en la base.");
            }
            long idSolicitudDuena = unId(conexion, "SELECT id_solicitud FROM prestamos"
                    + " WHERE id_prestamo = " + idPrestamo);
            long idCliente = unId(conexion, "SELECT id_cliente FROM solicitudes_credito"
                    + " WHERE id_solicitud = " + idSolicitudDuena);
            estadoAntes = unTexto(conexion, "SELECT estado_prestamo FROM prestamos"
                    + " WHERE id_prestamo = " + idPrestamo);

            // Otra solicitud del MISMO cliente: es la que haría el papel de "la que se está
            // formalizando" en la vida real (hoy, SOL-2026-0004 con CC-2026-00001 ya cerrado). Si
            // no hubiera, alcanza un id distinto cualquiera: la guarda solo compara ids.
            long idOtraSolicitud = unId(conexion, "SELECT id_solicitud FROM solicitudes_credito"
                    + " WHERE id_cliente = " + idCliente + " AND id_solicitud <> " + idSolicitudDuena
                    + " ORDER BY id_solicitud LIMIT 1");
            if (idOtraSolicitud < 0) {
                idOtraSolicitud = idSolicitudDuena + 1_000_000L;
            }

            System.out.println("Base ANTES: préstamos abiertos = " + abiertosAntes
                    + "; préstamo elegido id " + idPrestamo + " (estado " + estadoAntes
                    + ", solicitud dueña " + idSolicitudDuena + ", cliente " + idCliente
                    + "); otra solicitud del mismo cliente = " + idOtraSolicitud);

            try {
                // --- Caso D: el caso normal, sin tocar nada todavía. Las dos guardas pasan.
                servicioAprobacion.validarSinPrestamoAbierto(conexion, idCliente);
                servicioFormalizacion.validarSinOtroPrestamoAbierto(conexion, idCliente, idOtraSolicitud);

                // Ahora el cliente SÍ tiene un préstamo abierto (el mismo, puesto en ACTIVO).
                ejecutar(conexion, "UPDATE prestamos SET estado_prestamo = 'ACTIVO'"
                        + " WHERE id_prestamo = " + idPrestamo);
                assertEquals("El escenario no quedó armado: debería haber un préstamo abierto más.",
                        abiertosAntes + 1, unValor(conexion, SQL_PRESTAMOS_ABIERTOS));

                // --- Caso A: aprobar rechaza, con el texto acordado con Joel el 28/10/2026.
                NegocioException errorAprobar = assertThrows(
                        "RN-01 no bloqueó al APROBAR con un préstamo abierto del cliente.",
                        NegocioException.class,
                        () -> servicioAprobacion.validarSinPrestamoAbierto(conexion, idCliente));
                assertEquals(MENSAJE_APROBAR, errorAprobar.getMessage());

                // --- Caso B: formalizar rechaza cuando el préstamo abierto es de OTRA solicitud.
                final long idQueSeFormaliza = idOtraSolicitud;
                NegocioException errorFormalizar = assertThrows(
                        "RN-01 no bloqueó al FORMALIZAR con un préstamo abierto de otra solicitud.",
                        NegocioException.class,
                        () -> servicioFormalizacion.validarSinOtroPrestamoAbierto(
                                conexion, idCliente, idQueSeFormaliza));
                assertEquals(MENSAJE_FORMALIZAR, errorFormalizar.getMessage());

                // --- Caso C (el punto de Joel): si el único préstamo abierto es el de la PROPIA
                // solicitud, formalizar NO se bloquea a sí misma.
                servicioFormalizacion.validarSinOtroPrestamoAbierto(conexion, idCliente, idSolicitudDuena);
            } finally {
                // SIEMPRE, también si una verificación falló: nada de esta prueba queda en la base.
                conexion.rollback();
            }
        }

        // Conexión NUEVA: se verifica contra la base ya comiteada, no contra la transacción propia.
        try (Connection verificacion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(verificacion);
            long abiertosDespues = unValor(verificacion, SQL_PRESTAMOS_ABIERTOS);
            String estadoDespues = unTexto(verificacion, "SELECT estado_prestamo FROM prestamos"
                    + " WHERE id_prestamo = " + idPrestamo);
            System.out.println("Base DESPUÉS del rollback: préstamos abiertos = " + abiertosDespues
                    + "; préstamo id " + idPrestamo + " en estado " + estadoDespues);

            assertEquals("El rollback no devolvió el conteo de préstamos abiertos como estaba.",
                    abiertosAntes, abiertosDespues);
            assertEquals("El rollback no devolvió el estado del préstamo como estaba.",
                    estadoAntes, estadoDespues);
        }
    }

    // =====================================================================
    // 2) El criterio es el de RN-01: 3 estados bloquean, 2 no
    // =====================================================================

    /**
     * Si alguien cambiara el {@code NOT IN} de
     * {@code SolicitudDAO.buscarPorClienteConPrestamoAbierto}, acá se nota: recorre los 5 estados
     * posibles de {@code EstadoPrestamo} sobre el mismo préstamo y verifica cuáles bloquean.
     */
    @Test
    public void losTresEstadosAbiertosBloqueanYLosDosCerradosNo() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            long idPrestamo = unId(conexion, "SELECT p.id_prestamo FROM prestamos p"
                    + " JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud"
                    + " WHERE NOT EXISTS (SELECT 1 FROM prestamos o"
                    + "   JOIN solicitudes_credito os ON os.id_solicitud = o.id_solicitud"
                    + "   WHERE os.id_cliente = s.id_cliente AND o.id_prestamo <> p.id_prestamo"
                    + "   AND o.estado_prestamo NOT IN ('FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO'))"
                    + " ORDER BY p.id_prestamo LIMIT 1");
            if (idPrestamo < 0) {
                fail("No hay ningún préstamo cuyo cliente esté libre de otros préstamos abiertos.");
            }
            long idCliente = unId(conexion, "SELECT s.id_cliente FROM solicitudes_credito s"
                    + " JOIN prestamos p ON p.id_solicitud = s.id_solicitud"
                    + " WHERE p.id_prestamo = " + idPrestamo);
            String estadoAntes = unTexto(conexion, "SELECT estado_prestamo FROM prestamos"
                    + " WHERE id_prestamo = " + idPrestamo);
            System.out.println("Préstamo id " + idPrestamo + " (cliente " + idCliente
                    + ") en estado " + estadoAntes + " antes de recorrer los 5 estados.");

            try {
                for (String estado : new String[] {"PENDIENTE_DESEMBOLSO", "ACTIVO", "EN_MORA"}) {
                    ejecutar(conexion, "UPDATE prestamos SET estado_prestamo = '" + estado
                            + "' WHERE id_prestamo = " + idPrestamo);
                    assertThrows("RN-01 debería bloquear con el préstamo en " + estado + ".",
                            NegocioException.class,
                            () -> servicioAprobacion.validarSinPrestamoAbierto(conexion, idCliente));
                    System.out.println("  " + estado + ": bloquea, como manda RN-01.");
                }
                for (String estado : new String[] {"FINALIZADO", "ANULADO_ANTES_DESEMBOLSO"}) {
                    ejecutar(conexion, "UPDATE prestamos SET estado_prestamo = '" + estado
                            + "' WHERE id_prestamo = " + idPrestamo);
                    servicioAprobacion.validarSinPrestamoAbierto(conexion, idCliente);
                    System.out.println("  " + estado + ": no bloquea, como manda RN-01.");
                }
            } finally {
                conexion.rollback();
            }

            assertEquals("El rollback no devolvió el estado del préstamo como estaba.", estadoAntes,
                    unTexto(conexion, "SELECT estado_prestamo FROM prestamos WHERE id_prestamo = " + idPrestamo));
        }
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    /** Guarda dura: la URL sale de la conexión abierta, no de un archivo que alguien pudo mover. */
    private void abortarSiNoEsLaBaseDePruebas(Connection conexion) throws Exception {
        String url = conexion.getMetaData().getURL();
        String catalogo = conexion.getCatalog();
        if (url == null || !url.endsWith("/" + BASE_PERMITIDA) || !BASE_PERMITIDA.equals(catalogo)) {
            fail("ABORTADO: esta prueba solo corre contra " + BASE_PERMITIDA
                    + ". La conexión activa apunta a \"" + url + "\" (catálogo \"" + catalogo
                    + "\"). Revisar bd.properties.");
        }
    }

    private long unValor(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    /** El id que devuelve la consulta, o -1 si no devolvió ninguna fila. */
    private long unId(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            return resultado.next() ? resultado.getLong(1) : -1L;
        }
    }

    private String unTexto(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    private void ejecutar(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }
}
