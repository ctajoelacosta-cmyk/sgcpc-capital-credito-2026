# Escena 03 — Aprobación

**Tramo 2 del recorrido, probado en pantalla por Joel el 21/10/2026, rol gerente (en otra
ventana, con el oficial todavía conectado — varios usuarios pueden estar conectados a la vez),
desde el login hasta aprobar la solicitud.** Cliente de ejemplo ficticio: **Marta Ovelar (C.I.
1.000.001)**, solicitud de ejemplo `SOL-2026-0001` (ver
[01_llega_el_cliente.md](01_llega_el_cliente.md) y [02_solicitud.md](02_solicitud.md)).

**Qué pasa por detrás** (tablas, parámetros, auditoría y condiciones de cada botón, verificado
contra el código): [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md).

## Paso 1 — Login del gerente (RF36)

- **Usuario/rol**: Gerente administrador, en una ventana/navegador distinto del oficial.
- **Pantalla y ruta**: `/acceso`.
- **Qué hace el usuario**: inicia sesión mientras el oficial sigue conectado en su propia
  ventana — el sistema admite varios usuarios conectados a la vez, sin pisarse.
- **Regla de negocio**: ver [procesos/login.md](../funcionamiento/procesos/login.md).
- **Hallazgos**: ninguno propio de este paso.

## Paso 2 — Panel del gerente (RF30)

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: `/panel`.
- **Qué hace el usuario**: entra al panel después de loguearse.
- **Qué responde el sistema**: 10 indicadores, agrupados en 3 bloques — **Hoy**: cobros de hoy,
  promesas de hoy, "Sin caja abierta", y "1 solicitudes pendientes de aprobar" con el enlace "Ir
  a Aprobaciones"; **Cartera**: préstamos en mora, cartera en la calle, cobrado en la semana,
  cobrado en el mes; **Pendientes**: préstamos pendientes de desembolso, reversiones por
  autorizar.
- **Regla de negocio**: ver [RF30_panel.md](../funcionamiento/RF30_panel.md).
- **Qué sigue**: "Ir a Aprobaciones" (Paso 3) o cualquier otro módulo del menú.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: panel del gerente (22/10/2026, segundo cliente de práctica)

Mismo panel, en una ventana de incógnito, con el oficial todavía conectado en la suya — más
tarde en el circuito (ya con el segundo cliente de práctica enviado a aprobación, un préstamo
del primer cliente ya desembolsado). Saludo: **"Buenas noches, [nombre]"** (según la hora del
servidor). "Este es el resumen de hoy."

- **"Hoy"**: Cobros de hoy (0), Promesas de hoy (0) con "Ir a Seguimiento de mora", Efectivo en
  caja (0 Gs.) con "Ir a Caja", **"1 Solicitudes pendientes de aprobar"** con "Ir a
  Aprobaciones".
- **"Cartera"**: Préstamos en mora (0), **Cartera en la calle (capital pendiente): 3.000.000
  Gs.** (el capital pendiente del préstamo ya desembolsado del primer cliente), Cobrado esta
  semana (0), Cobrado este mes (0).
- **"Pendientes"**: préstamos pendientes de desembolso (0 · 0 listos), Reversiones por autorizar
  (0) con "Ir a Reversiones".
- **Menú del gerente**: Panel, Clientes y solicitudes, Aprobación, Formalización, Desembolso y
  caja, Cobranza y mora, Cancelación, Informes, Seguridad.

**Comparado con el panel del 21/10/2026** (ver arriba, "Qué responde el sistema"): la estructura
de los 3 bloques es la misma. Lo que cambió es el contenido, porque el circuito avanzó —
"Efectivo en caja" ya no dice "Sin caja abierta" (ahora hay una sesión abierta, con 0 Gs.
porque no se le entregó fondo todavía), y "Cartera en la calle" pasó de 0 a 3.000.000 Gs. (ya
hay un préstamo desembolsado). "1 Solicitudes pendientes de aprobar" se repite — hoy es la
solicitud del segundo cliente de práctica, no la de Marta Ovelar (que ya se aprobó y formalizó).

**Verificado en el código (22/10/2026)**:

- **Un método único, `ServicioPanel.calcularIndicadores`, arma los 10 indicadores** (dto
  `IndicadoresPanel`), reutilizando SIEMPRE el servicio que ya calcula cada dato en su propio
  módulo — "ninguna cuenta financiera nueva acá" (comentario del propio archivo). Por tarjeta:
  - Cobros de hoy: `PagoDAO.contarConfirmadosEntreFechas`/`sumarAplicadoEntreFechas` (hoy-hoy).
  - Promesas de hoy: `GestionCobranzaDAO.contarPromesasDeHoy` (oficial, gerente y administrador).
  - Efectivo en caja: `ServicioCaja.obtenerSesionAbierta` + `calcularSaldoTeorico` (cajero y
    gerente; si no hay sesión abierta, la tarjeta muestra "Sin caja abierta" en vez de un
    monto).
  - **"Solicitudes pendientes de aprobar"**: `SolicitudDAO.listarPendientesAprobacion(conexion).
    size()` — mismo método ya documentado en `02_solicitud.md` para "cómo se entera el
    gerente".
  - Préstamos en mora: `PrestamoDAO.listarEnMora(conexion).size()` — los 3 perfiles.
  - **Cartera en la calle**: `CuotaDAO.sumarCapitalPendienteActivos(conexion)` — suma el
    **capital pendiente de las cuotas de los préstamos `ACTIVO`** (no el monto aprobado
    original ni el saldo de todos los préstamos sin distinguir estado); solo gerente y
    administrador.
  - Cobrado esta semana / este mes: `PagoDAO.sumarAplicadoEntreFechas`, con
    `hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))` y `hoy.withDayOfMonth(1)`
    como inicio de cada rango.
  - Préstamos pendientes de desembolso / listos: `PrestamoDAO.listarPendientesDesembolso` +
    `ServicioFormalizacion.listarListosParaDesembolsar` (filtra por checklist completo) — cajero,
    gerente y administrador.
  - Reversiones por autorizar: `ServicioReversiones.listarSolicitadas(conexion).size()` —
    gerente y administrador.
- **De dónde sale el saludo**: `panel.jsp` calcula `Reloj.ahora().getHour()` (hora del
  **servidor**, no la del navegador — mismo criterio que la fecha de la barra superior) y
  arma el texto en la propia vista, sin pasar por `ServicioPanel`: menos de 12 → "Buenos días";
  menos de 19 → "Buenas tardes"; si no, "Buenas noches". El nombre es
  `usuarioPanel.getNombres()` (solo el/los nombres, sin apellido).
- **Qué dispara el panel, además de mostrar los indicadores**: `calcularIndicadores` arranca
  llamando a `ServicioMora.recalcularMorosidadDiaria` — el panel (junto con `/mora`) es quien
  dispara el recálculo diario de mora, no una tarea aparte.

Ver B36 y B37 en Hallazgos.

## Paso 3 — Bandeja de aprobación (RF07)

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: `/aprobaciones` (pestañas Pendientes/Historial, vía `?vista=historial`).
- **Qué hace el usuario**: ve la pestaña Pendientes, con las solicitudes en orden de llegada;
  puede buscar por código, cédula o nombre; toca "Revisar" en `SOL-2026-0001`.
- **Datos que pide**: ninguno para listar — el buscador es de texto libre, opcional.
- **Qué responde el sistema**: columnas Código, Enviada (fecha), Esperando (cuenta desde el
  envío a aprobación — el sistema no registra la llegada física del cliente, así que "Esperando"
  mide desde que la solicitud quedó pendiente, no desde que el cliente se sentó a esperar; es lo
  correcto, no hace falta otra marca de tiempo), Cliente, Monto, Plazo, Afectación, y el botón
  "Revisar".
- **Regla de negocio**: ver [RF07_bandeja_aprobacion.md](../funcionamiento/RF07_bandeja_aprobacion.md).
- **Qué sigue**: "Revisar" (Paso 4).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: la bandeja en detalle (22/10/2026, segundo cliente de práctica)

El gerente tocó **"Ir a Aprobaciones"** desde su panel y llegó a `/aprobaciones`. Título
**"Aprobaciones"**, descripción **"Solicitudes pendientes de aprobación, en orden de
llegada."**; pestañas **"Pendientes"** e **"Historial"**; buscador con la etiqueta **"N.º de
solicitud, cédula o nombre del cliente"**, botones **"Limpiar"** y **"Buscar"**; tabla con
**Código**, **Enviada** (fecha), **Esperando** (tiempo desde el envío, ej. "51 min"),
**Cliente** (nombre y C.I.), **Monto solicitado**, **Plazo**, **Afectación**, y el botón
**"Revisar"**.

**Corrige lo escrito el 21/10/2026**: la descripción exacta de la pantalla ("Aprobaciones" /
"Solicitudes pendientes de aprobación, en orden de llegada.") y la etiqueta exacta del buscador
("N.º de solicitud, cédula o nombre del cliente") no estaban anotadas; se agregan acá. El resto
(columnas, "Revisar", pestañas) coincide con lo ya escrito.

**Verificado en el código (22/10/2026)**:

- **Cómo se calcula "Esperando"** — `ServicioAprobacionCredito.construirFilaBandeja`:
  `Duration.between(fecha_envio_aprobacion, Reloj.ahora()).toMinutes()`, formateado por
  `FormateadorFechas.comoDuracion`: menos de 60 minutos → **"N min"**; de 1 a 23 horas → **"N
  h"** o **"N h M min"** si sobran minutos; 24 horas o más → **"N día(s)"** o **"N día(s) M h"**
  si sobran horas (singular exacto para "1 día", a diferencia de B36 del Panel). Se calcula
  siempre desde `fecha_envio_aprobacion` (cuando el oficial envió a aprobación) — nunca desde
  que el cliente llegó físicamente a la sucursal, porque el sistema no registra ese momento;
  es el criterio correcto, no hace falta otra marca de tiempo.
  **No cambia de color ni muestra ninguna alerta pasado cierto tiempo** — es texto plano en la
  celda, sin ninguna clase CSS ni condición en `aprobaciones/_resultados.jsp` que dependa de
  cuánto lleve esperando (a diferencia de, por ejemplo, el aviso de timbrado del panel, que sí
  tiene su propia lógica de alerta).
- **Orden**: `SolicitudDAO.listarPendientesAprobacion` — `ORDER BY fecha_envio_aprobacion` (sin
  `DESC`), **las más antiguas primero** — comentario del propio DAO: "más antiguas primero
  (orden de atención)". Coincide con la descripción de la pantalla ("en orden de llegada").
- **Qué muestra "Historial"** — `SolicitudDAO.listarDecididas`/`listarDecididasConFiltro`:
  solicitudes `APROBADA`, `RECHAZADA` o `CANCELADA`, **las más recientes primero**
  (`ORDER BY COALESCE(fecha_decision, fecha_cancelacion) DESC`). Columnas distintas de
  Pendientes: sin "Esperando", y con **Decisión** (badge gris con la etiqueta del estado),
  **Decidida por** y **Fecha de decisión** en su lugar; el botón dice **"Ver"**, no "Revisar".
  Esta pestaña tiene, además, sus propios filtros — un `<select>` **Estado** ("Todas" /
  Aprobada / Rechazada / Cancelada) y un rango de fechas **desde/hasta** (sobre la fecha de la
  decisión) — y un enlace a `/reportes/aprobaciones` (exportar), que Pendientes no tiene.

Ver B38 en Hallazgos.

## Paso 4 — Revisión de la solicitud, antes de decidir (RF08)

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: `/aprobaciones/{id}`.
- **Qué hace el usuario**: lee el resumen completo de `SOL-2026-0001` antes de decidir.
- **Qué responde el sistema**: datos del cliente (edad, zona, situación laboral, empleador,
  otras deudas), la ficha/expediente del cliente, condiciones solicitadas (monto, plazo, cuota,
  TEA/TNA/CFT) **de solo lectura**, con la afectación en 9,3 % mostrada en verde (dentro del
  límite); el checklist de 8 requisitos, todos ✓; el resultado del buró con su evidencia
  (puntaje, clasificación, mora, con el PDF cargado); los botones **Aprobar**, **Rechazar**,
  **Cancelar solicitud**, y "← Volver a la bandeja". **El comprobante de domicilio no aparece en
  ningún lado de esta pantalla** (no forma parte de los 8 requisitos ni del resumen) — mismo
  hallazgo ya registrado en la Escena 01, con la decisión del 21/10/2026 ya tomada
  (obligatorio, implementación pendiente).
- **Regla de negocio**: ver [RF08_resumen_solicitud.md](../funcionamiento/RF08_resumen_solicitud.md)
  y el detalle de qué se recalcula en vivo vs. qué queda congelado en
  [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md).
- **Qué sigue**: Aprobar, Rechazar o Cancelar — ver "Reglas de decisión del crédito" más abajo.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: la ficha de revisión en detalle (22/10/2026, segundo cliente de práctica)

El gerente tocó **"Revisar"** y llegó a `/aprobaciones/{id}`. Título **"Solicitud
SOL-[año]-[número]"**, con **"Pendiente de aprobación"**.

- **Tarjeta "Cliente"**: nombre y C.I.; **"Ingreso neto comprobado (evaluado al enviar)"**;
  enlace **"Ver ficha / expediente"**; una línea con **Edad**, **Zona** (departamento/ciudad),
  **Situación laboral**, **Empleador** (con la antigüedad entre paréntesis), **Cuota de otras
  deudas**; debajo, los **8 requisitos** con ✓/✗, recalculados en el momento (no una foto
  vieja de cuando se envió).
- **"Condiciones solicitadas y simulación"**: monto (número y, debajo, en letras), plazo,
  destino, primer vencimiento estimado, cuota más alta (con IVA), TEA/TNA, CFT, total a pagar,
  y la afectación al ingreso resaltada en **verde**.
- **"Resultado del buró (Informconf)"**: Persona, Fecha, Puntaje, Clasificación, ¿Mora?,
  Resumen, y "Ver" para la evidencia.
- Botones: **"Aprobar"** (verde), **"Rechazar"** (rojo), **"Cancelar solicitud"** (con solo el
  borde en rojo, `btn-outline-danger`), y **"Volver a la bandeja"**.

**Corrige lo escrito el 21/10/2026**: coincide en lo esencial (condiciones de solo lectura,
checklist, buró, los 3 botones); se agrega acá el detalle exacto de cada tarjeta (línea de
edad/zona/situación/empleador/deudas, enlace "Ver ficha / expediente", destino y fecha
explícitos en la tabla de condiciones) que no estaba anotado.

**Verificado en el código (22/10/2026)**:

- **Criterio de color de la afectación** — `aprobaciones/ficha.jsp`: la fila de la tabla usa
  `superaLimiteRevision ? "table-warning" : "table-success"`. Son **solo dos** colores, no tres:
  **verde** si la afectación congelada es menor o igual al límite aplicado, **amarillo/ámbar**
  (`table-warning` de Bootstrap) si lo supera — **nunca rojo** en esta fila puntual (el rojo de
  esta pantalla lo usa, en otro lugar, la franja de mora/riesgo alto y la fila de la consulta
  del buró con mora, no la afectación).
- **¿El gerente puede modificar monto o plazo antes de aprobar?** **No.** La tabla de
  "Condiciones solicitadas y simulación" es HTML plano, sin ningún `<input>` — no hay ningún
  formulario para cambiar monto, plazo ni ninguna otra condición en esta pantalla.
  `ServicioAprobacionCredito.aprobar(long idSolicitud, long idUsuario)` ni siquiera recibe un
  monto o plazo como parámetro — el propio comentario del método lo dice explícito: **"RN-07:
  nunca otro monto/plazo"**. Es la misma regla ya documentada en "Reglas de decisión del
  crédito" (más abajo): "Se aprueba exactamente lo solicitado". Ver C18 (Por diseño, cerrado el
  22/10/2026).
- **Confirmaciones de Aprobar/Rechazar**: las dos exigen confirmar, con textos distintos.
  "Aprobar" confirma de inmediato al tocarlo: **"¿Aprobar esta solicitud?"** con **"Sí,
  aprobar"** — o, si la clasificación es de riesgo alto (D/E) sin mora, el texto reforzado
  **"Informconf indica clasificación de riesgo alto (D/E). ¿Aprobar igualmente?"**. "Rechazar"
  es un paso más largo: primero despliega un formulario para elegir el motivo (obligatorio) y
  un detalle (obligatorio solo si el motivo es "Otro"), y recién al tocar **"Confirmar
  rechazo"** pide **"¿Rechazar esta solicitud?"** con **"Sí, rechazar"**. Ver B41.

**Nota aclaratoria (22/10/2026) — verificación urgente sobre el documento de evidencia del
buró**: al tocar "Ver" en la evidencia del buró de `SOL-2026-0002` (segundo cliente de
práctica), se abrió un PDF que parecía ser la autorización firmada del primer cliente
(`AUT-2026-00001`, generada el 21/10 16:13). Verificado con `SELECT` en `sgcpc_db_pruebas`:
**no es un error de código — el sistema abre el documento correcto**.
`consultas_crediticias.id_documento_resultado` de la consulta del segundo cliente apunta al
documento correcto (persona correcta, tipo `RESULTADO_CONSULTA_BURO` correcto); el botón "Ver"
arma el enlace directo con ese id (`ServicioAprobacionCredito.construirFilasConsultaBuro`) hacia
`GET /documentos/{id}`, y `ControladorDocumentosServlet.descargar` sirve exactamente ese
documento, sin ningún cruce que pudiera mezclarlo con otra persona. Lo que pasó es que, al
probar con el segundo cliente, **se cargó el mismo archivo genérico de prueba "ejemplo.pdf"**
para varios documentos de ese cliente (la autorización y el informe de Informconf) — y ese
"ejemplo.pdf" resultó ser, casualmente, una **copia byte a byte** (mismo tamaño, mismo hash
SHA-256 en `archivos_binarios`) de la autorización firmada que de verdad se usó para el primer
cliente. Es un artefacto de los datos de prueba, no un error del sistema; el documento no
contiene ningún dato personal real (es el mismo archivo de prueba reutilizado). Ver B42 y C19
en Hallazgos.

Ver B40, B41, B31 (se repite), B13 (se repite) y C18 (Por diseño, cerrado) en Hallazgos.

## Paso 5 — Aprobar (RF09)

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: `/aprobaciones/{id}` (botón "Aprobar") → `POST /aprobaciones/{id}/aprobar`.
- **Qué hace el usuario**: toca "Aprobar", confirma el diálogo "¿Aprobar esta solicitud?" con
  "Sí, aprobar" (el botón alternativo del diálogo dice "Cancelar").
- **Qué responde el sistema**: mensaje flash "Solicitud aprobada. Ahora se formaliza en
  Formalización." (botón "Aceptar"). La ficha vuelve a mostrarse, ahora con el estado
  `APROBADA`: la franja verde dice "Aprobada por `<gerente>` el `<fecha y hora>`. Monto
  aprobado: 3.000.000 Gs., plazo: 12 meses." **Aprobar/Rechazar ya no aparecen** (la pantalla
  solo los muestra mientras la solicitud sigue `PENDIENTE_APROBACION`); "Cancelar solicitud"
  **sigue disponible** (la solicitud sigue `APROBADA` sin formalizar — ver el hallazgo de
  separar visualmente esta acción de las de decisión, más abajo); "← Volver a la bandeja" sigue
  visible. No se tocó "Cancelar solicitud" en esta prueba.
- **Regla de negocio**: ver [RF09_aprobar.md](../funcionamiento/RF09_aprobar.md) y "Reglas de
  decisión del crédito" más abajo (revalidación de tope de tasa, afectación y sin mora en
  Informconf, todo ANTES de guardar nada).
- **Qué cambia en la base y qué registra la auditoría**: ver
  [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md) § Aprobar — `solicitudes_credito`
  pasa de `PENDIENTE_APROBACION` a `APROBADA` (con el snapshot de condiciones aprobadas y
  `decidido_por`), se vincula la consulta al buró más reciente si es distinta de la ya
  vinculada al enviar, y queda `APROBAR_SOLICITUD` ("Aprobar solicitud") en la auditoría, con
  el detalle de qué consulta(s) vio el gerente al decidir.
- **Qué sigue**: la solicitud queda lista para formalizar — ver
  [04_formalizacion.md](04_formalizacion.md).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: aprobar en detalle (22/10/2026, segundo cliente de práctica)

Orden real observado: 1) confirmación **"¿Aprobar esta solicitud?"** con **"Sí, aprobar"** y
**"Cancelar"**; 2) cuadro de éxito **"Solicitud aprobada. Ahora se formaliza en
Formalización."** con **"Aceptar"**; 3) la pantalla queda en `/aprobaciones/{id}` con el estado
**"Aprobada"**, la línea **"Aprobada por [usuario] el [fecha hora]. Monto aprobado: [monto],
plazo: [n] meses."**; ya no aparecen **"Aprobar"**, **"Rechazar"** ni la lista de los 8
requisitos; siguen **"Cancelar solicitud"** y **"Volver a la bandeja"**. La pantalla **no vuelve
sola a la bandeja** — hay que tocar "Volver a la bandeja" para salir.

**Verificado en el código y con `SELECT` en `sgcpc_db_pruebas`**:

- **Método que aprueba**: `ServicioAprobacionCredito.aprobar(long idSolicitud, long idUsuario)`.
- **Estado y campos guardados** (`SolicitudDAO.marcarDecidida`, confirmado con `SELECT` sobre
  `SOL-2026-0002`): `estado_solicitud = 'APROBADA'`; `monto_aprobado` y
  `plazo_aprobado_meses` iguales a los solicitados (RN-07, ver C18); también
  `tasa_tna_aprobada`, `tasa_tea_aprobada`, `cft_aprobado`,
  `primera_fecha_vencimiento_aprobada` e `id_limite_tasa_aplicado` (una copia "aprobada" de las
  condiciones, separada de la simulación); `decidido_por` (el usuario) y
  `fecha_decision = now()`; `motivo_rechazo` se limpia a `NULL`. El `UPDATE` exige
  `estado_solicitud = 'PENDIENTE_APROBACION'` en el propio `WHERE` — red de seguridad real
  contra una carrera de dos pestañas (dos gerentes decidiendo la misma solicitud a la vez).
- **Qué queda congelado**: todo lo de "Qué queda congelado" del envío a aprobación
  (`02_solicitud.md`) sigue igual (ingreso evaluado, afectación); al aprobar se agrega una
  segunda fotografía — las condiciones **aprobadas** (siempre idénticas a las solicitadas) y
  qué tope de tasa estaba vigente en ese momento (`id_limite_tasa_aplicado`).
- **Auditoría**: `APROBAR_SOLICITUD` (SOLICITUD_CREDITO) — confirmado con `SELECT`:
  *"Aprobó la solicitud SOL-2026-0002. Consulta(s) vista(s) al decidir: [id 2, 22/10/2026,
  clasificación A, mora no]"* — el detalle deja constancia de qué consulta al buró vio el
  gerente al decidir, no solo que aprobó.
- **Cómo se entera el oficial**: **no hay ningún aviso directo** (ni en su Panel ni en ningún
  otro lado) — y, puntualmente, el indicador **"Mis solicitudes en curso"** del Panel del
  oficial (`ServicioPanel.calcularIndicadores`) solo cuenta `BORRADOR` y
  `PENDIENTE_APROBACION`: **al aprobarse, la solicitud deja de contar ahí** (no hay ningún
  indicador de "aprobadas, listas para formalizar"). El único lugar donde el oficial la
  encuentra es entrando directamente a **Formalización** — su bandeja
  (`SolicitudDAO.listarAprobadasSinFormalizar`, `estado_solicitud = 'APROBADA' AND NOT
  formalizada`) la lista ahí, sin que el Panel se lo haya avisado antes.

Ver B43, B44, B45, B3 (se repite) y C20 en Hallazgos.

## Reglas de decisión del crédito — probado en pantalla el 17/10/2026, a reconfirmar en el recorrido

Probado por Joel con la solicitud de ejemplo (tag `reglas-decision-probado`; consolidado contra
el código el mismo día — ver el detalle técnico en
[RF06_envio_aprobacion.md](../funcionamiento/RF06_envio_aprobacion.md),
[RF10_rechazar.md](../funcionamiento/RF10_rechazar.md) y
[RF14_anular_prestamo.md](../funcionamiento/RF14_anular_prestamo.md)).

**Cuándo una solicitud llega a aprobación (filtros automáticos, el sistema no deja avanzar):**
- Al crear (Escena 02): cliente activo; edad 22 a 71; zona Asunción o Central; situación laboral
  admitida (ingreso fijo comprobable); monto 500.000 a 15.000.000; plazo 3 a 24; una sola
  solicitud abierta y ningún préstamo sin cerrar.
- Al enviar (Escena 02): **se revalida todo de nuevo con los datos ACTUALES**, no solo lo que se
  completó al crear — cliente activo; política de crédito (edad, zona, situación laboral); cédula
  vigente; comprobante de ingreso cargado; autorización del buró vigente y consulta a Informconf
  registrada con evidencia; afectación (cuota + otras deudas) ≤ 40 % del ingreso; **sin mora
  registrada en Informconf (RN-18, bloqueo automático)**. Al enviar se congela la evaluación
  (ingreso evaluado, afectación, límite).

**Qué decide el gerente (juicio humano sobre lo que el sistema ya filtró):**
- **Aprobar**: la evaluación es aceptable. El sistema revalida al aprobar: sigue pendiente, tope
  de tasa vigente, afectación dentro del límite, y **sin mora en Informconf (RN-18) — si la
  consulta más reciente del cliente indica mora (incluida una registrada DESPUÉS del envío),
  bloquea la aprobación sin excepción; no existe "¿Aprobar igualmente?"**. Riesgo alto
  (clasificación D/E) SIN mora sigue siendo juicio del gerente, con una confirmación reforzada.
  Se aprueba exactamente lo solicitado (nunca otro monto, plazo o tasa).
- **Rechazar** (motivo de una lista): Informconf con deudas; capacidad de pago insuficiente;
  documentación incompleta o inconsistente; otro (con detalle). Queda RECHAZADA para siempre.
  Ejemplos: el cliente quiere cambiar sus ingresos después del envío → "Documentación incompleta
  o inconsistente"; una mora aparece después del envío y el sistema bloqueó la aprobación → el
  gerente rechaza con "Informconf: figura con deudas".

**Cancelar, rechazar y anular no son lo mismo:**
- **Cancelar** (motivo libre, obligatorio): el trámite se corta sin juicio sobre el crédito — el
  cliente desiste o hubo un error de carga. Oficial en borrador; gerente en pendiente o aprobada
  sin formalizar.
- **Rechazar**: decisión del gerente de que el crédito no corresponde. Solo en pendiente de
  aprobación.
- **Anular préstamo** (motivo ≥ 10 caracteres): préstamo ya formalizado que no se desembolsa.
  Solo el gerente, solo antes del desembolso (ver [04_formalizacion.md](04_formalizacion.md)).
- En los tres casos nada se borra y el cliente puede iniciar una solicitud nueva.

**Controles posteriores a la aprobación:**
- Al formalizar se revalida el tope de tasa y el 40 % con el ingreso actual del cliente (segundo
  control, RF11 — ver [04_formalizacion.md](04_formalizacion.md)).
- Sin los 3 firmados (contrato, pagaré y plan) no hay desembolso.

**Bloqueo de edición de los datos de evaluación (implementado el 17/10/2026, mensaje por
permiso desde el paso F2 de la tanda de usabilidad — a reconfirmar en el recorrido):** con una
solicitud `PENDIENTE_APROBACION`, una solicitud `APROBADA` sin formalizar, o un préstamo
`PENDIENTE_DESEMBOLSO`, 7 datos del cliente quedan de solo lectura en la ficha — los 5 laborales
(situación laboral, comprobante de ingreso, empleador, ingreso neto comprobado, cuota de otras
deudas) más la fecha de nacimiento y la ciudad del domicilio (definen la edad y la zona ya
evaluadas). El resto de la ficha (nombre, teléfono, domicilio salvo la ciudad, cónyuge,
referencias) se sigue editando sin restricción, aunque haya una evaluación en curso. Mientras la
solicitud sigue en `BORRADOR`, nada se bloquea.

Si el cliente dice que esos datos cambiaron, el mensaje en pantalla depende de si quien está
viendo la ficha puede resolverlo él mismo (`ServicioClientes.mensajeEvaluacionBloqueada`, según
el permiso para la acción correspondiente — nunca por nombre de rol):
- **Oficial y cajera** (no pueden resolverlo): "Si los datos del cliente cambiaron, informe al
  gerente para que la rechace" (solicitud pendiente) / "...para que la cancele" (aprobada sin
  formalizar) / "...para que lo anule" (préstamo pendiente de desembolso) — siempre "registrando
  el motivo".
- **Gerente** (puede resolverlo desde su propia pantalla): "Puede rechazarla desde Aprobación"
  (solicitud pendiente) / "Puede cancelarla desde Aprobación" (aprobada sin formalizar) / "Puede
  anularlo desde Formalización" (préstamo pendiente de desembolso) — siempre "registrando el
  motivo, si los datos del cliente cambiaron".

No se creó ningún flujo nuevo: rechazar (RF10), cancelar (RF06) y anular (RF14) ya existían.

**Ingreso evaluado, no el actual (implementado el 17/10/2026):** una vez que la solicitud ya no
está en `BORRADOR`, la ficha de la solicitud (oficial) y el resumen del gerente muestran el
ingreso EVALUADO (congelado al enviar a aprobación, columna `ingreso_cliente_evaluado`), no el
valor actual de la ficha del cliente — así el número mostrado siempre coincide con el que de
verdad se usó para calcular la afectación.

**Hallazgo de esta prueba, ya corregido**: el `<select>` Departamento de Precalificación quedaba
editable aunque el resto de los datos de evaluación estuviera bloqueado — al cambiarlo, el JS de
cascada repoblaba Ciudad y la ciudad bloqueada dejaba de pertenecer al departamento enviado, dando
"La ciudad elegida no pertenece al departamento seleccionado." al guardar. Corregido: Departamento
también queda `disabled` + oculto con el valor real (`clientes/ficha.jsp`), y
`ServicioClientes.validarCiudadPerteneceADepartamento` ya no exige `idDepartamento` si no llega
(confía en el departamento real de la ciudad ya guardada). Caso nuevo en `pruebas/humo_http.sh`.
Reverificado en pantalla por Joel: Departamento bloqueado, "Ficha actualizada" al cambiar solo el
teléfono, sin errores. `mvn test` 775/775; humo 913/922 (los 9 de codeudor, preexistentes).

## Hallazgos de esta escena (tramo 2, gerente)

Separados de "Cómo funciona hoy" (arriba, ficha por paso). Todos tipo **B** (mejora de uso, no
cambian ningún RF).

### Panel (RF30)

- **B** — "1 solicitudes pendientes de aprobar" podría ir primero y resaltado entre los 10
  indicadores, en vez de mezclado con el resto del bloque "Hoy".
- **B** — El menú no muestra ningún número junto a "Aprobación" (ej. "Aprobación (1)") — hoy
  hay que entrar al panel o a la bandeja para saber cuántas esperan.
- **B36** — Falta de concordancia singular/plural: **"1 Solicitudes pendientes de aprobar"**
  (confirmado de nuevo el 22/10/2026, con la solicitud del segundo cliente de práctica) —
  `panel.jsp` concatena el número con el texto fijo **"Solicitudes pendientes de aprobar"**, sin
  ningún singular. **Revisado el resto de las tarjetas del panel con el mismo problema**: les
  pasa lo mismo a "Préstamos en mora", "Promesas de hoy", "Cobros de hoy", "Reversiones por
  autorizar" y "Mis solicitudes en curso" (oficial) — todas concatenan el número con una
  etiqueta fija en plural, sin importar si el número es 0, 1 o más. **Ya está resuelto, y
  correctamente, en una sola tarjeta**: "préstamos pendientes de desembolso" (bloque
  "Pendientes") usa un singular/plural real (`préstamo<%= ... == 1 ? "" : "s" %> pendiente<%=
  ... %> de desembolso · N listo<%= ... %> para desembolsar`) — sirve de modelo a copiar en las
  demás si se corrige.
- **B37 (para el dueño de Capital Crédito)** — El panel muestra, al mismo tiempo, "Efectivo en
  caja: 0 Gs." y "1 Solicitudes pendientes de aprobar", sin relacionar los dos datos: si el
  gerente aprueba esa solicitud, la caja va a necesitar entregar el monto aprobado en efectivo,
  y hoy no hay ningún aviso de cuánto. Sugerencia: un aviso del tipo **"Si aprueba, la caja
  necesitará [monto]."**, cruzando `getSolicitudesPendientesAprobar` (o, mejor, la suma de los
  montos solicitados de esas solicitudes) con `getEfectivoEnCaja`/`isHayCajaAbierta` —
  `ServicioPanel` ya tiene los dos datos por separado, pero ningún método los cruza hoy. No
  decido acá si esto amerita cambio de código — queda para que Joel o el dueño de Capital
  Crédito lo evalúe.

### Bandeja (RF07)

- **B** — Sin columna de clasificación del buró (A-E) ni de mora en la tabla — hoy hay que
  entrar a "Revisar" para verlas.
- **B** — Sin color por nivel de afectación (ej. verde/amarillo/rojo según qué tan cerca está
  del 40 %) ni por tiempo de espera (ej. resaltar una solicitud que espera hace mucho).
- **B** — Solo el botón "Revisar" abre la ficha; toda la fila podría ser clickeable.
- **B38 (menor)** — La bandeja no muestra el resultado del buró (clasificación/mora) — hay que
  tocar "Revisar" para verlo. Con una sola solicitud pendiente hoy no se nota, pero con varias
  en la lista, no hay forma de ver el riesgo de un vistazo sin entrar a cada una. **Verificado
  en el código**: `ControladorAprobacionesServlet.mostrarLista` no consulta
  `ConsultaCrediticia` para armar la lista (solo resuelve clientes y, en Historial, decisores);
  `ServicioAprobacionCredito.construirFilaBandeja`/`FilaBandejaAprobacion` tampoco traen ese
  dato. Agregar una columna (ej. clasificación con su color, o un ícono si hay mora) sí
  necesitaría una consulta más por fila en la lista — el método que ya existe y se reutiliza en
  otras pantallas es `ServicioConsultaCrediticia.obtenerMasRecienteConEvidencia`, no haría falta
  SQL nuevo, solo llamarlo también desde este controlador.
- **B39** — El tiempo "Esperando" de la bandeja (ver B de arriba, "ni por tiempo de espera") no
  solo no tiene color por afectación: **nunca cambia de color ni muestra ninguna alerta, sin
  importar cuánto lleve esperando una solicitud** (confirmado en el código, ver "Registro
  adicional" del Paso 3). Sugerencia: amarillo pasadas N horas y rojo al día siguiente — guarda
  relación con la promesa de atención "sale en el mismo día" ya citada en otras escenas (si una
  solicitud lleva más de un día esperando, ya incumplió esa promesa). **Para decidir con el
  dueño de Capital Crédito**: cuántas horas son razonables antes del amarillo, y si "al día
  siguiente" (calendario) o "24 horas corridas" es el criterio correcto para el rojo.

### Revisión (RF08)

- **B** — Falta un bloque "Resumen para decidir" arriba de todo (monto, plazo, cuota, afectación,
  buró, los 8 requisitos, quién la envió y hace cuánto) con Aprobar/Rechazar visibles sin bajar
  la página.
- **B** — Aprobar/Rechazar deberían separarse visualmente de Cancelar solicitud (hoy los 3
  botones están juntos, y Cancelar es una acción distinta — corta el trámite, no decide sobre el
  crédito).
- **B** — "← Volver a la bandeja" debería ir arriba de la ficha, no solo abajo.
- **B** — **Criterio de nombres de botones, propuesto para todo el sistema** (no solo esta
  pantalla): el botón dice lo que hace y, si la acción es delicada, lleva una línea de ayuda
  debajo. Aplicado a esta ficha: **"Aprobar crédito"**, **"Rechazar crédito"**, **"Cerrar sin
  decidir"** (reemplaza "Cancelar solicitud" en esta pantalla; se descartaron "Retirar" —suena
  a retirar dinero— y "Suspender" —da la idea de que se puede reanudar, y esto es definitivo—),
  y **"← Volver a la bandeja"**. El diálogo de confirmación de Aprobar pasa de "¿Aprobar esta
  solicitud?" con "Sí, aprobar"/"Cancelar" a **"Sí, aprobar crédito"**/**"No, seguir
  revisando"** — hoy el "Cancelar" del diálogo se puede confundir con el botón "Cancelar
  solicitud" de la misma pantalla, que es una acción distinta. **Ojo antes de aplicar esto**:
  "cancelar" se usa en varios lugares del sistema que NO son esta pantalla (el borrador de la
  oficial, el código de auditoría `CANCELAR_SOLICITUD`, el módulo "Cancelación" de préstamos ya
  formalizados) — cualquier cambio de nombre tendría que ir a todos esos lugares a la vez
  (pantallas, mensajes, auditoría, manual de usuario, casos de `pruebas/humo_http.sh`), nunca
  solo acá, para no dejar el sistema con dos vocabularios distintos para la misma palabra.
- **B** — El expediente del cliente no se ve ahí mismo con un "Ver" — hay que salir a la ficha
  del cliente para repasarlo.
- **B** — El título de la sección de condiciones podría decir "Condiciones solicitadas" sin la
  palabra "simulación" (que ya quedó reservada para la pantalla del oficial, Escena 02).
- **B** — La zona muestra departamento y ciudad por separado cuando en los hechos uno implica al
  otro (Asunción es su propio departamento); evaluar no repetirlo.
- **B40** — El enlace a la ficha del cliente tiene nombres distintos según la pantalla: acá dice
  **"Ver ficha / expediente"**; en la ficha de la solicitud del oficial (ver
  `02_solicitud.md`) dice **"Ver ficha completa / documentos"**. Es el mismo destino (la ficha
  del cliente), con dos textos distintos — sugerir unificar uno de los dos en las dos pantallas.
- **B41** — "Aprobar" y "Rechazar" están juntos, en el mismo bloque (ver también el "B" de
  arriba sobre separarlos de "Cancelar solicitud"). **Verificado que ambos sí piden
  confirmación clara, aunque de forma distinta**: "Aprobar" confirma al instante
  ("¿Aprobar esta solicitud?"/"Sí, aprobar", o el texto reforzado si hay riesgo alto D/E);
  "Rechazar" primero abre un formulario (motivo obligatorio) y solo al tocar "Confirmar
  rechazo" pide "¿Rechazar esta solicitud?"/"Sí, rechazar" — ver el detalle completo en
  "Registro adicional" del Paso 4. No hay, hoy, ningún camino para aprobar o rechazar de un
  solo clic sin confirmar.
- **B31 (se repite)** — "Cancelar solicitud" (borde rojo) puede confundirse con "Rechazar"
  (relleno rojo) — misma familia que B3/B13 de confirmaciones y nombres de botones ya anotada
  en otras escenas. Mismo criterio que el "B" de arriba (Criterio de nombres de botones): citar
  la propuesta ya escrita acá mismo de renombrar "Cancelar solicitud" a **"Cerrar sin decidir"**
  en esta pantalla específicamente (con la misma advertencia de revisar todos los lugares que
  usan "cancelar" antes de aplicarlo).
- **B13 (se repite)** — El destino se muestra tal como el oficial lo escribió, sin ningún
  formato (`solicitudRevision.getDestinoCredito()` directo) — por ejemplo "personal" en
  minúscula, como en la Escena 02. Mismo hallazgo, confirmado también en esta pantalla.
- **C18 — Por diseño (RN-07) (cerrado 22/10/2026)** — El gerente no tiene, en esta pantalla,
  ninguna forma de aprobar por un monto o un plazo distinto al solicitado. **Verificado en el
  código: no existe ningún camino.** La tabla de condiciones es de solo lectura (sin ningún
  `<input>`), y `ServicioAprobacionCredito.aprobar(long idSolicitud, long idUsuario)` no recibe
  monto ni plazo como parámetro — el propio comentario del método lo dice explícito: "RN-07:
  nunca otro monto/plazo".
  **Cierre**: no es un hueco — es la regla de negocio tal como está pensada. El gerente aprueba
  o rechaza **exactamente** el monto y el plazo que se solicitaron; si quiere otras
  condiciones, la vía es **cancelar o rechazar esta solicitud y que el oficial cree una
  solicitud nueva** con el monto/plazo que corresponda. Esto deja trazabilidad real de qué se
  pidió y qué se decidió en cada caso (en vez de una misma solicitud mutando de condiciones a
  mitad de camino). **Cualquier cambio a esto sería una decisión nueva del dueño de Capital
  Crédito**, no una corrección de un error — no se decide acá ninguna clasificación A/B porque
  no hay nada que corregir.

### Documentos y evidencia del buró (verificación urgente del 22/10/2026)

- **B42** — El sistema no avisa cuando se carga un archivo **idéntico** (mismo hash SHA-256) a
  otro ya existente, aunque sea de otro tipo de documento o de otra persona. **Verificado en el
  código**: `archivos_binarios.hash_sha256` ya se calcula y se guarda para cada archivo subido,
  pero ningún servicio de carga (`ServicioDocumentos.cargarDocumento`,
  `ServicioConsultaCrediticia.cargarInformeBuro`/`cargarAutorizacionFirmada`) lo compara contra
  los hashes ya existentes antes de guardar — el dato está, simplemente no se usa para avisar
  nada. Es justamente lo que pasó con "ejemplo.pdf" (nota aclaratoria de arriba): nada en
  pantalla avisó que ese archivo ya existía en el sistema, con otro tipo y de otra persona.
  Sugerencia: un aviso **no bloqueante** al cargar un archivo con un hash ya presente en
  `archivos_binarios` — algo como **"Este archivo ya fue cargado como [tipo de documento] de
  [cliente]. ¿Desea continuar?"** — útil tanto para detectar un error de carga (archivo
  equivocado) como, en un caso real, para avisar de una posible reutilización indebida de un
  documento de otra persona.

  **RESUELTO el 01/11/2026 (decisión de Joel), con dos cambios sobre esta sugerencia.** (1) La
  búsqueda **no es global**: se limita a los documentos vigentes **de ese mismo expediente** (la
  persona, o el préstamo en el camino de Formalización). Nombrar el expediente de otro cliente en
  un aviso de pantalla filtraría datos de un tercero, y un hash igual en otra carpeta no es
  asunto de esta carga — así que lo de "reutilización indebida entre personas" **queda afuera a
  propósito**. (2) No todo es un aviso: **mismo tipo + mismo contenido se rechaza** (no se crea
  una versión 2 idéntica a la 1), y **otro tipo + mismo contenido se guarda y solo se avisa** en
  amarillo, porque una sola hoja escaneada puede servir para dos tipos. Dueño del detalle:
  `funcionamiento/RF03_expediente.md` §2.

**Lo que vi en pantalla el 01/11 y el 03/11/2026 (tanda 2, B42)**

- **01/11/2026 21:39-21:40 (expediente de Ana)** — *"Reemplazar"* el comprobante de domicilio con
  el mismo `ejemplo.pdf` **siguió creando versiones**: la 2 y la 3, las tres con el mismo hash.
  La primera versión del control **no bloqueaba nada**, porque comparaba contra un
  `getArchivoBinario()` que venía en **null** (el DAO del documento vigente no hace JOIN con
  `archivos_binarios`).
- **01/11/2026, reprueba** — el mismo reemplazo mostró el cartel **rojo** y **no** creó versión
  nueva; el caso de **otro tipo** con el mismo contenido mostró el **aviso amarillo** y sí
  guardó. **OK.** Quedó un detalle: el texto se veía **sin el punto final** — no faltaba en el
  código, lo saca el pintor de carteles (`pie.jsp`, `dividirEnTituloYDetalle`), que usa como
  título cualquier mensaje de una sola oración y a los títulos les quita el punto.
- **03/11/2026 06:33-06:38** — con el texto partido en dos oraciones: **"Ese archivo ya está
  cargado"** / **"Es idéntico al que figura como «Comprobante de domicilio»: no hace falta volver
  a subirlo."**, el punto a la vista y sin versión nueva (el documento sigue fechado 01/11/2026).
  **Texto aprobado por Joel.**
- **C19 (seguridad)** — `GET /documentos/{id}` sirve el archivo por id, sin ningún control
  visible de que el documento pertenezca al cliente/solicitud que la pantalla de origen estaba
  mostrando. **Verificado en el código y en la matriz de permisos**:
  - **Permiso de la ruta**: `FiltroAutorizacion` protege todo el prefijo `/documentos` (GET y
    POST, carga y visualización por igual) con `Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO`. Según
    `ServicioAutorizacion.construirMatriz` (mismo detalle que `RF38_perfiles.md`): **Gerente y
    Oficial = TOTAL**, **Cajero = NINGUNO**, **Administrador = TOTAL** (acceso total a todo,
    por diseño, D7). Para un GET alcanza con cualquier nivel distinto de `NINGUNO`
    (`FiltroAutorizacion.autorizaAcceso`), pero como este permiso no tiene ningún nivel
    `CONSULTA` intermedio, el resultado es el mismo: TOTAL o NINGUNO.
  - **¿Puede la cajera, o un rol sin permiso, abrir un documento cambiando el id en la URL?**
    **No** — la cajera (`NINGUNO` en este permiso) recibe 403 en `/documentos/{cualquier id}`,
    sin importar qué id ponga; no hace falta que el id sea válido ni que el documento exista.
  - **¿Y entre los roles que SÍ tienen acceso (gerente, oficial, administrador)?** Acá sí hay
    un hueco: `ControladorDocumentosServlet.descargar` solo valida que el `id_documento` exista
    (`servicioDocumentos.obtenerPorId`) — **no valida que ese documento pertenezca a un cliente
    o una solicitud que el usuario tenga motivo para estar viendo**. Cualquier oficial o
    gerente puede abrir el documento de **cualquier cliente** (cédula, comprobante de ingreso,
    domicilio, autorización, resultado del buró, contratos) solo conociendo o probando un id —
    y los id son `BIGINT GENERATED ALWAYS AS IDENTITY`, es decir, **secuenciales y fáciles de
    enumerar** (1, 2, 3...), confirmado con el `SELECT` de hoy.
  - **¿Se audita cada visualización?** **No.** `ServicioDocumentos.obtenerPorId`/
    `obtenerContenido` (los métodos que usa `descargar`) no llaman a `ServicioAuditoria` en
    ningún punto, y `ControladorDocumentosServlet` no importa `ServicioAuditoria` en absoluto.
    La auditoría de este servicio solo existe para la **carga** de un documento, nunca para su
    **visualización/descarga** — no queda ningún rastro de quién vio qué documento ni cuándo.
  - **Conclusión — riesgo PARCIAL** (sin clasificar A/B): el filtro por rol funciona bien y
    cubre el caso más evidente (la cajera no puede entrar, ningún perfil sin este permiso
    puede). El residual real es **dentro** de los roles con acceso (oficial, gerente,
    administrador): pueden ver documentos personales sensibles de cualquier cliente — no solo
    los que les corresponde atender — con ids fáciles de adivinar y **sin ningún registro de
    auditoría de la visualización**. En una empresa chica con pocos roles puede ser un riesgo
    menor en la práctica (todos los oficiales ya suelen necesitar ver cualquier cliente), pero
    no hay forma de probar ni de detectar, hoy, un acceso indebido si ocurriera.

### Después de aprobar (ejecutado hoy)

- **B (→ B104) — RESUELTO el 01/11/2026, probado en pantalla el 03/11/2026.** El aviso
  "Solicitud aprobada. Ahora se formaliza en Formalización." (texto ya implementado, ver
  `ControladorAprobacionesServlet`) podría traer un botón "Formalizar ahora" — corregido tras
  verificar la matriz de permisos: `GENERAR_DOCUMENTOS_FORMALIZACION` es TOTAL tanto para el
  gerente como para el oficial (solo el cajero no lo tiene), así que el gerente SÍ podría usar
  ese botón hoy mismo, sin pedido de permiso adicional; ver `03_que_pasa_por_detras.md` para la
  matriz completa. **Así quedó**: el botón no está en el cartel (que se cierra y desaparece)
  sino en la **franja verde de la ficha**, que sigue ahí cuando uno vuelve a entrar — *"Ya
  formalizada como CC-…"* / *"Ahora se formaliza: …"* con su botón. Dueño del detalle:
  `funcionamiento/RF09_aprobar.md` §5.

**Lo que vi en pantalla el 01/11 y el 03/11/2026 (tanda 2, B104)**

- **01/11/2026 21:39-21:51 (Ramón)** — `/aprobaciones/7`, con `SOL-2026-0007` ya formalizada: la
  franja verde decía **solo** *"Aprobada por Ramón Benítez el 01/11/2026 12:31. Monto aprobado:
  2.000.000 Gs., plazo: 12 meses."* — **sin** la línea *"Ya formalizada como CC-2026-00006."* ni
  el botón, ni con Ctrl+F5. **Causa**: el paso se había puesto solo en `solicitudes/ficha.jsp`
  (la ficha del oficial), y la pantalla donde el gerente aprueba —y donde aparece el cartel— es
  `/aprobaciones/{id}` → `aprobaciones/ficha.jsp` (el POST de *Aprobar* redirige ahí). No fue
  caché.
- **03/11/2026 06:33-06:38 (Ramón)** — el mismo camino (Aprobación → Aprobaciones → Historial →
  `SOL-2026-0007` → "Ver"): la franja mostró la línea **y** el botón **"Ver el préstamo
  CC-2026-00006 →"**, que llevó a `/formalizacion/6` con los 3 firmados y *"Documentación
  completa: listo para desembolso"*. **OK.**
- **Lo que NO se vio en pantalla, anotado como tal**: `/solicitudes/7` (la misma franja en la
  ficha del oficial, que usa el mismo fragmento compartido) y la rama *"Formalizar esta
  solicitud →"* (no quedaba ninguna solicitud aprobada sin formalizar) — esa rama queda cubierta
  por JUnit hasta la próxima.
- **B43 (familia B4)** — La confirmación **"¿Aprobar esta solicitud?"** no muestra qué se está
  aprobando (monto, plazo, cliente) — mismo tipo de hallazgo ya anotado para la apertura de
  caja (familia B4: confirmaciones genéricas que deberían mostrar los datos concretos de la
  operación). Sugerir, por ejemplo: **"¿Aprobar la solicitud de [cliente] por [monto] en [n]
  meses?"**
- **B44** — El mensaje de éxito ("Solicitud aprobada. Ahora se formaliza en Formalización.") no
  dice **quién** formaliza — un lector nuevo podría pensar que es el propio gerente. En la
  operación real formaliza la **oficial** (separación de funciones, ver "Forma de trabajo del
  negocio" más abajo); el gerente es un respaldo técnico del sistema (D-68: tiene el mismo
  permiso `GENERAR_DOCUMENTOS_FORMALIZACION`), no quien formaliza normalmente. Sugerir aclarar
  el mensaje, ej.: "...Ahora la oficial la formaliza en Formalización."
- **B45 (prioridad alta — principio de paneles, ver `00_LEEME.md` § Hallazgos generales) —
  Confirmado en pantalla (23/10/2026, 08:59)** — Al aprobarse la solicitud, el Panel del
  oficial **no muestra ningún aviso de trabajo pendiente en Formalización**: "Mis solicitudes
  en curso" deja de contarla (ver "Registro adicional: aprobar en detalle" arriba) y ahí
  termina — el oficial solo se entera si entra por su cuenta a la bandeja de Formalización.
  **Confirmado en pantalla**: con `SOL-2026-0002` ya `APROBADA` desde el 22/10 20:48, el Panel
  del oficial el 23/10 a las 08:59 mostró **Cobros de hoy (0)**, **Promesas de hoy (0)**,
  **Préstamos en mora (0)** y **"0 Mis solicitudes en curso"** — ninguna tarjeta ni ningún
  aviso de que esa misma solicitud sigue esperando que el oficial la formalice. **Verificado en
  el código**: en el Panel del oficial no existe
  ninguna tarjeta de Formalización — ni en `IndicadoresPanel` (sin ningún campo del tipo
  "aprobadosParaFormalizar") ni en `ServicioPanel.calcularIndicadores` (la rama
  `OFICIAL_CREDITO` solo calcula `misSolicitudesEnCurso`, que ya excluye `APROBADA`) ni en
  `panel.jsp` (los 7 enlaces condicionales del panel —`verEnlaceCobranza/Mora/Desembolso/Caja/
  Aprobaciones/Reversiones/Solicitudes`— no incluyen ninguno hacia Formalización). No faltó un
  dato: **nunca se calculó ni se mostró**, porque hoy nada en `ServicioPanel` cuenta
  `solicitudes_credito` con `estado_solicitud = 'APROBADA' AND NOT formalizada` — el mismo
  `SolicitudDAO.listarAprobadasSinFormalizar` que ya alimenta la bandeja de Formalización
  (`04_formalizacion.md`) simplemente no se reutiliza acá. Sugerir una tarjeta **"Préstamos
  aprobados para formalizar (N)"**, con enlace a Formalización, reusando ese mismo método —
  mismo criterio que ya proponía el principio de paneles en `00_LEEME.md`, ahora con el método
  exacto a reutilizar.
- **B3 (se repite)** — La confirmación de "Aprobar" vuelve a usar **"Cancelar"** para "no
  aprobar" — mismo hallazgo ya registrado varias veces en esta escena y en otras (04_formalización,
  02_solicitud); sugerir **"No, seguir revisando"** (ya propuesto en el criterio de nombres de
  botones de "Revisión (RF08)", arriba).
- **C20** — **(29/10/2026: resuelto y probado en pantalla; el estado del ítem vive en
  [FASE2_PLAN.md](FASE2_PLAN.md).)** Lo que vi ese día: con `SOL-2026-0006` ya formalizada
  (`CC-2026-00005`), `/aprobaciones/6` **todavía mostraba "Cancelar solicitud"** — no lo apreté,
  para no dejar la solicitud cancelada con el préstamo vivo. Después del arreglo, esa misma
  pantalla ya no muestra el botón (solo "Volver a la bandeja"), y `SOL-2026-0005`, que sigue
  `PENDIENTE_APROBACION`, **sí** se canceló normalmente ("Cancelada por Ramón Benítez el
  29/10/2026 13:48. Motivo: Otro - prueba C20"). Hallazgo original — **"Cancelar solicitud" sigue
  visible con la solicitud ya `APROBADA`.** Verificado
  en el código y con `SELECT`:
  - **Estado resultante**: igual que desde `PENDIENTE_APROBACION` — pasa a `CANCELADA`
    (`SolicitudDAO.marcarCancelada`), irreversible.
  - **¿Es posible hasta formalizar?** Sí, mientras `formalizada` sigue en `false` — es
    exactamente el caso previsto y ya documentado ("Cancelar, rechazar y anular no son lo
    mismo", más abajo): cancelar una `APROBADA` sin formalizar.
  - **¿Y DESPUÉS de formalizar (`formalizada = true`)?** Acá hay un hueco real, verificado con
    `SELECT`: `SOL-2026-0001` (ya formalizada, con el préstamo `CC-2026-00001`, estado `ACTIVO`,
    ya desembolsado)
    sigue con `estado_solicitud = 'APROBADA'` en la base — **la tabla `solicitudes_credito`
    nunca cambia de estado al formalizar**, solo se marca `formalizada = true` en una columna
    aparte. `ServicioAprobacionCredito.cancelar` valida únicamente
    `estado_solicitud == PENDIENTE_APROBACION || estado_solicitud == APROBADA` — **nunca mira
    `formalizada`** — y `SolicitudDAO.marcarCancelada` tampoco lleva ningún `WHERE` que lo
    impida (a diferencia de `marcarDecidida`, que sí exige `estado_solicitud =
    'PENDIENTE_APROBACION'` en el propio `UPDATE`). En la pantalla, el botón "Cancelar
    solicitud" tampoco mira `formalizada` (`aprobaciones/ficha.jsp`:
    `cancelable = pendiente || estadoSolicitud == APROBADA`). **No se probó en pantalla** (no
    se tocó "Cancelar solicitud" sobre `SOL-2026-0001` para no alterar datos ya usados en otras
    escenas), pero la lectura del código y el estado real de la base indican que el camino
    existe tal cual.
  - **¿La vía real para un préstamo ya formalizado es "Anular préstamo"?** Sí — es el
    mecanismo pensado para eso ("Reglas de decisión del crédito" más abajo: "Anular préstamo...
    préstamo ya formalizado que no se desembolsa. Solo el gerente, solo antes del desembolso"),
    con su propio código de auditoría. Pero nada en el código **impide** usar "Cancelar
    solicitud" en su lugar sobre una solicitud ya formalizada.
  - **Auditoría**: mismo código `CANCELAR_SOLICITUD` (SOLICITUD_CREDITO) que el resto de los
    caminos de cancelación, sin distinguir si la solicitud detrás ya tiene un préstamo.
  - **Conclusión — riesgo PARCIAL** (sin clasificar A/B): no es un hueco de permisos (solo el
    gerente puede, como corresponde) ni de auditoría (queda registrado) — es que la validación
    de estado no contempla `formalizada`. Si alguien cancelara una solicitud ya formalizada
    (por error, confundiéndola con "Anular préstamo"), la solicitud quedaría `CANCELADA`
    mientras el préstamo sigue existiendo y operando con total normalidad (sin ningún vínculo
    roto a nivel de base, pero con dos registros contándose historias distintas) — un
    préstamo activo cuya solicitud de origen dice "cancelada".
  - **Por qué no se probó en pantalla, y qué falta para cerrarlo**: la prueba real (tocar
    "Cancelar solicitud" sobre `SOL-2026-0001`, ya `APROBADA`/formalizada) **no se hace sobre
    la base actual** — rompería el préstamo `CC-2026-00001`, ya desembolsado y en uso en otras
    escenas de este recorrido (Formalización, Desembolso, Caja). Queda pendiente para una base
    limpia (`recrear_base.sh`), con una solicitud formalizada descartable, antes de poder
    marcar este hallazgo como confirmado en pantalla.
  - **Si se confirma en esa base limpia**: deja de ser solo una lectura de código — pasa a ser
    **candidato a hallazgo A**, no B: una solicitud `CANCELADA` con un préstamo `ACTIVO`
    (o cualquier otro estado post-formalización) apuntando a ella es un **estado contradictorio
    de los datos** (dos registros del mismo trámite dando información incompatible), no solo
    una mejora de uso — a reconfirmar y clasificar recién con esa prueba, no antes.

### Comprobante de domicilio

- Ya resuelto con la decisión del 21/10/2026 (ver Escena 01) — no aparece en la revisión del
  gerente porque no forma parte del checklist de 8 requisitos; cuando se implemente esa
  decisión, correspondería agregarlo también acá.

### Forma de trabajo del negocio (no cambia el sistema — a confirmar con el dueño de Capital Crédito)

Según lo que describe Joel: en la operación real, **formaliza la oficial**, por separación de
funciones dentro del circuito completo — prepara la oficial (alta, expediente, solicitud),
decide el gerente (aprobar/rechazar), ejecuta la formalización la oficial, y paga la cajera
(desembolso). El gerente formaliza solo como respaldo (por ejemplo, si la oficial no está). El
sistema ya queda igual frente a esto: los dos perfiles tienen `GENERAR_DOCUMENTOS_FORMALIZACION`
en `TOTAL` por diseño (D-68, ver `03_que_pasa_por_detras.md`) — esto no es un hallazgo de
software, es una convención de uso del negocio que no necesita ningún cambio de permisos para
funcionar. **Pendiente**: confirmar esta descripción del circuito con el dueño de Capital
Crédito antes de asumirla como la forma de trabajo real.

## Recorrido completo del gerente (22/10/2026)

Resumen en orden real de todo el circuito del gerente con el **segundo cliente de práctica**,
de punta a punta — cada paso enlaza al detalle ya documentado, con sus hallazgos, sin repetir
el contenido completo acá.

1. **Panel** (`/panel`, en una ventana de incógnito, con el oficial todavía conectado en la
   suya) — saludo según la hora, 10 indicadores en 3 bloques. "Registro adicional: panel del
   gerente" (Paso 2, arriba). Hallazgos: B36 (concordancia singular/plural, varias tarjetas),
   B37 (para el dueño: sin aviso de cuánto efectivo necesitaría la caja si se aprueba).
2. **Bandeja de aprobación** (`/aprobaciones`) — pestañas Pendientes/Historial, buscador,
   "Esperando" sin color ni alerta. "Registro adicional: la bandeja en detalle" (Paso 3).
   Hallazgos: B38 (sin columna de buró en la tabla), B39 (tiempo "Esperando" nunca cambia de
   color, para decidir con el dueño).
3. **Revisar** (`/aprobaciones/{id}`, botón "Revisar") — tarjetas Cliente/Condiciones/Buró,
   afectación en verde o amarillo (nunca rojo), monto/plazo de solo lectura. "Registro
   adicional: la ficha de revisión en detalle" (Paso 4). Hallazgos: B40 (enlace "Ver ficha /
   expediente" con nombre distinto al del oficial), B41 (confirmación de Aprobar/Rechazar
   verificada), B31 (se repite: Cancelar solicitud vs. Rechazar), B13 (se repite: destino en
   minúscula), C18 (Por diseño, RN-07: no se puede aprobar otro monto/plazo — cerrado).
4. **Ver la evidencia del buró** ("Ver" en la tabla de Informconf) — verificación urgente del
   22/10/2026: el PDF que pareció ser de otro cliente resultó ser un archivo de prueba
   reutilizado, sin error de código (nota aclaratoria, Paso 4). Hallazgos: B42 (sin aviso de
   archivo duplicado por hash), C19 (seguridad: sin control de pertenencia ni auditoría de
   visualización entre oficial/gerente/administrador — riesgo parcial).
5. **Aprobar** (botón "Aprobar", confirmación, cuadro de éxito) — estado "Aprobada", snapshot
   de condiciones aprobadas, auditoría `APROBAR_SOLICITUD`. "Registro adicional: aprobar en
   detalle" (Paso 5). Hallazgos: B43 (confirmación sin monto/plazo/cliente), B44 (mensaje de
   éxito sin decir quién formaliza), **B45 (prioridad alta, confirmado en pantalla el
   23/10/2026: el Panel del oficial no avisa nada)**, B3 (se repite: "Cancelar" del diálogo),
   C20 (Cancelar solicitud sigue visible ya Aprobada — riesgo parcial, ampliado: candidato a A
   si se confirma en una base limpia).

**Qué sigue**: la solicitud queda lista para formalizar — ver
[04_formalizacion.md](04_formalizacion.md). **Dato para la escena siguiente**: la aprobación de
`SOL-2026-0002` fue el 22/10/2026 a las 20:48; la formalización, en cambio, se hizo recién el
23/10/2026 — un día después. La sesión de caja de la cajera abierta el 22/10 sigue **abierta**
a esta fecha: antes de poder desembolsar este préstamo una vez formalizado, habrá que cerrarla y
abrir la sesión del día — se recorrerá como parte del tramo 6 (cierre del día), no de este
tramo ni del de Formalización/Desembolso.

## Completitud del tramo 2

Control de completitud contra el código (solo lectura, verificado hoy): todos los caminos que
existen en Aprobación, marcando cuáles se recorrieron en pantalla y cuáles quedan pendientes.
Detalle de cada camino ya recorrido en los Pasos 1-5 de arriba y en
[03_que_pasa_por_detras.md](03_que_pasa_por_detras.md).

| Camino | Existe en el código | ¿Se recorrió en pantalla? |
|---|---|---|
| Aprobar | `ServicioAprobacionCredito.aprobar` | **Sí** — 21/10/2026 (`SOL-2026-0001`) y 22/10/2026 (`SOL-2026-0002`) |
| Rechazar (con motivo de una lista) | `ServicioAprobacionCredito.rechazar` | **No recorrido** — las dos solicitudes de práctica terminaron aprobadas, ninguna se rechazó |
| Cancelar una solicitud `PENDIENTE_APROBACION` | `ServicioAprobacionCredito.cancelar` | **No recorrido en pantalla** — verificado solo en el código y en `aprobaciones/ficha.jsp` |
| Cancelar una solicitud `APROBADA` sin formalizar | Mismo método, mismo botón, distinto estado de entrada | **No recorrido en pantalla** — ninguna de las dos solicitudes de práctica se canceló en este estado |
| Cancelar una solicitud ya formalizada (`formalizada = true`) | Mismo método — **no valida `formalizada`** (ver C20) | **No recorrido** — no se prueba sobre la base actual (rompería `CC-2026-00001`, ya desembolsado); pendiente para una base limpia |
| Aprobar con algún requisito que cambió después del envío (ej. una consulta al buró nueva, con mora) | `revincularConsultasMasRecientes` + `tieneMoraVinculada`, dentro de `aprobar` | **No recorrido** — en los dos casos de práctica nada cambió entre el envío y la aprobación |
| Solicitud con mora vinculada (RN-18, bloqueo real al aprobar, botón deshabilitado) | `aprobaciones/ficha.jsp` (`bloqueadoPorMora`) + `aprobar` (`tieneMoraVinculada`) | **No recorrido** — ninguno de los dos clientes de práctica tenía mora (confirmado con `SELECT`: los dos, clasificación A, sin mora) |
| Clasificación de riesgo alto D/E (confirmación reforzada, sin bloquear) | `avisoBuroRevision.isHayRiesgoAlto()` → texto reforzado en el `data-confirmar` de "Aprobar" | **No recorrido** — confirmado con `SELECT`: las dos consultas de práctica son clasificación A |
| Doble aprobación de la misma solicitud desde dos pestañas (carrera) | `marcarDecidida` exige `estado_solicitud = 'PENDIENTE_APROBACION'` en el propio `UPDATE`; si pierde la carrera, `construirErrorYaDecidida` | **No recorrido** — verificado solo en el código, difícil de reproducir manualmente sin dos sesiones simultáneas a propósito |
| Pestaña "Historial", con sus filtros (Estado, desde/hasta) | `SolicitudDAO.listarDecididas`/`listarDecididasConFiltro` | **No recorrido en pantalla** — descrito desde el código y el JSP (Paso 3), nunca se entró a la pestaña ni se usaron los filtros |
| Acceso por URL directa de un rol sin permiso (ej. cajera a `/aprobaciones`) | `FiltroAutorizacion` exige `APROBAR_RECHAZAR_SOLICITUD`, `NINGUNO` para cajero y oficial → 403 | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Aprobación) |
