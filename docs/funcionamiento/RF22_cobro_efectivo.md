# RF22 — Registrar el cobro en efectivo, con vuelto

**Estado: probado en pantalla el 12/10/2026 (tag `rf22-probado`).** Las correcciones del cartel
de confirmación compartido con RF27 (ver §9) ya se probaron en pantalla y quedaron OK. "Cobrar"
en sí (con una cuota realmente vencida) sigue pendiente de la prueba con fechas reales — ver §10.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF22 (módulo Cobranza y mora): *"El sistema debe permitir registrar
el cobro en efectivo de una o más cuotas, calculando el vuelto."*

Reglas relacionadas (`docs/00_especificacion_maestra.md`): **RN-10** (orden de aplicación:
moratorio → punitorio → interés, cada uno con su IVA, → capital; "el cajero no elige"); **RN-20**
(pago parcial exige fecha de promesa obligatoria); **RN-21** (el excedente sobre lo exigible es
vuelto o, si el cliente lo pide, anticipo a capital — nunca se aplica a la próxima cuota);
**RN-11** (el adelanto de capital no cobra intereses futuros ni penalidad, Ley 6624/2020).

## 2. Cómo funciona

**(27/10/2026) Qué cambió y qué no, con la ampliación pedida por el tutor.** La tarjeta "Cobranza"
pasó a una sola pregunta — "¿Cuánto quiere pagar el cliente?" — con tres opciones excluyentes
(ver `RF27_adelantar_capital.md` §2a). **Para este RF22 no cambió nada de la lógica**: `POST
/cobranza/{id}/cobrar` y `ServicioCobranza.confirmarCobro` no se tocaron en una sola línea. Lo
único que cambió es presentación:

- El botón "Pagar cuota" ahora se llama **"Pagar lo vencido"** (rótulo aprobado por Joel el
  27/10/2026): puede cubrir varias cuotas vencidas más la que vence justo hoy (D-144), así que
  "cuota" en singular era engañoso. El cartel de confirmación dice *"Confirmar: Pagar lo
  vencido"* / **"Sí, cobrar lo vencido"**.
- **Con cuotas vencidas se ven solo dos opciones**: "Pagar lo vencido" y "Todo el préstamo", más
  la línea *"Primero hay que cobrar lo vencido. Adelantar por cantidad de cuotas o por monto queda
  disponible cuando el préstamo no tenga nada vencido."* Las opciones de adelanto (por cuotas o
  por monto) no se muestran, igual que antes.
- **El pago parcial no se movió de lugar**: si lo recibido no cubre lo exigible, aparece sola la
  fecha de promesa y sigue siendo un parcial RN-20 con su `GestionCobranza` tipo `PAGO_PARCIAL`.
  Es decir, "por monto" dentro de lo vencido sigue existiendo donde siempre, con el significado de
  siempre (parcial con promesa), no como adelanto.
- Sigue vigente el caso D-146 (P-17): cubre lo vencido pero no la cuota de hoy.

El cajero abre la ficha de cobranza (`GET /cobranza/{idPrestamo}`, llegado desde RF20) y confirma
"Cobrar" (`POST /cobranza/{idPrestamo}/cobrar`) con el efectivo recibido. Todo en
`ServicioCobranza.confirmarCobro`, una sola transacción:

1. Repite la deuda exigible al momento del cobro (RF21) — nunca confía en un valor ya calculado
   antes.
2. Si lo recibido **cubre** lo exigible (incluida la cuota que vence justo hoy, si la hay): cobro
   **normal**. Si **no cubre**: cobro **parcial** — exige una fecha de promesa posterior a hoy,
   si no la hay, rechaza sin cobrar nada.
3. Distribuye el monto entre todas las cuotas vencidas, de la más vieja a la más nueva, cuota por
   cuota, y dentro de cada cuota en el orden RN-10 (moratorio → punitorio → interés → capital,
   cada concepto con su IVA antes de pasar al siguiente) — ver §6a.
4. Calcula el vuelto: lo que sobra sobre lo aplicado.
5. Emite el recibo siempre (RF23); emite la factura solo si algo de lo cobrado es gravado
   (interés/moratorio/punitorio > 0 — un cobro 100 % capital, al final del préstamo, no genera
   factura).
6. Registra el movimiento de caja (y el de vuelto, si corresponde), la auditoría, y recalcula el
   estado del préstamo (`ACTIVO`/`EN_MORA`/`FINALIZADO`).

**Pago adelantado de una cuota NO vencida — "Cobrar" la rechaza.** Si no hay nada vencido ni una
cuota que venza justo hoy, `confirmarCobro` termina con *"Este préstamo no tiene cuotas vencidas
por cobrar. Si el cliente quiere pagar antes del vencimiento, use 'Adelantar capital'."* — ver
§6a, es importante para la prueba de hoy con Mirtha.

## 3. Auditoría

`servicioAuditoria.registrar(..., "COBRAR", "PAGO", idPago, "Cobró [monto] de [operación]
(normal/parcial). [Redondeo a favor del cliente: -X, si aplica]")`.

## 4. Dónde está

- **Servicio**: `ServicioCobranza.confirmarCobro` (orquesta); `ServicioMotorFinanciero.
  distribuirPago`/`aplicarPagoACuota`/`aplicarConcepto` (RN-10, el reparto en sí — motor único,
  nada de esto está en `ServicioCobranza` ni en el controlador).
- **Controlador**: `ControladorCobranzaServlet.procesarCobrar` (`/cobranza/{id}/cobrar`, `POST`).
- **Permisos**: mismo `Permiso.COBRAR` de toda `/cobranza` (cajera y administrador, ver RF20 §6a).
- **DAO**: `PagoDAO` (tabla `pagos`), `AplicacionPagoDAO` (tabla `aplicaciones_pago`, el detalle
  por cuota/concepto), `MovimientoCajaDAO`, `SesionCajaDAO`, `NumeradorDAO` (clave `RECIBO`).
- **Parámetros**: `credito.dias_gracia_mora` (RF21), `credito.redondeo_efectivo` (D-141, SEDECO).
- **Modelo**: `Pago` (tipo `NORMAL`/`PARCIAL`/`CANCELACION`/`ANTICIPO`, monto recibido/vuelto/
  aplicado/redondeo, número de recibo).

## 5. Dependencias

- **RF20**: el botón "Cobrar" llega desde la lista de trabajo/búsqueda.
- **RF21**: la deuda exigible que `confirmarCobro` cobra es la que calcula ese RF.
- **RF23**: recibo y factura, emitidos dentro de la misma transacción de este RF22.
- **RF24**: un cobro parcial crea una `GestionCobranza` tipo `PAGO_PARCIAL` con la promesa.
- **RF25**: la reversión de un cobro (RF25) deshace exactamente lo que este RF22 hizo.
- **RF27** ("Adelantar capital"): es la vía real para pagar una cuota no vencida — RF22
  explícitamente no lo hace (ver §2/§6a).
- **RF15-RF19** (caja): "Cobrar" exige una caja `ABIERTA` del día (RF15), y genera movimientos
  (RF18) que entran en el arqueo (RF19).

## 6. Análisis (13/10/2026, pedido de Joel)

**a) Cómo se cobra**:
- **Una o más cuotas**: sí — `distribuirPago` recorre TODAS las cuotas vencidas de una sola vez
  (ordenadas de la más vieja a la más nueva), sin que el cajero elija cuáles; si el monto
  alcanza, también cubre la cuota que vence justo hoy (D-144).
- **Cobro parcial**: sí, permitido — si lo recibido no cubre el total exigible, el sistema exige
  una fecha de promesa (RN-20) y crea una `GestionCobranza` (`PAGO_PARCIAL`) con el saldo
  prometido; sin esa fecha, rechaza sin cobrar nada (`ServicioCobranza.confirmarCobro`, la
  condición `esParcial && (fechaPromesa == null || !fechaPromesa.isAfter(fecha))`).
- **Imputación**: moratorio → punitorio → interés → capital, cada uno con su IVA antes de pasar
  al siguiente concepto (`ServicioMotorFinanciero.aplicarPagoACuota`, líneas 441/444/449; mismo
  orden que ya reusa RF21). El cajero no elige nada — RN-10 lo dice explícito.
- **Pago adelantado de una cuota NO vencida**: **"Cobrar" lo rechaza.** `distribuirPago` solo
  recorre cuotas con `fechaVencimiento.isBefore(fecha)` — una cuota futura nunca entra; si no hay
  ninguna vencida (ni una que venza justo hoy), `aplicacion.getDetalles()` queda vacío y
  `confirmarCobro` lanza el mensaje de §2 sugiriendo "Adelantar capital". **Con Mirtha
  (CC-2026-00001, cuota 1 vence el 24/11/2026) la prueba de hoy con "Cobrar" directamente
  fallaría con ese mensaje — la vía real para pagar antes es "Adelantar capital" (botón
  separado, `/cobranza/{id}/anticipar`, `ServicioCobranza.confirmarAnticipoCapital`), que es
  RF27 ("Adelantar capital"), no este RF22.** Aviso esto ahora para no perder tiempo probando el
  camino equivocado — si querés probar HOY el pago adelantado, es con ese otro botón.
- **Vuelto**: lo que sobra sobre lo aplicado (`aplicacion.getMontoSobrante()`), salvo que
  corresponda redondeo de efectivo (D-141, SEDECO, solo en cobro normal, nunca en parcial) — ahí
  el vuelto se calcula sobre el monto redondeado, no sobre el exacto.
- **Efectivo recibido menor al monto exigible**: no se rechaza de entrada — se convierte
  automáticamente en cobro parcial (ver arriba); solo se rechaza si además falta la fecha de
  promesa.

**b) Controles**:
- **Caja**: debe existir una `SesionCaja` `ABIERTA` (`sesionCajaDAO.obtenerAbierta`) y debe ser
  de **hoy** (`servicioCaja.esDeHoy`) — si es de un día anterior, rechaza con el mismo aviso que
  ya usa RF15/RF16 ("La caja del [fecha] sigue abierta: ciérrela y rinda antes de operar hoy.").
- **Cajera**: `Permiso.COBRAR` en el filtro (cajera y administrador únicamente, ver RF20 §6a) —
  el oficial/gerente no llegan ni a la pantalla.
- **Doble clic / dos pestañas**: `identificadorConfirmacion` único por intento (generado al
  abrir la ficha, viaja en un campo oculto). Si ya existe un `Pago` con esa clave, devuelve el
  mismo resultado sin cobrar de nuevo (`pagoIdempotente`) — y si dos clics llegan exactamente
  al mismo tiempo, la restricción `UNIQUE` de la base lo frena (`SQLState 23505`) y el segundo
  intento también recibe el pago ya existente, nunca un error ni un cobro duplicado.
- **Auditoría**: ver §3.
- **Movimiento de caja**: un movimiento `COBRO` (signo +1, por el monto recibido) y, si hay
  vuelto, un `VUELTO` (signo −1) — ambos con `referencia_tipo="PAGO"` e `id_referencia` al pago,
  para poder rastrear cada movimiento hasta su operación de origen.

**c) RF23 — ver el documento `RF23_recibo_factura.md`** (mismo análisis, documento separado
porque RF23 es su propio RF, aunque ambos ocurran en la misma transacción que este RF22).

**e) Qué se puede probar HOY**:
- Con Mirtha (CC-2026-00001, al día, sin vencidas): intentar "Cobrar" directamente debe
  rechazar con el mensaje de §6a — confirma el control, aunque no haya cobro real que ver.
  **El pago adelantado de la cuota 1 se prueba con "Adelantar capital" (RF27), no con "Cobrar".**
- El control de caja (RF15/RF16, ya probado en esos RF): sin caja abierta de hoy, "Cobrar"
  rechaza igual que cualquier otra operación de caja.
- Los permisos (oficial/gerente sin acceso a `/cobranza`, ya probado en RF20).
- **Lo que queda para la prueba con fechas reales** (decisión de Joel, al final): un cobro
  normal y uno parcial de verdad, con una cuota efectivamente vencida — vuelto, redondeo,
  recibo y factura reales, idempotencia con un doble clic real. Hoy esto está cubierto por
  pruebas unitarias del motor (`distribuirPago`/`aplicarPagoACuota`) y, posiblemente, por
  pruebas de `ServicioCobranzaTest` ya existentes de etapas anteriores (a confirmar, no revisé
  cobertura exhaustiva en esta pasada).

## 7. Decisiones

Ninguna todavía — solo análisis, a la espera de la aprobación de Joel.

### La cuota que vence hoy y el adelanto (30/10/2026, paso 15)

**Con una cuota que vence hoy no se ofrece adelantar capital**, y eso es correcto: la cuota del
día es el **cobro contractual de hoy** (D-144 — `ServicioMotorFinanciero.distribuirPago` la cobra
sin prorrateo, y nunca toca una cuota futura), mientras que el adelanto de **RN-11**/RF27 es para
capital que **todavía no es exigible**. Adelantar dejando esa cuota sin pagar la pondría en mora
al día siguiente.

**Si el cliente quiere pagar la cuota del día y además adelantar, son dos operaciones**: primero
el cobro de la cuota, después el adelanto. **RN-21** permite aplicar el **excedente** como anticipo
"si el cliente lo pide", pero D-145/D-146 ya decidieron que acá el excedente **es vuelto** y nunca
se aplica solo a la próxima cuota — no hay "saldo a favor".


## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 13/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | *(pendiente)* | — |
| D-141 (sin fecha exacta) | Redondeo de efectivo (SEDECO), solo en cobro normal | — | — |
| D-145 (sin fecha exacta, ajuste de Joel) | Se sacó la combinación automática de vuelto+anticipo; "Cobrar" siempre devuelve el vuelto, "Adelantar capital" es una operación aparte | — | — |
| D-144 (sin fecha exacta, ajuste de Joel) | "Cobrar" nunca toca una cuota futura; cubre lo vencido + la que vence justo hoy | — | — |

| 30/10/2026 | **B81** (con **B79**): en un **pago parcial**, la línea grande del *"Resumen del cobro"* dice ahora **"Cobra {monto} Gs. (pago parcial)"** —lo que de verdad se va a cobrar— y debajo, en ámbar, **"de {exigible} Gs. exigibles — queda pendiente {pendiente} Gs."**. Antes la línea más grande mostraba el total exigible aunque se estuviera cobrando menos, y la explicación vivía en el renglón "Vuelto", más chico. Las dos frases tienen **un solo dueño** (`ServicioCobranza.lineaCobroParcial` y `detalleCobroParcial`, puras, con JUnit que cruza la plantilla del JS contra el render del servidor, patrón de B92): el JS solo reemplaza números. En consecuencia **"Vuelto" deja de repetir el mismo dato** y dice *"Sin vuelto (0 Gs.)"* en ámbar, que es lo que corresponde a ese rótulo (se aplica todo). Sin tocar `confirmarCobro`, RN-20, la promesa de pago ni ningún cálculo. **Probado en pantalla el mismo día** con una cuota vencida conseguida por el corrimiento de fechas de D-81 (§9). `mvn test` 858/858 | `acd53c6` | — |

| 30/10/2026 (2.ª tanda) | **B95 + B96**, los dos textos que quedaban del pago parcial. **B95**: con el campo "Fecha de promesa para el resto" vacío ya salía en rojo *"La fecha de promesa debe ser posterior a hoy"* — un error de algo que la cajera no había hecho, porque un solo booleano habilitaba "Cobrar" **y** decidía el aviso. Se partió en dos preguntas: vacío → **pedido en gris** en el renglón de motivos de arriba de "Cobrar" (`ServicioCobranza.MOTIVO_FALTA_FECHA_PROMESA`, dueño único, en un `data-`; el JS no redacta, regla de B92), y fecha ≤ hoy → el rojo de siempre; "Cobrar" sigue deshabilitado en los dos casos, y **un solo lugar decide ese renglón** (`refrescarAvisoAnticipo`). **B96**: el cartel de confirmación dejó de redactar por su cuenta (*"Pago parcial: X" / "Queda pendiente: Y"*, tercera versión de lo que el resumen ya decía) y usa **las mismas frases** que la tarjeta; y su botón pasó de *"Sí, cobrar lo vencido"* —falso en un parcial— a **"Sí, cobrar 100.000 Gs."** (`botonCobroParcial`, mismo dueño). El título del cartel no cambió. Ningún cálculo, monto ni regla de negocio se tocó. **Probado en pantalla el mismo día** (§9), con el cartel cerrado con **Cancelar**. `mvn test` 862/862 | `39c284b` | — |

| 30/10/2026 (paso 15) | **B98**: con una cuota que **vence hoy** no hay nada vencido (`isBefore`) pero sí algo que cobrar (D-144), y la pantalla elegía **las palabras** con la pregunta equivocada: decía *"vencido"* en el rótulo de la opción, en la nota, en la línea grande y en el cartel, al lado de *"Sin cuotas vencidas."*. Ahora hay un `enum SituacionCobroHoy` y **todos esos textos tienen dueño** en `ServicioCobranza` (*"Pagar la cuota de hoy"*, *"Hoy vence la cuota N/M…"*, *"Cobra X (a cuenta de la cuota de hoy)"* + *"de Y — quedan Z, sin mora hasta mañana"*, y el cartel que **repite** esas frases con los botones *"Sí, cobrar la cuota de hoy"* / *"Sí, cobrar X"*). También se corrigió una **violación de B79** en esa rama (el número grande no seguía al monto escrito) y los dos textos de D-146 (P-17) se mudaron del JS al servicio sin cambiar una palabra. Ningún monto, cálculo ni regla cambió. **Probado en pantalla el mismo día** (§9). `mvn test` 872/872 | `1f71147` | — |

| 30/10/2026 (paso 15 b) | **Primer cobro real de una cuota que VENCE HOY**, de punta a punta y sin un solo desvío respecto de la predicción sellada antes de abrir la pantalla (`CC-2026-00004`, cajera Sofía, 22:21-22:26). Cuota 1/6 de **180.609 Gs.** (capital 158.479 + interés 20.118 + IVA 2.012), **mora 0** (`dias = 0 <= dias_gracia`), cobrada con **180.600 Gs.** en efectivo: la cuota quedó **PAGADA** porque lo que se distribuye es el **monto exacto** (D-141: `ResultadoRedondeoEfectivo.getMontoAplicado()` es *"lo que se aplica a la deuda, aunque se haya cobrado menos"*), con **9 Gs. de redondeo a favor del cliente**. Mensaje: *"Cobro de 180.609 Gs. aplicado. Cobrado en efectivo: 180.600 Gs. (redondeo a favor del cliente: 9 Gs.) Recibo REC-2026-000015. Factura 001-001-0000007."*. Saldo de capital **841.521**, 5 cuotas pendientes, próxima **2/6 — 27/11/2026**, préstamo **sigue ACTIVO**. Caja (sesión 13): `ENTREGA_GERENTE +1.000.000` → `DESEMBOLSO −1.000.000` → `COBRO +180.600`, saldo teórico **180.600**. **Observación de Joel, registrada como B101**: el *"Son:"* del recibo dice lo **aplicado** (180.609) y no lo **recibido** (180.600) — está así por D-141, y la decisión queda abierta. Sin cambios de código en este cierre | `48501b6` | — |
| 05/11/2026 | **B106** (tramo 2 de B86): los rótulos de las opciones de cobro y los **tres** textos del botón del cartel de confirmación dejan de estar escritos a mano en `cobranza-ficha.js` y pasan a tener **un solo dueño** en `ServicioCobranza` (7 constantes), igual que B92/B96/B98 ya habían hecho con las otras frases de la misma pantalla. «Todo el préstamo» tenía **seis** copias visibles (botón y nota de la JSP, dos puntos del JS y **dos mensajes del servidor** de `ControladorCobranzaServlet`) y «Adelantar a capital» **no existía en el servidor**: vivía sólo en el navegador. La JSP imprime las constantes y las deja en `data-opcion-*`/`data-boton-*`; el JS **sólo las lee**. **Ningún texto cambió de palabras y no se tocó ninguna lógica, ningún cálculo ni ningún endpoint.** `TextosDePantallaTest` prohíbe ahora las 5 frases en el JS; el respaldo de B98 (*"Sí, cobrar la cuota de hoy"*) se conserva porque es un *fallback* con dueño propio. `EtiquetasAuditoria` mantiene su copia a propósito (una etiqueta de auditoría no se renombra con el texto visible). Probado en pantalla por Joel el mismo día (§9). `mvn test` 907/907 | `5a22902` | — |
| 05/11/2026 (B114) | **El redondeo de D-141 que la pantalla ofrece ya no se rechaza como pago parcial.** `confirmarCobro` decidía `esParcial = montoRecibido < exigible` **antes** de calcular el redondeo, y el redondeo solo se calculaba `esParcial ? null : …`: circular, porque el monto redondeado está por definición entre 1 y `credito.redondeo_efectivo - 1` Gs. por debajo del exacto. Con cuotas vencidas de verdad, la ficha ofrecía *"Cancela la deuda de 540.316 Gs. (redondeo a favor del cliente: 16 Gs.)"* y el servidor contestaba *"El pago no cubre lo exigible"*; si la cajera ponía una fecha de promesa para pasar el bloqueo, el cobro entraba como `PARCIAL`, se distribuían 540.300 (cuota `PARCIAL`, préstamo `EN_MORA`) y se insertaba una gestión de cobranza con una promesa de **16 Gs.** Ahora el redondeo se calcula primero —el mismo orden que `confirmarCancelacionTotal` ya usaba— y la regla vive en un método puro, `esCobroParcial(montoRecibido, totalExigible, aplicaRedondeo)`. **Cota**: `aplicaRedondeo` exige recibir al menos el exacto ya redondeado hacia abajo, y ese exacto nunca es menor que lo exigible, así que lo único que se pasa a aceptar está a lo sumo `redondeoParam - 1` Gs. por debajo de lo exigible — la ventana de perdón de D-141, nunca un pago parcial real de RN-20. **Preexistente** desde el commit `a3659bd` (01/10/2026): sobrevivió 35 días porque el control es **vacío cuando lo exigible es 0**, que es el caso de una cuota que vence hoy (D-144/B98) y el único cobro normal con monto redondeado que se había probado en pantalla (30/10/2026, `REC-2026-000015`). **El humo no lo veía** porque siempre pagaba `data-monto-pagar-cuota` (el exacto) y nunca `data-monto-pagar-cuota-redondeado`, que es con el que la ficha autocompleta *"Recibido"*: 1015/1015 en verde con el camino real bloqueado. Probado en pantalla por Joel y verificado en la base (§9). `mvn test` 912/912 | `675250d` | — |
| 05/11/2026 (B115) | **El aviso "lo recibido no alcanza para N cuotas" tiene un solo dueño, y la pantalla nombra la cantidad.** Estaba escrito dos veces —`ControladorCobranzaServlet` y `cobranza-ficha.js`— y las dos redacciones ya habían divergido: la del navegador decía *"no alcanza para 3 cuotas"* y la del servidor *"para la cantidad de cuotas elegida"*. **Redacción elegida por Joel**: la que nombra la cantidad. La arma `ServicioCobranza.textoNoAlcanzaParaLasCuotas` y viaja a la pantalla en `data-falta-cuotas-singular`/`-plural`; el JS solo reemplaza `{recibido}`, `{n}` y `{monto}` (regla de B92). **El servidor sigue sin nombrar la cantidad, y no es un olvido**: el formulario de confirmación manda `montoSugerido` y `modoAnticipo`, nunca la cantidad, así que nombrarla exigiría un campo oculto que el servidor **no puede cruzar contra el monto** — manipulado, el mensaje diría una cantidad falsa. Es *"una redacción, dos arranques"* (patrón de B91): un solo armador, y lo único que puede cambiar es ese fragmento, con un JUnit que cruza las dos formas y falla si difieren en algo más. Decisión de Joel del 05/11/2026. **Ningún bloqueo cambió**: los dos avisos ya frenaban el cobro igual; esto es solo la redacción. Probado en pantalla por Joel el mismo día (`CC-2026-00015`, 3 cuotas con 300.000 y 1 cuota con 100.000, el singular correcto; §9). `mvn test` 915/915 | `08bdf7d` | — |
| 05/11/2026 (B116) | **El cartel de confirmación del cobro dice "No, volver" en el botón de descartar**, no *"Cancelar"*. Lo encontró Joel comparando dos carteles de la misma sesión: el de desembolso abría con *"No, volver"* y el de cobranza con *"Cancelar"*. No era una regresión — B3/B31 cambió el **default de `plantilla/pie.jsp`** (commit `f585f28`) y los 24 `data-confirmar` ya lo decían, pero el `Swal.fire` de `cobranza-ficha.js` es la **otra familia** y no pasa por ahí (lección de B105). Acá era el peor caso: en una pantalla de cobranza *"Cancelar"* ya significa **cancelación total del préstamo**, así que el botón de descartar se leía como el de una operación, y encima al lado de un *"Sí, cobrar 540.300 Gs."* que sí nombra la operación (B96/B106). Solo el texto del botón: el foco (B105), el resumen del cartel y lo que se envía al servidor no se tocaron. Probado en pantalla por Joel el mismo día (§9) | `c422c50` | — |
| 07/11/2026 (B117) | **Con 1 cuota elegida, el aviso ya no aconseja algo imposible.** El consejo *"Elija menos cuotas"* era un pedazo **fijo** pegado a la cantidad, que **varía** — la misma trampa de B103 — y con N = 1 aconsejaba algo que no existe. Ahora el final depende de la cantidad, dentro del mismo dueño (`ServicioCobranza.armarNoAlcanzaParaLasCuotas`): con **N ≥ 2** sigue diciendo *"Elija menos cuotas o use «Por monto»."* y con **N = 1**, solo *"Use «Por monto»."*. **Una línea, sin parámetro nuevo y sin tocar el JavaScript ni la JSP**: el método de 4 argumentos ya recibía `plural` y el JS ya elegía entre la plantilla singular y la plural según `cuotasElegidas === 1`. El **respaldo del servidor conserva el consejo general**, que sigue siendo el correcto para una cantidad desconocida (ver B115). Sigue partiéndose en título + detalle (B42). Probado en pantalla por Joel el mismo día: `CC-2026-00015`, 1 cuota con 100.000 → *"Lo recibido (100.000 Gs.) no alcanza para 1 cuota (157.350 Gs.). Use «Por monto»."* (§9). `mvn test` 917/917 | `78d3124` | — |
| 07/11/2026 | **B108** (B86, tramo 4): el **nombre de quien firma ya no se corta** en el recuadro de firma del recibo. El campo medía 180 pt y JasperReports no recorta, corta en seco (B83): se leía *"Firma de la cajera (Sofía Martínez"*, sin cerrar el paréntesis. Pasó a **200 pt** arrancando en **x=0**, que conserva el centro en 100 —el mismo de la línea de firma— así que **nada se mueve de lugar**. Medido contra el dato real: los 171 recibos emitidos son todos de *Sofía Martínez* (35 caracteres) y el peor caso posible es de **38**, porque el ADMINISTRADOR puede cobrar y firmar. El ancho se verificó **renderizando** las dos ramas (cobro normal y anticipo), no estimando. Solo geometría: ningún texto, ningún dato y ningún cálculo cambió. Los recibos ya emitidos no cambian (D-94). Probado con el PDF real por Joel el mismo día (§9) | `7225afe` | — |

## 9. Pruebas realizadas en pantalla

### 27/10/2026 (tarde), cajera Sofía Martínez, `CC-2026-00003` — **"Pagar lo vencido" sobre una cuota realmente vencida. Cierra los pasos 12-16.**

**Esta es la prueba que faltaba desde el principio del proyecto.** Lo decía
`06_cobro_mapa_completo.md` §13 (*"el cobro normal de una cuota realmente vencida nunca se probó
en pantalla en todo el proyecto"*) y el pendiente de §10 de este documento (pasos 12-16,
verificados solo por código). El dato se preparó con el desplazamiento de fechas de D-81 (−35 días
sobre `CC-2026-00003`, ver `AVANCE.md` del 27/10/2026): cuota 1 vencida hacía **5 días**.

**Método**: el resultado se calculó **a mano y sellado antes** de abrir la pantalla, por Joel y por
Claude por separado, y después se comparó. Coincidieron los tres. Detalle del método y de la
cuenta de la mora en `RF21_deuda_exigible.md` §9.

**Deuda exigible al empezar: 180.696 Gs.** (capital 165.361 + interés 13.236 + IVA 1.324 +
moratorio 542 + punitorio 163 + IVA de mora 70).

**Los dos cobros de este RF, en orden:**

| | `REC-2026-000009` (13:59) | `REC-2026-000010` (14:02) |
|---|---|---|
| Camino | **Pago parcial con promesa** | **"Pagar lo vencido"** (cobro normal) |
| Recibido | 100.000 | **80.700** |
| Aplicado | 100.000 | **80.696** |
| **Vuelto** | 0 | **50** |
| **Redondeo a favor del cliente** (D-141) | — (nunca en el parcial) | **46** |
| Reparto | moratorio 542 + punitorio 163 + interés 13.236 + IVA 1.394 (RN-10: la mora y el interés primero) + capital 84.665 | **100 % capital** (80.696) |
| Factura | **001-001-0000004** por 15.335 | **ninguna** — "no hay conceptos gravados" |

**Lo que confirma, punto por punto:**

- **El rótulo "Pagar lo vencido"** apareció por primera vez en pantalla con algo vencido de verdad
  (con `CC-2026-00001` nunca se veía, porque no tenía vencidas).
- **El orden de aplicación de RN-10**: el parcial se llevó toda la mora y todo el interés antes de
  tocar capital. Por eso el segundo cobro fue **100 % capital** y **salió sin factura** — el camino
  *"no hay conceptos gravados"*, que hasta hoy solo se había visto por otra causa (RF27, quinta
  prueba, donde el interés del día ya estaba cobrado).
- **El redondeo de D-141 en un cobro normal**: exigible 80.696 → efectivo 80.650, recibido 80.700,
  vuelto 50, y los 46 de diferencia perdonados al cliente. A las cuotas se aplicó el **exacto**
  (80.696), a la caja entró el **redondeado**. Verificado en el cierre de caja del mismo día, que
  cerró con **diferencia 0** (`RF19_cierre_arqueo.md` §9).
- **La cuota 1 quedó `PAGADA`** con 84.665 + 80.696 = 165.361 de capital, su importe completo.
- La promesa que generó el parcial quedó **cumplida** — ver `RF24_seguimiento_mora.md` §9.

**Con esto los pasos 12-16 de §10 quedan probados en pantalla** y el pendiente se cierra (ver §10
para el estado exacto de cada paso: el 15 sigue abierto).

**Evidencia**: `evidencias\2026-10-27\recibos\REC-2026-000009.pdf` (el parcial) y
`evidencias\2026-10-27\recibos\REC-2026-000010.pdf` (el cobro del resto, con el vuelto de 50 y el
redondeo de 46). La factura del parcial, en
`evidencias\2026-10-27\facturas\001-001-0000004.pdf`; el segundo cobro **no tiene factura**, a
propósito. Ver `evidencias\2026-10-27\LEEME.md`.

El recibo del doble clic (`REC-2026-000006`, más abajo) está en
`evidencias\2026-10-27\recibos\REC-2026-000006.pdf`. Su PDF no existía cuando se exportó el resto
—nadie lo había abierto, así que `pagos.id_archivo_recibo` estaba en `NULL`, que es el
comportamiento normal de `obtenerOGenerarPdf`— y se generó al reimprimirlo, por el mismo camino de
siempre. **Ese recibo único es la evidencia de la idempotencia**: hubo dos clics y hay un solo
recibo.

### 27/10/2026 (mañana), `CC-2026-00001` — idempotencia con un doble clic real

**27/10/2026 (Joel), cajera Sofía Martínez, `CC-2026-00001` — idempotencia con un doble clic
real.** Es la **primera vez en todo el proyecto** que se prueba en pantalla el doble clic sobre la
confirmación de un cobro; hasta ahora el control de §6 ("Doble clic / dos pestañas:
`identificadorConfirmacion` único por intento") estaba verificado solo por código y con pruebas
unitarias.

- **Doble clic rápido sobre "Sí, adelantar a capital"** (vía RF27, 228.050 Gs.): **un solo** cartel
  de éxito, recibo **`REC-2026-000006`**, y **una sola fila nueva en el historial** (07:52). **No
  se duplicó.**
- **F5 después de "Aceptar"**: el navegador **no** preguntó por reenviar el formulario, y el
  historial siguió con 2 filas. Es el redirect-después-de-POST de D-89: tras confirmar, el
  servidor responde con un `sendRedirect` a la misma ficha, así que recargar no reenvía nada.

La operación fue un adelanto a capital (RF27), no un "Cobrar" de este RF22 — pero **la
idempotencia es el mismo mecanismo compartido** (un UUID por formulario generado al armar la
ficha, con el `SQLState 23505` como red final, §14.3), así que el resultado vale igual para
`confirmarCobro`. Detalle completo en `RF27_adelantar_capital.md` §9, quinta prueba.

**27/10/2026 (Joel), cajera Sofía Martínez, `CC-2026-00001`** — se probó la pantalla nueva, pero
**no este RF22**: `CC-2026-00001` no tiene nada vencido, así que la opción "Pagar lo vencido" no
aparecía. Lo que sí se vio en esa pasada corresponde a RF27 (adelanto por cantidad de cuotas y por
monto) y está registrado en `RF27_adelantar_capital.md` §9, para no duplicarlo acá. **El rótulo
nuevo "Pagar lo vencido" y el caso con vencidas siguen sin prueba en pantalla** — ver §10.

**12/10/2026 (Joel), cajera, CC-2026-00001 (Mirtha)** — probó "Adelantar a capital" (RF27, no
este RF22 — "Cobrar" no tenía nada vencido para probar hoy). Confirmó en el camino que el
control de caja de este RF22 ("Cobrar" también exige caja abierta y de hoy,
`ServicioCobranza.java` líneas 648-656) es el mismo que ya usa `confirmarAnticipoCapital` — ver
el detalle completo de la prueba (recibo, factura, caja, movimientos) en
`RF27_adelantar_capital.md` §9, para no duplicarlo acá. **"Cobrar" en sí (RF22) sigue sin una
prueba en pantalla propia** — queda para cuando haya una cuota vencida de verdad.

**12/10/2026 (segunda vuelta, tras revisión de Joel del recibo/factura reales)**: aprobadas e
implementadas las correcciones de pantalla de la confirmación unificada
(`cobranza-ficha.js`/`cobranza/ficha.jsp`), compartidas por "Cobrar" (este RF22), "Cancelar
todo" y "Adelantar a capital" (RF27) — ver el detalle completo en `RF27_adelantar_capital.md`
§9, para no duplicarlo acá: el cartel de confirmación ahora nombra la acción en el título y en
el botón ("Sí, cobrar" / "Sí, cancelar todo" / "Sí, adelantar a capital") y muestra "Sin vuelto
(0 Gs.)" cuando corresponde, en las 3 acciones.

## 10. Pendientes y trabajo futuro

- ~~**(27/10/2026, decisión de Joel) Prueba en pantalla con una cuota realmente vencida (pasos
  12-16)**~~ — **probado en pantalla el 27/10/2026 (tarde) con `CC-2026-00003`**, ver §9. Estado
  de cada paso, para no dejarlo ambiguo:
  - **(12)** la ficha con las dos opciones: **cubierto en lo esencial** — se usaron las dos
    ("Pagar lo vencido" y "Todo el préstamo"). El detalle de que el control de cantidad y el
    "Si adelanta…" queden ocultos con vencidas **no quedó registrado** en esa pasada.
  - **(13)** cobrar el total exigible: **cubierto** — `REC-2026-000010` pagó los 80.696 que eran
    todo lo exigible en ese momento.
  - **(14)** cobrar menos que lo exigible con promesa obligatoria (RN-20): **cubierto** —
    `REC-2026-000009`, 100.000 sobre 180.696 exigibles.
  - **(15)** cubrir lo vencido pero no la cuota de hoy (D-146 P-17): **SIGUE PENDIENTE**, y ahora
    se sabe por qué cuesta tanto. Se analizó a fondo el 27/10/2026 sobre `CC-2026-00001`
    (ver `AVANCE.md`): hace falta un préstamo con **una cuota venciendo exactamente hoy y otra ya
    vencida**, y con un desplazamiento de fechas eso **choca con RN-19**. En ese plan, el único
    desplazamiento uniforme que ponía una cuota venciendo hoy era de 55 días —**no múltiplo de
    7**, así que no conserva el día de la semana— y dejaba la cuota 1 **sobre un feriado**
    (29/09/2026, Día de la Victoria de Boquerón) y otra cuota en sábado. Ningún múltiplo de 7
    pone una cuota en el día de hoy. **Conclusión: este paso no se cierra envejeciendo un
    préstamo; necesita uno formalizado con una cuota que caiga justo hoy.** Queda para después de
    la limpieza de la base.
  - **(16)** "Todo el préstamo" con mora (RF26): ~~cubierto con una salvedad~~ — **CERRADO CON
    MORA el 27/10/2026 22:19** con `CC-2026-00001`, desplazado −63 días (ver §9 de
    `RF26_cancelacion_anticipada.md` y `RF21_deuda_exigible.md`). La liquidación llevó
    **moratorio 6.932 + punitorio 2.079 + IVA de mora 901** de dos cuotas vencidas (36 y 8 días),
    dentro de un total de **2.433.459**. La salvedad que había quedado de `CC-2026-00003` (donde
    el parcial previo ya había cobrado la mora) **queda resuelta**.
- **Reprueba formal contra el texto del RF22** ("Cobrar" con una cuota realmente vencida):
  **hecha el 27/10/2026** para el tramo 1-30 días (§9). Los tramos de mora más largos siguen sin
  prueba en pantalla — ver `RF21_deuda_exigible.md` §10.
- **Confirmar cobertura de `ServicioCobranzaTest`** para `confirmarCobro`/`distribuirPago` en
  esta pasada no se revisó en detalle — si falta algo, se agrega al programar.
- ~~**(13/10/2026) Posible unificación de `validarCajaAbiertaHoy`**: `confirmarCobro` repite
  inline el mismo chequeo de caja que `confirmarAnticipoCapital`/`confirmarCancelacionTotal` ya
  comparten en un helper.~~ — **resuelto (19/10/2026, ronda de duplicaciones, Commit 1,
  `55567c7`)**: `confirmarCobro` ahora llama al mismo `validarCajaAbiertaHoy`, que a su vez
  delega en `ServicioCaja.validarSesionDeHoy` (único lugar para los 3 métodos). Ver
  `AVANCE.md`.
- **(12/10/2026) D-141 y "Adelantar capital"**: el redondeo a múltiplos de `credito.redondeo_efectivo`
  (50 Gs., SEDECO) que D-141 documenta para "cobro normal, cancelación total y adelantar
  capital" vive en dos lugares distintos del código: en `ServicioCobranza.calcularRedondeoEfectivo`
  para "Cobrar"/"Cancelar todo", y en `ControladorCobranzaServlet.redondearHaciaAbajo` (en el
  controlador, no en el servicio) solo para el monto sugerido del botón de "Adelantar a
  capital" — porque `confirmarAnticipoCapital` quedó explícitamente afuera de los cambios por
  pedido de Joel. Mismo criterio (piso al múltiplo de 50 Gs.), dos implementaciones; no es un
  error, pero queda anotado para quien lea el código y se pregunte por qué no hay una sola.
- **(12/10/2026) `ServicioConsultaCrediticia` no usa `domicilioSinReferencia`**: al corregir el
  domicilio con referencia coloquial en la factura (RF23, punto C7), se encontró que
  `ServicioConsultaCrediticia.java:355` tiene el mismo defecto (usa `persona.getDomicilio()` sin
  filtrar) en un comprobante de otro RF — no se tocó en esta vuelta (fuera del pedido de Joel,
  cambio mínimo), queda anotado para cuando corresponda revisar ese RF.

## 11. Guion para la defensa

1. Abrir la ficha de un préstamo al día — mostrar que no hay nada que "Cobrar" (RF20/RF21).
2. Intentar "Cobrar" sobre Mirtha (sin vencidas) — mostrar el rechazo con el mensaje que sugiere
   "Adelantar capital".
3. (Con una cuota vencida real) cobrar el total exigible — mostrar el recibo, la factura (si
   corresponde) y el movimiento de caja.
4. (Con una cuota vencida real) cobrar menos de lo exigible sin fecha de promesa — mostrar el
   rechazo; con fecha de promesa — mostrar el cobro parcial y la gestión de cobranza creada.
5. Explicar el orden RN-10 (moratorio → punitorio → interés → capital) con el recibo en mano.
