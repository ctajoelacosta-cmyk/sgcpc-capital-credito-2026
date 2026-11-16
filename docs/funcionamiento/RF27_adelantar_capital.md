# RF27 — Adelantar capital (mostrar antes de confirmar)

**Estado: probado en pantalla el 27/10/2026** (pantalla nueva "¿Cuánto quiere pagar el cliente?",
cajera Sofía Martínez, `CC-2026-00001`, cobro real `REC-2026-000005`). **Cuatro pasadas de prueba
esa noche**, con dos errores de coherencia de pantalla encontrados por Joel y corregidos; la cuarta
cerró OK — ver §9. La pasada anterior fue el 12/10/2026 (tag `rf27-probado`), con la pantalla de
los botones fijos "1/2/3 cuotas", que esta ampliación reemplazó.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF27 (módulo Cancelación): *"El sistema debe permitir adelantar
capital, mostrando antes de confirmar el interés devengado, el capital aplicado, el saldo que
queda y la nueva fecha de la próxima cuota."*

Reglas relacionadas: **RN-11** ("Pago anticipado parcial o total sin cobrar intereses futuros ni
penalidad, Ley 6624/2020. El anticipo parcial reduce la cantidad de cuotas y mantiene el monto de
la cuota."); **RN-13** (un plan nuevo queda como versión histórica, el anterior no se borra).

## 2. Cómo funciona

### 2a. La pantalla desde el 27/10/2026 (ampliación pedida por el tutor)

La tarjeta "Cobranza" hace **una sola pregunta — "¿Cuánto quiere pagar el cliente?" — con tres
opciones excluyentes**, en vez de los botones fijos "1 cuota / 2 cuotas / 3 cuotas" más un
"Cancelar todo" suelto al costado (D-145 P-7, reemplazado):

1. **"Por cantidad de cuotas"** — la cajera elige N entre 1 y **K = las cuotas que le quedan**
   (botones `−` / `+` y una casilla para tipear la cantidad directo; con 24 cuotas, llegar a 17 a
   fuerza de clics era inviable). **El monto de N cuotas NO es "N × la cuota"**: es el **monto
   mínimo que deja exactamente K − N cuotas** en el plan nuevo
   (`ServicioCobranza.calcularMontoMinimoParaDejarCuotas`). Se muestra **un solo monto**, ya
   múltiplo de `credito.redondeo_efectivo`, con la cantidad adelante — desde el 29/10/2026 (**B92**),
   **"Adelanta 4 cuotas (sin intereses futuros): X Gs."**: "Paga" invitaba a comparar ese monto con la
   cuota del plan, que es otra cosa (acá la cuota que desaparece es la última, y el adelanto no paga
   intereses futuros, RN-11). La frase tiene un solo dueño, `ServicioCobranza.etiquetaAdelantoCuotas`: la
   JSP la usa para el primer render y deja las plantillas en un `data-` que `cobranza-ficha.js` completa
   con el número, tanto en la tarjeta como en el cartel de confirmación.
2. **"Por monto"** — la cajera escribe en "Recibido" lo que trae el cliente y el servidor muestra
   qué cubre y cómo queda el préstamo. Con el campo vacío o en cero **no se muestra ninguna vista
   previa**: dice *"Escriba el monto que trae el cliente."* y "Cobrar" queda en gris.
3. **"Todo el préstamo"** — el flujo de cancelación total de siempre (RF26/RF28), sin ningún
   cambio. **N = K** en la opción 1 pasa a este mismo flujo (se puede leer "12 cuotas = todo el
   préstamo"): pagar todas las cuotas que quedan no es un adelanto.

**Por qué se cambió** (motivo del tutor, 27/10/2026, confirmado por Joel en pantalla): con "N × la
cuota", pagar 4 cuotas de un plan de 12 dejaba **7**, no 8, y con 11 el sistema no dejaba
intentarlo. La causa no era un error de cálculo: el adelanto no cobra los intereses futuros
(RN-11, Ley 6624/2020), así que un monto igual a N cuotas **rinde más** que N cuotas. La pregunta
se dio vuelta: la cajera dice cuántas cuotas quiere sacar del plan y el sistema dice cuánto cuesta
exactamente eso. Lo que el cliente deja de pagar se muestra aparte: *"El cliente se ahorra Y Gs.
de intereses por pagar por adelantado."* (Y = N × la cuota programada − el monto, informativo).

**Sin fórmulas nuevas.** `ServicioMotorFinanciero` no se tocó. El monto se busca por búsqueda
binaria sobre los múltiplos de 50 Gs., llamando al `calcularAnticipoParcial` que ya existía sobre
el préstamo cargado **una sola vez** (ninguna iteración vuelve a la base; a lo sumo ~20 llamadas
de aritmética pura por vista previa). La búsqueda es válida porque la cantidad de cuotas del plan
regenerado (el `n'` de `regenerarPlanAnticipo`) es **monótona no creciente** respecto del monto.

**Sobre el redondeo (D-141).** En esta opción el monto **no es un precio con descuento** sino el
mínimo que logra sacar esas cuotas, así que no va el par "exacto / en efectivo" de D-145 (P-2):
un piso a 50 Gs. lo dejaría por debajo del mínimo y agregaría una cuota de vuelta. La búsqueda
recorre directamente los múltiplos de 50, y los ≤ 49 Gs. de diferencia con el exacto van íntegros
a capital — nunca son un cargo. D-141 como descuento a favor del cliente **sigue igual** en
"Pagar lo vencido" y en "Todo el préstamo". Decisión de Joel, 27/10/2026.

**Rótulos.** "Pagar cuota" pasó a **"Pagar lo vencido"** (puede ser más de una cuota, D-144) y
"Cancelar todo" a **"Todo el préstamo"** (se confundía con "cancelar la operación", familia B3).
El cartel de confirmación usa los mismos nombres ("Confirmar: Pagar lo vencido" / "Todo el
préstamo", botones "Sí, cobrar lo vencido" / "Sí, cobrar todo el préstamo"); "Adelantar a capital"
no cambió, es el nombre de la operación en este RF y en el recibo.

### 2b. El cálculo y la confirmación (sin cambios)

El botón "Adelantar a capital" (`cobranza/ficha.jsp`) aparece en vez de "Pagar cuota" cuando el
préstamo no tiene nada vencido por cobrar. **Precisión del 30/10/2026 (paso 15, B98)**: "nada
vencido por cobrar" incluye **la cuota que vence hoy** — mientras esté sin pagar, el adelanto no se
ofrece (ni por cantidad de cuotas ni por monto), porque esa cuota es el cobro contractual del día
(D-144) y RN-11 es para capital que todavía no es exigible. Si el cliente quiere pagarla **y**
adelantar, son **dos operaciones**: primero el cobro, después el adelanto (RN-21 + D-145/D-146: el
excedente es vuelto, no se aplica solo a la próxima cuota). Ver
[RF22_cobro_efectivo.md](RF22_cobro_efectivo.md) §7. El servidor ya calcula, al cargar la ficha, una vista
previa real (`ServicioCobranza.calcularPreviewAnticipoParcial` → `ServicioMotorFinanciero.
calcularAnticipoParcial`) con interés devengado del período en curso + su IVA, capital aplicado,
saldo que queda y la próxima cuota del plan nuevo — **ese cálculo ya existe y ya es correcto**;
se muestra en dos lugares de la pantalla, **antes** de tocar "Adelantar a capital":

**Fórmula del interés devengado** (confirmado contra el código, `ServicioMotorFinanciero.
calcularAnticipoParcial` + `periodoActual`, líneas 822-833/637-663): `capitalPendienteTotal ×
tasaMensual × (díasTranscurridos ÷ díasDelPeríodo)`, donde `díasTranscurridos` son los días
corridos desde el inicio teórico del período actual (para la cuota 1, la fecha de
desembolso/formalización) hasta la fecha del anticipo, y `díasDelPeríodo` son los días de ese
período completo (hasta el vencimiento teórico de la cuota). Como todavía no se pagó ninguna
cuota, `capitalPendienteTotal` es el mismo capital base que ya usa la cuota 1 programada —
`capitalPendienteTotal × tasaMensual` da exactamente el interés programado completo de esa
cuota (100.589 Gs. en la prueba real), así que el resultado equivale, en la práctica, al
interés de la cuota 1 prorrateado por los días transcurridos: en la prueba real, el anticipo
fue 1 día después del desembolso sobre un período de ≈ 44 días, dando 2.286 Gs. (100.589 ×
1/44 ≈ 2.286, redondeado). El motor no toma literalmente el valor guardado de la cuota 1 y lo
escala — lo recalcula desde `capitalPendienteTotal`, que en este caso da el mismo resultado.
- Siempre visible: "Si adelanta, su próxima cuota será el [fecha] por [monto], quedan N cuotas."
  **[fecha] es el día en que se cobra** (ya corrida al siguiente día hábil si hace falta), no el
  ancla teórica del plan — C39, 29/10/2026. La arma `ServicioCobranza.fechaProximaCuotaNuevoPlan`,
  el único dueño, que usan también el recibo del adelanto y la vista previa en vivo.
- Detrás de "Ver detalle del adelanto" (colapsado por defecto): interés devengado + IVA, capital
  aplicado, saldo que queda.

Al tocar el botón, aparece un cartel de confirmación (SweetAlert2, armado a mano en
`cobranza-ficha.js`, **no** el mecanismo genérico `data-confirmar` de `pie.jsp`) con: la acción,
el monto, lo recibido y el vuelto — **sin repetir el detalle de arriba**. Al confirmar, el
servidor (`ServicioCobranza.confirmarAnticipoCapital`) recalcula todo de nuevo de verdad (nunca
confía en lo que mostró la vista previa), guarda el pago, el plan nuevo (versión histórica, RN-13)
y, si corresponde, la factura.

## 3. Auditoría

Ninguna propia — el pago en sí no se audita con un evento de auditoría (a diferencia de otras
operaciones de dinero del sistema, esto queda igual que el resto de `ServicioCobranza`: el rastro
es el propio registro en `pagos`/`liquidaciones_anticipadas`, inmutable por los triggers
`BEFORE DELETE`).

## 4. Dónde está

- **Servicio**: `ServicioCobranza.confirmarAnticipoCapital` (confirma), `.
  calcularPreviewAnticipoParcial` (vista previa, sin grabar nada); `ServicioMotorFinanciero.
  calcularAnticipoParcial` (el cálculo real — interés devengado, capital, saldo, plan nuevo).
- **Controlador**: `ControladorCobranzaServlet.procesarAnticipar` (`/cobranza/{id}/anticipar`,
  `POST`); `.mostrarVistaPreviaAnticipo` (`/cobranza/{id}/vista-previa-anticipo`, `GET`, AJAX).
- **Vistas**: `cobranza/ficha.jsp` (botón + vista previa siempre visible + detalle colapsado);
  `cobranza/fragmento_vista_previa_anticipo.jsp` (mismo contenido, para cuando la cajera cambia
  la cantidad de cuotas a adelantar); `recursos/js/cobranza-ficha.js` (el cartel de confirmación).
- **DTO**: `ResultadoLiquidacion` (interés devengado, IVA, capital del anticipo, nuevo saldo,
  plan nuevo).
- **Parámetros**: `credito.redondeo_efectivo` (50 Gs. — pisa el monto sugerido del botón, en el
  controlador, sin tocar `confirmarAnticipoCapital`, pedido explícito de Joel de no tocarlo).

## 5. Dependencias

- **RF20/RF22**: mismo botón unificado de la ficha de cobranza; "Adelantar capital" es la vía
  real para pagar una cuota que todavía no venció (RF22 la rechaza, ver `RF22_cobro_efectivo.md`
  §6a).
- **RF23**: la factura (si el interés devengado es > 0) sigue el mismo mecanismo de RF23, con un
  solo concepto — ver §6f de este documento.
- **RF26** (cancelación total): comparte `ServicioMotorFinanciero`/mismo tipo de liquidación.
- **RF15/RF18** (caja): exige caja abierta y de hoy, igual que RF22 — ver §6a.

## 6. Análisis (13/10/2026 y prueba del 12/10/2026, pedido de Joel)

**Prueba real de Joel (12/10/2026, cajera), CC-2026-00001 (Mirtha)**: "Adelantar a capital",
1 cuota, recibido 500.000 → "Anticipo de capital aplicado (483.150 Gs.)", vuelto 16.850,
recibo REC-2026-000001, factura 001-001-0000001. Confirmado leyendo `sgcpc_db_pruebas` (solo
lectura, sin tocar nada): `aplicaciones_pago` del pago 1 — capital 480.635, interés 2.286, IVA
229 (2.286+229=2.515, cuadra con `facturas.total_gravada_10`); `facturas` — timbrado 16842795,
001-001-0000001, `total_exenta=0`, `total_gravada_10=2.515`, `iva_10=229` (ver §6f).

**a) Caja de un día anterior abierta — ¿también bloquea el cobro, como el desembolso?** **Sí,
exactamente igual.** Tres citas:
- `ServicioCobranza.confirmarCobro` (RF22, "Cobrar"), líneas 648-656: `sesionCajaDAO.
  obtenerAbierta` + `if (!servicioCaja.esDeHoy(sesion)) throw ... "La caja del [fecha] sigue
  abierta: ciérrela y rinda antes de operar hoy."`
- `ServicioCobranza.confirmarAnticipoCapital` (este RF27, "Adelantar capital"), línea 1096:
  `SesionCaja sesion = validarCajaAbiertaHoy(conexion);` — llama al helper compartido
  (líneas 1240-1251) que hace el MISMO chequeo exacto (también usado por
  `confirmarCancelacionTotal`, línea 861).
- `ServicioDesembolso` (RF16), líneas 244-248: mismo patrón (`sesionCajaDAO.obtenerAbierta` +
  `servicioCaja.esDeHoy`). Las 4 operaciones de dinero del sistema (desembolso, cobro, adelanto,
  cancelación) comparten exactamente el mismo control, aunque 3 de ellas no llamen al mismo
  método compartido (podría unificarse, pero no es parte de este pedido — anotado en §10 por si
  Joel quiere ese refactor más adelante).

**b) RF15/RF18 — registrado como prueba hecha** (ver también `RF15_apertura_caja.md` y
`RF18_movimientos_caja.md`): Joel cerró la caja del 11/10 a las 08:12 (diferencia 0), abrió la
de hoy a las 08:13 con fondo 0, y el cobro de Mirtha entró en esa caja nueva: movimiento `COBRO`
500.000, movimiento `VUELTO` −16.850, saldo teórico resultante 483.150 Gs. — exactamente el monto
aplicado del anticipo. **Sin falla.**

**c) Lo que falta mostrar antes de confirmar (hallazgo de Joel, 12/10/2026) — IMPLEMENTADO**:
el servidor YA calculaba y YA mostraba en la ficha (siempre visible + detrás de "Ver detalle")
los 4 datos que exige el RF27, pero el cartel de confirmación no los repetía. Aprobado por Joel
"como propusiste" (reusar `contenidoDetalleAnticipo`/`textoProximaCuotaAnticipo`, sin cálculos
nuevos): cuando `accionElegida === 'anticipar'`, el `html` del cartel ahora agrega el
`innerHTML` de esos dos elementos (interés devengado + IVA, capital aplicado, saldo que queda,
próxima cuota con fecha y monto, y cuotas que quedan) — sin ningún cálculo nuevo ni llamada al
servidor. Archivo único: `recursos/js/cobranza-ficha.js`.

**d) Confirmación con "Sí" suelto (hallazgo de Joel) — IMPLEMENTADO**: el cartel usaba
`confirmButtonText: 'Sí'` y `title: 'Confirmar'` fijos para las 3 acciones. Implementado con el
texto exacto que pidió Joel: título `'Confirmar: ' + etiquetaAccion` ("Confirmar: Pagar
cuota"/"Confirmar: Cancelar todo"/"Confirmar: Adelantar a capital") y botón "Sí, cobrar" / "Sí,
cancelar todo" / "Sí, adelantar a capital" (coincide con el texto del botón "Cobrar" de la
tarjeta). Mismo archivo, mismo bloque.

**e) Vuelto con monto justo — IMPLEMENTADO**: antes, "anticipar" con vuelto 0 decía "Se aplica
todo a su deuda" y "cobrar"/"cancelar" con vuelto 0 decían "Vuelto: 0 Gs." — dos textos
distintos para el mismo caso. Unificado a **"Sin vuelto (0 Gs.)"** en las 3 acciones cuando
`vuelto === 0`.

**f) Redondeo y plazo — IMPLEMENTADO con el texto corregido por Joel**: el botón ya muestra
"483.199" y, debajo, "en efectivo: 483.150" — el monto real se redondea hacia abajo al múltiplo
de `credito.redondeo_efectivo` (50 Gs., D-141, SEDECO, a favor del cliente). Mi propuesta
original decía "el monto de cada cuota no cambia", que Joel marcó como **falsa** (en la prueba
real la cuota pasó de 483.199 a 482.025 al regenerarse el plan con menos capital — ver §2, la
fórmula del interés devengado, y la nota de abajo). Texto final, dictado por Joel y ya puesto en
`cobranza/ficha.jsp` (justo antes de "Si adelanta, su próxima cuota será..."): *"El monto en
efectivo se redondea a favor del cliente, en múltiplos de 50 Gs. Adelantar a capital reduce la
cantidad de cuotas; el monto de cada cuota queda similar (ver abajo)."*

**Por qué 483.199 → 482.025 (explicación pedida por Joel)**: no es un error — al adelantar
capital se regenera el plan completo (D-87/D-90) con el nuevo saldo, ya reducido. La nueva
cuota 1 se recalcula desde cero (capital constante + interés sobre ese saldo menor), así que su
interés —y por lo tanto su total con IVA— baja un poco respecto de la cuota 1 original. Por eso
"queda similar" es la frase correcta, no "no cambia".

**f) Factura 001-001-0000001 — qué lleva** (verificado contra `sgcpc_db_pruebas`, solo lectura):
**un solo concepto**, "Intereses cuota 1 — CC-2026-00001 — vto. 24/11/2026" por el interés
devengado del período en curso (2.286 Gs.) + IVA 10 % (229 Gs.) = **2.515 Gs. de total
gravado** — **el capital (480.635 Gs.) NO aparece en la factura**, solo en `aplicaciones_pago` y
en el recibo (RN-25, igual que RF22/RF23). Los datos del timbrado (timbrado 16842795,
establecimiento 001, punto de expedición 001) salen de los parámetros `fiscal.timbrado_numero`/
`fiscal.establecimiento`/`fiscal.punto_expedicion` (nunca hardcodeados); el número 0000001 es el
primero que emitió `NumeradorDAO` para la clave `FACTURA` en esta base de pruebas — ver §10 y
`AVANCE.md` sobre reiniciar la numeración en `sgcpc_db` antes de entregar.

## 7. Decisiones

- Las 4 correcciones de §6c-f (confirmación con el detalle completo, título/botón con la
  acción, "Sin vuelto (0 Gs.)" uniforme, texto de redondeo/plazo con la frase exacta de Joel)
  aprobadas e implementadas el 12/10/2026. Pendiente la prueba en pantalla (Clean and Build).
- **(27/10/2026, pedido del tutor, aprobado por Joel) Ampliación de alcance**: la forma de cobrar
  pasa a una sola pregunta con tres opciones (cuotas libres / monto / todo el préstamo). Reemplaza
  D-145 (P-7). Ver §2a. No es solo UX: cambia qué monto se le sugiere a la cajera.
- **(27/10/2026, decisión de Joel — opción B, tras la primera prueba en pantalla)** El monto de
  "N cuotas" es el **mínimo que deja exactamente K − N cuotas**, no "N × la cuota". El tope del
  control es K (las cuotas que le quedan), sin un tope menor, y **N = K usa el flujo de "Todo el
  préstamo"** (`confirmarCancelacionTotal`, RF26/RF28).
- **(27/10/2026, decisión de Joel)** En "Por cantidad de cuotas" se muestra **un solo monto**, ya
  múltiplo de 50 Gs., sin el par "exacto / en efectivo". Motivo en §2a ("Sobre el redondeo").
- **(27/10/2026, decisión de Joel)** "Cobrar" no se habilita mientras la vista previa esté en
  camino ni con un monto rechazado por el servidor: la cajera no confirma un monto que no vio.
- **(27/10/2026, decisión de Joel tras la tercera prueba en pantalla)** En **"Por cantidad de
  cuotas"**, un monto recibido **menor** al de esa cantidad **se rechaza también en el servidor**,
  sin cobrar nada — no se aplica menos en silencio, porque la tarjeta diría "Paga 3 cuotas" y el
  cobro sería de otra cantidad. **En "Por monto" no cambia nada**: ahí el monto libre es la
  respuesta de la cajera y sigue valiendo el `min(recibido, sugerido)` de D-145 (P-5).
  Implementación: un `modoAnticipo` oculto (`"cuotas"` / `"monto"`) en `formAnticiparUnificado`,
  que `cobranza-ficha.js` escribe al confirmar, y el rechazo en
  `ControladorCobranzaServlet.procesarAnticipar` (helpers puros
  `recibidoNoAlcanzaParaLasCuotas`/`mensajeNoAlcanzaParaLasCuotas`, con prueba JUnit). **Sin tocar
  `ServicioCobranza` ni `ServicioMotorFinanciero`.** Un `modoAnticipo` ausente, vacío o manipulado
  se trata como "no es la opción por cantidad" y no rechaza nada: el control nunca endurece de más
  un camino que ya funcionaba. El mensaje del servidor dice los dos montos pero **no** la cantidad
  de cuotas — esa cantidad solo existe en el navegador, y traerla en el formulario sería meter un
  texto manipulable dentro de un mensaje que se muestra en pantalla.
- **(27/10/2026, correcciones de Joel tras la segunda prueba en pantalla)** (1) El rechazo de
  D-90 se muestra con el nombre del botón de esta pantalla — *"...Elija «Todo el préstamo»."* en
  vez de *"...Use Cancelación total."*; se traduce en el controlador
  (`ControladorCobranzaServlet.conNombresDeEstaPantalla`), **sin tocar el texto de la excepción**
  del motor, que protege a cualquier llamador (D-90). (2) Con el monto bloqueado, "Vuelto" muestra
  **"—"**, no "Sin vuelto (0 Gs.)".
- **(27/10/2026, noche — pedido de Joel, diseño aprobado por él) Acomodo de la tarjeta
  "Cobranza", SOLO presentación.** Las tres opciones pasan a un **selector compacto en una fila**
  (la elegida resaltada) y debajo quedan **dos columnas**: a la izquierda lo que la cajera elige
  y escribe, a la derecha **"Resumen del cobro"** como ticket (lo que paga arriba y en grande;
  cuotas que quedan, próxima cuota, ahorro y vuelto debajo; los avisos en rojo y el botón al
  pie). En pantalla chica se apilan. Sin tocar cálculos, endpoints, servlet, servicios ni
  controles del servidor. Archivos: `cobranza/ficha.jsp`, `recursos/css/sgcpc-tema.css`
  (todo prefijado con `.cc-cobranza`, que existe solo en esa tarjeta) y `cobranza-ficha.js`.
- **(27/10/2026, decisión de Joel) El botón dice el monto, y ese monto es el que se cobra EN
  EFECTIVO.** "Cobrar 232.600 Gs."; en gris y sin monto cuando no hay monto válido o el servidor
  bloquea. Con el redondeo de D-141, en "Todo el préstamo" y en "Pagar lo vencido" el botón
  muestra el **monto en efectivo** (2.548.400), no el exacto de la deuda (2.548.415): es lo que
  la cajera tiene en la mano. El texto sale de `montoQueSeVaACobrar()`, que replica rama por rama
  el `montoAplicado` del manejador del clic, así botón y cartel nunca dicen números distintos.
- **(27/10/2026, decisión de Joel — opción (c), tras ver el cartel en pantalla)** Cuando hay
  redondeo de efectivo, el **cartel de confirmación** abre con *"Cobra en efectivo: 2.548.400
  Gs."* y debajo *"Cancela la deuda de: 2.548.415 Gs. (redondeo a favor del cliente: 15 Gs.)"*;
  después, Recibido y Vuelto como siempre. **Sin** redondeo (montos ya múltiplos del parámetro
  `credito.redondeo_efectivo`) el cartel queda exactamente como antes, con una sola línea.
  "Adelantar a capital" nunca entra en este caso: su monto ya viene en múltiplos de 50 (§2a).
  Se descartaron las otras dos opciones que se le plantearon: dejar el cartel como estaba (la
  cajera leía 2.548.415 con el botón diciendo 2.548.400) y mostrar solo el efectivo (se perdía
  de vista cuánta deuda cancela el cobro). **Solo el `html` del cartel; el servidor no cambió.**
- **(27/10/2026, decisión de Joel) "Todo el préstamo" con lo recibido por debajo del monto en
  efectivo se bloquea antes del clic**, igual que "Por cantidad de cuotas": aviso rojo dentro del
  resumen — *"Lo recibido (X Gs.) no alcanza para cancelar todo el préstamo (Y Gs.)."* —,
  "Vuelto" en **"—"** y "Cobrar" en gris. Vale también para **N = K**, que es la misma
  cancelación total. No cambia ninguna regla: con ese monto el cobro nunca salía (lo cortaban el
  manejador del clic y el servidor); el cartel "Falta efectivo" queda como red de seguridad.
- **(27/10/2026, decisión de Joel) Títulos como el resto del sistema**: sin el rótulo "ELEGIR"
  (sobraba) y **"Resumen del cobro"** como `<h5>`, el mismo subtítulo de sección de "Datos
  personales" o "Historial de cobros" — nada en mayúsculas. Y las **tres opciones del mismo
  color** (azul de marca): en un selector segmentado, una de otro color (el ámbar que tenía
  "Todo el préstamo") se ve "elegida" siempre y compite con el resaltado; además `GUIA_VISUAL.md`
  reserva el ámbar para aviso/peligro.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 12/10/2026 | Implementadas las 4 correcciones de §6c-f (`cobranza-ficha.js`/`cobranza/ficha.jsp`) y las de recibo/factura (ver `RF23_recibo_factura.md` §9). Sin commit todavía — a la espera del Clean and Build y la prueba en pantalla de Joel. | *(pendiente)* | — |
| 13/10/2026 | Análisis + registro de la prueba del 12/10/2026 (este documento). Sin cambios de código. | 380b400 | — |
| 12/10/2026 (Joel) | Primera prueba real en pantalla de "Adelantar a capital" (CC-2026-00001, Mirtha) — ver §6/§9 | — | — |
| D-145/D-146 (sin fecha exacta, ajuste de Joel) | Rediseño de la pantalla única de cobranza (botones Pagar cuota/Adelantar a capital/Cancelar todo, vista previa, 1/2/3 cuotas) | — | — |
| 27/10/2026 | **Ampliación pedida por el tutor**: una pregunta con 3 opciones (cuotas libres / monto / todo el préstamo); el monto de N cuotas pasa a ser el mínimo que deja K − N cuotas; método nuevo `ServicioCobranza.calcularMontoMinimoParaDejarCuotas` (sin tocar `ServicioMotorFinanciero`); rótulos "Pagar lo vencido"/"Todo el préstamo"; "Por monto" sin monto escrito ya no deja una vista previa vieja; cierra **B74** y **C28**. Archivos: `ControladorCobranzaServlet`, `ServicioCobranza`, `cobranza/ficha.jsp`, `fragmento_vista_previa_anticipo.jsp`, `cobranza-ficha.js` (+ 2 de prueba). `mvn test`: 795 pruebas, 0 fallos. | `6a274ea` | — |
| 27/10/2026 (2.ª vuelta) | Correcciones de texto de la primera prueba en pantalla: el rechazo de D-90 nombra «Todo el préstamo» (`conNombresDeEstaPantalla`, sin tocar la excepción del motor) y "Vuelto" muestra "—" con el monto bloqueado. | `6a274ea` | — |
| 27/10/2026 (4.ª vuelta) | **Segundo error de coherencia, misma familia, encontrado por Joel y corregido** (ver §9): en "Por cantidad de cuotas" con N = 3, escribir menos que el monto de esas 3 cuotas dejaba la tarjeta en "Paga 3 cuotas" y la vista previa en "le quedan 9", con "Cobrar" habilitado. Ahora "Cobrar" queda en gris con el motivo exacto, y no se pide ninguna vista previa por ese monto. Más dos mejoras de texto pedidas por Joel: el ahorro dice "El cliente se ahorra X Gs. de intereses por pagar por adelantado." y el cartel de confirmación arranca con "Paga N cuotas". Solo `cobranza-ficha.js`, `cobranza/ficha.jsp` y `fragmento_vista_previa_anticipo.jsp`. `mvn test`: 795 pruebas, 0 fallos. | `6a274ea` | — |
| 27/10/2026 (5.ª vuelta) | **Acomodo de la tarjeta "Cobranza", solo presentación** (pedido de Joel, diseño aprobado por él): selector compacto en una fila + dos columnas ("Elegir" / "Resumen del cobro" como ticket, con el botón al pie); el botón dice el monto **en efectivo** ("Cobrar 2.548.400 Gs."); el cartel, con redondeo, abre con "Cobra en efectivo" y explica "Cancela la deuda de … (redondeo a favor del cliente: …)"; "Todo el préstamo" bloquea antes del clic si lo recibido no alcanza; títulos como el resto del sistema. Sin tocar cálculos, endpoints, servlet, servicios ni base. Archivos: `cobranza/ficha.jsp`, `recursos/css/sgcpc-tema.css`, `cobranza-ficha.js`. Probado en pantalla por Joel (§9, sexta prueba). `mvn test`: 800 pruebas, 0 fallos. | `03b8984` | — |
| 27/10/2026 (3.ª vuelta) | **Error de coherencia de pantalla encontrado por Joel y corregido** (ver §9): volver a "Por cantidad de cuotas" dejaba tarjeta, casilla, vista previa y vuelto con cantidades distintas, porque un segundo pedido de vista previa cancelaba al de la cantidad. Solo `cobranza-ficha.js`: se conserva la cantidad al volver, las cinco piezas se actualizan juntas con la respuesta del servidor, hay estado "calculando…" con "Cobrar" en gris, al cambiar de opción "Recibido" toma el monto de la opción nueva, y `montoSugerido` se escribe al confirmar desde el mismo monto del cartel. `mvn test`: 795 pruebas, 0 fallos. | `6a274ea` | — |

| 29/10/2026 | **B92**: la opción "Por cantidad de cuotas" pasa a decir *"Adelanta N cuota(s) (sin intereses futuros): X Gs."*, con **un solo constructor** (`ServicioCobranza.etiquetaAdelantoCuotas`) para los tres lugares que la muestran — antes el texto estaba escrito tres veces, una en la JSP y dos en el JS. Ningún monto ni cálculo cambió. **Probado en pantalla el mismo día** (§9). `mvn test` 847/847 | *(este commit)* | — |

| 30/10/2026 | **B79** (con **B81**): la línea grande del *"Resumen del cobro"* pasa a tener **un solo pintor** (`pintarResumen` en `cobranza-ficha.js`) en vez de cinco escritores sueltos, con dos reglas de Joel: **con un motivo en rojo no afirma ningún monto** (muestra `—`; antes decía *"Adelantar a capital: 3.500.000 Gs."* mientras el aviso decía que no se podía cobrar) y **con bloqueo se ocultan también la vista previa y el detalle del adelanto**, que quedaban con los datos del monto anterior. El estado de bloqueo lo fija un solo lugar, el mismo que muestra el motivo; por eso el **mínimo del adelanto** (D-88/D-90), que se escribía directo en "Vuelto" y era el único bloqueo que no pasaba por ahí, mudó su motivo al renglón rojo —y quedó solo para *Por monto*, porque en *Por cantidad de cuotas* `faltaParaLasCuotas` ya da una frase más útil y antes salían las dos. Ningún monto, cálculo ni endpoint cambió. También se corrigió el **punto duplicado** de ese aviso (*"1.880 Gs.."*, D-145 punto 8) y, con él, dos textos de auditoría de `ServicioCobranza`; el control automático nuevo `TextosDePantallaTest` impide que vuelva. **Probado en pantalla el mismo día** (§9). `mvn test` 858/858 | `acd53c6` | — |

| 30/10/2026 (paso 15) | **B98**, precisión de §2b: *"nada vencido por cobrar"* incluye **la cuota que vence hoy** — mientras esté sin pagar, el adelanto **no se ofrece** (ni por cantidad de cuotas ni por monto), y eso es correcto: esa cuota es el cobro contractual del día (D-144) y RN-11 es para capital que todavía no es exigible. Si el cliente quiere pagarla **y** adelantar, son **dos operaciones** (RN-21 + D-145/D-146: el excedente es vuelto). La nota de la pantalla dejó de decir *"lo vencido"* y ahora nombra la cuota del día; el texto lo arma `ServicioCobranza.notaAntesDeAdelantar`. Sin cambios en el cálculo del adelanto. Ver [RF22_cobro_efectivo.md](RF22_cobro_efectivo.md) §7 | `1f71147` | — |
| 05/11/2026 | **B106** (tramo 2 de B86): *"Adelantar a capital"* —el nombre de esta operación en la línea grande del *"Resumen del cobro"* y en el título del cartel— era el **único** texto visible del sistema que **no tenía dueño en el servidor**: estaba escrito a mano en `cobranza-ficha.js` y en ninguna otra parte. Pasó a `ServicioCobranza.ACCION_ADELANTAR_A_CAPITAL`, y con él el rótulo **«Por monto»** (`OPCION_POR_MONTO`, que comparte con el mensaje del servidor que manda a esa opción) y el botón del cartel *"Sí, adelantar a capital"* (`BOTON_ADELANTAR_A_CAPITAL`). El JS los lee de un `data-`, no los redacta (regla de B92). **Mismas palabras, ninguna lógica ni cálculo tocado**; el mínimo del adelanto (D-88/D-90/D-145) y la vista previa quedaron intactos. `mvn test` 907/907 | `5a22902` | — |

## 9. Pruebas realizadas en pantalla

**12/10/2026 (Joel), cajera, CC-2026-00001 (Mirtha)**: "Adelantar a capital", 1 cuota, recibido
500.000 → "Anticipo de capital aplicado (483.150 Gs.)", vuelto 16.850, REC-2026-000001, factura
001-001-0000001. Caja: cerrada la del 11/10 a las 08:12 (diferencia 0), abierta la de hoy a las
08:13 con fondo 0, cobro registrado ahí (RF15/RF18, sin falla). **Hallazgo**: falta el detalle
completo (interés devengado, capital aplicado, saldo que queda, próxima cuota) en el cartel de
confirmación — ver §6c, propuesta sin programar.

**12/10/2026 (segunda vuelta) — implementación, sin prueba en pantalla todavía**: programadas
las 4 correcciones de §6c-f más las del recibo/factura que Joel encontró al revisar los
comprobantes de la prueba anterior (detalle completo en `RF23_recibo_factura.md` §9):
cartel de confirmación con el detalle reusado, título/botón con la acción, "Sin vuelto (0 Gs.)"
uniforme, texto de redondeo/plazo con la frase exacta de Joel; recibo con un solo monto de
cuota y "Firma de la cajera"/"N.º"; factura sin la referencia coloquial del domicilio y con la
tabla de detalle corregida (`stretchType`). `mvn test`: 692 pruebas, 0 fallos (sin pruebas
nuevas — cambios de presentación/maquetación, sin lógica de negocio nueva). Los 2 `.jrxml`
tocados recompilados con `herramientas/compilar_reportes.sh`. **Falta la prueba en pantalla**
(Joel la hace con el Clean and Build) — repetir "Adelantar a capital" con Mirtha (u otro
préstamo) y revisar el cartel, el recibo y la factura nuevos.

**12/10/2026 (tercera vuelta) — prueba en pantalla OK, salvo 2 detalles ya corregidos**: Joel
probó las 4 correcciones de la vuelta anterior en pantalla — **todo bien**, salvo: (1) con
recibido = monto exacto, el campo "Vuelto" de la pantalla (antes de confirmar) seguía diciendo
"Se aplica todo a su deuda" en vez de "Sin vuelto (0 Gs.)" — esa frase vieja (y la equivalente
"0 Gs." sin aclaración, en "Cobrar"/"Cancelar todo") quedaba en las funciones que calculan el
texto en vivo (`recalcularAnticipo`/`recalcularCobrar`/`recalcular`), aparte del cartel —
unificadas las 3 a "Sin vuelto (0 Gs.)". (2) los 2 últimos renglones del cartel de "Adelantar
capital" (`renglonesProximaCuota`) pasaron de la oración completa ("Si adelanta, su próxima
cuota será el FECHA por MONTO, quedan N cuotas.") al mismo formato "dato: valor" que el resto
del cartel: "Próxima cuota: FECHA por MONTO" / "Cuotas que quedan: N" — los 3 valores se leen
de los `<strong>` de `textoProximaCuotaAnticipo` (cobranza/ficha.jsp, sin tocar), no se
recalcula nada. `mvn test`: 692 pruebas, 0 fallos (cambios de JS, sin lógica nueva).

**12/10/2026 (cuarta vuelta) — reprueba OK**: Joel confirmó en pantalla "Sin vuelto (0 Gs.)" y
el cartel con "Próxima cuota: ..." / "Cuotas que quedan: 8" — **RF27 queda probado**, tag
`rf27-probado`.

### 27/10/2026 — pantalla nueva "¿Cuánto quiere pagar el cliente?" (probado por Joel)

**Probado en pantalla por Joel el 27/10/2026**, cajera **Sofía Martínez**, `CC-2026-00001`.
Registro de lo observado, tal como lo pasó Joel:

| Qué probó | Qué mostró la pantalla |
|---|---|
| **Por cantidad de cuotas, N = 1** | "Paga 1 cuota: **234.600 Gs.**"; próxima cuota **21/11/2026** por **288.539**; **le quedan 11**; "se ahorra **55.319**" |
| **N = 4** | **932.550 Gs.**; próxima **287.369**; **le quedan 8**; se ahorra **227.126** |
| **N = 11** (tipeado directo en la casilla) | **2.732.800 Gs.**; próxima **283.397**; **le queda 1**; se ahorra **456.309** |
| **Por monto, campo vacío** | Desaparece la vista previa; *"Escriba el monto que trae el cliente."*; "Cobrar" en gris |
| **Por monto, 1.000.000** | Le quedan **8 cuotas**; próxima **287.256**; capital aplicado **988.935**; saldo **2.011.065** |
| **Por monto, 3.500.000** | *"El monto cubre todo el saldo del préstamo. Use Cancelación total."* y "Cobrar" en gris |
| **Todo el préstamo** | **3.011.065 Gs.** (en efectivo **3.011.050**) — sin cambios respecto de antes |

**Cobro real, N = 1**: cartel *"Confirmar: Adelantar a capital"* (234.600, sin vuelto, 11 cuotas)
→ *"Anticipo de capital aplicado (234.600 Gs.)"*. **Recibo `REC-2026-000005`** (capital 223.535 +
interés 10.059 + IVA 1.006; 11 cuotas restantes, último vencimiento 21/09/2027; saldo 2.776.465) y
**factura `001-001-0000002`** (interés 11.065 Gs., IVA 10 % 1.006). La ficha se actualizó sola a
"de 11 cuotas que le quedan".

**Lo que la prueba dejó corregido** (dos cambios de texto, aprobados y aplicados el mismo día; ver
§7): el mensaje de bloqueo ahora dice *"...Elija «Todo el préstamo»."* en vez de *"...Use
Cancelación total."*, y con el monto bloqueado "Vuelto" muestra **"—"** en vez de "Sin vuelto
(0 Gs.)". **Las dos quedaron confirmadas en pantalla en la segunda prueba** (más abajo).

### 27/10/2026, segunda prueba (tras el Clean and Build) — error de coherencia de pantalla

**Probado por Joel**, mismo préstamo, ya con 11 cuotas restantes. Las dos correcciones de texto:
**OK**. El estado inicial y el `+` hasta 3: **coherentes** ("Paga 1 cuota: 228.050", casilla 1, le
quedan 10, Recibido 228.050 → "Paga 3 cuotas: 697.950", casilla 3, le quedan 8, Recibido 697.950).

**Error reproducido**: con la secuencia **N = 3 → "Por monto" → escribir 3.500.000 → volver a "Por
cantidad de cuotas"**, la pantalla quedaba contradictoria — tarjeta con el monto de N = 3, casilla
en 1, vista previa y vuelto de N = 1, y "Cobrar" habilitado. Si se confirmaba, el cartel mostraba
228.050 pero el formulario mandaba `montoSugerido = 3.500.000` (sobrante de "Por monto") y el
motor lo rechazaba con **D-90 sin cobrar nada**: el dinero estaba protegido, la pantalla no.

**Causa raíz**: al volver a la opción, se pedía `?cuotas=1` pero el recálculo inmediato disparaba
un segundo pedido `?monto=…` que **cancelaba** al primero; un pedido por monto no trae el monto de
la cantidad, así que la tarjeta nunca se actualizaba.

**Corregido** (solo `cobranza-ficha.js`): se conserva la cantidad al volver a la opción; las cinco
piezas (tarjeta, monto elegido, `montoSugerido`, "Recibido", vuelto) se actualizan **juntas** con
la respuesta del servidor; mientras se calcula, la tarjeta dice "calculando…", la vista previa
vieja se oculta y "Cobrar" queda en gris; al cambiar de opción "Recibido" toma el monto de la
opción nueva; y `montoSugerido` se escribe al confirmar desde el mismo monto del cartel, así lo
que el servidor aplica es por construcción lo que la cajera confirmó. Detalle completo y la
revisión de todas las transiciones en `docs/recorrido/06_primer_cobro.md`, Paso 18.

### 27/10/2026, tercera prueba — la secuencia del error quedó corregida, y apareció otra

**Probado por Joel**, `CC-2026-00001` con 11 cuotas restantes. **La secuencia del error anterior
quedó corregida**: N = 1 coherente → `+` hasta 3 ("Paga 3 cuotas: **697.950**", casilla 3, le
quedan 8, Recibido 697.950, **interés devengado 0** porque ya se había cobrado ese mismo día,
saldo 2.078.515) → "Por monto" (Recibido 697.950, le quedan 8) → escribir 3.500.000 (bloqueo con
*"…Elija «Todo el préstamo»."*, "Vuelto —", "Cobrar" en gris) → volver a "Por cantidad de cuotas":
**todo en N = 3** (tarjeta 697.950, casilla 3, le quedan 8, Recibido 697.950, sin vuelto).

**El cartel también coincidió**: *"Confirmar: Adelantar a capital"* con 697.950, Recibido 697.950,
sin vuelto, interés 0 + IVA 0, capital 697.950, saldo 2.078.515, próxima 21/11/2026 por 288.066,
quedan 8. Con "Cancelar" la pantalla quedó igual, sin cobrar.

**Segundo error, de la misma familia.** En "Por cantidad de cuotas" con N = 3 (697.950), al
escribir **500.000** en Recibido quedaba: tarjeta *"Paga 3 cuotas: 697.950 Gs."*, vista previa
*"le quedan 9 cuotas"*, capital aplicado 500.000, "Sin vuelto" y **"Cobrar" habilitado** — la
tarjeta decía 3 cuotas y el cobro real habría sido de 500.000 (≈ 2 cuotas). Causa: el recálculo
pedía la vista previa del monto recibido (D-145 P-5, "se puede adelantar menos que lo sugerido"),
que en esta opción contradice el rótulo de la tarjeta.

**Corregido** (solo pantalla): en "Por cantidad de cuotas", si lo recibido no alcanza para esa
cantidad, **"Cobrar" queda en gris**, "Vuelto" muestra "—", **no se pide ninguna vista previa por
ese monto** (así tarjeta y vista previa siguen siendo las de la cantidad elegida) y se muestra el
motivo: *"Lo recibido (X Gs.) no alcanza para N cuotas (Y Gs.). Elija menos cuotas o use «Por
monto»."* Con Recibido ≥ el monto, el vuelto funciona como siempre.

**El servidor también lo rechaza** (decisión de Joel del 27/10/2026, aplicada — ver §7): antes
`procesarAnticipar` aplicaba `min(montoRecibido, montoSugerido)` (D-145 P-5) y **no sabía qué
opción eligió la cajera**, porque el mismo `POST /anticipar` sirve a las dos. Ahora el formulario
manda un `modoAnticipo` oculto y, cuando es `"cuotas"` y lo recibido es menor que lo sugerido, la
operación se rechaza completa con el mismo aviso de la pantalla, sin cobrar nada. **"Por monto"
quedó igual.** Sin tocar `ServicioCobranza` ni el motor; con prueba JUnit sobre los helpers puros.

**Dos mejoras de texto pedidas por Joel y aplicadas**: el ahorro dice *"El cliente se ahorra X Gs.
de intereses por pagar por adelantado."* (antes "Se ahorra X Gs. de intereses."), y el cartel de
confirmación arranca con **"Paga N cuotas"**, con las mismas palabras que eligió la cajera.

### 27/10/2026, cuarta prueba — **todo OK, RF27 queda probado en pantalla**

**Probado por Joel**, `CC-2026-00001` con 11 cuotas restantes, después del Clean and Build:

| Qué probó | Qué mostró la pantalla |
|---|---|
| **N = 1** (228.050), Recibido **500.000** | **Vuelto 271.950**, "Cobrar" habilitado · *"El cliente se ahorra **60.489 Gs.** de intereses por pagar por adelantado."* |
| **N = 3** (697.950), Recibido **500.000** | En rojo: *"Lo recibido (500.000 Gs.) no alcanza para 3 cuotas (697.950 Gs.). Elija menos cuotas o use «Por monto»."* · **Vuelto "—"** · "Cobrar" **en gris** · **la tarjeta y la vista previa siguen en 3 cuotas / le quedan 8** |
| Bajar con **−** a **N = 2** (460.650) | El aviso **desaparece solo** · le quedan **9** · **vuelto 39.350** · "Cobrar" habilitado · ahorro **116.428** |
| Cartel de confirmación (N = 2) | **"Paga 2 cuotas"** en negrita arriba; Adelantar a capital 460.650, Recibido 500.000, Vuelto 39.350, saldo 2.315.815, próxima 21/11/2026 por 288.543, quedan 9 |
| **"Cancelar"** en el cartel | No se cobró nada |
| La secuencia del error anterior (cantidad → monto 3.500.000 → cantidad) | **Corregida**: todo coherente en N = 3, y el cartel coincide con la tarjeta |

**Con esto queda probado en pantalla el rediseño completo de RF27** (las tres opciones, la
cantidad libre de cuotas, el bloqueo por efectivo insuficiente, el ahorro y el cartel). Los dos
errores de coherencia de pantalla de las pasadas anteriores quedaron **cerrados y verificados**.

<!-- Captura pendiente (Joel): N=3 con Recibido 500.000 (aviso en rojo, Vuelto "—", Cobrar en gris). -->
<!-- Captura pendiente (Joel): el cartel con "Paga 2 cuotas" arriba. -->

### 27/10/2026, quinta prueba — idempotencia con un doble clic real (OK)

**Probado por Joel**, `CC-2026-00001` (11 cuotas restantes), cajera Sofía Martínez. **Primera vez
en todo el proyecto que se prueba un doble clic real sobre la confirmación de un cobro.**

- **Doble clic rápido sobre "Sí, adelantar a capital"** con N = 1 (**228.050 Gs.**): **un solo**
  cartel de éxito — *"Anticipo de capital aplicado (228.050 Gs.)"* —, recibo
  **`REC-2026-000006`**, y *"Sin factura (no hay conceptos gravados)"* (correcto: el interés del
  día ya se había cobrado en `REC-2026-000005`, así que el interés devengado era 0 y no hay nada
  gravado que facturar, RF23). **El historial quedó con UNA sola fila nueva** (07:52). **No se
  duplicó.**
- **F5 después de "Aceptar"**: el navegador **no** preguntó por reenviar el formulario, y el
  historial siguió con 2 filas (07:52 `REC-2026-000006` 228.050 y 06:32 `REC-2026-000005`
  234.600). Es el redirect-después-de-POST de D-89 funcionando: tras confirmar, el servidor
  responde con un `sendRedirect` a la misma ficha, así que recargar no reenvía nada.
- Después del cobro quedan **10 cuotas** y la ficha pasó a mostrar *"Paga 1 cuota: 232.600 Gs."*

Con esto queda probada en pantalla la **idempotencia por `identificadorConfirmacion`** (§14.3),
que esta ampliación no tocó: un UUID por formulario generado al armar la ficha, con el
`SQLState 23505` como red final.

<!-- Captura pendiente (Joel): el historial con las dos filas (REC-000006 y REC-000005) tras el doble clic. -->

**Sigue sin probarse en pantalla** (pendientes explícitos, ver §10): **N = K** ("Todo el préstamo"
por la vía del control de cantidad — la lógica de RF26 en sí ya se probó el 25/10/2026 con
`CC-2026-00002`), el **caso con cuotas vencidas** (pasos 12-16, `RF22_cobro_efectivo.md` §10) y
**C39** (la cuota programada vence el 23/11/2026 y el plan regenerado muestra el 21/11/2026).

<!-- Captura pendiente (Joel): pantalla "¿Cuánto quiere pagar el cliente?" con N = 4. -->
<!-- Captura pendiente (Joel): recibo REC-2026-000005 y factura 001-001-0000002. -->
<!-- Captura pendiente (Joel): mensaje de bloqueo con «Todo el préstamo» y "Vuelto: —". -->

### 27/10/2026, sexta prueba — el acomodo nuevo de la tarjeta (OK)

**Probado por Joel**, `CC-2026-00001` con **10 cuotas restantes**, después del Clean and Build y
un Ctrl+F5 (hacía falta: cambiaron `sgcpc-tema.css` y `cobranza-ficha.js`, que el navegador
cachea). Esta pasada es **solo presentación** — ver §7, "Acomodo de la tarjeta «Cobranza»".

| Qué probó | Qué mostró la pantalla |
|---|---|
| El acomodo | Selector en **una fila**; **sin** el rótulo "ELEGIR"; **"Resumen del cobro"** con el mismo estilo que "Historial de cobros" |
| **Por cantidad, N = 1** | "Paga 1 cuota: **232.600**", le quedan **9**, ahorro **56.411**, botón **"Cobrar 232.600 Gs."** |
| **Por monto, 232.600** | "Adelantar a capital: **232.600**", mismo botón |
| **Todo el préstamo** | Resumen **2.548.415** / "en efectivo **2.548.400**"; botón **"Cobrar 2.548.400 Gs."** (el monto en efectivo, no el exacto) |
| **Todo el préstamo**, Recibido **2.500.000** | Aviso rojo *"Lo recibido (2.500.000 Gs.) no alcanza para cancelar todo el préstamo (2.548.400 Gs.)."* · **Vuelto "—"** · "Cobrar" **en gris**, sin monto |
| **Todo el préstamo**, Recibido **2.600.000** | **Vuelto 51.600** · botón **"Cobrar 2.548.400 Gs."** |
| **Cartel** (sin confirmar) | *"Cobra en efectivo: **2.548.400 Gs.**"* / *"Cancela la deuda de: **2.548.415 Gs.** (redondeo a favor del cliente: **15 Gs.**)"* / Recibido 2.600.000 / Vuelto 51.600 — **mismo número que el botón** |
| **"Cancelar"** en el cartel | **No se cobró nada** |
| Ida y vuelta: N = 3 → Por monto 3.500.000 → volver a Por cantidad | **Todo coherente en N = 3**: "Paga 3 cuotas: 712.000", le quedan **7**, Recibido 712.000, botón "Cobrar 712.000 Gs.", detalle capital 712.000 / saldo 1.836.415. En el paso intermedio, el aviso *"…Elija «Todo el préstamo»."* con Vuelto "—" y "Cobrar" en gris |
| **Zoom ampliado** | El menú lateral pasa a íconos y **nada se encima** |

**Con esto queda probado en pantalla el acomodo nuevo**, junto con las cuatro decisiones de esa
ronda (botón con el monto en efectivo, cartel opción (c), bloqueo de "Todo el préstamo" cuando no
alcanza, y los títulos). `mvn test`: **800 pruebas, 0 fallos**.

**Lo único que quedó anotado para más adelante** (hallazgo **B79** en `FASE2_PLAN.md`, área H,
solo registrado): con el monto bloqueado, la línea grande del resumen sigue mostrando el monto
tecleado (*"Adelantar a capital: 3.500.000 Gs."*) mientras el aviso de abajo dice que no se puede
cobrar. No es un error — describe lo tecleado y nada deja cobrarlo —, pero conviene evaluar
mostrar otra cosa en esa línea.

<!-- Captura pendiente (Joel): la tarjeta "Cobranza" con el selector en una fila y las dos columnas. -->
<!-- Captura pendiente (Joel): "Todo el préstamo" con Recibido 2.500.000 (aviso en rojo, Vuelto "—", Cobrar en gris). -->
<!-- Captura pendiente (Joel): el cartel con "Cobra en efectivo" y "Cancela la deuda de". -->
<!-- Captura pendiente (Joel): la tarjeta con la ventana angosta, las dos columnas apiladas. -->

### 27/10/2026 13:21, séptima prueba — **primera vez con FACTURA por "Por cantidad de cuotas"** (OK)

**Probado por Joel**, cajera **Sofía Martínez**, y por primera vez sobre **`CC-2026-00003`** (las
seis pasadas anteriores fueron todas sobre `CC-2026-00001`). Es la primera vez que la vía nueva
"Por cantidad de cuotas" **emite una factura**: en la quinta prueba el interés devengado del día
ya se había cobrado en `REC-2026-000005`, así que `REC-2026-000006` salió *"Sin factura (no hay
conceptos gravados)"* — correcto, pero dejaba sin probar el camino con concepto gravado.

**Por qué acá sí había interés que facturar, sin esperar días** (DOGMA 1): `CC-2026-00003` se
había desembolsado el **26/10**, así que el 27 tenía **1 día de interés devengado**. No se tocó
ninguna fecha de la base.

| Qué probó | Qué mostró la pantalla |
|---|---|
| **Por cantidad, N = 1** | "Paga 1 cuota: **140.150**", le quedan **4**, ahorro **40.095** |
| Detalle del adelanto | interés devengado **532** + IVA **53**, capital **139.565**, saldo **679.835** |
| Cartel de confirmación | "Paga 1 cuota" / "Adelantar a capital: **140.150**" / Recibido **140.150** / **Sin vuelto** |
| Resultado | *"Anticipo de capital aplicado (140.150 Gs.)"* — Recibo **REC-2026-000008** y **Factura 001-001-0000003** |

**La factura salió con el diseño nuevo de RF23** y Joel la revisó impresa, campo por campo: una
sola línea *"Interés compensatorio hasta el 27/10/2026 (anticipo de capital)"* por **585 Gs.** en
la columna 10 %, subtotales 0/0/585, "Total de la operación" 585 Gs., IVA (10 %) 53. El detalle
completo de la factura, con los datos legales y la verificación posterior contra la base, está en
**`RF23_recibo_factura.md` §9** — esa prueba y esta son la misma pasada, vista desde cada RF.

**Lo que confirma para RF27**: el reparto que muestra la pantalla es el que se registra. 532 + 53
= **585** = el total gravado de la factura, y 139.565 (capital) + 585 = **140.150** = lo recibido;
el plan vigente quedó en **4 cuotas** y el saldo de capital tras la cuota 1 (514.474) más el
capital de esa cuota da los **679.835** del detalle. El capital **no se factura** (RN-25): quedó
solo en `aplicaciones_pago` y en el recibo.

**Evidencia**: `evidencias\2026-10-27\recibos\REC-2026-000008.pdf` y
`evidencias\2026-10-27\facturas\001-001-0000003.pdf`. Ver `evidencias\2026-10-27\LEEME.md`.

### 27/10/2026 (tarde), octava prueba — **la pantalla nueva con cuotas VENCIDAS** (OK)

Las siete pasadas anteriores fueron todas sobre préstamos **al día**, donde la ficha ofrece
"Por cantidad de cuotas", "Por monto" y "Todo el préstamo". Esta es la primera con una cuota
**realmente vencida** (`CC-2026-00003`, cuota 1 vencida hacía 5 días, preparada con el
desplazamiento de D-81 — ver `AVANCE.md` del 27/10/2026), que es el otro estado de la misma
pantalla: con vencidas, el adelanto a capital desaparece y aparece **"Pagar lo vencido"**.

**Método**: el resultado se calculó **a mano y sellado antes** de abrir la pantalla, por Joel y por
Claude por separado, y después se comparó. Coincidieron los tres (`RF21_deuda_exigible.md` §9).

| Qué se usó | Resultado |
|---|---|
| **"Pagar lo vencido"**, cobrando menos que lo exigible | Pago parcial con promesa — `REC-2026-000009`, 100.000 de 180.696 (`RF24_seguimiento_mora.md` §9) |
| **"Pagar lo vencido"**, cobrando el resto | `REC-2026-000010`, 80.696 aplicados, vuelto 50, redondeo 46 (`RF22_cobro_efectivo.md` §9) |
| **"Todo el préstamo"** | Cancelación total — `REC-2026-000011`, 516.372 liquidados / 516.350 en efectivo, constancia CAN-2026-000002, préstamo `FINALIZADO` (`RF26_cancelacion_anticipada.md` §9) |

**Lo que cierra para RF27**: la pantalla nueva quedó probada en **sus dos estados** — al día (las
siete pasadas anteriores) y con vencidas (esta). El selector mostró las opciones correctas para
cada caso y los montos de pantalla coincidieron con lo que se registró.

**Lo que NO cierra**: el camino **N = K** (llegar a la cancelación total por el control de
cantidad, poniendo N igual a las cuotas que quedan) — sigue siendo el punto 1 del PUNTO DE
RETOMADA. Esta pasada llegó a la cancelación por la opción **"Todo el préstamo"**, no por N = K.

**Un hallazgo de esta pasada, registrado y no corregido (B81)**: en el pago parcial, la línea
grande del "Resumen del cobro" siguió diciendo *"Pagar lo vencido: 180.696 Gs."* mientras se
cobraban 100.000. Misma familia que **B79** — la línea describe la opción elegida y su importe
exigible, no lo que se va a cobrar. No impidió nada (el cobro salió correcto), pero conviene
evaluarla junto con B79. Ver `FASE2_PLAN.md`, área H.

**Evidencia** de esta pasada: `evidencias\2026-10-27\recibos\REC-2026-000009.pdf`,
`REC-2026-000010.pdf` y `REC-2026-000011.pdf`, con sus facturas
`001-001-0000004.pdf` y `001-001-0000005.pdf`. Ver `evidencias\2026-10-27\LEEME.md`.

**29/10/2026, Joel (cajera Sofía Martínez, 23:46-23:47, `CC-2026-00005`) — B92, el texto de
"Por cantidad de cuotas"** (sin confirmar el cobro):

- Con **1**: *"Adelanta 1 cuota (sin intereses futuros): 232.600 Gs."*
- Con **3**: *"Adelanta 3 cuotas (sin intereses futuros): 711.950 Gs."* — plural correcto.
- El **cartel de confirmación** arranca con la misma frase, sin monto pegado, y sigue con el
  detalle de siempre. Se canceló sin cobrar.
- La rama *"Pagar lo vencido"* **no se probó en pantalla** (hoy no hay cuotas vencidas) y **no se
  tocó**: queda anotada así, no dada por probada.

## 10. Pendientes y trabajo futuro

- ~~**(27/10/2026) Reprueba de las dos correcciones de texto** (mensaje con «Todo el préstamo»,
  "Vuelto: —")~~ — **confirmadas en pantalla por Joel el 27/10/2026** (segunda prueba, §9).
- ~~**(27/10/2026) Tercera prueba en pantalla pendiente**: la corrección de la coherencia de la
  pantalla al cambiar de opción~~ — **probada OK por Joel el 27/10/2026** (cuarta prueba, §9).
  Nota que sigue vigente: `cobranza-ficha.js` no tiene pruebas automáticas en este proyecto, así
  que esa parte se verifica en pantalla, no con `mvn test`.
- **(27/10/2026) `N = K` ("Todo el préstamo" desde el control de cantidad) sin probar en
  pantalla**: no se probó para no dejar `CC-2026-00001` en `FINALIZADO`. Verificado por código y
  con JUnit (la búsqueda no devuelve monto de adelanto con objetivo 0, y el monto de cancelar todo
  queda por encima del de N = 11). **La lógica de RF26 en sí ya está probada en pantalla** desde
  el 25/10/2026 con `CC-2026-00002` (`RF26_cancelacion_anticipada.md` §9): lo que falta es solo
  llegar a ese flujo por esta vía nueva.
- ~~**(27/10/2026) Doble clic en "Cobrar" sin probar en pantalla**~~ — **probado OK por Joel el
  27/10/2026** (quinta prueba, §9): doble clic rápido sobre "Sí, adelantar a capital" → un solo
  cobro, `REC-2026-000006`, una sola fila en el historial. La idempotencia por
  `identificadorConfirmacion` no se tocó en esta ampliación.
- ~~**(27/10/2026) C39 (hallazgo nuevo, sin corregir)**: la cuota programada vence el 23/11/2026,
  pero el plan regenerado tras el adelanto muestra la próxima el **21/11/2026** y el último
  vencimiento el **21/09/2027**.~~ — **RESUELTO y PROBADO EN PANTALLA el 29/10/2026**: era
  **presentación**, no el motor. `regenerarPlanAnticipo` guardaba bien las dos fechas de cada cuota
  (la teórica —el ancla— y la de cobro, ya corrida a día hábil); lo que mostraba la teórica eran
  tres textos: el recibo del adelanto, "Su próxima cuota será el…" de la ficha y la vista previa en
  vivo. Hoy los tres piden la fecha a `ServicioCobranza.fechaProximaCuotaNuevoPlan` (puro, único
  dueño). El `21/09/2027` nunca fue parte del defecto: sale de la fecha de cobro y ese día es
  martes. Ver `FASE2_PLAN.md` (C39).
- ~~**(13/10/2026) Posible unificación de `validarCajaAbiertaHoy`**: `confirmarCobro` repite el
  mismo chequeo inline en vez de llamar al helper que ya usan `confirmarAnticipoCapital`/
  `confirmarCancelacionTotal`.~~ — **resuelto (19/10/2026, ronda de duplicaciones, Commit 1,
  `55567c7`)**: `confirmarCobro` ahora llama al mismo `validarCajaAbiertaHoy`, que a su vez
  delega en `ServicioCaja.validarSesionDeHoy` (único lugar para los 3 métodos). Ver
  `AVANCE.md`.
- **(13/10/2026, Joel) Numeración de recibos y facturas en `sgcpc_db`**: anotado en `AVANCE.md`
  "Antes de entregar" — la prueba de hoy ya consumió `REC-2026-000001`/`001-001-0000001` en
  `sgcpc_db_pruebas`; antes de entregar, la numeración en la base real debe arrancar en 1.
- **(12/10/2026) `ANTICIPO_CUOTA_NUEVA` sigue en `ServicioCobranza` sin usarse en `recibo.jrxml`**:
  al sacar la línea confusa del recibo (ver `RF23_recibo_factura.md` §9, punto 1), el parámetro
  `ANTICIPO_CUOTA_NUEVA` quedó calculado pero sin imprimirse — cambio mínimo, no se tocó el
  Java. Si en algún momento se confirma que no hace falta en ningún lado, se puede sacar.
- **(12/10/2026) Falta la firma del emisor en la factura**: ver `RF23_recibo_factura.md` §10 —
  análisis a pedido de Joel sobre los datos obligatorios de una factura de autoimpresor
  (DNIT); no se agregó nada, es solo análisis.

## 11. Guion para la defensa

1. Abrir la ficha de Mirtha (al día) — mostrar "Adelantar a capital" con el monto exacto y "en
   efectivo" redondeado, y la vista previa siempre visible ("su próxima cuota será...").
2. Abrir "Ver detalle del adelanto" — mostrar interés devengado, capital aplicado, saldo que
   queda.
3. Confirmar el adelanto — mostrar el cartel de confirmación (con el detalle completo, si la
   propuesta de §6c ya está aprobada y programada) y el resultado final con recibo y factura.
4. Mostrar la factura: un solo concepto (interés + IVA), sin el capital.
