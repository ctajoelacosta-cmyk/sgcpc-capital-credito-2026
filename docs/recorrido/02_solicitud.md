# Escena 02 — Solicitud de crédito

**Recorrido probado en pantalla por Joel el 21/10/2026, rol oficial de crédito, base limpia
(`sgcpc_db_pruebas` recién recreada, sin humo).** Reemplaza el contenido migrado de
`docs/funcionamiento/FLUJO_OPERATIVO.md` (recorrido del 17/10/2026) por lo visto hoy. Cliente
de ejemplo ficticio: **Marta Ovelar (C.I. 1.000.001)** (ver
[01_llega_el_cliente.md](01_llega_el_cliente.md)).

**Qué pasa por detrás** (tablas, parámetros, auditoría, archivos y numeraciones de cada paso,
verificado contra el código): [01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md).

## Paso 1 — Nueva solicitud y simulación (RF05)

**Registro de lo observado en uso real (22/10/2026, segundo cliente de práctica), llegando
desde ③ Consulta al buró de la ficha del cliente.**

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: desde la ficha del cliente, **"④ Solicitud"** (nav-pill) o **"Siguiente:
  ④ Solicitud →"** (mismo elemento, solo cambia el texto/color cuando el Expediente y el Buró
  están completos) llevan a `GET /solicitudes/nuevo?cedulaCliente=[cédula]` — **fuera de la
  ficha del cliente, a otro módulo** (el menú lateral marca "Solicitudes"); `POST
  /solicitudes/nuevo`; ficha en `/solicitudes/{id}`; `POST /solicitudes/{id}/simular`.
- **Qué responde el sistema (pantalla "Nueva solicitud")**: aviso **"Los campos marcados con \*
  son obligatorios."**; **Cédula del cliente**, precargada, con la ayuda **"El cliente debe
  estar registrado en 'Clientes' antes de crear la solicitud."**; **Monto (Gs.)**; **Plazo
  (meses)**; **Destino del crédito** (texto libre); **Fecha estimada del primer vencimiento**,
  propuesta a un mes desde hoy; botones **"Crear solicitud"** y **"Cancelar"**. Al completar
  monto y plazo, aparece en vivo (sin recargar) **"Cuota estimada: [monto] Gs. · Afectación:
  [x,x] % (límite: 40 %)."** (en rojo y con el texto ". Baje el monto o alargue el plazo." si lo
  supera). **No hay ningún aviso de que el Expediente está incompleto** (hoy, sin cédula ni
  comprobante de ingreso cargados) — ver B28 (confirmado con este ejemplo) en Hallazgos.
- **Qué hace el usuario**: la cédula ya viene precargada (cliente registrado en la Escena 01),
  pero monto, plazo y destino se escriben de nuevo (no vienen del cotizador de la Escena 01);
  fecha propuesta del primer vencimiento. "Cancelar" vuelve a la lista de solicitudes. "Crear
  solicitud" crea `SOL-2026-0001` directo en Borrador — **sin ningún cuadro de éxito** (en esta
  prueba no apareció ninguno; el sistema redirige directo a `/solicitudes/{id}`) — ver el
  hallazgo ya anotado más abajo ("algún aviso al crear la solicitud, hoy no hay ninguno
  intermedio"), sin duplicarlo. En la ficha, "Simular" recalcula y guarda las condiciones. Se
  probó cambiar el monto a 4.000.000 (quedó guardado en 4.000.000) y volver a simular con
  3.000.000.
- **Datos que pide** (obligatorios y validaciones): cédula del cliente (ya registrado), monto,
  plazo, destino, fecha estimada del primer vencimiento.
- **La pantalla de la solicitud** (`/solicitudes/{id}`, estado Borrador): título **"Solicitud
  SOL-[año]-[número]"** con la etiqueta **"Borrador"**; tarjeta **"Cliente"** — nombre y C.I.,
  ingreso neto comprobado (o "declarado, sin comprobante"), "Presenta comprobante de ingresos:
  Sí/No", enlace **"Ver ficha completa / documentos"**; tarjeta **"Condiciones y simulación"**
  — monto, plazo, destino, primer vencimiento estimado (todos editables mientras es Borrador),
  el indicador en vivo de cuota/afectación, botón **"Simular"** con la ayuda **"Guarda las
  condiciones y recalcula la cuota y la capacidad de pago."**; debajo, los resultados: **cuota
  más alta (con IVA)**, **TEA / TNA**, **CFT simulado**, **total a pagar (con IVA)**,
  **afectación al ingreso** (con el límite al lado); la **lista de 8 requisitos**, cada uno con
  ✓ verde o ✗ rojo (edad, zona, situación laboral, cédula de identidad vigente cargada en el
  expediente, comprobante de ingresos cargado, autorización de consulta al buró vigente,
  consulta al buró con evidencia, sin deudas en Informconf) — en el ejemplo de hoy, ✗ en cédula
  y en comprobante de ingresos; aviso **"No cumple validaciones preliminares"** (amarillo).
  Botones: **"Enviar a aprobación"** (verde, `btn-success`), **"Cancelar solicitud"** (rojo,
  `btn-danger`) y, aparte, **"Volver a la lista"**.
- **Regla de negocio**: ver [RF05_solicitud.md](../funcionamiento/RF05_solicitud.md) §1-§2; la
  fecha del primer vencimiento exige el rango de 20 a 45 días desde hoy (§6, decisión del
  10/10/2026) — ver el hallazgo C de la fecha, más abajo.
- **Qué cambia en la base**: `INSERT` en `solicitudes_credito` (estado `BORRADOR`, código
  `SOL-aaaa-NNNN`); cada "Simular" recalcula y **vuelve a guardar** sobre la misma fila (no crea
  una nueva).
- **Qué registra la auditoría**: `CREAR_SOLICITUD` (SOLICITUD_CREDITO), al crear. "Simular" **no
  audita** — es un recálculo, mismo criterio que el cotizador de RF01 ("no guarda registros" en
  el sentido de eventos, aunque sí actualiza la fila de la solicitud) — mismo hallazgo ya
  anotado sobre el nombre del botón "Simular" y su falta de auditoría, ver Hallazgos, sin
  duplicarlo.
- **Qué sigue**: "Enviar a aprobación" o "Cancelar solicitud" (Paso 2).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Verificado en el código (22/10/2026)

- **¿La pestaña ④ y el botón "Siguiente: ④ Solicitud →" llevan al mismo lugar?** Sí — son
  literalmente **el mismo elemento** en `clientes/ficha.jsp` (el mismo `<a>` del nav-pill ④):
  cuando el Expediente y el Buró están completos, ese mismo enlace cambia de texto ("④
  Solicitud" → "④ Solicitud →") y de estilo (gris → azul y negrita), pero el `href` es
  siempre `/solicitudes/nuevo?cedulaCliente=[cédula]`. No son dos botones distintos que puedan
  desalinearse entre sí.
- **Qué valida "Crear solicitud"** (`ServicioSolicitudes.crear`/`aplicarSimulacion`): que el
  cliente exista y esté `ACTIVA` (no dado de baja); que no tenga ya una solicitud u otro
  compromiso abierto (`buscarCompromisoAbierto`); la política de crédito (edad/zona/situación
  laboral/comprobante — las mismas 4 de Precalificación); destino no vacío; monto y plazo
  dentro de los límites; fecha del primer vencimiento dentro de 20 a 45 días desde hoy; tope
  legal de tasa vigente; y afectación al ingreso ≤ 40 % (`credito.limite_afectacion`).
  **No exige expediente completo, autorización de buró vigente ni consulta registrada** — nada
  de eso aparece en `crear`/`aplicarSimulacion`.
- **En qué momento se exigen expediente y buró**: recién al **"Enviar a aprobación"**
  (`ServicioSolicitudes.enviarAAprobacion`, Paso 2 de esta escena) —
  `agregarFaltantesExpediente` (cédula + comprobante de ingreso **+ comprobante de domicilio
  desde el 28/10/2026**) y `agregarFaltantesBuro` (autorización vigente + consulta con
  evidencia), ya documentados en [01_llega_el_cliente.md](01_llega_el_cliente.md) (C13/C14).
  Crear la solicitud **nunca** los chequea — por eso la pantalla de hoy no avisó nada, aunque el expediente siguiera incompleto.
- **De dónde sale la fecha propuesta**: `solicitudes/nueva.jsp` pone directamente
  `LocalDate.now().plusMonths(1)` (hoy + 1 mes) como valor por defecto del campo, con
  `min="[hoy]"` en el `<input type="date">` — **no es el mismo criterio que el cotizador**
  (`ControladorCotizadorServlet`, que sugiere el **mínimo** del rango real de 20-45 días,
  calculado con `ServicioFormalizacion.calcularRangoPrimerVencimiento`). Son dos valores
  sugeridos, calculados de dos formas distintas, en dos pantallas distintas — "hoy + 1 mes" cae
  casi siempre dentro del rango válido (coincide con el criterio de mes fijo de D-156), pero el
  campo de Nueva Solicitud no muestra el rango real (20-45 días) como sí hace el cotizador; el
  servidor sigue validando el rango real igual (`aplicarSimulacion` →
  `validarRangoFechaPrimerVencimiento`), rechazaría una fecha fuera de rango aunque el `min` del
  campo la deje escribir.
- **¿El destino es texto libre o una lista?** Texto libre — `<input type="text"
  name="destinoCredito">`, validado solo contra vacío
  (`NormalizadorTexto.normalizarTextoLibre` + no vacío), sin ningún catálogo ni `<select>`
  detrás.
- **Numerador**: `SOL-%d-%04d` (4 dígitos) — `ServicioSolicitudes.generarCodigo`, clave
  `SOLICITUD` en `numeradores`, incrementado con `FOR UPDATE`.
- **De dónde salen los 8 requisitos, y cuáles bloquean al enviar**: los 8 vienen de
  `ServicioSolicitudes.construirChecklistPrevioEnvio` — pero **los 8, sin excepción, son
  controles reales** dentro de `enviarAAprobacion`, no solo decorativos: edad/zona/situación
  laboral los revalida `validarPoliticaCredito`; cédula vigente cargada y comprobante de
  ingresos cargado los revalida `agregarFaltantesExpediente`; autorización vigente y consulta
  con evidencia los revalida `agregarFaltantesBuro`; "sin deudas en Informconf" se revalida
  aparte (RN-18) con un rechazo directo e inmediato — **"El cliente figura con deudas en
  Informconf. No se puede otorgar el crédito."** — en vez de sumarse a la lista de faltantes.
  Si falta cualquiera de los otros 7, el mensaje junta TODOS en una sola lista: **"Faltan
  completar estos puntos antes de enviar a aprobación:"** + una viñeta por cada uno
  (`enviarAAprobacion`, confirmado también en `01_llega_el_cliente.md`, C13).
- **Por qué el comprobante de domicilio no está entre los 8**: `agregarFaltantesExpediente`
  solo mira `CODIGO_CEDULA_IDENTIDAD` y `CODIGO_COMPROBANTE_INGRESO` (los mismos 2 de
  `CODIGOS_EXPEDIENTE_OBLIGATORIOS` que no son la autorización, que se revisa aparte) — el
  domicilio queda fuera por diseño, igual que en el contador "N de 3" del Expediente. **Relación
  con la decisión del 21/10/2026 (domicilio obligatorio, fase 2, sin implementar)**: si se
  implementa, este checklist pasaría a tener un 9.º ítem ("Comprobante de domicilio cargado"),
  y `agregarFaltantesExpediente` tendría que agregarlo a sus chequeos reales — hoy ninguno de
  los dos lo hace.
- **"Cancelar solicitud"**: botón rojo que primero despliega un formulario (no envía de un
  clic) — pide **Motivo de la cancelación** (lista fija, `<select>` obligatorio) y un detalle
  (obligatorio solo si el motivo es "Otro"); al confirmar, la pregunta es **"¿Cancelar esta
  solicitud? No se puede deshacer."** `ServicioSolicitudes.cancelar` solo funciona mientras la
  solicitud sigue en `BORRADOR` (`validarEstadoBorrador`); pasa el estado a `CANCELADA`
  (`solicitudDAO.marcarCancelada`) y audita `CANCELAR_SOLICITUD` (SOLICITUD_CREDITO). **No es
  reversible** — no existe ningún método que vuelva una solicitud `CANCELADA` a `BORRADOR` ni a
  ningún otro estado; queda como historial permanente (visible en "Solicitudes y préstamos" de
  la ficha del cliente).
- **Matiz sobre "Enviar a aprobación"**: el botón en sí (`btn-success`) **siempre está
  habilitado y siempre envía el formulario** — pero el cuadro de confirmación de SweetAlert2
  (`data-confirmar="¿Enviar esta solicitud a aprobación?"`) **solo se agrega al formulario
  cuando `cumpleValidaciones` es verdadero** (`solicitudes/ficha.jsp`). Con algún ✗ en el
  checklist, como en el ejemplo de hoy, el formulario no tiene ningún `data-confirmar` — el
  clic manda el `POST` directo, sin ningún paso intermedio, y el servidor es quien rechaza con
  el mensaje de "Faltan completar...". Ver B30.

### Registro adicional: expediente y buró completos, checklist en ✓ (22/10/2026)

Más tarde, con el Expediente completo (cédula, comprobante de ingreso, autorización y
comprobante de domicilio) y el Buró ya registrado, el oficial volvió a esta misma solicitud
desde la pestaña **"④ SOL-[número] (Borrador) →"** de la ficha del cliente — **1 clic**,
directo a `/solicitudes/{id}`.

En la ficha de la solicitud: la **lista de 8 requisitos** ahora aparece completa, los 8 en ✓;
el aviso cambia de amarillo a **verde: "Cumple validaciones preliminares"**. El estado sigue
siendo **"Borrador"** (entrar a verla no lo cambia). Los mismos 3 botones de siempre: "Enviar a
aprobación", "Cancelar solicitud", "Volver a la lista".

**Verificado en el código**: con el checklist completo, `solicitudes/ficha.jsp` SÍ agrega el
`data-confirmar` al formulario de "Enviar a aprobación" — confirmado el texto exacto:
**`data-confirmar="¿Enviar esta solicitud a aprobación?"` / `data-confirmar-boton="Sí,
enviar"`**. Es el comportamiento inverso, y complementario, al ya verificado para B30 (sin
`data-confirmar` cuando el checklist tiene algún ✗): el mismo formulario cambia de
comportamiento según el mismo booleano `cumpleValidaciones`.

**Dato de uso, para unificar en la fase 2**: el camino de vuelta **ficha del cliente →
solicitud** es de **1 clic** (la pestaña ④ ya lo resuelve del todo). El camino inverso,
**solicitud → expediente**, le tomó al oficial **5 pasos** reales (ver B32 y B33, en
`01_llega_el_cliente.md`) — una asimetría notoria entre ir y volver por el mismo circuito, que
convendría emparejar cuando se revisen esos hallazgos.

## Paso 2 — Enviar a aprobación (RF06)

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: `/solicitudes/{id}`; `POST /solicitudes/{id}/enviar`.
- **Qué hace el usuario**: con la solicitud en Borrador y el checklist preliminar en ✓, toca
  "Enviar a aprobación" y confirma "Sí, enviar". Se envió sin comprobante de domicilio cargado
  (hoy complementario, no bloquea — **decisión tomada el 21/10/2026: pasa a ser obligatorio,
  implementación pendiente**, ver Escena 01 y `AVANCE.md`).
- **Datos que pide** (obligatorios y validaciones): ninguno nuevo — revalida en el servidor
  todo lo ya cargado (cliente activo, política de crédito, cédula vigente, comprobante de
  ingreso, autorización de buró vigente, consulta al buró registrada, afectación ≤ 40 %, sin
  mora en Informconf).
- **Qué responde el sistema**: confirmación "Sí, enviar / Cancelar" → "Solicitud enviada a
  aprobación. Ahora la revisa el gerente en Aprobación." Pasa a **Pendiente de aprobación**: el
  ingreso evaluado queda congelado, las condiciones quedan de solo lectura, desaparecen
  "Simular", "Enviar" y "Cancelar" (ese botón de cancelar; el gerente sigue pudiendo cancelarla
  desde Aprobación — ver [03_aprobacion.md](03_aprobacion.md)).
- **Regla de negocio**: ver
  [RF06_envio_aprobacion.md](../funcionamiento/RF06_envio_aprobacion.md) §1-§2; las reglas
  completas de qué bloquea el envío y qué decide después el gerente están en
  [03_aprobacion.md](03_aprobacion.md) § Reglas de decisión del crédito.
- **Qué cambia en la base**: `solicitudes_credito.estado_solicitud` → `PENDIENTE_APROBACION`;
  se congela el ingreso evaluado (`ingreso_cliente_evaluado`) y la afectación; se vincula la
  consulta al buró del cliente a esta solicitud.
- **Qué registra la auditoría**: `ENVIAR_SOLICITUD_APROBACION` (SOLICITUD_CREDITO).
- **Qué sigue**: la revisa el gerente en Aprobación — ver [03_aprobacion.md](03_aprobacion.md).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: intento rechazado (22/10/2026, segundo cliente de práctica)

Con la solicitud en Borrador y la cédula y el comprobante de ingreso todavía sin cargar en el
Expediente, el oficial tocó **"Enviar a aprobación"**. **No pidió confirmación** (coherente con
lo ya verificado: sin el checklist completo, el formulario no lleva `data-confirmar`, ver el
Paso 1) y mostró directo el cuadro de error (cruz roja): **"Faltan completar estos puntos antes
de enviar a aprobación:"**, con la lista:

- "Falta cargar la cédula de identidad vigente del cliente en su expediente."
- "El cliente indica que presenta comprobante de ingresos, pero no hay ninguno cargado en su
  expediente."

Botón **"Entendido"**. La solicitud **sigue en Borrador**; no se envió nada al gerente.

**Verificado en el código**:

- **Método y orden de la lista**: `ServicioSolicitudes.enviarAAprobacion` arma `faltantes` en
  un orden fijo — primero `agregarFaltantesExpediente` (cédula, después comprobante de
  ingreso, en ese orden dentro del mismo método), después la política de crédito (no aplicó
  hoy), después la afectación (no aplicó), después `agregarFaltantesBuro` (autorización y
  consulta, no aplicaron — el segundo cliente de práctica ya los tenía en orden). Con solo el
  expediente incompleto, la lista queda exactamente con esos 2 textos, en ese orden — coincide
  con lo observado en pantalla.
- **¿El intento rechazado deja auditoría?** No — `enviarAAprobacion` lanza la
  `NegocioException` **antes** de llegar a `solicitudDAO.marcarEnviada` o a
  `servicioAuditoria.registrar`; el `catch` de la transacción hace `rollback()`. No queda
  ninguna fila nueva en `auditoria_operaciones` por un intento rechazado.
- **¿Cambia el estado?** No — la excepción se lanza antes de cualquier `UPDATE`; la solicitud
  queda exactamente como estaba (`BORRADOR`, misma simulación, mismos datos).

**Esto confirma en pantalla lo que ya se había verificado en el código para B30** (ver el Paso
1 y `02_solicitud.md` § Hallazgos): el servidor rechaza correctamente un envío incompleto —
el problema real es solo de uso (el botón no avisa de antemano, no un hueco de seguridad). Ver
también B32.

### Camino real observado: llegar al Expediente desde el cuadro de faltantes

**Registra el camino que de verdad siguió el oficial (22/10/2026), no el más corto que existe
— para documentar cómo se usa hoy el sistema, no cómo debería usarse.**

Con el cuadro "Faltan completar estos puntos..." todavía en pantalla, el oficial:

1. Tocó **"Entendido"** → el sistema se queda en la misma solicitud (`/solicitudes/{id}`), sin
   redirigir a ningún lado ni ofrecer un acceso al Expediente.
2. Fue al menú **"Clientes y solicitudes" → "Clientes"**.
3. Buscó al cliente en la lista.
4. Tocó **"Ver / editar"** → abre la ficha del cliente, en la pestaña **"① Datos"**.
5. Hizo clic en la pestaña **"② Expediente"**.

**Total: 5 pasos** para llegar a donde en realidad se resuelve el problema — aunque la propia
tarjeta "Cliente" de esa misma ficha de solicitud ya tiene el enlace **"Ver ficha completa /
documentos"** (ver Paso 1): el oficial no lo vio y tomó el camino largo.

**Verificado en el código, adónde lleva ese enlace**: `solicitudes/ficha.jsp` —
`<a href="…/clientes/{idPersona}">Ver ficha completa / documentos</a>`, **sin ningún ancla**
(no lleva a `#tabDocumentos`) y **sin `target`** (abre en la misma pestaña del navegador). Si
el oficial lo hubiera visto y usado, le habría ahorrado los pasos 2-4 del camino real (no hace
falta ir al menú, buscar al cliente ni tocar "Ver / editar" — el enlace ya conoce el ID del
cliente) y lo habría llevado directo a **"① Datos"** de su ficha — pero **no directo a "②
Expediente"**: igual habría tenido que dar el paso 5 (un clic más, en la pestaña). Ver B33.

### Registro adicional: envío exitoso (22/10/2026, segundo cliente de práctica)

Con los 8 requisitos en ✓, el oficial tocó **"Enviar a aprobación"**. Confirmación —
**"¿Enviar esta solicitud a aprobación?"**, botones **"Sí, enviar"** / **"Cancelar"** (ver B3,
se repite acá). Al confirmar: cuadro de éxito — **"Solicitud enviada a aprobación. Ahora la
revisa el gerente en Aprobación."**, con **"Aceptar"**.

La pantalla vuelve a `/solicitudes/{id}`, ahora así:

- Estado: **"Pendiente de aprobación"**.
- Línea informativa: **"Enviada por [usuario] el [fecha hora]. Espera la decisión del
  gerente."**
- Tarjeta "Cliente": la etiqueta del ingreso cambia a **"Ingreso neto comprobado (evaluado al
  enviar)"** — ya no dice solo "Ingreso neto comprobado" como en Borrador.
- "Condiciones y simulación" pasa a **solo lectura**: cuota más alta, TEA/TNA, CFT, total a
  pagar y afectación siguen visibles, pero **ya no hay campos editables, ni "Simular"**.
- **Desaparece la lista de 8 requisitos** y **los botones "Enviar a aprobación"/"Cancelar
  solicitud"**. Solo queda **"Volver a la lista"**.

**Verificado en el código y con `SELECT` en `sgcpc_db_pruebas`**:

- **Qué se congela (snapshot) al enviar**: `ServicioSolicitudes.enviarAAprobacion` guarda, en
  la misma fila de `solicitudes_credito`: `estado_solicitud = 'PENDIENTE_APROBACION'`,
  `ingreso_cliente_evaluado` (el ingreso real del cliente en ese momento, nunca recalculado
  después), `limite_afectacion_aplicado` y `porcentaje_afectacion` (recalculados contra el
  ingreso/deudas actuales, no la simulación vieja, ver Paso 1). También se vincula, con
  `servicioConsultaCrediticia.vincularASolicitud`, la consulta al buró más reciente con
  evidencia (`consultas_crediticias.id_solicitud` pasa a apuntar a esta solicitud). **Nota
  (22/10/2026)**: una verificación urgente sobre el documento de evidencia de esta consulta no
  encontró ningún error de código — ver la nota aclaratoria y B42/C19 en `03_aprobacion.md`.
- **Auditoría**: `ENVIAR_SOLICITUD_APROBACION` (SOLICITUD_CREDITO) — confirmada con `SELECT`
  sobre `auditoria_operaciones` del segundo cliente de práctica.
- **Cómo se entera el gerente**: no hay ninguna notificación directa (correo, aviso emergente)
  — el gerente lo ve en su propio **Panel**, tarjeta "Pendientes": `ServicioPanel.
  calcularIndicadores` (perfil `GERENTE_ADMINISTRADOR`) cuenta
  `solicitudDAO.listarPendientesAprobacion(conexion).size()` — el mismo "1 solicitudes
  pendientes de aprobar" ya documentado en `00_apertura.md`/`03_aprobacion.md`. Desde ahí entra
  a la bandeja de Aprobación y la ve listada.
- **¿El oficial o el gerente pueden retirar/cerrar una solicitud ya enviada?** El **oficial,
  no** — `ServicioSolicitudes.cancelar` solo funciona en `BORRADOR`
  (`validarEstadoBorrador`), y el botón "Cancelar solicitud" de esta ficha deja de mostrarse
  apenas se envía. **El gerente, sí** — `ServicioAprobacionCredito.cancelar` acepta
  `PENDIENTE_APROBACION` o `APROBADA` (sin formalizar), con motivo obligatorio, y audita
  `CANCELAR_SOLICITUD` igual que el camino del oficial (mismo código de auditoría, dos
  orígenes distintos). Es el mismo botón "Cancelar solicitud" de la pantalla de Aprobación ya
  documentado en `03_aprobacion.md` — ver C17 (Cubierto, cerrado el 22/10/2026).

## Hallazgos de esta escena

Separados de "Cómo funciona hoy" (arriba, ficha por ficha). Clasificación **A** = no cumple el
anteproyecto; **B** = mejora de uso, no cambia el RF; **C** = a confirmar leyendo el RF (se
resuelve más abajo, sin corregir nada).

### Nueva solicitud y simulación (RF05)

- **C → clasificado B** (ver el detalle abajo): la fecha propuesta en el cotizador (10/11) no
  coincide con la fecha de la solicitud (21/11). **Reconfirmado hoy (22/10/2026)**: tampoco
  coincide el CRITERIO de cada sugerencia (cotizador = mínimo del rango 20-45 días; Nueva
  Solicitud = hoy + 1 mes fijo, ver "Verificado en el código" del Paso 1) — mismo hallazgo, sin
  duplicar.
- **C → clasificado B** (ver el detalle abajo): el monto queda editable mientras la solicitud
  sigue en Borrador ("Simular" lo guarda).
- **B29** — La pestaña/botón **"④ Solicitud"** saca al oficial de la ficha del cliente a otro
  módulo (`/solicitudes/nuevo`, menú "Solicitudes") — pierde las pestañas ①-③ y sus tildes de
  avance, sin ningún rastro visual de que viene de esa ficha salvo la cédula ya precargada.
  Mismo patrón que el cotizador (RF01, Escena 01: "falta un botón que lleve los datos hacia la
  Solicitud, hoy cada pantalla se llena de nuevo") — acá el salto es de pantalla completa, no
  solo de datos sin traer.
- **Confirmado con este ejemplo (B28, ver `01_llega_el_cliente.md`)**: se llega a "Nueva
  solicitud" con el Expediente incompleto (sin cédula ni comprobante de ingreso cargados) y
  **sin ningún aviso** — verificado en el código que `ServicioSolicitudes.crear` no chequea el
  expediente en absoluto (ver "Verificado en el código" del Paso 1); el aviso recién llegaría,
  si faltara algo, al "Enviar a aprobación".
- **B** — "Cancelar" en Nueva Solicitud debería volver a la ficha del cliente, no solo a la
  lista de solicitudes; falta un encabezado "Cliente: … ← Volver a la ficha" (evaluar migas de
  pan) y algún aviso al crear la solicitud (hoy no hay ninguno intermedio).
- **B** — El nombre "Condiciones y simulación"/el botón "Simular" confunden porque en realidad
  guarda; proponer el título "Condiciones del préstamo y capacidad de pago", un texto de ayuda,
  y renombrar el botón a "Recalcular".
- **B30** — "Enviar a aprobación" se muestra siempre habilitado, aunque la lista de 8
  requisitos tenga algún ✗ (como en el ejemplo de hoy, cédula y comprobante de ingresos).
  Verificado que, en ese caso, el formulario ni siquiera pide confirmación (el
  `data-confirmar` de SweetAlert2 solo se agrega cuando el checklist está completo) — el clic
  manda el `POST` directo, y es el servidor quien lo rechaza con la lista de faltantes. Sugerir
  deshabilitar el botón (o al menos pedir confirmación siempre) e indicar en pantalla qué falta
  antes de intentarlo, en vez de dejar que el oficial descubra el rechazo después del clic.
- **B31 (familia B3)** — "Cancelar solicitud" (rojo) queda junto a "Enviar a aprobación"
  (verde) — riesgo de un clic equivocado entre las dos acciones, de consecuencias muy
  distintas. Verificado que "Cancelar solicitud" sí tiene un paso intermedio (despliega un
  formulario con motivo obligatorio antes de poder confirmar) y que la confirmación dice
  explícitamente "No se puede deshacer" — mitiga el riesgo de un clic accidental que llegue a
  concretarse solo, pero no el de abrir por error el formulario de cancelación pensando que es
  el de envío. Revisar nombre/ubicación de los dos botones (ej. separar más, o mover
  "Cancelar solicitud" a un lugar menos prominente) — mismo tipo de riesgo ya anotado en B3
  (texto de "Cancelar" genérico en los modales de confirmación del sistema).
- **B32 (ampliado con el camino real observado)** — El cuadro de error de "Faltan completar
  estos puntos antes de enviar a aprobación:" (confirmado en pantalla el 22/10/2026, ver Paso
  2) solo lista el texto de lo que falta, sin ningún acceso directo al Expediente —
  confirmado que el camino real que siguió el oficial para llegar a resolverlo tomó 5 pasos
  (ver "Camino real observado" arriba). Sugerir un botón **"Ir al expediente"** dentro del
  mismo cuadro (mismo mecanismo que `mensajeErrorEnlaces`, ya usado en otros mensajes de error
  del sistema para convertir un dato del texto en un enlace real) — y, más específico todavía,
  que **cada ✗ de la lista de 8 requisitos** (en la ficha de la solicitud, no solo en el
  cuadro de error) enlace directo al lugar donde se resuelve — ej. "✗ Cédula de identidad
  vigente" → Expediente, fila de la cédula; "✗ Autorización de consulta al buró vigente" →
  pestaña ③, Paso A.
- **B33** — El enlace **"Ver ficha completa / documentos"** de la tarjeta "Cliente" (en la
  ficha de la solicitud) pasa desapercibido — confirmado con el camino real observado: el
  oficial no lo vio y en su lugar hizo los 5 pasos completos (menú → Clientes → buscar → Ver /
  editar → pestaña ② Expediente) para llegar al mismo lugar al que ese enlace ya lo hubiera
  acercado en un paso menos. Sugerir hacerlo más visible (un botón, no un enlace de texto
  simple dentro de un párrafo) y que lleve directo a la pestaña **② Expediente**
  (`#tabDocumentos`) en vez de a "① Datos" — verificado en el código que hoy no lleva ningún
  ancla, así que ese cambio es solo agregar `#tabDocumentos` al `href` ya existente.

### Enviar a aprobación (RF06)

- **Decisión tomada: obligatorio — implementación pendiente (fase 2)** — ver el detalle en
  [01_llega_el_cliente.md](01_llega_el_cliente.md). Aplica también acá: el envío a aprobación
  de hoy pasó sin el comprobante de domicilio, sin bloqueo; cuando se implemente la decisión del
  21/10/2026, `ServicioSolicitudes.agregarFaltantesExpediente` (el gate real de este paso)
  tendrá que incluirlo entre los faltantes que bloquean "Enviar a aprobación" — ver
  `AVANCE.md`.
- **B3 (se repite)** — La confirmación de "Enviar a aprobación" usa **"Cancelar"** para "no
  enviar" — mismo hallazgo ya registrado en `04_formalizacion.md` (el botón "Cancelar" de un
  diálogo de confirmación se puede confundir con cancelar el crédito/la solicitud, cuando en
  realidad significa "no, seguir en esta pantalla"). Confirmado hoy en esta pantalla
  puntualmente: sugerir **"No, volver"** en vez de "Cancelar" para este diálogo — mismo cambio
  que ya propone B3, acá solo con el ejemplo real de esta pantalla.
- **C17 — Cubierto (cerrado 22/10/2026)** — El oficial no tiene, en esta pantalla, ninguna forma
  de retirar una solicitud ya enviada (por ejemplo, si el cliente desiste después de enviarla) —
  el botón "Cancelar solicitud" desaparece apenas pasa a `PENDIENTE_APROBACION`. **Verificado
  que el camino sí existe, pero solo para el gerente**: `ServicioAprobacionCredito.cancelar`
  acepta `PENDIENTE_APROBACION`/`APROBADA` (sin formalizar), con motivo obligatorio, desde la
  pantalla de Aprobación — ver "Registro adicional: envío exitoso" arriba. Es el mismo botón
  "Cancelar solicitud" de `03_aprobacion.md` § Hallazgos, que esa misma escena ya propone
  renombrar a **"Cerrar sin decidir"** (criterio de nombres de botones, con la advertencia
  explícita de revisar todos los lugares que usan la palabra "cancelar" antes de aplicarlo). No
  hay, hoy, un camino para que el **oficial** mismo retire una solicitud que ya envió — tendría
  que pedírselo al gerente.
  **Cierre**: no es un hueco — es **coherente con la separación de funciones** ya verificada en
  el resto del sistema (el mismo criterio de B-2 de la entrega de efectivo del gerente en Caja,
  distinguiendo quién puede decidir sobre una operación ya enviada/confirmada de quién solo la
  originó). Mientras una solicitud está `PENDIENTE_APROBACION`, la decisión sobre ella —
  aprobar, rechazar o cerrarla sin decidir — es, por diseño, del gerente, no del oficial que la
  envió. El camino real, hoy, para "el cliente desiste después de enviada": el oficial avisa al
  gerente (en persona o por el medio que usen), y el gerente la cierra desde Aprobación con
  "Cancelar solicitud"/"Cerrar sin decidir". No hace falta ningún cambio de código para esto —
  no se decide acá ninguna clasificación A/B porque no hay nada que corregir.

## Hallazgos tipo C, clasificados (lectura de los RF, sin modificarlos ni corregir nada)

**Fecha del primer vencimiento: 10/11 en el cotizador, 21/11 en la solicitud.**
`RF01_cotizador.md` §2 y `RF05_solicitud.md` §6: el cotizador (RF01) es una pantalla "sin
registrar ningún dato" — una simulación de uso libre, sin ningún vínculo con la ficha del
cliente ni con la solicitud real que se cree después. La solicitud (RF05) carga sus propios
datos "de nuevo" por diseño (confirmado hoy: "monto, plazo y destino se cargan de nuevo, no
vienen del cotizador"). Ambas fechas (10/11 y 21/11) caen dentro del rango válido de 20 a 45
días desde hoy (21/10/2026) que exigen las dos pantallas desde el 10/10/2026 — no hay ninguna
regla del anteproyecto que exija que coincidan, porque son dos operaciones independientes por
diseño. **Clasificación: B** — no incumple ningún RF; la mejora ya está anotada en Hallazgos de
la Escena 01 (botón "Siguiente: verificar si califica →" que lleve los datos del cotizador
hacia la solicitud, en vez de tener que volver a escribirlos).

**Monto editable en la solicitud mientras está en Borrador.** `RF05_solicitud.md` §2: "Un
formulario para volver a simular (cambiar monto/plazo/destino/fecha) — cada vez que se simula,
se recalcula todo con `ServicioMotorFinanciero` y se vuelve a guardar" — comportamiento
documentado y deliberado mientras la solicitud sigue en `BORRADOR`; recién al "Enviar a
aprobación" (RF06) se congela la evaluación. El anteproyecto (RF05: "calcular la cuota... sin
permitir que la cuota supere el 40 % del ingreso") no restringe la edición en borrador.
**Clasificación: B** — no incumple el RF; lo señalado como mejorable es el nombre del botón
"Simular" (no deja claro que guarda), ya anotado arriba.

## Recorrido completo del oficial (22/10/2026)

Resumen en orden real de todo el circuito del oficial con el **segundo cliente de práctica**,
de punta a punta — cada paso enlaza al detalle ya documentado, con sus hallazgos, sin repetir
el contenido completo acá.

1. **Cotizador** (`GET`/`POST /cotizador`) — simulación libre, sin guardar nada.
   [01_llega_el_cliente.md](01_llega_el_cliente.md) Paso 1. Hallazgos: B (sin accesos directos
   desde el Panel, resultado no al costado, sin botón hacia Precalificación), B25 (el PDF de
   cotización audita `EMITIR_COTIZACION_PDF`, el de requisitos no).
2. **Clientes → "+ Nuevo cliente"**: Precalificación (4 preguntas: fecha de nacimiento,
   departamento, situación laboral, ¿presenta comprobante?) antes de cargar el resto del
   formulario. [01_llega_el_cliente.md](01_llega_el_cliente.md) Paso 2. Hallazgos: B (papel de
   requisitos sin empleador/referencias/cónyuge/40 %/cédula vigente).
3. **Imprimir requisitos**: PDF con los documentos a presentar según la situación laboral, sin
   persistir nada. Mismo Paso 2. Hallazgo: B25 (sin auditoría, a diferencia del cotizador).
4. **Registrar cliente**: alta en una sola transacción (`personas` + domicilio + laboral +
   referencias), auditado `ALTA_CLIENTE`. Mismo Paso 2, "Verificado en el código"/"Registro
   adicional". Hallazgo: C13 (editar después solo avisa si no hay evaluación en curso —
   **cerrado como Cubierto**, cruzado con el checklist de 8 requisitos del gerente).
5. **Expediente — generar y cargar la autorización de buró**: 3 subpasos (generar, firmar en
   papel, cargar firmada); salto automático a `#tabBuro` al cargarla.
   [01_llega_el_cliente.md](01_llega_el_cliente.md) Paso 3. Hallazgos: B26 (debería ir primera
   en la lista), B27 (salto automático sin avisar qué falta — ampliado: también ocurre al
   completar el expediente entero), C14 (el cuadro de "Faltan" exige también el comprobante de
   domicilio, que la barra/el contador tratan como complementario — causa raíz: dos métodos
   con dos criterios; **CERRADO el 28/10/2026**: se implementó la decisión del 21/10/2026, el
   domicilio pasó a obligatorio y los dos criterios coinciden, sin tocar
   `itemsFaltantesExpediente`), confirmación de B13 (coma duplicada en el domicilio del PDF).
6. **Consulta al buró**: cargar el informe de Informconf (se guarda aparte, antes de
   "Registrar consulta") y registrar puntaje/clasificación/mora/resumen.
   [01_llega_el_cliente.md](01_llega_el_cliente.md) Paso 4. Hallazgos: B28 (el cuadro de éxito
   y "Siguiente: ④ Solicitud →" no avisan que el Expediente sigue incompleto), C15 (informe
   cargado sin registrar consulta — **cerrado como Cubierto**, quedó vinculado al registrar).
7. **Solicitud — crearla y revisarla**: desde "④ Solicitud"/"Siguiente: ④ Solicitud →" (mismo
   elemento) hacia `/solicitudes/nuevo`, con el expediente todavía incompleto y sin aviso; la
   ficha en Borrador, con los 8 requisitos y 2 de ellos en ✗. [02_solicitud.md](02_solicitud.md)
   Paso 1. Hallazgos: B29 (saca al oficial de la ficha del cliente), B30 (Enviar a aprobación
   habilitado con ✗, sin confirmación en ese caso), B31 (Cancelar solicitud en rojo junto a
   Enviar en verde, familia B3), confirmación de B28 con este ejemplo real.
8. **Intento de envío con faltantes**: "Enviar a aprobación" sin confirmación previa, cuadro de
   error con la lista exacta (cédula, comprobante de ingreso); la solicitud sigue en Borrador,
   sin auditoría del intento. [02_solicitud.md](02_solicitud.md) Paso 2, "Registro adicional:
   intento rechazado". Confirma en pantalla lo ya verificado en código para B30.
9. **Camino real de 5 pasos al Expediente**: Entendido → menú Clientes → buscar → Ver/editar →
   pestaña ② Expediente — en vez del enlace "Ver ficha completa / documentos", ya disponible
   en la misma ficha de la solicitud, que no se vio. [02_solicitud.md](02_solicitud.md) Paso 2,
   "Camino real observado". Hallazgos: B32 (ampliado con el ejemplo de los 5 pasos), B33 (el
   enlace pasa desapercibido, no lleva directo a Expediente).
10. **Carga de los documentos que faltaban**: cédula (2 de 3, se queda en el Expediente),
    comprobante de ingreso (3 de 3, barra verde, "✓ Expediente completo" — pero el cuadro de
    esa carga todavía dice "Faltan: Comprobante de domicilio"), y por último el comprobante de
    domicilio (recién ahí el cuadro deja de decir "Faltan", y salta solo a `#tabBuro` aunque el
    buró ya estaba hecho). [01_llega_el_cliente.md](01_llega_el_cliente.md) Paso 3. Hallazgos:
    B34 (el botón de ② sigue ofreciendo "Siguiente: ③" con el buró y la solicitud ya en
    curso — actualizado con la causa exacta: el botón de ③ sí revisa si hay una solicitud
    abierta, el de ② nunca lo hizo), B35 (el cuadro "Expediente completo" no distingue si el
    buró ya está hecho), C14 (ejemplo más claro: "✓ Expediente completo" y "Faltan:
    Comprobante de domicilio" convivían en la misma pantalla — **CERRADO el 28/10/2026**, ver
    el punto 5 de arriba: ya no puede darse, porque sin el domicilio el expediente no está
    completo).
11. **Vuelta a la solicitud**: desde la pestaña "④ SOL-...(Borrador) →", **1 clic** — con los 8
    requisitos ya en ✓ y el aviso en verde (**9 requisitos desde el 28/10/2026**: se sumó el
    comprobante de domicilio). [02_solicitud.md](02_solicitud.md) Paso 1,
    "Registro adicional: expediente y buró completos". Dato de uso: este camino de vuelta (1
    clic) es mucho más corto que el camino de ida al Expediente (5 pasos, ver el punto 9) —
    asimetría para unificar en fase 2.
12. **Envío a aprobación**: con el checklist completo, la confirmación SÍ aparece
    ("¿Enviar esta solicitud a aprobación?"); al confirmar, la solicitud pasa a **Pendiente de
    aprobación**, con el ingreso y la afectación congelados, y queda de solo lectura para el
    oficial. [02_solicitud.md](02_solicitud.md) Paso 2, "Registro adicional: envío exitoso".
    Hallazgos: B3 (se repite — el "Cancelar" del diálogo de confirmación), C17 (el oficial no
    puede retirar una solicitud ya enviada; solo el gerente, desde Aprobación, con el mismo
    botón que esa escena ya propone renombrar a "Cerrar sin decidir" — **Cubierto**, cerrado el
    22/10/2026: es la separación de funciones esperada, no un hueco).

**Qué sigue**: la solicitud queda esperando al gerente en Aprobación — ver
[03_aprobacion.md](03_aprobacion.md) (ya recorrido con el primer cliente, el 21/10/2026; queda
pendiente repetirlo con este segundo cliente de práctica si hiciera falta un segundo caso real
de aprobación).

## Completitud del tramo 1

Control de completitud contra el código (solo lectura, verificado hoy 22/10/2026): todos los
caminos que existen en la parte del oficial (Cotizador, alta de cliente, Expediente, Consulta al
buró y Solicitud), marcando cuáles se recorrieron en pantalla y cuáles quedan pendientes. Detalle
de cada camino ya recorrido en los Pasos de arriba, en
[01_llega_el_cliente.md](01_llega_el_cliente.md) y en
[01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md).

| Camino | Existe en el código | ¿Se recorrió en pantalla? |
|---|---|---|
| Crear la solicitud y simular (monto/plazo/destino/fecha) | `ServicioSolicitudes.crear`/`aplicarSimulacion` | **Sí** — Paso 1, con los dos clientes de práctica |
| Cliente que no califica en la Precalificación (edad, zona, situación laboral o sin comprobante) | `ServicioClientes.evaluarPrecalificacion` (reutiliza `evaluarPoliticaCredito` y `motivoSinComprobanteSiAplica`, mismo texto y misma condición que `validarPoliticaCreditoAlta`) | **No recorrido** — los dos clientes de práctica calificaron en los 4 puntos; nunca se probó con una persona fuera de rango |
| Cédula duplicada al registrar | `ServicioClientes.alta` rechaza con `NegocioException`: "Ya existe una persona registrada con la cédula..." | **No recorrido en pantalla** — verificado solo en el código; el hallazgo B sobre el momento en que se detecta (recién al tocar "Registrar cliente") viene de leer el código, no de probar un alta duplicada real |
| Edición de datos después del alta — aviso (deja de calificar, pero guarda igual) vs. bloqueo (hay una evaluación en curso) | `ServicioClientes.actualizar` (siempre guarda y solo avisa) + `bloquearSiCambianDatosDeEvaluacion`/`cambiaronDatosDeEvaluacion` (bloquea antes de escribir, solo si hay solicitud `PENDIENTE_APROBACION`/`APROBADA` sin formalizar o préstamo `PENDIENTE_DESEMBOLSO` Y cambian los 7 campos de evaluación) | **No recorrido en pantalla** — ningún cliente de práctica fue editado mientras tenía una evaluación en curso; aclarado y cerrado como **C13 (Cubierto)** solo con lectura del código |
| Autorización de consulta al buró vencida (más de 30 días) | `ServicioConsultaCrediticia.validarConsultaPermitida`/`tieneAutorizacionVigente`, parámetro `buro.dias_vigencia_consentimiento` (= 30) | **No recorrido** — los dos clientes generaron y usaron la autorización el mismo día |
| Reemplazar un documento del Expediente ya cargado (ej. subir una cédula nueva encima de la vigente) | `ServicioDocumentos.cargarDocumento` marca el `VIGENTE` anterior como `REEMPLAZADO` y versiona; la ficha vuelve a mostrar el documento como "Falta" hasta que se cargue el nuevo | **No recorrido** — cada documento de los dos clientes se cargó una sola vez (mismo patrón sin probar que C7 en `04_formalizacion.md`) |
| Consulta al buró con mora (bloqueo RN-18, al enviar a aprobación) | `ServicioSolicitudes.enviarAAprobacion` — rechazo directo e inmediato: "El cliente figura con deudas en Informconf. No se puede otorgar el crédito." | **No recorrido** — ninguno de los dos clientes de práctica tenía mora registrada (si la tuviera, el 8.º requisito del checklist nunca llegaría a ✓) |
| Consulta al buró con clasificación D o E, confirmando "No hay mora" (solo aviso, no bloquea) | Confirmación de SweetAlert2 en `clientes/ficha.jsp`: "La clasificación [D/E] suele indicar atrasos. ¿Confirma que Informconf no informa mora?" — si se confirma, el sistema sigue igual (clasificación y mora son dos datos independientes) | **No recorrido** — ninguno de los dos clientes tuvo esa combinación puntual (clasificación D/E junto con "No" en mora) |
| Registrar una nueva consulta al buró sobre un cliente que ya tiene una registrada | Botón "Nueva consulta" (`botonNuevaConsulta`), que reaparece el formulario oculto — mismo `ServicioConsultaCrediticia.registrar` de la primera vez | **No recorrido** — cada cliente de práctica tuvo una sola consulta registrada |
| Afectación al ingreso mayor al 40 % (al crear o al simular) | Indicador en vivo + `superaLimiteAfectacion` dentro de `crear`/`aplicarSimulacion`, contra `credito.limite_afectacion` | **No recorrido** — la afectación de los dos clientes quedó bien por debajo del límite en todas las simulaciones probadas |
| Envío a aprobación con requisitos faltantes | `ServicioSolicitudes.enviarAAprobacion` — junta `agregarFaltantesExpediente`/política/afectación/`agregarFaltantesBuro` en un solo mensaje "Faltan completar estos puntos antes de enviar a aprobación:" | **Sí** — Paso 2, "Registro adicional: intento rechazado", con el segundo cliente de práctica |
| Envío a aprobación con los 8 requisitos en ✓ (éxito) | Mismo método, snapshot de ingreso/afectación, `marcarEnviada`, auditoría `ENVIAR_SOLICITUD_APROBACION` | **Sí** — Paso 2, "Registro adicional: envío exitoso", con el segundo cliente de práctica |
| "Cancelar solicitud" en Borrador | `ServicioSolicitudes.cancelar` — solo funciona en `BORRADOR` (`validarEstadoBorrador`), pasa a `CANCELADA`, audita `CANCELAR_SOLICITUD`, no reversible | **No recorrido en pantalla** — los dos clientes llegaron hasta el envío exitoso, ninguna solicitud se canceló; verificado solo en el código y en `solicitudes/ficha.jsp` |
| Solicitud retirada/cerrada después de enviada (solo el gerente, no el oficial) | `ServicioAprobacionCredito.cancelar` — acepta `PENDIENTE_APROBACION`/`APROBADA` sin formalizar, motivo obligatorio, mismo código de auditoría `CANCELAR_SOLICITUD` | **No recorrido en pantalla** — verificado solo en el código, ver "Registro adicional: envío exitoso" (Paso 2) y C17 (**Cubierto**, cerrado el 22/10/2026) |
| Acceso por URL directa de un rol sin permiso (ej. cajera o gerente a `/solicitudes`; cajera a `/clientes` con intención de editar) | `FiltroAutorizacion` exige `GESTIONAR_SOLICITUD` para `/solicitudes` (`NINGUNO` para gerente y cajero → 403) y `REGISTRAR_EDITAR_CLIENTE` para `/clientes` (`CONSULTA` para cajero, nunca `TOTAL` → no puede registrar/editar) | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Clientes/Solicitud) |
