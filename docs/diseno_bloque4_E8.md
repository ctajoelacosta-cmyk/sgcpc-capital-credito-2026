# Diseño del Bloque 4 (E8) — Seguimiento de mora, alerta de garante, panel y reportes

Propuesta para el OK de Joel. No programado todavía. Fuentes: `docs/00_especificacion_maestra.md`
RF-37/RF-38/RF-43, PO-11, PO-15, §3.1 (permisos), §12 (pantallas), §13 (reportes), §5.3
(parámetros), §6.4 (mora), §10 (modelo de datos) — y el código ya construido en E1–E8/Bloque 2.

Orden de lectura sugerido: primero las 5 decisiones marcadas **[DECISIÓN]** (todas juntas en la
sección 0), porque varias de las secciones siguientes dependen de cuál elija Joel. Después el
resto es la propuesta completa, sección por sección, como pidió.

## 0. Decisiones que necesito antes de programar

**[DECISIÓN 1] — Cómo se determina que una promesa está "cumplida".**
`gestiones_cobranza.cumplida` existe en la base desde E0 pero **ningún código la escribe nunca**
(confirmado: no aparece en ningún INSERT/UPDATE fuera del mapeo de lectura). RF-37 pide destacar
"promesas vencidas" -- necesito una regla para marcarlas cumplidas, si no, TODAS quedan vencidas
para siempre en cuanto pasa la fecha, aunque el cliente haya pagado.
Propongo: cuando un cobro (normal, parcial, cancelación o anticipo) deja al préstamo **sin
ninguna cuota vencida**, se marcan `cumplida = TRUE` todas las gestiones de ese préstamo que
tenían `fecha_promesa` no nula y `cumplida = FALSE` -- el mismo momento en que `ServicioCobranza`
ya recalcula `estado_prestamo` (D-89/D-91). No hace falta comparar el monto prometido contra el
monto pagado: si ya no hay nada vencido, la promesa cumplió su propósito práctico. Sin este
cambio (chico, en `ServicioCobranza`, sin tocar esquema) la sección 1.4 no tiene forma de
distinguir una promesa vieja ya resuelta de una promesa realmente incumplida.

**[DECISIÓN 2] — Permiso para la pantalla de Reportes (P11), separado del Panel (P02).**
Doc. 00 tiene una tensión: §3.1 dice "Ver panel y reportes: Gerente X, Oficial Parcial (su
trabajo), Cajero Parcial (caja)", pero §12 dice "P11 Reportes: Gerente" a secas (sin oficial ni
cajero). El permiso `VER_PANEL_REPORTES` ya existe en el código desde E2 y hoy da `CONSULTA` a
los 3 perfiles -- si lo reuso tal cual para `/reportes`, oficial y cajero podrían entrar (GET
exige distinto de NINGUNO). Interpreto que el "parcial" de §3.1 se refiere al **Panel** (cada uno
ve sus propios indicadores, sección 3) y que §12 es la regla específica y más reciente para la
pantalla de Reportes en sí: **Reportes queda exclusivo del gerente**. Propongo agregar un permiso
nuevo `VER_REPORTES` (`TOTAL` solo gerente, `NINGUNO` oficial/cajero) para `/reportes`, dejando
`VER_PANEL_REPORTES` tal cual está para `/panel` -- mismo patrón que D-60 (`GESTIONAR_SOLICITUD`
vs. `APROBAR_RECHAZAR_SOLICITUD`, dos permisos para dos pantallas relacionadas).

**[DECISIÓN 3] — Formato de cada reporte: pantalla vs. PDF vs. CSV.**
Ninguno de los 12 renglones de §13 está en la lista de "Documentos imprimibles" de §11 (que son
justo los 5 que ya construimos en el Bloque 2 más contrato/pagaré/plan). Propongo que los
reportes de este bloque salgan como **tabla en pantalla** (mismo estilo AdminLTE que
`clientes/lista.jsp`), con **CSV** para los 2 que van a salir de la casa (IVA del mes, para el
contador; Cumplimiento, para BCP/SEPRELAD) y nada de PDF/Jasper nuevo -- evita construir 10
`.jrxml` más en este bloque, cuando lo que hace falta operativamente es la lista y los números,
no un documento formal. El detalle sección por sección (abajo, sección 5) ya sigue este criterio;
decime si alguno lo querés como PDF igual (ej. "Estado de cuenta del cliente" para entregárselo a
un cliente en mano) y lo ajusto antes de programar.

**[DECISIÓN 4] — Quién entra a `/mora` (P14).**
`REGISTRAR_GESTION_COBRANZA` ya existe y hoy da `TOTAL` a los 3 perfiles (para que
`confirmarCobro` pueda insertar la gestión automática de un pago parcial sin chocar con ningún
permiso). Pero §12 dice "P14 Seguimiento de mora: Oficial, gerente" -- la cajera no debería poder
entrar a esta pantalla ni registrar una gestión manual (llamada/mensaje/visita), solo la
automática que ya se genera sola al cobrar. La gestión automática de `confirmarCobro` **no pasa
por `FiltroAutorizacion`** (es una llamada interna al DAO dentro de una acción ya autorizada por
`COBRAR`), así que cambiar el nivel de la cajera en este permiso a `NINGUNO` no rompe nada de lo
ya construido. Propongo: mapear `/mora` a `REGISTRAR_GESTION_COBRANZA` y bajar
`cajero → NINGUNO` en la matriz (gerente y oficial quedan en `TOTAL`, sin cambios). Es un cambio
de una línea en `ServicioAutorizacion`, no de esquema.

**[DECISIÓN 5] — Recalcular `estado_prestamo` de TODOS los préstamos activos, no solo el que se mira.**
Hoy `estado_prestamo` pasa a `EN_MORA` recién cuando alguien abre la ficha de `/cobranza/{id}` de
ESE préstamo en particular (`recalcularEstadoPrestamo` es por-préstamo, D-89). Fuera de eso, la
columna puede quedar desactualizada. PO-15 paso 1 dice literalmente *"el sistema actualiza
estados del día (EN_MORA)"* al iniciar el día -- así que esto ya está previsto en el diseño
original, solo faltaba dónde engancharlo. Propongo: al cargar `/panel` o `/mora` (lo que se abra
primero en el día), recalcular en lote todos los préstamos `ACTIVO`/`EN_MORA` (mismo cálculo que
ya existe, `calcularEstadoSegunCuotas`, en un loop) -- con la cantidad de préstamos de esta casa
de crédito (decenas, no miles) esto es liviano y no necesita un job aparte ni un scheduler nuevo.
Sin esto, "Vencidos" del panel y la lista de `/mora` podrían mostrar un préstamo como `ACTIVO`
aunque ya tenga una cuota vencida de ayer que nadie miró todavía.

No hacen falta cambios de esquema para nada de este bloque (ver sección 6): todo lo que falta es
código -- las 5 decisiones de arriba y las tablas de datos que ya existen.

## 1. Seguimiento de mora (RF-37, P14)

### 1.1. Pantalla y quién la usa

Ruta `/mora`, protegida por `REGISTRAR_GESTION_COBRANZA` (decisión 4: oficial y gerente, `TOTAL`
los dos -- el oficial puede registrar gestiones sin pedirle permiso al gerente, igual que hoy
puede armar una solicitud solo). En el menú: "Oficial: ... · Seguimiento de mora" y "Gerente:
todo" (ya en §12); la cajera no la ve.

### 1.2. Listado

Recalculo en lote de estados primero (decisión 5), después:

```sql
SELECT p.id_prestamo, p.identificador_operacion, per.nombres, per.apellidos, per.telefono_whatsapp,
       MIN(c.fecha_vencimiento) AS vencimiento_mas_viejo,
       CURRENT_DATE - MIN(c.fecha_vencimiento) AS dias_atraso,
       COUNT(c.id_cuota) FILTER (WHERE c.fecha_vencimiento < CURRENT_DATE AND c.estado_pago <> 'PAGADA') AS cuotas_vencidas
FROM prestamos p
JOIN solicitudes_credito s ON s.id_solicitud = p.id_solicitud
JOIN personas per ON per.id_persona = s.id_cliente
JOIN planes_pago pp ON pp.id_prestamo = p.id_prestamo AND pp.vigente = true
JOIN cuotas c ON c.id_plan_pago = pp.id_plan_pago
WHERE p.estado_prestamo = 'EN_MORA'
GROUP BY p.id_prestamo, p.identificador_operacion, per.nombres, per.apellidos, per.telefono_whatsapp
ORDER BY <promesa vencida primero, después dias_atraso DESC>
```

Columnas: Operación · Cliente · Teléfono · Días de atraso (del vencimiento más viejo sin pagar) ·
Cuotas vencidas · Monto exigible hoy (reusa `ServicioCobranza.calcularDeudaExigible`, ya
construido en E7 -- nunca se recalcula en SQL ni en la JSP, REGLAS_DESARROLLO.md §5) · Alerta de garante
(sección 2) · Última gestión (tipo + fecha, si hay) · Promesa (fecha + monto, si hay una vigente
sin cumplir) · Acción ("Registrar gestión").

**Orden**: primero las filas con una promesa **vencida** (`fecha_promesa < hoy` y
`cumplida = false`, RF-37 "vuelven a aparecer al inicio de la lista"), dentro de ese grupo por
`dias_atraso` descendente; después el resto, también por `dias_atraso` descendente.

### 1.3. Registrar una gestión

Formulario en la misma fila o en un modal: a quién se contactó (cliente o codeudor/garante, si
hay uno -- `id_persona_contactada`), tipo (`LLAMADA`, `MENSAJE`, `VISITA_LOCAL` -- `PAGO_PARCIAL`
NO se ofrece acá, ya se genera solo al cobrar, D-95), resultado (texto libre corto), excusa
(texto libre, opcional), fecha de promesa (opcional, si el cliente prometió pagar) y monto
prometido (obligatorio si hay fecha de promesa). Guarda con `registrado_por` = usuario de sesión,
`fecha_hora = now()`, `cumplida = false`.

### 1.4. Promesas vencidas (decisión 1)

Una promesa está **vencida** cuando `fecha_promesa < CURRENT_DATE` y `cumplida = FALSE`. Se
marcan `cumplida = TRUE` automáticamente (decisión 1) la próxima vez que ese préstamo cobra algo
y queda sin cuotas vencidas -- en `ServicioCobranza`, en el mismo punto donde ya se recalcula
`estado_prestamo` tras un cobro/cancelación/anticipo.

### 1.5. Historial por préstamo

Dentro de la ficha de `/cobranza/{id}` (que ya existe, D-97) o en una sub-vista de `/mora/{id}`:
lista de `gestiones_cobranza` de ese préstamo, más recientes primero, con quién la registró.

## 2. Alerta de garante (RF-38)

*Superado por el retiro de codeudor/garante (18/10/2026, commit `6e7da13`) — ver
`docs/funcionamiento/RF24_seguimiento_mora.md` §5/§7. Esta sección queda como diseño histórico,
sin reescribir.*

**Qué dispara**: `cuotas_vencidas >= credito.cuotas_alerta_garante` (parámetro ya existe, valor
2) **y** la solicitud tiene codeudor (`solicitudes_credito.aplica_codeudor = true` /
`id_codeudor` no nulo). Es un dato **derivado en consulta** (igual que dice Doc. 00 §10.2 para
`prestamos.requiere_contactar_garante`), nunca una columna guardada -- se recalcula solo con
mirar `cuotas_vencidas` y `id_codeudor`, así que nunca puede quedar desactualizado.

**Dónde aparece**:
- En el listado de `/mora` (sección 1.2): badge rojo "Contactar garante" + teléfono del codeudor
  en la misma fila, para no tener que entrar a la ficha para verlo.
- En la ficha del préstamo en mora: nombre completo y teléfono del codeudor, con el mismo botón
  "Registrar gestión" pero pre-seleccionando `id_persona_contactada` = el codeudor.

**Sin codeudor**: si `aplica_codeudor = false` (el cliente presentó comprobante de ingreso y no
necesitó garante, RN-04), no se muestra ninguna alerta ni badge -- nunca un error ni un campo
vacío confuso.

## 3. Panel (RF-43, P02)

Reescribe `ControladorPanelServlet`/`panel.jsp` (hoy son un `"próximamente"`, E2). Recalculo en
lote primero (decisión 5). Indicadores por perfil, todos ya calculables con servicios que existen:

| Indicador | Quién lo ve | De dónde sale |
|---|---|---|
| Cobros de hoy (cantidad y monto) | Los 3 | `pagos` con `estado_pago='CONFIRMADO'` y `fecha_hora_pago::date = hoy` |
| Cuotas vencidas / préstamos en mora | Los 3 | `prestamos.estado_prestamo='EN_MORA'` (ya recalculado) + `cuotas` vencidas sin pagar |
| Promesas de hoy | Oficial, gerente | `gestiones_cobranza` con `fecha_promesa = hoy` y `cumplida=false` |
| Desembolsos pendientes (cantidad) y total a proveer | Cajera, gerente | `PrestamoDAO.listarPendientesDesembolso` + `ServicioCaja.calcularFondoSugerido` (ya existen, D-74/RF-36) |
| Efectivo en caja (saldo teórico) | Cajera, gerente | `ServicioCaja.calcularSaldoTeorico` de la sesión ABIERTA (si no hay caja abierta: "Sin caja abierta", no un error) |
| Cartera en la calle (capital pendiente) | Gerente | `SUM(capital_programado - capital_pagado)` de cuotas de planes vigentes de préstamos `ACTIVO`/`EN_MORA` |
| Cobrado esta semana / este mes | Gerente | `SUM(monto_aplicado)` de `pagos` confirmados, agrupado por `fecha_hora_pago >= inicio_semana` / `inicio_mes` |
| Solicitudes pendientes de aprobar | Gerente | `SolicitudDAO.listarPendientesAprobacion` (ya existe) -- enlace directo a `/aprobaciones` |
| Reversiones por autorizar | Gerente | **No aplica todavía** -- E9 (reversiones) no está construido; el panel deja el lugar reservado y muestra "—" hasta ese bloque, no un 0 engañoso |
| Mi trabajo de hoy (solicitudes en curso) | Oficial | `SolicitudDAO.listarPorOficial` (ya existe), filtrado a las suyas no decididas |

Cada tarjeta con un enlace directo a la pantalla correspondiente (Cobros de hoy → `/cobranza`,
Desembolsos pendientes → `/desembolso`, etc.), mismo criterio que "botones con la acción
concreta" de §12.

## 4. Reportes (§13, P11) -- propuesta completa

Todos con **filtro de fecha** donde corresponda (por defecto: hoy, la semana o el mes según el
reporte, editable) y **totales al pie** de cada columna numérica. "Tablas" indica de dónde sale
cada número -- ninguno se calcula en la JSP/SQL del reporte si ya existe un método de servicio
(REGLAS_DESARROLLO.md §5): cuando la columna es financiera y ya hay un servicio que la calcula, el reporte
lo reusa tal cual.

### 4.1. Vencimientos -- **Debe**

- **Quién lo ve**: oficial, gerente (mismo criterio que `/mora`).
- **Filtros**: rango de fechas (por defecto: hoy a +7 días).
- **Columnas**: Operación · Cliente · Teléfono · N.º de cuota · Fecha de vencimiento · Monto de
  la cuota (con IVA) · Estado (pendiente / parcial).
- **Totales**: cantidad de cuotas, monto total a vencer en el rango.
- **Tablas**: `cuotas` (join `planes_pago` vigente, `prestamos` `ACTIVO`/`EN_MORA`), `personas`.
- **Formato**: pantalla.

### 4.2. Morosidad -- **Debe**

- **Quién lo ve**: oficial, gerente.
- **Filtros**: ninguno (foto del momento) o "a una fecha" para reconstruir un cierre de mes
  anterior.
- **Columnas**: Operación · Cliente · Teléfono · Codeudor + teléfono (si hay) · Días de atraso ·
  Tramo (1–30 / 31–60 / 61–90 / 91–180 / más de 180, Doc. 00 §13) · Cuotas vencidas · Monto
  exigible (capital + interés + moratorio + punitorio + IVA, vía
  `ServicioCobranza.calcularDeudaExigible`) · Contactar garante (sección 2).
- **Totales**: cantidad de préstamos y monto exigible por tramo, y total general.
- **Tablas**: igual que 1.2 (mismo query base que `/mora`, sin el orden por promesa -- acá
  agrupado por tramo).
- **Formato**: pantalla.

### 4.3. Gestiones de cobranza -- **Debería**

- **Quién lo ve**: oficial, gerente.
- **Filtros**: rango de fechas, tipo de gestión, solo incumplidas.
- **Columnas**: Fecha/hora · Préstamo · Contactado (cliente/codeudor) · Tipo · Resultado · Excusa
  · Promesa (fecha + monto) · Cumplida · Registrado por.
- **Totales**: cantidad de gestiones por tipo, cantidad de promesas incumplidas en el rango.
- **Tablas**: `gestiones_cobranza`, `personas`, `usuarios`.
- **Formato**: pantalla.

### 4.4. Cartera -- **Debería**

- **Quién lo ve**: gerente.
- **Filtros**: ninguno (foto del momento).
- **Columnas**: Operación · Cliente · Monto aprobado · Capital pendiente · Interés por cobrar
  (suma de `interes_programado` de cuotas no pagadas) · Estado.
- **Totales**: cantidad de préstamos activos, capital en la calle, interés por cobrar total.
- **Tablas**: `prestamos` (`ACTIVO`/`EN_MORA`), `cuotas` de su plan vigente.
- **Formato**: pantalla.

### 4.5. Resumen mensual -- **Debe**

- **Quién lo ve**: gerente.
- **Filtros**: mes (por defecto el actual).
- **Columnas/bloques**:
  - Préstamos otorgados en el mes: cantidad y monto (`desembolsos` confirmados del mes).
  - Cobrado en el mes, con el desglose de la composición del arqueo (mismo criterio que
    `ServicioCaja.generarPdfArqueo`, D-94): capital, **interés compensatorio** (pedido explícito
    de Joel: "intereses cobrados"), moratorio, punitorio, IVA. Nuevo método
    `AplicacionPagoDAO.sumarPorRangoFechas(desde, hasta)`, mismo array de 5 posiciones que ya
    devuelve `sumarPorSesion` pero agregando TODAS las sesiones del mes por
    `pagos.fecha_hora_pago`, no por `id_sesion_caja` -- no se reinventa el cálculo, se generaliza
    el que ya existe.
  - Ganancia del mes: interés compensatorio + moratorio + punitorio, **sin IVA** (según Doc. 00
    §13, la ganancia no incluye el IVA que se retiene para el fisco).
  - Morosidad %: préstamos `EN_MORA` / préstamos `ACTIVO` + `EN_MORA`, y capital vencido / cartera
    total (las dos, una es más simple de leer y la otra pesa por monto).
- **Totales**: ya son totales en sí mismo (es un resumen).
- **Tablas**: `desembolsos`, `pagos` + `aplicaciones_pago` (vía el nuevo método), `prestamos`,
  `cuotas`.
- **Formato**: pantalla + botón "Exportar CSV" (decisión 3).

### 4.6. Caja -- **Debe**

- **Quién lo ve**: gerente.
- **Filtros**: rango de fechas.
- **Columnas**: Sesión · Cajera · Apertura · Cierre · Saldo inicial · Saldo teórico · Saldo
  físico · Diferencia · Motivo (si hay) · Revisada por gerente (sí/no) · Enlace al arqueo en PDF
  (ya construido, D-94 -- reusa `/caja/{idSesion}/arqueo`, no se genera nada nuevo).
- **Totales**: cantidad de sesiones, suma de diferencias (para ver de un vistazo si las
  diferencias tienden a favor o en contra).
- **Tablas**: `sesiones_caja`.
- **Formato**: pantalla, con el PDF de cada arqueo ya resuelto por el enlace.

### 4.7. IVA del mes -- **Debe**

- **Quién lo ve**: gerente (para pasarle al contador).
- **Filtros**: mes.
- **Columnas**: N.º de factura · Fecha · Cliente · RUC/C.I. · Exenta · Gravada 10 % · IVA 10 % ·
  Total.
- **Totales**: suma de exenta, gravada, IVA y total del mes.
- **Tablas**: `facturas` (`estado='EMITIDA'`, `fecha` dentro del mes) -- reusa exactamente las
  columnas que ya calcula/guarda `confirmarCobro`/`confirmarCancelacionTotal`, nunca recalcula
  IVA en el reporte.
- **Formato**: pantalla + CSV (decisión 3, para mandarle el archivo al contador).

### 4.8. Incobrables -- **Podría**

- **Quién lo ve**: gerente.
- **Filtros**: ninguno (foto del momento).
- **Columnas**: Operación · Cliente · Teléfono · Codeudor + teléfono · Días de atraso · Monto
  exigible.
- **Totales**: cantidad y monto total.
- **Tablas**: mismo query que Morosidad, filtrado a `dias_atraso > credito.dias_incobrable`
  (parámetro ya existe, valor 180).
- **Formato**: pantalla.

### 4.9. Estado de cuenta del cliente -- **Debería**

- **Quién lo ve**: oficial, gerente (a pedido, ej. si el cliente llama a preguntar cuánto debe).
- **Filtros**: buscar por préstamo u operación (reusa la búsqueda de `/cobranza`).
- **Columnas**: datos del préstamo (monto, plazo, tasa) · historial de pagos (fecha, tipo, monto,
  recibo -- reusa `ServicioCobranza.listarHistorialCobros`, D-97) · cuotas pendientes · gestiones
  de cobranza del préstamo.
- **Totales**: saldo de capital pendiente, total pagado a la fecha.
- **Tablas**: las mismas que ya arma la ficha de `/cobranza/{id}` (D-97) más `gestiones_cobranza`
  -- básicamente es esa misma ficha con la sección de gestiones agregada, accesible también para
  el oficial (que hoy no puede ver `/cobranza`, solo la cajera).
- **Formato**: pantalla (imprimible con el navegador si hace falta entregarlo; no Jasper).

### 4.10. Auditoría -- **Debería**

- **Quién lo ve**: gerente (ya existe el permiso `CONSULTAR_AUDITORIA`, sin usar todavía en
  ninguna pantalla).
- **Filtros**: rango de fechas, usuario, tipo de entidad, operación (texto).
- **Columnas**: Fecha/hora · Usuario · Operación · Entidad · ID de entidad · Detalle.
- **Totales**: cantidad de registros en el rango.
- **Tablas**: `auditoria_operaciones` (ya se llena desde E2, nunca tuvo una pantalla para leerla).
- **Formato**: pantalla.

### 4.11. Cumplimiento (CSV) -- **Podría**

- **Quién lo ve**: gerente.
- **Filtros**: rango de fechas.
- Doc. 00 no especifica las columnas exactas que pide BCP/SEPRELAD (es un requisito regulatorio
  real, no algo que yo deba inventar). Para no armar un CSV que parezca oficial sin serlo,
  propongo dejarlo con **prioridad Podría** y, cuando Joel tenga el formato exacto que pide el
  regulador (columnas, nombres, codificación), lo armamos como un caso aparte -- mientras tanto,
  no lo programo en este bloque salvo que Joel ya tenga esa planilla.
- **Formato**: CSV.

## 5. Cambios de esquema

**Ninguno.** Las 5 decisiones de la sección 0 son de código (matriz de permisos, un `Permiso`
nuevo, un `if` en `ServicioCobranza`, un método nuevo en `AplicacionPagoDAO` que generaliza uno
que ya existe) y dos rutas/pantallas nuevas (`/mora`, `/reportes`) más la reescritura de
`/panel`. Todas las columnas que necesito (`gestiones_cobranza.cumplida`, `telefono_whatsapp`,
`id_codeudor`, `aplica_codeudor`, `estado_prestamo`, toda la composición de `aplicaciones_pago`)
ya existen desde E0/E1 o desde los bloques anteriores.

## 6. Qué va a probar Joel en el navegador (con datos reales)

Usando préstamos que ya están en la base y que Joel ya aprobó en bloques anteriores, para poder
verificar los números contra lo ya visto:

1. **`/mora`**: debería aparecer **CC-2026-90001** si todavía tiene alguna cuota vencida sin
   pagar en este momento (si ya está `FINALIZADO` por la cancelación de la sesión 16, no
   aparece -- es un buen caso para confirmar que un préstamo finalizado NO sale en la lista de
   mora). Si hace falta un préstamo con mora real para probar el listado completo, mejor usar uno
   de los que el humo deja en la base con cuotas vencidas de verdad (con cédula "9...", D-75) en
   vez de tocar un préstamo de Joel.
2. **Registrar una gestión manual** contra ese préstamo: tipo LLAMADA, resultado "No contesta",
   sin promesa -- confirmar que aparece en el historial y que NO genera ninguna promesa vencida.
3. **Alerta de garante**: buscar (o crear con humo) un préstamo con codeudor y 2+ cuotas vencidas
   -- confirmar el badge y el teléfono. Y uno SIN codeudor con 2+ vencidas -- confirmar que no
   sale ningún badge ni error.
4. **`/panel`** como cajera: confirmar que ve cobros de hoy, desembolsos pendientes y efectivo en
   caja (0 si no hay caja abierta en ese momento -- Joel puede abrir/cerrar una caja de prueba
   con cédula "9..." si quiere ver el número real).
5. **`/panel`** como gerente: cartera en la calle y cobrado del mes -- verificar a mano contra un
   par de `SELECT` de referencia que le paso en el momento.
6. **`/reportes` → IVA del mes**: para el mes de septiembre 2026, debería aparecer la factura
   **001-001-0000003** (CC-2026-90001, D-93/D-99) con sus montos exactos (gravada 193.779, IVA
   17.617) -- mismos números que ya vio en el PDF de la factura.
7. **`/reportes` → Caja**: debería aparecer la **sesión 16**, con el enlace a su arqueo en PDF
   (el mismo que ya aprobó en D-100) sin volver a generarlo.
8. **`/reportes` → Resumen mensual**: el "interés compensatorio cobrado" de septiembre debería
   coincidir con la suma de `COMPOSICION_INTERES` que ya vio en los arqueos de las sesiones de
   ese mes (puede verificarlo sumando a mano los arqueos que ya tiene, o le paso el `SELECT`).
9. **Permisos**: oficial sin acceso a `/reportes` (403); cajera sin acceso a `/mora` ni
   `/reportes` (403); oficial y gerente sí entran a `/mora`.

Avisame con tu OK (y las respuestas a las 5 decisiones de la sección 0) y arranco a programar.
