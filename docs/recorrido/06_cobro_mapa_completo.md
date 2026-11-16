# Mapa completo del cobro tal como funciona hoy

**Prioridad alta, solo lectura** (código, `docs/funcionamiento/`, plantillas `.jrxml` y `SELECT`
en `sgcpc_db_pruebas`, contraseña leída de `bd.properties`) — pedido de Joel: el cobro es el
módulo más sensible del sistema y en la fase 2 se va a cambiar su metodología; antes de tocar
nada hay que conocer en detalle cómo funciona HOY, en todos sus aspectos.

Este documento **no agrega información nueva de cero** — consolida y referencia el análisis ya
hecho, RF por RF, en `docs/funcionamiento/RF20_lista_trabajo_cobranza.md` hasta
`RF29_cierre_documental.md` (más RF33/RF34), cruzado con el código y con `SELECT` directos sobre
`sgcpc_db_pruebas`. Cada punto dice si fue **recorrido en pantalla** (con fecha y quién lo probó),
**solo verificado en el código** (lectura/pruebas unitarias, sin pantalla), o **no recorrido**
(depende de una fecha futura, descartado por decisión de Joel del 23/10/2026).

**Dato importante de esta verificación (23/10/2026)**: la base `sgcpc_db_pruebas` **ya no tiene**
los datos de las pruebas de los RF citados (`CC-2026-00001` "Mirtha", `CC-2026-00026/00027/00029`,
etc. — `SELECT COUNT(*)` sobre `pagos`, `reversiones_pago` y `facturas` da **0 filas** hoy). La
base se recreó en algún momento entre esas pruebas (12-14/10/2026) y el ciclo del segundo cliente
de práctica de esta sesión (22-23/10/2026). Las pruebas en pantalla citadas más abajo **sí
ocurrieron de verdad** (quedaron documentadas con tag de Git y, en su momento, verificadas contra
la base) — solo que esos datos concretos ya no existen hoy para volver a mirarlos.

## 1. Pantallas y roles de Cobranza y mora

- **`/cobranza`** (`ControladorCobranzaServlet.mostrarBusqueda`, `GET`) — bandeja/búsqueda:
  - **Sin texto**: 3 tarjetas, en este orden — **"En mora"** (roja), **"Vencen hoy"** (amarilla),
    **"Próximos 7 días"** (gris) — cada una con cliente, C.I., operación, cuota, monto y botón
    **"Cobrar"**; si está vacía, un texto exacto por tarjeta ("Ningún préstamo en mora."/"Nada
    vence hoy."/"Nada vence en los próximos 7 días."). Un préstamo `EN_MORA` **nunca** se repite
    en las otras dos (`ServicioCobranza.armarListaTrabajo`, Decisión 2 de RF20).
  - **Con texto** (AJAX, `?q=...`): operación exacta → cédula/nombre/apellido entre clientes
    `ACTIVO`/`EN_MORA` → mensajes claros por caso (sin préstamo, pendiente de desembolso, anulado
    antes del desembolso, ambiguo con hasta 10 coincidencias, inexistente).
  - **Permiso**: `Permiso.COBRAR` — cajera y administrador `TOTAL`; gerente y oficial `NINGUNO`
    (403 directo, candado en el menú).
  - **Verificado**: `ServicioCobranza.buscarParaCobranza`/`resolverUnPrestamo`/
    `resolverSinPrestamoActivo`; `PersonaDAO.construirWhere` (`ILIKE`, sin acentos, sin escapar
    `%`/`_`).
  - **Recorrido en pantalla**: Sí — 12/10/2026, Joel, cajera (`RF20_lista_trabajo_cobranza.md`
    §9: 3 tarjetas vacías con sus textos exactos; búsquedas por nombre/cédula/operación/cédula
    inexistente; oficial con 403).
- **`/cobranza/{idPrestamo}`** (`ControladorCobranzaServlet.mostrarFicha`, `GET`) — ficha
  unificada de cobro, con hasta 4 tarjetas según el estado del préstamo:
  1. **"Deuda exigible hoy"** — solo si hay algo vencido (RF21); columnas Cuota/Capital/
     Interés/Moratorio/Punitorio/IVA 10 %/Total, más el total general.
  2. **Botón único de pago**, que cambia de texto según `hayAlgoParaCobrar`
     (`ServicioCobranza.calcularMontoCobrableNormal` > 0): **"Pagar cuota"** (hay algo vencido o
     que vence hoy) o **"Adelantar a capital"** (no hay nada vencido) — es el **mismo botón**,
     no dos. Junto a él, **"Cancelar todo"** (vista previa de cancelación total, siempre
     disponible si el préstamo es `ACTIVO`/`EN_MORA`).
  3. **"Historial de cobros"** — todos los pagos del préstamo (incluidos los `REVERTIDO`, con su
     marca), con **"Solicitar reversión"** solo en la fila de la operación más reciente.
  4. **Reversión**/**Confirmar desembolso** no viven acá — viven en `/reversiones` y
     `/desembolso` respectivamente.
  - **Permiso**: mismo `Permiso.COBRAR`.
  - **Recorrido en pantalla**: parcial — la tarjeta "Deuda exigible" sí (13/10/2026, Joel, cajera,
    `CC-2026-00027`, `RF21_deuda_exigible.md` §9); "Adelantar a capital" sí (12/10/2026, Mirtha,
    `RF27_adelantar_capital.md` §9); **"Pagar cuota" (cobro normal de algo realmente vencido)
    nunca se probó en pantalla** — ver punto 2.
- **`/mora`** (`ControladorMoraServlet.mostrarLista`, `GET`) — seguimiento de mora, **oficial y
  gerente** (`Permiso.REGISTRAR_GESTION_COBRANZA`; cajera `NINGUNO`, no está en su menú): tabla
  con operación, cliente, teléfono, días de atraso (o "Incobrable"), cuotas vencidas, monto
  exigible, promesa (en rojo si venció) y última gestión; modal **"Registrar gestión"** (5
  campos: Contactado, Tipo, Resultado, Excusa, Promesa de pago).
  - **Recorrido en pantalla**: Sí — 12 y 14/10/2026, Joel, oficial, `CC-2026-00027`
    (`RF24_seguimiento_mora.md` §9), incluidos los 2 ajustes de usabilidad y 2 rondas de
    corrección de bugs reales de doble clic/HTML inválido.
- **`/reversiones`** (`ControladorReversionesServlet`) — bandeja del **gerente**
  (`Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA`; cajera `NINGUNO`, oficial `NINGUNO`), pestañas
  Pendientes/Historial — ver punto 10.
- **`/reportes/estado-cuenta/{id}`** — historial completo y reimpresión, accesible también al
  **oficial** (`SUBREPORTES_OFICIAL`, ruta separada de `/cobranza` a propósito, para no darle
  403 pero tampoco acceso a cobrar) — ver punto 8/12.

## 2. Cobro de cuota vencida

- **Qué cuotas entra**: `ServicioMotorFinanciero.distribuirPago` recorre **todas** las cuotas
  vencidas de una sola vez, de la más vieja a la más nueva — el cajero no elige cuáles; si el
  monto alcanza, cubre también la cuota que vence **exactamente hoy** (D-144). Una cuota futura
  **nunca** entra por esta vía.
- **Monto propuesto**: `ServicioCobranza.calcularMontoCobrableNormal` = todo lo vencido
  (`ServicioMotorFinanciero.calcularDeudaExigible`, RF21) + la cuota de hoy si la hay. Si no hay
  nada vencido ni nada vence hoy, da **0 Gs.** y el botón cambia a "Adelantar a capital" (punto 5).
- **Orden de imputación (RN-10, y literalmente la cláusula QUINTA del contrato — "Mora")**:
  **moratorio → punitorio → interés (compensatorio) → capital**, cada concepto con su IVA antes
  de pasar al siguiente — `ServicioMotorFinanciero.aplicarPagoACuota`/`aplicarConcepto`. El
  cajero **no elige** nada del orden.
- **Cobro de varias cuotas a la vez**: sí, en una sola operación — el monto recibido se reparte
  cuota por cuota (más vieja primero) hasta agotarse o hasta cubrir todo lo exigible.
- **Rechazo si no hay nada vencido**: mensaje exacto (`ServicioCobranza.confirmarCobro`, línea
  680): *"Este préstamo no tiene cuotas vencidas por cobrar. Si el cliente quiere pagar antes del
  vencimiento, use 'Adelantar capital'."*
- **Controles**: caja `ABIERTA` y **de hoy** (`ServicioCaja.esDeHoy`, mismo mensaje
  `mensajeCajaDeOtroDia` que caja/desembolso); `Permiso.COBRAR`; idempotencia por
  `identificadorConfirmacion` (doble clic o dos pestañas no duplican, `SQLState 23505` como red
  final).
- **Auditoría**: `COBRAR` (PAGO) — *"Cobró [monto] de [operación] (normal/parcial). [Redondeo a
  favor del cliente: -X, si aplica]"*.
- **RF/RN**: RF22 (anteproyecto), RN-10 (orden), RN-20 (parcial con promesa, punto 3), RN-21
  (excedente, punto 4).
- **Recorrido en pantalla**: **No** — ver la nota de RF22 §9/§10: *"'Cobrar' en sí (RF22) sigue
  sin una prueba en pantalla propia — queda para cuando haya una cuota vencida de verdad."* Y
  RF21 §9: con `CC-2026-00027` (vencida real) se vio la tarjeta de deuda exigible, **"sin
  registrar ningún cobro"**. En todo este proyecto, según los documentos de `docs/funcionamiento`,
  **nunca se ejecutó un "Pagar cuota" real sobre una cuota efectivamente vencida** — solo se
  probó "Adelantar a capital" (cuota no vencida, Mirtha) y la sola vista de la deuda exigible
  (`CC-2026-00027`, sin cobrar). Queda **no recorrido: requiere una cuota realmente vencida** —
  con el segundo cliente de práctica (`CC-2026-00002`, primera cuota 23/11/2026) tampoco se puede
  hoy (23/10/2026), por decisión de Joel de no cambiar ninguna fecha.

## 3. Pago parcial (menos que la cuota)

- **Se permite**: sí. Si lo recibido no cubre el total exigible, `confirmarCobro` lo trata como
  **parcial** (RN-20) — exige una **fecha de promesa** posterior a hoy; sin ella, rechaza sin
  cobrar nada (`esParcial && (fechaPromesa == null || !fechaPromesa.isAfter(fecha))`).
- **Cómo queda la cuota**: el motor reparte lo recibido en el mismo orden del punto 2
  (moratorio→punitorio→interés→capital), hasta agotar el monto — lo que falta de cada concepto
  queda pendiente en las columnas de la cuota (`capital_pagado`/`interes_pagado`,
  `moratorio_pendiente`/`punitorio_pendiente`/sus IVA). `estado_pago` de la cuota pasa a
  **`PARCIAL`** (no `PENDIENTE`, no `PAGADA`) — valor intermedio real de la base, no solo de
  pantalla.
- **Gestión automática**: un cobro parcial crea una `GestionCobranza` tipo `PAGO_PARCIAL` con la
  fecha y el monto prometido — mismo `CHECK` de `gestiones_cobranza` que usan las gestiones
  manuales de `/mora`, pero esta fila **solo la crea `confirmarCobro`** (el modal de "Registrar
  gestión" la rechaza si alguien la elige a mano).
- **RF/RN**: RF22 §2 punto 2, RN-20.
- **Recorrido en pantalla**: **No** — depende de una cuota vencida real, mismo bloqueo del
  punto 2.

## 4. Pago de más / vuelto

- **Excedente sobre lo exigible**: siempre **vuelto en efectivo** (`aplicacion.
  getMontoSobrante()`) — `ServicioCobranza.confirmarCobro` **nunca** lo aplica automáticamente a
  la próxima cuota (RN-21: "nunca se aplica a la próxima cuota") ni lo convierte solo en anticipo.
  **Matiz frente a RN-21** ("...o, si el cliente lo pide, anticipo a capital"): D-145 (ajuste de
  Joel, documentado en RF22 §8) **sacó** esa combinación automática — hoy, si el cliente quiere
  que el excedente vaya a capital, la cajera tiene que hacer una **operación aparte**
  ("Adelantar a capital", punto 5), no es parte de la misma confirmación de "Cobrar".
- **Redondeo de efectivo (D-141, SEDECO)**: solo en cobro **normal** (nunca en parcial) — el
  vuelto se calcula sobre el monto **redondeado** hacia abajo a múltiplos de
  `credito.redondeo_efectivo` (50 Gs.), no sobre el exacto; la diferencia es un descuento a favor
  del cliente, registrado en el detalle de auditoría.
- **Movimiento de caja**: `COBRO` (signo +1, lo recibido) y, si hay vuelto, `VUELTO` (signo −1) —
  los dos con `referencia_tipo = "PAGO"`.
- **"Saldo a favor"**: no existe como concepto en este sistema — no hay ninguna cuenta corriente
  de saldo a favor del cliente; todo excedente sale de la caja como vuelto en el momento, o se
  destina a capital en una operación aparte.
- **RF/RN**: RN-21 (con el matiz de D-145, arriba).
- **Recorrido en pantalla**: parcial — el mecanismo de vuelto y redondeo sí se probó
  (12/10/2026, Mirtha, "Adelantar a capital": recibido 500.000, aplicado 483.150, vuelto 16.850,
  `RF27_adelantar_capital.md` §9) pero dentro de un anticipo, no de un "Cobrar" normal con algo
  vencido — mismo bloqueo del punto 2 para ese caso puntual.

## 5. Adelanto a capital (cuota no vencida)

> **ACTUALIZADO el 27/10/2026 — ampliación pedida por el tutor (ver `FASE2_PLAN.md`).** Todo lo
> que sigue en este punto (fórmula del interés devengado, precondición D-88, regeneración del plan,
> límite D-90, factura) **sigue igual: `ServicioMotorFinanciero` no se tocó**. Lo que cambió es
> **cómo se elige el monto en pantalla**:
>
> - La tarjeta hace una sola pregunta, "¿Cuánto quiere pagar el cliente?", con tres opciones
>   excluyentes: **por cantidad de cuotas** (N libre, de 1 hasta las que le quedan), **por monto**
>   y **todo el préstamo**. Reemplaza los botones fijos "1/2/3 cuotas" (D-145 P-7) y el
>   "Cancelar todo" suelto.
> - El monto de "N cuotas" ya **no** es "N × la cuota": es el **mínimo que deja exactamente
>   K − N cuotas** en el plan nuevo (`ServicioCobranza.calcularMontoMinimoParaDejarCuotas`, un
>   método nuevo que **busca** con el cálculo del motor que ya existía). Motivo: "N × la cuota"
>   dejaba menos cuotas de las que la cajera decía, porque el adelanto no cobra intereses futuros
>   (RN-11) y rinde más que la suma de las cuotas.
> - **N = K** usa el flujo de cancelación total (punto 6), sin tocarlo.
> - Se muestra **un solo monto**, ya múltiplo de 50 Gs., sin el par "exacto / en efectivo": acá el
>   monto no es un precio con descuento sino el mínimo que logra sacar esas cuotas (decisión de
>   Joel; D-141 como descuento sigue igual en cobro normal y cancelación total).
> - Detalle completo y decisiones: `docs/funcionamiento/RF27_adelantar_capital.md` §2a y §7.
> - **Recorrido en pantalla**: Sí — 27/10/2026, Joel, cajera Sofía Martínez, `CC-2026-00001`,
>   N = 1 cobrado de verdad (`REC-2026-000005`, factura `001-001-0000002`), más N = 4, N = 11 y
>   "por monto" vistos sin cobrar (RF27 §9).

- **Cálculo del interés devengado** (`ServicioMotorFinanciero.calcularAnticipoParcial`):
  ```
  interés devengado = capitalPendienteTotal × tasaMensual × (díasTranscurridos ÷ díasDelPeríodo)
  ```
  `díasTranscurridos` = días desde el inicio teórico del período actual (para la cuota 1, la
  fecha de desembolso); `díasDelPeríodo` = días hasta el vencimiento teórico de esa cuota. Solo
  se devenga interés del **período en curso** — nunca de períodos futuros (Ley 6624/2020).
- **Precondición dura (D-88)**: si hay una cuota no vencida (fecha efectiva) pero con fecha
  **teórica** ya pasada (hueco de 1-3 días por corrimiento RN-19), rechaza: *"Primero hay que
  cobrar la cuota del [fecha]."* — ese período ya devengó su interés completo y no se puede
  "saltar" sin perderlo.
- **Regeneración del plan** (cláusula SEXTA del contrato, RN-11): el plan vigente pasa a **no
  vigente** (versión histórica, RN-13, nunca se borra) y se inserta un plan **nuevo**, con la
  cuota constante del mismo monto y **menos cuotas** restantes (nunca "la misma cantidad de
  cuotas con un monto menor"). Fórmula de `n'` (`regenerarPlanAnticipo`):
  `n' = techo(-ln(1 - saldo×i/cuotaConstante) / ln(1+i))`.
- **Límite (D-90)**: si lo que iría a capital cubre o supera el saldo total pendiente, se rechaza
  — *"El monto cubre todo el saldo del préstamo. Use Cancelación total."* (evitaría un plan
  degenerado de una cuota en cero, bug real ya corregido, `CC-2026-00028`).
- **Factura**: solo si el interés devengado es > 0 — un anticipo el mismo día del desembolso (0
  días transcurridos) no generaría factura, solo recibo.
- **RF/RN**: RF27, RN-11, RN-13, cláusula SEXTA ("Pago anticipado").
- **Recorrido en pantalla**: **Sí** — 12/10/2026, Joel, cajera, Mirtha (`CC-2026-00001`), 1 cuota,
  recibido 500.000 → "Anticipo de capital aplicado (483.150 Gs.)", vuelto 16.850,
  `REC-2026-000001`, factura `001-001-0000001` (`RF27_adelantar_capital.md` §9, 4 rondas de
  correcciones ya probadas en pantalla, tag `rf27-probado`). Dato puntual, ya documentado en
  `06_primer_cobro.md`: con `CC-2026-00002` HOY (23/10/2026, mismo día del desembolso), el
  interés devengado sería exactamente **0 Gs.** (0 días transcurridos).

## 6. Cancelación total anticipada

- **Fórmula** (Doc. 00 §6.6, `ServicioMotorFinanciero.calcularCancelacionTotal`): todo lo vencido
  (capital + interés + IVA + mora) + capital pendiente de las cuotas **no** vencidas + interés
  devengado del período en curso (mismo prorrateo del punto 5) + su IVA. **Intereses de períodos
  futuros: 0. Penalidad: 0** (Ley 6624/2020, cláusula SEXTA).
- **Qué marca**: todas las cuotas pendientes pasan a `PAGADA`; el préstamo pasa a **`FINALIZADO`**
  (`PrestamoDAO.marcarFinalizado`) — **no existe un estado `CANCELADO` separado**, ver punto 12.
  Resuelve cualquier promesa de pago abierta (RF24).
- **Comprobantes**: recibo siempre; factura solo si hay interés/mora gravado > 0 (puede no
  haberla si se cancela exactamente al vencimiento, sin mora y sin interés devengado pendiente);
  **constancia de cancelación** `CAN-aaaa-nnnnnn` (número asignado en el momento, PDF generado al
  primer pedido, RF28).
- **Cierre documental (RF29, paso aparte y posterior, RN-12)**: con el préstamo ya `FINALIZADO`,
  3 pasos manuales en `/formalizacion/{id}/cierre-documental` — generar la constancia de
  devolución del pagaré (`DEV-aaaa-nnnnnn`), cargar el escaneo firmado por el cliente, marcar el
  cierre **COMPLETO** (bloqueado sin el escaneo cargado). No depende de fechas — se puede probar
  el mismo día de la cancelación.
- **RF/RN**: RF26, RF28, RF29, RN-11, RN-12, cláusula SEXTA.
- **Recorrido en pantalla**: **Sí, completo** — 12/10/2026, Joel, cajera, `CC-2026-00026`
  (`EN_MORA`, cuota 1 parcial, cuota 2 vencida, cuota 3 período en curso): monto calculado a mano
  de antemano (847.639 Gs.) coincidió **exacto** con la pantalla (`RF26_cancelacion_anticipada.md`
  §9, tag `rf26-probado`); constancia `CAN-2026-000004`, factura `001-001-0000014`
  (`RF28_constancia_cancelacion.md` §9, tag `rf28-probado`); cierre documental completo el mismo
  día, constancia `DEV-2026-000002` (`RF29_cierre_documental.md` §9, tag `rf29-probado`).

## 7. Mora

- **Cuándo empieza**: de pleno derecho, por el solo vencimiento de cualquier cuota impaga —
  cláusula QUINTA del contrato ("sin necesidad de interpelación judicial ni extrajudicial").
- **Días de gracia**: `credito.dias_gracia_mora` — **hoy en 0** (sin tolerancia real; D-77: es
  una tolerancia, no un descuento de días — si los días de atraso caen dentro de ese margen, la
  mora del tramo es cero, pero no se restan días del cálculo si se supera).
- **Fórmula** (`ServicioMotorFinanciero.calcularMora`, idéntica a Doc. 00 §6.4):
  ```
  días = hoy − fecha_vencimiento_efectiva (o fecha_corte_mora si una reversión recortó el tramo)
  moratorio = redondear( capital_pendiente_de_LA_CUOTA × ((1 + tasa_moratoria)^(días/365) − 1) )
  punitorio = redondear( moratorio × porcentaje_punitorio )
  IVA de cada uno, 10 %, redondeado aparte
  ```
  **Base = capital pendiente de la cuota vencida** (nunca el saldo total del préstamo, ni la suma
  de varias cuotas) — confirmado línea por línea contra `calcularDeudaExigible`/
  `Cuota.capitalPendiente()` (RF21 §6, "confirmación pedida por Joel").
- **Tasas, congeladas al formalizar** (D-82): `tasa_moratoria = TEA_aprobada ×
  credito.factor_moratorio` (hoy el factor es 1, o sea moratoria = TEA exacta, cumpliendo RN-16
  "≤ tasa del préstamo" en el límite); `tasa_punitoria = credito.porcentaje_punitorio` tal cual
  (0,30 = 30 % del moratorio, **nunca** multiplicada por la moratoria — bug real ya corregido).
  Si Joel cambia esos parámetros después, **los préstamos ya formalizados no cambian**, solo los
  nuevos.
- **IVA sobre mora**: 10 % sobre moratorio y sobre punitorio, cada uno redondeado antes de sumar
  — el capital **nunca** lleva IVA (RN-25).
- **Feriados y fines de semana**: los días de atraso se cuentan **corridos** (no hábiles) desde
  la fecha de vencimiento **efectiva** (ya corrida por RN-19 si el vencimiento teórico caía
  sábado/domingo/feriado) — el corrimiento de la fecha de vencimiento en sí ya tiene en cuenta el
  calendario de `feriados`, pero una vez fijada esa fecha, los días de mora son calendario.
- **Vencimiento anticipado (2 cuotas vencidas)**: la cláusula OCTAVA del contrato dice
  textualmente *"La falta de pago de [`credito.cuotas_alerta_garante` = 2] cuotas vencidas
  faculta a EL ACREEDOR a declarar vencidos todos los plazos y exigir el saldo total adeudado...
  pudiendo ejecutar el pagaré."* **Verificado en el código: no existe ningún mecanismo que
  implemente esto** — ni automático (el préstamo sigue `EN_MORA`, nunca cambia de estado solo),
  ni un botón manual para "declarar vencimiento anticipado". Es **únicamente texto contractual
  impreso**, sin ninguna consecuencia en el sistema — ver "Observaciones para la fase 2".
- **Estado del préstamo**: `EN_MORA` cuando hay alguna cuota pendiente con fecha de vencimiento
  anterior a hoy (`ServicioCobranza.calcularEstadoSegunCuotas`, pura) — recalculado una vez al día
  (`ServicioMora.recalcularMorosidadDiaria`, con guarda de "una vez por día" vía la propia
  auditoría) al abrir `/mora`, `/panel` o cualquier informe de cartera/morosidad, lo que se abra
  primero.
- **RF/RN**: RF21, RF24, RN-16, cláusulas QUINTA y OCTAVA.
- **Recorrido en pantalla**: parcial — la tarjeta de deuda exigible con mora real sí (13/10/2026,
  `CC-2026-00027`, 10 días de atraso, `RF21_deuda_exigible.md` §9, cálculo verificado hasta el
  guaraní); el recálculo diario y el listado de `/mora` también (12-14/10/2026, mismo préstamo,
  `RF24_seguimiento_mora.md` §9). **Nunca recorrido**: un cobro real sobre una cuota en mora
  (punto 2), el vencimiento anticipado (no existe para probar), ni los tramos 61-90/91-180/
  incobrable (ningún préstamo de prueba llegó tan lejos — `RF21_deuda_exigible.md` §10).

## 8. Comprobantes

| Documento | Cuándo se emite | Numeración | Gravado | Reimpresión |
|---|---|---|---|---|
| **Recibo** | Siempre, en todo cobro (normal/parcial/anticipo/cancelación) | `REC-aaaa-NNNNNN`, correlativo por **año calendario** (`NumeradorDAO`, clave `RECIBO`) | Incluye el **capital** (a diferencia de la factura) | Mismo PDF guardado (D-94), nunca regenerado — salvo que el pago esté `REVERTIDO` (franja roja "ANULADO POR REVERSIÓN") |
| **Factura** | Solo si lo cobrado incluye interés/moratorio/punitorio > 0 | `001-001-NNNNNNN`, correlativo por **vigencia del timbrado** (no por año), `SELECT...FOR UPDATE` para que dos cobros simultáneos nunca pisen el número | Solo interés+moratorio+punitorio con su IVA — el capital **nunca** aparece (RN-25) | Igual que el recibo; si está `ANULADA`, franja "FACTURA ANULADA" |
| **Constancia de cancelación** (`CAN-aaaa-nnnnnn`) | Al cancelar total (RF26/28) | Propia, asignada en el momento | — | PDF al primer pedido, guardado |
| **Constancia de devolución del pagaré** (`DEV-aaaa-nnnnnn`) | Al generarla en el cierre documental (RF29), con el préstamo ya `FINALIZADO` | Propia | — | Regenerable sin perder el escaneo firmado ya cargado |
| **Constancia de reversión** (`REV-aaaa-NNNNNN`, usa `id_reversion_pago` como secuencia, sin numerador aparte) | Al autorizar una reversión | — | — | Se regenera siempre (necesita la fecha/hora real de cada reimpresión) |
| **Nota de anulación** (factura) | Al autorizar una reversión de un pago con factura | Numerador propio | — | — |

- **Timbrado/datos fiscales**: **Autoimpresor con timbrado** (`fiscal.tipo_comprobante =
  AUTOIMPRESOR`), **no** SIFEN — decisión ya documentada desde el anteproyecto (Doc. 00, riesgo
  R-03, "SIFEN en V2"), no una omisión. `fiscal.timbrado_numero`/`_vigencia_desde`/`_hasta`,
  `fiscal.establecimiento`/`punto_expedicion`/`factura_numero_desde`/`_hasta` — todo por
  parámetro, nunca hardcodeado; cualquier cobro gravado con el timbrado vencido o agotado se
  **rechaza completo** (no solo la factura) antes de insertar nada.
  - **Pendiente de revisión legal** (contador/abogado, ya anotado en `AVANCE.md`): la factura no
    tiene línea de firma del emisor; la norma exacta de autoimpresor no está citada con artículo.
- **Reversión**: anula sin borrar (`FacturaDAO.marcarAnulada`, nunca `DELETE`) — ver punto 10.
- **RF/RN**: RF23, RF28, RF29, RN-25, RN-26 (conservación 5 años, sin borrado).
- **Recorrido en pantalla**: Sí para recibo/factura/constancia de cancelación/devolución
  (12/10/2026, Mirtha y `CC-2026-00026`); constancia de reversión también probada
  (12/10/2026, `RF25_reversion_cobro.md`, con 9 ajustes de presentación ya corregidos). **No
  recorrido**: anular una factura que SÍ sea la última operación de su préstamo (en la prueba
  real, la única factura disponible ya había dejado de ser la última operación — ver punto 10).

## 9. Caja

| Operación | Movimiento | Signo | Exige caja de hoy |
|---|---|---|---|
| Cobro normal/parcial | `COBRO` | +1 | Sí |
| Vuelto | `VUELTO` | −1 | Sí (mismo movimiento del cobro) |
| Adelanto a capital | `COBRO` (+ `VUELTO` si corresponde) | +1/−1 | Sí |
| Cancelación total | `COBRO` (+ `VUELTO` si corresponde) | +1/−1 | Sí |
| Reversión autorizada | `COMPENSACION` | −1 (sale efectivo de la caja) | Sí, con saldo teórico suficiente |

- **Exigencia de caja**: las 4 operaciones de dinero del sistema (desembolso, cobro, anticipo,
  cancelación, y también la reversión) exigen una `SesionCaja` **`ABIERTA`** y **de hoy**
  (`ServicioCaja.esDeHoy`/`validarSesionDeHoy`) — mismo mensaje exacto en todas: *"La caja del
  [fecha] sigue abierta: ciérrela y rinda antes de operar hoy."* `confirmarCobro` ya delega en el
  mismo helper compartido desde el 19/10/2026 (antes repetía el chequeo inline) — `confirmarCobro`/
  `confirmarAnticipoCapital`/`confirmarCancelacionTotal` comparten `validarCajaAbiertaHoy`;
  `ServicioDesembolso` usa el mismo patrón, aunque sin el mismo método compartido textualmente
  (ver "Observaciones para la fase 2").
- **Efecto en el arqueo** (`07_cierre_del_dia.md`, ya recorrido en pantalla con el desembolso):
  cada movimiento de cobro/vuelto/compensación suma o resta del saldo teórico exactamente igual
  que un desembolso — el PDF de arqueo trae "Cobros de cuotas (n)" y "Compensaciones por
  reversión (n)" como líneas propias del `RESUMEN`, con su cantidad y monto (ver
  `ServicioCaja.generarPdfArqueo`, ya documentado en `07_cierre_del_dia.md` § "Ver arqueo").
- **RF/RN**: RF15, RF18, RF19 (caja), RF22/25/26/27 (cada operación de dinero).
- **Recorrido en pantalla**: parcial — el desembolso sí, de punta a punta, con este mismo
  mecanismo de caja (`05_desembolso.md`). Cobro/anticipo/cancelación con caja también se probaron
  (12/10/2026, Mirtha y `CC-2026-00026`), pero sobre una base que ya no existe hoy. El **bloqueo
  por caja de otro día** específicamente para "Cobrar"/"Adelantar capital"/"Cancelar todo" **no
  se ha confirmado en pantalla en esta sesión** (sí para Desembolso, `05_desembolso.md`) —
  verificado solo en el código (mismo mensaje y mismo helper).

## 10. Reversiones/anulaciones de cobros

- **Quién puede**: la **cajera** solicita (`Permiso.SOLICITAR_REVERSION`); el **gerente**
  autoriza o rechaza (`Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA`) — el oficial no tiene ningún
  permiso en este flujo (403 directo a `/reversiones` y a `/reversiones/solicitar`).
- **Qué se puede revertir**: **solo la operación más reciente** de un préstamo (regla propia, ya
  renombrada "regla de §6c" — antes citada por error como "D-75"), sin importar en qué sesión de
  caja se hizo. Si ya hay una solicitud `SOLICITADA` o `RECHAZADA` pendiente de reintento, no se
  ofrece una segunda.
- **Doble control de la factura (DNIT)**: si el pago generó factura, **la cajera** declara al
  solicitar ("Tengo en mi poder el original y todas las copias...") y **el gerente** vuelve a
  declararlo al autorizar — dos controles independientes, ninguno sustituye al otro. Sin la
  factura física de vuelta, no se autoriza (correspondería Nota de Crédito, fuera de esta
  versión).
- **Bloqueo por cierre documental completo** (13/10/2026, hallazgo de Joel): si
  `estado_cierre_documental = 'COMPLETO'` (el pagaré ya está en poder del cliente), no se puede
  ni solicitar ni autorizar una reversión de la cancelación que llevó a ese cierre — revertir
  dejaría una deuda sin que la empresa tenga el pagaré de vuelta.
- **Qué hace `autorizarReversion`, en orden, una sola transacción**: revalida todo lo de arriba
  → exige caja abierta de hoy con saldo suficiente → **restaura cada cuota** con la inversa exacta
  del motor financiero (`revertirAplicacionEnCuota`, nunca una resta a mano) → si era un anticipo,
  el plan anterior vuelve a vigente (RN-13); si era una cancelación, la liquidación se marca
  revertida → recalcula el estado del préstamo (`calcularEstadoSegunCuotas`, **nunca**
  `recalcularEstadoPrestamo`, que no sabe revertir un `FINALIZADO`) → anula la factura si había
  (Nota de anulación) → reabre la gestión de cobranza que el pago haya cerrado → **movimiento
  `COMPENSACION`** (−1) en la caja de hoy, por el monto devuelto → el pago pasa a `REVERTIDO`, la
  reversión a `AUTORIZADA`.
- **"Nunca se borra"**: pago, liquidación y factura quedan todas como filas reimprimibles,
  marcadas — nunca `DELETE` (RN-12/RN-26, coherente con el resto del sistema).
- **RF/RN**: RF25, RN-12, RN-13, RN-26, DNIT RG 118/2017 (o Decreto 6539/05 — las dos citas
  convivían en el código, sin resolver cuál es la vigente; en pantalla ya no se cita ninguna).
- **Recorrido en pantalla**: Sí, con hallazgos reales corregidos — 12/10/2026, Joel: "Reversión,
  auditoría, panel y caja: OK", más el hallazgo real de un recibo/factura revertidos que se
  reimprimían **sin ninguna marca** (corregido con la franja roja) y 9 ajustes de presentación de
  la constancia (`RF25_reversion_cobro.md` §9/§10). **No recorrido**: anular una factura real que
  siga siendo la última operación de su préstamo (en la única prueba disponible, la factura ya
  había dejado de ser la última — bloqueada por la regla de §6c) y el bloqueo por cierre
  documental completo (programado, sin prueba en pantalla).

## 11. Promesas de pago y seguimiento de mora

- **Promesa de pago**: se registra de dos formas — (a) automáticamente, dentro de un cobro
  **parcial** (RN-20, punto 3); (b) manualmente, en el modal de `/mora` (fecha + monto, los dos
  juntos o ninguno — si falta el monto con fecha puesta, `MontoPrometidoFaltanteException`).
- **Cuándo se marca cumplida**: `actualizarPromesasCumplidas`, dentro de **cualquier** operación
  de dinero que deje al préstamo sin ninguna cuota vencida, o cuyo monto aplicado alcance hasta
  la fecha de la promesa — el cajero **no decide** explícitamente "esta promesa se cumplió"; es
  una consecuencia automática del pago. Una cancelación total, en particular, **siempre** resuelve
  cualquier promesa abierta (no puede quedar una promesa vigente sobre un préstamo `FINALIZADO`).
- **Si se revierte el pago que cerró una promesa**: la gestión vuelve a quedar **abierta**
  (`GestionCobranzaDAO.marcarNoCumplidaPorPago`, dentro de `autorizarReversion`).
  Si se revierte un pago que no cerró ninguna promesa, no hay ningún efecto sobre gestiones.
- **Orden de la lista de `/mora`**: promesa **vencida primero**, después por días de atraso
  descendente — para que el oficial vea primero a quien ya incumplió lo prometido.
- **RF/RN**: RF24, RN-20.
- **Recorrido en pantalla**: Sí (ver punto 1/7) — incluido el caso real de doble clic
  registrando 2 gestiones idénticas (bug real, corregido con un token de un solo uso) y el
  control "Resultado obligatorio" (14/10/2026, Tanda 2, ya probado).

## 12. Estados

**De la cuota** (`cuotas.estado_pago`, 3 valores reales en la base — **no existe "vencida" como
estado guardado**, es siempre derivado comparando `fecha_vencimiento` contra hoy):
- `PENDIENTE` — nada pagado todavía.
- `PARCIAL` — se pagó algo, pero no cubre el total exigible de esa cuota (punto 3).
- `PAGADA` — totalmente cubierta (capital + interés + mora si la había).
- **"Vencida"** no es un valor de `estado_pago`: una cuota está vencida cuando `estado_pago !=
  PAGADA` y `fecha_vencimiento < hoy` — se recalcula en cada consulta, nunca se guarda.

**Del préstamo** (`prestamos.estado_prestamo`, 5 valores — **no existe "cancelado" como estado
separado**):
- `PENDIENTE_DESEMBOLSO` — formalizado, esperando el desembolso.
- `ACTIVO` — con cuotas pendientes, ninguna vencida.
- `EN_MORA` — con cuotas pendientes, al menos una vencida.
- `FINALIZADO` — sin ninguna cuota pendiente (`calcularEstadoSegunCuotas`) — llega ahí **tanto**
  por pagar normalmente todas las cuotas **como** por una cancelación total anticipada (RF26) —
  es el **mismo** estado final para los dos caminos, el anteproyecto/RN-12 no distingue "pagado
  en su totalidad" de "cancelado anticipadamente" a nivel de estado (sí a nivel de qué constancia
  se cita, ver RF29 §6e — `TIENE_CANCELACION`).
- `ANULADO_ANTES_DESEMBOLSO` — nunca llegó a desembolsarse (RF14, fuera de este mapa).
- **Transición ACTIVO↔EN_MORA**: pura (`calcularEstadoSegunCuotas`), recalculada tanto después
  de cada pago como una vez al día (`recalcularMorosidadDiaria`) — nunca queda "atrasada" más de
  un día sin recalcularse, y cualquier pantalla que la dispare primero (panel, mora, cobranza,
  reportes de cartera) la deja al día para el resto.

**Del pago** (`pagos.estado_pago`): `CONFIRMADO`/`REVERTIDO`. **Del pago, tipo**:
`NORMAL`/`PARCIAL`/`CANCELACION`/`ANTICIPO`. **De la factura**: `EMITIDA`/`ANULADA`. **De la
reversión**: `SOLICITADA`/`AUTORIZADA`/`RECHAZADA`. **Del cierre documental** (columna aparte del
préstamo, no un estado del préstamo en sí): `PENDIENTE`/`COMPLETO` (o `NO_APLICA`).

## 13. Observaciones para la fase 2

Sin clasificar A/B, sin proponer código — solo lo que, al recorrer el módulo completo, aparece
incoherente, frágil o digno de atención antes de rediseñar la metodología de cobro.

> **Nota del 27/10/2026.** La "metodología de cobro" que este documento anticipaba **ya se
> rediseñó en pantalla**, en la parte del adelanto: ver el aviso del punto 5 y
> `FASE2_PLAN.md` § "AMPLIACIÓN EXPLÍCITA DE ALCANCE (27/10/2026)". Ese rediseño **cerró C28**
> (el monto del adelanto ya no está limitado a 1/2/3 cuotas) y **B74**, y **atenuó C23** sin
> resolverlo. Las observaciones de abajo siguen todas abiertas, y se suma una nueva: **C39** — la
> cuota programada vence el 23/11/2026 pero el plan regenerado tras un adelanto muestra la próxima
> el 21/11/2026 y el último vencimiento el 21/09/2027 (ya pasaba antes del rediseño; a verificar
> aparte contra RF27, RN-13 y el contrato, sin corregir nada todavía).

1. **La cláusula OCTAVA (vencimiento anticipado a 2 cuotas vencidas) no tiene ningún mecanismo
   real en el sistema** — es únicamente texto contractual impreso; el préstamo nunca cambia de
   estado ni se bloquea por esto, y no existe ningún botón ni acción para "declarar vencimiento
   anticipado". Si un cliente real llega a esa situación, hoy depende enteramente de una acción
   humana fuera del sistema.
2. **La tasa moratoria queda congelada al formalizar y nunca se re-valida contra el tope de
   usura vigente** durante la mora (RF21 §6/§10) — coherente con que el resto del préstamo
   también queda congelado, pero si el tope legal bajara después de formalizado, ningún préstamo
   ya en mora lo reflejaría ni avisaría.
3. **El cobro normal de una cuota realmente vencida (RF22, "Pagar cuota") nunca se probó en
   pantalla en todo el proyecto** — según los propios documentos de `docs/funcionamiento`, todas
   las pruebas reales de cobro fueron "Adelantar a capital" (cuota no vencida) o cancelación
   total; la vista de "Deuda exigible" se probó una vez, explícitamente sin cobrar nada. El
   camino más común y más sensible del módulo (cobrar lo vencido) queda, en los hechos, el
   **menos** ejercitado contra datos reales.
4. **El excedente sobre lo exigible nunca se convierte automáticamente en anticipo** (D-145 lo
   sacó): si el cliente quiere aplicar el vuelto a capital, hace falta una segunda operación
   separada — RN-21 describe la posibilidad ("si el cliente lo pide") como si fuera parte de la
   misma decisión de cobro, pero la implementación actual exige dos pasos.
5. **El redondeo de efectivo (`credito.redondeo_efectivo`) vive en dos implementaciones
   distintas** para la misma regla: `ServicioCobranza.calcularRedondeoEfectivo` (Cobrar/Cancelar
   todo) y `ControladorDesembolsoServlet`/`ControladorCobranzaServlet.redondearHaciaAbajo` (en el
   controlador, solo para el monto sugerido de "Adelantar a capital") — mismo criterio, dos
   caminos de código (RF22 §10).
6. **Dos citas de norma distintas para la misma regla** (factura física de vuelta antes de
   anular una reversión): el código cita tanto "Decreto 6539/05" como "RG 118/2017" en
   comentarios, sin que se haya confirmado cuál es la vigente — en pantalla ya no se cita
   ninguna, pero el código sigue con las dos.
7. **La revisión legal de la factura (autoimpresor) sigue pendiente** — falta, como mínimo, la
   firma del emisor; nadie confirmó contra la norma exacta de la DNIT que el resto del contenido
   alcance. Ya anotado en `AVANCE.md`, repetido acá porque es central al módulo de cobro.
8. **El "ahorro del cliente" de una cancelación anticipada no se muestra como dato explícito** —
   se puede derivar restando, pero no hay un campo dedicado (RF26 §10), aunque el anteproyecto no
   lo exige.
9. **El control de caja de hoy no usa literalmente el mismo método en los 4 lugares** — ya
   unificado para Cobrar/Anticipar/Cancelar (`validarCajaAbiertaHoy`), pero `ServicioDesembolso`
   sigue con su propio patrón equivalente, no exactamente la misma llamada (RF27 §6a, "podría
   unificarse, no es parte de este pedido").
10. **El estado `FINALIZADO` no distingue "pagado en su totalidad" de "cancelado
    anticipadamente"** a nivel de estado del préstamo — la distinción solo vive en si existe una
    `liquidaciones_anticipadas` o no (y en qué constancia cita la devolución del pagaré, RF29
    §6e) — alguien que solo mire `estado_prestamo` no puede saber cuál de los dos pasó.
11. **El buscador de `/cobranza` no escapa `%`/`_` antes de armar el `LIKE`** — mismo quirk ya
    presente en `/clientes`, no es nuevo de este módulo, pero convive en el mismo texto de
    búsqueda que maneja dinero.

## Tabla de caminos del cobro

| Camino | Existe en el código | ¿Se recorrió en pantalla? |
|---|---|---|
| Bandeja de trabajo (3 tarjetas, búsqueda) | `ServicioCobranza.armarListaTrabajo`/`buscarParaCobranza` | **Sí** — 12/10/2026, cajera |
| Deuda exigible con mora real | `ServicioMotorFinanciero.calcularDeudaExigible` | **Sí** — 13/10/2026, `CC-2026-00027`, sin cobrar |
| Cobro normal de una cuota realmente vencida | `ServicioCobranza.confirmarCobro` | **No** — nunca se ejecutó en todo el proyecto (ver Observación 3) |
| Cobro de varias cuotas a la vez | `ServicioMotorFinanciero.distribuirPago` | **No** — mismo bloqueo, requiere ≥2 cuotas vencidas reales |
| Pago parcial con promesa | `confirmarCobro` (rama `esParcial`) | **No** — requiere cuota vencida real |
| Rechazo de "Cobrar" sin nada vencido | `confirmarCobro` (mensaje exacto) | **Sí** — confirmado en el código y citado en RF22 §9 como control verificable sin cobro real |
| Adelanto a capital (cuota no vencida) | `ServicioMotorFinanciero.calcularAnticipoParcial` | **Sí** — 12/10/2026, Mirtha, 4 rondas de corrección, `rf27-probado` |
| Cancelación total anticipada | `ServicioMotorFinanciero.calcularCancelacionTotal` | **Sí** — 12/10/2026, `CC-2026-00026`, monto exacto, `rf26-probado` |
| Constancia de cancelación + FINALIZADO | `ServicioCobranza.confirmarCancelacionTotal`/`PrestamoDAO.marcarFinalizado` | **Sí** — mismo caso, `rf28-probado` |
| Cierre documental (constancia devolución + escaneo + completo) | `ServicioFormalizacion.generarConstanciaDevolucion`/`completarCierreDocumental` | **Sí** — mismo día, `rf29-probado` |
| Recibo y factura de un cobro gravado | `ServicioCobranza.emitirFactura`/`generarPdfRecibo` | **Sí** — 12/10/2026, Mirtha |
| Reimpresión de un recibo/factura revertidos (franja roja) | `obtenerPdfRecibo`/`obtenerPdfFactura` (sin cachear si `REVERTIDO`/`ANULADA`) | **Sí** — hallazgo real de Joel, corregido y reprobado |
| Anulación de factura que sigue siendo la última operación | `ServicioReversiones.autorizarReversion` (paso 7) | **No** — en la única prueba disponible, la factura ya no era la última operación |
| Solicitar/autorizar/rechazar reversión | `ServicioReversiones.*` | **Sí** — 12/10/2026, con 9 ajustes de presentación corregidos |
| Reintentar una reversión tras un rechazo (Opción A) | `ReversionPagoDAO.marcarResolicitada` | **Solo código** — programado, sin prueba en pantalla registrada |
| Bloqueo de reversión con cierre documental completo | `ServicioReversiones.validarPuedeSolicitarReversion` | **Solo código** — programado, sin prueba en pantalla registrada |
| Registrar/ver gestión de cobranza y promesa de pago | `ServicioMora.registrarGestion` | **Sí** — 12 y 14/10/2026, con 2 bugs reales de doble clic/HTML corregidos |
| Recálculo diario de mora (ACTIVO↔EN_MORA) | `ServicioMora.recalcularMorosidadDiaria` | **Sí** — mismo préstamo, mismas fechas |
| Vencimiento anticipado por 2 cuotas vencidas (cláusula OCTAVA) | **No existe** — solo texto contractual | **No recorrido** — no hay nada que recorrer, el camino no existe |
| Tramos de mora 61-90 / 91-180 / incobrable | `ServicioMotorFinanciero.calcularMora` (la fórmula es la misma para cualquier tramo) | **No recorrido** — ningún préstamo de prueba llegó tan lejos |
| Caja de otro día bloqueando Cobrar/Anticipar/Cancelar | `ServicioCaja.validarSesionDeHoy` (mismo mecanismo ya confirmado para Desembolso) | **No recorrido en esta sesión** — solo verificado en el código; sí confirmado para Desembolso (`05_desembolso.md`) |
| Estado de cuenta / reimpresión por el oficial | `ControladorReportesServlet` (`estado-cuenta`) | **Sí** — 13/10/2026, `rf34-probado` |
| **Todo lo anterior con el segundo cliente de práctica (`CC-2026-00002`) y fechas reales posteriores al 23/10/2026** | — | **No recorrido: requiere cambio de fecha, descartado por decisión de Joel (23/10/2026)** |
