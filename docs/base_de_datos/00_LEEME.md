# Base de datos — índice y regla de uso

Documentación permanente del diseño de la base de datos (`sgcpc_db_pruebas`/`sgcpc_db`), separada
de `docs/funcionamiento/` (que documenta procesos/RF) y de `AVANCE.md` (bitácora general del
proyecto). Nace el 15/10/2026, a partir de la auditoría completa de base pedida por Joel y del
pedido del tutor de normalizar `personas`.

**Regla de uso**: antes de tocar cualquier tabla, columna, índice o restricción de la base, leer
`04_esquema_actual.md` (el estado de HOY, no el de la auditoría inicial). Después de cada cambio
real sobre la base, en este orden:

1. Actualizar `04_esquema_actual.md` para que vuelva a describir el estado real.
2. Agregar una entrada nueva en `03_bitacora_cambios.md` (fecha, qué se hizo, archivos, cómo se
   verificó, commit y tag).

Esta misma regla está repetida en `CLAUDE.md` (instrucción local, no versionada) para que no
dependa de que alguien se acuerde de leer esta carpeta.

## Índice

| Archivo | Qué contiene | ¿Se actualiza? |
|---|---|---|
| `00_LEEME.md` | Este índice. | Rara vez — solo si cambia la organización de la carpeta. |
| `01_auditoria_inicial_v1.0.md` | Auditoría completa de `sgcpc_db_pruebas` tal como estaba el 15/10/2026 (commit `3a248af`, tags `v1.0-estable`/`lote2-D1-probado`), **antes** de normalizar `personas`. Es una FOTO: una vez escrita, no se vuelve a tocar — sirve como punto de comparación fijo ("cómo era antes"). | **No, nunca.** |
| `02_analisis_y_problemas.md` | Los hallazgos de la auditoría explicados en términos de formas normales, el problema concreto que señaló el tutor (`personas` con 49 columnas) y la decisión de diseño tomada para resolverlo. | Se agregan análisis nuevos si aparecen, no se reescribe lo ya aprobado. |
| `03_bitacora_cambios.md` | Registro cronológico de cada cambio real hecho sobre el esquema (un cambio = una entrada), grupo por grupo de la normalización de `personas` y cualquier otro cambio de esquema futuro. | **Sí, en cada cambio** (se agrega una entrada, nunca se borra una vieja). |
| `04_esquema_actual.md` | Tablas, columnas, PK, FK, relaciones y diagrama Mermaid del esquema **tal como está hoy** (arranca igual a la auditoría inicial, el 15/10/2026; diverge de ahí en adelante). | **Sí, en cada cambio** (es la única fuente confiable de "cómo es la base ahora", no `01_auditoria_inicial_v1.0.md`, que queda congelada). |

## Por qué esta separación

`01_auditoria_inicial_v1.0.md` y `04_esquema_actual.md` arrancan siendo el mismo documento
(15/10/2026) pero cumplen roles distintos a propósito: uno es el "antes" fijo para poder explicar
qué cambió y por qué (útil para la tesis/defensa), el otro es la referencia viva que hay que leer
antes de tocar algo. Confundirlos (actualizar la auditoría inicial, o no actualizar nunca el
esquema actual) deja a cualquiera que lea esta carpeta después sin saber cuál de los dos es cierto
hoy — de ahí la regla de uso de arriba.
