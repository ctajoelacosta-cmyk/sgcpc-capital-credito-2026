-- =====================================================================
-- E9_01_reversiones.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para el Bloque 5 (E9):
-- reversiones y cierre documental (docs/diseno_bloque5_E9.md, decisiones
-- aprobadas por Joel el 28/09/2026, más los ajustes A-I de su segunda
-- revisión).
--
-- Sin backfill: reversiones_pago existe desde E0 pero NINGÚN código la usó
-- nunca (confirmado: 0 filas, y "REVERTIDO"/"ANULADA" no aparecen escritos
-- en ningún lado del código Java) -- no hay ninguna reversión real que
-- tenga que adaptarse a las columnas nuevas.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql,
-- 02_indices.sql y 03_parametros.sql, que ya incluyen estos mismos cambios
-- desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db, recién después del OK
-- de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E9_01_reversiones.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. reversiones_pago: fase "pendiente de autorizar" (Doc. 00 §7:
--    SOLICITADA/AUTORIZADA/RECHAZADA -- hoy la tabla solo podía
--    representar el resultado YA ejecutado). Las columnas de ejecución
--    (autorizado_por, procesado_por, id_sesion_caja, monto_salida_caja,
--    fecha_hora_reversion) pasan a NULL: se completan recién al autorizar.
--    motivo_reversion sigue NOT NULL -- es el motivo de la SOLICITUD
--    (se pide siempre, no cambia de significado).
--    factura_en_poder_confirmado (ajuste I, DNIT RG 118/2017 / Decreto
--    6539/05): la cajera/gerente tiene que confirmar que la factura
--    original y sus copias siguen en su poder antes de anular -- si ya
--    se la dio al cliente, corresponde Nota de Crédito, no disponible en
--    esta versión. NULL cuando la operación revertida no tenía factura
--    (nunca se pregunta si no aplica).
-- ---------------------------------------------------------------------
-- reversiones_pago tiene 0 filas (verificado antes de escribir esta migración): las columnas
-- NOT NULL se agregan directo, no hace falta ningún DEFAULT temporal.
ALTER TABLE reversiones_pago ADD COLUMN estado VARCHAR(20) NOT NULL
    CHECK (estado IN ('SOLICITADA', 'AUTORIZADA', 'RECHAZADA'));
ALTER TABLE reversiones_pago ADD COLUMN solicitado_por BIGINT NOT NULL;
ALTER TABLE reversiones_pago ADD COLUMN fecha_hora_solicitud TIMESTAMP NOT NULL;
ALTER TABLE reversiones_pago ADD COLUMN motivo_rechazo VARCHAR(500) NULL;
ALTER TABLE reversiones_pago ADD COLUMN factura_en_poder_confirmado BOOLEAN NULL;

ALTER TABLE reversiones_pago ALTER COLUMN autorizado_por DROP NOT NULL;
ALTER TABLE reversiones_pago ALTER COLUMN procesado_por DROP NOT NULL;
ALTER TABLE reversiones_pago ALTER COLUMN id_sesion_caja DROP NOT NULL;
ALTER TABLE reversiones_pago ALTER COLUMN monto_salida_caja DROP NOT NULL;
ALTER TABLE reversiones_pago ALTER COLUMN fecha_hora_reversion DROP NOT NULL;

ALTER TABLE reversiones_pago ADD CONSTRAINT chk_reversion_pago_consistencia CHECK (
    (estado = 'SOLICITADA' AND autorizado_por IS NULL AND procesado_por IS NULL
        AND id_sesion_caja IS NULL AND monto_salida_caja IS NULL AND fecha_hora_reversion IS NULL)
    OR (estado = 'AUTORIZADA' AND autorizado_por IS NOT NULL AND procesado_por IS NOT NULL
        AND id_sesion_caja IS NOT NULL AND monto_salida_caja IS NOT NULL AND fecha_hora_reversion IS NOT NULL)
    OR (estado = 'RECHAZADA' AND motivo_rechazo IS NOT NULL)
);

ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_solicitado_por
    FOREIGN KEY (solicitado_por) REFERENCES usuarios (id_usuario);

CREATE INDEX ix_reversiones_pago_estado ON reversiones_pago (estado);

-- ---------------------------------------------------------------------
-- 2. liquidaciones_anticipadas: nuevo estado REVERTIDA (ajuste A -- ni el
--    anticipo ni la cancelación se borran al revertirse, quedan marcados;
--    la versión de plan anterior vuelve a ser la vigente, ver el diseño).
-- ---------------------------------------------------------------------
ALTER TABLE liquidaciones_anticipadas DROP CONSTRAINT liquidaciones_anticipadas_estado_liquidacion_check;
ALTER TABLE liquidaciones_anticipadas ADD CONSTRAINT liquidaciones_anticipadas_estado_liquidacion_check
    CHECK (estado_liquidacion IN ('GENERADA', 'APLICADA', 'CANCELADA', 'REVERTIDA'));

-- ---------------------------------------------------------------------
-- 3. facturas: la Nota de anulación (ajuste F) es un comprobante propio,
--    ligado a la factura anulada -- mismo patrón que
--    liquidaciones_anticipadas.numero_constancia/id_archivo_liquidacion
--    (no documentos_expediente, que no tiene una columna id_factura en su
--    CHECK de pertenencia).
-- ---------------------------------------------------------------------
ALTER TABLE facturas ADD COLUMN numero_nota_anulacion VARCHAR(30) NULL UNIQUE;
ALTER TABLE facturas ADD COLUMN id_archivo_nota_anulacion BIGINT NULL;
ALTER TABLE facturas ADD CONSTRAINT fk_facturas_archivo_nota_anulacion
    FOREIGN KEY (id_archivo_nota_anulacion) REFERENCES archivos_binarios (id_archivo);
CREATE INDEX ix_facturas_estado ON facturas (estado);

-- ---------------------------------------------------------------------
-- 4. prestamos: número de la constancia de devolución del pagaré (P10) --
--    mismo patrón que numero_contrato/numero_pagare (columna propia, el
--    PDF en sí viaja por documentos_expediente como el resto del
--    expediente, código DEVOLUCION_PAGARE_GENERADO/_FIRMADO del punto 6).
-- ---------------------------------------------------------------------
ALTER TABLE prestamos ADD COLUMN numero_constancia_devolucion VARCHAR(30) NULL UNIQUE;

-- ---------------------------------------------------------------------
-- 5. numeradores: dos claves nuevas -- ANULACION (Nota de anulación,
--    NAN-aaaa-nnnnnn) y DEVOLUCION (constancia de devolución del pagaré,
--    DEV-aaaa-nnnnnn). Clave propia para cada una (ajuste D de Joel: NO
--    reusar CONSTANCIA, que dejaría saltos en la numeración CAN- de las
--    constancias de cancelación).
-- ---------------------------------------------------------------------
ALTER TABLE numeradores DROP CONSTRAINT numeradores_clave_check;
ALTER TABLE numeradores ADD CONSTRAINT numeradores_clave_check
    CHECK (clave IN ('FACTURA', 'OPERACION', 'RECIBO', 'SOLICITUD', 'CONSTANCIA', 'DESEMBOLSO', 'ANULACION', 'DEVOLUCION'));

-- ---------------------------------------------------------------------
-- 6. tipos_documento: la constancia de devolución del pagaré entra al
--    expediente del préstamo con el mismo patrón GENERADO/FIRMADO que
--    CONTRATO/PAGARE (E5) -- no aplica a codeudor (el pagaré es del
--    cliente), no es obligatoria para el desembolso (pasa después,
--    cuando el préstamo ya está FINALIZADO).
-- ---------------------------------------------------------------------
INSERT INTO tipos_documento (codigo_tipo, nombre_tipo, contexto, obligatorio_desembolso, aplica_cliente, aplica_codeudor, requiere_firma, origen_esperado, activo) VALUES
('DEVOLUCION_PAGARE_GENERADO', 'Constancia de devolución de pagaré (generada por el sistema)', 'PRESTAMO', FALSE, TRUE, FALSE, FALSE, 'JASPER', TRUE),
('DEVOLUCION_PAGARE_FIRMADO', 'Constancia de devolución de pagaré firmada', 'PRESTAMO', FALSE, TRUE, FALSE, TRUE, 'CARGADO', TRUE);

COMMIT;
