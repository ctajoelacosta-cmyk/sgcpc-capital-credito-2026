-- =====================================================================
-- E10_06_redondeo_efectivo.sql
-- Migración sobre la base EXISTENTE para D-141 (pedido de Joel, cumplimiento
-- SEDECO): redondeo del efectivo a favor del cliente en cobros en efectivo
-- (cobro normal, cancelación total, adelantar capital).
--
-- pagos.monto_redondeo: el descuento a favor del cliente cuando el monto
-- redondeado hacia abajo es menor al exacto -- 0 cuando no corresponde
-- (parámetro credito.redondeo_efectivo en 0, o el cliente no llega ni al
-- monto ya redondeado). La deuda queda saldada por el monto EXACTO
-- (monto_aplicado); monto_redondeo es la diferencia que el efectivo
-- realmente entregado (monto_recibido) no cubre, perdonada por la empresa.
--
-- El CHECK monto_recibido = monto_aplicado + monto_vuelto ya no alcanza
-- con redondeo de por medio (monto_aplicado puede ser mayor que lo
-- realmente recibido): pasa a monto_recibido + monto_redondeo =
-- monto_aplicado + monto_vuelto.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql,
-- que ya incluye este mismo cambio desde el origen.
--
-- Se corre UNA vez, a mano, conectado a la base que corresponda (en esta
-- tanda, sgcpc_db_pruebas -- Joel pidió explícitamente no tocar sgcpc_db
-- todavía), recién después del OK de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_06_redondeo_efectivo.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE pagos ADD COLUMN monto_redondeo NUMERIC(18,0) NOT NULL DEFAULT 0;

ALTER TABLE pagos DROP CONSTRAINT pagos_check;
ALTER TABLE pagos DROP CONSTRAINT pagos_check1;
ALTER TABLE pagos ADD CONSTRAINT pagos_check CHECK (monto_recibido + monto_redondeo = monto_aplicado + monto_vuelto);
ALTER TABLE pagos ADD CONSTRAINT pagos_check1
    CHECK (monto_recibido >= 0 AND monto_vuelto >= 0 AND monto_aplicado >= 0 AND monto_redondeo >= 0);

-- Parámetro nuevo (D-141): dato, no estructura -- parametros_sistema ya está pensada para
-- agregar claves nuevas sin ningún cambio de esquema; se inserta acá nada más por prolijidad,
-- para que esta migración y 03_parametros.sql (que ya trae la misma fila) dejen la base en el
-- mismo estado en los dos caminos (base nueva / base migrada), igual que cualquier otro parámetro.
INSERT INTO parametros_sistema (clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion)
SELECT 'credito.redondeo_efectivo', 'Redondeo del efectivo a favor del cliente (Gs., 0 = sin redondeo)', '50', 'ENTERO',
    CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM parametros_sistema WHERE clave_parametro = 'credito.redondeo_efectivo');

COMMIT;
