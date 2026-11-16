# Escena 00 — Apertura del día

Qué cubre: el ingreso de cada perfil al sistema, el cambio de contraseña obligatorio en el
primer ingreso, la revisión de parámetros del día, la primera vista del panel según el perfil,
y la apertura de caja con la entrega de efectivo del gerente antes de empezar a atender.

**Tramo 4 del recorrido, iniciado en pantalla por Joel el 21/10/2026, rol cajera.** Cliente de
ejemplo ficticio de todo el recorrido: **Marta Ovelar (C.I. 1.000.001)**, préstamo
`CC-2026-NNNNN` ya formalizado con los 3 firmados cargados (ver
[04_formalizacion.md](04_formalizacion.md)).

## Paso 1 — Login de cada perfil (RF36)

El login del gerente ya quedó documentado en
[03_aprobacion.md](03_aprobacion.md) Paso 1 (tramo 2); el del oficial no se documentó por
separado (el tramo 1 arrancó con la oficial ya conectada). Hoy se agrega el de la cajera:

- **Usuario/rol**: Cajera (usuario de prueba `cajera`), en su propia ventana.
- **Pantalla y ruta**: inicia sesión en `/acceso`; el sistema la lleva directo a `/panel`.
- **Regla de negocio**: ver [procesos/login.md](../funcionamiento/procesos/login.md).
- **Hallazgos**: ninguno propio de este paso.

### Paso 2 — Cambio de contraseña obligatorio en el primer ingreso (RF36)

### Paso 3 — Revisión de parámetros del día (tasas, límites, feriados) — gerente/administrador (RF39)

## Paso 4 — Primera vista del panel según el perfil (RF30, RF38)

El panel del gerente ya quedó documentado en [03_aprobacion.md](03_aprobacion.md) Paso 2; el de
la oficial en [04_formalizacion.md](04_formalizacion.md) Paso 1. Hoy se agrega el de la cajera:

- **Usuario/rol**: Cajera.
- **Pantalla y ruta**: `/panel`, después del login.
- **Qué responde el sistema**:
  - **"Hoy"**: "Cobros de hoy (0 Gs.)" con enlace "Ir a Cobranza"; "Sin caja abierta" /
    "Efectivo en caja" con enlace "Ir a Caja" (todavía no abrió la caja).
  - **"Cartera"**: "Préstamos en mora" (sin el resto de las tarjetas de Cartera — cartera en la
    calle, cobrado semana/mes — que son solo del gerente/administrador).
  - **"Pendientes"**: **"1 préstamo pendiente de desembolso · 1 listo para desembolsar
    (monto)"**, con enlace "Ir a Desembolsos" — el único préstamo listo es `CC-2026-NNNNN`
    (Marta Ovelar), con el checklist de firmados completo desde el tramo 3.
  - **Menú lateral**: Panel, Clientes y solicitudes, Desembolso y caja, Cobranza y mora,
    Cancelación (sin candados en las opciones que no puede usar — directamente no aparecen;
    Aprobación, Formalización, Informes y Seguridad desaparecen enteras, sin ninguna opción
    habilitada para su perfil).
- **Regla de negocio**: ver [RF30_panel.md](../funcionamiento/RF30_panel.md) y
  [RF38_perfiles.md](../funcionamiento/RF38_perfiles.md) (menú sin candados, D1, ya probado en
  pantalla el 13/10/2026).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Método que arma cada tarjeta**: una sola función para todo el panel,
    `ServicioPanel.calcularIndicadores(conexion, idUsuario, perfil = CAJERO)` — arma distinto
    según el perfil, sin duplicar el método por rol.
  - **"Cobros de hoy" / "Préstamos en mora"**: comunes a los 3 perfiles
    (`pagoDAO.contarConfirmadosEntreFechas`/`sumarAplicadoEntreFechas`,
    `prestamoDAO.listarEnMora`) — ver el detalle completo ya verificado en
    [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md) §1.
  - **"Sin caja abierta"**: `indicadores.setHayCajaAbierta(sesionAbierta != null)`, con
    `sesionAbierta = ServicioCaja.obtenerSesionAbierta(conexion)` — lee `sesiones_caja` buscando
    una fila con `estado_sesion = 'ABIERTA'` (índice único parcial, solo puede existir una). Hoy
    no hay ninguna, así que `isHayCajaAbierta()` es `false` y `panel.jsp` muestra el texto fijo
    "Sin caja abierta" en vez de un monto.
  - **"Pendiente" vs. "listo para desembolsar"**: dos números DISTINTOS, calculados con dos
    métodos distintos — `getDesembolsosPendientes()` = `prestamoDAO.listarPendientesDesembolso(
    conexion).size()` (TODOS los préstamos `PENDIENTE_DESEMBOLSO`, **sin mirar el checklist**);
    `getDesembolsosListos()` = `ServicioFormalizacion.listarListosParaDesembolsar(
    conexion).size()` (la MISMA lista que filtra por `esChecklistCompleto`/`obtenerChecklist`,
    confirmada en el tramo 3 — es la que de verdad arma la bandeja de `/desembolso`). Hoy
    coinciden (1 y 1) porque el único préstamo pendiente ya tiene los 3 firmados cargados.
- **Qué sigue**: "Ir a Caja" para abrir la caja (próximo paso) o "Ir a Desembolsos" (una vez
  abierta).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

**Confirma el principio de "trabajo pendiente" (registrado en [00_LEEME.md](00_LEEME.md) §
Hallazgos generales)**: la cajera ve a `CC-2026-NNNNN` listo para desembolsar apenas entra a su
propio panel, sin que nadie le haya avisado por fuera del sistema — la operación "aparece sola"
porque el checklist se completó en el tramo anterior y el panel ya lo refleja.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

Panel de la cajera, en Edge. Saludo **"Buenos días, [nombre]"**.

- **"Hoy"**: Cobros de hoy (0) con "Ir a Cobranza"; **Efectivo en caja: 0 Gs.** con "Ir a Caja".
- **"Cartera"**: Préstamos en mora (0).
- **"Pendientes"**: **"1 préstamo pendiente de desembolso · 1 listo para desembolsar
  (2.000.000 Gs.)"**, con "Ir a Desembolsos" — es `CC-2026-00002` (segundo cliente de
  práctica), con el checklist ya completo desde el tramo 3.

**El panel sí avisa el trabajo pendiente a la cajera** — contraste directo con B45 (el panel
del oficial no avisa nada cuando se aprueba una solicitud): acá "listo para desembolsar" sube
de 0 a 1 solo con que el checklist se complete, sin que nadie tenga que entrar a avisarle.

**La tarjeta de efectivo muestra "0 Gs.", no "Sin caja abierta"**, porque la sesión de caja
abierta el 22/10/2026 **sigue abierta** (saldo 0 Gs. tras el desembolso de `CC-2026-00001`, que
dejó la caja en cero).

**Verificado en el código y con `SELECT` en `sgcpc_db_pruebas`**:

- **Estado y fecha de la sesión abierta**: `sesiones_caja` tiene 2 filas — la sesión 1,
  `CERRADA` (21/10/2026 22:04); la sesión 2, **`ABIERTA`**, abierta el **22/10/2026 06:09**,
  `saldo_inicial` 3.000.000 Gs. Los movimientos de esa sesión: un solo `DESEMBOLSO` de
  3.000.000 Gs. (signo −1, 22/10/2026 06:20) — saldo teórico = 3.000.000 + (−3.000.000) = **0
  Gs.**, coincide con lo mostrado hoy.
- **¿El panel distingue una caja abierta de otro día?** **No.** `ServicioPanel.
  calcularIndicadores` solo llama a `ServicioCaja.obtenerSesionAbierta` y
  `calcularSaldoTeorico` — ninguno de los dos, ni ningún otro punto de `ServicioPanel`, llama a
  `ServicioCaja.esDeHoy`/`validarSesionDeHoy`. El panel muestra "Efectivo en caja" con el
  mismo criterio sin importar si la sesión es de hoy o de hace una semana — la tarjeta nunca va
  a decir "Sin caja abierta" mientras exista una fila `ABIERTA`, aunque sea vieja.
  **Verificado en el código (misma lógica también para el gerente, que ve la misma tarjeta)**.
- **Qué mensaje exacto aparecería si la cajera intenta desembolsar con esta caja**:
  `ServicioDesembolso.confirmarDesembolso` llama a `ServicioCaja.validarSesionDeHoy(sesion)`,
  que (al no ser de hoy) lanza `CajaDeOtroDiaException` con el texto exacto de
  `ServicioCaja.mensajeCajaDeOtroDia`: **"La caja del 22/10/2026 sigue abierta: ciérrela y
  rinda antes de operar hoy."** — mismo texto reutilizado por los 3 controladores (Caja,
  Cobranza, Desembolso), confirmado en el código, no probado hoy en pantalla (no se intentó
  desembolsar todavía con esta caja vieja).

Ver B49 en Hallazgos.

## Paso 5 — Apertura de caja por la cajera: pantalla /caja (RF15)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: toca **"Ir a Caja"** desde el panel (Paso 4). Se abre `GET /caja`.
- **Qué responde el sistema**:
  - Tarjeta **"Apertura de caja"**: texto **"Fondo sugerido (suma de los préstamos listos para
    desembolsar, con checklist completo): [monto]"** con un ícono ⓘ de ayuda ("Suma el monto
    aprobado de los préstamos que ya tienen el checklist de firmados completo (listos para
    desembolsar hoy). No incluye préstamos pendientes de desembolso cuyo checklist todavía está
    incompleto, porque todavía no se les puede entregar el efectivo."). Campo **"Fondo
    inicial"**, precargado con ese mismo monto (editable) — como el campo es
    `campo-guaranies` (mismo mecanismo que cualquier monto del sistema), apenas se carga la
    pantalla pide en vivo el monto en letras y lo muestra debajo, en gris. Botón **"Abrir
    caja"**.
  - Tarjeta **"Sesiones cerradas"**: filtro **"Cierre entre [fecha] y [fecha]"** + botón
    **"Filtrar"**; hoy, sin ninguna sesión cerrada todavía, dice **"No hay sesiones cerradas
    todavía."**
- **Qué hace el usuario (continuación)**: deja el fondo inicial en el monto sugerido (según
  C9/D-73, representa el efectivo que el gerente ya le entregó en mano) y toca **"Abrir
  caja"**.
- **Qué responde el sistema**:
  - Confirmación: **"¿Abrir la caja con un fondo inicial de [monto]?"**, botones **"Sí, abrir
    caja"** / **"Cancelar"** — el texto se arma en vivo con el monto tecleado en el campo (no
    es un texto fijo, cambia si se edita el fondo).
  - Éxito: cuadro **"Caja abierta."** con botón **"Aceptar"**.
  - La pantalla `/caja` pasa a mostrar: tarjeta **"Sesión abierta"** (Apertura con fecha/hora,
    Fondo inicial, Saldo teórico actual); tarjeta **"Movimientos"** (columnas Fecha/hora, Tipo,
    Monto, Observación, Comprobante) con una primera fila fija **"Apertura (fondo inicial)"**
    (no es una fila de `movimientos_caja`, se agrega siempre primera solo para mostrarla) y,
    como todavía no hubo ningún movimiento real, **"Sin movimientos desde la apertura."**;
    tarjeta **"Cierre y rendición"** (campo "Efectivo contado", "Motivo de la diferencia
    (obligatorio si el efectivo contado no coincide con el saldo teórico)", un texto que
    compara en vivo lo contado contra el saldo teórico ya mostrado arriba, botón **"Cerrar
    caja"** — visible desde el momento mismo de abrir, sin ninguna otra condición); tarjeta
    **"Sesiones cerradas"**, igual que antes de abrir.
- **Regla de negocio**: ver [RF15_apertura_caja.md](../funcionamiento/RF15_apertura_caja.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Fondo sugerido**: `ServicioCaja.calcularFondoSugerido` → delega en
    `ServicioFormalizacion.calcularFondoSugeridoDesembolso`, que suma `monto_aprobado` SOLO de
    los préstamos `PENDIENTE_DESEMBOLSO` con el checklist completo (`esChecklistCompleto` —
    exactamente la misma lista de `listarListosParaDesembolsar`, que ya arma la bandeja de
    `/desembolso` y el indicador del panel, Paso 4).
  - **"Abrir caja"** (`ServicioCaja.abrirCaja`, ejecutado hoy): primero llama a
    `sesionCajaDAO.obtenerAbierta` — si ya hay una sesión `ABIERTA` (de cualquier cajera),
    rechaza con `CajaYaAbiertaException` ("Ya hay una caja abierta desde el [fecha hora]
    ([usuario]). No se abrió una nueva.") **sin llegar a escribir nada** — así que no es
    posible abrir dos cajas a la vez, ni para la misma cajera ni para otra distinta (el índice
    único parcial de `sesiones_caja` es la red de seguridad final ante una carrera real). Si no
    hay ninguna abierta: `INSERT sesiones_caja` (`saldo_inicial` = lo escrito en el campo,
    `estado_sesion = 'ABIERTA'`, `id_cajero` = quien la abrió); audita `ABRIR_CAJA` (entidad
    SESION_CAJA) con el saldo inicial en el detalle. **No inserta ningún `movimiento_caja`
    propio** — el fondo inicial vive únicamente como esa columna de `sesiones_caja` (de ahí la
    fila fija "Apertura (fondo inicial)" en la tabla de Movimientos: no existe como fila real,
    la agrega la JSP solo para mostrarla junto con el resto).
  - **Mensaje de éxito exacto** (verificado en `ControladorCajaServlet`): **"Caja abierta."**
- **Qué sigue**: con la caja abierta, la cajera puede entrar a `/desembolso` (ver C9 más arriba
  para la relación entre este fondo inicial y una entrega del gerente) — aunque, hoy, ningún
  enlace de esta pantalla la lleva directo ahí (ver B19).
- **Al volver al panel**: la tarjeta que antes decía "Sin caja abierta" ahora muestra
  **"[monto] Gs. — Efectivo en caja"**, con el mismo enlace "Ir a Caja" — el panel ya refleja
  la caja recién abierta. Verificado en el código: es el mismo `ServicioPanel.
  calcularIndicadores` del Paso 4 — ahora `isHayCajaAbierta()` da `true`
  (`servicioCaja.obtenerSesionAbierta` encuentra la sesión que se acaba de crear) y
  `getEfectivoEnCaja()` sale de `ServicioCaja.calcularSaldoTeorico(sesionAbierta.
  getIdSesionCaja())` — el mismo cálculo (`saldo_inicial + movimientos`) que ya se vio en
  "Sesión abierta" de `/caja`; hoy, sin ningún movimiento todavía, coincide exactamente con el
  fondo inicial.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Registro adicional: segunda pasada (23/10/2026, apertura de la caja del día)

Después de cerrar la sesión del 22/10 (ver [07_cierre_del_dia.md](07_cierre_del_dia.md)), la
cajera volvió a `/caja`, ya sin ninguna sesión abierta. Tarjeta **"Apertura de caja"**:
**"Fondo sugerido (suma de los préstamos listos para desembolsar, con checklist completo):
2.000.000 Gs."**, con **Fondo inicial** precargado en 2.000.000 Gs. (representa el efectivo
que el gerente le entregó en mano, D-73). Tocó **"Abrir caja"** → confirmación **"¿Abrir la
caja con un fondo inicial de 2.000.000 Gs.?"** (**"Sí, abrir caja"**/**"Cancelar"**) → éxito
**"Caja abierta."** (**"Aceptar"**) → **"Sesión abierta"** (apertura 23/10/2026 10:22, fondo
2.000.000 Gs., saldo teórico 2.000.000 Gs.); **"Movimientos"** (Apertura 2.000.000 Gs.;
**"Sin movimientos desde la apertura."**); **"Cierre y rendición"** (saldo teórico
2.000.000 Gs.); **"Sesiones cerradas"** (2 filas, las ya vistas).

**Igual que la primera apertura** (21/10/2026, Paso 5 de arriba) — misma pantalla, mismos
mensajes, mismo mecanismo (`ServicioCaja.abrirCaja`, auditoría `ABRIR_CAJA`). **B18 se
repite**: el mensaje de éxito "Caja abierta." tampoco dijo qué sigue esta vez.

**Con esto se completa, en pantalla, el ciclo real de un día de caja**: cierre de la sesión
anterior (con su arqueo) → apertura de la sesión del día siguiente — los dos extremos del
tramo 6 (cierre del día) y del tramo 4 (apertura), unidos en una sola secuencia real, aunque
con el salto de un día entre el cierre (23/10 10:10) y esta apertura (23/10 10:22) en vez del
mismo día — ver la nota de apertura de esta escena y C21/B53 en `07_cierre_del_dia.md` sobre
esa misma demora.

### Paso 6 — Entrega de efectivo a la caja — gerente (RF18)

## Hallazgos de esta escena (tramo 4, inicio)

Separados de "Cómo funciona hoy" (arriba, ficha por paso).

- **B16** — El panel no indica que primero hay que abrir la caja para poder desembolsar — la
  tarjeta "Pendientes" dice "1 préstamo pendiente de desembolso · 1 listo para desembolsar",
  pero no dice que, sin caja abierta, no se puede confirmar ningún desembolso (esa condición
  recién se ve al entrar a la ficha del préstamo en `/desembolso/{id}`). Sugerir que la tarjeta
  de Pendientes agregue, cuando `!isHayCajaAbierta()`, algo como "— abra la caja antes de
  desembolsar".
- **B17 (ajustado)** — El texto "1 préstamo pendiente de desembolso · 1 listo para
  desembolsar" es redundante cuando los dos números coinciden (como hoy). Verificado en el
  código cuándo SÍ pueden diferir: "pendientes" (`getDesembolsosPendientes`,
  `prestamoDAO.listarPendientesDesembolso`) cuenta TODOS los préstamos `PENDIENTE_DESEMBOLSO`
  sin mirar el checklist; "listos" (`getDesembolsosListos`,
  `ServicioFormalizacion.listarListosParaDesembolsar`) solo los que ya tienen los 3 firmados
  cargados — difieren exactamente cuando hay, al mismo tiempo, un préstamo **formalizado pero
  todavía esperando que carguen sus firmados** junto con otro que ya los tiene. Propuesta
  ajustada: mostrar **un solo número cuando coinciden** ("N préstamos listos para
  desembolsar (monto)") y, cuando difieren, **"N listos para desembolsar · M esperando
  documentos firmados"** — más claro que repetir la palabra "pendiente" para dos conceptos
  distintos (uno que ya se puede desembolsar, otro que todavía no porque falta papelería).
- **B49 (relacionado con el principio de paneles, confirmado en pantalla el 23/10/2026)** — El
  panel de la cajera muestra "Efectivo en caja" de una sesión abierta de **un día anterior**
  sin avisar que hay que cerrarla antes de operar hoy — confirmado con la sesión abierta el
  22/10/2026 (0 Gs., ver "Registro adicional: segunda pasada" arriba), que el panel del
  23/10/2026 siguió mostrando igual que si fuera de hoy. **Verificado en el código**:
  `ServicioPanel` nunca llama a `ServicioCaja.esDeHoy`, así que la tarjeta no tiene forma de
  distinguir una caja vieja de una de hoy — solo lo descubre quien intente desembolsar o cobrar
  (ahí sí aparece `CajaDeOtroDiaException`: "La caja del [fecha] sigue abierta: ciérrela y
  rinda antes de operar hoy."). Sugerir un aviso en el Panel, tan pronto `!esDeHoy(sesion)`,
  del tipo **"Tiene una caja abierta del [fecha]: ciérrela antes de operar."**, con enlace a
  Caja — mismo criterio que el resto del principio de paneles
  (`00_LEEME.md` § Hallazgos generales): si el sistema ya sabe que hay un bloqueo esperando,
  debería avisarlo en el panel, no recién cuando la cajera intente operar y se encuentre con el
  rechazo.
- **B18** — El mensaje de éxito "Caja abierta." no dice qué sigue, a diferencia de otros avisos
  del sistema que ya incorporan el siguiente paso (ej. "Expediente completo. Siguiente paso:
  registrar el resultado del buró."). Sugerir, cuando haya al menos un préstamo listo para
  desembolsar: **"Caja abierta. Siguiente paso: desembolsar CC-2026-NNNNN → Ir a
  Desembolsos."**
- **B19** — La pantalla `/caja` no tiene ningún acceso directo a Desembolsos (hay que volver al
  panel o usar el menú), y la tarjeta "Cierre y rendición" — con el botón "Cerrar caja" — queda
  visible desde el mismo momento de abrir, antes de hacer ninguna operación del día. Sugerir un
  botón **"Ir a Desembolsos (N listos)"** arriba de todo (apenas se abre la caja) y mover
  "Cierre y rendición" más abajo en la pantalla, o plegado por defecto, para que no compita
  visualmente con lo que la cajera necesita hacer primero.
- **Nota para B4 (Escena 04, Paso 4, "Generar documentos")**: la confirmación de "Abrir caja"
  ("¿Abrir la caja con un fondo inicial de 3.000.000 Gs.?") es un buen ejemplo de resumen de una
  línea antes de confirmar — el texto se arma en vivo con el monto real, no un genérico "¿Está
  seguro?". El mismo criterio (mostrar el dato concreto, no solo la pregunta) es lo que B4 pide
  para la confirmación de "Generar documentos" en Formalización.
- **C8 — La cajera ve "Clientes y solicitudes" en el menú: qué le permite hacer, verificado en
  el código.** El módulo queda visible porque "Cotizador" y "Clientes" no tienen condición
  propia (siempre habilitados en `menu.jsp`); "Solicitudes" sí la tiene
  (`esOficial || esAdministrador`) y **no aparece** para la cajera. Dentro de "Clientes": la
  cajera tiene `REGISTRAR_EDITAR_CLIENTE = CONSULTA` (`ServicioAutorizacion`, no `TOTAL` como
  oficial/gerente) — por regla general de `FiltroAutorizacion`, un `GET` solo exige que el
  nivel no sea `NINGUNO` (`CONSULTA` alcanza), un `POST` exige `TOTAL`: puede ENTRAR a
  `/clientes` y abrir una ficha, pero no puede dar de alta ni editar nada (el servidor rechaza
  cualquier `POST` real). **Además, desde la decisión D2 de `RF38_perfiles.md` (13/10/2026,
  probada en pantalla, tag `rf38-probado`), la ficha que ve la cajera ya NO es la completa**:
  solo se dibujan Datos personales, Domicilio y Contacto — Precalificación, Cónyuge, Trabajo e
  ingresos (empleador, ingresos, gastos, cuota de otras deudas) y las Referencias quedan sin
  dibujar en el servidor cuando `REGISTRAR_EDITAR_CLIENTE != TOTAL` (mismo patrón que ya ocultaba
  la pestaña de documentos/buró). Cita del RF de roles y permisos: ver
  [RF38_perfiles.md](../funcionamiento/RF38_perfiles.md) §2.2 (tabla de menú por perfil) y §7
  (decisión D2, detalle completo con archivo/línea).

## C9 — Relación entre el "fondo inicial" de la apertura y la entrega de efectivo del gerente

**Duda levantada ANTES de tocar "Abrir caja"** (verificado en el código y en la documentación
existente, sin ejecutar nada). Resumen del flujo correcto que surge de lo verificado, en pocas
líneas: **la cajera debe escribir en "Fondo inicial" el monto que el gerente YA le entregó en
efectivo, en mano, antes de abrir** (hoy, el fondo sugerido — 3.000.000 Gs., exactamente el
único préstamo listo — alcanza) **y el gerente NO debe registrar, además, una entrega aparte
por ese mismo monto** — "Entregar efectivo" (`ENTREGA_GERENTE`) es solo para refuerzos
posteriores durante el día, no para la apertura.

**Qué guarda "Abrir caja" y de dónde sale el fondo sugerido** (`ServicioCaja.abrirCaja`/
`calcularFondoSugerido`): `INSERT sesiones_caja` con `saldo_inicial` = lo que la cajera escribió
(editable, precargado con el sugerido), `estado_sesion = 'ABIERTA'`; audita `ABRIR_CAJA`
(actor = cajera). **No inserta ningún `movimiento_caja`** — el fondo inicial vive solo como esa
columna. El fondo sugerido (`ServicioFormalizacion.calcularFondoSugeridoDesembolso`) suma
`monto_aprobado` de los préstamos `PENDIENTE_DESEMBOLSO` con checklist completo (confirmado en
el Paso 5 de arriba).

**La entrega de efectivo del gerente a una caja ya abierta, SÍ existe, y es un mecanismo
distinto** (`ServicioCaja.registrarEntregaGerente`): exige perfil gerente/administrador
(rechaza a la cajera, separación de funciones) y una sesión `ABIERTA` de hoy; `INSERT
movimiento_caja` (tipo `ENTREGA_GERENTE`, signo `+1`, `registrado_por` = gerente); audita
`ENTREGAR_EFECTIVO_CAJA` (entidad MOVIMIENTO_CAJA). **Este monto SÍ se suma al saldo teórico**
(`calcularSaldoTeorico` = `saldo_inicial + SUM(movimientos.monto × signo)`).

**¿Contaría el efectivo dos veces si se abre con el fondo sugerido Y además se registra la
entrega del gerente por ese mismo monto?** **Sí** — verificado por la fórmula de arriba: el
`saldo_inicial` y cualquier `ENTREGA_GERENTE` son ADITIVOS, nunca se restan ni se reconcilian
entre sí. Si el gerente entrega 3.000.000 Gs. en mano (reflejados en el "Fondo inicial" al
abrir) y el sistema registra ADEMÁS un `ENTREGA_GERENTE` de 3.000.000 Gs., el saldo teórico
quedaría en 6.000.000 Gs. — el doble del efectivo físico real — y el cierre del día (comparando
contra el efectivo contado) mostraría una diferencia real, no un error del sistema sino una
doble carga de datos.

**Qué dicen los documentos**: `RF_ANTEPROYECTO.md` (RF15: "...abrir la caja del día con un
fondo inicial..."; RF18: "...registrar los movimientos de la caja (entregas de efectivo del
gerente, desembolsos y cobros)...") no dice nada sobre si el fondo inicial y una entrega del
gerente son la misma cosa o dos cosas distintas — ninguno de los dos RF toca ese punto.
`docs/00_especificacion_maestra.md` (PO-06, no es un requisito por sí solo, documento de
trabajo) describe un flujo de 2 pasos: "1. La cajera abre la caja; el sistema propone el fondo
según el total de desembolsos pendientes del día. 2. El gerente entrega el efectivo y confirma
la entrega con su usuario (movimiento ENTREGA_GERENTE). 3. Durante el día, el gerente puede
hacer más entregas..." — leído literalmente, parecería que el paso 2 (la entrega) es SIEMPRE
necesario después del paso 1. **Pero `AVANCE.md` (D-73, "decisión 1 del plan de E6, aprobada
tal cual por Joel") decidió explícitamente lo contrario**: "la cajera ingresa `saldo_inicial`
(el fondo que el gerente ya le entregó en mano) y confirma — un solo paso, sin depender de que
el gerente esté logueado en ese momento" — el paso 2 de PO-06 queda reinterpretado como "entregas
ADICIONALES durante el día" (decisión 2 de la misma entrada), no como un paso obligatorio
después de cada apertura. Esto es exactamente lo que el código implementa (arriba).
`docs/funcionamiento/RF15_apertura_caja.md` (líneas 36-44) ya tiene este mismo hallazgo anotado
con cita de archivo/línea: "la cajera abre con el fondo que el gerente ya le entregó en mano
(`ServicioCaja.java:109`) — pero esa entrega física INICIAL no tiene su propio registro: no hay
un movimiento `ENTREGA_GERENTE` asociado a la apertura, ni un campo que diga quién entregó el
fondo inicial", y su propia tabla de auditoría aclara `ENTREGAR_EFECTIVO_CAJA` como "el gerente
entregó efectivo durante el día (**no es la apertura**)".

**Clasificación pendiente**: el diseño (D-73) es coherente y ya está decidido — no hay una
contradicción real en el sistema, siempre que la cajera y el gerente entiendan que el "Fondo
inicial" YA representa la entrega física inicial. El riesgo real es de uso: nada en pantalla le
avisa al gerente, si entra después, que esa entrega inicial no tiene su propio registro — podría
creer que todavía falta "entregarle" el efectivo y duplicarlo por el sistema. Si eso cuenta como
hallazgo A o B (y si conviene, por ejemplo, un aviso en pantalla o en el ícono ⓘ del fondo
sugerido) queda sin decidir en esta nota.
