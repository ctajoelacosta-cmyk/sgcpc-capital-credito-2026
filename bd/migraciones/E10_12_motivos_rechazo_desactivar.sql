-- =====================================================================
-- E10_12_motivos_rechazo_desactivar.sql
-- Migración de DATOS (no de estructura) -- RF10 (hallazgo de Joel,
-- 11/10/2026): los motivos de rechazo EDAD, ZONA y POLITICA_LABORAL
-- corresponden exactamente a lo que ServicioSolicitudes.validarPoliticaCredito
-- ya bloquea al ENVIAR la solicitud a aprobación (enviarAAprobacion) -- una
-- solicitud que llega PENDIENTE_APROBACION ya cumple los tres puntos, así
-- que el gerente nunca tiene un motivo real para elegirlos al rechazar.
-- Ver docs/funcionamiento/RF10_rechazar.md §6.2 para el análisis completo.
--
-- Desactiva (activo = FALSE) los 3 motivos, sin borrarlos -- cualquier
-- solicitud ya rechazada con alguno de ellos sigue mostrando su motivo
-- exactamente igual (solicitudes_credito.motivo_rechazo guarda el texto ya
-- resuelto, no una referencia que se vuelve a leer; id_motivo_rechazo sigue
-- siendo una referencia válida, la fila no se borra). El único efecto es
-- que dejan de ofrecerse en el <select> de rechazos nuevos.
--
-- No cambia ninguna estructura (no hace falta comparar_esquemas.sh), pero
-- SÍ se actualizó también bd/03_parametros.sql (a diferencia de una
-- migración de datos sobre filas existentes): una base nueva creada con
-- recrear_base.sh nace ya sin esos 3 motivos activos.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db
-- (la real) NO se toca -- queda anotado en Pendientes
-- (RF10_rechazar.md, sección "Antes de entregar") para cuando corresponda
-- aplicarlo ahí.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_12_motivos_rechazo_desactivar.sql
--
-- Todo en una sola transacción, e idempotente: correrlo de nuevo no cambia
-- nada (los 3 ya quedarían en activo = FALSE).
-- =====================================================================

BEGIN;

UPDATE motivos_rechazo_solicitud
SET activo = FALSE
WHERE codigo IN ('EDAD', 'ZONA', 'POLITICA_LABORAL')
  AND activo = TRUE;

COMMIT;
