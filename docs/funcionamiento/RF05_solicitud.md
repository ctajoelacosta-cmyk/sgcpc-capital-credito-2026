# RF05 — Solicitud de crédito: crear, cuota, tasa, CFT y afectación

## 1. Qué exige el anteproyecto

**RF05** (`docs/RF_ANTEPROYECTO.md`):

> El sistema debe permitir crear la solicitud de crédito y calcular la cuota, la tasa, el costo
> financiero total y el porcentaje de afectación del ingreso, sin permitir que la cuota supere el
> 40 % del ingreso.

Relacionados, citados en el análisis porque delimitan qué es responsabilidad de RF05 y qué no:

> **RF01**: ...simular un préstamo indicando monto, plazo y fecha del primer vencimiento, y
> mostrar e imprimir el plan de cuotas sin registrar ningún dato.
>
> **RF03**: ...cargar el expediente de documentos del cliente según su situación laboral, e
> indicar qué documentos faltan y si la cédula está vencida.
>
> **RF04**: autorización firmada y consulta al buró (ver `RF04_buro.md`).
>
> **RF06**: ...enviar la solicitud a aprobación solo cuando estén completos los requisitos
> previos, y permitir cancelarla registrando el motivo.
>
> **RF07**: ...mostrar al gerente las solicitudes pendientes de aprobación en orden de llegada y
> el historial de las solicitudes ya decididas.

## 2. Cómo funciona

El oficial de crédito abre **Solicitudes → Nueva solicitud** (`solicitudes/nueva.jsp`), ingresa la
cédula de un cliente ya registrado, el monto, el plazo, el destino del crédito y la fecha estimada
del primer vencimiento, y confirma. El sistema:

1. Verifica que el cliente no tenga ya un compromiso abierto con la empresa (una solicitud propia
   sin formalizar o un préstamo no cerrado, RN-01/RN-02) — si lo tiene, rechaza con un mensaje que
   indica cuál es.
2. Verifica que el cliente exista y esté `ACTIVA` (no dado de baja).
3. Verifica la política de crédito (edad, departamento habilitado, situación laboral).
4. Simula el plan con `ServicioMotorFinanciero.cotizar` (el mismo motor del cotizador de RF01,
   ver §4) y calcula el porcentaje de afectación al ingreso.
5. Guarda la solicitud en estado `BORRADOR` con el código `SOL-aaaa-NNNN`.

La ficha de la solicitud (`solicitudes/ficha.jsp`) muestra, mientras está en `BORRADOR`:

- Un formulario para volver a simular (cambiar monto/plazo/destino/fecha) — cada vez que se
  simula, se recalcula todo con `ServicioMotorFinanciero` y se vuelve a guardar.
- Un checklist informativo previo al envío (edad, zona, situación laboral, cédula vigente,
  comprobante de ingresos, autorización de buró vigente, consulta al buró registrada) — solo
  informativo, no bloquea nada por sí mismo.
- La cuota más alta del plan (con IVA), la TEA/TNA simulada, el CFT simulado, el total a pagar
  (con IVA) y el porcentaje de afectación junto al límite aplicado.
- Los botones "Enviar a aprobación" (RF06) y "Cancelar solicitud".

**Importante (alcance de RF05 frente a RF06), actualizado el 10/10/2026 — corregido el
11/10/2026 en la reprueba del Lote 1 (esta sección había quedado desactualizada):** crear la
solicitud (`crear`) y volver a simular (`simular`) **nunca bloquean** por expediente incompleto
ni por falta de consulta al buró — esos dos bloqueos están en `enviarAAprobacion` (RF06), no en
la creación. **El 40 % de afectación es distinto: desde el 10/10/2026 (decisión de Joel, ver
sección 9) SÍ bloquea ya en `crear`/`simular`** — no se puede crear ni simular una solicitud por
encima del 40 %, sin la excepción de garante que existía antes. Esto es coherente con la letra
del anteproyecto, que separa "crear la solicitud y calcular" (RF05) de "enviar a aprobación solo
cuando estén completos los requisitos previos" (RF06) — pero el 40 % de afectación, al ser una
condición de la propia cuota calculada, termina siendo un control de `crear` en la práctica. Ver
el detalle completo (y por qué) en los puntos 3 y 4 de abajo y en la sección 9.

## 3. Auditoría

`ServicioAuditoria.registrar`, entidad `SOLICITUD_CREDITO`, registra:

| Acción | Cuándo |
|---|---|
| `CREAR_SOLICITUD` | Al crear la solicitud en `BORRADOR` (incluye código y cliente) |
| `ENVIAR_SOLICITUD_APROBACION` | Al pasar a `PENDIENTE_APROBACION` (RF06) |
| `CANCELAR_SOLICITUD` | Al cancelar desde `BORRADOR`, con el motivo en el detalle |

`simular` (volver a cotizar monto/plazo/destino/fecha) **no audita** — es un recálculo, igual
que la cotización de RF01 ("no guarda registros").

## 4. Dónde está

- **Servicio**: `servicios/ServicioSolicitudes.java` — `crear`, `simular`,
  `enviarAAprobacion` (RF06), `cancelar`, y el método privado `aplicarSimulacion` (el corazón de
  RF05: valida límites, llama al motor financiero y calcula la capacidad de pago).
- **Motor financiero**: `servicios/ServicioMotorFinanciero.java` — `cotizar` (cuota/TEA/TNA/CFT),
  `validarLimiteTasa` (tope de usura), `calcularCapacidadPago` (porcentaje de afectación).
- **Cotizador RF01**: `servicios/ServicioCotizacion.java` — delega en el mismo
  `ServicioMotorFinanciero.cotizar`.
- **Controlador**: `controladores/ControladorSolicitudesServlet.java`.
- **Permisos**: `filtros/FiltroAutorizacion.java` (`/solicitudes` → `Permiso.GESTIONAR_SOLICITUD`)
  y `servicios/ServicioAutorizacion.java` (matriz).
- **Pantallas**: `solicitudes/nueva.jsp`, `solicitudes/ficha.jsp`, `solicitudes/lista.jsp`.
- **Parámetros** (`bd/03_parametros.sql`, tabla `parametros_sistema`): `credito.monto_minimo`
  (500.000 Gs.), `credito.monto_maximo` (15.000.000 Gs.), `credito.plazo_minimo` (3 meses),
  `credito.plazo_maximo` (24 meses), `credito.limite_afectacion` (0,40 = 40 %),
  `credito.tea_empresa` (0,27), `credito.edad_minima`
  (22), `credito.edad_maxima` (71), `credito.departamentos_habilitados`,
  `credito.situaciones_laborales_admitidas`. (`credito.exigir_garante` ya no lo lee ningún
  servicio desde el retiro de codeudor, 18/10/2026 — pendiente de eliminar en el Commit 3.)
- **Tabla** `limites_tasa_usura` (no `parametros_sistema`): tope de tasa vigente del BCP, hoy
  27,39 % TEA ("Tope BCP vigente sept. 2026").
- No existe ningún `.jrxml`/`.jasper` para "la solicitud" en sí (no hay PDF de la solicitud) —
  solo se muestra en pantalla. `informe_solicitudes.jrxml` es el informe de RF35 (lista con
  filtros), no una ficha individual; `requisitos_prestamo.jrxml` lo usa `ServicioClientes`
  (RF03). RF08 (resumen de la solicitud para el gerente) es el único lugar donde existe un
  resumen imprimible de una solicitud puntual, y pertenece a Aprobación, no a RF05.

## 5. Dependencias

- **RF01**: comparte el mismo motor (`ServicioMotorFinanciero.cotizar`) — con los mismos datos
  (monto, plazo, fecha, TEA, feriados), RF05 y el cotizador de RF01 dan exactamente el mismo
  resultado, porque es literalmente el mismo método (ver §6, punto 2).
- **RF02**: la solicitud usa `ingresoNetoComprobado`, `cuotaOtrasDeudasMensual`, fecha de
  nacimiento, departamento y situación laboral del cliente, cargados en RF02.
- **RF03**: la cédula vigente y el comprobante de ingresos son parte del checklist informativo de
  RF05, pero el bloqueo real (que impide enviar a aprobación) está en RF06, no en RF05.
- **RF04**: la consulta al buró (con evidencia, posterior a la autorización vigente) se exige
  recién en `enviarAAprobacion` (RF06) vía `obtenerParaVincularSolicitud` — RF05 no la exige para
  crear ni para simular.
- **RF06**: es quien realmente bloquea con el expediente, la consulta al buró y el 40 % de
  afectación — RF05 solo calcula y deja los datos listos.
- **RF07**: la bandeja de pendientes de aprobación del gerente lista las solicitudes que RF06 ya
  dejó en `PENDIENTE_APROBACION` — RF05 es el origen de esos datos (monto/plazo/cuota/afectación
  aprobados o no dependen de lo que RF05 calculó y guardó).

Un cambio en `ServicioMotorFinanciero` (cuota, CFT, capacidad de pago) afecta a la vez a RF01,
RF05 y, por `aplicarSimulacion`, también a lo que RF06/RF07/RF08 terminan mostrando — no se puede
tocar una fórmula pensando solo en una pantalla.

## 6. Análisis

### Análisis del 10/10/2026 (antes de tocar código, pedido de Joel)

Pregunta de Joel: revisar el código de RF05 contra el texto firmado del anteproyecto, en 7 puntos
concretos. Resultado punto por punto:

**1) Crear la solicitud: quién puede y qué requisitos previos exige.**

- **Quién puede**: la ruta `/solicitudes` está protegida por `Permiso.GESTIONAR_SOLICITUD`
  (`FiltroAutorizacion`). En la matriz de `ServicioAutorizacion`: `OFICIAL_CREDITO` → `TOTAL`;
  `GERENTE_ADMINISTRADOR` → `NINGUNO`; `CAJERO` → `NINGUNO`; `ADMINISTRADOR` → `TOTAL` (por la
  regla general D7: el administrador tiene acceso total a todo el sistema). Es decir, **solo el
  oficial de crédito** puede crear solicitudes en el uso normal (el administrador también puede,
  pero por su acceso total, no porque el anteproyecto lo prevea para esta tarea operativa). Esto
  se valida en el servidor (el filtro), no solo ocultando el botón en el menú.
- **Requisitos previos exigidos AL CREAR** (dentro de la transacción de `crear`, método
  `ServicioSolicitudes.crear`):
  - Que el cliente no tenga ya un compromiso abierto (solicitud propia sin formalizar o préstamo
    no cerrado) — **sí**, servidor, `RuntimeException`/`NegocioException` si lo tiene.
  - Que el cliente exista y esté `ACTIVA` — **sí**, servidor.
  - Política de crédito (edad/zona/situación laboral) — **sí**, servidor,
    `validarPoliticaCredito`.
  - Límites de monto, plazo, fecha de primer vencimiento y tope de tasa de usura — **sí**,
    servidor, dentro de `aplicarSimulacion` (ver punto 4).
  - **Expediente completo** (cédula vigente, comprobante de ingresos) — **no** se exige para
    crear ni para simular. Solo aparece como ítem informativo del checklist en pantalla. El
    bloqueo real está en `enviarAAprobacion` (RF06).
  - **Consulta al buró registrada y vigente** — **no** se exige para crear ni para simular. El
    bloqueo real también está en `enviarAAprobacion` (RF06), vía
    `ServicioConsultaCrediticia.obtenerParaVincularSolicitud`.
  - **Cédula vigente** — igual que expediente: informativo en RF05, bloqueante en RF06.

  Esto **no es un incumplimiento**: el anteproyecto separa "crear la solicitud y calcular" (RF05)
  de "enviar a aprobación solo cuando estén completos los requisitos previos" (RF06) — el sistema
  sigue esa misma separación. Lo señalo igual porque Joel preguntó explícitamente por expediente y
  buró "al crear", y la respuesta exacta es: se piden recién para enviar a aprobación, no para
  crear.

**2) Cuota, tasa, CFT y % de afectación: dónde se calculan, mismo motor que RF01, se muestran en
pantalla y en el PDF.**

- Se calculan en `ServicioSolicitudes.aplicarSimulacion`, que llama a
  `ServicioMotorFinanciero.cotizar(monto, plazoMeses, primeraFechaVencimiento, teaEmpresa,
  feriados)` — el **mismo método**, con la misma firma, que usa `ServicioCotizacion.cotizar`
  (RF01). No hay una segunda fórmula ni una copia: es literalmente el mismo código. Con los
  mismos cuatro datos de entrada (monto, plazo, fecha, TEA) y los mismos feriados activos, RF01 y
  RF05 dan exactamente el mismo resultado — **cumple** lo que pidió Joel verificar.
- El porcentaje de afectación se calcula con `ServicioMotorFinanciero.calcularCapacidadPago`
  (ver punto 3) — motor único también para esa fórmula.
- Se muestran en pantalla: sí, en la ficha de la solicitud (cuota más alta con IVA, TEA/TNA, CFT,
  total a pagar con IVA, afectación y límite aplicado) — todas con `FormateadorNumeros` y, en los
  importes principales, con el monto en letras (regla de formato de guaraníes).
- **PDF de la solicitud**: no existe. RF05 no tiene ningún `.jrxml`/`.jasper` propio (ver §4). El
  anteproyecto, a diferencia de RF01 ("mostrar **e imprimir** el plan de cuotas"), no dice que
  haya que imprimir la solicitud — solo que haya que calcular y, se entiende, mostrar. El único
  PDF con datos de una solicitud puntual es el resumen de RF08 (aprobación), que no es RF05.

**3) Tope del 40 %: qué se compara, si es parámetro, si se valida en servidor, qué mensaje ve el
usuario, y si el sistema es más estricto que el texto firmado.**

- **Qué se compara exactamente**: `calcularCapacidadPago(cuotaMaxima, ingresoCliente,
  cuotaOtrasDeudas)` compara **(cuota nueva más alta del plan + cuota de otras deudas del
  cliente) ÷ ingreso del cliente** contra el límite. **No es solo la cuota nueva**: desde la
  decisión D-127 ("Tanda B, Opción A elegida por Joel") se le suma la cuota de otras deudas que
  el cliente ya viene pagando. (Hasta el 18/10/2026 la fórmula sumaba también el ingreso y la
  cuota de otras deudas del codeudor, si había uno asignado — retirado junto con el codeudor.)
- **Es un parámetro**: sí, `credito.limite_afectacion` = `0,400000` (40,0000 %) en
  `parametros_sistema`, editable desde Parámetros (RF39) sin tocar código.
- **¿Es el sistema más estricto que el texto firmado?** Formalmente **sí**, y es importante que
  quede explícito: el texto dice "sin permitir que la cuota supere el 40 % del ingreso" (una sola
  cuota contra un solo ingreso), y el sistema suma otras deudas y otros ingresos. **Esto ya fue
  decidido por Joel** (D-127, "Tanda B, Opción A"), así que no es un hallazgo nuevo que requiera
  una decisión — es la confirmación de que el código implementa esa decisión ya tomada, y que esa
  decisión efectivamente hace al sistema más estricto que la letra textual del RF05. Lo marco en
  la tabla de abajo como "cumple, con un criterio más estricto, ya decidido por Joel (D-127)".
- **¿Se valida en el servidor?** Sí — el cálculo y el bloqueo (rechazo directo, sin ninguna
  excepción: "La cuota supera el límite de afectación del ingreso... Baje el monto o alargue el
  plazo.") están los dos en `aplicarSimulacion`, llamado desde `crear`/`simular` (ver la
  aclaración de §2 sobre desde cuándo bloquea ya en `crear`/`simular`).
- **Mensaje que ve el usuario**: el de arriba, más visible también en la ficha de la solicitud en
  todo momento (no solo al enviar): un cartel verde/amarillo con "Cumple/No cumple validaciones
  preliminares" y, en la tabla de condiciones, la fila de afectación resaltada en amarillo cuando
  supera el límite.

**4) Límites: monto y plazo mínimo/máximo, fecha del primer vencimiento, tasa contra el tope de
usura.**

- **Monto**: `credito.monto_minimo` = 500.000 Gs., `credito.monto_maximo` = 15.000.000 Gs.
  Validado en servidor en cada `aplicarSimulacion` (`ServicioCotizacion.validarMontoDentroDeLimites`,
  reutilizada de RF01).
- **Plazo**: `credito.plazo_minimo` = 3 meses, `credito.plazo_maximo` = 24 meses. Mismo patrón
  (`validarPlazoDentroDeLimites`).
- **Fecha del primer vencimiento**: en RF05 solo se exige que **no sea una fecha pasada**
  (`ServicioCotizacion.validarFechaPrimerVencimiento`, sin rango mínimo/máximo de días). Esto es
  **distinto** del cotizador de RF01 y de la formalización (RF11), que sí validan un rango de días
  desde hoy (`formalizacion.dias_min/max_primer_vencimiento`, 20 a 45 días) con
  `validarRangoFechaPrimerVencimiento` — el propio código deja un comentario explícito de que ese
  cambio de regla "no fue aprobado para esta pantalla" (la de solicitudes). No es un error: es una
  decisión ya tomada y documentada en el código, la dejo igual registrada porque Joel preguntó
  puntualmente por este límite.
- **Tasa contra el tope de usura del BCP**: en cada `aplicarSimulacion` se lee el límite vigente
  de la tabla `limites_tasa_usura` (no un parámetro de `parametros_sistema`) y se valida con
  `ServicioMotorFinanciero.validarLimiteTasa(teaEmpresa, tasaMaximaVigente)`. Hoy
  `credito.tea_empresa` = 27 % y el límite vigente registrado es 27,39 % ("Tope BCP vigente sept.
  2026") — dentro del tope. Si no hay ningún límite vigente cargado, la solicitud se rechaza
  igual ("No hay un límite de tasa vigente cargado. Consulte al gerente.").

**5) Qué pasa si el ingreso o las deudas del cliente cambian después de crear la solicitud.**

La solicitud guarda una **foto** del ingreso y la cuota de otras deudas evaluados en el momento de
la última simulación (`ingresoClienteEvaluado`), no una referencia
viva a la ficha del cliente. Si el oficial edita la ficha del cliente (RF02) y le cambia el
ingreso o la cuota de otras deudas después de crear la solicitud, **la solicitud no se actualiza
sola**: sigue mostrando los valores viejos hasta que alguien vuelve a simular (botón "Simular" en
la ficha). Nada obliga a volver a simular antes de enviar a
aprobación — `enviarAAprobacion` usa los valores ya guardados en la solicitud, no vuelve a leer el
ingreso actual del cliente.

**Hallazgo puntual, ya no aplica**: este punto analizaba una inconsistencia de
`ServicioSolicitudes.asignarCodeudor` (mezclaba una foto vieja de ingreso con un dato fresco de
cuota de otras deudas al asignar codeudor). Superado por el retiro de codeudor/garante
(18/10/2026, commit `6e7da13`): el método se eliminó entero — ver §10.

**6) Auditoría: qué queda registrado.** Ver §3 — `CREAR_SOLICITUD`,
`ENVIAR_SOLICITUD_APROBACION`, `CANCELAR_SOLICITUD`. `simular` no audita (es un recálculo, mismo
criterio que el cotizador).

**7) Dependencias con RF03, RF04, RF06 y RF07.** Ver §5.

## 7. Decisiones

- (Previa, D-127, Joel) El 40 % de afectación se mide sobre cuota nueva + otras deudas del
  cliente y del codeudor, contra ingreso del cliente + codeudor — más estricto que la letra
  textual del RF05, decisión ya tomada y vigente. *Superado por el retiro de codeudor/garante
  (18/10/2026, commit `6e7da13`) — ver §10: la fórmula ya no suma ingreso/deudas de codeudor.*
- (Previa, D-125, Joel) `credito.exigir_garante` configurable; hoy en `"nunca"`. *Superado por
  el retiro de codeudor/garante (18/10/2026, commit `6e7da13`) — ver §10.*
- **(12/10/2026, Joel, CODEUDOR) Cerrado el pendiente de D9 punto 3** (AVANCE.md): con
  `credito.exigir_garante="nunca"` la pantalla ya ocultaba el formulario de `/solicitudes/{id}`,
  pero `ServicioSolicitudes.asignarCodeudor` no lo rechazaba en el servidor — un `POST` directo a
  `/solicitudes/{id}/codeudor` seguía asignando uno. Ahora el método rechaza con
  `CodeudorNoAdmitidoException` ("La política vigente no admite codeudor.", advertencia/"Entendido")
  antes de tocar la solicitud, si `exigir_garante="nunca"`. Probado en `pruebas/humo_http.sh`
  (sección D-125, valor real de producción) y con unitarias puras
  (`ServicioSolicitudes.motivoCodeudorNoAdmitidoSiAplica`). *Superado por el retiro de
  codeudor/garante (18/10/2026, commit `6e7da13`) — ver §10: `asignarCodeudor` y
  `CodeudorNoAdmitidoException` ya no existen.*
- **(10/10/2026, Joel, en respuesta a este análisis) El 40 % se rechaza ya al crear/simular, no
  solo al enviar a aprobación — sin excepción.** Implementado en
  `ServicioSolicitudes.validarLimiteAfectacion` (pura), llamada desde `aplicarSimulacion`. La
  primera versión (misma fecha) dejaba una salvedad no pedida por Joel (si `credito.exigir_garante`
  no era `"nunca"` y todavía no había codeudor, no rechazaba, para no cerrar la vía de asignar uno
  después) — Joel probó en pantalla y decidió que **no**: con cualquier valor del parámetro, nunca
  se puede crear ni simular por encima del 40 %. Se quitó esa salvedad (y el parámetro
  `garanteHabilitado` del método). **Consecuencia a tener en cuenta** (no pedida explícitamente,
  pero se desprende de esta decisión): si en algún momento se activa `credito.exigir_garante`
  ("siempre"/"según_monto"), un cliente que solo él ya supera el 40 % no va a poder crear ninguna
  solicitud — nunca podría llegar a asignarle un codeudor, porque la creación se rechaza antes. La
  vía del codeudor queda sin uso práctico mientras el rechazo sea sin excepción. *Esta
  "consecuencia a tener en cuenta" quedó sin objeto: superada por el retiro de codeudor/garante
  (18/10/2026, commit `6e7da13`) — ver §10.*
- **(10/10/2026, Joel) En la simulación se muestra la afectación en vivo**, antes de guardar nada
  — `GET /solicitudes/previsualizar-afectacion` (nuevo), usado tanto en "Nueva solicitud" como en
  el formulario "Simular" de la ficha. Probado en pantalla por Joel: da el mismo resultado que el
  cotizador con los mismos datos (Gs. 5.000.000 a 12 meses = cuota Gs. 5.745.449, TEA 27 %) y suma
  correctamente las otras deudas (caso probado: 17,4 % de afectación). Ajustado después de la
  primera prueba (ver más abajo): el porcentaje mostraba toda la precisión del cálculo
  ("66,86953333 %"); ahora se redondea a un decimal (`FormateadorNumeros.comoPorcentajeUnDecimal`,
  nuevo) y el indicador también muestra la cuota estimada: "Cuota estimada: 2.700.000 Gs. ·
  Afectación: 66,9 % (límite: 40 %)". El límite se sigue mostrando con `comoPorcentaje` (sin
  decimales de más, igual que en el resto del sistema). En rojo con "Baje el monto o alargue el
  plazo." cuando supera el límite. Es solo informativo; el bloqueo real es el del punto anterior.
- **(10/10/2026, Joel) Se mantiene el control del 40 % al enviar a aprobación** (RF06,
  `enviarAAprobacion`) sin tocarlo — sigue como red de seguridad adicional, por si los datos
  cambiaron entre la última simulación y el envío.
- **(10/10/2026, Joel) Corregido el hallazgo del punto 5 del análisis**: `asignarCodeudor` ya no
  usa la foto vieja `solicitud.getIngresoClienteEvaluado()` — usa `cliente.getIngresoNetoComprobado()`
  leído en el momento, igual que `crear`/`simular`. Los tres lugares que calculan la afectación
  (`crear`, `simular`, `asignarCodeudor`) pasan ahora siempre por el mismo `aplicarSimulacion` con
  datos frescos del cliente; no hay ningún otro lugar en el código que calcule
  `calcularCapacidadPago` por su cuenta (verificado).
- **(10/10/2026, Joel) La fecha del primer vencimiento de la solicitud pasa a exigir el mismo
  rango de 20 a 45 días** (`formalizacion.dias_min/max_primer_vencimiento`) que el cotizador y la
  formalización, con el mismo mensaje — ya no alcanza con que no sea una fecha pasada. Probado en
  pantalla por Joel. Se eliminó `ServicioCotizacion.validarFechaPrimerVencimiento` (quedó sin
  ningún llamador) y sus dos pruebas en `ServicioCotizacionTest`.
- **(10/10/2026, Joel, ajuste tras la prueba) El rechazo del servidor ya no vacía "Nueva
  solicitud".** El rechazo del 40 % (y cualquier otro rechazo de `crear`) hace un *forward*, no un
  *redirect* — los parámetros del POST (monto, plazo, destino, fecha) siguen disponibles en el
  `request`, así que `nueva.jsp` los vuelve a mostrar en vez de dejarlos en blanco.
- **(10/10/2026, Joel, ajuste tras la prueba) Asteriscos en los campos obligatorios de "Nueva
  solicitud"**: ninguno de los 5 campos (cédula, monto, plazo, destino, fecha) lo tenía. Se agregó
  el `*` en las 5 etiquetas y la nota "Los campos marcados con `*` son obligatorios", igual que el
  resto del sistema (`clientes/ficha.jsp`).
- **(10/10/2026, Joel, ajuste tras la prueba) Carteles de rango (monto, plazo, fecha) con título y
  detalle**, no solo título. Compartido por cotizador/solicitud/formalización donde corresponde:
  - Fecha (`ServicioFormalizacion.validarPrimerVencimientoFormalizacion`): título "La fecha del
    primer vencimiento debe estar entre {mínima} y {máxima}", detalle "Elija una fecha entre {N} y
    {M} días desde hoy."
  - Monto (`ServicioCotizacion.validarMontoDentroDeLimites`): título "El monto debe estar entre
    {mínimo} y {máximo}", detalle "Ajuste el monto dentro de ese rango."
  - Plazo (`ServicioCotizacion.validarPlazoDentroDeLimites`): título "El plazo debe estar entre
    {mínimo} y {máximo} meses", detalle "Ajuste el plazo dentro de ese rango."
- Navegación de la ficha del cliente (fuera de RF05 en sí, pedido en la misma tanda): botón
  "Siguiente: (número) (nombre) →" al final de las pestañas ①, ② y ③, que dispara un clic
  programático sobre la pestaña real (nunca duplica su lógica ni sus avisos). El botón de ③ ya
  existía ("Siguiente: Crear solicitud →"); se le cambió el texto para seguir el mismo patrón
  ("Siguiente: ④ Solicitud →") — el comportamiento (enlace a `/solicitudes/nuevo`) no cambió,
  porque ④ nunca fue una pestaña con contenido propio, siempre fue ese mismo enlace.
- **(10/10/2026, Joel, mejora chica tras la segunda prueba) El código de la solicitud abierta
  ("Ver solicitud SOL-2026-0001") pasa a ser un enlace real.** Hallazgo al implementarlo: el `<a>`
  que ya existía en el controlador (Ajuste 4, previo a este documento) **nunca funcionó como
  enlace** — `plantilla/pie.jsp` lee el mensaje de error con `.textContent`, que descarta
  cualquier etiqueta, así que se veía como texto plano aunque el código tuviera un `<a href=...>`
  (y violaba, sin que nadie lo notara, la regla ya documentada "el mensaje nunca lleva HTML",
  `plantilla/cabecera.jsp`). Arreglado con el mismo patrón que ya existía para los mensajes de
  éxito con botones para imprimir (`mensajeExitoImprimibles`): el mensaje de error sigue siendo
  texto plano; un nuevo atributo paralelo (`mensajeErrorEnlaces`, texto visible → ruta) viaja
  aparte, y `plantilla/pie.jsp` reemplaza ese texto por un enlace real solo en el HTML que entra
  por `.html` (nunca en el título, que SweetAlert2 siempre muestra como texto plano). Sin probar
  en pantalla todavía — queda para el próximo Clean and Build.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 14/11/2026 | **B177**: en el orden del listado, los **nulos van al final en los dos sentidos**. `ordenarParaListado` invertía el resultado completo en descendente, así que invertía también ese criterio y una fila sin el dato encabezaba la lista; ahora el sentido lo aplican `comparar`/`compararTextos`/`compararFechas` **después** de resolver los nulos, igual que en la bandeja de Aprobaciones (B175). El desempate por fecha descendente pasa por el mismo camino. Sin cambio visible en los datos de hoy (ninguna fila llega con el cliente nulo): cubierto por `ServicioSolicitudesOrdenTest`, 22 casos | *(este commit)* | — |
| 14/11/2026 | **B168**: cancelar una solicitud pasa de un formulario desplegable dentro de la ficha a una **ventana** (patrón de B155), con el mismo formulario, los mismos `id` —así el script del detalle obligatorio sigue andando— y sin `data-confirmar`, porque la ventana **es** la confirmación. A diferencia de la baja de un cliente, **abre con el foco en *"No, volver"*** (`autofocus`): cancelar **no se deshace**, el tercer criterio de B105. **B169**: el alta muestra los límites de **monto, plazo y fecha** debajo de cada campo, copiados del Cotizador y leídos de los mismos parámetros y del mismo `calcularRangoPrimerVencimiento` que valida el servidor al crear. Ver `recorrido/FASE2_PLAN.md` § Solicitudes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **B167**: el listado de solicitudes **pagina de 20 en 20** y **ordena por las seis columnas**. Las dos cosas **en memoria**, sobre la lista que la pantalla ya arma: el buscador de texto filtra en memoria desde el 20/10/2026, así que con `LIMIT/OFFSET` ese filtro se aplicaría después del corte y las páginas saldrían incompletas. Se ordena la lista **completa antes de cortar**; la lista blanca y la regla del sentido viven en `ServicioSolicitudes` (`ordenAplicado`/`sentidoAplicado`) y el controlador solo pasa los parámetros crudos. El contador cuenta el total, no la página. La paginación es ahora el fragmento compartido `plantilla/paginacion.jsp`, que usan Clientes y Solicitudes. Cubierto por `ServicioSolicitudesOrdenTest` (19 casos). Ver `recorrido/FASE2_PLAN.md` § Solicitudes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **Tanda 1 de la revisión UX de Solicitudes** (B161-B166). **B161**: *"Nueva solicitud"* ahora dice **para quién es** — el aviso en vivo nombra al cliente (nombre + C.I.) en cuanto la cédula está escrita, y si no existe ninguno lo dice en rojo en vez de quedarse mudo hasta el POST; el mismo endpoint `previsualizar-afectacion` resuelve la persona **antes** de parsear monto y plazo, y el texto lo arma el servidor (el JS solo elige la clase). La rama que usa la ficha (`idSolicitud`) **no cambió**. **B162**: el botón de salir del alta dice *"Volver"* y no *"Cancelar"*, que en esta pantalla es una operación real del negocio (RF06). **B163**: *"Volver a la lista"* conserva la búsqueda y el filtro de estado (mismo mecanismo de sesión que B147 en Clientes). **B165**: el listado vacío distingue tres casos y, sin filtros, dice que todavía no hay solicitudes y ofrece crear la primera. **B166**: *"Limpiar"* y *"Buscar"* pasan a la fila de los filtros. Ver `recorrido/FASE2_PLAN.md` § Solicitudes — tanda 1 | *(este commit)* | — |
| 12/10/2026 | CODEUDOR: cerrado el pendiente de D9 punto 3 — `ServicioSolicitudes.asignarCodeudor` rechaza en el servidor con `credito.exigir_garante="nunca"` (`CodeudorNoAdmitidoException`, advertencia); además, 2 rastros nuevos encontrados y cerrados (opción "Garante" del modal de `mora/lista.jsp`, texto fijo del glosario `GlosarioFinanciero.AFECTACION`). Ver §7. VERIFICADO EN PANTALLA el 12/10/2026 (ver §9). `mvn test` 686 en verde. | *(pendiente)* | `rf14-probado` |
| 10/10/2026 | RF05: enlace real al código de la solicitud abierta (antes era HTML muerto que se mostraba como texto), mecanismo nuevo y genérico `mensajeErrorEnlaces` (mismo patrón que `mensajeExitoImprimibles`). `mvn test` en verde. Cierre de RF05. | `df59811` | `rf05-probado` |
| 10/10/2026 | RF05, ajustes tras la prueba en pantalla: rechazo del 40 % sin excepción de garante, indicador en vivo con cuota estimada y un decimal, carteles de rango con título y detalle, formulario no se vacía al rechazar, asteriscos en obligatorios. `mvn test` en verde (613 pruebas). | (sin commit propio, ver más arriba) | — |
| 10/10/2026 | RF05: 40 % rechazado al crear/simular (primera versión, con salvedad del garante — luego quitada), afectación en vivo en la simulación, fecha del primer vencimiento con el rango de 20-45 días, corrección del dato del cliente al asignar codeudor. `mvn test` en verde (612 pruebas). | (sin commit propio, ver más arriba) | — |
| 10/10/2026 | Primer documento del RF05: análisis completo contra el texto firmado, sin cambios de código | 277b7ce | — |

## 9. Pruebas realizadas en pantalla

**10/10/2026, Joel** — primera ronda, confirmó que funciona:

- Tope del 40 % al crear: rechazo del servidor.
- Indicador en vivo.
- Mismo resultado que el cotizador con los mismos datos (Gs. 5.000.000 a 12 meses = Gs. 5.745.449,
  TEA 27 %).
- La afectación suma las otras deudas (caso probado: 17,4 %).
- Rangos de fecha (20-45 días), monto (500.000-15.000.000) y plazo (3-24).

**10/10/2026, Joel** — segunda ronda, después del Clean and Build con los ajustes de §7, confirmó
que funciona:

- Indicador "Cuota estimada · Afectación" con un decimal y en rojo cuando supera el límite.
- Rechazo del 40 % en el servidor, con el formulario conservando los datos escritos.
- Asteriscos en los campos obligatorios de "Nueva solicitud".
- Carteles de rango con título y detalle.
- Un cliente con una solicitud abierta es rechazado ("El cliente ya tiene una solicitud abierta.
  Ver solicitud SOL-2026-0001.").
- Permisos: la cajera ve "Solicitudes" con candado en el menú y "Acceso restringido" si fuerza la
  dirección.
- Mismo resultado que el cotizador del RF01.

De esa segunda ronda surgió la mejora del enlace (ver §7, último punto) — aplicada, `mvn test` en
verde, **sin probar en pantalla todavía**: queda para el próximo Clean and Build de Joel.

**12/10/2026, Joel** — CODEUDOR, VERIFICADO EN PANTALLA: el cierre del pendiente de D9 punto 3
(rechazo en servidor con `credito.exigir_garante="nunca"`) no introdujo ninguna regresión en el
resto de RF14/RF15/caja probados esa misma pasada (ver `RF14_anular_prestamo.md` §9).

## 10. Pendientes y trabajo futuro

- **(20/10/2026, tanda de usabilidad, punto 6 paso 3)**: la bandeja (`solicitudes/lista.jsp`) ya
  tiene buscador con AJAX (`listado-vivo.js`), por N.º de solicitud, cédula o nombre del cliente
  -- combinado (nunca en reemplazo) con el filtro de estado que ya tenía. Sigue sin paginar (ver
  §10c de `docs/funcionamiento/RNF_revision.md`, pendiente aparte, no se toca en este paso).
- Probar en pantalla, en el próximo Clean and Build, que "SOL-2026-0001" en el cartel de
  solicitud abierta ya es un enlace clickeable a esa solicitud.
- Consecuencia del rechazo sin excepción de garante (ver §7): si algún día se activa
  `credito.exigir_garante`, revisar si la vía del codeudor para un cliente sobre el 40 % sigue
  teniendo sentido tal como está, dado que nunca podría llegar a crear la solicitud para
  asignárselo.
- No hay PDF de la solicitud; confirmar con Joel si en algún momento se quiere uno (RF05 no lo
  pide explícitamente, a diferencia de RF01).
- **Restos de codeudor** (hallazgo de Joel, 16/10/2026): §2 ("formulario para asignar codeudor")
  y §7 ("y el codeudor") de este documento, y posiblemente código/JSP/PDF, siguen mencionando al
  codeudor — contradice la política vigente `credito.exigir_garante = nunca` (RF02/D-125, ver
  `docs/base_de_datos/02_analisis_y_problemas.md`). Sin corregir todavía; revisar y limpiar en la
  etapa de refactor. **Resuelto** por el retiro de codeudor/garante (18/10/2026, commit
  `6e7da13` y documentación de este mismo Commit 2) — ver §10 de este documento.
- **Hueco preexistente destapado al quitar el codeudor** (hallazgo de Joel, 18/10/2026, corregido
  en el mismo commit del refactor): sin comprobante de ingresos, RN-04a exigía un codeudor que,
  con `credito.exigir_garante="nunca"`, nunca se podía asignar — eso era, indirectamente, el único
  bloqueo real. Al quitar el codeudor se perdió, y quedó solo el bloqueo del alta (RF02): un
  cliente ya registrado, editado para sacarle el comprobante (la edición nunca bloquea por esto),
  podía crear una solicitud sin ningún freno. Se agregó un bloqueo directo —
  `ServicioClientes.motivoSinComprobanteSolicitudSiAplica`, mismo texto en los tres momentos de la
  solicitud (`ServicioSolicitudes.validarPoliticaCredito`, llamado desde `crear` y desde
  `enviarAAprobacion`; `ServicioFormalizacion.revalidarCapacidadPagoFormalizacion`) — ver
  REGLAS_DESARROLLO.md §5 ("solo ingreso fijo comprobable").

## 11. Guion para la defensa

RF05 crea la solicitud y calcula cuota, tasa, CFT y afectación con el mismo motor financiero que
el cotizador de RF01 (nunca dos fórmulas distintas, probado dando el mismo resultado con los
mismos datos). El 40 % de afectación se mide, por decisión del negocio, sumando otras deudas del
cliente (más estricto que la letra del RF, a propósito), con la afectación mostrada
en vivo mientras se simula (cuota estimada y porcentaje) y rechazada en el servidor sin excepción
ya al crear o volver a simular -- no solo al enviar a aprobación, que sigue siendo una segunda red
de seguridad por si los datos cambiaron entre medio.
