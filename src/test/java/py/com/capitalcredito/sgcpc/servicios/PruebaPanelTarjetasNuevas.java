package py.com.capitalcredito.sgcpc.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.IndicadoresPanel;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;

/**
 * Las tres tarjetas nuevas del panel (28/10/2026, pedido de Joel): B45 "Solicitudes aprobadas
 * para formalizar" y B76 "Préstamos con cierre documental pendiente" (panel del OFICIAL), y B63
 * "Diferencias de caja por revisar" (panel del GERENTE/ADMINISTRADOR) -- los tres hallazgos del
 * "principio de paneles" de {@code docs/recorrido/00_LEEME.md}, catalogados en
 * {@code docs/recorrido/FASE2_PLAN.md}.
 *
 * <p>Qué verifica, que es lo que puede romperse en silencio:</p>
 * <ol>
 *   <li>Que cada indicador valga <b>exactamente</b> lo que dice la base, contado con un
 *       {@code SELECT} propio de esta prueba -- si alguien cambia el criterio de una de las tres
 *       consultas reutilizadas, acá se nota.</li>
 *   <li>Que el número <b>siga</b> al estado de la base: se fuerza la condición dentro de la
 *       transacción y el indicador tiene que moverse.</li>
 *   <li>Que cada indicador llegue <b>solo</b> al perfil que le corresponde (decisiones de Joel
 *       del 28/10/2026: B45/B76 solo el oficial -- ni el gerente ni el administrador; B63 el
 *       gerente y el administrador, nunca la cajera).</li>
 * </ol>
 *
 * <h3>Seguridad (DOGMA 1 y 2 de {@code docs/REGLAS_DESARROLLO.md})</h3>
 * <ul>
 *   <li>Aborta si la base no es {@code sgcpc_db_pruebas}, leído de la conexión abierta y no de un
 *       archivo que alguien pudo mover -- misma guarda que {@link PruebaEmisionFacturaPdf}.</li>
 *   <li>Una sola conexión por prueba, {@code autoCommit=false} y {@code rollback()} en un
 *       {@code finally}: revierte siempre, también si una verificación falla. Es la "prueba
 *       automática que no deja rastro" que pide el DOGMA 1 para lo que no se puede ver hoy en
 *       pantalla (hoy no hay ninguna solicitud aprobada sin formalizar ni ninguna caja con
 *       diferencia sin revisar).</li>
 *   <li>Solo hace {@code UPDATE} sobre filas que ya existen -- ningún {@code INSERT} ni
 *       {@code DELETE}, así que no toca los 13 triggers {@code BEFORE DELETE} de
 *       {@code REGLAS_DESARROLLO.md} §14 ni consume ninguna numeración.</li>
 *   <li>Al final vuelve a contar las tres cosas y verifica que la base quedó igual que antes.</li>
 * </ul>
 *
 * <p><b>No reemplaza la prueba en pantalla</b> (DOGMA 1: "verificado por código" no es
 * "probado"): el circuito real de las tres tarjetas lo prueba Joel en el navegador. Esta clase
 * es la red que avisa si un cambio futuro desconecta una tarjeta de su consulta.</p>
 *
 * <p>No corre con {@code mvn test} (el nombre no matchea {@code *Test}/{@code Test*} de
 * Surefire, igual que el resto de las pruebas con base de este proyecto). Se ejecuta a propósito
 * con {@code mvn -Dtest=PruebaPanelTarjetasNuevas test}.</p>
 */
public class PruebaPanelTarjetasNuevas {

    private static final String BASE_PERMITIDA = "sgcpc_db_pruebas";

    /** Los tres criterios, escritos de nuevo acá a propósito: si esta prueba llamara a los mismos
     * métodos que el panel, no podría detectar que uno de ellos cambió de criterio. */
    private static final String SQL_B45 = "SELECT count(*) FROM solicitudes_credito"
            + " WHERE estado_solicitud = 'APROBADA' AND NOT formalizada";
    private static final String SQL_B63 = "SELECT count(*) FROM sesiones_caja"
            + " WHERE estado_sesion = 'CERRADA' AND diferencia_arqueo <> 0"
            + " AND id_gerente_revisor IS NULL";
    private static final String SQL_B76 = "SELECT count(*) FROM prestamos"
            + " WHERE estado_prestamo = 'FINALIZADO' AND estado_cierre_documental <> 'COMPLETO'";

    private final ServicioPanel servicioPanel = new ServicioPanel();

    // =====================================================================
    // 1) Los tres indicadores valen lo que dice la base, y la siguen cuando cambia
    // =====================================================================

    @Test
    public void losTresIndicadoresSiguenElEstadoRealDeLaBase() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            long b45Antes = unValor(conexion, SQL_B45);
            long b63Antes = unValor(conexion, SQL_B63);
            long b76Antes = unValor(conexion, SQL_B76);
            System.out.println("Base ANTES: B45 (aprobadas sin formalizar) = " + b45Antes
                    + ", B63 (cajas con diferencia sin revisar) = " + b63Antes
                    + ", B76 (finalizados con cierre pendiente) = " + b76Antes);

            try {
                // La foto de hoy: cada tarjeta tiene que mostrar exactamente lo que hay.
                IndicadoresPanel oficial = indicadoresDe(conexion, PerfilUsuario.OFICIAL_CREDITO);
                IndicadoresPanel gerente = indicadoresDe(conexion, PerfilUsuario.GERENTE_ADMINISTRADOR);
                assertEquals("B45: el panel del oficial no muestra las aprobadas sin formalizar que hay.",
                        b45Antes, oficial.getAprobadasParaFormalizar());
                assertEquals("B76: el panel del oficial no muestra los cierres documentales pendientes que hay.",
                        b76Antes, oficial.getCierresDocumentalesPendientes());
                assertEquals("B63: el panel del gerente no muestra las diferencias de caja sin revisar que hay.",
                        b63Antes, gerente.getDiferenciasDeCajaSinRevisar());

                // B45: una solicitud ya formalizada vuelve a "aprobada sin formalizar" -> +1.
                long idSolicitud = unId(conexion, "SELECT s.id_solicitud FROM solicitudes_credito s"
                        + " WHERE s.estado_solicitud = 'APROBADA' AND s.formalizada"
                        // El índice parcial ux_solicitudes_una_abierta_por_cliente admite UNA sola
                        // solicitud abierta por cliente: se elige una cuyo cliente no tenga otra.
                        + " AND NOT EXISTS (SELECT 1 FROM solicitudes_credito o"
                        + "   WHERE o.id_cliente = s.id_cliente AND o.id_solicitud <> s.id_solicitud"
                        + "   AND (o.estado_solicitud IN ('BORRADOR', 'PENDIENTE_APROBACION')"
                        + "        OR (o.estado_solicitud = 'APROBADA' AND NOT o.formalizada)))"
                        + " ORDER BY s.id_solicitud LIMIT 1");
                if (idSolicitud < 0) {
                    fail("No hay ninguna solicitud APROBADA y formalizada usable para probar B45."
                            + " Hace falta al menos un crédito formalizado en la base.");
                }
                ejecutar(conexion, "UPDATE solicitudes_credito SET formalizada = false"
                        + " WHERE id_solicitud = " + idSolicitud);
                assertEquals("B45: la tarjeta del oficial no subió al aparecer una aprobada sin formalizar.",
                        b45Antes + 1,
                        indicadoresDe(conexion, PerfilUsuario.OFICIAL_CREDITO).getAprobadasParaFormalizar());

                // B63: una caja cerrada pasa a tener diferencia sin revisar -> +1; y al asignarle
                // un gerente revisor (RN-23) tiene que volver a bajar.
                long idSesion = unId(conexion, "SELECT id_sesion_caja FROM sesiones_caja"
                        + " WHERE estado_sesion = 'CERRADA'"
                        + " AND NOT (diferencia_arqueo <> 0 AND id_gerente_revisor IS NULL)"
                        + " ORDER BY id_sesion_caja LIMIT 1");
                if (idSesion < 0) {
                    fail("No hay ninguna sesión de caja CERRADA usable para probar B63."
                            + " Hace falta al menos un cierre de caja en la base.");
                }
                ejecutar(conexion, "UPDATE sesiones_caja SET diferencia_arqueo = -50000,"
                        + " id_gerente_revisor = NULL WHERE id_sesion_caja = " + idSesion);
                assertEquals("B63: la tarjeta del gerente no subió al aparecer una diferencia sin revisar.",
                        b63Antes + 1,
                        indicadoresDe(conexion, PerfilUsuario.GERENTE_ADMINISTRADOR).getDiferenciasDeCajaSinRevisar());

                long idGerente = idUsuarioDe(conexion, PerfilUsuario.GERENTE_ADMINISTRADOR);
                ejecutar(conexion, "UPDATE sesiones_caja SET id_gerente_revisor = " + idGerente
                        + " WHERE id_sesion_caja = " + idSesion);
                assertEquals("B63: la tarjeta del gerente no bajó después de revisar la diferencia.",
                        b63Antes,
                        indicadoresDe(conexion, PerfilUsuario.GERENTE_ADMINISTRADOR).getDiferenciasDeCajaSinRevisar());

                // B76: completar el cierre documental de un préstamo pendiente -> -1 (es lo que
                // Joel ve en pantalla hoy: 2 pasa a 1). Si no hubiera ninguno pendiente, se prueba
                // al revés -- que al quedar uno pendiente la tarjeta sube.
                long idPendiente = unId(conexion, "SELECT id_prestamo FROM prestamos"
                        + " WHERE estado_prestamo = 'FINALIZADO' AND estado_cierre_documental <> 'COMPLETO'"
                        + " ORDER BY id_prestamo LIMIT 1");
                if (idPendiente >= 0) {
                    ejecutar(conexion, "UPDATE prestamos SET estado_cierre_documental = 'COMPLETO'"
                            + " WHERE id_prestamo = " + idPendiente);
                    assertEquals("B76: la tarjeta del oficial no bajó al completar un cierre documental.",
                            b76Antes - 1,
                            indicadoresDe(conexion, PerfilUsuario.OFICIAL_CREDITO).getCierresDocumentalesPendientes());
                } else {
                    long idCompleto = unId(conexion, "SELECT id_prestamo FROM prestamos"
                            + " WHERE estado_prestamo = 'FINALIZADO' AND estado_cierre_documental = 'COMPLETO'"
                            + " ORDER BY id_prestamo LIMIT 1");
                    if (idCompleto < 0) {
                        fail("No hay ningún préstamo FINALIZADO usable para probar B76.");
                    }
                    ejecutar(conexion, "UPDATE prestamos SET estado_cierre_documental = 'PENDIENTE'"
                            + " WHERE id_prestamo = " + idCompleto);
                    assertEquals("B76: la tarjeta del oficial no subió al quedar un cierre documental pendiente.",
                            b76Antes + 1,
                            indicadoresDe(conexion, PerfilUsuario.OFICIAL_CREDITO).getCierresDocumentalesPendientes());
                }
            } finally {
                // SIEMPRE, también si una verificación falló: nada de esta prueba queda en la base.
                conexion.rollback();
            }

            long b45Despues = unValor(conexion, SQL_B45);
            long b63Despues = unValor(conexion, SQL_B63);
            long b76Despues = unValor(conexion, SQL_B76);
            System.out.println("Base DESPUÉS del rollback: B45 = " + b45Despues
                    + ", B63 = " + b63Despues + ", B76 = " + b76Despues);

            assertEquals("El rollback no devolvió las solicitudes como estaban.", b45Antes, b45Despues);
            assertEquals("El rollback no devolvió las sesiones de caja como estaban.", b63Antes, b63Despues);
            assertEquals("El rollback no devolvió los cierres documentales como estaban.", b76Antes, b76Despues);
        }
    }

    // =====================================================================
    // 2) Cada indicador llega SOLO al perfil que le corresponde
    // =====================================================================

    /**
     * Decisiones de Joel del 28/10/2026: B45 y B76 son solo del OFICIAL -- el administrador no las
     * ve, igual que hoy no ve "Mis solicitudes en curso" (aunque por D7 vea todo lo demás del
     * gerente); B63 es del gerente y del administrador, nunca de la cajera (revisar una diferencia
     * es {@code AUTORIZAR_REVERSION_O_DIFERENCIA}, permiso que ella no tiene).
     *
     * <p>Primero fuerza que las tres condiciones existan: con todo en 0 esta prueba no distinguiría
     * "este perfil no lo calcula" de "no hay nada que contar".</p>
     */
    @Test
    public void cadaIndicadorLlegaSoloAlPerfilQueLeCorresponde() throws Exception {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            abortarSiNoEsLaBaseDePruebas(conexion);
            conexion.setAutoCommit(false);

            try {
                forzarQueExistanLasTresCondiciones(conexion);

                long b45 = unValor(conexion, SQL_B45);
                long b63 = unValor(conexion, SQL_B63);
                long b76 = unValor(conexion, SQL_B76);
                System.out.println("Con las tres condiciones forzadas: B45 = " + b45
                        + ", B63 = " + b63 + ", B76 = " + b76);
                assertTrue("No se pudo forzar una solicitud aprobada sin formalizar.", b45 > 0);
                assertTrue("No se pudo forzar una caja con diferencia sin revisar.", b63 > 0);
                assertTrue("No se pudo forzar un cierre documental pendiente.", b76 > 0);

                IndicadoresPanel oficial = indicadoresDe(conexion, PerfilUsuario.OFICIAL_CREDITO);
                assertEquals("B45 tiene que llegarle al oficial.", b45, oficial.getAprobadasParaFormalizar());
                assertEquals("B76 tiene que llegarle al oficial.", b76, oficial.getCierresDocumentalesPendientes());
                assertEquals("B63 NO es del oficial.", 0, oficial.getDiferenciasDeCajaSinRevisar());

                IndicadoresPanel gerente = indicadoresDe(conexion, PerfilUsuario.GERENTE_ADMINISTRADOR);
                assertEquals("B63 tiene que llegarle al gerente.", b63, gerente.getDiferenciasDeCajaSinRevisar());
                assertEquals("B45 NO es del gerente (decisión de Joel del 28/10/2026).",
                        0, gerente.getAprobadasParaFormalizar());
                assertEquals("B76 NO es del gerente (decisión de Joel del 28/10/2026).",
                        0, gerente.getCierresDocumentalesPendientes());

                IndicadoresPanel administrador = indicadoresDe(conexion, PerfilUsuario.ADMINISTRADOR);
                assertEquals("B63 tiene que llegarle al administrador (D7, ve lo mismo que el gerente).",
                        b63, administrador.getDiferenciasDeCajaSinRevisar());
                assertEquals("B45 NO es del administrador (decisión de Joel del 28/10/2026).",
                        0, administrador.getAprobadasParaFormalizar());
                assertEquals("B76 NO es del administrador (decisión de Joel del 28/10/2026).",
                        0, administrador.getCierresDocumentalesPendientes());

                IndicadoresPanel cajera = indicadoresDe(conexion, PerfilUsuario.CAJERO);
                assertEquals("B63 NO es de la cajera: ella no revisa diferencias.",
                        0, cajera.getDiferenciasDeCajaSinRevisar());
                assertEquals("B45 NO es de la cajera.", 0, cajera.getAprobadasParaFormalizar());
                assertEquals("B76 NO es de la cajera.", 0, cajera.getCierresDocumentalesPendientes());
            } finally {
                conexion.rollback();
            }
        }
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    private void forzarQueExistanLasTresCondiciones(Connection conexion) throws Exception {
        if (unValor(conexion, SQL_B45) == 0) {
            long idSolicitud = unId(conexion, "SELECT s.id_solicitud FROM solicitudes_credito s"
                    + " WHERE s.estado_solicitud = 'APROBADA' AND s.formalizada"
                    + " AND NOT EXISTS (SELECT 1 FROM solicitudes_credito o"
                    + "   WHERE o.id_cliente = s.id_cliente AND o.id_solicitud <> s.id_solicitud"
                    + "   AND (o.estado_solicitud IN ('BORRADOR', 'PENDIENTE_APROBACION')"
                    + "        OR (o.estado_solicitud = 'APROBADA' AND NOT o.formalizada)))"
                    + " ORDER BY s.id_solicitud LIMIT 1");
            if (idSolicitud < 0) {
                fail("No hay ninguna solicitud APROBADA y formalizada usable para forzar B45.");
            }
            ejecutar(conexion, "UPDATE solicitudes_credito SET formalizada = false"
                    + " WHERE id_solicitud = " + idSolicitud);
        }
        if (unValor(conexion, SQL_B63) == 0) {
            long idSesion = unId(conexion, "SELECT id_sesion_caja FROM sesiones_caja"
                    + " WHERE estado_sesion = 'CERRADA' ORDER BY id_sesion_caja LIMIT 1");
            if (idSesion < 0) {
                fail("No hay ninguna sesión de caja CERRADA usable para forzar B63.");
            }
            ejecutar(conexion, "UPDATE sesiones_caja SET diferencia_arqueo = -50000,"
                    + " id_gerente_revisor = NULL WHERE id_sesion_caja = " + idSesion);
        }
        if (unValor(conexion, SQL_B76) == 0) {
            long idPrestamo = unId(conexion, "SELECT id_prestamo FROM prestamos"
                    + " WHERE estado_prestamo = 'FINALIZADO' ORDER BY id_prestamo LIMIT 1");
            if (idPrestamo < 0) {
                fail("No hay ningún préstamo FINALIZADO usable para forzar B76.");
            }
            ejecutar(conexion, "UPDATE prestamos SET estado_cierre_documental = 'PENDIENTE'"
                    + " WHERE id_prestamo = " + idPrestamo);
        }
    }

    private IndicadoresPanel indicadoresDe(Connection conexion, PerfilUsuario perfil) throws Exception {
        return servicioPanel.calcularIndicadores(conexion, idUsuarioDe(conexion, perfil), perfil);
    }

    /** El id real del usuario de ese perfil: {@code recalcularMorosidadDiaria} escribe en
     * {@code auditoria_operaciones} con ese id, así que no sirve uno inventado. */
    private long idUsuarioDe(Connection conexion, PerfilUsuario perfil) throws Exception {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT id_usuario FROM usuarios WHERE perfil_usuario = ? ORDER BY id_usuario LIMIT 1")) {
            sentencia.setString(1, perfil.name());
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    fail("No hay ningún usuario con perfil " + perfil.name() + " en la base.");
                }
                return resultado.getLong(1);
            }
        }
    }

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

    private void ejecutar(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate(sql);
        }
    }
}
