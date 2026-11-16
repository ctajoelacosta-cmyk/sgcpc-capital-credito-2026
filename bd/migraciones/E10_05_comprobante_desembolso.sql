-- =====================================================================
-- E10_05_comprobante_desembolso.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para el Bloque 7 (E10),
-- pedido de Joel tras revisar el Grupo 1 (AVANCE.md D-130): comprobante de
-- desembolso impreso y firmado ANTES de entregar el efectivo (prioridad
-- máxima). El N° de comprobante se asigna al imprimir "para firma",
-- todavía con el préstamo PENDIENTE_DESEMBOLSO -- antes se asignaba recién
-- al confirmar (D-93/D-94, E8). Se guarda en prestamos porque en ese
-- momento el desembolso (que también tiene numero_comprobante, E8) todavía
-- no existe como fila.
--
-- Sin backfill: la columna nueva queda NULL en los préstamos existentes
-- (ya desembolsados antes de este cambio, con su numero_comprobante propio
-- en la fila de desembolsos, sin tocar).
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql/
-- 03_parametros.sql, que ya incluyen estos mismos cambios desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db, recién después del OK
-- de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_05_comprobante_desembolso.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE prestamos ADD COLUMN numero_comprobante_desembolso VARCHAR(20) NULL UNIQUE;

-- Mismo patrón GENERADO/FIRMADO que CONTRATO/PAGARE/DEVOLUCION_PAGARE (ver 03_parametros.sql) --
-- acá solo hace falta el FIRMADO: el comprobante "generado por el sistema" no se guarda como
-- documento del expediente (se regenera al vuelo en cada impresión, para poder timbrarlo con
-- "REIMPRESIÓN" y la fecha/hora real de esa impresión, pedido de Joel) -- el escaneo firmado que
-- sube la cajera después de la firma del cliente sí es un documento fijo, como los demás.
INSERT INTO tipos_documento (codigo_tipo, nombre_tipo, contexto, obligatorio_desembolso, aplica_cliente, aplica_codeudor, requiere_firma, origen_esperado, activo) VALUES
('COMPROBANTE_DESEMBOLSO_FIRMADO', 'Comprobante de desembolso firmado (escaneo)', 'PRESTAMO', FALSE, TRUE, FALSE, TRUE, 'CARGADO', TRUE);

COMMIT;
