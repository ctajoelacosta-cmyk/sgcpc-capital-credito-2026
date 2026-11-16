# Análisis y problemas de diseño

Basado en `01_auditoria_inicial_v1.0.md` (foto del 15/10/2026). Acá se explican los hallazgos en
términos de formas normales y se registra la decisión tomada para el problema que señaló el tutor
de la tesis: la tabla `personas` con 49 columnas, "todo en una sola tabla, hay que separar y usar
JOIN".

## 1. El problema del tutor: `personas` (49 columnas)

`personas` mezclaba 7 temas distintos en una sola tabla (ver el detalle completo en
`01_auditoria_inicial_v1.0.md` §2 y §7.5): datos básicos de identidad, domicilio, contacto,
laborales, cónyuge, referencias personales y metadatos de alta/baja. Es la tabla más ancha de las
30 de la base, y la 2.ª en filas (75, después de `archivos_binarios`).

El problema no es solo de volumen de columnas — hay 2 violaciones concretas de forma normal
adentro, que son las que de verdad justifican separar en varias tablas (no solo "ordenar por
prolijidad"):

### 1FN — grupo repetido en las referencias personales

`referencia_nombre`/`referencia_telefono` (referencia 1) y `referencia2_nombre`/
`referencia2_telefono` (referencia 2) son, columna por columna, valores atómicos (cada celda tiene
un solo dato, no una lista) — en el sentido más literal de 1FN, no hay nada roto. Pero el **patrón**
de las 4 columnas es exactamente lo que 1FN pide eliminar: un **grupo repetido** disfrazado de
columnas numeradas (nombre1/teléfono1, nombre2/teléfono2) en vez de una relación 1–N real. Hoy el
límite de "como mucho 2 referencias" es un límite del ESQUEMA (no existe una 3.ª columna), no una
regla de negocio — si mañana hiciera falta una 3.ª referencia, la única forma de agregarla sería
otra columna más (`referencia3_nombre`, `referencia3_telefono`...), repitiendo el problema. La
forma correcta es una tabla hija `personas_referencias` con una fila por referencia, sin límite
fijo en el esquema.

### 3FN — dependencia transitiva de `id_departamento`

`personas` guardaba **a la vez** `id_ciudad` (FK a `ciudades.id_ciudad`) **y** `id_departamento`
(FK directa a `departamentos.id_departamento`) — pero `ciudades` ya tiene su propio
`id_departamento` (toda ciudad pertenece a un único departamento, FK `NOT NULL`). Eso significa que
`personas.id_departamento`, cuando `id_ciudad` está cargado, depende de `id_ciudad` (un atributo NO
clave de `personas`), no directamente de `id_persona` (la clave) — la definición exacta de una
dependencia transitiva, lo que 3FN prohíbe. El departamento de una persona siempre se puede obtener
con un JOIN a `ciudades` a partir de su `id_ciudad`; guardarlo aparte es una copia que podría
desalinearse (una persona con `id_ciudad` de Asunción pero `id_departamento` de otro departamento,
sin que nada en el esquema lo impida).

## 2. Otros hallazgos de la auditoría, relevantes para esta etapa (no se tocan ahora)

Quedan documentados en `01_auditoria_inicial_v1.0.md` §7, sin acción en esta normalización de
`personas` (alcance explícitamente acotado a esa tabla, a pedido de Joel):

- `cuotas.total_programado` y `desembolsos.monto_neto_entregado`: sumas/restas derivadas sin
  `CHECK` que las proteja.
- 2 tablas sin ningún uso en el código (`cargos_operacion`, `reversiones_desembolso`) — por diseño
  ya documentado, no un olvido.
- `prestamos.limite_tasa_id`/`sesiones_caja.gerente_revisa_id`: nombres de FK inconsistentes con el
  resto (sufijo `_id` en vez de prefijo `id_`).
- `reversiones_pago` sin `trg_bloquear_eliminacion` (a diferencia de `reversiones_desembolso`).
- Columnas FK sin índice propio, sin impacto real por el tamaño actual de la base.

## 3. Decisión de diseño tomada (15/10/2026, Joel)

Como **todos los datos de `sgcpc_db_pruebas` y `sgcpc_db` son de prueba**, no hace falta migrar
datos existentes — se diseña el modelo correcto desde cero y se recrean las bases (`bd/recrear_base.sh`),
sin arrastrar nada legado. Decisiones concretas:

- **Se eliminan 3 columnas legado, sin copiarlas a ningún lado**: `direccion_laboral` y
  `lugar_trabajo` (reemplazadas hace tiempo por el grupo laboral estructurado, `empleador_*`, y ya
  casi vacías: 4/75 en la auditoría inicial) y `referencias` texto libre (reemplazada por las
  referencias estructuradas, también casi vacía: 2/75). El *fallback* que tenía `ficha.jsp` para
  mostrar el primer valor no vacío entre esos 3 campos se elimina también.
- **`personas` queda solo con lo que es atributo directo de la persona** (identidad + contacto +
  metadatos de alta/baja): `id_persona`, `cedula_identidad`, `ruc`, `nombres`, `apellidos`,
  `fecha_nacimiento`, `estado_civil`, `fecha_vencimiento_cedula`, `telefono_whatsapp`,
  `correo_electronico`, `telefono_alternativo`, `estado_persona`, `registrado_por`,
  `fecha_registro`, `fecha_actualizacion`. **`domicilio` NO queda acá** — ver el ajuste de
  duplicación más abajo (15/10/2026, 2.ª ronda).
- **`ingreso_neto_comprobado` y `presenta_comprobante_ingreso` pasan a `personas_laborales`**
  (son datos de capacidad de pago/situación laboral, no identidad de la persona — a diferencia de
  la propuesta anterior, que los había dejado en `personas` por el uso frecuente en RN-04; con el
  enfoque de diseño limpio, van con el resto de lo laboral).
- **`id_departamento` se elimina** de `personas`/`personas_domicilio` — se obtiene siempre por
  JOIN a través de `id_ciudad → ciudades.id_departamento`.
- **5 tablas nuevas**: `personas_referencias` (1–N, el grupo repetido), `personas_conyuge` (1–1),
  `personas_baja` (1–1), `personas_domicilio` (1–1), `personas_laborales` (1–1). Diseño completo,
  columna por columna, PK/FK y cardinalidad: ver la propuesta aprobada en el chat del 15/10/2026 (y
  el diseño final que se vuelca en `04_esquema_actual.md` a medida que cada grupo se implementa).
- **Orden de implementación, un grupo por vez**: Referencias → Cónyuge → Baja → Domicilio →
  Laborales (del más chico/aislado al más usado en el código, para afirmar el patrón de migración
  con poco riesgo antes de tocar lo grande).
- **Método por grupo** (sin migración de datos, al ser todo de prueba): cambiar
  `bd/01_esquema.sql` + código (DAO/modelo/servicios/controladores/JSP/`.jrxml`/pruebas) →
  recrear `sgcpc_db_pruebas` desde cero (`bd/recrear_base.sh`, autorizado por Joel, nunca
  `sgcpc_db`) → cargar datos de ejemplo con el cargador actualizado → `mvn test` → prueba en
  pantalla con Joel → commit + tag por grupo.

## 4. Ajustes aprobados sobre el diseño (15/10/2026, 2.ª ronda, Joel)

Tras aprobar el diseño de las 5 tablas nuevas (§3), Joel pidió 3 ajustes antes de arrancar:

### `personas.domicilio` duplicaba el domicilio estructurado

El diseño original (§3) dejaba `domicilio` (texto libre, el que imprimen los 5 reportes legales —
contrato, pagaré, factura, comprobante de desembolso, autorización del buró) en `personas`, sin
moverlo, "para no tocar esos `.jrxml`". Joel señaló que eso guarda el MISMO dato dos veces: una vez
como texto armado (`domicilio`) y otra vez ya estructurado (`personas_domicilio`, desde el grupo
Domicilio en adelante) — exactamente el tipo de redundancia que esta normalización busca eliminar,
aunque esta vez no sea una violación de forma normal clásica sino una copia derivada sin `CHECK`
que la proteja (mismo tipo de hallazgo que `01_auditoria_inicial_v1.0.md` §7.2).

**Decisión**: `domicilio` se elimina de `personas` también — **en el grupo Domicilio**, no en
Referencias. El texto legal para contrato/pagaré/factura/comprobante de desembolso/autorización del
buró se arma al momento de generar cada documento, con un único método reutilizable (ya existe la
mitad: `ServicioClientes.construirTextoDomicilio(direccionDomicilio, barrio, nombreCiudad,
nombreDepartamento, referenciaDomicilio)`, hoy se llama una sola vez al guardar la ficha y el
resultado se guarda en la columna; pasa a llamarse por JOIN en el momento de generar cada PDF, sin
guardar nada). `ServicioClientes.domicilioSinReferencia(...)` (ya existe, quita el paréntesis final
de la referencia coloquial) sigue aplicándose igual, para los documentos legales. Los documentos ya
emitidos no cambian (el PDF ya generado y guardado es el que es, D-94); los que se generen después
de este grupo usan el texto armado al vuelo. Ejemplo del texto armado, con datos de muestra (antes
de aplicar nada):

```
direccionDomicilio = "Av. Mariscal López 1234"
barrio              = "Villa Morra"
ciudad              = "Asunción"
departamento        = "Asunción"          (= ciudad → se omite, no se repite)
referenciaDomicilio = "frente a la farmacia"

Texto para la ficha (con referencia):
  "Av. Mariscal López 1234, Villa Morra, Asunción (frente a la farmacia)"

Texto para documentos legales (domicilioSinReferencia, sin el paréntesis):
  "Av. Mariscal López 1234, Villa Morra, Asunción"
```

```
direccionDomicilio = "Ruta 2 Km 23"
barrio              = "Centro"
ciudad              = "San Lorenzo"
departamento        = "Central"           (≠ ciudad → se incluye)
referenciaDomicilio = (vacío)

Texto (igual para ficha y documentos legales, sin referencia que quitar):
  "Ruta 2 Km 23, Centro, San Lorenzo, Central"
```

### `personas_domicilio.id_ciudad` pasa a `NOT NULL`

El diseño original (§3) lo dejaba nulable (igual que hoy `personas.id_ciudad`). Joel pidió que sea
obligatorio: el departamento (obligatorio para la precalificación, RN-02) sale siempre de la ciudad
(`ciudades.id_departamento`) — sin `id_ciudad`, no hay forma de saber el departamento. Esto además
refuerza por esquema la dependencia transitiva ya señalada en §1 (el departamento SIEMPRE se
obtiene por JOIN, nunca puede faltar la ciudad que lo determina).

### `personas_referencias`: `UNIQUE (id_persona, numero_referencia)`

Ya incluido en el diseño de §3 pero remarcado explícitamente por Joel — a lo sumo una fila por
número de referencia y persona (no puede haber dos filas "referencia 1" para la misma persona).
Implementado en `bd/01_esquema.sql` (grupo Referencias, 15/10/2026).

### Regla general: los `CHECK` de una columna viajan con ella a la tabla nueva

Cuando una columna que tenía un `CHECK` en `personas` (ej. `motivo_baja`, `situacion_laboral`,
`tipo_vivienda`) se mueve a una tabla nueva, el mismo `CHECK` se recrea ahí — nunca se pierde ni se
deja flotando en `personas`. Las columnas de `personas_referencias` nunca tuvieron `CHECK` propio en
`personas` (solo `NOT NULL`, validado en la aplicación), así que no hay ninguno que trasladar para
ese grupo.

## 5. Ajuste aprobado sobre el diseño — grupo Baja (16/10/2026, Joel)

### `personas_baja`: upsert, nunca `DELETE` (a diferencia de `personas_conyuge`)

`personas_conyuge` (grupo Cónyuge) usa el patrón "DELETE + INSERT si corresponde": la fila se borra
en cuanto deja de aplicar (estado civil que ya no exige cónyuge). Para `personas_baja` ese mismo
criterio **rompería un comportamiento ya probado por humo** (`pruebas/humo_http.sh`, desde antes de
esta normalización): al reactivar un cliente, `PersonaDAO.marcarReactivada` a propósito nunca tocó
`motivo_baja`/`detalle_baja`/`fecha_baja`/`dado_de_baja_por` — quedan como **historial de la última
baja**, aunque el cliente ya esté `ACTIVA` de nuevo. Borrar la fila al reactivar habría sido un
cambio de comportamiento, no solo de forma (REGLAS_DESARROLLO.md §13).

**Decisión de Joel**: `personas_baja` usa upsert (`INSERT ... ON CONFLICT (id_persona) DO UPDATE`)
en `ServicioClientes.darDeBaja` — una sola fila por persona, sobreescrita si se la da de baja más de
una vez; `ServicioClientes.reactivar` **no toca esta tabla en absoluto** (ni siquiera para
actualizarla), igual que hoy. `estado_persona` se queda en `personas` (es el flag vigente, no un
dato del evento de baja — mismo criterio que `estado_civil` se quedó cuando salió `personas_conyuge`).

## 6. Ajuste aprobado sobre el diseño — grupo Laborales (16/10/2026, Joel)

Último grupo de la normalización — cierra `personas` en 15 columnas (28 − 10 de este grupo − 1
`lugar_trabajo`, legado sin reemplazo).

### `personas_laborales`: mismo patrón que `personas_domicilio` (1–1, siempre existe, upsert)

PK = FK (`id_persona`, clave compartida, sin id propio). `situacion_laboral` `NOT NULL` (CHECK
igual que tenía en `personas`); `empleador_*` nulables (obligatorios en la aplicación,
`ServicioClientes.validarLaboral`, solo si la situación laboral es `ASALARIADO_SECTOR_PRIVADO` o
`FUNCIONARIO_PUBLICO` — igual que hoy). `ingreso_neto_comprobado` y `presenta_comprobante_ingreso`
`NOT NULL` (ya lo eran en `personas`); `cuota_otras_deudas_mensual` `NOT NULL` (0 si no tiene —
entra en la fórmula de capacidad de pago, nunca puede faltar); `otros_ingresos_mensuales` y
`gastos_fijos_mensuales` nulables (informativos, D-127, Joel no pidió que sean obligatorios).

### `lugar_trabajo`: se elimina sin reemplazo

Tercer legado que el diseño original (§3) ya planeaba eliminar junto con `direccion_laboral`/
`referencias` ("ya casi vacías"), pero el grupo Domicilio solo quitó esas 2 — `lugar_trabajo`
quedó pendiente para este grupo. Solo se usaba como *fallback* de pantalla (precompletar
"Dirección laboral (empleador)" cuando `empleador_direccion` estaba vacío, `clientes/ficha.jsp`) —
ningún otro lugar la leía. Se quita la columna, el hidden de `ficha.jsp` y el *fallback* en sí (ya
no hace falta: `empleador_direccion` existe desde hace rato).

### Único método para los datos de capacidad de pago (ingreso, cuota de otras deudas)

`ServicioClientes.obtenerLaboral(Connection, idPersona)` es la única fuente, usada por los 4
lugares que antes leían `persona.getIngresoNetoComprobado()`/`getCuotaOtrasDeudasMensual()` en
vivo: `ServicioSolicitudes.crear`/`simular`/`enviarAAprobacion` y la revalidación RF11 de
`ServicioFormalizacion.calcularCapacidadActualFormalizacion`. Mismo patrón que
`obtenerTextoDomicilio` del grupo Domicilio: un solo punto de lectura, para no tener que encontrar
y actualizar la lógica de capacidad de pago en varios lugares si cambia cómo se guarda el dato.

### `solicitudes_credito`/`prestamos`: sin cambios, el snapshot queda igual

Confirmado leyendo el código: `solicitudes_credito.ingreso_cliente_evaluado`/
`ingreso_codeudor_evaluado`/`porcentaje_afectacion`/`limite_afectacion_aplicado` ya existían como
columnas propias desde antes de esta normalización, y ya se recalculaban leyendo el valor
**actual** de la persona en 3 momentos (`crear`, `simular` mientras `BORRADOR`, `enviarAAprobacion`)
y se congelaban recién al enviar a aprobación — `ServicioAprobacionCredito.aprobar` nunca vuelve a
leer `Persona`/`PersonaLaboral`, solo usa las columnas ya congeladas de la solicitud. Mover el
ingreso/cuota de `personas` a `personas_laborales` no cambia nada de este comportamiento: los 3
puntos que leían el valor en vivo ahora lo leen de `personas_laborales` en vez de `personas`, con
el mismo resultado.

### Probado en pantalla (17/10/2026, Joel) — grupo cerrado, `norm-laborales-probado`

Con Jesús Acosta (id 1, único cliente de la base limpia, cargado a mano — ver `AVANCE.md` D-155):
- Comerciante sin campos de empleador exigidos; cambio a Asalariado exige los 5 datos del
  empleador (`empleador_nombre`/`ruc`/`direccion`/`telefono`/`cargo`); completados, se guardan y
  se leen igual tras F5; vuelta a Comerciante, los campos desaparecen sin exigirlos — confirma
  que la obligatoriedad condicional (`ServicioClientes.validarLaboral`) sigue funcionando igual
  que cuando vivía en `personas`.
- `otros_ingresos_mensuales`/`gastos_fijos_mensuales` (informativos, nulables): confirmado que no
  entran en la fórmula de capacidad de pago — solo `ingreso_neto_comprobado` y
  `cuota_otras_deudas_mensual` la afectan.
- Solicitud SOL-2026-0001 creada y enviada a aprobación con los datos ACTUALES de
  `personas_laborales` en el momento del envío (`ServicioSolicitudes.enviarAAprobacion`, sin
  cambios de este grupo — ver el análisis de "reglas de decisión del crédito" del 17/10/2026):
  afectación = (cuota simulada 483.199 + cuota de otras deudas 250.000) ÷ ingreso 5.700.000 =
  12,86 % → mostrada con un decimal como **12,9 %**.
- Verificado por `SELECT` contra `sgcpc_db_pruebas` (solo lectura, `sgcpc_user`): `personas` con
  15 columnas; 1 persona y 1 fila en `personas_laborales` (1 por persona, sin huérfanas); Jesús
  (id 1) con `situacion_laboral = ASALARIADO_SECTOR_PRIVADO`, `ingreso_neto_comprobado = 5700000`,
  `cuota_otras_deudas_mensual = 250000`; SOL-2026-0001 con `ingreso_cliente_evaluado = 5700000`,
  `porcentaje_afectacion = 0.1286`.

**Problemas encontrados durante la prueba (Expediente "2 de 4", comprobante de domicilio nunca
cargado por el cargador) son preexistentes, no del grupo Laborales** — investigados y confirmados
el 17/10/2026 contra `v1.0-entrega` (el tag más antiguo), ver
`docs/funcionamiento/RF03_expediente.md §10` y `AVANCE.md` D-155. No bloquean el cierre de este
grupo.

## 7. Auditoría de normalización de toda la base (17/10/2026, solo lectura, sin aplicar nada)

Con `personas` ya normalizada (los 5 grupos, tags `norm-*-probado`), Joel pidió revisar con la
misma vara las **33 tablas restantes** de `bd/01_esquema.sql` (34 en total): cohesión, 1FN, 2FN/
3FN, columnas vacías o legado, catálogos de más o de menos, claves/índices, y restos de codeudor
en el esquema — distinguiendo problemas reales de copias históricas intencionales.

**Método**: se releyó `01_auditoria_inicial_v1.0.md §7` (la auditoría original, 15/10/2026, hecha
con la base poblada por el cargador) y `04_esquema_actual.md`, se contrastó cada hallazgo contra
el `bd/01_esquema.sql`/`bd/02_indices.sql` actuales, y se verificó con `grep` en `src/main/java`
qué columnas de verdad usa el código (no solo qué DAO las mapea). **No se pudo verificar con
`SELECT` de conteo de filas**: `sgcpc_db_pruebas` está vacía a propósito desde el cierre del Paso
1 de la tanda anterior (D-155) — para la "mayormente vacía" se citan en su lugar los conteos reales
ya registrados en `01_auditoria_inicial_v1.0.md` (base poblada, 75 personas) cuando aplica, y se
marca explícitamente cuándo un hallazgo es solo de código (sin dato de filas).

**Resultado general**: con la normalización de `personas` ya cerrada, **1FN y 3FN quedan
satisfechas en toda la base** — `personas` era el único caso de cada una (confirmado en
`01_auditoria_inicial_v1.0.md §7.1`, "29 de 30 tablas cumplen 3FN sin reparos"; la única salvedad
ya se resolvió). 2FN nunca tuvo ningún caso real (todas las PK son de una sola columna, salvo
`numeradores`, con un único atributo no clave que depende de las dos partes de su clave
compuesta). Lo que queda son hallazgos de **cohesión/columnas sin uso/índices/nombres**, no de
forma normal.

### a) Tabla resumen

| Tabla | Columnas | Veredicto | Detalle |
|---|---|---|---|
| `usuarios` | 15 | Bien | Identidad + credenciales + estado de cuenta, un solo tema. Autorreferencia `creado_por` correcta (§7.9 auditoría original). |
| `parametros_sistema` | 10 | Problema (menor) | `clave_parametro` sin índice/UNIQUE pese a ser la columna por la que más se filtra — ver grupo E. |
| `limites_tasa_usura` | 7 | Bien | Serie histórica (`vigente_desde`/`vigente_hasta`), no un catálogo — diseño correcto para un valor que cambia en el tiempo y debe conservar el histórico. |
| `personas` | 15 | Bien | **Normalización terminada** (los 5 grupos). Sin columnas ajenas a la identidad de la persona. |
| `personas_referencias` | 5 | Bien | Grupo repetido resuelto (1–N real, sin límite fijo). |
| `personas_conyuge` | 4 | Bien | 1–1 opcional, upsert/delete según corresponda. |
| `personas_baja` | 5 | Bien | 1–1, historial de la última baja (upsert, nunca se borra). |
| `personas_domicilio` | 7 | Bien | 1–1 siempre existe; `id_departamento` eliminado (JOIN desde `id_ciudad`). |
| `personas_laborales` | 13 | Bien | 1–1 siempre existe; único punto de lectura (`obtenerLaboral`). |
| `archivos_binarios` | 10 | Bien | Contenido + metadatos de un solo archivo, un tema. `cargado_por` sin índice — ver grupo E. |
| `tipos_documento` | 10 | **Problema** | Catálogo correcto en su diseño, pero 4 de sus 10 columnas (`aplica_cliente`, `aplica_codeudor`, `requiere_firma`, `origen_esperado`) nunca se leen en ninguna decisión de negocio — ver grupo A. |
| `documentos_expediente` | 13 | Bien | Un documento por fila; autorreferencia de versionado (`id_documento_reemplazado`) correcta (§7.9). FK sin índice (`id_tipo_documento`, `id_documento_reemplazado`) — grupo E. |
| `consentimientos_crediticios` | 8 | Bien | Un consentimiento por fila, un tema. FK sin índice — grupo E. |
| `consultas_crediticias` | 12 | Bien | Una consulta al buró por fila. Sin índice secundario por `id_persona` (no estaba en la auditoría original; se agrega a grupo E). |
| `solicitudes_credito` | 34 | **Copia histórica** | Mezcla datos de solicitar/simular/aprobar/rechazar/cancelar — pero son fases SUCESIVAS de la MISMA solicitud (nunca dos temas distintos), con nulidad condicionada al estado. `ingreso_cliente_evaluado`/`ingreso_codeudor_evaluado`/`porcentaje_afectacion`/`limite_afectacion_aplicado` (congelados al enviar) y el par propuesto/aprobado de tasa/CFT/cuota son copias deliberadas — ver sección c. |
| `prestamos` | 29 | **Copia histórica** | Mezcla contrato/anulación/cierre documental de UN préstamo — mismo criterio que arriba. `tasa_tna`/`tasa_tea`/`cft`/`tasa_mensual`/`cuota_contractual` son la condición pactada, congelada al formalizar — ver sección c. |
| `cargos_operacion` | — | **Eliminada (17/10/2026)** | Sin ningún uso en el código (RN-17, de la especificación maestra — fuera del anteproyecto firmado) — ver grupo B y §8. |
| `planes_pago` | 8 | Bien | Un plan de pagos por fila, versionado (`version_plan`/`vigente`) correcto. |
| `cuotas` | 19 | Bien (corregido 17/10/2026) | Mezcla original/mora/pagado — todo es de UNA cuota, cohesión correcta. `total_programado` ya tiene `CHECK` ligándolo a capital+interés+tributos (`chk_cuotas_total_programado`, grupo C, §8) — mismo criterio que `pagos`/`aplicaciones_pago`. `saldo_capital_posterior` es derivable pero guardado por legibilidad/performance, aceptado (§7.2 original). |
| `liquidaciones_anticipadas` | 19 | Bien | Una liquidación por fila; mezcla cálculo/aplicación/constancia pero de LA MISMA liquidación — cohesión correcta. FK sin índice (`id_prestamo`) — grupo E. |
| `sesiones_caja` | 11 | Bien | Una sesión de caja por fila, apertura+cierre de LA MISMA sesión. |
| `desembolsos` | 11 | Bien (corregido 17/10/2026) | `monto_neto_entregado` ya tiene `CHECK` ligándolo a `monto_aprobado_snapshot - monto_deducciones` (`chk_desembolsos_monto_neto`, grupo C, §8). `monto_deducciones` (siempre 0, RN-17) queda pendiente de evaluar si se elimina, para el refactor. |
| `reversiones_desembolso` | — | **Eliminada (17/10/2026)** | Sin ningún DAO/modelo/servicio Java — la funcionalidad que anticipaba (RN-13/PO-13, especificación maestra) no está en el anteproyecto firmado, que cubre el desembolso con RF14/RF16/RF17 (protección antes de confirmar) — ver grupo B y §8. |
| `pagos` | 15 | Bien | Un pago por fila. Ya tiene los 2 `CHECK` de consistencia que `cuotas`/`desembolsos` no tienen — ejemplo de la práctica correcta. |
| `aplicaciones_pago` | 10 | Bien | Una aplicación de un pago a una cuota/anticipo, con `CHECK` de consistencia completo. |
| `reversiones_pago` | 15 | Bien (hallazgo viejo corregido) | Flujo SOLICITADA/AUTORIZADA/RECHAZADA con `CHECK` de consistencia completo (`chk_reversion_pago_consistencia`). **La auditoría original (§7.4) decía que esta tabla NO tenía `trg_bloquear_eliminacion` — es incorrecto, lo tiene desde el commit inicial `b81973a` (E0)**; se corrige el registro, ver grupo F. |
| `auditoria_operaciones` | 12 | Bien | Un evento de auditoría por fila; FK polimórfica (`tipo_entidad`+`id_entidad`) documentada como limitación conocida del modelo relacional, no un error. |
| `movimientos_caja` | 11 | Bien | Un movimiento de caja por fila; FK polimórfica (`referencia_tipo`+`id_referencia`), mismo patrón que arriba, consistente. |
| `gestiones_cobranza` | 12 | Bien | Una gestión de cobranza por fila, un tema. |
| `feriados` | 4 | Bien | Catálogo simple, correcto. |
| `facturas` | 18 | Copia histórica | `cliente_nombre`/`cliente_documento` son copia textual de `personas` al momento de emitir — exigencia fiscal, ya documentada (§7.1 original) — ver sección c. FK sin índice (`id_pago`) — grupo E. |
| `numeradores` | 3 | Bien | PK compuesta correcta, sin violación de 2FN. |
| `departamentos` | 3 | Bien | Catálogo simple, correcto. |
| `ciudades` | 4 | Bien | Catálogo correcto, con su FK a `departamentos` (resuelve la dependencia transitiva que antes tenía `personas`). |
| `motivos_rechazo_solicitud` | 6 | Bien | Catálogo correcto — el caso de uso que SÍ justifica una tabla en vez de un `CHECK` (editable sin tocar código, ya usado para desactivar 3 motivos sin borrar historial, ver `RF10_rechazar.md §9`). |

**Ningún catálogo innecesario encontrado** — no hay ninguna tabla de referencia que debería ser,
al revés, una lista fija en código o un `CHECK`; `motivos_rechazo_solicitud` es, de hecho, el
ejemplo de cuándo SÍ conviene una tabla (editable en caliente) en vez de un enum.

### b) Problemas reales, ordenados por importancia, agrupados

#### Grupo F — Corrección de un hallazgo de la auditoría original (prioridad: corregir el registro, no el código)

**Estado: aplicado 17/10/2026** — nota agregada en `01_auditoria_inicial_v1.0.md §7.4`.

**Problema**: `01_auditoria_inicial_v1.0.md §7.4` afirma que `reversiones_pago` no tiene
`trg_bloquear_eliminacion`, a diferencia de `reversiones_desembolso`. Es falso: confirmado con
`git log -p -- bd/02_indices.sql`, el array de tablas protegidas incluye `'reversiones_pago'`
desde el primer commit del esquema (`b81973a`, "E0: base del proyecto SGCPC") — nunca faltó.

**Causa**: error de la auditoría original, no un cambio posterior (el commit que agregó el índice
`ix_reversiones_pago_estado` en E9 ya menciona en su propio mensaje "BEFORE DELETE ya existente en
reversiones_pago desde E0").

**Propuesta**: agregar una nota en `01_auditoria_inicial_v1.0.md §7.4` marcando este punto como
corregido (nunca se tocaría el contenido congelado de esa auditoría salvo para esto), para que no
se repita en la defensa. Archivos: solo documentación. Riesgo: ninguno.

#### Grupo A — 4 columnas de `tipos_documento` sin ningún efecto en el código (incluye el único resto real de codeudor en el esquema)

**Estado: aplicado 18/10/2026** — `aplica_codeudor` ya se había eliminado con el retiro de
codeudor/garante (Commit 3, ver §9); `aplica_cliente`/`requiere_firma`/`origen_esperado` se
eliminaron en la ronda de columnas sin uso, camino (b) de la propuesta de más abajo —
confirmado antes con grep que ninguna se duplicaba con otra cosa, mismo criterio que D-117.

**Problema**: `tipos_documento` tiene 10 columnas; `codigo_tipo`, `nombre_tipo`, `contexto`,
`obligatorio_desembolso` y `activo` SÍ se consultan en decisiones reales (confirmado con `grep`:
`contexto` filtra `listarActivosPorContexto`; `obligatorio_desembolso` alimenta el checklist de
desembolso en `ServicioFormalizacion`/`verificar_listo_para_desembolsar`). Las otras 4 —
**`aplica_cliente`, `aplica_codeudor`, `requiere_firma`, `origen_esperado`** — se leen de la base
en `TipoDocumentoDAO`/`DocumentoExpedienteDAO` y se guardan en el modelo `TipoDocumento`
(`setAplicaCliente`/`setAplicaCodeudor`/`setRequiereFirma`/`setOrigenEsperado`), pero **ningún
servicio ni controlador llama jamás a sus getters** (`isAplicaCliente()`/`isAplicaCodeudor()`/
`isRequiereFirma()`/`getOrigenEsperado()`, cero resultados en todo `src/main/java` fuera de la
propia clase y los DAO que las mapean). La aplicabilidad real de un documento (si corresponde al
cliente, al codeudor, o si tiene que venir firmado) está hoy **hardcodeada** en
`ServicioClientes.CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN` (los mismos 4 documentos, sin diferenciar
cliente de codeudor) — las columnas del catálogo que en teoría permitirían esa diferenciación
nunca se usan para ese fin.

**Corrección (17/10/2026, decisión de Joel — ver §9)**: en el momento de escribir este punto se
clasificó `solicitudes_credito.id_codeudor`/`ingreso_codeudor_evaluado` como "funcionalidad
activa, no un resto", distinguiéndolos de `tipos_documento.aplica_codeudor`. Joel corrigió esa
lectura: la política del anteproyecto es sin garante ni codeudor, sin excepción — **todo** lo de
codeudor/garante es un resto, incluidos `id_codeudor`/`ingreso_codeudor_evaluado`. El inventario
completo para eliminarlo todo, en orden, está en §9 (no se tocó en el grupo "Integridad" de §8,
va en su propio refactor).

**Propuesta** (a decidir con Joel, dos caminos, sin aplicar ninguno):
- (a) Implementar lo que el diseño original previó: usar `aplica_cliente`/`aplica_codeudor` para
  armar la lista de documentos exigidos por rol (en vez del array fijo), y `requiere_firma` para
  validar que un documento cargado como "firmado" realmente lo requiera, `origen_esperado` para
  controlar de dónde debe venir el archivo. Archivos: `ServicioClientes` (reemplaza el array fijo
  por una consulta a `tipos_documento` filtrada por rol), `TipoDocumentoDAO`. Riesgo: medio — hoy
  cliente y codeudor comparten exactamente los mismos 4 documentos, así que el comportamiento
  visible no cambiaría salvo que alguien cargue datos distintos en el catálogo.
- (b) Eliminar las 4 columnas si nunca se les va a dar ese uso (`tipos_documento` quedaría con 6
  columnas, todas con efecto real). Archivos: `bd/01_esquema.sql`, `TipoDocumento.java`,
  `TipoDocumentoDAO.java`, `DocumentoExpedienteDAO.java`, el seed de `bd/03_parametros.sql`.
  Riesgo: bajo (se borra código muerto), pero es un cambio de esquema que hay que decidir
  explícitamente, no asumir.

#### Grupo C — 2 columnas derivadas sin `CHECK` que las proteja (ya señalado en la auditoría original, ahora con la comparación concreta)

**Estado: aplicado 17/10/2026** — verificado antes en `ServicioMotorFinanciero`/
`ServicioDesembolso` que la suma es exacta por construcción (ver §8), los 2 `CHECK` de más abajo
se agregaron tal cual.

**Actualización (18/10/2026, ronda de columnas sin uso)**: `monto_deducciones` se eliminó (nunca
se leía para nada, siempre valía 0 — ver grupo A del mismo motivo). El `CHECK` de `desembolsos`
quedó `CHECK (monto_neto_entregado = monto_aprobado_snapshot)`, sin esa columna.

**Problema**: `cuotas.total_programado` (no ligado a `capital_programado + interes_programado +
tributos_programados`) y `desembolsos.monto_neto_entregado` (no ligado a
`monto_aprobado_snapshot - monto_deducciones`) son los 2 únicos derivados sin resguardo de toda la
base — confirmado comparando contra `pagos` (`CHECK (monto_recibido + monto_redondeo =
monto_aplicado + monto_vuelto)`) y `aplicaciones_pago` (`CHECK (subtotal_aplicado = ...)`), que SÍ
protegen la misma clase de invariante. `ServicioMotorFinanciero`/`ServicioDesembolso` siempre
calculan estos valores de forma consistente hoy (ningún caso real desalineado), pero nada en el
esquema lo garantiza si un código futuro escribiera estas columnas por otro camino.

**Propuesta**: agregar los 2 `CHECK` faltantes, mismo patrón que los existentes:
```sql
ALTER TABLE cuotas ADD CONSTRAINT chk_cuotas_total_programado
    CHECK (total_programado = capital_programado + interes_programado + tributos_programados);
ALTER TABLE desembolsos ADD CONSTRAINT chk_desembolsos_monto_neto
    CHECK (monto_neto_entregado = monto_aprobado_snapshot - monto_deducciones);
```
Archivos: `bd/01_esquema.sql` (y una migración si se aplica a una base con datos). Riesgo: bajo —
si el código ya siempre cumple la relación (de ser así, el `ALTER` entra sin violar ningún dato
existente); si alguna fila real NO la cumpliera, el intento de agregar el `CHECK` fallaría y
avisaría de un bug real que hoy no se ve. No se verificó con `SELECT` contra datos reales porque
la base de pruebas está vacía ahora — a confirmar la próxima vez que haya datos cargados, antes
de aplicar.

#### Grupo B — 2 tablas sin ningún código, ya documentadas como decisión (sin acción, solo confirmación)

**Estado: aplicado 17/10/2026 — las 2 tablas se eliminaron.** Decisión de Joel, con la corrección
de que RN-13/PO-13/RN-17 son de la especificación maestra, no del anteproyecto firmado (que cubre
el desembolso con RF14/RF16/RF17, protección antes de confirmar) — ver el detalle completo y la
justificación en §8.

**`cargos_operacion`**: 0 usos fuera de un comentario que explica por qué (`RN-17`, decisión de
no tener descuentos en V1). **`reversiones_desembolso`**: 0 usos, ningún DAO/modelo/servicio —
a diferencia de `cargos_operacion`, no hay ningún comentario en el código explicando la ausencia;
la tabla tiene columnas y FK pero **no evolucionó** junto con `reversiones_pago` (que en E9 ganó
el flujo SOLICITADA/AUTORIZADA/RECHAZADA con su `CHECK` de consistencia) — quedó congelada en el
diseño original de E0, nunca se construyó el servicio. No es un error de normalización, es una
funcionalidad del anteproyecto que no se llegó a implementar.

**Propuesta**: ninguna acción de esquema. Confirmar con Joel si la reversión de desembolso entra
en el alcance final de la tesis (si sí, es un RF/servicio nuevo a planificar aparte, no un ajuste
de normalización) o si se documenta como alcance explícitamente no cubierto. Riesgo: ninguno,
es una decisión de alcance, no de diseño de datos.

#### Grupo D — 2 nombres de columna FK inconsistentes (cosmético, ya señalado en la auditoría original)

**Estado: aplicado 18/10/2026** — renombradas a `id_limite_tasa`/`id_gerente_revisor` (columna y
Java, getter/setter incluidos), en la ronda de columnas sin uso.

`prestamos.limite_tasa_id` (hoy `id_limite_tasa`) y `sesiones_caja.gerente_revisa_id` (hoy
`id_gerente_revisor`) eran las únicas 2 de 87 FK con el
sufijo `_id` en vez del prefijo `id_` del resto (`id_limite_tasa_aplicado`, `id_persona`, etc.).

**Propuesta**: renombrar a `id_limite_tasa`/`id_gerente_revisor` (o similar) por prolijidad.
Archivos afectados (todo lo que lee esas columnas por nombre): `PrestamoDAO`, `ServicioCaja`,
`SesionCajaDAO`, y cualquier `.jrxml`/JSP que las muestre. Riesgo: medio-bajo — cambio mecánico
pero con bastante superficie (hay que encontrar cada `resultado.getLong("limite_tasa_id")`/
`"gerente_revisa_id"` y su contraparte en los `INSERT`/`UPDATE`). Valor real bajo (no es un bug,
solo estilo) — candidato a posponer indefinidamente o descartar.

#### Grupo E — FK sin índice propio (ya señalado en la auditoría original, actualizado tras la normalización de `personas`)

**Estado: aplicado 17/10/2026** — los 10 índices (todos salvo `solicitudes_credito.id_codeudor`,
que queda para el refactor de §9) se agregaron en `bd/02_indices.sql`.

Confirmado contra el `bd/02_indices.sql` actual — lista vigente de columnas FK sin índice
secundario (más allá de la PK): `archivos_binarios.cargado_por`; `consentimientos_crediticios.
id_persona`/`id_solicitud`; `consultas_crediticias.id_persona` (nuevo, no estaba en la auditoría
original); `documentos_expediente.id_tipo_documento`/`id_documento_reemplazado`;
`facturas.id_pago`; `liquidaciones_anticipadas.id_prestamo`; `parametros_sistema.clave_parametro`
(la que más se filtra, sin ningún índice); `personas_domicilio.id_ciudad` (nuevo, consecuencia de
la normalización); `solicitudes_credito.id_codeudor` (`id_cliente` ya está parcialmente cubierto
por el índice único parcial de "una sola solicitud abierta"). `personas.id_departamento`/
`id_ciudad` de la lista original ya no aplican (esas columnas se eliminaron).

**Propuesta**: ninguna acción ahora — mismo veredicto que la auditoría original, con el tamaño
actual de la base (hoy vacía, antes algunas decenas/cientos de filas) ninguno es un problema de
rendimiento real. Queda la lista actualizada para cuando haga falta un script de índices nuevos.
Riesgo: ninguno por ahora.

### c) Copias históricas intencionales (correctas, no normalizar)

- **`prestamos.tasa_tna`/`tasa_tea`/`cft`/`tasa_moratoria`/`tasa_punitoria`/`tasa_mensual`/
  `cuota_contractual`**: la condición pactada al formalizar, congelada para siempre — si
  `limites_tasa_usura` cambia después, el contrato ya firmado no debe cambiar. Verificado en
  código: `ServicioAprobacionCredito.aprobar` nunca vuelve a leer `Persona`/`PersonaLaboral`, usa
  las columnas ya congeladas de la solicitud; `ServicioFormalizacion` solo revalida (no
  recalcula) el tope de tasa al formalizar.
- **`solicitudes_credito.ingreso_cliente_evaluado`/`ingreso_codeudor_evaluado`/
  `limite_afectacion_aplicado`/`porcentaje_afectacion`**: el ingreso/afectación con los que de
  verdad se decidió el crédito, congelados al enviar a aprobación (RF06) — confirmado en pantalla
  el 17/10/2026 con SOL-2026-0001 (Jesús Acosta): `ingreso_cliente_evaluado = 5.700.000`,
  `porcentaje_afectacion = 0.1286`, mostrados en la ficha como "(evaluado al enviar)" en vez del
  valor actual de `personas_laborales` (que puede haber cambiado después).
- **`solicitudes_credito`, el par propuesto/simulado vs. aprobado** (`tasa_tna_propuesta` vs.
  `tasa_tna_aprobada`, etc.): un gerente puede aprobar con condiciones distintas a las
  solicitadas (aunque RN-07 hoy exige que sean las mismas) — son dos momentos reales de la misma
  solicitud, no un duplicado.
- **`facturas.cliente_nombre`/`cliente_documento`**: copia textual de `personas` al momento de
  emitir — exigencia fiscal paraguaya (una factura ya emitida no cambia si el cliente corrige su
  nombre después). Ya documentado en `01_auditoria_inicial_v1.0.md §7.1`.
- **PDF ya emitidos (`documentos_expediente`/`archivos_binarios`, vía `id_archivo`)**: el contrato,
  pagaré, plan de pagos, factura, comprobante de desembolso y autorización del buró, una vez
  generados, quedan guardados como bytes fijos (D-94) — no se regeneran si el domicilio, la
  situación laboral o cualquier otro dato de la persona cambia después. Confirmado en pantalla
  durante el grupo Domicilio (16/10/2026): el documento 363 (domicilio viejo) quedó `REEMPLAZADO`
  sin tocarse tras cambiar el domicilio del cliente.
- **`cuotas.saldo_capital_posterior`**: derivable sumando `capital_programado` de las cuotas
  anteriores del mismo plan, pero guardado directo por columna — práctica común en tablas de
  amortización (performance/legibilidad), no se considera un problema real (§7.2 de la auditoría
  original, mismo criterio que `aplicaciones_pago.subtotal_aplicado` si no tuviera su `CHECK`).

## 8. Auditoría de reversiones (17/10/2026, solo lectura — decisiones de Joel tras §7)

Joel decidió sobre el grupo B de §7: **`cargos_operacion` se elimina** (no hay comisiones ni
gastos en el anteproyecto) — aplicar recién cuando se apruebe el plan completo, sin tocar nada
ahora. Antes de decidir sobre `reversiones_desembolso`, pidió entender a fondo cómo funciona hoy
la reversión de pago (para no perder ninguna pieza del diseño si algún día se construye el
equivalente de desembolso) y confirmar con evidencia si de verdad no hay ningún código.

### a) `reversiones_pago` (RF25) — cómo funciona hoy, completo

**Solicitar (cajera)** — `POST /reversiones/solicitar` (`ControladorReversionesServlet`) →
`ServicioReversiones.solicitarReversion`. Botón "Solicitar reversión" en `cobranza/ficha.jsp`
("Historial de cobros"), visible **solo en la fila de la última operación** del préstamo
(`ServicioCobranza.listarHistorialCobros` decide cuál, regla de `RF25_reversion_cobro.md §6c`:
"solo se puede revertir la última operación, sin importar la sesión de caja en que se hizo").
Valida, en orden: el pago existe y está `CONFIRMADO`; no hay otra solicitud pendiente/rechazada
sobre el mismo pago; sigue siendo la última operación; el cierre documental del préstamo no está
`COMPLETO` (RF14/RF25, hallazgo de Joel 13/10/2026); si el cobro generó factura, la cajera declaró
con una casilla obligatoria que tiene en su poder el original y las copias
(`ServicioReversiones.validarDeclaracionFacturaCajera`, pura). Deja la fila en `SOLICITADA`, sin
tocar dinero ni cuotas.

**Autorizar o rechazar (gerente)** — bandeja `/reversiones` (`Permiso.
AUTORIZAR_REVERSION_O_DIFERENCIA`). **Rechazar** (`ServicioReversiones.rechazarReversion`): pide
motivo, no toca nada más — el pago sigue `CONFIRMADO`, la cajera puede volver a solicitar
(reutilizando la misma fila, `ReversionPagoDAO.marcarResolicitada`, ya que `id_pago` es `UNIQUE`
en `reversiones_pago`). **Autorizar** (`ServicioReversiones.autorizarReversion`), una sola
transacción, en orden: (1) revalida `SOLICITADA` + última operación + cierre documental no
completo; (2) si hay factura, exige la casilla propia del gerente (declaración independiente de
la de la cajera, DNIT RG 118/2017); (3) exige caja `ABIERTA` de **hoy** con saldo teórico
suficiente; (4) restaura cada cuota que el pago tocó con
`ServicioMotorFinanciero.revertirAplicacionEnCuota` (la inversa exacta, nunca una resta a mano);
(5) si era anticipo de capital, el plan nuevo queda como versión histórica (nunca se borra, RN-13)
y el anterior vuelve a vigente; si era cancelación total, la liquidación se marca `REVERTIDA`;
(6) recalcula el estado del préstamo con `calcularEstadoSegunCuotas` (nunca
`recalcularEstadoPrestamo`, que no sabe deshacer un `FINALIZADO`); (7) si había factura, la anula
con una Nota de anulación (`NAN-aaaa-NNNNNN`, numerador propio); (8) la gestión de cobranza que
ese pago haya cerrado vuelve a abierta; (9) un movimiento `COMPENSACION` (signo −1) en la caja de
hoy, por el monto devuelto; (10) el pago pasa a `REVERTIDO`, la reversión a `AUTORIZADA`.

**Nada se borra**: pago, liquidación y factura quedan como filas reimprimibles, marcadas
(`REVERTIDO`/`REVERTIDA`/`ANULADA`) — los PDF ya emitidos (recibo, factura) se regeneran siempre
(nunca el caché de D-94) con una franja/sello "ANULADO" cuando el estado cambió, y una constancia
de reversión nueva (`constancia_reversion.jrxml`) reusa `reversiones_pago.id_archivo_comprobante`
(columna que ya existía sin usar) con número `REV-aaaa-NNNNNN` (usa `id_reversion_pago` como
secuencia, sin numerador aparte). La secuencia completa de cada intento (cada solicitud, cada
decisión, incluidas las sobrescritas por un reintento) queda en `auditoria_operaciones` — la
tabla `reversiones_pago` solo guarda el **último** estado.

**Dónde está**: `ServicioReversiones.java`, `ControladorReversionesServlet.java`,
`ReversionPagoDAO.java`, `ReversionPago.java`/`EstadoReversion.java`, `reversiones/bandeja.jsp` +
el botón en `cobranza/ficha.jsp`, `informe_reversiones.jrxml`. Detalle completo, con las 9
correcciones de pantalla y las 2 reglas agregadas tras la prueba de Joel (cierre documental
completo, doble control de factura): `docs/funcionamiento/RF25_reversion_cobro.md`.

### b) `reversiones_desembolso` — ¿de verdad sin código?

**Confirmado con `grep` exhaustivo, cero resultados reales** (un solo acierto, en un comentario de
`FiltroAutorizacion.java` que menciona "reversion" y "desembolso" por separado, sin relación):

- `**/*.java`: ningún modelo, DAO, servicio ni controlador (`ReversionDesembolsoDAO`,
  `ReversionDesembolso.java`, etc. no existen).
- `**/*.jsp`, `**/*.jrxml`: cero.
- `**/*.sql` (fuera de `bd/01_esquema.sql`/`02_indices.sql`, que solo definen la tabla): cero.
- `pruebas/humo_http.sh`: cero.
- `src/test/`: cero.

La tabla existe desde el primer commit (`b81973a`, "E0: base del proyecto SGCPC") con todas sus
columnas, FK y el trigger `trg_bloquear_eliminacion` — pero **nunca se construyó el servicio**.
A diferencia de `reversiones_pago`, que en E9 ganó el flujo `SOLICITADA`/`AUTORIZADA`/`RECHAZADA`
con su `CHECK` de consistencia (`chk_reversion_pago_consistencia`), `reversiones_desembolso`
quedó congelada en el diseño original y más simple de E0 (todas las columnas de ejecución
`NOT NULL`, como si la reversión siempre estuviera ya decidida) — nunca se la actualizó para
seguir el mismo patrón.

**¿Qué pasa hoy si un desembolso se hizo mal?** Nada — no hay ningún mecanismo. Revisado
`ServicioDesembolso.java` completo: sus únicos métodos públicos son de consulta
(`obtenerContratoGeneradoVigente`, `validarDocumentosVigentes`,
`construirMensajeSaldoInsuficiente`), de confirmación (`confirmarDesembolso`) y de comprobante
(`generarComprobantePreDesembolso`, `obtenerPdfComprobanteDesembolso`) — ninguno revierte nada.
`prestamos.estado_prestamo` solo admite `PENDIENTE_DESEMBOLSO`/`ACTIVO`/`EN_MORA`/`FINALIZADO`/
`ANULADO_ANTES_DESEMBOLSO` — no existe ningún estado de "desembolso revertido". La propia tabla
`desembolsos` SÍ tiene `estado_desembolso CHECK (... IN ('CONFIRMADO', 'REVERTIDO'))` y el índice
único parcial `ux_desembolsos_un_confirmado_por_prestamo` (pensado justo para permitir un segundo
desembolso `CONFIRMADO` después de revertir el primero) — el modelo de datos de `desembolsos` SÍ
está preparado para una reversión, pero **nada en el código llega jamás a escribir
`'REVERTIDO'`** (confirmado con `grep`: ningún `UPDATE`/`setEstadoDesembolso` a ese valor existe).

**¿Hay otro mecanismo que lo cubra?** Solo **RF14** (`ServicioFormalizacion.
anularAntesDeDesembolso`), y **no cubre este caso**: exige explícitamente
`estado_prestamo = 'PENDIENTE_DESEMBOLSO'` — una vez que el desembolso ya se confirmó (`ACTIVO`),
RF14 ya no aplica (`PrestamoNoPendienteDesembolsoException` lo rechaza). No hay ningún camino,
ni manual ni de negocio, para corregir un desembolso ya confirmado por error (monto equivocado,
préstamo equivocado, etc.) dentro de la aplicación.

**¿El anteproyecto pide esto?** Sí, explícitamente — `docs/00_especificacion_maestra.md`:
- **RN-13**: *"Nada se borra ni se edita: cobros y desembolsos se corrigen con reversión
  autorizada por el gerente."* — nombra desembolsos, no solo cobros.
- **PO-13. "Reversión de un cobro o desembolso"**: un único proceso de negocio descrito en
  términos genéricos ("la cajera busca la operación y solicita la reversión con motivo... si
  autoriza: se crea el movimiento inverso en la caja, se restauran saldos y estados, y se anula
  la factura asociada"), sin distinguir explícitamente cobro de desembolso en los pasos.

Es decir: el anteproyecto (en su versión de especificación maestra, Doc. 00) pidió un solo
proceso para los dos casos; el equipo implementó completo el de cobro (RF25, con todo el detalle
propio que un desembolso no tiene — DNIT, factura, anticipos) y dejó el de desembolso sin
construir, aunque le preparó la tabla desde el principio.

### c) Decisión (17/10/2026, Joel) — corrección a la recomendación de este análisis

**Corrección de Joel al punto b/c de arriba**: RN-13 y PO-13 son de
`docs/00_especificacion_maestra.md` (la especificación maestra, Doc. 00) — **no del anteproyecto
firmado** (`docs/RF_ANTEPROYECTO.md`), igual que el caso de codeudor (§9). El anteproyecto firmado
no pide una reversión de desembolso: pide, en cambio, que el desembolso se **proteja antes de
confirmarse**, no que se revierta después —
- **RF16**: *"El sistema debe permitir el desembolso solo si el checklist de firmados está
  completo, los documentos están vigentes, la caja del día está abierta y el saldo de la caja es
  suficiente."* (el checklist y la validación de caja, antes de confirmar).
- **RF17**: *"El sistema debe emitir el comprobante de desembolso para la firma del cliente antes
  de confirmar la entrega del efectivo, y registrar la salida en la caja."* (la firma del cliente
  es la salvaguarda, antes de que el efectivo salga).
- **RF14**: *"El sistema debe permitir al gerente anular un préstamo antes del desembolso
  registrando el motivo."* — la corrección de un préstamo mal armado se hace ANTES del
  desembolso, no después.
- **RNF12**: *"El sistema no debe permitir borrar operaciones registradas. Las correcciones se
  hacen por anulación o reversión, con motivo, autorización del gerente y registro en la
  auditoría."* — principio general, ya cumplido por RF14 (anulación, antes) y RF25 (reversión,
  para cobros) — no exige que exista una reversión para cada tipo de operación en particular.

**Decisión: `reversiones_desembolso` se elimina del esquema** (aplicado el 17/10/2026, grupo
"Integridad" de la normalización) — no es un requisito pendiente del anteproyecto firmado, es una
tabla que anticipaba una funcionalidad de la especificación maestra que el anteproyecto firmado
no terminó pidiendo, igual que `cargos_operacion` (RN-17, especificación maestra, también fuera
del anteproyecto firmado — ver la nota de alcance en `docs/00_especificacion_maestra.md`).
`cargos_operacion` se elimina por el mismo motivo y en el mismo grupo.

**Pendiente para el refactor (no se toca ahora)**: `desembolsos.monto_deducciones` siempre vale 0
desde que no hay cargos (política sin comisiones ni gastos, mismo fundamento de RN-17) — evaluar
si se elimina la columna junto con el `CHECK` nuevo (`chk_desembolsos_monto_neto`, ver abajo) en
la etapa de refactor, cuando se revise `tipos_documento`/codeudor.

### Aplicado (17/10/2026) — grupo "Integridad", último de la normalización

- **Eliminadas** `cargos_operacion` y `reversiones_desembolso` de `bd/01_esquema.sql` (tablas,
  `CHECK` propios, y sus 6 FK) y `reversiones_desembolso` del array de `trg_bloquear_eliminacion`
  en `bd/02_indices.sql`. Verificado antes con `grep` exhaustivo (java/jsp/jrxml/sql/humo/tests):
  cero uso real de las dos. Sin seeds que tocar (ninguna de las dos tenía filas en
  `bd/03_parametros.sql`).
- **Agregados 2 `CHECK`** (grupo C de §7): `chk_cuotas_total_programado` (`cuotas`) y
  `chk_desembolsos_monto_neto` (`desembolsos`), mismo criterio que ya tenían `pagos`/
  `aplicaciones_pago`. Verificado antes en `ServicioMotorFinanciero`/`ServicioDesembolso` que la
  suma es exacta por construcción en los únicos lugares que crean estas filas (sin redondeo
  independiente) — ver el detalle de la verificación en el chat del 17/10/2026.
- **Agregados 10 índices** de FK sin índice propio (grupo E de §7), en `bd/02_indices.sql`:
  `ix_archivos_binarios_cargado_por`, `ix_consentimientos_persona`, `ix_consentimientos_solicitud`,
  `ix_consultas_persona`, `ix_documentos_tipo_documento`, `ix_documentos_reemplazado`,
  `ix_facturas_pago`, `ix_liquidaciones_prestamo`, `ix_parametros_clave` (no `UNIQUE`:
  `clave_parametro` se repite entre versiones históricas del mismo parámetro),
  `ix_personas_domicilio_ciudad`. **`solicitudes_credito.id_codeudor` queda sin índice a
  propósito** — va con el refactor de codeudor (§9), no con este cierre.
- **No tocado en este grupo** (quedan para el refactor): grupo A (`tipos_documento.
  aplica_cliente`/`aplica_codeudor`/`requiere_firma`/`origen_esperado`), grupo D (nombres de FK
  inconsistentes, `prestamos.limite_tasa_id`/`sesiones_caja.gerente_revisa_id`), y todo lo de
  codeudor/garante de §9.

## 9. Auditoría de codeudor/garante (17/10/2026, solo lectura) — inventario completo para eliminación

### Aplicado (18/10/2026)

Ejecutado en 3 commits: Commit 1, `6e7da13` (código, JSP, 5 `.jrxml`, tests, humo); Commit 2
(esta documentación); Commit 3 (esquema — columnas `id_codeudor`/`ingreso_codeudor_evaluado`/
`aplica_codeudor`, parámetro `credito.exigir_garante`, tag `refac-codeudor-probado`). Mismo
patrón que §6/§8. Este inventario queda sin reescribir, como registro de la auditoría original
que fundamentó la decisión.

**Decisión de Joel**: se elimina **todo** lo relacionado a codeudor/garante, sin excepción — la
política del anteproyecto es "no se presta a quien no garantiza su propio ingreso", aunque traiga
garante. No es una funcionalidad activa desactivada por parámetro: es un resto de una versión
anterior del alcance que hay que retirar por completo.

**Contexto encontrado, relevante para la defensa**: `docs/RF_ANTEPROYECTO.md` (el texto firmado)
**no menciona garante ni codeudor en ningún punto** (`grep` sin resultados). Sí aparece en
`docs/00_especificacion_maestra.md` (la especificación maestra más amplia, Doc. 00, anterior al
anteproyecto firmado): RN-04, RN-22, RN-28, PO-02, "RF-08" (numeración propia de ese documento,
distinta de la del anteproyecto). RN-04 incluso registra una decisión previa explícita (D-125,
29/09/2026) en sentido contrario a la de hoy: *"La función de garante no se eliminó: con
cualquier valor del parámetro se lo puede seguir asignando manualmente."* — es decir, mantenerlo
disponible fue una decisión consciente en su momento; la de hoy la reemplaza. `REGLAS_DESARROLLO.
md §5` ya dice "Política del anteproyecto: solo ingreso fijo comprobable; NO se exige garante ni
codeudor... Ninguna pantalla, PDF, prueba ni muestra debe mostrar codeudor con esa política" —
redactado como una regla condicionada al parámetro (`credito.exigir_garante = nunca`); con la
decisión de hoy pasa a ser incondicional (no existe ningún valor del parámetro que lo reactive,
porque el parámetro mismo se elimina).

### Inventario completo, por capa

**Columnas/esquema**:
- `solicitudes_credito.id_codeudor` (FK a `personas`) y `ingreso_codeudor_evaluado` — más el
  `CHECK (id_codeudor IS NULL OR id_codeudor <> id_cliente)` y la FK `fk_solicitudes_codeudor`.
- `tipos_documento.aplica_codeudor` — ya identificado en §7 grupo A como columna sin ningún uso
  real en el código (ningún servicio llama a `isAplicaCodeudor()`); con la decisión de hoy, deja
  de ser "a decidir" y pasa directo a eliminar.
- **Columna con "garante" en el nombre que NO es resto de codeudor — no tocar**:
  `parametros_sistema` fila `credito.cuotas_alerta_garante` (clave literal). El nombre es legado
  de cuando solo alertaba al garante (RF-22 de Doc. 00); desde el 13/10/2026 (decisión de Joel,
  RF39/D1) también alimenta la cláusula de vencimiento anticipado del contrato y el pagaré, sin
  relación con el garante — usado en `ServicioFormalizacion.java:1196` y `ServicioMora.java:106`.
  Si se elimina el garante, **este parámetro se queda** (la cláusula de vencimiento anticipado no
  depende de él) — a lo sumo, renombrar la clave por prolijidad en otro momento, aparte de este
  plan.

**Parámetro**: `credito.exigir_garante` (bd/03_parametros.sql, valores válidos `nunca`/
`siempre`/`segun_monto` en `ServicioParametros.VALORES_EXIGIR_GARANTE`) — gobierna: (a) si
`asignarCodeudor` rechaza de entrada (ya rechaza siempre hoy, porque el valor real de producción
es `nunca`); (b) en `ServicioClientes` (RF02, precalificación), si un cliente sin comprobante de
ingresos **no califica directamente** o solo recibe un aviso ("va a necesitar un garante más
adelante") — funciones `motivoSinComprobanteSiAplica`/el chequeo inline en `alta`/`actualizar`/
`evaluarPrecalificacion` (líneas ~347, ~1064, ~1083, ~1098, ~1177 de `ServicioClientes.java`). **Importante**: hoy, con el valor real `nunca`, estas 5 lecturas del parámetro YA SIEMPRE toman la
rama "bloquea directo" — eliminar el parámetro y hardcodear esa rama **no cambia ningún
comportamiento visible**, solo quita la rama muerta y el parámetro en sí.

**Java — lógica de negocio**:
- `ServicioSolicitudes.java` (la clase con más menciones, 102): `determinaSiRequiereCodeudor`
  (pura), `validarCodeudorDistintoDeCliente` (pura), `sumarCuotasOtrasDeudas(cuotaCliente,
  cuotaCodeudor)` (pura — queda con un solo parámetro), `validarCodeudorSinDeuda` (RN-28),
  `asegurarCodeudorElegible` (con `Connection`), `MOTIVO_CODEUDOR_NO_ADMITIDO`/
  `motivoCodeudorNoAdmitidoSiAplica`, `asignarCodeudor` (el método completo de la acción
  `POST /solicitudes/{id}/codeudor` — se elimina entero), y los parámetros/variables `codeudor`/
  `laboralCodeudor`/`ingresoCodeudor` dentro de `crear`/`simular`/`enviarAAprobacion`/
  `previsualizarAfectacion`.
- `ServicioAprobacionCredito.java` (29): `etiquetaPersonaConsulta(..., Persona codeudor)`,
  `listarConsultasMasRecientes(..., Persona codeudor)`, y el manejo de `solicitud.getIdCodeudor()`
  dentro de `aprobar`/`rechazar`/`cancelar` (ya no hay nada que revalidar del codeudor).
- `ServicioFormalizacion.java` (48): los parámetros de reporte `TIENE_CODEUDOR`/
  `CODEUDOR_NOMBRE`/`CODEUDOR_CEDULA`/`CODEUDOR_DOMICILIO`/`CODEUDOR_TELEFONO` (contrato), la
  etiqueta "cliente y codeudor" vs. "el cliente" (quién firma), la fila condicional del pagaré
  (DÉCIMA/UNDÉCIMA según haya codeudor) y el mensaje de revalidación de capacidad ("... y las del
  codeudor"). El comentario ya conocido, `CODEUDOR_DOMICILIO` adaptado "al paso" durante la
  normalización de Domicilio, se resuelve de una: se elimina.
- `ServicioClientes.java` (16): la lógica de precalificación de RN-04 descrita arriba.
- `ServicioMotorFinanciero.java` (7): `sumarCuotasOtrasDeudas` (si existe una copia propia aparte
  de la de `ServicioSolicitudes` — a confirmar cuál llama a cuál al tocar el código; no se
  investigó el detalle de la duplicación en esta auditoría de solo lectura, queda para el paso de
  aplicación).
- `ServicioMora.java` (11) + `dto/FilaMora.java` (17): la función de **alerta de garante en mora**
  (RN-22, parte de RF24/E8: "el sistema alerta para contactar al garante cuando el préstamo tiene
  2+ cuotas vencidas") — `alertaGarante`/`idGarante`/`garanteNombre`/`garanteTelefono` en
  `FilaMora`, y la lógica que las llena en `ServicioMora`. **Esta es una sub-funcionalidad
  completa (RF24/RN-22) que desaparece entera**, no solo código muerto — a confirmar con Joel que
  efectivamente se retira (coherente con "no se presta a quien no garantice", si nunca hay
  garante, la alerta nunca tiene nada que mostrar, pero el código en sí es más que un resto
  trivial).
- `dto/ResultadoCapacidadPago.java` (5): campo `requiereGarante` (lo calcula
  `ServicioMotorFinanciero.calcularCapacidadPago`) — pasa a no tener sentido si nunca hay
  garante.
- `dto/ClausulaReporte.java`, `dto/AvisoBuroSolicitud.java`: solo comentarios que mencionan
  codeudor, sin campos propios — ajustar el comentario, no hay código que borrar.
- `excepciones/CodeudorNoAdmitidoException.java`: clase dedicada completa, se elimina.
- `infraestructura/GlosarioFinanciero.java` (8): `afectacionCompleta(..., garanteHabilitado)` —
  la cláusula "(y del codeudor, si tiene)" del ⓘ de "Afectación al ingreso"; se simplifica a un
  texto fijo sin esa cláusula condicional.
- `servicios/ServicioParametros.java` (5): `VALORES_EXIGIR_GARANTE` y la validación del parámetro
  al guardarlo desde Parámetros — se elimina junto con el parámetro.
- `servicios/ServicioInformes.java`/`ServicioReportes.java` (6): columnas/filas de codeudor en
  informes (ver JSP/jrxml abajo, son el mismo hallazgo visto desde el lado del servicio).
- `dao/SolicitudDAO.java` (13): `id_codeudor` en `COLUMNAS`/`COLUMNAS_S`, el `mapear`, y el
  `INSERT`/`UPDATE` de la solicitud.
- `dao/DocumentoExpedienteDAO.java`/`dao/TipoDocumentoDAO.java` (4+2): mapeo de `aplica_codeudor`
  (ya cubierto arriba, columna de esquema).
- `modelos/SolicitudCredito.java` (11): campos `idCodeudor`/`ingresoCodeudorEvaluado` + sus
  getters/setters.
- `modelos/Persona.java` (2): solo 2 comentarios ("cliente o codeudor") sin ningún campo propio de
  codeudor en el modelo — ajustar el texto, no hay código.
- `modelos/TipoDocumento.java` (5): campo `aplicaCodeudor` + getter/setter (ya cubierto).

**Controladores y rutas**:
- `ControladorSolicitudesServlet.java`: la rama `pathInfo.endsWith("/codeudor")` completa (ruta
  `POST /solicitudes/{id}/codeudor`) y los atributos que pasa a la vista (`codeudor`,
  `laboralCodeudor`, `requiereCodeudor`, `garanteHabilitado`).
- `ControladorAprobacionesServlet.java`: mismos atributos, para la ficha del gerente.
- `ControladorFormalizacionServlet.java` (9): resuelve el codeudor de la solicitud para pasarlo a
  `ServicioFormalizacion` al generar el contrato/pagaré.
- `ControladorReportesServlet.java` (18): método `garanteHabilitado(Connection)` — resuelve
  `credito.exigir_garante != "nunca"` para pasarlo como parámetro `GARANTE_HABILITADO` a los
  `.jrxml` de informes (incobrables, morosidad) y ocultar la columna "Codeudor".
- **No hay ningún `Permiso` dedicado a codeudor** — la ruta `/codeudor` cae bajo el permiso
  general de `/solicitudes` (`GESTIONAR_SOLICITUD`); eliminarla no toca `FiltroAutorizacion.java`
  ni la matriz de `ServicioAutorizacion`.

**JSP**: `solicitudes/ficha.jsp` (29 menciones — la tarjeta "Codeudor" completa: formulario para
asignar, aviso "necesita codeudor", cartel verde "codeudor asignado"); `aprobaciones/ficha.jsp`
(18 — misma tarjeta, vista del gerente); `formalizacion/ficha.jsp`/`nueva.jsp` (6+6 — datos del
codeudor para el contrato); `reportes/incobrables.jsp`/`reportes/morosidad.jsp` (2+2 — pasan
`garanteHabilitado` a la vista).

**PDF/`.jrxml`**: `contrato.jrxml` (parámetros `TIENE_CODEUDOR`/`CODEUDOR_*` + el párrafo "EL
CODEUDOR SOLIDARIO: ..."); `pagare.jrxml` (texto "pagaremos" vs. "pagaré", fila condicional del
codeudor); `plan_pagos.jrxml` (línea "Codeudor: ... C.I. ..." en el encabezado);
`informe_incobrables.jrxml`/`informe_morosidad.jrxml` (columna "Codeudor", ya con
`GARANTE_HABILITADO=false` ocultándola y corriendo las demás columnas — código ya preparado para
que la columna nunca se vea, confirmando que es seguro quitarla del todo).

**Motivos**: `ServicioSolicitudes.MOTIVO_CODEUDOR_NO_ADMITIDO` ("La política vigente no admite
codeudor.") — constante de texto, no una fila de catálogo (`motivos_rechazo_solicitud` no tiene
ningún motivo de codeudor).

**Tests** (4 archivos): `ServicioSolicitudesTest` (la mayoría de los casos de
`determinaSiRequiereCodeudor`/`validarCodeudorDistintoDeCliente`/`validarCodeudorSinDeuda`/
`motivoCodeudorNoAdmitidoSiAplica`/`sumarCuotasOtrasDeudas`), `ServicioAprobacionCreditoTest`,
`ServicioFormalizacionTest`, `ServicioMotorFinancieroTest`.

**Humo** (`pruebas/humo_http.sh`, 70 líneas): toda la sección de E4 que crea un codeudor de
prueba, lo asigna, prueba D-66 (codeudor con deuda propia), fuerza `credito.exigir_garante` a
"siempre" para poder ejercitar el flujo (revertido al final) — **incluye los 9 fallos
preexistentes** (la asignación de codeudor no persiste `id_codeudor`, bug ya documentado en
sesiones anteriores). **Eliminar todo esto de humo resuelve, por eliminación del código que
falla, los 9 fallos preexistentes** — no hace falta arreglar el bug de `asignarCodeudor`, el
método completo deja de existir.

**Cargador** (`CargadorDatosPrueba.java`, 4 líneas): crea una persona adicional y la asigna como
codeudor de un escenario de demostración — se quita junto con el resto.

**Documentación** (20 archivos con menciones): `docs/00_especificacion_maestra.md` (RN-04/RN-22/
RN-28/PO-02/"RF-08", sección de parámetros), `REGLAS_DESARROLLO.md §5` (pasa de "no debe mostrar
codeudor con esa política" a "no existe codeudor"), `AVANCE.md` (decisiones D9/D-125/D-66, todas
sobre codeudor — se marcan como superadas por la decisión de hoy, sin borrar el historial),
`docs/funcionamiento/RF04_*.md`/`RF05_solicitud.md`/`RF06_envio_aprobacion.md` (secciones
enteras sobre el flujo de codeudor), y los documentos de este mismo análisis (§7 de este archivo,
grupo A, que queda resuelto por esta decisión).

### Qué se rompería al quitarlo (para no perder nada al aplicar)

- Si se borran las columnas de esquema **antes** que el código que las lee, la aplicación no
  compila/falla en runtime — el esquema va **último**.
- Si se borra `ServicioSolicitudes.asignarCodeudor` sin quitar la ruta del controlador, la
  compilación falla (el controlador ya no encontraría el método) — controlador y servicio van
  juntos.
- Si se borra `credito.exigir_garante` sin antes simplificar `ServicioClientes` (RF02) a la rama
  fija, la precalificación se rompe (lee un parámetro que ya no existe).
- Si se borra la columna `aplica_codeudor` sin quitar su mapeo en los 2 DAO, el `SELECT` con
  columnas explícitas falla.
- `ResultadoCapacidadPago.requiereGarante`/`ServicioMora`-`FilaMora` (alerta de garante): si se
  quita el campo del DTO sin tocar quien lo construye (`ServicioMotorFinanciero.
  calcularCapacidadPago`, `ServicioMora`), la compilación falla.
- Los 9 fallos preexistentes del humo desaparecen solos al quitar esa sección — no hay que
  "arreglarlos" aparte.

### Propuesta de plan por pasos (sin aplicar nada)

Mismo criterio que la normalización de `personas`: cambio mínimo, de afuera hacia adentro, con
`mvn test` en verde en cada paso antes de seguir.

1. **Pantallas y reportes**: quitar la tarjeta "Codeudor" de `solicitudes/ficha.jsp`/
   `aprobaciones/ficha.jsp`, los datos de codeudor de `formalizacion/ficha.jsp`/`nueva.jsp`, la
   columna "Codeudor" (y `GARANTE_HABILITADO`) de `informe_incobrables.jrxml`/
   `informe_morosidad.jrxml`/sus JSP, y los parámetros/párrafos de codeudor de `contrato.jrxml`/
   `pagare.jrxml`/`plan_pagos.jrxml` (recompilar con `herramientas/compilar_reportes.sh`).
2. **Controladores**: quitar la ruta `/codeudor` de `ControladorSolicitudesServlet`, los
   atributos de codeudor en `ControladorSolicitudesServlet`/`ControladorAprobacionesServlet`/
   `ControladorFormalizacionServlet`, y `garanteHabilitado(Connection)` de
   `ControladorReportesServlet`.
3. **Servicios/modelos/DAO**: `ServicioSolicitudes` (todos los métodos y parámetros listados
   arriba), `ServicioAprobacionCredito`, `ServicioFormalizacion`, `ServicioClientes` (simplificar
   RF02 a la rama fija), `ServicioMotorFinanciero`/`ResultadoCapacidadPago.requiereGarante`,
   `ServicioMora`/`FilaMora` (alerta de garante, confirmar alcance con Joel antes), `GlosarioFinanciero.afectacionCompleta`, `ServicioParametros.VALORES_EXIGIR_GARANTE`,
   `excepciones/CodeudorNoAdmitidoException` (borrar la clase), `modelos/SolicitudCredito`
   (campos), `SolicitudDAO` (columnas), `TipoDocumentoDAO`/`DocumentoExpedienteDAO` (mapeo de
   `aplica_codeudor`).
4. **Tests**: actualizar/borrar los casos de codeudor en los 4 archivos de test — `mvn test` debe
   quedar en verde sin ningún caso de codeudor.
5. **Humo**: quitar las 70 líneas de `pruebas/humo_http.sh` — confirmar que el total de
   verificaciones baja y que ya no quedan los 9 fallos.
6. **Cargador**: quitar la creación del codeudor de prueba.
7. **Documentación**: actualizar `REGLAS_DESARROLLO.md §5`, `AVANCE.md` (nueva decisión, citando
   D9/D-125/D-66 como superadas), los RF de `docs/funcionamiento/` que describían el flujo de
   codeudor, y `docs/00_especificacion_maestra.md` (marcar RN-04/RN-22/RN-28/PO-02 como retiradas
   de esta versión, sin borrar el texto histórico).
8. **Esquema, al final**: migración para `solicitudes_credito` (quitar `id_codeudor`,
   `ingreso_codeudor_evaluado`, su `CHECK` y su FK) y `tipos_documento` (quitar `aplica_codeudor`),
   y quitar la fila `credito.exigir_garante` de `parametros_sistema` (y de `03_parametros.sql`
   para instalaciones nuevas) — recién cuando los 7 pasos anteriores ya estén aplicados y
   probados, siguiendo el mismo método ya usado en la normalización de `personas` (recrear
   `sgcpc_db_pruebas`, `mvn test`, humo, prueba en pantalla, commit + tag).

No se tocó ningún archivo de código ni de esquema en esta auditoría — solo lectura, como se
pidió.
