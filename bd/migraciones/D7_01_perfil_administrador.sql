-- =====================================================================
-- D7_01_perfil_administrador.sql
-- Migración para D7 (pedido de Joel, anteproyecto entregado): agrega el
-- perfil ADMINISTRADOR, con acceso a TODO el sistema (todos los permisos
-- en TOTAL, sin excepción) -- es el único que ve Usuarios y Parámetros.
--
-- Decisión final de Joel (no la propuesta original del Paso 1): el perfil
-- GERENTE_ADMINISTRADOR NO se toca -- mismo nombre, mismos permisos,
-- mismos usuarios, todo lo que hoy funciona queda exactamente igual.
-- ADMINISTRADOR se AGREGA como un cuarto valor nuevo, no reemplaza nada.
--
-- Dos cambios:
-- 1. El CHECK de usuarios.perfil_usuario pasa de 3 a 4 valores. Se recrea
--    con el mismo nombre que Postgres ya le había asignado solo
--    (usuarios_perfil_usuario_check, confirmado con pg_get_constraintdef
--    sobre sgcpc_db_pruebas) para que una base migrada y una recreada
--    desde 01_esquema.sql (que de acá en más ya lo trae con 4 valores y
--    el mismo nombre) queden con la misma estructura -- mismo criterio
--    que E10_06_redondeo_efectivo.sql con los CHECK de "pagos".
-- 2. Un usuario nuevo "administrador" (Joel Jesús Acosta), con la misma
--    contraseña temporal del seed (mismo hash bcrypt que ya usan
--    gerente/oficial/cajera en 03_parametros.sql -- "Cambiar@2026",
--    nunca en texto plano acá ni en ningún lado fuera del hash),
--    debe_cambiar_contrasena = TRUE, igual que los otros tres.
--
-- NO se corre en una instalación nueva: desde esta migración,
-- 01_esquema.sql y 03_parametros.sql ya traen los dos cambios desde el
-- origen.
--
-- NO SE APLICA A sgcpc_db EN ESTA TANDA -- pedido explícito de Joel: queda
-- en la cola de "Migraciones pendientes de aplicar a sgcpc_db" (AVANCE.md)
-- para cuando decida pasarla a la base real (F2). Se aplica solo a
-- sgcpc_db_pruebas, autorizado explícitamente para esta tarea. Cuando
-- corresponda aplicarla a la base oficial, el comando es:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/D7_01_perfil_administrador.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- Las dos operaciones son idempotentes (CHECK con el mismo nombre final
-- sin importar si ya se había recreado antes; el INSERT del usuario con
-- WHERE NOT EXISTS), por si hiciera falta reintentar.
-- =====================================================================

BEGIN;

ALTER TABLE usuarios DROP CONSTRAINT usuarios_perfil_usuario_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_perfil_usuario_check
    CHECK (perfil_usuario IN ('OFICIAL_CREDITO', 'GERENTE_ADMINISTRADOR', 'ADMINISTRADOR', 'CAJERO'));

INSERT INTO usuarios (
    nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad,
    perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena,
    fecha_creacion, creado_por
)
SELECT
    'administrador', '$2a$10$viR8.rhVb5mtYuXIpK9uFu36YaLkPhwnhzk5gcEYAI.N5GdBWSs3a',
    'Joel Jesús', 'Acosta', '2456812',
    'ADMINISTRADOR', 'ACTIVA', 0, TRUE,
    CURRENT_TIMESTAMP, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente')
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE nombre_usuario = 'administrador');

COMMIT;
