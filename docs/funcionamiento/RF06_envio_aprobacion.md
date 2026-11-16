# RF06 — Enviar la solicitud a aprobación y cancelarla

**Estado: cerrado y probado en pantalla completo el 10/10/2026 (ver §9), tag `rf06-probado`. La
mejora de usabilidad agregada después (glosario financiero con ícono ⓘ, ver §7/§9) también quedó
probada en pantalla el 10/10/2026 — sin tag nuevo, sigue siendo parte del mismo RF06.**

## 1. Qué exige el anteproyecto

**RF06** (`docs/RF_ANTEPROYECTO.md`):

> El sistema debe permitir enviar la solicitud a aprobación solo cuando estén completos los
> requisitos previos, y permitir cancelarla registrando el motivo.

Relacionados, citados porque delimitan el alcance de RF06:

> **RF04**: autorización firmada y consulta al buró (ver `RF04_buro.md`).
>
> **RF05**: crear la solicitud y calcular cuota/tasa/CFT/afectación (ver `RF05_solicitud.md`).
>
> **RF07**: mostrar al gerente las solicitudes pendientes de aprobación en orden de llegada y el
> historial de las solicitudes ya decididas.

## 2. Cómo funciona

Desde la ficha de una solicitud en `BORRADOR` (`solicitudes/ficha.jsp`), el oficial tiene dos
botones: "Enviar a aprobación" y "Cancelar solicitud".

**Enviar a aprobación** (`ServicioSolicitudes.enviarAAprobacion`): revalida todo de nuevo en el
servidor (nunca confía en lo que el checklist de la pantalla mostraba) y, si falta algo, junta
**todos** los puntos faltantes en un solo mensaje con viñetas — la pantalla los muestra como una
lista, no solo el primero. Si todo está completo, la solicitud pasa a `PENDIENTE_APROBACION`, se
vincula la consulta al buró del cliente a esta solicitud, y queda lista
para que el gerente la vea en su bandeja (RF07).

**El checklist de la pantalla mira lo mismo que el servidor** (B122, 10/11/2026). Son **10 ítems**
(`ServicioSolicitudes.construirChecklistPrevioEnvio`): los 9 del cliente —edad, zona, situación
laboral, cédula, comprobante de ingresos, comprobante de domicilio, autorización del buró,
consulta con evidencia, sin deudas en Informconf— **más la capacidad de pago**, calculada con el
ingreso y las deudas de **hoy** por `calcularAfectacionVigente`, que es el **mismo método** que
usa `enviarAAprobacion` para aceptar o rechazar. Hasta el 10/11/2026 ese décimo chequeo no estaba
en el checklist y la ficha lo resolvía por su cuenta comparando la **foto** guardada al simular:
con el ingreso del cliente ya cambiado, la pantalla decía *"Cumple validaciones preliminares"* y
ofrecía el botón mientras el servidor rechazaba el envío. La regla que queda: **lo que la pantalla
promete y lo que el servidor exige salen del mismo método**, y el dato que se muestra en BORRADOR
es el de hoy (ya enviada, la foto, que es la base sobre la que se decidió).

**Cancelar** (`ServicioSolicitudes.cancelar`): pide un motivo en texto libre (sin el cual no se
puede cancelar), marca la solicitud como `CANCELADA` y queda en el historial — nunca se borra.

**Importante, hallazgo de este análisis**: existe un **segundo camino de cancelación**, que no es
RF06 en sentido estricto (vive en el módulo de Aprobación, `ServicioAprobacionCredito.cancelar`,
P05) pero responde a la misma frase del anteproyecto ("permitir cancelarla") para los estados que
`ServicioSolicitudes.cancelar` no cubre: una solicitud `PENDIENTE_APROBACION` o `APROBADA` (todavía
sin formalizar) la cancela el **gerente**, no el oficial. Ver el detalle en el punto 4 del análisis
(§6).

**"Todavía sin formalizar" se verifica de verdad desde el 29/10/2026 (C20).** Antes, esa frase
estaba solo en el mensaje de error: el código miraba **el estado** (`PENDIENTE_APROBACION` o
`APROBADA`) y nunca la columna `formalizada` — y una solicitud formalizada se queda justamente en
`APROBADA`, porque no existe un estado "FORMALIZADA". Resultado: el gerente podía cancelar una
solicitud cuyo préstamo `CC-…` ya estaba creado (y acaso desembolsado y con cobros), porque
`marcarCancelada` solo toca `solicitudes_credito` y no avisa nada. Hoy la regla vive en
`ServicioAprobacionCredito.validarCancelable` (pura, probada con JUnit): exige el estado **y**
`!formalizada`, con el mismo texto de siempre. La ficha del gerente
(`aprobaciones/ficha.jsp`) ya no dibuja el botón en ese caso.

**Para deshacer una formalización** el camino es otro y no cambió: anular el préstamo antes del
desembolso (permiso `ANULAR_PRESTAMO_PRE_DESEMBOLSO`), que anula el **préstamo**. Cancelar la
solicitud nunca fue eso — ver "Cancelar, rechazar y anular no son lo mismo" en
`recorrido/03_aprobacion.md`.

## 3. Auditoría

| Evento | Entidad | Cuándo | Quién lo registra |
|---|---|---|---|
| `ENVIAR_SOLICITUD_APROBACION` | SOLICITUD_CREDITO | Al pasar a `PENDIENTE_APROBACION` | `ServicioSolicitudes.enviarAAprobacion` |
| `CANCELAR_SOLICITUD` | SOLICITUD_CREDITO | Al cancelar desde `BORRADOR` (oficial), con el motivo en el detalle | `ServicioSolicitudes.cancelar` |
| `CANCELAR_SOLICITUD` | SOLICITUD_CREDITO | Al cancelar desde `PENDIENTE_APROBACION`/`APROBADA` (gerente), con el motivo en el detalle | `ServicioAprobacionCredito.cancelar` |

Mismo nombre de evento y misma entidad en los dos caminos de cancelación — se distinguen por el
usuario que lo hizo (`id_usuario`) y por el texto del detalle (uno dice "en borrador", el otro
repite el código de la solicitud).

## 4. Dónde está

- **Servicio (oficial, BORRADOR)**: `servicios/ServicioSolicitudes.java` — `enviarAAprobacion`,
  `cancelar`, `agregarFaltantesExpediente` (privado), `validarMotivoCancelacion` (pura),
  `validarEstadoBorrador` (privado).
- **Servicio (gerente, PENDIENTE_APROBACION/APROBADA)**: `servicios/ServicioAprobacionCredito.java`
  — `cancelar`.
- **Controladores**: `controladores/ControladorSolicitudesServlet.java` (`POST
  /solicitudes/{id}/enviar`, `POST /solicitudes/{id}/cancelar`); `controladores/
  ControladorAprobacionesServlet.java` (`POST /aprobaciones/{id}/cancelar`).
- **Permisos**: `filtros/FiltroAutorizacion.java` — `/solicitudes` → `Permiso.GESTIONAR_SOLICITUD`
  (oficial); `/aprobaciones` → `Permiso.APROBAR_RECHAZAR_SOLICITUD` (gerente). Matriz completa en
  `servicios/ServicioAutorizacion.java`.
- **Pantalla**: `solicitudes/ficha.jsp` (botones "Enviar a aprobación"/"Cancelar solicitud",
  checklist previo al envío, visibles solo mientras `esBorrador`).
- **Tabla** `solicitudes_credito`: columnas `estado_solicitud`, `fecha_envio_aprobacion`,
  `cancelado_por`, `fecha_cancelacion`, `motivo_cancelacion`. Nunca se borra una fila (regla
  general del sistema, sin trigger `BEFORE DELETE` específico porque la aplicación directamente no
  tiene ningún `DELETE` sobre esta tabla).
- **Catálogo** `motivos_rechazo_solicitud`: existe, pero es **solo para el rechazo del gerente**
  (RF10), no para la cancelación — ver el punto 4 del análisis (§6).

## 5. Dependencias

- **RF04**: `enviarAAprobacion` exige una consulta al buró vigente y con evidencia, vía
  `ServicioConsultaCrediticia.obtenerParaVincularSolicitud` (para el cliente) — es el único punto
  donde RF06 vuelve a consultar algo de RF04 en el momento del envío (no usa ningún dato guardado
  de antes).
- **RF05**: `enviarAAprobacion` usa el **snapshot** que dejó la última simulación
  (`porcentaje_afectacion`, `limite_afectacion_aplicado`, ya guardados en la fila) — no vuelve a
  calcular nada con `ServicioMotorFinanciero`. Si el oficial no vuelve a simular después de un
  cambio en los datos del cliente, el envío juzga con el número viejo (ver punto 2 del análisis).
- **RF07**: la bandeja de pendientes del gerente (`listarPendientesAprobacion`) lee exactamente las
  filas que `marcarEnviada` deja en `PENDIENTE_APROBACION`, ordenadas por `fecha_envio_aprobacion`
  (orden de llegada, tal como pide el texto de RF07). El "Historial" de esa misma bandeja
  (`listarDecididas`) incluye además las `CANCELADA`, de los dos caminos de cancelación.
- **RF02**: la política de crédito (edad/zona/situación laboral) y el estado activo/inactivo del
  cliente vienen de la ficha del cliente — ver el punto 1 del análisis para cuándo se revisan y
  cuándo no.

## 6. Análisis

> **Aclaración (17/10/2026, pedido de Joel — esta sección describe el estado ANTES del cambio
> del mismo 10/10/2026, no el actual):** el punto 2 de más abajo ("Datos que cambian... No, para
> ingreso y deudas") refleja el comportamiento previo al análisis. Ese mismo día, en respuesta a
> este análisis (ver §7, "Decisiones"), Joel pidió el cambio y se implementó: desde el commit
> `905aca0` (tag `rf06-probado`), `enviarAAprobacion` SÍ recalcula la afectación con el ingreso y
> la cuota de otras deudas ACTUALES del cliente (hasta el 18/10/2026 también del codeudor, ver
> nota de retiro de codeudor/garante más abajo) — la normalización de personas, grupo Laborales,
> 16/10/2026, solo cambió de dónde se lee ese dato, de `Persona` a `personas_laborales`, no la
> lógica. Leer este análisis junto con §7, nunca esta sección sola. **Nota (18/10/2026):** la
> tabla de la sección siguiente, con sus referencias a codeudor/`credito.exigir_garante`,
> describe el estado anterior al retiro de codeudor/garante (commit `6e7da13`) — ver §10.

### Análisis del 10/10/2026 (antes de tocar código, pedido de Joel)

Pregunta de Joel: revisar RF06 contra el texto firmado, en 7 puntos. Resultado punto por punto.

**1) Requisitos previos: cuáles exige el envío exactamente, si se validan todos en el servidor, y
qué ve el usuario si falta uno.**

Lo que **sí** revalida `enviarAAprobacion`, siempre en el servidor, dentro de la misma transacción
(nunca confía en el checklist de la pantalla):

| Requisito | ¿Se revalida al enviar? | Cómo |
|---|---|---|
| Préstamo no cerrado del cliente (RN-01) | Sí, con rechazo inmediato (no entra a la lista de faltantes) | `existeClienteConPrestamoAbierto` |
| Cédula vigente del cliente | Sí | `agregarFaltantesExpediente` (lee la fecha de vencimiento **actual** de la persona) |
| Comprobante de ingresos del cliente (si lo presenta) | Sí | `agregarFaltantesExpediente` |
| Cédula/comprobante del codeudor (si hay) | Sí, mismos criterios | `agregarFaltantesExpediente`, segunda vez |
| El codeudor (si hay) sigue sin deuda con la empresa | Sí | `asegurarCodeudorElegible` — bloqueo directo, no entra a la lista (no se "completa" cargando algo) |
| Necesita codeudor y no tiene (según `credito.exigir_garante` y el % de afectación **guardado**) | Sí | `determinaSiRequiereCodeudor` sobre el snapshot |
| La capacidad sigue superando el límite con el codeudor ya asignado | Sí | comparación directa sobre el snapshot |
| Consulta al buró vigente y con evidencia (cliente y codeudor) | Sí, **en vivo**, no sobre ningún dato guardado | `obtenerParaVincularSolicitud` |

Lo que **no** se revalida al enviar (aunque Joel los nombró explícitamente como esperados):

- **El 40 % de afectación con los datos actuales del cliente**: se compara contra el número que
  quedó guardado en la última simulación (`porcentaje_afectacion`), no contra un cálculo nuevo con
  el ingreso/deudas de hoy. Ver el punto 2 de este análisis — es la misma observación, aplicada
  acá al momento del envío.
- **Política de crédito (edad, zona, situación laboral)**: solo se valida al **crear** la solicitud
  (`ServicioSolicitudes.crear` → `validarPoliticaCredito`). `enviarAAprobacion` no la vuelve a
  mirar. Si el cliente cumplía al crear la solicitud pero después, por ejemplo, se le corrige la
  fecha de nacimiento o se cambia `credito.edad_maxima`/`departamentos_habilitados` en Parámetros,
  el envío no lo detecta.
- **Cliente activo**: solo se valida al **crear** (`EstadoPersona.INACTIVA` rechaza la creación).
  `enviarAAprobacion` no lo vuelve a mirar. **Caso concreto posible**: el gerente da de baja a un
  cliente que ya tenía una solicitud en `BORRADOR` sin enviar (`ServicioClientes.darDeBaja` solo
  bloquea la baja si el cliente tiene préstamos activos/en mora — nunca mira si tiene una solicitud
  en borrador) — el oficial podría enviar esa solicitud igual, para un cliente ya inactivo, y nada
  en el servidor lo frena.

**Qué ve el usuario si falta algo**: un cartel de error con **todos** los puntos faltantes en una
lista con viñetas (`enviarAAprobacion` junta cada falta en `faltantes` y las arma con "\n- " antes
de lanzar la excepción; `plantilla/pie.jsp` ya tiene una rama específica que detecta el salto de
línea y arma un `<ul>` en el modal, en vez de una sola oración) — esto ya es lo que Joel marcó como
"lo ideal", sin que haga falta cambiar nada.

**2) Datos que cambian: si el envío recalcula con los datos actuales del cliente.**

**No, para ingreso y deudas.** `enviarAAprobacion` no llama a `ServicioMotorFinanciero` ni a
`aplicarSimulacion` — usa `solicitud.getPorcentajeAfectacion()`/`getLimiteAfectacionAplicado()`,
los valores que quedaron guardados la última vez que alguien simuló (al crear, al volver a
simular, o al asignar/reasignar codeudor). Si el ingreso del cliente bajó o sus otras deudas
subieron después de esa última simulación, y nadie vuelve a tocar "Simular" antes de enviar, el
envío se evalúa con un número desactualizado.

**Sí, para cédula y consulta al buró.** Estos dos sí se vuelven a mirar con el dato de HOY:
`agregarFaltantesExpediente` lee la fecha de vencimiento de la cédula tal como está en la ficha de
la persona en ese momento (no una foto vieja), y `obtenerParaVincularSolicitud` hace una consulta
en vivo a las tablas de consentimiento/consulta al buró.

Esto es consistente con el hallazgo ya registrado en `RF05_solicitud.md` (punto 5 de su análisis):
la solicitud guarda una foto de ingreso/deudas, no una referencia viva.

**3) Quién puede enviar y quién puede cancelar, y si se valida en el servidor.**

| Acción | Quién | Validado en servidor | Dónde |
|---|---|---|---|
| Enviar a aprobación | Oficial (y administrador, por su acceso total) | Sí | `/solicitudes` → `GESTIONAR_SOLICITUD` |
| Cancelar desde `BORRADOR` | Oficial (y administrador) | Sí | `/solicitudes` → `GESTIONAR_SOLICITUD` |
| Cancelar desde `PENDIENTE_APROBACION`/`APROBADA` sin formalizar | Gerente (y administrador) | Sí | `/aprobaciones` → `APROBAR_RECHAZAR_SOLICITUD` |

El gerente **no** puede enviar a aprobación (no tiene sentido: es quien aprueba) ni cancelar una
`BORRADOR` (todavía no es "suya"); el oficial **no** puede cancelar una vez que ya está
`PENDIENTE_APROBACION` o `APROBADA` — a partir de ahí, solo el gerente decide.

**4) Cancelar con motivo.**

- **¿Es obligatorio?** Sí, en los dos caminos: `ServicioSolicitudes.validarMotivoCancelacion` y la
  validación equivalente (inline) en `ServicioAprobacionCredito.cancelar` — los dos rechazan con
  "Ingrese el motivo de la cancelación." si viene vacío.
- **¿Lista fija, texto libre, o ambos?** **Texto libre únicamente**, en los dos caminos. Esto es
  distinto del **rechazo** (RF10, `ServicioAprobacionCredito.rechazar`), que sí usa una lista fija
  (`motivos_rechazo_solicitud`, con un motivo "Otro" que exige detalle) — la cancelación nunca usó
  ese catálogo. Lo marco porque Joel preguntó explícitamente por esto y la respuesta no es
  simétrica con el rechazo; no es un error (RF06 solo dice "registrando el motivo", sin exigir una
  lista), pero es una asimetría real en el sistema que puede valer una decisión consciente.
- **¿En qué estados se puede cancelar?** Los tres que Joel nombró, cubiertos por los dos caminos:
  `BORRADOR` (oficial, `ServicioSolicitudes.cancelar`); `PENDIENTE_APROBACION` y `APROBADA`
  todavía sin formalizar (gerente, `ServicioAprobacionCredito.cancelar`). Una vez formalizada (hay
  un préstamo creado a partir de esa solicitud), ya no se puede cancelar por este camino — eso es
  RF14 (anular antes del desembolso), sobre el préstamo, no sobre la solicitud.
- **¿Qué pasa con la solicitud cancelada?** Nunca se borra (ninguna de las dos funciones hace
  `DELETE`; coherente con la regla general del sistema). Queda con `estado_solicitud = CANCELADA`,
  `cancelado_por`, `fecha_cancelacion` y `motivo_cancelacion` guardados, visible en el historial del
  oficial (bandeja de "Solicitudes", filtro por estado) y en el "Historial" de la bandeja del
  gerente (`listarDecididas`).
- **¿El cliente puede crear una nueva después?** Sí. `buscarAbiertaPorCliente` (la consulta que
  bloquea crear una segunda solicitud mientras hay una abierta) busca `BORRADOR`,
  `PENDIENTE_APROBACION`, o `APROBADA` sin formalizar — `CANCELADA` nunca entra en esa lista, así
  que no queda ningún bloqueo después de cancelar.

**5) Después de enviar: ¿se puede modificar? ¿Qué estado toma? ¿Qué ve el gerente?**

- **¿Se puede modificar?** No. `simular` llama a `validarEstadoBorrador`, que
  rechaza con "Solo se puede modificar una solicitud mientras está en borrador." si el estado ya no
  es `BORRADOR` — dos veces protegido (la pantalla ya oculta esos formularios cuando `esBorrador`
  es falso, y el servidor los bloquea igual si se fuerza el `POST`).
- **¿Qué estado toma?** `PENDIENTE_APROBACION` (`marcarEnviada`, con `fecha_envio_aprobacion =
  now()`).
- **¿Qué ve el gerente y qué dice "qué sigue"?** Esto ya es el territorio de RF07/RF08 (sin
  analizar todavía en detalle, se analizarán en su propio turno) — lo que confirmé para esta
  pregunta puntual: `ServicioAprobacionCredito.listarPendientes` (bandeja de
  `ControladorAprobacionesServlet`) lee exactamente las filas que `marcarEnviada` deja en
  `PENDIENTE_APROBACION`, ordenadas por `fecha_envio_aprobacion` (orden de llegada, tal como pide
  el texto de RF07) — el enlace entre RF06 y RF07 es exacto y directo, sin ningún paso intermedio.

**6) Auditoría.** Ver §3.

**7) Dependencias con RF04, RF05 y RF07.** Ver §5.

## 7. Decisiones

- Existe un segundo camino de cancelación, fuera de `ServicioSolicitudes` (en el módulo de
  Aprobación, para `PENDIENTE_APROBACION`/`APROBADA`) — ya estaba decidido e implementado antes de
  este análisis; se documenta acá porque responde a la misma frase del anteproyecto.
- **(10/10/2026, Joel, en respuesta a este análisis) El envío revalida todo con los datos
  actuales, misma regla que el RF05.** Implementado en `ServicioSolicitudes.enviarAAprobacion`:
  - **40 % recalculado con el ingreso y las deudas de HOY** del cliente y del codeudor (si hay),
    no con el snapshot de la última simulación — misma cuota ya simulada (monto/plazo no cambian
    acá), mismo motor (`ServicioMotorFinanciero.calcularCapacidadPago`). Si el envío se acepta, el
    nuevo snapshot (`ingreso_cliente_evaluado`, `ingreso_codeudor_evaluado`,
    `limite_afectacion_aplicado`, `porcentaje_afectacion`) se guarda (`actualizarSimulacion`) para
    que la ficha, el resumen del gerente (RF08) y la revalidación al aprobar (RF09) vean el mismo
    número que de verdad se usó para decidir — no mantuve a propósito la estructura completa de
    "requiere codeudor"/"supera el límite" que ya existía (D-125, `credito.exigir_garante`): solo
    cambié de dónde vienen los números (datos de hoy en vez del snapshot), no esa lógica en sí,
    porque Joel no pidió tocarla y sigue siendo coherente con D-125.
  - **Política de crédito (edad/zona/situación laboral) revalidada**, agregada a la lista de
    faltantes si no se cumple (no es un bloqueo directo: no hay una única acción para "arreglarla"
    desde esta pantalla). Reutiliza `validarPoliticaCredito` (la misma que usa `crear`), capturada
    con `try/catch` para que entre a la lista en vez de cortar de una.
  - **Cliente activo revalidado**: si el gerente dio de baja al cliente después de crearse la
    solicitud, el envío ahora lo rechaza (bloqueo directo, no en la lista — no hay nada que
    "completar": hay que reactivar al cliente). Cierra exactamente el caso concreto que señalé en
    el análisis (§6, punto 1).
  - Sigue sin tocarse: la revalidación al **aprobar** (RF09, `ServicioAprobacionCredito.aprobar`)
    no recalcula desde cero, solo compara el % ya guardado (que ahora, gracias a este cambio, es
    el de la última vez que se envió exitosamente, no el de la simulación original) — Joel no
    pidió este cambio para RF09, queda anotado en Pendientes. *Nota (18/10/2026): las menciones
    de codeudor/`credito.exigir_garante` de este punto quedaron superadas por el retiro de
    codeudor/garante (commit `6e7da13`) — ver §10.*
- **(10/10/2026, Joel) Dar de baja a un cliente con una solicitud abierta (borrador, pendiente, o
  aprobada sin formalizar) se rechaza**, nombrando la solicitud y pidiendo cancelarla primero, con
  un enlace real a esa solicitud — mismo mecanismo que el hallazgo de RF05 ("Ver solicitud..."):
  el controlador hace el chequeo antes de llamar al servicio y arma el enlace con
  `MensajesFlash.guardarError(sesión, mensaje, enlaces)` (nuevo overload, mismo patrón que
  `mensajeExitoImprimibles` pero para un flujo con *redirect* en vez de *forward*, a diferencia de
  RF05 que no lo necesitaba); `ServicioClientes.darDeBaja` repite el mismo chequeo (sin enlace)
  como red de seguridad del servidor si se fuerza el `POST`.
- **(10/10/2026, Joel) Motivo de cancelación: lista fija + detalle, igual que el rechazo (RF10) y
  la baja de clientes.** Nueva lista fija en código (no en tabla, a diferencia de
  `motivos_rechazo_solicitud`: son 4 opciones, no hace falta editarlas desde una pantalla, mismo
  criterio que `ServicioClientes.MOTIVOS_BAJA`) — ver `ServicioSolicitudes.listarMotivosCancelacion`:
  "El cliente desistió", "Datos incorrectos, se carga de nuevo", "Cambió la situación del
  cliente", "Otro" (exige detalle). Aplicado a los dos caminos (`ServicioSolicitudes.cancelar`,
  oficial/borrador; `ServicioAprobacionCredito.cancelar`, gerente/pendiente-aprobada) a través de
  un único método compartido (`construirMotivoCancelacion`), para que no se desincronicen. Las dos
  pantallas de "Cancelar solicitud" pasan de un `<input>` de texto libre a un `<select>` + detalle
  condicional, mismo patrón visual que el rechazo de `aprobaciones/ficha.jsp`.
- **(10/10/2026, Joel, tras la primera prueba en pantalla) Lista completa de faltantes del
  buró, con textos propios del envío.** `ServicioSolicitudes.agregarFaltantesBuro` (nuevo,
  privado) reemplaza el `try/catch` único sobre `obtenerParaVincularSolicitud` por los dos
  chequeos independientes (`tieneAutorizacionVigente`, `tieneConsultaConEvidenciaRegistrada`),
  cada uno con su propio texto ("Falta la autorización de consulta al buró vigente del
  {cliente/codeudor}.", "Falta registrar la consulta al buró del {cliente/codeudor}."). Era un
  bug real, no solo una mejora de texto: con ambos requisitos faltando, antes solo se veía uno.
  *Nota (18/10/2026): el placeholder `{cliente/codeudor}` quedó en "cliente" fijo, sin variante,
  desde el retiro de codeudor/garante (commit `6e7da13`) — ver §10.*
- **(10/10/2026, Joel) "Enviar a aprobación" no pide confirmación si ya no cumple.** El atributo
  `data-confirmar` del formulario ahora es condicional a `cumpleValidaciones` (calculado en la
  propia pantalla) — si no cumple, el formulario se envía directo y el servidor responde con la
  lista completa de faltantes, igual que siempre. El servidor no cambió: solo se evitó la
  pregunta "¿Enviar esta solicitud...?" cuando la respuesta ya se sabía que iba a fallar.
- **(10/10/2026, Joel) "Cumple validaciones preliminares" y el checklist ya no se muestran fuera
  de `BORRADOR`.** Antes ese cartel (y los avisos de codeudor/capacidad) se calculaban y mostraban
  siempre, aunque la solicitud ya estuviera cancelada/aprobada/rechazada/pendiente — con los
  campos ya "congelados" de una solicitud vieja, podían decir "Cumple" en verde sin que eso
  significara nada. Ahora esa sección entera queda dentro de `<% if (esBorrador) %>`; las
  condiciones y los números (cuota, TEA/TNA, CFT, total a pagar, afectación) se ven siempre, sin
  importar el estado. (Los avisos de codeudor en sí se eliminaron con el retiro de
  codeudor/garante, 18/10/2026 — ver §10.)
- **(10/10/2026, Joel) Afectación con un decimal también en la tabla de condiciones**
  (`FormateadorNumeros.comoPorcentajeUnDecimal`, el mismo que ya se usaba en el indicador en vivo
  de RF05) — antes la tabla mostraba toda la precisión del cálculo ("16,39...%") mientras el
  indicador de arriba ya redondeaba a un decimal ("16,4 %"), una inconsistencia visible en la
  misma pantalla. Aplicado también en `aprobaciones/ficha.jsp` (la ficha del gerente tiene la
  misma fila), aunque Joel no la señaló puntualmente, para no dejar la misma inconsistencia ahí.
- **(10/10/2026, Joel) Enlace real en el rechazo de la baja de un cliente.** Mismo síntoma que ya
  había pasado una vez en RF05: el código de la solicitud estaba en el TÍTULO del cartel, que
  SweetAlert2 siempre muestra como texto plano (nunca HTML) — el enlace nunca se veía clickeable.
  El mensaje pasa a dos oraciones ("...tiene una solicitud abierta. Cancélela primero: Ver
  solicitud SOL-...") para que el código caiga del lado del DETALLE, donde sí se aplica el
  reemplazo por un enlace real (mecanismo de RF05/RF06, `mensajeErrorEnlaces`).
- **(10/10/2026, Joel) "Qué sigue" después de enviar**: el cartel de éxito pasa a "Solicitud
  enviada a aprobación. Ahora la revisa el gerente en Aprobación." (antes, sin detalle), y la
  ficha de una solicitud `PENDIENTE_APROBACION` muestra una franja "Enviada por (usuario) el
  (fecha y hora). Espera la decisión del gerente.", igual estilo que las de
  aprobada/rechazada/cancelada. "Quién envió" no tiene columna propia en `solicitudes_credito` (a
  diferencia de `decidido_por`/`cancelado_por`) — se resuelve desde la auditoría
  (`ServicioSolicitudes.obtenerIdUsuarioQueEnvio`, mismo patrón que
  `ServicioConsultaCrediticia.obtenerUltimaGeneracionAutorizacion`), sin tocar el esquema.
- **(10/10/2026, Joel, tras la segunda prueba en pantalla) "Enviada por" sin nombre — bug de
  lectura en `AuditoriaDAO.obtenerUltima`, corregido.** Investigado con una consulta de solo
  lectura en `sgcpc_db_pruebas` (pedido explícito de Joel): la fila de auditoría del envío de
  SOL-2026-0001 tenía `id_usuario=4` (administrador) correctamente guardado; el problema era de
  lectura, no de datos. `resultado.wasNull()` se llamaba después de leer `fecha_hora` y
  `valor_nuevo` (este último, NULL para este tipo de evento), así que devolvía la nulidad de esas
  columnas en vez de la de `id_usuario` — un error clásico de JDBC (`wasNull()` refleja el último
  `getXxx()` llamado, sin importar sobre qué columna se pregunta después). Corregido guardando la
  nulidad de `id_usuario` en una variable apenas se lee esa columna. Como es un bug de lectura, se
  corrige sola para todos los envíos que ya existen en la base, sin tocar ningún dato. Además, se
  agregó el texto de respaldo pedido por Joel ("Enviada el...", sin "por -") para el caso en que
  de verdad no haya ningún usuario asociado a la auditoría.
- **(10/10/2026, Joel, mejora de usabilidad "como parte del RF06", sin tag nuevo hasta probarla)
  Ícono ⓘ con el glosario financiero del RF01, en las dos fichas (oficial y gerente), junto a
  Cuota más alta, TEA, TNA, CFT, Total a pagar y Afectación al ingreso.** Reutiliza
  `infraestructura/GlosarioFinanciero` (los mismos textos de TEA/TNA/CFT ya aprobados para el
  cotizador, sin duplicarlos) y el mismo mecanismo de `plantilla/pie.jsp` (clic en ⓘ abre el
  cartel, "Entendido" lo cierra) — ningún JS nuevo. Se agregaron 3 términos que no existían:
  `CUOTA_MAXIMA`, `TOTAL_A_PAGAR` y `AFECTACION` (este último interpola el límite de afectación
  vigente, igual que TEA interpola el tope del BCP). Para TEA/TNA, las dos fichas necesitaban el
  tope de tasa vigente y la tasa mensual (mismos valores que ya resuelve
  `ControladorCotizadorServlet`) — se agregó la misma resolución en
  `ControladorSolicitudesServlet` y `ControladorAprobacionesServlet`
  (`LimiteTasaUsuraDAO.obtenerVigente`, atributo `topeTeaVigente`). Los textos de "Afectación al
  ingreso" y "Cuota más alta" siguen la guía exacta que dio Joel; el de "Total a pagar" no tenía
  guía (no estaba entre los dos ejemplos de Joel aunque sí entre los 5 íconos pedidos) — se
  redactó igual, mostrado para su revisión antes de la prueba en pantalla.
- **Hallazgo adicional de esta revisión** (pedido explícito: listar, sin cambiar todavía):
  estos términos aparecen sin ⓘ en otras pantallas — `formalizacion/ficha.jsp` y
  `formalizacion/nueva.jsp` ("TEA / TNA"); `reportes/estado_cuenta_ficha.jsp` ("TEA:"). No se
  encontró ninguno en `desembolso/`. Al margen de la lista de 5 que pidió Joel: la tabla del plan
  de cuotas de `formalizacion/ficha.jsp` tiene columnas "Capital"/"Interés"/"IVA" sin ícono, y esos
  tres términos sí están en el glosario desde el RF01 (hoy solo se usan con ícono en el cotizador).
- **(10/10/2026, Joel, probando los ⓘ en pantalla) Los textos nuevos del glosario estaban en
  voseo** ("pagás", "debés", "tenés", "sepas", "tu ingreso") — REGLAS_DESARROLLO.md §12 exige
  usted siempre. Corregidos los 6 textos de TEA, TNA (ya estaba bien, sin cambios), CFT, Cuota más
  alta, Total a pagar y Afectación al ingreso, con las palabras exactas que pasó Joel — un solo
  lugar (`GlosarioFinanciero`), así que la corrección alcanza también al cotizador (pantalla y PDF
  `cotizacion.jasper`, que ya leía el texto de esta misma clase, sin ninguna copia propia) y a las
  dos fichas de RF06, sin tener que tocar nada más. De paso, revisados y corregidos (tenían
  voseo/tuteo desde antes, no pedidos por Joel esta vez pero parte del mismo criterio): `CAPITAL`
  ("Lo que devolvés..." → "Lo que se devuelve...") e `INTERES` ("...por prestarte." → "...por
  prestarle.").
- **Revisión pedida por Joel: todo el sistema, voseo o tuteo** ("te", "tu", "tenés", "podés",
  "sabés"...). Barrido por `grep` en `src/main/webapp` (JSP y JS propios, sin las librerías de
  terceros) y `src/main/java` (literales de mensajes), con varias tandas de patrones (formas
  verbales de vos: tenés/podés/sabés/querés/pagás/debés/mirás...; de tú: tienes/puedes/sabes/
  quieres/debes/pagas...; pronombres con límite de palabra `\btu\b`/`\bte\b`/`\bvos\b`/`\btú\b`/
  `\bcontigo\b`, para no confundir con "estudiante", "virtud", "estas" [demostrativo], etc.).
  **Un solo caso encontrado, fuera del glosario**: `plantilla/menu.jsp`, el tooltip del candado en
  las opciones de menú sin permiso decía "No disponible para **tu** perfil" (dos veces, en
  `opcionMenu`/`opcionMenuSinActiva`) — corregido a "No disponible para **su** perfil".
  `TextoLegalAutorizacionBuro` (la autorización al buró) se revisó también: está en primera
  persona ("autorizo", "declaro", el cliente hablando de sí mismo), no es voseo ni tuteo, no
  necesita cambios.
- **Decisión de diseño, no pedida explícitamente**: elegí una lista fija **en código**, no una
  tabla nueva en la base (a diferencia de `motivos_rechazo_solicitud`). Crear una tabla nueva es
  un cambio de estructura con un proceso formal propio (migración + esquema +
  `comparar_esquemas.sh`, REGLAS_DESARROLLO.md §10), y Joel citó **los dos** patrones existentes
  ("igual que el rechazo del RF10 **y** la baja de clientes") — el segundo ya es una lista fija en
  código. Si Joel prefiere la tabla (editable sin tocar código, como el rechazo), lo registro para
  decidirlo explícitamente antes de migrarlo.

### Decisiones del 17/10/2026 (tanda "reglas de decisión del crédito")

- **RN-18 (mora en Informconf) pasa de confirmación reforzada a bloqueo automático.** Antes:
  `aprobar` permitía aprobar igual con mora, solo pedía "¿Aprobar igualmente?"; el envío
  (`enviarAAprobacion`) no revisaba mora en absoluto. Ahora: `enviarAAprobacion` bloquea
  directamente (antes de armar la lista de faltantes) si la consulta vigente del cliente o del
  codeudor indica mora, con el mensaje "El cliente/codeudor figura con deudas en Informconf. No
  se puede otorgar el crédito." (desde el retiro de codeudor/garante, 18/10/2026, el mensaje
  quedó fijo en "El cliente..." — ver §10); `ServicioAprobacionCredito.aprobar` revincula la consulta más
  reciente de cada persona (igual que ya hacía para la auditoría de RF08, solo que ahora ANTES de
  decidir, no después) y bloquea si indica mora, con "El cliente figura con deudas en Informconf.
  No se puede aprobar: rechace la solicitud con el motivo \"Informconf: figura con deudas\"." El
  motivo de rechazo `INFORMCONF` sigue activo en `motivos_rechazo_solicitud` — es la salida que
  le queda al gerente cuando el bloqueo aparece. Riesgo alto (D/E) SIN mora no cambió: sigue
  siendo juicio del gerente con la misma confirmación reforzada de siempre.
  `aprobaciones/ficha.jsp`: con mora, el botón "Aprobar" queda deshabilitado (con el motivo en un
  `title`) en vez de mostrar el formulario con "¿Aprobar igualmente?"; el aviso de arriba pasa de
  "Revise antes de decidir" a "No se puede aprobar esta solicitud — rechácela con el motivo...".
- **"Al enviar" ahora incluye explícitamente política de crédito y cliente activo** en la tabla
  de la sección 6 — ya estaban implementados desde el 10/10/2026 (ver la aclaración al principio
  de la sección 6), este documento solo no los había trasladado de la lista de "no se revalida"
  a la de "sí se revalida".
- Ver el detalle completo de esta tanda (incluida la propuesta aprobada por Joel) en el análisis
  del 17/10/2026 registrado en `AVANCE.md` y en `docs/funcionamiento/FLUJO_OPERATIVO.md` ("Reglas
  de decisión del crédito").

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 17/10/2026 | Tanda "reglas de decisión del crédito": RN-18 (mora en Informconf) pasa de confirmación reforzada a bloqueo automático al enviar y al aprobar; aclaración de que §6 describe el estado previo al cambio del 10/10/2026. Incluye el fix del Departamento bloqueado (hallazgo de la prueba en pantalla, ver `FLUJO_OPERATIVO.md`). Probado en pantalla por Joel con Jesús Acosta (SOL-2026-0001). `mvn test` 775/775; humo 913/922 (los 9 de codeudor, preexistentes). | ver tag | `reglas-decision-probado` |
| 10/10/2026 | RF06, mejora de usabilidad, cierre: corregido el voseo de los textos nuevos del glosario (TEA/CFT/Cuota más alta/Total a pagar/Afectación al ingreso, con las palabras exactas de Joel; TNA ya estaba bien) y de CAPITAL/INTERES (voseo/tuteo previo); un solo lugar, alcanza también al cotizador y su PDF. Barrido de voseo/tuteo en todo el sistema: un caso más encontrado y corregido (`plantilla/menu.jsp`, "tu perfil" → "su perfil"). Probado en pantalla por Joel: ⓘ en los 5 términos, en usted, en las dos fichas. `mvn test` en verde. | `c996489` | — |
| 10/10/2026 | RF06, mejora de usabilidad (sin tag nuevo hasta probarla): ícono ⓘ con el glosario financiero (Cuota más alta, TEA, TNA, CFT, Total a pagar, Afectación al ingreso) en las fichas de solicitud y aprobación; 3 términos nuevos en `GlosarioFinanciero`; hallazgo de los mismos términos sin ⓘ en formalización/informes, listado sin cambiar. `mvn test` en verde (619 pruebas). | (sin commit propio, ver más arriba) | — |
| 10/10/2026 | RF06, cierre: corregido `AuditoriaDAO.obtenerUltima` (bug de `wasNull()`, confirmado con Joel en pantalla); revisados los 47 usos de `wasNull()` en los 19 DAO del proyecto, sin otro caso. `mvn test` en verde (618 pruebas). | `905aca0` | `rf06-probado` |
| 10/10/2026 | RF06, ajustes tras la prueba en pantalla: lista completa de faltantes del buró (antes se cortaba en el primero, con un texto pensado para otra pantalla); "Enviar a aprobación" sin confirmación cuando ya no cumple; "Cumple validaciones preliminares" y el checklist ocultos fuera de BORRADOR; afectación con un decimal también en la tabla; enlace real en el rechazo de baja; cartel y franja de "qué sigue" al enviar. `mvn test` en verde (618 pruebas). | (sin commit propio, ver más arriba) | — |
| 10/10/2026 | RF06: envío revalida afectación/política/cliente activo con datos actuales; baja de cliente rechazada si tiene solicitud abierta (con enlace); motivo de cancelación con lista fija + detalle en los dos caminos. `mvn test` en verde (618 pruebas). | (sin commit propio, ver más arriba) | — |
| 10/10/2026 | Primer documento del RF06: análisis completo contra el texto firmado, sin cambios de código | e1c014e | — |
| 10/11/2026 | **B122**: el checklist previo al envío pasa de 9 a **10 ítems** — se suma la capacidad de pago, con el ingreso de hoy y el mismo `calcularAfectacionVigente` que usa el rechazo de `enviarAAprobacion` (un solo dueño del número). La ficha en BORRADOR muestra esa afectación y ese límite, no la foto de la simulación, y deja de agregar el faltante por su cuenta (lo trae el checklist). Nació de un caso real: ingreso editado después de simular, pantalla en 2,6 % y servidor en 154,6 %. `mvn test` 925/925; probado en pantalla por Joel en SOL-2026-0197 | (este commit) | — |

| 29/10/2026 | **B89**: el cartel *"No cumple validaciones preliminares"* **nombra lo que falta** ("Falta: …" / "Faltan: …"), armado con los mismos ítems de `construirChecklistPrevioEnvio` que ya se listan con ✓/✗ y con el texto de capacidad de `enviarAAprobacion` — ningún chequeo nuevo, un solo archivo (`solicitudes/ficha.jsp`). En la misma tanda, el texto del bloqueo de **RN-18** pasó a la constante `ServicioSolicitudes.MENSAJE_MORA_INFORMCONF`, compartida con el flujo guiado de la ficha del cliente (B88), para que el oficial lea lo mismo antes de intentar enviar y al intentarlo; y el enlace *"Ver ficha completa / documentos"* abre la ficha en la pestaña del primer paso pendiente. **Probado en pantalla el mismo día** (§9). `mvn test` 827/827 | *(este commit)* | — |

| 29/10/2026 | **C20**: cancelar una solicitud **ya formalizada** queda bloqueado. La condición pasó al método puro `ServicioAprobacionCredito.validarCancelable` (estado **y** `!formalizada`) y la ficha del gerente dejó de ofrecer el botón; el texto del rechazo no cambió, ya prometía esa regla. Revisadas las otras dos puertas de `/cancelar`: sanas. **Bug demostrado y arreglo probado en pantalla el mismo día** (§9). `mvn test` 831/831 | *(este commit)* | — |

## 9. Pruebas realizadas en pantalla

**10/10/2026, Joel** — primera ronda, confirmó que funciona:

- Dar de baja a un cliente con una solicitud abierta se rechaza.
- El envío con requisitos faltantes se rechaza.
- Cancelar exige motivo, y "Otro" exige detalle.
- La franja de cancelada muestra quién, cuándo y el motivo.
- Después de cancelar, el cliente puede crear una nueva.
- El envío correcto (SOL-2026-0001, Mirtha) pasa a "Pendiente de aprobación" con 17,4 %.

De esa ronda surgieron 7 ajustes (ver §7 e Historial) — aplicados, `mvn test` en verde. Dos eran
bugs reales encontrados en esta vuelta, no solo preferencias de pantalla:

1. **Lista incompleta de faltantes del buró** (caso real: Rodrigo, 2 faltantes en el checklist de
   la pantalla pero el cartel del servidor solo mostraba 1, con un texto de otra pantalla). Causa:
   `enviarAAprobacion` delegaba los dos chequeos del buró en
   `ServicioConsultaCrediticia.obtenerParaVincularSolicitud`, que lanza una sola excepción en el
   primer problema que encuentra y nunca llega a mirar el segundo. Corregido con
   `agregarFaltantesBuro` (nuevo, privado): revisa `tieneAutorizacionVigente` y
   `tieneConsultaConEvidenciaRegistrada` por separado (los mismos dos chequeos que ya usa el
   checklist de la pantalla), cada uno con su propio texto para el envío.
2. **El enlace de "Ver solicitud..." en el rechazo de la baja de un cliente no funcionaba** (ya
   había pasado una vez en RF05, con el mismo síntoma): el código había quedado en el TÍTULO del
   mensaje, no en el detalle, y SweetAlert2 siempre muestra el título como texto plano. Movido a
   una segunda oración ("Cancélela primero: Ver solicitud SOL-...") para que caiga del lado del
   detalle, donde el reemplazo por un enlace real sí se aplica.

Falta el Clean and Build y la segunda vuelta de prueba en pantalla de Joel para confirmar estos 7
ajustes. Propuesto para esa prueba:

- Repetir el caso de Rodrigo (o uno similar, sin autorización ni consulta al buró) → el cartel
  debe mostrar las dos líneas, con los textos nuevos ("Falta la autorización de consulta al buró
  vigente del cliente.", "Falta registrar la consulta al buró del cliente.").
- Con una solicitud que ya no cumple (ej. el checklist en rojo), tocar "Enviar a aprobación" →
  no debe aparecer el cartel "¿Enviar esta solicitud...?"; debe ir directo al cartel de faltantes.
- Abrir una solicitud `CANCELADA` → no debe verse "Cumple/No cumple validaciones preliminares" ni
  la lista de requisitos; sí deben verse la cuota, la TEA/TNA, el CFT, el total a pagar y la
  afectación.
- Ver la afectación con un decimal tanto en el indicador en vivo como en la tabla de abajo (deben
  coincidir, ej. "16,4 %" en los dos lugares).
- Repetir el rechazo de baja de un cliente con solicitud abierta → el código de la solicitud debe
  verse como un enlace clickeable dentro del detalle del cartel.
- Enviar una solicitud correctamente → el cartel debe decir "Solicitud enviada a aprobación." con
  el detalle "Ahora la revisa el gerente en Aprobación."; la ficha debe mostrar la franja "Enviada
  por (usuario) el (fecha y hora). Espera la decisión del gerente.", igual estilo que la de
  cancelada/aprobada/rechazada.

**10/10/2026, Joel** — segunda ronda, después del Clean and Build, confirmó que funciona:

- La cancelada ya no muestra "Cumple validaciones" ni la lista.
- La afectación sale con un decimal (16,4 % y 17,4 %).
- La pendiente muestra la franja "Espera la decisión del gerente".

Un error encontrado en esa ronda: la franja de SOL-2026-0001 decía "Enviada por - el 10/10/2026
18:45" (sin el nombre), aunque Joel la había enviado él mismo como administrador. Investigado con
una consulta de solo lectura en `sgcpc_db_pruebas` (pedido explícito de Joel, no es `sgcpc_db`):
la fila de auditoría (`id_auditoria=227`) tiene `id_usuario=4` (administrador, Joel Jesús Acosta)
— no nulo — pero `valor_nuevo` sí es `NULL` (este evento se registra con `ServicioAuditoria.
registrar`, que no guarda ningún "valor", a diferencia de `registrarCambioDeValor`). La causa es
un bug clásico de JDBC en `AuditoriaDAO.obtenerUltima`: `resultado.wasNull()` se llamaba después
de leer `fecha_hora` y `valor_nuevo`, así que devolvía si ESOS campos eran nulos (sí, `valor_nuevo`
lo es) en vez de si `id_usuario` lo era (no lo es). Corregido guardando la nulidad de `id_usuario`
en una variable apenas se lee esa columna, antes de tocar cualquier otra. Es una falla de
**lectura**, no de los datos guardados: se corrige sola para todos los envíos que ya existen, sin
tocar ninguna fila. Se agregó además el texto de respaldo que pidió Joel ("Enviada el...", sin
"por -") para el caso en que de verdad no haya ningún usuario asociado.

Falta el Clean and Build y la confirmación de Joel sobre este último fix antes del commit con tag
`rf06-probado`. Propuesto para esa prueba:

- Abrir de nuevo la ficha de SOL-2026-0001 → la franja debe decir "Enviada por Joel Jesús Acosta
  el 10/10/2026 18:45. Espera la decisión del gerente.".
- Enviar una solicitud nueva con cualquier usuario y confirmar que también muestra el nombre
  correcto (no solo el caso ya existente).

**Confirmado por Joel**: "Enviada por Joel Jesús Acosta el 10/10/2026 18:45. Espera la decisión
del gerente." — correcto.

**Revisión adicional pedida por Joel, antes del commit**: buscar el mismo patrón de `wasNull()`
en todos los DAO del proyecto. Revisados los 47 usos de `wasNull()` en los 19 DAO que lo usan
(`AplicacionPagoDAO`, `ArchivoComprobanteDAO`, `AuditoriaDAO`, `ConsentimientoCrediticioDAO`,
`ConsultaCrediticiaDAO`, `DesembolsoDAO`, `DocumentoExpedienteDAO`, `FacturaDAO`,
`GestionCobranzaDAO`, `LiquidacionAnticipadaDAO`, `MovimientoCajaDAO`, `PagoDAO`, `PersonaDAO`,
`PlanPagoDAO`, `PrestamoDAO`, `ReversionPagoDAO`, `SesionCajaDAO`, `SolicitudDAO`, `UsuarioDAO`):
**el único caso con el error era el de `AuditoriaDAO.obtenerUltima`, ya corregido** (ver arriba).
En los otros 45 usos reales (los otros 2 de la cuenta son menciones de "wasNull()" dentro de
comentarios, no llamadas) la variable se lee y se compara con `wasNull()` sin ninguna otra lectura
de columna en el medio — están bien escritos. `ConsultaCrediticiaDAO` ya tenía, desde antes, un
comentario propio explicando exactamente este riesgo (línea ~153), señal de que ya se había tenido
cuidado ahí. No se encontró ningún otro caso, así que no hace falta ninguna prueba nueva (una
prueba real de esto necesitaría un `ResultSet` de JDBC, que no se puede armar sin la base — mismo
motivo por el que los DAO de este proyecto no tienen pruebas unitarias de su mapeo, solo de los
métodos puros que a veces viven adentro, ej. `PrestamoDAOTest`).

**10/10/2026, Joel** — primera vuelta, probó el ícono ⓘ en la solicitud: aparece en los 5 términos
y abre/cierra con "Entendido" como el cotizador. Encontró el voseo de los textos nuevos (ver §7)
— corregido, `mvn test` en verde.

**10/10/2026, Joel** — segunda vuelta, después del Clean and Build: confirmó que los ⓘ del
glosario aparecen tanto en la ficha de la solicitud (oficial) como en la ficha de aprobación
(gerente), con los 5 términos en usted y "Entendido". **RF06 (incluida esta mejora de
usabilidad) queda cerrado y probado por completo.**

No quedó pendiente de probar en esta vuelta: el texto exacto de TEA/TNA con el tope BCP y la tasa
mensual real, el PDF del cotizador, y el tooltip de "su perfil" — Joel no los mencionó
puntualmente al confirmar, quedan para si surge algo al usarlos de nuevo.

**29/10/2026, Joel (oficial Lorenzo Ramírez) — B89, el cartel que nombra lo que falta:**

- **`SOL-2026-0006`** (Prueba de Flujo Jueves 29) con el comprobante de domicilio pendiente:
  *"No cumple validaciones preliminares. **Falta: Comprobante de domicilio cargado.**"* —
  "Enviar a aprobación" siguió bloqueando con el texto del domicilio (el gate del servidor no
  cambió). Cargado el domicilio: **9/9 ✓** y *"Cumple validaciones preliminares"* → enviada.
- **`SOL-2026-0007`** (Ana María Prueba, 2.000.000 a 12 meses) con **varios** faltantes:
  *"No cumple validaciones preliminares. **Faltan:** Autorización de consulta al buró vigente,
  Consulta al buró (Informconf) registrada con evidencia."*

**29/10/2026, Joel — C20, cancelar una solicitud ya formalizada:**

- **Antes del arreglo (13:36), el bug demostrado**: `SOL-2026-0006` aprobada 13:33 por el gerente
  Ramón Benítez y formalizada por el oficial Lorenzo Ramírez como **`CC-2026-00005`**
  (`PENDIENTE_DESEMBOLSO`). En `/aprobaciones/6` **seguía apareciendo "Cancelar solicitud"**.
  **No se apretó** (decisión de Joel: no dejar datos contradictorios en la base).
- **Consulta previa de solo lectura**: **0 filas** con `estado_solicitud = 'CANCELADA'` y
  `formalizada = TRUE` — el bug nunca produjo datos malos, no hubo nada que reparar.
- **(a) Después del arreglo**: `/aprobaciones/6` ya **no** muestra "Cancelar solicitud"; queda
  solo "Volver a la bandeja".
- **(b) Lo que sí debe cancelarse sigue andando**, por la misma puerta y el mismo método:
  `SOL-2026-0005` (`PENDIENTE_APROBACION`) → motivo "Otro" + "prueba C20" → *"¿Cancelar esta
  solicitud? No se puede deshacer."* → *"Solicitud cancelada"* → *"Cancelada por Ramón Benítez el
  29/10/2026 13:48. Motivo: Otro - prueba C20"*, sin botones de acción.
- La puerta del **oficial** (`BORRADOR`) no se probó en pantalla: no se tocó, y la cubre su propia
  guarda `validarEstadoBorrador`.

## 10. Pendientes y trabajo futuro

- **Para cuando se analice RF07** (pedido de Joel, anotado el 10/10/2026, no implementado
  todavía):
  1. En el Panel del gerente y del administrador, una tarjeta "N solicitudes pendientes de
     aprobación → Ir a Aprobaciones".
  2. El checklist de la ficha del gerente (`aprobaciones/ficha.jsp`, `checklistCliente`) también
     filtrado por estado — mismo problema ya corregido en RF06 (§7, punto 3) para la ficha del
     oficial, pendiente de aplicar ahí.

- Confirmar si Joel prefiere la lista de motivos de cancelación en una tabla editable (como el
  rechazo) en vez de en código — ver la decisión de diseño anotada en §7.
- Si se quiere que RF09 (aprobar) también recalcule la afectación desde cero en vez de comparar el
  % ya guardado, es un cambio a decidir aparte (no pedido para esta tanda).
- `pruebas/humo_http.sh` no tiene hoy ningún caso para `/solicitudes/{id}/cancelar` ni
  `/aprobaciones/{id}/cancelar` con el parámetro viejo `motivo` (se revisó: no hay ninguna llamada
  a esas dos rutas en el script), así que este cambio no rompe ningún caso existente del humo.
- Agregar el ícono ⓘ en `formalizacion/ficha.jsp`/`formalizacion/nueva.jsp` (TEA/TNA) y
  `reportes/estado_cuenta_ficha.jsp` (TEA) — y, si Joel lo pide, en las columnas
  Capital/Interés/IVA del plan de cuotas de `formalizacion/ficha.jsp` — listado en §7, no
  implementado todavía (pedido explícito de Joel: "sin cambiarlas todavía").
- **Hueco preexistente destapado al quitar el codeudor** (hallazgo de Joel, 18/10/2026, corregido
  en el mismo commit del refactor): sin comprobante de ingresos, RN-04a exigía un codeudor que,
  con `credito.exigir_garante="nunca"`, nunca se podía asignar — eso era, indirectamente, el único
  bloqueo real al enviar a aprobación. Al quitar el codeudor se perdió, y quedó solo el bloqueo
  del alta (RF02): un cliente ya registrado, editado para sacarle el comprobante (la edición
  nunca bloquea por esto), podía enviar una solicitud sin ningún freno. Se agregó un bloqueo
  directo — `ServicioClientes.motivoSinComprobanteSolicitudSiAplica`, mismo texto en los tres
  momentos de la solicitud (`ServicioSolicitudes.validarPoliticaCredito`, llamado desde `crear` y
  desde `enviarAAprobacion`; `ServicioFormalizacion.revalidarCapacidadPagoFormalizacion`) — ver
  REGLAS_DESARROLLO.md §5 ("solo ingreso fijo comprobable") y RF05_solicitud.md §10.

## 11. Guion para la defensa

RF06 no deja enviar una solicitud a aprobación si falta algo, y revalida todo con los datos de
hoy del cliente: afectación, política de crédito y que siga activo — no alcanza con que haya
cumplido en el momento de crearla. Cuando falta algo, lo muestra todo de una vez (cada requisito
con su propio texto, nunca cortado en el primero), no de a un punto por vez, y sin pedir una
confirmación de "¿enviar?" cuando la pantalla ya sabe que no va a pasar. Una solicitud decidida
(aprobada, rechazada o cancelada) muestra sus condiciones y números, pero ya no las validaciones
preliminares, que dejaron de tener sentido. Cancelar exige un motivo de una lista fija ("El
cliente desistió", "Datos incorrectos", "Cambió la situación", "Otro" con detalle obligatorio),
igual que el rechazo del gerente y la baja de un cliente, en dos momentos distintos según quién
cancela: el oficial mientras es un borrador, el gerente una vez que ya está en aprobación o
aprobada sin formalizar. Dar de baja a un cliente con una solicitud abierta también se bloquea,
con un enlace real a esa solicitud para cancelarla primero. Ninguna cancelación borra nada, y el
cliente puede empezar una solicitud nueva después.
