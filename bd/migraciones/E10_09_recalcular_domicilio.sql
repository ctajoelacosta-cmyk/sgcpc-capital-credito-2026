-- =====================================================================
-- E10_09_recalcular_domicilio.sql
-- Migración de DATOS (no de estructura) para RF03 Parte 3 (pedido de Joel):
-- ServicioClientes.armarDomicilioDesdePartes nunca usaba direccion_domicilio
-- ("calle y número", obligatoria en la ficha desde E10_04) para armar la
-- columna personas.domicilio -- por eso salía, por ejemplo, "Sajonia,
-- Asunción, Asunción" (barrio, ciudad y departamento, sin calle, con
-- Asunción repetida porque es ciudad Y departamento a la vez). Ya corregido
-- en el código (construirTextoDomicilio); este script recalcula el texto
-- ya guardado de TODAS las personas existentes, con la misma regla exacta:
--   direccion_domicilio, barrio, ciudad[, departamento solo si el nombre
--   es distinto del de la ciudad] [+ " (referencia_domicilio)" si la hay]
--
-- Solo toca personas con id_ciudad e id_departamento cargados (no debería
-- haber ninguna sin esos datos, son obligatorios desde Tanda A, pero si
-- hubiera alguna vieja sin completar, esta migración la deja intacta en
-- vez de romper con un domicilio a medias).
--
-- Los PDF ya emitidos (contrato, pagaré, comprobantes, autorización al
-- buró, etc.) NO cambian: son bytea ya guardado en archivos_binarios, esta
-- migración no los toca ni los regenera -- solo corrige personas.domicilio
-- para lo que se emita de ahora en más.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas. sgcpc_db (la
-- real) NO se toca. No hace falta actualizar 01_esquema.sql ni correr
-- comparar_esquemas.sh: no cambia ninguna estructura, solo recalcula un
-- texto ya existente -- una base nueva (recrear_base.sh) arranca sin
-- personas, así que no hay nada que este script deba igualar ahí.
--
-- El comando, cuando corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_09_recalcular_domicilio.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

UPDATE personas p
SET domicilio = array_to_string(
        array_remove(
            ARRAY[
                NULLIF(TRIM(p.direccion_domicilio), ''),
                NULLIF(TRIM(p.barrio), ''),
                c.nombre,
                CASE WHEN c.nombre <> d.nombre THEN d.nombre ELSE NULL END
            ],
            NULL
        ),
        ', '
    )
    || CASE WHEN NULLIF(TRIM(p.referencia_domicilio), '') IS NOT NULL
            THEN ' (' || p.referencia_domicilio || ')'
            ELSE ''
       END
FROM ciudades c
JOIN departamentos d ON d.id_departamento = c.id_departamento
WHERE c.id_ciudad = p.id_ciudad
  AND d.id_departamento = p.id_departamento
  AND p.id_ciudad IS NOT NULL
  AND p.id_departamento IS NOT NULL;

COMMIT;
