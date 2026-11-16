# RF09 — Aprobar la solicitud

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf09-probado`.** La
carrera de dos pestañas quedó bloqueada en pantalla (no se pudo decidir dos veces). El hallazgo
de mensaje de la sección 11 (el aviso venía del `validarPendienteAprobacion` genérico, no del
rico con nombre y fecha) quedó **implementado y cubierto con pruebas unitarias** el 11/10/2026
según la propuesta de la sección 14 (`mvn test` en verde) — **la verificación en pantalla queda
pendiente para la próxima solicitud pendiente de aprobación** (ver sección 12), no se pudo
repetir la prueba de dos pestañas en esta vuelta porque no había una solicitud `PENDIENTE_APROBACION`
disponible.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF09 (módulo Aprobación):

> El sistema debe permitir aprobar la solicitud y registrar el usuario que la aprobó y la fecha.

RF09 comparte pantalla y datos con RF07 (bandeja) y RF08 (resumen) — el botón "Aprobar" vive en
la misma ficha que RF08 ya describe completa. Ver sección 5.

## 2. Cómo funciona

1. El gerente (o administrador) abre `/aprobaciones/{id}` desde la bandeja (RF07) y revisa el
   resumen (RF08). Si la solicitud está `PENDIENTE_APROBACION`, ve el botón "Aprobar"
   (`aprobaciones/ficha.jsp`, dentro del bloque `<% if (pendiente) { %>`).
2. Al tocar "Aprobar", un formulario hace `POST /aprobaciones/{id}/aprobar` (con el token CSRF
   de `csrf.jsp`) — antes de enviarse, pide confirmación por SweetAlert2
   (`plantilla/pie.jsp`, mecanismo genérico `data-confirmar`): "¿Aprobar esta solicitud?", o si
   el buró indica mora (o riesgo alto D/E, RF08), un texto reforzado ("El buró indica mora.
   ¿Aprobar igualmente?" / "Informconf indica clasificación de riesgo alto (D/E). ¿Aprobar
   igualmente?").
3. `ControladorAprobacionesServlet.doPost` (líneas 81-83) llama
   `ServicioAprobacionCredito.aprobar(idSolicitud, solicitante.getIdUsuario())` y, si no hay
   error, redirige (PRG) a la misma ficha con un mensaje flash de éxito ("Solicitud aprobada.").
4. `ServicioAprobacionCredito.aprobar` (líneas 180-249), en una sola transacción:
   - Vuelve a leer la solicitud de la base (no confía en nada que haya llegado del formulario
     salvo el id) y exige que siga `PENDIENTE_APROBACION` (`validarPendienteAprobacion`).
   - Revalida el tope de tasa vigente (RN-15) contra la TEA simulada — si el tope bajó desde que
     se simuló, bloquea con un mensaje claro ("Vuelva a simular la solicitud antes de aprobar").
   - Revalida que la afectación siga dentro del límite aplicado (RN-03) — si no, bloquea con el
     mismo criterio.
   - Si todo pasa: marca `APROBADA`, **congela las condiciones solicitadas como aprobadas**
     (RN-07: nunca otro monto/plazo/tasa — `montoAprobado = montoSolicitado`,
     `plazoAprobadoMeses = plazoSolicitadoMeses`, etc.), guarda `decididoPor` e `idLimiteTasaAplicado`.
   - `SolicitudDAO.marcarDecidida` hace el `UPDATE` real (ver sección 3 sobre
     `fecha_decision`).
   - RF08 (11/10/2026): vincula también la consulta al buró más reciente de cada persona si
     cambió desde el envío (`revincularConsultasMasRecientes`), y el detalle de auditoría
     incluye qué consulta se vio — ver `RF08_resumen_solicitud.md` §9, no se repite acá.
   - Si hay mora vinculada (antes o después de la re-vinculación), lo anota en el detalle de
     auditoría, pero **no bloquea** — el gerente ya confirmó por SweetAlert2 en la pantalla.

## 3. Dónde quedan guardados el usuario y la fecha

- **`solicitudes_credito.decidido_por`** (BIGINT, FK a `usuarios`) — el usuario que aprobó (el
  mismo campo lo usa también RF10 para quien rechaza; no hay una columna separada por acción).
- **`solicitudes_credito.fecha_decision`** (TIMESTAMP, `now()` puesto por el propio `UPDATE` de
  `SolicitudDAO.marcarDecidida` — nunca un valor que viaje desde Java, así que no se puede
  falsear la hora desde el formulario).
- **`auditoria_operaciones`**: una fila nueva por cada aprobación, operación
  `APROBAR_SOLICITUD`, entidad `SOLICITUD_CREDITO`, con `id_usuario` (quien aprobó) y
  `fecha_hora` (de la propia tabla de auditoría, también `now()` del servidor) — es un segundo
  registro, independiente de las columnas de `solicitudes_credito`, con el detalle en texto
  libre ("Aprobó la solicitud COD. (con indicador de mora del buró) Consulta(s) vista(s) al
  decidir: [...]").
- **Dónde se muestra**: `aprobaciones/ficha.jsp` y `solicitudes/ficha.jsp` (franja verde
  "Aprobada por NOMBRE el FECHA HORA. Monto aprobado: ..., plazo: ... meses.") leen
  `decididoPor`/`fechaDecision` directamente de `solicitudes_credito` — no hace falta ir a la
  auditoría para verlo en pantalla, aunque la auditoría también lo tiene (con más detalle, para
  RF40).

## 4. Qué controles tiene

| Control | Cumple | Dónde / detalle |
|---|---|---|
| Solo gerente o administrador | **Sí** | `FiltroAutorizacion` exige `Permiso.APROBAR_RECHAZAR_SOLICITUD` en `/aprobaciones` — matriz: gerente TOTAL, administrador TOTAL (D7), oficial NINGUNO, cajero NINGUNO (verificado en el análisis de RF07, no se repite el detalle acá). Bloqueo en el servidor, no solo oculta el botón. |
| Solo `PENDIENTE_APROBACION` | **Sí, con una salvedad** | `validarPendienteAprobacion` relee la solicitud de la base dentro de la transacción y lanza si el estado ya cambió. La salvedad: no hay ningún bloqueo a nivel SQL (ver la fila de abajo) — depende de que esta relectura sea la única fuente de verdad. |
| CSRF | **Sí** | Token de sesión (`Csrf`/`FiltroCsrf`), igual que el resto del sistema — pero es por **sesión**, no de un solo uso: dos pestañas de la misma sesión comparten el mismo token válido (ver la fila de doble envío). |
| Doble envío (dos clics) | **Cumple en la práctica, no por diseño explícito** | Un segundo clic normal llega des pués de que el primero ya hizo `commit` (la vuelta HTTP+SweetAlert2+confirmación ya introduce una pausa) — ahí `validarPendienteAprobacion` lo frena ("Esta solicitud ya fue decidida..."). No hay ningún mecanismo que deshabilite el botón ni un token de un solo uso. |
| Dos pestañas (aprobar en una, rechazar en la otra, casi al mismo tiempo) | **Corregido el 11/10/2026, pendiente de prueba en pantalla** | `SolicitudDAO.marcarDecidida` ahora exige `AND estado_solicitud = 'PENDIENTE_APROBACION'` en el `WHERE` y devuelve `boolean` (`executeUpdate() > 0`). Bajo `READ COMMITTED`, si la otra transacción ya comiteó un cambio de estado sobre esta fila, Postgres espera su lock y vuelve a evaluar el `WHERE` contra el valor ya comiteado — si ya no es `PENDIENTE_APROBACION`, no actualiza nada, y `aprobar`/`rechazar` lo detectan y tiran `SolicitudYaDecididaException` ("Esta solicitud ya fue decidida por NOMBRE el FECHA HORA. Actualice la página para ver su estado actual."), sin dejar ni auditoría ni re-vinculación de ninguna consulta de la decisión que no se aplicó. Ver sección 6 para el análisis completo y sección 9 para la decisión. |
| No se puede decidir dos veces (ya decidida, ej. aprobar una RECHAZADA) | **Sí**, por la misma `validarPendienteAprobacion` — con la misma salvedad de la fila de arriba para el caso de dos peticiones realmente simultáneas. |
| Cliente sin otro préstamo abierto (RN-01, "un cliente, un préstamo a la vez") | **Sí, desde el 28/10/2026** (B84) | `ServicioAprobacionCredito.validarSinPrestamoAbierto`, llamado en `aprobar` justo después de `validarPendienteAprobacion` y **antes** de `revincularConsultasMasRecientes` (que escribe): si rechaza, no escribe nada. Reusa `SolicitudDAO.existeClienteConPrestamoAbierto` (RN-01, D-61), sin consulta nueva — bloquean `PENDIENTE_DESEMBOLSO`, `ACTIVO` y `EN_MORA`. Mensaje: "El cliente ya tiene un préstamo no cerrado. No se puede aprobar. Para pedir otro crédito, primero debe terminar de pagarlo o cancelarlo." (error con botón "Entendido"). Es **defensa en profundidad**: por pantalla no se llega a este escenario, porque `ServicioSolicitudes.crear` ya bloquea antes. Ver §9 (decisión), §11 (prueba) y §13 (nota para la defensa). |

## 5. Qué pasa después

- **La aprobada pasa a la bandeja de Formalización** (RF11, `/formalizacion`):
  `SolicitudDAO.listarAprobadasSinFormalizar` — `WHERE estado_solicitud = 'APROBADA' AND NOT
  formalizada`, ordenadas por `fecha_decision`. Protegida por `Permiso.GENERAR_DOCUMENTOS_FORMALIZACION`
  (gerente TOTAL, oficial TOTAL, administrador TOTAL, cajero NINGUNO) — el oficial que la creó
  puede formalizarla, igual que el gerente que la aprobó.
- **Qué ve el oficial de crédito**: en `/solicitudes` (su propia bandeja,
  `Permiso.GESTIONAR_SOLICITUD` = TOTAL solo para oficial y administrador; el gerente no tiene
  acceso a `/solicitudes`, NINGUNO) y en la ficha de esa solicitud
  (`solicitudes/ficha.jsp`) ve la misma franja verde "Aprobada por NOMBRE el FECHA. Monto
  aprobado: ..., plazo: ... meses." que ve el gerente en su propia ficha — mismas columnas,
  mismo dato, sin ninguna diferencia de contenido entre las dos pantallas.
- **El paso siguiente, dentro de la franja verde (B104, 01/11/2026)**: la franja decía quién
  aprobó, cuánto y a qué plazo, y **ahí se terminaba** — mientras el cartel de éxito prometía
  *"Solicitud aprobada. Ahora se formaliza en Formalización."* y dejaba al usuario buscando
  Formalización en el menú. Ahora la franja cierra con una línea y un botón que salen del **paso
  guiado**:
  - si la solicitud todavía **no** se formalizó → *"Ahora se formaliza: se generan el contrato,
    el pagaré y el plan de pagos."* + **"Formalizar esta solicitud →"** (`/formalizacion/nueva/{idSolicitud}`);
  - si **ya** hay préstamo → *"Ya formalizada como CC-2026-00006."* + **"Ver el préstamo
    CC-2026-00006 →"** (`/formalizacion/{idPrestamo}`).

  **Un dato, un dueño**: el texto y el destino los decide
  `ServicioFormalizacion.calcularPasoDeLaSolicitudAprobada` (dueño del tramo, función pura,
  mismo DTO `PasoGuiado` que B88) y el markup vive una sola vez en
  `plantilla/paso_formalizacion.jsp`, que **las dos** fichas incluyen. Los mismos dos destinos
  que ya elegía el historial de la ficha del cliente (`ControladorClientesServlet.construirEnlaceHistorial`),
  ahora con la traducción destino → pantalla en un solo lugar (`PasoGuiado.rutaRelativa`).
  El botón **no agrega ningún permiso**: `/formalizacion` sigue protegido por
  `FiltroAutorizacion` (`GENERAR_DOCUMENTOS_FORMALIZACION`, que gerente y oficial tienen TOTAL,
  así que ninguno de los dos llega a un 403).
- **Cada rol llega a una sola de las dos fichas** (lo que obliga a que el paso esté en las dos):
  el **gerente** solo a `/aprobaciones/{id}` → `aprobaciones/ficha.jsp` (menú *Aprobación →
  Aprobaciones* → Pendientes/Historial → "Ver", desde *Reportes → Informe de solicitudes*, y
  automáticamente al Aprobar/Rechazar/Cancelar, porque el POST redirige a `/aprobaciones/{id}`);
  el **oficial** solo a `/solicitudes/{id}` → `solicitudes/ficha.jsp` (menú *Créditos →
  Solicitudes* → "Ver", sobre su propia bandeja). La **cajera** a ninguna de las dos
  (`GESTIONAR_SOLICITUD` y `APROBAR_RECHAZAR_SOLICITUD` en NINGUNO).
- **Qué ve la cajera**: **nada todavía**. La cajera no tiene acceso ni a `/solicitudes`
  (NINGUNO) ni a `/formalizacion` (NINGUNO) — recién entra en el proceso en `/desembolso`
  (RF15/RF16), después de que el oficial o el gerente formalizan el préstamo (RF11-RF13) y
  queda `PENDIENTE_DESEMBOLSO`. Es el diseño esperado (la cajera maneja efectivo, no decide
  crédito), no un hallazgo.

## 6. Análisis

### Análisis del 11/10/2026 (este documento)

Hecho a pedido de Joel, revisando el texto firmado de RF09 contra el código real
(`ControladorAprobacionesServlet`, `ServicioAprobacionCredito.aprobar`, `SolicitudDAO`,
`aprobaciones/ficha.jsp`), sin cambiar nada. Resultado resumido en la tabla de la sección 7.

El hallazgo más importante es el de la fila "Dos pestañas" de la sección 4: no hay ningún
bloqueo de fila ni condición en el `UPDATE` que impida que dos decisiones distintas (aprobar y
rechazar, por ejemplo) lleguen a "ganar" la carrera según cuál comitee último, sin que quede
ningún rastro de que hubo una carrera (la auditoría registra las DOS operaciones, cada una con
su detalle, pero el estado final de la solicitud es el de la que comiteó después — nadie se
entera de que hubo un conflicto). Es el mismo tipo de ausencia de bloqueo optimista que ya se
ve en `SolicitudDAO.marcarDecidida`/`marcarCancelada` en general — no es exclusivo de RF09, pero
el texto de Joel pidió revisarlo específicamente acá.

## 7. Tabla de cumplimiento

| Parte del texto | Cumple | Dónde / detalle |
|---|---|---|
| Permitir aprobar la solicitud | **Sí** | `ServicioAprobacionCredito.aprobar`, con las revalidaciones de RN-03/RN-15/RN-28 |
| Registrar el usuario que la aprobó | **Sí** | `solicitudes_credito.decidido_por` + `auditoria_operaciones.id_usuario` (evento `APROBAR_SOLICITUD`) |
| Registrar la fecha | **Sí** | `solicitudes_credito.fecha_decision` (`now()` del servidor) + `auditoria_operaciones.fecha_hora` |
| Solo gerente/administrador | **Sí** | `Permiso.APROBAR_RECHAZAR_SOLICITUD`, bloqueado en el servidor |
| Solo solicitudes pendientes | **Sí, con la salvedad de la carrera de dos pestañas** | `validarPendienteAprobacion`, sin bloqueo de fila a nivel SQL |

El texto de RF09 en sí **se cumple sin excepción** — "permitir aprobar y registrar usuario y
fecha" está completo y correcto. La condición de carrera no era parte del texto literal del RF,
era un hallazgo de robustez que Joel pidió revisar explícitamente (punto 3 de su pedido, "que no
se pueda decidir dos veces") — ya corregido, ver sección 9.

## 8. Propuestas (para que Joel decida)

1. **Cerrar la ventana de la carrera**: agregar `AND estado_solicitud = 'PENDIENTE_APROBACION'`
   al `WHERE` del `UPDATE` de `marcarDecidida` (y de `marcarCancelada`, que tiene el mismo
   problema) y comprobar `executeUpdate() == 0` para decidir si de verdad no se actualizó nada
   (en cuyo caso, lanzar el mismo `NegocioException` de "ya fue decidida") — un cambio chico,
   sin tocar el esquema, que cierra la ventana sin usar `SELECT ... FOR UPDATE` (que además
   podría quedar bloqueando otras lecturas mientras dura la transacción).
2. **Mejoras del botón "Aprobar" con mora** (pedido de Joel, anotadas también en
   `RF08_resumen_solicitud.md` §12, mejoras b y c — se repiten acá por si conviene resolverlas
   en el mismo cambio que toque este botón):
   - El texto del botón de confirmación debería decir "Sí, aprobar" en vez de "Sí" genérico.
   - El foco inicial de la confirmación debería quedar en "Cancelar", no en "Sí". **Hallazgo
     nuevo de este análisis**: este mecanismo **ya existe**, genérico, en `plantilla/pie.jsp`
     (líneas 63-75): cualquier formulario con `data-confirmar-foco="cancelar"` hace que
     SweetAlert2 arranque con `focusCancel: true` (ya se usa en cerrar caja, entregar efectivo,
     solicitar/autorizar una reversión). Aplicarlo al formulario de "Aprobar" cuando hay mora
     es un cambio de una sola línea (agregar el atributo al `<form>` en
     `aprobaciones/ficha.jsp`, condicionado a `avisoBuroRevision.isHayMora()`). El texto del
     botón ("Sí, aprobar") es más trabajo: hoy `confirmButtonText` está fijo en `'Sí'` para
     **todos** los formularios `data-confirmar` del sistema — cambiarlo solo para este caso
     necesita un atributo nuevo (ej. `data-confirmar-boton="Sí, aprobar"`, con `'Sí'` como
     valor por defecto si no está) en el mismo mecanismo genérico de `pie.jsp`.
3. **No mostrar "Revise antes de decidir" en una solicitud ya decidida** (mejora d de RF08,
   repetida acá porque toca el mismo bloque): el aviso dentro de la card del buró
   (`aprobaciones/ficha.jsp`) hoy se muestra si `avisoBuroRevision.isHayMora()`, sin mirar el
   estado — condicionarlo también a `pendiente`.

## 9. Decisiones

Decisiones del 11/10/2026 (Joel), sobre las 3 propuestas de la sección 8 — todas implementadas:

1. **Cerrar la ventana de la carrera**: `SolicitudDAO.marcarDecidida` pasó a `UPDATE ... WHERE
   id_solicitud = ? AND estado_solicitud = 'PENDIENTE_APROBACION'`, devuelve `boolean`
   (`executeUpdate() > 0`). Si devuelve `false`, `aprobar`/`rechazar` NO hacen `rollback()`
   manual (dejan que el `catch` genérico de la transacción lo haga al recibir la excepción) —
   releen la solicitud ya actualizada (`SolicitudDAO.obtenerPorId`, misma transacción; bajo
   `READ COMMITTED` ya ve el valor comiteado por la otra, porque `marcarDecidida` esperó su
   lock antes de revisar el `WHERE`) y tiran
   `py.com.capitalcredito.sgcpc.excepciones.SolicitudYaDecididaException` (subtipo de
   `NegocioException`, mismo patrón que `ContrasenaActualIncorrectaException`) con el mensaje
   exacto pedido: "Esta solicitud ya fue decidida por NOMBRE el FECHA HORA. Actualice la página
   para ver su estado actual." — resuelve NOMBRE/FECHA desde `decididoPor`/`fechaDecision` o,
   si la otra ganadora fue una cancelación, desde `canceladoPor`/`fechaCancelacion`.
   `ControladorAprobacionesServlet.doPost` atrapa ese subtipo ANTES del `catch (NegocioException
   ...)` genérico y usa `MensajesFlash.guardarAdvertencia` (ícono y botón "Entendido" de
   advertencia, no de error — nada salió mal del lado del servidor).
2. **Botón "Aprobar": texto propio y foco en Cancelar con mora**: mecanismo genérico nuevo en
   `plantilla/pie.jsp` — `data-confirmar-boton="texto"` en cualquier formulario cambia
   `confirmButtonText` (si el formulario no lo trae, sigue siendo `'Sí'`, sin tocar ningún otro
   `data-confirmar` del sistema). `aprobaciones/ficha.jsp` lo usa con `"Sí, aprobar"` en el
   formulario de Aprobar (siempre, haya o no mora) y agrega `data-confirmar-foco="cancelar"`
   solo cuando `avisoBuroRevision.isHayMora()` (no para riesgo alto D/E sin mora — así lo pidió
   Joel, literal). El formulario de "Confirmar rechazo" usa `data-confirmar-boton="Sí,
   rechazar"` (RF10).
3. **No mostrar "Revise antes de decidir" en una solicitud ya decidida**: el aviso dentro de la
   card del buró ahora se condiciona también a `pendiente` (antes solo a
   `avisoBuroRevision.isHayMora()`) — la franja de arriba y la tabla siguen mostrándose igual en
   cualquier estado, solo este aviso puntual desaparece cuando ya no hay nada que decidir.

**Pruebas**: `ServicioAprobacionCreditoTest` —
`testConstruirMensajeYaDecididaAprobada`/`Rechazada`/`Cancelada`/`SinUsuario` (el mensaje exacto
para cada estado, y el caso sin usuario resuelto). La lógica de la carrera en sí
(`marcarDecidida` devolviendo `false`, la transacción completa) **no se pudo cubrir con JUnit**
— necesita dos conexiones reales corriendo a la vez contra Postgres para forzar la carrera, y
este proyecto no tiene infraestructura de pruebas con base de datos (ver
`RF08_resumen_solicitud.md` §9, mismo límite ya documentado para la re-vinculación). Queda para
la prueba en pantalla (sección 11) — con dos pestañas, aprobando y rechazando casi al mismo
tiempo.

### Decisión del 28/10/2026 (Joel) — B84: revalidar RN-01 al aprobar

**La regla**: "un cliente NO puede tener dos préstamos a la vez; para pedir otro tiene que
cancelar primero el que tiene". Ya se aplicaba en 2 momentos (`ServicioSolicitudes.crear` y
`enviarAAprobacion`, D-61/D-68) y faltaba en los otros dos. Joel decidió el **mismo criterio que
ya se había elegido para el comprobante de ingreso** (18/10/2026): el mismo control en los tres
momentos. Implementado:

1. **`validarSinPrestamoAbierto`** (nuevo, visible en el paquete), llamado desde `aprobar`.
   Reusa `SolicitudDAO.existeClienteConPrestamoAbierto` — **sin consulta nueva**. El booleano
   alcanza acá: una solicitud `PENDIENTE_APROBACION` nunca tiene préstamo todavía, así que
   cualquier préstamo abierto del cliente es por fuerza de otra solicitud (en `ServicioFormalizacion`
   sí hace falta filtrar el propio, ver `RF11_formalizacion.md` §7).
2. **Texto** (decisión de Joel sobre el redactado, 28/10/2026): en usted, con el "qué sigue",
   sin la sigla interna "RN-01" a la vista (punto 11 del Bloque 7/E10) — "El cliente ya tiene un
   préstamo no cerrado. No se puede aprobar. Para pedir otro crédito, primero debe terminar de
   pagarlo o cancelarlo." Sale como error con botón "Entendido" (`MensajesFlash.guardarError` ya
   existente en `ControladorAprobacionesServlet`): **ningún controlador ni JSP se tocó**.
3. **Alcance**: es **defensa en profundidad**, no un agujero que estuviera abierto — por pantalla
   no se puede llegar al escenario (para tener una solicitud en evaluación y a la vez un préstamo
   abierto habría que pasar por `crear`, que ya lo bloquea). Por eso no se pide prueba en pantalla
   de este momento: la cubre `PruebaRN01AprobarFormalizar` (ver §11).
4. **Jerarquía de requisitos**: la regla **no** está respaldada por ningún RF del anteproyecto
   (RF05 habla de la cuota y del 40 %, RF06 de los requisitos previos) — vale como decisión del
   dueño, nivel 2 (`CLAUDE.md`, regla del 18/10/2026). Ver §13.

## 10. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 05/11/2026 | **B43** (B86, tramo transversal): **"Aprobar"** resume cliente y C.I., monto, plazo, cuota más alta y afectación al ingreso (con el límite), y abre con el foco en **"No, volver"**; el monto y el plazo son los **solicitados** porque RN-07 aprueba esos y no otros. **"Rechazar"** dice cliente y **el motivo elegido** (la JSP deja `{motivo}`; el script que ya escuchaba ese `<select>` escribe solo el valor en `data-confirmar-valor-motivo`) y, tras la prueba de Joel, **también abre en "No, volver"**: no mueve dinero ni emite nada, pero no se deshace — `enviarAAprobacion` exige BORRADOR, así que una solicitud RECHAZADA no vuelve a pendiente. El literal **"¿Aprobar igualmente?"** del refuerzo de riesgo alto D/E **se conservó** como título, porque `pruebas/humo_http.sh:2082` lo verifica ausente en el caso sin riesgo. Al cartel de "Aprobar" **no se le agregó un "qué sigue"**: esa frase tiene dueño en Java (`calcularPasoDeLaSolicitudAprobada`, §5) y el servlet solo la expone con la solicitud ya aprobada. Solo presentación: `aprobaciones/ficha.jsp` y el mecanismo compartido de `plantilla/pie.jsp`. **"Aprobar" quedó "no visto"** — las 5 solicitudes pendientes figuran con mora y el formulario ni se renderiza (§11). `mvn test` 907/907 | *(este commit)* | — |
| 01/11/2026 (tanda 2) | **B104**: la franja verde de la solicitud aprobada ofrece el paso siguiente (*"Formalizar esta solicitud →"* / *"Ya formalizada como CC-… → Ver el préstamo"*), en **las dos** fichas y desde un solo dueño — ver §5. Archivos: `ServicioFormalizacion` (`calcularPasoDeLaSolicitudAprobada`, pura), `PasoGuiado` (2 destinos nuevos + `idPrestamo`), `ControladorAprobacionesServlet` y `ControladorSolicitudesServlet` (un atributo cada uno), `plantilla/paso_formalizacion.jsp` (nuevo, el markup una sola vez) incluido por `aprobaciones/ficha.jsp` y `solicitudes/ficha.jsp`. **Primer intento fallido**: el botón se puso solo en la ficha del oficial, y la pantalla donde el gerente aprueba es `/aprobaciones/{id}` — lo encontró Joel en pantalla el 01/11 21:39-21:51. **Probado en pantalla el 03/11/2026** (§11). `mvn test` 899/899 | `74f3b2a` | — |
| 28/10/2026 | **B84**: RN-01 revalidada al aprobar (`validarSinPrestamoAbierto`, reusa `existeClienteConPrestamoAbierto`), con el texto acordado con Joel. 4 JUnit nuevos (`mvn test` 804 en verde) + `PruebaRN01AprobarFormalizar` (2 pruebas con base, rollback total). Probado en pantalla por Joel el mismo día (§11) | *(este commit)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (A.8, Lote 1): `aprobar()` ahora lanza `CondicionesCambiaronException` (en vez de `NegocioException` genérica) cuando el tope de tasa vigente cambió o la capacidad de pago supera el límite al momento de aprobar — mismo criterio que `SolicitudYaDecididaException`: advertencia (ícono y "Entendido"), no error. `ControladorAprobacionesServlet` la captura antes del catch genérico. | *(pendiente)* | — |
| 11/10/2026 | Implementada la propuesta de la sección 14: `validarPendienteAprobacion` ahora lanza `SolicitudYaDecididaException` (con nombre, fecha y la rama "decidida"/"cancelada") en vez del mensaje genérico; `construirErrorYaDecidida` reutiliza el mismo `resolverQuienDecidio`; prueba del texto "cancelada" corregida, `mvn test` 641 en verde | `7046dfa` | `rf11-probado` |
| 11/10/2026 | Probado en pantalla completo: aprobación con "Sí, aprobar" y franja correcta; dos pestañas bloqueadas (con el hallazgo de mensaje de la sección 11) | *(pendiente)* | `rf09-probado` |
| 11/10/2026 | Implementadas las 3 decisiones de la sección 9 (carrera de dos pestañas cerrada, botón "Aprobar" con texto propio y foco en Cancelar con mora, aviso de mora oculto si ya decidida), 4 pruebas nuevas, `mvn test` en verde | *(pendiente)* | — |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

## 11. Pruebas realizadas en pantalla

**05/11/2026 (Joel, perfil gerente) — B43 (la confirmación resume qué va a pasar):**
- **`/aprobaciones/12`** (`SOL-2026-0012`): el cartel de *"Rechazar"* mostró, en renglones,
  *"Cliente: … — C.I. 90.938.379"* y *"Motivo del rechazo: Informconf: figura con deudas"* — el
  motivo que se acababa de elegir en el `<select>`. **OK en el texto**; **se corrigió en el
  acto** que abría con el foco en *"Sí, rechazar"*: ahora abre en **"No, volver"**, verificado de
  nuevo en pantalla.
- **Cartel de control, misma pantalla**: *"¿Cancelar esta solicitud? No se puede deshacer."*
  salió **idéntico** a antes del cambio (una línea, centrado, foco en "Sí, cancelar"), con el CSS
  y el JS nuevos ya cargados. Es la prueba de que esto es **por cartel** y no un cambio global
  del mecanismo compartido.
- **Nunca se confirmó**: al terminar, `SOL-2026-0012` seguía **"Pendiente de aprobación"**.
- **"Aprobar": NO VISTO.** Las **5** solicitudes pendientes son los clientes de prueba del humo y
  **todas figuran con mora**, así que por RN-18 el botón se dibuja deshabilitado y el
  `<form action=".../aprobar">` **ni se renderiza** (verificado en pantalla: 0 formularios).
  Haría falta **una solicitud pendiente de aprobación cuyo cliente no figure con mora en el
  buró**; crearla es armar el escenario para la foto, que la Lista de control B86 prohíbe.

**03/11/2026 06:33-06:38 (Joel, perfil gerente — Ramón) — B104, el paso siguiente en la franja
verde:**
- **`/aprobaciones/7`** (Aprobación → Aprobaciones → Historial → `SOL-2026-0007` → "Ver"): la
  franja verde dijo *"Aprobada por Ramón Benítez el 01/11/2026 12:31. Monto aprobado: 2.000.000
  Gs., plazo: 12 meses."* **+ "Ya formalizada como CC-2026-00006." + el botón "Ver el préstamo
  CC-2026-00006 →"**, que llevó a **`/formalizacion/6`** con los 3 firmados y *"Documentación
  completa: listo para desembolso"*. **OK.**
- **`/solicitudes/7`** (la misma franja en la ficha del oficial): **no se vio en pantalla** —
  usa el mismo fragmento (`plantilla/paso_formalizacion.jsp`) y el mismo atributo que la de
  aprobaciones. Queda así anotado, sin suponer que se probó.
- **La rama *"Formalizar esta solicitud →"*: NO probada en pantalla**, cubierta por JUnit, hasta
  que haya una solicitud aprobada sin formalizar (el 03/11 no quedaba ninguna: `SOL-2026-0007` ya
  se había formalizado como `CC-2026-00006`).
- **Antes, el 01/11/2026 21:39-21:51**, la misma prueba sobre `/aprobaciones/7` mostró la franja
  **sin** la línea ni el botón (ni con Ctrl+F5): el fragmento todavía no existía y el `include`
  estaba solo en `solicitudes/ficha.jsp`. Esa es la falla que corrigió este cambio.

**28/10/2026 (Joel), después del Clean and Build — B84 (RN-01 en los tres momentos):**
- **Formalizar `SOL-2026-0004`** (la prueba de no-regresión del control nuevo): **`CC-2026-00004`
  creado**, `PENDIENTE_DESEMBOLSO`, formalizado 28/10/2026 10:37, con contrato, pagaré y plan
  generados; primer vencimiento elegido 28/11 (sábado) → corrido al lunes **30/11/2026**. El
  control nuevo **no se autobloqueó** con el préstamo que estaba creando (capa 3 del filtro, ver
  `RF11_formalizacion.md` §7). **OK.**
- **Con `CC-2026-00004` ya abierto, nueva solicitud para el mismo cliente** (Joel Acosta,
  3.637.936, 1.000.000 a 6 meses) → **modal rojo con "Entendido"**: "El cliente ya tiene un
  préstamo no cerrado. No puede solicitar otro crédito hasta que finalice." (texto del momento
  "crear", que ya existía desde D-61/D-68). **OK** — la regla completa quedó verificada de punta a
  punta en pantalla.
- **Aprobar y formalizar con un préstamo abierto**: **no alcanzable por pantalla** (ver §9, punto
  3). Cubierto por `PruebaRN01AprobarFormalizar`, que verifica el rechazo de las dos guardas, que
  formalizar no se bloquee a sí misma, el caso normal, y que el criterio sea el de RN-01 sobre los
  **5** estados de `EstadoPrestamo` (bloquean `PENDIENTE_DESEMBOLSO`, `ACTIVO` y `EN_MORA`; no
  bloquean `FINALIZADO` ni `ANULADO_ANTES_DESEMBOLSO`). Rollback total verificado con una conexión
  nueva y, por fuera de la prueba, con `psql`: la base quedó idéntica.

**11/10/2026 (Joel), después del Clean and Build:**
- Aprobación de SOL-2026-0001: cartel de confirmación con el botón "Sí, aprobar" (no "Sí") —
  OK. Franja verde: "Aprobada por Joel Jesús Acosta el 11/10/2026 10:57. Monto aprobado:
  5.000.000 Gs., plazo: 12 meses." — usuario, fecha/hora y condiciones correctas.
- **Dos pestañas**: con SOL-2026-0001 ya aprobada en la primera pestaña, en la segunda (todavía
  con la página vieja, sin recargar) se abrió "Rechazar" → "Confirmar rechazo" → cartel con el
  botón "Sí, rechazar" → al confirmar, **quedó bloqueado** — la solicitud siguió `APROBADA`, el
  rechazo no se aplicó. El control en sí funciona.
- **Hallazgo de mensaje** (menor, no bloqueante): el cartel que apareció al bloquear el rechazo
  **no fue** el de `SolicitudYaDecididaException` — fue el mensaje viejo y genérico de
  `validarPendienteAprobacion` ("Esta solicitud ya fue decidida o no está pendiente de
  aprobación."), mostrado como **error** (ícono X roja), sin nombre, sin fecha, sin "Actualice
  la página". Causa (confirmada, ver el análisis de más abajo): en el escenario real probado
  (dos pestañas, sin recargar, un clic bastante después del otro — no dos peticiones en el
  mismo instante) la segunda petición ya encuentra la solicitud `APROBADA` en el primer
  `obtenerPorId` de `rechazar`, así que `validarPendienteAprobacion` la frena ANTES de llegar al
  `marcarDecidida` condicional — el mensaje nuevo (`SolicitudYaDecididaException`) solo se
  dispara si `validarPendienteAprobacion` llega a pasar y es el propio `UPDATE` el que pierde la
  carrera, una ventana mucho más angosta (milisegundos) que la que de hecho se probó. Análisis y
  propuesta completos más abajo (sección posterior al cierre, sin programar todavía).

## 12. Pendientes y trabajo futuro

- **`CondicionesCambiaronException` (A.8, consolidación RF08-RF19, 11/10/2026)**: implementada,
  sin verificación en pantalla todavía — pendiente repetir el caso (tope de tasa cambiado o
  capacidad de pago excedida al aprobar) y confirmar que el cartel sale con el ícono de
  advertencia y "Entendido", no la X roja de error.
- ~~**Mensaje unificado de "ya decidida"**: verificación en pantalla pendiente~~ — **confirmado
  OK en pantalla el 11/10/2026 (Joel), en la reprueba del Lote 1**: la carrera de dos pestañas
  (RF09/RF10) muestra el mensaje con el ícono de advertencia, quién decidió y cuándo — no el
  error genérico de antes.
- **Otros cambios de estado con el mismo patrón** (pedido de Joel, "SOLO listarlos, no
  tocarlos") — el mismo problema que tenía `marcarDecidida` (un `UPDATE` sin condición sobre el
  estado anterior, sin bloqueo de fila) existe también en:
  - `SolicitudDAO.marcarEnviada` (BORRADOR → PENDIENTE_APROBACION, RF06) — mismo archivo que
    `marcarDecidida`.
  - `SolicitudDAO.marcarCancelada` (→ CANCELADA, RF06) — mismo archivo.
  - `DocumentoExpedienteDAO.marcarReemplazado` (documento → REEMPLAZADO).
  - `FacturaDAO.marcarAnulada` (factura → ANULADA).
  - `LiquidacionAnticipadaDAO.marcarRevertida` (liquidación anticipada → REVERTIDA).
  - `PagoDAO.marcarRevertido` (pago → REVERTIDO).
  - `PersonaDAO.marcarBaja` / `marcarReactivada` (persona → INACTIVA / ACTIVA).
  - ~~`PrestamoDAO.marcarAnuladoAntesDesembolso`, `marcarDesembolsado`~~ — **corregidos el
    11/10/2026** (RF14, pedido de Joel: la carrera anular-vs-desembolsar tenía impacto
    financiero, a diferencia del resto de esta lista) — ver
    [RF14_anular_prestamo.md](RF14_anular_prestamo.md) §6f. `PrestamoDAO.actualizarEstadoMora`,
    `revertirFinalizacion`, `marcarCierreDocumentalCompleto`, `marcarFinalizado` (préstamo,
    otros cambios de estado) siguen sin tocar.
  - `ReversionPagoDAO.marcarAutorizada` / `marcarRechazada` (reversión → AUTORIZADA / RECHAZADA).
  - `SesionCajaDAO.marcarCerrada` (sesión de caja → CERRADA).
  - `UsuarioDAO.actualizarTrasIntentoFallido` / `actualizarEstadoCuenta` (cuenta de usuario).

  Ninguno se tocó en esta vuelta — quedan listados para que Joel decida, caso por caso, si vale
  la pena el mismo tipo de corrección (la mayoría son de un solo usuario operando una sola fila
  a la vez en el uso normal del sistema, así que el riesgo real de carrera varía mucho de uno a
  otro; `marcarDecidida` era especial porque dos PERFILES DISTINTOS con acceso a la MISMA
  pantalla podían decidir la misma solicitud desde dos sesiones).
- ~~Cuando se abra el documento de RF11 (Formalización), confirmar que la fila "Qué pasa
  después" de la sección 5 de este documento sigue siendo exacta~~ — **confirmado** el
  11/10/2026 al escribir [RF11_formalizacion.md](RF11_formalizacion.md): la bandeja, el filtro y
  la matriz de permisos coinciden exactamente con lo descrito acá.

## 13. Guion para la defensa

1. Abrir una solicitud `PENDIENTE_APROBACION` sin mora, tocar "Aprobar", confirmar — mostrar la
   franja verde con el usuario y la fecha/hora, y el monto/plazo aprobados (iguales a los
   solicitados, nunca otros).
2. Repetir con una solicitud con mora — mostrar el texto reforzado de la confirmación, y que
   aprobar igual se puede (no bloquea), con la anotación en la auditoría.
3. Intentar aprobar como oficial o cajero (forzando la URL) — mostrar el bloqueo 403 del
   servidor.
4. Mostrar en `/formalizacion` que la solicitud recién aprobada ya aparece en esa bandeja.
5. Mostrar en `/solicitudes` (como oficial) la misma solicitud con la misma franja verde que ve
   el gerente.
6. Abrir la misma solicitud en dos pestañas, aprobar en una y rechazar en la otra casi al mismo
   tiempo — mostrar que la segunda en confirmarse recibe la advertencia "Esta solicitud ya fue
   decidida por..." (ícono y botón "Entendido" de advertencia) en vez de pisar la primera en
   silencio.
7. Mostrar que el botón de confirmar "Aprobar" dice "Sí, aprobar" (no "Sí"), y que con una
   solicitud con mora el foco inicial del cartel queda en "Cancelar".
8. **RN-01, "un cliente, un préstamo a la vez" (B84, 28/10/2026)**: con un préstamo del cliente
   todavía abierto, intentar una solicitud nueva para ese mismo cliente — mostrar el bloqueo. Y
   explicar que el mismo control está en los **tres** momentos del circuito (enviar a aprobación,
   aprobar y formalizar), no solo al crear.

   > **Nota para la defensa (28/10/2026)**: esta regla es **política de crédito del dueño**, no un
   > requisito del anteproyecto — ningún RF la exige (RF05 habla de la cuota y del 40 %, RF06 de
   > los requisitos previos). Vale como decisión de Joel, que es el nivel 2 de la jerarquía de
   > requisitos; si en la defensa se pregunta de dónde sale, la respuesta es "del dueño del
   > negocio", no "del RF tal". Al citar requisitos obligatorios se cita siempre el anteproyecto.
   > Los dos momentos nuevos (aprobar y formalizar) son **defensa en profundidad**: no se llega a
   > ellos por pantalla, porque el momento de crear ya bloquea antes.

   > Nota (11/10/2026): el punto 6 describe el comportamiento objetivo. Hoy, en el escenario
   > real probado (ver sección 11), el mensaje que aparece es el genérico de
   > `validarPendienteAprobacion`, como error, no la advertencia "Esta solicitud ya fue
   > decidida por...". La sección 14 (más abajo) analiza cómo cerrar esa diferencia — sin
   > programar todavía.

## 14. Análisis posterior al cierre (11/10/2026) — unificar el mensaje de "ya decidida"

**Pedido de Joel, después del commit `bd1962a`: aprobado, implementado y cubierto con pruebas
unitarias el 11/10/2026 (`mvn test` en verde) — verificación en pantalla pendiente para la
próxima solicitud `PENDIENTE_APROBACION` (sección 12).** Texto exacto pedido: que
`validarPendienteAprobacion` use el mismo mensaje
informativo que `SolicitudYaDecididaException` ("Esta solicitud ya fue decidida por [nombre] el
[fecha y hora]. Actualice la página para ver su estado actual."), como advertencia con botón
"Entendido"; si la solicitud fue cancelada y no decidida, que diga "...fue cancelada por... el
...".

### 14.1 Por qué pasa hoy (causa, confirmada en el código)

`aprobar()` y `rechazar()` llaman primero a `validarPendienteAprobacion(solicitud)` — una
validación **pura**, sin `Connection`, que solo mira el estado ya cargado y lanza un
`NegocioException` genérico:

```java
private void validarPendienteAprobacion(SolicitudCredito solicitud) {
    if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE_APROBACION) {
        throw new NegocioException("Esta solicitud ya fue decidida o no está pendiente de aprobación.");
    }
}
```

Más abajo, solo si esa validación pasó, viene el `UPDATE` condicional de `marcarDecidida`; si
ESE pierde la carrera, recién ahí se construye el mensaje rico vía `construirErrorYaDecidida` →
`construirMensajeYaDecidida` (que sí resuelve nombre y fecha, con una consulta a
`ServicioUsuarios`).

En la prueba real (dos pestañas, un clic bastante después del otro, no dos peticiones en el
mismo instante) la solicitud ya está `APROBADA` en la base cuando la segunda petición hace su
propio `obtenerPorId` al principio de `rechazar()` — así que es
`validarPendienteAprobacion` quien la frena, con su mensaje viejo. El camino de
`SolicitudYaDecididaException` solo se dispara en la ventana mucho más angosta (milisegundos)
en que `validarPendienteAprobacion` llega a pasar porque leyó justo antes de que la otra
transacción confirmara, y es el propio `UPDATE` el que pierde la carrera después. Las dos rutas
construyen mensajes distintos para, en la práctica, el mismo problema de fondo.

### 14.2 Por qué `construirMensajeYaDecidida` tampoco distingue "cancelada" hoy

El método ya separa `fecha_decision`/`decidido_por` de `fecha_cancelacion`/`cancelado_por`
según el estado, pero el texto siempre dice "ya fue **decidida** por...", incluso para
`CANCELADA`:

```java
String construirMensajeYaDecidida(SolicitudCredito actual, Usuario quienDecidio) {
    boolean cancelada = actual.getEstadoSolicitud() == EstadoSolicitud.CANCELADA;
    LocalDateTime fecha = cancelada ? actual.getFechaCancelacion() : actual.getFechaDecision();
    ...
    return "Esta solicitud ya fue decidida por " + nombre + " el " + ...
}
```

Esto hay que corregirlo junto con la unificación, no por separado — es el mismo método.

### 14.3 Propuesta

1. **Agregar la rama de redacción en `construirMensajeYaDecidida`** (sin cambiar su firma): si
   `cancelada`, el texto empieza "Esta solicitud ya fue **cancelada** por..."; si no, sigue
   "...ya fue **decidida** por...". El resto (nombre, fecha, "Actualice la página...") queda
   igual para ambos casos.
2. **Extraer la resolución de "quién"** (`cancelada ? getCanceladoPor() : getDecididoPor()` +
   `servicioUsuarios.buscarPorId`) de `construirErrorYaDecidida` a un método privado nuevo,
   `Usuario resolverQuienDecidio(Connection conexion, SolicitudCredito solicitud)`, para no
   duplicarlo en el próximo punto.
3. **Cambiar `validarPendienteAprobacion` para que use el mismo mensaje**: pasa a recibir la
   `Connection` (ya disponible en `aprobar()`/`rechazar()`, que corren dentro de una
   transacción) y, si el estado no es `PENDIENTE_APROBACION`, construye el mensaje con
   `resolverQuienDecidio` + `construirMensajeYaDecidida` (la misma solicitud ya cargada, sin
   necesidad de volver a leerla) y lanza `SolicitudYaDecididaException` en vez de
   `NegocioException`:
   ```java
   private void validarPendienteAprobacion(Connection conexion, SolicitudCredito solicitud) throws SQLException {
       if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE_APROBACION) {
           Usuario quienDecidio = resolverQuienDecidio(conexion, solicitud);
           throw new SolicitudYaDecididaException(construirMensajeYaDecidida(solicitud, quienDecidio));
       }
   }
   ```
   Los dos puntos de llamada (línea ~218 de `aprobar()`, línea ~319 de `rechazar()`) pasan a
   `validarPendienteAprobacion(conexion, solicitud)`.
4. **`construirErrorYaDecidida` se simplifica** para usar el mismo `resolverQuienDecidio` en vez
   de repetir la lógica de `cancelada ? ... : ...` que ya tiene.
5. **Controlador**: sin cambios — `ControladorAprobacionesServlet` ya captura
   `SolicitudYaDecididaException` antes del `catch (NegocioException)` genérico y la muestra con
   `MensajesFlash.guardarAdvertencia` (ícono de advertencia, botón "Entendido"). Al lanzar la
   misma excepción desde los dos puntos, el comportamiento visual queda unificado sin tocar el
   controlador.
6. **Pruebas a ajustar** (si se aprueba): las 4 `testConstruirMensajeYaDecidida*` existentes —
   agregar el caso de la rama "cancelada" con el texto nuevo ("...ya fue cancelada por...");
   agregar una prueba para `validarPendienteAprobacion` (ahora necesita una solicitud no
   pendiente + un `quienDecidio` resuelto — sigue sin poder probar la resolución real del
   `Usuario` por `Connection`, igual que hoy, por la misma limitación de no tener JUnit con base
   de datos).

### 14.4 Qué NO cambia

- El `UPDATE` condicional de `marcarDecidida` y la ventana de carrera que cierra: sin cambios,
  ya está probado.
- El texto para quien SÍ puede decidir (nunca llega a ver este mensaje): sin cambios.
- Ninguna otra pantalla ni servicio: el cambio queda contenido en
  `ServicioAprobacionCredito`/`ControladorAprobacionesServlet` (que ya está preparado) y sus
  pruebas.

**Implementado el 11/10/2026** tal cual los puntos 1-4 de la propuesta (14.3): el controlador no
necesitó cambios (punto 5), y la prueba del texto "cancelada" se corrigió sobre las 4 pruebas ya
existentes (punto 6) en vez de agregar una nueva — ya cubrían exactamente esa rama. **Commit
`rf11-probado` (junto con RF11) — la verificación en pantalla de este mensaje queda pendiente
para la próxima solicitud `PENDIENTE_APROBACION` (sección 12), no se pudo repetir la prueba de
dos pestañas en esta vuelta.**
