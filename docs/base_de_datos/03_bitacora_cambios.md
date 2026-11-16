# Bitácora de cambios de esquema

Un cambio real sobre el esquema de la base = una entrada nueva acá, agregada **después** de
terminar ese cambio (código + base recreada + `mvn test` + prueba en pantalla + commit/tag).
Nunca se borra ni se reescribe una entrada vieja — si algo se revierte, se agrega una entrada nueva
que lo explique, no se borra la anterior. Ver también la regla de uso en `00_LEEME.md`.

Formato de cada entrada:

```
## AAAA-MM-DD — <grupo o nombre del cambio>

- **Qué se hizo**: ...
- **Archivos tocados**: ...
- **Verificación**: (conteos, `mvn test`, prueba en pantalla, etc.)
- **Commit**: `<hash o referencia>`
- **Tag**: `<nombre del tag>` (si corresponde)
```

---

## 2026-11-11 — `caja.gerente_predeterminado`: el texto que ya no era cierto

- **Qué se hizo**: se corrigió la **descripción** del parámetro `caja.gerente_predeterminado`, que
  seguía diciendo *"Gerente que recibe la rendición (si no hay revisión registrada)"*. Desde **C22**
  (03/11/2026) el acta de arqueo nombra al gerente **solo** si quedó registrado al revisar una
  diferencia (`sesiones_caja.id_gerente_revisor`) y, si no hay ninguno, deja la firma en blanco:
  **ningún código lee esa clave** — verificado, el único lugar de `src/main` que la nombra es un
  comentario de `ServicioCaja:597` que explica justamente que *antes* la imprimía. El parámetro
  **no se borra**: se conserva por el dato histórico de quién figuraba en las actas impresas antes
  del cambio. `nombre_parametro` pasó a *"Gerente que figuraba en el «Recibí» de las actas hasta
  C22 (ya no se usa)"* y se le escribió una `observacion` que explica todo eso (se ve en
  `/parametros/historial`).
- **Esto NO es un cambio de esquema**: no se creó, borró ni alteró ninguna tabla, columna,
  restricción ni índice. Es una corrección de **datos de la siembra**, y por eso
  `04_esquema_actual.md` **no cambia** (ese documento describe la estructura, no los valores).
  Queda acá igual porque alguien va a buscar en esta bitácora por qué cambió el texto.
- **Archivos tocados**: `bd/03_parametros.sql` (la fila sembrada + un `UPDATE` al final para la
  `observacion`, porque el `INSERT` de ese archivo no incluye esa columna) y la misma corrección
  aplicada a **`sgcpc_db_pruebas`** con ese mismo SQL, dentro de una transacción.
- **Verificación**: `UPDATE 1` + `UPDATE 1` y `COMMIT`; después, `SELECT` de control — el parámetro
  quedó con el nombre y la observación nuevos, **1 sola fila** para esa clave (no se versionó nada:
  el **valor** sigue siendo *"Ramón Benítez"* y `vigente_desde`/`vigente_hasta` no se tocaron), y
  los **52 parámetros vigentes** siguen siendo 52. `mvn test`: **928/928**.
- **De paso, una guarda que funcionó**: el primer intento falló porque el texto de la observación
  **tiene un `;` adentro** y el script que extraía el `UPDATE` cortaba en el primer punto y coma.
  Como todo iba dentro de `BEGIN`/`COMMIT` con `ON_ERROR_STOP=1`, la transacción se revirtió sola y
  la fila quedó intacta (verificado antes de reintentar).
- **Commit**: pendiente (Joel lo revisa antes).
- **Tag**: —

---

## 2026-10-15 — Normalización de `personas`, grupo Referencias

- **Qué se hizo**: tabla nueva `personas_referencias` (1–N; PK `id_referencia`, FK `id_persona`,
  `UNIQUE (id_persona, numero_referencia)`), reemplaza a `referencia_nombre`/`referencia_telefono`/
  `referencia2_nombre`/`referencia2_telefono` de `personas` (grupo repetido, 1FN — ver
  `02_analisis_y_problemas.md` §1). `personas` pasó de 49 a 45 columnas. Las 2 referencias siguen
  obligatorias en alta y edición (`ServicioClientes.validarReferencias`, mismo criterio de antes,
  ahora sobre la lista en vez de 4 columnas fijas). Sin migración de datos (toda la base es de
  prueba): esquema + código, recrear `sgcpc_db_pruebas` desde cero, cargar datos de ejemplo.
  De paso, corregidos 4 desvíos preexistentes del cargador de datos de demostración (no
  relacionados entre sí, de RF02/RF03/RF04) que impedían correrlo — ver AVANCE.md PA-32 para el
  quinto hallazgo (etapa cobros/caja, separada y pendiente de re-coordinar, no se tocó el sistema).
- **Archivos tocados**: `bd/01_esquema.sql`; `PersonaReferencia.java`/`PersonaReferenciaDAO.java`
  (nuevos); `Persona.java`, `PersonaDAO.java`, `ServicioClientes.java`,
  `ControladorClientesServlet.java`, `clientes/ficha.jsp`; `CargadorDatosPrueba.java`,
  `PruebaD117PromesaPagoParcial.java`, `ServicioClientesTest.java`; `AVANCE.md`,
  `docs/base_de_datos/02_analisis_y_problemas.md`, `docs/base_de_datos/04_esquema_actual.md`.
- **Verificación**: `mvn test` 759/759 verde; cargador corrido de punta a punta sin error (9
  préstamos + 11 personas, 22 filas en `personas_referencias` = 2 por persona, verificado por
  `SELECT` solo lectura); probado en pantalla por Joel (16/10/2026): ver, editar y alta de cliente
  con 2 referencias, OK.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-referencias-probado`.

## 2026-10-16 — Normalización de `personas`, grupo Cónyuge

- **Qué se hizo**: tabla nueva `personas_conyuge` (1–1 opcional; PK `id_persona` también FK, sin
  id propio), reemplaza a `conyuge_nombre`/`conyuge_cedula`/`conyuge_telefono` de `personas`. A
  diferencia de `personas_referencias` (siempre 2 filas, obligatorias), acá la fila solo existe
  si el estado civil lo exige (`ServicioClientes.validarConyuge`, "Casado/a" o "Unión de hecho");
  con otro estado civil la fila no existe (nunca queda vacía) — se descarta cualquier dato
  recibido si no corresponde. `personas` pasó de 45 a 42 columnas. Sin migración de datos (toda
  la base es de prueba): esquema + código, recrear `sgcpc_db_pruebas` desde cero, cargar datos de
  ejemplo (variando 3 de las 11 personas del cargador a "Soltero/a" — antes las 11 eran siempre
  "Casado/a" — para que la carga cubra los dos casos, pedido de Joel).
- **Archivos tocados**: `bd/01_esquema.sql`; `PersonaConyuge.java`/`PersonaConyugeDAO.java`
  (nuevos); `Persona.java`, `PersonaDAO.java`, `ServicioClientes.java`,
  `ControladorClientesServlet.java`, `clientes/ficha.jsp`; `CargadorDatosPrueba.java`,
  `PruebaD117PromesaPagoParcial.java`, `ServicioClientesTest.java`;
  `docs/base_de_datos/04_esquema_actual.md`.
- **Verificación**: `mvn test` 760/760 verde; cargador corrido de punta a punta sin error (8
  personas "Casado/a" con su fila en `personas_conyuge`, 3 "Soltero/a" sin fila, verificado por
  `SELECT` solo lectura); probado en pantalla por Joel, usuario oficial (16/10/2026):
  cliente 5 (Carlos Benítez) Casado→Soltero hizo desaparecer la sección y borró la fila;
  Soltero→Casado la mostró vacía y rechazó guardar vacía ("Faltan datos obligatorios"); cargada
  (María González / 4.123.456 / 0981123456) quedó tras recargar; editado solo el teléfono
  (0981654321) quedó tras recargar; cliente 4 (Luis Aquino, soltero) sin sección; cliente nuevo
  12 (Pedro Rolón, casado) rechazó el alta sin cónyuge y la aceptó con Ana Benítez / 5.555.112 /
  0982111222 — verificado después por `SELECT` con `JOIN` entre `personas` y `personas_conyuge`:
  cliente 5 con exactamente 1 fila (María González/4123456/0981654321, la fila de demostración
  anterior ya no existía), cliente 4 con 0 filas, cliente 12 con 1 fila (Ana Benítez), y en el
  conjunto completo (12 personas tras el alta nueva) los 9 "Casado/a" con exactamente 1 fila cada
  uno, los 3 "Soltero/a" con 0, sin ninguna fila huérfana.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-conyuge-probado`.

## 2026-10-16 — Normalización de `personas`, grupo Baja

- **Qué se hizo**: tabla nueva `personas_baja` (1–1; PK `id_persona` también FK, sin id propio),
  reemplaza a `motivo_baja`/`detalle_baja`/`fecha_baja`/`dado_de_baja_por` de `personas`. A
  diferencia de `personas_conyuge` (DELETE + INSERT si corresponde), esta tabla **nunca se
  borra** — `ServicioClientes.darDeBaja` hace upsert (`PersonaBajaDAO.marcarBaja`, `INSERT ...
  ON CONFLICT (id_persona) DO UPDATE`) y `ServicioClientes.reactivar` no la toca, para conservar
  el motivo/detalle/fecha/usuario de la ÚLTIMA baja como historial — mismo comportamiento que
  tenía `personas.marcarReactivada` antes de este grupo, ya probado por `pruebas/humo_http.sh`
  (ver `02_analisis_y_problemas.md` §5 para el porqué de este ajuste frente al criterio de
  Cónyuge). `personas` pasó de 42 a 38 columnas. Sin migración de datos (0 filas con datos de
  baja en `sgcpc_db_pruebas` antes de este cambio, verificado por `SELECT`).
- **Archivos tocados**: `bd/01_esquema.sql`; `PersonaBaja.java`/`PersonaBajaDAO.java` (nuevos);
  `Persona.java`, `PersonaDAO.java`, `ServicioClientes.java`,
  `ControladorClientesServlet.java`, `clientes/ficha.jsp`; `docs/base_de_datos/02_analisis_y_problemas.md`,
  `docs/base_de_datos/04_esquema_actual.md`; `docs/funcionamiento/RF02_clientes.md` (sección
  nueva "Baja y reactivación"). Sin cambios en `CargadorDatosPrueba.java` ni tests unitarios
  nuevos (pedido explícito de Joel para este grupo). Aparte, en un commit separado (`143521c`,
  sin tag propio): `pruebas/humo_http.sh` actualizado a las validaciones vigentes del alta/
  edición de cliente (`direccionDomicilio`, obligatoria desde el 14/10/2026 y nunca incorporada
  al humo) y al checklist de formalización (3er documento `PLAN_PAGOS_FIRMADO`) — sin este
  ajuste el humo no podía probar nada más allá del primer alta de cliente, incluido este mismo
  grupo Baja.
- **Verificación**: `sgcpc_db_pruebas` recreada desde cero (`bd/recrear_base.sh`); cargador
  corrido sin error (11 personas, 0 con datos de baja, igual que antes de este grupo); `mvn test`
  760/760 verde; humo completo 865/885 OK (20 fallos preexistentes, ninguno de `personas_baja` —
  el tramo de baja/reactivación dio 9/9 OK); probado en pantalla por Joel, usuario gerente
  (16/10/2026): Diego Fernández (id 10) dado de baja (PEDIDO_CLIENTE / "Prueba de baja 1", aviso
  correcto), reactivado (aviso desaparece), dado de baja de nuevo (DATOS_DUPLICADOS / "Prueba de
  baja 2", el aviso muestra los datos nuevos); Miguel Sosa (id 11) rechazó la baja por solicitud
  abierta SOL-2026-0010 — verificado después por `SELECT`: cliente 10 con exactamente 1 fila en
  `personas_baja` (DATOS_DUPLICADOS/"Prueba de baja 2"/fecha de la segunda baja/`estado_persona`
  INACTIVA), cliente 11 con 0 filas (`estado_persona` ACTIVA), sin ninguna fila huérfana.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-baja-probado`.

## 2026-10-16 — Normalización de `personas`, grupo Domicilio

- **Qué se hizo**: tabla nueva `personas_domicilio` (1–1, siempre existe; PK `id_persona` también
  FK, sin id propio; `id_ciudad NOT NULL` con FK a `ciudades`), reemplaza a `id_departamento`/
  `id_ciudad`/`barrio`/`referencia_domicilio`/`tipo_vivienda`/`antiguedad_domicilio_anios`/
  `direccion_domicilio` de `personas`. Además se eliminaron, sin reemplazo: `domicilio` (el texto
  legal armado — dejó de guardarse del todo, ver abajo) y las 2 columnas legado
  `direccion_laboral`/`referencias` (sin dato real que preservar). `personas` pasó de 38 a 28
  columnas. A diferencia de `personas_conyuge` (opcional) y de `personas_baja` (upsert, pero
  nunca se toca al reactivar), `personas_domicilio` usa upsert igual que `personas_baja`
  (`PersonaDomicilioDAO.guardar`, `INSERT ... ON CONFLICT (id_persona) DO UPDATE`) sin esa
  semántica de "nunca se toca" — se actualiza siempre que se edita la ficha.
  - **`id_departamento` sin reemplazo**: el departamento de una persona se obtiene siempre por
    JOIN desde su ciudad (`ciudades.id_departamento`, dependencia transitiva — 3FN, ver
    `02_analisis_y_problemas.md` §1/§3). `ServicioClientes.nombreDepartamentoDesdeCiudad` es la
    única fuente para política de crédito, solicitudes, aprobaciones, informes y el filtro de la
    lista de clientes. El `<select>` de Departamento de la ficha (Precalificación) sigue
    viajando en el `POST` solo para filtrar el `<select>` de Ciudad y para la Precalificación; al
    guardar, se sigue validando que la ciudad pertenezca al departamento enviado (D-126), pero
    ese valor nunca se persiste.
  - **El texto legal del domicilio ya no se guarda**: se arma al vuelo con
    `ServicioClientes.obtenerTextoDomicilio` (JOIN a `ciudades`/`departamentos` +
    `construirTextoDomicilio`, mismo método puro de siempre, sin cambiar su lógica/orden/
    puntuación), cada vez que lo necesita un PDF — único método, usado por `ServicioFormalizacion`
    (contrato, pagaré, plan de pagos, cliente y codeudor), `ServicioCobranza` (factura),
    `ServicioDesembolso` (comprobante de desembolso), `ServicioConsultaCrediticia` (autorización
    del buró) y `ServicioClientes.actualizar` (detalle de auditoría). D-94 (PDF ya emitidos no
    cambian) se preserva porque esa lectura solo ocurre al generar un PDF nuevo: confirmado en
    pantalla con el préstamo CC-2026-00031 — el documento 363 (domicilio viejo) quedó
    `REEMPLAZADO` sin tocarse tras cambiar el domicilio del cliente, y el documento 366
    (re-emitido después del cambio) quedó `VIGENTE` con el domicilio nuevo. Sin migración de
    datos (toda la base es de prueba): esquema + código, recrear `sgcpc_db_pruebas` desde cero,
    cargar datos de ejemplo.
- **Archivos tocados**: `bd/01_esquema.sql`; `PersonaDomicilio.java`/`PersonaDomicilioDAO.java`
  (nuevos); `Persona.java`, `PersonaDAO.java`, `ServicioClientes.java`,
  `ServicioFormalizacion.java`, `ServicioCobranza.java`, `ServicioDesembolso.java`,
  `ServicioConsultaCrediticia.java`, `ServicioSolicitudes.java`, `ServicioInformes.java`,
  `ServicioReportes.java`, `ControladorClientesServlet.java`, `ControladorAprobacionesServlet.java`,
  `clientes/ficha.jsp`, `clientes/_resultados.jsp`; `CargadorDatosPrueba.java`,
  `PruebaD117PromesaPagoParcial.java`, `ServicioClientesTest.java`, `ServicioInformesTest.java`;
  `docs/base_de_datos/04_esquema_actual.md`, `docs/funcionamiento/RF02_clientes.md`. Sin cambios
  en `pruebas/humo_http.sh` (ningún `SELECT` de verificación apunta a las columnas que se
  movieron; los nombres de los campos del formulario HTTP no cambiaron). Los restos de codeudor
  (`CODEUDOR_DOMICILIO`, en `ServicioFormalizacion`) se adaptaron al nuevo método sin agregar
  nada nuevo; su limpieza sigue pendiente para el refactor (los 9 fallos de codeudor del humo, ya
  registrados antes de este grupo).
- **Verificación**: `sgcpc_db_pruebas` recreada desde cero (`bd/recrear_base.sh`); cargador
  corrido sin error (11 personas, 11 filas en `personas_domicilio`, 1 por persona); `mvn test`
  761/761 verde; humo completo 877/886 OK (los 9 fallos son de codeudor, preexistentes a este
  grupo, mismo total que antes); probado en pantalla por Joel, usuarios oficial y gerente
  (16/10/2026): Miguel Sosa (id 11) editado de Central/Ñemby a Asunción/Asunción/Villa
  Morra/Av. Mariscal López 1234, tras F5 el Departamento mostró Asunción (derivado de la
  ciudad); el filtro de la lista de Clientes por Asunción incluyó a Miguel y por Central no; el
  resumen de aprobación de SOL-2026-0010 mostró "Asunción / Asunción" con la zona habilitada
  correcta, aprobada por el gerente; formalizada → CC-2026-00031, contrato (documento 363) con
  "Av. Mariscal López 1234, Villa Morra, Asunción"; domicilio cambiado a Recoleta / Calle España
  500, el documento 363 no cambió (D-94 OK), y el contrato re-emitido (documento 366) mostró
  "Calle España 500, Recoleta, Asunción" — verificado después por `SELECT`: `personas_domicilio`
  de la persona 11 con los datos nuevos; `personas` con 28 columnas; 41 personas y 41 filas en
  `personas_domicilio` (1 por persona, sin huérfanas); documento 363 `REEMPLAZADO` (sigue
  guardado, no se borró) y documento 366 `VIGENTE`.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-domicilio-probado`.

## 2026-10-17 — Normalización de `personas`, grupo Laborales (ÚLTIMO GRUPO — normalización completa)

- **Qué se hizo**: tabla nueva `personas_laborales` (1–1, siempre existe; PK `id_persona` también
  FK, sin id propio, mismo patrón que `personas_domicilio`), reemplaza a `situacion_laboral`/
  `empleador_nombre`/`empleador_ruc`/`empleador_direccion`/`empleador_telefono`/`cargo`/
  `antiguedad_laboral_anios`/`ingreso_neto_comprobado`/`presenta_comprobante_ingreso`/
  `otros_ingresos_mensuales`/`gastos_fijos_mensuales`/`cuota_otras_deudas_mensual` de `personas`
  (12 columnas). Además se eliminó, sin reemplazo, `lugar_trabajo` (legado: solo alimentaba un
  *fallback* de pantalla ya innecesario desde que existe `empleador_direccion`). `personas` pasó
  de 28 a **15 columnas**, cerrando la normalización completa (los 5 grupos planeados ya están
  aplicados: Referencias, Cónyuge, Baja, Domicilio, Laborales).
  - **`situacion_laboral` `NOT NULL`** (CHECK igual que tenía en `personas`); `empleador_*`
    nulables en el esquema (obligatorios en la aplicación solo si la situación laboral es
    `ASALARIADO_SECTOR_PRIVADO` o `FUNCIONARIO_PUBLICO`, `ServicioClientes.validarLaboral`, igual
    que antes); `ingreso_neto_comprobado`/`presenta_comprobante_ingreso`/
    `cuota_otras_deudas_mensual` `NOT NULL` (ya lo eran, o nunca pueden faltar en la fórmula de
    capacidad de pago); `otros_ingresos_mensuales`/`gastos_fijos_mensuales` nulables
    (informativos, no entran en la fórmula — confirmado en la prueba en pantalla).
  - **Único método de lectura para capacidad de pago**: `ServicioClientes.obtenerLaboral`
    (`Connection`, `idPersona`) reemplaza a las lecturas directas de `Persona` en los 4 lugares
    que usaban ingreso/cuota de otras deudas: `ServicioSolicitudes.crear`/`simular`/
    `enviarAAprobacion` y la revalidación RF11 de `ServicioFormalizacion` — mismo criterio que
    `obtenerTextoDomicilio` del grupo Domicilio.
  - **`solicitudes_credito`/`prestamos`: sin cambios.** El snapshot (`ingreso_cliente_evaluado`/
    `ingreso_codeudor_evaluado`/`porcentaje_afectacion`/`limite_afectacion_aplicado`) ya existía
    como columnas propias de la solicitud desde antes de esta normalización y ya se recalculaba
    con el valor actual de la persona en `crear`/`simular`/`enviarAAprobacion`, congelándose
    recién al enviar — mover el dato de origen a `personas_laborales` no cambia ese
    comportamiento (confirmado leyendo el código, ver `02_analisis_y_problemas.md` §6).
  - Sin migración de datos (toda la base es de prueba): esquema + código, recrear
    `sgcpc_db_pruebas` desde cero. **Cambio de criterio de datos de prueba para esta etapa
    (decisión de Joel, ver `AVANCE.md` D-155)**: en vez de correr `CargadorDatosPrueba` (~40
    escenarios automáticos), la base se recreó limpia (solo el seed: usuarios, parámetros,
    catálogos) y los ejemplos para probar en pantalla se cargaron a mano desde las pantallas,
    pocos y controlados (Jesús Acosta, id 1).
- **Archivos tocados**: `bd/01_esquema.sql`; `PersonaLaboral.java`/`PersonaLaboralDAO.java`
  (nuevos); `Persona.java`, `PersonaDAO.java`, `ServicioClientes.java`, `ServicioFormalizacion.java`,
  `ServicioInformes.java`, `ServicioReportes.java`, `ServicioSolicitudes.java`,
  `ControladorClientesServlet.java`, `ControladorAprobacionesServlet.java`,
  `ControladorDocumentosServlet.java`, `ControladorSolicitudesServlet.java`, `clientes/ficha.jsp`,
  `clientes/_resultados.jsp`, `solicitudes/ficha.jsp`, `aprobaciones/ficha.jsp`;
  `CargadorDatosPrueba.java`, `PruebaD117PromesaPagoParcial.java`, `ServicioClientesTest.java`,
  `ServicioInformesTest.java`; `pruebas/humo_http.sh` (2 referencias colgantes a `personas`
  corregidas a `personas_laborales`); `docs/base_de_datos/02_analisis_y_problemas.md`,
  `04_esquema_actual.md`, `docs/funcionamiento/RF02_clientes.md`.
- **Verificación**: `mvn test` 763/763 verde; humo completo 877/886 OK (los 9 fallos son de
  codeudor, preexistentes a este grupo); probado en pantalla por Joel (17/10/2026) con Jesús
  Acosta (id 1): comerciante sin campos de empleador exigidos; cambio a Asalariado exige los 5
  datos del empleador, se guardan y se leen igual tras F5; vuelta a Comerciante los campos
  desaparecen sin exigirlos; SOL-2026-0001 enviada a aprobación con afectación (483.199 + 250.000)
  ÷ 5.700.000 = 12,86 % → mostrada como 12,9 % — verificado después por `SELECT`: `personas` con
  15 columnas; 1 persona y 1 fila en `personas_laborales` (sin huérfanas); Jesús (id 1) con
  `ingreso_neto_comprobado = 5700000`, `cuota_otras_deudas_mensual = 250000`; SOL-2026-0001 con
  `ingreso_cliente_evaluado = 5700000`, `porcentaje_afectacion = 0.1286`. Dos hallazgos de pantalla
  (Expediente "2 de 4" porque `COMPROBANTE_DOMICILIO` nunca se exige para enviar a aprobación, y
  el cargador nunca lo carga) investigados y confirmados **preexistentes** (idénticos desde el tag
  `v1.0-entrega`, el más antiguo del repositorio) — no son del grupo Laborales, no bloquean el
  cierre; registrados en `docs/funcionamiento/RF03_expediente.md §10` y `AVANCE.md` D-155.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-laborales-probado`.

**Normalización de `personas` terminada**: los 5 grupos (Referencias, Cónyuge, Baja, Domicilio,
Laborales) están aplicados y probados en pantalla. `personas` pasó de 49 a 15 columnas.

## 2026-10-17 — Auditoría de toda la base e Integridad (último grupo de la normalización)

- **Qué se hizo**: tras cerrar `personas`, se auditó con la misma vara el resto de la base
  (`docs/base_de_datos/02_analisis_y_problemas.md §7`, §8, §9) y se aplicó el grupo "Integridad"
  (decisión de Joel, con una corrección a la recomendación original: RN-13/PO-13/RN-17 son de
  `docs/00_especificacion_maestra.md`, no del anteproyecto firmado — que cubre el desembolso con
  RF14/RF16/RF17, protección antes de confirmar, no con una reversión posterior):
  - **Eliminadas `cargos_operacion` y `reversiones_desembolso`** (tablas, `CHECK` propios, 6 FK,
    y `reversiones_desembolso` del array de `trg_bloquear_eliminacion`) — confirmado antes con
    `grep` exhaustivo (java/jsp/jrxml/sql/humo/tests) que ninguna tenía código real.
  - **2 `CHECK` nuevos**: `chk_cuotas_total_programado` (`cuotas.total_programado = capital_programado +
    interes_programado + tributos_programados`) y `chk_desembolsos_monto_neto`
    (`desembolsos.monto_neto_entregado = monto_aprobado_snapshot - monto_deducciones`), mismo
    criterio que ya tenían `pagos`/`aplicaciones_pago`. Verificado ANTES en
    `ServicioMotorFinanciero`/`ServicioDesembolso` que la suma es exacta por construcción en los
    únicos lugares que crean estas filas, sin redondeo independiente.
  - **10 índices nuevos** sobre FK sin índice propio: `ix_archivos_binarios_cargado_por`,
    `ix_consentimientos_persona`, `ix_consentimientos_solicitud`, `ix_consultas_persona`,
    `ix_documentos_tipo_documento`, `ix_documentos_reemplazado`, `ix_facturas_pago`,
    `ix_liquidaciones_prestamo`, `ix_parametros_clave` (no `UNIQUE`: `clave_parametro` se repite
    entre versiones históricas del mismo parámetro), `ix_personas_domicilio_ciudad`.
    `solicitudes_credito.id_codeudor` queda sin índice a propósito (va con el refactor de
    codeudor, §9).
  - **No tocado en este grupo** (quedan para el refactor): `tipos_documento.aplica_cliente`/
    `aplica_codeudor`/`requiere_firma`/`origen_esperado` (§7 grupo A), los 2 nombres de FK
    inconsistentes (§7 grupo D), y todo lo de codeudor/garante (§9) — columnas, parámetro
    `credito.exigir_garante`, ~15 archivos Java, controladores, JSP, 5 `.jrxml`, tests, humo
    (incluidos los 9 fallos preexistentes), cargador y documentación.
  - **Corrección de un hallazgo de la auditoría original**: `01_auditoria_inicial_v1.0.md §7.4`
    decía que `reversiones_pago` no tenía `trg_bloquear_eliminacion` — es incorrecto, lo tiene
    desde el commit inicial (`b81973a`); se agregó una nota de corrección sin alterar el texto
    congelado.
  - **Pendiente para el refactor, registrado**: `desembolsos.monto_deducciones` siempre vale 0
    (RN-17) — evaluar si se elimina junto con `chk_desembolsos_monto_neto` en esa etapa.
  - Sin migración de datos (toda la base es de prueba): esquema + recrear `sgcpc_db_pruebas` desde
    cero.
- **Archivos tocados**: `bd/01_esquema.sql`, `bd/02_indices.sql`;
  `docs/base_de_datos/01_auditoria_inicial_v1.0.md` (nota de corrección §7.4),
  `docs/base_de_datos/02_analisis_y_problemas.md` (§7/§8, estado "aplicado"),
  `docs/00_especificacion_maestra.md` (RN-13, RN-17, PO-13, §10.3, marcados fuera de alcance del
  anteproyecto firmado), este archivo, `04_esquema_actual.md`.
- **Verificación**: *(se completa más abajo, tras `mvn test`/humo/prueba en pantalla)*.
- **Commit**: ver tag (mismo commit).
- **Tag**: `norm-integridad-probado`.

## 2026-10-18 — Retiro de codeudor/garante del esquema (Commit 3/3 del refactor)

- **Qué se hizo**: eliminadas `solicitudes_credito.id_codeudor`/`ingreso_codeudor_evaluado`
  (+ su `CHECK` y la FK `fk_solicitudes_codeudor`) y `tipos_documento.aplica_codeudor`;
  eliminado el parámetro `credito.exigir_garante` (fila + comentario D-125 en
  `bd/03_parametros.sql`). `credito.cuotas_alerta_garante` NO se toca — sin relación con
  codeudor, alimenta la cláusula de vencimiento anticipado del contrato y del pagaré
  (`ServicioFormalizacion`, `pagare.jrxml`). Limpiados también 3 comentarios de
  `bd/01_esquema.sql`/`02_indices.sql`/`03_parametros.sql` que mencionaban codeudor/garante de
  paso, sin cambiar ninguna columna. Confirmado antes con `grep` exhaustivo (java/jsp/jrxml/
  humo/cargador) que ningún código leía ya estas columnas ni el parámetro — lo único que
  los mencionaba eran 2 comentarios Java (historial/explicación), reescritos sin nombrarlos.
  Sin migración de datos (toda la base es de prueba, mismo criterio que los 6 grupos de
  normalización): esquema + recrear `sgcpc_db_pruebas` desde cero. `sgcpc_db` no se toca.
- **Archivos tocados**: `bd/01_esquema.sql`, `bd/02_indices.sql`, `bd/03_parametros.sql`;
  `src/main/java/.../dao/ParametroDAO.java`, `src/main/java/.../servicios/ServicioClientes.java`
  (2 comentarios, sin cambios de código); `docs/base_de_datos/04_esquema_actual.md`,
  `docs/base_de_datos/02_analisis_y_problemas.md` (§9, estado "Aplicado"),
  `docs/base_de_datos/01_auditoria_inicial_v1.0.md` (nota), `docs/REGLAS_DESARROLLO.md` §5,
  este archivo, `AVANCE.md`.
- **Verificación**: `sgcpc_db_pruebas` recreada desde cero con el esquema sin codeudor/garante,
  sin error; `mvn test` 760/760 en verde (mismo número que el Commit 1, confirma que el esquema
  no rompió nada); `pruebas/humo_http.sh` completo 947/947 OK, 0 fallas (mismo número que el
  Commit 1); probado en pantalla por Joel: parámetros sin `exigir_garante`
  (`cuotas_alerta_garante` sigue en 2), alta de cliente, expediente completo, autorización y
  consulta al buró, solicitud simulada con todos los controles en verde.
- **Commit**: ver tag (mismo commit).
- **Tag**: `refac-codeudor-probado`.

## 2026-10-18 — Columnas sin uso y renombre de 2 FK fuera de patrón

- **Qué se hizo**: eliminadas `tipos_documento.aplica_cliente`/`requiere_firma`/`origen_esperado`
  (+ el `CHECK` de `origen_esperado`) — se leían de la base pero ningún servicio/controlador/JSP
  llamaba jamás a sus getters, confirmado con `grep`; el diseño original que las necesitaba
  (armar la lista de documentos exigidos por catálogo) nunca se implementó. Renombradas las 2
  únicas FK de 87 con sufijo `_id` en vez de prefijo `id_`: `prestamos.limite_tasa_id` →
  `id_limite_tasa`, `sesiones_caja.gerente_revisa_id` → `id_gerente_revisor` (columna y Java,
  getter/setter incluidos). Eliminada `desembolsos.monto_deducciones` — siempre se escribía en
  `BigDecimal.ZERO` y solo se releía para volver a escribirla (RN-17: sin comisiones ni gastos
  en V1, mismo motivo que `cargos_operacion`, ya eliminada); el `CHECK` que dependía de ella se
  reemplazó por `monto_neto_entregado = monto_aprobado_snapshot`. A diferencia del refactor de
  codeudor/garante, código y esquema fueron en un solo commit (acá hay columnas VIVAS: renombrar
  las FK en el SQL antes de cambiar el esquema rompía formalización y caja; dejar de enviar
  `monto_deducciones` antes de borrar la columna `NOT NULL` rompía el desembolso). Grep de riesgo
  contra `pruebas/humo_http.sh`/`src/test/`/`.jrxml`/`bd/*.sql` antes de aplicar: encontró 2
  `SELECT` directos sobre `gerente_revisa_id` en el humo (Bloque 5/E6), ajustados en el mismo
  commit. Sin migración de datos (toda la base es de prueba, mismo criterio de siempre): esquema
  + recrear `sgcpc_db_pruebas` desde cero. `sgcpc_db` no se toca.
- **Archivos tocados**: `bd/01_esquema.sql`, `bd/03_parametros.sql`; `TipoDocumento.java`,
  `TipoDocumentoDAO.java`, `DocumentoExpedienteDAO.java`, `Desembolso.java`, `DesembolsoDAO.java`,
  `ServicioDesembolso.java`, `PrestamoCredito.java`, `PrestamoDAO.java`,
  `ServicioFormalizacion.java`, `SesionCaja.java`, `SesionCajaDAO.java`, `ServicioCaja.java`,
  `ServicioReportes.java`, `caja/ficha.jsp`, `pruebas/humo_http.sh` (Commit 1); documentación:
  `docs/base_de_datos/04_esquema_actual.md`, `docs/08_modelo_datos.md` (nota, histórico),
  `docs/base_de_datos/01_auditoria_inicial_v1.0.md` (nota, histórico),
  `docs/base_de_datos/02_analisis_y_problemas.md` (§7 grupos A/C/D, estado "Aplicado"),
  `docs/funcionamiento/RF13_firmados_checklist.md` (corrige la mención incorrecta a
  `requiere_firma`), este archivo, `AVANCE.md` (Commit 2).
- **Verificación**: `sgcpc_db_pruebas` recreada desde cero con el esquema nuevo, sin error;
  `mvn test` 760/760 en verde (sin cambio); `pruebas/humo_http.sh` completo 949/949 OK, 0 fallas
  (sin cambio); probado en pantalla por Joel: arqueo con diferencia revisada
  (`id_gerente_revisor`), bandeja de formalización, rechazo por 40 % (SOL-0018) y por falta de
  comprobante (SOL-0019), expediente completo.
- **Commit**: ver tag (Commit 2, documentación; el Commit 1 de código+esquema es `46f55cd`).
- **Tag**: `refac-columnas-sin-uso-probado`.
