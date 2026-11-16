# RF25 — Reversión de un cobro

**Estado: SOLO ANÁLISIS (12/10/2026), sin tocar código.** Esperando la aprobación de Joel.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF25 (módulo Cobranza y mora): *"El sistema debe permitir a la
cajera solicitar la reversión de un cobro y al gerente autorizarla o rechazarla."*

## 2. Cómo funciona

**Solicitar (cajera)** — `POST /reversiones/solicitar`, botón "Solicitar reversión" en el
"Historial de cobros" de `cobranza/ficha.jsp`, **solo en la fila de la operación más reciente**
de ese préstamo (lo decide `ServicioCobranza.listarHistorialCobros`, nunca la JSP a ojo — regla
de §6c: "sin importar la sesión de caja en la que se hizo"). Pide un motivo (texto libre, obligatorio) y
manda un `identificadorConfirmacion` (UUID nuevo por cada `GET` de la ficha) para que un doble
clic no cree dos solicitudes — `ServicioReversiones.solicitarReversion` además valida que el
pago exista, esté `CONFIRMADO` (no revertido ya), que no haya otra solicitud pendiente o
rechazada sobre el mismo pago, que siga siendo la última operación, **y que el cierre
documental del préstamo no esté ya COMPLETO** (13/10/2026, hallazgo de Joel — ver §6g/§8). Deja
la solicitud en `SOLICITADA`, **sin tocar dinero ni cuotas todavía**.

**Doble control de la factura, empezando en la cajera (pedido de Joel, 12/10/2026)**: si el
cobro generó factura, el formulario de solicitar muestra su número y exige una casilla tildada
("Tengo en mi poder el original y todas las copias de la factura N.º..., devueltas por el
cliente."), con el texto de ayuda "Sin la factura original y sus copias no se puede anular.
Corresponde una nota de crédito; consulte con el contador." Sin la casilla, el `<input
required>` dispara el cartel común "Falta un dato" (pie.jsp) antes de enviar, y
`ServicioReversiones.validarDeclaracionFacturaCajera` la revalida en el servidor (pura, sin
Connection, ver §7) — rechaza con un mensaje claro si falta. El gerente mantiene su **propia**
casilla al autorizar (ver más abajo): son dos controles independientes, uno no sustituye al
otro.

**Mientras la solicitud espera, el cobro sigue abierto — y se avisa (C30, 03/11/2026)**. Los
tres caminos de cobro (`confirmarCobro`, `confirmarAnticipoCapital`, `confirmarCancelacionTotal`)
**no miran** `reversiones_pago` y **no se bloquean**: el cliente que vino a pagar no puede quedar
esperando la decisión del gerente (decisión de Joel: avisar, no bloquear). Pero confirmar
cualquiera de los tres deja la reversión **imposible de autorizar**, porque `autorizarReversion`
revalida que el pago a revertir siga siendo **la última operación** del préstamo — y eso vale
para un cobro de cuota igual que para un adelanto o una cancelación (`PagoDAO.listarPorPrestamo`
ordena por fecha y filtra `CONFIRMADO`, sin distinguir el tipo). Antes nadie lo decía:

- **La cajera** lo ve en el **cartel de confirmación** de los tres caminos, al final y en
  amarillo: *"Hay una reversión pendiente de autorizar sobre el recibo REC-…. Si confirma esta
  operación, esa reversión ya no podrá autorizarse: el gerente solo podrá rechazarla."* No
  impide nada: se lee la consecuencia y se decide.
- **El gerente** lo ve en su bandeja, sobre la solicitud afectada: *"Ya hay una operación
  posterior: solo se puede rechazar."* El botón "Autorizar" **sigue visible** a propósito (la
  decisión es suya; lo único que va a poder hacer es rechazar). Antes se enteraba recién al
  intentar autorizar, con el rechazo automático como único resultado.

**Un dato, un dueño**: los dos textos son de `ServicioReversiones`
(`textoAvisoDeReversionPendiente`, `textoAvisoOperacionPosterior`, puros) y la pregunta "¿ya hay
una operación posterior?" la contesta `hayOperacionPosterior`, que llama **al mismo**
`esLaUltimaOperacion` que usa la autorización — nunca una consulta paralela que pueda decir otra
cosa. El aviso de la ficha viaja a la pantalla en un `data-` y `cobranza-ficha.js` solo lo
muestra (mismo patrón que B79/B92: el JavaScript no redacta).

**Autorizar o rechazar (gerente)** — bandeja `/reversiones` (pestañas "Pendientes"/"Historial"),
protegida por `Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA`:
- **Rechazar** (`POST /reversiones/rechazar/{id}`): pide motivo, no toca nada más —
  `ServicioReversiones.rechazarReversion`.
- **Autorizar** (`POST /reversiones/autorizar/{id}`): el corazón del RF. Orden exacto dentro de
  una sola transacción (`ServicioReversiones.autorizarReversion`):
  1. Revalida `SOLICITADA` + que siga siendo la última operación + que el cierre documental no
     se haya completado entre la solicitud y la autorización (mismo chequeo que al solicitar,
     revalidado acá por el mismo motivo: pudo cambiar en el medio).
     Además, si la constancia de devolución (DEV-...) ya se generó pero el cierre **todavía no**
     está COMPLETO, la bandeja muestra un aviso al gerente (no bloquea, solo advierte): "Ya se
     generó la constancia de devolución del pagaré. Verifique que el pagaré original siga en
     poder de la empresa antes de autorizar."
  2. **DNIT (RG 118/2017)**: si el pago generó factura, exige una casilla tildada ("Tengo el
     original y las copias de la factura ... en mi poder") — sin ella, rechaza con el mensaje
     de que corresponde Nota de Crédito (no disponible en esta versión). La bandeja (pendientes)
     muestra, justo arriba de esa casilla, que la cajera ya declaró lo mismo al solicitar (ver
     arriba) — un aviso informativo, no reemplaza la casilla propia del gerente.
  3. Exige una caja `ABIERTA`, **de hoy**, con saldo teórico suficiente para devolver el monto.
  4. Restaura cada cuota que el pago tocó — la inversa exacta del motor financiero
     (`ServicioMotorFinanciero.revertirAplicacionEnCuota`), nunca una resta a mano.
  5. Si era un anticipo de capital: el plan nuevo que generó queda como versión histórica (RN-13,
     nunca se borra) y el plan anterior vuelve a vigente. Si era una cancelación total: la
     liquidación se marca revertida.
  6. Recalcula el estado del préstamo con `calcularEstadoSegunCuotas` — **nunca**
     `recalcularEstadoPrestamo`, que no sabe revertir un `FINALIZADO`.
  7. Si había factura, la anula (Nota de anulación NAN-aaaa-NNNNNN, numerador propio).
  8. La gestión de cobranza que ese pago haya cerrado (si cerró alguna) vuelve a abierta.
  9. Un movimiento `COMPENSACION` (signo −1) en la caja de hoy, por el monto que se devuelve.
  10. El pago pasa a `REVERTIDO`, la reversión a `AUTORIZADA`.

**"Nunca se borra"**: ni el pago, ni la liquidación (si había), ni la factura (si había)
desaparecen — todas quedan como filas reimprimibles, marcadas (`REVERTIDO`/`REVERTIDA`/
`ANULADA`).

**Reintentar tras un rechazo — Opción A (pedido de Joel, 12/10/2026)**: si el gerente rechaza,
la cajera puede volver a solicitar la reversión del **mismo** cobro con un motivo nuevo (mínimo
10 caracteres), siempre que siga cumpliendo las demás reglas (sobre todo la de §6c: que siga siendo
la última operación — puede haber dejado de serlo si, entre el rechazo y el reintento, se
registró un cobro nuevo). La fila muestra "Reversión rechazada: [motivo]" junto con el campo de
motivo y el botón "Solicitar de nuevo". No se puede tener dos solicitudes pendientes del mismo
cobro (si ya hay una `SOLICITADA`, no se ofrece el botón). Implementación: `reversiones_pago`
tiene `id_pago UNIQUE` — nunca se inserta una segunda fila por el mismo cobro, se **reutiliza**
la misma (`ReversionPagoDAO.marcarResolicitada`: vuelve a `SOLICITADA`, motivo y fecha de
solicitud nuevos, borra el motivo/fecha de la decisión anterior). El historial completo de
**cada** intento (cada solicitud, cada decisión, incluidas las que ya no se ven en la fila
porque se sobrescribieron) queda en `auditoria_operaciones` — la tabla `reversiones_pago` solo
guarda el estado actual.

**Flujo de negocio completo (punto 6, pedido de Joel)**: la cajera se equivoca al cobrar (monto,
cliente, operación) → solicita la reversión con el motivo real → el gerente decide:
- **Autoriza**: se deshace exactamente lo que RF22 registró (cuotas, plan, factura si había) y
  la cajera puede volver a cobrar bien. La salida de efectivo de caja (`COMPENSACION`) queda
  reflejada en el saldo teórico del día; cualquier diferencia entre lo que la caja "debería"
  tener y lo que tiene físicamente se explica y se resuelve en el **arqueo de cierre** (RF19),
  no acá.
- **Rechaza**: el cobro queda firme (el pago sigue `CONFIRMADO`, nada cambió). Si la cajera
  considera que el gerente se equivocó o que puede dar un motivo mejor, puede volver a
  solicitar (Opción A, arriba) — ya no queda "atascada" sin camino, como pasaba antes de este
  cambio (ver §6/§7 de la versión anterior de este documento).
- **Limitación conocida, fuera de alcance**: si el cliente **ya se llevó la factura física**, no
  se anula (DNIT) — correspondería una Nota de Crédito, que esta versión del sistema no emite;
  el gerente debe rechazar en ese caso (o resolver la devolución fuera del sistema) y la cajera
  no vuelve a ofrecer el botón de "poder en poder del cliente" tildado sin tener la factura en
  mano.

**Comprobantes de una reversión autorizada (hallazgo de Joel en pantalla, 12/10/2026)**: tres
documentos distintos, todos reimprimibles, ninguno sale como si la reversión nunca hubiera
pasado:
- **Recibo del cobro revertido** (`recibo.jasper`, reimpresión desde el historial de cobros):
  antes de este hallazgo, un recibo ya impreso ANTES de la reversión quedaba cacheado
  (`pagos.id_archivo_recibo`, D-94) y se seguía entregando como si el cobro fuera válido — **esto
  permitía reimprimir un comprobante de un cobro ya anulado, sin ninguna marca, prestándose a
  fraude**. Corregido: `ServicioCobranza.obtenerPdfRecibo` ya no usa el PDF cacheado si el pago
  está `REVERTIDO` — lo regenera siempre, con una franja roja "ANULADO POR REVERSIÓN" (fecha,
  quién autorizó y motivo) que cubre buena parte de la página.
- **Factura revertida** (`factura.jasper`, mismo hallazgo, mismo arreglo): franja "FACTURA
  ANULADA" con la fecha.
- **Constancia de reversión** (`constancia_reversion.jasper`, documento nuevo, interno, no
  fiscal — a diferencia de la Nota de anulación, que es la que atiende la norma de la DNIT sobre
  la factura): operación, cliente, recibo anulado, monto devuelto en efectivo, motivo de la
  solicitud, cajera, gerente, fecha/hora, y si había factura: **"Factura N.º [número] devuelta
  por el cliente y anulada; original y copias adjuntas."** (texto exacto pedido por Joel,
  12/10/2026). Con el texto "Adjuntar a esta constancia el recibo original y..." y 3 firmas
  (cajera, gerente, cliente — "Recibí conforme la devolución de X Gs."). Se imprime desde el
  cartel de "Reversión autorizada" y se reimprime desde el historial de cobros y desde el
  historial de reversiones. Reusa `reversiones_pago.id_archivo_comprobante` (columna que ya
  existía en el esquema, sin usar) en vez de agregar una columna nueva. Número
  `REV-aaaa-NNNNNN` sin numerador aparte (Joel permitió "o el que ya exista", cambio mínimo):
  usa `id_reversion_pago` como secuencia — ya es único y siempre creciente, y una reversión
  autorizada nunca se vuelve a autorizar.

Ya NO estaba cubierto por §6a (el análisis original decía "cumple completo"): este hallazgo es
posterior, encontrado por Joel al probar en pantalla, no en el análisis de solo lectura.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `SOLICITAR_REVERSION` | PAGO | La cajera solicita — "Solicitó revertir REC-... : [motivo]." Si es un reintento tras un rechazo (Opción A), el detalle agrega "(rechazo anterior: [motivo del rechazo])". |
| `AUTORIZAR_REVERSION` | PAGO | El gerente autoriza — monto, operación, N.º de Nota de anulación si hubo factura, y un aviso extra si el cierre documental ya estaba completo (caso que no debería darse). |
| `RECHAZAR_REVERSION` | REVERSION_PAGO | El gerente rechaza — con el motivo. |

## 4. Dónde está

- **Servicio**: `ServicioReversiones.java` (`solicitarReversion`, `rechazarReversion`,
  `autorizarReversion`, `listarSolicitadas`, `listarDecididas`) y, desde el 03/11/2026 (**C30**),
  el aviso de la reversión pendiente: `textoAvisoDeReversionPendiente`,
  `textoAvisoOperacionPosterior` (los dos puros), `avisoDeReversionPendiente` y
  `hayOperacionPosterior` (solo lectura, sobre el mismo `esLaUltimaOperacion` de la
  autorización).
- **Controlador**: `ControladorReversionesServlet.java` (arma la marca por fila de la bandeja) y
  `ControladorCobranzaServlet.java` (pone el aviso de la ficha en `avisoReversionPendiente`).
- **Vista del aviso**: `cobranza/ficha.jsp` lo lleva en `data-aviso-reversion-pendiente` y
  `recursos/js/cobranza-ficha.js` lo agrega al final del cartel de confirmación de los tres
  caminos (el JS no redacta: solo muestra y escapa).
- **DAO**: `ReversionPagoDAO.java`.
- **Modelo/enum**: `ReversionPago.java`, `EstadoReversion.java` (SOLICITADA/AUTORIZADA/RECHAZADA).
- **Vistas**: `reversiones/bandeja.jsp` (gerente); el botón de solicitar vive en
  `cobranza/ficha.jsp` (cajera), dentro de "Historial de cobros".
- **Menú**: `plantilla/menu.jsp` línea 176 — "Reversiones" visible solo para
  gerente/administrador (la cajera actúa desde `/cobranza`, no tiene bandeja propia).
- **Panel**: `panel.jsp` línea 258/262 — tarjeta "Reversiones por autorizar" para el gerente.
- **Reporte**: `reportes/reversiones` (`informe_reversiones.jrxml`), solo gerente
  (`Permiso.VER_REPORTES`).
- **Permisos**: `Permiso.SOLICITAR_REVERSION` (cajera TOTAL, el resto NINGUNO salvo
  administrador) y `Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA` (gerente TOTAL, el resto NINGUNO
  salvo administrador).

## 5. Dependencias

- **RF22/RF23**: revierte exactamente lo que esos RF registraron (pago, recibo, factura).
- **RF15/RF18 (caja)**: exige caja abierta y de hoy, con saldo suficiente — mismo criterio que
  desembolso/cobro/anticipo.
- **RF24**: la gestión que el pago revertido haya cerrado vuelve a quedar abierta en `/mora`.
- **DNIT, RG 118/2017**: la factura física tiene que volver a poder de la empresa antes de
  anular — si no, no se autoriza (Nota de Crédito, fuera de esta versión).
- **Regla de §6c**: "solo la operación más reciente", sin importar la sesión de caja en que se hizo.

## 6. Análisis

**a) Qué cumple hoy el texto firmado y qué no**: cumple **completo, y bastante más allá del
mínimo**. El texto pide dos pasos (cajera solicita, gerente autoriza o rechaza); el código
cubre eso y además una cantidad de reglas que el texto no pide explícitamente pero que
protegen que la reversión sea segura (DNIT, caja, idempotencia, "solo la última operación",
reversión exacta con el motor financiero en vez de una resta a mano). No encontré ninguna
parte del texto sin cubrir.

**b) Qué miente o confunde en pantalla**: no encontré ningún texto falso. Encontré dos citas
distintas para la misma regla (factura física de vuelta antes de anular) — el aviso de pantalla
citaba "Decreto 6539/05" y el código citaba "RG 118/2017". **Corregido (12/10/2026, pedido de
Joel)**: en pantalla (`reversiones/bandeja.jsp`) ya no se cita ningún número — dice *"corresponde
Nota de Crédito según la normativa de la DNIT; no autorice"*. Las dos citas originales quedan
en el comentario de `ServicioReversiones.autorizarReversion` ("Ajuste I"), con la nota "a
confirmar con el contador cuál es la vigente" — mismo criterio que la revisión legal de la
factura (RF23 §10), sin tocar cuál de las dos es la correcta hasta esa confirmación.

**c) Qué depende de fechas (mora real) y queda para el final**: **nada**. A diferencia de
RF20/RF21/RF22/RF24, la reversión actúa sobre la **última operación** de un préstamo, que
puede ser un cobro de **hoy** — no depende de que una cuota esté vencida ni de que pase el
tiempo. Es, de los 5 RF de este bloque, el único que se puede probar de punta a punta hoy
mismo con un cobro real (ver §9/§11).

**d) Mejoras chicas y seguras que ayuden a la cajera o al oficial**: ninguna para el oficial
(no tiene ningún permiso en este RF, por diseño — ver §6e). Para la cajera, ninguna
encontrada — el botón ya está en el lugar donde ella mira el historial, con la confirmación
("¿Solicitar la reversión de este cobro?") y el motivo obligatorio. No propongo cambios.

**e) Quién ve qué (oficial, cajera, gerente) y si coincide con los perfiles**: coincide
exactamente con el texto, que solo nombra a la cajera (solicita) y al gerente (decide). El
oficial no aparece en el texto y, en el código, tiene `NINGUNO` en los dos permisos
(`SOLICITAR_REVERSION`/`AUTORIZAR_REVERSION_O_DIFERENCIA`) — no ve la bandeja, no tiene el
botón, y una URL directa le da 403 (confirmado en el humo de hoy: "Sin permiso para acceder a
/reversiones"/"/reversiones/solicitar"). Administrador TOTAL en ambos, por D7.

**g) (13/10/2026, hallazgo de Joel) Regla de negocio faltante: cierre documental ya completo**.
Probando CC-2026-00026 (FINALIZADO por cancelación total, cierre documental COMPLETO, pagaré ya
devuelto al cliente), Joel encontró que la cajera **podía** solicitar la reversión de esa
cancelación — si el gerente la hubiera autorizado, el préstamo habría vuelto a tener deuda sin
que la empresa tuviera el pagaré en su poder (el cliente ya se lo llevó). Ninguna de las
validaciones existentes (`esLaUltimaOperacion`, factura, caja) cubría este caso: una cancelación
total siempre es la última operación de su préstamo, así que esa regla nunca la bloqueaba.
**Corregido**: `ServicioReversiones.validarPuedeSolicitarReversion`/`.autorizarReversion`
rechazan si `prestamos.estado_cierre_documental = 'COMPLETO'`, con el mismo mensaje en los dos
lugares — ver §2/§8. Caso intermedio (constancia DEV generada, cierre todavía `PENDIENTE`): no
se bloquea (el pagaré puede seguir en poder de la empresa, con la constancia solo generada y sin
que el cliente la haya firmado todavía), pero la bandeja le muestra al gerente un aviso antes de
autorizar, para que lo verifique a mano.

**Nota sobre la numeración "D-75" — resuelta (13/10/2026, decisión de Joel, PA-23 de
`AVANCE.md`)**: este documento (y el código, `ServicioReversiones.java`/`ServicioCobranza.java`/
`ServicioReversionesTest.java`) citaban "D-75" para la regla "solo se puede revertir la última
operación de un préstamo" (ver §6a/§7/arriba), pero ese número nunca estuvo en la tabla de
Decisiones de `AVANCE.md` — era una reutilización informal del mismo número que sí usa
`REGLAS_DESARROLLO.md` §7, puntos 11-12 (el D-75 real, el incidente de la sesión de caja del
26/09/2026). Se renombró: la regla de este documento ya no se cita como "D-75" en ningún lado,
se cita como "regla de §6c" (este mismo documento) o "RF25_reversion_cobro.md §6c" desde el
código. El D-75 real (`REGLAS_DESARROLLO.md`, incidente de caja) no se tocó.

## 7. Decisiones

**Aprobado por Joel (12/10/2026), condicionado**: punto 3 del análisis sin número de norma en
pantalla (§6b, ya aplicado). RF24 puntos 1/2 confirmados y programados (ver RF24.md). Guion de
prueba con `REC-2026-000003` (único cobro de hoy que sigue siendo la última operación, §9).

**Opción A (12/10/2026, pedido de Joel, tras la pregunta de §6 anterior sobre "si el gerente se
equivoca al rechazar")**: 6 cambios mínimos, todos implementados:
1. Reintentar tras un rechazo, reutilizando la misma fila de `reversiones_pago` (arriba, §2) —
   con pruebas unitarias en `ServicioReversionesTest` (`validarPuedeSolicitarReversion`, pura,
   sin base de datos).
2. Confirmación de la cajera con título y texto propios ("Confirmar: Solicitar reversión" / "¿Solicitar
   la reversión del cobro REC-... por X Gs.?") — nuevo atributo genérico
   `data-confirmar-titulo` en `pie.jsp` (retrocompatible: sin el atributo, sigue diciendo
   "Confirmar").
3. Confirmación del gerente con el monto, en Autorizar y en Rechazar (`reversiones/bandeja.jsp`
   — Rechazar no tenía ninguna confirmación antes de este cambio).
4. Historial de reversiones con columnas separadas "Motivo/Fecha de la solicitud" y
   "Motivo/Fecha de la decisión" — `ReversionPagoDAO.marcarRechazada` ahora también guarda
   `fecha_hora_reversion` (antes solo se guardaba al autorizar).
5. El cartel "Falta un dato" usa el `placeholder`/`aria-label` del campo cuando no hay
   `<label>` — cambio genérico en `pie.jsp` (`etiquetaDeCampo`), confirmado que **no cambia
   nada** para ningún campo que ya tenga `<label>` (el `if (texto)` existente devuelve antes de
   llegar al nuevo fallback). Pantallas que se benefician (campos con `placeholder`/`required`
   sin `<label>`, halladas por `grep`): `caja/ficha.jsp` (RF19, motivo de la diferencia),
   `cobranza/ficha.jsp` (este RF, motivo de la solicitud), `formalizacion/ficha.jsp` (RF14,
   motivo de la anulación), `parametros/administracion.jsp` (3 campos: valor nuevo de un
   parámetro, descripción de un feriado, fuente del límite de tasa) y `reversiones/bandeja.jsp`
   (este RF, motivo del rechazo — el ejemplo exacto que dio Joel). **Corregido (12/10/2026,
   pedido de Joel)**: el campo `tasaMaximaTea` tenía un placeholder de **ejemplo** ("27,39 (%)"),
   no un nombre de campo — el cartel decía "Falta completar «27,39»." Se le agregó
   `aria-label="Tasa máxima (TEA)"` y se invirtió la prioridad en `etiquetaDeCampo` (ahora mira
   primero el `aria-label`, después el `placeholder`) para que ese campo use el nombre real sin
   afectar a los demás (que no tienen `aria-label` y siguen usando su `placeholder` igual que
   antes).
6. Flujo de negocio documentado arriba (§2) y en §6 (más abajo, al cierre del bloque).

**Verificación de auditoría (12/10/2026, solo lectura + un hallazgo corregido)**: confirmado que
`auditoria_operaciones` guarda **cada** solicitud y **cada** decisión por separado, con
`id_usuario` y `fecha_hora` propios (puestos por `AuditoriaDAO.insertar`, `now()`) y el motivo en
`detalle` — nunca solo el estado final. `reversiones_pago` (la fila que `marcarResolicitada`
reutiliza) solo guarda el **último** estado; **el historial muestra el último estado, la
secuencia completa queda en la auditoría (RF40)**. Al revisar esto encontré un bug real en el
reintento: `ServicioReversiones.solicitarReversion` armaba el detalle de auditoría ("rechazo
anterior: ...") leyendo `reversionPrevia.getMotivoRechazo()` **después** de haberlo puesto en
`null` con `reversion.setMotivoRechazo(null)` — como `reversion` y `reversionPrevia` son el
mismo objeto (se reutiliza la fila), el detalle quedaba "(rechazo anterior: null)" en vez del
motivo real. **Corregido**: el motivo anterior se guarda en una variable local antes de
limpiarlo.

**Hallazgo de Joel en pantalla (12/10/2026)**: "el recibo REC-2026-000003, ya REVERTIDO, se
reimprime como un recibo válido, sin ninguna marca." Investigado: la causa es el cacheo de PDF
de D-94 (`obtenerOGenerarPdf`, pensado para un documento que nunca cambia una vez emitido) — un
recibo normalmente se imprime en el momento del cobro, **antes** de que exista ninguna
reversión, así que el PDF guardado en `archivos_binarios` queda fijo con la versión "válida" de
siempre, y la reversión (que pasa días o meses después) nunca lo toca. Mismo problema en la
factura. Tres cambios (detallados en §2, con los 3 puntos de Joel):
1. `obtenerPdfRecibo`/`obtenerPdfFactura` regeneran siempre (sin cachear) cuando el pago está
   `REVERTIDO`/la factura está `ANULADA` — franja roja grande en el PDF, nunca sale como válido.
2. Constancia de reversión nueva (`constancia_reversion.jrxml`), reusando una columna del
   esquema que ya existía sin usar (`reversiones_pago.id_archivo_comprobante`) y un número sin
   numerador aparte (`id_reversion_pago` como secuencia, Joel permitió el atajo).
3. Factura anulada con marca "ANULADA" + fecha, mismo mecanismo que el recibo.
4. Títulos de confirmación del gerente ("Confirmar: Autorizar reversión"/"Confirmar: Rechazar
   reversión"), detalle menor pedido junto con lo anterior.

`mvn test` 700/0 (2 pruebas nuevas de `construirNumeroConstanciaReversion`, más la extensión de
un caso existente en `FiltroAutorizacionTest` para la nueva ruta `/cobranza/{id}/reversion/{id}`).
`herramientas/compilar_reportes.sh` corrido: 38 reportes compilados, `reportes_jasper.sha256`
actualizado.

**Agregado de Joel (mismo bloque, antes de probar): doble control de la factura, empezando en
la cajera**. Hasta este punto, el único control de "factura física de vuelta antes de anular"
era el del gerente, al autorizar — Joel pidió adelantar un primer control a la cajera, al
solicitar. Implementado con `ServicioReversiones.validarDeclaracionFacturaCajera(tieneFactura,
declarado)` (pura, sin `Connection`, igual que `validarPuedeSolicitarReversion` — permite la
prueba unitaria pedida: "solicitud de un cobro con factura sin la casilla → rechazada",
`ServicioReversionesTest.testSolicitarConFacturaSinLaCasillaSeRechaza`), llamada dentro de
`solicitarReversion` antes de insertar/reutilizar la fila.

**Decisión de diseño: sin columna nueva para la declaración de la cajera.** `reversiones_pago`
ya tiene `factura_en_poder_confirmado` para la declaración del **gerente** (se guarda recién al
autorizar); agregar una segunda columna solo para mostrar la declaración de la **cajera**
mientras la solicitud está pendiente no hacía falta, porque la validación de arriba es
**obligatoria**: ninguna solicitud de un pago con factura puede llegar a existir sin que la
cajera haya tildado la casilla. Por lo tanto, en la bandeja del gerente (pendientes), con
`facturaFila != null` siempre se puede mostrar con seguridad "La cajera declaró tener en su
poder..." sin leer ningún dato nuevo — es una consecuencia garantizada de la validación, no una
suposición. La secuencia completa de declaraciones (cajera y gerente, cada una con su fecha y
usuario) queda en `auditoria_operaciones` (eventos `SOLICITAR_REVERSION`/`AUTORIZAR_REVERSION`,
mismo criterio que §7 más arriba sobre el historial de reintentos). Si en el futuro se necesitara
auditar esto de forma más estructurada (no solo texto libre en `detalle`), ahí sí convendría la
columna — por ahora, cambio mínimo.

Textos exactos de pantalla (pedido de Joel): casilla de la cajera "Tengo en mi poder el
original y todas las copias de la factura N.º..., devueltas por el cliente.", ayuda "Sin la
factura original y sus copias no se puede anular. Corresponde una nota de crédito; consulte con
el contador.", constancia "Factura N.º [número] devuelta por el cliente y anulada; original y
copias adjuntas."

`mvn test` 703/0 (3 pruebas nuevas en `ServicioReversionesTest` para
`validarDeclaracionFacturaCajera`). No se tocó ningún `.jrxml` de layout (solo el texto de un
`textFieldExpression` ya existente en `constancia_reversion.jrxml`, recompilado).

**Resultado de la prueba en pantalla de Joel (12/10/2026): "Reversión, auditoría, panel y caja:
OK."** Devolvió 9 ajustes de presentación, cambio mínimo, todos implementados:
1. `constancia_reversion.jrxml`: el número no aparecía ("CONSTANCIA N.º REV-" cortado) — el
   badge de 150 de ancho no alcanzaba para "CONSTANCIA N.º " + el número; se achicó el texto a
   "N.º " (mismo ancho que recibo/factura/nota_anulacion, que sí entran) — "CONSTANCIA" ya está
   en el título debajo.
2. Texto directo, sin "al/a la Sr./Sra.": "...la devolución en efectivo a [nombre], C.I. N.º
   [cédula], por la reversión del cobro N.º [recibo]...".
3. Monto en letras agregado ("Son: ... GUARANÍES.", `ConversorNumerosALetras`, mismo conversor
   que el recibo).
4. "N°" → "N.º" en todo el documento; "Gs.." → "Gs." (la frase de la firma del cliente sumaba un
   punto extra al que ya trae `FormateadorNumeros.comoGuaranies`).
5. Nombres bajo las 3 firmas (cajera/gerente/cliente, con la cédula del cliente).
6. Reimpresión: mismo mecanismo que RF17/RF18 (`ServicioDesembolso.construirMarcaReimpresion`) —
   `obtenerPdfConstanciaReversion` deja de cachear el PDF después de la primera vez (antes usaba
   D-94 como el resto de los comprobantes; acá hacía falta la fecha/hora REAL de cada
   reimpresión, así que se regenera siempre, igual que el comprobante de desembolso).
   `id_archivo_comprobante` pasa a ser solo la marca de "ya se imprimió una vez".
7. Recibo/factura: la franja se movió de un `background` fijo (tapaba "ORIGINAL/COPIA" y
   "Emitido por", recortable) a un `pageHeader` con `printWhenExpression` sobre `REVERTIDO`/
   `ANULADA` — la banda entera no imprime ni ocupa espacio cuando es falso, así que un recibo o
   una factura normal quedan pixel-idénticos a antes. Sello diagonal nuevo ("ANULADO"/"ANULADA")
   sobre todo el cuerpo: como JasperReports solo rota texto en pasos de 90°,
   `GeneradorInformesPdf.construirMarcaAguaDiagonal` genera la imagen con AWT (Graphics2D,
   rotación libre) y se manda como parámetro `byte[]`, igual que el logo.
8. `caja/ficha.jsp`: la fila "Compensación" ya tenía la columna "Comprobante"/"Reimprimir"
   genérica (D-146) — solo hacía falta que `ServicioCaja.listarMovimientosConComprobante`
   resolviera el número (REV-...) y la URL para `referenciaTipo = "REVERSION_PAGO"`, igual que ya
   hace para PAGO/DESEMBOLSO. Sin cambios en la JSP.
9. Misma fila de "Compensación": la fila del cobro revertido ahora agrega "(revertido)" a la
   observación (mismo criterio que el badge "REVERTIDO" del historial de cobros).

**Hallazgos propios al generar las muestras del punto 10** (ver RF23 §7): el párrafo de
`constancia_reversion.jrxml` creció a 3 líneas con el texto directo del punto 2 y pisaba la línea
"Son:" nueva — alto del párrafo aumentado (40→54) y todo lo de abajo corrido en consecuencia,
posiciones fijas (mismo criterio que recibo.jrxml para evitar la duda ya documentada sobre
`positionType="Float"`). Y un "Motivo: [texto de la cajera]. Este documento..." podía dar ".."
si el motivo ya terminaba en punto (mismo tipo de bug que el punto 4) — separador " — " en vez
de depender de que el motivo no termine en punto.

`mvn test` 703/0 (sin pruebas nuevas, son solo cambios de presentación/.jrxml/una resolución de
comprobante ya existente). `herramientas/compilar_reportes.sh` corrido: 38 reportes compilados,
`reportes_jasper.sha256` actualizado. Verificado con PDF de muestra (recibo revertido, no
guardado, solo para esta verificación) que la franja ya no tapa el pie y el sello diagonal se ve
sin romper nada.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **Corrección de B192** (defecto que Joel vio al probarlo) y **B197**. El cuerpo de la ventana decía *"Monto aplicado: 50.000 Gs.."*: `getMontoAplicado()` devuelve el monto **ya formateado** (lo arma `ServicioCobranza:2478`), así que ese *Gs.* ya cierra la oración. Es la **cuarta forma** del punto duplicado y la que se escapaba a las tres redes de `TextosDePantallaTest`, porque el formateo ocurre en Java y el nombre del formateador no aparece en la JSP; ahora la cubre `ningunaJspLePegaUnPuntoAUnGetterDeDinero`, verificada contra el defecto vivo y medida sobre las 46 expresiones seguidas de punto de `src/main` (marca 1, la del defecto). **B197**: el **historial de cobros** pagina de a 20 con su propio parámetro (`paginaHistorial`), y **la página 1 sigue siendo la más nueva**, que es donde vive el botón de reversión — paginar no puede esconderlo. `mvn test` 1033/0. | *(este commit)* | — |
| 15/11/2026 | **B192**: solicitar la reversión de un cobro pasó de un formulario en línea **dentro de una celda de la tabla del historial** —el motivo en un campo de 180 px y, con factura, la casilla donde la cajera declara tener el original y las copias— a una **ventana** por cobro (patrón de B155/B188), con el recibo en el título, el cliente, la C.I. y el monto aplicado en el cuerpo, la casilla de la factura entera con su letra chica, el motivo con su `minlength="10"` y el foco en *"No, volver"*. El cuerpo dice además lo que la fila no decía: que la reversión **no la decide la cajera**, queda pendiente de autorización del gerente. **El formulario y el servidor no cambian**: misma ruta POST, los mismos tres campos ocultos, el mismo CSRF y el mismo id de la casilla por pago; se quitó el `data-confirmar` porque la ventana ES la confirmación. El humo no se ve afectado: inventa su propio `identificadorConfirmacion` y postea directo (línea 4829), nunca lee este marcado. `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 03/11/2026 (tanda 3) | **C30**: con una reversión esperando autorización, los tres caminos de cobro **avisan y no bloquean** (cartel de la cajera) y la bandeja del gerente marca la solicitud que ya solo se puede rechazar — ver §2. Dos textos puros y una consulta sobre el **mismo** `esLaUltimaOperacion` de la autorización. Archivos: `ServicioReversiones` (4 métodos), `ControladorCobranzaServlet`, `ControladorReversionesServlet`, `cobranza/ficha.jsp`, `cobranza-ficha.js`, `reversiones/bandeja.jsp`. **Probado en pantalla el mismo día** (§9). `mvn test` 907/907 | `2029d2c` | — |
| 12/10/2026 | Opción A (6 puntos): reintentar tras un rechazo, confirmaciones con monto, historial con fecha/motivo de la decisión separados, "Falta un dato" con fallback a placeholder/aria-label, flujo de negocio documentado. `mvn test` 698/0 (6 pruebas nuevas). | *(pendiente, sin prueba en pantalla)* | — |
| 12/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | *(pendiente)* | — |
| (sin fecha exacta) | "Solo se puede revertir la última operación de un préstamo", sin importar la sesión de caja (regla de §6c; antes citada por error como "D-75", renombrada el 13/10/2026, ver §10) | — | — |
| 13/10/2026 | Bloqueo de la reversión con cierre documental COMPLETO (solicitar y autorizar) + aviso al gerente si la constancia DEV ya se generó sin estar completo. `ServicioReversiones.java` (+2 pruebas unitarias), `reversiones/bandeja.jsp`. `mvn test` 707/0. | *(pendiente, sin prueba en pantalla)* | — |

## 9. Pruebas realizadas en pantalla

**03/11/2026 08:05-08:18 (Joel, con Sofía cajera y Ramón gerente) — C30, el aviso de la
reversión pendiente.** Caja del 03/11 abierta con fondo 0.

- **Control previo** (lo primero, para que el aviso no sea un cartel que siempre está): con
  `CC-2026-00005` **sin** ninguna reversión pendiente, el cartel de *"Adelantar a capital"*
  (1 cuota, 242.000) salió **sin** recuadro amarillo. **OK.**
- **Sofía solicitó la reversión de `REC-2026-000014`** (motivo *"Prueba del aviso C30"*):
  confirmación *"¿Solicitar la reversión del cobro REC-2026-000014 por 228.050 Gs.?"* con el
  foco en *"No, volver"* → *"Solicitud de reversión enviada / Queda pendiente de autorización
  del gerente."* → el historial pasó a *"Reversión solicitada — pendiente de autorización"*.
- **Con la solicitud pendiente**, los carteles de *"Adelantar a capital"* (242.000) y de *"Todo
  el préstamo"* (2.557.800) mostraron al final: *"Hay una reversión pendiente de autorizar sobre
  el recibo REC-2026-000014. Si confirma esta operación, esa reversión ya no podrá autorizarse:
  el gerente solo podrá rechazarla."* **Los dos se cancelaron** (el aviso no bloquea nada, y la
  prueba no gastó numeración). **OK.**
- **Ramón**: el panel mostró *"1 Reversión por autorizar"* con *"Ir a Reversiones →"*; en la
  bandeja la solicitud apareció **sin** marca amarilla, que es lo correcto — no había ninguna
  operación posterior. Rechazada con *"Prueba C30 terminada, sin error real"* → *"Solicitud de
  reversión rechazada"*. `CC-2026-00005` quedó limpio (rechazar no gasta numeración ni toca
  caja).
- **NO probado en pantalla, cubierto por JUnit**: la marca *"Ya hay una operación posterior:
  solo se puede rechazar."* (habría que confirmar una operación posterior, y se decidió no
  gastar un recibo y una factura en eso) y el aviso en **"Pagar la cuota"** (ese día
  `CC-2026-00005` no tenía ninguna cuota vencida ni venciendo — la próxima es el 30/11/2026).

Ninguna dedicada a este análisis todavía. **Verificado por SQL de solo lectura (12/10/2026)
cuál de los 3 cobros reales de hoy sobre CC-2026-00001 se puede revertir**: los 3
(`REC-2026-000001`, `002`, `003`) son del mismo préstamo, en ese orden — por la regla de §6c
("solo la última operación"), **solo `REC-2026-000003` califica hoy**. `REC-2026-000001` (el
único con factura, `001-001-0000001`) y `REC-2026-000002` ya NO son la última operación de ese
préstamo — el sistema los rechazaría si se intentaran revertir ahora. La prueba de hoy, entonces,
no puede ejercitar la anulación de factura (ver §10). Guion completo en §11.

## 10. Pendientes y trabajo futuro

- **(12/10/2026) Corregido**: en pantalla ya no se cita ningún número de norma (ver §6b);
  queda pendiente que el contador confirme cuál de las dos (Decreto 6539/05 / RG 118/2017) es
  la vigente para dejar una sola cita en el código.
- **(12/10/2026) La prueba de hoy no alcanza para la anulación de factura**: de los 3 cobros
  reales de CC-2026-00001, solo el único con factura (`REC-2026-000001`) ya no es la última
  operación del préstamo — la regla de "solo la última operación" (§6c) lo bloquea. Queda
  pendiente una prueba futura donde el cobro a revertir (con factura) sea efectivamente el
  último de su préstamo, para ver completo el paso 7 de `autorizarReversion` (anular factura +
  Nota de anulación).
- **Prueba en pantalla con un cobro real de hoy (`REC-2026-000003`)** — ver §11, no depende de
  fechas.
- **(13/10/2026) Prueba en pantalla del bloqueo de cierre documental completo**: intentar
  "Solicitar reversión" sobre CC-2026-00026 (cierre documental COMPLETO) y confirmar el mensaje
  de rechazo; y, con un préstamo cuya constancia DEV esté generada pero el cierre siga
  `PENDIENTE`, confirmar que la bandeja muestra el aviso al gerente antes de autorizar.
- ~~**(13/10/2026) Numeración "D-75" repetida para dos reglas distintas**~~ — **resuelto el
  13/10/2026** (decisión de Joel, PA-23 de `AVANCE.md`): la regla de este documento se renombró
  a "regla de §6c" en toda la documentación y el código (ver la nota de arriba, antes de §7).

## 11. Guion para la defensa

**Con el cobro real de hoy (`REC-2026-000003`, CC-2026-00001, sin factura — es el único de los
3 cobros de hoy que todavía es la última operación del préstamo; los otros dos quedaron
bloqueados por la misma regla, ver §9/§10):**

1. (Cajera) En la ficha de CC-2026-00001, "Historial de cobros": mostrar que el botón
   "Solicitar reversión" aparece **solo** en la fila de `REC-2026-000003` — ni en
   `REC-2026-000001` ni en `REC-2026-000002` (ya no son la última operación).
2. (Cajera) Tocar "Solicitar reversión" sobre `REC-2026-000003`, completar el motivo (ej.
   "Prueba de RF25") y confirmar.
3. (Gerente) Entrar a `/reversiones` — ver la solicitud en "Pendientes", con cajera, operación
   CC-2026-00001, recibo REC-2026-000003, monto 481.250 Gs. y el motivo.
4. (Gerente) Como este cobro no generó factura, no debería aparecer la casilla de "factura en
   mi poder" — confirmar que el botón "Autorizar" está disponible sin esa casilla.
5. (Gerente) Autorizar.
6. **Verificar**:
   - En `/cobranza/{CC-2026-00001}`: el recibo `REC-2026-000003` queda con la marca
     `REVERTIDO` en el historial.
   - En `/caja` (sesión abierta de hoy): un movimiento `COMPENSACION` de −481.250 Gs.,
     bajando el saldo teórico.
   - En la base (o el estado de cuenta): el préstamo vuelve a `ACTIVO` con el saldo de capital
     de antes de `REC-2026-000003` (el plan anterior al anticipo vuelve a vigente, RN-13).
   - Nada desaparece: `REC-2026-000003` sigue existiendo, reimprimible, solo marcado.
7. Repetir el caso de **rechazo** sobre otra operación (si hay una disponible) — motivo
   obligatorio, el pago sigue `CONFIRMADO`.
8. Mostrar que el oficial no tiene acceso ni a la bandeja ni al botón (403 directo).
