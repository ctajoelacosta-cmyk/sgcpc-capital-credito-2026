-- =====================================================================
-- E10_04_clientes_abm_direccion.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para el Bloque 7 (E10),
-- revisión en el navegador de Joel (Obs. 31 y 16, AVANCE.md D-129): baja
-- lógica de cliente (motivo/detalle/fecha/usuario) y dirección (calle y
-- número) del domicilio, separada de barrio/referencia.
--
-- Sin backfill: las columnas nuevas quedan NULL en las personas existentes
-- -- mismo criterio que las Tandas A/B/D anteriores.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql,
-- que ya incluye estos mismos cambios desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db, recién después del OK
-- de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_04_clientes_abm_direccion.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE personas ADD COLUMN direccion_domicilio VARCHAR(250) NULL;

ALTER TABLE personas ADD COLUMN motivo_baja VARCHAR(30) NULL
    CHECK (motivo_baja IN ('FALLECIMIENTO', 'PEDIDO_CLIENTE', 'DATOS_DUPLICADOS', 'OTRO'));
ALTER TABLE personas ADD COLUMN detalle_baja      VARCHAR(300) NULL;
ALTER TABLE personas ADD COLUMN fecha_baja        TIMESTAMP NULL;
ALTER TABLE personas ADD COLUMN dado_de_baja_por  BIGINT NULL;

ALTER TABLE personas ADD CONSTRAINT fk_personas_dado_de_baja_por
    FOREIGN KEY (dado_de_baja_por) REFERENCES usuarios (id_usuario);

COMMIT;
