# RF13 — Carga de firmados y bloqueo del desembolso hasta completar el checklist

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf13-probado`.** El texto
se cumple sin excepción con CC-2026-00001: carga de los 2 firmados, los 3 rechazos de archivo
(PDF falso, `.docx`, 6 MB) y el bloqueo de regenerar, todos confirmados. Queda pendiente ver el
desembolso bloqueado con el checklist incompleto (sección 10), que se verifica con la próxima
operación formalizada antes de cargar los firmados.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF13 (módulo Formalización):

> El sistema debe permitir cargar los documentos firmados escaneados y no permitir el
> desembolso hasta que el checklist de firmados esté completo.

RF13 depende de RF11/RF12 (el préstamo y los 3 PDF ya generados) y es la condición de entrada
de E6 (desembolso) — comparte la ficha `/formalizacion/{id}` con esos dos RF.

## 2. Cómo funciona

**a) Dónde está y cómo funciona la carga.** En la ficha de formalización
(`formalizacion/ficha.jsp`), card "Checklist antes del desembolso": una lista con el estado de
cada tipo (✓ o "Pendiente", con enlace "Ver" si ya hay uno vigente) y, debajo, un formulario con
3 campos — `<select>` "Tipo de documento" (solo los tipos del checklist), `<input type="file">`
"Archivo escaneado (PDF, JPG o PNG)" y el botón "Cargar". Postea a la ruta GENÉRICA
`/documentos/cargar` (la misma que usa el expediente de clientes de RF03), con `idPrestamo` en
un campo oculto en vez de `idPersona` — el controlador (`ControladorDocumentosServlet.doPost`)
distingue cuál de los dos llegó y llama a `ServicioDocumentos.cargarDocumentoPrestamo` en vez de
`cargarDocumento`.

**Qué tipos hay**: **3**, todos con `obligatorio_desembolso = TRUE` y contexto `PRESTAMO`
(`bd/03_parametros.sql`, cerca de la línea 230) — **corregido el 11/10/2026**: este documento
decía "exactamente 2" y le faltaba `PLAN_PAGOS_FIRMADO` (hallazgo de Joel en la reprueba del
Lote 1 de la consolidación RF08-RF19: el anteproyecto pide contrato, pagaré Y plan de cuotas
firmados y controlados, no solo los dos primeros — ver sección 8, Historial):

| Código | Nombre | Requiere firma |
|---|---|---|
| `CONTRATO_FIRMADO` | Contrato firmado | Sí |
| `PAGARE_FIRMADO` | Pagaré firmado | Sí |
| `PLAN_PAGOS_FIRMADO` | Plan de pagos firmado | Sí |

**Corrección (18/10/2026, ronda de columnas sin uso)**: la columna "Requiere firma" de la tabla
de arriba la decide el sufijo `_FIRMADO` del propio `codigo_tipo` (y `obligatorio_desembolso`
para que entre a ESTE checklist) — nunca existió un `requiere_firma=TRUE` real que lo determinara:
esa columna de `tipos_documento` se leía de la base pero ningún servicio la consultaba jamás;
se eliminó del esquema en esta misma ronda.

(`CONTRATO_GENERADO`/`PAGARE_GENERADO`/`PLAN_PAGOS_GENERADO`, los PDF que arma el sistema —
RF12 —, tienen `obligatorio_desembolso = FALSE`: no forman parte de ESTE checklist, solo los
_FIRMADO. `aplica_codeudor` sigue en `tipos_documento` sin uso desde el retiro de codeudor,
18/10/2026 — pendiente de eliminar en el Commit 3 del refactor.)

**Etiqueta de cada ítem**: siempre "Contrato firmado (firmado por el cliente)", sin variante —
desde el retiro de codeudor (18/10/2026) no hay una segunda firma que agregar al mismo
escaneo.

**El checklist es genérico, no hardcodeado**: `obtenerChecklist` recorre
`tipoDocumentoDAO.listarActivosPorContexto(conexion, "PRESTAMO")` filtrando
`obligatorio_desembolso=TRUE`, sin ningún código de tipo fijo en el código — por eso agregar
`PLAN_PAGOS_FIRMADO` solo necesitó una fila nueva en `tipos_documento`
(`bd/migraciones/E10_14_plan_pagos_firmado.sql`), sin tocar ningún JSP ni ningún método de
`confirmarDesembolso`/`asegurarCondicionesDesembolso`/la bandeja de desembolsos/el fondo
sugerido: los 3 ya comparten el mismo método. El orden en que aparece (3.º, después de
contrato y pagaré) es por `ORDER BY nombre_tipo` (alfabético) — no hay una columna de orden
explícita, así que es correcto por el nombre elegido, no por un mecanismo a prueba de futuros
tipos con otro nombre.

**Préstamos ya desembolsados antes de este cambio (ej. CC-2026-00001)**: no se tocó su estado.
La ficha de formalización sigue mostrando el ítem "Plan de pagos firmado" como no cumplido
(nunca se cargó, el tipo no existía cuando se formalizó), pero con el texto "No exigido al
desembolsar (requisito agregado el 11/10/2026)" en vez de "Pendiente" — sería falso decir que
está pendiente algo que, para ese préstamo, ya no se le va a volver a exigir (ya desembolsó).
Confirmado que ningún informe lee el checklist (`ServicioInformes`/`ServicioReportes`: cero
referencias), así que esto no afecta ningún listado.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `CARGAR_DOCUMENTO_FORMALIZACION` | DOCUMENTO_EXPEDIENTE | Primera carga de ese tipo para ese préstamo — "Cargó "nombre.pdf" (versión 1) para el préstamo id N." |
| `NUEVA_VERSION_DOCUMENTO_FORMALIZACION` | DOCUMENTO_EXPEDIENTE | Se volvió a cargar el mismo tipo (reemplazo) — "Cargó "nombre.pdf" (versión 2) para el préstamo id N." |

Cada carga (primera o reemplazo) queda registrada con el nombre del archivo, el número de
versión y el préstamo — quién la cargó es el `idUsuario` del evento de auditoría (el usuario de
la sesión que hizo el POST).

## 4. Dónde está

- `servicios/ServicioDocumentos.java` — `cargarDocumentoPrestamo`, `validarArchivo`,
  `detectarExtensionPorContenido`, `extensionDeclarada`, `listarTiposDocumentoPrestamo`.
  Desde el 01/11/2026 (**B42**) este camino tiene el **mismo** control del archivo repetido que
  el expediente —rechazo si es del mismo tipo, aviso amarillo si es de otro—, acotado a **este
  préstamo** (`listarVigentesConMismoHash` con `id_prestamo`). Es el mismo código, no una copia:
  el detalle y los textos están en [RF03_expediente.md](RF03_expediente.md) §2.
- `servicios/ServicioFormalizacion.java` — `obtenerChecklist`, `esChecklistCompleto`,
  `ItemChecklist` (incluye `getIdDocumentoVigente` para el enlace "Ver"),
  `asegurarSinDocumentosFirmados` (bloquea regenerar, ver sección 6d).
- `servicios/ServicioDesembolso.java` — `confirmarDesembolso` (revalida el checklist antes de
  desembolsar, dentro de la transacción).
- `controladores/ControladorDocumentosServlet.java` (`POST /documentos/cargar`,
  `GET /documentos/{id}` para "Ver").
- `controladores/ControladorDesembolsoServlet.java`, `controladores/ControladorFormalizacionServlet.java`.
- `webapp/WEB-INF/vistas/formalizacion/ficha.jsp` (carga), `webapp/WEB-INF/vistas/desembolso/ficha.jsp`
  (condiciones para desembolsar).
- Parámetro `archivos.tamano_maximo_mb` = 5 (`bd/03_parametros.sql:171`).
- Tabla `tipos_documento` (filas `CONTRATO_FIRMADO`/`PAGARE_FIRMADO`), `documentos_expediente`
  (versionado), `archivos_binarios` (contenido).

## 5. Dependencias

- **Depende de**: RF11/RF12 (el préstamo tiene que existir, `PENDIENTE_DESEMBOLSO`, con los 3
  PDF generados); RF03 (`ServicioDocumentos.validarArchivo`/`detectarExtensionPorContenido` son
  exactamente los mismos que ya valida el expediente de clientes, sin duplicar la lógica).
- **Lo afecta un cambio en**: `ServicioDocumentos.validarArchivo` (compartido con RF03 — un
  cambio ahí afecta a los dos); el parámetro `archivos.tamano_maximo_mb` (compartido con
  cualquier otra carga del sistema, no solo RF13).
- **A quién afecta un cambio acá**: E6/desembolso (el checklist es la condición de entrada,
  `ServicioDesembolso.confirmarDesembolso` depende directamente de `esChecklistCompleto`); la
  bandeja de `/formalizacion` (segunda sección, préstamos `PENDIENTE_DESEMBOLSO` esperando el
  checklist) y la de `/desembolso` (solo lista los que ya lo tienen completo).

## 6. Análisis

### 11/10/2026 — revisión completa de RF13 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioDocumentos.java`, la parte de checklist de
`ServicioFormalizacion.java`, `ServicioDesembolso.confirmarDesembolso` y
`ControladorDesembolsoServlet.mostrarFicha`, `formalizacion/ficha.jsp` y `desembolso/ficha.jsp`,
`ControladorDocumentosServlet.java`, `FiltroAutorizacion.java`/`ServicioAutorizacion.construirMatriz`,
y `bd/03_parametros.sql` (tipos de documento, tamaño máximo).

### b) Validaciones del archivo

`ServicioDocumentos.validarArchivo` (línea 91-109) — **la misma que usa RF03** (literalmente el
mismo método, llamado por `cargarDocumento` y `cargarDocumentoPrestamo` por igual, sin
duplicar nada):

1. **Extensión declarada**: el nombre del archivo debe terminar en `.pdf`, `.jpg`/`.jpeg` o
   `.png` — cualquier otra (`.docx` incluido) rechaza con "Solo se aceptan archivos PDF, JPG o
   PNG. Si tiene un documento Word, guárdelo como PDF antes de cargarlo." (el mensaje menciona
   Word explícitamente, pensado para este caso exacto).
2. **Tamaño**: contra `archivos.tamano_maximo_mb` (hoy 5 MB) — "El archivo supera 5 MB.
   Escanéelo con menor resolución o en blanco y negro para que pese menos." Un archivo de 6 MB
   cae en el límite de la APLICACIÓN (no en el techo de 20 MB de `@MultipartConfig`, que solo es
   una red de seguridad del contenedor) — se valida y rechaza con el mensaje de pantalla normal,
   no con la página técnica de Tomcat.
3. **Contenido real, no solo la extensión** (`detectarExtensionPorContenido`): lee los primeros
   bytes y los compara contra la firma real de PDF (`%PDF`), JPG (`FF D8 FF`) o PNG (`89 PNG`).
   Un archivo falso (ej. un `.txt` renombrado a `.pdf`, o cualquier byte que no sea ninguna de
   las 3 firmas) rechaza con "El archivo no es un PDF, JPG o PNG real. Vuelva a escanear el
   documento y guárdelo como PDF." Si el contenido SÍ es un formato real pero distinto al que
   dice la extensión (ej. un PNG real guardado como `.pdf`), el mensaje es "El archivo no
   coincide con su tipo. Parece un archivo con la extensión cambiada..."
4. **Archivo vacío**: se corta antes, en el controlador (`ControladorDocumentosServlet.java:109`):
   `if (parte == null || parte.getSize() == 0) throw new NegocioException("Seleccione un
   archivo para cargar.")`.

**¿Son las mismas que en el expediente (RF03)?** Sí, exactamente — no hay ninguna validación
propia de RF13; todo pasa por el mismo `validarArchivo`/`detectarExtensionPorContenido` que
usa el expediente de clientes.

### c) Reemplazo

Si se carga dos veces el mismo tipo para el mismo préstamo: el anterior (VIGENTE) pasa a
`REEMPLAZADO` (nunca se borra, `documentoExpedienteDAO.marcarReemplazado`) y el nuevo se guarda
como VIGENTE con `version_documento` incrementado — mismo patrón de versionado que todo el
sistema (`REGLAS_DESARROLLO.md` §5). La auditoría lo registra como
`NUEVA_VERSION_DOCUMENTO_FORMALIZACION` (sección 3).

**¿Se puede ver, reemplazar o anular?**
- **Ver**: sí, el enlace "Ver" junto al ✓ abre el documento VIGENTE (`/documentos/{id}`, inline
  en una pestaña nueva). No hay una pantalla que liste las versiones REEMPLAZADAS de un
  documento de préstamo (solo queda recuperable por auditoría/base, no por una pantalla).
- **Reemplazar**: sí, cargando de nuevo el mismo tipo — no hay un botón "Reemplazar" aparte, es
  la misma acción de "Cargar".
- **Anular**: **no existe ninguna ruta para esto** — no hay forma de dejar un tipo "sin
  documento" otra vez después de haber cargado uno, salvo reemplazándolo por otro archivo. No
  es un hallazgo (nada en el texto de RF13 pide poder anular), solo una respuesta directa a la
  pregunta de Joel.

### d) El bloqueo del desembolso

**Dónde se controla (servidor, no solo pantalla)**: `ServicioDesembolso.confirmarDesembolso`
(línea 130-133) revalida `esChecklistCompleto(obtenerChecklist(conexion, idPrestamo))` **dentro
de la misma transacción** que registra el desembolso — si falta algo, lanza
`NegocioException("Faltan documentos firmados; complete el checklist antes de desembolsar.")`
y no se inserta nada. Esto corre siempre, sin importar qué mostraba la pantalla ni si se forzó
el `POST` directamente.

**Qué ve la cajera**:
- **Bandeja `/desembolso`** (lista): `listarListosParaDesembolsar` solo incluye préstamos con el
  checklist YA completo (`ServicioFormalizacion.filtrarConChecklistCompleto`) — uno incompleto
  simplemente no aparece ahí.
- **Entrando directo por la URL** (`/desembolso/{idPrestamo}`, conociendo o adivinando el id):
  la ficha SÍ se abre (el `GET` no filtra por checklist, solo la lista lo hace) y muestra la
  card "Condiciones para desembolsar" con cada requisito (checklist, documentos vigentes, caja
  lista, saldo suficiente) con una viñeta ✓ o "Pendiente"/"Vencido"/etc., y un aviso amarillo
  puntual: *"Faltan documentos firmados cargados."* con un enlace "Ir a formalización". **La
  card "Confirmar desembolso" ni siquiera se renderiza** mientras falte alguna condición
  (`<% if (puedeDesembolsarDesembolso) { %>`, `desembolso/ficha.jsp:160`) — no es solo un botón
  deshabilitado, el formulario completo no existe en el HTML servido. Si alguien forzara el
  `POST` igual (sin pasar por ese formulario), el servidor lo bloquea igual (párrafo anterior).

**Regenerar documentos una vez cargado un firmado**: `ServicioFormalizacion.asegurarSinDocumentosFirmados`
(llamada al principio de `regenerarDocumentos`) rechaza con "No se puede regenerar: ya hay un
documento firmado cargado para esta operación (regenerar dejaría firmado un documento distinto
al vigente)." si existe un `CONTRATO_FIRMADO` o `PAGARE_FIRMADO` VIGENTE. La pantalla
(`formalizacion/ficha.jsp`) también lo anticipa: si `hayFirmadoCargado`, ni siquiera muestra el
formulario de "Regenerar documentos" — en su lugar, el texto "No se puede regenerar: ya hay
documentos firmados cargados." Confirmado en los dos niveles (servidor y pantalla).

### e) Permisos

- **Cargar** (`POST /documentos/cargar`) y **ver** (`GET /documentos/{id}`): la ruta
  `/documentos` está protegida por `Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO`
  (`FiltroAutorizacion.java:45`) — **TOTAL para gerente y oficial, NINGUNO para la cajera**
  (`ServicioAutorizacion.java:40,79,106`). El administrador, TOTAL como siempre.
- **¿La cajera puede ver los firmados?** No — ni cargarlos ni verlos; la ruta `/documentos`
  le da 403 completo. Solo entra en el proceso después, en `/desembolso` (`Permiso.DESEMBOLSAR`),
  donde ve el RESULTADO del checklist (✓/Pendiente) pero no el enlace "Ver" (ese vive en
  `formalizacion/ficha.jsp`, que la cajera tampoco puede abrir —
  `Permiso.GENERAR_DOCUMENTOS_FORMALIZACION` es NINGUNO para ella).

### f) Cuando el checklist se completa

- **¿Cambia el estado de la operación?** No — el préstamo ya está `PENDIENTE_DESEMBOLSO` desde
  que se formalizó (RF11/RF12); completar el checklist no es una transición de estado, es una
  condición que se recalcula en vivo cada vez que se consulta (`esChecklistCompleto`, sin
  columna propia en la base).
- **¿Aparece en la bandeja de desembolso de la cajera?** Sí, inmediatamente — la próxima vez
  que la cajera abra `/desembolso`, `listarListosParaDesembolsar` ya lo incluye (no hay caché
  ni paso intermedio).
- **¿Se avisa en la pantalla?** Sí, en `formalizacion/ficha.jsp`: cartel verde "Documentación
  completa — listo para desembolso." apenas el ÚLTIMO documento pendiente queda cargado
  (corrección 3 de Joel, ya implementada).

### g) Textos

Revisados los mensajes de `ServicioDocumentos`/`ControladorDocumentosServlet` para esta carga:
todos en usted ("Seleccione un archivo...", "Si tiene un documento Word, guárdelo como PDF...",
"Escanéelo con menor resolución...", "Vuelva a escanear el documento...", "Guárdelo de nuevo
como PDF, JPG o PNG..."), sin voseo. Los carteles de éxito ("Documento cargado.") y error pasan
por el mecanismo genérico de `MensajesFlash`/`plantilla/pie.jsp`: "Aceptar" en éxito, "Entendido"
en error — sin excepciones para esta carga en particular.

### h) Cumplimiento

El texto se cumple **sin excepción**: se puede cargar los documentos firmados escaneados
(contrato y pagaré, los 2 tipos `obligatorio_desembolso`), y el desembolso queda bloqueado hasta
que el checklist esté completo — bloqueado en el servidor (dentro de la misma transacción que
registraría el desembolso), no solo en la pantalla. No se encontró ninguna parte del texto sin
cumplir ni ningún camino que permita desembolsar con el checklist incompleto.

## 7. Decisiones

No se encontraron hallazgos que exijan programar algo — RF13 cumple el texto firmado tal cual
ya estaba implementado. Confirmado en pantalla el 11/10/2026 (sección 9).

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 14/11/2026 | **B181**: en la sección *"Pendientes de desembolso"* de la bandeja, la columna *"Checklist"* decía *"Incompleto"* sin decir qué falta — había que abrir cada préstamo para saberlo. Ahora debajo de la etiqueta va *"Falta: Contrato firmado, Pagaré firmado"*, armado con **`ServicioFormalizacion.nombrarLoQueFalta`**, el mismo método que alimenta el aviso de la tarjeta del panel (B100): un dato, un dueño. **Sin consultas nuevas** (el checklist de esa misma vuelta ya estaba calculado; el controlador solo deja de descartarlo) y **sin tocar la etiqueta *"Incompleto"***, que es la que verifica `pruebas/humo_http.sh:2273`. El cálculo de "completo" y el bloqueo del desembolso no cambiaron. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 11/10/2026 | **Reprueba del Lote 1 confirmada en pantalla (Joel)**: checklist con 3 ítems, 2/3 no habilita, 3/3 "Documentación completa", regenerar bloqueado, `/desembolso/2` bloqueado, CC-2026-00001 con el texto nuevo. 2 detalles menores encontrados (b, e), para el Lote 2 | *(pendiente)* | `consolidacion-rf08-rf19-lote1` |
| 11/10/2026 | **ERROR GRAVE encontrado por Joel en la reprueba del Lote 1**: faltaba `PLAN_PAGOS_FIRMADO` en el checklist (el anteproyecto pide contrato, pagaré Y plan de cuotas) — agregado vía `bd/migraciones/E10_14_plan_pagos_firmado.sql` (solo `sgcpc_db_pruebas`) + `bd/03_parametros.sql`; `CargadorDatosPrueba`/`PruebaD117PromesaPagoParcial` actualizados; 2 pruebas unitarias nuevas (2 de 3 / 3 de 3); corregido "exactamente 2" de este mismo documento | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con CC-2026-00001: carga de los 2 firmados, 3 rechazos de archivo (PDF falso, `.docx`, 6 MB), bloqueo de regenerar; bandeja de la cajera actualizada | *(pendiente)* | `rf13-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, con CC-2026-00001 (Mirtha):**
- Carga del contrato firmado (PDF real) y del pagaré firmado (PDF real): ✓ en cada ítem del
  checklist y, al completar el segundo, el cartel verde "Documentación completa — listo para
  desembolso." (sección 6f).
- **3 rechazos de archivo, todos con "Entendido" (advertencia/error, no éxito)**:
  - PDF falso (bytes sin la firma real): "...no es un PDF, JPG o PNG real" — confirma
    `detectarExtensionPorContenido` (sección 6b).
  - `.docx`: mensaje mencionando específicamente "documento Word" — confirma el texto exacto de
    `validarArchivo`.
  - PDF de 6 MB: "...supera 5 MB" — confirma el parámetro `archivos.tamano_maximo_mb`.
  - En los 3 casos el checklist no se alteró (ningún ítem cambió de estado) — confirma que el
    rechazo ocurre ANTES de tocar `documentos_expediente` (la validación corre primero,
    `ServicioDocumentos.validarArchivo`, dentro de la misma transacción que la inserción).
- Regenerar documentos bloqueado: "No se puede regenerar: ya hay documentos firmados cargados."
  — confirma `asegurarSinDocumentosFirmados` (sección 6d).
- Cajera (Sofía Martínez, perfil CAJERO): el Panel mostró "1 listo para desembolsar" y
  CC-2026-00001 apareció en "Desembolsos pendientes" con el botón "Desembolsar" habilitado —
  confirma que la bandeja se actualiza en vivo apenas se completa el checklist (sección 6f), sin
  ningún paso intermedio.

**13/10/2026 (Joel), después de Clean and Build — Tanda 1 de mejoras pendientes:**
- `/formalizacion/1` (CC-2026-00001): el ítem no exigido del checklist muestra el badge corto
  "No exigido" con el mismo formato visual que "✓"/"Pendiente" de los demás ítems (la
  explicación "requisito agregado el 11/10/2026" quedó en una nota chica aparte) — confirma el
  cambio de `formalizacion/ficha.jsp` (ver sección 10, punto e, resuelto).

**Resultado: RF13 queda probado completo, sin pendientes.**

## 10. Pendientes y trabajo futuro

- ~~**Reprueba en pantalla de `PLAN_PAGOS_FIRMADO`**~~ — **confirmada el 11/10/2026 (Joel)**:
  checklist con 3 ítems, 2 de 3 no habilita, 3 de 3 "Documentación completa", regenerar
  bloqueado, `/desembolso/2` con checklist incompleto bloqueado (ficha y comprobante), y
  CC-2026-00001 mostrando "No exigido al desembolsar...".
- ~~**Ver el desembolso BLOQUEADO con el checklist incompleto**~~ — **confirmado** en la misma
  reprueba del 11/10/2026, con CC-2026-00002 (checklist incompleto).

**Detalles menores de la reprueba del 11/10/2026 (Joel) — se corrigen junto con el Lote 2:**

b) ~~`asegurarSinDocumentosFirmados` (el bloqueo de "Regenerar documentos" cuando ya hay un
   firmado cargado, para no dejar un escaneo firmado desincronizado del plan) solo mira
   `CONTRATO_FIRMADO`/`PAGARE_FIRMADO`~~ — **resuelto el 14/10/2026** (Tanda 2): agregado el
   3.er chequeo (`PLAN_PAGOS_FIRMADO`), con la condición extraída a `bloqueaRegenerar` (pura).
   Cubierto por 4 pruebas nuevas en `ServicioFormalizacionTest` (`mvn test` en verde) — **sin
   prueba en pantalla todavía** (no apareció en la Tanda 2 de hoy, Joel autorizó el commit solo
   con la cobertura de JUnit).
e) ~~En la ficha de formalización, el ítem "No exigido al desembolsar..." (préstamo ya
   desembolsado antes de este requisito, ej. CC-2026-00001) se ve en un recuadro gris chico
   (`badge badge-light`), distinto del resto de los ítems~~ — **resuelto y probado en pantalla
   el 13/10/2026**: el badge ahora es corto ("No exigido"), igual que "✓"/"Pendiente"; la
   explicación ("requisito agregado el 11/10/2026") se movió a una nota chica aparte, después
   del enlace "Ver".

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con CC-2026-00001 — salvo el punto 1
(checklist incompleto en /desembolso), pendiente para la próxima solicitud formalizada
(sección 10).**

1. Mostrar el checklist incompleto en `/formalizacion/{id}` (2 ítems "Pendiente") y la ficha de
   `/desembolso/{id}` con "Condiciones para desembolsar" marcando el checklist en rojo y el
   aviso "Faltan documentos firmados cargados." — sin el formulario de confirmar desembolso.
2. Intentar cargar un `.docx` — mostrar el rechazo mencionando específicamente "documento Word".
3. Intentar cargar un PDF falso (bytes sin la firma real) — mostrar "no es un PDF, JPG o PNG
   real".
4. Intentar cargar un PDF de 6 MB — mostrar el rechazo por tamaño (límite 5 MB), sin que
   aparezca ninguna página técnica de Tomcat.
5. Cargar el contrato firmado real — mostrar el ✓ y el enlace "Ver" (se abre inline).
6. Cargar el pagaré firmado real — mostrar el cartel verde "Documentación completa — listo para
   desembolso." y que el préstamo ya aparece en la bandeja `/desembolso`.
7. Intentar regenerar documentos — mostrar que la pantalla ya no ofrece el formulario ("No se
   puede regenerar: ya hay documentos firmados cargados").
8. Entrar como cajera y mostrar que no puede ver `/formalizacion/{id}` ni `/documentos/{id}`
   (403 en los dos).
9. Forzar (si hace falta, con una herramienta externa) un `POST` directo a
   `/desembolso/{id}/confirmar` sin que el checklist esté completo — mostrar el rechazo del
   servidor con el mismo mensaje, aunque se saltee la pantalla.
