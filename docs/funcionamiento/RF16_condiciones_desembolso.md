# RF16 — Las 4 condiciones para desembolsar

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf16-probado`.** Las 4
condiciones (checklist, documentos vigentes, caja del día abierta, saldo suficiente) quedaron
confirmadas en pantalla, incluida la secuencia completa "sin caja → saldo insuficiente →
entrega del gerente → las 4 en verde" con CC-2026-00001. Quedan 4 mejoras menores (sección 10,
a-d, compartidas con RF15), que NO se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF16 (módulo Desembolso y caja):

> El sistema debe permitir el desembolso solo si el checklist de firmados está completo, los
> documentos están vigentes, la caja del día está abierta y el saldo de la caja es suficiente.

RF16 comparte pantalla con RF17 (comprobante de desembolso) y depende directamente de RF13
(checklist), RF11/RF12 (documentos generados) y RF15 (caja abierta).

## 2. Cómo funciona

Las 4 condiciones se calculan en el `GET /desembolso/{idPrestamo}`
(`ControladorDesembolsoServlet.mostrarFicha`, líneas 223-249) y se muestran TODAS JUNTAS en la
card "Condiciones para desembolsar" (`desembolso/ficha.jsp`, una viñeta por condición, ✓ o en
rojo) — y se vuelven a revalidar, una por una, dentro de `ServicioDesembolso.confirmarDesembolso`
cuando la cajera efectivamente envía el formulario (ver sección 6f para el detalle de cada una).
La card "Confirmar desembolso" ni siquiera se renderiza mientras falte alguna de las 4 (más dos
condiciones adicionales de RF17, ver sección 6f).

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `DESEMBOLSAR` (o equivalente, ver nota) | PRESTAMO/DESEMBOLSO | Se confirmó el desembolso |

*(No se encontró en este análisis el nombre exacto del evento de auditoría de
`confirmarDesembolso` más allá de lo ya visto en RF13; se deja para cuando se analice RF17 en
detalle, que es donde termina ese método.)*

## 4. Dónde está

- `servicios/ServicioDesembolso.java` — `confirmarDesembolso` (el método que de verdad aplica
  las 4 condiciones, dentro de una sola transacción), `validarDocumentosVigentes`,
  `obtenerContratoGeneradoVigente`, `construirMensajeSaldoInsuficiente`.
- `servicios/ServicioCaja.java` — `obtenerSesionAbierta`, `esDeHoy`, `calcularSaldoTeorico`.
- `servicios/ServicioFormalizacion.java` — `esChecklistCompleto`, `obtenerChecklist` (RF13).
- `controladores/ControladorDesembolsoServlet.java` (`GET`/`POST /desembolso/{id}`).
- `webapp/WEB-INF/vistas/desembolso/ficha.jsp`.
- `dao/DesembolsoDAO.java`, `dao/MovimientoCajaDAO.java` (`sumarMovimientos`).
- Tabla `desembolsos` (`identificador_confirmacion UNIQUE`, índice único parcial
  `ux_desembolsos_un_confirmado_por_prestamo`), parámetro `formalizacion.dias_validez_documentos`.

## 5. Dependencias

- **Depende de**: RF13 (checklist completo), RF11/RF12 (documentos generados, su fecha de
  emisión), RF15 (caja abierta y del día).
- **Lo afecta un cambio en**: `formalizacion.dias_validez_documentos` (compartido con la
  revalidación de RF11 al formalizar/regenerar); `MovimientoCajaDAO.sumarMovimientos` (fórmula
  única del saldo teórico, compartida con el arqueo de cierre, RF15).
- **A quién afecta un cambio acá**: RF17 (comprobante de desembolso, continúa exactamente donde
  termina `confirmarDesembolso`); el arqueo de caja (cada desembolso es un movimiento más).

## 6. Análisis

### 11/10/2026 — revisión completa de RF16 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioDesembolso.confirmarDesembolso` y los métodos que
llama, `ControladorDesembolsoServlet.mostrarFicha`, `desembolso/ficha.jsp`,
`MovimientoCajaDAO.sumarMovimientos`, `bd/01_esquema.sql` (tabla `desembolsos`),
`bd/02_indices.sql` (índice único parcial).

### f) Dónde se controla cada condición (servidor, dentro de la transacción)

`ServicioDesembolso.confirmarDesembolso` las revisa en este orden fijo, cada una con un
`if (...) throw new NegocioException(...)` — la primera que falla corta ahí (no sigue
revisando las demás):

1. **Checklist completo** (línea 130-133): `servicioFormalizacion.esChecklistCompleto(obtenerChecklist(...))`
   — exactamente lo mismo que RF13, sin duplicar la lógica. Mensaje: *"Faltan documentos
   firmados; complete el checklist antes de desembolsar."*
2. **Documentos vigentes** (línea 134-140): **¿qué significa "vigentes" acá?** — ÚNICAMENTE la
   antigüedad del `CONTRATO_GENERADO` (el PDF que arma el sistema, RF12), comparando su
   `fecha_documento` (cuándo se emitió la última versión vigente — se refresca al regenerar)
   contra el parámetro `formalizacion.dias_validez_documentos` (hoy 5 días). **No tiene nada
   que ver** con la cédula de identidad vencida, la autorización de consulta al buró vencida, ni
   con que un documento haya sido reemplazado por una versión más nueva (eso es justamente lo
   esperado: siempre se mira la VERSIÓN VIGENTE, nunca una reemplazada) — esos 3 conceptos
   pertenecen a otros RF (RF03/RF04, al crear/enviar la solicitud) y no se vuelven a revisar
   acá. Mensaje: *"Los documentos generados vencieron (más de N días sin desembolsar);
   regénerelos antes de desembolsar."*
3. **Caja del día abierta** (línea 142-150): dos chequeos — `sesion == null` → *"No hay una caja
   abierta."*; si hay una pero no es de hoy (`ServicioCaja.esDeHoy`) → el mismo mensaje de RF15,
   *"La caja del dd/mm/aaaa sigue abierta: ciérrela y rinda antes de operar hoy."*
4. **Saldo suficiente** (línea 151-156): `saldoTeorico = sesion.getSaldoInicial() +
   MovimientoCajaDAO.sumarMovimientos(...)`. La fórmula real, en la base, es genérica:
   `SUM(monto * signo)` sobre TODOS los movimientos de la sesión (entregas del gerente signo
   +1, cobros/pagos +1, vueltos −1, desembolsos −1, compensaciones según corresponda) — la
   descripción de Joel ("fondo + entregas del gerente + cobros − desembolsos") es exactamente
   el caso típico de esa fórmula general, antes de que existan vueltos o compensaciones en la
   sesión. Si `saldoTeorico < montoNeto` (el monto aprobado del préstamo, sin deducciones —
   RN-08/RN-17), rechaza con un mensaje que incluye los 3 montos
   (`construirMensajeSaldoInsuficiente`).

Después de estas 4 (que son las del texto de RF16), el método sigue con 2 condiciones MÁS, de
RF17, no de RF16: el comprobante de desembolso ya impreso al menos una vez (línea 162-164) y la
casilla de "verifiqué la cédula y entregué el efectivo" (línea 110-112, antes incluso de todo lo
anterior) — se mencionan acá solo para que quede claro que desembolsar de punta a punta necesita
más que las 4 de RF16, aunque esas 2 pertenecen al documento de RF17.

### g) Qué ve la cajera

- **En la pantalla (GET), antes de intentar nada**: TODAS JUNTAS — la card "Condiciones para
  desembolsar" muestra las 4 (más las vigentes de RF17) en una sola lista, cada una con su
  propia viñeta ✓/✗ y, debajo, un aviso amarillo puntual por cada una que falte (ej. "Faltan
  documentos firmados cargados.", "No hay caja abierta.") — puede haber varios avisos
  amarillos a la vez, uno por cada condición pendiente.
- **Si se fuerza el envío** (`POST`) con más de una condición faltando: el mensaje del
  SERVIDOR es el de la PRIMERA que falla en el orden fijo de arriba (1→2→3→4) — no junta los 4
  mensajes en uno solo. La pantalla (GET, después del redirect) vuelve a mostrar la lista
  completa igual, así que la cajera termina viendo todas de todos modos, solo que el cartel de
  error puntual nombra una sola.

### h) Doble desembolso de la misma operación

Dos mecanismos, uno para cada escenario:
1. **Mismo formulario, doble clic o F5** (mismo `identificador_confirmacion`, un UUID generado
   una vez por cada `GET` de la ficha y repetido en un campo oculto): `confirmarDesembolso`
   busca primero si esa clave ya se usó (`desembolsoDAO.obtenerPorIdentificadorConfirmacion`) —
   si sí, no inserta nada de nuevo y devuelve el préstamo tal cual, sin error (mismo patrón de
   idempotencia que ya usa el resto del sistema para doble clic).
2. **Dos pestañas abiertas por separado** (dos `GET` distintos, cada uno con SU PROPIO UUID
   nuevo): si la primera ya terminó (el préstamo pasó a `ACTIVO`), la segunda falla en el
   chequeo `prestamo.getEstadoPrestamo() != PENDIENTE_DESEMBOLSO` → *"Este préstamo ya no está
   pendiente de desembolso."* — pero, **hallazgo**: `PrestamoDAO.marcarDesembolsado`
   (`UPDATE prestamos SET estado_prestamo = 'ACTIVO', fecha_desembolso = ? WHERE id_prestamo =
   ?`) es un `UPDATE` **sin condición sobre el estado anterior** — el mismo patrón que ya se
   encontró y se dejó "solo listado, sin tocar" en el análisis de RF09 (`RF09_aprobar.md` §12,
   `marcarDesembolsado` aparece ahí explícitamente). Si dos peticiones llegan a la vez (carrera
   real, no el caso secuencial de arriba) y las dos leen el préstamo todavía
   `PENDIENTE_DESEMBOLSO` antes de que ninguna confirme, las dos podrían llegar al `INSERT` de
   `desembolsos`. **El respaldo final es el índice único parcial
   `ux_desembolsos_un_confirmado_por_prestamo`** (`ON desembolsos (id_prestamo) WHERE
   estado_desembolso = 'CONFIRMADO'`, `bd/02_indices.sql:23-25`): la segunda transacción que
   intente insertar un segundo desembolso `CONFIRMADO` para el mismo préstamo choca con la
   base, nunca queda un duplicado — igual que ya se documentó para el número de operación único
   (RF12) y la carrera de dos pestañas de aprobar/rechazar (RF09/RF10). El mensaje que recibe
   la segunda cajera en ESE caso puntual (la carrera real) es el genérico "No se pudo completar
   la operación. Intente nuevamente.", no uno específico — mismo matiz que ya se señaló en
   RF09/RF10/RF12/RF15 para este mismo tipo de patrón.

### i) Secuencia de prueba propuesta (respuesta a Joel)

La secuencia que propone **es posible tal cual**, con una aclaración:

1. Intentar desembolsar CC-2026-00001 sin caja abierta → bloqueado, "No hay una caja abierta."
   — confirmado por el código (sección 6f, punto 3).
2. Abrir caja con fondo 1.000.000 → el saldo teórico (1.000.000) es menor que el monto del
   préstamo (5.000.000) → bloqueado con el mensaje de saldo insuficiente con los 3 montos.
3. **Entrega de efectivo del gerente**: se registra en `/caja` (la misma pantalla de RF15),
   card "Entregar efectivo a la caja" — **solo la ve y la puede usar el gerente** (o el
   administrador), nunca la cajera (decisión de separación de funciones, `ServicioCaja.registrarEntregaGerente`
   exige el perfil GERENTE_ADMINISTRADOR/ADMINISTRADOR, y la card ni se muestra si el usuario no
   es gerente). Con una entrega de 4.000.000 (o más), el saldo teórico llega a 5.000.000 y
   la condición 4 queda ✓.
4. Desembolsar: en este punto las 4 condiciones de RF16 ya están OK, pero **todavía faltan 2
   pasos de RF17** antes de que el botón "Confirmar desembolso" se habilite de verdad: imprimir
   el comprobante de desembolso para firma (al menos una vez) y tildar la casilla de
   verificación de cédula/entrega de efectivo — por eso Joel ya anotó correctamente "ya entra
   RF17" al final de este paso.

**Para "documentos vigentes", ¿se puede probar sin dañar los datos de Mirtha?** Sí, pero no
con CC-2026-00001 directamente hoy mismo: su `CONTRATO_GENERADO` se emitió HOY, así que aunque
se bajara `formalizacion.dias_validez_documentos` a 0 desde Parámetros, la condición
`!hoy.isAfter(fechaDocumento.plusDays(0))` sigue siendo `!hoy.isAfter(hoy)` = verdadero (sigue
vigente) — el parámetro en 0 recién bloquearía A PARTIR DE MAÑANA, no hoy. Para demostrarlo hoy
sin tocar los datos de Mirtha, la opción limpia sería usar **otra** operación de prueba
(formalizar una solicitud distinta) y, en `sgcpc_db_pruebas` únicamente, adelantar la
`fecha_documento` de SU `CONTRATO_GENERADO` a varios días atrás con un script chico — mismo
criterio y mismas salvaguardas que `pruebas/rf15_caja_dia_anterior.sql` (ver `RF15_apertura_caja.md`
§6e). No se escribió ese script en esta vuelta porque Joel no lo pidió explícitamente para este
caso — si lo quiere, se puede preparar igual (propuesto, sin ejecutar) en la próxima vuelta.

### j) Cumplimiento

RF15 y RF16 se cumplen **sin excepción**: la cajera puede abrir la caja del día con un fondo
inicial (RF15), no puede operar mientras quede abierta una de otro día (bloqueado en el
servidor para entregar efectivo y desembolsar, la única salida es cerrarla) y el desembolso
exige las 4 condiciones exactas del texto de RF16, todas revalidadas dentro de la misma
transacción que registra el desembolso. No se encontró ninguna parte de los dos textos sin
cumplir.

Dos observaciones, ninguna es un incumplimiento:
1. El fondo inicial de la apertura no deja un segundo registro de "quién lo entregó
   físicamente" (RF15 §2a) — el texto no lo pide.
2. `PrestamoDAO.marcarDesembolsado` tiene el mismo patrón de `UPDATE` sin guarda de estado que
   ya se encontró (y se dejó sin tocar, a propósito) en RF09 — el índice único de la base es la
   garantía real de que nunca queda un desembolso duplicado, aunque el mensaje de error en la
   carrera real (poco frecuente) sea el genérico en vez de uno específico.

## 7. Decisiones

No se encontraron hallazgos que exijan programar algo — RF16 cumple el texto firmado tal cual
ya estaba implementado. Confirmado en pantalla el 11/10/2026 (sección 9). Quedan 4 mejoras
menores (sección 10, a-d, compartidas con RF15), anotadas para una próxima vuelta.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B191** (tanda 2): la bandeja de `/desembolso` con **contador**, **encabezados que ordenan** y **paginación de 20**. Tiene un solo listado, así que usa los parámetros de siempre (`orden`/`sentido`/`pagina`), su paginación pasa por `listado-vivo.js` y el fragmento de paginación va dentro de `_resultados.jsp` — igual que Clientes, Solicitudes y Aprobaciones. **El orden lo resuelve el mismo método que la sección "pendientes de desembolso" de Formalización** (`ServicioFormalizacion.ordenarPrestamosDeBandeja` con `SeccionBandeja.DESEMBOLSO`): misma entidad y las mismas cuatro columnas, así que las dos pantallas ordenan igual sin una segunda copia de la regla. `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 15/11/2026 | **B186 + B189 + B190** (tanda 1 de la revisión UX de Desembolso y caja). **B186**: el aviso del estado de la caja (*"La caja del dd/mm/aaaa sigue abierta: ciérrela y rinda antes de operar hoy."* y *"No hay caja abierta"*) ahora está **arriba de la bandeja** `/desembolso`, no solo dentro de la ficha — antes la lista ofrecía *"Desembolsar"* y el bloqueo aparecía un clic después. Ningún texto nuevo: el dueño sigue siendo `ServicioCaja.mensajeCajaDeOtroDia`, y el controlador repite lo que ya hacía `mostrarFicha` (`obtenerSesionAbierta` + `esDeHoy`). Va en `lista.jsp`, fuera del fragmento de resultados, porque no depende de la búsqueda. **B189**: el *"Volver a la bandeja"* de la ficha conserva la búsqueda (`RETORNO_BANDEJA_EN_SESION`, mecanismo de B147/B163/B170/B180) — importa especialmente acá, porque el redirect de después de confirmar el desembolso vuelve a `/desembolso` sin parámetros. **B190**: el encabezado de la ficha dice también el nombre del cliente, además del N.º de operación. `mvn test` 1017/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 11/10/2026 | RF14: `confirmarDesembolso` ahora exige `PrestamoDAO.marcarDesembolsado` con `UPDATE` condicionado a `PENDIENTE_DESEMBOLSO` — si un gerente anuló el préstamo mientras se confirmaba el desembolso (carrera de dos pestañas), se muestra "Este préstamo ya fue anulado por... el..." (advertencia) y se hace rollback completo (ni el comprobante ni el movimiento de caja quedan registrados) — ver [RF14_anular_prestamo.md](RF14_anular_prestamo.md) §6f | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): las 4 condiciones también exigidas al imprimir el comprobante previo a la firma (A.5), saldo negativo en rojo (A.4), "caja de otro día" como advertencia (A.8) | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con CC-2026-00001: sin caja, saldo insuficiente, entrega del gerente, 4 condiciones ✓ | *(pendiente)* | `rf16-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, cajera Sofía Martínez, con CC-2026-00001:**
- **Sin caja abierta**: las 4 condiciones mostradas juntas, caja "No disponible", saldo
  arrastrado como insuficiente también, aviso *"No hay caja abierta. Ir a caja."* y **sin el
  botón de confirmar** — confirma la sección 2/6g (todas juntas en pantalla).
- **Caja abierta con fondo 1.000.000** (insuficiente para los 5.000.000 del préstamo): *"Saldo
  Insuficiente — faltan 4.000.000 Gs."* y *"Pida al gerente que entregue efectivo."* — confirma
  `construirMensajeSaldoInsuficiente` (sección 6f, punto 4) con los montos exactos.
- **Entrega del gerente** (administrador) de 4.000.000: movimiento "Entrega del gerente"
  registrado, saldo teórico sube a 5.000.000 — confirma `registrarEntregaGerente` (separación
  de funciones: la cajera no tiene esa opción, sección 6i).
- **Las 4 condiciones en verde**: aparece la card "Confirmar desembolso", con la casilla de
  verificación deshabilitada hasta imprimir el comprobante (RF17) — confirma que RF16 por sí
  solo no alcanza para desembolsar, falta RF17. **No se confirmó el desembolso en esta
  vuelta** (queda para cuando se cierre RF17).

**Resultado: RF16 queda probado completo.** 4 mejoras menores encontradas junto con RF15
(sección 10, a-d), NO corregidas en esta vuelta.

## 10. Pendientes y trabajo futuro

- **(20/10/2026, tanda de usabilidad, punto 6 paso 5)**: la bandeja (`desembolso/lista.jsp`) ya
  tiene buscador con AJAX (`listado-vivo.js`), por N.º de operación, cédula o nombre del cliente
  -- reutiliza `ServicioFormalizacion.filtrarPrestamosPorTexto` (ya existía desde el paso 4) sin
  tocar ningún servicio (el controlador ya tenía `ServicioFormalizacion` como campo).

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 (compartidas con RF15,
mismo pedido de Joel) — **NO corregidas en esta vuelta**:

a) ~~El botón "Imprimir comprobante para firma" (RF17) está disponible aunque no se cumplan las
   4 condiciones de RF16~~ — **corregido el 11/10/2026** (consolidación RF08-RF19, Lote 1, A.5) y
   **confirmado OK en pantalla en la reprueba del 11/10/2026 (Joel), incluido el caso de que solo
   falte el saldo**: `ServicioDesembolso.asegurarCondicionesDesembolso` (las 4 condiciones en un
   solo método, antes solo las revalidaba `confirmarDesembolso`) ahora también la exige
   `generarComprobantePreDesembolso` — bloqueada tanto si se pide por el botón como por la URL
   directa del PDF.
b) ~~En la pantalla de Caja, "Saldo teórico después" muestra un número negativo~~ — **corregido
   el 11/10/2026** (A.4) y **confirmado OK en pantalla** ("Faltan 3.000.000 Gs." en rojo).
c) La entrega de efectivo del gerente queda con comprobante "-" — evaluar un comprobante de
   entrega con firma del gerente y la cajera (corresponde a RF18). Sin tocar en esta tanda.
d) Confirmaciones con texto explícito (`data-confirmar-boton`): "Sí, abrir caja", "Sí, cerrar
   caja" y "Sí, entregar" — las 3 ya estaban implementadas (ver `RF15_apertura_caja.md` §10). De
   paso, el bloqueo "caja de otro día sigue abierta" (`registrarEntregaGerente`/
   `asegurarCondicionesDesembolso`) ahora lanza `CajaDeOtroDiaException` y se muestra como
   advertencia, no error (A.8, mismo criterio que RF11 "fuera de plazo") — pendiente de
   reprueba.
- Si Joel quiere probar "documentos vigentes" sin esperar a mañana, preparar (propuesto, sin
  ejecutar, mismo criterio que `pruebas/rf15_caja_dia_anterior.sql`) un script equivalente para
  adelantar la `fecha_documento` de OTRA operación de prueba (no CC-2026-00001).

- **RF14, carrera anular vs. desembolsar (11/10/2026)**: implementada, sin reprueba en pantalla
  todavía — ver [RF14_anular_prestamo.md](RF14_anular_prestamo.md) §10.

**Detalle menor de la reprueba del 11/10/2026 (Joel) — se corrige junto con el Lote 2:**

c) En `/desembolso/{id}`, el enlace "Ir a formalización" (avisos de "documentos vencidos o no
   existen" y "faltan documentos firmados cargados") — **revisado el código, el `href` ya
   apunta a la ficha del préstamo** (`/formalizacion/<idPrestamo>`, `desembolso/ficha.jsp` líneas
   ~127/131), no a la bandeja (`/formalizacion`). La ruta y el permiso (`GENERAR_DOCUMENTOS_FORMALIZACION`,
   NINGUNO para la cajera) tampoco redirigen a la bandeja ante un rechazo — un 403 directo. No
   se encontró, en el código, una forma de que ese enlace lleve a la bandeja. **Pendiente:
   confirmar con qué perfil y en qué paso exacto se vio la bandeja** (si fue como cajera, es
   esperable un 403 "No tiene permiso", no la bandeja — puede ser esa pantalla la que se
   confundió con la bandeja) para poder reproducirlo antes de tocar nada en el Lote 2.

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con CC-2026-00001 — salvo el paso final de
"Confirmar desembolso" (requiere RF17, todavía no probado).**

1. Mostrar la card "Condiciones para desembolsar" con las 4 viñetas, todas en rojo al principio.
2. Abrir caja con un fondo insuficiente a propósito — mostrar el mensaje con los 3 montos.
3. Registrar la entrega del gerente (mostrar que la cajera no tiene esa opción) y que el saldo
   teórico sube.
4. Mostrar que, aun con las 4 condiciones de RF16 en verde, "Confirmar desembolso" sigue sin
   aparecer hasta imprimir el comprobante (RF17).
5. Mostrar el índice único `ux_desembolsos_un_confirmado_por_prestamo` como la garantía final
   contra un desembolso duplicado, más allá del mensaje de pantalla.
