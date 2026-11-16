-- =====================================================================
-- E10_11_seguridad_auditoria.sql
-- Migración de ESTRUCTURA -- bloque de seguridad/auditoría/eficiencia (hallazgo de Joel,
-- D-113: todo cambio de estructura primero como migración, y se incorpora también a
-- 01_esquema.sql/02_indices.sql -- ya hecho en este mismo cambio).
--
-- 1) auditoria_operaciones.id_usuario pasa a ser NULL-able (decisión nueva, cambia D-27): antes
--    un intento de acceso con un usuario que NO existe no se auditaba, porque no había ninguna
--    cuenta real a la que atribuirle la fila (NOT NULL + FK). Ahora se audita igual, con
--    id_usuario NULL y el nombre tal cual se escribió en usuario_escrito (recortado a 60
--    caracteres) -- para poder ver patrones de fuerza bruta sin necesitar una cuenta real.
-- 2) auditoria_operaciones.navegador (User-Agent) -- mismo criterio que direccion_ip, para los
--    eventos de acceso y de sesión.
-- 3) Índice compuesto (id_usuario, fecha_hora) -- ya existía uno solo por fecha_hora
--    (ix_auditoria_fecha); este es para filtrar por usuario Y fecha a la vez (pantalla de
--    Auditoría).
-- 4) Índice único sobre usuarios (lower(nombre_usuario)) -- red de seguridad explícita además
--    del UNIQUE simple que ya existe sobre la columna (usuarios_nombre_usuario_key); verificado
--    antes de aplicar que no hay ningún duplicado case-insensitive en sgcpc_db_pruebas (si
--    hubiera, este CREATE UNIQUE INDEX fallaría solo, sin dejar nada a medio aplicar).
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db (la real) NO se toca
-- -- ni siquiera para comparar con bd/comparar_esquemas.sh (que sí la lee, aunque sea de solo
-- lectura): queda pendiente para que Joel la corra él mismo cuando quiera confirmar.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_11_seguridad_auditoria.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE auditoria_operaciones ALTER COLUMN id_usuario DROP NOT NULL;
ALTER TABLE auditoria_operaciones ADD COLUMN IF NOT EXISTS navegador VARCHAR(255) NULL;
ALTER TABLE auditoria_operaciones ADD COLUMN IF NOT EXISTS usuario_escrito VARCHAR(60) NULL;

CREATE INDEX IF NOT EXISTS ix_auditoria_usuario_fecha ON auditoria_operaciones (id_usuario, fecha_hora);

CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_nombre_usuario_minusculas ON usuarios (lower(nombre_usuario));

COMMIT;
