# RF29 — Cierre documental (devolución del pagaré)

**Estado: probado en pantalla el 12/10/2026 (tag `rf29-probado`); reprobado de punta a punta el
28/10/2026 con la pantalla rehecha (B85 + B77 + B78).**

> **Nombre**: el anteproyecto (RF29) y el código lo llaman **"cierre documental"**; desde el
> 28/10/2026 **todo lo que ve el usuario** lo llama **"devolución del pagaré"** (B85) — el menú ya
> lo hacía desde antes. Los nombres internos (estado `estado_cierre_documental`, operación de
> auditoría `COMPLETAR_CIERRE_DOCUMENTAL`, el `id="cierre-documental"`, las URL y el informe
> "Cierres documentales") **no cambiaron**: siguen el texto del anteproyecto.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF29 (módulo Cancelación): *"El sistema debe gestionar el cierre
documental del préstamo: generar la constancia de devolución del pagaré, cargar el escaneo
firmado por el cliente y marcar el cierre como completo."*

Regla relacionada: **RN-12** ("Saldo total en cero → préstamo FINALIZADO. La devolución del
pagaré se registra aparte, con constancia firmada.") — el texto de la regla distingue
explícitamente este paso del cierre financiero (RF28): son dos procesos separados, uno
financiero (automático, en el momento de cancelar) y uno documental (manual, posterior, puede
tardar días en completarse mientras se localiza al cliente para que firme y devuelva el pagaré
físico).

## 2. Cómo funciona

El préstamo queda con `estado_cierre_documental = 'PENDIENTE'` al momento de `marcarFinalizado`
(RF28) — aparece entonces en una bandeja propia de `/formalizacion` ("cierre documental
pendiente", `listarFinalizadosConCierrePendiente`). Desde ahí, 3 pasos manuales, cada uno su
propio botón en `/formalizacion/{id}/cierre-documental`. **Desde el 28/10/2026 (B77) los 3 pasos
están siempre visibles**: el que toca lleva la chapita "Paso actual" y los que todavía no se
pueden hacer salen deshabilitados con el motivo escrito — es **solo presentación**, las
condiciones reales siguen siendo las de abajo, validadas en el servidor.

1. **Generar la constancia** (`ServicioFormalizacion.generarConstanciaDevolucion`, idempotente):
   exige que el préstamo esté FINALIZADO; asigna el número `DEV-aaaa-nnnnnn` (si no existe
   todavía) y genera el PDF sistema-generado (`constancia_devolucion.jasper`), guardado como
   `documentos_expediente` tipo `DEVOLUCION_PAGARE_GENERADO` — **mismo patrón que el checklist de
   contrato/pagaré** (`documentos_expediente` + `guardarPdfGenerado`), no el patrón D-94 de
   recibo/factura/constancia de cancelación (columna de archivo dedicada) — decisión explícita de
   Joel documentada en el propio código ("mismo patrón que CONTRATO/PAGARE").
2. **Cargar el escaneo firmado**: mismo endpoint genérico `/documentos/cargar` que ya usa el
   checklist de formalización, con el tipo `DEVOLUCION_PAGARE_FIRMADO`
   (`obtenerIdTipoDevolucionFirmado`).
3. **Completar el cierre** (`completarCierreDocumental`): rechaza con `NegocioException` si el
   escaneo firmado todavía no está cargado ("Cargue el escaneo firmado... antes de completar el
   cierre documental."); si ya está `COMPLETO`, es idempotente (no vuelve a hacer nada). Al
   completar: `PrestamoDAO.marcarCierreDocumentalCompleto` (`estado_cierre_documental =
   'COMPLETO'`, `cierre_documental_por` = quien lo hizo) + auditoría
   `COMPLETAR_CIERRE_DOCUMENTAL`.

La constancia generada incluye (verificado en `generarPdfConstanciaDevolucion`): datos de la
empresa, número de constancia de devolución, fecha, cliente, operación, número de pagaré, monto
desembolsado, fecha de desembolso, fecha y número de la constancia de **cancelación** (si hubo
una — puede no haberla si el préstamo se finalizó con el último pago normal en vez de una
cancelación total, en cuyo caso usa `fecha_finalizacion_financiera`), y quién la emitió. Lleva
**tres bloques de firma** (B78, 28/10/2026): el representante legal por la empresa, el cliente
("Recibí el pagaré original"), y **quien entregó el pagaré** — nombre y C.I. de quien genera la
constancia (`USUARIO_EMISOR`/`USUARIO_EMISOR_CEDULA`). Ojo con el tercero: el nombre se fija en
el **paso 1**, así que si entrega otra persona, el impreso no la refleja — por eso el paso 1
avisa *"La constancia sale a su nombre como quien entrega el pagaré."*

## 3. Auditoría

`ServicioAuditoria.registrar(..., "COMPLETAR_CIERRE_DOCUMENTAL", "PRESTAMO", idPrestamo, ...)` —
solo en el paso 3 (completar). Los pasos 1 y 2 (generar constancia, cargar escaneo) no tienen
auditoría propia — mismo criterio que el resto de `documentos_expediente` (el rastro es el propio
registro, con su `cargado_por`/`fecha_carga`).

## 4. Dónde está

- **Servicio**: `ServicioFormalizacion.generarConstanciaDevolucion`,
  `.obtenerConstanciaDevolucionGenerada`, `.obtenerConstanciaDevolucionFirmada`,
  `.obtenerIdTipoDevolucionFirmado`, `.completarCierreDocumental`,
  `.listarFinalizadosConCierrePendiente`.
- **DAO**: `PrestamoDAO` (`numero_constancia_devolucion`, `estado_cierre_documental`,
  `cierre_documental_por`, `listarFinalizadosConCierrePendiente`, `marcarCierreDocumentalCompleto`
  — columnas ya en `bd/01_esquema.sql` líneas 342-357, sin migración nueva).
- **Controlador**: `ControladorFormalizacionServlet.procesarGenerarConstanciaDevolucion`,
  `.procesarCompletarCierreDocumental`, `.mostrarCierreDocumental` (`GET`/`POST
  /formalizacion/{id}/cierre-documental`).
- **Vista**: `formalizacion/cierre-documental.jsp`; enlace "Devolución del pagaré" en el menú
  (módulo Cancelación → apunta a `/formalizacion#cierre-documental`, con el ajuste V3h de
  `pie.jsp` para que marque el módulo correcto al llegar).
- **Reporte**: `reportes_jasper/constancia_devolucion.jrxml`/`.jasper`.
- **Tipos de documento**: `DEVOLUCION_PAGARE_GENERADO`, `DEVOLUCION_PAGARE_FIRMADO` (contexto
  `PRESTAMO`, ya sembrados, sin `obligatorio_desembolso` — no bloquean nada, son posteriores al
  desembolso).

## 5. Dependencias

- **RF28** (constancia de cancelación + FINALIZADO): precondición dura — `generarConstanciaDevolucion`
  rechaza si el préstamo no está FINALIZADO. No se puede probar RF29 sin haber pasado antes por
  RF26/RF28.
- **RF17** (documentos del expediente / checklist): mismo endpoint genérico de carga
  (`/documentos/cargar`) y mismo patrón de almacenamiento (`documentos_expediente`).
- **Menú** (V3h, pie.jsp): el enlace "Devolución del pagaré" coexiste con "Formalización" en el
  mismo path `/formalizacion`, distinguidos por el fragmento `#cierre-documental` — un defecto en
  ese ajuste afectaría la navegación visual, no la lógica de este RF.

## 6. Análisis

**a) ¿Genera la constancia de devolución del pagaré?** **Sí** — `generarConstanciaDevolucion` es
idempotente (no genera dos veces el número ni el PDF si ya existen) y exige préstamo FINALIZADO,
cumpliendo el orden que exige RN-12 (primero FINALIZADO, después la devolución, "aparte").

**b) ¿Permite cargar el escaneo firmado por el cliente?** **Sí** — reutiliza el mecanismo
genérico de carga de documentos del expediente (RF17), con un tipo de documento propio
(`DEVOLUCION_PAGARE_FIRMADO`), sin necesidad de código nuevo de carga de archivos.

**c) ¿Marca el cierre como completo?** **Sí**, y de forma correcta: `completarCierreDocumental`
**no deja marcar completo sin el escaneo firmado cargado** — es un control real en el servidor,
no solo una validación de pantalla (`NegocioException` explícita). Esto cumple el orden exacto
del texto del RF29 ("generar... cargar... marcar como completo").

**d) ¿Depende de fechas?** **No, de forma significativa.** A diferencia de RF26/RF24 (que
dependen de la fecha de hoy frente a vencimientos), el cierre documental solo depende de que el
préstamo ya esté FINALIZADO (sin importar cuándo) y de que el escaneo se cargue cuando el cliente
efectivamente devuelva el pagaré firmado — puede probarse en pantalla con cualquier préstamo
recién finalizado, el mismo día, sin necesitar datos con fechas especiales. Esto lo distingue de
RF26/RF28 (que sí dependen del momento de cancelación) y simplifica la prueba conjunta: **un solo
préstamo de prueba, cancelado hoy, permite probar RF26 + RF28 + RF29 en una sola sesión**.

**e) ¿Algo miente o confunde?** **Sí, un caso real (13/10/2026, hallazgo de Joel, ya corregido)**:
la constancia de devolución de un préstamo FINALIZADO **sin** "Cancelar todo" (pagó la última
cuota normal, sin CAN) decía *"...por haber sido cancelada en su totalidad la operación
(Constancia de cancelación N.º -)"* — citaba una constancia de cancelación inexistente.
Confirmado con CC-2026-00025 (0 filas en `liquidaciones_anticipadas`, `fecha_finalizacion_
financiera` = la del último pago NORMAL). **Corregido**: `TIENE_CANCELACION` (booleano,
`ServicioFormalizacion.generarPdfConstanciaDevolucion`) distingue los dos textos en
`constancia_devolucion.jrxml`; sin CAN, ahora dice *"...por haber sido pagada en su totalidad la
operación."*, sin mencionar ninguna constancia.

Un matiz más, sin corrección (no es un defecto): el anteproyecto dice "cargar el escaneo firmado por el
cliente" en singular, pero el sistema permite **regenerar** la constancia generada
(`DEVOLUCION_PAGARE_GENERADO`) sin tocar el escaneo firmado ya cargado (son dos tipos de
documento independientes, con su propio vigente) — esto es correcto y deseable (si hubiera un
error de tipeo en la constancia antes de que el cliente la firme, se puede corregir sin perder el
escaneo ya cargado si por algún motivo ya existiera), pero conviene verificarlo en pantalla para
confirmar que no hay confusión visual entre "constancia generada" y "constancia firmada" (dos
estados distintos, mostrados por separado según el código de `mostrarCierreDocumental`).

**f) (13/10/2026, hallazgo de Joel) Interacción con RF25 (reversión)**: con el cierre documental
ya COMPLETO (este RF), el pagaré original ya está en poder del cliente — revertir la cancelación
que dejó el préstamo FINALIZADO dejaría una deuda sin que la empresa tenga el pagaré de vuelta.
Antes de esta corrección, nada lo impedía. Ahora `ServicioReversiones` rechaza solicitar o
autorizar una reversión si `estado_cierre_documental = 'COMPLETO'` — ver `RF25_reversion_cobro.md`
§6g/§2 para el detalle completo (la validación vive en RF25, pero el disparador es el estado que
gestiona este RF). Caso intermedio (DEV generada, cierre `PENDIENTE`): no se bloquea, pero la
bandeja de reversiones avisa al gerente que verifique el pagaré a mano antes de autorizar.

## 7. Decisiones

Ninguna nueva — el diseño (reutilizar el patrón de `documentos_expediente` del checklist en vez
del patrón D-94 de recibo/factura) ya está decidido y documentado en el propio código como pedido
explícito de Joel ("mismo patrón que CONTRATO/PAGARE"), desde la etapa E9/Bloque 5.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en la etapa E9/Bloque 5 (P10). | — | — |
| ~10-11/10/2026 | V3h: corrección de navegación del menú ("Devolución del pagaré" marcaba el módulo equivocado al llegar por `/formalizacion#cierre-documental`). | — | — |
| 12/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. | *(pendiente)* | — |
| 12/10/2026 | Probado en pantalla completo (CC-2026-00026); ajustes chicos aprobados por Joel (logo, texto directo, "N.º", `aria-label` del escaneo, confirmación de cierre). | `115d3af` | `rf29-probado` |
| 13/10/2026 | `TIENE_CANCELACION` en `constancia_devolucion.jrxml` (ver §6e) + bloqueo de reversión con cierre COMPLETO (vive en RF25, ver §6f). `mvn test` 707/0. | *(pendiente, sin prueba en pantalla)* | — |
| 14/11/2026 | **B179**: la confirmación del **paso 3** (*"Marcar el préstamo como cerrado"*) decía solo *"No se puede deshacer."*, al revés que el paso 1, que desde B112 nombra al cliente y la operación. Ahora repite las **mismas expresiones** del paso 1 (mismos getters, mismo `comoCedulaConPuntos`, el mismo `canceladaElCierre`) y cierra con *"El préstamo queda cerrado y la devolución del pagaré, terminada. No se puede deshacer."*. Solo el texto del cartel: `completarCierreDocumental` no se tocó. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 28/10/2026 | **B85 + B77 + B78**: pantalla rehecha (título "Devolución del pagaré — CC-…", explicación, 3 pasos siempre visibles con "Paso actual" y motivos de bloqueo, cierre con "Préstamo cerrado"), vocabulario unificado en toda la ruta del trámite, y **tercer bloque de firma "Entregó el pagaré"** en la constancia. Probado en pantalla el mismo día con `CC-2026-00001` → `DEV-2026-000003`. `mvn test` 804/0. | *(este commit)* | — |

## 9. Pruebas realizadas en pantalla

**12/10/2026, prueba de Joel (oficial), CC-2026-00026, inmediatamente después de RF26/RF28**:
constancia de devolución **DEV-2026-000002** generada, escaneo de prueba cargado como firmado,
cierre documental marcado **COMPLETO**. Todo OK, en el mismo día de la cancelación (confirma
§6d: no depende de fechas especiales).

**13/10/2026, reprueba de Joel, CC-2026-00025 → constancia DEV-2026-000003**: confirmado OK el
logo, el texto directo, el nombre sin mayúsculas, "N.º", el cartel "Falta completar «el escaneo
firmado»" y la confirmación "Sí, completar cierre". **Pendiente**: la corrección de
`TIENE_CANCELACION` (§6e, encontrada en esta misma reprueba) y el bloqueo de reversión con cierre
COMPLETO (§6f) todavía no se probaron en pantalla.

**25/10/2026, primer recorrido completo del RF en la base actual, `CC-2026-00002` →
`DEV-2026-000001`**: los 3 pasos en pantalla (constancia, escaneo firmado, "COMPLETO — 25/10/2026
11:37"). Registro detallado en `recorrido/04_formalizacion.md` § "Tercera pasada". Hallazgos
**B76, B77, B78**; cierra **C38**.

**28/10/2026, prueba de Joel (oficial), `CC-2026-00003` → constancia `DEV-2026-000002`**: el
segundo cierre documental de la base actual, hecho **llegando desde la tarjeta nueva del panel**
(B76, ver `RF30_panel.md` §9) en vez de entrando a `/formalizacion` por el menú. Panel del
oficial **"2 Préstamos con cierre documental pendiente"** → "Ir al cierre documental" → la
sección lista `CC-2026-00003` y `CC-2026-00001` → constancia generada, escaneo firmado cargado,
franja **"COMPLETO — 28/10/2026 09:31 por Lorenzo Ramírez"** → el panel bajó a **1**. Verificado
con `SELECT`: `estado_cierre_documental = 'COMPLETO'`, `fecha_cierre_documental = 2026-10-28
09:31:57`, `numero_constancia_devolucion = 'DEV-2026-000002'`.

**28/10/2026 (tarde), prueba de Joel (oficial Lorenzo Ramírez), `CC-2026-00001` → constancia
`DEV-2026-000003`**: primera pasada con la **pantalla rehecha** (B85 + B77 + B78), de punta a
punta. Panel del oficial **"1 Pagaré pendiente de devolución"** → sección **"Préstamos
finalizados — pagaré pendiente de devolución"** → botón **"Devolución del pagaré"**. Estado
inicial: título **"Devolución del pagaré — CC-2026-00001"**, *"Préstamo cancelado el
27/10/2026"*, la explicación, y los **3 pasos a la vista** con **"Paso actual"** en el 1 y los
otros dos deshabilitados con su motivo. Paso 1 → `DEV-2026-000003`, PDF revisado: **"Lorenzo
Ramírez — C.I. N.º 2.345.678 — Entregó el pagaré"**, 1 página, datos correctos. Paso 2 →
constancia firmada cargada (*"Ver (cargado 28/10/2026)"*), "Paso actual" pasa al 3. Paso 3 →
*"¿Marcar el préstamo como cerrado? No se puede deshacer."* / **"Sí, cerrar el préstamo"** →
*"**Préstamo cerrado.** El pagaré original quedó devuelto al cliente el 28/10/2026 13:03,
registrado por Lorenzo Ramírez…"*, y el paso 3 queda en *"Hecho el … por Lorenzo Ramírez"*.
**Panel de vuelta en 0.**

> **Ojo con los números de constancia**: los `DEV-2026-000002`/`DEV-2026-000003` de las pruebas
> del 12 y 13/10/2026 son de la base **anterior**. En la base actual (`sgcpc_db_pruebas`) la
> numeración `DEVOLUCION` va `DEV-2026-000001` (25/10, `CC-2026-00002`), `DEV-2026-000002`
> (28/10 mañana, `CC-2026-00003`) y `DEV-2026-000003` (28/10 tarde, `CC-2026-00001`). Todos
> quedan en 0 con la limpieza previa a la entrega.

## 10. Pendientes y trabajo futuro

- **(12/10/2026, pedido de Joel, corregido y probado el 13/10/2026)** El input de carga del
  escaneo ya da "Falta completar «el escaneo firmado»" con `aria-label`.
- **(12/10/2026, pedido de Joel, corregido y probado el 13/10/2026)** "Marcar cierre documental
  completo" ya pide confirmación ("Sí, completar cierre").
- **(13/10/2026) Prueba en pantalla pendiente**: `TIENE_CANCELACION` en la constancia DEV (un
  préstamo FINALIZADO sin CAN, ver §6e) y el bloqueo de reversión con cierre COMPLETO (§6f, ver
  también `RF25_reversion_cobro.md` §10).
- **(28/10/2026) PENDIENTE DE VERSE EN PANTALLA — dos textos de B85 corregidos después de la
  prueba**: (1) el **aviso emergente** al cerrar, que todavía decía *"Cierre documental
  completo."* y ahora dice **"Préstamo cerrado."** (`ControladorFormalizacionServlet`), y (2) el
  aviso **"Pagaré pendiente de devolución — Ir a la devolución del pagaré"** de la ficha del
  estado de cuenta (`reportes/estado_cuenta_ficha.jsp`). **No están probados en pantalla**: los
  dos necesitan un préstamo FINALIZADO con el pagaré todavía sin devolver, y después del cierre
  de `CC-2026-00001` no queda ninguno en la base. Se ven solos en la próxima cancelación total
  que se haga — **verificarlos ahí antes de la entrega**. (El aviso emergente sí se puede forzar
  hoy reenviando el formulario del paso 3 de un préstamo ya cerrado: `completarCierreDocumental`
  es idempotente y no escribe nada, ver §2.)
- **(28/10/2026) Pedido de Joel, anotado como B86/B87 en `FASE2_PLAN.md` área B**: mejora general
  de UX/UI de **todo** el sistema (el "antes" que la disparó es el control "Seleccionar archivo"
  del paso 2, el botón pegado debajo y el contenido cargado a la izquierda), y dentro de ella el
  estilo de los botones deshabilitados (los de los pasos 2 y 3 no se leen como deshabilitados).
  No se tocan en este RF: es una pasada de diseño de todo el sistema.
- **(05/11/2026) B87 RESUELTO y PROBADO EN PANTALLA** (primer ítem del tramo transversal de B86;
  el detalle, en `FASE2_PLAN.md` área B). Los botones de los pasos **2** y **3** de esta pantalla
  ya se leen como deshabilitados: gris plano, sin hover, `cursor: not-allowed`. **El arreglo NO
  está en esta pantalla** sino una sola vez en `recursos/css/sgcpc-tema.css` (§2b), y alcanza a
  todo botón deshabilitado del sistema. De este RF solo cambió **la clase** de las dos líneas que
  ya explicaban el motivo (`text-muted` → `motivo-deshabilitado`, `cierre-documental.jsp:98` y
  `:137`): **ningún texto, ninguna etiqueta de botón, ninguna condición**. Lo demás del "antes" que
  disparó B86 (el control "Seleccionar archivo", el botón pegado debajo, el contenido cargado a la
  izquierda) **sigue pendiente** dentro de B86.
- **(28/10/2026, hallazgo de Joel en la prueba de B76) — B85, RESUELTO Y PROBADO EN PANTALLA el
  mismo día** (ver §9 y el historial de §8; se resolvió junto con B77 y B78): el nombre
  **"Cierre documental" no se entiende**. Propuesta de Joel: título **"Devolución del pagaré —
  CC-…"**, una línea de explicación (*"El cliente ya no debe nada: devuélvale el pagaré original
  y deje constancia"*) y los **3 pasos numerados desde el inicio** ("Paso 1 de 3…"). Se trata en
  una sola tanda con **B77** (las 3 cards aparecen de a una) y **B78** (la constancia no dice
  quién entregó físicamente el pagaré) — misma pantalla, mismo trámite. Ver `FASE2_PLAN.md`
  área F. El menú ya llama "Devolución del pagaré" a este acceso, así que el título nuevo
  alinearía la pantalla con el nombre por el que ya se la conoce.
- **(13/10/2026, hallazgo de Joel, RF38)** El defecto de navegación ya anotado en §5 ("Devolución
  del pagaré" coexiste con "Formalización" en el mismo path, el menú marcaba el módulo
  equivocado y la página abría arriba en vez de bajar a la sección) tenía además una causa real
  más allá de la V3h original: el `scrollIntoView` corría antes de que terminaran de cargar las
  fuentes web, demasiado temprano para que el reacomodo tardío de la página no lo corriera de
  nuevo fuera de vista. **Corregido y probado en pantalla el mismo día (D4, ver
  `RF38_perfiles.md` §7/§8/§9, tag `rf38-probado`)**: el mismo `scrollIntoView` ahora corre en
  `window.load`, sin tocar la lógica de este RF; no reabre `rf29-probado`. Confirmado en pantalla
  por Joel: baja sola también la primera vez.

## 11. Guion para la defensa

"RF29 exige gestionar el cierre documental como un paso aparte de la cancelación financiera
(RN-12 lo distingue explícitamente): generar la constancia de devolución del pagaré, cargar el
escaneo que el cliente firmó al devolverlo, y solo entonces marcar el cierre como completo. El
sistema reutiliza el mismo mecanismo de carga de documentos que ya usa el checklist de
formalización (contrato/pagaré), y no permite marcar el cierre completo sin el escaneo firmado
cargado — un control real del servidor, no solo de pantalla. A diferencia de la cancelación, este
paso no depende de fechas de vencimiento, así que se puede probar el mismo día."
