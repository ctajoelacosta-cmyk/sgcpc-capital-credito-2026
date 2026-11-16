-- =====================================================================
-- E10_13_parametro_monto_maximo_arqueo.sql
-- Migración de DATOS (no de estructura) -- RF19 (pedido de Joel,
-- 11/10/2026): tope razonable del efectivo contado al cerrar caja
-- (ServicioCaja.cerrarCaja), para frenar un error de tipeo (ceros de más)
-- antes de que quede guardado. Ver docs/funcionamiento/RF19_cierre_arqueo.md.
--
-- No cambia ninguna estructura (no hace falta comparar_esquemas.sh), pero
-- SÍ se actualizó también bd/03_parametros.sql -- una base nueva creada
-- con recrear_base.sh nace ya con este parámetro.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db
-- (la real) NO se toca -- queda anotado en Pendientes de
-- RF19_cierre_arqueo.md para cuando corresponda aplicarlo ahí.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_13_parametro_monto_maximo_arqueo.sql
--
-- Todo en una sola transacción, e idempotente: correrlo de nuevo no
-- inserta una segunda fila (guardado con WHERE NOT EXISTS).
-- =====================================================================

BEGIN;

INSERT INTO parametros_sistema (clave_parametro, nombre_parametro, valor_parametro, tipo_valor,
    vigente_desde, modificado_por, fecha_modificacion)
SELECT 'caja.monto_maximo_arqueo', 'Efectivo contado máximo razonable al cerrar caja (Gs.)',
    '500000000', 'ENTERO', CURRENT_DATE,
    (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM parametros_sistema WHERE clave_parametro = 'caja.monto_maximo_arqueo'
);

COMMIT;
