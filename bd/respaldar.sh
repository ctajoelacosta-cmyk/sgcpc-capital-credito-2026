#!/usr/bin/env bash
# SGCPC - respaldar.sh (D-112, Bloque 7/E10, 7.2)
# Respalda sgcpc_db en dos formatos (Doc. 00 Sec. 14.4: backup custom para restaurar rapido +
# SQL plano por si cambia la version de PostgreSQL), en una carpeta FUERA del proyecto -- nunca
# dentro de "sistema/", para que un respaldo real jamas pueda terminar en un commit ni en el
# .war. pg_dump es de solo lectura: este script nunca modifica sgcpc_db ni ninguna otra base.
#
# Carpeta de destino, en este orden: 1er argumento > variable de entorno
# SGCPC_CARPETA_RESPALDOS > "$HOME/respaldos_sgcpc" por defecto -- configurable a proposito para
# poder reusar este mismo script en el servidor Ubuntu de la defensa con otra ruta (Bloque 3).
#
# La contrasena se lee de bd.properties (nunca escrita en ningun archivo nuevo ni impresa en la
# salida) y se pasa a pg_dump via PGPASSWORD, mismo patron que recrear_base.sh. Uso, desde
# sistema/:
#   source ./entorno.sh && bash bd/respaldar.sh [carpeta_destino]
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPIEDADES="$CARPETA/../src/main/resources/configuracion/bd.properties"

if [ ! -f "$PROPIEDADES" ]; then
    echo "No se encontro $PROPIEDADES." >&2
    echo "Copiar bd.properties.ejemplo a bd.properties y completar la contrasena." >&2
    exit 1
fi

leer_propiedad() {
    grep "^$1=" "$PROPIEDADES" | head -1 | cut -d= -f2-
}

BD_URL="$(leer_propiedad bd.url)"
BD_USUARIO="$(leer_propiedad bd.usuario)"
export PGPASSWORD="$(leer_propiedad bd.contrasena)"

# BD_URL con forma jdbc:postgresql://host:puerto/nombre_base
BD_HOST="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')"
BD_PUERTO="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://[^:]+:([0-9]+).*#\1#')"
BD_NOMBRE="$(echo "$BD_URL" | sed -E 's#.*/([^/?]+).*#\1#')"

if [ -z "$PGPASSWORD" ]; then
    echo "bd.contrasena esta vacia en $PROPIEDADES." >&2
    exit 1
fi

# Guarda D-75: este script solo respalda sgcpc_db, nunca otra base -- si bd.properties llegara a
# apuntar a otro lado (ej. sgcpc_db_restaurada, por error de configuracion), se aborta.
if [ "$BD_NOMBRE" != "sgcpc_db" ]; then
    echo "== ABORTADO: bd.properties no apunta a sgcpc_db (apunta a '$BD_NOMBRE'). ==" >&2
    exit 1
fi

CARPETA_DESTINO="${1:-${SGCPC_CARPETA_RESPALDOS:-$HOME/respaldos_sgcpc}}"
mkdir -p "$CARPETA_DESTINO"

MARCA_TIEMPO="$(date +%Y%m%d_%H%M%S)"
ARCHIVO_CUSTOM="$CARPETA_DESTINO/sgcpc_${MARCA_TIEMPO}.backup"
ARCHIVO_SQL="$CARPETA_DESTINO/sgcpc_${MARCA_TIEMPO}.sql"

echo "== Respaldando '$BD_NOMBRE' (solo lectura, host $BD_HOST:$BD_PUERTO) en $CARPETA_DESTINO =="
# Portabilidad (pedido de Joel): --no-owner --no-privileges -- sin estas dos banderas, el
# respaldo queda con "ALTER ... OWNER TO sgcpc_user" (y los GRANT/REVOKE que haya) grabados con
# ese nombre fijo; al restaurar en otra PostgreSQL (el docente, con otro usuario), eso falla si
# ese rol no existe ahí. Con las banderas, el respaldo no lleva ningún nombre de usuario: al
# restaurarlo, todo queda de quien sea que corra la restauración, cualquiera sea su nombre.
pg_dump -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" --no-owner --no-privileges -F c -f "$ARCHIVO_CUSTOM" "$BD_NOMBRE"
pg_dump -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" --no-owner --no-privileges -f "$ARCHIVO_SQL" "$BD_NOMBRE"

echo "== Listo =="
echo "  $ARCHIVO_CUSTOM"
echo "  $ARCHIVO_SQL"
echo ""
echo "Recordatorio (D-110): pg_dump NO copia el 'ALTER DATABASE sgcpc_db SET timezone' (vive en"
echo "bd/01_esquema.sql, no es un dato) -- al levantar la base en un servidor nuevo, correrlo aparte."
