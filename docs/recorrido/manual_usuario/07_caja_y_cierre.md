Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Caja y cierre del día

## Dónde está

Menú **"Desembolso y caja"** → **"Caja"**. Lleva a `/caja`.

## Abrir la caja

Si no hay ninguna sesión de caja abierta, ve la tarjeta **"Apertura de caja"**: un **"Fondo
sugerido"** (la suma de los préstamos ya listos para desembolsar, con el checklist de firmados
completo — no incluye los que todavía están esperando documentos), y un campo **"Fondo
inicial"** ya precargado con ese monto, que puede editar si el gerente le entregó otra cantidad
en mano. Al tocar **"Abrir caja"**, confirme **"¿Abrir la caja con un fondo inicial de
[monto]?"** con **"Sí, abrir caja"**.

**Atención — solo puede existir una caja abierta a la vez.** Si ya hay una abierta (suya o de
otra cajera), el sistema rechaza abrir otra — tiene que cerrar primero.

## Mientras la caja está abierta

Con una sesión abierta, la pantalla muestra:

- **"Sesión abierta"**: Apertura (fecha y hora), Fondo inicial, y el **Saldo teórico actual**
  (fondo inicial más todos los movimientos de la caja desde que se abrió).
- **"Movimientos"**: una tabla con Fecha/hora, Tipo, Monto, Observación y Comprobante. La
  primera fila siempre es la apertura (el fondo inicial); debajo, cada desembolso, cobro, etc.
  Si algún movimiento tiene un comprobante impreso, puede tocar **"Reimprimir"**.
- **"Cierre y rendición"**: para cerrar la caja al final del día (ver abajo).
- **"Sesiones cerradas"**: el historial de cajas ya cerradas, con un filtro por fecha de cierre
  y el botón **"Ver arqueo"** de cada una.

**No va a ver el formulario de apertura mientras la caja siga abierta** — esa tarjeta
desaparece hasta que vuelva a cerrarla.

## Cerrar la caja (arqueo)

En **"Cierre y rendición"**: cuente el efectivo físico de la caja y anótelo en **"Efectivo
contado"**. Apenas escribe el monto, la pantalla le muestra en vivo cuánto esperaba el sistema
y si coincide — por ejemplo, **"Saldo teórico: 0 Gs. · Coincide"** (en verde), o, si no
coincide, **"Saldo teórico: [monto] · Diferencia: Falta [monto]"** (en rojo) o **"...Sobra
[monto]"** (en ámbar). Si hay diferencia, tiene que completar **"Motivo de la diferencia"**
(obligatorio solo en ese caso, con al menos 10 caracteres reales — si escribe menos, un
contador le va a avisar cuántos faltan).

Al tocar **"Cerrar caja"**, la confirmación ya resume la operación real — por ejemplo,
**"¿Cerrar caja con 0 Gs. contados? Coincide con el saldo teórico."**, o, si hay diferencia,
**"¿Cerrar caja con [monto] contados? Diferencia: falta/sobra [monto]"** — con
**"Sí, cerrar caja"**/**"Cancelar"**. Al confirmar, un mensaje de éxito del tipo **"Caja
cerrada. Saldo teórico [monto], efectivo contado [monto], diferencia [monto] (sin diferencia/
sobrante/faltante)."** — si hubo diferencia, agrega que queda pendiente de revisión del
gerente.

**Atención — es irreversible.** Una vez cerrada, la sesión queda fija para siempre (no se puede
reabrir ni corregir); si se equivocó al contar, vuelva a revisar antes de confirmar.

## Qué hacer si el efectivo contado no coincide

Si al escribir "Efectivo contado" el sistema le muestra una diferencia (en rojo si falta
dinero, en ámbar si sobra), **tiene que explicar el motivo antes de poder cerrar** — el campo
"Motivo de la diferencia" se vuelve obligatorio, con un contador que le avisa cuántos caracteres
le faltan (mínimo 10 reales). Si toca "Cerrar caja" sin completarlo, el sistema le avisa "Falta
completar «Motivo de la diferencia»" y no la deja seguir.

Con el motivo completo, la confirmación le va a mostrar el monto exacto de la diferencia antes
de cerrar (por ejemplo, "¿Cerrar caja con 143.250 Gs. contados? Diferencia: falta 50.000 Gs."),
y el mensaje de éxito le va a avisar que **"Queda pendiente de revisión del gerente."**

**Atención — usted solo informa la diferencia, no la corrige.** Una vez que escribe el efectivo
contado y el motivo, esos números quedan fijos para siempre en esa sesión — no hay forma de
"arreglarlos" después, ni para usted ni para el gerente. Lo único que puede pasar después es que
el **gerente** marque la diferencia como revisada (con su propia observación de qué se hizo),
pero eso tampoco cambia los montos — solo registra que ya se enteró y decidió algo al respecto.

**Atención — en "Sesiones cerradas", una diferencia sin revisar no se distingue claramente de
una ya revisada.** Si el gerente ya la revisó, la fila agrega la palabra "(revisada)" en gris
junto al monto; si todavía está pendiente, simplemente no dice nada — tiene que fijarse en que
*falte* esa palabra, no hay un aviso de "pendiente" explícito en esta tabla. Si necesita
confirmar con seguridad si una diferencia vieja ya se revisó o no, el Informe de Caja (que ve el
gerente/administrador en Reportes) sí tiene una columna clara "Revisada: Sí/No".

**El pie del acta de arqueo** (lo que firma el papel) tiene dos columnas: la suya, con
**"Entregado por: [su nombre]"**, y la del gerente. En la del gerente, el acta **solo escribe un
nombre cuando el sistema tiene registrado a alguien** — y eso pasa únicamente cuando un gerente
revisó una diferencia de esa sesión. En todos los demás cierres dice **"Recibí (firma):"** y
queda en blanco, para que quien reciba el efectivo firme a mano sobre la raya. Es a propósito:
antes el acta imprimía siempre un nombre configurado de antemano, y el papel afirmaba que esa
persona había recibido la plata aunque no la hubiera contado.

**Después de cerrar**, la pantalla vuelve a mostrarle la tarjeta de **"Apertura de caja"** (con
el fondo sugerido ya actualizado) y **"Sesiones cerradas"** incluye la que acaba de cerrar, la
más reciente primero, con su **"Ver arqueo"**. El mensaje de éxito no le dice qué hacer
después — el siguiente paso, en la práctica, es abrir la caja de hoy con el efectivo que le
entregue el gerente.

## Cómo revisa el gerente una diferencia

Para el gerente, no hay una pantalla separada: entra por el mismo camino, **"Desembolso y
caja"** → **"Caja"** (la misma pantalla que usa la cajera). Ahí ve:

- **"Apertura de caja"**: un aviso de que **solo la cajera puede abrirla** (sin el formulario) —
  **atención**, igual le muestra el "Fondo sugerido" aunque no pueda usarlo; no le haga caso a
  ese número si no es cajera.
- Una tarjeta exclusiva para su perfil, **"Diferencias de arqueo sin revisar (N)"** — una fila
  por cada caja cerrada con una diferencia que nadie marcó todavía como revisada: la cajera, las
  fechas de apertura y cierre, si faltó o sobró y cuánto, y el motivo que escribió la cajera. Con
  un campo **"Qué se hizo con la diferencia (obligatorio)"** y el botón **"Marcar revisada"**.
- **"Sesiones cerradas"**: la misma tabla que ve la cajera.

**Atención — usted tiene que entrar a buscarla; nada le avisa que hay una diferencia
pendiente.** Ni el panel, ni el menú, ni ninguna notificación le dicen que hay una caja
esperando revisión — si la cajera no le avisa de palabra (o por chat), puede no enterarse. Entre
a "Caja" después de cada cierre, o pídale a la cajera que le avise cuando cierre con diferencia.

**Atención — usted solo marca que ya se ocupó, no corrige ningún monto.** El campo de texto es
para anotar qué hizo con la diferencia (por ejemplo, si la cubrió de su bolsillo, si la
descontó del sueldo de la cajera, etc.) — no hay forma de cambiar el efectivo contado ni el
saldo teórico de esa sesión, quedan fijos para siempre tal como se cerraron.

**Atención — "Marcar revisada" no le pide confirmar, y es irreversible.** A diferencia de otras
acciones importantes del sistema (como cerrar la caja, que sí le muestra un resumen antes de
pedirle confirmar), acá al tocar el botón ya queda hecho, sin ningún "¿Está seguro?" en el
medio — y una vez marcada, no hay forma de deshacerlo ni de volver a editar lo que escribió.
Revise bien el texto antes de tocar "Marcar revisada".

**Atención — después de marcarla, el sistema no le vuelve a mostrar lo que usted escribió.** El
mensaje de éxito es genérico ("Diferencia revisada"), sin repetir el monto ni su resolución; y
ninguna pantalla (ni "Sesiones cerradas", ni el Informe de Caja) le va a mostrar después qué
escribió — solo queda la palabra "(revisada)". Si necesita recordar qué resolvió con una
diferencia vieja, no hay dónde volver a leerlo dentro del sistema — llévelo aparte si lo
necesita para algo (por ejemplo, el acuerdo firmado con la cajera).

**Atención — el PDF de arqueo de esta caja puede quedar con datos viejos para siempre.** Si
alguien ya pidió "Ver arqueo" de esa sesión ANTES de que usted la marcara revisada, ese mismo
PDF (el único que existe) nunca se actualiza: va a seguir mostrando **"Recibí (firma):" en
blanco**, como si nadie hubiera revisado nada, y nunca va a mostrar su resolución. Si usted pide
el arqueo recién después de revisarla (y nadie lo pidió antes), sí sale con **su nombre en
"Recibí"** — pero igual sin su resolución, porque el PDF de arqueo no tiene espacio para eso en
ningún caso. **Si le importa que el acta lleve su nombre, revise la diferencia antes de que
alguien imprima el arqueo de esa sesión.**

## Atención — si la caja sigue abierta de un día anterior

Si entra a `/caja` y la sesión abierta **no es de hoy**, va a ver un aviso amarillo arriba de
todo: **"La caja del [fecha] sigue abierta: ciérrela y rinda antes de operar hoy."** Mientras
esa caja siga abierta, **no va a poder desembolsar ni cobrar** — el mismo aviso (con el mismo
texto) le va a aparecer también si intenta esas operaciones desde otras pantallas, con un
enlace **"Ir a caja"** para volver acá.

**La solución es siempre la misma**: cierre esa caja vieja (con el arqueo correspondiente a lo
que quedó esa vez) y, recién después, abra la caja de hoy. El aviso, hoy, no la lleva
directamente al formulario de cierre — tiene que bajar un poco en la misma pantalla hasta
"Cierre y rendición".

## Ver el arqueo de una caja ya cerrada

En "Sesiones cerradas", el botón **"Ver arqueo"** abre un PDF de una página: los datos de la
empresa, cajera, apertura y cierre, un **"RESUMEN"** con el fondo inicial y cada tipo de
movimiento que entró o salió (entregas del gerente, cobros, anticipos, cancelaciones, vueltos,
desembolsos, compensaciones) hasta llegar al saldo teórico, el efectivo contado y la
diferencia; la **"COMPOSICIÓN DE LO COBRADO"** (cuánto de lo cobrado fue capital, intereses,
mora, IVA); los **"COMPROBANTES EMITIDOS"** de la sesión (recibos, facturas, constancias,
desembolsos); y el **"DETALLE DE MOVIMIENTOS"**, con la hora, el tipo, la operación, el
comprobante y el monto de cada uno. Al pie, dos firmas: **"Cajera — Entregué: [su nombre]"** y
**"Gerente — Recibí: [nombre del gerente]"**.

**Atención — el nombre que aparece en "Recibí" no siempre significa que ese gerente revisó
esta caja en particular, ni siquiera cuando SÍ hubo una diferencia.** Si la caja cerró sin
ninguna diferencia (el caso más común), ningún gerente tuvo que intervenir en el sistema — el
nombre que ve ahí es el gerente que está configurado como responsable por defecto, no
necesariamente quien de verdad recibió el efectivo esa vez. **Y esto es igual aunque haya
quedado un faltante o un sobrante sin revisar**: mientras nadie haya tocado "Marcar revisada"
para esa sesión, el PDF va a mostrar ese mismo nombre por defecto en "Recibí", como si el
gerente ya hubiera aceptado la diferencia — aunque todavía nadie la revisó. La firma en papel,
al entregar físicamente el dinero, sigue siendo la constancia real de esa entrega.

**Atención — el arqueo tampoco le dice si la diferencia ya fue revisada o sigue pendiente.** A
diferencia del Informe de Caja (que ve el gerente/administrador en Reportes, con su columna
clara "Revisada: Sí/No"), este PDF de una sola sesión no tiene ningún aviso al respecto — tiene
que consultar el Informe de Caja o la pantalla de `/caja` si necesita saber con seguridad si
esa diferencia puntual ya se resolvió.

**El detalle de movimientos solo muestra la hora, no la fecha.** Si una sesión estuvo abierta
más de un día (por ejemplo, abierta un día y cerrada al día siguiente), el PDF no distingue en
qué día ocurrió cada movimiento — tenga presente la fecha de apertura y de cierre de arriba
para ubicarse.

## Lo que se vio el 23/10/2026 (segundo cliente de práctica)

Con la caja abierta desde el 22/10/2026 (06:09, con un desembolso de 3.000.000 Gs. ya aplicado,
saldo teórico en 0 Gs.), al llegar a `/caja` desde el aviso de la pantalla de Desembolso, se vio
exactamente este caso: el aviso amarillo de caja de otro día, la "Sesión abierta" con el saldo
en 0 Gs., el movimiento del desembolso con su comprobante y "Reimprimir", y el formulario de
"Cierre y rendición" listo para usar, con el saldo teórico (0 Gs.) ya calculado.

Al escribir "0" en Efectivo contado (coincidía con el saldo teórico), se cerró sin ninguna
diferencia: confirmación **"¿Cerrar caja con 0 Gs. contados? Coincide con el saldo teórico."**,
éxito **"Caja cerrada. Saldo teórico 0 Gs., efectivo contado 0 Gs., diferencia 0 Gs. (sin
diferencia)."** La pantalla volvió a "Apertura de caja" (ya con el fondo sugerido en
2.000.000 Gs., el monto del préstamo que sigue esperando ser desembolsado) y "Sesiones
cerradas" pasó a mostrar las 2 sesiones ya cerradas, la más reciente primero.

Al tocar **"Ver arqueo"** de esa sesión, el PDF mostró: apertura 22/10/2026 06:09, cierre
23/10/2026 10:10; en el detalle, un desembolso a las 06:20 (−3.000.000 Gs., con su comprobante
`DES-2026-000001`) y la rendición de cierre a las 10:10 (sin operación ni comprobante, 0 Gs.);
comprobantes emitidos: solo ese desembolso. La firma "Recibí" mostró el nombre del gerente
configurado por defecto — esta sesión cerró sin diferencia, así que ningún gerente tuvo que
intervenir realmente en el sistema para esta caja puntual.

Más tarde, la misma cajera cerró la sesión siguiente (abierta a las 10:22, con el desembolso y
el anticipo de capital ya hechos) con una diferencia real: contó **143.250 Gs.**, el sistema
esperaba **193.250 Gs.** — **"Falta 50.000 Gs."** en rojo, y "0 de 10 caracteres mínimos" bajo
el motivo. Al intentar cerrar sin motivo, el aviso "Falta completar «Motivo de la diferencia»"
la frenó; con un motivo de 81 caracteres (contador en verde), la confirmación mostró el monto
exacto de la diferencia y, al aceptar, el éxito avisó **"Queda pendiente de revisión del
gerente."** "Sesiones cerradas" pasó a tener 3 filas, con la nueva mostrando "Falta 50.000 Gs."
en rojo, sin la palabra "(revisada)" — exactamente el caso de la advertencia de más arriba.

Al tocar **"Ver arqueo"** de esta sesión con diferencia, el PDF mostró el mismo RESUMEN con
los montos ya conocidos (fondo inicial 2.000.000; anticipo de capital 193.250, clasificado
junto con "Anticipos de capital", no como un cobro normal; desembolso −2.000.000; saldo teórico
193.250; efectivo contado 143.250; "Falta 50.000 Gs.", con el motivo escrito debajo) y, en el
detalle, la fila final **"Rendición gerente −143.250"** — exactamente el efectivo que se contó
y se le entregó al gerente, no el saldo teórico. La firma "Recibí" mostró, otra vez, el nombre
del gerente configurado por defecto — tal cual la segunda advertencia de más arriba: esta vez
SÍ hay un faltante real y sigue sin revisar, y el PDF igual lo presenta como si ese gerente ya
lo hubiera recibido, sin ningún aviso de "pendiente" en ningún lado del documento.

Después, el gerente entró a "Desembolso y caja" → "Caja" (la misma pantalla) y vio: "Apertura
de caja" con "Fondo sugerido … 0 Gs." y "No hay ninguna caja abierta. Solo la cajera puede
abrirla."; la tarjeta "Diferencias de arqueo sin revisar (1)" con la fila de esta sesión
("Cajera [nombre] — 23/10/2026 10:22 a 23/10/2026 12:50 — faltante de 50.000 Gs.", en rojo, con
el motivo escrito por la cajera), el campo "Qué se hizo con la diferencia (obligatorio)" y el
botón "Marcar revisada"; y "Sesiones cerradas", igual que ve la cajera.

El gerente escribió en "Qué se hizo con la diferencia": *"Se habló con la cajera; el faltante
se descontará de su sueldo según acuerdo firmado."* y tocó "Marcar revisada" — sin ningún
cuadro de confirmación de por medio, el sistema mostró directamente **"Diferencia revisada"**
("Aceptar"). La tarjeta de diferencias pendientes desapareció, y "Sesiones cerradas" pasó a
mostrar "Falta 50.000 Gs. **(revisada)**" — exactamente el caso de las advertencias de más
arriba: ni el mensaje de éxito ni ninguna pantalla posterior volvieron a repetir la resolución
que el gerente acababa de escribir.
