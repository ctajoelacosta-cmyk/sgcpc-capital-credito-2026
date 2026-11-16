#!/usr/bin/env bash
# SGCPC - preparar_entrega_servidor.sh (PROPUESTA, sin correr todavia -- pedido de Joel, 26/10/2026)
#
# Repite en un solo paso el procedimiento manual documentado en docs/entregas/ENTREGA_SERVIDOR.md
# ("Como se armo el .war de entrega") para preparar una entrega nueva al servidor de la
# institucion (Ubuntu 24.04 / Tomcat 9 / PostgreSQL 16). El nombre de la base y el usuario de
# conexion son las variables BD_NOMBRE/BD_USUARIO de mas abajo (pedido del encargado del
# servidor, 26/10/2026: "proyecto_final"/"app_user" chocaba con otros proyectos en la misma
# PostgreSQL -- ahora "sgcpc_joel_acosta"/"joel_acosta_app"). Referencia obligatoria: leer ese
# documento antes de correr esto.
#
# Que hace, en orden:
#   1. Verifica que no haya cambios sin comitear (el .war tiene que salir del ULTIMO COMMIT).
#   2. Compila con "mvn clean package" (igual que se hizo a mano el 26/10/2026: BUILD SUCCESS
#      con el bd.properties normal de esta PC, apuntando a sgcpc_db_pruebas/sgcpc_user).
#   3. Extrae target/sgcpc.war a una carpeta temporal con "jar xf" (no hay "zip"/"unzip" en
#      este entorno -- mismo motivo que preparar_entrega.sh).
#   4. Reemplaza WEB-INF/classes/configuracion/bd.properties por la version de entrega:
#      bd.url=jdbc:postgresql://localhost:5432/$BD_NOMBRE, bd.usuario=$BD_USUARIO,
#      bd.contrasena= (vacia a proposito -- la completa el tecnico), entorno=produccion.
#   5. Saca bd.properties.real y bd.properties.pruebas si quedaron copiados (hallazgo de
#      seguridad ya documentado: el plugin resources:resources copia toda la carpeta
#      src/main/resources/configuracion/, no solo bd.properties). Causa raiz (excluirlos en
#      pom.xml) sigue pendiente de decision de Joel -- este script no toca el pom.xml.
#   6. Busca menciones a inteligencia artificial dentro del .war ya parchado (mismos terminos y
#      mismas exclusiones de librerias de terceros que preparar_entrega.sh).
#   7. Vuelve a empaquetar con "jar cf" desde la carpeta ya parchada (preserva el
#      META-INF/MANIFEST.MF original de Maven, igual que se hizo a mano) y deja el resultado en
#      entrega_vX.Y/sgcpc.war.
#
# Lo que NO hace (a proposito, fuera de alcance de este script):
#   - No toca pom.xml ni ningun archivo de src/.
#   - No genera ni toca proyecto_final.sql ni INSTALACION.md (ENTREGA_SERVIDOR.md: "Proximas
#     entregas: solo un .war nuevo" -- esos dos solo cambian si Joel lo pide expresamente).
#   - No copia nada a entrega_final/ ni hace tags de git.
#
# Uso, desde sistema/:
#   bash preparar_entrega_servidor.sh <version>
# Ejemplo:
#   bash preparar_entrega_servidor.sh 1.1
# Crea "sistema/entrega_v1.1/sgcpc.war". Carpeta nueva siempre (si ya existe, aborta -- no
# pisa una entrega anterior).
#
# NOTA pendiente para Joel: "entrega_v1.0-estable/" y "entrega_final/" estan en .gitignore por
# nombre exacto; una carpeta "entrega_vX.Y/" con una version nueva NO queda excluida todavia.
# Falta agregar un patron generico (por ejemplo "entrega_v*/") al .gitignore -- no se hizo en
# este script porque no se pidio tocar ese archivo; avisar antes de dejar correr esto, para no
# comitear un .war por accidente.
set -euo pipefail

# Nombre de la base y usuario de conexion en el servidor de destino (pedido del encargado del
# servidor, 26/10/2026 -- "proyecto_final"/"app_user" chocaba con otros proyectos). Cambiar
# solo estas dos lineas si el servidor de destino vuelve a pedir otros nombres.
BD_NOMBRE="sgcpc_joel_acosta"
BD_USUARIO="joel_acosta_app"

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$CARPETA"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
    echo "Uso: bash preparar_entrega_servidor.sh <version>" >&2
    echo "Ejemplo: bash preparar_entrega_servidor.sh 1.1" >&2
    exit 1
fi

DESTINO="$CARPETA/entrega_v${VERSION}"
if [ -e "$DESTINO" ]; then
    echo "== ABORTADO: '$DESTINO' ya existe. Elegir otra version o borrarla primero (a mano, revisando que hay). ==" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "== ABORTADO: hay cambios sin comitear. ==" >&2
    echo "   Este script compila la carpeta de trabajo tal cual esta, y el .war de entrega tiene" >&2
    echo "   que salir del ULTIMO COMMIT (ver ENTREGA_SERVIDOR.md). Comitear o descartar los" >&2
    echo "   cambios pendientes antes de volver a correr esto." >&2
    exit 1
fi

COMMIT="$(git rev-parse --short HEAD)"
echo "== Compilando el commit $COMMIT con 'mvn clean package' =="
source ./entorno.sh
mvn clean package

WAR_ORIGEN="$CARPETA/target/sgcpc.war"
if [ ! -f "$WAR_ORIGEN" ]; then
    echo "== ABORTADO: no se encontro '$WAR_ORIGEN' despues de compilar. ==" >&2
    exit 1
fi

TMP_EXTRACCION="$(mktemp -d)"
trap 'rm -rf "$TMP_EXTRACCION"' EXIT

echo
echo "== Extrayendo '$WAR_ORIGEN' =="
(cd "$TMP_EXTRACCION" && jar xf "$WAR_ORIGEN")

BD_PROPERTIES="$TMP_EXTRACCION/WEB-INF/classes/configuracion/bd.properties"
if [ ! -f "$BD_PROPERTIES" ]; then
    echo "== ABORTADO: no se encontro '$BD_PROPERTIES' dentro del .war extraido. ==" >&2
    exit 1
fi

echo "== Reemplazando bd.properties por la version de entrega (produccion, $BD_USUARIO, sin contrasena) =="
cat > "$BD_PROPERTIES" <<EOF
bd.url=jdbc:postgresql://localhost:5432/$BD_NOMBRE
bd.usuario=$BD_USUARIO
bd.contrasena=
entorno=produccion
EOF

for ARCHIVO in bd.properties.real bd.properties.pruebas; do
    RUTA="$TMP_EXTRACCION/WEB-INF/classes/configuracion/$ARCHIVO"
    if [ -f "$RUTA" ]; then
        rm -f "$RUTA"
        echo "   Sacado del .war: $ARCHIVO (hallazgo de seguridad conocido, ver ENTREGA_SERVIDOR.md)"
    fi
done

echo
echo "== Buscando menciones a inteligencia artificial dentro del .war ya parchado =="
# Mismas exclusiones de librerias de terceros que preparar_entrega.sh (AdminLTE, Bootstrap,
# jQuery, SweetAlert2, Font Awesome -- codigo publico de la libreria, no contenido propio).
EXCLUIR_VENDOR=(
    --exclude-dir=adminlte
    --exclude-dir=bootstrap
    --exclude-dir=jquery
    --exclude-dir=sweetalert2
    --exclude-dir=fontawesome
    --exclude=*.min.js
    --exclude=*.min.css
    --exclude=*.map
)

ENCONTRADOS=0

if resultado_ia=$(grep -rIn "${EXCLUIR_VENDOR[@]}" -E '\bIA\b' "$TMP_EXTRACCION" 2>/dev/null); then
    echo "-- Coincidencias de 'IA' (palabra completa, mayusculas) --"
    echo "$resultado_ia"
    ENCONTRADOS=1
fi

if resultado_general=$(grep -rIni "${EXCLUIR_VENDOR[@]}" -E 'claude|anthropic|inteligencia artificial|chatgpt|openai|copilot' "$TMP_EXTRACCION" 2>/dev/null); then
    echo "-- Coincidencias de Claude/Anthropic/'inteligencia artificial'/otros nombres de IA --"
    echo "$resultado_general"
    ENCONTRADOS=1
fi

if [ "$ENCONTRADOS" -eq 1 ]; then
    echo >&2
    echo "== ENCONTRADO: hay menciones a inteligencia artificial en el .war (ver arriba). ==" >&2
    echo "   Revisar y corregir en el proyecto original, comitear, y volver a correr este" >&2
    echo "   script -- no entregar un .war con estas coincidencias." >&2
    exit 1
fi

echo "Sin coincidencias."

echo
echo "== Empaquetando el .war de entrega =="
mkdir -p "$DESTINO"
(cd "$TMP_EXTRACCION" && jar cf "$DESTINO/sgcpc.war" .)

echo
echo "== Lista: '$DESTINO/sgcpc.war' (del commit $COMMIT) =="
echo "   proyecto_final.sql e INSTALACION.md NO se tocaron -- solo cambian si Joel lo pide."
