# RF14 — Anular un préstamo antes del desembolso

**Estado: probado contra el anteproyecto, completo el 12/10/2026 (`rf14-probado`)** — análisis
aprobado por Joel el 11/10/2026 con 3 decisiones (motivo obligatorio con mínimo de 10 caracteres,
mismo criterio que RF19; carrera de dos pestañas corregida con `UPDATE` condicionado al estado y
mensaje de advertencia con quién y cuándo; botón "Sí, anular"), implementadas el 11/10/2026 y
reprobadas en pantalla el 12/10/2026 con CC-2026-00002 (Rodrigo), con 3 correcciones de esa
reprueba (franja con quién y cuándo, checklist corregido, mecanismo genérico de `pie.jsp`)
también ya reprobadas en pantalla. Único punto que sigue pendiente de pantalla: el bloqueo EN
PANTALLA del motivo corto, con la próxima operación `PENDIENTE_DESEMBOLSO` (ver §9/§10).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF14 (módulo Formalización/Desembolso):

> El sistema debe permitir al gerente anular un préstamo antes del desembolso registrando el
> motivo.

## 2. Cómo funciona

Desde `/formalizacion/{idPrestamo}` (ficha del préstamo), con el préstamo todavía
`PENDIENTE_DESEMBOLSO`: un botón "Anular préstamo" (colapsable, en rojo) abre un formulario con
un campo de texto libre para el motivo y lo postea a `POST /formalizacion/{id}/anular`.
`ServicioFormalizacion.anularAntesDeDesembolso`:

1. Valida el motivo (`validarMotivoAnulacion`, 11/10/2026): obligatorio, **mínimo 10 caracteres
   reales** (mismo criterio que el motivo de diferencia de RF19) — vacío y "menos de 10" son el
   mismo caso, `MotivoAnulacionInsuficienteException` (advertencia, "Entendido"), no error. En
   pantalla, el campo tiene `minlength="10" required`; el cartel que lo frena antes de llegar al
   servidor es el genérico de `pie.jsp` (A.9, consolidación RF08-RF19).
2. Revalida, dentro de la transacción, que el préstamo siga `PENDIENTE_DESEMBOLSO` (si ya se
   desembolsó o ya se anuló, rechaza con `NegocioException`).
3. `PrestamoDAO.marcarAnuladoAntesDesembolso` (11/10/2026: `UPDATE` condicionado a
   `estado_prestamo = 'PENDIENTE_DESEMBOLSO'`, devuelve si de verdad actualizó): `estado_prestamo
   = 'ANULADO_ANTES_DESEMBOLSO'`, guarda `motivo_anulacion_sin_desembolso`,
   `fecha_anulacion_sin_desembolso` (= `now()`) y `anulado_por`. **Si devuelve `false`**
   (alguien confirmó el desembolso en la angosta ventana entre el paso 2 y este `UPDATE`, carrera
   de dos pestañas): se arma `PrestamoNoPendienteDesembolsoException` con "Este préstamo ya fue
   desembolsado por [cajera] el [fecha y hora]. Actualice la página." (datos reales de
   `desembolsos`, ver sección 6f) y se corta — el `catch` de la transacción hace rollback de
   todo, ni la auditoría del paso 4 queda.
4. Auditoría (`ANULAR_PRESTAMO_PRE_DESEMBOLSO`, entidad PRESTAMO, detalle con el motivo) — solo
   si el paso 3 sí actualizó.

No hay ningún otro efecto: no se toca la solicitud, los PDF generados, los documentos firmados
cargados, la caja, ni el número de operación.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `ANULAR_PRESTAMO_PRE_DESEMBOLSO` | PRESTAMO | Se anuló el préstamo — detalle: "Anuló CC-aaaa-nnnnn antes del desembolso. Motivo: [texto]." |

## 4. Dónde está

- `servicios/ServicioFormalizacion.java` — `anularAntesDeDesembolso`, `validarMotivoAnulacion`.
- `dao/PrestamoDAO.java` — `marcarAnuladoAntesDesembolso`.
- `controladores/ControladorFormalizacionServlet.java` — `procesarAnular` (`POST /formalizacion/{id}/anular`),
  con el chequeo de perfil explícito (ver sección 6a).
- `webapp/WEB-INF/vistas/formalizacion/ficha.jsp` — botón/formulario de anular (líneas ~298-312)
  y el aviso del estado ya anulado (líneas ~89-93).
- `enumeraciones/EstadoPrestamo.java` — `ANULADO_ANTES_DESEMBOLSO` ("Anulado antes del
  desembolso"), estado terminal (no hay transición de salida).
- Columnas en `prestamos`: `motivo_anulacion_sin_desembolso`, `fecha_anulacion_sin_desembolso`,
  `anulado_por`.

## 5. Dependencias

- **Depende de**: RF11 (formalización) — solo se puede anular un préstamo ya formalizado
  (`PENDIENTE_DESEMBOLSO`); RF13 (checklist) — no influye, se puede anular con el checklist
  completo o incompleto, antes de confirmar el desembolso.
- **A quién afecta un cambio acá**: RF16 (la bandeja de desembolso deja de listarlo, ver 6c);
  RF09 (comparte, sin resolver, el mismo patrón de UPDATE sin re-chequeo de estado que
  `marcarDecidida` tenía — ver 6d).

## 6. Análisis

### a) Quién puede anular — servidor y pantalla

**Gerente administrador y administrador, nunca el oficial.** La ruta `/formalizacion/*` solo
exige `GENERAR_DOCUMENTOS_FORMALIZACION` (TOTAL para oficial y gerente — D-68, misma pantalla
para los dos perfiles), así que `procesarAnular` hace un chequeo de perfil EXPLÍCITO, más
restrictivo que el de la ruta: `solicitante.getPerfilUsuario() != GERENTE_ADMINISTRADOR &&
!= ADMINISTRADOR` → 403. **Confirma el texto** ("el gerente") y, de paso ("D7", pedido de Joel,
sin fecha exacta reconstruida), el administrador también puede — igual criterio que otras
acciones reservadas al gerente (`ANULAR_PRESTAMO_PRE_DESEMBOLSO`, `AUTORIZAR_REVERSION_O_DIFERENCIA`,
etc.). En pantalla, el botón "Anular préstamo" solo se renderiza si
`esGerenteFormalizacion` (mismo chequeo de perfil) — un oficial no lo ve, y si forzara la URL
del POST, el servidor lo rechaza igual.

### b) En qué estados se puede anular

**Solo `PENDIENTE_DESEMBOLSO`** — revalidado dentro de la transacción
(`anularAntesDeDesembolso`), no solo en la pantalla. Un préstamo `ACTIVO`/`EN_MORA`/`FINALIZADO`/
ya `ANULADO_ANTES_DESEMBOLSO` se rechaza con "Solo se puede anular un préstamo pendiente de
desembolso." Coincide exactamente con el texto ("antes del desembolso").

### c) Motivo — ¿lista o texto? ¿mínimo de caracteres?

**Decisión de Joel (11/10/2026): texto libre (no lista), con mínimo de 10 caracteres reales,
mismo criterio que el motivo de diferencia de arqueo (RF19) — implementado.**
`validarMotivoAnulacion` lanza `MotivoAnulacionInsuficienteException` (advertencia) si el motivo
es nulo o tiene menos de 10 caracteres; en pantalla, `minlength="10" required` en el campo, con
el cartel genérico de `pie.jsp` (A.9) en vez del popup nativo del navegador.

### d) Qué pasa con la solicitud, los PDF, los firmados y el número de operación

- **Solicitud** (`solicitudes_credito`): **no se toca**. Queda `FORMALIZADA` para siempre (ese
  estado ya es terminal desde que se formalizó, `solicitudDAO.marcarFormalizada`) — la
  anulación del préstamo no se refleja en la solicitud. Quien mire solo la solicitud (sin abrir
  el préstamo) no ve ningún indicio de que terminó anulada. No es un incumplimiento del texto
  (que no pide nada sobre la solicitud), pero es una limitación a tener en cuenta si alguna vez
  se pide un informe "por solicitud".
- **PDF generados** (`CONTRATO_GENERADO`/`PAGARE_GENERADO`/`PLAN_PAGOS_GENERADO`) y **firmados
  cargados** (`CONTRATO_FIRMADO`/`PAGARE_FIRMADO`/`PLAN_PAGOS_FIRMADO` si ya se cargó alguno):
  **no se tocan, no se borran, no se marcan como inválidos** — quedan en `documentos_expediente`
  como registro histórico de lo que de hecho se generó/cargó antes de anular. Correcto: son
  evidencia de lo que pasó, no hay motivo para borrarlos.
- **Número de operación** (`identificador_operacion`, ej. CC-2026-00003): **nunca se reutiliza**
  — `anularAntesDeDesembolso` no toca el numerador (`NumeradorDAO`, una secuencia simple por
  tipo/año) ni genera uno nuevo. El número queda "quemado" para siempre en el préstamo anulado;
  el siguiente préstamo que se formalice sigue la secuencia, nunca vuelve a usar ese número. Esto
  es correcto y esperable (mismo criterio que un cheque anulado: el número no se repite).

### e) Caja

**Sin ningún efecto.** No hay ninguna fila en `movimientos_caja` ni `desembolsos` para un
préstamo que nunca llegó a desembolsarse — coherente con que la caja solo registra dinero que
efectivamente se movió.

### f) Dos pestañas: ¿anular y desembolsar al mismo tiempo? — CORREGIDO el 11/10/2026

**Era un hallazgo real (no un bug nuevo de RF14 — ya estaba catalogado, sin resolver, en
`RF09_aprobar.md` §12 "Otros cambios de estado con el mismo patrón", que listaba textualmente
`PrestamoDAO.marcarAnuladoAntesDesembolso` y `marcarDesembolsado` entre los que compartían el
problema de `marcarDecidida` antes de su corrección) — Joel aprobó la corrección completa:**

- Los dos `UPDATE` (`marcarAnuladoAntesDesembolso` y `marcarDesembolsado`) ahora tienen
  `AND estado_prestamo = 'PENDIENTE_DESEMBOLSO'` en su propio `WHERE`, y devuelven `boolean`
  (`executeUpdate() > 0`) en vez de `void` — mismo patrón que `SesionCajaDAO.marcarCerrada`
  (RF19) y `SolicitudDAO.marcarDecidida` (RF09).
- **Si `anularAntesDeDesembolso` pierde la carrera** (el desembolso se confirmó primero):
  `ServicioFormalizacion.construirErrorPrestamoYaDesembolsado` relee el préstamo y busca el
  `Desembolso` confirmado (`DesembolsoDAO.obtenerConfirmadoPorPrestamo`) para el nombre de la
  cajera y la fecha/hora reales, y lanza `PrestamoNoPendienteDesembolsoException` ("Este
  préstamo ya fue desembolsado por [cajera] el [fecha y hora]. Actualice la página.") —
  advertencia, no error. El `catch` de la transacción hace rollback completo: nada de auditoría.
- **Si `confirmarDesembolso` pierde la carrera** (el préstamo se anuló primero): en ese punto ya
  se insertaron la fila de `desembolsos` y el movimiento `DESEMBOLSO` en `movimientos_caja`
  (dentro de la misma transacción, sin comitear) — `ServicioDesembolso.construirErrorPrestamoYaAnulado`
  relee el préstamo (`anulado_por`/`fecha_anulacion_sin_desembolso`) y lanza la misma excepción
  ("Este préstamo ya fue anulado por [gerente] el [fecha y hora]. Actualice la página."). El
  rollback completo de la transacción deshace el `desembolsos`/`movimientos_caja` ya insertados
  — **no queda ningún comprobante ni movimiento de caja**, exactamente lo que pedía Joel.
- Ambos controladores (`ControladorFormalizacionServlet.procesarAnular`,
  `ControladorDesembolsoServlet.procesarConfirmar`) capturan `PrestamoNoPendienteDesembolsoException`
  antes del `catch` genérico y lo muestran como advertencia ("Entendido").

### g) Cumplimiento contra el texto firmado

El texto se cumple: el gerente (y el administrador) puede anular un préstamo antes del
desembolso, registrando el motivo. El único punto abierto es de calidad/robustez (f, la carrera
de dos pestañas), no de incumplimiento del texto en el camino normal (un solo usuario, sin
carrera real).

## 7. Decisiones

- **Estado terminal, sin reversa**: `ANULADO_ANTES_DESEMBOLSO` no tiene ninguna transición de
  salida en el código — una vez anulado, el préstamo queda así para siempre (coherente con "no
  se puede deshacer", como ya se confirma en el cartel de `data-confirmar` del botón).
- **Perfil restringido a mano, no por `FiltroAutorizacion`**: porque la ruta `/formalizacion`
  comparte permiso entre oficial y gerente para las demás acciones (generar/regenerar), anular
  necesita su propio chequeo de perfil dentro del controlador — mismo patrón ya usado para
  "generar-pdf"/otras sub-acciones restringidas de esta misma pantalla.
- **El número de operación nunca se reutiliza** — ya es el comportamiento del código (el
  numerador es una secuencia simple, nunca se "libera" un número), no hace falta ningún cambio
  para cumplir esto.
- **Motivo: texto libre con mínimo de 10 caracteres** (11/10/2026, decisión de Joel) — mismo
  criterio que RF19, no una lista cerrada.
- **Carrera de dos pestañas: `UPDATE` condicionado + mensaje con quién y cuándo** (11/10/2026,
  decisión de Joel) — mismo patrón que RF09/RF19, en vez de dejarla catalogada sin resolver;
  prioridad alta por el impacto financiero potencial (a diferencia del resto de la lista de
  RF09, que son solo mensajes menos informativos).

### Cuándo se anula, se cancela y se rechaza (17/10/2026, consolidado, pedido de Joel)

Ver el detalle completo en `RF10_rechazar.md §10`, "Cuándo se cancela, se rechaza y se anula" — se
documenta una sola vez, citado desde los tres RF para que no se desincronicen. Resumen desde la
perspectiva de este RF: **anular** (`anularAntesDeDesembolso`, motivo ≥ 10 caracteres) es lo único
que aplica a un préstamo YA FORMALIZADO (existe `CC-AAAA-NNNNN`) y todavía `PENDIENTE_DESEMBOLSO`
— **cancelar** (RF06) y **rechazar** (RF10) actúan sobre la *solicitud*, antes de que exista
préstamo. El bloqueo de edición de los datos de evaluación del cliente
(`ServicioClientes.actualizar`, 17/10/2026) usa exactamente esta distinción para decirle al
oficial a quién pedirle qué, según la etapa real.

## 8. Historial de cambios

*(Reconstruido desde `git log` el 11/10/2026 — este RF no tuvo documento propio hasta ahora.)*

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 14/11/2026 | **B178**: anular dejó de ser un formulario desplegable dentro de la ficha (`data-toggle="collapse"`) y pasó a una **ventana** titulada *"¿Anular el préstamo CC-AAAA-NNNNN?"*, con el cliente, la C.I. y el monto aprobado en el cuerpo, el foco en *"No, volver"* y sin `data-confirmar` (la ventana ES la confirmación). Mismo patrón que B155/B168/B171. El formulario no cambió —misma ruta POST, mismo `motivo` con `minlength="10"`, mismo CSRF y el mismo id `campoMotivoAnular`, así que el cartel de `invalid` de `pie.jsp` sigue andando— y **el servidor tampoco**: `procesarAnular` sigue exigiendo gerente/administrador (D7) y `validarMotivoAnulacion` sus 10 caracteres. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 17/10/2026 | Consolidado "cuándo se anula, cancela y rechaza" (tanda "reglas de decisión del crédito"), citando `RF10_rechazar.md §10`. Sin cambios de código en este RF. Probado en pantalla por Joel (ver `FLUJO_OPERATIVO.md`). | ver tag | `reglas-decision-probado` |
| 12/10/2026 | Reprueba de las 3 correcciones (franja, checklist, mecanismo de `pie.jsp`): VERIFICADO EN PANTALLA con CC-2026-00002. Franja: "Anulado antes del desembolso por Joel Jesús Acosta el 12/10/2026 00:11. Motivo: ...". Checklist: sin "listo para desembolso". Mecanismo de `checkValidity()` antes de confirmar: sin regresión (probado también en `/caja`, "Marcar revisada" con el campo vacío → "Falta un dato" sin pedir confirmación). RF15 revisto de paso: pasada la medianoche, "La caja del 11/10/2026 sigue abierta...". Único punto que sigue pendiente de pantalla: el bloqueo EN PANTALLA del motivo corto (CC-2026-00002 ya está anulado, no hay otra operación `PENDIENTE_DESEMBOLSO` a mano ahora) — se verifica con la próxima (ver §9/§10). | *(pendiente)* | `rf14-probado` |
| 12/10/2026 | Reprueba en pantalla de Joel con CC-2026-00002 (Rodrigo): confirmó el flujo completo (motivo corto rechazado por el servidor, motivo válido + "Sí, anular" → "Préstamo anulado", estado y franja, Re-emitir/Anular/carga de documentos ya no se ofrecen, desaparece de Formalización y Desembolsos). Encontró y se corrigieron 3 detalles: (1) la pantalla NO frenaba un motivo corto ANTES de la confirmación — `pie.jsp` ahora llama `formulario.checkValidity()`/`reportValidity()` explícitamente antes de mostrar el SweetAlert de confirmación, en el mecanismo genérico de `data-confirmar` (afecta a todos los formularios del sistema, sin cambiar el comportamiento de los que ya validaban bien, ej. RF19); (2) la franja de anulado solo decía el motivo — ahora dice "Anulado antes del desembolso por [nombre] el [fecha y hora]. Motivo: ..." (`ControladorFormalizacionServlet` resuelve `anuladoPor` igual que ya hace con `cierreDocumentalPor`); (3) el checklist de un préstamo ya `ANULADO_ANTES_DESEMBOLSO` seguía mostrando "Documentación completa — listo para desembolso" (falso) — ahora ese aviso puntual se oculta con ese estado, sin tocar la lista de documentos con sus "Ver". `mvn test` 686 en verde (sin pruebas nuevas: los 3 cambios son de pantalla/wiring, no de reglas de negocio). | *(pendiente)* | — |
| 11/10/2026 | Implementadas las 3 decisiones de Joel: motivo obligatorio con mínimo de 10 caracteres (`MotivoAnulacionInsuficienteException`, advertencia), carrera de dos pestañas corregida (`UPDATE` condicionado + `PrestamoNoPendienteDesembolsoException` con quién y cuándo, en `anularAntesDeDesembolso` y `confirmarDesembolso`), botón "Sí, anular" (ya existía desde el Lote 1). 4 pruebas unitarias nuevas del motivo + 4 del mensaje (2 en `ServicioFormalizacionTest`, 2 en `ServicioDesembolsoTest`), `mvn test` 681 en verde | *(pendiente)* | — |
| 24-25/09/2026 | Implementación base de formalización (préstamo, plan, checklist, firmados, anulación), junto con clientes/documentos/buró/cotizador (E3/E5) | `acb12bb` | — |
| (sin fecha exacta) | D7: agrega el perfil ADMINISTRADOR, con acceso también a anular | `30c152a` | — |
| 11/10/2026 | Documento reconstruido, sin cambios de código | *(este documento)* | — |

## 9. Pruebas realizadas en pantalla

**12/10/2026 (Joel), CC-2026-00002 (Rodrigo, checklist completo, sin desembolsar)** — todo
verificado en pantalla:
- Motivo "prueba" (menos de 10 caracteres reales): el servidor rechazó con la advertencia
  "Explique el motivo de la anulación con al menos 10 caracteres" y "Entendido"; el préstamo
  siguió en Pendiente de desembolso. *(Encontrado: la pantalla no frenaba esto antes de la
  confirmación — corregido, ver §8 y §10.)*
- Motivo válido → "Sí, anular" → "Préstamo anulado" con "Aceptar"; estado "Anulado antes del
  desembolso"; franja con el motivo. *(Encontrado: la franja no decía quién ni cuándo —
  corregido.)*
- Ya no se ofrecen "Re-emitir", "Anular" ni la carga de documentos.
- No aparece en la bandeja de Formalización ni en Desembolsos.
- Codeudor: cerrado como D9 (ver `docs/funcionamiento/RF05_solicitud.md` §7).
- *(Encontrado, corregido: el checklist seguía diciendo "Documentación completa — listo para
  desembolso" con el préstamo ya anulado — era falso.)*

**12/10/2026 (Joel), reprueba de las 3 correcciones — VERIFICADO EN PANTALLA**, con CC-2026-00002:
- Franja: "Anulado antes del desembolso por Joel Jesús Acosta el 12/10/2026 00:11. Motivo: ..."
- Checklist: ya no muestra "Documentación completa — listo para desembolso".
- Mecanismo genérico de `pie.jsp` (`checkValidity()` antes de confirmar): sin regresión —
  probado también en `/caja`, "Marcar revisada" con el campo vacío → "Falta un dato" sin pedir
  confirmación antes.
- Único punto pendiente: el bloqueo EN PANTALLA del motivo corto (CC-2026-00002 ya quedó
  anulado, no había otra operación a mano en ese momento) — ver §10.

**13/10/2026 (Joel), después de Clean and Build — Tanda 1 de mejoras pendientes:**
- `/desembolso/2` (CC-2026-00002, anulado): la ficha ya dice "Este préstamo fue anulado antes
  del desembolso el 12/10/2026 00:11." en vez de "Este préstamo no tiene comprobante de
  desembolso registrado." — confirma el cambio de `desembolso/ficha.jsp` (ver sección 10,
  resuelto).

**13/10/2026 (Joel), después de Clean and Build — corrección del resumen del desembolso
(aprobada):** `/desembolso/2` (CC-2026-00002, anulado) ya NO muestra la fila "Monto entregado en
efectivo" en "Resumen del desembolso" — queda solo "Monto aprobado" y "Deducciones". **APROBADO.**

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`:
`/desembolso/2` (anulado) ahora encabeza la tarjeta "Resumen del préstamo" (antes "Resumen del
desembolso"), y sigue sin la fila "Monto entregado en efectivo". **OK.**

## 10. Pendientes y trabajo futuro

- ~~**(13/10/2026, Joel, Tanda 2 — anotar, no implementar)**: en `/desembolso/{id}` de un
  préstamo `ANULADO_ANTES_DESEMBOLSO`, la tarjeta sigue encabezada "Resumen del desembolso" —
  debería decir "Resumen del préstamo"~~ — **resuelto y probado en pantalla el 14/10/2026** (ver
  §9).
- **(12/10/2026, Joel) Pendiente de pantalla**: el bloqueo del motivo corto en la pantalla de
  anular (cartel "Falta un dato" ANTES de la confirmación "¿Anular este préstamo...?", sin que
  llegue a mostrarse) se verifica con la próxima operación `PENDIENTE_DESEMBOLSO` que haya —
  server-side ya está probado (§9), solo falta repetir el intento EN PANTALLA con un motivo
  corto en un préstamo real.
- **Reprueba de la carrera de dos pestañas** (implementada, sin probar en pantalla): con dos
  pestañas sobre el mismo préstamo `PENDIENTE_DESEMBOLSO`, anular en una y confirmar el
  desembolso en la otra casi al mismo tiempo — confirmar que la que pierde ve la advertencia
  "Este préstamo ya fue [anulado/desembolsado] por... el...", y que si pierde la del desembolso,
  no queda ningún comprobante ni movimiento de caja.
- ~~**La ficha de desembolso de un préstamo anulado** (si la cajera entra directo a
  `/desembolso/{id}`) cae en la misma rama que "ya desembolsado" (`yaDesembolsado`, pensada
  para el caso normal) y muestra "Este préstamo no tiene comprobante de desembolso
  registrado."~~ — **resuelto y probado en pantalla el 13/10/2026**: ahora dice "Este préstamo
  fue anulado antes del desembolso el dd/mm/aaaa hh:mm." (alerta gris), distinguiendo el caso
  anulado del caso "todavía no se desembolsó".
- **(12/10/2026, Joel) El préstamo anulado desaparece de TODAS las listas** (bandejas de
  Formalización y Desembolsos, confirmado en la reprueba) — queda sin resolver si eso es
  correcto para siempre o si en algún momento hace falta encontrarlo desde algún lado que no sea
  la ficha directa (`/formalizacion/{id}`). **Confirmar en RF32** (el RF de informes/reportes,
  todavía sin analizar en este repaso) si existe o va a existir un informe donde un préstamo
  `ANULADO_ANTES_DESEMBOLSO` se pueda encontrar (ej. un listado de préstamos por estado). Sin
  implementar nada en esta tanda — solo queda anotado.

## 11. Guion para la defensa

1. Abrir la ficha de CC-2026-00002 (Rodrigo), con el checklist completo y sin desembolsar.
2. Como oficial: confirmar que NO aparece el botón "Anular préstamo".
3. Como gerente: hacer clic en "Anular préstamo", intentar con un motivo corto (menos de 10
   caracteres) y confirmar que la pantalla lo frena con el cartel propio. Escribir un motivo
   válido (10+ caracteres), confirmar con el cartel ("¿Anular este préstamo antes del
   desembolso? No se puede deshacer.", botón "Sí, anular").
4. Mostrar el estado "Anulado antes del desembolso" con el motivo, en la misma ficha.
5. Ir a `/desembolso` (bandeja de la cajera) y mostrar que CC-2026-00002 ya no aparece.
6. Mostrar en la auditoría el evento `ANULAR_PRESTAMO_PRE_DESEMBOLSO` con el motivo en el
   detalle.
7. (Si hay forma de probarlo con dos pestañas) Carrera anular vs. desembolsar: mostrar la
   advertencia "Este préstamo ya fue ... por ... el ..." en la pestaña que pierde, y confirmar
   que no quedó ningún comprobante/movimiento de caja huérfano.
