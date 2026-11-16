# Diseño del Bloque 5 (E9) — Reversiones y cierre documental

Propuesta para el OK de Joel. No programado todavía. Fuentes: `docs/00_especificacion_maestra.md`
PO-13 (reversión de cobro/desembolso), PO-14 (cierre documental), PO-15 (panel), RF-38/RF-39/RF-41,
RN-12/RN-13, §7 (estados), §10 (modelo de datos) — y el esquema real (`bd/01_esquema.sql`), leído
directo, no por el Doc. 08 (que en varios puntos está desactualizado frente al esquema real, ya
confirmado en bloques anteriores).

Orden de lectura sugerido: primero las 4 decisiones marcadas **[DECISIÓN]** (sección 0), porque el
resto del diseño depende de cuál elija Joel. Después la propuesta completa, en el mismo orden que
pidió: operaciones reversibles y límite de tiempo, flujo cajera/gerente, factura, restauración,
constancia de devolución del pagaré, panel, esquema, y qué recortar.

## 0. Hallazgo principal y decisiones que necesito antes de programar

**La base de datos ya tiene la mitad de esto construido desde E0, sin usar todavía.** Verificado
leyendo `bd/01_esquema.sql` directo:

- `reversiones_pago` ya existe (`id_reversion_pago`, `identificador_confirmacion` UNIQUE,
  `id_pago` UNIQUE — un solo reverso por pago —, `motivo_reversion`, `fecha_hora_reversion`,
  `autorizado_por`, `procesado_por`, `id_sesion_caja`, `monto_salida_caja`,
  `id_archivo_comprobante`).
- `facturas.estado` ya tiene `'ANULADA'` en su CHECK, y `facturas.id_reversion_pago` ya es una FK
  a `reversiones_pago` — la anulación de factura por reversión ya estaba prevista en el diseño
  original, sin que nadie la usara.
- `pagos.estado_pago` y `desembolsos.estado_desembolso` ya tienen `REVERTIDO` en su CHECK (junto a
  `CONFIRMADO`), sin que ningún código lo escriba nunca (confirmado: `grep REVERTIDO` en
  `src/main/java` solo encuentra las dos declaraciones del enum).
- `movimientos_caja.tipo` ya tiene `'COMPENSACION'` en su CHECK, con un comentario en el propio
  `TipoMovimientoCaja.java`: *"COMPENSACION para E9 (reversiones)"*.
- `prestamos.estado_cierre_documental` (`PENDIENTE`/`COMPLETO`/`NO_APLICA`),
  `fecha_cierre_documental` y `cierre_documental_por` ya existen, sin usar.
- Los permisos `SOLICITAR_REVERSION` (cajero `TOTAL`, oficial/gerente `NINGUNO`) y
  `AUTORIZAR_REVERSION_O_DIFERENCIA` (gerente `TOTAL`, cajero/oficial `NINGUNO`) ya están en
  `Permiso.java` y en la matriz de `ServicioAutorizacion`, sin ninguna ruta mapeada todavía.

**Falta, y es un cambio de esquema real:** `reversiones_desembolso` no existe en
`bd/01_esquema.sql` (sí está descripta en `docs/08_modelo_datos.md`, tabla 18 — otro caso de Doc.
08 más completo que el esquema real, al revés de lo habitual). Y **ninguna de las dos tablas de
reversión tiene una fase "pendiente"**: todas sus columnas son `NOT NULL` salvo
`id_archivo_comprobante` — representan solo el resultado YA autorizado y ejecutado, no una
solicitud esperando al gerente. El §7 del Doc. 00 sí define esos 3 estados
(`SOLICITADA`/`AUTORIZADA`/`RECHAZADA`), así que hace falta decidir dónde viven.

**[DECISIÓN 1] — Dónde vive el estado "pendiente de autorizar".**
Propongo **extender `reversiones_pago`** (agregar `estado`, `solicitado_por`,
`fecha_hora_solicitud`, `motivo_rechazo`, y aflojar a `NULL` las columnas que solo se completan al
autorizar: `autorizado_por`, `procesado_por`, `id_sesion_caja`, `monto_salida_caja`,
`fecha_hora_reversion`) en vez de crear una cola aparte. Mismo patrón que ya usa
`solicitudes_credito` (una fila nace "abierta" con los campos de la decisión en `NULL`, se
completan cuando alguien decide). Como la tabla existe desde E0 pero **ningún código la usó
nunca** (confirmado arriba), modificarla ahora no toca ni una fila real — no es una migración de
datos en vivo, es terminar de diseñar una tabla vacía. `reversiones_desembolso` se crea nueva, ya
con las columnas de la fase pendiente desde el principio (sin tener que corregirla después).

**[DECISIÓN 2] — Qué se puede revertir: solo la ÚLTIMA operación del préstamo.**
El Doc. 00 no dice nada sobre revertir una operación vieja con operaciones más nuevas encima, y
ahí está el riesgo real: `cuotas.capital_pagado`/`interes_pagado`/`estado_pago` son **totales que
se van acumulando fila por fila** (no un historial repetible) — si se revierte un pago viejo
mientras hay pagos más nuevos que ya asumieron ese estado, restaurar los números exige "rebobinar"
todos los pagos posteriores en orden, un problema mucho más grande que "deshacer el último error".
Propongo limitar la reversión a **el pago o desembolso más reciente y todavía no revertido de ese
préstamo** — cubre el caso real que describe el Doc. 00 ("Operación confirmada por error", PO-13,
paso 1: la cajera se da cuenta y pide revertir), que casi siempre es "me equivoqué recién", y evita
tener que reconstruir un historial. Si hace falta revertir algo más viejo, el sistema lo rechaza
con un mensaje claro ("Hay una operación más reciente sobre este préstamo; revierta esa primero").
Esto también responde al límite de tiempo que proponía Joel ("solo en la misma sesión de caja"):
el Doc. 00 mismo contradice esa restricción en su propio caso de aceptación de PO-13 ("Reversión
con caja cerrada compensa en caja abierta" — es decir, se espera poder revertir algo de una sesión
YA cerrada, compensando en la sesión abierta actual), así que el límite real que propongo no es de
tiempo/sesión, sino de **orden**: solo la operación más reciente, sin importar cuándo fue.

**[DECISIÓN 3] — Factura: se anula, no se emite nota de crédito.**
El propio Doc. 00 (RF-41, PO-13 paso 3) dice literalmente "se anula la factura asociada (nota de
anulación)" — no "nota de crédito". Una nota de crédito es un documento fiscal aparte, con su
propia numeración/timbrado y más carga regulatoria; el esquema real ya tiene exactamente lo que
describe el Doc. 00 (`facturas.estado = 'ANULADA'`, `facturas.id_reversion_pago`). Propongo: la
factura original **nunca se borra ni se reemplaza** (RN-13, "nada se borra"), pasa a
`estado = 'ANULADA'`, y se genera un comprobante PDF nuevo y simple — "Nota de anulación" — que
referencia el número de la factura anulada, el motivo y los datos de la reversión. Si el tutor o
un cliente real exige más adelante una Nota de Crédito formal (con su propio timbrado), queda
documentado como trabajo futuro, mismo tratamiento que "Cumplimiento (CSV)" del Bloque 4.

**[DECISIÓN 4] — Quién hace el cierre documental (P10).**
El Doc. 00 dice "Oficial o gerente" para PO-14, sin nombrar un permiso nuevo en la matriz de §3.1.
Propongo reusar `GENERAR_DOCUMENTOS_FORMALIZACION` (ya `TOTAL` para oficial y gerente desde E5/D-68,
mismo actor exacto que pide PO-14) en vez de crear un permiso nuevo solo para esto — es la misma
familia de tarea ("documentos del préstamo"), y evita otra entrada más en la matriz para una sola
pantalla.

## 1. Qué se puede revertir, y hasta cuándo

| Operación | Tabla de origen | Se revierte vía | Condición además de "es la más reciente" |
|---|---|---|---|
| Cobro normal, parcial (con o sin promesa) | `pagos` (`tipo` = `NORMAL`/`PARCIAL`) | `reversiones_pago` | — |
| Anticipo de capital | `pagos` (`tipo` = `ANTICIPO_CAPITAL`) | `reversiones_pago` | Además restaura el plan anterior (sección 4) |
| Cancelación total | `pagos` (`tipo` = `CANCELACION_TOTAL`) | `reversiones_pago` | Además restaura `estado_prestamo` a lo que corresponda |
| Desembolso | `desembolsos` | `reversiones_desembolso` | El préstamo no puede tener ningún `pago` todavía (si los tiene, hay que revertirlos primero, en orden) |

Los 4 casos de "cobro/anticipo/cancelación" son, en el fondo, el mismo mecanismo (una fila de
`pagos`, un solo flujo de reversión) — no hace falta un tratamiento especial por tipo, solo la
restauración de más abajo cambia según qué generó ese pago en particular (plan nuevo si fue
anticipo, estado FINALIZADO si fue cancelación).

**Límite**: sin límite de tiempo/sesión (decisión 2) — sí exige que sea la operación más reciente
del préstamo y todavía no revertida, y (RN-13) que haya una caja `ABIERTA` en el momento de
autorizar (ahí es donde se registra el movimiento inverso, sea o no la misma sesión donde se hizo
la operación original).

## 2. Flujo: la cajera solicita, el gerente autoriza

1. **Solicitar** (`SOLICITAR_REVERSION`, cajero `TOTAL` — ya en la matriz): desde el historial de
   cobros de `/cobranza/{id}` (o desde `/desembolso`, para un desembolso), un botón "Solicitar
   reversión" en la fila de la operación más reciente (las anteriores no lo muestran, ya
   cubierto por la decisión 2). Formulario: motivo (texto obligatorio). Inserta la fila en
   `reversiones_pago`/`reversiones_desembolso` con `estado = 'SOLICITADA'`, `solicitado_por` =
   la cajera, el resto de los campos de ejecución en `NULL`.
2. **Bandeja del gerente** (`AUTORIZAR_REVERSION_O_DIFERENCIA`, gerente `TOTAL` — ya en la
   matriz): pantalla nueva `/reversiones`, lista las `SOLICITADA` (mismo estilo que
   `/aprobaciones`). Cada fila: préstamo, operación, monto, motivo, quién y cuándo la pidió.
3. **Autorizar**: exige una caja `ABIERTA` en ese momento (si no hay, se rechaza con el mismo
   mensaje que ya usan `ServicioCaja`/`ServicioDesembolso` para "no hay caja abierta"). En una
   sola transacción: restaura cuotas/plan/estado del préstamo (sección 4), inserta el movimiento
   `COMPENSACION` en la caja `ABIERTA` actual, anula la factura si había una (sección 3), marca
   `pagos.estado_pago`/`desembolsos.estado_desembolso = 'REVERTIDO'`, completa
   `autorizado_por`/`procesado_por`/`id_sesion_caja`/`monto_salida_caja`/`fecha_hora_reversion`,
   `estado = 'AUTORIZADA'`. Auditoría: `AUTORIZAR_REVERSION`.
4. **Rechazar**: exige motivo (obligatorio, `motivo_rechazo`). `estado = 'RECHAZADA'`. No toca
   nada más — la operación original queda exactamente como estaba. Auditoría: `RECHAZAR_REVERSION`.
5. **Idempotencia** (§14.3, mismo patrón que el resto del sistema): `identificador_confirmacion`
   nuevo por cada solicitud, generado al mostrar el formulario; un doble clic en "Autorizar" no
   duplica el movimiento de caja ni la anulación de la factura.

## 3. La factura ya emitida

Decisión 3 (arriba). Al autorizar una reversión cuyo pago tenía factura (`pagos.id_pago` →
`facturas.id_pago`, si existe):
- `facturas.estado = 'ANULADA'`, `facturas.id_reversion_pago` = la reversión recién autorizada.
  La factura original (PDF ya guardado) sigue existiendo tal cual, nunca se borra ni se
  reemplaza — sigue siendo reimprimible desde el historial, ahora con un cartel "ANULADA".
- Nuevo comprobante simple, "Nota de anulación" (JasperReports, `.jrxml` a diseñar con Joel
  cuando llegue el momento, mismo patrón que el resto de comprobantes — REGLAS_DESARROLLO.md §6: nada se
  diseña sin preguntarle antes qué debe llevar): número de la factura anulada, fecha, cliente,
  motivo de la reversión, monto, firma de quién autorizó.
- Un anticipo/cancelación sin conceptos gravados (D-89, ya establecido) nunca generó factura —
  ahí no hay nada que anular, la reversión sigue igual sin ese paso.

## 4. Cómo se restauran cuotas, plan, caja y estado del préstamo

**Cobro/parcial** (sin cambio de plan): por cada `aplicaciones_pago` de ese `pago`, restar
exactamente lo que sumó en su momento — `cuotas.capital_pagado -= monto_capital`,
`interes_pagado -= monto_interes`, y volver a sumar `moratorio_pendiente`/`punitorio_pendiente`/los
IVA de mora que ese pago había descontado. `estado_pago` se recalcula con la MISMA función que ya
existe (nunca una regla nueva): `PAGADA` si queda íntegro, `PARCIAL` si queda algo pagado,
`PENDIENTE` si vuelve a 0. Si el pago había limpiado `fecha_corte_mora`, se restaura al valor
anterior (guardado en el detalle de la reversión, no en una columna nueva — alcanza con
recalcularlo desde las cuotas vecinas, mismo criterio que ya usa el motor).

**Anticipo de capital**: además de lo anterior, el anticipo generó una versión nueva del plan
(`planes_pago.origen_plan = 'ANTICIPO_PARCIAL'`, `vigente = true`, la anterior pasa a
`vigente = false` — ya así desde E7). Revertir marca esa versión nueva `vigente = false` de nuevo
y **restaura `vigente = true` en la versión anterior** — ninguna fila de plan se borra (mismo
"nada se borra"), solo cambia cuál es la vigente. `cuotas` de la versión revivida ya tienen sus
propios valores intactos (nunca se tocaron, porque la versión no vigente no recibe pagos).

**Cancelación total**: mismo mecanismo de plan que el anticipo, si la cancelación generó una
versión de liquidación. Además, `estado_prestamo` vuelve a recalcularse con
`ServicioCobranza.recalcularEstadoPrestamo` (la misma función de siempre) — puede volver a
`ACTIVO` o a `EN_MORA`, según queden o no cuotas vencidas después de deshacer el pago.

**Desembolso**: `prestamos.estado_prestamo = 'PENDIENTE_DESEMBOLSO'`, `fecha_desembolso = NULL`.
No hay cuotas que tocar (nada se cobró todavía, es la condición de la sección 1).

**Caja**: un movimiento `COMPENSACION` en la sesión `ABIERTA` actual (no necesariamente la
original — decisión 2/RN-13):
- Revertir un **cobro** = el efectivo tiene que volver al cliente → `signo = -1` (sale de caja),
  `monto` = lo que ese pago había aplicado.
- Revertir un **desembolso** = el cliente devuelve el efectivo → `signo = +1` (entra a caja).

## 5. Constancia de devolución del pagaré (cierre documental, P10)

Decisión 4 (`GENERAR_DOCUMENTOS_FORMALIZACION`, oficial o gerente). Ruta nueva `/prestamos/{id}/cierre-documental`
(o similar — a definir con el resto de las rutas), habilitada solo si `estado_prestamo = 'FINALIZADO'`
y `estado_cierre_documental = 'PENDIENTE'`:

1. Genera la constancia (JasperReports, a diseñar con Joel): operación, cliente, monto original,
   fecha de cancelación, texto de devolución del pagaré original. Numeración: mismo mecanismo que
   la constancia de cancelación (`numeradorDAO.siguienteValor(conexion, "CONSTANCIA", año)`, clave
   `CONSTANCIA` ya existente en `numeradores` — reusada, no una clave nueva), con un prefijo propio
   para distinguirla, ej. `DEV-2026-000001` (la de cancelación usa `CAN-...`).
2. El cliente firma en papel; el oficial/gerente escanea y lo carga como documento del expediente
   (mismo mecanismo que ya existe desde E3/E5 — `documentos_expediente`, `ControladorDocumentosServlet`
   — no hace falta inventar nada, solo un `tipos_documento` nuevo, sembrado por datos, no por DDL:
   `codigo_tipo = 'DEVOLUCION_PAGARE'`, `contexto = 'PRESTAMO'`).
3. Al confirmar la carga: `estado_cierre_documental = 'COMPLETO'`, `fecha_cierre_documental = now()`,
   `cierre_documental_por` = quien lo hizo. Auditoría: `CERRAR_EXPEDIENTE`.

## 6. Panel: "Reversiones por autorizar"

Ya está reservado el lugar (`docs/diseno_bloque4_E8.md`, sección 3: *"No aplica todavía -- E9 no
está construido; el panel deja el lugar reservado y muestra '—'"*). Con E9 construido, pasa a un
número real: `count(*)` de `reversiones_pago` + `reversiones_desembolso` con `estado = 'SOLICITADA'`,
visible solo para el gerente (mismo perfil que `AUTORIZAR_REVERSION_O_DIFERENCIA`), con enlace a
`/reversiones`.

## 7. Cambios de esquema

Sí hacen falta, a diferencia del Bloque 4 (que no tocó el esquema). Todos sobre tablas que **hoy
están vacías** (nunca las usó ningún código), así que no son una migración de datos reales:

- **`reversiones_pago`** (`ALTER TABLE`): agregar `estado VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA' CHECK (estado IN ('SOLICITADA','AUTORIZADA','RECHAZADA'))`,
  `solicitado_por BIGINT NOT NULL`, `fecha_hora_solicitud TIMESTAMP NOT NULL`,
  `motivo_rechazo VARCHAR(500) NULL`; aflojar a `NULL`
  `autorizado_por`/`procesado_por`/`id_sesion_caja`/`monto_salida_caja`/`fecha_hora_reversion`
  (hoy `NOT NULL`, se completan solo al autorizar).
- **`reversiones_desembolso`** (`CREATE TABLE`, nueva): mismas columnas que
  `docs/08_modelo_datos.md` tabla 18 (`id_reversion_desembolso`, `identificador_confirmacion`
  UNIQUE, `id_desembolso` UNIQUE, `motivo_reversion`, `fecha_hora_reversion`, `autorizado_por`,
  `procesado_por`, `id_sesion_caja`, `monto_ingreso_caja`, `id_archivo_comprobante`) más los
  mismos campos de fase pendiente que `reversiones_pago` (ya de entrada, sin tener que corregirla
  después).
- **Triggers `BEFORE DELETE`** (D-46, `bd/02_indices.sql`) en las dos tablas de reversión, mismo
  patrón que el resto de las tablas financieras.
- **`tipos_documento`** (dato, no DDL): una fila nueva `DEVOLUCION_PAGARE` (contexto `PRESTAMO`).
- **Sin cambios** en `cuotas`, `prestamos`, `planes_pago`, `movimientos_caja` ni `facturas` — todo
  lo que hace falta ya existe (`estado_pago`/`estado_prestamo`/`vigente`/`'COMPENSACION'`/
  `'ANULADA'`/`id_reversion_pago`).

## 8. Qué recortaría para un alcance mínimo defendible

El propio Doc. 00 marca tanto "Reversiones" como "Constancia de devolución de pagaré" como
**Debería**, no Debe — hay margen real para recortar sin contradecir la especificación:

1. **Reversión solo de la operación más reciente** (decisión 2) — ya es en sí mismo el recorte
   más grande: sin esto, revertir una operación vieja obliga a reconstruir un historial completo
   de pagos, mucho más allá del alcance de un bloque.
2. **Nota de anulación simple, no Nota de Crédito formal** (decisión 3) — evita timbrado/numeración
   fiscal nueva.
3. **Un solo flujo para cobro/parcial/anticipo/cancelación** (misma tabla `reversiones_pago`,
   mismo formulario) — no tres pantallas distintas.
4. **Cierre documental sin flujo de escaneo dedicado** — reusa `documentos_expediente` tal cual
   existe, con un `tipos_documento` nuevo, en vez de una pantalla nueva de carga.
5. Si hiciera falta recortar más todavía: **reversión de desembolso** es la pieza más chica y más
   aislada (nunca toca cuotas) — podría quedar para una vuelta siguiente sin afectar el resto,
   dejando el bloque enfocado en reversión de cobros (el caso que más menciona el Doc. 00) y
   cierre documental.

## 9. Qué va a probar Joel en el navegador

Con datos reales o de humo (cédula "9...", D-75), sin tocar preston de Joel salvo que él lo pida:

1. **Cajera** solicita revertir el cobro más reciente de un préstamo (con motivo) — confirma que
   el botón solo aparece en la fila más reciente, no en las anteriores.
2. **Gerente** ve la solicitud en `/reversiones`, la autoriza — confirma: cuota vuelve a su estado
   anterior, movimiento `COMPENSACION` en la caja abierta, factura (si había) pasa a `ANULADA` con
   su nota de anulación, `pagos.estado_pago = 'REVERTIDO'`.
3. **Rechazo**: otra solicitud, el gerente la rechaza con motivo — confirma que nada cambió en el
   préstamo ni en la caja.
4. **Reversión con la caja original ya cerrada**: revertir algo de una sesión cerrada, compensando
   en la sesión abierta actual (el caso de aceptación explícito del Doc. 00).
5. **Reversión de un anticipo**: confirma que el plan vuelve a la versión anterior (cuotas como
   estaban antes del anticipo).
6. **Reversión de una cancelación total**: confirma que el préstamo vuelve de `FINALIZADO` a
   `ACTIVO`/`EN_MORA` según corresponda.
7. **Reversión de un desembolso** (préstamo recién desembolsado, sin pagos): vuelve a
   `PENDIENTE_DESEMBOLSO`, aparece de nuevo en `/desembolso`.
8. **Intentar revertir una operación vieja** (con una más nueva encima): rechazado con el mensaje
   claro.
9. **Cierre documental**: préstamo `FINALIZADO`, genera la constancia de devolución del pagaré,
   carga el escaneo firmado, confirma `estado_cierre_documental = 'COMPLETO'`.
10. **Panel del gerente**: "Reversiones por autorizar" muestra el número real, con enlace a
    `/reversiones`.
11. **Permisos**: oficial y gerente sin `SOLICITAR_REVERSION` (403 al intentar solicitar); cajera
    sin `AUTORIZAR_REVERSION_O_DIFERENCIA` (403 en `/reversiones`); cierre documental accesible a
    oficial y gerente, 403 a la cajera.

Avisame con tu OK (y las respuestas a las 4 decisiones de la sección 0) y arranco a programar.
