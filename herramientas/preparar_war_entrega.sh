#!/usr/bin/env bash
# SGCPC - herramientas/preparar_war_entrega.sh
#
# Arma LOS DOS .war de la entrega a partir de los que acaba de generar el Clean and Build, y los
# deja en entrega_final/:
#
#     sistema/target/sgcpc.war          ->  entrega_final/sgcpc.war       (el sistema web)
#     ../sgcpc-api/target/sgcpc-api.war ->  entrega_final/sgcpc-api.war   (la API de la app movil)
#
# Son dos proyectos Maven separados desde el 12/11/2026 (pedido del profesor) con un solo dueño de
# las reglas de negocio: sgcpc-api usa los servicios, DAO e infraestructura del SGCPC como
# dependencia (el jar de clases). Por eso este script, que vive en el proyecto SGCPC, prepara los
# dos: la entrega es el par, y los dos llevan el MISMO bd.properties.
#
# QUE HACE con cada .war, y en este orden:
#   1. Controles previos: que exista el .war, que el arbol de git de SU proyecto este limpio (src/
#      y pom.xml -- cada proyecto tiene su propio repositorio), y que el .war no sea mas viejo que
#      sus fuentes (un .war viejo es el error facil: se edita algo, no se vuelve a empaquetar y se
#      entrega lo anterior). Para sgcpc-api las fuentes incluyen tambien las del SGCPC: su
#      contenido sale del jar de clases de ese proyecto, asi que un cambio alla lo deja viejo.
#   2. Extrae el .war a una carpeta temporal.
#   3. Reemplaza WEB-INF/classes/configuracion/bd.properties por
#      herramientas/bd.properties.entrega (proyecto_final / app_user / contrasena vacia /
#      entorno=produccion). Esa plantilla esta VERSIONADA: no tiene contrasena, a proposito.
#      (Ojo: no puede vivir en herramientas/entrega/ -- el .gitignore ignora toda carpeta
#      llamada "entrega/", asi que ahi nunca se comitearia y el script fallaria en otra PC.)
#      Los dos .war lo llevan porque los dos abren la base; y los dos lo toman del MISMO archivo
#      de desarrollo (el pom de sgcpc-api lo declara como <resource> del proyecto SGCPC), asi que
#      no pueden terminar apuntando a bases distintas.
#   4. Comprueba que NO viajen bd.properties.real ni bd.properties.pruebas. Desde el 11/11/2026
#      los excluye el propio pom.xml (<packagingExcludes> de maven-war-plugin, la causa raiz del
#      hallazgo de seguridad del 26/10), asi que esto es una segunda red: si alguna vez aparecen,
#      el script los saca y avisa fuerte.
#   5. ADENTRO DE CADA JAR de WEB-INF/lib: ningun bd.properties, ninguna carpeta configuracion/.
#      Este control es nuevo (12/11/2026) y es el que cierra el agujero que abrio la separacion:
#      el jar de clases del SGCPC se arma desde target/classes, donde bd.properties.real y
#      bd.properties.pruebas SI estan. El <packagingExcludes> del .war no los saca de ahi, y
#      WEB-INF/lib es justo donde los controles de los puntos 3 y 4 no miran. Seria el hallazgo
#      del 26/10 otra vez, en un lugar nuevo. El pom del SGCPC excluye toda la carpeta
#      configuracion/ del jar (maven-jar-plugin); esto lo verifica sobre el .war ya armado.
#   6. Reempaqueta con "jar cfM" -- la M es a proposito: NO genera un MANIFEST nuevo, conserva
#      el que Maven dejo adentro. Despues verifica que el manifiesto del .war nuevo sea
#      byte a byte el mismo del original.
#   7. Copia a entrega_final/ (si habia uno, lo guarda como <nombre>.anterior).
#   8. Verificacion final sobre el .war YA ARMADO (no sobre la carpeta temporal): las 4 lineas de
#      bd.properties, la ausencia de .real/.pruebas, los jars limpios, el manifiesto y el tamano.
#
# QUE NO HACE, a proposito:
#   - NO corre "mvn clean package": el Clean and Build es siempre de Joel (regla del proyecto).
#     Acordarse del orden: primero sistema (deja el jar de clases en ~/.m2), despues sgcpc-api.
#   - NO toca proyecto_final.sql ni INSTALACION.md. La base de la entrega solo se regenera si
#     Joel lo pide -- ver ENTREGA_SERVIDOR.md, "Proximas entregas: solo un .war nuevo".
#   - NO toca nada de src/ ni de target/ de ninguno de los dos proyectos: los .war de target
#     quedan intactos, se trabaja sobre copias temporales que se borran al salir.
#
# Uso, desde sistema/:
#   bash herramientas/preparar_war_entrega.sh
#   bash herramientas/preparar_war_entrega.sh --permitir-sucio   (solo si Joel sabe por que)
#   bash herramientas/preparar_war_entrega.sh --solo-sistema     (sin la API; plan B)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
RAIZ="$(pwd)"
if [ -f ./entorno.sh ]; then
    # shellcheck disable=SC1091
    source ./entorno.sh
fi

PERMITIR_SUCIO="no"
SOLO_SISTEMA="no"
for argumento in "$@"; do
    case "$argumento" in
        --permitir-sucio) PERMITIR_SUCIO="si" ;;
        --solo-sistema)   SOLO_SISTEMA="si" ;;
        *) echo "Opcion desconocida: $argumento" >&2; exit 2 ;;
    esac
done

# Las variables de abajo existen SOLO para poder probar este script sin tocar la entrega de
# verdad (se le pasan otros .war de origen y otra carpeta de salida). En el uso normal no se
# definen y quedan en los valores de siempre.
WAR_ORIGEN="${SGCPC_WAR_ORIGEN:-target/sgcpc.war}"
RAIZ_API="${SGCPC_RAIZ_API:-$RAIZ/../sgcpc-api}"
WAR_ORIGEN_API="${SGCPC_WAR_ORIGEN_API:-$RAIZ_API/target/sgcpc-api.war}"
PLANTILLA_BD="herramientas/bd.properties.entrega"
CARPETA_ENTREGA="${SGCPC_CARPETA_ENTREGA:-entrega_final}"
RUTA_BD_EN_WAR="WEB-INF/classes/configuracion/bd.properties"

fallar() { echo "== ABORTADO: $* ==" >&2; exit 1; }

# "jar" es el del JDK de Windows: con una ruta de Git Bash (/c/Users/...) no encuentra el
# archivo. cygpath -m la traduce a la forma que Java entiende (C:/Users/...). Con rutas
# relativas no hace falta, pero este script tambien acepta rutas absolutas (SGCPC_WAR_ORIGEN).
ruta_para_jar() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
absoluta() { ( cd "$(dirname "$1")" && printf "%s/%s" "$(pwd)" "$(basename "$1")" ); }

# ---------------------------------------------------------------- controles comunes
command -v jar >/dev/null 2>&1 || fallar "no encuentro 'jar' (sale de JAVA_HOME/bin, lo pone entorno.sh)."
command -v unzip >/dev/null 2>&1 || fallar "no encuentro 'unzip' (lo usan las verificaciones)."
[ -f "$PLANTILLA_BD" ] || fallar "no existe $PLANTILLA_BD (es el bd.properties de la entrega, versionado)."

TEMPORAL_BASE="$(mktemp -d)"
limpiar() { rm -rf "$TEMPORAL_BASE"; }
trap limpiar EXIT

PROBLEMAS_TOTAL=0
RESUMEN=""

# ==========================================================================================
# preparar_un_war <nombre del .war> <.war de origen> <raiz del proyecto> <fuentes extra o "">
#
# Un solo cuerpo para los dos .war: lo que cambia es de donde sale cada uno y contra que arbol
# de git y que fuentes se controla. Asi no hay dos procedimientos que puedan desincronizarse.
# ==========================================================================================
preparar_un_war() {
    local nombre="$1"
    local origen="$2"
    local raiz_proyecto="$3"
    local fuentes_extra="$4"

    echo
    echo "=============================================================="
    echo "== $nombre"
    echo "=============================================================="

    # ------------------------------------------------------------ 1. controles previos
    [ -f "$origen" ] || fallar "no existe $origen. Corra primero el Clean and Build de ese proyecto
    (primero sistema, despues sgcpc-api: el segundo necesita el jar de clases del primero)."

    if [ "$PERMITIR_SUCIO" = "no" ] && command -v git >/dev/null 2>&1; then
        # Cada proyecto tiene su propio repositorio: se pregunta por el de ESTE .war.
        # Cambios SEGUIDOS por git (modificados/agregados/borrados): el .war de una entrega sale
        # de un commit. Los NO seguidos se miran aparte, porque no todos importan igual.
        local sucios
        sucios="$(git -C "$raiz_proyecto" status --porcelain --untracked-files=no -- src pom.xml | wc -l | tr -d ' ')"
        if [ "$sucios" != "0" ]; then
            git -C "$raiz_proyecto" status --short --untracked-files=no -- src pom.xml >&2
            fallar "hay cambios sin comitear en src/ o pom.xml de $raiz_proyecto. El .war de una
    entrega sale de un commit, no del escritorio. Comitee (o use --permitir-sucio si sabe por que)."
        fi
        # Un archivo nuevo sin comitear en src/main SI viaja en el .war, asi que frena la entrega.
        # En src/test no: ahi viven las clases *Temporal, que a proposito nunca se comitean y
        # nunca se empaquetan (maven-war-plugin solo toma src/main/webapp y target/classes).
        local nuevos_main
        nuevos_main="$(git -C "$raiz_proyecto" ls-files --others --exclude-standard -- src/main | head -5)"
        if [ -n "$nuevos_main" ]; then
            echo "$nuevos_main" | sed 's/^/   /' >&2
            fallar "hay archivos nuevos sin comitear en src/main de $raiz_proyecto: esos SI entran al .war."
        fi
    fi

    # El .war no puede ser mas viejo que las fuentes de su proyecto. Y para sgcpc-api, tampoco que
    # las del SGCPC: su contenido sale del jar de clases de ese proyecto, asi que un cambio en un
    # servicio de alla lo deja viejo sin que su propio src/ se haya tocado.
    local mas_nuevo
    mas_nuevo="$(find "$raiz_proyecto/src" "$raiz_proyecto/pom.xml" -type f -newer "$origen" -print -quit 2>/dev/null || true)"
    if [ -z "$mas_nuevo" ] && [ -n "$fuentes_extra" ]; then
        mas_nuevo="$(find "$fuentes_extra/src" "$fuentes_extra/pom.xml" -type f -newer "$origen" -print -quit 2>/dev/null || true)"
    fi
    if [ -n "$mas_nuevo" ]; then
        fallar "$origen es mas viejo que '$mas_nuevo'. Ese .war no tiene los ultimos cambios:
    corra el Clean and Build otra vez (y si el cambio es del proyecto SGCPC, los dos) antes de
    preparar la entrega."
    fi

    # ------------------------------------------------------------ 2. extraer
    local temporal="$TEMPORAL_BASE/$nombre"
    mkdir -p "$temporal"
    echo "   origen : $origen ($(wc -c < "$origen") bytes)"
    local origen_abs
    origen_abs="$(absoluta "$origen")"
    ( cd "$temporal" && jar xf "$(ruta_para_jar "$origen_abs")" )

    [ -f "$temporal/$RUTA_BD_EN_WAR" ] || fallar "$nombre no trae $RUTA_BD_EN_WAR: algo cambio en el empaquetado."
    local manifiesto_original="$TEMPORAL_BASE/manifiesto_$nombre.mf"
    [ -f "$temporal/META-INF/MANIFEST.MF" ] || fallar "$nombre no trae META-INF/MANIFEST.MF."
    cp "$temporal/META-INF/MANIFEST.MF" "$manifiesto_original"

    # ------------------------------------------------------------ 3. bd.properties de la entrega
    cp "$PLANTILLA_BD" "$temporal/$RUTA_BD_EN_WAR"
    echo "   bd.properties reemplazado por el de la entrega:"
    sed 's/^/     | /' "$PLANTILLA_BD" | grep -v '^     | #'

    # ------------------------------------------------------------ 4. los dos que no deben viajar
    local sobrantes=0
    local extra
    for extra in bd.properties.real bd.properties.pruebas; do
        if [ -f "$temporal/WEB-INF/classes/configuracion/$extra" ]; then
            rm -f "$temporal/WEB-INF/classes/configuracion/$extra"
            echo "   !! $extra VENIA en $nombre y se saco. Revise el <packagingExcludes> del pom.xml." >&2
            sobrantes=$((sobrantes + 1))
        fi
    done
    [ "$sobrantes" = "0" ] && echo "   bd.properties.real / .pruebas: no venian (los excluye el pom.xml)"

    # ------------------------------------------------------------ 5. adentro de cada jar
    # El control nuevo del 12/11/2026. Se mira TODO jar de WEB-INF/lib, no solo el de clases del
    # SGCPC: el dia que alguien agregue otro jar propio, ya esta cubierto.
    local jars_sucios=""
    local cantidad_jars=0
    local jar_lib
    if [ -d "$temporal/WEB-INF/lib" ]; then
        for jar_lib in "$temporal/WEB-INF/lib"/*.jar; do
            [ -f "$jar_lib" ] || continue
            cantidad_jars=$((cantidad_jars + 1))
            local adentro
            adentro="$(unzip -Z1 "$jar_lib" 2>/dev/null | grep -E 'bd\.properties|^configuracion/' || true)"
            if [ -n "$adentro" ]; then
                jars_sucios="$jars_sucios $(basename "$jar_lib")"
                echo "   !! $(basename "$jar_lib") trae archivos de configuracion adentro:" >&2
                echo "$adentro" | sed 's/^/        /' >&2
            fi
        done
    fi
    if [ -n "$jars_sucios" ]; then
        fallar "hay jars con configuracion adentro en $nombre:$jars_sucios
    Esos archivos tienen la contrasena de la base y WEB-INF/lib es justo donde no se mira.
    Revise el <excludes> del maven-jar-plugin en el pom.xml del proyecto SGCPC."
    fi
    echo "   los $cantidad_jars jar de WEB-INF/lib: sin bd.properties ni configuracion/ adentro"

    # ------------------------------------------------------------ 6. reempaquetar
    local war_nuevo="$TEMPORAL_BASE/nuevo_$nombre"
    rm -f "$war_nuevo"
    ( cd "$temporal" && jar cfM "$(ruta_para_jar "$war_nuevo")" . )

    # ------------------------------------------------------------ 7. copiar a entrega_final/
    mkdir -p "$CARPETA_ENTREGA"
    local destino="$CARPETA_ENTREGA/$nombre"
    if [ -f "$destino" ]; then
        mv "$destino" "$destino.anterior"
        echo "   el .war anterior quedo como $destino.anterior"
    fi
    cp "$war_nuevo" "$destino"

    # ------------------------------------------------------------ 8. verificacion del resultado
    local verifica="$TEMPORAL_BASE/verificacion_$nombre"
    mkdir -p "$verifica"
    # La ruta absoluta se calcula ACA, no adentro del parentesis: ahi dentro ya se hizo el cd a
    # la carpeta temporal, y "absoluta" resolveria "entrega_final/sgcpc.war" contra ESA carpeta.
    # Con una carpeta de salida relativa -- que es justo la de uso normal -- fallaba con
    # "cd: entrega_final: No such file or directory". La prueba del 12/11 no lo vio porque le
    # paso una carpeta ABSOLUTA: una prueba que no usa el valor por defecto no prueba el camino
    # de todos los dias.
    local destino_abs
    destino_abs="$(absoluta "$destino")"
    ( cd "$verifica" && unzip -o -q "$destino_abs" "META-INF/MANIFEST.MF" "$RUTA_BD_EN_WAR" )

    local problemas=0
    revisar() {
        if [ "$2" = "ok" ]; then printf "   [ok]    %s\n" "$1"; else printf "   [FALLA] %s\n" "$1"; problemas=$((problemas + 1)); fi
    }
    local bd_en_war="$verifica/$RUTA_BD_EN_WAR"
    revisar "bd.properties apunta a proyecto_final" \
        "$(grep -q '^bd.url=jdbc:postgresql://localhost:5432/proyecto_final$' "$bd_en_war" && echo ok)"
    revisar "bd.usuario = app_user" "$(grep -q '^bd.usuario=app_user$' "$bd_en_war" && echo ok)"
    revisar "bd.contrasena vacia (la completa el tecnico)" "$(grep -q '^bd.contrasena=$' "$bd_en_war" && echo ok)"
    revisar "entorno = produccion" "$(grep -q '^entorno=produccion$' "$bd_en_war" && echo ok)"
    revisar "no viajan bd.properties.real / .pruebas" \
        "$(unzip -l "$destino" 2>/dev/null | grep -qE 'bd\.properties\.(real|pruebas)$' || echo ok)"
    revisar "ningun jar de WEB-INF/lib trae configuracion adentro" \
        "$([ -z "$jars_sucios" ] && echo ok)"
    revisar "el MANIFEST.MF es el que dejo Maven" \
        "$(cmp -s "$verifica/META-INF/MANIFEST.MF" "$manifiesto_original" && echo ok)"
    local tamano_origen tamano_destino
    tamano_origen="$(wc -c < "$origen")"
    tamano_destino="$(wc -c < "$destino")"
    revisar "tamano razonable ($tamano_destino bytes, origen $tamano_origen)" \
        "$([ "$tamano_destino" -gt $((tamano_origen * 80 / 100)) ] && echo ok)"

    PROBLEMAS_TOTAL=$((PROBLEMAS_TOTAL + problemas))
    if [ "$problemas" = "0" ]; then
        RESUMEN="$RESUMEN
   [ok]    $destino ($tamano_destino bytes)"
    else
        RESUMEN="$RESUMEN
   [FALLA] $destino -- $problemas verificacion(es) en falla"
    fi
}

echo "== Preparando los .war de entrega =="
preparar_un_war "sgcpc.war" "$WAR_ORIGEN" "$RAIZ" ""
if [ "$SOLO_SISTEMA" = "si" ]; then
    echo
    echo "   (--solo-sistema: la API no se preparo. La aplicacion movil NO funciona sin ella.)"
else
    preparar_un_war "sgcpc-api.war" "$WAR_ORIGEN_API" "$RAIZ_API" "$RAIZ"
fi

echo
echo "=============================================================="
if [ "$PROBLEMAS_TOTAL" = "0" ]; then
    echo "== LISTO ==$RESUMEN"
    echo
    echo "   Los dos .war van al mismo Tomcat: /sgcpc y /sgcpc-api, contra la misma base."
    echo "   Falta solo lo que NO hace este script: proyecto_final.sql e INSTALACION.md se tocan"
    echo "   unicamente si Joel lo pide (ver docs/entregas/ENTREGA_SERVIDOR.md)."
else
    echo "== $PROBLEMAS_TOTAL verificacion(es) en FALLA ==$RESUMEN" >&2
    echo "   NO entregue estos .war hasta revisarlo." >&2
    exit 1
fi
