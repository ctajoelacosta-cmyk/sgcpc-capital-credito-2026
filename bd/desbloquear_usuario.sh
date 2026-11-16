#!/usr/bin/env bash
# SGCPC - desbloquear_usuario.sh
# Procedimiento de recuperacion (E2, punto 4): reactiva a mano un usuario
# bloqueado por intentos fallidos (RNF09) cuando no queda ningun otro
# GERENTE_ADMINISTRADOR activo para hacerlo desde la pantalla de Usuarios.
# Deja la cuenta ACTIVA, intentos en 0 y exige cambio de contrasena en el
# proximo acceso (igual que siempre -- por si el bloqueo fue justo mientras
# probaba una contraseña que ya no es la correcta).
#
# Por defecto NO cambia la contraseña en si (queda la que ya tenia). D-117
# (pedido de Joel): --restablecer ademas
# genera una contraseña temporal nueva (mismo patron que
# ServicioUsuarios.restablecerContrasena, la funcion que ya usa la propia
# pantalla de Usuarios) y exige cambiarla en el proximo acceso -- para esto
# hace falta --motivo "<texto>" (obligatorio, nunca vacio): esta accion
# reemplaza una credencial real, y a diferencia de un click en la pantalla
# de Usuarios (que ya queda en auditoria_operaciones con el gerente que lo
# hizo) este script corre FUERA de la aplicacion, sin sesion ni usuario
# validado -- el motivo es lo unico que documenta por que se uso. Se anota
# en bd/desbloquear_usuario.log (fecha, base, usuario, motivo -- nunca la
# contraseña) ademas de imprimirse en pantalla.
#
# La contraseña temporal generada se imprime UNA sola vez al final, para
# entregarsela a la persona -- igual que la pantalla de Usuarios se la
# muestra una vez al gerente que restablece. Nunca se guarda en ningun
# archivo ni se imprime de nuevo.
#
# La contraseña de conexion a la base se lee de bd.properties (nunca esta
# escrita en este script) y se pasa a psql via PGPASSWORD. Uso, desde
# sistema/:
#   source ./entorno.sh && bash bd/desbloquear_usuario.sh gerente
#   source ./entorno.sh && bash bd/desbloquear_usuario.sh gerente --restablecer --motivo "Se olvidó la contraseña, sin otro gerente activo para restablecerla desde la pantalla."
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Uso: bash bd/desbloquear_usuario.sh <nombre_usuario> [--restablecer --motivo \"<texto>\"]" >&2
    exit 1
fi

NOMBRE_USUARIO="$1"
shift
RESTABLECER=0
MOTIVO=""
while [ $# -gt 0 ]; do
    case "$1" in
        --restablecer)
            RESTABLECER=1
            shift
            ;;
        --motivo)
            MOTIVO="${2:-}"
            shift 2
            ;;
        *)
            echo "Opción no reconocida: $1" >&2
            echo "Uso: bash bd/desbloquear_usuario.sh <nombre_usuario> [--restablecer --motivo \"<texto>\"]" >&2
            exit 1
            ;;
    esac
done

if [ "$RESTABLECER" -eq 1 ] && [ -z "$(echo "$MOTIVO" | tr -d '[:space:]')" ]; then
    echo "Con --restablecer, --motivo es obligatorio (no puede estar vacío)." >&2
    exit 1
fi

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

BD_HOST="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')"
BD_PUERTO="$(echo "$BD_URL" | sed -E 's#jdbc:postgresql://[^:]+:([0-9]+).*#\1#')"
BD_NOMBRE="$(echo "$BD_URL" | sed -E 's#.*/([^/?]+).*#\1#')"

echo "== Reactivando '$NOMBRE_USUARIO' en '$BD_NOMBRE' =="
# Nota: la interpolacion "-v nombre=... -c ':nombre'" de psql no funciona en esta
# instalacion (psql 18.0 da "error de sintaxis en o cerca de :" incluso con la forma
# mas simple, ej. "-v x=5 -c 'SELECT :x;'"); se arma la sentencia a mano, escapando
# comillas simples (unico caracter especial posible en un nombre_usuario).
NOMBRE_ESCAPADO="${NOMBRE_USUARIO//\'/\'\'}"
psql -h "$BD_HOST" -p "$BD_PUERTO" -U "$BD_USUARIO" -d "$BD_NOMBRE" -v ON_ERROR_STOP=1 \
    -c "UPDATE usuarios SET estado_cuenta = 'ACTIVA', intentos_fallidos = 0, debe_cambiar_contrasena = TRUE WHERE nombre_usuario = '$NOMBRE_ESCAPADO';"

echo "== '$NOMBRE_USUARIO' queda ACTIVA y debe cambiar su contraseña al entrar =="

if [ "$RESTABLECER" -eq 1 ]; then
    echo "== Generando una contraseña temporal nueva para '$NOMBRE_USUARIO' =="
    SALIDA_JAVA="$(cd "$CARPETA/.." && mvn -q -Dtest=RestablecerContrasenaUsuario "-DnombreUsuario=$NOMBRE_USUARIO" test)"
    CONTRASENA_TEMPORAL="$(echo "$SALIDA_JAVA" | grep '^CONTRASENA_TEMPORAL=' | tail -1 | sed 's/^CONTRASENA_TEMPORAL=//')"
    if [ -z "$CONTRASENA_TEMPORAL" ]; then
        echo "No se pudo generar la contraseña temporal -- revise la salida de Maven arriba." >&2
        exit 1
    fi

    REGISTRO="$CARPETA/desbloquear_usuario.log"
    printf '%s | base=%s | usuario=%s | motivo=%s\n' \
        "$(date '+%Y-%m-%d %H:%M:%S')" "$BD_NOMBRE" "$NOMBRE_USUARIO" "$MOTIVO" >> "$REGISTRO"

    echo
    echo "== Contraseña temporal para '$NOMBRE_USUARIO' (entréguesela ahora, no queda guardada en ningún lado): $CONTRASENA_TEMPORAL =="
    echo "== Debe cambiarla en el próximo acceso. Motivo anotado en $REGISTRO =="
fi
