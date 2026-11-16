#!/usr/bin/env bash
# SGCPC - restaurar_prueba.sh (D-112, Bloque 7/E10, 7.2)
# Restaura un respaldo de bd/respaldar.sh en la base de PRUEBA "sgcpc_db_restaurada" (o la que se
# pase como 2do argumento) -- NUNCA en sgcpc_db, el script se niega a correr si el destino
# coincide con ese nombre. La base de prueba la crea Joel una sola vez a mano (mismo dueño
# sgcpc_user, UTF8, igual que sgcpc_db, D-12: sgcpc_user no puede crear ni borrar bases) -- este
# script solo le vacia el contenido (DROP SCHEMA/CREATE SCHEMA, mismo patron que
# recrear_base.sh) y lo vuelve a llenar; nunca toca sgcpc_db salvo para LEER (las verificaciones
# de abajo comparan contra ella, sin modificarla).
#
# Acepta el respaldo en formato custom (.backup, con pg_restore --no-owner --role=sgcpc_user
# para que no falle por dueños al restaurar en otra base) o SQL plano (.sql, con psql) -- se
# detecta por la extension del archivo.
#
# Uso, desde sistema/:
#   source ./entorno.sh && bash bd/restaurar_prueba.sh <archivo .backup o .sql> [base_prueba]
set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPIEDADES="$CARPETA/../src/main/resources/configuracion/bd.properties"
ARCHIVO_BACKUP="${1:-}"
BASE_PRUEBA="${2:-sgcpc_db_restaurada}"

if [ -z "$ARCHIVO_BACKUP" ] || [ ! -f "$ARCHIVO_BACKUP" ]; then
    echo "Uso: bash bd/restaurar_prueba.sh <archivo .backup o .sql> [base_prueba]" >&2
    exit 1
fi

# Guarda D-75: nunca se restaura sobre sgcpc_db, ni sobre sgcpc_db_pruebas (D-113: esa es solo
# del humo, esta solo es para probar restauraciones -- no se mezclan).
if [ "$BASE_PRUEBA" = "sgcpc_db" ] || [ "$BASE_PRUEBA" = "sgcpc_db_pruebas" ]; then
    echo "== ABORTADO: nunca se restaura sobre '$BASE_PRUEBA'. ==" >&2
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
BD_NOMBRE_REAL="$(echo "$BD_URL" | sed -E 's#.*/([^/?]+).*#\1#')"

if [ -z "$PGPASSWORD" ]; then
    echo "bd.contrasena esta vacia en $PROPIEDADES." >&2
    exit 1
fi

echo "== Vaciando '$BASE_PRUEBA' (host $BD_HOST:$BD_PUERTO) =="
psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -v ON_ERROR_STOP=1 \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

echo "== Restaurando $ARCHIVO_BACKUP en '$BASE_PRUEBA' =="
case "$ARCHIVO_BACKUP" in
    *.backup)
        pg_restore -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" \
            --no-owner --role="$BD_USUARIO" "$ARCHIVO_BACKUP"
        ;;
    *.sql)
        psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -v ON_ERROR_STOP=1 \
            -f "$ARCHIVO_BACKUP"
        ;;
    *)
        echo "Extension no reconocida (se espera .backup o .sql): $ARCHIVO_BACKUP" >&2
        exit 1
        ;;
esac

echo ""
echo "== Verificando la restauracion contra '$BD_NOMBRE_REAL' (solo lectura) =="
FALLOS=0

verificar_conteo() {
    local tabla="$1" real prueba
    real="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE_REAL" -tAc "SELECT count(*) FROM $tabla")"
    prueba="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -tAc "SELECT count(*) FROM $tabla")"
    if [ "$real" = "$prueba" ]; then
        echo "  OK    $tabla: $prueba filas"
    else
        echo "  FALLO $tabla: real=$real prueba=$prueba"
        FALLOS=$((FALLOS + 1))
    fi
}

# Todas las tablas de public, descubiertas -- no una lista escrita a mano (mismo motivo que el
# descubrimiento de secuencias de más abajo: que nunca falte una, ni ahora ni si el esquema
# suma alguna tabla más el día de mañana).
TABLAS="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE_REAL" -qtAc \
    "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")"
for tabla in $TABLAS; do
    verificar_conteo "$tabla"
done

verificar_suma() {
    local descripcion="$1" consulta="$2" real prueba
    real="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE_REAL" -tAc "$consulta")"
    prueba="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -tAc "$consulta")"
    if [ "$real" = "$prueba" ]; then
        echo "  OK    $descripcion: $prueba"
    else
        echo "  FALLO $descripcion: real=$real prueba=$prueba"
        FALLOS=$((FALLOS + 1))
    fi
}

verificar_suma "suma de desembolsos (monto_neto_entregado)" \
    "SELECT COALESCE(SUM(monto_neto_entregado),0) FROM desembolsos"
verificar_suma "suma de pagos (monto_aplicado)" \
    "SELECT COALESCE(SUM(monto_aplicado),0) FROM pagos"
verificar_suma "suma de movimientos de caja (monto*signo)" \
    "SELECT COALESCE(SUM(monto*signo),0) FROM movimientos_caja"

echo ""
echo "== Verificando que las secuencias quedaron al dia (last_value >= MAX(id)) =="
# Descubre cada secuencia y la tabla/columna que numera via pg_depend (todos los id_* de este
# esquema son GENERATED ALWAYS AS IDENTITY, con su secuencia "owned" automatica) -- generico, no
# depende de una lista escrita a mano.
DESALINEADAS="$(psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BASE_PRUEBA" -qtAc "
    CREATE TEMP TABLE _verificacion_secuencias (secuencia text, tabla text, columna text, ultimo_valor bigint, id_maximo bigint);
    DO \$\$
    DECLARE
        r RECORD;
        v_max bigint;
        v_ultimo bigint;
    BEGIN
        FOR r IN
            SELECT seq_ns.nspname AS esquema, seq.relname AS secuencia, tab.relname AS tabla, attr.attname AS columna
            FROM pg_class seq
            JOIN pg_namespace seq_ns ON seq_ns.oid = seq.relnamespace
            JOIN pg_depend dep ON dep.objid = seq.oid AND dep.deptype = 'a'
            JOIN pg_class tab ON tab.oid = dep.refobjid
            JOIN pg_attribute attr ON attr.attrelid = tab.oid AND attr.attnum = dep.refobjsubid
            WHERE seq.relkind = 'S'
        LOOP
            EXECUTE format('SELECT COALESCE(MAX(%I),0) FROM %I', r.columna, r.tabla) INTO v_max;
            EXECUTE format('SELECT last_value FROM %I.%I', r.esquema, r.secuencia) INTO v_ultimo;
            IF v_ultimo < v_max THEN
                INSERT INTO _verificacion_secuencias VALUES (r.secuencia, r.tabla, r.columna, v_ultimo, v_max);
            END IF;
        END LOOP;
    END \$\$;
    SELECT string_agg(secuencia || ' (tabla ' || tabla || ', last_value=' || ultimo_valor || ' < MAX(' || columna || ')=' || id_maximo || ')', E'\n  ')
    FROM _verificacion_secuencias;
")"
if [ -z "$DESALINEADAS" ]; then
    echo "  OK    todas las secuencias >= el id maximo de su tabla"
else
    echo "  FALLO secuencias desalineadas:"
    echo "  $DESALINEADAS"
    FALLOS=$((FALLOS + 1))
fi

echo ""
if [ "$FALLOS" -eq 0 ]; then
    echo "== RESTAURACION OK: '$BASE_PRUEBA' coincide con '$BD_NOMBRE_REAL' en todo lo verificado =="
else
    echo "== RESTAURACION CON $FALLOS FALLO(S): revisar arriba =="
    exit 1
fi
