-- RF15 -- prueba manual: simular una caja ABIERTA de un día anterior.
--
-- Propósito: repetir, en sgcpc_db_pruebas, la regla "no permitir operar si quedó
-- abierta una caja de un día anterior" (RF15), sin esperar a que pase un día
-- real de calendario.
--
-- Alcance (pedido de Joel, 11/10/2026): SOLO sgcpc_db_pruebas. Nunca ejecutar
-- contra sgcpc_db. No es una migración de esquema ni de datos permanentes --
-- por eso vive en pruebas/, no en bd/migraciones/ -- y se puede correr tantas
-- veces como haga falta para repetir la prueba (cada corrida exige que no haya
-- ninguna sesión ABIERTA todavía, ver el bloque de abajo).
--
-- Requisito antes de correrlo: que HOY no haya ninguna sesión de caja ABIERTA
-- (el índice único ux_sesiones_caja_una_abierta solo permite una fila ABIERTA
-- en toda la tabla sesiones_caja). Si hay una abierta, cerrarla primero desde
-- la pantalla /caja, normalmente, con su arqueo -- este script no la cierra
-- por su cuenta, para no pisar una caja real o una prueba en curso.
--
-- Cómo deshacerla después de probar: no hace falta ningún DELETE -- se cierra
-- igual que cualquier caja real, desde /caja, con "Cerrar caja" (deja su
-- propio registro de auditoría, coherente con el resto de las pruebas).
--
-- Ejecutar (ejemplo, ajustar si la contraseña cambió en bd.properties):
--   source ./entorno.sh
--   PGPASSWORD=$(grep '^bd.contrasena=' src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -v ON_ERROR_STOP=1 \
--     -f pruebas/rf15_caja_dia_anterior.sql
--
-- NO EJECUTAR TODAVÍA: a la espera de la aprobación explícita de Joel (pedido
-- del 11/10/2026: "Proponé... No lo ejecutes; esperá mi aprobación").

BEGIN;

DO $$
DECLARE
    ya_abierta INT;
BEGIN
    SELECT COUNT(*) INTO ya_abierta FROM sesiones_caja WHERE estado_sesion = 'ABIERTA';
    IF ya_abierta > 0 THEN
        RAISE EXCEPTION 'Ya hay una caja ABIERTA -- ciérrela primero desde /caja antes de simular la de ayer.';
    END IF;
END $$;

INSERT INTO sesiones_caja (id_cajero, fecha_hora_apertura, saldo_inicial, estado_sesion)
SELECT id_usuario, (CURRENT_DATE - INTERVAL '1 day') + TIME '08:00:00', 500000, 'ABIERTA'
FROM usuarios
WHERE nombre_usuario = 'cajera';

COMMIT;
