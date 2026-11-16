#!/usr/bin/env bash
# SGCPC - vaciar_prueba.sh (D-112, Bloque 7/E10, 7.2)
# Vacia el contenido de la base de PRUEBA "sgcpc_db_restaurada" (o la que se pase) cuando Joel
# termina de revisar una restauracion -- deja la base vacia y lista para el proximo
# bd/restaurar_prueba.sh, sin borrar la base en si (D-12: sgcpc_user no puede crear ni borrar
# bases). Mismo guarda que restaurar_prueba.sh: nunca corre sobre sgcpc_db.
#
# Uso, desde sistema/:
#   source ./entorno.sh && bash bd/vaciar_prueba.sh [base_prueba]
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPIEDADES="$CARPETA/../src/main/resources/configuracion/bd.properties"
BASE_PRUEBA="${1:-sgcpc_db_restaurada}"

if [ "$BASE_PRUEBA" = "sgcpc_db" ] || [ "$BASE_PRUEBA" = "sgcpc_db_pruebas" ]; then
    echo "== ABORTADO: nunca se vacia '$BASE_PRUEBA' con este script (D-113: esa es la del humo, este es solo para la de restauración). ==" >&2
    exit 1
fi

if [ ! -f "$PROPIEDADES" ]; then
    echo "No se encontro $PROPIEDADES." >&2
    exit 1
fi

leer_propiedad() {
    grep "^$1=" "$PROPIEDADES" | head -1 | cut -d= -f2-
}

BD_URL="$(leer_propiedad bd.url)"
BD_USUARIO="$(leer_propiedad bd.usuario)"
export PGPASSWORD="$(leer_propiedad bd.contrasena)"

BD_HOST="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')"
BD_PUERTO="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://[^:]+:([0-9]+).*#\1#')"

if [ -z "$PGPASSWORD" ]; then
    echo "bd.contrasena esta vacia en $PROPIEDADES." >&2
    exit 1
fi

echo "== Vaciando '$BASE_PRUEBA' (host $BD_HOST:$BD_PUERTO) =="
psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -v ON_ERROR_STOP=1 \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
echo "== Listo: '$BASE_PRUEBA' vacia =="
