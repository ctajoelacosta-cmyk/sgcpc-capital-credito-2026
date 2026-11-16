# Escena 04 — Formalización

Qué cubre: generación de contrato/pagaré/plan, re-emisión y regeneración de los PDF, checklist
de firmados, y anulación antes del desembolso. Contenido migrado de
`docs/funcionamiento/FLUJO_OPERATIVO.md` (retirado el 21/10/2026) — ver plantilla completa en
[00_LEEME.md](00_LEEME.md).

**Tramo 3 del recorrido, iniciado en pantalla por Joel el 21/10/2026, rol oficial, justo
después de la aprobación del tramo 2** (`SOL-2026-0001` ya `APROBADA`, ver
[03_aprobacion.md](03_aprobacion.md)). Cliente de ejemplo ficticio: **Marta Ovelar (C.I.
1.000.001)**. Hoy se formalizó: panel, pantalla de entrada a Formalización, pantalla de
condiciones, confirmación y generación real de los 3 documentos (préstamo nuevo
`PENDIENTE_DESEMBOLSO`). Cargar los firmados en el checklist queda para la próxima sesión.

## Paso 1 — Panel de la oficial después de la aprobación (RF30)

- **Usuario/rol**: Oficial (en su propia ventana, sin volver a loguearse).
- **Pantalla y ruta**: `/panel`.
- **Qué hace el usuario**: vuelve al panel después de que el gerente aprobó la solicitud.
- **Qué responde el sistema**: "Mis solicitudes en curso: 0" — la solicitud ya `APROBADA` deja
  de contar (`ServicioPanel.calcularIndicadores` solo cuenta `BORRADOR` y
  `PENDIENTE_APROBACION` para ese indicador). **Ningún aviso de que `SOL-2026-0001` fue
  aprobada** — nada en el panel, ni en ningún otro lugar, le informa a la oficial que ya puede
  formalizar.
- **Regla de negocio**: ver [RF30_panel.md](../funcionamiento/RF30_panel.md).
- **Qué sigue**: la oficial tiene que acordarse sola de entrar a Formalización (Paso 2) — no
  hay ningún empuje del sistema.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena, y el principio general en
  [00_LEEME.md](00_LEEME.md) § Hallazgos generales.

## Paso 2 — Pantalla /formalizacion: solicitudes listas para formalizar (RF11)

- **Usuario/rol**: Oficial.
- **Pantalla y ruta**: `/formalizacion`.
- **Qué hace el usuario**: entra desde el menú **Formalización**.
- **Qué responde el sistema**: subtítulo "Solicitudes aprobadas, listas para formalizar
  (generar contrato, pagaré y plan de pagos)."; un buscador de texto libre que filtra las 3
  secciones a la vez; 3 secciones con "listado vivo" — la primera (solicitudes aprobadas, con
  el botón **"Formalizar"**, `SOL-2026-0001` ya aparece ahí) sin título propio; "Préstamos
  pendientes de desembolso" (`<h2>`); "Préstamos finalizados — cierre documental pendiente"
  (`<h2>`).
- **Regla de negocio**: ver [RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md).
- **Qué sigue**: "Formalizar" sobre `SOL-2026-0001` → Paso 3.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: segunda pasada (23/10/2026, 09:23, segundo cliente de práctica)

Como el panel del oficial no avisa nada (B45, ver `03_aprobacion.md`), el oficial entró por el
menú **"Formalización"** → opción **"Formalización"** (la única opción del menú) y llegó a
`/formalizacion`. Misma pantalla que el 21/10, con `SOL-2026-0002` en la primera sección en vez
de `SOL-2026-0001`:

- Título **"Formalización"**, descripción **"Solicitudes aprobadas, listas para formalizar
  (generar contrato, pagaré y plan de pagos)."**; buscador **"N.º de solicitud/operación,
  cédula o nombre del cliente"** con **"Limpiar"**/**"Buscar"**.
- Primera sección (sin título propio, ver el hallazgo ya anotado): tabla con **Código**,
  **Aprobada** (fecha), **Cliente**, **Monto aprobado**, **Plazo** y el botón **"Formalizar"**
  — una sola fila, `SOL-2026-0002`, aprobada el 22/10/2026.
- **"Préstamos pendientes de desembolso"** (*"Ya formalizados; el checklist de firmados debe
  estar completo antes de poder desembolsar."*): columnas N.º de operación, Cliente, Monto,
  Formalizado, Checklist — **vacía** (el préstamo de `SOL-2026-0001` ya pasó de ahí, está
  `ACTIVO` y desembolsado).
- **"Préstamos finalizados — cierre documental pendiente"**: columnas N.º de operación,
  Cliente, Monto — **vacía** (ningún préstamo llegó todavía a `FINALIZADO`).

**Diferencia con lo registrado el 21/10/2026**: la pantalla es idéntica en estructura y
textos — la única diferencia real es el contenido de las 3 secciones, que refleja el avance del
circuito desde entonces (la solicitud del primer cliente ya no aparece en ninguna de las 3:
pasó por "para formalizar" → "pendientes de desembolso" → ya desembolsada y `ACTIVA`, fuera de
las 3 listas de esta bandeja).

**Verificado en el código (23/10/2026)**:

- **Qué lista cada sección y con qué método** (`ControladorFormalizacionServlet.mostrarLista`):
  - Sección 1: `ServicioFormalizacion.listarParaFormalizar` → delega directo en
    `SolicitudDAO.listarAprobadasSinFormalizar` (`estado_solicitud = 'APROBADA' AND NOT
    formalizada`, más antiguas primero) — el mismo método que ya se documentó como la fuente
    real de esta bandeja en `03_aprobacion.md` (B45) y que **no se reutiliza en ningún panel**.
  - Sección 2: `ServicioFormalizacion.listarPendientesDesembolso` → `PrestamoDAO.
    listarPendientesDesembolso` (`estado_prestamo = 'PENDIENTE_DESEMBOLSO'`, ordenado por
    `fecha_formalizacion`) — mismo método que ya alimenta el indicador del Panel de la cajera/
    gerente y la bandeja de `/desembolso`.
  - Sección 3: `ServicioFormalizacion.listarFinalizadosConCierrePendiente` →
    `PrestamoDAO.listarFinalizadosConCierrePendiente`.
- **Qué es el "cierre documental pendiente" y cuándo aparece un préstamo ahí**: un préstamo
  entra a esta sección cuando `estado_prestamo = 'FINALIZADO'` (RN-12: ya no le queda ninguna
  cuota pendiente, está totalmente pagado) **y** `estado_cierre_documental <> 'COMPLETO'` (los
  valores posibles son `PENDIENTE`, `COMPLETO` y `NO_APLICA`). "Cierre documental" es un trámite
  aparte, posterior a que el préstamo ya esté saldado: la devolución física del pagaré firmado
  al cliente (y, si corresponde, una constancia de esa devolución) — no tiene nada que ver con
  el desembolso ni con el checklist de firmados de la sección 2. Hoy está vacía porque ningún
  préstamo de práctica llegó todavía a `FINALIZADO` (el de `SOL-2026-0001` sigue `ACTIVO`, recién
  desembolsado).

Ver B46 y B47 en Hallazgos.

## Paso 3 — Formalizar SOL-2026-0001: condiciones aprobadas y generación de documentos (RF11)

- **Usuario/rol**: Oficial, ventana normal de Chrome.
- **Qué hace el usuario**: toca **"Formalizar"** sobre `SOL-2026-0001` desde la bandeja
  (Paso 2).
- **Pantalla y ruta**: `GET /formalizacion/nueva/{id}`, título "Formalizar SOL-2026-0001".

**Tarjeta "Condiciones aprobadas (no editables)"**:
- Cliente y C.I.; monto aprobado (3.000.000 Gs., con el monto también en letras, debajo en
  texto gris); plazo (12 meses); TEA / TNA; cuota más alta; total a pagar; CFT; afectación
  actual, con su límite entre paréntesis (en rojo si lo superara — no fue el caso hoy).
- Si el cliente no presentara comprobante de ingreso, aparecería una fila roja adicional
  ("Comprobante de ingreso") — no se mostró hoy, Marta Ovelar sí presenta comprobante.
- **La afectación mostrada NO es la que quedó congelada al aprobar — se recalcula en este
  momento, con los datos ACTUALES del cliente** (segundo control de RF11, independiente del
  control de RF06 al enviar/aprobar): verificado en
  `ServicioFormalizacion.calcularResumenParaMostrar` → `calcularCapacidadActualFormalizacion`,
  que vuelve a leer `personas_laborales` (ingreso neto comprobado, cuota de otras deudas) y
  compara contra el parámetro `credito.limite_afectacion` — la cuota más alta/total/CFT, en
  cambio, no cambian con la fecha elegida (el motor calcula por período secuencial, no por
  fecha calendario real entre cuotas).

**Tarjeta "Generar documentos"**:
- Texto de ayuda: "La fecha aprobada en la solicitud puede haber quedado desactualizada si se
  formaliza días después. Se propone el primer vencimiento un mes desde hoy; puede ajustarla
  dentro del rango permitido."
- Campo **"Primer vencimiento"**, propuesto a un mes desde hoy
  (`ServicioFormalizacion.proponerPrimerVencimiento`, pura: `fechaFormalizacion.plusMonths(1)`
  — con hoy 21/10/2026, propone 21/11/2026); debajo, "Entre 10/11/2026 y 05/12/2026." — el
  rango sale de `ServicioFormalizacion.calcularRangoPrimerVencimiento`, que lee los parámetros
  `formalizacion.dias_min_primer_vencimiento`/`dias_max_primer_vencimiento` (20 y 45 días desde
  hoy, `bd/03_parametros.sql`) y los suma a la fecha de hoy — el mismo rango que ya usan el
  Cotizador y la Solicitud (Escenas 01/02).
- Aviso en vivo si la fecha elegida cae en día no hábil (`fecha-primer-vencimiento.js`): compara
  contra sábado/domingo y contra la tabla `feriados` (cargada al vuelo como JSON en el campo,
  `FeriadoDAO.listarActivos`), con el texto exacto "El [fecha] es sábado/domingo/feriado
  ([descripción]). El primer vencimiento quedará el [día] [fecha]." — ejemplo real de esta regla
  (no probado hoy en pantalla): si se elige un sábado 21/11, avisa que el primer vencimiento
  quedará el lunes 23/11. Es solo informativo — el corrimiento real lo aplica el servidor
  (`ServicioMotorFinanciero.fechaHabil`) al generar. **Corrección (23/10/2026)**: este aviso en
  vivo, con la clase `campo-primer-vencimiento`, **existe únicamente en esta pantalla** —
  verificado que ni `cotizador/formulario.jsp` ni `solicitudes/nueva.jsp` ni
  `solicitudes/ficha.jsp` tienen esa clase; lo que comparten las 3 pantallas con Formalización es
  solo el RANGO de 20-45 días (`validarRangoFechaPrimerVencimiento`/
  `validarPrimerVencimientoFormalizacion`), nunca este aviso ni el corrimiento a día hábil. Ver
  B48.
- Botón **"Generar documentos"** y, debajo de las 2 tarjetas, el enlace **"← Volver a la
  bandeja"** (vuelve a `/formalizacion`).

- **Regla de negocio**: ver [RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md).
- **Qué cambia en la base / auditoría**: nada todavía — esta pantalla es de solo lectura.
- **Qué sigue**: tocar "Generar documentos" → Paso 4 (el cuadro de confirmación).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

El oficial tocó **"Formalizar"** sobre `SOL-2026-0002` desde la bandeja y llegó a
`/formalizacion/nueva/{id}`, título **"Formalizar SOL-2026-0002"**. Igual que con el primer
cliente: tarjeta **"Condiciones aprobadas (no editables)"** (cliente, monto, plazo, TEA/TNA,
cuota más alta, total a pagar, CFT, **afectación actual recalculada: 3,9 %, límite 40,0 %**) y
tarjeta **"Generar documentos"**, con el mismo texto de ayuda sobre la fecha desactualizada.

**Diferencias de esta segunda pasada**: la solicitud tenía un primer vencimiento **estimado**
de **22/11/2026** (congelado al aprobar), pero la pantalla propone **23/11/2026** — un mes
desde **hoy** (23/10/2026), no desde la fecha de la solicitud, porque esta formalización ocurre
**un día después** de la aprobación (22/10/2026). Rango mostrado: **"Entre 12/11/2026 y
07/12/2026."** (20 y 45 días desde el 23/10/2026). **No aparece ningún aviso de día no hábil**
— no porque falte el mecanismo, sino porque **23/11/2026 es lunes**: el valor propuesto ya cae
en un día hábil, así que el aviso en vivo (ver abajo) no tiene nada que mostrar. Botones
**"Generar documentos"** y **"← Volver a la bandeja"**, iguales al 21/10.

**Verificado en el código (23/10/2026) — corrección a lo escrito el 21/10**: el aviso en vivo de
día no hábil (`fecha-primer-vencimiento.js`, clase `campo-primer-vencimiento`) **no existe en
"Nueva solicitud"** (`solicitudes/nueva.jsp`) ni en ninguna otra pantalla — solo en
`formalizacion/nueva.jsp`. Tampoco hay ningún control de día hábil en el servidor al crear o
simular una solicitud: `ServicioSolicitudes.crear`/`aplicarSimulacion` solo llaman a
`validarRangoFechaPrimerVencimiento` (el rango de 20 a 45 días), sin ningún chequeo de
sábado/domingo/feriado — ese control (`validarPrimerVencimientoEfectivoFormalizacion` +
`ServicioMotorFinanciero.fechaHabil`, con el corrimiento automático) existe **solo dentro de
Formalización**. Por eso la fecha propuesta de "Nueva solicitud" puede caer en domingo (como el
22/11/2026 del primer vencimiento estimado de `SOL-2026-0002`) sin que esa pantalla lo note en
ningún momento — recién en Formalización, con una fecha nueva (recalculada desde la fecha real
de formalización, no la de la solicitud), aparecería el aviso si correspondiera. Ver B48, que
cita también el hallazgo ya clasificado (B) sobre las fechas distintas entre el cotizador y la
solicitud en `02_solicitud.md`.

## Paso 4 — Confirmar "Generar documentos" (RF12)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: toca **"Generar documentos"** (botón del Paso 3).
- **Qué responde el sistema**: un cuadro de confirmación con ícono de advertencia (amarillo),
  texto **"¿Generar los documentos de formalización con estas condiciones?"**, botón de
  confirmar **"Sí, generar"** y botón de cancelar **"Cancelar"**. Hoy se tocó **"Sí, generar"**
  — ver el resultado en el Paso 5.
- **Dónde está, verificado en el código**: no es un archivo `.js` aparte ni algo propio de
  `formalizacion/nueva.jsp` — es el mecanismo **genérico** de confirmación que comparte TODO el
  sistema, en `WEB-INF/vistas/plantilla/pie.jsp` (incluido al final de cada pantalla): un
  manejador delegado de jQuery sobre `submit` en cualquier `form[data-confirmar]`, que arma el
  cuadro con **SweetAlert2** (`Swal.fire`). El formulario de `nueva.jsp` (ver Paso 3) trae
  `data-confirmar="¿Generar los documentos de formalización con estas condiciones?"` y
  `data-confirmar-boton="Sí, generar"` — el resto de las opciones del cuadro
  (`icon: 'warning'`, `showCancelButton: true`) son las mismas para cualquier confirmación del
  sistema que no las sobrescriba. **El texto del botón de cancelar ("Cancelar") está fijo en
  `pie.jsp` (`cancelButtonText: 'Cancelar'`) — hoy no existe ningún atributo
  `data-confirmar-...` para cambiarlo por formulario**, a diferencia del título
  (`data-confirmar-titulo`) y del botón de confirmar (`data-confirmar-boton`), que sí son
  personalizables por pantalla.
- **Regla de negocio**: ver [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md).
- **Qué sigue**: "Sí, generar" → Paso 5.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

## Paso 5 — Documentos generados: préstamo nuevo y ficha /formalizacion/{id} (RF11-RF13)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: toca **"Sí, generar"** en el cuadro del Paso 4.
- **Qué responde el sistema**: cuadro de éxito **"Documentos generados — Ahora cargue los
  firmados en el checklist de esta ficha."**, botón **"Aceptar"**. Al aceptar, el sistema
  redirige a `/formalizacion/{id}`: título con el número de operación nuevo (`CC-2026-NNNNN`),
  etiqueta de estado **"Pendiente de desembolso"**; tarjeta **"Condiciones"** (cliente, monto,
  plazo, TEA/TNA, primer vencimiento, formalizado, leyenda de que no hay comisiones ni gastos);
  tarjeta **"Documentos generados"** con **"Ver"** para contrato, pagaré y plan de pagos; botón
  **"Re-emitir los PDF (sin cambiar plan)"** con un ícono ⓘ de ayuda al lado.
- **Regla de negocio**: ver [RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md),
  [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md) y
  [RF13_firmados_checklist.md](../funcionamiento/RF13_firmados_checklist.md).
- **Qué pasa por detrás, verificado en el código** (`ServicioFormalizacion.generarDocumentos`):
  - **Número de operación**: `generarIdentificadorOperacion` toma el siguiente valor de
    `numeradores` (clave `OPERACION`, por año) y arma `CC-{año}-{secuencia de 5 dígitos}` —
    mismo número reutilizado como `numero_contrato` y `numero_pagare` (D-68: una sola operación,
    tres documentos).
  - **Tablas que escribe, todo en una sola transacción**: `INSERT` en `prestamos` (nace
    **directamente con `estado_prestamo = PENDIENTE_DESEMBOLSO`** — no hay ningún estado
    intermedio en el `CHECK` de la tabla); `INSERT` en `planes_pago` (versión 1, `ORIGINAL`,
    vigente) y en `cuotas` (el plan completo); `UPDATE` en `solicitudes_credito` — **no cambia
    `estado_solicitud`** (sigue `APROBADA`), lo que cambia es un campo booleano aparte,
    `formalizada` (pasa a `TRUE`, para que la solicitud deje de contar como "abierta" en las
    reglas RN-01/02/28); `INSERT` en `archivos_binarios` + `documentos_expediente` por cada uno
    de los 3 PDF (contrato, pagaré, plan de pagos, versión 1, `VIGENTE`).
  - **Auditoría — son 4 filas, no 1**: `GENERAR_DOCUMENTOS_FORMALIZACION` ("Generar documentos
    de formalización"), entidad PRESTAMO, con el detalle de la fecha de primer vencimiento
    (elegida y la ya corrida a día hábil) y la cuota/CFT recién calculados; **más una fila
    `GENERAR_PDF_FORMALIZACION` ("Re-emitir PDF de formalización") por cada uno de los 3 PDF**,
    entidad DOCUMENTO_EXPEDIENTE — cada documento generado audita por separado, además del
    evento principal.
  - **Revalidaciones antes de escribir nada** (si alguna falla, no se genera nada): tope de tasa
    vigente contra la TEA aprobada; la fecha elegida Y la fecha ya corrida al día hábil
    siguiente, ambas dentro del rango de 20-45 días; el 40 % de afectación otra vez con los
    datos ACTUALES del cliente (mismo mecanismo que ya se vio en el Paso 3 de esta escena).
  - **"Re-emitir los PDF (sin cambiar plan)"** (`ServicioFormalizacion.regenerarSoloPdfs`, no se
    tocó hoy): vuelve a generar los 3 PDF con los datos ACTUALES del préstamo/cliente —a
    diferencia de "Generar los PDF que faltan" (otro botón, solo visible si falta alguno, que
    NO reemplaza los que ya existen)— **siempre** reemplaza los 3, sin tocar el plan de pagos
    ni el número de operación. El documento anterior de cada tipo queda `REEMPLAZADO` (no se
    borra, versión +1 sobre el nuevo). Exige que el préstamo siga `PENDIENTE_DESEMBOLSO` y que
    **todavía no haya ningún `CONTRATO_FIRMADO`/`PAGARE_FIRMADO` cargado** (si ya hay uno, el
    botón ni aparece: "No se puede re-emitir: ya hay documentos firmados cargados"). Audita
    `REGENERAR_DOCUMENTOS_FORMALIZACION` (entidad PRESTAMO) + otra vez 3 filas
    `GENERAR_PDF_FORMALIZACION` (una por documento reemplazado).
  - **Checklist antes del desembolso** (todavía no se cargó ningún firmado en este tramo):
    contrato, pagaré y plan de pagos firmados, los 3 escaneados y cargados.
- **Qué sigue**: el oficial recorrió el resto de la ficha — ver Paso 6 (Condiciones con el
  detalle de la fecha corrida, Checklist y carga de firmados, Plan de pagos, Regenerar
  documentos). Cargar los 3 documentos firmados de verdad queda para la próxima sesión.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

Mismo orden que el 21/10: 1) **"Generar documentos"** → confirmación **"¿Generar los
documentos de formalización con estas condiciones?"** con **"Sí, generar"**/**"Cancelar"**; 2)
cuadro de éxito **"Documentos generados — Ahora cargue los firmados en el checklist de esta
ficha."** con **"Aceptar"**; 3) ficha de la operación **CC-2026-00002**, estado **"Pendiente de
desembolso"**, con tarjeta **"Condiciones"** (incluida la leyenda de que la operación no
incluye comisiones ni gastos, y que el CFT se compone solo de intereses e IVA sobre intereses),
**"Documentos generados"** (Contrato, Pagaré, Plan de pagos, cada uno con **"Ver"**) y
**"Re-emitir los PDF (sin cambiar plan)"**. Igual que en la primera pasada; la numeración `CC`
continúa correlativa (`00001` → `00002`). **B3, B4 y B5 se repiten** (confirmación genérica con
"Cancelar", sin resumen de qué va a pasar, y mensaje de éxito que no aclara que hay que
imprimir/firmar en papel — ver el detalle original de cada uno más abajo, en Hallazgos).

**Verificado con `SELECT` en `sgcpc_db_pruebas`**:

- **Préstamo creado**: `prestamos` — `identificador_operacion = 'CC-2026-00002'` (igual en
  `numero_contrato` y `numero_pagare`, D-68), `id_solicitud` apuntando a `SOL-2026-0002`, monto
  2.000.000 Gs., plazo 12 meses, `estado_prestamo = 'PENDIENTE_DESEMBOLSO'`, formalizado el
  23/10/2026 09:34:54.
- **Numerador `CC`**: correlativo real, sin huecos — `CC-2026-00001` (primer cliente,
  21/10/2026) y `CC-2026-00002` (segundo cliente, 23/10/2026), ambos del año 2026.
- **Cuotas generadas**: 12 filas en `cuotas` (plan vigente, versión 1), con la primera
  `fecha_vencimiento = 23/11/2026` (coincide con lo propuesto en el Paso 3) y las siguientes
  mes a mes — la 2.ª el 23/12/2026, pero la 3.ª el **25/01/2027** (no el 23, que cae sábado —
  confirma el corrimiento automático al día hábil, RN-19) y la 4.ª de nuevo el 23/02/2027.
- **Estado de la solicitud**: `SOL-2026-0002` sigue con `estado_solicitud = 'APROBADA'` —
  **no cambia** al formalizar (mismo comportamiento ya documentado para `SOL-2026-0001` en
  `03_aprobacion.md` § C20) — lo que cambia es `formalizada`, que pasa de `FALSE` a `TRUE`.
- **Auditoría**: `GENERAR_DOCUMENTOS_FORMALIZACION` (PRESTAMO, id 2) más 3 filas
  `GENERAR_PDF_FORMALIZACION` (DOCUMENTO_EXPEDIENTE, ids 16/17/18 — contrato, pagaré y plan),
  las 4 con la misma fecha y hora exacta de la transacción (23/10/2026 09:34:54) — mismo patrón
  de "4 filas, no 1" ya documentado para el primer cliente.

## Paso 6 — Recorrido completo de la ficha /formalizacion/{id} (RF11-RF13)

- **Usuario/rol**: Oficial, misma ficha del Paso 5.

**Tarjeta "Condiciones" (detalle no cubierto en el Paso 5)**:
- **"Primer vencimiento"** se muestra con el formato **"21/11/2026 → lunes 23/11/2026"**
  (ejemplo con las fechas de hoy: el oficial eligió/propuso 21/11/2026, que cae sábado, y el
  sistema la corre al siguiente día hábil) — solo se muestra la flecha cuando la fecha elegida
  y la fecha efectiva difieren; si coincidieran, se vería una sola fecha
  (`FormateadorFechas.comoDiaYFecha`, el mismo "lunes 23/11/2026" con el nombre del día).
- **"Formalizado"** muestra la fecha y hora reales en que se generaron los documentos
  (`prestamoFicha.getFechaFormalizacion()`, formato `dd/mm/aaaa hh:mm`).
- Debajo de la tabla: "La presente operación no incluye comisiones, gastos administrativos ni
  cargos adicionales; el costo total del crédito está compuesto únicamente por intereses e IVA
  sobre intereses."

**Tarjeta "Checklist antes del desembolso"**:
- 3 renglones, uno por documento — hoy los 3 en **Pendiente** (badge gris): **"Contrato firmado
  (firmado por el cliente)"**, **"Pagaré firmado (firmado por el cliente)"**, **"Plan de pagos
  firmado (firmado por el cliente)"**. El texto entre paréntesis no viene del nombre del tipo de
  documento (`tipos_documento.nombre_tipo` es solo "Contrato firmado"/"Pagaré firmado"/"Plan de
  pagos firmado") — lo agrega `ServicioFormalizacion.obtenerChecklist`, que concatena
  **" (firmado por el cliente)"** a los 3 por igual, sin distinguir el contrato (que además
  lleva la firma del representante legal y de "quien atendió", ver
  [03_aprobacion.md](03_aprobacion.md) §3 sobre los firmantes del contrato).
- Formulario de carga, debajo de la lista (visible mientras falte algo): selector **"Tipo de
  documento"** (las 3 opciones del checklist), campo de archivo **"Archivo escaneado (PDF, JPG o
  PNG)"**, botón **"Cargar"** — un solo documento por envío, no hay manera de cargar los 3 a la
  vez ni un botón por renglón.
- **Regla de negocio**: ver [RF13_firmados_checklist.md](../funcionamiento/RF13_firmados_checklist.md).
- **Qué acepta la carga, verificado en el código** (`ServicioDocumentos.validarArchivo`):
  extensión declarada PDF/JPG/PNG únicamente (si no, "Solo se aceptan archivos PDF, JPG o PNG.
  Si tiene un documento Word, guárdelo como PDF antes de cargarlo."); los primeros bytes del
  archivo tienen que coincidir con esa extensión (si no, "El archivo no coincide con su tipo...
  Parece un archivo con la extensión cambiada."); tamaño máximo = parámetro
  `archivos.tamano_maximo_mb` (**5 MB** hoy, `bd/03_parametros.sql`) — "El archivo supera 5 MB.
  Escanéelo con menor resolución o en blanco y negro para que pese menos." si lo excede. **Se
  guarda igual que cualquier otro documento del sistema: nada en disco, completo como `BYTEA` en
  `archivos_binarios.contenido`** (ver [01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md)
  § Resumen: archivos en disco).

**Tarjeta "Plan de pagos"**:
- Columnas: Cuota, Vencimiento, Capital, Interés, IVA, Total, **Saldo según plan**. Fila de
  totales al pie. Leyenda: **"No refleja pagos realizados; ver estado de cuenta."** (el "Saldo
  según plan" es el saldo programado del plan original — nunca se actualiza con pagos reales,
  eso vive en otra pantalla, Cobranza/Estado de cuenta).
- **Los vencimientos quedan anclados al día original del mes, uno por uno** — verificado en
  `ServicioMotorFinanciero.generarPlanPagos`: cada cuota `k` usa
  `fechaPrimerVencimiento.plusMonths(k - 1)` como fecha teórica (siempre el mismo día de cada
  mes, ej. el 21), y RECIÉN sobre esa fecha teórica se aplica el corrimiento a día hábil
  (`fechaHabil`) si cae en fin de semana o feriado — el corrimiento de una cuota nunca arrastra
  ni corre las siguientes, cada una se calcula de nuevo desde el día 21 de su propio mes.
- **El interés de la primera cuota NO se ajusta por tener 33 días en vez de 30** (21/10 → 23/11,
  contando la fecha efectiva): verificado en el mismo método — el interés de CUALQUIER cuota
  (la primera incluida) es siempre `saldo × tasaMensual(TEA)`, una tasa mensual fija aplicada al
  saldo, sin mirar nunca cuántos días calendario tiene ese período en particular. La fecha
  efectiva (ya corrida a día hábil) solo se usa para mostrar el vencimiento, nunca para el
  cálculo del interés — es el mismo "período secuencial, no día-calendario real" que ya se
  documentó para RF11 (afectación/cuota) en el Paso 3.
- **Por qué la cuota total baja levemente, mes a mes**: la cuota de capital + interés es
  constante (sistema francés, `cuotaConstante` calculada una sola vez al generar el plan); lo
  que se suma encima es el IVA, calculado solo sobre el interés de ESE período
  (`ivaCuota = iva(interes)`) — como el saldo baja cada mes, el interés también baja, así que el
  IVA (proporcional al interés) baja con él, y el total (`interés + capital + IVA`) achica una
  fracción cada vez, aunque capital + interés por sí solos se mantengan iguales.

**Tarjeta "Regenerar documentos"** (con ícono ⓘ: "Recalcula el plan de pagos con una fecha de
primer vencimiento distinta (queda como una nueva versión del plan, con cuotas nuevas). El
número de operación, el contrato y el pagaré no cambian. Ya no se puede usar una vez que haya un
contrato o pagaré firmado cargado."):
- Texto de ayuda: "Recalcula el plan si cambia la fecha del primer vencimiento (reutiliza el
  mismo número de operación). Ya no se puede una vez que haya un contrato o pagaré firmado
  cargado."
- Resumen repetido: cuota más alta, total a pagar, CFT, afectación actual (con su límite) — los
  mismos 4 datos que ya se vieron en la tarjeta "Condiciones aprobadas" del Paso 3 (cotizador
  previo a formalizar), ahora recalculados sobre el préstamo ya formalizado.
- Campo de fecha con el mismo rango ("Entre 10/11/2026 y 05/12/2026.") y el mismo aviso en vivo
  de día no hábil que ya se vio en el Paso 3.
- Botón **"Regenerar"** → confirmación "¿Regenerar los documentos?" / "Sí, regenerar". Bloqueado
  (no se muestra, solo el aviso "No se puede regenerar: ya hay documentos firmados cargados.")
  una vez que haya un contrato o pagaré firmado.
- **Qué hace exactamente, verificado en el código** (`ServicioFormalizacion.regenerarDocumentos`
  — un método DISTINTO de "Re-emitir los PDF"/`regenerarSoloPdfs` del Paso 5): si la fecha
  elegida es la MISMA que ya tenía el préstamo, no toca nada (ni plan ni PDF), solo deja una
  fila de auditoría sin detalle de fecha. Si la fecha CAMBIA: **no borra `cuotas`** — marca el
  `planes_pago` vigente como `no vigente` e **inserta una versión nueva** (`version_plan + 1`,
  `vigente = true`) con sus propias `cuotas` nuevas (mismo patrón de versionado sin destruir
  nada que usa el resto del sistema); actualiza `prestamos.primera_fecha_vencimiento` y
  `prestamos.cft`; **mantiene exactamente el mismo `identificador_operacion`/`numero_contrato`/
  `numero_pagare`** (no se tocan); regenera (reemplaza) los 3 PDF, porque el plan cambió.
  Auditoría: `REGENERAR_DOCUMENTOS_FORMALIZACION` (PRESTAMO) + 3 filas `GENERAR_PDF_FORMALIZACION`
  si hubo cambio de fecha. Bloqueado por `asegurarSinDocumentosFirmados`, mismo chequeo que
  "Re-emitir los PDF".
- **Regla de negocio**: ver [RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md).
- **Qué sigue**: cargar los 3 documentos firmados (próxima sesión) o anular el préstamo (solo
  gerente, ver [03_aprobacion.md](03_aprobacion.md) § Reglas de decisión del crédito).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

Ficha de `CC-2026-00002`: tarjeta **"Condiciones"** con **"Primer vencimiento: 23/11/2026"**
(una sola fecha, sin flecha — a diferencia del primer cliente, acá la fecha elegida y la
efectiva coinciden: 23/11/2026 ya es lunes), **"Formalizado: 23/10/2026 09:34"**, y la misma
leyenda de que la operación no incluye comisiones. Tarjeta **"Documentos generados"**.
**"Checklist antes del desembolso"** con los 3 firmados en **Pendiente**. **"Plan de pagos"**
de 12 cuotas, y **"Regenerar documentos"** disponible (ningún firmado cargado todavía).

**Plan de pagos, verificado con `SELECT`**: las 12 cuotas quedan ancladas al día **23** de cada
mes (mismo día que la fecha elegida/efectiva de la cuota 1, acá sin diferencia entre una y
otra) — con el corrimiento a día hábil disparando en 3 cuotas: la **3.ª** (teórica 23/01/2027,
sábado → efectiva **25/01/2027**), la **7.ª** (teórica 23/05/2027, domingo → efectiva
**24/05/2027**) y la **12.ª** (teórica 23/10/2027, sábado → efectiva **25/10/2027**) — cada
corrimiento es independiente, ninguno arrastra al siguiente (mismo mecanismo ya documentado
arriba para el primer cliente). **Montos idénticos a la cotización del mismo cliente con otra
fecha** (cuota 1: 193.280 Gs.; total a pagar: 2.298.180 Gs.) — confirma en la práctica **D-156**
(mes fijo: el interés se calcula siempre por período secuencial, nunca por días reales entre
fechas, así que cambiar la fecha elegida no cambia ningún monto). Totales (`SUM` de las 12
cuotas): **capital 2.000.000 Gs., interés 271.072 Gs., IVA 27.108 Gs.** (coincide con monto
aprobado 2.000.000 + los 298.180 Gs. de costo financiero = 2.298.180 Gs. total a pagar).

**Verificado en el código y con `SELECT`**:

- **¿El pagaré toma como vencimiento la fecha efectiva de la última cuota?** **Sí, confirmado.**
  `pagare.jrxml` imprime literalmente **"Vence el " + ULTIMA_FECHA_VENCIMIENTO + "."** — y ese
  parámetro (`ServicioFormalizacion.construirParametrosReporte`) sale de
  `cuotas.get(cuotas.size() - 1).getFechaVencimiento()`: la fecha **efectiva** (ya corrida, no
  la teórica) de la cuota 12. Confirmado con `SELECT`: la cuota 12 de `CC-2026-00002` tiene
  `fecha_vencimiento_teorica = 23/10/2027` pero `fecha_vencimiento = 25/10/2027` (sábado →
  lunes) — el pagaré va a decir "Vence el 25/10/2027.", la fecha real, no la teórica. Mismo
  parámetro que usa el contrato para su "Último vencimiento" (`contrato.jrxml`), así que los
  dos documentos legales siempre coinciden entre sí en esta fecha.
- **Qué dice la cláusula SEGUNDA del contrato para este caso, y su relación con C3**: mismo
  texto que ya se citó en el Paso 6 original — *"La primera cuota vence el
  {PRIMERA_FECHA_VENCIMIENTO} y las siguientes el mismo día de cada mes. Cuando un vencimiento
  cae en sábado, domingo o feriado, se traslada al siguiente día hábil."* — con
  `PRIMERA_FECHA_VENCIMIENTO = 23/11/2026` (la fecha efectiva de la cuota 1) para este cliente.
  **Este caso es un buen contraste con C3, no una repetición**: C3 señala que el contrato
  describe mal el ancla real del plan cuando la fecha ELEGIDA y la EFECTIVA de la cuota 1
  difieren (el primer cliente: elegida 21, efectiva 23 — el plan ancla al 21, no al 23 que dice
  la cláusula). Acá, para `SOL-2026-0002`, **elegida y efectiva son la misma fecha (23/11/2026,
  ya es lunes)** — así que el plan, que ancla cada cuota al día de la fecha elegida (23), **sí
  coincide** con lo que la cláusula promete ("las siguientes el mismo día", es decir, el 23).
  No hay contradicción en este ejemplo puntual — confirma que C3 es específicamente un problema
  de cuándo elegida y efectiva **difieren**, no un error que aparezca siempre.

## Paso 7 — Ver el contrato en PDF (RF12)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: toca **"Ver"** en "Contrato" (tarjeta "Documentos generados", Paso
  5). Se abre `/documentos/{id}` — PDF de 2 páginas.
- **Qué responde el sistema** (cliente ficticio Marta Ovelar, C.I. 1.000.001):
  - **Encabezado**: datos de la empresa (razón social, RUC, domicilio, teléfono/WhatsApp,
    correo, registro BCP) con el logo a la izquierda; recuadro azul "CONTRATO DE PRÉSTAMO" con
    "N.º CC-2026-NNNNN"; título "CONTRATO DE PRÉSTAMO DE DINERO".
  - **Recuadro de resumen** (fondo gris, solo en la página 1): monto del préstamo y plazo
    (3.000.000 Gs. · 12 cuotas); cuota 1.ª con IVA, TEA/TNA/CFT; total a pagar; primer y último
    vencimiento.
  - **Comparecencia** (sin encabezado en negrita, como una cláusula más): "En la ciudad de
    Asunción, República del Paraguay, a los [fecha en letras], entre:" — "EL ACREEDOR: [razón
    social], RUC [...], con domicilio en [...], representada en este acto por [representante
    legal]; y" — "EL DEUDOR: Marta Ovelar, C.I. N.º 1.000.001, con domicilio en [domicilio tal
    cual cargado], teléfono [...];" — "convienen celebrar el presente contrato de préstamo,
    sujeto a las siguientes cláusulas:".
  - **Cláusulas PRIMERA a DÉCIMA** (títulos): PRIMERA — Objeto; SEGUNDA — Plazo y forma de pago;
    TERCERA — Tasas y costo del crédito; CUARTA — Sin cargos adicionales; QUINTA — Mora; SEXTA —
    Pago anticipado; SÉPTIMA — Pagaré; OCTAVA — Vencimiento anticipado; NOVENA — Información
    crediticia; DÉCIMA — Domicilio y jurisdicción.
  - **3 firmas** (página 2, banda de cierre): **EL ACREEDOR** (representante legal + "p/" razón
    social), **EL DEUDOR** (Marta Ovelar, C.I. 1.000.001), **QUIEN LE ATENDIÓ** (nombre y C.I.
    de quien formalizó — fila propia, bajo la columna del deudor).
  - **Pie**: "Emitido por: [usuario] — [fecha hora]" a la izquierda, "Página X de 2" a la
    derecha.
- **Regla de negocio**: ver [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Plantilla**: `src/main/resources/reportes/contrato.jrxml` — ningún cálculo ni texto de
    cláusula vive ahí; el `.jrxml` solo define el encabezado/recuadro/firmas/pie (con
    parámetros `$P{...}`) y una banda `detail` que imprime, fila por fila, un datasource de
    cláusulas ya armadas en Java (comentario propio del archivo: "Ningún cálculo acá: todo
    llega ya calculado/formateado desde ServicioFormalizacion").
  - **De dónde salen los textos y parámetros**: `ServicioFormalizacion.generarPdfContrato` llama
    a `construirParametrosReporte` (los datos de `empresa.*` — razón social, RUC, domicilio,
    teléfono, WhatsApp, correo, registro BCP — leídos de `parametros_sistema`, vigentes A LA
    FECHA de formalización, no a la de reimpresión; `EMPRESA_REPRESENTANTE` del parámetro
    `empresa.representante_legal`; datos del cliente desde `Persona`/`personas_domicilio`) y a
    `construirClausulasContrato` (el texto completo de las 10 cláusulas, hardcoded en Java,
    interpolando esos mismos parámetros más `DESTINO_CREDITO` —de la solicitud—,
    `CUOTAS_ALERTA_GARANTE` y `PORCENTAJE_PUNITORIO` —de `parametros_sistema`—).
- **Qué sigue**: ver también el pagaré y el plan de pagos (no recorridos todavía en detalle).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

## Paso 8 — Ver el pagaré en PDF (RF12)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: toca **"Ver"** en "Pagaré" (tarjeta "Documentos generados", Paso 5).
  Se abre `/documentos/{id}` — PDF de 1 página.
- **Qué responde el sistema** (cliente ficticio Marta Ovelar, C.I. 1.000.001):
  - **Encabezado**: mismos datos de la empresa y logo que el contrato; recuadro "PAGARÉ" / "N.º
    CC-2026-NNNNN".
  - Título **"PAGARÉ A LA ORDEN"**, con el número de operación repetido a la derecha y el monto
    total (3.447.270 Gs., el mismo total a pagar del contrato) debajo, alineado a la derecha.
  - **"Vence el [última cuota, fecha efectiva]."**
  - **"Lugar y fecha de emisión: Asunción, [fecha de formalización en dd/mm/aaaa]."**
  - Párrafo del pagaré en sí: **"Pagaré sin protesto a [razón social] (RUC [...]), o a su
    orden, en su domicilio de [domicilio de la empresa], la suma de [total a pagar en letras,
    MAYÚSCULAS] ([total a pagar en número]), por igual valor recibido en préstamo a mi entera
    satisfacción."**
  - Párrafo de garantía: **"Este pagaré se emite en garantía del Contrato de Préstamo N.º
    CC-2026-NNNNN, pagadero en 12 cuotas según el Plan de Pagos. La falta de pago de [cuotas de
    alerta] cuotas vencidas producirá el vencimiento anticipado de este pagaré por el saldo
    adeudado. En caso de mora se aplicarán los intereses moratorios y punitorios pactados en el
    contrato. Se renuncia al protesto y a la presentación para el pago. Para todos los efectos,
    las partes se someten a la jurisdicción de los tribunales de Asunción."**
  - **1 sola firma**, centrada: **"DEUDOR (librador)"**, con "Marta Ovelar — C.I. 1.000.001" y
    "Domicilio: [domicilio del cliente]" debajo — ninguna firma por parte de la empresa (el
    pagaré es un título unilateral del librador/deudor, no del acreedor).
  - **Pie**: "Emitido por: [usuario] — [fecha hora]" y "Página 1 de 1".
- **Regla de negocio**: ver [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Plantilla**: `src/main/resources/reportes/pagare.jrxml` — una sola banda `detail` con
    texto fijo (ningún datasource de filas, a diferencia del contrato): se llena con
    `new JREmptyDataSource(1)` porque no hace falta ninguna fila repetida, solo los parámetros.
  - **De dónde salen monto, vencimiento y textos**: `ServicioFormalizacion.generarPdfPagare`
    llama a la MISMA `construirParametrosReporte` que usa el contrato (una sola fuente de
    parámetros para los 3 documentos) y llena `pagare.jasper` directamente, sin ningún método
    propio de cláusulas (el texto de garantía está fijo en el `.jrxml`, interpolando parámetros,
    no en una lista armada en Java como las cláusulas del contrato).
    - **Monto** (`TOTAL_A_PAGAR`/`TOTAL_A_PAGAR_LETRAS`): suma de `cuota.getTotalProgramado()`
      de TODAS las cuotas vigentes (`listarCuotasVigentes`) — el mismo total que ya se vio en
      el contrato (cláusula tercera) y en el plan de pagos (fila de totales), una sola fuente.
    - **Vencimiento** (`ULTIMA_FECHA_VENCIMIENTO`): `cuotas.get(cuotas.size() - 1).
      getFechaVencimiento()` — la fecha EFECTIVA (ya corrida a día hábil) de la última cuota del
      plan, igual que el contrato usa la efectiva de la primera.
    - **Lugar y fecha de emisión**: `FECHA_FORMALIZACION` = la fecha real de formalización
      (`prestamo.getFechaFormalizacion()`), no la fecha de reimpresión si se vuelve a ver el PDF
      más tarde.
    - **Textos de garantía** (cuotas de alerta, moratorios/punitorios, renuncia al protesto,
      jurisdicción): fijos en el `.jrxml`, interpolando `PLAZO_MESES` y
      `CUOTAS_ALERTA_GARANTE` (parámetro `credito.cuotas_alerta_garante`) — ningún cálculo en
      la plantilla, todo ya resuelto por `construirParametrosReporte`.
- **Sin hallazgos en este paso**: el pagaré se recorrió completo y no se encontró ninguna
  inconsistencia — a diferencia del contrato (Paso 7, C3), acá el único vencimiento que se
  menciona en el texto es el de la última cuota (`ULTIMA_FECHA_VENCIMIENTO`), que sí coincide
  exactamente con la fecha real de la última fila del plan (mismo dato, misma fuente) — no hay
  ninguna frase sobre "los demás vencimientos" que pueda contradecir cómo se ancla el resto del
  plan.

## Paso 9 — Ver el plan de pagos en PDF (RF12)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: toca **"Ver"** en "Plan de pagos" (tarjeta "Documentos generados",
  Paso 5). Se abre `/documentos/{id}` — PDF de 1 página.
- **Qué responde el sistema** (cliente ficticio Marta Ovelar, C.I. 1.000.001):
  - **Encabezado**: mismos datos de la empresa y logo que contrato/pagaré; recuadro "PLAN DE
    PAGOS" / "N.º CC-2026-NNNNN"; título "PLAN DE PAGOS" y "Operación N.º CC-2026-NNNNN".
  - **Datos de la operación**, en 2 columnas: "Cliente: Marta Ovelar — C.I. 1.000.001 / Monto
    del préstamo: 3.000.000 Gs. / (monto en letras)" a la izquierda; "Plazo: 12 cuotas
    mensuales · Sistema francés / TEA .../TNA .../CFT ... / Fecha de formalización:
    21/10/2026" a la derecha.
  - **Tabla de 12 cuotas**: columnas N.º, Vencimiento, Capital, Interés, IVA 10 %, Total cuota,
    Saldo según plan — fila de **Total** al pie (capital, interés, IVA y total a pagar sumados;
    la columna "Saldo según plan" queda vacía en esa fila, no tiene sentido sumarla).
  - **4 notas al pie**, numeradas: "1. Los pagos se realizan en efectivo en el local de la
    empresa. Si el vencimiento cae en día inhábil, se paga el siguiente día hábil sin recargo."
    — "2. Pasado el vencimiento se cobran intereses moratorios y punitorios según la cláusula
    QUINTA del contrato." — "3. Puede cancelar o adelantar pagos en cualquier momento sin
    penalidad ni intereses futuros (Ley 6624/2020)." — "4. La columna 'Saldo según plan' es el
    saldo programado de este plan original. No refleja pagos realizados; ver estado de cuenta."
  - **2 líneas de conformidad**, cada una con su propia raya encima: **"Recibí copia de este
    plan de pagos. Marta Ovelar — C.I. 1.000.001"** y, debajo, **"Entregado por [oficial] — C.I.
    [...]"**.
  - **Pie**: "Emitido por: [usuario] — [fecha hora]" y "Página 1 de 1".
- **Regla de negocio**: ver [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Plantilla**: `src/main/resources/reportes/plan_pagos.jrxml` — a diferencia del pagaré
    (texto fijo) y como el contrato, usa un datasource de filas (`CuotaReporte`, una por cuota,
    `JRBeanCollectionDataSource`); a diferencia del contrato, cada fila ya trae sus columnas
    numéricas resueltas (no hay cláusulas de texto libre que armar). Los 4 totales
    (`TOTAL_CAPITAL`/`TOTAL_INTERES`/`TOTAL_IVA`/`TOTAL_A_PAGAR`) y las 4 notas/2 líneas de
    conformidad son parámetros/texto fijo del `.jrxml`, no se recalculan en el reporte.
  - **De dónde salen los datos**: `ServicioFormalizacion.generarPdfPlanDePagos` usa la MISMA
    `construirParametrosReporte` que contrato y pagaré (empresa, cliente, monto, TEA/TNA/CFT,
    fecha de formalización — una sola fuente para los 3 documentos) y además arma la lista de
    `CuotaReporte` a partir de `listarCuotasVigentes` — la MISMA lista de cuotas que ya usa
    `ControladorFormalizacionServlet.mostrarFicha` para dibujar la tabla "Plan de pagos" de la
    ficha (Paso 6 de esta escena): mismo método, una sola fuente, nunca dos cálculos separados
    que puedan desalinearse.
- **Las cifras coinciden con la ficha** (verificado: mismo método `listarCuotasVigentes` detrás
  de las dos pantallas) **y el plan muestra las cuotas siguientes a la primera ancladas al día
  original** (día 21, no 23) — exactamente lo que ya se confirmó en el Paso 6 leyendo
  `ServicioMotorFinanciero.generarPlanPagos`. Esto termina de confirmar el hallazgo C3 del Paso
  7: la diferencia está únicamente en el TEXTO de la cláusula SEGUNDA del contrato ("las
  siguientes el mismo día" = día 23), no en el plan de pagos real, que acá se ve con sus fechas
  correctas (día 21 en las cuotas siguientes). El PDF del plan de pagos, por sí solo, no tiene
  ningún error — es coherente con la ficha y con el motor financiero.
- **Sin hallazgos nuevos en este paso**.

## Paso 10 — Cargar los 3 firmados (RF13)

- **Usuario/rol**: Oficial.
- **Qué hace el usuario**: en la tarjeta "Checklist antes del desembolso" (Paso 6), carga los 3
  documentos firmados por Marta Ovelar, de a uno: elige el tipo en el selector, adjunta el
  archivo escaneado y toca **"Cargar"** — tres veces, una por documento (contrato, pagaré, plan
  de pagos).
- **Qué responde el sistema, después de cada carga**: cuadro de éxito con botón "Aceptar" —
  **"Documento cargado. Faltan: [los que siguen]."** mientras falta algo (ej. después de cargar
  solo el contrato: "Faltan: Pagaré firmado (firmado por el cliente), Plan de pagos firmado
  (firmado por el cliente)."); al cargar el tercero y último, **"Documento cargado. Checklist
  completo: listo para desembolso."** Cada renglón ya cargado pasa del badge gris "Pendiente" a
  **✓ verde**, con un enlace **"Ver"** al archivo recién subido.
- **Desde el primer firmado cargado**: "Re-emitir los PDF (sin cambiar plan)" (Paso 5) se
  reemplaza por el aviso "No se puede re-emitir: ya hay documentos firmados cargados."; y
  "Regenerar documentos" (Paso 6) hace lo mismo, con "No se puede regenerar: ya hay documentos
  firmados cargados." — las dos tarjetas comparten el mismo guardia
  (`asegurarSinDocumentosFirmados`, visto en los Pasos 5 y 6).
- **Con los 3 cargados**: el formulario de carga desaparece de la ficha (ya no hay ningún tipo
  pendiente al que cargarle nada) y aparece el aviso verde **"Documentación completa: listo
  para desembolso. Siguiente paso: la cajera lo desembolsa desde Desembolso y caja →
  Desembolsos."** El estado del préstamo sigue mostrando el badge **"Pendiente de
  desembolso"** — completar el checklist no cambia el `estado_prestamo`, solo habilita el
  siguiente paso.
- **Regla de negocio**: ver [RF13_firmados_checklist.md](../funcionamiento/RF13_firmados_checklist.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Método que guarda cada firmado**: `ServicioDocumentos.cargarDocumentoPrestamo` (ruta
    genérica `POST /documentos/cargar`, con `idPrestamo` en vez de `idPersona` — mismo endpoint
    multipart/CSRF que ya usa el expediente del cliente, E3/E4, sin duplicar esa lógica).
    Escribe, en una sola transacción: `archivos_binarios` (`INSERT`, `origen_archivo = 'CARGADO'`,
    con el hash SHA-256) y `documentos_expediente` (`INSERT`, `id_prestamo` en vez de
    `id_persona`, `estado_documento = 'VIGENTE'`, versión 1 si es la primera carga de ese tipo).
    Auditoría: `CARGAR_DOCUMENTO_FORMALIZACION` ("Cargar documento del cliente" — mismo nombre
    legible genérico de carga) la primera vez; si ya existía un vigente de ese tipo,
    `NUEVA_VERSION_DOCUMENTO_FORMALIZACION` en vez de ese, y el anterior queda
    `REEMPLAZADO` (nunca se borra).
  - **Validaciones de tipo y tamaño**: las mismas que cualquier carga del sistema
    (`ServicioDocumentos.validarArchivo`, ver Paso 6) — PDF/JPG/PNG con la firma real de los
    primeros bytes, máximo `archivos.tamano_maximo_mb` (5 MB).
  - **Si un tipo ya cargado sigue apareciendo en el selector**: SÍ — el `<select>` de
    "Tipo de documento" (`formalizacion/ficha.jsp`) recorre los 3 ítems del checklist sin
    filtrar por `isCumplido()`, así que mientras el formulario siga visible (al menos un tipo
    todavía pendiente), se puede volver a elegir un tipo YA cargado y subir otro archivo — eso
    dispara la rama de "ya existe vigente" de `cargarDocumentoPrestamo` (nueva versión,
    `NUEVA_VERSION_DOCUMENTO_FORMALIZACION`), no un error. Esto es justamente la respuesta a
    C7, ver más abajo.
  - **Cómo se decide "checklist completo" y si eso habilita a la cajera**: el mismo mecanismo ya
    confirmado en el Paso de la escena anterior (tramo 3, pregunta C1) —
    `ServicioFormalizacion.esChecklistCompleto(obtenerChecklist(...))` exige que los 3 tipos con
    `obligatorio_desembolso = TRUE` (`CONTRATO_FIRMADO`/`PAGARE_FIRMADO`/`PLAN_PAGOS_FIRMADO`)
    tengan un documento vigente — y es EXACTAMENTE ese mismo método el que usa
    `ServicioFormalizacion.listarListosParaDesembolsar` para filtrar la bandeja de la cajera
    (`GET /desembolso`). Completar los 3 firmados, entonces, es lo que de verdad habilita a la
    cajera a verlo — confirmado en el código, pendiente de confirmar EN PANTALLA en el tramo 4.
- **Qué sigue**: la cajera desembolsa desde Desembolso y caja → Desembolsos (tramo 4).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

**Nota (no es un hallazgo, es una limitación inherente al papel)**: el sistema no puede validar
que el archivo cargado sea de verdad el documento firmado por el cliente — solo valida que
"parece" un PDF/JPG/PNG real (extensión + firma de los primeros bytes). Que el contenido
corresponda al documento correcto es control humano, de quien carga. En esta prueba se usó un
archivo de ejemplo, no un escaneo real de una firma.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

En el checklist de `CC-2026-00002`, el oficial eligió **"Contrato firmado"** en el selector
(opciones: Contrato firmado, Pagaré firmado, Plan de pagos firmado), seleccionó el archivo y
tocó **"Cargar"**. Cuadro **"Documento cargado — Faltan: Pagaré firmado, Plan de pagos
firmado."** El contrato pasa a tilde verde con **"Ver"**; **"Re-emitir los PDF"** se reemplaza
por **"No se puede re-emitir: ya hay documentos firmados cargados."**; **"Regenerar
documentos"** muestra **"No se puede regenerar: ya hay documentos firmados cargados."** —
idéntico a lo observado con el primer cliente (Paso 10, arriba).

Carga del **pagaré firmado** → **"Documento cargado — Faltan: Plan de pagos firmado."** Carga
del **plan de pagos firmado** → **"Documento cargado. Checklist completo: listo para
desembolso."** La ficha muestra los 3 renglones con tilde verde y **"Ver"**, desaparece el
formulario de carga, y aparece el aviso verde **"Documentación completa: listo para desembolso.
Siguiente paso: la cajera lo desembolsa desde Desembolso y caja → Desembolsos."** El estado
sigue mostrando **"Pendiente de desembolso"** (completar el checklist no cambia
`estado_prestamo`, igual que con el primer cliente). **Comportamiento idéntico** al del primer
cliente, en los 3 pasos de carga.

**B42 (dato, 23/10/2026 — RESUELTO el 01/11/2026**, ver el registro en `03_aprobacion.md`. **Ojo:
este caso puntual seguiría sin avisar nada, y es lo decidido**: el archivo repetido estaba en el
expediente de **otra persona**, y la búsqueda es acotada a este mismo expediente —acá, a este
préstamo— para no nombrar documentos de un tercero en un aviso de pantalla. Lo que sí avisa hoy
es el mismo contenido repetido **dentro** del mismo préstamo o de la misma persona): se volvió a
cargar el mismo archivo de prueba
"ejemplo.pdf" (mismo hash SHA-256 que la autorización firmada del primer cliente — ver la nota
aclaratoria y B42 en `03_aprobacion.md`) como **"Contrato firmado"** de `CC-2026-00002`, sin ningún aviso. Es la
**cuarta reutilización** del mismo archivo entre los dos clientes de práctica y 4 tipos de
documento distintos (autorización de consulta al buró y resultado del buró del segundo cliente,
más ahora el contrato firmado) — mismo hallazgo ya registrado (`ServicioDocumentos.
cargarDocumentoPrestamo`, igual que `cargarDocumento`, nunca compara el hash contra
`archivos_binarios` antes de guardar), confirmado de nuevo con un tipo de documento distinto
(del expediente del cliente a un documento del préstamo).

La revalidación del tope de tasa y del 40 % con el ingreso actual del cliente (segundo control,
al formalizar), y las reglas de **anulación del préstamo** (motivo ≥ 10 caracteres, solo el
gerente, solo antes del desembolso) están en
[03_aprobacion.md](03_aprobacion.md) § Reglas de decisión del crédito — se migraron juntas
porque el documento original ya las trataba como una sola unidad.

Ver el detalle completo y las reglas de negocio en
[RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md),
[RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md),
[RF13_firmados_checklist.md](../funcionamiento/RF13_firmados_checklist.md) y
[RF14_anular_prestamo.md](../funcionamiento/RF14_anular_prestamo.md).

## Hallazgos de esta escena (tramo 3, inicio)

Separados de "Cómo funciona hoy" (arriba, ficha por paso). Todos tipo **B**.

- **B** — La primera sección de `/formalizacion` (solicitudes aprobadas listas para formalizar)
  no tiene un título propio, a diferencia de las otras 2 ("Préstamos pendientes de desembolso",
  "Préstamos finalizados — cierre documental pendiente") — podría llamarse **"Solicitudes
  aprobadas para formalizar"**, para que las 3 secciones se vean parejas.
- **B46** — La primera sección de la bandeja (solicitudes aprobadas listas para formalizar) no
  muestra cuánto tiempo lleva esperando cada solicitud — a diferencia de la bandeja del
  gerente (`/aprobaciones`), que sí tiene la columna **"Esperando"**. Confirmado hoy
  (23/10/2026, segundo cliente de práctica): `SOL-2026-0002` aparece con "Aprobada: 22/10/2026"
  pero nada indica que ya pasaron más de 12 horas desde la aprobación. Útil para la promesa de
  atención "sale en el mismo día" (ver `00_apertura.md`/`01_llega_el_cliente.md`) y
  directamente relacionado con **B45** (el panel tampoco avisa) — entre los dos, hoy no hay
  ningún lugar del sistema que le muestre al oficial cuánto tiempo lleva una solicitud ya
  aprobada sin formalizar. Sugerir la misma columna "Esperando", calculada desde
  `fecha_decision` (la misma columna que ya usa `03_aprobacion.md` para el "Aprobada" de esta
  misma fila).
- **B47 (menor) — confirmado de nuevo (23/10/2026, segundo cliente de práctica)** — El menú
  tiene una sola opción debajo de "Formalización", con el mismo nombre ("Formalización" →
  "Formalización") — un clic de más sin necesidad; podría ir directo a la pantalla sin el paso
  intermedio de desplegar el módulo (enlace directo), o renombrar la opción interna (ej.
  "Solicitudes y préstamos"). **Verificado en el código**: `plantilla/menu.jsp` arma este
  módulo con `moduloMenu(..., "Formalización", ..., opcionMenu(..., "/formalizacion", ...,
  "Formalización", ...))` — literalmente el mismo texto en el módulo y en su única opción.
- **B1** — El botón "Generar documentos" (pantalla del Paso 3) no dice específicamente qué
  genera; sugerir **"Generar contrato, pagaré y plan de pagos"** para que quede claro sin tener
  que leer el resto de la pantalla.
- **B2** — El texto de ayuda sobre la fecha del primer vencimiento ("La fecha aprobada en la
  solicitud puede haber quedado desactualizada si se formaliza días después. Se propone el
  primer vencimiento un mes desde hoy; puede ajustarla dentro del rango permitido.") es largo;
  sugerir acortarlo, por ejemplo: "Se propone un mes desde hoy; puede ajustarla dentro del
  rango permitido."
- **B48 (menor)** — En "Nueva solicitud" (`02_solicitud.md`), la fecha estimada propuesta puede
  caer en un día no hábil sin que esa pantalla avise nada — confirmado hoy: `SOL-2026-0002`
  quedó con primer vencimiento estimado **22/11/2026, domingo**. **Verificado en el código**:
  "Nueva solicitud" no controla sábados/domingos/feriados en absoluto — ni en el campo (no
  tiene la clase `campo-primer-vencimiento`) ni en el servidor (`ServicioSolicitudes.crear`/
  `aplicarSimulacion` solo valida el rango de 20-45 días). **Formalización, en cambio, sí lo
  controla**: tiene el aviso en vivo (`fecha-primer-vencimiento.js`) y, sobre todo, **propone
  una fecha nueva** (un mes desde la fecha real de formalización, no desde la de la solicitud),
  así que en la práctica la fecha problemática de la solicitud ni siquiera llega a Formalización
  — se reemplaza por una recalculada. El hueco es solo de aviso en la pantalla de origen, nunca
  de datos reales (ningún domingo llega a convertirse en vencimiento real de una cuota). Mismo
  tipo de hallazgo que el ya clasificado **B** en `02_solicitud.md` § "Hallazgos tipo C,
  clasificados" ("Fecha del primer vencimiento: 10/11 en el cotizador, 21/11 en la solicitud")
  — las dos pantallas (Cotizador y Nueva Solicitud) proponen una fecha sin verificar si es
  hábil, cada una por su cuenta; sugerir que, si se corrige, se revisen las dos juntas.
- **B3** — En el cuadro de confirmación de "Generar documentos" (Paso 4), el botón "Cancelar"
  puede confundirse con cancelar el crédito (como "Cancelar solicitud" en Aprobación — mismo
  tipo de confusión ya anotado en `03_aprobacion.md`); sugerir **"No, volver"**. Implica agregar
  un atributo nuevo (ej. `data-confirmar-cancelar-boton`) al mecanismo genérico de `pie.jsp`,
  porque hoy el texto "Cancelar" del botón de cancelar está fijo para todo el sistema (a
  diferencia del título y del botón de confirmar, que ya son personalizables por formulario) —
  cualquier cambio tendría que decidir si es solo para esta pantalla o para todas.
- **B4** — La confirmación de "Generar documentos" no resume qué va a pasar: no dice la fecha
  real del primer vencimiento (ya corrida al día hábil siguiente, si correspondiera), que se le
  va a asignar un número de operación nuevo, ni que son 3 documentos (contrato, pagaré, plan de
  pagos) los que se generan. Sugerir una línea de resumen antes de la pregunta, por ejemplo:
  "Se generarán el contrato, el pagaré y el plan de pagos, con primer vencimiento el
  [fecha real] y un número de operación nuevo."
- **B5** — El mensaje de éxito ("Documentos generados — Ahora cargue los firmados en el
  checklist de esta ficha.") deja implícito que hay que imprimir los documentos y hacerlos
  firmar antes de cargarlos, y usa la palabra "checklist" (en inglés, cuando el resto del
  sistema habla en español). Sugerir: **"Imprima los 3 documentos, hágalos firmar por el
  cliente y cárguelos en la lista de documentos de esta ficha."**
- **B6** — La lista del checklist dice "(firmado por el cliente)" para los 3 renglones por
  igual (confirmado en el código: `ServicioFormalizacion.obtenerChecklist` concatena ese mismo
  texto a los 3 tipos, sin distinguir) — pero el contrato lleva, además de la firma del cliente,
  la del representante legal de la empresa y la de "quien atendió" (ver
  [03_aprobacion.md](03_aprobacion.md) §3). Para el contrato, el texto entre paréntesis queda
  incompleto o directamente incorrecto si se lee como "solo falta la firma del cliente".
- **B7** — La carga de firmados es de a uno por vez, con un selector "Tipo de documento" que
  hay que elegir antes de adjuntar el archivo — sugerir un botón "Cargar" propio en cada
  renglón "Pendiente" del checklist (3 formularios chicos en vez de 1 con selector), para que
  quede claro de entrada qué archivo corresponde a cuál documento.
- **B8** — "Re-emitir los PDF (sin cambiar plan)" (Paso 5) y "Regenerar documentos" (esta
  tarjeta) se confunden — los dos regeneran los 3 PDF y los dos se bloquean con un firmado
  cargado, pero uno nunca toca el plan y el otro sí, solo si la fecha cambia. Sugerir renombrar
  "Regenerar documentos" a **"Cambiar primer vencimiento y regenerar"**, para que el nombre
  distinga la acción real (cambiar la fecha) de "Re-emitir" (repetir los mismos PDF tal cual).
  Además, la tarjeta "Regenerar documentos" repite el mismo resumen (cuota más alta, total,
  CFT, afectación) que ya se vio en la tarjeta "Condiciones aprobadas" del Paso 3 — evaluar si
  hace falta repetirlo acá o alcanza con un enlace a esos datos.
- **B9** — No hay ningún botón para imprimir los 3 documentos juntos — hoy hay que abrir
  "Ver" tres veces (contrato, pagaré, plan de pagos) y pedirle al navegador que los imprima uno
  por uno.
- **B10 — Propuesta integral de la ficha (fase 2, reúne B5-B9)**: ordenar toda la ficha
  `/formalizacion/{id}` por la tarea real del oficial, en vez de por tipo de dato:
  1. **Aviso "qué sigue"**, arriba de todo, que cambia según el avance real — "Imprima los 3
     documentos y hágalos firmar" (0 de 3 cargados) → "Cargó 2 de 3 firmados — falta el
     contrato" (parcial) → "Documentación completa: listo para desembolso. Pasó a la cajera"
     (los 3 cargados, mismo texto que ya existe hoy para el caso completo).
  2. **Una sola tarjeta "Documentos para firmar"**, con un renglón por documento (contrato,
     pagaré, plan de pagos) y, por renglón: quién firma (cliente; cliente + representante +
     quien atendió, en el caso del contrato — corrige B6), botones **"Ver / imprimir"** y
     **"Cargar firmado"** propios (corrige B7, sin selector de tipo porque cada renglón ya sabe
     el suyo), y el estado con fecha y usuario de quien lo cargó, una vez cumplido. Un botón
     **"Imprimir los 3 documentos"** arriba de la tarjeta (corrige B9).
  3. **Condiciones**, resumidas (lo esencial: cliente, monto, plazo, primer vencimiento), sin
     repetir TEA/TNA/CFT/afectación si ya se vieron en el Paso 3 de esta misma visita.
  4. **Plan de pagos**, plegado por defecto (un "Ver plan completo" para desplegarlo) — la
     tabla completa de cuotas no es lo primero que el oficial necesita ver al volver a esta
     ficha más tarde.
  5. **Al final de la ficha, como opción secundaria** (ya no en el medio, mezcladas con lo
     principal): "Cambiar primer vencimiento y regenerar" (nombre corregido, B8) y "Re-emitir
     los PDF (sin cambiar plan)", las dos con su resumen actual, para quien de verdad necesite
     usarlas.

  **No cambia ninguna regla de negocio, ningún bloqueo del desembolso ni ningún evento de
  auditoría** — es una reorganización de la misma información y los mismos botones que ya
  existen hoy, repartidos distinto en la pantalla.
- **B11** — "a los veintiuno días" debería ser **"a los veintiún días"** (apócope delante de un
  sustantivo masculino). Verificado en el código:
  `ConversorFechasALetras.comoDiaMesAnioEnLetras` llama a
  `ConversorNumerosALetras.comoLetras(21)`, que devuelve **"veintiuno"** (tabla `VEINTIS`) sin
  pasar por el método que sí hace la apócope "veintiuno" → "veintiún" — ese método existe en la
  misma clase (`ConversorNumerosALetras`, usado para number antes de "mil"/"millones", ej.
  "veintiún mil") pero `comoDiaMesAnioEnLetras` no lo usa para el día. El resultado final:
  "veintiuno" + " día" + "s" = **"veintiuno días"**, el mismo error para cualquier fecha cuyo
  día termine en 21 (21, incluido el de este recorrido).
- **B12** — Espacio en blanco grande entre el recuadro de resumen y la comparecencia, en la
  página 1. **Ya documentado en el propio `.jrxml`** (comentario de `pageHeader`,
  `contrato.jrxml` líneas 116-126): JasperReports exige reservar, también en la página 1, la
  altura completa del letterhead chico que se repite en las páginas siguientes (~76pt) aunque
  ese bloque esté oculto ahí (`printWhenExpression` solo controla si se dibuja, no la altura
  mínima declarada de la banda) — el propio comentario ya propone la solución (un subreporte
  para el letterhead, sugerido por Joel) como "fuera de alcance de este ajuste puntual" en su
  momento.
- **B13** — Domicilio y destino del crédito aparecen en el contrato tal como se cargaron en la
  ficha del cliente/la solicitud (`CLIENTE_DOMICILIO`, `DESTINO_CREDITO` — sin ningún formateo
  de mayúsculas/minúsculas en `construirParametrosReporte`) — si se cargaron en minúsculas o con
  mayúscula en medio de la frase, así salen impresos en un documento legal. Sugerir normalizar
  (ej. mayúscula inicial de oración) al armar estos 2 parámetros, antes de generar el PDF.
- Ver también el principio general sobre el panel y el aviso entre roles, registrado en
  [00_LEEME.md](00_LEEME.md) § Hallazgos generales y en `AVANCE.md` — aplica directamente al
  Paso 1 de esta escena (la oficial no se entera por el panel de que ya puede formalizar).

## Pendiente de confirmar (no es un hallazgo B, es una pregunta para el tramo 4)

**C1 — El préstamo nace `PENDIENTE_DESEMBOLSO` antes de tener los documentos firmados.**
Verificado en el código (solo lectura, sin ejecutar nada): la cajera **no ve** este préstamo en
su bandeja de desembolso (`GET /desembolso` usa
`ServicioFormalizacion.listarListosParaDesembolsar`, que filtra por
`obtenerChecklist`/`esChecklistCompleto` — un préstamo sin los 3 firmados no aparece en esa
lista). Pero si la cajera **fuerza la URL** (`GET /desembolso/{idPrestamo}` con el id de este
préstamo), la ficha SÍ se abre — no da 404 — y muestra el checklist incompleto con
`puedeDesembolsar=false` (calculado en `ControladorDesembolsoServlet` como
`checklistCompleto && documentosVigentes && cajaLista && saldoSuficiente`); si de todos modos
se forzara un `POST` de confirmación, `ServicioDesembolso.confirmarDesembolso` vuelve a chequear
`esChecklistCompleto` y lo rechaza en el servidor. **Para confirmar en pantalla en el tramo 4**:
que la cajera, con la sesión de caja abierta, efectivamente no vea este préstamo en su bandeja
y que, si fuerza la URL de la ficha, el botón de confirmar esté deshabilitado o el servidor
rechace el intento.

**C2 — El interés del primer período se cobra como un mes completo, sin mirar los días reales.**
**Cerrado — decisión de Joel 22/10/2026: se mantiene mes fijo; no se cambia el sistema.**
Motivo: simple, cuotas parejas y fácil de explicar al cliente, aceptando la diferencia en los
extremos del rango (20-45 días). Decisión registrada en `AVANCE.md` (D-156).
`ServicioMotorFinanciero.generarPlanPagos` cobra cada cuota, incluida la primera, como
`saldo × tasaMensual(TEA)` — un mes completo de interés, sin importar si el primer período
real duró 20 días o 45 (el rango permitido para elegir la fecha del primer vencimiento). Caso
real de este recorrido: el período fue de 33 días (21/10 al 23/11) y se cobró como si fueran
30. Con el rango de 20-45 días que permite RF11, en el extremo corto el cliente pagaría un mes
de interés por solo 20 días de uso real del dinero (paga de más); en el extremo largo, la
financiera cobraría un mes por 45 días de uso real (pierde 15 días de interés que no cobró).

**Qué dice el anteproyecto**: `docs/RF_ANTEPROYECTO.md` no menciona este punto en absoluto.
RF01 y RF05 dicen "calcular la cuota" sin especificar el criterio de días; RF11 solo fija el
rango de 20 a 45 días para la fecha, sin decir cómo se calcula el interés de ese primer tramo.

**Qué dice `RF11_formalizacion.md`** (el documento de este RF): confirma el mecanismo, pero sin
discutir si es correcto o no desde el punto de vista financiero — §7 punto 2: "con el motor
actual, el CFT y la cuota más alta **no dependen de qué fecha de primer vencimiento se
elija** — `ServicioMotorFinanciero.calcularCft`/`generarPlanPagos` descuentan por período
secuencial (cuota 1, 2, 3...), no por la cantidad real de días entre vencimientos"; y §11
(guion de defensa), punto 4: "el interés se calcula por período mensual secuencial..., no por
la cantidad real de días entre vencimientos — el mismo criterio que usa la cotización de RF05
[...] lo único que cambia con la fecha es el calendario de cobro, nunca los montos." Ninguno de
los dos pasajes analiza el efecto de cobrar siempre un mes completo sobre un período de
duración variable (20 a 45 días) — solo explican que el monto no cambia con la fecha elegida,
no si ese monto es el correcto para cada caso.

**Clasificación: cerrada, no corresponde A/B** — no era un hallazgo contra el anteproyecto (que
no fija ningún criterio de días para este cálculo), sino una decisión de negocio, y Joel ya la
tomó: se acepta el redondeo a mes fijo en vez de prorratear el primer período por días reales
(ver arriba).

**29/10/2026 — lo que vi: C3 corregido en pantalla.** Regeneré los documentos de `CC-2026-00005`
eligiendo a propósito el **sábado 28/11/2026**: la cláusula SEGUNDA pasó a decir *"La primera cuota
vence el 30/11/2026 y las siguientes **el día 28 de cada mes**. Cuando un vencimiento cae en sábado,
domingo o feriado, se traslada al siguiente día hábil."*, y el plan quedó 30/11, 28/12, 28/01,
**02/03** (28/02 cae domingo), 29/03, 28/04… 28/10/2027 — el contrato describe por fin lo que el plan
hace. El contrato de `CC-2026-00004`, emitido el 28/10, **sigue con el texto viejo** (D-94). Estado
del ítem en [FASE2_PLAN.md](FASE2_PLAN.md).

**C3 — El contrato (cláusula SEGUNDA) contradice al plan de pagos sobre a qué día del mes
vencen las cuotas siguientes a la primera (probable A).** Texto exacto, ubicado en
`ServicioFormalizacion.construirClausulasContrato` (no en el `.jrxml`, que solo imprime filas
de un datasource armado en Java):

> SEGUNDA — Plazo y forma de pago. [...] La primera cuota vence el {PRIMERA_FECHA_VENCIMIENTO}
> y las siguientes el mismo día de cada mes. Cuando un vencimiento cae en sábado, domingo o
> feriado, se traslada al siguiente día hábil. [...]

`PRIMERA_FECHA_VENCIMIENTO` es la fecha EFECTIVA (ya corrida a día hábil, confirmado en
`construirParametrosReporte`: `FormateadorFechas.comoDdMmAaaa(primeraFechaVencimientoEfectiva)`)
— en el ejemplo de este recorrido, **23/11/2026** (lunes), porque el 21/11/2026 elegido cae
sábado. La cláusula dice literalmente que "las siguientes" cuotas vencen "el mismo día" — es
decir, el día 23 de cada mes siguiente. Pero `ServicioMotorFinanciero.generarPlanPagos` ancla
cada cuota `k` al día de la fecha ELEGIDA original (`fechaPrimerVencimiento.plusMonths(k-1)`,
siempre día **21**, no 23), y recién sobre esa fecha teórica aplica su propio corrimiento a día
hábil, independiente del de la cuota 1 — confirmado también en el Paso 6 de esta escena. El
contrato promete un día que el plan real no usa.

**Qué dice el anteproyecto y el RF de formalización**: `docs/RF_ANTEPROYECTO.md` no dice nada
sobre el contenido o la redacción del contrato — RF12 solo exige "generar en PDF el contrato,
el pagaré y el plan de pagos... con un número de operación único", sin especificar ninguna
cláusula ni texto. `RF12_documentos_pdf.md` (§6, tabla de "de dónde sale cada dato") ya tiene
una fila sobre esto: "Primer vencimiento | Fecha EFECTIVA de la cuota 1 (ya corrida, RF11) — no
la teórica elegida | Contrato (cláusula segunda), plan (primera fila)", y concluye "¿Coinciden
exactamente con el plan de la pantalla? Sí" — pero esa conclusión compara que el NÚMERO de la
primera fecha coincide (mismo dato, misma fuente), nunca llegó a revisar si el TEXTO "las
siguientes el mismo día" describe correctamente cómo se ancla el resto del plan. Es un hallazgo
nuevo, no visto antes en ese documento. No corregí nada — queda para que Joel decida si es un
error real del contrato (probable, dado que describe mal el comportamiento) o si se acepta
documentar la diferencia.

**Contraste confirmado con el segundo cliente (23/10/2026)**: en `SOL-2026-0002`/`CC-2026-00002`
la fecha elegida y la efectiva de la cuota 1 **coinciden** (23/11/2026, ya es lunes) — ahí el
plan ancla al mismo día que promete la cláusula, sin contradicción. Confirma que C3 depende de
que elegida y efectiva difieran (ver "Registro adicional: segunda pasada", más abajo en esta
misma escena, tras el Paso 6).

**C4 — La cláusula SEXTA sobre el pago anticipado parcial, verificado en el código (no
contradice, a confirmar en pantalla en el tramo 7).** Texto: "El pago anticipado parcial se
aplica al capital, mantiene el monto de la cuota y reduce la cantidad de cuotas." Verificado en
`ServicioMotorFinanciero.regenerarPlanAnticipo`: toma `cuotaConstante` de la cuota 1 ya vigente
(capital + interés, sin recalcular) y resuelve la nueva cantidad de cuotas `n'` con la fórmula
`techo(-ln(1 - S×i/C) / ln(1+i))` sobre el nuevo saldo `S` — exactamente "mantiene el monto de
la cuota [C, fija] y reduce la cantidad de cuotas [n']", tal como promete el contrato. No es una
contradicción encontrada hoy; queda para confirmarlo en pantalla, con un adelanto real, en el
tramo 7 (días siguientes — pago anticipado).

**C5 — La cita "Ley 6624/2020" en la cláusula SEXTA, de dónde salió (normativa sin verificar).**
Rastreado en `docs/` (sin buscar en internet, solo lo que ya está escrito en el proyecto): la
cita **viene del propio anteproyecto firmado** — `docs/RF_ANTEPROYECTO.md` (RNF18): "El sistema
debe... calcular la cancelación anticipada sin intereses futuros (Ley N.º 6624/2020)...". De
ahí pasó a `docs/00_especificacion_maestra.md` (RN-11) y a `docs/diseno_reportes_e5.md` (línea
46, el diseño de los 3 reportes aprobado por Joel), que trae la cláusula SEXTA **palabra por
palabra** igual a como quedó en el código — no se redactó aparte, se copió del diseño aprobado.
`docs/funcionamiento/RNF_revision.md` (fila RNF18) ya deja anotado, como pendiente conocido,
que **la revisión legal de forma del contrato y el pagaré por un abogado todavía no se hizo**
("la revisión LEGAL de forma... sigue pendiente — decisión de Joel del 13/10/2026: la factura
ya se considera legal y aprobada..., contrato/pagaré siguen esperando esa revisión"), y
`AVANCE.md` repite el mismo pendiente ("Revisión legal de contrato y pagaré: el texto de
`contrato.jrxml`/`pagare.jrxml` lo tiene que revisar un abogado antes de usarse con clientes
reales"). Queda, como ya estaba, pendiente de esa revisión legal — no agrego nada nuevo a ese
pendiente, solo confirmo de dónde sale la cita.

**C7 — No se ve en pantalla cómo reemplazar o quitar un firmado subido por error (camino
parcialmente cubierto, no inexistente).** Verificado en el código: SÍ existe un camino para
reemplazar un firmado **mientras el checklist siga incompleto** — `ServicioDocumentos.
cargarDocumentoPrestamo` ya maneja el caso de "ya existe un vigente de ese tipo" sin rechazarlo:
lo marca `REEMPLAZADO` e inserta la nueva versión (auditado como
`NUEVA_VERSION_DOCUMENTO_FORMALIZACION`), y el `<select>` de "Tipo de documento" (Paso 6) ofrece
igual las 3 opciones, incluidas las ya cargadas, sin filtrarlas — así que, con al menos un
documento todavía pendiente, el oficial (o el gerente, mismo permiso
`GENERAR_DOCUMENTOS_FORMALIZACION`/ruta `CARGAR_DOCUMENTOS_CONSULTA_BURO`) puede volver a elegir
un tipo ya cargado y subir el archivo correcto. **Lo que no existe es ningún camino una vez que
el checklist queda completo**: el formulario de carga desaparece de la ficha por completo
(`if (pendienteDesembolso && !checklistCompletoFicha)`, Paso 6) — ni un botón "Reemplazar", ni
"Quitar", ni ningún otro acceso visible al formulario de carga. `cargarDocumentoPrestamo` en sí
no tiene ningún chequeo que impida una carga tardía (no valida si el checklist ya estaba
completo, ni el estado del préstamo) — el único bloqueo real es que la pantalla ya no ofrece
cómo llegar ahí. Camino no cubierto desde la interfaz, no desde el servidor.

**Queda como hallazgo para la fase 2 (agregar, en la propuesta B10, un "Reemplazar" visible por
renglón incluso con el checklist completo) — clasificación A o B según el RF, pendiente**: el
anteproyecto (RF13: "El sistema debe permitir cargar los documentos firmados escaneados y no
permitir el desembolso hasta que el checklist de firmados esté completo") no dice nada sobre
corregir un firmado ya cargado, así que no hay una letra concreta del RF que compare contra
esta ausencia — queda sin resolver si cuenta como A (no cumple algo exigible del RF) o B (mejora
de uso, el RF no lo exige). No decido esa clasificación acá.

**Pendiente para el tramo 4**: confirmar si el panel de la cajera muestra esta operación
(`CC-2026-NNNNN`, ya con el checklist completo) como trabajo pendiente — según el principio de
paneles registrado en [00_LEEME.md](00_LEEME.md) § Hallazgos generales ("el Panel de cada rol
muestra su trabajo pendiente... cuando alguien termina su parte, el siguiente se entera por su
Panel"). Verificado en el código que el panel YA distingue los dos números, en la misma tarjeta
(`panel.jsp`, bloque "Pendientes"): el número grande es
`indicadoresPanel.getDesembolsosPendientes()` (`PrestamoDAO.listarPendientesDesembolso` — TODOS
los `PENDIENTE_DESEMBOLSO`, sin filtrar por checklist) y la descripción agrega "· N listo(s)
para desembolsar (monto)" con `getDesembolsosListos()` (`ServicioFormalizacion.
listarListosParaDesembolsar` — la MISMA lista filtrada por checklist que usa la bandeja de
`/desembolso`). Es decir: el principio de paneles **ya parece estar aplicado acá**, con los dos
números separados. Falta confirmar EN PANTALLA, como cajera, que el número de "listos" sube de
0 a 1 justo al completar los 3 firmados de este préstamo (no verificado hoy, sin loguearse como
cajera en este tramo).

## Segunda pasada de formalización (23/10/2026)

Resumen en orden real de la formalización del **segundo cliente de práctica**
(`SOL-2026-0002` → `CC-2026-00002`), de punta a punta — cada paso enlaza al "Registro adicional"
ya documentado arriba, con sus hallazgos, sin repetir el contenido completo acá.

1. **Panel de la oficial, sin aviso** — con la solicitud ya `APROBADA` desde el 22/10 20:48, el
   Panel del 23/10 (08:59) no mostró ninguna tarjeta ni aviso de trabajo pendiente en
   Formalización ("Mis solicitudes en curso: 0"). **B45, confirmado en pantalla** (ver
   `03_aprobacion.md`).
2. **Menú "Formalización"** → opción "Formalización" (la única del menú) → `/formalizacion`.
   **B47**, confirmado de nuevo (un clic de más sin necesidad).
3. **Bandeja**: `SOL-2026-0002` en la primera sección, sin columna de tiempo de espera; las
   otras 2 secciones, vacías. **B46** (sin "Esperando", a diferencia de la bandeja del
   gerente).
4. **"Formalizar"** → tarjeta "Condiciones aprobadas (no editables)", afectación recalculada
   3,9 % (límite 40,0 %).
5. **Fecha recalculada**: la solicitud traía 22/11/2026 (domingo) congelado al aprobar, pero la
   pantalla propuso **23/11/2026** (un mes desde la fecha real de formalización, no desde la de
   la solicitud) — que cayó lunes, sin ningún aviso de día no hábil que mostrar. **B48**
   (confirma que "Nueva solicitud" puede proponer un día no hábil sin avisar, mientras que
   Formalización sí controla y recalcula).
6. **"Generar documentos"** → confirmación → **CC-2026-00002** (numeración correlativa,
   00001 → 00002), estado "Pendiente de desembolso". **B3, B4 y B5 se repiten** (confirmación
   genérica, sin resumen, mensaje de éxito sin aclarar el papel).
7. **Plan de pagos**: 12 cuotas ancladas al día 23, con 3 corridas a día hábil (cuotas 3, 7 y
   12); montos idénticos a la cotización con otra fecha (confirma D-156); totales capital
   2.000.000 / interés 271.072 / IVA 27.108. El pagaré toma la fecha **efectiva** de la última
   cuota (25/10/2027), igual que el contrato. **Aclaración de C3**: para este cliente, la fecha
   elegida y la efectiva de la cuota 1 coinciden (23/11/2026), así que la cláusula SEGUNDA del
   contrato **no contradice** al plan en este caso — a diferencia del primer cliente, donde sí
   difieren.
8. **Carga de los 3 firmados** (contrato, pagaré, plan de pagos): mismos mensajes y mismo
   comportamiento que con el primer cliente ("Faltan: ..." en cada carga parcial,
   "Checklist completo: listo para desembolso" con el tercero, estado sigue "Pendiente de
   desembolso"). **B42** (dato): cuarta reutilización del mismo archivo de prueba, esta vez
   como contrato firmado de un préstamo. **Hoy** (B42 resuelto el 01/11/2026) esa carga sería
   **rechazada** si el mismo contenido ya estuviera cargado como ese mismo tipo en este mismo
   préstamo, y **avisada en amarillo** si estuviera en otro tipo del mismo préstamo.

**Qué se confirmó igual que el 21/10/2026**: la estructura de todas las pantallas (bandeja,
Formalizar, ficha, checklist), los textos de los mensajes, el mecanismo de confirmación
genérico, el número de operación correlativo compartido entre contrato/pagaré/plan, la
revalidación de tope de tasa y afectación antes de generar, y el criterio de "mes fijo" (D-156)
aplicado otra vez con un monto y una fecha distintos.

**Qué se descubrió de nuevo o se corrigió con esta segunda pasada**: **B45** pasó de "verificado
en el código" a **confirmado en pantalla**; **B46**, **B47** y **B48** son hallazgos nuevos de
esta pasada; la relación con **C3** quedó más clara al tener un segundo ejemplo que NO
contradice la cláusula (contraste, no repetición); y se corrigió una afirmación de la
documentación del 21/10 que decía, equivocadamente, que el aviso de día no hábil era compartido
con Cotizador/Solicitud — verificado que es exclusivo de Formalización.

## Tercera pasada de formalización: cierre documental (25/10/2026)

**Registro de lo observado (25/10/2026, 11:35–11:37)** — primera vez en todo el proyecto que se
recorre **RF29** en pantalla, sobre `CC-2026-00002`, ya `FINALIZADO` por la cancelación total
del Paso 16 de `06_primer_cobro.md`.

1. La cajera cierra su sesión (11:34:44); el oficial inicia la suya (11:34:52) — el **panel no
   muestra ningún aviso** de que hay un cierre documental pendiente (ver **B76**).
2. Formalización → tercera sección **"Préstamos finalizados — cierre documental pendiente"**:
   `CC-2026-00002` aparece ahí (ya pagado en su totalidad), con el botón **"Cierre
   documental"**.
3. **Card 1 — "Constancia de devolución (sistema)"**: *"Todavía no se generó."* y el botón
   **"Generar constancia de devolución"** — **sin ningún diálogo de confirmación previo**. Al
   tocarlo: **`DEV-2026-000001`**, con el enlace **"Ver"** y la instrucción *"Imprima este PDF,
   hágalo firmar por el cliente al entregarle el pagaré original, y cargue el escaneo firmado
   abajo."* El PDF **"CONSTANCIA DE DEVOLUCIÓN DE PAGARÉ"** cita el pagaré de `CC-2026-00002` y
   la constancia de cancelación `CAN-2026-000001`, dice que *"el expediente queda
   documentalmente cerrado"*, y lleva las firmas del representante legal y **"Recibí el pagaré
   original (cliente)"**, con su C.I.; el pie dice *"Emitido por: [oficial] — [fecha]"* — nunca
   un campo propio de "quién hizo la entrega física" (ver **B78**). **Recién después de generar
   esta constancia aparece la card 2** (ver **B77**).
4. **Card 2 — "Escaneo firmado"**: *"Todavía no se cargó el escaneo firmado."*, con un único
   campo de archivo (obligatorio) y el botón **"Cargar escaneo firmado"**. Al cargarlo:
   **"Ver (cargado 25/10/2026)"** — sin un botón explícito de "reemplazar" (el mismo formulario
   de carga sigue visible mientras el cierre no esté completo). **Recién después de cargarlo
   aparece la card 3**.
5. **Card 3 — "Completar cierre documental"**: botón **"Marcar cierre documental completo"**,
   con la confirmación **"¿Completar el cierre documental? No se puede deshacer."**
   (**"Sí, completar cierre"**/**"Cancelar"**). Al confirmar: franja verde **"COMPLETO —
   25/10/2026 11:37 por [oficial]"**, y las cards 2 y 3 **desaparecen** (ya no hace falta volver
   a cargar ni a completar nada).
- **Regla de negocio**: ver [RF29_cierre_documental.md](../funcionamiento/RF29_cierre_documental.md).
- **Hallazgos**: ver B76, B77, B78 más abajo; cierra **C38**.

### Verificado con `SELECT` en `sgcpc_db_pruebas`

- **`prestamos`**: `estado_cierre_documental = 'COMPLETO'`, `fecha_cierre_documental =
  2026-10-25 11:37:09`, `cierre_documental_por` = el oficial (id 2),
  `numero_constancia_devolucion = 'DEV-2026-000001'`.
- **`numeradores`**: `('DEVOLUCION', 2026, 1)` — primer uso de este numerador en todo el
  proyecto.
- **`documentos_expediente`**: dos filas nuevas, ambas `fecha_documento = 2026-10-25` —
  `DEVOLUCION_PAGARE_GENERADO` (id 22) y `DEVOLUCION_PAGARE_FIRMADO` (id 23).
- **`auditoria_operaciones`** (25/10/2026, en orden): `GENERAR_PDF_FORMALIZACION` (11:35:36, sobre
  `DOCUMENTO_EXPEDIENTE` id 22), `CARGAR_DOCUMENTO_FORMALIZACION` (11:36:43, id 23),
  `COMPLETAR_CIERRE_DOCUMENTAL` (11:37:09, sobre `PRESTAMO` id 2) — exacto los 3 pasos de la
  pantalla, cada uno auditado por separado.

### Verificado en el código

- **El panel del oficial nunca calcula nada de cierre documental** — revisado `ServicioPanel`
  completo: ninguna referencia a `cierreDocumental`/`CierreDocumental`/
  `listarFinalizadosConCierrePendiente`, el mismo hueco ya confirmado para B45 (Formalización) y
  B63 (diferencias de caja), ahora en una tercera variante. Ver **B76**.
- **La generación de la constancia reutiliza la misma operación de auditoría que cualquier otro
  PDF de Formalización** (`GENERAR_PDF_FORMALIZACION`, no un nombre propio como
  "GENERAR_CONSTANCIA_DEVOLUCION") — dato, no se agrega como hallazgo nuevo porque no es el
  mismo caso que B23 (ese es sobre nombres **ilegibles**, no sobre nombres **genéricos**
  reutilizados).

## Hallazgos de la tercera pasada

- **B76** — El panel del oficial no avisa cuando hay un préstamo con el cierre documental
  pendiente (mismo principio que B45/B63, confirmado ahora en el código: `ServicioPanel` nunca
  calcula este indicador).
- **B77** — Las 3 cards del cierre documental aparecen **de a una**, cada una recién visible
  cuando la anterior ya está resuelta (`docGeneradoCierre != null` para la 2ª, `docFirmadoCierre
  != null` para la 3ª) — aunque cada una ya diga "1.", "2." y "3." en su título, no hay ningún
  aviso previo (antes de generar la constancia) de que el trámite completo son 3 pasos.
- **B78** — La constancia de devolución no tiene ninguna línea que identifique a **quién**
  entregó físicamente el pagaré (el oficial que atendió al cliente) — solo el pie genérico
  "Emitido por" (automático, no una firma) y las firmas de la empresa (representante legal) y
  del cliente.

### Los tres resueltos y probados el 28/10/2026 (B76 a la mañana; B85 + B77 + B78 a la tarde)

**B76** se resolvió a la mañana (tarjeta del panel del oficial, ver `RF30_panel.md` §9).
**B85 + B77 + B78** se resolvieron a la tarde en una sola tanda, con `CC-2026-00001` →
**`DEV-2026-000003`** (oficial Lorenzo Ramírez). Lo que cambió, contra el registro de arriba:

1. La pantalla ya **no se llama "Cierre documental"**: el título es **"Devolución del pagaré —
   CC-2026-00001"**, con el cliente y *"Préstamo cancelado el 27/10/2026"* debajo, y el recuadro
   *"**El cliente ya no debe nada.** Para cerrar el préstamo, devuélvale el pagaré original y
   deje constancia en 3 pasos."* (**B85**).
2. Las **3 cards están desde el inicio** (**B77**), como "Paso 1 de 3 — Generar la constancia de
   devolución", "Paso 2 de 3 — Cargar la constancia firmada por el cliente" y "Paso 3 de 3 —
   Marcar el préstamo como cerrado". La que toca lleva la chapita **"Paso actual"**; las otras
   salen en gris, con el botón deshabilitado y el motivo escrito (*"Primero genere la constancia
   (paso 1)."* / *"Primero cargue la constancia firmada (paso 2)."*). La card del paso 3 **ya no
   desaparece** al cerrar: queda en *"Hecho el … por …"*.
3. El paso 1 avisa *"La constancia sale a su nombre como quien entrega el pagaré."*, y la
   constancia lleva el **tercer bloque de firma "Entregó el pagaré"** con nombre y C.I.
   (**B78**) — en la prueba: *"Lorenzo Ramírez — C.I. N.º 2.345.678 — Entregó el pagaré"*.
4. La confirmación es ahora *"¿Marcar el préstamo como cerrado? No se puede deshacer."* /
   **"Sí, cerrar el préstamo"**, y el cierre muestra *"**Préstamo cerrado.** El pagaré original
   quedó devuelto al cliente el 28/10/2026 13:03, registrado por Lorenzo Ramírez…"*.
5. El vocabulario quedó unificado en **toda la ruta**: tarjeta del panel (*"Pagaré pendiente de
   devolución"* / *"Ir a la devolución del pagaré"*), sección de `/formalizacion` (*"Préstamos
   finalizados — pagaré pendiente de devolución"*, botón *"Devolución del pagaré"*), ficha del
   préstamo y ficha del estado de cuenta. **El `id="cierre-documental"` y las URL no se
   tocaron**, porque de ellos dependen el enlace del menú, el del panel, el script V3h de
   `pie.jsp` y el `scroll-margin-top` de `cabecera.jsp`.

**Hallazgos nuevos de esta prueba**: **B86** (mejora general de UX/UI de todo el sistema, pedido
explícito de Joel) y **B87** (los botones deshabilitados no se leen como deshabilitados; entra
dentro de B86) — los dos en `FASE2_PLAN.md` área B.

**Lo que quedó sin verse en pantalla** (no se marca como probado): el aviso emergente
**"Préstamo cerrado."** y el aviso *"Pagaré pendiente de devolución"* de la ficha del estado de
cuenta, los dos corregidos **después** de la prueba — y ya no queda ningún préstamo con el
pagaré pendiente en la base. Ver `RF29_cierre_documental.md` §10.

## Completitud del tramo 3

Control de completitud contra el código (solo lectura, verificado hoy): todos los caminos que
existen en Formalización, marcando cuáles se recorrieron en pantalla y cuáles quedan
pendientes. Detalle de cada camino ya recorrido en los Pasos 1-10 de arriba y en
[04_que_pasa_por_detras.md](04_que_pasa_por_detras.md).

| Camino | Existe en el código | ¿Se recorrió hoy? |
|---|---|---|
| Formalizar como oficial | `ServicioFormalizacion.generarDocumentos`, permiso `GENERAR_DOCUMENTOS_FORMALIZACION` (oficial TOTAL) | **Sí** — Pasos 3-5 |
| Formalizar como gerente (respaldo, D-68) | Mismo método y mismo permiso, TOTAL también para GERENTE_ADMINISTRADOR (`ServicioAutorizacion.construirMatriz`) — `USUARIO_EMISOR`/"QUIEN LE ATENDIÓ" del contrato mostraría el nombre y C.I. del gerente en vez de la oficial (`prestamo.getFormalizadoPor()`, quien llamó al método) | **No recorrido todavía** — a probar entrando como gerente |
| Fecha fuera de rango (antes de corrimiento) | `validarPrimerVencimientoFormalizacion` rechaza con `NegocioException` (fechas concretas del rango en el mensaje) | **No recorrido** — la fecha usada hoy (21/11/2026) cayó dentro del rango 10/11-05/12 |
| Fecha que, tras el corrimiento a día hábil, queda fuera de rango | `validarPrimerVencimientoEfectivoFormalizacion` rechaza con `FechaFueraDePlazoException` (advertencia, sugiere la última fecha hábil válida) | **No recorrido** — el corrimiento de hoy (21/11 sábado → 23/11 lunes) no llegó a cruzar el límite del rango |
| Afectación actual mayor al límite, al formalizar | `revalidarCapacidadPagoFormalizacion` rechaza con `NegocioException` (mensaje con versión (a)/(b) según `puedeResolver`) | **No recorrido** — el ingreso del cliente no cambió desde la aprobación, la afectación sigue en 9,3 % |
| Sin comprobante de ingreso, al formalizar | Mismo método, revalida `motivoSinComprobanteFormalizacion` | **No recorrido** |
| "Regenerar documentos" con fecha distinta, antes de cargar firmados | `ServicioFormalizacion.regenerarDocumentos` — nueva versión de `planes_pago`/`cuotas`, mismo número de operación, PDF regenerados | **No recorrido** — descrito en el Paso 6, sin ejecutar |
| "Regenerar documentos" con la MISMA fecha | El método no toca nada (ni plan ni PDF), solo audita sin detalle de fecha | **No recorrido** |
| "Re-emitir los PDF (sin cambiar plan)" | `ServicioFormalizacion.regenerarSoloPdfs` — reemplaza los 3 PDF, nunca el plan ni el número | **No recorrido** — descrito en el Paso 5, sin ejecutar |
| Reemplazar un firmado subido por error, ANTES de completar el checklist | `ServicioDocumentos.cargarDocumentoPrestamo` acepta una "nueva versión" sin rechazar; el `<select>` sigue ofreciendo el tipo ya cargado mientras el formulario siga visible | **No recorrido** — hoy se cargó cada tipo una sola vez, sin reemplazar ninguno (ver C7) |
| Corregir un firmado equivocado, DESPUÉS de completar el checklist | **No existe ningún camino desde la pantalla** (el formulario de carga desaparece por completo); el método del servidor no tiene ningún chequeo que lo impida, solo falta la interfaz — ver C7 | **No aplica** (es la ausencia misma del camino) |
| Archivo de tipo no permitido (ej. .docx) | `ServicioDocumentos.validarArchivo` rechaza ("Solo se aceptan archivos PDF, JPG o PNG...") | **No recorrido** |
| Archivo que supera el tamaño máximo (5 MB) | Mismo método, rechaza con el tamaño máximo en el mensaje | **No recorrido** |
| Archivo con la extensión cambiada (firma real no coincide) | Mismo método, rechaza ("Parece un archivo con la extensión cambiada...") | **No recorrido** |
| Intentar formalizar la misma solicitud dos veces (doble clic/F5) | Primer chequeo (`prestamoDAO.obtenerPorSolicitud`) + protección real por `UNIQUE` en `prestamos.id_solicitud` (`SQLState 23505`), traducido al mismo mensaje de negocio | **No recorrido** — verificado solo en el código (Sección 5 del anexo) |
| Anular el préstamo antes del desembolso | `ServicioFormalizacion.anularAntesDeDesembolso`, solo gerente/administrador (chequeo de perfil explícito en el controlador, no por `FiltroAutorizacion`) | **No recorrido** — ver también `03_aprobacion.md` § Reglas de decisión del crédito |
| Acceso por URL directa de un rol sin permiso (ej. cajera a `/formalizacion`) | `FiltroAutorizacion` exige `GENERAR_DOCUMENTOS_FORMALIZACION`/`CARGAR_DOCUMENTOS_CONSULTA_BURO`, `NINGUNO` para cajero → 403 | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Formalización) |
| Cierre documental / devolución del pagaré | `mostrarCierreDocumental`, `procesarGenerarConstanciaDevolucion`, `procesarCompletarCierreDocumental` — corresponde a un préstamo `FINALIZADO`, etapa posterior (Cobranza/Cancelación) | **Sí — recorrido el 25/10/2026** (ver "Tercera pasada de formalización: cierre documental" arriba), primera vez en todo el proyecto — `DEV-2026-000001`, `CC-2026-00002` |


## 01/11/2026 — lo que vi al formalizar un día 1

Registro con fecha (el estado de cada ítem vive en [FASE2_PLAN.md](FASE2_PLAN.md), no acá).

- **12:31-12:39, Ramón, `SOL-2026-0007` (Ana).** Aprobada a las 12:31 y formalizada a las 12:39
  como **`CC-2026-00006`**: 12 cuotas, primer vencimiento 01/12/2026, cuota 1 de 193.280 Gs.
- **El contrato dice ahora "al primer día del mes de noviembre de dos mil veintiséis"**, con la
  preposición y el artículo concordados con el día. Antes decía *"a los primer día"* — el
  `"a los "` era texto fijo de la cláusula y el día 1 viene en singular. Ítems
  [B11](FASE2_PLAN.md) y [B103](FASE2_PLAN.md); el detalle de cómo se arma la frase vive en
  [`RF12_documentos_pdf.md`](../funcionamiento/RF12_documentos_pdf.md).
- **La TNA del contrato sale con sus cuatro decimales: 24,1413 %** — es el valor real de
  `prestamos.tasa_tna`, y `FormateadorNumeros.comoPorcentaje` no redondea.
- **Re-guardé el domicilio de Ana como "general santos 123"** y quedó guardado
  **"General Santos 123"**; con *"Re-emitir los PDF"* (mismo número, D-94) el contrato pasó a
  decir *"con domicilio en General Santos 123, Jara, Asunción"*. El *"Emitido por"* conservó las
  **12:39**, la hora de la formalización. Ítem [B13](FASE2_PLAN.md).
- **Después de "Aprobar", la ficha de la solicitud no ofrece ningún botón ni enlace a
  Formalización**, aunque el cartel dice *"Ahora se formaliza en Formalización"*. Anotado como
  [B104](FASE2_PLAN.md), no se tocó. Los carteles de *Aprobar* y *Generar documentos* siguen
  genéricos ([B43](FASE2_PLAN.md), [B4](FASE2_PLAN.md), ya anotados).
