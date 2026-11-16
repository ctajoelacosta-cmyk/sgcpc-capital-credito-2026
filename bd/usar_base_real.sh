#!/usr/bin/env bash
# SGCPC - usar_base_real.sh (D-113, Bloque 7/E10)
# Deshace bd/usar_base_pruebas.sh: devuelve bd.properties.real (la configuracion real, guardada
# antes del swap) a su lugar como bd.properties. Falta el mismo Undeploy -> Clean and Build ->
# Run de siempre para que Tomcat tome el cambio.
#
# Uso, desde sistema/:
#   bash bd/usar_base_real.sh
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$CARPETA/../src/main/resources/configuracion"

if [ ! -f "$CONFIG/bd.properties.real" ]; then
    echo "No se encontro $CONFIG/bd.properties.real -- no hay nada que restaurar." >&2
    echo "(Se genera con bd/usar_base_pruebas.sh la primera vez que se usa.)" >&2
    exit 1
fi

cp "$CONFIG/bd.properties.real" "$CONFIG/bd.properties"

echo "== bd.properties restaurado a la base real. =="
echo "== Falta Undeploy -> Clean and Build -> Run para que Tomcat tome el cambio. =="
