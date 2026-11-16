-- =====================================================================
-- E10_10_parametro_minutos_bloqueo.sql
-- Migración de DATOS (no de estructura) -- RF36/RF37 (hallazgo de Joel):
-- el usuario "administrador" quedó bloqueado por intentos fallidos en
-- sgcpc_db_pruebas; por D-151 (Usuarios exclusivo del administrador),
-- si el único administrador se bloquea, nadie puede reactivarlo desde la
-- app. Un bloqueo AUTOMATICO (por intentos fallidos, operación
-- CUENTA_BLOQUEADA en auditoria_operaciones) ahora se libera solo tras
-- "seguridad.minutos_bloqueo" minutos; un bloqueo o inactivación MANUAL
-- (hecho por un administrador, operación BLOQUEAR_USUARIO/
-- INACTIVAR_USUARIO) nunca se libera solo -- ver ServicioAutenticacion.
--
-- Agrega el parámetro nuevo a parametros_sistema. No cambia ninguna
-- estructura (no hace falta comparar_esquemas.sh), pero SÍ hay que
-- agregarlo también a 03_parametros.sql (a diferencia de una migración de
-- datos sobre filas existentes, como E10_09): una base nueva creada con
-- recrear_base.sh tiene que nacer con este parámetro, porque el login lo
-- necesita desde el primer arranque -- ya está agregado ahí.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db
-- (la real) NO se toca.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_10_parametro_minutos_bloqueo.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

INSERT INTO parametros_sistema (
    clave_parametro, nombre_parametro, valor_parametro, tipo_valor,
    vigente_desde, modificado_por, fecha_modificacion
)
SELECT 'seguridad.minutos_bloqueo', 'Minutos para liberar un bloqueo automático por intentos fallidos',
       '15', 'ENTERO', CURRENT_DATE,
       (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM parametros_sistema
    WHERE clave_parametro = 'seguridad.minutos_bloqueo' AND vigente_hasta IS NULL
);

COMMIT;
