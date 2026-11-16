# RF10 — Rechazar la solicitud

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf10-probado`.** Lista de
motivos reducida a los 4 activos, botón "Confirmar rechazo" con texto propio y carrera de dos
pestañas bloqueada. El mismo hallazgo de mensaje que RF09 (sección 11) ya quedó **implementado
y cubierto con pruebas unitarias** el 11/10/2026 según la propuesta de `RF09_aprobar.md` §14 —
compartida entre aprobar y rechazar, sin repetir el código acá — `mvn test` en verde, commit
`rf11-probado`. **La verificación en pantalla queda pendiente para la próxima solicitud
pendiente de aprobación** (ver sección 12) — no hubo una disponible en esta vuelta para repetir
la prueba de dos pestañas.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF10 (módulo Aprobación):

> El sistema debe permitir rechazar la solicitud eligiendo un motivo de una lista y registrar
> el usuario que la rechazó y la fecha.

RF10 comparte pantalla y datos con RF07 (bandeja) y RF08 (resumen) — el botón "Rechazar" vive
en la misma ficha que RF08 ya describe completa. Ver sección 5.

## 2. Cómo funciona

1. El gerente (o administrador) abre `/aprobaciones/{id}` y, si la solicitud está
   `PENDIENTE_APROBACION`, ve el botón "Rechazar" (`aprobaciones/ficha.jsp`, dentro del mismo
   bloque `<% if (pendiente) { %>` que "Aprobar"). Al tocarlo, despliega un formulario propio
   (no pide confirmación aparte — el formulario en sí ya es la "intención" de rechazar, la
   confirmación es el botón "Confirmar rechazo").
2. El formulario tiene un `<select>` con la **lista de motivos** (ver sección 2.1) y un
   `<textarea>` de detalle, obligatorio solo si el motivo elegido lo exige (hoy, solo "Otro" —
   `data-requiere-detalle`, validado también en JavaScript para habilitar/deshabilitar el
   `required` del campo, y revalidado en el servidor).
3. `POST /aprobaciones/{id}/rechazar` (con el token CSRF) →
   `ControladorAprobacionesServlet.doPost` (líneas 84-90) lee `idMotivoRechazo` y
   `detalleMotivo`, llama `ServicioAprobacionCredito.rechazar(idSolicitud, idMotivoRechazo,
   detalle, idUsuario)` y redirige (PRG) con el mensaje flash.
4. `ServicioAprobacionCredito.rechazar` (líneas 253-290), en una sola transacción:
   - Exige `idMotivoRechazo` no nulo ("Seleccione el motivo del rechazo.") y que el motivo
     exista (`MotivoRechazoSolicitudDAO.buscarPorId`).
   - Si el motivo exige detalle (hoy, "Otro") y no vino, bloquea ("El motivo \"Otro\" requiere
     un detalle.").
   - Arma el texto final: `motivo.getNombre()` solo, o `nombre + " - " + detalle` si hay
     detalle — este texto combinado es el que se guarda tal cual, para siempre (ver sección 3).
   - Relee la solicitud y exige que siga `PENDIENTE_APROBACION` (`validarPendienteAprobacion`,
     la misma validación que usa `aprobar` — ver RF09 §4 para el análisis de esa validación,
     incluida la misma salvedad sobre la ausencia de bloqueo de fila).
   - Marca `RECHAZADA`, guarda `decididoPor`, `idMotivoRechazo` y `motivoRechazo` (el texto
     combinado). **A diferencia de aprobar, no toca ningún campo de condiciones** (nunca hubo
     "monto rechazado"; los campos `montoAprobado`/`plazoAprobadoMeses`/etc. quedan como
     estaban, es decir, `NULL` si la solicitud nunca fue aprobada antes).
   - `SolicitudDAO.marcarDecidida` hace el mismo `UPDATE` que usa `aprobar` (es un solo método
     compartido entre las dos acciones, ver sección 3).
   - RF08 (11/10/2026): igual que al aprobar, vincula también la consulta al buró más reciente
     de cada persona si cambió desde el envío, y el detalle de auditoría incluye qué consulta se
     vio — ver `RF08_resumen_solicitud.md` §9 y la prueba de pantalla de la sección 9 de este
     documento (el caso real que lo confirmó fue justamente un rechazo).

### 2.1 La lista de motivos

Catálogo en la tabla `motivos_rechazo_solicitud` (`bd/01_esquema.sql` líneas 771-778; sembrada
en `bd/03_parametros.sql` líneas 330-337) — **no** es un enum de Java ni un parámetro de
`parametros_sistema`, es su propia tabla con `codigo` (único), `nombre` (el texto que ve el
gerente), `requiere_detalle`, `activo` y `orden`. `MotivoRechazoSolicitudDAO.listarActivos`
(`WHERE activo = TRUE ORDER BY orden`) es la única consulta que la lee para la pantalla — el
comentario del DAO la llama "Catálogo de solo lectura": no hay ninguna pantalla de
administración para crear/editar/desactivar motivos, solo el seed SQL.

**Lista desde el 11/10/2026** (decisión de Joel, sección 9 — `POLITICA_LABORAL`/`ZONA`/`EDAD`
desactivados, no borrados; ver el análisis completo en la sección 6.2):

| Orden | Código | Nombre | ¿Requiere detalle? | Activo |
|---|---|---|---|---|
| 1 | `POLITICA_LABORAL` | Política laboral (situación laboral no admitida) | No | **No** (desde 11/10/2026) |
| 2 | `ZONA` | Zona (fuera de la cobertura) | No | **No** (desde 11/10/2026) |
| 3 | `EDAD` | Edad (fuera del rango permitido) | No | **No** (desde 11/10/2026) |
| 4 | `INFORMCONF` | Informconf: figura con deudas | No | Sí |
| 5 | `CAPACIDAD_PAGO` | Capacidad de pago insuficiente | No | Sí |
| 6 | `DOCUMENTACION_INCOMPLETA` | Documentación incompleta o inconsistente | No | Sí |
| 7 | `OTRO` | Otro | **Sí** | Sí |

Las filas 1-3 siguen existiendo (nunca se borran, sección 6.2) pero ya no aparecen en el
`<select>` de `aprobaciones/ficha.jsp` — el `<select>` real, de hoy en adelante, ofrece 4
opciones: Informconf, Capacidad de pago, Documentación incompleta y Otro.

## 3. Dónde quedan guardados el usuario y la fecha

Mismas columnas que RF09 (es el mismo método `marcarDecidida`, ver RF09 §3 para el detalle
completo de por qué la fecha no se puede falsear):

- **`solicitudes_credito.decidido_por`** — el usuario que rechazó (columna compartida con
  aprobar; no hay una columna "rechazado_por" separada).
- **`solicitudes_credito.fecha_decision`** — `now()` del servidor.
- **`solicitudes_credito.motivo_rechazo`** (TEXT) — el texto combinado ("nombre - detalle" o
  solo "nombre"), **guardado como texto fijo en el momento del rechazo**, no como una
  referencia que se resuelve después contra la tabla de motivos. Esto importa para la pregunta
  de Joel sobre qué pasa con los registros históricos si se desactiva un motivo (sección 6.2):
  **nada** — el texto ya quedó escrito, no depende de que el motivo siga existiendo o siga
  activo.
- **`solicitudes_credito.id_motivo_rechazo`** (FK a `motivos_rechazo_solicitud`) — además del
  texto fijo, se guarda la referencia al motivo elegido (para poder filtrar/agrupar rechazos por
  motivo en el futuro, aunque hoy ningún informe lo hace todavía).
- **`auditoria_operaciones`**: evento `RECHAZAR_SOLICITUD`, mismo criterio que
  `APROBAR_SOLICITUD` (RF09 §3) — `id_usuario` y `fecha_hora` del servidor, detalle en texto
  libre ("Rechazó la solicitud COD. Motivo: ... Consulta(s) vista(s) al decidir: [...]").

## 4. Qué controles tiene

Los mismos que RF09 (misma ficha, mismo permiso, misma `validarPendienteAprobacion`, mismo
`marcarDecidida` ya corregido el 11/10/2026 contra la carrera de dos pestañas) — no se repite la
tabla completa, ver RF09 §4 y §9. La única diferencia de RF10 es el motivo obligatorio:

| Control | Cumple | Dónde / detalle |
|---|---|---|
| Motivo obligatorio, de una lista | **Sí** | `idMotivoRechazo` no puede ser nulo; debe existir en `motivos_rechazo_solicitud` |
| Detalle obligatorio solo cuando el motivo lo exige | **Sí** | Revalidado en el servidor (`motivo.isRequiereDetalle()`), no solo en el JavaScript del `<select>` |
| Solo gerente/administrador, solo `PENDIENTE_APROBACION`, CSRF, doble envío, dos pestañas | **Igual que RF09, ya corregida la carrera** | Ver `RF09_aprobar.md` §4 y §9 |

## 5. Qué pasa después

- **La rechazada NO pasa a ningún lado** — queda en `RECHAZADA` para siempre; no hay ninguna
  bandeja que la vuelva a mostrar como pendiente de algo (a diferencia de la aprobada, que sigue
  a Formalización). El único lugar donde se la vuelve a ver es el Historial de RF07
  (`/aprobaciones?vista=historial`) y la ficha del cliente/solicitud.
- **Qué ve el oficial de crédito**: en `/solicitudes` y en la ficha de esa solicitud
  (`solicitudes/ficha.jsp`), la misma franja roja "Rechazada por NOMBRE el FECHA. Motivo: ..."
  que ve el gerente — mismo texto de motivo, carácter por carácter (es el mismo campo
  `motivoRechazo` leído por las dos pantallas).
- **Qué ve la cajera**: nada — mismo motivo que RF09 (nunca llega a `/desembolso`).
- **¿Se puede volver a intentar?** El anteproyecto no lo pide y el sistema no ofrece "reabrir"
  una rechazada — el oficial tendría que crear una solicitud nueva si quiere volver a intentarlo
  (con los datos que hagan falta cambiar). No es un hallazgo, es coherente con que RECHAZADA es
  un estado final (igual que APROBADA, CANCELADA).

## 6. Análisis

### 6.1 Análisis del 11/10/2026 (este documento)

Hecho a pedido de Joel, revisando el texto firmado de RF10 contra el código real
(`ControladorAprobacionesServlet`, `ServicioAprobacionCredito.rechazar`,
`MotivoRechazoSolicitudDAO`, `aprobaciones/ficha.jsp`), sin cambiar nada. Resultado resumido en
la tabla de la sección 7.

### 6.2 Los 3 motivos que ya bloquean el envío (pedido de Joel)

Confirmado leyendo `ServicioSolicitudes.enviarAAprobacion` (línea 547) y
`validarPoliticaCredito` (línea 365): los tres motivos `EDAD`, `ZONA` y `POLITICA_LABORAL`
corresponden **exactamente** a los tres puntos que `validarPoliticaCredito` ya revisa al
**enviar** la solicitud a aprobación — si alguno falla, se agrega a la lista de `faltantes` y
`enviarAAprobacion` lanza `NegocioException` ("Faltan completar estos puntos antes de enviar a
aprobación"), **bloqueando el envío**. La solicitud nunca llega a `PENDIENTE_APROBACION` si no
cumple los tres.

`ServicioAprobacionCredito.rechazar` (ni `aprobar`) **no vuelve a llamar**
`validarPoliticaCredito` — confirmado con una búsqueda de todas las llamadas a ese método en el
código: las únicas dos son dentro de `ServicioSolicitudes` (el checklist previo al envío y el
envío mismo). Esto significa que, en el flujo normal, **toda solicitud que llega a la ficha del
gerente ya cumple edad/zona/situación laboral** — el gerente nunca tendría un motivo real para
elegir esos 3 de la lista.

**El único caso borde donde sí podrían aplicar**: si el parámetro de política (`credito.edad_minima`,
`credito.edad_maxima`, `credito.departamentos_habilitados`, `credito.situaciones_laborales_admitidas`)
cambia **después** de que la solicitud se envió pero **antes** de que el gerente decida — en ese
momento la solicitud ya no cumpliría la política vigente, pero nadie la revisa de nuevo contra
el parámetro nuevo (ni al mostrar la ficha, ni al aprobar/rechazar). Es un caso real pero
infrecuente, y hoy el sistema no avisa de él de ninguna forma (no hay nada parecido al aviso de
mora de RF08 para "esta solicitud ya no cumple la política vigente").

**Propuesta de lista final** (sin Edad, Zona ni Política laboral), por `orden`:

| Orden | Código | Nombre |
|---|---|---|
| 4 | `INFORMCONF` | Informconf: figura con deudas |
| 5 | `CAPACIDAD_PAGO` | Capacidad de pago insuficiente |
| 6 | `DOCUMENTACION_INCOMPLETA` | Documentación incompleta o inconsistente |
| 7 | `OTRO` | Otro |

No haría falta renumerar `orden` — como `listarActivos` filtra `WHERE activo = TRUE` antes de
ordenar, los 4 que quedan activos se ven en el mismo orden relativo aunque sus números salten de
4 a 7 (no hay ningún "hueco" visible en la pantalla).

`CAPACIDAD_PAGO` se mantiene a propósito: a diferencia de los 3 que se sacan, la afectación
**sí** se revalida en `aprobar` (RN-03, bloquea aprobar si se supera el límite) pero **no** en
`rechazar` — un gerente podría elegir "Capacidad de pago insuficiente" como un juicio más amplio
que el número mecánico de afectación (por ejemplo, estabilidad laboral dudosa aunque la cuota
entre dentro del límite), así que sigue teniendo sentido como motivo manual.

**¿Cómo está guardada la lista y qué pasa con los registros históricos si se desactiva un
motivo?** Es una tabla real (`motivos_rechazo_solicitud`), con una columna `activo` ya pensada
para esto — desactivar es `UPDATE motivos_rechazo_solicitud SET activo = FALSE WHERE codigo IN
('EDAD', 'ZONA', 'POLITICA_LABORAL')`, no un `DELETE`. Como `solicitudes_credito.motivo_rechazo`
ya guarda el texto combinado como una cadena fija (sección 3) y no una referencia que se vuelve
a resolver, **ninguna solicitud ya rechazada con esos motivos cambia de texto ni de apariencia**
— el historial de RF07 y la ficha de esas solicitudes siguen mostrando exactamente lo que
decían. El `id_motivo_rechazo` (la referencia FK) tampoco se rompe: la fila en
`motivos_rechazo_solicitud` sigue existiendo, solo con `activo = FALSE`, así que la clave
foránea sigue siendo válida. El único efecto de desactivar es que esos 3 motivos **dejan de
aparecer en el `<select>`** de rechazos nuevos.

No hay ninguna pantalla de administración para hacer este `UPDATE` desde la interfaz — habría
que hacerlo a mano (un script de migración, mismo patrón que `bd/migraciones/`, o editando el
seed para instalaciones nuevas) **cuando Joel lo apruebe explícitamente** — no se toca
`sgcpc_db` ni `sgcpc_db_pruebas` sin su autorización puntual para esa acción.

## 7. Tabla de cumplimiento

| Parte del texto | Cumple | Dónde / detalle |
|---|---|---|
| Permitir rechazar la solicitud | **Sí** | `ServicioAprobacionCredito.rechazar` |
| Elegir un motivo de una lista | **Sí** | `motivos_rechazo_solicitud`, `<select>` con `listarActivos` |
| Registrar el usuario que la rechazó | **Sí** | `solicitudes_credito.decidido_por` + auditoría |
| Registrar la fecha | **Sí** | `solicitudes_credito.fecha_decision` (`now()` del servidor) + auditoría |
| Solo gerente/administrador, solo pendientes | **Sí, misma salvedad que RF09** | Ver RF09 §4 |

El texto de RF10 **se cumple sin excepción**. El hallazgo de la sección 6.2 (motivos que ya
están bloqueados al enviar) no es un incumplimiento del texto — la lista de motivos existe y
funciona tal como la pide el anteproyecto — es una observación de calidad/consistencia que Joel
pidió revisar explícitamente.

## 8. Propuestas (para que Joel decida)

1. **Sacar `EDAD`, `ZONA` y `POLITICA_LABORAL` de la lista** (desactivarlos, no borrarlos) — ver
   el análisis completo y la propuesta de lista final en la sección 6.2.
2. **Avisar si la política cambió entre el envío y la decisión** (caso borde de la sección 6.2):
   agregar una validación informativa (no bloqueante, al estilo del aviso de mora de RF08) que
   re-chequee edad/zona/situación laboral al mostrar la ficha del gerente, y avise si alguno ya
   no se cumple con la política vigente — a diseñar con Joel si vale la pena para un caso tan
   infrecuente.
3. **Cerrar la ventana de la carrera de dos pestañas** — mismo hallazgo y misma propuesta que
   RF09 §8 punto 1 (el `UPDATE` de `marcarDecidida` es compartido entre aprobar y rechazar).

## 9. Decisiones

Decisiones del 11/10/2026 (Joel), sobre las 3 propuestas de la sección 8 — todas implementadas:

1. **Sacar `EDAD`, `ZONA` y `POLITICA_LABORAL` de la lista**: `bd/migraciones/
   E10_12_motivos_rechazo_desactivar.sql` (idempotente, `UPDATE ... SET activo = FALSE WHERE
   codigo IN (...) AND activo = TRUE`, `BEGIN`/`COMMIT`, mismo estilo que las demás migraciones
   de datos de `E10_1x`) — **aplicado SOLO a `sgcpc_db_pruebas`, nunca a `sgcpc_db`** (pedido
   explícito de Joel). `bd/03_parametros.sql` también se actualizó: los 3 nacen con `activo =
   FALSE` desde una instalación nueva. Sin borrar ninguna fila — ver sección 6.2 para por qué
   no afecta a lo ya rechazado. Queda pendiente aplicar el mismo script a `sgcpc_db` antes de
   entregar — ver sección 12.
2. **Avisar si la política cambió entre el envío y la decisión**: **no se implementa en esta
   vuelta** — Joel no lo mencionó entre las decisiones de este pedido; queda como propuesta
   abierta en la sección 8, sin programar.
3. **Cerrar la ventana de la carrera de dos pestañas**: implementado, compartido con RF09 — ver
   `RF09_aprobar.md` §9 para el detalle completo (`marcarDecidida`, `SolicitudYaDecididaException`,
   `MensajesFlash.guardarAdvertencia`). No se repite acá.

### Cuándo se cancela, se rechaza y se anula (17/10/2026, consolidado, pedido de Joel)

Los tres términos no son intercambiables — cada uno vive en un RF distinto, con su propio motivo
y su propio momento:

- **Cancelar** (`ServicioSolicitudes.cancelar`/`ServicioAprobacionCredito.cancelar`, RF06, motivo
  libre obligatorio): el trámite se corta SIN juicio sobre el crédito en sí — el cliente desiste o
  hubo un error de carga. El oficial cancela mientras está en `BORRADOR`; el gerente cancela una
  `PENDIENTE_APROBACION` o una `APROBADA` todavía sin formalizar.
- **Rechazar** (`ServicioAprobacionCredito.rechazar`, este documento, motivo de una lista fija):
  es la decisión del gerente de que el crédito NO corresponde — un juicio sobre el crédito, no un
  desistimiento. Solo sobre una solicitud `PENDIENTE_APROBACION`. Desde el 17/10/2026, el motivo
  `INFORMCONF` ("Informconf: figura con deudas") es además la salida obligatoria cuando el
  sistema detecta mora y bloquea la aprobación (RN-18, ver `RF06_envio_aprobacion.md §7`) — el
  gerente ya no puede "aprobar igual", tiene que rechazar con ese motivo.
- **Anular** (`ServicioFormalizacion.anularAntesDeDesembolso`, `RF14_anular_prestamo.md`, motivo
  de texto ≥ 10 caracteres): préstamo YA FORMALIZADO (existe `CC-AAAA-NNNNN`) que no llega a
  desembolsarse. Solo el gerente, solo mientras sigue `PENDIENTE_DESEMBOLSO`.
- En los tres casos: nada se borra (queda como historial, `CANCELADA`/`RECHAZADA`/
  `ANULADO_ANTES_DESEMBOLSO`, respectivamente) y el cliente puede iniciar una solicitud nueva.
- **Por qué importa la distinción ahora**: el bloqueo de edición de los datos de evaluación
  (`ServicioClientes.actualizar`, ver `docs/funcionamiento/FLUJO_OPERATIVO.md`) le dice al oficial
  a cuál de los tres pedirle al gerente, según la etapa exacta en la que está la solicitud o el
  préstamo — nunca "cancele o rechace, lo que sea".

## 10. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 17/10/2026 | Consolidado "cuándo se cancela, rechaza y anula" (tanda "reglas de decisión del crédito"); el motivo `INFORMCONF` pasa a ser la salida obligatoria cuando RN-18 bloquea la aprobación por mora. Sin cambios de código en este RF. Probado en pantalla por Joel (ver `FLUJO_OPERATIVO.md`). | ver tag | `reglas-decision-probado` |
| 11/10/2026 | Mensaje unificado de "ya decidida" (compartido con RF09, `validarPendienteAprobacion` ahora lanza `SolicitudYaDecididaException`), `mvn test` en verde | `7046dfa` | `rf11-probado` |
| 11/10/2026 | Probado en pantalla completo: lista de motivos reducida a 4, "Sí, rechazar" correcto, dos pestañas bloqueadas (con el hallazgo de mensaje de la sección 11) | *(pendiente)* | `rf10-probado` |
| 11/10/2026 | Implementadas las decisiones 1 y 3 de la sección 9 (motivos EDAD/ZONA/POLITICA_LABORAL desactivados vía migración + `03_parametros.sql`; carrera de dos pestañas cerrada, compartida con RF09); botón "Confirmar rechazo" con texto propio ("Sí, rechazar") | *(pendiente)* | — |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

## 11. Pruebas realizadas en pantalla

**11/10/2026 (Joel)**, como parte de la prueba de pantalla de RF08 (segunda vuelta,
`RF08_resumen_solicitud.md` §11): rechazo de SOL-2026-0004 con motivo "Informconf: figura con
deudas" — confirmado en pantalla que:
- El `<select>` ofreció la lista completa de motivos (incluidos, todavía, `EDAD`/`ZONA`/
  `POLITICA_LABORAL` — la decisión 1 de la sección 9 todavía no estaba implementada en ese
  momento).
- La franja roja mostró el motivo elegido.
- La auditoría registró `RECHAZAR_SOLICITUD` con el detalle "Consulta(s) vista(s) al decidir:
  [id 3, clasificación E, mora Sí]" — esta es, de hecho, la prueba que confirmó la
  re-vinculación de RF08 (punto 4), que no se pudo cubrir con JUnit por necesitar conexión.

Sirvió como prueba en pantalla de RF10 en general (motivo obligatorio, usuario y fecha
registrados, franja correcta).

**11/10/2026 (Joel), después del Clean and Build:**
- Lista de motivos en el `<select>`: solo las 4 opciones activas (Informconf, Capacidad de
  pago, Documentación incompleta, Otro) — confirmado, Edad/Zona/Política laboral ya no
  aparecen.
- **Dos pestañas** (compartida con RF09, ver `RF09_aprobar.md` §11): en la segunda pestaña, sin
  recargar, "Confirmar rechazo" mostró el cartel con el botón "Sí, rechazar" correctamente y, al
  confirmar, **quedó bloqueado** — la solicitud (SOL-2026-0001, ya aprobada en la otra pestaña)
  siguió `APROBADA`. Mismo hallazgo de mensaje que en RF09: el cartel mostrado fue el genérico
  viejo de `validarPendienteAprobacion`, como error, no el nuevo de `SolicitudYaDecididaException`
  como advertencia — ver el análisis en `RF09_aprobar.md` (posterior al cierre, sin programar
  todavía).
- Rechazo de SOL-2026-0004 (motivo de la lista, usuario y fecha): ya estaba confirmado desde la
  prueba de RF08 (sección 11, arriba) — Joel lo reconfirmó como válido para RF10 sin volver a
  repetirlo.

## 12. Pendientes y trabajo futuro

- **Antes de entregar**: aplicar `bd/migraciones/E10_12_motivos_rechazo_desactivar.sql` también
  a `sgcpc_db` (la base real) — hoy solo se aplicó a `sgcpc_db_pruebas`, por pedido explícito de
  Joel de no tocar la real sin que él lo pida puntualmente.
- **Mensaje unificado de "ya decidida"** (hallazgo de la sección 11, compartido con RF09,
  implementado y cubierto con pruebas unitarias el 11/10/2026 según `RF09_aprobar.md` §14,
  commit `rf11-probado`): **verificación en pantalla pendiente para la próxima solicitud
  pendiente** — repetir la prueba de dos pestañas de rechazo y confirmar que ya aparece la
  advertencia con nombre, fecha y botón "Entendido".
- La propuesta 2 de la sección 8 (avisar si la política cambió entre el envío y la decisión)
  sigue abierta, sin programar — a decidir con Joel si vale la pena para un caso tan
  infrecuente.
- **Otros cambios de estado con el mismo patrón que tenía `marcarDecidida`** (la misma lista que
  `RF09_aprobar.md` §12, no se repite acá — es el mismo `marcarDecidida` compartido entre
  aprobar y rechazar).

## 13. Guion para la defensa

1. Abrir una solicitud `PENDIENTE_APROBACION`, tocar "Rechazar", mostrar que la lista de motivos
   ya solo tiene 4 opciones (Informconf, Capacidad de pago, Documentación incompleta, Otro) y
   que el detalle se vuelve obligatorio solo al elegir "Otro".
2. Confirmar el rechazo (botón "Sí, rechazar") — mostrar la franja roja con el usuario, la
   fecha/hora y el motivo elegido.
3. Entrar como oficial a `/solicitudes` y mostrar la misma franja, mismo texto.
4. Mostrar una solicitud rechazada ANTES de este cambio con motivo Edad/Zona/Política laboral
   (si hay alguna de prueba) y confirmar que sigue mostrando su motivo original sin cambios.
5. Intentar rechazar sin elegir motivo (forzando el formulario) — mostrar el bloqueo del
   servidor ("Seleccione el motivo del rechazo."), no solo del JavaScript.
6. Compartir con RF09 la prueba de dos pestañas (aprobar en una, rechazar en la otra casi al
   mismo tiempo) — ver `RF09_aprobar.md` §13.
