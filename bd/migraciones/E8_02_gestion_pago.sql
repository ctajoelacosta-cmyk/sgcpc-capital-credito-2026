-- =====================================================================
-- E8_02_gestion_pago.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) -- D-95 (E8 Bloque 2): vincula
-- gestiones_cobranza con el pago parcial que la generó, para que el recibo
-- de un pago parcial pueda mostrar "Promesa de pago: dd/mm/aaaa por X Gs."
-- leyendo un vínculo seguro, nunca adivinando por cercanía de fecha.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql, que
-- ya incluye la columna desde el origen (sin backfill, no hay datos previos).
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E8_02_gestion_pago.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. Columna nueva. NULL para las gestiones que no vienen de un pago
--    (LLAMADA/MENSAJE/VISITA_LOCAL) y para las PAGO_PARCIAL que, tras el
--    backfill, no tengan un vínculo seguro.
-- ---------------------------------------------------------------------
ALTER TABLE gestiones_cobranza ADD COLUMN id_pago BIGINT NULL;
ALTER TABLE gestiones_cobranza ADD CONSTRAINT fk_gestiones_pago
    FOREIGN KEY (id_pago) REFERENCES pagos (id_pago);

-- ---------------------------------------------------------------------
-- 2. Backfill: SOLO cuando el vínculo es seguro -- mismo préstamo, tipo
--    PAGO_PARCIAL/PARCIAL, y el MISMO INSTANTE DE TRANSACCIÓN. No es una
--    aproximación por cercanía: pagos.fecha_hora_pago y
--    gestiones_cobranza.fecha_hora se escriben con now() de SQL, y en
--    PostgreSQL now() devuelve el mismo valor exacto para TODA una misma
--    transacción -- confirmarCobro inserta el pago y su gestión en la
--    misma transacción, así que cuando de verdad son el mismo pago, las
--    dos columnas coinciden byte a byte. Si hay más de un pago PARCIAL
--    del mismo préstamo con ese mismo instante (no debería pasar nunca,
--    pero si pasara), la ambigüedad se detecta con el COUNT y esa
--    gestión queda sin vincular -- nunca se adivina.
-- ---------------------------------------------------------------------
WITH candidatos AS (
    SELECT g.id_gestion_cobranza, p.id_pago,
        COUNT(*) OVER (PARTITION BY g.id_gestion_cobranza) AS cantidad_candidatos
    FROM gestiones_cobranza g
    JOIN pagos p ON p.id_prestamo = g.id_prestamo AND p.tipo = 'PARCIAL' AND p.fecha_hora_pago = g.fecha_hora
    WHERE g.tipo = 'PAGO_PARCIAL' AND g.id_pago IS NULL
)
UPDATE gestiones_cobranza g
SET id_pago = c.id_pago
FROM candidatos c
WHERE g.id_gestion_cobranza = c.id_gestion_cobranza AND c.cantidad_candidatos = 1;

COMMIT;
