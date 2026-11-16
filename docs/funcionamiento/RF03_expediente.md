# RF03 — Expediente de documentos según la situación laboral

**Estado: REPROBADO FORMALMENTE el 14/10/2026 por Joel (perfil oficial, `sgcpc_db_pruebas`) —
tag `rf03-reprobado`. Antes: probado en pantalla desde el 09/10/2026 — tag `rf03-probado`
(ajustes visuales finales) más `rf03-agujero-cerrado` y `rf03-situacion-laboral` (2
correcciones de seguridad/integridad del 09/10/2026).**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF03 (módulo Clientes y solicitudes):

> El sistema debe permitir cargar el expediente de documentos del cliente según su situación
> laboral, e indicar qué documentos faltan y si la cédula está vencida.

## 2. Cómo funciona

**Orden de las filas** (B90, decisión de Joel del 29/10/2026): **1. Autorización de consulta al buró, 2. Cédula de identidad, 3. Comprobante de ingreso, 4. Comprobante de domicilio** — el buró es el filtro que ahorra tiempo, así que se pide primero: si el cliente no pasa, no tiene sentido juntar el resto de los papeles. El orden vive en una sola línea (`ServicioClientes.CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN`); el número de cada fila sale de su posición y el contador "N de 4" de `CODIGOS_EXPEDIENTE_OBLIGATORIOS`, que no tiene orden. **Ninguna lógica resuelve por posición**: todo por código de tipo.

**Flujo guiado** (B88, pedido de Joel del 28/10/2026): después de cada acción, el sistema lleva al **primer paso pendiente** y dice exactamente qué falta. La regla tiene un solo dueño, `ServicioClientes.calcularSiguientePaso` — el orden, la excepción de "cargando papeles no te saco del Expediente" y los textos están ahí, no en cada pantalla. Nunca cambia qué se PERMITE hacer (la consulta al buró no se bloquea por el expediente): solo a dónde lleva y qué dice.

Pestaña "Documentos" de la ficha del cliente (`/clientes/{id}`, misma ruta/permiso que RF02 —
`REGISTRAR_EDITAR_CLIENTE`): una fila por tipo de documento del contexto PERSONA
(`tipos_documento`, catálogo de solo lectura), cada una con su etiqueta, badge ✓/"Falta" y el
botón de carga correspondiente — mismo mecanismo de validación de archivo que RF13 (extensión
declarada + firma real de los primeros bytes + tamaño máximo, `ServicioDocumentos.validarArchivo`,
sin duplicar nada entre los dos RF).

**Según la situación laboral**: qué documento se SUGIERE para "Comprobante de ingreso" cambia
según la situación laboral elegida en RF02 — comerciante (declaraciones de IVA/facturas +
patente), empleado/a doméstico/a (extracto de aportes IPS), jubilado/pensionado (recibo de
haberes), asalariado/funcionario (comprobante de ingreso estándar). El tipo de documento en sí
sigue siendo el mismo (`COMPROBANTE_INGRESO`); lo que cambia es la descripción sugerida en
pantalla, no una regla de bloqueo distinta por situación.

**Cédula vencida** (RF03 Parte 1): la fecha de vencimiento de la cédula se valida contra hoy —
en el ALTA, una cédula vencida rechaza (mismo criterio que la edad); en la EDICIÓN, solo un
aviso amarillo (nunca bloquea, igual que RF02). La fila de la cédula en el Expediente queda en
rojo/"Falta" si está vencida, y "Enviar a aprobación" (RF06) queda bloqueado mientras siga así.
**Si se edita la fecha de vencimiento**, el escaneo ya cargado queda automáticamente
`REEMPLAZADO` (vuelve a "Falta: cargar la cédula renovada") — mismo patrón de versionado de
siempre, en la misma transacción que guardar los datos, sin tocar el esquema.

**El mismo archivo cargado dos veces** (B42, 01/11/2026) — `archivos_binarios.hash_sha256` ya se
calculaba y se guardaba para cada archivo, pero nadie lo miraba. Ahora, al cargar, se consultan
los documentos **VIGENTES de ese mismo expediente** que tienen el mismo SHA-256
(`DocumentoExpedienteDAO.listarVigentesConMismoHash`) y se separan **dos casos distintos**:

- **Mismo tipo + mismo contenido → se rechaza**, antes de insertar nada: no queda ni el archivo
  ni una versión nueva. *"Ese archivo ya está cargado. Es idéntico al que figura como
  «Comprobante de domicilio»: no hace falta volver a subirlo."* Una "versión 2" byte a byte
  idéntica a la 1 no aporta nada y ensucia el historial de versiones. Al **reemplazar**, el
  documento anterior de ese tipo todavía está `VIGENTE` cuando se hace el chequeo (pasa a
  `REEMPLAZADO` recién después de insertar el nuevo), así que la consulta lo encuentra.
- **Otro tipo + mismo contenido → se guarda y solo se avisa** (amarillo): *"Este archivo ya está
  cargado como «Cédula de identidad». Si es el mismo papel, está bien; si se equivocó de
  archivo, vuelva a cargarlo."* Es legítimo: una sola hoja escaneada puede servir para dos tipos
  (cédula y comprobante de domicilio en la misma imagen), así que bloquearlo rompería el
  circuito. Se calcula **después** de la carga y excluye el tipo recién cargado.

**La búsqueda es acotada a esa persona —o a ese préstamo, en el camino de RF13— y nunca global**:
un hash igual en el expediente de otro cliente no es asunto de esta carga, y nombrarlo en un
aviso de pantalla filtraría datos de un tercero. Eso se aparta a propósito de la sugerencia
original del recorrido (*"...ya fue cargado como [tipo] de [cliente]"*).

**Los dos textos los decide un dueño propio** (`ServicioDocumentos.mensajeDeArchivoYaCargado` y
`textoDelAvisoDeContenidoRepetido`, puro), y van en **dos oraciones** a propósito: el cartel
parte el mensaje en título y detalle por el primer ". " seguido de mayúscula (`pie.jsp`,
`dividirEnTituloYDetalle`) y al título le saca el punto final. Un mensaje de una sola oración se
muestra entero como título y **pierde su punto** — es lo que se vio en pantalla el 01/11/2026.

**Autorización al buró** (fila 1 desde el 29/10/2026 — antes fila 4, ver B90; comparte protagonismo con RF04): "Generar autorización"
(número único `AUT-aaaa-NNNNN`) y "Cargar autorización firmada" en un solo paso, con vigencia
visible ("quedan N días" / "vencida" en rojo) — ver el detalle completo en `RF04_buro.md`
(ya documentado y probado, tag `rf04-probado`), no se repite acá.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `CARGAR_DOCUMENTO` / `NUEVA_VERSION_DOCUMENTO` | DOCUMENTO_EXPEDIENTE | Se cargó o reemplazó un documento del expediente |
| (dentro del evento de edición de PERSONA) | PERSONA | El cambio de "vencimiento de cédula" o "situación laboral" queda en `camposModificados`, el mismo mecanismo de auditoría de cualquier edición |

## 4. Dónde está

- `servicios/ServicioDocumentos.java` — `cargarDocumento`, `validarArchivo`,
  `detectarExtensionPorContenido` (compartidos con RF13, sin duplicar) y, desde el 01/11/2026,
  el control del archivo repetido (B42): `rechazarSiYaEstaCargadoIgual`,
  `avisoDeContenidoRepetido` y las tres funciones puras `nombreDelTipoRepetido`,
  `otrosTiposConElMismoContenido`, `textoDelAvisoDeContenidoRepetido` + el dueño del texto
  `mensajeDeArchivoYaCargado`.
- `dao/DocumentoExpedienteDAO.java` — `listarVigentesConMismoHash` (JOIN con
  `archivos_binarios` y `tipos_documento`; acotada por persona o por préstamo). **Ojo**: los
  `obtenerVigentePorPersonaYTipo`/`...PorPrestamoYTipo` **no** hacen ese JOIN, así que su
  `getArchivoBinario()` viene en null y no sirven para comparar hashes — fue la causa de que la
  primera versión de B42 no bloqueara nada (§9).
- `servicios/ServicioClientes.java` — `agregarFaltantesExpediente`, `cedulaVencida`,
  `descripcionComprobanteIngresoSegunSituacionLaboral`, `itemsFaltantesExpediente`.
- `controladores/ControladorDocumentosServlet.java` (`POST /documentos/cargar`,
  `GET /documentos/{id}`).
- `webapp/WEB-INF/vistas/clientes/ficha.jsp` — pestaña "Documentos".
- Tablas `tipos_documento` (catálogo, contexto PERSONA), `documentos_expediente`,
  `archivos_binarios`.
- `bd/migraciones/E10_09_recalcular_domicilio.sql` — no es de este RF puntualmente (toca el
  domicilio, RF02), pero se aplicó en la misma tanda de trabajo de RF03 Parte 3.

## 5. Dependencias

- **Depende de**: RF02 (la situación laboral elegida ahí determina la descripción sugerida del
  comprobante de ingreso).
- **Lo afecta un cambio en**: `ServicioDocumentos.validarArchivo` (compartido con RF13 — un
  cambio ahí afecta a los dos).
- **A quién afecta un cambio acá**: RF04 (la autorización al buró es, estructuralmente, una fila
  más de este mismo expediente); RF06 (bloquea "Enviar a aprobación" con cédula vencida o
  expediente incompleto).

## 6. Análisis

### Cumplimiento contra el texto firmado

El texto se cumple **sin excepción**: se carga el expediente, la lista de documentos sugeridos
depende de la situación laboral, se indica claramente qué falta (badge rojo/"Falta" por fila,
nunca una lista separada que pueda desincronizarse) y si la cédula está vencida (fila propia,
en rojo, con el bloqueo de "Enviar a aprobación" si corresponde).

### Hallazgos de seguridad ya cerrados (09/10/2026)

1. **Agujero del PDF falso** (`rf03-agujero-cerrado`): la sección "Otros documentos" del
   expediente permitía subir CUALQUIER PDF como si fuera la autorización de buró firmada o el
   resultado de la consulta — se quitó esa sección genérica para esos 2 tipos reservados, cada
   uno pasó a tener su propia ruta dedicada (`cargar-autorizacion-firmada`/`cargar-informe-buro`)
   que la ruta genérica rechaza explícitamente. De paso: la fila de autorización solo cuenta
   como cumplida si, además del documento, hay un consentimiento registrado (antes alcanzaba
   con subir cualquier PDF por la genérica); `/documentos/{id}` pasó a servir "inline" en vez de
   forzar la descarga.
2. **Cambio de situación laboral invalidaba el comprobante sin que nadie se enterara**
   (`rf03-situacion-laboral`): mismo agujero que el de la cédula (punto anterior), ahora con la
   situación laboral — al editar y cambiar la situación laboral, el `COMPROBANTE_INGRESO`
   vigente pasa a `REEMPLAZADO` automáticamente (ya no es evidencia válida para la situación
   nueva), la fila vuelve a "Falta", y bloquea "Enviar a aprobación".

### Corrección real de datos encontrada en el camino (no es de RF03 puntualmente, pero se
aplicó en la misma tanda)

`ServicioClientes.armarDomicilioDesdePartes` nunca usaba la calle/número (`direccion_domicilio`)
para armar el domicilio compuesto — salía "Sajonia, Asunción, Asunción" (departamento repetido,
sin calle). Corregido con `construirTextoDomicilio` (método puro nuevo, con pruebas) y una
migración de datos (`E10_09_recalcular_domicilio.sql`, aplicada solo a `sgcpc_db_pruebas`) para
recalcular los registros existentes — afecta a todos los PDF que usan ese mismo campo (ver
`RF12_documentos_pdf.md`/`RF17_comprobante_desembolso.md` para el hallazgo posterior sobre la
referencia coloquial en documentos legales, mismo campo, análisis distinto).

## 7. Decisiones

- **(21/10/2026, Joel, durante el recorrido operativo — `docs/recorrido/01_llega_el_cliente.md`)
  El comprobante de domicilio pasa a ser OBLIGATORIO. IMPLEMENTADO el 28/10/2026.** Reemplaza la
  "opción b" del 19/10/2026 (ver más abajo), que lo había dejado complementario. El anteproyecto
  (RF03: "indicar qué documentos faltan") no fija la lista de documentos obligatorios — esta es
  una decisión interna de diseño, no una corrección del anteproyecto, así que
  `docs/RF_ANTEPROYECTO.md` no se toca.

  **Qué se cambió** (28/10/2026): `ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS` pasa de 3 a
  **4** documentos (constante nueva `CODIGO_COMPROBANTE_DOMICILIO`), con lo que el contador queda
  en **"Expediente: N de 4"**, la fila se numera como **3.** y su badge pasa de "No cargado"
  (gris) a **"Falta"** (amarillo) — todo eso salió solo, porque el contador
  (`ControladorClientesServlet`) y la pestaña (`clientes/ficha.jsp`) ya leían
  `ItemExpediente.isObligatorio()`, que sale de esa única lista. Además:
  `ServicioSolicitudes.agregarFaltantesExpediente` (gate real de RF06) lo exige **siempre**, sin
  condición previa —a diferencia del comprobante de ingreso, que depende de la ficha—, con el
  texto *"Falta cargar el comprobante de domicilio del cliente en su expediente."*, y
  `construirChecklistPrevioEnvio` pasa de **8 a 9 ítems** ("Comprobante de domicilio cargado",
  ubicado entre el comprobante de ingreso y la autorización, según el orden del expediente), que
  ven tanto el oficial (`solicitudes/ficha.jsp`) como el gerente (`aprobaciones/ficha.jsp`).

  **Cierra C14**: `ControladorDocumentosServlet.itemsFaltantesExpediente` nunca filtró por
  `isObligatorio()`, así que ya listaba el domicilio entre los faltantes mientras el contador no
  lo contaba. Los dos criterios quedan de acuerdo **sin tocar ese método**.

  **Efecto sobre lo que ya existía** (verificado con `SELECT` el 28/10/2026): ninguna solicitud ni
  préstamo se bloquea — las 4 solicitudes de la base están `APROBADA`, el gate vive en
  `enviarAAprobacion` (ya ocurrido para todas), y ni `ServicioAprobacionCredito`, ni
  `ServicioFormalizacion`, ni `ServicioDesembolso` revalidan el expediente de la persona (solo
  usan `obtenerVigentePorPrestamoYTipo`, contexto PRESTAMO). Lo único retroactivo es **visible**:
  la ficha de **Joel Jesús Acosta** (la única persona sin comprobante de domicilio) pasó de
  "3 de 3 ✓" a **"3 de 4"** con "Falta", sin bloquear el desembolso de `CC-2026-00004`
  — aprobado explícitamente por Joel el 28/10/2026.

  **Pendiente, no bloqueante**: revisar los casos de `pruebas/humo_http.sh` que dependen de
  "3 de 3" o de la lista vieja (ver §10) — no se corrió ni se modificó en esta tanda.
- **Alta vs. edición, mismo criterio que RF02**: cédula vencida bloquea el ALTA, solo avisa en
  la EDICIÓN.
- **Cambiar un dato que invalida evidencia ya cargada la vuelve a poner en "Falta" automáticamente**
  (cédula, situación laboral) — nunca queda un documento vigente que ya no corresponde a los
  datos actuales de la persona.
- **Los 2 tipos reservados del buró (autorización/resultado) solo se cargan por su propia ruta**,
  nunca por la genérica de "Otros documentos" — cierre del agujero de seguridad.
- **"Documento cargado" dice qué falta** (lista de etiquetas), no solo si "quedó completo" o no.

## 8. Historial de cambios

*(Reconstruido desde `git log` el 11/10/2026.)*

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 24-25/09/2026 | Implementación base del expediente junto con clientes/buró/cotizador (E3) | `153e612`/`741963f` | — |
| 08/10/2026 | Circuito de autorización al buró (Parte 1) + cédula vencida: alta rechaza, edición avisa, invalida el escaneo al cambiar la fecha | `f25da5c` | `rf03-parte1` |
| 08/10/2026 | Parte 2: botón de 3 estados para la autorización, reimpresión sin generar un número nuevo, 3 íconos ⓘ con el texto legal | `3f11934` | `rf03-parte2` |
| 08/10/2026 | Parte 3: Ley 6534/2020 correcta en el texto legal, corrección real del domicilio compuesto (migración de datos) — **reprobado por Joel, Clean and Build OK** | `6a9f928` | `rf03-parte3` |
| 08/10/2026 | Parte 4: detalles visuales del expediente, modal "Documento cargado" con lo que falta, pestaña renombrada — **reprobado por Joel, Clean and Build OK** | `496047b` | `rf03-parte4` |
| 09/10/2026 | Últimos ajustes visuales (botón de paso ③, tamaño de botones, aviso de cédula vencida en vivo) — **reprobado por Joel: OK los 3 puntos** | `30b9e2f` | `rf03-probado` |
| 09/10/2026 | Cierra el agujero del PDF falso en el expediente | `89e5c43` | `rf03-agujero-cerrado` |
| 09/10/2026 | Cambio de situación laboral invalida el comprobante de ingreso | `7946119` | `rf03-situacion-laboral` |
| 28/10/2026 | **Comprobante de domicilio OBLIGATORIO** (decisión de Joel del 21/10/2026): los obligatorios pasan de 3 a 4, "Expediente: N de 4", gate de RF06 y checklist de 8 a 9 ítems. Cierra C14. **Probado en pantalla el mismo día** (§9, pruebas A y B). `mvn test` 804/0 + `PruebaDomicilioObligatorio` 4/0 | *(este commit)* | — |

| 29/10/2026 | **Flujo guiado (B88, + B28/B27/B34/B35) y orden del expediente (B90)**: la autorización pasa a ser la fila 1, y el destino y el texto de cada acción salen de un único dueño (`ServicioClientes.calcularSiguientePaso`), con la excepción de "cargando papeles se queda en ②", el botón que no se ofrece en su propia pestaña y los dos enlaces a la ficha que abren la pestaña pendiente. **Probado en pantalla el mismo día** (§9). `mvn test` 827/827 | *(este commit)* | — |

| 01/11/2026 (tanda 2) | **B42**: el mismo archivo cargado dos veces — rechazo si es del mismo tipo, aviso amarillo si es de otro, búsqueda acotada al expediente (§2). Archivos: `ServicioDocumentos` (6 métodos), `DocumentoExpedienteDAO.listarVigentesConMismoHash`, `ControladorDocumentosServlet` (el aviso amarillo en los dos caminos de carga). **Dos fallas propias encontradas en pantalla por Joel el mismo día** y corregidas (§9): el chequeo no bloqueaba nada (comparaba contra un `getArchivoBinario()` en null) y el mensaje se veía sin el punto final (lo saca el pintor de carteles). **Probado en pantalla el 01/11 y reprobado el 03/11/2026** (§9). `mvn test` 899/899 | `5c93936` | — |
| 09/11/2026 | **C19** (seguridad): **abrir un documento del expediente queda registrado en la auditoría**, y el registro dice **de quién es con palabras**. Operación `VER_DOCUMENTO_EXPEDIENTE` → *"Ver documento del cliente"*, con el mismo `tipoEntidad` y el mismo id que la carga, así el informe muestra juntas las dos operaciones del mismo archivo. Se registra **cada** apertura y **antes** de servir el contenido. El detalle: *"Abrió «Cédula de identidad» (versión 1) de López Rojas Mirtha Lucía (C.I. 4.150.164)."*, o el N.º de operación del préstamo, o el código de la solicitud — la primera versión decía *"de persona 3"* y Joel la rechazó: el gerente no sabe quién es "persona 3". **NO se implementó control de pertenencia, por decisión de Joel**: en Capital Crédito todo oficial ve a todos los clientes, y el sistema no tiene la noción de "mis clientes" (ni oficial asignado en `personas` ni filtro en el listado), así que cambiar el id en la URL no da acceso a nada que el usuario no pueda abrir navegando la ficha. La cajera sigue bloqueada por el permiso de siempre, que no se tocó. Probado en pantalla por Joel (§9) | `COMMIT_C19` | — |

## 9. Pruebas realizadas en pantalla

**01/11/2026 21:39-21:51 y 03/11/2026 06:33-06:38 (Joel) — B42, el mismo archivo cargado dos
veces:**

- **La primera versión no bloqueaba nada** (01/11, 21:39-21:40): *"Reemplazar"* el comprobante
  de domicilio de **Ana** con el mismo `ejemplo.pdf` creó la **versión 2 y la 3**, las tres con
  el mismo hash. **Causa**: comparaba contra el documento vigente de
  `obtenerVigentePorPersonaYTipo`, que trae solo `id_archivo` y **no** hace JOIN con
  `archivos_binarios` — el `getArchivoBinario()` venía en **null** y el chequeo se salía sin
  comparar. Se corrigió con una consulta propia con JOIN (`listarVigentesConMismoHash`).
  **Lección**: verificar qué devuelve de verdad un DAO antes de comparar contra un campo que
  puede venir en null (anotada en la skill `sgcpc-cambio-seguro`).
- **Reprueba el 01/11** (en la misma franja 21:39-21:51): el mismo reemplazo mostró el cartel
  **rojo** y **no** creó
  versión nueva. El caso de **otro tipo** con el mismo contenido mostró el **aviso amarillo** y
  **sí** guardó. **OK.**
- **El texto se veía sin el punto final** (01/11): no faltaba en el código — el cartel usaba el
  mensaje entero como **título** y a los títulos les quita el punto (`pie.jsp`,
  `dividirEnTituloYDetalle`). Se partió en dos oraciones.
- **03/11/2026, texto nuevo aprobado por Joel**: *"Reemplazar"* el comprobante de domicilio de
  Ana con `ejemplo.pdf` → rojo **"Ese archivo ya está cargado"** / **"Es idéntico al que figura
  como «Comprobante de domicilio»: no hace falta volver a subirlo."**, con el punto visible, y
  **sin versión nueva** (el documento sigue fechado 01/11/2026). **OK.**

**29/10/2026, Joel (oficial Lorenzo Ramírez) — flujo guiado (B88) y orden del expediente (B90),
en `sgcpc_db_pruebas`:**

- Cliente nuevo **`Prueba de Flujo Jueves 29`** (C.I. 1.222.333, persona 6). El alta avisó
  *"Siguiente paso: generar la autorización, hacerla firmar y cargarla."* y la pestaña ② mostró
  **autorización 1, cédula 2, ingreso 3, domicilio 4** y **"0 de 4"** (B90).
- **`AUT-2026-00005`** generada y cargada → llevó a **③** con *"Autorización cargada y
  consentimiento registrado. Siguiente paso: registrar el resultado del buró."*
- Cédula → **se quedó en ②**: *"Documento cargado. Faltan: Comprobante de ingreso, Comprobante de
  domicilio. Pendiente también: registrar el resultado del buró."* Comprobante de ingreso →
  *"Falta: Comprobante de domicilio. Pendiente también: registrar el resultado del buró."*
- Buró **850 · A · sin mora** → llevó a **②**: *"Consulta registrada. 850 · A · sin mora — Falta:
  Comprobante de domicilio."*, con **③ en ✓**.
- **`SOL-2026-0006`** creada con el domicilio pendiente; cargado el domicilio →
  *"Documento cargado. Siguiente paso: enviar SOL-2026-0006 a aprobación."* y **llevó a la ficha
  de la solicitud**; **9/9 ✓** y enviada: "Pendiente de aprobación", 11:43.
- **Ajustes de la misma prueba**: en `Ana María Prueba` (persona 4, sin autorización) el botón
  *"Siguiente: ② Expediente →"* aparece en **① Datos** y **no** en ② (no se ofrece a sí mismo); y
  el enlace *"Ver ficha completa / documentos"* de **`SOL-2026-0007`** abre
  `/clientes/4#tabDocumentos`, directo en ②.

**28/10/2026, Joel (oficial Lorenzo Ramírez) — comprobante de domicilio OBLIGATORIO, las dos
pruebas de la tanda, en `sgcpc_db_pruebas`:**

- **Prueba A, efecto retroactivo sobre un cliente ya cargado** (`Joel Jesús Acosta`, la única
  persona de la base sin comprobante de domicilio): su ficha pasó de "3 de 3 ✓" a **"3 de 4"**,
  con la fila **"3. Comprobante de domicilio (obligatorio)"** en **"Falta"**, la autorización
  renumerada a **4**, y el paso **② sin ✓**. **No bloqueó `CC-2026-00004`** (`PENDIENTE_DESEMBOLSO`),
  como estaba previsto: el gate vive en `enviarAAprobacion`, que para ese préstamo ya ocurrió.
- **Prueba B, circuito completo con un cliente nuevo** (`Ejemplo 03 Ejemplo 03`, C.I. 789.456,
  persona 5): cédula + comprobante de ingreso → **"2 de 4"**; autorización **AUT-2026-00004** y
  consulta al buró **850 · A · sin mora**; **SOL-2026-0005** creada, con el checklist de **9
  ítems** y **"✗ Comprobante de domicilio cargado"**. **"Enviar a aprobación" BLOQUEÓ** con el
  texto exacto *"Falta cargar el comprobante de domicilio del cliente en su expediente."* y el
  botón "Entendido". Cargado el comprobante → **"Expediente completo"**, **② con ✓**, checklist
  **9/9** y *"Cumple validaciones preliminares"* → enviada: **"Pendiente de aprobación"**,
  28/10/2026 15:32.
- **Dato de prueba**: antes se creó por error `Ana María Prueba` (persona 4, C.I. 4.567.891), con
  el domicilio cargado. Queda en la base; se va con la limpieza previa a la entrega.
- **Hallazgos nuevos de esta prueba**: **B88** (flujo guiado: a dónde lleva el sistema después de
  cada carga), **B89** ("No cumple validaciones preliminares" no dice cuál falta) y **B90** (orden
  de la lista del expediente, pendiente de decisión de Joel) — los tres en `FASE2_PLAN.md`.

**08/10/2026 (Joel)**, Parte 3 (`6a9f928`): "Reprobado por Joel: Clean and Build OK" — confirmó
el texto legal correcto y el domicilio recalculado.

**08/10/2026 (Joel)**, Parte 4 (`496047b`): "Reprobado por Joel: Clean and Build OK" — confirmó
los ajustes visuales del expediente.

**09/10/2026 (Joel)**, cierre (`30b9e2f`): "Reprobado por Joel: OK los 3 puntos" — el botón
corto de paso ③, el tamaño uniforme de los botones, y el aviso en vivo de cédula vencida.

Los 2 commits de seguridad posteriores (`89e5c43`/`7946119`, mismo día) no tienen una frase
explícita de "reprobado por Joel" en el mensaje — son correcciones de un hallazgo encontrado
por Joel mismo (según el propio mensaje de `rf03-agujero-cerrado`: "Mismo agujero que el del
vencimiento de cédula"), lo que indica que surgieron de que Joel SIGUIÓ probando después del
cierre formal, pero sin una confirmación posterior documentada de que la corrección en sí
quedó vista en pantalla.

**14/10/2026 (Joel), REPRUEBA FORMAL, perfil oficial, `sgcpc_db_pruebas`** — tag
`rf03-reprobado`: expediente completo para un cliente asalariado (4 documentos exigidos);
"Faltan: ..." actualizándose tras cada carga, barra de progreso del 25 % al 100 %;
autorización AUT-2026-00066 generada en PDF, firmada y cargada; cédula con vencimiento ya
pasado → aviso en vivo en la ficha, advertencia al intentar guardar, el documento mostrando
"Vencida" y el expediente cayendo al 75 %; al renovar la fecha de vencimiento, el sistema pide
cargar la cédula renovada de nuevo. **Resultado: APROBADO, sin hallazgos nuevos** — incluye y
confirma en pantalla las 2 correcciones de seguridad del 09/10/2026
(`rf03-agujero-cerrado`/`rf03-situacion-laboral`), que quedaban sin esa confirmación explícita.

## 10. Pendientes y trabajo futuro

Ninguno de los de pantalla — la reprueba formal del 14/10/2026 confirmó las 2 correcciones de
seguridad del 09/10/2026 que quedaban sin ver en pantalla.

- **2 pruebas diferidas a RF20** (dependen del paso del tiempo, no se pueden probar el mismo
  día — mismo criterio que la autorización vencida a los 30 días, ya probada en `RF04_buro.md`
  §10): (a) cargar una autorización de buró firmada un día distinto al de su emisión → debe
  rechazar con "Esta autorización es de otro día…"; (b) una cédula vigente hoy que vence al día
  siguiente, con el solo paso del tiempo (sin editar nada) → debe aparecer el renglón rojo de
  "vencida" sin ninguna acción manual. Sin implementar ni probar en esta tanda.

- ~~**(17/10/2026, hallazgo de Joel, grupo Laborales — sin corregir todavía, solo análisis)
  La pestaña "Documentos" no distingue obligatorio de complementario.** El Expediente muestra
  "2 de 4 documentos requeridos" y marca "Falta" en `COMPROBANTE_DOMICILIO` con el mismo peso
  visual que `CEDULA_IDENTIDAD`/`COMPROBANTE_INGRESO`, aunque ya está confirmado (ver análisis
  de `RF06_envio_aprobacion.md §6` punto 1, y la investigación del 17/10/2026 sobre el grupo
  Laborales) que `ServicioSolicitudes.agregarFaltantesExpediente` — el gate real de "Enviar a
  aprobación" (RF06) — **solo** exige `CEDULA_IDENTIDAD` y `COMPROBANTE_INGRESO` (condicional);
  `COMPROBANTE_DOMICILIO` es puramente informativo, nunca bloquea el envío. La pantalla, tal
  como está hoy, no comunica esa diferencia — un oficial puede leer "2 de 4" y asumir
  erróneamente que la solicitud no se puede enviar. Dos soluciones posibles, a decidir después,
  sin aplicar nada ahora: (a) que la barra/el contador de `construirItemsExpediente` reflejen
  solo los 2 documentos obligatorios (dejando `COMPROBANTE_DOMICILIO` como una fila aparte, sin
  pesar en el "N de N"); o (b) mantener las 4 filas tal como están pero explicar la diferencia en
  pantalla (ej. una etiqueta "(obligatorio)"/"(complementario)" junto a cada fila, o un ⓘ). No
  decidido todavía cuál.~~ — **resuelto (19/10/2026, tanda de usabilidad, Commit 3 de 3,
  opción b elegida por Joel)**: `ItemExpediente` agrega `obligatorio` (`ServicioClientes.
  CODIGOS_EXPEDIENTE_OBLIGATORIOS` — la única lista: cédula, comprobante de ingreso y
  autorización de buró **—los 4 desde el 28/10/2026, ver §7—**; también referenciada por nombre, no por un literal repetido, desde
  `ServicioSolicitudes.agregarFaltantesExpediente`, el gate real de RF06). En `clientes/
  ficha.jsp`: las 3 obligatorias quedan numeradas (1, 2, 3) con "(obligatorio)" junto al título;
  el comprobante de domicilio queda sin número, con "(complementario)", y su badge pasa de
  "Falta" (amarillo) a "No cargado" (gris) cuando no está cargado — conserva su misma tarjeta con
  "Cargar"/"Ver", solo cambia el peso visual. El encabezado y la barra de progreso
  (`ControladorClientesServlet`) ahora cuentan y suman solo sobre los 3 obligatorios: "Expediente:
  X de 3 documentos obligatorios" (antes "X de 4 documentos requeridos"). **Efecto en cascada,
  correcto y antes no lo era**: el indicador "② Expediente ✓" (y, por lo tanto, el botón
  "④ Solicitud", habilitado cuando ① y ② están completos) usa el mismo cálculo —
  `pasoExpedienteCompleto` ya no exige el comprobante de domicilio, igual que el control real de
  RF06. Antes, un cliente con cédula, comprobante de ingreso y autorización de buró completos
  pero sin comprobante de domicilio NO veía el ✓ en "② Expediente" ni podía avanzar directo a
  "④ Solicitud" aunque ya calificaba para enviar a aprobación -- contradicción real con el gate,
  no solo cosmética. **Superado el 28/10/2026**: la decisión de Joel del 21/10/2026 (ver §7) hizo
  obligatorio el comprobante de domicilio, así que ya no hay ninguna fila complementaria y el
  contador volvió a contar los 4 — pero ahora **sí** coincide con el gate real de RF06, porque el
  gate también los exige a los 4. El mecanismo de la opción b (`isObligatorio()`, la etiqueta y el
  badge) **se conserva**: es el que hizo que este cambio fuera de una sola línea.

- **(28/10/2026) `pruebas/humo_http.sh`: CORREGIDO, falta correrlo.** El arreglo está aplicado
  (carga del tipo 3 dentro de `crear_persona_con_expediente`, líneas 1549-1556, verificado con
  `bash -n`); **correr el guion sigue siendo decisión de Joel**, así que todavía no se ejecutó.
  El relevamiento que lo detectó, en solo lectura:
  - **Causa**: el helper **`crear_persona_con_expediente` (líneas 1498-1570)** arma el expediente
    de los clientes de prueba cargando los tipos **1** (cédula, línea 1537), **2** (comprobante de
    ingreso, 1542), **4** (autorización, 1555) y **5** (informe del buró, 1560) — **nunca el tipo
    3, el comprobante de domicilio**. Con el domicilio ya obligatorio, todo cliente creado por ese
    helper queda con el expediente incompleto y **no puede enviar su solicitud a aprobación**.
  - **Alcance**: el helper se usa en **12 lugares** (1748, 1797, 1853, 1880, 1927, 2542, 3178,
    4158, 5077, 5107, 5176, más la definición). El primero que rompe es
    `POST /solicitudes/{id}/enviar` de la **línea 1774** (Cliente 2, espera *"Solicitud enviada a
    aprobación"*); detrás caen en cascada los tramos que dependen de una solicitud enviada
    (aprobación, formalización, desembolso, cobro: E6, E7, E8).
  - **El arreglo fue uno solo, ya aplicado** (28/10/2026): un `POST /documentos/cargar` con
    `idTipoDocumento=3` dentro del helper, entre el bloque del tipo 2 y el de la autorización —
    copia literal del bloque del tipo 2, con `filename=comprobante_domicilio.pdf` y su
    `registrar ... 302`. Cubre los 12 llamadores de una vez. **No se relajó la regla en ningún
    caso de prueba.**
  - **Lo que NO hay que tocar**: el caso de las **líneas 1218-1228**, que sube un PDF al tipo 3 y
    verifica `"Documento cargado."` y el `SELECT` de `VIGENTE` — sigue válido, no afirma nada
    sobre obligatoriedad. Y el cliente principal del guion ya carga el tipo 3 en la **línea
    1222**, así que su envío (línea 1737) no se ve afectado.
  - **Textos**: el `grep` del 28/10 sobre `documentos obligatorios|complementario|No cargado|de 4
    documentos` en el guion **no devolvió ninguna aserción** — no hay casos que verifiquen
    literalmente "N de 3", "(complementario)" ni el badge "No cargado", así que esos textos nuevos
    no rompen nada. Confirmarlo igual al correrlo.

## 11. Guion para la defensa

1. Mostrar el Expediente de un cliente nuevo — todas las filas en "Falta".
2. Cargar un documento — mostrar el ✓ y el modal diciendo qué sigue faltando.
3. Cambiar la fecha de vencimiento de la cédula ya cargada — mostrar que la fila vuelve a
   "Falta" automáticamente.
4. Cambiar la situación laboral de un cliente con comprobante de ingreso ya cargado — mostrar
   el mismo efecto.
5. Intentar cargar la autorización de buró por la ruta genérica (forzando el formulario) —
   mostrar el rechazo (agujero cerrado).
6. Mostrar el circuito completo de autorización al buró (generar, cargar firmada, vigencia) —
   compartido con `RF04_buro.md`.
