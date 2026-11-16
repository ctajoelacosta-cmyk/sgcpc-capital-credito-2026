-- =====================================================================
-- E10_15_rf39_tope_legal_y_nombre_parametro.sql
-- Migración de DATOS (no de estructura) -- RF39 (análisis + decisiones de
-- Joel, 13/10/2026). Dos cambios sin relación entre sí, en el mismo
-- script porque Joel pidió aplicarlos juntos:
--
-- 1) Tope legal de TEA, una sola fuente (E1). Hasta ahora había dos
--    registros separados: la clave `credito.tea_tope_legal` en
--    parametros_sistema (usada solo por ServicioParametros.
--    validarTeaEmpresa, al guardar un cambio de credito.tea_empresa) y la
--    fila vigente de `limites_tasa_usura` (usada por el cotizador, la
--    aprobación y la formalización -- las 3 revalidaciones de RN-15), sin
--    ningún código que los mantuviera sincronizados. ServicioParametros.
--    validarTeaEmpresa ahora lee de limites_tasa_usura, igual que los
--    otros 3 lugares -- esta migración cierra la vigencia de
--    credito.tea_tope_legal para que deje de listarse como parámetro
--    editable en /parametros (ya no la lee ningún código).
--
-- 2) Nombre visible de credito.cuotas_alerta_garante (E4). El nombre
--    "Cuotas vencidas para alertar al garante" era engañoso: el
--    parámetro también alimenta la cláusula de vencimiento anticipado del
--    contrato y del pagaré (ServicioFormalizacion, pagare.jrxml), sin
--    relación con el garante. Solo cambia el nombre_parametro -- la
--    clave y los .jrxml no se tocan.
--
-- No cambia ninguna estructura (no hace falta comparar_esquemas.sh), pero
-- SÍ se actualizó también bd/03_parametros.sql -- el nombre nuevo de
-- credito.cuotas_alerta_garante, y la fila de credito.tea_tope_legal ya NO
-- se siembra (decisión de Joel, 13/10/2026: limites_tasa_usura, sembrada
-- más arriba en el mismo script, queda como única fuente). Una base nueva
-- creada con recrear_base.sh nace ya sin esa fila y con el nombre correcto.
-- Esta migración sigue siendo necesaria para una base YA EXISTENTE creada
-- con la siembra vieja (ej. sgcpc_db, la base real, todavía pendiente) --
-- ver docs/funcionamiento/RF39_parametros.md.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas, y recién
-- después de que Joel apruebe el comando en pantalla. sgcpc_db (la real)
-- NO se toca -- queda anotado en "Antes de entregar" de AVANCE.md.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_15_rf39_tope_legal_y_nombre_parametro.sql
--
-- Todo en una sola transacción, e idempotente: correrlo de nuevo no
-- cambia nada más (el primer UPDATE ya no encuentra ninguna fila con
-- vigente_hasta IS NULL; el segundo deja escrito el mismo texto).
-- =====================================================================

BEGIN;

-- 1) Tope legal: cierra la vigencia de credito.tea_tope_legal (deja de
--    ser un parámetro editable/vigente; limites_tasa_usura es la única
--    fuente a partir de ahora).
UPDATE parametros_sistema
SET vigente_hasta = CURRENT_DATE
WHERE clave_parametro = 'credito.tea_tope_legal' AND vigente_hasta IS NULL;

-- 2) Nombre visible de credito.cuotas_alerta_garante (solo el nombre;
--    la clave y el valor no cambian).
UPDATE parametros_sistema
SET nombre_parametro = 'Cuotas vencidas para el vencimiento anticipado'
WHERE clave_parametro = 'credito.cuotas_alerta_garante' AND vigente_hasta IS NULL;

COMMIT;
