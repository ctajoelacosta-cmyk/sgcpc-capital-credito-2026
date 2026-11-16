-- =====================================================================
-- E10_03_tanda_b_politica_credito.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para el Bloque 7 (E10),
-- Tanda B: situación laboral + empleador, otros ingresos/gastos y cuota de
-- otras deudas, política de crédito parametrizable (D-127, AVANCE.md).
--
-- Sin backfill: las columnas nuevas quedan NULL en las personas existentes
-- -- por diseño (mismo criterio que la Tanda A, D-126): los campos nuevos
-- son obligatorios solo en altas nuevas, los existentes se completan al
-- editar.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql y
-- 03_parametros.sql, que ya incluyen estos mismos cambios desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db, recién después del OK
-- de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_03_tanda_b_politica_credito.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE personas ADD COLUMN situacion_laboral VARCHAR(30) NULL
    CHECK (situacion_laboral IN ('ASALARIADO_SECTOR_PRIVADO', 'FUNCIONARIO_PUBLICO',
        'JUBILADO_PENSIONADO', 'INDEPENDIENTE', 'COMERCIANTE', 'EMPLEADO_DOMESTICO',
        'DESEMPLEADO', 'OTRO'));
ALTER TABLE personas ADD COLUMN empleador_nombre           VARCHAR(150) NULL;
ALTER TABLE personas ADD COLUMN empleador_ruc              VARCHAR(20) NULL;
ALTER TABLE personas ADD COLUMN empleador_direccion        VARCHAR(250) NULL;
ALTER TABLE personas ADD COLUMN empleador_telefono         VARCHAR(30) NULL;
ALTER TABLE personas ADD COLUMN cargo                      VARCHAR(100) NULL;
ALTER TABLE personas ADD COLUMN antiguedad_laboral_anios   SMALLINT NULL;
ALTER TABLE personas ADD COLUMN otros_ingresos_mensuales   NUMERIC(18,0) NULL;
ALTER TABLE personas ADD COLUMN gastos_fijos_mensuales     NUMERIC(18,0) NULL;
ALTER TABLE personas ADD COLUMN cuota_otras_deudas_mensual NUMERIC(18,0) NULL;

-- ---------------------------------------------------------------------
-- Parámetros de política de crédito (mismos datos que bd/03_parametros.sql)
-- ---------------------------------------------------------------------
INSERT INTO parametros_sistema (clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion) VALUES
('credito.edad_minima', 'Edad mínima para otorgar crédito', '22', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.edad_maxima', 'Edad máxima para otorgar crédito', '71', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.departamentos_habilitados', 'Departamentos habilitados para otorgar crédito (separados por coma)', 'Asunción,Central', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.situaciones_laborales_admitidas', 'Situaciones laborales admitidas para otorgar crédito (códigos separados por coma)', 'ASALARIADO_SECTOR_PRIVADO,FUNCIONARIO_PUBLICO,JUBILADO_PENSIONADO,COMERCIANTE,EMPLEADO_DOMESTICO,OTRO', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP);

COMMIT;
