Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Cobranza

## Dónde está

Menú **"Cobranza y mora"** → **"Cobranza"**. Lleva a `/cobranza`. Solo la cajera (y el
administrador) la ven — el oficial y el gerente no tienen acceso a esta pantalla.

## Lo primero que ve al entrar: su panel

En el bloque **"Hoy"** del panel hay dos tarjetas que hablan de su día: **"Cobros de hoy"** es lo
que **ya cobró** (cantidad y total), y **"Vencen hoy"** es lo que **todavía falta cobrar** —
cuántas cuotas vencen hoy y por cuánto, con el botón **"Ir a Cobranza"**. Si dice *"0 — Vencen
hoy"* no hay vencimientos para hoy; los préstamos que ya están atrasados **no** se cuentan ahí,
tienen su propia tarjeta (**"Préstamos en mora"**).

## La bandeja de trabajo

Al entrar, sin buscar nada, ve 3 secciones, en orden de urgencia:

- **"En mora"** (roja): préstamos con alguna cuota vencida sin cobrar. Columnas: Cliente, C.I.,
  Operación, Cuota, Monto exigible, Vencimiento, Atraso (días).
- **"Vencen hoy"** (amarilla): préstamos cuya próxima cuota vence hoy mismo.
- **"Próximos 7 días"** (gris): préstamos que van a vencer dentro de la próxima semana.

Un mismo préstamo nunca aparece en dos secciones a la vez — si está en mora, solo sale en "En
mora". Cada fila tiene un botón **"Cobrar"**.

**Atención — esta lista no muestra todo.** Solo trae lo que ya venció, vence hoy o vence en 7
días. Si un cliente viene a pagar con mucha anticipación (por ejemplo, el mismo día que le
desembolsaron, para la primera cuota de dentro de un mes), **no va a aparecer en ninguna de las
3 secciones** — tiene que buscarlo a propósito.

## Buscar un préstamo

Campo **"Buscar"**, con la ayuda **"Cédula, nombre o apellido, o número de operación"**,
botones **"Buscar"**/**"Limpiar"**. Del número de operación **alcanza con escribir una parte**:
`00005` encuentra `CC-2026-00005`, y da igual si lo escribe en mayúsculas o en minúsculas. Si lo
que escribió coincide con varios préstamos, el sistema se lo dice y le pide el número completo —
nunca elige uno por usted. Lo mismo vale para el buscador de **Reportes → Estado de cuenta**: los
dos buscan igual (antes uno aceptaba cosas que el otro no). Si encuentra un solo préstamo cobrable, muestra una tabla
con:

- **Operación**, **Cliente**, **C.I.**
- **Próxima cuota** (por ejemplo, "1/12 — vence 23/11/2026")
- **Monto a cobrar**
- **Estado**: **"Al día"** (verde) o **"[N] día(s) de atraso"** (rojo)
- Botón **"Cobrar"**

**Atención — "Monto a cobrar" y "Al día" no garantizan que se pueda cobrar ese monto hoy.**
Para un préstamo sin nada vencido, esta tabla muestra el monto completo de la **próxima** cuota
(aunque falten semanas para que venza) y siempre dice "Al día" — pero el sistema **no permite
cobrar por anticipado** una cuota que todavía no venció: si la próxima cuota está lejos, al
tocar "Cobrar" va a tener que usar **"Adelantar a capital"** en la ficha, no "Pagar cuota" por
ese monto exacto. No es un error de la pantalla — es que esta tabla y el botón real de la ficha
calculan cosas distintas; tenga esto presente antes de prometerle al cliente que "hoy se le
cobra la cuota 1" si todavía falta mucho para su vencimiento.

Si no hay un préstamo cobrable (pendiente de desembolso, ya cancelado, cédula ambigua o
inexistente), la pantalla le explica la situación con un mensaje claro en cada caso, en vez de
un genérico "sin resultados".

## Tocar "Cobrar"

El botón **"Cobrar"** (tanto de la lista de trabajo como de un resultado de búsqueda) no cobra
nada por sí mismo — lo lleva a la **ficha del préstamo**, donde recién ahí ve las condiciones
reales: la deuda exigible (si hay algo vencido), y la pregunta **"¿Cuánto quiere pagar el
cliente?"**, cuya primera opción dice **"Pagar lo vencido"** si hay algo vencido, o **"Por
cantidad de cuotas"** si el préstamo está al día (rótulos actualizados el 27/10/2026; antes
decían "Pagar cuota" y "Adelantar a capital").

**Con cuotas vencidas, primero se cobra lo vencido.** En ese caso ve solo dos opciones — "Pagar lo
vencido" y "Todo el préstamo" — y la línea *"Primero hay que cobrar lo vencido. Adelantar por
cantidad de cuotas o por monto queda disponible cuando el préstamo no tenga nada vencido."* Eso no
cambió con la pantalla nueva: el adelanto nunca se saltea una cuota vencida.

## La ficha del préstamo, con una cuota que no venció todavía

> **Actualizado el 27/10/2026.** Esta pantalla cambió: antes tenía botones fijos de "1 cuota /
> 2 cuotas / 3 cuotas" y un "Cancelar todo" al costado. Ahora hace **una sola pregunta** con tres
> formas de responder. Si ve la pantalla vieja, es que el servidor todavía no se actualizó.

Al tocar "Cobrar" (desde la bandeja o desde un resultado de búsqueda), llega a la ficha del
préstamo. Si no hay nada vencido, ve: el historial de cobros (vacío si todavía no cobró nada),
**"Deuda exigible hoy": "Sin cuotas vencidas."**, y una tarjeta **"Cobranza"** que le pregunta
**"¿Cuánto quiere pagar el cliente?"**.

**Cómo está armada la tarjeta.** Arriba, en **una sola fila**, las tres formas de pagar; la que
usted elige queda **resaltada**. Debajo, **dos columnas**: a la **izquierda** lo que usted elige
y escribe (la cantidad de cuotas, "Recibido", "Ver detalle del adelanto"); a la **derecha**, el
**"Resumen del cobro"** — un ticket con lo que el cliente paga arriba y en grande, debajo las
cuotas que le quedan, la próxima cuota, el ahorro y el vuelto, y al pie el botón para cobrar.
**Todo lo que necesita mirar antes de cobrar está en esa columna derecha**, sin bajar la pantalla.
Si la ventana es angosta (o usted amplía el zoom), las dos columnas se **apilan**: primero lo que
elige, después el resumen.

Las tres opciones de la fila de arriba:

1. **"Por cantidad de cuotas"** — con los botones **−** y **+**, o escribiendo el número directo
   en la casilla, elija **cuántas cuotas quiere pagar el cliente**: desde 1 hasta las que le
   queden (si le quedan 12, puede elegir cualquier número de 1 a 12, no solo 1, 2 o 3). El
   resumen le dice el monto: *"Adelanta 4 cuotas (sin intereses futuros): 932.550 Gs."* — **un
   solo número, ya en múltiplos
   de 50 Gs.**, el que le tiene que pedir al cliente.
2. **"Por monto"** — para cuando el cliente trae una cantidad y no un número de cuotas. Escriba
   lo que trae en **"Recibido"** y el sistema le muestra qué cubre y cómo queda el préstamo.
   Mientras no escriba nada, dice *"Escriba el monto que trae el cliente."* y "Cobrar" está en
   gris: eso es normal, no es un error.
3. **"Todo el préstamo"** — el saldo completo (lo que antes se llamaba "Cancelar todo"); la
   opción muestra el monto al lado del nombre. Si en la opción 1 elige **todas** las cuotas que
   le quedan, el sistema pasa solo a esta opción: pagar todas las cuotas es, justamente, pagar
   todo el préstamo.

Con cualquiera de las dos primeras opciones ve siempre en el resumen, **antes de confirmar
nada**: *"Su próxima cuota será el [fecha] por [monto]; le quedan [N] cuotas."* y *"El cliente se
ahorra [monto] de intereses por pagar por adelantado."*
Ese ahorro es el interés que el cliente **deja de pagar** por adelantar (es un dato para
explicarle, no un descuento que usted tenga que calcular).

**El botón le dice exactamente cuánto va a cobrar**: no dice "Cobrar" a secas, dice **"Cobrar
232.600 Gs."**, con el monto que tiene que recibir en efectivo. Si todavía no hay un monto
válido, o el sistema no deja cobrar eso, el botón queda **en gris y sin monto**, y el **motivo
aparece en rojo justo encima**, dentro del resumen. Dos casos que va a ver seguido:

- *"Escriba el monto que trae el cliente."* (en "Por monto", con "Recibido" vacío).
- *"Lo recibido (X Gs.) no alcanza para …"* — le faltó efectivo para la cantidad de cuotas
  elegida, o para cancelar todo el préstamo. **No se cobra de menos en silencio**: o pide el
  monto completo, o elige menos cuotas, o usa "Por monto".

<!-- Captura pendiente (Joel): la tarjeta "Cobranza" con el selector en una fila y las dos columnas. -->
<!-- Captura pendiente (Joel): el aviso en rojo dentro del resumen con el botón "Cobrar" en gris. -->
<!-- Captura pendiente (Joel): la tarjeta en una ventana angosta, con las columnas apiladas. -->

**Por qué el monto no es "la cuota × la cantidad".** Si la cuota es 289.919 Gs., pagar 4 cuotas
**no** cuesta 1.159.676 Gs. sino menos — 932.550 Gs. en el ejemplo. No es un error ni un
descuento que haga usted: al adelantar, el cliente **no paga los intereses futuros** de esas
cuotas (es la ley). El sistema le dice exactamente cuánto cuesta dejar el préstamo en N cuotas
menos, y la diferencia es el "se ahorra".

<!-- Captura pendiente (Joel): la tarjeta "Cobranza" con las tres opciones y el control de cantidad. -->
<!-- Captura pendiente (Joel): "Por monto" con el campo vacío y el aviso "Escriba el monto...". -->

**Atención — abra siempre "Ver detalle del adelanto" antes de cobrar.** Es un desplegable,
cerrado por defecto, en la columna de la izquierda, con el dato más importante de la operación:
**interés devengado + IVA, capital aplicado y saldo que queda**. El monto grande del resumen
("Adelanta 4 cuotas (sin intereses futuros): [monto]") es todo lo que el cliente entrega — pero lo que de verdad baja de su
deuda es lo que figura ahí dentro, en "Capital aplicado", que es un poco menor: la diferencia es
el interés del período y su IVA. No se fíe solo del número grande del resumen.

**Atención — no hay ningún botón para "pagar la cuota" tal cual, cuando todavía no venció.** El
único botón de pago, en este caso, dice **"Cobrar"** al confirmar — pero lo que hace de verdad
es un **adelanto de capital**, no un cobro de esa cuota puntual. Esto tiene dos consecuencias
importantes para explicarle al cliente:

1. **La próxima cuota sigue venciendo la misma fecha de siempre**, y el cliente **sigue
   debiéndola** — adelantar no "tacha" la cuota 1, solo recorta el final del préstamo (una
   cuota menos al final, no una menos al principio). Si el cliente piensa que ya pagó la cuota
   de ese mes, aclárele que no es así: lo que hizo fue abonar capital por adelantado.
2. **El monto de la próxima cuota puede quedar un poco distinto** al que tenía antes (más chico,
   normalmente) — no es un error, es que se recalcula sobre un saldo menor.

## Cuando la cuota vence HOY

Ese día la cuota **todavía no está vencida**: **no tiene mora** y el préstamo sigue en *"Activo"*.
La ficha lo dice así: *"Deuda exigible hoy: **Sin cuotas vencidas**"*, y la opción de pago se llama
**"Pagar la cuota de hoy"** (no "Pagar lo vencido", que sería falso). Debajo verá *"Hoy vence la
cuota 1/10. Adelantar por cantidad de cuotas o por monto queda disponible cuando no haya ninguna
cuota para cobrar."*

**Ese día no se puede adelantar capital, y es a propósito**: la cuota del día hay que cobrarla
primero. Si el cliente quiere pagar la cuota **y además** adelantar, son **dos operaciones**:
primero el cobro, después el adelanto.

**Si el cliente trae menos que la cuota**, el resumen dice lo que de verdad va a cobrar:

> **Cobra 100.000 Gs. (a cuenta de la cuota de hoy)**
> de 289.011 Gs. — quedan 189.011 Gs., sin mora hasta mañana

No le va a pedir fecha de promesa (eso es solo para cuotas ya vencidas) y **no hay mora hasta
mañana**: lo que quede sin pagar recién empieza a generar mora al día siguiente. El cartel repite
esas mismas palabras, y el botón dice el monto: **"Sí, cobrar 100.000 Gs."**. Si cobra la cuota
completa, el cartel dice **"Cobra la cuota 1/10 de hoy: 289.000 Gs."** y el botón **"Sí, cobrar la
cuota de hoy"**.

## Cuándo el resumen muestra "—", y qué dice en un pago parcial

**El número grande del resumen es siempre el monto que se va a cobrar.** Por eso, si lo que
escribió no se puede cobrar, ese número **desaparece y queda un guion ("—")**, y el motivo
aparece **en rojo justo encima del botón "Cobrar"**, que queda deshabilitado. Los casos que va a
ver, con el texto exacto:

- *"El efectivo va en múltiplos de 50 Gs."* — el billete/monto que escribió no es múltiplo de 50.
- *"El mínimo para adelantar hoy es [monto]"* — en **"Por monto"**, lo que trae el cliente no
  alcanza ni para el interés del período (es poco: suele ser del orden de un par de miles).
- *"Lo recibido ([monto]) no alcanza para N cuotas ([monto])"* — en **"Por cantidad de cuotas"**;
  elija menos cuotas o use "Por monto".
- *"El monto cubre todo el saldo del préstamo. Elija «Todo el préstamo»."*

Mientras el resumen muestra "—" **tampoco se ve la vista previa** (la próxima cuota, las cuotas
que quedan, el "Ver detalle del adelanto"): si el cobro no va a salir, esos datos no corresponden
a nada. Vuelven a aparecer en cuanto el monto es válido. Un aviso **en gris** (*"Escriba el monto
que trae el cliente"*) no es un error: es que falta que usted escriba algo.

**En un pago parcial** (hay cuotas vencidas y el cliente trae menos de lo exigible) el resumen
dice, en grande, **lo que de verdad va a cobrar**:

> **Cobra 100.000 Gs. (pago parcial)**
> de 289.883 Gs. exigibles — queda pendiente 189.883 Gs.

y en "Vuelto" dice **"Sin vuelto (0 Gs.)"**, porque se aplica todo lo recibido.

El sistema le pide además la **fecha de promesa de pago**, y hasta que la elija "Cobrar" queda
deshabilitado. Mientras no la haya elegido, el pedido aparece **en gris** arriba del botón
(*"Elija la fecha de promesa para habilitar «Cobrar»"*): eso **no es un error**, es lo que falta.
El aviso **en rojo** debajo del campo (*"La fecha de promesa debe ser posterior a hoy"*) aparece
solo si la fecha que puso es de hoy o anterior.

Al tocar **"Cobrar"**, el cartel de confirmación repite **las mismas palabras** de la tarjeta, con
la promesa agregada, y el botón nombra el monto:

> **Confirmar: Pagar lo vencido**
> Cobra 100.000 Gs. (pago parcial)
> de 289.883 Gs. exigibles — queda pendiente 189.883 Gs.
> Promesa: 15/11/2026
> **[ Sí, cobrar 100.000 Gs. ]  [ Cancelar ]**

**Ese botón dice el monto a propósito**: en un pago parcial el cliente no paga todo lo vencido, así
que no diría la verdad un botón que dijera "cobrar lo vencido". Y **"Cancelar" no cobra nada ni
consume ningún número de recibo**: el cobro recién ocurre cuando usted toca el botón de confirmar.

<!-- Captura pendiente (Joel): el resumen en "—" con el motivo en rojo. -->
<!-- Captura pendiente (Joel): el resumen de un pago parcial. -->

## Confirmar y qué queda después

Al tocar "Cobrar", la confirmación ya resume la operación completa (monto, recibido, vuelto,
interés devengado, capital aplicado, saldo que queda, próxima cuota y cuántas quedan) con los
botones **"Sí, adelantar a capital"**/**"Cancelar"**. Al confirmar: **"Anticipo de capital
aplicado ([monto]) — Recibo [número]. Sin factura (no hay conceptos gravados)."** (o, si hubo
interés, con la factura correspondiente), con **"Imprimir recibo"** y **"Aceptar"**.

La ficha vuelve a mostrar el historial con esta operación, y la tarjeta "Cobranza" ya recalculada
con el nuevo saldo (menos capital, un nuevo monto sugerido para adelantar otra vez, si quisiera).

**Atención — el plan de pagos que el cliente se llevó impreso al formalizar queda
desactualizado.** El sistema sí muestra el plan correcto en pantalla (en la ficha de
Formalización, con un aviso de que cambió por un adelanto), pero **no imprime
automáticamente un plan nuevo para el cliente**, y una vez que los documentos firmados ya
están cargados (como en este préstamo), no hay botón para generar un plan impreso nuevo. Si el
cliente necesita algo en papel con el plan actualizado, hoy la única opción es el Informe de
estado de cuenta (un reporte distinto del plan de pagos original, no pensado como entrega
estándar) — tenga esto presente para no prometerle al cliente un plan nuevo que el sistema no
entrega solo.

**Atención — "Solicitar reversión"**: en el historial de cobros, solo la fila del cobro más
reciente (y todavía válido) tiene el campo de motivo y el botón rojo "Solicitar reversión" — no
todas las filas, aunque con un solo cobro pueda parecerlo. Al solicitarla, el **gerente** es
quien autoriza o rechaza desde su propia pantalla — usted solo pide, con un motivo de al menos
10 caracteres.

## El recibo impreso

Al tocar "Imprimir recibo" se abre un PDF de una sola página con los datos de la empresa, el
número de recibo, los datos del cliente, el monto cobrado, y — si fue un adelanto a capital —
un resumen del **plan nuevo**: cuántas cuotas quedan, cuándo vence la última, y cuánto y cuándo
es la próxima. Al pie, la leyenda "ORIGINAL: Cliente / COPIA: Capital Crédito".

**Atención — si el sistema no generó factura, dice "Factura asociada: no corresponde (sin
conceptos gravados)"**, y es normal: solo se genera factura cuando lo cobrado incluye interés o
mora (que llevan IVA); un adelanto el mismo día del desembolso no tiene interés, así que no hay
factura.

**Atención — el recibo es un solo papel, no imprime automáticamente una copia para la
empresa**, a diferencia del comprobante de desembolso (que sí sale en dos hojas). Si necesita
guardar una copia para Capital Crédito, tiene que volver a imprimir el mismo PDF usted misma.

**Atención — el recibo no lleva el plan completo del préstamo, cuota por cuota.** Si el cliente
pide el plan nuevo completo en papel (no solo el resumen del recibo), hoy eso solo lo puede
generar el **gerente o el oficial**, desde el Informe de estado de cuenta — la cajera no tiene
acceso a esa pantalla.

## El efecto en la caja de hoy

Cada cobro (normal, parcial, anticipo o cancelación) queda, en la caja del día (`/caja`), como
una fila más en "Movimientos", con su monto, su comprobante y un botón **"Reimprimir"**.

**Atención — la columna "Tipo" dice "Cobro" también para un adelanto a capital.** La pantalla
de caja no distingue "Cobro de cuota" de "Anticipo de capital" en esa columna — solo lo
distingue por la observación (el texto que dice, por ejemplo, "Anticipo de capital de
[operación]"). **El resumen que sale al cerrar la caja (el arqueo) sí los separa**, en dos
renglones distintos: "Cobros de cuotas" y "Anticipos de capital". No es un error de suma — el
dinero está bien contado en los dos lados — pero si busca "los anticipos de hoy" en la tabla de
movimientos, no van a estar marcados como tales; tiene que guiarse por la observación de cada
fila, o esperar al arqueo del cierre.

**Atención — "Reimprimir" abre el mismo PDF ya guardado**, sin volver a generarlo (salvo que el
cobro haya sido revertido) — y esa reimpresión **no queda registrada** en ningún lado: el
sistema no guarda quién reimprimió un comprobante ni cuándo.

**Atención — si hubo vuelto, va a ver DOS filas para el mismo cobro, cada una con su propio
botón "Reimprimir".** Una fila es el "Cobro" (lo que el cliente le entregó) y la otra es el
"Vuelto" (lo que le devolvió) — las dos llevan el mismo número de recibo y el mismo botón abre
exactamente el mismo PDF las dos veces. No necesita reimprimir dos veces: con cualquiera de los
dos botones ya tiene el comprobante completo.

## Si el cliente entrega más efectivo del necesario

Si escribe en "Recibido" un monto mayor al que propone la pantalla (el de la cantidad de cuotas
que tenga elegida), el sistema calcula el **vuelto** en vivo y lo muestra antes de confirmar —
tanto en la ficha como en la confirmación final.

**Atención — con "Por cantidad de cuotas", lo que se aplica nunca es más que el monto de esa
cantidad.** Si el cliente le da 1.000.000 Gs. pero eligió una cantidad cuyo monto es 932.550 Gs.,
**se aplican 932.550 Gs. y se devuelven 67.450 Gs. de vuelto**.

**Si el cliente trae un monto y quiere que se aplique todo, use "Por monto"** (actualizado el
27/10/2026). Ahí **se aplica todo lo que escriba**, sin vuelto, y el sistema le muestra cuántas
cuotas le quedan con ese monto. Esa es la respuesta a "el cliente trae 1.000.000 y no un número
redondo de cuotas": ya no hace falta elegir entre dos montos fijos.

**Si escribe un monto que cubre todo el saldo**, el sistema no lo deja seguir por esta vía: avisa
en rojo *"El monto cubre todo el saldo del préstamo. Elija «Todo el préstamo»."* y deja "Cobrar"
en gris, con **"Vuelto: —"**. Es correcto: para pagar todo hay que usar esa otra opción, que emite
además la constancia de cancelación.

**Atención — no existe ningún "saldo a favor".** Lo único que puede devolverle al cliente es el
vuelto, en efectivo, en el momento — el sistema no tiene ninguna forma de guardar ese excedente
como un crédito para el próximo cobro.

**Atención — si el cliente le entrega menos de lo que cuesta esa cantidad de cuotas, el sistema no
lo deja seguir** (actualizado el 27/10/2026). Avisa en rojo, por ejemplo: *"Lo recibido
(500.000 Gs.) no alcanza para 3 cuotas (697.950 Gs.). Elija menos cuotas o use «Por monto»."*, y
"Cobrar" queda en gris. Es a propósito: si lo dejara pasar, la tarjeta diría "Paga 3 cuotas" y el
cobro real sería de otra cantidad. **Las dos salidas son**: bajar la cantidad de cuotas con el
botón **−**, o pasar a **"Por monto"** y escribir ahí lo que trae el cliente.

**En "Por monto" sí se aplica todo lo que reciba**, sin vuelto, siempre que alcance para cubrir
como mínimo el interés del período en curso — si ni eso alcanza, el sistema rechaza la operación
con un mensaje claro.

El recibo que se imprime siempre muestra los tres montos por separado: **"Efectivo recibido",
"Vuelto" y "Aplicado"** — fíjese siempre en "Aplicado", que es el que de verdad bajó la deuda
del cliente. **La línea "Son:" (el monto en letras) expresa lo Recibido** — el efectivo que
usted contó y entró a la caja. Si hubo vuelto, el monto en letras es el total que el cliente le
entregó, y lo que de verdad bajó la deuda está en "Aplicado", en su propio renglón.

**Atención — cada vez que adelanta una cuota, el final del plan se acorta un mes, no la
próxima cuota.** Si el cliente adelanta varias veces, va a ver en los recibos que el "nuevo
último vencimiento" retrocede un mes cada vez (por ejemplo, de 23/09/2027 a 23/08/2027), pero
la "próxima cuota" sigue siendo la misma fecha de siempre — explíquele que son dos cosas
distintas: una es cuándo termina de pagar, la otra es cuándo le toca pagar la próxima.

## Si se equivocó y necesita revertir un cobro

En el Historial de cobros, junto a cada fila, puede escribir un **motivo (mínimo 10
caracteres)** y tocar **"Solicitar reversión"**. La confirmación le muestra el monto exacto del
cobro antes de pedírselo, y al aceptar, el sistema le avisa que **queda pendiente de
autorización del gerente** — la fila cambia a "Reversión solicitada — pendiente de
autorización" (en naranja).

**Atención — solo puede solicitar la reversión del último cobro de ese préstamo.** Si después
de ese cobro hubo otro (otro anticipo, u otro cobro), el botón de "Solicitar reversión" ya no
va a estar en la fila vieja — solo en la más reciente. Si necesita revertir un cobro anterior,
primero tiene que revertirse el más reciente (y así, uno por uno, en orden) — no hay un camino
directo para "saltarse" al de en medio.

**Atención — una vez enviada la solicitud, usted no puede cancelarla.** Solo el gerente puede
resolverla, autorizándola o rechazándola — no hay ningún botón para que usted misma la retire o
la corrija si se equivocó al pedirla.

**Atención — mientras la solicitud está pendiente, el sistema no la bloquea de nada, pero ahora
se lo avisa.** Puede seguir cobrando, adelantando capital o cancelando el préstamo con total
normalidad (si el cliente vino a pagar, no tiene que esperar la decisión del gerente), aunque el
saldo que ve ya tenga descontado el cobro cuya reversión todavía no se decidió. Desde el
03/11/2026, al confirmar cualquiera de esas tres operaciones el cartel termina con un aviso
amarillo:

> Hay una reversión pendiente de autorizar sobre el recibo REC-…. Si confirma esta operación,
> esa reversión ya no podrá autorizarse: el gerente solo podrá rechazarla.

Léalo antes de tocar "Sí": **no le impide nada**, le dice qué pasa si sigue. Si el cobro en duda
hay que corregirlo de verdad, conviene esperar a que el gerente resuelva la reversión antes de
operar otra vez sobre ese préstamo. Si la operación nueva no puede esperar, hágala — pero sepa
que la reversión ya solo va a poder rechazarse.

**Atención — el motivo que usted escribió no se vuelve a mostrar mientras está pendiente.** La
fila solo dice "pendiente de autorización", sin repetir lo que anotó — quedó guardado en el
sistema, pero no a la vista en esta pantalla.

**Si el gerente rechaza la solicitud**, el cobro queda exactamente igual que antes (nada
cambia) y usted sí ve el motivo del rechazo: la fila muestra, en rojo, **"Reversión rechazada:
[motivo]"**, con un botón **"Solicitar de nuevo"** por si quiere volver a pedirla con otro
motivo.

El gerente se entera de la solicitud por su propio panel ("Reversiones por autorizar"), que
sube de número apenas usted la envía — no necesita avisarle aparte, aunque tampoco está de más
si es urgente.

## Cómo autoriza el gerente una reversión

El gerente llega desde su panel ("Ir a Reversiones") o por el menú **"Cobranza y mora"** →
**"Reversiones"**. Ahí ve las solicitudes pendientes, con la cajera que la pidió, el recibo, el
monto y el motivo completo — y dos acciones: el botón verde **"Autorizar"**, o un campo "Motivo
del rechazo" junto al botón **"Rechazar"**.

La propia pantalla ya explica la regla: *"Solo se puede revertir la operación más reciente de
cada préstamo, y hace falta una caja abierta hoy con efectivo suficiente."*

**Atención — "Autorizar" es irreversible y no se puede deshacer, y no deja ningún comentario
del gerente.** La confirmación se lo dice explícitamente ("No se puede deshacer"), pero no hay
ningún campo para que el gerente anote por qué autorizó — a diferencia de "Rechazar", que sí
exige un motivo. Si el gerente quiere dejar constancia de su decisión, hoy no tiene dónde
hacerlo dentro del sistema.

**Atención — al autorizar, se devuelve en efectivo el monto que de verdad se aplicó, no lo que
el cliente entregó originalmente.** Si el cobro que se revierte tuvo vuelto (el cliente entregó
más de lo necesario y se le devolvió la diferencia), lo que sale de la caja al revertir es el
monto **aplicado** — el vuelto de esa vez no se vuelve a tocar, porque ya se le entregó al
cliente en su momento. Es la cuenta correcta: el cliente nunca estuvo "a deber" ese vuelto.

**Atención — si no hay caja abierta, o no alcanza el efectivo para devolver, el sistema
rechaza la autorización.** No se puede autorizar una reversión sin una caja de hoy con
suficiente dinero en efectivo para entregar.

**Rechazar** no toca nada más — ni el pago, ni la caja, ni el plan — solo guarda el motivo.
**Atención — el sistema no le avisa nada a la cajera en el momento**, ni si se autoriza ni si
se rechaza: ella se entera recién si vuelve a mirar la ficha del préstamo (el motivo del
rechazo aparece ahí, con la opción de "Solicitar de nuevo") o `/caja` (si se autorizó). Avísele
usted mismo, de palabra, si es algo urgente.

**Atención — la confirmación de "Rechazar" no dice "No se puede deshacer".** Es normal: a
diferencia de "Autorizar" (que mueve dinero y cambia el plan), rechazar no cambia nada del
cobro — el cliente sigue debiendo lo mismo, como si nunca se hubiera pedido la reversión.

**Atención — en el Historial de Reversiones, la autorización (la que de verdad hace algo) es
la que menos explicación deja.** La columna "Motivo de la decisión" queda vacía ("-") para una
reversión autorizada, mientras que para una rechazada sí muestra el motivo completo del
gerente. Si usted quiere dejar registrado por qué autorizó algo, hoy no hay dónde hacerlo
dentro del sistema (ver la sección de arriba).

## Qué pasa después de autorizar

Al autorizar, el éxito le muestra cuánto se devolvió en efectivo (por ejemplo, **"Reversión
autorizada — Se devolvieron 192.850 Gs. de la caja."**), con **"Imprimir constancia de
reversión"**.

**Atención — ese dinero sale de la caja de la cajera, no de una caja suya.** Aunque usted
autoriza desde su propia pantalla, el descuento se aplica en la sesión de caja que la cajera
tiene abierta en ese momento. **El sistema mismo no le avisa a ella que ya se descontó** —
pero sí hay un control real, en papel: la Constancia de reversión (ver abajo) incluye la firma
del cliente confirmando que recibió el dinero, así que la entrega física queda documentada
igual, solo que fuera del sistema.

**Atención — el plan de pagos vuelve exactamente al que había antes del cobro revertido** —
con sus mismas cuotas y montos de entonces, no uno recalculado de nuevo. La confirmación de
"Autorizar" no le menciona este efecto ni el del punto anterior — solo el monto — tenga
presente que autorizar mueve más cosas de las que esa confirmación describe.

El documento que se imprime es la **"Constancia de reversión"** — un papel distinto de la
"Nota de anulación" (que solo existe si el cobro original tenía factura; en este caso no la
hubo, porque no había conceptos gravados).

## La constancia de reversión, qué lleva

El PDF **"CONSTANCIA DE REVERSIÓN DE COBRO"** trae: la fecha, un párrafo explicando que la
empresa deja constancia de la devolución en efectivo al cliente por la reversión de ese
recibo, el monto en letras, y un cuadro **"DATOS DE LA REVERSIÓN"** con el recibo anulado, el
monto devuelto, el motivo de la solicitud, quién solicitó (la cajera) y quién autorizó (el
gerente), con fecha y hora. Al pie, la leyenda de adjuntar el recibo original (y la factura
anulada, si hubo), y **tres firmas: la cajera, el gerente y el cliente** — el cliente firmando
**"Recibí conforme la devolución de [monto]"**.

**Esta firma del cliente es, en los hechos, el control de que el dinero realmente se entregó**
— aunque el sistema no lo pida como un paso dentro de la pantalla, el papel que sale sí lo
documenta.

**Atención — el encabezado de este documento se ve con letra más chica que el recibo o la
factura.** No le falta nada (tiene su "Emitido por" y "Página X de Y" igual que los demás),
solo que el tamaño de letra de los datos de la empresa es más chico — una diferencia de
formato, no de contenido.

## Cómo queda su propia caja después de una reversión

Si usted entra a `/caja` después de que el gerente autorizó una reversión de un cobro suyo, va
a ver: las filas originales del cobro (y del vuelto, si hubo) siguen ahí, **sin borrarse**,
pero con **"(revertido)"** agregado a la observación; y una fila **nueva**, "Compensación", con
el monto que salió de su caja y su propia constancia para reimprimir. **Este es, hoy, el único
lugar donde usted ve que hubo una reversión** — nadie le avisa antes, así que revise `/caja` si
sospecha que algo cambió sin que usted lo hiciera.

**Atención — si reimprime el recibo de un cobro ya revertido, nunca le va a salir como
válido.** Va a salir siempre con una franja roja grande ("ANULADO POR REVERSIÓN"), con el
texto *"Este cobro fue revertido el [fecha] (autorizó: [gerente]). Motivo: [motivo] — Este
documento NO es válido como comprobante de pago."* y una marca de agua "ANULADO" en diagonal
sobre toda la hoja — nunca el PDF original tal cual era antes. Esto es igual tanto si lo
reimprime desde `/caja` como desde el historial de la ficha del préstamo.

**Atención — el resumen del plan que trae ese recibo anulado queda "congelado" en el momento
en que se emitió, no se actualiza con la reversión.** Por ejemplo, si el recibo original decía
"10 cuotas restantes, próxima cuota 192.503 Gs.", sigue diciendo exactamente eso aunque el
plan ya se haya restaurado a 11 cuotas — tiene sentido: es un comprobante anulado, muestra lo
que decía en su momento, no el estado actual del préstamo. Para ver el plan real, use la ficha
del préstamo o el estado de cuenta, nunca un recibo ya anulado.

**Atención — el resumen del arqueo no distingue un cobro revertido de uno válido en sus
categorías normales.** El cobro y el vuelto revertidos siguen contándose en "Anticipos de
capital" y "Vueltos entregados" con su monto completo, igual que si todavía fueran válidos — la
Compensación aparece aparte, en su propio renglón ("Compensaciones por reversión"), y entre las
tres el saldo total queda correcto, pero el arqueo no le avisa, en ningún renglón, que una de
esas operaciones ya fue anulada.

## Tocar "Todo el préstamo": qué ve antes y después de confirmar

> **Actualizado el 27/10/2026.** Esta opción se llamaba **"Cancelar todo"** (se confundía con
> "cancelar la operación"). Ahora es **"Todo el préstamo"**, una de las tres opciones de la fila
> de arriba de la tarjeta, con el monto al lado del nombre.

Al elegirla, el **"Resumen del cobro"** muestra *"Todo el préstamo: [monto]"* y, debajo en letra
chica, *"en efectivo: [monto]"*: cuánto se cobra de verdad si el cliente paga justo, porque el
sistema **redondea a favor del cliente**. Ese monto sube un poco cada día que pasa desde el
último pago, porque sigue sumando el interés del período en curso.

**El botón dice el monto en efectivo** — *"Cobrar 2.548.400 Gs."* —, que es el que usted tiene
que recibir y contar. Desaparecen de la pantalla el control de cantidad de cuotas, la próxima
cuota, el ahorro y la letra chica del adelanto: son datos del adelanto, no de esta operación.

**Si lo que recibe no alcanza**, el resumen lo avisa en rojo antes de que toque nada — *"Lo
recibido (2.500.000 Gs.) no alcanza para cancelar todo el préstamo (2.548.400 Gs.)."* —, el
vuelto queda en **"—"** y el botón, **en gris**. No hay forma de cancelar el préstamo con menos
efectivo del que corresponde.

Al tocar el botón, la confirmación resume la operación. Cuando el redondeo cambia el monto, lo
dice en dos líneas, para que usted sepa qué cobra y qué cancela ese cobro:

```
Cobra en efectivo: 2.548.400 Gs.
Cancela la deuda de: 2.548.415 Gs. (redondeo a favor del cliente: 15 Gs.)
Recibido: 2.600.000 Gs.
Vuelto: 51.600 Gs.
```

(Si el monto ya es múltiplo de 50 Gs., no hay redondeo y va una sola línea: *"Todo el préstamo:
[monto]"*.) Todavía **sin desglose por cuota** — eso recién sale en el recibo, ver más abajo.
Los botones son **"Sí, cobrar todo el préstamo"** y **"Cancelar"**, uno junto al otro: lea bien
cuál es cuál antes de tocar, porque uno cobra el préstamo entero y el otro solo cierra el cuadro
sin hacer nada.

<!-- Captura pendiente (Joel): el cartel con "Cobra en efectivo" y "Cancela la deuda de". -->

Al confirmar de verdad, el mensaje de éxito resume todo: el total aplicado, el vuelto (si hubo),
lo cobrado en efectivo, el redondeo a favor del cliente (si lo hubo), y el número de recibo y de
factura — con botones para imprimir los tres documentos (recibo, factura y constancia). El
préstamo pasa a figurar como **"Finalizado"**.

**Atención — después de cancelar, el historial sigue ofreciendo "Solicitar reversión" en esa
fila**, con la misma casilla de la factura y el mismo texto sobre la nota de crédito que
cualquier otro cobro. Esto es así a propósito: todavía puede hacer falta revertir una
cancelación por error, mientras el pagaré original no se haya devuelto todavía. **Apenas el
cierre documental de ese préstamo quede completo** (ver "La constancia de cancelación" más
abajo), esa opción desaparece sola, y en su lugar el sistema avisa **"No admite reversión:
pagaré devuelto al cliente"** — ya no hace falta que usted se acuerde de nada para que deje de
ofrecerse.

## El recibo de una cancelación total, con el desglose por cuota

A diferencia de un anticipo (que no muestra tabla), el recibo de una cancelación total trae
una fila por cada cuota del plan — capital, interés, moratorio, punitorio e IVA de cada una —
y, al final, una fila TOTAL con la suma de todo. El interés y el IVA (si hay) se cargan en una
sola cuota (normalmente la más próxima); el resto de las filas solo muestra capital.

**Atención — ese desglose completo por cuota recién aparece en el recibo, después de
confirmar.** La confirmación que ve antes de tocar el botón final solo le muestra el total a
cancelar, lo recibido y el vuelto — sin desglosar nada por cuota. Si necesita explicarle al
cliente de dónde sale cada número antes de cobrar, hoy no tiene esa pantalla; recién la va a
ver impresa, después de que la operación ya quedó hecha.

**El "Son:" del recibo expresa el efectivo que usted contó**, no el monto que se aplicó a la
deuda. Si hubo redondeo a favor del cliente (la empresa perdonó unos guaraníes para no andar con
vueltos de centavos), el "Son:" en letras va a ser un poco **menor** que lo que quedó saldado:
un recibo de dinero certifica lo que entró a la caja, y por eso coincide con el efectivo
contado. El monto que bajó la deuda está en el renglón **"Aplicado"**, y la diferencia, en
**"Redondeo"**.

**La constancia de cancelación es al revés, y está bien.** Ahí el "Son:" expresa el **total
cancelado**, porque ese documento certifica que la deuda quedó saldada, no cuánto efectivo
entró. Por ejemplo, con 844.004 Gs. de deuda pagados con 844.000: el recibo dice
*"OCHOCIENTOS CUARENTA Y CUATRO MIL"* y la constancia, *"OCHOCIENTOS CUARENTA Y CUATRO MIL
CUATRO"*.

## La factura, cuando cancela todo el préstamo

Si al cancelar todo el préstamo queda algo de interés devengado (por ejemplo, porque pasaron
unos días desde la última cuota pagada o desde el desembolso), el sistema le ofrece
**"Imprimir factura"**, además del recibo.

**Atención — la factura nunca muestra el capital, solo el interés y su IVA.** El recibo sí
acredita todo el dinero (capital + interés + IVA); la factura es un documento aparte, más
chico, que solo describe la parte gravada — por eso el monto de la factura siempre va a ser
menor que el del recibo, y eso es correcto, no un error.

**Atención — la descripción de esa línea dice "Interés compensatorio cuota [N]/[total]", igual
que si fuera una cuota vencida normal.** No menciona que se trata de una cancelación
anticipada ni que ese interés es por unos pocos días — tenga presente, si el cliente pregunta,
que ese número sí es el interés real de esos días, calculado sobre el saldo que todavía debía,
aunque la factura no lo explique con esas palabras.

**Atención — revise la dirección del cliente antes de entregar la factura.** Si el domicilio
quedó cargado con algún dato faltante (por ejemplo, el barrio vacío), puede aparecer con una
coma de más en el papel — igual que ya puede pasar en el contrato o en el comprobante de
desembolso.

## La constancia de cancelación

Además del recibo (y la factura, si corresponde), al cancelar todo el préstamo puede imprimir
una **"Constancia de cancelación total"** — el documento que deja por escrito que el cliente
**ya no debe nada** de ese préstamo. Trae los datos del préstamo (operación, fecha de
desembolso, fecha de cancelación, monto, plazo), el desglose completo de la liquidación
(cuotas vencidas, capital, interés, IVA y el total cancelado), la referencia al recibo y a la
factura, y la firma del representante legal junto a la del cliente.

**Atención — el casillero de "se devuelve el pagaré" es solo un recordatorio en papel, no un
botón del sistema.** Tildar esa casilla a mano no actualiza nada — la devolución real del
pagaré se registra por otro camino, en **Formalización**, desde la sección "Préstamos
finalizados — cierre documental pendiente": ahí se genera una constancia de devolución propia,
se imprime, el cliente la firma al recibir su pagaré, se carga el escaneo firmado, y recién
ahí queda completado el cierre documental del préstamo. Mientras eso no se haga, el préstamo
sigue figurando con el cierre documental pendiente, aunque ya esté cancelado y sin deuda.

## Lo que se vio el 23/10/2026 (segundo cliente de práctica)

Las 3 secciones de la bandeja, vacías (la cuota 1 del segundo cliente de práctica vence el
23/11/2026, fuera de la ventana de 7 días). Buscando por su cédula: `CC-2026-00002`, próxima
cuota "1/12 — vence 23/11/2026", **"Monto a cobrar: 193.280 Gs."**, **"Al día"** — exactamente
el caso de la advertencia de arriba.

Al entrar a la ficha: sin cuotas vencidas, **"Adelantar a capital 193.280 Gs. — en efectivo:
193.250 Gs."** y **"Cancelar todo 2.000.000 Gs."** (sin ningún interés, porque hoy es el mismo
día del desembolso). *"Si adelanta, su próxima cuota será el 23/11/2026 por 192.891 Gs., quedan
11 cuotas."* — la misma fecha de siempre, un monto apenas distinto, una cuota menos al final.

Al abrir "Ver detalle del adelanto": **"Interés devengado hoy: 0 Gs. + IVA 0 Gs. Capital
aplicado: 193.250 Gs. Saldo que queda: 1.806.750 Gs."** — ahí queda claro que lo que de verdad
se aplica es 193.250 Gs., no los 193.280 Gs. del botón grande. Ver el detalle completo de los
cálculos en `docs/recorrido/06_primer_cobro.md` § Paso 2.

Al confirmar: **"Anticipo de capital aplicado (193.250 Gs.) — Recibo REC-2026-000001. Sin
factura (no hay conceptos gravados)."** El recibo impreso muestra, además de los datos
habituales, el resumen del plan nuevo: **"11 cuotas restantes, nuevo último vencimiento
23/09/2027. Próxima cuota (IVA incluido): 23/11/2026 por 192.891 Gs. Saldo de capital después
del pago: 1.806.750 Gs."** La ficha queda con el historial de este cobro (con su campo de
motivo y el botón "Solicitar reversión"), "Cancelar todo 1.806.750 Gs." y un nuevo adelanto
sugerido de 192.891 Gs. (192.850 en efectivo), con la próxima cuota en 192.503 Gs. y 10 cuotas
por delante. Ver el desarrollo completo (confirmación, verificación en la base de datos y los
hallazgos C26/B57/B58/B59) en `docs/recorrido/06_primer_cobro.md` § Pasos 3 y 4.

En `/caja`: sesión abierta desde las 10:22 (fondo 2.000.000 Gs.), con el desembolso de las
10:35 (−2.000.000) y este cobro de las 11:30 (**"Cobro" +193.250**, "Anticipo de capital de
CC-2026-00002.", REC-2026-000001, con "Reimprimir"), saldo teórico actual 193.250 Gs. — tal
cual la advertencia de más arriba: la fila dice "Cobro", no "Anticipo". Ver el detalle en
`docs/recorrido/06_primer_cobro.md` § Paso 5.

Más tarde, con una caja nueva abierta (fondo 100.000 Gs., entregado por el gerente como
cambio), se cobró un segundo adelanto del mismo préstamo: la pantalla proponía 192.850 Gs., el
cliente entregó **200.000 Gs.**, y el sistema mostró en vivo **"Vuelto: 7.150 Gs."**. Al
confirmar: **"Anticipo de capital aplicado (192.850 Gs.) — Vuelto: 7.150 Gs. Recibo
REC-2026-000002."** El recibo mostró **"Efectivo recibido: 200.000 Gs. Vuelto: 7.150 Gs.
Aplicado: 192.850 Gs."** — exactamente el caso de la sección de arriba: lo recibido y lo
aplicado son distintos, y el vuelto sale como un movimiento propio de la caja, aparte del
cobro. El recibo de ese día mostró "Son:" en letras por los 192.850 Gs. (no por los 200.000 Gs.
recibidos) — **ese criterio cambió el 01/11/2026**: desde entonces el "Son:" expresa el efectivo
recibido, como explica la sección de arriba; un recibo emitido antes de esa fecha conserva el
texto con el que se imprimió, y el "nuevo último vencimiento" del plan pasó de 23/09/2027 (primer recibo) a
23/08/2027 (este recibo) — un mes antes, mientras la próxima cuota se mantuvo en 23/11/2026.
Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 6.

En `/caja` de esta sesión (abierta a las 13:22, fondo 100.000 Gs.): "Apertura" +100.000, luego
**"Cobro" +200.000** y **"Vuelto" −7.150**, las dos a las 13:23, las dos con el mismo recibo
REC-2026-000002 y su propio botón "Reimprimir" — saldo teórico final 292.850 Gs. Tal cual la
advertencia de arriba: dos filas, un solo comprobante real. Ver el detalle completo en
`docs/recorrido/06_primer_cobro.md` § Paso 7.

Después, la cajera solicitó la reversión del cobro de las 13:23 (el único con el botón
disponible — el de las 11:30 ya no lo tenía), con el motivo "Error de la cajera: se registró un
anticipo no solicitado por el cliente." La confirmación mostró el monto exacto (192.850 Gs.);
al aceptar, el éxito avisó "Queda pendiente de autorización del gerente." y la fila pasó a
"Reversión solicitada — pendiente de autorización" — exactamente el caso de la sección de
arriba. Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 8.

El gerente entró a "Reversiones" y vio la solicitud con todos sus datos (cajera, operación,
recibo, monto, motivo completo) y los dos botones — "Autorizar" en verde, y "Motivo del
rechazo" con "Rechazar" al lado — tal cual la sección de arriba. Ver el detalle completo en
`docs/recorrido/06_primer_cobro.md` § Paso 10.

Al tocar "Autorizar" y confirmar, el éxito avisó "Se devolvieron 192.850 Gs. de la caja." — el
plan de `CC-2026-00002` volvió exactamente al que tenía después del primer anticipo (11
cuotas, próxima 192.891 Gs.), y la caja de la cajera registró la salida de esos 192.850 Gs. sin
que a ella se le avisara en el momento. La bandeja de "Pendientes de autorizar" quedó vacía.
Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 11.

Al imprimir la constancia, el PDF mostró exactamente lo descrito arriba: el recibo anulado
(`REC-2026-000002`), el monto devuelto (192.850 Gs.), el motivo, quién solicitó y quién
autorizó, y las tres firmas — con el cliente confirmando "Recibí conforme la devolución de
192.850 Gs." Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 11.

Al volver a `/caja`, el Cobro y el Vuelto de las 13:23 seguían ahí, ahora con "(revertido)" en
la observación; la Compensación de las 14:42 (−192.850 Gs.) apareció como fila nueva, con su
propia constancia `REV-2026-000001` para reimprimir — exactamente el caso descrito arriba. Ver
el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 13.

Al reimprimir `REC-2026-000002` desde `/caja`, salió con la franja roja "ANULADO POR
REVERSIÓN", el motivo y la fecha reales, y la marca de agua en diagonal — tal cual la
advertencia de arriba. El "Resumen del plan regenerado" de ese recibo seguía mostrando "10
cuotas restantes, próxima cuota 192.503 Gs." (el estado de antes de la reversión), no el plan
ya restaurado. Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 13.

Por último, al volver a buscar `CC-2026-00002`, la ficha mostró el plan **ya restaurado**:
monto a cobrar 192.891 Gs., 11 cuotas, igual que antes del segundo anticipo. El historial
mostró la fila revertida (con la etiqueta "REVERTIDO" y sin ninguna acción disponible) y, en
la fila de arriba (el primer anticipo), el botón "Solicitar reversión" **volvió a aparecer** —
confirmando que, si hiciera falta revertir ese cobro también, tendría que hacerlo ahora, con
el segundo ya resuelto. Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso
14 y el resumen de todo el circuito en "Recorrido completo de la reversión".

Con ese botón disponible, la cajera solicitó también la reversión del primer anticipo (motivo:
"Posible error: el cliente dice que no pidió este anticipo."); el gerente, al principio, tocó
"Rechazar" sin escribir el motivo y el sistema lo frenó ("Falta completar «Motivo del
rechazo»."); con el motivo "Revisado con el cliente: el anticipo sí fue solicitado; el cobro
es correcto.", rechazó la solicitud. El cobro quedó exactamente igual que antes, y la fila
mostró "Reversión rechazada: [motivo]" con la opción de volver a pedirla. En el Historial, la
fila de esta rechazada mostró su motivo completo, mientras que la fila de la que sí se
autorizó antes seguía con "-" en "Motivo de la decisión" — tal cual la advertencia de más
arriba. Ver el detalle completo en `docs/recorrido/06_primer_cobro.md` § Paso 15.

## Lo que se vio el 25/10/2026 (cancelación total del segundo cliente de práctica)

Dos días después, la ficha de `CC-2026-00002` mostraba **"Cancelar todo 1.809.330 — en
efectivo 1.809.300"** — ya no los 1.806.750 Gs. del 23/10, porque en el medio se sumaron 2 días
de interés más su IVA. La fila de las 11:30 (el primer anticipo) seguía mostrando "Reversión
rechazada: [motivo]" y "Solicitar de nuevo", sin cambios desde el 23/10.

Al tocar "Cancelar todo", el formulario propuso "Recibido: 1.809.300" — y, al lado, el selector
"1/2/3 cuotas" y "Si adelanta…" seguían ahí, tal cual la advertencia de arriba. Con 1.810.000
Gs. recibidos, el sistema calculó "Vuelto: 700 Gs." La confirmación mostró "Cancelar todo:
1.809.330 Gs.", "Recibido: 1.810.000 Gs.", "Vuelto: 700 Gs.", con "Sí, cancelar todo" junto a
"Cancelar". Al confirmar: **"Cancelación total aplicada — Total aplicado 1.809.330. Vuelto 700.
Cobrado en efectivo 1.809.300 (redondeo a favor del cliente 30). Recibo REC-2026-000003.
Factura 001-001-0000001."**, con los tres botones de impresión. El préstamo pasó a
**"Finalizado"**, y la nueva fila "Cancelación total" ofreció "Solicitar reversión" con la
misma casilla de la factura de siempre — tal cual se explica arriba.

Ver el detalle completo de la factura, el recibo con el desglose por cuota y la constancia de
cancelación en las dos secciones siguientes, y el cierre documental (devolución del pagaré) en
`docs/recorrido/manual_usuario/06_formalizacion.md`.
