# -*- coding: utf-8 -*-
"""Opción B (11/11/2026, decisión de Joel): le agrega a entrega_final/proyecto_final.sql las dos
sentencias que corrigen el texto de caja.gerente_predeterminado.

POR QUÉ UN PARCHE Y NO UN DUMP NUEVO: el dump de la entrega es del 26/10/2026 y lleva una base
mínima y limpia (2 personas, 2 préstamos). Regenerarlo hoy traería las 217 personas, los 129
préstamos y las 6.421 filas de auditoría que dejó la prueba de humo. El esquema no cambió desde
entonces y los 52 parámetros tienen los mismos 52 valores: lo único desfasado es el TEXTO de ese
parámetro (su nombre y su observación), corregido el 11/11 en la base y en bd/03_parametros.sql.

EL TEXTO NO SE ESCRIBE ACÁ: se lee de bd/03_parametros.sql, que es el dueño de la siembra y ya
tiene el cambio aplicado. Así los dos no pueden quedar distintos.

Lo corre Joel (los .sql están protegidos por una regla deny del proyecto), desde sistema/:

    cp entrega_final/proyecto_final.sql entrega_final/proyecto_final.sql.antes_del_parche
    python herramientas/parchar_dump_entrega.py
    tail -20 entrega_final/proyecto_final.sql

Es idempotente: si el parche ya está, aborta sin tocar nada.
"""
import io
import sys

ORIGEN = "bd/03_parametros.sql"
DUMP = "entrega_final/proyecto_final.sql"
MARCA = "Parche 11/11/2026"

lineas = io.open(ORIGEN, encoding="utf-8").read().splitlines()

# El UPDATE de la observación, tal cual quedó en la siembra. NO se corta en el primer ";": el
# propio texto de la observación tiene uno adentro.
i = next(k for k, l in enumerate(lineas) if l.startswith("UPDATE parametros_sistema"))
j = next(k for k in range(i, len(lineas)) if lineas[k].rstrip().endswith("IS NULL;"))
update_observacion = "\n".join(lineas[i:j + 1])

# El nombre nuevo sale de la fila sembrada (en el INSERT no hay UPDATE que copiar).
fila = [l for l in lineas if l.startswith("('caja.gerente_predeterminado',")][0]
nombre = fila.split("', '")[1]
if "'" in nombre:
    sys.exit("ABORTA: el nombre tiene una comilla simple y rompería el SQL.")

d = io.open(DUMP, encoding="utf-8", newline="").read()
if "caja.gerente_predeterminado" not in d:
    sys.exit("ABORTA: %s no menciona la clave. ¿Es el archivo correcto?" % DUMP)
if MARCA in d:
    sys.exit("ABORTA: %s YA tiene el parche aplicado." % DUMP)

PARCHE = (
    "\n"
    "--\n"
    "-- " + MARCA + " (opcion B, decision de Joel): el texto de caja.gerente_predeterminado.\n"
    "-- Este dump es del 26/10/2026 y trae la descripcion vieja, que desde C22 (03/11/2026) dejo\n"
    "-- de ser cierta: el acta de arqueo nombra al gerente SOLO si quedo registrado al revisar una\n"
    "-- diferencia, y ningun codigo lee ya esta clave. El VALOR no se toca (es el dato historico de\n"
    "-- quien figuraba en las actas impresas); se corrigen el nombre y la observacion, igual que en\n"
    "-- bd/03_parametros.sql y que en sgcpc_db_pruebas.\n"
    "-- Van al final, despues del COPY de parametros_sistema, para no regenerar el dump entero:\n"
    "-- regenerarlo hoy traeria los 217 clientes y las 6.421 filas de auditoria del humo, en vez de\n"
    "-- la base limpia de 2 personas que esta entrega lleva.\n"
    "--\n"
    "-- EL search_path (12/11/2026): pg_dump abre el dump con\n"
    "--   SELECT pg_catalog.set_config('search_path', '', false);\n"
    "-- que es su guarda para que nada resuelva por casualidad. Con el search_path vacio, un\n"
    "-- \"parametros_sistema\" SIN esquema no resuelve: al restaurar, estas dos sentencias daban\n"
    "-- \"no existe la relacion parametros_sistema\" y el parche quedaba en el archivo sin\n"
    "-- aplicarse -- el parametro seguia con el texto viejo, y el restore seguia de largo sin\n"
    "-- que se notara. Se repone el esquema aca, y no calificando las dos tablas, porque el\n"
    "-- UPDATE de la observacion se copia TEXTUAL de bd/03_parametros.sql y no hay que\n"
    "-- reescribirlo.\n"
    "SET search_path TO public;\n"
    "\n"
    "UPDATE parametros_sistema\n"
    "   SET nombre_parametro = '" + nombre + "'\n"
    " WHERE clave_parametro = 'caja.gerente_predeterminado'\n"
    "   AND vigente_hasta IS NULL;\n"
    "\n"
    + update_observacion + "\n"
)

# Control antes de escribir: el parche tiene que traer su SET search_path. Sin el, al
# restaurar no se aplica -- y no se nota, porque el restore sigue de largo y el parametro queda
# con el texto viejo. Es exactamente lo que paso con la primera version de este generador.
if "SET search_path TO public;" not in PARCHE:
    sys.exit("ABORTA: el parche no trae el SET search_path y al restaurar no se aplicaria.")

io.open(DUMP, "a", encoding="utf-8", newline="").write(PARCHE)
print("Parche agregado al final de " + DUMP + ":")
print(PARCHE)
