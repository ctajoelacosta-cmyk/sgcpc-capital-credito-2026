-- =====================================================================
-- E10_14_plan_pagos_firmado.sql
-- Migración de DATOS (no de estructura) -- RF13 (hallazgo de Joel en la
-- reprueba del Lote 1, 11/10/2026): el anteproyecto firmado pide generar
-- contrato, pagaré Y plan de cuotas, y controlar que los TRES estén
-- firmados antes del desembolso -- el checklist solo tenía
-- CONTRATO_FIRMADO/PAGARE_FIRMADO. Ver docs/funcionamiento/RF13_firmados_checklist.md.
--
-- No cambia ninguna estructura (no hace falta comparar_esquemas.sh), pero
-- SÍ se actualizó también bd/03_parametros.sql -- una base nueva creada
-- con recrear_base.sh nace ya con este tipo de documento.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db
-- (la real) NO se toca -- queda anotado en Pendientes de
-- RF13_firmados_checklist.md para cuando corresponda aplicarlo ahí.
--
-- El checklist (ServicioFormalizacion.obtenerChecklist) es genérico: recorre
-- tipos_documento con contexto='PRESTAMO' y obligatorio_desembolso=TRUE sin
-- ningún nombre hardcodeado, así que esta sola fila alcanza para que el
-- tercer ítem aparezca en la ficha de formalización, en el <select> de
-- "Tipo de documento", y para que confirmarDesembolso/asegurarCondicionesDesembolso
-- lo exijan -- no hace falta tocar ningún otro código para esa parte.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_14_plan_pagos_firmado.sql
--
-- Todo en una sola transacción, e idempotente: correrlo de nuevo no
-- inserta una segunda fila (guardado con WHERE NOT EXISTS).
-- =====================================================================

BEGIN;

INSERT INTO tipos_documento (codigo_tipo, nombre_tipo, contexto, obligatorio_desembolso,
    aplica_cliente, aplica_codeudor, requiere_firma, origen_esperado, activo)
SELECT 'PLAN_PAGOS_FIRMADO', 'Plan de pagos firmado', 'PRESTAMO', TRUE, TRUE, TRUE, TRUE, 'CARGADO', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM tipos_documento WHERE codigo_tipo = 'PLAN_PAGOS_FIRMADO'
);

COMMIT;
