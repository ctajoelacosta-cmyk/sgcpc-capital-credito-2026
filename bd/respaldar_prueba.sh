#!/usr/bin/env bash
# SGCPC - respaldar_prueba.sh (PROPUESTA, sin correr todavia -- pedido de Joel, 10/10/2026)
#
# Respalda "sgcpc_db_pruebas" en dos formatos (mismo patron que bd/respaldar.sh, que solo
# respalda sgcpc_db), en una carpeta FUERA del proyecto -- nunca dentro de "sistema/", para que
# un respaldo jamas pueda terminar en un commit ni en el .war. pg_dump es de solo lectura: este
# script nunca modifica ninguna base.
#
# Guarda de seguridad (mismo patron que recrear_base.sh/D-113): este script SOLO corre si
# bd.properties apunta a "sgcpc_db_pruebas" -- nunca a sgcpc_db ni a ninguna otra base, aunque se
# lo invoque por error con la base real configurada.
#
# Carpeta de destino, en este orden: 1er argumento > variable de entorno
# SGCPC_CARPETA_RESPALDOS_PRUEBAS > "$HOME/respaldos_sgcpc_pruebas" por defecto.
#
# La contrasena se lee de bd.properties (nunca escrita en ningun archivo nuevo ni impresa en la
# salida) y se pasa a pg_dump via PGPASSWORD, mismo patron que respaldar.sh. Uso, desde sistema/:
#   source ./entorno.sh && bash bd/respaldar_prueba.sh [carpeta_destino]
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

# Guarda: este script solo respalda sgcpc_db_pruebas, nunca otra base -- ni siquiera sgcpc_db.
if [ "$BD_NOMBRE" != "sgcpc_db_pruebas" ]; then
    echo "== ABORTADO: bd.properties no apunta a sgcpc_db_pruebas (apunta a '$BD_NOMBRE'). ==" >&2
    exit 1
fi

CARPETA_DESTINO="${1:-${SGCPC_CARPETA_RESPALDOS_PRUEBAS:-$HOME/respaldos_sgcpc_pruebas}}"
mkdir -p "$CARPETA_DESTINO"

MARCA_TIEMPO="$(date +%Y%m%d_%H%M%S)"
ARCHIVO_CUSTOM="$CARPETA_DESTINO/sgcpc_db_pruebas_${MARCA_TIEMPO}.backup"
ARCHIVO_SQL="$CARPETA_DESTINO/sgcpc_db_pruebas_${MARCA_TIEMPO}.sql"

echo "== Respaldando '$BD_NOMBRE' (solo lectura, host $BD_HOST:$BD_PUERTO) en $CARPETA_DESTINO =="
pg_dump -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" --no-owner --no-privileges -F c -f "$ARCHIVO_CUSTOM" "$BD_NOMBRE"
pg_dump -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" --no-owner --no-privileges -f "$ARCHIVO_SQL" "$BD_NOMBRE"

echo "== Listo =="
echo "  $ARCHIVO_CUSTOM"
echo "  $ARCHIVO_SQL"
