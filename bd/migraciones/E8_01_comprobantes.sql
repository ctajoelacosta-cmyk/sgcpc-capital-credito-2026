-- =====================================================================
-- E8_01_comprobantes.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para los 5 comprobantes de
-- E8 (docs/diseno_comprobantes_E8.md, decisiones del Bloque 1 aprobadas
-- por Joel el 28/09/2026). NO usa recrear_base.sh (borraría CC-2026-90001
-- y la sesión de caja 16, que son la referencia de los ejemplos del
-- documento).
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql y
-- 03_parametros.sql, que ya incluyen estos mismos cambios desde el
-- origen (sin backfill, porque no hay datos previos que numerar). Este
-- script es solo para poner al día la base de desarrollo que ya tiene
-- historial.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E8_01_comprobantes.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. Numeradores nuevos: CONSTANCIA (constancia de cancelación total,
--    CAN-aaaa-nnnnnn) y DESEMBOLSO (comprobante de desembolso,
--    DES-aaaa-nnnnnn). Mismo patrón (clave, año) que RECIBO -- año
--    calendario, sin la regla de año-de-timbrado de D-89 (no son
--    documentos fiscales).
-- ---------------------------------------------------------------------
ALTER TABLE numeradores DROP CONSTRAINT numeradores_clave_check;
ALTER TABLE numeradores ADD CONSTRAINT numeradores_clave_check
    CHECK (clave IN ('FACTURA', 'OPERACION', 'RECIBO', 'SOLICITUD', 'CONSTANCIA', 'DESEMBOLSO'));

-- ---------------------------------------------------------------------
-- 2. Columnas nuevas.
--    - liquidaciones_anticipadas.numero_constancia: NULL para las
--      liquidaciones PARCIAL (nunca llevan constancia); se completa para
--      las TOTAL. UNIQUE permite varios NULL sin problema (Postgres no
--      los considera iguales entre sí).
--    - liquidaciones_anticipadas.monto_vencidas_impagas / iva_moratorio /
--      iva_punitorio: decisión de Joel (versión completa) -- el motor ya
--      calcula estos valores en calcularCancelacionTotal pero hoy se
--      descartan al persistir; a partir de este cambio
--      confirmarCancelacionTotal los va a guardar (Bloque 2). DEFAULT 0
--      porque ninguna cancelación total registrada hasta ahora tuvo
--      cuotas vencidas -- verificado por aplicaciones_pago (no solo
--      moratorio/punitorio = 0, que no alcanza por los días de gracia):
--      para las 4 cancelaciones TOTAL existentes, TODAS las cuotas que
--      tocó cada pago de cancelación tenían fecha_vencimiento posterior
--      a la fecha de esa cancelación (ninguna era vencida). El default
--      es entonces también el valor histórico correcto -- no hace falta
--      reconstruir nada con cálculo.
--    - desembolsos.numero_comprobante: mismo criterio que numero_recibo
--      de pagos, pero se deja NULLABLE por ahora (no NOT NULL): recién en
--      el Bloque 2 el código empieza a completarlo al confirmar un
--      desembolso -- si quedara NOT NULL desde ya, cualquier desembolso
--      real entre el Bloque 1 y el Bloque 2 rompería el INSERT.
-- ---------------------------------------------------------------------
ALTER TABLE liquidaciones_anticipadas ADD COLUMN numero_constancia VARCHAR(30) NULL UNIQUE;
ALTER TABLE liquidaciones_anticipadas ADD COLUMN monto_vencidas_impagas NUMERIC(18,0) NOT NULL DEFAULT 0;
ALTER TABLE liquidaciones_anticipadas ADD COLUMN iva_moratorio NUMERIC(18,0) NOT NULL DEFAULT 0;
ALTER TABLE liquidaciones_anticipadas ADD COLUMN iva_punitorio NUMERIC(18,0) NOT NULL DEFAULT 0;

ALTER TABLE desembolsos ADD COLUMN numero_comprobante VARCHAR(30) NULL UNIQUE;

-- ---------------------------------------------------------------------
-- 3. Parámetros: separar empresa.representante_legal en nombre y cédula,
--    SIN tocar ni cerrar la vigencia del parámetro combinado original
--    (ServicioFormalizacion.java:708 todavía lo usa para contrato/pagaré,
--    ya cerrados en E5 -- no se toca código que ya funciona sin avisar).
--    Quedan 3 parámetros de empresa: el combinado (como estaba) y los 2
--    nuevos separados, para que la constancia (Bloque 2) use estos.
--    vigente_desde = la MISMA fecha que el combinado original (no
--    CURRENT_DATE): así, si algún día se reimprime un documento anterior
--    a hoy que dependa de estos parámetros, la búsqueda "vigente a la
--    fecha del documento" los encuentra igual que encuentra al combinado.
-- ---------------------------------------------------------------------
INSERT INTO parametros_sistema
    (clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion)
SELECT 'empresa.representante_legal_nombre', 'Representante legal (nombre)', 'Ramón Benítez', 'TEXTO',
        original.vigente_desde, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), now()
FROM parametros_sistema original
WHERE original.clave_parametro = 'empresa.representante_legal' AND original.vigente_hasta IS NULL
UNION ALL
SELECT 'empresa.representante_legal_cedula', 'Representante legal (C.I.)', '2.845.117', 'TEXTO',
        original.vigente_desde, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), now()
FROM parametros_sistema original
WHERE original.clave_parametro = 'empresa.representante_legal' AND original.vigente_hasta IS NULL;

-- ---------------------------------------------------------------------
-- 4. Backfill: numerar cronológicamente DENTRO DE CADA AÑO (PARTITION BY
--    año, ORDER BY id -- el id coincide con el orden real de
--    fecha_calculo/fecha_hora_desembolso, verificado antes de escribir
--    esto) los desembolsos y las cancelaciones totales que ya existen --
--    coherente con que el numerador de destino también es por (clave,
--    año). Hoy da lo mismo (las 4 liquidaciones TOTAL y los 24
--    desembolsos son todos de 2026), pero queda correcto si alguna vez
--    hay datos de más de un año.
-- ---------------------------------------------------------------------
WITH numerado AS (
    SELECT id_liquidacion,
        ROW_NUMBER() OVER (PARTITION BY EXTRACT(YEAR FROM fecha_calculo) ORDER BY id_liquidacion) AS n
    FROM liquidaciones_anticipadas
    WHERE tipo_liquidacion = 'TOTAL'
)
UPDATE liquidaciones_anticipadas la
SET numero_constancia = 'CAN-' || EXTRACT(YEAR FROM la.fecha_calculo)::text || '-' || LPAD(numerado.n::text, 6, '0')
FROM numerado
WHERE la.id_liquidacion = numerado.id_liquidacion;

WITH numerado AS (
    SELECT id_desembolso,
        ROW_NUMBER() OVER (PARTITION BY EXTRACT(YEAR FROM fecha_hora_desembolso) ORDER BY id_desembolso) AS n
    FROM desembolsos
)
UPDATE desembolsos d
SET numero_comprobante = 'DES-' || EXTRACT(YEAR FROM d.fecha_hora_desembolso)::text || '-' || LPAD(numerado.n::text, 6, '0')
FROM numerado
WHERE d.id_desembolso = numerado.id_desembolso;

-- ---------------------------------------------------------------------
-- 5. Numeradores al último valor asignado, para que la próxima emisión
--    real siga desde ahí (nunca reinicia ni pisa lo recién numerado).
-- ---------------------------------------------------------------------
INSERT INTO numeradores (clave, anio, ultimo_valor)
SELECT 'CONSTANCIA', EXTRACT(YEAR FROM fecha_calculo)::smallint, COUNT(*)
FROM liquidaciones_anticipadas WHERE tipo_liquidacion = 'TOTAL'
GROUP BY EXTRACT(YEAR FROM fecha_calculo)::smallint
ON CONFLICT (clave, anio) DO UPDATE SET ultimo_valor = EXCLUDED.ultimo_valor;

INSERT INTO numeradores (clave, anio, ultimo_valor)
SELECT 'DESEMBOLSO', EXTRACT(YEAR FROM fecha_hora_desembolso)::smallint, COUNT(*)
FROM desembolsos
GROUP BY EXTRACT(YEAR FROM fecha_hora_desembolso)::smallint
ON CONFLICT (clave, anio) DO UPDATE SET ultimo_valor = EXCLUDED.ultimo_valor;

COMMIT;
