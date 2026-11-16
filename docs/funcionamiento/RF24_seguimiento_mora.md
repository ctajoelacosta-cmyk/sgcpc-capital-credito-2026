# RF24 — Seguimiento de mora y gestiones de cobranza

**Estado: analizado y con las 2 mejoras chicas de §6d programadas (12/10/2026), pendiente de
Clean and Build y prueba en pantalla.**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF24 (módulo Cobranza y mora): *"El sistema debe mostrar los
préstamos en mora con sus días de atraso y permitir registrar las gestiones de cobranza
(llamada, mensaje o visita), incluidas las promesas de pago con su fecha y monto."*

Aclaración ya registrada (`AVANCE.md`, PA-20): "RF-38 Alerta de garante" que aparece en
`docs/00_especificacion_maestra.md` §9 es numeración INTERNA de ese documento, no del
anteproyecto firmado — el RF38 real es otra cosa, sin relación con mora. La "alerta de
garante" que existía en el código (`ServicioMora`/`mora/lista.jsp`) nunca fue parte del texto
que se está consolidando acá; se mencionaba en §5 solo porque convivía en el mismo código. Se
eliminó por completo con el retiro de codeudor/garante (18/10/2026) — ver §5/§7.

## 2. Cómo funciona

`GET /mora` (`ControladorMoraServlet.mostrarLista`):
1. Dispara `ServicioMora.recalcularMorosidadDiaria` — recorre todos los préstamos
   ACTIVO/EN_MORA y recalcula su estado con la MISMA regla que ya usa `ServicioCobranza`
   (cuota no pagada con fecha de vencimiento efectiva < hoy). Se protege con una guarda de
   "una vez por día" (auditoría `RECALCULO_MORA_DIARIO`) — no hay tabla nueva, la propia
   auditoría hace de marca. La llaman también `/panel` y los reportes de Morosidad/Cartera/
   Incobrables, lo que se abra primero en el día.
2. `ServicioMora.listarPrestamosEnMora` devuelve una fila por préstamo EN_MORA, ordenadas con
   **promesa vencida primero**, después por días de atraso descendente.
3. Cada fila trae: operación, cliente, teléfono, días de atraso (y "Incobrable" si supera
   `credito.dias_incobrable`), cuotas vencidas, monto exigible (el mismo cálculo de RF21,
   `calcularDeudaExigible`), promesa abierta (fecha y monto, en rojo si ya venció) y última
   gestión registrada (tipo y fecha). La columna de alerta de garante se eliminó por completo
   con el retiro de codeudor/garante (18/10/2026) — ver §5/§7.

`POST /mora/{idPrestamo}/gestion` (`ServicioMora.registrarGestion`): modal con 5 campos —
Contactado (cliente), Tipo (Llamada /
Mensaje / Visita al local — `TipoGestionCobranza`, el mismo `CHECK` de `gestiones_cobranza`
que usa `PAGO_PARCIAL` para los pagos parciales de RF22, pero esa opción no se ofrece acá:
`registrarGestion` la rechaza explícitamente, "se genera solo al cobrar"), Resultado (texto
libre), Excusa (si no pagó, texto libre) y Promesa de pago (fecha + monto, ambos opcionales
pero si se pone uno hace falta el otro — validado en el servidor). Queda en `gestiones_cobranza`,
una transacción corta, sin tocar dinero ni caja.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `RECALCULO_MORA_DIARIO` | SISTEMA (id 0) | Una vez por día, al abrir `/mora`, `/panel` o un reporte de cartera — "N préstamo(s) cambiaron de estado." |
| `RECALCULAR_ESTADO_MORA` | PRESTAMO | Solo si ESE préstamo cambió de estado en el recálculo — "ACTIVO -> EN_MORA" (o viceversa). |
| `REGISTRAR_GESTION_COBRANZA` | GESTION_COBRANZA | Cada gestión manual registrada — "Gestión Llamada sobre CC-2026-00001." |

## 4. Dónde está

- **Servicio**: `ServicioMora.java` (`recalcularMorosidadDiaria`, `listarPrestamosEnMora`,
  `listarIncobrables`, `registrarGestion`, `listarGestionesDelPrestamo`).
- **Controlador**: `ControladorMoraServlet.java` (`GET /mora`, `POST /mora/{id}/gestion`).
- **DAO**: `GestionCobranzaDAO.java`.
- **Modelo/enum**: `GestionCobranza.java`, `TipoGestionCobranza.java`, `FilaMora.java` (dto).
- **Vista**: `mora/lista.jsp` (la única pantalla; el modal de "Registrar gestión" vive adentro).
- **Menú**: `plantilla/menu.jsp` línea 175 — visible para oficial/gerente/administrador, oculto
  para la cajera.
- **Panel**: `panel.jsp` líneas 116/177 — tarjeta con enlace directo a `/mora`.
- **Permiso**: `Permiso.REGISTRAR_GESTION_COBRANZA` — `FiltroAutorizacion` protege toda la ruta
  `/mora` con este único permiso (no distingue leer de escribir).

## 5. Dependencias

- **RF20/RF21**: el monto exigible de cada fila es el mismo cálculo de la deuda exigible
  (`ServicioCobranza.calcularDeudaExigible`) ya analizado en `RF21_deuda_exigible.md`; "días de
  atraso"/"EN_MORA" usa la misma regla que ya decide la exclusividad de la lista de trabajo de
  RF20 (`RF20_lista_trabajo_cobranza.md`).
- **RF22**: `PAGO_PARCIAL` (la gestión que genera un cobro parcial con promesa) es del mismo
  `CHECK` de `gestiones_cobranza`, pero la crea solo `confirmarCobro` — `registrarGestion` la
  rechaza si alguien la manda desde este formulario.
- **RF25**: si un pago se revierte, la gestión que ese pago haya cerrado (si cerró alguna)
  vuelve a quedar abierta (`GestionCobranzaDAO.marcarNoCumplidaPorPago`, dentro de
  `autorizarReversion`) — ver `RF25_reversion_cobro.md`.
- **Retiro de codeudor/garante (18/10/2026)**: la columna/alerta de garante, que D9/D-125 dejaba
  oculta mientras `credito.exigir_garante = "nunca"`, se eliminó por completo del código
  (`ServicioMora`/`mora/lista.jsp`) — ya no es una condición, no existe.
- **Parámetros**: `credito.dias_incobrable`.

## 6. Análisis

**a) Qué cumple hoy el texto firmado y qué no**: cumple **completo**. Las dos obligaciones del
texto están ambas implementadas y conectadas entre sí: "mostrar los préstamos en mora con sus
días de atraso" (la tabla de `/mora`, con "Incobrable" como agregado) y "permitir registrar las
gestiones de cobranza (llamada, mensaje o visita), incluidas las promesas de pago con su fecha
y monto" (el modal, con los 3 tipos exactos del texto y los 2 campos de la promesa). No
encontré ninguna parte del texto sin cubrir.

**b) Qué miente o confunde en pantalla**: nada que diga algo falso. Dos puntos menores de
redacción, no de datos:
- El campo "Excusa (si no pagó)" y "Resultado" pueden superponerse en la cabeza de quien
  completa el formulario (¿el resultado de la llamada va en "Resultado" o en "Excusa"?) — los
  dos son texto libre, sin guía. No es un error, solo ambigüedad de UX.
- La columna "Última gestión" muestra tipo y fecha, pero no el "Resultado" que se escribió —
  para verlo hay que abrir el estado de cuenta del cliente. No miente, pero obliga a un clic de
  más para algo que ya está cargado.

**c) Qué depende de fechas (mora real) y queda para el final**: **todo lo que depende de que
exista un préstamo realmente EN_MORA**. Confirmé por SQL de solo lectura contra
`sgcpc_db_pruebas`: **no hay ningún préstamo `EN_MORA` en este momento** — ni siquiera de
prueba (el `CC-2026-00005` de Bloque 4/E8 quedó en `PENDIENTE_DESEMBOLSO`, no en mora). Como el
botón "Registrar" de cada fila **solo existe dentro de la tabla de `/mora`**, no hay ningún otro
camino en el sistema para registrar una gestión hoy — ni la pantalla principal (mostrar la
lista) ni la secundaria (el modal) se pueden probar de punta a punta sin una cuota realmente
vencida. Es exactamente el mismo punto que ya quedó pendiente en RF20/RF21/RF22: la prueba con
fechas reales, al final (decisión de Joel).

**d) Mejoras chicas y seguras que ayuden a la cajera o al oficial**: ninguna para la cajera (no
tiene acceso a `/mora`, por diseño — RF24 es del oficial). Para el oficial/gerente, 2
**implementadas (12/10/2026)** tras confirmar que el humo, corrido sin caja abierta, deja un
préstamo real `EN_MORA` visible en `/mora` (Bloque 4/E8 — ver §10):
1. Mostrar el "Resultado" de la última gestión junto al tipo/fecha en la columna "Última
   gestión" (evita el clic extra al estado de cuenta).
2. Un aviso en el formulario ("si pone fecha de promesa, el monto es obligatorio") antes de
   enviar — hoy la validación existe pero solo se ve después de que el servidor la rechaza.

**e) Quién ve qué (oficial, cajera, gerente) y si coincide con los perfiles**: coincide
exactamente. `Permiso.REGISTRAR_GESTION_COBRANZA`: oficial TOTAL, gerente TOTAL, cajera
NINGUNO (administrador TOTAL por D7, sin excepción en ningún permiso). El menú oculta "/mora"
para la cajera (coherente con el permiso, no solo cosmético: `FiltroAutorizacion` también lo
bloquea). Esto coincide con el reparto ya confirmado en RF20 (la cajera cobra, el oficial sigue
la mora) — RF24 es, en los hechos, la pantalla de seguimiento que le corresponde al oficial.

## 7. Decisiones

Las 2 mejoras chicas de §6d, aprobadas por Joel el 12/10/2026 condicionadas a que el humo
pudiera dejar un préstamo `EN_MORA` visible sin caja abierta — confirmado (§10) antes de
programarlas.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B197** (tanda 2): la bandeja de `/mora` con **contador**, **encabezados que ordenan** y **paginación de 20**. Lo propio de esta pantalla: su orden de siempre **no es una columna**, es la **prioridad de trabajo** que promete el subtítulo (*promesa vencida primero, después por días de atraso descendente*), así que esa sigue siendo la clave por defecto (`prioridad`) y es además el **desempate** de cualquier otro orden. *Teléfono*, *Promesa* y *Última gestión* **no ordenan**: llegan como texto ya formateado y una fecha *dd/mm/aaaa* ordena mal como cadena (B157). El **sentido por defecto depende de la columna**: descendente donde *más* es *más urgente* (días, cuotas, monto), ascendente en los alfabéticos. Las reglas viven en `ServicioMora` (puras, con lista blanca) y el controlador pasa los parámetros crudos (B167), con **10 JUnit** nuevos. De paso, el bucle que arma un token por fila recorre **solo la página**. **Efecto en el humo**: se rompe la línea 4466 y está medido — ver `FASE2_PLAN.md`, nota *B197 y el humo*. `mvn test` 1033/0. | *(este commit)* | — |
| 15/11/2026 | **B194**: la ventana de *"Registrar gestión"* descarta con *"No, volver"* y no con *"Cancelar"* — era la única ventana del sistema que había quedado con ese verbo, y acá *cancelar* además significa otra cosa en el negocio (se cancelan solicitudes y préstamos). Es el criterio de **B116** aplicado a los modales. **Sin `autofocus` a propósito**: registrar una gestión no es irreversible ni mueve dinero, así que no entra en el criterio de B105 y el foco útil es el primer campo. `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 12/10/2026 | Las 2 mejoras chicas de §6d: `FilaMora`/`ServicioMora`/`mora/lista.jsp` (resultado de la última gestión) + aviso "Falta un dato" (mismo cartel/botón que el resto del sistema, pie.jsp) con texto propio si hay fecha de promesa sin monto. Corregido el mismo día (pedido de Joel, consolidación ítem 9): la primera versión usaba el "required" nativo del navegador, reemplazada por el mecanismo común antes de probar en pantalla. Sin pruebas unitarias nuevas (son de presentación). `mvn test` en verde. | *(pendiente)* | — |
| 12/10/2026 | Reprueba de Joel en pantalla (oficial, CC-2026-00027): lista y gestión con resultado/promesa/monto OK. Falló el control de "Falta un dato" con fecha sin monto (el formulario se enviaba igual). Corregido: `mora/lista.jsp` pasó de un `document.addEventListener('submit', ..., true)` delegado a atar el manejador DIRECTO a cada `<form>` de gestión tras `DOMContentLoaded` (mismo patrón que `campo-guaranies.js` en esta misma página). Punto 2: nueva `MontoPrometidoFaltanteException` (mismo patrón que `MotivoDiferenciaInsuficienteException`, RF19) para que `ControladorMoraServlet` muestre el rechazo del servidor como advertencia ("!"), no como error (X), cuando falta el monto. `mvn test` 705/0. | *(pendiente)* | — |
| 12/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | `a189848` | — |
| D-119 (sin fecha exacta) | El fixture de prueba de mora (Bloque 4/E8) siempre crea su propio préstamo, nunca reusa uno existente | — | — |
| D9/D-125 (sin fecha exacta, pedido de Joel) | Columna/alerta de garante oculta mientras `credito.exigir_garante = "nunca"`. Superado por el retiro de codeudor/garante (18/10/2026, commit `6e7da13`) — ver `docs/funcionamiento/RF05_solicitud.md` §10. | — | — |
| D-146 (sin fecha exacta) | La cuota más antigua sin pagar decide "días de atraso" en la lista de trabajo de `/cobranza` (RF20), mismo criterio que ya usaba `/mora` | — | — |

## 9. Pruebas realizadas en pantalla

### 27/10/2026 (Joel), cajera Sofía Martínez, `CC-2026-00003` — **el ciclo completo de RN-20, de punta a punta, en una jornada**

Hasta hoy este documento decía *"queda para cuando haya un préstamo realmente EN_MORA"*. Ya lo
hubo: `CC-2026-00003` con la cuota 1 vencida hacía **5 días**, preparada con el desplazamiento de
fechas de D-81 (−35 días, ver `AVANCE.md` del 27/10/2026). El resultado se calculó **a mano y
sellado antes** de abrir la pantalla, por Joel y por Claude por separado, y después se comparó
(método y cuenta en `RF21_deuda_exigible.md` §9).

**La mora, en pantalla:** préstamo recalculado a `EN_MORA`, 5 días de atraso, **moratorio 542** y
**punitorio 163** (30 % del moratorio, D-82) sobre una base de 165.361 de capital pendiente, más
70 de IVA de la mora.

**El ciclo de la promesa (RN-20), completo y cerrado el mismo día:**

| Paso | Qué pasó |
|---|---|
| 1. Pago parcial | `REC-2026-000009` (13:59), 100.000 Gs. sobre 180.696 exigibles → al no cubrir lo vencido, el sistema **exigió la fecha de promesa** |
| 2. Gestión registrada | `gestiones_cobranza`: tipo `PAGO_PARCIAL`, **promesa para el 05/11/2026**, **monto prometido 80.696**, `cumplida = false` |
| 3. Pago del resto | `REC-2026-000010` (14:02): **exactamente los 80.696 prometidos** |
| 4. Promesa cumplida | la gestión pasó a **`cumplida = true`** sola, al detectar el pago del monto prometido |

**Esto es lo que ninguna prueba anterior había mostrado**: que la promesa no solo se crea y se
lista, sino que **se cierra por sí misma** cuando el cliente paga lo que prometió. Verificado
después contra la base (solo lectura): una sola fila en `gestiones_cobranza` para el préstamo 3,
con `fecha_promesa = 2026-11-05`, `monto_prometido = 80696` y `cumplida = t`.

**Lo que esta pasada NO cubrió**, y queda en §10: el tramo de **promesa incumplida** (la fecha
pasa sin que el cliente pague, `GestionCobranzaDAO:123`), y los tramos de mora de más de 30 días
— el préstamo quedó `FINALIZADO` a las 14:04 del mismo día.

**Un hallazgo de esta pasada, registrado y no corregido (B80)**: después de correr las fechas por
SQL, la bandeja de Cobranza siguió diciendo "En mora: 0" y la búsqueda "Al día" hasta que se abrió
la ficha. Se investigó en solo lectura y **no es un defecto de RF24**: `recalcularMorosidadDiaria`
corre una vez por día y ese día ya había corrido (`RECALCULO_MORA_DIARIO` del 27/10 a las
**04:25:43**, confirmado en `auditoria_operaciones`). En la vida real, cuando una cuota vence de un
día al otro, el primer acceso a `/panel` o `/mora` del día siguiente no encuentra la marca y el
recálculo sí corre. El caso que vimos solo aparece si los datos cambian **por fuera de la
aplicación** después del recálculo del día. Ver `FASE2_PLAN.md`, área A.

**Evidencia**: `evidencias\2026-10-27\recibos\REC-2026-000009.pdf` (el parcial que generó la
promesa) y `evidencias\2026-10-27\recibos\REC-2026-000010.pdf` (el pago de los 80.696 prometidos,
que la dejó cumplida). La mora facturada, en
`evidencias\2026-10-27\facturas\001-001-0000004.pdf`. Ver `evidencias\2026-10-27\LEEME.md`.

### 12/10/2026

**12/10/2026 (verificación de solo lectura, antes de programar §6d puntos 1 y 2)**: Joel pidió
confirmar si el humo, corrido sin ninguna caja abierta, deja un préstamo `EN_MORA` visible en
`/mora` para poder probar estas 2 mejoras hoy. Confirmado leyendo `pruebas/humo_http.sh`
(Bloque 4/E8, líneas 3658-3692): el bloque cierra su propia caja, atrasa las fechas del
préstamo y sus cuotas por SQL (2 cuotas vencidas), dispara el recálculo con
`GET /cobranza/{id}` (sin la guarda de "una vez por día") y verifica por SQL que el préstamo
quedó `EN_MORA` — **sí**, deja un caso real y visible. Con esa confirmación, se programaron los
2 puntos (ver §7/§8).

**12/10/2026, prueba en pantalla de Joel (oficial, CC-2026-00027, 36 días de atraso)**: lista de
mora OK; gestión con resultado, fecha de promesa y monto (20/10/2026 — 500.000 Gs.) se guarda y
se ve bien bajo "Última gestión". **Una falla**: con fecha de promesa y sin monto, el control de
pantalla no frenó el envío — el formulario llegó al servidor, que rechazó correctamente pero el
cartel salió como error (X) en vez de advertencia ("!"), y el modal se cerró perdiendo los datos
tecleados. Corregido (ver §8): el control ahora se ata directo a cada formulario (no por
delegación en `document`) y el rechazo del servidor usa `MontoPrometidoFaltanteException` para
salir como advertencia.

**12/10/2026, reprueba de Joel (commit `7163338`, tag `rf24-probado`)**: el punto 2 (advertencia
"!" del servidor, con "Entendido") ya sale bien. El punto 1 (control de pantalla antes de
enviar) **sigue sin actuar**, incluso con el manejador atado directo a cada `<form>` — mismo
síntoma: el formulario se envía, el modal se cierra y se pierden los datos. Investigación de
solo lectura (máximo 15 minutos, sin tocar nada, ver §10): no se pudo confirmar la causa exacta
con evidencia concluyente dentro del plazo. Queda anotado como mejora pendiente.

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`:
- "Resultado" obligatorio: registrar una gestión sin escribir nada en "Resultado" → "Falta
  completar «Resultado»". **OK.**
- Doble clic en "Guardar" (CC-2026-00027): una sola gestión nueva registrada ("Prueba doble clic
  2", 07:58) — el token de un solo uso (`TokenUnaVez`) funcionó. (Las 2 gestiones idénticas de
  07:40, "Prueba: cliente no atendió", son del intento ANTERIOR a esta corrección — quedan sin
  borrar, RNF12, ver §10.)
- Enlace del número de operación en `/mora` → `/reportes/estado-cuenta/27` (antes `/cobranza/27`,
  donde el oficial tenía "Acceso restringido"). **OK.**

## 10. Pendientes y trabajo futuro

- **Prueba en pantalla completa contra el texto firmado**: depende de fechas reales (§6c) —
  decisión de Joel, al final, igual que RF20/RF21/RF22.
- **Prueba en pantalla de las 2 mejoras de §6d**: correr el humo sin caja abierta (deja el
  préstamo `EN_MORA` de prueba, ver §9) y revisar la columna "Última gestión" y el aviso del
  monto en el modal.
- ~~**(12/10/2026) Idempotencia de `POST /mora/{id}/gestion`**: a diferencia de las operaciones
  de dinero (cobro, desembolso, reversión), este POST no tiene `identificadorConfirmacion` — un
  doble clic podría registrar la misma gestión dos veces.~~ — **confirmado el 14/10/2026 (Joel,
  Tanda 2): un doble clic rápido en "Guardar" sí registró 2 gestiones idénticas** en
  CC-2026-00027 (14/10/2026 07:40, "Prueba: cliente no atendió"), visibles en
  `/reportes/estado-cuenta/27`. **Resuelto sin tocar la base**: `TokenUnaVez`
  (`infraestructura/TokenUnaVez.java`) — un token de un solo uso en la sesión HTTP, generado por
  fila al mostrar `/mora` y consumido por `ControladorMoraServlet.procesarGestion`; un segundo
  POST con el mismo token no vuelve a registrar, y muestra la misma confirmación ("Gestión
  registrada."), mismo criterio que `identificador_confirmacion` pero sin columna nueva. **Las 2
  gestiones duplicadas de la prueba NO se borraron** (RNF12, nunca se borra nada) — quedan como
  dato de prueba, se limpian junto con el resto en E10.
- ~~**(16/10/2026, hallazgo de Joel) `TokenUnaVez` en gestión de mora: si el token es inválido
  (ej. dos pestañas abiertas) muestra "Gestión registrada." sin guardar.**~~ — **resuelto
  (19/10/2026, tanda de usabilidad)**: `ControladorMoraServlet.procesarGestion` ya no asume
  éxito cuando `TokenUnaVez.consumir` da `false` — ahora muestra una advertencia honesta que
  sirve para los 3 casos indistinguibles (doble clic real, token inválido, token vencido): "No
  se registró una gestión nueva. Si ya la había enviado, aparece en la columna Última gestión;
  si no, actualice la página e intente de nuevo." (`guardarAdvertencia`, "Entendido"). Cambio
  mínimo (REGLAS §13): no se agregó un segundo registro de tokens para distinguir los 3 casos,
  se prefirió un mensaje que es verdad en todos. Cubierto por un caso nuevo de
  `pruebas/humo_http.sh` (Bloque 4): `POST /mora/{id}/gestion` con un `tokenGestion` inventado
  → confirma que ya NO aparece "Gestión registrada." y que no se crea ninguna fila nueva en
  `gestiones_cobranza`.
- ~~**(13/10/2026, hallazgo de Joel) El "Resultado" debería ser obligatorio al registrar una
  gestión**~~ — **resuelto el 14/10/2026 (Tanda 2)**: `ServicioMora.registrarGestion` rechaza
  `resultado` vacío con `ResultadoGestionFaltanteException` ("Ingrese el resultado de la
  gestión."), mostrada como advertencia ("!"). Probado en pantalla por Joel: "Falta completar
  «Resultado»". OK.
- ~~**(12/10/2026) Control de pantalla "Falta un dato" en `/mora` sigue sin frenar el envío**~~
  — **causa confirmada el 14/10/2026** (Tanda 2, al investigar por qué la protección de doble
  clic del navegador tampoco frenaba): cada modal de gestión
  (`<div class="modal fade" id="modalGestion...">`, con su `<form>` adentro) sigue ubicado
  directamente dentro de `<tbody>`, entre `<tr>` y `<tr>` — HTML inválido que el navegador
  corrige con "foster parenting", reubicando ese `<div>` entero fuera de la tabla. En ese
  reacomodo, el `<form>` y su `<button>` dejan de ser garantizadamente padre/hijo en el DOM real
  (la especificación HTML5 vuelve a aplicar foster parenting a CADA elemento nuevo mientras el
  `<table>` siga abierto en la pila del parser, aplanando el árbol completo del modal como
  hermanos sueltos antes de la tabla) — por eso `this.querySelector('button[type=submit]')`
  (agregado en la Tanda 2 anterior para deshabilitar el botón) podía no encontrar el botón y
  lanzar una excepción silenciosa, dejando el botón habilitado para el segundo clic. **No se
  corrigió la estructura HTML** (cambiar el modal de posición es un cambio más grande, fuera de
  "cambio mínimo") — la protección real ya no depende de esto: pasó al servidor (`TokenUnaVez`,
  punto de arriba). El deshabilitado del botón sigue ahí como ayuda visual, sin ser la
  protección real.
- ~~**(14/10/2026, Tanda 3 — anotar, no implementar todavía)**: el HTML inválido de arriba (el
  modal como hijo directo de `<tbody>`) ya no causa daño real gracias al token del servidor,
  pero sigue siendo inválido y conviene corregirlo — mover cada
  `<div class="modal fade" id="modalGestion...">` dentro de la celda de su propia fila
  (`<tr><td colspan="...">`, oculto, nunca entre `<tr>` y `<tr>`) o, más simple, sacar los
  modales FUERA de la `<table>` por completo (un solo bloque después de `</table>`, cada uno con
  su `id="modalGestion<%= idPrestamo %>"` para que `data-target`/Bootstrap los siga encontrando
  igual). Cambio de estructura, no de lógica — queda para la Tanda 3.~~ — **resuelto (20/10/2026,
  tanda de usabilidad, punto 6 paso 1)**: al separar la tabla + modales en `mora/_resultados.jsp`
  (fragmento nuevo para el buscador AJAX, ver §2/§4), se aprovechó para sacar cada
  `<div class="modal fade" id="modalGestion...">` fuera de `<table>` por completo, en su propio
  bucle después de `</table>` — la opción "más simple" que ya estaba anotada acá. HTML válido
  ahora: ningún `<div>` queda más como hijo directo de `<tbody>`, así que el navegador no tiene
  nada que "corregir" con foster parenting en cada reemplazo (ni en la carga normal ni después de
  un filtrado AJAX).
- **(14/10/2026, hallazgo de Joel) El número de operación llevaba a `/cobranza/{id}`**, donde
  el oficial no tiene permiso ("Acceso restringido") — **corregido**: ahora lleva a
  `/reportes/estado-cuenta/{id}` (`VER_REPORTES` + `SUBREPORTES_OFICIAL`, ya incluye
  "estado-cuenta" desde RF34), que oficial y gerente sí pueden ver.

## 11. Guion para la defensa

1. Mostrar `/mora` vacía ("No hay préstamos en mora en este momento.") — explicar que el
   recálculo diario ya corrió (auditoría `RECALCULO_MORA_DIARIO`).
2. (Con un préstamo en mora real) mostrar la fila completa: días de atraso, monto exigible,
   promesa si hay una vencida en rojo.
3. Registrar una gestión de cada tipo (llamada, mensaje, visita) y una con promesa de pago —
   mostrar el rechazo si se pone fecha sin monto.
4. Mostrar que la cajera no tiene "/mora" en el menú ni puede entrar por URL directa (403).
5. Citar RF20/RF21 para el monto exigible.
