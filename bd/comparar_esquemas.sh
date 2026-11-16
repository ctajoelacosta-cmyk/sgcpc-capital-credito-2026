#!/usr/bin/env bash
# SGCPC - comparar_esquemas.sh (D-113, AVANCE.md: nunca cambios de estructura a mano)
# Compara SOLO la estructura (pg_dump --schema-only: tablas, columnas, índices, triggers,
# funciones -- nunca datos) de "sgcpc_db" contra otra base (por defecto sgcpc_db_pruebas) --
# para confirmar que nunca se aplicó un cambio de esquema a mano en una sola base, sin pasar por
# bd/migraciones/ y sin incorporarlo a 01_esquema.sql/02_indices.sql/03_parametros.sql. Nunca
# modifica ninguna base: pg_dump es de solo lectura.
#
# Uso, desde sistema/:
#   source ./entorno.sh && bash bd/comparar_esquemas.sh [base_a_comparar]
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPIEDADES="$CARPETA/../src/main/resources/configuracion/bd.properties"
BASE_A_COMPARAR="${1:-sgcpc_db_pruebas}"

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

if [ "$BASE_A_COMPARAR" = "sgcpc_db" ]; then
    echo "== ABORTADO: nada que comparar, sgcpc_db contra sí misma. ==" >&2
    exit 1
fi

DUMP_SGCPC_DB="$(mktemp)"
DUMP_COMPARAR="$(mktemp)"
trap 'rm -f "$DUMP_SGCPC_DB" "$DUMP_COMPARAR"' EXIT

# --no-owner/--no-privileges: ignora ALTER ... OWNER TO / GRANT/REVOKE (ya verificado que
# sgcpc_user es dueño de todo en las tres bases, D-112/D-113 -- comparar eso otra vez acá sería
# ruido). Filtra las líneas de encabezado con la versión/fecha del volcado en sí, y las líneas
# \restrict/\unrestrict (pg_dump 18+: token de seguridad aleatorio por corrida) -- ninguna de las
# dos es parte de la estructura real, nunca van a coincidir entre dos corridas aunque la
# estructura sea idéntica.
volcar_estructura() {
    local base="$1"
    pg_dump -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" --schema-only --no-owner --no-privileges \
        -d "$base" | grep -v -E '^-- (Dumped from|Dumped by|Started on|Completed on)|^[\]restrict|^[\]unrestrict'
}

echo "== Volcando la estructura de 'sgcpc_db' =="
volcar_estructura "sgcpc_db" > "$DUMP_SGCPC_DB"

echo "== Volcando la estructura de '$BASE_A_COMPARAR' =="
volcar_estructura "$BASE_A_COMPARAR" > "$DUMP_COMPARAR"

echo ""
if diff -q "$DUMP_SGCPC_DB" "$DUMP_COMPARAR" > /dev/null; then
    echo "== OK: la estructura de 'sgcpc_db' y '$BASE_A_COMPARAR' es idéntica. =="
else
    echo "== DIFERENCIAS entre 'sgcpc_db' y '$BASE_A_COMPARAR' (< sgcpc_db, > $BASE_A_COMPARAR): =="
    diff -u "$DUMP_SGCPC_DB" "$DUMP_COMPARAR" || true
    exit 1
fi
