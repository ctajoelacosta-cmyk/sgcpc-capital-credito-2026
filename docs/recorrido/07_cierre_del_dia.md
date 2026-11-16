# Escena 07 — Cierre del día

Qué cubre: el cierre de caja con arqueo, la revisión de una diferencia por el gerente (si la
hay), la vista del panel/informes al final del día, y la revisión de la auditoría del día.

**Tramo 6 del recorrido, iniciado en pantalla por Joel el 23/10/2026, rol cajera**, desde el
aviso de "caja de otro día" que ya apareció al intentar desembolsar `CC-2026-00002`
(ver [05_desembolso.md](05_desembolso.md)). Cliente de ejemplo ficticio: **el segundo cliente de
práctica** (sin nombre, por las reglas de esta documentación).

**Dato de la prueba, verificado con `SELECT` en `sgcpc_db_pruebas`**: la sesión N.º 1 se cerró y
la sesión N.º 2 se abrió el **22/10/2026 a las 06:09** (8 segundos entre el cierre de una y la
apertura de la otra) — fue un **cierre y reapertura manual de Joel** para seguir probando el
sistema, no el cierre real de una jornada. La sesión N.º 2, abierta ese mismo momento, es la que
sigue abierta hoy (23/10/2026) y la que dispara el aviso de "caja de otro día".

## Paso 1 — Cierre de caja con arqueo (RF19)

- **Usuario/rol**: Cajera.
- **Pantalla y ruta**: `/caja` — llegó desde el enlace **"Ir a caja"** del aviso de "caja de
  otro día" en la ficha de desembolso (ver [05_desembolso.md](05_desembolso.md)).
- **Qué hace el usuario**: entra a revisar la sesión de caja que sigue abierta desde el día
  anterior, antes de poder operar hoy.
- **Qué responde el sistema**:
  - Aviso amarillo, arriba de todo: **"La caja del 22/10/2026 sigue abierta: ciérrela y rinda
    antes de operar hoy."**
  - Tarjeta **"Sesión abierta"**: **Apertura** (22/10/2026 06:09), **Fondo inicial**
    (3.000.000 Gs.), **Saldo teórico actual** (**0 Gs.**).
  - Tarjeta **"Movimientos"**: **Apertura** (+3.000.000 Gs.); **Desembolso** (−3.000.000 Gs.,
    *"Desembolso de CC-2026-00001."*, comprobante **DES-2026-000001**, con **"Reimprimir"**).
  - Tarjeta **"Cierre y rendición"**: campo **"Efectivo contado"**; campo **"Motivo de la
    diferencia"** (*"obligatorio si el efectivo contado no coincide con el saldo teórico"*);
    en vivo, al escribir el efectivo contado, se compara contra **"Saldo teórico: 0 Gs."**;
    botón **"Cerrar caja"**.
  - Tarjeta **"Sesiones cerradas"**: filtro **"Cierre entre [fecha] y [fecha]"** con
    **"Filtrar"**; una fila (cajera, apertura 21/10/2026 22:04, cierre 22/10/2026 06:09,
    diferencia 0 Gs., **"Ver arqueo"**).
  - **No se muestra la tarjeta de "Apertura de caja"** mientras hay una sesión abierta — esa
    tarjeta (con el formulario para abrir, y el fondo sugerido) solo aparece cuando no hay
    ninguna sesión `ABIERTA`.
- **Regla de negocio**: ver [RF19_cierre_caja.md](../funcionamiento/RF19_cierre_caja.md).
- **Qué sigue**: cargar "Efectivo contado" (y el motivo, si hay diferencia) y tocar "Cerrar
  caja" — queda para una próxima sesión de este recorrido.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

### Verificado en el código y con `SELECT`

- **Dónde está cada dato en la base**: `sesiones_caja` — sesión N.º 1, `CERRADA`, apertura
  21/10/2026 22:04:18, cierre 22/10/2026 06:09:33, `saldo_teorico_cierre`/`saldo_fisico` ambos
  3.000.000, `diferencia_arqueo` 0; sesión N.º 2, **`ABIERTA`**, apertura 22/10/2026 06:09:41,
  `saldo_inicial` 3.000.000, sin cierre todavía. El movimiento de la sesión 2:
  `movimientos_caja` tipo `DESEMBOLSO`, monto 3.000.000, signo −1 — saldo teórico = 3.000.000 +
  (−3.000.000) = 0 Gs., igual a lo mostrado.
- **De dónde sale el aviso amarillo**: `ControladorCajaServlet.mostrarFicha` pone en el
  `request` el booleano `esDeHoy` (`ServicioCaja.esDeHoy(sesion)`) y el texto
  `mensajeCajaOtroDia` (`ServicioCaja.mensajeCajaDeOtroDia(sesion)`); `caja/ficha.jsp` muestra
  el `<div class="alert alert-warning">` solo si `!sesionEsDeHoyFicha` — amarillo (no rojo),
  mismo criterio que Cobranza y Desembolso (bloqueo de tiempo, no un error).
- **Por qué no aparece la tarjeta "Apertura de caja"**: el propio JSP la envuelve en
  `<% if (sesionCajaFicha == null) { %> ... <% } else { %>` — con una sesión ya abierta (de
  hoy o de otro día), cae directo a la rama del `else` (aviso + "Sesión abierta" + el resto),
  nunca muestra el formulario de apertura.
- **Tarjetas que la cajera NO ve en esta pantalla** (visibles solo para el gerente/
  administrador): **"Entregar efectivo a la caja"** y **"Diferencias de arqueo sin revisar"**
  — ambas condicionadas a `esGerenteCaja` en el JSP; coherente con la separación de funciones
  ya documentada en el plan de Caja/Desembolso (solo el gerente entrega efectivo, solo el
  gerente revisa diferencias).

### Registro adicional: el cierre en sí (23/10/2026)

La cajera escribió **"0"** en **"Efectivo contado"** — el campo, en vivo, lo convierte a
**"cero guaraníes"** (en letras, debajo) y muestra en verde **"Saldo teórico: 0 Gs. ·
Coincide"**. Al tocar **"Cerrar caja"**, confirmación **"¿Cerrar caja con 0 Gs. contados?
Coincide con el saldo teórico."**, con **"Sí, cerrar caja"**/**"Cancelar"**. Éxito: **"Caja
cerrada — Saldo teórico 0 Gs., efectivo contado 0 Gs., diferencia 0 Gs. (sin diferencia)."**

La pantalla vuelve a mostrar la tarjeta **"Apertura de caja"** (con el "Fondo sugerido" ya en
**2.000.000 Gs.** — el monto aprobado de `CC-2026-00002`, el único listo para desembolsar
ahora que la caja anterior cerró — y el campo "Fondo inicial" precargado con ese mismo monto).
**"Sesiones cerradas"** ya muestra **2 filas, la más reciente primero**: apertura 22/10/2026
06:09 – cierre 23/10/2026 10:10, diferencia 0; apertura 21/10/2026 22:04 – cierre 22/10/2026
06:09, diferencia 0 — ambas con **"Ver arqueo"**.

**Verificado con `SELECT` en `sgcpc_db_pruebas`**:

- **Campos guardados al cerrar** (`sesiones_caja`, sesión N.º 2): `estado_sesion = 'CERRADA'`;
  `fecha_hora_cierre = 23/10/2026 10:10:34`; `saldo_teorico_cierre = 0`; `saldo_fisico = 0`;
  `diferencia_arqueo = 0`; `motivo_diferencia` vacío (no exigido, sin diferencia);
  `id_gerente_revisor` vacío (no hace falta revisión sin diferencia). **No existe una columna
  "cerrado por"** en `sesiones_caja` — quien cierra queda registrado solo en la auditoría
  (`id_usuario`), nunca en la propia fila de la sesión.
- **Auditoría**: `CERRAR_CAJA` (SESION_CAJA, misma entidad que `ABRIR_CAJA`), con el detalle
  *"Cerró la caja. Teórico 0 Gs., físico 0 Gs., diferencia 0 Gs."* — mismo patrón que la
  apertura (`ABRIR_CAJA`, "Abrió la caja con saldo inicial...").
- **Si el contado NO coincide y el motivo está vacío**: doble validación, cliente y servidor.
  En el navegador (`caja/ficha.jsp`), apenas la diferencia en vivo es distinta de 0, el campo
  "Motivo de la diferencia" pasa a `required` con `minLength = 10` — el popup nativo del
  navegador se reemplaza por el cartel propio del sistema (mismo mecanismo de `pie.jsp`), así
  que no se puede ni enviar el formulario sin escribir, al menos, 10 caracteres reales. En el
  servidor, `ServicioCaja.validarMotivoDiferencia` repite exactamente la misma regla
  (`diferencia.abs() > caja.diferencia_tolerada` y motivo nulo o con menos de 10 caracteres) y
  rechaza con **"Hay una diferencia de [monto] (Sobra/Falta). Explique el motivo con al menos
  10 caracteres."** — tratado como **advertencia**, no como error (ícono y botón "Entendido"),
  y la pantalla devuelve lo que la cajera ya había tecleado (efectivo contado y motivo), para
  no hacerla escribir todo de nuevo.

### Registro adicional: "Ver arqueo" (23/10/2026)

Desde "Sesiones cerradas", **"Ver arqueo"** de la sesión N.º 2 abre el PDF **"ARQUEO Y CIERRE
DE CAJA — Sesión N.º 2"** (1 página): encabezado de la empresa; **Cajera**; **Apertura**
22/10/2026 06:09 / **Cierre** 23/10/2026 10:10; **RESUMEN** (Fondo inicial; (+) Entregas del
gerente (n), Cobros de cuotas (n), Anticipos de capital (n), Cancelaciones totales (n); (−)
Vueltos entregados (n), Desembolsos (n), Compensaciones por reversión (n); SALDO TEÓRICO;
EFECTIVO CONTADO; DIFERENCIA); **COMPOSICIÓN DE LO COBRADO** (capital, intereses
compensatorios, moratorios, punitorios, IVA, "Aplicado a deuda"); **COMPROBANTES EMITIDOS**
(Recibos, Facturas, Constancias, Desembolsos: `DES-2026-000001` (1)); **DETALLE DE
MOVIMIENTOS** (hora, tipo, operación, comprobante, monto: *"06:20 Desembolso CC-2026-00001
DES-2026-000001 −3.000.000 Gs."*; *"10:10 Rendición gerente - - −0 Gs."*); firmas **"Cajera —
Entregué: [cajera]"** y **"Gerente — Recibí: [gerente]"**; pie "Emitido por" y "Página 1 de 1".

**Verificado en el código**:

- **Plantilla**: `arqueo_caja.jasper` (`.jrxml` fuente: `reportes/arqueo_caja.jrxml`), armada
  por `ServicioCaja.generarPdfArqueo` — mismo patrón "generar al primer pedido y guardar" (D-94)
  que el comprobante de desembolso: la primera vez que se pide, genera el PDF y lo guarda en
  `archivos_binarios` (vinculado por `sesiones_caja.id_archivo_cierre`); las siguientes veces,
  reutiliza el mismo archivo ya guardado, sin volver a generarlo.
- **"Rendición gerente"**: es el movimiento `TipoMovimientoCaja.RENDICION_GERENTE`, insertado
  por `ServicioCaja.cerrarCaja` en el mismo momento del cierre — monto = el efectivo contado,
  signo −1, y **`registrado_por` = la propia cajera** (`idUsuarioCajero`), no el gerente, pese
  al nombre del tipo. **No entra en el RESUMEN** (a propósito: `cerrarCaja` ya calculó y guardó
  `saldo_teorico_cierre` antes de insertar este movimiento, así que contarlo ahí restaría dos
  veces lo mismo) — por eso en el "Detalle de movimientos" aparece con operación y comprobante
  en "-" y el monto "−0 Gs." (en esta sesión, con saldo 0) — ver B52.
- **De dónde sale "Recibí: [gerente]" y cómo se asigna**: `generarPdfArqueo` prueba primero
  `sesion.getIdGerenteRevisor()` — si hay un gerente que **revisó una diferencia** de esta
  sesión puntual (ver más abajo), usa su nombre. **Si no hay ninguno** (el caso de hoy: la
  sesión cerró sin diferencia, nunca pasó por esa revisión), usa el parámetro
  `caja.gerente_predeterminado` vigente a la fecha de cierre — un nombre configurado, no vacío,
  pero que **no corresponde a ningún gerente que haya confirmado nada sobre esta sesión en
  particular**. El comentario del propio código lo dice explícito: "la mayoría de las veces no
  hay ningún gerente registrado contra ESTA sesión puntual". Ver C22.
- **¿El gerente tiene algún paso en el sistema para confirmar la recepción del arqueo?**
  **No, para un cierre sin diferencia.** El único mecanismo que vincula a un gerente con una
  sesión cerrada es `ServicioCaja.marcarDiferenciaRevisada` (`id_gerente_revisor`), y solo
  corre cuando hay una **diferencia** que revisar (exige una observación obligatoria, audita
  `REVISAR_DIFERENCIA_CAJA`). Con diferencia 0 — como hoy — no existe ningún paso para que el
  gerente confirme, dentro del sistema, que recibió el efectivo de esta sesión.
- **Auditoría de "Ver arqueo"**: **no se audita, ni la primera generación ni ninguna
  reimpresión** — `generarPdfArqueo`/`obtenerOGenerarPdf` no llaman a `ServicioAuditoria` en
  ningún punto (a diferencia del comprobante de desembolso, que sí audita cada reimpresión,
  `REIMPRIMIR_COMPROBANTE_DESEMBOLSO`, ver `05_desembolso.md` B23).

### Cierre del ciclo: apertura de la caja del día siguiente (23/10/2026)

Con la sesión del 22/10 ya cerrada, la cajera abrió la caja del 23/10 — misma pantalla, mismo
mecanismo, mismo mensaje ("Caja abierta.", B18 se repite). El detalle completo de esa apertura
(fondo sugerido 2.000.000 Gs., sesión abierta a las 10:22) queda documentado en
[00_apertura.md](00_apertura.md) § "Registro adicional: segunda pasada (apertura de la caja
del día)", junto al resto de las aperturas de caja de este recorrido — para no duplicar el
contenido acá.

**Con esto queda recorrido en pantalla, de punta a punta, el ciclo completo**: caja abierta
con actividad de un día → intento de desembolsar al día siguiente bloqueado por la caja
vieja → cierre con arqueo de esa sesión → apertura de la sesión nueva. Entre el cierre
(23/10 10:10) y esta apertura (23/10 10:22) pasaron solo 12 minutos — ambos pasos en la misma
sesión de prueba, no al cabo de una jornada real.

### Registro adicional: cierre con diferencia (23/10/2026, sesión N.º 3)

La sesión N.º 3 (abierta 23/10 10:22, fondo 2.000.000 Gs., con el desembolso y el anticipo de
capital de `CC-2026-00002` ya recorridos en `06_primer_cobro.md`) se cerró con una diferencia a
propósito. En **"Efectivo contado"**: **143.250** — en vivo, debajo, **"cero… / ciento cuarenta
y tres mil doscientos cincuenta guaraníes"** y, en rojo, **"Saldo teórico: 193.250 Gs. ·
Diferencia: Falta 50.000 Gs."**; bajo el campo **"Motivo de la diferencia"**, en rojo, **"0 de 10
caracteres mínimos"**.

Al tocar **"Cerrar caja"** sin motivo: advertencia **"Falta un dato — Falta completar «Motivo de
la diferencia»."** (**"Entendido"**). Al escribir un motivo de 81 caracteres, el contador pasa a
**"✓ 81 caracteres"** (verde). Al tocar **"Cerrar caja"** de nuevo: confirmación **"¿Cerrar caja
con 143.250 Gs. contados? Diferencia: falta 50.000 Gs."** (**"Sí, cerrar caja"**/**"Cancelar"**).
Éxito: **"Caja cerrada — Saldo teórico 193.250 Gs., efectivo contado 143.250 Gs., diferencia
-50.000 Gs. (faltante). Queda pendiente de revisión del gerente."**

La pantalla vuelve a **"Apertura de caja"** (fondo sugerido 0 Gs. — no hay ningún préstamo listo
para desembolsar en este momento) y **"Sesiones cerradas"** ya muestra **3 filas**; la nueva
(23/10/2026 10:22 – 12:50) con **"Falta 50.000 Gs."** en rojo y **"Ver arqueo"**.

**Verificado con `SELECT` en `sgcpc_db_pruebas`** (`sesiones_caja`, sesión N.º 3):
`estado_sesion = 'CERRADA'`; `fecha_hora_apertura = 23/10/2026 10:22:17`; `fecha_hora_cierre =
23/10/2026 12:50:59`; `saldo_inicial = 2.000.000`; `saldo_teorico_cierre = 193.250`;
`saldo_fisico = 143.250`; `diferencia_arqueo = -50.000`; `motivo_diferencia = "Faltante
detectado al contar el efectivo; se informa al gerente para su revisión."`; **`id_gerente_revisor`
sigue `NULL`** — nadie la revisó todavía. **Auditoría** (`auditoria_operaciones`): un único
evento `CERRAR_CAJA` (entidad `SESION_CAJA`, id 3), detalle exacto: *"Cerró la caja. Teórico
193.250 Gs., físico 143.250 Gs., diferencia -50.000 Gs.. Motivo: Faltante detectado al contar el
efectivo; se informa al gerente para su revisión."* (dato menor, no elevado a hallazgo: el
doble punto antes de "Motivo" — "Gs.. Motivo" — porque `comoGuaranies` ya termina en el punto de
la abreviatura "Gs." y el código le agrega otro punto antes de "Motivo:"; mismo patrón de "Gs.."
ya corregido en otro comprobante, `constancia_reversion.jrxml`, sin corregir acá).

**Cómo y dónde se entera el gerente**: no hay ningún aviso en un panel aparte — el gerente se
entera entrando a la **misma pantalla `/caja`** (con `Permiso.ABRIR_CERRAR_CAJA` en nivel
`CONSULTA`): ahí, `ControladorCajaServlet.mostrarFicha` llama a
`ServicioCaja.listarConDiferenciaSinRevisar` **en cada carga de la pantalla**, sin importar si
hay o no una caja abierta, y muestra una tarjeta **"Diferencias de arqueo sin revisar (N)"**
(visible solo para `GERENTE_ADMINISTRADOR`/`ADMINISTRADOR`, nunca para la cajera) con, por cada
sesión: cajera, apertura→cierre, "sobrante"/"faltante" y el monto (mismos colores que el cierre:
rojo faltante, ámbar oscuro sobrante), el motivo que escribió la cajera, y un campo de texto
**obligatorio** ("Qué se hizo con la diferencia") con el botón **"Marcar revisada"**.

**Qué puede hacer el gerente — solo marcar, nunca corregir montos**: `ServicioCaja.
marcarDiferenciaRevisada(idSesionCaja, idUsuarioGerente, observacion)` exige una observación no
vacía ("Ingrese qué se hizo con la diferencia.", si viene vacía) y, si ya tiene
`id_gerente_revisor` (alguien ya la revisó), rechaza con "Esta diferencia ya fue revisada."
(carrera de dos pestañas, tratada como advertencia, no error). **No recibe ningún parámetro de
monto** — no hay forma de corregir `saldo_fisico`/`diferencia_arqueo` desde esta pantalla ni
desde este método; la sesión ya cerrada (`sesiones_caja`) queda fija para siempre en esos
valores. Lo único que cambia al marcar revisada es `id_gerente_revisor` (una sola vez) — la
observación del gerente **no se guarda en una columna propia**: queda únicamente en el `detalle`
de la auditoría `REVISAR_DIFERENCIA_CAJA` (comentario del propio código: "no hay columna para
ese texto en `sesiones_caja` y no se toca el esquema").

**Si afecta algo más — sí, el Informe de Caja (gerente/administrador, RF30)**:
`ServicioReportes.listarInformeCaja` (usado tanto en pantalla como en el PDF/CSV de `/reportes/
caja`) trae **cada sesión cerrada** con su diferencia y, además, una columna explícita
**"Revisada"** (`Sí`/`No`, derivada de `idGerenteRevisor != null` — `informe_caja.jrxml`/
`reportes/caja.jsp`). El total del informe **suma sobrantes y faltantes por separado, nunca
netea uno contra el otro** (pedido explícito de Joel, `calcularTotalesCaja`) — para la
contabilidad, un día con 50.000 Gs. de faltante en una caja y 50.000 Gs. de sobrante en otra NO
se cancela a cero, quedan los dos montos visibles. No se encontró ningún otro lugar (balance,
estado de cuenta, panel del gerente) donde la diferencia de esta sesión vuelva a aparecer.

### Registro adicional: "Ver arqueo" de la sesión con diferencia (23/10/2026, sesión N.º 3)

El PDF de arqueo de la sesión N.º 3 mostró:

- **RESUMEN**: Fondo inicial 2.000.000; (+) Entregas del gerente (0) 0; (+) Cobros de cuotas (0)
  0; **(+) Anticipos de capital (1) 193.250**; (+) Cancelaciones totales (0) 0; (−) Vueltos (0)
  0; (−) Desembolsos (1) 2.000.000; (−) Compensaciones por reversión (0) 0; **SALDO TEÓRICO
  193.250**; **EFECTIVO CONTADO 143.250**; **DIFERENCIA "Falta 50.000 Gs."**; "Motivo de la
  diferencia: [el texto que escribió la cajera]".
- **COMPOSICIÓN DE LO COBRADO**: Capital 193.250; intereses compensatorios, moratorios,
  punitorios e IVA, todos 0; "Aplicado a deuda: 193.250".
- **COMPROBANTES EMITIDOS**: Recibos `REC-2026-000001` (1); Facturas —; Constancias —;
  Desembolsos `DES-2026-000002` (1).
- **DETALLE DE MOVIMIENTOS**: *"10:35 Desembolso CC-2026-00002 DES-2026-000002 −2.000.000"*;
  *"11:30 Anticipo cap. CC-2026-00002 REC-2026-000001 193.250"*; *"12:50 Rendición gerente - -
  −143.250"*.
- Firmas **"Cajera — Entregué: [cajera]"** / **"Gerente — Recibí: [gerente predeterminado]"**.
  Página 1 de 1.

**Confirma lo ya verificado en el código**: el anticipo de capital de `CC-2026-00002` se
clasifica, en este resumen, como **"Anticipo cap."**/"Anticipos de capital (1) 193.250" — el
mismo mecanismo de `generarPdfArqueo` (que distingue por `pagos.tipo`, ver
`06_primer_cobro.md` § Paso 5) ya documentado para el hallazgo **B60**, que es **exclusivamente
de la pantalla `/caja` en vivo** (ahí la columna "Tipo" sí dice genéricamente "Cobro" para
cualquier pago) — el arqueo nunca tuvo ese problema. La fila **"Rendición gerente"** registra,
en el detalle, exactamente el **efectivo contado** que la cajera tecleó (143.250, el mismo
monto que "EFECTIVO CONTADO" del resumen) — es la salida de caja que representa la entrega
física de ese efectivo, no el saldo teórico.

**Verificado en el código**:

- **"Recibí: [gerente]" con una diferencia todavía sin revisar**: `generarPdfArqueo`
  (`ServicioCaja`, líneas ~559-566) decide el nombre de "Recibí" únicamente por
  `sesion.getIdGerenteRevisor() != null` — **no por si hay o no una diferencia pendiente**. Como
  la sesión N.º 3 tiene `id_gerente_revisor = NULL` (nadie la revisó todavía, ver el registro de
  arriba), cae en el mismo `else` que cualquier sesión sin diferencia: imprime el parámetro
  `caja.gerente_predeterminado`. El documento firma, en nombre de un gerente concreto, la
  recepción de un efectivo que **falta 50.000 Gs.** — sin que ese gerente (ni ningún otro) haya
  revisado ni aceptado el faltante dentro del sistema.
- **Sin ninguna marca de "pendiente"/"revisada" en el PDF**: revisado `arqueo_caja.jrxml`
  completo — no existe ningún parámetro ni campo que imprima el estado de revisión de la
  diferencia (ni "pendiente", ni "revisada", ni la fecha/nombre de quien la revisó si la revisó
  después). Contraste ya documentado para el Informe de Caja (§ "Si afecta algo más" más
  arriba): ese informe sí tiene una columna explícita "Revisada: Sí/No" — el PDF de arqueo de
  una sesión puntual, no.

### Registro adicional: el panel del gerente después del cierre con diferencia (23/10/2026, 13:00)

Con la sesión N.º 3 ya cerrada (falta 50.000 Gs., sin revisar), el panel del gerente mostró:

- **Hoy**: Cobros de hoy **1 (193.250 Gs.)**; Promesas de hoy 0; **"Sin caja abierta — Efectivo
  en caja"** (con el enlace **"Ir a Caja"**); Solicitudes pendientes de aprobar 0.
- **Cartera**: Préstamos en mora 0; Cartera en la calle **4.806.750 Gs.** (3.000.000 de
  `CC-2026-00001` + 1.806.750 de `CC-2026-00002`); Cobrado esta semana 193.250; Cobrado este mes
  193.250.
- **Pendientes**: préstamos pendientes de desembolso 0; Reversiones por autorizar 0.

**Ningún indicador, en ningún lugar del panel, menciona la diferencia de 50.000 Gs. que acaba
de quedar pendiente de revisión.**

**Verificado en el código**:

- **`ServicioPanel.calcularIndicadores` no tiene ningún indicador de diferencias de caja
  pendientes**: revisado el método completo — calcula `CantidadCobrosHoy`/`MontoCobrosHoy`,
  `PrestamosEnMora`, `CarteraEnLaCalle`, `CobradoEstaSemana`/`CobradoEsteMes`,
  `SolicitudesPendientesDeAprobar`, `PrestamosPendientesDesembolso`, `ReversionesPorAutorizar`,
  y, para la cajera, `HayCajaAbierta`/`EfectivoEnCaja`/`TotalAProveer` — **nunca llama a
  `ServicioCaja.listarConDiferenciaSinRevisar`**, la misma lista que sí alimenta la tarjeta de
  `/caja` (ver el registro de "cierre con diferencia" más arriba). El dato ya existe y ya se usa
  en otro lado del sistema; simplemente no llega al panel.
- **"Cobros de hoy" sí incluye anticipos (y cualquier tipo de pago)**:
  `PagoDAO.contarConfirmadosEntreFechas`/`.sumarAplicadoEntreFechas` —

  ```sql
  SELECT count(*) FROM pagos WHERE estado_pago = 'CONFIRMADO' AND fecha_hora_pago::date BETWEEN ? AND ?
  ```

  Sin ningún filtro por `pagos.tipo` — cuenta `NORMAL`, `PARCIAL`, `CANCELACION` y `ANTICIPO`
  por igual. Por eso "Cobros de hoy" muestra **1 (193.250 Gs.)**: el único pago de hoy fue,
  precisamente, el anticipo de capital de `CC-2026-00002` (ver `06_primer_cobro.md` § Paso 3) —
  no hubo ningún cobro de cuota vencida en sentido estricto, pero el indicador no distingue.
- **Por dónde llega hoy el gerente a revisar una diferencia**: no hay ninguna pantalla, menú ni
  URL dedicados. El camino es: menú **"Desembolso y caja"** → **"Caja"** (`/caja`, visible para
  `esCajero || esGerente || esAdministrador` en `menu.jsp`) — **la misma pantalla que usa la
  cajera para abrir/cerrar la caja**, no una vista propia del gerente. Una vez ahí, tiene que
  bajar hasta encontrar la tarjeta **"Diferencias de arqueo sin revisar (N)"** (visible solo
  para su perfil) — nada en el panel, en el menú, ni en la propia pantalla de `/caja` más
  arriba de esa tarjeta, avisa que existe o que conviene bajar a buscarla.

## Paso 2 — El gerente ve la diferencia en `/caja` (RF19)

- **Usuario/rol**: Gerente.
- **Pantalla y ruta**: menú **"Desembolso y caja"** → **"Caja"** — la **misma** `/caja` que usa
  la cajera (no hay una pantalla propia del gerente para esto).
- **Qué hace el usuario**: entra a revisar si hay diferencias de arqueo pendientes, después de
  que la cajera avisó (fuera del sistema) que cerró con un faltante.
- **Qué responde el sistema**:
  - Tarjeta **"Apertura de caja"**: **"Fondo sugerido … 0 Gs."** y, debajo, **"No hay ninguna
    caja abierta. Solo la cajera puede abrirla."** (sin formulario — ver B64).
  - Tarjeta exclusiva del gerente, **"Diferencias de arqueo sin revisar (1)"**: *"Cajera
    [nombre] — 23/10/2026 10:22 a 23/10/2026 12:50 — faltante de 50.000 Gs."* (en rojo) *"—
    motivo: [el texto de la cajera]"*, con un campo **"Qué se hizo con la diferencia
    (obligatorio)"** y el botón **"Marcar revisada"**.
  - Tarjeta **"Sesiones cerradas"**: igual que la que ve la cajera (mismas 3 filas, mismo
    **"Ver arqueo"**).
- **Regla de negocio**: ver [RF19_cierre_caja.md](../funcionamiento/RF19_cierre_caja.md).
- **Qué sigue**: escribir la observación y tocar "Marcar revisada" — ver el registro siguiente.
- **Hallazgos**: **B63 (confirmación)**, **B64**, **B65**, **B66** y **C27** — ver la sección de
  Hallazgos al final de esta escena.

### Registro adicional: "Marcar revisada" (23/10/2026, 13:07)

El gerente escribió en **"Qué se hizo con la diferencia"**: *"Se habló con la cajera; el
faltante se descontará de su sueldo según acuerdo firmado."* y tocó **"Marcar revisada"**. **Sin
ningún cuadro de confirmación**, el sistema mostró directamente **"Diferencia revisada"**
(**"Aceptar"**). La tarjeta **"Diferencias de arqueo sin revisar"** desapareció (ya no quedaba
ninguna); en **"Sesiones cerradas"**, la fila de esta sesión pasó a mostrar **"Falta 50.000 Gs.
(revisada)"**.

**Verificado con `SELECT` en `sgcpc_db_pruebas`**:

- **Campos guardados** (`sesiones_caja`, sesión N.º 3): `id_gerente_revisor = 1` (el gerente que
  marcó revisada). **No existe ninguna columna de "fecha de revisión"** en `sesiones_caja` —
  esa fecha/hora solo queda en la auditoría (ver abajo). `diferencia_arqueo`/`motivo_diferencia`
  siguen exactamente iguales a como quedaron al cerrar (`marcarDiferenciaRevisada` nunca los
  toca — ver "Verificado en el código" de más abajo).
- **Auditoría**: un único evento `REVISAR_DIFERENCIA_CAJA` (`SESION_CAJA`, id 3, usuario del
  gerente, 23/10/2026 13:07:23), con el detalle completo: *"Revisó la diferencia de -50.000
  Gs.. Se habló con la cajera; el faltante se descontará de su sueldo según acuerdo firmado."*
  — **el texto que escribió el gerente queda únicamente acá**, nunca en una columna de
  `sesiones_caja` (mismo criterio ya documentado al cerrar la caja: la observación "no tiene
  columna propia... y no se toca el esquema"). Mismo detalle menor de redacción ya visto al
  cerrar ("Gs.." con doble punto antes del texto siguiente).

### Verificado en el código (validación, reversibilidad, arqueo e informe)

- **Validación del texto obligatorio — sin mínimo de caracteres, a diferencia del motivo de
  cierre**: `ServicioCaja.marcarDiferenciaRevisada` solo exige que la observación no esté vacía
  (`observacion == null || observacion.isEmpty()` → *"Ingrese qué se hizo con la diferencia."*)
  — **no hay ningún control de longitud mínima**, ni en el servidor ni en el campo HTML
  (`caja/ficha.jsp`, el `<input>` solo tiene `required`, sin `minlength`). Contraste directo con
  el motivo de la diferencia al **cerrar** la caja, que sí exige 10 caracteres reales en los dos
  lados (`validarMotivoDiferencia`, ver el registro de cierre más arriba) — dos campos del mismo
  flujo (explicar una diferencia de caja), con exigencias distintas.
- **Reversible: no.** `marcarDiferenciaRevisada` rechaza con *"Esta diferencia ya fue
  revisada."* si `id_gerente_revisor` ya está puesto (tratado como advertencia, no error —
  pensado para una carrera de dos pestañas, no para permitir corregir). No existe, en todo
  `ServicioCaja`, ningún método para deshacer una revisión ya marcada.
- **El Informe de Caja sí se actualiza** (`/reportes/caja`, `ServicioReportes.
  listarInformeCaja`): como lee `sesion.getIdGerenteRevisor()` **en vivo**, sin ningún caché,
  la próxima vez que alguien lo genere va a mostrar **"Revisada: Sí"** para esta sesión. Sigue
  sin mostrar la resolución escrita por el gerente — `FilaInformeCaja` no tiene ningún campo
  para ese texto, solo el booleano `revisada`.
- **El PDF de arqueo de ESTA sesión puntual quedó con el nombre viejo, de forma permanente —
  hallazgo propio, verificado con `SELECT`**: `obtenerPdfArqueo` usa el mismo patrón de caché
  que recibo/factura/comprobante (`obtenerOGenerarPdf`, "generar una vez y guardar",
  `sesiones_caja.id_archivo_cierre`) — **sin ninguna excepción que fuerce regenerarlo cuando la
  sesión pasa a revisada** (a diferencia de un pago `REVERTIDO` o una factura `ANULADA`, que sí
  tienen esa excepción). La sesión N.º 3 ya tenía `id_archivo_cierre = 24` desde el "Ver arqueo"
  del registro anterior (antes de esta revisión) — confirmado con `SELECT`. **Consecuencia real
  y verificada**: ese PDF, el único que existe para esta sesión, va a seguir mostrando para
  siempre **"Recibí: [gerente predeterminado]"** (no el gerente real que de hecho revisó la
  diferencia) y **no va a mostrar la resolución en absoluto** (`arqueo_caja.jrxml` no tiene
  ningún parámetro para el estado de revisión ni para su texto, ya verificado para B62). Si en
  cambio nadie hubiera pedido el arqueo antes de la revisión, la primera generación sí habría
  usado el gerente real (la condición `getIdGerenteRevisor() != null` ya lo contempla) — el
  problema de esta sesión en particular es específicamente el orden en que ocurrieron las dos
  cosas (arqueo pedido antes que la revisión), agravado por el hecho de que la caché nunca
  vuelve a mirar si algo cambió después.

### Verificado en el código

- **B63 (confirmación)**: la tarjeta "Diferencias de arqueo sin revisar (N)" existe y funciona
  exactamente en `/caja` (`ServicioCaja.listarConDiferenciaSinRevisar`, mostrada en
  `caja/ficha.jsp` solo para `esGerenteCaja`) — el dato ya está calculado y ya se muestra en
  algún lugar del sistema. Lo que ya se verificó (ver el registro del panel más arriba) es que
  **ese mismo dato no llega al panel del gerente** — el gerente lo ve recién si entra
  específicamente a `/caja`, no antes.
- **B64**: en `caja/ficha.jsp` (línea 59), el párrafo **"Fondo sugerido..."** se imprime ANTES
  del `if (esGerenteCaja)` que decide si mostrar el formulario de apertura o el aviso "Solo la
  cajera puede abrirla" (línea 65) — es decir, el texto y el monto del fondo sugerido se
  calculan y muestran **sin condición de perfil**, aunque el gerente nunca vaya a usarlos (no
  tiene el formulario debajo para aplicarlo). No es un dato incorrecto (la suma de préstamos
  listos para desembolsar es la misma, exista o no quien la use ahora mismo), solo una
  información que, para este perfil, no lleva a ninguna acción.

### Registro adicional: "Ver arqueo" después de "Marcar revisada" (23/10/2026, 13:14)

Con la sesión N.º 3 ya marcada revisada, se volvió a tocar **"Ver arqueo"** de esa misma
sesión. El PDF que se abrió es **exactamente el mismo** que antes de la revisión: **"Emitido
por: [cajera] — 23/10/2026 12:50"**, con **"DIFERENCIA Falta 50.000 Gs."** y el motivo que
escribió la cajera — **sin ningún indicio de que la sesión ya fue revisada**: no aparece la
resolución del gerente, no aparece su nombre como quien revisó, y **"Recibí" sigue mostrando el
gerente predeterminado**, igual que antes de marcarla revisada.

Esto **confirma en pantalla, exactamente, lo que ya se había verificado en el código** en el
registro anterior ("Verificado en el código (validación, reversibilidad, arqueo e informe)"):
el PDF quedó cacheado (`sesiones_caja.id_archivo_cierre = 24`) desde el "Ver arqueo" que se
pidió **antes** de la revisión, y `obtenerPdfArqueo` nunca lo regenera solo porque la sesión
cambió de estado — no hay ninguna excepción para eso, a diferencia de un pago `REVERTIDO` o una
factura `ANULADA`. El documento oficial de esta caja, el único que existe, queda **fijo para
siempre** con el estado de las 12:50, aunque la realidad (ya revisada, a las 13:07) sea otra.

## Hallazgos de esta escena (tramo 6, inicio)

Separados de "Cómo funciona hoy" (arriba, ficha por paso).

- **B50 (menor)** — El aviso de "caja de otro día" (arriba de todo, en amarillo) no ofrece un
  acceso directo al formulario de cierre, que está más abajo en la misma pantalla ("Cierre y
  rendición") — hay que bajar hasta encontrarlo. Sugerir que el propio aviso incluya un botón o
  enlace **"Cerrar esta caja"** que lleve (o haga scroll) directo al formulario de cierre, en
  vez de dejar solo el texto. **Relacionado con B19** (`00_apertura.md`): esa misma tarjeta
  "Cierre y rendición" ya se señaló como mal ubicada (visible desde el primer momento de abrir
  la caja, compitiendo con lo que la cajera necesita hacer primero) — B50 es el mismo problema
  de ubicación, visto ahora desde el otro extremo: cuando SÍ hace falta cerrar con urgencia (una
  caja de otro día bloqueando todo lo demás), el aviso que lo dice no acerca el camino para
  resolverlo.
- **B51 (familia B18)** — El mensaje de éxito **"Caja cerrada. Saldo teórico..., diferencia...
  (sin diferencia)."** no indica qué sigue — a diferencia de otros avisos del sistema que ya
  incorporan el siguiente paso (ej. "Expediente completo. Siguiente paso: registrar el
  resultado del buró.", o B18 de esta misma escena para "Caja abierta."). Sugerir agregar algo
  como **"Siguiente paso: abra la caja de hoy con el efectivo que le entregue el gerente."**
- **Dato positivo (23/10/2026)**: la confirmación de "Cerrar caja" sí resume la operación real
  antes de pedir confirmar — **"¿Cerrar caja con 0 Gs. contados? Coincide con el saldo
  teórico."** (o, si no coincidiera, el monto exacto de la diferencia) — con el monto y si
  coincide o no, no un texto genérico. Es un **modelo a copiar** para **B4** (`04_formalizacion.md`:
  "Generar documentos" no resume qué va a pasar) y **B43** (`03_aprobacion.md`: "¿Aprobar esta
  solicitud?" no muestra monto/plazo/cliente) — dos confirmaciones del sistema que todavía no
  hacen lo que esta sí hace bien.
- **C21** — Una sesión de caja abierta el 22/10/2026 y cerrada el 23/10/2026 a las 10:10 —
  **¿a qué día la atribuyen los informes de caja/cierre del día?** Verificado en el código: el
  único informe de Caja que existe (`ServicioReportes.listarInformeCaja`, reutiliza
  `SesionCajaDAO.listarCerradasEntreFechas`) filtra **por la fecha de `fecha_hora_cierre`**, no
  por la de apertura ni por la fecha de los movimientos reales — `WHERE estado_sesion =
  'CERRADA' AND fecha_hora_cierre::date BETWEEN ? AND ?`. Para este caso puntual: un informe
  filtrado "del 22/10" **no va a mostrar la sesión N.º 2 en absoluto** (cierra el 23/10, no el
  22/10) — aunque esa sesión contenga, de hecho, casi toda la actividad real del 22/10 (su
  apertura y el desembolso de `CC-2026-00001`, las dos cosas ocurridas el 22/10). Un informe
  filtrado "del 23/10", en cambio, **sí** va a traer esa sesión completa — mezclando, bajo la
  fecha 23/10, una sesión que en los hechos fue casi enteramente del día anterior. La fila del
  informe sí muestra la fecha de apertura por separado (no se pierde el dato), pero el FILTRO
  por rango de fechas puede dejar a alguien que pida "el informe del 22/10" sin ver esta sesión
  en absoluto. **Conclusión: riesgo** (sin clasificar A/B) — no es que el informe esté roto
  (los números de cada sesión son correctos, y la fecha de apertura queda visible en la fila),
  sino que el criterio de agrupamiento (por cierre, no por apertura ni por fecha real de cada
  movimiento) puede dejar un día con un informe incompleto y el día siguiente con uno que
  mezcla actividad de dos jornadas distintas — justo el caso de esta prueba, donde el cierre se
  demoró de un día para el otro.
- **B52 (menor)** — En el "Detalle de movimientos" del PDF de arqueo, la fila de **"Rendición
  gerente"** muestra **"−0 Gs."** cuando el saldo contado es 0 — el signo negativo sobre un
  monto que es cero no aporta nada y puede leerse como un error de imprenta. Sugerir mostrar
  simplemente **"0 Gs."** cuando el monto es cero, sin el signo, en esta fila (y en cualquier
  otra con el mismo criterio de `montoConSigno`).
- **B53 (relacionado con C21)** — El "Detalle de movimientos" del arqueo muestra **solo la
  hora** de cada movimiento (`FormateadorFechas.comoDdMmAaaaHhMm(...).substring(11)`, que
  recorta la fecha y deja solo "hh:mm"), nunca el día. En una sesión que abarca más de un día
  calendario (este caso: abierta el 22/10, cerrada el 23/10), el detalle no distingue qué pasó
  cada día — "06:20 Desembolso..." podría ser del 22 o del 23 sin que el PDF lo diga, y hay que
  deducirlo por el contexto (sabiendo que la apertura fue el 22 y el cierre el 23). Sugerir
  agregar la fecha en esta tabla, al menos cuando la sesión abarca más de un día. Mismo tipo de
  ambigüedad que C21 (agrupar por sesión, no por día calendario, puede perder o mezclar el
  detalle de qué ocurrió cuándo).
- **C22 — RESUELTO el 03/11/2026** (decisión de Joel: "el papel dice la verdad"; dueño del
  detalle: `funcionamiento/RF19_cierre_arqueo.md` §2). Hoy el acta nombra al gerente **solo** si
  quedó registrado en esa sesión, y si no deja *"Recibí (firma):"* en blanco; el lado de la
  cajera dice *"Entregado por:"*. Lo que decía el hallazgo:
  El arqueo imprime **"Recibí: [gerente]"** aunque, en este caso, **ningún gerente
  intervino en el cierre** (la sesión cerró sin diferencia, nadie tuvo que revisarla).
  **Verificado**: el nombre que aparece sale del parámetro `caja.gerente_predeterminado`
  (un nombre configurado de antemano, no inventado al vuelo, pero tampoco ligado a ninguna
  acción real de ese gerente sobre esta sesión) — se usa exactamente cuando
  `id_gerente_revisor` es nulo, que es el caso de **toda sesión sin diferencia** (la gran
  mayoría). El único camino real para que un gerente quede vinculado a una sesión es
  `marcarDiferenciaRevisada`, que solo existe para revisar una **diferencia** — no hay, hoy,
  ningún paso en el sistema para que un gerente "confirme la recepción" de un cierre sin
  diferencia. **Conclusión: riesgo** (sin clasificar A/B): el documento firma, en nombre de un
  gerente concreto, la recepción de un efectivo que ese gerente puede no haber contado ni visto
  — no es que el dato esté mal calculado (el parámetro existe y se usa consistentemente), sino
  que el documento presenta como una confirmación real algo que, en la mayoría de los cierres,
  nunca ocurrió dentro del sistema. Cómo se resuelve esto en la operación real en papel (si el
  gerente firma físicamente este PDF al margen del sistema) queda para confirmar con Joel o el
  dueño de Capital Crédito.
  **Agravado (23/10/2026, sesión N.º 3)**: el mismo mecanismo se confirmó ahora con una sesión
  que **sí** tiene una diferencia real (falta 50.000 Gs.) **todavía sin revisar**
  (`id_gerente_revisor = NULL`) — `generarPdfArqueo` no distingue este caso de uno sin
  diferencia: cae igual en el `caja.gerente_predeterminado` e imprime **"Recibí: [ese nombre]"**
  como si ese gerente hubiera recibido el efectivo, aunque **nadie, en el sistema, revisó ni
  aceptó el faltante**. La versión sin diferencia (más arriba) ya era un riesgo de que el
  documento presentara una confirmación que no ocurrió; con una diferencia real y pendiente, el
  mismo texto además **sugiere que el faltante ya fue aceptado por un gerente concreto**, cuando
  en los hechos todavía está sin tocar. Sigue sin clasificar A/B.

**Lo que vi en pantalla el 03/11/2026 08:05-08:18 (tanda 3, C22)** — las dos ramas del pie de
firmas, sobre sesiones ya cerradas cuya acta **nunca se había impreso** (por eso la primera
impresión guardó directamente el formato nuevo y no se pisó ningún documento viejo, D-94):

- **Sesión 14** (01/11 19:40 → 03/11 06:17, sin diferencia, sin revisor): *"Entregado por:
  Sofía Martínez"* / **"Recibí (firma):" sin nombre**. Saldo 844.000, contado 844.000,
  diferencia 0, aplicado 844.004, redondeo −4.
- **Sesión 9** (28/10, sobra 10.000, **ya revisada**): *"Entregado por: Sofía Martínez"* /
  **"Recibí: Ramón Benítez"** — el único caso en que el sistema sí tiene registrado a alguien.
- Las dos quedaron guardadas con el formato nuevo. De acá en adelante se reimprimen así.
- **B61** — La tabla **"Sesiones cerradas"** de `/caja` muestra la diferencia en rojo/ámbar pero
  **no indica, en la fila de una sesión pendiente, que está pendiente de revisión** —
  **verificado en el código** (`caja/ficha.jsp`, línea ~501): la fila **sí** agrega una etiqueta
  gris **"(revisada)"** cuando `idGerenteRevisor != null`, pero cuando es `null` (pendiente,
  como la sesión N.º 3 de hoy) **no agrega ninguna etiqueta** — la única señal de "pendiente" es
  la ausencia de un texto que, si no se sabe que existe para el caso contrario, no se nota. Dato
  que atenúa el hallazgo: la información para distinguirlo **ya existe y ya se usa
  correctamente** en otro lugar — el Informe de Caja (`/reportes/caja`, gerente/administrador)
  tiene una columna explícita **"Revisada: Sí/No"**, sin ambigüedad. Es decir: el problema no es
  que falte el dato (`id_gerente_revisor` existe y el informe ya lo muestra bien), sino que la
  pantalla operativa del día a día (`/caja`, donde el gerente de hecho se entera de las
  diferencias sin revisar) usa un criterio más débil ("etiqueta si sí, nada si no") que el
  informe formal del mismo sistema. Sugerencia: que la fila de "Sesiones cerradas" use el mismo
  criterio explícito que ya funciona en el Informe de Caja.
  **Actualización (23/10/2026, 13:07, tras "Marcar revisada")**: confirmado en pantalla el otro
  extremo del mismo hallazgo — apenas el gerente marcó la diferencia revisada, la fila de
  "Sesiones cerradas" pasó de no decir nada a mostrar **"Falta 50.000 Gs. (revisada)"**. Queda
  así confirmado, de punta a punta, el comportamiento descrito arriba: la etiqueta "(revisada)"
  sí aparece y es correcta cuando corresponde — el hallazgo sigue siendo, únicamente, que
  **antes** de revisar no hay ningún texto equivalente ("pendiente"), solo la ausencia de la
  etiqueta.
- **B62 (mismo patrón que B61, pero en el PDF de arqueo)** — El **PDF de arqueo** de una sesión
  con diferencia no indica en ningún lado si esa diferencia está pendiente de revisión o ya
  revisada — verificado leyendo `arqueo_caja.jrxml` completo: no existe ningún parámetro para
  el estado de revisión, ni "pendiente" ni "revisada". El Informe de Caja (`/reportes/caja`) sí
  lo resuelve con su columna "Revisada: Sí/No" (mismo dato ya citado para B61), pero **ese dato
  no llega al arqueo de la sesión individual** — alguien que solo mira el PDF de arqueo (por
  ejemplo, para archivarlo o entregarlo) no tiene forma de saber, desde ese mismo documento, si
  el faltante/sobrante que muestra ya fue atendido por un gerente o sigue abierto. Sugerencia:
  que `arqueo_caja.jrxml` imprima el mismo "Revisada: Sí/No" (y, si corresponde, quién y
  cuándo) cuando la sesión tiene una diferencia.
- **B63 (prioridad alta — principio de paneles, ver `00_LEEME.md` § Hallazgos generales, y
  `03_aprobacion.md` B45) — Confirmado en pantalla (23/10/2026, 13:00)**: al cerrar la caja con
  una diferencia, el sistema le dice a la cajera **"Queda pendiente de revisión del gerente."**
  — pero el panel del gerente, revisado en pantalla justo después de ese cierre, **no muestra
  ningún aviso de diferencias de caja pendientes** (ver el registro de arriba: Cobros de hoy,
  Promesas, Cartera, Pendientes — ninguna tarjeta menciona la diferencia de 50.000 Gs.).
  **Verificado en el código**: `ServicioPanel.calcularIndicadores` nunca llama a
  `ServicioCaja.listarConDiferenciaSinRevisar` — el dato ya existe y ya alimenta la tarjeta de
  `/caja` (y la columna "Revisada" del Informe de Caja), pero no llega al panel. **Mismo
  principio que B45** (panel del oficial sin aviso de trabajo pendiente en Formalización): un
  indicador que el sistema ya calcula en otro lado no se muestra donde el perfil responsable lo
  vería primero. **Ya estaba anotado como "a revisar" en `03_aprobacion.md`** ("gerente...
  'diferencias de arqueo' (cuando una caja cerró con diferencia, sin revisar todavía)") — este
  registro lo confirma en pantalla. **Agravante propio de este caso**: hoy el único camino para
  que el gerente se entere es entrar por su cuenta a `/caja` (menú "Desembolso y caja" → "Caja",
  la misma pantalla de la cajera) y bajar hasta la tarjeta "Diferencias de arqueo sin revisar" —
  si nadie le avisa por fuera del sistema (de palabra, por chat, etc.), el faltante puede quedar
  sin revisar indefinidamente, sin que nada en el panel, el menú o un correo/notificación lo
  recuerde. **Sugerencia**: agregar al panel del gerente una tarjeta **"Diferencias de caja por
  revisar (N)"** con un enlace directo a `/caja` (o a la tarjeta correspondiente), mismo criterio
  que ya correspondería aplicar para B45.
  **Confirmación adicional (23/10/2026, Paso 2)**: el gerente entró a `/caja` por su cuenta (sin
  ningún aviso del panel) y ahí sí encontró, funcionando correctamente, la tarjeta "Diferencias
  de arqueo sin revisar (1)" con la sesión N.º 3 — confirma que el dato y el mecanismo de
  revisión **existen y funcionan**; lo único que falta es que el panel avise que hace falta ir
  a buscarlos.
- **B64 (menor)** — En la misma pantalla `/caja`, la tarjeta "Apertura de caja" le muestra al
  **gerente** el texto y el monto de **"Fondo sugerido"** (hoy, 0 Gs.) aunque, debajo, el propio
  sistema le aclara que **"Solo la cajera puede abrirla"** — sin ningún formulario para que el
  gerente haga algo con ese número. **Verificado en el código**: `caja/ficha.jsp` imprime el
  párrafo del fondo sugerido (línea 59) **antes** de la condición `esGerenteCaja` que decide si
  mostrar el formulario de apertura o el aviso de "solo la cajera" (línea 65) — el dato no está
  mal calculado, simplemente se muestra sin condición de perfil, a alguien que no lo va a usar.
  Sugerencia: mostrar el fondo sugerido solo cuando hay un formulario para aplicarlo (es decir,
  solo a la cajera).
- **B65** — **"Marcar revisada" no pide ninguna confirmación, aunque es irreversible**
  (verificado: no existe ningún método para deshacerla, ver el registro de "Marcar revisada" más
  arriba). El gerente toca el botón y, sin ningún "¿Está seguro?" de por medio, el sistema ya
  grabó `id_gerente_revisor` para siempre. Contraste con otras confirmaciones del mismo sistema
  que sí resumen la operación real antes de ejecutarla de forma irreversible: el propio cierre
  de caja (**"¿Cerrar caja con [monto] contados? Diferencia: falta/sobra [monto]"**, ya elogiado
  como "modelo a copiar" más arriba en esta misma escena) o la confirmación del anticipo de
  capital (`06_primer_cobro.md` § Paso 3, con el desglose completo). Sugerencia: agregar una
  confirmación del tipo *"¿Marcar como revisada la diferencia de [monto] de la sesión de
  [cajera]? Resolución: [el texto que escribió]."* — mismo patrón ya usado en otros lados del
  sistema, no una idea nueva.
- **B66** — **El mensaje de éxito "Diferencia revisada" no dice qué se revisó ni cuál fue la
  resolución** — ni el monto, ni la sesión, ni el texto que el gerente acaba de escribir. Un
  mensaje genérico, igual para cualquier diferencia de cualquier sesión. Además, **ninguna
  pantalla vuelve a mostrar la resolución del gerente después**: ni la fila de "Sesiones
  cerradas" (solo agrega "(revisada)", sin texto), ni el Informe de Caja (`FilaInformeCaja` solo
  tiene el booleano `revisada`, verificado que no existe ningún campo para la resolución), ni el
  PDF de arqueo (sin ningún parámetro al respecto, B62). El único lugar donde ese texto queda
  guardado, ya verificado, es el `detalle` de la auditoría `REVISAR_DIFERENCIA_CAJA` — fuera del
  alcance de cualquier pantalla operativa normal. Sugerencia: que el mensaje de éxito repita el
  monto y la resolución, y que al menos una pantalla (la ficha de "Sesiones cerradas", o el
  propio arqueo) permita volver a leer esa resolución más adelante.
- **C27 (riesgo, sin clasificar A/B) — confirmado en pantalla (23/10/2026, 13:14)** — **El
  documento oficial del cierre de una sesión (el PDF de arqueo) no refleja la revisión
  posterior del gerente.** Verificado de punta a punta en este mismo tramo: "Ver arqueo" de la
  sesión N.º 3, pedido DESPUÉS de "Marcar revisada", devolvió el **mismo PDF** que antes de la
  revisión — sin la resolución, sin el gerente real, sin ningún indicio de que la diferencia ya
  fue atendida. No es un error aislado de esta sesión: es el mismo mecanismo de caché que ya
  usan recibo/factura/comprobante de desembolso (D-94, "generar la primera vez y guardar"),
  pero sin la excepción que sí tienen un pago `REVERTIDO` o una factura `ANULADA` (que fuerzan
  regenerar el PDF porque su estado cambió de forma importante) — para `sesiones_caja`, pasar a
  "revisada" **no** dispara ninguna regeneración. **Relacionado directamente con C22** (el
  arqueo ya firmaba "Recibí: [gerente predeterminado]" sin que ningún gerente real interviniera,
  incluso con una diferencia sin revisar) y con **B62** (el arqueo tampoco distingue
  "pendiente"/"revisada" en su propio texto) — C27 es la confirmación, en pantalla y con el
  mismo documento real, de que ambos problemas **persisten incluso después de que el gerente sí
  revisó**: el documento queda fijo en el momento del cierre, nunca se actualiza con lo que pasa
  después, aunque el sistema sí lo sepa (`id_gerente_revisor` ya está puesto en la base).
  **Dato adicional, verificado junto con este registro**: la observación del gerente
  (`marcarDiferenciaRevisada`) no exige ningún mínimo de caracteres, a diferencia del motivo que
  exige la cajera al cerrar (10 caracteres); y la revisión, una vez marcada, es **irreversible**
  — no existe ningún camino para deshacerla ni corregirla (ver B65 arriba). Sin clasificar A/B
  — queda para que Joel o el dueño de Capital Crédito decidan si el arqueo de una sesión con
  diferencia debería regenerarse (o reimprimirse distinto) una vez que el gerente la revisa.

## Pasos previstos (sin recorrer todavía)

### Paso 3 — Panel e informes al cierre del día (RF30)

### Paso 4 — Revisión de la auditoría del día por el gerente (RF40)

## Recorrido completo del cierre del día (23/10/2026)

Resumen en orden real de todo el circuito del cierre del día con el **segundo cliente de
práctica**, de punta a punta — cada paso enlaza al detalle ya documentado, con sus hallazgos,
sin repetir el contenido completo acá.

1. **La caja de otro día bloquea el desembolso**: al intentar desembolsar `CC-2026-00002`, la
   ficha de desembolso avisa que la sesión del 22/10 sigue abierta, con el enlace **"Ir a
   caja"** — ver [05_desembolso.md](05_desembolso.md). Es el disparador real que lleva a este
   tramo, no una entrada directa por el menú.
2. **Entrar a `/caja` con la caja vieja todavía abierta**: aviso amarillo, "Sesión abierta" con
   su saldo teórico (0 Gs.), el movimiento del desembolso del 22/10 con "Reimprimir", y el
   formulario de "Cierre y rendición" listo. Paso 1 de esta escena. Hallazgos: B50 (el aviso no
   lleva directo al formulario de cierre).
3. **Cierre cuadrado de la sesión N.º 2**: "0" en Efectivo contado, coincide con el saldo
   teórico, confirmación con el monto real, éxito "sin diferencia". Paso 1, "Registro adicional:
   el cierre en sí". Hallazgos: B51 (el éxito no dice qué sigue); dato positivo (la confirmación
   sí resume la operación real — modelo para B4/B43).
4. **Arqueo de esa sesión (`Ver arqueo`)**: PDF con RESUMEN, COMPOSICIÓN DE LO COBRADO,
   COMPROBANTES EMITIDOS y DETALLE DE MOVIMIENTOS; "Recibí" con el gerente predeterminado
   (ningún gerente intervino, sin diferencia que revisar). Paso 1, "Registro adicional: 'Ver
   arqueo'". Hallazgos: B52 ("−0 Gs." en la rendición), B53 (detalle sin fecha, solo hora), C21
   (el informe de Caja agrupa por fecha de cierre, no de apertura), C22 (arqueo firma "Recibí"
   sin confirmación real del gerente).
5. **Apertura de la sesión N.º 3** (el día de hoy): mismo mecanismo, fondo sugerido
   2.000.000 Gs. — cierra el ciclo caja vieja → caja nueva. Paso 1, "Cierre del ciclo" (detalle
   completo en [00_apertura.md](00_apertura.md)).
6. **Operaciones del día dentro de la sesión N.º 3**: desembolso de `CC-2026-00002`
   (−2.000.000 Gs.) y anticipo de capital (+193.250 Gs.) — ver
   [06_primer_cobro.md](06_primer_cobro.md) § Paso 5 para el detalle completo de este segundo
   movimiento (B60: la columna "Tipo" de `/caja` muestra "Cobro", no "Anticipo").
7. **Cierre con diferencia de la sesión N.º 3**: 143.250 Gs. contados contra 193.250 Gs.
   teóricos — **sin motivo, "Cerrar caja" bloquea** ("Falta completar «Motivo de la
   diferencia»."); **con un motivo de 81 caracteres, cierra**, con el monto exacto de la
   diferencia en la confirmación y el éxito avisando "Queda pendiente de revisión del gerente."
   "Registro adicional: cierre con diferencia (sesión N.º 3)". Hallazgos: B61 (la fila de
   "Sesiones cerradas" solo distingue "pendiente" por la ausencia de la etiqueta "(revisada)").
8. **Arqueo de la sesión con diferencia**: mismo RESUMEN con la diferencia impresa y el motivo
   de la cajera; confirma que el anticipo se clasifica en "Anticipos de capital" (B60 es
   exclusivo de la pantalla `/caja` en vivo); "Recibí" sigue con el gerente predeterminado,
   aunque la diferencia esté sin revisar. "Registro adicional: 'Ver arqueo' de la sesión con
   diferencia". Hallazgos: C22 (agravado — el documento firma una recepción que nadie confirmó,
   con un faltante real de por medio), B62 (el PDF no indica pendiente/revisada).
9. **Panel del gerente, justo después del cierre con diferencia**: ningún indicador menciona la
   diferencia de 50.000 Gs.; "Cobros de hoy (1)" incluye el anticipo sin distinguirlo.
   "Registro adicional: el panel del gerente...". Hallazgo: **B63** (prioridad alta — principio
   de paneles, mismo de B45).
10. **El gerente revisa en `/caja`**: mismo camino que la cajera (menú "Desembolso y caja" →
    "Caja"), sin aviso previo; encuentra la tarjeta "Diferencias de arqueo sin revisar (1)" y la
    tarjeta de apertura con el "Fondo sugerido" aunque no pueda abrir caja. Paso 2. Hallazgos:
    B63 (confirmación de que el mecanismo funciona), B64 (dato irrelevante mostrado sin
    condición de perfil).
11. **"Marcar revisada"**: el gerente escribe la resolución y confirma — **sin ningún cuadro de
    confirmación previo**, aunque es irreversible; éxito genérico "Diferencia revisada."
    "Registro adicional: 'Marcar revisada'". Hallazgos: B65 (sin confirmación), B66 (el éxito y
    ninguna pantalla posterior repiten la resolución).
12. **La lista pasa a "(revisada)"**: "Sesiones cerradas" muestra ahora "Falta 50.000 Gs.
    (revisada)" — confirma en pantalla el otro extremo de B61.
13. **El arqueo queda congelado**: "Ver arqueo" de la misma sesión, pedido después de la
    revisión, devuelve el **mismo PDF** de antes — sin la resolución, sin el gerente real.
    "Registro adicional: 'Ver arqueo' después de 'Marcar revisada'". Hallazgo: **C27** (riesgo,
    relacionado con C22 y B62 — el documento oficial no refleja la revisión posterior).

**Qué sigue**: con la sesión N.º 3 cerrada, revisada y su arqueo archivado, el ciclo de caja de
este segundo cliente de práctica queda cerrado. Faltan, de este mismo tramo, el panel/informes
formales al cierre del día (RF30, más allá de lo ya visto en el punto 9) y la revisión de la
auditoría del día por el gerente (RF40) — ver "Pasos previstos" arriba.

## Completitud del tramo 6

Control de completitud contra el código (solo lectura, verificado hoy 23/10/2026): todos los
caminos que existen en Caja (cierre, arqueo y revisión de diferencias), marcando cuáles se
recorrieron en pantalla y cuáles quedan pendientes. Detalle de cada camino ya recorrido en los
Pasos de arriba.

| Camino | Existe en el código | ¿Se recorrió en pantalla? |
|---|---|---|
| Cierre cuadrado (efectivo contado = saldo teórico) | `ServicioCaja.cerrarCaja`, diferencia = 0, sin exigir motivo | **Sí** — sesión N.º 2, Paso 1 |
| Cierre con faltante | `cerrarCaja` + `validarMotivoDiferencia` (diferencia < 0) | **Sí** — sesión N.º 3, "Registro adicional: cierre con diferencia" |
| Cierre con sobrante | Mismo mecanismo, diferencia > 0 (texto "Sobra [monto]", color ámbar) | **No recorrido** — solo se probó el caso de faltante |
| Motivo de la diferencia más corto que el mínimo (10 caracteres) | Validación doble, cliente (`minLength`) y servidor (`validarMotivoDiferencia`) | **Sí** — intento sin motivo bloqueado con "Falta completar «Motivo de la diferencia»."; no se probó puntualmente un motivo de, por ejemplo, 5 caracteres (solo "sin motivo" y "81 caracteres") |
| Entrega de efectivo del gerente a una caja ya abierta | `ServicioCaja.registrarEntregaGerente`, exige perfil gerente/administrador | **No recorrido** — ninguna entrega adicional se registró en este ciclo (el fondo inicial cubrió todo lo necesario) |
| Cerrar una caja que ya cerró otro usuario (carrera de dos pestañas) | `marcarCerrada` con `UPDATE` condicionado a `ABIERTA`; si ya cerró, `CajaYaCerradaException` con quién y cuándo | **No recorrido** — no se simuló una carrera real |
| Caja de otro día bloqueando desembolso/cobro | `ServicioCaja.esDeHoy`/`validarSesionDeHoy`, aviso amarillo + bloqueo | **Sí** — disparador de todo este tramo (punto 1 del "Recorrido completo") |
| Informe de Caja por rango de fechas (`/reportes/caja`) | `ServicioReportes.listarInformeCaja`/`calcularTotalesCaja`, columna "Revisada: Sí/No" | **No recorrido en pantalla** — verificado solo en el código (B61/B63, "Verificado en el código") |
| Reimpresión del arqueo | `obtenerPdfArqueo`/`obtenerOGenerarPdf`, mismo PDF cacheado siempre | **Sí** — "Ver arqueo" pedido dos veces para la sesión N.º 3 (antes y después de la revisión), confirmando el mismo PDF las dos veces (C27) |
| Revisión de una diferencia por el gerente | `ServicioCaja.marcarDiferenciaRevisada`, observación obligatoria (sin mínimo de caracteres), `id_gerente_revisor` | **Sí** — Paso 2, "Registro adicional: 'Marcar revisada'" |
| Intentar revisar una diferencia ya revisada (carrera de dos pestañas) | `DiferenciaYaRevisadaException` ("Esta diferencia ya fue revisada."), advertencia no error | **No recorrido** — no se simuló una carrera real |
| Acceso por URL directa de un rol sin permiso (ej. oficial a `/caja`, o cajera a `/caja/revisar-diferencia`) | `FiltroAutorizacion`: `/caja` exige `ABRIR_CERRAR_CAJA` (NINGUNO para oficial → 403); `/caja/revisar-diferencia` exige `AUTORIZAR_REVERSION_O_DIFERENCIA` (NINGUNO para cajera → 403) | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Caja) |
