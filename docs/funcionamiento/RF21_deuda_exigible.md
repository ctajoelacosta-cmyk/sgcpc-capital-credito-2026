# RF21 — Cálculo de la deuda exigible (cuotas vencidas, moratorio, punitorio, IVA)

**Estado: SOLO ANÁLISIS (13/10/2026), sin tocar código.** Esperando la aprobación de Joel antes
de programar cualquier cosa.

**Aviso (pedido explícito de Joel, "si difiere... avisame")**: el texto que pasó Joel es *"El
sistema debe calcular la deuda exigible a la fecha, con las cuotas vencidas, el interés
moratorio y punitorio, y el IVA correspondiente."* El texto real en `docs/RF_ANTEPROYECTO.md`
tiene 5 palabras más: **"...el interés moratorio y punitorio SEGÚN LOS DÍAS DE ATRASO, y el IVA
correspondiente."** Uso el texto real (§1) para el análisis — la diferencia no cambia nada de lo
ya implementado (el cálculo siempre fue por días de atraso), pero la aviso como pediste.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF21 (módulo Cobranza y mora): *"El sistema debe calcular la deuda
exigible a la fecha, con las cuotas vencidas, el interés moratorio y punitorio según los días de
atraso, y el IVA correspondiente."*

Reglas de negocio relacionadas (`docs/00_especificacion_maestra.md`):
- **RN-16**: "Interés moratorio: tasa ≤ tasa del préstamo, solo sobre capital vencido, sin
  capitalizar. Punitorio: ≤ 30 % del moratorio. Ambos gravados con IVA. Valores iniciales al
  máximo legal, aprobados por el cliente."
- **RN-10**: dentro de una cuota, el orden de aplicación de un pago es moratorio → punitorio →
  interés → capital (cada uno con su IVA) — RF22, no este documento, pero la deuda exigible que
  calcula RF21 es la que ese orden consume.
- Doc. 00 §6.4 (Mora: moratorio y punitorio) trae la fórmula exacta — es, literalmente, la misma
  fórmula que el código usa hoy (ver §6).

## 2. Cómo funciona

El cálculo se dispara cada vez que se abre la ficha de cobranza de un préstamo
(`GET /cobranza/{idPrestamo}`) y en cualquier operación de cobro/reversión que necesite saber
cuánto se debe — **nunca a partir de un estado guardado, siempre recalculado desde las fechas**
(comentario del propio código: *"Deuda al día (Doc. 10 ficha de cobranza): siempre a partir de
las fechas, nunca del estado guardado."*). Por cada cuota con `fecha_vencimiento` anterior a hoy
y que no esté `PAGADA`:
1. Se cuentan los días de atraso desde el vencimiento (o desde `fecha_corte_mora`, si hay una
   reversión que recortó el tramo — RF25).
2. Si esos días están dentro de `credito.dias_gracia_mora` (hoy 0), la mora del tramo es cero.
3. Si no, se calcula moratorio + su IVA, punitorio + su IVA (fórmula en §6).
4. Se suma el capital pendiente + interés pendiente + IVA del interés + moratorio + IVA moratorio
   + punitorio + IVA punitorio de esa cuota — ese es el total exigible de la cuota.
5. El total general es la suma de todas las cuotas vencidas impagas.

La pantalla (`cobranza/ficha.jsp`, tarjeta "Deuda exigible hoy") muestra una fila por cuota
vencida con columnas Cuota/Capital/Interés/Moratorio/Punitorio/IVA 10 %/Total — el IVA mostrado
es la suma de los 3 IVA (interés + moratorio + punitorio) en una sola columna. Si no hay cuotas
vencidas, la tarjeta no aparece (RF20 ya cubre el caso "al día" con la próxima cuota, no esta
tarjeta).

## 3. Auditoría

Ninguna: es un cálculo de lectura, se repite cada vez que se mira la ficha, no se guarda ningún
"cálculo de deuda exigible" como evento propio. (El cobro que después se aplica contra esa deuda
sí audita — RF22.)

## 4. Dónde está

- **Motor único**: `ServicioMotorFinanciero.calcularMora` (moratorio/punitorio de una cuota, un
  tramo de días) y `.calcularDeudaExigible` (recorre todas las cuotas vencidas de un préstamo y
  suma) — **todo el cálculo vive acá**, nada de esto está duplicado en `ServicioCobranza`, en el
  controlador, en el JSP ni en SQL (REGLAS_DESARROLLO.md §5). `ServicioCobranza.
  calcularDeudaExigible(Connection, idPrestamo, fecha)` es solo un envoltorio: lee
  `credito.dias_gracia_mora`, carga el préstamo para el motor y delega.
- **Controlador**: `ControladorCobranzaServlet.mostrarFicha` (`/cobranza/{id}`, `GET`) — pone el
  `ResultadoDeudaExigible` en el request como `"exigible"`.
- **Vista**: `cobranza/ficha.jsp`, tarjeta "Deuda exigible hoy".
- **DTO**: `ResultadoDeudaExigible` (lista de `DetalleCuotaExigible` + total general);
  `DetalleCuotaExigible` (capital/interés/IVA interés/moratorio/IVA moratorio/punitorio/IVA
  punitorio/total, por cuota); `ResultadoMora` (un tramo: días, moratorio/punitorio del tramo y
  acumulado con lo ya pendiente de la cuota).
- **Modelo**: `Cuota.capitalPendiente()`/`.interesPendiente()` (programado − pagado, ya resuelve
  parciales); `PrestamoCredito`/`Prestamo` (motor) con `tasaMoratoria`/`tasaPunitoria`, snapshot
  de la formalización (ver §6).
- **Parámetros** (`parametros_sistema`, nunca hardcodeados): `credito.factor_moratorio` (1,000000
  — moratoria = TEA del préstamo × este factor), `credito.porcentaje_punitorio` (0,300000 — 30 %
  del moratorio), `credito.dias_gracia_mora` (0 días de tolerancia antes de que corra la mora).

## 5. Dependencias

- **RF20** (lista de trabajo): el botón "Cobrar" de la tarjeta "En mora" lleva a esta misma
  ficha; `ServicioMora.listarPrestamosEnMora` usa el mismo `monto_exigible` para esa tarjeta.
- **RF22** (cobro en efectivo): consume el `ResultadoDeudaExigible` para calcular cuánto hay que
  cobrar y cómo se distribuye (RN-10).
- **RF23** (recibo y factura): la factura muestra intereses/moratorio/punitorio con su IVA
  (RN-25) — mismos montos que calculó este RF21, no un cálculo propio.
- **RF24** (gestiones de mora, `/mora`): columna "Monto exigible" de `ServicioMora`, mismo motor.
- **RF25** (reversión de un cobro): `fecha_corte_mora` que usa `calcularMora` para no recontar
  mora ya cobrada y revertida.
- **RF34** (estado de cuenta): probablemente muestra esta misma deuda exigible histórica — no lo
  confirmé en este análisis, lo reviso cuando toque RF34.
- **RF05/RF09/RF11** (tope de tasa al origen, RN-15): la TEA que `credito.factor_moratorio`
  multiplica ya pasó por el control de usura al aprobar/formalizar — ver §6 sobre si hace falta
  un control aparte durante la mora.

## 6. Análisis (13/10/2026, pedido de Joel)

**Dónde está el cálculo**: confirmado, **solo en `ServicioMotorFinanciero`**
(`calcularMora`/`calcularDeudaExigible`, públicos; `iva`/`redondear`/`potencia`/`dividir`,
utilidades privadas/de paquete del mismo motor). `ServicioCobranza` no recalcula nada: carga el
préstamo, lee el parámetro de días de gracia y llama al motor. El JSP solo imprime lo que ya
viene calculado. Ningún SQL hace la cuenta.

**Fórmula del moratorio y punitorio** (idéntica al código y a Doc. 00 §6.4):
```
días      = fecha_actual − fecha_vencimiento_de_la_cuota (o fecha_corte_mora si es posterior)
moratorio = redondear( capital_pendiente × ( (1 + tasa_moratoria)^(días/365) − 1 ) )
punitorio = redondear( moratorio × porcentaje_punitorio )
iva_moratorio = redondear( moratorio × 10 % )
iva_punitorio = redondear( punitorio × 10 % )
```
- **Tasas**: `tasa_moratoria` y `tasa_punitoria` **se calculan una sola vez, al formalizar el
  préstamo** (`calcularTasasMora`, D-82) y quedan **congeladas** en el préstamo
  (`prestamos.tasa_moratoria`/`tasa_punitoria`) — si Joel cambia `credito.factor_moratorio` o
  `credito.porcentaje_punitorio` después, los préstamos ya formalizados NO cambian, solo los
  nuevos. `tasa_moratoria = TEA_aprobada × factor_moratorio` (hoy factor = 1, o sea moratoria =
  TEA exacta — cumple RN-16, "tasa ≤ tasa del préstamo", en el límite). `tasa_punitoria =
  porcentaje_punitorio` tal cual (0,30 = 30 %), **nunca** multiplicada por la moratoria — hay un
  comentario del código (D-82) marcando que esto fue un bug ya corregido ("antes se guardaba
  tasaMoratoria × porcentajePunitorio, dejando el punitorio real en ~8,1 % en vez de 30 %").
- **Base de cálculo**: `capital_pendiente` de la cuota (capital programado − capital ya pagado,
  nunca el total de la cuota con interés) — así que el moratorio es **solo sobre capital
  vencido**, tal cual exige RN-16 (no sobre interés ni sobre el total de la cuota).
- **Días**: corridos (no hábiles), desde el vencimiento REAL de la cuota (ya corrido por feriado/
  fin de semana si correspondía, RN-19) o desde `fecha_corte_mora` si una reversión recortó el
  tramo (RF25) — nunca negativos (`Math.max(0, ...)`).
- **"Sin capitalizar" (RN-16)**: la base de cada tramo es siempre `capital_pendiente` de la
  cuota, un valor fijo — el moratorio de un día no se suma a la base del día siguiente (no hay
  interés sobre interés). El exponente `días/365` es la forma de convertir la tasa ANUAL en la
  tasa exacta de ese tramo de días (misma técnica que ya usa el motor para TEA→TNA), no
  "capitalización" en el sentido de la ley.
- **Punitorio**: no es un porcentaje aparte de nada — es, literalmente, el 30 % del moratorio ya
  calculado (`moratorio × porcentaje_punitorio`), como dice RN-16 ("≤ 30 % del moratorio").

**IVA sobre qué**: sobre moratorio y sobre punitorio, cada uno por separado y redondeado antes
de sumar (`iva(moratorioTramo)`, `iva(punitorioTramo)` — 10 % fijo, `TASA_IVA` del motor). En la
deuda exigible de la cuota también se suma el IVA del **interés** pendiente (ese interés es el
contractual de la cuota, no de la mora, pero también lleva IVA — RN-16 dice "ambos gravados con
IVA" refiriéndose a moratorio/punitorio; el interés contractual ya llevaba IVA desde RF01/RF05).
El **capital nunca lleva IVA**, ni el pendiente ni el moratorio en sí (solo su IVA aparte) —
coincide con RN-25 ("el capital se documenta en el recibo, no en la factura").

**Tope de tasa (Ley 6624/usura)**: `ServicioMotorFinanciero.validarLimiteTasa` existe y se usa en
RF05/RF09/RF11 — pero **solo valida la TEA al aprobar/formalizar/desembolsar** (RN-15), nunca
durante la cobranza. No hay ningún control que re-valide, día a día, que `tasa_moratoria` (= TEA
congelada) siga por debajo del tope vigente del BCP si el tope bajara después de formalizado. Es
coherente con que la tasa moratoria queda congelada al formalizar (mismo criterio que el resto
del préstamo, D-82) — pero lo anoto en §10 porque no lo vi documentado en ningún lado hasta
ahora: si el tope legal baja y hay préstamos en mora formalizados con la tasa vieja, el sistema
no los re-valida ni avisa.

**Redondeo**: cada concepto se redondea a guaraní entero (`HALF_UP`) **antes** de sumarlo al
siguiente — no hay un redondeo final sobre la suma. Capital/interés pendientes ya vienen
redondeados de la cuota original (no se redondean de nuevo acá).

**Corrección (13/10/2026) a lo que decía este párrafo el 12/10/2026** — afirmaba "hoy (12/10/
2026) ningún préstamo tiene una cuota vencida de verdad, no hay manera de ver la tarjeta con
números reales sin una cuota realmente vencida". **Era un error**: no se repitió el `SELECT`
sobre `sgcpc_db_pruebas` para esta sesión (13/10/2026) antes de afirmarlo — se generalizó la
conclusión del día anterior (válida ese día, con el ejemplo de Mirtha CC-2026-00001, que en
efecto no tiene nada vencido) a "ningún préstamo", sin revisar el resto. En los hechos, **sí
hay préstamos con cuotas vencidas de verdad**, sin que nadie moviera ningún reloj: son
préstamos de prueba reales, de semanas atrás (`CC-2026-00027`, desembolsado 05/08/2026;
`CC-2026-00029`, desembolsado 10/09/2026), cuyas primeras cuotas mensuales ya vencieron con el
solo paso del tiempo real del proyecto. Confirmado y probado en pantalla el mismo 13/10/2026
(ver §9) — la tarjeta "Deuda exigible hoy" de `CC-2026-00027` coincidió peso por peso con el
cálculo del motor. La fórmula en sí (moratorio/punitorio/IVA) ya estaba cubierta por
`ServicioMotorFinancieroTest` (`testBF06_MoraDeLaCuota1`, 15 días de atraso) desde antes de
este análisis — lo que faltaba, y ya se hizo, era la prueba en pantalla con una cuota vencida
real.

**Confirmación pedida por Joel (13/10/2026) — "capital pendiente" del moratorio: ¿cuota vencida
o saldo total?**

Confirmado leyendo el código, **es el capital de LA CUOTA vencida, nunca el saldo total del
préstamo**:
- `ServicioMotorFinanciero.calcularDeudaExigible` (línea 533) recorre `prestamo.getCuotas()` y,
  para cada cuota, primero decide si está vencida (línea 538: `cuota.getEstadoPago() !=
  EstadoPago.PAGADA && cuota.getFechaVencimiento().isBefore(fecha)`) — si no, la salta
  (`continue`). Solo entonces llama a `calcularMora(cuota, ...)` (línea 542), **pasándole esa
  misma cuota**, nunca el préstamo completo ni una suma de saldos.
- `calcularMora` (línea 280) usa como base, en la línea 302: `BigDecimal base =
  cuota.capitalPendiente();` — y `Cuota.capitalPendiente()` (`Cuota.java` línea 199) es
  `capitalProgramado.subtract(capitalPagado)`, el capital de **esa cuota puntual** (programado
  menos lo que ya se le pagó a ella), nunca una cuenta contra el saldo total del préstamo.
- Si hay varias cuotas vencidas, cada una genera su propio tramo de mora sobre SU PROPIO capital
  pendiente, por separado — nunca se suma el capital de varias cuotas antes de calcular el
  moratorio.

**No es el hallazgo grave que preguntabas — la base está bien, es por cuota.**

**Ejemplo verificado contra el motor real** (préstamo de 5.000.000 Gs. a 12 meses, TEA 27 %,
igual que CC-2026-00001; cuota 1 vencida hace 10 días sin pagar; parámetros reales de
`sgcpc_db_pruebas`: `factor_moratorio=1,000000`, `porcentaje_punitorio=0,300000`,
`dias_gracia_mora=0`). Corrí una verificación puntual (sin tocar ningún archivo del sistema,
solo un programa temporal que llama a `ServicioMotorFinanciero.cotizar`/`calcularDeudaExigible`
tal cual existen hoy) y confirmé:

| Concepto | Monto |
|---|---|
| Capital de la cuota 1 (capital pendiente, base del moratorio) | 372.551 Gs. |
| Interés pendiente de la cuota 1 | 100.589 Gs. (IVA 10.059 Gs.) |
| Moratorio (10 días) | 2.448 Gs. |
| IVA del moratorio | 245 Gs. |
| Punitorio (30 % del moratorio) | 734 Gs. |
| IVA del punitorio | 73 Gs. |
| **Deuda exigible total de la cuota 1** | **486.699 Gs.** |

(372.551 + 100.589 + 10.059 + 2.448 + 245 + 734 + 73 = 486.699 — cuadra exacto con lo que
devolvió `calcularDeudaExigible`.) El monto de la cuota 1 sin mora, 483.199 Gs., es el mismo que
ya viste en pantalla para Mirtha en la reprueba de RF20 — confirma que es la misma cuota real,
no un dato inventado para este ejemplo.

## 7. Decisiones

Ninguna todavía — este documento es solo análisis, a la espera de la aprobación de Joel.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B195**: *"Deuda exigible hoy"* tiene **fila de total**. Eran 7 columnas de dinero con un total por cuota y ninguno general, así que la cajera sumaba de cabeza para saber cuánto pedir. Las cinco columnas del medio se acumulan mientras se dibujan las filas (igual que la fila de totales del plan de pagos de RF11); el total de la última columna **no se suma en la pantalla**: sale de `ResultadoDeudaExigible.getTotalGeneral()`, el número del servicio, el mismo que la tarjeta de cobranza usa en `data-monto-exigible` y contra el que compara `confirmarCobro` — así la pantalla no puede mostrar un total distinto del que el sistema va a exigir. Ningún cálculo nuevo. `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 13/10/2026 | Probado en pantalla por Joel, sin cobrar nada (ver §9); corregido el error de §6 sobre "ningún préstamo con cuota vencida". | *(este commit)* | `rf21-probado` |
| 13/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | *(sin commit propio, documentación)* | — |
| D-82 (sin fecha exacta) | Corregido el bug del punitorio (se guardaba tasaMoratoria × porcentajePunitorio en vez de porcentajePunitorio tal cual) | — | — |
| D-77 (sin fecha exacta, ajuste de Joel) | Días de gracia como tolerancia (mora cero dentro del plazo), no como descuento de días | — | — |

## 9. Pruebas realizadas en pantalla

### 27/10/2026 (Joel), cajera Sofía Martínez, `CC-2026-00003` — deuda exigible **predicha antes de la pantalla**

**El método, que es la parte nueva.** Hasta hoy la deuda exigible se comparaba contra el motor
*después* de verla en pantalla — es decir, se verificaba que dos ejecuciones del mismo código
coincidieran. Esta vez se hizo al revés y por triplicado:

1. Se preparó el dato con el desplazamiento de fechas de D-81 (−35 días sobre `CC-2026-00003`,
   ver `AVANCE.md` del 27/10/2026), dejando la cuota 1 vencida hacía 5 días.
2. **Antes de abrir la pantalla**, Claude recibió solo los **datos de entrada** (vencimiento,
   componentes de la cuota, tasas, parámetros, fórmula y redondeos) y calculó el resultado
   esperado, que quedó **guardado y sellado, sin mostrárselo a Joel**.
3. Joel calculó el mismo resultado **a mano**, por su cuenta.
4. Recién entonces se abrió `/cobranza/3` y se comparó.

**Coincidieron los tres**: predicción, cuenta a mano y pantalla. Eso es lo que una comparación
contra el propio motor no puede dar — si la fórmula estuviera mal implementada, el motor y la
pantalla coincidirían igual.

| Concepto | Valor |
|---|---|
| Vencimiento de la cuota 1 | 22/10/2026 |
| **Días de atraso** | **5** |
| Base del moratorio (capital pendiente) | 165.361 |
| Capital | 165.361 |
| Interés | 13.236 |
| IVA del interés | 1.324 |
| **Moratorio** | **542** |
| **Punitorio** (30 % del moratorio, D-82) | **163** |
| IVA de la mora (54 + 16) | 70 |
| **Total exigible de la cuota 1** | **180.696** |
| **Deuda exigible total** | **180.696** (la cuota 2 vencía el 23/11, no entraba) |

La cuenta, con los valores de los parámetros vigentes (`credito.dias_gracia_mora` = 0,
`credito.factor_moratorio` = 1,0, `credito.porcentaje_punitorio` = 0,30,
`prestamos.tasa_moratoria` = 0,27):

```
dias      = 5                                   (> 0 dias de gracia, la mora corre)
exponente = 5 / 365 = 0,0136986301               escala 10, HALF_UP
factor    = 1,27 ^ 0,0136986301 - 1 = 0,0032795711
moratorio = redondear(165.361 x 0,0032795711) = redondear(542,313) = 542
punitorio = redondear(542 x 0,30) = redondear(162,6) = 163
IVA       = redondear(542 x 0,10) + redondear(163 x 0,10) = 54 + 16 = 70
```

**Confirmado después contra la base** (solo lectura): los 542 de moratorio y los 163 de punitorio
aparecen tal cual en `aplicaciones_pago`, y los 180.696 se repartieron entre el pago parcial de
100.000 y el cobro del resto de 80.696 (ver `RF22_cobro_efectivo.md` §9).

**Esta pasada cubre el tramo 1-30 días.**

**Evidencia**: `evidencias\2026-10-27\facturas\001-001-0000004.pdf` (la factura del pago parcial,
donde los 542 de moratorio y los 163 de punitorio quedaron documentados: 15.335 de gravado) y
`evidencias\2026-10-27\recibos\REC-2026-000009.pdf`. Los números a mano están en
`evidencias\2026-10-27\LEEME.md`.

### 27/10/2026 22:19 (Joel), `CC-2026-00001` — **dos tramos de mora en la misma deuda exigible**

Mismo método que la pasada anterior (resultado calculado a mano y **sellado antes** de la
pantalla, por Joel y por Claude por separado). El dato se preparó con el desplazamiento de D-81,
esta vez **−63 días** (múltiplo de 7: los nueve vencimientos conservaron su día de la semana, y
ninguno cayó en sábado, domingo ni feriado). Quedaron **dos cuotas vencidas con mora impaga**:

| Cuota | Vence | Atraso | Tramo | Capital | Interés | Moratorio | Punitorio | IVA | **Total** |
|---|---|---|---|---|---|---|---|---|---|
**1** | 21/09/2026 | **36 días** | 31-60 | 237.295 | 46.589 | **5.661** | **1.698** | 5.395 | **296.638** |
**2** | 19/10/2026 | **8 días** | 1-30 | 242.069 | 41.815 | **1.271** | **381** | 4.347 | **289.883** |
| | | | | | | **6.932** | **2.079** | 9.742 | **586.521** |

Estado del préstamo en pantalla: **"En mora"**.

**Lo nuevo acá es que los dos tramos aparecen en la MISMA deuda exigible**, cada cuota con su
propio factor según sus días de atraso. El tramo 31-60 ya se había visto en pantalla el
13/10/2026 (`CC-2026-00027`, 37 días, más abajo), pero nunca junto con otro tramo en el mismo
cálculo:

```
cuota 1:  36/365 → 1,27^0,0986301370 − 1 = 0,0238543500 → 237.295 × … = 5.661
cuota 2:   8/365 → 1,27^0,0219178082 − 1 = 0,0052524720 → 242.069 × … = 1.271
```

**Coincidencia exacta** entre predicción sellada, cuenta a mano y pantalla, **concepto por
concepto** (no solo en el total): los dos moratorios, los dos punitorios y los dos IVA. Verificado
después en `aplicaciones_pago` (solo lectura): las filas de las cuotas 1 y 2 traen
`monto_moratorio` 5.661 y 1.271, `monto_punitorio` 1.698 y 381, `monto_tributos` 5.395 y 4.347, y
`subtotal_aplicado` 296.638 y 289.883.

**Evidencia**: `evidencias\2026-10-27\recibos\REC-2026-000012.pdf` (el detalle de las 9 cuotas),
`evidencias\2026-10-27\facturas\001-001-0000006.pdf` (7 renglones: compensatorio, moratorio y
punitorio de las cuotas 1 y 2, más el compensatorio de la cuota 3).

**Lo que sigue sin probarse** (§10): los tramos **61-90**, **91-180** e **incobrable (> 180)**.
`CC-2026-00001` quedó `FINALIZADO` esa misma noche.

### 13/10/2026

**Probado en pantalla por Joel el 13/10/2026**, como cajera, en `/cobranza/27`
(`CC-2026-00027`), **sin registrar ningún cobro** (confirmado que la vista sola no exige caja
abierta ni cobra nada — §4):

- Tarjeta "Deuda exigible hoy": cuota 1 → capital 223.531 / interés 60.353 / moratorio 1.645 /
  punitorio 1.645 / IVA 6.748 / total 297.759; cuota 2 → capital 228.028 / interés 55.856 /
  moratorio 898 / punitorio 269 / IVA 5.703 / total 290.754. "Pagar cuota": 588.513 Gs. (en
  efectivo, 588.500 Gs. redondeado).
- **Coincide peso por peso** con el cálculo corrido contra el motor real (mismo método que usa
  la pantalla, `ServicioCobranza.calcularDeudaExigible`, ver la verificación de esta misma
  sesión) y con lo que muestra Seguimiento de mora (`/mora`) para el mismo préstamo.

No se probó en pantalla (y queda para cuando se resuelvan los tramos faltantes, ver §10): un
préstamo en los tramos 61–90, 91–180 o incobrable (`> 180` días) — ninguno de los que existen
hoy en `sgcpc_db_pruebas` llega tan lejos (ver análisis de tramos del 13/10/2026).

## 10. Pendientes y trabajo futuro

- **(13/10/2026) Sin control de usura durante la mora**: la tasa moratoria queda congelada al
  formalizar (coherente con el resto del préstamo) pero nada re-valida que siga bajo el tope
  legal vigente si ese tope bajara después. No es un incumplimiento del RF21 (que no lo exige),
  pero queda anotado para que Joel decida si hace falta un control aparte en algún momento.
  Ver §6.
- **Confirmar en RF34** (estado de cuenta, todavía sin analizar) si muestra la misma deuda
  exigible histórica con el mismo detalle.
- ~~Prueba en pantalla con una cuota vencida de verdad~~ — **resuelto y probado el 13/10/2026**
  (ver §9), con `CC-2026-00027` (37 y 6 días de atraso).
- **(13/10/2026) Tramos 61–90, 91–180 y "Más de 180"/incobrable sin ningún dato todavía**: los
  2 préstamos en mora real que existen hoy (`CC-2026-00027`, 37 días → tramo "31–60";
  `CC-2026-00029`, 1 día → tramo "1–30") no llegan tan lejos. Queda para cuando se decida cómo
  cubrir el resto de las pruebas por fecha (RF04/RF16/incobrables) — ver el análisis de
  `CargadorDatosPrueba` del 13/10/2026 (sin documento propio todavía, ver `LEEME.md`).

## 11. Guion para la defensa

1. Abrir la ficha de cobranza de un préstamo al día (sin vencidas) — mostrar que no aparece la
   tarjeta "Deuda exigible hoy", solo la próxima cuota a cobrar (RF20).
2. Abrir la ficha de `CC-2026-00027` (préstamo en mora real, probado el 13/10/2026) — mostrar la
   tarjeta con el detalle por cuota: capital, interés, moratorio, punitorio, IVA 10 % y total, y
   el total general exigible (588.513 Gs.).
3. Explicar la fórmula del moratorio (interés compuesto sobre el capital pendiente **de la
   cuota vencida**, por los días de atraso, tasa igual a la del préstamo) y del punitorio (30 %
   del moratorio) — con su respaldo documentado:
   - **RN-16** (`docs/00_especificacion_maestra.md`): "Interés moratorio: tasa ≤ tasa del
     préstamo, solo sobre capital vencido, sin capitalizar. Punitorio: ≤ 30 % del moratorio.
     Ambos gravados con IVA."
   - **Doc. 00 §5.3** (tabla de parámetros) y **§6.4** ("Mora: moratorio y punitorio"): fórmula
     exacta, idéntica a la del código.
   - **D-20** (`AVANCE.md`, 24/09/2026, decisión de Joel): "Tasa moratoria de un préstamo = TEA
     del préstamo × `factor_moratorio`... `credito.porcentaje_punitorio` se mantiene en 0,300000
     (fracción), con validación ≤ 0,300000 (RN-16)." — es la decisión que fija los dos valores
     que usa el código hoy.
   - **D-82** (`AVANCE.md`, 26/09/2026): bug real encontrado por Joel en pantalla (el punitorio
     salía en 8,1 % en vez de 30 % porque `ServicioFormalizacion` multiplicaba tasa moratoria ×
     porcentaje punitorio en vez de guardar el porcentaje tal cual) — corregido con
     `calcularTasasMora` y un `UPDATE` sobre los 23 préstamos ya formalizados en ese momento.
