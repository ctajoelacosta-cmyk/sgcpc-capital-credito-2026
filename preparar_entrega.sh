#!/usr/bin/env bash
# SGCPC - preparar_entrega.sh (PROPUESTA, sin correr todavia -- pedido de Joel, 10/10/2026)
#
# Copia el proyecto a una carpeta limpia, lista para entregar al tutor, y verifica que no quede
# ninguna mencion a inteligencia artificial.
#
# Como arma la copia: usa "git archive" sobre el ultimo commit (HEAD) -- exporta exactamente lo
# que esta versionado en git, así que YA excluye solo (sin lista para mantener a mano): todo lo
# que esta en .gitignore (CLAUDE.md, .claude/, target/, entrega/, bd.properties, logs locales,
# etc.) y todo archivo sin comitear. Encima de eso, bd/../.gitattributes (export-ignore) saca
# ademas AVANCE.md y docs/funcionamiento/ (notas internas del trabajo con Claude Code, no son
# parte de la tesis) -- ver ese archivo para la lista completa y ajustarla si hace falta.
#
# Por eso usa el ultimo COMMIT, no la carpeta de trabajo tal cual esta ahora: si hay cambios sin
# comitear, este script avisa y no los incluye -- comitear antes de preparar la entrega de verdad.
#
# Uso, desde sistema/:
#   bash preparar_entrega.sh [carpeta_destino]
# Carpeta por defecto: una hermana de "sistema/", fuera del proyecto y del repositorio
# (../entrega_sgcpc_<fecha_hora>), para que un "git add -A" accidental en sistema/ nunca la
# agarre.
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$CARPETA"

MARCA_TIEMPO="$(date +%Y%m%d_%H%M%S)"
DESTINO="${1:-$CARPETA/../entrega_sgcpc_${MARCA_TIEMPO}}"

if [ -e "$DESTINO" ]; then
    echo "== ABORTADO: '$DESTINO' ya existe. Elegir otro destino o borrarlo primero (a mano, revisando qué hay). ==" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "== AVISO: hay cambios sin comitear -- este script exporta el ULTIMO COMMIT, no la" >&2
    echo "   carpeta de trabajo actual. Esos cambios NO van a estar en la copia. ==" >&2
    echo "   Si hace falta que estén, comitear primero y volver a correr este script." >&2
    echo >&2
fi

echo "== Exportando el commit $(git rev-parse --short HEAD) a '$DESTINO' =="
mkdir -p "$DESTINO"
# --worktree-attributes: lee .gitattributes de la carpeta de trabajo, no solo del commit --
# así excluye AVANCE.md/docs/funcionamiento/CLAUDE.md aunque el .gitattributes que los agrega
# todavía no esté comiteado (verificado: sin el flag, un .gitattributes recién creado y sin
# comitear no tiene efecto en git archive).
git archive --worktree-attributes --format=tar HEAD | tar -x -C "$DESTINO"

echo
echo "== Buscando menciones a inteligencia artificial en la copia =="
# Librerías de terceros (AdminLTE, Bootstrap, jQuery, SweetAlert2, Font Awesome --
# REGLAS_DESARROLLO.md §3: archivos locales dentro del .war, nunca escritos por nosotros): se
# excluyen de la búsqueda -- son código público de la librería, no contenido propio, y sus
# .min.js/.map son líneas gigantes que no aportan nada acá (solo hacen lento y ruidoso el grep).
# Este mismo script se excluye también: su código tiene que nombrar los términos que busca.
EXCLUIR_VENDOR=(
    --exclude-dir=adminlte
    --exclude-dir=bootstrap
    --exclude-dir=jquery
    --exclude-dir=sweetalert2
    --exclude-dir=fontawesome
    --exclude=*.min.js
    --exclude=*.min.css
    --exclude=*.map
    --exclude=preparar_entrega.sh
)

# "IA" en mayusculas sueltas (palabra completa, con limite de palabra) -- a proposito en una
# pasada case-SENSITIVE aparte, para no confundir con "ia" adentro de palabras normales del
# espanol (guia, historia, compania, etc.).
ENCONTRADOS=0

if resultado_ia=$(grep -rIn "${EXCLUIR_VENDOR[@]}" -E '\bIA\b' "$DESTINO" 2>/dev/null); then
    echo "-- Coincidencias de 'IA' (palabra completa, mayusculas) --"
    echo "$resultado_ia"
    ENCONTRADOS=1
fi

if resultado_general=$(grep -rIni "${EXCLUIR_VENDOR[@]}" -E 'claude|anthropic|inteligencia artificial|chatgpt|openai|copilot' "$DESTINO" 2>/dev/null); then
    echo "-- Coincidencias de Claude/Anthropic/'inteligencia artificial'/otros nombres de IA --"
    echo "$resultado_general"
    ENCONTRADOS=1
fi

if [ "$ENCONTRADOS" -eq 1 ]; then
    echo >&2
    echo "== ENCONTRADO: hay menciones a inteligencia artificial en la copia (ver arriba). ==" >&2
    echo "   Revisar y corregir en el proyecto original (sistema/), comitear, y volver a" >&2
    echo "   correr este script -- no entregar '$DESTINO' tal como esta." >&2
    exit 1
fi

echo "Sin coincidencias."
echo
echo "== Lista: '$DESTINO' =="
