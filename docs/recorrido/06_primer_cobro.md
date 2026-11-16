# Escena 06 — Primer cobro

Qué cubre: la lista de trabajo de cobranza, el cálculo de la deuda exigible, el cobro en
efectivo (con o sin vuelto), la cancelación total anticipada y la emisión de recibo, factura
con IVA y constancia de cancelación. **Recorrido completo del cobro** (23-25/10/2026, tramo 5,
decisión de Joel de no cambiar ninguna fecha): recorridos en pantalla la lista de trabajo, la
ficha de cobranza sin nada vencido, el adelanto a capital completo (con y sin interés
devengado), el recibo, el efecto en caja, la reversión de un cobro (ciclo completo, con un
rechazo) y la cancelación total con sus tres comprobantes — ver "Recorrido completo del cobro y
cierre" y "Completitud del tramo 5" al final de este documento. Lo que requiere una fecha
futura (cobro de cuota vencida, mora, pago parcial, vencimiento anticipado) queda **"no
recorrido: requiere fecha futura, descartado por decisión de Joel"**.

## Plan del tramo 5: cobro de la cuota 1 antes del vencimiento (decisión 23/10/2026)

**Decisión de Joel (23/10/2026): no se cambia ninguna fecha (ni Windows, ni PostgreSQL, ni una
fecha simulada) y no se hace ningún cambio en el sistema.** Se sigue el ciclo normal de
cobranza, como lo usaría la cajera hoy mismo (23/10/2026), para cobrar la **cuota 1 de
`CC-2026-00002`** (vence el 23/11/2026 — todavía no vencida). Informe de solo lectura (código,
`docs/` y `SELECT` en `sgcpc_db_pruebas`), antes de hacerlo en pantalla.

### 1) ¿El sistema permite cobrar una cuota no vencida?

**Sí, pero no por el camino de "Cobrar" — por "Adelantar a capital".** Dos reglas en juego:

- **Clausula SEXTA del contrato ("Pago anticipado")**, armada en
  `ServicioFormalizacion.construirClausulasContrato`: *"EL DEUDOR puede cancelar total o
  parcialmente el préstamo en cualquier momento, sin penalidad y sin pagar intereses de
  períodos futuros (Ley 6624/2020). (...) El pago anticipado parcial se aplica al capital,
  mantiene el monto de la cuota y reduce la cantidad de cuotas."* — la base legal de que SÍ se
  puede pagar antes.
- **"Cobrar" (RF22) la rechaza explícitamente.** `ServicioCobranza.confirmarCobro`: si no hay
  nada vencido ni una cuota que venza justo hoy, corta con el mensaje exacto (línea 680-682):
  *"Este préstamo no tiene cuotas vencidas por cobrar. Si el cliente quiere pagar antes del
  vencimiento, use 'Adelantar capital'."* Documentado también en
  `docs/funcionamiento/RF22_cobro_efectivo.md` §2 y `RF27_adelantar_capital.md` §5: *"'Adelantar
  capital' es la vía real para pagar una cuota que todavía no venció (RF22 la rechaza)."*
  El método que decide si hay algo cobrable por "Cobrar" es
  `ServicioCobranza.calcularMontoCobrableNormal` — suma lo vencido más, **si la hay, la cuota
  que vence exactamente hoy** (nunca una cuota futura). Para `CC-2026-00002` hoy
  (23/10/2026), con la cuota 1 venciendo el 23/11/2026, este monto es **0 Gs.**: no hay nada
  que "Cobrar" pueda tocar.

**Conclusión**: no existe, en este sistema, una acción "pagar la cuota N por anticipado, tal
cual, sin tocar el resto del plan". La única vía es **"Adelantar capital"** — que no es lo
mismo: no paga "la cuota 1" como una unidad cerrada, sino que aplica el monto a reducir el
capital pendiente total y **regenera todo el plan** desde ese nuevo saldo (clausula SEXTA: "...
reduce la cantidad de cuotas").

### 2) ¿Qué monto propone la pantalla, y por qué?

En la ficha de cobranza (`cobranza/ficha.jsp`), el único botón de pago de esta tarjeta cambia
de nombre según el estado: se llama **"Pagar cuota"** si hay algo vencido/vence hoy, o
**"Adelantar a capital"** si no hay nada vencido (`hayAlgoParaCobrar` es `false` para
`CC-2026-00002` hoy) — es el **mismo botón**, no dos botones distintos. El monto que propone
sale de `ServicioCobranza.calcularPreviewAnticipoParcial` →
`ServicioMotorFinanciero.calcularAnticipoParcial`, con esta fórmula para el interés:

```
interés devengado = capitalPendienteTotal × tasaMensual × (díasTranscurridos ÷ díasDelPeríodo)
```

**Dato específico de `CC-2026-00002` (verificado con `SELECT`)**: el préstamo se desembolsó
**hoy mismo, 23/10/2026** (`fecha_desembolso`), y el período de la cuota 1 va del 23/10/2026 al
23/11/2026 (31 días). Si la cajera abre esta ficha **hoy**, `díasTranscurridos = 0` — así que
**el interés devengado sería exactamente 0 Gs.** (no hay ningún día transcurrido todavía del
período). Esto significa que, a diferencia del ejemplo ya documentado en `RF27_adelantar_
capital.md` (Mirtha, anticipo 1 día después del desembolso, interés 2.286 Gs.), acá **no
descuenta nada de interés por adelantarse — el monto completo que la cajera reciba iría
íntegro a capital**, porque hoy es literalmente el día 0 del préstamo. **No es un error del
sistema ni de este informe: es consecuencia directa de que, en esta prueba, "hoy" coincide con
la fecha de desembolso.**

La pantalla **no propone cobrar el monto exacto de "la cuota 1" (193.280 Gs.)** como una cifra
fija — "Adelantar a capital" deja que la cajera escriba cualquier monto de efectivo recibido
(o elija cuántas cuotas adelantar, P-7), y recalcula todo a partir de eso. Si quisiera
simular "pagar lo que sería la cuota 1", tendría que escribir ese monto a mano; el sistema no
lo sugiere como tal.

### 3) ¿Cobro normal o anticipo de capital? ¿Qué cambia?

**Anticipo de capital** (`TipoPago.ANTICIPO`), nunca un cobro normal — porque, como ya se vio
en el punto 1, "Cobrar" no tiene nada que hacer hoy con este préstamo.

**Qué cambia, verificado en el código** (`ServicioCobranza.confirmarAnticipoCapital` →
`ServicioMotorFinanciero.calcularAnticipoParcial`/`regenerarPlanAnticipo`):

- **En la cuota/plan**: **no se borra nada** — el plan vigente actual pasa a `no vigente` (RN-13,
  queda de historial) y se inserta una **versión nueva** del plan, con un nuevo saldo de
  capital (el capital pendiente menos lo aplicado) y **menos cuotas** (la cuota constante se
  mantiene, el número de cuotas restantes se recalcula) — exactamente lo que dice la cláusula
  SEXTA.
- **En el préstamo**: sigue `ACTIVO` (un anticipo parcial no lo finaliza, a menos que el monto
  cubriera todo el saldo — en ese caso el propio motor lo rechaza con *"El monto cubre todo el
  saldo del préstamo. Use Cancelación total."*, D-90).
- **En la caja (sesión N.º 3, abierta hoy)**: movimiento `COBRO` por el monto recibido en
  efectivo (redondeado a favor del cliente, `credito.redondeo_efectivo` = 50 Gs.) y, si
  corresponde, un movimiento `VUELTO` — mismo mecanismo que cualquier cobro, ya documentado en
  `05_desembolso.md`/`07_cierre_del_dia.md` para los movimientos de caja. **Mismo control de
  caja de hoy** que desembolso/cierre (`validarCajaAbiertaHoy`, el mismo helper): si la sesión
  no fuera de hoy, rechazaría con el mismo mensaje ya visto (`mensajeCajaDeOtroDia`) — hoy no
  aplica, la sesión 3 ya es de hoy.
- **Auditoría**: ninguna propia para el pago en sí (`RF27_adelantar_capital.md` §3) — a
  diferencia de desembolso/caja, el rastro queda solo en `pagos`/`aplicaciones_pago`/
  `liquidaciones_anticipadas` (inmutables por los triggers `BEFORE DELETE`), no en
  `auditoria_operaciones`.

### 4) ¿Qué comprobante emite?

- **Recibo**: siempre (`NumeradorDAO`, clave `RECIBO`) — verificado con `SELECT`:
  **todavía no existe ningún recibo en esta base de pruebas** (`numeradores` no tiene fila
  `RECIBO`), así que el primero que se emita sería **`REC-2026-000001`**.
- **Factura**: **solo si algo de lo cobrado es gravado** (interés/moratorio/punitorio > 0,
  RF22 §2 punto 5). Con el interés devengado en **0 Gs.** (punto 2, "hoy" = día 0 del
  préstamo), **un anticipo hecho hoy mismo no generaría ninguna factura** — sería 100 %
  capital, sin nada gravado. Tampoco existe todavía ninguna factura en esta base
  (`numeradores` sin fila `FACTURA`); si llegara a emitirse una, sería
  **`001-001-0000001`** (con los datos de timbrado de `fiscal.timbrado_numero`/
  `establecimiento`/`punto_expedicion`).

### 5) Pantallas y botones, en el orden que la cajera va a ver

1. Menú **"Cobranza y mora"** → lista de trabajo (`/cobranza`, RF20) → buscar `CC-2026-00002`
   (o el cliente) → **"Ver"**/entrar a la ficha.
2. **Ficha de cobranza** (`/cobranza/{id}`): deuda exigible del día (RF21) — hoy, **0 Gs.**
   exigibles (nada vencido, nada vence hoy); el único botón de pago dice **"Adelantar a
   capital"** (no "Pagar cuota", por lo del punto 2); al lado, **"Cancelar todo"** (vista
   previa de cancelación total, siempre disponible). Siempre visible: *"Si adelanta, su
   próxima cuota será el [fecha] por [monto], quedan N cuotas."* Detrás de **"Ver detalle del
   adelanto"** (colapsado): interés devengado + IVA (hoy, 0 + 0), capital aplicado, saldo que
   queda.
3. La cajera escribe el **efectivo recibido** (campo de texto) — el sistema recalcula en vivo
   lo mismo de arriba y, aparte, "en efectivo" ya redondeado a múltiplos de 50 Gs.
4. Toca **"Adelantar a capital"** → cartel de confirmación (SweetAlert2 armado a mano,
   `cobranza-ficha.js`): título **"Confirmar: Adelantar a capital"**, con el detalle completo
   (interés devengado, capital aplicado, saldo que queda, próxima cuota, cuotas que quedan) y
   el botón **"Sí, adelantar a capital"** / cancelar.
5. Al confirmar: mensaje de éxito **"Anticipo de capital aplicado ([monto])"**, con el vuelto
   si corresponde (**"Sin vuelto (0 Gs.)"** si el efectivo recibido coincide exacto); recibo
   siempre, factura solo si hay algo gravado (hoy, no habría — punto 4).
6. Vuelve a la ficha del préstamo, ahora con el plan nuevo (menos cuotas, mismo monto de cuota
   "similar") y el estado de cuenta actualizado.

### Lo que queda "no recorrido" por la decisión del 23/10/2026

Los casos que **requieren una fecha futura o pasada** para observarse en pantalla —
**cobro con atraso (para ver la mora)**, pago parcial con promesa vencida, vencimiento
anticipado por mora acumulada, y cualquier otro escenario del tramo 7 que dependa de que haya
pasado tiempo real desde el 23/10/2026 — quedan **"no recorrido: requiere cambio de fecha,
descartado por decisión de Joel (23/10/2026)"**. Solo se recorre, por ahora, lo que se puede
observar **hoy, con la fecha real**: deuda exigible en 0 (nada vencido todavía), y el anticipo
de capital de la cuota 1 antes de su vencimiento.

## Paso 1 — Lista de trabajo de cobranza: buscar el préstamo (RF20)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

- **Usuario/rol**: Cajera.
- **Pantalla y ruta**: menú **"Cobranza y mora"** → **"Cobranza"** → `GET /cobranza`.
- **Qué responde el sistema**: buscador con la etiqueta **"Buscar"** y el texto de ayuda
  **"Cédula, nombre o apellido, o número de operación"**, botones **"Buscar"**/**"Limpiar"**;
  debajo, 3 secciones por urgencia — **"En mora (n)"** (roja: Cliente, C.I., Operación, Cuota,
  Monto exigible, Vencimiento, Atraso), **"Vencen hoy (n)"** (amarilla) y **"Próximos 7 días
  (n)"** (gris) — estas dos últimas con Cliente, C.I., Operación, Cuota, Monto, Vencimiento. Hoy,
  **las tres en 0** — el segundo cliente de práctica no aparece en ninguna, porque su cuota 1
  vence el 23/11/2026 (fuera de la ventana de "próximos 7 días").
- **Qué hace el usuario**: busca por la cédula del segundo cliente de práctica. Resultado: tabla
  con **Operación** `CC-2026-00002`, **Cliente**, **C.I.**, **Próxima cuota** **"1/12 — vence
  23/11/2026"**, **Monto a cobrar** **193.280 Gs.**, **Estado** **"Al día"** (verde), botón
  **"Cobrar"**.
- **Regla de negocio**: ver [RF20_lista_trabajo_cobranza.md](../funcionamiento/RF20_lista_trabajo_cobranza.md).
- **Qué sigue**: tocar "Cobrar" lleva a la ficha de cobranza (`/cobranza/{id}`) — ver Paso 2 y
  `06_cobro_mapa_completo.md` § 2 para lo que pasa ahí (confirmación de B55, abajo).
- **Hallazgos**: ver B54 y B55 más abajo.

### Verificado en el código (23/10/2026)

- **Método de cada sección**: las 3 tarjetas de la lista de trabajo (sin búsqueda) salen de
  `ControladorCobranzaServlet.mostrarBusqueda` llamando a `ServicioMora.listarPrestamosEnMora`
  ("En mora") y `ServicioReportes.listarVencimientos` ("Vencen hoy"/"Próximos 7 días", filtrado
  por rango de fechas) — `ServicioCobranza.armarListaTrabajo` (pura) después las combina para
  que un préstamo `EN_MORA` no se repita en las otras dos.
- **Método de la búsqueda**: `ServicioCobranza.buscarParaCobranza` — número de operación exacto
  primero; si no, `PersonaDAO.construirWhere` (`ILIKE` sobre cédula/nombre/apellido, sin
  acentos, sin escapar `%`/`_`) entre clientes con préstamo `ACTIVO`/`EN_MORA`/
  `PENDIENTE_DESEMBOLSO`. Con un único préstamo cobrable, `resolverUnPrestamo` lo devuelve
  directo.
- **De dónde sale "Monto a cobrar" para una cuota no vencida — confirma B55**:
  `ControladorCobranzaServlet.construirInfoCobroPorPrestamo` — para un préstamo que **no** está
  `EN_MORA` (sin fila en `ServicioMora.listarPrestamosEnMora`), toma la próxima cuota pendiente
  (`ServicioCobranza.obtenerProximaCuotaPendiente`) y muestra **literalmente su
  `getTotalProgramado()`** como "Monto a cobrar", marcando siempre **"Al día"** (verde) — **sin
  ninguna condición sobre si esa cuota ya venció o no**. Es un número real (el total programado
  de la cuota 1, con IVA), pero **no es lo que "Cobrar" aceptaría hoy**: ese botón, en la ficha,
  usa `ServicioCobranza.calcularMontoCobrableNormal` (ver
  `06_cobro_mapa_completo.md` § 2), que da **0 Gs.** para esta misma cuota porque todavía no
  venció. Dos métodos distintos, mostrando dos números distintos para la misma cuota, en dos
  pantallas seguidas del mismo flujo.
- **Qué hace "Cobrar" (adónde lleva)**: es un enlace simple
  (`<a href=".../cobranza/{idPrestamo}">Cobrar</a>`), **no dispara ningún cobro por sí mismo** —
  lleva a `GET /cobranza/{idPrestamo}` (la ficha), donde recién ahí el botón real de la ficha
  (el mismo que cambia de texto según `hayAlgoParaCobrar`, ver el mapa completo) decide si hay
  algo que cobrar de verdad.

## Hallazgos del Paso 1

- **B54 (menor)** — La bandeja de `/cobranza` solo lista préstamos en mora, que vencen hoy o
  que vencen en los próximos 7 días. Un cliente que viene a pagar **antes** de esa ventana (como
  el segundo cliente de práctica, a 31 días de su primera cuota) **no aparece en ninguna de las
  3 secciones** — la única forma de encontrarlo es el buscador, sabiendo de antemano su cédula,
  nombre o número de operación. No hay ninguna lista de "préstamos activos" en general para
  revisar sin buscar algo puntual.
- **B55** — El resultado de la búsqueda muestra **"Monto a cobrar 193.280 Gs."** y el botón
  **"Cobrar"** para una cuota que el sistema, un paso después, **no permite cobrar por esa vía**
  (debe usarse "Adelantar a capital", ver `06_cobro_mapa_completo.md` § 2 y § 5). La pantalla
  invita a una acción concreta (cobrar 193.280 Gs.) que luego se rechaza — confirmado en el
  código (`construirInfoCobroPorPrestamo` vs. `calcularMontoCobrableNormal`, dos cálculos
  distintos para la misma cuota, ver "Verificado en el código" arriba). **A confirmar en el
  paso siguiente** (Paso 2 de esta escena): si al tocar "Cobrar"/"Pagar cuota" en la ficha
  aparece el mensaje de rechazo exacto de RF22 ("Este préstamo no tiene cuotas vencidas por
  cobrar...") o si la ficha ya corrige el número y ofrece "Adelantar a capital" directamente,
  sin pasar por el rechazo.

## Pasos previstos

## Paso 2 — Deuda exigible del día y tarjeta de Cobranza (RF21/RF22/RF27)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: toca **"Cobrar"** desde la bandeja (Paso 1). Se abre, en la misma
  pestaña, `GET /cobranza/{id}?q=…` (conserva el texto buscado, para que "Volver a la búsqueda"
  regrese ahí en vez de a la lista de trabajo).
- **Qué responde el sistema**: título **"CC-2026-00002"** con la etiqueta **"Activo"**, cliente
  y C.I.; tarjeta **"Historial de cobros"** (columnas Fecha/hora, Tipo, Recibo, Monto,
  Comprobantes, Reversión), hoy **vacía**: **"Todavía no hay cobros registrados."**; tarjeta
  **"Deuda exigible hoy"**: **"Sin cuotas vencidas."** (no se arma la tabla de RF21, no hay
  nada vencido); tarjeta **"Cobranza"**, con dos botones lado a lado — **"Adelantar a capital
  193.280 Gs. — en efectivo: 193.250 Gs."** y **"Cancelar todo 2.000.000 Gs."** — y, debajo del
  primero, el selector **"1 cuota / 2 cuotas / 3 cuotas"**. Texto de ayuda: *"El monto en
  efectivo se redondea a favor del cliente, en múltiplos de 50 Gs. Adelantar a capital reduce la
  cantidad de cuotas; el monto de cada cuota queda similar (ver abajo)."* Siempre visible:
  *"Si adelanta, su próxima cuota será el **23/11/2026** por **192.891 Gs.**, quedan **11
  cuotas**."* Campo **"Recibido"** (193.250, con el monto en letras debajo); **"Vuelto: Sin
  vuelto (0 Gs.)"**; **"Ver detalle del adelanto"** (desplegable, cerrado por defecto); botón
  **"Cobrar"**; enlace **"Volver a la búsqueda"**.
- **Al abrir "Ver detalle del adelanto"**: **"Interés devengado hoy: 0 Gs. + IVA 0 Gs. Capital
  aplicado: 193.250 Gs. Saldo que queda: 1.806.750 Gs."** (2.000.000 − 193.250) — **confirma en
  pantalla, con el desglose completo, lo que "Verificado en el código" ya anticipaba**: el
  **capital aplicado es 193.250 Gs. (el redondeado), no 193.280 Gs.** (el de la tarjeta grande)
  — exactamente C25. El "saldo que queda" (1.806.750) coincide con el cálculo ya hecho más
  abajo.
- **Regla de negocio**: ver [RF21_deuda_exigible.md](../funcionamiento/RF21_deuda_exigible.md),
  [RF22_cobro_efectivo.md](../funcionamiento/RF22_cobro_efectivo.md) y
  [RF27_adelantar_capital.md](../funcionamiento/RF27_adelantar_capital.md); cláusula **SEXTA**
  del contrato ("Pago anticipado").
- **IMPORTANTE, confirmado hoy**: con la cuota no vencida, **la pantalla no muestra ningún
  botón de "cobro normal de cuota"** — el único botón de pago dice literalmente **"Cobrar"**,
  pero la operación detrás es un **anticipo de capital** (confirmado en "Verificado en el
  código", abajo). **El rechazo exacto de RF22** ("Este préstamo no tiene cuotas vencidas por
  cobrar. Si el cliente quiere pagar antes del vencimiento, use 'Adelantar capital'.") **existe
  solo en el servidor y no es alcanzable desde la interfaz** — no hay ningún camino en pantalla
  para llegar a verlo con esta cuota. **La "parte 1" del plan queda cerrada así: no hay botón de
  cobro normal, no hay mensaje de rechazo en pantalla** — el servidor lo rechazaría si alguien
  forzara la petición, pero la cajera, siguiendo la pantalla tal cual la ve, nunca se topa con
  ese mensaje.
- **Qué sigue**: tocar "Cobrar" (el anticipo) queda para un próximo registro — no se confirmó
  hoy, solo se dejó la ficha cargada con la vista previa.
- **Hallazgos**: ver C23, C24, C25, B55 (ampliación) y B56 más abajo.

### Verificado en el código (23/10/2026)

- **Cómo se calcula 193.280 y por qué se redondea a 193.250**: `ControladorCobranzaServlet.
  mostrarFicha` (línea ~570) toma la **próxima cuota pendiente** (`ServicioCobranza.
  obtenerProximaCuotaPendiente`) — su `getTotalProgramado()` **es** 193.280 Gs. (la cuota 1
  original, sin tocar) — y la pasa por `redondearHaciaAbajo(193.280, credito.redondeo_efectivo
  = 50)` → **193.250 Gs.** Ese monto **redondeado** (no el exacto) es el que de verdad se usa
  para calcular la vista previa (`calcularPreviewAnticipoParcial(..., montoAnticipoRedondeado)`)
  y el que se manda al servidor como **`montoAplicado`** si la cajera confirma sin editar nada
  — el campo "Recibido" que ella ve solo sirve para calcular el vuelto **en el navegador**,
  nunca se manda al servicio (comentario del propio código, D-145: *"nunca con lo que la cajera
  escriba en 'Recibido' — eso sirve solo para el vuelto en pantalla, nunca se manda al
  servicio"*).
- **Quién absorbe la diferencia de 30 Gs. (193.280 − 193.250) — confirma C25**: **nadie la
  "absorbe" como un descuento registrado.** A diferencia de "Cobrar"/"Cancelar todo"
  (`ServicioCobranza.calcularRedondeoEfectivo`, donde el monto **exacto** sí se aplica a la
  deuda —la empresa de verdad perdona esos guaraníes— y queda registrado en
  `pagos.monto_redondeo`), **`ServicioCobranza.confirmarAnticipoCapital` no calcula ningún
  redondeo propio**: recibe `montoAplicado` como parámetro ya resuelto (193.250, puesto por el
  controlador) y lo aplica tal cual al capital — el comentario del código lo confirma
  explícito: *"variante ANTICIPO, que no aplica redondeo"* (`pago.getMontoRedondeo()` queda en
  0 para un anticipo). En los hechos: los 30 Gs. **nunca entran en esta operación** — simplemente
  se adelantan 193.250 Gs. de capital en vez de 193.280 Gs.; esos 30 Gs. siguen como parte del
  capital pendiente, a cobrarse más adelante con el resto del saldo. No hay una fila de
  auditoría ni una columna que diga "se descontaron 30 Gs." — es, sencillamente, un monto más
  chico desde el principio.
- **Cómo se calcula la nueva cuota (192.891 Gs.) y por qué no es igual a la original**:
  `ServicioMotorFinanciero.calcularAnticipoParcial` → `regenerarPlanAnticipo`. Con la fecha de
  hoy igual a la de desembolso, `periodoActual` devuelve el tramo de la cuota 1 con
  **`díasTranscurridos = 0`** y `díasDelPeríodo = 31` (23/10 a 23/11) — así que el interés
  devengado del anticipo es **`2.000.000 × tasaMensual × (0/31) = 0 Gs.`** (sin IVA tampoco) —
  por eso **"Vuelto: Sin vuelto (0 Gs.)"** y todo el monto (193.250) va íntegro a capital
  (`montoParaCapital = monto − 0 − 0`). El **nuevo saldo** queda en `2.000.000 − 193.250 =
  1.806.750 Gs.` El plan se regenera con la **misma `cuotaConstante`** (capital + interés de la
  cuota 1 original, sin IVA: `149.020 + 40.236 = 189.256`) y una cantidad nueva de cuotas `n'`
  (11, en vez de 12) — pero la **cuota 1 del plan nuevo** (misma fecha de vencimiento, 23/11/2026,
  porque el período sigue siendo el mismo) se recalcula **sobre el saldo ya reducido**:
  interés ≈ `1.806.750 × tasaMensual` ≈ **36.350 Gs.** (en vez de los 40.236 Gs. originales,
  porque el saldo es menor), capital = `189.256 − 36.350` ≈ **152.906 Gs.**, IVA = `10 % ×
  36.350` ≈ **3.635 Gs.** → total = `36.350 + 152.906 + 3.635` = **192.891 Gs.** La diferencia
  con los 193.280 Gs. originales **no es un error ni rompe la cláusula SEXTA en sí** — es el
  mismo mecanismo ya documentado en `04_formalizacion.md` (C4) y en `02_solicitud.md` ("por qué
  la cuota total baja levemente, mes a mes"): `cuotaConstante` (capital + interés, **sin IVA**)
  sí queda fija, pero el **IVA se calcula sobre el interés de ESE período puntual**, que baja
  junto con el saldo — así que el **total con IVA** (lo único que la pantalla llama "el monto de
  la cuota") nunca es idéntico entre una cuota y la siguiente, con o sin anticipo. Ver C24.
- **Qué hace cada opción del selector "1 cuota / 2 cuotas / 3 cuotas"**: pura presentación,
  sin ningún cálculo nuevo en el servidor (`ControladorCobranzaServlet`, comentario D-145 P-7).
  "2 cuotas"/"3 cuotas" multiplican el monto exacto de la **misma** próxima cuota (193.280) por
  2 o por 3 (nunca suman cuotas reales distintas con sus propios montos) y lo redondean igual —
  sirven para pre-cargar el campo "Recibido" con un monto mayor de un solo saque. Un botón **N**
  se oculta si el monto de **N** cuotas ya iguala o supera "Cancelar todo" (para ese caso ya
  está el otro botón). El monto real que se aplica sigue siendo el que termine en el campo al
  confirmar — `procesarAnticipar` manda **el menor entre lo recibido y el monto elegido**, nunca
  más de lo que la cajera efectivamente tecleó.
- **Cálculo de "Cancelar todo" (2.000.000 Gs.) — interés devengado 0 por ser el mismo día del
  desembolso**: `ServicioMotorFinanciero.calcularCancelacionTotal` — sin nada vencido (ninguna
  cuota con fecha de vencimiento anterior a hoy) y con el interés devengado del período en
  curso también en **0 Gs.** (mismo motivo que arriba, `díasTranscurridos = 0`), el total a
  cancelar es, exactamente, el **capital pendiente total** (2.000.000 Gs., nada más) —
  **ningún interés, ninguna mora**, tal como promete la Ley 6624/2020 citada en la cláusula
  SEXTA.

## Hallazgos del Paso 2

- **C23 (para el dueño y la fase 2)** — Un cliente que "paga su primera cuota" antes del
  vencimiento **no está, en los hechos, pagando esa cuota** — recibe un **anticipo de capital**:
  su próxima cuota sigue venciendo el **23/11/2026** (la misma fecha de siempre) y **la sigue
  debiendo igual**, solo que un poco más chica (192.891 en vez de 193.280) y con 1 cuota menos
  al final del plan (11 en vez de 12). Si el cliente (o la cajera) esperaba que "adelantar la
  cuota 1" significara que esa cuota queda saldada y la próxima pasa a ser la 2, **no es así**:
  sigue siendo, formalmente, la cuota 1 de un plan nuevo. Es un choque real entre la expectativa
  más natural ("ya pagué la cuota de noviembre") y el comportamiento del sistema (un adelanto de
  capital que recorta el final del préstamo, no "tacha" la próxima cuota) — directamente
  relacionado con el rediseño de la metodología de cobro que planea Joel para la fase 2. No
  decido acá si esto amerita cambiarse — queda para que Joel o el dueño de Capital Crédito lo
  evalúen.
- **C24 (relacionado con C4, `04_formalizacion.md`)** — La cláusula SEXTA dice que el pago
  anticipado parcial **"mantiene el monto de la cuota"**, pero la pantalla ya dice **"similar"**
  (no "no cambia", corrección ya hecha por Joel el 12/10/2026, ver `RF27_adelantar_capital.md`
  §6f) y, con este ejemplo real, la próxima cuota pasa de **193.280 a 192.891 Gs.** — una
  diferencia real, no solo teórica. **Verificado que no es una contradicción nueva**: C4 ya
  había concluido, leyendo el código, que `cuotaConstante` (capital + interés, **sin IVA**) sí
  queda fija — lo que varía es el **IVA sobre el interés** de esa cuota puntual, que baja junto
  con el saldo (mismo mecanismo, con o sin anticipo, que ya hace que la cuota 2 de **cualquier**
  plan valga un poco menos que la cuota 1). Este registro **confirma con un número real** lo que
  C4 dejaba pendiente de confirmar en pantalla. **Queda para que Joel o el dueño decidan** si
  "mantiene el monto de la cuota" (cláusula SEXTA, cara al cliente) debería referirse al total
  con IVA (como hoy podría leerse, y no se cumple al pie de la letra) o al capital+interés fijo
  (como lo implementa el código, y sí se cumple) — no es un error de cálculo, es una posible
  imprecisión de redacción entre el contrato y lo que de verdad ve el cliente en el recibo.
- **C25** — El redondeo a favor del cliente (193.280 → 193.250, múltiplos de 50 Gs.) funciona
  **distinto** en "Adelantar a capital" que en "Cobrar"/"Cancelar todo". **Verificado en el
  código**: acá no hay un monto "exacto" que se aplique a la deuda mientras se cobra menos en
  efectivo (como sí pasa en el cobro normal, donde la empresa perdona la diferencia) — el monto
  **aplicado** ya es, directamente, el redondeado (193.250); los 30 Gs. de diferencia **no se
  registran como descuento en ningún lado** (`pago.getMontoRedondeo()` queda en 0 para un
  anticipo) — simplemente ese capital nunca se adelantó, sigue como parte de la deuda futura.
  **Nadie "absorbe" los 30 Gs. en el sentido contable** — no hay pérdida para la empresa ni
  beneficio adicional para el cliente más allá de adelantar un monto levemente menor al exacto.
  No decido si esto amerita unificarse con el otro mecanismo de redondeo — queda anotado, en la
  misma línea que la observación ya registrada en `06_cobro_mapa_completo.md` § 13 punto 5
  (dos implementaciones distintas del mismo criterio de redondeo).
- **B55 (ampliación)** — Confirmado con este registro: el botón se llama **"Cobrar"** tanto en
  la bandeja (Paso 1) como en esta misma ficha — en los dos casos, el texto no cambia aunque la
  operación real detrás sea un anticipo de capital, no un cobro de cuota. El propio código
  distingue internamente "Pagar cuota" vs. "Adelantar a capital" como dos textos de **ayuda**
  distintos (`textoEtiquetaPagarCuota`) para la tarjeta grande, pero el **botón final de
  confirmación**, al pie de la ficha, sigue diciendo solo **"Cobrar"** sin distinguir cuál de
  las dos operaciones es. El nombre no dice lo que realmente va a pasar.
- **B56** — El desglose **interés devengado + IVA, capital aplicado y saldo que queda** — lo
  más importante para entender de verdad qué va a pasar con el pago — está **oculto detrás de
  "Ver detalle del adelanto", cerrado por defecto**. La cajera (o el cliente, si mira la
  pantalla) tiene que acordarse de abrirlo para ver, por ejemplo, que el **capital aplicado es
  193.250 Gs. y no los 193.280 Gs.** que muestra el botón grande (C25) — información que hoy
  queda un clic más allá de donde se ve el monto principal. Sugerir mostrar este desglose
  siempre, junto al monto, **antes** de tocar "Cobrar" — no detrás de un desplegable que
  requiere saber que existe.

## Paso 3 — Confirmar el anticipo: recibo, caja y plan nuevo (RF22/RF23/RF27)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: toca **"Cobrar"** (Paso 2).
- **Qué responde el sistema**: confirmación **"Confirmar: Adelantar a capital"**, con el
  detalle completo ya resumido —**Adelantar a capital 193.250 Gs.**; **Recibido 193.250 Gs.**;
  **Sin vuelto (0 Gs.)**; un separador; **Interés devengado hoy 0 Gs. + IVA 0 Gs.**; **Capital
  aplicado 193.250 Gs.**; **Saldo que queda 1.806.750 Gs.**; **Próxima cuota 23/11/2026 por
  192.891 Gs.**; **Cuotas que quedan 11**— y los botones **"Sí, adelantar a capital"** /
  **"Cancelar"**. **Dato positivo**: esta confirmación ya es un modelo completo (título de la
  operación real, no genérico, y el desglose entero antes de confirmar) — citar como patrón
  para **B4** (`04_formalizacion.md`) y **B43** (`03_aprobacion.md`), y como contraejemplo de
  **B55** (el botón "Cobrar" en sí no distingue la operación, pero esta confirmación sí).
- **Al confirmar**: cuadro de éxito **"Anticipo de capital aplicado (193.250 Gs.) — Recibo
  REC-2026-000001. Sin factura (no hay conceptos gravados)."**, con **"Imprimir recibo"** y
  **"Aceptar"**.
- **La ficha vuelve a mostrar**: **"Historial de cobros"** con una fila — 23/10/2026 11:30,
  **Anticipo de capital**, el recibo, el monto, los comprobantes, y en la columna
  **"Reversión"** un campo **"Motivo (mínimo 10 caracteres)"** con el botón rojo **"Solicitar
  reversión"**. Tarjeta **"Cobranza"** actualizada: **"Cancelar todo 1.806.750 Gs."** (el nuevo
  saldo), un nuevo adelanto propuesto en **192.891 Gs. (efectivo 192.850 Gs.)**, con la próxima
  cuota **23/11/2026 por 192.503 Gs.**, quedan **10 cuotas** (si se volviera a adelantar).
- **Regla de negocio**: ver [RF22_cobro_efectivo.md](../funcionamiento/RF22_cobro_efectivo.md),
  [RF23_recibo_factura.md](../funcionamiento/RF23_recibo_factura.md) y
  [RF27_adelantar_capital.md](../funcionamiento/RF27_adelantar_capital.md).
- **Hallazgos**: ver C26 y B57 más abajo.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`pagos`** (id 1): `id_prestamo`=2, `id_sesion_caja`=3 (la caja del 23/10), `numero_recibo`
  **`REC-2026-000001`**, `fecha_hora_pago` 23/10/2026 11:30:53, `monto_recibido`=193.250,
  `monto_vuelto`=0, `monto_aplicado`=193.250, `estado_pago`=`CONFIRMADO`, `tipo`=**`ANTICIPO`**,
  **`monto_redondeo`=0** (confirma otra vez C25: no se registra ningún descuento para un
  anticipo).
- **`aplicaciones_pago`** (fila 1): `tipo_aplicacion`=`ANTICIPO_CAPITAL`, **`id_cuota` = NULL**
  (no se imputa a ninguna cuota puntual, es una aplicación a capital en general), `monto_capital`
  = `subtotal_aplicado` = 193.250, moratorio/punitorio/interés/tributos todos en 0.
- **`planes_pago`**: el plan original (`id 2`, versión 1, `ORIGINAL`) pasa a `vigente = false`;
  se inserta un **plan nuevo** (`id 3`, versión 2, `origen_plan = 'ANTICIPO_PARCIAL'`, `vigente
  = true`, `id_liquidacion_origen` apunta al pago 1) — RN-13, el plan anterior **no se borra**.
- **`cuotas` del plan nuevo (vigente)**: **11 filas**, todas `PENDIENTE`. Cuota 1: vencimiento
  23/11/2026, capital 152.908, interés 36.348, IVA 3.635, **total 192.891 Gs.** (exacto, ver el
  cálculo del Paso 2) — hasta la cuota 11 (23/09/2027), que absorbe el resto del saldo (capital
  131.547, bastante menor que el resto, por ser la última).
- **Movimiento de caja** (sesión N.º 3): `COBRO`, monto 193.250, signo +1, `referencia_tipo =
  PAGO`. Saldo teórico de la sesión: fondo inicial 2.000.000 + `DESEMBOLSO` (−2.000.000, del
  Paso de Desembolso) + este `COBRO` (+193.250) = **193.250 Gs.** — **no 2.193.250**: el
  desembolso de esta misma sesión ya había bajado el saldo a 0 antes de este cobro.
- **Numerador `RECIBO`**: `ultimo_valor = 1` para 2026 — el primero de esta base. **No existe
  ninguna fila en `numeradores` para `FACTURA`** — confirma "Sin factura" literalmente: ni
  siquiera se llegó a tocar ese numerador (coherente con interés devengado = 0, nada gravado).
- **Auditoría**: operación **`ANTICIPAR_CAPITAL`** (entidad PAGO, id 1) — *"Adelantó 193.250
  Gs. a capital de CC-2026-00002."* — **no** `COBRAR` (ese código es el de RF22, "Cobrar"; un
  anticipo audita con su propio nombre de operación).

## Hallazgos del Paso 3

- **C26** — Tras el anticipo, el **plan de pagos cambia** (12 cuotas → 11, otros montos), pero
  el **plan de pagos firmado que el cliente se llevó al formalizar** (el PDF original, con 12
  cuotas) **queda desactualizado** — nadie se lo reemplaza automáticamente. Verificado:
  - **¿El sistema genera o permite imprimir un plan nuevo para el cliente?** **Parcialmente.**
    La pantalla `/formalizacion/{id}` → pestaña "Plan de pagos" **sí muestra el plan vigente en
    vivo** (la tabla lee las cuotas del plan `vigente = true`, no un PDF estático) y **ya
    incluye un aviso explícito** cuando no es el original: *"Plan vigente después del adelanto
    de capital del [fecha]."* (implementado desde el 14/10/2026, antes de este recorrido) — así
    que alguien que entre a esa pantalla SÍ ve el plan correcto. Pero el **PDF formal, numerado,
    firmado por las partes** (`plan_pagos.jasper`, el que el cliente tiene en papel) **no se
    regenera solo**, y los botones que podrían regenerarlo ("Regenerar documentos"/"Re-emitir
    los PDF") están **bloqueados** para este préstamo — el checklist de los 3 firmados ya está
    completo desde la formalización (`asegurarSinDocumentosFirmados` rechaza una vez que hay un
    contrato/pagaré/plan firmado cargado). No hay, hoy, ningún camino para entregarle al cliente
    un **nuevo** plan de pagos en papel con el número de operación formal.
  - **¿El recibo lo menciona?** Solo de forma mínima — el recibo trae *"Próxima cuota (IVA
    incluido): [fecha] por [monto]"* (un solo dato, no el plan completo), sin decir
    explícitamente que el plan cambió de 12 a 11 cuotas ni ofrecer un camino para conseguir el
    plan actualizado.
  - **Alternativa real que sí existe**: el **Informe de estado de cuenta**
    (`/reportes/estado-cuenta/{id}/informe`, RF34) incluye el plan de pagos **vigente** (no el
    original) junto con el historial — es un documento imprimible y actualizado, aunque es un
    informe operativo, no el documento legal formal que el cliente firmó.
  - **Qué dice el RF/contrato**: ni el anteproyecto (RF27) ni la cláusula SEXTA exigen entregar
    un nuevo plan impreso tras un anticipo — el RF27 solo exige "mostrar antes de confirmar" el
    interés devengado, capital, saldo y próxima cuota (ya cumplido, Paso 2). La entrega de un
    plan físico actualizado no está pedida en ningún documento normativo de este proyecto.
  - **Conclusión: riesgo parcial** (sin clasificar A/B) — no hay un incumplimiento de ningún
    RF, pero el cliente queda con un papel en la mano que ya no refleja la realidad de su deuda
    (12 cuotas en el papel, 11 en el sistema, con montos distintos desde la cuota 1), y el único
    camino para mostrarle algo actualizado en papel es un informe operativo, no pensado como
    documento para entregar al cliente. Queda para que Joel o el dueño de Capital Crédito
    decidan si hace falta un camino más directo (ej. habilitar "Re-emitir los PDF" incluso con
    firmados cargados, solo para el plan, o imprimir el Informe de estado de cuenta como
    entrega estándar tras un anticipo).
- **B57** — Verificado en el código: **"Solicitar reversión" no aparece en cada fila del
  historial** — `ServicioCobranza.listarHistorialCobros` lo decide, y solo lo habilita en la
  fila de la **operación más reciente todavía vigente** (`filaHistorial.
  isPuedeSolicitarReversion()`), igual para cualquier préstamo con más de un cobro. Con el
  segundo cliente de práctica hoy solo hay **un** cobro, así que se ve en la única fila que
  existe — lo que podía leerse como "en cada fila" es, en los hechos, "en la única fila de
  hoy". **Quién autoriza y efecto** (ver `06_cobro_mapa_completo.md` § 10, ya documentado): la
  cajera solicita (con el motivo, mínimo 10 caracteres, y la casilla de la factura si
  corresponde — acá no, "Sin factura"); el **gerente** autoriza o rechaza desde `/reversiones`;
  al autorizar, se deshace exactamente el anticipo (el plan de 11 cuotas vuelve a no vigente, el
  de 12 vuelve a vigente), se genera un movimiento `COMPENSACION` en la caja de hoy, y el pago
  queda `REVERTIDO`. **¿Conviene esa ubicación?** Tiene sentido estar junto al cobro que se
  quiere revertir (no hay que ir a buscarlo a otro lado), pero el campo de motivo y el botón
  rojo, dentro de la misma fila angosta de la tabla del historial, compiten visualmente con las
  columnas de comprobantes — no se encontró ningún problema de **permisos** (la condición ya es
  correcta, solo la última operación), solo una posible mejora de presentación. No se propone
  nada concreto — queda anotado para la fase 2.

## Paso 4 — El recibo impreso (RF23/RF25)

### Lo observado (23/10/2026, segundo cliente de práctica)

Al tocar "Imprimir recibo" desde el mensaje de éxito del Paso 3, se abre un PDF de una sola
página:

- Encabezado con el escudo, razón social, RUC, domicilio, teléfono/WhatsApp y **correo
  electrónico** de la empresa, y el recuadro "RECIBO DE DINERO N.º REC-2026-000001".
- "Asunción, [fecha y hora]"; "Operación: [identificador]"; "Cliente: [nombre] C.I.: [cédula]".
- **"Factura asociada: no corresponde (sin conceptos gravados)."**
- "Anticipo de capital: 193.250 Gs. – Interés del período en curso: 0 Gs. – IVA: 0 Gs."
- "Resumen del plan regenerado: 11 cuotas restantes, nuevo último vencimiento 23/09/2027.
  Próxima cuota (IVA incluido): 23/11/2026 por 192.891 Gs. Saldo de capital después del pago:
  1.806.750 Gs."
- "Efectivo recibido: 193.250 Gs. Vuelto: 0 Gs. Aplicado: 193.250 Gs."
- "Son:" con el monto en letras.
- Líneas de firma ("Firma de la cajera ([nombre])" / "Firma del cliente").
- "ORIGINAL: Cliente                    COPIA: Capital Crédito".
- "Emitido por: [usuario] — [fecha de emisión]" y "Página 1 de 1".

### Verificado en el código

- **Plantilla**: `src/main/resources/reportes/recibo.jrxml` (documento interno, no fiscal —
  comentario propio del archivo, "no calcula ni recalcula nada", todo viene calculado desde
  `ServicioCobranza.generarPdfRecibo`). El PDF se genera **una sola vez** y se guarda
  (`obtenerOGenerarPdf`, con `id_archivo_recibo` en `pagos`); "Imprimir recibo" siempre reabre el
  mismo archivo — no se regenera en cada impresión, salvo que el pago pase a `REVERTIDO` (ahí sí
  se regenera, con la franja roja "ANULADO POR REVERSIÓN").
- **"Resumen del plan regenerado" (`ANTICIPO_CUOTAS_RESTANTES`/`ANTICIPO_ULTIMO_VENCIMIENTO`/
  `ANTICIPO_PROXIMA_CUOTA_FECHA`/`_MONTO`)**: `ServicioCobranza.generarPdfRecibo` (líneas
  ~1976-1997) lee directamente las cuotas del **plan nuevo ya regenerado** (`cuotasNuevoPlan`,
  el mismo que acaba de versionar `planes_pago`/`cuotas` en el Paso 3) — no es un texto fijo ni
  una aproximación: cuotas restantes = `cuotasNuevoPlan.size()`, último vencimiento = la fecha de
  la última cuota de esa lista, próxima cuota = capital+interés+IVA de la primera.
- **"Factura asociada: no corresponde..."**: `parametros.put("TIENE_FACTURA", factura != null)`
  (línea 1899) — simplemente si existe o no una `Factura` ligada a este pago. Como el anticipo
  del mismo día de desembolso tiene interés devengado = 0 (Paso 2), no hay conceptos gravados y
  nunca se crea la factura (RN-25, ya citado en `06_cobro_mapa_completo.md` § 8).
- **Una o dos copias**: el sistema genera **un solo PDF de una sola página** con el texto fijo
  "ORIGINAL: Cliente / COPIA: Capital Crédito" impreso una vez — no hay una segunda página ni un
  segundo PDF generado para la copia de la empresa. Comparado con el comprobante de desembolso
  (`comprobante_desembolso.jrxml`, comentario propio del archivo: *"Dos ejemplares en dos
  páginas: banda title = ORIGINAL (Empresa), banda summary = COPIA"*, con los textos "ORIGINAL –
  Empresa" / "COPIA – Cliente"), el recibo no reproduce ese patrón de dos páginas — si la empresa
  quiere quedarse con una copia física, hoy depende de que la cajera imprima el mismo PDF dos
  veces por su cuenta.
- **Plan completo regenerado para el cliente**: la ficha de `/formalizacion/{id}`, pestaña "Plan
  de pagos", ya muestra el plan vigente completo en pantalla (Paso 3/C26). Para entregar algo en
  papel con el plan completo (no solo la próxima cuota, como el recibo), la única vía existente
  es el **Informe de estado de cuenta** (`/reportes/estado-cuenta/{id}`, RF34,
  `ServicioInformes.generarEstadoCuentaPdf`). Verificado en
  `src/main/java/.../filtros/FiltroAutorizacion.java` (línea 71) y
  `ServicioAutorizacion.construirMatriz()`: la ruta `/reportes` exige `Permiso.VER_REPORTES`, con
  nivel **TOTAL para el gerente, CONSULTA para el oficial y NINGUNO para la cajera**. Es decir:
  **la cajera que acaba de cobrar el anticipo no puede generar ella misma el estado de cuenta**
  para entregárselo al cliente en el momento — tendría que pedírselo al gerente o al oficial.

## Hallazgos del Paso 4

- **C26 (actualización final)**: con el recibo ya visto, el cuadro queda así — el recibo
  **sí** informa, con datos en vivo del plan recién regenerado, las cuotas restantes, el nuevo
  último vencimiento, el monto de la próxima cuota y el saldo de capital; lo que **no** entrega
  es el **plan completo cuota por cuota** (eso solo está en pantalla, en
  `/formalizacion/{id}`, o en el Informe de estado de cuenta de RF34) y ese informe está **fuera
  del alcance de la cajera** (`Permiso.VER_REPORTES` = NINGUNO para su perfil). Conclusión:
  **cubierto para lo esencial del día a día (el cliente sabe cuánto y cuándo paga después), pero
  parcial para un plan completo en papel** — si el cliente lo pide, la cajera no tiene cómo
  dárselo sin la intervención de otro perfil. Sin clasificar A/B — queda para que Joel o el
  dueño de Capital Crédito decida si esto amerita un comprobante propio o ampliar el acceso.
- **B58**: el recibo dice "ORIGINAL: Cliente / COPIA: Capital Crédito" pero se imprime en una
  sola página (un solo PDF); el comprobante de desembolso, en cambio, imprime 2 páginas
  separadas y con el criterio **inverso** ("ORIGINAL – Empresa", "COPIA – Cliente" — ahí el
  original es de la empresa, no del cliente). Dos comprobantes del mismo sistema no coinciden ni
  en cuántas páginas usan ni en quién se queda con el "original". Sugerencia: unificar el
  criterio de original/copia (y el formato de una o dos páginas) en todos los comprobantes de
  SGCPC.
- **B59 (menor)**: el correo electrónico de la empresa solo aparece una vez, en el encabezado;
  y, en la variante de anticipo (sin tabla de cuotas, porque `columnHeader`/`detail` no imprimen
  cuando `ES_ANTICIPO`), el contenido real ocupa poco más de la mitad de la página y deja un
  espacio en blanco grande hacia el final — la banda `summary` tiene una altura fija (260) pensada
  para la variante con tabla de cuotas, de forma que un recibo de anticipo (sin tabla) la deja
  mayormente vacía. Mismo patrón ya señalado en otros comprobantes (familia **B12**).

## Paso 5 — El efecto en la caja de hoy (RF27)

### Lo observado (23/10/2026, segundo cliente de práctica)

En `/caja`, después del anticipo: sesión abierta desde las 10:22 (fondo 2.000.000 Gs.), saldo
teórico actual **193.250 Gs.**. Tres movimientos:

| Fecha/hora | Tipo | Monto | Observación | Comprobante |
|---|---|---|---|---|
| 10:22 | Apertura (fondo inicial) | +2.000.000 | — | — |
| 10:35 | Desembolso | −2.000.000 | "Desembolso de CC-2026-00002." | DES-2026-000002 (Reimprimir) |
| 11:30 | **Cobro** | +193.250 | "Anticipo de capital de CC-2026-00002." | REC-2026-000001 (Reimprimir) |

Formulario de "Cierre y rendición" con saldo teórico 193.250. "Sesiones cerradas: 2" (de antes
de hoy).

### Verificado en el código

- **Tipo exacto del movimiento**: verificado con `SELECT` en `sgcpc_db_pruebas` —
  `movimientos_caja` tiene una única fila para esta operación, **`tipo = 'COBRO'`, `signo = 1`,
  `monto = 193250`**, enlazada a `pagos.id_pago = 1` (`tipo_pago = 'ANTICIPO'`, `numero_recibo =
  'REC-2026-000001'`). El modelo de datos (`enumeraciones/TipoMovimientoCaja.java`, espejo del
  `CHECK` de `movimientos_caja.tipo`) **no tiene un valor `ANTICIPO` propio** — solo
  `ENTREGA_GERENTE`/`RENDICION_GERENTE`/`DESEMBOLSO`/`COBRO`/`VUELTO`/`COMPENSACION`. Un anticipo
  de capital, un cobro normal y una cancelación total usan el **mismo** `TipoMovimientoCaja.COBRO`
  — lo único que distingue cuál fue cuál es el `pagos.tipo` del pago enlazado
  (`ServicioCobranza.confirmarAnticipoCapital`/`confirmarCobro`/`confirmarCancelacionTotal`, todas
  llaman al mismo patrón `registrarMovimientosCobro` con `TipoMovimientoCaja.COBRO`).
- **Columna "Tipo" de la pantalla `/caja`**: `ServicioCaja.listarMovimientosConComprobante`
  (línea ~462) construye la etiqueta con `movimiento.getTipo().getEtiqueta()` — es decir,
  **directamente** la etiqueta genérica del enum ("Cobro"), sin distinguir si el pago detrás es
  `NORMAL`/`PARCIAL`/`ANTICIPO`/`CANCELACION`. Por eso la fila de hoy dice "Cobro" (con la
  observación aclarando "Anticipo de capital de CC-2026-00002." como único indicio en pantalla).
- **Línea del RESUMEN del arqueo donde suma**: en cambio, `ServicioCaja.generarPdfArqueo`
  (líneas ~606-639) **sí** distingue: para cada movimiento con `referencia_tipo = 'PAGO'`, busca
  el `Pago` enlazado y, si `pago.getTipo() == TipoPago.ANTICIPO`, lo clasifica como **"Anticipo
  cap."** (contador `CANT_ANTICIPOS`/`MONTO_ANTICIPOS`) — no como "Cobro". El PDF del arqueo
  (`arqueo_caja.jrxml`) imprime esto en renglones separados del resumen: **"(+) Cobros de cuotas
  (N)"** y **"(+) Anticipos de capital (N)"**. Es decir: hoy, al cerrar la caja, el anticipo de
  193.250 Gs. **no** va a sumar en "Cobros de cuotas" — va a aparecer en su propio renglón
  "Anticipos de capital (1)".
- **"Reimprimir" del recibo**: la URL es la misma que "Imprimir recibo"/"Recibo" en la ficha del
  préstamo (`/cobranza/{idPrestamo}/recibo/{idPago}`, `ServicioCobranza.obtenerPdfRecibo` →
  `obtenerOGenerarPdf`). Abre el **mismo PDF ya guardado** (`archivoComprobanteDAO`/
  `archivoBinarioDAO`, nunca se regenera salvo que el pago esté `REVERTIDO`). **No se audita**:
  revisado `obtenerOGenerarPdf` (líneas ~1780-1810), el `idUsuario` que recibe el método solo se
  usa para `archivo.setCargadoPor(idUsuario)` la primera vez que se genera el PDF — una
  reimpresión posterior (el PDF ya existe) no llama a `ServicioAuditoria.registrar` en ningún
  punto del camino. No queda registro de quién reimprimió ni cuándo.

## Hallazgos del Paso 5

- **B60**: la columna "Tipo" de la tabla de Movimientos en `/caja` muestra **"Cobro"** para un
  anticipo de capital (la cajera solo lo distingue por la observación en texto libre, "Anticipo
  de capital de..."), mientras que el **resumen del arqueo**, al cerrar la caja, sí separa
  "Cobros de cuotas" de "Anticipos de capital" en renglones distintos — dos pantallas del mismo
  sistema, sobre el mismo movimiento, con dos criterios de clasificación distintos (uno genérico
  por `TipoMovimientoCaja`, otro refinado por `pagos.tipo`). No es un error de cálculo (el dinero
  está bien sumado en ambos lados, y el arqueo sí sabe distinguirlo), pero puede confundir a la
  cajera si busca "los anticipos del día" en la tabla de movimientos y no los encuentra
  etiquetados como tales. Sugerencia: que la columna "Tipo" de `/caja` use la misma distinción
  que ya existe (y ya funciona) en el arqueo, en vez de la etiqueta genérica del movimiento.

## Paso 6 — Pago de más, con vuelto (RF22/RF27)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

- **Usuario/rol**: Cajera.
- **Contexto**: nueva sesión de caja (**N.º 4**, abierta 13:22), con un fondo inicial de
  **100.000 Gs.** — cambio entregado por el gerente para esta prueba (el "Fondo sugerido" de
  apertura, en cambio, propuso 0 Gs. — ver B67 más abajo).
- **Qué hace el usuario**: busca `CC-2026-00002` por cédula (`4512789`) — fila **"1/11 — vence
  23/11/2026"**, **"Monto a cobrar 192.891 Gs."** → **"Cobrar"** → ficha: historial con el
  primer anticipo (11:30, con "Recibo" en Comprobantes); tarjeta **"Cobranza"** con **"Adelantar
  a capital 192.891 Gs. — en efectivo: 192.850 Gs."** y **"Cancelar todo 1.806.750 Gs."**;
  selector en **"1 cuota"**; *"Si adelanta, su próxima cuota será el 23/11/2026 por 192.503
  Gs., quedan 10 cuotas."*
- La cajera escribe en **"Recibido"**: **200.000** ("doscientos mil guaraníes") — en vivo,
  **"Vuelto: 7.150 Gs."**; al abrir "Ver detalle del adelanto": *"Interés devengado hoy: 0 Gs. +
  IVA 0 Gs. Capital aplicado: 192.850 Gs. Saldo que queda: 1.613.900 Gs."*
- **Confirmación** ("Confirmar: Adelantar a capital"): **Adelantar a capital 192.850 Gs.**;
  **Recibido 200.000 Gs.**; **Vuelto 7.150 Gs.**; el desglose completo; **Próxima cuota
  23/11/2026 por 192.503 Gs.**; **Cuotas que quedan 10**.
- **Éxito**: **"Anticipo de capital aplicado (192.850 Gs.) — Vuelto: 7.150 Gs. Recibo
  REC-2026-000002. Sin factura (no hay conceptos gravados)."**
- **La ficha vuelve a mostrar**: historial con **2 anticipos** (13:23 y 11:30); **"Cancelar
  todo 1.613.900 Gs."**; nuevo adelanto propuesto **192.503 Gs. (efectivo 192.500 Gs.)**, **9
  cuotas** (si se volviera a adelantar).
- **Regla de negocio**: ver [RF22_cobro_efectivo.md](../funcionamiento/RF22_cobro_efectivo.md) y
  [RF27_adelantar_capital.md](../funcionamiento/RF27_adelantar_capital.md).
- **Hallazgos**: ver C28, B67 y la repetición de C23, más abajo.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`pagos`** (id 2): `numero_recibo` `REC-2026-000002`, `monto_recibido` = 200.000,
  `monto_vuelto` = 7.150, `monto_aplicado` = 192.850, `monto_redondeo` = 0 (sigue sin aplicar
  para `ANTICIPO`), `id_sesion_caja` = 4.
- **`movimientos_caja`** de la sesión N.º 4: **dos filas separadas, no un movimiento neto** —
  `COBRO` **+200.000** (signo +1, lo recibido completo) y `VUELTO` **−7.150** (signo −1), ambas
  con `referencia_tipo = PAGO` apuntando al mismo pago 2. El dinero entra completo a la caja; el
  vuelto sale como su propio movimiento, nunca como un ajuste del `COBRO`.
- **Saldo teórico resultante de la sesión 4**: `100.000` (fondo) `+ 200.000` (COBRO) `− 7.150`
  (VUELTO) = **292.850 Gs.**
- **En qué líneas del arqueo va a aparecer**: el `COBRO` (enlazado a `pagos.tipo = ANTICIPO`)
  suma en **"Anticipos de capital"**; el `VUELTO` suma por separado en **"Vueltos entregados"**
  — dos renglones distintos del RESUMEN, mismo criterio ya confirmado para la sesión N.º 3 en
  `07_cierre_del_dia.md`.
- **El recibo sí muestra recibido/vuelto/aplicado**: confirmado en pantalla — *"Efectivo
  recibido: 200.000 Gs. Vuelto: 7.150 Gs. Aplicado: 192.850 Gs."* (ver el caso completo en
  `06_comprobantes_recibo_factura.md` § 7, "Segundo ejemplo real").

### Verificado en el código: ¿el monto aplicado es libre?

**No — está limitado al valor del selector "1/2/3 cuotas", nunca a un monto intermedio
elegido por la cajera.** `ControladorCobranzaServlet.procesarAnticipar`:

```java
BigDecimal montoAplicado = montoRecibido;
if (montoSugeridoTexto != null && !montoSugeridoTexto.trim().isEmpty()) {
    montoAplicado = montoRecibido.min(parsearMonto(montoSugeridoTexto));
}
```

`montoSugerido` es siempre uno de los tres valores fijos (1×, 2× o 3× la próxima cuota, ya
redondeada) que ofrece el selector (ver Paso 2, "Verificado en el código"). El monto aplicado es
**el menor entre lo recibido y ese valor fijo** — en este caso, `min(200.000, 192.850) =
192.850`, con el resto como vuelto. **Si "Recibido" es menor que el monto sugerido**: se aplica
exactamente lo recibido (el menor de los dos), sin vuelto — salvo que ni eso alcance para cubrir
el interés + IVA del período en curso, caso en el que `confirmarAnticipoCapital` rechaza con
*"El monto no alcanza a cubrir siquiera el interés del período en curso (...)."* (D-90/D-88, ya
citado en el mapa completo). **No existe ningún camino para aplicar un monto libre** distinto de
los tres botones, ni ningún concepto de "saldo a favor" del cliente para un pago futuro
(revisado `bd/01_esquema.sql`: ninguna tabla ni columna lo contempla) — lo único que puede
volver es efectivo, como vuelto, en el momento.

### El recibo impreso

Al tocar "Imprimir recibo" desde el mensaje de éxito, se abre un PDF de una sola página,
**"RECIBO DE DINERO N.º REC-2026-000002"**:

- "Asunción, 23/10/2026 13:23"; "Operación: [identificador]"; "Cliente: [nombre] C.I.:
  [cédula]".
- **"Factura asociada: no corresponde (sin conceptos gravados)."**
- "Anticipo de capital: 192.850 Gs. – Interés del período en curso: 0 Gs. – IVA: 0 Gs."
- "Resumen del plan regenerado: **10 cuotas restantes, nuevo último vencimiento 23/08/2027**.
  Próxima cuota (IVA incluido): 23/11/2026 por 192.503 Gs. Saldo de capital después del pago:
  1.613.900 Gs."
- "Efectivo recibido: 200.000 Gs. Vuelto: 7.150 Gs. Aplicado: 192.850 Gs."
- **"Son:" en letras, por el monto APLICADO (192.850 Gs.), no por lo recibido (200.000 Gs.)** —
  mismo criterio que el primer recibo (donde recibido y aplicado coincidían, sin que se notara
  la diferencia); acá, con un vuelto real de por medio, queda claro que "Son:" describe lo que
  de verdad bajó la deuda, no el efectivo que pasó por el mostrador.
- Firmas de la cajera (con nombre) y del cliente.
- "ORIGINAL: Cliente                    COPIA: Capital Crédito".
- "Emitido por: [usuario] — [fecha de emisión]" y "Página 1 de 1".

**Dato a anotar — el "último vencimiento" retrocede un mes por cada anticipo de una cuota,
mientras la próxima cuota sigue en la misma fecha**: comparado con el primer recibo
(`REC-2026-000001`, "nuevo último vencimiento **23/09/2027**", 11 cuotas), este segundo recibo
dice "nuevo último vencimiento **23/08/2027**", 10 cuotas — un mes antes, una cuota menos. En
los dos casos, la **próxima cuota sigue siendo el 23/11/2026** (ver C23): adelantar un mes de
capital nunca mueve la próxima cuota hacia adelante, siempre recorta el final del plan en un
mes. Con dos anticipos consecutivos de "1 cuota" cada uno, el plan ya perdió 2 cuotas del final
(de 12 a 10) sin que la fecha de la próxima cuota se haya movido ni un día.

## Hallazgos del Paso 6

- **C28 (para el rediseño del cobro, sin clasificar A/B)** — El monto que de verdad se aplica a
  capital en un adelanto **no es libre**: queda limitado a uno de los tres valores fijos del
  selector "1/2/3 cuotas" (siempre múltiplos de la misma próxima cuota, nunca una suma
  arbitraria). Con 200.000 Gs. recibidos, el sistema aplicó 192.850 Gs. (el tope de "1 cuota") y
  devolvió el resto como vuelto — no hubo manera de, por ejemplo, aplicar los 200.000 Gs.
  enteros a capital, ni de aplicar un monto intermedio exacto. **Verificado**: tampoco existe
  ninguna alternativa de **saldo a favor** (un crédito que el cliente pudiera usar en el próximo
  cobro en vez de recibir el vuelto en efectivo) — el esquema de base de datos no tiene ninguna
  tabla para eso. Queda documentada esta limitación para que Joel o el dueño de Capital Crédito
  la tengan presente al rediseñar la metodología de cobro en la fase 2: si el negocio quisiera
  permitir aplicar un monto libre a capital, o dejar un saldo a favor en vez de devolver
  efectivo, ninguno de los dos caminos existe hoy.
- **B67 (menor)** — El **"Fondo sugerido"** de la tarjeta de apertura de caja solo suma los
  préstamos listos para desembolsar (`ServicioCaja.calcularFondoSugerido`) — **no contempla el
  cambio que la cajera podría necesitar para dar vuelto** en un cobro o un adelanto. Hoy esa
  sugerencia propuso **0 Gs.** para la sesión N.º 4, aunque de hecho hiciera falta efectivo
  suelto (100.000 Gs., entregados por el gerente fuera de lo que el sistema sugirió) para poder
  devolver los 7.150 Gs. de vuelto de esta misma sesión. No es un error de cálculo (el fondo
  sugerido cumple lo que dice: cubrir los desembolsos pendientes), solo una limitación de alcance
  — sugerir que considere también algo de efectivo suelto para vueltos, o al menos lo mencione
  como un dato aparte al sugerir el fondo.
- **C23 (se repite, ver Paso 2)** — Con este segundo anticipo sobre el mismo préstamo, la
  próxima cuota **sigue venciendo el 23/11/2026** — la misma fecha de siempre, ahora por
  192.503 Gs. en vez de 192.891 Gs., con 9 cuotas en vez de 10. Confirma, con un segundo
  adelanto consecutivo, el mismo comportamiento ya documentado en C23: adelantar a capital nunca
  "tacha" la próxima cuota, solo recorta el final del plan, cuantas veces se repita la
  operación. **Confirmado también en el recibo impreso** (ver arriba): el "último vencimiento"
  retrocedió de **23/09/2027 a 23/08/2027** (un mes exacto, una cuota menos) entre el primer y
  el segundo anticipo, mientras la próxima cuota no se movió ni un día de su 23/11/2026 — el
  mismo choque de expectativa de C23, ahora visible en el propio papel que recibe el cliente,
  no solo en la pantalla.

## Paso 7 — El efecto en la caja del pago con vuelto (RF27)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

En `/caja`, después del pago con vuelto: sesión N.º 4, apertura 23/10/2026 13:22, fondo
**100.000 Gs.**, saldo teórico **292.850 Gs.**. Movimientos:

| Fecha/hora | Tipo | Monto | Observación | Comprobante |
|---|---|---|---|---|
| 13:22 | Apertura (fondo inicial) | +100.000 | — | — |
| 13:23 | **Cobro** | +200.000 | "Anticipo de capital de CC-2026-00002." | REC-2026-000002 (Reimprimir) |
| 13:23 | **Vuelto** | −7.150 | "Vuelto de CC-2026-00002." | REC-2026-000002 (Reimprimir) |

Formulario de "Cierre y rendición" con saldo teórico 292.850.

**Confirma en pantalla, exactamente, lo ya verificado con `SELECT` en el Paso 6**: dos
movimientos separados (`COBRO` +200.000 y `VUELTO` −7.150), nunca un movimiento neto de
192.850 — tal cual se había anticipado leyendo `movimientos_caja` directamente.

### Verificado en el código — B68

**El mismo recibo aparece con dos botones "Reimprimir" independientes, uno por cada
movimiento.** `ServicioCaja.listarMovimientosConComprobante` resuelve el comprobante de
**cada** movimiento con `referencia_tipo = 'PAGO'` por separado — y como el `COBRO` y el
`VUELTO` de esta operación comparten el mismo `id_referencia` (el pago 2), los dos terminan
apuntando exactamente al mismo recibo:

```java
if ("PAGO".equals(movimiento.getReferenciaTipo())) {
    Pago pago = pagoDAO.obtenerPorId(conexion, movimiento.getIdReferencia());
    if (pago != null) {
        numeroComprobante = pago.getNumeroRecibo();
        urlReimprimir = "/cobranza/" + pago.getIdPrestamo() + "/recibo/" + pago.getIdPago();
    }
}
```

No hay ninguna condición que, para la fila `VUELTO`, omita el comprobante o lo marque como "ya
mostrado en la fila de arriba" — cada fila se resuelve de forma independiente, sin mirar las
demás filas de la misma sesión.

## Hallazgos del Paso 7

- **B68 (menor)** — La tabla de "Movimientos" de `/caja` muestra **dos filas con el mismo
  comprobante y el mismo botón "Reimprimir"** (una para el `COBRO`, otra para el `VUELTO`), para
  una sola operación de cobro que generó un solo recibo. No es un error de datos (el recibo
  `REC-2026-000002` es, efectivamente, el comprobante de las dos filas — ambas forman parte del
  mismo pago), pero puede confundir a la cajera, que ve dos oportunidades de reimprimir "algo"
  cuando en realidad es el mismo PDF las dos veces. Sugerencia: mostrar el botón "Reimprimir"
  solo en la primera fila de cada comprobante (o fusionar visualmente las filas de un mismo
  pago), no en cada movimiento de caja que ese pago generó.

## Paso 8 — Solicitar la reversión de un cobro, paso 1: la cajera (RF25)

**Registro de lo observado (23/10/2026, segundo cliente de práctica).**

- **Usuario/rol**: Cajera.
- **Qué responde el sistema (ficha `/cobranza/{id}`)**: el **Historial** muestra los dos
  anticipos — **13:23, `REC-2026-000002`, 192.850 Gs.**, con el campo **"Motivo (mínimo 10
  caracteres)"** y el botón rojo **"Solicitar reversión"**; y **11:30, `REC-2026-000001`,
  193.250 Gs., SIN ninguna opción de reversión** — antes, cuando era el único cobro, sí la
  tenía (ver B57, ya documentado: solo la operación más reciente la ofrece).
- **Qué hace el usuario**: escribe el motivo *"Error de la cajera: se registró un anticipo no
  solicitado por el cliente."* y toca **"Solicitar reversión"**.
- **Confirmación**: **"Confirmar: Solicitar reversión — ¿Solicitar la reversión del cobro
  REC-2026-000002 por 192.850 Gs.?"** (**"Sí, solicitar"**/**"Cancelar"**).
- **Éxito**: **"Solicitud de reversión enviada — Queda pendiente de autorización del
  gerente."**
- **La fila cambia**: pasa a **"Reversión solicitada — pendiente de autorización"** (en
  naranja), en el mismo lugar donde antes estaba el formulario.
- **La ficha sigue mostrando**, sin ningún aviso ni bloqueo, **"Adelantar a capital
  192.503 Gs."** y **"Cancelar todo 1.613.900 Gs."** — montos que ya descuentan el cobro cuya
  reversión está pendiente.
- **Regla de negocio**: ver [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md).
- **Qué sigue**: la autorización o el rechazo del gerente — queda para un próximo registro.
- **Hallazgos**: ver C29, C30 y B69 más abajo.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`reversiones_pago`** (id 1): `id_pago` = 2, `motivo_reversion` = el texto exacto de la
  cajera, **`estado = 'SOLICITADA'`**, `solicitado_por` = la cajera, `fecha_hora_solicitud`
  23/10/2026 14:07:17 — `autorizado_por`/`procesado_por`/`fecha_hora_reversion` todos **vacíos**
  (nadie los llena hasta que el gerente decida). `pagos.estado_pago` del pago 2 **sigue
  `CONFIRMADO`** — una solicitud pendiente no cambia el estado del pago, solo cuando se autoriza
  pasa a `REVERTIDO`.
- **Auditoría**: un único evento `SOLICITAR_REVERSION` (entidad `PAGO`, id 2), detalle: *"Solicitó
  revertir REC-2026-000002: Error de la cajera: se registró un anticipo no solicitado por el
  cliente."*
- **Cómo se entera el gerente**: `ServicioPanel.calcularIndicadores` (para el perfil gerente) sí
  llama a `ServicioReversiones.listarSolicitadas` y lo muestra como **"Reversiones por autorizar
  (N)"** en su propio panel — con esta solicitud recién creada, ese número sube de 0 a 1 sin que
  nadie tenga que avisarle por fuera del sistema. Contraste directo con **B63** (diferencias de
  caja sin revisar, que NO llegan al panel): este indicador de reversiones **ya está
  implementado correctamente**, mismo principio de paneles ya documentado en `00_LEEME.md`.

### Verificado en el código: la regla exacta de qué cobros se pueden revertir — confirma C29

**Solo el pago más reciente, todavía `CONFIRMADO`, del préstamo — sin límite de tiempo, sin
exigir la misma caja/sesión, sin exigir que esa caja siga abierta.** `ServicioReversiones.
esLaUltimaOperacion`:

```java
private boolean esLaUltimaOperacion(Connection conexion, Pago pago) throws SQLException {
    List<Pago> pagosPrestamo = pagoDAO.listarPorPrestamo(conexion, pago.getIdPrestamo());
    return !pagosPrestamo.isEmpty() && pagosPrestamo.get(0).getIdPago().equals(pago.getIdPago());
}
```

`PagoDAO.listarPorPrestamo` filtra **`WHERE estado_pago = 'CONFIRMADO'`** (excluye los ya
`REVERTIDO`) y ordena por `fecha_hora_pago DESC` — el pago en la posición `[0]` es, literalmente,
el último cobro vigente del préstamo, sin ninguna otra condición (ni fecha límite, ni
`id_sesion_caja`, ni si esa sesión sigue `ABIERTA` o ya `CERRADA`). Esto se revalida dos veces:
al **solicitar** (`validarPuedeSolicitarReversion`) y de nuevo, por separado, al **autorizar**
(`autorizarReversion`, con el mensaje exacto *"Ya se registró una operación posterior en este
préstamo; no se puede autorizar esta reversión."* si algo cambió entre medio).

**Cómo se revertiría un cobro anterior (ej. `REC-2026-000001`, el primer anticipo)**: no existe
un camino directo — hay que revertir primero el/los cobros más recientes, **uno por uno, en
orden**. Al autorizarse la reversión de `REC-2026-000002`, ese pago pasa a `REVERTIDO` y sale
del filtro `estado_pago = 'CONFIRMADO'` — recién en ese momento `REC-2026-000001` vuelve a ser
"el último pago vigente" del préstamo y recién ahí la ficha le ofrecería su propio botón
"Solicitar reversión". No hay ninguna forma de revertir el primero mientras el segundo siga
`CONFIRMADO`.

### Verificado en el código: cancelar la propia solicitud, operaciones nuevas y qué pasaría si se aprueba después — confirma/responde C30

- **¿La cajera puede cancelar su propia solicitud?** **No.** Revisado `ServicioReversiones`
  completo: los únicos métodos que tocan una `ReversionPago` ya creada son
  `rechazarReversion` y `autorizarReversion`, **ambos exclusivos del gerente**
  (`Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA`). No existe ningún método "cancelar"/"retirar" la
  solicitud. Una vez enviada, la cajera no tiene ningún camino para deshacerla — solo puede
  esperar a que el gerente la autorice o la rechace.
- **¿Se permiten nuevos cobros o la cancelación total mientras hay una reversión pendiente?**
  **Sí, sin ningún bloqueo.** Revisados `confirmarCobro`/`confirmarAnticipoCapital`/
  `confirmarCancelacionTotal` completos: **ninguno de los tres consulta `reversiones_pago` ni
  `EstadoReversion` en absoluto** — nada les impide confirmar una operación nueva sobre un saldo
  que todavía incluye el cobro cuya reversión está pendiente.
- **¿Qué pasaría si, después de una operación nueva, el gerente aprobara la reversión
  pendiente?** El sistema sí tiene una red de seguridad, pero **en el momento de autorizar, no
  antes**: `autorizarReversion` repite `esLaUltimaOperacion` sobre el pago a revertir, y si para
  entonces ya existe un pago más reciente y `CONFIRMADO` en el préstamo (por ejemplo, un tercer
  anticipo confirmado mientras la reversión del segundo seguía pendiente), **rechaza la
  autorización completa** con *"Ya se registró una operación posterior en este préstamo; no se
  puede autorizar esta reversión."* — nunca llega a deshacer el segundo anticipo y dejar el
  saldo/plan en un estado inconsistente con una operación más nueva ya confirmada encima. La
  única salida, en ese caso, es que el gerente **rechace** la solicitud (no hay forma de
  "aprobarla a medias" ni de reordenar las operaciones).
- **Conclusión: riesgo parcial.** La integridad del saldo y del plan está **cubierta** en el
  momento decisivo (el gerente nunca puede autorizar una reversión que dejaría el sistema
  inconsistente con una operación posterior ya confirmada) — pero la **experiencia** no está
  cubierta: ni la ficha avisa a la cajera que hay una reversión pendiente antes de dejarla
  adelantar capital o cancelar todo sobre ese mismo préstamo, ni el gerente ve, al revisar la
  bandeja de reversiones, que ya existe una operación posterior que va a hacer fallar la
  autorización — se entera recién al intentarlo, con el rechazo automático como único resultado
  posible en ese momento (y la solicitud original, mientras tanto, solo se puede rechazar, nunca
  autorizar).

## Hallazgos del Paso 8

- **C29 (ver desarrollo completo arriba)** — Confirmado: solo el último cobro vigente de un
  préstamo ofrece "Solicitar reversión" — no hay límite de tiempo ni requisito de sesión de
  caja, solo la posición en el orden de pagos `CONFIRMADO`. Para revertir uno anterior, hace
  falta revertir primero todos los posteriores, uno por uno, en orden estricto. Sin clasificar
  A/B — queda documentada la regla exacta para que Joel o el dueño de Capital Crédito decidan si
  alcanza o si conviene un camino más directo.
- **C30 (ver desarrollo completo arriba) — Concluido: riesgo parcial.** Con una reversión
  pendiente, la ficha sigue permitiendo "Adelantar a capital" y "Cancelar todo" sin ningún aviso
  ni bloqueo; la integridad del dato queda protegida recién al momento de autorizar (rechazo
  automático si ya hay una operación posterior), nunca antes — ni la cajera ni el gerente tienen
  ninguna advertencia previa de este riesgo mientras deciden.
  **RESUELTO el 03/11/2026 (decisión de Joel: avisar, no bloquear).** Dueño del detalle:
  `funcionamiento/RF25_reversion_cobro.md` §2.

**Lo que vi en pantalla el 03/11/2026 08:05-08:18 (tanda 3, C30)** — Sofía (cajera) y Ramón
(gerente), caja del 03/11 abierta con fondo 0, sobre `CC-2026-00005`:

- **Antes de solicitar nada**: el cartel de *"Adelantar a capital"* (1 cuota, 242.000) salió
  **sin** recuadro amarillo. Era el control que había que hacer primero: un aviso que aparece
  siempre no avisa nada.
- **Sofía solicitó la reversión de `REC-2026-000014`** (*"Prueba del aviso C30"*) → *"Solicitud
  de reversión enviada / Queda pendiente de autorización del gerente."*, y el historial pasó a
  *"Reversión solicitada — pendiente de autorización"*.
- **Con la solicitud pendiente**, los carteles de *"Adelantar a capital"* (242.000) y de *"Todo
  el préstamo"* (2.557.800) cerraron con el recuadro amarillo nombrando `REC-2026-000014` y la
  consecuencia (*"…esa reversión ya no podrá autorizarse: el gerente solo podrá rechazarla."*).
  **Los dos se cancelaron**: la prueba no gastó ni un recibo ni una factura.
- **Ramón**: panel *"1 Reversión por autorizar"* → *"Ir a Reversiones →"*; la bandeja mostró la
  solicitud **sin** marca amarilla (correcto: no había operación posterior) y la **rechazó** con
  *"Prueba C30 terminada, sin error real"*. `CC-2026-00005` quedó como estaba.
- **No se vio en pantalla**: la marca *"Ya hay una operación posterior…"* (habría costado un
  recibo y una factura) y el aviso en *"Pagar la cuota"* (no había cuota del día). Las dos
  quedan cubiertas por JUnit.
- **B69 (menor)** — El Historial **no muestra el motivo de la reversión solicitada** —
  verificado en `cobranza/ficha.jsp` (línea ~183): mientras el estado es `SOLICITADA`, solo
  imprime el texto fijo *"Reversión solicitada — pendiente de autorización"*, sin el motivo que
  la cajera escribió (que sí quedó guardado, en `reversiones_pago.motivo_reversion` y en la
  auditoría — el dato existe, solo no se muestra ahí). Sugerencia: mostrar el motivo junto al
  aviso, útil tanto para la propia cajera como para cualquiera que revise la ficha mientras la
  solicitud sigue pendiente.

## Paso 9 — El panel del gerente con la reversión pendiente (RF25/RF30)

**Registro de lo observado (23/10/2026, 14:28).**

Con la solicitud de reversión de `REC-2026-000002` todavía `SOLICITADA`, el panel del gerente
mostró:

- **Hoy**: Cobros de hoy **2 (386.100 Gs.)**; Promesas de hoy 0; **Efectivo en caja
  292.850 Gs.**; Solicitudes pendientes de aprobar 0.
- **Cartera**: Préstamos en mora 0; Cartera en la calle **4.613.900 Gs.** (3.000.000 de
  `CC-2026-00001` + 1.613.900 de `CC-2026-00002`); Cobrado esta semana 386.100; Cobrado este mes
  386.100.
- **Pendientes**: préstamos pendientes de desembolso 0; **"1 Reversiones por autorizar"**, con
  el enlace **"Ir a Reversiones"**.

**Confirma en pantalla lo ya verificado en el código** (Paso 8: `ServicioPanel.
calcularIndicadores` sí llama a `ServicioReversiones.listarSolicitadas`) — el panel **avisa**
la reversión pendiente, a diferencia de **B63** (diferencias de caja sin revisar, que el panel
del gerente no muestra en absoluto). Mismo principio de paneles, dos resultados distintos
dentro del mismo sistema: acá sí está implementado.

**Dato a tener presente**: todos los indicadores de "Hoy"/"Cartera" (Cobros de hoy, Efectivo en
caja, Cartera en la calle, Cobrado esta semana/mes) **todavía incluyen el cobro de
192.850 Gs. cuya reversión está pendiente** — ninguno de los dos cálculos (`Cobros de hoy`,
`Cartera en la calle`, etc.) resta ni separa el monto en reversión mientras la solicitud sigue
`SOLICITADA`. Es coherente con lo ya verificado en el Paso 8 (el pago sigue `estado_pago =
'CONFIRMADO'` hasta que el gerente decida) — no es un error, es el estado real del sistema en
este momento.

**Verificación pendiente para el paso siguiente** (**cerrada** — ver Paso 12 más abajo): una
vez que el gerente autorice (o rechace) esta reversión, confirmar si **"Cobros de hoy"**,
**"Efectivo en caja"**, **"Cartera en la calle"** y **"Cobrado esta semana/mes"** se ajustan
para sacar el cobro revertido, o si alguno de ellos queda desactualizado (por ejemplo, si solo
se recalcula al recargar la página, o si algún indicador sigue sin descontar el monto). **Sí se
ajustan, confirmado en pantalla en el panel de la cajera** (Paso 12): "Cobros de hoy" bajó de 2
a 1 y "Efectivo en caja" bajó de 292.850 a 100.000, exactamente.

## Paso 10 — La pantalla de Reversiones: pendientes, Autorizar, Rechazar e Historial (RF25)

**Registro de lo observado (23/10/2026, 14:34).**

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: desde el panel, **"Ir a Reversiones"** → menú **"Cobranza y mora"** →
  **"Reversiones"** (`/reversiones`).
- **Qué responde el sistema**: título **"Reversiones"**, con el texto *"Solicitudes de
  reversión de cobro pendientes de autorizar. Solo se puede revertir la operación más reciente
  de cada préstamo, y hace falta una caja abierta hoy con efectivo suficiente."* Pestañas
  **"Pendientes"** / **"Historial"**. Tarjeta **"Pendientes de autorizar"**, columnas
  **Solicitada, Cajera, Operación, Recibo, Monto, Motivo, Acción**; una fila: *23/10/2026 14:07,
  [cajera], `CC-2026-00002`, `REC-2026-000002`, 192.850 Gs., el motivo completo de la cajera*;
  botón verde **"Autorizar"** y, al lado, un campo **"Motivo del rechazo"** con el botón
  **"Rechazar"**.
- **Regla de negocio**: ver [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md).
- **Hallazgos**: ver C29 (actualización), B70 y C31 más abajo.

### Verificado en el código

- **"La regla ya está explicada en la propia pantalla" — confirma C29**: el texto fijo de
  arriba de la bandeja (`reversiones/bandeja.jsp`, línea ~31) dice, literalmente, *"Solo se
  puede revertir la operación más reciente de cada préstamo, y hace falta una caja abierta hoy
  con efectivo suficiente."* — la misma regla que C29 ya había encontrado leyendo
  `esLaUltimaOperacion`, puesta en palabras simples, a la vista de cualquier gerente antes de
  decidir nada.
- **Qué hace "Autorizar"** (`ServicioReversiones.autorizarReversion`, orden real de la
  transacción):
  1. Revalida que la solicitud siga `SOLICITADA` y que el pago **siga siendo la operación más
     reciente** del préstamo (si no, rechaza con el mismo mensaje ya visto en el Paso 8).
  2. Si el pago tenía factura, exige la casilla **"Factura en poder del cliente"** tildada (no
     aplica en este caso — "Sin factura").
  3. Exige una **caja `ABIERTA`, de hoy**, con `saldoTeorico >= pago.getMontoAplicado()` — si no
     hay caja abierta: *"No hay una caja abierta."*; si es de otro día: el mismo aviso ya
     conocido de "caja de otro día"; si no alcanza el efectivo: *"La caja no tiene efectivo
     suficiente para devolver [monto] (saldo actual [monto])."*
  4. **Restaura cada cuota que el pago haya tocado** — inversa exacta del motor financiero
     (aplica solo a `NORMAL`/`PARCIAL`/`CANCELACION`, que tocan filas `cuotas`; un `ANTICIPO` no
     toca ninguna cuota puntual).
  5. **Para un `ANTICIPO`** (el caso de hoy): el **plan nuevo** que generó el anticipo
     (`origen_plan = 'ANTICIPO_PARCIAL'`) se marca `no vigente`; el **plan anterior** (la
     versión inmediatamente de antes) vuelve a `vigente` — el plan **se restaura exactamente al
     que había antes**, no se regenera uno nuevo desde cero.
  6. Recalcula el estado del préstamo (nunca "desrevierte" un `FINALIZADO` directo — usa
     `calcularEstadoSegunCuotas`, no el método genérico).
  7. Si había factura, la anula y emite la **Nota de anulación** (igual que ya documentado en
     `06_comprobantes_recibo_factura.md` § 3).
  8. Inserta el movimiento **`COMPENSACION`**, signo −1 — **ver C31 más abajo para el monto
     exacto**.
  9. `pagos.estado_pago → REVERTIDO`; `reversiones_pago.estado → AUTORIZADA`.
  10. Auditoría **`AUTORIZAR_REVERSION`** (entidad PAGO).
  - **¿Pide confirmación?** **Sí** — *"¿Autorizar la reversión del cobro [recibo] por [monto]?
    No se puede deshacer."* (**"Sí, autorizar"**/**"Cancelar"**, foco por defecto en
    "Cancelar").
  - **¿Exige algún comentario del gerente?** **No** — el método no recibe ningún parámetro de
    motivo/observación; solo la casilla de la factura, cuando corresponde. Ver **B70**.
- **Qué hace "Rechazar"** (`ServicioReversiones.rechazarReversion`): exige un **"Motivo del
  rechazo"** no vacío (server: *"Ingrese el motivo del rechazo."* si viene vacío — sin un
  mínimo de caracteres, a diferencia del motivo de la cajera al solicitar, que sí exige 10).
  También pide confirmación (*"¿Rechazar la reversión del cobro [recibo] por [monto]?"*). Marca
  `reversiones_pago.estado → RECHAZADA`, guarda el motivo, audita `RECHAZAR_REVERSION` — **no
  toca el pago, la caja ni el plan** (el pago sigue `CONFIRMADO` tal cual estaba). La cajera
  puede volver a solicitar la reversión del mismo pago después (con un motivo nuevo — la misma
  fila se reutiliza, `id_pago` es `UNIQUE`).
- **Qué muestra "Historial"**: ambos estados, **autorizadas y rechazadas**, con columnas
  **Fecha de la solicitud, Operación, Cliente, Monto, Motivo de la solicitud, Solicitó, Estado,
  Fecha de la decisión, Motivo de la decisión, Autorizó/Rechazó, Comprobante**. La columna
  "Motivo de la decisión" solo se llena para **Rechazada** (`reversion.getMotivoRechazo()`); para
  **Autorizada** queda **"-"** — no hay ningún motivo del gerente que mostrar, porque nunca se
  pidió uno (mismo hallazgo, B70). La columna "Comprobante" solo ofrece el enlace a la
  **Constancia de reversión** cuando el estado es Autorizada (no existe comprobante de un
  rechazo).

### Verificado en el código: cuánto efectivo se devuelve si el cobro tuvo vuelto — responde C31

**Se devuelve exactamente el monto `aplicado` (192.850 Gs.), no el `recibido` (200.000 Gs.), y
no hay ningún movimiento separado que "revierta" el vuelto.** El movimiento `COMPENSACION`:

```java
compensacion.setMonto(pago.getMontoAplicado());  // 192.850, no 200.000
compensacion.setSigno(-1);
```

**Por qué es, igual, matemáticamente correcto** — reconstruyendo el efecto neto en la caja de
la sesión N.º 4: Apertura +100.000, Cobro +200.000, Vuelto −7.150 → saldo teórico antes de la
reversión: **292.850 Gs.** Con la `COMPENSACION` de −192.850: **292.850 − 192.850 = 100.000
Gs.** — exactamente el fondo inicial de la sesión, **como si el anticipo nunca hubiera
existido**. La razón: el vuelto de 7.150 Gs. ya salió físicamente de la caja en el momento del
cobro original — el cliente nunca estuvo "a deber" esos 7.150 Gs., así que no hace falta
devolvérselos de nuevo al revertir. Lo único que el cliente efectivamente adelantó a capital
fueron los 192.850 Gs. (el `aplicado`), y es exactamente ese monto el que vuelve a sus manos
(en efectivo, de la caja) cuando se revierte. **Conclusión**: el mecanismo es coherente — no es
un error ni un faltante, es la cuenta correcta — pero vale la pena que la cajera lo tenga
presente al entregar el efectivo de una reversión con vuelto: **lo que corresponde entregar de
vuelta es el aplicado, no lo que el cliente recibió como vuelto en su momento, ni lo que
originalmente entregó**.

## Hallazgos del Paso 10

- **C29 (actualización)** — La regla "solo se puede revertir la operación más reciente de cada
  préstamo" (ya documentada en el código, Paso 8) **también está escrita, en lenguaje simple,
  en la propia pantalla de `/reversiones`** — el gerente no depende de adivinarla ni de leer el
  código: el texto de ayuda de la bandeja ya la explica, junto con el requisito de caja abierta
  con efectivo suficiente.
- **B70 (menor)** — **"Autorizar" no exige ningún comentario del gerente** — a diferencia de
  "Rechazar", que sí pide un motivo (aunque sin mínimo de caracteres). La decisión de autorizar
  una reversión (revertir cuotas, plan, caja y, si corresponde, anular una factura) queda sin
  ningún registro de "por qué" más allá del propio hecho de haber tocado el botón — ni en
  pantalla, ni en el Historial (columna "Motivo de la decisión" vacía para las autorizadas), ni
  en la auditoría (el detalle de `AUTORIZAR_REVERSION` repite el monto y la operación, no un
  motivo propio del gerente). Sugerencia: agregar un campo de observación opcional u obligatorio
  al autorizar, igual que ya existe al rechazar.
- **C31 (ver desarrollo completo arriba)** — Verificado: al revertir un cobro que tuvo vuelto,
  la caja recibe una `COMPENSACION` por el monto **aplicado** (192.850 Gs.), no por lo
  **recibido** (200.000 Gs.) — matemáticamente correcto, porque el vuelto ya había salido de la
  caja en el momento del cobro original; el efecto neto deja la caja exactamente como estaba
  antes del cobro. Sin clasificar A/B — queda documentado el mecanismo exacto, y la
  recomendación de que la cajera entienda que el monto a devolver en efectivo es el aplicado,
  no el recibido original.

## Paso 11 — El gerente autoriza: pago revertido, plan restaurado y constancia (RF25)

**Registro de lo observado (23/10/2026, 14:42).**

- **Usuario/rol**: Gerente.
- **Qué hace el usuario**: toca **"Autorizar"** sobre la solicitud de `REC-2026-000002`.
- **Confirmación**: **"Confirmar: Autorizar reversión — ¿Autorizar la reversión del cobro
  REC-2026-000002 por 192.850 Gs.? No se puede deshacer."** (**"Sí, autorizar"**/**"Cancelar"**).
- **Éxito**: **"Reversión autorizada — Se devolvieron 192.850 Gs. de la caja."**, con
  **"Imprimir constancia de reversión"** y **"Aceptar"**.
- **La bandeja**: "Pendientes de autorizar" queda **"No hay solicitudes pendientes."**
- **Regla de negocio**: ver [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md).
- **Hallazgos**: ver B71 y C32 más abajo.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`pagos`** (id 2): `estado_pago` pasó de `CONFIRMADO` a **`REVERTIDO`**; `monto_aplicado`
  sigue en 192.850 (el dato histórico del pago no se borra ni se recalcula, solo cambia el
  estado).
- **`reversiones_pago`** (id 1): `estado = 'AUTORIZADA'`; `autorizado_por` = `procesado_por` =
  el gerente (id 1); `id_sesion_caja = 4` (la sesión abierta de la **cajera**, no una sesión del
  gerente); `monto_salida_caja = 192.850`; `fecha_hora_reversion` 23/10/2026 14:42:11;
  `factura_en_poder_confirmado = false` (correcto: este pago nunca tuvo factura).
- **`planes_pago` del préstamo** (id 2): el plan de la versión 3 (`ANTICIPO_PARCIAL`, el del
  segundo anticipo, 10 cuotas) pasó a `vigente = false`; el plan de la **versión 2**
  (`ANTICIPO_PARCIAL`, el del **primer** anticipo) volvió a `vigente = true`. **Confirma
  exactamente lo esperado**: ese plan tiene **11 cuotas**, cuota 1 con vencimiento 23/11/2026 y
  **`total_programado = 192.891`** — el plan quedó restaurado tal cual estaba antes del segundo
  anticipo, sin recalcular nada desde cero.
- **`movimientos_caja`** de la sesión N.º 4 (la de la cajera): la `COMPENSACION` (id 9) tiene
  `monto = 192.850`, `signo = -1`, `referencia_tipo = 'REVERSION_PAGO'`, y **`registrado_por =
  1` (el gerente)** — no la cajera, aunque el movimiento se inserta en **la sesión de caja
  abierta de la cajera** (id 4), la misma donde están sus propios `COBRO`/`VUELTO`
  (`registrado_por = 3`, la cajera). **Saldo teórico resultante**: `292.850 − 192.850 =
  100.000 Gs.` — exactamente el fondo inicial de la sesión, confirmando el cálculo del Paso 10.
- **Auditoría**: un único evento `AUTORIZAR_REVERSION` (entidad PAGO, id 2, usuario 1 — el
  gerente), detalle: *"Autorizó revertir REC-2026-000002 (192.850 Gs.) de CC-2026-00002."* —
  no menciona el plan restaurado ni el efecto en la caja de la cajera, solo el monto y la
  operación.
- **0 filas en `facturas`** (sin cambios) — confirma que no se generó ninguna Nota de
  anulación, correctamente, porque este pago nunca tuvo factura.

### Verificado en el código: el documento emitido y los nombres

**"Imprimir constancia de reversión" genera la "CONSTANCIA DE REVERSIÓN DE COBRO"
(`constancia_reversion.jrxml`, número `REV-2026-000001`) — un documento DISTINTO de la "Nota
de anulación" (`nota_anulacion.jrxml`, clave `ANULACION`), no el mismo documento con dos
nombres.** Verificado leyendo ambos `.jrxml`: el comentario propio de `constancia_reversion.
jrxml` lo dice explícito: *"Documento interno (no fiscal, a diferencia de nota_anulacion.
jrxml)"*. La diferencia real:

- **Constancia de reversión** (`REV-aaaa-NNNNNN`): se emite **siempre** que se autoriza una
  reversión, haya o no factura — es el comprobante de que el cobro fue anulado y el efectivo
  devuelto. Es este el documento del botón "Imprimir constancia de reversión" de hoy.
- **Nota de anulación** (`NAN-aaaa-NNNNNN`): se emite **solo si el pago original tenía
  factura** — anula específicamente esa factura ante la DNIT. Hoy no se generó ninguna (sin
  factura en este pago), consistente con las 0 filas en `facturas`.

**Verificación de coherencia de nombres, sin hallazgo**: revisada toda la documentación propia
de este recorrido (`06_comprobantes_recibo_factura.md` § 2/§ 3) — ya distingue correctamente
los dos documentos, con sus respectivas claves de numerador (`CONSTANCIA`/`ANULACION` no
aplican acá; la propia constancia de reversión no usa numerador, ver § 3) — no se encontró
ninguna confusión entre "constancia de reversión" (botón, pantalla, PDF) y "Nota de anulación"
(documento fiscal aparte); el código tampoco los confunde en ningún punto revisado.

### Verificado en el código: ¿la cajera recibe algún aviso? — responde C32

**No, ningún aviso dentro del sistema.** Revisado el proyecto completo (no existe ninguna clase
`ServicioNotificaciones` ni tabla de notificaciones — la única mención de "notificaciones" en
todo el código es la cláusula contractual DÉCIMA, sobre domicilio legal, sin relación con esto)
— el gerente autoriza desde `/reversiones`, una pantalla completamente separada de `/caja`, sin
ningún mensaje, correo ni aviso dirigido a la cajera en ese momento. La cajera solo se entera
si vuelve a entrar a `/caja`: ahí va a ver, en "Movimientos", una nueva fila
**"Compensación"** (por −192.850 Gs., con su propia "Constancia" para reimprimir) **sin que
nadie se lo haya avisado antes**. Su saldo teórico, para el cierre, ya va a venir con este
descuento incluido (el cálculo suma todos los movimientos de la sesión, sin importar quién los
registró) — así que, en los hechos, el libro y el efectivo físico deberían seguir coincidiendo
siempre que el efectivo también haya salido realmente de esa caja en el momento de la
autorización.

**Quién entrega físicamente el dinero al cliente**: **no está modelado en el sistema.** No hay
ningún paso, casilla ni confirmación (como la que sí existe para el desembolso, "Verifiqué la
cédula del cliente y le entregué...") que registre quién le devolvió el efectivo al cliente ni
que lo confirme. El `COMPENSACION` es un asiento contable automático en el momento de
autorizar, no la confirmación de una entrega física real.

### El documento impreso: Constancia de reversión (23/10/2026, 14:53)

**Registro de lo observado.** "Imprimir constancia de reversión" abre un PDF, **"CONSTANCIA DE
REVERSIÓN DE COBRO — N.º REV-2026-000001"**:

- Encabezado de la empresa (con letra visiblemente **más chica** que la de recibo/factura/
  comprobante de desembolso — ver B72).
- "Asunción, 23 de octubre de 2026".
- *"Por la presente, Capital Crédito Paraguay S.R.L. deja constancia de la devolución en
  efectivo a [cliente], C.I. N.º [...], por la reversión del cobro N.º REC-2026-000002 de la
  operación CC-2026-00002."*
- **"Son:"** en letras, por 192.850 Gs.
- **"DATOS DE LA REVERSIÓN"**: Recibo anulado `REC-2026-000002`; Monto devuelto en efectivo
  192.850 Gs.; Motivo de la solicitud; Solicitó (la cajera); Autorizó (el gerente); Fecha y
  hora de la autorización 23/10/2026 14:42.
- Leyenda: *"Adjuntar a esta constancia el recibo original y, si corresponde, el original y
  todas las copias de la factura anulada."*
- Firmas de **la cajera, el gerente y el cliente**, con el cliente firmando **"Recibí conforme
  la devolución de 192.850 Gs."**

**Verificado en el código** (parámetros de `constancia_reversion.jrxml`): coinciden
exactamente con lo impreso — `NUMERO_CONSTANCIA`, `FECHA_EMISION_LARGA`, `CLIENTE_NOMBRE`/
`CLIENTE_CEDULA`, `NUMERO_RECIBO`, `MONTO_DEVUELTO`/`MONTO_DEVUELTO_LETRAS`,
`MOTIVO_SOLICITUD`, `SOLICITADO_POR`/`AUTORIZADO_POR`, `FECHA_EMISION` (como "fecha y hora de
la autorización").

**La firma del cliente "Recibí conforme la devolución..." sí existe — actualiza C32 (ver
Hallazgos).**

**Verificado en el código — responde B72 (¿tiene pie "Emitido por"/"Página"?)**: **sí, los
tiene** — `constancia_reversion.jrxml` (líneas ~147-159) imprime, en el `pageFooter`,
*"Emitido por: [usuario] — [fecha]"* y *"Página [N] de [total]"*, el mismo patrón que
recibo/factura/comprobante de desembolso. **Lo que sí es distinto es el encabezado**: las 3
constancias (cancelación, reversión, devolución) comparten un subreporte propio
(`encabezado_constancia.jrxml`), cuyo bloque de datos de la empresa usa **letra tamaño 8**
(`<font ... size="8"/>`) — mientras que `recibo.jrxml`/`factura.jrxml`/`comprobante_
desembolso.jrxml` imprimen su propio encabezado de empresa en **tamaño 9**, directamente, sin
pasar por ese subreporte compartido. Es una diferencia real, de un punto, consistente en las 3
constancias (no exclusiva de la de reversión), pero sí distinta del resto de los comprobantes
del sistema.

## Hallazgos del Paso 11

- **B71 (menor)** — La confirmación de **"Autorizar reversión"** dice el recibo, el monto y
  **"No se puede deshacer"**, pero **no anticipa el efecto real** de la operación: ni que va a
  **salir efectivo de la caja de la cajera** (no de una caja del propio gerente), ni que el
  **plan de pagos se va a restaurar** a una versión anterior. El gerente confirma sabiendo el
  monto, pero no necesariamente todo lo que esa confirmación va a mover por detrás. Sugerencia:
  ampliar el texto de confirmación con, al menos, una línea sobre el efecto en caja y en el
  plan.
- **C32 (separación de funciones, para el rediseño del cobro) — Actualizado con el documento
  impreso (14:53): reclasificado de "riesgo parcial" a "cubierto en papel, riesgo solo dentro
  del sistema".** La Constancia de reversión **sí** deja constancia formal de la entrega
  física: lleva las firmas de la cajera, el gerente **y el cliente**, con el cliente firmando
  explícitamente *"Recibí conforme la devolución de 192.850 Gs."* — ese es, precisamente, el
  control de "quién entregó el efectivo" que antes de ver el PDF parecía no estar cubierto: está
  cubierto, pero **en papel, fuera del sistema**, no con ningún paso o casilla dentro de la
  pantalla de `/reversiones`. Lo que **sigue sin estar cubierto** es exclusivamente el aviso
  **dentro del sistema**: nada le informa a la cajera, en el momento de la autorización, que su
  caja ya fue descontada — ella se entera recién si vuelve a mirar `/caja`, y de ahí en más
  depende de la coordinación presencial con el gerente (quien, en los hechos, es quien reúne las
  tres firmas en el momento de la entrega). **Lo que está cubierto**: la separación de
  funciones en sí (solo el gerente puede autorizar, `Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA`),
  la integridad contable (el movimiento queda registrado, auditado y el saldo teórico se ajusta
  correctamente), y ahora también la constancia física de la entrega (la firma del cliente).
  **Lo que no está cubierto**: únicamente el aviso a la cajera en el momento, dentro del
  sistema — queda librado a la coordinación presencial (de palabra) entre gerente y cajera.
  Sin clasificar A/B — queda para que Joel o el dueño de Capital Crédito decidan si esto
  alcanza para la operación real o si conviene, además, un aviso explícito dentro del sistema,
  de cara al rediseño del cobro en la fase 2.
- **B72 (menor)** — La Constancia de reversión (y, por compartir el mismo subreporte de
  encabezado, también la constancia de cancelación y la de devolución del pagaré) imprime los
  datos de la empresa en **letra más chica** que el recibo, la factura y el comprobante de
  desembolso — verificado: `encabezado_constancia.jrxml` usa tamaño 8, esos otros tres
  documentos usan tamaño 9 para el mismo bloque. **Sí tiene** pie "Emitido por"/"Página X de
  Y" (verificado en `constancia_reversion.jrxml`, mismo patrón que el resto) — eso no falta.
  Sugerencia: unificar el tamaño de letra del encabezado de empresa en todos los comprobantes
  del sistema, ya que hoy las 3 constancias usan un tamaño distinto del resto sin que se
  encuentre una razón funcional para la diferencia.

## Paso 12 — El panel de la cajera después de la reversión autorizada (RF25/RF30)

**Registro de lo observado (23/10/2026, 14:57).**

Con la reversión ya autorizada, el panel de la cajera mostró:

- **"Cobros de hoy" 1 (193.250 Gs.)** — antes, 2 (386.100 Gs.): el cobro revertido ya no se
  cuenta.
- **"Efectivo en caja" 100.000 Gs.** — antes, 292.850 Gs.: se descontó la `COMPENSACION` de
  192.850 Gs.
- Préstamos en mora 0; préstamos pendientes de desembolso 0.

**Ningún aviso, en ningún lugar del panel, menciona que el gerente revirtió un cobro de su
caja** — los números simplemente cambiaron, sin ninguna explicación visible de por qué.

### Verificado en el código: cómo excluye `ServicioPanel` los pagos revertidos — no hay ninguna exclusión especial

**`ServicioPanel.calcularIndicadores` no tiene ningún código propio para "descontar" un pago
revertido — el ajuste es automático, heredado de cómo ya funcionan los métodos que calculan
cada indicador:**

- **"Cobros de hoy"**: `pagoDAO.contarConfirmadosEntreFechas`/`.sumarAplicadoEntreFechas` —
  ambos filtran `WHERE estado_pago = 'CONFIRMADO'` (ver Paso 1/Paso 11). En el momento en que
  `pagos.estado_pago` del pago 2 pasó a `REVERTIDO` (al autorizar, Paso 11), automáticamente
  deja de contar en cualquier consulta futura con ese filtro — sin que el panel tenga que
  "saber" que hubo una reversión.
- **"Efectivo en caja"**: `ServicioCaja.calcularSaldoTeorico` simplemente suma **todos** los
  movimientos de la sesión abierta (`fondo_inicial + Σ(monto × signo)`), sin filtrar por tipo ni
  por quién los registró. La `COMPENSACION` de −192.850 ya es, para este cálculo, un movimiento
  más de la sesión — se resta exactamente igual que cualquier otro.

**Conclusión**: no es un mecanismo diseñado específicamente para reversiones — es una
consecuencia natural de dos reglas ya simples (contar solo lo `CONFIRMADO`, sumar todos los
movimientos de la caja) que, combinadas, dan el resultado correcto sin ningún caso especial.

## Hallazgos del Paso 12

- **C32 (confirmado en pantalla, cajera)** — La cajera ve sus propios indicadores
  ("Cobros de hoy", "Efectivo en caja") cambiar de un momento a otro, **sin ningún aviso ni
  explicación** de que fue por una reversión que el gerente autorizó. Los números son
  correctos (ya verificado arriba: el ajuste es automático y preciso), pero, para la cajera,
  simplemente "cambiaron solos" — si no se enteró de la reversión por otro medio (mirando
  `/caja` o de palabra con el gerente), no tiene cómo saber, desde el panel, **por qué**
  cambiaron. Mismo hallazgo ya documentado en los Pasos 10-11 (riesgo solo dentro del sistema,
  cubierto en papel con la firma del cliente) — esta es su confirmación final, del lado de la
  cajera y en su propia pantalla.

## Paso 13 — `/caja` de la cajera después de la reversión (RF25/RF27)

**Registro de lo observado (23/10/2026, 14:59).**

En `/caja` (sesión N.º 4, de la cajera), "Movimientos" mostró:

| Fecha/hora | Tipo | Monto | Observación | Comprobante |
|---|---|---|---|---|
| 13:22 | Apertura (fondo inicial) | +100.000 | — | — |
| 13:23 | Cobro | +200.000 | "Anticipo de capital de CC-2026-00002. **(revertido)**" | REC-2026-000002 (Reimprimir) |
| 13:23 | Vuelto | −7.150 | "Vuelto de CC-2026-00002. **(revertido)**" | REC-2026-000002 (Reimprimir) |
| 14:42 | Compensación | −192.850 | "Reversión de REC-2026-000002 de CC-2026-00002." | REV-2026-000001 (Reimprimir) |

Saldo teórico **100.000 Gs.** (= fondo inicial). **Los movimientos originales no se borran**:
se marcan **"(revertido)"** y la compensación aparece como un movimiento **nuevo**, con su
propia constancia — nada se reescribe ni desaparece. **Es el único lugar donde la cajera ve,
con sus propios ojos, que hubo una reversión** (sin que nadie le avisara antes, ver C32).

**Regla de negocio**: ver [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md).
**Hallazgos**: ver C33 más abajo.

### Verificado en el código

- **De dónde sale la marca "(revertido)"**: `ServicioCaja.listarMovimientosConComprobante` —
  para cualquier movimiento con `referencia_tipo = 'PAGO'`, si `pago.getEstadoPago() ==
  EstadoConfirmacionPago.REVERTIDO`, le agrega **" (revertido)"** al texto de la observación
  que ya tenía (la misma lógica, ya documentada, que agrega el badge "REVERTIDO" en el
  historial de cobros de la ficha del préstamo — un solo criterio, dos pantallas).
- **Qué imprime "Reimprimir" de un recibo ya anulado — responde C33**: **nunca sale como
  válido.** `ServicioCobranza.obtenerPdfRecibo` tiene una rama explícita para esto:

  ```java
  if (pagoRevisar != null && pagoRevisar.getEstadoPago() == EstadoConfirmacionPago.REVERTIDO) {
      return generarPdfRecibo(conexionRevisar, idPago);  // nunca el PDF cacheado
  }
  ```

  Es decir, **se salta el caché** (que para un recibo normal nunca se regenera) y vuelve a
  generar el PDF, esta vez con `REVERTIDO = true` — lo que dispara, en `recibo.jrxml`: la franja
  roja **"ANULADO POR REVERSIÓN"** en el encabezado, con el texto *"Este cobro fue revertido el
  [fecha] (autorizó: [gerente]). Motivo: [motivo] — Este documento NO es válido como
  comprobante de pago."*, más el sello diagonal de marca de agua sobre todo el cuerpo. Los tres
  datos (fecha, quién autorizó, motivo) se leen en vivo de `reversiones_pago` en el momento de
  reimprimir — nunca un texto fijo. **Mismo mecanismo, exacto, para el "Reimprimir" de la caja y
  para el del historial de la ficha del préstamo** — ambos llaman al mismo método.
- **En qué línea del arqueo va la compensación**: confirmado — **"(−) Compensaciones por
  reversión (N)"** (`arqueo_caja.jrxml`, parámetro `CANT_COMPENSACIONES`/`MONTO_COMPENSACIONES`),
  un renglón propio del RESUMEN, separado de "Cobros de cuotas"/"Anticipos de capital"/
  "Vueltos entregados".
- **Cómo se van a mostrar el cobro y el vuelto revertidos, en el arqueo — confirma C34 (ver
  Hallazgos)**: a diferencia de la pantalla `/caja` en vivo (que sí agrega "(revertido)" a la
  observación), **`generarPdfArqueo` no distingue en nada** la fila del `COBRO`/`VUELTO` de un
  pago ya `REVERTIDO` — los clasifica exactamente igual que uno vigente: el `COBRO` sigue
  sumando en **"Anticipos de capital"** (por `pago.getTipo() == ANTICIPO`, sin mirar
  `estado_pago`) y el `VUELTO` sigue sumando en **"Vueltos entregados"**, con sus montos
  completos (200.000 y 7.150). El `DETALLE DE MOVIMIENTOS` del arqueo, además, **no tiene
  columna de observación** (solo hora/tipo/operación/comprobante/monto) — así que ni siquiera
  hay espacio ahí para un "(revertido)" como el que sí existe en `/caja`. El `SALDO TEÓRICO`
  final del arqueo sigue siendo exacto (el `COMPENSACION` lo corrige), pero el desglose por
  categoría del RESUMEN ("Anticipos de capital 200.000", "Vueltos entregados 7.150",
  "Compensaciones por reversión 192.850") describe, en los hechos, una operación que ya no es
  válida como si todavía contara en sus categorías normales — solo quien suma las tres líneas
  (o nota que `Anticipos − Vueltos − Compensaciones` no cuadra con la actividad "real" del día)
  se da cuenta.

### Registro adicional: la reimpresión en pantalla (23/10/2026, 15:08) — confirma C33 en pantalla

**Registro de lo observado.** "Reimprimir" de `REC-2026-000002` desde `/caja` generó el recibo
con **franja roja "ANULADO POR REVERSIÓN"**, el texto *"Este cobro fue revertido el 23/10/2026
14:42 (autorizó: [gerente]). Motivo: [motivo] — Este documento NO es válido como comprobante
de pago."* y la **marca de agua "ANULADO" en diagonal** — exactamente lo que "Verificado en el
código" ya había anticipado. **C33 queda confirmado en pantalla, no solo en el código, como
cubierto.**

**Dato (sin hallazgo)**: el recibo anulado conserva, intacto, el **"Resumen del plan
regenerado"** tal como estaba al momento de emitirlo originalmente — **10 cuotas restantes,
próxima cuota 192.503 Gs.** — y no el plan ya restaurado tras la reversión (11 cuotas, próxima
192.891 Gs., ver Paso 11). Es coherente con el resto del mecanismo ya documentado: el recibo
**nunca recalcula nada al reimprimir** (`recibo.jrxml`, comentario propio del archivo), solo
agrega la franja/marca de agua cuando el pago está `REVERTIDO` — el resto del contenido es una
fotografía fija del momento en que se emitió por primera vez, incluido ese resumen. No es un
error: un comprobante anulado, por definición, no tiene por qué actualizarse con el estado
actual del préstamo — muestra lo que decía en el momento del cobro que ya no es válido.

## Hallazgos del Paso 13

- **C33 — Concluido: cubierto. Confirmado en pantalla (23/10/2026, 15:08).** El recibo
  `REC-2026-000002`, ya anulado por la reversión,
  **sigue ofreciendo "Reimprimir"** tanto en `/caja` como en el historial del préstamo — pero
  la reimpresión **nunca sale como un documento válido**: siempre se regenera (nunca usa el PDF
  viejo cacheado) con la franja roja "ANULADO POR REVERSIÓN", el motivo, la fecha y quién
  autorizó, más el sello diagonal — igual en los dos lugares, porque ambos llaman al mismo
  método (`obtenerPdfRecibo`). No hay ningún camino para que alguien, reimprimiendo este
  recibo, se lleve la impresión de que sigue siendo un cobro válido.
- **C34 (riesgo parcial, informe de control, sin clasificar A/B)** — `ServicioCaja.
  generarPdfArqueo` clasifica el `COBRO` y el `VUELTO` de un pago ya `REVERTIDO` **dentro de
  "Anticipos de capital" y "Vueltos entregados", igual que una operación vigente** — sin mirar
  `estado_pago` en ningún punto de esa clasificación, y sin ninguna marca "(revertido)" en el
  `DETALLE DE MOVIMIENTOS` (que ni siquiera tiene columna de observación para eso). **El saldo
  final del arqueo es correcto** — la `COMPENSACION`, en su propio renglón "(−) Compensaciones
  por reversión", corrige el total exacto — pero el **desglose por categoría** del RESUMEN
  describe una actividad del día que ya no es real: un lector que mire solo "Anticipos de
  capital: 200.000 Gs." no tiene, dentro de ese mismo documento, ninguna forma de saber que
  192.850 Gs. de eso ya fueron anulados, salvo que cruce las tres líneas a mano. **Relacionado
  con C27** (el arqueo, documento oficial del cierre de una sesión, ya tenía el riesgo de no
  reflejar cambios posteriores — ahí era la revisión de una diferencia; acá es la reversión de
  un cobro, misma familia de riesgo sobre el mismo documento). **Contraste directo con la
  pantalla `/caja` en vivo**, que sí agrega "(revertido)" a la observación de cada movimiento
  afectado — la información para distinguirlo existe y ya se usa correctamente en un lugar del
  sistema, solo que no llega al arqueo impreso. Sin clasificar A/B — queda para que Joel o el
  dueño de Capital Crédito decidan si el arqueo (como informe de control) debería marcar de
  algún modo las operaciones ya revertidas dentro de sus categorías, en vez de solo corregir el
  total por separado.

## Paso 14 — Verificación final: la ficha confirma el plan restaurado (RF25/RF27)

**Registro de lo observado (23/10/2026, 15:12).**

- **Búsqueda** por cédula (`4512789`): fila **"1/11 — vence 23/11/2026"**, **"Monto a cobrar
  192.891 Gs."** — el plan restaurado al estado previo al anticipo revertido (el mismo que
  tenía después del primer anticipo, antes del segundo).
- **Ficha de cobranza**: Historial con **dos filas**:
  - **13:23, "Anticipo de capital", etiqueta `REVERTIDO`**, `REC-2026-000002`, 192.850 Gs.,
    comprobantes **"Recibo"** y **"Constancia de reversión"**, **sin ninguna acción de
    reversión** disponible (ya revertido, no hay nada más que hacer con esa fila).
  - **11:30, "Anticipo de capital"**, `REC-2026-000001`, 193.250 Gs., con **"Recibo"** y
    **ahora SÍ** el campo "Motivo (mínimo 10 caracteres)" y el botón **"Solicitar
    reversión"** — pasó a ser, de nuevo, la operación válida más reciente del préstamo.
  - **"Cancelar todo 1.806.750 Gs."**; nuevo adelanto propuesto **192.891 Gs. (efectivo
    192.850 Gs.)** — los mismos números que tenía la ficha justo después del primer anticipo,
    antes de que existiera el segundo.
- **Confirma**: plan, saldo y cuotas quedaron **restaurados exactamente** al estado anterior al
  anticipo revertido — ningún número quedó a mitad de camino.
- **Confirma también — cierra C29**: el botón "Solicitar reversión" **se trasladó** de la fila
  revertida a la fila anterior, automáticamente, apenas la de arriba dejó de ser `CONFIRMADO` —
  en pantalla, de punta a punta, queda demostrado que **las reversiones se encadenan de la más
  reciente a la más antigua**, una por una, sin ningún camino para saltarse el orden.

## Recorrido completo de la reversión (23/10/2026)

Resumen en orden real de todo el circuito de reversión de un cobro, con el **segundo cliente de
práctica**, de punta a punta — cada paso enlaza al detalle ya documentado, con sus hallazgos,
sin repetir el contenido completo acá.

1. **La cajera solicita, con motivo**: en el Historial, "Motivo (mínimo 10 caracteres)" +
   "Solicitar reversión" sobre `REC-2026-000002` (la única fila con el botón). Paso 8.
2. **Confirmación**: "¿Solicitar la reversión del cobro REC-2026-000002 por 192.850 Gs.?"
   ("Sí, solicitar"/"Cancelar"). Paso 8.
3. **Pendiente**: éxito "Queda pendiente de autorización del gerente."; la fila pasa a
   "Reversión solicitada — pendiente de autorización". Paso 8. Hallazgos: **C29** (solo la
   operación más reciente puede revertirse, sin límite de tiempo ni de sesión), **C30**
   (riesgo parcial: la ficha sigue permitiendo adelantar/cancelar sobre un saldo con una
   reversión pendiente, sin aviso), **B69** (el historial no vuelve a mostrar el motivo
   mientras está pendiente).
4. **El panel del gerente avisa**: "1 Reversiones por autorizar" con "Ir a Reversiones" — a
   diferencia de B63 (diferencias de caja), este aviso sí funciona. Paso 9.
5. **Pantalla de Reversiones, con las reglas explicadas**: "Solo se puede revertir la operación
   más reciente de cada préstamo, y hace falta una caja abierta hoy con efectivo suficiente.";
   botones "Autorizar" y "Rechazar" (con motivo obligatorio). Paso 10. Hallazgo: **C29**
   (actualización: la regla también está escrita en la propia pantalla).
6. **Autorizar, con confirmación irreversible**: "¿Autorizar la reversión del cobro [...] por
   [...]? No se puede deshacer." ("Sí, autorizar"/"Cancelar") — **sin pedir ningún comentario
   del gerente**. Paso 10. Hallazgos: **B70** (sin comentario del gerente, a diferencia de
   Rechazar), **C31** (al revertir un cobro con vuelto, la caja devuelve el monto aplicado, no
   el recibido — matemáticamente correcto).
7. **Éxito con el efecto en caja**: "Reversión autorizada — Se devolvieron 192.850 Gs. de la
   caja.", con "Imprimir constancia de reversión". El pago pasa a `REVERTIDO`, el plan anterior
   vuelve a `vigente`. Paso 11. Hallazgo: **B71** (la confirmación no anticipó este efecto ni
   el del plan).
8. **Constancia de reversión, con firma del cliente**: `REV-2026-000001`, "DATOS DE LA
   REVERSIÓN" completos, firmas de cajera, gerente y cliente — el cliente firmando "Recibí
   conforme la devolución de 192.850 Gs." Paso 11. Hallazgo: **C32** (reclasificado: la entrega
   física queda cubierta en papel; solo sigue sin cubrir el aviso dentro del sistema), **B72**
   (encabezado en letra más chica que el resto, sí tiene pie "Emitido por"/"Página").
9. **El panel de la cajera se ajusta, sin aviso**: "Cobros de hoy" baja de 2 a 1, "Efectivo en
   caja" baja de 292.850 a 100.000 — ajuste automático y correcto, pero sin ninguna explicación
   de por qué. Paso 12. Hallazgo: **C32** (confirmado en pantalla, del lado de la cajera).
10. **La caja de la cajera, con "(revertido)" y la Compensación**: el Cobro y el Vuelto
    originales quedan marcados "(revertido)", sin borrarse; la Compensación entra como
    movimiento nuevo, con su propia constancia. Paso 13. Hallazgo: **C34** (el arqueo clasifica
    el cobro/vuelto revertidos en sus categorías normales, sin marcarlos — relacionado con
    C27).
11. **El recibo reimpreso sale anulado**: franja roja "ANULADO POR REVERSIÓN", motivo, fecha y
    autorizador reales, marca de agua diagonal — nunca como documento válido. Paso 13.
    Hallazgo: **C33** (concluido: cubierto).
12. **El plan queda congelado en el recibo anulado**: ese mismo recibo sigue mostrando "10
    cuotas restantes, próxima cuota 192.503 Gs." — el estado de antes de la reversión, no el
    restaurado. Paso 13 (dato sin hallazgo, coherente con que el recibo nunca recalcula nada
    al reimprimir).
13. **La ficha confirma el plan restaurado y cierra la cadena**: búsqueda y ficha muestran el
    plan, saldo y próxima cuota exactamente como antes del anticipo revertido; el botón
    "Solicitar reversión" se trasladó a la fila anterior, confirmando el encadenamiento de
    reversiones de la más reciente a la más antigua. Paso 14. Hallazgo: **C29** (completo).

**Qué sigue**: con la reversión resuelta de punta a punta (solicitud, autorización, caja,
comprobantes y verificación final), el ciclo de este segundo cliente de práctica queda
consistente — `CC-2026-00002` con un plan vigente de 11 cuotas, próxima el 23/11/2026 por
192.891 Gs., exactamente como después del primer anticipo.

## Paso 15 — Reversión rechazada: segundo intento, sobre el primer anticipo (RF25)

**Registro de lo observado (23/10/2026, 15:20–15:23).**

- **La cajera solicita** sobre la fila de las 11:30 (`REC-2026-000001`, 193.250 Gs. — la que
  volvió a ofrecer el botón tras la primera reversión, Paso 14): motivo *"Posible error: el
  cliente dice que no pidió este anticipo."* → confirmación **"¿Solicitar la reversión del
  cobro REC-2026-000001 por 193.250 Gs.?"** → éxito **"Solicitud de reversión enviada — Queda
  pendiente de autorización del gerente."** → fila **"Reversión solicitada — pendiente de
  autorización"**.
- **El gerente**: panel con **"1 Reversiones por autorizar"**. En `/reversiones`, toca
  **"Rechazar"** sin motivo → advertencia **"Falta un dato — Falta completar «Motivo del
  rechazo»."** (**"Entendido"**). Con el motivo *"Revisado con el cliente: el anticipo sí fue
  solicitado; el cobro es correcto."* → confirmación **"Confirmar: Rechazar reversión —
  ¿Rechazar la reversión del cobro REC-2026-000001 por 193.250 Gs.?"** (**sin** "No se puede
  deshacer" — ver el dato más abajo) → éxito **"Solicitud de reversión rechazada"**
  (**"Aceptar"**; **sin ninguna constancia ofrecida**).
- **Pestaña "Historial"** (*"Reversiones ya decididas (autorizadas o rechazadas)."*), columnas
  **Fecha de la solicitud, Operación, Cliente, Monto, Motivo de la solicitud, Solicitó, Estado,
  Fecha de la decisión, Motivo de la decisión, Autorizó/Rechazó, Comprobante** — dos filas:
  - **Rechazada**: solicitud 15:20 → decisión 15:23, con el motivo del rechazo, **sin
    comprobante**.
  - **Autorizada**: solicitud 14:07 → decisión 14:42, **"Motivo de la decisión: -"**,
    comprobante "Constancia".
- **Regla de negocio**: ver [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md).
- **Hallazgos**: ver B70 (confirmación en pantalla) más abajo.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`pagos`** (id 1): `estado_pago` **sigue `CONFIRMADO`** — un rechazo no toca el pago en
  absoluto.
- **`reversiones_pago`** (id 2, nueva fila — `id_pago` = 1): `estado = 'RECHAZADA'`,
  `motivo_reversion` el texto de la cajera, `solicitado_por` = cajera (id 3),
  `fecha_hora_solicitud` 15:20:44; `motivo_rechazo` el texto del gerente,
  **`fecha_hora_reversion` = 15:23:01** (sí tiene valor, a pesar de no haber movido dinero —
  ver "Verificado en el código" abajo); `autorizado_por`/`procesado_por` **vacíos** (nunca se
  llenan en un rechazo, solo en una autorización).
- **Auditoría**: `SOLICITAR_REVERSION` (entidad PAGO, id 1, usuario cajera) y
  `RECHAZAR_REVERSION` (entidad **REVERSION_PAGO**, id 2, usuario gerente) — distinta entidad
  de auditoría que `AUTORIZAR_REVERSION` (que audita sobre PAGO), coherente con que un rechazo
  no afecta al pago en sí.

### Verificado en el código

- **`fecha_hora_reversion` se reutiliza para "fecha de la decisión", también en un rechazo**:
  `ReversionPagoDAO.marcarRechazada` — comentario propio del método: *"fecha_hora_reversion
  pasa a significar 'fecha de la última decisión' (antes solo se llenaba al autorizar) — así
  'Fecha de la decisión' en el historial tiene un valor también para un rechazo, no solo para
  una autorización."* Decisión ya tomada y documentada en el propio código (Joel, RF25 punto
  4) — no es una inconsistencia del nombre de la columna, es un cambio de significado
  deliberado.
- **¿La cajera ve el rechazo y su motivo en la ficha del préstamo?** **Sí.**
  `cobranza/ficha.jsp` (línea ~147): cuando `estadoReversionSolicitud == "RECHAZADA"`, imprime
  **`<div class="text-danger mb-1">Reversión rechazada: [motivo]</div>`**, justo arriba del
  formulario — el motivo del gerente queda visible, en rojo, en la misma fila del historial.
- **¿Puede volver a solicitar la reversión del mismo cobro?** **Sí.** La misma condición ya
  documentada en el Paso 8 (`sinSolicitudViva = reversionDelPago == null ||
  reversionDelPago.getEstado() == RECHAZADA`) hace que `puedeSolicitarReversion` vuelva a ser
  `true` — el botón cambia de texto a **"Solicitar de nuevo"**, y `ServicioReversiones.
  solicitarReversion` reutiliza la misma fila de `reversiones_pago` (`id_pago` es `UNIQUE`,
  nunca inserta una segunda) con el nuevo motivo.

## Hallazgos del Paso 15

- **B70 (confirmado en pantalla)** — Ya documentado en el código (Paso 10): en el **Historial**,
  la reversión **autorizada** (la que de verdad mueve dinero, cambia el plan y anula
  comprobantes) queda con **"Motivo de la decisión: -"**, mientras que la **rechazada** (que no
  mueve nada) **sí** tiene su motivo completo. Confirmado ahora con las dos filas reales, una
  junto a la otra, en la misma tabla — la operación más consecuente es la que menos explicación
  deja registrada.
- **Dato (sin hallazgo)**: la confirmación de **"Rechazar"** no dice **"No se puede deshacer"**
  — coherente, porque rechazar no mueve dinero ni cambia nada del préstamo; solo "Autorizar" (que
  sí es irreversible en los hechos) lleva esa advertencia.

## Paso 16 — La cancelación total: ficha, confirmación y mensaje de éxito (RF26-RF28)

**Registro de lo observado (25/10/2026, 10:54–10:55).**

- **Ficha de Cobranza de `CC-2026-00002`**, con la caja ya abierta del día: la tarjeta de cobro
  muestra **"Cancelar todo 1.809.330 — en efectivo 1.809.300"** — ya no los **1.806.750 Gs.**
  que mostraba el 23/10/2026 ("Próximo paso exacto" de `00_LEEME.md`): la diferencia son **2
  días de interés devengado (2.345 Gs.) + su IVA (235 Gs.)**, el mismo cálculo ya verificado con
  `SELECT` en `06_comprobantes_recibo_factura.md` § 2. El adelanto propuesto al lado también
  cambió, a **"Adelantar a capital — próxima cuota 192.298 Gs."** (recalculado con el mismo
  criterio de siempre; no es un hallazgo nuevo, solo el dato de esa pantalla en ese momento).
- **Historial de cobros, fila de las 11:30** (`REC-2026-000001`): sigue mostrando **"Reversión
  rechazada: [motivo]"** y el botón **"Solicitar de nuevo"** — **ya registrado completo en el
  Paso 15** (23/10/2026, con su verificación en `cobranza/ficha.jsp`); no es un evento nuevo, es
  la misma fila, sin cambios, todavía visible dos días después.
- **Selecciona "Cancelar todo"**: el formulario propone **"Recibido: 1.809.300"**, sin ningún
  desglose por cuota (ver B73) — y, al lado, **siguen visibles y habilitados** el selector
  **"1/2/3 cuotas"** y el texto **"Si adelanta…"** de la tarjeta de "Adelantar a capital", aunque
  no tienen ningún sentido una vez que se eligió cancelar el préstamo entero. Ver **B74**.
- **Cambia el monto recibido a 1.810.000 Gs.** → el sistema calcula **"Vuelto: 700 Gs."**
  automáticamente (mismo mecanismo ya documentado para el cobro normal y el adelanto).
- **Confirmación**: **"Confirmar: Cancelar todo"** — *"Cancelar todo: 1.809.330 Gs.", "Recibido:
  1.810.000 Gs.", "Vuelto: 700 Gs."* (sin desglose — ver B73), con los botones **"Sí, cancelar
  todo"** y **"Cancelar"** uno junto al otro. Ver **B3 (peor caso)**.
- **Éxito**: **"Cancelación total aplicada — Total aplicado 1.809.330. Vuelto 700. Cobrado en
  efectivo 1.809.300 (redondeo a favor del cliente 30). Recibo REC-2026-000003. Factura
  001-001-0000001."**, con los botones **"Imprimir recibo"**, **"Imprimir factura"** e
  **"Imprimir constancia"**.
- **El préstamo pasa a "Finalizado"** en la ficha.
- **Nueva fila en el historial, "Cancelación total"**: con la misma casilla y el mismo texto ya
  vistos para cualquier cobro con factura (*"Tengo en mi poder el original y todas las copias de
  la factura... devueltas por el cliente"*, *"Sin la factura original y sus copias no se puede
  anular. Corresponde una nota de crédito; consulte con el contador."*) y el botón **"Solicitar
  reversión"**. Ver **C35**.
- **Regla de negocio**: ver
  [RF26_cancelacion_anticipada.md](../funcionamiento/RF26_cancelacion_anticipada.md),
  [RF27_adelantar_capital.md](../funcionamiento/RF27_adelantar_capital.md) y
  [RF28_constancia_cancelacion.md](../funcionamiento/RF28_constancia_cancelacion.md).

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`prestamos`**: `estado_prestamo = 'FINALIZADO'`.
- **`cuotas`** del plan vigente (`id_plan_pago` 3, 11 cuotas): las **11 cuotas, `PAGADA`** (1/11
  a 11/11) — ninguna queda `PENDIENTE` ni `PARCIAL`, coherente con "Cancelación total".
- **`movimientos_caja`** del 25/10/2026: **`COBRO` 1.810.000** y **`VUELTO` 700**, ambos con
  `referencia_tipo = 'PAGO'` — exacto lo que se tecleó en pantalla.
- **`auditoria_operaciones`**: **`CANCELAR_TOTAL`** (entidad `PAGO`) a las **10:55:02**, dos
  segundos después del clic — coherente con la hora del recibo (`REC-2026-000003`, "Asunción,
  25/10/2026 10:55").

### Verificado en el código — C35 (qué haría "Solicitar reversión" acá, sin probarlo)

**No es un hueco: ya está resuelto por diseño, con dos controles independientes** (decisiones
previas de Joel, 12-13/10/2026, antes de este recorrido):

1. **`ServicioReversiones.validarPuedeSolicitarReversion`**: mientras
   `prestamos.estado_cierre_documental` **no** sea `COMPLETO` (como en este momento, 10:54-11:34,
   antes del cierre documental del Paso siguiente), el botón **"Solicitar reversión"** se ofrece
   igual que para cualquier cobro, con la misma casilla de la factura. Una vez que el cierre
   documental pasa a `COMPLETO`, la misma ficha (`cobranza/ficha.jsp`, método
   `isNoAdmiteReversionPorCierreDocumental`) deja de mostrar el formulario y en su lugar imprime
   **"No admite reversión: pagaré devuelto al cliente"** — confirmado leyendo el código, sin
   necesidad de probarlo en pantalla, porque el `SELECT` de arriba ya confirma que el cierre
   documental de esta operación se completó más tarde (ver el Paso siguiente).
2. **Si se autorizara** (`ServicioReversiones.autorizarReversion`, con la factura devuelta): la
   transacción restaura las 11 cuotas a su estado anterior (inversa exacta del motor), marca
   `liquidaciones_anticipadas.estado = 'REVERTIDA'`, saca al préstamo de `FINALIZADO`
   (`PrestamoDAO.revertirFinalizacion`, recalculando el estado real según las cuotas), **anula la
   factura** (nunca la borra) y emite una **Nota de anulación** (`NAN-aaaa-nnnnnn`, numerador
   propio) — no una "Nota de Crédito" literal, a pesar de que el texto en pantalla (la casilla de
   la cajera) diga "Corresponde una nota de crédito". Si la factura **no** se declara devuelta, el
   método rechaza directo: *"La factura ya fue entregada al cliente. Corresponde emitir una Nota
   de Crédito, que no está disponible en esta versión."* — una limitación ya conocida y
   documentada en el propio código (comentario del 12/10/2026, DNIT Decreto 6539/05 vs. RG
   118/2017, cita a confirmar con el contador).

**Implicancia fiscal para el contador (sin opinar sobre la normativa)**: confirmar que **"Nota de
anulación"** (lo que el sistema de verdad emite al revertir, con factura devuelta) es el
tratamiento fiscal correcto para anular una cancelación total ya facturada — y no una "Nota de
Crédito" (lo que dice el texto de la pantalla, y lo que el sistema todavía no sabe emitir).

## Hallazgos del Paso 16

- **B74** — Con **"Cancelar todo"** ya seleccionado, el formulario sigue mostrando el selector
  **"1/2/3 cuotas"** y el texto **"Si adelanta…"** de la tarjeta de "Adelantar a capital" — datos
  de otra operación distinta, sin ningún sentido una vez que se eligió cancelar el préstamo
  entero.
- **B3 (peor caso de esta familia)** — La confirmación de "Cancelar todo" ofrece **"Sí, cancelar
  todo"** junto a **"Cancelar"** — de todas las variantes de B3 ya registradas (`02_solicitud.md`,
  `03_aprobacion.md`), esta es la más parecida entre sí: una palabra de diferencia entre
  "confirmar la cancelación total del préstamo" y "cerrar el diálogo sin hacer nada".
- **C35** — Verificado en el código, sin probarlo (ver arriba): un préstamo `FINALIZADO` con
  factura sigue ofreciendo "Solicitar reversión" mientras el cierre documental no esté
  `COMPLETO` — por diseño, con dos controles (al solicitar y al autorizar) y un mensaje claro una
  vez que el pagaré ya se devolvió. Queda para el contador confirmar si "Nota de anulación" es el
  tratamiento fiscal correcto, dado que el propio texto en pantalla todavía habla de "Nota de
  Crédito".

## Paso 17 — El recibo y la factura impresos de la cancelación total (RF23/RF33)

**Registro de lo observado (25/10/2026, 11:16).**

Tras la cancelación total de `CC-2026-00002` (recibo `REC-2026-000003`, constancia
`CAN-2026-000001` — ver el desarrollo completo de esa cancelación, con la cuenta exacta del
interés de 2 días y cómo cuadran los montos, en `06_comprobantes_recibo_factura.md` §
"Por qué recibo, factura y constancia de cancelación"), **"Imprimir factura"** abrió la
**primera factura real de esta base de pruebas**:

- Encabezado de la empresa ("Préstamos personales", correo electrónico).
- Recuadro fiscal: **"RUC 80123456-5 — TIMBRADO N.º 16842795 — Vigencia: 01/09/2026 al
  31/08/2027 — FACTURA N.º 001-001-0000001"**.
- "Fecha de emisión: 25/10/2026"; "Condición de venta: CONTADO ☒ CRÉDITO ☐".
- Nombre o razón social, RUC/C.I., Dirección (con "Barcequillo,,"), Operación `CC-2026-00002`,
  "Recibo asociado: REC-2026-000003".
- Tabla Cant/Descripción/P. Unit./Exentas/5 %/10 %: **"1 — Interés compensatorio cuota 1/11 —
  2.580 Gs."**, en la columna 10 %.
- SUBTOTALES 0/0/2.580; "TOTAL A PAGAR: 2.580 Gs." (y en letras); "LIQUIDACIÓN DEL IVA: (5 %)
  0 Gs. (10 %) 235 Gs. TOTAL IVA 235 Gs."; "Tipo de transacción: Prestación de servicios";
  "ORIGINAL: Cliente / DUPLICADO: Archivo tributario".
- **Regla de negocio**: ver [RF23_recibo_factura.md](../funcionamiento/RF23_recibo_factura.md) y
  [RF33_facturas_iva.md](../funcionamiento/RF33_facturas_iva.md).
- **Hallazgos**: ver B73, B75, B13 (se repite), C36, C37 y C38 más abajo.

### Registro adicional: el recibo completo, con el desglose por cuota (25/10/2026, 11:20)

**"RECIBO DE DINERO N.º REC-2026-000003"**: "Asunción, 25/10/2026 10:55"; Operación; Cliente y
C.I.; **"Factura asociada: 001-001-0000001"**. Tabla **Cuota/Capital/Interés/Morat./Punit./
IVA/Total**, de 1/11 a 11/11: **cuota 1/11** con interés 2.345 e IVA 235 cargados (total
155.488); **cuotas 2/11 a 11/11** solo capital. **TOTAL**: capital 1.806.750, interés 2.345,
morat. 0, punit. 0, IVA 235, **total 1.809.330**. Después de la tabla: **"Efectivo recibido:
1.810.000 Gs. Vuelto: 700 Gs. Aplicado: 1.809.330 Gs. Redondeo: -30 Gs."**; **"Son: UN MILLÓN
OCHOCIENTOS NUEVE MIL TRESCIENTOS TREINTA GUARANÍES."** (el monto aplicado — ver **C37**); la
leyenda **"PRÉSTAMO CANCELADO"**; firmas; **"ORIGINAL: Cliente / COPIA: Capital Crédito"**;
"Emitido por"; "Página 1 de 1".

Confirma exacto lo ya verificado con `SELECT`: la fila `TOTAL` de esta tabla es, literalmente,
la suma de las 11 filas de `aplicaciones_pago` de este pago (ver el detalle completo en
`06_comprobantes_recibo_factura.md` § "Lo observado en pantalla... el recibo completo").

### Verificado en el código (y por qué 2.580 Gs. = 2.345 + 235)

Los **2.580 Gs.** de esta factura son, exacto, **el interés devengado (2.345 Gs.) + su IVA
(235 Gs.)** — nunca el capital. Es la misma cuenta ya verificada con `SELECT` contra
`liquidaciones_anticipadas` en `06_comprobantes_recibo_factura.md` § 2: `capitalNoVencido
(1.806.750) × tasaMensual (0,020118) × (2 ÷ 31) ≈ 2.345 Gs.`, IVA `= redondear(2.345 × 0,10) =
235 Gs.` **Relación con el recibo**: el recibo `REC-2026-000003` acreditó **1.809.330 Gs.** en
total (capital + interés + IVA) — la factura solo describe los **2.580 Gs. gravados** de esa
cifra, un subconjunto, nunca un monto aparte.

El detalle completo de dónde salen timbrado/vigencia/establecimiento/punto de expedición
(todos parámetros `fiscal.*`, confirmados con `SELECT`), cómo se arma exactamente la
descripción **"Interés compensatorio cuota 1/11"** en una cancelación total
(`reconstruirLineasFactura` → `derivarLineasCuotaFactura`, reusando el mismo formato que una
cuota vencida normal porque el interés devengado se graba como una aplicación `CUOTA` sobre la
primera cuota pendiente), por qué es **una sola página** (igual que el recibo, sin la
estructura de dos bandas que sí usa el comprobante de desembolso), qué pasa si el timbrado
está vencido o agotado (**se rechaza todo el cobro**, no solo la factura — mismo criterio ya
documentado para cualquier cobro gravado), y por qué el IVA se calcula **sobre el interés**
(nunca como total÷11, aunque matemáticamente coincida) — todo eso queda documentado con el
código citado exacto en `06_comprobantes_recibo_factura.md` § 2, "Lo observado en pantalla", y
en los hallazgos B75/B13/C36 que ese mismo documento ya desarrolla por completo.

### Registro adicional: la constancia de cancelación completa (25/10/2026, 11:20)

**"CONSTANCIA DE CANCELACIÓN TOTAL DE PRÉSTAMO N.º CAN-2026-000001"**: *"Por la presente,
Capital Crédito Paraguay S.R.L. deja constancia de que [cliente]... ha CANCELADO EN SU
TOTALIDAD el préstamo que se detalla, no registrando deuda pendiente por esta operación a la
fecha."*; "Son:" 1.809.330 en letras; **"DATOS DEL PRÉSTAMO"** (operación, desembolso
23/10/2026, cancelación 25/10/2026, monto 2.000.000, plazo 12 cuotas); **"LIQUIDACIÓN DE LA
CANCELACIÓN"** (vencidas impagas 0; capital no vencido 1.806.750; interés 2.345; IVA 235;
**TOTAL CANCELADO 1.809.330**); cita de la Ley 6624/2020; "Comprobantes: Recibo
REC-2026-000003 — Factura: 001-001-0000001"; **"Pagaré: se devuelve al cliente el pagaré
original firmado. [   ]"** (casilla manual); firmas del representante legal y "Recibí
conforme (cliente)".

**Verificado en el código**: el representante legal sale de dos parámetros,
`empresa.representante_legal_nombre`/`_cedula` (confirmado con `SELECT`: "Ramón Benítez" / C.I.
2.845.117). La casilla del pagaré **no tiene ningún control real detrás de ese PDF** — el
correlato verdadero del sistema es un flujo aparte de 3 pasos
(`marcarFinalizado` → `generarConstanciaDevolucion` → `completarCierreDocumental`, con su
propia Constancia de devolución `DEV-aaaa-nnnnnn`), desarrollado por completo en
`06_comprobantes_recibo_factura.md` § 3 — confirmado con `SELECT` que, hoy,
`estado_cierre_documental = 'PENDIENTE'` y ninguno de esos 3 pasos se ejecutó todavía. Ver
**C38**.

## Hallazgos del Paso 17

- **B73 (matizar)** — El desglose cuota por cuota (visto recién en el recibo) **existe, pero
  solo aparece después de confirmar el cobro** — la confirmación previa de "Cancelar todo"
  (verificada en `cobranza-ficha.js`) solo muestra tres líneas, todas en total: *"Cancelar
  todo: [monto exacto]"*, *"Recibido: [monto]"*, *"Vuelto: [monto]"* (o "Sin vuelto") — sin
  ningún desglose por cuota. No falta el dato (ya se calcula cuota por cuota antes de
  confirmar), falta mostrarlo en ese momento, no solo después.
- **B75** — La factura describe el interés de una cancelación total con el mismo formato que
  el de una cuota vencida normal ("Interés compensatorio cuota 1/11"), sin ningún texto que
  indique que es una cancelación anticipada ni que el monto es un prorrateo por días.
  Desarrollo completo en `06_comprobantes_recibo_factura.md` § "Hallazgos de este caso".
- **B13 (se repite)** — La dirección del cliente, con la coma duplicada ("Barcequillo,,"), ya
  vista en el contrato y en el comprobante de desembolso, aparece también en la factura — ahora
  en un documento fiscal.
- **C36 (para el contador/revisión legal, sin opinar sobre normativa)** — Verificar que el
  formato de esta factura cumpla la normativa SET vigente (incluida la situación de SIFEN) y si
  el timbrado configurado es de prueba o real. Relacionado con RF23/RF33.
- **C37 (para el contador, sin opinar sobre normativa)** — El **"Son:"** del recibo expresa el
  monto **aplicado** a la deuda (1.809.330 Gs.), no el efectivo neto que entró a la caja
  (recibido − vuelto = 1.809.300 Gs., 30 Gs. menos, por el redondeo perdonado). Relacionado con
  **C25**: en un adelanto a capital no existe este mecanismo de perdón de redondeo, así que ahí
  el "Son:" nunca se aparta de lo realmente entregado — la pregunta de C37 es exclusiva de los
  caminos que sí lo perdonan (cobro normal y cancelación total). Desarrollo completo en
  `06_comprobantes_recibo_factura.md` § "Hallazgos de este caso".
- **C38 — Verificado en el código; queda recorrer en pantalla.** La devolución del pagaré
  figura en la constancia de cancelación como una casilla a marcar a mano, sin control real
  detrás de ese PDF — pero sí existe un correlato real del sistema, en un flujo aparte de 3
  pasos (`marcarFinalizado` → `generarConstanciaDevolucion` → `completarCierreDocumental`, con
  su propia Constancia de devolución `DEV-aaaa-nnnnnn`). Confirmado con `SELECT` que ninguno de
  esos 3 pasos se ejecutó todavía para `CC-2026-00002` — se recorrerá en pantalla en el paso
  siguiente. Desarrollo completo en `06_comprobantes_recibo_factura.md` § 3.
- **Dato (contraste con B73)**: la constancia de cancelación sí trae el desglose completo de la
  liquidación (vencidas impagas, capital no vencido, interés, IVA, total) — a diferencia de la
  confirmación previa de "Cancelar todo", que solo muestra totales.
- **Dato**: la constancia cita la Ley 6624/2020, la misma base legal que ya sustenta la
  cláusula SEXTA del contrato (hallazgo **C5**, `04_formalizacion.md`) — citada en dos
  documentos distintos del mismo préstamo.

## Recorrido completo del cobro y cierre (23–25/10/2026)

Resumen en orden real de todo el circuito de cobro que se pudo probar hoy, con el **segundo
cliente de práctica**, sin cambiar ninguna fecha — cada paso enlaza al detalle ya documentado,
con sus hallazgos, sin repetir el contenido completo acá.

1. **Bandeja de cobranza** (`/cobranza`): las 3 secciones (En mora, Vencen hoy, Próximos 7 días)
   en 0 — el segundo cliente de práctica no aparece en ninguna, porque su cuota 1 vence a 31
   días. Paso 1. Hallazgo: **B54** (sin una lista de préstamos activos en general).
2. **Búsqueda por cédula**: resultado con "Monto a cobrar 193.280 Gs." y "Al día", para una
   cuota que todavía no venció. Paso 1. Hallazgo: **B55** (la pantalla invita a cobrar un monto
   que el paso siguiente no acepta por esa vía).
3. **Ficha de cobranza, sin ningún botón de cobro normal**: "Deuda exigible hoy: Sin cuotas
   vencidas.", y el único botón de pago dice "Adelantar a capital" — el rechazo exacto de RF22
   existe solo en el servidor, nunca alcanzable desde esta pantalla. Paso 2. Hallazgos: **C23**
   (adelantar no "tacha" la cuota 1, solo recorta el final del plan), **B55 (ampliación)** (el
   botón final de confirmación también dice solo "Cobrar").
4. **Adelanto a capital, con interés devengado 0 (desembolso del mismo día)**: 193.280 Gs.
   redondeados a 193.250 Gs. en efectivo; próxima cuota 192.891 Gs., 11 cuotas en vez de 12.
   Paso 2, "Verificado en el código". Hallazgos: **C24** (relacionado con C4, confirma con un
   número real que "mantiene el monto de la cuota" se cumple en capital+interés, no en el total
   con IVA), **C25** (los 30 Gs. de redondeo no se registran como descuento, a diferencia del
   cobro normal).
5. **"Ver detalle del adelanto"**: interés devengado 0 Gs. + IVA 0 Gs., capital aplicado
   193.250 Gs., saldo que queda 1.806.750 Gs. — confirma en pantalla el cálculo del punto
   anterior. Paso 2. Hallazgo: **B56** (el desglose más importante queda oculto en un
   desplegable cerrado por defecto).
6. **Confirmación y éxito**: "Confirmar: Adelantar a capital" con el desglose completo (dato
   positivo, modelo para B4/B43); éxito "Anticipo de capital aplicado (193.250 Gs.) — Recibo
   REC-2026-000001. Sin factura (no hay conceptos gravados)." Paso 3. Hallazgos: **C26**
   (el plan firmado que el cliente se llevó queda desactualizado — riesgo parcial, con la ficha
   de Formalización y el Informe de estado de cuenta como únicas vías de respaldo), **B57**
   (precisión: "Solicitar reversión" solo aparece en la última operación vigente, no en "cada
   fila" — la condición ya es correcta).
7. **El recibo impreso**: REC-2026-000001, con el resumen del plan regenerado y "Factura
   asociada: no corresponde". Paso 4. Hallazgos: **B58** (criterio de original/copia distinto
   entre comprobantes), **B59** (correo solo en el encabezado, espacio en blanco en la variante
   de anticipo, familia B12), cierre de **C26**.
8. **El efecto en la caja de hoy**: movimiento `COBRO` (no un tipo propio de "anticipo") por
   193.250 Gs., enlazado a `pagos.tipo = ANTICIPO`; "Reimprimir" sin auditar. Paso 5. Hallazgo:
   **B60** (la columna "Tipo" de `/caja` muestra "Cobro", mientras el arqueo sí distingue
   "Anticipos de capital" — confirmado también en `07_cierre_del_dia.md`).
9. **Pago de más, con vuelto**: un segundo anticipo sobre el mismo préstamo, recibiendo
   200.000 Gs. contra un monto sugerido de 192.850 Gs. — vuelto de 7.150 Gs., con `COBRO` por lo
   recibido completo y `VUELTO` por la diferencia, dos movimientos de caja separados.
   Paso 6. Hallazgos: **C28** (el monto aplicado no es libre, limitado al selector 1/2/3
   cuotas, sin alternativa de saldo a favor), **B67** (el fondo sugerido de apertura no
   contempla el cambio para dar vuelto), repetición de **C23** con un segundo adelanto.
10. **El efecto en la caja del pago con vuelto**: confirma en pantalla el `COBRO`/`VUELTO`
    separado ya verificado por `SELECT` en el Paso 6. Paso 7. Hallazgo: **B68** (el mismo
    recibo aparece con dos botones "Reimprimir", uno por cada movimiento de caja que generó).
11. **Solicitar la reversión de un cobro (paso 1, cajera)**: motivo + "Solicitar reversión"
    sobre el segundo anticipo (el único que lo ofrece); éxito "Queda pendiente de autorización
    del gerente."; la fila pasa a "pendiente de autorización". Paso 8. Hallazgos: **C29** (solo
    el último cobro vigente puede revertirse, sin límite de tiempo ni de sesión), **C30**
    (riesgo parcial: la ficha sigue ofreciendo adelantar/cancelar sobre un saldo con una
    reversión pendiente, sin aviso), **B69** (el historial no muestra el motivo de la
    reversión solicitada).
12. **El panel del gerente con la reversión pendiente (paso 2)**: "1 Reversiones por autorizar"
    con su enlace — confirma que el panel SÍ avisa (contraste con B63); los indicadores de hoy
    todavía incluyen el cobro en reversión. Paso 9. Verificación pendiente: si al autorizar se
    ajustan los indicadores.
13. **La pantalla de Reversiones: pendientes, Autorizar, Rechazar e Historial (paso 3)**: la
    regla de "solo la operación más reciente" ya explicada en pantalla; botones "Autorizar"
    (con confirmación, sin pedir motivo) y "Rechazar" (con confirmación y motivo obligatorio).
    Paso 10. Hallazgos: **C29** (actualización: la regla también está en la propia pantalla),
    **B70** (Autorizar no exige comentario del gerente), **C31** (al revertir un cobro con
    vuelto, la caja recibe de vuelta el monto aplicado, no el recibido — matemáticamente
    correcto).
14. **El gerente autoriza (paso 4)**: éxito "Se devolvieron 192.850 Gs. de la caja."; el pago
    queda `REVERTIDO`, el plan vuelve a 11 cuotas (próxima 192.891 Gs.), la `COMPENSACION` entra
    en la caja de la cajera, "Imprimir constancia de reversión" genera `REV-2026-000001`.
    Paso 11. Hallazgos: **B71** (la confirmación no anticipa el efecto en caja ni en el plan),
    **C32** (riesgo parcial en ese momento: sin aviso a la cajera, sin confirmación de quién
    entrega el efectivo).
15. **El documento impreso: Constancia de reversión (paso 5)**: `REV-2026-000001`, con las
    firmas de cajera, gerente y cliente ("Recibí conforme la devolución de 192.850 Gs."). Paso
    11. Hallazgos: **C32 actualizado** (la entrega física queda cubierta en papel; el aviso
    dentro del sistema sigue sin cubrir), **B72** (encabezado en letra más chica que el resto
    de comprobantes; sí tiene pie "Emitido por"/"Página").
16. **El panel de la cajera después de la reversión (paso 6)**: "Cobros de hoy" bajó de 2 a 1,
    "Efectivo en caja" bajó de 292.850 a 100.000 — ajuste automático y correcto, sin ningún
    aviso de por qué cambiaron. Paso 12. Hallazgo: **C32 confirmado en pantalla, del lado de la
    cajera**.
17. **`/caja` de la cajera después de la reversión (paso 7)**: los movimientos originales
    quedan marcados "(revertido)", sin borrarse; la Compensación entra como movimiento nuevo,
    con su propia constancia. Paso 13. Hallazgos: **C33** (concluido: cubierto — "Reimprimir" de
    un recibo anulado nunca sale como válido), **C34** (riesgo parcial: el arqueo clasifica el
    cobro/vuelto revertidos en sus categorías normales, sin marcarlos — relacionado con C27).
18. **La reimpresión del recibo anulado, confirmada en pantalla (paso 8)**: franja roja
    "ANULADO POR REVERSIÓN", motivo/fecha/autorizó reales y marca de agua diagonal — confirma
    C33 en pantalla. Paso 13. Dato sin hallazgo: el recibo anulado conserva el resumen del plan
    tal como estaba al emitirse (10 cuotas, 192.503 Gs.), no el plan ya restaurado.
19. **Verificación final, cierre de la reversión (paso 9)**: búsqueda y ficha confirman el
    plan, saldo y próxima cuota restaurados exactamente al estado previo al anticipo revertido;
    "Solicitar reversión" se trasladó a la fila anterior. Paso 14. Hallazgo: **C29**
    (completo: las reversiones se encadenan de la más reciente a la más antigua).
20. **Reversión rechazada, sobre el primer anticipo**: nueva solicitud sobre
    `REC-2026-000001`; "Rechazar" sin motivo bloquea, con motivo rechaza ("sin 'No se puede
    deshacer'"); el pago sigue `CONFIRMADO`; la cajera ve el rechazo y su motivo en la ficha, y
    puede "Solicitar de nuevo". Paso 15. Hallazgo: **B70** (confirmado en pantalla: la
    autorizada queda sin motivo de la decisión, la rechazada sí lo tiene).
21. **Cancelación total: ficha, confirmación y éxito (25/10/2026, 10:54–10:55)**: "Cancelar
    todo 1.809.330 — en efectivo 1.809.300" (ya con 2 días de interés + IVA sobre los
    1.806.750 Gs. del 23/10); con "Cancelar todo" seleccionado, siguen visibles el selector
    "1/2/3 cuotas" y "Si adelanta…"; confirmación "Sí, cancelar todo"/"Cancelar"; éxito con los
    3 comprobantes para imprimir; préstamo "Finalizado"; nueva fila "Cancelación total" con
    "Solicitar reversión". Paso 16. Hallazgos: **B74** (selector y "Si adelanta…" siguen
    visibles sin sentido), **B3 (peor caso)** ("Sí, cancelar todo" junto a "Cancelar"), **C35**
    (verificado en el código: la reversión queda bloqueada por diseño una vez que el cierre
    documental esté `COMPLETO`; implicancia fiscal para el contador sobre "Nota de anulación"
    vs. "Nota de Crédito").
22. **Factura, recibo completo y constancia de cancelación impresos (25/10/2026, 11:16–11:20)**:
    primera factura real de esta base (2.580 Gs. = 2.345 interés + 235 IVA), recibo con el
    desglose por cuota (1/11 a 11/11) y constancia `CAN-2026-000001` con la liquidación
    completa. Paso 17. Hallazgos: **B73** (desglose solo después de confirmar), **B75**
    (descripción "cuota 1/11" en una cancelación anticipada), **B13 (se repite)** (dirección con
    coma duplicada, ahora en un documento fiscal), **C36** (revisión legal/SET-SIFEN de la
    factura), **C37** (criterio del "Son:" con redondeo perdonado), **C38** (devolución del
    pagaré: verificado en el código, queda recorrer en pantalla — ver el paso siguiente).
23. **Cierre documental de `CC-2026-00002` (25/10/2026, 11:35–11:37)**: primera vez en todo el
    proyecto que se recorre RF29 en pantalla — constancia de devolución `DEV-2026-000001`,
    escaneo firmado cargado, "Marcar cierre documental completo". Detalle completo, con sus
    hallazgos (**B76**, **B77**, **B78**) y el cierre de **C38** (pasa a "Cubierto"), en
    `04_formalizacion.md` § "Tercera pasada de formalización: cierre documental (25/10/2026)".

**Qué sigue**: con el ciclo de cobro completo (dos adelantos, uno revertido, una reversión
rechazada, una cancelación total con sus tres comprobantes, y el cierre documental del
préstamo), `CC-2026-00002` queda `FINALIZADO`, con `estado_cierre_documental = COMPLETO` y sin
ninguna cuota pendiente. Lo que sigue de este tramo queda, ahora sí, limitado a los caminos que
**requieren una fecha futura** (cobro de cuota realmente vencida, varias cuotas vencidas, pago
parcial de una cuota vencida, mora, y el vencimiento anticipado de la cláusula OCTAVA, que
además no tiene ningún mecanismo en el código) — descartados por decisión de Joel (23/10/2026,
ver "Pendiente de decisión de Joel" en `00_LEEME.md`). Ver "Completitud del tramo 5" abajo para
el detalle camino por camino.

## Completitud del tramo 5

Control de completitud contra el código (solo lectura, verificado hoy 23/10/2026): todos los
caminos que existen en Cobranza (lista de trabajo, deuda exigible, cobro, adelanto, cancelación,
reversión y comprobantes), marcando cuáles se recorrieron en pantalla, cuáles quedan pendientes
sin necesitar fecha, y cuáles requieren una fecha futura (descartados por decisión de Joel).

| Camino | Existe en el código | ¿Se recorrió en pantalla? |
|---|---|---|
| Lista de trabajo (en mora / vencen hoy / próximos 7 días) | `ServicioMora.listarPrestamosEnMora` + `ServicioReportes.listarVencimientos` | **Sí** — Paso 1 (las 3 secciones en 0 para este cliente) |
| Búsqueda por cédula/nombre/operación | `ServicioCobranza.buscarParaCobranza` | **Sí** — Paso 1 |
| Deuda exigible con nada vencido | `ServicioCobranza.calcularMontoCobrableNormal` = 0 Gs. | **Sí** — Paso 2 ("Sin cuotas vencidas.") |
| **Cobro de una cuota realmente vencida** | `ServicioCobranza.confirmarCobro`, `TipoPago.NORMAL` | **No recorrido: requiere fecha futura, descartado por decisión de Joel (23/10/2026)** |
| **Cobro de varias cuotas vencidas a la vez** | Mismo método, `calcularMontoCobrableNormal` suma todo lo vencido + la que vence hoy | **No recorrido: requiere fecha futura, descartado por decisión de Joel** |
| **Pago parcial** (no cubre toda la deuda exigible) | `TipoPago.PARCIAL`, mismo `confirmarCobro` | **No recorrido: requiere fecha futura (una cuota vencida), descartado por decisión de Joel** |
| **Mora** (moratorio, punitorio, alerta de garante) | `ServicioMora`/`ServicioMotorFinanciero.calcularMora`, cláusula QUINTA | **No recorrido: requiere fecha futura, descartado por decisión de Joel** |
| Adelanto a capital sin interés devengado (mismo día del desembolso) | `confirmarAnticipoCapital`, `liquidacion.getInteresDevengado() = 0` | **Sí** — Pasos 2-3, con `CC-2026-00002` |
| Adelanto a capital con interés devengado (días ya transcurridos) | Mismo método, `díasTranscurridos > 0` | **No recorrido en pantalla hoy** — ejemplo ya documentado de otra prueba (Mirtha, `RF27_adelantar_capital.md`, interés 2.286 Gs. + IVA 229 Gs.), citado en `06_comprobantes_recibo_factura.md` § 7 |
| Selector "1/2/3 cuotas" del adelanto | Pura presentación, multiplica la misma próxima cuota | **No recorrido en pantalla** — verificado solo en el código (Paso 2, "Verificado en el código") |
| **Cancelación total anticipada** | `ServicioCobranza.confirmarCancelacionTotal`, constancia `CAN-aaaa-nnnnnn` | **Sí — recorrida el 25/10/2026.** `CC-2026-00002` canceló su saldo de 1.806.750 Gs. con la fecha real; emitió recibo (`REC-2026-000003`), factura (`001-001-0000001`, por el interés de 2 días devengado) y constancia (`CAN-2026-000001`) — ver `06_comprobantes_recibo_factura.md` § "Por qué recibo, factura y constancia de cancelación" y los Pasos 16-17 |
| **Reversión de un cobro, ciclo completo** (solicitud → pendiente → autorización → caja → comprobantes → verificación final) | `ServicioReversiones.solicitarReversion`/`.autorizarReversion` | **Sí, recorrido en pantalla de punta a punta** — Pasos 8-14, ver "Recorrido completo de la reversión" (`REC-2026-000002`: solicitada, autorizada, 192.850 Gs. devueltos, plan restaurado y confirmado en la ficha) |
| **Rechazar una reversión** | `ServicioReversiones.rechazarReversion` | **Sí** — Paso 15, rechazo de la solicitud sobre `REC-2026-000001` |
| **Pago de más / vuelto** | `registrarMovimientosCobro`, movimiento `COBRO` por lo recibido + `VUELTO` por la diferencia | **Sí, recorrido en pantalla de punta a punta** — Pasos 6 y 7, segundo anticipo de `CC-2026-00002` (recibido 200.000, aplicado 192.850, vuelto 7.150: confirmado en el recibo, en la base con `SELECT` y en `/caja` con los dos movimientos separados) |
| **Factura con conceptos gravados** | `ServicioCobranza.correspondeEmitirFactura(totalGravado > 0)` | **Sí — recorrida el 25/10/2026**, vía la factura de la cancelación total (`001-001-0000001`, 2.580 Gs. gravados: interés de 2 días transcurridos + IVA) — ver Paso 17 |
| **Vencimiento anticipado** (cláusula OCTAVA, 2 cuotas vencidas) | Sin ningún enforcement en código (confirmado en `06_cobro_mapa_completo.md` § 13) | **No recorrido: requiere fecha futura, descartado por decisión de Joel** — y, además, no hay ningún mecanismo que recorrer aunque hubiera fecha |
| Reimpresión del recibo (normal, cacheado) | `obtenerPdfRecibo`/`obtenerOGenerarPdf`, mismo PDF cacheado siempre | **Sí** — Paso 7 (B68, el mismo recibo con dos botones "Reimprimir") |
| Reimpresión del recibo de un pago ya revertido (anulado) | `obtenerPdfRecibo`, se salta el caché y regenera con `REVERTIDO = true` | **Sí** — Paso 13 (franja "ANULADO POR REVERSIÓN", C33 cubierto) |
| Acceso por URL directa de un rol sin permiso (ej. oficial o gerente a `/cobranza`) | `FiltroAutorizacion` exige `COBRAR` (TOTAL solo cajera) | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Cobranza) |
| **Cierre documental / devolución del pagaré** (RF29) | `ServicioFormalizacion.generarConstanciaDevolucion`/`.completarCierreDocumental` | **Sí — recorrida el 25/10/2026**, primera vez en todo el proyecto — `DEV-2026-000001`, ver `04_formalizacion.md` § "Tercera pasada de formalización: cierre documental" |

**Resumen**: tras esta ronda, los únicos caminos que quedan **sin recorrer en este tramo son los
que requieren una fecha futura** (cobro de cuota realmente vencida, varias cuotas vencidas, pago
parcial, mora, vencimiento anticipado) — descartados por decisión de Joel (23/10/2026). Quedan,
aparte, tres caminos menores sin recorrer **sin que dependan de la fecha**: el adelanto con
interés devengado con este cliente específico (ya hay un ejemplo real de otra prueba, citado
arriba), el selector "1/2/3 cuotas" (verificado solo en el código) y el acceso por URL directa de
un rol sin permiso a `/cobranza` (patrón ya probado en otras escenas).

---

## Paso 18 — Pantalla nueva del cobro: "¿Cuánto quiere pagar el cliente?" (27/10/2026)

**Registro de lo observado (27/10/2026, cajera Sofía Martínez, `CC-2026-00001`).** Esta es la
primera pasada de la **ampliación de alcance pedida por el tutor** — ver
`FASE2_PLAN.md` § "AMPLIACIÓN EXPLÍCITA DE ALCANCE (27/10/2026)" y
`docs/funcionamiento/RF27_adelantar_capital.md` §2a. La fila "Selector 1/2/3 cuotas" de la tabla
de arriba **quedó superada**: ese selector ya no existe.

El selector de "1/2/3 cuotas" se reemplazó por una **cantidad libre de cuotas** (`−` / `+` y una
casilla para tipear), más una opción **"Por monto"** y la opción **"Todo el préstamo"** (la
cancelación total de siempre). El monto de "N cuotas" pasó a ser el **mínimo que deja exactamente
K − N cuotas**, no "N × la cuota".

### Lo que vio Joel en pantalla

| Qué probó | Qué mostró |
|---|---|
| **Por cantidad de cuotas, N = 1** | "Paga 1 cuota: **234.600 Gs.**"; próxima cuota **21/11/2026** por **288.539**; **le quedan 11**; "se ahorra **55.319**" |
| **N = 4** | **932.550 Gs.**; próxima **287.369**; **le quedan 8**; se ahorra **227.126** |
| **N = 11** (tipeado directo en la casilla) | **2.732.800 Gs.**; próxima **283.397**; **le queda 1**; se ahorra **456.309** |
| **Por monto, campo vacío** | Desaparece la vista previa; *"Escriba el monto que trae el cliente."*; "Cobrar" en gris |
| **Por monto, 1.000.000** | Le quedan **8 cuotas**; próxima **287.256**; capital **988.935**; saldo **2.011.065** |
| **Por monto, 3.500.000** | *"El monto cubre todo el saldo del préstamo. Use Cancelación total."*; "Cobrar" en gris |
| **Todo el préstamo** | **3.011.065 Gs.** (en efectivo **3.011.050**) — sin cambios |

### El cobro real (N = 1)

Cartel *"Confirmar: Adelantar a capital"* (234.600, sin vuelto, 11 cuotas) → *"Anticipo de capital
aplicado (234.600 Gs.)"*. **Recibo `REC-2026-000005`**: capital 223.535 + interés 10.059 + IVA
1.006; 11 cuotas restantes, **último vencimiento 21/09/2027**, saldo 2.776.465. **Factura
`001-001-0000002`**: interés 11.065 Gs., IVA 10 % 1.006. La ficha se actualizó sola a "de 11
cuotas que le quedan".

<!-- Captura pendiente (Joel): la tarjeta "Cobranza" con las tres opciones y N = 4. -->
<!-- Captura pendiente (Joel): "Por monto" con el campo vacío (sin vista previa). -->
<!-- Captura pendiente (Joel): recibo REC-2026-000005 y factura 001-001-0000002. -->

### Lo que esta prueba dejó corregido (ya reprobado OK, ver más abajo)

Dos cambios de **texto**, aprobados por Joel y aplicados el mismo día (**confirmados en pantalla
en la segunda prueba**, más abajo):

1. El mensaje de bloqueo decía *"...Use Cancelación total."*, pero en esta pantalla ese botón se
   llama **«Todo el préstamo»**: la cajera leía el nombre de un botón que no existe. Ahora dice
   *"El monto cubre todo el saldo del préstamo. Elija «Todo el préstamo»."* Se traduce en el
   controlador (`conNombresDeEstaPantalla`), **sin tocar el texto de la excepción del motor**,
   que protege a cualquier llamador (D-90).
2. Con el monto bloqueado, **"Vuelto"** mostraba *"Sin vuelto (0 Gs.)"* como si el cobro fuera a
   salir. Ahora muestra **"—"**, igual que con el campo vacío.

### No probado en pantalla en esta pasada

- **N = K ("Todo el préstamo" desde el control de cantidad)**: no se probó para no dejar
  `CC-2026-00001` en `FINALIZADO`. Verificado por código y con JUnit. **Sigue pendiente** — ver la
  lista del final del Paso 18.
- **Doble clic en "Cobrar"**: no se probó en esta pasada. **Probado OK en la quinta pasada**, más
  abajo (`REC-2026-000006`, una sola fila en el historial).
- **El caso con cuotas vencidas (pasos 12-16)**: verificado **solo por código**, por decisión de
  Joel de no cargar ni modificar préstamos en la base. **Sigue pendiente** — ver
  `RF22_cobro_efectivo.md` §10 con los cinco pasos que quedan por ver en pantalla.

### Segunda prueba en pantalla (27/10/2026, tras el Clean and Build) — error reproducido

**Las dos correcciones de texto quedaron bien**: con 3.500.000 en "Por monto", la pantalla dice
*"El monto cubre todo el saldo del préstamo. Elija «Todo el préstamo»."*, "Vuelto" muestra **"—"**
y "Cobrar" queda en gris.

**El estado inicial también quedó coherente** (`CC-2026-00001`, ya con 11 cuotas restantes después
del cobro de la primera pasada): al abrir la ficha, *"Paga 1 cuota: 228.050 Gs."*, casilla 1, le
quedan 10, Recibido 228.050, sin vuelto. Con `+` hasta 3: *"Paga 3 cuotas: 697.950 Gs."*, casilla
3, le quedan 8, Recibido 697.950. Al tocar "Por monto" sin escribir nada: Recibido 697.950, le
quedan 8.

**ERROR encontrado por Joel — pantalla contradictoria.** Secuencia exacta: **N = 3 → "Por monto" →
escribir 3.500.000 en Recibido (bloqueo correcto) → volver a "Por cantidad de cuotas"**. Quedaba,
todo al mismo tiempo:

| Pieza de la pantalla | Qué mostraba | A qué cantidad correspondía |
|---|---|---|
| Tarjeta | "Paga 3 cuotas: 697.950 Gs." | N = 3 |
| Casilla de cantidad | 1 | N = 1 |
| Vista previa | "…21/11/2026 por 289.011 Gs.; le quedan 10 cuotas." | N = 1 |
| Recibido / Vuelto | 3.500.000 / 3.271.950 Gs. (= 3.500.000 − 228.050) | N = 1 |
| "Cobrar" | **habilitado** | — |

**Qué habría hecho el servidor si se tocaba "Cobrar": rechazar todo, sin cobrar nada.** El campo
oculto `montoSugerido` había quedado en **3.500.000** (lo dejó "Por monto" y nada lo volvió a
escribir), así que `procesarAnticipar` calculaba `min(3.500.000, 3.500.000)` y
`ServicioMotorFinanciero` lo rechazaba con **D-90**. Pero el cartel de confirmación iba a mostrar
**228.050 con vuelto 3.271.950**: el cartel decía una cosa, el formulario mandaba otra y el
servidor hacía una tercera. **El dinero estaba protegido** (el servidor nunca confía en la vista
previa, D-145); la pantalla, no.

**Causa raíz** (una sola, en `cobranza-ficha.js`): al volver a "Por cantidad de cuotas",
`seleccionar` llamaba a `seleccionarCuotas(1)`, que pedía `?cuotas=1`; pero inmediatamente después
`recalcular()` detectaba que el monto aplicado había cambiado (3.500.000 → 228.050) y disparaba un
**segundo** pedido `?monto=228050` que **cancelaba el primero** (`clearTimeout`). Un pedido por
monto no trae `#fragMonto`, así que la tarjeta no se actualizaba nunca y quedaba con el texto de
la cantidad anterior.

### Corrección aplicada (pendiente de la tercera prueba de Joel)

Todo en `cobranza-ficha.js` (`ficha.jsp` no hizo falta):

1. **Volver a "Por cantidad de cuotas" conserva la cantidad elegida** (antes volvía a 1), y le
   pide al servidor el monto de **esa** cantidad.
2. **Las cinco piezas se actualizan juntas**, en un solo lugar: la respuesta del servidor para
   `?cuotas=N` escribe tarjeta, monto elegido, `montoSugerido`, "Recibido" y (vía el recálculo) el
   vuelto. Ya no hay ningún camino que actualice unas y no otras.
3. **Mientras el servidor calcula** (`esperandoMontoDeCuotas`): la tarjeta dice *"Paga N cuotas:
   calculando…"*, la vista previa de la cantidad anterior **se oculta**, "Vuelto" muestra "—" y
   "Cobrar" queda en gris. No se dispara ningún otro pedido que pueda cancelar al de la cantidad.
4. **Al cambiar de opción, "Recibido" se rellena con el monto de la opción nueva** — arrastrar el
   monto de la opción anterior era la otra mitad del problema. Dentro de una misma opción se sigue
   respetando lo que la cajera escribió a mano (regla del 13/10/2026), y **con cuotas vencidas no
   cambia nada** (ahí la regla del 13/10/2026 sigue tal cual).
5. **`montoSugerido` se escribe al confirmar**, desde el mismo monto con el que se armó el cartel,
   en vez de confiar en que alguien lo haya dejado al día. Así queda garantizado por construcción
   que lo que el servidor aplica — `min(montoRecibido, montoSugerido)` — es exactamente el monto
   que la cajera leyó y confirmó.

**Transiciones revisadas una por una** (cantidad ↔ monto ↔ todo el préstamo, en los dos sentidos,
con el campo escrito, vacío y bloqueado; más N = K y varios `+` seguidos): en todas, cuando
"Cobrar" está habilitado, tarjeta, casilla, vista previa, Recibido, vuelto y `montoSugerido`
describen la misma operación. **`cobranza-ficha.js` no tiene pruebas automáticas** en este
proyecto, así que esto se verifica en pantalla, no con JUnit.

### Tercera prueba en pantalla (27/10/2026) — la secuencia quedó corregida, y apareció otra

**Probado por Joel**, mismo préstamo (11 cuotas restantes). **La secuencia del error anterior
quedó CORREGIDA**: N = 1 coherente → `+` hasta 3 (*"Paga 3 cuotas: 697.950"*, casilla 3, le quedan
8, Recibido 697.950, **interés devengado 0** porque ya se había cobrado ese mismo día, saldo
2.078.515) → "Por monto" (Recibido 697.950, le quedan 8) → escribir 3.500.000 (bloqueo
*"…Elija «Todo el préstamo»."*, "Vuelto —", "Cobrar" en gris) → volver a "Por cantidad de cuotas":
**todo en N = 3** (tarjeta 697.950, casilla 3, le quedan 8, Recibido 697.950, sin vuelto).

**El cartel coincidió con la tarjeta**: *"Confirmar: Adelantar a capital"* — 697.950, Recibido
697.950, sin vuelto, interés 0 + IVA 0, capital 697.950, saldo 2.078.515, próxima 21/11/2026 por
288.066, quedan 8. Con **"Cancelar"**, la pantalla quedó igual y no se cobró nada.

### Segundo error (misma familia): Recibido menor que el monto de N cuotas

**Encontrado por Joel.** En "Por cantidad de cuotas" con N = 3 (697.950), al escribir **500.000**
en Recibido quedaba:

| Pieza | Qué mostraba | A qué correspondía |
|---|---|---|
| Tarjeta | "Paga 3 cuotas: 697.950 Gs." | 3 cuotas |
| Vista previa | "le quedan 9 cuotas", capital aplicado 500.000 | ≈ 2 cuotas |
| Vuelto | "Sin vuelto" | — |
| "Cobrar" | **habilitado** | — |

La tarjeta decía 3 cuotas y el cobro real habría sido de 500.000. **Causa**: el recálculo pedía la
vista previa del monto recibido — el comportamiento de D-145 (P-5), "se puede adelantar menos que
lo sugerido" — que en esta opción contradice el rótulo de la tarjeta.

**Corregido (solo pantalla).** En "Por cantidad de cuotas", si lo recibido no alcanza para esa
cantidad: **"Cobrar" en gris**, "Vuelto" en **"—"**, **no se pide ninguna vista previa por ese
monto** (así tarjeta y vista previa siguen siendo las de la cantidad elegida) y se muestra el
motivo exacto — *"Lo recibido (500.000 Gs.) no alcanza para 3 cuotas (697.950 Gs.). Elija menos
cuotas o use «Por monto»."* Con Recibido ≥ el monto, el vuelto funciona como siempre.

**El servidor también lo rechaza** (Joel dio el OK el 27/10/2026 y ya está aplicado): antes
`procesarAnticipar` aplicaba `min(montoRecibido, montoSugerido)` (D-145 P-5) y no sabía qué opción
había elegido la cajera, porque el mismo `POST /anticipar` sirve a las dos. Ahora el formulario
manda un `modoAnticipo` oculto (`"cuotas"` / `"monto"`, que escribe `cobranza-ficha.js` al
confirmar) y, cuando es `"cuotas"` y lo recibido es menor que lo sugerido, la operación se rechaza
completa con el mismo aviso que muestra la pantalla, **sin cobrar nada**. **"Por monto" quedó
igual**: ahí el `min()` es justamente lo que la hace funcionar. Sin tocar `ServicioCobranza` ni el
motor; con **prueba JUnit** sobre dos helpers puros (`recibidoNoAlcanzaParaLasCuotas` y
`mensajeNoAlcanzaParaLasCuotas`).

Dos detalles de ese control, para que no sorprendan al leer el código:

- Un `modoAnticipo` **ausente, vacío o manipulado** se trata como "no es la opción por cantidad" y
  **no rechaza nada**: el control nunca endurece de más un camino que ya funcionaba.
- El mensaje del servidor dice los dos montos pero **no** la cantidad de cuotas. Esa cantidad solo
  existe en el navegador, y traerla en el formulario sería meter un texto manipulable dentro de un
  mensaje que se muestra en pantalla; los dos montos sí vienen del formulario, pero pasan por
  `parsearMonto` y se vuelven a formatear desde un número. Por eso el texto del servidor es *"Lo
  recibido (X) no alcanza para **la cantidad de cuotas elegida** (Y). Elija menos cuotas o use
  «Por monto»."*, mientras el de la pantalla sí nombra las cuotas.

**Dos mejoras de texto pedidas por Joel y aplicadas**: el ahorro dice *"El cliente se ahorra X Gs.
de intereses por pagar por adelantado."*, y el cartel de confirmación arranca con **"Paga N
cuotas"** — las mismas palabras que eligió la cajera.

### Cuarta prueba en pantalla (27/10/2026) — **OK, cierra el Paso 18**

**Probado por Joel**, mismo préstamo (11 cuotas restantes), después del Clean and Build:

| Qué probó | Qué mostró |
|---|---|
| **N = 1** (228.050), Recibido **500.000** | Vuelto **271.950**, "Cobrar" habilitado · *"El cliente se ahorra **60.489 Gs.** de intereses por pagar por adelantado."* |
| **N = 3** (697.950), Recibido **500.000** | En rojo *"Lo recibido (500.000 Gs.) no alcanza para 3 cuotas (697.950 Gs.). Elija menos cuotas o use «Por monto»."* · Vuelto **"—"** · "Cobrar" **en gris** · **la tarjeta y la vista previa siguen en 3 cuotas / le quedan 8** |
| Bajar con **−** a **N = 2** (460.650) | El aviso **desaparece solo** · le quedan **9** · vuelto **39.350** · "Cobrar" habilitado · ahorro **116.428** |
| Cartel (N = 2) | **"Paga 2 cuotas"** en negrita arriba; Adelantar a capital 460.650, Recibido 500.000, Vuelto 39.350, saldo 2.315.815, próxima 21/11/2026 por 288.543, quedan 9 |
| **"Cancelar"** | No se cobró nada |
| La secuencia del primer error (cantidad → monto 3.500.000 → cantidad) | **Corregida**: todo coherente en N = 3, y el cartel coincide con la tarjeta |

**Con esto el Paso 18 queda cerrado**: la pantalla nueva del cobro está recorrida y probada, y los
dos errores de coherencia de las pasadas anteriores quedaron verificados como corregidos.

<!-- Captura pendiente (Joel): la secuencia N=3 → Por monto → 3.500.000 → Por cantidad de cuotas, ya corregida. -->
<!-- Captura pendiente (Joel): N=3 con Recibido 500.000, con "Cobrar" en gris y el motivo. -->
<!-- Captura pendiente (Joel): el cartel con "Paga 2 cuotas" arriba. -->

### Quinta prueba (27/10/2026) — idempotencia con un doble clic real, OK

**Probado por Joel**, mismo préstamo (11 cuotas), cajera Sofía Martínez. **Primera vez en todo el
proyecto que se prueba un doble clic real sobre la confirmación de un cobro** — hasta ahora la
idempotencia estaba verificada solo por código y con pruebas unitarias.

- **Doble clic rápido sobre "Sí, adelantar a capital"** con N = 1 (**228.050 Gs.**): **un solo**
  cartel — *"Anticipo de capital aplicado (228.050 Gs.)"* —, recibo **`REC-2026-000006`** y
  *"Sin factura (no hay conceptos gravados)"*. Lo de la factura es correcto, no un hueco: el
  interés del día ya se había cobrado en `REC-2026-000005`, así que el interés devengado de este
  segundo adelanto era 0 y no quedaba nada gravado que facturar (RF23).
- **Historial: UNA sola fila nueva** (07:52). **No se duplicó.**
- **F5 después de "Aceptar"**: el navegador **no** preguntó por reenviar el formulario; el
  historial siguió con 2 filas (07:52 `REC-2026-000006` 228.050 y 06:32 `REC-2026-000005`
  234.600). Es el redirect-después-de-POST de D-89 funcionando.
- Después del cobro quedan **10 cuotas** y la ficha pasó a *"Paga 1 cuota: 232.600 Gs."*

<!-- Captura pendiente (Joel): el historial con las dos filas tras el doble clic. -->

### Sexta pasada (27/10/2026, noche) — el acomodo nuevo de la tarjeta, solo presentación

**Probado por Joel**, mismo préstamo con **10 cuotas restantes**, tras el Clean and Build y un
**Ctrl+F5** (hacía falta: cambiaron `sgcpc-tema.css` y `cobranza-ficha.js`, que el navegador
cachea). Pedido de Joel, diseño aprobado por él; **no se tocó ningún cálculo, endpoint, servlet,
servicio ni control del servidor** — ver `FASE2_PLAN.md` § "Segunda parte, SOLO presentación" y
`RF27_adelantar_capital.md` §7/§9.

Las tres tarjetas gigantes apiladas pasaron a un **selector compacto en una fila** y, debajo, dos
columnas: a la izquierda lo que la cajera elige y escribe (`−` `[N]` `+`, "Recibido" con su monto
en letras, "Ver detalle del adelanto"); a la derecha **"Resumen del cobro"** como un ticket — lo
que paga arriba y en grande, después las cuotas que quedan, la próxima cuota, el ahorro y el
vuelto, y al pie los avisos en rojo y el botón. En pantalla chica las dos columnas se apilan.

| Qué probó | Qué mostró |
|---|---|
| El acomodo | Selector en **una fila**; **sin** el rótulo "ELEGIR"; "Resumen del cobro" con el mismo estilo que "Historial de cobros" |
| **Por cantidad, N = 1** | "Paga 1 cuota: **232.600**", le quedan **9**, ahorro **56.411**, botón **"Cobrar 232.600 Gs."** |
| **Por monto, 232.600** | "Adelantar a capital: **232.600**", mismo botón |
| **Todo el préstamo** | Resumen **2.548.415** / "en efectivo **2.548.400**"; botón **"Cobrar 2.548.400 Gs."** |
| **Recibido 2.500.000** | Aviso rojo *"Lo recibido (2.500.000 Gs.) no alcanza para cancelar todo el préstamo (2.548.400 Gs.)."*, **Vuelto "—"**, "Cobrar" **en gris** |
| **Recibido 2.600.000** | **Vuelto 51.600**, botón "Cobrar 2.548.400 Gs." |
| **Cartel** (sin confirmar) | *"Cobra en efectivo: 2.548.400 Gs."* / *"Cancela la deuda de: 2.548.415 Gs. (redondeo a favor del cliente: 15 Gs.)"* / Recibido 2.600.000 / Vuelto 51.600. **"Cancelar": no se cobró nada** |
| Ida y vuelta (N = 3 → Por monto 3.500.000 → Por cantidad) | **Todo coherente en N = 3**: "Paga 3 cuotas: 712.000", le quedan **7**, Recibido 712.000, botón "Cobrar 712.000 Gs.", capital 712.000, saldo 1.836.415 |
| **Zoom ampliado** | El menú lateral pasa a íconos y **nada se encima** |

**Cuatro decisiones de Joel quedaron fijadas en esta pasada**: (1) el botón dice el monto, y es
el que se cobra **en efectivo** (D-141), no el exacto de la deuda; (2) con redondeo, el cartel
abre con *"Cobra en efectivo"* y explica debajo cuánta deuda cancela y de cuánto es el redondeo a
favor del cliente — sin redondeo queda igual que antes; (3) "Todo el préstamo" **bloquea antes
del clic** si lo recibido no alcanza, igual que "Por cantidad de cuotas"; (4) los títulos, como
el resto del sistema, sin mayúsculas.

**Anotado para más adelante** (hallazgo **B79**, solo registrado): con el monto bloqueado, la
línea grande del resumen sigue mostrando el monto tecleado mientras el aviso de abajo dice que no
se puede cobrar.

<!-- Captura pendiente (Joel): la tarjeta "Cobranza" con el selector en una fila y las dos columnas. -->
<!-- Captura pendiente (Joel): "Todo el préstamo" con Recibido 2.500.000 (aviso en rojo, Cobrar en gris). -->
<!-- Captura pendiente (Joel): el cartel con "Cobra en efectivo" y "Cancela la deuda de". -->

### Pendientes explícitos del Paso 18 (no probados en pantalla)

1. **N = K** — "Todo el préstamo" por la vía del control de cantidad: no se probó para no dejar
   `CC-2026-00001` en `FINALIZADO`. Verificado por código y con JUnit. **La lógica de RF26 en sí
   ya está probada en pantalla** desde el 25/10/2026 con `CC-2026-00002` (Paso 16): lo que falta
   es solo llegar a ese mismo flujo por la vía nueva.
2. **El caso con cuotas vencidas (pasos 12-16)**: verificado **solo por código**, por decisión de
   Joel de no cargar ni modificar préstamos en la base. Los cinco pasos están listados en
   `RF22_cobro_efectivo.md` §10.
3. **C39** — la cuota programada vence el **23/11/2026** y el plan regenerado tras el adelanto
   muestra la próxima el **21/11/2026** (último vencimiento 21/09/2027). **Ya pasaba antes de esta
   ampliación.** Solo registrado: a verificar aparte contra RF27, RN-13 y el contrato.

## Hallazgos del Paso 18

- **B79 (nuevo, 27/10/2026, solo registrado).** Con el monto bloqueado por el servidor, la línea
  grande del "Resumen del cobro" sigue mostrando el monto que la cajera escribió (*"Adelantar a
  capital: 3.500.000 Gs."*) mientras el aviso rojo de abajo dice que ese monto no se puede cobrar
  y "Cobrar" está en gris. No es un error (describe lo tecleado, y nada deja cobrarlo), pero
  conviene evaluar mostrar otra cosa en esa línea. Registrado en `FASE2_PLAN.md`, área H.
- **B74 — RESUELTO.** Con "Todo el préstamo" elegido ya no quedan a la vista el selector de cuotas
  ni el "Si adelanta…": las tres formas de pagar son opciones excluyentes.
- **C28 — RESUELTO.** El monto del adelanto ya no está limitado a 1/2/3 cuotas: la cantidad es
  libre y además existe "Por monto". "Saldo a favor" sigue sin existir, y no se agregó (RN-21).
- **C23 — sigue abierto, atenuado.** La pantalla ya no habla de "pagar la cuota N" sino de cuántas
  cuotas salen del plan, y dice siempre cuántas quedan, por cuánto será la próxima y cuánto se
  ahorra de intereses. El motor no se tocó: las cuotas siguen sin "tacharse" una por una.
**29/10/2026, 23:46-23:47 — lo que vi: el texto de "Por cantidad de cuotas" (B92).** En la ficha
de `CC-2026-00005`, con 1 cuota la tarjeta dice *"Adelanta 1 cuota (sin intereses futuros):
232.600 Gs."* y con 3, *"Adelanta 3 cuotas (sin intereses futuros): 711.950 Gs."*; el cartel de
confirmación arranca con la misma frase. No confirmé el cobro. Antes decía *"Paga N cuotas"*, que
invitaba a compararlo con la cuota del plan — y a mí me pasó. Estado del ítem en
[FASE2_PLAN.md](FASE2_PLAN.md).

**29/10/2026, 23:31-23:39 — lo que vi: el recibo de talonario (B83/B58).** Un adelanto de
228.050 sobre `CC-2026-00005` emitió **`REC-2026-000014`** con el diseño nuevo: recuadro de
emisor, celda *"RECIBO DE DINERO / N.º REC-2026-000014 / Documento interno — no fiscal"*, datos
del cobro en renglones enmarcados, cuerpo y firmas en recuadros, y al pie **ORIGINAL — CLIENTE /
COPIA — ARCHIVO CAPITAL CRÉDITO**. Los números, correctos (próxima cuota 30/11/2026 por 289.011,
10 cuotas, último vencimiento 30/08/2027, saldo 2.548.400). La reimpresión de `REC-2026-000013`
sale con el **diseño viejo**, como corresponde (D-94). Se nota un espacio vacío dentro del
recuadro del anticipo: anotado para B86. Estado del ítem en [FASE2_PLAN.md](FASE2_PLAN.md).

**29/10/2026 — lo que vi: C39 corregido en pantalla.** Con `CC-2026-00005` (ancla sábado
28/11/2026, cobro lunes 30/11), **antes** del arreglo la vista previa del adelanto decía *"Su
próxima cuota será el 28/11/2026"* mientras la lista de Cobranza decía *"1/12 — vence
30/11/2026"*. **Después**: vista previa y confirmación dicen **30/11/2026** por 289.470 Gs., y el
adelanto de 223.550 (cajera Sofía Martínez, 21:06) emitió **`REC-2026-000013`** con *"Próxima
cuota (IVA incluido): 30/11/2026 por 289.470 Gs."*, *"nuevo último vencimiento 28/09/2027"* y
saldo 2.776.450 — *"Factura asociada: no corresponde (sin conceptos gravados)"*, porque el
adelanto a capital puro no tiene interés gravado. Estado del ítem en
[FASE2_PLAN.md](FASE2_PLAN.md).

- **C39 (nuevo, solo registrado — NO corregir).** La cuota programada vence el **23/11/2026**,
  pero el plan regenerado tras el adelanto muestra la próxima el **21/11/2026** y el último
  vencimiento el **21/09/2027**. **Ya pasaba antes de esta ampliación** — no lo causó este cambio.
  A verificar aparte contra `RF27_adelantar_capital.md`, **RN-13** y el contrato antes de tocar
  nada (`ServicioMotorFinanciero.regenerarPlanAnticipo`, `fechaTeoricaInicio`/`fechaHabil`).

## 30/10/2026 — lo que vi en la pantalla de cobro

Registro con fecha (el estado de cada ítem vive en [FASE2_PLAN.md](FASE2_PLAN.md), no acá).

- **07:03 y 07:40, cajera Sofía, `CC-2026-00005`, sin cobrar.** Con un monto que el sistema no puede adelantar, la línea grande del *"Resumen del cobro"* ya no afirma ese monto: dice `—` y el motivo queda en rojo arriba de *"Cobrar"* (3.500.000 → *"cubre todo el saldo"*; 223.575 → *"múltiplos de 50"*; 1.000 → *"El mínimo para adelantar hoy es 1.880 Gs."*). En la primera pasada vi dos cosas que faltaban —el mínimo seguía dejando el monto en la línea grande, y debajo quedaban la próxima cuota y el *"Capital aplicado"* **del monto anterior**— y se corrigieron el mismo día. También vi que **10.000 no se bloquea**, y está bien: el mínimo real es 1.880 (interés de un día 1.709 + IVA 171). Ítems [B79](FASE2_PLAN.md) y [B81](FASE2_PLAN.md).
- **07:55, con una cuota vencida** (conseguida con el corrimiento de fechas de D-81, −35 días, y devuelta el mismo día): escribiendo 100.000 contra 289.883 exigibles, la tarjeta dice **"Cobra 100.000 Gs. (pago parcial)"** y debajo *"de 289.883 Gs. exigibles — queda pendiente 189.883 Gs."*; *"Vuelto"* dice *"Sin vuelto (0 Gs.)"*. Antes la línea más grande mostraba los 289.883. El desglose de la deuda exigible (capital 232.616, interés 51.268, moratorio 610, punitorio 183, IVA 5.206) **coincidió con el número calculado y sellado antes de abrir la pantalla**.
- **Con el campo de fecha de promesa vacío y sin tocarlo, ya aparece en rojo** *"La fecha de promesa debe ser posterior a hoy"*, como si la cajera hubiera hecho algo mal. Anotado como [B95](FASE2_PLAN.md); no se corrigió hoy.
- **Escribir `00005` en el buscador de Cobranza encuentra `CC-2026-00005`** (antes hacía falta el número completo). Al probarlo vi que la **barra de direcciones no acompaña** lo que se busca: queda la búsqueda anterior. Es como está hecho el listado vivo, no un defecto de esta pantalla; anotado como [B97](FASE2_PLAN.md). Ítems [B82](FASE2_PLAN.md) y [B93](FASE2_PLAN.md).

- **09:08-09:10, mismo préstamo con el corrimiento aplicado otra vez, sin cobrar.** Con 100.000 escritos y **sin elegir la fecha de promesa**, el aviso rojo ya no aparece: el pedido sale **en gris** arriba de *"Cobrar"* (*"Elija la fecha de promesa para habilitar «Cobrar»"*). Escribiendo la fecha de **hoy** sí sale el rojo *"La fecha de promesa debe ser posterior a hoy"*, y con el **15/11/2026** no queda ningún aviso y el botón se habilita diciendo *"Cobrar 100.000 Gs."*. El cartel de confirmación dice ahora lo mismo que la tarjeta —*"Cobra 100.000 Gs. (pago parcial)"* y *"de 289.883 Gs. exigibles — queda pendiente 189.883 Gs."*— con la promesa debajo y el botón **"Sí, cobrar 100.000 Gs."**; lo cerré con **Cancelar**, que no manda nada. Ítems [B95](FASE2_PLAN.md) y [B96](FASE2_PLAN.md).
- **Observación mía sobre los carteles**: el foco inicial queda en el botón de confirmar, así que un **Enter cobra** sin leer el cartel. Anotado como candidato de [B86](FASE2_PLAN.md) (foco en *"Cancelar"* en los carteles que mueven dinero); no se tocó.

- **10:16-10:25, con la cuota 1 venciendo HOY** (corrimiento de −31 días del paso 15, devuelto el mismo día). Lo que vi **antes** del arreglo: la ficha decía *"Deuda exigible hoy: Sin cuotas vencidas."* y al mismo tiempo *"Pagar lo vencido"*, *"Primero hay que cobrar lo vencido…"*, *"Pagar lo vencido: 289.011 Gs."* y, en el cartel, *"Se cobra lo vencido. Lo que sobra (100.000 Gs.)…"*; además, escribiendo 100.000 el botón decía *"Cobrar 100.000 Gs."* pero el número grande se quedaba en 289.011. **Después**: *"Pagar la cuota de hoy"*, *"Hoy vence la cuota 1/10. Adelantar por cantidad de cuotas o por monto queda disponible cuando no haya ninguna cuota para cobrar."*, *"Pagar la cuota de hoy: 289.011 Gs. / en efectivo: 289.000 Gs."*; con 100.000, *"Cobra 100.000 Gs. (a cuenta de la cuota de hoy)"* + *"de 289.011 Gs. — quedan 189.011 Gs., sin mora hasta mañana"*; y los carteles *"Cobra la cuota 1/10 de hoy: 289.000 Gs."* / las mismas dos frases, con los botones *"Sí, cobrar la cuota de hoy"* y *"Sí, cobrar 100.000 Gs."*. **En la primera pasada (10:16) la línea grande todavía decía "Pagar lo vencido"** hasta con Ctrl+F5: se corrigió el mismo día. Ítem [B98](FASE2_PLAN.md).
- **El panel de Sofía**, que no decía nada de su trabajo del día, ahora abre con **"1 — Vencen hoy (289.011 Gs.)"** y el enlace *"Ir a Cobranza"* lleva a la lista con *"Vencen hoy 1"*. Ítem [B99](FASE2_PLAN.md).
- **No se cobró nada**: los carteles se cerraron con Cancelar y el corrimiento se deshizo con la VUELTA (+31). El cobro real de una cuota que vence hoy es el paso 15 (b), con otro préstamo.

- **22:21-22:26 (paso 15 b), cajera Sofía, `CC-2026-00004` con la cuota 1 venciendo HOY** (corrimiento de −31 días, **este préstamo no vuelve**). **Primer cobro real de una cuota del día en todo el proyecto, y salió idéntico a la predicción sellada antes de abrir la pantalla.** Panel *"1 — Vencen hoy (180.609 Gs.)"* con la caja en 0; lista *"Vencen hoy"* con 1/6 por 180.609 al 30/10/2026; ficha *"Sin cuotas vencidas"* + *"Pagar la cuota de hoy: 180.609 Gs. / en efectivo: 180.600 Gs."* + *"Todo el préstamo · 1.023.605 Gs."*; cartel *"Cobra la cuota 1/6 de hoy: 180.600 Gs."*; y el mensaje *"Cobro de 180.609 Gs. aplicado. Cobrado en efectivo: 180.600 Gs. (redondeo a favor del cliente: 9 Gs.) Recibo REC-2026-000015. Factura 001-001-0000007."*. Detrás, la ficha volvió a ofrecer *Por cantidad de cuotas / Por monto* y *"Todo el préstamo · 842.763 Gs."*.
- **Los 842.763 del "Todo el préstamo" después del cobro** no son el saldo de capital (841.521): los **1.242 Gs.** de diferencia son el **interés devengado de 2 días** del período en curso más su IVA — 841.521 × 2,01177 % × 2/30 = **1.129**, IVA **113**. Los 2 días salen de que el período arranca en la **fecha teórica de la cuota 1 (28/10)**, corrida por el propio corrimiento: sin él, hoy ni siquiera estaríamos dentro del período. Es el prorrateo de RN-11/D-88, no un recargo.
- **Dos observaciones de texto en los documentos**, anotadas sin tocar nada: el *"Son:"* del recibo dice lo **aplicado** (180.609) y no lo **recibido** (180.600) → [B101](FASE2_PLAN.md); y la línea de la factura salió *"Interés compensatorio cuota 1/6"*, distinta de la que se usa al emitir para sumar los totales → [B102](FASE2_PLAN.md).
- **La caja la cierra el usuario después**: al terminar el cobro quedó en `ENTREGA_GERENTE +1.000.000` / `DESEMBOLSO −1.000.000` / `COBRO +180.600` = **saldo teórico 180.600 Gs.**


## 01/11/2026 — la primera cancelación total con redondeo

Registro con fecha (el estado de cada ítem vive en [FASE2_PLAN.md](FASE2_PLAN.md), no acá).

- **19:40-19:50, cajera Sofía, `CC-2026-00004`, caja abierta con fondo 0.** Hoy no había ninguna
  cuota vencida ni venciendo (la 2/6 vence el 27/11), así que *"Cobrar"* no estaba disponible y el
  adelanto no aplica redondeo: la única operación con redondeo era **Cancelación total**. El motor
  pidió **844.004 Gs.** (capital 841.521 + interés 2.257 + IVA 226) y se pagó con **844.000** en
  efectivo — **redondeo de 4 Gs. a favor del cliente** (D-141). El préstamo quedó **FINALIZADO**.
- **`REC-2026-000016`**: *"Son: OCHOCIENTOS CUARENTA Y CUATRO MIL GUARANÍES"* — el **efectivo
  recibido**, con *"Aplicado: 844.004 Gs. · Redondeo: -4 Gs."* en su propio renglón. Antes
  deletreaba lo aplicado. Detalle de las cuotas 2/6 a 6/6 y leyenda *"PRÉSTAMO CANCELADO"*.
  Ítem [C37](FASE2_PLAN.md).
- **`001-001-0000008`**: una sola línea, *"Interés compensatorio cuota 2/6"* por 2.483 Gs. con
  IVA 226 — las cuotas 3 a 6 recibieron solo capital, que no se factura (RN-25). Ítem
  [B102](FASE2_PLAN.md). **La dirección del cliente salió "mburicao 444"**: dato viejo, sin
  migrar, esperado — [B13](FASE2_PLAN.md) normaliza al guardar, no retroactivamente.
- **`CAN-2026-000004`**: total cancelado **844.004** (841.521 + 2.257 + 226) y
  *"Son: OCHOCIENTOS CUARENTA Y CUATRO MIL CUATRO GUARANÍES"* — **el total, no el efectivo**, y
  está bien: la constancia certifica **la deuda saldada** (D-142), no lo que entró a la caja. Con
  la Ley 6624/2020, los comprobantes `REC-2026-000016` / `001-001-0000008` y las firmas de Ramón y
  del cliente.
- **La constancia dice "Fecha de desembolso: 29/09/2026"**: es efecto del corrimiento de −31 días
  del paso 15 (b), que para este préstamo **no tuvo vuelta** por diseño. Esperado.
- **La caja queda abierta con 844.000 Gs.**
