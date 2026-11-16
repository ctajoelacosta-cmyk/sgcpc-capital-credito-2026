-- =====================================================================
-- E8_04_gerente_predeterminado.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) -- D-99 (E8 Bloque 2, corrección
-- pedida por Joel): el arqueo de caja muestra "Recibí: <gerente>" -- cuando la
-- sesión cerró sin diferencia (la gran mayoría), sesiones_caja.gerente_revisa_id
-- queda NULL (ese campo solo se completa al revisar una diferencia, decisión 4
-- del plan de E6), así que no hay ningún gerente registrado contra esa sesión
-- puntual. Este parámetro es el nombre que se usa en ese caso.
--
-- NO se corre en una instalación nueva: ahí alcanza con 03_parametros.sql, que
-- ya incluye esta fila desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E8_04_gerente_predeterminado.sql
--
-- vigente_desde = 2026-09-01 (antes de cualquier sesión de caja real que ya
-- exista, D-93/D-95: para que la reimpresión de un arqueo anterior a hoy
-- encuentre igual el parámetro vigente en su propia fecha), no CURRENT_DATE.
-- =====================================================================

BEGIN;

INSERT INTO parametros_sistema (clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion)
VALUES ('caja.gerente_predeterminado', 'Gerente que recibe la rendición (si no hay revisión registrada)',
        'Ramón Benítez', 'TEXTO', DATE '2026-09-01',
        (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP);

COMMIT;
