# RF26 — Cancelación anticipada (monto para cancelar)

**Estado: probado en pantalla el 12/10/2026 (tag `rf26-probado`).**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF26 (módulo Cancelación): *"El sistema debe calcular el monto para
cancelar el préstamo por anticipado, cobrando solo los intereses devengados hasta la fecha y sin
intereses futuros, y registrar la cancelación total."*

Reglas relacionadas: **RN-11** ("Pago anticipado parcial o total sin cobrar intereses futuros ni
penalidad, Ley 6624/2020. El anticipo parcial reduce la cantidad de cuotas y mantiene el monto de
la cuota."); **RN-12** ("Saldo total en cero → préstamo FINALIZADO. La devolución del pagaré se
registra aparte, con constancia firmada." — ver `RF29_cierre_documental.md`); **RN-21** (el
excedente sobre lo exigible se devuelve como vuelto o se aplica a capital, nunca a la próxima
cuota).

Fórmula exacta del anteproyecto (Doc. 00 §6.6):

```
total a cancelar a la fecha F =
todo lo vencido (capital + interés + IVA + mora pendiente y del tramo)
+ capital pendiente de las cuotas no vencidas
+ interés devengado del período en curso = redondear( saldo × i × días_transcurridos / días_del_período )
+ IVA de ese interés
Intereses de los períodos futuros: 0 (Ley 6624/2020). Penalidad: 0.
```

## 2. Cómo funciona

**(27/10/2026) Lo único que cambió con la ampliación pedida por el tutor: el nombre del botón.**
"Cancelar todo" pasó a llamarse **"Todo el préstamo"** y dejó de ser un botón suelto al costado:
es la **tercera opción** de la pregunta "¿Cuánto quiere pagar el cliente?" (ver
`RF27_adelantar_capital.md` §2a). El motivo del rótulo es que "Cancelar todo" se confundía con
"cancelar la operación" (familia B3). El cartel de confirmación dice *"Confirmar: Todo el
préstamo"* / **"Sí, cobrar todo el préstamo"**.

**Nada de la lógica se tocó**: `POST /cobranza/{id}/cancelar`, `confirmarCancelacionTotal`, la
fórmula, el redondeo D-141, la constancia `CAN-…` (RF28) y el cierre documental (RF29) quedaron
exactamente como estaban. Dos detalles nuevos, los dos de presentación:

- **Sigue disponible con cuotas vencidas** (es un caso real): con mora, la ficha muestra
  "Pagar lo vencido" + "Todo el préstamo".
- **Desde el control de cantidad de cuotas, elegir N = K** (todas las que le quedan) pasa a este
  mismo flujo, con este mismo monto y este mismo formulario — pagar todas las cuotas que quedan no
  es un adelanto. Se lee como "12 cuotas = todo el préstamo". Decisión de Joel, 27/10/2026.
- Al elegir esta opción **desaparecen** el control de cantidad y el texto "Si adelanta…" del
  adelanto, que antes quedaban a la vista sin sentido — eso cierra el hallazgo **B74**.

El botón "Cancelar todo" (`cobranza/ficha.jsp`) muestra, **antes** de confirmar, la vista previa
real (`ServicioCobranza.calcularPreviewCancelacionTotal` → `ServicioMotorFinanciero.
calcularCancelacionTotal`): todo lo vencido (capital + interés + IVA + mora), el capital de las
cuotas no vencidas, el interés devengado del período en curso (prorrateado por días
transcurridos/días del período) + su IVA, y el total. Al confirmar, `ServicioCobranza.
confirmarCancelacionTotal` recalcula todo de nuevo (nunca confía en la vista previa), dentro de
una sola transacción:

1. Valida la clave de idempotencia (`identificadorConfirmacion`) y, si ya existe, devuelve el
   resultado anterior sin repetir nada (doble clic no duplica).
2. Exige préstamo ACTIVO o EN_MORA, caja ABIERTA y de hoy.
3. Aplica el redondeo del efectivo (`credito.redondeo_efectivo`, mismo criterio SEDECO que
   `confirmarCobro`).
4. Asigna el número de constancia `CAN-aaaa-nnnnnn` y el de recibo `REC-aaaa-nnnnnn` en la misma
   transacción (correlatividad real, D-93/D-94).
5. Aplica el pago a cada cuota (vencidas: capital + interés + mora; no vencidas: solo capital,
   salvo la primera, que además lleva el interés devengado del período en curso — RN-11, "sin
   intereses futuros" en las demás).
6. Marca todas las cuotas pendientes como PAGADAS, registra los movimientos de caja, emite
   factura si corresponde (interés/mora gravado > 0, RF23), marca el préstamo FINALIZADO
   (`PrestamoDAO.marcarFinalizado`) y resuelve cualquier promesa de pago abierta (RF24 — una
   cancelación total no puede dejar una promesa vigente, Bloque 4).
7. Auditoría `CANCELAR_TOTAL`.

## 3. Auditoría

`ServicioAuditoria.registrar(..., "CANCELAR_TOTAL", "PAGO", idPago, ...)`, dentro de la misma
transacción que el pago — mismo criterio que el resto de las operaciones de dinero (el registro
en `pagos`/`liquidaciones_anticipadas` es en sí mismo inmutable, triggers `BEFORE DELETE`).

## 4. Dónde está

- **Motor financiero**: `ServicioMotorFinanciero.calcularCancelacionTotal` (Sec. 6.6 exacta, con
  `buscarCuotaConTeoricaPasada`/D-88 para no dejar un hueco de 1-3 días sin devengar interés).
- **Servicio**: `ServicioCobranza.calcularPreviewCancelacionTotal` (vista previa),
  `.confirmarCancelacionTotal` (confirma, transacción completa).
- **Controlador**: `ControladorCobranzaServlet.procesarCancelar` (`/cobranza/{id}/cancelar`,
  `POST`); la vista previa se calcula al cargar `GET /cobranza/{id}` (línea ~532).
- **Vista**: `cobranza/ficha.jsp` (botón "Cancelar todo" + vista previa).
- **DTO**: `ResultadoLiquidacion` (capital, interés devengado, IVA, moratorio/punitorio, total).
- **Modelo**: `LiquidacionAnticipada` (`liquidaciones_anticipadas`, `tipo_liquidacion = 'TOTAL'`).
- **Parámetros**: `credito.dias_gracia_mora`, `credito.redondeo_efectivo` (ninguno nuevo).

## 5. Dependencias

- **RF20/RF22** (ficha de cobranza unificada): mismo botón, mismo mecanismo de confirmación
  (`cobranza-ficha.js`).
- **RF23** (factura): si el interés/mora gravado es > 0, se emite con el mismo mecanismo que el
  resto de `ServicioCobranza` — ver `RF23_recibo_factura.md`.
- **RF27** (adelantar capital): comparte el mismo `ServicioMotorFinanciero` y el mismo tipo de
  liquidación (`TipoLiquidacion`); ver `RF27_adelantar_capital.md` §6 (ya probado en pantalla,
  misma familia de cálculo).
- **RF24** (mora): una cancelación total resuelve cualquier promesa de pago abierta
  (`actualizarPromesasCumplidas`).
- **RF28** (constancia + finalizado): la cancelación total es la que dispara el estado
  FINALIZADO y el número `CAN-` que usa la constancia — ver `RF28_constancia_cancelacion.md`.
- **RF15/RF18/RF19** (caja): exige caja ABIERTA y de hoy, mismo criterio que RF22/RF27.

## 6. Análisis

**a) ¿Cumple "cobrando solo los intereses devengados hasta la fecha y sin intereses futuros"?**
**Sí**, verificado contra el código: el único interés que se cobra de las cuotas NO vencidas es
el del período en curso, prorrateado por días transcurridos (`interesTeoricaPasada` +
`interesPeriodoEnCurso`, líneas 709-787 de `ServicioMotorFinanciero`) — ninguna cuota futura
aporta interés. Esto reproduce exactamente la fórmula de Doc. 00 §6.6 y el caso BF-09 (capital
336.642, interés 336.642 × 2,011776 % × 10/30 = 2.257, IVA 226, total 339.125), cubierto por los
tests `testBF09_CancelacionTotal*` en `ServicioMotorFinancieroTest`.

**b) Historial de bugs reales ya encontrados y corregidos en esta misma fórmula (relevante para
la defensa — no son hipótesis, son incidentes reales con Joel probando a mano)**:
  - **D-83** (26/09/2026): con cuotas vencidas pendientes, `cuotaEnCurso` devolvía la cuota
    vencida en vez de la primera no vencida, duplicando el interés del período en curso
    (3.260.319 calculado vs. ≈3.150.112 esperado). Corregido: `cuotaEnCurso` ahora exige
    `!cuota.getFechaVencimiento().isBefore(fecha)`. Tests `testBF13`/`testBF14`.
  - **D-88**: una cuota no vencida (fecha efectiva) pero con fecha TEÓRICA ya pasada (hueco de
    RN-19, 1-3 días) no devengaba su interés completo. Corregido con
    `buscarCuotaConTeoricaPasada`.
  - **D-144** (02/10/2026, Ley 6624/2020): hallazgo de Joel con María González (CC-2026-00007,
    datos reales en `sgcpc_db_pruebas`) en un diseño relacionado (pago anticipado de una cuota NO
    vencida dentro de "Cobrar", ya retirado) — el saldo que seguía siendo deuda vigente no
    generaba interés durante el hueco de días, pérdida real ≈ Gs. 83.339. **No afecta
    directamente a `calcularCancelacionTotal`** (esa función siempre calculó el interés devengado
    sobre el saldo completo, nunca sobre una cuota aislada), pero confirma que esta fórmula ya
    fue sometida a verificación cruzada estricta por Joel con datos reales, no solo con JUnit.
  Conclusión: la fórmula **cumple** el texto firmado y tiene un historial de verificación sólido
  — no es optimismo de implementación, es código ya puesto a prueba contra casos reales y
  corregido donde fallaba.

**c) ¿Depende de fechas?** **Sí, fuertemente** — el resultado (capital vencido vs. no vencido,
interés devengado del período en curso, mora) depende de `fecha` (siempre `Reloj.hoy()` en
producción) relativa a las fechas de vencimiento del plan de cuotas. Esto es inherente al texto
del RF ("hasta la fecha") y no es un defecto: cualquier préstamo de prueba necesita cuotas con
vencimientos ya pasados (para ver capital vencido + mora) y una cuota no vencida con período en
curso (para ver el interés devengado prorrateado) para ejercitar la fórmula completa en pantalla.
Un préstamo recién formalizado (todo no vencido, día 1 del período) solo ejercita la rama
"interés devengado del período en curso" con un número muy chico (1 día de 30-45), sin capital
vencido ni mora — sirve para ver el flujo pero no para verificar visualmente los 3 componentes
de la fórmula a la vez.

**d) ¿Algo miente o confunde?** No se encontró nada que contradiga el texto firmado. Un punto que
conviene aclarar en la prueba en pantalla (no es un error, es una posible confusión de lectura):
el "ahorro del cliente" (Doc. 00 §6.6, columna final del ejemplo BF-09) **no se muestra como un
dato separado en la ficha** — se puede derivar restando el total cancelado de la suma de las
cuotas pendientes, pero no hay un campo "Ahorro: Gs. X" explícito. El anteproyecto no lo exige
como un campo de pantalla (solo aparece en la tabla de ejemplo de la especificación), así que no
es un incumplimiento — se anota para que Joel decida si quiere agregarlo como mejora (§10).

## 7. Decisiones

Ninguna nueva en este análisis — la fórmula y el flujo ya estaban decididos e implementados
desde E7/D-144. Este documento es el primer recorrido formal contra el texto firmado.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| 02/10/2026 | D-144: se retira el pago anticipado de una cuota dentro de "Cobrar" (no afecta a esta función directamente, pero es la corrección más reciente en la misma familia de cálculo). | — | `d145-caja-simple` |
| 12/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código — la fórmula ya cumple. | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

### 27/10/2026 22:19 (Joel), `CC-2026-00001` — **cancelación total CON MORA en la liquidación**

**Cierra la salvedad que había quedado unas horas antes** con `CC-2026-00003` (más abajo), donde
la cancelación no llevó mora porque un pago parcial previo ya la había cobrado por RN-10. Acá la
mora entró **en la propia liquidación**, que es lo que exige el paso 16 de
`RF22_cobro_efectivo.md` §10.

El dato se preparó con el desplazamiento de D-81, **−63 días** (múltiplo de 7, sin ningún
vencimiento en sábado, domingo ni feriado), dejando **dos cuotas vencidas con mora impaga**: la 1
hacía 36 días y la 2 hacía 8. Método: resultado calculado **a mano y sellado antes** de la
pantalla, por Joel y por Claude por separado.

| Componente de la liquidación | Monto |
|---|---|
| Deuda exigible (cuotas 1 y 2, **con su mora**) | **586.521** |
| └ moratorio / punitorio / IVA de la mora | **6.932** / **2.079** / **901** |
| Capital **no** vencido (cuotas 3 a 9) | **1.836.451** |
| Interés devengado del período en curso | **9.534** |
| IVA de ese interés | **953** |
| **Total de la liquidación** | **2.433.459** |
| **Efectivo cobrado** | **2.433.450** (redondeo a favor del cliente: **9**) |

En pantalla apareció el **cartel de confirmación en su opción (c)** — la que distingue "Cobra en
efectivo" de "Cancela la deuda de", con el redondeo explicitado — que es justamente el caso para
el que se diseñó (D-141, decisión 2 de la ronda del 27/10).

**Documentos**: recibo **`REC-2026-000012`** con el **detalle de las 9 cuotas**, factura
**001-001-0000006** con **7 renglones** (compensatorio, moratorio y punitorio de las cuotas 1 y 2,
más el compensatorio de la cuota 3) por **117.644** con **10.695** de IVA, y constancia
**CAN-2026-000003**. Préstamo **`FINALIZADO`**.

**Verificado después contra la base** (solo lectura): `liquidaciones_anticipadas` id 10, tipo
`TOTAL`, `monto_vencidas_impagas` 586.521, `saldo_capital_fecha` 2.315.815 (el capital total
pendiente, incluido el de las dos cuotas vencidas), `interes_devengado` 9.534,
`tributos_aplicables` 953, `interes_moratorio` **6.932**, `interes_punitorio` **2.079**,
`iva_moratorio` 693, `iva_punitorio` 208, total **2.433.459**, `CAN-2026-000003`, `APLICADA`. Las
9 filas de `aplicaciones_pago` reparten el pago cuota por cuota.

**Evidencia**: `evidencias\2026-10-27\constancias\CAN-2026-000003.pdf`,
`evidencias\2026-10-27\recibos\REC-2026-000012.pdf` y
`evidencias\2026-10-27\facturas\001-001-0000006.pdf`.

### 27/10/2026 (Joel), cajera Sofía Martínez, `CC-2026-00003` — cancelación total **por la vía nueva**, sobre un préstamo que estuvo en mora

Primera cancelación total que se llega a ejecutar desde el **selector nuevo de tres opciones** de
la ficha (la ampliación del 27/10/2026, RF27 §2a) y no desde el botón "Cancelar todo" anterior.
También la primera sobre un préstamo que había estado `EN_MORA` ese mismo día.

| | |
|---|---|
| Recibo | **`REC-2026-000011`** (14:04) |
| Cuotas liquidadas | 2, 3 y 4 |
| Capital no vencido | **514.474** |
| Interés devengado del período en curso | **1.725** |
| IVA del interés | **173** |
| **Total de la liquidación** | **516.372** |
| **Efectivo cobrado** | **516.350** |
| **Redondeo a favor del cliente** (D-141) | **22** |
| Factura | **001-001-0000005**, por 1.898 (1.725 + 173) |
| Constancia de cancelación | **CAN-2026-000002** |
| Estado del préstamo | **`FINALIZADO`** 14:04:14, las 4 cuotas `PAGADA` |

**Confirma la fórmula de §6a con un caso nuevo**: los 514.474 de capital son exactamente el
`saldo_capital_posterior` de la cuota 1 que la ficha mostraba antes del cobro, y el interés del
período en curso se cobró solo por los días corridos. **El capital no se factura** (RN-25): la
factura 0000005 lleva únicamente el interés y su IVA.

**Salvedad, para que no se lea de más**: el préstamo **había estado** en mora, pero la cancelación
en sí **no llevó moratorio ni punitorio**, porque el pago parcial de las 13:59 ya los había
cobrado por el orden de RN-10 (ver `RF22_cobro_efectivo.md` §9). **Esa salvedad quedó resuelta esa
misma noche** con la cancelación de `CC-2026-00001` de las 22:19, más arriba, donde la mora sí
entró en la liquidación.

**Verificado después contra la base** (solo lectura): `liquidaciones_anticipadas` id 9, tipo
`TOTAL`, capital 514.474, interés 1.725, IVA 173, total 516.372, `numero_constancia`
**CAN-2026-000002**, estado `APLICADA`, PDF guardado.

**Evidencia**: `evidencias\2026-10-27\constancias\CAN-2026-000002.pdf`,
`evidencias\2026-10-27\recibos\REC-2026-000011.pdf` y
`evidencias\2026-10-27\facturas\001-001-0000005.pdf`. La constancia dice **"Fecha de desembolso:
21/09/2026"** (la fecha corrida por el desplazamiento de D-81), y es el motivo de **D-157**: no se
vuelven las fechas atrás porque este documento ya emitido las cita. Ver
`evidencias\2026-10-27\LEEME.md`.

### 12/10/2026

**12/10/2026, prueba de Joel (cajera), CC-2026-00026 (EN_MORA, cuota 1 parcial, cuota 2 vencida,
cuota 3 con período en curso)**: calculado de antemano por solo lectura (sin tocar nada) el
monto exacto esperado de "Cancelar todo" — 847.639 Gs. (vencido con mora de las cuotas 1+2:
539.897; capital no vencido de la cuota 3: 305.994; interés devengado del período en curso +
IVA: 1.748) — y la pantalla dio **exactamente ese número**. Confirma que la fórmula de §6a no
solo es correcta en el análisis estático: coincide hasta el guaraní con el cálculo manual
independiente hecho antes de ver la pantalla.

## 10. Pendientes y trabajo futuro

> **Revisado el 15/11/2026 (B202, pedido de Joel).** De los tres pendientes que tenía esta sección,
> **dos ya estaban hechos** y seguían acá pidiendo algo que el sistema hacía — un pendiente falso
> cuesta el mismo tiempo de lectura que uno real, y encima manda a buscar un defecto que no existe.
> El tercero dejó de ser "opcional": entró como ítem. Queda el detalle de cada uno abajo.

- ~~**(opcional, a decisión de Joel)** Mostrar el "ahorro del cliente" como un dato explícito en la
  ficha de cancelación.~~ → **Ya no es opcional: es el ítem B204** (tanda 2 de la revisión UX de
  Cancelación, decisión de Joel del 15/11/2026: *"entra, no se descarta"*). Llevaba desde el
  12/10/2026 anotado como "posible mejora"; el informe de UX lo volvió a encontrar porque el
  **adelanto** sí muestra el ahorro y la cancelación —que es donde más se ahorra, por la Ley 6624—
  no. Ver su fila en `recorrido/FASE2_PLAN.md`.
  - **Aplicado el 15/11/2026 (B203 + B204)**, y la fórmula la eligió Joel con los números medidos a
    la vista. La ficha ahora abre un *"Ver detalle de la cancelación"* con el desglose de §6
    —capital, interés devengado y su IVA, y la mora y los punitorios **nombrados** cuando los
    hay—, el total, y como último renglón el ahorro: *"El cliente se ahorra X de intereses por
    cancelar hoy."*. El ahorro es **el interés futuro no devengado** (Σ interés pendiente + su IVA
    de las cuotas **no** vencidas − el interés que esta liquidación cobra), **no** "lo que faltaba
    del plan menos el total": esa resta le descuenta la mora al ahorro —con 5 cuotas vencidas daba
    44.559 donde el interés que de verdad no se cobra es 135.647— y la mora es un castigo, no un
    ahorro menos. Sin cuotas vencidas las dos fórmulas dan el mismo número, así que esto no
    contradice al ahorro del adelanto (RF27/RN-11). Lo calcula el motor dentro de
    `calcularCancelacionTotal` y viaja en `ResultadoLiquidacion.getInteresFuturoNoCobrado()`;
    **es informativo y no interviene en ningún cobro** — la fórmula de §6 no cambió.
- ~~**(12/10/2026, pedido de Joel)** Precargar el campo "Recibido" con el monto en efectivo
  (múltiplo de 50) en vez del monto exacto.~~ → **RESUELTO**, y hace tiempo: lo cubren **D-141** y
  **B114** con `data-monto-cancelar-todo-redondeado` (lo deja el servidor en la tarjeta) y
  `autocompletarRecibido` de `recursos/js/cobranza-ficha.js`, que precarga ese monto ya redondeado
  al elegir la opción. Aplica igual a "Pagar cuota", como pedía la nota. **Verificado el 15/11/2026
  leyendo las dos piezas**; la nota quedó viva 34 días después de estar hecha.
- **(13/10/2026, hallazgo de Joel, RF38)** El menú tenía "Cancelación anticipada" apuntando a
  `/cobranza` sin distinguirse de "Cobranza" (mismo screen, mismo nombre de módulo al llegar) —
  es un problema de **navegación del menú**, no de este RF (la fórmula/cálculo de §6 no se vio
  afectada). **Corregido y probado en pantalla el mismo día (D3, ver `RF38_perfiles.md` §7/§9,
  tag `rf38-probado`)**: la ruta pasa a `/cobranza?origen=cancelacion-anticipada`, con un aviso y
  título propios en `cobranza/busqueda.jsp` y el menú marcando "Cancelación anticipada" en vez de
  "Cobranza" — sin tocar `ControladorCobranzaServlet` ni `ServicioCobranza`, no reabre
  `rf26-probado`.
  - ~~Queda un ajuste de texto menor pendiente (el aviso repite el título), ver
    `RF38_perfiles.md` §10.~~ → **RESUELTO**: lo arregló el commit `e226150` y
    `RF38_perfiles.md:398` ya lo registra *("ya no repite el título")*. Esta copia era la que
    seguía viva.
  - **Lo que sí estaba roto, y se arregló el 15/11/2026 (B198)**: ese aviso mandaba a tocar
    «Cancelar todo», y el botón se llama **«Todo el préstamo»** desde B106 (05/11/2026). Ahora el
    aviso imprime el rótulo del **mismo dueño que el botón**
    (`ServicioCobranza.OPCION_TODO_EL_PRESTAMO`), así que un renombre futuro no puede volver a
    dejarlo atrás. Y **B201** completó la navegación: el `origen` viaja en los enlaces a la ficha,
    la ficha lo dice y su "Volver" lo conserva, con el valor y la comparación en un solo dueño
    (`ORIGEN_CANCELACION_ANTICIPADA` / `esOrigenCancelacionAnticipada`) en vez del literal repetido
    en cuatro vistas.

## 11. Guion para la defensa

"RF26 exige calcular el monto de cancelación anticipada cobrando solo el interés devengado hasta
hoy, sin intereses futuros (Ley 6624/2020). El sistema separa capital vencido, capital no
vencido e interés del período en curso (prorrateado por días transcurridos sobre días del
período), sin tocar las cuotas futuras — exactamente la fórmula del anteproyecto (§6.6). Esta
fórmula ya fue puesta a prueba con casos reales (D-83, D-144) y corregida donde falló, antes de
esta etapa de consolidación."
