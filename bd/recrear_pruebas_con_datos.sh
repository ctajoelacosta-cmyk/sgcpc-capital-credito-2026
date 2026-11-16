#!/usr/bin/env bash
# SGCPC - recrear_pruebas_con_datos.sh (PROPUESTA, sin correr todavia -- pedido de Joel, 10/10/2026)
#
# Deja "sgcpc_db_pruebas" en un estado conocido de punta a punta, para trabajar mas seguro:
#   1) Respaldo de "sgcpc_db_pruebas" tal como esta ahora (bd/respaldar_prueba.sh).
#   2) Recrea esquema + indices + parametros (bd/recrear_base.sh) -- deja los 4 perfiles con sus
#      contrasenas conocidas (ver mas abajo); si existe ~/.sgcpc_prueba, recrear_base.sh ya
#      reaplica esas contrasenas solo, automaticamente (D-117).
#   3) Carga los 11 escenarios de demostracion (clientes, solicitudes, prestamos, caja, cobros,
#      una reversion autorizada -- D-115) con los servicios reales de la aplicacion, nunca con
#      INSERT directo, para que numeracion/auditoria/PDF salgan igual que en una operacion real.
#
# No inventa datos de prueba nuevos: reusa CargadorDatosPrueba (ya existe, D-115) en vez de
# duplicar esa logica en SQL a mano.
#
# Guarda de seguridad: cada paso ya se niega por si solo a correr fuera de sgcpc_db_pruebas
# (recrear_base.sh, CargadorDatosPrueba via FabricaConexionBD.esBaseDePruebas()); este script no
# agrega ninguna excepcion a esas guardas.
#
# Uso, desde sistema/ (con bd.properties ya apuntando a sgcpc_db_pruebas):
#   source ./entorno.sh && bash bd/recrear_pruebas_con_datos.sh
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "== Paso 1/3: respaldo de sgcpc_db_pruebas =="
bash "$CARPETA/respaldar_prueba.sh"

echo
echo "== Paso 2/3: recrear esquema + indices + parametros =="
bash "$CARPETA/recrear_base.sh"

echo
echo "== Paso 3/3: cargar los 11 escenarios de demostracion (D-115) =="
(cd "$CARPETA/.." && mvn -Dtest=CargadorDatosPrueba test)

echo
echo "== Listo: sgcpc_db_pruebas recreada con datos semilla conocidos. =="
echo
echo "Perfiles y contrasenas (seed, bd/03_parametros.sql -- cambian al primer ingreso):"
echo "  administrador  CambiarAdmin@2026"
echo "  gerente        Cambiar@2026"
echo "  oficial        Cambiar@2026"
echo "  cajera         Cambiar@2026"
echo "(si existe ~/.sgcpc_prueba, el paso 2 ya reaplico esas contrasenas en vez de las de arriba)"
