# RF30 — Panel con indicadores por perfil

**Estado: probado en pantalla el 13/10/2026 (tag `rf30-probado`).** Panel de cajera, oficial,
gerente y administrador, cada uno con sus tarjetas (implementado desde la etapa E8/Bloque 4, con
ajustes de pulido V4/V5/V5-bis posteriores).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF30 (módulo Informes): *"El sistema debe mostrar al ingresar un
panel con los indicadores del día según el perfil: cobros de hoy, préstamos en mora, promesas de
pago, efectivo en caja, cartera, cobrado en la semana y en el mes, y solicitudes pendientes de
aprobar."*

Doc. 00 §13 (no vinculante si difiere del firmado, CLAUDE.md §2, pero coincide acá) agrega el
mismo detalle en su §7.2: *"El panel muestra: cobros de hoy, vencidos, promesas de hoy,
desembolsos pendientes y total a proveer, efectivo en caja, cartera en la calle y cobrado de la
semana y el mes."*

## 2. Cómo funciona

`GET /panel` (sin restricción de permiso propia — cualquier perfil autenticado entra, el
contenido cambia según el perfil, no el acceso a la ruta) → `ControladorPanelServlet` →
`ServicioPanel.calcularIndicadores` → `dto/IndicadoresPanel` → `panel.jsp`.

La pantalla arma tarjetas en 3 bloques ("Hoy", "Pendientes", "Cartera"), cada una visible según
el perfil (`esGerentePanel`/`esOficialPanel`/`esCajeroPanel`/`esAdministradorPanel`, booleanos
calculados en el propio JSP, líneas 28-37):
- **Cobros de hoy** y **Préstamos en mora**: siempre visibles, para todos los perfiles.
- **Promesas de pago** (de hoy): visible para quien gestiona cobranza.
- **Efectivo en caja**: cajero y gerente.
- **Cartera** (capital en la calle): visible según perfil.
- **Cobrado semana/mes**: visible según perfil.
- **Solicitudes pendientes de aprobar**: gerente (quien aprueba).
- **Desembolsos pendientes y total a proveer**: cajero (quien desembolsa) y gerente. Desde el
  01/11/2026 (**B100**) la tarjeta no se queda en los dos números: cuando entre los préstamos
  pendientes hay alguno al que le falta el checklist, agrega la línea **"falta cargar: Contrato
  firmado, Pagaré firmado"** —los tipos que faltan, sin repetir— y, **solo en ese caso**, el
  enlace *"Ir a Formalización →"* para el perfil que no ve *"Ir a Desembolsos"*. Eso es
  justamente el gerente: `/desembolso` es del cajero y del administrador, así que antes se
  quedaba con *"N pendientes · 0 listos"* sin enlace y sin saber qué faltaba, siendo que los
  firmados **le tocan a él** (la cajera no puede cargarlos, `/documentos` pide
  `CARGAR_DOCUMENTOS_CONSULTA_BURO`). El texto sale del **mismo** checklist que pinta la ficha de
  Formalización (`ServicioFormalizacion.obtenerChecklist`), no de una consulta nueva, y lo arma
  un dueño puro: `ServicioFormalizacion.avisoDeLoQueFaltaParaDesembolsar`. Nombra el **tipo de
  documento** (`getNombreTipo()`) y no la etiqueta del checklist, que ya trae "(firmado por el
  cliente)" y en un aviso quedaría repetida.
- **Solicitudes aprobadas para formalizar** (bloque "Hoy") y **Préstamos con cierre documental
  pendiente** (bloque "Pendientes"): **solo el oficial** — las dos agregadas el 28/10/2026 (B45 y
  B76), reusando `SolicitudDAO.listarAprobadasSinFormalizar` y
  `ServicioFormalizacion.listarFinalizadosConCierrePendiente`. El administrador NO las ve, igual
  que no ve "Mis solicitudes en curso" (decisión de Joel, 28/10/2026).
- **Diferencias de caja por revisar** (bloque "Pendientes"): gerente y administrador (D7), nunca
  la cajera — agregada el 28/10/2026 (B63), reusando
  `ServicioCaja.listarConDiferenciaSinRevisar`. Revisar una diferencia es
  `AUTORIZAR_REVERSION_O_DIFERENCIA`, permiso que la cajera no tiene.
- Cada tarjeta lleva, si corresponde, un enlace "Ir a..." que solo se muestra si el perfil
  **también** tiene esa ruta en `menu.jsp`/`FiltroAutorizacion` (V5 punto 4) — nunca un enlace a
  una pantalla que el servidor igual le bloquearía.

Las tres tarjetas del 28/10/2026 cierran el **"principio de paneles"** de
`recorrido/00_LEEME.md` § Hallazgos generales: *el panel de cada rol muestra su trabajo
pendiente, y cuando alguien termina su parte el siguiente se entera por su panel*. Ninguna
agrega una cuenta nueva — las tres reusan la consulta que ya alimenta la pantalla donde ese
trabajo se resuelve (`/formalizacion` y `/caja`), así que el número del panel y el de la pantalla
no pueden desincronizarse.

## 3. Auditoría

Ninguna — es una pantalla de **solo lectura**, sin ninguna acción que registre.

## 4. Dónde está

- **Servicio**: `ServicioPanel.calcularIndicadores` (único punto que arma `IndicadoresPanel`).
  El aviso de B100 lo calcula `ServicioFormalizacion.obtenerAvisoDeLoQueFaltaParaDesembolsar`
  (lee el checklist de los pendientes y se queda con los incompletos) sobre la función pura
  `avisoDeLoQueFaltaParaDesembolsar` — el dueño del tramo de formalización, no el panel.
- **Controlador**: `ControladorPanelServlet` (`GET /panel`).
- **DTO**: `dto/IndicadoresPanel`.
- **Vista**: `panel.jsp` (lógica de visibilidad por perfil, líneas 24-64).
- **Permisos**: `/panel` no está en `FiltroAutorizacion.RUTAS_PROTEGIDAS` — solo exige sesión
  autenticada (`FiltroAutenticacion`), igual para los 4 perfiles. El contenido, no el acceso, es
  lo que distingue el perfil (según el propio texto del RF: "según el perfil").

## 5. Dependencias

- **RF31/RF32** (informes): comparten varias de las mismas consultas de fondo (cobrado del mes,
  cartera) — ver esos documentos para el detalle de cada cálculo.
- **RF20/RF22** (cobranza): "Cobros de hoy" lee de la misma tabla `pagos` que esos RF, con el
  mismo filtro `estado_pago = 'CONFIRMADO'` (ver §6c).
- **Aprobaciones/Desembolso/Caja**: cada tarjeta correspondiente depende de los RF de esos
  módulos para el dato que muestra.

## 6. Análisis

**a) ¿Cumple la lista exacta del texto firmado?** **Sí, las 7 (cobros de hoy, préstamos en mora,
promesas de pago, efectivo en caja, cartera, cobrado semana/mes, solicitudes pendientes) están
presentes.** El sistema además agrega "desembolsos pendientes y total a proveer" (de Doc. 00
§7.2, no del texto firmado RF30 en sí) — un agregado útil, no contradice nada.

**b) ¿"Según el perfil" se cumple de verdad?** **Sí**, verificado en `panel.jsp`: cada tarjeta
tiene su propia condición booleana por perfil (líneas 47-58), no es un panel genérico idéntico
para todos. Administrador ve lo mismo que gerente (D7, pedido de Joel), decisión de negocio
explícita, no un descuido.

**c) ¿Las cifras excluyen lo revertido/anulado, consistente con el hallazgo de hoy en RF25?**
**Sí, confirmado por código**: toda consulta de `PagoDAO` usada para indicadores de cobro filtra
`estado_pago = 'CONFIRMADO'` (líneas 35, 102, 120, 200, 223, 237 — ninguna cuenta un pago
`REVERTIDO`). La única excepción deliberada es `listarTodosPorRango` (línea 139, comentario
explícito "B4... aunque no sume a los totales"), usada **solo** por el informe de Cobros (RF32,
no este panel) para mostrar el revertido con su marca sin ocultarlo, excluyéndolo igual de la
suma — ver `RF32_informes_operacion.md` §6.

**d) ¿Depende de fechas?** El panel siempre es "de hoy" (`Reloj.hoy()`/`Reloj.ahora()`) — no
tiene filtro de rango (eso es RF31/RF32/RF35, informes aparte). Se puede probar en pantalla
cualquier día, con cualquier dato real existente a esa fecha — no requiere preparar datos con
fechas especiales.

**e) ¿Algo miente o confunde?** No se encontró ningún texto falso. Un matiz a verificar en
pantalla (no es un defecto de código): "Efectivo en caja" depende de que haya una sesión
`ABIERTA` — si no hay ninguna, conviene confirmar que la tarjeta dice algo claro ("sin caja
abierta") en vez de un monto en 0 ambiguo (0 Gs. real vs. "no hay dato").

## 7. Decisiones

Ninguna nueva — el diseño (una tarjeta, una condición por perfil, en el propio JSP sin tocar
`FiltroAutorizacion`) ya está decidido desde E8, con los ajustes de pulido V4/V5/V5-bis (saludo
según hora del servidor, enlaces condicionados, sufijo " Gs." separado visualmente).

## 8. Historial de cambios

**16/11/2026 — tanda 2 (B234, B235), pendiente de la prueba en pantalla.** El panel ganó una fila de **accesos rápidos** con *Cotizador* y *Nuevo cliente* (**B234**), y **quien ve cada uno sale de `ServicioAutorizacion.tieneAccesoTotal`**, el mismo servicio con que `FiltroAutorizacion` bloquea — no de copiar `menu.jsp`, que ofrece `/clientes` a los cuatro perfiles porque la **lista** la ve la cajera: ella tiene `REGISTRAR_EDITAR_CLIENTE` en **CONSULTA**, así que *“Nuevo cliente”* **no** le aparece. Y **toda la tarjeta se puede clickear** (**B235**), estirando con CSS el enlace que cada una ya tiene: el teclado sigue funcionando porque el foco lo recibe el mismo `<a>`, las tarjetas **sin** enlace no se vuelven clickeables, y el recuadro de foco se dibuja sobre la tarjeta. **`panel.jsp` no se tocó para B235**, y los 4 conteos de guardas que lee el proyecto hermano siguen en 2/1/2/4 (su suite: 103/103).

**16/11/2026 — tanda 1 de la revisión de UX (B228-B232), pendiente de la prueba en pantalla de Joel.** El orden de los bloques pasó a **Hoy → Pendientes → Cartera**, que es el que este documento ya declaraba en §2 (**B228**; se movieron los bloques enteros, con sus guardas dentro, y se verificó que ninguna condición cambió — ver abajo). Los **11 contadores** pasan por `FormateadorNumeros.comoCantidad` (**B229**, la lección de B205). Si la base falla, el panel ya no queda en blanco: hay un `else` que dice el estado y ofrece *“Volver a intentar”* (**B230**). El aviso de continuidad lleva **“Ir a Usuarios →”**, sin tocar la frase, porque el proyecto hermano tiene una copia de ese texto (**B231**). Y *“Ir a Cobranza”* dejó de estar en dos tarjetas vecinas: quedó en *“Vencen hoy”*, la que manda a trabajar (**B232**). **Frenado: B233** (la mayúscula de una descripción — el pedazo en minúscula lo exige el test del hermano y ahí está decidido a propósito). **Para después de la defensa: B236** (tres tarjetas que un perfil ve y no puede accionar; cambia las condiciones que copia la app móvil).

**Y el pendiente de §10 quedó HECHO el 16/11/2026**: la prueba con los **4 perfiles**, midiendo en vivo qué tarjetas y qué enlaces ve cada uno — administrador **12 tarjetas / 8 enlaces**, gerente **12 / 7**, oficial **7 / 5**, cajera **5 / 3** (los enlaces, después de B232) — y coincide con lo documentado en §2. De paso quedó confirmado el caso *“sin caja abierta”* (la tarjeta pasa a gris, que es lo que `GUIA_VISUAL.md` declara) y que los avisos de timbrado hoy no salen porque el timbrado vence el 31/08/2027 y la numeración va en 345 de 9.999.999.


| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en la etapa E8/Bloque 4 (P14/P11). | — | — |
| — | V4/V5/V5-bis: saludo por hora del servidor, enlaces condicionados por perfil+ruta real, sufijo " Gs." separado. | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. | *(pendiente)* | — |
| 28/10/2026 | **Tres tarjetas nuevas (fase 2, primer lote): B45 "Solicitud(es) aprobada(s) para formalizar" y B76 "Préstamo(s) con cierre documental pendiente" (oficial), B63 "Diferencia(s) de caja por revisar" (gerente/administrador).** Sin consultas nuevas: reusan `SolicitudDAO.listarAprobadasSinFormalizar`, `ServicioFormalizacion.listarFinalizadosConCierrePendiente` y `ServicioCaja.listarConDiferenciaSinRevisar`. Archivos: `IndicadoresPanel` (3 campos), `ServicioPanel` (3 líneas, sin colaboradores nuevos), `panel.jsp` (3 tarjetas + flag `verEnlaceFormalizacion`), `caja/ficha.jsp` (ancla `#diferencias-arqueo`), `cabecera.jsp` (ese id sumado al `scroll-margin-top` existente). **Probado en pantalla por Joel el mismo día** (ver §9). | *(este commit)* | — |

| 30/10/2026 (paso 15) | **B99**: tarjeta nueva **"Vencen hoy: N (X Gs.)"** en el bloque *Hoy*, con enlace *"Ir a Cobranza"*. El panel mostraba *"Cobros de hoy"* (lo ya cobrado) pero **no lo que falta cobrar**, así que con una cuota venciendo hoy ninguna tarjeta le decía a la cajera su trabajo del día. Sale del **mismo dato que la lista de /cobranza** (`ServicioReportes.listarVencimientos(hoy, hoy)` + `ServicioCobranza.armarListaTrabajo`, que descuenta los préstamos ya contados en *"En mora"*), no de una consulta nueva; `FilaVencimiento` suma un `montoExacto` (BigDecimal) para poder totalizar. `ServicioPanel` pudo tomar el campo `ServicioReportes` **sin ciclo de construcción** (el riesgo que ya documentó `armarListaTrabajo`), confirmado con `mvn test`. **Probado en pantalla el mismo día** (§9). `mvn test` 872/872 | `1f71147` | — |

| 01/11/2026 (tanda 2) | **B100**: la tarjeta de desembolsos nombra lo que falta (*"falta cargar: …"*) y ofrece *"Ir a Formalización →"* al perfil que no ve *"Ir a Desembolsos"* — ver §2. Sin consulta nueva: reusa `obtenerChecklist`, con dueño puro en `ServicioFormalizacion` y un campo nuevo en `IndicadoresPanel`. Archivos: `ServicioPanel` (1 línea), `IndicadoresPanel` (1 campo), `panel.jsp` (línea + enlace condicionado), `ServicioFormalizacion` (3 métodos). **Probado en pantalla por Joel el mismo día** (§9). `mvn test` 899/899 | `74f3b2a` | — |
| 07/11/2026 | **B107** (B86, tramo 3): las tres cifras de cobro del panel dicen que son **lo aplicado a la deuda**, no el efectivo. *"Cobros de hoy"* pasó a *"Cobros de hoy, aplicado a la deuda (… Gs.)"*, y *"Cobrado esta semana"* / *"Cobrado este mes"* a *"… (aplicado a la deuda)"*. Las tres salen de `pagoDAO.sumarAplicadoEntreFechas` (`monto_aplicado`), mientras la caja muestra el efectivo: el 01/11/2026 el panel decía **844.004** y la caja **844.000** —los dos correctos, la diferencia es el redondeo a favor del cliente (D-141/D-142)— y quien los comparaba creía que la caja estaba descuadrada. *"Efectivo en caja"* no se tocó: ya lo decía. **Solo etiquetas: ningún cálculo, ninguna consulta.** La aclaración de por qué pueden diferir no se repite acá; vive en la ficha de caja (ver RF18). Probado en pantalla por Joel el mismo día (§9) | `baf2fdf` | — |

## 9. Pruebas realizadas en pantalla

Ninguna todavía. **Guion aprobado por Joel (13/10/2026), consolidación del Bloque Informes**:

1. **Abrir `/panel`** con los 4 perfiles, uno por uno (gerente, oficial, cajero, administrador).
2. Para cada uno, anotar qué tarjetas aparecen de las 7 del texto firmado (cobros de hoy,
   préstamos en mora, promesas de pago, efectivo en caja, cartera, cobrado semana/mes,
   solicitudes pendientes de aprobar) — contra lo documentado en §2/§6b (qué perfil debería ver
   cada una).
3. No depende de ningún dato especial — siempre hay algo que mostrar (cobros de hoy/mora, al
   menos en 0).
4. Confirmar que administrador ve exactamente lo mismo que gerente (D7, §2).

**Pendiente**: que Joel confirme el resultado antes de taguear `rf30-probado`.

**13/10/2026 (Joel), después de Clean and Build — Tanda 1 de mejoras pendientes:**
- Panel de cajera y de gerente: la tarjeta "Pendientes" ya dice "15 préstamos pendientes de
  desembolso · 0 listos para desembolsar (0 Gs.)" en vez de "15 pendientes · 0 listos..." sin
  decir de qué — confirma el cambio de `panel.jsp` (ver sección 10, resuelto).

### PROBADO EN PANTALLA por Joel el 28/10/2026 — las tres tarjetas nuevas (B45, B63, B76)

Las tres se probaron **con el circuito real completo**, viendo el número subir y bajar, no solo
la tarjeta dibujada. Estado de partida verificado con `SELECT` antes de empezar: **B45 = 0,
B63 = 0, B76 = 2** (la caja del 23/10 con diferencia de −50.000 ya estaba revisada, por eso B63
arrancaba en 0).

1. **B76 — oficial, cierre documental** (se probó sin crear nada, porque ya había 2):
   panel **"2 Préstamos con cierre documental pendiente"** → "Ir al cierre documental" baja a la
   sección con el **título a la vista** (no tapado por la barra superior) y lista
   `CC-2026-00003` y `CC-2026-00001` → se completó el cierre de `CC-2026-00003`:
   **`DEV-2026-000002`**, escaneo firmado cargado, franja **"COMPLETO — 28/10/2026 09:31 por
   Lorenzo Ramírez"** → el panel pasó a **"1 Préstamo con cierre documental pendiente"**, en
   **singular** (concordancia correcta).
2. **B63 — gerente, diferencias de caja**: panel **"0 Diferencias de caja por revisar"** → la
   cajera abrió y cerró caja con un **sobrante de 10.000** (motivo "prueba 28-10-2026",
   sesión 9) → panel **"1 Diferencia de caja por revisar"**, *sin que nadie le avisara por
   fuera del sistema* → "Ir a Caja" llegó a `/caja#diferencias-arqueo` con **"Diferencias de
   arqueo sin revisar (1)" visible** → "Marcar revisada" → "Diferencia revisada" → el panel
   **volvió a 0**.
3. **B45 — oficial, aprobadas para formalizar**: `SOL-2026-0004` (Joel Jesús Acosta, 1.000.000
   a 6 meses) enviada 09:43 → el gerente aprobó 09:45 → panel del oficial: **"1 Solicitud
   aprobada para formalizar"** en el bloque **"Hoy"** y **"Mis solicitudes en curso" en 0** —
   exactamente el hueco que B45 cerraba (antes, "en curso" pasaba a 0 y ahí se terminaba el
   aviso). `SOL-2026-0004` quedó **aprobada sin formalizar a propósito**, como dato de prueba.

**Pruebas automáticas**: `PruebaPanelTarjetasNuevas` (`src/test/java/.../servicios/`, nueva) —
2 pruebas en verde, con la guarda de `sgcpc_db_pruebas` y `rollback`: verifica que cada indicador
valga lo que dice un `SELECT` propio, que siga a la base cuando cambia (B63 sube al aparecer la
diferencia y baja al asignarle revisor, las dos mitades de RN-23) y que cada indicador llegue
**solo** al perfil que corresponde. No corre con `mvn test` (nombre `Prueba*`, fuera del patrón
de Surefire): `mvn -Dtest=PruebaPanelTarjetasNuevas test`. `mvn test` siguió en **800 pruebas,
0 fallas**.

### PROBADO EN PANTALLA por Joel el 01/11/2026 — B100, la tarjeta que nombra lo que falta

Panel de **Ramón (gerente)** con `CC-2026-00006` formalizado y **sin** los 3 firmados: la
tarjeta de desembolsos mostró la línea **"falta cargar…"** y el enlace **"Ir a Formalización
→"** — los dos faltaban antes.

El caso contrario (checklist completo → la línea y el enlace **no** se muestran, porque el
aviso es cadena vacía) está **cubierto por JUnit y no visto en pantalla**: el 03/11/2026, con
los 3 firmados de `CC-2026-00006` ya cargados, se miró `/formalizacion/6` (*"Documentación
completa: listo para desembolso"*) pero **no** el panel.

## 10. Pendientes y trabajo futuro

- **Prueba en pantalla** con los 4 perfiles (gerente, oficial, cajero, administrador),
  confirmando qué tarjetas ve cada uno contra lo documentado en §2/§6b.
- Confirmar visualmente el caso "sin caja abierta" en la tarjeta de efectivo (§6e).
- ~~**(13/10/2026, hallazgo de Joel, RF38)** En el panel de cajera y gerente, la tarjeta de
  "Pendientes" dice "15 pendientes · 0 listos para desembolsar" sin decir **de qué** son
  pendientes~~ — **resuelto y probado en pantalla el 13/10/2026**: la descripción ahora dice
  "préstamo(s) pendiente(s) de desembolso · N listo(s) para desembolsar (Gs. X)".
- **(13/10/2026, hallazgo del análisis de RNF14/rendimiento, Tanda 2 — anotar, no implementar;
  probado que entra bien dentro de los 3 s de todos modos, ver `RNF_revision.md`)**: dos patrones
  N+1 dentro de `ServicioPanel.calcularIndicadores`, ambos disparados en cada `GET /panel`:
  **(1)** `ServicioMora.recalcularMorosidadDiaria` (línea 41) hace, por cada préstamo
  activo/en mora, un `recalcularEstadoPrestamo` + un `SELECT` aparte (`obtenerPorId`) solo para
  comparar el estado antes/después — un viaje extra a la base por préstamo, aunque está
  protegido por `yaSeRegistroHoy` (solo corre completo una vez por día, la primera carga); **(2)**
  `ServicioFormalizacion.calcularChecklistCompletoPorPrestamo` (usada por la tarjeta de
  Desembolsos pendientes) llama `obtenerChecklist` una vez por préstamo `PENDIENTE_DESEMBOLSO`
  en vez de una sola consulta con `IN (...)`; de paso, `ServicioPanel` pide
  `listarPendientesDesembolso` dos veces en la misma carga (línea 62 directo, y de nuevo adentro
  de `listarListosParaDesembolsar`, línea 63). Con el volumen de datos de hoy no hace falta
  tocarlos (medido: 0,74-1,00 s, 3,13 s solo la primera carga del día con caché desactivada,
  bien dentro de los 3 s de RNF14) — quedan anotados para si la cartera de préstamos crece mucho.

## 11. Guion para la defensa

"RF30 exige un panel con indicadores del día, distintos según el perfil. El panel cubre las 7
tarjetas del texto firmado, cada una con su propia condición de visibilidad por perfil — no es
un panel genérico. Las cifras de cobro nunca cuentan un pago revertido (mismo filtro
`estado_pago = 'CONFIRMADO'` que usan los informes de cobranza), consistente con la regla de
negocio reafirmada hoy en RF25."
