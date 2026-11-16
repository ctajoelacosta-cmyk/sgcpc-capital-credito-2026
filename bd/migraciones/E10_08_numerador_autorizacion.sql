-- =====================================================================
-- E10_08_numerador_autorizacion.sql
-- Migración de ESTRUCTURA (D-113) para RF03/RF04: número de autorización
-- de consulta al buró, único por año, mismo mecanismo que OPERACION/
-- RECIBO/FACTURA/DESEMBOLSO/etc. (NumeradorDAO.siguienteValor, bd/01_
-- esquema.sql tabla "numeradores"). Solo amplía el CHECK de la columna
-- "clave" con un valor nuevo, 'AUTORIZACION' -- no agrega ninguna tabla
-- ni columna.
--
-- Formato del número que arma ServicioConsultaCrediticia.generarAutorizacionBuroPdf:
-- "AUT-%d-%05d" (año, secuencia) -- ej. "AUT-2026-00001", mismo patrón que
-- "DES-%d-%06d"/"SOL-%d-%04d" ya usados.
--
-- NO se corre en una instalación nueva: 01_esquema.sql ya incluye
-- 'AUTORIZACION' en el CHECK desde el origen.
--
-- Pedido explícito de Joel: se aplica SOLO a sgcpc_db_pruebas por ahora.
-- sgcpc_db (la real) NO se toca -- queda para F2, junto con el resto de
-- los cambios de estructura pendientes de esta sesión. El comando, cuando
-- corresponda:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d <sgcpc_db_pruebas o sgcpc_db> -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_08_numerador_autorizacion.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

ALTER TABLE numeradores DROP CONSTRAINT numeradores_clave_check;
ALTER TABLE numeradores ADD CONSTRAINT numeradores_clave_check
    CHECK (clave IN ('FACTURA', 'OPERACION', 'RECIBO', 'SOLICITUD', 'CONSTANCIA', 'DESEMBOLSO',
                      'ANULACION', 'DEVOLUCION', 'AUTORIZACION'));

COMMIT;
