#!/usr/bin/env bash
# SGCPC - pruebas/humo_http.sh
#
# Prueba de humo HTTP real, de punta a punta, contra un Tomcat ya
# desplegado (por defecto http://localhost:8081/sgcpc). Recorre las rutas
# de E0 a E3 y compara el codigo HTTP obtenido contra el esperado; ademas
# revisa el CONTENIDO de las respuestas (mensaje de exito/error esperado)
# y, cuando corresponde, confirma con un SELECT de solo lectura que el
# registro quedo en la base.
#
# Requisito (REGLAS_DESARROLLO.md §7, D-52): correr esta prueba y que de OK en todo
# antes de avisar que una etapa esta lista para que Joel la pruebe.
#
# REGLA (12/10/2026, PA-22, pedido de Joel): correr esto SOLO si no hay ninguna caja
# ABIERTA en este momento -- confirmar con un SELECT de solo lectura antes de arrancar
# (SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'). Un run anterior (antes
# del chequeo que ahora tienen Bloque 4/Cobranza/E6/Bloque 5) entrego efectivo de prueba a
# una caja real que Joel tenia abierta y la cerro con un "saldo fisico" calculado por SQL, sin
# que nadie la contara (ver AVANCE.md PA-22). Los bloques de caja ya abortan solos si
# encuentran una abierta, pero esta regla es ademas operativa: si Joel esta usando la caja en
# pantalla en este momento, no correr el humo.
#
# El usuario/contrasena NUNCA se escriben aca: se leen de las variables de
# entorno SGCPC_USUARIO / SGCPC_CONTRASENA (perfil con acceso total, para
# que todas las rutas de la primera pasada den el codigo esperado). Si
# ademas estan SGCPC_USUARIO_OFICIAL / SGCPC_CONTRASENA_OFICIAL, se agrega
# una pasada con perfil OFICIAL_CREDITO (crea y envia una solicitud de
# credito, P04 -- GESTIONAR_SOLICITUD es NINGUNO para el gerente, asi que
# la pasada principal no puede probar esa parte). Si ademas estan
# SGCPC_USUARIO_CAJERA / SGCPC_CONTRASENA_CAJERA, se agrega otra pasada con
# ese perfil (solo lectura en Clientes, sin acceso a Documentos/Usuarios/
# Parametros/Solicitudes/Aprobaciones). Las pasadas de oficial y cajera son
# opcionales: si no estan, se omiten con un aviso (no es un fallo) -- pero
# sin la de oficial, tambien se omiten las verificaciones de aprobar/
# rechazar de la pasada de gerente (no hay ninguna solicitud
# PENDIENTE_APROBACION para actuar sobre ella).
#
# Usa cedulas de prueba que empiezan con 9 (9 + hora:minuto:segundo, para
# que cada corrida use una distinta) para no mezclarse con los datos que
# carga Joel. No corre nada que modifique datos que Joel este usando (no
# crea usuarios del sistema, no cambia parametros_sistema, no cambia la
# contrasena real de nadie). Los clientes de prueba (cedula 9xxxxxx) SI
# quedan en la base de desarrollo -- a proposito (D-55, AVANCE.md): se
# limpian recien en E10, junto con el resto de los datos de prueba.
#
# Las credenciales de acceso a la aplicacion se leen de ~/.sgcpc_prueba
# (fuera del proyecto y del repositorio, ver REGLAS_DESARROLLO.md §7 punto 11 --
# nunca se imprime su contenido ni ninguna contrasena en ningun momento
# de esta corrida):
#   export SGCPC_USUARIO=gerente
#   export SGCPC_CONTRASENA='...'
#   export SGCPC_USUARIO_OFICIAL=...        (opcional, pasada de oficial)
#   export SGCPC_CONTRASENA_OFICIAL='...'   (opcional, pasada de oficial)
#   export SGCPC_USUARIO_CAJERA=...         (opcional, pasada de cajera)
#   export SGCPC_CONTRASENA_CAJERA='...'    (opcional, pasada de cajera)
#   export SGCPC_USUARIO_ADMIN=...          (perfil ADMINISTRADOR, D-151)
#   export SGCPC_CONTRASENA_ADMIN='...'     (perfil ADMINISTRADOR, D-151)
#
# D-151 (pedido de Joel): Usuarios pasó a ser exclusivo del administrador y el gerente
# pasó a CONSULTA en Parámetros (ya no modifica) -- SGCPC_USUARIO_ADMIN/SGCPC_CONTRASENA_ADMIN
# ya no son opcionales, como SGCPC_USUARIO_OFICIAL/CAJERA (D-113): sin ellas no se puede probar
# ni /usuarios (ahora 403 para el gerente) ni los POST de /parametros (ahora 403 para el gerente,
# se mueven a la sesión del administrador vía login_admin/actualizar_parametro_como_admin).
#
# Las verificaciones SQL de solo lectura usan las credenciales de
# sgcpc_user que ya estan en src/main/resources/configuracion/bd.properties
# (mismo archivo que usa la aplicacion; gitignored). Solo se hacen SELECT,
# nunca UPDATE/INSERT/DELETE (D-47).
#
# Uso (Git Bash, desde sistema/):
#   bash pruebas/humo_http.sh
# (tambien funciona con las variables ya exportadas en la sesion actual,
# sin el archivo -- ver mas abajo.)

set -uo pipefail

if [ -f ./entorno.sh ]; then
    # shellcheck disable=SC1091
    source ./entorno.sh
fi

BASE_URL="${SGCPC_BASE_URL:-http://localhost:8081/sgcpc}"

ARCHIVO_CREDENCIALES="$HOME/.sgcpc_prueba"
if [ -z "${SGCPC_USUARIO:-}" ] || [ -z "${SGCPC_CONTRASENA:-}" ]; then
    if [ -f "$ARCHIVO_CREDENCIALES" ]; then
        # shellcheck disable=SC1090
        source "$ARCHIVO_CREDENCIALES"
    fi
fi

if [ -z "${SGCPC_USUARIO:-}" ] || [ -z "${SGCPC_CONTRASENA:-}" ]; then
    echo "Defina SGCPC_USUARIO y SGCPC_CONTRASENA como variables de entorno, o cree" >&2
    echo "$ARCHIVO_CREDENCIALES con esas dos líneas (nunca dentro del repositorio)." >&2
    exit 1
fi

# D-113 (pedido de Joel): SGCPC_USUARIO_OFICIAL/CAJERA dejaron de ser "opcionales" -- las usan
# E4 en adelante (la mayor parte del script), y si faltaban, cada sección se saltaba con un aviso
# a stderr y sin ningún FALLO (mismo problema de fondo que CC-2026-90002, D-113: un salto
# silencioso). Ahora son un requisito duro, igual que SGCPC_USUARIO/SGCPC_CONTRASENA arriba --
# nunca más "corrió y pasó" cuando en realidad se saltearon secciones enteras sin probar nada.
if [ -z "${SGCPC_USUARIO_OFICIAL:-}" ] || [ -z "${SGCPC_CONTRASENA_OFICIAL:-}" ] \
        || [ -z "${SGCPC_USUARIO_CAJERA:-}" ] || [ -z "${SGCPC_CONTRASENA_CAJERA:-}" ]; then
    echo "Defina SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL y SGCPC_USUARIO_CAJERA/" >&2
    echo "SGCPC_CONTRASENA_CAJERA (variables de entorno o en $ARCHIVO_CREDENCIALES) -- ya no son" >&2
    echo "opcionales: sin las tres pasadas (gerente/oficial/cajera) la mayor parte del humo no" >&2
    echo "prueba nada (D-113)." >&2
    exit 1
fi

# D-151 (pedido de Joel): mismo criterio que D-113 arriba -- sin la sesión de administrador no
# se puede probar ni /usuarios (ahora exclusivo del administrador) ni los POST de /parametros
# (ahora exclusivos del administrador; el gerente quedó en CONSULTA).
if [ -z "${SGCPC_USUARIO_ADMIN:-}" ] || [ -z "${SGCPC_CONTRASENA_ADMIN:-}" ]; then
    echo "Defina SGCPC_USUARIO_ADMIN y SGCPC_CONTRASENA_ADMIN (variables de entorno o en" >&2
    echo "$ARCHIVO_CREDENCIALES) -- ya no son opcionales: sin la sesión de administrador no se" >&2
    echo "puede probar /usuarios ni los POST de /parametros, exclusivos del administrador desde D-151." >&2
    exit 1
fi

# Credenciales de sgcpc_user (dueño de sgcpc_db) para las verificaciones SQL de solo
# lectura -- NUNCA se imprime BD_CONTRASENA. Distintas de SGCPC_USUARIO/SGCPC_CONTRASENA
# (esas son de un usuario de la aplicacion; estas son de la conexion JDBC a Postgres).
BD_PROPIEDADES="src/main/resources/configuracion/bd.properties"
if [ ! -f "$BD_PROPIEDADES" ]; then
    echo "No se encontró $BD_PROPIEDADES: no se pueden hacer las verificaciones SQL de solo lectura." >&2
    exit 1
fi
BD_USUARIO="$(grep '^bd.usuario=' "$BD_PROPIEDADES" | cut -d= -f2- | tr -d '\r')"
BD_CONTRASENA="$(grep '^bd.contrasena=' "$BD_PROPIEDADES" | cut -d= -f2- | tr -d '\r')"
# D-113 (Bloque 7/E10, separación del humo): BD_NOMBRE ya NO va fijo -- se lee de bd.properties
# (mismo patrón que recrear_base.sh/respaldar.sh), porque el humo ahora corre contra su propia
# base (sgcpc_db_pruebas), nunca contra sgcpc_db. Guarda explícita: si bd.properties no apunta a
# sgcpc_db_pruebas (por ejemplo, si alguien se olvidó de correr bd/usar_base_pruebas.sh antes),
# el script se niega a arrancar -- nunca corre el humo, que crea/borra datos libremente, contra
# la base real ni contra sgcpc_db_restaurada (esa es solo para probar restauraciones, D-112).
BD_URL_HUMO="$(grep '^bd.url=' "$BD_PROPIEDADES" | cut -d= -f2- | tr -d '\r')"
BD_NOMBRE="$(echo "$BD_URL_HUMO" | sed -E 's#.*/([^/?]+).*#\1#')"
if [ "$BD_NOMBRE" != "sgcpc_db_pruebas" ]; then
    echo "== ABORTADO: bd.properties apunta a '$BD_NOMBRE', no a sgcpc_db_pruebas. ==" >&2
    echo "== Corra 'bash bd/usar_base_pruebas.sh' y redespliegue antes de correr el humo. ==" >&2
    exit 1
fi

# --- archivos temporales: se van agregando con temporal() y se borran todos al salir ---
declare -a ARCHIVOS_TEMPORALES=()
temporal() {
    local archivo
    archivo="$(mktemp "$@")"
    ARCHIVOS_TEMPORALES+=("$archivo")
    echo "$archivo"
}
limpiar() {
    rm -f "${ARCHIVOS_TEMPORALES[@]}"
}

al_salir() {
    limpiar
}
trap al_salir EXIT

COOKIE_JAR="$(temporal)"
CUERPO_TMP="$(temporal)"
ENCABEZADOS_TMP="$(temporal)"
DESCARGA_TMP="$(temporal)"

ARCHIVO_PRUEBA="$(temporal --suffix=.pdf)"
printf '%%PDF-1.4 prueba de humo SGCPC' > "$ARCHIVO_PRUEBA"

# B42 (03/11/2026): SEGUNDO PDF de prueba, con contenido DISTINTO del de arriba. Desde B42, el
# sistema rechaza volver a cargar un archivo idéntico (mismo SHA-256) en el mismo tipo de
# documento del mismo expediente -- "reemplazar" subiendo los mismos bytes dejó de ser un
# reemplazo. Donde el humo quiera probar un reemplazo DE VERDAD (corregir un escaneo malo), usa
# este archivo, no el de arriba. El texto distinto alcanza: lo que compara el sistema es el hash.
ARCHIVO_PRUEBA_B="$(temporal --suffix=.pdf)"
printf '%%PDF-1.4 prueba de humo SGCPC -- segundo archivo, escaneo corregido' > "$ARCHIVO_PRUEBA_B"

ARCHIVO_TXT_COMO_PDF="$(temporal --suffix=.pdf)"
printf 'esto es un archivo de texto plano, no un PDF real (D-55: caso negativo de firma).' > "$ARCHIVO_TXT_COMO_PDF"

# El curl de este entorno es un build nativo de Windows (mingw64): "-F campo=@ruta" no
# puede leer una ruta POSIX como /tmp/xxx.pdf que devuelve mktemp (da status 000, sin
# transacción HTTP). Necesita la ruta en formato Windows.
ARCHIVO_PRUEBA_WIN="$(cygpath -w "$ARCHIVO_PRUEBA")"
ARCHIVO_PRUEBA_B_WIN="$(cygpath -w "$ARCHIVO_PRUEBA_B")"
ARCHIVO_TXT_COMO_PDF_WIN="$(cygpath -w "$ARCHIVO_TXT_COMO_PDF")"

# (12/11/2026, arreglo aprobado por Joel): la cédula base se verifica LIBRE antes de arrancar, y TODAS las demás
# cédulas del humo salen de ella (ver más abajo) con un sufijo distinto.
#
# Antes era "9$(date +%H%M%S)" a secas, y cada bloque volvía a leer `date` para armar la suya.
# Eso traía dos choques, los dos reales:
#
#   1. CON CORRIDAS VIEJAS. La cédula sólo lleva la HORA DEL DÍA, y el humo no limpia lo que crea.
#      El 12/11/2026 la corrida arrancó 15:25:09 y una del 05/11 había llegado a los mismos
#      bloques en el mismo segundo del día: las cédulas chocaron con filas que seguían en la base
#      (91525512 y 91525564, del 05/11). POST /clientes/nuevo devolvió 200 -- el formulario con el
#      error de cédula duplicada -- y de ahí cayó en cascada el desembolso del timbrado y 9
#      verificaciones del Bloque 5 y 6. Intermitente: depende de a qué hora se corra.
#
#   2. ENTRE BLOQUES DE LA MISMA CORRIDA. "9$(echo "$CEDULA_PRUEBA" | tail -c 7)1" da EXACTAMENTE
#      lo mismo que "${CEDULA_PRUEBA}1". Hoy no se nota porque esos dos casos son negativos (no
#      crean la persona, verifican el mensaje de rechazo), pero el día que uno pase a crearla, el
#      otro falla por una razón que no es la que dice probar.
#
# Si la base ya tiene algo con esta cédula base, se espera un segundo y se recalcula. El LIKE
# cubre la base y todas las derivadas en una sola consulta.
CEDULA_PRUEBA="9$(date +%H%M%S)"
for INTENTO_CEDULA in 1 2 3 4 5 6 7 8 9 10; do
    CEDULAS_OCUPADAS="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT count(*) FROM personas WHERE cedula_identidad LIKE '${CEDULA_PRUEBA}%'" 2>/dev/null | tr -d '[:space:]')"
    if [ "${CEDULAS_OCUPADAS:-0}" = "0" ]; then
        break
    fi
    echo "== La cédula base $CEDULA_PRUEBA ya está usada en la base (corrida anterior): se recalcula. ==" >&2
    sleep 1
    CEDULA_PRUEBA="9$(date +%H%M%S)"
done

# Cédula formateada con puntos (D-53): mismo algoritmo que FormateadorTexto.comoCedulaConPuntos
# para una cédula de 7 dígitos -- agrupa de a 3 desde la derecha ("9165213" -> "9.165.213").
# ficha.jsp ya la muestra así en el value del campo (no solo campo-cedula.js en el navegador).
CEDULA_PRUEBA_CON_PUNTOS="${CEDULA_PRUEBA:0:1}.${CEDULA_PRUEBA:1:3}.${CEDULA_PRUEBA:4:3}"
FECHA_COTIZADOR="$(date -d '+45 days' +%Y-%m-%d 2>/dev/null || date -v+45d +%Y-%m-%d)"
TOKEN_CSRF=""

# --- resultados: filas "ruta|esperado|obtenido|OK" ---
declare -a RESULTADOS=()
FALLOS=0

registrar() {
    local descripcion="$1" esperado="$2" obtenido="$3"
    local estado="OK"
    if [ "$obtenido" != "$esperado" ]; then
        estado="FALLO"
        FALLOS=$((FALLOS + 1))
    fi
    RESULTADOS+=("$descripcion|$esperado|$obtenido|$estado")
}

# B113 (05/11/2026, hallazgo de Joel al comparar las dos corridas del dia; arreglo del 07/11/2026):
# compara dos numeros de factura consecutivos -- la correlatividad sin saltos de D-89, item A, que
# es FISCAL. Es una funcion y no un bloque suelto para poder ejercitar las dos ramas sin una corrida
# completa (ver "pruebas de la funcion" en el registro del item).
#
# Antes esto vivia inline y, si no habia dos numeros, el "else" solo hacia un echo a stderr y NUNCA
# llamaba a registrar: la verificacion desaparecia del total y de la salida, y el humo seguia
# diciendo "TODO OK". Ahora la precondicion no dada se REGISTRA, asi que se ve en la salida y en el
# conteo (mismo criterio que el bloque E9 y que el caso de B114).
#
# $1 = "Factura 001-001-0000079" (el primero), $2 = idem el siguiente, $3 = de donde salio el 2do
verificar_secuencia_factura() {
    local primero="$1" siguiente="$2" origen="$3"
    if [ -z "$primero" ] || [ -z "$siguiente" ]; then
        registrar "PRECONDICIÓN no dada (D-89 item A, Bloque 5): no se pudieron capturar los dos números de factura para comparar la secuencia (primero=[$primero], siguiente=[$siguiente], 2do de $origen); la correlatividad NO se verificó" \
            "sin-comparar" "sin-comparar"
        return
    fi
    local secuencia_n secuencia_n1 esperada
    secuencia_n="$(echo "$primero" | grep -oE '[0-9]{7}$')"
    secuencia_n1="$(echo "$siguiente" | grep -oE '[0-9]{7}$')"
    esperada="$(printf '%07d' $((10#$secuencia_n + 1)))"
    registrar "Numeración de factura correlativa sin saltos (D-89, item A: N=$secuencia_n, N+1 de $origen)" \
        "$esperada" "$secuencia_n1"
}

# metodo url esperado [curl-args...]
verificar() {
    local metodo="$1" ruta="$2" esperado="$3"; shift 3
    local status
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X "$metodo" "$BASE_URL$ruta" "$@")
    registrar "$metodo $ruta" "$esperado" "$status"
}

# Confirma que un texto (mensaje de exito o de error) aparece en una respuesta ya
# guardada -- por defecto $CUERPO_TMP, o el archivo que se pase como 3er argumento.
# Punto 1 del pedido de Joel: no alcanza con el codigo HTTP, hay que ver el mensaje.
verificar_texto() {
    local descripcion="$1" patron="$2" archivo="${3:-$CUERPO_TMP}"
    local obtenido="ausente"
    if grep -qF -- "$patron" "$archivo"; then
        obtenido="presente"
    fi
    registrar "$descripcion" "presente" "$obtenido"
}

# Lo mismo que verificar_texto, pero para confirmar que un texto NO aparece -- D-56: para un
# perfil con nivel NINGUNO en CARGAR_DOCUMENTOS_CONSULTA_BURO (la cajera), las pestañas de
# Documentos y de Buró ni siquiera deben llegar ocultas en el HTML (información crediticia
# sensible).
verificar_texto_ausente() {
    local descripcion="$1" patron="$2" archivo="${3:-$CUERPO_TMP}"
    local obtenido="ausente"
    if grep -qF -- "$patron" "$archivo"; then
        obtenido="presente"
    fi
    registrar "$descripcion" "ausente" "$obtenido"
}

# SELECT de solo lectura contra sgcpc_db (nunca UPDATE/INSERT/DELETE, D-47). D-91 (pedido de
# Joel, encontrado en la corrida anterior): un error de psql (ej. columna inexistente) con
# "2>/dev/null" quedaba tragado en silencio -- el caso daba FALLO con "obtenido" vacío, sin decir
# por qué, obligando a repetir la consulta a mano para diagnosticar. Ahora el mensaje de error de
# psql (si lo hay) se muestra COMO el valor obtenido, así el caso nunca "pasa en silencio" y la
# causa queda a la vista en la misma tabla final.
verificar_sql() {
    local descripcion="$1" consulta="$2" esperado="$3"
    local obtenido salidaErrorSql
    salidaErrorSql="$(mktemp)"
    # Solo recorta espacios/salto de línea al principio y al final (psql -tAc agrega un salto
    # de línea al final): "tr -d espacios" borraría también los espacios DENTRO del valor (ej.
    # "Humo Http Editado" -> "HumoHttpEditado"), dando un falso FALLO.
    obtenido="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc "$consulta" \
        2>"$salidaErrorSql" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    if [ -s "$salidaErrorSql" ]; then
        obtenido="ERROR SQL: $(tr '\n' ' ' < "$salidaErrorSql" | sed 's/[[:space:]]*$//')"
    fi
    rm -f "$salidaErrorSql"
    registrar "$descripcion" "$esperado" "$obtenido"
}

# D-130 (Bloque 7/E10, pedido de Joel): antes de poder confirmar un desembolso hay que
# "imprimir para firma" el comprobante -- esa ruta asigna numero_comprobante_desembolso (una
# sola vez, nunca se regenera) y devuelve el PDF; confirmarDesembolso ahora exige que ya
# exista. Mismo chequeo en los 3 lugares que confirman un desembolso (E6, Bloque 5/E9 vía
# desembolsar_prestamo_e7, E8 mora): código 200 + Content-Type application/pdf + la
# columna quedó asignada en la base.
imprimir_comprobante_desembolso() {
    local cookieJar="$1" idPrestamo="$2" contexto="$3" status tipoContenido
    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$cookieJar" -c "$cookieJar" \
        -X GET "$BASE_URL/desembolso/$idPrestamo/comprobante")
    registrar "GET /desembolso/{id}/comprobante (imprimir para firma, $contexto, D-130)" 200 "$status"
    tipoContenido="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
    registrar "GET /desembolso/{id}/comprobante (tipo application/pdf, $contexto, D-130)" "presente" \
        "$(echo "$tipoContenido" | grep -qi 'application/pdf' && echo presente || echo ausente)"
    verificar_sql "SELECT: préstamo ($contexto) quedó con número de comprobante asignado al imprimir (D-130)" \
        "SELECT (numero_comprobante_desembolso IS NOT NULL) FROM prestamos WHERE id_prestamo=$idPrestamo" "t"
}

# D-145 (ficha única de cobranza, Opción B): el monto a cobrar/cancelar ya no se lee de una
# etiqueta de texto ("Monto cobrable normal", "Costo hoy:", quitadas de la pantalla) -- viaja
# en los atributos data-* de #tarjetaCobranzaUnificada, sin formato de miles (más simple y más
# robusto que raspar texto formateado). Devuelve vacío (nunca inventa un 0) si no encuentra el
# atributo, para que el llamador pueda cortar con un mensaje claro, mismo criterio que
# extraer_monto_etiqueta.
extraer_atributo_tarjeta_cobranza() {
    local cuerpo="$1" atributo="$2"
    tr '\n' ' ' < "$cuerpo" | grep -oE "$atributo"'="[0-9]+"' | head -1 | grep -oE '[0-9]+'
}

# D-144 (pedido de Joel): "Cobrar" ya no toca ninguna cuota que no esté vencida o venza justo
# hoy (antes sí, cobrando el interés devengado a la fecha) -- ver ServicioCobranza.
# confirmarCobro, "aplicacion.getDetalles().isEmpty()" -> "Este préstamo no tiene cuotas
# vencidas por cobrar.". Los préstamos de este bloque se desembolsan HOY, con la primera cuota
# semanas/meses después, así que sin este ajuste "Cobrar" ya no tendría nada que cobrar.
# Atrasa por SQL las fechas del préstamo exactamente los días que faltan para que la cuota
# $2 (1-based) quede vencida HOY -- ni un día antes (evita moratorio/punitorio de más, que
# cambiaría los montos que ya espera el resto del caso, p. ej. el vuelto exacto). Mismo
# criterio que el desplazamiento de -70 días de los casos de mora/D-95 (misma comprobación de
# feriados), pero con el desplazamiento justo, calculado, en vez de un número fijo.
#
# Deja en la variable global ULTIMO_DIAS_DESPLAZADOS cuántos días atrasó (0 si no hizo falta o
# si se omitió por un feriado) -- para los casos que necesitan la cuota vencida un rato y
# después tienen que volver a "hoy mismo del desembolso" (p. ej. Caso 22/23 de timbrado
# agotado: uno exige algo vencido, el otro exige lo contrario), deshacer_vencimiento (abajo)
# revierte exactamente ese desplazamiento.
#
# Hallazgo de Joel (20/10/2026, D-145/R2, Bloque 5): el desplazamiento se calculaba sobre
# fecha_vencimiento (la real, ya corrida a día hábil por RN-19) y se restaba igual de las dos
# columnas de cuotas -- si esa cuota YA tenía una diferencia entre su fecha real y su fecha
# teórica (porque la teórica original caía en fin de semana/feriado), esa diferencia quedaba
# intacta, solo reubicada en el calendario de hoy: la cuota "vencía hoy" en los hechos pero su
# fecha_vencimiento_teorica -- la que usa ServicioMotorFinanciero para el interés del período y
# para el bloqueo "primero hay que cobrar..." (D-23/D-88), no la real -- quedaba días atrás (o
# adelante). Ahora el desplazamiento se calcula sobre la TEÓRICA (así esa columna queda exacta
# en CURRENT_DATE por el mismo mecanismo de siempre) y un segundo ajuste, de un solo renglón,
# lleva también la fecha REAL de esa cuota a CURRENT_DATE -- el tamaño de ese ajuste se guarda en
# ULTIMO_GAP_TEORICA_REAL para que deshacer_vencimiento pueda revertirlo también.
ULTIMO_DIAS_DESPLAZADOS=0
ULTIMO_GAP_TEORICA_REAL=0
vencer_cuota_hoy() {
    local idPrestamo="$1" numeroCuota="$2" idPlan diasDesplazar colisiones gapTeoricaReal
    ULTIMO_DIAS_DESPLAZADOS=0
    ULTIMO_GAP_TEORICA_REAL=0
    idPlan="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_plan_pago FROM planes_pago WHERE id_prestamo=$idPrestamo AND vigente=true" 2>/dev/null | tr -d '[:space:]')"
    diasDesplazar="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT (fecha_vencimiento_teorica - CURRENT_DATE) FROM cuotas WHERE id_plan_pago=$idPlan AND numero_cuota=$numeroCuota" \
        2>/dev/null | tr -d '[:space:]')"
    if [ -z "$diasDesplazar" ] || [ "$diasDesplazar" -le 0 ] 2>/dev/null; then
        return 0
    fi
    # Diferencia real/teórica que esta cuota YA tenía antes de tocar nada -- se guarda ahora,
    # con los valores originales, para poder anular SOLO esa diferencia después del
    # desplazamiento uniforme de abajo (que la preserva tal cual).
    gapTeoricaReal="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT (fecha_vencimiento - fecha_vencimiento_teorica) FROM cuotas WHERE id_plan_pago=$idPlan AND numero_cuota=$numeroCuota" \
        2>/dev/null | tr -d '[:space:]')"
    [ -z "$gapTeoricaReal" ] && gapTeoricaReal=0
    # Hallazgo de Joel (16/10/2026, caso de finalización por 3 cobros): esta verificación
    # contaba TODAS las cuotas del plan, incluidas las ya PAGADA -- cuya fecha de vencimiento ya
    # no significa nada real una vez pagada -- y abortaba el desplazamiento de una cuota sin
    # ningún problema real solo porque la fecha (histórica) de una cuota ya pagada coincidía con
    # un feriado. Se filtran las PAGADA: solo importa si la cuota que de verdad se está
    # desplazando (o cualquier otra todavía pendiente) chocaría con un feriado.
    # 03/11/2026 (corrida con 5 fallas del bloque E9): la verificación miraba TODAS las cuotas no
    # pagadas, incluidas las FUTURAS -- y abortaba el escenario entero porque una cuota que el
    # caso nunca toca caía en un feriado. Esa cuota futura no cambia nada de lo que se prueba
    # (dos cobros sobre cuotas que vencen hoy) y, además, el propio humo deshace el corrimiento
    # al final con deshacer_vencimiento. Ahora solo cuentan las cuotas que el escenario realmente
    # usa: la que se está haciendo vencer y cualquier otra que quede vencida o venciendo hoy.
    #
    # Por qué NO se busca "el siguiente día hábil" corriendo uno o dos días más: con
    # credito.dias_gracia_mora = 0 la mora corre desde el día 1, así que una cuota vencida ayer
    # ya exige moratorio + punitorio, el cobro del humo (que paga total_programado) pasaría a ser
    # PARCIAL, pediría fecha de promesa y sería rechazado. El único corrimiento que mantiene el
    # escenario limpio es el que deja la cuota venciendo HOY.
    colisiones="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT count(*) FROM cuotas c JOIN feriados f ON f.fecha = (c.fecha_vencimiento - INTERVAL '$diasDesplazar days')::date AND f.activo WHERE c.id_plan_pago=$idPlan AND c.estado_pago <> 'PAGADA' AND (c.fecha_vencimiento - INTERVAL '$diasDesplazar days')::date <= CURRENT_DATE" \
        2>/dev/null | tr -d '[:space:]')"
    if [ "$colisiones" != "0" ]; then
        echo "== No se pudo adelantar el vencimiento del préstamo $idPrestamo (la cuota $numeroCuota caería en un feriado activo). ==" >&2
        return 1
    fi
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q > /dev/null <<SQL
BEGIN;
UPDATE prestamos SET fecha_desembolso = fecha_desembolso - INTERVAL '$diasDesplazar days',
    fecha_formalizacion = fecha_formalizacion - INTERVAL '$diasDesplazar days',
    primera_fecha_vencimiento = primera_fecha_vencimiento - INTERVAL '$diasDesplazar days'
    WHERE id_prestamo = $idPrestamo;
UPDATE planes_pago SET fecha_generacion = fecha_generacion - INTERVAL '$diasDesplazar days'
    WHERE id_plan_pago = $idPlan;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento - INTERVAL '$diasDesplazar days',
    fecha_vencimiento_teorica = fecha_vencimiento_teorica - INTERVAL '$diasDesplazar days'
    WHERE id_plan_pago = $idPlan;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento - INTERVAL '$gapTeoricaReal days'
    WHERE id_plan_pago = $idPlan AND numero_cuota = $numeroCuota;
COMMIT;
SQL
    ULTIMO_DIAS_DESPLAZADOS="$diasDesplazar"
    ULTIMO_GAP_TEORICA_REAL="$gapTeoricaReal"
}

# Deshace exactamente el desplazamiento de la última vencer_cuota_hoy de este préstamo (mismas
# 3 tablas, mismo monto, sumando en vez de restando; más el ajuste de un solo renglón sobre la
# cuota forzada, sumando gapTeoricaReal) -- no hace nada si no se desplazó nada.
deshacer_vencimiento() {
    local idPrestamo="$1" diasDesplazar="$2" numeroCuota="${3:-0}" gapTeoricaReal="${4:-0}" idPlan
    if [ -z "$diasDesplazar" ] || [ "$diasDesplazar" -le 0 ] 2>/dev/null; then
        return 0
    fi
    idPlan="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_plan_pago FROM planes_pago WHERE id_prestamo=$idPrestamo AND vigente=true" 2>/dev/null | tr -d '[:space:]')"
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q > /dev/null <<SQL
BEGIN;
UPDATE prestamos SET fecha_desembolso = fecha_desembolso + INTERVAL '$diasDesplazar days',
    fecha_formalizacion = fecha_formalizacion + INTERVAL '$diasDesplazar days',
    primera_fecha_vencimiento = primera_fecha_vencimiento + INTERVAL '$diasDesplazar days'
    WHERE id_prestamo = $idPrestamo;
UPDATE planes_pago SET fecha_generacion = fecha_generacion + INTERVAL '$diasDesplazar days'
    WHERE id_plan_pago = $idPlan;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento + INTERVAL '$diasDesplazar days',
    fecha_vencimiento_teorica = fecha_vencimiento_teorica + INTERVAL '$diasDesplazar days'
    WHERE id_plan_pago = $idPlan;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento + INTERVAL '$gapTeoricaReal days'
    WHERE id_plan_pago = $idPlan AND numero_cuota = $numeroCuota;
COMMIT;
SQL
}

# Id del documento VIGENTE de un tipo para una persona, leído de la base -- más confiable que
# raspar el HTML de la ficha (documentos_expediente.listarPorPersona ordena por nombre de tipo,
# no por el más reciente: una vez que la persona tiene más de un documento, "el primer enlace de
# /documentos/ en la página" ya no es necesariamente el que se acaba de subir). D-91: mismo
# criterio que verificar_sql -- un error de psql no se traga en silencio, se avisa por stderr
# (esta función no llama a registrar/no arma un caso propio, así que no puede dar FALLO ella
# misma; el llamador igual va a fallar con un ID vacío, pero al menos queda la causa a la vista).
obtener_id_documento_vigente() {
    local idPersona="$1" idTipoDocumento="$2"
    local resultado salidaErrorSql
    salidaErrorSql="$(mktemp)"
    resultado="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_documento FROM documentos_expediente WHERE id_persona=$idPersona AND id_tipo_documento=$idTipoDocumento AND estado_documento='VIGENTE' ORDER BY id_documento DESC LIMIT 1" \
        2>"$salidaErrorSql" | tr -d '[:space:]')"
    if [ -s "$salidaErrorSql" ]; then
        echo "== ERROR SQL en obtener_id_documento_vigente: $(tr '\n' ' ' < "$salidaErrorSql") ==" >&2
    fi
    rm -f "$salidaErrorSql"
    echo "$resultado"
}

# Igual que obtener_id_documento_vigente, pero para el checklist de formalización (D-70): filtra
# por id_prestamo, no por id_persona.
obtener_id_documento_vigente_prestamo() {
    local idPrestamo="$1" codigoTipoDocumento="$2"
    local resultado salidaErrorSql
    salidaErrorSql="$(mktemp)"
    resultado="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT d.id_documento FROM documentos_expediente d JOIN tipos_documento t ON t.id_tipo_documento = d.id_tipo_documento WHERE d.id_prestamo=$idPrestamo AND t.codigo_tipo='$codigoTipoDocumento' AND d.estado_documento='VIGENTE' ORDER BY d.id_documento DESC LIMIT 1" \
        2>"$salidaErrorSql" | tr -d '[:space:]')"
    if [ -s "$salidaErrorSql" ]; then
        echo "== ERROR SQL en obtener_id_documento_vigente_prestamo: $(tr '\n' ' ' < "$salidaErrorSql") ==" >&2
    fi
    rm -f "$salidaErrorSql"
    echo "$resultado"
}

extraer_token_csrf() {
    grep -o 'name="tokenCsrf" value="[^"]*"' "$CUERPO_TMP" | head -1 | sed 's/.*value="//;s/"$//'
}

# Tanda 2 (14/10/2026, hallazgo de Joel): protección real de doble clic en /mora/{id}/gestion --
# un input oculto "tokenGestion" (TokenUnaVez), de un solo uso por sesión, DENTRO del <form
# action=".../mora/{idPrestamo}/gestion"> de mora/lista.jsp. Puede haber un modal por cada
# préstamo en mora en la misma página, así que hay que acotar la búsqueda al bloque del
# préstamo pedido antes de sacar el valor (si no, se podría extraer el de otro préstamo). Sin
# este token (o con uno inválido/repetido), el servidor muestra la misma "Gestión registrada."
# pero nunca llama a ServicioMora.registrarGestion (ver RF24_seguimiento_mora.md §10).
extraer_token_gestion() {
    local cuerpo="$1" idPrestamo="$2"
    tr '\n' ' ' < "$cuerpo" | grep -oE "action=\"[^\"]*/mora/$idPrestamo/gestion\".{0,600}" | \
        grep -oE 'name="tokenGestion" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"$//'
}

# Punto de control (hallazgo de Joel, 12/10/2026, caso real: préstamo id 10, PA-21): el
# formulario "Confirmar desembolso" de /desembolso/{id} solo se renderiza con las 4 condiciones
# cumplidas (desembolso/ficha.jsp, "puedeDesembolsarDesembolso") -- si falta alguna (ej. un
# documento firmado del checklist), la página no tiene NINGÚN tokenCsrf para esa acción y
# extraer_token_csrf devuelve vacío. Mandar igual el POST de /confirmar con un token vacío solo
# consigue un 403 sin decir por qué, y deja el resto del bloque (que depende de ese préstamo ya
# desembolsado) en cascada. Esta función corta ahí, con el detalle de qué documento falta
# (lectura directa del checklist, sin inventar nada).
verificar_listo_para_desembolsar() {
    local idPrestamo="$1" token="$2" descripcion="$3"
    if [ -n "$token" ]; then
        return 0
    fi
    local faltantes
    faltantes="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT string_agg(td.codigo_tipo, ', ') FROM tipos_documento td WHERE td.contexto='PRESTAMO' \
         AND td.obligatorio_desembolso=TRUE AND NOT EXISTS ( \
             SELECT 1 FROM documentos_expediente de WHERE de.id_prestamo=$idPrestamo \
             AND de.id_tipo_documento=td.id_tipo_documento AND de.estado_documento='VIGENTE')" \
        2>/dev/null | tr -d '\r')"
    echo "== $descripcion ABORTADO: préstamo $idPrestamo no listo para desembolsar -- el formulario \"Confirmar desembolso\" no aparece en la ficha (falta: ${faltantes:-no se pudo determinar cuál}). ==" >&2
    return 1
}

# D-151 (pedido de Joel): el gerente pasó a CONSULTA en /parametros -- ADMINISTRAR_PARAMETROS ya
# no le alcanza para modificar. Logueado como administrador en un jar propio (no toca el jar de
# quien llama); devuelve la ruta del jar por stdout.
login_admin() {
    local jar
    jar="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$jar" -c "$jar" -X GET "$BASE_URL/acceso" > /dev/null
    local token
    token="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$jar" -c "$jar" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_ADMIN" --data-urlencode "contrasena=$SGCPC_CONTRASENA_ADMIN" \
        --data-urlencode "tokenCsrf=$token" > /dev/null
    echo "$jar"
}

# D-151: actualiza un parámetro como administrador -- antes estos POST se hacían con la sesión
# del gerente, que ahora da 403 (CONSULTA, no TOTAL). Loguea su propia sesión, pide un tokenCsrf
# fresco desde /parametros y hace el POST; devuelve el status HTTP por stdout.
# RF39/D1 (pedido de Joel, 13/10/2026): "motivo" es obligatorio desde el servidor (mínimo 10
# caracteres) -- sin esto, los 8 llamados de este script a actualizar-valor quedarían rechazados
# en silencio (siguen dando 302 por el patrón PRG, pero el parámetro no cambia).
actualizar_parametro_como_admin() {
    local clave="$1" valorNuevo="$2"
    local jar token status
    jar="$(login_admin)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$jar" -c "$jar" -X GET "$BASE_URL/parametros" > /dev/null
    token="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$jar" -c "$jar" -X POST "$BASE_URL/parametros/actualizar-valor" \
        --data-urlencode "clave=$clave" --data-urlencode "valorNuevo=$valorNuevo" \
        --data-urlencode "motivo=Prueba automática de humo" \
        --data-urlencode "tokenCsrf=$token")
    curl -s -o /dev/null -w '%{http_code}' -b "$jar" -c "$jar" -X GET "$BASE_URL/salir" > /dev/null
    echo "$status"
}

# Post/Redirect/Get (D-57): guardar, consentimiento y consulta al buró ahora SIEMPRE
# redirigen a /clientes/{id} (éxito o error) en vez de un forward -- un F5 en la página
# final ya no reenvía el POST. El mensaje (flash) recién aparece en el GET que sigue al
# redirect, así que el patrón siempre es: POST (se espera 302) -> GET -> verificar_texto.
# ruta esperado-del-POST mensajeEsperado [curl-args del POST...]
postear_y_verificar() {
    local descripcion="$1" ruta="$2" mensajeEsperado="$3"; shift 3
    local status
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL$ruta" "$@")
    registrar "$descripcion" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
    verificar_texto "GET /clientes/{id} tras: $descripcion" "$mensajeEsperado"
}

# Igual que postear_y_verificar, pero para acciones de /solicitudes y /aprobaciones (E4): esas
# no vuelven a /clientes/{id}, sino a su propia ficha -- se indica la URL de verificación aparte.
postear_y_verificar_en() {
    local descripcion="$1" ruta="$2" urlVerificacion="$3" mensajeEsperado="$4"; shift 4
    local status
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL$ruta" "$@")
    registrar "$descripcion" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL$urlVerificacion" > /dev/null
    verificar_texto "GET $urlVerificacion tras: $descripcion" "$mensajeEsperado"
}

echo "== Prueba de humo HTTP: $BASE_URL (cédula de prueba: $CEDULA_PRUEBA) =="

# ---------------------------------------------------------------------
# Parámetros reales (SELECT de solo lectura, D-38/ajuste C del plan de E3):
# se usan para construir los casos de archivo demasiado grande y para no
# hardcodear "5 MB" en el mensaje esperado si Joel cambió el parámetro.
# ---------------------------------------------------------------------
TAMANO_MAXIMO_MB="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='archivos.tamano_maximo_mb' AND vigente_hasta IS NULL" \
    2>/dev/null | tr -d '[:space:]')"
if ! [[ "$TAMANO_MAXIMO_MB" =~ ^[0-9]+$ ]]; then
    echo "No se pudo leer archivos.tamano_maximo_mb de la base (¿Postgres corriendo?). Abortando." >&2
    exit 1
fi

ARCHIVO_GRANDE="$(temporal --suffix=.pdf)"
TAMANO_GRANDE_BYTES=$(( (TAMANO_MAXIMO_MB + 1) * 1024 * 1024 ))

# ---------------------------------------------------------------------
# Bloque 7/E10, Tanda A (D-126): ids de catálogos nuevos (departamento/ciudad/motivo de rechazo),
# leídos de la base en vez de hardcodear un número -- se usan en crear_persona_con_expediente y en
# la verificación del rechazo referencial, más abajo.
# ---------------------------------------------------------------------
ID_DEPARTAMENTO_HUMO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_departamento FROM departamentos WHERE nombre='Central'" 2>/dev/null | tr -d '[:space:]')"
ID_CIUDAD_HUMO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_ciudad FROM ciudades WHERE nombre='Luque'" 2>/dev/null | tr -d '[:space:]')"
# De OTRO departamento (Asunción), para probar que una combinación departamento/ciudad
# inconsistente se rechaza (D-126).
ID_CIUDAD_OTRO_DEPARTAMENTO_HUMO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_ciudad FROM ciudades WHERE nombre='Asunción'" 2>/dev/null | tr -d '[:space:]')"
ID_MOTIVO_RECHAZO_INFORMCONF="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_motivo_rechazo FROM motivos_rechazo_solicitud WHERE codigo='INFORMCONF'" 2>/dev/null | tr -d '[:space:]')"
ID_MOTIVO_RECHAZO_OTRO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_motivo_rechazo FROM motivos_rechazo_solicitud WHERE codigo='OTRO'" 2>/dev/null | tr -d '[:space:]')"
ID_MOTIVO_RECHAZO_CAPACIDAD_PAGO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT id_motivo_rechazo FROM motivos_rechazo_solicitud WHERE codigo='CAPACIDAD_PAGO'" 2>/dev/null | tr -d '[:space:]')"
if ! [[ "$ID_DEPARTAMENTO_HUMO" =~ ^[0-9]+$ ]] || ! [[ "$ID_CIUDAD_HUMO" =~ ^[0-9]+$ ]] \
        || ! [[ "$ID_CIUDAD_OTRO_DEPARTAMENTO_HUMO" =~ ^[0-9]+$ ]] \
        || ! [[ "$ID_MOTIVO_RECHAZO_INFORMCONF" =~ ^[0-9]+$ ]] || ! [[ "$ID_MOTIVO_RECHAZO_OTRO" =~ ^[0-9]+$ ]] \
        || ! [[ "$ID_MOTIVO_RECHAZO_CAPACIDAD_PAGO" =~ ^[0-9]+$ ]]; then
    echo "No se pudieron leer los catálogos de departamentos/ciudades/motivos de rechazo (D-126). Abortando." >&2
    exit 1
fi
head -c "$TAMANO_GRANDE_BYTES" /dev/zero > "$ARCHIVO_GRANDE"
ARCHIVO_GRANDE_WIN="$(cygpath -w "$ARCHIVO_GRANDE")"
# Si el archivo de prueba (tamaño_maximo + 1 MB) ya supera el techo del contenedor
# (@MultipartConfig, 20 MB, ajuste C del plan de E3), el rechazo lo da Tomcat antes de
# llegar a ServicioDocumentos -- otro mensaje, igual de válido (nunca un 500).
if [ "$((TAMANO_MAXIMO_MB + 1))" -le 19 ]; then
    MENSAJE_ARCHIVO_GRANDE="El archivo supera ${TAMANO_MAXIMO_MB} MB."
else
    MENSAJE_ARCHIVO_GRANDE="El archivo es demasiado grande."
fi

# ---------------------------------------------------------------------
# 0. Sin sesión (cookie jar vacío): navegación normal redirige al login.
# ---------------------------------------------------------------------
verificar GET /panel 302
verificar GET /clientes 302

# ---------------------------------------------------------------------
# 1. Login: token CSRF de /acceso, POST con las credenciales.
# ---------------------------------------------------------------------
verificar GET /acceso 200
TOKEN_CSRF="$(extraer_token_csrf)"
if [ -z "$TOKEN_CSRF" ]; then
    echo "No se pudo leer el tokenCsrf de /acceso. Abortando." >&2
    exit 1
fi

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
    --data-urlencode "nombreUsuario=$SGCPC_USUARIO" \
    --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /acceso (login)" 302 "$status"

# Seguridad (a pedido de Joel): si el login real falla, DETENERSE aca mismo -- nunca
# reintentar sola/automaticamente. Cada intento fallido suma a intentos_fallidos del usuario
# real (RNF09); reintentar sin que Joel lo sepa podria terminar bloqueando la cuenta.
if [ "$status" != "302" ]; then
    echo
    echo "== El login falló (status $status, se esperaba 302). Me detengo acá: no reintento. ==" >&2
    echo "== Revise SGCPC_USUARIO/SGCPC_CONTRASENA en ~/.sgcpc_prueba antes de volver a correr esto. ==" >&2
    exit 1
fi

# CSRF invalido: no debe dejar pasar el POST (incluido el propio login). FiltroCsrf rechaza
# ANTES de llegar a ServicioAutenticacion.autenticar, asi que esto no cuenta como un intento
# fallido de login real (no toca intentos_fallidos).
status=$(curl -s -o /dev/null -w '%{http_code}' -c /dev/null -X POST "$BASE_URL/acceso" \
    --data-urlencode "nombreUsuario=$SGCPC_USUARIO" \
    --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
    --data-urlencode "tokenCsrf=token-invalido")
registrar "POST /acceso (sin CSRF válido)" 403 "$status"

# ---------------------------------------------------------------------
# 2. Panel / Cotizador
# ---------------------------------------------------------------------
verificar GET /panel 200
verificar GET /cotizador 200
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cotizador" \
    --data-urlencode "monto=2.000.000" \
    --data-urlencode "plazoMeses=6" \
    --data-urlencode "fechaPrimerVencimiento=$FECHA_COTIZADOR" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /cotizador" 200 "$status"
verificar_texto "POST /cotizador (aviso informativo)" "Cotización informativa, no constituye aprobación del crédito."

status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cotizador" \
    --data-urlencode "monto=2000000" --data-urlencode "plazoMeses=6" \
    --data-urlencode "fechaPrimerVencimiento=$FECHA_COTIZADOR")
registrar "POST /cotizador (sin CSRF)" 403 "$status"

# ---------------------------------------------------------------------
# 3. Clientes: lista, alta, ficha (GET /clientes/{id} es el bug reportado),
#    duplicado, edición. Cada alta/edición se confirma también con un
#    SELECT de solo lectura (punto 1 del pedido de Joel).
# ---------------------------------------------------------------------
verificar GET /clientes 200
verificar GET /clientes/nuevo 200

# Un solo POST real (antes eran dos: uno para leer el Location y otro para el status -- el
# segundo chocaba con el primero como "cedula duplicada" porque el primero ya lo habia creado).
# -D + -w en la misma llamada da status y encabezados (Location) de la misma respuesta.
status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (alta)" 302 "$status"
LOCATION="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"

ID_CLIENTE="$(echo "$LOCATION" | grep -o '[0-9]*$')"
if [ -z "$ID_CLIENTE" ]; then
    echo "No se pudo obtener el id del cliente recién creado (Location: '$LOCATION')." >&2
    ID_CLIENTE="0"
fi
verificar_sql "SELECT: persona $CEDULA_PRUEBA existe" \
    "SELECT count(*) FROM personas WHERE cedula_identidad='$CEDULA_PRUEBA'" "1"

verificar GET "/clientes/$ID_CLIENTE" 200
verificar_texto "GET /clientes/{id} (datos de la persona recién creada)" "Humo Http"
verificar_texto "GET /clientes/{id} (cédula mostrada con puntos, D-53)" "$CEDULA_PRUEBA_CON_PUNTOS"

# RF02 (pedido de Joel), punto g: verificación de C.I. al salir del campo (AJAX liviano, texto
# plano "idPersona|Nombre Apellido" si ya está registrada, vacío si no).
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/verificar-cedula?cedula=$CEDULA_PRUEBA")
registrar "GET /clientes/verificar-cedula (cédula ya registrada)" 200 "$status"
verificar_texto "GET /clientes/verificar-cedula (trae id y nombre de la persona)" "$ID_CLIENTE|Prueba Humo Http"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/verificar-cedula?cedula=9$(date +%s)9")
registrar "GET /clientes/verificar-cedula (cédula que no existe)" 200 "$status"
verificar_texto_ausente "GET /clientes/verificar-cedula (cédula que no existe, respuesta vacía)" "|"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/verificar-cedula?cedula=$CEDULA_PRUEBA&idPersonaActual=$ID_CLIENTE")
registrar "GET /clientes/verificar-cedula (propia cédula, al editarse a sí misma)" 200 "$status"
verificar_texto_ausente "GET /clientes/verificar-cedula (no se marca a sí misma como duplicada)" "|"

# Mejora de pantalla (Precalificación, pedido de Joel): indicador en vivo, solo lectura, texto
# plano vacío si califica o el motivo si no -- mismo patrón que verificar-cedula.
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/precalificar?fechaNacimiento=1985-01-01&idDepartamento=$ID_DEPARTAMENTO_HUMO&situacionLaboral=COMERCIANTE&presentaComprobante=true")
registrar "GET /clientes/precalificar (datos que califican)" 200 "$status"
verificar_texto_ausente "GET /clientes/precalificar (califica: respuesta vacía)" "no está habilitada"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/precalificar?fechaNacimiento=2020-01-01&idDepartamento=$ID_DEPARTAMENTO_HUMO&situacionLaboral=COMERCIANTE&presentaComprobante=true")
registrar "GET /clientes/precalificar (no califica por edad)" 200 "$status"
verificar_texto "GET /clientes/precalificar (motivo de edad)" "La edad permitida es de"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/precalificar?fechaNacimiento=1985-01-01&idDepartamento=$ID_DEPARTAMENTO_HUMO&situacionLaboral=DESEMPLEADO&presentaComprobante=true")
registrar "GET /clientes/precalificar (no califica por situación laboral no admitida)" 200 "$status"
verificar_texto "GET /clientes/precalificar (motivo de situación laboral)" "situación laboral del cliente no está admitida"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/precalificar")
registrar "GET /clientes/precalificar (sin parámetros, todavía incompleto: no rompe, responde vacío)" 200 "$status"

# Mejora de pantalla (Precalificación, pedido de Joel): botón "Imprimir requisitos" -- PDF
# informativo, no persiste nada. Solo lectura de parámetros/empresa.
status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/requisitos" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "nombreCompleto=Humo Http" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/requisitos (PDF de requisitos)" 200 "$status"
TIPO_CONTENIDO_REQUISITOS="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
registrar "POST /clientes/requisitos (tipo application/pdf)" "presente" \
    "$(echo "$TIPO_CONTENIDO_REQUISITOS" | grep -qi 'application/pdf' && echo presente || echo ausente)"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/requisitos" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/requisitos (sin situación laboral, rechazado)" 400 "$status"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Otra" \
    --data-urlencode "apellidos=Persona" \
    --data-urlencode "fechaNacimiento=1985-01-01" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000001" \
    --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle distinta 456" \
    --data-urlencode "referenciaDomicilio=Otra direccion" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (cédula duplicada)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje de cédula duplicada, con puntos)" \
    "Ya existe una persona registrada con la cédula \"$CEDULA_PRUEBA_CON_PUNTOS\"."
verificar_texto "POST /clientes/nuevo (formulario no queda vacío tras el rechazo, D-126: referencia del domicilio)" "Otra direccion"

# RF02 (pedido de Joel): "si no califica, ni siquiera se puede cargar" -- sin
# presentaComprobanteIngreso el alta se rechaza y NO se guarda nada (a diferencia de la
# edición, que más abajo sí guarda con un aviso). Cédula propia, distinta de CEDULA_PRUEBA,
# para no chocar con el alta de más arriba.
CEDULA_PRUEBA_SIN_COMPROBANTE="${CEDULA_PRUEBA}1"
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA_SIN_COMPROBANTE" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Sin Comprobante" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000002" \
    --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (sin comprobante -> rechazado)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje claro de rechazo sin comprobante)" \
    "Sin comprobante de ingresos no puede solicitar un crédito (política vigente)."
verificar_sql "SELECT: NO se guardó la persona rechazada por falta de comprobante" \
    "SELECT count(*) FROM personas WHERE cedula_identidad='$CEDULA_PRUEBA_SIN_COMPROBANTE'" "0"

# RF02 (pedido de Joel): "misma lógica" para la primera referencia personal -- sin ella, el alta
# se rechaza y no se guarda (con comprobante y el resto de los datos en regla, para aislar que el
# rechazo es específicamente por la referencia faltante).
CEDULA_PRUEBA_SIN_REFERENCIA="${CEDULA_PRUEBA}2"
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA_SIN_REFERENCIA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Sin Referencia" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000010" \
    --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (sin primera referencia -> rechazado)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje claro de rechazo sin primera referencia)" \
    "Complete la primera referencia personal (nombre y teléfono)."
verificar_sql "SELECT: NO se guardó la persona rechazada por falta de la primera referencia" \
    "SELECT count(*) FROM personas WHERE cedula_identidad='$CEDULA_PRUEBA_SIN_REFERENCIA'" "0"

postear_y_verificar "POST /clientes/{id}/guardar" "/clientes/$ID_CLIENTE/guardar" "Ficha actualizada." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_texto "GET /clientes/{id} tras guardar (apellido editado se ve en la ficha)" "Humo Http Editado"
verificar_sql "SELECT: ficha editada quedó guardada" \
    "SELECT apellidos FROM personas WHERE id_persona=$ID_CLIENTE" "Humo Http Editado"

# RF02 (pedido de Joel): a diferencia del alta, la EDICIÓN de un cliente existente siempre
# guarda, aunque quede sin comprobante -- con el aviso amarillo, nunca bloqueada.
postear_y_verificar "POST /clientes/{id}/guardar (sin comprobante, edición igual guarda)" \
    "/clientes/$ID_CLIENTE/guardar" "Datos guardados" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: edición sin comprobante quedó guardada igual (RF02, nunca bloquea edición)" \
    "SELECT presenta_comprobante_ingreso FROM personas_laborales WHERE id_persona=$ID_CLIENTE" "f"
# Vuelve a dejar con comprobante, para no interferir con las pruebas 3b que siguen.
postear_y_verificar "POST /clientes/{id}/guardar (repone comprobante)" "/clientes/$ID_CLIENTE/guardar" "Ficha actualizada." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"

# Corrección (pedido de Joel): la PRIMERA referencia ya no es un dato de política -- es
# obligatoria igual que la segunda, en alta Y edición por igual. Sin ella, la edición de un
# cliente existente se rechaza igual que el alta (antes se guardaba igual, con un aviso amarillo).
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$ID_CLIENTE/guardar" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/{id}/guardar (sin primera referencia -> rechazado, ya no bloquea solo el alta)" 200 "$status"
verificar_texto "POST /clientes/{id}/guardar (mismo mensaje único de faltantes que el alta)" \
    "Complete la primera referencia personal (nombre y teléfono)."
verificar_sql "SELECT: la edición sin primera referencia NO se guardó" \
    "SELECT apellidos FROM personas WHERE id_persona=$ID_CLIENTE" "Humo Http Editado"

# ---------------------------------------------------------------------
# 3a. RF03 (pedido de Joel), Parte 1: cédula de identidad vencida -- alta rechazada (mismo
#     criterio que la edad) y edición con aviso aparte (nunca bloquea el guardado).
# ---------------------------------------------------------------------

CEDULA_PRUEBA_CEDULA_VENCIDA="${CEDULA_PRUEBA}3"
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA_CEDULA_VENCIDA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Cedula Vencida" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000011" \
    --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2020-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (cédula vencida -> rechazado, RF03 Parte 1)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje de cédula vencida)" \
    "La cédula de identidad está vencida (venció el 01/01/2020); no se puede registrar al cliente hasta que la renueve."
verificar_sql "SELECT: NO se guardó la persona con cédula vencida" \
    "SELECT count(*) FROM personas WHERE cedula_identidad='$CEDULA_PRUEBA_CEDULA_VENCIDA'" "0"

# Edición: aviso amarillo APARTE (no es "no califica"), con el texto exacto pedido por Joel --
# nunca bloquea, la ficha se guarda igual con la fecha vencida.
postear_y_verificar "POST /clientes/{id}/guardar (cédula vencida -> aviso, nunca bloquea, RF03 Parte 1)" \
    "/clientes/$ID_CLIENTE/guardar" \
    "La cédula venció el 01/01/2020. El cliente debe renovarla antes de solicitar un crédito." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2020-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: edición con cédula vencida igual se guardó (RF03 Parte 1, nunca bloquea edición)" \
    "SELECT fecha_vencimiento_cedula FROM personas WHERE id_persona=$ID_CLIENTE" "2020-01-01"

# Repone una fecha vigente -- de acá en más (3b, 4, 4.5...) el cliente de prueba necesita la
# cédula vigente otra vez. Todavía no se cargó ningún escaneo en esta sección (eso es en "4.
# Documentos"), así que no hay ningún documento VIGENTE que este cambio pueda invalidar.
postear_y_verificar "POST /clientes/{id}/guardar (repone vencimiento de cédula vigente)" \
    "/clientes/$ID_CLIENTE/guardar" "Ficha actualizada." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"

# ---------------------------------------------------------------------
# 3b. Bloque 7/E10 (D-129): ABM completo de Clientes (Obs. 31), textos sin
#     códigos internos (Obs. 14), orden del formulario y un solo campo de
#     dirección laboral (Obs. 15/16/17).
# ---------------------------------------------------------------------
verificar GET "/clientes?estado=ACTIVOS" 200
verificar GET "/clientes?estado=INACTIVOS" 200
verificar GET "/clientes?estado=TODOS" 200
verificar GET "/clientes?pagina=1" 200

# Obs. 15/17: secciones con título, en el orden pedido; un solo campo de
# dirección laboral visible (los otros dos, ocultos, sin perder datos).
verificar GET "/clientes/$ID_CLIENTE" 200
# Mejora de pantalla (Precalificación, pedido de Joel): "Situación laboral y empleador" +
# "Ingresos y deudas" se fusionaron en un solo "Trabajo e ingresos" (la situación laboral y el
# comprobante se movieron a la caja de Precalificación, verificada más abajo).
verificar_texto "GET /clientes/{id} (Precalificación: sección Trabajo e ingresos)" "Trabajo e ingresos"
verificar_texto "GET /clientes/{id} (Precalificación: caja destacada)" "Precalificación"
verificar_texto_ausente "GET /clientes/{id} (Obs. 15/17: ya no hay 'Dirección laboral (opcional)' duplicada)" "Dirección laboral (opcional)"
verificar_texto_ausente "GET /clientes/{id} (Obs. 15/17: ya no hay 'Lugar de trabajo (opcional)' duplicado)" "Lugar de trabajo (opcional)"
# Obs. 16: nuevo campo "Dirección (calle y número)" en Domicilio.
verificar_texto "GET /clientes/{id} (Obs. 16: campo Dirección de domicilio)" "Dirección (calle y número)"
# Obs. 14: ningún código interno visible (RN-/RF-/E4) en la ficha.
verificar_texto_ausente "GET /clientes/{id} (Obs. 14: sin código RN- visible)" "(RN-"
verificar_texto_ausente "GET /clientes/{id} (Obs. 14: sin código RF- visible)" "(RF-"

# Obs. 31: baja lógica (bloqueada solo si hay préstamos activos/en mora -- este
# cliente de prueba no tiene ninguno) y reactivación, ambas exclusivas del gerente.
postear_y_verificar "POST /clientes/{id}/baja (motivo PEDIDO_CLIENTE)" "/clientes/$ID_CLIENTE/baja" \
    "Cliente dado de baja." \
    --data-urlencode "motivoBaja=PEDIDO_CLIENTE" \
    --data-urlencode "detalleBaja=Prueba de humo" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: cliente quedó INACTIVA tras la baja" \
    "SELECT estado_persona FROM personas WHERE id_persona=$ID_CLIENTE" "INACTIVA"
verificar_texto "GET /clientes/{id} tras la baja (aviso de inactivo)" "inactivo"
verificar GET "/clientes?estado=INACTIVOS" 200
verificar_texto "GET /clientes (filtro Inactivos incluye al cliente recién dado de baja)" "Humo Http Editado"

status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" --data-urlencode "nombres=No" --data-urlencode "apellidos=Importa" \
    --data-urlencode "fechaNacimiento=1990-01-01" --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" --data-urlencode "barrio=Barrio" --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "direccionDomicilio=Calle de prueba" \
    --data-urlencode "antiguedadDomicilioAnios=1" \
    --data-urlencode "referenciaNombre=Ref Uno" --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Ref" --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (cédula duplicada -- también funciona con el cliente ya inactivo)" 200 "$status"

postear_y_verificar "POST /clientes/{id}/reactivar" "/clientes/$ID_CLIENTE/reactivar" \
    "Cliente reactivado." \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: cliente quedó ACTIVA tras reactivar" \
    "SELECT estado_persona FROM personas WHERE id_persona=$ID_CLIENTE" "ACTIVA"
verificar_sql "SELECT: reactivar conserva el motivo de la última baja como historial" \
    "SELECT motivo_baja FROM personas_baja WHERE id_persona=$ID_CLIENTE" "PEDIDO_CLIENTE"

verificar GET "/clientes/999999999" 404

# D-126: cónyuge obligatorio con estado civil "Casado/a" -- sin esos 3 campos, se rechaza.
CEDULA_SIN_CONYUGE="${CEDULA_PRUEBA}10"
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_SIN_CONYUGE" \
    --data-urlencode "nombres=Sin" --data-urlencode "apellidos=Conyuge" \
    --data-urlencode "fechaNacimiento=1990-01-01" --data-urlencode "estadoCivil=Casado/a" \
    --data-urlencode "telefonoWhatsapp=0981000006" --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "direccionDomicilio=Calle de prueba" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Ref Uno" --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (Casado/a sin cónyuge, D-126)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje: cónyuge obligatorio)" "complete el nombre, la cédula y el teléfono del cónyuge"

# D-126: departamento/ciudad inconsistentes (ciudad de OTRO departamento) -- se rechaza.
CEDULA_DOMICILIO_INCONSISTENTE="${CEDULA_PRUEBA}11"
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
    --data-urlencode "cedulaIdentidad=$CEDULA_DOMICILIO_INCONSISTENTE" \
    --data-urlencode "nombres=Domicilio" --data-urlencode "apellidos=Inconsistente" \
    --data-urlencode "fechaNacimiento=1990-01-01" --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000007" --data-urlencode "ingresoNetoComprobado=1.000.000" \
    --data-urlencode "fechaVencimientoCedula=2030-01-01" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" --data-urlencode "idCiudad=$ID_CIUDAD_OTRO_DEPARTAMENTO_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "direccionDomicilio=Calle de prueba" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Ref Uno" --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/nuevo (ciudad de otro departamento, D-126)" 200 "$status"
verificar_texto "POST /clientes/nuevo (mensaje: ciudad no pertenece al departamento)" "no pertenece al departamento seleccionado"

# ---------------------------------------------------------------------
# 4. Documentos: carga (tipo 4 = autorización de consulta al buró),
#    descarga (con verificación de integridad SHA-256), consentimiento,
#    consulta al buró -- caso negativo (sin consentimiento) y positivo.
# ---------------------------------------------------------------------

# Caso negativo (RN-24 / D-38): sin un consentimiento vigente todavía cargado para esta
# persona, la consulta al buró se rechaza. Se prueba ANTES de cargar ningún documento ni
# registrar el consentimiento, justo para asegurar que todavía no hay ninguno. Desde D-57,
# consulta-buro SIEMPRE redirige (éxito o error), así que el código esperado es 302 y el
# mensaje aparece recién en el GET que sigue.
# idDocumentoResultado="1" es un valor cualquiera, solo para que pase la validación de
# "¿adjuntó algo?" (validarDocumentoResultado) y se llegue a la de RN-24, que es la que este
# caso quiere probar: validarIndicadorMora -> validarDocumentoResultado -> validarConsultaPermitida,
# en ese orden, y todavía no se llegó a insertar nada cuando esto corta.
postear_y_verificar "POST /clientes/{id}/consulta-buro (sin consentimiento)" "/clientes/$ID_CLIENTE/consulta-buro" \
    "No hay una autorización de consulta vigente." \
    --data-urlencode "proveedorBuro=Informconf" \
    --data-urlencode "puntajeObtenido=650" \
    --data-urlencode "clasificacionObtenida=Bueno" \
    --data-urlencode "indicadorMora=false" \
    --data-urlencode "resumenResultado=Prueba de humo (sin consentimiento)" \
    --data-urlencode "idDocumentoResultado=1" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"

# Caso negativo (punto 5 del pedido de Joel): sin "¿Tiene mora?" (nunca "(sin dato)"), la
# consulta se rechaza -- se valida en el servidor, no solo con el "required" del <select> (que
# curl, a diferencia de un navegador, no respeta). Independiente del consentimiento: la
# validación corre antes de tocar la base (ver ServicioConsultaCrediticia.registrarConsulta).
postear_y_verificar "POST /clientes/{id}/consulta-buro (sin indicadorMora)" "/clientes/$ID_CLIENTE/consulta-buro" \
    "Debe indicar si el cliente tiene mora o no." \
    --data-urlencode "proveedorBuro=Informconf" \
    --data-urlencode "resumenResultado=Prueba de humo (sin indicador de mora)" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"

# Caso negativo (D-58, ajuste D del plan de E3): sin la evidencia (el informe de Informconf
# cargado como documento), la consulta se rechaza -- con "¿Tiene mora?" completo, para aislar
# justo esta validación (corre antes que la de RN-24, así que tampoco depende del consentimiento).
postear_y_verificar "POST /clientes/{id}/consulta-buro (sin evidencia)" "/clientes/$ID_CLIENTE/consulta-buro" \
    "Primero cargue en Expediente / Documentos la respuesta de Informconf (tipo Resultado de consulta al buró)." \
    --data-urlencode "proveedorBuro=Informconf" \
    --data-urlencode "indicadorMora=false" \
    --data-urlencode "resumenResultado=Prueba de humo (sin evidencia)" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"

# Sube funciones para el patrón "subir documento -> seguir el flash a /clientes/{id} -> ver
# el mensaje": lo reutilizan el caso válido y los dos casos negativos de más abajo.
subir_documento_y_verificar() {
    local idTipoDocumento="$1" archivoWin="$2" nombreArchivo="$3" descripcion="$4" mensajeEsperado="$5"
    local status
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_CSRF" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=$idTipoDocumento" \
        -F "archivo=@$archivoWin;type=application/pdf;filename=$nombreArchivo")
    registrar "POST /documentos/cargar ($descripcion)" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
    verificar_texto "GET /clientes/{id} tras cargar ($descripcion)" "$mensajeEsperado"
}

# Caso válido: PDF real, tipo 3 (comprobante de domicilio) -- sirve para probar la descarga
# (integridad SHA-256) y los permisos de GET /documentos/{id} más abajo. Ya no tipo 4 acá: punto
# 2 del pedido de Joel (hallazgo de un PDF falso cargado como autorización desde "Otros
# documentos", ya quitado) -- ver el caso negativo que sigue.
subir_documento_y_verificar 3 "$ARCHIVO_PRUEBA_WIN" "comprobante_domicilio.pdf" "PDF válido" "Documento cargado."
ID_DOCUMENTO="$(obtener_id_documento_vigente "$ID_CLIENTE" 3)"
if [ -z "$ID_DOCUMENTO" ]; then
    echo "No se pudo obtener el id del documento recién cargado." >&2
    ID_DOCUMENTO="0"
fi
verificar_sql "SELECT: documento de comprobante de domicilio quedó VIGENTE" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=3 AND estado_documento='VIGENTE'" "1"

# Caso negativo (punto 2 del pedido de Joel, hallazgo del PDF falso de Rodrigo): la autorización
# de consulta al buró y el informe del buró solo se cargan por su propia ruta dedicada -- la
# genérica los rechaza aunque el archivo sea un PDF válido y el tipo exista de verdad.
subir_documento_y_verificar 4 "$ARCHIVO_PRUEBA_WIN" "autorizacion_por_la_generica.pdf" \
    "autorización por la genérica, rechazada" \
    "Autorización de consulta al buró se carga desde su propio paso, no desde acá."
verificar_sql "SELECT: el intento rechazado no guardó ningún documento de autorización" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=4" "0"
subir_documento_y_verificar 5 "$ARCHIVO_PRUEBA_WIN" "resultado_por_la_generica.pdf" \
    "informe del buró por la genérica, rechazado" \
    "Resultado de la consulta al buró se carga desde su propio paso, no desde acá."
verificar_sql "SELECT: el intento rechazado no guardó ningún documento de resultado" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=5" "0"

# Tipo 1 (cédula de identidad) -- E4 (D-63, ajuste A): "enviar a aprobación" exige cédula
# vigente en el expediente del cliente. Se carga acá para que ya esté lista cuando la pasada
# de oficial cree la solicitud sobre este mismo cliente.
subir_documento_y_verificar 1 "$ARCHIVO_PRUEBA_WIN" "cedula.pdf" "cédula del cliente" "Documento cargado."

# RF03 (pedido de Joel), Parte 1 -- reprueba: cambiar el vencimiento de la cédula en Datos
# invalida el escaneo recién cargado, aunque no se toque el Expediente para nada.
ID_DOCUMENTO_CEDULA_ANTES_RENOVACION="$(obtener_id_documento_vigente "$ID_CLIENTE" 1)"
# B88/B90 (03/11/2026): el aviso que sale al guardar NO es la lista de faltantes del expediente,
# sino el PASO GUIADO (ControladorClientesServlet:218-220 -> ServicioPasoGuiado ->
# ServicioClientes.calcularSiguientePaso), y ese método ofrece la AUTORIZACIÓN antes que
# cualquier otro faltante. En este punto del humo la autorización todavía no está cumplida: el
# tipo 4 se subió por la ruta genérica más arriba, y sin el consentimiento que registra
# cargarAutorizacionFirmada no cuenta como cargada (construirItemDocumento la deja en null a
# propósito, por el hallazgo del PDF falso). Así que el flash es siempre
# "Ficha actualizada. Siguiente paso: generar la autorización, hacerla firmar y cargarla."
#
# Lo que esta verificación prueba, entonces, es que la edición devuelve su paso guiado. QUE EL
# ESCANEO QUEDÓ INVALIDADO lo prueban los dos SELECT de abajo (REEMPLAZADO y 0 VIGENTE) y el
# texto "Cargar la cédula renovada (nuevo vencimiento ...)" de la ficha, que no dependen de esto.
postear_y_verificar "POST /clientes/{id}/guardar (cambia vencimiento -> invalida el escaneo, RF03 Parte 1)" \
    "/clientes/$ID_CLIENTE/guardar" \
    "Ficha actualizada. Siguiente paso: generar la autorización, hacerla firmar y cargarla." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2031-03-15" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=COMERCIANTE" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: el escaneo anterior quedó REEMPLAZADO (RF03 Parte 1)" \
    "SELECT estado_documento FROM documentos_expediente WHERE id_documento=$ID_DOCUMENTO_CEDULA_ANTES_RENOVACION" "REEMPLAZADO"
verificar_sql "SELECT: ya no hay ningún documento de cédula VIGENTE (RF03 Parte 1)" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=1 AND estado_documento='VIGENTE'" "0"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_texto "GET /clientes/{id} (renglón 1 del Expediente, aviso de renovación, RF03 Parte 1)" \
    "Cargar la cédula renovada (nuevo vencimiento 15/03/2031)."

# Vuelve a cargar la cédula ("renovada") -- de otro modo el expediente queda sin este
# documento para las secciones de solicitud/buró que siguen más abajo.
subir_documento_y_verificar 1 "$ARCHIVO_PRUEBA_WIN" "cedula_renovada.pdf" "cédula renovada, RF03 Parte 1" "Documento cargado."

# RF03 (pedido de Joel): mismo agujero que la cédula, ahora con la situación laboral -- primero
# un comprobante de ingreso válido para la situación actual (Comerciante).
subir_documento_y_verificar 2 "$ARCHIVO_PRUEBA_WIN" "iva_comerciante.pdf" "comprobante de ingreso (Comerciante)" "Documento cargado."
ID_DOCUMENTO_COMPROBANTE_ANTES_CAMBIO="$(obtener_id_documento_vigente "$ID_CLIENTE" 2)"

# Cambia la situación laboral en Datos (de Comerciante a Asalariado) -- invalida el comprobante
# recién cargado, aunque no se toque el Expediente para nada.
# B88/B90 (03/11/2026): mismo motivo que en el bloque de la cédula de arriba -- el paso guiado
# ofrece primero la autorización, que acá sigue sin consentimiento registrado, así que el flash
# es el mismo. Que el comprobante de ingreso quedó invalidado lo prueba el SELECT de abajo.
postear_y_verificar "POST /clientes/{id}/guardar (cambia situación laboral -> invalida el comprobante, RF03)" \
    "/clientes/$ID_CLIENTE/guardar" \
    "Ficha actualizada. Siguiente paso: generar la autorización, hacerla firmar y cargarla." \
    --data-urlencode "cedulaIdentidad=$CEDULA_PRUEBA" \
    --data-urlencode "nombres=Prueba" \
    --data-urlencode "apellidos=Humo Http Editado" \
    --data-urlencode "fechaNacimiento=1990-05-20" \
    --data-urlencode "estadoCivil=Soltero/a" \
    --data-urlencode "telefonoWhatsapp=0981000000" \
    --data-urlencode "ingresoNetoComprobado=2.500.000" \
    --data-urlencode "fechaVencimientoCedula=2031-03-15" \
    --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
    --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
    --data-urlencode "barrio=Barrio de prueba" \
    --data-urlencode "direccionDomicilio=Calle de prueba 123" \
    --data-urlencode "referenciaDomicilio=Calle de prueba 123" \
    --data-urlencode "tipoVivienda=PROPIA" \
    --data-urlencode "antiguedadDomicilioAnios=2" \
    --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
    --data-urlencode "referenciaTelefono=0981000008" \
    --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
    --data-urlencode "referencia2Telefono=0981000009" \
    --data-urlencode "situacionLaboral=ASALARIADO_SECTOR_PRIVADO" \
    --data-urlencode "empleadorNombre=Empresa de prueba S.A." \
    --data-urlencode "empleadorDireccion=Avenida de prueba 456" \
    --data-urlencode "empleadorTelefono=0981000007" \
    --data-urlencode "cargo=Vendedor" \
    --data-urlencode "antiguedadLaboralAnios=2" \
    --data-urlencode "cuotaOtrasDeudasMensual=0" \
    --data-urlencode "presentaComprobanteIngreso=on" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_sql "SELECT: el comprobante anterior quedó REEMPLAZADO (RF03)" \
    "SELECT estado_documento FROM documentos_expediente WHERE id_documento=$ID_DOCUMENTO_COMPROBANTE_ANTES_CAMBIO" "REEMPLAZADO"
verificar_sql "SELECT: ya no hay ningún comprobante de ingreso VIGENTE (RF03)" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=2 AND estado_documento='VIGENTE'" "0"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
# Hallazgo de Joel (16/10/2026): ficha.jsp:1177-1178 escribe ese texto en dos líneas de HTML (el
# <%= etiquetaSituacionLaboral %> queda en la línea siguiente) -- grep -F de una sola línea
# nunca matchea el texto completo. Se colapsan los saltos de línea antes de buscar, igual que
# ya hace extraer_token_gestion más arriba.
registrar "GET /clientes/{id} (renglón 2 del Expediente, aviso de cambio de situación laboral, RF03)" "presente" \
    "$(tr '\n' ' ' < "$CUERPO_TMP" | tr -s ' ' | grep -qF 'Cargar el comprobante de ingreso de Asalariado (sector privado).' && echo presente || echo ausente)"

# Vuelve a cargar el comprobante (para la situación nueva) -- de otro modo el expediente queda
# sin este documento para las secciones de solicitud/buró que siguen más abajo.
subir_documento_y_verificar 2 "$ARCHIVO_PRUEBA_WIN" "liquidacion_asalariado.pdf" \
    "comprobante de ingreso (nueva situación), RF03" "Documento cargado."

# Caso negativo (ajuste C): un .txt renombrado a .pdf -- la extensión declarada pasa, pero la
# firma de los primeros bytes no coincide con ningún formato reconocido.
subir_documento_y_verificar 2 "$ARCHIVO_TXT_COMO_PDF_WIN" "comprobante.pdf" "firma inválida" \
    "No se pudo cargar el archivo. El archivo no es un PDF, JPG o PNG real."

# Caso negativo (ajuste C): archivo más grande que archivos.tamano_maximo_mb -- nunca debe
# dar 500 (Tomcat), siempre el mensaje amigable en rojo.
subir_documento_y_verificar 2 "$ARCHIVO_GRANDE_WIN" "comprobante_grande.pdf" "archivo demasiado grande" \
    "$MENSAJE_ARCHIVO_GRANDE"

# Tipo 5 (resultado de la consulta al buró) -- D-58: la evidencia que ahora exige el formulario
# "Registrar resultado de la consulta". Punto 2 del pedido de Joel: ruta dedicada, ya no la
# genérica (ver los casos negativos de más arriba).
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-informe-buro" \
    -F "tokenCsrf=$TOKEN_CSRF" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=5" \
    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=resultado_informconf.pdf")
registrar "POST /documentos/cargar-informe-buro (resultado del buró)" 302 "$status"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_texto "GET /clientes/{id} tras cargar (resultado del buró)" "Documento cargado."
ID_DOCUMENTO_RESULTADO="$(obtener_id_documento_vigente "$ID_CLIENTE" 5)"
if [ -z "$ID_DOCUMENTO_RESULTADO" ]; then
    echo "No se pudo obtener el id del documento de resultado recién cargado." >&2
    ID_DOCUMENTO_RESULTADO="0"
fi

verificar GET "/documentos/$ID_DOCUMENTO" 200

# Integridad de la descarga (punto 2 del pedido de Joel): el contenido descargado debe ser
# byte a byte igual al que se subió -- se compara por SHA-256, no solo por el código HTTP.
curl -s -o "$DESCARGA_TMP" -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$BASE_URL/documentos/$ID_DOCUMENTO" > /dev/null
HASH_ORIGINAL="$(sha256sum "$ARCHIVO_PRUEBA" | cut -d' ' -f1)"
HASH_DESCARGADO="$(sha256sum "$DESCARGA_TMP" | cut -d' ' -f1)"
HASH_COMPARACION="distinto"
[ "$HASH_ORIGINAL" = "$HASH_DESCARGADO" ] && HASH_COMPARACION="igual"
registrar "GET /documentos/{id} (SHA-256 igual al archivo subido)" "igual" "$HASH_COMPARACION"

# RF03/RF04 (pedido de Joel): la ruta vieja "/clientes/{id}/consentimiento" ya no existe -- el
# único camino para registrar el consentimiento es "Generar autorización" + "Cargar autorización
# firmada" (una sola acción, mismo día).

# Caso negativo: todavía no se generó ninguna autorización (vía /clientes/{id}/autorizacion-buro)
# para este cliente en esta corrida -- cargar un escaneo firmado se rechaza SIN guardar nada,
# mismo mensaje que si la última generación fuera de otro día (autorizacionGeneradaHoy en false
# en los dos casos).
status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-autorizacion-firmada" \
    -F "tokenCsrf=$TOKEN_CSRF" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=4" \
    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=autorizacion_firmada.pdf")
registrar "POST /documentos/cargar-autorizacion-firmada (sin generar antes, RF03/RF04)" 302 "$status"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_texto "GET /clientes/{id} tras cargar sin generar (mensaje de otro día, RF03/RF04)" \
    "Esta autorización es de otro día. Genere una nueva para que la fecha coincida con la firma."
verificar_sql "SELECT: no se guardó ningún documento de autorización con el intento rechazado" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=4" "0"

# Caso válido: "Generar autorización" -- RF03 Parte 2 (pedido de Joel): ya no entrega el PDF
# directo -- redirige con el modal de éxito (número único AUT-aaaa-NNNNN en el mensaje) y el
# enlace "Abrir para imprimir"; queda la marca de auditoría (✓ de "Generar", y la base del
# chequeo de "mismo día").
status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST "$BASE_URL/clientes/$ID_CLIENTE/autorizacion-buro" --data-urlencode "tokenCsrf=$TOKEN_CSRF")
registrar "POST /clientes/{id}/autorizacion-buro (generar, RF03 Parte 2)" 302 "$status"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_texto "GET /clientes/{id} tras generar (modal con N.º AUT-, RF03 Parte 2)" "Autorización N.º AUT-"
verificar_texto "GET /clientes/{id} tras generar (siguiente paso, RF03 Parte 2)" \
    "Siguiente paso: imprímala, que el cliente la firme, y cárguela escaneada."
verificar_texto "GET /clientes/{id} tras generar (enlace Abrir para imprimir, RF03 Parte 2)" "Abrir para imprimir"
verificar_sql "SELECT: quedó la auditoría de 'Generar autorización' (RF03/RF04)" \
    "SELECT count(*) FROM auditoria_operaciones WHERE operacion='GENERAR_AUTORIZACION_BURO' AND tipo_entidad='PERSONA' AND id_entidad=$ID_CLIENTE" "1"
verificar_sql "SELECT: el número de autorización generado empieza con AUT- (RF03/RF04)" \
    "SELECT (valor_nuevo->>'valor' LIKE 'AUT-%') FROM auditoria_operaciones WHERE operacion='GENERAR_AUTORIZACION_BURO' AND tipo_entidad='PERSONA' AND id_entidad=$ID_CLIENTE" "t"

# RF03 Parte 2 (pedido de Joel): "Reimprimir autorización" -- GET, de solo lectura, mismo número
# (no suma otra fila de auditoría).
status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X GET "$BASE_URL/clientes/$ID_CLIENTE/autorizacion-buro")
registrar "GET /clientes/{id}/autorizacion-buro (reimprimir, RF03 Parte 2)" 200 "$status"
TIPO_CONTENIDO_AUTORIZACION_BURO="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
registrar "GET /clientes/{id}/autorizacion-buro (tipo application/pdf, RF03 Parte 2)" "presente" \
    "$(echo "$TIPO_CONTENIDO_AUTORIZACION_BURO" | grep -qi 'application/pdf' && echo presente || echo ausente)"
verificar_sql "SELECT: reimprimir no sumó otra auditoría (RF03 Parte 2)" \
    "SELECT count(*) FROM auditoria_operaciones WHERE operacion='GENERAR_AUTORIZACION_BURO' AND tipo_entidad='PERSONA' AND id_entidad=$ID_CLIENTE" "1"

# Caso válido: "Cargar autorización firmada" en el mismo día -- sube el escaneo Y registra el
# consentimiento en un solo paso, sin pedir ninguna fecha. Primer documento real de tipo 4 de
# esta corrida (los intentos de más arriba por la genérica fueron rechazados, punto 2 del pedido
# de Joel: no dejaron ninguno).
status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-autorizacion-firmada" \
    -F "tokenCsrf=$TOKEN_CSRF" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=4" \
    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=autorizacion_firmada.pdf")
registrar "POST /documentos/cargar-autorizacion-firmada (mismo día, RF03/RF04)" 302 "$status"
curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_texto_ausente "GET /clientes/{id} tras cargar la firmada (sin el mensaje de otro día, RF03/RF04)" \
    "Esta autorización es de otro día."
verificar_sql "SELECT: quedó un solo documento de autorización, el de esta carga (RF03/RF04)" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=4" "1"
verificar_sql "SELECT: hay un solo documento de autorización VIGENTE (RF03/RF04)" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=4 AND estado_documento='VIGENTE'" "1"
verificar_sql "SELECT: el consentimiento quedó registrado (RF03/RF04)" \
    "SELECT count(*) FROM consentimientos_crediticios WHERE id_persona=$ID_CLIENTE" "1"

# ---------------------------------------------------------------------
# 4-bis. RF03/RF04 (pedido de Joel): "Reemplazar" -- mismo día, corrige un escaneo malo, sin
# generar de nuevo (la última generación sigue siendo de hoy), y suma otro consentimiento.
# B42 (03/11/2026): el reemplazo va con ARCHIVO_PRUEBA_B, un PDF DISTINTO. Hasta esta corrida
# subía el mismo archivo con otro nombre, y desde B42 eso se rechaza ("Ese archivo ya está
# cargado...") -- no se creaba la versión 2 ni el segundo consentimiento, y las dos
# verificaciones de abajo fallaban. Un reemplazo que sube los mismos bytes no es un reemplazo:
# el escenario real que esto prueba es "corrige un escaneo malo", o sea, otro archivo.
# Corrección de Joel (12/10/2026, causa C): va ANTES de la consulta al buró positiva de más
# abajo -- antes quedaba después y dejaba el consentimiento más reciente SIN ninguna consulta
# registrada detrás (ServicioSolicitudes.agregarFaltantesBuro/tieneConsultaConEvidenciaRegistrada
# exige una consulta posterior al consentimiento vigente más nuevo), bloqueando "enviar a
# aprobación" de cualquier solicitud que usara este mismo cliente más abajo (sección 4.5).
# ---------------------------------------------------------------------
status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-autorizacion-firmada" \
    -F "tokenCsrf=$TOKEN_CSRF" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=4" \
    -F "archivo=@$ARCHIVO_PRUEBA_B_WIN;type=application/pdf;filename=autorizacion_firmada_corregida.pdf")
registrar "POST /documentos/cargar-autorizacion-firmada (reemplazar, mismo día, RF03/RF04)" 302 "$status"
verificar_sql "SELECT: el reemplazo (mismo día) no generó un número nuevo (RF03/RF04)" \
    "SELECT count(*) FROM auditoria_operaciones WHERE operacion='GENERAR_AUTORIZACION_BURO' AND tipo_entidad='PERSONA' AND id_entidad=$ID_CLIENTE" "1"
verificar_sql "SELECT: el reemplazo (mismo día) quedó VIGENTE y sin duplicar lo anterior (RF03/RF04)" \
    "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=4" "2"
verificar_sql "SELECT: el reemplazo (mismo día) sumó un segundo consentimiento (RF03/RF04)" \
    "SELECT count(*) FROM consentimientos_crediticios WHERE id_persona=$ID_CLIENTE" "2"

# Caso positivo: ahora sí hay un consentimiento vigente, con "¿Tiene mora?" completo (Sí/No),
# la consulta se registra. Puntaje entero (punto 6): "850", nunca "850,5".
postear_y_verificar "POST /clientes/{id}/consulta-buro" "/clientes/$ID_CLIENTE/consulta-buro" \
    "Consulta registrada." \
    --data-urlencode "proveedorBuro=Informconf" \
    --data-urlencode "puntajeObtenido=650" \
    --data-urlencode "clasificacionObtenida=Bueno" \
    --data-urlencode "indicadorMora=false" \
    --data-urlencode "resumenResultado=Prueba de humo" \
    --data-urlencode "idDocumentoResultado=$ID_DOCUMENTO_RESULTADO" \
    --data-urlencode "tokenCsrf=$TOKEN_CSRF"
verificar_texto "GET /clientes/{id} (enlace de descarga de la evidencia en el historial)" \
    "/documentos/$ID_DOCUMENTO_RESULTADO"
verificar_texto "GET /clientes/{id} (puntaje del buró, 650)" "650"
verificar_texto_ausente "GET /clientes/{id} (puntaje del buró, sin decimales: no '650.00')" "650.00"
verificar_sql "SELECT: consulta al buró quedó registrada" \
    "SELECT count(*) FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE AND proveedor_buro='Informconf'" "1"
verificar_sql "SELECT: la consulta guardó el id de la evidencia (D-58)" \
    "SELECT id_documento_resultado FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE AND proveedor_buro='Informconf'" \
    "$ID_DOCUMENTO_RESULTADO"

# F5 simulado (punto pedido por Joel): el navegador, tras el redirect, ya está parado en un GET
# a /clientes/{id} (no en el POST) -- repetir ESE GET (como si Joel apretara F5 en la pantalla
# final) no debe reenviar ningún POST ni duplicar la consulta recién registrada.
curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE" > /dev/null
verificar_sql "SELECT: F5 (repetir el GET final) no duplicó la consulta al buró" \
    "SELECT count(*) FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE AND proveedor_buro='Informconf'" "1"

# Crea una persona de prueba con el expediente mínimo para servir de segundo cliente en una
# solicitud (E4): presenta comprobante de ingresos, cédula + comprobante de ingreso vigentes,
# autorización + consentimiento, resultado + consulta al buró (sin mora). Usa la sesión/token
# ACTUALES (se llama durante la pasada de oficial). Deja el id en $ID_PERSONA_CREADA.
crear_persona_con_expediente() {
    local cedula="$1" nombres="$2" apellidos="$3" ingreso="$4" token="$5"
    local status location idPersona idResultado
    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/clientes/nuevo" \
        --data-urlencode "cedulaIdentidad=$cedula" \
        --data-urlencode "nombres=$nombres" \
        --data-urlencode "apellidos=$apellidos" \
        --data-urlencode "fechaNacimiento=1985-03-15" \
        --data-urlencode "estadoCivil=Casado/a" \
        --data-urlencode "telefonoWhatsapp=0981000003" \
        --data-urlencode "ingresoNetoComprobado=$ingreso" \
        --data-urlencode "presentaComprobanteIngreso=on" \
        --data-urlencode "fechaVencimientoCedula=2030-01-01" \
        --data-urlencode "conyugeNombre=Cónyuge de prueba" \
        --data-urlencode "conyugeCedula=9999998" \
        --data-urlencode "conyugeTelefono=0981000004" \
        --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
        --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
        --data-urlencode "barrio=Barrio de prueba" \
        --data-urlencode "direccionDomicilio=Calle de prueba E4" \
        --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
        --data-urlencode "tipoVivienda=ALQUILADA" \
        --data-urlencode "antiguedadDomicilioAnios=3" \
        --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
        --data-urlencode "referenciaTelefono=0981000006" \
        --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
        --data-urlencode "referencia2Telefono=0981000005" \
        --data-urlencode "situacionLaboral=COMERCIANTE" \
        --data-urlencode "cuotaOtrasDeudasMensual=0" \
        --data-urlencode "tokenCsrf=$token")
    registrar "POST /clientes/nuevo ($nombres, E4)" 302 "$status"
    location="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    idPersona="$(echo "$location" | grep -o '[0-9]*$')"
    if [ -z "$idPersona" ]; then
        echo "No se pudo crear la persona de prueba ($nombres)." >&2
        idPersona="0"
    fi

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$token" -F "idPersona=$idPersona" -F "idTipoDocumento=1" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=cedula.pdf")
    registrar "POST /documentos/cargar (cédula, $nombres, E4)" 302 "$status"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$token" -F "idPersona=$idPersona" -F "idTipoDocumento=2" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=comprobante_ingreso.pdf")
    registrar "POST /documentos/cargar (comprobante de ingreso, $nombres, E4)" 302 "$status"

    # 28/10/2026 (decisión de Joel del 21/10, RF03_expediente.md §7): el comprobante de domicilio
    # pasó a ser OBLIGATORIO -- sin él, "Enviar a aprobación" bloquea
    # (ServicioSolicitudes.agregarFaltantesExpediente). Este helper cargaba los tipos 1, 2, 4 y 5
    # pero no el 3, así que sus 12 clientes de prueba se quedaban sin poder enviar la solicitud y
    # caían en cascada los tramos de aprobación, formalización, desembolso y cobro.
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$token" -F "idPersona=$idPersona" -F "idTipoDocumento=3" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=comprobante_domicilio.pdf")
    registrar "POST /documentos/cargar (comprobante de domicilio, $nombres, E4)" 302 "$status"

    # RF03/RF04 (pedido de Joel): ya no existe "/consentimiento" -- el único camino es "Generar
    # autorización" (número + auditoría) seguido de "Cargar autorización firmada" el mismo día
    # (sube el escaneo y registra el consentimiento en un solo paso).
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
        "$BASE_URL/clientes/$idPersona/autorizacion-buro" --data-urlencode "tokenCsrf=$token")
    registrar "POST /clientes/{id}/autorizacion-buro (generar, $nombres, E4, RF03 Parte 2)" 302 "$status"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-autorizacion-firmada" \
        -F "tokenCsrf=$token" -F "idPersona=$idPersona" -F "idTipoDocumento=4" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=autorizacion_firmada.pdf")
    registrar "POST /documentos/cargar-autorizacion-firmada ($nombres, E4)" 302 "$status"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-informe-buro" \
        -F "tokenCsrf=$token" -F "idPersona=$idPersona" -F "idTipoDocumento=5" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=resultado.pdf")
    registrar "POST /documentos/cargar-informe-buro (resultado, $nombres, E4)" 302 "$status"
    idResultado="$(obtener_id_documento_vigente "$idPersona" 5)"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$idPersona/consulta-buro" \
        --data-urlencode "proveedorBuro=Informconf" \
        --data-urlencode "indicadorMora=false" \
        --data-urlencode "resumenResultado=Prueba de humo E4 ($nombres)" \
        --data-urlencode "idDocumentoResultado=$idResultado" \
        --data-urlencode "tokenCsrf=$token")
    registrar "POST /clientes/{id}/consulta-buro ($nombres, E4)" 302 "$status"

    ID_PERSONA_CREADA="$idPersona"
}

# ---------------------------------------------------------------------
# 4.5. Pasada de Oficial (P04, E4): crear y enviar una solicitud de crédito.
#    GESTIONAR_SOLICITUD es NINGUNO para el gerente, así que esta parte
#    solo se puede probar con el perfil oficial. Opcional (mismo patrón
#    que la de cajera, D-56): si SGCPC_USUARIO_OFICIAL/
#    SGCPC_CONTRASENA_OFICIAL no están, se omite con aviso -- y con eso
#    también se omiten los chequeos de aprobar/rechazar de más abajo (no
#    habría ninguna solicitud pendiente de aprobación).
# ---------------------------------------------------------------------
ID_SOLICITUD=""
ID_SOLICITUD_2="0"
if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ]; then
    COOKIE_JAR_GERENTE="$COOKIE_JAR"
    COOKIE_JAR="$(temporal)"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_OFICIAL="$(extraer_token_csrf)"
    if [ -z "$TOKEN_CSRF_OFICIAL" ]; then
        echo "No se pudo leer el tokenCsrf de /acceso para el oficial. Se omite su pasada." >&2
        COOKIE_JAR="$COOKIE_JAR_GERENTE"
    else
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /acceso (login oficial)" 302 "$status"
        if [ "$status" != "302" ]; then
            echo
            echo "== El login del oficial falló (status $status, se esperaba 302). Me detengo acá: no reintento. ==" >&2
            echo "== Revise SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba. ==" >&2
            exit 1
        fi

        verificar GET /solicitudes 200
        verificar GET /solicitudes/nuevo 200
        # E5 (D-68): GENERAR_DOCUMENTOS_FORMALIZACION es TOTAL para oficial y gerente (a
        # diferencia de /solicitudes vs /aprobaciones) -- el flujo completo se prueba más abajo
        # con el gerente; acá solo se confirma que el oficial tampoco está bloqueado.
        verificar GET /formalizacion 200

        FECHA_SOLICITUD="$(date -d '+45 days' +%Y-%m-%d 2>/dev/null || date -v+45d +%Y-%m-%d)"

        # Solicitud #1: monto/plazo moderados (plazo largo, cuota baja), cómodamente dentro del
        # límite de afectación para el ingreso de este cliente.
        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_PRUEBA" \
            --data-urlencode "monto=3.000.000" \
            --data-urlencode "plazoMeses=12" \
            --data-urlencode "destinoCredito=Capital de trabajo (prueba de humo)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (alta)" 302 "$status"
        LOCATION_SOLICITUD="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD="$(echo "$LOCATION_SOLICITUD" | grep -o '[0-9]*$')"
        if [ -z "$ID_SOLICITUD" ]; then
            echo "No se pudo obtener el id de la solicitud recién creada (Location: '$LOCATION_SOLICITUD')." >&2
            ID_SOLICITUD="0"
        fi
        verificar_sql "SELECT: solicitud quedó en BORRADOR" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "BORRADOR"

        # D-126: aviso (no bloquea) si la cédula del cliente está vencida. Con la cédula vigente
        # (2030-01-01, sección 3) no debe aparecer; se fuerza a vencida con un UPDATE directo para
        # confirmar que sí aparece.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/solicitudes/$ID_SOLICITUD" > /dev/null
        verificar_texto_ausente "GET /solicitudes/{id} (cédula vigente: sin aviso de vencimiento, D-126)" "venció"
        PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -q \
            -c "UPDATE personas SET fecha_vencimiento_cedula='2020-01-01' WHERE cedula_identidad='$CEDULA_PRUEBA'" \
            > /dev/null 2>&1
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/solicitudes/$ID_SOLICITUD" > /dev/null
        verificar_texto "GET /solicitudes/{id} (cédula vencida: aviso, D-126)" \
            "La cédula de identidad del cliente venció"

        # RF03 (pedido de Joel), Parte 1: a diferencia de D-126 (solo un aviso visual en esta
        # ficha), "Enviar a aprobación" ahora SÍ bloquea de verdad si la cédula del cliente está
        # vencida -- se prueba acá mismo, aprovechando que ya quedó vencida arriba, ANTES de
        # restaurarla (si no, este caso no sería reproducible más abajo sin forzarla de nuevo).
        postear_y_verificar_en "POST /solicitudes/{id}/enviar (cédula vencida -> rechazado, RF03 Parte 1)" \
            "/solicitudes/$ID_SOLICITUD/enviar" "/solicitudes/$ID_SOLICITUD" \
            "venció el 01/01/2020" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"
        verificar_sql "SELECT: la solicitud sigue en BORRADOR (cédula vencida, RF03 Parte 1)" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "BORRADOR"

        # A diferencia de D-126 (donde no hacía falta restaurar, antes de RF03 Parte 1: acá abajo
        # "enviar" ya esperaba éxito desde antes de este bloque, así que ahora SÍ hay que reponer
        # una fecha vigente para no romper ese caso.
        PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -q \
            -c "UPDATE personas SET fecha_vencimiento_cedula='2030-01-01' WHERE cedula_identidad='$CEDULA_PRUEBA'" \
            > /dev/null 2>&1

        # Ajuste 3 (Joel): columna Cliente en la bandeja, y filtro por estado.
        verificar GET "/solicitudes?estado=BORRADOR" 200
        verificar_texto "GET /solicitudes?estado=BORRADOR (columna Cliente)" "Humo Http"
        verificar GET "/solicitudes?estado=APROBADA" 200
        verificar_texto_ausente "GET /solicitudes?estado=APROBADA (filtro excluye la de BORRADOR)" \
            "$CEDULA_PRUEBA_CON_PUNTOS"

        # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 3): buscador
        # AJAX de /solicitudes -- mismo patrón que Mora/Aprobaciones: pedido con X-Requested-With
        # para recibir el fragmento (_resultados.jsp) directo.
        CODIGO_SOLICITUD="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT codigo_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" \
            2>/dev/null | tr -d '[:space:]')"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/solicitudes?texto=$CODIGO_SOLICITUD" > /dev/null
        verificar_texto "GET /solicitudes?texto= (filtro encuentra por código)" "$CODIGO_SOLICITUD"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/solicitudes?texto=$CEDULA_PRUEBA" > /dev/null
        verificar_texto "GET /solicitudes?texto= (filtro encuentra por cédula sin puntos)" "$CODIGO_SOLICITUD"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/solicitudes?texto=TextoQueNoExisteEnNingunaSolicitud" > /dev/null
        verificar_texto "GET /solicitudes?texto= (sin resultados, mensaje correcto)" \
            "No se encontraron solicitudes para esa búsqueda."
        verificar_texto_ausente "GET /solicitudes?texto= (sin resultados, $CODIGO_SOLICITUD ausente)" \
            "$CODIGO_SOLICITUD"

        # Nombre en el orden invertido (apellido + nombre). El apellido de este cliente ya quedó
        # editado más arriba (línea ~850, "Humo Http" -> "Humo Http Editado") antes de este punto
        # del script -- "Humo Http Prueba" (el apellido viejo) daba 0, falso FALLO del caso de
        # prueba, no del producto. Mismo criterio que el paso 0 (Clientes), el paso 1 (Mora) y el
        # paso 2 (Aprobaciones).
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/solicitudes?texto=Humo+Http+Editado+Prueba" > /dev/null
        verificar_texto "GET /solicitudes?texto= (nombre apellido+nombre encuentra igual)" "$CODIGO_SOLICITUD"

        # Combinado con estado=APROBADA (la de prueba sigue en BORRADOR): 0 resultados -- confirma
        # que el texto se SUMA al filtro de estado, nunca lo reemplaza.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/solicitudes?estado=APROBADA&texto=$CODIGO_SOLICITUD" > /dev/null
        verificar_texto "GET /solicitudes?estado=&texto= (combinados, la BORRADOR no aparece entre las aprobadas)" \
            "No se encontraron solicitudes para esa búsqueda."

        verificar GET "/solicitudes/$ID_SOLICITUD" 200
        # "Cumple validaciones preliminares" depende únicamente del checklist (comprobante
        # cargado, sección 3) y la capacidad de pago (cómodamente dentro del límite con este
        # monto/plazo).
        verificar_texto "GET /solicitudes/{id} (cumple validaciones preliminares)" "Cumple validaciones preliminares"
        # Corrección visual 2 (Joel): el cliente de prueba quedó con "presenta comprobante de
        # ingresos" marcado desde la edición de la sección 3 ("repone comprobante"), así que la
        # etiqueta debe decir "Ingreso neto comprobado" (el caso contrario se prueba más abajo,
        # con CEDULA_SIN_COMPROBANTE, donde el campo sí queda en false de verdad).
        verificar_texto "GET /solicitudes/{id} (etiqueta con comprobante, corrección visual 2)" \
            "Ingreso neto comprobado"

        # RN-02: una segunda solicitud abierta para el mismo cliente se rechaza -- /nuevo con
        # error sigue haciendo forward (D-53/D-57), no redirige.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_PRUEBA" \
            --data-urlencode "monto=1.000.000" \
            --data-urlencode "plazoMeses=6" \
            --data-urlencode "destinoCredito=Otra (no debería crearse)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (segunda solicitud abierta, RN-02)" 200 "$status"
        verificar_texto "POST /solicitudes/nuevo (mensaje RN-02)" "ya tiene una solicitud abierta"
        verificar_texto "POST /solicitudes/nuevo (RN-02, enlace a la solicitud abierta, ajuste 4)" "Ver solicitud SOL-"

        postear_y_verificar_en "POST /solicitudes/{id}/enviar" "/solicitudes/$ID_SOLICITUD/enviar" \
            "/solicitudes/$ID_SOLICITUD" "Solicitud enviada a aprobación" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"
        verificar_sql "SELECT: solicitud quedó PENDIENTE_APROBACION" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "PENDIENTE_APROBACION"
        verificar_sql "SELECT: la consulta del cliente quedó vinculada a la solicitud (D-59)" \
            "SELECT count(*) FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE AND id_solicitud=$ID_SOLICITUD" "1"

        # Solicitud #2: cliente nuevo con ingreso alto y monto chico, para la prueba de
        # "rechazar" más abajo (con el gerente).
        CEDULA_CLIENTE_2="${CEDULA_PRUEBA}12"
        crear_persona_con_expediente "$CEDULA_CLIENTE_2" "Cliente" "Prueba Humo E4 Dos" "6.000.000" "$TOKEN_CSRF_OFICIAL"
        ID_CLIENTE_2="$ID_PERSONA_CREADA"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_CLIENTE_2" \
            --data-urlencode "monto=600.000" \
            --data-urlencode "plazoMeses=3" \
            --data-urlencode "destinoCredito=Prueba de humo (rechazo)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (alta, segunda solicitud)" 302 "$status"
        LOCATION_SOLICITUD_2="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD_2="$(echo "$LOCATION_SOLICITUD_2" | grep -o '[0-9]*$')"
        if [ -z "$ID_SOLICITUD_2" ]; then
            echo "No se pudo obtener el id de la segunda solicitud (Location: '$LOCATION_SOLICITUD_2')." >&2
            ID_SOLICITUD_2="0"
        fi
        verificar GET "/solicitudes/$ID_SOLICITUD_2" 200
        verificar_texto "GET /solicitudes/{id} (segunda solicitud, cumple validaciones)" "Cumple validaciones preliminares"
        # Bloque 7/E10 (D-129, Obs. 19/22): checklist (✓ / falta) antes de "Enviar a aprobación".
        verificar_texto "GET /solicitudes/{id} (Obs. 19/22: checklist -- ítem de cédula vigente)" \
            "Cédula de identidad vigente cargada en el expediente"
        verificar_texto "GET /solicitudes/{id} (Obs. 19/22: checklist -- ítem de consulta al buró)" \
            "Consulta al buró (Informconf) registrada con evidencia"

        postear_y_verificar_en "POST /solicitudes/{id}/enviar (segunda solicitud)" \
            "/solicitudes/$ID_SOLICITUD_2/enviar" "/solicitudes/$ID_SOLICITUD_2" "Solicitud enviada a aprobación" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"

        # ---------------------------------------------------------------------
        # 2d (pedido de Joel, 17/10/2026): la ficha de una solicitud ya enviada muestra el
        # ingreso EVALUADO (congelado al enviar), no el actual -- sobre la solicitud #2, recién
        # enviada arriba. El ingreso de Cliente 2 (6.000.000, fijado en crear_persona_con_expediente)
        # no cambió desde el alta, así que el valor congelado y el literal coinciden.
        # ---------------------------------------------------------------------
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/solicitudes/$ID_SOLICITUD_2" > /dev/null
        verificar_texto "GET /solicitudes/{id} (2d: etiqueta 'evaluado al enviar' tras el envío)" "evaluado al enviar"
        verificar_texto "GET /solicitudes/{id} (2d: el ingreso evaluado mostrado es 6.000.000)" "6.000.000"
        verificar_sql "SELECT: ingreso_cliente_evaluado de la solicitud #2 quedó en 6.000.000 (2d)" \
            "SELECT ingreso_cliente_evaluado FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_2" "6000000"

        # ---------------------------------------------------------------------
        # 2a (pedido de Joel, 17/10/2026): RN-18 "no se presta a quien figura con deudas en
        # Informconf" -- bloqueo real en el servidor (antes era solo una confirmación reforzada,
        # "¿Aprobar igualmente?"). Cliente nuevo y dedicado, con una consulta al buró CON mora
        # (indicadorMora=true) como la más reciente.
        # ---------------------------------------------------------------------
        CEDULA_CLIENTE_MORA="${CEDULA_PRUEBA}13"
        crear_persona_con_expediente "$CEDULA_CLIENTE_MORA" "Cliente" "Prueba Humo Mora" "4.000.000" "$TOKEN_CSRF_OFICIAL"
        ID_CLIENTE_MORA="$ID_PERSONA_CREADA"

        # Segunda consulta al buró para la misma persona, ahora CON mora -- queda como la más
        # reciente (obtenerMasRecienteConEvidencia la elige por fecha), reusando la misma
        # evidencia ya cargada por crear_persona_con_expediente (tipo 5, resultado del buró).
        ID_DOCUMENTO_RESULTADO_MORA="$(obtener_id_documento_vigente "$ID_CLIENTE_MORA" 5)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
            "$BASE_URL/clientes/$ID_CLIENTE_MORA/consulta-buro" \
            --data-urlencode "proveedorBuro=Informconf" \
            --data-urlencode "indicadorMora=true" \
            --data-urlencode "resumenResultado=Prueba de humo (2a, con mora)" \
            --data-urlencode "idDocumentoResultado=$ID_DOCUMENTO_RESULTADO_MORA" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /clientes/{id}/consulta-buro (2a: segunda consulta, con mora)" 302 "$status"
        verificar_sql "SELECT: la consulta con mora quedó registrada (2a)" \
            "SELECT count(*) FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE_MORA AND indicador_mora=true" "1"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_CLIENTE_MORA" \
            --data-urlencode "monto=500.000" \
            --data-urlencode "plazoMeses=6" \
            --data-urlencode "destinoCredito=Prueba de humo (2a, mora en Informconf)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (2a: alta, cliente con mora)" 302 "$status"
        LOCATION_SOLICITUD_MORA="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD_MORA="$(echo "$LOCATION_SOLICITUD_MORA" | grep -o '[0-9]*$')"
        if [ -z "$ID_SOLICITUD_MORA" ]; then
            echo "No se pudo obtener el id de la solicitud de mora (2a)." >&2
            ID_SOLICITUD_MORA="0"
        fi

        # Checklist en pantalla (antes de enviar): el ítem nuevo de RN-18 aparece en la lista
        # (mismo criterio ya usado en el script para los demás ítems del checklist, línea
        # ~1747/1750 -- solo se verifica que el texto está presente).
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/solicitudes/$ID_SOLICITUD_MORA" > /dev/null
        verificar_texto "GET /solicitudes/{id} (2a: checklist -- ítem de Informconf presente)" \
            "Sin deudas registradas en Informconf"

        # Bloqueo real al enviar: mensaje específico de RN-18, no la lista genérica de
        # "faltantes" -- y la solicitud nunca pasa de BORRADOR.
        postear_y_verificar_en "POST /solicitudes/{id}/enviar (2a: cliente con mora, bloqueado)" \
            "/solicitudes/$ID_SOLICITUD_MORA/enviar" "/solicitudes/$ID_SOLICITUD_MORA" \
            "El cliente figura con deudas en Informconf. No se puede otorgar el crédito." \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"
        verificar_sql "SELECT: la solicitud con mora sigue en BORRADOR (2a, nunca llegó a enviarse)" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_MORA" "BORRADOR"

        # RF05 (pedido de Joel, 18/10/2026): hueco preexistente destapado al quitar el codeudor
        # -- sin comprobante de ingresos, la solicitud ni se crea (REGLAS_DESARROLLO.md §5,
        # "solo ingreso fijo comprobable"). El campo queda en false de verdad ANTES del POST de
        # creación (a diferencia del caso de abajo, que lo degrada DESPUÉS de crear, para probar
        # el bloqueo en el otro momento).
        CEDULA_SIN_COMPROBANTE="${CEDULA_PRUEBA}14"
        crear_persona_con_expediente "$CEDULA_SIN_COMPROBANTE" "Cliente" "Prueba Humo Sin Comprobante" "3.000.000" "$TOKEN_CSRF_OFICIAL"
        ID_CLIENTE_SIN_COMPROBANTE="$ID_PERSONA_CREADA"
        # crear_persona_con_expediente marca "presenta comprobante de ingresos" -- para este
        # caso hace falta lo contrario.
        PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c \
            "UPDATE personas_laborales SET presenta_comprobante_ingreso = FALSE WHERE id_persona=$ID_CLIENTE_SIN_COMPROBANTE;" \
            > /dev/null 2>&1

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_SIN_COMPROBANTE" \
            --data-urlencode "monto=500.000" \
            --data-urlencode "plazoMeses=12" \
            --data-urlencode "destinoCredito=Prueba de humo (sin comprobante de ingresos, bloqueo al crear)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (sin comprobante -> rechazado al crear)" 200 "$status"
        verificar_texto "POST /solicitudes/nuevo (mensaje: sin comprobante no califica)" \
            "Solo se otorgan créditos a personas con ingreso fijo comprobable."
        verificar_sql "SELECT: no se creó ninguna solicitud para el cliente sin comprobante" \
            "SELECT count(*) FROM solicitudes_credito WHERE id_cliente=$ID_CLIENTE_SIN_COMPROBANTE" "0"

        # RF06: mismo hueco, otro momento -- el cliente presenta comprobante al crear (pasa) y se
        # le degrada por SQL DESPUÉS, antes de enviar (mismo mecanismo que el resto del script
        # para forzar un cambio que el flujo HTTP normal no permitiría, ya que la edición de un
        # cliente existente nunca bloquea por esto).
        CEDULA_SIN_COMPROBANTE_AL_ENVIAR="${CEDULA_PRUEBA}15"
        crear_persona_con_expediente "$CEDULA_SIN_COMPROBANTE_AL_ENVIAR" "Cliente" "Prueba Humo Sin Comprobante Enviar" "3.000.000" "$TOKEN_CSRF_OFICIAL"
        ID_CLIENTE_SIN_COMPROBANTE_AL_ENVIAR="$ID_PERSONA_CREADA"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_SIN_COMPROBANTE_AL_ENVIAR" \
            --data-urlencode "monto=500.000" \
            --data-urlencode "plazoMeses=12" \
            --data-urlencode "destinoCredito=Prueba de humo (sin comprobante de ingresos, bloqueo al enviar)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (con comprobante al crear, para degradar antes de enviar)" 302 "$status"
        UBICACION_SIN_COMPROBANTE_AL_ENVIAR="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR="$(echo "$UBICACION_SIN_COMPROBANTE_AL_ENVIAR" | grep -o '[0-9]*$')"
        [ -z "$ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR" ] && ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR="0"

        PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c \
            "UPDATE personas_laborales SET presenta_comprobante_ingreso = FALSE WHERE id_persona=$ID_CLIENTE_SIN_COMPROBANTE_AL_ENVIAR;" \
            > /dev/null 2>&1

        verificar GET "/solicitudes/$ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR" 200
        verificar_texto_ausente "GET /solicitudes/{id} (sin tarjeta de codeudor)" \
            "Codeudor"
        # Corrección visual 2 (Joel): acá el campo sí queda en false de verdad (UPDATE de
        # arriba), a diferencia de la Solicitud #1 (que queda con comprobante marcado).
        verificar_texto "GET /solicitudes/{id} (etiqueta sin comprobante, corrección visual 2)" \
            "Ingreso declarado (sin comprobante)"
        verificar_texto "GET /solicitudes/{id} (sin comprobante, no cumple validaciones)" "No cumple validaciones preliminares"

        postear_y_verificar_en "POST /solicitudes/{id}/enviar (sin comprobante, degradado después de crear)" \
            "/solicitudes/$ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR/enviar" "/solicitudes/$ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR" \
            "Solo se otorgan créditos a personas con ingreso fijo comprobable." \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"
        verificar_sql "SELECT: solicitud sin comprobante sigue en BORRADOR (bloqueo al enviar)" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_SIN_COMPROBANTE_AL_ENVIAR" "BORRADOR"

        # ---------------------------------------------------------------------
        # 2a (pedido de Joel, 17/10/2026), segunda parte: mora descubierta DESPUÉS del
        # envío (no al enviar) -- ejercita la revinculación de la consulta más reciente
        # dentro de ServicioAprobacionCredito.aprobar. Cliente propio, CON comprobante (para
        # llegar de verdad a PENDIENTE_APROBACION sin chocar con el bloqueo de comprobante de
        # arriba): se envía SIN mora y, después, se le registra una consulta NUEVA, con mora --
        # el intento de aprobar (más abajo, sección 4.6, con el gerente) debe revincular esta
        # consulta y bloquear. No se reusa ID_CLIENTE_MORA (ese cliente ya está "envenenado"
        # desde el envío, nunca llegaría a PENDIENTE_APROBACION).
        # ---------------------------------------------------------------------
        CEDULA_MORA_TARDIA="${CEDULA_PRUEBA}16"
        crear_persona_con_expediente "$CEDULA_MORA_TARDIA" "Cliente" "Prueba Humo Mora Tardia" "3.000.000" "$TOKEN_CSRF_OFICIAL"
        ID_CLIENTE_MORA_TARDIA="$ID_PERSONA_CREADA"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_MORA_TARDIA" \
            --data-urlencode "monto=500.000" \
            --data-urlencode "plazoMeses=12" \
            --data-urlencode "destinoCredito=Prueba de humo (2a, mora tardía)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /solicitudes/nuevo (2a: alta, cliente para mora tardía)" 302 "$status"
        UBICACION_MORA_TARDIA="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD_MORA_TARDIA="$(echo "$UBICACION_MORA_TARDIA" | grep -o '[0-9]*$')"
        [ -z "$ID_SOLICITUD_MORA_TARDIA" ] && ID_SOLICITUD_MORA_TARDIA="0"

        postear_y_verificar_en "POST /solicitudes/{id}/enviar (2a: mora tardía, sin mora todavía)" \
            "/solicitudes/$ID_SOLICITUD_MORA_TARDIA/enviar" "/solicitudes/$ID_SOLICITUD_MORA_TARDIA" \
            "Solicitud enviada a aprobación" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL"
        verificar_sql "SELECT: solicitud de mora tardía quedó PENDIENTE_APROBACION (2a)" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_MORA_TARDIA" "PENDIENTE_APROBACION"

        ID_DOCUMENTO_RESULTADO_MORA_TARDIA="$(obtener_id_documento_vigente "$ID_CLIENTE_MORA_TARDIA" 5)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
            "$BASE_URL/clientes/$ID_CLIENTE_MORA_TARDIA/consulta-buro" \
            --data-urlencode "proveedorBuro=Informconf" \
            --data-urlencode "indicadorMora=true" \
            --data-urlencode "resumenResultado=Prueba de humo (2a, mora registrada después del envío)" \
            --data-urlencode "idDocumentoResultado=$ID_DOCUMENTO_RESULTADO_MORA_TARDIA" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL")
        registrar "POST /clientes/{id}/consulta-buro (2a: mora registrada después del envío)" 302 "$status"
        verificar_sql "SELECT: ahora hay 2 consultas para este cliente, la más reciente con mora (2a)" \
            "SELECT count(*) FROM consultas_crediticias WHERE id_persona=$ID_CLIENTE_MORA_TARDIA" "2"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (oficial)" 302 "$status"

        COOKIE_JAR="$COOKIE_JAR_GERENTE"
    fi
else
    echo
    echo "== Pasada del oficial omitida: defina SGCPC_USUARIO_OFICIAL y SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba para incluirla (y con eso, también las verificaciones de aprobar/rechazar). ==" >&2
fi

# ---------------------------------------------------------------------
# 4.6. Aprobaciones (P05, E4): el gerente aprueba la primera solicitud y
#    rechaza la segunda. GESTIONAR_SOLICITUD es NINGUNO para el gerente
#    (chequeo 403 siempre corre); el resto solo si la pasada de oficial
#    dejó solicitudes pendientes de aprobación.
# ---------------------------------------------------------------------
verificar GET /solicitudes 403
if [ -n "$ID_SOLICITUD" ] && [ "$ID_SOLICITUD" != "0" ]; then
    verificar GET /aprobaciones 200
    # B175 (14/11/2026, aprobado por Joel): la bandeja ahora PAGINA de 20, y Pendientes ordena por
    # la que mas espera primero -- asi que la solicitud que acaba de crear esta corrida, que es la
    # mas nueva, cae en la ultima pagina y ya no esta en el listado pelado de arriba. Se pide la
    # misma pantalla con el orden invertido (un clic en el encabezado "Enviada"), que es la que
    # tiene a la mas nueva primero: asi esta verificacion sigue mirando la columna Cliente (ajuste
    # 3) y de paso ejercita el orden nuevo. La de arriba se mantiene porque verifica otra cosa: que
    # la bandeja sin parametros conteste 200.
    verificar GET "/aprobaciones?orden=enviada&sentido=desc" 200
    verificar_texto "GET /aprobaciones?orden= (columna Cliente, ajuste 3, con la mas nueva primero)" "Humo Http"
    verificar GET "/aprobaciones/$ID_SOLICITUD" 200
    verificar_texto "GET /aprobaciones/{id} (TEA visible)" "TEA"
    verificar_texto "GET /aprobaciones/{id} (montos en letras)" "guaraníes"
    # Bloque 7/E10 (D-129, Obs. 26): resumen del cliente para decidir sin entrar a la ficha.
    verificar_texto "GET /aprobaciones/{id} (Obs. 26: resumen -- edad)" "Edad:"
    verificar_texto "GET /aprobaciones/{id} (Obs. 26: resumen -- situación laboral)" "Situación laboral:"
    verificar_texto "GET /aprobaciones/{id} (Obs. 26: resumen -- checklist de documentos)" \
        "Cédula de identidad vigente cargada en el expediente"
    # Ajuste 1 (Joel): el total a pagar ya no es una aproximación (cuota más alta × plazo, que
    # queda inflada porque el IVA sobre intereses baja en cada cuota) -- se muestra la suma
    # exacta del plan y ya no dice "(aprox.".
    verificar_texto_ausente "GET /aprobaciones/{id} (total exacto, sin \"aprox.\")" "(aprox."

    postear_y_verificar_en "POST /aprobaciones/{id}/aprobar" "/aprobaciones/$ID_SOLICITUD/aprobar" \
        "/aprobaciones/$ID_SOLICITUD" "Solicitud aprobada." \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"
    verificar_sql "SELECT: solicitud quedó APROBADA" \
        "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "APROBADA"
    verificar_sql "SELECT: monto aprobado = monto solicitado (RN-07)" \
        "SELECT monto_aprobado FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "3000000"
    verificar_texto "GET /aprobaciones/{id} (ajuste 6, quién aprobó y cuándo)" "Aprobada por"

    # Tanda de usabilidad (21/10/2026, punto 8 paso 2, PA-28): aprobar solicitud es una operación
    # de negocio -- debe quedar con IP y navegador (curl, acá, no tiene User-Agent propio puesto
    # por este script, así que manda el suyo por defecto: "curl/<versión>").
    verificar_sql "SELECT: aprobar solicitud guardó IP y navegador (PA-28)" \
        "SELECT (direccion_ip IS NOT NULL AND navegador LIKE 'curl/%') FROM auditoria_operaciones WHERE tipo_entidad='SOLICITUD_CREDITO' AND id_entidad=$ID_SOLICITUD AND operacion='APROBAR_SOLICITUD'" "t"

    # Reintento: aprobar de nuevo una solicitud ya decidida se rechaza (no la reprocesa).
    postear_y_verificar_en "POST /aprobaciones/{id}/aprobar (ya decidida)" "/aprobaciones/$ID_SOLICITUD/aprobar" \
        "/aprobaciones/$ID_SOLICITUD" "ya fue decidida" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"

    # F5 simulado: el navegador, tras el redirect, ya está en un GET -- repetirlo no vuelve a aprobar.
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/aprobaciones/$ID_SOLICITUD" > /dev/null
    verificar_sql "SELECT: F5 (repetir el GET) no duplicó el registro de auditoría de la aprobación" \
        "SELECT count(*) FROM auditoria_operaciones WHERE tipo_entidad='SOLICITUD_CREDITO' AND id_entidad=$ID_SOLICITUD AND operacion='APROBAR_SOLICITUD'" "1"

    # ---------------------------------------------------------------------
    # 2a (pedido de Joel, 17/10/2026), cierre: ID_SOLICITUD_MORA_TARDIA quedó PENDIENTE_APROBACION
    # SIN mora (sección 4.5) y, después, se le registró una consulta nueva con mora para el
    # mismo cliente -- el intento de aprobar debe revincular esa consulta (igual que ya hace RF08
    # para la auditoría) y bloquear, sin aprobar nada. El aviso de la ficha ya no dice
    # "¿Aprobar igualmente?" para este caso.
    # ---------------------------------------------------------------------
    if [ -n "${ID_SOLICITUD_MORA_TARDIA:-}" ] && [ "$ID_SOLICITUD_MORA_TARDIA" != "0" ]; then
        verificar GET "/aprobaciones/$ID_SOLICITUD_MORA_TARDIA" 200
        verificar_texto "GET /aprobaciones/{id} (2a: aviso de mora, ya no 'revise antes de decidir')" \
            "No se puede aprobar esta solicitud"
        verificar_texto_ausente "GET /aprobaciones/{id} (2a: ya no pide '¿Aprobar igualmente?')" \
            "¿Aprobar igualmente?"

        postear_y_verificar_en "POST /aprobaciones/{id}/aprobar (2a: mora revinculada, bloqueado)" \
            "/aprobaciones/$ID_SOLICITUD_MORA_TARDIA/aprobar" "/aprobaciones/$ID_SOLICITUD_MORA_TARDIA" \
            "El cliente figura con deudas en Informconf. No se puede aprobar: rechace la solicitud con el motivo \"Informconf: figura con deudas\"." \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF"
        verificar_sql "SELECT: la solicitud con mora revinculada sigue PENDIENTE_APROBACION (2a, nunca se aprobó)" \
            "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_MORA_TARDIA" "PENDIENTE_APROBACION"
    fi

    # -------------------------------------------------------------
    # E5 (Formalización, P06): con la solicitud recién aprobada, el gerente la formaliza --
    # GENERAR_DOCUMENTOS_FORMALIZACION es TOTAL para los dos perfiles (D-68), a diferencia de
    # /solicitudes vs /aprobaciones. Cubre: fecha de primer vencimiento propuesta/validada
    # (D-68), checklist (contrato/pagaré firmados, "Documento pendiente de diseño" mientras no
    # haya .jrxml), regenerar bloqueado con algo ya firmado, y anular antes del desembolso.
    # -------------------------------------------------------------
    DIAS_MIN_PRIMER_VENCIMIENTO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='formalizacion.dias_min_primer_vencimiento' AND vigente_hasta IS NULL" \
        2>/dev/null | tr -d '[:space:]')"
    DIAS_MAX_PRIMER_VENCIMIENTO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='formalizacion.dias_max_primer_vencimiento' AND vigente_hasta IS NULL" \
        2>/dev/null | tr -d '[:space:]')"
    FECHA_PROPUESTA_FORMALIZACION="$(date -d '+1 month' +%Y-%m-%d 2>/dev/null || date -v+1m +%Y-%m-%d)"
    FECHA_FUERA_DE_RANGO="$(date -d "+$((DIAS_MIN_PRIMER_VENCIMIENTO - 1)) days" +%Y-%m-%d 2>/dev/null \
        || date -v+"$((DIAS_MIN_PRIMER_VENCIMIENTO - 1))"d +%Y-%m-%d)"
    FECHA_PRIMER_VENCIMIENTO_OK="$(date -d "+$(( (DIAS_MIN_PRIMER_VENCIMIENTO + DIAS_MAX_PRIMER_VENCIMIENTO) / 2 )) days" +%Y-%m-%d 2>/dev/null \
        || date -v+"$(( (DIAS_MIN_PRIMER_VENCIMIENTO + DIAS_MAX_PRIMER_VENCIMIENTO) / 2 ))"d +%Y-%m-%d)"

    verificar GET /formalizacion 200
    # B183 (15/11/2026, aprobado por Joel): la bandeja pagina de a 20 y la sección 1 ordena por
    # fecha de aprobación ASCENDENTE (la que espera hace más tiempo, primero), así que la solicitud
    # que acaba de crear esta corrida queda ÚLTIMA -- hoy en la página 5 de 5, con 96 pendientes de
    # formalizar. Medido antes de cambiarlo: de las 20 filas de la página 1, CERO son de "Humo
    # Http". Se pide el orden inverso, que además ejercita el orden nuevo.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X GET "$BASE_URL/formalizacion?ordenFormalizar=aprobada&sentidoFormalizar=desc" > /dev/null
    verificar_texto "GET /formalizacion?ordenFormalizar= (aparece la solicitud aprobada)" "Humo Http"

    # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): buscador
    # AJAX de /formalizacion -- UN solo campo filtra las 3 secciones, probado acá sobre la
    # sección 1 (para formalizar) mientras $ID_SOLICITUD sigue ahí (antes de generar, más abajo).
    # El apellido de este cliente ya quedó editado más arriba ("Humo Http" -> "Humo Http
    # Editado") -- mismo cuidado que en el paso 3 (Solicitudes).
    CODIGO_SOLICITUD_FORMALIZAR="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT codigo_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" \
        2>/dev/null | tr -d '[:space:]')"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/formalizacion?texto=$CODIGO_SOLICITUD_FORMALIZAR" > /dev/null
    verificar_texto "GET /formalizacion?texto= (sección 1, filtro encuentra por código)" "$CODIGO_SOLICITUD_FORMALIZAR"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/formalizacion?texto=$CEDULA_PRUEBA" > /dev/null
    verificar_texto "GET /formalizacion?texto= (sección 1, filtro encuentra por cédula sin puntos)" "$CODIGO_SOLICITUD_FORMALIZAR"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/formalizacion?texto=Humo+Http+Editado+Prueba" > /dev/null
    verificar_texto "GET /formalizacion?texto= (sección 1, nombre apellido+nombre encuentra igual)" "$CODIGO_SOLICITUD_FORMALIZAR"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/formalizacion?texto=TextoQueNoExisteEnNingunaSolicitud" > /dev/null
    verificar_texto "GET /formalizacion?texto= (sin resultados, mensaje correcto)" \
        "No se encontraron solicitudes para esa búsqueda."
    verificar_texto_ausente "GET /formalizacion?texto= (sin resultados, $CODIGO_SOLICITUD_FORMALIZAR ausente)" \
        "$CODIGO_SOLICITUD_FORMALIZAR"

    verificar GET "/formalizacion/nueva/$ID_SOLICITUD" 200
    verificar_texto "GET /formalizacion/nueva/{id} (fecha propuesta = hoy + 1 mes, D-68)" \
        "value=\"$FECHA_PROPUESTA_FORMALIZACION\""
    verificar_texto "GET /formalizacion/nueva/{id} (rango de fechas concreto, corrección 2)" "Entre "

    postear_y_verificar_en "POST /formalizacion/nueva/{id}/generar (fecha fuera de rango, D-68)" \
        "/formalizacion/nueva/$ID_SOLICITUD/generar" "/formalizacion/nueva/$ID_SOLICITUD" \
        "debe estar entre" \
        --data-urlencode "primerVencimiento=$FECHA_FUERA_DE_RANGO" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD/generar" \
        --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF")
    registrar "POST /formalizacion/nueva/{id}/generar (fecha válida)" 302 "$status"
    LOCATION_PRESTAMO="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_PRESTAMO="$(echo "$LOCATION_PRESTAMO" | grep -o '[0-9]*$')"
    if [ -z "$ID_PRESTAMO" ]; then
        echo "No se pudo obtener el id del préstamo (Location: '$LOCATION_PRESTAMO')." >&2
        ID_PRESTAMO="0"
    fi

    verificar_sql "SELECT: préstamo quedó PENDIENTE_DESEMBOLSO" \
        "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" "PENDIENTE_DESEMBOLSO"
    verificar_sql "SELECT: primera_fecha_vencimiento del préstamo es la propuesta al formalizar, no la de la solicitud (D-68)" \
        "SELECT primera_fecha_vencimiento FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" "$FECHA_PRIMER_VENCIMIENTO_OK"
    verificar_sql "SELECT: numero_contrato/numero_pagare derivados del mismo identificador_operacion (D-68)" \
        "SELECT (identificador_operacion = numero_contrato AND identificador_operacion = numero_pagare) FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" "t"
    verificar_sql "SELECT: D-68 -- la solicitud quedó formalizada (ya no cuenta como 'abierta' para RN-01/02/28)" \
        "SELECT formalizada FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD" "t"

    verificar GET "/formalizacion/$ID_PRESTAMO" 200
    verificar_texto "GET /formalizacion/{id} (checklist: contrato)" "Contrato firmado"
    verificar_texto "GET /formalizacion/{id} (checklist: pagaré)" "Pagaré firmado"
    verificar_texto "GET /formalizacion/{id} (sin comisiones ni gastos, D-68)" "no incluye comisiones"
    verificar_texto "GET /formalizacion/{id} (fila de totales del plan, corrección 5)" 'colspan="2">Total<'

    # D-70 (diseño aprobado): los 3 PDF ya se generan solos al formalizar -- se descargan y se
    # confirma el código 200 y el tipo application/pdf.
    verificar_texto "GET /formalizacion/{id} (contrato ya generado, con enlace de descarga, RF03: ahora 'Ver')" "Ver"
    verificar_texto_ausente "GET /formalizacion/{id} (los 3 ya existen: no se ofrece \"generar los que faltan\")" "Generar los PDF que faltan"
    ID_DOC_CONTRATO_GENERADO="$(obtener_id_documento_vigente_prestamo "$ID_PRESTAMO" CONTRATO_GENERADO)"
    ID_DOC_PAGARE_GENERADO="$(obtener_id_documento_vigente_prestamo "$ID_PRESTAMO" PAGARE_GENERADO)"
    ID_DOC_PLAN_PAGOS_GENERADO="$(obtener_id_documento_vigente_prestamo "$ID_PRESTAMO" PLAN_PAGOS_GENERADO)"
    for par in "contrato:$ID_DOC_CONTRATO_GENERADO" "pagaré:$ID_DOC_PAGARE_GENERADO" "plan de pagos:$ID_DOC_PLAN_PAGOS_GENERADO"; do
        nombre="${par%%:*}"; idDocumentoPdf="${par#*:}"
        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X GET "$BASE_URL/documentos/$idDocumentoPdf")
        registrar "GET /documentos/{id} (descarga del PDF de $nombre, D-70)" 200 "$status"
        tipoContenido="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
        registrar "GET /documentos/{id} (tipo application/pdf, $nombre)" "presente" \
            "$(echo "$tipoContenido" | grep -qi 'application/pdf' && echo presente || echo ausente)"
    done

    # Idempotente: pedir "generar los que faltan" cuando ya están los 3 no falla (no hay nada que hacer).
    postear_y_verificar_en "POST /formalizacion/{id}/generar-pdf (nada que generar, D-70)" \
        "/formalizacion/$ID_PRESTAMO/generar-pdf" "/formalizacion/$ID_PRESTAMO" \
        "Documentos PDF generados." \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"

    # Corrección 1 (Joel): recién generado, todavía sin firmados -- aparece "Incompleto" en la
    # segunda sección de la bandeja.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/formalizacion" > /dev/null
    verificar_texto "GET /formalizacion (préstamos pendientes de desembolso, corrección 1)" "Préstamos pendientes de desembolso"
    verificar_texto "GET /formalizacion (aparece el N.º de operación recién generado)" "$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" 2>/dev/null | tr -d '[:space:]')"
    verificar_texto "GET /formalizacion (checklist incompleto todavía)" "Incompleto"

    # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): buscador
    # AJAX de /formalizacion, sección 2 (pendientes de desembolso) -- antes de desembolsar
    # $ID_PRESTAMO más abajo (sección 4.6).
    IDENTIFICADOR_OPERACION_FORMALIZAR="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" \
        2>/dev/null | tr -d '[:space:]')"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/formalizacion?texto=$IDENTIFICADOR_OPERACION_FORMALIZAR" > /dev/null
    verificar_texto "GET /formalizacion?texto= (sección 2, filtro encuentra por N.º de operación)" \
        "$IDENTIFICADOR_OPERACION_FORMALIZAR"

    ID_TIPO_CONTRATO_FIRMADO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='CONTRATO_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"
    ID_TIPO_PAGARE_FIRMADO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='PAGARE_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"
    # PA-21 (E10_14, hallazgo de Joel, 12/10/2026): tercer documento obligatorio del checklist --
    # resuelto acá (global, una sola vez) para que preparar_prestamo_e7 y el resto de los
    # préstamos de este humo lo puedan subir sin repetir la consulta.
    ID_TIPO_PLAN_PAGOS_FIRMADO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='PLAN_PAGOS_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"

    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_CSRF" -F "idPrestamo=$ID_PRESTAMO" -F "idTipoDocumento=$ID_TIPO_CONTRATO_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=contrato_firmado.pdf")
    registrar "POST /documentos/cargar (contrato firmado, E5)" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO" > /dev/null
    verificar_texto "GET /formalizacion/{id} (contrato cargado)" "Documento cargado."

    # Ajuste de Joel: regenerar queda bloqueado apenas hay un firmado cargado.
    postear_y_verificar_en "POST /formalizacion/{id}/regenerar (con contrato ya firmado, D-68)" \
        "/formalizacion/$ID_PRESTAMO/regenerar" "/formalizacion/$ID_PRESTAMO" \
        "ya hay un documento firmado" \
        --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"

    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_CSRF" -F "idPrestamo=$ID_PRESTAMO" -F "idTipoDocumento=$ID_TIPO_PAGARE_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=pagare_firmado.pdf")
    registrar "POST /documentos/cargar (pagaré firmado, E5)" 302 "$status"
    # PA-21: el checklist exige también PLAN_PAGOS_FIRMADO (3er documento, Tanda 2 14/10/2026) --
    # sin esto, el préstamo nunca queda "completo" y la verificación de más abajo falla.
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_CSRF" -F "idPrestamo=$ID_PRESTAMO" -F "idTipoDocumento=$ID_TIPO_PLAN_PAGOS_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=plan_pagos_firmado.pdf")
    registrar "POST /documentos/cargar (plan de pagos firmado, E5, PA-21)" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO" > /dev/null
    verificar_texto "GET /formalizacion/{id} (checklist completo, corrección 3)" "Documentación completa: listo para desembolso. Siguiente paso: la cajera lo desembolsa desde Desembolso y caja → Desembolsos."
    verificar_texto "GET /formalizacion/{id} (enlace del firmado, corrección 3, RF03: ahora 'Ver')" "Ver"
    verificar_texto "GET /formalizacion/{id} (motivo por el que no se puede regenerar, corrección 4)" \
        "No se puede regenerar: ya hay documentos firmados cargados."
    verificar_texto_ausente "GET /formalizacion/{id} (checklist completo: ya no se ofrece el botón Regenerar)" ">Regenerar<"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/formalizacion" > /dev/null
    verificar_texto "GET /formalizacion (checklist ya completo en la bandeja, corrección 1)" "Completo"

    # Corrección RN-01/RN-02 (D-68): se anula este préstamo (gerente) y más abajo (con el
    # oficial) se confirma que el cliente puede volver a pedir -- antes de la corrección
    # quedaba bloqueado para siempre porque la solicitud vieja seguía "abierta".
    postear_y_verificar_en "POST /formalizacion/{id}/anular (gerente, D-68)" \
        "/formalizacion/$ID_PRESTAMO/anular" "/formalizacion/$ID_PRESTAMO" \
        "Préstamo anulado." \
        --data-urlencode "motivo=Prueba de humo (D-68)" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"
    verificar_sql "SELECT: préstamo quedó ANULADO_ANTES_DESEMBOLSO" \
        "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO" "ANULADO_ANTES_DESEMBOLSO"
    ID_PRESTAMO_ANULADO="$ID_PRESTAMO"
fi

# ---------------------------------------------------------------------
# 4.6b. Corrección RN-01/RN-02 (D-68): con el préstamo de arriba ya
#    ANULADO_ANTES_DESEMBOLSO, el mismo cliente ya no debe quedar
#    bloqueado para siempre -- se necesita la sesión del oficial (los
#    permisos de /solicitudes son NINGUNO para el gerente), así que se
#    reloguea aparte, sin tocar $COOKIE_JAR de la pasada de gerente.
# ---------------------------------------------------------------------
if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ] \
        && [ -n "${ID_PRESTAMO_ANULADO:-}" ] && [ "$ID_PRESTAMO_ANULADO" != "0" ]; then
    COOKIE_JAR_GERENTE_D68="$COOKIE_JAR"
    COOKIE_JAR="$(temporal)"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_OFICIAL_D68="$(extraer_token_csrf)"
    if [ -z "$TOKEN_CSRF_OFICIAL_D68" ]; then
        echo "No se pudo leer el tokenCsrf de /acceso para el oficial (recheck D-68). Se omite." >&2
    else
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_D68")
        registrar "POST /acceso (login oficial, recheck D-68)" 302 "$status"
        if [ "$status" = "302" ]; then
            # RN-02: el mismo cliente puede volver a pedir un crédito propio (302 = aceptado; un
            # rechazo por RN-01/02 hace forward, 200, no redirect).
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
                -X POST "$BASE_URL/solicitudes/nuevo" \
                --data-urlencode "cedulaCliente=$CEDULA_PRUEBA" \
                --data-urlencode "monto=600.000" \
                --data-urlencode "plazoMeses=3" \
                --data-urlencode "destinoCredito=Prueba de humo (D-68, mismo cliente de nuevo)" \
                --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_D68")
            registrar "POST /solicitudes/nuevo (D-68: cliente con préstamo anulado puede pedir de nuevo)" 302 "$status"
        fi
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (oficial, recheck D-68)" 302 "$status"
    fi

    COOKIE_JAR="$COOKIE_JAR_GERENTE_D68"
fi

# ---------------------------------------------------------------------
# 2c (pedido de Joel, 17/10/2026): bloqueo de edición de los datos de evaluación -- Cliente 2
# (ID_CLIENTE_2) todavía tiene la solicitud #2 PENDIENTE_APROBACION en este punto (se rechaza
# más abajo), así que es la ventana real para probar el bloqueo sin crear un cliente aparte.
# El resto de la ficha (teléfono) debe seguir guardando siempre.
# ---------------------------------------------------------------------
if [ -n "$ID_SOLICITUD_2" ] && [ "$ID_SOLICITUD_2" != "0" ]; then
    TELEFONO_NUEVO_CLIENTE_2="0987$(date +%H%M%S)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$ID_CLIENTE_2/guardar" \
        --data-urlencode "cedulaIdentidad=$CEDULA_CLIENTE_2" \
        --data-urlencode "nombres=Cliente" \
        --data-urlencode "apellidos=Prueba Humo E4 Dos" \
        --data-urlencode "fechaNacimiento=1985-03-15" \
        --data-urlencode "estadoCivil=Casado/a" \
        --data-urlencode "telefonoWhatsapp=$TELEFONO_NUEVO_CLIENTE_2" \
        --data-urlencode "ingresoNetoComprobado=6.000.000" \
        --data-urlencode "fechaVencimientoCedula=2030-01-01" \
        --data-urlencode "conyugeNombre=Cónyuge de prueba" \
        --data-urlencode "conyugeCedula=9999998" \
        --data-urlencode "conyugeTelefono=0981000004" \
        --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
        --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
        --data-urlencode "barrio=Barrio de prueba" \
        --data-urlencode "direccionDomicilio=Calle de prueba E4" \
        --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
        --data-urlencode "tipoVivienda=ALQUILADA" \
        --data-urlencode "antiguedadDomicilioAnios=3" \
        --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
        --data-urlencode "referenciaTelefono=0981000006" \
        --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
        --data-urlencode "referencia2Telefono=0981000005" \
        --data-urlencode "situacionLaboral=COMERCIANTE" \
        --data-urlencode "cuotaOtrasDeudasMensual=0" \
        --data-urlencode "presentaComprobanteIngreso=on" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF")
    registrar "POST /clientes/{id}/guardar (2c: solo cambia el teléfono, no bloquea)" 302 "$status"
    verificar_sql "SELECT: el teléfono de Cliente 2 cambió (2c, campo fuera de la lista de evaluación)" \
        "SELECT telefono_whatsapp FROM personas WHERE id_persona=$ID_CLIENTE_2" "$TELEFONO_NUEVO_CLIENTE_2"

    # Hallazgo de Joel (prueba en pantalla, 17/10/2026): con los datos de evaluación bloqueados,
    # el <select> de Departamento también queda disabled -- el navegador real no manda
    # "idDepartamento" en el POST (igual que ya no manda "idCiudad"/"situacionLaboral" disabled).
    # Sin este caso, el servidor exigía un idDepartamento y tiraba "El departamento seleccionado
    # no existe." -- corregido en validarCiudadPerteneceADepartamento (null = confía en la ciudad
    # ya guardada). Se repite el mismo cambio de teléfono, pero sin mandar idDepartamento.
    TELEFONO_NUEVO_CLIENTE_2_B="0987$(date +%H%M%S)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$ID_CLIENTE_2/guardar" \
        --data-urlencode "cedulaIdentidad=$CEDULA_CLIENTE_2" \
        --data-urlencode "nombres=Cliente" \
        --data-urlencode "apellidos=Prueba Humo E4 Dos" \
        --data-urlencode "fechaNacimiento=1985-03-15" \
        --data-urlencode "estadoCivil=Casado/a" \
        --data-urlencode "telefonoWhatsapp=$TELEFONO_NUEVO_CLIENTE_2_B" \
        --data-urlencode "ingresoNetoComprobado=6.000.000" \
        --data-urlencode "fechaVencimientoCedula=2030-01-01" \
        --data-urlencode "conyugeNombre=Cónyuge de prueba" \
        --data-urlencode "conyugeCedula=9999998" \
        --data-urlencode "conyugeTelefono=0981000004" \
        --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
        --data-urlencode "barrio=Barrio de prueba" \
        --data-urlencode "direccionDomicilio=Calle de prueba E4" \
        --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
        --data-urlencode "tipoVivienda=ALQUILADA" \
        --data-urlencode "antiguedadDomicilioAnios=3" \
        --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
        --data-urlencode "referenciaTelefono=0981000006" \
        --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
        --data-urlencode "referencia2Telefono=0981000005" \
        --data-urlencode "situacionLaboral=COMERCIANTE" \
        --data-urlencode "cuotaOtrasDeudasMensual=0" \
        --data-urlencode "presentaComprobanteIngreso=on" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF")
    registrar "POST /clientes/{id}/guardar (2c: sin idDepartamento, como manda el <select> disabled)" 302 "$status"
    verificar_sql "SELECT: el teléfono de Cliente 2 cambió de nuevo, sin idDepartamento (2c, hallazgo del Departamento)" \
        "SELECT telefono_whatsapp FROM personas WHERE id_persona=$ID_CLIENTE_2" "$TELEFONO_NUEVO_CLIENTE_2_B"

    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$ID_CLIENTE_2/guardar" \
        --data-urlencode "cedulaIdentidad=$CEDULA_CLIENTE_2" \
        --data-urlencode "nombres=Cliente" \
        --data-urlencode "apellidos=Prueba Humo E4 Dos" \
        --data-urlencode "fechaNacimiento=1985-03-15" \
        --data-urlencode "estadoCivil=Casado/a" \
        --data-urlencode "telefonoWhatsapp=$TELEFONO_NUEVO_CLIENTE_2" \
        --data-urlencode "ingresoNetoComprobado=6.500.000" \
        --data-urlencode "fechaVencimientoCedula=2030-01-01" \
        --data-urlencode "conyugeNombre=Cónyuge de prueba" \
        --data-urlencode "conyugeCedula=9999998" \
        --data-urlencode "conyugeTelefono=0981000004" \
        --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
        --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
        --data-urlencode "barrio=Barrio de prueba" \
        --data-urlencode "direccionDomicilio=Calle de prueba E4" \
        --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
        --data-urlencode "tipoVivienda=ALQUILADA" \
        --data-urlencode "antiguedadDomicilioAnios=3" \
        --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
        --data-urlencode "referenciaTelefono=0981000006" \
        --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
        --data-urlencode "referencia2Telefono=0981000005" \
        --data-urlencode "situacionLaboral=COMERCIANTE" \
        --data-urlencode "cuotaOtrasDeudasMensual=0" \
        --data-urlencode "presentaComprobanteIngreso=on" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF")
    registrar "POST /clientes/{id}/guardar (2c: cambia el ingreso con solicitud pendiente, bloqueado)" 200 "$status"
    # F2 (21/10/2026, pedido de Joel, punto 7): este POST corre como GERENTE (COOKIE_JAR quedó en
    # COOKIE_JAR_GERENTE_D68 más arriba) -- GERENTE_ADMINISTRADOR tiene APROBAR_RECHAZAR_SOLICITUD
    # en TOTAL, así que ahora ve la versión (b) (acción directa), no la (a) de siempre.
    verificar_texto "POST /clientes/{id}/guardar (2c, gerente: mensaje de bloqueo, versión (b))" \
        "Puede rechazarla desde Aprobación, registrando el motivo, si los datos del cliente cambiaron."
    verificar_sql "SELECT: el ingreso de Cliente 2 NO cambió (2c, bloqueado)" \
        "SELECT ingreso_neto_comprobado FROM personas_laborales WHERE id_persona=$ID_CLIENTE_2" "6000000"

    # La ficha en pantalla muestra los 7 campos como solo lectura, con el aviso -- confirmado
    # acá leyendo el mismo GET que ya hace postear_y_verificar_en más abajo al verificar el
    # rechazo (no se repite un GET aparte solo para esto).
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE_2" > /dev/null
    verificar_texto "GET /clientes/{id} (2c: aviso de datos de evaluación bloqueados)" \
        "no se pueden modificar porque la solicitud"

    # F2 (21/10/2026, pedido de Joel, punto 7): mismo bloqueo, ahora como OFICIAL -- Cliente 2 /
    # solicitud #2 sigue PENDIENTE_APROBACION en este punto exacto (se rechaza más abajo), así que
    # es la misma ventana que arriba. OFICIAL_CREDITO tiene APROBAR_RECHAZAR_SOLICITUD en NINGUNO
    # -- tiene que seguir viendo la versión (a), sin cambios, aunque el gerente ya vea la (b).
    COOKIE_JAR_OFICIAL_2C="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_2C" -c "$COOKIE_JAR_OFICIAL_2C" \
        -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_OFICIAL_2C="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_2C" -c "$COOKIE_JAR_OFICIAL_2C" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_2C" > /dev/null
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_2C" -c "$COOKIE_JAR_OFICIAL_2C" \
        -X POST "$BASE_URL/clientes/$ID_CLIENTE_2/guardar" \
        --data-urlencode "cedulaIdentidad=$CEDULA_CLIENTE_2" \
        --data-urlencode "nombres=Cliente" \
        --data-urlencode "apellidos=Prueba Humo E4 Dos" \
        --data-urlencode "fechaNacimiento=1985-03-15" \
        --data-urlencode "estadoCivil=Casado/a" \
        --data-urlencode "telefonoWhatsapp=$TELEFONO_NUEVO_CLIENTE_2" \
        --data-urlencode "ingresoNetoComprobado=7.000.000" \
        --data-urlencode "fechaVencimientoCedula=2030-01-01" \
        --data-urlencode "conyugeNombre=Cónyuge de prueba" \
        --data-urlencode "conyugeCedula=9999998" \
        --data-urlencode "conyugeTelefono=0981000004" \
        --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
        --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
        --data-urlencode "barrio=Barrio de prueba" \
        --data-urlencode "direccionDomicilio=Calle de prueba E4" \
        --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
        --data-urlencode "tipoVivienda=ALQUILADA" \
        --data-urlencode "antiguedadDomicilioAnios=3" \
        --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
        --data-urlencode "referenciaTelefono=0981000006" \
        --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
        --data-urlencode "referencia2Telefono=0981000005" \
        --data-urlencode "situacionLaboral=COMERCIANTE" \
        --data-urlencode "cuotaOtrasDeudasMensual=0" \
        --data-urlencode "presentaComprobanteIngreso=on" \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_2C")
    registrar "POST /clientes/{id}/guardar (2c, oficial: cambia el ingreso con solicitud pendiente, bloqueado)" 200 "$status"
    verificar_texto "POST /clientes/{id}/guardar (2c, oficial: mensaje de bloqueo, versión (a) sin cambios)" \
        "informe al gerente para que la rechace, registrando el motivo."
    verificar_sql "SELECT: el ingreso de Cliente 2 NO cambió (2c, oficial, bloqueado)" \
        "SELECT ingreso_neto_comprobado FROM personas_laborales WHERE id_persona=$ID_CLIENTE_2" "6000000"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_2C" -c "$COOKIE_JAR_OFICIAL_2C" -X GET "$BASE_URL/salir" > /dev/null
fi

if [ -n "$ID_SOLICITUD_2" ] && [ "$ID_SOLICITUD_2" != "0" ]; then
    # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): buscador
    # AJAX de /aprobaciones, pestaña Pendientes -- mientras la segunda solicitud sigue
    # PENDIENTE_APROBACION (antes de rechazarla, más abajo). Mismo patrón que el caso de Mora:
    # pedido con X-Requested-With para recibir el fragmento (_resultados.jsp) directo.
    CODIGO_SOLICITUD_2="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT codigo_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_2" \
        2>/dev/null | tr -d '[:space:]')"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?texto=$CODIGO_SOLICITUD_2" > /dev/null
    verificar_texto "GET /aprobaciones?texto= (Pendientes, filtro encuentra por código)" "$CODIGO_SOLICITUD_2"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?texto=$CEDULA_CLIENTE_2" > /dev/null
    verificar_texto "GET /aprobaciones?texto= (Pendientes, filtro encuentra por cédula sin puntos)" "$CODIGO_SOLICITUD_2"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?texto=TextoQueNoExisteEnNingunaSolicitud" > /dev/null
    verificar_texto "GET /aprobaciones?texto= (Pendientes, sin resultados, mensaje correcto)" \
        "No se encontraron solicitudes para esa búsqueda."
    verificar_texto_ausente "GET /aprobaciones?texto= (Pendientes, sin resultados, $CODIGO_SOLICITUD_2 ausente)" \
        "$CODIGO_SOLICITUD_2"

    postear_y_verificar_en "POST /aprobaciones/{id}/rechazar (sin motivo)" "/aprobaciones/$ID_SOLICITUD_2/rechazar" \
        "/aprobaciones/$ID_SOLICITUD_2" "Seleccione el motivo del rechazo." \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"

    # D-126: el motivo "Otro" exige detalle -- sin detalle, se rechaza con mensaje claro y la
    # solicitud sigue PENDIENTE_APROBACION (probado ANTES del rechazo real, sobre la misma
    # solicitud, para no tener que crear una aparte solo para este caso).
    postear_y_verificar_en "POST /aprobaciones/{id}/rechazar (motivo Otro sin detalle)" "/aprobaciones/$ID_SOLICITUD_2/rechazar" \
        "/aprobaciones/$ID_SOLICITUD_2" "requiere un detalle" \
        --data-urlencode "idMotivoRechazo=$ID_MOTIVO_RECHAZO_OTRO" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"
    verificar_sql "SELECT: motivo Otro sin detalle NO rechazó la solicitud (D-126)" \
        "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_2" "PENDIENTE_APROBACION"

    # D-126: el motivo ahora es referencial (idMotivoRechazo); detalleMotivo es libre y opcional
    # salvo para "Otro" (ya probado arriba).
    postear_y_verificar_en "POST /aprobaciones/{id}/rechazar (con motivo)" "/aprobaciones/$ID_SOLICITUD_2/rechazar" \
        "/aprobaciones/$ID_SOLICITUD_2" "Solicitud rechazada." \
        --data-urlencode "idMotivoRechazo=$ID_MOTIVO_RECHAZO_CAPACIDAD_PAGO" \
        --data-urlencode "detalleMotivo=Prueba de humo" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF"
    verificar_sql "SELECT: solicitud quedó RECHAZADA" \
        "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_2" "RECHAZADA"
    verificar_texto "GET /aprobaciones/{id} (ajuste 6, quién rechazó y motivo)" "Rechazada por"
    verificar_sql "SELECT: id_motivo_rechazo quedó guardado (D-126)" \
        "SELECT id_motivo_rechazo FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_2" \
        "$ID_MOTIVO_RECHAZO_CAPACIDAD_PAGO"

    # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): buscador
    # AJAX de /aprobaciones, pestaña Historial -- ahora que la segunda solicitud ya quedó
    # RECHAZADA. Confirma que el texto se combina (AND) con el filtro de estado que ya existía,
    # no lo reemplaza.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?vista=historial&texto=$CODIGO_SOLICITUD_2" > /dev/null
    verificar_texto "GET /aprobaciones?vista=historial&texto= (Historial, filtro encuentra la rechazada por código)" \
        "$CODIGO_SOLICITUD_2"

    # Nombre en el orden invertido (apellido + nombre, Prueba Humo E4 Dos Cliente), mismo
    # criterio que el paso 0 (Clientes) y el paso 1 (Mora).
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?vista=historial&texto=Prueba+Humo+E4+Dos+Cliente" > /dev/null
    verificar_texto "GET /aprobaciones?vista=historial&texto= (Historial, nombre apellido+nombre encuentra igual)" \
        "$CODIGO_SOLICITUD_2"

    # Combinado con estado=APROBADA (una rechazada no puede aparecer ahí): 0 resultados --
    # confirma que el filtro de texto nunca reemplaza al de estado, se suman.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/aprobaciones?vista=historial&estado=APROBADA&texto=$CODIGO_SOLICITUD_2" > /dev/null
    verificar_texto "GET /aprobaciones?vista=historial&estado=&texto= (combinados, rechazada no aparece entre las aprobadas)" \
        "No se encontraron solicitudes para esa búsqueda."
fi

# ---------------------------------------------------------------------
# 4.7. E6 (P07/P08, caja y desembolso): preparar un préstamo PENDIENTE_DESEMBOLSO
#    con checklist completo, SIN anularlo (a diferencia del de la sección 4.6),
#    para poder probar caja y desembolso más abajo con la cajera. Necesita al
#    oficial (crear y enviar la solicitud) y al gerente (aprobar, formalizar,
#    forzar y revertir la vigencia de documentos). Se omite si no hay
#    credenciales de oficial (con eso también se omite la prueba de E6 de más
#    abajo, en la pasada de la cajera).
# ---------------------------------------------------------------------
ID_PRESTAMO_E6="0"
if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ]; then
    COOKIE_JAR_GERENTE_E6="$COOKIE_JAR"
    COOKIE_JAR="$(temporal)"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_OFICIAL_E6="$(extraer_token_csrf)"
    ID_SOLICITUD_E6="0"
    if [ -z "$TOKEN_CSRF_OFICIAL_E6" ]; then
        echo "No se pudo leer el tokenCsrf de /acceso para el oficial (E6). Se omite la preparación del préstamo de E6." >&2
    else
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E6")
        registrar "POST /acceso (login oficial, E6)" 302 "$status"
        if [ "$status" != "302" ]; then
            echo
            echo "== El login del oficial (E6) falló (status $status). Me detengo acá: no reintento. ==" >&2
            exit 1
        fi

        CEDULA_CLIENTE_E6="${CEDULA_PRUEBA}17"
        crear_persona_con_expediente "$CEDULA_CLIENTE_E6" "Cliente" "Prueba Humo E6" "6.000.000" "$TOKEN_CSRF_OFICIAL_E6"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/solicitudes/nuevo" \
            --data-urlencode "cedulaCliente=$CEDULA_CLIENTE_E6" \
            --data-urlencode "monto=2.000.000" \
            --data-urlencode "plazoMeses=6" \
            --data-urlencode "destinoCredito=Prueba de humo E6 (caja y desembolso)" \
            --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E6")
        registrar "POST /solicitudes/nuevo (alta, préstamo de E6)" 302 "$status"
        LOCATION_SOLICITUD_E6="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_SOLICITUD_E6="$(echo "$LOCATION_SOLICITUD_E6" | grep -o '[0-9]*$')"
        if [ -z "$ID_SOLICITUD_E6" ]; then
            ID_SOLICITUD_E6="0"
        fi

        postear_y_verificar_en "POST /solicitudes/{id}/enviar (préstamo de E6)" \
            "/solicitudes/$ID_SOLICITUD_E6/enviar" "/solicitudes/$ID_SOLICITUD_E6" "Solicitud enviada a aprobación" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E6"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (oficial, E6)" 302 "$status"
    fi

    COOKIE_JAR="$COOKIE_JAR_GERENTE_E6"

    if [ -n "$ID_SOLICITUD_E6" ] && [ "$ID_SOLICITUD_E6" != "0" ]; then
        postear_y_verificar_en "POST /aprobaciones/{id}/aprobar (préstamo de E6)" "/aprobaciones/$ID_SOLICITUD_E6/aprobar" \
            "/aprobaciones/$ID_SOLICITUD_E6" "Solicitud aprobada." \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF"

        status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_E6/generar" \
            --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF")
        registrar "POST /formalizacion/nueva/{id}/generar (préstamo de E6)" 302 "$status"
        LOCATION_PRESTAMO_E6="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
        ID_PRESTAMO_E6="$(echo "$LOCATION_PRESTAMO_E6" | grep -o '[0-9]*$')"
        if [ -z "$ID_PRESTAMO_E6" ]; then
            ID_PRESTAMO_E6="0"
        fi
        verificar_sql "SELECT: préstamo de E6 quedó PENDIENTE_DESEMBOLSO" \
            "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6" "PENDIENTE_DESEMBOLSO"

        # ---------------------------------------------------------------------
        # 2c (pedido de Joel, 17/10/2026), tercera etapa: préstamo PENDIENTE_DESEMBOLSO también
        # bloquea la edición de los datos de evaluación del cliente -- ventana real entre que
        # queda PENDIENTE_DESEMBOLSO (arriba) y que la cajera lo desembolsa (más abajo).
        # ---------------------------------------------------------------------
        ID_CLIENTE_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_persona FROM personas WHERE cedula_identidad='$CEDULA_CLIENTE_E6'" 2>/dev/null | tr -d '[:space:]')"
        if [ -n "$ID_CLIENTE_E6" ]; then
            # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 0): el
            # filtro de texto de /clientes ahora también encuentra "nombres apellidos" juntos y
            # "apellidos nombres" juntos (como se lee la columna "Apellidos y Nombres" de la
            # tabla), no solo cada columna por separado -- antes, "Cliente Prueba Humo E6" daba 0.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" -X GET "$BASE_URL/clientes?texto=Cliente+Prueba+Humo+E6" > /dev/null
            verificar_texto "GET /clientes?texto= (nombre+apellido concatenados encuentran al cliente, E6)" "Prueba Humo E6 Cliente"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" -X GET "$BASE_URL/clientes?texto=E6+Cliente" > /dev/null
            verificar_texto "GET /clientes?texto= (apellido+nombre concatenados encuentran al cliente, E6)" "Prueba Humo E6 Cliente"

            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/$ID_CLIENTE_E6/guardar" \
                --data-urlencode "cedulaIdentidad=$CEDULA_CLIENTE_E6" \
                --data-urlencode "nombres=Cliente" \
                --data-urlencode "apellidos=Prueba Humo E6" \
                --data-urlencode "fechaNacimiento=1985-03-15" \
                --data-urlencode "estadoCivil=Casado/a" \
                --data-urlencode "telefonoWhatsapp=0981000003" \
                --data-urlencode "ingresoNetoComprobado=6.800.000" \
                --data-urlencode "fechaVencimientoCedula=2030-01-01" \
                --data-urlencode "conyugeNombre=Cónyuge de prueba" \
                --data-urlencode "conyugeCedula=9999998" \
                --data-urlencode "conyugeTelefono=0981000004" \
                --data-urlencode "idDepartamento=$ID_DEPARTAMENTO_HUMO" \
                --data-urlencode "idCiudad=$ID_CIUDAD_HUMO" \
                --data-urlencode "barrio=Barrio de prueba" \
                --data-urlencode "direccionDomicilio=Calle de prueba E4" \
                --data-urlencode "referenciaDomicilio=Calle de prueba E4" \
                --data-urlencode "tipoVivienda=ALQUILADA" \
                --data-urlencode "antiguedadDomicilioAnios=3" \
                --data-urlencode "referenciaNombre=Referencia 1 de prueba" \
                --data-urlencode "referenciaTelefono=0981000006" \
                --data-urlencode "referencia2Nombre=Referencia 2 de prueba" \
                --data-urlencode "referencia2Telefono=0981000005" \
                --data-urlencode "situacionLaboral=COMERCIANTE" \
                --data-urlencode "cuotaOtrasDeudasMensual=0" \
                --data-urlencode "presentaComprobanteIngreso=on" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF")
            registrar "POST /clientes/{id}/guardar (2c: préstamo pendiente de desembolso, ingreso bloqueado)" 200 "$status"
            # F2 (21/10/2026, pedido de Joel, punto 7): este POST corre como GERENTE
            # (COOKIE_JAR_GERENTE_E6) -- GERENTE_ADMINISTRADOR tiene ANULAR_PRESTAMO_PRE_DESEMBOLSO
            # en TOTAL, así que ahora ve la versión (b), no la (a) de siempre.
            verificar_texto "POST /clientes/{id}/guardar (2c, gerente: mensaje de bloqueo, versión (b))" \
                "Puede anularlo desde Formalización, registrando el motivo, si los datos del cliente cambiaron."
            verificar_sql "SELECT: el ingreso del cliente de E6 NO cambió (2c, bloqueado por préstamo pendiente de desembolso)" \
                "SELECT ingreso_neto_comprobado FROM personas_laborales WHERE id_persona=$ID_CLIENTE_E6" "6000000"
        else
            echo "== 2c (préstamo pendiente de desembolso) OMITIDO: no se pudo resolver el id del cliente de E6. ==" >&2
        fi

        # El resto (elegibilidad por checklist, vigencia de documentos, caja y desembolso) se
        # prueba más abajo, en la pasada de la cajera -- /desembolso es DESEMBOLSAR, TOTAL solo
        # para su perfil (NINGUNO para el gerente, que no puede ni verlo).
        IDENTIFICADOR_OPERACION_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6" 2>/dev/null | tr -d '[:space:]')"
        ID_TIPO_CONTRATO_FIRMADO_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='CONTRATO_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"
        ID_TIPO_PAGARE_FIRMADO_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='PAGARE_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"
        # PA-21 (E10_14, hallazgo de Joel, 12/10/2026): desde esta migración el checklist exige
        # también el plan de pagos firmado -- sin esto, el préstamo nunca queda "listo" y
        # /desembolso/{id} no muestra el formulario "Confirmar desembolso" (su tokenCsrf
        # queda vacío, ver el aviso más abajo).
        ID_TIPO_PLAN_PAGOS_FIRMADO_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_tipo_documento FROM tipos_documento WHERE codigo_tipo='PLAN_PAGOS_FIRMADO'" 2>/dev/null | tr -d '[:space:]')"
        DIAS_VALIDEZ_ORIGINAL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='formalizacion.dias_validez_documentos' AND vigente_hasta IS NULL" \
            2>/dev/null | tr -d '[:space:]')"
    fi
else
    echo
    echo "== Preparación del préstamo de E6 omitida: requiere SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba. ==" >&2
fi

# ---------------------------------------------------------------------
# 5. Usuarios / Parámetros (solo lectura: no se crea ningún usuario del
#    sistema ni se cambia ningún parámetro real).
# D-151 (pedido de Joel): Usuarios pasa a ser exclusivo del administrador -- el gerente ya NO
# tiene acceso (antes daba 200, D7). Parámetros sigue en 200 para el gerente (CONSULTA: ve
# valores/historial, sin los botones de modificar -- verificados más abajo con la sesión de
# administrador).
# ---------------------------------------------------------------------
verificar GET /usuarios 403
verificar GET /usuarios/nuevo 403
verificar GET /parametros 200

# D-151: sesión propia del administrador -- confirma que SÍ entra a Usuarios (alta incluida).
COOKIE_JAR_ADMIN="$(login_admin)"
COOKIE_JAR_ANTERIOR_ADMIN="$COOKIE_JAR"
COOKIE_JAR="$COOKIE_JAR_ADMIN"
verificar GET /usuarios 200
verificar GET /usuarios/nuevo 200
verificar GET /parametros 200
curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_ADMIN" -c "$COOKIE_JAR_ADMIN" -X GET "$BASE_URL/salir" > /dev/null
COOKIE_JAR="$COOKIE_JAR_ANTERIOR_ADMIN"

# ---------------------------------------------------------------------
# 6. Cambiar contraseña (solo se abre el formulario: NO se cambia la
#    contraseña real de la cuenta usada para esta prueba).
# ---------------------------------------------------------------------
verificar GET /cambiar-contrasena 200

# ---------------------------------------------------------------------
# 7. Utilidades (montos en letras) y renovación de sesión.
# ---------------------------------------------------------------------
verificar GET "/utilidades/letras?monto=2000000" 200 -H 'X-Requested-With: XMLHttpRequest'
verificar GET /sesion/renovar 204 -H 'X-Requested-With: XMLHttpRequest'

# ---------------------------------------------------------------------
# 8. Salir.
# ---------------------------------------------------------------------
verificar GET /salir 302
verificar GET /panel 302

# ---------------------------------------------------------------------
# 9. Segunda pasada, opcional: perfil Cajero. RN de autorización (Doc. 00,
#    matriz de ServicioAutorizacion): Clientes en modo CONSULTA (ve, no
#    edita), Cotizador TOTAL, Documentos/Usuarios/Parámetros sin acceso.
#    Punto 3 del pedido de Joel: si no hay credenciales, se omite con
#    aviso -- no es un fallo.
# ---------------------------------------------------------------------
if [ -n "${SGCPC_USUARIO_CAJERA:-}" ] && [ -n "${SGCPC_CONTRASENA_CAJERA:-}" ]; then
    COOKIE_JAR="$(temporal)"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_CAJERA="$(extraer_token_csrf)"
    if [ -z "$TOKEN_CSRF_CAJERA" ]; then
        echo "No se pudo leer el tokenCsrf de /acceso para la cajera. Se omite su pasada." >&2
    else
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_CAJERA" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_CAJERA" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJERA")
        registrar "POST /acceso (login cajera)" 302 "$status"

        # Misma regla de seguridad que con el gerente: si falla, no reintentar sola.
        if [ "$status" != "302" ]; then
            echo
            echo "== El login de la cajera falló (status $status, se esperaba 302). Me detengo acá: no reintento. ==" >&2
            echo "== Revise SGCPC_USUARIO_CAJERA/SGCPC_CONTRASENA_CAJERA en ~/.sgcpc_prueba. ==" >&2
            exit 1
        fi

        verificar GET /clientes 200

        verificar GET "/clientes/$ID_CLIENTE" 200
        # Corrección de Joel (12/10/2026): antes buscaba la palabra suelta "Expediente" en toda la
        # respuesta -- un comentario de JavaScript sin gatear por permiso (clientes/ficha.jsp,
        # "Siguiente: ② Expediente") la menciona igual aunque la pestaña esté bien oculta, dando
        # un falso "presente". El id del nav-link real solo aparece cuando el bloque gateado por
        # puedeVerDocumentosBuro se renderiza -- no se toca ficha.jsp (consolidado).
        verificar_texto_ausente "GET /clientes/{id} (cajera, sin pestaña de Expediente/Documentos)" 'id="enlacePestanaExpediente"'
        verificar_texto_ausente "GET /clientes/{id} (cajera, sin pestaña de consulta al buró)" "consulta al buró"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/clientes/nuevo" \
            --data-urlencode "cedulaIdentidad=9000000" --data-urlencode "nombres=X" --data-urlencode "apellidos=Y" \
            --data-urlencode "fechaNacimiento=1990-01-01" --data-urlencode "estadoCivil=Soltero/a" \
            --data-urlencode "domicilio=Z" --data-urlencode "telefonoWhatsapp=0981000000" \
            --data-urlencode "ingresoNetoComprobado=1.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJERA")
        registrar "POST /clientes/nuevo (cajera, sin permiso)" 403 "$status"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar" \
            -F "tokenCsrf=$TOKEN_CSRF_CAJERA" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=4" \
            -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=autorizacion.pdf")
        registrar "POST /documentos/cargar (cajera, sin permiso)" 403 "$status"

        # RF03/RF04 (pedido de Joel): la cajera (CARGAR_DOCUMENTOS_CONSULTA_BURO = NINGUNO) tampoco
        # puede generar el PDF de autorización ni cargar el escaneo firmado -- mismo permiso que
        # /documentos/cargar, verificado también del lado del servidor (ControladorClientesServlet.
        # generarAutorizacionBuroPdf llama puedeCargarDocumentos aparte, porque /clientes está
        # protegida por REGISTRAR_EDITAR_CLIENTE, más amplio que lo que necesita esta ruta puntual).
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
            "$BASE_URL/clientes/$ID_CLIENTE/autorizacion-buro" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJERA")
        registrar "POST /clientes/{id}/autorizacion-buro (cajera, sin permiso, RF03/RF04)" 403 "$status"

        # RF03 Parte 2 (pedido de Joel): tampoco puede reimprimir (GET), mismo permiso.
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET \
            "$BASE_URL/clientes/$ID_CLIENTE/autorizacion-buro")
        registrar "GET /clientes/{id}/autorizacion-buro (cajera, sin permiso, RF03 Parte 2)" 403 "$status"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/documentos/cargar-autorizacion-firmada" \
            -F "tokenCsrf=$TOKEN_CSRF_CAJERA" -F "idPersona=$ID_CLIENTE" -F "idTipoDocumento=4" \
            -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=autorizacion.pdf")
        registrar "POST /documentos/cargar-autorizacion-firmada (cajera, sin permiso, RF03/RF04)" 403 "$status"

        verificar GET "/documentos/$ID_DOCUMENTO" 403
        verificar GET /usuarios 403
        verificar GET /parametros 403
        verificar GET /solicitudes 403
        verificar GET /aprobaciones 403
        verificar GET /formalizacion 403
        verificar GET /cotizador 200

        # -----------------------------------------------------------
        # Bug encontrado por Joel en la prueba manual de E6: el fondo sugerido de /caja (sin
        # caja abierta) y la suma de los montos que lista /desembolso deben coincidir siempre
        # (mismo criterio: checklist completo) -- se compara por HTTP, con los datos que ya
        # existan en la base, sin crear ningún préstamo nuevo (no ensucia la base en cada corrida).
        # Solo tiene sentido antes de que este mismo bloque abra una caja más abajo.
        # -----------------------------------------------------------
        SESIONES_CAJA_ABIERTAS_ANTES="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
        if [ "$SESIONES_CAJA_ABIERTAS_ANTES" = "0" ]; then
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            FONDO_SUGERIDO_GS="$(grep -oE '<strong>[0-9.]+ Gs\.</strong>' "$CUERPO_TMP" | head -1 | tr -dc '0-9')"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso" > /dev/null
            # B160 (16/11/2026, autorizado por Joel): la celda del monto ahora lleva
            # class="cc-monto" (columna de cifras a la derecha), asi que el patron admite
            # atributos en el <td>. Se verifico sobre el HTML del dia que encuentra LA MISMA
            # celda y suma lo mismo que el patron exacto: 1 celda, 2.000.000 Gs., igual al
            # fondo sugerido de /caja.
            SUMA_DESEMBOLSO_GS=0
            while IFS= read -r monto; do
                [ -n "$monto" ] && SUMA_DESEMBOLSO_GS=$((SUMA_DESEMBOLSO_GS + monto))
            done < <(grep -oE '<td[^>]*>[0-9][0-9.]* Gs\.</td>' "$CUERPO_TMP" | tr -dc '0-9\n')
            registrar "GET /caja fondo sugerido == suma de los montos de /desembolso (bug de la prueba manual de E6)" \
                "$SUMA_DESEMBOLSO_GS" "${FONDO_SUGERIDO_GS:-0}"
        else
            echo
            echo "== Verificación fondo sugerido == suma de /desembolso omitida: ya había una caja ABIERTA antes de este punto. ==" >&2
        fi

        # -----------------------------------------------------------
        # E6 (P07/P08): caja y desembolso, con el préstamo preparado en la
        # sección 4.7. Se omite si esa preparación no dejó un préstamo listo.
        # -----------------------------------------------------------
        if [ -n "${ID_PRESTAMO_E6:-}" ] && [ "$ID_PRESTAMO_E6" != "0" ]; then
            # Incidente real (Joel, prueba manual de E6): esta corrida NO debe tocar una caja que
            # no abrió ella misma -- podría ser una prueba manual de Joel en curso. Si ya hay una
            # caja ABIERTA a esta altura, se aborta toda la sección de caja/desembolso con un
            # aviso claro y se sigue con el resto del script, en vez de intentar abrir otra
            # (rechazada igual) o, peor, tocar la existente.
            SESIONES_CAJA_ABIERTAS_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
            if [ "$SESIONES_CAJA_ABIERTAS_E6" != "0" ]; then
                echo
                echo "== Prueba de caja y desembolso (E6) ABORTADA: ya hay una caja ABIERTA que esta corrida no abrió (posible prueba manual de Joel en curso). No se toca. ==" >&2
            else

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto "GET /caja (cajera, sin sesión: formulario de apertura, E6)" "Apertura de caja"
            verificar_texto "GET /caja (fondo sugerido visible, E6)" "Fondo sugerido"
            TOKEN_CSRF_CAJA="$(extraer_token_csrf)"

            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=3.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/abrir (E6)" 302 "$status"
            verificar_sql "SELECT: quedó una caja ABIERTA (E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "1"
            ID_SESION_CAJA_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_sesion_caja FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=1.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/abrir (segunda caja mientras la primera sigue abierta, E6)" 302 "$status"
            verificar_sql "SELECT: sigue habiendo una sola caja ABIERTA (una sola caja, E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "1"
            # UX (Joel, tras el incidente de la sesión 5): el rechazo no puede ser silencioso --
            # el mensaje debe decir desde cuándo está abierta y quién la abrió.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto "GET /caja (mensaje visible al rechazar la segunda apertura, con fecha y usuario, E6)" \
                "Ya hay una caja abierta desde el"

            # Separación de funciones (ajuste de Joel): la cajera no puede registrar una entrega,
            # aunque ENTREGAR_RECIBIR_EFECTIVO_CAJA sea TOTAL también para su perfil. Los conteos
            # de movimientos se acotan a ID_SESION_CAJA_E6 (esta caja), no a toda la tabla, porque
            # ya puede haber movimientos de pruebas manuales anteriores.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto_ausente "GET /caja (cajera, sin formulario de entrega, separación de funciones, E6)" \
                "Entregar efectivo a la caja"
            TOKEN_CSRF_CAJA="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/entregar" \
                --data-urlencode "monto=200.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/entregar (cajera, rechazado por separación de funciones, E6)" 302 "$status"
            verificar_sql "SELECT: la entrega de la cajera NO se registró (separación de funciones, E6)" \
                "SELECT count(*) FROM movimientos_caja WHERE tipo='ENTREGA_GERENTE' AND id_sesion_caja=$ID_SESION_CAJA_E6" "0"

            # Elegibilidad (Doc. 10 §10.11): con el checklist todavía incompleto (nada firmado
            # cargado), el préstamo de E6 no debe aparecer en la bandeja ni dejarse confirmar.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso" > /dev/null
            verificar_texto_ausente "GET /desembolso (checklist incompleto: el préstamo de E6 no aparece todavía)" \
                "$IDENTIFICADOR_OPERACION_E6"

            # Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7 paso E): "Caso 2" (0 listos,
            # con incompletos) -- se confirma por SQL que el préstamo de E6 está en
            # PENDIENTE_DESEMBOLSO y sin checklist completo.
            # 03/11/2026: antes contaba los PENDIENTE_DESEMBOLSO de TODA la base y exigía 1. Eso
            # convertía en FALLO cualquier préstamo de Joel que estuviera esperando su desembolso
            # (pasó con CC-2026-00006), sin que nada estuviera mal: el humo no es dueño de la base
            # y no puede exigir que nadie más tenga trabajo pendiente. Ahora la verificación es
            # sobre SU préstamo, que es lo que este caso venía a probar.
            verificar_sql "SELECT: el préstamo de E6 quedó PENDIENTE_DESEMBOLSO (Caso 2, checklist incompleto, E6)" \
                "SELECT count(*) FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6 AND estado_prestamo='PENDIENTE_DESEMBOLSO'" "1"
            verificar_texto "GET /desembolso (Caso 2: aviso de pendientes esperando los firmados, E6)" \
                "esperando los documentos firmados, que se cargan en Formalización."
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso/$ID_PRESTAMO_E6" > /dev/null
            verificar_texto_ausente "GET /desembolso/{id} (checklist incompleto: no se ofrece confirmar)" "Confirmar desembolso"

            # El gerente entrega efectivo, sube los firmados y fuerza la vigencia -- todo
            # /desembolso es DESEMBOLSAR (NINGUNO para el gerente), así que las vistas de
            # desembolso se verifican de vuelta con la sesión de la cajera, no con esta.
            COOKIE_JAR_CAJERA_E6="$COOKIE_JAR"
            COOKIE_JAR_GERENTE_E6B="$(temporal)"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X GET "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_GERENTE_E6B="$(extraer_token_csrf)"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B" > /dev/null

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto "GET /caja (gerente, ve el formulario de entrega, E6)" "Entregar efectivo a la caja"
            # Pregunta de Joel (evidencia: Doc. 00 §3.1 "Abrir / cerrar caja, arqueo" = C para el
            # gerente, X para el cajero): el servidor ya rechazaba abrir/cerrar del gerente (403,
            # FiltroAutorizacion exige TOTAL en POST y el gerente solo tiene CONSULTA) -- el bug
            # era que la pantalla igual le mostraba el formulario de cierre. Ahora no aparece.
            verificar_texto_ausente "GET /caja (gerente, sin formulario de cierre, E6)" "Cierre y rendición"
            TOKEN_CSRF_GERENTE_E6B="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=3.000.000" --data-urlencode "motivoDiferencia=" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B")
            registrar "POST /caja/cerrar (gerente, sin permiso, E6)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=1.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B")
            registrar "POST /caja/abrir (gerente, sin permiso, E6)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/caja/entregar" \
                --data-urlencode "monto=500.000" --data-urlencode "observacion=Fondo adicional (prueba de humo)" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B")
            registrar "POST /caja/entregar (gerente, E6)" 302 "$status"
            verificar_sql "SELECT: la entrega del gerente sí se registró (E6)" \
                "SELECT count(*) FROM movimientos_caja WHERE tipo='ENTREGA_GERENTE' AND id_sesion_caja=$ID_SESION_CAJA_E6" "1"

            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/documentos/cargar" \
                -F "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B" -F "idPrestamo=$ID_PRESTAMO_E6" -F "idTipoDocumento=$ID_TIPO_CONTRATO_FIRMADO_E6" \
                -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=contrato_firmado_e6.pdf")
            registrar "POST /documentos/cargar (contrato firmado, préstamo de E6)" 302 "$status"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/documentos/cargar" \
                -F "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B" -F "idPrestamo=$ID_PRESTAMO_E6" -F "idTipoDocumento=$ID_TIPO_PAGARE_FIRMADO_E6" \
                -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=pagare_firmado_e6.pdf")
            registrar "POST /documentos/cargar (pagaré firmado, préstamo de E6)" 302 "$status"
            # PA-21 (E10_14, hallazgo de Joel): tercer documento obligatorio del checklist.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X POST "$BASE_URL/documentos/cargar" \
                -F "tokenCsrf=$TOKEN_CSRF_GERENTE_E6B" -F "idPrestamo=$ID_PRESTAMO_E6" -F "idTipoDocumento=$ID_TIPO_PLAN_PAGOS_FIRMADO_E6" \
                -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=plan_pagos_firmado_e6.pdf")
            registrar "POST /documentos/cargar (plan de pagos firmado, préstamo de E6, PA-21)" 302 "$status"

            # Vigencia de documentos (decisión 6 del plan de E6): validarDocumentosVigentes usa
            # "!hoy.isAfter(fecha_documento + dias_validez)" -- con el documento recién generado
            # HOY, dias_validez=0 todavía no vence (0 días desde hoy sigue siendo hoy). Se fuerza
            # a -1 para simular que ya venció sin esperar un día real; se revierte enseguida.
            # D-151: el gerente ya no puede modificar Parámetros -- sesión propia de administrador.
            status="$(actualizar_parametro_como_admin formalizacion.dias_validez_documentos -1)"
            registrar "POST /parametros/actualizar-valor (dias_validez_documentos=-1, prueba de vigencia)" 302 "$status"
            verificar_sql "SELECT: dias_validez_documentos quedó en -1 de verdad" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='formalizacion.dias_validez_documentos' AND vigente_hasta IS NULL" \
                "-1"

            # Con el checklist ya completo, el préstamo aparece en la bandeja (que no depende de
            # la vigencia) pero la ficha lo bloquea por documentos vencidos (dias_validez=-1).
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -X GET "$BASE_URL/desembolso" > /dev/null
            verificar_texto "GET /desembolso (checklist completo: el préstamo de E6 ya aparece)" "$IDENTIFICADOR_OPERACION_E6"

            # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 5):
            # buscador AJAX de /desembolso -- mismo patrón que Mora/Aprobaciones/Solicitudes/
            # Formalización: pedido con X-Requested-With para recibir el fragmento
            # (_resultados.jsp) directo.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -H "X-Requested-With: XMLHttpRequest" \
                -X GET "$BASE_URL/desembolso?texto=$IDENTIFICADOR_OPERACION_E6" > /dev/null
            verificar_texto "GET /desembolso?texto= (filtro encuentra por N.º de operación)" "$IDENTIFICADOR_OPERACION_E6"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -H "X-Requested-With: XMLHttpRequest" \
                -X GET "$BASE_URL/desembolso?texto=$CEDULA_CLIENTE_E6" > /dev/null
            verificar_texto "GET /desembolso?texto= (filtro encuentra por cédula sin puntos)" "$IDENTIFICADOR_OPERACION_E6"

            # Nombre en el orden invertido (apellido + nombre, Prueba Humo E6 Cliente), mismo
            # criterio que los pasos anteriores.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -H "X-Requested-With: XMLHttpRequest" \
                -X GET "$BASE_URL/desembolso?texto=Prueba+Humo+E6+Cliente" > /dev/null
            verificar_texto "GET /desembolso?texto= (nombre apellido+nombre encuentra igual)" "$IDENTIFICADOR_OPERACION_E6"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -H "X-Requested-With: XMLHttpRequest" \
                -X GET "$BASE_URL/desembolso?texto=TextoQueNoExisteEnNingunPrestamo" > /dev/null
            verificar_texto "GET /desembolso?texto= (sin resultados, mensaje correcto)" \
                "No se encontraron préstamos para esa búsqueda."
            verificar_texto_ausente "GET /desembolso?texto= (sin resultados, $IDENTIFICADOR_OPERACION_E6 ausente)" \
                "$IDENTIFICADOR_OPERACION_E6"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E6" -c "$COOKIE_JAR_CAJERA_E6" -X GET "$BASE_URL/desembolso/$ID_PRESTAMO_E6" > /dev/null
            verificar_texto "GET /desembolso/{id} (documentos vencidos con dias_validez_documentos=-1)" "Vencido"
            verificar_texto_ausente "GET /desembolso/{id} (bloqueado por vigencia: no se ofrece confirmar)" "Confirmar desembolso"

            status="$(actualizar_parametro_como_admin formalizacion.dias_validez_documentos "$DIAS_VALIDEZ_ORIGINAL")"
            registrar "POST /parametros/actualizar-valor (revertir dias_validez_documentos)" 302 "$status"
            verificar_sql "SELECT: dias_validez_documentos quedó restaurado de verdad" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='formalizacion.dias_validez_documentos' AND vigente_hasta IS NULL" \
                "$DIAS_VALIDEZ_ORIGINAL"

            # DESEMBOLSAR es TOTAL solo para la cajera: el gerente no puede ni ver la bandeja.
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X GET "$BASE_URL/desembolso")
            registrar "GET /desembolso (gerente, sin permiso, E6)" 403 "$status"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E6B" -c "$COOKIE_JAR_GERENTE_E6B" -X GET "$BASE_URL/salir" > /dev/null

            COOKIE_JAR="$COOKIE_JAR_CAJERA_E6"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso/$ID_PRESTAMO_E6" > /dev/null
            verificar_texto "GET /desembolso/{id} (vigente de nuevo tras revertir el parámetro, listo para desembolsar, E6)" "Confirmar desembolso"
            # D-130 (Bloque 7/E10, antes E6/E8 D-94): el comprobante ya no se genera al confirmar --
            # hay que imprimirlo para firma ANTES, con el botón de la ficha.
            verificar_texto "GET /desembolso/{id} (aviso de imprimir el comprobante antes de confirmar, D-130)" \
                "Imprima el comprobante, haga firmar al cliente y recién después confirme el desembolso."
            TOKEN_CSRF_DESEMBOLSO="$(extraer_token_csrf)"
            ID_CONFIRMACION_E6="$(grep -o 'name="identificadorConfirmacion" value="[^"]*"' "$CUERPO_TMP" | head -1 | sed 's/.*value="//;s/"$//')"

            if verificar_listo_para_desembolsar "$ID_PRESTAMO_E6" "$TOKEN_CSRF_DESEMBOLSO" "Confirmar desembolso E6"; then
                # D-130 (regla nueva, verificación pedida por Joel): confirmar sin haber impreso el
                # comprobante se rechaza -- el préstamo tiene que seguir PENDIENTE_DESEMBOLSO.
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/desembolso/$ID_PRESTAMO_E6/confirmar" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_DESEMBOLSO" --data-urlencode "identificadorConfirmacion=$ID_CONFIRMACION_E6" \
                    --data-urlencode "confirmoEntregaEfectivo=true")
                registrar "POST /desembolso/{id}/confirmar (sin imprimir el comprobante, rechazado, D-130)" 302 "$status"
                verificar_sql "SELECT: el préstamo de E6 sigue PENDIENTE_DESEMBOLSO (comprobante no impreso, D-130)" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6" "PENDIENTE_DESEMBOLSO"

                imprimir_comprobante_desembolso "$COOKIE_JAR" "$ID_PRESTAMO_E6" "E6"

                # Casilla obligatoria (ajuste de Joel): sin confirmoEntregaEfectivo=true, se rechaza.
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/desembolso/$ID_PRESTAMO_E6/confirmar" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_DESEMBOLSO" --data-urlencode "identificadorConfirmacion=$ID_CONFIRMACION_E6" \
                    --data-urlencode "confirmoEntregaEfectivo=false")
                registrar "POST /desembolso/{id}/confirmar (sin la casilla, rechazado, E6)" 302 "$status"
                verificar_sql "SELECT: el préstamo de E6 sigue PENDIENTE_DESEMBOLSO (casilla no marcada)" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6" "PENDIENTE_DESEMBOLSO"

                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/desembolso/$ID_PRESTAMO_E6/confirmar" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_DESEMBOLSO" --data-urlencode "identificadorConfirmacion=$ID_CONFIRMACION_E6" \
                    --data-urlencode "confirmoEntregaEfectivo=true")
                registrar "POST /desembolso/{id}/confirmar (E6, primer clic)" 302 "$status"
                verificar_sql "SELECT: el préstamo de E6 quedó ACTIVO tras el desembolso" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_E6" "ACTIVO"

                # Bug de Joel: tras confirmar, redirigía a /formalizacion/{id} (403 para la cajera, sin
                # acceso a esa ruta). Ahora vuelve a /desembolso -- se sigue la redirección como cajera
                # y se confirma 200 con el mensaje de éxito visible.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso")
                registrar "GET /desembolso (siguiendo la redirección tras confirmar, cajera, E6)" 200 "$status"
                verificar_texto "GET /desembolso (mensaje de éxito tras confirmar, con operación y monto, E6)" \
                    "Desembolso de $IDENTIFICADOR_OPERACION_E6 confirmado (2.000.000 Gs.)"

                # Doble clic (misma clave de idempotencia): no debe duplicar nada.
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/desembolso/$ID_PRESTAMO_E6/confirmar" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_DESEMBOLSO" --data-urlencode "identificadorConfirmacion=$ID_CONFIRMACION_E6" \
                    --data-urlencode "confirmoEntregaEfectivo=true")
                registrar "POST /desembolso/{id}/confirmar (doble clic, mismo identificadorConfirmacion, E6)" 302 "$status"
                verificar_sql "SELECT: un solo desembolso quedó registrado (idempotencia, E6)" \
                    "SELECT count(*) FROM desembolsos WHERE id_prestamo=$ID_PRESTAMO_E6" "1"
                verificar_sql "SELECT: un solo movimiento DESEMBOLSO en la caja (idempotencia, E6)" \
                    "SELECT count(*) FROM movimientos_caja WHERE tipo='DESEMBOLSO' AND id_referencia=(SELECT id_desembolso FROM desembolsos WHERE id_prestamo=$ID_PRESTAMO_E6)" "1"

                # Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): la
                # pestaña "Solicitudes y préstamos" de /clientes/{id} -- el enlace de cada fila se
                # decide por PERMISO, nunca por el nombre del rol: la cajera no tiene VER_REPORTES
                # (ningún enlace a /reportes), el oficial sí.
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/clientes/$ID_CLIENTE_E6" > /dev/null
                verificar_texto "GET /clientes/{id} (pestaña Solicitudes y préstamos, cajera, E6)" "Solicitudes y préstamos"
                verificar_texto_ausente "GET /clientes/{id} (cajera sin VER_REPORTES: ningún enlace a /reportes, E6)" "/reportes/estado-cuenta"

                COOKIE_JAR_OFICIAL_HIST="$(temporal)"
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_HIST" -c "$COOKIE_JAR_OFICIAL_HIST" -X GET "$BASE_URL/acceso" > /dev/null
                TOKEN_CSRF_OFICIAL_HIST="$(extraer_token_csrf)"
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_HIST" -c "$COOKIE_JAR_OFICIAL_HIST" -X POST "$BASE_URL/acceso" \
                    --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_HIST")
                registrar "POST /acceso (login oficial, historial de crédito, E6)" 302 "$status"
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_HIST" -c "$COOKIE_JAR_OFICIAL_HIST" -X GET "$BASE_URL/clientes/$ID_CLIENTE_E6" > /dev/null
                verificar_texto "GET /clientes/{id} (oficial: enlace a estado de cuenta del préstamo activo, E6)" "/reportes/estado-cuenta/$ID_PRESTAMO_E6"
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_HIST" -c "$COOKIE_JAR_OFICIAL_HIST" -X GET "$BASE_URL/salir")
                registrar "GET /salir (oficial, historial de crédito, E6)" 302 "$status"
            fi

            # Cierre y rendición, con diferencia y motivo obligatorio.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA="$(extraer_token_csrf)"
            SALDO_TEORICO_CIERRE_E6="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT s.saldo_inicial + COALESCE((SELECT SUM(m.monto*m.signo) FROM movimientos_caja m WHERE m.id_sesion_caja=s.id_sesion_caja),0) FROM sesiones_caja s WHERE s.estado_sesion='ABIERTA'" \
                2>/dev/null | tr -d '[:space:]')"
            SALDO_FISICO_CON_DIFERENCIA_E6=$((SALDO_TEORICO_CIERRE_E6 - 123000))

            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=$SALDO_FISICO_CON_DIFERENCIA_E6" --data-urlencode "motivoDiferencia=" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/cerrar (con diferencia, SIN motivo, rechazado, E6)" 302 "$status"
            verificar_sql "SELECT: la caja sigue ABIERTA (motivo obligatorio, E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "1"

            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=$SALDO_FISICO_CON_DIFERENCIA_E6" --data-urlencode "motivoDiferencia=Faltante detectado en la prueba de humo" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/cerrar (con diferencia y motivo, E6)" 302 "$status"
            verificar_sql "SELECT: la caja quedó CERRADA (E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "0"

            # Caja cerrada inmutable: sin ninguna caja abierta, un intento de cerrar se rechaza.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto "GET /caja (sin sesión tras cerrar: vuelve a mostrar apertura, E6)" "Apertura de caja"
            TOKEN_CSRF_CAJA="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=0" --data-urlencode "motivoDiferencia=" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA")
            registrar "POST /caja/cerrar (sin ninguna caja abierta, rechazado, E6)" 302 "$status"

            # Revisión de la diferencia (gerente), con observación obligatoria.
            COOKIE_JAR_GERENTE_REVISION="$(temporal)"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" -X GET "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_GERENTE_REVISION="$(extraer_token_csrf)"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_REVISION" > /dev/null
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" -X GET "$BASE_URL/caja" > /dev/null
            verificar_texto "GET /caja (gerente, diferencia sin revisar, E6)" "Diferencias de arqueo sin revisar"
            # Sin ninguna caja abierta en este punto: el gerente tampoco debe ver el formulario de
            # apertura (mismo ajuste de Joel que el de cierre, de más arriba).
            verificar_texto_ausente "GET /caja (gerente, sin formulario de apertura, E6)" \
                '<input type="text" name="saldoInicial"'
            TOKEN_CSRF_GERENTE_REVISION="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=1.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_REVISION")
            registrar "POST /caja/abrir (gerente, sin caja abierta, sin permiso, E6)" 403 "$status"
            ID_SESION_CAJA_E6="$(grep -o '/caja/revisar-diferencia/[0-9]*' "$CUERPO_TMP" | tail -1 | grep -o '[0-9]*$')"

            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" \
                -X POST "$BASE_URL/caja/revisar-diferencia/$ID_SESION_CAJA_E6" \
                --data-urlencode "observacion=" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_REVISION")
            registrar "POST /caja/revisar-diferencia (sin observación, rechazado, E6)" 302 "$status"
            verificar_sql "SELECT: la diferencia sigue sin revisar (observación obligatoria, E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE id_sesion_caja=$ID_SESION_CAJA_E6 AND id_gerente_revisor IS NULL" "1"

            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" \
                -X POST "$BASE_URL/caja/revisar-diferencia/$ID_SESION_CAJA_E6" \
                --data-urlencode "observacion=Se descontó de la caja chica del local (prueba de humo)" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_REVISION")
            registrar "POST /caja/revisar-diferencia (con observación, E6)" 302 "$status"
            verificar_sql "SELECT: la diferencia quedó revisada (E6)" \
                "SELECT count(*) FROM sesiones_caja WHERE id_sesion_caja=$ID_SESION_CAJA_E6 AND id_gerente_revisor IS NOT NULL" "1"
            verificar_sql "SELECT: la observación quedó en el detalle de la auditoría (E6)" \
                "SELECT count(*) FROM auditoria_operaciones WHERE operacion='REVISAR_DIFERENCIA_CAJA' AND tipo_entidad='SESION_CAJA' AND id_entidad=$ID_SESION_CAJA_E6 AND detalle LIKE '%Se descontó de la caja chica del local%'" "1"

            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_REVISION" -c "$COOKIE_JAR_GERENTE_REVISION" -X GET "$BASE_URL/salir" > /dev/null
            fi
        else
            echo
            echo "== Prueba de caja y desembolso (E6) omitida: no se pudo preparar el préstamo de prueba (revisar sección 4.7). ==" >&2
        fi

        # ---------------------------------------------------------------------
        # Bloque 5 (P09, E7): Cobranza -- cobro normal/parcial/vuelto, anticipo,
        # cancelación total, mora, PA-12 (número de factura en el mensaje),
        # numeración continua de facturas (D-89) y timbrado agotado (D-89).
        # Corre acá adentro (sesión de la cajera ya activa) porque necesita
        # cobrar/anticipar/cancelar (DESEMBOLSAR/COBRAR, TOTAL solo para su
        # perfil) y también preparar sus propios préstamos (necesita al
        # oficial). Se omite con aviso si no hay credenciales de oficial.
        # D-75 vigente: cédulas propias (9...), nunca toca CC-2026-00014/00023
        # ni una caja que esta corrida no abrió.
        # ---------------------------------------------------------------------
        if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ]; then
            SESIONES_CAJA_ABIERTAS_E7="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
            if [ "$SESIONES_CAJA_ABIERTAS_E7" != "0" ]; then
                echo
                echo "== Bloque 5 (Cobranza) ABORTADO: ya hay una caja ABIERTA que esta corrida no abrió (posible prueba manual de Joel en curso). No se toca. ==" >&2
            else

            echo
            echo "== Bloque 5: Cobranza (E7) =="

            # Prepara un préstamo PENDIENTE_DESEMBOLSO de punta a punta (persona -> solicitud ->
            # enviar -> aprobar -> formalizar -> subir contrato/pagaré firmados), sin
            # desembolsarlo -- eso lo hace el llamador, con la caja de la cajera ya abierta.
            # Dedicado a Bloque 5 para no reutilizar los préstamos de 4.6/4.7. Usa sesiones
            # temporales propias (oficial y gerente), sin tocar $COOKIE_JAR (la de la cajera).
            preparar_prestamo_e7() {
                local sufijoCedula="$1" monto="$2" plazoMeses="$3" descripcion="$4"
                local status location idSolicitud idPrestamo cookieOficial cookieGerente tokenOficial tokenGerente cookieAnterior cedula

                cookieOficial="$(temporal)"
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$cookieOficial" -c "$cookieOficial" -X GET "$BASE_URL/acceso" > /dev/null
                tokenOficial="$(extraer_token_csrf)"
                curl -s -o /dev/null -w '%{http_code}' -b "$cookieOficial" -c "$cookieOficial" -X POST "$BASE_URL/acceso" \
                    --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
                    --data-urlencode "tokenCsrf=$tokenOficial" > /dev/null

                cedula="9$(date +%H%M%S)$sufijoCedula"
                cookieAnterior="$COOKIE_JAR"
                COOKIE_JAR="$cookieOficial"
                crear_persona_con_expediente "$cedula" "Cliente" "Prueba Humo E7 $descripcion" "6.000.000" "$tokenOficial"
                COOKIE_JAR="$cookieAnterior"

                status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$cookieOficial" -c "$cookieOficial" \
                    -X POST "$BASE_URL/solicitudes/nuevo" \
                    --data-urlencode "cedulaCliente=$cedula" \
                    --data-urlencode "monto=$monto" \
                    --data-urlencode "plazoMeses=$plazoMeses" \
                    --data-urlencode "destinoCredito=Prueba de humo E7 ($descripcion)" \
                    --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" \
                    --data-urlencode "tokenCsrf=$tokenOficial")
                registrar "POST /solicitudes/nuevo (E7, $descripcion)" 302 "$status"
                location="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
                idSolicitud="$(echo "$location" | grep -o '[0-9]*$')"
                [ -z "$idSolicitud" ] && idSolicitud="0"

                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookieOficial" -c "$cookieOficial" \
                    -X POST "$BASE_URL/solicitudes/$idSolicitud/enviar" --data-urlencode "tokenCsrf=$tokenOficial")
                registrar "POST /solicitudes/{id}/enviar (E7, $descripcion)" 302 "$status"
                curl -s -o /dev/null -w '%{http_code}' -b "$cookieOficial" -c "$cookieOficial" -X GET "$BASE_URL/salir" > /dev/null

                cookieGerente="$(temporal)"
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X GET "$BASE_URL/acceso" > /dev/null
                tokenGerente="$(extraer_token_csrf)"
                curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X POST "$BASE_URL/acceso" \
                    --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
                    --data-urlencode "tokenCsrf=$tokenGerente" > /dev/null

                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" \
                    -X POST "$BASE_URL/aprobaciones/$idSolicitud/aprobar" --data-urlencode "tokenCsrf=$tokenGerente")
                registrar "POST /aprobaciones/{id}/aprobar (E7, $descripcion)" 302 "$status"

                status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" \
                    -X POST "$BASE_URL/formalizacion/nueva/$idSolicitud/generar" \
                    --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" --data-urlencode "tokenCsrf=$tokenGerente")
                registrar "POST /formalizacion/nueva/{id}/generar (E7, $descripcion)" 302 "$status"
                location="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
                idPrestamo="$(echo "$location" | grep -o '[0-9]*$')"
                [ -z "$idPrestamo" ] && idPrestamo="0"

                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X POST "$BASE_URL/documentos/cargar" \
                    -F "tokenCsrf=$tokenGerente" -F "idPrestamo=$idPrestamo" -F "idTipoDocumento=$ID_TIPO_CONTRATO_FIRMADO" \
                    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=contrato_firmado_e7.pdf")
                registrar "POST /documentos/cargar (contrato firmado, E7, $descripcion)" 302 "$status"
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X POST "$BASE_URL/documentos/cargar" \
                    -F "tokenCsrf=$tokenGerente" -F "idPrestamo=$idPrestamo" -F "idTipoDocumento=$ID_TIPO_PAGARE_FIRMADO" \
                    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=pagare_firmado_e7.pdf")
                registrar "POST /documentos/cargar (pagaré firmado, E7, $descripcion)" 302 "$status"
                # PA-21 (E10_14, hallazgo de Joel, 12/10/2026): tercer documento obligatorio del
                # checklist -- sin esto, el préstamo nunca queda "listo" y /desembolso/{id} no
                # muestra el formulario "Confirmar desembolso" (ver el aviso en desembolsar_prestamo_e7).
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X POST "$BASE_URL/documentos/cargar" \
                    -F "tokenCsrf=$tokenGerente" -F "idPrestamo=$idPrestamo" -F "idTipoDocumento=$ID_TIPO_PLAN_PAGOS_FIRMADO" \
                    -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=plan_pagos_firmado_e7.pdf")
                registrar "POST /documentos/cargar (plan de pagos firmado, E7, $descripcion, PA-21)" 302 "$status"
                curl -s -o /dev/null -w '%{http_code}' -b "$cookieGerente" -c "$cookieGerente" -X GET "$BASE_URL/salir" > /dev/null

                PRESTAMO_PREPARADO_ID="$idPrestamo"
                PRESTAMO_PREPARADO_OPERACION="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                    "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$idPrestamo" 2>/dev/null | tr -d '[:space:]')"
            }

            # Confirma el desembolso de un préstamo PENDIENTE_DESEMBOLSO ya preparado, con la caja
            # de la cajera (COOKIE_JAR) ya abierta.
            desembolsar_prestamo_e7() {
                local idPrestamo="$1" descripcion="$2" idConfirmacion status tokenCsrf
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/desembolso/$idPrestamo" > /dev/null
                idConfirmacion="$(grep -o 'name="identificadorConfirmacion" value="[^"]*"' "$CUERPO_TMP" | head -1 | sed 's/.*value="//;s/"$//')"
                tokenCsrf="$(extraer_token_csrf)"
                if ! verificar_listo_para_desembolsar "$idPrestamo" "$tokenCsrf" "desembolsar_prestamo_e7 ($descripcion)"; then
                    return 1
                fi
                # D-130: hay que imprimir el comprobante para firma antes de poder confirmar.
                imprimir_comprobante_desembolso "$COOKIE_JAR" "$idPrestamo" "$descripcion"
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/desembolso/$idPrestamo/confirmar" \
                    --data-urlencode "tokenCsrf=$tokenCsrf" --data-urlencode "identificadorConfirmacion=$idConfirmacion" \
                    --data-urlencode "confirmoEntregaEfectivo=true")
                registrar "POST /desembolso/{id}/confirmar ($descripcion, Bloque 5)" 302 "$status"
                verificar_sql "SELECT: préstamo ($descripcion) quedó ACTIVO tras el desembolso (Bloque 5)" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$idPrestamo" "ACTIVO"
            }

            # La ficha de /cobranza/{id} puede tener hasta 3 formularios (cobrar/cancelar/
            # anticipar), cada uno con su propio campo oculto "identificadorConfirmacion" (D-77
            # punto "un identificador distinto por formulario"). Buscar por POSICIÓN (head/tail/
            # sed -n 'Np') es frágil: si el bloque "Adelantar capital" no se muestra (hay
            # vencidas) o cambia el orden de las tarjetas en ficha.jsp, agarra el campo
            # equivocado -- pedido explícito de Joel, encontrado al revisar el script antes de
            # correrlo. Busca en cambio el <form ...> cuyo action termina en "/$accion" y devuelve
            # el identificadorConfirmacion DENTRO de ese form, sin importar la posición ni si los
            # otros formularios están presentes.
            extraer_identificador_confirmacion() {
                local cuerpo="$1" accion="$2"
                awk -v accion="$accion" '
                    $0 ~ /<form/ && $0 ~ ("action=\"[^\"]*/" accion "\"") { enform = 1 }
                    enform && $0 ~ /name="identificadorConfirmacion"/ { print; exit }
                    enform && $0 ~ /<\/form>/ { enform = 0 }
                ' "$cuerpo" | grep -o 'value="[^"]*"' | head -1 | sed 's/value="//;s/"$//'
            }

            # ficha.jsp pone la etiqueta ("Costo hoy:", "Monto cobrable normal") y el <strong> con
            # el importe en líneas DISTINTAS del HTML -- un grep -oE por línea nunca matchea
            # (encontrado al revisar la corrida anterior, Joel lo confirmó). Junta todo el cuerpo
            # en una sola línea antes de buscar, así el patrón cruza el salto de línea. Devuelve
            # vacío (nunca inventa un 0) si no encuentra nada, para que el llamador pueda cortar
            # con un mensaje claro en vez de mandar un POST con el monto vacío.
            extraer_monto_etiqueta() {
                local cuerpo="$1" etiqueta="$2"
                tr '\n' ' ' < "$cuerpo" | grep -oE "$etiqueta"'[^0-9]*[0-9][0-9.]*' | grep -oE '[0-9][0-9.]*$' \
                    | head -1 | tr -d '.'
            }

            # --- Caso 5 (sin sesión): GET /cobranza redirige al login. ---
            status=$(curl -s -o /dev/null -w '%{http_code}' -c /dev/null -X GET "$BASE_URL/cobranza")
            registrar "GET /cobranza (sin sesión, redirige al login, Bloque 5)" 302 "$status"

            # --- Caso 4 (permisos): gerente y oficial reciben 403 en /cobranza y sus 3 POST. ---
            COOKIE_JAR_GERENTE_E7="$(temporal)"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X GET "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_GERENTE_E7="$(extraer_token_csrf)"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E7" > /dev/null
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X GET "$BASE_URL/cobranza")
            registrar "GET /cobranza (gerente, sin permiso, Bloque 5)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X POST "$BASE_URL/cobranza/999999999/cobrar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E7")
            registrar "POST /cobranza/{id}/cobrar (gerente, sin permiso, Bloque 5)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X POST "$BASE_URL/cobranza/999999999/cancelar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E7")
            registrar "POST /cobranza/{id}/cancelar (gerente, sin permiso, Bloque 5)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X POST "$BASE_URL/cobranza/999999999/anticipar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_E7")
            registrar "POST /cobranza/{id}/anticipar (gerente, sin permiso, Bloque 5)" 403 "$status"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E7" -c "$COOKIE_JAR_GERENTE_E7" -X GET "$BASE_URL/salir" > /dev/null

            COOKIE_JAR_OFICIAL_E7="$(temporal)"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E7" -c "$COOKIE_JAR_OFICIAL_E7" -X GET "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_OFICIAL_E7="$(extraer_token_csrf)"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E7" -c "$COOKIE_JAR_OFICIAL_E7" -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E7" > /dev/null
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E7" -c "$COOKIE_JAR_OFICIAL_E7" -X GET "$BASE_URL/cobranza")
            registrar "GET /cobranza (oficial, sin permiso, Bloque 5)" 403 "$status"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E7" -c "$COOKIE_JAR_OFICIAL_E7" -X GET "$BASE_URL/salir" > /dev/null

            # --- Preparar los 4 préstamos de Bloque 5 (cobranza normal, mora, timbrado, finalización). ---
            preparar_prestamo_e7 "7" "900.000" "3" "cobranza"
            ID_PRESTAMO_COBRANZA="$PRESTAMO_PREPARADO_ID"
            OPERACION_COBRANZA="$PRESTAMO_PREPARADO_OPERACION"

            preparar_prestamo_e7 "8" "900.000" "3" "mora"
            ID_PRESTAMO_MORA="$PRESTAMO_PREPARADO_ID"

            preparar_prestamo_e7 "9" "900.000" "3" "timbrado"
            ID_PRESTAMO_TIMBRADO="$PRESTAMO_PREPARADO_ID"

            preparar_prestamo_e7 "10" "900.000" "3" "finalizacion"
            ID_PRESTAMO_FINALIZACION="$PRESTAMO_PREPARADO_ID"

            # --- Abrir la caja de Bloque 5 con el FONDO SUGERIDO de la pantalla (D-74), no un
            # monto fijo -- pedido de Joel tras la corrida anterior (D-91): un saldoInicial fijo
            # de 3.000.000 alcanzaba para 3 préstamos pero no para el 4.º agregado, y la app
            # rechazó el desembolso de forma correcta (D-76) pero con un síntoma confuso (403 de
            # FiltroCsrf, porque ficha.jsp no renderiza el formulario si el saldo no alcanza). El
            # sugerido se LEE de la ficha, nunca se calcula en el script. Antes de abrir, se
            # verifica aparte que ese sugerido alcance para lo que este bloque va a desembolsar
            # (los 4 préstamos que acaba de preparar) -- si no alcanza, el caso da FALLO con un
            # mensaje claro en vez de dejar que cada desembolso individual falle más adelante.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA_E7="$(extraer_token_csrf)"
            FONDO_SUGERIDO_E7="$(extraer_monto_etiqueta "$CUERPO_TMP" 'Fondo sugerido')"
            NECESARIO_BLOQUE5="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT SUM(monto_aprobado) FROM prestamos WHERE id_prestamo IN ($ID_PRESTAMO_COBRANZA,$ID_PRESTAMO_MORA,$ID_PRESTAMO_TIMBRADO,$ID_PRESTAMO_FINALIZACION)" \
                2>/dev/null | tr -d '[:space:]')"
            if [ -n "$FONDO_SUGERIDO_E7" ] && [ -n "$NECESARIO_BLOQUE5" ] && [ "$FONDO_SUGERIDO_E7" -ge "$NECESARIO_BLOQUE5" ] 2>/dev/null; then
                registrar "Caja Bloque 5: el fondo sugerido alcanza para los 4 préstamos (sugerido=$FONDO_SUGERIDO_E7, necesario=$NECESARIO_BLOQUE5)" "alcanza" "alcanza"
            else
                registrar "Caja Bloque 5: el fondo sugerido alcanza para los 4 préstamos (sugerido=$FONDO_SUGERIDO_E7, necesario=$NECESARIO_BLOQUE5)" "alcanza" "no alcanza"
            fi
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=$FONDO_SUGERIDO_E7" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA_E7")
            registrar "POST /caja/abrir (Bloque 5, fondo sugerido)" 302 "$status"

            desembolsar_prestamo_e7 "$ID_PRESTAMO_COBRANZA" "cobranza"
            desembolsar_prestamo_e7 "$ID_PRESTAMO_MORA" "mora"
            desembolsar_prestamo_e7 "$ID_PRESTAMO_TIMBRADO" "timbrado"
            desembolsar_prestamo_e7 "$ID_PRESTAMO_FINALIZACION" "finalización"

            # --- Caso 6 (caja cerrada): el token CSRF y el identificadorConfirmacion se capturan
            # con la caja TODAVÍA ABIERTA -- ficha.jsp no renderiza el formulario de cobro (ni su
            # CSRF ni su identificador) cuando no hay caja abierta, así que raspar esos campos
            # DESPUÉS de cerrarla nunca funciona (el FiltroCsrf rechaza con 403 un token vacío en
            # vez del 302 de negocio que se quiere probar) -- pedido explícito de Joel, encontrado
            # al revisar la corrida anterior. Recién con esos valores ya guardados se cierra la
            # caja y se manda el POST: debe rechazarse sin dejar pago ni movimiento.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            TOKEN_CSRF_COBRO_CERRADA="$(extraer_token_csrf)"
            ID_CONF_COBRO_CERRADA="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA_E7="$(extraer_token_csrf)"
            SALDO_TEORICO_E7="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT s.saldo_inicial + COALESCE((SELECT SUM(m.monto*m.signo) FROM movimientos_caja m WHERE m.id_sesion_caja=s.id_sesion_caja),0) FROM sesiones_caja s WHERE s.estado_sesion='ABIERTA'" \
                2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=$SALDO_TEORICO_E7" --data-urlencode "motivoDiferencia=" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA_E7")
            registrar "POST /caja/cerrar (sin diferencia, antes del caso de caja cerrada, Bloque 5)" 302 "$status"

            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cobrar" \
                --data-urlencode "montoRecibido=100.000" --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_CERRADA" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRO_CERRADA")
            registrar "POST /cobranza/{id}/cobrar (caja cerrada, Bloque 5)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza/{id} (mensaje: caja cerrada, Bloque 5)" "No hay una caja abierta."
            verificar_sql "SELECT: el cobro con caja cerrada no se registró (Bloque 5)" \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA" "0"

            # Reabrir la caja para el resto de Bloque 5.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA_E7="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/abrir" \
                --data-urlencode "saldoInicial=3.000.000" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA_E7")
            registrar "POST /caja/abrir (segunda vez, Bloque 5)" 302 "$status"

            # --- Caso 3 (búsqueda: cédula inexistente, sin 500). ---
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza?q=9999999" > /dev/null
            # A3b (pedido de Joel): el mensaje cambió al reescribir buscarParaCobranza en árbol de
            # decisión (exacto/con préstamo/sin préstamo/ambiguo) -- ya no distingue "activo o en
            # mora", directamente dice que no existe el cliente.
            verificar_texto "GET /cobranza?q= (cédula inexistente, mensaje claro, Bloque 5)" "No existe un cliente con esos datos."

            # --- Casos 1/2 (búsqueda por cédula / por número de operación). ---
            CEDULA_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT p.cedula_identidad FROM prestamos pr JOIN solicitudes_credito s ON s.id_solicitud=pr.id_solicitud JOIN personas p ON p.id_persona=s.id_cliente WHERE pr.id_prestamo=$ID_PRESTAMO_COBRANZA" \
                2>/dev/null | tr -d '[:space:]')"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza?q=$CEDULA_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza?q= (búsqueda por cédula, Bloque 5)" "$OPERACION_COBRANZA"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza?q=$OPERACION_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza?q= (búsqueda por número de operación, Bloque 5)" "$OPERACION_COBRANZA"

            # --- Caso 21 (formulario tras rechazo): movido al préstamo de mora, más abajo -- este
            # préstamo recién desembolsado no tiene nada vencido, así que cualquier monto se
            # acepta como cobro normal en vez de rechazarse (D-90, causa raíz A). ---

            # --- Caso 7 (CSRF): POST sin token. ---
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cobrar" \
                --data-urlencode "montoRecibido=100.000" --data-urlencode "identificadorConfirmacion=sin-csrf-$RANDOM")
            registrar "POST /cobranza/{id}/cobrar (sin CSRF, Bloque 5)" 403 "$status"

            # D-144 (pedido de Joel): sin esto, "Cobrar" no tiene nada vencido que tocar (el
            # préstamo se acaba de desembolsar hoy) y el cobro de más abajo se rechaza.
            vencer_cuota_hoy "$ID_PRESTAMO_COBRANZA" 1

            # --- Item A paso 1 / Caso 9 / Caso 11 / Caso 20 (PA-12): cobro con vuelto e interés en
            # la cuota 1 -- el excedente no debe pasar a capital. D-90 (causa raíz D): este es el
            # cobro INMEDIATAMENTE anterior al anticipo -- "N" se captura acá. Entre esto y el
            # anticipo/cancelación de más abajo no debe correr ninguna otra operación que facture
            # (por eso el caso 10, que antes estaba entre medio, se movió al préstamo de mora).
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
            ID_CONF_COBRO_2="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
            TOTAL_CUOTA1_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT total_programado FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_COBRANZA AND pp.vigente=true AND c.numero_cuota=1" \
                2>/dev/null | tr -d '[:space:]')"
            MONTO_CON_VUELTO_COBRANZA=$((TOTAL_CUOTA1_COBRANZA + 50000))
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cobrar" \
                --data-urlencode "montoRecibido=$MONTO_CON_VUELTO_COBRANZA" --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_2" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/cobrar (monto con vuelto, con interés, Bloque 5)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza/{id} (mensaje con vuelto, Bloque 5)" "Vuelto:"
            NUMERO_FACTURA_N="$(grep -oE 'Factura [0-9]{3}-[0-9]{3}-[0-9]{7}' "$CUERPO_TMP" | head -1)"
            registrar "GET /cobranza/{id} (PA-12: número de factura en el mensaje, cobro 1, Bloque 5)" "presente" \
                "$([ -n "$NUMERO_FACTURA_N" ] && echo presente || echo ausente)"
            verificar_sql "SELECT: la cuota 1 (Bloque 5) quedó PAGADA" \
                "SELECT estado_pago FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_COBRANZA AND pp.vigente=true AND c.numero_cuota=1" "PAGADA"
            # D-90 (causa raíz A): filtra por el pago ESPECÍFICO (identificador_confirmacion), no
            # por "toda cuota 1" -- si alguna otra aplicación tocara la misma cuota, la vieja
            # consulta traía más de una fila y rompía el formato de la tabla final.
            verificar_sql "SELECT: el vuelto no pasó a capital (capital aplicado == capital programado, Bloque 5)" \
                "SELECT (ap.monto_capital = c.capital_programado) FROM aplicaciones_pago ap JOIN cuotas c ON c.id_cuota=ap.id_cuota JOIN pagos p ON p.id_pago=ap.id_pago WHERE p.identificador_confirmacion='$ID_CONF_COBRO_2' AND c.numero_cuota=1" "t"
            verificar_sql "SELECT: hay un movimiento VUELTO separado del COBRO (Bloque 5)" \
                "SELECT count(*) FROM movimientos_caja m JOIN pagos p ON p.id_pago=m.id_referencia WHERE p.identificador_confirmacion='$ID_CONF_COBRO_2' AND m.tipo='VUELTO'" "1"

            # --- Caso 8 (idempotencia): repetir el mismo identificador_confirmacion no cobra dos veces. ---
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cobrar" \
                --data-urlencode "montoRecibido=$MONTO_CON_VUELTO_COBRANZA" --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_2" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/cobrar (doble clic, mismo identificadorConfirmacion, Bloque 5)" 302 "$status"
            # D-90 (causa raíz A): filtra por esa clave específica, no por "todos los pagos del
            # préstamo" -- así la idempotencia se prueba sola, sin importar cuántos otros pagos
            # tenga el préstamo en ese momento.
            verificar_sql "SELECT: un solo pago quedó registrado con esa clave (idempotencia, Bloque 5)" \
                "SELECT count(*) FROM pagos WHERE identificador_confirmacion='$ID_CONF_COBRO_2'" "1"

            # --- Caso 10 (parcial con promesa): movido al préstamo de mora, más abajo -- mismo
            # motivo que el caso 21 (D-90, causa raíz A/D): sin nada vencido, "parcial" nunca se
            # dispara, y de paso factura un cobro extra entre N y N+1 (causa raíz D). ---

            # --- Item A paso 2 / Caso 13 (anticipo, sin factura, interés en curso = 0): D-90 --
            # el monto tiene que quedar SIEMPRE por debajo del saldo total (un guaraní menos),
            # nunca el saldo completo -- un anticipo que cubre todo el saldo se rechaza (D-90,
            # "use Cancelación total"; el préstamo real que expuso este caso, CC-2026-00028,
            # quedó ACTIVO con saldo 0 para siempre). Primero se prueba justo ese rechazo, y
            # recién después el anticipo válido (saldo − 1) que deja el resto a capital.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            # D-145 (pedido de Joel, punto 2): el detalle del interés devengado quitó el texto
            # "interés mínimo del período en curso" de la pantalla -- ahora va detrás de "Ver
            # detalle del adelanto", con el mismo valor como "Interés devengado hoy".
            verificar_texto "GET /cobranza/{id} (D-145: interés devengado hoy = 0, Bloque 5)" \
                "Interés devengado hoy: 0 Gs."
            SALDO_PENDIENTE_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT SUM(c.capital_programado - c.capital_pagado) FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_COBRANZA AND pp.vigente=true AND c.estado_pago != 'PAGADA'" \
                2>/dev/null | tr -d '[:space:]')"

            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
            ID_CONF_ANTICIPO_TODO="$(extraer_identificador_confirmacion "$CUERPO_TMP" anticipar)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/anticipar" \
                --data-urlencode "montoRecibido=$SALDO_PENDIENTE_COBRANZA" --data-urlencode "identificadorConfirmacion=$ID_CONF_ANTICIPO_TODO" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/anticipar (D-90: cubre todo el saldo, rechazado, Bloque 5)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza/{id} (D-90: mensaje exacto del rechazo, Bloque 5)" \
                "El monto cubre todo el saldo del préstamo. Use Cancelación total."
            verificar_sql "SELECT: el anticipo rechazado por D-90 no dejó ningún pago (Bloque 5)" \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA AND tipo='ANTICIPO'" "0"

            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
            ID_CONF_ANTICIPO="$(extraer_identificador_confirmacion "$CUERPO_TMP" anticipar)"
            SALDO_MENOS_UNO_COBRANZA=$((SALDO_PENDIENTE_COBRANZA - 1))
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/anticipar" \
                --data-urlencode "montoRecibido=$SALDO_MENOS_UNO_COBRANZA" --data-urlencode "identificadorConfirmacion=$ID_CONF_ANTICIPO" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/anticipar (todo a capital menos 1 Gs, Bloque 5)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza/{id} (PA-12: anticipo sin factura, Bloque 5)" "Sin factura (no hay conceptos gravados)."
            verificar_sql "SELECT: el anticipo no generó factura (Bloque 5)" \
                "SELECT count(*) FROM facturas f JOIN pagos p ON p.id_pago=f.id_pago WHERE p.id_prestamo=$ID_PRESTAMO_COBRANZA AND p.tipo='ANTICIPO'" "0"

            # --- Item A paso 3 / Casos 16/17/18 (cancelación total sin conceptos gravados ->
            # FINALIZADO, sin factura, redirect SIN 404 -- D-89, el bug reportado por Joel).
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            COSTO_HOY_COBRANZA="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-cancelar-todo')"
            if [ -z "$COSTO_HOY_COBRANZA" ]; then
                echo "== Caso de cancelación (Bloque 5) OMITIDO: no se pudo leer data-monto-cancelar-todo en la ficha. ==" >&2
            else
                TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
                ID_CONF_CANCELAR="$(extraer_identificador_confirmacion "$CUERPO_TMP" cancelar)"
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cancelar" \
                    --data-urlencode "montoRecibido=$COSTO_HOY_COBRANZA" --data-urlencode "identificadorConfirmacion=$ID_CONF_CANCELAR" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
                registrar "POST /cobranza/{id}/cancelar (Bloque 5)" 302 "$status"
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza")
                registrar "GET /cobranza tras cancelar (SIN 404, D-89, Bloque 5)" 200 "$status"
                verificar_texto "GET /cobranza tras cancelar (mensaje de éxito visible, Bloque 5)" "Cancelación total aplicada."
                verificar_texto "GET /cobranza tras cancelar (PA-12: sin factura, Bloque 5)" "Sin factura (no hay conceptos gravados)."
                verificar_sql "SELECT: el préstamo (Bloque 5) quedó FINALIZADO" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA" "FINALIZADO"
                verificar_sql "SELECT: la cancelación no generó factura (sin conceptos gravados, Bloque 5)" \
                    "SELECT count(*) FROM facturas f JOIN pagos p ON p.id_pago=f.id_pago WHERE p.id_prestamo=$ID_PRESTAMO_COBRANZA AND p.id_pago=(SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA ORDER BY id_pago DESC LIMIT 1)" "0"
            fi

            # --- Caso 19 (mora): desplazar el calendario del préstamo de mora 70 días hacia atrás
            # (múltiplo de 7: conserva el día de la semana), igual que D-81 -- la cuota 1 ya está
            # pagada (paso anterior); las cuotas 2 y 3 quedan vencidas, con moratorio y punitorio a
            # cobrar. Acotado ESTRICTAMENTE al plan vigente de este préstamo; se verifica antes que
            # ningún vencimiento desplazado choque con un feriado activo -- si choca, se omite solo
            # este caso (no todo el script), sin tocar nada.
            ID_PLAN_MORA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_plan_pago FROM planes_pago WHERE id_prestamo=$ID_PRESTAMO_MORA AND vigente=true" 2>/dev/null | tr -d '[:space:]')"
            COLISIONES_FERIADOS_MORA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM cuotas c JOIN feriados f ON f.fecha = (c.fecha_vencimiento - INTERVAL '70 days')::date AND f.activo WHERE c.id_plan_pago=$ID_PLAN_MORA" \
                2>/dev/null | tr -d '[:space:]')"
            if [ "$COLISIONES_FERIADOS_MORA" != "0" ]; then
                echo "== Caso de mora (Bloque 5) OMITIDO: el desplazamiento de -70 días chocaría con un feriado activo. ==" >&2
            else
                PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q > /dev/null <<SQL
BEGIN;
UPDATE prestamos SET fecha_desembolso = fecha_desembolso - INTERVAL '70 days',
    fecha_formalizacion = fecha_formalizacion - INTERVAL '70 days',
    primera_fecha_vencimiento = primera_fecha_vencimiento - INTERVAL '70 days'
    WHERE id_prestamo = $ID_PRESTAMO_MORA;
UPDATE planes_pago SET fecha_generacion = fecha_generacion - INTERVAL '70 days'
    WHERE id_plan_pago = $ID_PLAN_MORA;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento - INTERVAL '70 days',
    fecha_vencimiento_teorica = fecha_vencimiento_teorica - INTERVAL '70 days'
    WHERE id_plan_pago = $ID_PLAN_MORA;
COMMIT;
SQL
                if [ "$?" != "0" ]; then
                    echo "== Caso de mora (Bloque 5) ABORTADO: el UPDATE de fechas falló (revisar el mensaje de psql de arriba). ==" >&2
                else
                    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_MORA" > /dev/null
                    verificar_texto "GET /cobranza/{id} (mora: cuotas vencidas, Bloque 5)" "Moratorio"
                    verificar_sql "SELECT: hay cuotas vencidas en el préstamo de mora (Bloque 5)" \
                        "SELECT (count(*) > 0) FROM cuotas WHERE id_plan_pago=$ID_PLAN_MORA AND estado_pago != 'PAGADA' AND fecha_vencimiento < CURRENT_DATE" "t"

                    MONTO_EXIGIBLE_MORA="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-pagar-cuota')"
                    if [ -z "$MONTO_EXIGIBLE_MORA" ]; then
                        echo "== Casos de mora (Bloque 5) OMITIDOS: no se pudo leer data-monto-pagar-cuota en la ficha. ==" >&2
                    else
                        # Caso 21 (formulario tras rechazo, D-90 causa raíz A): acá SÍ hay vencido
                        # de verdad -- un monto menor a lo exigible, sin fecha de promesa, se
                        # rechaza y el formulario conserva el monto.
                        MONTO_PARCIAL_MORA=$((MONTO_EXIGIBLE_MORA / 2))
                        TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
                        ID_CONF_COBRO_MORA_1="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_MORA/cobrar" \
                            --data-urlencode "montoRecibido=$MONTO_PARCIAL_MORA" --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_MORA_1" \
                            --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
                        registrar "POST /cobranza/{id}/cobrar (mora: monto insuficiente sin promesa, rechazado, Bloque 5)" 302 "$status"
                        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_MORA" > /dev/null
                        verificar_texto "GET /cobranza/{id} (mora: formulario conserva el monto tras el rechazo, Bloque 5)" \
                            "value=\"$MONTO_PARCIAL_MORA\""

                        # Caso 10 (parcial con promesa), mismo préstamo y mismo monto insuficiente
                        # -- esta vez CON fecha de promesa: aceptado, gestión de cobranza registrada.
                        TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
                        ID_CONF_COBRO_MORA_2="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                        FECHA_PROMESA_E7="$(date -d '+5 days' +%Y-%m-%d 2>/dev/null || date -v+5d +%Y-%m-%d)"
                        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_MORA/cobrar" \
                            --data-urlencode "montoRecibido=$MONTO_PARCIAL_MORA" --data-urlencode "fechaPromesa=$FECHA_PROMESA_E7" \
                            --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_MORA_2" --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
                        registrar "POST /cobranza/{id}/cobrar (mora: parcial con promesa, Bloque 5)" 302 "$status"
                        verificar_sql "SELECT: la gestión de cobranza (promesa, mora, Bloque 5) quedó registrada" \
                            "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_MORA AND tipo='PAGO_PARCIAL'" "1"

                        # B113 (D-89, item A: numeración continua, FISCAL). Acá se compara la
                        # secuencia, y no antes: la factura de ESTE cobro es la N+1 de la capturada
                        # en el cobro 1 (NUMERO_FACTURA_N). En el medio, el humo hace un anticipo y
                        # una cancelación total, y ya verifica que ninguno de los dos emite factura.
                        #
                        # Antes había acá arriba un cobro ARTIFICIAL "para N+1" que nunca funcionó:
                        # corría ANTES del UPDATE que atrasa 70 días las fechas, así que el préstamo
                        # todavía no tenía nada vencido y confirmarCobro lo rechazaba por D-144. El
                        # "registrar" de ese POST esperaba 302, que es lo que devuelve TANTO un cobro
                        # exitoso como uno rechazado, así que pasaba en verde; sin cobro no había
                        # factura, el segundo número quedaba vacío y la comparación desaparecía en
                        # silencio. Se borró: este cobro, que sí funciona, ya da la factura que hace
                        # falta -- menos líneas y una verificación que de verdad corre.
                        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_MORA" > /dev/null
                        NUMERO_FACTURA_N1="$(grep -oE 'Factura [0-9]{3}-[0-9]{3}-[0-9]{7}' "$CUERPO_TMP" | head -1)"
                        verificar_secuencia_factura "$NUMERO_FACTURA_N" "$NUMERO_FACTURA_N1" \
                            "el parcial con promesa de la mora"

                        # D-117 (reemplaza a PruebaD117PromesaPagoParcial.java, borrada): la promesa
                        # recién creada NO debe quedar cumplida con el mismo pago que la generó --
                        # era el bug de D-117 (now() de PostgreSQL no avanza dentro de la transacción
                        # de confirmarCobro, así que el pago contaba en su propia ventana).
                        verificar_sql "SELECT: la promesa (D-117) NO quedó cumplida por el mismo pago que la generó (Bloque 5)" \
                            "SELECT cumplida FROM gestiones_cobranza g JOIN pagos p ON p.id_pago = g.id_pago WHERE p.identificador_confirmacion='$ID_CONF_COBRO_MORA_2' AND g.tipo='PAGO_PARCIAL'" "f"

                        # Cobro del resto (moratorio+punitorio): se relee la ficha para que el
                        # monto exigible ya refleje el pago parcial de arriba.
                        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_MORA" > /dev/null
                        MONTO_RESTANTE_MORA="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-pagar-cuota')"
                        if [ -z "$MONTO_RESTANTE_MORA" ]; then
                            echo "== Caso de mora (moratorio+punitorio, Bloque 5) OMITIDO: no se pudo releer data-monto-pagar-cuota. ==" >&2
                        else
                            # B114 (05/11/2026, hallazgo de Joel en pantalla): este cobro paga el
                            # monto REDONDEADO hacia abajo (data-monto-pagar-cuota-redondeado), que
                            # es con el que la ficha autocompleta "Recibido" -- no el exacto. Es el
                            # agujero que dejó pasar el defecto: el humo siempre pagaba el exacto
                            # (o más), así que "recibido >= exigible" era siempre cierto y el
                            # control de pago parcial nunca se cruzaba con el redondeo de D-141.
                            # Con cuotas vencidas de verdad, el monto redondeado está 1..49 Gs. por
                            # debajo de lo exigible y el servidor lo rechazaba pidiendo una fecha
                            # de promesa, contradiciendo a la pantalla, que ofrecía "redondeo a
                            # favor del cliente".
                            # Lo aplicado NO cambia (se distribuye el monto exacto, D-141), así que
                            # las verificaciones de moratorio/punitorio/IVA de más abajo siguen
                            # dando lo mismo: lo único distinto es el efectivo que entra a la caja.
                            MONTO_REDONDEO_PARAM="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='credito.redondeo_efectivo' AND vigente_hasta IS NULL" 2>/dev/null | tr -d '[:space:]')"
                            MONTO_A_PAGAR_MORA="$MONTO_RESTANTE_MORA"
                            if [ -n "$MONTO_REDONDEO_PARAM" ] && [ "$MONTO_REDONDEO_PARAM" -gt 0 ] 2>/dev/null; then
                                MONTO_A_PAGAR_MORA=$(( MONTO_RESTANTE_MORA / MONTO_REDONDEO_PARAM * MONTO_REDONDEO_PARAM ))
                            fi
                            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
                            ID_CONF_COBRO_MORA_3="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_MORA/cobrar" \
                                --data-urlencode "montoRecibido=$MONTO_A_PAGAR_MORA" --data-urlencode "identificadorConfirmacion=$ID_CONF_COBRO_MORA_3" \
                                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
                            registrar "POST /cobranza/{id}/cobrar (mora: moratorio+punitorio, con el monto redondeado, B114, Bloque 5)" 302 "$status"
                            # B114: si el monto exacto ya era múltiplo del parámetro, este cobro no
                            # ejercita el redondeo. No se calla: se registra la precondición, para
                            # que no desaparezca del conteo (lección de B113).
                            if [ "$MONTO_A_PAGAR_MORA" = "$MONTO_RESTANTE_MORA" ]; then
                                registrar "PRECONDICIÓN no dada (B114, Bloque 5): el monto exigible ($MONTO_RESTANTE_MORA) ya era múltiplo de $MONTO_REDONDEO_PARAM, el redondeo no se ejercitó" \
                                    "sin-redondeo" "sin-redondeo"
                            else
                                # El cobro NO puede quedar como pago parcial: la deuda se salda por
                                # completo y no se le pide ninguna promesa al cliente.
                                verificar_sql "SELECT: el cobro con el monto redondeado y cuotas vencidas quedó NORMAL, no PARCIAL (B114, Bloque 5)" \
                                    "SELECT tipo FROM pagos WHERE identificador_confirmacion='$ID_CONF_COBRO_MORA_3'" "NORMAL"
                                verificar_sql "SELECT: ese cobro registró el redondeo a favor del cliente (B114, Bloque 5)" \
                                    "SELECT (monto_redondeo = $(( MONTO_RESTANTE_MORA - MONTO_A_PAGAR_MORA ))) FROM pagos WHERE identificador_confirmacion='$ID_CONF_COBRO_MORA_3'" "t"
                                verificar_sql "SELECT: se aplicó el monto EXACTO aunque entró el redondeado (B114, Bloque 5)" \
                                    "SELECT (monto_aplicado = $MONTO_RESTANTE_MORA AND monto_recibido = $MONTO_A_PAGAR_MORA) FROM pagos WHERE identificador_confirmacion='$ID_CONF_COBRO_MORA_3'" "t"
                                verificar_sql "SELECT: ese cobro NO generó una gestión de cobranza por los guaraníes del redondeo (B114, Bloque 5)" \
                                    "SELECT count(*) FROM gestiones_cobranza g JOIN pagos p ON p.id_pago=g.id_pago WHERE p.identificador_confirmacion='$ID_CONF_COBRO_MORA_3'" "0"
                            fi

                            # D-117 (contraparte): un pago POSTERIOR que cubre lo prometido SÍ debe
                            # cumplir la promesa -- confirma que el fix (idPagoExcluido solo excluye
                            # el pago que generó la gestión) no rompió este caso.
                            verificar_sql "SELECT: un pago posterior que cubre lo prometido SÍ cumple la promesa (D-117, Bloque 5)" \
                                "SELECT cumplida FROM gestiones_cobranza g JOIN pagos p ON p.id_pago = g.id_pago WHERE p.identificador_confirmacion='$ID_CONF_COBRO_MORA_2' AND g.tipo='PAGO_PARCIAL'" "t"

                            ID_PAGO_MORA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                                "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_MORA ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
                            # D-91 (diagnóstico de la corrida anterior): por RN-10 (moratorio ->
                            # punitorio -> interés -> capital), el moratorio/punitorio de la cuota
                            # vencida se cobra entero en el PRIMER cobro que alcance para cubrirlo
                            # (acá, el parcial con promesa de más arriba) -- no necesariamente en
                            # este último cobro. Se verifica entonces sobre el TOTAL de
                            # aplicaciones_pago del préstamo, no sobre un pago en particular:
                            # moratorio cobrado > 0, y punitorio cobrado = 30 % del moratorio (la
                            # relación entre tasa_moratoria y tasa_punitoria de este préstamo de
                            # prueba, con ±1 Gs de margen por redondeo).
                            verificar_sql "SELECT: hubo moratorio cobrado en la mora, en total (Bloque 5)" \
                                "SELECT (SUM(ap.monto_moratorio) > 0) FROM aplicaciones_pago ap JOIN pagos p ON p.id_pago=ap.id_pago WHERE p.id_prestamo=$ID_PRESTAMO_MORA" "t"
                            verificar_sql "SELECT: el punitorio total es 30 % del moratorio total, ±1 Gs (Bloque 5)" \
                                "SELECT (ABS(SUM(ap.monto_punitorio) - ROUND(SUM(ap.monto_moratorio) * 0.30)) <= 1) FROM aplicaciones_pago ap JOIN pagos p ON p.id_pago=ap.id_pago WHERE p.id_prestamo=$ID_PRESTAMO_MORA" "t"
                            # D-85: el IVA se verifica sobre el NETO (total_gravada_10 ya incluye
                            # el IVA en este sistema -- confirmado con los números reales de una
                            # corrida anterior: total_gravada_10=neto+iva, no solo neto). ±1 Gs de
                            # margen (encontrado en esta corrida, préstamo de mora con moratorio y
                            # punitorio reales): iva_10 se calculó en la app con HALF_UP sobre los
                            # componentes exactos antes de redondear cada uno; recalcularlo acá al
                            # revés, sobre el neto YA redondeado, puede quedar a ±1 Gs por el mismo
                            # arrastre de redondeo que ya se tolera dos líneas arriba (moratorio/
                            # punitorio) -- no es un error de la factura.
                            verificar_sql "SELECT: la factura de la mora cuadra con el IVA, ±1 Gs (D-85, Bloque 5)" \
                                "SELECT (ABS(iva_10 - ROUND((total_gravada_10 - iva_10) * 0.10)) <= 1) FROM facturas WHERE id_pago=$ID_PAGO_MORA" "t"
                        fi
                    fi
                fi
            fi

            # --- Caso nuevo (pregunta de Joel, 2026-09-28): un préstamo se paga COMPLETO con tres
            # cobros normales seguidos (no Cancelación total, no anticipo) -- confirma que
            # confirmarCobro lleva el préstamo a FINALIZADO por el mismo camino que un cobro
            # normal (ServicioCobranzaTest.testCobroQuePagaLaUltimaCuotaPendienteDaEstadoFinalizado
            # ya prueba la cadena distribuirPago -> reconstruirConCuotasActualizadas ->
            # calcularEstadoSegunCuotas en aislamiento; esto la prueba contra la base real por
            # HTTP, con la persistencia y la ficha). El monto de cada cobro se LEE de la ficha
            # ("Monto cobrable normal"), nunca se calcula en el script -- como pidió Joel. Se
            # corre ANTES del bloque de timbrado agotado para no verse afectado por el numerador
            # forzado al límite.
            CASO_FINALIZACION_COMPLETO=1
            for NUMERO_COBRO_FINALIZACION in 1 2 3; do
                # D-144 (pedido de Joel): antes de cada cobro, adelanta justo la cuota que le toca
                # a este cobro (1, luego 2, luego 3) para que "Cobrar" tenga algo vencido -- nunca
                # las demás, que tienen que seguir en el futuro para que el monto exigible de este
                # cobro no se mezcle con el de los próximos (calcularMontoCobrableNormal corta en
                # la primera cuota pendiente que no esté vencida).
                vencer_cuota_hoy "$ID_PRESTAMO_FINALIZACION" "$NUMERO_COBRO_FINALIZACION"
                curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_FINALIZACION" > /dev/null
                TOKEN_CSRF_FINALIZACION="$(extraer_token_csrf)"
                ID_CONF_FINALIZACION="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                MONTO_COBRO_FINALIZACION="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-pagar-cuota')"
                if [ -z "$MONTO_COBRO_FINALIZACION" ]; then
                    echo "== Caso de finalización por cobro (Bloque 5) ABORTADO en el cobro $NUMERO_COBRO_FINALIZACION/3: no se pudo leer data-monto-pagar-cuota en la ficha. ==" >&2
                    CASO_FINALIZACION_COMPLETO=0
                    break
                fi
                status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_FINALIZACION/cobrar" \
                    --data-urlencode "montoRecibido=$MONTO_COBRO_FINALIZACION" --data-urlencode "identificadorConfirmacion=$ID_CONF_FINALIZACION" \
                    --data-urlencode "tokenCsrf=$TOKEN_CSRF_FINALIZACION")
                registrar "POST /cobranza/{id}/cobrar (cobro $NUMERO_COBRO_FINALIZACION/3 hasta FINALIZADO, Bloque 5)" 302 "$status"
            done

            if [ "$CASO_FINALIZACION_COMPLETO" = "1" ]; then
                # Tras el 3er cobro el préstamo queda FINALIZADO: redirigirTrasOperacion manda a
                # /cobranza (búsqueda), no a la ficha (D-89, mismo criterio que la cancelación) --
                # se relee ahí el mensaje de éxito, no siguiendo el redirect del POST de arriba.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza")
                registrar "GET /cobranza tras el 3er cobro (SIN 404, redirect a la búsqueda, Bloque 5)" 200 "$status"
                verificar_texto "GET /cobranza tras el 3er cobro (mensaje de éxito visible, Bloque 5)" "Cobro de"
                verificar_texto "GET /cobranza tras el 3er cobro (PA-12: número de factura en el mensaje, Bloque 5)" "Factura "
                verificar_sql "SELECT: el préstamo (finalización) quedó FINALIZADO tras el 3er cobro normal (Bloque 5)" \
                    "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_FINALIZACION" "FINALIZADO"
                verificar_sql "SELECT: las 3 cuotas (finalización) quedaron PAGADAS (Bloque 5)" \
                    "SELECT count(*) FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_FINALIZACION AND pp.vigente=true AND c.estado_pago='PAGADA'" "3"
                verificar_sql "SELECT: el saldo de capital (finalización) quedó en 0 (Bloque 5)" \
                    "SELECT COALESCE(SUM(c.capital_programado - c.capital_pagado),0) FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_FINALIZACION AND pp.vigente=true" "0"

                # La ficha de un préstamo FINALIZADO ya NO da 404 desde D-97 (Bloque 2, E8): sigue
                # existiendo en modo solo lectura, para poder reimprimir su historial -- este caso
                # ya no espera 404 (criterio D-89 original), sino 200 sin el formulario de cobro,
                # el mismo comportamiento que ya prueba el bloque E8/Bloque 2 más abajo.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_FINALIZACION")
                registrar "GET /cobranza/{id} (finalización, préstamo FINALIZADO, solo lectura desde D-97, Bloque 5)" 200 "$status"
                verificar_texto_ausente "GET /cobranza/{id} (finalización, préstamo FINALIZADO, sin formulario de cobro, Bloque 5)" \
                    "/cobranza/$ID_PRESTAMO_FINALIZACION/cobrar\""
            fi

            # --- Casos 22/23 (timbrado agotado, D-89): forzar fiscal.factura_numero_hasta justo en
            # el numerador actual, con trap ... EXIT para restaurarlo aunque el script falle a
            # mitad de camino. La cajera no tiene acceso a /parametros (403): se usa una sesión de
            # gerente aparte, sin tocar $COOKIE_JAR (la de la cajera, que sí hace los intentos de
            # cobro/anticipo).
            ANIO_TIMBRADO_ACTUAL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT EXTRACT(YEAR FROM valor_parametro::date)::int FROM parametros_sistema WHERE clave_parametro='fiscal.timbrado_vigencia_desde' AND vigente_hasta IS NULL" \
                2>/dev/null | tr -d '[:space:]')"
            VALOR_NUMERADOR_FACTURA_ACTUAL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT COALESCE((SELECT ultimo_valor FROM numeradores WHERE clave='FACTURA' AND anio=$ANIO_TIMBRADO_ACTUAL), 0)" \
                2>/dev/null | tr -d '[:space:]')"
            FACTURA_NUMERO_HASTA_ORIGINAL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.factura_numero_hasta' AND vigente_hasta IS NULL" \
                2>/dev/null | tr -d '[:space:]')"

            # Guarda pedida por Joel: si una corrida anterior quedó interrumpida sin restaurar el
            # parámetro, no seguir a ciegas -- se aborta si el tope ya está sospechosamente cerca
            # (menos de 1000 números de margen) del numerador actual.
            if [ "$FACTURA_NUMERO_HASTA_ORIGINAL" -lt "$((VALOR_NUMERADOR_FACTURA_ACTUAL + 1000))" ]; then
                echo "== ABORTADO: fiscal.factura_numero_hasta ($FACTURA_NUMERO_HASTA_ORIGINAL) ya está sospechosamente cerca del numerador actual ($VALOR_NUMERADOR_FACTURA_ACTUAL) -- parece que una corrida anterior no lo restauró. Revise a mano antes de seguir. ==" >&2
                exit 1
            fi

            COOKIE_JAR_GERENTE_TIMBRADO="$(temporal)"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_TIMBRADO" -c "$COOKIE_JAR_GERENTE_TIMBRADO" -X GET "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_GERENTE_TIMBRADO="$(extraer_token_csrf)"
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_TIMBRADO" -c "$COOKIE_JAR_GERENTE_TIMBRADO" -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_GERENTE_TIMBRADO" > /dev/null

            # D-151: el gerente ya no puede modificar Parámetros -- sesión propia de administrador.
            restaurar_factura_numero_hasta() {
                actualizar_parametro_como_admin fiscal.factura_numero_hasta "$FACTURA_NUMERO_HASTA_ORIGINAL" > /dev/null 2>&1
            }
            trap 'restaurar_factura_numero_hasta; limpiar' EXIT

            NUEVO_FACTURA_NUMERO_HASTA="$VALOR_NUMERADOR_FACTURA_ACTUAL"
            status="$(actualizar_parametro_como_admin fiscal.factura_numero_hasta "$NUEVO_FACTURA_NUMERO_HASTA")"
            registrar "POST /parametros/actualizar-valor (factura_numero_hasta forzado al límite, gerente, Bloque 5)" 302 "$status"
            verificar_sql "SELECT: factura_numero_hasta quedó forzado de verdad (Bloque 5)" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.factura_numero_hasta' AND vigente_hasta IS NULL" \
                "$NUEVO_FACTURA_NUMERO_HASTA"

            # Caso 22: un cobro CON interés debe rechazarse por agotado, sin dejar nada -- ni pago,
            # ni movimiento de caja, ni número de factura gastado.
            # D-144 (pedido de Joel): sin esto, "Cobrar" se rechazaría antes por no tener nada
            # vencido (el préstamo se desembolsó hoy), nunca llegaría a validar el timbrado.
            vencer_cuota_hoy "$ID_PRESTAMO_TIMBRADO" 1
            DIAS_DESPLAZADOS_TIMBRADO="$ULTIMO_DIAS_DESPLAZADOS"
            GAP_TEORICA_REAL_TIMBRADO="$ULTIMO_GAP_TEORICA_REAL"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO" > /dev/null
            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
            ID_CONF_TIMBRADO_AGOTADO="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
            TOTAL_CUOTA1_TIMBRADO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT total_programado FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_TIMBRADO AND pp.vigente=true AND c.numero_cuota=1" \
                2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO/cobrar" \
                --data-urlencode "montoRecibido=$TOTAL_CUOTA1_TIMBRADO" --data-urlencode "identificadorConfirmacion=$ID_CONF_TIMBRADO_AGOTADO" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/cobrar (timbrado agotado, rechazado, Bloque 5)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO" > /dev/null
            verificar_texto "GET /cobranza/{id} (mensaje exacto de timbrado agotado, Bloque 5)" \
                "Timbrado agotado: se alcanzó el último número autorizado (hasta $NUEVO_FACTURA_NUMERO_HASTA). Solicite un nuevo timbrado."
            verificar_sql "SELECT: el cobro rechazado por agotado no dejó ningún pago (Bloque 5)" \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_TIMBRADO" "0"
            verificar_sql "SELECT: el numerador de FACTURA no se movió (agotado, Bloque 5)" \
                "SELECT ultimo_valor FROM numeradores WHERE clave='FACTURA' AND anio=$ANIO_TIMBRADO_ACTUAL" "$VALOR_NUMERADOR_FACTURA_ACTUAL"

            # Caso 23: con el mismo timbrado agotado, un anticipo sin conceptos gravados sigue
            # funcionando (el mismo día del desembolso, interés en curso = 0, D-88) -- nunca llega
            # a validar el timbrado, porque correspondeEmitirFactura da falso. D-144 (pedido de
            # Joel): el Caso 22 de arriba venció la cuota 1 a propósito (y el cobro rechazado no
            # pagó nada, así que sigue vencida) -- "Adelantar capital" ahora rechaza si hay algo
            # vencido ("Hay cuotas vencidas; cóbrelas antes de adelantar capital."), así que hay
            # que deshacer ESE MISMO desplazamiento antes de este caso, para volver exactamente a
            # "la cuota 1 todavía no vencida, mismo día del desembolso".
            deshacer_vencimiento "$ID_PRESTAMO_TIMBRADO" "$DIAS_DESPLAZADOS_TIMBRADO" 1 "$GAP_TEORICA_REAL_TIMBRADO"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO" > /dev/null
            TOKEN_CSRF_COBRANZA="$(extraer_token_csrf)"
            ID_CONF_TIMBRADO_ANTICIPO="$(extraer_identificador_confirmacion "$CUERPO_TMP" anticipar)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO/anticipar" \
                --data-urlencode "montoRecibido=100.000" --data-urlencode "identificadorConfirmacion=$ID_CONF_TIMBRADO_ANTICIPO" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COBRANZA")
            registrar "POST /cobranza/{id}/anticipar (timbrado agotado no bloquea lo sin factura, Bloque 5)" 302 "$status"
            verificar_sql "SELECT: el anticipo se aplicó igual, pese al timbrado agotado (Bloque 5)" \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_TIMBRADO AND tipo='ANTICIPO'" "1"

            # Bloque 6 (PA-13): con el timbrado ya forzado al límite (restantes=0), el aviso de
            # números de factura por agotarse tiene que aparecer solo (el umbral por defecto es
            # 500, no hace falta tocar fiscal.factura_numero_aviso) -- reaprovecha $CUERPO_TMP de
            # la última GET de la ficha (caso 23, arriba), sin pedirla de nuevo.
            verificar_texto "GET /cobranza/{id} (aviso de números de factura por agotarse, Bloque 6/PA-13)" \
                "Solicite un nuevo timbrado."
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_TIMBRADO" -c "$COOKIE_JAR_GERENTE_TIMBRADO" -X GET "$BASE_URL/panel" > /dev/null
            verificar_texto "GET /panel (aviso de números de factura por agotarse, Bloque 6/PA-13)" \
                "Solicite un nuevo timbrado."

            # Restaurar el parámetro YA (no solo al salir del script) y volver al trap normal.
            restaurar_factura_numero_hasta
            verificar_sql "SELECT: factura_numero_hasta quedó restaurado (Bloque 5)" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.factura_numero_hasta' AND vigente_hasta IS NULL" \
                "$FACTURA_NUMERO_HASTA_ORIGINAL"
            trap limpiar EXIT

            # ---------------------------------------------------------------------
            # Bloque 6 (PA-13): aviso de vigencia del timbrado por vencer -- ajuste aparte del de
            # arriba, sube fiscal.timbrado_vigencia_hasta a "hoy + 15" (dentro de los 30 días por
            # defecto de fiscal.timbrado_dias_aviso, pero todavía vigente, así que no bloquea
            # ningún cobro real de los que siguen más abajo en el script).
            # ---------------------------------------------------------------------
            TIMBRADO_VIGENCIA_HASTA_ORIGINAL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.timbrado_vigencia_hasta' AND vigente_hasta IS NULL" \
                2>/dev/null | tr -d '[:space:]')"

            # D-151: el gerente ya no puede modificar Parámetros -- sesión propia de administrador.
            restaurar_timbrado_vigencia_hasta() {
                actualizar_parametro_como_admin fiscal.timbrado_vigencia_hasta "$TIMBRADO_VIGENCIA_HASTA_ORIGINAL" > /dev/null 2>&1
            }
            trap 'restaurar_timbrado_vigencia_hasta; limpiar' EXIT

            NUEVA_TIMBRADO_VIGENCIA_HASTA="$(date -d '+15 days' +%Y-%m-%d 2>/dev/null || date -v+15d +%Y-%m-%d)"
            status="$(actualizar_parametro_como_admin fiscal.timbrado_vigencia_hasta "$NUEVA_TIMBRADO_VIGENCIA_HASTA")"
            registrar "POST /parametros/actualizar-valor (timbrado_vigencia_hasta forzado a 15 días, gerente, Bloque 6)" 302 "$status"
            verificar_sql "SELECT: timbrado_vigencia_hasta quedó forzado de verdad (Bloque 6)" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.timbrado_vigencia_hasta' AND vigente_hasta IS NULL" \
                "$NUEVA_TIMBRADO_VIGENCIA_HASTA"

            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_TIMBRADO" > /dev/null
            verificar_texto "GET /cobranza/{id} (aviso de vigencia del timbrado por vencer, Bloque 6/PA-13)" \
                "Solicite la renovación."
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_TIMBRADO" -c "$COOKIE_JAR_GERENTE_TIMBRADO" -X GET "$BASE_URL/panel" > /dev/null
            verificar_texto "GET /panel (aviso de vigencia del timbrado por vencer, Bloque 6/PA-13)" \
                "Solicite la renovación."

            restaurar_timbrado_vigencia_hasta
            verificar_sql "SELECT: timbrado_vigencia_hasta quedó restaurado (Bloque 6)" \
                "SELECT valor_parametro FROM parametros_sistema WHERE clave_parametro='fiscal.timbrado_vigencia_hasta' AND vigente_hasta IS NULL" \
                "$TIMBRADO_VIGENCIA_HASTA_ORIGINAL"
            trap limpiar EXIT
            curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_TIMBRADO" -c "$COOKIE_JAR_GERENTE_TIMBRADO" -X GET "$BASE_URL/salir" > /dev/null

            # ---------------------------------------------------------------------
            # Bloque 2 (E8, D-94): reimpresión de los 5 comprobantes. Reutiliza
            # préstamos ya generados arriba en este mismo Bloque 5 (ID_PRESTAMO_COBRANZA
            # ya quedó FINALIZADO por cancelación total, con un recibo NORMAL con
            # factura y un recibo CANCELACION con constancia; ID_PRESTAMO_MORA ya
            # tiene un desembolso CONFIRMADO). Mismo patrón que el chequeo de PDF de
            # /documentos/{id} (D-70) de más arriba: código 200 + Content-Type.
            # ---------------------------------------------------------------------
            verificar_pdf_e8() {
                local descripcion="$1" url="$2" status tipoContenido
                status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$url")
                registrar "GET $descripcion (E8, 200)" 200 "$status"
                tipoContenido="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
                registrar "GET $descripcion (E8, tipo application/pdf)" "presente" \
                    "$(echo "$tipoContenido" | grep -qi 'application/pdf' && echo presente || echo ausente)"
            }

            # D-94: la ficha de un préstamo FINALIZADO ya NO da 404 (cambio de Bloque 2 sobre el
            # criterio D-89) -- sigue existiendo en modo solo lectura, para reimprimir su historial.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA")
            registrar "GET /cobranza/{id} (préstamo FINALIZADO, ya NO 404, E8/Bloque 2)" 200 "$status"
            # D-146 (E-4, pedido de Joel): el texto cambió a "Préstamo cancelado/finalizado el ...
            # -- sin deuda pendiente." -- "sin deuda pendiente." es la parte común a las dos ramas
            # (con o sin fecha de cierre).
            verificar_texto "GET /cobranza/{id} (préstamo FINALIZADO, aviso de solo lectura, E8/Bloque 2)" \
                "sin deuda pendiente."
            verificar_texto_ausente "GET /cobranza/{id} (préstamo FINALIZADO, sin formulario de cobro, E8/Bloque 2)" \
                "/cobranza/$ID_PRESTAMO_COBRANZA/cobrar\""
            verificar_texto_ausente "GET /cobranza/{id} (préstamo FINALIZADO, sin formulario de cancelación, E8/Bloque 2)" \
                "/cobranza/$ID_PRESTAMO_COBRANZA/cancelar\""
            verificar_texto_ausente "GET /cobranza/{id} (préstamo FINALIZADO, sin formulario de anticipo, E8/Bloque 2)" \
                "/cobranza/$ID_PRESTAMO_COBRANZA/anticipar\""

            # Pedido de Joel: el servidor también rechaza un POST forzado a mano sobre un préstamo
            # FINALIZADO (no solo que la ficha no muestre el formulario) -- confirmarCobro/
            # confirmarCancelacionTotal/confirmarAnticipoCapital ya validan ACTIVO/EN_MORA desde E7
            # (D-89), esto lo prueba explícitamente contra el préstamo recién finalizado de este
            # mismo Bloque 5, sin dejar ningún pago nuevo.
            CANTIDAD_PAGOS_ANTES_RECHAZO_FINALIZADO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA" 2>/dev/null | tr -d '[:space:]')"
            # La ficha en solo lectura no trae ningún formulario (ni csrf.jsp): el token se saca de
            # /caja (siempre tiene al menos un formulario), no de la ficha del préstamo FINALIZADO.
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_RECHAZO_FINALIZADO="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cobrar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "identificadorConfirmacion=finalizado-cobrar-$RANDOM" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_RECHAZO_FINALIZADO")
            registrar "POST /cobranza/{id}/cobrar (préstamo FINALIZADO, rechazado, E8/Bloque 2)" 302 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/cancelar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "identificadorConfirmacion=finalizado-cancelar-$RANDOM" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_RECHAZO_FINALIZADO")
            registrar "POST /cobranza/{id}/cancelar (préstamo FINALIZADO, rechazado, E8/Bloque 2)" 302 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/anticipar" \
                --data-urlencode "montoRecibido=1.000" --data-urlencode "identificadorConfirmacion=finalizado-anticipar-$RANDOM" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_RECHAZO_FINALIZADO")
            registrar "POST /cobranza/{id}/anticipar (préstamo FINALIZADO, rechazado, E8/Bloque 2)" 302 "$status"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA" > /dev/null
            verificar_texto "GET /cobranza/{id} (préstamo FINALIZADO, mensaje exacto del rechazo, E8/Bloque 2)" \
                "Este préstamo no está activo."
            verificar_sql "SELECT: los 3 POST rechazados (préstamo FINALIZADO) no dejaron ningún pago nuevo, E8/Bloque 2" \
                "SELECT count(*) FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA" "$CANTIDAD_PAGOS_ANTES_RECHAZO_FINALIZADO"

            ID_PAGO_NORMAL_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA AND tipo='NORMAL' ORDER BY id_pago LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
            ID_FACTURA_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_factura FROM facturas WHERE id_pago=$ID_PAGO_NORMAL_COBRANZA" 2>/dev/null | tr -d '[:space:]')"
            ID_PAGO_CANCELACION_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_COBRANZA AND tipo='CANCELACION' ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
            ID_LIQUIDACION_COBRANZA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_liquidacion FROM liquidaciones_anticipadas WHERE id_prestamo=$ID_PRESTAMO_COBRANZA AND tipo_liquidacion='TOTAL' ORDER BY id_liquidacion DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"

            verificar_pdf_e8 "/cobranza/{id}/recibo/{idPago} (recibo NORMAL con factura, E8/Bloque 2)" \
                "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/recibo/$ID_PAGO_NORMAL_COBRANZA"
            verificar_pdf_e8 "/cobranza/{id}/factura/{idFactura} (E8/Bloque 2)" \
                "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/factura/$ID_FACTURA_COBRANZA"
            verificar_pdf_e8 "/cobranza/{id}/recibo/{idPago} (recibo CANCELACION, E8/Bloque 2)" \
                "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/recibo/$ID_PAGO_CANCELACION_COBRANZA"
            verificar_pdf_e8 "/cobranza/{id}/constancia/{idLiquidacion} (E8/Bloque 2)" \
                "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/constancia/$ID_LIQUIDACION_COBRANZA"

            # Reimpresión: pedir el mismo recibo una segunda vez entrega el PDF ya guardado (D-94,
            # "generar al primer pedido y guardar") -- no crea un segundo archivo_binario.
            CANTIDAD_ARCHIVOS_RECIBO_ANTES_E8="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_archivo_recibo FROM pagos WHERE id_pago=$ID_PAGO_NORMAL_COBRANZA" 2>/dev/null | tr -d '[:space:]')"
            verificar_pdf_e8 "/cobranza/{id}/recibo/{idPago} (segunda vez, reimpresión, E8/Bloque 2)" \
                "$BASE_URL/cobranza/$ID_PRESTAMO_COBRANZA/recibo/$ID_PAGO_NORMAL_COBRANZA"
            verificar_sql "SELECT: la reimpresión del recibo entregó el MISMO archivo guardado (D-94, E8/Bloque 2)" \
                "SELECT (id_archivo_recibo = $CANTIDAD_ARCHIVOS_RECIBO_ANTES_E8) FROM pagos WHERE id_pago=$ID_PAGO_NORMAL_COBRANZA" "t"

            # Comprobante de desembolso (D-94): ID_PRESTAMO_MORA ya tiene un desembolso CONFIRMADO
            # (desembolsar_prestamo_e7, más arriba).
            verificar_pdf_e8 "/desembolso/{id}/comprobante (E8/Bloque 2)" \
                "$BASE_URL/desembolso/$ID_PRESTAMO_MORA/comprobante"

            # D-95 (gestiones_cobranza.id_pago): préstamo dedicado con DOS pagos PARCIALES con
            # promesa, cada uno con su PROPIA fecha -- Joel pidió un JUnit "de base" para este caso;
            # se decidió en su lugar probarlo acá (D-95, AVANCE.md) porque el vínculo es
            # intrínsecamente de base de datos y la convención del proyecto es que JUnit nunca la
            # toca. Verifica que cada gestión quedó con su propio id_pago, nunca cruzados.
            preparar_prestamo_e7 "11" "900.000" "3" "d95-promesas"
            ID_PRESTAMO_D95="$PRESTAMO_PREPARADO_ID"
            desembolsar_prestamo_e7 "$ID_PRESTAMO_D95" "d95-promesas"

            # Corrección encontrada al correr este caso por primera vez: confirmarCobro solo trata
            # un pago como PARCIAL (y solo ahí inserta la gestión de cobranza) cuando el monto es
            # MENOR que la deuda EXIGIBLE (lo ya vencido) -- un préstamo recién desembolsado no
            # tiene nada vencido todavía, así que cualquier monto contra la próxima cuota (ni
            # siquiera vencida) se procesaba como cobro NORMAL, sin gestión. Mismo desplazamiento de
            # -70 días que el caso de mora (Bloque 5, más arriba) para que la cuota 1 quede
            # realmente vencida antes de los dos pagos parciales.
            ID_PLAN_D95="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_plan_pago FROM planes_pago WHERE id_prestamo=$ID_PRESTAMO_D95 AND vigente=true" 2>/dev/null | tr -d '[:space:]')"
            COLISIONES_FERIADOS_D95="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM cuotas c JOIN feriados f ON f.fecha = (c.fecha_vencimiento - INTERVAL '70 days')::date AND f.activo WHERE c.id_plan_pago=$ID_PLAN_D95" \
                2>/dev/null | tr -d '[:space:]')"
            if [ "$COLISIONES_FERIADOS_D95" != "0" ]; then
                echo "== Caso D-95 (Bloque 2) OMITIDO: el desplazamiento de -70 días chocaría con un feriado activo. ==" >&2
            else
                PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q > /dev/null <<SQL
BEGIN;
UPDATE prestamos SET fecha_desembolso = fecha_desembolso - INTERVAL '70 days',
    fecha_formalizacion = fecha_formalizacion - INTERVAL '70 days',
    primera_fecha_vencimiento = primera_fecha_vencimiento - INTERVAL '70 days'
    WHERE id_prestamo = $ID_PRESTAMO_D95;
UPDATE planes_pago SET fecha_generacion = fecha_generacion - INTERVAL '70 days'
    WHERE id_plan_pago = $ID_PLAN_D95;
UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento - INTERVAL '70 days',
    fecha_vencimiento_teorica = fecha_vencimiento_teorica - INTERVAL '70 days'
    WHERE id_plan_pago = $ID_PLAN_D95;
COMMIT;
SQL
                if [ "$?" != "0" ]; then
                    echo "== Caso D-95 (Bloque 2) ABORTADO: el UPDATE de fechas falló (revisar el mensaje de psql de arriba). ==" >&2
                else
                    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_D95" > /dev/null
                    verificar_sql "SELECT: D-95 -- hay cuotas vencidas en el préstamo antes de los dos parciales" \
                        "SELECT (count(*) > 0) FROM cuotas WHERE id_plan_pago=$ID_PLAN_D95 AND estado_pago != 'PAGADA' AND fecha_vencimiento < CURRENT_DATE" "t"
                    TOKEN_CSRF_D95="$(extraer_token_csrf)"
                    ID_CONF_D95_1="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                    FECHA_PROMESA_D95_1="$(date -d '+3 days' +%Y-%m-%d 2>/dev/null || date -v+3d +%Y-%m-%d)"
                    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_D95/cobrar" \
                        --data-urlencode "montoRecibido=50.000" --data-urlencode "fechaPromesa=$FECHA_PROMESA_D95_1" \
                        --data-urlencode "identificadorConfirmacion=$ID_CONF_D95_1" --data-urlencode "tokenCsrf=$TOKEN_CSRF_D95")
                    registrar "POST /cobranza/{id}/cobrar (D-95, primer parcial con promesa)" 302 "$status"
                    ID_PAGO_D95_1="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                        "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_D95 ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"

                    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_D95" > /dev/null
                    TOKEN_CSRF_D95="$(extraer_token_csrf)"
                    ID_CONF_D95_2="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
                    FECHA_PROMESA_D95_2="$(date -d '+7 days' +%Y-%m-%d 2>/dev/null || date -v+7d +%Y-%m-%d)"
                    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_D95/cobrar" \
                        --data-urlencode "montoRecibido=50.000" --data-urlencode "fechaPromesa=$FECHA_PROMESA_D95_2" \
                        --data-urlencode "identificadorConfirmacion=$ID_CONF_D95_2" --data-urlencode "tokenCsrf=$TOKEN_CSRF_D95")
                    registrar "POST /cobranza/{id}/cobrar (D-95, segundo parcial con OTRA promesa)" 302 "$status"
                    ID_PAGO_D95_2="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                        "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_D95 ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"

                    verificar_sql "SELECT: D-95 -- los dos pagos parciales quedaron cada uno con su propia gestión" \
                        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_D95 AND id_pago IN ($ID_PAGO_D95_1,$ID_PAGO_D95_2)" "2"
                    verificar_sql "SELECT: D-95 -- ninguna gestión quedó sin vincular (id_pago NULL) para este préstamo" \
                        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_D95 AND id_pago IS NULL" "0"
                    verificar_sql "SELECT: D-95 -- las dos gestiones apuntan a pagos DISTINTOS (nunca cruzadas)" \
                        "SELECT (SELECT id_pago FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_D95 AND fecha_promesa='$FECHA_PROMESA_D95_1') <> (SELECT id_pago FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_D95 AND fecha_promesa='$FECHA_PROMESA_D95_2')" "t"
                    verificar_pdf_e8 "/cobranza/{id}/recibo/{idPago} (D-95, primer parcial con promesa)" \
                        "$BASE_URL/cobranza/$ID_PRESTAMO_D95/recibo/$ID_PAGO_D95_1"
                    verificar_pdf_e8 "/cobranza/{id}/recibo/{idPago} (D-95, segundo parcial con OTRA promesa)" \
                        "$BASE_URL/cobranza/$ID_PRESTAMO_D95/recibo/$ID_PAGO_D95_2"
                fi
            fi

            # --- Cerrar la caja de Bloque 5 antes de terminar (D-75: nunca dejar una caja abierta). ---
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
            TOKEN_CSRF_CAJA_E7="$(extraer_token_csrf)"
            ID_SESION_CAJA_BLOQUE5="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT id_sesion_caja FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
            SALDO_TEORICO_CIERRE_E7="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT s.saldo_inicial + COALESCE((SELECT SUM(m.monto*m.signo) FROM movimientos_caja m WHERE m.id_sesion_caja=s.id_sesion_caja),0) FROM sesiones_caja s WHERE s.estado_sesion='ABIERTA'" \
                2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
                --data-urlencode "saldoFisico=$SALDO_TEORICO_CIERRE_E7" --data-urlencode "motivoDiferencia=" --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJA_E7")
            registrar "POST /caja/cerrar (cierre final de Bloque 5)" 302 "$status"
            verificar_sql "SELECT: no queda ninguna caja ABIERTA al terminar Bloque 5" \
                "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "0"

            # E8 (D-94, Bloque 2): arqueo de la sesión recién cerrada -- necesita CERRADA (usa
            # fecha_hora_cierre), por eso se pide después del POST /caja/cerrar de arriba.
            #
            # Diagnóstico pedido por Joel (falla intermitente detectada hoy, no reproducible a
            # mano de forma aislada): si esta pasada no da 200, dejar constancia de a dónde
            # redirigió (Location), con qué idSesion se pidió y quién estaba logueado en ese
            # momento -- para distinguir sesión de usuario vencida, permiso insuficiente o un
            # idSesion equivocado.
            ARQUEO_E7_STATUS=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja/$ID_SESION_CAJA_BLOQUE5/arqueo")
            registrar "GET /caja/{idSesion}/arqueo (E8/Bloque 2, 200)" 200 "$ARQUEO_E7_STATUS"
            ARQUEO_E7_TIPO_CONTENIDO="$(grep -i '^content-type:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
            registrar "GET /caja/{idSesion}/arqueo (E8/Bloque 2, tipo application/pdf)" "presente" \
                "$(echo "$ARQUEO_E7_TIPO_CONTENIDO" | grep -qi 'application/pdf' && echo presente || echo ausente)"
            if [ "$ARQUEO_E7_STATUS" != "200" ]; then
                ARQUEO_E7_LOCATION="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | tr -d '\r')"
                curl -s -o "$CUERPO_TMP" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
                ARQUEO_E7_USUARIO="$(grep -o 'pie-usuario-nombre">[^<]*' "$CUERPO_TMP" | head -1 | sed 's/.*>//')"
                ARQUEO_E7_MENSAJE="$(grep -A1 'alert-danger' "$CUERPO_TMP" | tail -1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
                echo "== DIAGNÓSTICO arqueo E8: status=$ARQUEO_E7_STATUS idSesion=[$ID_SESION_CAJA_BLOQUE5] $ARQUEO_E7_LOCATION usuario=[$ARQUEO_E7_USUARIO] mensaje=[$ARQUEO_E7_MENSAJE] ==" >&2
            fi

            fi
        else
            echo
            echo "== Bloque 5 (Cobranza) omitido: requiere SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba. ==" >&2
        fi

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (cajera)" 302 "$status"
    fi
else
    echo
    echo "== Pasada de la cajera omitida: defina SGCPC_USUARIO_CAJERA y SGCPC_CONTRASENA_CAJERA en ~/.sgcpc_prueba para incluirla. ==" >&2
fi

# ---------------------------------------------------------------------
# Bloque 4 (E8): seguimiento de mora, panel y reportes (RF-37/RF-43/§13).
#    Permisos de /mora y /reportes por rol, más lo principal de cada
#    pantalla. Usa un préstamo EN_MORA propio (2 cuotas vencidas) -- hasta
#    el D-113 se reusaba CC-2026-90002, un fixture insertado una sola vez
#    por SQL directo en sgcpc_db (D-101); al separar el humo a su propia
#    base (sgcpc_db_pruebas), ese préstamo no existe ahí, y esta sección
#    entera se saltaba en silencio (61 verificaciones, encontrado por
#    Joel comparando 644 vs 583 verificaciones totales entre las dos
#    bases -- "si alguna se saltea en silencio, tiene que fallar o crear
#    sus propios datos, nunca pasar sin probar nada"). D-113 lo resolvió
#    creando el fixture si no encontraba uno existente, pero reusándolo
#    si sí -- con el cargador de 7.4 (D-115) dejando cargado su propio
#    préstamo EN_MORA (E3), ese "si existe, lo reusa" pasó a dispararse
#    siempre, y el total del humo (644 en vez de 673) terminó dependiendo
#    de si el cargador ya había corrido antes o no (D-118, AVANCE.md).
#    D-119 (pedido explícito de Joel): este bloque SIEMPRE crea su propio
#    préstamo -- nunca reusa uno existente, así el total del humo es
#    fijo, sin importar el orden de las corridas -- y por lo mismo nunca
#    toca (ni lee para decidir) los préstamos de los 11 escenarios de
#    demostración. Por el flujo real (solicitud, aprobación,
#    formalización, desembolso, todo por HTTP, para que
#    cálculos/numeración/auditoría salgan iguales a un caso real) y
#    después atrasa por SQL las fechas de ESE préstamo (nunca las de
#    otro) para que 2 cuotas ya estén vencidas sin esperar 2 meses de
#    calendario real. Sección propia, con su propio login por rol (mismo
#    patrón que Bloque 5/D-89/D-91), así no depende de en qué estado haya
#    quedado $COOKIE_JAR más arriba. Cédulas con prefijo "9" (D-52): nunca
#    coinciden con las cédulas 800000001-800000012 del cargador de 7.4.
# ---------------------------------------------------------------------
echo
echo "== Bloque 4 (E8): mora, panel y reportes =="
echo "== Se crea siempre un préstamo EN_MORA propio para este bloque (D-119: nunca reusa ni toca los escenarios de demostración). =="

SESIONES_CAJA_ABIERTAS_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
if [ "$SESIONES_CAJA_ABIERTAS_B4" != "0" ]; then
    echo "== Bloque 4 (E8) ABORTADO: ya hay una caja ABIERTA que esta corrida no abrió (posible prueba manual de Joel en curso). No se toca. ==" >&2
else

    COOKIE_JAR_OFICIAL_B4="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_B4" -c "$COOKIE_JAR_OFICIAL_B4" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_OFICIAL_B4="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_B4" -c "$COOKIE_JAR_OFICIAL_B4" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_B4" > /dev/null

    CEDULA_CLIENTE_B4="${CEDULA_PRUEBA}18"
    COOKIE_JAR_ANTERIOR_B4="$COOKIE_JAR"
    COOKIE_JAR="$COOKIE_JAR_OFICIAL_B4"
    crear_persona_con_expediente "$CEDULA_CLIENTE_B4" "Cliente" "Prueba Humo E8 mora" "3.000.000" "$TOKEN_OFICIAL_B4"
    ID_CLIENTE_90002_B4="$ID_PERSONA_CREADA"
    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_B4"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_B4" -c "$COOKIE_JAR_OFICIAL_B4" \
        -X POST "$BASE_URL/solicitudes/nuevo" \
        --data-urlencode "cedulaCliente=$CEDULA_CLIENTE_B4" --data-urlencode "monto=3.000.000" \
        --data-urlencode "plazoMeses=12" --data-urlencode "destinoCredito=Prueba de humo E8 (mora)" \
        --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_B4")
    registrar "POST /solicitudes/nuevo (E8, mora)" 302 "$status"
    UBICACION_B4="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_SOLICITUD_B4="$(echo "$UBICACION_B4" | grep -o '[0-9]*$')"
    [ -z "$ID_SOLICITUD_B4" ] && ID_SOLICITUD_B4="0"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_B4" -c "$COOKIE_JAR_OFICIAL_B4" \
        -X POST "$BASE_URL/solicitudes/$ID_SOLICITUD_B4/enviar" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_B4")
    registrar "POST /solicitudes/{id}/enviar (E8, mora)" 302 "$status"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_B4" -c "$COOKIE_JAR_OFICIAL_B4" -X GET "$BASE_URL/salir" > /dev/null

    COOKIE_JAR_GERENTE_B4="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_GERENTE_B4="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
        --data-urlencode "tokenCsrf=$TOKEN_GERENTE_B4" > /dev/null

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" \
        -X POST "$BASE_URL/aprobaciones/$ID_SOLICITUD_B4/aprobar" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_B4")
    registrar "POST /aprobaciones/{id}/aprobar (E8, mora)" 302 "$status"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" \
        -X POST "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_B4/generar" \
        --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_B4")
    registrar "POST /formalizacion/nueva/{id}/generar (E8, mora)" 302 "$status"
    UBICACION_B4="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_PRESTAMO_90002_B4="$(echo "$UBICACION_B4" | grep -o '[0-9]*$')"
    [ -z "$ID_PRESTAMO_90002_B4" ] && ID_PRESTAMO_90002_B4="0"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_GERENTE_B4" -F "idPrestamo=$ID_PRESTAMO_90002_B4" -F "idTipoDocumento=$ID_TIPO_CONTRATO_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=contrato_firmado_e8.pdf")
    registrar "POST /documentos/cargar (contrato firmado, E8, mora)" 302 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_GERENTE_B4" -F "idPrestamo=$ID_PRESTAMO_90002_B4" -F "idTipoDocumento=$ID_TIPO_PAGARE_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=pagare_firmado_e8.pdf")
    registrar "POST /documentos/cargar (pagaré firmado, E8, mora)" 302 "$status"
    # PA-21 (E10_14, hallazgo de Joel, 12/10/2026): tercer documento obligatorio del checklist --
    # este préstamo es el que queda EN_MORA para probar /mora (D-119); sin esto nunca se
    # desembolsa y nunca llega a vencer ninguna cuota.
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X POST "$BASE_URL/documentos/cargar" \
        -F "tokenCsrf=$TOKEN_GERENTE_B4" -F "idPrestamo=$ID_PRESTAMO_90002_B4" -F "idTipoDocumento=$ID_TIPO_PLAN_PAGOS_FIRMADO" \
        -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=plan_pagos_firmado_e8.pdf")
    registrar "POST /documentos/cargar (plan de pagos firmado, E8, mora, PA-21)" 302 "$status"

    # --- Desembolso: la cajera necesita la caja abierta y fondeada con exactamente el monto
    # aprobado (D-74, mismo criterio que el resto del humo). ---
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X GET "$BASE_URL/caja" > /dev/null
    COOKIE_JAR_CAJERA_B4="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CAJERA_B4="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_CAJERA" --data-urlencode "contrasena=$SGCPC_CONTRASENA_CAJERA" \
        --data-urlencode "tokenCsrf=$TOKEN_CAJERA_B4" > /dev/null
    curl -s -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -o "$CUERPO_TMP" -X GET "$BASE_URL/caja" > /dev/null
    TOKEN_CAJERA_B4="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X POST "$BASE_URL/caja/abrir" \
        --data-urlencode "saldoInicial=0" --data-urlencode "tokenCsrf=$TOKEN_CAJERA_B4")
    registrar "POST /caja/abrir (E8, mora)" 302 "$status"

    curl -s -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -o "$CUERPO_TMP" -X GET "$BASE_URL/caja" > /dev/null
    TOKEN_GERENTE_B4="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X POST "$BASE_URL/caja/entregar" \
        --data-urlencode "monto=3.000.000" --data-urlencode "observacion=Prueba de humo: fondo para E8 mora." \
        --data-urlencode "tokenCsrf=$TOKEN_GERENTE_B4")
    registrar "POST /caja/entregar (E8, mora)" 302 "$status"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X GET "$BASE_URL/desembolso/$ID_PRESTAMO_90002_B4" > /dev/null
    ID_CONF_DESEMBOLSO_B4="$(grep -o 'name="identificadorConfirmacion" value="[^"]*"' "$CUERPO_TMP" | head -1 | sed 's/.*value="//;s/"$//')"
    TOKEN_CAJERA_B4="$(extraer_token_csrf)"
    if verificar_listo_para_desembolsar "$ID_PRESTAMO_90002_B4" "$TOKEN_CAJERA_B4" "Confirmar desembolso E8, mora"; then
        # D-130: hay que imprimir el comprobante para firma antes de poder confirmar.
        imprimir_comprobante_desembolso "$COOKIE_JAR_CAJERA_B4" "$ID_PRESTAMO_90002_B4" "E8, mora"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X POST "$BASE_URL/desembolso/$ID_PRESTAMO_90002_B4/confirmar" \
            --data-urlencode "tokenCsrf=$TOKEN_CAJERA_B4" --data-urlencode "identificadorConfirmacion=$ID_CONF_DESEMBOLSO_B4" \
            --data-urlencode "confirmoEntregaEfectivo=true")
        registrar "POST /desembolso/{id}/confirmar (E8, mora)" 302 "$status"
        verificar_sql "SELECT: préstamo (E8, mora) quedó ACTIVO tras el desembolso" \
            "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_90002_B4" "ACTIVO"
    fi

    SALDO_TEORICO_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT s.saldo_inicial + COALESCE((SELECT SUM(m.monto*m.signo) FROM movimientos_caja m WHERE m.id_sesion_caja=s.id_sesion_caja),0) FROM sesiones_caja s WHERE s.estado_sesion='ABIERTA'" \
        2>/dev/null | tr -d '[:space:]')"
    curl -s -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -o "$CUERPO_TMP" -X GET "$BASE_URL/caja" > /dev/null
    TOKEN_CAJERA_B4="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X POST "$BASE_URL/caja/cerrar" \
        --data-urlencode "saldoFisico=$SALDO_TEORICO_B4" --data-urlencode "motivoDiferencia=" --data-urlencode "tokenCsrf=$TOKEN_CAJERA_B4")
    registrar "POST /caja/cerrar (E8, mora)" 302 "$status"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_B4" -c "$COOKIE_JAR_GERENTE_B4" -X GET "$BASE_URL/salir" > /dev/null

    # --- Atrasar por SQL, SOLO este préstamo, para que 2 cuotas ya estén vencidas (RF24) sin
    # esperar 2 meses de calendario real -- el mismo desplazamiento se aplica al préstamo y a
    # TODAS sus cuotas, conservando el espaciado real que ya calculó el motor. ---
    DIAS_HASTA_CUOTA2_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT (c.fecha_vencimiento - CURRENT_DATE) FROM cuotas c JOIN planes_pago pl ON pl.id_plan_pago=c.id_plan_pago \
         WHERE pl.id_prestamo=$ID_PRESTAMO_90002_B4 AND pl.vigente=true AND c.numero_cuota=2" 2>/dev/null | tr -d '[:space:]')"
    DESPLAZAMIENTO_DIAS_B4=$((DIAS_HASTA_CUOTA2_B4 + 5))
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c "
        UPDATE prestamos SET fecha_desembolso = fecha_desembolso - INTERVAL '$DESPLAZAMIENTO_DIAS_B4 days',
               fecha_formalizacion = fecha_formalizacion - INTERVAL '$DESPLAZAMIENTO_DIAS_B4 days',
               primera_fecha_vencimiento = primera_fecha_vencimiento - INTERVAL '$DESPLAZAMIENTO_DIAS_B4 days'
        WHERE id_prestamo=$ID_PRESTAMO_90002_B4;
        UPDATE cuotas SET fecha_vencimiento = fecha_vencimiento - INTERVAL '$DESPLAZAMIENTO_DIAS_B4 days',
               fecha_vencimiento_teorica = fecha_vencimiento_teorica - INTERVAL '$DESPLAZAMIENTO_DIAS_B4 days'
        WHERE id_plan_pago IN (SELECT id_plan_pago FROM planes_pago WHERE id_prestamo=$ID_PRESTAMO_90002_B4);
        " > /dev/null 2>&1

    # --- Disparar el recálculo de mora DE ESTE préstamo -- NO por /panel/mora (ServicioMora.
    # recalcularMorosidadDiaria tiene una guarda "una sola vez por día", ServicioAuditoria.
    # yaSeRegistroHoy, así que si el humo ya pasó por /panel o /mora antes en esta misma corrida
    # -- como ya pasa en E6/E7, mucho antes de este bloque -- un segundo GET /panel no vuelve a
    # recorrer los préstamos y este, recién atrasado, nunca se toca; encontrado corriendo el humo
    # con la primera versión de este fixture, D-113). ControladorCobranzaServlet.mostrarFicha
    # llama a ServicioCobranza.recalcularEstadoPrestamo DIRECTO por cada pedido, sin esa guarda
    # -- alcanza con pedir la ficha de cobranza de este préstamo puntual. ---
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" \
        -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_90002_B4" > /dev/null
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_B4" -c "$COOKIE_JAR_CAJERA_B4" -X GET "$BASE_URL/salir" > /dev/null

    verificar_sql "SELECT: préstamo de prueba (E8, mora) quedó EN_MORA tras el atraso y el recálculo" \
        "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_90002_B4" "EN_MORA"
    verificar_sql "SELECT: 2 cuotas del préstamo de prueba (E8, mora) quedaron vencidas" \
        "SELECT count(*) FROM cuotas c JOIN planes_pago pl ON pl.id_plan_pago=c.id_plan_pago \
         WHERE pl.id_prestamo=$ID_PRESTAMO_90002_B4 AND pl.vigente=true AND c.estado_pago <> 'PAGADA' AND c.fecha_vencimiento < CURRENT_DATE" "2"

OPERACION_90002_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_90002_B4" 2>/dev/null | tr -d '[:space:]')"

if [ -z "$ID_PRESTAMO_90002_B4" ] || [ "$ID_PRESTAMO_90002_B4" = "0" ]; then
    echo "== Bloque 4 (E8) OMITIDO por completo: no se pudo crear el préstamo de prueba EN_MORA. ==" >&2
else
    # --- Gerente: TOTAL en /mora y en los 10 reportes ---
    COOKIE_JAR="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_B4="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4")
    registrar "POST /acceso (login gerente, Bloque 4)" 302 "$status"

    verificar GET /mora 200
    # B197 (15/11/2026, aprobado por Joel): /mora pagina de a 20 y ordena por PRIORIDAD DE TRABAJO
    # (promesa vencida primero, despues por dias de atraso descendente), asi que el prestamo que
    # arma este bloque -- con la cuota 2 atrasada 5 dias, o sea la cuota 1 con unos 35, y sin
    # promesa -- queda alrededor del puesto 47 de 69: pagina 3. Se pide el orden por operacion
    # descendente, que lo trae a la pagina 1 porque su N.o es el mas alto de todos, y de paso
    # ejercita el orden nuevo.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X GET "$BASE_URL/mora?orden=operacion&sentido=desc" > /dev/null
    verificar_texto "GET /mora?orden=operacion ($OPERACION_90002_B4 en la lista, Bloque 4)" "$OPERACION_90002_B4"

    # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): buscador
    # AJAX de /mora -- mismo patrón que el caso de Clientes (paso 0): pedido con el encabezado
    # X-Requested-With para recibir el fragmento (_resultados.jsp) directo, igual que
    # recursos/js/listado-vivo.js.
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/mora?texto=$OPERACION_90002_B4" > /dev/null
    verificar_texto "GET /mora?texto= (filtro de mora encuentra por número de operación, Bloque 4)" "$OPERACION_90002_B4"

    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H "X-Requested-With: XMLHttpRequest" \
        -X GET "$BASE_URL/mora?texto=TextoQueNoExisteEnNingunaMora" > /dev/null
    verificar_texto "GET /mora?texto= (filtro de mora sin resultados, mensaje correcto, Bloque 4)" \
        "No se encontraron préstamos en mora para esa búsqueda."
    verificar_texto_ausente "GET /mora?texto= (filtro de mora sin resultados, $OPERACION_90002_B4 ausente, Bloque 4)" \
        "$OPERACION_90002_B4"

    # Las 2 verificaciones de arriba dejan $CUERPO_TMP con el fragmento AJAX filtrado (el último,
    # "sin resultados", sin ningún <form> adentro) -- hay que restaurar una página COMPLETA antes
    # de seguir, porque extraer_token_csrf/extraer_token_gestion de más abajo leen de ese mismo
    # archivo (si no, el POST real de gestión que sigue falla con 403 por falta de tokenCsrf).
    # B197 (15/11/2026, aprobado por Joel): esta página se pide FILTRADA por el N.o de operación de
    # este préstamo, y no pelada como antes. Con /mora paginado de a 20, el préstamo de la corrida
    # cae fuera de la primera página (medido el 15/11: puesto 47 de 71, página 3) y entonces su
    # <form> no está en el HTML: extraer_token_gestion devuelve VACIO y el POST de abajo se rechaza
    # como si el token fuera inventado -- eso tiró 4 fallas en la corrida de esa tarde. Con el
    # filtro queda un solo resultado, siempre en la página 1, sin depender de ningún orden; y la
    # página completa sigue trayendo el tokenCsrf de la cabecera, que es el otro motivo por el que
    # este pedido existe.
    verificar GET "/mora?texto=$OPERACION_90002_B4" 200

    verificar_sql "SELECT: el recálculo diario de mora quedó marcado hoy (decisión 5, Bloque 4)" \
        "SELECT (count(*) > 0) FROM auditoria_operaciones WHERE operacion='RECALCULO_MORA_DIARIO' AND fecha_hora::date = CURRENT_DATE" "t"
    # Tanda de usabilidad (21/10/2026, punto 8 paso 2, PA-28): RECALCULO_MORA_DIARIO es del sistema
    # (tipo_entidad='SISTEMA') -- sigue sin IP, PA-28 no le agrega una.
    verificar_sql "SELECT: el recálculo diario de mora sigue sin IP (operación del sistema, PA-28)" \
        "SELECT (count(*) = 0) FROM auditoria_operaciones WHERE operacion='RECALCULO_MORA_DIARIO' AND fecha_hora::date = CURRENT_DATE AND direccion_ip IS NOT NULL" "t"
    verificar_sql "SELECT: $OPERACION_90002_B4 pasó a EN_MORA tras el recálculo (Bloque 4)" \
        "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_90002_B4" "EN_MORA"

    # Cuenta ANTES de postear (no asumir "0 previas": Joel puede haber probado a mano en el
    # navegador sobre este mismo préstamo, como pasó en el cierre del Bloque 4) -- se compara la
    # diferencia, no un total absoluto.
    GESTIONES_LLAMADA_ANTES_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_90002_B4 AND tipo='LLAMADA'" \
        2>/dev/null | tr -d '[:space:]')"
    TOKEN_CSRF_B4="$(extraer_token_csrf)"
    TOKEN_GESTION_B4="$(extraer_token_gestion "$CUERPO_TMP" "$ID_PRESTAMO_90002_B4")"
    postear_y_verificar_en "POST /mora/{id}/gestion (llamada, gerente, Bloque 4)" \
        "/mora/$ID_PRESTAMO_90002_B4/gestion" "/mora" "Gestión registrada." \
        --data-urlencode "idPersonaContactada=$ID_CLIENTE_90002_B4" --data-urlencode "tipo=LLAMADA" \
        --data-urlencode "resultado=Prueba de humo Bloque 4" --data-urlencode "tokenGestion=$TOKEN_GESTION_B4" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4"
    verificar_sql "SELECT: la gestión manual de $OPERACION_90002_B4 quedó registrada (Bloque 4)" \
        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_90002_B4 AND tipo='LLAMADA'" \
        "$((GESTIONES_LLAMADA_ANTES_B4 + 1))"

    # Tanda de usabilidad (19/10/2026, hallazgo de Joel, RF24 §10): token de gestión inválido/
    # inventado -- antes el servidor decía "Gestión registrada." igual, sin guardar nada. Ahora
    # debe mostrar la advertencia nueva, sin crear ninguna fila.
    GESTIONES_TOTAL_ANTES_B4="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_90002_B4" \
        2>/dev/null | tr -d '[:space:]')"
    TOKEN_CSRF_B4="$(extraer_token_csrf)"
    # B197 (15/11/2026, misma causa que la línea 4496): este POST se sigue con un GET que deja la
    # página en $CUERPO_TMP, y de ESA página sale el extraer_token_gestion de más abajo (el del
    # PAGO_PARCIAL). Con /mora paginado, la lista pelada no trae el <form> de este préstamo
    # --queda en la página 3--, así que ese token salía vacío y el POST de abajo se rechazaba por
    # token en vez de por la regla de negocio que se quiere probar. Se sigue con la página
    # FILTRADA: un resultado, página 1 siempre, y el aviso flash se ve igual (va en cualquier
    # pantalla).
    postear_y_verificar_en "POST /mora/{id}/gestion (token inválido/inventado, Bloque 4)" \
        "/mora/$ID_PRESTAMO_90002_B4/gestion" "/mora?texto=$OPERACION_90002_B4" \
        "No se registró una gestión nueva. Si ya la había enviado, aparece en la columna Última gestión; si no, actualice la página e intente de nuevo." \
        --data-urlencode "idPersonaContactada=$ID_CLIENTE_90002_B4" --data-urlencode "tipo=LLAMADA" \
        --data-urlencode "resultado=No debería guardarse" --data-urlencode "tokenGestion=token-invalido-de-prueba" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4"
    verificar_texto "GET /mora tras token inválido (aviso nuevo presente, Bloque 4)" \
        "No se registró una gestión nueva"
    verificar_texto_ausente "GET /mora tras token inválido (sin \"Gestión registrada.\", Bloque 4)" "Gestión registrada."
    verificar_sql "SELECT: ninguna fila nueva en gestiones_cobranza con token inválido (Bloque 4)" \
        "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo=$ID_PRESTAMO_90002_B4" "$GESTIONES_TOTAL_ANTES_B4"

    TOKEN_CSRF_B4="$(extraer_token_csrf)"
    TOKEN_GESTION_B4="$(extraer_token_gestion "$CUERPO_TMP" "$ID_PRESTAMO_90002_B4")"
    postear_y_verificar_en "POST /mora/{id}/gestion (PAGO_PARCIAL a mano, rechazado, Bloque 4)" \
        "/mora/$ID_PRESTAMO_90002_B4/gestion" "/mora" "Ese tipo de gestión se genera solo al cobrar." \
        --data-urlencode "idPersonaContactada=$ID_CLIENTE_90002_B4" --data-urlencode "tipo=PAGO_PARCIAL" \
        --data-urlencode "tokenGestion=$TOKEN_GESTION_B4" --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4"

    verificar GET /panel 200
    verificar_texto "GET /panel (gerente ve Cartera en la calle, Bloque 4)" "Cartera en la calle"
    verificar_texto "GET /panel (gerente ve Cobrado esta semana, Bloque 4)" "Cobrado esta semana"

    verificar GET /reportes 200
    # Bloque 7/E10 (D-129, Obs. 27): tarjetas agrupadas en secciones con título.
    verificar_texto "GET /reportes (Obs. 27: sección Para el dueño)" "Para el dueño"
    verificar_texto "GET /reportes (Obs. 27: sección Cobranza)" "Cobranza"
    verificar_texto "GET /reportes (Obs. 27: sección Impuestos)" "Impuestos"
    verificar_texto "GET /reportes (Obs. 27: sección Control)" "Control"
    verificar GET /reportes/vencimientos 200
    verificar GET /reportes/morosidad 200
    verificar GET /reportes/gestiones 200
    verificar GET /reportes/cartera 200
    verificar_texto "GET /reportes/cartera (rótulo Ley 6624, Bloque 4)" "Interés futuro programado (no exigible, Ley 6624)"
    verificar GET /reportes/resumen-mensual 200
    verificar GET /reportes/caja 200
    verificar GET /reportes/iva 200
    verificar GET /reportes/incobrables 200
    verificar GET /reportes/auditoria 200
    verificar GET /reportes/estado-cuenta 200

    # Bloque 7/E10, Tanda D (D-128): resumen financiero, informes desde-hasta, proyección de fondos.
    verificar GET /reportes/resumen-financiero 200
    verificar_texto "GET /reportes/resumen-financiero (créditos en trámite, D-128)" "Créditos en trámite"
    verificar_texto "GET /reportes/resumen-financiero (cartera, D-128)" "Capital pendiente"
    verificar_texto "GET /reportes/resumen-financiero (demanda por tramo, D-128)" "Demanda por tramo de monto"
    verificar_texto "GET /reportes/resumen-financiero (Obs. 28: tarjeta Aprobados sin desembolsar)" "Aprobados sin desembolsar"
    verificar GET /reportes/proyeccion-fondos 200
    verificar_texto "GET /reportes/proyeccion-fondos (los 3 horizontes, D-128)" "A 90 días"
    verificar_texto "GET /reportes/proyeccion-fondos (Obs. 30a: columna de posibles egresos)" "Posibles egresos (solicitudes en trámite)"
    verificar_texto "GET /reportes/proyeccion-fondos (Obs. 30c: nota -- ingresos incluyen mora)" "incluye las cuotas de préstamos en"
    verificar GET /reportes/solicitudes 200
    verificar GET /reportes/desembolsos 200
    verificar GET /reportes/cobros 200
    verificar GET /reportes/reversiones 200
    verificar GET /reportes/clientes-nuevos 200
    verificar GET /reportes/cierres-documentales 200

    verificar GET "/reportes/estado-cuenta/$ID_PRESTAMO_90002_B4" 200
    # Hallazgo de Joel (16/10/2026): la hoja de estilo "media=print"/window.print() se retiró en
    # RF34 (commit 5ea3d4b, 13/10/2026) -- "Imprimir" pasó a "Informe PDF" + "Excel/CSV" (Jasper),
    # corregido acá a la base real.
    verificar_texto "GET /reportes/estado-cuenta/{id} (botón Informe PDF, RF34)" "Informe PDF"
    verificar_texto "GET /reportes/estado-cuenta/{id} (botón Excel/CSV, RF34)" "Excel/CSV"
    verificar_texto "GET /reportes/estado-cuenta/{id} (historial de cobros, Bloque 4)" "Historial de cobros"
    verificar_texto "GET /reportes/estado-cuenta/{id} (gestiones de cobranza, Bloque 4)" "Gestiones de cobranza"
    verificar_texto "GET /reportes/estado-cuenta/{id} (la gestión recién registrada aparece, Bloque 4)" "Llamada"

    verificar GET /reportes/resumen-mensual/csv 200
    verificar_texto "GET /reportes/resumen-mensual/csv (encabezado CSV, Bloque 4)" "Concepto;Valor"
    verificar GET /reportes/iva/csv 200
    verificar_texto "GET /reportes/iva/csv (encabezado CSV, Bloque 4)" "Factura;Fecha;Cliente"
    verificar GET /reportes/resumen-financiero/csv 200
    verificar_texto "GET /reportes/resumen-financiero/csv (encabezado CSV, D-128)" "Concepto;Valor"
    verificar_texto "GET /reportes/resumen-financiero/csv (Obs. 28: columna Aprobados sin desembolsar)" "Aprobados sin desembolsar"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
    registrar "GET /salir (gerente, Bloque 4)" 302 "$status"

    # --- Oficial: CONSULTA en VER_REPORTES (4 de 10 reportes), TOTAL en /mora ---
    if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ]; then
        COOKIE_JAR="$(temporal)"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
        TOKEN_CSRF_B4="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4")
        registrar "POST /acceso (login oficial, Bloque 4)" 302 "$status"

        verificar GET /mora 200
        verificar GET /panel 200

        verificar GET /reportes 200
        verificar GET /reportes/vencimientos 200
        verificar GET /reportes/morosidad 200
        verificar GET /reportes/gestiones 200
        verificar GET /reportes/estado-cuenta 200
        verificar GET "/reportes/estado-cuenta/$ID_PRESTAMO_90002_B4" 200

        verificar GET /reportes/cartera 403
        verificar GET /reportes/resumen-mensual 403
        verificar GET /reportes/caja 403
        verificar GET /reportes/iva 403
        verificar GET /reportes/incobrables 403
        verificar GET /reportes/auditoria 403
        # Bloque 7/E10, Tanda D (D-128): los nuevos reportes también son exclusivos del gerente.
        verificar GET /reportes/resumen-financiero 403
        verificar GET /reportes/proyeccion-fondos 403
        verificar GET /reportes/solicitudes 403
        verificar GET /reportes/desembolsos 403
        verificar GET /reportes/cobros 403
        verificar GET /reportes/reversiones 403
        verificar GET /reportes/clientes-nuevos 403
        verificar GET /reportes/cierres-documentales 403
        verificar GET /cobranza 403

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (oficial, Bloque 4)" 302 "$status"
    else
        echo
        echo "== Bloque 4 (oficial) omitido: defina SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba. ==" >&2
    fi

    # --- Cajera: NINGUNO en /mora y /reportes; su /panel sigue funcionando ---
    if [ -n "${SGCPC_USUARIO_CAJERA:-}" ] && [ -n "${SGCPC_CONTRASENA_CAJERA:-}" ]; then
        COOKIE_JAR="$(temporal)"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
        TOKEN_CSRF_B4="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_CAJERA" --data-urlencode "contrasena=$SGCPC_CONTRASENA_CAJERA" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_B4")
        registrar "POST /acceso (login cajera, Bloque 4)" 302 "$status"

        verificar GET /mora 403
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/mora/$ID_PRESTAMO_90002_B4/gestion" \
            --data-urlencode "idPersonaContactada=$ID_CLIENTE_90002_B4" --data-urlencode "tipo=LLAMADA" --data-urlencode "tokenCsrf=x")
        registrar "POST /mora/{id}/gestion (cajera, NINGUNO, Bloque 4)" 403 "$status"

        verificar GET /panel 200
        verificar GET /reportes 403
        verificar GET /reportes/vencimientos 403

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (cajera, Bloque 4)" 302 "$status"
    else
        echo
        echo "== Bloque 4 (cajera) omitido: defina SGCPC_USUARIO_CAJERA/SGCPC_CONTRASENA_CAJERA en ~/.sgcpc_prueba. ==" >&2
    fi
fi
fi

# ---------------------------------------------------------------------
# Bloque 5 (E9): reversiones de cobro y cierre documental. Requiere
#    oficial y cajera (prepara/desembolsa préstamos propios, cédula 9...,
#    reutiliza preparar_prestamo_e7/desembolsar_prestamo_e7/
#    crear_persona_con_expediente/extraer_identificador_confirmacion/
#    extraer_monto_etiqueta, ya definidas más arriba). D-75 vigente:
#    nunca toca una caja que esta corrida no abrió.
#    Encontrado al correr esto por primera vez: ServicioDesembolso exige
#    caja con saldo suficiente ANTES de poder desembolsar -- por eso la
#    caja de este bloque no arranca vacía sin más: el gerente entrega
#    exactamente lo que cada préstamo necesita, en el momento justo, y el
#    desembolso de R3 (1.000.000, más chico que la cuota 1 de R2) es lo
#    que deliberadamente deja la caja por debajo de lo que hace falta
#    para revertir el cobro de R2 -- ahí se prueba el caso 6.
# ---------------------------------------------------------------------
echo
echo "== Bloque 5 (E9): reversiones de cobro y cierre documental =="

if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ] \
        && [ -n "${SGCPC_USUARIO_CAJERA:-}" ] && [ -n "${SGCPC_CONTRASENA_CAJERA:-}" ]; then

    SESIONES_CAJA_ABIERTAS_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" 2>/dev/null | tr -d '[:space:]')"
    # Encontrado corriendo esto con una caja abierta a mitad de la corrida (pedido de Joel): este
    # bloque reutiliza preparar_prestamo_e7, definida adentro del "else" de Bloque 5 (Cobranza/E7)
    # más arriba -- si esa sección se abortó por el mismo motivo (caja abierta en SU momento,
    # aunque ya no lo esté ahora), la función nunca se creó y este bloque explotaba con "command
    # not found" en vez de abortar prolijo como el resto.
    if [ "$SESIONES_CAJA_ABIERTAS_E9" != "0" ]; then
        echo "== Bloque 5 (E9) ABORTADO: ya hay una caja ABIERTA que esta corrida no abrió (posible prueba manual de Joel en curso). No se toca. ==" >&2
    elif ! declare -F preparar_prestamo_e7 > /dev/null; then
        echo "== Bloque 5 (E9) ABORTADO: preparar_prestamo_e7 no está definida porque Bloque 5 (Cobranza) se abortó antes en esta misma corrida (posible prueba manual de Joel en curso). No se toca. ==" >&2
    else

    # --- Permisos (caso 7), sin datos: cajera sin acceso a /reversiones; oficial y gerente
    #     sin el permiso de solicitar (idPago=999999999 no importa, el 403 lo da el filtro,
    #     nunca llega al servicio). ---
    COOKIE_JAR_GERENTE_E9="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_E9="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9" > /dev/null
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/solicitar" \
        --data-urlencode "idPago=999999999" --data-urlencode "motivo=x" --data-urlencode "identificadorConfirmacion=x" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
    registrar "POST /reversiones/solicitar (gerente, sin SOLICITAR_REVERSION, Bloque 5 E9)" 403 "$status"

    COOKIE_JAR_OFICIAL_E9="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_OFICIAL_E9="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E9" > /dev/null
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X POST "$BASE_URL/reversiones/solicitar" \
        --data-urlencode "idPago=999999999" --data-urlencode "motivo=x" --data-urlencode "identificadorConfirmacion=x" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_OFICIAL_E9")
    registrar "POST /reversiones/solicitar (oficial, sin SOLICITAR_REVERSION, Bloque 5 E9)" 403 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X GET "$BASE_URL/reversiones")
    registrar "GET /reversiones (oficial, sin AUTORIZAR_REVERSION_O_DIFERENCIA, Bloque 5 E9)" 403 "$status"

    COOKIE_JAR_CAJERA_E9="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E9" -c "$COOKIE_JAR_CAJERA_E9" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_CSRF_CAJERA_E9="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E9" -c "$COOKIE_JAR_CAJERA_E9" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_CAJERA" --data-urlencode "contrasena=$SGCPC_CONTRASENA_CAJERA" \
        --data-urlencode "tokenCsrf=$TOKEN_CSRF_CAJERA_E9" > /dev/null
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E9" -c "$COOKIE_JAR_CAJERA_E9" -X GET "$BASE_URL/reversiones")
    registrar "GET /reversiones (cajera, 403, Bloque 5 E9)" 403 "$status"

    # --- La cajera abre su propia caja, vacía (el gerente la va financiando a medida que hace
    #     falta, más abajo). preparar_prestamo_e7/desembolsar_prestamo_e7 operan sobre
    #     $COOKIE_JAR: se reasigna temporalmente a la sesión de la cajera. ---
    COOKIE_JAR_ANTERIOR_E9="$COOKIE_JAR"
    COOKIE_JAR="$COOKIE_JAR_CAJERA_E9"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
    TOKEN_CSRF_E9="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/abrir" \
        --data-urlencode "saldoInicial=0" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
    registrar "POST /caja/abrir (Bloque 5 E9)" 302 "$status"

    entregar_efectivo_e9() {
        local monto="$1" observacion="$2"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/caja" > /dev/null
        local token
        token="$(extraer_token_csrf)"
        local st
        st=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/caja/entregar" \
            --data-urlencode "monto=$monto" --data-urlencode "observacion=$observacion" --data-urlencode "tokenCsrf=$token")
        registrar "POST /caja/entregar ($observacion, Bloque 5 E9)" 302 "$st"
    }

    # =======================================================================
    # Préstamo R1 (2.000.000/12): casos 2 (rechazo con motivo) y 3 (solo la
    # última operación). El gerente entrega exactamente el monto del
    # préstamo antes de desembolsar (D-73: la caja tiene que alcanzar).
    # =======================================================================
    entregar_efectivo_e9 "2.000.000" "Prueba de humo: fondo para desembolsar R1."
    preparar_prestamo_e7 "51" "2.000.000" "12" "Reversion R1"
    ID_PRESTAMO_R1_E9="$PRESTAMO_PREPARADO_ID"
    PRESTAMO_R1_OPERACION_E9="$PRESTAMO_PREPARADO_OPERACION"
    if [ "$ID_PRESTAMO_R1_E9" != "0" ]; then
        desembolsar_prestamo_e7 "$ID_PRESTAMO_R1_E9" "R1, Bloque 5 E9"

        # 03/11/2026: si el escenario no se puede armar (un feriado impide hacer vencer la cuota),
        # el bloque FALLA FUERTE acá en vez de seguir. Antes seguía igual y el préstamo quedaba
        # con UN solo cobro: "revertir el pago A, que no es la última operación" dejaba de existir
        # (A y B eran el mismo pago), la solicitud se aceptaba con razón, y la corrida reportaba
        # 5 fallas que no eran defectos de nada. Una precondición que no se cumple se dice, no se
        # disfraza de falla funcional.
        ESCENARIO_R1_OK="si"
        for numeroCuota in 1 2; do
            # D-144 (pedido de Joel): sin esto, "Cobrar" no tendría nada vencido que tocar (el
            # préstamo se acaba de desembolsar hoy).
            if ! vencer_cuota_hoy "$ID_PRESTAMO_R1_E9" "$numeroCuota"; then
                registrar "PRECONDICIÓN R1 (Bloque 5 E9): hacer vencer la cuota $numeroCuota para armar el escenario de reversiones" \
                    "posible" "imposible: la cuota caería en un feriado activo"
                ESCENARIO_R1_OK="no"
                break
            fi
            TOTAL_CUOTA_R1_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT c.total_programado FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_R1_E9 AND pp.vigente=true AND c.numero_cuota=$numeroCuota" \
                2>/dev/null | tr -d '[:space:]')"
            curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
            TOKEN_CSRF_E9="$(extraer_token_csrf)"
            ID_CONF_R1_E9="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9/cobrar" \
                --data-urlencode "montoRecibido=$TOTAL_CUOTA_R1_E9" --data-urlencode "identificadorConfirmacion=$ID_CONF_R1_E9" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
            registrar "POST /cobranza/{id}/cobrar (R1 cuota $numeroCuota, Bloque 5 E9)" 302 "$status"
        done
        ID_PAGO_R1_A_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_R1_E9 AND estado_pago='CONFIRMADO' ORDER BY id_pago ASC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
        ID_PAGO_R1_B_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_R1_E9 AND estado_pago='CONFIRMADO' ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
        # 03/11/2026: el caso 3 ("solo la última operación") EXIGE dos cobros distintos. Si los
        # dos cobros no quedaron hechos, A y B son el mismo pago y la solicitud se aceptaría con
        # razón -- lo que se vería como dos fallas funcionales que no existen. Se verifica la
        # precondición y, si no está, se saltan los casos que dependen de ella diciéndolo.
        if [ "$ESCENARIO_R1_OK" = "si" ] && { [ -z "$ID_PAGO_R1_A_E9" ] || [ "$ID_PAGO_R1_A_E9" = "$ID_PAGO_R1_B_E9" ]; }; then
            registrar "PRECONDICIÓN R1 (Bloque 5 E9): los dos cobros necesarios para el caso 'solo la última operación'" \
                "2 pagos distintos" "solo 1 pago: el escenario no se armó"
            ESCENARIO_R1_OK="no"
        fi
        if [ "$ESCENARIO_R1_OK" != "si" ]; then
            echo "== Bloque 5 (E9) R1 ABORTADO: no se pudo armar el escenario de reversiones (ver la PRECONDICIÓN en el resumen). ==" >&2
        fi
        if [ "$ESCENARIO_R1_OK" = "si" ]; then

        # Caso 3: intentar revertir el pago A (no es la última operación: el B es más nuevo).
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        # RF25 (factura, pedido de Joel, 12/10/2026): sin esta casilla el servidor rechaza --
        # cualquier cuota con interés genera factura (correspondeEmitirFactura), así que R1
        # también la tiene, igual que R2 más abajo.
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_R1_A_E9" --data-urlencode "motivo=Prueba de humo: no es la última." \
            --data-urlencode "identificadorConfirmacion=r1a-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R1_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/solicitar (R1 pago A, no es la última, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        verificar_texto "GET /cobranza/{id} (R1: rechazado por no ser la última operación, Bloque 5 E9)" \
            "Solo se puede revertir la operación más reciente de este préstamo."
        verificar_sql "SELECT: no quedó ninguna solicitud de reversión para el pago A de R1 (Bloque 5 E9)" \
            "SELECT count(*) FROM reversiones_pago WHERE id_pago=$ID_PAGO_R1_A_E9" "0"

        # Caso 2: solicitar sobre el pago B (la última) y el gerente la rechaza con motivo.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_R1_B_E9" --data-urlencode "motivo=Prueba de humo: motivo de solicitud." \
            --data-urlencode "identificadorConfirmacion=r1b-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R1_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/solicitar (R1 pago B, la última, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        verificar_texto "GET /cobranza/{id} (R1: solicitud pendiente, Bloque 5 E9)" \
            "Reversión solicitada — pendiente de autorización"

        ID_REVERSION_R1_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_reversion_pago FROM reversiones_pago WHERE id_pago=$ID_PAGO_R1_B_E9" 2>/dev/null | tr -d '[:space:]')"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        verificar_texto "GET /reversiones (R1 en la bandeja del gerente, Bloque 5 E9)" "$PRESTAMO_R1_OPERACION_E9"
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/rechazar/$ID_REVERSION_R1_E9" \
            --data-urlencode "motivoRechazo=Prueba de humo: motivo de rechazo." --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/rechazar/{id} (R1, con motivo, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        verificar_texto "GET /reversiones tras rechazar (mensaje de éxito, Bloque 5 E9)" "Solicitud de reversión rechazada."
        verificar_sql "SELECT: la reversión de R1 quedó RECHAZADA con su motivo (Bloque 5 E9)" \
            "SELECT estado||'|'||motivo_rechazo FROM reversiones_pago WHERE id_reversion_pago=$ID_REVERSION_R1_E9" \
            "RECHAZADA|Prueba de humo: motivo de rechazo."
        verificar_sql "SELECT: el pago B de R1 sigue CONFIRMADO (un rechazo no revierte nada, Bloque 5 E9)" \
            "SELECT estado_pago FROM pagos WHERE id_pago=$ID_PAGO_R1_B_E9" "CONFIRMADO"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        verificar_texto "GET /cobranza/{id} (R1: motivo de rechazo visible en la columna Reversión, Bloque 5 E9)" \
            "Reversión rechazada: Prueba de humo: motivo de rechazo."
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_R1_B_E9" --data-urlencode "motivo=Reintento tras el rechazo, con mejor motivo." \
            --data-urlencode "identificadorConfirmacion=r1b-retry-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R1_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        # RF25 Opción A (pedido de Joel, 12/10/2026): ya NO se bloquea -- se puede volver a
        # solicitar con un motivo nuevo (antes de la Opción A, esto quedaba bloqueado para
        # siempre; ver ServicioReversiones.solicitarReversion/validarPuedeSolicitarReversion).
        registrar "POST /reversiones/solicitar (R1 pago B, reintento tras rechazo -- Opción A, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R1_E9" > /dev/null
        verificar_texto "GET /cobranza/{id} (R1: reversión solicitada de nuevo tras el rechazo, Bloque 5 E9)" \
            "Reversión solicitada — pendiente de autorización"
        verificar_sql "SELECT: la reversión de R1 volvió a SOLICITADA con el motivo nuevo (Opción A, Bloque 5 E9)" \
            "SELECT estado||'|'||motivo_reversion FROM reversiones_pago WHERE id_pago=$ID_PAGO_R1_B_E9" \
            "SOLICITADA|Reintento tras el rechazo, con mejor motivo."
        fi
    else
        echo "== Préstamo R1 (Bloque 5 E9) no se pudo preparar; se omiten los casos 2 y 3. ==" >&2
    fi

    # =======================================================================
    # Préstamo R2 (10.000.000/6, cuota grande a propósito -- pero sin pasarse
    # del 40 % de afectación del ingreso fijo de crear_persona_con_expediente,
    # 6.000.000, o RN-03 rechaza el envío con un 302 igual "OK" pero sin
    # mandar la solicitud de verdad, encontrado en la corrida anterior):
    # casos 1 (DNIT), 4 (anticipo) y 5 (cancelación). El caso 6 (caja
    # insuficiente) se prueba más abajo, después de desembolsar R3.
    # =======================================================================
    entregar_efectivo_e9 "10.000.000" "Prueba de humo: fondo para desembolsar R2."
    preparar_prestamo_e7 "52" "10.000.000" "6" "Reversion R2"
    ID_PRESTAMO_R2_E9="$PRESTAMO_PREPARADO_ID"
    if [ "$ID_PRESTAMO_R2_E9" != "0" ]; then
        desembolsar_prestamo_e7 "$ID_PRESTAMO_R2_E9" "R2, Bloque 5 E9"

        # D-144 (pedido de Joel): sin esto, "Cobrar" no tendría nada vencido que tocar (el
        # préstamo se acaba de desembolsar hoy).
        vencer_cuota_hoy "$ID_PRESTAMO_R2_E9" 1
        TOTAL_CUOTA1_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT c.total_programado FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_R2_E9 AND pp.vigente=true AND c.numero_cuota=1" \
            2>/dev/null | tr -d '[:space:]')"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        ID_CONF_R2_E9="$(extraer_identificador_confirmacion "$CUERPO_TMP" cobrar)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9/cobrar" \
            --data-urlencode "montoRecibido=$TOTAL_CUOTA1_R2_E9" --data-urlencode "identificadorConfirmacion=$ID_CONF_R2_E9" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /cobranza/{id}/cobrar (R2 cuota 1, con factura, Bloque 5 E9)" 302 "$status"
        ID_PAGO_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_R2_E9 ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
        verificar_sql "SELECT: el cobro de R2 generó factura (interés siempre gravado, Bloque 5 E9)" \
            "SELECT count(*) FROM facturas WHERE id_pago=$ID_PAGO_R2_E9" "1"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_R2_E9" --data-urlencode "motivo=Prueba de humo: DNIT y caja." \
            --data-urlencode "identificadorConfirmacion=r2-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R2_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/solicitar (R2, con factura, casilla de la cajera, Bloque 5 E9)" 302 "$status"
        ID_REVERSION_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_reversion_pago FROM reversiones_pago WHERE id_pago=$ID_PAGO_R2_E9" 2>/dev/null | tr -d '[:space:]')"
    else
        echo "== Préstamo R2 (Bloque 5 E9) no se pudo preparar; se omiten los casos 1, 4, 5 y 6. ==" >&2
    fi

    # =======================================================================
    # Préstamo R3, parte 1 (desembolso): a propósito NO se entrega efectivo
    # nuevo -- se desembolsa con lo que ya quedó en caja tras el cobro de R2
    # (residuales de R1, sin revertir, más la cuota 1 de R2 recién cobrada).
    # Entregar exactamente el monto de R3 antes de desembolsarlo (como se
    # hacía antes) deja el saldo neto sin cambios (entra 1.000.000, sale
    # 1.000.000): el desembolso de R3 nunca drenaba nada. Sin esa entrega, el
    # desembolso de R3 sí resta 1.000.000 reales de la caja -- más que los
    # residuales de R1 (siempre por debajo de 1.000.000 con los montos fijos
    # de esta prueba), así que el saldo que queda es siempre menor que la
    # cuota 1 de R2, sea cual sea su monto exacto. La cancelación total y el
    # cierre documental de R3 quedan para la parte 2, más abajo, después de
    # terminar con R2.
    # =======================================================================
    preparar_prestamo_e7 "53" "1.000.000" "3" "Cierre documental R3"
    ID_PRESTAMO_R3_E9="$PRESTAMO_PREPARADO_ID"
    if [ "$ID_PRESTAMO_R3_E9" != "0" ]; then
        desembolsar_prestamo_e7 "$ID_PRESTAMO_R3_E9" "R3, Bloque 5 E9"
    else
        echo "== Préstamo R3 (Bloque 5 E9) no se pudo preparar; se omite el cierre documental. ==" >&2
    fi

    # =======================================================================
    # Préstamo R2, continuación: casos 1 (DNIT), 6 (caja insuficiente, recién
    # ahora que R3 desembolsó), 4 (anticipo) y 5 (cancelación).
    # =======================================================================
    if [ -n "${ID_REVERSION_R2_E9:-}" ]; then
        # Caso 1: autorizar SIN la casilla DNIT -> rechazado, la reversión sigue SOLICITADA.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/autorizar/$ID_REVERSION_R2_E9" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/autorizar/{id} (R2, sin casilla DNIT, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        verificar_texto "GET /reversiones (R2: rechazado por falta la casilla DNIT, Bloque 5 E9)" \
            "La factura ya fue entregada al cliente. Corresponde emitir una Nota de Crédito"
        verificar_sql "SELECT: la reversión de R2 sigue SOLICITADA (DNIT no la consumió, Bloque 5 E9)" \
            "SELECT estado FROM reversiones_pago WHERE id_reversion_pago=$ID_REVERSION_R2_E9" "SOLICITADA"

        # Caso 6: con la casilla, pero la caja (drenada por el desembolso de R3) no alcanza para
        # devolver el monto de la cuota 1 de R2 -> rechazado por caja.
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/autorizar/$ID_REVERSION_R2_E9" \
            --data-urlencode "facturaEnPoderConfirmado=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/autorizar/{id} (R2, caja sin efectivo suficiente, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        verificar_texto "GET /reversiones (R2: rechazado por caja sin efectivo suficiente, Bloque 5 E9)" \
            "La caja no tiene efectivo suficiente para devolver"
        verificar_sql "SELECT: la reversión de R2 sigue SOLICITADA (caja insuficiente no la consumió, Bloque 5 E9)" \
            "SELECT estado FROM reversiones_pago WHERE id_reversion_pago=$ID_REVERSION_R2_E9" "SOLICITADA"

        # El gerente entrega efectivo de sobra (cubre esta reversión y las que siguen: anticipo y
        # cancelación) y autoriza de nuevo (mismo identificador de reversión, todavía SOLICITADA).
        entregar_efectivo_e9 "20.000.000" "Prueba de humo: entrega para poder revertir R2."

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/autorizar/$ID_REVERSION_R2_E9" \
            --data-urlencode "facturaEnPoderConfirmado=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/autorizar/{id} (R2, con casilla y caja suficiente, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        verificar_texto "GET /reversiones (R2: autorizada, mensaje con monto y factura, Bloque 5 E9)" \
            "Reversión autorizada. Se devolvieron"
        verificar_sql "SELECT: reversión de R2 quedó AUTORIZADA (Bloque 5 E9)" \
            "SELECT estado FROM reversiones_pago WHERE id_reversion_pago=$ID_REVERSION_R2_E9" "AUTORIZADA"
        verificar_sql "SELECT: pago de R2 quedó REVERTIDO (Bloque 5 E9)" \
            "SELECT estado_pago FROM pagos WHERE id_pago=$ID_PAGO_R2_E9" "REVERTIDO"
        verificar_sql "SELECT: factura de R2 quedó ANULADA con su Nota (Bloque 5 E9)" \
            "SELECT (estado='ANULADA' AND numero_nota_anulacion IS NOT NULL) FROM facturas WHERE id_pago=$ID_PAGO_R2_E9" "t"
        verificar_sql "SELECT: cuota 1 de R2 volvió a PENDIENTE, sin nada pagado (Bloque 5 E9)" \
            "SELECT estado_pago||'|'||capital_pagado||'|'||interes_pagado FROM cuotas c JOIN planes_pago pp ON pp.id_plan_pago=c.id_plan_pago WHERE pp.id_prestamo=$ID_PRESTAMO_R2_E9 AND pp.vigente=true AND c.numero_cuota=1" \
            "PENDIENTE|0|0"

        # Caso 4: anticipo de capital, después revertido -- el plan vuelve al anterior.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        VERSION_PLAN_ANTES_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT version_plan FROM planes_pago WHERE id_prestamo=$ID_PRESTAMO_R2_E9 AND vigente=true" 2>/dev/null | tr -d '[:space:]')"
        INTERES_MINIMO_R2_E9="$(extraer_monto_etiqueta "$CUERPO_TMP" 'Interés devengado hoy:')"
        MONTO_ANTICIPO_R2_E9=$((INTERES_MINIMO_R2_E9 + 500000))
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        ID_CONF_ANTICIPO_R2_E9="$(extraer_identificador_confirmacion "$CUERPO_TMP" anticipar)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9/anticipar" \
            --data-urlencode "montoRecibido=$MONTO_ANTICIPO_R2_E9" --data-urlencode "identificadorConfirmacion=$ID_CONF_ANTICIPO_R2_E9" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /cobranza/{id}/anticipar (R2, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        verificar_texto "GET /cobranza/{id} (R2: anticipo aplicado, Bloque 5 E9)" "Anticipo de capital aplicado"
        ID_PAGO_ANTICIPO_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_R2_E9 AND tipo='ANTICIPO' ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        # RF25 (factura, pedido de Joel): se manda la casilla igual que arriba -- si este pago no
        # tiene factura, el servidor la ignora (validarDeclaracionFacturaCajera no exige nada).
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_ANTICIPO_R2_E9" --data-urlencode "motivo=Prueba de humo: revertir anticipo." \
            --data-urlencode "identificadorConfirmacion=r2anticipo-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R2_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/solicitar (R2 anticipo, Bloque 5 E9)" 302 "$status"
        ID_REVERSION_ANTICIPO_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_reversion_pago FROM reversiones_pago WHERE id_pago=$ID_PAGO_ANTICIPO_R2_E9" 2>/dev/null | tr -d '[:space:]')"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        # D-144 (pedido de Joel): el anticipo de más arriba ya no es necesariamente "el mismo día
        # del desembolso" (la cuota 1 de R2 se adelantó a hoy para el cobro de más arriba), así
        # que el interés devengado puede ser > 0 y generar factura -- mismo chequeo robusto que ya
        # usa R2/cancelación más abajo, en vez de asumir "sin factura" a ciegas.
        AUTORIZAR_DATA_R2_ANTICIPO=(--data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        if grep -q "id=\"factura$ID_REVERSION_ANTICIPO_R2_E9\"" "$CUERPO_TMP"; then
            AUTORIZAR_DATA_R2_ANTICIPO+=(--data-urlencode "facturaEnPoderConfirmado=true")
        fi
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/autorizar/$ID_REVERSION_ANTICIPO_R2_E9" \
            "${AUTORIZAR_DATA_R2_ANTICIPO[@]}")
        registrar "POST /reversiones/autorizar/{id} (R2 anticipo, factura según corresponda, Bloque 5 E9)" 302 "$status"
        verificar_sql "SELECT: pago de anticipo de R2 quedó REVERTIDO (Bloque 5 E9)" \
            "SELECT estado_pago FROM pagos WHERE id_pago=$ID_PAGO_ANTICIPO_R2_E9" "REVERTIDO"
        verificar_sql "SELECT: el plan de R2 volvió a la versión anterior al anticipo (Bloque 5 E9)" \
            "SELECT version_plan FROM planes_pago WHERE id_prestamo=$ID_PRESTAMO_R2_E9 AND vigente=true" "$VERSION_PLAN_ANTES_R2_E9"
        verificar_sql "SELECT: la liquidación del anticipo de R2 quedó REVERTIDA (Bloque 5 E9)" \
            "SELECT estado_liquidacion FROM liquidaciones_anticipadas la JOIN pagos p ON p.id_liquidacion_anticipada=la.id_liquidacion WHERE p.id_pago=$ID_PAGO_ANTICIPO_R2_E9" "REVERTIDA"

        # Caso 5: cancelación total, después revertida -- el préstamo deja de estar FINALIZADO.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        COSTO_HOY_R2_E9="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-cancelar-todo')"
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        ID_CONF_CANCELAR_R2_E9="$(extraer_identificador_confirmacion "$CUERPO_TMP" cancelar)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9/cancelar" \
            --data-urlencode "montoRecibido=$COSTO_HOY_R2_E9" --data-urlencode "identificadorConfirmacion=$ID_CONF_CANCELAR_R2_E9" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /cobranza/{id}/cancelar (R2, Bloque 5 E9)" 302 "$status"
        verificar_sql "SELECT: R2 quedó FINALIZADO tras la cancelación (Bloque 5 E9)" \
            "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R2_E9" "FINALIZADO"
        ID_PAGO_CANCELACION_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_pago FROM pagos WHERE id_prestamo=$ID_PRESTAMO_R2_E9 AND tipo='CANCELACION' ORDER BY id_pago DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"

        # D-94: un préstamo FINALIZADO no da 404, sigue en modo lectura -- ahí se solicita.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R2_E9" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/reversiones/solicitar" \
            --data-urlencode "idPago=$ID_PAGO_CANCELACION_R2_E9" --data-urlencode "motivo=Prueba de humo: revertir cancelación." \
            --data-urlencode "identificadorConfirmacion=r2cancel-$RANDOM" --data-urlencode "volverA=/cobranza/$ID_PRESTAMO_R2_E9" \
            --data-urlencode "facturaEnPoderDeclaradoCajera=true" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /reversiones/solicitar (R2 cancelación, préstamo FINALIZADO, Bloque 5 E9)" 302 "$status"
        ID_REVERSION_CANCEL_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT id_reversion_pago FROM reversiones_pago WHERE id_pago=$ID_PAGO_CANCELACION_R2_E9" 2>/dev/null | tr -d '[:space:]')"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reversiones" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        AUTORIZAR_DATA_R2_CANCEL=(--data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        if grep -q "id=\"factura$ID_REVERSION_CANCEL_R2_E9\"" "$CUERPO_TMP"; then
            AUTORIZAR_DATA_R2_CANCEL+=(--data-urlencode "facturaEnPoderConfirmado=true")
        fi
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/reversiones/autorizar/$ID_REVERSION_CANCEL_R2_E9" \
            "${AUTORIZAR_DATA_R2_CANCEL[@]}")
        registrar "POST /reversiones/autorizar/{id} (R2 cancelación, Bloque 5 E9)" 302 "$status"
        verificar_sql "SELECT: R2 dejó de estar FINALIZADO tras revertir la cancelación (Bloque 5 E9)" \
            "SELECT estado_prestamo <> 'FINALIZADO' FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R2_E9" "t"
        verificar_sql "SELECT: fecha_finalizacion_financiera de R2 quedó NULL (Bloque 5 E9)" \
            "SELECT (fecha_finalizacion_financiera IS NULL) FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R2_E9" "t"
        verificar_sql "SELECT: la liquidación TOTAL de R2 quedó REVERTIDA (Bloque 5 E9)" \
            "SELECT estado_liquidacion FROM liquidaciones_anticipadas la JOIN pagos p ON p.id_liquidacion_anticipada=la.id_liquidacion WHERE p.id_pago=$ID_PAGO_CANCELACION_R2_E9" "REVERTIDA"
    fi

    # =======================================================================
    # Préstamo R3, parte 2: cierre documental completo (P10). Se cancela y
    # NO se revierte, para poder probar generar la constancia, cargar el
    # escaneo firmado y completar el cierre.
    # =======================================================================
    if [ -n "${ID_PRESTAMO_R3_E9:-}" ] && [ "$ID_PRESTAMO_R3_E9" != "0" ]; then
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/cobranza/$ID_PRESTAMO_R3_E9" > /dev/null
        COSTO_HOY_R3_E9="$(extraer_atributo_tarjeta_cobranza "$CUERPO_TMP" 'data-monto-cancelar-todo')"
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        ID_CONF_CANCELAR_R3_E9="$(extraer_identificador_confirmacion "$CUERPO_TMP" cancelar)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/cobranza/$ID_PRESTAMO_R3_E9/cancelar" \
            --data-urlencode "montoRecibido=$COSTO_HOY_R3_E9" --data-urlencode "identificadorConfirmacion=$ID_CONF_CANCELAR_R3_E9" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /cobranza/{id}/cancelar (R3, para el cierre documental, Bloque 5 E9)" 302 "$status"
        verificar_sql "SELECT: R3 quedó FINALIZADO (Bloque 5 E9)" \
            "SELECT estado_prestamo FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R3_E9" "FINALIZADO"

        # Aviso visible en el estado de cuenta y en la ficha de formalización (corrección de
        # Joel: antes no había ningún acceso visible al cierre documental).
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reportes/estado-cuenta/$ID_PRESTAMO_R3_E9" > /dev/null
        # B85 (03/11/2026): el aviso dejó de nombrar el estado interno ("Cierre documental
        # pendiente") y nombra el trámite -- estado_cuenta_ficha.jsp:82.
        verificar_texto "GET /reportes/estado-cuenta/{id} (R3: aviso de devolución del pagaré pendiente, Bloque 5 E9)" \
            "Pagaré pendiente de devolución"
        # El N.º de operación de R3 se lee ACÁ (y no más abajo, donde estaba) porque lo usan las
        # DOS verificaciones de la sección 3: la del listado y la del buscador. Es un SELECT de
        # solo lectura y R3 ya está FINALIZADO desde cuatro líneas más arriba.
        # Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 4): las dos
        # van ANTES de generar la constancia de devolución, más abajo (R3 deja de estar pendiente
        # después de eso).
        IDENTIFICADOR_OPERACION_R3_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R3_E9" \
            2>/dev/null | tr -d '[:space:]')"
        # B183 (15/11/2026, aprobado por Joel): dos arreglos en una sola línea de verificación.
        # (1) Buscaba "CC-2026", un prefijo que comparten TODAS las operaciones del año, así que no
        #     se rompía nunca -- ni siquiera si R3 desapareciera de la sección: medía "hay alguna
        #     fila", no "está la mía". Ahora busca el N.º de operación de ESTA corrida.
        # (2) La sección 3 pagina de a 20 y ordena por fecha de cancelación ASCENDENTE (la que
        #     espera hace más tiempo, primero), así que R3 -- recién cancelado -- queda ÚLTIMO, hoy
        #     en la página 3 de 3. Se pide el orden inverso, que además ejercita el orden nuevo.
        # Ojo si alguna vez se mueve esta línea: con la variable sin asignar, el texto buscado sería
        # la cadena vacía y la verificación pasaría SIEMPRE.
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion?ordenPagare=cancelada&sentidoPagare=desc" > /dev/null
        verificar_texto "GET /formalizacion?ordenPagare= (R3 en la sección del pagaré pendiente, Bloque 5 E9)" \
            "$IDENTIFICADOR_OPERACION_R3_E9"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -H "X-Requested-With: XMLHttpRequest" \
            -X GET "$BASE_URL/formalizacion?texto=$IDENTIFICADOR_OPERACION_R3_E9" > /dev/null
        verificar_texto "GET /formalizacion?texto= (sección 3, filtro encuentra por N.º de operación, Bloque 5 E9)" \
            "$IDENTIFICADOR_OPERACION_R3_E9"

        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental")
        registrar "GET /formalizacion/{id}/cierre-documental (R3, antes de generar, Bloque 5 E9)" 200 "$status"

        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental" > /dev/null
        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/generar-constancia-devolucion" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /formalizacion/{id}/generar-constancia-devolucion (R3, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental" > /dev/null
        verificar_texto "GET /formalizacion/{id}/cierre-documental (R3: constancia generada, número DEV, Bloque 5 E9)" "DEV-"
        verificar_sql "SELECT: R3 tiene número de constancia de devolución (Bloque 5 E9)" \
            "SELECT (numero_constancia_devolucion IS NOT NULL) FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R3_E9" "t"

        ID_TIPO_DEVOLUCION_FIRMADO_E9="$(grep -o 'name="idTipoDocumento" value="[^"]*"' "$CUERPO_TMP" | head -1 | sed 's/.*value="//;s/"$//')"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/documentos/cargar" \
            -F "tokenCsrf=$TOKEN_CSRF_E9" -F "idPrestamo=$ID_PRESTAMO_R3_E9" -F "idTipoDocumento=$ID_TIPO_DEVOLUCION_FIRMADO_E9" \
            -F "volverA=/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental" \
            -F "archivo=@$ARCHIVO_PRUEBA_WIN;type=application/pdf;filename=devolucion_firmada.pdf")
        registrar "POST /documentos/cargar (escaneo firmado de R3, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental" > /dev/null
        verificar_texto "GET /formalizacion/{id}/cierre-documental (R3: escaneo firmado cargado, Bloque 5 E9, RF03: ahora 'Ver')" \
            "cargado"

        TOKEN_CSRF_E9="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X POST "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/completar-cierre-documental" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
        registrar "POST /formalizacion/{id}/completar-cierre-documental (R3, Bloque 5 E9)" 302 "$status"
        curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/formalizacion/$ID_PRESTAMO_R3_E9/cierre-documental" > /dev/null
        # B85 (03/11/2026): la pantalla ya no muestra el nombre del estado interno ("COMPLETO —")
        # sino lo que pasó -- cierre-documental.jsp:46. El estado interno se sigue verificando en
        # el SELECT de la línea de abajo, que es donde corresponde.
        verificar_texto "GET /formalizacion/{id}/cierre-documental (R3: pagaré devuelto, Bloque 5 E9)" \
            "El pagaré original quedó devuelto al cliente"
        verificar_sql "SELECT: R3 quedó con cierre documental COMPLETO (Bloque 5 E9)" \
            "SELECT estado_cierre_documental FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_R3_E9" "COMPLETO"
    fi

    # --- Reporte "Facturas anuladas del mes" (gerente): debe listar la factura anulada de R2.
    #     $COOKIE_JAR sigue siendo el de la cajera acá -- por eso se usa $COOKIE_JAR_GERENTE_E9
    #     explícito, nunca el helper "verificar" (que pega con el $COOKIE_JAR global). ---
    if [ -n "${ID_PAGO_R2_E9:-}" ]; then
        # La tabla del reporte no trae la operación del préstamo (columnas: Factura, Timbrado,
        # Fecha, Cliente, Documento, Total, Nota de anulación, Fecha de anulación, Motivo -- ver
        # facturas-anuladas.jsp), así que se verifica contra datos que sí se muestran: el número
        # de la Nota de anulación, el timbrado, la fecha de la reversión (RG 118/2017, no la de
        # emisión de la factura) y el motivo con el que se solicitó (literal de más arriba).
        NUMERO_NOTA_ANULACION_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT numero_nota_anulacion FROM facturas WHERE id_pago=$ID_PAGO_R2_E9" 2>/dev/null | tr -d '[:space:]')"
        TIMBRADO_FACTURA_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT timbrado FROM facturas WHERE id_pago=$ID_PAGO_R2_E9" 2>/dev/null | tr -d '[:space:]')"
        FECHA_ANULACION_R2_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT to_char(r.fecha_hora_reversion, 'DD/MM/YYYY') FROM facturas f JOIN reversiones_pago r ON r.id_reversion_pago = f.id_reversion_pago WHERE f.id_pago=$ID_PAGO_R2_E9" \
            2>/dev/null | tr -d '[:space:]')"
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/reportes/facturas-anuladas")
        registrar "GET /reportes/facturas-anuladas (gerente, Bloque 5 E9)" 200 "$status"
        verificar_texto "GET /reportes/facturas-anuladas (factura anulada de R2 listada, Bloque 5 E9)" "$NUMERO_NOTA_ANULACION_R2_E9"
        verificar_texto "GET /reportes/facturas-anuladas (timbrado de la factura de R2, Bloque 5 E9)" "$TIMBRADO_FACTURA_R2_E9"
        verificar_texto "GET /reportes/facturas-anuladas (fecha de anulación de R2, RG 118/2017, Bloque 5 E9)" "$FECHA_ANULACION_R2_E9"
        verificar_texto "GET /reportes/facturas-anuladas (motivo de la reversión de R2, RG 118/2017, Bloque 5 E9)" \
            "Prueba de humo: DNIT y caja."
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X GET "$BASE_URL/reportes/facturas-anuladas")
        registrar "GET /reportes/facturas-anuladas (oficial, sin acceso, Bloque 5 E9)" 403 "$status"

        # El CSV lleva las mismas columnas nuevas (D-52: humo cubre también la descarga).
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" \
            -X GET "$BASE_URL/reportes/facturas-anuladas/csv?mes=$(date +%Y-%m)")
        registrar "GET /reportes/facturas-anuladas/csv (gerente, Bloque 5 E9)" 200 "$status"
        verificar_texto "GET /reportes/facturas-anuladas/csv (encabezado con las columnas de RG 118/2017, Bloque 5 E9)" \
            "Fecha de anulación;Motivo"
        verificar_texto "GET /reportes/facturas-anuladas/csv (motivo de la reversión de R2, Bloque 5 E9)" \
            "Prueba de humo: DNIT y caja."
    fi

    # --- Panel del gerente: la tarjeta "Reversiones por autorizar" existe. ---
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/panel" > /dev/null
    # B36 (03/11/2026): la tarjeta concuerda en singular/plural -- con UNA sola pendiente dice
    # "Reversión por autorizar" (panel.jsp:352), así que el literal en plural no la encontraba.
    # Se busca la parte que no cambia; que el número sea el correcto lo cubre el SELECT de la
    # bandeja, no esta verificación, que es de presencia de la tarjeta.
    verificar_texto "GET /panel (tarjeta de reversiones por autorizar, Bloque 5 E9)" "por autorizar"

    # --- Cierre de la caja de este bloque, para no dejar ninguna abierta (mismo reporte final
    #     de siempre: OK/FALLO, factura_numero_hasta y cajas abiertas). ---
    SALDO_TEORICO_E9="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
        "SELECT s.saldo_inicial + COALESCE((SELECT SUM(m.monto*m.signo) FROM movimientos_caja m WHERE m.id_sesion_caja=s.id_sesion_caja),0) FROM sesiones_caja s WHERE s.estado_sesion='ABIERTA'" \
        2>/dev/null | tr -d '[:space:]')"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/caja" > /dev/null
    TOKEN_CSRF_E9="$(extraer_token_csrf)"
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/caja/cerrar" \
        --data-urlencode "saldoFisico=$SALDO_TEORICO_E9" --data-urlencode "tokenCsrf=$TOKEN_CSRF_E9")
    registrar "POST /caja/cerrar (fin del Bloque 5 E9, sin diferencia)" 302 "$status"
    verificar_sql "SELECT: no queda ninguna caja ABIERTA al terminar el Bloque 5 (E9)" \
        "SELECT count(*) FROM sesiones_caja WHERE estado_sesion='ABIERTA'" "0"

    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_E9"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_E9" -c "$COOKIE_JAR_GERENTE_E9" -X GET "$BASE_URL/salir" > /dev/null
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_E9" -c "$COOKIE_JAR_OFICIAL_E9" -X GET "$BASE_URL/salir" > /dev/null
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_CAJERA_E9" -c "$COOKIE_JAR_CAJERA_E9" -X GET "$BASE_URL/salir" > /dev/null
    fi
else
    echo
    echo "== Bloque 5 (E9) omitido: defina SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL y SGCPC_USUARIO_CAJERA/SGCPC_CONTRASENA_CAJERA en ~/.sgcpc_prueba. ==" >&2
fi

# ---------------------------------------------------------------------
# Bloque 6 (RF05/RF06/RF11, agregado 18/10/2026, pedido de Joel): confirma de punta a punta
# que el bloqueo del 40 % de afectación se revalida con los datos ACTUALES del cliente tanto
# al ENVIAR a aprobación (RF06, Caso A) como al FORMALIZAR (RF11, Caso B). Como
# ServicioSolicitudes.validarLimiteAfectacion ya rechaza directo si se supera el límite al
# crear/simular, para ejercitar la revalidación justo en ese momento posterior se crea la
# solicitud DENTRO del límite y se degrada el ingreso del cliente por SQL DESPUÉS de crear y
# ANTES de enviar/formalizar -- mismo mecanismo que ya usa este script para forzar otros
# escenarios (UPDATE personas_laborales ... presenta_comprobante_ingreso, más arriba).
# Caso C (mismo día, mismo pedido): mismo mecanismo, ahora para el bloqueo directo de
# comprobante de ingresos al FORMALIZAR (el de crear y enviar ya se prueba en la sección 4.5,
# más arriba) -- hueco preexistente destapado al quitar el codeudor, ver REGLAS_DESARROLLO.md
# §5 ("solo ingreso fijo comprobable"). Autocontenido: cédulas y sesiones propias, no
# interfiere con nada de los bloques anteriores.
# ---------------------------------------------------------------------
if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ]; then
    COOKIE_JAR_ANTERIOR_RF06="$COOKIE_JAR"
    COOKIE_JAR_OFICIAL_RF06="$(temporal)"
    COOKIE_JAR="$COOKIE_JAR_OFICIAL_RF06"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_OFICIAL_RF06="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06" > /dev/null

    FECHA_SOLICITUD_RF06="$(date -d '+45 days' +%Y-%m-%d 2>/dev/null || date -v+45d +%Y-%m-%d)"

    # --- Caso A: bloqueo al ENVIAR a aprobación (RF06) ---
    CEDULA_RF06A="${CEDULA_PRUEBA}19"
    crear_persona_con_expediente "$CEDULA_RF06A" "Cliente" "Prueba Humo RF06 Enviar" "3.000.000" "$TOKEN_OFICIAL_RF06"
    ID_CLIENTE_RF06A="$ID_PERSONA_CREADA"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/solicitudes/nuevo" \
        --data-urlencode "cedulaCliente=$CEDULA_RF06A" --data-urlencode "monto=800.000" \
        --data-urlencode "plazoMeses=12" --data-urlencode "destinoCredito=Prueba de humo (RF06, bloqueo al enviar)" \
        --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD_RF06" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06")
    registrar "POST /solicitudes/nuevo (RF06, dentro del límite al crear)" 302 "$status"
    UBICACION_RF06A="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_SOLICITUD_RF06A="$(echo "$UBICACION_RF06A" | grep -o '[0-9]*$')"
    [ -z "$ID_SOLICITUD_RF06A" ] && ID_SOLICITUD_RF06A="0"
    verificar_sql "SELECT: solicitud RF06A quedó en BORRADOR, dentro del límite al crear" \
        "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_RF06A" "BORRADOR"

    # Degrada el ingreso DESPUÉS de crear (ya pasó el 40 % al crear) -- RF06 revalida con el
    # ingreso ACTUAL al enviar, así que esto fuerza el bloqueo sin tocar la solicitud.
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c \
        "UPDATE personas_laborales SET ingreso_neto_comprobado = 50000 WHERE id_persona=$ID_CLIENTE_RF06A;" \
        > /dev/null 2>&1

    postear_y_verificar_en "POST /solicitudes/{id}/enviar (RF06, cuota supera la capacidad actual -> bloqueado)" \
        "/solicitudes/$ID_SOLICITUD_RF06A/enviar" "/solicitudes/$ID_SOLICITUD_RF06A" \
        "La cuota supera la capacidad de pago del cliente. Reduzca el monto o amplíe el plazo." \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06"
    verificar_sql "SELECT: solicitud RF06A sigue en BORRADOR (RF06, bloqueo al enviar)" \
        "SELECT estado_solicitud FROM solicitudes_credito WHERE id_solicitud=$ID_SOLICITUD_RF06A" "BORRADOR"

    # --- Caso B: bloqueo al FORMALIZAR (RF11) ---
    CEDULA_RF06B="${CEDULA_PRUEBA}20"
    crear_persona_con_expediente "$CEDULA_RF06B" "Cliente" "Prueba Humo RF06 Formalizar" "3.000.000" "$TOKEN_OFICIAL_RF06"
    ID_CLIENTE_RF06B="$ID_PERSONA_CREADA"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/solicitudes/nuevo" \
        --data-urlencode "cedulaCliente=$CEDULA_RF06B" --data-urlencode "monto=800.000" \
        --data-urlencode "plazoMeses=12" --data-urlencode "destinoCredito=Prueba de humo (RF11, bloqueo al formalizar)" \
        --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD_RF06" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06")
    registrar "POST /solicitudes/nuevo (RF11, dentro del límite al crear)" 302 "$status"
    UBICACION_RF06B="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_SOLICITUD_RF06B="$(echo "$UBICACION_RF06B" | grep -o '[0-9]*$')"
    [ -z "$ID_SOLICITUD_RF06B" ] && ID_SOLICITUD_RF06B="0"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/solicitudes/$ID_SOLICITUD_RF06B/enviar" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06")
    registrar "POST /solicitudes/{id}/enviar (RF11, todavía dentro del límite)" 302 "$status"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir" > /dev/null
    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_RF06"

    COOKIE_JAR_GERENTE_RF06="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_GERENTE_RF06="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
        --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06" > /dev/null

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" \
        -X POST "$BASE_URL/aprobaciones/$ID_SOLICITUD_RF06B/aprobar" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06")
    registrar "POST /aprobaciones/{id}/aprobar (RF11, todavía dentro del límite)" 302 "$status"

    # Degrada el ingreso DESPUÉS de aprobar -- RF11 revalida con el ingreso ACTUAL al generar
    # los documentos de formalización, sin excepción. (Al aprobar, ServicioAprobacionCredito.
    # aprobar revisa el porcentaje YA CONGELADO al enviar, no uno recalculado -- por eso aprobar
    # no se ve afectado por este UPDATE hecho después.)
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c \
        "UPDATE personas_laborales SET ingreso_neto_comprobado = 50000 WHERE id_persona=$ID_CLIENTE_RF06B;" \
        > /dev/null 2>&1

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" \
        -X POST "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_RF06B/generar" \
        --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06")
    registrar "POST /formalizacion/nueva/{id}/generar (RF11, cuota supera la capacidad actual -> bloqueado)" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" \
        -X GET "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_RF06B" > /dev/null
    # F2 (21/10/2026, pedido de Joel, punto 7): esta GET corre como GERENTE (COOKIE_JAR_GERENTE_RF06)
    # -- GERENTE_ADMINISTRADOR tiene APROBAR_RECHAZAR_SOLICITUD en TOTAL, así que ahora ve la
    # versión (b) al final del mensaje (no se fija el % exacto, ver la nota de paso F1 más arriba).
    verificar_texto "GET /formalizacion/nueva/{id} (RF11, gerente: mensaje de bloqueo, versión (b))" \
        "Puede cancelar la solicitud desde Aprobación para evaluar una nueva con los datos actuales del cliente."
    verificar_sql "SELECT: no se creó ningún préstamo para la solicitud RF06B (RF11, bloqueo al formalizar)" \
        "SELECT count(*) FROM prestamos WHERE id_solicitud=$ID_SOLICITUD_RF06B" "0"

    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06" -c "$COOKIE_JAR_GERENTE_RF06" -X GET "$BASE_URL/salir" > /dev/null
    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_RF06"

    # --- Caso C: bloqueo por falta de comprobante de ingresos al FORMALIZAR (RF05/RF06/RF11,
    # hueco preexistente destapado al quitar el codeudor, pedido de Joel 18/10/2026) -- mismo
    # mecanismo que el Caso B (degradar DESPUÉS de aprobar), ahora sobre
    # presenta_comprobante_ingreso en vez de ingreso_neto_comprobado. ---
    COOKIE_JAR_OFICIAL_RF06C="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_RF06C" -c "$COOKIE_JAR_OFICIAL_RF06C" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_OFICIAL_RF06C="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_OFICIAL_RF06C" -c "$COOKIE_JAR_OFICIAL_RF06C" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
        --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06C" > /dev/null

    CEDULA_RF06C="${CEDULA_PRUEBA}21"
    COOKIE_JAR_ANTERIOR_RF06C="$COOKIE_JAR"
    COOKIE_JAR="$COOKIE_JAR_OFICIAL_RF06C"
    crear_persona_con_expediente "$CEDULA_RF06C" "Cliente" "Prueba Humo RF11 Sin Comprobante" "3.000.000" "$TOKEN_OFICIAL_RF06C"
    ID_CLIENTE_RF06C="$ID_PERSONA_CREADA"

    status=$(curl -s -D "$ENCABEZADOS_TMP" -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/solicitudes/nuevo" \
        --data-urlencode "cedulaCliente=$CEDULA_RF06C" --data-urlencode "monto=800.000" \
        --data-urlencode "plazoMeses=12" --data-urlencode "destinoCredito=Prueba de humo (RF11, bloqueo por comprobante al formalizar)" \
        --data-urlencode "primeraFechaVencimiento=$FECHA_SOLICITUD_RF06" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06C")
    registrar "POST /solicitudes/nuevo (RF11C, con comprobante al crear)" 302 "$status"
    UBICACION_RF06C="$(grep -i '^location:' "$ENCABEZADOS_TMP" | tail -1 | sed 's/^[Ll]ocation: *//;s/\r$//')"
    ID_SOLICITUD_RF06C="$(echo "$UBICACION_RF06C" | grep -o '[0-9]*$')"
    [ -z "$ID_SOLICITUD_RF06C" ] && ID_SOLICITUD_RF06C="0"

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X POST "$BASE_URL/solicitudes/$ID_SOLICITUD_RF06C/enviar" --data-urlencode "tokenCsrf=$TOKEN_OFICIAL_RF06C")
    registrar "POST /solicitudes/{id}/enviar (RF11C, todavía con comprobante)" 302 "$status"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir" > /dev/null
    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_RF06C"

    COOKIE_JAR_GERENTE_RF06C="$(temporal)"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" -X GET "$BASE_URL/acceso" > /dev/null
    TOKEN_GERENTE_RF06C="$(extraer_token_csrf)"
    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" -X POST "$BASE_URL/acceso" \
        --data-urlencode "nombreUsuario=$SGCPC_USUARIO" --data-urlencode "contrasena=$SGCPC_CONTRASENA" \
        --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06C" > /dev/null

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" \
        -X POST "$BASE_URL/aprobaciones/$ID_SOLICITUD_RF06C/aprobar" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06C")
    registrar "POST /aprobaciones/{id}/aprobar (RF11C, todavía con comprobante)" 302 "$status"

    # Degrada el comprobante DESPUÉS de aprobar (mismo mecanismo que el Caso B, ahora sobre
    # presenta_comprobante_ingreso) -- RF11 revalida con el dato ACTUAL al generar los
    # documentos de formalización. (aprobar() no se ve afectado: no revisa comprobante, punto 2
    # ya aprobado por Joel.)
    PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 -q -c \
        "UPDATE personas_laborales SET presenta_comprobante_ingreso = FALSE WHERE id_persona=$ID_CLIENTE_RF06C;" \
        > /dev/null 2>&1

    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" \
        -X POST "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_RF06C/generar" \
        --data-urlencode "primerVencimiento=$FECHA_PRIMER_VENCIMIENTO_OK" --data-urlencode "tokenCsrf=$TOKEN_GERENTE_RF06C")
    registrar "POST /formalizacion/nueva/{id}/generar (RF11C, sin comprobante -> bloqueado)" 302 "$status"
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" \
        -X GET "$BASE_URL/formalizacion/nueva/$ID_SOLICITUD_RF06C" > /dev/null
    # F2 (21/10/2026, pedido de Joel, punto 7): esta GET corre como GERENTE
    # (COOKIE_JAR_GERENTE_RF06C) -- ve la versión (b) al final del mensaje.
    verificar_texto "GET /formalizacion/nueva/{id} (RF11C, gerente: mensaje de bloqueo, versión (b))" \
        "Puede cancelar la solicitud desde Aprobación para evaluar una nueva cuando el cliente presente el comprobante."
    verificar_sql "SELECT: no se creó ningún préstamo para la solicitud RF06C (RF11C, bloqueo por comprobante)" \
        "SELECT count(*) FROM prestamos WHERE id_solicitud=$ID_SOLICITUD_RF06C" "0"

    curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_GERENTE_RF06C" -c "$COOKIE_JAR_GERENTE_RF06C" -X GET "$BASE_URL/salir" > /dev/null
    COOKIE_JAR="$COOKIE_JAR_ANTERIOR_RF06C"
else
    echo "== Bloque 6 (RF05/RF06/RF11, bloqueo del 40% al enviar/formalizar) omitido: defina SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba. ==" >&2
fi

# =====================================================================
# Bloque API movil (FASE 1, 12/11/2026, pedido de Joel)
#
# Los cinco casos que pidio Joel para la API que consume la aplicacion Flutter:
# login malo (401), cajera (403), expediente con token (200), sin token (401) y una
# carga de documento (201).
#
# ESCRITO SIN CORRER: lo corre Joel junto con el resto del humo. Si algun caso falla,
# aparece en la tabla final como cualquier otro.
#
# La API NO usa cookies ni token CSRF: cada pedido lleva "Authorization: Bearer xxx".
# Por eso aca no se usa COOKIE_JAR en ningun curl -- si alguno lo usara, estaria
# probando algo que la aplicacion movil no hace.
#
# El caso de carga usa la CEDULA a proposito: es el unico camino que ademas ejercita
# ServicioClientes.actualizarVencimientoCedula (el metodo angosto nuevo) y el orden
# "primero la fecha, despues el archivo". Con un archivo DISTINTO de los dos que ya
# subio la pasada principal, para no chocar con el rechazo de B42 (mismo tipo + mismo
# contenido).
# =====================================================================
if [ -n "${SGCPC_USUARIO_OFICIAL:-}" ] && [ -n "${SGCPC_CONTRASENA_OFICIAL:-}" ] && [ "$ID_CLIENTE" != "0" ]; then
    # API separada (12/11/2026, pedido del profesor): la API ya no vive dentro del .war del
    # sistema, vive en el suyo (/sgcpc-api). Se arma a partir de BASE_URL cambiandole el
    # contexto, asi con SGCPC_BASE_URL apuntando a otra maquina los dos van al mismo servidor;
    # y se puede forzar con SGCPC_API_URL si algun dia los dos .war no estan en el mismo Tomcat.
    RAIZ_HUMO="${BASE_URL%/}"
    API_URL="${SGCPC_API_URL:-${RAIZ_HUMO%/sgcpc}/sgcpc-api/api}"

    # --- Caso 0: las paginas de error del .war de la API ------------------------------
    # Lo encontro Joel el 12/11/2026 abriendo /sgcpc-api/ en el navegador: la raiz respondia
    # "Ocurrio un error inesperado" cuando lo que pasaba era un 404 limpio (la raiz no tiene
    # nada mapeado, a proposito). Un 404 tiene que decir que la direccion no existe: si no, el
    # primer dia que la app apunte a una ruta vieja, el mensaje manda a buscar un problema del
    # servidor donde no hay ninguno. Y el 405 ni llegaba a la pagina de error: caia en la
    # pantalla tecnica de Tomcat, que publica la version del servidor (Doc. 10 §10.18).
    # Van antes de todo lo demas porque no necesitan token ni base: si el .war desplegado no es
    # el que se armo, se ve aca.
    RAIZ_WAR_API="${API_URL%/api}"
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$RAIZ_WAR_API/")
    registrar "GET /sgcpc-api/ (la raiz del .war de la API)" 404 "$status"
    verificar_texto "GET /sgcpc-api/ (dice que la direccion no existe)" "no existe en esta API"
    verificar_texto_ausente "GET /sgcpc-api/ (no dice 'error inesperado')" "inesperado"

    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/ruta-inventada-del-humo")
    registrar "GET /api/{ruta inexistente}" 404 "$status"
    verificar_texto "GET /api/{ruta inexistente} (dice que la direccion no existe)" "no existe en esta API"

    # POST a una ruta que solo atiende GET: lo resuelve HttpServlet con un 405, y sin la
    # error-page propia la respuesta seria HTML con la version de Tomcat adentro.
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/panel")
    registrar "POST /api/panel (metodo no permitido)" 405 "$status"
    verificar_texto "POST /api/panel (responde JSON, no la pantalla de Tomcat)" '"error"'
    verificar_texto_ausente "POST /api/panel (no publica la version del servidor)" "Apache Tomcat"

    # --- Caso 1: contrasena incorrecta -> 401, con el mensaje unico de RNF09 ---------
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/sesion" \
        --data-urlencode "usuario=$SGCPC_USUARIO_OFICIAL" \
        --data-urlencode "contrasena=contrasena-incorrecta-del-humo")
    registrar "POST /api/sesion (contrasena incorrecta)" 401 "$status"
    # El mismo texto para los 5 casos que el sistema no distingue a proposito: si la API
    # agregara una palabra propia, estaria filtrando si la cuenta existe o esta bloqueada.
    verificar_texto "POST /api/sesion (mensaje unico de RNF09, textual del servicio)" \
        "Usuario o contraseña incorrectos."
    verificar_texto_ausente "POST /api/sesion (el error NO entrega ningun token)" '"token"'

    # --- Caso 2: sin token -> 401 en las dos rutas de datos -------------------------
    status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes?buscar=prueba")
    registrar "GET /api/clientes (sin token)" 401 "$status"
    verificar_texto_ausente "GET /api/clientes (sin token no devuelve ningun cliente)" '"clientes"'

    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/expediente")
    registrar "GET /api/clientes/{id}/expediente (sin token)" 401 "$status"

    # Un token inventado tampoco entra (el token se valida, no solo se exige su presencia).
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes?buscar=prueba" \
        -H "Authorization: Bearer token-inventado-del-humo")
    registrar "GET /api/clientes (token inventado)" 401 "$status"

    # ETAPA 2 (13/11/2026): el Inicio tampoco se ve sin token.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/pendientes")
    registrar "GET /api/pendientes (sin token)" 401 "$status"

    # ETAPA 3 (13/11/2026): el simulador tampoco, ni para leer los limites.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/simulador")
    registrar "GET /api/simulador (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/simulador" --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12")
    registrar "POST /api/simulador (sin token)" 401 "$status"

    # ETAPA 4 (13/11/2026): ninguna pestana de la ficha se ve sin token.
    for RUTA_FICHA in ficha solicitudes creditos; do
        status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/$RUTA_FICHA")
        registrar "GET /api/clientes/{id}/$RUTA_FICHA (sin token)" 401 "$status"
    done
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/creditos/1/cuotas")
    registrar "GET /api/creditos/{id}/cuotas (sin token)" 401 "$status"

    # ETAPA 5 (13/11/2026): el panel no exige NINGUN permiso (igual que /panel en el web,
    # que es la unica pantalla sin entrada en FiltroAutorizacion), pero SI exige token.
    # Esta verificacion es la que importa de ese cambio: "sin permiso" no es "sin sesion".
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/panel")
    registrar "GET /api/panel (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/panel" -H "Authorization: Bearer token-inventado")
    registrar "GET /api/panel (con un token inventado)" 401 "$status"

    # ETAPA 7 (13/11/2026): las dos rutas que ESCRIBEN. Sin token no se crea nada, y la
    # respuesta es 401 (sesion), no 403 (permiso): no se sabe de quien seria el permiso.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/solicitudes" \
        --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
        --data-urlencode "destinoCredito=Capital de trabajo" \
        --data-urlencode "primeraFechaVencimiento=2026-12-02")
    registrar "POST /api/clientes/{id}/solicitudes (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/solicitudes/1/enviar")
    registrar "POST /api/solicitudes/{id}/enviar (sin token)" 401 "$status"
    # Y una ruta de POST que no existe exige token igual antes de contestar 404: sin eso,
    # cualquiera podria averiguar que rutas tiene la API sin haber entrado nunca.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/prestamos")
    registrar "POST /api/clientes/{id}/prestamos (ruta inexistente, sin token: 401 antes que 404)" 401 "$status"

    # ETAPA 8 (13/11/2026): las aprobaciones del gerente. Nada de esto se ve sin sesion.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/aprobaciones")
    registrar "GET /api/aprobaciones (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/aprobaciones/1")
    registrar "GET /api/aprobaciones/{id} (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/1/aprobar")
    registrar "POST /api/aprobaciones/{id}/aprobar (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/1/rechazar" \
        --data-urlencode "idMotivoRechazo=5")
    registrar "POST /api/aprobaciones/{id}/rechazar (sin token)" 401 "$status"

    # ETAPA 9 (13/11/2026): la gestion de mora. Nada se ve ni se escribe sin sesion.
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/mora")
    registrar "GET /api/mora (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/mora/1/gestiones")
    registrar "GET /api/mora/{id}/gestiones (sin token)" 401 "$status"
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/mora/1/gestiones" \
        --data-urlencode "tipo=LLAMADA" --data-urlencode "resultado=prueba")
    registrar "POST /api/mora/{id}/gestiones (sin token)" 401 "$status"

    # --- Caso 3: la cajera ENTRA, y ve lo suyo -------------------------------------
    if [ -n "${SGCPC_USUARIO_CAJERA:-}" ] && [ -n "${SGCPC_CONTRASENA_CAJERA:-}" ]; then
        # La marca del ultimo id de auditoria ANTES de llamar a la API. Sin ella, contar por
        # ventana de tiempo cuenta tambien los accesos WEB de la pasada de cajera: en la
        # corrida del 12/11 dieron 5 y la verificacion fallo (1 esperado, 5 obtenido) sin que
        # la API tuviera nada malo. En la auditoria no hay ninguna columna que distinga un
        # acceso por la API de uno por el navegador -- a proposito: es el mismo evento del
        # mismo usuario. Lo que aisla el de la API es el id.
        AUDITORIA_ANTES_CAJERA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/sesion" \
            --data-urlencode "usuario=$SGCPC_USUARIO_CAJERA" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_CAJERA")
        # ETAPA 5 (13/11/2026, decision de Joel): hasta la ETAPA 4 esto daba 403 -- la
        # aplicacion era solo el expediente y la cajera no tiene ese permiso
        # (CARGAR_DOCUMENTOS_CONSULTA_BURO en NINGUNO). Con el Panel y el Simulador adentro,
        # rechazarla era negarle de entrada pantallas que el sistema web SI le da. Ahora
        # entra, y lo que puede hacer se decide pedido por pedido.
        registrar "POST /api/sesion (cajera: ENTRA, igual que al sistema web)" 200 "$status"
        verificar_texto "POST /api/sesion (cajera: dice su perfil)" '"perfil":"CAJERO"'
        # El nivel de cada permiso viaja para que la app no le ofrezca lo que le va a rebotar.
        verificar_texto "POST /api/sesion (cajera: expediente en NINGUNO, para esconder Mis pendientes)" \
            '"expediente":"NINGUNO"'
        verificar_texto "POST /api/sesion (cajera: clientes en CONSULTA, puede buscar y ver la ficha)" \
            '"clientes":"CONSULTA"'
        verificar_texto "POST /api/sesion (cajera: cotizar en TOTAL, tiene el simulador)" \
            '"cotizar":"TOTAL"'
        # ETAPA 9: se afirma la PRESENCIA del NINGUNO, no la ausencia del TOTAL. Una verificacion
        # por ausencia que mire el cuerpo equivocado pasa sin poder fallar; esta, si el nivel
        # cambiara, falla fuerte.
        verificar_texto "POST /api/sesion (cajera: mora en NINGUNO, no gestiona mora)" \
            '"mora":"NINGUNO"'
        verificar_sql "SELECT: el acceso de la cajera POR LA API quedo auditado (ACCESO_CONCEDIDO)" \
            "SELECT count(*) FROM auditoria_operaciones ao JOIN usuarios u ON u.id_usuario = ao.id_usuario WHERE u.nombre_usuario='$SGCPC_USUARIO_CAJERA' AND ao.operacion='ACCESO_CONCEDIDO' AND ao.id_auditoria > ${AUDITORIA_ANTES_CAJERA:-0}" \
            "1"
        TOKEN_API_CAJERA="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"
        if [ -z "$TOKEN_API_CAJERA" ]; then
            registrar "POST /api/sesion (cajera: entrega un token)" "con-token" "sin-token"
        else
            registrar "POST /api/sesion (cajera: entrega un token)" "con-token" "con-token"

            # EL caso de la ETAPA 5: con su token, la cajera ve SU panel -- el mismo que ve
            # al entrar al sistema web. Lo decide el servidor (TarjetasPanelApi, con las
            # condiciones copiadas de panel.jsp y su JUnit), no la aplicacion.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/panel" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/panel (cajera: tiene panel, sin pedirle ningun permiso)" 200 "$status"
            verificar_texto "GET panel (cajera: su caja)" '"efectivoEnCaja"'
            verificar_texto "GET panel (cajera: lo cobrado hoy)" '"cobrosHoy"'
            verificar_texto "GET panel (cajera: lo que vence hoy)" '"vencenHoy"'
            verificar_texto "GET panel (cajera: la mora, que en el web tampoco lleva condicion)" '"prestamosEnMora"'
            verificar_texto "GET panel (cajera: los desembolsos pendientes, que son su trabajo)" '"desembolsosPendientes"'
            # Y lo que NO le corresponde: los numeros de direccion. En el web no los ve
            # (panel.jsp los envuelve en if (esGerentePanel)), asi que acá tampoco pueden
            # viajar -- ni escondidos en el JSON, que es lo que pasaria si la app filtrara.
            verificar_texto_ausente "GET panel (cajera: NO viaja la cartera en la calle)" '"carteraEnLaCalle"'
            verificar_texto_ausente "GET panel (cajera: NO viaja lo cobrado en el mes)" '"cobradoMes"'
            verificar_texto_ausente "GET panel (cajera: NO viaja lo que el gerente tiene que aprobar)" '"solicitudesPendientesAprobar"'
            verificar_texto_ausente "GET panel (cajera: NO viajan las reversiones por autorizar)" '"reversionesPorAutorizar"'
            verificar_texto_ausente "GET panel (cajera: NO viajan las diferencias de caja por revisar)" '"diferenciasDeCajaSinRevisar"'
            # Tampoco las dos del oficial.
            verificar_texto_ausente "GET panel (cajera: NO viaja Mis solicitudes en curso)" '"misSolicitudesEnCurso"'
            verificar_texto_ausente "GET panel (cajera: NO viajan los pagares por devolver)" '"cierresDocumentalesPendientes"'

            # Que entre no le da el expediente: cada pedido sigue validando SU permiso.
            # Estas tres son la razon por la que abrir el login no abrio nada mas.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/pendientes" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/pendientes (cajera: 403, no tiene el permiso del expediente)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/expediente" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/clientes/{id}/expediente (cajera: 403)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/creditos" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/clientes/{id}/creditos (cajera: 403, VER_REPORTES en NINGUNO)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/clientes/{id}/solicitudes (cajera: 403, GESTIONAR_SOLICITUD en NINGUNO)" 403 "$status"
            # Lo que SI puede: buscar un cliente y ver su ficha (clientes en CONSULTA, igual
            # que /clientes en el web), y el simulador (COTIZAR en TOTAL).
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes?buscar=$CEDULA_PRUEBA" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/clientes?buscar= (cajera: 200, clientes en CONSULTA)" 200 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/ficha" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/clientes/{id}/ficha (cajera: 200, es la unica pestana que ve)" 200 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/simulador" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/simulador (cajera: 200, COTIZAR en TOTAL)" 200 "$status"
            # Y que CONSULTA no alcanza para escribir: cargar un documento exige TOTAL.
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/documentos" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA" -F "codigoTipo=CEDULA_IDENTIDAD")
            registrar "POST /api/clientes/{id}/documentos (cajera: 403, leer no es escribir)" 403 "$status"

            # ETAPA 7 (13/11/2026): la cajera NO puede crear una solicitud. Es la verificacion
            # que pidio Joel, y la que importa de esta etapa: la aplicacion abre una accion que
            # escribe en la base, y la puerta es la misma matriz de siempre.
            # GESTIONAR_SOLICITUD es NINGUNO para la cajera, y un POST exige TOTAL.
            SOLICITUDES_ANTES_CAJERA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM solicitudes_credito" 2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA" \
                --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "destinoCredito=Capital de trabajo" \
                --data-urlencode "primeraFechaVencimiento=2026-12-02")
            registrar "POST /api/clientes/{id}/solicitudes (cajera: 403, GESTIONAR_SOLICITUD en NINGUNO)" 403 "$status"
            verificar_texto "POST solicitudes (cajera: dice que no es su atribucion)" \
                "Su perfil no tiene"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/solicitudes/1/enviar" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "POST /api/solicitudes/{id}/enviar (cajera: 403)" 403 "$status"
            # Y lo que de verdad hay que comprobar de un 403 en una ruta que escribe: que NO
            # escribio. Un 403 que ya hizo el INSERT es peor que no tener permisos.
            SOLICITUDES_DESPUES_CAJERA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM solicitudes_credito" 2>/dev/null | tr -d '[:space:]')"
            registrar "SELECT: el 403 de la cajera no creo ninguna solicitud" \
                "$SOLICITUDES_ANTES_CAJERA" "$SOLICITUDES_DESPUES_CAJERA"

            # ETAPA 8: la cajera no decide creditos. APROBAR_RECHAZAR_SOLICITUD en NINGUNO,
            # asi que ni ve la bandeja.
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/aprobaciones" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/aprobaciones (cajera: 403, ni la ve)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/1/aprobar" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "POST /api/aprobaciones/{id}/aprobar (cajera: 403)" 403 "$status"

            # ETAPA 9: la cajera NO gestiona mora. REGISTRAR_GESTION_COBRANZA en NINGUNO:
            # ni ve la lista. Ella cobra, que es la otra mitad del circuito -- y el reparto de
            # esos dos permisos es lo que mantiene separadas las dos tareas.
            status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/mora" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "GET /api/mora (cajera: 403, ni la ve)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/mora/1/gestiones" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA" \
                --data-urlencode "tipo=LLAMADA" --data-urlencode "resultado=prueba")
            registrar "POST /api/mora/{id}/gestiones (cajera: 403)" 403 "$status"

            # Cierra su sesion, para no dejar un token vivo en el almacen.
            status=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API_URL/sesion" \
                -H "Authorization: Bearer $TOKEN_API_CAJERA")
            registrar "DELETE /api/sesion (cajera)" 204 "$status"
        fi
    else
        registrar "PRECONDICION no dada (API movil): sin SGCPC_USUARIO_CAJERA no se verifico el panel de la cajera" \
            "sin-probar" "sin-probar"
    fi

    # --- Token valido del oficial ----------------------------------------------------
    curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/sesion" \
        --data-urlencode "usuario=$SGCPC_USUARIO_OFICIAL" \
        --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" > /dev/null
    # El token sale del JSON con sed (no hay jq en esta maquina). Nunca se imprime.
    TOKEN_API="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"
    if [ -z "$TOKEN_API" ]; then
        registrar "POST /api/sesion (oficial: entrega un token)" "con-token" "sin-token"
    else
        registrar "POST /api/sesion (oficial: entrega un token)" "con-token" "con-token"
        verificar_texto "POST /api/sesion (dice cuando vence la sesion)" '"vence"'
        verificar_texto "POST /api/sesion (dice el perfil, para que la app arme su menu)" \
            '"perfil":"OFICIAL_CREDITO"'
        # ETAPA 9: el nivel de /mora, para que la app sepa si le corresponde ese destino. Va ACA,
        # pegado al login, porque verificar_texto lee $CUERPO_TMP: unas lineas mas abajo ese
        # archivo ya tiene el cuerpo de otro pedido.
        verificar_texto "POST /api/sesion (oficial: mora en TOTAL, es el que sale a la calle)" \
            '"mora":"TOTAL"'

        # --- Caso 4: buscar y leer el expediente, con token -------------------------
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes?buscar=$CEDULA_PRUEBA" \
            -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes?buscar={cedula} (con token)" 200 "$status"
        verificar_texto "GET /api/clientes (encuentra al cliente del humo por su C.I.)" \
            "$CEDULA_PRUEBA_CON_PUNTOS"

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/expediente" \
            -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes/{id}/expediente (con token)" 200 "$status"
        # Las 4 filas del expediente, en el orden del tramite (B90).
        verificar_texto "GET expediente (fila de la autorizacion del buro)" '"codigo":"AUTORIZACION_CONSULTA_BURO"'
        verificar_texto "GET expediente (fila de la cedula)" '"codigo":"CEDULA_IDENTIDAD"'
        verificar_texto "GET expediente (fila del comprobante de ingreso)" '"codigo":"COMPROBANTE_INGRESO"'
        verificar_texto "GET expediente (fila del comprobante de domicilio)" '"codigo":"COMPROBANTE_DOMICILIO"'
        verificar_texto "GET expediente (el contador sale de la unica lista de obligatorios: 4)" '"obligatorios":4'
        # Decision de Joel (opcion a): la autorizacion se MUESTRA pero no se carga desde la app.
        verificar_texto "GET expediente (la autorizacion del buro es de solo lectura en la app)" \
            '"notaCarga":"Se carga en el sistema web."'

        # --- ETAPA 2 (13/11/2026): GET /api/pendientes ------------------------------
        # Los clientes con el expediente incompleto, el que mas le falta primero.
        # Solo lectura: no escribe nada en la base.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/pendientes" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/pendientes (con token)" 200 "$status"
        verificar_texto "GET /api/pendientes (trae el total, que es el del resumen del Inicio)" '"total"'
        verificar_texto "GET /api/pendientes (trae la lista)" '"pendientes"'
        verificar_texto "GET /api/pendientes (dice su tope de filas)" '"tope":50'
        # La verificacion que de verdad prueba algo: el cliente de la pasada principal tiene
        # los CUATRO obligatorios cargados (comprobante de domicilio, cedula, comprobante de
        # ingreso y la autorizacion firmada con su consentimiento), asi que NO puede estar en
        # pendientes. Si apareciera, significaria que las lecturas EN BLOQUE de ETAPA 2 no
        # coinciden con las de a una que usa la ficha web -- que es justo el error que este
        # endpoint podria introducir, y el unico que no se ve a ojo.
        verificar_texto_ausente "GET /api/pendientes (el cliente con el expediente completo NO aparece)" "\"cliente\":{\"id\":$ID_CLIENTE,"
        # Y, para que un fallo de arriba se pueda diagnosticar sin repetir nada a mano:
        # cuantos tipos obligatorios VIGENTES tiene ese cliente (1 cedula, 2 ingreso,
        # 3 domicilio, 4 autorizacion).
        verificar_sql "SELECT: el cliente de la pasada tiene sus 4 obligatorios VIGENTES" "SELECT count(DISTINCT id_tipo_documento) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND estado_documento='VIGENTE' AND id_tipo_documento IN (1,2,3,4)" "4"

        # --- Caso 5: cargar la cedula con su vencimiento ----------------------------
        ARCHIVO_API="$(temporal --suffix=.pdf)"
        printf '%%PDF-1.4 prueba de humo SGCPC -- foto de la cedula desde la app movil' > "$ARCHIVO_API"
        ARCHIVO_API_WIN="$(cygpath -w "$ARCHIVO_API")"
        VENCIMIENTO_API="$(date -d '+4 years' +%Y-%m-%d 2>/dev/null || echo 2030-12-31)"
        # El id del escaneo de cedula VIGENTE antes de la carga (vacio si no hay ninguno) y la
        # marca de auditoria: con los dos se puede verificar despues sobre el documento EXACTO
        # que creo la API, en vez de contar filas por ventana de tiempo.
        ID_DOC_CEDULA_ANTES="$(obtener_id_documento_vigente "$ID_CLIENTE" 1)"
        AUDITORIA_ANTES_CARGA="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/documentos" \
            -H "Authorization: Bearer $TOKEN_API" \
            -F "idTipoDocumento=1" \
            -F "fechaVencimientoCedula=$VENCIMIENTO_API" \
            -F "archivo=@$ARCHIVO_API_WIN;type=application/pdf;filename=cedula_desde_la_app.pdf")
        # 201: se creo una version nueva del documento. Sin token CSRF en ningun campo.
        registrar "POST /api/clientes/{id}/documentos (cedula + vencimiento, con token)" 201 "$status"
        verificar_texto "POST documentos (avisa que quedo cargado)" "Documento cargado."
        # La respuesta trae el expediente YA recalculado, asi que la app no supone nada.
        verificar_texto "POST documentos (devuelve el expediente recalculado)" '"items"'
        # OJO con lo que NO se verifica aca, y por que (hallazgo del 12/11/2026, PREEXISTENTE,
        # registrado y pendiente de la decision de Joel): en el camino de "renovacion" de la
        # cedula --cambiar el vencimiento y recien despues subir el escaneo-- la version NO
        # sigue la serie: vuelve a 1 y id_documento_reemplazado queda NULL. El motivo es que el
        # cambio de fecha marca REEMPLAZADO el escaneo vigente ANTES de que llegue el archivo
        # nuevo, asi que cargarDocumento no encuentra ningun VIGENTE de ese tipo y lo trata
        # como una primera carga. Pasa IGUAL por el sistema web: en esta misma corrida, el
        # documento 1923 subido por /documentos/cargar quedo en "v1, CARGAR_DOCUMENTO". No lo
        # introdujo la API. Hasta que se decida, se verifica lo que de verdad pasa: la carga
        # creo un escaneo NUEVO y es el unico vigente.
        ID_DOC_CEDULA_DESPUES="$(obtener_id_documento_vigente "$ID_CLIENTE" 1)"
        if [ -n "$ID_DOC_CEDULA_DESPUES" ] && [ "$ID_DOC_CEDULA_DESPUES" != "$ID_DOC_CEDULA_ANTES" ]; then
            registrar "La carga por la API creo un escaneo NUEVO de la cedula" "nuevo" "nuevo"
        else
            registrar "La carga por la API creo un escaneo NUEVO de la cedula" "nuevo" "sin-cambio"
        fi
        verificar_sql "SELECT: queda UN solo escaneo VIGENTE de la cedula (el anterior quedo REEMPLAZADO)" \
            "SELECT count(*) FROM documentos_expediente WHERE id_persona=$ID_CLIENTE AND id_tipo_documento=1 AND estado_documento='VIGENTE'" \
            "1"
        verificar_sql "SELECT: el vencimiento de la cedula quedo guardado (metodo angosto nuevo)" \
            "SELECT fecha_vencimiento_cedula FROM personas WHERE id_persona=$ID_CLIENTE" \
            "$VENCIMIENTO_API"
        # Sobre EL documento que acaba de crear la API, no por ventana de tiempo: la pasada
        # principal sube decenas de documentos con el mismo usuario (el conteo por tiempo dio
        # 91 en la corrida del 12/11), asi que esa verificacion no probaba lo que decia.
        verificar_sql "SELECT: la carga quedo auditada con el usuario del TOKEN, no con uno enviado por el cliente" \
            "SELECT u.nombre_usuario FROM auditoria_operaciones ao JOIN usuarios u ON u.id_usuario = ao.id_usuario WHERE ao.tipo_entidad='DOCUMENTO_EXPEDIENTE' AND ao.id_entidad=${ID_DOC_CEDULA_DESPUES:-0} AND ao.operacion IN ('CARGAR_DOCUMENTO','NUEVA_VERSION_DOCUMENTO')" \
            "$SGCPC_USUARIO_OFICIAL"
        # Y que la API no haya dejado NINGUNA operacion a nombre de otro usuario.
        verificar_sql "SELECT: la API no dejo ninguna operacion a nombre de otro usuario" \
            "SELECT count(*) FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_CARGA:-0} AND id_usuario <> (SELECT id_usuario FROM usuarios WHERE nombre_usuario='$SGCPC_USUARIO_OFICIAL')" \
            "0"

        # Repetir la MISMA carga la rechaza (B42: mismo tipo, mismo contenido) -- la regla del
        # servicio vale igual por la API, no hay un camino mas permisivo.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/documentos" \
            -H "Authorization: Bearer $TOKEN_API" \
            -F "idTipoDocumento=1" \
            -F "archivo=@$ARCHIVO_API_WIN;type=application/pdf;filename=cedula_otra_vez.pdf")
        registrar "POST /api/clientes/{id}/documentos (el mismo archivo otra vez, B42)" 422 "$status"
        verificar_texto "POST documentos (B42 por la API: mismo mensaje que en la pantalla)" \
            "Ese archivo ya está cargado."

        # Un archivo que no es un PDF real tampoco pasa (ajuste C), y NO toca la fecha: la
        # validacion corre antes de escribir nada.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE/documentos" \
            -H "Authorization: Bearer $TOKEN_API" \
            -F "idTipoDocumento=1" \
            -F "fechaVencimientoCedula=2031-01-31" \
            -F "archivo=@$ARCHIVO_TXT_COMO_PDF_WIN;type=application/pdf;filename=falso.pdf")
        registrar "POST /api/clientes/{id}/documentos (archivo con la extension cambiada)" 422 "$status"
        verificar_sql "SELECT: el archivo rechazado NO cambio el vencimiento de la cedula" \
            "SELECT fecha_vencimiento_cedula FROM personas WHERE id_persona=$ID_CLIENTE" \
            "$VENCIMIENTO_API"

        # --- ETAPA 7 (13/11/2026): la solicitud nueva, cargada desde la visita -------
        # LA etapa que escribe. Se usa un cliente PROPIO, recien creado: el de la pasada
        # principal ya tiene un prestamo abierto, y RN-01/RN-02 rechaza una solicitud nueva
        # para quien tiene un compromiso abierto -- con razon. El helper lo deja completo
        # (los cuatro documentos obligatorios, la autorizacion del buro y su consulta sin
        # mora), que es lo que hace falta para que el envio a aprobacion tambien se pueda
        # probar de verdad y no solo por su rechazo.
        #
        # Sesion web propia para crearlo (misma leccion que la comparacion con el Cotizador):
        # el $COOKIE_JAR global, a esta altura del script, ya fue cerrado por las pasadas
        # intermedias.
        COOKIE_JAR_ANTES_ETAPA7="$COOKIE_JAR"
        COOKIE_JAR="$(temporal)"
        curl -s -o "$CUERPO_TMP" -c "$COOKIE_JAR" "$BASE_URL/acceso" > /dev/null
        TOKEN_CSRF_ETAPA7="$(extraer_token_csrf)"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
            -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_ETAPA7")
        registrar "POST /acceso (oficial, sesion propia de la ETAPA 7)" 302 "$status"
        # El token sigue siendo el que se leyo de /acceso, igual que en las demas pasadas
        # (TOKEN_CSRF_OFICIAL). Aca habia una relectura desde /clientes que lo dejaba VACIO: esa
        # pagina es una lista, no tiene formulario, asi que no trae ningun tokenCsrf -- medido:
        # /clientes 0 tokenCsrf, /clientes/nuevo 2. Con el token vacio, los 8 POST del helper
        # daban 403 y el bloque entero fallaba sin que el sistema tuviera nada malo.
        CEDULA_ETAPA7="${CEDULA_PRUEBA}22"
        ID_PERSONA_CREADA=""
        crear_persona_con_expediente "$CEDULA_ETAPA7" "Cliente" "Prueba Humo App Movil" \
            "6.000.000" "$TOKEN_CSRF_ETAPA7"
        ID_CLIENTE_ETAPA7="$ID_PERSONA_CREADA"
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
        registrar "GET /salir (la sesion propia de la ETAPA 7)" 302 "$status"
        COOKIE_JAR="$COOKIE_JAR_ANTES_ETAPA7"

        if [ -z "${ID_CLIENTE_ETAPA7:-}" ] || [ "$ID_CLIENTE_ETAPA7" = "0" ]; then
            registrar "PRECONDICION no dada (ETAPA 7): sin cliente propio no se pudo probar el alta de solicitud" \
                "sin-probar" "sin-probar"
        else
            # La fecha que propone el servidor es la que el servidor acepta.
            curl -s -o "$CUERPO_TMP" -X GET "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API" > /dev/null
            FECHA_ETAPA7="$(sed -n 's/.*"sugerida":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"

            # Primero los rechazos, ANTES de crear la buena: asi se comprueba que un rechazo
            # no deja nada a medio escribir (si creara, la siguiente fallaria por RN-02).
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE_ETAPA7/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "monto=99.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "destinoCredito=Capital de trabajo" \
                --data-urlencode "primeraFechaVencimiento=$FECHA_ETAPA7")
            registrar "POST solicitudes (monto fuera de rango)" 422 "$status"
            verificar_texto "POST solicitudes (el mensaje es el del sistema, no uno propio)" "El monto"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE_ETAPA7/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "destinoCredito=" \
                --data-urlencode "primeraFechaVencimiento=$FECHA_ETAPA7")
            registrar "POST solicitudes (sin destino)" 422 "$status"
            verificar_texto "POST solicitudes (pide el destino con el texto del sistema)" "Ingrese el destino del crédito."

            # Y ahora la buena. El monto va CON puntos de miles, como lo escribe el campo de
            # la aplicacion y como lo acepta el campo web (ConversorNumeros).
            AUDITORIA_ANTES_ETAPA7="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE_ETAPA7/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "destinoCredito=Capital de trabajo" \
                --data-urlencode "primeraFechaVencimiento=$FECHA_ETAPA7")
            registrar "POST /api/clientes/{id}/solicitudes (oficial: la crea)" 200 "$status"
            verificar_texto "POST solicitudes (el aviso dice el codigo SOL-)" '"aviso":"Solicitud SOL-'
            verificar_texto "POST solicitudes (dice cual es la nueva, para destacarla)" '"idNueva"'
            verificar_texto "POST solicitudes (devuelve la lista ya recalculada)" '"solicitudes"'
            verificar_texto "POST solicitudes (la nueva viene en BORRADOR, igual que el alta web)" '"estado":"BORRADOR"'
            verificar_texto "POST solicitudes (el monto formateado por el servidor)" '"montoTexto":"5.000.000 Gs."'
            verificar_texto "POST solicitudes (el destino, normalizado y guardado)" 'Capital de trabajo'
            ID_SOLICITUD_ETAPA7="$(sed -n 's/.*"idNueva":\([0-9]*\).*/\1/p' "$CUERPO_TMP")"
            # El CODIGO (SOL-AAAA-NNNN), para la bandeja del gerente de la ETAPA 8. Se saca del
            # aviso, que lo trae textual. No se usa la cedula para buscar la solicitud en esa
            # bandeja: el servidor la manda FORMATEADA CON PUNTOS (construirFilaBandeja usa
            # FormateadorTexto.comoCedulaConPuntos), asi que buscar la cruda no la encuentra
            # nunca -- y lo peor es que la verificacion por AUSENCIA pasaba por ese mismo motivo,
            # sin poder fallar. El codigo viaja igual en los dos lados.
            CODIGO_ETAPA7="$(sed -n 's/.*"aviso":"Solicitud \([^ ]*\) creada.*/\1/p' "$CUERPO_TMP")"

            # Lo que de verdad prueba que "aparece al instante en /solicitudes del sistema web":
            # la fila esta en la base, de ese cliente, en BORRADOR y firmada por el oficial que
            # entro por la API.
            verificar_sql "SELECT: la solicitud existe, es de ese cliente y quedo en BORRADOR" \
                "SELECT count(*) FROM solicitudes_credito WHERE id_cliente = $ID_CLIENTE_ETAPA7 AND estado_solicitud = 'BORRADOR'" \
                "1"
            verificar_sql "SELECT: la creo el usuario que entro por la aplicacion, no un usuario de sistema" \
                "SELECT count(*) FROM solicitudes_credito s JOIN usuarios u ON u.id_usuario = s.creado_por WHERE s.id_cliente = $ID_CLIENTE_ETAPA7 AND u.nombre_usuario = '$SGCPC_USUARIO_OFICIAL'" \
                "1"
            verificar_sql "SELECT: el codigo sigue el numerador del sistema (SOL-AAAA-NNNN)" \
                "SELECT count(*) FROM solicitudes_credito WHERE id_cliente = $ID_CLIENTE_ETAPA7 AND codigo_solicitud ~ '^SOL-[0-9]{4}-[0-9]{4}$'" \
                "1"
            verificar_sql "SELECT: el alta quedo auditada como CREAR_SOLICITUD" \
                "SELECT count(*) FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_ETAPA7:-0} AND operacion = 'CREAR_SOLICITUD'" \
                "1"
            # API separada (12/11/2026): la fila de auditoria tiene que tener IP y navegador.
            # ServicioAuditoria no los recibe por parametro: los lee de ContextoPeticion, un
            # ThreadLocal que en el sistema web llena FiltroAutenticacion y que en el .war de la
            # API llena FiltroContextoApi. Si ese filtro no esta mapeado en el web.xml de
            # sgcpc-api, el alta funciona igual y la auditoria queda ciega -- sin que nada avise.
            # Su JUnit prueba el contrato del filtro; ESTA verificacion es la que falla si el
            # filtro esta desconectado, porque mira la fila de verdad en la base.
            verificar_sql "SELECT: esa auditoria de la API guardo IP y navegador (filtro de contexto)" \
                "SELECT (direccion_ip IS NOT NULL AND navegador LIKE 'curl/%') FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_ETAPA7:-0} AND operacion = 'CREAR_SOLICITUD'" \
                "t"
            # La simulacion se guarda con el alta (las columnas *_simulado son NOT NULL): la
            # cuota de la solicitud la calculo el mismo motor, no la aplicacion.
            verificar_sql "SELECT: la solicitud quedo con su cuota simulada, calculada por el servidor" \
                "SELECT count(*) FROM solicitudes_credito WHERE id_cliente = $ID_CLIENTE_ETAPA7 AND cuota_simulada > 0" \
                "1"

            # RN-02: una segunda solicitud para el mismo cliente se rechaza, y el mensaje es el
            # del sistema (decision de Joel: se muestra tal cual, con el codigo de la abierta).
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/clientes/$ID_CLIENTE_ETAPA7/solicitudes" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "destinoCredito=Otro destino" \
                --data-urlencode "primeraFechaVencimiento=$FECHA_ETAPA7")
            registrar "POST solicitudes (la segunda para el mismo cliente: RN-02)" 422 "$status"
            # El texto es el de ServicioSolicitudes.mensajeCompromisoAbierto, TEXTUAL. Ojo: NO
            # nombra el codigo de la solicitud abierta -- el que lo nombra es el pre-chequeo del
            # controlador web, que arma un enlace y que la API no copia a proposito (un enlace
            # HTML no sirve en una aplicacion). En la app el codigo igual esta a la vista: la
            # pestana Solicitudes muestra la lista con la abierta y su estado.
            verificar_texto "POST solicitudes (RN-02, el mensaje del servicio tal cual)"                 "El cliente ya tiene una solicitud abierta."
            verificar_sql "SELECT: el rechazo de RN-02 no creo una segunda solicitud" \
                "SELECT count(*) FROM solicitudes_credito WHERE id_cliente = $ID_CLIENTE_ETAPA7" \
                "1"

            # --- Enviar a aprobacion (el mismo boton de la ficha web) ---
            if [ -z "${ID_SOLICITUD_ETAPA7:-}" ]; then
                registrar "PRECONDICION no dada (ETAPA 7): sin id de la solicitud no se pudo probar el envio" \
                    "sin-probar" "sin-probar"
            else
                AUDITORIA_ANTES_ENVIO="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                    "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/solicitudes/$ID_SOLICITUD_ETAPA7/enviar" \
                    -H "Authorization: Bearer $TOKEN_API")
                registrar "POST /api/solicitudes/{id}/enviar (oficial: la envia)" 200 "$status"
                verificar_texto "POST enviar (el aviso dice que ahora la revisa el gerente)" "la revisa el gerente"
                verificar_texto "POST enviar (devuelve el estado que de verdad quedo)" '"estadoTexto"'
                verificar_sql "SELECT: la solicitud paso a PENDIENTE_APROBACION" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'PENDIENTE_APROBACION'" \
                    "1"
                verificar_sql "SELECT: quedo la fecha de envio a aprobacion" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND fecha_envio_aprobacion IS NOT NULL" \
                    "1"
                verificar_sql "SELECT: el envio quedo auditado como ENVIAR_SOLICITUD_APROBACION" \
                    "SELECT count(*) FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_ENVIO:-0} AND operacion = 'ENVIAR_SOLICITUD_APROBACION'" \
                    "1"
                # Enviarla de nuevo se rechaza: ya no esta en BORRADOR. Es el mismo
                # validarEstadoBorrador de la ficha web -- y lo que evita que dos toques
                # seguidos en la tablet manden la misma solicitud dos veces.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/solicitudes/$ID_SOLICITUD_ETAPA7/enviar" \
                    -H "Authorization: Bearer $TOKEN_API")
                registrar "POST enviar (otra vez: ya no esta en BORRADOR)" 422 "$status"
                verificar_sql "SELECT: el segundo envio no cambio nada" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'PENDIENTE_APROBACION'" \
                    "1"
                # Una solicitud que no existe: 422 con el texto del sistema, no un 500.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/solicitudes/999999999/enviar" \
                    -H "Authorization: Bearer $TOKEN_API")
                registrar "POST enviar (una solicitud que no existe)" 422 "$status"
                verificar_texto "POST enviar (lo dice con el texto del sistema)" "La solicitud no existe."
            fi
        fi

        # --- ETAPA 9 (13/11/2026): la gestion de mora, desde la calle ----------------
        # El oficial (TOKEN_API) es quien gestiona la mora. Se usa el prestamo EN MORA que
        # el Bloque 4 ya creo para sus propias pruebas (D-119: nunca reusa ni toca los
        # escenarios de demostracion), que es el que aparece en /mora.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/mora" \
            -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/mora (oficial: los clientes en mora)" 200 "$status"
        verificar_texto "GET mora (trae la lista y el total)" '"enMora"'
        verificar_texto "GET mora (los dias de atraso)" '"diasAtraso"'
        verificar_texto "GET mora (el tramo, que ya calcula el servidor)" '"tramo"'
        verificar_texto "GET mora (el monto exigible, formateado por el servidor)" '"montoExigibleTexto"'
        # El telefono viaja a proposito: en la calle, antes de ir hasta el domicilio, se llama.
        verificar_texto "GET mora (el telefono del cliente)" '"telefono"'
        # Lo que NO tiene que viajar: nada del buro en esta pantalla.
        verificar_texto_ausente "GET mora (NO viaja el puntaje del buro)" '"puntaje"'
        ID_PRESTAMO_MORA="$(sed -n 's/.*"idPrestamo":\([0-9]*\).*/\1/p' "$CUERPO_TMP" | head -1)"

        if [ -z "${ID_PRESTAMO_MORA:-}" ]; then
            registrar "PRECONDICION no dada (ETAPA 9): no hay ningun prestamo en mora para gestionar" \
                "sin-probar" "sin-probar"
        else
            # El historial, antes de tocar nada.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X GET "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API")
            registrar "GET /api/mora/{id}/gestiones (el historial)" 200 "$status"
            verificar_texto "GET gestiones (trae la lista)" '"gestiones"'

            # Los rechazos primero, para comprobar que un rechazo no deja nada escrito.
            GESTIONES_ANTES="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo = $ID_PRESTAMO_MORA" 2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=LLAMADA" --data-urlencode "resultado=")
            registrar "POST gestiones (sin resultado)" 422 "$status"
            verificar_texto "POST gestiones (el texto del sistema para el dato faltante)" \
                "Ingrese el resultado de la gestión."
            # Con fecha de promesa y sin monto: el servicio lo rechaza (RF24).
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=LLAMADA" --data-urlencode "resultado=Dijo que paga" \
                --data-urlencode "fechaPromesa=$(date -d '+3 days' +%Y-%m-%d 2>/dev/null || date -v+3d +%Y-%m-%d)")
            registrar "POST gestiones (promesa sin monto)" 422 "$status"
            verificar_texto "POST gestiones (pide el monto prometido, con el texto del sistema)" \
                "ingrese el monto prometido"
            # EL control de esta etapa: la app NO ofrece visita, y la API tampoco la acepta.
            # VISITA_LOCAL significa que el cliente VINO al local; usarla para una visita al
            # domicilio pondria un dato falso en el informe de gestiones.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=VISITA_LOCAL" --data-urlencode "resultado=Fui a la casa")
            registrar "POST gestiones (VISITA_LOCAL: la API no la acepta)" 422 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=PAGO_PARCIAL" --data-urlencode "resultado=x")
            registrar "POST gestiones (PAGO_PARCIAL: lo genera solo el cobro)" 422 "$status"
            verificar_sql "SELECT: ninguno de los rechazos escribio una gestion" \
                "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo = $ID_PRESTAMO_MORA" \
                "${GESTIONES_ANTES:-0}"

            # Y la buena: una llamada con promesa, que es el caso real del oficial.
            AUDITORIA_ANTES_GESTION="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"
            # La marca del ultimo id de gestion, por el mismo motivo que la de auditoria: el humo
            # no limpia lo que crea, asi que la SEGUNDA corrida del dia encuentra la gestion de la
            # primera -- mismo prestamo, mismo texto, misma promesa -- y los cuatro SELECT de abajo
            # devuelven 2 en vez de 1. Paso el 12/11/2026. Lo que aisla la fila de ESTA corrida es
            # el id, no el texto.
            ID_GESTION_ANTES="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT coalesce(max(id_gestion_cobranza), 0) FROM gestiones_cobranza" 2>/dev/null | tr -d '[:space:]')"
            FECHA_PROMESA="$(date -d '+3 days' +%Y-%m-%d 2>/dev/null || date -v+3d +%Y-%m-%d)"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=LLAMADA" \
                --data-urlencode "resultado=Visita al domicilio: hable con la hermana, el cliente no estaba" \
                --data-urlencode "excusa=Dijo que esta sin trabajo desde octubre" \
                --data-urlencode "fechaPromesa=$FECHA_PROMESA" \
                --data-urlencode "montoPrometido=500.000")
            registrar "POST /api/mora/{id}/gestiones (oficial: la registra)" 200 "$status"
            verificar_texto "POST gestiones (devuelve el historial ya recalculado)" '"gestiones"'
            verificar_texto "POST gestiones (la gestion nueva, con su resultado)" "hable con la hermana"
            verificar_texto "POST gestiones (la promesa, con el monto formateado)" '"promesaMontoTexto":"500.000 Gs."'

            # Lo que de verdad prueba que "la oficina lo ve al instante": la fila esta en la
            # base, con su tipo, su promesa y el usuario que la registro.
            verificar_sql "SELECT: la gestion quedo guardada como LLAMADA" \
                "SELECT count(*) FROM gestiones_cobranza WHERE id_gestion_cobranza > ${ID_GESTION_ANTES:-0} AND id_prestamo = $ID_PRESTAMO_MORA AND tipo = 'LLAMADA' AND resultado LIKE '%hable con la hermana%'" \
                "1"
            verificar_sql "SELECT: la registro el usuario que entro por la aplicacion" \
                "SELECT count(*) FROM gestiones_cobranza g JOIN usuarios u ON u.id_usuario = g.registrado_por WHERE g.id_gestion_cobranza > ${ID_GESTION_ANTES:-0} AND g.id_prestamo = $ID_PRESTAMO_MORA AND u.nombre_usuario = '$SGCPC_USUARIO_OFICIAL'" \
                "1"
            verificar_sql "SELECT: la promesa quedo con su fecha y su monto" \
                "SELECT count(*) FROM gestiones_cobranza WHERE id_gestion_cobranza > ${ID_GESTION_ANTES:-0} AND id_prestamo = $ID_PRESTAMO_MORA AND fecha_promesa = '$FECHA_PROMESA' AND monto_prometido = 500000" \
                "1"
            # La persona contactada es el CLIENTE del prestamo: la columna es NOT NULL con FK
            # a personas, y un familiar no esta registrado. La API lo resuelve con los mismos
            # dos servicios que el controlador web.
            verificar_sql "SELECT: la persona contactada es el cliente del prestamo" \
                "SELECT count(*) FROM gestiones_cobranza g JOIN prestamos p ON p.id_prestamo = g.id_prestamo JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud WHERE g.id_gestion_cobranza > ${ID_GESTION_ANTES:-0} AND g.id_prestamo = $ID_PRESTAMO_MORA AND g.id_persona_contactada = s.id_cliente" \
                "1"
            verificar_sql "SELECT: quedo auditada como REGISTRAR_GESTION_COBRANZA" \
                "SELECT count(*) FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_GESTION:-0} AND operacion = 'REGISTRAR_GESTION_COBRANZA'" \
                "1"
            # Lo mismo que en el alta de solicitud, en el otro servlet que escribe: dos puntos
            # distintos del .war de la API con la misma pregunta, porque el filtro es uno solo
            # (mapeado a /*) y se cae para todos a la vez.
            verificar_sql "SELECT: esa auditoria de la API guardo IP y navegador (filtro de contexto)" \
                "SELECT (direccion_ip IS NOT NULL AND navegador LIKE 'curl/%') FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_GESTION:-0} AND operacion = 'REGISTRAR_GESTION_COBRANZA'" \
                "t"

            # Y que la oficina la ve: la misma gestion aparece en /mora del sistema web.
            # Sesion web propia, por la leccion de la comparacion con el Cotizador.
            COOKIE_JAR_ANTES_MORA="$COOKIE_JAR"
            COOKIE_JAR="$(temporal)"
            curl -s -o "$CUERPO_TMP" -c "$COOKIE_JAR" "$BASE_URL/acceso" > /dev/null
            TOKEN_CSRF_MORA="$(extraer_token_csrf)"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
                -X POST "$BASE_URL/acceso" \
                --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
                --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_MORA")
            registrar "POST /acceso (oficial, sesion propia de la ETAPA 9)" 302 "$status"
            # B197 (15/11/2026, aprobado por Joel): esta verificacion pedia el listado PELADO y
            # buscaba el texto de la gestion en la fila de ESE prestamo. Con /mora paginado de a 20
            # eso deja de ser seguro, y por DOS motivos -- el segundo no es obvio:
            #   1. el listado ahora tiene paginas, y la fila puede caer fuera de la primera;
            #   2. la gestion que acaba de registrar la app TRAE UNA PROMESA con fecha futura, asi
            #      que el prestamo deja de tener "promesa vencida" y SALE del grupo que va primero
            #      (ServicioMora.construirFilas: promesaVencida = la promesa abierta vence antes de
            #      hoy). O sea que la propia operacion que se esta verificando mueve la fila.
            # Por eso se entra FILTRANDO por el N.o de operacion de ese prestamo: un solo resultado,
            # siempre en la pagina 1, y no depende de ningun orden. Sigue verificando lo mismo --
            # que la oficina ve en la columna "Ultima gestion" lo que se cargo desde la calle.
            OPERACION_MORA_APP="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                "SELECT identificador_operacion FROM prestamos WHERE id_prestamo=$ID_PRESTAMO_MORA" \
                2>/dev/null | tr -d '[:space:]')"
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
                -X GET "$BASE_URL/mora?texto=$OPERACION_MORA_APP")
            registrar "GET /mora?texto= (la pantalla web, tras la gestion de la app)" 200 "$status"
            verificar_texto "GET /mora?texto= (la oficina ve la gestion que se registro desde la app)" \
                "hable con la hermana"
            status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/salir")
            registrar "GET /salir (la sesion propia de la ETAPA 9)" 302 "$status"
            COOKIE_JAR="$COOKIE_JAR_ANTES_MORA"

            # Un prestamo que no existe: 422 con el texto del sistema, no un 500.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' \
                -X POST "$API_URL/mora/999999999/gestiones" \
                -H "Authorization: Bearer $TOKEN_API" \
                --data-urlencode "tipo=LLAMADA" --data-urlencode "resultado=prueba")
            registrar "POST gestiones (un prestamo que no existe)" 422 "$status"
            verificar_texto "POST gestiones (lo dice con el texto del sistema)" "El préstamo no existe."
            # Y una ruta de POST que no existe.
            status=$(curl -s -o /dev/null -w '%{http_code}' \
                -X POST "$API_URL/mora/$ID_PRESTAMO_MORA/cobrar" \
                -H "Authorization: Bearer $TOKEN_API")
            registrar "POST /api/mora/{id}/cobrar (no esta en la API)" 404 "$status"
        fi

        # --- ETAPA 8 (13/11/2026): la decision del gerente ---------------------------
        # Cierra el circuito: la solicitud que acaba de crear y enviar la ETAPA 7 es la que
        # se decide aca. Se prueban las dos puntas: que el OFICIAL no pueda decidir la suya
        # (separacion de funciones) y que el GERENTE si, con su propia sesion de API.
        if [ -z "${ID_SOLICITUD_ETAPA7:-}" ]; then
            registrar "PRECONDICION no dada (ETAPA 8): sin la solicitud de la ETAPA 7 no se pudo probar la decision" \
                "sin-probar" "sin-probar"
        else
            # EL control de esta etapa: el oficial cargo y envio la solicitud, pero NO la
            # decide. Es la misma separacion que mantiene el sistema web, y la aplicacion no
            # la puede aflojar -- menos todavia desde el telefono del mismo que la cargo.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/aprobaciones" \
                -H "Authorization: Bearer $TOKEN_API")
            registrar "GET /api/aprobaciones (oficial: 403, ni ve la bandeja)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/aprobar" \
                -H "Authorization: Bearer $TOKEN_API")
            registrar "POST aprobar (oficial: 403, no decide su propia solicitud)" 403 "$status"
            status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/rechazar" \
                -H "Authorization: Bearer $TOKEN_API" --data-urlencode "idMotivoRechazo=5")
            registrar "POST rechazar (oficial: 403)" 403 "$status"
            verificar_sql "SELECT: los 403 del oficial no decidieron nada" \
                "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'PENDIENTE_APROBACION'" \
                "1"

            # Ahora si, la sesion de API del GERENTE (SGCPC_USUARIO es el gerente).
            curl -s -o "$CUERPO_TMP" -X POST "$API_URL/sesion" \
                --data-urlencode "usuario=$SGCPC_USUARIO" \
                --data-urlencode "contrasena=$SGCPC_CONTRASENA" > /dev/null
            TOKEN_API_GERENTE="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"
            if [ -z "$TOKEN_API_GERENTE" ]; then
                registrar "POST /api/sesion (gerente: entrega un token)" "con-token" "sin-token"
            else
                registrar "POST /api/sesion (gerente: entrega un token)" "con-token" "con-token"
                verificar_texto "POST /api/sesion (gerente: aprobaciones en TOTAL)" '"aprobaciones":"TOTAL"'
                verificar_texto "POST /api/sesion (gerente: solicitudes en NINGUNO, no las carga)" '"solicitudes":"NINGUNO"'

                # La bandeja: tiene que estar la solicitud que la ETAPA 7 acaba de enviar.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/aprobaciones" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "GET /api/aprobaciones (gerente: la bandeja)" 200 "$status"
                verificar_texto "GET aprobaciones (trae la lista y el total)" '"pendientes"'
                if [ -z "${CODIGO_ETAPA7:-}" ]; then
                    registrar "PRECONDICION no dada (ETAPA 8): sin el codigo de la solicitud no se pudo buscarla en la bandeja" \
                        "sin-probar" "sin-probar"
                else
                    verificar_texto "GET aprobaciones (esta la solicitud que acaba de enviar el oficial)" \
                        "\"codigo\":\"$CODIGO_ETAPA7\""
                fi
                verificar_texto "GET aprobaciones (dice cuanto lleva esperando)" '"esperando"'
                verificar_texto "GET aprobaciones (la afectacion, ya formateada por el servidor)" '"afectacionTexto"'

                # El detalle con el que decide.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "GET /api/aprobaciones/{id} (gerente: el detalle)" 200 "$status"
                verificar_texto "GET detalle (el cliente con su C.I.)" '"cedula"'
                verificar_texto "GET detalle (la cuota que calculo el motor al enviar)" '"cuotaTexto"'
                verificar_texto "GET detalle (la afectacion CON su limite)" '"limiteAfectacionTexto"'
                verificar_texto "GET detalle (el buro)" '"buro"'
                verificar_texto "GET detalle (los motivos de rechazo de la tabla del sistema)" '"motivosRechazo"'
                verificar_texto "GET detalle (un motivo que exige detalle viene marcado)" '"requiereDetalle":true'
                # El cliente de la ETAPA 7 se creo con la consulta del buro SIN mora, asi que
                # el aviso no tiene que decir que hay mora.
                verificar_texto "GET detalle (sin mora en el buro)" '"hayMora":false'

                # Rechazar SIN motivo: RN-06. Lo rechaza el servicio, con su texto.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/rechazar" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "POST rechazar (sin motivo: RN-06)" 422 "$status"
                verificar_texto "POST rechazar (el texto del sistema, no uno propio)" "Seleccione el motivo del rechazo."
                verificar_sql "SELECT: el rechazo sin motivo no cambio el estado" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'PENDIENTE_APROBACION'" \
                    "1"
                # Un motivo que exige detalle, sin detalle: tambien lo rechaza el servicio.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/rechazar" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE" --data-urlencode "idMotivoRechazo=7")
                registrar "POST rechazar (motivo 'Otro' sin detalle)" 422 "$status"
                verificar_texto "POST rechazar (dice que ese motivo requiere detalle)" "requiere un detalle"

                # Y la aprobacion de verdad.
                AUDITORIA_ANTES_APROBAR="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
                    "SELECT coalesce(max(id_auditoria), 0) FROM auditoria_operaciones" 2>/dev/null | tr -d '[:space:]')"
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/aprobar" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "POST /api/aprobaciones/{id}/aprobar (gerente: la aprueba)" 200 "$status"
                verificar_texto "POST aprobar (el aviso dice que sigue la formalizacion)" "se formaliza en Formalización"
                verificar_texto "POST aprobar (devuelve el estado que de verdad quedo)" '"estado":"APROBADA"'
                verificar_sql "SELECT: la solicitud quedo APROBADA" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'APROBADA'" \
                    "1"
                verificar_sql "SELECT: la decidio el GERENTE que entro por la aplicacion, no el oficial" \
                    "SELECT count(*) FROM solicitudes_credito s JOIN usuarios u ON u.id_usuario = s.decidido_por WHERE s.id_solicitud = $ID_SOLICITUD_ETAPA7 AND u.nombre_usuario = '$SGCPC_USUARIO'" \
                    "1"
                verificar_sql "SELECT: quedo la fecha de la decision" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND fecha_decision IS NOT NULL" \
                    "1"
                verificar_sql "SELECT: el monto y el plazo aprobados quedaron guardados" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND monto_aprobado IS NOT NULL AND plazo_aprobado_meses IS NOT NULL" \
                    "1"
                verificar_sql "SELECT: la aprobacion quedo auditada como APROBAR_SOLICITUD" \
                    "SELECT count(*) FROM auditoria_operaciones WHERE id_auditoria > ${AUDITORIA_ANTES_APROBAR:-0} AND operacion = 'APROBAR_SOLICITUD'" \
                    "1"

                # Aprobarla otra vez: ya la decidio alguien. Es el caso de las dos pantallas
                # abiertas -- y desde esta etapa, el de la tablet y el navegador a la vez.
                status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/aprobar" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "POST aprobar (otra vez: ya fue decidida)" 422 "$status"
                verificar_sql "SELECT: el segundo intento no cambio nada" \
                    "SELECT count(*) FROM solicitudes_credito WHERE id_solicitud = $ID_SOLICITUD_ETAPA7 AND estado_solicitud = 'APROBADA'" \
                    "1"
                # Y la aprobada ya NO esta en la bandeja.
                curl -s -o "$CUERPO_TMP" -X GET "$API_URL/aprobaciones" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE" > /dev/null
                if [ -z "${CODIGO_ETAPA7:-}" ]; then
                    registrar "PRECONDICION no dada (ETAPA 8): sin el codigo no se pudo verificar que salio de la bandeja" \
                        "sin-probar" "sin-probar"
                else
                    verificar_texto_ausente "GET aprobaciones (la ya aprobada salio de la bandeja)" \
                        "\"codigo\":\"$CODIGO_ETAPA7\""
                fi

                # Una ruta de POST que no existe: 404, no una decision por descarte.
                status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/aprobaciones/$ID_SOLICITUD_ETAPA7/cancelar" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "POST /api/aprobaciones/{id}/cancelar (no esta en la API)" 404 "$status"

                status=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API_URL/sesion" \
                    -H "Authorization: Bearer $TOKEN_API_GERENTE")
                registrar "DELETE /api/sesion (gerente)" 204 "$status"
            fi
        fi

        # --- ETAPA 5 (13/11/2026): GET /api/panel, el Inicio de la aplicacion -------
        # El mismo panel que este usuario ve al entrar al sistema web. Es el UNICO
        # endpoint de la aplicacion que escribe, y a proposito: calcularIndicadores
        # arranca con ServicioMora.recalcularMorosidadDiaria, el mismo mantenimiento que
        # dispara /panel y /mora del web (PO-15 paso 1). Idempotente y auditado; si la
        # aplicacion lo salteara, la tablet mostraria una mora vieja.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/panel" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/panel (oficial, con token)" 200 "$status"
        verificar_texto "GET panel (saluda con la hora del servidor, no la de la tablet)" '"saludo":"Buen'
        verificar_texto "GET panel (dice el perfil, para la chapita del Inicio)" '"perfilEtiqueta":"Oficial de crédito"'
        verificar_texto "GET panel (el bloque Hoy)" '"titulo":"Hoy"'
        verificar_texto "GET panel (el bloque Cartera)" '"titulo":"Cartera"'
        verificar_texto "GET panel (el bloque Pendientes)" '"titulo":"Pendientes"'
        # Las siete tarjetas del oficial, las mismas que le pinta panel.jsp.
        verificar_texto "GET panel (oficial: cobros de hoy)" '"cobrosHoy"'
        verificar_texto "GET panel (oficial: vencen hoy)" '"vencenHoy"'
        verificar_texto "GET panel (oficial: aprobadas para formalizar, B45)" '"aprobadasParaFormalizar"'
        verificar_texto "GET panel (oficial: promesas de hoy)" '"promesasDeHoy"'
        verificar_texto "GET panel (oficial: prestamos en mora)" '"prestamosEnMora"'
        verificar_texto "GET panel (oficial: mis solicitudes en curso)" '"misSolicitudesEnCurso"'
        verificar_texto "GET panel (oficial: pagares pendientes de devolucion, B76)" '"cierresDocumentalesPendientes"'
        # Y las que NO son suyas. La de la caja es la que mas importa: el oficial no ve
        # el efectivo en el web (if (esCajeroPanel || esGerentePanel)), y el dinero en
        # caja no es un dato que convenga pasear en una tablet.
        verificar_texto_ausente "GET panel (oficial: NO viaja el efectivo en caja)" '"efectivoEnCaja"'
        verificar_texto_ausente "GET panel (oficial: NO viaja la cartera en la calle)" '"carteraEnLaCalle"'
        verificar_texto_ausente "GET panel (oficial: NO viaja lo cobrado en la semana)" '"cobradoSemana"'
        verificar_texto_ausente "GET panel (oficial: NO viajan las diferencias de caja)" '"diferenciasDeCajaSinRevisar"'
        # Los colores son los del CSS del sistema web: la tablet no tiene su propia paleta.
        verificar_texto "GET panel (el azul de la casa, el del CSS)" '"#0C325C"'
        verificar_texto "GET panel (el rojo es SOLO la mora, igual que en el web)" '"#991B1B"'
        # El texto de la tarjeta viene armado por el servidor, con el singular/plural de
        # la JSP: la aplicacion lo muestra tal cual y no puede escribirlo distinto.
        verificar_texto "GET panel (el texto ya armado, como en panel.jsp)" ' de hoy, aplicado a la deuda ('
        # El recalculo de mora que dispara el panel es IDEMPOTENTE, y esto lo comprueba:
        # se cuentan los prestamos EN_MORA despues del primer pedido, se pide el panel
        # otra vez, y se vuelven a contar. Tienen que dar lo mismo. Es la verificacion que
        # justifica la unica escritura de toda la aplicacion: abrir el Inicio dos veces no
        # puede cambiarle el estado a ningun prestamo.
        MORA_TRAS_EL_PRIMER_PANEL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT count(*) FROM prestamos WHERE estado_prestamo = 'EN_MORA'" 2>/dev/null | tr -d '[:space:]')"
        status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/panel" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/panel (pedirlo dos veces sigue dando 200)" 200 "$status"
        MORA_TRAS_EL_SEGUNDO_PANEL="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
            "SELECT count(*) FROM prestamos WHERE estado_prestamo = 'EN_MORA'" 2>/dev/null | tr -d '[:space:]')"
        registrar "SELECT: abrir el panel dos veces NO cambia ningun estado (el recalculo es idempotente)" \
            "$MORA_TRAS_EL_PRIMER_PANEL" "$MORA_TRAS_EL_SEGUNDO_PANEL"

        # --- ETAPA 4 (13/11/2026): la ficha del cliente, pestana por pestana --------
        # Las cuatro son de SOLO LECTURA y cada una exige el permiso de su pantalla en el
        # sistema web. El oficial tiene las cuatro.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/ficha" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes/{id}/ficha (Datos, con token)" 200 "$status"
        verificar_texto "GET ficha (los datos personales)" '"persona"'
        verificar_texto "GET ficha (el cliente del humo, por su nombre)" "Humo Http"
        verificar_texto "GET ficha (la C.I. con puntos, igual que la ficha web)" "$CEDULA_PRUEBA_CON_PUNTOS"
        verificar_texto "GET ficha (el domicilio armado por el mismo metodo que los PDF)" '"textoCompleto"'
        verificar_texto "GET ficha (la situacion laboral)" '"laboral"'
        verificar_texto "GET ficha (las referencias)" '"referencias"'
        # Lo que NO tiene que viajar: nada del buro. Es un dato sensible que esa pestana
        # del sistema web muestra en OTRA pestana, con otro permiso, y que no se pidio para
        # la aplicacion -- en una tablet que se lleva a la calle, un dato de mas no es una
        # comodidad.
        verificar_texto_ausente "GET ficha (NO viaja el puntaje del buro)" '"puntaje"'
        verificar_texto_ausente "GET ficha (NO viaja la clasificacion del buro)" '"clasificacion"'

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/solicitudes" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes/{id}/solicitudes (con token)" 200 "$status"
        verificar_texto "GET solicitudes (trae la lista y el total)" '"total"'

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/clientes/$ID_CLIENTE/creditos" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes/{id}/creditos (con token)" 200 "$status"
        verificar_texto "GET creditos (trae la lista)" '"creditos"'
        # LA verificacion de esta pestana: el filtro del informe busca la C.I. con LIKE, asi
        # que puede traer los creditos de otro cliente cuya C.I. CONTENGA a esta; el resultado
        # se vuelve a filtrar por igualdad exacta. Se compara el total que devolvio la API
        # contra los creditos que ese cliente tiene en la base: si sobra uno, se colo el de
        # otra persona; si falta, el filtro exacto se llevo uno propio.
        TOTAL_CREDITOS_API="$(grep -oE '"total":[0-9]+' "$CUERPO_TMP" | head -1 | cut -d: -f2)"
        verificar_sql "SELECT: la API devolvio EXACTAMENTE los creditos de este cliente (ni de otro, ni de menos)" "SELECT count(*) FROM prestamos p JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud WHERE s.id_cliente = $ID_CLIENTE" "${TOTAL_CREDITOS_API:-sin-dato}"

        # El plan de pagos de un credito: el mismo que arma el Estado de cuenta del web.
        if [ -n "${ID_PRESTAMO:-}" ] && [ "$ID_PRESTAMO" != "0" ]; then
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/creditos/$ID_PRESTAMO/cuotas" -H "Authorization: Bearer $TOKEN_API")
            registrar "GET /api/creditos/{id}/cuotas (con token)" 200 "$status"
            verificar_texto "GET cuotas (el resumen de pagadas/vencidas/pendientes)" '"pendientes"'
            verificar_texto "GET cuotas (la cuota 1)" '"numero":1'
            # El estado viene ROTULADO por el servidor (Pagada/Parcial/Vencida/Pendiente).
            # "Vencida" no existe en la base: lo decide la misma regla que la mora.
            verificar_texto "GET cuotas (el estado ya rotulado, no el de la tabla)" '"estado":"'
            verificar_texto_ausente "GET cuotas (NO viaja el PENDIENTE crudo de la base)" '"estado":"PENDIENTE"'
        else
            registrar "PRECONDICION no dada (ETAPA 4): sin ID_PRESTAMO no se pudo pedir el plan de pagos" "sin-probar" "sin-probar"
        fi

        # --- ETAPA 3 (13/11/2026): el simulador de credito ---------------------------
        # Reutiliza ServicioCotizacion.cotizar, el MISMO metodo de la pantalla del
        # Cotizador. No persiste nada.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X GET "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/simulador (limites, con token)" 200 "$status"
        verificar_texto "GET /api/simulador (trae el monto minimo y maximo)" '"maximoTexto"'
        verificar_texto "GET /api/simulador (trae el rango de la primera fecha)" '"sugerida"'
        # RF01-E: el texto corto del glosario sale del servidor, no de una copia en la app.
        verificar_texto "GET /api/simulador (trae el texto corto de TEA)" '"sigla":"TEA"'
        # La fecha que propone el servidor es la que el servidor acepta: se usa esa.
        FECHA_SIM="$(sed -n 's/.*"sugerida":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"

        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API" --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" --data-urlencode "fechaPrimerVencimiento=$FECHA_SIM")
        registrar "POST /api/simulador (5.000.000 a 12 meses)" 200 "$status"
        verificar_texto "POST /api/simulador (la cuota es la PRIMERA, con IVA)" '"primeraConIva"'
        verificar_texto "POST /api/simulador (el total a pagar)" '"conIvaTexto"'
        verificar_texto "POST /api/simulador (las tres tasas)" '"cftTexto"'
        verificar_texto "POST /api/simulador (el plan de pagos, con la cuota 12)" '"numero":12'
        # §5: todo importe principal lleva su monto en letras.
        verificar_texto "POST /api/simulador (la cuota lleva su monto en letras)" '"primeraConIvaLetras"'
        # La cuota que devolvio la API, para compararla contra la del sistema web.
        CUOTA_API="$(sed -n 's/.*"primeraConIvaTexto":"\([^"]*\)".*/\1/p' "$CUERPO_TMP")"

        # LA verificacion de esta etapa: el MISMO monto, plazo y fecha por la pantalla del
        # Cotizador tienen que dar la MISMA cuota. Si difieren, en algun lado se duplico una
        # formula -- que es exactamente lo que esta etapa no debia hacer. Se compara el texto
        # ya formateado, asi tambien se verifica que el formato sea el mismo (un solo dueno).
        #
        # SESION WEB PROPIA (arreglo del 12/11/2026, aprobado por Joel). Antes esto reusaba
        # $COOKIE_JAR y $TOKEN_CSRF, tomados al principio del script: cuando la corrida llega
        # hasta aca, 5.000 lineas despues, esa sesion ya fue cerrada por las pasadas
        # intermedias (los GET /salir del oficial, del administrador y del gerente), asi que el
        # POST daba 403 y devolvia una pagina SIN ningun monto. La verificacion lo leia como
        # "la cuota no esta" y reportaba un FALLO que parecia una formula duplicada en el
        # sistema -- cuando las dos cuotas eran identicas (verificado a mano: 483.199 Gs. por
        # las dos vias). Mismo patron que las otras pasadas del humo: jar propio, y el
        # tokenCsrf leido de la pantalla ya con la sesion abierta.
        COOKIE_JAR_COTIZADOR_API="$(temporal)"
        curl -s -o "$CUERPO_TMP" -c "$COOKIE_JAR_COTIZADOR_API" "$BASE_URL/acceso" > /dev/null
        TOKEN_CSRF_COTIZADOR_API="$(extraer_token_csrf)"
        curl -s -o /dev/null -b "$COOKIE_JAR_COTIZADOR_API" -c "$COOKIE_JAR_COTIZADOR_API" \
            -X POST "$BASE_URL/acceso" \
            --data-urlencode "nombreUsuario=$SGCPC_USUARIO_OFICIAL" \
            --data-urlencode "contrasena=$SGCPC_CONTRASENA_OFICIAL" \
            --data-urlencode "tokenCsrf=$TOKEN_CSRF_COTIZADOR_API" > /dev/null
        curl -s -o "$CUERPO_TMP" -b "$COOKIE_JAR_COTIZADOR_API" -c "$COOKIE_JAR_COTIZADOR_API" \
            "$BASE_URL/cotizador" > /dev/null
        TOKEN_CSRF_COTIZADOR_API="$(extraer_token_csrf)"
        if [ -z "$CUOTA_API" ]; then
            registrar "PRECONDICION no dada (ETAPA 3): la API no devolvio la cuota, no se pudo comparar con el Cotizador web" "sin-comparar" "sin-comparar"
        else
            # El codigo HTTP del pedido web se REGISTRA antes de comparar. Sin esto, un pedido
            # que falla y una cuota que difiere se ven igual en la tabla, y significan cosas
            # opuestas: lo primero es un problema del humo, lo segundo seria del sistema.
            status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -b "$COOKIE_JAR_COTIZADOR_API" \
                -c "$COOKIE_JAR_COTIZADOR_API" -X POST "$BASE_URL/cotizador" \
                --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12" \
                --data-urlencode "fechaPrimerVencimiento=$FECHA_SIM" \
                --data-urlencode "tokenCsrf=$TOKEN_CSRF_COTIZADOR_API")
            registrar "POST /cotizador (la misma cotizacion por la pantalla web, para comparar)" 200 "$status"
            verificar_texto "La cuota del simulador es IDENTICA a la del Cotizador web ($CUOTA_API)" "$CUOTA_API"
        fi
        # Se cierra la sesion de la comparacion: el humo no deja sesiones abiertas detras.
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR_COTIZADOR_API" \
            -c "$COOKIE_JAR_COTIZADOR_API" -X GET "$BASE_URL/salir")
        registrar "GET /salir (la sesion de la comparacion con el Cotizador)" 302 "$status"

        # Un monto fuera de rango se rechaza con el MISMO mensaje que la pantalla web.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API" --data-urlencode "monto=99.000.000" --data-urlencode "plazoMeses=12" --data-urlencode "fechaPrimerVencimiento=$FECHA_SIM")
        registrar "POST /api/simulador (monto fuera de rango)" 422 "$status"
        verificar_texto "POST /api/simulador (el mensaje es el del sistema, no uno propio)" "El monto"

        # Y un plazo fuera de rango, idem.
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API" --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=99" --data-urlencode "fechaPrimerVencimiento=$FECHA_SIM")
        registrar "POST /api/simulador (plazo fuera de rango)" 422 "$status"

        # Sin fecha: el mensaje dice que hacer, no "parametro invalido".
        status=$(curl -s -o "$CUERPO_TMP" -w '%{http_code}' -X POST "$API_URL/simulador" -H "Authorization: Bearer $TOKEN_API" --data-urlencode "monto=5.000.000" --data-urlencode "plazoMeses=12")
        registrar "POST /api/simulador (sin la fecha)" 422 "$status"
        verificar_texto "POST /api/simulador (dice que hay que elegir la fecha)" "Elegí la fecha estimada del primer vencimiento."

        # --- Cierre de sesion: el token deja de valer -------------------------------
        status=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API_URL/sesion" \
            -H "Authorization: Bearer $TOKEN_API")
        registrar "DELETE /api/sesion (cierre desde la app)" 204 "$status"
        status=$(curl -s -o /dev/null -w '%{http_code}' -X GET "$API_URL/clientes?buscar=prueba" \
            -H "Authorization: Bearer $TOKEN_API")
        registrar "GET /api/clientes (con el token ya cerrado)" 401 "$status"
    fi
else
    echo "== Bloque API movil omitido: defina SGCPC_USUARIO_OFICIAL/SGCPC_CONTRASENA_OFICIAL en ~/.sgcpc_prueba (y hace falta el cliente de prueba de la pasada principal). ==" >&2
fi

# ---------------------------------------------------------------------
# Tabla final
# ---------------------------------------------------------------------
echo
printf '%-70s %-10s %-10s %s\n' "VERIFICACIÓN" "ESPERADO" "OBTENIDO" "OK"
printf '%-70s %-10s %-10s %s\n' "----------------------------------------------------------------" "--------" "--------" "--"
for fila in "${RESULTADOS[@]}"; do
    IFS='|' read -r descripcion esperado obtenido estado <<< "$fila"
    printf '%-70s %-10s %-10s %s\n' "$descripcion" "$esperado" "$obtenido" "$estado"
done

echo
if [ "$FALLOS" -eq 0 ]; then
    echo "== TODO OK (${#RESULTADOS[@]} verificaciones) =="
    exit 0
else
    echo "== $FALLOS de ${#RESULTADOS[@]} verificaciones FALLARON =="
    exit 1
fi
