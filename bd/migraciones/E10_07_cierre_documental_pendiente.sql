-- =====================================================================
-- E10_07_cierre_documental_pendiente.sql
-- Migración de DATOS (ningún cambio de estructura) para E-13 (pedido de
-- Joel, encontrado en E-12): corrige los préstamos ya FINALIZADO que
-- quedaron con estado_cierre_documental = 'NO_APLICA' en vez de
-- 'PENDIENTE' -- el bug real era de código, no de datos (PrestamoDAO.
-- marcarFinalizado nunca tocaba esa columna, así que el valor sembrado al
-- crear el préstamo, D-68, quedaba ahí para siempre). E-13 ya corrigió el
-- código (marcarFinalizado pasa a PENDIENTE; revertirFinalizacion vuelve
-- a NO_APLICA); esta migración es solo para poner al día los préstamos
-- que ya se finalizaron ANTES de ese arreglo.
--
-- No cambia ningún comportamiento ni ninguna pantalla: tanto la bandeja
-- de /formalizacion (PrestamoDAO.listarFinalizadosConCierrePendiente)
-- como el informe de Cierres documentales (ServicioReportes.
-- listarCierresDocumentalesPorRango) ya tratan NO_APLICA y PENDIENTE
-- exactamente igual (cualquier valor distinto de 'COMPLETO' cuenta como
-- "pendiente de devolución") -- esta migración es solo para que el DATO
-- quede correcto de cara al futuro (por ejemplo, si algún día se agrega
-- una consulta que SÍ distinga NO_APLICA de PENDIENTE).
--
-- NO se corre en una instalación nueva: desde E-13, el código ya deja
-- bien el dato en marcarFinalizado para cualquier préstamo que se
-- finalice de acá en adelante; esta migración solo repara el historial.
--
-- NO SE CORRE TODAVÍA EN NINGUNA BASE (ni sgcpc_db_pruebas ni sgcpc_db) --
-- pedido explícito de Joel: queda para cuando corresponda (F1/F2). Cuando
-- se decida aplicarla, el comando es:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d <base que corresponda> -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_07_cierre_documental_pendiente.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

UPDATE prestamos
SET estado_cierre_documental = 'PENDIENTE'
WHERE estado_prestamo = 'FINALIZADO'
  AND estado_cierre_documental = 'NO_APLICA';

COMMIT;
