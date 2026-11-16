#!/usr/bin/env bash
# SGCPC - recrear_base.sh
# Recrea el CONTENIDO de la base "sgcpc_db" (DROP SCHEMA public CASCADE +
# CREATE SCHEMA public) y corre 01, 02 y 03 en orden, conectado como
# sgcpc_user. Nunca crea ni borra la base en si (eso se hizo a mano en
# pgAdmin) ni toca ninguna otra base del servidor.
#
# La contraseña se lee de bd.properties (nunca esta escrita en este script)
# y se pasa a psql via PGPASSWORD. Uso, desde sistema/:
#   source ./entorno.sh && bash bd/recrear_base.sh
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

# Guarda E-10 (pedido de Joel, mismo criterio D-113 que pruebas/humo_http.sh): este script hace
# DROP SCHEMA public CASCADE -- nunca puede correr sobre sgcpc_db ni ninguna otra base real por
# error de configuracion. Sin flag para saltarsela: para la base oficial, F2 usa respaldo +
# migraciones (bd/migraciones/), nunca recrear_base.sh.
if [ "$BD_NOMBRE" != "sgcpc_db_pruebas" ]; then
    echo "== ABORTADO: recrear_base.sh solo puede correr sobre sgcpc_db_pruebas (bd.properties apunta a '$BD_NOMBRE'). ==" >&2
    exit 1
fi

echo "== Recreando el contenido de '$BD_NOMBRE' (usuario $BD_USUARIO, host $BD_HOST:$BD_PUERTO) =="
psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

for script in 01_esquema.sql 02_indices.sql 03_parametros.sql; do
    echo "== Ejecutando $script =="
    psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 \
        -f "$CARPETA/$script"
done

echo "== Listo: '$BD_NOMBRE' recreada con esquema, protecciones y parametros =="

# D-117 (pedido de Joel): recrear la base de pruebas deja gerente/oficial/cajera/administrador
# con los hashes fijos del seed (03_parametros.sql) -- si Joel ya tiene otras contraseñas
# guardadas en ~/.sgcpc_prueba (las que pruebas/humo_http.sh usa para loguearse de verdad), el
# primer login real tras recrear la base falla, y con RNF09 (3 intentos) puede terminar
# bloqueando la cuenta (pasó de verdad: D-117, Etapa 2; y de nuevo con administrador, que había
# quedado afuera de este restablecimiento -- 16/10/2026). Si esta es la base de pruebas y existe
# ~/.sgcpc_prueba, se reaplican solas ahora mismo -- nunca contra ninguna otra base (la propia
# RestablecerContrasenasPruebas aborta si bd.properties no tiene la marca entorno=pruebas,
# misma guarda que el cargador de 7.4). Nunca se imprime ninguna contraseña.
if grep -q '^entorno=pruebas$' "$PROPIEDADES" && [ -f "$HOME/.sgcpc_prueba" ]; then
    echo "== Reaplicando las contraseñas de ~/.sgcpc_prueba (base de pruebas) =="
    if [ -z "${SGCPC_USUARIO:-}" ] || [ -z "${SGCPC_CONTRASENA:-}" ]; then
        source "$HOME/.sgcpc_prueba"
    fi
    (cd "$CARPETA/.." && mvn -Dtest=RestablecerContrasenasPruebas test)
else
    echo "== No se reaplicaron contraseñas (no es la base de pruebas, o no existe ~/.sgcpc_prueba). =="
fi
