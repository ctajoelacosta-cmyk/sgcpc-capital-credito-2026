#!/usr/bin/env bash
# SGCPC - usar_base_pruebas.sh (D-113, Bloque 7/E10)
# Pone bd.properties.pruebas (sgcpc_db_pruebas, marca "entorno=pruebas") en el lugar de
# bd.properties, para que el próximo despliegue (Undeploy -> Clean and Build -> Run, REGLAS_DESARROLLO.md
# Sec. 7.11) y pruebas/humo_http.sh corran contra la base dedicada al humo -- nunca contra
# sgcpc_db. Antes de pisarlo, guarda el bd.properties actual como bd.properties.real (si todavía
# no hay uno guardado), para que bd/usar_base_real.sh lo pueda devolver después.
#
# Uso, desde sistema/:
#   bash bd/usar_base_pruebas.sh
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$CARPETA/../src/main/resources/configuracion"

if [ ! -f "$CONFIG/bd.properties.pruebas" ]; then
    echo "No se encontro $CONFIG/bd.properties.pruebas." >&2
    echo "Copiar bd.properties.pruebas.ejemplo a bd.properties.pruebas y completar la contrasena." >&2
    exit 1
fi

if [ ! -f "$CONFIG/bd.properties" ]; then
    echo "No se encontro $CONFIG/bd.properties." >&2
    exit 1
fi

if grep -q '^entorno=pruebas$' "$CONFIG/bd.properties"; then
    echo "== bd.properties ya apunta a la base de pruebas. =="
    exit 0
fi

cp "$CONFIG/bd.properties" "$CONFIG/bd.properties.real"
cp "$CONFIG/bd.properties.pruebas" "$CONFIG/bd.properties"

echo "== bd.properties ahora apunta a la base de pruebas (marca entorno=pruebas). =="
echo "== La base real quedo guardada en bd.properties.real. =="
echo "== Falta Undeploy -> Clean and Build -> Run para que Tomcat tome el cambio. =="
